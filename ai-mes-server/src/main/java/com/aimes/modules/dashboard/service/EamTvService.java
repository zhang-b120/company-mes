package com.aimes.modules.dashboard.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * EAM 设备资产大屏数据聚合服务
 * 5 个 Tab: 设备总览 / 运行告警 / 保养管理 / 点检管理 / 维修工单
 * 所有 SQL 失败都用 0 / 空列表兜底,保证大屏永不崩
 *
 * 任务状态码:0=待执行 1=执行中 2=已完成 3=已取消 4=已暂停
 */
@Service
@RequiredArgsConstructor
public class EamTvService {

    private final JdbcTemplate jdbc;

    public Map<String, Object> getAll() {
        Map<String, Object> all = new LinkedHashMap<>();
        all.put("equipment", getEquipment());
        all.put("alarm", getAlarm());
        all.put("maintenance", getMaintenance());
        all.put("inspection", getInspection());
        all.put("repair", getRepair());
        all.put("ticker", getTicker());
        return all;
    }

    // ==================== TAB 0: 设备总览 ====================
    private Map<String, Object> getEquipment() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalCount", q1("SELECT COUNT(*) FROM eam_equipment WHERE deleted=0", 0L));
        m.put("normalCount", q1("SELECT COUNT(*) FROM eam_equipment WHERE deleted=0 AND status='NORMAL'", 0L));
        m.put("maintenanceCount", q1("SELECT COUNT(*) FROM eam_equipment WHERE deleted=0 AND status='MAINTENANCE'", 0L));
        m.put("retiredCount", q1("SELECT COUNT(*) FROM eam_equipment WHERE deleted=0 AND status='RETIRED'", 0L));

        // 设备类型分布(柱)
        m.put("categoryBar", qList(
                "SELECT IFNULL(NULLIF(category,''),'未分类') name, COUNT(*) value " +
                "FROM eam_equipment WHERE deleted=0 GROUP BY category ORDER BY value DESC LIMIT 8"));

        // 设备状态分布(饼)
        m.put("statusPie", qList(
                "SELECT status name, COUNT(*) value FROM eam_equipment WHERE deleted=0 GROUP BY status"));

