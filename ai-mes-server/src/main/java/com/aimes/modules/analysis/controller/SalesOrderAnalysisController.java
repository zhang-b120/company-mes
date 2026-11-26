package com.aimes.modules.analysis.controller;

import com.aimes.common.result.R;
import com.aimes.modules.analysis.service.SalesOrderAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 销售订单分析看板
 */
@RestController
@RequestMapping("/api/analysis/sales-order")
@RequiredArgsConstructor
public class SalesOrderAnalysisController {

    private final SalesOrderAnalysisService service;

    /** 一次性获取全部看板数据 */
    @GetMapping("/all")
    public R<Map<String, Object>> all() {
        return R.ok(service.getAll());
    }

    @GetMapping("/summary")
    public R<Map<String, Object>> summary() {
        return R.ok(service.getSummary());
    }

    @GetMapping("/order-trend")
    public R<List<Map<String, Object>>> orderTrend() {
        return R.ok(service.getOrderTrend());
    }

    @GetMapping("/status-distribution")
    public R<List<Map<String, Object>>> statusDistribution() {
        return R.ok(service.getStatusDistribution());
    }

    @GetMapping("/top-products")
    public R<List<Map<String, Object>>> topProducts() {
        return R.ok(service.getTopProducts());
    }

    @GetMapping("/monthly-revenue")
    public R<List<Map<String, Object>>> monthlyRevenue() {
        return R.ok(service.getMonthlyRevenue());
    }

    @GetMapping("/sales-ranking")
    public R<List<Map<String, Object>>> salesRanking() {
        return R.ok(service.getSalesRanking());
    }

    @GetMapping("/customer-ranking")
    public R<List<Map<String, Object>>> customerRanking() {
        return R.ok(service.getCustomerRanking());
    }

    @GetMapping("/delivery-performance")
    public R<Map<String, Object>> deliveryPerformance() {
        return R.ok(service.getDeliveryPerformance());
    }

    @GetMapping("/amount-distribution")
    public R<List<Map<String, Object>>> amountDistribution() {
        return R.ok(service.getAmountDistribution());
    }

    @GetMapping("/daily-order-count")
    public R<List<Map<String, Object>>> dailyOrderCount() {
        return R.ok(service.getDailyOrderCount());
    }

    @GetMapping("/settlement-amount-distribution")
    public R<List<Map<String, Object>>> settlementAmountDistribution() {
        return R.ok(service.getSettlementAmountDistribution());
    }
}
