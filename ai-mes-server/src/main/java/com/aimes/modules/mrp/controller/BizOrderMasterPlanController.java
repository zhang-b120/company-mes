package com.aimes.modules.mrp.controller;

import com.aimes.common.result.R;
import com.aimes.modules.mrp.service.BizOrderMasterPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 订单主计划: 销售订单 → 多级 BOM 展开 + 库存/在途/缺口
 */
@RestController
@RequestMapping("/api/master-plan")
@RequiredArgsConstructor
public class BizOrderMasterPlanController {

    private final BizOrderMasterPlanService masterPlanService;

    /** 触发实时计算, 返回完整树 */
    @GetMapping("/calculate")
    public R<List<Map<String, Object>>> calculate(@RequestParam Long orderId) {
        return R.ok(masterPlanService.calculate(orderId));
    }

    /** 已加入主计划的销售订单列表 */
    @GetMapping("/orders")
    public R<List<Map<String, Object>>> listOrders() {
        return R.ok(masterPlanService.listOrders());
    }

    /** 把销售订单加入主计划 (从订单详情页"转主计划"调) */
    @PostMapping("/add-order/{orderId}")
    public R<Void> addOrder(@PathVariable Long orderId) {
        masterPlanService.addOrder(orderId);
        return R.ok();
    }

    /** 从主计划移除 */
    @DeleteMapping("/orders/{orderId}")
    public R<Void> removeOrder(@PathVariable Long orderId) {
        masterPlanService.removeOrder(orderId);
        return R.ok();
    }

    /** 转生产计划: 创建一条草稿生产计划单, 返回新计划 ID */
    @PostMapping("/convert-to-wo")
    @SuppressWarnings("unchecked")
    public R<Map<String, Object>> convertToWo(@RequestBody Map<String, Object> body) {
        Long orderId = Long.valueOf(body.get("orderId").toString());
        Long productId = Long.valueOf(body.get("productId").toString());
        BigDecimal qty = new BigDecimal(body.get("qty").toString());
        // 主计划替代料选择: {materialId(String) → substituteId(Number)}
        Map<Long, Long> substituteSels = null;
        if (body.get("substituteSelections") instanceof Map<?, ?> rawSels) {
            substituteSels = new java.util.HashMap<>();
            for (Map.Entry<?, ?> e : rawSels.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    substituteSels.put(Long.valueOf(e.getKey().toString()),
                            Long.valueOf(e.getValue().toString()));
                }
            }
        }
        Long planId = masterPlanService.convertToProductionPlan(orderId, productId, qty, substituteSels);
        return R.ok(Map.of("planId", planId));
    }

    /** 转采购申请: 创建一条草稿采购申请, 返回新申请 ID (用户后续在采购申请页面编辑/提交审批) */
    @PostMapping("/convert-to-po")
    public R<Map<String, Object>> convertToPo(@RequestBody Map<String, Object> body) {
        Long orderId = Long.valueOf(body.get("orderId").toString());
        Long materialId = Long.valueOf(body.get("materialId").toString());
        BigDecimal qty = new BigDecimal(body.get("qty").toString());
        Long prId = masterPlanService.convertToPurchaseRequest(orderId, materialId, qty);
        // 兼容前端: prId 同时塞回 poId 字段, 避免破坏老前端代码
        return R.ok(Map.of("prId", prId, "poId", prId));
    }
}
