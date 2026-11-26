package com.aimes.modules.mrp.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.PageResult;
import com.aimes.common.utils.CodeUtil;
import com.aimes.modules.bom.entity.BizBomItem;
import com.aimes.modules.bom.entity.BizBomSubstitute;
import com.aimes.modules.bom.entity.BizBomVersion;
import com.aimes.modules.bom.mapper.BizBomItemMapper;
import com.aimes.modules.bom.mapper.BizBomSubstituteMapper;
import com.aimes.modules.bom.mapper.BizBomVersionMapper;
import com.aimes.modules.inventory.entity.BizInventory;
import com.aimes.modules.inventory.mapper.BizInventoryMapper;
import com.aimes.modules.master.entity.BizCustomer;
import com.aimes.modules.master.entity.BizMaterial;
import com.aimes.modules.master.entity.BizProduct;
import com.aimes.modules.master.entity.BizSupplier;
import com.aimes.modules.master.mapper.BizCustomerMapper;
import com.aimes.modules.master.mapper.BizMaterialMapper;
import com.aimes.modules.master.mapper.BizProductMapper;
import com.aimes.modules.master.mapper.BizSupplierMapper;
import com.aimes.modules.mrp.entity.*;
import com.aimes.modules.mrp.mapper.*;
import com.aimes.modules.mrp.entity.BizProductionPlanItem;
import com.aimes.modules.order.entity.BizSalesOrder;
import com.aimes.modules.order.entity.BizSalesOrderItem;
import com.aimes.modules.order.mapper.BizSalesOrderItemMapper;
import com.aimes.modules.order.mapper.BizSalesOrderMapper;
import com.aimes.modules.purchase.entity.BizPurchaseOrder;
import com.aimes.modules.purchase.entity.BizPurchaseOrderItem;
import com.aimes.modules.purchase.entity.BizPurchaseRequest;
import com.aimes.modules.purchase.mapper.BizPurchaseOrderItemMapper;
import com.aimes.modules.purchase.mapper.BizPurchaseOrderMapper;
import com.aimes.modules.purchase.mapper.BizPurchaseRequestMapper;
import com.aimes.modules.production.entity.BizWorkOrder;
import com.aimes.modules.production.mapper.BizWorkOrderMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * MRP运算服务
 * 核心流程: 收集需求 → BOM展开 → 毛需求汇总 → 净需求计算 → 批量修正 → 时间倒推 → 生成建议
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BizMrpService extends ServiceImpl<BizMrpRunMapper, BizMrpRun> {

    private final BizMrpDemandMapper demandMapper;
    private final BizMrpDemandOrderMapper demandOrderMapper;
    private final BizMrpPurchaseSuggestionMapper purchaseSuggestionMapper;
    private final BizMrpProductionSuggestionMapper productionSuggestionMapper;
    private final BizSalesOrderMapper salesOrderMapper;
    private final BizSalesOrderItemMapper salesOrderItemMapper;
    private final BizBomVersionMapper bomVersionMapper;
    private final BizBomItemMapper bomItemMapper;
    private final BizBomSubstituteMapper substituteMapper;
    private final BizInventoryMapper inventoryMapper;
    private final BizMaterialMapper materialMapper;
    private final BizProductMapper productMapper;
    private final BizCustomerMapper customerMapper;
    private final BizPurchaseOrderMapper purchaseOrderMapper;
    private final BizPurchaseOrderItemMapper purchaseOrderItemMapper;
    private final BizWorkOrderMapper workOrderMapper;
    private final BizProductionPlanItemMapper planItemMapper;
    private final BizPurchaseRequestMapper purchaseRequestMapper;
    private final BizSupplierMapper supplierMapper;
    private final ObjectMapper objectMapper;

    // ==================== MRP统计 ====================

    /**
     * MRP统计数据
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new LinkedHashMap<>();
        // 需求池订单数: 与 getPlannedOrders 一致, 只算源订单仍存在的; 用轻量查询避免加载明细
        long pendingOrderCount = 0L;
        List<BizMrpDemandOrder> demandOrders = demandOrderMapper.selectList(
                new LambdaQueryWrapper<BizMrpDemandOrder>().select(BizMrpDemandOrder::getId, BizMrpDemandOrder::getOrderId));
        if (!demandOrders.isEmpty()) {
            List<Long> orderIds = demandOrders.stream().map(BizMrpDemandOrder::getOrderId).toList();
            Set<Long> existingOrderIds = salesOrderMapper.selectBatchIds(orderIds).stream()
                    .map(BizSalesOrder::getId).collect(Collectors.toSet());
            pendingOrderCount = demandOrders.stream().filter(d -> existingOrderIds.contains(d.getOrderId())).count();
        }
        stats.put("pendingOrderCount", pendingOrderCount);
        // 总运行次数
        stats.put("totalRunCount", count());
        // 待确认采购建议数
        stats.put("pendingPurchaseCount", purchaseSuggestionMapper.selectCount(
                new LambdaQueryWrapper<BizMrpPurchaseSuggestion>().eq(BizMrpPurchaseSuggestion::getStatus, "pending")));
        // 待确认生产建议数
        stats.put("pendingProductionCount", productionSuggestionMapper.selectCount(
                new LambdaQueryWrapper<BizMrpProductionSuggestion>().eq(BizMrpProductionSuggestion::getStatus, "pending")));
        // 采购建议总数
        stats.put("totalPurchaseAmount", purchaseSuggestionMapper.selectCount(null));
        // 生产建议总数
        stats.put("totalProductionAmount", productionSuggestionMapper.selectCount(null));
        return stats;
    }

    // ==================== MRP运行记录查询 ====================

    /**
     * MRP运行记录分页
     */
    public PageResult<Map<String, Object>> getRunPage(String keyword, String status, int pageNum, int pageSize) {
        LambdaQueryWrapper<BizMrpRun> wrapper = new LambdaQueryWrapper<BizMrpRun>()
                .likeRight(StringUtils.hasText(keyword), BizMrpRun::getRunNo, keyword)
                .eq(StringUtils.hasText(status), BizMrpRun::getStatus, status)
                .orderByDesc(BizMrpRun::getRunTime);

        Page<BizMrpRun> page = page(new Page<>(pageNum, pageSize), wrapper);

        List<Map<String, Object>> list = page.getRecords().stream().map(run -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", run.getId());
            map.put("runNo", run.getRunNo());
            map.put("runType", run.getRunType());
            map.put("runTime", run.getRunTime());
            map.put("status", run.getStatus());
            map.put("orderCount", run.getOrderCount());
            map.put("resultCount", run.getResultCount());
            map.put("errorMsg", run.getErrorMsg());
            map.put("remark", run.getRemark());
            map.put("createTime", run.getCreateTime());
            return map;
        }).collect(Collectors.toList());

        return new PageResult<>(list, page.getTotal(), pageNum, pageSize);
    }

    /**
     * MRP运行详情（含需求明细、采购建议、生产建议）
     */
    public Map<String, Object> getRunDetail(Long runId) {
        BizMrpRun run = getById(runId);
        if (run == null) throw new BusinessException("MRP运行记录不存在");

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", run.getId());
        detail.put("runNo", run.getRunNo());
        detail.put("runType", run.getRunType());
        detail.put("runTime", run.getRunTime());
        detail.put("status", run.getStatus());
        detail.put("orderCount", run.getOrderCount());
        detail.put("resultCount", run.getResultCount());

        // 需求明细
        List<BizMrpDemand> demands = demandMapper.selectList(
                new LambdaQueryWrapper<BizMrpDemand>().eq(BizMrpDemand::getMrpRunId, runId));
        detail.put("demands", buildDemandList(demands));

        // 采购建议
        List<BizMrpPurchaseSuggestion> purchaseSuggestions = purchaseSuggestionMapper.selectList(
                new LambdaQueryWrapper<BizMrpPurchaseSuggestion>()
                        .eq(BizMrpPurchaseSuggestion::getMrpRunId, runId)
                        .orderByAsc(BizMrpPurchaseSuggestion::getRequiredDate));
        detail.put("purchaseSuggestions", buildPurchaseSuggestionList(purchaseSuggestions));

        // 生产建议
        List<BizMrpProductionSuggestion> productionSuggestions = productionSuggestionMapper.selectList(
                new LambdaQueryWrapper<BizMrpProductionSuggestion>()
                        .eq(BizMrpProductionSuggestion::getMrpRunId, runId)
                        .orderByAsc(BizMrpProductionSuggestion::getRequiredDate));
        detail.put("productionSuggestions", buildProductionSuggestionList(productionSuggestions));

        return detail;
    }

    // ==================== 待运算订单 ====================

    /**
     * 获取需求订单池中的待运算订单列表
     */
    public List<Map<String, Object>> getPlannedOrders() {
        // 从需求订单池查询所有订单（可重复参与MRP运算）
        List<BizMrpDemandOrder> demandOrders = demandOrderMapper.selectList(
                new LambdaQueryWrapper<BizMrpDemandOrder>()
                        .orderByDesc(BizMrpDemandOrder::getCreateTime));
        if (demandOrders.isEmpty()) return Collections.emptyList();

        // 批量查源订单（实时数据）
        List<Long> orderIds = demandOrders.stream().map(BizMrpDemandOrder::getOrderId).toList();
        Map<Long, BizSalesOrder> orderMap = salesOrderMapper.selectBatchIds(orderIds)
                .stream().collect(Collectors.toMap(BizSalesOrder::getId, o -> o, (a, b) -> a));

        // 批量查客户名称
        Set<Long> customerIds = orderMap.values().stream()
                .map(BizSalesOrder::getCustomerId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> customerNameMap = customerIds.isEmpty() ? Map.of() :
                customerMapper.selectBatchIds(customerIds).stream()
                        .collect(Collectors.toMap(BizCustomer::getId, BizCustomer::getCustomerName, (a, b) -> a));

        // 批量查所有订单行项
        List<BizSalesOrderItem> allItems = salesOrderItemMapper.selectList(
                new LambdaQueryWrapper<BizSalesOrderItem>().in(BizSalesOrderItem::getOrderId, orderIds));
        Map<Long, List<BizSalesOrderItem>> itemsByOrder = allItems.stream()
                .collect(Collectors.groupingBy(BizSalesOrderItem::getOrderId));

        // 批量查产品信息（兼容统一物料模式）
        Set<Long> productIds = allItems.stream().map(BizSalesOrderItem::getProductId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Map<String, String>> productInfoMap = batchResolveProductInfo(productIds);

        List<Map<String, Object>> result = new ArrayList<>();
        List<Long> orphanDemandIds = new ArrayList<>();
        for (BizMrpDemandOrder dOrder : demandOrders) {
            BizSalesOrder order = orderMap.get(dOrder.getOrderId());
            if (order == null) {
                // 源订单已删除/不存在: 顺手把孤儿 demand_order 清掉, 避免卡片数和列表数对不上
                orphanDemandIds.add(dOrder.getId());
                continue;
            }

            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", dOrder.getOrderId());
            map.put("demandOrderId", dOrder.getId());
            map.put("orderNo", order.getOrderNo());
            map.put("customerName", order.getCustomerId() != null ? customerNameMap.get(order.getCustomerId()) : "");
            map.put("deliveryDate", order.getDeliveryDate());
            map.put("totalAmount", order.getTotalAmount());
            map.put("orderStatus", order.getStatus());
            map.put("createTime", dOrder.getCreateTime());

            // 订单行项
            List<BizSalesOrderItem> items = itemsByOrder.getOrDefault(dOrder.getOrderId(), List.of());
            List<Map<String, Object>> itemList = new ArrayList<>();
            for (BizSalesOrderItem item : items) {
                Map<String, Object> im = new LinkedHashMap<>();
                im.put("productId", item.getProductId());
                Map<String, String> pInfo = productInfoMap.getOrDefault(item.getProductId(), Map.of());
                im.put("productName", pInfo.getOrDefault("name", ""));
                im.put("spec", pInfo.getOrDefault("spec", ""));
                im.put("quantity", item.getQuantity());
                im.put("shippedQty", item.getShippedQty());
                im.put("deliveryDate", item.getDeliveryDate());
                itemList.add(im);
            }
            map.put("items", itemList);
            result.add(map);
        }
        if (!orphanDemandIds.isEmpty()) {
            try { demandOrderMapper.deleteBatchIds(orphanDemandIds); } catch (Exception ignore) {}
        }
        return result;
    }

    // ==================== 需求订单池管理 ====================

    /**
     * 将销售订单加入MRP需求池
     * 由 BizSalesOrderService.convertToPlan() 调用
     */
    @Transactional
    public void addToDemandPool(Long orderId) {
        // 检查是否已在需求池中
        Long existCount = demandOrderMapper.selectCount(
                new LambdaQueryWrapper<BizMrpDemandOrder>()
                        .eq(BizMrpDemandOrder::getOrderId, orderId));
        if (existCount > 0) return; // 已存在则跳过

        BizSalesOrder order = salesOrderMapper.selectById(orderId);
        if (order == null) throw new BusinessException("订单不存在");

        BizMrpDemandOrder demandOrder = new BizMrpDemandOrder();
        demandOrder.setOrderId(order.getId());
        demandOrder.setOrderNo(order.getOrderNo());
        demandOrderMapper.insert(demandOrder);
    }

    /**
     * 从MRP需求池中移除指定订单
     * 由 BizSalesOrderService.cancel/voidOrder/complete 调用
     */
    @Transactional
    public void removeFromDemandPool(Long orderId) {
        demandOrderMapper.delete(
                new LambdaQueryWrapper<BizMrpDemandOrder>()
                        .eq(BizMrpDemandOrder::getOrderId, orderId));
    }

    // ==================== MRP核心运算 ====================

    /**
     * 执行MRP运算
     * @param orderIds 指定订单ID列表，null表示运算所有需求池订单
     * @param runType  manual/scheduled
     * @param config   运算配置: considerInTransit(考虑在途), considerSafetyStock(考虑安全库存), considerSubstitute(考虑替代料)
     */
    @Transactional
    public Long runMrp(List<Long> orderIds, String runType, Map<String, Boolean> config) {
        // 解析配置开关（默认值）
        boolean considerInTransit = config != null && Boolean.TRUE.equals(config.get("considerInTransit"));
        boolean considerSafetyStock = config != null && Boolean.TRUE.equals(config.get("considerSafetyStock"));
        boolean considerSubstitute = config != null && Boolean.TRUE.equals(config.get("considerSubstitute"));

        // 1. 创建运行记录
        BizMrpRun run = new BizMrpRun();
        run.setRunNo(CodeUtil.generate("MRP"));
        run.setRunType(runType != null ? runType : "manual");
        run.setRunTime(LocalDateTime.now());
        run.setStatus("running");
        run.setRemark(String.format("在途:%s 安全库存:%s 替代料:%s",
                considerInTransit ? "是" : "否", considerSafetyStock ? "是" : "否", considerSubstitute ? "是" : "否"));
        save(run);

        try {
            // 2. 清理旧的待确认建议（新运算替代旧建议，避免重复）
            purchaseSuggestionMapper.update(null, new LambdaUpdateWrapper<BizMrpPurchaseSuggestion>()
                    .eq(BizMrpPurchaseSuggestion::getStatus, "pending")
                    .set(BizMrpPurchaseSuggestion::getStatus, "superseded"));
            productionSuggestionMapper.update(null, new LambdaUpdateWrapper<BizMrpProductionSuggestion>()
                    .eq(BizMrpProductionSuggestion::getStatus, "pending")
                    .set(BizMrpProductionSuggestion::getStatus, "superseded"));

            // 3. 收集需求订单
            List<BizSalesOrder> orders = collectDemandOrders(orderIds);
            run.setOrderCount(orders.size());

            if (orders.isEmpty()) {
                run.setStatus("completed");
                run.setResultCount(0);
                updateById(run);
                return run.getId();
            }

            // 3. 收集所有订单行项
            List<BizSalesOrderItem> allItems = new ArrayList<>();
            Map<Long, BizSalesOrder> orderMap = new HashMap<>();
            for (BizSalesOrder order : orders) {
                orderMap.put(order.getId(), order);
                List<BizSalesOrderItem> items = salesOrderItemMapper.selectList(
                        new LambdaQueryWrapper<BizSalesOrderItem>()
                                .eq(BizSalesOrderItem::getOrderId, order.getId()));
                allItems.addAll(items);
            }

            // 4. BOM展开 + 需求计算
            Map<Long, MrpDemandAccum> purchaseDemands = new LinkedHashMap<>();    // materialId → 外购需求
            Map<Long, MrpDemandAccum> productionDemands = new LinkedHashMap<>();  // productId → 自制需求

            Set<Long> finishedProductIds = allItems.stream()
                    .map(BizSalesOrderItem::getProductId).filter(Objects::nonNull).collect(Collectors.toSet());

            for (BizSalesOrderItem item : allItems) {
                if (item.getProductId() == null || item.getQuantity() == null) continue;

                BizSalesOrder order = orderMap.get(item.getOrderId());
                LocalDate deliveryDate = item.getDeliveryDate() != null ? item.getDeliveryDate() :
                        (order != null ? order.getDeliveryDate() : null);

                // 剩余生产需求 = 订单数量 - 已生产数量(取已生产/已发货较大者兜底)
                // 已生产部分(含已发货)不再算需求,避免重复
                BigDecimal consumedQty = BigDecimal.ZERO;
                if (item.getProducedQty() != null) consumedQty = consumedQty.max(item.getProducedQty());
                if (item.getShippedQty() != null) consumedQty = consumedQty.max(item.getShippedQty());
                BigDecimal remainQty = item.getQuantity().subtract(consumedQty);
                if (remainQty.compareTo(BigDecimal.ZERO) <= 0) continue;

                // 成品 → 生产需求
                accumDemand(productionDemands, item.getProductId(), remainQty, deliveryDate,
                        item.getOrderId(), item.getId(), null);

                // BOM展开：按树形结构分流到采购/生产需求
                expandBomForMrp(item.getProductId(), remainQty, deliveryDate,
                        item.getOrderId(), item.getId(),
                        purchaseDemands, productionDemands);
            }

            // 5. 净需求计算 + 生成建议
            int resultCount = 0;

            // ===== 5a. 处理外购需求 → 采购建议 =====
            for (Map.Entry<Long, MrpDemandAccum> entry : purchaseDemands.entrySet()) {
                Long materialId = entry.getKey();
                MrpDemandAccum accum = entry.getValue();

                BigDecimal onHand = getAvailableStock(materialId, null);
                BigDecimal inTransit = considerInTransit ? getInTransitQty(materialId) : BigDecimal.ZERO;
                BigDecimal netQty = accum.totalQty.subtract(onHand).subtract(inTransit);

                // 安全库存补充：库存低于安全库存时，额外补充差额
                if (considerSafetyStock) {
                    BizMaterial mat = materialMapper.selectById(materialId);
                    if (mat != null && mat.getSafetyStock() != null && mat.getSafetyStock().compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal safetyGap = mat.getSafetyStock().subtract(onHand).max(BigDecimal.ZERO);
                        netQty = netQty.add(safetyGap);
                    }
                }

                // 替代料抵扣：用替代料的库存可用量抵扣主料净需求
                if (considerSubstitute && netQty.compareTo(BigDecimal.ZERO) > 0 && !accum.bomItemIds.isEmpty()) {
                    List<BizBomSubstitute> subs = substituteMapper.selectList(
                            new LambdaQueryWrapper<BizBomSubstitute>()
                                    .in(BizBomSubstitute::getBomItemId, accum.bomItemIds)
                                    .orderByAsc(BizBomSubstitute::getPriority));
                    for (BizBomSubstitute sub : subs) {
                        if (netQty.compareTo(BigDecimal.ZERO) <= 0) break;
                        BigDecimal subStock = getAvailableStock(sub.getMaterialId(), null);
                        if (subStock.compareTo(BigDecimal.ZERO) <= 0) continue;
                        BigDecimal ratio = sub.getSubstituteRatio() != null && sub.getSubstituteRatio().compareTo(BigDecimal.ZERO) > 0
                                ? sub.getSubstituteRatio() : BigDecimal.ONE;
                        BigDecimal coverable = subStock.divide(ratio, 4, RoundingMode.HALF_UP);
                        BigDecimal actual = coverable.min(netQty);
                        netQty = netQty.subtract(actual);
                    }
                }

                // 记录需求明细
                BizMrpDemand demand = new BizMrpDemand();
                demand.setMrpRunId(run.getId());
                demand.setMaterialId(materialId);
                demand.setDemandType("dependent");
                demand.setItemType("purchase");
                demand.setGrossQty(accum.totalQty);
                demand.setOnHandQty(onHand);
                demand.setInTransitQty(inTransit);
                demand.setInProductionQty(BigDecimal.ZERO);
                demand.setNetQty(netQty.max(BigDecimal.ZERO));
                demand.setRequiredDate(accum.earliestDate);
                demand.setSourceOrderId(accum.sourceOrderIds.isEmpty() ? null : accum.sourceOrderIds.get(0));
                demand.setSourceOrderItemId(accum.sourceOrderItemIds.isEmpty() ? null : accum.sourceOrderItemIds.get(0));
                demandMapper.insert(demand);

                // 净需求 > 0 → 生成采购建议
                if (netQty.compareTo(BigDecimal.ZERO) > 0) {
                    BizMaterial material = materialMapper.selectById(materialId);
                    BigDecimal adjustedQty = applyBatchRules(netQty,
                            material != null ? material.getMinOrderQty() : null,
                            material != null ? material.getOrderQtyMultiple() : null);
                    demand.setAdjustedQty(adjustedQty);
                    demandMapper.updateById(demand);

                    int leadTime = material != null && material.getPurchaseLeadTime() != null
                            ? material.getPurchaseLeadTime() : 0;

                    BizMrpPurchaseSuggestion suggestion = new BizMrpPurchaseSuggestion();
                    suggestion.setMrpRunId(run.getId());
                    suggestion.setMaterialId(materialId);
                    suggestion.setSuggestQty(adjustedQty);
                    suggestion.setRequiredDate(accum.earliestDate);
                    suggestion.setSuggestOrderDate(accum.earliestDate != null
                            ? accum.earliestDate.minusDays(leadTime) : null);
                    suggestion.setSupplierId(material != null ? material.getDefaultSupplierId() : null);
                    suggestion.setStatus("pending");
                    suggestion.setSourceSummary(buildSourceSummary(accum));
                    purchaseSuggestionMapper.insert(suggestion);
                    resultCount++;
                }
            }

            // ===== 5b. 处理自制需求 → 生产建议（受原材料库存约束，仅考虑仓库库存） =====
            // 构建原材料可用库存池（仅仓库在手库存，不含在途）
            Map<Long, BigDecimal> materialPool = new HashMap<>();
            for (Long materialId : purchaseDemands.keySet()) {
                materialPool.put(materialId, getAvailableStock(materialId, null));
            }

            // 竞争追踪：记录每种物料被哪些产品分配、每个产品用到哪些物料
            Map<Long, Set<Long>> materialAllocatedBy = new HashMap<>();
            Map<Long, List<Long>> productSuggestionIdMap = new HashMap<>();
            Map<Long, Set<Long>> productUsedMaterials = new HashMap<>();
            Map<Long, Map<Long, BigDecimal>> productPerUnitNeeds = new HashMap<>();
            Map<Long, BigDecimal> productSuggestQty = new HashMap<>();

            for (Map.Entry<Long, MrpDemandAccum> entry : productionDemands.entrySet()) {
                Long productId = entry.getKey();
                MrpDemandAccum accum = entry.getValue();

                boolean isFinished = finishedProductIds.contains(productId);

                // 成品:remainQty 已经按"订单数量 - 已生产"精确计算,不再用全局库存/在产抵扣
                //      (避免把其他订单的库存或在产误算给本订单)
                // 半成品(BOM 展开出的中间件):没有订单归属,继续用全局库存/在产抵扣
                BigDecimal onHand = isFinished ? BigDecimal.ZERO : getAvailableStockByProduct(productId);
                BigDecimal inProduction = isFinished ? BigDecimal.ZERO : getInProductionQty(productId);
                BigDecimal netQty = accum.totalQty.subtract(onHand).subtract(inProduction);

                BizMrpDemand demand = new BizMrpDemand();
                demand.setMrpRunId(run.getId());
                demand.setProductId(productId);
                demand.setDemandType(isFinished ? "independent" : "dependent");
                demand.setItemType("manufacture");
                demand.setGrossQty(accum.totalQty);
                demand.setOnHandQty(onHand);
                demand.setInTransitQty(BigDecimal.ZERO);
                demand.setInProductionQty(inProduction);
                demand.setNetQty(netQty.max(BigDecimal.ZERO));
                demand.setRequiredDate(accum.earliestDate);
                demand.setSourceOrderId(accum.sourceOrderIds.isEmpty() ? null : accum.sourceOrderIds.get(0));
                demand.setSourceOrderItemId(accum.sourceOrderItemIds.isEmpty() ? null : accum.sourceOrderItemIds.get(0));
                demandMapper.insert(demand);

                if (netQty.compareTo(BigDecimal.ZERO) > 0) {
                    Map<String, String> prodInfo = resolveProductInfo(productId);
                    String minQtyStr = prodInfo.get("minQty");
                    String multipleStr = prodInfo.get("multiple");

                    // 计算该产品BOM的单位原材料用量
                    Map<Long, BigDecimal> perUnitNeeds = getPerUnitMaterialNeeds(productId);
                    // 每个产品独立计算最大可生产数量（不扣减共享池）
                    // 竞争关系由竞争组标注，由用户决定优先级
                    BigDecimal maxProducible = calculateMaxProducibleQty(perUnitNeeds, materialPool);

                    String remarkText = null;
                    BigDecimal originalNetQty = netQty;
                    BigDecimal effectiveNetQty = netQty;
                    boolean constrained = false;
                    if (maxProducible != null && maxProducible.compareTo(netQty) < 0) {
                        effectiveNetQty = maxProducible;
                        constrained = true;
                        remarkText = String.format("原材料不足,需求%s,最大可产%s",
                                netQty.stripTrailingZeros().toPlainString(),
                                maxProducible.stripTrailingZeros().toPlainString());
                    }

                    if (effectiveNetQty.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal adjustedQty = applyBatchRules(effectiveNetQty,
                                minQtyStr != null ? new BigDecimal(minQtyStr) : null,
                                multipleStr != null ? new BigDecimal(multipleStr) : null);
                        // 批量规则可能向上取整超过原材料约束，需再次限制
                        if (maxProducible != null && adjustedQty.compareTo(maxProducible) > 0) {
                            adjustedQty = maxProducible.setScale(4, RoundingMode.HALF_UP);
                        }
                        demand.setAdjustedQty(adjustedQty);
                        demandMapper.updateById(demand);

                        int leadTime = Integer.parseInt(prodInfo.getOrDefault("leadTime", "0"));

                        // 按订单拆分生产建议：同产品多订单 → 多行建议
                        List<Long> suggestionIds = new ArrayList<>();
                        if (accum.orderQtyMap.size() > 1) {
                            // 多订单：按订单比例拆分
                            for (Map.Entry<Long, BigDecimal> orderEntry : accum.orderQtyMap.entrySet()) {
                                Long oid = orderEntry.getKey();
                                BigDecimal orderGrossQty = orderEntry.getValue();
                                BigDecimal ratio = orderGrossQty.divide(accum.totalQty, 10, RoundingMode.HALF_UP);
                                BigDecimal orderSuggestQty = adjustedQty.multiply(ratio).setScale(4, RoundingMode.HALF_UP);
                                if (orderSuggestQty.compareTo(BigDecimal.ZERO) <= 0) continue;

                                LocalDate orderDate = accum.orderDateMap.getOrDefault(oid, accum.earliestDate);
                                List<Long> oItemIds = accum.orderItemIdsMap.getOrDefault(oid, List.of());

                                Map<String, Object> orderSummary = new LinkedHashMap<>();
                                orderSummary.put("orderIds", List.of(oid));
                                orderSummary.put("orderItemIds", oItemIds);
                                orderSummary.put("totalQty", orderGrossQty);
                                if (constrained) {
                                    orderSummary.put("constrained", true);
                                    orderSummary.put("originalNetQty", originalNetQty.multiply(ratio).setScale(4, RoundingMode.HALF_UP));
                                    orderSummary.put("maxProducible", maxProducible);
                                }
                                String json;
                                try { json = objectMapper.writeValueAsString(orderSummary); } catch (Exception ex) { json = "{}"; }

                                // maxProducibleQty 按产品独立计算（基于全量库存），不按订单比例拆分
                                BizMrpProductionSuggestion suggestion = new BizMrpProductionSuggestion();
                                suggestion.setMrpRunId(run.getId());
                                suggestion.setProductId(productId);
                                suggestion.setSuggestQty(orderSuggestQty);
                                suggestion.setGrossQty(orderGrossQty);
                                suggestion.setMaxProducibleQty(maxProducible);
                                suggestion.setActualQty(BigDecimal.ZERO);
                                suggestion.setRequiredDate(orderDate);
                                suggestion.setSuggestStartDate(orderDate != null ? orderDate.minusDays(leadTime) : null);
                                suggestion.setIsSemiFinished(!isFinished);
                                suggestion.setStatus("pending");
                                suggestion.setSourceSummary(json);
                                suggestion.setRemark(remarkText);
                                productionSuggestionMapper.insert(suggestion);
                                resultCount++;
                                suggestionIds.add(suggestion.getId());
                            }
                        } else {
                            // 单订单或无订单：保持一行
                            Map<String, Object> summary = new LinkedHashMap<>();
                            summary.put("orderIds", accum.sourceOrderIds);
                            summary.put("orderItemIds", accum.sourceOrderItemIds);
                            summary.put("totalQty", accum.totalQty);
                            if (constrained) {
                                summary.put("constrained", true);
                                summary.put("originalNetQty", originalNetQty);
                                summary.put("maxProducible", maxProducible);
                            }
                            String json;
                            try { json = objectMapper.writeValueAsString(summary); } catch (Exception ex) { json = buildSourceSummary(accum); }

                            BizMrpProductionSuggestion suggestion = new BizMrpProductionSuggestion();
                            suggestion.setMrpRunId(run.getId());
                            suggestion.setProductId(productId);
                            suggestion.setSuggestQty(adjustedQty);
                            suggestion.setGrossQty(accum.totalQty);
                            suggestion.setMaxProducibleQty(maxProducible);
                            suggestion.setActualQty(BigDecimal.ZERO);
                            suggestion.setRequiredDate(accum.earliestDate);
                            suggestion.setSuggestStartDate(accum.earliestDate != null
                                    ? accum.earliestDate.minusDays(leadTime) : null);
                            suggestion.setIsSemiFinished(!isFinished);
                            suggestion.setStatus("pending");
                            suggestion.setSourceSummary(json);
                            suggestion.setRemark(remarkText);
                            productionSuggestionMapper.insert(suggestion);
                            resultCount++;
                            suggestionIds.add(suggestion.getId());
                        }

                        // 记录竞争追踪信息（不从池中扣减，每个产品独立计算）
                        if (!suggestionIds.isEmpty()) productSuggestionIdMap.put(productId, suggestionIds);
                        productUsedMaterials.put(productId, new HashSet<>(perUnitNeeds.keySet()));
                        productPerUnitNeeds.put(productId, perUnitNeeds);
                        productSuggestQty.put(productId, adjustedQty);
                        for (Long matId : perUnitNeeds.keySet()) {
                            materialAllocatedBy.computeIfAbsent(matId, k -> new HashSet<>()).add(productId);
                        }
                    } else {
                        // 原材料完全不足，记录adjustedQty=0
                        demand.setAdjustedQty(BigDecimal.ZERO);
                        demandMapper.updateById(demand);
                    }
                }
            }

            // ===== 5c. 计算物料竞争组，标注共享原材料的生产建议 =====
            // 仅当物料总需求 > 库存时才标记竞争
            computeAndUpdateCompetitionGroups(productSuggestionIdMap, productUsedMaterials,
                    materialAllocatedBy, productPerUnitNeeds, productSuggestQty, materialPool);

            // 6. 更新运行记录
            run.setStatus("completed");
            run.setResultCount(resultCount);
            updateById(run);

            return run.getId();

        } catch (Exception e) {
            log.error("MRP运算异常: {}", e.getMessage(), e);
            run.setStatus("failed");
            run.setErrorMsg(e.getMessage());
            updateById(run);
            throw new BusinessException("MRP运算失败: " + e.getMessage());
        }
    }

    /**
     * 定时MRP运算入口
     */
    @Transactional
    public Long runScheduledMrp() {
        Map<String, Boolean> defaultConfig = Map.of("considerInTransit", true, "considerSafetyStock", false, "considerSubstitute", false);
        return runMrp(null, "scheduled", defaultConfig);
    }

    // ==================== 建议确认操作 ====================

    /**
     * 确认采购建议 → 转为采购申请
     */
    @Transactional
    public void confirmPurchaseSuggestion(Long suggestionId) {
        BizMrpPurchaseSuggestion suggestion = purchaseSuggestionMapper.selectById(suggestionId);
        if (suggestion == null) throw new BusinessException("采购建议不存在");
        if (!"pending".equals(suggestion.getStatus()) && !"confirmed".equals(suggestion.getStatus())) {
            throw new BusinessException("当前状态不允许操作");
        }

        BizMaterial material = materialMapper.selectById(suggestion.getMaterialId());

        // 创建采购申请
        BizPurchaseRequest request = new BizPurchaseRequest();
        request.setRequestNo(CodeUtil.generate("PR"));
        request.setMaterialId(suggestion.getMaterialId());
        request.setMaterialName(material != null ? material.getMaterialName() : null);
        request.setSpec(material != null ? material.getSpec() : null);
        request.setUnit(material != null ? material.getUnit() : null);
        request.setQuantity(suggestion.getSuggestQty());
        request.setSuggestedSupplierId(suggestion.getSupplierId());
        request.setExpectedDate(suggestion.getRequiredDate());
        request.setSourceType("mrp");
        request.setStatus("pending");
        purchaseRequestMapper.insert(request);

        // 更新建议状态
        suggestion.setStatus("transferred");
        suggestion.setPurchaseRequestId(request.getId());
        purchaseSuggestionMapper.updateById(suggestion);
    }

    /**
     * 批量确认采购建议
     */
    @Transactional
    public void batchConfirmPurchaseSuggestions(List<Long> ids) {
        for (Long id : ids) {
            confirmPurchaseSuggestion(id);
        }
    }

    /**
     * 确认生产建议 → 转入生产计划
     * 由 BizProductionPlanService 调用处理
     */
    @Transactional
    public void markProductionSuggestionTransferred(Long suggestionId, Long planItemId) {
        BizMrpProductionSuggestion suggestion = productionSuggestionMapper.selectById(suggestionId);
        if (suggestion == null) throw new BusinessException("生产建议不存在");

        suggestion.setStatus("transferred");
        suggestion.setPlanItemId(planItemId);
        productionSuggestionMapper.updateById(suggestion);
    }

    /**
     * 忽略同一竞争组中其他未处理的生产建议
     * 当竞争组中某个建议被转入计划后，其余建议自动忽略
     *
     * @param mrpRunId         MRP运行ID
     * @param competitionGroup 竞争组标签（如 "A", "B"）
     * @param excludeIds       需排除的建议ID（已被转入计划的）
     */
    @Transactional
    /**
     * 竞争组转计划后处理：检查剩余物料是否还够支撑同组其他建议
     * - 物料够用的：保留 pending 状态，更新 maxProducibleQty
     * - 物料不够（maxProducibleQty=0）的：自动忽略
     *
     * @param mrpRunId          MRP运行批次ID
     * @param competitionGroup  竞争组标签
     * @param excludeIds        已转计划的建议ID（排除）
     * @param consumedByTransferred 已转计划的建议消耗的物料量 {materialId -> consumedQty}
     */
    public void recheckCompetitionGroupAfterTransfer(Long mrpRunId, String competitionGroup,
                                                      Set<Long> excludeIds,
                                                      Map<Long, BigDecimal> consumedByTransferred) {
        if (competitionGroup == null || competitionGroup.isEmpty()) return;

        // 查找同竞争组剩余的 pending 建议
        List<BizMrpProductionSuggestion> allSuggestions = productionSuggestionMapper.selectList(
                new LambdaQueryWrapper<BizMrpProductionSuggestion>()
                        .eq(BizMrpProductionSuggestion::getMrpRunId, mrpRunId)
                        .in(BizMrpProductionSuggestion::getStatus, "pending", "confirmed")
                        .notIn(!excludeIds.isEmpty(), BizMrpProductionSuggestion::getId, excludeIds));

        List<BizMrpProductionSuggestion> groupMembers = new ArrayList<>();
        for (BizMrpProductionSuggestion sug : allSuggestions) {
            String sugGroup = parseCompetitionGroup(sug.getSourceSummary());
            if (competitionGroup.equals(sugGroup)) {
                groupMembers.add(sug);
            }
        }
        if (groupMembers.isEmpty()) return;

        // 构建扣减后的物料池：原始库存 - 已转计划消耗
        Map<Long, BigDecimal> remainingPool = new HashMap<>();
        Set<Long> allMaterialIds = new HashSet<>(consumedByTransferred.keySet());
        // 也收集剩余建议需要的物料
        Set<Long> allProductIds = groupMembers.stream()
                .map(BizMrpProductionSuggestion::getProductId).collect(Collectors.toSet());
        Map<Long, Map<Long, BigDecimal>> productPerUnitMap = new HashMap<>();
        for (Long pid : allProductIds) {
            Map<Long, BigDecimal> perUnit = getPerUnitMaterialNeeds(pid);
            productPerUnitMap.put(pid, perUnit);
            allMaterialIds.addAll(perUnit.keySet());
        }
        for (Long matId : allMaterialIds) {
            BigDecimal available = getAvailableStock(matId, null);
            BigDecimal consumed = consumedByTransferred.getOrDefault(matId, BigDecimal.ZERO);
            remainingPool.put(matId, available.subtract(consumed).max(BigDecimal.ZERO));
        }

        // 对每个剩余建议重算 maxProducibleQty
        for (BizMrpProductionSuggestion sug : groupMembers) {
            Map<Long, BigDecimal> perUnit = productPerUnitMap.getOrDefault(sug.getProductId(), Map.of());
            if (perUnit.isEmpty()) continue;

            BigDecimal maxProd = null;
            for (Map.Entry<Long, BigDecimal> matEntry : perUnit.entrySet()) {
                BigDecimal perUnitQty = matEntry.getValue();
                if (perUnitQty.compareTo(BigDecimal.ZERO) <= 0) continue;
                BigDecimal remaining = remainingPool.getOrDefault(matEntry.getKey(), BigDecimal.ZERO);
                BigDecimal canProduce = remaining.divide(perUnitQty, 0, RoundingMode.FLOOR);
                if (maxProd == null || canProduce.compareTo(maxProd) < 0) {
                    maxProd = canProduce;
                }
            }

            if (maxProd == null) maxProd = BigDecimal.ZERO;

            if (maxProd.compareTo(BigDecimal.ZERO) <= 0) {
                // 物料已不足以生产任何数量 → 自动忽略
                sug.setStatus("ignored");
                sug.setMaxProducibleQty(BigDecimal.ZERO);
                sug.setRemark("竞争组[" + competitionGroup + "]物料已被其他计划占用，自动忽略");
                productionSuggestionMapper.updateById(sug);
                log.info("竞争组物料不足自动忽略: suggestionId={}, productId={}, group={}",
                        sug.getId(), sug.getProductId(), competitionGroup);
            } else {
                // 物料仍够用 → 更新 maxProducibleQty，保留 pending
                sug.setMaxProducibleQty(maxProd);
                // actualQty 如果超过新的 maxProducibleQty，清零让用户重新分配
                if (sug.getActualQty() != null && sug.getActualQty().compareTo(maxProd) > 0) {
                    sug.setActualQty(BigDecimal.ZERO);
                }
                productionSuggestionMapper.updateById(sug);
                log.info("竞争组物料仍够用: suggestionId={}, productId={}, group={}, newMaxProd={}",
                        sug.getId(), sug.getProductId(), competitionGroup, maxProd);
            }
        }
    }

    /**
     * 计算生产指定数量产品所消耗的原材料量
     * @return {materialId -> consumedQty}
     */
    public Map<Long, BigDecimal> calculateMaterialConsumption(Long productId, BigDecimal qty) {
        Map<Long, BigDecimal> perUnit = getPerUnitMaterialNeeds(productId);
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        for (Map.Entry<Long, BigDecimal> e : perUnit.entrySet()) {
            result.put(e.getKey(), e.getValue().multiply(qty).setScale(4, RoundingMode.HALF_UP));
        }
        return result;
    }

    /**
     * 从sourceSummary中提取竞争组标签
     */
    private String parseCompetitionGroup(String sourceSummary) {
        if (sourceSummary == null || sourceSummary.isEmpty()) return null;
        try {
            Map<String, Object> summary = objectMapper.readValue(sourceSummary, Map.class);
            Object group = summary.get("competitionGroup");
            return group != null ? group.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 忽略建议
     */
    @Transactional
    public void ignoreSuggestion(String type, Long id) {
        if ("purchase".equals(type)) {
            BizMrpPurchaseSuggestion s = purchaseSuggestionMapper.selectById(id);
            if (s != null) {
                s.setStatus("ignored");
                purchaseSuggestionMapper.updateById(s);
            }
        } else if ("production".equals(type)) {
            BizMrpProductionSuggestion s = productionSuggestionMapper.selectById(id);
            if (s != null) {
                s.setStatus("ignored");
                productionSuggestionMapper.updateById(s);
            }
        }
    }

    /**
     * 更新采购建议（调整数量、供应商等）
     */
    @Transactional
    public void updatePurchaseSuggestion(Long id, BizMrpPurchaseSuggestion update) {
        BizMrpPurchaseSuggestion existing = purchaseSuggestionMapper.selectById(id);
        if (existing == null) throw new BusinessException("采购建议不存在");
        if (!"pending".equals(existing.getStatus())) {
            throw new BusinessException("仅待确认状态可修改");
        }

        if (update.getSuggestQty() != null) existing.setSuggestQty(update.getSuggestQty());
        if (update.getSupplierId() != null) existing.setSupplierId(update.getSupplierId());
        if (update.getRequiredDate() != null) existing.setRequiredDate(update.getRequiredDate());
        if (update.getSuggestOrderDate() != null) existing.setSuggestOrderDate(update.getSuggestOrderDate());
        if (update.getRemark() != null) existing.setRemark(update.getRemark());
        purchaseSuggestionMapper.updateById(existing);
    }

    /**
     * 更新生产建议（调整数量、半成品处理方式等）
     */
    @Transactional
    public void updateProductionSuggestion(Long id, BizMrpProductionSuggestion update) {
        BizMrpProductionSuggestion existing = productionSuggestionMapper.selectById(id);
        if (existing == null) throw new BusinessException("生产建议不存在");
        if (!"pending".equals(existing.getStatus())) {
            throw new BusinessException("仅待确认状态可修改");
        }

        if (update.getSuggestQty() != null) existing.setSuggestQty(update.getSuggestQty());
        if (update.getSemiHandleType() != null) existing.setSemiHandleType(update.getSemiHandleType());
        if (update.getRequiredDate() != null) existing.setRequiredDate(update.getRequiredDate());
        if (update.getSuggestStartDate() != null) existing.setSuggestStartDate(update.getSuggestStartDate());
        if (update.getRemark() != null) existing.setRemark(update.getRemark());

        // 处理实际生产数量变更 → 自动重算竞争组
        boolean actualQtyChanged = update.getActualQty() != null
                && (existing.getActualQty() == null || existing.getActualQty().compareTo(update.getActualQty()) != 0);
        if (actualQtyChanged) {
            // 不能超过最大可生产数量
            if (existing.getMaxProducibleQty() != null
                    && update.getActualQty().compareTo(existing.getMaxProducibleQty()) > 0) {
                throw new BusinessException("实际生产数量不能超过最大可生产数量("
                        + existing.getMaxProducibleQty().stripTrailingZeros().toPlainString() + ")");
            }
            existing.setActualQty(update.getActualQty());
        }

        productionSuggestionMapper.updateById(existing);

        // 实际生产数量变更后，校验竞争组物料约束
        if (actualQtyChanged) {
            validateCompetitionGroupStock(existing);
        }
    }

    /**
     * 校验竞争组物料约束：检查同竞争组内所有建议的actualQty合计消耗不超过库存
     * 如超出则抛异常阻止保存
     */
    private void validateCompetitionGroupStock(BizMrpProductionSuggestion changed) {
        Map<String, Object> info = parseCompetitionInfo(changed.getSourceSummary());
        String groupLabel = (String) info.get("competitionGroup");
        if (groupLabel == null || groupLabel.isEmpty()) return;

        // 查找同一MRP运行批次中的所有pending生产建议
        List<BizMrpProductionSuggestion> allSuggestions = productionSuggestionMapper.selectList(
                new LambdaQueryWrapper<BizMrpProductionSuggestion>()
                        .eq(BizMrpProductionSuggestion::getMrpRunId, changed.getMrpRunId())
                        .eq(BizMrpProductionSuggestion::getStatus, "pending"));

        // 筛选同竞争组的建议
        List<BizMrpProductionSuggestion> groupMembers = new ArrayList<>();
        for (BizMrpProductionSuggestion sug : allSuggestions) {
            Map<String, Object> sugInfo = parseCompetitionInfo(sug.getSourceSummary());
            if (groupLabel.equals(sugInfo.get("competitionGroup"))) {
                groupMembers.add(sug);
            }
        }
        if (groupMembers.size() <= 1) return;

        // 收集所有产品的单位物料需求
        Set<Long> allProductIds = groupMembers.stream()
                .map(BizMrpProductionSuggestion::getProductId).collect(Collectors.toSet());
        Map<Long, Map<Long, BigDecimal>> productPerUnitMap = new HashMap<>();
        Set<Long> allMaterialIds = new HashSet<>();
        for (Long pid : allProductIds) {
            Map<Long, BigDecimal> perUnit = getPerUnitMaterialNeeds(pid);
            productPerUnitMap.put(pid, perUnit);
            allMaterialIds.addAll(perUnit.keySet());
        }

        // 计算每种物料的总消耗
        Map<Long, BigDecimal> totalConsumption = new HashMap<>();
        for (BizMrpProductionSuggestion sug : groupMembers) {
            BigDecimal actual = sug.getActualQty() != null ? sug.getActualQty() : BigDecimal.ZERO;
            Map<Long, BigDecimal> perUnit = productPerUnitMap.getOrDefault(sug.getProductId(), Map.of());
            for (Map.Entry<Long, BigDecimal> e : perUnit.entrySet()) {
                totalConsumption.merge(e.getKey(), e.getValue().multiply(actual), BigDecimal::add);
            }
        }

        // 检查是否超过库存
        for (Map.Entry<Long, BigDecimal> e : totalConsumption.entrySet()) {
            BigDecimal available = getAvailableStock(e.getKey(), null);
            if (e.getValue().compareTo(available) > 0) {
                BizMaterial mat = materialMapper.selectById(e.getKey());
                String matName = mat != null ? mat.getMaterialName() : String.valueOf(e.getKey());
                throw new BusinessException(String.format("竞争组物料[%s]超出库存: 需要%s, 库存%s",
                        matName,
                        e.getValue().stripTrailingZeros().toPlainString(),
                        available.stripTrailingZeros().toPlainString()));
            }
        }
    }

    // ==================== 查询待确认建议列表 ====================

    /**
     * 分页查询采购建议
     */
    public PageResult<Map<String, Object>> getPurchaseSuggestionPage(Long mrpRunId, String status,
                                                                      LocalDate dateFrom, LocalDate dateTo,
                                                                      int pageNum, int pageSize) {
        LambdaQueryWrapper<BizMrpPurchaseSuggestion> wrapper = new LambdaQueryWrapper<BizMrpPurchaseSuggestion>()
                .eq(mrpRunId != null, BizMrpPurchaseSuggestion::getMrpRunId, mrpRunId)
                .eq(StringUtils.hasText(status), BizMrpPurchaseSuggestion::getStatus, status)
                .ge(dateFrom != null, BizMrpPurchaseSuggestion::getRequiredDate, dateFrom)
                .le(dateTo != null, BizMrpPurchaseSuggestion::getRequiredDate, dateTo)
                .orderByAsc(BizMrpPurchaseSuggestion::getRequiredDate);

        Page<BizMrpPurchaseSuggestion> page = new Page<>(pageNum, pageSize);
        purchaseSuggestionMapper.selectPage(page, wrapper);

        List<Map<String, Object>> list = buildPurchaseSuggestionList(page.getRecords());
        return new PageResult<>(list, page.getTotal(), pageNum, pageSize);
    }

    /**
     * 分页查询生产建议
     */
    public PageResult<Map<String, Object>> getProductionSuggestionPage(Long mrpRunId, String status,
                                                                        LocalDate dateFrom, LocalDate dateTo,
                                                                        int pageNum, int pageSize) {
        LambdaQueryWrapper<BizMrpProductionSuggestion> wrapper = new LambdaQueryWrapper<BizMrpProductionSuggestion>()
                .eq(mrpRunId != null, BizMrpProductionSuggestion::getMrpRunId, mrpRunId)
                .eq(StringUtils.hasText(status), BizMrpProductionSuggestion::getStatus, status)
                .ge(dateFrom != null, BizMrpProductionSuggestion::getRequiredDate, dateFrom)
                .le(dateTo != null, BizMrpProductionSuggestion::getRequiredDate, dateTo)
                .orderByAsc(BizMrpProductionSuggestion::getRequiredDate);

        Page<BizMrpProductionSuggestion> page = new Page<>(pageNum, pageSize);
        productionSuggestionMapper.selectPage(page, wrapper);

        List<Map<String, Object>> list = buildProductionSuggestionList(page.getRecords());
        return new PageResult<>(list, page.getTotal(), pageNum, pageSize);
    }

    // ==================== 内部计算方法 ====================

    /**
     * 收集需要MRP运算的销售订单（从需求订单池查询）
     */
    private List<BizSalesOrder> collectDemandOrders(List<Long> orderIds) {
        // 从需求订单池查询订单ID（需求池中的订单可重复参与运算）
        LambdaQueryWrapper<BizMrpDemandOrder> poolWrapper = new LambdaQueryWrapper<>();
        if (orderIds != null && !orderIds.isEmpty()) {
            poolWrapper.in(BizMrpDemandOrder::getOrderId, orderIds);
        }
        List<BizMrpDemandOrder> demandOrders = demandOrderMapper.selectList(poolWrapper);
        if (demandOrders.isEmpty()) return Collections.emptyList();

        List<Long> poolOrderIds = demandOrders.stream()
                .map(BizMrpDemandOrder::getOrderId).collect(Collectors.toList());

        // 从销售订单表读取完整订单数据
        LambdaQueryWrapper<BizSalesOrder> wrapper = new LambdaQueryWrapper<BizSalesOrder>()
                .in(BizSalesOrder::getId, poolOrderIds)
                .orderByAsc(BizSalesOrder::getDeliveryDate);
        return salesOrderMapper.selectList(wrapper);
    }

    /**
     * BOM展开（MRP专用）
     * 查找产品的生效BOM，按树形结构（parentItemId）递归展开
     * 外购(purchase) → 采购需求，自制(manufacture/空) → 生产需求
     */
    private void expandBomForMrp(Long productId, BigDecimal parentQty, LocalDate requiredDate,
                                  Long sourceOrderId, Long sourceOrderItemId,
                                  Map<Long, MrpDemandAccum> purchaseDemands,
                                  Map<Long, MrpDemandAccum> productionDemands) {
        // 查找产品的生效BOM版本
        BizBomVersion activeBom = bomVersionMapper.selectOne(
                new LambdaQueryWrapper<BizBomVersion>()
                        .eq(BizBomVersion::getProductId, productId)
                        .eq(BizBomVersion::getStatus, "active")
                        .last("LIMIT 1"));
        if (activeBom == null) return;

        // 获取产品的生产提前期，倒推物料需求日期（兼容统一物料模式）
        Map<String, String> prodInfo = resolveProductInfo(productId);
        int productionLeadTime = Integer.parseInt(prodInfo.getOrDefault("leadTime", "0"));
        LocalDate materialRequiredDate = requiredDate != null
                ? requiredDate.minusDays(productionLeadTime) : null;

        // 一次性加载该BOM版本下的所有行项
        List<BizBomItem> allItems = bomItemMapper.selectList(
                new LambdaQueryWrapper<BizBomItem>()
                        .eq(BizBomItem::getBomVersionId, activeBom.getId())
                        .orderByAsc(BizBomItem::getSortOrder));

        // 按parentItemId分组，构建树形索引
        Map<Long, List<BizBomItem>> childrenMap = allItems.stream()
                .collect(Collectors.groupingBy(item ->
                        item.getParentItemId() != null && item.getParentItemId() != 0
                                ? item.getParentItemId() : 0L));

        // 从根节点（parentItemId=0）开始递归展开
        expandBomTree(0L, parentQty, materialRequiredDate,
                sourceOrderId, sourceOrderItemId,
                childrenMap, purchaseDemands, productionDemands, 0);
    }

    /**
     * 递归展开BOM树形结构（同一BOM版本内）
     * parentQty为父节点的总需求量，子项需求 = parentQty × 子项实际用量
     */
    private void expandBomTree(Long parentItemId, BigDecimal parentQty, LocalDate requiredDate,
                                Long sourceOrderId, Long sourceOrderItemId,
                                Map<Long, List<BizBomItem>> childrenMap,
                                Map<Long, MrpDemandAccum> purchaseDemands,
                                Map<Long, MrpDemandAccum> productionDemands, int depth) {
        if (depth > 10) return; // 防止异常数据导致无限递归
        List<BizBomItem> children = childrenMap.getOrDefault(parentItemId, List.of());

        for (BizBomItem item : children) {
            // 优先取实际用量（含损耗），否则取标准用量
            BigDecimal qty = item.getActualQuantity() != null ? item.getActualQuantity() : item.getQuantity();
            if (qty == null) qty = BigDecimal.ZERO;
            // 本行项总需求 = 父级数量 × 单件用量
            BigDecimal totalQty = parentQty.multiply(qty).setScale(4, RoundingMode.HALF_UP);

            if ("purchase".equals(item.getSourceType()) && item.getMaterialId() != null) {
                // 外购物料 → 采购需求（记录bomItemId用于查替代料）
                accumDemand(purchaseDemands, item.getMaterialId(), totalQty, requiredDate,
                        sourceOrderId, sourceOrderItemId, item.getId());
            } else {
                // 自制（sourceType="manufacture"或为空）→ 生产需求
                if (item.getProductId() != null) {
                    accumDemand(productionDemands, item.getProductId(), totalQty, requiredDate,
                            sourceOrderId, sourceOrderItemId, null);
                } else if (item.getMaterialId() != null) {
                    // 没有productId只有materialId → 按采购处理（兜底）
                    accumDemand(purchaseDemands, item.getMaterialId(), totalQty, requiredDate,
                            sourceOrderId, sourceOrderItemId, item.getId());
                }
                // 递归展开子项（自制件的下级物料）
                expandBomTree(item.getId(), totalQty, requiredDate,
                        sourceOrderId, sourceOrderItemId,
                        childrenMap, purchaseDemands, productionDemands, depth + 1);
            }
        }
    }

    /**
     * 累积需求到汇总Map
     * @param bomItemId BOM行项ID，用于后续查替代料（外购物料传bomItemId，自制产品传null）
     */
    private void accumDemand(Map<Long, MrpDemandAccum> map, Long id, BigDecimal qty,
                              LocalDate requiredDate, Long orderId, Long orderItemId, Long bomItemId) {
        MrpDemandAccum accum = map.computeIfAbsent(id, k -> new MrpDemandAccum());
        accum.totalQty = accum.totalQty.add(qty);
        if (requiredDate != null && (accum.earliestDate == null || requiredDate.isBefore(accum.earliestDate))) {
            accum.earliestDate = requiredDate;
        }
        if (orderId != null && !accum.sourceOrderIds.contains(orderId)) {
            accum.sourceOrderIds.add(orderId);
        }
        if (orderItemId != null && !accum.sourceOrderItemIds.contains(orderItemId)) {
            accum.sourceOrderItemIds.add(orderItemId);
        }
        if (bomItemId != null) {
            accum.bomItemIds.add(bomItemId);
        }
        // 按订单追踪数量和交期
        if (orderId != null) {
            accum.orderQtyMap.merge(orderId, qty, BigDecimal::add);
            LocalDate existing = accum.orderDateMap.get(orderId);
            if (requiredDate != null && (existing == null || requiredDate.isBefore(existing))) {
                accum.orderDateMap.put(orderId, requiredDate);
            }
            accum.orderItemIdsMap.computeIfAbsent(orderId, k -> new ArrayList<>());
            if (orderItemId != null && !accum.orderItemIdsMap.get(orderId).contains(orderItemId)) {
                accum.orderItemIdsMap.get(orderId).add(orderItemId);
            }
        }
    }

    /**
     * 获取物料的库存可用量（汇总所有仓库）
     */
    private BigDecimal getAvailableStock(Long materialId, Long warehouseId) {
        LambdaQueryWrapper<BizInventory> wrapper = new LambdaQueryWrapper<BizInventory>()
                .eq(BizInventory::getMaterialId, materialId)
                .eq(warehouseId != null, BizInventory::getWarehouseId, warehouseId);
        List<BizInventory> inventories = inventoryMapper.selectList(wrapper);
        return inventories.stream()
                .map(inv -> inv.getAvailableQty() != null ? inv.getAvailableQty() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 获取产品的库存可用量（通过产品ID查物料库存）
     */
    private BigDecimal getAvailableStockByProduct(Long productId) {
        // 产品在库存中可能以materialId形式存在
        return getAvailableStock(productId, null);
    }

    /**
     * 获取物料的在途采购量 = 采购订单未到货量 + 未转单的采购申请量
     * 通过 request_id 关联排除已转成采购订单的申请，避免重复计算
     */
    private BigDecimal getInTransitQty(Long materialId) {
        BigDecimal total = BigDecimal.ZERO;
        Set<Long> linkedRequestIds = new HashSet<>();

        // 第一部分: 采购订单行项（已下单未到货）
        List<BizPurchaseOrder> openOrders = purchaseOrderMapper.selectList(
                new LambdaQueryWrapper<BizPurchaseOrder>()
                        .notIn(BizPurchaseOrder::getStatus, Arrays.asList("closed", "cancelled")));
        if (!openOrders.isEmpty()) {
            List<Long> openOrderIds = openOrders.stream().map(BizPurchaseOrder::getId).collect(Collectors.toList());
            List<BizPurchaseOrderItem> poItems = purchaseOrderItemMapper.selectList(
                    new LambdaQueryWrapper<BizPurchaseOrderItem>()
                            .in(BizPurchaseOrderItem::getPurchaseOrderId, openOrderIds)
                            .eq(BizPurchaseOrderItem::getMaterialId, materialId));
            for (BizPurchaseOrderItem item : poItems) {
                BigDecimal ordered = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
                BigDecimal received = item.getInvTaskQty() != null ? item.getInvTaskQty() : BigDecimal.ZERO;
                total = total.add(ordered.subtract(received).max(BigDecimal.ZERO));
                // 记录已关联的采购申请ID，后续排除
                if (item.getRequestId() != null) {
                    linkedRequestIds.add(item.getRequestId());
                }
            }
        }

        // 第二部分: 采购申请（已审批未转单）
        LambdaQueryWrapper<BizPurchaseRequest> reqWrapper = new LambdaQueryWrapper<BizPurchaseRequest>()
                .eq(BizPurchaseRequest::getMaterialId, materialId)
                .notIn(BizPurchaseRequest::getStatus, Arrays.asList("cancelled", "rejected"));
        if (!linkedRequestIds.isEmpty()) {
            reqWrapper.notIn(BizPurchaseRequest::getId, linkedRequestIds);
        }
        List<BizPurchaseRequest> openRequests = purchaseRequestMapper.selectList(reqWrapper);
        for (BizPurchaseRequest req : openRequests) {
            if (req.getQuantity() != null) {
                total = total.add(req.getQuantity());
            }
        }

        return total;
    }

    /**
     * 获取产品的在制数量（进行中的工单的未完成数量）
     */
    private BigDecimal getInProductionQty(Long productId) {
        // 以生产计划行为准（非取消状态），统计已计划但尚未完成的数量
        // planQty - completedQty 即为已在计划中、仍需生产的数量
        List<BizProductionPlanItem> planItems = planItemMapper.selectList(
                new LambdaQueryWrapper<BizProductionPlanItem>()
                        .eq(BizProductionPlanItem::getProductId, productId)
                        .ne(BizProductionPlanItem::getStatus, "cancelled"));
        return planItems.stream()
                .map(item -> {
                    BigDecimal plan = item.getPlanQty() != null ? item.getPlanQty() : BigDecimal.ZERO;
                    BigDecimal completed = item.getCompletedQty() != null ? item.getCompletedQty() : BigDecimal.ZERO;
                    return plan.subtract(completed).max(BigDecimal.ZERO);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 批量修正：应用最小批量和批量倍数规则
     */
    private BigDecimal applyBatchRules(BigDecimal netQty, BigDecimal minQty, BigDecimal multiple) {
        BigDecimal result = netQty;

        // 最小批量
        if (minQty != null && minQty.compareTo(BigDecimal.ZERO) > 0 && result.compareTo(minQty) < 0) {
            result = minQty;
        }

        // 批量倍数：向上取整到倍数
        if (multiple != null && multiple.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal[] divResult = result.divideAndRemainder(multiple);
            if (divResult[1].compareTo(BigDecimal.ZERO) > 0) {
                result = divResult[0].add(BigDecimal.ONE).multiply(multiple);
            }
        }

        return result.setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * 构建来源追溯摘要
     */
    private String buildSourceSummary(MrpDemandAccum accum) {
        try {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("orderIds", accum.sourceOrderIds);
            summary.put("orderItemIds", accum.sourceOrderItemIds);
            summary.put("totalQty", accum.totalQty);
            return objectMapper.writeValueAsString(summary);
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 列表构建辅助方法 ====================

    private List<Map<String, Object>> buildDemandList(List<BizMrpDemand> demands) {
        Set<Long> matIds = demands.stream().map(BizMrpDemand::getMaterialId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> prodIds = demands.stream().map(BizMrpDemand::getProductId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> matNameMap = matIds.isEmpty() ? Map.of() :
                materialMapper.selectBatchIds(matIds).stream().collect(Collectors.toMap(BizMaterial::getId, BizMaterial::getMaterialName, (a, b) -> a));
        // 产品名称：兼容统一物料模式
        Map<Long, Map<String, String>> prodInfoMap = batchResolveProductInfo(prodIds);

        return demands.stream().map(d -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", d.getId());
            map.put("materialId", d.getMaterialId());
            map.put("materialName", d.getMaterialId() != null ? matNameMap.getOrDefault(d.getMaterialId(), "") : null);
            map.put("productId", d.getProductId());
            Map<String, String> pInfo = d.getProductId() != null ? prodInfoMap.getOrDefault(d.getProductId(), Map.of()) : Map.of();
            map.put("productName", pInfo.getOrDefault("name", ""));
            map.put("demandType", d.getDemandType());
            map.put("itemType", d.getItemType());
            map.put("sourceType", "purchase".equals(d.getItemType()) ? "purchase" : "manufacture");
            map.put("grossQty", d.getGrossQty());
            map.put("onHandQty", d.getOnHandQty());
            map.put("inTransitQty", d.getInTransitQty());
            map.put("inProductionQty", d.getInProductionQty());
            map.put("netQty", d.getNetQty());
            map.put("adjustedQty", d.getAdjustedQty());
            map.put("requiredDate", d.getRequiredDate());
            return map;
        }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> buildPurchaseSuggestionList(List<BizMrpPurchaseSuggestion> suggestions) {
        Set<Long> matIds = suggestions.stream().map(BizMrpPurchaseSuggestion::getMaterialId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, BizMaterial> matMap = matIds.isEmpty() ? Map.of() :
                materialMapper.selectBatchIds(matIds).stream().collect(Collectors.toMap(BizMaterial::getId, m -> m, (a, b) -> a));

        // 批量查询供应商名称
        Set<Long> supplierIds = suggestions.stream().map(BizMrpPurchaseSuggestion::getSupplierId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> supplierNameMap = supplierIds.isEmpty() ? Map.of() :
                supplierMapper.selectBatchIds(supplierIds).stream()
                        .collect(Collectors.toMap(BizSupplier::getId, BizSupplier::getSupplierName, (a, b) -> a));

        return suggestions.stream().map(s -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", s.getId());
            map.put("mrpRunId", s.getMrpRunId());
            map.put("materialId", s.getMaterialId());
            BizMaterial mat = matMap.get(s.getMaterialId());
            map.put("materialName", mat != null ? mat.getMaterialName() : "");
            map.put("materialNo", mat != null ? mat.getMaterialNo() : "");
            map.put("spec", mat != null ? mat.getSpec() : "");
            map.put("unit", mat != null ? mat.getUnit() : "");
            map.put("suggestQty", s.getSuggestQty());
            map.put("requiredDate", s.getRequiredDate());
            map.put("suggestOrderDate", s.getSuggestOrderDate());
            map.put("supplierId", s.getSupplierId());
            map.put("supplierName", s.getSupplierId() != null ? supplierNameMap.getOrDefault(s.getSupplierId(), "") : "");
            map.put("status", s.getStatus());
            map.put("purchaseRequestId", s.getPurchaseRequestId());
            map.put("sourceOrderNos", resolveSourceOrderNos(s.getSourceSummary()));
            map.put("sourceOrders", resolveSourceOrders(s.getSourceSummary()));
            map.put("remark", s.getRemark());
            return map;
        }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> buildProductionSuggestionList(List<BizMrpProductionSuggestion> suggestions) {
        Set<Long> prodIds = suggestions.stream().map(BizMrpProductionSuggestion::getProductId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Map<String, String>> prodInfoMap = batchResolveProductInfo(prodIds);

        // 批量查 demand 表，取在库/在途数量（供参考展示）
        Set<Long> runIds = suggestions.stream().map(BizMrpProductionSuggestion::getMrpRunId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, BizMrpDemand> demandKey2Record = new HashMap<>();
        if (!runIds.isEmpty() && !prodIds.isEmpty()) {
            List<BizMrpDemand> demands = demandMapper.selectList(new LambdaQueryWrapper<BizMrpDemand>()
                    .in(BizMrpDemand::getMrpRunId, runIds)
                    .in(BizMrpDemand::getProductId, prodIds)
                    .eq(BizMrpDemand::getItemType, "manufacture"));
            for (BizMrpDemand d : demands) {
                demandKey2Record.put(d.getMrpRunId() + "_" + d.getProductId(), d);
            }
        }

        // 动态计算净需求：总需求 - 已有生产计划数量（同建议单关联、非已取消）
        Set<Long> suggestionIds = suggestions.stream().map(BizMrpProductionSuggestion::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, BigDecimal> plannedQtyBySuggestion = new HashMap<>();
        if (!suggestionIds.isEmpty()) {
            List<BizProductionPlanItem> planItems = planItemMapper.selectList(
                    new LambdaQueryWrapper<BizProductionPlanItem>()
                            .in(BizProductionPlanItem::getMrpSuggestionId, suggestionIds)
                            .ne(BizProductionPlanItem::getStatus, "cancelled"));
            for (BizProductionPlanItem item : planItems) {
                plannedQtyBySuggestion.merge(item.getMrpSuggestionId(),
                        item.getPlanQty() != null ? item.getPlanQty() : BigDecimal.ZERO,
                        BigDecimal::add);
            }
        }

        return suggestions.stream().map(s -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", s.getId());
            map.put("mrpRunId", s.getMrpRunId());
            map.put("productId", s.getProductId());
            Map<String, String> info = prodInfoMap.getOrDefault(s.getProductId(), Map.of());
            map.put("productName", info.getOrDefault("name", ""));
            map.put("productNo", info.getOrDefault("no", ""));
            map.put("spec", info.getOrDefault("spec", ""));
            map.put("unit", info.getOrDefault("unit", ""));
            map.put("suggestQty", s.getSuggestQty());
            map.put("grossQty", s.getGrossQty());
            map.put("maxProducibleQty", s.getMaxProducibleQty());
            map.put("actualQty", s.getActualQty());
            map.put("requiredDate", s.getRequiredDate());
            map.put("suggestStartDate", s.getSuggestStartDate());
            map.put("isSemiFinished", s.getIsSemiFinished());
            map.put("semiHandleType", s.getSemiHandleType());
            map.put("status", s.getStatus());
            map.put("planItemId", s.getPlanItemId());
            map.put("sourceOrderNos", resolveSourceOrderNos(s.getSourceSummary()));
            map.put("sourceOrders", resolveSourceOrders(s.getSourceSummary()));
            map.put("remark", s.getRemark());
            // 净需求数量：总需求 - 在手库存 - 已在产 - 已转计划，实时反映还需规划的量
            BigDecimal plannedQty = plannedQtyBySuggestion.getOrDefault(s.getId(), BigDecimal.ZERO);
            BizMrpDemand demand = demandKey2Record.get(s.getMrpRunId() + "_" + s.getProductId());
            BigDecimal onHand = demand != null && demand.getOnHandQty() != null ? demand.getOnHandQty() : BigDecimal.ZERO;
            BigDecimal inProd = demand != null && demand.getInProductionQty() != null ? demand.getInProductionQty() : BigDecimal.ZERO;
            BigDecimal dynamicNetQty = s.getGrossQty() != null
                    ? s.getGrossQty().subtract(onHand).subtract(inProd).subtract(plannedQty).max(BigDecimal.ZERO) : null;
            map.put("netQty", dynamicNetQty);
            map.put("plannedQty", plannedQty);
            map.put("onHandQty", demand != null ? demand.getOnHandQty() : null);
            map.put("inProductionQty", demand != null ? demand.getInProductionQty() : null);

            // 解析sourceSummary中的竞争组与约束信息
            Map<String, Object> competitionInfo = parseCompetitionInfo(s.getSourceSummary());
            map.put("materialConstrained", competitionInfo.getOrDefault("constrained", false));
            map.put("originalNetQty", competitionInfo.get("originalNetQty"));
            map.put("maxProducible", competitionInfo.get("maxProducible"));
            map.put("competitionGroup", competitionInfo.get("competitionGroup"));
            map.put("contestedMaterials", competitionInfo.getOrDefault("contestedMaterials", List.of()));
            map.put("competitors", competitionInfo.getOrDefault("competitors", List.of()));
            map.put("contestedMaterialStocks", competitionInfo.getOrDefault("contestedMaterialStocks", Map.of()));
            map.put("perUnitMaterialNeeds", competitionInfo.getOrDefault("perUnitMaterialNeeds", Map.of()));

            return map;
        }).collect(Collectors.toList());
    }

    /**
     * 从sourceSummary JSON解析竞争组与物料约束信息
     */
    private Map<String, Object> parseCompetitionInfo(String sourceSummary) {
        Map<String, Object> result = new HashMap<>();
        if (!StringUtils.hasText(sourceSummary)) return result;
        try {
            Map<String, Object> summary = objectMapper.readValue(sourceSummary, Map.class);
            if (summary.containsKey("constrained")) result.put("constrained", summary.get("constrained"));
            if (summary.containsKey("originalNetQty")) result.put("originalNetQty", summary.get("originalNetQty"));
            if (summary.containsKey("maxProducible")) result.put("maxProducible", summary.get("maxProducible"));
            if (summary.containsKey("competitionGroup")) result.put("competitionGroup", summary.get("competitionGroup"));
            if (summary.containsKey("contestedMaterials")) result.put("contestedMaterials", summary.get("contestedMaterials"));
            if (summary.containsKey("competitors")) result.put("competitors", summary.get("competitors"));
            if (summary.containsKey("contestedMaterialStocks")) result.put("contestedMaterialStocks", summary.get("contestedMaterialStocks"));
            if (summary.containsKey("perUnitMaterialNeeds")) result.put("perUnitMaterialNeeds", summary.get("perUnitMaterialNeeds"));
        } catch (Exception e) {
            log.debug("解析竞争信息失败: {}", e.getMessage());
        }
        return result;
    }

    // ==================== 导出方法 ====================

    /**
     * 获取全部采购建议（用于导出）
     */
    public List<Map<String, Object>> getAllPurchaseSuggestions(String status, LocalDate dateFrom, LocalDate dateTo) {
        LambdaQueryWrapper<BizMrpPurchaseSuggestion> wrapper = new LambdaQueryWrapper<BizMrpPurchaseSuggestion>()
                .eq(StringUtils.hasText(status), BizMrpPurchaseSuggestion::getStatus, status)
                .ge(dateFrom != null, BizMrpPurchaseSuggestion::getRequiredDate, dateFrom)
                .le(dateTo != null, BizMrpPurchaseSuggestion::getRequiredDate, dateTo)
                .orderByAsc(BizMrpPurchaseSuggestion::getRequiredDate);
        return buildPurchaseSuggestionList(purchaseSuggestionMapper.selectList(wrapper));
    }

    /**
     * 获取全部生产建议（用于导出）
     */
    public List<Map<String, Object>> getAllProductionSuggestions(String status, LocalDate dateFrom, LocalDate dateTo) {
        LambdaQueryWrapper<BizMrpProductionSuggestion> wrapper = new LambdaQueryWrapper<BizMrpProductionSuggestion>()
                .eq(StringUtils.hasText(status), BizMrpProductionSuggestion::getStatus, status)
                .ge(dateFrom != null, BizMrpProductionSuggestion::getRequiredDate, dateFrom)
                .le(dateTo != null, BizMrpProductionSuggestion::getRequiredDate, dateTo)
                .orderByAsc(BizMrpProductionSuggestion::getRequiredDate);
        return buildProductionSuggestionList(productionSuggestionMapper.selectList(wrapper));
    }

    // ==================== 辅助方法 ====================

    /**
     * 从sourceSummary JSON中解析出来源订单号
     */
    private String resolveSourceOrderNos(String sourceSummary) {
        if (!StringUtils.hasText(sourceSummary)) return "";
        try {
            Map<String, Object> summary = objectMapper.readValue(sourceSummary, Map.class);
            Object orderIdsObj = summary.get("orderIds");
            if (orderIdsObj instanceof List) {
                List<Number> orderIds = (List<Number>) orderIdsObj;
                if (orderIds.isEmpty()) return "";
                List<Long> ids = orderIds.stream().map(Number::longValue).collect(Collectors.toList());
                List<BizSalesOrder> orders = salesOrderMapper.selectBatchIds(ids);
                return orders.stream().map(BizSalesOrder::getOrderNo).filter(Objects::nonNull)
                        .collect(Collectors.joining(", "));
            }
        } catch (Exception e) {
            log.debug("解析sourceSummary失败: {}", e.getMessage());
        }
        return "";
    }

    /**
     * 从sourceSummary中解析来源订单列表（含ID和订单号），供前端点击查看详情
     */
    private List<Map<String, Object>> resolveSourceOrders(String sourceSummary) {
        if (!StringUtils.hasText(sourceSummary)) return List.of();
        try {
            Map<String, Object> summary = objectMapper.readValue(sourceSummary, Map.class);
            Object orderIdsObj = summary.get("orderIds");
            if (orderIdsObj instanceof List) {
                List<Number> orderIds = (List<Number>) orderIdsObj;
                if (orderIds.isEmpty()) return List.of();
                List<Long> ids = orderIds.stream().map(Number::longValue).collect(Collectors.toList());
                List<BizSalesOrder> orders = salesOrderMapper.selectBatchIds(ids);
                return orders.stream().filter(o -> o.getOrderNo() != null).map(o -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", o.getId());
                    m.put("orderNo", o.getOrderNo());
                    return m;
                }).collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.debug("解析sourceOrders失败: {}", e.getMessage());
        }
        return List.of();
    }

    /**
     * 统一解析产品信息（兼容统一物料模式）
     * 销售订单中的productId实际可能是materialId，因此先查biz_product，找不到再查biz_material
     */
    private Map<String, String> resolveProductInfo(Long productId) {
        Map<String, String> info = new LinkedHashMap<>();
        // 先查产品表
        BizProduct prod = productMapper.selectById(productId);
        if (prod != null && StringUtils.hasText(prod.getProductName())) {
            info.put("no", prod.getProductNo());
            info.put("name", prod.getProductName());
            info.put("spec", prod.getSpec());
            info.put("unit", prod.getUnit());
            info.put("leadTime", prod.getProductionLeadTime() != null ? String.valueOf(prod.getProductionLeadTime()) : "0");
            info.put("minQty", prod.getMinProductionQty() != null ? prod.getMinProductionQty().toPlainString() : null);
            info.put("multiple", prod.getProductionQtyMultiple() != null ? prod.getProductionQtyMultiple().toPlainString() : null);
            return info;
        }
        // 回退查物料表（统一物料模式下productId实际是materialId）
        BizMaterial mat = materialMapper.selectById(productId);
        if (mat != null) {
            info.put("no", mat.getMaterialNo());
            info.put("name", mat.getMaterialName());
            info.put("spec", mat.getSpec());
            info.put("unit", mat.getUnit());
            info.put("leadTime", mat.getProductionLeadTime() != null ? String.valueOf(mat.getProductionLeadTime()) : "0");
            info.put("minQty", mat.getMinOrderQty() != null ? mat.getMinOrderQty().toPlainString() : null);
            info.put("multiple", mat.getOrderQtyMultiple() != null ? mat.getOrderQtyMultiple().toPlainString() : null);
        }
        return info;
    }

    /**
     * 批量解析产品信息（兼容统一物料模式）
     */
    private Map<Long, Map<String, String>> batchResolveProductInfo(Set<Long> productIds) {
        Map<Long, Map<String, String>> result = new HashMap<>();
        if (productIds.isEmpty()) return result;
        // 先批量查产品表
        Map<Long, BizProduct> prodMap = productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(BizProduct::getId, p -> p, (a, b) -> a));
        Set<Long> notFound = new HashSet<>();
        for (Long id : productIds) {
            BizProduct prod = prodMap.get(id);
            if (prod != null && StringUtils.hasText(prod.getProductName())) {
                Map<String, String> info = new LinkedHashMap<>();
                info.put("no", prod.getProductNo());
                info.put("name", prod.getProductName());
                info.put("spec", prod.getSpec());
                info.put("unit", prod.getUnit());
                result.put(id, info);
            } else {
                notFound.add(id);
            }
        }
        // 回退查物料表
        if (!notFound.isEmpty()) {
            Map<Long, BizMaterial> matMap = materialMapper.selectBatchIds(notFound).stream()
                    .collect(Collectors.toMap(BizMaterial::getId, m -> m, (a, b) -> a));
            for (Long id : notFound) {
                BizMaterial mat = matMap.get(id);
                if (mat != null) {
                    Map<String, String> info = new LinkedHashMap<>();
                    info.put("no", mat.getMaterialNo());
                    info.put("name", mat.getMaterialName());
                    info.put("spec", mat.getSpec());
                    info.put("unit", mat.getUnit());
                    result.put(id, info);
                }
            }
        }
        return result;
    }

    // ==================== 物料竞争组计算 ====================

    /**
     * 计算并更新物料竞争组
     * 多个生产建议共享同一原材料时，通过连通分量算法分组，将组信息写入sourceSummary
     */
    private void computeAndUpdateCompetitionGroups(
            Map<Long, List<Long>> productSuggestionIdMap,
            Map<Long, Set<Long>> productUsedMaterials,
            Map<Long, Set<Long>> materialAllocatedBy,
            Map<Long, Map<Long, BigDecimal>> productPerUnitNeeds,
            Map<Long, BigDecimal> productSuggestQty,
            Map<Long, BigDecimal> materialPool) {
        if (productSuggestionIdMap.size() <= 1) return;

        // 找出被2个以上产品共享且总需求超过库存的物料（真正的竞争物料）
        Map<Long, Set<Long>> contestedMaterials = new HashMap<>();
        for (Map.Entry<Long, Set<Long>> e : materialAllocatedBy.entrySet()) {
            if (e.getValue().size() <= 1) continue;

            Long matId = e.getKey();
            Set<Long> productIds = e.getValue();
            // 计算所有产品对该物料的总需求量
            BigDecimal totalDemand = BigDecimal.ZERO;
            for (Long pid : productIds) {
                Map<Long, BigDecimal> perUnit = productPerUnitNeeds.getOrDefault(pid, Map.of());
                BigDecimal sugQty = productSuggestQty.getOrDefault(pid, BigDecimal.ZERO);
                BigDecimal perUnitQty = perUnit.getOrDefault(matId, BigDecimal.ZERO);
                totalDemand = totalDemand.add(perUnitQty.multiply(sugQty));
            }
            // 只有总需求 > 库存时才算竞争
            BigDecimal available = materialPool.getOrDefault(matId, BigDecimal.ZERO);
            if (totalDemand.compareTo(available) > 0) {
                contestedMaterials.put(matId, productIds);
            }
        }
        if (contestedMaterials.isEmpty()) return;

        // 查竞争物料名称和单位
        Map<Long, BizMaterial> matEntityMap = materialMapper.selectBatchIds(contestedMaterials.keySet()).stream()
                .collect(Collectors.toMap(BizMaterial::getId, m -> m, (a, b) -> a));
        Map<Long, String> matNameMap = matEntityMap.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getMaterialName(), (a, b) -> a));

        // 构建竞争物料的库存信息 {物料名称 -> {stock: 库存量, unit: 单位}}
        Map<String, Map<String, Object>> contestedMaterialStocks = new LinkedHashMap<>();
        for (Long matId : contestedMaterials.keySet()) {
            String matName = matNameMap.get(matId);
            if (matName == null) continue;
            BizMaterial mat = matEntityMap.get(matId);
            Map<String, Object> stockInfo = new LinkedHashMap<>();
            stockInfo.put("stock", materialPool.getOrDefault(matId, BigDecimal.ZERO));
            stockInfo.put("unit", mat != null ? mat.getUnit() : "");
            stockInfo.put("materialId", matId);
            contestedMaterialStocks.put(matName, stockInfo);
        }

        // BFS连通分量：通过共享物料连接的产品归入同一竞争组
        Map<Long, Integer> productGroupMap = new HashMap<>();
        int groupCounter = 0;
        Set<Long> visited = new HashSet<>();

        for (Long pid : productSuggestionIdMap.keySet()) {
            if (visited.contains(pid)) continue;
            Set<Long> myMats = productUsedMaterials.getOrDefault(pid, Set.of());
            boolean hasContest = myMats.stream().anyMatch(contestedMaterials::containsKey);
            if (!hasContest) continue;

            groupCounter++;
            Queue<Long> queue = new LinkedList<>();
            queue.add(pid);
            while (!queue.isEmpty()) {
                Long curr = queue.poll();
                if (visited.contains(curr)) continue;
                visited.add(curr);
                productGroupMap.put(curr, groupCounter);
                Set<Long> currMats = productUsedMaterials.getOrDefault(curr, Set.of());
                for (Long matId : currMats) {
                    Set<Long> competitors = contestedMaterials.get(matId);
                    if (competitors != null) {
                        for (Long comp : competitors) {
                            if (!visited.contains(comp)) queue.add(comp);
                        }
                    }
                }
            }
        }
        if (productGroupMap.isEmpty()) return;

        // 查产品名称用于说明
        Map<Long, Map<String, String>> prodInfoMap = batchResolveProductInfo(new HashSet<>(productGroupMap.keySet()));

        // 更新每个竞争组成员的sourceSummary（遍历所有建议ID，同一产品可能有多条建议）
        for (Map.Entry<Long, Integer> ge : productGroupMap.entrySet()) {
            Long pid = ge.getKey();
            Integer group = ge.getValue();
            List<Long> suggestionIds = productSuggestionIdMap.get(pid);
            if (suggestionIds == null || suggestionIds.isEmpty()) continue;

            String groupLabel = String.valueOf((char) ('A' + (group - 1) % 26));

            // 该产品涉及的竞争物料名称
            Set<Long> myMats = productUsedMaterials.getOrDefault(pid, Set.of());
            List<String> contestedNames = myMats.stream()
                    .filter(contestedMaterials::containsKey)
                    .map(matNameMap::get).filter(Objects::nonNull)
                    .collect(Collectors.toList());

            // 同组中其他产品名称
            List<String> competitorNames = productGroupMap.entrySet().stream()
                    .filter(e -> e.getValue().equals(group) && !e.getKey().equals(pid))
                    .map(e -> prodInfoMap.getOrDefault(e.getKey(), Map.of()).getOrDefault("name", ""))
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());

            // 该产品的单位物料消耗（用于前端计算）
            Map<Long, BigDecimal> perUnit = productPerUnitNeeds.getOrDefault(pid, Map.of());
            Map<String, Object> perUnitDisplay = new LinkedHashMap<>();
            for (Map.Entry<Long, BigDecimal> matEntry : perUnit.entrySet()) {
                String matName = matNameMap.get(matEntry.getKey());
                if (matName != null && contestedMaterials.containsKey(matEntry.getKey())) {
                    perUnitDisplay.put(matName, matEntry.getValue());
                }
            }

            // 更新该产品的所有建议（同产品多订单拆分的情况）
            for (Long suggestionId : suggestionIds) {
                BizMrpProductionSuggestion sug = productionSuggestionMapper.selectById(suggestionId);
                if (sug == null) continue;

                try {
                    Map<String, Object> summary = sug.getSourceSummary() != null
                            ? objectMapper.readValue(sug.getSourceSummary(), LinkedHashMap.class)
                            : new LinkedHashMap<>();
                    summary.put("competitionGroup", groupLabel);
                    summary.put("contestedMaterials", contestedNames);
                    summary.put("competitors", competitorNames);
                    summary.put("contestedMaterialStocks", contestedMaterialStocks);
                    summary.put("perUnitMaterialNeeds", perUnitDisplay);
                    sug.setSourceSummary(objectMapper.writeValueAsString(summary));
                    productionSuggestionMapper.updateById(sug);
                } catch (Exception e) {
                    log.debug("更新竞争组信息失败: {}", e.getMessage());
                }
            }
        }
    }

    // ==================== 原材料约束计算 ====================

    /**
     * 获取产品BOM的单位原材料用量（生产1个该产品需要多少原材料）
     * 仅展开当前BOM版本内的树形结构，自制子件不跨BOM递归（由各自的生产建议独立处理）
     */
    private Map<Long, BigDecimal> getPerUnitMaterialNeeds(Long productId) {
        BizBomVersion activeBom = bomVersionMapper.selectOne(
                new LambdaQueryWrapper<BizBomVersion>()
                        .eq(BizBomVersion::getProductId, productId)
                        .eq(BizBomVersion::getStatus, "active")
                        .last("LIMIT 1"));
        if (activeBom == null) return Collections.emptyMap();

        List<BizBomItem> allItems = bomItemMapper.selectList(
                new LambdaQueryWrapper<BizBomItem>()
                        .eq(BizBomItem::getBomVersionId, activeBom.getId())
                        .orderByAsc(BizBomItem::getSortOrder));

        Map<Long, List<BizBomItem>> childrenMap = allItems.stream()
                .collect(Collectors.groupingBy(item ->
                        item.getParentItemId() != null && item.getParentItemId() != 0
                                ? item.getParentItemId() : 0L));

        Map<Long, BigDecimal> materialNeeds = new LinkedHashMap<>();
        collectPurchaseMaterialNeeds(0L, BigDecimal.ONE, childrenMap, materialNeeds, 0);
        return materialNeeds;
    }

    /**
     * 递归收集BOM树中所有外购物料的单位用量（含损耗）
     */
    private void collectPurchaseMaterialNeeds(Long parentItemId, BigDecimal parentQty,
                                               Map<Long, List<BizBomItem>> childrenMap,
                                               Map<Long, BigDecimal> materialNeeds, int depth) {
        if (depth > 10) return;
        List<BizBomItem> children = childrenMap.getOrDefault(parentItemId, List.of());

        for (BizBomItem item : children) {
            // 使用实际用量(含损耗)，与MRP需求计算保持一致
            BigDecimal qty = item.getActualQuantity() != null ? item.getActualQuantity() : item.getQuantity();
            if (qty == null) qty = BigDecimal.ZERO;
            BigDecimal totalQty = parentQty.multiply(qty).setScale(4, RoundingMode.HALF_UP);

            if ("purchase".equals(item.getSourceType()) && item.getMaterialId() != null) {
                materialNeeds.merge(item.getMaterialId(), totalQty, BigDecimal::add);
            } else {
                // 自制件：继续展开同BOM内的子项
                collectPurchaseMaterialNeeds(item.getId(), totalQty, childrenMap, materialNeeds, depth + 1);
            }
        }
    }

    /**
     * 根据单位原材料用量和库存池，计算最大可生产数量
     * @return 最大可生产数量，null表示无BOM约束（无外购物料）
     */
    private BigDecimal calculateMaxProducibleQty(Map<Long, BigDecimal> perUnitNeeds,
                                                   Map<Long, BigDecimal> materialPool) {
        if (perUnitNeeds.isEmpty()) return null;

        BigDecimal maxProducible = null;
        for (Map.Entry<Long, BigDecimal> entry : perUnitNeeds.entrySet()) {
            Long materialId = entry.getKey();
            BigDecimal perUnit = entry.getValue();
            if (perUnit.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal available = materialPool.containsKey(materialId)
                    ? materialPool.get(materialId)
                    : getAvailableStock(materialId, null);
            BigDecimal canProduce = available.divide(perUnit, 4, RoundingMode.FLOOR);

            if (maxProducible == null || canProduce.compareTo(maxProducible) < 0) {
                maxProducible = canProduce;
            }
        }
        return maxProducible != null ? maxProducible.max(BigDecimal.ZERO) : null;
    }

    /**
     * 从原材料库存池中扣减已分配给生产的用量
     */
    private void deductMaterialsFromPool(Map<Long, BigDecimal> perUnitNeeds,
                                          BigDecimal produceQty,
                                          Map<Long, BigDecimal> materialPool) {
        for (Map.Entry<Long, BigDecimal> entry : perUnitNeeds.entrySet()) {
            Long materialId = entry.getKey();
            BigDecimal perUnit = entry.getValue();
            BigDecimal used = perUnit.multiply(produceQty).setScale(4, RoundingMode.HALF_UP);
            materialPool.merge(materialId, used.negate(), BigDecimal::add);
        }
    }

    /**
     * 内部辅助类：MRP需求累积器
     */
    private static class MrpDemandAccum {
        BigDecimal totalQty = BigDecimal.ZERO;
        LocalDate earliestDate = null;
        List<Long> sourceOrderIds = new ArrayList<>();
        List<Long> sourceOrderItemIds = new ArrayList<>();
        Set<Long> bomItemIds = new HashSet<>();
        /** 按订单追踪数量和交期，用于按订单拆分生产建议 */
        Map<Long, BigDecimal> orderQtyMap = new LinkedHashMap<>();
        Map<Long, LocalDate> orderDateMap = new LinkedHashMap<>();
        Map<Long, List<Long>> orderItemIdsMap = new LinkedHashMap<>();
    }
}
