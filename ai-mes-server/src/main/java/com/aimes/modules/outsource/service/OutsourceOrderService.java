package com.aimes.modules.outsource.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.PageResult;
import com.aimes.common.utils.CodeUtil;
import com.aimes.common.utils.SecurityUtil;
import com.aimes.modules.inventory.service.BizInvTaskService;
import com.aimes.modules.inventory.service.InventoryService;
import com.aimes.modules.master.entity.BizMaterial;
import com.aimes.modules.master.entity.BizProduct;
import com.aimes.modules.master.entity.BizSupplier;
import com.aimes.modules.master.mapper.BizMaterialMapper;
import com.aimes.modules.master.mapper.BizProductMapper;
import com.aimes.modules.master.mapper.BizSupplierMapper;
import com.aimes.modules.outsource.entity.*;
import com.aimes.modules.outsource.mapper.*;
import com.aimes.modules.production.entity.BizWorkOrder;
import com.aimes.modules.production.mapper.BizWorkOrderMapper;
import com.aimes.modules.bom.entity.BizBomItem;
import com.aimes.modules.bom.entity.BizBomVersion;
import com.aimes.modules.bom.mapper.BizBomVersionMapper;
import com.aimes.modules.bom.mapper.BizBomItemMapper;
import com.aimes.modules.order.entity.BizSalesOrder;
import com.aimes.modules.order.mapper.BizSalesOrderMapper;
import com.aimes.modules.quality.entity.BizQcTask;
import com.aimes.modules.quality.service.BizQcTaskService;
import com.aimes.modules.production.entity.BizWoProcessTask;
import com.aimes.modules.production.mapper.BizWoProcessTaskMapper;
import com.aimes.modules.system.service.SysNotificationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutsourceOrderService extends ServiceImpl<BizOutsourceOrderMapper, BizOutsourceOrder> {

    private static final Map<String, String> STATUS_NAME_MAP = Map.of(
            "draft",              "草稿",
            "pending_approval",   "待审批",
            "confirmed",          "已确认",
            "processing",         "加工中",
            "completed",          "已完成",
            "cancelled",          "已取消"
    );

    private final BizOutsourceOrderRequestMapper orderRequestMapper;
    private final BizOutsourceDeliveryMapper deliveryMapper;
    private final BizOutsourceDeliveryItemMapper deliveryItemMapper;
    private final BizOutsourceReceiptMapper receiptMapper;
    private final InventoryService inventoryService;
    private final BizQcTaskService qcTaskService;
    private final SysNotificationService notificationService;
    private final BizSupplierMapper supplierMapper;
    private final BizProductMapper productMapper;
    private final BizMaterialMapper materialMapper;
    private final BizWoProcessTaskMapper processTaskMapper;
    private final OutsourceChainService outsourceChainService;
    private final BizOutsourceRequestMapper requestMapper;
    private final BizWorkOrderMapper workOrderMapper;
    private final BizSalesOrderMapper salesOrderMapper;
    private final BizBomVersionMapper bomVersionMapper;
    private final BizBomItemMapper bomItemMapper;
    private final BizInvTaskService invTaskService;
    private final com.aimes.modules.inventory.mapper.BizInvTaskItemMapper invTaskItemMapper;

    /**
     * 分页查询外协订单
     */
    public PageResult<Map<String, Object>> getPage(String keyword, String status, Long supplierId,
                                                    String workOrderNo, String stepName, Long productId,
                                                    String startDate, String endDate,
                                                    int pageNum, int pageSize) {
        return getPage(keyword, status, supplierId, workOrderNo, stepName, productId, null, startDate, endDate, pageNum, pageSize);
    }

    public PageResult<Map<String, Object>> getPage(String keyword, String status, Long supplierId,
                                                    String workOrderNo, String stepName, Long productId, String productName,
                                                    String startDate, String endDate,
                                                    int pageNum, int pageSize) {
        // 产品名称模糊查 → productIds → 用 IN 过滤
        Set<Long> productNameMatchedIds = null;
        if (StringUtils.hasText(productName)) {
            List<Long> ids = materialMapper.selectList(
                    new LambdaQueryWrapper<BizMaterial>().like(BizMaterial::getMaterialName, productName))
                    .stream().map(BizMaterial::getId).collect(Collectors.toList());
            if (ids.isEmpty()) return new PageResult<>(List.of(), 0L, pageNum, pageSize);
            productNameMatchedIds = new HashSet<>(ids);
        }
        // 关联单号筛选:同时匹配工单编号(工序外协)和销售订单号(整单外协)
        Set<Long> matchedWoIds = null;
        Set<Long> matchedSalesOrderIds = null;
        if (StringUtils.hasText(workOrderNo)) {
            matchedWoIds = workOrderMapper.selectList(
                    new LambdaQueryWrapper<BizWorkOrder>()
                            .like(BizWorkOrder::getWoNo, workOrderNo))
                    .stream().map(BizWorkOrder::getId).collect(Collectors.toSet());
            matchedSalesOrderIds = salesOrderMapper.selectList(
                    new LambdaQueryWrapper<BizSalesOrder>()
                            .like(BizSalesOrder::getOrderNo, workOrderNo))
                    .stream().map(BizSalesOrder::getId).collect(Collectors.toSet());
            if (matchedWoIds.isEmpty() && matchedSalesOrderIds.isEmpty()) {
                return new PageResult<>(List.of(), 0L, pageNum, pageSize);
            }
        }

        final Set<Long> finalWoIds = matchedWoIds;
        final Set<Long> finalSalesIds = matchedSalesOrderIds;
        LambdaQueryWrapper<BizOutsourceOrder> wrapper = new LambdaQueryWrapper<BizOutsourceOrder>()
                .like(StringUtils.hasText(keyword), BizOutsourceOrder::getOrderNo, keyword)
                .eq(StringUtils.hasText(status), BizOutsourceOrder::getStatus, status)
                .eq(supplierId != null, BizOutsourceOrder::getSupplierId, supplierId)
                .like(StringUtils.hasText(stepName), BizOutsourceOrder::getStepName, stepName)
                .eq(productId != null, BizOutsourceOrder::getProductId, productId)
                .in(productNameMatchedIds != null, BizOutsourceOrder::getProductId, productNameMatchedIds != null ? productNameMatchedIds : List.of())
                .and(finalWoIds != null || finalSalesIds != null, w -> {
                    boolean hasWo = finalWoIds != null && !finalWoIds.isEmpty();
                    boolean hasSo = finalSalesIds != null && !finalSalesIds.isEmpty();
                    if (hasWo) w.in(BizOutsourceOrder::getWorkOrderId, finalWoIds);
                    if (hasWo && hasSo) w.or();
                    if (hasSo) w.in(BizOutsourceOrder::getOrderId, finalSalesIds);
                })
                .ge(StringUtils.hasText(startDate), BizOutsourceOrder::getDeliveryDate, StringUtils.hasText(startDate) ? java.time.LocalDate.parse(startDate) : null)
                .le(StringUtils.hasText(endDate), BizOutsourceOrder::getDeliveryDate, StringUtils.hasText(endDate) ? java.time.LocalDate.parse(endDate) : null)
                .orderByDesc(BizOutsourceOrder::getCreateTime);

        Page<BizOutsourceOrder> page = page(new Page<>(pageNum, pageSize), wrapper);

        // 批量加载关联实体名称
        List<BizOutsourceOrder> records = page.getRecords();
        Set<Long> supplierIds = records.stream().map(BizOutsourceOrder::getSupplierId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> supplierNameMap = supplierIds.isEmpty() ? Map.of() :
                supplierMapper.selectBatchIds(supplierIds).stream().collect(Collectors.toMap(BizSupplier::getId, BizSupplier::getSupplierName, (a, b) -> a));

        Set<Long> productIds = records.stream().map(BizOutsourceOrder::getProductId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> productNameMap = productIds.isEmpty() ? Map.of() :
                materialMapper.selectBatchIds(productIds).stream().collect(Collectors.toMap(BizMaterial::getId, BizMaterial::getMaterialName, (a, b) -> a));
        Map<Long, String> productNoMap = productIds.isEmpty() ? Map.of() :
                materialMapper.selectBatchIds(productIds).stream().collect(Collectors.toMap(BizMaterial::getId, m -> m.getMaterialNo() != null ? m.getMaterialNo() : "", (a, b) -> a));
        Map<Long, String> productSpecMap = productIds.isEmpty() ? Map.of() :
                materialMapper.selectBatchIds(productIds).stream().collect(Collectors.toMap(BizMaterial::getId, m -> m.getSpec() != null ? m.getSpec() : "", (a, b) -> a));
        Map<Long, String> productUnitMap = productIds.isEmpty() ? Map.of() :
                materialMapper.selectBatchIds(productIds).stream().collect(Collectors.toMap(BizMaterial::getId, m -> m.getUnit() != null ? m.getUnit() : "", (a, b) -> a));

        // 收料物料信息
        Set<Long> receiptMatIds = records.stream().map(BizOutsourceOrder::getReceiptMaterialId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, BizMaterial> receiptMatMap = receiptMatIds.isEmpty() ? Map.of() :
                materialMapper.selectBatchIds(receiptMatIds).stream().collect(Collectors.toMap(BizMaterial::getId, m -> m, (a, b) -> a));

        // 批量加载关联销售订单编号（整单外协）
        Set<Long> salesOrderIds = records.stream().map(BizOutsourceOrder::getOrderId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> salesOrderNoMap = salesOrderIds.isEmpty() ? Map.of() :
                salesOrderMapper.selectBatchIds(salesOrderIds).stream()
                        .collect(Collectors.toMap(BizSalesOrder::getId, BizSalesOrder::getOrderNo, (a, b) -> a));

        // 批量加载关联工单编号（工序外协）
        Set<Long> workOrderIds = records.stream().map(BizOutsourceOrder::getWorkOrderId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> workOrderNoMap = workOrderIds.isEmpty() ? Map.of() :
                workOrderMapper.selectBatchIds(workOrderIds).stream()
                        .collect(Collectors.toMap(BizWorkOrder::getId, BizWorkOrder::getWoNo, (a, b) -> a));

        List<Map<String, Object>> list = records.stream().map(o -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", o.getId());
            map.put("orderNo", o.getOrderNo());
            map.put("supplierId", o.getSupplierId());
            map.put("supplierName", o.getSupplierId() != null ? supplierNameMap.get(o.getSupplierId()) : null);
            map.put("productId", o.getProductId());
            map.put("productName", o.getProductId() != null ? productNameMap.get(o.getProductId()) : null);
            map.put("materialNo", o.getProductId() != null ? productNoMap.getOrDefault(o.getProductId(), "") : "");
            map.put("spec", o.getProductId() != null ? productSpecMap.getOrDefault(o.getProductId(), "") : "");
            map.put("unit", o.getProductId() != null ? productUnitMap.getOrDefault(o.getProductId(), "") : "");
            map.put("stepName", o.getStepName());
            map.put("totalQty", o.getTotalQty());
            map.put("quantity", o.getTotalQty());       // 兼容前端 item.quantity
            map.put("unitPrice", o.getUnitPrice());
            map.put("totalAmount", o.getTotalAmount());
            map.put("taxRate", o.getTaxRate());
            map.put("taxAmount", o.getTaxAmount());
            map.put("deliveryDate", o.getDeliveryDate());
            map.put("expectedDate", o.getDeliveryDate()); // 兼容前端 item.expectedDate
            map.put("deliveredQty", o.getDeliveredQty());
            map.put("deliveryQty", o.getDeliveryQty());
            map.put("qualifiedQty", o.getQualifiedQty());
            map.put("virtualMaterialId", o.getVirtualMaterialId());
            map.put("receiptMaterialId", o.getReceiptMaterialId());
            BizMaterial rMat = o.getReceiptMaterialId() != null ? receiptMatMap.get(o.getReceiptMaterialId()) : null;
            map.put("receiptMaterialNo", rMat != null ? rMat.getMaterialNo() : "");
            map.put("receiptMaterialName", rMat != null ? rMat.getMaterialName() : "");
            map.put("receiptMaterialSpec", rMat != null && rMat.getSpec() != null ? rMat.getSpec() : "");
            map.put("receiptMaterialUnit", rMat != null && rMat.getUnit() != null ? rMat.getUnit() : "");
            map.put("workOrderId", o.getWorkOrderId());
            map.put("taskId", o.getTaskId());
            map.put("requestId", o.getRequestId());
            map.put("salesOrderId", o.getOrderId());
            map.put("salesOrderNo", o.getOrderId() != null ? salesOrderNoMap.get(o.getOrderId()) : null);
            map.put("workOrderNo", o.getWorkOrderId() != null ? workOrderNoMap.get(o.getWorkOrderId()) : null);
            // 整单外协：有销售订单但没具体工单(workOrderId 为 null 或 0 均视为整单)
            boolean isFullOutsource = o.getOrderId() != null
                    && (o.getWorkOrderId() == null || o.getWorkOrderId() == 0);
            map.put("isFullOutsource", isFullOutsource);
            // 来源类型和关联单据号
            map.put("sourceType", isFullOutsource ? "销售订单" : "生产工单");
            map.put("sourceNo", isFullOutsource
                    ? (o.getOrderId() != null ? salesOrderNoMap.get(o.getOrderId()) : null)
                    : (o.getWorkOrderId() != null ? workOrderNoMap.get(o.getWorkOrderId()) : null));
            map.put("status", o.getStatus());
            map.put("statusName", STATUS_NAME_MAP.getOrDefault(o.getStatus(), o.getStatus()));
            map.put("receiptQty", o.getReceiptQty());
            map.put("deliveryTaskQty", o.getDeliveryTaskQty());
            map.put("deliveryOutQty", o.getDeliveryOutQty());
            map.put("receiptTaskQty", o.getReceiptTaskQty());
            map.put("receiptInQty", o.getReceiptInQty());
            map.put("processRequirement", o.getProcessRequirement());
            map.put("remark", o.getRemark());
            map.put("createTime", o.getCreateTime());
            map.put("updateTime", o.getUpdateTime());
            return map;
        }).collect(Collectors.toList());

        return new PageResult<>(list, page.getTotal(), pageNum, pageSize);
    }

    /**
     * 通过订单编号查询详情（供关联单据跳转使用）
     */
    public Map<String, Object> getDetailByOrderNo(String orderNo) {
        BizOutsourceOrder order = getOne(new LambdaQueryWrapper<BizOutsourceOrder>()
                .eq(BizOutsourceOrder::getOrderNo, orderNo).last("LIMIT 1"));
        // 兜底：如果传进来的是外协收料单号(OSC开头)，通过收料单反查关联的外协订单
        if (order == null) {
            BizOutsourceReceipt receipt = receiptMapper.selectOne(
                    new LambdaQueryWrapper<BizOutsourceReceipt>()
                            .eq(BizOutsourceReceipt::getReceiptNo, orderNo).last("LIMIT 1"));
            if (receipt != null && receipt.getOutsourceOrderId() != null) {
                order = getById(receipt.getOutsourceOrderId());
            }
        }
        if (order == null) throw new BusinessException("外协订单不存在: " + orderNo);
        return getDetail(order.getId());
    }

    /**
     * 订单详情（含关联申请、发货记录、收货记录）
     */
    public Map<String, Object> getDetail(Long id) {
        BizOutsourceOrder order = getById(id);
        if (order == null) {
            throw new BusinessException("外协订单不存在");
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", order.getId());
        detail.put("orderNo", order.getOrderNo());
        detail.put("supplierId", order.getSupplierId());
        // 供应商名称
        if (order.getSupplierId() != null) {
            BizSupplier supplier = supplierMapper.selectById(order.getSupplierId());
            detail.put("supplierName", supplier != null ? supplier.getSupplierName() : null);
        } else {
            detail.put("supplierName", null);
        }
        detail.put("productId", order.getProductId());
        // 产品名称：优先从 receiptMaterialId，再从 productId（整单外协的产品存在 receiptMaterialId）
        String productName = null;
        String productSpec = null;
        String productUnit = null;
        if (order.getReceiptMaterialId() != null) {
            BizMaterial mat = materialMapper.selectById(order.getReceiptMaterialId());
            if (mat != null) {
                productName = mat.getMaterialName();
                productSpec = mat.getSpec();
                productUnit = mat.getUnit();
            }
        }
        if (productName == null && order.getProductId() != null) {
            BizMaterial mat = materialMapper.selectById(order.getProductId());
            if (mat != null) {
                productName = mat.getMaterialName();
                productSpec = mat.getSpec();
                productUnit = mat.getUnit();
            }
        }
        detail.put("productName", productName);
        detail.put("productSpec", productSpec);
        detail.put("productUnit", productUnit);
        detail.put("stepName", order.getStepName());
        detail.put("totalQty", order.getTotalQty());
        detail.put("quantity", order.getTotalQty());
        detail.put("unitPrice", order.getUnitPrice());
        detail.put("totalAmount", order.getTotalAmount());
        detail.put("taxRate", order.getTaxRate());
        detail.put("taxAmount", order.getTaxAmount());
        detail.put("deliveryDate", order.getDeliveryDate());
        detail.put("deliveredQty", order.getDeliveredQty());
        detail.put("deliveryQty", order.getDeliveryQty());
        detail.put("qualifiedQty", order.getQualifiedQty());
        detail.put("workOrderId", order.getWorkOrderId());
        detail.put("taskId", order.getTaskId());
        detail.put("requestId", order.getRequestId());
        detail.put("salesOrderId", order.getOrderId());
        detail.put("salesOrderItemId", order.getOrderItemId());
        detail.put("isFullOutsource", order.getOrderId() != null && order.getWorkOrderId() == null);
        // 查询关联销售订单编号
        if (order.getOrderId() != null) {
            BizSalesOrder salesOrder = salesOrderMapper.selectById(order.getOrderId());
            detail.put("salesOrderNo", salesOrder != null ? salesOrder.getOrderNo() : null);
        }
        detail.put("status", order.getStatus());
        detail.put("deliveryTaskQty", order.getDeliveryTaskQty());
        detail.put("deliveryOutQty", order.getDeliveryOutQty());
        detail.put("receiptQty", order.getReceiptQty());
        detail.put("receiptTaskQty", order.getReceiptTaskQty());
        detail.put("receiptInQty", order.getReceiptInQty());
        detail.put("processRequirement", order.getProcessRequirement());
        detail.put("drawingUrls", order.getDrawingUrls());
        detail.put("approvedBy", order.getApprovedBy());
        detail.put("approvedAt", order.getApprovedAt());
        detail.put("approveRemark", order.getApproveRemark());
        detail.put("remark", order.getRemark());
        detail.put("createTime", order.getCreateTime());

        // 关联的外协申请
        List<BizOutsourceOrderRequest> orderRequests = orderRequestMapper.selectList(
                new LambdaQueryWrapper<BizOutsourceOrderRequest>()
                        .eq(BizOutsourceOrderRequest::getOutsourceOrderId, id));
        detail.put("requests", orderRequests);

        // 发货记录 — 拼接 delivery_item 中的物料信息，前端字段：deliveryNo/materialName/quantity/...
        List<BizOutsourceDelivery> deliveries = deliveryMapper.selectList(
                new LambdaQueryWrapper<BizOutsourceDelivery>()
                        .eq(BizOutsourceDelivery::getOutsourceOrderId, id)
                        .orderByDesc(BizOutsourceDelivery::getCreateTime));
        // 一次性加载所有相关 delivery_item
        List<Long> deliveryIds = deliveries.stream().map(BizOutsourceDelivery::getId).collect(java.util.stream.Collectors.toList());
        Map<Long, List<BizOutsourceDeliveryItem>> diMap = deliveryIds.isEmpty() ? Map.of() :
                deliveryItemMapper.selectList(new LambdaQueryWrapper<BizOutsourceDeliveryItem>()
                        .in(BizOutsourceDeliveryItem::getDeliveryId, deliveryIds))
                        .stream().collect(java.util.stream.Collectors.groupingBy(BizOutsourceDeliveryItem::getDeliveryId));
        // 预加载物料名称
        java.util.Set<Long> matIds = diMap.values().stream().flatMap(List::stream)
                .map(BizOutsourceDeliveryItem::getMaterialId).filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        Map<Long, com.aimes.modules.master.entity.BizMaterial> matLookup = matIds.isEmpty() ? Map.of() :
                materialMapper.selectBatchIds(matIds).stream()
                        .collect(java.util.stream.Collectors.toMap(com.aimes.modules.master.entity.BizMaterial::getId, m -> m));
        List<Map<String, Object>> deliveryViews = new ArrayList<>();
        for (BizOutsourceDelivery d : deliveries) {
            List<BizOutsourceDeliveryItem> items = diMap.getOrDefault(d.getId(), List.of());
            // 主物料信息：取第一条 item
            BizOutsourceDeliveryItem firstItem = items.isEmpty() ? null : items.get(0);
            com.aimes.modules.master.entity.BizMaterial mat = (firstItem != null && firstItem.getMaterialId() != null)
                    ? matLookup.get(firstItem.getMaterialId()) : null;
            Map<String, Object> dv = new LinkedHashMap<>();
            dv.put("id", d.getId());
            dv.put("deliveryNo", d.getDeliveryNo());
            dv.put("deliveryType", d.getDeliveryType());
            dv.put("status", d.getStatus());
            dv.put("createTime", d.getCreateTime());
            dv.put("materialId", mat != null ? mat.getId() : null);
            dv.put("materialNo", mat != null ? mat.getMaterialNo() : null);
            dv.put("materialName", mat != null ? mat.getMaterialName() : null);
            BigDecimal qty = items.stream().map(i -> i.getActualQty() != null ? i.getActualQty()
                            : (i.getShouldQty() != null ? i.getShouldQty() : BigDecimal.ZERO))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (qty.compareTo(BigDecimal.ZERO) == 0 && d.getDeliveryQty() != null) qty = d.getDeliveryQty();
            dv.put("quantity", qty);
            dv.put("warehouseId", firstItem != null ? firstItem.getWarehouseId() : null);
            dv.put("warehouseName", "");
            dv.put("operatorName", "");
            dv.put("items", items);
            deliveryViews.add(dv);
        }
        detail.put("deliveries", deliveryViews);

        // 收货记录（修正字段）
        List<BizOutsourceReceipt> receipts = receiptMapper.selectList(
                new LambdaQueryWrapper<BizOutsourceReceipt>()
                        .eq(BizOutsourceReceipt::getOutsourceOrderId, id)
                        .orderByDesc(BizOutsourceReceipt::getCreateTime));
        detail.put("receipts", receipts);

        // 关联库存任务（发料+收货任务明细，供详情页展示完整物料信息）
        List<com.aimes.modules.inventory.entity.BizInvTask> invTasks = invTaskService.list(
                new LambdaQueryWrapper<com.aimes.modules.inventory.entity.BizInvTask>()
                        .eq(com.aimes.modules.inventory.entity.BizInvTask::getSourceType, "outsource_order")
                        .eq(com.aimes.modules.inventory.entity.BizInvTask::getSourceId, id)
                        .orderByDesc(com.aimes.modules.inventory.entity.BizInvTask::getCreateTime));
        // 按类型拆分，并加载任务明细
        List<Map<String, Object>> deliveryTaskList = new ArrayList<>();
        List<Map<String, Object>> receiptTaskList = new ArrayList<>();
        // 预收集所有 materialId 用于一次性 fallback 查物料(spec/unit 等基础信息)
        java.util.Set<Long> taskItemMatIds = new java.util.HashSet<>();
        Map<Long, List<com.aimes.modules.inventory.entity.BizInvTaskItem>> itemsByTask = new HashMap<>();
        for (com.aimes.modules.inventory.entity.BizInvTask t : invTasks) {
            List<com.aimes.modules.inventory.entity.BizInvTaskItem> its = invTaskItemMapper.selectList(
                    new LambdaQueryWrapper<com.aimes.modules.inventory.entity.BizInvTaskItem>()
                            .eq(com.aimes.modules.inventory.entity.BizInvTaskItem::getTaskId, t.getId()));
            itemsByTask.put(t.getId(), its);
            its.forEach(it -> { if (it.getMaterialId() != null) taskItemMatIds.add(it.getMaterialId()); });
        }
        Map<Long, BizMaterial> taskItemMatMap = taskItemMatIds.isEmpty() ? new HashMap<>() :
                materialMapper.selectBatchIds(taskItemMatIds).stream()
                        .collect(java.util.stream.Collectors.toMap(BizMaterial::getId, m -> m, (a, b) -> a));
        for (com.aimes.modules.inventory.entity.BizInvTask t : invTasks) {
            List<com.aimes.modules.inventory.entity.BizInvTaskItem> items = itemsByTask.getOrDefault(t.getId(), List.of());
            for (com.aimes.modules.inventory.entity.BizInvTaskItem item : items) {
                BizMaterial fallbackMat = item.getMaterialId() != null ? taskItemMatMap.get(item.getMaterialId()) : null;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("taskNo", t.getTaskNo());
                row.put("invType", t.getInvType());
                row.put("status", t.getStatus());
                row.put("materialNo", item.getMaterialNo() != null ? item.getMaterialNo() : (fallbackMat != null ? fallbackMat.getMaterialNo() : null));
                row.put("materialName", item.getMaterialName() != null ? item.getMaterialName() : (fallbackMat != null ? fallbackMat.getMaterialName() : null));
                row.put("spec", item.getSpec() != null ? item.getSpec() : (fallbackMat != null ? fallbackMat.getSpec() : null));
                row.put("unit", item.getUnit() != null ? item.getUnit() : (fallbackMat != null ? fallbackMat.getUnit() : null));
                row.put("quantity", item.getQuantity());
                row.put("processedQty", item.getProcessedQty());
                row.put("warehouseId", item.getWarehouseId());
                row.put("createTime", t.getCreateTime());
                row.put("remark", t.getRemark());
                row.put("returnReason", t.getReturnReason());
                if ("outsource_out".equals(t.getInvType())) {
                    deliveryTaskList.add(row);
                } else if ("outsource_in".equals(t.getInvType())) {
                    receiptTaskList.add(row);
                }
            }
        }
        detail.put("deliveryTasks", deliveryTaskList);
        detail.put("receiptTasks", receiptTaskList);

        // ========== 发料物料明细（供发料弹窗展示） ==========
        List<Map<String, Object>> deliveryMaterials = new ArrayList<>();
        if (order.getVirtualMaterialId() != null) {
            // 工序外协 + 已建虚拟半成品: 用虚拟半成品作为发料项
            BizMaterial vMat = materialMapper.selectById(order.getVirtualMaterialId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("materialId", order.getVirtualMaterialId());
            m.put("materialNo", vMat != null ? vMat.getMaterialNo() : null);
            m.put("materialName", vMat != null ? vMat.getMaterialName() : null);
            m.put("spec", vMat != null ? vMat.getSpec() : null);
            m.put("unit", vMat != null ? vMat.getUnit() : null);
            m.put("perUnitQty", BigDecimal.ONE);
            m.put("defaultWarehouseId", 999L);
            deliveryMaterials.add(m);
        } else if (order.getProductId() != null) {
            // 整单外协 / 工序外协回退: 按产品 BOM 展开到叶子层
            List<BizBomItem> bomItems = getBomItemsForProduct(order.getProductId());
            for (BizBomItem bomItem : bomItems) {
                BizMaterial mat = materialMapper.selectById(bomItem.getMaterialId());
                BigDecimal perUnitQty = bomItem.getActualQuantity() != null ? bomItem.getActualQuantity() : bomItem.getQuantity();
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("materialId", bomItem.getMaterialId());
                m.put("materialNo", mat != null ? mat.getMaterialNo() : null);
                m.put("materialName", mat != null ? mat.getMaterialName() : null);
                m.put("spec", mat != null ? mat.getSpec() : null);
                m.put("unit", mat != null ? mat.getUnit() : null);
                m.put("perUnitQty", perUnitQty);
                m.put("defaultWarehouseId", mat != null ? mat.getDefaultWarehouseId() : 999L);
                deliveryMaterials.add(m);
            }
        }
        detail.put("deliveryMaterials", deliveryMaterials);

        // ========== 收货物料明细（供收货弹窗展示） ==========
        Long receiptMaterialId = order.getReceiptMaterialId() != null ? order.getReceiptMaterialId() : order.getProductId();
        if (receiptMaterialId != null) {
            BizMaterial rMat = materialMapper.selectById(receiptMaterialId);
            Map<String, Object> rm = new LinkedHashMap<>();
            rm.put("materialId", receiptMaterialId);
            rm.put("materialNo", rMat != null ? rMat.getMaterialNo() : null);
            rm.put("materialName", rMat != null ? rMat.getMaterialName() : null);
            rm.put("spec", rMat != null ? rMat.getSpec() : null);
            rm.put("unit", rMat != null ? rMat.getUnit() : null);
            rm.put("defaultWarehouseId", 999L);
            detail.put("receiptMaterial", rm);
        }

        return detail;
    }

    /**
     * 创建外协订单
     */
    @Transactional(rollbackFor = Exception.class)
    public void create(BizOutsourceOrder order, List<Long> requestIds) {
        order.setOrderNo(CodeUtil.generate("OO"));
        order.setDeliveredQty(BigDecimal.ZERO);
        order.setQualifiedQty(BigDecimal.ZERO);
        order.setDeliveryTaskQty(BigDecimal.ZERO);
        order.setDeliveryOutQty(BigDecimal.ZERO);
        order.setReceiptTaskQty(BigDecimal.ZERO);
        order.setReceiptInQty(BigDecimal.ZERO);

        // 单价为含税单价: 含税总金额 = 单价 × 数量; 不含税金额 = 含税 / (1+税率/100); 税额 = 含税 - 不含税
        // 不含税金额未在 entity 单独存,前端展示时从 totalAmount - taxAmount 派生
        BigDecimal taxRate = order.getTaxRate() != null ? order.getTaxRate() : new BigDecimal("13");
        order.setTaxRate(taxRate);
        BigDecimal totalAmount = order.getUnitPrice().multiply(order.getTotalQty()).setScale(2, BigDecimal.ROUND_HALF_UP);
        BigDecimal factor = BigDecimal.ONE.add(taxRate.divide(new BigDecimal("100"), 6, BigDecimal.ROUND_HALF_UP));
        BigDecimal preTax = totalAmount.divide(factor, 2, BigDecimal.ROUND_HALF_UP);
        BigDecimal taxAmount = totalAmount.subtract(preTax);
        order.setTaxAmount(taxAmount);
        order.setTotalAmount(totalAmount);

        if (order.getDeliveryQty() == null) {
            order.setDeliveryQty(BigDecimal.ZERO);
        }
        order.setStatus("draft");
        save(order);

        // 保存关联申请 & 更新外协链路
        if (requestIds != null) {
            for (Long requestId : requestIds) {
                BizOutsourceOrderRequest orderRequest = new BizOutsourceOrderRequest();
                orderRequest.setOutsourceOrderId(order.getId());
                orderRequest.setOutsourceRequestId(requestId);
                orderRequestMapper.insert(orderRequest);

                // 从外协申请继承工单关联信息
                BizOutsourceRequest request = requestMapper.selectById(requestId);
                if (request != null) {
                    if (order.getWorkOrderId() == null && request.getWorkOrderId() != null) {
                        order.setWorkOrderId(request.getWorkOrderId());
                    }
                    if (order.getTaskId() == null && request.getTaskId() != null) {
                        order.setTaskId(request.getTaskId());
                    }
                    if (order.getVirtualMaterialId() == null && request.getVirtualMaterialId() != null) {
                        order.setVirtualMaterialId(request.getVirtualMaterialId());
                    }
                    if (order.getReceiptMaterialId() == null && request.getReceiptMaterialId() != null) {
                        order.setReceiptMaterialId(request.getReceiptMaterialId());
                    }
                    if (order.getRequestId() == null) {
                        order.setRequestId(requestId);
                    }

                    // 更新外协申请: 已转单数量 + 状态
                    outsourceChainService.onOrderCreatedFromRequest(requestId, order.getTotalQty());
                }
            }
            // 更新工单关联到订单
            updateById(order);
        }
    }

    /**
     * 编辑草稿状态的加工单（供应商、单价、税率、交付日期、工艺要求等）
     */
    public void updateDraft(Long id, BizOutsourceOrder updates) {
        BizOutsourceOrder order = getById(id);
        if (order == null) throw new BusinessException("外协订单不存在");
        if (!"draft".equals(order.getStatus())) throw new BusinessException("只有草稿状态可编辑");
        if (updates.getSupplierId() != null) order.setSupplierId(updates.getSupplierId());
        if (updates.getUnitPrice() != null) order.setUnitPrice(updates.getUnitPrice());
        if (updates.getTaxRate() != null) order.setTaxRate(updates.getTaxRate());
        // 任何影响金额的字段更新后,统一按"单价含税"重算 taxAmount / totalAmount
        if (updates.getUnitPrice() != null || updates.getTaxRate() != null) {
            BigDecimal taxRate = order.getTaxRate() != null ? order.getTaxRate() : BigDecimal.ZERO;
            BigDecimal totalAmount = order.getUnitPrice().multiply(order.getTotalQty()).setScale(2, BigDecimal.ROUND_HALF_UP);
            BigDecimal factor = BigDecimal.ONE.add(taxRate.divide(new BigDecimal("100"), 6, BigDecimal.ROUND_HALF_UP));
            BigDecimal preTax = totalAmount.divide(factor, 2, BigDecimal.ROUND_HALF_UP);
            order.setTaxAmount(totalAmount.subtract(preTax));
            order.setTotalAmount(totalAmount);
        }
        if (updates.getDeliveryDate() != null) order.setDeliveryDate(updates.getDeliveryDate());
        if (updates.getProcessRequirement() != null) order.setProcessRequirement(updates.getProcessRequirement());
        if (updates.getDrawingUrls() != null) order.setDrawingUrls(updates.getDrawingUrls());
        if (updates.getRemark() != null) order.setRemark(updates.getRemark());
        updateById(order);
    }

    /**
     * 提交审批：draft → pending_approval
     */
    public void submit(Long id) {
        BizOutsourceOrder order = getById(id);
        if (order == null) throw new BusinessException("外协订单不存在");
        if (!"draft".equals(order.getStatus())) throw new BusinessException("只有草稿状态可提交审批");
        if (order.getSupplierId() == null) throw new BusinessException("请先选择供应商");
        if (order.getUnitPrice() == null) throw new BusinessException("请先填写单价");
        order.setStatus("pending_approval");
        updateById(order);
    }

    /**
     * 审批通过：pending_approval → confirmed
     */
    public void approve(Long id, Long approvedBy, String remark) {
        BizOutsourceOrder order = getById(id);
        if (order == null) throw new BusinessException("外协订单不存在");
        if (!"pending_approval".equals(order.getStatus())) throw new BusinessException("只有待审批状态可审批");
        order.setApprovedBy(approvedBy);
        order.setApprovedAt(java.time.LocalDateTime.now());
        order.setApproveRemark(remark);
        order.setStatus("confirmed");
        updateById(order);
    }

    /**
     * 驳回：pending_approval → draft
     */
    public void reject(Long id, Long approvedBy, String remark) {
        BizOutsourceOrder order = getById(id);
        if (order == null) throw new BusinessException("外协订单不存在");
        if (!"pending_approval".equals(order.getStatus())) throw new BusinessException("只有待审批状态可驳回");
        order.setApprovedBy(approvedBy);
        order.setApprovedAt(java.time.LocalDateTime.now());
        order.setApproveRemark(remark);
        order.setStatus("draft");
        updateById(order);
    }

    /**
     * 确认外协订单（兼容旧接口）
     */
    public void confirm(Long id) {
        BizOutsourceOrder order = getById(id);
        if (order == null) throw new BusinessException("外协订单不存在");
        if (!"draft".equals(order.getStatus())) throw new BusinessException("只有草稿状态的订单可以确认");
        order.setStatus("confirmed");
        updateById(order);
    }

    /**
     * 取消外协订单，并回收已出库的虚拟半成品库存
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long id) {
        BizOutsourceOrder order = getById(id);
        if (order == null) {
            throw new BusinessException("外协订单不存在");
        }
        String status = order.getStatus();
        if ("cancelled".equals(status) || "completed".equals(status)) {
            throw new BusinessException("当前状态[" + STATUS_NAME_MAP.getOrDefault(status, status) + "]不允许取消");
        }

        // 取消关联的 pending 状态库存任务
        try {
            invTaskService.cancelTasksBySource("outsource_order", id);
        } catch (Exception e) {
            log.warn("取消外协关联库存任务失败: orderId={}", id, e);
        }

        // 若已发料虚拟半成品，则回冲库存
        if (order.getVirtualMaterialId() != null
                && order.getDeliveryQty() != null
                && order.getDeliveryQty().compareTo(BigDecimal.ZERO) > 0) {
            try {
                BizMaterial virtualMaterial = materialMapper.selectById(order.getVirtualMaterialId());
                Long warehouseId = virtualMaterial != null && virtualMaterial.getDefaultWarehouseId() != null
                        ? virtualMaterial.getDefaultWarehouseId() : 999L;
                inventoryService.stockIn(order.getVirtualMaterialId(), warehouseId,
                        order.getDeliveryQty(), "outsource_cancel", null,
                        "outsource_order", id);
                log.info("外协取消回冲虚拟库存: orderId={}, virtualMaterialId={}, qty={}",
                        id, order.getVirtualMaterialId(), order.getDeliveryQty());
            } catch (Exception e) {
                log.error("外协取消回冲库存失败: orderId={}", id, e);
            }
        }

        order.setStatus("cancelled");
        updateById(order);
        log.info("外协订单已取消: orderId={}", id);
    }

    /**
     * 从外协订单生成收料单（新流程）
     * @param items [{qty, batchNo}]
     */
    @Transactional(rollbackFor = Exception.class)
    public void generateReceipts(Long orderId, List<Map<String, Object>> items) {
        BizOutsourceOrder order = getById(orderId);
        if (order == null) throw new BusinessException("外协订单不存在");
        Set<String> allowedStatus = Set.of("confirmed", "processing", "partial_receipt");
        if (!allowedStatus.contains(order.getStatus())) throw new BusinessException("当前状态不允许生成收料单");
        if (items == null || items.isEmpty()) throw new BusinessException("收料明细不能为空");

        BigDecimal currentReceiptQty = order.getReceiptQty() != null ? order.getReceiptQty() : BigDecimal.ZERO;

        for (Map<String, Object> itemData : items) {
            BigDecimal qty = new BigDecimal(itemData.get("qty").toString());
            String batchNo = itemData.get("batchNo") != null ? itemData.get("batchNo").toString() : null;

            if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal remaining = order.getTotalQty().subtract(currentReceiptQty);
            if (qty.compareTo(remaining) > 0) {
                throw new BusinessException("收料数量超出剩余可收料数量");
            }

            BizOutsourceReceipt receipt = new BizOutsourceReceipt();
            receipt.setReceiptNo(com.aimes.common.utils.CodeUtil.generate("OR"));
            receipt.setOutsourceOrderId(orderId);
            receipt.setReceivedQty(qty);
            receipt.setStatus("pending_receive");
            receipt.setQcStatus("none");
            receipt.setQualifiedQty(BigDecimal.ZERO);
            receipt.setDefectQty(BigDecimal.ZERO);
            receipt.setInvTaskQty(BigDecimal.ZERO);
            receipt.setStockInQty(BigDecimal.ZERO);
            if (org.springframework.util.StringUtils.hasText(batchNo)) receipt.setBatchNo(batchNo);
            receiptMapper.insert(receipt);

            currentReceiptQty = currentReceiptQty.add(qty);
        }

        order.setReceiptQty(currentReceiptQty);
        // 更新订单状态
        if (currentReceiptQty.compareTo(order.getTotalQty()) >= 0) {
            order.setStatus("fully_receipt");
        } else if (currentReceiptQty.compareTo(BigDecimal.ZERO) > 0) {
            order.setStatus("partial_receipt");
        }
        updateById(order);
    }

    /**
     * 创建发货单（统一走库存任务）
     * 所有路径都创建 outsource_out 库存任务，由仓管员确认出库。
     */
    @Transactional(rollbackFor = Exception.class)
    public void createDelivery(Long orderId, BizOutsourceDelivery delivery, List<BizOutsourceDeliveryItem> items) {
        BizOutsourceOrder order = getById(orderId);
        if (order == null) throw new BusinessException("外协订单不存在");
        if (!"confirmed".equals(order.getStatus()) && !"processing".equals(order.getStatus())) {
            throw new BusinessException("只有已确认/加工中状态可以发料");
        }

        delivery.setDeliveryNo(CodeUtil.generate("OD"));
        delivery.setOutsourceOrderId(orderId);
        if (delivery.getStatus() == null) delivery.setStatus("pending");
        deliveryMapper.insert(delivery);

        BigDecimal deliveryQty = delivery.getDeliveryQty() != null ? delivery.getDeliveryQty() : BigDecimal.ZERO;
        List<Map<String, Object>> taskMaterials = new ArrayList<>();

        // ========== 路径A：整单外协（BOM展开多种原材料） ==========
        if ((items == null || items.isEmpty()) && order.getVirtualMaterialId() == null && order.getOrderId() != null) {
            if (deliveryQty.compareTo(BigDecimal.ZERO) <= 0) deliveryQty = order.getTotalQty();
            List<BizBomItem> bomItems = getBomItemsForProduct(order.getProductId());
            for (BizBomItem bomItem : bomItems) {
                BigDecimal requiredQty = bomItem.getActualQuantity() != null
                        ? bomItem.getActualQuantity() : bomItem.getQuantity();
                BigDecimal issueQty = requiredQty.multiply(deliveryQty);
                BizMaterial mat = materialMapper.selectById(bomItem.getMaterialId());
                Long warehouseId = mat != null && mat.getDefaultWarehouseId() != null
                        ? mat.getDefaultWarehouseId() : 999L;

                BizOutsourceDeliveryItem dItem = new BizOutsourceDeliveryItem();
                dItem.setDeliveryId(delivery.getId());
                dItem.setMaterialId(bomItem.getMaterialId());
                dItem.setShouldQty(issueQty);
                dItem.setActualQty(issueQty);
                dItem.setWarehouseId(warehouseId);
                deliveryItemMapper.insert(dItem);

                Map<String, Object> matMap = new LinkedHashMap<>();
                matMap.put("materialId", bomItem.getMaterialId());
                matMap.put("materialNo", mat != null ? mat.getMaterialNo() : null);
                matMap.put("materialName", mat != null ? mat.getMaterialName() : null);
                matMap.put("spec", mat != null ? mat.getSpec() : null);
                matMap.put("unit", mat != null ? mat.getUnit() : null);
                matMap.put("quantity", issueQty);
                matMap.put("warehouseId", warehouseId);
                taskMaterials.add(matMap);
            }
        }
        // ========== 路径B：工序外协（虚拟半成品，单种物料） ==========
        else if ((items == null || items.isEmpty()) && order.getVirtualMaterialId() != null) {
            if (deliveryQty.compareTo(BigDecimal.ZERO) <= 0) return;
            BizMaterial virtualMaterial = materialMapper.selectById(order.getVirtualMaterialId());
            Long warehouseId = 999L; // 默认外协中转仓

            BizOutsourceDeliveryItem dItem = new BizOutsourceDeliveryItem();
            dItem.setDeliveryId(delivery.getId());
            dItem.setMaterialId(order.getVirtualMaterialId());
            dItem.setShouldQty(deliveryQty);
            dItem.setActualQty(deliveryQty);
            dItem.setWarehouseId(warehouseId);
            deliveryItemMapper.insert(dItem);

            Map<String, Object> matMap = new LinkedHashMap<>();
            matMap.put("materialId", order.getVirtualMaterialId());
            matMap.put("materialNo", virtualMaterial != null ? virtualMaterial.getMaterialNo() : null);
            matMap.put("materialName", virtualMaterial != null ? virtualMaterial.getMaterialName() : null);
            matMap.put("spec", virtualMaterial != null ? virtualMaterial.getSpec() : null);
            matMap.put("unit", virtualMaterial != null ? virtualMaterial.getUnit() : null);
            matMap.put("quantity", deliveryQty);
            matMap.put("warehouseId", warehouseId);
            taskMaterials.add(matMap);
        }
        // ========== 路径C：手动传入物料明细 ==========
        else if (items != null && !items.isEmpty()) {
            for (BizOutsourceDeliveryItem item : items) {
                item.setDeliveryId(delivery.getId());
                if (item.getWarehouseId() == null) item.setWarehouseId(999L);
                deliveryItemMapper.insert(item);

                BizMaterial mat = item.getMaterialId() != null ? materialMapper.selectById(item.getMaterialId()) : null;
                Map<String, Object> matMap = new LinkedHashMap<>();
                matMap.put("materialId", item.getMaterialId());
                matMap.put("materialNo", mat != null ? mat.getMaterialNo() : null);
                matMap.put("materialName", mat != null ? mat.getMaterialName() : null);
                matMap.put("spec", mat != null ? mat.getSpec() : null);
                matMap.put("unit", mat != null ? mat.getUnit() : null);
                matMap.put("quantity", item.getActualQty());
                matMap.put("warehouseId", item.getWarehouseId());
                taskMaterials.add(matMap);
            }
            // 路径C: deliveryQty 已从 delivery.getDeliveryQty() 获取，不重复累加
        }

        // 创建 outsource_out 库存任务
        if (!taskMaterials.isEmpty()) {
            invTaskService.createFromOutsourceDelivery(
                    orderId, order.getOrderNo(), taskMaterials, SecurityUtil.getCurrentUserId());
        }

        // 更新订单发料任务数、发货数量、状态
        BigDecimal currentDeliveryQty = order.getDeliveryQty() != null ? order.getDeliveryQty() : BigDecimal.ZERO;
        order.setDeliveryQty(currentDeliveryQty.add(deliveryQty));
        BigDecimal currentTaskQty = order.getDeliveryTaskQty() != null ? order.getDeliveryTaskQty() : BigDecimal.ZERO;
        order.setDeliveryTaskQty(currentTaskQty.add(deliveryQty));
        if ("confirmed".equals(order.getStatus())) {
            order.setStatus("processing");
        }
        updateById(order);
    }

    /**
     * 创建收货单（统一走库存任务）
     * 创建 outsource_in 库存任务，由仓管处理后触发入库和完成逻辑。
     */
    @Transactional(rollbackFor = Exception.class)
    public void createReceipt(Long orderId, BizOutsourceReceipt receipt) {
        BizOutsourceOrder order = getById(orderId);
        if (order == null) throw new BusinessException("外协订单不存在");
        if (!"confirmed".equals(order.getStatus()) && !"processing".equals(order.getStatus())) {
            throw new BusinessException("只有已确认/加工中状态可以收货");
        }

        receipt.setReceiptNo(CodeUtil.generate("OC"));
        receipt.setOutsourceOrderId(orderId);
        receipt.setDefectQty(BigDecimal.ZERO);

        // 确定收货物料和仓库
        Long receiptMaterialId = order.getReceiptMaterialId() != null
                ? order.getReceiptMaterialId() : order.getProductId();
        Long warehouseId = receipt.getWarehouseId() != null ? receipt.getWarehouseId() : 999L;

        BizMaterial mat = materialMapper.selectById(receiptMaterialId);

        // 创建 outsource_in 库存任务
        Long taskId = invTaskService.createFromOutsourceReceipt(
                orderId, order.getOrderNo(),
                receiptMaterialId,
                mat != null ? mat.getMaterialNo() : null,
                mat != null ? mat.getMaterialName() : null,
                mat != null ? mat.getSpec() : null,
                mat != null ? mat.getUnit() : null,
                receipt.getReceivedQty(),
                warehouseId,
                SecurityUtil.getCurrentUserId());

        // 判断是否需要质检
        boolean needQc = false;
        if (order.getTaskId() != null) {
            BizWoProcessTask processTask = processTaskMapper.selectById(order.getTaskId());
            needQc = processTask != null && processTask.getQcTemplateId() != null;
        }

        if (needQc) {
            receipt.setQcStatus("pending");
            receipt.setQualifiedQty(BigDecimal.ZERO);
            receiptMapper.insert(receipt);

            BizQcTask qcTask = new BizQcTask();
            qcTask.setQcType("outsource");
            qcTask.setProductId(order.getProductId());
            qcTask.setPlanQty(receipt.getReceivedQty());
            qcTask.setRelatedOrderType("outsource_order");
            qcTask.setRelatedOrderId(orderId);
            qcTaskService.createTask(qcTask);
            receipt.setQcTaskId(qcTask.getId());
            receiptMapper.updateById(receipt);
            notificationService.notifyRole("quality", "外协质检任务",
                    "外协收货[" + receipt.getReceiptNo() + "]已到货, 请安排外协检验",
                    "approval", "qc_task", qcTask.getId());
        } else {
            receipt.setQcStatus("passed");
            receipt.setQualifiedQty(receipt.getReceivedQty());
            receiptMapper.insert(receipt);
        }

        // 更新收货任务数和收货数量
        BigDecimal newDeliveredQty = (order.getDeliveredQty() != null ? order.getDeliveredQty() : BigDecimal.ZERO)
                .add(receipt.getReceivedQty());
        order.setDeliveredQty(newDeliveredQty);
        BigDecimal currentReceiptTaskQty = order.getReceiptTaskQty() != null ? order.getReceiptTaskQty() : BigDecimal.ZERO;
        order.setReceiptTaskQty(currentReceiptTaskQty.add(receipt.getReceivedQty()));
        if ("confirmed".equals(order.getStatus())) {
            order.setStatus("processing");
        }
        updateById(order);
    }

    /**
     * 查询产品BOM展开到叶子层(支持多级BOM, manufacture 节点递归子件, purchase / 自制叶子节点直接返回)。
     * 单位用量已经累乘到叶子, 同一物料多分支出现会合并相加。
     */
    private List<BizBomItem> getBomItemsForProduct(Long productId) {
        BizBomVersion activeVersion = bomVersionMapper.selectOne(
                new LambdaQueryWrapper<BizBomVersion>()
                        .eq(BizBomVersion::getProductId, productId)
                        .eq(BizBomVersion::getStatus, "active")
                        .last("LIMIT 1"));
        if (activeVersion == null) return Collections.emptyList();

        List<BizBomItem> all = bomItemMapper.selectList(
                new LambdaQueryWrapper<BizBomItem>()
                        .eq(BizBomItem::getBomVersionId, activeVersion.getId())
                        .orderByAsc(BizBomItem::getSortOrder));
        if (all.isEmpty()) return Collections.emptyList();

        // 按 parentItemId 分组(NULL/0 视为顶层 key=0)
        Map<Long, List<BizBomItem>> childrenMap = new HashMap<>();
        for (BizBomItem it : all) {
            Long pid = (it.getParentItemId() == null || it.getParentItemId() == 0L) ? 0L : it.getParentItemId();
            childrenMap.computeIfAbsent(pid, k -> new ArrayList<>()).add(it);
        }

        // 递归: manufacture+有子件 跳过本级; 否则 (叶子且有 materialId) 累加用量
        Map<Long, java.math.BigDecimal> leafQty = new java.util.LinkedHashMap<>();
        Map<Long, BizBomItem> leafSample = new HashMap<>();
        java.util.function.BiConsumer<BizBomItem, java.math.BigDecimal> visit = new java.util.function.BiConsumer<>() {
            @Override
            public void accept(BizBomItem node, java.math.BigDecimal mul) {
                java.math.BigDecimal nodeQty = node.getActualQuantity() != null ? node.getActualQuantity()
                        : (node.getQuantity() != null ? node.getQuantity() : java.math.BigDecimal.ONE);
                java.math.BigDecimal cum = mul.multiply(nodeQty);
                List<BizBomItem> kids = childrenMap.get(node.getId());
                boolean isManufacture = "manufacture".equalsIgnoreCase(node.getSourceType());
                if (isManufacture && kids != null && !kids.isEmpty()) {
                    for (BizBomItem k : kids) accept(k, cum);
                } else if (node.getMaterialId() != null) {
                    leafQty.merge(node.getMaterialId(), cum, java.math.BigDecimal::add);
                    leafSample.putIfAbsent(node.getMaterialId(), node);
                }
            }
        };
        for (BizBomItem top : childrenMap.getOrDefault(0L, Collections.emptyList())) {
            visit.accept(top, java.math.BigDecimal.ONE);
        }

        // 输出: 复用 leafSample 的源行, 用累加量覆盖 actualQuantity
        List<BizBomItem> out = new ArrayList<>();
        for (var e : leafQty.entrySet()) {
            BizBomItem src = leafSample.get(e.getKey());
            BizBomItem copy = new BizBomItem();
            copy.setId(src.getId());
            copy.setBomVersionId(src.getBomVersionId());
            copy.setMaterialId(src.getMaterialId());
            copy.setSourceType(src.getSourceType());
            copy.setQuantity(e.getValue());
            copy.setActualQuantity(e.getValue());
            copy.setSortOrder(src.getSortOrder());
            out.add(copy);
        }
        return out;
    }
}
