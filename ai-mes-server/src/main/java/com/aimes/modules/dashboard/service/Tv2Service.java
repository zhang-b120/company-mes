package com.aimes.modules.dashboard.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * TV2 大屏数据聚合服务
 * 用 jdbcTemplate 直接 SQL 查,避免依赖太多 Service / 循环依赖
 * 任何查询失败都用 0 / 空列表兜底,保证大屏不崩
 */
@Service
@RequiredArgsConstructor
public class Tv2Service {

    private final JdbcTemplate jdbc;

    public Map<String, Object> getAll() {
        Map<String, Object> all = new LinkedHashMap<>();
        all.put("production", getProduction());
        all.put("sales", getSales());
        all.put("inventory", getInventory());
        all.put("purchase", getPurchase());
        all.put("quality", getQuality());
        all.put("ticker", getTicker());
        return all;
    }

    // ==================== 实时消息跑马灯 ====================
    /**
     * 聚合最近的真实业务事件,按时间倒序组装跑马灯消息
     * 每条 { c: 颜色, t: 文案 }
     */
    private List<Map<String, String>> getTicker() {
        List<Map<String, String>> list = new ArrayList<>();
        try {
            // 1) 库存预警(最严重的 3 条)
            List<Map<String, Object>> warns = qList(
                    "SELECT m.material_name name, m.material_no code, " +
                    "IFNULL(w.warehouse_name,'-') warehouse, " +
                    "i.available_qty qty, " +
                    "IFNULL(NULLIF(i.safety_stock,0), IFNULL(m.min_stock,0)) safe " +
                    "FROM biz_inventory i " +
                    "LEFT JOIN biz_material m ON m.id=i.material_id " +
                    "LEFT JOIN sys_warehouse w ON w.id=i.warehouse_id " +
                    "WHERE i.deleted=0 AND IFNULL(m.deleted,0)=0 " +
                    "AND IFNULL(NULLIF(i.safety_stock,0), IFNULL(m.min_stock,0)) > 0 " +
                    "AND i.available_qty < IFNULL(NULLIF(i.safety_stock,0), IFNULL(m.min_stock,0)) " +
                    "ORDER BY i.available_qty ASC LIMIT 3");
            for (Map<String, Object> w : warns) {
                list.add(msg("var(--orange)",
                    String.format("库存预警:%s(%s)在 %s 库存 %s,低于阈值 %s,请及时补货",
                        w.get("name"), w.get("code"), w.get("warehouse"), w.get("qty"), w.get("safe"))));
            }

            // 2) 逾期订单(最早交期的 2 条)
            List<Map<String, Object>> overdue = qList(
                    "SELECT o.order_no orderNo, o.delivery_date deliveryDate, " +
                    "IFNULL(c.customer_name,'-') customerName " +
                    "FROM biz_sales_order o LEFT JOIN biz_customer c ON c.id=o.customer_id " +
                    "WHERE o.deleted=0 AND o.delivery_date < CURDATE() " +
                    "AND o.status NOT IN ('shipped','settled','cancelled','voided') " +
                    "ORDER BY o.delivery_date ASC LIMIT 2");
            for (Map<String, Object> o : overdue) {
                list.add(msg("var(--red)",
                    String.format("⚠ 订单 %s 已逾期(交期 %s),客户:%s,请尽快处理",
                        o.get("orderNo"), o.get("deliveryDate"), o.get("customerName"))));
            }

            // 3) 最近完工的工单(3 条)
            List<Map<String, Object>> finished = qList(
                    "SELECT wo.wo_no woNo, wo.completed_qty qty, m.material_name productName " +
                    "FROM biz_work_order wo LEFT JOIN biz_material m ON m.id=wo.product_id " +
                    "WHERE wo.deleted=0 AND wo.status='completed' " +
                    "ORDER BY wo.update_time DESC LIMIT 3");
            for (Map<String, Object> w : finished) {
                list.add(msg("var(--green)",
                    String.format("工单 %s %s已完工,生产数量 %s 件",
                        w.get("woNo"), w.get("productName"), w.get("qty"))));
            }

            // 4) 最近确认的销售订单(3 条)
            List<Map<String, Object>> recentSo = qList(
                    "SELECT o.order_no orderNo, o.total_amount amount, " +
                    "IFNULL(c.customer_name,'-') customerName, o.status " +
                    "FROM biz_sales_order o LEFT JOIN biz_customer c ON c.id=o.customer_id " +
                    "WHERE o.deleted=0 ORDER BY o.create_time DESC LIMIT 3");
            for (Map<String, Object> o : recentSo) {
                list.add(msg("var(--cyan)",
                    String.format("销售订单 %s(%s),金额 ¥%s",
                        o.get("orderNo"), o.get("customerName"), o.get("amount"))));
            }

            // 5) 最近采购订单
            List<Map<String, Object>> recentPo = qList(
                    "SELECT po.po_no poNo, po.total_amount amount, IFNULL(s.supplier_name,'-') supplierName " +
                    "FROM biz_purchase_order po LEFT JOIN biz_supplier s ON s.id=po.supplier_id " +
                    "WHERE po.deleted=0 ORDER BY po.create_time DESC LIMIT 2");
            for (Map<String, Object> p : recentPo) {
                list.add(msg("var(--purple)",
                    String.format("采购订单 %s 已下达,供应商:%s,金额 ¥%s",
                        p.get("poNo"), p.get("supplierName"), p.get("amount"))));
            }

            // 6) 质检不合格(最近 2 条)
            List<Map<String, Object>> qcFail = qList(
                    "SELECT q.task_no taskNo, m.material_name materialName " +
                    "FROM biz_qc_task q LEFT JOIN biz_material m ON m.id=q.material_id " +
                    "WHERE q.deleted=0 AND q.result='fail' " +
                    "ORDER BY q.update_time DESC LIMIT 2");
            for (Map<String, Object> q : qcFail) {
                list.add(msg("var(--red)",
                    String.format("⚠ 质检任务 %s 不合格,物料:%s,请处理",
                        q.get("taskNo"), q.get("materialName"))));
            }
        } catch (Exception ignore) {}

        // 至少要有几条,空数据时用兜底
        if (list.isEmpty()) {
            list.add(msg("var(--green)", "系统运行正常,暂无重要消息"));
        }
        return list;
    }

