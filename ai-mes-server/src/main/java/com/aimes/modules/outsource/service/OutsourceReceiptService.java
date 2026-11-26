package com.aimes.modules.outsource.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.PageResult;
import com.aimes.common.utils.CodeUtil;
import com.aimes.common.utils.SecurityUtil;
import com.aimes.modules.inventory.service.BizInvTaskService;
import com.aimes.modules.master.entity.BizMaterial;
import com.aimes.modules.master.entity.BizSupplier;
import com.aimes.modules.master.mapper.BizMaterialMapper;
import com.aimes.modules.master.mapper.BizSupplierMapper;
import com.aimes.modules.order.service.BizSalesOrderService;
import com.aimes.modules.outsource.entity.BizOutsourceOrder;
import com.aimes.modules.outsource.entity.BizOutsourceReceipt;
import com.aimes.modules.outsource.mapper.BizOutsourceOrderMapper;
import com.aimes.modules.outsource.mapper.BizOutsourceReceiptMapper;
import com.aimes.modules.quality.entity.BizQcTask;
import com.aimes.modules.quality.service.BizQcTaskService;
import com.aimes.modules.system.service.SysNotificationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 外协收料单服务（参照PurchaseReceiptService实现）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutsourceReceiptService extends ServiceImpl<BizOutsourceReceiptMapper, BizOutsourceReceipt> {

    private final BizOutsourceReceiptMapper receiptMapper;
    private final BizOutsourceOrderMapper orderMapper;
    private final BizMaterialMapper materialMapper;
    private final BizSupplierMapper supplierMapper;
    private final BizQcTaskService qcTaskService;
    @Lazy
    private final BizInvTaskService invTaskService;
    private final SysNotificationService notificationService;
    @Lazy
    private final BizSalesOrderService salesOrderService;
    @org.springframework.beans.factory.annotation.Autowired
    @Lazy
    private OutsourceChainService outsourceChainService;
    private final com.aimes.modules.production.mapper.BizWoProcessTaskMapper processTaskMapper;

    // ======================== 分页查询 ========================

    public PageResult<Map<String, Object>> getPage(String receiptNo, String orderNo, String supplierName,
                                                    String status, String qcStatus,
                                                    int pageNum, int pageSize) {
        return getPage(receiptNo, orderNo, supplierName, null, status, qcStatus, pageNum, pageSize);
    }

    public PageResult<Map<String, Object>> getPage(String receiptNo, String orderNo, String supplierName, String productName,
                                                    String status, String qcStatus,
                                                    int pageNum, int pageSize) {
        try {
            return getPageInternal(receiptNo, orderNo, supplierName, productName, status, qcStatus, pageNum, pageSize);
        } catch (Exception e) {
            log.error("外协收料单分页查询失败: receiptNo={}, orderNo={}, supplierName={}, productName={}, status={}, qcStatus={}",
                    receiptNo, orderNo, supplierName, productName, status, qcStatus, e);
            throw new BusinessException("外协收料单查询失败: " + e.getClass().getSimpleName() + " - "
                    + (e.getMessage() != null ? e.getMessage() : "未知错误"));
        }
    }

    private PageResult<Map<String, Object>> getPageInternal(String receiptNo, String orderNo, String supplierName, String productName,
                                                             String status, String qcStatus,
                                                             int pageNum, int pageSize) {
        Set<Long> orderIdFilter = null;
        // 产品名称过滤:模糊匹配物料 → 取关联的 outsource_order(receiptMaterialId 或 productId 命中)
        if (StringUtils.hasText(productName)) {
            List<Long> matchedMatIds = materialMapper.selectList(
                    new LambdaQueryWrapper<BizMaterial>().like(BizMaterial::getMaterialName, productName))
                    .stream().map(BizMaterial::getId).collect(Collectors.toList());
            if (matchedMatIds.isEmpty()) return new PageResult<>(List.of(), 0L, pageNum, pageSize);
            Set<Long> matchedOOIds = new HashSet<>();
            orderMapper.selectList(new LambdaQueryWrapper<BizOutsourceOrder>()
                    .and(w -> w.in(BizOutsourceOrder::getReceiptMaterialId, matchedMatIds)
                              .or().in(BizOutsourceOrder::getProductId, matchedMatIds)))
                    .forEach(o -> matchedOOIds.add(o.getId()));
            if (matchedOOIds.isEmpty()) return new PageResult<>(List.of(), 0L, pageNum, pageSize);
            orderIdFilter = matchedOOIds;
        }
        if (StringUtils.hasText(supplierName)) {
            List<Long> sids = supplierMapper.selectList(
                    new LambdaQueryWrapper<BizSupplier>().like(BizSupplier::getSupplierName, supplierName))
                    .stream().map(BizSupplier::getId).toList();
            if (sids.isEmpty()) return new PageResult<>(List.of(), 0L, pageNum, pageSize);
            Set<Long> supplierMatched = orderMapper.selectList(
                    new LambdaQueryWrapper<BizOutsourceOrder>().in(BizOutsourceOrder::getSupplierId, sids))
                    .stream().map(BizOutsourceOrder::getId).collect(Collectors.toSet());
            if (supplierMatched.isEmpty()) return new PageResult<>(List.of(), 0L, pageNum, pageSize);
            orderIdFilter = orderIdFilter != null
                    ? orderIdFilter.stream().filter(supplierMatched::contains).collect(Collectors.toSet())
                    : supplierMatched;
            if (orderIdFilter.isEmpty()) return new PageResult<>(List.of(), 0L, pageNum, pageSize);
        }
        if (StringUtils.hasText(orderNo)) {
            Set<Long> matchedIds = orderMapper.selectList(
                    new LambdaQueryWrapper<BizOutsourceOrder>().like(BizOutsourceOrder::getOrderNo, orderNo))
                    .stream().map(BizOutsourceOrder::getId).collect(Collectors.toSet());
            if (matchedIds.isEmpty()) return new PageResult<>(List.of(), 0L, pageNum, pageSize);
            orderIdFilter = orderIdFilter != null ? orderIdFilter.stream().filter(matchedIds::contains).collect(Collectors.toSet()) : matchedIds;
            if (orderIdFilter.isEmpty()) return new PageResult<>(List.of(), 0L, pageNum, pageSize);
        }

        LambdaQueryWrapper<BizOutsourceReceipt> wrapper = new LambdaQueryWrapper<BizOutsourceReceipt>()
                .like(StringUtils.hasText(receiptNo), BizOutsourceReceipt::getReceiptNo, receiptNo)
                .eq(StringUtils.hasText(status), BizOutsourceReceipt::getStatus, status)
                .eq(StringUtils.hasText(qcStatus), BizOutsourceReceipt::getQcStatus, qcStatus)
                .orderByDesc(BizOutsourceReceipt::getCreateTime);
        if (orderIdFilter != null) wrapper.in(BizOutsourceReceipt::getOutsourceOrderId, orderIdFilter);

        Page<BizOutsourceReceipt> page = receiptMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<BizOutsourceReceipt> records = page.getRecords();

        Set<Long> ooIds = records.stream().map(BizOutsourceReceipt::getOutsourceOrderId).filter(Objects::nonNull).collect(Collectors.toSet());
        // 用 HashMap 而非 Map.of() — Map.of() 不接受 null key，
        // 后续 getOrDefault(null, ...) 会直接抛 NPE
        Map<Long, BizOutsourceOrder> orderMap = new HashMap<>();
        if (!ooIds.isEmpty()) {
            orderMapper.selectBatchIds(ooIds).forEach(o -> orderMap.put(o.getId(), o));
        }
        Set<Long> supplierIds = orderMap.values().stream().map(BizOutsourceOrder::getSupplierId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> supplierNameMap = new HashMap<>();
        if (!supplierIds.isEmpty()) {
            supplierMapper.selectBatchIds(supplierIds).forEach(s ->
                    supplierNameMap.put(s.getId(), s.getSupplierName() != null ? s.getSupplierName() : ""));
        }
        // 收集所有需要的物料ID：优先收料物料，没有则用成品
        Set<Long> allMatIds = new HashSet<>();
        orderMap.values().forEach(o -> {
            if (o.getReceiptMaterialId() != null) allMatIds.add(o.getReceiptMaterialId());
            if (o.getProductId() != null) allMatIds.add(o.getProductId());
        });
        Map<Long, BizMaterial> allMatMap = new HashMap<>();
        if (!allMatIds.isEmpty()) {
            materialMapper.selectBatchIds(allMatIds).forEach(mt -> allMatMap.put(mt.getId(), mt));
        }

        List<Map<String, Object>> list = records.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("receiptNo", r.getReceiptNo());
            m.put("outsourceOrderId", r.getOutsourceOrderId());
            BizOutsourceOrder oo = orderMap.get(r.getOutsourceOrderId());
            m.put("orderNo", oo != null ? oo.getOrderNo() : "");
            m.put("supplierName", (oo != null && oo.getSupplierId() != null)
                    ? supplierNameMap.getOrDefault(oo.getSupplierId(), "") : "");
            // 物料信息：优先收料物料(receiptMaterialId)，没有则用成品(productId)
            Long matId = oo != null && oo.getReceiptMaterialId() != null ? oo.getReceiptMaterialId()
                    : (oo != null ? oo.getProductId() : null);
            BizMaterial mat = matId != null ? allMatMap.get(matId) : null;
            m.put("materialNo", mat != null ? mat.getMaterialNo() : "");
            m.put("productName", mat != null ? mat.getMaterialName() : "");
            m.put("spec", mat != null && mat.getSpec() != null ? mat.getSpec() : "");
            m.put("unit", mat != null && mat.getUnit() != null ? mat.getUnit() : "");
            m.put("stepName", oo != null ? oo.getStepName() : "");
            m.put("receivedQty", r.getReceivedQty());
            m.put("batchNo", r.getBatchNo());
            m.put("status", r.getStatus());
            m.put("qcStatus", r.getQcStatus());
            m.put("qualifiedQty", r.getQualifiedQty());
            m.put("defectQty", r.getDefectQty());
            m.put("invTaskQty", r.getInvTaskQty());
            m.put("stockInQty", r.getStockInQty());
            m.put("createTime", r.getCreateTime());
            return m;
        }).toList();
        return new PageResult<>(list, page.getTotal(), pageNum, pageSize);
    }

    // ======================== 详情 ========================

    public Map<String, Object> getDetail(Long id) {
        BizOutsourceReceipt r = getById(id);
        if (r == null) throw new BusinessException("外协收料单不存在");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("receiptNo", r.getReceiptNo());
        m.put("outsourceOrderId", r.getOutsourceOrderId());
        BizOutsourceOrder oo = orderMapper.selectById(r.getOutsourceOrderId());
        m.put("orderNo", oo != null ? oo.getOrderNo() : "");
        if (oo != null) {
            BizSupplier supplier = supplierMapper.selectById(oo.getSupplierId());
            m.put("supplierName", supplier != null ? supplier.getSupplierName() : "");
            // 物料信息：优先收料物料(receiptMaterialId)
            Long matId = oo.getReceiptMaterialId() != null ? oo.getReceiptMaterialId() : oo.getProductId();
            BizMaterial mat = matId != null ? materialMapper.selectById(matId) : null;
            m.put("productName", mat != null ? mat.getMaterialName() : "");
            m.put("materialNo", mat != null ? mat.getMaterialNo() : "");
            m.put("spec", mat != null ? mat.getSpec() : "");
            m.put("unit", mat != null ? mat.getUnit() : "");
            m.put("productId", matId);
            m.put("stepName", oo.getStepName());
            m.put("defaultWarehouseId", mat != null ? mat.getDefaultWarehouseId() : null);
        }
        m.put("receivedQty", r.getReceivedQty());
        m.put("batchNo", r.getBatchNo());
        m.put("warehouseId", r.getWarehouseId());
        m.put("status", r.getStatus());
        m.put("qcStatus", r.getQcStatus());
        m.put("qcTaskId", r.getQcTaskId());
        m.put("qualifiedQty", r.getQualifiedQty());
        m.put("defectQty", r.getDefectQty());
        m.put("invTaskQty", r.getInvTaskQty());
        m.put("stockInQty", r.getStockInQty());
        m.put("remark", r.getRemark());
        m.put("createTime", r.getCreateTime());
        return m;
    }

    // ======================== 收料操作 — 需质检 ========================

    @Transactional(rollbackFor = Exception.class)
    public void receiveWithQc(Long receiptId, Long warehouseId) {
        BizOutsourceReceipt receipt = getById(receiptId);
        if (receipt == null) throw new BusinessException("外协收料单不存在");
        if (!"pending_receive".equals(receipt.getStatus())) throw new BusinessException("当前状态不允许收料操作");

        receipt.setWarehouseId(warehouseId);

        BizOutsourceOrder oo = orderMapper.selectById(receipt.getOutsourceOrderId());
        // 优先使用收料物料ID
        Long receiptMatId = oo != null && oo.getReceiptMaterialId() != null ? oo.getReceiptMaterialId()
                : (oo != null ? oo.getProductId() : null);

        BizQcTask qcTask = new BizQcTask();
        qcTask.setQcType("outsource");
        qcTask.setProductId(receiptMatId);
        qcTask.setBatchNo(receipt.getBatchNo());
        qcTask.setPlanQty(receipt.getReceivedQty());
        qcTask.setRelatedOrderType("outsource_receipt");
        qcTask.setRelatedOrderId(receiptId);
        if (oo != null) qcTask.setWorkOrderId(oo.getWorkOrderId());
        qcTaskService.createTask(qcTask);

        receipt.setQcTaskId(qcTask.getId());
        receipt.setStatus("receiving");
        receipt.setQcStatus("pending_qc");
        updateById(receipt);

        try {
            notificationService.notifyRole("quality",
                    "外协收料[" + receipt.getReceiptNo() + "]已到货, 请安排质检",
                    null, null, "outsource_receipt", receiptId);
        } catch (Exception ignored) {}
    }

    // ======================== 收料操作 — 无需质检 ========================

    @Transactional(rollbackFor = Exception.class)
    public void receiveWithoutQc(Long receiptId, Long warehouseId) {
        BizOutsourceReceipt receipt = getById(receiptId);
        if (receipt == null) throw new BusinessException("外协收料单不存在");
        if (!"pending_receive".equals(receipt.getStatus())) throw new BusinessException("当前状态不允许收料操作");

        receipt.setWarehouseId(warehouseId);
        BizOutsourceOrder oo = orderMapper.selectById(receipt.getOutsourceOrderId());
        String orderNo = oo != null ? oo.getOrderNo() : "";
        // 优先使用收料物料(receiptMaterialId)，没有则用成品(productId)
        Long matId = oo != null && oo.getReceiptMaterialId() != null ? oo.getReceiptMaterialId()
                : (oo != null ? oo.getProductId() : null);
        BizMaterial mat = matId != null ? materialMapper.selectById(matId) : null;
        BigDecimal unitPrice = oo != null && oo.getUnitPrice() != null ? oo.getUnitPrice() : BigDecimal.ZERO;
        Long applicantId = SecurityUtil.getCurrentUserId();

        invTaskService.createFromOutsourceReceipt(
                receiptId, receipt.getReceiptNo(), orderNo, receipt.getBatchNo(),
                warehouseId, applicantId,
                mat != null ? mat.getId() : 0L,
                mat != null ? mat.getMaterialNo() : "",
                mat != null ? mat.getMaterialName() : "",
                mat != null ? mat.getSpec() : "",
                mat != null ? mat.getUnit() : "",
                receipt.getReceivedQty(), unitPrice);

        receipt.setStatus("receiving");
        receipt.setQcStatus("none");
        receipt.setInvTaskQty(receipt.getReceivedQty());
        updateById(receipt);
    }

    // ======================== 质检完成回调 ========================

    @Transactional(rollbackFor = Exception.class)
    public void onQcComplete(Long receiptId, BigDecimal qualifiedQty, BigDecimal defectQty) {
        BizOutsourceReceipt receipt = getById(receiptId);
        if (receipt == null) throw new BusinessException("外协收料单不存在");

        // 直接使用质检结果的合格数和不合格数
        BigDecimal actualQualified = qualifiedQty != null ? qualifiedQty : BigDecimal.ZERO;
        BigDecimal actualDefect = defectQty != null ? defectQty : BigDecimal.ZERO;
        receipt.setQualifiedQty(actualQualified);
        receipt.setDefectQty(actualDefect);
        receipt.setQcStatus("qc_done");

        BizOutsourceOrder oo = orderMapper.selectById(receipt.getOutsourceOrderId());
        String orderNo = oo != null ? oo.getOrderNo() : "";
        // 优先使用收料物料(receiptMaterialId)，没有则用成品(productId)
        Long matId = oo != null && oo.getReceiptMaterialId() != null ? oo.getReceiptMaterialId()
                : (oo != null ? oo.getProductId() : null);
        BizMaterial mat = matId != null ? materialMapper.selectById(matId) : null;
        BigDecimal unitPrice = oo != null && oo.getUnitPrice() != null ? oo.getUnitPrice() : BigDecimal.ZERO;
        Long applicantId = SecurityUtil.getCurrentUserId();

        BizQcTask qcTask = receipt.getQcTaskId() != null ? qcTaskService.getById(receipt.getQcTaskId()) : null;
        String qcTaskNo = qcTask != null ? qcTask.getTaskNo() : "";

        BigDecimal invTaskQtyTotal = BigDecimal.ZERO;

        // 库存任务数量 = 收料总数量 - 不合格数
        BigDecimal invQty = receipt.getReceivedQty().subtract(actualDefect);
        if (invQty.compareTo(BigDecimal.ZERO) > 0) {
            invTaskService.createFromQcTask(
                    receipt.getQcTaskId(), receipt.getReceiptNo(), orderNo, receipt.getBatchNo(),
                    receipt.getWarehouseId(), applicantId,
                    mat != null ? mat.getId() : 0L,
                    mat != null ? mat.getMaterialNo() : "",
                    mat != null ? mat.getMaterialName() : "",
                    mat != null ? mat.getSpec() : "",
                    mat != null ? mat.getUnit() : "",
                    invQty, unitPrice,
                    "outsource_in", qcTaskNo);
            invTaskQtyTotal = invTaskQtyTotal.add(invQty);
        }

        // 不合格数退回到收料单：扣减收料单的receivedQty
        if (actualDefect.compareTo(BigDecimal.ZERO) > 0) {
            receipt.setReceivedQty(receipt.getReceivedQty().subtract(actualDefect).max(BigDecimal.ZERO));
            // 同步扣减外协订单的receiptQty，允许重新生成收料单
            if (oo != null) {
                BigDecimal currentReceiptQty = oo.getReceiptQty() != null ? oo.getReceiptQty() : BigDecimal.ZERO;
                oo.setReceiptQty(currentReceiptQty.subtract(actualDefect).max(BigDecimal.ZERO));
                orderMapper.updateById(oo);
            }
        }

        receipt.setInvTaskQty(invTaskQtyTotal);
        updateById(receipt);

        // 累加外协订单合格数（质检通过场景）
        if (actualQualified.compareTo(BigDecimal.ZERO) > 0) {
            BizOutsourceOrder order = orderMapper.selectById(receipt.getOutsourceOrderId());
            if (order != null) {
                BigDecimal currentOrderQualified = order.getQualifiedQty() != null ? order.getQualifiedQty() : BigDecimal.ZERO;
                order.setQualifiedQty(currentOrderQualified.add(actualQualified));
                orderMapper.updateById(order);
            }
        }
    }

    // ======================== 库存任务处理回调 ========================

    @Transactional(rollbackFor = Exception.class)
    public void onInvTaskProcess(Long receiptId, BigDecimal processedQty) {
        BizOutsourceReceipt receipt = getById(receiptId);
        if (receipt == null) return;
        BigDecimal currentStockIn = receipt.getStockInQty() != null ? receipt.getStockInQty() : BigDecimal.ZERO;
        receipt.setStockInQty(currentStockIn.add(processedQty));
        if (receipt.getStockInQty().compareTo(receipt.getReceivedQty()) >= 0
                && !"closed".equals(receipt.getStatus())) {
            receipt.setStatus("received");
        }
        // 无质检场景：入库即视为合格
        if ("none".equals(receipt.getQcStatus())) {
            receipt.setQualifiedQty(receipt.getStockInQty());
        }
        updateById(receipt);

        // 累加外协订单合格数（无质检场景）
        if ("none".equals(receipt.getQcStatus())) {
            BizOutsourceOrder order = orderMapper.selectById(receipt.getOutsourceOrderId());
            if (order != null) {
                BigDecimal currentOrderQualified = order.getQualifiedQty() != null ? order.getQualifiedQty() : BigDecimal.ZERO;
                order.setQualifiedQty(currentOrderQualified.add(processedQty));
                orderMapper.updateById(order);
            }
        }

        // 入库完成后检查并推进外协订单状态
        checkAndCompleteOutsourceOrder(receipt.getOutsourceOrderId());
    }

    /**
     * 检查外协订单所有收料单是否都已完成(received/closed)，若是则将订单推进为 completed
     */
    private void checkAndCompleteOutsourceOrder(Long outsourceOrderId) {
        if (outsourceOrderId == null) return;
        BizOutsourceOrder order = orderMapper.selectById(outsourceOrderId);
        if (order == null) return;
        if ("completed".equals(order.getStatus()) || "cancelled".equals(order.getStatus())) return;
        // 订单必须已是 fully_receipt 状态
        if (!"fully_receipt".equals(order.getStatus())) return;

        List<BizOutsourceReceipt> receipts = list(
                new LambdaQueryWrapper<BizOutsourceReceipt>()
                        .eq(BizOutsourceReceipt::getOutsourceOrderId, outsourceOrderId)
                        .eq(BizOutsourceReceipt::getDeleted, 0));
        if (receipts.isEmpty()) return;
        boolean allDone = receipts.stream().allMatch(r ->
                "received".equals(r.getStatus()) || "closed".equals(r.getStatus()));
        if (allDone) {
            order.setStatus("completed");
            orderMapper.updateById(order);
            log.info("外协订单所有收料单已完成，状态推进为 completed: orderId={}", outsourceOrderId);

            // 工序外协：加工单完成时,无质检/放行路径走与质检通过路径相同的链路
            // (设 task.qualifiedQty + task.status=completed + 激活后道工序 + 推进工单)
            if (order.getTaskId() != null) {
                try {
                    com.aimes.modules.production.entity.BizWoProcessTask task =
                            processTaskMapper.selectById(order.getTaskId());
                    if (task != null && !"completed".equals(task.getStatus())) {
                        BigDecimal qualifiedQty = order.getQualifiedQty() != null
                                ? order.getQualifiedQty() : order.getTotalQty();
                        // 已经累计过的部分要扣掉,避免重复
                        BigDecimal existing = task.getQualifiedQty() != null ? task.getQualifiedQty() : BigDecimal.ZERO;
                        BigDecimal delta = qualifiedQty.subtract(existing).max(BigDecimal.ZERO);
                        if (delta.compareTo(BigDecimal.ZERO) > 0) {
                            outsourceChainService.onOutsourceReceiptQcPassed(outsourceOrderId, delta);
                        } else {
                            // 已累齐,只补外协状态
                            task.setOutsourceStatus("completed");
                            processTaskMapper.updateById(task);
                        }
                    } else if (task != null && !"completed".equals(task.getOutsourceStatus())) {
                        task.setOutsourceStatus("completed");
                        processTaskMapper.updateById(task);
                    }
                } catch (Exception e) {
                    log.warn("同步工序任务外协状态失败: taskId={}", order.getTaskId(), e);
                }
            }

            // 同步销售订单行项进度并推进订单状态（整单外协场景）
            if (order.getOrderId() != null) {
                try {
                    BigDecimal qualifiedQty = order.getQualifiedQty() != null ? order.getQualifiedQty() : order.getTotalQty();
                    salesOrderService.syncOutsourceOrderCompleted(order.getOrderId(), order.getOrderItemId(), qualifiedQty);
                } catch (Exception e) {
                    log.warn("同步销售订单进度失败: salesOrderId={}", order.getOrderId(), e);
                }
            }
        }
    }

    // ======================== 库存任务退回回调 ========================

    @Transactional(rollbackFor = Exception.class)
    public void onInvTaskReturn(Long receiptId, BigDecimal returnedQty) {
        BizOutsourceReceipt receipt = getById(receiptId);
        if (receipt == null) return;
        BigDecimal currentStockIn = receipt.getStockInQty() != null ? receipt.getStockInQty() : BigDecimal.ZERO;
        BigDecimal currentInvTask = receipt.getInvTaskQty() != null ? receipt.getInvTaskQty() : BigDecimal.ZERO;
        receipt.setStockInQty(currentStockIn.subtract(returnedQty).max(BigDecimal.ZERO));
        receipt.setInvTaskQty(currentInvTask.subtract(returnedQty).max(BigDecimal.ZERO));

        // 回退收料单的receivedQty
        BigDecimal currentReceivedQty = receipt.getReceivedQty() != null ? receipt.getReceivedQty() : BigDecimal.ZERO;
        receipt.setReceivedQty(currentReceivedQty.subtract(returnedQty).max(BigDecimal.ZERO));

        if (receipt.getReceivedQty().compareTo(BigDecimal.ZERO) <= 0) {
            receipt.setStatus("pending_receive");
        } else if ("received".equals(receipt.getStatus())) {
            receipt.setStatus("receiving");
        }
        updateById(receipt);

        // 同步回退外协订单的receiptQty
        BizOutsourceOrder oo = orderMapper.selectById(receipt.getOutsourceOrderId());
        if (oo != null) {
            BigDecimal currentReceiptQty = oo.getReceiptQty() != null ? oo.getReceiptQty() : BigDecimal.ZERO;
            oo.setReceiptQty(currentReceiptQty.subtract(returnedQty).max(BigDecimal.ZERO));
            orderMapper.updateById(oo);
        }
    }

    // ======================== 办结 ========================

    @Transactional(rollbackFor = Exception.class)
    public void closeReceipt(Long receiptId) {
        BizOutsourceReceipt receipt = getById(receiptId);
        if (receipt == null) throw new BusinessException("外协收料单不存在");
        if ("closed".equals(receipt.getStatus())) throw new BusinessException("收料单已办结");
        receipt.setStatus("closed");
        updateById(receipt);
        // 办结后检查并推进外协订单状态
        checkAndCompleteOutsourceOrder(receipt.getOutsourceOrderId());
    }

    // ======================== 关联任务 ========================

    public List<Map<String, Object>> getRelatedQcTasks(Long receiptId) {
        BizOutsourceReceipt receipt = getById(receiptId);
        if (receipt == null) return List.of();
        List<BizQcTask> tasks = qcTaskService.list(
                new LambdaQueryWrapper<BizQcTask>()
                        .eq(BizQcTask::getRelatedOrderType, "outsource_receipt")
                        .eq(BizQcTask::getRelatedOrderId, receiptId)
                        .orderByDesc(BizQcTask::getCreateTime));
        return tasks.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("taskNo", t.getTaskNo());
            m.put("qcType", t.getQcType());
            m.put("result", t.getResult());
            m.put("planQty", t.getPlanQty());
            m.put("sampleQty", t.getSampleQty());
            m.put("passQty", t.getPassQty());
            m.put("failQty", t.getFailQty());
            m.put("conclusion", t.getConclusion());
            m.put("inspectedAt", t.getInspectedAt());
            m.put("createTime", t.getCreateTime());
            return m;
        }).toList();
    }

    public List<Map<String, Object>> getRelatedInvTasks(Long receiptId) {
        BizOutsourceReceipt receipt = getById(receiptId);
        if (receipt == null) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        result.addAll(invTaskService.findBySource("outsource_receipt", receiptId));
        if (receipt.getQcTaskId() != null) {
            result.addAll(invTaskService.findBySource("qc_task", receipt.getQcTaskId()));
        }
        return result;
    }
}
