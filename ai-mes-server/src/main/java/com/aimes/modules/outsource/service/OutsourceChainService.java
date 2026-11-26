package com.aimes.modules.outsource.service;

import com.aimes.common.utils.CodeUtil;
import com.aimes.modules.master.entity.BizMaterial;
import com.aimes.modules.mrp.service.BizProductionPlanService;
import com.aimes.modules.order.service.BizSalesOrderService;
import com.aimes.modules.outsource.entity.BizOutsourceOrder;
import com.aimes.modules.outsource.entity.BizOutsourceRequest;
import com.aimes.modules.outsource.mapper.BizOutsourceOrderMapper;
import com.aimes.modules.outsource.mapper.BizOutsourceRequestMapper;
import com.aimes.modules.production.entity.BizWoProcessTask;
import com.aimes.modules.production.entity.BizWorkOrder;
import com.aimes.modules.production.mapper.BizWoProcessTaskMapper;
import com.aimes.modules.production.mapper.BizWorkOrderMapper;
import com.aimes.modules.production.service.VirtualSemiProductService;
import com.aimes.modules.system.service.SysNotificationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 外协链路服务 - 核心业务编排
 *
 * 链路: 前道报工(+质检) → 半成品入库 → 自动创建外协申请 →
 *       外协加工单 → 外协发料 → 外协收货质检 →
 *       末道工序: 成品入库 / 非末道: 半成品入库 → 后道领料报工
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutsourceChainService {

    private final BizOutsourceRequestMapper requestMapper;
    private final BizOutsourceOrderMapper orderMapper;
    private final BizWoProcessTaskMapper processTaskMapper;
    private final BizWorkOrderMapper workOrderMapper;
    private final VirtualSemiProductService virtualSemiService;
    private final SysNotificationService notificationService;
    private final BizProductionPlanService productionPlanService;
    private final BizSalesOrderService salesOrderService;
    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private com.aimes.modules.workreport.service.WorkReportService workReportService;

    /**
     * 前道工序报工/质检通过后调用
     * 查询下一道工序是否为外协工序（查 biz_wo_process_task，按 step_group 排序，is_outsourceable=1）
     * 若是外协工序，则：
     * 1. 添加半成品物料信息（创建/获取虚拟半成品）
     * 2. 进行半成品入库（外协发料时出库）
     * 3. 创建或更新外协申请（差额模式）
     *
     * @param workOrderId  工单ID
     * @param reportedTask 当前报工的工序任务（qualifiedQty已累加）
     * @param stockInQty   本次入库数量（无质检=本次报工合格数，有质检=质检通过后全部合格数）
     */
    @Transactional(rollbackFor = Exception.class)
    public void handlePrevStepReported(Long workOrderId, BizWoProcessTask reportedTask, BigDecimal stockInQty) {
        if (stockInQty == null || stockInQty.compareTo(BigDecimal.ZERO) <= 0) return;

        // 查询下一道外协工序：按 work_order_id 查询工单工序任务，按 step_group 排序
        List<BizWoProcessTask> allTasks = processTaskMapper.selectList(
                new LambdaQueryWrapper<BizWoProcessTask>()
                        .eq(BizWoProcessTask::getWorkOrderId, workOrderId)
                        .orderByAsc(BizWoProcessTask::getStepGroup)
                        .orderByAsc(BizWoProcessTask::getStepNo));

        Integer currentGroup = reportedTask.getStepGroup();

        BizWorkOrder wo = workOrderMapper.selectById(workOrderId);
        if (wo == null) return;

        Integer maxGroup = allTasks.stream()
                .map(BizWoProcessTask::getStepGroup)
                .max(Integer::compareTo).orElse(0);

        // ── 同组并行外协处理 ──────────────────────────────────────────────────
        // 若本组内还有尚未建单的外协工序（与当前报工任务并行），直接为其创建草稿加工单。
        // 此类外协无前道虚拟半成品，发料时需人工填写明细。
        List<BizWoProcessTask> sameGroupOutsource = allTasks.stream()
                .filter(t -> currentGroup != null && currentGroup.equals(t.getStepGroup()))
                .filter(t -> t.getIsOutsourceable() != null && t.getIsOutsourceable() == 1)
                .filter(t -> !t.getId().equals(reportedTask.getId()))
                .filter(t -> t.getOutsourceOrderId() == null)
                .collect(Collectors.toList());
        if (!sameGroupOutsource.isEmpty()) {
            BigDecimal sameGroupQty = allTasks.stream()
                    .filter(t -> currentGroup.equals(t.getStepGroup()) && !"cancelled".equals(t.getStatus()))
                    .filter(t -> t.getIsOutsourceable() == null || t.getIsOutsourceable() != 1)
                    .map(t -> t.getQualifiedQty() != null ? t.getQualifiedQty() : BigDecimal.ZERO)
                    .min(BigDecimal::compareTo)
                    .orElse(stockInQty);
            if (sameGroupQty.compareTo(BigDecimal.ZERO) > 0) {
                for (BizWoProcessTask outsourceTask : sameGroupOutsource) {
                    try {
                        boolean sameGroupIsLast = currentGroup.equals(maxGroup);
                        Long receiptMaterialId = sameGroupIsLast ? wo.getProductId()
                                : virtualSemiService.getOrCreateVirtualMaterial(
                                        wo.getProductId(), outsourceTask.getStepName()).getId();
                        createOrUpdateOutsourceOrder(wo, outsourceTask, null, receiptMaterialId, sameGroupQty);
                        log.info("同组并行外协建单: workOrderId={}, outsourceTaskId={}, qty={}",
                                workOrderId, outsourceTask.getId(), sameGroupQty);
                    } catch (Exception e) {
                        log.error("同组并行外协建单失败: workOrderId={}, outsourceTaskId={}",
                                workOrderId, outsourceTask.getId(), e);
                    }
                }
            }
        }

        // ── 下一组外协处理 ──────────────────────────────────────────────────
        // 找到当前工序之后的下一个 step_group
        Optional<Integer> nextGroupOpt = allTasks.stream()
                .map(BizWoProcessTask::getStepGroup)
                .filter(g -> g != null && currentGroup != null && g > currentGroup)
                .distinct()
                .min(Integer::compareTo);
        if (nextGroupOpt.isEmpty()) return;

        // 从下一组中筛选外协工序（is_outsourceable = 1）
        Integer nextGroup = nextGroupOpt.get();
        List<BizWoProcessTask> outsourceableTasks = allTasks.stream()
                .filter(t -> nextGroup.equals(t.getStepGroup()))
                .filter(t -> t.getIsOutsourceable() != null && t.getIsOutsourceable() == 1)
                .collect(Collectors.toList());
        if (outsourceableTasks.isEmpty()) return;

        // 判断外协工序是否为末道工序组
        boolean isLastGroup = nextGroup.equals(maxGroup);

        // 并行工序场景: 外协可开数量应为上一组所有并行任务合格数的 MIN
        // (仅当全部并行任务都产出 N 件时, 才真正有 N 件半成品可流转到下一道外协)
        Integer prevGroup = reportedTask.getStepGroup();
        BigDecimal cumulativeQualified = allTasks.stream()
                .filter(t -> prevGroup != null && prevGroup.equals(t.getStepGroup()))
                .filter(t -> !"cancelled".equals(t.getStatus()))
                .map(t -> t.getQualifiedQty() != null ? t.getQualifiedQty() : BigDecimal.ZERO)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        if (cumulativeQualified.compareTo(BigDecimal.ZERO) <= 0) {
            log.debug("上一组并行工序 MIN 合格数=0, 暂不触发外协链路: workOrderId={}, prevGroup={}",
                    workOrderId, prevGroup);
            return;
        }

        for (BizWoProcessTask outsourceTask : outsourceableTasks) {
            try {
                // 1. 创建/获取发料物料（前道工序虚拟半成品）
                BizMaterial deliveryMat = virtualSemiService.getOrCreateVirtualMaterial(
                        wo.getProductId(), reportedTask.getStepName());

                // 2. 发料物料入库（使用默认外协仓库999，外协发料时出库）
                virtualSemiService.stockIn(deliveryMat.getId(), stockInQty, "work_order", workOrderId);

                // 3. 确定收料物料：末道工序=产品本身，非末道=虚拟半成品
                Long receiptMaterialId;
                if (isLastGroup) {
                    receiptMaterialId = wo.getProductId();
                } else {
                    BizMaterial receiptMat = virtualSemiService.getOrCreateVirtualMaterial(
                            wo.getProductId(), outsourceTask.getStepName());
                    receiptMaterialId = receiptMat.getId();
                }

                // 4. 创建或更新外协加工单（差额模式，draft状态待人工提交审批）
                createOrUpdateOutsourceOrder(wo, outsourceTask, deliveryMat, receiptMaterialId, cumulativeQualified);

            } catch (Exception e) {
                log.error("外协链路处理失败: workOrderId={}, outsourceTaskId={}, error={}", workOrderId, outsourceTask.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * 创建或更新外协加工单（差额模式）
     * 规则：
     *   - 存在 draft 状态的加工单 → 更新数量为当前工序累计合格数
     *   - 不存在 draft → 新建加工单，数量 = 累计合格数 - 已有非取消加工单数量之和（差额）
     */
    private void createOrUpdateOutsourceOrder(BizWorkOrder wo, BizWoProcessTask outsourceTask,
                                               BizMaterial virtualMat, Long receiptMaterialId,
                                               BigDecimal cumulativeQualifiedQty) {
        // 查找草稿状态的加工单
        BizOutsourceOrder draftOrder = orderMapper.selectOne(
                new LambdaQueryWrapper<BizOutsourceOrder>()
                        .eq(BizOutsourceOrder::getTaskId, outsourceTask.getId())
                        .eq(BizOutsourceOrder::getStatus, "draft")
                        .last("LIMIT 1"));

        if (draftOrder != null) {
            // 更新草稿加工单的数量
            draftOrder.setTotalQty(cumulativeQualifiedQty);
            if (draftOrder.getUnitPrice() != null) {
                draftOrder.setTotalAmount(draftOrder.getUnitPrice().multiply(cumulativeQualifiedQty));
            }
            orderMapper.updateById(draftOrder);
            log.info("更新外协加工单数量: orderId={}, quantity={}", draftOrder.getId(), cumulativeQualifiedQty);
        } else {
            // 差额计算：累计合格数 - 已有非取消加工单的数量之和
            List<BizOutsourceOrder> existingOrders = orderMapper.selectList(
                    new LambdaQueryWrapper<BizOutsourceOrder>()
                            .eq(BizOutsourceOrder::getTaskId, outsourceTask.getId())
                            .ne(BizOutsourceOrder::getStatus, "cancelled"));
            BigDecimal alreadyOrdered = existingOrders.stream()
                    .map(o -> o.getTotalQty() != null ? o.getTotalQty() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal deltaQty = cumulativeQualifiedQty.subtract(alreadyOrdered);
            if (deltaQty.compareTo(BigDecimal.ZERO) <= 0) {
                log.debug("无新增可外协数量: cumulative={}, alreadyOrdered={}", cumulativeQualifiedQty, alreadyOrdered);
                return;
            }

            // 新建加工单（draft 状态，待人工提交审批）
            BizOutsourceOrder order = new BizOutsourceOrder();
            order.setOrderNo(CodeUtil.generate("OO"));
            order.setProductId(wo.getProductId());
            order.setStepName(outsourceTask.getStepName());
            order.setTotalQty(deltaQty);
            order.setDeliveredQty(BigDecimal.ZERO);
            order.setQualifiedQty(BigDecimal.ZERO);
            order.setDeliveryQty(BigDecimal.ZERO);
            order.setDeliveryTaskQty(BigDecimal.ZERO);
            order.setDeliveryOutQty(BigDecimal.ZERO);
            order.setReceiptTaskQty(BigDecimal.ZERO);
            order.setReceiptInQty(BigDecimal.ZERO);
            order.setWorkOrderId(wo.getId());
            order.setTaskId(outsourceTask.getId());
            if (virtualMat != null) order.setVirtualMaterialId(virtualMat.getId());
            order.setReceiptMaterialId(receiptMaterialId);
            order.setDeliveryDate(outsourceTask.getEndTime() != null
                    ? outsourceTask.getEndTime().toLocalDate() : wo.getDeliveryDate());
            order.setStatus("draft");
            orderMapper.insert(order);

            // 更新工序任务的外协关联
            if (virtualMat != null) outsourceTask.setVirtualMaterialId(virtualMat.getId());
            outsourceTask.setOutsourceOrderId(order.getId());
            outsourceTask.setOutsourceStatus("ordered");
            outsourceTask.setIsOutsourced(1);
            processTaskMapper.updateById(outsourceTask);

            // 通知
            notificationService.notifyRole("production",
                    "自动创建外协加工单",
                    "工单[" + wo.getWoNo() + "]工序[" + outsourceTask.getStepName() + "]前道报工，已自动创建外协加工单(草稿)，数量: " + deltaQty,
                    "approval", "outsource_order", order.getId());

            log.info("创建外协加工单: orderNo={}, workOrderId={}, qty={} (cumulative={}, alreadyOrdered={})",
                    order.getOrderNo(), wo.getId(), deltaQty, cumulativeQualifiedQty, alreadyOrdered);
        }
    }

    /**
     * 外协收货入库任务处理后回调（由 BizInvTaskService.onOutsourceInTaskProcessed 调用）
     * 注意：库存的实际入库已由库存任务 process() 完成，此处只做业务状态同步。
     * - 末道工序: 更新工单完工数 + 同步计划/订单
     * - 非末道工序: 激活后道工序
     *
     * @param outsourceOrderId 外协加工单ID
     * @param qualifiedQty     本批次入库数量
     */
    @Transactional(rollbackFor = Exception.class)
    public void onOutsourceReceiptQcPassed(Long outsourceOrderId, BigDecimal qualifiedQty) {
        if (qualifiedQty == null || qualifiedQty.compareTo(BigDecimal.ZERO) <= 0) return;

        BizOutsourceOrder order = orderMapper.selectById(outsourceOrderId);
        if (order == null || order.getTaskId() == null) return;

        BizWoProcessTask outsourceTask = processTaskMapper.selectById(order.getTaskId());
        if (outsourceTask == null) return;

        BizWorkOrder wo = workOrderMapper.selectById(outsourceTask.getWorkOrderId());
        if (wo == null) return;

        // 更新外协工序任务的合格数
        BigDecimal totalQualified = outsourceTask.getQualifiedQty() != null
                ? outsourceTask.getQualifiedQty().add(qualifiedQty) : qualifiedQty;
        outsourceTask.setQualifiedQty(totalQualified);
        outsourceTask.setOutsourceStatus("completed");

        // 判断外协工序是否完成
        BigDecimal totalDone = totalQualified.add(
                outsourceTask.getDefectQty() != null ? outsourceTask.getDefectQty() : BigDecimal.ZERO);
        if (outsourceTask.getPlanQty() != null && totalDone.compareTo(outsourceTask.getPlanQty()) >= 0) {
            outsourceTask.setStatus("completed");
            outsourceTask.setEndTime(LocalDateTime.now());
        }
        processTaskMapper.updateById(outsourceTask);

        boolean isLastGroup = isLastProcessGroup(outsourceTask.getWorkOrderId(), outsourceTask.getStepGroup());

        if (isLastGroup) {
            // 末道工序：库存已由任务入库，此处只更新工单完工数
            wo.setCompletedQty(totalQualified);
            if ("completed".equals(outsourceTask.getStatus())) {
                boolean allCompleted = processTaskMapper.selectCount(
                        new LambdaQueryWrapper<BizWoProcessTask>()
                                .eq(BizWoProcessTask::getWorkOrderId, wo.getId())
                                .ne(BizWoProcessTask::getStatus, "cancelled")
                                .ne(BizWoProcessTask::getStatus, "completed")) == 0;
                if (allCompleted) {
                    wo.setStatus("completed");
                    workOrderMapper.updateById(wo);
                    if (wo.getPlanItemId() != null) {
                        try { productionPlanService.syncPlanItemCompletedQty(wo.getPlanItemId()); }
                        catch (Exception e) { log.warn("同步计划行项失败: planItemId={}", wo.getPlanItemId(), e); }
                    }
                    if (wo.getOrderId() != null) {
                        try { salesOrderService.syncOrderItemProducedQty(wo.getOrderId(), wo.getOrderItemId()); }
                        catch (Exception e) { log.warn("同步订单行进度失败: orderId={}", wo.getOrderId(), e); }
                    }
                } else {
                    workOrderMapper.updateById(wo);
                }
            } else {
                workOrderMapper.updateById(wo);
            }
            log.info("末道外协完工: workOrderId={}, qualifiedQty={}, totalCompleted={}", wo.getId(), qualifiedQty, totalQualified);
            // 末道外协完成后，生成成品入库任务（与常规末道工序保持一致）
            try {
                workReportService.tryCreateLastStepStockIn(outsourceTask.getWorkOrderId(), outsourceTask.getStepNo());
            } catch (Exception e) {
                log.warn("末道外协完成后触发成品入库失败: workOrderId={}", outsourceTask.getWorkOrderId(), e);
            }
        } else {
            // 非末道工序：库存已由任务入库，此处只激活后道工序
            Long receiptMaterialId = order.getReceiptMaterialId();
            if (receiptMaterialId == null) {
                BizMaterial outputVirtual = virtualSemiService.getOrCreateVirtualMaterial(
                        wo.getProductId(), outsourceTask.getStepName());
                receiptMaterialId = outputVirtual.getId();
            }
            activatePostOutsourceSteps(outsourceTask, receiptMaterialId);
            log.info("非末道外协完工，激活后道: workOrderId={}, outsourceOrderId={}, qualifiedQty={}",
                    wo.getId(), outsourceOrderId, qualifiedQty);
        }
    }

    /**
     * 判断是否为末道工序组
     */
    private boolean isLastProcessGroup(Long workOrderId, Integer stepGroup) {
        Integer maxGroup = processTaskMapper.selectList(
                        new LambdaQueryWrapper<BizWoProcessTask>()
                                .eq(BizWoProcessTask::getWorkOrderId, workOrderId)
                                .ne(BizWoProcessTask::getStatus, "cancelled"))
                .stream()
                .map(BizWoProcessTask::getStepGroup)
                .max(Integer::compareTo)
                .orElse(0);
        return stepGroup.equals(maxGroup);
    }

    /**
     * 激活外协工序之后的工序
     */
    private void activatePostOutsourceSteps(BizWoProcessTask outsourceTask, Long outputVirtualMaterialId) {
        List<BizWoProcessTask> remaining = processTaskMapper.selectList(
                new LambdaQueryWrapper<BizWoProcessTask>()
                        .eq(BizWoProcessTask::getWorkOrderId, outsourceTask.getWorkOrderId())
                        .gt(BizWoProcessTask::getStepGroup, outsourceTask.getStepGroup())
                        .eq(BizWoProcessTask::getStatus, "pending")
                        .orderByAsc(BizWoProcessTask::getStepGroup)
                        .orderByAsc(BizWoProcessTask::getStepNo));
        if (remaining.isEmpty()) return;

        // 检查当前外协工序所在组是否全部完成
        long unfinishedInGroup = processTaskMapper.selectCount(
                new LambdaQueryWrapper<BizWoProcessTask>()
                        .eq(BizWoProcessTask::getWorkOrderId, outsourceTask.getWorkOrderId())
                        .eq(BizWoProcessTask::getStepGroup, outsourceTask.getStepGroup())
                        .notIn(BizWoProcessTask::getStatus, "completed", "cancelled"));
        if (unfinishedInGroup > 0) return;

        // 激活下一组
        Integer nextGroup = remaining.get(0).getStepGroup();
        boolean anyNonOutsourceActivated = false;
        for (BizWoProcessTask nextTask : remaining) {
            if (!nextTask.getStepGroup().equals(nextGroup)) break;
            // 如果下一道也是外协工序，不激活（由自身的外协链路处理）
            if (nextTask.getIsOutsourceable() != null && nextTask.getIsOutsourceable() == 1) continue;
            nextTask.setStatus("ready");
            nextTask.setVirtualMaterialId(outputVirtualMaterialId); // 关联外协输出虚拟半成品
            processTaskMapper.updateById(nextTask);
            anyNonOutsourceActivated = true;
        }

        // 如果下一组全是外协工序（没有激活任何非外协任务），
        // 需为这些外协工序触发外协申请创建（因为没有前道非外协报工来触发handlePrevStepReported）
        if (!anyNonOutsourceActivated) {
            BizWorkOrder wo = workOrderMapper.selectById(outsourceTask.getWorkOrderId());
            if (wo != null) {
                BigDecimal prevQualified = outsourceTask.getQualifiedQty() != null
                        ? outsourceTask.getQualifiedQty() : BigDecimal.ZERO;

                // 判断下一组外协工序是否末道
                Integer maxGroupAll = remaining.stream()
                        .map(BizWoProcessTask::getStepGroup)
                        .max(Integer::compareTo).orElse(0);
                boolean nextIsLast = nextGroup.equals(maxGroupAll);

                for (BizWoProcessTask nextTask : remaining) {
                    if (!nextTask.getStepGroup().equals(nextGroup)) break;
                    if (nextTask.getIsOutsourceable() != null && nextTask.getIsOutsourceable() == 1) {
                        try {
                            // 发料物料 = 当前外协工序的输出
                            BizMaterial deliveryMat = virtualSemiService.getOrCreateVirtualMaterial(
                                    wo.getProductId(), outsourceTask.getStepName());
                            // 收料物料：末道=产品，非末道=虚拟半成品
                            Long nextReceiptMaterialId;
                            if (nextIsLast) {
                                nextReceiptMaterialId = wo.getProductId();
                            } else {
                                BizMaterial receiptMat = virtualSemiService.getOrCreateVirtualMaterial(
                                        wo.getProductId(), nextTask.getStepName());
                                nextReceiptMaterialId = receiptMat.getId();
                            }
                            createOrUpdateOutsourceOrder(wo, nextTask, deliveryMat, nextReceiptMaterialId, prevQualified);
                        } catch (Exception e) {
                            log.warn("连续外协组触发失败: nextTaskId={}", nextTask.getId(), e);
                        }
                    }
                }
            }
        }
    }

    /**
     * 外协申请转为加工单时调用：更新申请的已申请数量
     */
    @Transactional(rollbackFor = Exception.class)
    public void onOrderCreatedFromRequest(Long requestId, BigDecimal orderQty) {
        BizOutsourceRequest request = requestMapper.selectById(requestId);
        if (request == null) return;

        BigDecimal newRequested = request.getRequestedQty() != null
                ? request.getRequestedQty().add(orderQty) : orderQty;
        request.setRequestedQty(newRequested);
        request.setStatus("ordered");
        requestMapper.updateById(request);

        // 更新工序任务的外协状态
        if (request.getTaskId() != null) {
            BizWoProcessTask task = processTaskMapper.selectById(request.getTaskId());
            if (task != null) {
                task.setOutsourceStatus("ordered");
                processTaskMapper.updateById(task);
            }
        }
    }

    /**
     * 获取某工序的最大可报工数量（受限于外协虚拟半成品库存）
     * 返回 null 表示无限制
     */
    public BigDecimal getMaxReportableQty(BizWoProcessTask task) {
        if (task.getVirtualMaterialId() == null) return null;
        return virtualSemiService.getAvailableQty(task.getVirtualMaterialId());
    }

}
