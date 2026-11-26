package com.aimes.modules.eam.controller;

import com.aimes.common.result.PageResult;
import com.aimes.common.result.R;
import com.aimes.modules.eam.entity.EamRepairManagement;
import com.aimes.modules.eam.entity.EamRepairRequest;
import com.aimes.modules.eam.service.EamRepairService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/eam/repair")
@RequiredArgsConstructor
public class EamRepairController {

    private final EamRepairService service;

    // ========== 报修申请 F9 ==========

    @GetMapping("/requests/page")
    public R<PageResult<EamRepairRequest>> requestPage(@RequestParam(required = false) String requestNo,
                                                         @RequestParam(required = false) String status,
                                                         @RequestParam(required = false) Long equipmentId,
                                                         @RequestParam(defaultValue = "1") int pageNum,
                                                         @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(service.requestPage(requestNo, status, equipmentId, pageNum, pageSize));
    }

    @GetMapping("/requests/{id}")
    public R<EamRepairRequest> requestDetail(@PathVariable Long id) { return R.ok(service.requestDetail(id)); }

    @PostMapping("/requests")
    public R<Void> createRequest(@RequestBody EamRepairRequest request) { service.createRequest(request); return R.ok(); }

    @PostMapping("/requests/{id}/accept")
    public R<Void> acceptRequest(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long acceptorId = body.get("acceptorId") != null ? Long.valueOf(body.get("acceptorId").toString()) : null;
        service.acceptRequest(id, acceptorId, (String) body.get("acceptorName"));
        return R.ok();
    }

    @PostMapping("/requests/{id}/submit")
    public R<Map<String, Object>> submitRequest(@PathVariable Long id) {
        Long orderId = service.submitRequest(id);
        return R.ok(Map.of("workOrderId", orderId));
    }

    @PostMapping("/requests/{id}/cancel")
    public R<Void> cancelRequest(@PathVariable Long id) { service.cancelRequest(id); return R.ok(); }

    @DeleteMapping("/requests/{id}")
    public R<Void> deleteRequest(@PathVariable Long id) { service.deleteRequest(id); return R.ok(); }

    @GetMapping("/requests/{id}/orders")
    public R<List<EamRepairManagement>> relatedOrders(@PathVariable Long id) {
        return R.ok(service.getRelatedOrders(id));
    }

    // ========== 维修工单 F10 ==========

    @GetMapping("/orders/page")
    public R<PageResult<EamRepairManagement>> orderPage(@RequestParam(required = false) String workOrderNo,
                                                         @RequestParam(required = false) String status,
                                                         @RequestParam(required = false) Long equipmentId,
                                                         @RequestParam(required = false) Long assigneeId,
                                                         @RequestParam(required = false) String priority,
                                                         @RequestParam(defaultValue = "1") int pageNum,
                                                         @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(service.orderPage(workOrderNo, status, equipmentId, assigneeId, priority, pageNum, pageSize));
    }

    @GetMapping("/orders/{id}")
    public R<EamRepairManagement> orderDetail(@PathVariable Long id) { return R.ok(service.orderDetail(id)); }

    @PostMapping("/orders")
    public R<Void> createOrder(@RequestBody EamRepairManagement order) { service.createOrder(order); return R.ok(); }

    @PutMapping("/orders/{id}")
    public R<Void> updateOrder(@PathVariable Long id, @RequestBody EamRepairManagement order) { service.updateOrder(id, order); return R.ok(); }

    @PostMapping("/orders/{id}/start")
    public R<Void> startOrder(@PathVariable Long id) { service.startOrder(id); return R.ok(); }

    @PostMapping("/orders/{id}/pause")
    public R<Void> pauseOrder(@PathVariable Long id) { service.pauseOrder(id); return R.ok(); }

    @PostMapping("/orders/{id}/resume")
    public R<Void> resumeOrder(@PathVariable Long id) { service.resumeOrder(id); return R.ok(); }

    @PostMapping("/orders/{id}/complete")
    public R<Void> completeOrder(@PathVariable Long id, @RequestBody EamRepairManagement payload) {
        service.completeOrder(id, payload); return R.ok();
    }

    @PostMapping("/orders/{id}/cancel")
    public R<Void> cancelOrder(@PathVariable Long id) { service.cancelOrder(id); return R.ok(); }

    @PostMapping("/orders/batch-start")
    public R<Void> batchStart(@RequestBody Map<String, List<Long>> body) {
        service.batchStart(body.get("ids")); return R.ok();
    }

    @PostMapping("/orders/batch-pause")
    public R<Void> batchPause(@RequestBody Map<String, List<Long>> body) {
        service.batchPause(body.get("ids")); return R.ok();
    }
}
