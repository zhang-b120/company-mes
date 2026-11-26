package com.aimes.modules.outsource.controller;

import com.aimes.common.annotation.DataScope;
import com.aimes.common.result.PageResult;
import com.aimes.common.result.R;
import com.aimes.modules.outsource.entity.BizOutsourceDelivery;
import com.aimes.modules.outsource.entity.BizOutsourceDeliveryItem;
import com.aimes.modules.outsource.entity.BizOutsourceOrder;
import com.aimes.modules.outsource.entity.BizOutsourceReceipt;
import com.aimes.modules.outsource.service.OutsourceOrderService;
import com.aimes.common.utils.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/outsource/orders")
@RequiredArgsConstructor
public class OutsourceOrderController {

    private final OutsourceOrderService orderService;

    /**
     * 分页查询外协订单
     */
    @DataScope(mapperClass = "com.aimes.modules.outsource.mapper.BizOutsourceOrderMapper")
    @GetMapping("/page")
    public R<PageResult<Map<String, Object>>> getPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String workOrderNo,
            @RequestParam(required = false) String stepName,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(orderService.getPage(keyword, status, supplierId, workOrderNo, stepName, productId, productName, startDate, endDate, pageNum, pageSize));
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
    public R<Map<String, Object>> getDetailByOrderNo(@RequestParam String orderNo) {
        return R.ok(orderService.getDetailByOrderNo(orderNo));
    }

    /**
     * 创建外协订单
     */
    @SuppressWarnings("unchecked")
    @PostMapping
    public R<Void> create(@RequestBody Map<String, Object> body) {
        BizOutsourceOrder order = new BizOutsourceOrder();
        if (body.get("supplierId") != null) order.setSupplierId(Long.valueOf(body.get("supplierId").toString()));
        if (body.get("productId") != null) order.setProductId(Long.valueOf(body.get("productId").toString()));
        if (body.get("stepName") != null) order.setStepName(body.get("stepName").toString());
        if (body.get("totalQty") != null) order.setTotalQty(new BigDecimal(body.get("totalQty").toString()));
        if (body.get("unitPrice") != null) order.setUnitPrice(new BigDecimal(body.get("unitPrice").toString()));
        if (body.get("taxRate") != null) order.setTaxRate(new BigDecimal(body.get("taxRate").toString()));
        if (body.get("deliveryQty") != null) order.setDeliveryQty(new BigDecimal(body.get("deliveryQty").toString()));
        if (body.get("deliveryDate") != null) order.setDeliveryDate(LocalDate.parse(body.get("deliveryDate").toString()));
        if (body.get("workOrderId") != null) order.setWorkOrderId(Long.valueOf(body.get("workOrderId").toString()));
        if (body.get("taskId") != null) order.setTaskId(Long.valueOf(body.get("taskId").toString()));
        if (body.get("requestId") != null) order.setRequestId(Long.valueOf(body.get("requestId").toString()));
        if (body.get("virtualMaterialId") != null) order.setVirtualMaterialId(Long.valueOf(body.get("virtualMaterialId").toString()));
        if (body.get("processRequirement") != null) order.setProcessRequirement(body.get("processRequirement").toString());
        if (body.get("drawingUrls") != null) order.setDrawingUrls(body.get("drawingUrls").toString());
        if (body.get("remark") != null) order.setRemark(body.get("remark").toString());

        List<Long> requestIds = null;
        if (body.get("requestIds") != null) {
            List<Object> ids = (List<Object>) body.get("requestIds");
            requestIds = new ArrayList<>();
            for (Object idObj : ids) {
                requestIds.add(Long.valueOf(idObj.toString()));
            }
        }

        orderService.create(order, requestIds);
        return R.ok();
    }

