package com.aimes.modules.dataimport.service.simulation;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据模拟生成配置（通用时间配置 + 各模块个性化人员/仓库/参数配置）
 */
@Data
public class SimConfig {

    // ===== 通用时间配置 =====
    private int startHour = 8;
    private int startMin = 0;
    private int endHour = 17;
    private int endMin = 0;
    private boolean skipWeekends = true;
    private boolean skipHolidays = false;
    private Set<LocalDate> holidays = Set.of();

    /** 数据截止日期：所有生成的时间不超过此日期（默认今天） */
    private LocalDate cutoffDate = LocalDate.now();

    // ===== 各角色人员配置（每个模块单独配置所需角色） =====
    private List<Long> creatorIds = List.of();      // 创建人（所有模块都需要）
    private List<Long> assigneeIds = List.of();     // 指派人（②工单工序、⑧返修）
    private List<Long> reporterIds = List.of();     // 报工人（③报工）
    private List<Long> inspectorIds = List.of();    // 检验员（④质检）
    private List<Long> handlerIds = List.of();      // 处理人（⑤质检处理）
    private List<Long> approverIds = List.of();     // 审批人（⑥外协）
    private List<Long> receiverIds = List.of();     // 收货人（⑦外协收料）

    // ===== 仓库配置（仅⑦外协收料需要） =====
    private Long warehouseId;

    // ===== 模块业务参数（全部为范围，每条数据取随机值） =====
    // ①生产计划
    private int delayDaysMin = 1;                 // 计划开始延迟(最小)
    private int delayDaysMax = 5;                 // 计划开始延迟(最大)
    private int advanceDaysMin = 1;               // 计划结束提前(最小)
    private int advanceDaysMax = 5;               // 计划结束提前(最大)
    // ②工单
    private int woDelayMin = 1;                   // 工单延迟天数(最小)
    private int woDelayMax = 3;                   // 工单延迟天数(最大)
    // ③报工
    private int defectRateMin = 1;                // 不良率%(最小)
    private int defectRateMax = 5;                // 不良率%(最大)
    private int reportStartHour = 17;             // 报工时间段开始(时)
    private int reportStartMin = 0;               // 报工时间段开始(分)
    private int reportEndHour = 18;               // 报工时间段结束(时)
    private int reportEndMin = 0;                 // 报工时间段结束(分)
    // ③+④ 质检时间配置
    private int qcDelayHoursMin = 2;              // 报工后质检延迟(小时，最小)
    private int qcDelayHoursMax = 6;              // 报工后质检延迟(小时，最大)
    private int nextStepDelayHoursMin = 1;        // 质检完成后下道工序/入库延迟(小时，最小)
    private int nextStepDelayHoursMax = 4;        // 质检完成后下道工序/入库延迟(小时，最大)
    // ③④质检
    private int passTaskPct = 90;                 // 全合格任务占比%（固定值，如90=100个任务中90个全合格）
    private int passQtyPctMin = 80;               // 不合格任务中合格数占比%(最小)
    private int passQtyPctMax = 95;               // 不合格任务中合格数占比%(最大)
    // ③ 每次报工占比
    private int reportBatchPctMin = 40;           // 每次报工占可报工数的比例%(最小)
    private int reportBatchPctMax = 50;           // 每次报工占可报工数的比例%(最大)
    // ③+⑤ 不合格处置配置（固定值，三项之和=100%）
    private int reworkPct = 40;                   // 返修比例%
    private int concessionPct = 30;               // 让步接收比例%
    // 报废比例 = 100 - reworkPct - concessionPct
    private int wholeBatchPct = 20;               // 整批处置概率%，其余为仅不合格数
    // 兼容旧字段
    private int scrapRateMin = 20;
    private int scrapRateMax = 50;
    // ⑦库存台账
    private int invProcessHoursMin = 1;           // 出入库处理延迟(小时，最小)
    private int invProcessHoursMax = 4;           // 出入库处理延迟(小时，最大)
    // ⑦期初备料 — 没流水的物料,期初数量在该区间内随机生成
    private int initialStockMin = 50;             // 期初备料数量(最小)
    private int initialStockMax = 500;            // 期初备料数量(最大)
    // ④采购来料
    private int receiptDelayDaysMin = 1;          // 收料单延迟天数(最小,相对采购订单交货日期)
    private int receiptDelayDaysMax = 3;          // 收料单延迟天数(最大)
    private int receiptProcessHoursMin = 2;       // 收料处理延迟时间(小时,最小)
    private int receiptProcessHoursMax = 8;       // 收料处理延迟时间(小时,最大)
    // ③ 发货延迟（相对成品入库时间）
    private int shipDelayDaysMin = 1;            // 发货延迟天数(最小)
    private int shipDelayDaysMax = 3;            // 发货延迟天数(最大)
    // ⑤整单外协
    private boolean enableFullOutsource = false;  // 是否启用整单外协
    private int fullOutsourcePct = 20;            // 整单外协比例%（从销售订单中抽取的比例）
    // 外协通用
    private Long defaultSupplierId;               // 默认外协供应商
    private int outsourceLeadDaysMin = 8;         // 外协交期(工作日，最小)
    private int outsourceLeadDaysMax = 15;        // 外协交期(工作日，最大)

