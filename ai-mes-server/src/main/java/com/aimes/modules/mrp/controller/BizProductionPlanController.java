package com.aimes.modules.mrp.controller;

import com.aimes.common.result.R;
import com.aimes.common.result.PageResult;
import com.aimes.common.utils.ExcelUtil;
import com.aimes.modules.mrp.entity.BizProductionPlan;
import com.aimes.modules.mrp.entity.BizProductionPlanAttachment;
import com.aimes.modules.mrp.entity.BizProductionPlanItem;
import com.aimes.modules.mrp.entity.BizProductionPlanItemStep;
import com.aimes.modules.mrp.service.BizProductionPlanAttachmentService;
import com.aimes.modules.mrp.service.BizProductionPlanService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 生产计划接口
 */
@RestController
@RequestMapping("/api/production-plan")
@RequiredArgsConstructor
public class BizProductionPlanController {

    private final BizProductionPlanService planService;
    private final BizProductionPlanAttachmentService planAttachmentService;

    // ==================== 统计 ====================

    @GetMapping("/statistics")
    public R<Map<String, Object>> statistics() {
        return R.ok(planService.getStatistics());
    }

    // ==================== 计划CRUD ====================

    /**
     * 计划分页
     */
    @GetMapping("/page")
    public R<PageResult<Map<String, Object>>> page(
            @RequestParam(required = false) String planNo,
            @RequestParam(required = false) String planName,
            @RequestParam(required = false) String materialNo,
            @RequestParam(required = false) String materialName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String spec,
            @RequestParam(required = false) String orderNo,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam java.util.Map<String, String> allParams) {
        java.util.Map<String, String> extFieldFilters = new java.util.HashMap<>();
        allParams.forEach((k, v) -> {
            if (k.startsWith("ext_") && org.springframework.util.StringUtils.hasText(v)) {
                extFieldFilters.put(k.substring(4), v);
            }
        });
        return R.ok(planService.getPage(planNo, planName, materialNo, materialName, status, spec, orderNo, extFieldFilters, pageNum, pageSize));
    }

