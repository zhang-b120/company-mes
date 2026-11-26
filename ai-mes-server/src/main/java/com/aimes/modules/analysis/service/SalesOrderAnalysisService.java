package com.aimes.modules.analysis.service;

import com.aimes.modules.analysis.mapper.SalesOrderAnalysisMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SalesOrderAnalysisService {

    private final SalesOrderAnalysisMapper mapper;

    public Map<String, Object> getSummary() {
        return mapper.getSummary();
    }

    public List<Map<String, Object>> getOrderTrend() {
        return mapper.getOrderTrend();
    }

    public List<Map<String, Object>> getStatusDistribution() {
        return mapper.getStatusDistribution();
    }

    public List<Map<String, Object>> getTopProducts() {
        return mapper.getTopProducts();
    }

    public List<Map<String, Object>> getMonthlyRevenue() {
        return mapper.getMonthlyRevenue();
    }

    public List<Map<String, Object>> getSalesRanking() {
        return mapper.getSalesRanking();
    }

    public List<Map<String, Object>> getCustomerRanking() {
        return mapper.getCustomerRanking();
    }

    public Map<String, Object> getDeliveryPerformance() {
        return mapper.getDeliveryPerformance();
    }

    public List<Map<String, Object>> getAmountDistribution() {
        return mapper.getAmountDistribution();
    }

    public List<Map<String, Object>> getDailyOrderCount() {
        return mapper.getDailyOrderCount();
    }

    public List<Map<String, Object>> getSettlementAmountDistribution() {
        return mapper.getSettlementAmountDistribution();
    }

    /** 一次性获取全部看板数据 */
    public Map<String, Object> getAll() {
        Map<String, Object> result = new HashMap<>();
        result.put("summary", getSummary());
        result.put("orderTrend", getOrderTrend());
        result.put("statusDistribution", getStatusDistribution());
        result.put("topProducts", getTopProducts());
        result.put("monthlyRevenue", getMonthlyRevenue());
        result.put("salesRanking", getSalesRanking());
        result.put("customerRanking", getCustomerRanking());
        result.put("deliveryPerformance", getDeliveryPerformance());
        result.put("amountDistribution", getAmountDistribution());
        result.put("dailyOrderCount", getDailyOrderCount());
        result.put("settlementAmountDistribution", getSettlementAmountDistribution());
        return result;
    }
}
