package com.aimes.modules.dataimport.service.simulation;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.utils.CodeUtil;
import com.aimes.common.utils.ExcelUtil;
import com.aimes.modules.bom.entity.BizBomItem;
import com.aimes.modules.bom.entity.BizBomVersion;
import com.aimes.modules.bom.mapper.BizBomItemMapper;
import com.aimes.modules.bom.mapper.BizBomVersionMapper;
import com.aimes.modules.master.entity.BizMaterial;
import com.aimes.modules.master.entity.BizSupplier;
import com.aimes.modules.master.mapper.BizMaterialMapper;
import com.aimes.modules.master.mapper.BizSupplierMapper;
import com.aimes.modules.order.entity.BizSalesOrder;
import com.aimes.modules.order.entity.BizSalesOrderItem;
import com.aimes.modules.order.mapper.BizSalesOrderMapper;
import com.aimes.modules.order.mapper.BizSalesOrderItemMapper;
import com.aimes.modules.outsource.entity.BizOutsourceDelivery;
import com.aimes.modules.outsource.entity.BizOutsourceOrder;
import com.aimes.modules.outsource.entity.BizOutsourceOrderRequest;
import com.aimes.modules.outsource.entity.BizOutsourceRequest;
import com.aimes.modules.outsource.mapper.BizOutsourceDeliveryMapper;
import com.aimes.modules.outsource.mapper.BizOutsourceOrderMapper;
import com.aimes.modules.outsource.mapper.BizOutsourceOrderRequestMapper;
import com.aimes.modules.outsource.mapper.BizOutsourceRequestMapper;
import com.aimes.modules.production.entity.BizWorkOrder;
import com.aimes.modules.production.mapper.BizWorkOrderMapper;
import com.aimes.modules.inventory.entity.BizInvTask;
import com.aimes.modules.inventory.entity.BizInvTaskItem;
import com.aimes.modules.inventory.entity.BizInvTransaction;
import com.aimes.modules.inventory.mapper.BizInvTaskMapper;
import com.aimes.modules.inventory.mapper.BizInvTaskItemMapper;
import com.aimes.modules.inventory.mapper.BizInvTransactionMapper;
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
import java.util.stream.Collectors;

import static com.aimes.modules.dataimport.service.simulation.SimTimeHelper.*;

