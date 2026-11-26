package com.aimes.modules.order.controller;

import com.aimes.common.result.PageResult;
import com.aimes.common.result.R;
import com.aimes.modules.order.service.BizReturnOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/order/return-orders")
@RequiredArgsConstructor
public class BizReturnOrderController {

    private final BizReturnOrderService returnOrderService;

    @GetMapping("/page")
    public R<PageResult<Map<String, Object>>> page(
            @RequestParam(required = false) String returnNo,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long orderId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(returnOrderService.getPage(returnNo, customerName, orderNo, status, orderId, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(returnOrderService.getDetail(id));
    }

    @SuppressWarnings("unchecked")
    @PostMapping
    public R<Void> create(@RequestBody Map<String, Object> body) {
        Long orderId = Long.valueOf(body.get("orderId").toString());
        String returnReason = body.get("returnReason") != null ? body.get("returnReason").toString() : null;
        LocalDate returnDate = body.get("returnDate") != null
                ? LocalDate.parse(body.get("returnDate").toString().substring(0, 10)) : null;
        String remark = body.get("remark") != null ? body.get("remark").toString() : null;
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        returnOrderService.create(orderId, returnReason, returnDate, remark, items);
        return R.ok();
    }

    @PutMapping("/{id}/approve")
    public R<Void> approve(@PathVariable Long id) {
        returnOrderService.approve(id);
        return R.ok();
    }

    @PutMapping("/{id}/reject")
    public R<Void> reject(@PathVariable Long id, @RequestBody Map<String, String> body) {
        returnOrderService.reject(id, body.getOrDefault("reason", ""));
        return R.ok();
    }

    @SuppressWarnings("unchecked")
    @PutMapping("/{id}/receive")
    public R<Void> receive(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        List<Map<String, Object>> receiveData = (List<Map<String, Object>>) body.get("items");
        returnOrderService.receive(id, receiveData);
        return R.ok();
    }

    @PutMapping("/{id}/complete")
    public R<Void> complete(@PathVariable Long id) {
        returnOrderService.complete(id);
        return R.ok();
    }
}
