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
import com.aimes.modules.order.entity.BizShipmentRecord;
import com.aimes.modules.order.mapper.BizShipmentRecordMapper;
import com.aimes.modules.outsource.entity.BizOutsourceOrder;
import com.aimes.modules.outsource.entity.BizOutsourceReceipt;
import com.aimes.modules.outsource.mapper.BizOutsourceOrderMapper;
import com.aimes.modules.outsource.mapper.BizOutsourceReceiptMapper;
import com.aimes.modules.production.entity.BizWoProcessTask;
import com.aimes.modules.production.entity.BizWorkOrder;
import com.aimes.modules.production.mapper.BizWoProcessTaskMapper;
import com.aimes.modules.production.mapper.BizWorkOrderMapper;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static com.aimes.modules.dataimport.service.simulation.SimTimeHelper.*;

/**
 * ⑦ 外协收料生成器
 * 读取已有的 BizOutsourceOrder → 生成 BizOutsourceReceipt
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OutsourceReceiptGenerator {

    private final BizOutsourceOrderMapper outsourceOrderMapper;
    private final BizOutsourceReceiptMapper outsourceReceiptMapper;
    private final BizMaterialMapper materialMapper;
    private final BizWoProcessTaskMapper processTaskMapper;
    private final BizWorkOrderMapper workOrderMapper;
    private final BizInvTaskMapper invTaskMapper;
    private final BizInvTaskItemMapper invTaskItemMapper;
    private final BizInvTransactionMapper invTransactionMapper;
    private final SysUserMapper userMapper;
    private final com.aimes.modules.quality.mapper.BizQcTemplateMapper qcTemplateMapper;
    private final com.aimes.modules.quality.mapper.BizQcTemplateItemMapper qcTemplateItemMapper;
    private final com.aimes.modules.quality.mapper.BizQcTaskMapper qcTaskMapper;
    private final com.aimes.modules.quality.mapper.BizQcResultMapper qcResultMapper;
    private final com.aimes.modules.quality.mapper.BizQcRecordMapper qcRecordMapper;
    private final SalesOrderStatusHelper salesOrderStatusHelper;
    private final com.aimes.modules.order.mapper.BizSalesOrderItemMapper salesOrderItemMapper;
    private final com.aimes.modules.order.mapper.BizSalesOrderMapper salesOrderMapper;
    private final BizShipmentRecordMapper shipmentRecordMapper;

    private List<String> allTemplateNames;

    public static final List<String> HEADERS = List.of(
            "收料单号", "加工单号(关联)", "收货数量", "批次号", "收货日期",
            "收货人", "合格数量", "不良数量", "仓库", "状态", "质检状态",
            "是否质检", "质检模板",
            "创建时间", "创建人");

    public static final List<String> INV_TASK_HEADERS = List.of(
            "任务编号", "关联单据号", "库存类型", "状态", "来源类型", "申请人", "仓库ID", "创建时间", "创建人");
    public static final List<String> INV_TASK_ITEM_HEADERS = List.of(
            "任务编号(关联)", "物料编号", "物料名称", "单位", "数量", "已处理数量", "仓库ID");
    public static final List<String> INV_TRANSACTION_HEADERS = List.of(
            "物料编号", "仓库ID", "交易类型", "数量", "关联单据类型", "关联单据号", "创建时间");

    private List<List<Object>> cachedRows;
    private List<List<Object>> cachedInvTaskRows, cachedInvTaskItemRows, cachedInvTransactionRows;

    public Map<String, Object> generate(SimConfig cfg) {
        Set<Long> allIds = new java.util.HashSet<>();
        allIds.addAll(cfg.getCreatorIds());
        allIds.addAll(cfg.getReceiverIds());
        Map<Long, String> userNames = loadUserNames(new java.util.ArrayList<>(allIds));

        // 加载所有已发起或更晚阶段的外协订单（material_sent 是发料后等收货的核心状态）
        List<BizOutsourceOrder> orders = outsourceOrderMapper.selectList(
                new LambdaQueryWrapper<BizOutsourceOrder>()
                        .eq(BizOutsourceOrder::getDeleted, 0)
                        .in(BizOutsourceOrder::getStatus,
                                List.of("confirmed", "material_sent", "received", "completed")));

        // 加载物料映射（用于库存任务）
        Map<Long, BizMaterial> materialMap = materialMapper.selectList(
                new LambdaQueryWrapper<BizMaterial>().eq(BizMaterial::getDeleted, 0))
                .stream().collect(Collectors.toMap(BizMaterial::getId, m -> m, (a, b) -> a));

        cachedRows = new ArrayList<>();

        // 加载质检模板名称（用于下拉框）
        allTemplateNames = qcTemplateMapper.selectList(
                new LambdaQueryWrapper<com.aimes.modules.quality.entity.BizQcTemplate>()
                        .eq(com.aimes.modules.quality.entity.BizQcTemplate::getDeleted, 0))
                .stream().map(com.aimes.modules.quality.entity.BizQcTemplate::getTemplateName)
                .filter(n -> n != null && !n.isEmpty()).distinct().sorted().collect(java.util.stream.Collectors.toList());
        cachedInvTaskRows = new ArrayList<>();
        cachedInvTaskItemRows = new ArrayList<>();
        cachedInvTransactionRows = new ArrayList<>();

        for (BizOutsourceOrder order : orders) {
            BigDecimal receivedQty = order.getTotalQty() != null ? order.getTotalQty() : BigDecimal.ONE;
            BigDecimal qualifiedQty = receivedQty;
            BigDecimal defectQty = BigDecimal.ZERO;

            // 收货日期 = 发料日 + 外协交期(SimConfig.outsourceLeadDays,默认 8~15 工作日,可配置)
            // 与 OutsourceGenerator 的 leadDays 逻辑保持一致 —— 要求交期和实际收料用同一口径
            // 外协流程时序:订单 create_time → +2 wd 发料 → +leadDays 收料
            LocalDate orderDate = order.getCreateTime() != null ? order.getCreateTime().toLocalDate() : LocalDate.now();
            LocalDate deliveryDate = addWorkingDays(orderDate, 2, cfg);
            int leadDays = cfg.randomOutsourceLeadDays();
            LocalDate receivedDate = addWorkingDays(deliveryDate, leadDays, cfg);
            // 自然收料日在截止日之后 → 收料还没发生,直接跳过(不压缩)
            LocalDate cutoff = cfg.getCutoffDate();
            if (cutoff != null && receivedDate.isAfter(cutoff)) continue;
            LocalDateTime createTime = randomTime(receivedDate, cfg);
            if (createTime == null) continue;

            // 批次号: "B" + yyyyMMdd
            String batchNo = "B" + receivedDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

            Long receiverId = cfg.pickReceiver();
            String receiverName = userNames.getOrDefault(receiverId, receiverId != null ? String.valueOf(receiverId) : "");

            Long creatorId = cfg.pickCreator();
            String creatorName = userNames.getOrDefault(creatorId, creatorId != null ? String.valueOf(creatorId) : "");

            // 仓库：优先使用产品的默认仓库
            BizMaterial productMat = order.getProductId() != null ? materialMap.get(order.getProductId()) : null;
            Long productWhId = productMat != null && productMat.getDefaultWarehouseId() != null
                    ? productMat.getDefaultWarehouseId()
                    : (cfg.getWarehouseId() != null ? cfg.getWarehouseId() : 1L);
            String warehouseIdStr = String.valueOf(productWhId);

            String receiptNo = CodeUtil.generate("OSC");
            cachedRows.add(List.of(
                    receiptNo, order.getOrderNo(),
                    receivedQty.toPlainString(), batchNo, fmtDate(receivedDate),
                    receiverName, qualifiedQty.toPlainString(), defectQty.toPlainString(),
                    warehouseIdStr, "received", "none",
                    "", "",  // 是否质检, 质检模板 — 由用户在Excel中填写
                    fmtDateTime(createTime), creatorName));

            // === 库存任务：外协入库(outsource_in) ===
            // sourceType 用 outsource_receipt + relatedNo 用 receiptNo,这样
            // OutsourceReceiptService.getRelatedInvTasks(receiptId) 才能找到这条任务
            if (qualifiedQty.compareTo(BigDecimal.ZERO) > 0) {
                String invTaskNo = CodeUtil.generate("INK");
                cachedInvTaskRows.add(List.of(invTaskNo, receiptNo, "outsource_in", "completed", "outsource_receipt", creatorName, warehouseIdStr, fmtDateTime(createTime), creatorName));

                // 产品信息
                String matNo = "";
                String matName = "";
                String matUnit = "";
                if (productMat != null) {
                    matNo = productMat.getMaterialNo() != null ? productMat.getMaterialNo() : "";
                    matName = productMat.getMaterialName() != null ? productMat.getMaterialName() : "";
                    matUnit = productMat.getUnit() != null ? productMat.getUnit() : "";
                }
                cachedInvTaskItemRows.add(List.of(invTaskNo, matNo, matName, matUnit, qualifiedQty.toPlainString(), qualifiedQty.toPlainString(), warehouseIdStr));
                cachedInvTransactionRows.add(List.of(matNo, warehouseIdStr, "outsource_in", qualifiedQty.toPlainString(), "outsource_receipt", receiptNo, fmtDateTime(createTime)));
            }
        }

        Map<String, Object> tables = new LinkedHashMap<>();
        tables.put("outsource_receipt", cachedRows.size());
        tables.put("inv_task", cachedInvTaskRows.size());
        tables.put("inv_task_item", cachedInvTaskItemRows.size());
        tables.put("inv_transaction", cachedInvTransactionRows.size());
        int total = cachedRows.size() + cachedInvTaskRows.size() + cachedInvTaskItemRows.size() + cachedInvTransactionRows.size();
        return Map.of("tables", tables, "totalRows", total);
    }

    public void exportZip(HttpServletResponse response) throws IOException {
        if (cachedRows == null) throw new BusinessException("请先执行生成");

        response.setContentType("application/zip");
        response.setHeader("Content-Disposition",
                "attachment; filename=" + java.net.URLEncoder.encode("07_外协收料.zip", "UTF-8"));

        try (var zos = new java.util.zip.ZipOutputStream(response.getOutputStream())) {
            // outsource_receipt.xlsx — "是否质检"(第11列)和"质检模板"(第12列)加下拉框
            zos.putNextEntry(new java.util.zip.ZipEntry("07_外协收料_outsource_receipt.xlsx"));
            Map<Integer, List<String>> dropdowns = new java.util.LinkedHashMap<>();
            dropdowns.put(11, List.of("是", "否"));  // 是否质检
            dropdowns.put(12, allTemplateNames != null ? allTemplateNames : List.of());  // 质检模板
            SimTimeHelper.writeExcelWithDropdowns(zos, HEADERS, cachedRows, dropdowns);
            zos.closeEntry();

            // inv_task.xlsx
            zos.putNextEntry(new java.util.zip.ZipEntry("07_外协收料_inv_task.xlsx"));
            SimTimeHelper.writeExcelToStream(zos, INV_TASK_HEADERS, cachedInvTaskRows);
            zos.closeEntry();

            // inv_task_item.xlsx
            zos.putNextEntry(new java.util.zip.ZipEntry("07_外协收料_inv_task_item.xlsx"));
            SimTimeHelper.writeExcelToStream(zos, INV_TASK_ITEM_HEADERS, cachedInvTaskItemRows);
            zos.closeEntry();

            // inv_transaction.xlsx
            zos.putNextEntry(new java.util.zip.ZipEntry("07_外协收料_inv_transaction.xlsx"));
            SimTimeHelper.writeExcelToStream(zos, INV_TRANSACTION_HEADERS, cachedInvTransactionRows);
            zos.closeEntry();
        }
    }

    @Transactional
    public Map<String, Object> importData(MultipartFile file, MultipartFile invTaskFile, MultipartFile invTaskItemFile, MultipartFile invTxFile) {
        if (file == null || file.isEmpty()) throw new BusinessException("请上传外协收料文件");
        List<Map<String, String>> rows = ExcelUtil.parseImport(file, HEADERS);
        int success = 0;
        List<String> errors = new ArrayList<>();

        Map<String, Long> userNameToId = loadUserNameToIdMap();

        // 加工单号→ID
        Map<String, Long> orderNoToId = outsourceOrderMapper.selectList(
                new LambdaQueryWrapper<BizOutsourceOrder>().eq(BizOutsourceOrder::getDeleted, 0))
                .stream().collect(Collectors.toMap(BizOutsourceOrder::getOrderNo, BizOutsourceOrder::getId, (a, b) -> a));

        // 收料单号→ID，给后面 inv_task 导入用 (sourceType=outsource_receipt 时反查 source_id)
        Map<String, Long> receiptNoToId = new HashMap<>();

        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> r = rows.get(i);
            try {
                String orderNo = r.get("加工单号(关联)");
                Long orderId = orderNoToId.get(orderNo);
                if (orderId == null) { errors.add("第" + (i + 2) + "行: 加工单不存在 " + orderNo); continue; }

                BizOutsourceReceipt receipt = new BizOutsourceReceipt();
                receipt.setReceiptNo(r.get("收料单号"));
                receipt.setOutsourceOrderId(orderId);
                receipt.setReceivedQty(parseBD(r.get("收货数量")));
                receipt.setBatchNo(r.get("批次号"));
                receipt.setReceivedDate(parseDate(r.get("收货日期")));
                receipt.setReceiverId(userNameToId.get(r.get("收货人")));
                receipt.setQualifiedQty(parseBD(r.get("合格数量")));
                receipt.setDefectQty(parseBD(r.get("不良数量")));
                // 仓库字段: 如果是数字则直接作为ID，否则置空
                String warehouseStr = r.get("仓库");
                if (warehouseStr != null && !warehouseStr.isEmpty()) {
                    try { receipt.setWarehouseId(Long.valueOf(warehouseStr.trim())); } catch (NumberFormatException ignored) {}
                }
                receipt.setStatus(r.get("状态"));

                // 解析"是否质检"列:是/yes/Y/true/1 → 需要质检
                String needQcStr = r.get("是否质检");
                boolean needQc = false;
                if (needQcStr != null) {
                    String s = needQcStr.trim().toLowerCase();
                    needQc = s.equals("是") || s.equals("y") || s.equals("yes") || s.equals("true") || s.equals("1");
                }
                // 需要质检时覆盖 qcStatus 为 qc_done(模拟历史数据,质检已完成),否则用 Excel 中的列值(通常 none)
                receipt.setQcStatus(needQc ? "qc_done" : r.get("质检状态"));
                receipt.setCreateTime(parseDatetime(r.get("创建时间")));
                receipt.setCreateBy(userNameToId.get(r.get("创建人")));
                receipt.setUpdateTime(receipt.getCreateTime());
                receipt.setUpdateBy(receipt.getCreateBy());
                outsourceReceiptMapper.insert(receipt);
                if (receipt.getReceiptNo() != null) {
                    receiptNoToId.put(receipt.getReceiptNo(), receipt.getId());
                }

                // 回写库存任务数和入库数(对应的 outsource_in inv_task 在下面的 Excel 文件里以 completed 状态导入)
                BigDecimal qualifiedForInv = receipt.getQualifiedQty() != null ? receipt.getQualifiedQty()
                        : (receipt.getReceivedQty() != null ? receipt.getReceivedQty() : BigDecimal.ZERO);
                if (qualifiedForInv.compareTo(BigDecimal.ZERO) > 0) {
                    receipt.setInvTaskQty(qualifiedForInv);
                    receipt.setStockInQty(qualifiedForInv);
                    outsourceReceiptMapper.updateById(receipt);
                }

                // === 生成外协质检任务(如果用户在 Excel 选了"是否质检=是") ===
                if (needQc) {
                    createOutsourceQcTask(receipt, r.get("质检模板"), userNameToId);
                }

                // 回写 outsourceOrder 的 qualifiedQty/receiptQty/status
                if (receipt.getOutsourceOrderId() != null) {
                    com.aimes.modules.outsource.entity.BizOutsourceOrder oo = outsourceOrderMapper.selectById(receipt.getOutsourceOrderId());
                    if (oo != null) {
                        java.math.BigDecimal addQty = receipt.getReceivedQty() != null ? receipt.getReceivedQty() : java.math.BigDecimal.ZERO;
                        oo.setQualifiedQty((oo.getQualifiedQty() != null ? oo.getQualifiedQty() : java.math.BigDecimal.ZERO).add(addQty));
                        oo.setReceiptQty((oo.getReceiptQty() != null ? oo.getReceiptQty() : java.math.BigDecimal.ZERO).add(addQty));
                        if (oo.getTotalQty() != null && oo.getQualifiedQty().compareTo(oo.getTotalQty()) >= 0) {
                            oo.setStatus("received");
                        }
                        outsourceOrderMapper.updateById(oo);
                    }
                }

                // 整单外协分支：关联到销售订单行而非工单工序
                // 外协回的就是成品 → 直接回写订单行的已生产/已入库/已发货,
                // 并补齐对应的发货记录 + 销售出库库存任务(避免 ShipmentGenerator 跳过没工单的订单)
                BizOutsourceOrder ooFull = outsourceOrderMapper.selectById(receipt.getOutsourceOrderId());
                if (ooFull != null && (ooFull.getTaskId() == null || ooFull.getTaskId() == 0)
                        && ooFull.getOrderItemId() != null && ooFull.getOrderItemId() > 0) {
                    BigDecimal addQty = receipt.getQualifiedQty() != null ? receipt.getQualifiedQty()
                            : (receipt.getReceivedQty() != null ? receipt.getReceivedQty() : BigDecimal.ZERO);
                    com.aimes.modules.order.entity.BizSalesOrderItem oi = salesOrderItemMapper.selectById(ooFull.getOrderItemId());
                    if (oi != null && addQty.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal curProduced = oi.getProducedQty() != null ? oi.getProducedQty() : BigDecimal.ZERO;
                        oi.setProducedQty(curProduced.add(addQty));
                        BigDecimal curInQty = oi.getWarehouseInQty() != null ? oi.getWarehouseInQty() : BigDecimal.ZERO;
                        oi.setWarehouseInQty(curInQty.add(addQty));

                        // 发货时间硬约束:baseTime + 4h 超过现在就只累加已生产/已入库,不发货
                        LocalDateTime baseTime = receipt.getCreateTime() != null ? receipt.getCreateTime() : LocalDateTime.now();
                        LocalDateTime shipTime = baseTime.plusHours(4);
                        boolean canShip = !shipTime.isAfter(LocalDateTime.now());

                        if (canShip) {
                            BigDecimal curShipped = oi.getShippedQty() != null ? oi.getShippedQty() : BigDecimal.ZERO;
                            oi.setShippedQty(curShipped.add(addQty));
                            BigDecimal curTaskQty = oi.getTaskQty() != null ? oi.getTaskQty() : BigDecimal.ZERO;
                            oi.setTaskQty(curTaskQty.add(addQty));
                        }
                        salesOrderItemMapper.updateById(oi);

                        if (canShip) {

                        com.aimes.modules.order.entity.BizSalesOrder relOrder = ooFull.getOrderId() != null
                                ? salesOrderMapper.selectById(ooFull.getOrderId()) : null;
                        String relOrderNo = relOrder != null ? relOrder.getOrderNo() : "";

                        BizShipmentRecord shipment = new BizShipmentRecord();
                        shipment.setOrderId(ooFull.getOrderId());
                        shipment.setOrderItemId(oi.getId());
                        shipment.setProductId(oi.getProductId());
                        shipment.setShippedQty(addQty);
                        shipment.setShipmentNo(CodeUtil.generate("SH"));
                        shipment.setShippedAt(shipTime);
                        shipment.setStatus("shipped");
                        shipment.setCreateTime(shipTime);
                        shipment.setUpdateTime(shipTime);
                        shipment.setCreateBy(receipt.getCreateBy());
                        shipment.setUpdateBy(receipt.getCreateBy());
                        shipmentRecordMapper.insert(shipment);

                        BizMaterial shipProduct = oi.getProductId() != null ? materialMapper.selectById(oi.getProductId()) : null;
                        Long shipWhId = shipProduct != null && shipProduct.getDefaultWarehouseId() != null
                                ? shipProduct.getDefaultWarehouseId()
                                : (receipt.getWarehouseId() != null ? receipt.getWarehouseId() : 1L);

                        BizInvTask shipTask = new BizInvTask();
                        shipTask.setTaskNo(CodeUtil.generate("INK"));
                        shipTask.setRelatedOrderNo(relOrderNo);
                        shipTask.setInvType("sales_out");
                        shipTask.setWarehouseId(shipWhId);
                        shipTask.setStatus("completed");
                        shipTask.setSourceType("sales_order");
                        shipTask.setSourceId(ooFull.getOrderId());
                        shipTask.setApplicantId(receipt.getCreateBy());
                        shipTask.setCreateBy(receipt.getCreateBy());
                        shipTask.setUpdateBy(receipt.getCreateBy());
                        shipTask.setCreateTime(shipTime);
                        shipTask.setUpdateTime(shipTime);
                        invTaskMapper.insert(shipTask);

                        BizInvTaskItem shipItem = new BizInvTaskItem();
                        shipItem.setTaskId(shipTask.getId());
                        shipItem.setMaterialId(oi.getProductId());
                        if (shipProduct != null) {
                            shipItem.setMaterialNo(shipProduct.getMaterialNo());
                            shipItem.setMaterialName(shipProduct.getMaterialName());
                            shipItem.setSpec(shipProduct.getSpec());
                            shipItem.setUnit(shipProduct.getUnit());
                        }
                        shipItem.setQuantity(addQty);
                        shipItem.setProcessedQty(addQty);
                        shipItem.setUnitPrice(oi.getUnitPrice());
                        shipItem.setWarehouseId(shipWhId);
                        invTaskItemMapper.insert(shipItem);

                        BizInvTransaction shipTx = new BizInvTransaction();
                        shipTx.setMaterialId(oi.getProductId());
                        shipTx.setWarehouseId(shipWhId);
                        shipTx.setTransactionType("sales_out");
                        shipTx.setQuantity(addQty.negate());
                        shipTx.setRelatedOrderType("sales_order");
                        shipTx.setRelatedOrderId(ooFull.getOrderId());
                        shipTx.setCreateTime(shipTime);
                        shipTx.setUpdateTime(shipTime);
                        invTransactionMapper.insert(shipTx);
                        } // end if (canShip)
                    }
                    // 推进销售订单：先 in_production → completed(生产完)→ partial_shipped/shipped(发货)
                    if (ooFull.getOrderId() != null) {
                        salesOrderStatusHelper.advanceToInProduction(ooFull.getOrderId());
                        salesOrderStatusHelper.advanceToCompleted(ooFull.getOrderId());
                        salesOrderStatusHelper.advanceByShipment(ooFull.getOrderId());
                    }
                }

                // 回写外协工序任务的合格数量和状态(分步外协)
                BizOutsourceOrder ooForTask = outsourceOrderMapper.selectById(receipt.getOutsourceOrderId());
                if (ooForTask != null && ooForTask.getTaskId() != null && ooForTask.getTaskId() > 0) {
                    BizWoProcessTask pt = processTaskMapper.selectById(ooForTask.getTaskId());
                    if (pt != null) {
                        BigDecimal addQty = receipt.getReceivedQty() != null ? receipt.getReceivedQty() : BigDecimal.ZERO;
                        pt.setQualifiedQty((pt.getQualifiedQty() != null ? pt.getQualifiedQty() : BigDecimal.ZERO).add(addQty));
                        pt.setOutsourceStatus("completed");
                        if (pt.getPlanQty() != null && pt.getQualifiedQty().compareTo(pt.getPlanQty()) >= 0) {
                            pt.setStatus("completed");
                        }
                        processTaskMapper.updateById(pt);

                        // 回写工单completedQty（同③报工的11b逻辑）
                        if (pt.getWorkOrderId() != null) {
                            BizWorkOrder wo2 = workOrderMapper.selectById(pt.getWorkOrderId());
                            if (wo2 != null) {
                                List<BizWoProcessTask> woTasks = processTaskMapper.selectList(
                                    new LambdaQueryWrapper<BizWoProcessTask>()
                                        .eq(BizWoProcessTask::getWorkOrderId, wo2.getId())
                                        .eq(BizWoProcessTask::getDeleted, 0)
                                        .ne(BizWoProcessTask::getStatus, "cancelled")
                                        .orderByDesc(BizWoProcessTask::getStepGroup));
                                if (!woTasks.isEmpty()) {
                                    int lastGroup = woTasks.get(0).getStepGroup() != null ? woTasks.get(0).getStepGroup() : 1;
                                    BigDecimal completedQty2 = woTasks.stream()
                                        .filter(t -> t.getStepGroup() != null && t.getStepGroup() == lastGroup)
                                        .map(t -> t.getQualifiedQty() != null ? t.getQualifiedQty() : BigDecimal.ZERO)
                                        .min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
                                    wo2.setCompletedQty(completedQty2);
                                    boolean allDone = woTasks.stream().allMatch(t -> "completed".equals(t.getStatus()));
                                    if (allDone && completedQty2.compareTo(BigDecimal.ZERO) > 0) {
                                        wo2.setStatus("completed");
                                    }
                                    workOrderMapper.updateById(wo2);
                                    // 推进关联销售订单状态
                                    salesOrderStatusHelper.advanceToInProduction(wo2.getOrderId());
                                    salesOrderStatusHelper.advanceToCompleted(wo2.getOrderId());
                                }
                            }
                        }
                    }
                }

                success++;
            } catch (Exception e) {
                errors.add("第" + (i + 2) + "行: " + e.getMessage());
            }
        }
        // 导入库存任务
        Map<String, Long> matNoToId = materialMapper.selectList(
                new LambdaQueryWrapper<BizMaterial>().eq(BizMaterial::getDeleted, 0))
                .stream().filter(m -> m.getMaterialNo() != null)
                .collect(Collectors.toMap(BizMaterial::getMaterialNo, BizMaterial::getId, (a, b) -> a));

        Map<String, Long> invTaskNoToId = new HashMap<>();
        if (invTaskFile != null && !invTaskFile.isEmpty()) {
            List<Map<String, String>> taskRows = ExcelUtil.parseImport(invTaskFile, INV_TASK_HEADERS);
            for (int i = 0; i < taskRows.size(); i++) {
                Map<String, String> r = taskRows.get(i);
                try {
                    BizInvTask t = new BizInvTask();
                    t.setTaskNo(r.get("任务编号"));
                    String relatedNo = r.get("关联单据号");
                    t.setRelatedOrderNo(relatedNo);
                    t.setInvType(r.get("库存类型"));
                    t.setStatus(r.get("状态"));
                    String srcType = r.get("来源类型");
                    t.setSourceType(srcType);
                    // 反查 source_id
                    if ("outsource_receipt".equals(srcType) && relatedNo != null) {
                        Long rid = receiptNoToId.get(relatedNo);
                        if (rid != null) t.setSourceId(rid);
                    } else if ("outsource_order".equals(srcType) && relatedNo != null) {
                        Long oid = orderNoToId.get(relatedNo);
                        if (oid != null) t.setSourceId(oid);
                    }
                    t.setApplicantId(userNameToId.get(r.get("申请人")));
                    String whStr = r.get("仓库ID");
                    if (whStr != null && !whStr.isEmpty()) try { t.setWarehouseId(Long.valueOf(whStr.trim())); } catch (Exception ignored) {}
                    t.setCreateTime(parseDatetime(r.get("创建时间")));
                    t.setCreateBy(userNameToId.get(r.get("创建人")));
                    t.setUpdateTime(t.getCreateTime());
                    t.setUpdateBy(t.getCreateBy());
                    invTaskMapper.insert(t);
                    invTaskNoToId.put(t.getTaskNo(), t.getId());
                    success++;
                } catch (Exception e) { errors.add("inv_task第" + (i + 2) + "行: " + e.getMessage()); }
            }
        }
        if (invTaskItemFile != null && !invTaskItemFile.isEmpty()) {
            List<Map<String, String>> itemRows = ExcelUtil.parseImport(invTaskItemFile, INV_TASK_ITEM_HEADERS);
            for (int i = 0; i < itemRows.size(); i++) {
                Map<String, String> r = itemRows.get(i);
                try {
                    BizInvTaskItem ti = new BizInvTaskItem();
                    ti.setTaskId(invTaskNoToId.get(r.get("任务编号(关联)")));
                    ti.setMaterialId(matNoToId.get(r.get("物料编号")));
                    ti.setMaterialNo(r.get("物料编号"));
                    ti.setMaterialName(r.get("物料名称"));
                    ti.setUnit(r.get("单位"));
                    ti.setQuantity(parseBD(r.get("数量")));
                    ti.setProcessedQty(parseBD(r.get("已处理数量")));
                    String wh = r.get("仓库ID");
                    if (wh != null && !wh.isEmpty()) try { ti.setWarehouseId(Long.valueOf(wh.trim())); } catch (Exception ignored) {}
                    invTaskItemMapper.insert(ti);
                    success++;
                } catch (Exception e) { errors.add("inv_task_item第" + (i + 2) + "行: " + e.getMessage()); }
            }
        }
        if (invTxFile != null && !invTxFile.isEmpty()) {
            List<Map<String, String>> txRows = ExcelUtil.parseImport(invTxFile, INV_TRANSACTION_HEADERS);
            for (int i = 0; i < txRows.size(); i++) {
                Map<String, String> r = txRows.get(i);
                try {
                    BizInvTransaction tx = new BizInvTransaction();
                    tx.setMaterialId(matNoToId.get(r.get("物料编号")));
                    String wh = r.get("仓库ID");
                    if (wh != null && !wh.isEmpty()) try { tx.setWarehouseId(Long.valueOf(wh.trim())); } catch (Exception ignored) {}
                    tx.setTransactionType(r.get("交易类型"));
                    tx.setQuantity(parseBD(r.get("数量")));
                    tx.setRelatedOrderType(r.get("关联单据类型"));
                    tx.setCreateTime(parseDatetime(r.get("创建时间")));
                    invTransactionMapper.insert(tx);
                    success++;
                } catch (Exception e) { errors.add("inv_transaction第" + (i + 2) + "行: " + e.getMessage()); }
            }
        }

        return Map.of("success", success, "errors", errors);
    }

    /**
     * 为外协收料单生成配套的质检任务 + 质检结果。
     * 根据收料单的合格/不合格数量反推 result;质检模板项自动填充合理默认值。
     */
    private void createOutsourceQcTask(BizOutsourceReceipt receipt, String templateName,
                                       Map<String, Long> userNameToId) {
        if (receipt == null || receipt.getId() == null) return;

        BizOutsourceOrder oo = outsourceOrderMapper.selectById(receipt.getOutsourceOrderId());
        Long productId = oo != null ? oo.getProductId() : null;

        BigDecimal receivedQty = receipt.getReceivedQty() != null ? receipt.getReceivedQty() : BigDecimal.ZERO;
        BigDecimal qualifiedQty = receipt.getQualifiedQty() != null ? receipt.getQualifiedQty() : receivedQty;
        BigDecimal defectQty = receipt.getDefectQty() != null ? receipt.getDefectQty() : BigDecimal.ZERO;
        String result = defectQty.compareTo(BigDecimal.ZERO) > 0 ? "fail" : "pass";

        // 通过模板名称查找模板 id
        Long templateId = null;
        com.aimes.modules.quality.entity.BizQcTemplate template = null;
        if (templateName != null && !templateName.isEmpty()) {
            template = qcTemplateMapper.selectOne(
                    new LambdaQueryWrapper<com.aimes.modules.quality.entity.BizQcTemplate>()
                            .eq(com.aimes.modules.quality.entity.BizQcTemplate::getTemplateName, templateName.trim())
                            .eq(com.aimes.modules.quality.entity.BizQcTemplate::getDeleted, 0)
                            .last("LIMIT 1"));
            if (template != null) templateId = template.getId();
        }

        com.aimes.modules.quality.entity.BizQcTask qcTask = new com.aimes.modules.quality.entity.BizQcTask();
        qcTask.setTaskNo(CodeUtil.generate("QC"));
        qcTask.setQcType("outsource");
        qcTask.setTemplateId(templateId);
        qcTask.setProductId(productId);
        qcTask.setMaterialId(productId);
        qcTask.setBatchNo(receipt.getBatchNo());
        qcTask.setRelatedOrderType("outsource_receipt");
        qcTask.setRelatedOrderId(receipt.getId());
        qcTask.setPlanQty(receivedQty);
        qcTask.setPassQty(qualifiedQty);
        qcTask.setFailQty(defectQty);
        qcTask.setResult(result);
        qcTask.setInspectorId(receipt.getReceiverId());
        // 检验时间 = 收料时间 + 1 小时(模拟收料后立即质检)
        // 硬约束:不能超过现在。如果超过,就让质检时间 = 收料时间(同一时刻,虽然不真实但至少不是未来)
        LocalDateTime inspectedAt = receipt.getCreateTime() != null
                ? receipt.getCreateTime().plusHours(1) : LocalDateTime.now();
        if (inspectedAt.isAfter(LocalDateTime.now())) {
            inspectedAt = receipt.getCreateTime() != null ? receipt.getCreateTime() : LocalDateTime.now();
        }
        qcTask.setInspectedAt(inspectedAt);
        qcTask.setDisposalStatus(result.equals("pass") ? "" : "undisposed");
        qcTask.setCreateTime(receipt.getCreateTime());
        qcTask.setUpdateTime(inspectedAt);
        qcTask.setCreateBy(receipt.getCreateBy());
        qcTask.setUpdateBy(receipt.getCreateBy());
        qcTaskMapper.insert(qcTask);

        // 回写 receipt 的 qc_task_id
        receipt.setQcTaskId(qcTask.getId());
        outsourceReceiptMapper.updateById(receipt);

        // 按模板项生成质检结果
        if (templateId != null) {
            List<com.aimes.modules.quality.entity.BizQcTemplateItem> tplItems = qcTemplateItemMapper.selectList(
                    new LambdaQueryWrapper<com.aimes.modules.quality.entity.BizQcTemplateItem>()
                            .eq(com.aimes.modules.quality.entity.BizQcTemplateItem::getTemplateId, templateId)
                            .eq(com.aimes.modules.quality.entity.BizQcTemplateItem::getDeleted, 0)
                            .orderByAsc(com.aimes.modules.quality.entity.BizQcTemplateItem::getSortOrder));
            boolean isPass = "pass".equals(result);
            for (com.aimes.modules.quality.entity.BizQcTemplateItem ti : tplItems) {
                com.aimes.modules.quality.entity.BizQcResult rs = new com.aimes.modules.quality.entity.BizQcResult();
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
                String actualVal;
                if ("quantitative".equalsIgnoreCase(ti.getItemType())) {
                    actualVal = ti.getDefaultValue() != null && !ti.getDefaultValue().isEmpty()
                            ? ti.getDefaultValue() : ti.getStandardValue();
                } else if ("radio".equalsIgnoreCase(ti.getItemType())) {
                    actualVal = isPass ? "合格" : "不合格";
                } else {
                    actualVal = ti.getDefaultValue() != null && !ti.getDefaultValue().isEmpty()
                            ? ti.getDefaultValue() : (isPass ? "正常" : "存在异常");
                }
                rs.setActualValue(actualVal);
                rs.setIsPass(isPass ? 1 : 0);
                rs.setCreateTime(inspectedAt);
                rs.setUpdateTime(inspectedAt);
                rs.setCreateBy(receipt.getCreateBy());
                rs.setUpdateBy(receipt.getCreateBy());
                qcResultMapper.insert(rs);
            }
        }

        // 生成 biz_qc_record(合格/不合格汇总记录)— 前端"检验记录"tab 和数量汇总都读这里
        if (qualifiedQty.compareTo(BigDecimal.ZERO) > 0) {
            com.aimes.modules.quality.entity.BizQcRecord rec = new com.aimes.modules.quality.entity.BizQcRecord();
            rec.setQcTaskId(qcTask.getId());
            rec.setRecordType("qualified");
            rec.setQuantity(qualifiedQty);
            rec.setBatchNo(receipt.getBatchNo());
            rec.setReporterId(receipt.getReceiverId());
            rec.setCreateTime(inspectedAt);
            rec.setUpdateTime(inspectedAt);
            rec.setCreateBy(receipt.getCreateBy());
            rec.setUpdateBy(receipt.getCreateBy());
            qcRecordMapper.insert(rec);
        }
        if (defectQty.compareTo(BigDecimal.ZERO) > 0) {
            com.aimes.modules.quality.entity.BizQcRecord rec = new com.aimes.modules.quality.entity.BizQcRecord();
            rec.setQcTaskId(qcTask.getId());
            rec.setRecordType("unqualified");
            rec.setQuantity(defectQty);
            rec.setBatchNo(receipt.getBatchNo());
            rec.setReporterId(receipt.getReceiverId());
            rec.setCreateTime(inspectedAt);
            rec.setUpdateTime(inspectedAt);
            rec.setCreateBy(receipt.getCreateBy());
            rec.setUpdateBy(receipt.getCreateBy());
            qcRecordMapper.insert(rec);
        }
    }

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