/**
 * ⑥ 外协管理生成器（整单外协）
 * 读取没有对应生产计划的confirmed销售订单 → 为它们生成整单外协数据
 * （外协申请 + 外协订单 + 外协发货 + 库存任务）
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OutsourceGenerator {

    private final BizWorkOrderMapper workOrderMapper;
    private final BizMaterialMapper materialMapper;
    private final BizSupplierMapper supplierMapper;
    private final BizSalesOrderMapper salesOrderMapper;
    private final BizSalesOrderItemMapper salesOrderItemMapper;
    private final SalesOrderStatusHelper salesOrderStatusHelper;
    private final BizBomVersionMapper bomVersionMapper;
    private final BizBomItemMapper bomItemMapper;
    private final BizOutsourceRequestMapper outsourceRequestMapper;
    private final BizOutsourceOrderMapper outsourceOrderMapper;
    private final BizOutsourceDeliveryMapper outsourceDeliveryMapper;
    private final BizOutsourceOrderRequestMapper outsourceOrderRequestMapper;
    private final com.aimes.modules.outsource.mapper.BizOutsourceDeliveryItemMapper deliveryItemMapper;
    private final BizInvTaskMapper invTaskMapper;
    private final BizInvTaskItemMapper invTaskItemMapper;
    private final BizInvTransactionMapper invTransactionMapper;
    private final SysUserMapper userMapper;

    public static final List<String> REQUEST_HEADERS = List.of(
            "申请编号", "销售订单号(关联)", "订单行产品编号", "产品名称", "数量",
            "要求交期", "状态", "审批人", "审批时间", "创建时间", "创建人");

    public static final List<String> ORDER_HEADERS = List.of(
            "加工单号", "供应商名称", "申请编号(关联)", "销售订单号", "产品编号",
            "工序名称", "总数量", "单价", "总金额", "交货日期",
            "已发货数量", "已收合格数量", "状态", "审批人", "审批时间", "创建时间", "创建人");

    public static final List<String> DELIVERY_HEADERS = List.of(
            "发货编号", "加工单号(关联)", "发货类型", "物料编号", "物料名称",
            "发货数量", "状态", "创建时间", "创建人");

    public static final List<String> INV_TASK_HEADERS = List.of(
            "任务编号", "关联单据号", "库存类型", "状态", "来源类型", "申请人", "仓库ID", "创建时间", "创建人");
    public static final List<String> INV_TASK_ITEM_HEADERS = List.of(
            "任务编号(关联)", "物料编号", "物料名称", "单位", "数量", "已处理数量", "仓库ID");
    public static final List<String> INV_TRANSACTION_HEADERS = List.of(
            "物料编号", "仓库ID", "交易类型", "数量", "关联单据类型", "关联单据号", "创建时间");

    private List<List<Object>> cachedRequestRows;
    private List<List<Object>> cachedOrderRows;
    private List<List<Object>> cachedDeliveryRows;
    private List<List<Object>> cachedInvTaskRows, cachedInvTaskItemRows, cachedInvTransactionRows;

    public Map<String, Object> generate(SimConfig cfg) {
        Set<Long> allIds = new HashSet<>();
        allIds.addAll(cfg.getCreatorIds());
        allIds.addAll(cfg.getApproverIds());
        Map<Long, String> userNames = loadUserNames(new ArrayList<>(allIds));

        // 查询所有confirmed的销售订单
        List<BizSalesOrder> allOrders = salesOrderMapper.selectList(
                new LambdaQueryWrapper<BizSalesOrder>()
                        .eq(BizSalesOrder::getDeleted, 0)
                        .eq(BizSalesOrder::getStatus, "confirmed"));

        // 查询已有工单关联的订单ID（这些有生产计划，不是整单外协）
        Set<Long> plannedOrderIds = new HashSet<>();
        List<BizWorkOrder> existingWos = workOrderMapper.selectList(
                new LambdaQueryWrapper<BizWorkOrder>().eq(BizWorkOrder::getDeleted, 0));
        for (BizWorkOrder wo : existingWos) {
            if (wo.getOrderId() != null) plannedOrderIds.add(wo.getOrderId());
        }

        // 筛选出没有生产计划的订单（即整单外协的订单）
        List<BizSalesOrder> outsourceOrders = allOrders.stream()
                .filter(o -> !plannedOrderIds.contains(o.getId()))
                .toList();

        // 加载产品/物料映射
        Map<Long, BizMaterial> materialMap = materialMapper.selectList(
                new LambdaQueryWrapper<BizMaterial>().eq(BizMaterial::getDeleted, 0))
                .stream().collect(Collectors.toMap(BizMaterial::getId, m -> m, (a, b) -> a));

        // 供应商名称
        String supplierName = "默认外协供应商";
        if (cfg.getDefaultSupplierId() != null) {
            BizSupplier supplier = supplierMapper.selectById(cfg.getDefaultSupplierId());
            if (supplier != null) supplierName = supplier.getSupplierName();
        }

        // 加载BOM版本映射: productId → active版本
        Map<Long, BizBomVersion> activeBomMap = bomVersionMapper.selectList(
                new LambdaQueryWrapper<BizBomVersion>()
                        .eq(BizBomVersion::getDeleted, 0)
                        .eq(BizBomVersion::getStatus, "active"))
                .stream().collect(Collectors.toMap(BizBomVersion::getProductId, v -> v, (a, b) -> a));

        cachedRequestRows = new ArrayList<>();
        cachedOrderRows = new ArrayList<>();
        cachedDeliveryRows = new ArrayList<>();
        cachedInvTaskRows = new ArrayList<>();
        cachedInvTaskItemRows = new ArrayList<>();
        cachedInvTransactionRows = new ArrayList<>();

        for (BizSalesOrder order : outsourceOrders) {
            // 加载该订单的所有行项
            List<BizSalesOrderItem> items = salesOrderItemMapper.selectList(
                    new LambdaQueryWrapper<BizSalesOrderItem>()
                            .eq(BizSalesOrderItem::getOrderId, order.getId())
                            .eq(BizSalesOrderItem::getDeleted, 0));

            for (BizSalesOrderItem oi : items) {
                // 产品信息
                String productNo = "";
                String productName = "";
                String productUnit = "";
                if (oi.getProductId() != null) {
                    BizMaterial mat = materialMap.get(oi.getProductId());
                    if (mat != null) {
                        productNo = mat.getMaterialNo() != null ? mat.getMaterialNo() : "";
                        productName = mat.getMaterialName() != null ? mat.getMaterialName() : "";
                        productUnit = mat.getUnit() != null ? mat.getUnit() : "";
                    }
                }

                BigDecimal qty = oi.getQuantity() != null ? oi.getQuantity() : BigDecimal.ONE;

                // 时间线: 基准=销售订单创建时间
                LocalDate baseDate = order.getCreateTime() != null
                        ? order.getCreateTime().toLocalDate() : LocalDate.now();
                baseDate = snapToWorkingDay(baseDate, cfg);

                LocalDate orderDate = addWorkingDays(baseDate, 2, cfg);
                LocalDate approveDate = addWorkingDays(orderDate, 1, cfg);
                LocalDate osoDate = addWorkingDays(approveDate, 1, cfg);

                // 外协交期
                int leadDays = cfg.randomOutsourceLeadDays();
                LocalDate deliveryDate = addWorkingDays(osoDate, 2, cfg);
                LocalDate receiptDate = addWorkingDays(deliveryDate, leadDays, cfg);

                // 截止日过滤：外协发货日（流程最晚事件）在截止日之后 → 整条外协还没完成，跳过
                // 收料(receiptDate)由 OutsourceReceiptGenerator 自己再做截止日校验
                if (cfg.isAfterCutoff(deliveryDate)) continue;
                // 不超过销售订单交货日期
                if (order.getDeliveryDate() != null && receiptDate.isAfter(order.getDeliveryDate()))
                    receiptDate = order.getDeliveryDate();

                // 要求交期 = receiptDate
                LocalDate requiredDate = receiptDate;

                LocalDateTime requestTime = randomTime(orderDate, cfg);
                LocalDateTime approveTime = randomTime(approveDate, cfg);
                LocalDateTime osoTime = randomTime(osoDate, cfg);
                LocalDateTime osoApproveTime = laterTime(osoTime, cfg);
                LocalDateTime deliveryTime = randomTime(deliveryDate, cfg);
                // 任一时间落到未来 → 整条外协记录跳过,不生成 request/order/delivery/inv_task
                if (requestTime == null || approveTime == null || osoTime == null
                        || osoApproveTime == null || deliveryTime == null) continue;

                Long creatorId = cfg.pickCreator();
                String creatorName = userNames.getOrDefault(creatorId,
                        creatorId != null ? String.valueOf(creatorId) : "");
                Long approverId = cfg.pickApprover();
                String approverName = userNames.getOrDefault(approverId,
                        approverId != null ? String.valueOf(approverId) : "");

                String requestNo = CodeUtil.generate("OSR");
                String orderNo = CodeUtil.generate("OSO");

                // 单价随机 10~100
                BigDecimal unitPrice = BigDecimal.valueOf(10 + Math.random() * 90)
                        .setScale(2, RoundingMode.HALF_UP);
                BigDecimal totalAmount = unitPrice.multiply(qty).setScale(2, RoundingMode.HALF_UP);

                // 外协申请行（整单外协：关联销售订单号，stepName="整单外协"）
                cachedRequestRows.add(List.of(
                        requestNo, order.getOrderNo(), productNo, productName,
                        qty.toPlainString(), fmtDate(requiredDate),
                        "approved", approverName, fmtDateTime(approveTime),
                        fmtDateTime(requestTime), creatorName));

                // 外协订单行（整单外协特征：stepName="整单外协"）
                cachedOrderRows.add(List.of(
                        orderNo, supplierName, requestNo, order.getOrderNo(), productNo,
                        "整单外协",
                        qty.toPlainString(), unitPrice.toPlainString(), totalAmount.toPlainString(),
                        fmtDate(requiredDate),
                        "0", "0",
                        "confirmed", approverName, fmtDateTime(osoApproveTime),
                        fmtDateTime(osoTime), creatorName));

                // === BOM展开：为每个原材料生成一条发货记录和库存任务 ===
                // 仓库优先使用物料档案的默认仓库
                BizMaterial product = oi.getProductId() != null ? materialMap.get(oi.getProductId()) : null;
                Long defaultWhId = product != null && product.getDefaultWarehouseId() != null
                        ? product.getDefaultWarehouseId()
                        : (cfg.getWarehouseId() != null ? cfg.getWarehouseId() : 1L);

                List<BizBomItem> bomItems = Collections.emptyList();
                if (oi.getProductId() != null) {
                    BizBomVersion bv = activeBomMap.get(oi.getProductId());
                    if (bv != null) {
                        bomItems = bomItemMapper.selectList(
                                new LambdaQueryWrapper<BizBomItem>()
                                        .eq(BizBomItem::getBomVersionId, bv.getId())
                                        .eq(BizBomItem::getDeleted, 0));
                    }
                }

                if (!bomItems.isEmpty()) {
                    // 有BOM：按原材料展开发料
                    String invTaskNo = CodeUtil.generate("INK");
                    String taskWhId = String.valueOf(defaultWhId);
                    cachedInvTaskRows.add(List.of(invTaskNo, orderNo, "outsource_out", "completed",
                            "outsource_order", creatorName, taskWhId, fmtDateTime(deliveryTime), creatorName));

                    for (BizBomItem bi : bomItems) {
                        Long matId = bi.getMaterialId();
                        if (matId == null) continue;
                        BizMaterial mat = materialMap.get(matId);
                        if (mat == null) continue;

                        String matNo = mat.getMaterialNo() != null ? mat.getMaterialNo() : "";
                        String matName = mat.getMaterialName() != null ? mat.getMaterialName() : "";
                        String matUnit = mat.getUnit() != null ? mat.getUnit() : "";

                        // 每个物料使用自己的默认仓库
                        String matWhId = String.valueOf(mat.getDefaultWarehouseId() != null ? mat.getDefaultWarehouseId() : defaultWhId);

                        // 发料数 = BOM单件用量 * 订单行数量
                        BigDecimal bomQty = bi.getQuantity() != null ? bi.getQuantity() : BigDecimal.ONE;
                        BigDecimal sendQty = bomQty.multiply(qty).setScale(4, RoundingMode.HALF_UP);

                        String deliveryNo = CodeUtil.generate("OSD");
                        cachedDeliveryRows.add(List.of(
                                deliveryNo, orderNo, "material_send", matNo, matName,
                                sendQty.toPlainString(), "completed",
                                fmtDateTime(deliveryTime), creatorName));

                        // 库存任务明细 + 流水
                        cachedInvTaskItemRows.add(List.of(invTaskNo, matNo, matName, matUnit,
                                sendQty.toPlainString(), sendQty.toPlainString(), matWhId));
                        cachedInvTransactionRows.add(List.of(matNo, matWhId, "outsource_out",
                                sendQty.negate().toPlainString(), "outsource_order", orderNo,
                                fmtDateTime(deliveryTime)));
                    }
                } else {
                    // 无BOM：直接用产品本身作为发料物料
                    String whId = String.valueOf(defaultWhId);
                    String deliveryNo = CodeUtil.generate("OSD");
                    cachedDeliveryRows.add(List.of(
                            deliveryNo, orderNo, "material_send", productNo, productName,
                            qty.toPlainString(), "completed",
                            fmtDateTime(deliveryTime), creatorName));

                    String invTaskNo = CodeUtil.generate("INK");
                    cachedInvTaskRows.add(List.of(invTaskNo, orderNo, "outsource_out", "completed",
                            "outsource_order", creatorName, whId, fmtDateTime(deliveryTime), creatorName));
                    cachedInvTaskItemRows.add(List.of(invTaskNo, productNo, productName, productUnit,
                            qty.toPlainString(), qty.toPlainString(), whId));
                    cachedInvTransactionRows.add(List.of(productNo, whId, "outsource_out",
                            qty.negate().toPlainString(), "outsource_order", orderNo,
                            fmtDateTime(deliveryTime)));
                }
            }
        }

        Map<String, Object> tables = new LinkedHashMap<>();
        tables.put("outsource_request", cachedRequestRows.size());
        tables.put("outsource_order", cachedOrderRows.size());
        tables.put("outsource_delivery", cachedDeliveryRows.size());
        tables.put("inv_task", cachedInvTaskRows.size());
        tables.put("inv_task_item", cachedInvTaskItemRows.size());
        tables.put("inv_transaction", cachedInvTransactionRows.size());
        int total = cachedRequestRows.size() + cachedOrderRows.size() + cachedDeliveryRows.size()
                + cachedInvTaskRows.size() + cachedInvTaskItemRows.size() + cachedInvTransactionRows.size();
        return Map.of("tables", tables, "totalRows", total);
    }

    public void exportZip(HttpServletResponse response) throws IOException {
        if (cachedRequestRows == null) throw new BusinessException("请先执行生成");

        response.setContentType("application/zip");
        response.setHeader("Content-Disposition",
                "attachment; filename=" + java.net.URLEncoder.encode("06_外协管理.zip", "UTF-8"));

        List<SimTimeHelper.SheetSpec> sheets = new ArrayList<>();
        sheets.add(new SimTimeHelper.SheetSpec("06_外协管理_outsource_request.xlsx", REQUEST_HEADERS, cachedRequestRows));
        sheets.add(new SimTimeHelper.SheetSpec("06_外协管理_outsource_order.xlsx", ORDER_HEADERS, cachedOrderRows));
        sheets.add(new SimTimeHelper.SheetSpec("06_外协管理_outsource_delivery.xlsx", DELIVERY_HEADERS, cachedDeliveryRows));
        sheets.add(new SimTimeHelper.SheetSpec("06_外协管理_inv_task.xlsx", INV_TASK_HEADERS, cachedInvTaskRows));
        sheets.add(new SimTimeHelper.SheetSpec("06_外协管理_inv_task_item.xlsx", INV_TASK_ITEM_HEADERS, cachedInvTaskItemRows));
        sheets.add(new SimTimeHelper.SheetSpec("06_外协管理_inv_transaction.xlsx", INV_TRANSACTION_HEADERS, cachedInvTransactionRows));

        try (var zos = new java.util.zip.ZipOutputStream(response.getOutputStream())) {
            SimTimeHelper.parallelSplitWriteToZip(zos, sheets, 50_000, 10);
        }
    }

    @Transactional
    public Map<String, Object> importData(MultipartFile requestFile, MultipartFile orderFile, MultipartFile deliveryFile,
                                           MultipartFile invTaskFile, MultipartFile[] invTaskItemFiles, MultipartFile[] invTxFiles) {
        int success = 0;
        List<String> errors = new ArrayList<>();

        Map<String, Long> userNameToId = loadUserNameToIdMap();

        // 销售订单号→实体
        Map<String, BizSalesOrder> soNoToEntity = salesOrderMapper.selectList(
                new LambdaQueryWrapper<BizSalesOrder>().eq(BizSalesOrder::getDeleted, 0))
                .stream().collect(Collectors.toMap(BizSalesOrder::getOrderNo, o -> o, (a, b) -> a));

        // 产品编号→ID
        Map<String, Long> materialNoToId = materialMapper.selectList(
                new LambdaQueryWrapper<BizMaterial>().eq(BizMaterial::getDeleted, 0))
                .stream().collect(Collectors.toMap(BizMaterial::getMaterialNo, BizMaterial::getId, (a, b) -> a));

        // 供应商名称→ID（同时收集所有供应商 ID,供没填供应商时随机选）
        List<BizSupplier> allSuppliers = supplierMapper.selectList(
                new LambdaQueryWrapper<BizSupplier>().eq(BizSupplier::getDeleted, 0));
        Map<String, Long> supplierNameToId = allSuppliers.stream()
                .collect(Collectors.toMap(BizSupplier::getSupplierName, BizSupplier::getId, (a, b) -> a));
        List<Long> allSupplierIds = allSuppliers.stream().map(BizSupplier::getId).collect(Collectors.toList());

        // 1. 导入外协申请
        Map<String, Long> requestNoToId = new HashMap<>();
        if (requestFile != null && !requestFile.isEmpty()) {
            List<Map<String, String>> rows = ExcelUtil.parseImport(requestFile, REQUEST_HEADERS);
            for (int i = 0; i < rows.size(); i++) {
                Map<String, String> r = rows.get(i);
                try {
                    String soNo = r.get("销售订单号(关联)");
                    BizSalesOrder so = soNoToEntity.get(soNo);
                    if (so == null) { errors.add("申请第" + (i + 2) + "行: 销售订单不存在 " + soNo); continue; }

                    String productNoStr = r.get("订单行产品编号");
                    Long productId = materialNoToId.get(productNoStr);

                    // 查找对应的销售订单行
                    Long orderItemId = null;
                    if (productId != null) {
                        List<BizSalesOrderItem> soItems = salesOrderItemMapper.selectList(
                                new LambdaQueryWrapper<BizSalesOrderItem>()
                                        .eq(BizSalesOrderItem::getOrderId, so.getId())
                                        .eq(BizSalesOrderItem::getProductId, productId)
                                        .eq(BizSalesOrderItem::getDeleted, 0));
                        if (!soItems.isEmpty()) orderItemId = soItems.get(0).getId();
                    }

                    BizOutsourceRequest req = new BizOutsourceRequest();
                    req.setRequestNo(r.get("申请编号"));
                    // 整单外协无具体工单，workOrderId/taskId 用 0 作占位
                    // (DB列 work_order_id NOT NULL 无默认值，必须显式赋值)
                    req.setWorkOrderId(0L);
                    req.setTaskId(0L);
                    req.setOrderId(so.getId());
                    req.setOrderItemId(orderItemId);
                    req.setProductId(productId);
                    req.setStepName("整单外协");
                    req.setQuantity(parseBD(r.get("数量")));
                    req.setRequiredDate(parseDate(r.get("要求交期")));
                    req.setStatus(r.get("状态"));
                    req.setApprovedBy(userNameToId.get(r.get("审批人")));
                    req.setApprovedAt(parseDatetime(r.get("审批时间")));
                    req.setCreateTime(parseDatetime(r.get("创建时间")));
                    req.setCreateBy(userNameToId.get(r.get("创建人")));
                    req.setUpdateTime(req.getCreateTime());
                    req.setUpdateBy(req.getCreateBy());
                    outsourceRequestMapper.insert(req);
                    requestNoToId.put(req.getRequestNo(), req.getId());

                    // 回写销售订单状态为 outsourcing（通过 helper）
                    salesOrderStatusHelper.advanceToOutsourcing(so.getId());
                    BizSalesOrder refreshed = salesOrderMapper.selectById(so.getId());
                    if (refreshed != null && !"full".equals(refreshed.getOutsourceType())) {
                        refreshed.setOutsourceType("full");
                        salesOrderMapper.updateById(refreshed);
                    }

                    success++;
                } catch (Exception e) {
                    errors.add("申请第" + (i + 2) + "行: " + e.getMessage());
                }
            }
        }

        // 2. 导入外协订单
        Map<String, Long> orderNoToId = new HashMap<>();
        if (orderFile != null && !orderFile.isEmpty()) {
            List<Map<String, String>> rows = ExcelUtil.parseImport(orderFile, ORDER_HEADERS);
            for (int i = 0; i < rows.size(); i++) {
                Map<String, String> r = rows.get(i);
                try {
                    String requestNo = r.get("申请编号(关联)");
                    Long requestId = requestNoToId.get(requestNo);
                    String soNo = r.get("销售订单号");
                    BizSalesOrder so = soNoToEntity.get(soNo);
                    Long productId = materialNoToId.get(r.get("产品编号"));
                    Long supplierId = supplierNameToId.get(r.get("供应商名称"));

                    // 查找对应的销售订单行
                    Long soId = so != null ? so.getId() : null;
                    Long orderItemId = null;
                    if (soId != null && productId != null) {
                        List<BizSalesOrderItem> soItems = salesOrderItemMapper.selectList(
                                new LambdaQueryWrapper<BizSalesOrderItem>()
                                        .eq(BizSalesOrderItem::getOrderId, soId)
                                        .eq(BizSalesOrderItem::getProductId, productId)
                                        .eq(BizSalesOrderItem::getDeleted, 0));
                        if (!soItems.isEmpty()) orderItemId = soItems.get(0).getId();
                    }

                    // supplierId 为空 → 从所有供应商里随机一个(模拟数据需要看起来真实)
                    if (supplierId == null && !allSupplierIds.isEmpty()) {
                        supplierId = allSupplierIds.get(
                                java.util.concurrent.ThreadLocalRandom.current().nextInt(allSupplierIds.size()));
                    }

                    BizOutsourceOrder osoOrder = new BizOutsourceOrder();
                    osoOrder.setOrderNo(r.get("加工单号"));
                    osoOrder.setSupplierId(supplierId);
                    osoOrder.setRequestId(requestId);
                    // 整单外协无具体工单，workOrderId/taskId 用 0 占位避免 NOT NULL 报错
                    osoOrder.setWorkOrderId(0L);
                    osoOrder.setTaskId(0L);
                    osoOrder.setOrderId(soId);
                    osoOrder.setOrderItemId(orderItemId);
                    osoOrder.setProductId(productId);
                    osoOrder.setStepName(r.get("工序名称"));
                    osoOrder.setTotalQty(parseBD(r.get("总数量")));
                    osoOrder.setUnitPrice(parseBD(r.get("单价")));
                    BigDecimal totalAmount = parseBD(r.get("总金额"));
                    osoOrder.setTotalAmount(totalAmount);
                    // 税率固定 13%,税额从含税金额反算: 税额 = 总金额 × 13 / 113
                    BigDecimal taxRate13 = new BigDecimal("13");
                    osoOrder.setTaxRate(taxRate13);
                    if (totalAmount != null) {
                        osoOrder.setTaxAmount(totalAmount.multiply(taxRate13)
                                .divide(new BigDecimal("113"), 2, RoundingMode.HALF_UP));
                    }
                    osoOrder.setDeliveryDate(parseDate(r.get("交货日期")));
                    osoOrder.setDeliveredQty(parseBD(r.get("已发货数量")));
                    osoOrder.setQualifiedQty(parseBD(r.get("已收合格数量")));
                    osoOrder.setStatus(r.get("状态"));
                    osoOrder.setApprovedBy(userNameToId.get(r.get("审批人")));
                    osoOrder.setApprovedAt(parseDatetime(r.get("审批时间")));
                    osoOrder.setCreateTime(parseDatetime(r.get("创建时间")));
                    osoOrder.setCreateBy(userNameToId.get(r.get("创建人")));
                    osoOrder.setUpdateTime(osoOrder.getCreateTime());
                    osoOrder.setUpdateBy(osoOrder.getCreateBy());
                    outsourceOrderMapper.insert(osoOrder);
                    orderNoToId.put(osoOrder.getOrderNo(), osoOrder.getId());

                    // 关联表
                    if (requestId != null) {
                        BizOutsourceOrderRequest rel = new BizOutsourceOrderRequest();
                        rel.setOutsourceOrderId(osoOrder.getId());
                        rel.setOutsourceRequestId(requestId);
                        outsourceOrderRequestMapper.insert(rel);
                    }

                    success++;
                } catch (Exception e) {
                    errors.add("订单第" + (i + 2) + "行: " + e.getMessage());
                }
            }
        }

        // 3. 导入外协发货
        if (deliveryFile != null && !deliveryFile.isEmpty()) {
            List<Map<String, String>> rows = ExcelUtil.parseImport(deliveryFile, DELIVERY_HEADERS);
            for (int i = 0; i < rows.size(); i++) {
                Map<String, String> r = rows.get(i);
                try {
                    String osoOrderNo = r.get("加工单号(关联)");
                    Long orderId = orderNoToId.get(osoOrderNo);

                    BizOutsourceDelivery delivery = new BizOutsourceDelivery();
                    delivery.setDeliveryNo(r.get("发货编号"));
                    delivery.setOutsourceOrderId(orderId);
                    delivery.setDeliveryType(r.get("发货类型"));
                    delivery.setDeliveryQty(parseBD(r.get("发货数量")));
                    delivery.setStatus(r.get("状态"));
                    delivery.setCreateTime(parseDatetime(r.get("创建时间")));
                    delivery.setCreateBy(userNameToId.get(r.get("创建人")));
                    delivery.setUpdateTime(delivery.getCreateTime());
                    delivery.setUpdateBy(delivery.getCreateBy());
                    outsourceDeliveryMapper.insert(delivery);

                    // 回写外协订单状态 + 累加已发货数量(deliveredQty)
                    if (orderId != null) {
                        BizOutsourceOrder oo = outsourceOrderMapper.selectById(orderId);
                        if (oo != null) {
                            BigDecimal addQty = delivery.getDeliveryQty() != null ? delivery.getDeliveryQty() : BigDecimal.ZERO;
                            BigDecimal cur = oo.getDeliveredQty() != null ? oo.getDeliveredQty() : BigDecimal.ZERO;
                            oo.setDeliveredQty(cur.add(addQty));
                            if ("confirmed".equals(oo.getStatus())) {
                                oo.setStatus("material_sent");
                            }
                            outsourceOrderMapper.updateById(oo);
                        }
                    }

                    // 自动创建发货明细 BizOutsourceDeliveryItem
                    if (orderId != null) {
                        String matNoStr = r.get("物料编号");
                        Long matId = materialNoToId.get(matNoStr);
                        if (matId != null) {
                            com.aimes.modules.outsource.entity.BizOutsourceDeliveryItem dItem =
                                    new com.aimes.modules.outsource.entity.BizOutsourceDeliveryItem();
                            dItem.setDeliveryId(delivery.getId());
                            dItem.setMaterialId(matId);
                            dItem.setShouldQty(delivery.getDeliveryQty());
                            dItem.setActualQty(delivery.getDeliveryQty());
                            dItem.setWarehouseId(999L);
                            deliveryItemMapper.insert(dItem);
                        }
                    }

                    success++;
                } catch (Exception e) {
                    errors.add("发货第" + (i + 2) + "行: " + e.getMessage());
                }
            }
        }

        // 4. 导入库存任务
        Map<String, Long> invTaskNoToId = new HashMap<>();
        if (invTaskFile != null && !invTaskFile.isEmpty()) {
            List<Map<String, String>> rows = ExcelUtil.parseImport(invTaskFile, INV_TASK_HEADERS);
            for (int i = 0; i < rows.size(); i++) {
                Map<String, String> r = rows.get(i);
                try {
                    BizInvTask task = new BizInvTask();
                    task.setTaskNo(r.get("任务编号"));
                    String relatedNo = r.get("关联单据号");
                    task.setRelatedOrderNo(relatedNo);
                    task.setInvType(r.get("库存类型"));
                    task.setStatus(r.get("状态"));
                    String srcType = r.get("来源类型");
                    task.setSourceType(srcType);
                    // 反查 source_id：outsource_order 类型 → relatedNo 是外协加工单号
                    if ("outsource_order".equals(srcType) && relatedNo != null && !relatedNo.isEmpty()) {
                        Long oid = orderNoToId.get(relatedNo);
                        if (oid != null) task.setSourceId(oid);
                    }
                    task.setApplicantId(userNameToId.get(r.get("申请人")));
                    String whStr = r.get("仓库ID");
                    if (whStr != null && !whStr.isEmpty()) {
                        try { task.setWarehouseId(Long.valueOf(whStr.trim())); } catch (NumberFormatException ignored) {}
                    }
                    task.setCreateTime(parseDatetime(r.get("创建时间")));
                    task.setCreateBy(userNameToId.get(r.get("创建人")));
                    task.setUpdateTime(task.getCreateTime());
                    task.setUpdateBy(task.getCreateBy());
                    invTaskMapper.insert(task);
                    invTaskNoToId.put(task.getTaskNo(), task.getId());
                    success++;
                } catch (Exception e) {
                    errors.add("inv_task第" + (i + 2) + "行: " + e.getMessage());
                }
            }
        }

        // 5. 导入库存任务明细
        List<Map<String, String>> invTaskItemAllRows = parseAllParts(invTaskItemFiles, INV_TASK_ITEM_HEADERS);
        if (!invTaskItemAllRows.isEmpty()) {
            List<Map<String, String>> rows = invTaskItemAllRows;
            for (int i = 0; i < rows.size(); i++) {
                Map<String, String> r = rows.get(i);
                try {
                    Long taskId = invTaskNoToId.get(r.get("任务编号(关联)"));
                    if (taskId == null) { errors.add("inv_task_item第" + (i + 2) + "行: 任务不存在"); continue; }

                    BizInvTaskItem item = new BizInvTaskItem();
                    item.setTaskId(taskId);
                    String matNo = r.get("物料编号");
                    item.setMaterialId(materialNoToId.get(matNo));
                    item.setMaterialNo(matNo);
                    item.setMaterialName(r.get("物料名称"));
                    item.setUnit(r.get("单位"));
                    item.setQuantity(parseBD(r.get("数量")));
                    item.setProcessedQty(parseBD(r.get("已处理数量")));
                    String whStr = r.get("仓库ID");
                    if (whStr != null && !whStr.isEmpty()) {
                        try { item.setWarehouseId(Long.valueOf(whStr.trim())); } catch (NumberFormatException ignored) {}
                    }
                    invTaskItemMapper.insert(item);
                    success++;
                } catch (Exception e) {
                    errors.add("inv_task_item第" + (i + 2) + "行: " + e.getMessage());
                }
            }
        }

        // 6. 导入库存流水
        List<Map<String, String>> invTxAllRows = parseAllParts(invTxFiles, INV_TRANSACTION_HEADERS);
        if (!invTxAllRows.isEmpty()) {
            List<Map<String, String>> rows = invTxAllRows;
            for (int i = 0; i < rows.size(); i++) {
                Map<String, String> r = rows.get(i);
                try {
                    BizInvTransaction tx = new BizInvTransaction();
                    tx.setMaterialId(materialNoToId.get(r.get("物料编号")));
                    String whStr = r.get("仓库ID");
                    if (whStr != null && !whStr.isEmpty()) {
                        try { tx.setWarehouseId(Long.valueOf(whStr.trim())); } catch (NumberFormatException ignored) {}
                    }
                    tx.setTransactionType(r.get("交易类型"));
                    tx.setQuantity(parseBD(r.get("数量")));
                    tx.setRelatedOrderType(r.get("关联单据类型"));
                    String relOrderNo = r.get("关联单据号");
                    if (relOrderNo != null && !relOrderNo.isEmpty()) {
                        Long relId = orderNoToId.get(relOrderNo);
                        tx.setRelatedOrderId(relId);
                    }
                    tx.setCreateTime(parseDatetime(r.get("创建时间")));
                    tx.setCreateBy(userNameToId.values().stream().findFirst().orElse(null));
                    tx.setUpdateTime(tx.getCreateTime());
                    tx.setUpdateBy(tx.getCreateBy());
                    invTransactionMapper.insert(tx);
                    success++;
                } catch (Exception e) {
                    errors.add("inv_transaction第" + (i + 2) + "行: " + e.getMessage());
                }
            }
        }

        return Map.of("success", success, "errors", errors);
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