        // 即将到期保养 TOP 10(按 next_maintenance_date 升序)
        m.put("upcomingList", qList(
                "SELECT equipment_no equipmentNo, equipment_name equipmentName, " +
                "IFNULL(category,'-') category, IFNULL(location,'-') location, " +
                "next_maintenance_date nextDate, " +
                "DATEDIFF(next_maintenance_date, CURDATE()) daysLeft " +
                "FROM eam_equipment WHERE deleted=0 AND next_maintenance_date IS NOT NULL " +
                "ORDER BY next_maintenance_date ASC LIMIT 10"));
        return m;
    }

    // ==================== TAB 1: 运行告警 ====================
    private Map<String, Object> getAlarm() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("runningCount", q1(
                "SELECT COUNT(*) FROM eam_equipment_status WHERE deleted=0 AND running_status='RUNNING'", 0L));
        m.put("alarmCount", q1(
                "SELECT COUNT(*) FROM eam_equipment_status WHERE deleted=0 " +
                "AND alarm_status IS NOT NULL AND alarm_status NOT IN ('','NORMAL','OK')", 0L));
        m.put("idleCount", q1(
                "SELECT COUNT(*) FROM eam_equipment_status WHERE deleted=0 AND running_status='IDLE'", 0L));
        m.put("totalAlarm7d", q1(
                "SELECT COUNT(*) FROM eam_equipment_alarm_log WHERE deleted=0 " +
                "AND occurred_at >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)", 0L));

        // 7 日告警趋势(单条 GROUP BY)
        Map<String, Long> alarmDay = new HashMap<>();
        try {
            for (Map<String, Object> r : qList(
                    "SELECT DATE(occurred_at) d, COUNT(*) c FROM eam_equipment_alarm_log " +
                    "WHERE deleted=0 AND occurred_at >= DATE_SUB(CURDATE(), INTERVAL 6 DAY) " +
                    "GROUP BY DATE(occurred_at)")) {
                Object d = r.get("d");
                if (d != null) alarmDay.put(d.toString(), ((Number) r.get("c")).longValue());
            }
        } catch (Exception ignore) {}
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = LocalDate.now().minusDays(i);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("date", d.toString().substring(5));
            r.put("count", alarmDay.getOrDefault(d.toString(), 0L));
            trend.add(r);
        }
        m.put("trend", trend);

        // 告警类型分布(按 alarm_status,SQL 直接转中文)
        String alarmPieCase = "CASE UPPER(IFNULL(alarm_status,'')) " +
                " WHEN 'CRITICAL' THEN '严重' WHEN 'FATAL' THEN '致命' " +
                " WHEN 'HIGH' THEN '高级' WHEN 'ERROR' THEN '错误' " +
                " WHEN 'WARN' THEN '警告' WHEN 'WARNING' THEN '警告' " +
                " WHEN 'INFO' THEN '提示' WHEN 'NORMAL' THEN '正常' " +
                " WHEN '' THEN '未分类' ELSE alarm_status END ";
        m.put("alarmPie", qList(
                "SELECT " + alarmPieCase + " name, COUNT(*) value " +
                "FROM eam_equipment_alarm_log WHERE deleted=0 " +
                "GROUP BY alarm_status ORDER BY value DESC LIMIT 6"));

        // 当前告警明细 TOP 10(alarm_status 也翻译)
        String alarmListCase = "CASE UPPER(IFNULL(log.alarm_status,'')) " +
                " WHEN 'CRITICAL' THEN '严重' WHEN 'FATAL' THEN '致命' " +
                " WHEN 'HIGH' THEN '高级' WHEN 'ERROR' THEN '错误' " +
                " WHEN 'WARN' THEN '警告' WHEN 'WARNING' THEN '警告' " +
                " WHEN 'INFO' THEN '提示' WHEN 'NORMAL' THEN '正常' " +
                " WHEN '' THEN '-' ELSE log.alarm_status END ";
        m.put("alarmList", qList(
                "SELECT e.equipment_no equipmentNo, e.equipment_name equipmentName, " +
                "IFNULL(e.location,'-') location, " +
                alarmListCase + " alarmStatus, IFNULL(log.alarm_message,'-') alarmMessage, " +
                "log.occurred_at occurredAt " +
                "FROM eam_equipment_alarm_log log " +
                "LEFT JOIN eam_equipment e ON e.id=log.equipment_id " +
                "WHERE log.deleted=0 ORDER BY log.occurred_at DESC LIMIT 10"));
        return m;
    }

    // ==================== TAB 2: 保养管理 ====================
    private Map<String, Object> getMaintenance() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pendingCount", q1("SELECT COUNT(*) FROM eam_maintenance_task WHERE deleted=0 AND task_status=0", 0L));
        m.put("inProgressCount", q1("SELECT COUNT(*) FROM eam_maintenance_task WHERE deleted=0 AND task_status=1", 0L));
        m.put("completedCount", q1("SELECT COUNT(*) FROM eam_maintenance_task WHERE deleted=0 AND task_status=2", 0L));
        m.put("overdueCount", q1(
                "SELECT COUNT(*) FROM eam_maintenance_task WHERE deleted=0 " +
                "AND task_status IN (0,1,4) AND planned_end_time < NOW()", 0L));

        // 12 月完成趋势(单条 GROUP BY 拉两组,Java 拼月份)
        YearMonth ym = YearMonth.now();
        Map<String, Long> planMap = monthGroup(
                "SELECT DATE_FORMAT(create_time,'%Y-%m') m, COUNT(*) c FROM eam_maintenance_task " +
                "WHERE deleted=0 AND create_time >= DATE_SUB(CURDATE(), INTERVAL 11 MONTH) " +
                "GROUP BY DATE_FORMAT(create_time,'%Y-%m')");
        Map<String, Long> doneMap = monthGroup(
                "SELECT DATE_FORMAT(actual_end_time,'%Y-%m') m, COUNT(*) c FROM eam_maintenance_task " +
                "WHERE deleted=0 AND task_status=2 AND actual_end_time >= DATE_SUB(CURDATE(), INTERVAL 11 MONTH) " +
                "GROUP BY DATE_FORMAT(actual_end_time,'%Y-%m')");
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 11; i >= 0; i--) {
            YearMonth ymi = ym.minusMonths(i);
            String key = ymi.toString();
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("month", ymi.getMonthValue() + "月");
            r.put("planned", planMap.getOrDefault(key, 0L));
            r.put("completed", doneMap.getOrDefault(key, 0L));
            trend.add(r);
        }
        m.put("trend", trend);

        // 任务状态饼
        m.put("statusPie", qList(
                "SELECT task_status name, COUNT(*) value FROM eam_maintenance_task " +
                "WHERE deleted=0 GROUP BY task_status"));

        // 即将到期保养任务 TOP 10
        m.put("upcomingList", qList(
                "SELECT t.task_code taskCode, IFNULL(e.equipment_name,'-') equipmentName, " +
                "IFNULL(t.executor_name,'-') executorName, " +
                "t.planned_end_time plannedEnd, t.task_status taskStatus " +
                "FROM eam_maintenance_task t " +
                "LEFT JOIN eam_equipment e ON e.id=t.equipment_id " +
                "WHERE t.deleted=0 AND t.task_status IN (0,1,4) " +
                "ORDER BY t.planned_end_time ASC LIMIT 10"));
        return m;
    }

    // ==================== TAB 3: 点检管理 ====================
    private Map<String, Object> getInspection() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pendingCount", q1("SELECT COUNT(*) FROM eam_inspection_task WHERE deleted=0 AND task_status=0", 0L));
        m.put("inProgressCount", q1("SELECT COUNT(*) FROM eam_inspection_task WHERE deleted=0 AND task_status=1", 0L));
        m.put("completedCount", q1("SELECT COUNT(*) FROM eam_inspection_task WHERE deleted=0 AND task_status=2", 0L));
        m.put("overdueCount", q1(
                "SELECT COUNT(*) FROM eam_inspection_task WHERE deleted=0 " +
                "AND task_status IN (0,1) AND planned_end_time < NOW()", 0L));

        // 14 日点检趋势(单条 GROUP BY)
        Map<String, Long> ipPlan = dayGroup(
                "SELECT DATE(create_time) d, COUNT(*) c FROM eam_inspection_task " +
                "WHERE deleted=0 AND create_time >= DATE_SUB(CURDATE(), INTERVAL 13 DAY) " +
                "GROUP BY DATE(create_time)");
        Map<String, Long> ipDone = dayGroup(
                "SELECT DATE(actual_end_time) d, COUNT(*) c FROM eam_inspection_task " +
                "WHERE deleted=0 AND task_status=2 AND actual_end_time >= DATE_SUB(CURDATE(), INTERVAL 13 DAY) " +
                "GROUP BY DATE(actual_end_time)");
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 13; i >= 0; i--) {
            LocalDate d = LocalDate.now().minusDays(i);
            String key = d.toString();
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("date", d.toString().substring(5));
            r.put("planned", ipPlan.getOrDefault(key, 0L));
            r.put("completed", ipDone.getOrDefault(key, 0L));
            trend.add(r);
        }
        m.put("trend", trend);

        // 任务状态饼
        m.put("statusPie", qList(
                "SELECT task_status name, COUNT(*) value FROM eam_inspection_task " +
                "WHERE deleted=0 GROUP BY task_status"));

        // 即将到期点检任务 TOP 10
        m.put("upcomingList", qList(
                "SELECT t.task_code taskCode, IFNULL(e.equipment_name,'-') equipmentName, " +
                "IFNULL(t.inspector_name,'-') inspectorName, " +
                "t.planned_end_time plannedEnd, t.task_status taskStatus " +
                "FROM eam_inspection_task t " +
                "LEFT JOIN eam_equipment e ON e.id=t.equipment_id " +
                "WHERE t.deleted=0 AND t.task_status IN (0,1) " +
                "ORDER BY t.planned_end_time ASC LIMIT 10"));
        return m;
    }

    // ==================== TAB 4: 维修工单 ====================
    private Map<String, Object> getRepair() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pendingRequest", q1("SELECT COUNT(*) FROM eam_repair_request WHERE deleted=0 AND status='pending'", 0L));
        m.put("inProgressOrder", q1("SELECT COUNT(*) FROM eam_repair_management WHERE deleted=0 AND status IN ('in_progress','paused')", 0L));
        m.put("completedOrder", q1("SELECT COUNT(*) FROM eam_repair_management WHERE deleted=0 AND status='completed'", 0L));
        m.put("urgentOrder", q1("SELECT COUNT(*) FROM eam_repair_management WHERE deleted=0 " +
                "AND status NOT IN ('completed','cancelled') AND priority IN ('urgent','high')", 0L));

        // 12 月维修工单趋势(单条 GROUP BY 拉两组)
        YearMonth ym = YearMonth.now();
        Map<String, Long> repCreate = monthGroup(
                "SELECT DATE_FORMAT(create_time,'%Y-%m') m, COUNT(*) c FROM eam_repair_management " +
                "WHERE deleted=0 AND create_time >= DATE_SUB(CURDATE(), INTERVAL 11 MONTH) " +
                "GROUP BY DATE_FORMAT(create_time,'%Y-%m')");
        Map<String, Long> repDone = monthGroup(
                "SELECT DATE_FORMAT(actual_end_time,'%Y-%m') m, COUNT(*) c FROM eam_repair_management " +
                "WHERE deleted=0 AND status='completed' AND actual_end_time >= DATE_SUB(CURDATE(), INTERVAL 11 MONTH) " +
                "GROUP BY DATE_FORMAT(actual_end_time,'%Y-%m')");
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 11; i >= 0; i--) {
            YearMonth ymi = ym.minusMonths(i);
            String key = ymi.toString();
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("month", ymi.getMonthValue() + "月");
            r.put("created", repCreate.getOrDefault(key, 0L));
            r.put("completed", repDone.getOrDefault(key, 0L));
            trend.add(r);
        }
        m.put("trend", trend);

        // 优先级分布
        m.put("priorityPie", qList(
                "SELECT IFNULL(NULLIF(priority,''),'normal') name, COUNT(*) value " +
                "FROM eam_repair_management WHERE deleted=0 GROUP BY priority"));

        // 当前维修工单(进行中/暂停/待处理) TOP 10
        m.put("orderList", qList(
                "SELECT work_order_no workOrderNo, IFNULL(equipment_name,'-') equipmentName, " +
                "IFNULL(priority,'normal') priority, status, IFNULL(assignee_name,'-') assigneeName, " +
                "planned_end_time plannedEnd " +
                "FROM eam_repair_management WHERE deleted=0 AND status NOT IN ('completed','cancelled') " +
                "ORDER BY FIELD(priority,'urgent','high','normal','low'), planned_end_time ASC LIMIT 10"));
        return m;
    }

    // ==================== 实时消息跑马灯 ====================
    private List<Map<String, String>> getTicker() {
        List<Map<String, String>> list = new ArrayList<>();
        try {
            // 1) 即将到期保养(3 条)
            List<Map<String, Object>> upcoming = qList(
                    "SELECT equipment_no equipmentNo, equipment_name equipmentName, next_maintenance_date nextDate, " +
                    "DATEDIFF(next_maintenance_date, CURDATE()) daysLeft " +
                    "FROM eam_equipment WHERE deleted=0 AND next_maintenance_date IS NOT NULL " +
                    "AND next_maintenance_date <= DATE_ADD(CURDATE(), INTERVAL 7 DAY) " +
                    "ORDER BY next_maintenance_date ASC LIMIT 3");
            for (Map<String, Object> u : upcoming) {
                Object days = u.get("daysLeft");
                long d = days == null ? 0 : ((Number) days).longValue();
                String tip = d < 0 ? String.format("⚠ 设备 %s(%s)保养已逾期 %d 天", u.get("equipmentName"), u.get("equipmentNo"), -d)
                        : String.format("⏰ 设备 %s(%s)将于 %d 天后到期保养", u.get("equipmentName"), u.get("equipmentNo"), d);
                list.add(msg(d < 0 ? "var(--red)" : "var(--orange)", tip));
            }

            // 2) 最新告警(3 条) — alarm_status 英文枚举翻译
            String alarmCase = "CASE UPPER(IFNULL(log.alarm_status,'')) " +
                    " WHEN 'CRITICAL' THEN '严重' WHEN 'FATAL' THEN '致命' " +
                    " WHEN 'HIGH' THEN '高级' WHEN 'ERROR' THEN '错误' " +
                    " WHEN 'WARN' THEN '警告' WHEN 'WARNING' THEN '警告' " +
                    " WHEN 'INFO' THEN '提示' WHEN 'NORMAL' THEN '正常' " +
                    " WHEN '' THEN '-' ELSE log.alarm_status END ";
            List<Map<String, Object>> alarms = qList(
                    "SELECT IFNULL(e.equipment_name,'-') equipmentName, " + alarmCase + " alarmStatusCn, " +
                    "IFNULL(log.alarm_message,'-') alarmMessage " +
                    "FROM eam_equipment_alarm_log log LEFT JOIN eam_equipment e ON e.id=log.equipment_id " +
                    "WHERE log.deleted=0 ORDER BY log.occurred_at DESC LIMIT 3");
            for (Map<String, Object> a : alarms) {
                list.add(msg("var(--red)", String.format("🔔 设备告警:%s [%s] %s",
                        a.get("equipmentName"), a.get("alarmStatusCn"), a.get("alarmMessage"))));
            }

            // 3) 待处理报修(3 条)
            List<Map<String, Object>> reqs = qList(
                    "SELECT request_no requestNo, IFNULL(equipment_name,'-') equipmentName, " +
                    "IFNULL(reporter_name,'-') reporterName " +
                    "FROM eam_repair_request WHERE deleted=0 AND status='pending' " +
                    "ORDER BY report_time DESC LIMIT 3");
            for (Map<String, Object> r : reqs) {
                list.add(msg("var(--orange)", String.format("🛠 报修申请 %s 待受理 - 设备:%s 报修人:%s",
                        r.get("requestNo"), r.get("equipmentName"), r.get("reporterName"))));
            }

            // 4) 进行中维修工单(3 条)
            List<Map<String, Object>> orders = qList(
                    "SELECT work_order_no workOrderNo, IFNULL(equipment_name,'-') equipmentName, " +
                    "IFNULL(assignee_name,'-') assigneeName " +
                    "FROM eam_repair_management WHERE deleted=0 AND status='in_progress' " +
                    "ORDER BY actual_start_time DESC LIMIT 3");
            for (Map<String, Object> o : orders) {
                list.add(msg("var(--cyan)", String.format("🔧 维修工单 %s 进行中 - 设备:%s 责任人:%s",
                        o.get("workOrderNo"), o.get("equipmentName"), o.get("assigneeName"))));
            }

            // 5) 最近完成保养(2 条)
            List<Map<String, Object>> mt = qList(
                    "SELECT t.task_code taskCode, IFNULL(e.equipment_name,'-') equipmentName, " +
                    "IFNULL(t.executor_name,'-') executorName " +
                    "FROM eam_maintenance_task t LEFT JOIN eam_equipment e ON e.id=t.equipment_id " +
                    "WHERE t.deleted=0 AND t.task_status=2 ORDER BY t.actual_end_time DESC LIMIT 2");
            for (Map<String, Object> t : mt) {
                list.add(msg("var(--green)", String.format("✓ 保养任务 %s 已完成 - 设备:%s 执行人:%s",
                        t.get("taskCode"), t.get("equipmentName"), t.get("executorName"))));
            }
        } catch (Exception ignore) {}

        if (list.isEmpty()) {
            list.add(msg("var(--green)", "EAM 系统运行正常,暂无重要消息"));
        }
        return list;
    }

    private Map<String, String> msg(String color, String text) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("c", color);
        m.put("t", text);
        return m;
    }

    // ==================== 工具 ====================
    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> T q1(String sql, T defaultValue, Object... args) {
        try {
            Class<?> cls = defaultValue == null ? Object.class : defaultValue.getClass();
            Object v = jdbc.queryForObject(sql, cls, args);
            return v == null ? defaultValue : (T) v;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /** SELECT m,c FROM ... GROUP BY m → Map<String,Long> */
    private Map<String, Long> monthGroup(String sql) {
        Map<String, Long> map = new HashMap<>();
        try {
            for (Map<String, Object> r : jdbc.queryForList(sql)) {
                Object k = r.get("m");
                if (k != null) map.put(k.toString(), ((Number) r.get("c")).longValue());
            }
        } catch (Exception ignore) {}
        return map;
    }

    /** SELECT d,c FROM ... GROUP BY d → Map<String,Long> */
    private Map<String, Long> dayGroup(String sql) {
        Map<String, Long> map = new HashMap<>();
        try {
            for (Map<String, Object> r : jdbc.queryForList(sql)) {
                Object k = r.get("d");
                if (k != null) map.put(k.toString(), ((Number) r.get("c")).longValue());
            }
        } catch (Exception ignore) {}
        return map;
    }

    private List<Map<String, Object>> qList(String sql, Object... args) {
        try {
            return jdbc.queryForList(sql, args);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
