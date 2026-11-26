package com.aimes.modules.dataimport.service.simulation;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.utils.CodeUtil;
import com.aimes.common.utils.ExcelUtil;
import com.aimes.modules.bom.entity.BizBomItem;
import com.aimes.modules.bom.entity.BizBomVersion;
import com.aimes.modules.bom.entity.BizProcess;
import com.aimes.modules.bom.entity.BizProcessRoute;
import com.aimes.modules.bom.entity.BizProcessRouteStep;
import com.aimes.modules.bom.mapper.*;
import com.aimes.modules.master.entity.BizMaterial;
import com.aimes.modules.master.mapper.BizMaterialMapper;
import com.aimes.modules.mrp.entity.BizProductionPlan;
import com.aimes.modules.mrp.entity.BizProductionPlanItem;
import com.aimes.modules.mrp.mapper.BizProductionPlanItemMapper;
import com.aimes.modules.mrp.mapper.BizProductionPlanMapper;
import com.aimes.modules.order.entity.BizSalesOrder;
import com.aimes.modules.order.entity.BizSalesOrderItem;
import com.aimes.modules.order.mapper.BizSalesOrderItemMapper;
import com.aimes.modules.order.mapper.BizSalesOrderMapper;
import com.aimes.modules.production.entity.BizWoBomSnapshot;
import com.aimes.modules.production.entity.BizWoProcessTask;
import com.aimes.modules.production.entity.BizWorkOrder;
import com.aimes.modules.production.mapper.BizWoBomSnapshotMapper;
import com.aimes.modules.production.mapper.BizWoProcessTaskMapper;
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
 * ② 生产工单生成器
 * 读取生产计划行 → 生成 BizWorkOrder + BizWoProcessTask + BizWoBomSnapshot
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WorkOrderGenerator {

    private final BizProductionPlanMapper planMapper;
    private final BizProductionPlanItemMapper planItemMapper;
    private final BizSalesOrderMapper salesOrderMapper;
    private final BizSalesOrderItemMapper salesOrderItemMapper;
    private final SalesOrderStatusHelper salesOrderStatusHelper;
    private final BizMaterialMapper materialMapper;
    private final BizBomVersionMapper bomVersionMapper;
    private final BizBomItemMapper bomItemMapper;
    private final BizProcessRouteMapper routeMapper;
    private final BizProcessRouteStepMapper routeStepMapper;
    private final BizProcessMapper processMapper;
    private final BizWorkOrderMapper workOrderMapper;
    private final BizWoProcessTaskMapper processTaskMapper;
    private final BizWoBomSnapshotMapper bomSnapshotMapper;
    private final BizInvTaskMapper invTaskMapper;
    private final BizInvTaskItemMapper invTaskItemMapper;
    private final BizInvTransactionMapper invTransactionMapper;
    private final SysUserMapper userMapper;
    private final com.aimes.modules.team.mapper.BizTeamMapper teamMapper;
    private final com.aimes.modules.quality.mapper.BizQcTemplateMapper qcTemplateMapper;
    private final com.aimes.modules.mrp.mapper.BizProductionPlanItemStepMapper planItemStepMapper;
    private final com.aimes.modules.mrp.mapper.BizProductionPlanOrderRelMapper planOrderRelMapper;

    public static final List<String> WO_HEADERS = List.of(
            "工单编号", "销售订单号(关联)", "产品编号", "产品名称", "计划编号(关联)",
            "计划数量", "完成数量", "交货日期", "优先级", "状态", "工单类型", "创建时间", "创建人");

    public static final List<String> TASK_HEADERS = List.of(
            "工单编号(关联)", "工序序号", "工序组", "工序名称", "标准工时", "计件单价",
            "计划数量", "合格数量", "不良数量",
            "指派方式(person/team)", "指派人", "指派班组",
            "状态", "开始时间", "结束时间", "质检模板", "创建时间", "创建人");

    public static final List<String> BOM_SNAP_HEADERS = List.of(
            "工单编号(关联)", "物料编号", "物料名称", "需求数量", "已发料数量", "已发料任务数",
            "创建时间", "创建人");

    public static final List<String> INV_TASK_HEADERS = List.of(
            "任务编号", "关联单据号", "库存类型", "状态", "来源类型", "申请人", "仓库ID", "创建时间", "创建人");
    public static final List<String> INV_TASK_ITEM_HEADERS = List.of(
            "任务编号(关联)", "物料编号", "物料名称", "单位", "数量", "已处理数量", "仓库ID");
    public static final List<String> INV_TRANSACTION_HEADERS = List.of(
            "物料编号", "仓库ID", "交易类型", "数量", "关联单据类型", "关联单据号", "创建时间");

    private List<List<Object>> cachedWoRows, cachedTaskRows, cachedBomRows;
    private List<List<Object>> cachedInvTaskRows, cachedInvTaskItemRows, cachedInvTransactionRows;
    // 下拉框数据源（用于process_task的指派人/班组列）
    private List<String> allUserNames, allTeamNames;

    public Map<String, Object> generate(SimConfig cfg) {
        Map<Long, String> userNames = loadUserNames(new ArrayList<>(cfg.getCreatorIds()));
        Map<Long, BizMaterial> matMap = materialMapper.selectList(
                new LambdaQueryWrapper<BizMaterial>().eq(BizMaterial::getDeleted, 0))
                .stream().collect(Collectors.toMap(BizMaterial::getId, m -> m, (a, b) -> a));

        // 查询所有已完成的生产计划行
        List<BizProductionPlanItem> planItems = planItemMapper.selectList(
                new LambdaQueryWrapper<BizProductionPlanItem>().eq(BizProductionPlanItem::getDeleted, 0));

        // 加载计划主表（获取计划编号和时间）
        Map<Long, BizProductionPlan> planMap = planMapper.selectList(
                new LambdaQueryWrapper<BizProductionPlan>().eq(BizProductionPlan::getDeleted, 0))
                .stream().collect(Collectors.toMap(BizProductionPlan::getId, p -> p, (a, b) -> a));

        // 加载销售订单映射（从计划→订单关联）
        Map<Long, BizSalesOrder> orderMap = salesOrderMapper.selectList(
                new LambdaQueryWrapper<BizSalesOrder>().eq(BizSalesOrder::getDeleted, 0))
                .stream().collect(Collectors.toMap(BizSalesOrder::getId, o -> o, (a, b) -> a));
        Map<Long, List<BizSalesOrderItem>> orderItemsMap = salesOrderItemMapper.selectList(
                new LambdaQueryWrapper<BizSalesOrderItem>().eq(BizSalesOrderItem::getDeleted, 0))
                .stream().collect(Collectors.groupingBy(BizSalesOrderItem::getOrderId));

        // 关键：planItemId → 真实的 orderId 映射（来自 biz_production_plan_order_rel）
        // 这才是 plan_item 跟具体销售订单的真实关联，否则按 productId 猜会把所有 WO 绑到同一个订单
        Map<Long, Long> planItemToOrderId = planOrderRelMapper.selectList(
                new LambdaQueryWrapper<com.aimes.modules.mrp.entity.BizProductionPlanOrderRel>()
                        .eq(com.aimes.modules.mrp.entity.BizProductionPlanOrderRel::getDeleted, 0))
                .stream().collect(Collectors.toMap(
                        com.aimes.modules.mrp.entity.BizProductionPlanOrderRel::getPlanItemId,
                        com.aimes.modules.mrp.entity.BizProductionPlanOrderRel::getOrderId,
                        (a, b) -> a));

        // 多线程并行: 每个 planItem 独立生成工单,工单间无共享可变状态
        cachedWoRows = java.util.Collections.synchronizedList(new ArrayList<>());
        cachedTaskRows = java.util.Collections.synchronizedList(new ArrayList<>());
        cachedBomRows = java.util.Collections.synchronizedList(new ArrayList<>());
        cachedInvTaskRows = java.util.Collections.synchronizedList(new ArrayList<>());
        cachedInvTaskItemRows = java.util.Collections.synchronizedList(new ArrayList<>());
        cachedInvTransactionRows = java.util.Collections.synchronizedList(new ArrayList<>());

        // 预加载下拉框数据源 — 同时把 id→name 映射缓存下来给 getProcessSteps 复用,
        // 避免并行循环里每个 plan_item 都重新查全表 user/team/qcTemplate(2000+ 并行 * 多次全表查询 = 连接池打爆 + socket 超时)
        List<SysUser> allUsers = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getDeleted, 0));
        cachedUserIdToName = allUsers.stream()
                .filter(u -> u.getId() != null)
                .collect(Collectors.toMap(SysUser::getId,
                        u -> u.getRealName() != null ? u.getRealName() : (u.getUsername() != null ? u.getUsername() : ""),
                        (a, b) -> a));
        allUserNames = allUsers.stream()
                .filter(u -> "active".equals(u.getStatus()))
                .map(u -> u.getRealName() != null ? u.getRealName() : u.getUsername())
                .filter(n -> n != null && !n.isEmpty()).distinct().sorted().collect(Collectors.toList());

        List<com.aimes.modules.team.entity.BizTeam> allTeams = teamMapper.selectList(
                new LambdaQueryWrapper<com.aimes.modules.team.entity.BizTeam>()
                        .eq(com.aimes.modules.team.entity.BizTeam::getDeleted, 0));
        cachedTeamIdToName = allTeams.stream()
                .filter(t -> t.getId() != null && t.getTeamName() != null)
                .collect(Collectors.toMap(com.aimes.modules.team.entity.BizTeam::getId,
                        com.aimes.modules.team.entity.BizTeam::getTeamName, (a, b) -> a));
        allTeamNames = allTeams.stream().map(com.aimes.modules.team.entity.BizTeam::getTeamName)
                .filter(n -> n != null && !n.isEmpty()).distinct().sorted().collect(Collectors.toList());

        cachedQcTemplateNameMap = qcTemplateMapper.selectList(
                new LambdaQueryWrapper<com.aimes.modules.quality.entity.BizQcTemplate>()
                        .eq(com.aimes.modules.quality.entity.BizQcTemplate::getDeleted, 0))
                .stream().filter(t -> t.getId() != null)
                .collect(Collectors.toMap(com.aimes.modules.quality.entity.BizQcTemplate::getId,
                        t -> t.getTemplateName() != null ? t.getTemplateName() : "", (a, b) -> a));

        // 重置工艺路线缓存(每次 generate 都要新算一遍, 避免上次跑的脏数据)
        cachedStepsByProduct.clear();

        planItems.parallelStream().forEach(pi -> {
            BizProductionPlan plan = planMap.get(pi.getPlanId());
            if (plan == null) return;

            BizMaterial product = matMap.get(pi.getProductId());
            if (product == null) return;

            // 查找关联的销售订单编号 — 优先用 plan_order_rel 里的真实 orderId
            String orderNo = "";
            Long realOrderId = planItemToOrderId.get(pi.getId());
            if (realOrderId != null) {
                BizSalesOrder order = orderMap.get(realOrderId);
                if (order != null) orderNo = order.getOrderNo();
            }
            // 兜底：plan_order_rel 没记录时，按 productId 找第一个匹配的订单
            if (orderNo.isEmpty()) {
                for (Map.Entry<Long, List<BizSalesOrderItem>> e : orderItemsMap.entrySet()) {
                    for (BizSalesOrderItem oi : e.getValue()) {
                        if (Objects.equals(oi.getProductId(), pi.getProductId())) {
                            BizSalesOrder order = orderMap.get(e.getKey());
                            if (order != null) orderNo = order.getOrderNo();
                            break;
                        }
                    }
                    if (!orderNo.isEmpty()) break;
                }
            }
            // 交货日期 = 计划结束日期（不用销售订单的deliveryDate，避免超出计划范围）
            LocalDate delivery = pi.getPlanEndDate() != null ? pi.getPlanEndDate() :
                    (plan.getPlanDateTo() != null ? plan.getPlanDateTo() : LocalDate.now().plusDays(14));

            // 时间计算：计划创建时间后推迟 delayDays 工作日
            LocalDate planDate = plan.getCreateTime() != null ? plan.getCreateTime().toLocalDate() :
                    (plan.getPlanDateFrom() != null ? plan.getPlanDateFrom() : LocalDate.now().minusDays(20));
            LocalDate woDate = addWorkingDays(planDate, cfg.getDelayDays(), cfg);
            woDate = snapToWorkingDay(woDate, cfg);
            // 截止日过滤：工单创建日在截止日之后 → 该工单尚未存在，跳过
            if (cfg.isAfterCutoff(woDate)) return;
            LocalDateTime woTime = randomTime(woDate, cfg);
            if (woTime == null) return;
            Long creatorId = cfg.pickCreator();
            String creatorName = userNames.getOrDefault(creatorId, String.valueOf(creatorId));

            String woNo = CodeUtil.generate("WO");
            BigDecimal planQty = pi.getPlanQty() != null ? pi.getPlanQty() : BigDecimal.ONE;

            // 工单初始状态为pending，completedQty=0，由③报工模块回写
            cachedWoRows.add(List.of(
                    woNo, orderNo, product.getMaterialNo(), product.getMaterialName(), plan.getPlanNo(),
                    planQty.toPlainString(), "0",
                    fmtDate(delivery), "1", "pending", "normal",
                    fmtDateTime(woTime), creatorName));

            // 工序任务 — 从产品工艺路线生成
            List<StepInfo> steps = getProcessSteps(pi.getProductId(), matMap);
            if (delivery == null) delivery = woDate.plusDays(14);

            // 工序时间均分: 只在 [woTime, delivery] 之间的"工作日"上分配,跳过周末/节假日
            // 每道工序至少占 1 个工作日; 不够时往后顺延
            LocalDate windowStartDate = SimTimeHelper.snapToWorkingDay(woTime.toLocalDate(), cfg);
            LocalDate windowEndDate = SimTimeHelper.snapToWorkingDay(delivery, cfg);
            if (windowEndDate.isBefore(windowStartDate)) windowEndDate = windowStartDate;
            List<LocalDate> workDays = SimTimeHelper.getWorkingDays(windowStartDate, windowEndDate, cfg);
            // 工作日不够分: 从交期往后顺延工作日,直到每道工序都有至少 1 天
            while (workDays.size() < steps.size()) {
                LocalDate next = SimTimeHelper.addWorkingDays(workDays.isEmpty() ? windowStartDate : workDays.get(workDays.size() - 1), 1, cfg);
                workDays.add(next);
            }

            for (int si = 0; si < steps.size(); si++) {
                StepInfo step = steps.get(si);
                // 每道工序按工作日数均分
                int perStep = Math.max(1, workDays.size() / steps.size());
                int startIdx = Math.min(si * perStep, workDays.size() - 1);
                int endIdx = (si == steps.size() - 1) ? workDays.size() - 1
                        : Math.min(startIdx + perStep - 1, workDays.size() - 1);
                if (endIdx < startIdx) endIdx = startIdx;
                LocalDateTime startTime = workDays.get(startIdx).atTime(cfg.getStartHour(), cfg.getStartMin());
                LocalDateTime endTime = workDays.get(endIdx).atTime(cfg.getEndHour(), cfg.getEndMin());
                if (!endTime.isAfter(startTime)) endTime = startTime.plusHours(2);

                // 指派方式/指派人/指派班组 — 从工序库绑定信息带出默认值
                String assignType = step.bindType != null ? step.bindType : "";
                String assignPerson = "person".equals(assignType) ? step.bindName : "";
                String assignTeam = "team".equals(assignType) ? step.bindName : "";
                cachedTaskRows.add(List.of(
                        woNo, String.valueOf(step.stepNo), String.valueOf(step.stepGroup),
                        step.name, step.stdTime != null ? String.valueOf(step.stdTime) : "",
                        step.price != null ? step.price.toPlainString() : "",
                        planQty.toPlainString(), "0", "0",
                        assignType, assignPerson, assignTeam,
                        "pending", fmtDateTime(startTime), fmtDateTime(endTime),
                        step.qcTemplateName != null ? step.qcTemplateName : "",
                        fmtDateTime(woTime), creatorName));
            }

            // === 生产计划行项工序指派(plan_item_step) — 直接写入DB ===
            // 这个数据不需要导出Excel修改，在导入时自动创建
            // 但generate阶段先记录步骤信息，importData时再写入

            // BOM快照 — 从产品BOM生成
            List<BizBomItem> bomItems = getBomItems(pi.getProductId());
            for (BizBomItem bi : bomItems) {
                if (bi.getMaterialId() == null) continue;
                BizMaterial mat = matMap.get(bi.getMaterialId());
                if (mat == null) continue;
                BigDecimal useQty = bi.getActualQuantity() != null ? bi.getActualQuantity() :
                        (bi.getQuantity() != null ? bi.getQuantity() : BigDecimal.ONE);
                BigDecimal reqQty = useQty.multiply(planQty).setScale(4, RoundingMode.HALF_UP);

                cachedBomRows.add(List.of(
                        woNo, mat.getMaterialNo(), mat.getMaterialName(),
                        reqQty.toPlainString(), "0", "0",  // requiredQty, issuedQty=0, issuedTaskQty=0
                        fmtDateTime(woTime), creatorName));
            }

            // === 库存任务：领料出库(production_out) ===
            // 仓库优先使用物料档案的默认仓库，支持在Excel中修改
            LocalDateTime issueTime = null;
            if (!bomItems.isEmpty()) {
                // 任务级仓库取第一个BOM物料的默认仓库
                Long firstWhId = bomItems.stream()
                        .map(bi -> matMap.get(bi.getMaterialId()))
                        .filter(m -> m != null && m.getDefaultWarehouseId() != null)
                        .map(BizMaterial::getDefaultWarehouseId)
                        .findFirst().orElse(cfg.getWarehouseId() != null ? cfg.getWarehouseId() : 1L);
                String taskWhId = String.valueOf(firstWhId);

                String issueTaskNo = CodeUtil.generate("INK");
                issueTime = randomTime(addWorkingDays(woDate, 1, cfg), cfg);
                // 领料时间为 null 或超过截止日 → 这批领料还没发生,跳过本次工单的领料生成
                if (issueTime == null || cfg.isAfterCutoff(issueTime)) {
                    return;
                }
                cachedInvTaskRows.add(List.of(issueTaskNo, woNo, "production_out", "completed", "work_order", creatorName, taskWhId, fmtDateTime(issueTime), creatorName));

                for (BizBomItem bi : bomItems) {
                    if (bi.getMaterialId() == null) continue;
                    BizMaterial mat = matMap.get(bi.getMaterialId());
                    if (mat == null) continue;
                    BigDecimal useQty = bi.getActualQuantity() != null ? bi.getActualQuantity() :
                            (bi.getQuantity() != null ? bi.getQuantity() : BigDecimal.ONE);
                    BigDecimal reqQty = useQty.multiply(planQty).setScale(4, RoundingMode.HALF_UP);

                    // 每个物料使用自己的默认仓库
                    String matWhId = String.valueOf(mat.getDefaultWarehouseId() != null ? mat.getDefaultWarehouseId() : firstWhId);
                    cachedInvTaskItemRows.add(List.of(issueTaskNo, mat.getMaterialNo(), mat.getMaterialName(),
                            mat.getUnit() != null ? mat.getUnit() : "", reqQty.toPlainString(), reqQty.toPlainString(), matWhId));
                    cachedInvTransactionRows.add(List.of(mat.getMaterialNo(), matWhId, "production_out",
                            reqQty.negate().toPlainString(), "work_order", woNo, fmtDateTime(issueTime)));
                }
            }

            // 成品入库(production_in)移到③报工模块——completedQty确定后再生成
        }); // end parallel forEach over plan items

        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> tables = new LinkedHashMap<>();
        tables.put("work_order", cachedWoRows.size());
        tables.put("process_task", cachedTaskRows.size());
        tables.put("bom_snapshot", cachedBomRows.size());
        tables.put("inv_task", cachedInvTaskRows.size());
        tables.put("inv_task_item", cachedInvTaskItemRows.size());
        tables.put("inv_transaction", cachedInvTransactionRows.size());
        result.put("tables", tables);
        result.put("totalRows", cachedWoRows.size() + cachedTaskRows.size() + cachedBomRows.size()
                + cachedInvTaskRows.size() + cachedInvTaskItemRows.size() + cachedInvTransactionRows.size());
        return result;
    }

    public void exportZip(HttpServletResponse response) throws IOException {
        if (cachedWoRows == null) throw new BusinessException("请先执行生成");

        response.setContentType("application/zip");
        response.setHeader("Content-Disposition",
                "attachment; filename=" + java.net.URLEncoder.encode("02_生产工单.zip", "UTF-8"));

        // process_task 列下拉框
        Map<Integer, List<String>> dropdowns = new LinkedHashMap<>();
        dropdowns.put(9, List.of("person", "team"));
        dropdowns.put(10, allUserNames != null ? allUserNames : List.of());
        dropdowns.put(11, allTeamNames != null ? allTeamNames : List.of());

        List<SimTimeHelper.SheetSpec> sheets = new ArrayList<>();
        sheets.add(new SimTimeHelper.SheetSpec("02_生产工单_work_order.xlsx", WO_HEADERS, cachedWoRows));
        sheets.add(new SimTimeHelper.SheetSpec("02_生产工单_process_task.xlsx", TASK_HEADERS, cachedTaskRows, dropdowns));
        sheets.add(new SimTimeHelper.SheetSpec("02_生产工单_bom_snapshot.xlsx", BOM_SNAP_HEADERS, cachedBomRows));
        sheets.add(new SimTimeHelper.SheetSpec("02_生产工单_inv_task.xlsx", INV_TASK_HEADERS, cachedInvTaskRows));
        sheets.add(new SimTimeHelper.SheetSpec("02_生产工单_inv_task_item.xlsx", INV_TASK_ITEM_HEADERS, cachedInvTaskItemRows));
        sheets.add(new SimTimeHelper.SheetSpec("02_生产工单_inv_transaction.xlsx", INV_TRANSACTION_HEADERS, cachedInvTransactionRows));

        try (var zos = new java.util.zip.ZipOutputStream(response.getOutputStream())) {
            SimTimeHelper.parallelSplitWriteToZip(zos, sheets, 50_000, 10);
        }
    }

    @Transactional
    public Map<String, Object> importData(MultipartFile woFile, MultipartFile[] taskFiles, MultipartFile[] bomFiles,
                                           MultipartFile invTaskFile, MultipartFile[] invTaskItemFiles, MultipartFile[] invTxFiles) {
        // 多线程: 每段(WO / 工序 / BOM / 库存任务...)按行并行,段间保持顺序避免 FK 依赖
        java.util.concurrent.atomic.AtomicInteger success = new java.util.concurrent.atomic.AtomicInteger();
        List<String> errors = java.util.Collections.synchronizedList(new ArrayList<>());

        Map<String, Long> userNameToId = loadUserNameToIdMap();
        Map<String, Long> matNoToId = loadMaterialNoToIdMap();
        // 计划编号→计划ID映射
        Map<String, Long> planNoToId = planMapper.selectList(
                new LambdaQueryWrapper<BizProductionPlan>().eq(BizProductionPlan::getDeleted, 0))
                .stream().collect(Collectors.toMap(BizProductionPlan::getPlanNo, BizProductionPlan::getId, (a, b) -> a));
        // 计划编号+产品编号 → 计划行项ID映射（工单需要planItemId而非planId）
        Map<String, Long> planProductToItemId = new HashMap<>();
        for (Map.Entry<String, Long> pe : planNoToId.entrySet()) {
            planItemMapper.selectList(new LambdaQueryWrapper<BizProductionPlanItem>()
                    .eq(BizProductionPlanItem::getPlanId, pe.getValue())
                    .eq(BizProductionPlanItem::getDeleted, 0))
                    .forEach(pi -> {
                        BizMaterial m = pi.getProductId() != null ? materialMapper.selectById(pi.getProductId()) : null;
                        String productNo = m != null ? m.getMaterialNo() : "";
                        planProductToItemId.put(pe.getKey() + "_" + productNo, pi.getId());
                    });
        }
        Map<String, Long> orderNoToId = salesOrderMapper.selectList(
                new LambdaQueryWrapper<BizSalesOrder>().eq(BizSalesOrder::getDeleted, 0))
                .stream().collect(Collectors.toMap(BizSalesOrder::getOrderNo, BizSalesOrder::getId, (a, b) -> a));
        // 班组名称→ID映射
        Map<String, Long> teamNameToId = loadTeamNameToIdMap();

        // 1. 导入工单
        Map<String, Long> woNoToId = new java.util.concurrent.ConcurrentHashMap<>();
        // 收集所有被工单关联到的销售订单ID, 工单全部 insert 完后一次性 bulk update 状态
        Set<Long> touchedOrderIds = java.util.Collections.synchronizedSet(new HashSet<>());
        if (woFile != null && !woFile.isEmpty()) {
            List<Map<String, String>> rows = ExcelUtil.parseImport(woFile, WO_HEADERS);
            java.util.stream.IntStream.range(0, rows.size()).parallel().forEach(i -> {
                Map<String, String> r = rows.get(i);
                try {
                    BizWorkOrder wo = new BizWorkOrder();
                    wo.setWoNo(r.get("工单编号"));
                    wo.setOrderId(orderNoToId.get(r.get("销售订单号(关联)")));
                    wo.setProductId(matNoToId.get(r.get("产品编号")));
                    // orderItemId: 通过 orderId + productId 查找订单行项
                    if (wo.getOrderId() != null && wo.getProductId() != null) {
                        BizSalesOrderItem oi = salesOrderItemMapper.selectOne(
                                new LambdaQueryWrapper<BizSalesOrderItem>()
                                        .eq(BizSalesOrderItem::getOrderId, wo.getOrderId())
                                        .eq(BizSalesOrderItem::getProductId, wo.getProductId())
                                        .eq(BizSalesOrderItem::getDeleted, 0)
                                        .last("LIMIT 1"));
                        if (oi != null) wo.setOrderItemId(oi.getId());
                    }
                    // planItemId通过 计划编号+产品编号 联合查找
                    String planKey = r.get("计划编号(关联)") + "_" + r.get("产品编号");
                    wo.setPlanItemId(planProductToItemId.get(planKey));
                    wo.setPlanQty(parseBD(r.get("计划数量")));
                    wo.setCompletedQty(parseBD(r.get("完成数量")));
                    wo.setDeliveryDate(parseDate(r.get("交货日期")));
                    wo.setPriority(parseInt(r.get("优先级")));
                    wo.setStatus(r.get("状态"));
                    wo.setWorkOrderType(r.get("工单类型"));
                    // stockInTaskQty/stockInQty/invTaskStatus 由③报工回写后或⑦库存台账设置
                    wo.setCreateTime(parseDatetime(r.get("创建时间")));
                    wo.setCreateBy(userNameToId.get(r.get("创建人")));
                    wo.setUpdateTime(wo.getCreateTime());
                    wo.setUpdateBy(wo.getCreateBy());
                    workOrderMapper.insert(wo);
                    woNoToId.put(wo.getWoNo(), wo.getId());

                    // 收集 orderId, 工单全部 insert 完后一次性 bulk update 销售订单状态
                    // 不在并发循环里改销售订单 → 避免多线程争同一行锁导致 Lock wait timeout
                    if (wo.getOrderId() != null) touchedOrderIds.add(wo.getOrderId());

                    success.incrementAndGet();
                } catch (Exception e) {
                    errors.add("work_order第" + (i + 2) + "行: " + e.getMessage());
                }
            });

            // 销售订单状态推进: 把所有被工单关联到的、还在 confirmed/mrp_planned 的订单一次性升到 in_production
            if (!touchedOrderIds.isEmpty()) {
                int advanced = salesOrderMapper.update(null,
                        new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<com.aimes.modules.order.entity.BizSalesOrder>()
                                .in(com.aimes.modules.order.entity.BizSalesOrder::getId, touchedOrderIds)
                                .in(com.aimes.modules.order.entity.BizSalesOrder::getStatus,
                                        java.util.List.of("confirmed", "mrp_planned"))
                                .set(com.aimes.modules.order.entity.BizSalesOrder::getStatus, "in_production")
                                .set(com.aimes.modules.order.entity.BizSalesOrder::getUpdateTime, java.time.LocalDateTime.now()));
                log.info("[Sim WO 导入] 销售订单状态推进 → in_production 数量: {}", advanced);
            }

            // WO 全部插入后,按真实工单数据重算每个 plan_item 的 releasedQty + 状态
            // 这是幂等的,无论 import 跑几次结果都一致
            Set<Long> touchedPlanItemIds = workOrderMapper.selectList(
                            new LambdaQueryWrapper<BizWorkOrder>()
                                    .eq(BizWorkOrder::getDeleted, 0)
                                    .isNotNull(BizWorkOrder::getPlanItemId))
                    .stream().map(BizWorkOrder::getPlanItemId).collect(Collectors.toSet());
            Set<Long> touchedPlanIds = new HashSet<>();
            for (Long piId : touchedPlanItemIds) {
                BigDecimal totalReleased = workOrderMapper.selectList(
                                new LambdaQueryWrapper<BizWorkOrder>()
                                        .eq(BizWorkOrder::getPlanItemId, piId)
                                        .eq(BizWorkOrder::getDeleted, 0)
                                        .ne(BizWorkOrder::getStatus, "cancelled"))
                        .stream().map(w -> w.getPlanQty() != null ? w.getPlanQty() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BizProductionPlanItem pi = planItemMapper.selectById(piId);
                if (pi != null) {
                    pi.setReleasedQty(totalReleased);
                    BigDecimal planQty = pi.getPlanQty() != null ? pi.getPlanQty() : BigDecimal.ZERO;
                    // 仅推进尚未完工/未开始下达过的状态
                    if (!"completed".equals(pi.getStatus())) {
                        if (totalReleased.compareTo(planQty) >= 0 && planQty.compareTo(BigDecimal.ZERO) > 0) {
                            pi.setStatus("fully_released");
                        } else if (totalReleased.compareTo(BigDecimal.ZERO) > 0) {
                            pi.setStatus("partial_released");
                        }
                    }
                    planItemMapper.updateById(pi);
                    if (pi.getPlanId() != null) touchedPlanIds.add(pi.getPlanId());
                }
            }

            // 有工单下达的计划 → 主表状态从 confirmed 推到 in_progress(执行中)
            for (Long planId : touchedPlanIds) {
                BizProductionPlan plan = planMapper.selectById(planId);
                if (plan != null && "confirmed".equals(plan.getStatus())) {
                    plan.setStatus("in_progress");
                    planMapper.updateById(plan);
                }
            }
        }

        // 2. 导入工序任务
        List<Map<String, String>> taskAllRows = parseAllParts(taskFiles, TASK_HEADERS);
        if (!taskAllRows.isEmpty()) {
            List<Map<String, String>> rows = taskAllRows;
            java.util.stream.IntStream.range(0, rows.size()).parallel().forEach(i -> {
                Map<String, String> r = rows.get(i);
                try {
                    Long woId = woNoToId.get(r.get("工单编号(关联)"));
                    if (woId == null) { errors.add("process_task第" + (i + 2) + "行: 工单不存在"); return; }

                    BizWoProcessTask t = new BizWoProcessTask();
                    t.setWorkOrderId(woId);
                    t.setStepNo(parseInt(r.get("工序序号")));
                    t.setStepGroup(parseInt(r.get("工序组")));
                    t.setStepName(r.get("工序名称"));
                    t.setStandardTime(parseInt(r.get("标准工时")));
                    t.setPiecePrice(parseBD(r.get("计件单价")));
                    t.setPlanQty(parseBD(r.get("计划数量")));
                    t.setQualifiedQty(parseBD(r.get("合格数量")));
                    t.setDefectQty(parseBD(r.get("不良数量")));
                    t.setReportedQty(BigDecimal.ZERO);

                    // 指派方式: person=个人指派, team=班组指派
                    String assignType = r.get("指派方式(person/team)");
                    t.setAssignType(assignType != null && !assignType.isEmpty() ? assignType : "person");
                    if ("team".equals(t.getAssignType())) {
                        String teamName = r.get("指派班组");
                        Long teamId = teamNameToId.get(teamName);
                        t.setAssignedTeamId(teamId);
                        t.setAssignedTeamName(teamName);
                    } else {
                        String personName = r.get("指派人");
                        t.setAssignedUserId(userNameToId.get(personName));
                        t.setAssignedUserName(personName);
                    }
                    t.setStatus(r.get("状态"));
                    t.setIsSemiStock(0);
                    t.setIsOutsourced(0);
                    t.setIsOutsourceable(0);
                    String qcTplName = r.get("质检模板");
                    if (qcTplName != null && !qcTplName.isEmpty()) {
                        Map<String, Long> tplNameToId = qcTemplateMapper.selectList(
                                new LambdaQueryWrapper<com.aimes.modules.quality.entity.BizQcTemplate>()
                                        .eq(com.aimes.modules.quality.entity.BizQcTemplate::getDeleted, 0))
                                .stream().collect(Collectors.toMap(
                                        com.aimes.modules.quality.entity.BizQcTemplate::getTemplateName,
                                        com.aimes.modules.quality.entity.BizQcTemplate::getId, (a, b) -> a));
                        t.setQcTemplateId(tplNameToId.get(qcTplName));
                    }
                    t.setStartTime(parseDatetime(r.get("开始时间")));
                    t.setEndTime(parseDatetime(r.get("结束时间")));
                    t.setCreateTime(parseDatetime(r.get("创建时间")));
                    t.setCreateBy(userNameToId.get(r.get("创建人")));
                    t.setUpdateTime(t.getEndTime() != null ? t.getEndTime() : t.getCreateTime());
                    t.setUpdateBy(t.getCreateBy());
                    processTaskMapper.insert(t);
                    success.incrementAndGet();
                } catch (Exception e) {
                    errors.add("process_task第" + (i + 2) + "行: " + e.getMessage());
                }
            });
        }

        // 2.5 自动生成 plan_item_step（计划详情中的工序流程数据）
        for (Map.Entry<String, Long> we : woNoToId.entrySet()) {
            try {
                BizWorkOrder wo = workOrderMapper.selectById(we.getValue());
                if (wo == null || wo.getPlanItemId() == null) continue;

                // 获取该工单的所有工序任务
                List<BizWoProcessTask> woTasks = processTaskMapper.selectList(
                        new LambdaQueryWrapper<BizWoProcessTask>()
                                .eq(BizWoProcessTask::getWorkOrderId, wo.getId())
                                .eq(BizWoProcessTask::getDeleted, 0)
                                .orderByAsc(BizWoProcessTask::getStepNo));

                // 获取产品的工艺路线步骤（用于stepId），没有专属路线时回退到通用路线
                BizProcessRoute route = routeMapper.selectOne(
                        new LambdaQueryWrapper<BizProcessRoute>()
                                .eq(BizProcessRoute::getProductId, wo.getProductId())
                                .eq(BizProcessRoute::getStatus, "active")
                                .eq(BizProcessRoute::getDeleted, 0)
                                .last("LIMIT 1"));
                if (route == null) {
                    route = routeMapper.selectOne(
                            new LambdaQueryWrapper<BizProcessRoute>()
                                    .isNull(BizProcessRoute::getProductId)
                                    .eq(BizProcessRoute::getStatus, "active")
                                    .eq(BizProcessRoute::getDeleted, 0)
                                    .last("LIMIT 1"));
                }
                Map<Integer, Long> stepNoToRouteStepId = new HashMap<>();
                if (route != null) {
                    routeStepMapper.selectList(new LambdaQueryWrapper<BizProcessRouteStep>()
                            .eq(BizProcessRouteStep::getRouteId, route.getId())
                            .eq(BizProcessRouteStep::getDeleted, 0))
                            .forEach(s -> stepNoToRouteStepId.put(s.getStepNo(), s.getId()));
                }

                // 一次性查出该 plan_item 已存在的 (stepId) 集合, 跳过重复插入
                // (多个工单共享同一个 plan_item, 同一个 (planItemId, stepId) 只能存在一条)
                Set<Long> existingStepIds = planItemStepMapper.selectList(
                        new LambdaQueryWrapper<com.aimes.modules.mrp.entity.BizProductionPlanItemStep>()
                                .eq(com.aimes.modules.mrp.entity.BizProductionPlanItemStep::getPlanItemId, wo.getPlanItemId()))
                        .stream().map(com.aimes.modules.mrp.entity.BizProductionPlanItemStep::getStepId)
                        .filter(Objects::nonNull).collect(Collectors.toSet());

                for (BizWoProcessTask pt : woTasks) {
                    Long stepId = stepNoToRouteStepId.getOrDefault(pt.getStepNo(), 0L);
                    if (existingStepIds.contains(stepId)) continue; // 已有就跳过, 不重复插入
                    com.aimes.modules.mrp.entity.BizProductionPlanItemStep pis =
                            new com.aimes.modules.mrp.entity.BizProductionPlanItemStep();
                    pis.setPlanItemId(wo.getPlanItemId());
                    pis.setStepId(stepId);
                    pis.setStepNo(pt.getStepNo());
                    pis.setStepName(pt.getStepName());
                    pis.setTeamId(pt.getAssignedTeamId());
                    pis.setTeamName(pt.getAssignedTeamName());
                    pis.setAssigneeId(pt.getAssignedUserId());
                    pis.setAssigneeName(pt.getAssignedUserName());
                    pis.setNeedQc(pt.getQcTemplateId() != null);
                    pis.setStandardTime(pt.getStandardTime());
                    pis.setPiecePrice(pt.getPiecePrice());
                    pis.setHandleType("self");
                    pis.setPlanStartTime(pt.getStartTime());
                    pis.setPlanEndTime(pt.getEndTime());
                    pis.setQcTemplateId(pt.getQcTemplateId());
                    pis.setRouteId(route != null ? route.getId() : null);
                    pis.setCreateTime(wo.getCreateTime());
                    pis.setCreateBy(wo.getCreateBy());
                    pis.setUpdateTime(wo.getCreateTime());
                    pis.setUpdateBy(wo.getCreateBy());
                    try {
                        planItemStepMapper.insert(pis);
                        existingStepIds.add(stepId); // 同一线程内并发兜底
                    } catch (org.springframework.dao.DuplicateKeyException dup) {
                        // 多线程并发场景下两个 worker 同时拿到 existingStepIds 副本时可能撞键 — 忽略即可
                    }
                }
            } catch (Exception e) {
                log.warn("生成plan_item_step失败: {}", e.getMessage());
            }
        }

        // 3. 导入BOM快照
        List<Map<String, String>> bomAllRows = parseAllParts(bomFiles, BOM_SNAP_HEADERS);
        if (!bomAllRows.isEmpty()) {
            List<Map<String, String>> rows = bomAllRows;
            java.util.stream.IntStream.range(0, rows.size()).parallel().forEach(i -> {
                Map<String, String> r = rows.get(i);
                try {
                    Long woId = woNoToId.get(r.get("工单编号(关联)"));
                    if (woId == null) { errors.add("bom_snapshot第" + (i + 2) + "行: 工单不存在"); return; }

                    Long matId = matNoToId.get(r.get("物料编号"));
                    if (matId == null) { errors.add("bom_snapshot第" + (i + 2) + "行: 物料不存在 " + r.get("物料编号")); return; }

                    BizWoBomSnapshot s = new BizWoBomSnapshot();
                    s.setWorkOrderId(woId);
                    s.setMaterialId(matId);
                    s.setRequiredQty(parseBD(r.get("需求数量")));
                    s.setIssuedQty(parseBD(r.get("已发料数量")));
                    s.setIssuedTaskQty(parseBD(r.get("已发料任务数")));
                    s.setReturnedQty(BigDecimal.ZERO);
                    s.setReturnedTaskQty(BigDecimal.ZERO);
                    s.setCreateTime(parseDatetime(r.get("创建时间")));
                    s.setCreateBy(userNameToId.get(r.get("创建人")));
                    s.setUpdateTime(s.getCreateTime());
                    s.setUpdateBy(s.getCreateBy());
                    bomSnapshotMapper.insert(s);
                    success.incrementAndGet();
                } catch (Exception e) {
                    errors.add("bom_snapshot第" + (i + 2) + "行: " + e.getMessage());
                }
            });
        }

        // 4. 导入库存任务
        Map<String, Long> invTaskNoToId = new java.util.concurrent.ConcurrentHashMap<>();
        if (invTaskFile != null && !invTaskFile.isEmpty()) {
            List<Map<String, String>> rows = ExcelUtil.parseImport(invTaskFile, INV_TASK_HEADERS);
            java.util.stream.IntStream.range(0, rows.size()).parallel().forEach(i -> {
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
                    // 反查 source_id：work_order 类型 → relatedNo 是 wo_no
                    if ("work_order".equals(srcType) && relatedNo != null && !relatedNo.isEmpty()) {
                        Long woId2 = woNoToId.get(relatedNo);
                        if (woId2 != null) task.setSourceId(woId2);
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
                    success.incrementAndGet();
                } catch (Exception e) {
                    errors.add("inv_task第" + (i + 2) + "行: " + e.getMessage());
                }
            });
        }

        // 5. 导入库存任务明细
        List<Map<String, String>> invTaskItemAllRows = parseAllParts(invTaskItemFiles, INV_TASK_ITEM_HEADERS);
        if (!invTaskItemAllRows.isEmpty()) {
            List<Map<String, String>> rows = invTaskItemAllRows;
            java.util.stream.IntStream.range(0, rows.size()).parallel().forEach(i -> {
                Map<String, String> r = rows.get(i);
                try {
                    Long taskId = invTaskNoToId.get(r.get("任务编号(关联)"));
                    if (taskId == null) { errors.add("inv_task_item第" + (i + 2) + "行: 任务不存在"); return; }

                    BizInvTaskItem item = new BizInvTaskItem();
                    item.setTaskId(taskId);
                    String matNo = r.get("物料编号");
                    item.setMaterialId(matNoToId.get(matNo));
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
                    success.incrementAndGet();
                } catch (Exception e) {
                    errors.add("inv_task_item第" + (i + 2) + "行: " + e.getMessage());
                }
            });
        }

        // 6. 导入库存流水
        List<Map<String, String>> invTxAllRows = parseAllParts(invTxFiles, INV_TRANSACTION_HEADERS);
        if (!invTxAllRows.isEmpty()) {
            List<Map<String, String>> rows = invTxAllRows;
            java.util.stream.IntStream.range(0, rows.size()).parallel().forEach(i -> {
                Map<String, String> r = rows.get(i);
                try {
                    BizInvTransaction tx = new BizInvTransaction();
                    tx.setMaterialId(matNoToId.get(r.get("物料编号")));
                    String whStr = r.get("仓库ID");
                    if (whStr != null && !whStr.isEmpty()) {
                        try { tx.setWarehouseId(Long.valueOf(whStr.trim())); } catch (NumberFormatException ignored) {}
                    }
                    tx.setTransactionType(r.get("交易类型"));
                    tx.setQuantity(parseBD(r.get("数量")));
                    tx.setRelatedOrderType(r.get("关联单据类型"));
                    // relatedOrderId: 通过关联单据号查找工单ID
                    String relOrderNo = r.get("关联单据号");
                    if (relOrderNo != null && !relOrderNo.isEmpty()) {
                        Long relId = woNoToId.get(relOrderNo);
                        tx.setRelatedOrderId(relId);
                    }
                    tx.setCreateTime(parseDatetime(r.get("创建时间")));
                    tx.setCreateBy(userNameToId.values().stream().findFirst().orElse(null));
                    tx.setUpdateTime(tx.getCreateTime());
                    tx.setUpdateBy(tx.getCreateBy());
                    invTransactionMapper.insert(tx);
                    success.incrementAndGet();
                } catch (Exception e) {
                    errors.add("inv_transaction第" + (i + 2) + "行: " + e.getMessage());
                }
            });
        }

        // 7. 回写 bom_snapshot.issuedQty / issuedTaskQty
        // 模拟数据走的是直接 insert 路径，没经过 InventoryService 的领料逻辑，
        // 所以 bom_snapshot 的 issuedQty 一直是 0。这里按真实 inv_task_item 重算。
        try {
            // 找所有 production_out 类型的任务及其明细
            List<BizInvTask> outTasks = invTaskMapper.selectList(
                    new LambdaQueryWrapper<BizInvTask>()
                            .eq(BizInvTask::getInvType, "production_out")
                            .eq(BizInvTask::getSourceType, "work_order")
                            .isNotNull(BizInvTask::getSourceId)
                            .eq(BizInvTask::getDeleted, 0));
            // (woId, materialId) → sum of processed_qty
            Map<String, BigDecimal> issuedAgg = new HashMap<>();
            Map<String, BigDecimal> taskAgg = new HashMap<>();
            for (BizInvTask t : outTasks) {
                List<BizInvTaskItem> items = invTaskItemMapper.selectList(
                        new LambdaQueryWrapper<BizInvTaskItem>().eq(BizInvTaskItem::getTaskId, t.getId()));
                for (BizInvTaskItem it : items) {
                    if (it.getMaterialId() == null) continue;
                    String key = t.getSourceId() + "_" + it.getMaterialId();
                    BigDecimal proc = it.getProcessedQty() != null ? it.getProcessedQty() : BigDecimal.ZERO;
                    BigDecimal qty = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
                    issuedAgg.merge(key, proc, BigDecimal::add);
                    taskAgg.merge(key, qty, BigDecimal::add);
                }
            }
            // 应用到 bom_snapshot
            for (Map.Entry<String, BigDecimal> e : issuedAgg.entrySet()) {
                String[] parts = e.getKey().split("_");
                Long woId = Long.valueOf(parts[0]);
                Long matId = Long.valueOf(parts[1]);
                BizWoBomSnapshot snap = bomSnapshotMapper.selectOne(
                        new LambdaQueryWrapper<BizWoBomSnapshot>()
                                .eq(BizWoBomSnapshot::getWorkOrderId, woId)
                                .eq(BizWoBomSnapshot::getMaterialId, matId)
                                .eq(BizWoBomSnapshot::getDeleted, 0)
                                .last("LIMIT 1"));
                if (snap != null) {
                    snap.setIssuedQty(e.getValue());
                    snap.setIssuedTaskQty(taskAgg.getOrDefault(e.getKey(), BigDecimal.ZERO));
                    bomSnapshotMapper.updateById(snap);
                }
            }
            log.info("BOM 快照领料数回写完成: 处理 {} 个 (工单,物料) 组合", issuedAgg.size());
        } catch (Exception e) {
            log.error("回写 bom_snapshot 领料数失败: {}", e.getMessage(), e);
            errors.add("回写 bom_snapshot 领料数失败: " + e.getMessage());
        }

        return Map.of("success", success.get(), "errors", errors);
    }

    // ===== 辅助方法 =====

    // generate() 内一次性预加载, 给 getProcessSteps 复用, 避免并行循环里反复查全表
    private volatile Map<Long, String> cachedUserIdToName;
    private volatile Map<Long, String> cachedTeamIdToName;
    private volatile Map<Long, String> cachedQcTemplateNameMap;
    // 同一产品的 steps 只算一次
    private final java.util.concurrent.ConcurrentHashMap<Long, List<StepInfo>> cachedStepsByProduct = new java.util.concurrent.ConcurrentHashMap<>();

    private List<StepInfo> getProcessSteps(Long productId, Map<Long, BizMaterial> matMap) {
        if (productId == null) return List.of();
        List<StepInfo> cached = cachedStepsByProduct.get(productId);
        if (cached != null) return cached;
        List<StepInfo> result = computeProcessSteps(productId, matMap);
        cachedStepsByProduct.put(productId, result);
        return result;
    }

    private List<StepInfo> computeProcessSteps(Long productId, Map<Long, BizMaterial> matMap) {
        // 1) 优先查找产品专属的工艺路线（必须有工序步骤才算有效）
        BizProcessRoute route = null;
        List<BizProcessRoute> ownRoutes = routeMapper.selectList(new LambdaQueryWrapper<BizProcessRoute>()
                .eq(BizProcessRoute::getProductId, productId)
                .eq(BizProcessRoute::getStatus, "active")
                .eq(BizProcessRoute::getDeleted, 0));
        log.info("[工艺路线选择] 产品ID={} 查到专属路线 {} 条", productId, ownRoutes.size());
        for (BizProcessRoute r : ownRoutes) {
            Long stepCount = routeStepMapper.selectCount(new LambdaQueryWrapper<BizProcessRouteStep>()
                    .eq(BizProcessRouteStep::getRouteId, r.getId())
                    .eq(BizProcessRouteStep::getDeleted, 0));
            log.info("[工艺路线选择]   专属路线 id={} name={} 工序数={}", r.getId(), r.getRouteName(), stepCount);
            if (stepCount != null && stepCount > 0) { route = r; break; }
        }
        // 2) 没有专属路线（或专属路线工序为空）→ 回退到通用工艺路线（product_id 为 null，必须有工序步骤）
        if (route == null) {
            List<BizProcessRoute> genericRoutes = routeMapper.selectList(new LambdaQueryWrapper<BizProcessRoute>()
                    .isNull(BizProcessRoute::getProductId)
                    .eq(BizProcessRoute::getStatus, "active")
                    .eq(BizProcessRoute::getDeleted, 0));
            log.info("[工艺路线选择] 产品ID={} 回落查通用路线 {} 条", productId, genericRoutes.size());
            for (BizProcessRoute r : genericRoutes) {
                Long stepCount = routeStepMapper.selectCount(new LambdaQueryWrapper<BizProcessRouteStep>()
                        .eq(BizProcessRouteStep::getRouteId, r.getId())
                        .eq(BizProcessRouteStep::getDeleted, 0));
                log.info("[工艺路线选择]   通用路线 id={} name={} 工序数={}", r.getId(), r.getRouteName(), stepCount);
                if (stepCount != null && stepCount > 0) { route = r; break; }
            }
        }
        // 3) 都没有 → 硬编码兜底一条假工序
        if (route == null) {
            log.warn("[工艺路线选择] 产品ID={} 无可用路线,使用硬编码兜底(生产加工)", productId);
            return List.of(new StepInfo(1, 1, "生产加工", 60, null, null, "", "", ""));
        }
        log.info("[工艺路线选择] 产品ID={} 最终使用路线 id={} name={}", productId, route.getId(), route.getRouteName());

        List<BizProcessRouteStep> steps = routeStepMapper.selectList(
                new LambdaQueryWrapper<BizProcessRouteStep>()
                        .eq(BizProcessRouteStep::getRouteId, route.getId())
                        .eq(BizProcessRouteStep::getDeleted, 0)
                        .orderByAsc(BizProcessRouteStep::getStepNo));

        Map<Long, BizProcess> procMap = new HashMap<>();
        List<Long> pids = steps.stream().map(BizProcessRouteStep::getProcessId).filter(Objects::nonNull).distinct().toList();
        if (!pids.isEmpty()) {
            processMapper.selectList(new LambdaQueryWrapper<BizProcess>().in(BizProcess::getId, pids)
                    .eq(BizProcess::getDeleted, 0)).forEach(p -> procMap.put(p.getId(), p));
        }

        // 用 generate() 顶部一次性预加载好的映射, 避免并行调用里反复查全表把连接池打爆
        Map<Long, String> userIdToName = cachedUserIdToName != null ? cachedUserIdToName : Map.of();
        Map<Long, String> teamIdToName = cachedTeamIdToName != null ? cachedTeamIdToName : Map.of();
        Map<Long, String> qcTemplateNameMap = cachedQcTemplateNameMap != null ? cachedQcTemplateNameMap : Map.of();

        return steps.stream().map(s -> {
            BizProcess proc = procMap.get(s.getProcessId());
            String name = proc != null ? proc.getProcessName() : "工序" + s.getStepNo();
            Integer time = s.getStandardTime() != null ? s.getStandardTime() : (proc != null ? proc.getStandardTime() : 60);
            BigDecimal price = s.getPiecePrice() != null ? s.getPiecePrice() : (proc != null ? proc.getPiecePrice() : null);
            Long qcTplId = s.getQcTemplateId() != null ? s.getQcTemplateId() : (proc != null ? proc.getQcTemplateId() : null);

            // 从工序库读取默认绑定信息
            String bindType = "";
            String bindName = "";
            if (proc != null && proc.getBindType() != null && !"none".equals(proc.getBindType())) {
                if ("team".equals(proc.getBindType())) {
                    bindType = "team";
                    // bindIds是JSON数组如[1]，取第一个班组ID
                    Long teamId = proc.getTeamId();
                    if (teamId != null) {
                        bindName = teamIdToName.getOrDefault(teamId, "");
                    } else if (proc.getBindIds() != null && !proc.getBindIds().isEmpty()) {
                        try {
                            String ids = proc.getBindIds().replaceAll("[\\[\\]\\s]", "");
                            if (!ids.isEmpty()) bindName = teamIdToName.getOrDefault(Long.valueOf(ids.split(",")[0]), "");
                        } catch (Exception ignored) {}
                    }
                } else if ("employee".equals(proc.getBindType())) {
                    bindType = "person";
                    if (proc.getBindIds() != null && !proc.getBindIds().isEmpty()) {
                        try {
                            String ids = proc.getBindIds().replaceAll("[\\[\\]\\s]", "");
                            if (!ids.isEmpty()) bindName = userIdToName.getOrDefault(Long.valueOf(ids.split(",")[0]), "");
                        } catch (Exception ignored) {}
                    }
                }
            }
            // 质检模板名称（从预加载的map获取）
            String qcTplName = qcTplId != null ? qcTemplateNameMap.getOrDefault(qcTplId, "") : "";
            return new StepInfo(s.getStepNo(), s.getStepGroup() != null ? s.getStepGroup() : 1, name, time, price, qcTplId, qcTplName, bindType, bindName);
        }).toList();
    }

    private List<BizBomItem> getBomItems(Long productId) {
        BizBomVersion ver = bomVersionMapper.selectOne(new LambdaQueryWrapper<BizBomVersion>()
                .eq(BizBomVersion::getProductId, productId).eq(BizBomVersion::getStatus, "active")
                .eq(BizBomVersion::getDeleted, 0).last("LIMIT 1"));
        if (ver == null) return List.of();
        return bomItemMapper.selectList(new LambdaQueryWrapper<BizBomItem>()
                .eq(BizBomItem::getBomVersionId, ver.getId()).eq(BizBomItem::getDeleted, 0)
                .isNotNull(BizBomItem::getMaterialId).orderByAsc(BizBomItem::getSortOrder));
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

    private Map<String, Long> loadMaterialNoToIdMap() {
        return materialMapper.selectList(new LambdaQueryWrapper<BizMaterial>().eq(BizMaterial::getDeleted, 0))
                .stream().filter(m -> m.getMaterialNo() != null)
                .collect(Collectors.toMap(BizMaterial::getMaterialNo, BizMaterial::getId, (a, b) -> a));
    }

    private Map<String, Long> loadTeamNameToIdMap() {
        return teamMapper.selectList(new LambdaQueryWrapper<com.aimes.modules.team.entity.BizTeam>()
                .eq(com.aimes.modules.team.entity.BizTeam::getDeleted, 0))
                .stream().filter(t -> t.getTeamName() != null)
                .collect(Collectors.toMap(com.aimes.modules.team.entity.BizTeam::getTeamName,
                        com.aimes.modules.team.entity.BizTeam::getId, (a, b) -> a));
    }

    private record StepInfo(int stepNo, int stepGroup, String name, Integer stdTime, BigDecimal price, Long qcTemplateId,
                                String qcTemplateName, String bindType, String bindName) {}

    /** 解析多个分片 xlsx, 合并所有数据行 */
    private static List<Map<String, String>> parseAllParts(MultipartFile[] files, List<String> headers) {
        if (files == null || files.length == 0) return new ArrayList<>();
        List<Map<String, String>> all = new ArrayList<>();
        for (MultipartFile f : files) {
            if (f != null && !f.isEmpty()) all.addAll(ExcelUtil.parseImport(f, headers));
        }
        return all;
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return LocalDate.parse(s.trim(), DateTimeFormatter.ISO_LOCAL_DATE); }
        catch (Exception e) { return LocalDate.parse(s.trim(), DateTimeFormatter.ofPattern("yyyy/MM/dd")); }
    }

    private static LocalDateTime parseDatetime(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return LocalDateTime.parse(s.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")); }
        catch (Exception e) { LocalDate d = parseDate(s); return d != null ? d.atStartOfDay() : null; }
    }

    private static BigDecimal parseBD(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return new BigDecimal(s.trim()); } catch (Exception e) { return null; }
    }

    private static Integer parseInt(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return Integer.parseInt(s.trim().split("\\.")[0]); } catch (Exception e) { return null; }
    }
}
