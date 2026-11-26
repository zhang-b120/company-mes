package com.aimes.modules.production.controller;

import com.aimes.common.result.PageResult;
import com.aimes.common.result.R;
import com.aimes.modules.production.entity.BizReworkOrder;
import com.aimes.modules.production.service.BizReworkOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/production/rework-orders")
@RequiredArgsConstructor
public class BizReworkOrderController {

    private final BizReworkOrderService reworkOrderService;

    @GetMapping("/page")
    public R<PageResult<Map<String, Object>>> page(
            @RequestParam(required = false) String reworkNo,
            @RequestParam(required = false) String workOrderNo,
            @RequestParam(required = false) String qcTaskNo,
            @RequestParam(required = false) String materialNo,
            @RequestParam(required = false) String materialName,
            @RequestParam(required = false) String spec,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(reworkOrderService.getPage(reworkNo, workOrderNo, qcTaskNo,
                materialNo, materialName, spec, status, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(reworkOrderService.getDetail(id));
    }

    @SuppressWarnings("unchecked")
    @PostMapping
    public R<Long> create(@RequestBody Map<String, Object> body) {
        BizReworkOrder order = new BizReworkOrder();
        if (body.get("qcTaskId") != null) order.setQcTaskId(Long.valueOf(body.get("qcTaskId").toString()));
        if (body.get("qcDisposalId") != null) order.setQcDisposalId(Long.valueOf(body.get("qcDisposalId").toString()));
        order.setWorkOrderId(Long.valueOf(body.get("workOrderId").toString()));
        if (body.get("processTaskId") != null) order.setProcessTaskId(Long.valueOf(body.get("processTaskId").toString()));
        if (body.get("productId") != null) order.setProductId(Long.valueOf(body.get("productId").toString()));
        if (body.get("materialId") != null) order.setMaterialId(Long.valueOf(body.get("materialId").toString()));
        order.setQuantity(new java.math.BigDecimal(body.get("quantity").toString()));
        order.setRemark(body.get("remark") != null ? body.get("remark").toString() : null);
        List<Map<String, Object>> steps = (List<Map<String, Object>>) body.get("steps");
        return R.ok(reworkOrderService.createReworkOrder(order, steps));
    }

    @PutMapping("/{id}/complete-step")
    public R<Void> completeStep(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long stepId = Long.valueOf(body.get("stepId").toString());
        reworkOrderService.completeStep(id, stepId);
        return R.ok();
    }

    @PutMapping("/{id}/complete")
    public R<Void> complete(@PathVariable Long id) {
        reworkOrderService.completeOrder(id);
        return R.ok();
    }
}
