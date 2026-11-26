package com.aimes.modules.purchase.controller;

import com.aimes.common.result.PageResult;
import com.aimes.common.result.R;
import com.aimes.modules.purchase.entity.BizPurchaseReturn;
import com.aimes.modules.purchase.entity.BizPurchaseReturnItem;
import com.aimes.modules.purchase.service.PurchaseReturnService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/purchase/returns")
@RequiredArgsConstructor
public class PurchaseReturnController {

    private final PurchaseReturnService returnService;

    /**
     * 分页查询采购退货单
     */
    @GetMapping("/page")
    public R<PageResult<Map<String, Object>>> getPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String returnNo,
            @RequestParam(required = false) Long purchaseOrderId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createTimeStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createTimeEnd,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(returnService.getPage(keyword, status, supplierId, returnNo, purchaseOrderId,
                createTimeStart != null ? createTimeStart.atStartOfDay() : null,
                createTimeEnd != null ? createTimeEnd.atTime(23, 59, 59) : null,
                pageNum, pageSize));
    }

    /**
     * 详情
     */
    @GetMapping("/{id}")
    public R<Map<String, Object>> getDetail(@PathVariable Long id) {
        return R.ok(returnService.getDetail(id));
    }

    /**
     * 新建草稿退货单
     */
    @SuppressWarnings("unchecked")
    @PostMapping
    public R<Void> create(@RequestBody Map<String, Object> body) {
        BizPurchaseReturn ret = new BizPurchaseReturn();
        if (body.get("supplierId") != null) ret.setSupplierId(Long.valueOf(body.get("supplierId").toString()));
        if (body.get("returnReason") != null) ret.setReturnReason(body.get("returnReason").toString());
        if (body.get("remark") != null) ret.setRemark(body.get("remark").toString());
        if (body.get("relatedOrderNo") != null) ret.setRelatedOrderNo(body.get("relatedOrderNo").toString());

        List<BizPurchaseReturnItem> items = parseItems(body);
        returnService.create(ret, items);
        return R.ok();
    }

    /**
     * 获取采购订单明细用于退货预览（前端展示可编辑明细）
     */
    @GetMapping("/preview-from-order/{orderId}")
    public R<List<Map<String, Object>>> previewFromOrder(@PathVariable Long orderId) {
        return R.ok(returnService.getReturnPreview(orderId));
    }

    /**
     * 由采购订单生成退货单（待审批，支持传入编辑后的明细）
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/from-purchase-order/{orderId}")
    public R<Long> createFromPurchaseOrder(@PathVariable Long orderId,
                                           @RequestBody(required = false) Map<String, Object> body) {
        String returnReason = body != null && body.get("returnReason") != null ? body.get("returnReason").toString() : null;
        String remark = body != null && body.get("remark") != null ? body.get("remark").toString() : null;
        List<BizPurchaseReturnItem> items = body != null ? parseItems(body) : null;
        return R.ok(returnService.createFromPurchaseOrder(orderId, returnReason, remark, items));
    }

    /**
     * 编辑（草稿状态）
     */
    @SuppressWarnings("unchecked")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        BizPurchaseReturn ret = new BizPurchaseReturn();
        if (body.get("supplierId") != null) ret.setSupplierId(Long.valueOf(body.get("supplierId").toString()));
        if (body.get("returnReason") != null) ret.setReturnReason(body.get("returnReason").toString());
        ret.setRemark(body.get("remark") != null ? body.get("remark").toString() : null);
        ret.setRelatedOrderNo(body.get("relatedOrderNo") != null ? body.get("relatedOrderNo").toString() : null);

        List<BizPurchaseReturnItem> items = parseItems(body);
        returnService.update(id, ret, items);
        return R.ok();
    }

    /**
     * 提交审批
     */
    @PutMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        returnService.submit(id);
        return R.ok();
    }

    /**
     * 审批通过
     */
    @PutMapping("/{id}/approve")
    public R<Void> approve(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        String remark = body != null && body.get("remark") != null ? body.get("remark").toString() : null;
        returnService.approve(id, remark);
        return R.ok();
    }

    /**
     * 审批驳回
     */
    @PutMapping("/{id}/reject")
    public R<Void> reject(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        String remark = body != null && body.get("remark") != null ? body.get("remark").toString() : null;
        returnService.reject(id, remark);
        return R.ok();
    }

    /**
     * 生成库存任务（出库）
     */
    @PostMapping("/{id}/inv-task")
    public R<Void> generateInvTask(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        Long warehouseId = body != null && body.get("warehouseId") != null
                ? Long.valueOf(body.get("warehouseId").toString()) : null;
        returnService.generateInvTask(id, warehouseId);
        return R.ok();
    }

    /**
     * 批量生成库存任务
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/batch-inv-task")
    public R<Void> batchGenerateInvTask(@RequestBody Map<String, Object> body) {
        List<Long> ids = ((List<Object>) body.get("ids")).stream()
                .map(o -> Long.valueOf(o.toString())).toList();
        Long warehouseId = body.get("warehouseId") != null ? Long.valueOf(body.get("warehouseId").toString()) : null;
        returnService.batchGenerateInvTask(ids, warehouseId);
        return R.ok();
    }

    @SuppressWarnings("unchecked")
    private List<BizPurchaseReturnItem> parseItems(Map<String, Object> body) {
        List<BizPurchaseReturnItem> items = new ArrayList<>();
        if (body.get("items") == null) return items;
        for (Map<String, Object> m : (List<Map<String, Object>>) body.get("items")) {
            BizPurchaseReturnItem item = new BizPurchaseReturnItem();
            if (m.get("materialId") != null) item.setMaterialId(Long.valueOf(m.get("materialId").toString()));
            if (m.get("quantity") != null) item.setQuantity(new BigDecimal(m.get("quantity").toString()));
            if (m.get("unitPrice") != null) item.setUnitPrice(new BigDecimal(m.get("unitPrice").toString()));
            if (m.get("remark") != null) item.setRemark(m.get("remark").toString());
            items.add(item);
        }
        return items;
    }
}
