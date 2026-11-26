package com.aimes.modules.mrp.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.PageResult;
import com.aimes.common.utils.CodeUtil;
import com.aimes.modules.master.entity.BizMaterial;
import com.aimes.modules.master.entity.BizProduct;
import com.aimes.modules.master.mapper.BizMaterialMapper;
import com.aimes.modules.master.mapper.BizProductMapper;
import com.aimes.modules.mrp.entity.*;
import com.aimes.modules.mrp.mapper.*;
import com.aimes.modules.order.entity.BizSalesOrder;
import com.aimes.modules.order.mapper.BizSalesOrderMapper;
import com.aimes.modules.bom.entity.BizBomVersion;
import com.aimes.modules.bom.service.BizBomService;
import com.aimes.modules.inventory.entity.BizInvReservation;
import com.aimes.modules.inventory.entity.BizInventory;
import com.aimes.modules.inventory.mapper.BizInvReservationMapper;
import com.aimes.modules.inventory.mapper.BizInventoryMapper;
import com.aimes.modules.inventory.service.InventoryService;
import com.aimes.modules.production.entity.BizWoProcessTask;
import com.aimes.modules.production.entity.BizWorkOrder;
import com.aimes.modules.production.mapper.BizWoProcessTaskMapper;
import com.aimes.modules.production.service.BizWorkOrderService;
import com.aimes.modules.system.service.SysNotificationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 生产计划服务
 * 生产计划状态: draft → scheduled(待派发) → confirmed(派发/已确认) → in_progress → completed / cancelled
 * 计划行项状态: pending → partial_released → fully_released → completed
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BizProductionPlanService extends ServiceImpl<BizProductionPlanMapper, BizProductionPlan> {

    private final BizProductionPlanItemMapper planItemMapper;
    private final BizProductionPlanItemStepMapper planItemStepMapper;
    private final BizProductionPlanOrderRelMapper planOrderRelMapper;
    private final BizPlanItemMaterialMapper planItemMaterialMapper;
    private final BizMrpProductionSuggestionMapper productionSuggestionMapper;
    private final BizMrpService mrpService;
    private final BizProductMapper productMapper;
    private final BizMaterialMapper materialMapper;
    private final BizWorkOrderService workOrderService;
    private final BizWoProcessTaskMapper woProcessTaskMapper;
    private final BizSalesOrderMapper salesOrderMapper;
    private final com.aimes.modules.order.mapper.BizSalesOrderItemMapper salesOrderItemMapper;
    private final BizBomService bomService;
    private final BizInventoryMapper inventoryMapper;
    private final InventoryService inventoryService;
    private final BizInvReservationMapper reservationMapper;
    private final SysNotificationService notificationService;
    private final com.aimes.modules.production.service.BizProcessDrawingService drawingService;
    private final com.aimes.modules.aftersale.mapper.BizServiceOrderPartMapper serviceOrderPartMapper;
    private final org.springframework.context.ApplicationContext applicationContext;

    // ==================== 计划CRUD ====================

    /**
     * 分页查询生产计划
     */
    public PageResult<Map<String, Object>> getPage(String planNo, String planName, String status, String spec, String orderNo, Map<String, String> extFieldFilters, int pageNum, int pageSize) {
        return getPage(planNo, planName, null, null, status, spec, orderNo, extFieldFilters, pageNum, pageSize);
    }

    public PageResult<Map<String, Object>> getPage(String planNo, String planName, String materialNo, String materialName,
                                                    String status, String spec, String orderNo,
                                                    Map<String, String> extFieldFilters, int pageNum, int pageSize) {
        LambdaQueryWrapper<BizProductionPlan> wrapper = new LambdaQueryWrapper<BizProductionPlan>()
                .like(StringUtils.hasText(planNo), BizProductionPlan::getPlanNo, planNo)
                .like(StringUtils.hasText(planName), BizProductionPlan::getPlanName, planName)
                .eq(StringUtils.hasText(status), BizProductionPlan::getStatus, status)
                .orderByDesc(BizProductionPlan::getCreateTime);

        // 物料编号 / 物料名称过滤: 物料表查 → productIds → planItem → planIds
        if (StringUtils.hasText(materialNo) || StringUtils.hasText(materialName)) {
            LambdaQueryWrapper<BizMaterial> mw = new LambdaQueryWrapper<BizMaterial>()
                    .like(StringUtils.hasText(materialNo), BizMaterial::getMaterialNo, materialNo)
                    .like(StringUtils.hasText(materialName), BizMaterial::getMaterialName, materialName);
            List<Long> mIds = materialMapper.selectList(mw).stream()
                    .map(BizMaterial::getId).collect(Collectors.toList());
            if (mIds.isEmpty()) return new PageResult<>(List.of(), 0L, pageNum, pageSize);
            List<Long> pIds = planItemMapper.selectList(
                    new LambdaQueryWrapper<BizProductionPlanItem>()
                            .in(BizProductionPlanItem::getProductId, mIds)
                            .select(BizProductionPlanItem::getPlanId))
                    .stream().map(BizProductionPlanItem::getPlanId).distinct().collect(Collectors.toList());
            if (pIds.isEmpty()) return new PageResult<>(List.of(), 0L, pageNum, pageSize);
            wrapper.in(BizProductionPlan::getId, pIds);
        }

        // 按销售订单号模糊筛选（orderNo -> orderId -> planItemId -> planId）
        if (StringUtils.hasText(orderNo)) {
            List<Long> matchedOrderIds = salesOrderMapper.selectList(
                    new LambdaQueryWrapper<BizSalesOrder>().like(BizSalesOrder::getOrderNo, orderNo))
                    .stream().map(BizSalesOrder::getId).collect(Collectors.toList());
            if (matchedOrderIds.isEmpty()) {
                return new PageResult<>(List.of(), 0L, pageNum, pageSize);
            }
            List<Long> matchedPlanItemIds = planOrderRelMapper.selectList(
                    new LambdaQueryWrapper<BizProductionPlanOrderRel>()
                            .in(BizProductionPlanOrderRel::getOrderId, matchedOrderIds))
                    .stream().map(BizProductionPlanOrderRel::getPlanItemId).distinct().collect(Collectors.toList());
            if (matchedPlanItemIds.isEmpty()) {
                return new PageResult<>(List.of(), 0L, pageNum, pageSize);
            }
            List<Long> matchedPlanIds = planItemMapper.selectList(
                    new LambdaQueryWrapper<BizProductionPlanItem>()
                            .in(BizProductionPlanItem::getId, matchedPlanItemIds)
                            .select(BizProductionPlanItem::getPlanId))
                    .stream().map(BizProductionPlanItem::getPlanId).distinct().collect(Collectors.toList());
            if (matchedPlanIds.isEmpty()) {
                return new PageResult<>(List.of(), 0L, pageNum, pageSize);
            }
            wrapper.in(BizProductionPlan::getId, matchedPlanIds);
        }

        if (extFieldFilters != null && !extFieldFilters.isEmpty()) {
            extFieldFilters.forEach((k, v) -> {
                if (StringUtils.hasText(v)) {
                    wrapper.apply("JSON_UNQUOTE(JSON_EXTRACT(ext_fields, '$." + k + "')) LIKE {0}", "%" + v + "%");
                }
            });
        }

        // 按规格型号筛选：先查匹配物料ID，再查关联这些物料的计划ID
        // 支持单值(NBR / Ø15mm) 与 复合(压力等级:300 材质:NBR) 两种输入
        if (StringUtils.hasText(spec)) {
            LambdaQueryWrapper<BizMaterial> sw = new LambdaQueryWrapper<>();
            com.aimes.common.utils.SpecQueryUtil.applyToBizMaterial(sw, spec);
            List<Long> matchedProductIds = materialMapper.selectList(sw)
                    .stream().map(BizMaterial::getId).collect(Collectors.toList());
            if (matchedProductIds.isEmpty()) {
                return new PageResult<>(List.of(), 0L, pageNum, pageSize);
            }
            List<Long> matchedPlanIds = planItemMapper.selectList(
                    new LambdaQueryWrapper<BizProductionPlanItem>()
                            .in(BizProductionPlanItem::getProductId, matchedProductIds)
                            .select(BizProductionPlanItem::getPlanId))
                    .stream().map(BizProductionPlanItem::getPlanId).distinct().collect(Collectors.toList());
            if (matchedPlanIds.isEmpty()) {
                return new PageResult<>(List.of(), 0L, pageNum, pageSize);
            }
            wrapper.in(BizProductionPlan::getId, matchedPlanIds);
        }

        Page<BizProductionPlan> page = page(new Page<>(pageNum, pageSize), wrapper);

        List<Map<String, Object>> list = page.getRecords().stream().map(plan -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", plan.getId());
            map.put("planNo", plan.getPlanNo());
            map.put("planName", plan.getPlanName());
            map.put("planDateFrom", plan.getPlanDateFrom());
            map.put("planDateTo", plan.getPlanDateTo());
            map.put("status", plan.getStatus());
            map.put("createType", plan.getCreateType());
            map.put("remark", plan.getRemark());
            map.put("extFields", plan.getExtFields());
            map.put("createTime", plan.getCreateTime());

            // 计算汇总信息
            List<BizProductionPlanItem> items = planItemMapper.selectList(
                    new LambdaQueryWrapper<BizProductionPlanItem>()
                            .eq(BizProductionPlanItem::getPlanId, plan.getId()));
            map.put("itemCount", items.size());
            map.put("totalPlanQty", items.stream()
                    .map(i -> i.getPlanQty() != null ? i.getPlanQty() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            map.put("totalReleasedQty", items.stream()
                    .map(i -> i.getReleasedQty() != null ? i.getReleasedQty() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            map.put("totalCompletedQty", items.stream()
                    .map(i -> i.getCompletedQty() != null ? i.getCompletedQty() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));

            // 产品名称/编号列表（兼容统一物料模式）
            Set<Long> pIds = items.stream().map(BizProductionPlanItem::getProductId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            if (!pIds.isEmpty()) {
                Map<Long, Map<String, String>> pInfoMap = batchResolveProductInfo(pIds);
                String productNames = pInfoMap.values().stream()
                        .map(info -> info.get("name"))
                        .filter(Objects::nonNull).distinct().collect(Collectors.joining("、"));
                String productNos = pInfoMap.values().stream()
                        .map(info -> info.get("no"))
                        .filter(Objects::nonNull).distinct().collect(Collectors.joining("、"));
                String productSpecs = pInfoMap.values().stream()
                        .map(info -> info.get("spec"))
                        .filter(Objects::nonNull).filter(s -> !s.isEmpty()).distinct()
                        .collect(Collectors.joining(" | "));
                map.put("productNames", productNames);
                map.put("productNos", productNos);
                map.put("productSpecs", productSpecs);
            } else {
                map.put("productNames", "");
                map.put("productNos", "");
                map.put("productSpecs", "");
            }

            // 关联销售订单（跨行项去重）
            Set<Long> itemIds = items.stream().map(BizProductionPlanItem::getId).collect(Collectors.toSet());
            List<Map<String, Object>> orderRelList = new ArrayList<>();
            if (!itemIds.isEmpty()) {
                List<BizProductionPlanOrderRel> rels = planOrderRelMapper.selectList(
                        new LambdaQueryWrapper<BizProductionPlanOrderRel>()
                                .in(BizProductionPlanOrderRel::getPlanItemId, itemIds));
                Set<Long> relOrderIds = rels.stream().map(BizProductionPlanOrderRel::getOrderId)
                        .filter(Objects::nonNull).collect(Collectors.toSet());
                Map<Long, String> orderNoMap = relOrderIds.isEmpty() ? Map.of() :
                        salesOrderMapper.selectBatchIds(relOrderIds).stream()
                                .collect(Collectors.toMap(BizSalesOrder::getId, BizSalesOrder::getOrderNo, (a, b) -> a));
                // 按 orderId 去重
                rels.stream().collect(Collectors.toMap(BizProductionPlanOrderRel::getOrderId,
                        r -> r, (a, b) -> a, LinkedHashMap::new)).forEach((oid, rel) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("orderId", oid);
                    m.put("orderNo", orderNoMap.getOrDefault(oid, ""));
                    orderRelList.add(m);
                });
            }
            map.put("orderRels", orderRelList);
            map.put("orderNos", orderRelList.stream()
                    .map(r -> (String) r.get("orderNo")).filter(s -> s != null && !s.isEmpty())
                    .collect(Collectors.joining("、")));

            return map;
        }).collect(Collectors.toList());

        return new PageResult<>(list, page.getTotal(), pageNum, pageSize);
    }

    /**
     * 生产计划统计数据
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalPlanCount", count());
        stats.put("draftCount", count(new LambdaQueryWrapper<BizProductionPlan>().eq(BizProductionPlan::getStatus, "draft")));
        stats.put("scheduledCount", count(new LambdaQueryWrapper<BizProductionPlan>().eq(BizProductionPlan::getStatus, "scheduled")));
        stats.put("confirmedCount", count(new LambdaQueryWrapper<BizProductionPlan>().eq(BizProductionPlan::getStatus, "confirmed")));
        stats.put("inProgressCount", count(new LambdaQueryWrapper<BizProductionPlan>().eq(BizProductionPlan::getStatus, "in_progress")));
        stats.put("completedCount", count(new LambdaQueryWrapper<BizProductionPlan>().eq(BizProductionPlan::getStatus, "completed")));

        // 汇总所有行项的计划数量和完成数量
        List<BizProductionPlanItem> allItems = planItemMapper.selectList(null);
        stats.put("totalPlanQty", allItems.stream()
                .map(i -> i.getPlanQty() != null ? i.getPlanQty() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        stats.put("totalCompletedQty", allItems.stream()
                .map(i -> i.getCompletedQty() != null ? i.getCompletedQty() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        return stats;
    }

    /**
     * 获取计划导出数据
     */
    public List<Map<String, Object>> getExportData(Long planId) {
        Map<String, Object> detail = getDetail(planId);
        List<Map<String, Object>> items = (List<Map<String, Object>>) detail.get("items");
        // 给每个行项加上计划头信息 + 来源单据号(关联销售订单号汇总)
        String planNo = (String) detail.get("planNo");
        String planName = (String) detail.get("planName");
        if (items != null) {
            for (Map<String, Object> item : items) {
                item.put("planNo", planNo);
                item.put("planName", planName);
                Object rels = item.get("orderRels");
                if (rels instanceof List<?> list && !list.isEmpty()) {
                    String sourceNos = list.stream()
                            .filter(r -> r instanceof Map)
                            .map(r -> {
                                Object no = ((Map<?, ?>) r).get("orderNo");
                                return no != null ? no.toString() : "";
                            })
                            .filter(s -> !s.isEmpty())
                            .distinct()
                            .reduce((a, b) -> a + ", " + b).orElse("");
                    item.put("sourceOrderNo", sourceNos);
                } else {
                    item.put("sourceOrderNo", "");
                }
            }
        }
        return items != null ? items : List.of();
    }

    /**
     * 批量导出多张计划: 把每张的 items 拼到一起,头部带计划号区分
     */
    public List<Map<String, Object>> getExportDataBatch(List<Long> planIds) {
        List<Map<String, Object>> all = new ArrayList<>();
        for (Long pid : planIds) {
            try { all.addAll(getExportData(pid)); } catch (Exception e) {
                log.warn("批量导出: 跳过失败的 planId={}, err={}", pid, e.getMessage());
            }
        }
        return all;
    }

    /**
     * 计划详情（含行项和订单追溯）
     */
    public Map<String, Object> getDetail(Long planId) {
        BizProductionPlan plan = getById(planId);
        if (plan == null) throw new BusinessException("生产计划不存在");

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", plan.getId());
        detail.put("planNo", plan.getPlanNo());
        detail.put("planName", plan.getPlanName());
        detail.put("planDateFrom", plan.getPlanDateFrom());
        detail.put("planDateTo", plan.getPlanDateTo());
        detail.put("status", plan.getStatus());
        detail.put("createType", plan.getCreateType());
        detail.put("remark", plan.getRemark());
        detail.put("extFields", plan.getExtFields());
        detail.put("createTime", plan.getCreateTime());

        // 行项列表（含产品信息）
        List<BizProductionPlanItem> items = planItemMapper.selectList(
                new LambdaQueryWrapper<BizProductionPlanItem>()
                        .eq(BizProductionPlanItem::getPlanId, planId)
                        .orderByDesc(BizProductionPlanItem::getPriority)
                        .orderByAsc(BizProductionPlanItem::getPlanStartDate));

        Set<Long> productIds = items.stream().map(BizProductionPlanItem::getProductId).filter(Objects::nonNull).collect(Collectors.toSet());
        // 批量解析产品信息（兼容统一物料模式：先查产品表，未找到则回退查物料表）
        Map<Long, Map<String, String>> productInfoMap = batchResolveProductInfo(productIds);
        // 批量加载物料的图片和图纸附件（兼容 biz_material 和 biz_product 两种来源）
        Map<Long, BizMaterial> materialMap = productIds.isEmpty() ? Map.of() :
                materialMapper.selectBatchIds(productIds).stream()
                        .collect(Collectors.toMap(BizMaterial::getId, m -> m, (a, b) -> a));
        // 对在 biz_material 中找不到的 ID，回退查 biz_product
        Set<Long> notInMaterial = productIds.stream().filter(id -> !materialMap.containsKey(id)).collect(Collectors.toSet());
        Map<Long, BizProduct> productImgMap = notInMaterial.isEmpty() ? Map.of() :
                productMapper.selectBatchIds(notInMaterial).stream()
                        .collect(Collectors.toMap(BizProduct::getId, p -> p, (a, b) -> a));

        List<Map<String, Object>> itemList = items.stream().map(item -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", item.getId());
            map.put("planId", item.getPlanId());
            map.put("productId", item.getProductId());
            Map<String, String> pInfo = productInfoMap.getOrDefault(item.getProductId(), Map.of());
            map.put("productName", pInfo.getOrDefault("name", ""));
            map.put("productNo", pInfo.getOrDefault("no", ""));
            map.put("spec", pInfo.getOrDefault("spec", ""));
            map.put("unit", pInfo.getOrDefault("unit", ""));
            BizMaterial mat = materialMap.get(item.getProductId());
            BizProduct prod = productImgMap.get(item.getProductId());
            if (mat != null) {
                map.put("productImages", mat.getImages());
                map.put("productAttachmentUrls", mat.getAttachmentUrls());
            } else if (prod != null) {
                map.put("productImages", parseJsonList(prod.getImageUrls()));
                map.put("productAttachmentUrls", parseJsonList(prod.getDrawingUrls()));
            } else {
                map.put("productImages", null);
                map.put("productAttachmentUrls", null);
            }
            map.put("planQty", item.getPlanQty());
            map.put("releasedQty", item.getReleasedQty());
            map.put("completedQty", item.getCompletedQty());
            map.put("unreleasedQty", item.getPlanQty().subtract(
                    item.getReleasedQty() != null ? item.getReleasedQty() : BigDecimal.ZERO));
            map.put("planStartDate", item.getPlanStartDate());
            map.put("planEndDate", item.getPlanEndDate());
            map.put("priority", item.getPriority());
            map.put("sourceType", item.getSourceType());
            map.put("status", item.getStatus());
            map.put("remark", item.getRemark());

            // 关联的订单追溯
            List<BizProductionPlanOrderRel> rels = planOrderRelMapper.selectList(
                    new LambdaQueryWrapper<BizProductionPlanOrderRel>()
                            .eq(BizProductionPlanOrderRel::getPlanItemId, item.getId()));
            Set<Long> orderIds = rels.stream().map(BizProductionPlanOrderRel::getOrderId).collect(Collectors.toSet());
            Map<Long, BizSalesOrder> orderMap = orderIds.isEmpty() ? Map.of() :
                    salesOrderMapper.selectBatchIds(orderIds).stream().collect(Collectors.toMap(BizSalesOrder::getId, o -> o, (a, b) -> a));

            map.put("orderRels", rels.stream().map(rel -> {
                Map<String, Object> relMap = new LinkedHashMap<>();
                relMap.put("id", rel.getId());
                relMap.put("orderId", rel.getOrderId());
                relMap.put("orderItemId", rel.getOrderItemId());
                relMap.put("allocatedQty", rel.getAllocatedQty());
                BizSalesOrder order = orderMap.get(rel.getOrderId());
                relMap.put("orderNo", order != null ? order.getOrderNo() : "");
                relMap.put("extFields", order != null ? order.getExtFields() : null);
                return relMap;
            }).collect(Collectors.toList()));

            // 工序指派配置
            List<BizProductionPlanItemStep> steps = planItemStepMapper.selectList(
                    new LambdaQueryWrapper<BizProductionPlanItemStep>()
                            .eq(BizProductionPlanItemStep::getPlanItemId, item.getId())
                            .orderByAsc(BizProductionPlanItemStep::getStepNo));
            map.put("stepConfigs", steps.stream().map(step -> {
                Map<String, Object> stepMap = new LinkedHashMap<>();
                stepMap.put("id", step.getId());
                stepMap.put("stepId", step.getStepId());
                stepMap.put("stepNo", step.getStepNo());
                stepMap.put("stepName", step.getStepName());
                stepMap.put("teamId", step.getTeamId());
                stepMap.put("teamName", step.getTeamName());
                stepMap.put("assigneeId", step.getAssigneeId());
                stepMap.put("assigneeName", step.getAssigneeName());
                stepMap.put("needQc", step.getNeedQc());
                stepMap.put("standardTime", step.getStandardTime());
                stepMap.put("piecePrice", step.getPiecePrice());
                stepMap.put("handleType", step.getHandleType() != null ? step.getHandleType() : "self");
                stepMap.put("supplierId", step.getSupplierId());
                stepMap.put("supplierName", step.getSupplierName());
                stepMap.put("planStartTime", step.getPlanStartTime());
                stepMap.put("planEndTime", step.getPlanEndTime());
                stepMap.put("qcTemplateId", step.getQcTemplateId());
                stepMap.put("qcRequirements", step.getQcRequirements());
                stepMap.put("routeId", step.getRouteId());
                return stepMap;
            }).collect(Collectors.toList()));

            return map;
        }).collect(Collectors.toList());

        detail.put("items", itemList);
        return detail;
    }

    /**
     * 批量解析产品信息（兼容统一物料模式）
     * 先查产品表，未找到则回退查物料表
     */
    private Map<Long, Map<String, String>> batchResolveProductInfo(Set<Long> productIds) {
        Map<Long, Map<String, String>> result = new HashMap<>();
        if (productIds == null || productIds.isEmpty()) return result;
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

    /**
     * 手动创建生产计划
     */
    @Transactional
    public Long createPlan(BizProductionPlan plan, List<BizProductionPlanItem> items) {
        plan.setPlanNo(CodeUtil.generate("PP"));
        if (plan.getStatus() == null) plan.setStatus("draft");
        if (plan.getCreateType() == null) plan.setCreateType("manual");
        save(plan);

        if (items != null) {
            for (BizProductionPlanItem item : items) {
                item.setPlanId(plan.getId());
                if (item.getReleasedQty() == null) item.setReleasedQty(BigDecimal.ZERO);
                if (item.getCompletedQty() == null) item.setCompletedQty(BigDecimal.ZERO);
                if (item.getStatus() == null) item.setStatus("pending");
                planItemMapper.insert(item);
            }
        }
        return plan.getId();
    }

    /**
     * 更新生产计划基本信息
     */
    @Transactional
    public void updatePlan(Long id, BizProductionPlan plan) {
        BizProductionPlan existing = getById(id);
        if (existing == null) throw new BusinessException("生产计划不存在");
        if (!"draft".equals(existing.getStatus())) {
            throw new BusinessException("仅草稿状态可修改基本信息");
        }

        plan.setId(id);
        updateById(plan);
    }

    /**
     * 确认生产计划（派发）
     * 物料锁库已在转计划时完成（createFromSuggestions），此处仅变更状态
     * 后续 releaseWorkOrder 会将 plan_lock 转移为 wo_lock
     */
    @Transactional
    public void confirmPlan(Long id) {
        BizProductionPlan plan = getById(id);
        if (plan == null) throw new BusinessException("生产计划不存在");
        if (!"scheduled".equals(plan.getStatus())) {
            throw new BusinessException("仅待派发状态可派发");
        }

        plan.setStatus("confirmed");
        updateById(plan);
    }

    /**
     * 确认计划并对所有行项下达工单
     * scheduled → confirmed → in_progress，同时为每个行项创建工单
     */
    @Transactional
    public Map<String, Object> confirmAndReleaseAll(Long id) {
        // 先确认计划（锁料）
        confirmPlan(id);

        // 查询所有行项，全部下达工单
        List<BizProductionPlanItem> items = planItemMapper.selectList(
                new LambdaQueryWrapper<BizProductionPlanItem>()
                        .eq(BizProductionPlanItem::getPlanId, id));

        List<Long> workOrderIds = new ArrayList<>();
        for (BizProductionPlanItem item : items) {
            BigDecimal released = item.getReleasedQty() != null ? item.getReleasedQty() : BigDecimal.ZERO;
            BigDecimal unreleased = item.getPlanQty().subtract(released);
            if (unreleased.compareTo(BigDecimal.ZERO) > 0) {
                Long woId = releaseWorkOrder(item.getId(), null);
                workOrderIds.add(woId);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("workOrderIds", workOrderIds);
        result.put("workOrderCount", workOrderIds.size());
        return result;
    }

    /**
     * 取消生产计划:
     * - 仅 draft / scheduled 状态允许; 派发后 (confirmed / in_progress / completed) 一律不让取消
     * - 释放该计划所有 plan_lock 预留, 锁库存归还可用
     * - 关联销售订单回退: 该订单没有其他 active 计划 → 状态从 mrp_planned/in_production 回退到 confirmed
     * - 系统通知通过场景 plan_cancelled 发送, 角色和开关在 [系统设置→通知配置] 配置.
     * 说明: "主计划"是基于销售订单 + 库存 + 在途等实时计算的视图, 不需要单独回退 ——
     *      计划标 cancelled 后, 主计划页面会自然显示该订单的"未派发量"重新增加.
     */
    @Transactional
    public void cancelPlan(Long id) {
        BizProductionPlan plan = getById(id);
        if (plan == null) throw new BusinessException("生产计划不存在");

        // 状态门禁: 派发后不让取消
        String st = plan.getStatus();
        if (!"draft".equals(st) && !"scheduled".equals(st)) {
            String label = switch (st == null ? "" : st) {
                case "confirmed" -> "已派发";
                case "in_progress" -> "进行中";
                case "completed" -> "已完成";
                case "cancelled" -> "已取消";
                default -> st;
            };
            throw new BusinessException("当前状态[" + label + "]不允许取消, 仅草稿/待派发可以取消");
        }
        // 双保险: 即使状态是 scheduled 也再确认一次没有已下工单
        List<BizProductionPlanItem> items = planItemMapper.selectList(
                new LambdaQueryWrapper<BizProductionPlanItem>()
                        .eq(BizProductionPlanItem::getPlanId, id));
        boolean hasReleased = items.stream().anyMatch(i ->
                i.getReleasedQty() != null && i.getReleasedQty().compareTo(BigDecimal.ZERO) > 0);
        if (hasReleased) {
            throw new BusinessException("已有下达的工单，无法取消计划");
        }

        // 释放该计划所有活跃的 plan_lock 预留
        releasePlanMaterialLocks(id);

        // 收集关联的销售订单 (取消前先记下来)
        Set<Long> relatedOrderIds = new HashSet<>();
        if (!items.isEmpty()) {
            Set<Long> planItemIds = items.stream().map(BizProductionPlanItem::getId).collect(Collectors.toSet());
            List<BizProductionPlanOrderRel> rels = planOrderRelMapper.selectList(
                    new LambdaQueryWrapper<BizProductionPlanOrderRel>()
                            .in(BizProductionPlanOrderRel::getPlanItemId, planItemIds));
            for (BizProductionPlanOrderRel r : rels) {
                if (r.getOrderId() != null) relatedOrderIds.add(r.getOrderId());
            }
        }

        plan.setStatus("cancelled");
        updateById(plan);
        // 同时把所有 plan_item 标 cancelled (否则 computeUnallocatedQty 按 status!=cancelled 筛会拿到僵尸记录, 销售订单的"未派发量"算不回)
        for (BizProductionPlanItem it : items) {
            if (!"cancelled".equals(it.getStatus())) {
                it.setStatus("cancelled");
                planItemMapper.updateById(it);
            }
        }

        // 解绑售后单配件: 引用了这些 plan_item 的售后配件清空 plan_item_id, 让售后可重新转计划
        if (!items.isEmpty()) {
            Set<Long> planItemIds = items.stream().map(BizProductionPlanItem::getId).collect(Collectors.toSet());
            List<com.aimes.modules.aftersale.entity.BizServiceOrderPart> linkedParts = serviceOrderPartMapper.selectList(
                    new LambdaQueryWrapper<com.aimes.modules.aftersale.entity.BizServiceOrderPart>()
                            .in(com.aimes.modules.aftersale.entity.BizServiceOrderPart::getPlanItemId, planItemIds));
            for (com.aimes.modules.aftersale.entity.BizServiceOrderPart p : linkedParts) {
                p.setPlanItemId(null);
                serviceOrderPartMapper.updateById(p);
            }
            if (!linkedParts.isEmpty()) {
                log.info("计划取消解绑售后配件: planId={}, 解绑 {} 条", id, linkedParts.size());
            }
        }

        // 关联销售订单回退
        for (Long orderId : relatedOrderIds) {
            tryRevertSalesOrderStatus(orderId);
        }

        // 系统通知: 走配置化场景 plan_cancelled, 角色和开关在 [系统设置→通知配置] 配置
        try {
            String content = "生产计划[" + plan.getPlanNo() + "]"
                    + (StringUtils.hasText(plan.getPlanName()) ? "(" + plan.getPlanName() + ")" : "")
                    + " 已取消";
            notificationService.notifyByScenario("plan_cancelled", "生产计划取消", content,
                    "warn", "production_plan", plan.getId());
        } catch (Exception ignored) {}
    }

    /**
     * 计划取消时尝试回退销售订单状态:
     * - 仅当订单当前是 mrp_planned / in_production 时回退到 confirmed
     * - 该订单还有其他 active(非 cancelled/completed) plan_item 时不回退
     */
    private void tryRevertSalesOrderStatus(Long orderId) {
        BizSalesOrder order = salesOrderMapper.selectById(orderId);
        if (order == null) return;
        String os = order.getStatus();
        if (!"mrp_planned".equals(os) && !"in_production".equals(os)) return;

        List<BizProductionPlanOrderRel> allRels = planOrderRelMapper.selectList(
                new LambdaQueryWrapper<BizProductionPlanOrderRel>()
                        .eq(BizProductionPlanOrderRel::getOrderId, orderId));
        Set<Long> allItemIds = allRels.stream().map(BizProductionPlanOrderRel::getPlanItemId).collect(Collectors.toSet());
        boolean hasActive = !allItemIds.isEmpty() && !planItemMapper.selectList(
                new LambdaQueryWrapper<BizProductionPlanItem>()
                        .in(BizProductionPlanItem::getId, allItemIds)
                        .notIn(BizProductionPlanItem::getStatus, java.util.List.of("cancelled", "completed"))).isEmpty();
        if (!hasActive) {
            order.setStatus("confirmed");
            salesOrderMapper.updateById(order);
        }
    }

    // ==================== 计划行项操作 ====================

    /**
     * 添加计划行项
     */
    @Transactional
    public void addPlanItem(Long planId, BizProductionPlanItem item) {
        BizProductionPlan plan = getById(planId);
        if (plan == null) throw new BusinessException("生产计划不存在");
        if (!"draft".equals(plan.getStatus())) {
            throw new BusinessException("仅草稿状态可添加行项");
        }

        item.setPlanId(planId);
        if (item.getReleasedQty() == null) item.setReleasedQty(BigDecimal.ZERO);
        if (item.getCompletedQty() == null) item.setCompletedQty(BigDecimal.ZERO);
        if (item.getStatus() == null) item.setStatus("pending");
        planItemMapper.insert(item);
    }

    /**
     * 更新计划行项
     */
    @Transactional
    public void updatePlanItem(Long itemId, BizProductionPlanItem item) {
        BizProductionPlanItem existing = planItemMapper.selectById(itemId);
        if (existing == null) throw new BusinessException("计划行项不存在");

        item.setId(itemId);
        planItemMapper.updateById(item);
    }

    /**
     * 删除计划行项
     */
    @Transactional
    public void deletePlanItem(Long itemId) {
        BizProductionPlanItem existing = planItemMapper.selectById(itemId);
        if (existing == null) throw new BusinessException("计划行项不存在");
        if (existing.getReleasedQty() != null && existing.getReleasedQty().compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException("已有下达的工单，无法删除");
        }

        planOrderRelMapper.delete(
                new LambdaQueryWrapper<BizProductionPlanOrderRel>()
                        .eq(BizProductionPlanOrderRel::getPlanItemId, itemId));
        planItemMapper.deleteById(itemId);
    }

    // ==================== 从MRP生产建议创建计划 ====================

    /**
     * 从MRP生产建议创建/合并到生产计划
     * @param suggestionIds 选中的生产建议ID列表
     */
    @Transactional
    public List<Long> createFromSuggestions(List<Long> suggestionIds, Long targetPlanId) {
        List<BizMrpProductionSuggestion> suggestions = productionSuggestionMapper.selectBatchIds(suggestionIds);
        if (suggestions.isEmpty()) throw new BusinessException("未找到有效的生产建议");

        // 过滤出待确认状态的建议
        suggestions = suggestions.stream()
                .filter(s -> "pending".equals(s.getStatus()) || "confirmed".equals(s.getStatus()))
                .collect(Collectors.toList());
        if (suggestions.isEmpty()) throw new BusinessException("所选建议状态不允许转入计划");

        List<Long> createdPlanIds = new ArrayList<>();

        // 每条建议独立创建一个生产计划（不合并）
        for (BizMrpProductionSuggestion suggestion : suggestions) {
            BizProductionPlan plan;
            if (targetPlanId != null) {
                // 合并到指定计划
                plan = getById(targetPlanId);
                if (plan == null) throw new BusinessException("目标计划不存在");
                if (!"draft".equals(plan.getStatus())) {
                    throw new BusinessException("目标计划必须为草稿状态");
                }
            } else {
                // 每条建议创建独立的生产计划
                plan = new BizProductionPlan();
                plan.setPlanNo(CodeUtil.generate("PP"));
                plan.setPlanName("MRP生成计划");
                plan.setStatus("draft");
                plan.setCreateType("mrp");
                plan.setPlanDateFrom(suggestion.getSuggestStartDate());
                plan.setPlanDateTo(suggestion.getRequiredDate());
                save(plan);
            }

            // 优先使用actualQty（用户确认的实际生产数量），未设置时回退到suggestQty
            BigDecimal qty = suggestion.getActualQty() != null ? suggestion.getActualQty() : suggestion.getSuggestQty();
            boolean isSemi = Boolean.TRUE.equals(suggestion.getIsSemiFinished());

            // 创建计划行项（每条建议一个行项，不合并）
            BizProductionPlanItem planItem = new BizProductionPlanItem();
            planItem.setPlanId(plan.getId());
            planItem.setProductId(suggestion.getProductId());
            planItem.setPlanQty(qty);
            planItem.setReleasedQty(BigDecimal.ZERO);
            planItem.setCompletedQty(BigDecimal.ZERO);
            planItem.setPlanStartDate(suggestion.getSuggestStartDate());
            planItem.setPlanEndDate(suggestion.getRequiredDate());
            planItem.setPriority(isSemi ? 2 : 1);
            planItem.setSourceType("mrp");
            planItem.setMrpSuggestionId(suggestion.getId());
            planItem.setStatus("pending");
            planItemMapper.insert(planItem);

            // 创建订单追溯关系
            createOrderRelsFromSuggestion(planItem.getId(), suggestion);
            mrpService.markProductionSuggestionTransferred(suggestion.getId(), planItem.getId());

            if (!createdPlanIds.contains(plan.getId())) {
                createdPlanIds.add(plan.getId());
            }
        }

        // ===== 竞争组处理：累加所有已转计划建议的物料消耗，一次性检查同组剩余建议 =====
        Set<Long> allTransferredIds = suggestions.stream().map(BizMrpProductionSuggestion::getId).collect(Collectors.toSet());
        Map<String, Map<Long, BigDecimal>> groupConsumedMap = new LinkedHashMap<>();
        Map<String, Long> groupMrpRunMap = new LinkedHashMap<>();
        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
        for (BizMrpProductionSuggestion suggestion : suggestions) {
            try {
                if (suggestion.getSourceSummary() == null) continue;
                Map<String, Object> summary = om.readValue(suggestion.getSourceSummary(), Map.class);
                Object group = summary.get("competitionGroup");
                if (group == null) continue;
                String groupLabel = group.toString();
                groupMrpRunMap.putIfAbsent(groupLabel, suggestion.getMrpRunId());

                BigDecimal transferQty = suggestion.getActualQty() != null
                        ? suggestion.getActualQty() : suggestion.getSuggestQty();
                Map<Long, BigDecimal> consumed = mrpService.calculateMaterialConsumption(
                        suggestion.getProductId(), transferQty);
                Map<Long, BigDecimal> groupConsumed = groupConsumedMap.computeIfAbsent(groupLabel, k -> new LinkedHashMap<>());
                consumed.forEach((matId, qty2) -> groupConsumed.merge(matId, qty2, BigDecimal::add));
            } catch (Exception e) {
                log.warn("竞争组物料计算失败: suggestionId={}, {}", suggestion.getId(), e.getMessage());
            }
        }
        for (Map.Entry<String, Map<Long, BigDecimal>> entry : groupConsumedMap.entrySet()) {
            String groupLabel = entry.getKey();
            Long mrpRunId = groupMrpRunMap.get(groupLabel);
            mrpService.recheckCompetitionGroupAfterTransfer(mrpRunId, groupLabel, allTransferredIds, entry.getValue());
        }

        // 转计划时立即锁定物料库存
        for (Long pid : createdPlanIds) {
            lockMaterialsForPlan(getById(pid));
        }

        return createdPlanIds;
    }

    /**
     * 从生产建议的sourceSummary中提取订单关系
     */
    private void createOrderRelsFromSuggestion(Long planItemId, BizMrpProductionSuggestion suggestion) {
        try {
            if (suggestion.getSourceSummary() == null) return;
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> summary = om.readValue(suggestion.getSourceSummary(), Map.class);
            List<Number> orderIds = (List<Number>) summary.get("orderIds");
            List<Number> orderItemIds = (List<Number>) summary.get("orderItemIds");
            if (orderIds != null) {
                for (int i = 0; i < orderIds.size(); i++) {
                    Long orderId = orderIds.get(i).longValue();
                    Long orderItemId = orderItemIds != null && i < orderItemIds.size()
                            ? orderItemIds.get(i).longValue() : null;

                    // 检查是否已存在
                    long exists = planOrderRelMapper.selectCount(
                            new LambdaQueryWrapper<BizProductionPlanOrderRel>()
                                    .eq(BizProductionPlanOrderRel::getPlanItemId, planItemId)
                                    .eq(BizProductionPlanOrderRel::getOrderId, orderId)
                                    .eq(orderItemId != null, BizProductionPlanOrderRel::getOrderItemId, orderItemId));
                    if (exists == 0) {
                        BizProductionPlanOrderRel rel = new BizProductionPlanOrderRel();
                        rel.setPlanItemId(planItemId);
                        rel.setOrderId(orderId);
                        rel.setOrderItemId(orderItemId != null ? orderItemId : 0L);
                        rel.setAllocatedQty(suggestion.getSuggestQty());
                        planOrderRelMapper.insert(rel);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("解析MRP来源摘要失败: {}", e.getMessage());
        }
    }

    // ==================== 下达工单 ====================

    /**
     * 从计划行项下达生产工单
     * @param planItemId 计划行项ID
     * @param releaseQty 下达数量（null则全部下达）
     */
    @Transactional
    public Long releaseWorkOrder(Long planItemId, BigDecimal releaseQty) {
        BizProductionPlanItem item = planItemMapper.selectById(planItemId);
        if (item == null) throw new BusinessException("计划行项不存在");

        BizProductionPlan plan = getById(item.getPlanId());
        if (plan == null) throw new BusinessException("生产计划不存在");
        if (!"confirmed".equals(plan.getStatus()) && !"in_progress".equals(plan.getStatus())) {
            throw new BusinessException("计划必须为已确认或执行中状态才能下达工单");
        }

        BigDecimal released = item.getReleasedQty() != null ? item.getReleasedQty() : BigDecimal.ZERO;
        BigDecimal unreleased = item.getPlanQty().subtract(released);
        if (unreleased.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("已全部下达，无可下达数量");
        }

        // 确定下达数量
        BigDecimal qty = releaseQty != null ? releaseQty : unreleased;
        if (qty.compareTo(unreleased) > 0) {
            throw new BusinessException("下达数量(" + qty + ")超过未下达数量(" + unreleased + ")");
        }

        // 查找关联的订单行项（取第一个追溯关系）
        BizProductionPlanOrderRel rel = planOrderRelMapper.selectOne(
                new LambdaQueryWrapper<BizProductionPlanOrderRel>()
                        .eq(BizProductionPlanOrderRel::getPlanItemId, planItemId)
                        .last("LIMIT 1"));

        // 从计划行项工序配置中获取工艺路线ID，确保工单生成工序任务时能找到正确的路线
        Long routeId = null;
        List<BizProductionPlanItemStep> planSteps = planItemStepMapper.selectList(
                new LambdaQueryWrapper<BizProductionPlanItemStep>()
                        .eq(BizProductionPlanItemStep::getPlanItemId, planItemId)
                        .last("LIMIT 1"));
        if (!planSteps.isEmpty() && planSteps.get(0).getRouteId() != null) {
            routeId = planSteps.get(0).getRouteId();
            log.info("releaseWorkOrder: 从计划工序配置获取routeId={}, planItemId={}", routeId, planItemId);
        }

        // 创建工单（复用已有逻辑，planItemId 非 null 时工单服务会跳过自动锁料）
        BizWorkOrder wo = new BizWorkOrder();
        wo.setProductId(item.getProductId());
        wo.setRouteId(routeId);
        wo.setPlanQty(qty);
        wo.setDeliveryDate(item.getPlanEndDate());
        wo.setPriority(item.getPriority() != null ? item.getPriority() : 0);
        wo.setPlanItemId(item.getId());
        // 工单 ext_fields = 计划级(继承自销售订单头) 合并 销售订单明细行级
        // sales_order 与 sales_order_item 配置 fieldKey 不允许冲突(由 BizExtFieldConfigService.validateFieldKey 保证),
        // 因此 putAll 不会丢字段
        wo.setExtFields(mergeExtFields(plan.getExtFields(), rel != null ? rel.getOrderItemId() : null));
        if (rel != null) {
            wo.setOrderId(rel.getOrderId());
            wo.setOrderItemId(rel.getOrderItemId());
        }
        // 传递主计划替代料选择，供 snapshotBom 写入 preferredSubstituteId
        if (item.getSubstituteSelections() != null && !item.getSubstituteSelections().isEmpty()) {
            try {
                java.util.Map<String, Long> rawMap = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(item.getSubstituteSelections(),
                                new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Long>>() {});
                java.util.Map<Long, Long> typedMap = new java.util.HashMap<>();
                rawMap.forEach((k, v) -> typedMap.put(Long.valueOf(k), v));
                wo.setSubstituteSelections(typedMap);
            } catch (Exception e) {
                log.warn("解析计划行替代料选择失败: planItemId={}, err={}", planItemId, e.getMessage());
            }
        }
        workOrderService.createWorkOrder(wo);

        // 将计划行项的工序配置（指派、时间、质检等）复制到工单工序任务
        applyPlanStepConfigsToWorkOrder(planItemId, wo.getId());

        // 将计划级锁料按比例转移到工单级
        transferPlanLocksToWorkOrder(item, wo.getId(), wo.getWoNo(), qty);

        // 更新计划行项已下达数量
        item.setReleasedQty(released.add(qty));
        if (item.getReleasedQty().compareTo(item.getPlanQty()) >= 0) {
            item.setStatus("fully_released");
        } else {
            item.setStatus("partial_released");
        }
        planItemMapper.updateById(item);

        // 更新计划主表状态为执行中
        if ("confirmed".equals(plan.getStatus())) {
            plan.setStatus("in_progress");
            updateById(plan);
        }

        // 更新关联的订单状态为 in_production
        if (rel != null) {
            BizSalesOrder order = salesOrderMapper.selectById(rel.getOrderId());
            if (order != null && !"in_production".equals(order.getStatus())) {
                order.setStatus("in_production");
                salesOrderMapper.updateById(order);
            }
        }

        return wo.getId();
    }

    /**
     * 将计划行项的工序配置（指派、时间、质检等）复制到工单工序任务
     * 按 stepNo 匹配计划配置和工单任务
     */
    private void applyPlanStepConfigsToWorkOrder(Long planItemId, Long workOrderId) {
        // 查询计划行项的工序配置
        List<BizProductionPlanItemStep> planSteps = planItemStepMapper.selectList(
                new LambdaQueryWrapper<BizProductionPlanItemStep>()
                        .eq(BizProductionPlanItemStep::getPlanItemId, planItemId)
                        .orderByAsc(BizProductionPlanItemStep::getStepNo));
        log.info("applyPlanStepConfigs: planItemId={}, workOrderId={}, planSteps.size={}", planItemId, workOrderId, planSteps.size());
        if (planSteps.isEmpty()) return;

        // 查询工单已生成的工序任务
        List<BizWoProcessTask> woTasks = woProcessTaskMapper.selectList(
                new LambdaQueryWrapper<BizWoProcessTask>()
                        .eq(BizWoProcessTask::getWorkOrderId, workOrderId)
                        .orderByAsc(BizWoProcessTask::getStepNo));
        log.info("applyPlanStepConfigs: woTasks.size={}", woTasks.size());
        if (woTasks.isEmpty()) return;

        // 按 stepNo 建立索引，同时按 stepName 备用匹配
        Map<Integer, BizProductionPlanItemStep> planStepByNo = planSteps.stream()
                .collect(Collectors.toMap(BizProductionPlanItemStep::getStepNo, s -> s, (a, b) -> a));
        Map<String, BizProductionPlanItemStep> planStepByName = planSteps.stream()
                .collect(Collectors.toMap(BizProductionPlanItemStep::getStepName, s -> s, (a, b) -> a));

        for (BizWoProcessTask task : woTasks) {
            // 优先按 stepNo 匹配，其次按 stepName 匹配
            BizProductionPlanItemStep config = planStepByNo.get(task.getStepNo());
            if (config == null && task.getStepName() != null) {
                config = planStepByName.get(task.getStepName());
            }
            if (config == null) {
                log.info("applyPlanStepConfigs: no match for stepNo={}, stepName={}", task.getStepNo(), task.getStepName());
                continue;
            }

            log.info("applyPlanStepConfigs: matched stepNo={}, config.teamId={}, config.assigneeId={}, config.handleType={}",
                    task.getStepNo(), config.getTeamId(), config.getAssigneeId(), config.getHandleType());

            // 使用 LambdaUpdateWrapper 显式更新所有字段，避免 MyBatis-Plus 的 null 字段跳过策略
            LambdaUpdateWrapper<BizWoProcessTask> updateWrapper = new LambdaUpdateWrapper<BizWoProcessTask>()
                    .eq(BizWoProcessTask::getId, task.getId())
                    .set(BizWoProcessTask::getAssignedTeamId, config.getTeamId())
                    .set(BizWoProcessTask::getAssignedUserId, config.getAssigneeId());

            // 复制工时和单价
            if (config.getStandardTime() != null) {
                updateWrapper.set(BizWoProcessTask::getStandardTime, config.getStandardTime());
            }
            if (config.getPiecePrice() != null) {
                updateWrapper.set(BizWoProcessTask::getPiecePrice, config.getPiecePrice());
            }
            // 复制计划时间
            updateWrapper.set(BizWoProcessTask::getStartTime, config.getPlanStartTime());
            updateWrapper.set(BizWoProcessTask::getEndTime, config.getPlanEndTime());
            // 复制质检配置
            if (config.getNeedQc() != null && config.getNeedQc()) {
                updateWrapper.set(BizWoProcessTask::getQcTemplateId, config.getQcTemplateId());
                updateWrapper.set(BizWoProcessTask::getQcStatus, "pending");
                // 同步生产计划中维护的质检要求(含标准值、公差等)
                updateWrapper.set(BizWoProcessTask::getQcRequirements, config.getQcRequirements());
            }
            // 复制处理方式（外协标记）+ 自动创建外协订单
            if ("outsource".equals(config.getHandleType())) {
                updateWrapper.set(BizWoProcessTask::getIsOutsourced, 1);
                updateWrapper.set(BizWoProcessTask::getIsOutsourceable, 1);
                woProcessTaskMapper.update(null, updateWrapper);
                createOutsourceOrderForPlanStep(workOrderId, task, config);
                continue; // 已 update 过, 跳过下面的统一 update
            }

            woProcessTaskMapper.update(null, updateWrapper);
        }

        // 同步复制工序图纸（计划工序 → 工单工序）
        try {
            int copied = drawingService.copyOnDispatch(planItemId, workOrderId);
            log.info("applyPlanStepConfigs: drawings copied = {}", copied);
        } catch (Exception e) {
            log.warn("派发时复制工序图纸失败: planItemId={}, workOrderId={}, err={}", planItemId, workOrderId, e.getMessage());
        }
    }

    /**
     * 计划下达时, 对配置为外协的工序自动生成外协订单
     * 必要字段: supplierId(无则跳过); 单价/税率默认 0/13; 交付日期取 planEndTime, 否则取工单 deliveryDate
     */
    private void createOutsourceOrderForPlanStep(Long workOrderId, BizWoProcessTask task,
                                                 BizProductionPlanItemStep config) {
        if (config.getSupplierId() == null) {
            log.warn("计划工序外协但未指定供应商, 跳过自动建单 woId={} stepNo={} stepName={}",
                    workOrderId, task.getStepNo(), task.getStepName());
            return;
        }
        // 同一 (workOrderId, taskId) 已有外协单则跳过, 防重复生成
        if (task.getOutsourceOrderId() != null) {
            return;
        }
        com.aimes.modules.production.entity.BizWorkOrder wo = workOrderService.getById(workOrderId);
        if (wo == null) return;
        try {
            com.aimes.modules.outsource.entity.BizOutsourceOrder oo =
                    new com.aimes.modules.outsource.entity.BizOutsourceOrder();
            oo.setSupplierId(config.getSupplierId());
            oo.setProductId(wo.getProductId());
            oo.setStepName(task.getStepName());
            oo.setTotalQty(wo.getPlanQty());
            oo.setUnitPrice(java.math.BigDecimal.ZERO);
            oo.setTaxRate(new java.math.BigDecimal("13"));
            java.time.LocalDate dd = config.getPlanEndTime() != null
                    ? config.getPlanEndTime().toLocalDate()
                    : wo.getDeliveryDate();
            if (dd == null) dd = java.time.LocalDate.now().plusDays(7);
            oo.setDeliveryDate(dd);
            oo.setWorkOrderId(wo.getId());
            oo.setTaskId(task.getId());
            oo.setRemark("计划下达自动生成外协单(供应商已配置, 单价待补)");
            // 通过 ApplicationContext 懒取, 避免启动期循环依赖
            applicationContext.getBean(com.aimes.modules.outsource.service.OutsourceOrderService.class).create(oo, null);

            // 回写 task 上的外协单关联
            woProcessTaskMapper.update(null, new LambdaUpdateWrapper<BizWoProcessTask>()
                    .eq(BizWoProcessTask::getId, task.getId())
                    .set(BizWoProcessTask::getOutsourceOrderId, oo.getId())
                    .set(BizWoProcessTask::getOutsourceStatus, "ordered"));

            log.info("计划下达自动生成外协单: woId={} taskId={} stepName={} → outsourceOrderId={} no={}",
                    wo.getId(), task.getId(), task.getStepName(), oo.getId(), oo.getOrderNo());
        } catch (Exception e) {
            log.error("计划下达自动生成外协单失败 woId={} taskId={}: {}", workOrderId, task.getId(), e.getMessage(), e);
        }
    }

    /**
     * 批量下达工单
     */
    @Transactional
    public List<Long> batchReleaseWorkOrders(List<Long> planItemIds) {
        List<Long> woIds = new ArrayList<>();
        for (Long itemId : planItemIds) {
            woIds.add(releaseWorkOrder(itemId, null));
        }
        return woIds;
    }

    /**
     * 批量派工（确认并下达工单）
     * 对每个计划独立处理，返回成功/失败汇总
     */
    public Map<String, Object> batchConfirmAndStart(List<Long> planIds) {
        int successCount = 0;
        int failCount = 0;
        List<String> failMessages = new ArrayList<>();
        int totalWoCount = 0;
        for (Long planId : planIds) {
            try {
                BizProductionPlan plan = getById(planId);
                if (plan == null) {
                    failCount++;
                    failMessages.add("计划ID=" + planId + "不存在");
                    continue;
                }
                if (!"scheduled".equals(plan.getStatus())) {
                    failCount++;
                    failMessages.add(plan.getPlanNo() + ": 状态不是待派发，无法派工");
                    continue;
                }
                Map<String, Object> result = confirmAndReleaseAll(planId);
                int woCount = result.get("workOrderCount") != null
                        ? ((Number) result.get("workOrderCount")).intValue() : 0;
                totalWoCount += woCount;
                successCount++;
            } catch (Exception e) {
                failCount++;
                failMessages.add("计划ID=" + planId + ": " + e.getMessage());
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("successCount", successCount);
        result.put("failCount", failCount);
        result.put("totalWorkOrderCount", totalWoCount);
        result.put("failMessages", failMessages);
        return result;
    }

    /**
     * 批量取消计划
     * 对每个计划独立处理，返回成功/失败汇总
     */
    public Map<String, Object> batchCancelPlans(List<Long> planIds) {
        int successCount = 0;
        int failCount = 0;
        List<String> failMessages = new ArrayList<>();
        for (Long planId : planIds) {
            try {
                cancelPlan(planId);
                successCount++;
            } catch (Exception e) {
                BizProductionPlan plan = getById(planId);
                String planNo = plan != null ? plan.getPlanNo() : "ID=" + planId;
                failCount++;
                failMessages.add(planNo + ": " + e.getMessage());
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("successCount", successCount);
        result.put("failCount", failCount);
        result.put("failMessages", failMessages);
        return result;
    }

    // ==================== 物料齐套检查 ====================

    /**
     * 检查计划的物料齐套性
     * 展开所有计划行项的BOM，汇总所需原材料，对比库存可用量
     */
    public List<Map<String, Object>> checkPlanMaterial(Long planId) {
        BizProductionPlan plan = getById(planId);
        if (plan == null) throw new BusinessException("生产计划不存在");

        List<BizProductionPlanItem> items = planItemMapper.selectList(
                new LambdaQueryWrapper<BizProductionPlanItem>()
                        .eq(BizProductionPlanItem::getPlanId, planId));

        // 汇总所有物料需求 materialId -> {materialName, spec, unit, requiredQty}
        Map<Long, Map<String, Object>> materialDemand = new LinkedHashMap<>();

        for (BizProductionPlanItem item : items) {
            if (item.getProductId() == null || item.getPlanQty() == null) continue;

            // 查找产品的active BOM
            BizBomVersion activeBom = bomService.getOne(new LambdaQueryWrapper<BizBomVersion>()
                    .eq(BizBomVersion::getProductId, item.getProductId())
                    .eq(BizBomVersion::getStatus, "active")
                    .last("LIMIT 1"));
            if (activeBom == null) continue;

            // 展开BOM到叶子物料
            List<Map<String, Object>> leafMaterials = bomService.expandToLeafMaterials(activeBom.getId());

            // 计算每种物料的需求量 = BOM单位用量 × 计划数量
            BigDecimal planQty = item.getPlanQty().subtract(
                    item.getReleasedQty() != null ? item.getReleasedQty() : BigDecimal.ZERO);
            if (planQty.compareTo(BigDecimal.ZERO) <= 0) continue;

            for (Map<String, Object> leaf : leafMaterials) {
                Long materialId = ((Number) leaf.get("materialId")).longValue();
                BigDecimal unitQty = (BigDecimal) leaf.get("totalQty");
                BigDecimal requiredQty = unitQty.multiply(planQty);

                Map<String, Object> existing = materialDemand.get(materialId);
                if (existing != null) {
                    BigDecimal existQty = (BigDecimal) existing.get("requiredQty");
                    existing.put("requiredQty", existQty.add(requiredQty));
                } else {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("materialId", materialId);
                    entry.put("materialName", leaf.get("materialName"));
                    entry.put("spec", leaf.get("spec"));
                    entry.put("unit", leaf.get("unit"));
                    entry.put("requiredQty", requiredQty);
                    materialDemand.put(materialId, entry);
                }
            }
        }

        // 查询库存可用量，计算缺口
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> demand : materialDemand.values()) {
            Long materialId = ((Number) demand.get("materialId")).longValue();

            // 汇总所有仓库的可用库存
            List<BizInventory> inventories = inventoryMapper.selectList(
                    new LambdaQueryWrapper<BizInventory>()
                            .eq(BizInventory::getMaterialId, materialId));
            BigDecimal availableQty = inventories.stream()
                    .map(inv -> inv.getAvailableQty() != null ? inv.getAvailableQty() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal requiredQty = (BigDecimal) demand.get("requiredQty");
            BigDecimal shortage = requiredQty.subtract(availableQty).max(BigDecimal.ZERO);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("materialId", materialId);
            row.put("materialName", demand.get("materialName"));
            row.put("spec", demand.get("spec"));
            row.put("unit", demand.get("unit"));
            row.put("requiredQty", requiredQty);
            row.put("availableQty", availableQty);
            row.put("shortage", shortage);
            result.add(row);
        }

        // 缺料的排前面
        result.sort((a, b) -> ((BigDecimal) b.get("shortage")).compareTo((BigDecimal) a.get("shortage")));

        return result;
    }

    // ==================== 工序指派配置 ====================

    /**
     * 保存计划行项的工序指派配置（有则更新，无则插入）
     */
    @Transactional
    public void saveItemStepConfigs(Long planItemId, List<BizProductionPlanItemStep> steps) {
        BizProductionPlanItem item = planItemMapper.selectById(planItemId);
        if (item == null) throw new BusinessException("计划行项不存在");

        // 查询已有配置，按stepId索引
        List<BizProductionPlanItemStep> existing = planItemStepMapper.selectList(
                new LambdaQueryWrapper<BizProductionPlanItemStep>()
                        .eq(BizProductionPlanItemStep::getPlanItemId, planItemId));
        Map<Long, BizProductionPlanItemStep> existingMap = existing.stream()
                .collect(Collectors.toMap(BizProductionPlanItemStep::getStepId, s -> s, (a, b) -> a));

        Set<Long> incomingStepIds = new HashSet<>();
        if (steps != null) {
            for (BizProductionPlanItemStep step : steps) {
                step.setPlanItemId(planItemId);
                incomingStepIds.add(step.getStepId());
                BizProductionPlanItemStep old = existingMap.get(step.getStepId());
                if (old != null) {
                    // 更新已有记录
                    step.setId(old.getId());
                    planItemStepMapper.updateById(step);
                } else {
                    // 插入新记录，清除可能携带的旧id
                    step.setId(null);
                    planItemStepMapper.insert(step);
                }
            }
        }

        // 删除不再存在的工序配置
        for (BizProductionPlanItemStep old : existing) {
            if (!incomingStepIds.contains(old.getStepId())) {
                planItemStepMapper.deleteById(old.getId());
            }
        }

        // 保存配置后，草稿状态自动变更为待派发，同时锁定物料
        BizProductionPlan plan = getById(item.getPlanId());
        if (plan != null && "draft".equals(plan.getStatus())) {
            plan.setStatus("scheduled");
            updateById(plan);
            // 手动创建的计划在此时锁库（MRP转计划的已在createFromSuggestions中锁过）
            if ("manual".equals(plan.getCreateType())) {
                lockMaterialsForPlan(plan);
            }
        }
    }

    /**
     * 获取计划行项的工序指派配置
     */
    public List<BizProductionPlanItemStep> getItemStepConfigs(Long planItemId) {
        return planItemStepMapper.selectList(
                new LambdaQueryWrapper<BizProductionPlanItemStep>()
                        .eq(BizProductionPlanItemStep::getPlanItemId, planItemId)
                        .orderByAsc(BizProductionPlanItemStep::getStepNo));
    }

    // ==================== 计划完成回调 ====================

    /**
     * 工单完成时更新计划完成数量
     */
    @Transactional
    public void onWorkOrderCompleted(Long planItemId, BigDecimal completedQty) {
        if (planItemId == null) return;
        BizProductionPlanItem item = planItemMapper.selectById(planItemId);
        if (item == null) return;

        BigDecimal newCompleted = (item.getCompletedQty() != null ? item.getCompletedQty() : BigDecimal.ZERO)
                .add(completedQty);
        // 完成数量不超过计划数量上限
        if (item.getPlanQty() != null && newCompleted.compareTo(item.getPlanQty()) > 0) {
            newCompleted = item.getPlanQty();
        }
        item.setCompletedQty(newCompleted);

        if (item.getPlanQty() != null && newCompleted.compareTo(item.getPlanQty()) >= 0) {
            item.setStatus("completed");
        }
        planItemMapper.updateById(item);

        // 检查所有行项是否都已完成
        List<BizProductionPlanItem> allItems = planItemMapper.selectList(
                new LambdaQueryWrapper<BizProductionPlanItem>()
                        .eq(BizProductionPlanItem::getPlanId, item.getPlanId()));
        boolean allCompleted = allItems.stream().allMatch(i -> "completed".equals(i.getStatus()));
        if (allCompleted) {
            BizProductionPlan plan = getById(item.getPlanId());
            if (plan != null) {
                plan.setStatus("completed");
                updateById(plan);
            }
        }
    }

    // ==================== 物料锁库（计划级） ====================

    /**
     * 计划确认时锁定物料库存
     * 遍历每个计划行项，展开BOM得到叶子物料需求，尝试锁定库存
     * 锁定失败（库存不足）不阻断确认流程，记录缺料并通知仓库
     */
    private void lockMaterialsForPlan(BizProductionPlan plan) {
        List<BizProductionPlanItem> items = planItemMapper.selectList(
                new LambdaQueryWrapper<BizProductionPlanItem>()
                        .eq(BizProductionPlanItem::getPlanId, plan.getId()));

        List<String> allShortages = new ArrayList<>();

        for (BizProductionPlanItem item : items) {
            if (item.getProductId() == null || item.getPlanQty() == null) continue;

            BigDecimal planQty = item.getPlanQty().subtract(
                    item.getReleasedQty() != null ? item.getReleasedQty() : BigDecimal.ZERO);
            if (planQty.compareTo(BigDecimal.ZERO) <= 0) continue;

            // 查找产品的生效 BOM
            BizBomVersion activeBom = bomService.getOne(new LambdaQueryWrapper<BizBomVersion>()
                    .eq(BizBomVersion::getProductId, item.getProductId())
                    .eq(BizBomVersion::getStatus, "active")
                    .last("LIMIT 1"));
            if (activeBom == null) continue;

            // BOM展开到叶子物料
            List<Map<String, Object>> leafMaterials = bomService.expandToLeafMaterials(activeBom.getId());

            for (Map<String, Object> leaf : leafMaterials) {
                Long materialId = ((Number) leaf.get("materialId")).longValue();
                BigDecimal unitQty = (BigDecimal) leaf.get("totalQty");
                BigDecimal requiredQty = unitQty.multiply(planQty);
                String materialName = (String) leaf.get("materialName");

                // 获取默认仓库
                BizMaterial material = materialMapper.selectById(materialId);
                Long warehouseId = material != null ? material.getDefaultWarehouseId() : null;

                // 创建计划行项物料需求记录
                BizPlanItemMaterial pim = new BizPlanItemMaterial();
                pim.setPlanId(plan.getId());
                pim.setPlanItemId(item.getId());
                pim.setMaterialId(materialId);
                pim.setWarehouseId(warehouseId);
                pim.setRequiredQty(requiredQty);
                pim.setReleasedToWoQty(BigDecimal.ZERO);

                if (warehouseId == null) {
                    pim.setLockedQty(BigDecimal.ZERO);
                    pim.setShortageQty(requiredQty);
                    planItemMaterialMapper.insert(pim);
                    allShortages.add(materialName + "(未设默认仓库)");
                    continue;
                }

                // 先检查库存是否足够，避免调用 lockStock 抛异常导致事务被标记 rollback-only
                BigDecimal availableQty = inventoryService.getAvailableQty(materialId, warehouseId);
                if (availableQty.compareTo(requiredQty) >= 0) {
                    inventoryService.lockStock(materialId, warehouseId, requiredQty,
                            "plan_lock", "plan_item", item.getId(),
                            plan.getPlanNo(), "计划[" + plan.getPlanNo() + "]确认锁料", item.getId());
                    pim.setLockedQty(requiredQty);
                    pim.setShortageQty(BigDecimal.ZERO);
                } else if (availableQty.compareTo(BigDecimal.ZERO) > 0) {
                    // 部分锁定：有多少锁多少
                    inventoryService.lockStock(materialId, warehouseId, availableQty,
                            "plan_lock", "plan_item", item.getId(),
                            plan.getPlanNo(), "计划[" + plan.getPlanNo() + "]确认锁料(部分)", item.getId());
                    pim.setLockedQty(availableQty);
                    pim.setShortageQty(requiredQty.subtract(availableQty));
                    allShortages.add(materialName + "(缺" + requiredQty.subtract(availableQty).stripTrailingZeros().toPlainString() + ")");
                } else {
                    pim.setLockedQty(BigDecimal.ZERO);
                    pim.setShortageQty(requiredQty);
                    allShortages.add(materialName + "(库存不足)");
                }
                planItemMaterialMapper.insert(pim);
            }
        }

        // 通知仓库角色
        if (!allShortages.isEmpty()) {
            String content = "计划[" + plan.getPlanNo() + "]确认锁料，以下物料库存不足: "
                    + String.join(", ", allShortages);
            notificationService.notifyRole("warehouse", "计划物料锁定不足提醒", content,
                    "alert", "production_plan", plan.getId());
        }
    }

    /**
     * 释放计划的所有活跃物料锁定
     * 查找该计划所有行项的 plan_lock 预留记录，逐个释放
     */
    private void releasePlanMaterialLocks(Long planId) {
        List<BizProductionPlanItem> items = planItemMapper.selectList(
                new LambdaQueryWrapper<BizProductionPlanItem>()
                        .eq(BizProductionPlanItem::getPlanId, planId));
        Set<Long> itemIds = items.stream().map(BizProductionPlanItem::getId).collect(Collectors.toSet());
        if (itemIds.isEmpty()) return;

        List<BizInvReservation> reservations = reservationMapper.selectList(
                new LambdaQueryWrapper<BizInvReservation>()
                        .eq(BizInvReservation::getReservationType, "plan_lock")
                        .in(BizInvReservation::getPlanItemId, itemIds)
                        .eq(BizInvReservation::getStatus, "active"));
        for (BizInvReservation reservation : reservations) {
            try {
                inventoryService.unlockStock(reservation.getId());
            } catch (Exception e) {
                log.warn("释放计划锁料失败: reservationId={}, 原因: {}", reservation.getId(), e.getMessage());
            }
        }
    }

    /**
     * 将计划级物料锁定按比例转移到工单级
     * 计算比例: ratio = 下达数量 / 计划数量
     * 对每种物料: 从 plan_lock 预留中拆分 transferQty 到新的 wo_lock 预留
     */
    private void transferPlanLocksToWorkOrder(BizProductionPlanItem planItem, Long workOrderId, String woNo, BigDecimal releaseQty) {
        List<BizPlanItemMaterial> pimList = planItemMaterialMapper.selectList(
                new LambdaQueryWrapper<BizPlanItemMaterial>()
                        .eq(BizPlanItemMaterial::getPlanItemId, planItem.getId()));
        if (pimList.isEmpty()) return;

        BigDecimal ratio = releaseQty.divide(planItem.getPlanQty(), 6, RoundingMode.HALF_UP);

        for (BizPlanItemMaterial pim : pimList) {
            if (pim.getLockedQty() == null || pim.getLockedQty().compareTo(BigDecimal.ZERO) <= 0) continue;

            // 计算本次应转移数量
            BigDecimal alreadyReleased = pim.getReleasedToWoQty() != null ? pim.getReleasedToWoQty() : BigDecimal.ZERO;
            BigDecimal remainingLocked = pim.getLockedQty().subtract(alreadyReleased);
            if (remainingLocked.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal transferQty = pim.getLockedQty().multiply(ratio)
                    .setScale(4, RoundingMode.HALF_UP).min(remainingLocked);

            // 查找该物料的 plan_lock 预留记录
            List<BizInvReservation> planReservations = reservationMapper.selectList(
                    new LambdaQueryWrapper<BizInvReservation>()
                            .eq(BizInvReservation::getReservationType, "plan_lock")
                            .eq(BizInvReservation::getPlanItemId, planItem.getId())
                            .eq(BizInvReservation::getMaterialId, pim.getMaterialId())
                            .eq(BizInvReservation::getStatus, "active"));

            BigDecimal remaining = transferQty;
            for (BizInvReservation planRes : planReservations) {
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;

                BigDecimal canTransfer = planRes.getRemainingQty().min(remaining);

                // 减少计划级预留
                planRes.setConsumedQty(planRes.getConsumedQty().add(canTransfer));
                planRes.setRemainingQty(planRes.getRemainingQty().subtract(canTransfer));
                if (planRes.getRemainingQty().compareTo(BigDecimal.ZERO) <= 0) {
                    planRes.setStatus("consumed");
                }
                reservationMapper.updateById(planRes);

                // 创建工单级预留（不改变库存的 lockedQty/availableQty，因为锁是平移）
                BizInvReservation woRes = new BizInvReservation();
                woRes.setMaterialId(pim.getMaterialId());
                woRes.setWarehouseId(pim.getWarehouseId());
                woRes.setReservationType("wo_lock");
                woRes.setLockedQty(canTransfer);
                woRes.setConsumedQty(BigDecimal.ZERO);
                woRes.setRemainingQty(canTransfer);
                woRes.setRelatedOrderType("work_order");
                woRes.setRelatedOrderId(workOrderId);
                woRes.setRelatedOrderNo(woNo);
                woRes.setPlanItemId(planItem.getId());
                woRes.setStatus("active");
                woRes.setReason("计划锁料转工单[" + woNo + "]");
                reservationMapper.insert(woRes);

                remaining = remaining.subtract(canTransfer);
            }

            // 更新计划行项物料的已转工单量
            BigDecimal actualTransferred = transferQty.subtract(remaining);
            pim.setReleasedToWoQty(alreadyReleased.add(actualTransferred));
            planItemMaterialMapper.updateById(pim);
        }
    }

    /**
     * 查询计划行项的物料需求及锁定状态
     */
    public List<Map<String, Object>> getPlanItemMaterials(Long planItemId) {
        List<BizPlanItemMaterial> pimList = planItemMaterialMapper.selectList(
                new LambdaQueryWrapper<BizPlanItemMaterial>()
                        .eq(BizPlanItemMaterial::getPlanItemId, planItemId));

        Set<Long> matIds = pimList.stream().map(BizPlanItemMaterial::getMaterialId).collect(Collectors.toSet());
        Map<Long, BizMaterial> matMap = matIds.isEmpty() ? Map.of() :
                materialMapper.selectBatchIds(matIds).stream()
                        .collect(Collectors.toMap(BizMaterial::getId, m -> m, (a, b) -> a));

        return pimList.stream().map(pim -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", pim.getId());
            map.put("materialId", pim.getMaterialId());
            BizMaterial mat = matMap.get(pim.getMaterialId());
            map.put("materialName", mat != null ? mat.getMaterialName() : "");
            map.put("materialNo", mat != null ? mat.getMaterialNo() : "");
            map.put("spec", mat != null ? mat.getSpec() : "");
            map.put("unit", mat != null ? mat.getUnit() : "");
            map.put("requiredQty", pim.getRequiredQty());
            map.put("lockedQty", pim.getLockedQty());
            map.put("shortageQty", pim.getShortageQty());
            map.put("releasedToWoQty", pim.getReleasedToWoQty());
            // 可用库存
            if (pim.getWarehouseId() != null) {
                BigDecimal available = inventoryService.getAvailableQty(pim.getMaterialId(), pim.getWarehouseId());
                map.put("availableQty", available);
            } else {
                map.put("availableQty", BigDecimal.ZERO);
            }
            return map;
        }).collect(Collectors.toList());
    }

    /**
     * 工单完工后同步计划行项完工数量和状态
     * 汇总该计划行下所有工单的 completedQty，更新计划行的 completedQty 和状态；
     * 若计划行全部完成，则尝试将整个生产计划标记为 completed
     */
    public void syncPlanItemCompletedQty(Long planItemId) {
        if (planItemId == null) return;
        BizProductionPlanItem item = planItemMapper.selectById(planItemId);
        if (item == null) return;

        // 汇总该计划行下所有未取消工单的 completedQty
        List<BizWorkOrder> workOrders = workOrderService.list(
                new LambdaQueryWrapper<BizWorkOrder>()
                        .eq(BizWorkOrder::getPlanItemId, planItemId)
                        .ne(BizWorkOrder::getStatus, "cancelled"));
        BigDecimal totalCompleted = workOrders.stream()
                .map(wo -> wo.getCompletedQty() != null ? wo.getCompletedQty() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        item.setCompletedQty(totalCompleted);
        // 计划行全部完成：所有工单已完工（completed）且累计完工数 >= 计划数
        boolean allWoDone = !workOrders.isEmpty() && workOrders.stream()
                .allMatch(wo -> "completed".equals(wo.getStatus()));
        if (allWoDone && item.getPlanQty() != null && totalCompleted.compareTo(item.getPlanQty()) >= 0) {
            item.setStatus("completed");
        }
        planItemMapper.updateById(item);
        log.info("计划行项完工数已同步: planItemId={}, completedQty={}, status={}", planItemId, totalCompleted, item.getStatus());

        // 若计划行已完成，检查整个生产计划是否全部完成
        if ("completed".equals(item.getStatus())) {
            syncPlanStatusIfAllCompleted(item.getPlanId());
        }
    }

    /**
     * 若生产计划下所有行项均已完成，将计划状态更新为 completed
     */
    private void syncPlanStatusIfAllCompleted(Long planId) {
        if (planId == null) return;
        long unfinished = planItemMapper.selectCount(
                new LambdaQueryWrapper<BizProductionPlanItem>()
                        .eq(BizProductionPlanItem::getPlanId, planId)
                        .ne(BizProductionPlanItem::getStatus, "completed"));
        if (unfinished == 0) {
            update(new LambdaUpdateWrapper<BizProductionPlan>()
                    .eq(BizProductionPlan::getId, planId)
                    .set(BizProductionPlan::getStatus, "completed"));
            log.info("生产计划全部行项已完工，计划状态更新为 completed: planId={}", planId);
        }
    }

    /** 将 JSON 字符串解析为 List，解析失败则返回 null */
    @SuppressWarnings("unchecked")
    private List<Object> parseJsonList(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, List.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 合并计划级(销售订单头继承)和销售订单行级扩展字段, 输出工单级 ext_fields JSON.
     * sales_order 与 sales_order_item 模块的 fieldKey 不允许重叠(配置时已校验),
     * 此处用 Map 合并即可,行级字段直接 putAll 不会覆盖头部字段(因为 key 不冲突)。
     */
    private String mergeExtFields(String planExt, Long orderItemId) {
        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
        Map<String, Object> merged = new java.util.LinkedHashMap<>();
        if (planExt != null && !planExt.isEmpty()) {
            try { merged.putAll(om.readValue(planExt, Map.class)); } catch (Exception ignore) {}
        }
        if (orderItemId != null) {
            com.aimes.modules.order.entity.BizSalesOrderItem orderItem = salesOrderItemMapper.selectById(orderItemId);
            if (orderItem != null && orderItem.getExtFields() != null && !orderItem.getExtFields().isEmpty()) {
                try { merged.putAll(om.readValue(orderItem.getExtFields(), Map.class)); } catch (Exception ignore) {}
            }
        }
        if (merged.isEmpty()) return null;
        try { return om.writeValueAsString(merged); } catch (Exception e) { return planExt; }
    }
}
