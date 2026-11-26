package com.aimes.modules.purchase.controller;

import com.aimes.common.annotation.DataScope;
import com.aimes.common.result.PageResult;
import com.aimes.common.result.R;
import com.aimes.modules.purchase.entity.BizPurchaseOrder;
import com.aimes.modules.purchase.entity.BizPurchaseOrderItem;
import com.aimes.modules.purchase.entity.BizPurchaseReceipt;
import com.aimes.modules.purchase.entity.BizPurchaseReturnItem;
import com.aimes.modules.purchase.service.PurchaseOrderService;
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
@RequestMapping("/api/purchase/orders")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService orderService;
    private final PurchaseReturnService returnService;

    /**
     * 分页查询采购订单（支持复杂查询）
     */
    @DataScope(mapperClass = "com.aimes.modules.purchase.mapper.BizPurchaseOrderMapper")
    @GetMapping("/page")
    public R<PageResult<Map<String, Object>>> getPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String poNo,
            @RequestParam(required = false) String paymentTerms,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deliveryDateStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deliveryDateEnd,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createTimeStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createTimeEnd,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(orderService.getPage(keyword, status, supplierId, poNo, paymentTerms,
                deliveryDateStart, deliveryDateEnd,
                createTimeStart != null ? createTimeStart.atStartOfDay() : null,
                createTimeEnd != null ? createTimeEnd.atTime(23, 59, 59) : null,
                pageNum, pageSize));
    }

    /**
     * 订单详情
     */
    @GetMapping("/{id}")
    public R<Map<String, Object>> getDetail(@PathVariable Long id) {
        return R.ok(orderService.getDetail(id));
    }

    /**
     * 通过订单编号查询详情（供关联单据跳转使用）
     */
    @GetMapping("/by-no")
    public R<Map<String, Object>> getDetailByPoNo(@RequestParam String poNo) {
        return R.ok(orderService.getDetailByPoNo(poNo));
    }

    /**
     * 历史采购价格查询
     */
    @GetMapping("/price-history")
    public R<List<Map<String, Object>>> priceHistory(@RequestParam Long materialId) {
        return R.ok(orderService.getPriceHistory(materialId));
    }

    /**
     * 创建采购订单
     */
    @SuppressWarnings("unchecked")
    @PostMapping
    public R<Void> create(@RequestBody Map<String, Object> body) {
        BizPurchaseOrder order = new BizPurchaseOrder();
        if (body.get("poNo") != null && !body.get("poNo").toString().trim().isEmpty()) order.setPoNo(body.get("poNo").toString().trim());
        if (body.get("supplierId") != null) order.setSupplierId(Long.valueOf(body.get("supplierId").toString()));
        if (body.get("contactName") != null) order.setContactName(body.get("contactName").toString());
        if (body.get("deliveryDate") != null) order.setDeliveryDate(LocalDate.parse(body.get("deliveryDate").toString()));
        if (body.get("paymentTerms") != null) order.setPaymentTerms(body.get("paymentTerms").toString());
        if (body.get("remark") != null) order.setRemark(body.get("remark").toString());
        if (body.get("attachmentUrls") != null) order.setAttachmentUrls((List<Object>) body.get("attachmentUrls"));

        List<BizPurchaseOrderItem> items = null;
        if (body.get("items") != null) {
            List<Map<String, Object>> itemMaps = (List<Map<String, Object>>) body.get("items");
            items = new ArrayList<>();
            for (Map<String, Object> m : itemMaps) {
                BizPurchaseOrderItem item = new BizPurchaseOrderItem();
                if (m.get("materialId") != null) item.setMaterialId(Long.valueOf(m.get("materialId").toString()));
                if (m.get("quantity") != null) item.setQuantity(new BigDecimal(m.get("quantity").toString()));
                if (m.get("unitPrice") != null) item.setUnitPrice(new BigDecimal(m.get("unitPrice").toString()));
                if (m.get("requestId") != null) item.setRequestId(Long.valueOf(m.get("requestId").toString()));
                if (m.get("remark") != null) item.setRemark(m.get("remark").toString());
                items.add(item);
            }
        }

        orderService.create(order, items);
        return R.ok();
    }

    /**
     * 编辑采购订单（仅草稿状态）
     */
    @SuppressWarnings("unchecked")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        BizPurchaseOrder order = new BizPurchaseOrder();
        if (body.get("supplierId") != null) order.setSupplierId(Long.valueOf(body.get("supplierId").toString()));
        if (body.get("contactName") != null) order.setContactName(body.get("contactName").toString());
        if (body.get("deliveryDate") != null) order.setDeliveryDate(LocalDate.parse(body.get("deliveryDate").toString()));
        if (body.get("paymentTerms") != null) order.setPaymentTerms(body.get("paymentTerms").toString());
        order.setRemark(body.get("remark") != null ? body.get("remark").toString() : null);
        if (body.get("attachmentUrls") != null) order.setAttachmentUrls((List<Object>) body.get("attachmentUrls"));

        List<BizPurchaseOrderItem> items = null;
        if (body.get("items") != null) {
            List<Map<String, Object>> itemMaps = (List<Map<String, Object>>) body.get("items");
            items = new ArrayList<>();
            for (Map<String, Object> m : itemMaps) {
                BizPurchaseOrderItem item = new BizPurchaseOrderItem();
                if (m.get("materialId") != null) item.setMaterialId(Long.valueOf(m.get("materialId").toString()));
                if (m.get("quantity") != null) item.setQuantity(new BigDecimal(m.get("quantity").toString()));
                if (m.get("unitPrice") != null) item.setUnitPrice(new BigDecimal(m.get("unitPrice").toString()));
                if (m.get("remark") != null) item.setRemark(m.get("remark").toString());
                items.add(item);
            }
        }
        orderService.updateOrder(id, order, items);
        return R.ok();
    }

    /**
     * 添加订单明细
     */
    @PostMapping("/{id}/items")
    public R<Void> addItem(@PathVariable Long id, @RequestBody BizPurchaseOrderItem item) {
        orderService.addItem(id, item);
        return R.ok();
    }

    /**
     * 提交审批
     */
    @PutMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        orderService.submit(id);
        return R.ok();
    }

    /**
     * 审批通过
     */
    @PutMapping("/{id}/approve")
    public R<Void> approve(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        String remark = body != null && body.get("remark") != null ? body.get("remark").toString() : null;
        orderService.approve(id, remark);
        return R.ok();
    }

    /**
     * 审批驳回
     */
    @PutMapping("/{id}/reject")
    public R<Void> reject(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        String remark = body != null && body.get("remark") != null ? body.get("remark").toString() : null;
        orderService.reject(id, remark);
        return R.ok();
    }

    /**
     * 创建收料单（单条，兼容旧接口）
     */
    @PostMapping("/{id}/receipt")
    public R<Void> createReceipt(@PathVariable Long id, @RequestBody BizPurchaseReceipt receipt) {
        orderService.createReceipt(id, receipt);
        return R.ok();
    }

    /**
     * 批量收料（多物料同时入库，生成一条多物料库存任务）
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/{id}/batch-receipt")
    public R<Void> createBatchReceipt(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        if (items == null || items.isEmpty()) return R.fail("收料明细不能为空");
        orderService.createBatchReceipts(id, items);
        return R.ok();
    }

    /**
     * 从采购订单批量创建入库申请（一个PO只能生成一条有效入库申请）
     */
    @PostMapping("/{id}/inv-request")
    public R<Void> createInvRequest(@PathVariable Long id) {
        orderService.createInvRequestFromPO(id);
        return R.ok();
    }

    /**
     * 自动生成库存任务（直接，不经过入库申请审批流）
     * 按剩余数量 + 物料默认仓库自动创建收料单和库存任务
     */
    @PostMapping("/{id}/auto-task")
    public R<Void> autoGenerateInvTask(@PathVariable Long id) {
        orderService.autoGenerateInvTask(id);
        return R.ok();
    }

    /**
     * 生成采购收料单（新流程：从采购订单生成收料单）
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/{id}/generate-receipts")
    public R<Void> generateReceipts(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        if (items == null || items.isEmpty()) return R.fail("收料明细不能为空");
        orderService.generateReceipts(id, items);
        return R.ok();
    }

    /**
     * 作废采购订单（仅已驳回状态可作废）
     */
    @PutMapping("/{id}/void")
    public R<Void> voidOrder(@PathVariable Long id) {
        orderService.voidOrder(id);
        return R.ok();
    }

    /**
     * 手动办结采购订单（仅当库存任务已退回时可用）
     */
    @PutMapping("/{id}/close")
    public R<Void> closeOrder(@PathVariable Long id) {
        orderService.closeOrder(id);
        return R.ok();
    }

    /**
     * 由办结的采购订单生成采购退货单（直接进入待审批状态）
     */
    @PostMapping("/{id}/return")
    public R<Long> createReturn(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        String returnReason = body != null && body.get("returnReason") != null ? body.get("returnReason").toString() : null;
        String remark = body != null && body.get("remark") != null ? body.get("remark").toString() : null;
        List<BizPurchaseReturnItem> items = null;
        if (body != null && body.get("items") != null) {
            List<Map<String, Object>> rawItems = (List<Map<String, Object>>) body.get("items");
            items = rawItems.stream().map(m -> {
                BizPurchaseReturnItem item = new BizPurchaseReturnItem();
                if (m.get("materialId") != null) item.setMaterialId(Long.valueOf(m.get("materialId").toString()));
                if (m.get("quantity") != null) item.setQuantity(new java.math.BigDecimal(m.get("quantity").toString()));
                if (m.get("unitPrice") != null) item.setUnitPrice(new java.math.BigDecimal(m.get("unitPrice").toString()));
                if (m.get("remark") != null) item.setRemark(m.get("remark").toString());
                return item;
            }).collect(java.util.stream.Collectors.toList());
        }
        return R.ok(returnService.createFromPurchaseOrder(id, returnReason, remark, items));
    }
}
