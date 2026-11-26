package com.aimes.modules.analysis.controller;

import com.aimes.common.result.R;
import com.aimes.modules.analysis.service.CustomerAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 客户分析看板
 */
@RestController
@RequestMapping("/api/analysis/customer")
@RequiredArgsConstructor
public class CustomerAnalysisController {

    private final CustomerAnalysisService service;

    /** 一次性获取全部看板数据 */
    @GetMapping("/all")
    public R<Map<String, Object>> all() {
        return R.ok(service.getAll());
    }

    @GetMapping("/summary")
    public R<Map<String, Object>> summary() {
        return R.ok(service.getSummary());
    }

    @GetMapping("/growth-trend")
    public R<List<Map<String, Object>>> growthTrend() {
        return R.ok(service.getGrowthTrend());
    }

    @GetMapping("/settlement-distribution")
    public R<List<Map<String, Object>>> settlementDistribution() {
        return R.ok(service.getSettlementDistribution());
    }

    @GetMapping("/top-by-amount")
    public R<List<Map<String, Object>>> topByAmount() {
        return R.ok(service.getTopByAmount());
    }

    @GetMapping("/credit-usage")
    public R<List<Map<String, Object>>> creditUsage() {
        return R.ok(service.getCreditUsage());
    }

    @GetMapping("/status-distribution")
    public R<List<Map<String, Object>>> statusDistribution() {
        return R.ok(service.getStatusDistribution());
    }

    @GetMapping("/order-frequency")
    public R<List<Map<String, Object>>> orderFrequency() {
        return R.ok(service.getOrderFrequency());
    }

    @GetMapping("/monthly-order-trend")
    public R<List<Map<String, Object>>> monthlyOrderTrend() {
        return R.ok(service.getMonthlyOrderTrend());
    }

    /** 客户看板：汇总表格+排名+地区分布 */
    @GetMapping("/kanban")
    public R<Map<String, Object>> kanban() {
        return R.ok(service.getKanbanAll());
    }

    @GetMapping("/return-analysis")
    public R<Map<String, Object>> returnAnalysis(
            @RequestParam(defaultValue = "halfYear") String period) {
        return R.ok(service.getReturnAnalysis(period));
    }
}
