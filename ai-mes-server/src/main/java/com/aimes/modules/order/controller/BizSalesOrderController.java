package com.aimes.modules.order.controller;

import com.aimes.common.annotation.DataScope;
import com.aimes.common.result.R;
import com.aimes.common.result.PageResult;
import com.aimes.common.utils.ExcelUtil;
import com.aimes.modules.order.entity.BizSalesOrder;
import com.aimes.modules.order.entity.BizSalesOrderItem;
import com.aimes.modules.order.entity.BizShipmentRecord;
import com.aimes.modules.order.entity.BizOrderInvoice;
import com.aimes.modules.order.entity.BizOrderAttachment;
import com.aimes.modules.order.entity.BizOrderFollowup;
import com.aimes.modules.order.entity.BizOrderLog;
import com.aimes.modules.order.service.BizSalesOrderService;
import com.aimes.modules.order.service.BizShipmentRecordService;
import com.aimes.modules.order.service.BizOrderInvoiceService;
import com.aimes.modules.order.service.BizOrderAttachmentService;
import com.aimes.modules.order.service.BizOrderFollowupService;
import com.aimes.modules.order.service.BizOrderLogService;
import com.aimes.common.extfield.BizExtFieldConfig;
import com.aimes.common.extfield.BizExtFieldConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.aimes.modules.inventory.service.BizInvTaskService;
import com.aimes.modules.master.entity.BizMaterial;
import com.aimes.modules.master.mapper.BizMaterialMapper;
import com.aimes.modules.production.service.BizWorkOrderService;
import com.aimes.common.utils.SecurityUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;

@RestController
@RequestMapping("/api/order/sales-orders")
@RequiredArgsConstructor
public class BizSalesOrderController {

    private final BizSalesOrderService salesOrderService;
    private final BizShipmentRecordService shipmentRecordService;
    private final BizOrderInvoiceService orderInvoiceService;
    private final BizOrderAttachmentService orderAttachmentService;
    private final BizInvTaskService invTaskService;
    private final BizMaterialMapper materialMapper;
    private final BizWorkOrderService workOrderService;
    private final BizOrderFollowupService orderFollowupService;
    private final BizOrderLogService orderLogService;
    private final BizExtFieldConfigMapper extFieldConfigMapper;

    @org.springframework.beans.factory.annotation.Value("${file.upload.path:./uploads}")
    private String fileUploadPath;

