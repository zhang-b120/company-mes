package com.aimes.modules.purchase.controller;

import com.aimes.common.result.PageResult;
import com.aimes.common.result.R;
import com.aimes.modules.purchase.entity.BizPurchaseOrder;
import com.aimes.modules.purchase.entity.BizPurchaseOrderItem;
import com.aimes.modules.purchase.entity.BizPurchaseRequest;
import com.aimes.modules.purchase.service.PurchaseRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/purchase/requests")
@RequiredArgsConstructor
public class PurchaseController {

    private final PurchaseRequestService requestService;

    /**
     * 分页查询采购申请（支持复杂查询）
     */
    @GetMapping("/page")
    public R<PageResult<Map<String, Object>>> getPage(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String requestNo,
            @RequestParam(required = false) String materialName,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) Long suggestedSupplierId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expectedDateStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expectedDateEnd,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createTimeStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createTimeEnd,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(requestService.getPage(status, requestNo, materialName, sourceType,
                suggestedSupplierId, expectedDateStart, expectedDateEnd,
                createTimeStart != null ? createTimeStart.atStartOfDay() : null,
                createTimeEnd != null ? createTimeEnd.atTime(23, 59, 59) : null,
                pageNum, pageSize));
    }

    /**
     * 查询单条采购申请详情
     */
    @GetMapping("/{id}")
    public R<Map<String, Object>> getDetail(@PathVariable Long id) {
        return R.ok(requestService.getDetail(id));
    }

    /**
     * 创建采购申请
     */
    @PostMapping
    public R<Void> create(@RequestBody BizPurchaseRequest request) {
        requestService.create(request);
        return R.ok();
    }

    /**
     * 审批通过
     */
    @PutMapping("/{id}/approve")
    public R<Void> approve(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long approvedBy = body.get("approvedBy") != null ? Long.valueOf(body.get("approvedBy").toString()) : null;
        String approveRemark = body.get("approveRemark") != null ? body.get("approveRemark").toString() : null;
        requestService.approve(id, approvedBy, approveRemark);
        return R.ok();
    }

    /**
     * 审批驳回
     */
    @PutMapping("/{id}/reject")
    public R<Void> reject(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long approvedBy = body.get("approvedBy") != null ? Long.valueOf(body.get("approvedBy").toString()) : null;
        String approveRemark = body.get("approveRemark") != null ? body.get("approveRemark").toString() : null;
        requestService.reject(id, approvedBy, approveRemark);
        return R.ok();
    }

    /**
     * 编辑草稿采购申请 (仅 draft 状态可改)
     */
    @PutMapping("/{id}")
    public R<Void> updateDraft(@PathVariable Long id, @RequestBody BizPurchaseRequest patch) {
        requestService.updateDraft(id, patch);
        return R.ok();
    }

    /**
     * 提交草稿: draft → pending
     */
    @PutMapping("/{id}/submit")
    public R<Void> submitDraft(@PathVariable Long id) {
        requestService.submitDraft(id);
        return R.ok();
    }

    /**
     * 删除草稿状态采购申请（物理删除）
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        requestService.delete(id);
        return R.ok();
    }

    /**
     * 下发采购订单（功能3）
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/issue-to-order")
    public R<Void> issueToOrder(@RequestBody Map<String, Object> body) {
        List<Long> requestIds = new ArrayList<>();
        if (body.get("requestIds") != null) {
            for (Object id : (List<?>) body.get("requestIds")) {
                requestIds.add(Long.valueOf(id.toString()));
            }
        }

        BizPurchaseOrder order = new BizPurchaseOrder();
        if (body.get("poNo") != null && !body.get("poNo").toString().trim().isEmpty()) order.setPoNo(body.get("poNo").toString().trim());
        if (body.get("supplierId") != null) order.setSupplierId(Long.valueOf(body.get("supplierId").toString()));
        if (body.get("contactName") != null) order.setContactName(body.get("contactName").toString());
        if (body.get("deliveryDate") != null) order.setDeliveryDate(LocalDate.parse(body.get("deliveryDate").toString()));
        if (body.get("paymentTerms") != null) order.setPaymentTerms(body.get("paymentTerms").toString());
        if (body.get("remark") != null) order.setRemark(body.get("remark").toString());

        List<BizPurchaseOrderItem> items = new ArrayList<>();
        if (body.get("items") != null) {
            for (Map<String, Object> m : (List<Map<String, Object>>) body.get("items")) {
                BizPurchaseOrderItem item = new BizPurchaseOrderItem();
                if (m.get("materialId") != null) item.setMaterialId(Long.valueOf(m.get("materialId").toString()));
                if (m.get("quantity") != null) item.setQuantity(new BigDecimal(m.get("quantity").toString()));
                if (m.get("unitPrice") != null) item.setUnitPrice(new BigDecimal(m.get("unitPrice").toString()));
                if (m.get("requestId") != null) item.setRequestId(Long.valueOf(m.get("requestId").toString()));
                if (m.get("remark") != null) item.setRemark(m.get("remark").toString());
                items.add(item);
            }
        }

        requestService.issueToOrder(requestIds, order, items);
        return R.ok();
    }
}