    private Map<String, String> msg(String color, String text) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("c", color);
        m.put("t", text);
        return m;
    }

    // ==================== 生产 ====================
    private Map<String, Object> getProduction() {
        Map<String, Object> m = new LinkedHashMap<>();
        // KPI
        m.put("totalWo", q1("SELECT COUNT(*) FROM biz_work_order WHERE deleted=0", 0L));
        m.put("completedWo", q1("SELECT COUNT(*) FROM biz_work_order WHERE deleted=0 AND status='completed'", 0L));
        m.put("inProgressWo", q1("SELECT COUNT(*) FROM biz_work_order WHERE deleted=0 AND status='in_progress'", 0L));
        m.put("totalQty", q1("SELECT IFNULL(SUM(completed_qty),0) FROM biz_work_order WHERE deleted=0", 0));

        // 近 7 日完成趋势(按 update_time 当 status=completed 的近似)
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = LocalDate.now().minusDays(i);
            Long planned = q1("SELECT COUNT(*) FROM biz_work_order WHERE deleted=0 AND DATE(create_time)=?", 0L, d);
            Long done = q1("SELECT COUNT(*) FROM biz_work_order WHERE deleted=0 AND status='completed' AND DATE(update_time)=?", 0L, d);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("date", d.toString().substring(5));
            r.put("planned", planned);
            r.put("completed", done);
            trend.add(r);
        }
        m.put("trend", trend);

        // 状态饼图
        m.put("statusPie", qList(
                "SELECT status name, COUNT(*) value FROM biz_work_order WHERE deleted=0 GROUP BY status"));

        // 产品(产线)分布 TOP 5
        m.put("productBar", qList(
                "SELECT m.material_name name, COUNT(*) value " +
                "FROM biz_work_order wo LEFT JOIN biz_material m ON m.id=wo.product_id " +
                "WHERE wo.deleted=0 GROUP BY wo.product_id, m.material_name " +
                "ORDER BY value DESC LIMIT 5"));
        return m;
    }

    // ==================== 销售 ====================
    private Map<String, Object> getSales() {
        Map<String, Object> m = new LinkedHashMap<>();
        YearMonth ym = YearMonth.now();
        LocalDate firstDay = ym.atDay(1);
        LocalDate lastDay = ym.atEndOfMonth();

        m.put("monthAmount", q1("SELECT IFNULL(SUM(total_amount),0) FROM biz_sales_order " +
                "WHERE deleted=0 AND DATE(create_time) BETWEEN ? AND ?", 0, firstDay, lastDay));
        m.put("toShip", q1("SELECT COUNT(*) FROM biz_sales_order WHERE deleted=0 " +
                "AND status IN ('confirmed','in_production','partial_shipped')", 0L));
        m.put("overdue", q1("SELECT COUNT(*) FROM biz_sales_order WHERE deleted=0 " +
                "AND delivery_date < CURDATE() AND status NOT IN ('shipped','settled','cancelled','voided')", 0L));
        m.put("activeCustomer", q1("SELECT COUNT(DISTINCT customer_id) FROM biz_sales_order " +
                "WHERE deleted=0 AND status NOT IN ('cancelled','voided','settled')", 0L));

        // 12 月销售趋势
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 11; i >= 0; i--) {
            YearMonth ymi = ym.minusMonths(i);
            BigDecimal amt = q1("SELECT IFNULL(SUM(total_amount),0) FROM biz_sales_order " +
                    "WHERE deleted=0 AND DATE_FORMAT(create_time,'%Y-%m')=?", BigDecimal.ZERO, ymi.toString());
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("month", ymi.getMonthValue() + "月");
            r.put("amount", amt);
            trend.add(r);
        }
        m.put("trend", trend);

        // 订单状态分布
        m.put("statusPie", qList(
                "SELECT status name, COUNT(*) value FROM biz_sales_order WHERE deleted=0 GROUP BY status"));

        // TOP 5 客户(按 total_amount)
        m.put("customerTop", qList(
                "SELECT c.customer_name name, IFNULL(SUM(o.total_amount),0) value " +
                "FROM biz_sales_order o LEFT JOIN biz_customer c ON c.id=o.customer_id " +
                "WHERE o.deleted=0 GROUP BY o.customer_id, c.customer_name " +
                "ORDER BY value DESC LIMIT 5"));
        return m;
    }

    // ==================== 库存 ====================
    private Map<String, Object> getInventory() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("materialKinds", q1("SELECT COUNT(*) FROM biz_material WHERE deleted=0 AND status='active'", 0L));
        // 预警数:库存行级(每个 material × warehouse 单独算)
        // 阈值优先 i.safety_stock,fallback m.min_stock;过量看 m.max_stock
        m.put("warnKinds", q1(
                "SELECT COUNT(*) FROM biz_inventory i LEFT JOIN biz_material m ON m.id=i.material_id " +
                "WHERE i.deleted=0 AND IFNULL(m.deleted,0)=0 AND (" +
                "  (IFNULL(NULLIF(i.safety_stock,0), IFNULL(m.min_stock,0)) > 0 " +
                "    AND i.available_qty < IFNULL(NULLIF(i.safety_stock,0), IFNULL(m.min_stock,0))) " +
                "  OR (m.max_stock > 0 AND i.available_qty > m.max_stock) " +
                ")", 0L));
        // 本期入库批次(本月)
        YearMonth ym = YearMonth.now();
        m.put("inBatch", q1("SELECT COUNT(*) FROM biz_inv_history WHERE deleted=0 " +
                "AND inv_type LIKE '%_in%' AND DATE_FORMAT(create_time,'%Y-%m')=?", 0L, ym.toString()));
        m.put("outBatch", q1("SELECT COUNT(*) FROM biz_inv_history WHERE deleted=0 " +
                "AND inv_type LIKE '%_out%' AND DATE_FORMAT(create_time,'%Y-%m')=?", 0L, ym.toString()));

        // 30 日出入趋势
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 29; i >= 0; i--) {
            LocalDate d = LocalDate.now().minusDays(i);
            Long in = q1("SELECT COUNT(*) FROM biz_inv_history WHERE deleted=0 AND inv_type LIKE '%_in%' AND DATE(create_time)=?", 0L, d);
            Long out = q1("SELECT COUNT(*) FROM biz_inv_history WHERE deleted=0 AND inv_type LIKE '%_out%' AND DATE(create_time)=?", 0L, d);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("date", d.toString().substring(5));
            r.put("in", in);
            r.put("out", out);
            trend.add(r);
        }
        m.put("trend", trend);

        // 仓库占比(按 total_qty 求和;sys_warehouse 字段是 warehouse_name)
        m.put("warehousePie", qList(
                "SELECT IFNULL(w.warehouse_name,'未分配') name, IFNULL(SUM(i.total_qty),0) value " +
                "FROM biz_inventory i LEFT JOIN sys_warehouse w ON w.id=i.warehouse_id " +
                "WHERE i.deleted=0 GROUP BY i.warehouse_id, w.warehouse_name " +
                "HAVING value > 0 ORDER BY value DESC LIMIT 6"));

        // 预警明细 TOP 10(行级:每个 material×warehouse 单独一条,带仓库名)
        m.put("warnList", qList(
                "SELECT m.material_name name, m.material_no code, " +
                "IFNULL(w.warehouse_name,'-') warehouse, " +
                "IFNULL(NULLIF(i.safety_stock,0), IFNULL(m.min_stock,0)) safe, " +
                "i.available_qty qty, m.max_stock maxStock " +
                "FROM biz_inventory i " +
                "LEFT JOIN biz_material m ON m.id=i.material_id " +
                "LEFT JOIN sys_warehouse w ON w.id=i.warehouse_id " +
                "WHERE i.deleted=0 AND IFNULL(m.deleted,0)=0 AND (" +
                "  (IFNULL(NULLIF(i.safety_stock,0), IFNULL(m.min_stock,0)) > 0 " +
                "    AND i.available_qty < IFNULL(NULLIF(i.safety_stock,0), IFNULL(m.min_stock,0))) " +
                "  OR (m.max_stock > 0 AND i.available_qty > m.max_stock) " +
                ") ORDER BY i.available_qty ASC LIMIT 10"));
        return m;
    }

    // ==================== 采购 ====================
    private Map<String, Object> getPurchase() {
        Map<String, Object> m = new LinkedHashMap<>();
        YearMonth ym = YearMonth.now();
        m.put("monthAmount", q1("SELECT IFNULL(SUM(total_amount),0) FROM biz_purchase_order " +
                "WHERE deleted=0 AND DATE_FORMAT(create_time,'%Y-%m')=?", 0, ym.toString()));
        m.put("toReceive", q1("SELECT COUNT(*) FROM biz_purchase_order WHERE deleted=0 " +
                "AND status IN ('confirmed','partial_receipt')", 0L));
        m.put("supplierCount", q1("SELECT COUNT(*) FROM biz_supplier WHERE deleted=0 AND status='active'", 0L));
        // 准时到货率(简化:按收料单 received 状态比例)
        Long receivedAll = q1("SELECT COUNT(*) FROM biz_purchase_receipt WHERE deleted=0", 0L);
        Long onTime = q1("SELECT COUNT(*) FROM biz_purchase_receipt WHERE deleted=0 AND status IN ('received','closed')", 0L);
        int rate = receivedAll > 0 ? (int) Math.round(onTime * 100.0 / receivedAll) : 100;
        m.put("onTimeRate", rate);

        // 12 月采购笔数
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 11; i >= 0; i--) {
            YearMonth ymi = ym.minusMonths(i);
            Long cnt = q1("SELECT COUNT(*) FROM biz_purchase_order WHERE deleted=0 AND DATE_FORMAT(create_time,'%Y-%m')=?", 0L, ymi.toString());
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("month", ymi.getMonthValue() + "月");
            r.put("count", cnt);
            trend.add(r);
        }
        m.put("trend", trend);

        // 供应商占比 TOP 6(按订单笔数)
        m.put("supplierPie", qList(
                "SELECT s.supplier_name name, COUNT(*) value " +
                "FROM biz_purchase_order po LEFT JOIN biz_supplier s ON s.id=po.supplier_id " +
                "WHERE po.deleted=0 GROUP BY po.supplier_id, s.supplier_name " +
                "ORDER BY value DESC LIMIT 6"));
        // 采购金额 TOP 5 供应商(替换原雷达)
        m.put("supplierAmountTop", qList(
                "SELECT s.supplier_name name, IFNULL(SUM(po.total_amount),0) value " +
                "FROM biz_purchase_order po LEFT JOIN biz_supplier s ON s.id=po.supplier_id " +
                "WHERE po.deleted=0 AND s.supplier_name IS NOT NULL " +
                "GROUP BY po.supplier_id, s.supplier_name " +
                "HAVING value > 0 ORDER BY value DESC LIMIT 5"));
        return m;
    }

    // ==================== 质量 ====================
    private Map<String, Object> getQuality() {
        Map<String, Object> m = new LinkedHashMap<>();
        Long total = q1("SELECT COUNT(*) FROM biz_qc_task WHERE deleted=0", 0L);
        Long pass = q1("SELECT COUNT(*) FROM biz_qc_task WHERE deleted=0 AND result='pass'", 0L);
        int rate = total > 0 ? (int) Math.round(pass * 100.0 / total) : 100;
        m.put("passRate", rate);
        m.put("totalBatch", total);
        m.put("defectQty", q1("SELECT IFNULL(SUM(quantity),0) FROM biz_defect_record WHERE deleted=0", 0));
        // 返修工单数(只数未完工:status IN pending/in_progress;已 completed/cancelled 不算)
        m.put("reworkCount", q1(
                "SELECT COUNT(*) FROM biz_rework_order " +
                "WHERE deleted=0 AND status NOT IN ('completed','cancelled')", 0L));

        // 7 周检验趋势
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate weekStart = LocalDate.now().minusWeeks(i).with(java.time.DayOfWeek.MONDAY);
            LocalDate weekEnd = weekStart.plusDays(6);
            Long batch = q1("SELECT COUNT(*) FROM biz_qc_task WHERE deleted=0 AND DATE(create_time) BETWEEN ? AND ?", 0L, weekStart, weekEnd);
            Long passW = q1("SELECT COUNT(*) FROM biz_qc_task WHERE deleted=0 AND result='pass' AND DATE(create_time) BETWEEN ? AND ?", 0L, weekStart, weekEnd);
            int passRateW = batch > 0 ? (int) Math.round(passW * 100.0 / batch) : 100;
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("week", "W" + (7 - i));
            r.put("batch", batch);
            r.put("passRate", passRateW);
            trend.add(r);
        }
        m.put("trend", trend);

        // 缺陷分类 TOP(SQL 层直接转中文,涵盖英文/脏值/空值)
        m.put("defectPie", qList(
                "SELECT " +
                "  CASE LOWER(IFNULL(defect_type,'')) " +
                "    WHEN 'appearance' THEN '外观缺陷' " +
                "    WHEN 'dimensional' THEN '尺寸超差' " +
                "    WHEN 'dimension' THEN '尺寸超差' " +
                "    WHEN 'performance' THEN '性能缺陷' " +
                "    WHEN 'function' THEN '功能缺陷' " +
                "    WHEN 'material' THEN '材料缺陷' " +
                "    WHEN 'process' THEN '工艺缺陷' " +
                "    WHEN 'assembly' THEN '装配缺陷' " +
                "    WHEN 'package' THEN '包装缺陷' " +
                "    WHEN 'packaging' THEN '包装缺陷' " +
                "    WHEN 'qc_fail' THEN '质检不合格' " +
                "    WHEN 'qc_pass' THEN '质检合格' " +
                "    WHEN 'other' THEN '其他' " +
                "    WHEN 'unknown' THEN '其他' " +
                "    WHEN '' THEN '其他' " +
                "    ELSE defect_type " +
                "  END name, COUNT(*) value " +
                "FROM biz_defect_record WHERE deleted=0 GROUP BY defect_type ORDER BY value DESC LIMIT 6"));

        // 质检类型分布(SQL 层直接转中文,避免前端缓存或值脏导致的英文残留)
        m.put("qcTypePie", qList(
                "SELECT " +
                "  CASE LOWER(IFNULL(qc_type,'')) " +
                "    WHEN 'incoming' THEN '来料检 IQC' " +
                "    WHEN 'process' THEN '过程检 IPQC' " +
                "    WHEN 'final' THEN '成品检 FQC' " +
                "    WHEN 'outgoing' THEN '出货检 OQC' " +
                "    WHEN 'outsource' THEN '外协检' " +
                "    WHEN 'semi' THEN '半成品检' " +
                "    WHEN 'qc_pass' THEN '质检通过' " +
                "    WHEN 'qc_fail' THEN '质检不通过' " +
                "    WHEN 'pending' THEN '待质检' " +
                "    WHEN 'completed' THEN '已完成' " +
                "    WHEN '' THEN '其他' " +
                "    ELSE qc_type " +
                "  END name, COUNT(*) value " +
                "FROM biz_qc_task WHERE deleted=0 GROUP BY qc_type ORDER BY value DESC"));
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

    private List<Map<String, Object>> qList(String sql, Object... args) {
        try {
            return jdbc.queryForList(sql, args);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
