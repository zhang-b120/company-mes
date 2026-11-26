package com.aimes.modules.aftersale.controller;

import com.aimes.common.result.PageResult;
import com.aimes.common.result.R;
import com.aimes.modules.aftersale.entity.BizServiceOrder;
import com.aimes.modules.aftersale.entity.BizServiceOrderPart;
import com.aimes.modules.aftersale.service.BizServiceOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/aftersale/service-orders")
@RequiredArgsConstructor
public class BizServiceOrderController {

    private final BizServiceOrderService service;

    @GetMapping("/page")
    public R<PageResult<Map<String, Object>>> page(
            @RequestParam(required = false) String serviceNo,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) Integer inWarranty,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(service.getPage(serviceNo, customerName, productName, inWarranty, status, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(service.getDetail(id));
    }

    @PostMapping
    public R<Long> create(@RequestBody BizServiceOrder order) {
        return R.ok(service.create(order));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizServiceOrder order) {
        service.update(id, order);
        return R.ok();
    }

    @PutMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) {
        service.cancel(id);
        return R.ok();
    }

    @PutMapping("/{id}/complete")
    public R<Void> complete(@PathVariable Long id) {
        service.complete(id);
        return R.ok();
    }

    // ====== 配件 ======
    @GetMapping("/{id}/parts")
    public R<List<Map<String, Object>>> parts(@PathVariable Long id) {
        return R.ok(service.listParts(id));
    }

    @PostMapping("/{id}/parts")
    public R<Long> addPart(@PathVariable Long id, @RequestBody BizServiceOrderPart part) {
        return R.ok(service.addPart(id, part));
    }

    @PutMapping("/parts/{partId}")
    public R<Void> updatePart(@PathVariable Long partId, @RequestBody BizServiceOrderPart part) {
        service.updatePart(partId, part);
        return R.ok();
    }

    @DeleteMapping("/parts/{partId}")
    public R<Void> deletePart(@PathVariable Long partId) {
        service.deletePart(partId);
        return R.ok();
    }

    /** 配件批量出库 (创建 service_out 库存任务) */
    @PostMapping("/{id}/issue-parts")
    public R<Integer> issueParts(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object w = body.get("warehouseId");
        Long warehouseId = w != null ? Long.valueOf(w.toString()) : null;
        return R.ok(service.issueParts(id, warehouseId));
    }

    /** 给配件关联生产工单 (传 null 解除关联) */
    @PutMapping("/parts/{partId}/link-work-order")
    public R<Void> linkWorkOrder(@PathVariable Long partId, @RequestBody Map<String, Object> body) {
        Object w = body.get("workOrderId");
        Long workOrderId = w != null && !w.toString().isEmpty() ? Long.valueOf(w.toString()) : null;
        service.linkWorkOrder(partId, workOrderId);
        return R.ok();
    }

    /** 给配件新建生产工单 (一键创建并关联) */
    @PostMapping("/parts/{partId}/create-work-order")
    public R<Long> createWorkOrder(@PathVariable Long partId, @RequestBody(required = false) Map<String, Object> body) {
        return R.ok(service.createWorkOrderForPart(partId, body));
    }

    /** 该售后单的关联任务: 库存出库任务 + 生产工单 */
    @GetMapping("/{id}/related-tasks")
    public R<Map<String, Object>> relatedTasks(@PathVariable Long id) {
        return R.ok(service.getRelatedTasks(id));
    }

    /** 客户售后统计 */
    @GetMapping("/statistics/customer")
    public R<List<Map<String, Object>>> customerStats(@RequestParam(required = false) String customerName) {
        return R.ok(service.getCustomerStatistics(customerName));
    }
}