    // ⑧设备管理(EAM)
    private int eamPlanMonths = 3;                   // 保养/点检计划回溯月数
    private int eamInspectionAbnormalPct = 10;       // 点检异常比例%
    private int eamMaintenanceCompletePct = 85;      // 保养任务完成率%
    private int eamInspectionCompletePct = 90;       // 点检任务完成率%
    private int eamRepairFromInspectionPct = 30;     // 来自点检的报修比例%
    private int eamRepairCompletePct = 80;           // 维修完成率%

    // 兼容旧字段（generate方法中不再直接使用，改用random方法）
    private int delayDays = 2;
    private BigDecimal defectRate = new BigDecimal("0.03");
    private BigDecimal passRate = new BigDecimal("0.95");
    private BigDecimal sampleRate = new BigDecimal("1.00");
    private BigDecimal scrapRate = new BigDecimal("0.30");
    // outsourceRate已移除，改用enableFullOutsource/fullOutsourcePct

    // ===== 便捷取人方法（用于Generator中） =====

    /** 取创建人 */
    public Long pickCreator() { return pickFrom(creatorIds); }

    /** 取指派人 */
    public Long pickAssignee() { return pickFrom(assigneeIds.isEmpty() ? creatorIds : assigneeIds); }

    /** 取报工人 */
    public Long pickReporter() { return pickFrom(reporterIds.isEmpty() ? creatorIds : reporterIds); }

    /** 取检验员 */
    public Long pickInspector() { return pickFrom(inspectorIds.isEmpty() ? creatorIds : inspectorIds); }

    /** 取处理人 */
    public Long pickHandler() { return pickFrom(handlerIds.isEmpty() ? creatorIds : handlerIds); }

    /** 取审批人 */
    public Long pickApprover() { return pickFrom(approverIds.isEmpty() ? creatorIds : approverIds); }

    /** 取收货人 */
    public Long pickReceiver() { return pickFrom(receiverIds.isEmpty() ? creatorIds : receiverIds); }

    /** 在[delayDaysMin, delayDaysMax]范围内随机取一个延迟天数 */
    public int randomDelayDays() {
        return randomRange(delayDaysMin, delayDaysMax);
    }

    /** 在[advanceDaysMin, advanceDaysMax]范围内随机取一个提前天数 */
    public int randomAdvanceDays() {
        return randomRange(advanceDaysMin, advanceDaysMax);
    }

    /** ②工单延迟天数随机 */
    public int randomWoDelay() { return randomRange(woDelayMin, woDelayMax); }

    /** ③在报工时间段内生成随机时间（自动截断到cutoffDate） */
    public java.time.LocalDateTime randomReportTime(java.time.LocalDate date) {
        int s = reportStartHour * 60 + reportStartMin;
        int e = reportEndHour * 60 + reportEndMin;
        // 若 date = 今天,把窗口上限钳到"现在"之前,避免生成未来报工
        // 如果当前时刻还没到报工开始时间,直接返回 null,由调用方跳过今天
        java.time.LocalDate today = java.time.LocalDate.now();
        if (date.isEqual(today)) {
            java.time.LocalTime now = java.time.LocalTime.now();
            int nowMin = now.getHour() * 60 + now.getMinute();
            if (nowMin <= s) return null;
            e = Math.min(e, nowMin);
        }
        int m = java.util.concurrent.ThreadLocalRandom.current().nextInt(s, Math.max(s + 1, e));
        java.time.LocalDateTime result = date.atTime(m / 60, m % 60, java.util.concurrent.ThreadLocalRandom.current().nextInt(60));
        return clampToCutoff(result);
    }

