package com.aimes.modules.inventory.controller;

import com.aimes.common.result.PageResult;
import com.aimes.common.result.R;
import com.aimes.modules.inventory.service.BizInvTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory/tasks")
@RequiredArgsConstructor
public class BizInvTaskController {

    private final BizInvTaskService taskService;

    @GetMapping("/page")
    public R<PageResult<Map<String, Object>>> getPage(
            @RequestParam(required = false) String taskNo,
            @RequestParam(required = false) String relatedOrderNo,
            @RequestParam(required = false) String invType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean includeCompleted,
            @RequestParam(required = false) String dateStart,
            @RequestParam(required = false) String dateEnd,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(taskService.getPage(taskNo, relatedOrderNo, invType, status, warehouseId,
                keyword, includeCompleted, dateStart, dateEnd, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> getDetail(@PathVariable Long id) {
        return R.ok(taskService.getDetail(id));
    }

    /** 根据来源类型和来源ID查询入库任务 */
    @GetMapping("/by-source")
    public R<Map<String, Object>> getBySource(@RequestParam String sourceType, @RequestParam Long sourceId) {
        return R.ok(taskService.getBySource(sourceType, sourceId));
    }

    @PutMapping("/{id}/warehouse")
    public R<Void> updateWarehouse(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long warehouseId = body.get("warehouseId") != null ? Long.valueOf(body.get("warehouseId").toString()) : null;
        Long locationId = body.get("locationId") != null ? Long.valueOf(body.get("locationId").toString()) : null;
        taskService.updateWarehouseLocation(id, warehouseId, locationId);
        return R.ok();
    }

    /** 工单列表"生成库存任务"按钮：直接创建 production_in 任务，仓库可由前端指定 */
    @PostMapping("/from-work-order/{woId}")
    public R<Map<String, Object>> createFromWorkOrder(@PathVariable Long woId, @RequestBody Map<String, Object> body) {
        Long warehouseId = body.get("warehouseId") != null ? Long.valueOf(body.get("warehouseId").toString()) : null;
        return R.ok(taskService.createOrGetForWorkOrder(woId, warehouseId));
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/{id}/process")
    public R<Void> process(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        String remark = body.get("remark") != null ? body.get("remark").toString() : null;
        String historyNo = body.get("historyNo") != null ? body.get("historyNo").toString().trim() : null;
        Long processWarehouseId = body.get("warehouseId") != null ? Long.valueOf(body.get("warehouseId").toString()) : null;
        Long processLocationId = body.get("locationId") != null ? Long.valueOf(body.get("locationId").toString()) : null;
        taskService.process(id, processWarehouseId, processLocationId, items, remark, historyNo);
        return R.ok();
    }

    /**
     * 采购入库任务退回（仅 pending 状态，退回原因必填）
     */
    @PutMapping("/{id}/return")
    public R<Void> returnTask(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String returnReason = body.get("returnReason") != null ? body.get("returnReason").toString() : null;
        taskService.returnTask(id, returnReason);
        return R.ok();
    }

    /** 查询采购订单关联的所有库存任务 */
    @GetMapping("/by-purchase-order/{orderId}")
    public R<List<Map<String, Object>>> getByPurchaseOrder(@PathVariable Long orderId) {
        return R.ok(taskService.getByPurchaseOrder(orderId));
    }

    /** 查询工单关联的所有库存任务（领料、退料、成品入库等） */
    @GetMapping("/by-work-order/{woId}")
    public R<List<Map<String, Object>>> getByWorkOrder(@PathVariable Long woId) {
        return R.ok(taskService.getByWorkOrder(woId));
    }
}