    /**
     * 计划详情
     */
    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(planService.getDetail(id));
    }

    /**
     * 创建计划
     */
    @PostMapping
    public R<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        BizProductionPlan plan = new BizProductionPlan();
        plan.setPlanName((String) body.get("planName"));
        if (body.get("planDateFrom") != null) plan.setPlanDateFrom(java.time.LocalDate.parse((String) body.get("planDateFrom")));
        if (body.get("planDateTo") != null) plan.setPlanDateTo(java.time.LocalDate.parse((String) body.get("planDateTo")));
        plan.setRemark((String) body.get("remark"));

        List<BizProductionPlanItem> items = null;
        if (body.get("items") != null) {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            om.findAndRegisterModules();
            items = om.convertValue(body.get("items"),
                    om.getTypeFactory().constructCollectionType(List.class, BizProductionPlanItem.class));
        }

        Long planId = planService.createPlan(plan, items);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", planId);
        return R.ok(result);
    }

    /**
     * 更新计划
     */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizProductionPlan plan) {
        planService.updatePlan(id, plan);
        return R.ok();
    }

    /**
     * 确认计划
     */
    @PostMapping("/{id}/confirm")
    public R<Void> confirm(@PathVariable Long id) {
        planService.confirmPlan(id);
        return R.ok();
    }

    /**
     * 确认并开始任务：确认计划 + 对所有行项下达工单
     */
    @PostMapping("/{id}/confirm-and-start")
    public R<Map<String, Object>> confirmAndStart(@PathVariable Long id) {
        return R.ok(planService.confirmAndReleaseAll(id));
    }

    /**
     * 取消计划
     */
    @PostMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) {
        planService.cancelPlan(id);
        return R.ok();
    }

    // ==================== 批量操作 ====================

    /**
     * 批量派工（确认并下达工单）
     */
    @PostMapping("/batch-confirm-and-start")
    public R<Map<String, Object>> batchConfirmAndStart(@RequestBody Map<String, List<Long>> body) {
        return R.ok(planService.batchConfirmAndStart(body.get("ids")));
    }

    /**
     * 批量取消计划
     */
    @PostMapping("/batch-cancel")
    public R<Map<String, Object>> batchCancel(@RequestBody Map<String, List<Long>> body) {
        return R.ok(planService.batchCancelPlans(body.get("ids")));
    }

    // ==================== 物料齐套检查 ====================

    /**
     * 检查计划物料齐套性
     */
    @GetMapping("/{planId}/check-material")
    public R<List<Map<String, Object>>> checkMaterial(@PathVariable Long planId) {
        return R.ok(planService.checkPlanMaterial(planId));
    }

    // ==================== 行项物料锁定查询 ====================

    /**
     * 查询行项的物料需求及锁定状态
     */
    @GetMapping("/items/{itemId}/materials")
    public R<List<Map<String, Object>>> getItemMaterials(@PathVariable Long itemId) {
        return R.ok(planService.getPlanItemMaterials(itemId));
    }

    // ==================== 行项操作 ====================

    /**
     * 添加行项
     */
    @PostMapping("/{planId}/items")
    public R<Void> addItem(@PathVariable Long planId, @RequestBody BizProductionPlanItem item) {
        planService.addPlanItem(planId, item);
        return R.ok();
    }

    /**
     * 更新行项
     */
    @PutMapping("/items/{itemId}")
    public R<Void> updateItem(@PathVariable Long itemId, @RequestBody BizProductionPlanItem item) {
        planService.updatePlanItem(itemId, item);
        return R.ok();
    }

    /**
     * 删除行项
     */
    @DeleteMapping("/items/{itemId}")
    public R<Void> deleteItem(@PathVariable Long itemId) {
        planService.deletePlanItem(itemId);
        return R.ok();
    }

    // ==================== 工序指派配置 ====================

    /**
     * 保存行项的工序指派配置
     */
    @PostMapping("/items/{itemId}/step-configs")
    public R<Void> saveStepConfigs(@PathVariable Long itemId, @RequestBody List<BizProductionPlanItemStep> steps) {
        planService.saveItemStepConfigs(itemId, steps);
        return R.ok();
    }

    /**
     * 获取行项的工序指派配置
     */
    @GetMapping("/items/{itemId}/step-configs")
    public R<List<BizProductionPlanItemStep>> getStepConfigs(@PathVariable Long itemId) {
        return R.ok(planService.getItemStepConfigs(itemId));
    }

    // ==================== 从MRP生产建议创建 ====================

    /**
     * 从MRP生产建议创建/合并到计划
     * body: { suggestionIds: [1,2,3], planId: null }
     */
    @PostMapping("/from-suggestions")
    public R<Map<String, Object>> createFromSuggestions(@RequestBody Map<String, Object> body) {
        List<Number> ids = (List<Number>) body.get("suggestionIds");
        List<Long> suggestionIds = ids.stream().map(Number::longValue).collect(java.util.stream.Collectors.toList());
        Long targetPlanId = body.get("planId") != null ? ((Number) body.get("planId")).longValue() : null;

        List<Long> planIds = planService.createFromSuggestions(suggestionIds, targetPlanId);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("planIds", planIds);
        result.put("count", planIds.size());
        return R.ok(result);
    }

    // ==================== 下达工单 ====================

    /**
     * 下达工单
     * body: { releaseQty: 100 } 可选，不传则全部下达
     */
    @PostMapping("/items/{itemId}/release")
    public R<Map<String, Object>> releaseWorkOrder(@PathVariable Long itemId,
                                                    @RequestBody(required = false) Map<String, Object> body) {
        BigDecimal releaseQty = null;
        if (body != null && body.get("releaseQty") != null) {
            releaseQty = new BigDecimal(body.get("releaseQty").toString());
        }
        Long woId = planService.releaseWorkOrder(itemId, releaseQty);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("workOrderId", woId);
        return R.ok(result);
    }

    /**
     * 批量下达工单
     */
    @PostMapping("/items/batch-release")
    public R<Map<String, Object>> batchRelease(@RequestBody Map<String, List<Long>> body) {
        List<Long> woIds = planService.batchReleaseWorkOrders(body.get("itemIds"));
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("workOrderIds", woIds);
        return R.ok(result);
    }

    // ==================== 附件管理 ====================

    @PostMapping("/{planId}/attachments")
    public R<BizProductionPlanAttachment> uploadAttachment(
            @PathVariable Long planId,
            @RequestParam("file") MultipartFile file) throws java.io.IOException {
        BizProductionPlan plan = planService.getById(planId);
        if (plan == null) return R.fail("生产计划不存在");
        return R.ok(planAttachmentService.upload(planId, plan.getPlanNo(), file));
    }

    @GetMapping("/{planId}/attachments")
    public R<List<BizProductionPlanAttachment>> getAttachments(@PathVariable Long planId) {
        return R.ok(planAttachmentService.getByPlanId(planId));
    }

    @DeleteMapping("/attachments/{attachmentId}")
    public R<Void> deleteAttachment(@PathVariable Long attachmentId) {
        planAttachmentService.deleteAttachment(attachmentId);
        return R.ok();
    }

    // ==================== 导出 ====================

    @GetMapping("/{planId}/export")
    public void exportPlan(@PathVariable Long planId, HttpServletResponse response) throws Exception {
        List<Map<String, Object>> data = planService.getExportData(planId);
        ExcelUtil.export(response, "生产计划", PLAN_EXPORT_HEADERS, data, PLAN_EXPORT_EXTRACTORS);
    }

    @GetMapping("/export-batch")
    public void exportPlansBatch(@RequestParam String ids, HttpServletResponse response) throws Exception {
        List<Long> planIds = java.util.Arrays.stream(ids.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .map(Long::parseLong).collect(Collectors.toList());
        List<Map<String, Object>> data = planService.getExportDataBatch(planIds);
        ExcelUtil.export(response, "生产计划批量导出", PLAN_EXPORT_HEADERS, data, PLAN_EXPORT_EXTRACTORS);
    }

    private static final List<String> PLAN_EXPORT_HEADERS = List.of(
            "计划编号", "来源单据号", "产品名称", "规格", "单位", "计划数量", "已下达", "已完工",
            "计划开工", "计划完工", "优先级", "状态");
    private static final List<Function<Map<String, Object>, Object>> PLAN_EXPORT_EXTRACTORS = List.of(
            m -> m.get("planNo"), m -> m.get("sourceOrderNo"), m -> m.get("productName"), m -> m.get("spec"), m -> m.get("unit"),
            m -> m.get("planQty"), m -> m.get("releasedQty"), m -> m.get("completedQty"),
            m -> m.get("planStartDate"), m -> m.get("planEndDate"),
            m -> { Object p = m.get("priority"); return p instanceof Number ? List.of("低","中","高").get(Math.min(((Number) p).intValue(), 2)) : "低"; },
            m -> m.get("status")
    );
}