    /** 判断某天是否可以生成报工(今天时要求当前时刻已进入报工窗口) */
    public boolean isReportableDay(java.time.LocalDate date) {
        if (date == null) return false;
        java.time.LocalDate today = java.time.LocalDate.now();
        if (date.isAfter(today)) return false;
        if (date.isEqual(today)) {
            java.time.LocalTime now = java.time.LocalTime.now();
            int nowMin = now.getHour() * 60 + now.getMinute();
            int s = reportStartHour * 60 + reportStartMin;
            return nowMin > s;
        }
        return true;
    }

    /** ③+④ 报工后延迟N小时进行质检，超出工作时间则顺延到下一工作日 */
    public java.time.LocalDateTime calcQcTime(java.time.LocalDateTime reportTime) {
        int delayHours = randomRange(qcDelayHoursMin, qcDelayHoursMax);
        return advanceWithOverflow(reportTime, delayHours);
    }

    /** ③+④ 质检完成后延迟N小时进入下道工序/入库，超出工作时间则顺延 */
    public java.time.LocalDateTime calcNextStepTime(java.time.LocalDateTime qcCompleteTime) {
        int delayHours = randomRange(nextStepDelayHoursMin, nextStepDelayHoursMax);
        return advanceWithOverflow(qcCompleteTime, delayHours);
    }

    /**
     * 时间推进：加N小时，如果超出当天工作结束时间，顺延到下一工作日的工作开始时间+随机偏移。
     * 硬约束：不允许推进到"现在"之后 —— 超过返回 null,由调用方跳过后续生成。
     */
    private java.time.LocalDateTime advanceWithOverflow(java.time.LocalDateTime base, int hours) {
        java.time.LocalDateTime result = base.plusHours(hours);
        java.time.LocalDateTime dayEnd = base.toLocalDate().atTime(endHour, endMin);
        if (result.isAfter(dayEnd)) {
            java.time.LocalDate nextDay = SimTimeHelper.addWorkingDays(base.toLocalDate(), 1, this);
            int offsetMin = java.util.concurrent.ThreadLocalRandom.current().nextInt(0, 120);
            result = nextDay.atTime(startHour, startMin).plusMinutes(offsetMin);
        }
        if (result.isAfter(java.time.LocalDateTime.now())) return null;
        return clampToCutoff(result);
    }

    /** ③不良率随机(返回小数，如0.03) */
    public double randomDefectRate() { return randomRange(defectRateMin, defectRateMax) / 100.0; }

    /** ③每次报工占比随机 */
    public int randomReportBatchPct() { return randomRange(reportBatchPctMin, reportBatchPctMax); }


    /** ③④该质检任务是否全合格（按passTaskPct概率） */
    public boolean isQcPass() {
        return java.util.concurrent.ThreadLocalRandom.current().nextInt(100) < passTaskPct;
    }

    /** ③④不合格任务中，合格数占质检数的比例（随机范围） */
    public double randomPassQtyRate() { return randomRange(passQtyPctMin, passQtyPctMax) / 100.0; }

    /** ⑤报废比例随机(返回小数，如0.30) — 兼容旧版 */
    public double randomScrapRate() { return randomRange(scrapRateMin, scrapRateMax) / 100.0; }

    /**
     * 不合格处置类型：返回 "rework"/"concession"/"scrap"
     * reworkPct + concessionPct + scrapPct(自动) = 100
     */
    public String randomDisposalType() {
        int roll = java.util.concurrent.ThreadLocalRandom.current().nextInt(100);
        if (roll < reworkPct) return "rework";
        if (roll < reworkPct + concessionPct) return "concession";
        return "scrap";
    }

    /** 整批处置概率：true=整批，false=仅不合格数 */
    public boolean isWholeBatch() {
        return java.util.concurrent.ThreadLocalRandom.current().nextInt(100) < wholeBatchPct;
    }

    /** ⑦出入库处理延迟(小时)随机 */
    public int randomInvProcessHours() { return randomRange(invProcessHoursMin, invProcessHoursMax); }

    /** ⑦期初备料数量随机(无流水物料兜底用) */
    public int randomInitialStock() { return randomRange(initialStockMin, initialStockMax); }

