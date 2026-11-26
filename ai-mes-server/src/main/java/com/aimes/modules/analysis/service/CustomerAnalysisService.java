package com.aimes.modules.analysis.service;

import com.aimes.modules.analysis.mapper.CustomerAnalysisMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomerAnalysisService {

    private final CustomerAnalysisMapper mapper;

    public Map<String, Object> getSummary() {
        return mapper.getSummary();
    }

    public List<Map<String, Object>> getGrowthTrend() {
        return mapper.getGrowthTrend();
    }

    public List<Map<String, Object>> getSettlementDistribution() {
        return mapper.getSettlementDistribution();
    }

    public List<Map<String, Object>> getTopByAmount() {
        return mapper.getTopByAmount();
    }

    public List<Map<String, Object>> getCreditUsage() {
        return mapper.getCreditUsage();
    }

    public List<Map<String, Object>> getStatusDistribution() {
        return mapper.getStatusDistribution();
    }

    public List<Map<String, Object>> getOrderFrequency() {
        return mapper.getOrderFrequency();
    }

    public List<Map<String, Object>> getMonthlyOrderTrend() {
        return mapper.getMonthlyOrderTrend();
    }

    /** 客户看板：一次性获取全部数据 */
    public Map<String, Object> getKanbanAll() {
        Map<String, Object> result = new HashMap<>();
        result.put("summaryTable", mapper.getKanbanSummaryTable());
        result.put("topByAmount", mapper.getTopByAmount());
        result.put("topByDebt", mapper.getTopByDebt());
        result.put("topByPayment", mapper.getTopByPayment());
        result.put("regionDistribution", mapper.getRegionDistribution());
        return result;
    }

    /** 退货分析：汇总 + 按客户 + 按产品 + 月度趋势 */
    public Map<String, Object> getReturnAnalysis(String period) {
        String startDate = periodToStartDate(period);
        Map<String, Object> result = new HashMap<>();
        result.put("summary", mapper.getReturnSummary(startDate));
        result.put("byCustomer", mapper.getReturnByCustomer(startDate));
        result.put("byProduct", mapper.getReturnByProduct(startDate));
        result.put("monthlyTrend", mapper.getReturnMonthlyTrend());
        return result;
    }

    private String periodToStartDate(String period) {
        if (period == null || period.isBlank() || "all".equals(period)) return "";
        LocalDate today = LocalDate.now();
        return switch (period) {
            case "month"    -> today.minusMonths(1).toString();
            case "quarter"  -> today.minusMonths(3).toString();
            case "halfYear" -> today.minusMonths(6).toString();
            case "year"     -> today.minusYears(1).toString();
            default         -> "";
        };
    }

    /** 一次性获取全部看板数据 */
    public Map<String, Object> getAll() {
        Map<String, Object> result = new HashMap<>();
        result.put("summary", getSummary());
        result.put("growthTrend", getGrowthTrend());
        result.put("settlementDistribution", getSettlementDistribution());
        result.put("topByAmount", getTopByAmount());
        result.put("creditUsage", getCreditUsage());
        result.put("statusDistribution", getStatusDistribution());
        result.put("orderFrequency", getOrderFrequency());
        result.put("monthlyOrderTrend", getMonthlyOrderTrend());
        return result;
    }
}
