package com.aimes.modules.dataimport.service.simulation;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.utils.CodeUtil;
import com.aimes.common.utils.ExcelUtil;
import com.aimes.modules.production.entity.BizWoProcessTask;
import com.aimes.modules.production.entity.BizWorkOrder;
import com.aimes.modules.production.mapper.BizWoProcessTaskMapper;
import com.aimes.modules.production.mapper.BizWorkOrderMapper;
import com.aimes.modules.system.entity.SysUser;
import com.aimes.modules.system.mapper.SysUserMapper;
import com.aimes.modules.team.entity.BizTeam;
import com.aimes.modules.team.entity.BizTeamMember;
import com.aimes.modules.team.mapper.BizTeamMapper;
import com.aimes.modules.team.mapper.BizTeamMemberMapper;
import com.aimes.modules.workreport.entity.BizTeamReportDetail;
import com.aimes.modules.workreport.entity.BizTeamWorkReport;
import com.aimes.modules.workreport.entity.BizWorkReport;
import com.aimes.modules.workreport.mapper.BizTeamReportDetailMapper;
import com.aimes.modules.workreport.mapper.BizTeamWorkReportMapper;
import com.aimes.modules.workreport.mapper.BizWorkReportMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static com.aimes.modules.dataimport.service.simulation.SimTimeHelper.*;