    @DataScope(mapperClass = "com.aimes.modules.order.mapper.BizSalesOrderMapper")
    @GetMapping("/page")
    public R<PageResult<Map<String, Object>>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long salesmanId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String contactName,
            @RequestParam(required = false) Long createBy,
            @RequestParam(required = false) BigDecimal totalAmountMin,
            @RequestParam(required = false) BigDecimal totalAmountMax,
            @RequestParam(required = false) String deliveryDateRange,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String productCode,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) Boolean overdueOnly,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam Map<String, String> allParams) {
        // 提取 ext_ 前缀的扩展字段查询参数，如 ext_po_number=PO123 (订单头)
        // 提取 extItem_ 前缀的为订单明细行级扩展字段,如 extItem_color=红
        Map<String, String> extFieldFilters = new HashMap<>();
        Map<String, String> itemExtFieldFilters = new HashMap<>();
        allParams.forEach((k, v) -> {
            if (!org.springframework.util.StringUtils.hasText(v)) return;
            if (k.startsWith("extItem_")) itemExtFieldFilters.put(k.substring(8), v);
            else if (k.startsWith("ext_")) extFieldFilters.put(k.substring(4), v);
        });
        return R.ok(salesOrderService.getPageAdvanced(keyword, status, customerId, salesmanId,
                startDate, endDate, orderNo, contactName, createBy, totalAmountMin, totalAmountMax,
                deliveryDateRange, productName, productCode, customerName,
                Boolean.TRUE.equals(overdueOnly),
                extFieldFilters.isEmpty() ? null : extFieldFilters,
                itemExtFieldFilters.isEmpty() ? null : itemExtFieldFilters,
                pageNum, pageSize));
    }

    @GetMapping("/credit-check")
    public R<Map<String, Object>> creditCheck(
            @RequestParam Long customerId,
            @RequestParam BigDecimal amount) {
        return R.ok(salesOrderService.checkCreditLimit(customerId, amount));
    }

    @DataScope(mapperClass = "com.aimes.modules.order.mapper.BizSalesOrderMapper")
    @GetMapping("/statistics")
    public R<Map<String, Object>> statistics() {
        return R.ok(salesOrderService.getStatistics());
    }

    @DataScope(mapperClass = "com.aimes.modules.order.mapper.BizSalesOrderMapper")
    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(salesOrderService.getDetail(id));
    }

    /**
     * 通过订单编号查询详情（供关联单据跳转使用）
     */
    @GetMapping("/by-no")
    public R<Map<String, Object>> getDetailByOrderNo(@RequestParam String orderNo) {
        return R.ok(salesOrderService.getDetailByOrderNo(orderNo));
    }

    @PostMapping
    public R<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        BizSalesOrder order = buildOrderFromMap(body);
        List<BizSalesOrderItem> items = buildItemsFromMap(body);
        salesOrderService.create(order, items);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", order.getId());
        result.put("orderNo", order.getOrderNo());
        return R.ok(result);
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        BizSalesOrder order = buildOrderFromMap(body);
        List<BizSalesOrderItem> items = buildItemsFromMap(body);
        salesOrderService.update(id, order, items);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        salesOrderService.physicalDelete(id);
        return R.ok();
    }

    @PutMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        salesOrderService.submit(id);
        return R.ok();
    }

    @PutMapping("/{id}/approve")
    public R<Void> approve(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String remark = body != null ? body.get("remark") : null;
        salesOrderService.approve(id, remark);
        return R.ok();
    }

    @PutMapping("/{id}/reject")
    public R<Void> reject(@PathVariable Long id, @RequestBody Map<String, String> body) {
        salesOrderService.reject(id, body.get("remark"));
        return R.ok();
    }

    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        salesOrderService.updateStatus(id, body.get("status"));
        return R.ok();
    }

    @PutMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id, @RequestBody Map<String, String> body) {
        // 使用级联取消：自动取消关联工单及库存预留
        salesOrderService.cancelWithCascade(id, body.get("reason"), workOrderService);
        return R.ok();
    }

    @PutMapping("/{id}/void")
    public R<Void> voidOrder(@PathVariable Long id, @RequestBody Map<String, String> body) {
        salesOrderService.voidOrder(id, body != null ? body.get("reason") : null);
        return R.ok();
    }

    @PutMapping("/{id}/complete")
    public R<Void> complete(@PathVariable Long id) {
        salesOrderService.complete(id);
        return R.ok();
    }

    /**
     * 转计划单：confirmed → mrp_planned
     */
    @PutMapping("/{id}/convert-to-plan")
    public R<Void> convertToPlan(@PathVariable Long id) {
        salesOrderService.convertToPlan(id);
        return R.ok();
    }

    /**
     * 直接转生产计划：跳过MRP，由销售订单直接生成生产计划
     */
    @PutMapping("/{id}/convert-to-production-plan")
    public R<Void> convertToProductionPlan(@PathVariable Long id) {
        salesOrderService.convertToProductionPlan(id);
        return R.ok();
    }

    // ==================== 批量操作 ====================

    @SuppressWarnings("unchecked")
    @PostMapping("/batch-submit")
    public R<Void> batchSubmit(@RequestBody Map<String, Object> body) {
        List<Number> ids = (List<Number>) body.get("ids");
        salesOrderService.batchSubmit(ids.stream().map(Number::longValue).toList());
        return R.ok();
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/batch-delete")
    public R<Void> batchDelete(@RequestBody Map<String, Object> body) {
        List<Number> ids = (List<Number>) body.get("ids");
        salesOrderService.batchDelete(ids.stream().map(Number::longValue).toList());
        return R.ok();
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/batch-void")
    public R<Void> batchVoid(@RequestBody Map<String, Object> body) {
        List<Number> ids = (List<Number>) body.get("ids");
        String reason = body.get("reason") != null ? body.get("reason").toString() : "";
        salesOrderService.batchVoid(ids.stream().map(Number::longValue).toList(), reason);
        return R.ok();
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/batch-to-work-order")
    public R<Map<String, Object>> batchToWorkOrder(@RequestBody Map<String, Object> body) {
        List<Number> ids = (List<Number>) body.get("ids");
        int count = salesOrderService.batchConvertToWorkOrder(
                ids.stream().map(Number::longValue).toList(), workOrderService);
        return R.ok(Map.of("createdCount", count));
    }

    /**
     * 整单外协：将已确认订单转为外协加工
     * POST /api/order/sales-orders/batch-to-outsource
     * Body: { ids: [1,2], supplierId: 1, taxRate: 13, deliveryDate: "2026-04-01", remark: "...", unitPrices: {itemId: price} }
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/batch-to-outsource")
    public R<Map<String, Object>> batchToOutsource(@RequestBody Map<String, Object> body) {
        List<Number> ids = (List<Number>) body.get("ids");
        Long supplierId = body.get("supplierId") != null ? Long.valueOf(body.get("supplierId").toString()) : null;
        BigDecimal taxRate = body.get("taxRate") != null ? new BigDecimal(body.get("taxRate").toString()) : null;
        LocalDate deliveryDate = body.get("deliveryDate") != null && !body.get("deliveryDate").toString().isBlank()
                ? LocalDate.parse(body.get("deliveryDate").toString()) : null;
        String remark = body.get("remark") != null ? body.get("remark").toString() : null;

        // 解析各行单价
        Map<Long, BigDecimal> unitPrices = null;
        Object unitPricesObj = body.get("unitPrices");
        if (unitPricesObj instanceof Map) {
            Map<String, Object> priceMap = (Map<String, Object>) unitPricesObj;
            unitPrices = new HashMap<>();
            for (Map.Entry<String, Object> entry : priceMap.entrySet()) {
                unitPrices.put(Long.valueOf(entry.getKey()), new BigDecimal(entry.getValue().toString()));
            }
        }

        int count = salesOrderService.batchConvertToOutsource(
                ids.stream().map(Number::longValue).toList(),
                supplierId, unitPrices, taxRate, deliveryDate, remark);
        return R.ok(Map.of("createdCount", count));
    }

    // ==================== 价格历史 ====================

    @GetMapping("/price-history")
    public R<List<Map<String, Object>>> priceHistory(
            @RequestParam Long productId,
            @RequestParam(required = false) Long customerId) {
        return R.ok(salesOrderService.getPriceHistory(productId, customerId));
    }

    // ==================== 发货记录 ====================

    @GetMapping("/{id}/shipments")
    public R<List<BizShipmentRecord>> getShipments(@PathVariable Long id) {
        return R.ok(shipmentRecordService.getByOrderId(id));
    }

    @PostMapping("/{id}/shipments")
    public R<Void> createShipment(@PathVariable Long id, @RequestBody BizShipmentRecord record) {
        record.setOrderId(id);
        shipmentRecordService.createShipment(record);
        salesOrderService.updateOrderShipmentStatus(id);
        return R.ok();
    }

    @DeleteMapping("/shipments/{shipmentId}")
    public R<Void> deleteShipment(@PathVariable Long shipmentId) {
        shipmentRecordService.deleteShipment(shipmentId);
        return R.ok();
    }

    /**
     * 创建发货出库任务
     * 从订单明细直接创建 sales_out 出库任务，仓库人员处理后自动扣减库存并更新发货状态
     * POST /api/order/sales-orders/{id}/shipment-task
     */
    @PostMapping("/{id}/shipment-task")
    public R<Map<String, Object>> createShipmentTask(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        BizSalesOrder order = salesOrderService.getById(id);
        if (order == null) return R.fail("订单不存在");

        Long orderItemId = body.get("orderItemId") != null ? Long.valueOf(body.get("orderItemId").toString()) : null;
        Long productId = body.get("productId") != null ? Long.valueOf(body.get("productId").toString()) : null;
        Long warehouseId = body.get("warehouseId") != null ? Long.valueOf(body.get("warehouseId").toString()) : null;
        Long locationId = body.get("locationId") != null ? Long.valueOf(body.get("locationId").toString()) : null;
        BigDecimal quantity = body.get("quantity") != null ? new BigDecimal(body.get("quantity").toString()) : null;
        String batchNo = body.get("batchNo") != null ? body.get("batchNo").toString().trim() : null;
        String remark = body.get("remark") != null ? body.get("remark").toString() : null;

        if (productId == null || warehouseId == null || quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return R.fail("产品、仓库和数量不能为空");
        }

        // 读取订单明细（合并：校验剩余量 + 获取单价）
        BigDecimal unitPrice = null;
        if (orderItemId != null) {
            BizSalesOrderItem orderItem = salesOrderService.getOrderItem(orderItemId);
            if (orderItem != null) {
                BigDecimal ordered = orderItem.getQuantity() != null ? orderItem.getQuantity() : BigDecimal.ZERO;
                BigDecimal shipped = orderItem.getShippedQty() != null ? orderItem.getShippedQty() : BigDecimal.ZERO;
                BigDecimal remain = ordered.subtract(shipped);
                if (quantity.compareTo(remain) > 0) {
                    return R.fail("发货数量（" + quantity + "）超过剩余可发数量（" + remain + "）");
                }
                unitPrice = orderItem.getUnitPrice();
            }
        }

        // 查询产品/物料信息
        BizMaterial material = materialMapper.selectById(productId);
        String materialNo = material != null ? material.getMaterialNo() : "";
        String materialName = material != null ? material.getMaterialName() : "";
        String spec = material != null ? material.getSpec() : "";
        String unit = material != null ? material.getUnit() : "";

        Long taskId = invTaskService.createFromSalesOrder(
                id, order.getOrderNo(), productId,
                materialNo, materialName, spec, unit,
                quantity, unitPrice, warehouseId, locationId, batchNo,
                SecurityUtil.getCurrentUserId(), remark);

        // 累加订单明细已生成任务数量
        salesOrderService.addItemTaskQty(orderItemId, quantity);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", taskId);
        return R.ok(result);
    }

    // ==================== 发票信息 ====================

    @GetMapping("/{id}/invoices")
    public R<List<BizOrderInvoice>> getInvoices(@PathVariable Long id) {
        return R.ok(orderInvoiceService.getByOrderId(id));
    }

    @PostMapping("/{id}/invoices")
    public R<Void> createInvoice(@PathVariable Long id, @RequestBody BizOrderInvoice invoice) {
        invoice.setOrderId(id);
        orderInvoiceService.createInvoice(invoice);
        return R.ok();
    }

    @PutMapping("/invoices/{invoiceId}")
    public R<Void> updateInvoice(@PathVariable Long invoiceId, @RequestBody BizOrderInvoice invoice) {
        orderInvoiceService.updateInvoice(invoiceId, invoice);
        return R.ok();
    }

    @PostMapping("/invoices/upload")
    public R<Map<String, Object>> uploadInvoiceAttachment(@RequestParam("file") MultipartFile file) throws IOException {
        return R.ok(orderInvoiceService.uploadFile(file));
    }

    @DeleteMapping("/invoices/{invoiceId}")
    public R<Void> deleteInvoice(@PathVariable Long invoiceId) {
        orderInvoiceService.deleteInvoice(invoiceId);
        return R.ok();
    }

    // ==================== 附件管理 ====================

    @PostMapping("/{orderId}/attachments")
    public R<BizOrderAttachment> uploadAttachment(
            @PathVariable Long orderId,
            @RequestParam("file") MultipartFile file) throws IOException {
        BizSalesOrder order = salesOrderService.getById(orderId);
        if (order == null) return R.fail("订单不存在");
        BizOrderAttachment attachment = orderAttachmentService.upload(orderId, order.getOrderNo(), file);
        return R.ok(attachment);
    }

    @GetMapping("/{orderId}/attachments")
    public R<List<BizOrderAttachment>> getAttachments(@PathVariable Long orderId) {
        return R.ok(orderAttachmentService.getByOrderId(orderId));
    }

    @DeleteMapping("/attachments/{attachmentId}")
    public R<Void> deleteAttachment(@PathVariable Long attachmentId) {
        orderAttachmentService.deleteAttachment(attachmentId);
        return R.ok();
    }

    // ==================== 生产进度详情 ====================

    @GetMapping("/{id}/production-detail")
    public R<Map<String, Object>> productionDetail(@PathVariable Long id) {
        return R.ok(salesOrderService.getProductionDetail(id));
    }

    // ==================== 导入 ====================

    @PostMapping("/import")
    public R<Map<String, Object>> importOrders(@RequestParam("file") MultipartFile file) throws IOException {
        return R.ok(salesOrderService.importOrders(file));
    }

    @GetMapping("/import-errors/{fileName}")
    public void downloadFailedFile(@PathVariable String fileName, HttpServletResponse response) throws IOException {
        java.io.File file = new java.io.File(fileUploadPath + "/import-errors/" + fileName);
        if (!file.exists()) { response.sendError(404, "文件不存在"); return; }
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=" + java.net.URLEncoder.encode(fileName, "UTF-8"));
        try (var is = new java.io.FileInputStream(file); var os = response.getOutputStream()) {
            is.transferTo(os);
        }
    }

    @GetMapping("/import-template")
    public void importTemplate(HttpServletResponse response) throws IOException {
        List<String> headers = new ArrayList<>(Arrays.asList(
                "订单编号(可空,空则自动生成)", "客户名称*", "联系人", "收货地址", "订单交货日期(yyyy-MM-dd)",
                "产品编号", "产品名称*", "规格型号", "计量单位",
                "数量*", "单价*", "税率%", "明细备注"));
        // 动态追加扩展字段列: 订单级在前, 明细级在后 (明细级加 [明细] 前缀避免与订单级 label 冲突)
        List<BizExtFieldConfig> extConfigs = extFieldConfigMapper.selectList(
                new LambdaQueryWrapper<BizExtFieldConfig>()
                        .eq(BizExtFieldConfig::getModule, "sales_order")
                        .eq(BizExtFieldConfig::getEnabled, 1)
                        .orderByAsc(BizExtFieldConfig::getSortOrder));
        for (BizExtFieldConfig fc : extConfigs) {
            headers.add(fc.getFieldLabel());
        }
        List<BizExtFieldConfig> itemExtConfigs = extFieldConfigMapper.selectList(
                new LambdaQueryWrapper<BizExtFieldConfig>()
                        .eq(BizExtFieldConfig::getModule, "sales_order_item")
                        .eq(BizExtFieldConfig::getEnabled, 1)
                        .orderByAsc(BizExtFieldConfig::getSortOrder));
        for (BizExtFieldConfig fc : itemExtConfigs) {
            headers.add("[明细]" + fc.getFieldLabel());
        }
        // 示例数据（3行：单规格+单单位、多规格+多单位、同客户第二条明细）
        int colCount = headers.size();
        List<String[]> sampleRows = new ArrayList<>();
        String[] row1 = new String[colCount];
        row1[0] = "SO20260501001"; row1[1] = "宁波精密机械有限公司"; row1[2] = "张经理"; row1[3] = "宁波市鄞州区XX路88号";
        row1[4] = "2026-05-01"; row1[5] = ""; row1[6] = "法兰盘A"; row1[7] = "Ø150×30mm";
        row1[8] = "个"; row1[9] = "500"; row1[10] = "50"; row1[11] = "13"; row1[12] = "";
        sampleRows.add(row1);
        String[] row2 = new String[colCount];
        row2[0] = "SO20260501001"; row2[1] = "宁波精密机械有限公司"; row2[2] = "张经理"; row2[3] = "宁波市鄞州区XX路88号";
        row2[4] = "2026-05-01"; row2[5] = ""; row2[6] = "密封圈B"; row2[7] = "材质:NBR,尺寸:Ø15mm";
        row2[8] = "箱(1箱=100个)"; row2[9] = "10"; row2[10] = "200"; row2[11] = "13"; row2[12] = "多规格+多单位示例(同订单第二行明细)";
        sampleRows.add(row2);
        String[] row3 = new String[colCount];
        row3[0] = ""; row3[1] = "杭州XX科技"; row3[2] = ""; row3[3] = "";
        row3[4] = "2026-05-15"; row3[5] = "M20250101012"; row3[6] = "弹簧垫圈M8"; row3[7] = "GB/T93 65Mn";
        row3[8] = "个"; row3[9] = "2000"; row3[10] = "0.5"; row3[11] = ""; row3[12] = "订单编号留空将自动生成";
        sampleRows.add(row3);
        List<Function<String[], Object>> extractorList = new ArrayList<>();
        for (int ci = 0; ci < colCount; ci++) {
            final int idx = ci;
            extractorList.add(r -> idx < r.length ? r[idx] : "");
        }
        ExcelUtil.export(response, "订单导入模板", headers, sampleRows, extractorList);
    }

    // ==================== 导出 ====================

    @GetMapping("/export")
    public void export(HttpServletResponse response,
                       @RequestParam(required = false) String ids) throws IOException {
        salesOrderService.exportOrders(response, ids);
    }

    // ==================== 跟进记录 ====================

    @GetMapping("/{id}/followups")
    public R<List<BizOrderFollowup>> listFollowups(@PathVariable Long id) {
        return R.ok(orderFollowupService.listByOrder(id));
    }

    @PostMapping("/{id}/followups")
    public R<Void> createFollowup(@PathVariable Long id, @RequestBody BizOrderFollowup followup) {
        orderFollowupService.create(id, followup);
        return R.ok();
    }

    @DeleteMapping("/followups/{followupId}")
    public R<Void> deleteFollowup(@PathVariable Long followupId) {
        orderFollowupService.delete(followupId);
        return R.ok();
    }

    // ==================== 操作日志 ====================

    @GetMapping("/{id}/logs")
    public R<List<BizOrderLog>> listLogs(@PathVariable Long id) {
        return R.ok(orderLogService.listByOrder(id));
    }

    // ==================== Helper ====================

    @SuppressWarnings("unchecked")
    private BizSalesOrder buildOrderFromMap(Map<String, Object> body) {
        BizSalesOrder order = new BizSalesOrder();
        if (body.get("orderNo") != null) order.setOrderNo(body.get("orderNo").toString());
        if (body.get("customerId") != null) order.setCustomerId(Long.valueOf(body.get("customerId").toString()));
        if (body.get("contactName") != null) order.setContactName(body.get("contactName").toString());
        if (body.get("shippingAddress") != null) order.setShippingAddress(body.get("shippingAddress").toString());
        if (body.get("settlementType") != null) order.setSettlementType(body.get("settlementType").toString());
        if (body.get("salesmanId") != null) order.setSalesmanId(Long.valueOf(body.get("salesmanId").toString()));
        if (body.get("attachmentUrls") != null) order.setAttachmentUrls(body.get("attachmentUrls").toString());
        if (body.get("remark") != null) order.setRemark(body.get("remark").toString());
        if (body.get("extFields") != null) order.setExtFields(body.get("extFields").toString());
        String orderDeliveryDate = body.get("deliveryDate") != null ? body.get("deliveryDate").toString() : null;
        if (orderDeliveryDate != null && !orderDeliveryDate.isBlank()) order.setDeliveryDate(LocalDate.parse(orderDeliveryDate));
        if (body.get("createBy") != null) order.setCreateBy(Long.valueOf(body.get("createBy").toString()));
        return order;
    }

    @SuppressWarnings("unchecked")
    private List<BizSalesOrderItem> buildItemsFromMap(Map<String, Object> body) {
        Object itemsObj = body.get("items");
        if (itemsObj == null) return null;

        List<Map<String, Object>> itemMaps = (List<Map<String, Object>>) itemsObj;
        List<BizSalesOrderItem> items = new java.util.ArrayList<>();
        for (Map<String, Object> m : itemMaps) {
            BizSalesOrderItem item = new BizSalesOrderItem();
            if (m.get("productId") != null) item.setProductId(Long.valueOf(m.get("productId").toString()));
            if (m.get("quantity") != null) item.setQuantity(new java.math.BigDecimal(m.get("quantity").toString()));
            if (m.get("unitPrice") != null) item.setUnitPrice(new java.math.BigDecimal(m.get("unitPrice").toString()));
            String deliveryDateStr = m.get("deliveryDate") != null ? m.get("deliveryDate").toString() : null;
            if (deliveryDateStr != null && !deliveryDateStr.isBlank()) item.setDeliveryDate(LocalDate.parse(deliveryDateStr));
            if (m.get("taxRate") != null) item.setTaxRate(new java.math.BigDecimal(m.get("taxRate").toString()));
            if (m.get("remark") != null) item.setRemark(m.get("remark").toString());
            // 行级扩展字段(由 sales_order_item 模块字段配置定义)
            if (m.get("extFields") != null) item.setExtFields(m.get("extFields").toString());
            items.add(item);
        }
        return items;
    }
}
