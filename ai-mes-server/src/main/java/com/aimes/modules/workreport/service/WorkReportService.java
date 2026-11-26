package com.aimes.modules.workreport.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.PageResult;
import com.aimes.common.utils.CodeUtil;
import com.aimes.common.utils.SecurityUtil;
import com.aimes.modules.finance.entity.BizWoCost;
import com.aimes.modules.finance.service.BizFinanceService;
import com.aimes.modules.inventory.service.BizInvTaskService;
import com.aimes.modules.inventory.service.InventoryService;
import com.aimes.modules.master.entity.BizMaterial;
import com.aimes.modules.master.mapper.BizMaterialMapper;
import com.aimes.modules.mrp.service.BizProductionPlanService;
import com.aimes.modules.outsource.service.OutsourceChainService;
import com.aimes.modules.production.entity.BizWoBomSnapshot;
import com.aimes.modules.production.entity.BizWoProcessTask;
import com.aimes.modules.production.entity.BizWorkOrder;
import com.aimes.modules.inventory.entity.BizInvRequest;
import com.aimes.modules.inventory.entity.BizInvTask;
import com.aimes.modules.inventory.entity.BizInvTaskItem;
import com.aimes.modules.inventory.mapper.BizInvRequestMapper;
import com.aimes.modules.inventory.mapper.BizInvTaskItemMapper;
import com.aimes.modules.inventory.mapper.BizInvTaskMapper;
import com.aimes.modules.production.mapper.BizWoBomSnapshotMapper;
import com.aimes.modules.production.mapper.BizWoProcessTaskMapper;
import com.aimes.modules.production.mapper.BizWorkOrderMapper;
import com.aimes.modules.quality.entity.BizDefectRecord;
import com.aimes.modules.quality.entity.BizQcTask;
import com.aimes.modules.quality.mapper.BizDefectRecordMapper;
import com.aimes.modules.quality.service.BizQcTaskService;
import com.aimes.modules.system.entity.SysUser;
import com.aimes.modules.system.mapper.SysUserMapper;
import com.aimes.modules.system.service.SysNotificationService;
import com.aimes.modules.system.service.SysBizConfigService;
import com.aimes.modules.order.service.BizSalesOrderService;
import com.aimes.modules.team.entity.BizTeam;
import com.aimes.modules.team.entity.BizTeamMember;
import com.aimes.modules.team.mapper.BizTeamMapper;
import com.aimes.modules.team.mapper.BizTeamMemberMapper;
import com.aimes.modules.workreport.entity.BizTeamReportDetail;
import com.aimes.modules.workreport.entity.BizTeamWorkReport;
import com.aimes.modules.workreport.entity.BizWageRecord;
import com.aimes.modules.workreport.entity.BizWorkReport;
import com.aimes.modules.workreport.mapper.BizTeamReportDetailMapper;
import com.aimes.modules.workreport.mapper.BizTeamWorkReportMapper;
import com.aimes.modules.workreport.mapper.BizWageRecordMapper;
import com.aimes.modules.workreport.mapper.BizWorkReportMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 报工服务
 * 核心业务联动：报工提交 → 更新工序任务进度 → 更新工单完工数 → 生成计件工资记录
 * 报工状态: submitted(已提交) → approved(已通过) / rejected(已驳回)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkReportService extends ServiceImpl<BizWorkReportMapper, BizWorkReport> {

    private final BizTeamMapper teamMapper;
    private final BizTeamMemberMapper teamMemberMapper;
    private final BizWageRecordMapper wageRecordMapper;
    private final BizTeamWorkReportMapper teamWorkReportMapper;
    private final BizTeamReportDetailMapper teamReportDetailMapper;
    private final BizWoProcessTaskMapper processTaskMapper;
    private final BizWorkOrderMapper workOrderMapper;
    private final BizInvRequestMapper invRequestMapper;
    private final BizInvTaskMapper invTaskMapper;
    private final BizInvTaskItemMapper invTaskItemMapper;
    private final BizFinanceService financeService;
    private final InventoryService inventoryService;
    private final BizMaterialMapper materialMapper;
    private final BizDefectRecordMapper defectRecordMapper;
    private final BizQcTaskService qcTaskService;
    private final SysNotificationService notificationService;
    private final SysUserMapper userMapper;
    private final BizProductionPlanService productionPlanService;
    private final BizSalesOrderService salesOrderService;
    private final OutsourceChainService outsourceChainService;
    private final com.aimes.modules.production.service.SemiStockService semiStockService;
    private final BizWoBomSnapshotMapper bomSnapshotMapper;
    private final BizInvTaskService invTaskService;
    private final SysBizConfigService bizConfigService;

    /**
     * 获取我的任务列表
     */
    /**
     * 查询当前用户的工序任务（"我的任务"）
     * 规则：
     * 1. 直接指派给该用户（assignType='person' AND assignedUserId=userId）
     * 2. 指派给该用户所在班组（assignType='team' AND assignedTeamId IN 用户班组）
     * 只有工序任务中有该用户的工单才会出现，不符合的工单不展示。
     *
     * @param userId 当前用户ID
     * @param status 工序任务状态过滤（可选）
     */
    public List<Map<String, Object>> getMyTasks(Long userId, String status) {
        // Step 1：查找用户所属班组
        List<Long> userTeamIds = teamMemberMapper.selectList(
                        new LambdaQueryWrapper<BizTeamMember>()
                                .eq(BizTeamMember::getUserId, userId)
                                .select(BizTeamMember::getTeamId))
                .stream().map(BizTeamMember::getTeamId).filter(Objects::nonNull).distinct()
                .collect(Collectors.toList());

        // Step 2：查询符合条件的工序任务
        final List<Long> teamIds = userTeamIds;
        LambdaQueryWrapper<BizWoProcessTask> taskWrapper = new LambdaQueryWrapper<BizWoProcessTask>()
                .and(w -> {
                    w.eq(BizWoProcessTask::getAssignType, "person")
                            .eq(BizWoProcessTask::getAssignedUserId, userId);
                    if (!teamIds.isEmpty()) {
                        w.or(tw -> tw.eq(BizWoProcessTask::getAssignType, "team")
                                .in(BizWoProcessTask::getAssignedTeamId, teamIds));
                    }
                })
                .eq(StringUtils.hasText(status), BizWoProcessTask::getStatus, status)
                .orderByAsc(BizWoProcessTask::getWorkOrderId)
                .orderByAsc(BizWoProcessTask::getStepNo);

        List<BizWoProcessTask> tasks = processTaskMapper.selectList(taskWrapper);
        // 班组允许报工成员白名单过滤
        tasks = tasks.stream().filter(t -> isTaskVisibleToUser(t, userId)).collect(Collectors.toList());
        if (tasks.isEmpty()) return List.of();

        // Step 3：批量加载工单信息
        Set<Long> woIds = tasks.stream().map(BizWoProcessTask::getWorkOrderId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        List<BizWorkOrder> workOrders = workOrderMapper.selectBatchIds(woIds);
        Map<Long, BizWorkOrder> woMap = workOrders.stream()
                .collect(Collectors.toMap(BizWorkOrder::getId, wo -> wo, (a, b) -> a));

        // Step 4：批量加载产品名称
        Set<Long> productIds = workOrders.stream().map(BizWorkOrder::getProductId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> productNameMap = productIds.isEmpty() ? Map.of() :
                materialMapper.selectBatchIds(productIds).stream()
                        .collect(Collectors.toMap(BizMaterial::getId, BizMaterial::getMaterialName, (a, b) -> a));

        // Step 5：组装返回数据
        return tasks.stream().map(task -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", task.getId());
            map.put("workOrderId", task.getWorkOrderId());
            map.put("stepName", task.getStepName());
            map.put("stepNo", task.getStepNo());
            map.put("status", task.getStatus());
            map.put("planQty", task.getPlanQty());
            map.put("reportedQty", task.getReportedQty());
            map.put("qualifiedQty", task.getQualifiedQty());
            map.put("defectQty", task.getDefectQty());
            map.put("qcTemplateId", task.getQcTemplateId());
            map.put("assignType", task.getAssignType());
            map.put("assigneeName", "team".equals(task.getAssignType())
                    ? task.getAssignedTeamName() : task.getAssignedUserName());

            BizWorkOrder wo = woMap.get(task.getWorkOrderId());
            if (wo != null) {
                map.put("woNo", wo.getWoNo());
                map.put("workOrderNo", wo.getWoNo());
                map.put("workOrderStatus", wo.getStatus());
                map.put("deliveryDate", wo.getDeliveryDate());
                map.put("completedQty", wo.getCompletedQty());
                map.put("productName", productNameMap.getOrDefault(wo.getProductId(), ""));
            }
            return map;
        }).collect(Collectors.toList());
    }

    /**
     * 工位机报工：根据员工账号查询待报工任务
     * 规则：班组指派任务对该班组的所有成员可见（任何成员都可以报自己那部分）
     *
     * @param username 员工登录账号（刷卡解析）
     * @return {userId, username, realName, tasks: [...]}
     */
    public Map<String, Object> getWorkstationTasks(String username) {
        // 1. 根据 username 查找用户
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, username)
                        .eq(SysUser::getDeleted, 0));
        if (user == null) {
            throw new BusinessException("未找到该员工: " + username);
        }
        Long userId = user.getId();

        // 2. 查询该用户所属的所有班组（作为成员，不限于组长）
        List<Long> memberTeamIds = teamMemberMapper.selectList(
                        new LambdaQueryWrapper<BizTeamMember>()
                                .eq(BizTeamMember::getUserId, userId)
                                .select(BizTeamMember::getTeamId))
                .stream().map(BizTeamMember::getTeamId).filter(Objects::nonNull).distinct()
                .collect(Collectors.toList());

        // 3. 构建查询条件：个人任务 + 所属班组任务
        LambdaQueryWrapper<BizWoProcessTask> taskWrapper = new LambdaQueryWrapper<BizWoProcessTask>()
                .and(w -> {
                    w.eq(BizWoProcessTask::getAssignType, "person")
                            .eq(BizWoProcessTask::getAssignedUserId, userId);
                    if (!memberTeamIds.isEmpty()) {
                        w.or(tw -> tw.eq(BizWoProcessTask::getAssignType, "team")
                                .in(BizWoProcessTask::getAssignedTeamId, memberTeamIds));
                    }
                })
                .in(BizWoProcessTask::getStatus, List.of("ready", "in_progress", "pending_qc"))
                .orderByAsc(BizWoProcessTask::getWorkOrderId)
                .orderByAsc(BizWoProcessTask::getStepNo);

        List<BizWoProcessTask> tasks = processTaskMapper.selectList(taskWrapper);
        // 班组允许报工成员白名单过滤
        tasks = tasks.stream().filter(t -> isTaskVisibleToUser(t, userId)).collect(Collectors.toList());

        // 4. 批量加载工单和产品信息（复用 getMyTasks 的数据组装逻辑）
        Map<Long, BizWorkOrder> woMap = Map.of();
        Map<Long, String> productNameMap = Map.of();
        Map<Long, String> materialNoMap = Map.of();
        if (!tasks.isEmpty()) {
            Set<Long> woIds = tasks.stream().map(BizWoProcessTask::getWorkOrderId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            List<BizWorkOrder> workOrders = workOrderMapper.selectBatchIds(woIds);
            woMap = workOrders.stream()
                    .collect(Collectors.toMap(BizWorkOrder::getId, wo -> wo, (a, b) -> a));

            Set<Long> productIds = workOrders.stream().map(BizWorkOrder::getProductId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            productNameMap = productIds.isEmpty() ? Map.of() :
                    materialMapper.selectBatchIds(productIds).stream()
                            .collect(Collectors.toMap(BizMaterial::getId, BizMaterial::getMaterialName, (a, b) -> a));
            materialNoMap = productIds.isEmpty() ? Map.of() :
                    materialMapper.selectBatchIds(productIds).stream()
                            .collect(Collectors.toMap(BizMaterial::getId, BizMaterial::getMaterialNo, (a, b) -> a));
        }

        // 5. 组装任务列表
        final Map<Long, BizWorkOrder> finalWoMap = woMap;
        final Map<Long, String> finalProductNameMap = productNameMap;
        final Map<Long, String> finalMaterialNoMap = materialNoMap;
        List<Map<String, Object>> taskList = tasks.stream().map(task -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", task.getId());
            map.put("workOrderId", task.getWorkOrderId());
            map.put("stepName", task.getStepName());
            map.put("stepNo", task.getStepNo());
            map.put("stepGroup", task.getStepGroup());
            map.put("status", task.getStatus());
            map.put("planQty", task.getPlanQty());
            map.put("reportedQty", task.getReportedQty());
            map.put("qualifiedQty", task.getQualifiedQty());
            map.put("defectQty", task.getDefectQty());
            map.put("qcTemplateId", task.getQcTemplateId());
            map.put("assignType", task.getAssignType());
            map.put("assignedTeamId", task.getAssignedTeamId());
            map.put("assignedTeamName", task.getAssignedTeamName());
            map.put("piecePrice", task.getPiecePrice());
            map.put("wageRatio", task.getWageRatio());
            map.put("assigneeName", "team".equals(task.getAssignType())
                    ? task.getAssignedTeamName() : task.getAssignedUserName());

            BizWorkOrder wo = finalWoMap.get(task.getWorkOrderId());
            if (wo != null) {
                map.put("woNo", wo.getWoNo());
                map.put("workOrderStatus", wo.getStatus());
                map.put("deliveryDate", wo.getDeliveryDate());
                map.put("completedQty", wo.getCompletedQty());
                map.put("planQtyWo", wo.getPlanQty());
                map.put("productId", wo.getProductId());
                map.put("productName", finalProductNameMap.getOrDefault(wo.getProductId(), ""));
                map.put("materialNo", finalMaterialNoMap.getOrDefault(wo.getProductId(), ""));
            }
            return map;
        }).collect(Collectors.toList());

        // 6. 返回结果
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("username", user.getUsername());
        result.put("realName", user.getRealName());
        result.put("tasks", taskList);
        return result;
    }

    /**
     * 提交个人报工
     * 联动操作（事务内）:
     * 1. 保存报工记录
     * 2. 更新工序任务: qualified_qty + 本次合格数, defect_qty + 本次不良数
     * 3. 更新工单进度: completed_qty = 末工序的qualified_qty
     * 4. 计算计件工资: 合格数 × 计件单价 → 生成工资记录
     * 5. 前道工序完成 → 后道工序变为ready
     *
     * @param report 报工记录
     */
    @Transactional(rollbackFor = Exception.class)
    public void submitPersonalReport(BizWorkReport report) {
        // 0a. 领料申请校验: 工单存在未通过(还没生成出库任务)的领料申请 → 阻断报工
        // 没领料申请的工单不强制(可能没BOM也不需要领料)
        checkPendingMaterialRequest(report.getWorkOrderId());

        // 0b. 半成品领料校验:
        //   (1) 上道工序入过半成品库 → 本道必须先领料才能报工
        //   (2) 本次报工后累计不能超过已领料数 (防止下道一次领 1 件却报 16 件)
        if (report.getTaskId() != null) {
            String blocked = semiStockService.checkReportBlocked(report.getTaskId());
            if (blocked != null) throw new BusinessException(blocked);
            semiStockService.checkReportWithinIssued(report.getTaskId(),
                    report.getQualifiedQty(), report.getDefectQty());
        }

        // 0. 前置校验: 首道工序检查BOM领料上限，非首道检查上道工序合格数上限
        // 若配置"允许超工单数量报工"=true，则跳过上限检查
        boolean allowOverQty = bizConfigService.getBoolValue("workreport.allow_over_qty", false);
        if (!allowOverQty && report.getTaskId() != null && report.getWorkOrderId() != null) {
            BizWoProcessTask reportTask = processTaskMapper.selectById(report.getTaskId());
            if (reportTask != null) {
                checkReportLimit(report.getWorkOrderId(), reportTask,
                        report.getQualifiedQty(), report.getDefectQty());
            }
        }

        // 1. 保存报工记录
        report.setReportNo(CodeUtil.generate("WR"));
        // 保留客户端传来的 reportType（如班组报工），若未指定则默认个人
        if (!StringUtils.hasText(report.getReportType())) {
            report.setReportType("personal");
        }
        // 个人报工: 报工人强制取计划里填的工序指派人(task.assignedUserId), 不看登录用户
        // 业务诉求: 计划已经定好这道工序由谁报工, 工站电脑可能多人共用, 不应该由登录账号决定
        // 没有指派人时 fallback 到前端传值或当前登录用户(createBy 走默认)
        if ("personal".equals(report.getReportType()) && report.getTaskId() != null) {
            BizWoProcessTask t = processTaskMapper.selectById(report.getTaskId());
            if (t != null && t.getAssignedUserId() != null) {
                report.setReporterId(t.getAssignedUserId());
            }
        }
        // 若配置"报工需要审批"=true，则提交后进入待审批状态；否则直接提交
        boolean needApproval = bizConfigService.getBoolValue("workreport.need_approval", false);
        report.setStatus(needApproval ? "pending_approval" : "submitted");
        save(report);

        // 2. 更新工序任务的合格数和不良数
        if (report.getTaskId() != null) {
            BizWoProcessTask task = processTaskMapper.selectById(report.getTaskId());
            if (task != null) {
                // 校验: 若前道是外协工序，报工数量不得超过外协收料物料库存
                BigDecimal maxReportable = outsourceChainService.getMaxReportableQty(task);
                BigDecimal thisQualified = report.getQualifiedQty() != null ? report.getQualifiedQty() : BigDecimal.ZERO;
                BigDecimal thisDefect = report.getDefectQty() != null ? report.getDefectQty() : BigDecimal.ZERO;
                BigDecimal thisTotal = thisQualified.add(thisDefect);
                if (maxReportable != null) {
                    // 有质检模板时，已报工量记在 reportedQty；无质检时记在 qualifiedQty
                    BigDecimal alreadyReported = task.getQcTemplateId() != null
                            ? (task.getReportedQty() != null ? task.getReportedQty() : BigDecimal.ZERO)
                            : (task.getQualifiedQty() != null ? task.getQualifiedQty() : BigDecimal.ZERO);
                    BigDecimal alreadyDone = alreadyReported.add(task.getDefectQty() != null ? task.getDefectQty() : BigDecimal.ZERO);
                    BigDecimal remainingStock = maxReportable.subtract(alreadyDone);
                    if (thisTotal.compareTo(remainingStock) > 0) {
                        BigDecimal shortage = thisTotal.subtract(remainingStock);
                        throw new BusinessException("前道外协收料物料库存不足，可报工: "
                                + remainingStock + "，缺少: " + shortage);
                    }
                    // 自动扣减外协收料物料库存
                    if (thisTotal.compareTo(BigDecimal.ZERO) > 0 && task.getVirtualMaterialId() != null) {
                        try {
                            BizMaterial mat = materialMapper.selectById(task.getVirtualMaterialId());
                            Long warehouseId = mat != null && mat.getDefaultWarehouseId() != null
                                    ? mat.getDefaultWarehouseId() : 999L;
                            inventoryService.stockOut(task.getVirtualMaterialId(), warehouseId,
                                    thisTotal, "production_consume", null,
                                    "work_order", report.getWorkOrderId());
                            log.info("报工自动扣减外协收料库存: materialId={}, qty={}, workOrderId={}",
                                    task.getVirtualMaterialId(), thisTotal, report.getWorkOrderId());
                        } catch (Exception e) {
                            throw new BusinessException("外协收料物料出库失败: " + e.getMessage());
                        }
                    }
                }

                // 累加报工数量：有质检模板时合格数进 reportedQty（待质检），无质检直接进 qualifiedQty
                BigDecimal thisReportQualified = report.getQualifiedQty() != null ? report.getQualifiedQty() : BigDecimal.ZERO;
                if (task.getQcTemplateId() != null) {
                    BigDecimal prevReported = task.getReportedQty() != null ? task.getReportedQty() : BigDecimal.ZERO;
                    task.setReportedQty(prevReported.add(thisReportQualified));
                } else {
                    BigDecimal prevQualified = task.getQualifiedQty() != null ? task.getQualifiedQty() : BigDecimal.ZERO;
                    task.setQualifiedQty(prevQualified.add(thisReportQualified));
                }
                task.setDefectQty((task.getDefectQty() != null ? task.getDefectQty() : BigDecimal.ZERO).add(
                        report.getDefectQty() != null ? report.getDefectQty() : BigDecimal.ZERO));
                // 工序正在进行中
                if ("pending".equals(task.getStatus()) || "ready".equals(task.getStatus())) {
                    task.setStatus("in_progress");
                }
                // 判断工序是否完成（已报工量 >= 计划数，不良数不影响）
                BigDecimal effectiveQty = task.getQcTemplateId() != null
                        ? (task.getReportedQty() != null ? task.getReportedQty() : BigDecimal.ZERO)
                        : (task.getQualifiedQty() != null ? task.getQualifiedQty() : BigDecimal.ZERO);
                boolean quantityMet = task.getPlanQty() != null && effectiveQty.compareTo(task.getPlanQty()) >= 0;

                // 工序配了质检模板 → 每次有合格数报工时立即创建质检任务（支持分批报工场景）
                if (task.getQcTemplateId() != null && thisQualified.compareTo(BigDecimal.ZERO) > 0) {
                    BizQcTask qcTask = createProcessQcTask(report.getWorkOrderId(), task, thisQualified);
                    if (qcTask != null) {
                        task.setQcTaskId(qcTask.getId());
                    }
                    task.setQcStatus("pending");
                    if (quantityMet) {
                        // 数量已达计划数，等待本批次质检完成后推进
                        task.setStatus("pending_qc");
                    }
                    // 否则继续保持 in_progress，等待后续批次继续报工
                } else if (quantityMet) {
                    // 无质检模板且数量已满 → 工序直接完成
                    task.setStatus("completed");
                    task.setEndTime(java.time.LocalDateTime.now());
                    activateNextTask(report.getWorkOrderId(), task.getStepNo());
                }
                processTaskMapper.updateById(task);

                // 流水线推进: 每次报工都尝试激活下一组(并行工序场景下, 下一组只要有
                // MIN>0 的可报工余量就应该可做, 不必等本组全部完成)
                if (thisQualified.compareTo(BigDecimal.ZERO) > 0) {
                    activateNextTask(report.getWorkOrderId(), task.getStepNo());
                }

                // 3. 更新工单完工数：取末工序的合格数作为工单完工数
                updateWorkOrderProgress(report.getWorkOrderId());

                // 4. 计算计件工资: 合格数 × 计件单价 × 工资倍率（班组模式由 submitTeamReport 按权重分配，此处跳过）
                //    工资倍率: 报工模式=三机 → 工序工资比例 × 0.9; 默认(正常) → 1
                BigDecimal wage = BigDecimal.ZERO;
                if (!"team".equals(report.getReportType())
                        && task.getPiecePrice() != null && report.getQualifiedQty() != null
                        && report.getQualifiedQty().compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal multiplier = computeWageMultiplier(report.getWageMode(), task.getWageRatio());
                    wage = report.getQualifiedQty().multiply(task.getPiecePrice()).multiply(multiplier)
                            .setScale(2, RoundingMode.HALF_UP);
                    report.setPieceWage(wage);
                    updateById(report); // 更新报工记录中的计件工资

                    BizWageRecord wageRecord = new BizWageRecord();
                    wageRecord.setUserId(report.getReporterId());
                    wageRecord.setWorkOrderId(report.getWorkOrderId());
                    wageRecord.setTaskId(report.getTaskId());
                    wageRecord.setReportId(report.getId());
                    wageRecord.setWageType("personal");
                    wageRecord.setAmount(wage);
                    wageRecord.setReportDate(LocalDate.now());
                    wageRecordMapper.insert(wageRecord);
                }

                // 5. 成本归集: 将计件工资累加到工单成本（班组模式也需要归集）
                BigDecimal costWage = wage;
                if ("team".equals(report.getReportType()) && costWage.compareTo(BigDecimal.ZERO) == 0
                        && task.getPiecePrice() != null && report.getQualifiedQty() != null
                        && report.getQualifiedQty().compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal multiplier = computeWageMultiplier(report.getWageMode(), task.getWageRatio());
                    costWage = report.getQualifiedQty().multiply(task.getPiecePrice()).multiply(multiplier)
                            .setScale(2, RoundingMode.HALF_UP);
                }
                if (costWage.compareTo(BigDecimal.ZERO) > 0) {
                    accumulateLaborCost(report.getWorkOrderId(), costWage);
                }

                // 6. 报工不良品 → 自动创建缺陷记录
                if (report.getDefectQty() != null && report.getDefectQty().compareTo(BigDecimal.ZERO) > 0) {
                    createDefectRecord(report, task);
                }

                // 7. 外协链路: 无质检 → 每次报工触发入库+申请; 有质检 → 等质检通过后在 onProcessQcPassed 中触发
                if (report.getQualifiedQty() != null && report.getQualifiedQty().compareTo(BigDecimal.ZERO) > 0
                        && task.getQcTemplateId() == null) {
                    try {
                        outsourceChainService.handlePrevStepReported(
                                report.getWorkOrderId(), task, report.getQualifiedQty());
                    } catch (Exception e) {
                        log.warn("外协链路触发失败: workOrderId={}, taskId={}", report.getWorkOrderId(), task.getId(), e);
                    }
                }

                // 9. 任何无质检工序报工合格数有增长 → 触发增量入库判断
                //    入库阈值 = 全部工序 min(qualifiedQty) - 已生成入库总量,delta>0 才会真正建任务
                //    (有质检工序的合格数在 onProcessQcPassed 里增长,那里也会调用一次)
                if (task.getQcTemplateId() == null && thisQualified.compareTo(BigDecimal.ZERO) > 0) {
                    tryCreateLastStepStockIn(report.getWorkOrderId(), task.getStepNo());
                }
            }
        }
    }

    /**
     * 判断当前用户是否允许看见这条工序任务:
     *   - 非班组指派: 直接放行(上层 SQL 已筛过)
     *   - 班组指派 + assignedMemberIds 为空/[]: 放行(全部班组成员)
     *   - 班组指派 + assignedMemberIds 非空: userId 必须在 JSON 列表里
     *   - JSON 解析失败: 保守放行,避免误拦
     */
    private static boolean isTaskVisibleToUser(BizWoProcessTask task, Long userId) {
        if (!"team".equals(task.getAssignType())) return true;
        String json = task.getAssignedMemberIds();
        if (json == null || json.isBlank() || "[]".equals(json.trim())) return true;
        try {
            java.util.List<?> ids = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(json, java.util.List.class);
            if (ids == null || ids.isEmpty()) return true;
            for (Object o : ids) {
                if (o == null) continue;
                try {
                    if (Long.valueOf(o.toString()).equals(userId)) return true;
                } catch (Exception ignored) {}
            }
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * 计算工资倍率：
     *   - normal(正常) 或未指定 → 1
     *   - three_machine(三机) → 工序在生产计划里配置的 wageRatio (空或 ≤0 则按 1)
     *     例:某道工序 wageRatio=0.9 则三机下倍率 0.9;wageRatio=1 则三机也=1
     */
    private static BigDecimal computeWageMultiplier(String wageMode, BigDecimal stepWageRatio) {
        if ("three_machine".equals(wageMode)) {
            return stepWageRatio != null && stepWageRatio.compareTo(BigDecimal.ZERO) > 0
                    ? stepWageRatio : BigDecimal.ONE;
        }
        return BigDecimal.ONE;
    }

    /**
     * 成本归集：将计件工资累加到工单成本的人工成本
     */
    private void accumulateLaborCost(Long workOrderId, BigDecimal wage) {
        try {
            BizWoCost woCost = financeService.getWoCost(workOrderId);
            woCost.setLaborCost(woCost.getLaborCost().add(wage));
            financeService.updateWoCost(woCost);
        } catch (Exception e) {
            log.warn("成本归集失败: workOrderId={}, wage={}", workOrderId, wage, e);
        }
    }

    /**
     * 半成品入库：工序完成且标记为入半成品库时，将合格品入半成品仓
     */
    private void semiProductStockIn(Long workOrderId, BizWoProcessTask task) {
        try {
            BizWorkOrder wo = workOrderMapper.selectById(workOrderId);
            if (wo == null || wo.getProductId() == null) return;

            BizMaterial material = materialMapper.selectById(wo.getProductId());
            Long warehouseId = material != null && material.getDefaultWarehouseId() != null
                    ? material.getDefaultWarehouseId() : 1L;

            inventoryService.stockIn(
                    wo.getProductId(), warehouseId, task.getQualifiedQty(),
                    "semi_in", null, "work_order", workOrderId);
            log.info("半成品入库: workOrderId={}, productId={}, qty={}",
                    workOrderId, wo.getProductId(), task.getQualifiedQty());
        } catch (Exception e) {
            log.warn("半成品入库失败: workOrderId={}, taskId={}", workOrderId, task.getId(), e);
        }
    }

    /**
     * 报工不良品 → 自动创建缺陷记录
     */
    private void createDefectRecord(BizWorkReport report, BizWoProcessTask task) {
        try {
            BizWorkOrder wo = workOrderMapper.selectById(report.getWorkOrderId());
            BizDefectRecord defect = new BizDefectRecord();
            defect.setDefectNo(CodeUtil.generate("DF"));
            defect.setSourceType("work_report");
            defect.setSourceId(report.getId());
            defect.setProductId(wo != null ? wo.getProductId() : null);
            defect.setWorkOrderId(report.getWorkOrderId());
            defect.setQuantity(report.getDefectQty());
            defect.setDefectType("process");
            defect.setHandleStatus("pending");
            defectRecordMapper.insert(defect);
        } catch (Exception e) {
            log.warn("创建缺陷记录失败: reportId={}", report.getId(), e);
        }
    }

    /**
     * 激活下一组工序任务（支持并行工序）
     * 当前工序完成后，检查同组所有工序是否都已完成
     * 如果同组全部完成，将下一组的所有工序从pending变为ready
     */
    /** 判断是否为末道工序(stepNo 最大) */
    private boolean isLastStep(BizWoProcessTask task) {
        if (task.getWorkOrderId() == null || task.getStepNo() == null) return false;
        Integer maxStepNo = processTaskMapper.selectList(
                new LambdaQueryWrapper<BizWoProcessTask>()
                        .eq(BizWoProcessTask::getWorkOrderId, task.getWorkOrderId())
                        .ne(BizWoProcessTask::getStatus, "cancelled")
                        .select(BizWoProcessTask::getStepNo))
                .stream().map(BizWoProcessTask::getStepNo).filter(java.util.Objects::nonNull)
                .max(Integer::compareTo).orElse(0);
        return task.getStepNo().equals(maxStepNo);
    }

    public void activateNextTask(Long workOrderId, Integer currentStepNo) {
        if (workOrderId == null || currentStepNo == null) return;

        // 1. 找到当前任务的stepGroup
        BizWoProcessTask currentTask = processTaskMapper.selectOne(
                new LambdaQueryWrapper<BizWoProcessTask>()
                        .eq(BizWoProcessTask::getWorkOrderId, workOrderId)
                        .eq(BizWoProcessTask::getStepNo, currentStepNo)
                        .last("LIMIT 1"));
        if (currentTask == null || currentTask.getStepGroup() == null) return;

        Integer currentGroup = currentTask.getStepGroup();

        // 2. 同组所有任务 qualifiedQty 都 > 0 即可激活下一组(支持流水线/并行并行报工);
        //    若某个并行任务一次都没报工,下一组能领到的 MIN=0, 强行激活也没有意义
        List<BizWoProcessTask> sameGroupTasks = processTaskMapper.selectList(
                new LambdaQueryWrapper<BizWoProcessTask>()
                        .eq(BizWoProcessTask::getWorkOrderId, workOrderId)
                        .eq(BizWoProcessTask::getStepGroup, currentGroup)
                        .ne(BizWoProcessTask::getStatus, "cancelled"));
        boolean allGroupReported = sameGroupTasks.stream().allMatch(t ->
                (t.getQualifiedQty() != null && t.getQualifiedQty().compareTo(BigDecimal.ZERO) > 0)
             || "completed".equals(t.getStatus()));
        if (!allGroupReported) {
            return; // 同组中还存在未开始报工的任务,MIN=0 无法推进下一组
        }

        // 3. 找下一组中所有pending任务
        List<BizWoProcessTask> remaining = processTaskMapper.selectList(
                new LambdaQueryWrapper<BizWoProcessTask>()
                        .eq(BizWoProcessTask::getWorkOrderId, workOrderId)
                        .gt(BizWoProcessTask::getStepGroup, currentGroup)
                        .eq(BizWoProcessTask::getStatus, "pending")
                        .orderByAsc(BizWoProcessTask::getStepGroup)
                        .orderByAsc(BizWoProcessTask::getStepNo));
        if (remaining.isEmpty()) return;

        // 4. 激活下一组的所有任务（跳过外协工序，由外协链路管理）
        Integer nextGroup = remaining.get(0).getStepGroup();
        for (BizWoProcessTask nextTask : remaining) {
            if (!nextTask.getStepGroup().equals(nextGroup)) break;
            if (nextTask.getIsOutsourceable() != null && nextTask.getIsOutsourceable() == 1) {
                continue; // 外协工序不激活，由 OutsourceChainService 管理
            }
            nextTask.setStatus("ready");
            processTaskMapper.updateById(nextTask);
        }
    }

    /**
     * 更新工单完工数量
     * 取末组（stepGroup最大）工序的最小合格数作为工单的completed_qty
     * 如果所有工序都已完成，直接创建生产入库任务（末道工序质检即为质量门控）
     */
    public void updateWorkOrderProgress(Long workOrderId) {
        if (workOrderId == null) return;
        // 查询所有工序任务（排除已取消）
        List<BizWoProcessTask> tasks = processTaskMapper.selectList(
                new LambdaQueryWrapper<BizWoProcessTask>()
                        .eq(BizWoProcessTask::getWorkOrderId, workOrderId)
                        .ne(BizWoProcessTask::getStatus, "cancelled"));
        if (tasks.isEmpty()) return;

        // 完工数 = 全部工序 min(qualifiedQty) — 反映整条工艺路线的合格瓶颈,
        // 与 tryCreateLastStepStockIn 的入库判断保持口径一致。
        // 例: 4 道工序合格 [10,9,10,10] → 完工数 = 9(中间打磨瓶颈),不是末道的 10
        BigDecimal completedQty = tasks.stream()
                .map(t -> t.getQualifiedQty() != null ? t.getQualifiedQty() : BigDecimal.ZERO)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        BizWorkOrder wo = workOrderMapper.selectById(workOrderId);
        if (wo != null) {
            wo.setCompletedQty(completedQty);
            // 如果所有工序都已完成，设为 completed（生产完成），由用户手动创建批量入库任务
            boolean allCompleted = tasks.stream().allMatch(t -> "completed".equals(t.getStatus()));
            if (allCompleted && completedQty.compareTo(BigDecimal.ZERO) > 0) {
                wo.setStatus("completed");
                // 通知仓库安排入库
                try {
                    notificationService.notifyRole("warehouse", "工单完工通知",
                            "工单[" + wo.getWoNo() + "]全部工序完成(" + completedQty + "件)，请安排成品入库",
                            "approval", "work_order", wo.getId());
                } catch (Exception e) {
                    log.warn("通知仓库失败: workOrderId={}", wo.getId(), e);
                }
                log.info("工单全部工序完成，等待手动入库: workOrderId={}, completedQty={}", wo.getId(), completedQty);
            }
            workOrderMapper.updateById(wo);

            // 每次completedQty变动都同步到销售订单和生产计划（不仅仅是全部完成时）
            if (wo.getOrderId() != null) {
                try {
                    salesOrderService.syncOrderItemProducedQty(wo.getOrderId(), wo.getOrderItemId());
                } catch (Exception e) {
                    log.warn("同步订单行生产进度失败: orderId={}", wo.getOrderId(), e);
                }
            }
            if (wo.getPlanItemId() != null) {
                try {
                    productionPlanService.syncPlanItemCompletedQty(wo.getPlanItemId());
                } catch (Exception e) {
                    log.warn("同步计划行项完工数失败: planItemId={}", wo.getPlanItemId(), e);
                }
            }
        }
    }

    /**
     * 末道工序"增量入库": 取工单内所有非取消工序 qualifiedQty 的最小值作为产品实际可入库瓶颈,
     * 再减去 wo.stockInTaskQty(已生成入库任务总量),得到本次需要新建/追加的入库数量。
     *
     * 为什么要看所有工序而不是只看末道:
     *   - 例: 4 道串联工序,前 3 道分别 100/99/100/100 合格,
     *     若只看末道 stepNo=40 的 100,会按 100 入库;但实际产品瓶颈是中间打磨的 99 合格,
     *     入库 100 跟现实对不上。取所有工序 min(qualifiedQty)=99 才是真实可入库量。
     *
     * 适用场景:
     *   - 串联工序: 前道工序合格瓶颈直接体现到入库数量
     *   - 并行末道工序 A/B/C 同 stepNo 各自独立报工: min 也包含同 stepNo 的所有并行任务
     *   - 分批报工: 第 1 批所有工序都报到 5 → 入库 5; 第 2 批继续报到 10 → 追加 5
     *   - 有质检 + 无质检 混合: 任一条推进时调用, QC 通过后 onProcessQcPassed 再调用一次都安全
     */
    public void tryCreateLastStepStockIn(Long workOrderId, Integer stepNo) {
        if (workOrderId == null) return;
        try {
            List<BizWoProcessTask> allTasks = processTaskMapper.selectList(
                    new LambdaQueryWrapper<BizWoProcessTask>()
                            .eq(BizWoProcessTask::getWorkOrderId, workOrderId)
                            .ne(BizWoProcessTask::getStatus, "cancelled"));
            if (allTasks.isEmpty()) return;
            BigDecimal minQualified = allTasks.stream()
                    .map(t -> t.getQualifiedQty() != null ? t.getQualifiedQty() : BigDecimal.ZERO)
                    .min(java.util.Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
            if (minQualified.compareTo(BigDecimal.ZERO) <= 0) return;

            BizWorkOrder wo = workOrderMapper.selectById(workOrderId);
            if (wo == null) return;
            BigDecimal alreadyTasked = getGeneratedProductionInTaskQty(workOrderId);
            BigDecimal storedTasked = wo.getStockInTaskQty() != null ? wo.getStockInTaskQty() : BigDecimal.ZERO;
            if (storedTasked.compareTo(alreadyTasked) != 0) {
                wo.setStockInTaskQty(alreadyTasked);
                BigDecimal completedQty = wo.getCompletedQty() != null ? wo.getCompletedQty() : BigDecimal.ZERO;
                if (completedQty.compareTo(BigDecimal.ZERO) > 0 && alreadyTasked.compareTo(BigDecimal.ZERO) > 0) {
                    wo.setInvTaskStatus(alreadyTasked.compareTo(completedQty) >= 0 ? "generated" : "partial");
                } else {
                    wo.setInvTaskStatus(null);
                }
                workOrderMapper.updateById(wo);
            }
            BigDecimal delta = minQualified.subtract(alreadyTasked);
            if (delta.compareTo(BigDecimal.ZERO) <= 0) return;

            triggerProductionStockIn(wo, delta);
            log.info("末道工序增量入库: woId={}, triggerStepNo={}, minQualifiedAcrossAllSteps={}, alreadyTasked={}, delta={}",
                    workOrderId, stepNo, minQualified, alreadyTasked, delta);
        } catch (Exception e) {
            log.warn("末道工序增量入库触发失败: workOrderId={}, stepNo={}", workOrderId, stepNo, e);
        }
    }

    private BigDecimal getGeneratedProductionInTaskQty(Long workOrderId) {
        List<BizInvTask> tasks = invTaskMapper.selectList(
                new LambdaQueryWrapper<BizInvTask>()
                        .eq(BizInvTask::getInvType, "production_in")
                        .eq(BizInvTask::getSourceType, "work_order")
                        .eq(BizInvTask::getSourceId, workOrderId)
                        .eq(BizInvTask::getDeleted, 0));
        if (tasks.isEmpty()) return BigDecimal.ZERO;
        List<Long> taskIds = tasks.stream().map(BizInvTask::getId).collect(Collectors.toList());
        return invTaskItemMapper.selectList(
                        new LambdaQueryWrapper<BizInvTaskItem>()
                                .in(BizInvTaskItem::getTaskId, taskIds)
                                .eq(BizInvTaskItem::getDeleted, 0))
                .stream()
                .map(i -> i.getQuantity() != null ? i.getQuantity() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 末道工序完成后直接创建生产入库任务
     * 末道工序的质检（如有配置）即为产品质量门控，无需单独的成品检验步骤
     */
    private void triggerProductionStockIn(BizWorkOrder wo, BigDecimal qty) {
        try {
            BizMaterial product = wo.getProductId() != null ? materialMapper.selectById(wo.getProductId()) : null;
            Long whId = product != null ? product.getDefaultWarehouseId() : null;
            Long taskId = invTaskService.createFromWorkOrder(
                    wo.getId(), wo.getWoNo(), wo.getProductId(),
                    product != null ? product.getMaterialNo() : null,
                    product != null ? product.getMaterialName() : null,
                    product != null ? product.getSpec() : null,
                    product != null ? product.getUnit() : null,
                    qty, whId, SecurityUtil.getCurrentUserId());

            notificationService.notifyRole("warehouse", "成品入库通知",
                    "工单[" + wo.getWoNo() + "]末道工序完成(" + qty + "件)，请安排成品入库",
                    "approval", "inv_task", taskId);
            log.info("生产入库任务已创建: workOrderId={}, invTaskId={}, qty={}", wo.getId(), taskId, qty);
        } catch (Exception e) {
            log.warn("创建生产入库任务失败: workOrderId={}", wo.getId(), e);
        }
    }

    /**
     * 驳回报工（回滚报工数量）
     * 约束：驳回后本道工序总报工数量不能小于下道工序的总报工数量
     */
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String reason) {
        BizWorkReport report = getById(id);
        if (report == null) throw new BusinessException("报工记录不存在");
        if ("rejected".equals(report.getStatus())) throw new BusinessException("已驳回，不可重复操作");

        // 回滚工序任务的报工数量
        if (report.getTaskId() != null) {
            BizWoProcessTask task = processTaskMapper.selectById(report.getTaskId());
            if (task != null) {
                BigDecimal rollbackQualified = report.getQualifiedQty() != null ? report.getQualifiedQty() : BigDecimal.ZERO;
                BigDecimal rollbackDefect = report.getDefectQty() != null ? report.getDefectQty() : BigDecimal.ZERO;

                // 计算回滚后的合格数
                BigDecimal qualifiedSource = task.getQcTemplateId() != null
                        ? (task.getReportedQty() != null ? task.getReportedQty() : BigDecimal.ZERO)
                        : (task.getQualifiedQty() != null ? task.getQualifiedQty() : BigDecimal.ZERO);
                BigDecimal qualifiedAfterRollback = qualifiedSource.subtract(rollbackQualified).max(BigDecimal.ZERO);

                // 约束检查：回滚后合格数不能小于下道工序的总报工数
                validateRollbackConstraint(report.getWorkOrderId(), task, qualifiedAfterRollback);

                // 应用回滚
                if (task.getQcTemplateId() != null) {
                    task.setReportedQty(qualifiedAfterRollback);
                } else {
                    task.setQualifiedQty(qualifiedAfterRollback);
                }
                task.setDefectQty((task.getDefectQty() != null ? task.getDefectQty() : BigDecimal.ZERO)
                        .subtract(rollbackDefect).max(BigDecimal.ZERO));

                // 重置工序状态
                if ("completed".equals(task.getStatus()) || "pending_qc".equals(task.getStatus())) {
                    task.setStatus("in_progress");
                    task.setEndTime(null);
                }
                processTaskMapper.updateById(task);

                // 同步工单进度
                updateWorkOrderProgress(report.getWorkOrderId());
            }
        }

        report.setStatus("rejected");
        report.setRejectReason(StringUtils.hasText(reason) ? reason : null);
        updateById(report);
    }

    /**
     * 校验驳回后的合格数约束：不能小于下道工序的总报工数量
     */
    private void validateRollbackConstraint(Long workOrderId, BizWoProcessTask task, BigDecimal qualifiedAfterRollback) {
        List<BizWoProcessTask> allTasks = processTaskMapper.selectList(
                new LambdaQueryWrapper<BizWoProcessTask>()
                        .eq(BizWoProcessTask::getWorkOrderId, workOrderId)
                        .ne(BizWoProcessTask::getStatus, "cancelled")
                        .orderByAsc(BizWoProcessTask::getStepGroup)
                        .orderByAsc(BizWoProcessTask::getStepNo));
        Integer currentGroup = task.getStepGroup();
        if (currentGroup == null) return;

        Integer nextGroup = allTasks.stream()
                .map(BizWoProcessTask::getStepGroup)
                .filter(g -> g != null && g > currentGroup)
                .min(Integer::compareTo).orElse(null);
        if (nextGroup == null) return;

        BigDecimal nextMinQualified = allTasks.stream()
                .filter(t -> nextGroup.equals(t.getStepGroup()))
                .map(t -> {
                    BigDecimal q = t.getQcTemplateId() != null
                            ? (t.getReportedQty() != null ? t.getReportedQty() : BigDecimal.ZERO)
                            : (t.getQualifiedQty() != null ? t.getQualifiedQty() : BigDecimal.ZERO);
                    return q.add(t.getDefectQty() != null ? t.getDefectQty() : BigDecimal.ZERO);
                })
                .min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);

        if (qualifiedAfterRollback.compareTo(nextMinQualified) < 0) {
            throw new BusinessException(
                    "驳回失败：驳回后本道工序合格数(" + qualifiedAfterRollback +
                    ")不能小于下道工序的总报工数量(" + nextMinQualified + ")");
        }
    }

    /**
     * 分页查询
     */
    public PageResult<Map<String, Object>> getPage(String keyword, String status, Long workOrderId,
                                              String woNo, int pageNum, int pageSize) {
        return getPage(keyword, status, workOrderId, woNo, null, null, pageNum, pageSize);
    }

    public PageResult<Map<String, Object>> getPage(String keyword, String status, Long workOrderId,
                                              String woNo, String processName, String reporterName,
                                              int pageNum, int pageSize) {
        LambdaQueryWrapper<BizWorkReport> wrapper = new LambdaQueryWrapper<BizWorkReport>()
                .like(StringUtils.hasText(keyword), BizWorkReport::getReportNo, keyword)
                .eq(StringUtils.hasText(status), BizWorkReport::getStatus, status)
                .eq(workOrderId != null, BizWorkReport::getWorkOrderId, workOrderId)
                .orderByDesc(BizWorkReport::getCreateTime);

        // 按工单号模糊查询：先找到匹配的工单ID集合
        if (StringUtils.hasText(woNo)) {
            List<Long> matchWoIds = workOrderMapper.selectList(
                            new LambdaQueryWrapper<BizWorkOrder>()
                                    .like(BizWorkOrder::getWoNo, woNo)
                                    .select(BizWorkOrder::getId))
                    .stream().map(BizWorkOrder::getId).collect(Collectors.toList());
            if (matchWoIds.isEmpty()) {
                return new PageResult<>(List.of(), 0, pageNum, pageSize);
            }
            wrapper.in(BizWorkReport::getWorkOrderId, matchWoIds);
        }
        // 工序名称过滤:模糊匹配工序任务的 stepName → taskIds
        if (StringUtils.hasText(processName)) {
            List<Long> matchTaskIds = processTaskMapper.selectList(
                    new LambdaQueryWrapper<BizWoProcessTask>()
                            .like(BizWoProcessTask::getStepName, processName)
                            .select(BizWoProcessTask::getId))
                    .stream().map(BizWoProcessTask::getId).collect(Collectors.toList());
            if (matchTaskIds.isEmpty()) return new PageResult<>(List.of(), 0, pageNum, pageSize);
            wrapper.in(BizWorkReport::getTaskId, matchTaskIds);
        }
        // 报工人模糊匹配:按真实姓名匹配 SysUser → reporterIds
        if (StringUtils.hasText(reporterName)) {
            List<Long> matchUserIds = userMapper.selectList(
                    new LambdaQueryWrapper<SysUser>().like(SysUser::getRealName, reporterName))
                    .stream().map(SysUser::getId).collect(Collectors.toList());
            if (matchUserIds.isEmpty()) return new PageResult<>(List.of(), 0, pageNum, pageSize);
            wrapper.in(BizWorkReport::getReporterId, matchUserIds);
        }

        Page<BizWorkReport> page = page(new Page<>(pageNum, pageSize), wrapper);

        // 批量加载关联实体名称
        List<BizWorkReport> records = page.getRecords();
        Set<Long> reporterIds = records.stream().map(BizWorkReport::getReporterId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> woIds = records.stream().map(BizWorkReport::getWorkOrderId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> taskIds = records.stream().map(BizWorkReport::getTaskId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> userNameMap = reporterIds.isEmpty() ? Map.of() :
                userMapper.selectBatchIds(reporterIds).stream().collect(Collectors.toMap(SysUser::getId, SysUser::getRealName, (a, b) -> a));
        Map<Long, String> woNoMap = woIds.isEmpty() ? Map.of() :
                workOrderMapper.selectBatchIds(woIds).stream().collect(Collectors.toMap(BizWorkOrder::getId, BizWorkOrder::getWoNo, (a, b) -> a));
        Map<Long, String> stepNameMap = taskIds.isEmpty() ? Map.of() :
                processTaskMapper.selectBatchIds(taskIds).stream().collect(Collectors.toMap(BizWoProcessTask::getId, BizWoProcessTask::getStepName, (a, b) -> a));

        List<Map<String, Object>> list = records.stream().map(r -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", r.getId());
            map.put("reportNo", r.getReportNo());
            map.put("taskId", r.getTaskId());
            map.put("workOrderId", r.getWorkOrderId());
            map.put("workOrderNo", r.getWorkOrderId() != null ? woNoMap.get(r.getWorkOrderId()) : null);
            map.put("stepName", r.getTaskId() != null ? stepNameMap.get(r.getTaskId()) : null);
            map.put("reporterId", r.getReporterId());
            map.put("employeeName", r.getReporterId() != null ? userNameMap.get(r.getReporterId()) : null);
            map.put("reportType", r.getReportType());
            map.put("qualifiedQty", r.getQualifiedQty());
            map.put("defectQty", r.getDefectQty());
            map.put("defectReasons", r.getDefectReasons());
            map.put("equipmentId", r.getEquipmentId());
            map.put("batchNo", r.getBatchNo());
            map.put("workHours", r.getWorkHours());
            map.put("photoUrls", r.getPhotoUrls());
            map.put("pieceWage", r.getPieceWage());
            map.put("status", r.getStatus());
            map.put("remark", r.getRemark());
            map.put("rejectReason", r.getRejectReason());
            map.put("teamReportId", r.getTeamReportId());
            map.put("createTime", r.getCreateTime());
            map.put("updateTime", r.getUpdateTime());
            return map;
        }).collect(Collectors.toList());

        return new PageResult<>(list, page.getTotal(), pageNum, pageSize);
    }

    /**
     * 获取报工记录完整详情（含物料清单和班组成员明细）
     */
    public Map<String, Object> getFullDetail(Long id) {
        BizWorkReport report = getById(id);
        if (report == null) throw new BusinessException("报工记录不存在");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", report.getId());
        result.put("reportNo", report.getReportNo());
        result.put("taskId", report.getTaskId());
        result.put("workOrderId", report.getWorkOrderId());
        result.put("reporterId", report.getReporterId());
        result.put("reportType", report.getReportType());
        result.put("qualifiedQty", report.getQualifiedQty());
        result.put("defectQty", report.getDefectQty());
        result.put("defectReasons", report.getDefectReasons());
        result.put("workHours", report.getWorkHours());
        result.put("pieceWage", report.getPieceWage());
        result.put("status", report.getStatus());
        result.put("remark", report.getRemark());
        result.put("rejectReason", report.getRejectReason());
        result.put("teamReportId", report.getTeamReportId());
        result.put("photoUrls", report.getPhotoUrls());
        result.put("createTime", report.getCreateTime());

        // 加载关联名称
        if (report.getReporterId() != null) {
            SysUser user = userMapper.selectById(report.getReporterId());
            result.put("employeeName", user != null ? user.getRealName() : null);
        }
        if (report.getWorkOrderId() != null) {
            BizWorkOrder wo = workOrderMapper.selectById(report.getWorkOrderId());
            result.put("workOrderNo", wo != null ? wo.getWoNo() : null);
            result.put("workOrderStatus", wo != null ? wo.getStatus() : null);
        }
        if (report.getTaskId() != null) {
            BizWoProcessTask task = processTaskMapper.selectById(report.getTaskId());
            result.put("stepName", task != null ? task.getStepName() : null);
        }

        // 工单BOM物料清单
        if (report.getWorkOrderId() != null) {
            List<BizWoBomSnapshot> snapshots = bomSnapshotMapper.selectList(
                    new LambdaQueryWrapper<BizWoBomSnapshot>()
                            .eq(BizWoBomSnapshot::getWorkOrderId, report.getWorkOrderId()));
            Set<Long> matIds = snapshots.stream().map(BizWoBomSnapshot::getMaterialId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            Map<Long, BizMaterial> matMap = matIds.isEmpty() ? Map.of() :
                    materialMapper.selectBatchIds(matIds).stream()
                            .collect(Collectors.toMap(BizMaterial::getId, m -> m, (a, b) -> a));
            List<Map<String, Object>> bomList = snapshots.stream().map(s -> {
                Map<String, Object> m = new LinkedHashMap<>();
                BizMaterial mat = matMap.get(s.getMaterialId());
                m.put("materialId", s.getMaterialId());
                m.put("materialNo", mat != null ? mat.getMaterialNo() : null);
                m.put("materialName", mat != null ? mat.getMaterialName() : null);
                m.put("spec", mat != null ? mat.getSpec() : null);
                m.put("unit", mat != null ? mat.getUnit() : null);
                m.put("requiredQty", s.getRequiredQty());
                m.put("issuedQty", s.getIssuedQty());
                return m;
            }).collect(Collectors.toList());
            result.put("bomItems", bomList);
        }

        // 人员明细（个人报工：显示排产指派人员；班组报工：显示班组成员明细）
        if ("personal".equals(report.getReportType()) && report.getTaskId() != null) {
            BizWoProcessTask task = processTaskMapper.selectById(report.getTaskId());
            if (task != null) {
                List<Map<String, Object>> memberList = new ArrayList<>();
                // 如果是班组指派，展开班组成员
                if ("team".equals(task.getAssignType()) && task.getAssignedTeamId() != null) {
                    List<BizTeamMember> members = teamMemberMapper.selectList(
                            new LambdaQueryWrapper<BizTeamMember>()
                                    .eq(BizTeamMember::getTeamId, task.getAssignedTeamId()));
                    Set<Long> memUserIds = members.stream().map(BizTeamMember::getUserId)
                            .filter(Objects::nonNull).collect(Collectors.toSet());
                    Map<Long, String> memNameMap = memUserIds.isEmpty() ? Map.of() :
                            userMapper.selectBatchIds(memUserIds).stream()
                                    .collect(Collectors.toMap(SysUser::getId, SysUser::getRealName, (a, b) -> a));
                    // 班组成员真实的合格/不合格数来自 biz_work_report 同 teamReportId 的个人记录(整数)
                    // 不再按 weight 算 ratio(会算成 6.67 这种小数, 跟工位机的整数报工不一致)
                    Map<Long, BizWorkReport> reportByUser = report.getTeamReportId() != null
                            ? list(new LambdaQueryWrapper<BizWorkReport>()
                                    .eq(BizWorkReport::getTeamReportId, report.getTeamReportId())).stream()
                                .filter(r -> r.getReporterId() != null)
                                .collect(Collectors.toMap(BizWorkReport::getReporterId, r -> r, (a, b) -> a))
                            : Map.of();
                    for (BizTeamMember member : members) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("userId", member.getUserId());
                        m.put("userName", memNameMap.getOrDefault(member.getUserId(), ""));
                        m.put("weight", member.getWeight());
                        BizWorkReport mr = reportByUser.get(member.getUserId());
                        m.put("qualifiedQty", mr != null && mr.getQualifiedQty() != null ? mr.getQualifiedQty() : BigDecimal.ZERO);
                        m.put("defectQty", mr != null && mr.getDefectQty() != null ? mr.getDefectQty() : BigDecimal.ZERO);
                        m.put("allocatedWage", mr != null && mr.getPieceWage() != null ? mr.getPieceWage() : BigDecimal.ZERO);
                        memberList.add(m);
                    }
                } else if (task.getAssignedUserId() != null) {
                    // 个人指派，只显示一条
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("userId", task.getAssignedUserId());
                    SysUser assignedUser = userMapper.selectById(task.getAssignedUserId());
                    m.put("userName", assignedUser != null ? assignedUser.getRealName() : "");
                    m.put("weight", BigDecimal.ONE);
                    m.put("qualifiedQty", report.getQualifiedQty());
                    m.put("defectQty", report.getDefectQty());
                    m.put("allocatedWage", report.getPieceWage());
                    memberList.add(m);
                }
                if (!memberList.isEmpty()) {
                    result.put("teamMembers", memberList);
                    result.put("totalWage", report.getPieceWage());
                }
            }
        }

        if ("team".equals(report.getReportType()) && report.getTeamReportId() != null) {
            List<BizTeamReportDetail> details = teamReportDetailMapper.selectList(
                    new LambdaQueryWrapper<BizTeamReportDetail>()
                            .eq(BizTeamReportDetail::getTeamReportId, report.getTeamReportId()));
            Set<Long> userIds = details.stream().map(BizTeamReportDetail::getUserId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            Map<Long, String> userNameMap = userIds.isEmpty() ? Map.of() :
                    userMapper.selectBatchIds(userIds).stream()
                            .collect(Collectors.toMap(SysUser::getId, SysUser::getRealName, (a, b) -> a));
            // 班组成员真实的合格/不合格数量来自 biz_work_report 里同 teamReportId 的个人记录(整数)
            // 不再按 weight 算 ratio (会算成 6.67 这种小数, 跟工位机实际整数报工不一致)
            List<BizWorkReport> memberReports = list(new LambdaQueryWrapper<BizWorkReport>()
                    .eq(BizWorkReport::getTeamReportId, report.getTeamReportId()));
            Map<Long, BizWorkReport> reportByUser = memberReports.stream()
                    .filter(r -> r.getReporterId() != null)
                    .collect(Collectors.toMap(BizWorkReport::getReporterId, r -> r, (a, b) -> a));

            List<Map<String, Object>> memberList = details.stream().map(d -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("userId", d.getUserId());
                m.put("userName", userNameMap.getOrDefault(d.getUserId(), ""));
                m.put("weight", d.getWeight());
                m.put("allocatedWage", d.getAllocatedWage());
                BizWorkReport mr = reportByUser.get(d.getUserId());
                m.put("qualifiedQty", mr != null && mr.getQualifiedQty() != null ? mr.getQualifiedQty() : BigDecimal.ZERO);
                m.put("defectQty", mr != null && mr.getDefectQty() != null ? mr.getDefectQty() : BigDecimal.ZERO);
                return m;
            }).collect(Collectors.toList());
            result.put("teamMembers", memberList);

            // 班组报工汇总信息
            BizTeamWorkReport teamReport = teamWorkReportMapper.selectById(report.getTeamReportId());
            if (teamReport != null) {
                result.put("totalWage", teamReport.getTotalWage());
            }
        }

        return result;
    }

    /**
     * 提交班组报工
     * 由班组长提交，按权重分配工资给各成员，并生成对应的工资记录
     *
     * @param teamReport 班组报工主记录
     * @param details    班组成员明细列表（包含各成员权重）
     */
    @Transactional(rollbackFor = Exception.class)
    public void submitTeamReport(BizTeamWorkReport teamReport, List<BizTeamReportDetail> details) {
        // 领料申请校验: 工单存在未通过(还没生成出库任务)的领料申请 → 阻断报工
        checkPendingMaterialRequest(teamReport.getWorkOrderId());

        // 生成报工单号并保存主记录
        teamReport.setReportNo(CodeUtil.generate("TWR"));
        teamWorkReportMapper.insert(teamReport);

        // 回写最近一条同 taskId+reporterId 的 biz_work_report（由 submitPersonalReport 创建），关联 teamReportId
        // 避免重复在 biz_work_report 里创建一条"班组主记录"（原来的做法会导致列表里同一次报工出现 2 行）
        BizWorkReport existing = getOne(new LambdaQueryWrapper<BizWorkReport>()
                .eq(BizWorkReport::getTaskId, teamReport.getTaskId())
                .eq(BizWorkReport::getReporterId, teamReport.getLeaderId())
                .isNull(BizWorkReport::getTeamReportId)
                .orderByDesc(BizWorkReport::getId)
                .last("LIMIT 1"));
        if (existing != null) {
            existing.setReportType("team");
            existing.setTeamReportId(teamReport.getId());
            updateById(existing);
        }

        // 计算权重总和
        BigDecimal totalWeight = details.stream()
                .map(BizTeamReportDetail::getWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalWeight.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("班组成员权重总和必须大于0");
        }

        // 按权重分配工资并保存明细
        for (BizTeamReportDetail detail : details) {
            detail.setTeamReportId(teamReport.getId());

            // 按权重占比计算分配工资
            BigDecimal allocatedWage = teamReport.getTotalWage()
                    .multiply(detail.getWeight())
                    .divide(totalWeight, 2, RoundingMode.HALF_UP);
            detail.setAllocatedWage(allocatedWage);
            teamReportDetailMapper.insert(detail);

            // 为每个成员生成工资记录
            BizWageRecord wageRecord = new BizWageRecord();
            wageRecord.setUserId(detail.getUserId());
            wageRecord.setWorkOrderId(teamReport.getWorkOrderId());
            wageRecord.setTaskId(teamReport.getTaskId());
            wageRecord.setReportId(teamReport.getId());
            wageRecord.setWageType("team_piece");
            wageRecord.setAmount(allocatedWage);
            wageRecord.setReportDate(LocalDate.now());
            wageRecordMapper.insert(wageRecord);
        }
    }

    /**
     * 获取报工数量限制
     * - 首道工序: 根据BOM已领料计算最大可报工数（最小物料比值）
     * - 非首道工序: 不能超过上道工序已报工合格数
     * @return { maxReportableQty, isFirstGroup, reason }
     */
    public Map<String, Object> getReportLimit(Long workOrderId, Long taskId) {
        Map<String, Object> result = new LinkedHashMap<>();
        BizWoProcessTask task = processTaskMapper.selectById(taskId);
        if (task == null) {
            result.put("maxReportableQty", 0);
            result.put("reason", "工序任务不存在");
            return result;
        }

        List<BizWoProcessTask> allTasks = processTaskMapper.selectList(
                new LambdaQueryWrapper<BizWoProcessTask>()
                        .eq(BizWoProcessTask::getWorkOrderId, workOrderId)
                        .ne(BizWoProcessTask::getStatus, "cancelled")
                        .orderByAsc(BizWoProcessTask::getStepGroup)
                        .orderByAsc(BizWoProcessTask::getStepNo));
        if (allTasks.isEmpty()) {
            result.put("maxReportableQty", 0);
            result.put("reason", "无工序任务");
            return result;
        }

        // 已报工量：有质检模板时用 reportedQty，无质检时用 qualifiedQty
        // 注意：defectQty 不再参与可报工数计算（质检报废不良不应影响报工）
        BigDecimal alreadyDone = task.getQcTemplateId() != null
                ? (task.getReportedQty() != null ? task.getReportedQty() : BigDecimal.ZERO)
                : (task.getQualifiedQty() != null ? task.getQualifiedQty() : BigDecimal.ZERO);

        Integer firstGroup = allTasks.get(0).getStepGroup();
        boolean isFirstGroup = firstGroup.equals(task.getStepGroup());
        result.put("isFirstGroup", isFirstGroup);

        if (isFirstGroup) {
            // 首道工序: 根据BOM已领料比值计算
            BizWorkOrder wo = workOrderMapper.selectById(workOrderId);
            BigDecimal planQty = wo != null ? wo.getPlanQty() : BigDecimal.ZERO;
            List<BizWoBomSnapshot> snapshots = bomSnapshotMapper.selectList(
                    new LambdaQueryWrapper<BizWoBomSnapshot>()
                            .eq(BizWoBomSnapshot::getWorkOrderId, workOrderId));
            if (snapshots.isEmpty()) {
                // 无BOM则不限制
                BigDecimal remaining = (task.getPlanQty() != null ? task.getPlanQty() : BigDecimal.ZERO).subtract(alreadyDone);
                result.put("maxReportableQty", remaining.max(BigDecimal.ZERO));
                result.put("reason", "无BOM快照，按计划数限制");
                return result;
            }

            BigDecimal maxFromBom = null;
            for (BizWoBomSnapshot snap : snapshots) {
                BigDecimal required = snap.getRequiredQty() != null ? snap.getRequiredQty() : BigDecimal.ZERO;
                BigDecimal issued = snap.getIssuedQty() != null ? snap.getIssuedQty() : BigDecimal.ZERO;
                if (required.compareTo(BigDecimal.ZERO) <= 0) continue;
                // unitUsage = requiredQty / planQty, maxProducts = issuedQty / unitUsage = issuedQty * planQty / requiredQty
                BigDecimal maxProducts = planQty.compareTo(BigDecimal.ZERO) > 0
                        ? issued.multiply(planQty).divide(required, 2, RoundingMode.FLOOR)
                        : BigDecimal.ZERO;
                if (maxFromBom == null || maxProducts.compareTo(maxFromBom) < 0) {
                    maxFromBom = maxProducts;
                }
            }

            BigDecimal maxReportable = (maxFromBom != null ? maxFromBom : BigDecimal.ZERO).subtract(alreadyDone);
            result.put("maxReportableQty", maxReportable.max(BigDecimal.ZERO));
            result.put("reason", maxReportable.compareTo(BigDecimal.ZERO) <= 0
                    ? "已领物料不足以支撑更多报工，请先领料" : "根据BOM已领料计算");
        } else {
            // 非首道工序: 取上一组工序的最小合格数
            Integer currentGroup = task.getStepGroup();
            // 找上一组
            Integer prevGroup = allTasks.stream()
                    .map(BizWoProcessTask::getStepGroup)
                    .filter(g -> g != null && g < currentGroup)
                    .max(Integer::compareTo)
                    .orElse(null);
            if (prevGroup == null) {
                // 找不到上一组，按计划数限制
                BigDecimal remaining = (task.getPlanQty() != null ? task.getPlanQty() : BigDecimal.ZERO).subtract(alreadyDone);
                result.put("maxReportableQty", remaining.max(BigDecimal.ZERO));
                result.put("reason", "按计划数限制");
                return result;
            }
            BigDecimal prevMinQualified = allTasks.stream()
                    .filter(t -> prevGroup.equals(t.getStepGroup()))
                    .map(t -> t.getQualifiedQty() != null ? t.getQualifiedQty() : BigDecimal.ZERO)
                    .min(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);

            BigDecimal maxReportable = prevMinQualified.subtract(alreadyDone);

            // 如果前道是外协工序，还要检查收料物料库存
            BigDecimal outsourceStock = outsourceChainService.getMaxReportableQty(task);
            if (outsourceStock != null) {
                BigDecimal stockRemaining = outsourceStock.subtract(alreadyDone);
                result.put("outsourceStockQty", outsourceStock);
                result.put("outsourceStockRemaining", stockRemaining.max(BigDecimal.ZERO));
                if (stockRemaining.compareTo(maxReportable) < 0) {
                    maxReportable = stockRemaining;
                }
            }

            result.put("maxReportableQty", maxReportable.max(BigDecimal.ZERO));
            result.put("prevGroupQualified", prevMinQualified);
            if (outsourceStock != null && maxReportable.compareTo(BigDecimal.ZERO) <= 0) {
                result.put("reason", "外协收料物料库存不足，请等待外协收货入库");
            } else {
                result.put("reason", maxReportable.compareTo(BigDecimal.ZERO) <= 0
                    ? "报工数量已达上道工序合格数上限" : "不超过上道工序合格数(" + prevMinQualified + ")");
            }
        }
        return result;
    }

    /**
     * 校验报工前置: 工单存在未完成的领料环节 → 阻断报工
     * 没创建领料申请的工单(没BOM/不需领料的) 不阻断
     *
     * 阻断条件(满足任一):
     *   1. biz_inv_request: production_out 状态 ∈ (pending, approved) — 申请待审 / 审批通过但还没生成出库任务
     *   2. biz_inv_task: production_out + source=work_order + 工单ID + 状态 ∈ (pending, partial) — 任务已生成但没处理完
     */
    private void checkPendingMaterialRequest(Long workOrderId) {
        if (workOrderId == null) return;
        BizWorkOrder wo = workOrderMapper.selectById(workOrderId);
        if (wo == null || wo.getWoNo() == null) return;

        // 1. 申请单层: 还没生成任务的
        Long pendingRequest = invRequestMapper.selectCount(
                new LambdaQueryWrapper<BizInvRequest>()
                        .eq(BizInvRequest::getRelatedOrderNo, wo.getWoNo())
                        .eq(BizInvRequest::getInvType, "production_out")
                        .in(BizInvRequest::getStatus, java.util.Arrays.asList("pending", "approved"))
                        .eq(BizInvRequest::getDeleted, 0));
        if (pendingRequest != null && pendingRequest > 0) {
            throw new BusinessException("工单 " + wo.getWoNo() + " 有未通过的领料申请, 请先处理领料后再报工");
        }

        // 2. 任务层: 已生成但没处理完(pending 待处理 / partial 部分完成)的出库任务
        Long pendingTask = invTaskMapper.selectCount(
                new LambdaQueryWrapper<BizInvTask>()
                        .eq(BizInvTask::getInvType, "production_out")
                        .eq(BizInvTask::getSourceType, "work_order")
                        .eq(BizInvTask::getSourceId, workOrderId)
                        .in(BizInvTask::getStatus, java.util.Arrays.asList("pending", "partial"))
                        .eq(BizInvTask::getDeleted, 0));
        if (pendingTask != null && pendingTask > 0) {
            throw new BusinessException("工单 " + wo.getWoNo() + " 有未处理完的领料出库任务, 请仓库处理后再报工");
        }
    }

    /**
     * 校验报工数量限制
     * - 首道工序: 根据BOM已领料计算最大可报工数（可叠加工单超产比例）
     * - 非首道工序: 不能超过上道工序已报工合格数
     */
    private void checkReportLimit(Long workOrderId, BizWoProcessTask task, BigDecimal qualifiedQty, BigDecimal defectQty) {
        Map<String, Object> limit = getReportLimit(workOrderId, task.getId());
        BigDecimal maxReportableQty = new BigDecimal(limit.get("maxReportableQty").toString());

        // 读取工单超产比例配置：允许实际生产数量超出工单计划数量的最大比例
        String overRatioStr = bizConfigService.getValue("production.wo.over_produce_ratio");
        if (overRatioStr != null && !overRatioStr.isBlank()) {
            try {
                BigDecimal overRatio = new BigDecimal(overRatioStr);
                if (overRatio.compareTo(BigDecimal.ZERO) > 0) {
                    BizWorkOrder wo = workOrderMapper.selectById(workOrderId);
                    if (wo != null && wo.getPlanQty() != null && wo.getPlanQty().compareTo(BigDecimal.ZERO) > 0) {
                        // 最大允许生产量 = 计划数 × (1 + 超产比例%)
                        BigDecimal maxWithRatio = wo.getPlanQty()
                                .multiply(BigDecimal.ONE.add(overRatio.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)));
                        BigDecimal alreadyEffective2 = task.getQcTemplateId() != null
                                ? (task.getReportedQty() != null ? task.getReportedQty() : BigDecimal.ZERO)
                                : (task.getQualifiedQty() != null ? task.getQualifiedQty() : BigDecimal.ZERO);
                        BigDecimal alreadyDone = alreadyEffective2.add(task.getDefectQty() != null ? task.getDefectQty() : BigDecimal.ZERO);
                        BigDecimal remainingWithRatio = maxWithRatio.subtract(alreadyDone);
                        // 超产比例允许报工更多，取两者中较大值
                        maxReportableQty = maxReportableQty.max(remainingWithRatio.max(BigDecimal.ZERO));
                    }
                }
            } catch (NumberFormatException ignored) {}
        }

        BigDecimal totalReport = (qualifiedQty != null ? qualifiedQty : BigDecimal.ZERO)
                .add(defectQty != null ? defectQty : BigDecimal.ZERO);
        if (totalReport.compareTo(maxReportableQty) > 0) {
            boolean isFirst = Boolean.TRUE.equals(limit.get("isFirstGroup"));
            String msg = isFirst
                    ? "首道工序报工受BOM领料限制，最大可报工: " + maxReportableQty
                    : "非首道工序报工不能超过上道工序合格数，最大可报工: " + maxReportableQty;
            throw new BusinessException(msg);
        }
    }

    /**
     * 创建工序质检任务
     * @param reportQty 本次报工的合格数量（非累计）
     */
    private BizQcTask createProcessQcTask(Long workOrderId, BizWoProcessTask task, BigDecimal reportQty) {
        try {
            BizWorkOrder wo = workOrderMapper.selectById(workOrderId);
            BizQcTask qcTask = new BizQcTask();
            qcTask.setQcType("process");
            qcTask.setTemplateId(task.getQcTemplateId());
            qcTask.setProductId(wo != null ? wo.getProductId() : null);
            qcTask.setWorkOrderId(workOrderId);
            qcTask.setTaskId(task.getId());
            qcTask.setRelatedOrderType("work_order");
            qcTask.setRelatedOrderId(workOrderId);
            qcTask.setPlanQty(reportQty != null ? reportQty : task.getQualifiedQty());
            qcTaskService.createTask(qcTask);

            // 通知质检角色
            notificationService.notifyRole("quality",
                    "工序质检任务",
                    "工单工序[" + task.getStepName() + "]已完工，请安排工序质检",
                    "approval", "qc_task", qcTask.getId());

            log.info("工序质检任务已创建: workOrderId={}, taskId={}, qcTaskId={}",
                    workOrderId, task.getId(), qcTask.getId());
            return qcTask;
        } catch (Exception e) {
            log.warn("创建工序质检任务失败: workOrderId={}, taskId={}", workOrderId, task.getId(), e);
            return null;
        }
    }

    /**
     * 工序质检通过回调：检查该工序所有批次是否全部质检通过且数量已足，才标记完成
     * 支持分批报工场景：每批报工生成一个质检任务，所有批次通过后工序才完成
     *
     * @param taskId        工序任务ID
     * @param batchPassedQty 本批次通过的数量（用于外协链路触发）
     */
    @Transactional(rollbackFor = Exception.class)
    public void onProcessQcPassed(Long taskId, BigDecimal batchPassedQty) {
        BizWoProcessTask task = processTaskMapper.selectById(taskId);
        if (task == null) return;

        task.setQcStatus("pass");

        // 本批次质检通过 → 将批次合格数累入 qualifiedQty
        BigDecimal batchQty = batchPassedQty != null ? batchPassedQty : BigDecimal.ZERO;
        BigDecimal prevQualified = task.getQualifiedQty() != null ? task.getQualifiedQty() : BigDecimal.ZERO;
        task.setQualifiedQty(prevQualified.add(batchQty));

        // 检查是否还有未完成的质检批次（分批报工场景）
        // 使用 reportedQty（总报工量）判断数量是否已满，而非 qualifiedQty（仅质检通过部分）
        long pendingQcCount = qcTaskService.countPendingByProcessTask(task.getId());
        BigDecimal totalReported = task.getReportedQty() != null ? task.getReportedQty() : BigDecimal.ZERO;
        boolean quantityMet = task.getPlanQty() != null && totalReported.compareTo(task.getPlanQty()) >= 0;

        if (quantityMet && pendingQcCount == 0) {
            // 所有批次报工完成且全部质检通过 → 工序完成，激活下一组
            task.setStatus("completed");
            task.setEndTime(java.time.LocalDateTime.now());
            processTaskMapper.updateById(task);
            activateNextTask(task.getWorkOrderId(), task.getStepNo());
        } else {
            // 数量未足（后续批次还未报工）或仍有待检批次 → 回到 in_progress 继续
            task.setStatus("in_progress");
            processTaskMapper.updateById(task);
        }
        // 不论是否完成: 只要质检通过 batchQty>0, qualifiedQty 已经变了, 工单 completedQty 必须重算
        // 修复: 之前只在 if 分支(工序完工)调用同步, else 分支(部分完工)不调,
        //       导致"工序合格 19/20 in_progress"时工单 completedQty 永远是 0
        updateWorkOrderProgress(task.getWorkOrderId());

        // 任意工序质检通过(包括处置完后以 batchQty=0 触发的回调)→ 触发增量入库判断
        // minQualified 取的是所有工序合格数最小值,只要任一工序 qualifiedQty 上涨都可能拉起瓶颈,
        // 该方法内部会自行判断 delta 是否大于 0,不会重复入库。
        tryCreateLastStepStockIn(task.getWorkOrderId(), task.getStepNo());

        // 外协链路：按本批次合格数触发（分批场景每批独立入库）
        BigDecimal triggerQty = batchPassedQty != null && batchPassedQty.compareTo(BigDecimal.ZERO) > 0
                ? batchPassedQty : (task.getQualifiedQty() != null ? task.getQualifiedQty() : BigDecimal.ZERO);
        if (triggerQty.compareTo(BigDecimal.ZERO) > 0) {
            try {
                outsourceChainService.handlePrevStepReported(task.getWorkOrderId(), task, triggerQty);
            } catch (Exception e) {
                log.warn("质检通过后外协链路触发失败: taskId={}", taskId, e);
            }
        }
    }

    /**
     * 工序质检不通过回调
     * 若配置"不合格阻断流转"=true，将工序任务状态设为 blocked，阻断后续工序推进并通知生产
     */
    @Transactional(rollbackFor = Exception.class)
    public void onProcessQcFailed(Long taskId) {
        BizWoProcessTask task = processTaskMapper.selectById(taskId);
        if (task == null) return;

        // 质检存在不合格，标记待处置，但不阻断工序（工序继续可报工）
        task.setQcStatus("pending_disposal");
        // 不设置 blocked，工序保持当前状态继续可报工

        processTaskMapper.updateById(task);
    }
}
