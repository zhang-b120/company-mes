package com.aimes.modules.dashboard.controller;

import com.aimes.common.result.R;
import com.aimes.modules.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 看板/仪表盘控制器
 * 提供首页统计数据接口：汇总卡片、生产趋势图、订单状态饼图
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    public R<Map<String, Object>> summary() {
        return R.ok(dashboardService.getSummary());
    }

    @GetMapping("/production-trend")
    public R<List<Map<String, Object>>> productionTrend() {
        return R.ok(dashboardService.getProductionTrend());
    }

    @GetMapping("/order-status")
    public R<Map<String, Object>> orderStatus() {
        return R.ok(dashboardService.getOrderStatus());
    }

    @GetMapping("/role")
    public R<Map<String, Object>> roleDashboard(@RequestParam(defaultValue = "boss") String role) {
        return R.ok(dashboardService.getRoleDashboard(role));
    }

    @GetMapping("/data")
    public R<Map<String, Object>> dashboardData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.putAll(dashboardService.getSummary());
        data.put("productionTrend", dashboardService.getProductionTrend());
        data.put("orderStatus", dashboardService.getOrderStatus());
        return R.ok(data);
    }

    @GetMapping("/alerts")
    public R<Map<String, Object>> alerts() {
        return R.ok(dashboardService.getAlerts());
    }

    @GetMapping("/ai-briefing")
    public R<Map<String, Object>> aiBriefing() {
        return R.ok(dashboardService.getAiBriefing());
    }
}