/**
 * ③ 报工记录生成器
 * 读取工序任务 → 根据指派方式生成:
 * - person指派: BizWorkReport(personal)
 * - team指派: BizTeamWorkReport + BizTeamReportDetail + BizWorkReport(team) per member
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WorkReportGenerator {

    private final BizWorkOrderMapper workOrderMapper;
    private final BizWoProcessTaskMapper processTaskMapper;
    private final BizWorkReportMapper workReportMapper;
    private final BizTeamWorkReportMapper teamWorkReportMapper;
    private final BizTeamReportDetailMapper teamReportDetailMapper;
    private final BizTeamMapper teamMapper;
    private final BizTeamMemberMapper teamMemberMapper;
    private final SysUserMapper userMapper;
    private final com.aimes.modules.workreport.mapper.BizWageRecordMapper wageRecordMapper;
    private final com.aimes.modules.quality.mapper.BizQcTaskMapper qcTaskMapper;
    private final com.aimes.modules.quality.mapper.BizQcTemplateMapper qcTemplateMapper;
    private final com.aimes.modules.quality.mapper.BizQcTemplateItemMapper qcTemplateItemMapper;
    private final com.aimes.modules.quality.mapper.BizQcResultMapper qcResultMapper;
    private final com.aimes.modules.quality.mapper.BizQcRecordMapper qcRecordMapper;
    private final com.aimes.modules.quality.mapper.BizQcDisposalMapper qcDisposalMapper;
    private final com.aimes.modules.quality.mapper.BizDefectRecordMapper defectRecordMapper;
    private final com.aimes.modules.production.mapper.BizReworkOrderMapper reworkOrderMapper;
    private final com.aimes.modules.production.mapper.BizReworkOrderStepMapper reworkOrderStepMapper;
    private final com.aimes.modules.order.mapper.BizSalesOrderItemMapper salesOrderItemMapper;
    private final com.aimes.modules.order.mapper.BizSalesOrderMapper salesOrderMapper;
    private final SalesOrderStatusHelper salesOrderStatusHelper;
    private final com.aimes.modules.mrp.mapper.BizProductionPlanItemMapper planItemMapper;
    private final com.aimes.modules.mrp.mapper.BizProductionPlanMapper planMapper;
    private final com.aimes.modules.master.mapper.BizMaterialMapper materialMapper;
    private final com.aimes.common.utils.BatchInsertHelper batchInsertHelper;
    private final JdbcTemplate jdbcTemplate;
    // 个人报工
    public static final List<String> REPORT_HEADERS = List.of(
            "报工编号", "工单编号(关联)", "工序序号(关联)", "报工人", "报工类型(personal/team)",
            "合格数量", "不良数量", "计件工资", "状态", "班组报工编号(关联)", "创建时间", "创建人");

    // 班组报工
    public static final List<String> TEAM_REPORT_HEADERS = List.of(
            "班组报工编号", "工单编号(关联)", "工序序号(关联)", "班组名称", "班组长",
            "合格数量", "不良数量", "总工资", "创建时间", "创建人");

    // 班组报工明细（成员分配）
    public static final List<String> TEAM_DETAIL_HEADERS = List.of(
            "班组报工编号(关联)", "成员姓名", "分配权重", "分配工资", "创建时间", "创建人");

    public static final List<String> WAGE_HEADERS = List.of(
            "报工编号(关联)", "工单编号(关联)", "工序序号(关联)", "员工姓名",
            "工资类型(personal/team_piece)", "金额", "报工日期", "创建时间", "创建人");

    // 质检任务（合并到报工模块，确保时间交替正确）
    public static final List<String> QC_TASK_HEADERS = List.of(
            "任务编号", "质检类型", "产品编号", "物料编号", "质检模板",
            "关联单据类型", "关联单据ID", "关联工单编号", "关联工序序号",
            "计划数量", "合格数量", "不合格数量", "结果", "检验员",
            "检验时间", "处置状态", "创建时间", "创建人");

    public static final List<String> QC_RECORD_HEADERS = List.of(
            "质检任务编号(关联)", "记录类型", "数量", "批次号", "报告人", "创建时间", "创建人");

    public static final List<String> QC_DISPOSAL_HEADERS = List.of(
            "处理编号", "质检任务编号(关联)", "工单编号", "工序名称", "物料编号",
            "数量", "处理类型(scrap/rework/concession)", "处理原因", "状态",
            "处理人", "处理时间", "创建时间", "创建人");

    public static final List<String> DEFECT_RECORD_HEADERS = List.of(
            "缺陷编号", "来源类型", "产品编号", "工单编号", "数量",
            "缺陷类型", "处理方式", "处理状态", "处理结果",
            "处理人", "处理时间", "创建时间", "创建人");

    public static final List<String> REWORK_ORDER_HEADERS = List.of(
            "返修编号", "质检任务编号(关联)", "处理单号(关联)", "工单编号(关联)",
            "工序名称", "产品编号", "数量", "状态", "创建时间", "创建人");

    public static final List<String> REWORK_STEP_HEADERS = List.of(
            "返修编号(关联)", "工序名称", "指派人", "排序号", "状态",
            "完成时间", "创建时间", "创建人");

    private List<List<Object>> cachedReportRows, cachedTeamReportRows, cachedTeamDetailRows, cachedWageRows;
    private List<List<Object>> cachedQcTaskRows, cachedQcRecordRows;
    private List<List<Object>> cachedDisposalRows, cachedDefectRows, cachedReworkOrderRows, cachedReworkStepRows;
    public Map<String, Object> generate(SimConfig cfg) {
        log.info("[Sim] ForkJoin commonPool 并行度 = {}",
                java.util.concurrent.ForkJoinPool.commonPool().getParallelism());
        Map<Long, String> userNames = loadAllUserNames();

        Map<Long, BizWorkOrder> woMap = workOrderMapper.selectList(
                new LambdaQueryWrapper<BizWorkOrder>().eq(BizWorkOrder::getDeleted, 0))
                .stream().collect(Collectors.toMap(BizWorkOrder::getId, w -> w, (a, b) -> a));

        // 查询所有非cancelled的工序任务（②工单导入时状态为pending，由本模块回写为completed）
        List<BizWoProcessTask> allTasks = processTaskMapper.selectList(
                new LambdaQueryWrapper<BizWoProcessTask>()
                        .eq(BizWoProcessTask::getDeleted, 0)
                        .ne(BizWoProcessTask::getStatus, "cancelled"));

        Map<Long, BizTeam> teamMap = teamMapper.selectList(
                new LambdaQueryWrapper<BizTeam>().eq(BizTeam::getDeleted, 0))
                .stream().collect(Collectors.toMap(BizTeam::getId, t -> t, (a, b) -> a));

        Map<Long, List<BizTeamMember>> teamMembersMap = teamMemberMapper.selectList(
                new LambdaQueryWrapper<BizTeamMember>().eq(BizTeamMember::getDeleted, 0))
                .stream().collect(Collectors.groupingBy(BizTeamMember::getTeamId));
        // 班组没成员时的兜底用户池: 从活跃用户里抓 80 个作候选,
        // 每个班组随机 1~12 人参与报工, 模拟真实班组规模的自然波动(独人班组 1 人, 大班组十几个人)
        List<Long> genFallbackUserIds = userMapper.selectList(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getDeleted, 0)
                        .eq(SysUser::getStatus, "active"))
                .stream().map(SysUser::getId).limit(80).collect(Collectors.toList());
        final int GEN_FAKE_TEAM_MIN = 1;
        final int GEN_FAKE_TEAM_MAX = 12;

        // 性能优化 — 提前一次性预加载主循环里反复访问的 Map,
        // 避免每工单/每工序在并行 forEach 里都打一次 DB(原来 N 个工单 = N 次 DB 查 product,
        // M 个 QC 工序 = M 次 DB 查 template,数千工单时是绝对的瓶颈)
        Map<Long, com.aimes.modules.master.entity.BizMaterial> productMap =
                materialMapper.selectList(new LambdaQueryWrapper<com.aimes.modules.master.entity.BizMaterial>()
                                .eq(com.aimes.modules.master.entity.BizMaterial::getDeleted, 0))
                        .stream().collect(Collectors.toMap(
                                com.aimes.modules.master.entity.BizMaterial::getId, m -> m, (a, b) -> a));
        Map<Long, String> qcTemplateNameMap =
                qcTemplateMapper.selectList(new LambdaQueryWrapper<com.aimes.modules.quality.entity.BizQcTemplate>()
                                .eq(com.aimes.modules.quality.entity.BizQcTemplate::getDeleted, 0))
                        .stream().collect(Collectors.toMap(
                                com.aimes.modules.quality.entity.BizQcTemplate::getId,
                                t -> t.getTemplateName() != null ? t.getTemplateName() : String.valueOf(t.getId()),
                                (a, b) -> a));

        // 多线程并行生成: 每个工单独立处理,工单间无共享可变状态(cursor 等都是工单内 local)
        // 共享的只有这几个 cached*Rows,用 synchronizedList 包一层保证 add 线程安全
        cachedReportRows = java.util.Collections.synchronizedList(new ArrayList<>());
        cachedTeamReportRows = java.util.Collections.synchronizedList(new ArrayList<>());
        cachedTeamDetailRows = java.util.Collections.synchronizedList(new ArrayList<>());
        cachedWageRows = java.util.Collections.synchronizedList(new ArrayList<>());
        cachedQcTaskRows = java.util.Collections.synchronizedList(new ArrayList<>());
        cachedQcRecordRows = java.util.Collections.synchronizedList(new ArrayList<>());
        cachedDisposalRows = java.util.Collections.synchronizedList(new ArrayList<>());
        cachedDefectRows = java.util.Collections.synchronizedList(new ArrayList<>());
        cachedReworkOrderRows = java.util.Collections.synchronizedList(new ArrayList<>());
        cachedReworkStepRows = java.util.Collections.synchronizedList(new ArrayList<>());
        // 按工单分组 → 每个工单独立任务,可并行
        Map<Long, List<BizWoProcessTask>> tasksByWo = allTasks.stream()
                .collect(Collectors.groupingBy(BizWoProcessTask::getWorkOrderId));

        tasksByWo.entrySet().parallelStream().forEach(entry -> {
            BizWorkOrder wo = woMap.get(entry.getKey());
            if (wo == null) return;

            List<BizWoProcessTask> tasks = entry.getValue();
            tasks.sort(Comparator.comparingInt(t -> t.getStepNo() != null ? t.getStepNo() : 0));

            // 时间游标：从工单创建时间开始，逐道工序递增
            LocalDateTime cursor = wo.getCreateTime() != null ? wo.getCreateTime().plusHours(2) : LocalDateTime.now().minusDays(10);
            String woNo = wo.getWoNo();
            // 从工单获取产品编号（用于质检任务的产品/物料关联）— 走预加载的 productMap,不再查 DB
            com.aimes.modules.master.entity.BizMaterial woProduct = wo.getProductId() != null
                    ? productMap.get(wo.getProductId()) : null;
            String productNo = woProduct != null && woProduct.getMaterialNo() != null ? woProduct.getMaterialNo() : "";

            for (int ti = 0; ti < tasks.size(); ti++) {
                BizWoProcessTask task = tasks.get(ti);

                BigDecimal planQty = task.getPlanQty() != null ? task.getPlanQty() : BigDecimal.ZERO;
                if (planQty.compareTo(BigDecimal.ZERO) <= 0) continue;

                String stepNo = task.getStepNo() != null ? String.valueOf(task.getStepNo()) : "1";
                boolean isTeam = "team".equals(task.getAssignType()) && task.getAssignedTeamId() != null;
                boolean needQc = task.getQcTemplateId() != null;

                // 创建人：个人报工=报工人，班组报工=班组长
                Long creatorId;
                if (isTeam) {
                    BizTeam team = teamMap.get(task.getAssignedTeamId());
                    creatorId = team != null ? team.getLeaderId() : cfg.pickCreator();
                } else {
                    creatorId = task.getAssignedUserId() != null ? task.getAssignedUserId() : cfg.pickCreator();
                }
                String creatorName = userNames.getOrDefault(creatorId, "");

                // === 计算分批报工次数和每次数量 ===
                int batchPct = cfg.randomReportBatchPct();
                BigDecimal batchQty = planQty.multiply(BigDecimal.valueOf(batchPct))
                        .divide(BigDecimal.valueOf(100), 0, RoundingMode.FLOOR)
                        .max(BigDecimal.ONE);
                int batchCount = planQty.divide(batchQty, 0, RoundingMode.CEILING).intValue();
                if (batchCount <= 0) batchCount = 1;

                // === 计算报工时间分配 ===
                // 硬约束：工单交货日期（不能超出）
                LocalDate woDelivery = wo.getDeliveryDate() != null ? wo.getDeliveryDate() :
                        (wo.getCreateTime() != null ? wo.getCreateTime().toLocalDate().plusDays(21) : LocalDate.now().plusDays(21));

                LocalDate stepStart = SimTimeHelper.snapToWorkingDay(cursor.toLocalDate(), cfg);
                // 如果cursor已超出交货日期，压缩到交货日期范围内
                if (stepStart.isAfter(woDelivery)) stepStart = woDelivery;

                LocalDate stepEnd = task.getEndTime() != null ? task.getEndTime().toLocalDate() : woDelivery;
                // 工序结束不能超过工单交货日期
                if (stepEnd.isAfter(woDelivery)) stepEnd = woDelivery;
                if (stepEnd.isBefore(stepStart)) stepEnd = stepStart;

                List<LocalDate> stepWorkDays = SimTimeHelper.getWorkingDays(stepStart, stepEnd, cfg);
                if (stepWorkDays.isEmpty()) {
                    // 没有工作日，直接用stepStart
                    stepWorkDays = List.of(stepStart);
                }
                // 截止日过滤：超过 cutoffDate 的工作日不生成报工
                // 同时:今天若还没到报工时间窗口,也不生成今天的报工
                LocalDate cutoff = cfg.getCutoffDate();
                stepWorkDays = stepWorkDays.stream()
                        .filter(d -> cutoff == null || !d.isAfter(cutoff))
                        .filter(cfg::isReportableDay)
                        .collect(java.util.stream.Collectors.toList());
                if (stepWorkDays.isEmpty()) {
                    // 整道工序都在截止日之后 → 跳过本工序及后续工序的所有报工
                    // 后续工序时间游标只会更晚，全部留给"未来"，工单状态停留在 pending/partial
                    return;
                }

                // 分段：每段的工作日
                int segLen = Math.max(1, stepWorkDays.size() / batchCount);

                BigDecimal remainQty = planQty;
                for (int batch = 0; batch < batchCount; batch++) {
                    BigDecimal thisQty = (batch == batchCount - 1) ? remainQty : batchQty.min(remainQty);
                    if (thisQty.compareTo(BigDecimal.ZERO) <= 0) break;
                    remainQty = remainQty.subtract(thisQty);

                    // 报工日期：在对应段内随机选一天
                    int segStart = Math.min(batch * segLen, stepWorkDays.size() - 1);
                    int segEnd = (batch == batchCount - 1) ? stepWorkDays.size() - 1 : Math.min(segStart + segLen - 1, stepWorkDays.size() - 1);
                    if (segEnd < segStart) segEnd = segStart; // 防止越界
                    LocalDate reportDate;
                    if (batch == batchCount - 1) {
                        reportDate = stepWorkDays.get(stepWorkDays.size() - 1);
                    } else if (segStart >= segEnd) {
                        reportDate = stepWorkDays.get(segStart);
                    } else {
                        int idx = java.util.concurrent.ThreadLocalRandom.current().nextInt(segStart, segEnd + 1);
                        reportDate = stepWorkDays.get(idx);
                    }
                    LocalDateTime reportTime = cfg.randomReportTime(reportDate);
                    if (reportTime == null || !reportTime.isAfter(cursor)) {
                        // 优先 bump 到下一工作日
                        LocalDate bumpedDate = SimTimeHelper.addWorkingDays(reportDate, 1, cfg);
                        LocalDateTime bumpedTime = (cutoff == null || !bumpedDate.isAfter(cutoff))
                                ? cfg.randomReportTime(bumpedDate) : null;
                        if (bumpedTime != null && bumpedTime.isAfter(cursor) && cfg.isReportableDay(bumpedDate)) {
                            reportDate = bumpedDate;
                            reportTime = bumpedTime;
                        } else {
                            // bump 跨过 cutoff / 落空 → 退回到 cursor 当天硬塞:
                            // cursor 之后随机 1~30 分钟,只要仍在当天报工窗口和 cutoff 内就用,避免直接 return 把后续工序全废掉
                            LocalDate fallbackDate = cursor.toLocalDate();
                            LocalDateTime fallbackTime = cursor.plusMinutes(
                                    java.util.concurrent.ThreadLocalRandom.current().nextInt(1, 31));
                            LocalDateTime dayEnd = fallbackDate.atTime(cfg.getEndHour(), cfg.getEndMin());
                            boolean dateOk = (cutoff == null || !fallbackDate.isAfter(cutoff))
                                    && cfg.isReportableDay(fallbackDate);
                            if (dateOk && fallbackTime.isBefore(dayEnd)
                                    && (fallbackDate.isBefore(LocalDate.now())
                                        || fallbackTime.isBefore(LocalDateTime.now()))) {
                                reportDate = fallbackDate;
                                reportTime = fallbackTime;
                            }
                        }
                    }
                    // 二次截止日校验：bump 后可能跨过截止日,或 bump 到今天但今天还没进报工窗口
                    if (reportTime == null || !cfg.isReportableDay(reportDate)
                            || (cutoff != null && reportDate.isAfter(cutoff))) {
                        // 该批及后续工序都生成不了 → 跳出整个工单的工序循环
                        return;
                    }

                    // 提前把 QC 检验时间算出来,null 则整条报工不生成,避免孤儿报工(有 WR 但没 QC)
                    // 下道工序时间(nextCursor)不在这里预检查 — 对最后一批来说它本来就可以为 null
                    LocalDateTime qcInspectedAtPre = null;
                    if (needQc) {
                        qcInspectedAtPre = cfg.calcQcTime(reportTime);
                        if (qcInspectedAtPre == null) return;
                    }

                    BigDecimal wage = null;
                    if (task.getPiecePrice() != null) {
                        wage = task.getPiecePrice().multiply(thisQty).setScale(2, RoundingMode.HALF_UP);
                    }

                    // === 生成报工记录 ===
                    // 班组指派也按"个人报工"方式生成 — 每个班组成员各写一条 personal 类型 BizWorkReport,
                    // 整数 qty 由 floor 平均分配 + 余数 +1 保证 sum = thisQty
                    // 不再写 biz_team_work_report / biz_team_report_detail (跟工位机口径一致)
                    if (isTeam) {
                        BizTeam team = teamMap.get(task.getAssignedTeamId());
                        Long leaderId = team != null ? team.getLeaderId() : null;

                        List<BizTeamMember> members = new ArrayList<>(
                                teamMembersMap.getOrDefault(task.getAssignedTeamId(), List.of()));
                        if (members.isEmpty()) {
                            // 班组没真实成员: leader + 从活跃用户池抓凑齐 5~8 人参与报工
                            int targetSize = java.util.concurrent.ThreadLocalRandom.current()
                                    .nextInt(GEN_FAKE_TEAM_MIN, GEN_FAKE_TEAM_MAX + 1);
                            Set<Long> usedIds = new HashSet<>();
                            if (leaderId != null) {
                                members.add(createFakeMember(task.getAssignedTeamId(), leaderId, BigDecimal.ONE));
                                usedIds.add(leaderId);
                            }
                            // 候选池随机打散后再取, 不同班组不会总抓到同一拨人
                            List<Long> shuffled = new ArrayList<>(genFallbackUserIds);
                            java.util.Collections.shuffle(shuffled);
                            for (Long uid : shuffled) {
                                if (members.size() >= targetSize) break;
                                if (usedIds.contains(uid)) continue;
                                members.add(createFakeMember(task.getAssignedTeamId(), uid, BigDecimal.ONE));
                                usedIds.add(uid);
                            }
                        }
                        // 1) 本次实际参与报工的成员数 — 不一定全员上, 随机抽取
                        //    最少 max(1, 40% 班组人数), 最多全员;
                        //    但至多 thisQty 个人 (避免人数 > 件数, 那样会有人 0 件)
                        int memberCount = members.size();
                        int minParticipants = Math.max(1, (int) Math.ceil(memberCount * 0.4));
                        int maxParticipants = Math.min(memberCount, Math.max(1, thisQty.intValue()));
                        if (minParticipants > maxParticipants) minParticipants = maxParticipants;
                        int participants = java.util.concurrent.ThreadLocalRandom.current()
                                .nextInt(minParticipants, maxParticipants + 1);

                        // 2) 从班组成员里随机抽 participants 个 (洗牌后取前 N)
                        List<BizTeamMember> participating = new ArrayList<>(members);
                        java.util.Collections.shuffle(participating);
                        participating = participating.subList(0, participants);

                        // 3) qty 分配: base = floor(thisQty / participants), 前 remainder 个 +1
                        //    sum(memberQty) = thisQty, 抽中的人最少 1 件 (没抽中的根本不写报工)
                        BigDecimal baseQty = thisQty.divide(BigDecimal.valueOf(participants), 0, RoundingMode.FLOOR);
                        int remainder = thisQty.subtract(baseQty.multiply(BigDecimal.valueOf(participants))).intValue();
                        List<int[]> reporterIdx = new ArrayList<>();   // [participatingIndex, memberQty]
                        for (int mi = 0; mi < participants; mi++) {
                            int mq = baseQty.intValue() + (mi < remainder ? 1 : 0);
                            if (mq > 0) reporterIdx.add(new int[]{mi, mq});
                        }

                        for (int[] memEntry : reporterIdx) {
                            int mi = memEntry[0];
                            BigDecimal memberQty = BigDecimal.valueOf(memEntry[1]);
                            BizTeamMember member = participating.get(mi);
                            String memberName = userNames.getOrDefault(member.getUserId(), String.valueOf(member.getUserId()));
                            // 每人工资 = 自己合格数 × 计件单价 (跟工位机一致, 不再按 weight 分摊)
                            BigDecimal memberWage = BigDecimal.ZERO;
                            if (task.getPiecePrice() != null) {
                                memberWage = memberQty.multiply(task.getPiecePrice()).setScale(2, RoundingMode.HALF_UP);
                            }

                            String memberReportNo = CodeUtil.generate("WR");
                            if (memberWage.compareTo(BigDecimal.ZERO) > 0) {
                                cachedWageRows.add(List.of(memberReportNo, woNo, stepNo, memberName, "personal",
                                        memberWage.toPlainString(), fmtDate(reportTime.toLocalDate()), fmtDateTime(reportTime), creatorName));
                            }
                            // 类型 personal, 班组报工编号字段留空(不再关联 team_work_report)
                            cachedReportRows.add(List.of(memberReportNo, woNo, stepNo, memberName, "personal",
                                    memberQty.toPlainString(), "0", memberWage.toPlainString(),
                                    "submitted", "", fmtDateTime(reportTime), creatorName));
                        }
                    } else {
                        Long reporterId = task.getAssignedUserId();
                        String reporterName = reporterId != null ? userNames.getOrDefault(reporterId, "") : "";
                        if (reporterName.isEmpty()) reporterName = task.getAssignedUserName() != null ? task.getAssignedUserName() : "";
                        // 如果工序没有指派人，使用检验员作为备选报工人
                        if (reporterName.isEmpty()) {
                            Long fallbackId = cfg.pickInspector();
                            if (fallbackId == null) fallbackId = cfg.pickCreator();
                            reporterName = fallbackId != null ? userNames.getOrDefault(fallbackId, "系统") : "系统";
                        }

                        String personalReportNo = CodeUtil.generate("WR");
                        cachedReportRows.add(List.of(personalReportNo, woNo, stepNo, reporterName, "personal",
                                thisQty.toPlainString(), "0", wage != null ? wage.toPlainString() : "",
                                "submitted", "", fmtDateTime(reportTime), creatorName));

                        if (wage != null && wage.compareTo(BigDecimal.ZERO) > 0) {
                            cachedWageRows.add(List.of(personalReportNo, woNo, stepNo, reporterName, "personal",
                                    wage.toPlainString(), fmtDate(reportTime.toLocalDate()), fmtDateTime(reportTime), creatorName));
                        }
                    }

                    // === 质检（如果工序配置了质检模板） ===
                    // 无质检时 nextCursor 走 reportTime + delay;有质检时走 qcInspectedAt + delay
                    // null 的处理放到批次末尾(只有还有后续批次/工序时才 break)
                    boolean isFinalBatch = (batch == batchCount - 1) && (ti == tasks.size() - 1);
                    LocalDateTime nextCursor;
                    if (!needQc) {
                        nextCursor = cfg.calcNextStepTime(reportTime);
                        if (nextCursor == null) {
                            if (!isFinalBatch) return;
                            nextCursor = reportTime; // 最后一批,cursor 无后续影响
                        }
                    } else {
                        nextCursor = null; // 下面质检块里赋值
                    }

                    if (needQc) {
                        // 质检创建时间 = 报工时间（报工完成自动生成质检任务）
                        LocalDateTime qcCreateTime = reportTime;
                        // 质检检验时间 = 报工时间 + 质检延迟(已预计算并保证非 null)
                        LocalDateTime qcInspectedAt = qcInspectedAtPre;

                        boolean pass = cfg.isQcPass();
                        BigDecimal qcPassQty, qcFailQty;
                        if (pass) {
                            qcPassQty = thisQty;
                            qcFailQty = BigDecimal.ZERO;
                        } else {
                            double pctRate = cfg.randomPassQtyRate();
                            qcPassQty = thisQty.multiply(BigDecimal.valueOf(pctRate)).setScale(0, RoundingMode.FLOOR);
                            qcFailQty = thisQty.subtract(qcPassQty);
                            if (qcFailQty.compareTo(BigDecimal.ZERO) <= 0) { qcFailQty = BigDecimal.ONE; qcPassQty = thisQty.subtract(BigDecimal.ONE); }
                        }
                        // result 表示质检结论(pass/fail)，generateQcResults 会读这个判断 actualValue
                        String qcResult = pass ? "pass" : "fail";

                        Long inspectorId = cfg.pickInspector();
                        String inspectorName = userNames.getOrDefault(inspectorId, "");
                        String qcTaskNo = CodeUtil.generate("QC");
                        // 质检模板：ID→名称 — 走预加载的 qcTemplateNameMap,不再查 DB
                        String templateName = "";
                        if (task.getQcTemplateId() != null) {
                            templateName = qcTemplateNameMap.getOrDefault(
                                    task.getQcTemplateId(), String.valueOf(task.getQcTemplateId()));
                        }

                        cachedQcTaskRows.add(List.of(qcTaskNo, "process", productNo, productNo,
                                templateName,  // 质检模板
                                "work_order", woNo,  // 关联单据类型, 关联单据ID(用工单编号)
                                woNo, stepNo,
                                thisQty.toPlainString(), qcPassQty.toPlainString(), qcFailQty.toPlainString(),
                                qcResult, inspectorName,
                                fmtDateTime(qcInspectedAt),   // 检验时间
                                pass ? "" : "undisposed",  // 全合格无需处置，不合格待处置
                                fmtDateTime(qcCreateTime),    // 创建时间=报工时间
                                creatorName));

                        // 合格数 > 0 才生成合格记录;不合格数 > 0 才生成不合格记录
                        if (qcPassQty.compareTo(BigDecimal.ZERO) > 0) {
                            cachedQcRecordRows.add(List.of(qcTaskNo, "qualified", qcPassQty.toPlainString(), "",
                                    inspectorName, fmtDateTime(qcInspectedAt), creatorName));
                        }
                        if (qcFailQty.compareTo(BigDecimal.ZERO) > 0) {
                            cachedQcRecordRows.add(List.of(qcTaskNo, "unqualified", qcFailQty.toPlainString(), "",
                                    inspectorName, fmtDateTime(qcInspectedAt), creatorName));
                        }

                        nextCursor = cfg.calcNextStepTime(qcInspectedAt);
                        if (nextCursor == null) {
                            if (!isFinalBatch) return;
                            nextCursor = qcInspectedAt; // 最后一批,cursor 无后续影响
                        }

                        if (!pass) {
                            String disposalType = cfg.randomDisposalType();
                            // 处置数量始终 = 不合格数量(只处置不合格的部分,合格的不动)
                            BigDecimal disposalQty = qcFailQty;
                            LocalDateTime disposalTime = cfg.calcNextStepTime(qcInspectedAt);
                            if (disposalTime == null) return;

                            Long handlerId = inspectorId;
                            String handlerName = inspectorName;
                            String disposalNo = CodeUtil.generate("QD");

                            cachedDisposalRows.add(List.of(disposalNo, qcTaskNo, woNo,
                                    task.getStepName() != null ? task.getStepName() : "", productNo,
                                    disposalQty.toPlainString(), disposalType, "质检不合格-" + disposalType,
                                    "processed", handlerName, fmtDateTime(disposalTime), fmtDateTime(disposalTime), creatorName));

                            String defectNo = CodeUtil.generate("DF");
                            String handlePath = "rework".equals(disposalType) ? "repair" : disposalType;
                            cachedDefectRows.add(List.of(defectNo, "qc_task", productNo, woNo,
                                    qcFailQty.toPlainString(), "quality_issue", handlePath, "completed", handlePath,
                                    handlerName, fmtDateTime(disposalTime), fmtDateTime(disposalTime), creatorName));

                            if ("rework".equals(disposalType)) {
                                String reworkNo = CodeUtil.generate("RW");
                                LocalDateTime reworkTime = disposalTime.plusHours(1);
                                if (reworkTime.isAfter(LocalDateTime.now())) return;
                                Long reworkAssignee = cfg.pickAssignee();
                                String reworkAssigneeName = userNames.getOrDefault(reworkAssignee, "");

                                cachedReworkOrderRows.add(List.of(reworkNo, qcTaskNo, disposalNo, woNo,
                                        task.getStepName() != null ? task.getStepName() : "", productNo,
                                        disposalQty.toPlainString(), "completed", fmtDateTime(reworkTime), creatorName));

                                LocalDateTime stepComplete = cfg.calcNextStepTime(reworkTime);
                                if (stepComplete == null) return;
                                cachedReworkStepRows.add(List.of(reworkNo,
                                        task.getStepName() != null ? task.getStepName() : "返修",
                                        reworkAssigneeName, "1", "completed",
                                        fmtDateTime(stepComplete), fmtDateTime(reworkTime), creatorName));

                                nextCursor = stepComplete;
                            }
                        }
                    }

                    // cursor不能超过工单交货日期（硬约束）
                    LocalDateTime deliveryLimit = woDelivery.atTime(cfg.getEndHour(), cfg.getEndMin());
                    cursor = nextCursor.isAfter(deliveryLimit) ? deliveryLimit.minusHours(1) : nextCursor;
                } // end for each batch

            } // end for each task in work order
        }); // end parallel forEach over work orders

        Map<String, Integer> tables = new LinkedHashMap<>();
        tables.put("work_report", cachedReportRows.size());
        tables.put("team_work_report", cachedTeamReportRows.size());
        tables.put("team_report_detail", cachedTeamDetailRows.size());
        tables.put("wage_record", cachedWageRows.size());
        tables.put("qc_task", cachedQcTaskRows.size());
        tables.put("qc_record", cachedQcRecordRows.size());
        tables.put("qc_disposal", cachedDisposalRows.size());
        tables.put("defect_record", cachedDefectRows.size());
        tables.put("rework_order", cachedReworkOrderRows.size());
        tables.put("rework_step", cachedReworkStepRows.size());
        int total = cachedReportRows.size() + cachedTeamReportRows.size() + cachedTeamDetailRows.size()
                + cachedWageRows.size() + cachedQcTaskRows.size() + cachedQcRecordRows.size()
                + cachedDisposalRows.size() + cachedDefectRows.size()
                + cachedReworkOrderRows.size() + cachedReworkStepRows.size();
        return Map.of("tables", tables, "totalRows", total);
    }

    public void exportZip(HttpServletResponse response) throws IOException {
        if (cachedReportRows == null) throw new BusinessException("请先执行生成");

        response.setContentType("application/zip");
        response.setHeader("Content-Disposition",
                "attachment; filename=" + java.net.URLEncoder.encode("03_报工记录.zip", "UTF-8"));

        List<SimTimeHelper.SheetSpec> sheets = new ArrayList<>();
        sheets.add(new SimTimeHelper.SheetSpec("03_报工记录_work_report.xlsx", REPORT_HEADERS, cachedReportRows));
        if (!cachedTeamReportRows.isEmpty()) {
            sheets.add(new SimTimeHelper.SheetSpec("03_报工记录_team_work_report.xlsx", TEAM_REPORT_HEADERS, cachedTeamReportRows));
            sheets.add(new SimTimeHelper.SheetSpec("03_报工记录_team_report_detail.xlsx", TEAM_DETAIL_HEADERS, cachedTeamDetailRows));
        }
        if (!cachedWageRows.isEmpty()) {
            sheets.add(new SimTimeHelper.SheetSpec("03_报工记录_wage_record.xlsx", WAGE_HEADERS, cachedWageRows));
        }
        if (!cachedQcTaskRows.isEmpty()) {
            sheets.add(new SimTimeHelper.SheetSpec("03_报工记录_qc_task.xlsx", QC_TASK_HEADERS, cachedQcTaskRows));
            sheets.add(new SimTimeHelper.SheetSpec("03_报工记录_qc_record.xlsx", QC_RECORD_HEADERS, cachedQcRecordRows));
        }
        if (!cachedDisposalRows.isEmpty()) {
            sheets.add(new SimTimeHelper.SheetSpec("03_报工记录_qc_disposal.xlsx", QC_DISPOSAL_HEADERS, cachedDisposalRows));
            sheets.add(new SimTimeHelper.SheetSpec("03_报工记录_defect_record.xlsx", DEFECT_RECORD_HEADERS, cachedDefectRows));
        }
        if (!cachedReworkOrderRows.isEmpty()) {
            sheets.add(new SimTimeHelper.SheetSpec("03_报工记录_rework_order.xlsx", REWORK_ORDER_HEADERS, cachedReworkOrderRows));
            sheets.add(new SimTimeHelper.SheetSpec("03_报工记录_rework_step.xlsx", REWORK_STEP_HEADERS, cachedReworkStepRows));
        }

        try (var zos = new java.util.zip.ZipOutputStream(response.getOutputStream())) {
            SimTimeHelper.parallelSplitWriteToZip(zos, sheets, 50_000, 10);
        }
    }

    @Transactional
    public Map<String, Object> importData(MultipartFile[] reportFiles, MultipartFile teamReportFile,
                                          MultipartFile[] teamDetailFiles, MultipartFile[] wageFiles,
                                          MultipartFile qcTaskFile, MultipartFile[] qcRecordFiles,
                                          MultipartFile disposalFile, MultipartFile defectFile,
                                          MultipartFile reworkOrderFile, MultipartFile reworkStepFile) {
        // 多线程: 段间顺序保留(后续段依赖前段产生的 id 映射),段内行级并行
        java.util.concurrent.atomic.AtomicInteger success = new java.util.concurrent.atomic.AtomicInteger();
        List<String> errors = java.util.Collections.synchronizedList(new ArrayList<>());

        Map<String, Long> userNameToId = loadUserNameToIdMap();
        Map<String, Long> woNoToId = workOrderMapper.selectList(
                new LambdaQueryWrapper<BizWorkOrder>().eq(BizWorkOrder::getDeleted, 0))
                .stream().collect(Collectors.toMap(BizWorkOrder::getWoNo, BizWorkOrder::getId, (a, b) -> a));
        Map<String, Long> teamNameToId = teamMapper.selectList(
                new LambdaQueryWrapper<BizTeam>().eq(BizTeam::getDeleted, 0))
                .stream().collect(Collectors.toMap(BizTeam::getTeamName, BizTeam::getId, (a, b) -> a));

        // 性能关键: 把循环内反复执行的 selectList 提到顶部一次性预加载,
        // 避免几万行 × 多段都重新拉全表(原 N+1 query 是导入慢的最大根因)
        Map<String, Long> matNoToId = materialMapper.selectList(
                new LambdaQueryWrapper<com.aimes.modules.master.entity.BizMaterial>()
                        .eq(com.aimes.modules.master.entity.BizMaterial::getDeleted, 0))
                .stream().filter(m -> m.getMaterialNo() != null)
                .collect(Collectors.toMap(com.aimes.modules.master.entity.BizMaterial::getMaterialNo,
                        com.aimes.modules.master.entity.BizMaterial::getId, (a, b) -> a));
        Map<String, Long> tplNameToId = qcTemplateMapper.selectList(
                new LambdaQueryWrapper<com.aimes.modules.quality.entity.BizQcTemplate>()
                        .eq(com.aimes.modules.quality.entity.BizQcTemplate::getDeleted, 0))
                .stream().filter(t -> t.getTemplateName() != null)
                .collect(Collectors.toMap(com.aimes.modules.quality.entity.BizQcTemplate::getTemplateName,
                        com.aimes.modules.quality.entity.BizQcTemplate::getId, (a, b) -> a));
        // taskId → assignedUserId(报工段 reporter 兜底用,避免循环内 selectById)
        Map<Long, Long> taskIdToAssignedUser = processTaskMapper.selectList(
                new LambdaQueryWrapper<BizWoProcessTask>().eq(BizWoProcessTask::getDeleted, 0))
                .stream().filter(t -> t.getId() != null && t.getAssignedUserId() != null)
                .collect(Collectors.toMap(BizWoProcessTask::getId, BizWoProcessTask::getAssignedUserId, (a, b) -> a));

        // woNo+stepNo → taskId — 一次 IN 查询拉全部工序任务,内存里反查 woNo,
        // 替代原先按工单逐个 selectList 的 N+1 模式(几千工单 = 几千次 DB 调用,
        // 远程 MySQL 场景下会撑爆连接池 + 被中间网络设备 RST)。
        Map<String, Long> taskKeyToId = new java.util.concurrent.ConcurrentHashMap<>();
        if (!woNoToId.isEmpty()) {
            // woId → woNo 反查
            Map<Long, String> woIdToNo = woNoToId.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey, (a, b) -> a));
            // IN 查询批量拉所有工序任务 — 1 次 SQL
            List<BizWoProcessTask> allTasks = processTaskMapper.selectList(
                    new LambdaQueryWrapper<BizWoProcessTask>()
                            .in(BizWoProcessTask::getWorkOrderId, woIdToNo.keySet())
                            .eq(BizWoProcessTask::getDeleted, 0));
            for (BizWoProcessTask t : allTasks) {
                String woNo = woIdToNo.get(t.getWorkOrderId());
                if (woNo != null && t.getStepNo() != null) {
                    taskKeyToId.put(woNo + "_" + t.getStepNo(), t.getId());
                }
            }
        }

        // 1. 导入班组报工（如果有）
        Map<String, Long> teamReportNoToId = new java.util.concurrent.ConcurrentHashMap<>();
        if (teamReportFile != null && !teamReportFile.isEmpty()) {
            List<Map<String, String>> rows = ExcelUtil.parseImport(teamReportFile, TEAM_REPORT_HEADERS);
            java.util.stream.IntStream.range(0, rows.size()).parallel().forEach(i -> {
                Map<String, String> r = rows.get(i);
                try {
                    Long woId = woNoToId.get(r.get("工单编号(关联)"));
                    if (woId == null) { errors.add("team_work_report第" + (i + 2) + "行: 工单不存在"); return; }
                    Long taskId = taskKeyToId.get(r.get("工单编号(关联)") + "_" + r.get("工序序号(关联)"));

                    BizTeamWorkReport tr = new BizTeamWorkReport();
                    tr.setReportNo(r.get("班组报工编号"));
                    tr.setWorkOrderId(woId);
                    tr.setTaskId(taskId);
                    tr.setTeamId(teamNameToId.get(r.get("班组名称")));
                    tr.setLeaderId(userNameToId.get(r.get("班组长")));
                    tr.setQualifiedQty(parseBD(r.get("合格数量")));
                    tr.setDefectQty(parseBD(r.get("不良数量")));
                    tr.setTotalWage(parseBD(r.get("总工资")));
                    tr.setCreateTime(parseDatetime(r.get("创建时间")));
                    tr.setCreateBy(userNameToId.get(r.get("创建人")));
                    tr.setUpdateTime(tr.getCreateTime());
                    tr.setUpdateBy(tr.getCreateBy());
                    teamWorkReportMapper.insert(tr);
                    teamReportNoToId.put(tr.getReportNo(), tr.getId());
                    success.incrementAndGet();
                } catch (Exception e) {
                    errors.add("team_work_report第" + (i + 2) + "行: " + e.getMessage());
                }
            });
        }

        // 2. 导入班组明细（如果有）
        List<Map<String, String>> teamDetailAllRows = parseAllParts(teamDetailFiles, TEAM_DETAIL_HEADERS);
        if (!teamDetailAllRows.isEmpty()) {
            List<Map<String, String>> rows = teamDetailAllRows;
            java.util.stream.IntStream.range(0, rows.size()).parallel().forEach(i -> {
                Map<String, String> r = rows.get(i);
                try {
                    Long teamReportId = teamReportNoToId.get(r.get("班组报工编号(关联)"));
                    if (teamReportId == null) { errors.add("team_detail第" + (i + 2) + "行: 班组报工不存在"); return; }

                    BizTeamReportDetail d = new BizTeamReportDetail();
                    d.setTeamReportId(teamReportId);
                    Long memberUserId = userNameToId.get(r.get("成员姓名"));
                    if (memberUserId == null) memberUserId = 1L;
                    d.setUserId(memberUserId);
                    d.setWeight(parseBD(r.get("分配权重")));
                    d.setAllocatedWage(parseBD(r.get("分配工资")));
                    d.setCreateTime(parseDatetime(r.get("创建时间")));
                    d.setCreateBy(userNameToId.get(r.get("创建人")));
                    d.setUpdateTime(d.getCreateTime());
                    d.setUpdateBy(d.getCreateBy());
                    teamReportDetailMapper.insert(d);
                    success.incrementAndGet();
                } catch (Exception e) {
                    errors.add("team_detail第" + (i + 2) + "行: " + e.getMessage());
                }
            });
        }

        // 3. 导入个人报工记录
        List<Map<String, String>> reportAllRows = parseAllParts(reportFiles, REPORT_HEADERS);
        if (!reportAllRows.isEmpty()) {
            List<Map<String, String>> rows = reportAllRows;
            java.util.stream.IntStream.range(0, rows.size()).parallel().forEach(i -> {
                Map<String, String> r = rows.get(i);
                try {
                    Long woId = woNoToId.get(r.get("工单编号(关联)"));
                    if (woId == null) { errors.add("work_report第" + (i + 2) + "行: 工单不存在"); return; }
                    Long taskId = taskKeyToId.get(r.get("工单编号(关联)") + "_" + r.get("工序序号(关联)"));

                    BizWorkReport rpt = new BizWorkReport();
                    rpt.setReportNo(r.get("报工编号"));
                    rpt.setWorkOrderId(woId);
                    rpt.setTaskId(taskId);
                    Long reporterId = userNameToId.get(r.get("报工人"));
                    if (reporterId == null) {
                        // 报工人名称找不到时,从预加载的 taskId→assignedUserId 映射兜底
                        if (taskId != null) reporterId = taskIdToAssignedUser.get(taskId);
                        if (reporterId == null) reporterId = 1L; // 最终兜底:默认管理员
                    }
                    rpt.setReporterId(reporterId);
                    rpt.setReportType(r.get("报工类型(personal/team)"));
                    rpt.setQualifiedQty(parseBD(r.get("合格数量")));
                    rpt.setDefectQty(parseBD(r.get("不良数量")));
                    rpt.setPieceWage(parseBD(r.get("计件工资")));
                    rpt.setStatus(r.get("状态"));

                    // 班组报工关联
                    String teamRefNo = r.get("班组报工编号(关联)");
                    if (teamRefNo != null && !teamRefNo.isEmpty()) {
                        rpt.setTeamReportId(teamReportNoToId.get(teamRefNo));
                    }

                    rpt.setCreateTime(parseDatetime(r.get("创建时间")));
                    rpt.setCreateBy(userNameToId.get(r.get("创建人")));
                    rpt.setUpdateTime(rpt.getCreateTime());
                    rpt.setUpdateBy(rpt.getCreateBy());
                    workReportMapper.insert(rpt);
                    success.incrementAndGet();
                } catch (Exception e) {
                    errors.add("work_report第" + (i + 2) + "行: " + e.getMessage());
                }
            });
        }

        // 4. 导入工资记录
        List<Map<String, String>> wageAllRows = parseAllParts(wageFiles, WAGE_HEADERS);
        if (!wageAllRows.isEmpty()) {
            List<Map<String, String>> wageRows = wageAllRows;
            Map<String, Long> reportNoToId = workReportMapper.selectList(
                    new LambdaQueryWrapper<BizWorkReport>().eq(BizWorkReport::getDeleted, 0))
                    .stream().collect(Collectors.toMap(BizWorkReport::getReportNo, BizWorkReport::getId, (a, b) -> a));

            java.util.stream.IntStream.range(0, wageRows.size()).parallel().forEach(i -> {
                Map<String, String> r = wageRows.get(i);
                try {
                    com.aimes.modules.workreport.entity.BizWageRecord wr = new com.aimes.modules.workreport.entity.BizWageRecord();
                    Long wageUserId = userNameToId.get(r.get("员工姓名"));
                    if (wageUserId == null) wageUserId = 1L; // 兜底
                    wr.setUserId(wageUserId);
                    wr.setWorkOrderId(woNoToId.get(r.get("工单编号(关联)")));
                    wr.setTaskId(taskKeyToId.get(r.get("工单编号(关联)") + "_" + r.get("工序序号(关联)")));
                    wr.setReportId(reportNoToId.get(r.get("报工编号(关联)")));
                    wr.setWageType(r.get("工资类型(personal/team_piece)"));
                    wr.setAmount(parseBD(r.get("金额")));
                    String dateStr = r.get("报工日期");
                    if (dateStr != null && !dateStr.isEmpty()) {
                        try { wr.setReportDate(java.time.LocalDate.parse(dateStr.trim())); } catch (Exception ignored) {}
                    }
                    wr.setCreateTime(parseDatetime(r.get("创建时间")));
                    wr.setCreateBy(userNameToId.get(r.get("创建人")));
                    wr.setUpdateTime(wr.getCreateTime());
                    wr.setUpdateBy(wr.getCreateBy());
                    wageRecordMapper.insert(wr);
                    success.incrementAndGet();
                } catch (Exception e) {
                    errors.add("wage_record第" + (i + 2) + "行: " + e.getMessage());
                }
            });
        }

        // 5. 导入质检任务（合并到报工模块确保时间一致）
        Map<String, Long> qcTaskNoToId = new java.util.concurrent.ConcurrentHashMap<>();
        if (qcTaskFile != null && !qcTaskFile.isEmpty()) {
            List<Map<String, String>> rows = ExcelUtil.parseImport(qcTaskFile, QC_TASK_HEADERS);
            java.util.stream.IntStream.range(0, rows.size()).parallel().forEach(i -> {
                Map<String, String> r = rows.get(i);
                try {
                    Long woId = woNoToId.get(r.get("关联工单编号"));
                    Long taskId = taskKeyToId.get(r.get("关联工单编号") + "_" + r.get("关联工序序号"));

                    com.aimes.modules.quality.entity.BizQcTask qt = new com.aimes.modules.quality.entity.BizQcTask();
                    qt.setTaskNo(r.get("任务编号"));
                    qt.setQcType(r.get("质检类型"));
                    // 走顶部预加载的 matNoToId / tplNameToId,不再循环内全表扫
                    qt.setProductId(matNoToId.get(r.get("产品编号")));
                    qt.setMaterialId(matNoToId.get(r.get("物料编号")));
                    String tplName = r.get("质检模板");
                    if (tplName != null && !tplName.isEmpty()) {
                        qt.setTemplateId(tplNameToId.get(tplName));
                    }
                    qt.setRelatedOrderType(r.get("关联单据类型"));
                    // 关联单据ID: 工单编号→工单ID
                    qt.setRelatedOrderId(woId);
                    qt.setWorkOrderId(woId);
                    qt.setTaskId(taskId);
                    qt.setPlanQty(parseBD(r.get("计划数量")));
                    qt.setPassQty(parseBD(r.get("合格数量")));
                    qt.setFailQty(parseBD(r.get("不合格数量")));
                    qt.setResult(r.get("结果"));
                    qt.setInspectorId(userNameToId.get(r.get("检验员")));
                    qt.setInspectedAt(parseDatetime(r.get("检验时间")));
                    qt.setDisposalStatus(r.get("处置状态"));
                    qt.setCreateTime(parseDatetime(r.get("创建时间")));
                    qt.setCreateBy(userNameToId.get(r.get("创建人")));
                    qt.setUpdateTime(qt.getInspectedAt() != null ? qt.getInspectedAt() : qt.getCreateTime());
                    qt.setUpdateBy(qt.getCreateBy());
                    qcTaskMapper.insert(qt);
                    qcTaskNoToId.put(qt.getTaskNo(), qt.getId());

                    // 根据质检模板生成明细结果（单选/文本默认值）
                    generateQcResults(qt);

                    // 回写 processTask.qcTaskId/qcStatus — 不再 selectById,直接 LambdaUpdateWrapper 一次 update
                    if (taskId != null && qt.getId() != null) {
                        BigDecimal fQty = qt.getFailQty() != null ? qt.getFailQty() : BigDecimal.ZERO;
                        String qcStatus = fQty.compareTo(BigDecimal.ZERO) > 0 ? "fail" : "pass";
                        processTaskMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<BizWoProcessTask>()
                                .eq(BizWoProcessTask::getId, taskId)
                                .set(BizWoProcessTask::getQcTaskId, qt.getId())
                                .set(BizWoProcessTask::getQcStatus, qcStatus));
                    }
                    success.incrementAndGet();
                } catch (Exception e) {
                    errors.add("qc_task第" + (i + 2) + "行: " + e.getMessage());
                }
            });
        }

        // 6. 导入质检记录
        List<Map<String, String>> qcRecordAllRows = parseAllParts(qcRecordFiles, QC_RECORD_HEADERS);
        if (!qcRecordAllRows.isEmpty()) {
            List<Map<String, String>> rows = qcRecordAllRows;
            java.util.stream.IntStream.range(0, rows.size()).parallel().forEach(i -> {
                Map<String, String> r = rows.get(i);
                try {
                    Long qcTaskId = qcTaskNoToId.get(r.get("质检任务编号(关联)"));
                    if (qcTaskId == null) { errors.add("qc_record第" + (i + 2) + "行: 质检任务不存在"); return; }

                    com.aimes.modules.quality.entity.BizQcRecord rec = new com.aimes.modules.quality.entity.BizQcRecord();
                    rec.setQcTaskId(qcTaskId);
                    rec.setRecordType(r.get("记录类型"));
                    rec.setQuantity(parseBD(r.get("数量")));
                    rec.setBatchNo(r.get("批次号"));
                    rec.setReporterId(userNameToId.get(r.get("报告人")));
                    rec.setCreateTime(parseDatetime(r.get("创建时间")));
                    rec.setCreateBy(userNameToId.get(r.get("创建人")));
                    rec.setUpdateTime(rec.getCreateTime());
                    rec.setUpdateBy(rec.getCreateBy());
                    qcRecordMapper.insert(rec);
                    success.incrementAndGet();
                } catch (Exception e) {
                    errors.add("qc_record第" + (i + 2) + "行: " + e.getMessage());
                }
            });
        }

        // 7. 导入质检处置单
        Map<String, Long> disposalNoToId = new java.util.concurrent.ConcurrentHashMap<>();
        if (disposalFile != null && !disposalFile.isEmpty()) {
            List<Map<String, String>> rows = ExcelUtil.parseImport(disposalFile, QC_DISPOSAL_HEADERS);
            java.util.stream.IntStream.range(0, rows.size()).parallel().forEach(i -> {
                Map<String, String> r = rows.get(i);
                try {
                    Long qcTaskId = qcTaskNoToId.get(r.get("质检任务编号(关联)"));
                    Long woId = woNoToId.get(r.get("工单编号"));

                    com.aimes.modules.quality.entity.BizQcDisposal d = new com.aimes.modules.quality.entity.BizQcDisposal();
                    d.setDisposalNo(r.get("处理编号"));
                    d.setQcTaskId(qcTaskId);
                    d.setWorkOrderId(woId);
                    d.setStepName(r.get("工序名称"));
                    // 走顶部预加载的 matNoToId,不再循环内全表扫
                    d.setMaterialId(matNoToId.get(r.get("物料编号")));
                    d.setQuantity(parseBD(r.get("数量")));
                    d.setDisposalType(r.get("处理类型(scrap/rework/concession)"));
                    d.setDisposalReason(r.get("处理原因"));
                    d.setStatus(r.get("状态"));
                    d.setProcessedBy(userNameToId.get(r.get("处理人")));
                    d.setProcessedAt(parseDatetime(r.get("处理时间")));
                    d.setCreateTime(parseDatetime(r.get("创建时间")));
                    d.setCreateBy(userNameToId.get(r.get("创建人")));
                    d.setUpdateTime(d.getCreateTime());
                    d.setUpdateBy(d.getCreateBy());
                    qcDisposalMapper.insert(d);
                    disposalNoToId.put(d.getDisposalNo(), d.getId());

                    // 回写 qcTask.disposalStatus='disposed' — LambdaUpdateWrapper 直接 update,免 selectById
                    if (qcTaskId != null) {
                        qcTaskMapper.update(null,
                                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<com.aimes.modules.quality.entity.BizQcTask>()
                                        .eq(com.aimes.modules.quality.entity.BizQcTask::getId, qcTaskId)
                                        .set(com.aimes.modules.quality.entity.BizQcTask::getDisposalStatus, "disposed"));
                    }
                    success.incrementAndGet();
                } catch (Exception e) {
                    errors.add("qc_disposal第" + (i + 2) + "行: " + e.getMessage());
                }
            });
        }

        // 8. 导入缺陷记录
        if (defectFile != null && !defectFile.isEmpty()) {
            List<Map<String, String>> rows = ExcelUtil.parseImport(defectFile, DEFECT_RECORD_HEADERS);
            java.util.stream.IntStream.range(0, rows.size()).parallel().forEach(i -> {
                Map<String, String> r = rows.get(i);
                try {
                    com.aimes.modules.quality.entity.BizDefectRecord dr = new com.aimes.modules.quality.entity.BizDefectRecord();
                    dr.setDefectNo(r.get("缺陷编号"));
                    dr.setSourceType(r.get("来源类型"));
                    // 走顶部预加载的 matNoToId
                    dr.setProductId(matNoToId.get(r.get("产品编号")));
                    dr.setWorkOrderId(woNoToId.get(r.get("工单编号")));
                    dr.setQuantity(parseBD(r.get("数量")));
                    dr.setDefectType(r.get("缺陷类型"));
                    dr.setHandlePath(r.get("处理方式"));
                    dr.setHandleStatus(r.get("处理状态"));
                    dr.setHandleResult(r.get("处理结果"));
                    dr.setHandlerId(userNameToId.get(r.get("处理人")));
                    dr.setHandledAt(parseDatetime(r.get("处理时间")));
                    dr.setCreateTime(parseDatetime(r.get("创建时间")));
                    dr.setCreateBy(userNameToId.get(r.get("创建人")));
                    dr.setUpdateTime(dr.getCreateTime());
                    dr.setUpdateBy(dr.getCreateBy());
                    defectRecordMapper.insert(dr);
                    success.incrementAndGet();
                } catch (Exception e) {
                    errors.add("defect_record第" + (i + 2) + "行: " + e.getMessage());
                }
            });
        }

        // 9. 导入返修工单
        Map<String, Long> reworkNoToId = new java.util.concurrent.ConcurrentHashMap<>();
        if (reworkOrderFile != null && !reworkOrderFile.isEmpty()) {
            List<Map<String, String>> rows = ExcelUtil.parseImport(reworkOrderFile, REWORK_ORDER_HEADERS);
            java.util.stream.IntStream.range(0, rows.size()).parallel().forEach(i -> {
                Map<String, String> r = rows.get(i);
                try {
                    Long qcTaskId = qcTaskNoToId.get(r.get("质检任务编号(关联)"));
                    Long disposalId = disposalNoToId.get(r.get("处理单号(关联)"));
                    Long woId = woNoToId.get(r.get("工单编号(关联)"));

                    com.aimes.modules.production.entity.BizReworkOrder ro = new com.aimes.modules.production.entity.BizReworkOrder();
                    ro.setReworkNo(r.get("返修编号"));
                    ro.setQcTaskId(qcTaskId);
                    ro.setQcDisposalId(disposalId);
                    ro.setWorkOrderId(woId);
                    // 走顶部预加载的 matNoToId
                    ro.setProductId(matNoToId.get(r.get("产品编号")));
                    ro.setQuantity(parseBD(r.get("数量")));
                    ro.setStatus(r.get("状态"));
                    ro.setCreateTime(parseDatetime(r.get("创建时间")));
                    ro.setCreateBy(userNameToId.get(r.get("创建人")));
                    ro.setUpdateTime(ro.getCreateTime());
                    ro.setUpdateBy(ro.getCreateBy());
                    reworkOrderMapper.insert(ro);
                    reworkNoToId.put(ro.getReworkNo(), ro.getId());

                    // 回写 disposal.reworkOrderId — LambdaUpdateWrapper 直接 update,免 selectById
                    if (disposalId != null && ro.getId() != null) {
                        Long roId = ro.getId();
                        qcDisposalMapper.update(null,
                                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<com.aimes.modules.quality.entity.BizQcDisposal>()
                                        .eq(com.aimes.modules.quality.entity.BizQcDisposal::getId, disposalId)
                                        .set(com.aimes.modules.quality.entity.BizQcDisposal::getReworkOrderId, roId));
                    }
                    success.incrementAndGet();
                } catch (Exception e) {
                    errors.add("rework_order第" + (i + 2) + "行: " + e.getMessage());
                }
            });
        }

        // 10. 导入返修步骤
        if (reworkStepFile != null && !reworkStepFile.isEmpty()) {
            List<Map<String, String>> rows = ExcelUtil.parseImport(reworkStepFile, REWORK_STEP_HEADERS);
            java.util.stream.IntStream.range(0, rows.size()).parallel().forEach(i -> {
                Map<String, String> r = rows.get(i);
                try {
                    Long reworkOrderId = reworkNoToId.get(r.get("返修编号(关联)"));
                    if (reworkOrderId == null) { errors.add("rework_step第" + (i + 2) + "行: 返修工单不存在"); return; }

                    com.aimes.modules.production.entity.BizReworkOrderStep rs = new com.aimes.modules.production.entity.BizReworkOrderStep();
                    rs.setReworkOrderId(reworkOrderId);
                    rs.setStepName(r.get("工序名称"));
                    rs.setAssignedUserId(userNameToId.get(r.get("指派人")));
                    String sortStr = r.get("排序号");
                    if (sortStr != null && !sortStr.isEmpty()) {
                        try { rs.setSortOrder(Integer.parseInt(sortStr.trim())); } catch (Exception ignored) {}
                    }
                    rs.setStatus(r.get("状态"));
                    rs.setCompletedAt(parseDatetime(r.get("完成时间")));
                    rs.setCreateTime(parseDatetime(r.get("创建时间")));
                    rs.setCreateBy(userNameToId.get(r.get("创建人")));
                    rs.setUpdateTime(rs.getCreateTime());
                    rs.setUpdateBy(rs.getCreateBy());
                    reworkOrderStepMapper.insert(rs);
                    success.incrementAndGet();
                } catch (Exception e) {
                    errors.add("rework_step第" + (i + 2) + "行: " + e.getMessage());
                }
            });
        }

        // ============ 11. 统一回写（严格按正常业务流程公式） ============
        // 流程: 报工→processTask.reportedQty/qualifiedQty → 质检通过→+passQty → 处置→调整 → 返修完成→+reworkQty
        //       → workOrder.completedQty = MIN(末组.qualifiedQty) → orderItem.producedQty → planItem.completedQty
        try {
            // 11a. 按工序任务逐一计算 qualifiedQty 和 defectQty
            List<BizWoProcessTask> allPts = processTaskMapper.selectList(
                    new LambdaQueryWrapper<BizWoProcessTask>()
                            .eq(BizWoProcessTask::getDeleted, 0));

            for (BizWoProcessTask pt : allPts) {
                try {
                BigDecimal totalDefect = BigDecimal.ZERO;
                BigDecimal totalReported = BigDecimal.ZERO;
                boolean hasQc = pt.getQcTemplateId() != null;

                // 汇总该工序的所有报工记录
                List<BizWorkReport> reports = workReportMapper.selectList(
                        new LambdaQueryWrapper<BizWorkReport>()
                                .eq(BizWorkReport::getTaskId, pt.getId())
                                .eq(BizWorkReport::getDeleted, 0));
                for (BizWorkReport rpt : reports) {
                    BigDecimal rptQty = rpt.getQualifiedQty() != null ? rpt.getQualifiedQty() : BigDecimal.ZERO;
                    BigDecimal rptDefect = rpt.getDefectQty() != null ? rpt.getDefectQty() : BigDecimal.ZERO;
                    totalReported = totalReported.add(rptQty);
                    totalDefect = totalDefect.add(rptDefect);
                }

                if (hasQc) {
                    // 有质检：qualifiedQty = SUM(质检通过批次的passQty) + 处置调整
                    pt.setReportedQty(totalReported);

                    // 扫描该工序关联的所有质检任务
                    List<com.aimes.modules.quality.entity.BizQcTask> qcTasks = qcTaskMapper.selectList(
                            new LambdaQueryWrapper<com.aimes.modules.quality.entity.BizQcTask>()
                                    .eq(com.aimes.modules.quality.entity.BizQcTask::getTaskId, pt.getId())
                                    .eq(com.aimes.modules.quality.entity.BizQcTask::getDeleted, 0));

                    BigDecimal qcQualified = BigDecimal.ZERO;
                    for (com.aimes.modules.quality.entity.BizQcTask qt : qcTasks) {
                        BigDecimal batchPlanQty = qt.getPlanQty() != null ? qt.getPlanQty() : BigDecimal.ZERO;

                        // result=completed（终态），通过failQty判断是否有不合格
                        BigDecimal failQty = qt.getFailQty() != null ? qt.getFailQty() : BigDecimal.ZERO;
                        BigDecimal passQty = qt.getPassQty() != null ? qt.getPassQty() : batchPlanQty;

                        if (failQty.compareTo(BigDecimal.ZERO) <= 0) {
                            // 全合格：passQty全部计入
                            qcQualified = qcQualified.add(passQty);
                        } else {
                            // 有不合格：passQty部分先计入

                            // 查找该质检任务的处置单
                            List<com.aimes.modules.quality.entity.BizQcDisposal> disposals = qcDisposalMapper.selectList(
                                    new LambdaQueryWrapper<com.aimes.modules.quality.entity.BizQcDisposal>()
                                            .eq(com.aimes.modules.quality.entity.BizQcDisposal::getQcTaskId, qt.getId())
                                            .eq(com.aimes.modules.quality.entity.BizQcDisposal::getDeleted, 0));

                            BigDecimal disposalAdjust = BigDecimal.ZERO;
                            for (com.aimes.modules.quality.entity.BizQcDisposal d : disposals) {
                                BigDecimal dQty = d.getQuantity() != null ? d.getQuantity() : BigDecimal.ZERO;
                                if ("concession".equals(d.getDisposalType())) {
                                    // 让步接收：处置数量全部视为合格
                                    disposalAdjust = disposalAdjust.add(dQty);
                                } else if ("scrap".equals(d.getDisposalType())) {
                                    // 报废：不增加合格数（报废品已移出）
                                    // passQty中已包含了非报废的部分
                                } else if ("rework".equals(d.getDisposalType())) {
                                    // 返修：查找返修工单是否已完成
                                    if (d.getReworkOrderId() != null) {
                                        com.aimes.modules.production.entity.BizReworkOrder ro = reworkOrderMapper.selectById(d.getReworkOrderId());
                                        if (ro != null && "completed".equals(ro.getStatus()) && ro.getQuantity() != null) {
                                            disposalAdjust = disposalAdjust.add(ro.getQuantity());
                                        }
                                    }
                                }
                            }
                            qcQualified = qcQualified.add(passQty).add(disposalAdjust);
                        }
                    }
                    pt.setQualifiedQty(qcQualified);
                    // 不良数 = 报工总数 - 最终合格数
                    pt.setDefectQty(totalReported.subtract(qcQualified).max(BigDecimal.ZERO));
                } else {
                    // 无质检：qualifiedQty = 报工合格数之和（直接累加）
                    pt.setQualifiedQty(totalReported);
                    pt.setDefectQty(totalDefect);
                }

                // 工序状态
                boolean allReported = pt.getPlanQty() != null && totalReported.compareTo(pt.getPlanQty()) >= 0;
                if (allReported) {
                    pt.setStatus("completed");
                } else if (totalReported.compareTo(BigDecimal.ZERO) > 0) {
                    pt.setStatus("in_progress");
                }
                processTaskMapper.updateById(pt);
                } catch (Exception ex) {
                    // 单条工序回写失败不该影响其它工序, 否则一次异常会让后面所有工序都漏刷
                    log.warn("[Sim 11b] 工序 {} (id={}) 状态回写失败: {}", pt.getStepName(), pt.getId(), ex.getMessage());
                }
            }

            // 11b. 回写工单 completedQty = MIN(末组工序.qualifiedQty)
            List<BizWorkOrder> allWo = workOrderMapper.selectList(
                    new LambdaQueryWrapper<BizWorkOrder>().eq(BizWorkOrder::getDeleted, 0)
                            .ne(BizWorkOrder::getStatus, "cancelled"));
            for (BizWorkOrder wo : allWo) {
                List<BizWoProcessTask> woTasks = processTaskMapper.selectList(
                        new LambdaQueryWrapper<BizWoProcessTask>()
                                .eq(BizWoProcessTask::getWorkOrderId, wo.getId())
                                .eq(BizWoProcessTask::getDeleted, 0)
                                .ne(BizWoProcessTask::getStatus, "cancelled")
                                .orderByDesc(BizWoProcessTask::getStepGroup));
                if (woTasks.isEmpty()) continue;

                int lastGroup = woTasks.get(0).getStepGroup() != null ? woTasks.get(0).getStepGroup() : 1;
                BigDecimal completedQty = woTasks.stream()
                        .filter(t -> t.getStepGroup() != null && t.getStepGroup() == lastGroup)
                        .map(t -> t.getQualifiedQty() != null ? t.getQualifiedQty() : BigDecimal.ZERO)
                        .min(BigDecimal::compareTo)
                        .orElse(BigDecimal.ZERO);

                wo.setCompletedQty(completedQty);
                boolean allDone = woTasks.stream().allMatch(t -> "completed".equals(t.getStatus()));
                boolean anyStarted = woTasks.stream().anyMatch(t ->
                        !"pending".equals(t.getStatus()) && !"cancelled".equals(t.getStatus()));
                if (allDone && completedQty.compareTo(BigDecimal.ZERO) > 0) {
                    wo.setStatus("completed");
                } else if (anyStarted) {
                    wo.setStatus("in_progress");
                }
                workOrderMapper.updateById(wo);

                // 11c. 回写 salesOrderItem.producedQty = SUM(关联工单.completedQty)
                if (wo.getOrderId() != null && wo.getOrderItemId() != null) {
                    List<BizWorkOrder> relatedWos = workOrderMapper.selectList(
                            new LambdaQueryWrapper<BizWorkOrder>()
                                    .eq(BizWorkOrder::getOrderId, wo.getOrderId())
                                    .eq(BizWorkOrder::getOrderItemId, wo.getOrderItemId())
                                    .eq(BizWorkOrder::getDeleted, 0).ne(BizWorkOrder::getStatus, "cancelled"));
                    BigDecimal totalProduced = relatedWos.stream()
                            .map(w -> w.getCompletedQty() != null ? w.getCompletedQty() : BigDecimal.ZERO)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    com.aimes.modules.order.entity.BizSalesOrderItem oi = salesOrderItemMapper.selectById(wo.getOrderItemId());
                    if (oi != null) {
                        oi.setProducedQty(totalProduced);
                        salesOrderItemMapper.updateById(oi);
                    }
                }

                // 11d. 回写 planItem.completedQty = SUM(关联工单.completedQty)
                if (wo.getPlanItemId() != null) {
                    List<BizWorkOrder> relatedWos = workOrderMapper.selectList(
                            new LambdaQueryWrapper<BizWorkOrder>()
                                    .eq(BizWorkOrder::getPlanItemId, wo.getPlanItemId())
                                    .eq(BizWorkOrder::getDeleted, 0).ne(BizWorkOrder::getStatus, "cancelled"));
                    BigDecimal totalCompleted = relatedWos.stream()
                            .map(w -> w.getCompletedQty() != null ? w.getCompletedQty() : BigDecimal.ZERO)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    com.aimes.modules.mrp.entity.BizProductionPlanItem pi = planItemMapper.selectById(wo.getPlanItemId());
                    if (pi != null) {
                        pi.setCompletedQty(totalCompleted);
                        BigDecimal planQty = pi.getPlanQty() != null ? pi.getPlanQty() : BigDecimal.ZERO;
                        BigDecimal released = pi.getReleasedQty() != null ? pi.getReleasedQty() : BigDecimal.ZERO;
                        boolean piAllDone = !relatedWos.isEmpty()
                                && relatedWos.stream().allMatch(w -> "completed".equals(w.getStatus()));
                        // 状态推进:
                        // - 全部工单完成且数量足够 → completed (已完工)
                        // - 有任何完成数量 → partial_released (生产中)
                        // - 全部下达但还没开干 → fully_released (已开始)
                        // - 部分下达 → partial_released
                        // - 都没下达 → 保持 pending
                        if (piAllDone && totalCompleted.compareTo(planQty) >= 0) {
                            pi.setStatus("completed");
                        } else if (totalCompleted.compareTo(BigDecimal.ZERO) > 0) {
                            pi.setStatus("partial_released");
                        } else if (released.compareTo(planQty) >= 0 && planQty.compareTo(BigDecimal.ZERO) > 0) {
                            pi.setStatus("fully_released");
                        } else if (released.compareTo(BigDecimal.ZERO) > 0) {
                            pi.setStatus("partial_released");
                        }
                        planItemMapper.updateById(pi);
                    }
                }
            }
            // 11e. 回写 plan.status（所有行项completed后计划也completed）
            Set<Long> updatedPlanIds = new HashSet<>();
            for (BizWorkOrder wo : allWo) {
                if (wo.getPlanItemId() == null) continue;
                com.aimes.modules.mrp.entity.BizProductionPlanItem pi = planItemMapper.selectById(wo.getPlanItemId());
                if (pi == null || pi.getPlanId() == null || updatedPlanIds.contains(pi.getPlanId())) continue;
                updatedPlanIds.add(pi.getPlanId());

                List<com.aimes.modules.mrp.entity.BizProductionPlanItem> allItems = planItemMapper.selectList(
                        new LambdaQueryWrapper<com.aimes.modules.mrp.entity.BizProductionPlanItem>()
                                .eq(com.aimes.modules.mrp.entity.BizProductionPlanItem::getPlanId, pi.getPlanId())
                                .eq(com.aimes.modules.mrp.entity.BizProductionPlanItem::getDeleted, 0));
                boolean allItemsDone = allItems.stream().allMatch(item -> "completed".equals(item.getStatus()));
                if (allItemsDone) {
                    com.aimes.modules.mrp.entity.BizProductionPlan plan = planMapper.selectById(pi.getPlanId());
                    if (plan != null && !"completed".equals(plan.getStatus())) {
                        plan.setStatus("completed");
                        planMapper.updateById(plan);
                    }
                }
            }

            // 11f. 回写销售订单状态
            // 先确保订单至少进入 in_production，再尝试推进到 completed（全部产品生产完）
            Set<Long> touchedOrderIds = new HashSet<>();
            for (BizWorkOrder wo : allWo) {
                if (wo.getOrderId() == null || touchedOrderIds.contains(wo.getOrderId())) continue;
                touchedOrderIds.add(wo.getOrderId());
                salesOrderStatusHelper.advanceToInProduction(wo.getOrderId());
                // 生产完成 → completed（需要 producedQty 已经回写到订单行上）
                salesOrderStatusHelper.advanceToCompleted(wo.getOrderId());
            }

            // 成品入库(11f)和销售发货(11g)已移至④销售发货模块(ShipmentGenerator)

            // 11h. 残留 pending / in_progress 工单兜底回写
            // 凡 deliveryDate <= 今天 → 按 planQty 强制完工; 已完成的不动。
            //
            // 性能改造 (2026-05-11): 原循环对每个 stuck WO 做 3+ 次 selectList + 多条 insert,
            // 265 工单 × ~10 round trip = 3000+ 远程 DB 调用, 超 socketTimeout=300s 必挂。
            // 改造方案:
            //   1) 预加载 tasks / 已有报工合格数 / 已有质检pass数 到 Map (3 条聚合 SQL)
            //   2) 循环内只构建实体 List, 不打 DB
            //   3) 循环外 batchInsertHelper 批量 flush
            //   4) plan_item / order_item / plan 状态回算改成纯 SQL 批量 UPDATE
            //   5) 班组报工补造路径删除, 跟主 generate() 保持 personal 一致
            LocalDate today = LocalDate.now();
            List<BizWorkOrder> stuck = workOrderMapper.selectList(
                    new LambdaQueryWrapper<BizWorkOrder>()
                            .eq(BizWorkOrder::getDeleted, 0)
                            .in(BizWorkOrder::getStatus, List.of("pending", "in_progress"))
                            .le(BizWorkOrder::getDeliveryDate, today));
            log.info("[Sim] 残留 pending/in_progress 工单兜底数量: {}", stuck.size());

            // 11h-2. 兜底: 凡是有报工/部分工序非 pending 的工单, 状态至少应该是 in_progress
            int advancedInflight = workOrderMapper.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<BizWorkOrder>()
                            .eq(BizWorkOrder::getDeleted, 0)
                            .eq(BizWorkOrder::getStatus, "pending")
                            .and(w -> w
                                    .exists("SELECT 1 FROM biz_work_report wr WHERE wr.work_order_id = biz_work_order.id AND wr.deleted = 0")
                                    .or().exists("SELECT 1 FROM biz_wo_process_task pt WHERE pt.work_order_id = biz_work_order.id AND pt.deleted = 0 AND pt.status NOT IN ('pending','cancelled')"))
                            .set(BizWorkOrder::getStatus, "in_progress")
                            .set(BizWorkOrder::getUpdateTime, LocalDateTime.now()));
            if (advancedInflight > 0) {
                log.info("[Sim] 有报工但 status 仍 pending 的工单 → in_progress 推进数量: {}", advancedInflight);
            }

            // 11h-3. 基于报工 SQL 重算工序数量/状态 (纯 SQL, 一次搞定)
            try {
                int tasksRecalc = jdbcTemplate.update(
                        "UPDATE biz_wo_process_task pt " +
                        "JOIN (SELECT task_id, SUM(IFNULL(qualified_qty,0)) AS sq, SUM(IFNULL(defect_qty,0)) AS sd " +
                        "      FROM biz_work_report WHERE deleted=0 AND task_id IS NOT NULL GROUP BY task_id) r " +
                        "  ON r.task_id = pt.id " +
                        "SET pt.reported_qty = r.sq + r.sd, pt.qualified_qty = r.sq, pt.defect_qty = r.sd, " +
                        "    pt.status = CASE WHEN r.sq + r.sd >= IFNULL(pt.plan_qty,0) THEN 'completed' " +
                        "                     WHEN r.sq + r.sd > 0 THEN 'in_progress' ELSE pt.status END, " +
                        "    pt.update_time = NOW() " +
                        "WHERE pt.deleted = 0 AND pt.status NOT IN ('completed','cancelled')");
                log.info("[Sim] 11b 兜底: 基于报工 SQL 重算工序任务状态/数量, 影响行数: {}", tasksRecalc);
            } catch (Exception e) {
                log.warn("[Sim] 11h-3 工序兜底回写失败: {}", e.getMessage());
            }

            if (!stuck.isEmpty()) {
                List<Long> stuckWoIds = stuck.stream().map(BizWorkOrder::getId).collect(Collectors.toList());
                String stuckWoIdsCsv = stuckWoIds.stream().map(String::valueOf).collect(Collectors.joining(","));

                // ==== 预加载阶段 (3 条聚合 SQL 替代上千次 selectList) ====
                // 1) wo → tasks
                Map<Long, List<BizWoProcessTask>> tasksByWo = processTaskMapper.selectList(
                                new LambdaQueryWrapper<BizWoProcessTask>()
                                        .in(BizWoProcessTask::getWorkOrderId, stuckWoIds)
                                        .eq(BizWoProcessTask::getDeleted, 0)
                                        .ne(BizWoProcessTask::getStatus, "cancelled"))
                        .stream().collect(Collectors.groupingBy(BizWoProcessTask::getWorkOrderId));

                List<Long> allTaskIds = tasksByWo.values().stream().flatMap(List::stream)
                        .map(BizWoProcessTask::getId).collect(Collectors.toList());
                String taskIdsCsv = allTaskIds.isEmpty() ? null
                        : allTaskIds.stream().map(String::valueOf).collect(Collectors.joining(","));

                // 2) task → 已有报工合格数汇总
                Map<Long, BigDecimal> reportedQtyByTask = new HashMap<>();
                if (taskIdsCsv != null) {
                    jdbcTemplate.queryForList(
                            "SELECT task_id, SUM(IFNULL(qualified_qty,0)) AS sq " +
                            "FROM biz_work_report WHERE deleted = 0 AND task_id IN (" + taskIdsCsv + ") " +
                            "GROUP BY task_id").forEach(row -> {
                        Long tid = ((Number) row.get("task_id")).longValue();
                        Object sq = row.get("sq");
                        reportedQtyByTask.put(tid, sq != null ? new BigDecimal(sq.toString()) : BigDecimal.ZERO);
                    });
                }

                // 3) task → 已有质检 pass 数
                Map<Long, BigDecimal> qcPassByTask = new HashMap<>();
                if (taskIdsCsv != null) {
                    jdbcTemplate.queryForList(
                            "SELECT task_id, SUM(IFNULL(pass_qty,0)) AS pq " +
                            "FROM biz_qc_task WHERE deleted = 0 AND task_id IN (" + taskIdsCsv + ") " +
                            "GROUP BY task_id").forEach(row -> {
                        Long tid = ((Number) row.get("task_id")).longValue();
                        Object pq = row.get("pq");
                        qcPassByTask.put(tid, pq != null ? new BigDecimal(pq.toString()) : BigDecimal.ZERO);
                    });
                }

                // ==== 内存构建阶段 (循环内零 DB) ====
                List<BizWorkReport> reportsToInsert = new ArrayList<>();
                List<com.aimes.modules.workreport.entity.BizWageRecord> wagesToInsert = new ArrayList<>();
                List<com.aimes.modules.quality.entity.BizQcTask> qcTasksToInsert = new ArrayList<>();

                Set<Long> stuckPiIds = new HashSet<>();
                Set<Long> stuckOrderItemIds = new HashSet<>();
                Set<Long> stuckOrderIds = new HashSet<>();

                for (BizWorkOrder wo : stuck) {
                    BigDecimal planQty = wo.getPlanQty() != null ? wo.getPlanQty() : BigDecimal.ZERO;
                    if (planQty.compareTo(BigDecimal.ZERO) <= 0) continue;

                    LocalDateTime makeupTime = (wo.getDeliveryDate() != null ? wo.getDeliveryDate() : today)
                            .atTime(15, 0);
                    Long makeupCreator = wo.getCreateBy() != null ? wo.getCreateBy() : 1L;

                    List<BizWoProcessTask> woTasks = new ArrayList<>(tasksByWo.getOrDefault(wo.getId(), List.of()));
                    woTasks.sort(Comparator.comparingInt(t -> t.getStepNo() != null ? t.getStepNo() : 0));

                    LocalDateTime woStart = wo.getCreateTime() != null
                            ? wo.getCreateTime() : makeupTime.minusDays(Math.max(1, woTasks.size()));
                    LocalDateTime woEnd = (wo.getDeliveryDate() != null ? wo.getDeliveryDate() : today)
                            .atTime(17, 0);
                    if (!woEnd.isAfter(woStart)) {
                        woEnd = woStart.plusDays(Math.max(1, woTasks.size()));
                    }
                    long totalMin = Duration.between(woStart, woEnd).toMinutes();
                    long perStepMin = totalMin / Math.max(1, woTasks.size());

                    for (int si = 0; si < woTasks.size(); si++) {
                        BizWoProcessTask t = woTasks.get(si);
                        BigDecimal tPlanQty = t.getPlanQty() != null ? t.getPlanQty() : BigDecimal.ZERO;
                        if (tPlanQty.compareTo(BigDecimal.ZERO) <= 0) continue;

                        LocalDateTime stepStart = woStart.plusMinutes(perStepMin * si);
                        LocalDateTime stepEnd = (si == woTasks.size() - 1)
                                ? woEnd : woStart.plusMinutes(perStepMin * (si + 1));
                        if (!stepEnd.isAfter(stepStart)) stepEnd = stepStart.plusHours(2);
                        LocalDateTime stepReportTime = stepEnd;

                        BigDecimal existingReported = reportedQtyByTask.getOrDefault(t.getId(), BigDecimal.ZERO);
                        BigDecimal gap = tPlanQty.subtract(existingReported);

                        if (gap.compareTo(BigDecimal.ZERO) > 0) {
                            BigDecimal piecePrice = t.getPiecePrice() != null ? t.getPiecePrice() : BigDecimal.ZERO;
                            BigDecimal wage = piecePrice.multiply(gap).setScale(2, RoundingMode.HALF_UP);

                            // 统一 personal 报工 (主 generate() 已废弃 team 报工)
                            Long reporterId = t.getAssignedUserId() != null ? t.getAssignedUserId() : makeupCreator;
                            BizWorkReport rpt = new BizWorkReport();
                            rpt.setReportNo(CodeUtil.generate("WR"));
                            rpt.setWorkOrderId(wo.getId());
                            rpt.setTaskId(t.getId());
                            rpt.setReporterId(reporterId);
                            rpt.setReportType("personal");
                            rpt.setQualifiedQty(gap);
                            rpt.setDefectQty(BigDecimal.ZERO);
                            rpt.setPieceWage(wage);
                            rpt.setStatus("submitted");
                            rpt.setCreateTime(stepReportTime);
                            rpt.setCreateBy(makeupCreator);
                            rpt.setUpdateTime(stepReportTime);
                            rpt.setUpdateBy(makeupCreator);
                            reportsToInsert.add(rpt);

                            if (wage.compareTo(BigDecimal.ZERO) > 0) {
                                com.aimes.modules.workreport.entity.BizWageRecord wr =
                                        new com.aimes.modules.workreport.entity.BizWageRecord();
                                wr.setUserId(reporterId);
                                wr.setWorkOrderId(wo.getId());
                                wr.setTaskId(t.getId());
                                // reportId 留空: 主 generate 路径会写, 这里 batch flush 后再回填代价高,
                                // 工资记录主要按 user/task 聚合统计, reportId 非必填
                                wr.setWageType("personal");
                                wr.setAmount(wage);
                                wr.setReportDate(stepReportTime.toLocalDate());
                                wr.setCreateTime(stepReportTime);
                                wr.setCreateBy(makeupCreator);
                                wr.setUpdateTime(stepReportTime);
                                wr.setUpdateBy(makeupCreator);
                                wagesToInsert.add(wr);
                            }

                            // 质检任务: 用预加载的 qcPassByTask
                            if (t.getQcTemplateId() != null) {
                                BigDecimal existingQcPass = qcPassByTask.getOrDefault(t.getId(), BigDecimal.ZERO);
                                BigDecimal qcGap = tPlanQty.subtract(existingQcPass);
                                if (qcGap.compareTo(BigDecimal.ZERO) > 0) {
                                    com.aimes.modules.quality.entity.BizQcTask qt =
                                            new com.aimes.modules.quality.entity.BizQcTask();
                                    qt.setTaskNo(CodeUtil.generate("QC"));
                                    qt.setQcType("process");
                                    qt.setProductId(wo.getProductId());
                                    qt.setMaterialId(wo.getProductId());
                                    qt.setTemplateId(t.getQcTemplateId());
                                    qt.setRelatedOrderType("work_order");
                                    qt.setRelatedOrderId(wo.getId());
                                    qt.setWorkOrderId(wo.getId());
                                    qt.setTaskId(t.getId());
                                    qt.setPlanQty(qcGap);
                                    qt.setPassQty(qcGap);
                                    qt.setFailQty(BigDecimal.ZERO);
                                    qt.setResult("pass");
                                    qt.setInspectorId(makeupCreator);
                                    qt.setInspectedAt(stepReportTime);
                                    qt.setDisposalStatus("none");
                                    qt.setCreateTime(stepReportTime);
                                    qt.setCreateBy(makeupCreator);
                                    qt.setUpdateTime(stepReportTime);
                                    qt.setUpdateBy(makeupCreator);
                                    qcTasksToInsert.add(qt);
                                }
                            }
                        }
                    }

                    if (wo.getPlanItemId() != null) stuckPiIds.add(wo.getPlanItemId());
                    if (wo.getOrderItemId() != null) stuckOrderItemIds.add(wo.getOrderItemId());
                    if (wo.getOrderId() != null) stuckOrderIds.add(wo.getOrderId());
                }

                // ==== 批量持久化阶段 (BATCH 模式, flush 每 1000) ====
                if (!reportsToInsert.isEmpty()) {
                    batchInsertHelper.insertAll(reportsToInsert,
                            com.aimes.modules.workreport.mapper.BizWorkReportMapper.class);
                    log.info("[Sim] 11h 兜底补造个人报工: {} 条", reportsToInsert.size());
                }
                if (!wagesToInsert.isEmpty()) {
                    batchInsertHelper.insertAll(wagesToInsert,
                            com.aimes.modules.workreport.mapper.BizWageRecordMapper.class);
                    log.info("[Sim] 11h 兜底补造工资记录: {} 条", wagesToInsert.size());
                }
                if (!qcTasksToInsert.isEmpty()) {
                    batchInsertHelper.insertAll(qcTasksToInsert,
                            com.aimes.modules.quality.mapper.BizQcTaskMapper.class);

                    // QcRecord 跟 QcTask 一一对应
                    List<com.aimes.modules.quality.entity.BizQcRecord> qcRecords = new ArrayList<>();
                    for (com.aimes.modules.quality.entity.BizQcTask qt : qcTasksToInsert) {
                        com.aimes.modules.quality.entity.BizQcRecord rec =
                                new com.aimes.modules.quality.entity.BizQcRecord();
                        rec.setQcTaskId(qt.getId());
                        rec.setRecordType("inspection");
                        rec.setQuantity(qt.getPassQty());
                        rec.setReporterId(qt.getInspectorId());
                        rec.setCreateTime(qt.getInspectedAt());
                        rec.setCreateBy(qt.getCreateBy());
                        rec.setUpdateTime(qt.getInspectedAt());
                        rec.setUpdateBy(qt.getCreateBy());
                        qcRecords.add(rec);
                    }
                    batchInsertHelper.insertAll(qcRecords,
                            com.aimes.modules.quality.mapper.BizQcRecordMapper.class);

                    // QcResult: 按 templateId 预加载模板项, 在内存里组装后批量写
                    Set<Long> templateIds = qcTasksToInsert.stream()
                            .map(com.aimes.modules.quality.entity.BizQcTask::getTemplateId)
                            .filter(Objects::nonNull).collect(Collectors.toSet());
                    Map<Long, List<com.aimes.modules.quality.entity.BizQcTemplateItem>> templateItemsMap = templateIds.isEmpty()
                            ? new HashMap<>()
                            : qcTemplateItemMapper.selectList(
                                    new LambdaQueryWrapper<com.aimes.modules.quality.entity.BizQcTemplateItem>()
                                            .in(com.aimes.modules.quality.entity.BizQcTemplateItem::getTemplateId, templateIds)
                                            .eq(com.aimes.modules.quality.entity.BizQcTemplateItem::getDeleted, 0))
                                    .stream().collect(Collectors.groupingBy(
                                            com.aimes.modules.quality.entity.BizQcTemplateItem::getTemplateId));
                    List<com.aimes.modules.quality.entity.BizQcResult> qcResults = new ArrayList<>();
                    for (com.aimes.modules.quality.entity.BizQcTask qt : qcTasksToInsert) {
                        if (qt.getTemplateId() == null) continue;
                        List<com.aimes.modules.quality.entity.BizQcTemplateItem> items =
                                templateItemsMap.getOrDefault(qt.getTemplateId(), List.of());
                        boolean isPass = "pass".equals(qt.getResult());
                        for (com.aimes.modules.quality.entity.BizQcTemplateItem ti : items) {
                            com.aimes.modules.quality.entity.BizQcResult rs =
                                    new com.aimes.modules.quality.entity.BizQcResult();
                            rs.setQcTaskId(qt.getId());
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
                            rs.setCreateTime(qt.getInspectedAt() != null ? qt.getInspectedAt() : qt.getCreateTime());
                            rs.setCreateBy(qt.getCreateBy());
                            rs.setUpdateTime(rs.getCreateTime());
                            rs.setUpdateBy(rs.getCreateBy());
                            qcResults.add(rs);
                        }
                    }
                    if (!qcResults.isEmpty()) {
                        batchInsertHelper.insertAll(qcResults,
                                com.aimes.modules.quality.mapper.BizQcResultMapper.class);
                    }

                    // 工序 qcTaskId / qcStatus 回写 (一次 SQL)
                    jdbcTemplate.update(
                            "UPDATE biz_wo_process_task pt " +
                            "JOIN (SELECT task_id, MAX(id) AS qt_id FROM biz_qc_task " +
                            "      WHERE deleted = 0 AND result = 'pass' AND task_id IS NOT NULL " +
                            "      GROUP BY task_id) q ON q.task_id = pt.id " +
                            "SET pt.qc_task_id = q.qt_id, pt.qc_status = 'pass' " +
                            "WHERE pt.deleted = 0 AND pt.qc_task_id IS NULL");
                    log.info("[Sim] 11h 兜底补造质检任务 {} 条 (含 record + result)", qcTasksToInsert.size());
                }

                // ==== 工序/工单状态批量推进 (纯 SQL) ====
                int taskFlushCount = jdbcTemplate.update(
                        "UPDATE biz_wo_process_task " +
                        "SET qualified_qty = IFNULL(plan_qty,0), reported_qty = IFNULL(plan_qty,0), " +
                        "    defect_qty = 0, status = 'completed', update_time = NOW() " +
                        "WHERE deleted = 0 AND status NOT IN ('completed','cancelled') " +
                        "  AND work_order_id IN (" + stuckWoIdsCsv + ")");
                log.info("[Sim] 11h 兜底刷完工序: {} 条", taskFlushCount);

                int woFlushCount = jdbcTemplate.update(
                        "UPDATE biz_work_order " +
                        "SET completed_qty = IFNULL(plan_qty,0), status = 'completed', update_time = NOW() " +
                        "WHERE deleted = 0 AND id IN (" + stuckWoIdsCsv + ")");
                log.info("[Sim] 11h 兜底刷完工单: {} 条", woFlushCount);

                // ==== plan_item / order_item / plan 状态批量回算 ====
                if (!stuckPiIds.isEmpty()) {
                    String piIdsCsv = stuckPiIds.stream().map(String::valueOf).collect(Collectors.joining(","));
                    jdbcTemplate.update(
                            "UPDATE biz_production_plan_item pi " +
                            "JOIN (SELECT plan_item_id, SUM(IFNULL(completed_qty,0)) AS total, " +
                            "             SUM(CASE WHEN status='completed' THEN 1 ELSE 0 END) AS done_cnt, " +
                            "             COUNT(*) AS total_cnt " +
                            "      FROM biz_work_order WHERE deleted=0 AND status<>'cancelled' AND plan_item_id IS NOT NULL " +
                            "      GROUP BY plan_item_id) w ON w.plan_item_id = pi.id " +
                            "SET pi.completed_qty = w.total, " +
                            "    pi.status = CASE WHEN w.done_cnt = w.total_cnt AND w.total >= IFNULL(pi.plan_qty,0) THEN 'completed' " +
                            "                     WHEN w.total > 0 THEN 'partial_released' ELSE pi.status END " +
                            "WHERE pi.id IN (" + piIdsCsv + ")");

                    // plan 主表: 全部 item completed → plan 也 completed
                    jdbcTemplate.update(
                            "UPDATE biz_production_plan p " +
                            "SET p.status = 'completed' " +
                            "WHERE p.deleted = 0 AND p.status <> 'completed' " +
                            "  AND p.id IN (SELECT DISTINCT plan_id FROM biz_production_plan_item WHERE id IN (" + piIdsCsv + ")) " +
                            "  AND NOT EXISTS (SELECT 1 FROM biz_production_plan_item pi2 " +
                            "                  WHERE pi2.plan_id = p.id AND pi2.deleted = 0 AND pi2.status <> 'completed')");
                }

                if (!stuckOrderItemIds.isEmpty()) {
                    String oiIdsCsv = stuckOrderItemIds.stream().map(String::valueOf).collect(Collectors.joining(","));
                    jdbcTemplate.update(
                            "UPDATE biz_sales_order_item oi " +
                            "JOIN (SELECT order_item_id, SUM(IFNULL(completed_qty,0)) AS total " +
                            "      FROM biz_work_order WHERE deleted=0 AND status<>'cancelled' AND order_item_id IS NOT NULL " +
                            "      GROUP BY order_item_id) w ON w.order_item_id = oi.id " +
                            "SET oi.produced_qty = w.total " +
                            "WHERE oi.id IN (" + oiIdsCsv + ")");
                }

                // 销售订单状态推进保持原循环 (advanceTo* 是业务幂等方法, 数量级在百以内可接受)
                for (Long oid : stuckOrderIds) {
                    try {
                        salesOrderStatusHelper.advanceToInProduction(oid);
                        salesOrderStatusHelper.advanceToCompleted(oid);
                    } catch (Exception e) {
                        log.warn("[Sim] 11h 推进销售订单 {} 失败: {}", oid, e.getMessage());
                    }
                }
            }

            log.info("报工导入后统一回写完成：工序{}个，工单{}个", allPts.size(), allWo.size());
        } catch (Exception e) {
            log.error("统一回写失败: {}", e.getMessage(), e);
            errors.add("统一回写失败: " + e.getMessage());
        }

        return Map.of("success", success.get(), "errors", errors);
    }

    // ===== 辅助方法 =====

    private BizTeamMember createFakeMember(Long teamId, Long userId, BigDecimal weight) {
        BizTeamMember m = new BizTeamMember();
        m.setTeamId(teamId);
        m.setUserId(userId);
        m.setWeight(weight);
        return m;
    }

    private Map<Long, String> loadAllUserNames() {
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>().eq(SysUser::getDeleted, 0))
                .stream().collect(Collectors.toMap(SysUser::getId,
                        u -> u.getRealName() != null ? u.getRealName() : u.getUsername(), (a, b) -> a));
    }

    private Map<String, Long> loadUserNameToIdMap() {
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>().eq(SysUser::getDeleted, 0))
                .stream().collect(Collectors.toMap(
                        u -> u.getRealName() != null ? u.getRealName() : u.getUsername(),
                        SysUser::getId, (a, b) -> a));
    }

    /** 根据质检模板生成质检结果明细（单选/文本默认值） */
    private void generateQcResults(com.aimes.modules.quality.entity.BizQcTask qt) {
        if (qt.getTemplateId() == null) return;
        List<com.aimes.modules.quality.entity.BizQcTemplateItem> tplItems = qcTemplateItemMapper.selectList(
                new LambdaQueryWrapper<com.aimes.modules.quality.entity.BizQcTemplateItem>()
                        .eq(com.aimes.modules.quality.entity.BizQcTemplateItem::getTemplateId, qt.getTemplateId())
                        .eq(com.aimes.modules.quality.entity.BizQcTemplateItem::getDeleted, 0)
                        .orderByAsc(com.aimes.modules.quality.entity.BizQcTemplateItem::getSortOrder));
        boolean isPass = "pass".equals(qt.getResult());
        for (com.aimes.modules.quality.entity.BizQcTemplateItem ti : tplItems) {
            com.aimes.modules.quality.entity.BizQcResult rs = new com.aimes.modules.quality.entity.BizQcResult();
            rs.setQcTaskId(qt.getId());
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
            rs.setCreateTime(qt.getInspectedAt() != null ? qt.getInspectedAt() : qt.getCreateTime());
            rs.setCreateBy(qt.getCreateBy());
            rs.setUpdateTime(rs.getCreateTime());
            rs.setUpdateBy(rs.getCreateBy());
            qcResultMapper.insert(rs);
        }
    }

    /** 解析多个分片 xlsx, 合并所有数据行(分片导出场景: work_report_part_01.xlsx ~ _part_NN.xlsx) */
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

}