    /**
     * 编辑草稿状态的加工单
     */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        BizOutsourceOrder updates = new BizOutsourceOrder();
        if (body.get("supplierId") != null) updates.setSupplierId(Long.valueOf(body.get("supplierId").toString()));
        if (body.get("unitPrice") != null) updates.setUnitPrice(new BigDecimal(body.get("unitPrice").toString()));
        if (body.get("taxRate") != null) updates.setTaxRate(new BigDecimal(body.get("taxRate").toString()));
        if (body.get("deliveryDate") != null) updates.setDeliveryDate(LocalDate.parse(body.get("deliveryDate").toString()));
        if (body.get("processRequirement") != null) updates.setProcessRequirement(body.get("processRequirement").toString());
        if (body.get("drawingUrls") != null) updates.setDrawingUrls(body.get("drawingUrls").toString());
        if (body.get("remark") != null) updates.setRemark(body.get("remark").toString());
        orderService.updateDraft(id, updates);
        return R.ok();
    }

    /**
     * 确认外协订单（兼容旧接口）
     */
    @PutMapping("/{id}/confirm")
    public R<Void> confirm(@PathVariable Long id) {
        orderService.confirm(id);
        return R.ok();
    }

    /**
     * 提交审批：draft → pending_approval
     */
    @PutMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        orderService.submit(id);
        return R.ok();
    }

    /**
     * 审批通过：pending_approval → confirmed
     */
    @PutMapping("/{id}/approve")
    public R<Void> approve(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        Long approvedBy = SecurityUtil.getCurrentUserId();
        String remark = body != null && body.get("remark") != null ? body.get("remark").toString() : null;
        orderService.approve(id, approvedBy, remark);
        return R.ok();
    }

    /**
     * 驳回：pending_approval → draft
     */
    @PutMapping("/{id}/reject")
    public R<Void> reject(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        Long approvedBy = SecurityUtil.getCurrentUserId();
        String remark = body != null && body.get("remark") != null ? body.get("remark").toString() : null;
        orderService.reject(id, approvedBy, remark);
        return R.ok();
    }

    /**
     * 创建发货单
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/{id}/delivery")
    public R<Void> createDelivery(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        BizOutsourceDelivery delivery = new BizOutsourceDelivery();
        if (body.get("deliveryType") != null) delivery.setDeliveryType(body.get("deliveryType").toString());
        if (body.get("deliveryQty") != null) delivery.setDeliveryQty(new BigDecimal(body.get("deliveryQty").toString()));
        if (body.get("remark") != null) delivery.setRemark(body.get("remark").toString());

        List<BizOutsourceDeliveryItem> items = null;
        if (body.get("items") != null) {
            List<Map<String, Object>> itemMaps = (List<Map<String, Object>>) body.get("items");
            items = new ArrayList<>();
            for (Map<String, Object> m : itemMaps) {
                BizOutsourceDeliveryItem item = new BizOutsourceDeliveryItem();
                if (m.get("materialId") != null) item.setMaterialId(Long.valueOf(m.get("materialId").toString()));
                if (m.get("shouldQty") != null) item.setShouldQty(new BigDecimal(m.get("shouldQty").toString()));
                if (m.get("actualQty") != null) item.setActualQty(new BigDecimal(m.get("actualQty").toString()));
                if (m.get("batchNo") != null) item.setBatchNo(m.get("batchNo").toString());
                if (m.get("warehouseId") != null) item.setWarehouseId(Long.valueOf(m.get("warehouseId").toString()));
                if (m.get("remark") != null) item.setRemark(m.get("remark").toString());
                items.add(item);
            }
        }

        orderService.createDelivery(id, delivery, items);
        return R.ok();
    }

    /**
     * 创建收货单
     */
    @PostMapping("/{id}/receipt")
    public R<Void> createReceipt(@PathVariable Long id, @RequestBody BizOutsourceReceipt receipt) {
        orderService.createReceipt(id, receipt);
        return R.ok();
    }

    /**
     * 生成外协收料单（新流程）
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
     * 取消外协订单（回收已出库的虚拟半成品库存）
     */
    @PutMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) {
        orderService.cancelOrder(id);
        return R.ok();
    }
}
