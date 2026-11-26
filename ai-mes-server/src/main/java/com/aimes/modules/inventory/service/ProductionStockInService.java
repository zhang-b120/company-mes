package com.aimes.modules.inventory.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.modules.master.entity.BizMaterial;
import com.aimes.modules.master.mapper.BizMaterialMapper;
import com.aimes.modules.mrp.service.BizProductionPlanService;
import com.aimes.modules.order.entity.BizSalesOrder;
import com.aimes.modules.order.entity.BizSalesOrderItem;
import com.aimes.modules.order.mapper.BizSalesOrderItemMapper;
import com.aimes.modules.order.mapper.BizSalesOrderMapper;
import com.aimes.modules.production.entity.BizWorkOrder;
import com.aimes.modules.production.entity.BizWoProcessTask;
import com.aimes.modules.production.mapper.BizWorkOrderMapper;
import com.aimes.modules.production.mapper.BizWoProcessTaskMapper;
import com.aimes.modules.inventory.entity.BizInvTask;
import com.aimes.modules.inventory.entity.BizInvTaskItem;
import com.aimes.modules.inventory.mapper.BizInvTaskItemMapper;
import com.aimes.modules.inventory.mapper.BizInvTaskMapper;
import com.aimes.modules.quality.entity.BizQcTask;
import com.aimes.modules.quality.mapper.BizQcTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 成品入库确认服务
 * 成品质检通过后工单标记为 pending_stock_in，由仓库人员手动确认入库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductionStockInService {

    private final BizWorkOrderMapper workOrderMapper;
    private final BizWoProcessTaskMapper processTaskMapper;
    private final BizQcTaskMapper qcTaskMapper;
    private final BizMaterialMapper materialMapper;
    private final BizSalesOrderItemMapper salesOrderItemMapper;
    private final BizSalesOrderMapper salesOrderMapper;
    private final InventoryService inventoryService;
    private final BizProductionPlanService productionPlanService;
    private final BizInvTaskMapper invTaskMapper;
    private final BizInvTaskItemMapper invTaskItemMapper;

    /**
     * 查询待入库工单列表
     * 条件: 工单状态=completed（生产完成）且库存任务未全部生成
     */
    public List<Map<String, Object>> getPendingStockIn() {
        List<BizWorkOrder> workOrders = workOrderMapper.selectList(
                new LambdaQueryWrapper<BizWorkOrder>()
                        .eq(BizWorkOrder::getStatus, "completed")
                        .and(w -> w.isNull(BizWorkOrder::getInvTaskStatus)
                                .or().eq(BizWorkOrder::getInvTaskStatus, "partial"))
                        .orderByDesc(BizWorkOrder::getUpdateTime));

        // 批量加载产品名称
        Set<Long> productIds = workOrders.stream()
                .map(BizWorkOrder::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, BizMaterial> materialMap = productIds.isEmpty() ? new HashMap<>() :
                materialMapper.selectBatchIds(productIds).stream()
                        .collect(Collectors.toMap(BizMaterial::getId, m -> m, (a, b) -> a));

        return workOrders.stream().map(wo -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("workOrderId", wo.getId());
            m.put("woNo", wo.getWoNo());
            m.put("productId", wo.getProductId());
            BizMaterial material = materialMap.get(wo.getProductId());
            m.put("productName", material != null ? material.getMaterialName() : "");
            m.put("planQty", wo.getPlanQty());
            m.put("completedQty", wo.getCompletedQty());

            // 查询成品质检合格数
            BizQcTask qcTask = qcTaskMapper.selectOne(
                    new LambdaQueryWrapper<BizQcTask>()
                            .eq(BizQcTask::getQcType, "final")
                            .eq(BizQcTask::getWorkOrderId, wo.getId())
                            .eq(BizQcTask::getResult, "pass")
                            .orderByDesc(BizQcTask::getCreateTime)
                            .last("LIMIT 1"));
            BigDecimal passQty = qcTask != null ? qcTask.getPassQty() : wo.getCompletedQty();
            m.put("passQty", passQty);
            m.put("qcResult", qcTask != null ? qcTask.getResult() : null);

            // 默认入库仓库
            m.put("defaultWarehouseId", material != null ? material.getDefaultWarehouseId() : null);

            m.put("updateTime", wo.getUpdateTime());
            return m;
        }).collect(Collectors.toList());
    }

    /**
     * 确认成品入库（通过库存任务流程，不再直接入库）
     * 创建 production_in 库存任务，由仓库人员通过库存任务处理完成实际入库
     */
    @Transactional(rollbackFor = Exception.class)
    public void confirmStockIn(Long workOrderId, Long warehouseId, BigDecimal qty, String batchNo) {
        createBatchStockInTask(workOrderId, qty, warehouseId);
        log.info("成品入库已转为库存任务: workOrderId={}, qty={}, warehouseId={}", workOrderId, qty, warehouseId);
    }

    /**
     * 创建批次成品入库任务（批量入库，允许多次创建）
     * 每次调用生成一条新的 production_in 库存任务，并累计 stockInTaskQty
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createBatchStockInTask(Long woId, BigDecimal qty, Long warehouseId) {
        BizWorkOrder wo = workOrderMapper.selectById(woId);
        if (wo == null) throw new BusinessException("工单不存在");
        if (wo.getProductId() == null) throw new BusinessException("工单未关联产品");

        BigDecimal completedQty = wo.getCompletedQty() != null ? wo.getCompletedQty() : BigDecimal.ZERO;
        BigDecimal alreadyTasked = wo.getStockInTaskQty() != null ? wo.getStockInTaskQty() : BigDecimal.ZERO;
        BigDecimal available = completedQty.subtract(alreadyTasked);
        if (qty == null || qty.compareTo(java.math.BigDecimal.ZERO) <= 0) throw new BusinessException("入库数量必须大于0");
        if (qty.compareTo(available) > 0) {
            throw new BusinessException("入库任务数量(" + qty + ")超过可入库剩余数量(" + available + ")");
        }

        BizMaterial product = materialMapper.selectById(wo.getProductId());
        Long effectiveWhId = warehouseId != null ? warehouseId
                : (product != null ? product.getDefaultWarehouseId() : null);

        // 创建新的 production_in 库存任务（每次批量入库单独一张任务单）
        BizInvTask task = new BizInvTask();
        task.setTaskNo(com.aimes.common.utils.CodeUtil.generate("INK"));
        task.setInvType("production_in");
        task.setRelatedOrderNo(wo.getWoNo());
        task.setWarehouseId(effectiveWhId);
        task.setStatus("pending");
        task.setSourceType("work_order");
        task.setSourceId(woId);
        task.setApplicantId(com.aimes.common.utils.SecurityUtil.getCurrentUserId());
        task.setRemark("工单[" + wo.getWoNo() + "]成品批次入库");
        invTaskMapper.insert(task);

        BizInvTaskItem taskItem = new BizInvTaskItem();
        taskItem.setTaskId(task.getId());
        taskItem.setMaterialId(wo.getProductId());
        taskItem.setMaterialNo(product != null ? product.getMaterialNo() : null);
        taskItem.setMaterialName(product != null ? product.getMaterialName() : null);
        taskItem.setSpec(product != null ? product.getSpec() : null);
        taskItem.setUnit(product != null ? product.getUnit() : null);
        taskItem.setQuantity(qty);
        taskItem.setProcessedQty(BigDecimal.ZERO);
        taskItem.setWarehouseId(effectiveWhId);
        invTaskItemMapper.insert(taskItem);

        // 累计已生成入库任务数量，更新库存任务状态
        BigDecimal newTaskQty = alreadyTasked.add(qty);
        wo.setStockInTaskQty(newTaskQty);
        syncInvTaskStatus(wo, completedQty, newTaskQty);
        workOrderMapper.updateById(wo);

        log.info("批次入库任务已创建: woId={}, taskId={}, qty={}", woId, task.getId(), qty);
        return task.getId();
    }

    /**
     * 每批次入库任务处理后联动回调（替代 onTaskCompleted，支持批量分次入库）
     * 累计 stockInQty，达到 planQty 时推进工单状态，并同步销售订单已生产数量
     */
    @Transactional(rollbackFor = Exception.class)
    public void onBatchStockIn(Long workOrderId, BigDecimal batchQty) {
        if (batchQty == null || batchQty.compareTo(BigDecimal.ZERO) <= 0) return;
        BizWorkOrder wo = workOrderMapper.selectById(workOrderId);
        if (wo == null) return;

        // 累计已确认入库数量
        BigDecimal newStockInQty = (wo.getStockInQty() != null ? wo.getStockInQty() : BigDecimal.ZERO).add(batchQty);
        wo.setStockInQty(newStockInQty);

        // 入库完成后检查工单状态 → 如果完成数量 >= 计划数量，设为 completed
        BigDecimal completedQty = wo.getCompletedQty() != null ? wo.getCompletedQty() : BigDecimal.ZERO;
        if ("in_progress".equals(wo.getStatus())
                && completedQty.compareTo(BigDecimal.ZERO) > 0
                && wo.getPlanQty() != null
                && completedQty.compareTo(wo.getPlanQty()) >= 0) {
            wo.setStatus("completed");
            log.info("工单完工数量已达计划，状态更新为 completed: workOrderId={}", workOrderId);

            // 修复可能滞留在 pending_qc 的工序任务状态
            List<BizWoProcessTask> pendingQcTasks = processTaskMapper.selectList(
                    new LambdaQueryWrapper<BizWoProcessTask>()
                            .eq(BizWoProcessTask::getWorkOrderId, workOrderId)
                            .eq(BizWoProcessTask::getStatus, "pending_qc"));
            for (BizWoProcessTask pt : pendingQcTasks) {
                pt.setStatus("completed");
                pt.setQcStatus("pass");
                pt.setEndTime(java.time.LocalDateTime.now());
                processTaskMapper.updateById(pt);
                log.info("修复工序任务状态: taskId={}, stepName={}", pt.getId(), pt.getStepName());
            }
        }

        workOrderMapper.updateById(wo);
        log.info("成品入库数量更新: workOrderId={}, batchQty={}, totalStockInQty={}", workOrderId, batchQty, newStockInQty);

        // 同步销售订单行项的已生产数量（requirement 5）
        if (wo.getOrderItemId() != null) {
            BizSalesOrderItem item = salesOrderItemMapper.selectById(wo.getOrderItemId());
            if (item != null) {
                // 更新已入库数量（producedQty 已由报工完工时的 syncOrderItemProducedQty 维护，此处不再重复累加）
                BigDecimal currentWarehouseIn = item.getWarehouseInQty() != null ? item.getWarehouseInQty() : BigDecimal.ZERO;
                item.setWarehouseInQty(currentWarehouseIn.add(batchQty));
                salesOrderItemMapper.updateById(item);

                if (item.getOrderId() != null) {
                    try {
                        List<BizSalesOrderItem> allItems = salesOrderItemMapper.selectList(
                                new LambdaQueryWrapper<BizSalesOrderItem>()
                                        .eq(BizSalesOrderItem::getOrderId, item.getOrderId()));
                        boolean allProduced = !allItems.isEmpty() && allItems.stream().allMatch(i -> {
                            BigDecimal p = i.getProducedQty() != null ? i.getProducedQty() : BigDecimal.ZERO;
                            return p.compareTo(i.getQuantity()) >= 0;
                        });
                        if (allProduced) {
                            BizSalesOrder order = salesOrderMapper.selectById(item.getOrderId());
                            if (order != null && "in_production".equals(order.getStatus())) {
                                order.setStatus("completed");
                                salesOrderMapper.updateById(order);
                                log.info("所有产品入库完成，订单状态推进为 completed: orderId={}", item.getOrderId());
                            }
                        }
                    } catch (Exception e) {
                        log.error("检查订单完工状态失败: orderId={}", item.getOrderId(), e);
                    }
                }
            }
        }

        // 回调生产计划
        if (wo.getPlanItemId() != null) {
            try {
                productionPlanService.onWorkOrderCompleted(wo.getPlanItemId(), batchQty);
            } catch (Exception e) {
                log.warn("回调生产计划失败: planItemId={}", wo.getPlanItemId(), e);
            }
        }

        log.info("批次入库联动完成: workOrderId={}, batchQty={}, totalStockInQty={}", workOrderId, batchQty, newStockInQty);
    }

    /**
     * 同步工单的库存任务状态（invTaskStatus）
     * 规则: completedQty > 0 && taskQty > 0 时:
     *   taskQty >= completedQty → generated（全部生成）
     *   taskQty < completedQty  → partial（部分生成）
     * completedQty <= 0 或 taskQty <= 0 → null（未生成）
     */
    public void syncInvTaskStatus(BizWorkOrder wo, BigDecimal completedQty, BigDecimal taskQty) {
        if (completedQty == null) completedQty = BigDecimal.ZERO;
        if (taskQty == null) taskQty = BigDecimal.ZERO;

        if (completedQty.compareTo(BigDecimal.ZERO) > 0 && taskQty.compareTo(BigDecimal.ZERO) > 0) {
            wo.setInvTaskStatus(taskQty.compareTo(completedQty) >= 0 ? "generated" : "partial");
        } else {
            wo.setInvTaskStatus(null);
        }
    }
}
