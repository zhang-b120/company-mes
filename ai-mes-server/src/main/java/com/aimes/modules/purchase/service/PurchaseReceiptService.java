package com.aimes.modules.purchase.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.PageResult;
import com.aimes.common.utils.CodeUtil;
import com.aimes.common.utils.SecurityUtil;
import com.aimes.modules.inventory.service.BizInvTaskService;
import com.aimes.modules.master.entity.BizMaterial;
import com.aimes.modules.master.entity.BizSupplier;
import com.aimes.modules.master.mapper.BizMaterialMapper;
import com.aimes.modules.master.mapper.BizSupplierMapper;
import com.aimes.modules.purchase.entity.BizPurchaseOrder;
import com.aimes.modules.purchase.entity.BizPurchaseOrderItem;
import com.aimes.modules.purchase.entity.BizPurchaseReceipt;
import com.aimes.modules.purchase.mapper.BizPurchaseOrderItemMapper;
import com.aimes.modules.purchase.mapper.BizPurchaseOrderMapper;
import com.aimes.modules.purchase.mapper.BizPurchaseReceiptMapper;
import com.aimes.modules.quality.entity.BizQcTask;
import com.aimes.modules.quality.service.BizQcTaskService;
import com.aimes.modules.system.service.SysNotificationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PurchaseReceiptService extends ServiceImpl<BizPurchaseReceiptMapper, BizPurchaseReceipt> {

    private final BizPurchaseReceiptMapper receiptMapper;
    private final BizPurchaseOrderMapper orderMapper;
    private final BizPurchaseOrderItemMapper orderItemMapper;
    private final BizMaterialMapper materialMapper;
    private final BizSupplierMapper supplierMapper;
    private final BizQcTaskService qcTaskService;
    @Lazy
    private final BizInvTaskService invTaskService;
    @Lazy
    private final PurchaseOrderService purchaseOrderService;
    private final SysNotificationService notificationService;
    private final com.aimes.modules.system.mapper.SysUserMapper userMapper;

    // ======================== 分页查询 ========================

    public PageResult<Map<String, Object>> getPage(String receiptNo, String poNo, String supplierName,
                                                    String status, String qcStatus, String materialName,
                                                    int pageNum, int pageSize) {
        LambdaQueryWrapper<BizPurchaseReceipt> wrapper = buildFilterWrapper(receiptNo, poNo, supplierName, status, qcStatus, materialName);
        if (wrapper == null) return new PageResult<>(List.of(), 0L, pageNum, pageSize);
        wrapper.orderByDesc(BizPurchaseReceipt::getCreateTime);

        Page<BizPurchaseReceipt> page = receiptMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return new PageResult<>(enrichToMaps(page.getRecords()), page.getTotal(), pageNum, pageSize);
    }

    /**
     * 按批次组(receipt_group_id)分页. 同一 group 的所有 receipt 行作为一个分页单位.
     * 老数据(无 group_id)各自单独成组. 前端折叠模式下 total 与可见组数一致.
     */
    public PageResult<Map<String, Object>> getGroupedPage(String receiptNo, String poNo, String supplierName,
                                                          String status, String qcStatus, String materialName,
                                                          int pageNum, int pageSize) {
        LambdaQueryWrapper<BizPurchaseReceipt> wrapper = buildFilterWrapper(receiptNo, poNo, supplierName, status, qcStatus, materialName);
        if (wrapper == null) return new PageResult<>(List.of(), 0L, pageNum, pageSize);

        // 仅取必要字段, 用于在内存中分组
        List<BizPurchaseReceipt> all = receiptMapper.selectList(wrapper);
        if (all.isEmpty()) return new PageResult<>(List.of(), 0L, pageNum, pageSize);

        // 按 group_id 聚合, 空 group 按 receipt id 视为独立组
        Map<String, List<BizPurchaseReceipt>> grouped = new LinkedHashMap<>();
        Map<String, java.time.LocalDateTime> latestTimeMap = new HashMap<>();
        for (BizPurchaseReceipt r : all) {
            String key = StringUtils.hasText(r.getReceiptGroupId()) ? r.getReceiptGroupId() : "__single_" + r.getId();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
            java.time.LocalDateTime prev = latestTimeMap.get(key);
            if (prev == null || (r.getCreateTime() != null && r.getCreateTime().isAfter(prev))) {
                latestTimeMap.put(key, r.getCreateTime());
            }
        }

        // 按最新 createTime 排序组, 再做分页
        List<String> orderedKeys = grouped.keySet().stream()
                .sorted((a, b) -> {
                    java.time.LocalDateTime ta = latestTimeMap.get(a);
                    java.time.LocalDateTime tb = latestTimeMap.get(b);
                    if (ta == null && tb == null) return 0;
                    if (ta == null) return 1;
                    if (tb == null) return -1;
                    return tb.compareTo(ta);
                })
                .toList();

        int totalGroups = orderedKeys.size();
        int from = Math.max(0, (pageNum - 1) * pageSize);
        int to = Math.min(from + pageSize, totalGroups);
        if (from >= totalGroups) return new PageResult<>(List.of(), (long) totalGroups, pageNum, pageSize);

        List<BizPurchaseReceipt> pageRecords = orderedKeys.subList(from, to).stream()
                .flatMap(k -> grouped.get(k).stream())
                .toList();

        return new PageResult<>(enrichToMaps(pageRecords), (long) totalGroups, pageNum, pageSize);
    }

    /** 构建公共筛选 wrapper. 若按字段反查无结果直接返回 null, 调用方应跳过查询. */
    private LambdaQueryWrapper<BizPurchaseReceipt> buildFilterWrapper(String receiptNo, String poNo, String supplierName,
                                                                       String status, String qcStatus, String materialName) {
        Set<Long> supplierOrderIds = null;
        if (StringUtils.hasText(supplierName)) {
            List<Long> sids = supplierMapper.selectList(
                    new LambdaQueryWrapper<BizSupplier>().like(BizSupplier::getSupplierName, supplierName))
                    .stream().map(BizSupplier::getId).toList();
            if (sids.isEmpty()) return null;
            supplierOrderIds = orderMapper.selectList(
                    new LambdaQueryWrapper<BizPurchaseOrder>().in(BizPurchaseOrder::getSupplierId, sids))
                    .stream().map(BizPurchaseOrder::getId).collect(Collectors.toSet());
            if (supplierOrderIds.isEmpty()) return null;
        }
        Set<Long> poOrderIds = null;
        if (StringUtils.hasText(poNo)) {
            poOrderIds = orderMapper.selectList(
                    new LambdaQueryWrapper<BizPurchaseOrder>().like(BizPurchaseOrder::getPoNo, poNo))
                    .stream().map(BizPurchaseOrder::getId).collect(Collectors.toSet());
            if (poOrderIds.isEmpty()) return null;
        }
        Set<Long> materialIds = null;
        if (StringUtils.hasText(materialName)) {
            materialIds = materialMapper.selectList(
                    new LambdaQueryWrapper<BizMaterial>().like(BizMaterial::getMaterialName, materialName))
                    .stream().map(BizMaterial::getId).collect(Collectors.toSet());
            if (materialIds.isEmpty()) return null;
        }

        LambdaQueryWrapper<BizPurchaseReceipt> wrapper = new LambdaQueryWrapper<BizPurchaseReceipt>()
                .like(StringUtils.hasText(receiptNo), BizPurchaseReceipt::getReceiptNo, receiptNo)
                .eq(StringUtils.hasText(status), BizPurchaseReceipt::getStatus, status)
                .eq(StringUtils.hasText(qcStatus), BizPurchaseReceipt::getQcStatus, qcStatus);
        if (supplierOrderIds != null) wrapper.in(BizPurchaseReceipt::getPurchaseOrderId, supplierOrderIds);
        if (poOrderIds != null) wrapper.in(BizPurchaseReceipt::getPurchaseOrderId, poOrderIds);
        if (materialIds != null) wrapper.in(BizPurchaseReceipt::getMaterialId, materialIds);
        return wrapper;
    }

    /** 把 receipt 列表 enrich 成前端列表项 Map. */
    private List<Map<String, Object>> enrichToMaps(List<BizPurchaseReceipt> records) {
        if (records == null || records.isEmpty()) return List.of();

        Set<Long> orderIds = records.stream().map(BizPurchaseReceipt::getPurchaseOrderId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> matIds = records.stream().map(BizPurchaseReceipt::getMaterialId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, BizPurchaseOrder> orderMap = orderIds.isEmpty() ? Map.of() :
                orderMapper.selectBatchIds(orderIds).stream().collect(Collectors.toMap(BizPurchaseOrder::getId, o -> o, (a, b) -> a));
        List<BizMaterial> mats = matIds.isEmpty() ? List.of() : materialMapper.selectBatchIds(matIds);
        Map<Long, BizMaterial> matMap = mats.stream().collect(Collectors.toMap(BizMaterial::getId, m -> m, (a, b) -> a));

        Set<Long> supplierIds = orderMap.values().stream().map(BizPurchaseOrder::getSupplierId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> supplierNameMap = supplierIds.isEmpty() ? Map.of() :
                supplierMapper.selectBatchIds(supplierIds).stream().collect(Collectors.toMap(BizSupplier::getId, BizSupplier::getSupplierName, (a, b) -> a));

        return records.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("receiptNo", r.getReceiptNo());
            m.put("receiptGroupId", r.getReceiptGroupId());
            m.put("purchaseOrderId", r.getPurchaseOrderId());
            BizPurchaseOrder po = orderMap.get(r.getPurchaseOrderId());
            m.put("poNo", po != null ? po.getPoNo() : "");
            m.put("supplierName", po != null ? supplierNameMap.getOrDefault(po.getSupplierId(), "") : "");
            BizMaterial mat = matMap.get(r.getMaterialId());
            m.put("materialId", r.getMaterialId());
            m.put("materialNo", mat != null ? mat.getMaterialNo() : "");
            m.put("materialName", mat != null ? mat.getMaterialName() : "");
            m.put("spec", mat != null && mat.getSpec() != null ? mat.getSpec() : "");
            m.put("unit", mat != null && mat.getUnit() != null ? mat.getUnit() : "");
            m.put("expectedQty", r.getExpectedQty());
            m.put("actualQty", r.getActualQty());
            m.put("batchNo", r.getBatchNo());
            m.put("warehouseId", r.getWarehouseId());
            m.put("status", r.getStatus());
            m.put("qcStatus", r.getQcStatus());
            m.put("qualifiedQty", r.getQualifiedQty());
            m.put("defectQty", r.getDefectQty());
            m.put("invTaskQty", r.getInvTaskQty());
            m.put("stockInQty", r.getStockInQty());
            m.put("createTime", r.getCreateTime());
            return m;
        }).toList();
    }

    // ======================== 按 group 聚合查询 + 打印对账单 ========================

    /** 同组所有收料明细 (用于前端折叠展开查询) */
    public List<Map<String, Object>> getByGroup(String groupId) {
        if (groupId == null || groupId.isEmpty()) return java.util.Collections.emptyList();
        // 直接拿 receipt id 列表, 再走 page 的转换逻辑保持字段一致
        List<Long> ids = receiptMapper.selectList(
                new LambdaQueryWrapper<BizPurchaseReceipt>()
                        .eq(BizPurchaseReceipt::getReceiptGroupId, groupId)
                        .eq(BizPurchaseReceipt::getDeleted, 0)
                        .orderByAsc(BizPurchaseReceipt::getId))
                .stream().map(BizPurchaseReceipt::getId).collect(Collectors.toList());
        if (ids.isEmpty()) return java.util.Collections.emptyList();
        // 复用 getPage 把单条 id 一个个查 — 这里量小可以接受; 也可手写一遍 enrich 逻辑提速
        return ids.stream().map(this::getDetail).filter(Objects::nonNull).collect(Collectors.toList());
    }

    /**
     * 打印对账单数据 (JSON, 给 hiprint 模板用).
     * 结构:
     *   {
     *     header: { groupId, poNo, supplierName, batchNo, receivedDate, totalActualQty, totalQualifiedQty, ... },
     *     items:  [ {seq, materialNo, materialName, spec, unit, expectedQty, actualQty, diffQty, qualifiedQty, defectQty, batchNo, qcStatus, remark}, ... ]
     *   }
     * 字段命名跟 hiprint 模板里的 field 一一对应.
     */
    public Map<String, Object> getGroupPrintData(String groupId) {
        List<Map<String, Object>> rows = getByGroup(groupId);
        if (rows.isEmpty()) throw new BusinessException("收料批次不存在或已删除");
        Map<String, Object> first = rows.get(0);

        BigDecimal totalExpected = BigDecimal.ZERO;
        BigDecimal totalActual = BigDecimal.ZERO;
        BigDecimal totalQualified = BigDecimal.ZERO;
        BigDecimal totalDefect = BigDecimal.ZERO;

        List<Map<String, Object>> items = new ArrayList<>();
        int seq = 0;
        for (Map<String, Object> r : rows) {
            seq++;
            BigDecimal exp = r.get("expectedQty") != null ? new BigDecimal(r.get("expectedQty").toString()) : BigDecimal.ZERO;
            BigDecimal act = r.get("actualQty") != null ? new BigDecimal(r.get("actualQty").toString()) : BigDecimal.ZERO;
            BigDecimal qua = r.get("qualifiedQty") != null ? new BigDecimal(r.get("qualifiedQty").toString()) : BigDecimal.ZERO;
            BigDecimal def = r.get("defectQty") != null ? new BigDecimal(r.get("defectQty").toString()) : BigDecimal.ZERO;
            totalExpected = totalExpected.add(exp);
            totalActual = totalActual.add(act);
            totalQualified = totalQualified.add(qua);
            totalDefect = totalDefect.add(def);

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("seq", seq);
            m.put("materialNo", r.get("materialNo"));
            m.put("materialName", r.get("materialName"));
            // 规格在数据库里是 JSON 数组 (例: [{"name":"口径","values":["32"]}]),
            // 这里格式化成 "key:val key:val" 字符串, hiprint 模板直接渲染.
            m.put("spec", com.aimes.common.utils.ExcelUtil.formatSpec(r.get("spec") == null ? null : r.get("spec").toString()));
            m.put("unit", r.get("unit"));
            m.put("expectedQty", exp);
            m.put("actualQty", act);
            m.put("diffQty", act.subtract(exp));
            m.put("qualifiedQty", qua);
            m.put("defectQty", def);
            m.put("batchNo", r.get("batchNo"));
            m.put("qcStatus", qcStatusLabel((String) r.get("qcStatus")));
            m.put("remark", r.get("remark"));
            items.add(m);
        }

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("groupId", groupId);
        header.put("poNo", first.get("poNo"));
        header.put("supplierName", first.get("supplierName"));
        header.put("batchNo", first.get("batchNo"));
        header.put("receivedDate", java.time.LocalDate.now().toString());
        // 采购员: 取该批次组关联的采购订单 createBy 对应的用户姓名
        String purchaserName = "";
        try {
            Object poIdObj = first.get("purchaseOrderId");
            if (poIdObj != null) {
                BizPurchaseOrder po = orderMapper.selectById(Long.valueOf(poIdObj.toString()));
                if (po != null && po.getCreateBy() != null) {
                    com.aimes.modules.system.entity.SysUser user = userMapper.selectById(po.getCreateBy());
                    if (user != null) {
                        purchaserName = user.getRealName() != null && !user.getRealName().isEmpty()
                                ? user.getRealName() : (user.getUsername() != null ? user.getUsername() : "");
                    }
                }
            }
        } catch (Exception ignored) {}
        header.put("purchaserName", purchaserName);
        header.put("itemCount", items.size());
        header.put("totalExpected", totalExpected);
        header.put("totalActual", totalActual);
        header.put("totalQualified", totalQualified);
        header.put("totalDefect", totalDefect);
        header.put("totalDiff", totalActual.subtract(totalExpected));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("header", header);
        result.put("items", items);
        return result;
    }

    /** 打印对账单: 按 group_id 聚合, 输出 Excel(供采购员/仓管员核对到货情况) */
    public void printGroupReconciliation(String groupId, jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        List<Map<String, Object>> rows = getByGroup(groupId);
        if (rows.isEmpty()) throw new BusinessException("收料批次不存在或已删除");

        // 表头汇总信息 (PO号 / 供应商 / 收料日期 / 仓库)
        Map<String, Object> first = rows.get(0);
        String poNo = String.valueOf(first.getOrDefault("poNo", ""));
        String supplier = String.valueOf(first.getOrDefault("supplierName", ""));
        String batchNo = String.valueOf(first.getOrDefault("batchNo", ""));

        List<String> headers = List.of("序号", "物料编码", "物料名称", "规格", "单位",
                "应收数量", "实收数量", "差异", "合格数", "不合格数", "批次号", "质检状态", "备注");

        // 包装第一行加汇总头信息 (复用 ExcelUtil 输出, 多加几个不在 headers 的字段用 remark 携带)
        // 简化做法: 直接输出明细, 表头信息放在文件名里
        java.util.concurrent.atomic.AtomicInteger seq = new java.util.concurrent.atomic.AtomicInteger();
        List<java.util.function.Function<Map<String, Object>, Object>> extractors = List.of(
                r -> seq.incrementAndGet(),
                r -> r.get("materialNo"),
                r -> r.get("materialName"),
                r -> r.get("spec"),
                r -> r.get("unit"),
                r -> r.get("expectedQty"),
                r -> r.get("actualQty"),
                r -> {
                    java.math.BigDecimal exp = r.get("expectedQty") != null ? new java.math.BigDecimal(r.get("expectedQty").toString()) : java.math.BigDecimal.ZERO;
                    java.math.BigDecimal act = r.get("actualQty") != null ? new java.math.BigDecimal(r.get("actualQty").toString()) : java.math.BigDecimal.ZERO;
                    return act.subtract(exp);
                },
                r -> r.get("qualifiedQty"),
                r -> r.get("defectQty"),
                r -> r.get("batchNo"),
                r -> qcStatusLabel((String) r.get("qcStatus")),
                r -> r.get("remark"));
        String fileName = "到货对账单_" + poNo + "_" + supplier + "_" + (batchNo != null ? batchNo : groupId);
        com.aimes.common.utils.ExcelUtil.export(response, fileName, headers, rows, extractors);
    }

    // ======================== 导出 ========================

    public void export(String receiptNo, String poNo, String supplierName, String status, String qcStatus,
                       String materialName, jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        // 复用分页查询逻辑取全部数据(最大 5000 条)
        PageResult<Map<String, Object>> all = getPage(receiptNo, poNo, supplierName, status, qcStatus, materialName, 1, 5000);
        List<Map<String, Object>> rows = all.getRecords();
        List<String> headers = List.of("收料单号", "采购订单号", "供应商", "物料编码", "物料名称", "规格", "单位",
                "收料数量", "收料状态", "质检状态", "合格数", "不合格数", "库存任务数", "入库数", "批次号", "创建时间");
        List<java.util.function.Function<Map<String, Object>, Object>> extractors = List.of(
                r -> r.get("receiptNo"), r -> r.get("poNo"), r -> r.get("supplierName"),
                r -> r.get("materialNo"), r -> r.get("materialName"), r -> r.get("spec"), r -> r.get("unit"),
                r -> r.get("actualQty"), r -> statusLabel((String) r.get("status")),
                r -> qcStatusLabel((String) r.get("qcStatus")),
                r -> r.get("qualifiedQty"), r -> r.get("defectQty"),
                r -> r.get("invTaskQty"), r -> r.get("stockInQty"),
                r -> r.get("batchNo"), r -> r.get("createTime"));
        com.aimes.common.utils.ExcelUtil.export(response, "采购收料单", headers, rows, extractors);
    }

    private static String statusLabel(String s) {
        if (s == null) return "";
        return switch (s) {
            case "pending_receive" -> "待收料";
            case "receiving" -> "收料中";
            case "received" -> "已收料";
            case "closed" -> "办结";
            default -> s;
        };
    }

    private static String qcStatusLabel(String s) {
        if (s == null) return "";
        return switch (s) {
            case "none" -> "无质检";
            case "pending_qc" -> "待质检";
            case "qc_done" -> "已质检";
            default -> s;
        };
    }

    // ======================== 详情 ========================

    public Map<String, Object> getDetail(Long id) {
        BizPurchaseReceipt r = getById(id);
        if (r == null) throw new BusinessException("收料单不存在");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("receiptNo", r.getReceiptNo());
        m.put("receiptGroupId", r.getReceiptGroupId());
        m.put("purchaseOrderId", r.getPurchaseOrderId());
        BizPurchaseOrder po = orderMapper.selectById(r.getPurchaseOrderId());
        m.put("poNo", po != null ? po.getPoNo() : "");
        if (po != null) {
            BizSupplier supplier = supplierMapper.selectById(po.getSupplierId());
            m.put("supplierName", supplier != null ? supplier.getSupplierName() : "");
        }
        BizMaterial mat = materialMapper.selectById(r.getMaterialId());
        m.put("materialId", r.getMaterialId());
        m.put("materialName", mat != null ? mat.getMaterialName() : "");
        m.put("materialNo", mat != null ? mat.getMaterialNo() : "");
        m.put("spec", mat != null ? mat.getSpec() : "");
        m.put("unit", mat != null ? mat.getUnit() : "");
        m.put("defaultWarehouseId", mat != null ? mat.getDefaultWarehouseId() : null);
        m.put("expectedQty", r.getExpectedQty());
        m.put("actualQty", r.getActualQty());
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
        BizPurchaseReceipt receipt = getById(receiptId);
        if (receipt == null) throw new BusinessException("收料单不存在");
        if (!"pending_receive".equals(receipt.getStatus())) throw new BusinessException("当前状态不允许收料操作");

        receipt.setWarehouseId(warehouseId);

        // 创建质检任务
        BizQcTask qcTask = new BizQcTask();
        qcTask.setQcType("incoming");
        qcTask.setMaterialId(receipt.getMaterialId());
        qcTask.setBatchNo(receipt.getBatchNo());
        qcTask.setPlanQty(receipt.getActualQty());
        qcTask.setRelatedOrderType("purchase_receipt");
        qcTask.setRelatedOrderId(receiptId);
        qcTaskService.createTask(qcTask);

        receipt.setQcTaskId(qcTask.getId());
        receipt.setStatus("receiving");
        receipt.setQcStatus("pending_qc");
        updateById(receipt);

        // 通知质检人员
        try {
            notificationService.notifyRole("quality",
                    "采购收料[" + receipt.getReceiptNo() + "]已到货, 请安排来料检验",
                    null, null, "purchase_receipt", receiptId);
        } catch (Exception ignored) {}
    }

    // ======================== 收料操作 — 无需质检 ========================

    @Transactional(rollbackFor = Exception.class)
    public void receiveWithoutQc(Long receiptId, Long warehouseId) {
        BizPurchaseReceipt receipt = getById(receiptId);
        if (receipt == null) throw new BusinessException("收料单不存在");
        if (!"pending_receive".equals(receipt.getStatus())) throw new BusinessException("当前状态不允许收料操作");

        receipt.setWarehouseId(warehouseId);

        // 获取物料信息
        BizMaterial mat = materialMapper.selectById(receipt.getMaterialId());
        if (mat == null) throw new BusinessException("物料不存在");

        // 获取PO信息
        BizPurchaseOrder po = orderMapper.selectById(receipt.getPurchaseOrderId());
        String poNo = po != null ? po.getPoNo() : "";

        // 获取单价
        BizPurchaseOrderItem item = orderItemMapper.selectById(receipt.getOrderItemId());
        BigDecimal unitPrice = item != null ? item.getUnitPrice() : BigDecimal.ZERO;

        Long applicantId = SecurityUtil.getCurrentUserId();

        // 创建库存任务: invType=purchase_in, source=purchase_receipt
        invTaskService.createFromPurchaseReceipt(
                receiptId, receipt.getReceiptNo(), poNo, receipt.getBatchNo(),
                warehouseId, applicantId,
                mat.getId(), mat.getMaterialNo(), mat.getMaterialName(),
                mat.getSpec(), mat.getUnit(), receipt.getActualQty(), unitPrice);

        receipt.setStatus("receiving");
        receipt.setQcStatus("none");
        receipt.setInvTaskQty(receipt.getActualQty());
        updateById(receipt);
    }

    // ======================== 质检完成回调 ========================

    @Transactional(rollbackFor = Exception.class)
    public void onQcComplete(Long receiptId, BigDecimal qualifiedQty, BigDecimal defectQty) {
        BizPurchaseReceipt receipt = getById(receiptId);
        if (receipt == null) throw new BusinessException("收料单不存在");

        // 直接使用质检结果的合格数和不合格数
        BigDecimal actualQualified = qualifiedQty != null ? qualifiedQty : BigDecimal.ZERO;
        BigDecimal actualDefect = defectQty != null ? defectQty : BigDecimal.ZERO;
        receipt.setQualifiedQty(actualQualified);
        receipt.setDefectQty(actualDefect);
        receipt.setQcStatus("qc_done");
        // 后续使用 actualQualified 和 actualDefect 创建库存任务

        // 获取物料和PO信息
        BizMaterial mat = materialMapper.selectById(receipt.getMaterialId());
        if (mat == null) throw new BusinessException("物料不存在");
        BizPurchaseOrder po = orderMapper.selectById(receipt.getPurchaseOrderId());
        String poNo = po != null ? po.getPoNo() : "";
        BizPurchaseOrderItem item = orderItemMapper.selectById(receipt.getOrderItemId());
        BigDecimal unitPrice = item != null ? item.getUnitPrice() : BigDecimal.ZERO;
        Long applicantId = SecurityUtil.getCurrentUserId();

        // 获取质检任务编号
        BizQcTask qcTask = receipt.getQcTaskId() != null ? qcTaskService.getById(receipt.getQcTaskId()) : null;
        String qcTaskNo = qcTask != null ? qcTask.getTaskNo() : "";

        BigDecimal invTaskQtyTotal = BigDecimal.ZERO;

        // 库存任务数量 = 收料总数量 - 不合格数（不是质检合格数，因为质检是抽检）
        BigDecimal invQty = receipt.getActualQty().subtract(actualDefect);
        if (invQty.compareTo(BigDecimal.ZERO) > 0) {
            invTaskService.createFromQcTask(
                    receipt.getQcTaskId(), receipt.getReceiptNo(), poNo, receipt.getBatchNo(),
                    receipt.getWarehouseId(), applicantId,
                    mat.getId(), mat.getMaterialNo(), mat.getMaterialName(),
                    mat.getSpec(), mat.getUnit(), invQty, unitPrice,
                    "purchase_in", qcTaskNo);
            invTaskQtyTotal = invTaskQtyTotal.add(invQty);
        }

        // 不合格数退回到收料单：扣减收料单的actualQty
        if (actualDefect.compareTo(BigDecimal.ZERO) > 0) {
            receipt.setActualQty(receipt.getActualQty().subtract(actualDefect).max(BigDecimal.ZERO));
            // 同步扣减采购订单明细的receiptQty，允许重新生成收料单
            if (item != null) {
                BigDecimal currentReceiptQty = item.getReceiptQty() != null ? item.getReceiptQty() : BigDecimal.ZERO;
                item.setReceiptQty(currentReceiptQty.subtract(actualDefect).max(BigDecimal.ZERO));
                orderItemMapper.updateById(item);
            }
        }

        receipt.setInvTaskQty(invTaskQtyTotal);
        updateById(receipt);
    }

    // ======================== 库存任务处理回调 ========================

    @Transactional(rollbackFor = Exception.class)
    public void onInvTaskProcess(Long receiptId, BigDecimal processedQty) {
        BizPurchaseReceipt receipt = getById(receiptId);
        if (receipt == null) return;

        BigDecimal currentStockIn = receipt.getStockInQty() != null ? receipt.getStockInQty() : BigDecimal.ZERO;
        receipt.setStockInQty(currentStockIn.add(processedQty));

        // 判断是否全部入库 → 自动改为已收料
        boolean becameReceived = false;
        if (receipt.getStockInQty().compareTo(receipt.getActualQty()) >= 0
                && !"closed".equals(receipt.getStatus())
                && !"received".equals(receipt.getStatus())) {
            receipt.setStatus("received");
            becameReceived = true;
        }
        updateById(receipt);

        // 同步更新PO明细行的stockInQty
        BizPurchaseOrderItem item = orderItemMapper.selectById(receipt.getOrderItemId());
        if (item != null) {
            BigDecimal itemStockIn = item.getStockInQty() != null ? item.getStockInQty() : BigDecimal.ZERO;
            item.setStockInQty(itemStockIn.add(processedQty));
            orderItemMapper.updateById(item);
        }

        // 收料单状态变更 → 触发采购订单状态重算
        if (becameReceived && receipt.getPurchaseOrderId() != null) {
            purchaseOrderService.updateReceiptStatus(receipt.getPurchaseOrderId());
        }
    }

    // ======================== 库存任务退回回调 ========================

    @Transactional(rollbackFor = Exception.class)
    public void onInvTaskReturn(Long receiptId, BigDecimal returnedQty) {
        BizPurchaseReceipt receipt = getById(receiptId);
        if (receipt == null) return;

        BigDecimal currentStockIn = receipt.getStockInQty() != null ? receipt.getStockInQty() : BigDecimal.ZERO;
        BigDecimal currentInvTask = receipt.getInvTaskQty() != null ? receipt.getInvTaskQty() : BigDecimal.ZERO;
        receipt.setStockInQty(currentStockIn.subtract(returnedQty).max(BigDecimal.ZERO));
        receipt.setInvTaskQty(currentInvTask.subtract(returnedQty).max(BigDecimal.ZERO));

        // 回退收料单的actualQty
        BigDecimal currentActualQty = receipt.getActualQty() != null ? receipt.getActualQty() : BigDecimal.ZERO;
        receipt.setActualQty(currentActualQty.subtract(returnedQty).max(BigDecimal.ZERO));

        // 退回后如果之前是received，改回receiving；如果收料数为0改回pending_receive
        if (receipt.getActualQty().compareTo(BigDecimal.ZERO) <= 0) {
            receipt.setStatus("pending_receive");
        } else if ("received".equals(receipt.getStatus())) {
            receipt.setStatus("receiving");
        }
        updateById(receipt);

        // 同步回退采购订单明细的receiptQty，允许重新生成收料单
        BizPurchaseOrderItem item = orderItemMapper.selectById(receipt.getOrderItemId());
        if (item != null) {
            BigDecimal currentReceiptQty = item.getReceiptQty() != null ? item.getReceiptQty() : BigDecimal.ZERO;
            item.setReceiptQty(currentReceiptQty.subtract(returnedQty).max(BigDecimal.ZERO));
            orderItemMapper.updateById(item);
        }

        // 收料量回退 → 重算采购订单状态
        if (receipt.getPurchaseOrderId() != null) {
            purchaseOrderService.updateReceiptStatus(receipt.getPurchaseOrderId());
        }
    }

    // ======================== 整组汇总收料 ========================

    /**
     * 整组汇总收料: 把 groupId 下所有 pending_receive 状态的 receipt 一次性收料,
     * 共用同一个目标仓库 + 同一个质检选项(报检 / 放行).
     * 已经收过料(非 pending_receive)的 receipt 直接跳过, 不报错.
     *
     * @return 实际处理的 receipt 数量
     */
    @Transactional(rollbackFor = Exception.class)
    public int batchReceiveByGroup(String groupId, Long warehouseId, boolean needQc) {
        if (!StringUtils.hasText(groupId)) throw new BusinessException("批次组ID不能为空");
        if (warehouseId == null) throw new BusinessException("请选择目标仓库");

        List<BizPurchaseReceipt> pending = receiptMapper.selectList(
                new LambdaQueryWrapper<BizPurchaseReceipt>()
                        .eq(BizPurchaseReceipt::getReceiptGroupId, groupId)
                        .eq(BizPurchaseReceipt::getStatus, "pending_receive")
                        .eq(BizPurchaseReceipt::getDeleted, 0));
        if (pending.isEmpty()) throw new BusinessException("该批次组内无待收料的明细");

        for (BizPurchaseReceipt r : pending) {
            if (needQc) {
                receiveWithQc(r.getId(), warehouseId);
            } else {
                receiveWithoutQc(r.getId(), warehouseId);
            }
        }
        return pending.size();
    }

    // ======================== 办结 ========================

    @Transactional(rollbackFor = Exception.class)
    public void closeReceipt(Long receiptId) {
        BizPurchaseReceipt receipt = getById(receiptId);
        if (receipt == null) throw new BusinessException("收料单不存在");
        if ("closed".equals(receipt.getStatus())) throw new BusinessException("收料单已办结");
        receipt.setStatus("closed");
        updateById(receipt);
        if (receipt.getPurchaseOrderId() != null) {
            purchaseOrderService.updateReceiptStatus(receipt.getPurchaseOrderId());
        }
    }

    // ======================== 关联质检任务 ========================

    public List<Map<String, Object>> getRelatedQcTasks(Long receiptId) {
        BizPurchaseReceipt receipt = getById(receiptId);
        if (receipt == null) return List.of();

        List<BizQcTask> tasks = qcTaskService.list(
                new LambdaQueryWrapper<BizQcTask>()
                        .eq(BizQcTask::getRelatedOrderType, "purchase_receipt")
                        .eq(BizQcTask::getRelatedOrderId, receiptId)
                        .orderByDesc(BizQcTask::getCreateTime));

        return tasks.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("taskNo", t.getTaskNo());
            m.put("qcType", t.getQcType());
            m.put("result", t.getResult());
            m.put("planQty", t.getPlanQty());
            m.put("passQty", t.getPassQty());
            m.put("failQty", t.getFailQty());
            m.put("conclusion", t.getConclusion());
            m.put("inspectedAt", t.getInspectedAt());
            m.put("createTime", t.getCreateTime());
            return m;
        }).toList();
    }

    // ======================== 关联库存任务 ========================

    public List<Map<String, Object>> getRelatedInvTasks(Long receiptId) {
        BizPurchaseReceipt receipt = getById(receiptId);
        if (receipt == null) return List.of();

        // 查找直接关联的库存任务(source=purchase_receipt)
        // 以及通过质检任务关联的库存任务(source=qc_task)
        List<Map<String, Object>> result = new ArrayList<>();

        // 直接关联
        result.addAll(invTaskService.findBySource("purchase_receipt", receiptId));

        // 通过质检任务关联
        if (receipt.getQcTaskId() != null) {
            result.addAll(invTaskService.findBySource("qc_task", receipt.getQcTaskId()));
        }

        return result;
    }
}
