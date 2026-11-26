package com.aimes.modules.dataimport.service.simulation;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.utils.CodeUtil;
import com.aimes.common.utils.ExcelUtil;
import com.aimes.modules.inventory.entity.BizInvTask;
import com.aimes.modules.inventory.entity.BizInvTaskItem;
import com.aimes.modules.inventory.entity.BizInvTransaction;
import com.aimes.modules.inventory.mapper.BizInvTaskMapper;
import com.aimes.modules.inventory.mapper.BizInvTaskItemMapper;
import com.aimes.modules.inventory.mapper.BizInvTransactionMapper;
import com.aimes.modules.master.entity.BizMaterial;
import com.aimes.modules.master.mapper.BizMaterialMapper;
import com.aimes.modules.purchase.entity.BizPurchaseOrder;
import com.aimes.modules.purchase.entity.BizPurchaseOrderItem;
import com.aimes.modules.purchase.entity.BizPurchaseReceipt;
import com.aimes.modules.purchase.mapper.BizPurchaseOrderItemMapper;
import com.aimes.modules.purchase.mapper.BizPurchaseOrderMapper;
import com.aimes.modules.purchase.mapper.BizPurchaseReceiptMapper;
import com.aimes.modules.quality.entity.BizQcRecord;
import com.aimes.modules.quality.entity.BizQcResult;
import com.aimes.modules.quality.entity.BizQcTask;
import com.aimes.modules.quality.entity.BizQcTemplateItem;
import com.aimes.modules.quality.mapper.BizQcRecordMapper;
import com.aimes.modules.quality.mapper.BizQcResultMapper;
import com.aimes.modules.quality.mapper.BizQcTaskMapper;
import com.aimes.modules.quality.mapper.BizQcTemplateItemMapper;
import com.aimes.modules.system.entity.SysUser;
import com.aimes.modules.system.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static com.aimes.modules.dataimport.service.simulation.SimTimeHelper.*;