    /** ③发货延迟天数随机 */
    public int randomShipDelayDays() { return randomRange(shipDelayDaysMin, shipDelayDaysMax); }

    /** ④收料单延迟天数随机 */
    public int randomReceiptDelayDays() { return randomRange(receiptDelayDaysMin, receiptDelayDaysMax); }

    /** ④收料处理延迟时间(小时)随机 */
    public int randomReceiptProcessHours() { return randomRange(receiptProcessHoursMin, receiptProcessHoursMax); }

    /** ⑤整单外协：该订单是否走整单外协 */
    public boolean isFullOutsource() {
        return enableFullOutsource && java.util.concurrent.ThreadLocalRandom.current().nextInt(100) < fullOutsourcePct;
    }

    /** 外协交期随机(工作日) */
    public int randomOutsourceLeadDays() { return randomRange(outsourceLeadDaysMin, outsourceLeadDaysMax); }

    /**
     * 检查给定时间是否超过 cutoffDate。
     * 旧版本会把超截止时间"回退到今天"，造成所有未来活动被压到今天的 bug。
     * 新语义：保持时间不变，由调用方使用 isAfterCutoff() 决定是否跳过该记录。
     */
    public java.time.LocalDateTime clampToCutoff(java.time.LocalDateTime dt) {
        return dt;
    }

    /**
     * 将日期截断到 cutoffDate 之内。
     */
    public LocalDate clampDateToCutoff(LocalDate date) {
        if (date == null || cutoffDate == null) return date;
        return date.isAfter(cutoffDate) ? cutoffDate : date;
    }

    /**
     * 检查日期是否已超过截止日
     */
    public boolean isAfterCutoff(LocalDate date) {
        return cutoffDate != null && date != null && date.isAfter(cutoffDate);
    }

    /**
     * 检查时间是否已超过截止日
     * 如果截止日是今天,按当前时刻(时分秒)判断 —— 避免生成出同一天但超过 "现在" 的事件
     * 如果截止日不是今天(历史日期),按截止日 23:59:59 判断
     */
    public boolean isAfterCutoff(java.time.LocalDateTime dt) {
        if (cutoffDate == null || dt == null) return false;
        java.time.LocalDate today = java.time.LocalDate.now();
        if (cutoffDate.isEqual(today)) {
            // 截止日 = 今天 → 超过当前时刻算未来
            return dt.isAfter(java.time.LocalDateTime.now());
        }
        // 截止日是别的日期 → 按日期比较
        return dt.toLocalDate().isAfter(cutoffDate);
    }

