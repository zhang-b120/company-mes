package com.aimes.modules.quality.controller;

import com.aimes.common.result.PageResult;
import com.aimes.common.result.R;
import com.aimes.common.utils.ExcelUtil;
import com.aimes.modules.quality.entity.BizQcResult;
import com.aimes.modules.quality.entity.BizQcTask;
import com.aimes.modules.quality.service.BizQcTaskService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@RestController
@RequestMapping("/api/quality/tasks")
@RequiredArgsConstructor
public class BizQcTaskController {

    private final BizQcTaskService taskService;

    @GetMapping("/page")
    public R<PageResult<Map<String, Object>>> page(
            @RequestParam(required = false) String qcType,
            @RequestParam(required = false) String relatedOrderType,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String status,   // 前端传 status，与 result 等效
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String taskNo,
            @RequestParam(required = false) String workOrderNo,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        // status 是前端传来的别名，优先 result，其次 status
        String effectiveResult = org.springframework.util.StringUtils.hasText(result) ? result : status;
        return R.ok(taskService.getPage(qcType, relatedOrderType, effectiveResult, keyword,
                taskNo, workOrderNo, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(taskService.getDetail(id));
    }

    @PostMapping
    public R<Long> create(@RequestBody BizQcTask task) {
        taskService.createTask(task);
        return R.ok(task.getId());
    }

    @SuppressWarnings("unchecked")
    @PutMapping("/{id}/submit")
    public R<Void> submitResult(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        List<BizQcResult> results = new java.util.ArrayList<>();
        Object resultsObj = body.get("results");
        if (resultsObj instanceof List) {
            List<Map<String, Object>> resultMaps = (List<Map<String, Object>>) resultsObj;
            for (Map<String, Object> m : resultMaps) {
                BizQcResult r = new BizQcResult();
                if (m.get("templateItemId") != null) r.setTemplateItemId(Long.valueOf(m.get("templateItemId").toString()));
                if (m.get("itemName") != null) r.setItemName(m.get("itemName").toString());
                if (m.get("standardValue") != null) r.setStandardValue(m.get("standardValue").toString());
                if (m.get("toleranceMin") != null) r.setToleranceMin(m.get("toleranceMin").toString());
                if (m.get("toleranceMax") != null) r.setToleranceMax(m.get("toleranceMax").toString());
                if (m.get("actualValue") != null) r.setActualValue(m.get("actualValue").toString());
                if (m.get("isPass") != null) r.setIsPass(Integer.valueOf(m.get("isPass").toString()));
                if (m.get("photoUrl") != null) r.setPhotoUrl(m.get("photoUrl").toString());
                if (m.get("remark") != null) r.setRemark(m.get("remark").toString());
                results.add(r);
            }
        }
        String conclusion = body.get("conclusion") != null ? body.get("conclusion").toString() : null;
        // 支持直接传入合格/不合格数量（采购收料、外协收料、生产报工场景）
        java.math.BigDecimal inputPassQty = body.get("passQty") != null ? new java.math.BigDecimal(body.get("passQty").toString()) : null;
        java.math.BigDecimal inputFailQty = body.get("failQty") != null ? new java.math.BigDecimal(body.get("failQty").toString()) : null;
        // 解析检验记录（合格/不合格维度）
        List<Map<String, Object>> records = null;
        Object recordsObj = body.get("records");
        if (recordsObj instanceof List) {
            records = (List<Map<String, Object>>) recordsObj;
        }
        Long templateId = body.get("templateId") != null ? Long.valueOf(body.get("templateId").toString()) : null;
        java.math.BigDecimal sampleQty = body.get("sampleQty") != null ? new java.math.BigDecimal(body.get("sampleQty").toString()) : null;
        String remark = body.get("remark") != null ? body.get("remark").toString() : null;
        taskService.submitResult(id, results, conclusion, inputPassQty, inputFailQty, records, templateId, sampleQty, remark);
        return R.ok();
    }

    @GetMapping("/records")
    public R<PageResult<Map<String, Object>>> records(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String taskNo,
            @RequestParam(required = false) String batchNo,
            @RequestParam(required = false) String qcType,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(taskService.getRecords(keyword, taskNo, batchNo, qcType, result, startDate, endDate, pageNum, pageSize));
    }

    @GetMapping("/records/stats")
    public R<Map<String, Object>> recordStats(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String taskNo,
            @RequestParam(required = false) String batchNo,
            @RequestParam(required = false) String qcType,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return R.ok(taskService.getRecordStats(keyword, taskNo, batchNo, qcType, startDate, endDate));
    }

    @GetMapping("/records/export")
    public void exportRecords(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String taskNo,
            @RequestParam(required = false) String batchNo,
            @RequestParam(required = false) String qcType,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            HttpServletResponse response) throws Exception {
        var page = taskService.getRecords(keyword, taskNo, batchNo, qcType, result,
                startDate, endDate, 1, 100000);
        Map<String, String> typeMap = Map.of(
                "incoming", "来料检验",
                "process", "过程检验",
                "final", "成品检验",
                "outsource", "外协检验");
        List<String> headers = List.of("任务编号", "检验类型", "产品/物料", "关联单号",
                "批次号", "送检数", "实检数", "合格数", "不合格数", "检验结果",
                "检验员", "检验时间", "结论", "备注");
        List<Function<Map<String, Object>, Object>> ex = List.of(
                m -> m.get("taskNo"),
                m -> typeMap.getOrDefault(String.valueOf(m.get("qcType")), String.valueOf(m.get("qcType"))),
                m -> {
                    Object pn = m.get("productName");
                    if (pn != null && !String.valueOf(pn).isBlank()) return pn;
                    return m.get("materialName");
                },
                m -> {
                    Object t = m.get("qcType");
                    if ("incoming".equals(t)) return m.get("purchaseOrderNo");
                    if ("outsource".equals(t)) return m.get("outsourceOrderNo");
                    return m.get("workOrderNo");
                },
                m -> m.get("batchNo"),
                m -> m.get("planQty"),
                m -> m.get("sampleQty"),
                m -> m.get("passQty"),
                m -> m.get("failQty"),
                m -> {
                    Object r = m.get("result");
                    Object fq = m.get("failQty");
                    boolean isPass = "pass".equals(r)
                            || ("completed".equals(r) && (fq == null
                                || (fq instanceof BigDecimal && ((BigDecimal) fq).compareTo(BigDecimal.ZERO) == 0)
                                || (fq instanceof Number && ((Number) fq).doubleValue() == 0d)));
                    return isPass ? "合格" : "不合格";
                },
                m -> m.get("inspectorName"),
                m -> m.get("inspectedAt"),
                m -> m.get("conclusion"),
                m -> m.get("remark"));
        ExcelUtil.export(response, "质检记录", headers, page.getRecords(), ex);
    }

    @SuppressWarnings("unchecked")
    @PutMapping("/{id}/dispose")
    public R<Map<String, Object>> dispose(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return R.ok(taskService.disposeFailedTask(id, body));
    }

    @GetMapping("/traceability")
    public R<Map<String, Object>> traceability(
            @RequestParam(required = false) Long workOrderId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String batchNo) {
        return R.ok(taskService.getTraceability(workOrderId, productId, batchNo));
    }

    @GetMapping("/analysis/overview")
    public R<Map<String, Object>> analysisOverview(@RequestParam(required = false, defaultValue = "month") String period) {
        return R.ok(taskService.getAnalysisOverview(period));
    }

    @GetMapping("/analysis/pass-rate-trend")
    public R<List<Map<String, Object>>> passRateTrend(@RequestParam(required = false, defaultValue = "month") String period) {
        return R.ok(taskService.getPassRateTrend(period));
    }

    @GetMapping("/analysis/defect-distribution")
    public R<List<Map<String, Object>>> defectDistribution(@RequestParam(required = false, defaultValue = "month") String period) {
        return R.ok(taskService.getDefectDistribution(period));
    }
}
