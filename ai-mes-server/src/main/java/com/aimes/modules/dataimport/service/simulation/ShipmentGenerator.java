package com.aimes.modules.dataimport.service.simulation;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.utils.CodeUtil;
import com.aimes.modules.inventory.entity.BizInvTask;
import com.aimes.modules.inventory.entity.BizInvTaskItem;
import com.aimes.modules.inventory.entity.BizInvTransaction;
import com.aimes.modules.inventory.mapper.BizInvTaskMapper;
import com.aimes.modules.inventory.mapper.BizInvTaskItemMapper;
import com.aimes.modules.inventory.mapper.BizInvTransactionMapper;
import com.aimes.modules.master.entity.BizMaterial;
import com.aimes.modules.master.mapper.BizMaterialMapper;
import com.aimes.modules.order.entity.BizSalesOrder;
import com.aimes.modules.order.entity.BizSalesOrderItem;
import com.aimes.modules.order.entity.BizShipmentRecord;
import com.aimes.modules.order.mapper.BizSalesOrderItemMapper;
import com.aimes.modules.order.mapper.BizSalesOrderMapper;
import com.aimes.modules.order.mapper.BizShipmentRecordMapper;
import com.aimes.modules.production.entity.BizWoProcessTask;
import com.aimes.modules.production.entity.BizWorkOrder;
import com.aimes.modules.production.mapper.BizWoProcessTaskMapper;
import com.aimes.modules.production.mapper.BizWorkOrderMapper;
import com.aimes.modules.quality.mapper.BizQcTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ④ 销售发货生成器
 * 基于已完成的工单，生成：
 * 1. 成品入库(production_in) — 库存任务+明细+流水
 * 2. 回写订单行项 producedQty / warehouseInQty
 * 3. 发货记录(biz_shipment_record)
 * 4. 销售出库(sales_out) — 库存任务+明细+流水
 * 5. 回写订单行项 shippedQty + 订单状态推进
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ShipmentGenerator {

    private final BizWorkOrderMapper workOrderMapper;
    private final BizWoProcessTaskMapper processTaskMapper;
    private final BizQcTaskMapper qcTaskMapper;
    private final BizSalesOrderMapper salesOrderMapper;
    private final BizSalesOrderItemMapper salesOrderItemMapper;
    private final SalesOrderStatusHelper salesOrderStatusHelper;
    private final BizShipmentRecordMapper shipmentRecordMapper;
    private final BizInvTaskMapper invTaskMapper;
    private final BizInvTaskItemMapper invTaskItemMapper;
    private final BizInvTransactionMapper invTransactionMapper;
    private final BizMaterialMapper materialMapper;

    /**
     * 扫描预览：统计可发货的工单数
     */
    public Map<String, Object> generate(SimConfig cfg) {
        List<BizWorkOrder> completedWos = workOrderMapper.selectList(
                new LambdaQueryWrapper<BizWorkOrder>()
                        .eq(BizWorkOrder::getDeleted, 0)
                        .eq(BizWorkOrder::getStatus, "completed")
                        .gt(BizWorkOrder::getCompletedQty, 0));

        // 统计可发货的工单（有orderId和orderItemId）
        long shippableCount = completedWos.stream()
                .filter(wo -> wo.getOrderId() != null && wo.getOrderItemId() != null)
                .count();

        // 统计关联的销售订单数
        Set<Long> orderIds = new HashSet<>();
        completedWos.stream()
                .filter(wo -> wo.getOrderId() != null)
                .forEach(wo -> orderIds.add(wo.getOrderId()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("completedWorkOrders", completedWos.size());
        result.put("shippableWorkOrders", shippableCount);
        result.put("relatedOrders", orderIds.size());
        result.put("shipDelayDays", cfg.getShipDelayDaysMin() + "~" + cfg.getShipDelayDaysMax());
        result.put("totalRows", shippableCount);
        return result;
    }

    public void exportZip(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        throw new BusinessException("销售发货不需要导出，请直接执行一键发货");
    }

    @Transactional
    public Map<String, Object> importData() {
        // 传默认 SimConfig 保证 cutoffDate(=今天) 过滤生效,否则会生成未来日期的发货
        return importData(new SimConfig());
    }

    public Map<String, Object> importData(SimConfig cfg) {
        // 改成串行 + 预加载: 远程 MySQL 慢网络下,parallelStream 并行度=20 时几百个 query 同时挤进
        // 80 条连接,任何一条卡顿都会被 Hikari 标 broken,造成连环 Communications link failure。
        // 串行流 + 一次性预加载 product/order/orderItem map,把循环里 selectById 改成查 map,
        // 显著减少 DB 调用次数。原 @Transactional 移除 — 大批量并行场景下,
        // 单线程大事务反而长时间持锁;改为每条 mapper 自动提交,跨工单失败互不影响,
        // 错误收集到 errors 数组返回(模拟数据可重跑,不强求强一致)
        int stockInCount = 0;
        int shipmentCount = 0;
        List<String> errors = new ArrayList<>();

        List<BizWorkOrder> allWo = workOrderMapper.selectList(
                new LambdaQueryWrapper<BizWorkOrder>()
                        .eq(BizWorkOrder::getDeleted, 0)
                        .eq(BizWorkOrder::getStatus, "completed"));

        // ========== 预加载: 把循环里的 selectById 全部干掉,远程 MySQL 单查询 1 次 vs 几千次的差距 ==========
        // 1) 产品 map
        Set<Long> productIds = allWo.stream().map(BizWorkOrder::getProductId)
                .filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        Map<Long, BizMaterial> productMap = productIds.isEmpty() ? Map.of()
                : materialMapper.selectBatchIds(productIds).stream()
                        .collect(java.util.stream.Collectors.toMap(BizMaterial::getId, m -> m, (a, b) -> a));
        // 2) 销售订单 / 订单行项 map
        Set<Long> orderIds = allWo.stream().map(BizWorkOrder::getOrderId)
                .filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        Map<Long, BizSalesOrder> orderMap = orderIds.isEmpty() ? Map.of()
                : salesOrderMapper.selectBatchIds(orderIds).stream()
                        .collect(java.util.stream.Collectors.toMap(BizSalesOrder::getId, o -> o, (a, b) -> a));
        Set<Long> itemIds = allWo.stream().map(BizWorkOrder::getOrderItemId)
                .filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        Map<Long, BizSalesOrderItem> itemMap = itemIds.isEmpty() ? new HashMap<>()
                : salesOrderItemMapper.selectBatchIds(itemIds).stream()
                        .collect(java.util.stream.Collectors.toMap(BizSalesOrderItem::getId, i -> i, (a, b) -> a, HashMap::new));
        // 3) 已有的 production_in 库存任务: 用 (woId) 去重, 顺带带出 createTime
        Map<Long, LocalDateTime> existingStockInTimeMap = new HashMap<>();
        if (!allWo.isEmpty()) {
            Set<Long> woIds = allWo.stream().map(BizWorkOrder::getId).collect(java.util.stream.Collectors.toSet());
            List<BizInvTask> existingInTasks = invTaskMapper.selectList(
                    new LambdaQueryWrapper<BizInvTask>()
                            .in(BizInvTask::getSourceId, woIds)
                            .eq(BizInvTask::getSourceType, "work_order")
                            .eq(BizInvTask::getInvType, "production_in")
                            .eq(BizInvTask::getDeleted, 0));
            existingInTasks.forEach(t -> existingStockInTimeMap.merge(t.getSourceId(), t.getCreateTime(),
                    (a, b) -> a != null ? a : b));
        }
        // 4) 已有的发货记录: 按 orderItemId 去重
        Set<Long> existingShipmentItemIds = new HashSet<>();
        if (!itemIds.isEmpty()) {
            shipmentRecordMapper.selectList(new LambdaQueryWrapper<BizShipmentRecord>()
                    .in(BizShipmentRecord::getOrderItemId, itemIds)
                    .eq(BizShipmentRecord::getDeleted, 0))
                    .forEach(s -> existingShipmentItemIds.add(s.getOrderItemId()));
        }
        // 5) 同 orderItemId 下的所有工单 — producedQty 重算用
        Map<Long, List<BizWorkOrder>> wosByItemId = allWo.stream()
                .filter(w -> w.getOrderItemId() != null
                        && (w.getStatus() == null || !"cancelled".equals(w.getStatus())))
                .collect(java.util.stream.Collectors.groupingBy(BizWorkOrder::getOrderItemId));
        // 6) 工单的最后质检时间 — 一次性查所有 woId 的 max(inspectedAt) / max(endTime)
        Map<Long, LocalDateTime> woLastQcTimeMap = preloadWoLastQcTime(allWo);

        // ========== 1. 成品入库(production_in) — 串行流 ==========
        Map<Long, LocalDateTime> woStockInTimeMap = new HashMap<>();
        woStockInTimeMap.putAll(existingStockInTimeMap);
        for (BizWorkOrder wo : allWo) {
            try {
                BigDecimal completedQty = wo.getCompletedQty();
                if (completedQty == null || completedQty.compareTo(BigDecimal.ZERO) <= 0) continue;

                // 检查是否已有成品入库 — 走预查询的 existingStockInTimeMap
                if (existingStockInTimeMap.containsKey(wo.getId())) continue;

                // 获取产品信息 — 走预加载的 productMap
                BizMaterial product = wo.getProductId() != null ? productMap.get(wo.getProductId()) : null;
                if (product == null) continue;

                // 入库日期 = 质检完成日 + 1 工作日,时间在工作时段内随机 — 用预加载的 woLastQcTimeMap
                LocalDateTime lastQcTime = woLastQcTimeMap.getOrDefault(wo.getId(), wo.getCreateTime());
                LocalDate stockInDate = lastQcTime != null
                        ? SimTimeHelper.addWorkingDays(lastQcTime.toLocalDate(), 1, cfg)
                        : LocalDate.now();
                LocalDateTime stockInTime = SimTimeHelper.randomTime(stockInDate, cfg);
                if (stockInTime == null) continue;
                woStockInTimeMap.put(wo.getId(), stockInTime);

                Long warehouseId = product.getDefaultWarehouseId() != null ? product.getDefaultWarehouseId() : 1L;

                // 生产入库的操作人:接收员 → 创建人 → 工单创建人 → admin(1) 兜底
                Long stockInUserId = cfg != null ? cfg.pickReceiver() : null;
                if (stockInUserId == null && cfg != null) stockInUserId = cfg.pickCreator();
                if (stockInUserId == null) stockInUserId = wo.getCreateBy();
                if (stockInUserId == null) stockInUserId = 1L;

                // 库存任务
                BizInvTask invTask = new BizInvTask();
                invTask.setTaskNo(CodeUtil.generate("INK"));
                invTask.setRelatedOrderNo(wo.getWoNo());
                invTask.setInvType("production_in");
                invTask.setWarehouseId(warehouseId);
                invTask.setStatus("completed");
                invTask.setSourceType("work_order");
                invTask.setSourceId(wo.getId());
                invTask.setApplicantId(stockInUserId);
                invTask.setCreateBy(stockInUserId);
                invTask.setUpdateBy(stockInUserId);
                invTask.setCreateTime(stockInTime);
                invTask.setUpdateTime(stockInTime);
                invTaskMapper.insert(invTask);

                // 库存任务明细
                BizInvTaskItem invItem = new BizInvTaskItem();
                invItem.setTaskId(invTask.getId());
                invItem.setMaterialId(product.getId());
                invItem.setMaterialNo(product.getMaterialNo());
                invItem.setMaterialName(product.getMaterialName());
                invItem.setSpec(product.getSpec());
                invItem.setUnit(product.getUnit());
                invItem.setQuantity(completedQty);
                invItem.setProcessedQty(completedQty);
                invItem.setWarehouseId(warehouseId);
                invTaskItemMapper.insert(invItem);

                // 库存流水
                BizInvTransaction tx = new BizInvTransaction();
                tx.setMaterialId(product.getId());
                tx.setWarehouseId(warehouseId);
                tx.setTransactionType("production_in");
                tx.setQuantity(completedQty);
                tx.setRelatedOrderType("work_order");
                tx.setRelatedOrderId(wo.getId());
                tx.setCreateTime(stockInTime);
                tx.setUpdateTime(stockInTime);
                invTransactionMapper.insert(tx);

                // 回写工单的入库字段
                wo.setStockInTaskQty(completedQty);
                wo.setStockInQty(completedQty);
                wo.setInvTaskStatus("generated");
                workOrderMapper.updateById(wo);

                // 回写订单行项的 producedQty 和 warehouseInQty — 走预加载的 itemMap / wosByItemId
                if (wo.getOrderItemId() != null) {
                    BizSalesOrderItem oi = itemMap.get(wo.getOrderItemId());
                    if (oi != null) {
                        // producedQty = SUM(关联工单.completedQty) — 用预分组的 wosByItemId
                        List<BizWorkOrder> relatedWos = wosByItemId.getOrDefault(wo.getOrderItemId(), List.of());
                        BigDecimal totalProduced = relatedWos.stream()
                                .map(w -> w.getCompletedQty() != null ? w.getCompletedQty() : BigDecimal.ZERO)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                        oi.setProducedQty(totalProduced);

                        BigDecimal currentInQty = oi.getWarehouseInQty() != null ? oi.getWarehouseInQty() : BigDecimal.ZERO;
                        oi.setWarehouseInQty(currentInQty.add(completedQty));
                        salesOrderItemMapper.updateById(oi);
                        // 更新 itemMap 缓存,后续阶段 2 从这里读最新值
                    }
                }

                stockInCount++;
            } catch (Exception e) {
                errors.add("成品入库失败(woId=" + wo.getId() + "): " + e.getMessage());
            }
        } // end for (production_in)

        // ========== 2. 销售发货 + 出库 — 串行流 ==========
        Set<Long> processedOrderIds = new HashSet<>();
        for (BizWorkOrder wo : allWo) {
            try {
                if (wo.getOrderId() == null || wo.getOrderItemId() == null) continue;
                BigDecimal completedQty = wo.getCompletedQty();
                if (completedQty == null || completedQty.compareTo(BigDecimal.ZERO) <= 0) continue;

                // 检查是否已有发货记录 — 走预查询的 existingShipmentItemIds
                if (existingShipmentItemIds.contains(wo.getOrderItemId())) continue;

                BizSalesOrderItem oi = itemMap.get(wo.getOrderItemId());
                if (oi == null || oi.getProducedQty() == null || oi.getProducedQty().compareTo(BigDecimal.ZERO) <= 0) continue;

                // 查询销售订单编号 — 走预加载的 orderMap
                BizSalesOrder relOrder = orderMap.get(wo.getOrderId());
                String orderNo = relOrder != null ? relOrder.getOrderNo() : "";

                BizMaterial product = wo.getProductId() != null ? productMap.get(wo.getProductId()) : null;
                if (product == null) continue;

                Long warehouseId = product.getDefaultWarehouseId() != null ? product.getDefaultWarehouseId() : 1L;

                // 发货日期 = 入库日期 + 发货延迟工作日,时间在工作时段内随机
                LocalDateTime stockInTimeSrc = woStockInTimeMap.get(wo.getId());
                int shipDelayDays = cfg != null ? cfg.randomShipDelayDays() : 2;
                LocalDate shipDate = stockInTimeSrc != null
                        ? SimTimeHelper.addWorkingDays(stockInTimeSrc.toLocalDate(), shipDelayDays, cfg)
                        : SimTimeHelper.addWorkingDays(LocalDate.now(), shipDelayDays, cfg);
                // 钳到 today: 入库太晚导致 shipDate > today 时, 强制改为今天发货,
                // 否则 randomTime 直接返回 null, 这条工单的发货就被跳过(已超期订单永远发不出)
                LocalDate today = LocalDate.now();
                if (shipDate.isAfter(today)) shipDate = today;
                LocalDateTime shipTime = SimTimeHelper.randomTime(shipDate, cfg);
                if (shipTime == null) {
                    // 当前时刻还没到工作开始时间 / 入库时间 + 1 小时已超过 now → 用 stockIn + 1h 兜底
                    if (stockInTimeSrc != null) {
                        LocalDateTime fallback = stockInTimeSrc.plusHours(1);
                        LocalDateTime now = LocalDateTime.now();
                        if (fallback.isAfter(now)) fallback = now.minusMinutes(5);
                        if (!fallback.isBefore(stockInTimeSrc)) shipTime = fallback;
                    }
                    if (shipTime == null) continue;
                }

                BigDecimal shippedQty = oi.getProducedQty();

                // 回写订单行的shippedQty
                oi.setShippedQty(shippedQty);
                salesOrderItemMapper.updateById(oi);

                // 生成发货记录
                BizShipmentRecord shipment = new BizShipmentRecord();
                shipment.setOrderId(wo.getOrderId());
                shipment.setOrderItemId(wo.getOrderItemId());
                shipment.setProductId(wo.getProductId());
                shipment.setShippedQty(shippedQty);
                shipment.setShipmentNo(CodeUtil.generate("SH"));
                shipment.setShippedAt(shipTime);
                shipment.setStatus("shipped");
                shipment.setCreateTime(shipTime);
                shipment.setUpdateTime(shipTime);
                shipmentRecordMapper.insert(shipment);

                // 销售出库库存任务
                // 销售出库操作人:出库员 → 创建人 → 工单创建人 → admin(1) 兜底
                Long shipOpUserId = cfg != null ? cfg.pickReceiver() : null;
                if (shipOpUserId == null && cfg != null) shipOpUserId = cfg.pickCreator();
                if (shipOpUserId == null) shipOpUserId = wo.getCreateBy();
                if (shipOpUserId == null) shipOpUserId = 1L;

                BizInvTask shipTask = new BizInvTask();
                shipTask.setTaskNo(CodeUtil.generate("INK"));
                shipTask.setRelatedOrderNo(orderNo);
                shipTask.setInvType("sales_out");
                shipTask.setWarehouseId(warehouseId);
                shipTask.setStatus("completed");
                shipTask.setSourceType("sales_order");
                shipTask.setSourceId(wo.getOrderId());
                shipTask.setApplicantId(shipOpUserId);
                shipTask.setCreateBy(shipOpUserId);
                shipTask.setUpdateBy(shipOpUserId);
                shipTask.setCreateTime(shipTime);
                shipTask.setUpdateTime(shipTime);
                invTaskMapper.insert(shipTask);

                BizInvTaskItem shipItem = new BizInvTaskItem();
                shipItem.setTaskId(shipTask.getId());
                shipItem.setMaterialId(product.getId());
                shipItem.setMaterialNo(product.getMaterialNo());
                shipItem.setMaterialName(product.getMaterialName());
                shipItem.setUnit(product.getUnit());
                shipItem.setQuantity(shippedQty);
                shipItem.setProcessedQty(shippedQty);
                shipItem.setUnitPrice(oi.getUnitPrice());
                shipItem.setWarehouseId(warehouseId);
                invTaskItemMapper.insert(shipItem);

                BizInvTransaction shipTx = new BizInvTransaction();
                shipTx.setMaterialId(product.getId());
                shipTx.setWarehouseId(warehouseId);
                shipTx.setTransactionType("sales_out");
                shipTx.setQuantity(shippedQty.negate()); // 出库为负
                shipTx.setRelatedOrderType("sales_order");
                shipTx.setRelatedOrderId(wo.getOrderId());
                shipTx.setCreateTime(shipTime);
                shipTx.setUpdateTime(shipTime);
                invTransactionMapper.insert(shipTx);

                shipmentCount++;

                // 收集订单 ID, 循环结束后批量推进状态(避免循环里重复对同一 SO 做 update)
                processedOrderIds.add(wo.getOrderId());
            } catch (Exception e) {
                errors.add("发货失败(woId=" + wo.getId() + "): " + e.getMessage());
            }
        } // end for (sales_out)

        // 销售订单状态推进 — 每个订单只跑一次 advanceByShipment(它内部按订单聚合所有行项)
        for (Long orderId : processedOrderIds) {
            try {
                salesOrderStatusHelper.advanceByShipment(orderId);
            } catch (Exception e) {
                errors.add("订单状态推进失败(orderId=" + orderId + "): " + e.getMessage());
            }
        }

        log.info("销售发货完成: 成品入库{}条, 发货{}条, 错误{}条",
                stockInCount, shipmentCount, errors.size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stockInCount", stockInCount);
        result.put("shipmentCount", shipmentCount);
        result.put("success", stockInCount + shipmentCount);
        result.put("errors", errors);
        return result;
    }

    /** 一次性预加载所有工单的最后质检/工序结束时间,避免循环内 N 次 selectList */
    private Map<Long, LocalDateTime> preloadWoLastQcTime(List<BizWorkOrder> wos) {
        Map<Long, LocalDateTime> result = new HashMap<>();
        if (wos.isEmpty()) return result;
        Set<Long> woIds = wos.stream().map(BizWorkOrder::getId).collect(java.util.stream.Collectors.toSet());

        // 1) 全量拉一次 qc_task: max(inspectedAt) per workOrderId
        try {
            List<com.aimes.modules.quality.entity.BizQcTask> qcTasks = qcTaskMapper.selectList(
                    new LambdaQueryWrapper<com.aimes.modules.quality.entity.BizQcTask>()
                            .in(com.aimes.modules.quality.entity.BizQcTask::getWorkOrderId, woIds)
                            .eq(com.aimes.modules.quality.entity.BizQcTask::getDeleted, 0));
            for (com.aimes.modules.quality.entity.BizQcTask q : qcTasks) {
                if (q.getWorkOrderId() == null || q.getInspectedAt() == null) continue;
                result.merge(q.getWorkOrderId(), q.getInspectedAt(),
                        (a, b) -> a.isAfter(b) ? a : b);
            }
        } catch (Exception ignore) {}

        // 2) 没质检的工单 → 拉 process_task max(endTime) 兜底
        Set<Long> woWithoutQc = new HashSet<>(woIds);
        woWithoutQc.removeAll(result.keySet());
        if (!woWithoutQc.isEmpty()) {
            try {
                List<BizWoProcessTask> tasks = processTaskMapper.selectList(
                        new LambdaQueryWrapper<BizWoProcessTask>()
                                .in(BizWoProcessTask::getWorkOrderId, woWithoutQc)
                                .eq(BizWoProcessTask::getDeleted, 0));
                for (BizWoProcessTask t : tasks) {
                    if (t.getWorkOrderId() == null || t.getEndTime() == null) continue;
                    result.merge(t.getWorkOrderId(), t.getEndTime(),
                            (a, b) -> a.isAfter(b) ? a : b);
                }
            } catch (Exception ignore) {}
        }
        return result;
    }

    /**
     * 获取工单最后一次质检/工序结束时间
     */
    private LocalDateTime getLastQcTime(BizWorkOrder wo) {
        // 优先取质检任务的inspectedAt
        List<com.aimes.modules.quality.entity.BizQcTask> woQcTasks = qcTaskMapper.selectList(
                new LambdaQueryWrapper<com.aimes.modules.quality.entity.BizQcTask>()
                        .eq(com.aimes.modules.quality.entity.BizQcTask::getWorkOrderId, wo.getId())
                        .eq(com.aimes.modules.quality.entity.BizQcTask::getDeleted, 0)
                        .orderByDesc(com.aimes.modules.quality.entity.BizQcTask::getInspectedAt));
        if (!woQcTasks.isEmpty() && woQcTasks.get(0).getInspectedAt() != null) {
            return woQcTasks.get(0).getInspectedAt();
        }
        // 没有质检，取最后工序结束时间
        List<BizWoProcessTask> woTasks = processTaskMapper.selectList(
                new LambdaQueryWrapper<BizWoProcessTask>()
                        .eq(BizWoProcessTask::getWorkOrderId, wo.getId())
                        .eq(BizWoProcessTask::getDeleted, 0)
                        .orderByDesc(BizWoProcessTask::getEndTime));
        if (!woTasks.isEmpty() && woTasks.get(0).getEndTime() != null) {
            return woTasks.get(0).getEndTime();
        }
        return wo.getCreateTime();
    }

    /**
     * 更新订单发货状态
     */
    private void updateOrderShipmentStatus(Long orderId) {
        // 发货后推进：partial_shipped / shipped
        // 注意：completed 表示"生产完成"，由 WorkReportGenerator/OutsourceReceiptGenerator 推进
        salesOrderStatusHelper.advanceByShipment(orderId);
    }
}
