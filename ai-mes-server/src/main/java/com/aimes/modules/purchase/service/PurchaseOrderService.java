package com.aimes.modules.purchase.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.PageResult;
import com.aimes.common.utils.CodeUtil;
import com.aimes.common.utils.SecurityUtil;
import com.aimes.modules.inventory.entity.BizInvRequest;
import com.aimes.modules.inventory.entity.BizInvRequestItem;
import com.aimes.modules.inventory.mapper.BizInvRequestItemMapper;
import com.aimes.modules.inventory.mapper.BizInvRequestMapper;
import com.aimes.modules.inventory.service.BizInvTaskService;
import com.aimes.modules.master.entity.BizMaterial;
import com.aimes.modules.master.entity.BizSupplier;
import com.aimes.modules.master.mapper.BizMaterialMapper;
import com.aimes.modules.master.mapper.BizSupplierMapper;
import com.aimes.modules.purchase.entity.BizPurchaseOrder;
import com.aimes.modules.purchase.entity.BizPurchaseOrderItem;
import com.aimes.modules.purchase.entity.BizPurchaseReceipt;
import com.aimes.modules.purchase.entity.BizPurchaseRequest;
import com.aimes.modules.purchase.entity.BizPurchaseReturnItem;
import com.aimes.modules.purchase.mapper.BizPurchaseOrderItemMapper;
import com.aimes.modules.purchase.mapper.BizPurchaseOrderMapper;
import com.aimes.modules.purchase.mapper.BizPurchaseReceiptMapper;
import com.aimes.modules.purchase.mapper.BizPurchaseRequestMapper;
import com.aimes.modules.quality.entity.BizQcTask;
import com.aimes.modules.quality.service.BizQcTaskService;
import com.aimes.modules.system.service.SysNotificationService;
import com.aimes.modules.system.service.SysBizConfigService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PurchaseOrderService extends ServiceImpl<BizPurchaseOrderMapper, BizPurchaseOrder> {

    private final BizPurchaseOrderItemMapper orderItemMapper;
    private final BizPurchaseReceiptMapper receiptMapper;
    private final BizSupplierMapper supplierMapper;
    private final BizMaterialMapper materialMapper;
    private final BizQcTaskService qcTaskService;
    private final SysNotificationService notificationService;
    private final BizPurchaseRequestMapper requestMapper;
    private final BizInvTaskService invTaskService;
    private final BizInvRequestMapper invRequestMapper;
    private final BizInvRequestItemMapper invRequestItemMapper;
    private final SysBizConfigService bizConfigService;
    private final org.springframework.context.ApplicationContext applicationContext;

    /**
     * 分页查询采购订单（支持复杂查询）
     */
    public PageResult<Map<String, Object>> getPage(String keyword, String status,
                                                    Long supplierId, String poNo,
                                                    String paymentTerms,
                                                    LocalDate deliveryDateStart, LocalDate deliveryDateEnd,
                                                    LocalDateTime createTimeStart, LocalDateTime createTimeEnd,
                                                    int pageNum, int pageSize) {
        LambdaQueryWrapper<BizPurchaseOrder> wrapper = new LambdaQueryWrapper<BizPurchaseOrder>()
                .like(StringUtils.hasText(keyword), BizPurchaseOrder::getPoNo, keyword)
                .like(StringUtils.hasText(poNo), BizPurchaseOrder::getPoNo, poNo)
                .eq(StringUtils.hasText(status), BizPurchaseOrder::getStatus, status)
                .eq(supplierId != null, BizPurchaseOrder::getSupplierId, supplierId)
                .eq(StringUtils.hasText(paymentTerms), BizPurchaseOrder::getPaymentTerms, paymentTerms)
                .ge(deliveryDateStart != null, BizPurchaseOrder::getDeliveryDate, deliveryDateStart)
                .le(deliveryDateEnd != null, BizPurchaseOrder::getDeliveryDate, deliveryDateEnd)
                .ge(createTimeStart != null, BizPurchaseOrder::getCreateTime, createTimeStart)
                .le(createTimeEnd != null, BizPurchaseOrder::getCreateTime, createTimeEnd)
                .orderByDesc(BizPurchaseOrder::getCreateTime);

        Page<BizPurchaseOrder> page = page(new Page<>(pageNum, pageSize), wrapper);

        // 批量加载供应商名称
        Set<Long> sids = page.getRecords().stream().map(BizPurchaseOrder::getSupplierId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> sNameMap = sids.isEmpty() ? new HashMap<>() :
                supplierMapper.selectBatchIds(sids).stream().collect(Collectors.toMap(BizSupplier::getId, BizSupplier::getSupplierName, (a, b) -> a));

        // 批量统计每张订单关联的采购申请数量
        Set<Long> pageOrderIds = page.getRecords().stream().map(BizPurchaseOrder::getId).collect(Collectors.toSet());
        Map<Long, Long> orderSourceCountMap = new HashMap<>();
        if (!pageOrderIds.isEmpty()) {
            List<BizPurchaseOrderItem> linkedItems = orderItemMapper.selectList(
                    new LambdaQueryWrapper<BizPurchaseOrderItem>()
                            .in(BizPurchaseOrderItem::getPurchaseOrderId, pageOrderIds)
                            .isNotNull(BizPurchaseOrderItem::getRequestId));
            orderSourceCountMap = linkedItems.stream()
                    .collect(Collectors.groupingBy(BizPurchaseOrderItem::getPurchaseOrderId, Collectors.counting()));
        }
        final Map<Long, Long> finalOrderSourceCountMap = orderSourceCountMap;

        // 批量查询采购退货状态
        var returnMapper = applicationContext.getBean(com.aimes.modules.purchase.mapper.BizPurchaseReturnMapper.class);
        var returnItemMapper = applicationContext.getBean(com.aimes.modules.purchase.mapper.BizPurchaseReturnItemMapper.class);
        Map<Long, String> returnStatusMap = new HashMap<>();
        if (!pageOrderIds.isEmpty()) {
            List<com.aimes.modules.purchase.entity.BizPurchaseReturn> returns = returnMapper.selectList(
                    new LambdaQueryWrapper<com.aimes.modules.purchase.entity.BizPurchaseReturn>()
                            .in(com.aimes.modules.purchase.entity.BizPurchaseReturn::getPurchaseOrderId, pageOrderIds));
            // 按采购订单ID分组
            Map<Long, List<com.aimes.modules.purchase.entity.BizPurchaseReturn>> returnsByOrder = returns.stream()
                    .collect(Collectors.groupingBy(com.aimes.modules.purchase.entity.BizPurchaseReturn::getPurchaseOrderId));
            // 查询退货明细
            Set<Long> returnIds = returns.stream().map(com.aimes.modules.purchase.entity.BizPurchaseReturn::getId).collect(Collectors.toSet());
            Map<Long, BigDecimal> returnTotalQtyByOrder = new HashMap<>();
            if (!returnIds.isEmpty()) {
                List<BizPurchaseReturnItem> returnItems = returnItemMapper.selectList(
                        new LambdaQueryWrapper<BizPurchaseReturnItem>()
                                .in(BizPurchaseReturnItem::getReturnId, returnIds));
                // 关联returnId→orderId，汇总已完成退货的数量
                Map<Long, Long> returnIdToOrderId = returns.stream()
                        .collect(Collectors.toMap(r -> r.getId(), r -> r.getPurchaseOrderId(), (a, b) -> a));
                for (BizPurchaseReturnItem ri : returnItems) {
                    Long ordId = returnIdToOrderId.get(ri.getReturnId());
                    if (ordId != null) {
                        returnTotalQtyByOrder.merge(ordId, ri.getQuantity() != null ? ri.getQuantity() : BigDecimal.ZERO, BigDecimal::add);
                    }
                }
            }
            for (Long ordId : pageOrderIds) {
                List<com.aimes.modules.purchase.entity.BizPurchaseReturn> orderReturns = returnsByOrder.get(ordId);
                if (orderReturns == null || orderReturns.isEmpty()) {
                    returnStatusMap.put(ordId, "no_return");
                } else {
                    boolean hasCompleted = orderReturns.stream().anyMatch(r -> "completed".equals(r.getStatus()));
                    boolean allCompleted = orderReturns.stream().allMatch(r -> "completed".equals(r.getStatus()) || "rejected".equals(r.getStatus()));
                    if (hasCompleted) {
                        // 比较退货总量与订单总量
                        BigDecimal returnedQty = returnTotalQtyByOrder.getOrDefault(ordId, BigDecimal.ZERO);
                        // 获取订单总量
                        List<BizPurchaseOrderItem> orderItems = orderItemMapper.selectList(
                                new LambdaQueryWrapper<BizPurchaseOrderItem>()
                                        .eq(BizPurchaseOrderItem::getPurchaseOrderId, ordId));
                        BigDecimal orderTotalQty = orderItems.stream()
                                .map(i -> i.getQuantity() != null ? i.getQuantity() : BigDecimal.ZERO)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                        if (returnedQty.compareTo(orderTotalQty) >= 0) {
                            returnStatusMap.put(ordId, "full_return");
                        } else {
                            returnStatusMap.put(ordId, "partial_return");
                        }
                    } else {
                        returnStatusMap.put(ordId, "returning");
                    }
                }
            }
        }

        // 批量计算每个订单"是否还有可生成收料单的明细"(quantity > receiptQty)
        Map<Long, Boolean> receiptableMap = new HashMap<>();
        if (!pageOrderIds.isEmpty()) {
            List<BizPurchaseOrderItem> allPageItems = orderItemMapper.selectList(
                    new LambdaQueryWrapper<BizPurchaseOrderItem>()
                            .in(BizPurchaseOrderItem::getPurchaseOrderId, pageOrderIds));
            Map<Long, List<BizPurchaseOrderItem>> itemsByOrder = allPageItems.stream()
                    .collect(Collectors.groupingBy(BizPurchaseOrderItem::getPurchaseOrderId));
            for (Long oid : pageOrderIds) {
                List<BizPurchaseOrderItem> ois = itemsByOrder.getOrDefault(oid, List.of());
                boolean any = ois.stream().anyMatch(oi ->
                        (oi.getQuantity() != null ? oi.getQuantity() : BigDecimal.ZERO)
                                .compareTo(oi.getReceiptQty() != null ? oi.getReceiptQty() : BigDecimal.ZERO) > 0);
                receiptableMap.put(oid, any);
            }
        }

        List<Map<String, Object>> list = page.getRecords().stream().map(o -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", o.getId());
            m.put("poNo", o.getPoNo());
            m.put("supplierId", o.getSupplierId());
            m.put("supplierName", sNameMap.getOrDefault(o.getSupplierId(), ""));
            m.put("contactName", o.getContactName());
            m.put("totalAmount", o.getTotalAmount());
            m.put("deliveryDate", o.getDeliveryDate());
            m.put("paymentTerms", o.getPaymentTerms());
            m.put("status", o.getStatus());
            m.put("invTaskGenStatus", o.getInvTaskGenStatus());
            m.put("invTaskProcessStatus", o.getInvTaskProcessStatus());
            m.put("approvedBy", o.getApprovedBy());
            m.put("approvedAt", o.getApprovedAt());
            m.put("approveRemark", o.getApproveRemark());
            m.put("remark", o.getRemark());
            // 扩展字段(列表展示扩展列 + 编辑回填用)
            m.put("extFields", o.getExtFields());
            m.put("createTime", o.getCreateTime());
            m.put("sourceRequestCount", finalOrderSourceCountMap.getOrDefault(o.getId(), 0L));
            m.put("returnStatus", returnStatusMap.getOrDefault(o.getId(), "no_return"));
            // 还有未生成收料单的明细行 → true;全部已生成 → false
            m.put("receiptable", receiptableMap.getOrDefault(o.getId(), false));
            return m;
        }).collect(Collectors.toList());

        return new PageResult<>(list, page.getTotal(), pageNum, pageSize);
    }

    /**
     * 订单详情（含明细items、收料记录、来源申请）
     */
    public Map<String, Object> getDetail(Long id) {
        BizPurchaseOrder order = getById(id);
        if (order == null) {
            throw new BusinessException("采购订单不存在");
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", order.getId());
        detail.put("poNo", order.getPoNo());
        detail.put("supplierId", order.getSupplierId());
        if (order.getSupplierId() != null) {
            BizSupplier supplier = supplierMapper.selectById(order.getSupplierId());
            detail.put("supplierName", supplier != null ? supplier.getSupplierName() : "");
        }
        detail.put("contactName", order.getContactName());
        detail.put("totalAmount", order.getTotalAmount());
        detail.put("deliveryDate", order.getDeliveryDate());
        detail.put("paymentTerms", order.getPaymentTerms());
        detail.put("status", order.getStatus());
        detail.put("invTaskGenStatus", order.getInvTaskGenStatus());
        detail.put("invTaskProcessStatus", order.getInvTaskProcessStatus());
        detail.put("invReturnReason", order.getInvReturnReason());
        detail.put("approvedBy", order.getApprovedBy());
        detail.put("approvedAt", order.getApprovedAt());
        detail.put("approveRemark", order.getApproveRemark());
        detail.put("remark", order.getRemark());
        detail.put("attachmentUrls", order.getAttachmentUrls());
        // 扩展字段(编辑表单回填用)
        detail.put("extFields", order.getExtFields());
        detail.put("createTime", order.getCreateTime());

        // 明细列表（附带物料信息）
        List<BizPurchaseOrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<BizPurchaseOrderItem>()
                        .eq(BizPurchaseOrderItem::getPurchaseOrderId, id)
                        .orderByAsc(BizPurchaseOrderItem::getId));

        Set<Long> itemMaterialIds = items.stream().map(BizPurchaseOrderItem::getMaterialId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, BizMaterial> itemMaterialMap = itemMaterialIds.isEmpty() ? new HashMap<>() :
                materialMapper.selectBatchIds(itemMaterialIds).stream().collect(Collectors.toMap(BizMaterial::getId, m -> m, (a, b) -> a));

        List<Map<String, Object>> itemMaps = items.stream().map(item -> {
            Map<String, Object> m = new LinkedHashMap<>();
            BizMaterial mat = itemMaterialMap.get(item.getMaterialId());
            m.put("id", item.getId());
            m.put("purchaseOrderId", item.getPurchaseOrderId());
            m.put("materialId", item.getMaterialId());
            m.put("materialNo", mat != null ? mat.getMaterialNo() : "");
            m.put("materialName", mat != null ? mat.getMaterialName() : "");
            m.put("spec", mat != null ? mat.getSpec() : "");
            m.put("unit", mat != null ? mat.getUnit() : "");
            m.put("quantity", item.getQuantity());
            m.put("unitPrice", item.getUnitPrice());
            m.put("amount", item.getAmount());
            m.put("receiptQty", item.getReceiptQty());
            m.put("invTaskQty", item.getInvTaskQty());
            m.put("stockInQty", item.getStockInQty());
            m.put("qualifiedQty", item.getQualifiedQty());
            m.put("returnInitiatedQty", item.getReturnInitiatedQty());
            m.put("returnOutQty", item.getReturnOutQty());
            m.put("requestId", item.getRequestId());
            m.put("remark", item.getRemark());
            m.put("unitConfig", mat != null ? mat.getUnitConfig() : null);
            return m;
        }).collect(Collectors.toList());
        detail.put("items", itemMaps);

        // 收料记录
        List<BizPurchaseReceipt> receipts = receiptMapper.selectList(
                new LambdaQueryWrapper<BizPurchaseReceipt>()
                        .eq(BizPurchaseReceipt::getPurchaseOrderId, id)
                        .orderByDesc(BizPurchaseReceipt::getCreateTime));
        detail.put("receipts", receipts);

        // 来源申请（功能8）
        Set<Long> requestIds = items.stream()
                .map(BizPurchaseOrderItem::getRequestId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!requestIds.isEmpty()) {
            List<BizPurchaseRequest> sourceRequests = requestMapper.selectBatchIds(requestIds);
            // 批量加载物料信息，作为 materialName/spec/unit 的兜底来源
            Set<Long> reqMaterialIds = sourceRequests.stream()
                    .map(BizPurchaseRequest::getMaterialId).filter(Objects::nonNull).collect(Collectors.toSet());
            Map<Long, BizMaterial> reqMaterialMap = reqMaterialIds.isEmpty() ? new HashMap<>() :
                    materialMapper.selectBatchIds(reqMaterialIds).stream()
                            .collect(Collectors.toMap(BizMaterial::getId, m -> m, (a, b) -> a));
            List<Map<String, Object>> sourceList = sourceRequests.stream().map(r -> {
                BizMaterial mat = reqMaterialMap.get(r.getMaterialId());
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("requestId", r.getId());
                m.put("requestNo", r.getRequestNo());
                m.put("materialId", r.getMaterialId());
                m.put("materialNo", mat != null ? mat.getMaterialNo() : "");
                m.put("materialName", StringUtils.hasText(r.getMaterialName()) ? r.getMaterialName() : (mat != null ? mat.getMaterialName() : ""));
                m.put("spec", StringUtils.hasText(r.getSpec()) ? r.getSpec() : (mat != null ? mat.getSpec() : ""));
                m.put("unit", StringUtils.hasText(r.getUnit()) ? r.getUnit() : (mat != null ? mat.getUnit() : ""));
                m.put("quantity", r.getQuantity());
                m.put("approvedAt", r.getApprovedAt());
                return m;
            }).collect(Collectors.toList());
            detail.put("sourceRequests", sourceList);
        } else {
            detail.put("sourceRequests", Collections.emptyList());
        }

        return detail;
    }

    /**
     * 通过订单编号查询详情（供关联单据跳转使用）
     */
    public Map<String, Object> getDetailByPoNo(String poNo) {
        BizPurchaseOrder order = getOne(new LambdaQueryWrapper<BizPurchaseOrder>()
                .eq(BizPurchaseOrder::getPoNo, poNo).last("LIMIT 1"));
        // 兜底：如果传进来的是收料单号(PR开头)，通过收料单反查关联的采购订单
        if (order == null) {
            BizPurchaseReceipt receipt = receiptMapper.selectOne(
                    new LambdaQueryWrapper<BizPurchaseReceipt>()
                            .eq(BizPurchaseReceipt::getReceiptNo, poNo).last("LIMIT 1"));
            if (receipt != null && receipt.getPurchaseOrderId() != null) {
                order = getById(receipt.getPurchaseOrderId());
            }
        }
        if (order == null) throw new BusinessException("采购订单不存在: " + poNo);
        return getDetail(order.getId());
    }

    /**
     * 创建采购订单
     */
    @Transactional(rollbackFor = Exception.class)
    public void create(BizPurchaseOrder order, List<BizPurchaseOrderItem> items) {
        // 根据配置决定是否自动生成订单号
        boolean autoNo = bizConfigService.getBoolValue("purchase.order.auto_no", true);
        if (StringUtils.hasText(order.getPoNo())) {
            long count = count(new LambdaQueryWrapper<BizPurchaseOrder>()
                    .eq(BizPurchaseOrder::getPoNo, order.getPoNo().trim()));
            if (count > 0) throw new BusinessException("采购订单号[" + order.getPoNo() + "]已存在，请使用其他单号");
            order.setPoNo(order.getPoNo().trim());
        } else if (autoNo) {
            order.setPoNo(CodeUtil.generate("PO"));
        } else {
            throw new BusinessException("系统配置要求手动填写采购订单号，请输入订单号");
        }
        order.setStatus("draft");

        // 单价为含税单价, amount = qty * unitPrice 即含税总价
        BigDecimal totalAmount = BigDecimal.ZERO;
        if (items != null) {
            for (BizPurchaseOrderItem item : items) {
                item.setAmount(computeTaxIncludedAmount(item.getQuantity(), item.getUnitPrice(), item.getTaxRate()));
                item.setInvTaskQty(BigDecimal.ZERO);
                item.setStockInQty(BigDecimal.ZERO);
                item.setQualifiedQty(BigDecimal.ZERO);
                totalAmount = totalAmount.add(item.getAmount());
            }
        }
        order.setTotalAmount(totalAmount);
        save(order);

        if (items != null) {
            for (BizPurchaseOrderItem item : items) {
                item.setPurchaseOrderId(order.getId());
                orderItemMapper.insert(item);
            }
        }
    }

    /**
     * 编辑采购订单（仅草稿状态可编辑，重建明细）
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateOrder(Long id, BizPurchaseOrder order, List<BizPurchaseOrderItem> items) {
        BizPurchaseOrder existing = getById(id);
        if (existing == null) throw new BusinessException("采购订单不存在");
        if (!"draft".equals(existing.getStatus())) throw new BusinessException("只有草稿状态的订单可以编辑");

        if (order.getSupplierId() != null) existing.setSupplierId(order.getSupplierId());
        if (order.getContactName() != null) existing.setContactName(order.getContactName());
        existing.setDeliveryDate(order.getDeliveryDate());
        existing.setPaymentTerms(order.getPaymentTerms());
        existing.setRemark(order.getRemark());
        existing.setAttachmentUrls(order.getAttachmentUrls());
        // 扩展字段(允许编辑时修改)
        if (order.getExtFields() != null) existing.setExtFields(order.getExtFields());

        // 重建订单明细
        orderItemMapper.delete(new LambdaQueryWrapper<BizPurchaseOrderItem>()
                .eq(BizPurchaseOrderItem::getPurchaseOrderId, id));
        // 单价为含税单价, amount = qty * unitPrice 即含税总价
        BigDecimal totalAmount = BigDecimal.ZERO;
        if (items != null) {
            for (BizPurchaseOrderItem item : items) {
                item.setPurchaseOrderId(id);
                item.setAmount(computeTaxIncludedAmount(item.getQuantity(), item.getUnitPrice(), item.getTaxRate()));
                item.setInvTaskQty(BigDecimal.ZERO);
                item.setStockInQty(BigDecimal.ZERO);
                item.setQualifiedQty(BigDecimal.ZERO);
                orderItemMapper.insert(item);
                totalAmount = totalAmount.add(item.getAmount());
            }
        }
        existing.setTotalAmount(totalAmount);
        updateById(existing);
    }

    /**
     * 单价为含税单价, 含税总价 = qty * unitPrice
     * taxRate 仅用于派生不含税口径(展示),不参与含税总价计算
     */
    private static BigDecimal computeTaxIncludedAmount(BigDecimal qty, BigDecimal unitPrice, BigDecimal taxRate) {
        if (qty == null || unitPrice == null) return BigDecimal.ZERO;
        return qty.multiply(unitPrice).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * 添加订单明细
     */
    @Transactional(rollbackFor = Exception.class)
    public void addItem(Long orderId, BizPurchaseOrderItem item) {
        BizPurchaseOrder order = getById(orderId);
        if (order == null) {
            throw new BusinessException("采购订单不存在");
        }
        if (!"draft".equals(order.getStatus())) {
            throw new BusinessException("只有草稿状态的订单可以添加明细");
        }

        item.setPurchaseOrderId(orderId);
        item.setAmount(computeTaxIncludedAmount(item.getQuantity(), item.getUnitPrice(), item.getTaxRate()));
        item.setInvTaskQty(BigDecimal.ZERO);
        item.setStockInQty(BigDecimal.ZERO);
        item.setQualifiedQty(BigDecimal.ZERO);
        orderItemMapper.insert(item);

        recalculateTotalAmount(orderId);
    }

    /**
     * 提交审批（draft/rejected → pending_approval）
     */
    public void submit(Long id) {
        BizPurchaseOrder order = getById(id);
        if (order == null) {
            throw new BusinessException("采购订单不存在");
        }
        if (!"draft".equals(order.getStatus()) && !"rejected".equals(order.getStatus())) {
            throw new BusinessException("只有草稿或已驳回的订单可以提交审批");
        }
        // 草稿可以没供应商, 但提交审批前必须填上 (订单主计划自动生成的草稿就是这种场景)
        if (order.getSupplierId() == null) {
            throw new BusinessException("提交审批前请先选择供应商");
        }

        long itemCount = orderItemMapper.selectCount(
                new LambdaQueryWrapper<BizPurchaseOrderItem>()
                        .eq(BizPurchaseOrderItem::getPurchaseOrderId, id));
        if (itemCount == 0) {
            throw new BusinessException("采购订单至少需要一条明细");
        }

        order.setStatus("pending_approval");
        updateById(order);
    }

    /**
     * 审批通过（pending_approval → confirmed）
     */
    public void approve(Long id, String remark) {
        BizPurchaseOrder order = getById(id);
        if (order == null) {
            throw new BusinessException("采购订单不存在");
        }
        if (!"pending_approval".equals(order.getStatus())) {
            throw new BusinessException("只有待审批的订单可以审批");
        }
        order.setStatus("confirmed");
        order.setApprovedBy(SecurityUtil.getCurrentUserId());
        order.setApprovedAt(LocalDateTime.now());
        order.setApproveRemark(remark);
        updateById(order);
    }

    /**
     * 审批驳回（pending_approval → rejected），同时将关联的已下发采购申请回退为已批准
     */
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String remark) {
        BizPurchaseOrder order = getById(id);
        if (order == null) {
            throw new BusinessException("采购订单不存在");
        }
        if (!"pending_approval".equals(order.getStatus())) {
            throw new BusinessException("只有待审批的订单可以驳回");
        }
        order.setStatus("rejected");
        order.setApprovedBy(SecurityUtil.getCurrentUserId());
        order.setApprovedAt(LocalDateTime.now());
        order.setApproveRemark(remark);
        updateById(order);

        // 将关联的已下发采购申请回退为已批准，允许重新下发
        List<BizPurchaseOrderItem> linkedItems = orderItemMapper.selectList(
                new LambdaQueryWrapper<BizPurchaseOrderItem>()
                        .eq(BizPurchaseOrderItem::getPurchaseOrderId, id)
                        .isNotNull(BizPurchaseOrderItem::getRequestId));
        if (!linkedItems.isEmpty()) {
            Set<Long> linkedRequestIds = linkedItems.stream()
                    .map(BizPurchaseOrderItem::getRequestId)
                    .collect(Collectors.toSet());
            List<BizPurchaseRequest> linkedRequests = requestMapper.selectBatchIds(linkedRequestIds);
            linkedRequests.stream()
                    .filter(pr -> "issued".equals(pr.getStatus()))
                    .forEach(pr -> {
                        pr.setStatus("approved");
                        requestMapper.updateById(pr);
                    });
        }
    }

    /**
     * 历史采购价格查询（功能4）
     */
    public List<Map<String, Object>> getPriceHistory(Long materialId) {
        if (materialId == null) return Collections.emptyList();

        List<BizPurchaseOrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<BizPurchaseOrderItem>()
                        .eq(BizPurchaseOrderItem::getMaterialId, materialId)
                        .orderByDesc(BizPurchaseOrderItem::getCreateTime)
                        .last("LIMIT 20"));

        if (items.isEmpty()) return Collections.emptyList();

        Set<Long> orderIds = items.stream().map(BizPurchaseOrderItem::getPurchaseOrderId).collect(Collectors.toSet());
        Map<Long, BizPurchaseOrder> orderMap = listByIds(orderIds).stream()
                .collect(Collectors.toMap(BizPurchaseOrder::getId, o -> o, (a, b) -> a));

        Set<Long> sids = orderMap.values().stream().map(BizPurchaseOrder::getSupplierId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> sNameMap = sids.isEmpty() ? new HashMap<>() :
                supplierMapper.selectBatchIds(sids).stream().collect(Collectors.toMap(BizSupplier::getId, BizSupplier::getSupplierName, (a, b) -> a));

        return items.stream()
                .filter(item -> {
                    BizPurchaseOrder o = orderMap.get(item.getPurchaseOrderId());
                    return o != null && List.of("confirmed", "closed").contains(o.getStatus());
                })
                .map(item -> {
                    BizPurchaseOrder o = orderMap.get(item.getPurchaseOrderId());
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("poNo", o != null ? o.getPoNo() : "");
                    m.put("supplierId", o != null ? o.getSupplierId() : null);
                    m.put("supplierName", o != null ? sNameMap.getOrDefault(o.getSupplierId(), "") : "");
                    m.put("unitPrice", item.getUnitPrice());
                    m.put("quantity", item.getQuantity());
                    m.put("createTime", item.getCreateTime());
                    return m;
                }).collect(Collectors.toList());
    }

    /**
     * 创建收料单
     */
    @Transactional(rollbackFor = Exception.class)
    public void createReceipt(Long orderId, BizPurchaseReceipt receipt) {
        BizPurchaseOrder order = getById(orderId);
        if (order == null) {
            throw new BusinessException("采购订单不存在");
        }

        receipt.setReceiptNo(CodeUtil.generate("PC"));
        receipt.setPurchaseOrderId(orderId);
        receipt.setQualifiedQty(BigDecimal.ZERO);
        receipt.setDefectQty(BigDecimal.ZERO);

        // 检查超额收料比例
        int overRatio = 0;
        try { overRatio = Integer.parseInt(bizConfigService.getValue("purchase.receipt.over_ratio")); } catch (Exception ignored) {}
        if (receipt.getOrderItemId() != null) {
            BizPurchaseOrderItem checkItem = orderItemMapper.selectById(receipt.getOrderItemId());
            if (checkItem != null) {
                BigDecimal alreadyReceived = checkItem.getInvTaskQty() != null ? checkItem.getInvTaskQty() : BigDecimal.ZERO;
                // 不允许部分到货时：本次收料量必须覆盖订单剩余未收量
                boolean allowPartial = bizConfigService.getBoolValue("purchase.receipt.allow_partial", true);
                if (!allowPartial) {
                    BigDecimal remaining = checkItem.getQuantity().subtract(alreadyReceived);
                    if (receipt.getActualQty().compareTo(remaining) < 0) {
                        throw new BusinessException("系统配置不允许部分到货，本次收料量(" + receipt.getActualQty()
                                + ")须等于订单剩余未收量(" + remaining + ")");
                    }
                }
                if (overRatio >= 0) {
                    BigDecimal maxAllowed = checkItem.getQuantity()
                            .multiply(BigDecimal.valueOf(1 + overRatio / 100.0));
                    if (alreadyReceived.add(receipt.getActualQty()).compareTo(maxAllowed) > 0) {
                        throw new BusinessException("收料数量超出允许范围，订单量: " + checkItem.getQuantity()
                                + "，允许超收比例: " + overRatio + "%，最大可收: " + maxAllowed);
                    }
                }
            }
        }

        // 判断是否需要来料检验（quality.incoming.enable 或 purchase.receipt.need_inspection）
        boolean needInspection = bizConfigService.getBoolValue("quality.incoming.enable", false)
                || bizConfigService.getBoolValue("purchase.receipt.need_inspection", false);
        if (needInspection) {
            receipt.setQcStatus("pending");
        } else {
            receipt.setQcStatus("passed");
        }
        receiptMapper.insert(receipt);

        // 按配置决定是否创建来料检任务
        if (needInspection) {
            BizQcTask qcTask = new BizQcTask();
            qcTask.setQcType("incoming");
            qcTask.setMaterialId(receipt.getMaterialId());
            qcTask.setBatchNo(receipt.getBatchNo());
            qcTask.setPlanQty(receipt.getActualQty());
            qcTask.setRelatedOrderType("purchase_order");
            qcTask.setRelatedOrderId(orderId);
            qcTaskService.createTask(qcTask);
            receipt.setQcTaskId(qcTask.getId());
            receiptMapper.updateById(receipt);
            notificationService.notifyRole("quality", "来料检任务",
                    "采购收料[" + receipt.getReceiptNo() + "]已到货, 请安排来料检验",
                    "approval", "qc_task", qcTask.getId());
        }

        // 更新明细库存任务数量
        BizPurchaseOrderItem orderItem = null;
        if (receipt.getOrderItemId() != null) {
            orderItem = orderItemMapper.selectById(receipt.getOrderItemId());
            if (orderItem != null) {
                orderItem.setInvTaskQty((orderItem.getInvTaskQty() != null ? orderItem.getInvTaskQty() : BigDecimal.ZERO).add(receipt.getActualQty()));
                orderItemMapper.updateById(orderItem);
            }
        }

        // 创建采购入库库存任务
        BizMaterial material = receipt.getMaterialId() != null
                ? materialMapper.selectById(receipt.getMaterialId()) : null;
        invTaskService.createFromPurchaseReceipt(
                receipt.getId(), receipt.getReceiptNo(), order.getPoNo(), receipt.getBatchNo(),
                receipt.getWarehouseId(), SecurityUtil.getCurrentUserId(),
                receipt.getMaterialId(),
                material != null ? material.getMaterialNo() : null,
                material != null ? material.getMaterialName() : null,
                material != null ? material.getSpec() : null,
                material != null ? material.getUnit() : null,
                receipt.getActualQty(),
                orderItem != null ? orderItem.getUnitPrice() : null);

        // 更新库存任务生成状态（PO状态不变，保持confirmed）
        updateInvTaskGenStatus(orderId);

        // 应付账款已改为在库存任务处理(process)时按实际入库数量*单价生成，此处不再自动创建
    }

    /**
     * 批量收料：一次操作多个物料，生成一条多物料 inv_task
     * items 每个 map 包含: orderItemId, materialId, actualQty, batchNo, warehouseId, locationId
     */
    @Transactional(rollbackFor = Exception.class)
    public void createBatchReceipts(Long orderId, List<Map<String, Object>> items) {
        BizPurchaseOrder order = getById(orderId);
        if (order == null) throw new BusinessException("采购订单不存在");

        List<Map<String, Object>> invTaskItems = new ArrayList<>();
        // 未手动填写批次号时，同一批量收料操作共用一个自动生成的批次号
        String sharedBatchNo = null;
        // 收料组 ID: 同一次批量收料的所有 receipt 共享, 用于列表折叠/打印对账单
        String receiptGroupId = CodeUtil.generate("RG");

        for (Map<String, Object> itemData : items) {
            if (itemData.get("actualQty") == null) continue;
            BigDecimal actualQty = new BigDecimal(itemData.get("actualQty").toString());
            if (actualQty.compareTo(BigDecimal.ZERO) <= 0) continue;

            BizPurchaseReceipt receipt = new BizPurchaseReceipt();
            receipt.setReceiptNo(CodeUtil.generate("PC"));
            receipt.setReceiptGroupId(receiptGroupId);
            receipt.setPurchaseOrderId(orderId);
            receipt.setQcStatus("pending");
            receipt.setQualifiedQty(BigDecimal.ZERO);
            receipt.setDefectQty(BigDecimal.ZERO);
            receipt.setActualQty(actualQty);
            if (itemData.get("orderItemId") != null) receipt.setOrderItemId(Long.valueOf(itemData.get("orderItemId").toString()));
            if (itemData.get("materialId") != null) receipt.setMaterialId(Long.valueOf(itemData.get("materialId").toString()));
            // 批次号：手动填写则直接用，否则整批共用自动生成的批次号
            String itemBatchNo = (itemData.get("batchNo") != null) ? itemData.get("batchNo").toString().trim() : "";
            if (itemBatchNo.isEmpty()) {
                if (sharedBatchNo == null) {
                    sharedBatchNo = invTaskService.generateInboundBatchNo();
                }
                itemBatchNo = sharedBatchNo;
            }
            receipt.setBatchNo(itemBatchNo);
            if (itemData.get("warehouseId") != null) receipt.setWarehouseId(Long.valueOf(itemData.get("warehouseId").toString()));
            receiptMapper.insert(receipt);

            // 自动创建来料检任务
            BizQcTask qcTask = new BizQcTask();
            qcTask.setQcType("incoming");
            qcTask.setMaterialId(receipt.getMaterialId());
            qcTask.setBatchNo(receipt.getBatchNo());
            qcTask.setPlanQty(actualQty);
            qcTask.setRelatedOrderType("purchase_order");
            qcTask.setRelatedOrderId(orderId);
            qcTaskService.createTask(qcTask);
            receipt.setQcTaskId(qcTask.getId());
            receiptMapper.updateById(receipt);

            // 更新明细库存任务数量
            BizPurchaseOrderItem orderItem = null;
            if (receipt.getOrderItemId() != null) {
                orderItem = orderItemMapper.selectById(receipt.getOrderItemId());
                if (orderItem != null) {
                    orderItem.setInvTaskQty((orderItem.getInvTaskQty() != null ? orderItem.getInvTaskQty() : BigDecimal.ZERO).add(actualQty));
                    orderItemMapper.updateById(orderItem);
                }
            }

            // 查询物料信息
            BizMaterial material = receipt.getMaterialId() != null
                    ? materialMapper.selectById(receipt.getMaterialId()) : null;
            Long warehouseId = receipt.getWarehouseId() != null ? receipt.getWarehouseId()
                    : (material != null && material.getDefaultWarehouseId() != null ? material.getDefaultWarehouseId() : null);

            Map<String, Object> ti = new LinkedHashMap<>();
            ti.put("receiptId", receipt.getId());
            ti.put("batchNo", itemBatchNo);
            ti.put("materialId", receipt.getMaterialId());
            ti.put("materialNo", material != null ? material.getMaterialNo() : null);
            ti.put("materialName", material != null ? material.getMaterialName() : null);
            ti.put("spec", material != null ? material.getSpec() : null);
            ti.put("unit", material != null ? material.getUnit() : null);
            ti.put("quantity", actualQty);
            ti.put("unitPrice", orderItem != null ? orderItem.getUnitPrice() : null);
            ti.put("warehouseId", warehouseId);
            ti.put("locationId", itemData.get("locationId"));
            invTaskItems.add(ti);

            notificationService.notifyRole("quality", "来料检任务",
                    "采购收料[" + receipt.getReceiptNo() + "]已到货, 请安排来料检验",
                    "approval", "qc_task", qcTask.getId());
        }

        // 创建一条多物料 inv_task
        if (!invTaskItems.isEmpty()) {
            invTaskService.createBatchReceiptsTask(orderId, order.getPoNo(),
                    SecurityUtil.getCurrentUserId(), invTaskItems);
        }

        // 更新库存任务生成状态（PO状态不变，保持confirmed）
        updateInvTaskGenStatus(orderId);

        // 应付账款已改为在库存任务处理(process)时按实际入库数量*单价生成，此处不再自动创建
    }

    private void recalculateTotalAmount(Long orderId) {
        List<BizPurchaseOrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<BizPurchaseOrderItem>()
                        .eq(BizPurchaseOrderItem::getPurchaseOrderId, orderId));
        BigDecimal totalAmount = items.stream()
                .map(item -> item.getAmount() != null ? item.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BizPurchaseOrder order = getById(orderId);
        order.setTotalAmount(totalAmount);
        updateById(order);
    }

    /**
     * 从采购订单创建入库申请。
     * 批量场景下，不符合条件时静默跳过（不抛异常）：
     *   - 订单状态不是 confirmed
     *   - 所有明细剩余数量均为 0（已全部申请过）
     * 唯一性检查：仅阻止 pending / approved 状态的重复申请；
     *   task_created 视为已处理，允许针对剩余数量再次申请。
     */
    @Transactional(rollbackFor = Exception.class)
    public void createInvRequestFromPO(Long orderId) {
        BizPurchaseOrder order = getById(orderId);
        if (order == null) return; // 静默跳过
        // 状态不符合则静默跳过
        if (!"confirmed".equals(order.getStatus())) return;

        // 已存在 pending 或 approved 的入库申请，静默跳过（批量场景不报错）
        long existCount = invRequestMapper.selectCount(
                new LambdaQueryWrapper<BizInvRequest>()
                        .eq(BizInvRequest::getRelatedOrderNo, order.getPoNo())
                        .eq(BizInvRequest::getInvType, "purchase_in")
                        .in(BizInvRequest::getStatus, "pending", "approved"));
        if (existCount > 0) return;

        List<BizPurchaseOrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<BizPurchaseOrderItem>()
                        .eq(BizPurchaseOrderItem::getPurchaseOrderId, orderId));
        if (items.isEmpty()) return; // 无明细，静默跳过

        // 批量加载物料信息
        Set<Long> matIds = items.stream().map(BizPurchaseOrderItem::getMaterialId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, BizMaterial> matMap = matIds.isEmpty() ? new HashMap<>() :
                materialMapper.selectBatchIds(matIds).stream()
                        .collect(Collectors.toMap(BizMaterial::getId, m -> m, (a, b) -> a));

        // 预先过滤出有剩余数量的明细行
        List<BizPurchaseOrderItem> remainingItems = items.stream().filter(oi -> {
            BigDecimal invTaskQty = oi.getInvTaskQty() != null ? oi.getInvTaskQty() : BigDecimal.ZERO;
            BigDecimal remaining = (oi.getQuantity() != null ? oi.getQuantity() : BigDecimal.ZERO).subtract(invTaskQty);
            return remaining.compareTo(BigDecimal.ZERO) > 0;
        }).collect(Collectors.toList());
        // 所有明细已全部收料，无需再次申请，静默跳过
        if (remainingItems.isEmpty()) return;

        BizInvRequest request = new BizInvRequest();
        request.setRequestNo(CodeUtil.generate("INR"));
        request.setInvType("purchase_in");
        request.setRelatedOrderNo(order.getPoNo());
        request.setStatus("pending");
        request.setApplicantId(SecurityUtil.getCurrentUserId());
        invRequestMapper.insert(request);

        for (BizPurchaseOrderItem oi : remainingItems) {
            BigDecimal invTaskQty = oi.getInvTaskQty() != null ? oi.getInvTaskQty() : BigDecimal.ZERO;
            BigDecimal remaining = (oi.getQuantity() != null ? oi.getQuantity() : BigDecimal.ZERO).subtract(invTaskQty);

            BizMaterial mat = oi.getMaterialId() != null ? matMap.get(oi.getMaterialId()) : null;
            BizInvRequestItem ri = new BizInvRequestItem();
            ri.setRequestId(request.getId());
            ri.setMaterialId(oi.getMaterialId());
            ri.setMaterialNo(mat != null ? mat.getMaterialNo() : null);
            ri.setMaterialName(mat != null ? mat.getMaterialName() : null);
            ri.setSpec(mat != null ? mat.getSpec() : null);
            ri.setUnit(mat != null ? mat.getUnit() : null);
            ri.setQuantity(remaining);
            ri.setUnitPrice(oi.getUnitPrice());
            if (oi.getUnitPrice() != null) {
                ri.setTotalPrice(remaining.multiply(oi.getUnitPrice()));
            }
            // 默认仓库：取物料的默认仓库
            if (mat != null && mat.getDefaultWarehouseId() != null) {
                ri.setWarehouseId(mat.getDefaultWarehouseId());
            }
            invRequestItemMapper.insert(ri);
        }
    }

    /**
     * 自动从采购订单直接生成库存任务（批量场景，不经过入库申请审批流）。
     * 按剩余数量 + 物料默认仓库自动构建收料明细，然后复用 createBatchReceipts 逻辑。
     * 静默跳过：状态不符合，或所有明细已全部收料。
     */
    @Transactional(rollbackFor = Exception.class)
    public void autoGenerateInvTask(Long orderId) {
        BizPurchaseOrder order = getById(orderId);
        if (order == null) return;
        if (!"confirmed".equals(order.getStatus())) return;

        List<BizPurchaseOrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<BizPurchaseOrderItem>()
                        .eq(BizPurchaseOrderItem::getPurchaseOrderId, orderId));
        if (items.isEmpty()) return;

        // 批量加载物料信息
        Set<Long> matIds = items.stream().map(BizPurchaseOrderItem::getMaterialId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, BizMaterial> matMap = matIds.isEmpty() ? new HashMap<>() :
                materialMapper.selectBatchIds(matIds).stream()
                        .collect(Collectors.toMap(BizMaterial::getId, m -> m, (a, b) -> a));

        // 构建收料明细列表（仅包含有剩余数量的行）
        List<Map<String, Object>> receiptItems = new ArrayList<>();
        for (BizPurchaseOrderItem oi : items) {
            BigDecimal invTaskQty = oi.getInvTaskQty() != null ? oi.getInvTaskQty() : BigDecimal.ZERO;
            BigDecimal remaining = (oi.getQuantity() != null ? oi.getQuantity() : BigDecimal.ZERO).subtract(invTaskQty);
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) continue;

            BizMaterial mat = oi.getMaterialId() != null ? matMap.get(oi.getMaterialId()) : null;
            Long warehouseId = mat != null ? mat.getDefaultWarehouseId() : null;

            Map<String, Object> itemData = new LinkedHashMap<>();
            itemData.put("orderItemId", oi.getId());
            itemData.put("materialId", oi.getMaterialId());
            itemData.put("actualQty", remaining);
            itemData.put("batchNo", "");  // 空字符串 → createBatchReceipts 内自动生成批次号
            itemData.put("warehouseId", warehouseId);
            itemData.put("locationId", null);
            receiptItems.add(itemData);
        }

        if (receiptItems.isEmpty()) return; // 所有明细已全部收料，静默跳过

        // 复用批量收料逻辑（创建收料单、质检任务、库存任务）
        createBatchReceipts(orderId, receiptItems);
    }

    /**
     * 手动办结采购订单
     */
    @Transactional(rollbackFor = Exception.class)
    public void closeOrder(Long id) {
        BizPurchaseOrder order = getById(id);
        if (order == null) throw new BusinessException("采购订单不存在");
        Set<String> closableStatus = Set.of("confirmed", "partial_receipt", "fully_receipt");
        if (!closableStatus.contains(order.getStatus())) throw new BusinessException("当前状态不允许办结");
        order.setStatus("closed");
        updateById(order);
    }

    /**
     * 从采购订单生成采购收料单（新流程）
     * @param items [{orderItemId, materialId, qty, batchNo}]
     */
    @Transactional(rollbackFor = Exception.class)
    public void generateReceipts(Long orderId, List<Map<String, Object>> items) {
        BizPurchaseOrder order = getById(orderId);
        if (order == null) throw new BusinessException("采购订单不存在");
        Set<String> allowedStatus = Set.of("confirmed", "partial_receipt");
        if (!allowedStatus.contains(order.getStatus())) throw new BusinessException("当前状态不允许生成收料单");
        if (items == null || items.isEmpty()) throw new BusinessException("收料明细不能为空");

        // 收料组 ID: 同一次生成的所有 receipt 共享, 用于列表折叠/打印对账单
        String receiptGroupId = CodeUtil.generate("RG");

        for (Map<String, Object> itemData : items) {
            Long orderItemId = Long.valueOf(itemData.get("orderItemId").toString());
            Long materialId = Long.valueOf(itemData.get("materialId").toString());
            BigDecimal qty = new BigDecimal(itemData.get("qty").toString());
            String batchNo = itemData.get("batchNo") != null ? itemData.get("batchNo").toString() : null;

            if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;

            BizPurchaseOrderItem orderItem = orderItemMapper.selectById(orderItemId);
            if (orderItem == null) throw new BusinessException("订单明细不存在: " + orderItemId);

            BigDecimal currentReceiptQty = orderItem.getReceiptQty() != null ? orderItem.getReceiptQty() : BigDecimal.ZERO;
            BigDecimal remaining = orderItem.getQuantity().subtract(currentReceiptQty);
            if (qty.compareTo(remaining) > 0) {
                throw new BusinessException("收料数量超出剩余可收料数量");
            }

            // 创建收料单
            BizPurchaseReceipt receipt = new BizPurchaseReceipt();
            receipt.setReceiptNo(CodeUtil.generate("PR"));
            receipt.setReceiptGroupId(receiptGroupId);
            receipt.setPurchaseOrderId(orderId);
            receipt.setOrderItemId(orderItemId);
            receipt.setMaterialId(materialId);
            receipt.setExpectedQty(orderItem.getQuantity());
            receipt.setActualQty(qty);
            receipt.setBatchNo(batchNo);
            receipt.setStatus("pending_receive");
            receipt.setQcStatus("none");
            receipt.setQualifiedQty(BigDecimal.ZERO);
            receipt.setDefectQty(BigDecimal.ZERO);
            receipt.setInvTaskQty(BigDecimal.ZERO);
            receipt.setStockInQty(BigDecimal.ZERO);
            receiptMapper.insert(receipt);

            // 更新订单明细的已生成收料单数量
            orderItem.setReceiptQty(currentReceiptQty.add(qty));
            orderItemMapper.updateById(orderItem);
        }

        // 更新采购订单状态
        updateReceiptStatus(orderId);
    }

    /**
     * 更新采购订单状态:基于实际已收料数量(只统计已收/已关单的收料单 actualQty)
     * 仅"申请收料"(receiptQty 累加)不应改变订单状态;
     * 必须等收料单 status=received/closed 且 actualQty>0 才算实际到货。
     * public:供 PurchaseReceiptService 在收料单状态变更后回调
     */
    public void updateReceiptStatus(Long orderId) {
        List<BizPurchaseOrderItem> allItems = orderItemMapper.selectList(
                new LambdaQueryWrapper<BizPurchaseOrderItem>()
                        .eq(BizPurchaseOrderItem::getPurchaseOrderId, orderId));
        BigDecimal totalQty = allItems.stream()
                .map(i -> i.getQuantity() != null ? i.getQuantity() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 实际收料量 = 该订单下所有"已收/已关单"收料单的 actualQty 累加
        List<BizPurchaseReceipt> receipts = receiptMapper.selectList(
                new LambdaQueryWrapper<BizPurchaseReceipt>()
                        .eq(BizPurchaseReceipt::getPurchaseOrderId, orderId)
                        .in(BizPurchaseReceipt::getStatus, "received", "closed"));
        BigDecimal totalReceived = receipts.stream()
                .map(r -> r.getActualQty() != null ? r.getActualQty() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BizPurchaseOrder order = getById(orderId);
        if (order == null) return;
        if (totalReceived.compareTo(BigDecimal.ZERO) <= 0) {
            // 还未实际收到货,保持 confirmed
            if (!"confirmed".equals(order.getStatus())
                    && !"partial_receipt".equals(order.getStatus())
                    && !"fully_receipt".equals(order.getStatus())) {
                return;
            }
            order.setStatus("confirmed");
        } else if (totalReceived.compareTo(totalQty) >= 0) {
            order.setStatus("fully_receipt");
        } else {
            order.setStatus("partial_receipt");
        }
        updateById(order);
    }

    /**
     * 作废采购订单（仅已驳回状态可作废）
     */
    public void voidOrder(Long id) {
        BizPurchaseOrder order = getById(id);
        if (order == null) throw new BusinessException("采购订单不存在");
        if (!"rejected".equals(order.getStatus())) {
            throw new BusinessException("只有已驳回的订单可以作废");
        }
        order.setStatus("voided");
        updateById(order);
    }

    /**
     * 更新采购订单的库存任务生成状态（根据所有明细的 invTaskQty vs quantity 计算）
     */
    private void updateInvTaskGenStatus(Long orderId) {
        List<BizPurchaseOrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<BizPurchaseOrderItem>()
                        .eq(BizPurchaseOrderItem::getPurchaseOrderId, orderId));
        if (items.isEmpty()) return;

        BigDecimal totalQty = items.stream()
                .map(i -> i.getQuantity() != null ? i.getQuantity() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalInvTaskQty = items.stream()
                .map(i -> i.getInvTaskQty() != null ? i.getInvTaskQty() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BizPurchaseOrder order = getById(orderId);
        if (order == null) return;

        if (totalInvTaskQty.compareTo(BigDecimal.ZERO) <= 0) {
            order.setInvTaskGenStatus(null);
        } else if (totalInvTaskQty.compareTo(totalQty) >= 0) {
            order.setInvTaskGenStatus("fully_generated");
        } else {
            order.setInvTaskGenStatus("partial_generated");
        }
        updateById(order);
    }
}