    private Long pickFrom(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return null;
        return ids.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(ids.size()));
    }

    private int randomRange(int min, int max) {
        if (min >= max) return min;
        return java.util.concurrent.ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    /**
     * 从前端JSON Map解析配置
     */
    @SuppressWarnings("unchecked")
    public static SimConfig fromMap(Map<String, Object> map) {
        SimConfig c = new SimConfig();
        if (map == null) return c;

        // 时间配置
        String st = (String) map.getOrDefault("workStartTime", "08:00");
        String et = (String) map.getOrDefault("workEndTime", "17:00");
        String[] sp = st.split(":");
        c.startHour = Integer.parseInt(sp[0]);
        c.startMin = sp.length > 1 ? Integer.parseInt(sp[1]) : 0;
        String[] ep = et.split(":");
        c.endHour = Integer.parseInt(ep[0]);
        c.endMin = ep.length > 1 ? Integer.parseInt(ep[1]) : 0;

        c.skipWeekends = Boolean.TRUE.equals(map.get("skipWeekends"));
        c.skipHolidays = Boolean.TRUE.equals(map.get("skipHolidays"));

        // 截止日期（默认今天）
        Object cutoff = map.get("cutoffDate");
        if (cutoff instanceof String s && !s.isEmpty()) {
            c.cutoffDate = LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE);
        } else {
            c.cutoffDate = LocalDate.now();
        }

        Object h = map.get("holidays");
        if (h instanceof List) {
            c.holidays = ((List<String>) h).stream()
                    .map(s -> LocalDate.parse(s.trim(), DateTimeFormatter.ISO_LOCAL_DATE))
                    .collect(Collectors.toSet());
        }

        // 各角色人员
        c.creatorIds = parseLongList(map.get("creatorIds"));
        c.assigneeIds = parseLongList(map.get("assigneeIds"));
        c.reporterIds = parseLongList(map.get("reporterIds"));
        c.inspectorIds = parseLongList(map.get("inspectorIds"));
        c.handlerIds = parseLongList(map.get("handlerIds"));
        c.approverIds = parseLongList(map.get("approverIds"));
        c.receiverIds = parseLongList(map.get("receiverIds"));

        // 兼容旧的 operatorIds（作为 creatorIds 的 fallback）
        if (c.creatorIds.isEmpty()) {
            c.creatorIds = parseLongList(map.get("operatorIds"));
        }

        // 仓库
        if (map.get("warehouseId") != null) c.warehouseId = Long.valueOf(map.get("warehouseId").toString());

        // 业务参数
        if (map.get("delayDaysMin") != null) c.delayDaysMin = Integer.parseInt(map.get("delayDaysMin").toString());
        if (map.get("delayDaysMax") != null) c.delayDaysMax = Integer.parseInt(map.get("delayDaysMax").toString());
        if (map.get("advanceDaysMin") != null) c.advanceDaysMin = Integer.parseInt(map.get("advanceDaysMin").toString());
        if (map.get("advanceDaysMax") != null) c.advanceDaysMax = Integer.parseInt(map.get("advanceDaysMax").toString());
        // ②工单延迟范围
        if (map.get("woDelayMin") != null) c.woDelayMin = Integer.parseInt(map.get("woDelayMin").toString());
        if (map.get("woDelayMax") != null) c.woDelayMax = Integer.parseInt(map.get("woDelayMax").toString());
        // ③不良率范围
        if (map.get("defectRateMin") != null) c.defectRateMin = Integer.parseInt(map.get("defectRateMin").toString());
        if (map.get("defectRateMax") != null) c.defectRateMax = Integer.parseInt(map.get("defectRateMax").toString());
        // ③报工时间段
        String rSt = (String) map.getOrDefault("reportStartTime", "");
        if (!rSt.isEmpty()) { String[] rsp = rSt.split(":"); c.reportStartHour = Integer.parseInt(rsp[0]); c.reportStartMin = rsp.length > 1 ? Integer.parseInt(rsp[1]) : 0; }
        String rEt = (String) map.getOrDefault("reportEndTime", "");
        if (!rEt.isEmpty()) { String[] rep = rEt.split(":"); c.reportEndHour = Integer.parseInt(rep[0]); c.reportEndMin = rep.length > 1 ? Integer.parseInt(rep[1]) : 0; }
        // ③+④ 质检延迟配置
        if (map.get("qcDelayHoursMin") != null) c.qcDelayHoursMin = Integer.parseInt(map.get("qcDelayHoursMin").toString());
        if (map.get("qcDelayHoursMax") != null) c.qcDelayHoursMax = Integer.parseInt(map.get("qcDelayHoursMax").toString());
        if (map.get("nextStepDelayHoursMin") != null) c.nextStepDelayHoursMin = Integer.parseInt(map.get("nextStepDelayHoursMin").toString());
        if (map.get("nextStepDelayHoursMax") != null) c.nextStepDelayHoursMax = Integer.parseInt(map.get("nextStepDelayHoursMax").toString());
        // ③④质检合格配置
        if (map.get("passTaskPct") != null) c.passTaskPct = Integer.parseInt(map.get("passTaskPct").toString());
        if (map.get("passQtyPctMin") != null) c.passQtyPctMin = Integer.parseInt(map.get("passQtyPctMin").toString());
        if (map.get("passQtyPctMax") != null) c.passQtyPctMax = Integer.parseInt(map.get("passQtyPctMax").toString());
        // ③ 每次报工占比
        if (map.get("reportBatchPctMin") != null) c.reportBatchPctMin = Integer.parseInt(map.get("reportBatchPctMin").toString());
        if (map.get("reportBatchPctMax") != null) c.reportBatchPctMax = Integer.parseInt(map.get("reportBatchPctMax").toString());
        // ③发货延迟
        if (map.get("shipDelayDaysMin") != null) c.shipDelayDaysMin = Integer.parseInt(map.get("shipDelayDaysMin").toString());
        if (map.get("shipDelayDaysMax") != null) c.shipDelayDaysMax = Integer.parseInt(map.get("shipDelayDaysMax").toString());
        // ⑦库存台账
        if (map.get("invProcessHoursMin") != null) c.invProcessHoursMin = Integer.parseInt(map.get("invProcessHoursMin").toString());
        if (map.get("invProcessHoursMax") != null) c.invProcessHoursMax = Integer.parseInt(map.get("invProcessHoursMax").toString());
        if (map.get("initialStockMin") != null) c.initialStockMin = Integer.parseInt(map.get("initialStockMin").toString());
        if (map.get("initialStockMax") != null) c.initialStockMax = Integer.parseInt(map.get("initialStockMax").toString());
        // ④采购来料
        if (map.get("receiptDelayDaysMin") != null) c.receiptDelayDaysMin = Integer.parseInt(map.get("receiptDelayDaysMin").toString());
        if (map.get("receiptDelayDaysMax") != null) c.receiptDelayDaysMax = Integer.parseInt(map.get("receiptDelayDaysMax").toString());
        if (map.get("receiptProcessHoursMin") != null) c.receiptProcessHoursMin = Integer.parseInt(map.get("receiptProcessHoursMin").toString());
        if (map.get("receiptProcessHoursMax") != null) c.receiptProcessHoursMax = Integer.parseInt(map.get("receiptProcessHoursMax").toString());
        // ③+⑤ 不合格处置比例（固定值）
        if (map.get("reworkPct") != null) c.reworkPct = Integer.parseInt(map.get("reworkPct").toString());
        if (map.get("concessionPct") != null) c.concessionPct = Integer.parseInt(map.get("concessionPct").toString());
        if (map.get("wholeBatchPct") != null) c.wholeBatchPct = Integer.parseInt(map.get("wholeBatchPct").toString());
        // 兼容旧字段
        if (map.get("scrapRateMin") != null) c.scrapRateMin = Integer.parseInt(map.get("scrapRateMin").toString());
        if (map.get("scrapRateMax") != null) c.scrapRateMax = Integer.parseInt(map.get("scrapRateMax").toString());
        // ⑥外协比例范围
        if (map.get("enableFullOutsource") != null) c.enableFullOutsource = Boolean.parseBoolean(map.get("enableFullOutsource").toString());
        if (map.get("fullOutsourcePct") != null) c.fullOutsourcePct = Integer.parseInt(map.get("fullOutsourcePct").toString());
        if (map.get("outsourceLeadDaysMin") != null) c.outsourceLeadDaysMin = Integer.parseInt(map.get("outsourceLeadDaysMin").toString());
        if (map.get("outsourceLeadDaysMax") != null) c.outsourceLeadDaysMax = Integer.parseInt(map.get("outsourceLeadDaysMax").toString());
        if (map.get("defaultSupplierId") != null) c.defaultSupplierId = Long.valueOf(map.get("defaultSupplierId").toString());
        // 兼容旧的固定值参数
        if (map.get("delayDays") != null) c.delayDays = Integer.parseInt(map.get("delayDays").toString());
        if (map.get("defectRate") != null) c.defectRate = new BigDecimal(map.get("defectRate").toString());
        if (map.get("passRate") != null) c.passRate = new BigDecimal(map.get("passRate").toString());
        if (map.get("scrapRate") != null) c.scrapRate = new BigDecimal(map.get("scrapRate").toString());
        // outsourceRate已移除

        // EAM 参数
        if (map.get("eamPlanMonths") != null) c.eamPlanMonths = Integer.parseInt(map.get("eamPlanMonths").toString());
        if (map.get("eamInspectionAbnormalPct") != null) c.eamInspectionAbnormalPct = Integer.parseInt(map.get("eamInspectionAbnormalPct").toString());
        if (map.get("eamMaintenanceCompletePct") != null) c.eamMaintenanceCompletePct = Integer.parseInt(map.get("eamMaintenanceCompletePct").toString());
        if (map.get("eamInspectionCompletePct") != null) c.eamInspectionCompletePct = Integer.parseInt(map.get("eamInspectionCompletePct").toString());
        if (map.get("eamRepairFromInspectionPct") != null) c.eamRepairFromInspectionPct = Integer.parseInt(map.get("eamRepairFromInspectionPct").toString());
        if (map.get("eamRepairCompletePct") != null) c.eamRepairCompletePct = Integer.parseInt(map.get("eamRepairCompletePct").toString());

        return c;
    }

    private static List<Long> parseLongList(Object obj) {
        if (obj instanceof List<?> list) {
            return list.stream().map(o -> Long.valueOf(o.toString())).toList();
        }
        return List.of();
    }
}
