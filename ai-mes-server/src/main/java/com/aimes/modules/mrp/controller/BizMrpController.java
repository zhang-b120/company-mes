package com.aimes.modules.mrp.controller;

import com.aimes.common.result.R;
import com.aimes.common.result.PageResult;
import com.aimes.common.utils.ExcelUtil;
import com.aimes.modules.mrp.entity.BizMrpPurchaseSuggestion;
import com.aimes.modules.mrp.entity.BizMrpProductionSuggestion;
import com.aimes.modules.mrp.service.BizMrpService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * MRP物料需求计划接口
 */
@RestController
@RequestMapping("/api/mrp")
@RequiredArgsConstructor
public class BizMrpController {

    private final BizMrpService mrpService;

    // ==================== MRP统计 ====================

    @GetMapping("/statistics")
    public R<Map<String, Object>> statistics() {
        return R.ok(mrpService.getStatistics());
    }

    // ==================== 待运算订单 ====================

    /**
     * 获取已转计划单(mrp_planned)的订单列表，供MRP页面选择
     */
    @GetMapping("/planned-orders")
    public R<List<Map<String, Object>>> getPlannedOrders() {
        return R.ok(mrpService.getPlannedOrders());
    }

    /**
     * 从需求池中移除订单
     */
    @DeleteMapping("/demand-pool/{orderId}")
    public R<Void> removeFromDemandPool(@PathVariable Long orderId) {
        mrpService.removeFromDemandPool(orderId);
        return R.ok();
    }

    // ==================== MRP运行 ====================

    /**
     * 手动运行MRP
     * @param body { orderIds: [1,2,3], considerInTransit: true, considerSafetyStock: false, considerSubstitute: false }
     */
    @PostMapping("/run")
    public R<Map<String, Object>> runMrp(@RequestBody(required = false) Map<String, Object> body) {
        List<Long> orderIds = null;
        Map<String, Boolean> config = new java.util.HashMap<>();
        if (body != null) {
            if (body.get("orderIds") != null) {
                List<Number> ids = (List<Number>) body.get("orderIds");
                orderIds = ids.stream().map(Number::longValue).collect(java.util.stream.Collectors.toList());
            }
            config.put("considerInTransit", Boolean.TRUE.equals(body.get("considerInTransit")));
            config.put("considerSafetyStock", Boolean.TRUE.equals(body.get("considerSafetyStock")));
            config.put("considerSubstitute", Boolean.TRUE.equals(body.get("considerSubstitute")));
        }
        Long runId = mrpService.runMrp(orderIds, "manual", config);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("runId", runId);
        return R.ok(result);
    }

    /**
     * MRP运行记录分页
     */
    @GetMapping("/runs")
    public R<PageResult<Map<String, Object>>> runPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(mrpService.getRunPage(keyword, status, pageNum, pageSize));
    }

    /**
     * MRP运行详情
     */
    @GetMapping("/runs/{id}")
    public R<Map<String, Object>> runDetail(@PathVariable Long id) {
        return R.ok(mrpService.getRunDetail(id));
    }

    // ==================== 采购建议 ====================

    /**
     * 采购建议分页
     */
    @GetMapping("/purchase-suggestions")
    public R<PageResult<Map<String, Object>>> purchaseSuggestionPage(
            @RequestParam(required = false) Long mrpRunId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dateTo,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(mrpService.getPurchaseSuggestionPage(mrpRunId, status, dateFrom, dateTo, pageNum, pageSize));
    }

    /**
     * 修改采购建议
     */
    @PutMapping("/purchase-suggestions/{id}")
    public R<Void> updatePurchaseSuggestion(@PathVariable Long id,
                                             @RequestBody BizMrpPurchaseSuggestion update) {
        mrpService.updatePurchaseSuggestion(id, update);
        return R.ok();
    }

    /**
     * 确认采购建议 → 转采购申请
     */
    @PostMapping("/purchase-suggestions/{id}/confirm")
    public R<Void> confirmPurchaseSuggestion(@PathVariable Long id) {
        mrpService.confirmPurchaseSuggestion(id);
        return R.ok();
    }

    /**
     * 批量确认采购建议
     */
    @PostMapping("/purchase-suggestions/batch-confirm")
    public R<Void> batchConfirmPurchaseSuggestions(@RequestBody Map<String, List<Long>> body) {
        mrpService.batchConfirmPurchaseSuggestions(body.get("ids"));
        return R.ok();
    }

    // ==================== 生产建议 ====================

    /**
     * 生产建议分页
     */
    @GetMapping("/production-suggestions")
    public R<PageResult<Map<String, Object>>> productionSuggestionPage(
            @RequestParam(required = false) Long mrpRunId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dateTo,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(mrpService.getProductionSuggestionPage(mrpRunId, status, dateFrom, dateTo, pageNum, pageSize));
    }

    /**
     * 修改生产建议
     */
    @PutMapping("/production-suggestions/{id}")
    public R<Void> updateProductionSuggestion(@PathVariable Long id,
                                               @RequestBody BizMrpProductionSuggestion update) {
        mrpService.updateProductionSuggestion(id, update);
        return R.ok();
    }

    // ==================== 通用操作 ====================

    /**
     * 忽略建议
     */
    @PostMapping("/suggestions/{type}/{id}/ignore")
    public R<Void> ignoreSuggestion(@PathVariable String type, @PathVariable Long id) {
        mrpService.ignoreSuggestion(type, id);
        return R.ok();
    }

    // ==================== 导出 ====================

    @GetMapping("/purchase-suggestions/export")
    public void exportPurchaseSuggestions(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dateTo,
            HttpServletResponse response) throws Exception {
        List<Map<String, Object>> data = mrpService.getAllPurchaseSuggestions(status, dateFrom, dateTo);
        List<String> headers = List.of("物料编号", "物料名称", "规格", "单位", "建议数量", "需求日期", "建议下单日", "供应商", "来源订单", "状态");
        List<Function<Map<String, Object>, Object>> extractors = List.of(
                m -> m.get("materialNo"), m -> m.get("materialName"), m -> m.get("spec"), m -> m.get("unit"),
                m -> m.get("suggestQty"), m -> m.get("requiredDate"), m -> m.get("suggestOrderDate"),
                m -> m.get("supplierName"), m -> m.get("sourceOrderNos"), m -> m.get("status")
        );
        ExcelUtil.export(response, "采购建议", headers, data, extractors);
    }

    @GetMapping("/production-suggestions/export")
    public void exportProductionSuggestions(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dateTo,
            HttpServletResponse response) throws Exception {
        List<Map<String, Object>> data = mrpService.getAllProductionSuggestions(status, dateFrom, dateTo);
        List<String> headers = List.of("产品编号", "产品名称", "规格", "单位", "建议数量", "需求日期", "建议开工日", "类型", "来源订单", "状态");
        List<Function<Map<String, Object>, Object>> extractors = List.of(
                m -> m.get("productNo"), m -> m.get("productName"), m -> m.get("spec"), m -> m.get("unit"),
                m -> m.get("suggestQty"), m -> m.get("requiredDate"), m -> m.get("suggestStartDate"),
                m -> Boolean.TRUE.equals(m.get("isSemiFinished")) ? "半成品" : "成品",
                m -> m.get("sourceOrderNos"), m -> m.get("status")
        );
        ExcelUtil.export(response, "生产建议", headers, data, extractors);
    }
}
