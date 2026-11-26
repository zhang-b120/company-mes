package com.aimes.modules.quality.controller;

import com.aimes.common.result.R;
import com.aimes.modules.quality.service.BizQcAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/quality/analysis")
@RequiredArgsConstructor
public class BizQcAnalysisController {

    private final BizQcAnalysisService analysisService;

    @GetMapping("/overview")
    public R<Map<String, Object>> overview() {
        return R.ok(analysisService.getOverview());
    }

    @GetMapping("/pass-rate-trend")
    public R<Map<String, Object>> passRateTrend(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(required = false) String qcType) {
        return R.ok(analysisService.getPassRateTrend(days, qcType));
    }

    @GetMapping("/defect-distribution")
    public R<Map<String, Object>> defectDistribution(
            @RequestParam(defaultValue = "30") int days) {
        return R.ok(analysisService.getDefectDistribution(days));
    }

    @GetMapping("/product-quality")
    public R<Map<String, Object>> productQuality(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "10") int top) {
        return R.ok(analysisService.getProductQualityRanking(days, top));
    }

    @GetMapping("/inspector-stats")
    public R<Map<String, Object>> inspectorStats(
            @RequestParam(defaultValue = "30") int days) {
        return R.ok(analysisService.getInspectorStats(days));
    }
}