/**
 * 4 采购来料生成器
 * 采购订单 -> 采购收料单 -> 质检任务 + 质检记录
 * 读取采购订单(已收货/关闭) -> 生成收料单 BizPurchaseReceipt + BizQcTask + BizQcRecord
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class QcTaskGenerator {

    private final BizPurchaseOrderMapper purchaseOrderMapper;
    private final BizPurchaseOrderItemMapper purchaseOrderItemMapper;
    private final BizPurchaseReceiptMapper purchaseReceiptMapper;
    private final BizQcTaskMapper qcTaskMapper;
    private final BizQcRecordMapper qcRecordMapper;
    private final BizQcResultMapper qcResultMapper;
    private final BizQcTemplateItemMapper qcTemplateItemMapper;
    private final BizMaterialMapper materialMapper;
    private final com.aimes.modules.quality.mapper.BizQcTemplateMapper qcTemplateMapper;
    private final BizInvTaskMapper invTaskMapper;
    private final BizInvTaskItemMapper invTaskItemMapper;
    private final BizInvTransactionMapper invTransactionMapper;
    private final SysUserMapper userMapper;

    public static final List<String> RECEIPT_HEADERS = List.of(
            "收料单号", "采购订单号(关联)", "物料编号", "物料名称",
            "预期数量", "实际数量", "批次号", "收料日期", "收货人",
            "仓库", "合格数量", "不良数量", "是否质检", "质检模板",
            "状态", "质检状态", "创建时间", "创建人");

    public static final List<String> TASK_HEADERS = List.of(
            "任务编号", "质检类型", "物料编号", "物料名称",
            "关联单据类型", "关联收料单号", "质检模板",
            "计划数量", "合格数量", "不合格数量", "结果", "检验员",
            "检验时间", "处置状态", "创建时间", "创建人");

    public static final List<String> RECORD_HEADERS = List.of(
            "质检任务编号(关联)", "记录类型", "数量", "批次号", "报告人", "创建时间", "创建人");

    public static final List<String> INV_TASK_HEADERS = List.of(
            "任务编号", "关联单据号", "库存类型", "状态", "来源类型", "申请人", "仓库ID", "创建时间", "创建人");
    public static final List<String> INV_TASK_ITEM_HEADERS = List.of(
            "任务编号(关联)", "物料编号", "物料名称", "单位", "数量", "已处理数量", "仓库ID");
    public static final List<String> INV_TRANSACTION_HEADERS = List.of(
            "物料编号", "仓库ID", "交易类型", "数量", "关联单据类型", "关联单据号", "创建时间");

    private List<List<Object>> cachedReceiptRows;
    private List<List<Object>> cachedTaskRows;
    private List<List<Object>> cachedRecordRows;
    private List<List<Object>> cachedInvTaskRows;
    private List<List<Object>> cachedInvTaskItemRows;
    private List<List<Object>> cachedInvTransactionRows;
    private List<String> allTemplateNames;

    public Map<String, Object> generate(SimConfig cfg) {
        Set<Long> allIds = new HashSet<>();
        allIds.addAll(cfg.getCreatorIds());
        allIds.addAll(cfg.getInspectorIds());
        allIds.addAll(cfg.getReceiverIds());
        Map<Long, String> userNames = loadUserNames(new ArrayList<>(allIds));

        Map<Long, BizMaterial> matMap = materialMapper.selectList(
                new LambdaQueryWrapper<BizMaterial>().eq(BizMaterial::getDeleted, 0))
                .stream().collect(Collectors.toMap(BizMaterial::getId, m -> m, (a, b) -> a));

        // 查询已收货/关闭的采购订单
        List<BizPurchaseOrder> poList = purchaseOrderMapper.selectList(
                new LambdaQueryWrapper<BizPurchaseOrder>()
                        .eq(BizPurchaseOrder::getDeleted, 0)
                        .notIn(BizPurchaseOrder::getStatus, List.of("draft", "cancelled", "rejected")));

        // 多线程: 每个采购订单独立处理,无跨订单依赖
        cachedReceiptRows = java.util.Collections.synchronizedList(new ArrayList<>());
        cachedTaskRows = java.util.Collections.synchronizedList(new ArrayList<>());
        cachedRecordRows = java.util.Collections.synchronizedList(new ArrayList<>());
        cachedInvTaskRows = java.util.Collections.synchronizedList(new ArrayList<>());
        cachedInvTaskItemRows = java.util.Collections.synchronizedList(new ArrayList<>());
        cachedInvTransactionRows = java.util.Collections.synchronizedList(new ArrayList<>());

        // 加载质检模板名称列表（用于导出Excel下拉框）
        allTemplateNames = qcTemplateMapper.selectList(
                new LambdaQueryWrapper<com.aimes.modules.quality.entity.BizQcTemplate>()
                        .eq(com.aimes.modules.quality.entity.BizQcTemplate::getDeleted, 0))
                .stream().map(com.aimes.modules.quality.entity.BizQcTemplate::getTemplateName)
                .filter(n -> n != null && !n.isEmpty()).distinct().sorted().collect(Collectors.toList());

        poList.parallelStream().forEach(po -> {
            List<BizPurchaseOrderItem> items = purchaseOrderItemMapper.selectList(
                    new LambdaQueryWrapper<BizPurchaseOrderItem>()
                            .eq(BizPurchaseOrderItem::getPurchaseOrderId, po.getId())
                            .eq(BizPurchaseOrderItem::getDeleted, 0));

            for (BizPurchaseOrderItem item : items) {
                if (item.getMaterialId() == null || item.getQuantity() == null) return;
                BigDecimal qty = item.getQuantity();
                if (qty.compareTo(BigDecimal.ZERO) <= 0) return;

                BizMaterial mat = matMap.get(item.getMaterialId());
                if (mat == null) return;

                // 查找物料关联的质检模板（优先applyMaterialId匹配，其次默认模板）
                String defaultTemplateName = "";
                com.aimes.modules.quality.entity.BizQcTemplate matchedTpl = qcTemplateMapper.selectOne(
                        new LambdaQueryWrapper<com.aimes.modules.quality.entity.BizQcTemplate>()
                                .eq(com.aimes.modules.quality.entity.BizQcTemplate::getDeleted, 0)
                                .eq(com.aimes.modules.quality.entity.BizQcTemplate::getApplyMaterialId, item.getMaterialId())
                                .last("LIMIT 1"));
                if (matchedTpl == null) {
                    // 没有物料专用模板，找默认模板
                    matchedTpl = qcTemplateMapper.selectOne(
                            new LambdaQueryWrapper<com.aimes.modules.quality.entity.BizQcTemplate>()
                                    .eq(com.aimes.modules.quality.entity.BizQcTemplate::getDeleted, 0)
                                    .eq(com.aimes.modules.quality.entity.BizQcTemplate::getIsDefault, 1)
                                    .last("LIMIT 1"));
                }
                if (matchedTpl == null && !allTemplateNames.isEmpty()) {
                    // 还没有就取第一个模板
                    defaultTemplateName = allTemplateNames.get(0);
                } else if (matchedTpl != null) {
                    defaultTemplateName = matchedTpl.getTemplateName() != null ? matchedTpl.getTemplateName() : "";
                }

                // === 时间链 ===
                // 基准日期 = 采购订单交货日期
                LocalDate baseDate = po.getDeliveryDate() != null ? po.getDeliveryDate() :
                        (po.getCreateTime() != null ? po.getCreateTime().toLocalDate().plusDays(7) : LocalDate.now());

                // 收料日期 = 交货日期 - 提前N个工作日
                LocalDate receivedDate = addWorkingDays(baseDate, -cfg.randomReceiptDelayDays(), cfg);

                // 截止日过滤：收料日在截止日之后 → 该收料单尚未发生，跳过整条记录(收料/质检/入库都不生成)
                if (cfg.isAfterCutoff(receivedDate)) return;

                // 收料处理时间 = 收料日期当天随机工作时间
                LocalDateTime receiptProcessTime = randomTime(receivedDate, cfg);
                if (receiptProcessTime == null) return;

                // 质检创建时间 = 收料处理时间 + randomReceiptProcessHours小时（超出工作时间顺延）
                LocalDateTime qcCreateTime = cfg.calcQcTime(receiptProcessTime);
                if (qcCreateTime == null) return;

                // 质检检验时间 = 质检创建时间 + calcQcTime延迟
                LocalDateTime qcInspectedAt = cfg.calcQcTime(qcCreateTime);
                if (qcInspectedAt == null) return;

                // 入库时间提前计算,null 则整条记录跳过(避免只有收料+质检没入库的孤立数据)
                LocalDateTime invTimeEarly = cfg.calcNextStepTime(qcInspectedAt);
                if (invTimeEarly == null) return;

                // === 质检判定 ===
                boolean pass = cfg.isQcPass();
                BigDecimal passQty, failQty;
                if (pass) {
                    passQty = qty;
                    failQty = BigDecimal.ZERO;
                } else {
                    double pctRate = cfg.randomPassQtyRate();
                    passQty = qty.multiply(BigDecimal.valueOf(pctRate)).setScale(0, RoundingMode.FLOOR);
                    failQty = qty.subtract(passQty);
                    if (failQty.compareTo(BigDecimal.ZERO) <= 0) {
                        failQty = BigDecimal.ONE;
                        passQty = qty.subtract(BigDecimal.ONE);
                    }
                }

                // === 人员 ===
                Long receiverId = cfg.pickReceiver();
                String receiverName = userNames.getOrDefault(receiverId, receiverId != null ? String.valueOf(receiverId) : "");
                Long inspectorId = cfg.pickInspector();
                String inspectorName = userNames.getOrDefault(inspectorId, "");
                Long creatorId = cfg.pickCreator();
                String creatorName = userNames.getOrDefault(creatorId, "");

                // === 收料单号和批次号 ===
                String receiptNo = CodeUtil.generate("PR");
                String batchNo = "B" + receivedDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                String whId = String.valueOf(mat.getDefaultWarehouseId() != null ? mat.getDefaultWarehouseId() :
                        (cfg.getWarehouseId() != null ? cfg.getWarehouseId() : 1));

                // === 是否需要质检（默认全部需要，用户可在Excel中修改） ===
                boolean needQc = true;
                String qcStatus = needQc ? "qc_done" : "none";

                // === 1. 生成收料单行 ===
                cachedReceiptRows.add(List.of(
                        receiptNo, po.getPoNo(), mat.getMaterialNo(), mat.getMaterialName(),
                        qty.toPlainString(), qty.toPlainString(), batchNo, fmtDate(receivedDate),
                        receiverName, whId,
                        passQty.toPlainString(), failQty.toPlainString(),
                        needQc ? "是" : "否", needQc ? defaultTemplateName : "",  // 是否质检, 质检模板
                        "closed", qcStatus,
                        fmtDateTime(receiptProcessTime), creatorName));

                // === 2. 如果需要质检，生成质检任务行和质检记录行 ===
                if (needQc) {
                    String taskNo = CodeUtil.generate("QC");

                    cachedTaskRows.add(List.of(
                            taskNo, "incoming",  // qcType=incoming（来料检验）
                            mat.getMaterialNo(), mat.getMaterialName(),
                            "purchase_receipt", receiptNo, defaultTemplateName,  // 关联单据类型, 关联单号, 质检模板
                            qty.toPlainString(), passQty.toPlainString(), failQty.toPlainString(),
                            pass ? "pass" : "fail", inspectorName,  // 结果:pass/fail (generateQcResults 依赖)
                            fmtDateTime(qcInspectedAt), pass ? "" : "undisposed",
                            fmtDateTime(qcCreateTime), creatorName));

                    if (passQty.compareTo(BigDecimal.ZERO) > 0) {
                        cachedRecordRows.add(List.of(
                                taskNo, "qualified", passQty.toPlainString(), batchNo,
                                inspectorName, fmtDateTime(qcInspectedAt), creatorName));
                    }
                    if (failQty.compareTo(BigDecimal.ZERO) > 0) {
                        cachedRecordRows.add(List.of(
                                taskNo, "unqualified", failQty.toPlainString(), batchNo,
                                inspectorName, fmtDateTime(qcInspectedAt), creatorName));
                    }
                }

                // === 3. 生成采购入库库存任务(purchase_in) ===
                if (passQty.compareTo(BigDecimal.ZERO) > 0) {
                    String invTaskNo = CodeUtil.generate("INK");
                    LocalDateTime invTime = invTimeEarly;

                    cachedInvTaskRows.add(List.of(
                            invTaskNo, receiptNo, "purchase_in", "completed",
                            "purchase_receipt", creatorName, whId,
                            fmtDateTime(invTime), creatorName));

                    String matUnit = mat.getUnit() != null ? mat.getUnit() : "";
                    cachedInvTaskItemRows.add(List.of(
                            invTaskNo, mat.getMaterialNo(), mat.getMaterialName(),
                            matUnit, passQty.toPlainString(), passQty.toPlainString(), whId));

                    cachedInvTransactionRows.add(List.of(
                            mat.getMaterialNo(), whId, "purchase_in", passQty.toPlainString(),
                            "purchase_receipt", receiptNo, fmtDateTime(invTime)));
                }
            }
        }); // end parallel forEach over purchase orders

        Map<String, Object> tables = new LinkedHashMap<>();
        tables.put("purchase_receipt", cachedReceiptRows.size());
        tables.put("qc_task", cachedTaskRows.size());
        tables.put("qc_record", cachedRecordRows.size());
        tables.put("inv_task", cachedInvTaskRows.size());
        tables.put("inv_task_item", cachedInvTaskItemRows.size());
        tables.put("inv_transaction", cachedInvTransactionRows.size());
        int total = cachedReceiptRows.size() + cachedTaskRows.size() + cachedRecordRows.size()
                + cachedInvTaskRows.size() + cachedInvTaskItemRows.size() + cachedInvTransactionRows.size();
        return Map.of("tables", tables, "totalRows", total);
    }

    public void exportZip(HttpServletResponse response) throws IOException {
        if (cachedReceiptRows == null) throw new BusinessException("请先执行生成");

        response.setContentType("application/zip");
        response.setHeader("Content-Disposition",
                "attachment; filename=" + java.net.URLEncoder.encode("04_采购来料.zip", "UTF-8"));

        Map<Integer, List<String>> receiptDropdowns = new LinkedHashMap<>();
        receiptDropdowns.put(12, List.of("是", "否"));
        receiptDropdowns.put(13, allTemplateNames != null ? allTemplateNames : List.of());
        Map<Integer, List<String>> taskDropdowns = new LinkedHashMap<>();
        taskDropdowns.put(6, allTemplateNames != null ? allTemplateNames : List.of());

        List<SimTimeHelper.SheetSpec> sheets = new ArrayList<>();
        sheets.add(new SimTimeHelper.SheetSpec("04_采购来料_receipt.xlsx", RECEIPT_HEADERS, cachedReceiptRows, receiptDropdowns));
        sheets.add(new SimTimeHelper.SheetSpec("04_采购来料_qc_task.xlsx", TASK_HEADERS, cachedTaskRows, taskDropdowns));
        sheets.add(new SimTimeHelper.SheetSpec("04_采购来料_qc_record.xlsx", RECORD_HEADERS, cachedRecordRows));
        sheets.add(new SimTimeHelper.SheetSpec("04_采购来料_inv_task.xlsx", INV_TASK_HEADERS, cachedInvTaskRows));
        sheets.add(new SimTimeHelper.SheetSpec("04_采购来料_inv_task_item.xlsx", INV_TASK_ITEM_HEADERS, cachedInvTaskItemRows));
        sheets.add(new SimTimeHelper.SheetSpec("04_采购来料_inv_transaction.xlsx", INV_TRANSACTION_HEADERS, cachedInvTransactionRows));

        try (var zos = new java.util.zip.ZipOutputStream(response.getOutputStream())) {
            SimTimeHelper.parallelSplitWriteToZip(zos, sheets, 50_000, 10);
        }
    }

    @Transactional
    public Map<String, Object> importData(MultipartFile receiptFile, MultipartFile taskFile,
                                           MultipartFile[] recordFiles, MultipartFile invTaskFile,
                                           MultipartFile[] invTaskItemFiles, MultipartFile[] invTxFiles) {
        if (receiptFile == null || receiptFile.isEmpty()) throw new BusinessException("请上传收料单文件");
        // 多线程: 段间顺序保留,段内行级并行
        java.util.concurrent.atomic.AtomicInteger success = new java.util.concurrent.atomic.AtomicInteger();
        List<String> errors = java.util.Collections.synchronizedList(new ArrayList<>());

        Map<String, Long> userNameToId = loadUserNameToIdMap();
        Map<String, Long> materialNoToId = materialMapper.selectList(
                new LambdaQueryWrapper<BizMaterial>().eq(BizMaterial::getDeleted, 0))
                .stream().filter(m -> m.getMaterialNo() != null)
                .collect(Collectors.toMap(BizMaterial::getMaterialNo, BizMaterial::getId, (a, b) -> a));

        // 采购单号->ID
        Map<String, Long> poNoToId = purchaseOrderMapper.selectList(
                new LambdaQueryWrapper<BizPurchaseOrder>().eq(BizPurchaseOrder::getDeleted, 0))
                .stream().collect(Collectors.toMap(BizPurchaseOrder::getPoNo, BizPurchaseOrder::getId, (a, b) -> a));

        // 采购订单号->采购订单行（按materialId索引）
        Map<String, List<BizPurchaseOrderItem>> poNoToItems = new HashMap<>();
        for (var entry : poNoToId.entrySet()) {
            List<BizPurchaseOrderItem> items = purchaseOrderItemMapper.selectList(
                    new LambdaQueryWrapper<BizPurchaseOrderItem>()
                            .eq(BizPurchaseOrderItem::getPurchaseOrderId, entry.getValue())
                            .eq(BizPurchaseOrderItem::getDeleted, 0));
            poNoToItems.put(entry.getKey(), items);
        }

        // 质检模板名称->ID
        Map<String, Long> tplNameToId = qcTemplateMapper.selectList(
                new LambdaQueryWrapper<com.aimes.modules.quality.entity.BizQcTemplate>()
                        .eq(com.aimes.modules.quality.entity.BizQcTemplate::getDeleted, 0))
                .stream().collect(Collectors.toMap(
                        com.aimes.modules.quality.entity.BizQcTemplate::getTemplateName,
                        com.aimes.modules.quality.entity.BizQcTemplate::getId, (a, b) -> a));

        // === 1. 导入收料单 ===
        // 串行 — 多条 receipt 属于同一 PO/POItem 时并发 updateById 会触发 InnoDB 行锁等待超时
        // (跟之前 SO advanceToInProduction 同样的竞争模式)。改串行 + PO 状态推进收集到末尾批量做,
        // POItem 累加保留在循环里(串行无锁竞争)
        List<Map<String, String>> receiptRows = ExcelUtil.parseImport(receiptFile, RECEIPT_HEADERS);
        Map<String, Long> receiptNoToId = new HashMap<>();
        // 记录用户在 Excel 中设置为 "否" 的收料单号，跳过对应的质检任务
        Set<String> noQcReceiptNos = new HashSet<>();
        // 累计每个 PO 的 receipt,循环结束后一次性推进 status,避免循环内对同一 PO 反复 update
        Set<Long> touchedPoIds = new HashSet<>();

        for (int i = 0; i < receiptRows.size(); i++) {
            Map<String, String> r = receiptRows.get(i);
            try {
                String poNo = r.get("采购订单号(关联)");
                Long poId = poNoToId.get(poNo);
                if (poId == null) {
                    errors.add("receipt第" + (i + 2) + "行: 采购订单不存在 " + poNo);
                    continue;
                }

                Long materialId = materialNoToId.get(r.get("物料编号"));

                // 读取"是否质检"：否 / 无需 / N / false / 0 → 不走质检流程
                String needQcStr = r.get("是否质检");
                boolean skipQc = false;
                if (needQcStr != null) {
                    String s = needQcStr.trim().toLowerCase();
                    skipQc = s.equals("否") || s.equals("无") || s.equals("无需") || s.equals("无需质检")
                            || s.equals("n") || s.equals("no") || s.equals("false") || s.equals("0");
                }
                if (skipQc) {
                    noQcReceiptNos.add(r.get("收料单号"));
                }

                // 匹配采购订单行项
                Long orderItemId = null;
                List<BizPurchaseOrderItem> poItems = poNoToItems.get(poNo);
                if (poItems != null && materialId != null) {
                    for (BizPurchaseOrderItem poItem : poItems) {
                        if (materialId.equals(poItem.getMaterialId())) {
                            orderItemId = poItem.getId();
                            break;
                        }
                    }
                }

                BizPurchaseReceipt receipt = new BizPurchaseReceipt();
                receipt.setReceiptNo(r.get("收料单号"));
                receipt.setPurchaseOrderId(poId);
                receipt.setOrderItemId(orderItemId);
                receipt.setMaterialId(materialId);
                receipt.setExpectedQty(parseBD(r.get("预期数量")));
                receipt.setActualQty(parseBD(r.get("实际数量")));
                receipt.setBatchNo(r.get("批次号"));
                receipt.setReceivedDate(parseDate(r.get("收料日期")));
                receipt.setReceiverId(userNameToId.get(r.get("收货人")));
                String whStr = r.get("仓库");
                if (whStr != null && !whStr.isEmpty()) {
                    try { receipt.setWarehouseId(Long.valueOf(whStr.trim())); } catch (Exception ignored) {}
                }
                // 无质检时:合格数强制 = 实收数,不良数强制 = 0,避免"无质检却有不合格"的自相矛盾
                if (skipQc) {
                    receipt.setQualifiedQty(receipt.getActualQty());
                    receipt.setDefectQty(BigDecimal.ZERO);
                } else {
                    receipt.setQualifiedQty(parseBD(r.get("合格数量")));
                    receipt.setDefectQty(parseBD(r.get("不良数量")));
                }
                receipt.setStatus(r.get("状态"));
                // 如果用户在Excel中选择"否"，则质检状态强制为 none
                receipt.setQcStatus(skipQc ? "none" : r.get("质检状态"));

                // 质检模板 -> 暂存到remark（收料单实体无模板字段）
                String tplName = r.get("质检模板");
                if (tplName != null && !tplName.isEmpty()) {
                    receipt.setRemark("质检模板:" + tplName);
                }

                receipt.setCreateTime(parseDatetime(r.get("创建时间")));
                receipt.setCreateBy(userNameToId.get(r.get("创建人")));
                receipt.setUpdateTime(receipt.getCreateTime());
                receipt.setUpdateBy(receipt.getCreateBy());

                purchaseReceiptMapper.insert(receipt);
                receiptNoToId.put(receipt.getReceiptNo(), receipt.getId());

                // 回写收料单的invTaskQty和stockInQty（入库数=合格数）
                BigDecimal qualifiedQty = receipt.getQualifiedQty() != null ? receipt.getQualifiedQty() : receipt.getActualQty();
                receipt.setInvTaskQty(qualifiedQty);
                receipt.setStockInQty(qualifiedQty);
                purchaseReceiptMapper.updateById(receipt);

                // 回写采购订单行的receiptQty和stockInQty
                if (orderItemId != null && receipt.getActualQty() != null) {
                    BizPurchaseOrderItem poItem = purchaseOrderItemMapper.selectById(orderItemId);
                    if (poItem != null) {
                        BigDecimal addQty = receipt.getActualQty();
                        poItem.setReceiptQty((poItem.getReceiptQty() != null ? poItem.getReceiptQty() : BigDecimal.ZERO).add(addQty));
                        poItem.setStockInQty((poItem.getStockInQty() != null ? poItem.getStockInQty() : BigDecimal.ZERO).add(qualifiedQty));
                        poItem.setQualifiedQty((poItem.getQualifiedQty() != null ? poItem.getQualifiedQty() : BigDecimal.ZERO).add(qualifiedQty));
                        poItem.setInvTaskQty((poItem.getInvTaskQty() != null ? poItem.getInvTaskQty() : BigDecimal.ZERO).add(qualifiedQty));
                        purchaseOrderItemMapper.updateById(poItem);
                    }
                }

                // 收集 PO ID,循环结束后统一推进状态(避免循环内 N 次 update 同一 PO)
                touchedPoIds.add(poId);

                success.incrementAndGet();
            } catch (Exception e) {
                errors.add("receipt第" + (i + 2) + "行: " + e.getMessage());
            }
        }

        // 循环结束后批量推进 PO 状态 — 每个 PO 只算一次, 避免锁竞争
        for (Long poId : touchedPoIds) {
            try {
                BizPurchaseOrder po = purchaseOrderMapper.selectById(poId);
                if (po == null || "closed".equals(po.getStatus())) continue;
                List<BizPurchaseOrderItem> allItems = purchaseOrderItemMapper.selectList(
                        new LambdaQueryWrapper<BizPurchaseOrderItem>()
                                .eq(BizPurchaseOrderItem::getPurchaseOrderId, poId)
                                .eq(BizPurchaseOrderItem::getDeleted, 0));
                boolean allReceived = true;
                boolean anyReceived = false;
                for (BizPurchaseOrderItem itm : allItems) {
                    BigDecimal rQty = itm.getReceiptQty() != null ? itm.getReceiptQty() : BigDecimal.ZERO;
                    BigDecimal oQty = itm.getQuantity() != null ? itm.getQuantity() : BigDecimal.ZERO;
                    if (rQty.compareTo(oQty) < 0) allReceived = false;
                    if (rQty.compareTo(BigDecimal.ZERO) > 0) anyReceived = true;
                }
                if (allReceived) {
                    po.setStatus("closed");
                    po.setInvTaskGenStatus("fully_generated");
                    po.setInvTaskProcessStatus("fully_processed");
                } else if (anyReceived) {
                    po.setStatus("partial_received");
                }
                purchaseOrderMapper.updateById(po);
            } catch (Exception e) {
                errors.add("PO状态推进失败(poId=" + poId + "): " + e.getMessage());
            }
        }

        // === 2. 导入质检任务（如有） ===
        Map<String, Long> taskNoToQcTaskId = new HashMap<>();

        if (taskFile != null && !taskFile.isEmpty()) {
            List<Map<String, String>> taskRows = ExcelUtil.parseImport(taskFile, TASK_HEADERS);
            java.util.stream.IntStream.range(0, taskRows.size()).parallel().forEach(i -> {
                Map<String, String> r = taskRows.get(i);
                try {
                    // 如果对应的收料单被用户标记为不质检，跳过此任务
                    String relatedReceiptNo = r.get("关联收料单号");
                    if (noQcReceiptNos.contains(relatedReceiptNo)) {
                        return;
                    }

                    BizQcTask qcTask = new BizQcTask();
                    qcTask.setTaskNo(r.get("任务编号"));
                    qcTask.setQcType(r.get("质检类型"));
                    Long matId = materialNoToId.get(r.get("物料编号"));
                    qcTask.setMaterialId(matId);
                    qcTask.setProductId(matId);
                    qcTask.setRelatedOrderType(r.get("关联单据类型"));

                    // 关联收料单
                    Long receiptId = receiptNoToId.get(relatedReceiptNo);
                    qcTask.setRelatedOrderId(receiptId);

                    // 质检模板
                    String tplName = r.get("质检模板");
                    if (tplName != null && !tplName.isEmpty()) {
                        qcTask.setTemplateId(tplNameToId.get(tplName));
                    }

                    qcTask.setPlanQty(parseBD(r.get("计划数量")));
                    qcTask.setPassQty(parseBD(r.get("合格数量")));
                    qcTask.setFailQty(parseBD(r.get("不合格数量")));
                    qcTask.setResult(r.get("结果"));
                    qcTask.setInspectorId(userNameToId.get(r.get("检验员")));
                    qcTask.setInspectedAt(parseDatetime(r.get("检验时间")));
                    qcTask.setDisposalStatus(r.get("处置状态"));
                    qcTask.setCreateTime(parseDatetime(r.get("创建时间")));
                    qcTask.setCreateBy(userNameToId.get(r.get("创建人")));
                    qcTask.setUpdateTime(qcTask.getInspectedAt() != null ? qcTask.getInspectedAt() : qcTask.getCreateTime());
                    qcTask.setUpdateBy(qcTask.getCreateBy());
                    qcTaskMapper.insert(qcTask);
                    taskNoToQcTaskId.put(qcTask.getTaskNo(), qcTask.getId());

                    // 根据质检模板生成明细结果（单选/文本默认值）
                    if (qcTask.getTemplateId() != null) {
                        List<BizQcTemplateItem> tplItems = qcTemplateItemMapper.selectList(
                                new LambdaQueryWrapper<BizQcTemplateItem>()
                                        .eq(BizQcTemplateItem::getTemplateId, qcTask.getTemplateId())
                                        .eq(BizQcTemplateItem::getDeleted, 0)
                                        .orderByAsc(BizQcTemplateItem::getSortOrder));
                        boolean isPass = "pass".equals(qcTask.getResult());
                        for (BizQcTemplateItem ti : tplItems) {
                            BizQcResult rs = new BizQcResult();
                            rs.setQcTaskId(qcTask.getId());
                            rs.setTemplateItemId(ti.getId());
                            rs.setItemName(ti.getItemName());
                            rs.setStandardValue(ti.getStandardValue());
                            rs.setToleranceMin(ti.getToleranceMin());
                            rs.setToleranceMax(ti.getToleranceMax());
                            rs.setUnit(ti.getUnit());
                            rs.setMethod(ti.getMethod());
                            rs.setItemType(ti.getItemType());
                            rs.setDefaultValue(ti.getDefaultValue());
                            rs.setIsRequired(ti.getIsRequired());
                            rs.setIsCritical(ti.getIsCritical());
                            // 根据类型填写合理默认值
                            String actualVal;
                            if ("quantitative".equalsIgnoreCase(ti.getItemType())) {
                                actualVal = ti.getDefaultValue() != null && !ti.getDefaultValue().isEmpty() ?
                                        ti.getDefaultValue() : ti.getStandardValue();
                            } else if ("radio".equalsIgnoreCase(ti.getItemType())) {
                                actualVal = isPass ? "合格" : "不合格";
                            } else {
                                actualVal = ti.getDefaultValue() != null && !ti.getDefaultValue().isEmpty() ?
                                        ti.getDefaultValue() : (isPass ? "正常" : "存在异常");
                            }
                            rs.setActualValue(actualVal);
                            rs.setIsPass(isPass ? 1 : 0);
                            rs.setCreateTime(qcTask.getInspectedAt() != null ? qcTask.getInspectedAt() : qcTask.getCreateTime());
                            rs.setCreateBy(qcTask.getCreateBy());
                            rs.setUpdateTime(rs.getCreateTime());
                            rs.setUpdateBy(rs.getCreateBy());
                            qcResultMapper.insert(rs);
                        }
                    }

                    // 回写收料单的qcTaskId
                    if (receiptId != null) {
                        BizPurchaseReceipt receipt = purchaseReceiptMapper.selectById(receiptId);
                        if (receipt != null) {
                            receipt.setQcTaskId(qcTask.getId());
                            purchaseReceiptMapper.updateById(receipt);
                        }
                    }

                    success.incrementAndGet();
                } catch (Exception e) {
                    errors.add("qc_task第" + (i + 2) + "行: " + e.getMessage());
                }
            });
        }

        // === 3. 导入质检记录（如有） ===
        List<Map<String, String>> recordAllRows = parseAllParts(recordFiles, RECORD_HEADERS);
        if (!recordAllRows.isEmpty()) {
            List<Map<String, String>> recordRows = recordAllRows;
            java.util.stream.IntStream.range(0, recordRows.size()).parallel().forEach(i -> {
                Map<String, String> r = recordRows.get(i);
                try {
                    String taskNo = r.get("质检任务编号(关联)");
                    Long qcTaskId = taskNoToQcTaskId.get(taskNo);
                    if (qcTaskId == null) {
                        // 对应任务被跳过（用户选择不质检），静默跳过记录
                        return;
                    }

                    BizQcRecord record = new BizQcRecord();
                    record.setQcTaskId(qcTaskId);
                    record.setRecordType(r.get("记录类型"));
                    record.setQuantity(parseBD(r.get("数量")));
                    record.setBatchNo(r.get("批次号"));
                    record.setReporterId(userNameToId.get(r.get("报告人")));
                    record.setCreateTime(parseDatetime(r.get("创建时间")));
                    record.setCreateBy(userNameToId.get(r.get("创建人")));
                    record.setUpdateTime(record.getCreateTime());
                    record.setUpdateBy(record.getCreateBy());
                    qcRecordMapper.insert(record);
                    success.incrementAndGet();
                } catch (Exception e) {
                    errors.add("qc_record第" + (i + 2) + "行: " + e.getMessage());
                }
            });
        }

        // === 4. 导入库存任务 ===
        Map<String, Long> invTaskNoToId = new HashMap<>();
        if (invTaskFile != null && !invTaskFile.isEmpty()) {
            List<Map<String, String>> invTaskRows = ExcelUtil.parseImport(invTaskFile, INV_TASK_HEADERS);
            java.util.stream.IntStream.range(0, invTaskRows.size()).parallel().forEach(i -> {
                Map<String, String> r = invTaskRows.get(i);
                try {
                    BizInvTask t = new BizInvTask();
                    t.setTaskNo(r.get("任务编号"));
                    String relatedNo = r.get("关联单据号");
                    t.setRelatedOrderNo(relatedNo);
                    t.setInvType(r.get("库存类型"));
                    t.setStatus(r.get("状态"));
                    String sourceType = r.get("来源类型");
                    t.setSourceType(sourceType);
                    // 根据 sourceType + relatedNo 反查 source_id，让 getByPurchaseOrder 等查询能找到
                    if (relatedNo != null && !relatedNo.isEmpty() && "purchase_receipt".equals(sourceType)) {
                        Long rid = receiptNoToId.get(relatedNo);
                        if (rid != null) t.setSourceId(rid);
                    }
                    t.setApplicantId(userNameToId.get(r.get("申请人")));
                    String whStr = r.get("仓库ID");
                    if (whStr != null && !whStr.isEmpty()) {
                        try { t.setWarehouseId(Long.valueOf(whStr.trim())); } catch (Exception ignored) {}
                    }
                    t.setCreateTime(parseDatetime(r.get("创建时间")));
                    t.setCreateBy(userNameToId.get(r.get("创建人")));
                    t.setUpdateTime(t.getCreateTime());
                    t.setUpdateBy(t.getCreateBy());
                    invTaskMapper.insert(t);
                    invTaskNoToId.put(t.getTaskNo(), t.getId());
                    success.incrementAndGet();
                } catch (Exception e) {
                    errors.add("inv_task第" + (i + 2) + "行: " + e.getMessage());
                }
            });
        }
        List<Map<String, String>> invTaskItemAllRows = parseAllParts(invTaskItemFiles, INV_TASK_ITEM_HEADERS);
        if (!invTaskItemAllRows.isEmpty()) {
            List<Map<String, String>> itemRows = invTaskItemAllRows;
            java.util.stream.IntStream.range(0, itemRows.size()).parallel().forEach(i -> {
                Map<String, String> r = itemRows.get(i);
                try {
                    String taskNoRef = r.get("任务编号(关联)");
                    Long taskId = invTaskNoToId.get(taskNoRef);
                    if (taskId == null) {
                        // 任务编号映射不到 → 孤儿 item, 直接跳过(通常是上游 inv_task 行也失败了)
                        errors.add("inv_task_item第" + (i + 2) + "行: 任务编号 '" + taskNoRef + "' 在 inv_task 中找不到, 跳过");
                        return;
                    }
                    BizInvTaskItem ti = new BizInvTaskItem();
                    ti.setTaskId(taskId);
                    ti.setMaterialId(materialNoToId.get(r.get("物料编号")));
                    ti.setMaterialNo(r.get("物料编号"));
                    ti.setMaterialName(r.get("物料名称"));
                    ti.setUnit(r.get("单位"));
                    ti.setQuantity(parseBD(r.get("数量")));
                    ti.setProcessedQty(parseBD(r.get("已处理数量")));
                    String wh = r.get("仓库ID");
                    if (wh != null && !wh.isEmpty()) {
                        try { ti.setWarehouseId(Long.valueOf(wh.trim())); } catch (Exception ignored) {}
                    }
                    invTaskItemMapper.insert(ti);
                    success.incrementAndGet();
                } catch (Exception e) {
                    errors.add("inv_task_item第" + (i + 2) + "行: " + e.getMessage());
                }
            });
        }
        List<Map<String, String>> invTxAllRows = parseAllParts(invTxFiles, INV_TRANSACTION_HEADERS);
        if (!invTxAllRows.isEmpty()) {
            List<Map<String, String>> txRows = invTxAllRows;
            java.util.stream.IntStream.range(0, txRows.size()).parallel().forEach(i -> {
                Map<String, String> r = txRows.get(i);
                try {
                    BizInvTransaction tx = new BizInvTransaction();
                    tx.setMaterialId(materialNoToId.get(r.get("物料编号")));
                    String wh = r.get("仓库ID");
                    if (wh != null && !wh.isEmpty()) {
                        try { tx.setWarehouseId(Long.valueOf(wh.trim())); } catch (Exception ignored) {}
                    }
                    tx.setTransactionType(r.get("交易类型"));
                    tx.setQuantity(parseBD(r.get("数量")));
                    tx.setRelatedOrderType(r.get("关联单据类型"));
                    tx.setCreateTime(parseDatetime(r.get("创建时间")));
                    invTransactionMapper.insert(tx);
                    success.incrementAndGet();
                } catch (Exception e) {
                    errors.add("inv_transaction第" + (i + 2) + "行: " + e.getMessage());
                }
            });
        }

        return Map.of("success", success.get(), "errors", errors);
    }

    // ===== 辅助方法 =====

    private Map<Long, String> loadUserNames(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>().in(SysUser::getId, ids))
                .stream().collect(Collectors.toMap(SysUser::getId,
                        u -> u.getRealName() != null ? u.getRealName() : u.getUsername(), (a, b) -> a));
    }

    private Map<String, Long> loadUserNameToIdMap() {
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>().eq(SysUser::getDeleted, 0))
                .stream().collect(Collectors.toMap(
                        u -> u.getRealName() != null ? u.getRealName() : u.getUsername(),
                        SysUser::getId, (a, b) -> a));
    }

    /** 解析多个分片 xlsx, 合并所有数据行 */
    private static List<Map<String, String>> parseAllParts(MultipartFile[] files, List<String> headers) {
        if (files == null || files.length == 0) return new ArrayList<>();
        List<Map<String, String>> all = new ArrayList<>();
        for (MultipartFile f : files) {
            if (f != null && !f.isEmpty()) all.addAll(ExcelUtil.parseImport(f, headers));
        }
        return all;
    }

    private static BigDecimal parseBD(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return new BigDecimal(s.trim()); } catch (Exception e) { return null; }
    }

    private static LocalDateTime parseDatetime(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return LocalDateTime.parse(s.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")); }
        catch (Exception e) { return null; }
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return LocalDate.parse(s.trim(), DateTimeFormatter.ISO_LOCAL_DATE); }
        catch (Exception e) { return null; }
    }
}
