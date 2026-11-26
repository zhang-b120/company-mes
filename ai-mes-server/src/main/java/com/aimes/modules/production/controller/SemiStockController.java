package com.aimes.modules.production.controller;

import com.aimes.common.result.R;
import com.aimes.modules.production.service.SemiStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 工序半成品入库 / 撤销 / 领料 接口
 * 与现有 work-order/work-report 模块解耦, 单独挂在 /api/production/semi-stock
 */
@RestController
@RequestMapping("/api/production/semi-stock")
@RequiredArgsConstructor
public class SemiStockController {

    private final SemiStockService semiStockService;

    /** 半成品入库: 生成 biz_inv_task (semi_in, pending), 仓管在"库存任务"里处理后落库. 返回任务 ID. */
    @PostMapping("/stock-in/{taskId}")
    public R<Long> stockIn(@PathVariable Long taskId,
                            @RequestBody Map<String, Object> body) {
        BigDecimal qty = body.get("qty") != null ? new BigDecimal(body.get("qty").toString()) : null;
        Long warehouseId = body.get("warehouseId") != null
                ? Long.valueOf(body.get("warehouseId").toString()) : null;
        return R.ok(semiStockService.stockIn(taskId, qty, warehouseId));
    }

    /** 撤销半成品入库 */
    @PostMapping("/revoke/{taskId}")
    public R<BigDecimal> revoke(@PathVariable Long taskId,
                                 @RequestBody Map<String, Object> body) {
        BigDecimal qty = body.get("qty") != null ? new BigDecimal(body.get("qty").toString()) : null;
        return R.ok(semiStockService.revokeStockIn(taskId, qty));
    }

    /** 下道工序领料 (taskId = 下道工序自身 id) */
    @PostMapping("/issue/{taskId}")
    public R<Void> issue(@PathVariable Long taskId,
                          @RequestBody Map<String, Object> body) {
        BigDecimal qty = body.get("qty") != null ? new BigDecimal(body.get("qty").toString()) : null;
        Long warehouseId = body.get("warehouseId") != null
                ? Long.valueOf(body.get("warehouseId").toString()) : null;
        semiStockService.issueSemiMaterial(taskId, qty, warehouseId);
        return R.ok();
    }

    /** 报工前置校验: 返回阻塞原因 + 本次报工最大允许数 (前端 input-number max 用) */
    @GetMapping("/check-report/{taskId}")
    public R<Map<String, Object>> checkReport(@PathVariable Long taskId) {
        String reason = semiStockService.checkReportBlocked(taskId);
        BigDecimal maxReportable = semiStockService.getMaxReportableByIssued(taskId);
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("blocked", reason != null);
        data.put("reason", reason != null ? reason : "");
        data.put("maxReportable", maxReportable); // null = 不受半成品约束
        return R.ok(data);
    }
}
