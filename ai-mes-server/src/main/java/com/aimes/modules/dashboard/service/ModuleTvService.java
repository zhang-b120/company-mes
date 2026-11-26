package com.aimes.modules.dashboard.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * 各业务模块独立大屏数据聚合服务
 * 每个 method 对应一个模块大屏(/tv/{module}),返回该模块完整 KPI + 图表数据
 *
 * 设计原则:
 * - 全部用 JdbcTemplate 直接查询,避免循环依赖与 Service 层副作用
 * - 任何 SQL 失败都用 0 / 空列表兜底,大屏永远不会因单点查询挂掉
 * - 每个模块返回 4 个 Tab + ticker
 */
@Service
@RequiredArgsConstructor
public class ModuleTvService {

    private final JdbcTemplate jdbc;

    // ============================================================
    // 1. 生产大屏
    // ============================================================
    public Map<String, Object> getProduction() {
        Map<String, Object> all = new LinkedHashMap<>();

        // overview Tab
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("totalWo", q1L("SELECT COUNT(*) FROM biz_work_order WHERE deleted=0"));
        overview.put("inProgressWo", q1L("SELECT COUNT(*) FROM biz_work_order WHERE deleted=0 AND status='in_progress'"));
        overview.put("completedWo", q1L("SELECT COUNT(*) FROM biz_work_order WHERE deleted=0 AND status='completed'"));
        overview.put("scheduledWo", q1L("SELECT COUNT(*) FROM biz_work_order WHERE deleted=0 AND status='scheduled'"));
        overview.put("trend", mergeDaily(
                aggDaily("biz_work_order", 7, "COUNT(*)", "create_time", null),
                List.of(aggDaily("biz_work_order", 7, "COUNT(*)", "update_time", "AND status='completed'")),
                new String[]{"planned", "completed"}));
        overview.put("statusPie", qList(
                "SELECT status name, COUNT(*) value FROM biz_work_order WHERE deleted=0 GROUP BY status"));
        overview.put("productTop", qList(
                "SELECT m.material_name name, COUNT(*) value " +
                "FROM biz_work_order wo LEFT JOIN biz_material m ON m.id=wo.product_id " +
                "WHERE wo.deleted=0 GROUP BY wo.product_id, m.material_name " +
                "ORDER BY value DESC LIMIT 8"));
        all.put("overview", overview);

        // process Tab (工序任务)
        Map<String, Object> process = new LinkedHashMap<>();
        process.put("pendingTask", q1L("SELECT COUNT(*) FROM biz_wo_process_task WHERE deleted=0 AND status='pending'"));
        process.put("readyTask", q1L("SELECT COUNT(*) FROM biz_wo_process_task WHERE deleted=0 AND status='ready'"));
        process.put("inProgressTask", q1L("SELECT COUNT(*) FROM biz_wo_process_task WHERE deleted=0 AND status='in_progress'"));
        process.put("completedTask", q1L("SELECT COUNT(*) FROM biz_wo_process_task WHERE deleted=0 AND status='completed'"));
        process.put("statusPie", qList(
                "SELECT status name, COUNT(*) value FROM biz_wo_process_task WHERE deleted=0 GROUP BY status"));
        process.put("stepTop", qList(
                "SELECT step_name name, COUNT(*) value FROM biz_wo_process_task " +
                "WHERE deleted=0 AND step_name IS NOT NULL " +
                "GROUP BY step_name ORDER BY value DESC LIMIT 8"));
        process.put("currentTaskList", qList(
                "SELECT wo.wo_no woNo, t.step_name stepName, IFNULL(t.assigned_team_name, t.assigned_user_name) assignee, " +
                "t.plan_qty planQty, t.reported_qty reportedQty, t.status status " +
                "FROM biz_wo_process_task t LEFT JOIN biz_work_order wo ON wo.id=t.work_order_id " +
                "WHERE t.deleted=0 AND t.status='in_progress' " +
                "ORDER BY t.start_time DESC LIMIT 10"));
        all.put("process", process);

        // capacity Tab
        Map<String, Object> capacity = new LinkedHashMap<>();
        BigDecimal todayQty = q1Bd("SELECT IFNULL(SUM(qualified_qty),0) FROM biz_work_report " +
                "WHERE deleted=0 AND DATE(create_time)=CURDATE()");
        BigDecimal monthQty = q1Bd("SELECT IFNULL(SUM(qualified_qty),0) FROM biz_work_report " +
                "WHERE deleted=0 AND DATE_FORMAT(create_time,'%Y-%m')=?", YearMonth.now().toString());
        capacity.put("todayOutput", todayQty);
        capacity.put("monthOutput", monthQty);
        capacity.put("avgCycleHours", q1Bd("SELECT IFNULL(AVG(work_hours),0) FROM biz_work_report WHERE deleted=0"));
        capacity.put("delayedWo", q1L("SELECT COUNT(*) FROM biz_work_order WHERE deleted=0 " +
                "AND status NOT IN ('completed','cancelled') AND delivery_date < CURDATE()"));

        capacity.put("outputTrend", mergeDaily(
                aggDailyBd("biz_work_report", 14, "IFNULL(SUM(qualified_qty),0)", "create_time", null),
                List.of(aggDailyBd("biz_work_report", 14, "IFNULL(SUM(defect_qty),0)", "create_time", null)),
                new String[]{"qualified", "defect"}));
        all.put("capacity", capacity);

        // exception Tab
        Map<String, Object> exception = new LinkedHashMap<>();
        exception.put("totalException", q1L("SELECT COUNT(*) FROM biz_exception_report WHERE deleted=0"));
        exception.put("openException", q1L("SELECT COUNT(*) FROM biz_exception_report WHERE deleted=0 AND status='open'"));
        exception.put("escalatedCount", q1L("SELECT COUNT(*) FROM biz_exception_report WHERE deleted=0 AND is_escalated=1"));
        exception.put("delayedWo", q1L("SELECT COUNT(*) FROM biz_work_order WHERE deleted=0 " +
                "AND status NOT IN ('completed','cancelled') AND delivery_date < CURDATE()"));
        exception.put("typePie", qList(
                "SELECT IFNULL(NULLIF(exception_type,''),'未分类') name, COUNT(*) value " +
                "FROM biz_exception_report WHERE deleted=0 GROUP BY exception_type"));
        exception.put("recentList", qList(
                "SELECT report_no reportNo, IFNULL(exception_type,'-') exceptionType, " +
                "IFNULL(urgency,'normal') urgency, status, IFNULL(description,'-') description, " +
                "create_time createTime " +
                "FROM biz_exception_report WHERE deleted=0 ORDER BY create_time DESC LIMIT 10"));
        all.put("exception", exception);

        all.put("ticker", productionTicker());
        return all;
    }

    private List<Map<String, String>> productionTicker() {
        List<Map<String, String>> list = new ArrayList<>();
        try {
            for (Map<String, Object> r : qList("SELECT wo.wo_no woNo, wo.completed_qty qty, m.material_name productName " +
                    "FROM biz_work_order wo LEFT JOIN biz_material m ON m.id=wo.product_id " +
                    "WHERE wo.deleted=0 AND wo.status='completed' ORDER BY wo.update_time DESC LIMIT 3"))
                list.add(msg("var(--green)", String.format("✓ 工单 %s(%s)已完工,合格 %s 件",
                        r.get("woNo"), r.get("productName"), r.get("qty"))));

            for (Map<String, Object> r : qList("SELECT wo.wo_no woNo, m.material_name productName " +
                    "FROM biz_work_order wo LEFT JOIN biz_material m ON m.id=wo.product_id " +
                    "WHERE wo.deleted=0 AND wo.status='in_progress' ORDER BY wo.update_time DESC LIMIT 3"))
                list.add(msg("var(--cyan)", String.format("⚙ 工单 %s(%s)生产中",
                        r.get("woNo"), r.get("productName"))));

            for (Map<String, Object> r : qList("SELECT wo.wo_no woNo, wo.delivery_date deliveryDate " +
                    "FROM biz_work_order wo WHERE wo.deleted=0 AND wo.delivery_date < CURDATE() " +
                    "AND wo.status NOT IN ('completed','cancelled') ORDER BY wo.delivery_date ASC LIMIT 2"))
                list.add(msg("var(--red)", String.format("⚠ 工单 %s 已逾期(交期 %s)",
                        r.get("woNo"), r.get("deliveryDate"))));
        } catch (Exception ignore) {}
        if (list.isEmpty()) list.add(msg("var(--green)", "生产线运行正常"));
        return list;
    }

    // ============================================================
    // 2. 销售大屏
    // ============================================================
    public Map<String, Object> getSales() {
        Map<String, Object> all = new LinkedHashMap<>();
        YearMonth ym = YearMonth.now();

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("monthAmount", q1Bd("SELECT IFNULL(SUM(total_amount),0) FROM biz_sales_order " +
                "WHERE deleted=0 AND DATE_FORMAT(create_time,'%Y-%m')=?", ym.toString()));
        overview.put("monthCount", q1L("SELECT COUNT(*) FROM biz_sales_order " +
                "WHERE deleted=0 AND DATE_FORMAT(create_time,'%Y-%m')=?", ym.toString()));
        overview.put("totalUnreceived", q1Bd("SELECT IFNULL(SUM(unreceived_amount),0) FROM biz_receivable " +
                "WHERE deleted=0 AND status<>'received'"));
        overview.put("activeCustomer", q1L("SELECT COUNT(DISTINCT customer_id) FROM biz_sales_order " +
                "WHERE deleted=0 AND status NOT IN ('cancelled','voided','settled')"));
        overview.put("monthTrend", mergeMonthly(
                aggMonthly("biz_sales_order", 12, "IFNULL(SUM(total_amount),0)", "create_time", null),
                List.of(aggMonthly("biz_sales_order", 12, "COUNT(*)", "create_time", null)),
                new String[]{"amount", "count"}));
        overview.put("statusPie", qList(
                "SELECT status name, COUNT(*) value FROM biz_sales_order WHERE deleted=0 GROUP BY status"));
        all.put("overview", overview);

        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("totalCustomer", q1L("SELECT COUNT(*) FROM biz_customer WHERE deleted=0"));
        customer.put("activeCustomer", q1L("SELECT COUNT(DISTINCT customer_id) FROM biz_sales_order " +
                "WHERE deleted=0 AND DATE_FORMAT(create_time,'%Y-%m')=?", ym.toString()));
        customer.put("newCustomer", q1L("SELECT COUNT(*) FROM biz_customer WHERE deleted=0 " +
                "AND DATE_FORMAT(create_time,'%Y-%m')=?", ym.toString()));
        customer.put("topCount", q1L("SELECT COUNT(DISTINCT customer_id) FROM biz_sales_order " +
                "WHERE deleted=0 GROUP BY customer_id HAVING SUM(total_amount) > 0 LIMIT 100"));
        customer.put("amountTop", qList(
                "SELECT IFNULL(c.customer_name,'-') name, IFNULL(SUM(o.total_amount),0) value " +
                "FROM biz_sales_order o LEFT JOIN biz_customer c ON c.id=o.customer_id " +
                "WHERE o.deleted=0 GROUP BY o.customer_id, c.customer_name " +
                "ORDER BY value DESC LIMIT 8"));
        customer.put("countTop", qList(
                "SELECT IFNULL(c.customer_name,'-') name, COUNT(*) value " +
                "FROM biz_sales_order o LEFT JOIN biz_customer c ON c.id=o.customer_id " +
                "WHERE o.deleted=0 GROUP BY o.customer_id, c.customer_name " +
                "ORDER BY value DESC LIMIT 8"));
        all.put("customer", customer);

        Map<String, Object> shipment = new LinkedHashMap<>();
        shipment.put("toShip", q1L("SELECT COUNT(*) FROM biz_sales_order WHERE deleted=0 " +
                "AND status IN ('confirmed','in_production','partial_shipped')"));
        shipment.put("partialShipped", q1L("SELECT COUNT(*) FROM biz_sales_order WHERE deleted=0 AND status='partial_shipped'"));
        shipment.put("shipped", q1L("SELECT COUNT(*) FROM biz_sales_order WHERE deleted=0 AND status='shipped'"));
        shipment.put("overdue", q1L("SELECT COUNT(*) FROM biz_sales_order WHERE deleted=0 " +
                "AND delivery_date < CURDATE() AND status NOT IN ('shipped','settled','cancelled','voided')"));
        shipment.put("upcomingList", qList(
                "SELECT o.order_no orderNo, IFNULL(c.customer_name,'-') customerName, " +
                "o.delivery_date deliveryDate, o.total_amount amount, o.status " +
                "FROM biz_sales_order o LEFT JOIN biz_customer c ON c.id=o.customer_id " +
                "WHERE o.deleted=0 AND o.status NOT IN ('shipped','settled','cancelled','voided') " +
                "ORDER BY o.delivery_date ASC LIMIT 12"));
        all.put("shipment", shipment);

        Map<String, Object> profit = new LinkedHashMap<>();
        BigDecimal monthSales = q1Bd("SELECT IFNULL(SUM(total_amount),0) FROM biz_sales_order " +
                "WHERE deleted=0 AND DATE_FORMAT(create_time,'%Y-%m')=?", ym.toString());
        BigDecimal monthCost = q1Bd("SELECT IFNULL(SUM(total_cost),0) FROM biz_wo_cost " +
                "WHERE deleted=0 AND DATE_FORMAT(create_time,'%Y-%m')=?", ym.toString());
        profit.put("monthRevenue", monthSales);
        profit.put("monthCost", monthCost);
        profit.put("monthProfit", monthSales.subtract(monthCost));
        profit.put("profitRate", monthSales.compareTo(BigDecimal.ZERO) > 0
                ? monthSales.subtract(monthCost).multiply(new BigDecimal(100)).divide(monthSales, 2, BigDecimal.ROUND_HALF_UP).intValue()
                : 0);
        profit.put("productAmountTop", qList(
                "SELECT IFNULL(m.material_name,'-') name, IFNULL(SUM(oi.amount),0) value " +
                "FROM biz_sales_order_item oi LEFT JOIN biz_material m ON m.id=oi.product_id " +
                "WHERE oi.deleted=0 GROUP BY oi.product_id, m.material_name " +
                "HAVING value > 0 ORDER BY value DESC LIMIT 8"));
        all.put("profit", profit);

        all.put("ticker", salesTicker());
        return all;
    }

    private List<Map<String, String>> salesTicker() {
        List<Map<String, String>> list = new ArrayList<>();
        try {
            for (Map<String, Object> r : qList("SELECT o.order_no orderNo, o.total_amount amount, " +
                    "IFNULL(c.customer_name,'-') customerName FROM biz_sales_order o " +
                    "LEFT JOIN biz_customer c ON c.id=o.customer_id WHERE o.deleted=0 " +
                    "ORDER BY o.create_time DESC LIMIT 3"))
                list.add(msg("var(--cyan)", String.format("📋 销售订单 %s(%s) 金额 ¥%s",
                        r.get("orderNo"), r.get("customerName"), r.get("amount"))));

            for (Map<String, Object> r : qList("SELECT o.order_no orderNo, o.delivery_date deliveryDate, " +
                    "IFNULL(c.customer_name,'-') customerName FROM biz_sales_order o " +
                    "LEFT JOIN biz_customer c ON c.id=o.customer_id WHERE o.deleted=0 " +
                    "AND o.delivery_date < CURDATE() AND o.status NOT IN ('shipped','settled','cancelled','voided') " +
                    "ORDER BY o.delivery_date ASC LIMIT 3"))
                list.add(msg("var(--red)", String.format("⚠ 订单 %s 已逾期 - 客户:%s 交期:%s",
                        r.get("orderNo"), r.get("customerName"), r.get("deliveryDate"))));

            for (Map<String, Object> r : qList("SELECT receivable_no rno, IFNULL(amount,0) amount " +
                    "FROM biz_receivable WHERE deleted=0 AND status='pending' " +
                    "ORDER BY due_date ASC LIMIT 2"))
                list.add(msg("var(--orange)", String.format("💰 应收 %s 待收款 ¥%s",
                        r.get("rno"), r.get("amount"))));
        } catch (Exception ignore) {}
        if (list.isEmpty()) list.add(msg("var(--green)", "销售业务运行正常"));
        return list;
    }

    // ============================================================
    // 3. 库存大屏
    // ============================================================
    public Map<String, Object> getInventory() {
        Map<String, Object> all = new LinkedHashMap<>();
        YearMonth ym = YearMonth.now();

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("materialKinds", q1L("SELECT COUNT(*) FROM biz_material WHERE deleted=0 AND status='active'"));
        overview.put("warehouseCount", q1L("SELECT COUNT(*) FROM sys_warehouse WHERE deleted=0 AND status='active'"));
        overview.put("totalStock", q1Bd("SELECT IFNULL(SUM(total_qty),0) FROM biz_inventory WHERE deleted=0"));
        overview.put("lockedStock", q1Bd("SELECT IFNULL(SUM(locked_qty),0) FROM biz_inventory WHERE deleted=0"));
        overview.put("warehousePie", qList(
                "SELECT IFNULL(w.warehouse_name,'未分配') name, IFNULL(SUM(i.total_qty),0) value " +
                "FROM biz_inventory i LEFT JOIN sys_warehouse w ON w.id=i.warehouse_id " +
                "WHERE i.deleted=0 GROUP BY i.warehouse_id, w.warehouse_name HAVING value > 0 " +
                "ORDER BY value DESC LIMIT 8"));
        overview.put("categoryPie", qList(
                "SELECT IFNULL(NULLIF(m.category,''),'未分类') name, IFNULL(SUM(i.total_qty),0) value " +
                "FROM biz_inventory i LEFT JOIN biz_material m ON m.id=i.material_id " +
                "WHERE i.deleted=0 GROUP BY m.category HAVING value > 0 ORDER BY value DESC LIMIT 8"));
        all.put("overview", overview);

        Map<String, Object> flow = new LinkedHashMap<>();
        flow.put("monthIn", q1L("SELECT COUNT(*) FROM biz_inv_history WHERE deleted=0 " +
                "AND inv_type LIKE '%_in%' AND DATE_FORMAT(create_time,'%Y-%m')=?", ym.toString()));
        flow.put("monthOut", q1L("SELECT COUNT(*) FROM biz_inv_history WHERE deleted=0 " +
                "AND inv_type LIKE '%_out%' AND DATE_FORMAT(create_time,'%Y-%m')=?", ym.toString()));
        flow.put("todayIn", q1L("SELECT COUNT(*) FROM biz_inv_history WHERE deleted=0 " +
                "AND inv_type LIKE '%_in%' AND DATE(create_time)=CURDATE()"));
        flow.put("todayOut", q1L("SELECT COUNT(*) FROM biz_inv_history WHERE deleted=0 " +
                "AND inv_type LIKE '%_out%' AND DATE(create_time)=CURDATE()"));

        flow.put("trend", mergeDaily(
                aggDaily("biz_inv_history", 30, "COUNT(*)", "create_time", "AND inv_type LIKE '%_in%'"),
                List.of(aggDaily("biz_inv_history", 30, "COUNT(*)", "create_time", "AND inv_type LIKE '%_out%'")),
                new String[]{"in", "out"}));
        flow.put("typePie", qList(
                "SELECT inv_type name, COUNT(*) value FROM biz_inv_history " +
                "WHERE deleted=0 AND DATE_FORMAT(create_time,'%Y-%m')=? GROUP BY inv_type " +
                "ORDER BY value DESC LIMIT 10", ym.toString()));
        all.put("flow", flow);

        Map<String, Object> warn = new LinkedHashMap<>();
        warn.put("shortageCount", q1L(
                "SELECT COUNT(*) FROM biz_inventory i LEFT JOIN biz_material m ON m.id=i.material_id " +
                "WHERE i.deleted=0 AND IFNULL(m.deleted,0)=0 " +
                "AND IFNULL(NULLIF(i.safety_stock,0), IFNULL(m.min_stock,0)) > 0 " +
                "AND i.available_qty < IFNULL(NULLIF(i.safety_stock,0), IFNULL(m.min_stock,0))"));
        warn.put("excessCount", q1L(
                "SELECT COUNT(*) FROM biz_inventory i LEFT JOIN biz_material m ON m.id=i.material_id " +
                "WHERE i.deleted=0 AND IFNULL(m.deleted,0)=0 AND m.max_stock > 0 AND i.available_qty > m.max_stock"));
        warn.put("zeroCount", q1L("SELECT COUNT(*) FROM biz_inventory WHERE deleted=0 AND available_qty <= 0"));
        warn.put("activeReservation", q1L("SELECT COUNT(*) FROM biz_inv_reservation WHERE deleted=0 AND status='active'"));
        warn.put("warnList", qList(
                "SELECT m.material_name name, m.material_no code, " +
                "IFNULL(w.warehouse_name,'-') warehouse, " +
                "IFNULL(NULLIF(i.safety_stock,0), IFNULL(m.min_stock,0)) safe, " +
                "i.available_qty qty, m.max_stock maxStock " +
                "FROM biz_inventory i LEFT JOIN biz_material m ON m.id=i.material_id " +
                "LEFT JOIN sys_warehouse w ON w.id=i.warehouse_id " +
                "WHERE i.deleted=0 AND IFNULL(m.deleted,0)=0 AND (" +
                "  (IFNULL(NULLIF(i.safety_stock,0), IFNULL(m.min_stock,0)) > 0 " +
                "    AND i.available_qty < IFNULL(NULLIF(i.safety_stock,0), IFNULL(m.min_stock,0))) " +
                "  OR (m.max_stock > 0 AND i.available_qty > m.max_stock)) " +
                "ORDER BY i.available_qty ASC LIMIT 12"));
        all.put("warn", warn);

        Map<String, Object> task = new LinkedHashMap<>();
        task.put("pendingTask", q1L("SELECT COUNT(*) FROM biz_inv_task WHERE deleted=0 AND status='pending'"));
        task.put("inProgressTask", q1L("SELECT COUNT(*) FROM biz_inv_task WHERE deleted=0 AND status='in_progress'"));
        task.put("completedTask", q1L("SELECT COUNT(*) FROM biz_inv_task WHERE deleted=0 AND status='completed'"));
        task.put("requestPending", q1L("SELECT COUNT(*) FROM biz_inv_request WHERE deleted=0 AND status='pending'"));
        task.put("statusPie", qList(
                "SELECT status name, COUNT(*) value FROM biz_inv_task WHERE deleted=0 GROUP BY status"));
        task.put("recentList", qList(
                "SELECT task_no taskNo, inv_type invType, status, batch_no batchNo, create_time createTime " +
                "FROM biz_inv_task WHERE deleted=0 ORDER BY create_time DESC LIMIT 10"));
        all.put("task", task);

        all.put("ticker", inventoryTicker());
        return all;
    }

    private List<Map<String, String>> inventoryTicker() {
        List<Map<String, String>> list = new ArrayList<>();
        try {
            for (Map<String, Object> r : qList("SELECT m.material_name name, m.material_no code, " +
                    "IFNULL(w.warehouse_name,'-') warehouse, i.available_qty qty " +
                    "FROM biz_inventory i LEFT JOIN biz_material m ON m.id=i.material_id " +
                    "LEFT JOIN sys_warehouse w ON w.id=i.warehouse_id " +
                    "WHERE i.deleted=0 AND IFNULL(m.deleted,0)=0 " +
                    "AND IFNULL(NULLIF(i.safety_stock,0), IFNULL(m.min_stock,0)) > 0 " +
                    "AND i.available_qty < IFNULL(NULLIF(i.safety_stock,0), IFNULL(m.min_stock,0)) " +
                    "ORDER BY i.available_qty ASC LIMIT 3"))
                list.add(msg("var(--orange)", String.format("⚠ %s(%s)在 %s 库存仅 %s,触发预警",
                        r.get("name"), r.get("code"), r.get("warehouse"), r.get("qty"))));

            for (Map<String, Object> r : qList("SELECT task_no taskNo, " + INV_TYPE_CASE + " invTypeCn " +
                    "FROM biz_inv_task WHERE deleted=0 AND status='completed' " +
                    "ORDER BY update_time DESC LIMIT 3"))
                list.add(msg("var(--green)", String.format("✓ 库存任务 %s [%s] 已完成",
                        r.get("taskNo"), r.get("invTypeCn"))));

            for (Map<String, Object> r : qList("SELECT history_no hno, " + INV_TYPE_CASE + " invTypeCn " +
                    "FROM biz_inv_history WHERE deleted=0 ORDER BY create_time DESC LIMIT 3"))
                list.add(msg("var(--cyan)", String.format("📦 流水 %s [%s]", r.get("hno"), r.get("invTypeCn"))));
        } catch (Exception ignore) {}
        if (list.isEmpty()) list.add(msg("var(--green)", "库存运行正常"));
        return list;
    }

    /** inv_type 英文枚举 → 中文(SQL 段) */
    private static final String INV_TYPE_CASE =
            "CASE LOWER(IFNULL(inv_type,'')) " +
            " WHEN 'purchase_in' THEN '采购入库' WHEN 'production_in' THEN '生产入库' " +
            " WHEN 'finished_in' THEN '成品入库' WHEN 'semi_in' THEN '半成品入库' " +
            " WHEN 'return_in' THEN '退货入库' WHEN 'rework_in' THEN '返修入库' " +
            " WHEN 'outsource_in' THEN '外协入库' WHEN 'transfer_in' THEN '调拨入库' " +
            " WHEN 'adjust_in' THEN '盘盈入库' WHEN 'stock_in' THEN '其他入库' " +
            " WHEN 'qc_in' THEN '质检入库' WHEN 'sales_out' THEN '销售出库' " +
            " WHEN 'production_out' THEN '生产领料' WHEN 'rework_out' THEN '返修出库' " +
            " WHEN 'outsource_out' THEN '外协发料' WHEN 'transfer_out' THEN '调拨出库' " +
            " WHEN 'adjust_out' THEN '盘亏出库' WHEN 'scrap_out' THEN '报废出库' " +
            " WHEN 'stock_out' THEN '其他出库' WHEN 'return_out' THEN '退料出库' " +
            " WHEN 'qc_out' THEN '质检出库' WHEN '' THEN '-' ELSE inv_type END ";

    // ============================================================
    // 4. 采购大屏
    // ============================================================
    public Map<String, Object> getPurchase() {
        Map<String, Object> all = new LinkedHashMap<>();
        YearMonth ym = YearMonth.now();

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("monthAmount", q1Bd("SELECT IFNULL(SUM(total_amount),0) FROM biz_purchase_order " +
                "WHERE deleted=0 AND DATE_FORMAT(create_time,'%Y-%m')=?", ym.toString()));
        overview.put("monthCount", q1L("SELECT COUNT(*) FROM biz_purchase_order " +
                "WHERE deleted=0 AND DATE_FORMAT(create_time,'%Y-%m')=?", ym.toString()));
        overview.put("toReceive", q1L("SELECT COUNT(*) FROM biz_purchase_order WHERE deleted=0 " +
                "AND status IN ('confirmed','partial_received')"));
        overview.put("totalUnpaid", q1Bd("SELECT IFNULL(SUM(unpaid_amount),0) FROM biz_payable " +
                "WHERE deleted=0 AND status<>'paid'"));
        overview.put("monthTrend", mergeMonthly(
                aggMonthly("biz_purchase_order", 12, "IFNULL(SUM(total_amount),0)", "create_time", null),
                List.of(aggMonthly("biz_purchase_order", 12, "COUNT(*)", "create_time", null)),
                new String[]{"amount", "count"}));
        overview.put("statusPie", qList(
                "SELECT status name, COUNT(*) value FROM biz_purchase_order WHERE deleted=0 GROUP BY status"));
        all.put("overview", overview);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("pendingReq", q1L("SELECT COUNT(*) FROM biz_purchase_request WHERE deleted=0 AND status='pending'"));
        request.put("approvedReq", q1L("SELECT COUNT(*) FROM biz_purchase_request WHERE deleted=0 AND status='approved'"));
        request.put("issuedReq", q1L("SELECT COUNT(*) FROM biz_purchase_request WHERE deleted=0 AND status='issued'"));
        request.put("rejectedReq", q1L("SELECT COUNT(*) FROM biz_purchase_request WHERE deleted=0 AND status='rejected'"));
        request.put("statusPie", qList(
                "SELECT status name, COUNT(*) value FROM biz_purchase_request WHERE deleted=0 GROUP BY status"));
        request.put("recentList", qList(
                "SELECT pr.request_no requestNo, IFNULL(pr.material_name,'-') materialName, " +
                "pr.quantity quantity, pr.status, IFNULL(s.supplier_name,'-') supplierName, " +
                "pr.expected_date expectedDate " +
                "FROM biz_purchase_request pr LEFT JOIN biz_supplier s ON s.id=pr.suggested_supplier_id " +
                "WHERE pr.deleted=0 ORDER BY pr.create_time DESC LIMIT 10"));
        all.put("request", request);

        Map<String, Object> supplier = new LinkedHashMap<>();
        supplier.put("totalSupplier", q1L("SELECT COUNT(*) FROM biz_supplier WHERE deleted=0 AND status='active'"));
        supplier.put("activeSupplier", q1L("SELECT COUNT(DISTINCT supplier_id) FROM biz_purchase_order " +
                "WHERE deleted=0 AND DATE_FORMAT(create_time,'%Y-%m')=?", ym.toString()));
        Long receiptAll = q1L("SELECT COUNT(*) FROM biz_purchase_receipt WHERE deleted=0");
        Long receiptOk = q1L("SELECT COUNT(*) FROM biz_purchase_receipt WHERE deleted=0 AND status IN ('received','closed')");
        int onTime = receiptAll > 0 ? (int) Math.round(receiptOk * 100.0 / receiptAll) : 100;
        supplier.put("onTimeRate", onTime);
        supplier.put("returnCount", q1L("SELECT COUNT(*) FROM biz_purchase_receipt WHERE deleted=0 AND status='returned'"));
        supplier.put("amountTop", qList(
                "SELECT IFNULL(s.supplier_name,'-') name, IFNULL(SUM(po.total_amount),0) value " +
                "FROM biz_purchase_order po LEFT JOIN biz_supplier s ON s.id=po.supplier_id " +
                "WHERE po.deleted=0 GROUP BY po.supplier_id, s.supplier_name " +
                "HAVING value > 0 ORDER BY value DESC LIMIT 8"));
        supplier.put("countTop", qList(
                "SELECT IFNULL(s.supplier_name,'-') name, COUNT(*) value " +
                "FROM biz_purchase_order po LEFT JOIN biz_supplier s ON s.id=po.supplier_id " +
                "WHERE po.deleted=0 GROUP BY po.supplier_id, s.supplier_name " +
                "ORDER BY value DESC LIMIT 8"));
        all.put("supplier", supplier);

        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("monthReceipt", q1L("SELECT COUNT(*) FROM biz_purchase_receipt WHERE deleted=0 " +
                "AND DATE_FORMAT(create_time,'%Y-%m')=?", ym.toString()));
        receipt.put("totalReceived", q1Bd("SELECT IFNULL(SUM(actual_qty),0) FROM biz_purchase_receipt WHERE deleted=0"));
        receipt.put("upcomingPo", q1L("SELECT COUNT(*) FROM biz_purchase_order WHERE deleted=0 " +
                "AND status IN ('confirmed','partial_received') AND delivery_date <= DATE_ADD(CURDATE(), INTERVAL 7 DAY)"));
        receipt.put("overduePo", q1L("SELECT COUNT(*) FROM biz_purchase_order WHERE deleted=0 " +
                "AND status IN ('confirmed','partial_received') AND delivery_date < CURDATE()"));
        receipt.put("upcomingList", qList(
                "SELECT po.po_no poNo, IFNULL(s.supplier_name,'-') supplierName, po.delivery_date deliveryDate, " +
                "po.total_amount amount, po.status " +
                "FROM biz_purchase_order po LEFT JOIN biz_supplier s ON s.id=po.supplier_id " +
                "WHERE po.deleted=0 AND po.status IN ('confirmed','partial_received') " +
                "ORDER BY po.delivery_date ASC LIMIT 12"));
        all.put("receipt", receipt);

        all.put("ticker", purchaseTicker());
        return all;
    }

    private List<Map<String, String>> purchaseTicker() {
        List<Map<String, String>> list = new ArrayList<>();
        try {
            for (Map<String, Object> r : qList("SELECT po.po_no poNo, po.total_amount amount, " +
                    "IFNULL(s.supplier_name,'-') supplierName FROM biz_purchase_order po " +
                    "LEFT JOIN biz_supplier s ON s.id=po.supplier_id WHERE po.deleted=0 " +
                    "ORDER BY po.create_time DESC LIMIT 3"))
                list.add(msg("var(--cyan)", String.format("📑 采购订单 %s 已下达,供应商:%s 金额:¥%s",
                        r.get("poNo"), r.get("supplierName"), r.get("amount"))));

            for (Map<String, Object> r : qList("SELECT receipt_no rno, actual_qty qty " +
                    "FROM biz_purchase_receipt WHERE deleted=0 AND status IN ('received','closed') " +
                    "ORDER BY create_time DESC LIMIT 3"))
                list.add(msg("var(--green)", String.format("✓ 收货单 %s 已收 %s 件", r.get("rno"), r.get("qty"))));

            for (Map<String, Object> r : qList("SELECT po_no poNo, delivery_date dd FROM biz_purchase_order " +
                    "WHERE deleted=0 AND status IN ('confirmed','partial_received') AND delivery_date < CURDATE() " +
                    "ORDER BY delivery_date ASC LIMIT 2"))
                list.add(msg("var(--red)", String.format("⚠ 采购订单 %s 已逾期(预计 %s)", r.get("poNo"), r.get("dd"))));
        } catch (Exception ignore) {}
        if (list.isEmpty()) list.add(msg("var(--green)", "采购运行正常"));
        return list;
    }

    // ============================================================
    // 5. 质检大屏
    // ============================================================
    public Map<String, Object> getQuality() {
        Map<String, Object> all = new LinkedHashMap<>();
        YearMonth ym = YearMonth.now();

        Map<String, Object> overview = new LinkedHashMap<>();
        Long total = q1L("SELECT COUNT(*) FROM biz_qc_task WHERE deleted=0");
        Long pass = q1L("SELECT COUNT(*) FROM biz_qc_task WHERE deleted=0 AND result='pass'");
        Long fail = q1L("SELECT COUNT(*) FROM biz_qc_task WHERE deleted=0 AND result='fail'");
        Long pending = q1L("SELECT COUNT(*) FROM biz_qc_task WHERE deleted=0 AND result='pending'");
        overview.put("totalTask", total);
        overview.put("passCount", pass);
        overview.put("failCount", fail);
        overview.put("pendingTask", pending);
        overview.put("passRate", total > 0 ? (int) Math.round(pass * 100.0 / total) : 100);
        overview.put("typePie", qList(
                "SELECT " +
                "  CASE LOWER(IFNULL(qc_type,'')) " +
                "    WHEN 'incoming' THEN '来料检 IQC' " +
                "    WHEN 'process' THEN '过程检 IPQC' " +
                "    WHEN 'final' THEN '成品检 FQC' " +
                "    WHEN 'outgoing' THEN '出货检 OQC' " +
                "    WHEN 'outsource' THEN '外协检' " +
                "    ELSE IFNULL(qc_type,'其他') " +
                "  END name, COUNT(*) value " +
                "FROM biz_qc_task WHERE deleted=0 GROUP BY qc_type ORDER BY value DESC"));
        overview.put("resultPie", qList(
                "SELECT result name, COUNT(*) value FROM biz_qc_task WHERE deleted=0 GROUP BY result"));
        all.put("overview", overview);

        Map<String, Object> trend = new LinkedHashMap<>();
        // 8 周质检趋势:单条 GROUP BY YEARWEEK 一次取全
        Map<String, long[]> weekMap = new HashMap<>();
        try {
            String sqlW = "SELECT YEARWEEK(create_time,3) yw, " +
                    "COUNT(*) wt, SUM(CASE WHEN result='pass' THEN 1 ELSE 0 END) wp " +
                    "FROM biz_qc_task WHERE deleted=0 " +
                    "AND create_time >= DATE_SUB(CURDATE(), INTERVAL 7 WEEK) " +
                    "GROUP BY YEARWEEK(create_time,3)";
            for (Map<String, Object> r : qList(sqlW)) {
                Object yw = r.get("yw");
                if (yw == null) continue;
                long wt = r.get("wt") == null ? 0L : ((Number) r.get("wt")).longValue();
                long wp = r.get("wp") == null ? 0L : ((Number) r.get("wp")).longValue();
                weekMap.put(yw.toString(), new long[]{wt, wp});
            }
        } catch (Exception ignore) {}
        List<Map<String, Object>> weekTrend = new ArrayList<>();
        for (int i = 7; i >= 0; i--) {
            LocalDate ws = LocalDate.now().minusWeeks(i).with(java.time.DayOfWeek.MONDAY);
            int yw = Integer.parseInt(String.format("%d%02d", ws.get(java.time.temporal.WeekFields.ISO.weekBasedYear()),
                    ws.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear())));
            long[] tp = weekMap.getOrDefault(String.valueOf(yw), new long[]{0L, 0L});
            int rate = tp[0] > 0 ? (int) Math.round(tp[1] * 100.0 / tp[0]) : 100;
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("week", "W" + (8 - i));
            r.put("batch", tp[0]);
            r.put("passRate", rate);
            weekTrend.add(r);
        }
        trend.put("weekTrend", weekTrend);
        trend.put("monthBatch", q1L("SELECT COUNT(*) FROM biz_qc_task WHERE deleted=0 AND DATE_FORMAT(create_time,'%Y-%m')=?", ym.toString()));
        trend.put("monthPass", q1L("SELECT COUNT(*) FROM biz_qc_task WHERE deleted=0 AND result='pass' AND DATE_FORMAT(create_time,'%Y-%m')=?", ym.toString()));
        trend.put("monthFail", q1L("SELECT COUNT(*) FROM biz_qc_task WHERE deleted=0 AND result='fail' AND DATE_FORMAT(create_time,'%Y-%m')=?", ym.toString()));
        Long mb = (Long) trend.get("monthBatch");
        Long mp = (Long) trend.get("monthPass");
        trend.put("monthPassRate", mb > 0 ? (int) Math.round(mp * 100.0 / mb) : 100);
        all.put("trend", trend);

        Map<String, Object> defect = new LinkedHashMap<>();
        defect.put("totalDefect", q1L("SELECT COUNT(*) FROM biz_defect_record WHERE deleted=0"));
        defect.put("totalDefectQty", q1Bd("SELECT IFNULL(SUM(quantity),0) FROM biz_defect_record WHERE deleted=0"));
        defect.put("scrapQty", q1Bd("SELECT IFNULL(SUM(scrap_qty),0) FROM biz_defect_record WHERE deleted=0"));
        defect.put("scrapCost", q1Bd("SELECT IFNULL(SUM(scrap_cost),0) FROM biz_defect_record WHERE deleted=0"));
        defect.put("defectPie", qList(
                "SELECT " +
                "  CASE LOWER(IFNULL(defect_type,'')) " +
                "    WHEN 'appearance' THEN '外观' WHEN 'dimensional' THEN '尺寸' WHEN 'dimension' THEN '尺寸' " +
                "    WHEN 'performance' THEN '性能' WHEN 'function' THEN '功能' " +
                "    WHEN 'material' THEN '材料' WHEN 'process' THEN '工艺' WHEN 'assembly' THEN '装配' " +
                "    WHEN 'package' THEN '包装' WHEN 'packaging' THEN '包装' " +
                "    WHEN 'other' THEN '其他' WHEN '' THEN '其他' ELSE defect_type " +
                "  END name, COUNT(*) value " +
                "FROM biz_defect_record WHERE deleted=0 GROUP BY defect_type ORDER BY value DESC LIMIT 8"));
        defect.put("levelPie", qList(
                "SELECT IFNULL(NULLIF(defect_level,''),'-') name, COUNT(*) value " +
                "FROM biz_defect_record WHERE deleted=0 GROUP BY defect_level"));
        all.put("defect", defect);

        Map<String, Object> task = new LinkedHashMap<>();
        task.put("undisposed", q1L("SELECT COUNT(*) FROM biz_qc_task WHERE deleted=0 AND result='fail' AND IFNULL(disposal_status,'undisposed')='undisposed'"));
        task.put("reworkOrder", q1L("SELECT COUNT(*) FROM biz_rework_order WHERE deleted=0 AND status NOT IN ('completed','cancelled')"));
        task.put("toInspect", q1L("SELECT COUNT(*) FROM biz_qc_task WHERE deleted=0 AND result='pending'"));
        task.put("templateCount", q1L("SELECT COUNT(*) FROM biz_qc_template WHERE deleted=0"));
        task.put("recentList", qList(
                "SELECT q.task_no taskNo, q.qc_type qcType, q.result, q.batch_no batchNo, " +
                "IFNULL(m.material_name,'-') materialName, q.create_time createTime " +
                "FROM biz_qc_task q LEFT JOIN biz_material m ON m.id=q.material_id " +
                "WHERE q.deleted=0 ORDER BY q.create_time DESC LIMIT 12"));
        all.put("task", task);

        all.put("ticker", qualityTicker());
        return all;
    }

    private List<Map<String, String>> qualityTicker() {
        List<Map<String, String>> list = new ArrayList<>();
        try {
            for (Map<String, Object> r : qList("SELECT q.task_no taskNo, IFNULL(m.material_name,'-') materialName " +
                    "FROM biz_qc_task q LEFT JOIN biz_material m ON m.id=q.material_id " +
                    "WHERE q.deleted=0 AND q.result='fail' ORDER BY q.update_time DESC LIMIT 3"))
                list.add(msg("var(--red)", String.format("⚠ 质检 %s 不合格 - 物料:%s", r.get("taskNo"), r.get("materialName"))));

            for (Map<String, Object> r : qList("SELECT q.task_no taskNo, IFNULL(m.material_name,'-') materialName " +
                    "FROM biz_qc_task q LEFT JOIN biz_material m ON m.id=q.material_id " +
                    "WHERE q.deleted=0 AND q.result='pass' ORDER BY q.update_time DESC LIMIT 3"))
                list.add(msg("var(--green)", String.format("✓ 质检 %s 合格 - 物料:%s", r.get("taskNo"), r.get("materialName"))));
        } catch (Exception ignore) {}
        if (list.isEmpty()) list.add(msg("var(--green)", "质检运行正常"));
        return list;
    }

    // ============================================================
    // 6. 财务大屏
    // ============================================================
    public Map<String, Object> getFinance() {
        Map<String, Object> all = new LinkedHashMap<>();
        YearMonth ym = YearMonth.now();

        Map<String, Object> overview = new LinkedHashMap<>();
        BigDecimal monthRev = q1Bd("SELECT IFNULL(SUM(total_amount),0) FROM biz_sales_order " +
                "WHERE deleted=0 AND DATE_FORMAT(create_time,'%Y-%m')=?", ym.toString());
        BigDecimal monthCost = q1Bd("SELECT IFNULL(SUM(total_amount),0) FROM biz_purchase_order " +
                "WHERE deleted=0 AND DATE_FORMAT(create_time,'%Y-%m')=?", ym.toString());
        BigDecimal arTotal = q1Bd("SELECT IFNULL(SUM(unreceived_amount),0) FROM biz_receivable WHERE deleted=0");
        BigDecimal apTotal = q1Bd("SELECT IFNULL(SUM(unpaid_amount),0) FROM biz_payable WHERE deleted=0");
        overview.put("monthRevenue", monthRev);
        overview.put("monthExpense", monthCost);
        overview.put("totalReceivable", arTotal);
        overview.put("totalPayable", apTotal);
        overview.put("monthTrend", mergeMonthly(
                aggMonthly("biz_sales_order", 12, "IFNULL(SUM(total_amount),0)", "create_time", null),
                List.of(aggMonthly("biz_purchase_order", 12, "IFNULL(SUM(total_amount),0)", "create_time", null)),
                new String[]{"revenue", "expense"}));
        all.put("overview", overview);

        Map<String, Object> ar = new LinkedHashMap<>();
        ar.put("totalAr", q1L("SELECT COUNT(*) FROM biz_receivable WHERE deleted=0"));
        ar.put("pendingAr", q1L("SELECT COUNT(*) FROM biz_receivable WHERE deleted=0 AND status='pending'"));
        ar.put("partialAr", q1L("SELECT COUNT(*) FROM biz_receivable WHERE deleted=0 AND status='partial'"));
        ar.put("overdueAr", q1L("SELECT COUNT(*) FROM biz_receivable WHERE deleted=0 AND status<>'received' AND due_date < CURDATE()"));
        ar.put("statusPie", qList(
                "SELECT status name, COUNT(*) value FROM biz_receivable WHERE deleted=0 GROUP BY status"));
        ar.put("topList", qList(
                "SELECT r.receivable_no rno, IFNULL(c.customer_name,'-') customerName, " +
                "r.amount amount, r.unreceived_amount unreceived, r.due_date dueDate, r.status " +
                "FROM biz_receivable r LEFT JOIN biz_customer c ON c.id=r.customer_id " +
                "WHERE r.deleted=0 AND r.status<>'received' " +
                "ORDER BY r.unreceived_amount DESC LIMIT 12"));
        all.put("ar", ar);

        Map<String, Object> ap = new LinkedHashMap<>();
        ap.put("totalAp", q1L("SELECT COUNT(*) FROM biz_payable WHERE deleted=0"));
        ap.put("pendingAp", q1L("SELECT COUNT(*) FROM biz_payable WHERE deleted=0 AND status='pending'"));
        ap.put("partialAp", q1L("SELECT COUNT(*) FROM biz_payable WHERE deleted=0 AND status='partial'"));
        ap.put("overdueAp", q1L("SELECT COUNT(*) FROM biz_payable WHERE deleted=0 AND status<>'paid' AND due_date < CURDATE()"));
        ap.put("statusPie", qList(
                "SELECT status name, COUNT(*) value FROM biz_payable WHERE deleted=0 GROUP BY status"));
        ap.put("topList", qList(
                "SELECT p.payable_no pno, IFNULL(s.supplier_name,'-') supplierName, " +
                "p.amount amount, p.unpaid_amount unpaid, p.due_date dueDate, p.status " +
                "FROM biz_payable p LEFT JOIN biz_supplier s ON s.id=p.supplier_id " +
                "WHERE p.deleted=0 AND p.status<>'paid' " +
                "ORDER BY p.unpaid_amount DESC LIMIT 12"));
        all.put("ap", ap);

        Map<String, Object> cost = new LinkedHashMap<>();
        BigDecimal materialC = q1Bd("SELECT IFNULL(SUM(material_cost),0) FROM biz_wo_cost WHERE deleted=0");
        BigDecimal laborC = q1Bd("SELECT IFNULL(SUM(labor_cost),0) FROM biz_wo_cost WHERE deleted=0");
        BigDecimal outsourceC = q1Bd("SELECT IFNULL(SUM(outsource_cost),0) FROM biz_wo_cost WHERE deleted=0");
        BigDecimal reworkC = q1Bd("SELECT IFNULL(SUM(rework_cost),0) FROM biz_wo_cost WHERE deleted=0");
        BigDecimal scrapC = q1Bd("SELECT IFNULL(SUM(scrap_loss),0) FROM biz_wo_cost WHERE deleted=0");
        BigDecimal overheadC = q1Bd("SELECT IFNULL(SUM(overhead),0) FROM biz_wo_cost WHERE deleted=0");
        BigDecimal totalC = materialC.add(laborC).add(outsourceC).add(reworkC).add(scrapC).add(overheadC);
        cost.put("totalCost", totalC);
        cost.put("materialCost", materialC);
        cost.put("laborCost", laborC);
        cost.put("outsourceCost", outsourceC);
        cost.put("reworkCost", reworkC);
        cost.put("scrapCost", scrapC);
        cost.put("overheadCost", overheadC);
        List<Map<String, Object>> costPie = new ArrayList<>();
        costPie.add(pie("物料", materialC));
        costPie.add(pie("人工", laborC));
        costPie.add(pie("外协", outsourceC));
        costPie.add(pie("返工", reworkC));
        costPie.add(pie("报废", scrapC));
        costPie.add(pie("制造费用", overheadC));
        cost.put("costPie", costPie);
        cost.put("highCostWo", qList(
                "SELECT wo.wo_no woNo, IFNULL(m.material_name,'-') productName, c.total_cost totalCost " +
                "FROM biz_wo_cost c LEFT JOIN biz_work_order wo ON wo.id=c.work_order_id " +
                "LEFT JOIN biz_material m ON m.id=wo.product_id " +
                "WHERE c.deleted=0 ORDER BY c.total_cost DESC LIMIT 10"));
        all.put("cost", cost);

        all.put("ticker", financeTicker());
        return all;
    }

    private Map<String, Object> pie(String name, BigDecimal v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("value", v);
        return m;
    }

    private List<Map<String, String>> financeTicker() {
        List<Map<String, String>> list = new ArrayList<>();
        try {
            for (Map<String, Object> r : qList("SELECT receivable_no rno, unreceived_amount amt, due_date dd " +
                    "FROM biz_receivable WHERE deleted=0 AND status<>'received' AND due_date < CURDATE() " +
                    "ORDER BY due_date ASC LIMIT 3"))
                list.add(msg("var(--red)", String.format("💰 应收 %s 已逾期(到期 %s),未收款 ¥%s",
                        r.get("rno"), r.get("dd"), r.get("amt"))));

            for (Map<String, Object> r : qList("SELECT payable_no pno, unpaid_amount amt, due_date dd " +
                    "FROM biz_payable WHERE deleted=0 AND status<>'paid' AND due_date < CURDATE() " +
                    "ORDER BY due_date ASC LIMIT 3"))
                list.add(msg("var(--orange)", String.format("📤 应付 %s 已逾期(到期 %s),待付款 ¥%s",
                        r.get("pno"), r.get("dd"), r.get("amt"))));

            for (Map<String, Object> r : qList("SELECT receivable_no rno, received_amount amt " +
                    "FROM biz_receivable WHERE deleted=0 AND status='received' " +
                    "ORDER BY update_time DESC LIMIT 3"))
                list.add(msg("var(--green)", String.format("✓ 应收 %s 已收清 ¥%s", r.get("rno"), r.get("amt"))));
        } catch (Exception ignore) {}
        if (list.isEmpty()) list.add(msg("var(--green)", "财务运行正常"));
        return list;
    }

    // ============================================================
    // 7. 外协大屏
    // ============================================================
    public Map<String, Object> getOutsource() {
        Map<String, Object> all = new LinkedHashMap<>();
        YearMonth ym = YearMonth.now();

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("totalRequest", q1L("SELECT COUNT(*) FROM biz_outsource_request WHERE deleted=0"));
        overview.put("pendingRequest", q1L("SELECT COUNT(*) FROM biz_outsource_request WHERE deleted=0 AND status='pending'"));
        overview.put("totalOrder", q1L("SELECT COUNT(*) FROM biz_outsource_order WHERE deleted=0"));
        overview.put("inProgressOrder", q1L("SELECT COUNT(*) FROM biz_outsource_order WHERE deleted=0 " +
                "AND status IN ('confirmed','material_sent','processing','partial_received')"));
        overview.put("monthAmount", q1Bd("SELECT IFNULL(SUM(total_amount),0) FROM biz_outsource_order " +
                "WHERE deleted=0 AND DATE_FORMAT(create_time,'%Y-%m')=?", ym.toString()));
        overview.put("requestStatusPie", qList(
                "SELECT status name, COUNT(*) value FROM biz_outsource_request WHERE deleted=0 GROUP BY status"));
        overview.put("orderStatusPie", qList(
                "SELECT status name, COUNT(*) value FROM biz_outsource_order WHERE deleted=0 GROUP BY status"));
        all.put("overview", overview);

        Map<String, Object> trend = new LinkedHashMap<>();
        trend.put("monthTrend", mergeMonthly(
                aggMonthly("biz_outsource_order", 12, "IFNULL(SUM(total_amount),0)", "create_time", null),
                List.of(aggMonthly("biz_outsource_order", 12, "COUNT(*)", "create_time", null)),
                new String[]{"amount", "count"}));
        all.put("trend", trend);

        Map<String, Object> supplier = new LinkedHashMap<>();
        supplier.put("supplierCount", q1L("SELECT COUNT(DISTINCT supplier_id) FROM biz_outsource_order WHERE deleted=0"));
        supplier.put("amountTop", qList(
                "SELECT IFNULL(s.supplier_name,'-') name, IFNULL(SUM(oo.total_amount),0) value " +
                "FROM biz_outsource_order oo LEFT JOIN biz_supplier s ON s.id=oo.supplier_id " +
                "WHERE oo.deleted=0 GROUP BY oo.supplier_id, s.supplier_name " +
                "HAVING value > 0 ORDER BY value DESC LIMIT 8"));
        supplier.put("countTop", qList(
                "SELECT IFNULL(s.supplier_name,'-') name, COUNT(*) value " +
                "FROM biz_outsource_order oo LEFT JOIN biz_supplier s ON s.id=oo.supplier_id " +
                "WHERE oo.deleted=0 GROUP BY oo.supplier_id, s.supplier_name " +
                "ORDER BY value DESC LIMIT 8"));
        all.put("supplier", supplier);

        Map<String, Object> active = new LinkedHashMap<>();
        active.put("toDeliver", q1L("SELECT COUNT(*) FROM biz_outsource_order WHERE deleted=0 " +
                "AND status IN ('confirmed','material_sent')"));
        active.put("processing", q1L("SELECT COUNT(*) FROM biz_outsource_order WHERE deleted=0 AND status='processing'"));
        active.put("toReceive", q1L("SELECT COUNT(*) FROM biz_outsource_order WHERE deleted=0 " +
                "AND status IN ('processing','partial_received')"));
        active.put("overdue", q1L("SELECT COUNT(*) FROM biz_outsource_order WHERE deleted=0 " +
                "AND status NOT IN ('received','settled','cancelled') AND delivery_date < CURDATE()"));
        active.put("activeList", qList(
                "SELECT oo.order_no orderNo, IFNULL(s.supplier_name,'-') supplierName, " +
                "IFNULL(oo.step_name,'-') stepName, oo.total_qty totalQty, oo.delivery_date deliveryDate, oo.status " +
                "FROM biz_outsource_order oo LEFT JOIN biz_supplier s ON s.id=oo.supplier_id " +
                "WHERE oo.deleted=0 AND oo.status NOT IN ('received','settled','cancelled') " +
                "ORDER BY oo.delivery_date ASC LIMIT 12"));
        all.put("active", active);

        all.put("ticker", outsourceTicker());
        return all;
    }

    private List<Map<String, String>> outsourceTicker() {
        List<Map<String, String>> list = new ArrayList<>();
        try {
            for (Map<String, Object> r : qList("SELECT request_no rno, IFNULL(step_name,'-') stepName, quantity " +
                    "FROM biz_outsource_request WHERE deleted=0 AND status='pending' " +
                    "ORDER BY create_time DESC LIMIT 3"))
                list.add(msg("var(--orange)", String.format("📋 外协申请 %s 待审批 - 工序:%s 数量:%s",
                        r.get("rno"), r.get("stepName"), r.get("quantity"))));

            for (Map<String, Object> r : qList("SELECT oo.order_no orderNo, IFNULL(s.supplier_name,'-') supplierName, " +
                    "oo.total_amount amount FROM biz_outsource_order oo " +
                    "LEFT JOIN biz_supplier s ON s.id=oo.supplier_id WHERE oo.deleted=0 " +
                    "ORDER BY oo.create_time DESC LIMIT 3"))
                list.add(msg("var(--cyan)", String.format("🔄 外协订单 %s - 供应商:%s 金额:¥%s",
                        r.get("orderNo"), r.get("supplierName"), r.get("amount"))));

            for (Map<String, Object> r : qList("SELECT order_no orderNo, delivery_date dd FROM biz_outsource_order " +
                    "WHERE deleted=0 AND status NOT IN ('received','settled','cancelled') AND delivery_date < CURDATE() " +
                    "ORDER BY delivery_date ASC LIMIT 2"))
                list.add(msg("var(--red)", String.format("⚠ 外协订单 %s 已逾期(预计 %s)",
                        r.get("orderNo"), r.get("dd"))));
        } catch (Exception ignore) {}
        if (list.isEmpty()) list.add(msg("var(--green)", "外协运行正常"));
        return list;
    }

    // ============================================================
    // 8. MRP 大屏
    // ============================================================
    public Map<String, Object> getMrp() {
        Map<String, Object> all = new LinkedHashMap<>();

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("totalRun", q1L("SELECT COUNT(*) FROM biz_mrp_run WHERE deleted=0"));
        overview.put("monthRun", q1L("SELECT COUNT(*) FROM biz_mrp_run WHERE deleted=0 AND DATE_FORMAT(create_time,'%Y-%m')=?", YearMonth.now().toString()));
        overview.put("successRun", q1L("SELECT COUNT(*) FROM biz_mrp_run WHERE deleted=0 AND status='success'"));
        overview.put("failedRun", q1L("SELECT COUNT(*) FROM biz_mrp_run WHERE deleted=0 AND status='failed'"));
        overview.put("statusPie", qList(
                "SELECT status name, COUNT(*) value FROM biz_mrp_run WHERE deleted=0 GROUP BY status"));
        overview.put("typePie", qList(
                "SELECT IFNULL(NULLIF(run_type,''),'manual') name, COUNT(*) value FROM biz_mrp_run WHERE deleted=0 GROUP BY run_type"));
        overview.put("recentRun", qList(
                "SELECT run_no runNo, IFNULL(NULLIF(run_type,''),'-') runType, status, " +
                "order_count orderCount, result_count resultCount, run_time runTime " +
                "FROM biz_mrp_run WHERE deleted=0 ORDER BY run_time DESC LIMIT 10"));
        all.put("overview", overview);

        Map<String, Object> demand = new LinkedHashMap<>();
        demand.put("totalDemand", q1L("SELECT COUNT(*) FROM biz_mrp_demand WHERE deleted=0"));
        demand.put("totalPurchaseSug", q1L("SELECT COUNT(*) FROM biz_mrp_purchase_suggestion WHERE deleted=0"));
        demand.put("totalProductionSug", q1L("SELECT COUNT(*) FROM biz_mrp_production_suggestion WHERE deleted=0"));
        demand.put("pendingPurchaseSug", q1L("SELECT COUNT(*) FROM biz_mrp_purchase_suggestion WHERE deleted=0 AND status='pending'"));
        demand.put("purchaseStatusPie", qList(
                "SELECT IFNULL(NULLIF(status,''),'pending') name, COUNT(*) value FROM biz_mrp_purchase_suggestion WHERE deleted=0 GROUP BY status"));
        demand.put("productionStatusPie", qList(
                "SELECT IFNULL(NULLIF(status,''),'pending') name, COUNT(*) value FROM biz_mrp_production_suggestion WHERE deleted=0 GROUP BY status"));
        all.put("demand", demand);

        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("totalPlan", q1L("SELECT COUNT(*) FROM biz_production_plan WHERE deleted=0"));
        plan.put("draftPlan", q1L("SELECT COUNT(*) FROM biz_production_plan WHERE deleted=0 AND status='draft'"));
        plan.put("releasedPlan", q1L("SELECT COUNT(*) FROM biz_production_plan WHERE deleted=0 AND status='released'"));
        plan.put("completedPlan", q1L("SELECT COUNT(*) FROM biz_production_plan WHERE deleted=0 AND status='completed'"));
        plan.put("statusPie", qList(
                "SELECT status name, COUNT(*) value FROM biz_production_plan WHERE deleted=0 GROUP BY status"));
        plan.put("recentPlan", qList(
                "SELECT plan_no planNo, status, " +
                "create_time createTime " +
                "FROM biz_production_plan WHERE deleted=0 ORDER BY create_time DESC LIMIT 10"));
        all.put("plan", plan);

        Map<String, Object> suggestion = new LinkedHashMap<>();
        suggestion.put("topPurchaseList", qList(
                "SELECT IFNULL(m.material_name,'-') name, IFNULL(SUM(s.suggested_qty),0) value " +
                "FROM biz_mrp_purchase_suggestion s LEFT JOIN biz_material m ON m.id=s.material_id " +
                "WHERE s.deleted=0 GROUP BY s.material_id, m.material_name " +
                "ORDER BY value DESC LIMIT 8"));
        suggestion.put("topProductionList", qList(
                "SELECT IFNULL(m.material_name,'-') name, IFNULL(SUM(s.suggested_qty),0) value " +
                "FROM biz_mrp_production_suggestion s LEFT JOIN biz_material m ON m.id=s.product_id " +
                "WHERE s.deleted=0 GROUP BY s.product_id, m.material_name " +
                "ORDER BY value DESC LIMIT 8"));
        all.put("suggestion", suggestion);

        all.put("ticker", mrpTicker());
        return all;
    }

    private List<Map<String, String>> mrpTicker() {
        List<Map<String, String>> list = new ArrayList<>();
        try {
            String runStatusCase = "CASE LOWER(IFNULL(status,'')) " +
                    " WHEN 'pending' THEN '待运算' WHEN 'running' THEN '运算中' " +
                    " WHEN 'success' THEN '成功' WHEN 'failed' THEN '失败' " +
                    " WHEN 'cancelled' THEN '已取消' ELSE IFNULL(status,'-') END ";
            for (Map<String, Object> r : qList("SELECT run_no runNo, " + runStatusCase + " statusCn, result_count rc " +
                    "FROM biz_mrp_run WHERE deleted=0 ORDER BY run_time DESC LIMIT 5"))
                list.add(msg("var(--cyan)", String.format("⚙ MRP 运算 %s [%s] 产生 %s 条建议",
                        r.get("runNo"), r.get("statusCn"), r.get("rc"))));

            String planStatusCase = "CASE LOWER(IFNULL(status,'')) " +
                    " WHEN 'draft' THEN '草稿' WHEN 'released' THEN '已发布' " +
                    " WHEN 'in_progress' THEN '执行中' WHEN 'completed' THEN '已完成' " +
                    " WHEN 'cancelled' THEN '已取消' ELSE IFNULL(status,'-') END ";
            for (Map<String, Object> r : qList("SELECT plan_no planNo, " + planStatusCase + " statusCn FROM biz_production_plan " +
                    "WHERE deleted=0 ORDER BY create_time DESC LIMIT 3"))
                list.add(msg("var(--green)", String.format("📋 生产计划 %s [%s]", r.get("planNo"), r.get("statusCn"))));
        } catch (Exception ignore) {}
        if (list.isEmpty()) list.add(msg("var(--green)", "MRP 运行正常"));
        return list;
    }

    // ============================================================
    // 9. 报工大屏
    // ============================================================
    public Map<String, Object> getWorkReport() {
        Map<String, Object> all = new LinkedHashMap<>();
        YearMonth ym = YearMonth.now();

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("todayReport", q1L("SELECT COUNT(*) FROM biz_work_report WHERE deleted=0 AND DATE(create_time)=CURDATE()"));
        overview.put("monthReport", q1L("SELECT COUNT(*) FROM biz_work_report WHERE deleted=0 " +
                "AND DATE_FORMAT(create_time,'%Y-%m')=?", ym.toString()));
        overview.put("todayQualified", q1Bd("SELECT IFNULL(SUM(qualified_qty),0) FROM biz_work_report WHERE deleted=0 AND DATE(create_time)=CURDATE()"));
        overview.put("monthQualified", q1Bd("SELECT IFNULL(SUM(qualified_qty),0) FROM biz_work_report WHERE deleted=0 " +
                "AND DATE_FORMAT(create_time,'%Y-%m')=?", ym.toString()));

        overview.put("trend", mergeDaily(
                aggDailyBd("biz_work_report", 14, "IFNULL(SUM(qualified_qty),0)", "create_time", null),
                List.of(
                    aggDailyBd("biz_work_report", 14, "IFNULL(SUM(defect_qty),0)", "create_time", null),
                    aggDaily("biz_work_report", 14, "COUNT(*)", "create_time", null)
                ),
                new String[]{"qualified", "defect", "count"}));
        overview.put("typePie", qList(
                "SELECT report_type name, COUNT(*) value FROM biz_work_report WHERE deleted=0 GROUP BY report_type"));
        all.put("overview", overview);

        Map<String, Object> personal = new LinkedHashMap<>();
        personal.put("activeReporter", q1L("SELECT COUNT(DISTINCT reporter_id) FROM biz_work_report " +
                "WHERE deleted=0 AND DATE_FORMAT(create_time,'%Y-%m')=?", ym.toString()));
        personal.put("totalWageMonth", q1Bd("SELECT IFNULL(SUM(amount),0) FROM biz_wage_record WHERE deleted=0 " +
                "AND DATE_FORMAT(report_date,'%Y-%m')=?", ym.toString()));
        personal.put("avgPersonOutput", q1Bd("SELECT IFNULL(AVG(qualified_qty),0) FROM biz_work_report WHERE deleted=0"));
        personal.put("avgWorkHours", q1Bd("SELECT IFNULL(AVG(work_hours),0) FROM biz_work_report WHERE deleted=0"));
        personal.put("personOutputTop", qList(
                "SELECT IFNULL(u.real_name, u.username) name, IFNULL(SUM(r.qualified_qty),0) value " +
                "FROM biz_work_report r LEFT JOIN sys_user u ON u.id=r.reporter_id " +
                "WHERE r.deleted=0 AND DATE_FORMAT(r.create_time,'%Y-%m')=? " +
                "GROUP BY r.reporter_id, u.real_name, u.username " +
                "HAVING value > 0 ORDER BY value DESC LIMIT 8", ym.toString()));
        personal.put("personWageTop", qList(
                "SELECT IFNULL(u.real_name, u.username) name, IFNULL(SUM(w.amount),0) value " +
                "FROM biz_wage_record w LEFT JOIN sys_user u ON u.id=w.user_id " +
                "WHERE w.deleted=0 AND DATE_FORMAT(w.report_date,'%Y-%m')=? " +
                "GROUP BY w.user_id, u.real_name, u.username " +
                "HAVING value > 0 ORDER BY value DESC LIMIT 8", ym.toString()));
        all.put("personal", personal);

        Map<String, Object> team = new LinkedHashMap<>();
        team.put("teamCount", q1L("SELECT COUNT(*) FROM biz_team WHERE deleted=0"));
        team.put("teamReportCount", q1L("SELECT COUNT(*) FROM biz_team_work_report WHERE deleted=0 " +
                "AND DATE_FORMAT(create_time,'%Y-%m')=?", ym.toString()));
        team.put("totalMember", q1L("SELECT COUNT(DISTINCT user_id) FROM biz_team_member WHERE deleted=0"));
        team.put("teamWageMonth", q1Bd("SELECT IFNULL(SUM(piece_wage),0) FROM biz_team_work_report WHERE deleted=0 " +
                "AND DATE_FORMAT(create_time,'%Y-%m')=?", ym.toString()));
        team.put("teamOutputTop", qList(
                "SELECT IFNULL(t.team_name,'-') name, IFNULL(SUM(tr.qualified_qty),0) value " +
                "FROM biz_team_work_report tr LEFT JOIN biz_team t ON t.id IN (" +
                "  SELECT team_id FROM biz_team_member WHERE user_id=tr.reporter_id LIMIT 1) " +
                "WHERE tr.deleted=0 GROUP BY t.id, t.team_name HAVING value > 0 ORDER BY value DESC LIMIT 8"));
        all.put("team", team);

        Map<String, Object> exception = new LinkedHashMap<>();
        exception.put("openException", q1L("SELECT COUNT(*) FROM biz_exception_report WHERE deleted=0 AND status='open'"));
        exception.put("escalatedCount", q1L("SELECT COUNT(*) FROM biz_exception_report WHERE deleted=0 AND is_escalated=1"));
        exception.put("urgencyPie", qList(
                "SELECT IFNULL(NULLIF(urgency,''),'normal') name, COUNT(*) value " +
                "FROM biz_exception_report WHERE deleted=0 GROUP BY urgency"));
        exception.put("typePie", qList(
                "SELECT IFNULL(NULLIF(exception_type,''),'未分类') name, COUNT(*) value " +
                "FROM biz_exception_report WHERE deleted=0 GROUP BY exception_type"));
        exception.put("recentList", qList(
                "SELECT report_no reportNo, IFNULL(exception_type,'-') exceptionType, " +
                "IFNULL(urgency,'normal') urgency, status, IFNULL(description,'-') description, create_time createTime " +
                "FROM biz_exception_report WHERE deleted=0 ORDER BY create_time DESC LIMIT 10"));
        all.put("exception", exception);

        all.put("ticker", workReportTicker());
        return all;
    }

    private List<Map<String, String>> workReportTicker() {
        List<Map<String, String>> list = new ArrayList<>();
        try {
            for (Map<String, Object> r : qList("SELECT r.report_no reportNo, r.qualified_qty qty, " +
                    "IFNULL(u.real_name, u.username) reporterName " +
                    "FROM biz_work_report r LEFT JOIN sys_user u ON u.id=r.reporter_id " +
                    "WHERE r.deleted=0 ORDER BY r.create_time DESC LIMIT 5"))
                list.add(msg("var(--cyan)", String.format("📝 报工 %s - %s 提交合格 %s 件",
                        r.get("reportNo"), r.get("reporterName"), r.get("qty"))));

            String exTypeCase = "CASE LOWER(IFNULL(exception_type,'')) " +
                    " WHEN 'quality' THEN '质量异常' WHEN 'equipment' THEN '设备故障' " +
                    " WHEN 'material' THEN '物料缺失' WHEN 'safety' THEN '安全事故' " +
                    " WHEN 'environment' THEN '环境异常' WHEN 'plan' THEN '计划变更' " +
                    " WHEN 'process' THEN '工艺异常' WHEN 'manpower' THEN '人力不足' " +
                    " WHEN 'delivery' THEN '交期异常' WHEN 'urgent' THEN '紧急异常' " +
                    " WHEN 'other' THEN '其他异常' WHEN '' THEN '其他' ELSE exception_type END ";
            String urgencyCase = "CASE LOWER(IFNULL(urgency,'')) " +
                    " WHEN 'urgent' THEN '紧急' WHEN 'high' THEN '高' " +
                    " WHEN 'normal' THEN '普通' WHEN 'low' THEN '低' " +
                    " WHEN '' THEN '普通' ELSE urgency END ";
            for (Map<String, Object> r : qList("SELECT report_no reportNo, " + exTypeCase + " typeCn, " + urgencyCase + " urgencyCn " +
                    "FROM biz_exception_report WHERE deleted=0 AND status='open' " +
                    "ORDER BY create_time DESC LIMIT 3"))
                list.add(msg("var(--red)", String.format("⚠ 异常上报 %s [%s] 紧急度:%s",
                        r.get("reportNo"), r.get("typeCn"), r.get("urgencyCn"))));
        } catch (Exception ignore) {}
        if (list.isEmpty()) list.add(msg("var(--green)", "报工运行正常"));
        return list;
    }

    // ============================================================
    // 工具方法
    // ============================================================
    /**
     * 单条 GROUP BY 拉日聚合(避免 N 条 SQL),Java 端按日期补齐缺失天
     * @param table 含 deleted/create_time 字段的表
     * @param days 最近 N 天(包含今天)
     * @param expr 需聚合的表达式,如 "COUNT(*)" 或 "IFNULL(SUM(qualified_qty),0)"
     * @param dateCol 日期列名,如 "create_time" / "update_time"
     * @param extraWhere 附加条件,无则传空串(注意以 AND 开头)
     */
    private List<Map<String, Object>> aggDaily(String table, int days, String expr, String dateCol, String extraWhere) {
        Map<String, Number> map = new HashMap<>();
        try {
            String sql = "SELECT DATE(" + dateCol + ") d, " + expr + " v FROM " + table +
                    " WHERE deleted=0 AND " + dateCol + " >= DATE_SUB(CURDATE(), INTERVAL " + (days - 1) + " DAY) " +
                    (extraWhere == null ? "" : extraWhere) +
                    " GROUP BY DATE(" + dateCol + ")";
            for (Map<String, Object> r : qList(sql)) {
                Object d = r.get("d");
                if (d == null) continue;
                map.put(d.toString(), (Number) r.get("v"));
            }
        } catch (Exception ignore) {}
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate d = LocalDate.now().minusDays(i);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("date", d.toString().substring(5));
            r.put("d", d.toString());
            Number n = map.get(d.toString());
            r.put("value", n == null ? 0L : n.longValue());
            list.add(r);
        }
        return list;
    }

    /** 同上 但返回 BigDecimal 数值(SUM 场景) */
    private List<Map<String, Object>> aggDailyBd(String table, int days, String expr, String dateCol, String extraWhere) {
        Map<String, BigDecimal> map = new HashMap<>();
        try {
            String sql = "SELECT DATE(" + dateCol + ") d, " + expr + " v FROM " + table +
                    " WHERE deleted=0 AND " + dateCol + " >= DATE_SUB(CURDATE(), INTERVAL " + (days - 1) + " DAY) " +
                    (extraWhere == null ? "" : extraWhere) +
                    " GROUP BY DATE(" + dateCol + ")";
            for (Map<String, Object> r : qList(sql)) {
                Object d = r.get("d");
                if (d == null) continue;
                Object v = r.get("v");
                map.put(d.toString(), v == null ? BigDecimal.ZERO : new BigDecimal(v.toString()));
            }
        } catch (Exception ignore) {}
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate d = LocalDate.now().minusDays(i);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("date", d.toString().substring(5));
            r.put("d", d.toString());
            r.put("value", map.getOrDefault(d.toString(), BigDecimal.ZERO));
            list.add(r);
        }
        return list;
    }

    /** 单条 GROUP BY 拉月聚合(返回 BigDecimal,适配金额 / 计数) */
    private List<Map<String, Object>> aggMonthly(String table, int months, String expr, String dateCol, String extraWhere) {
        Map<String, BigDecimal> map = new HashMap<>();
        try {
            String sql = "SELECT DATE_FORMAT(" + dateCol + ",'%Y-%m') m, " + expr + " v FROM " + table +
                    " WHERE deleted=0 AND " + dateCol + " >= DATE_SUB(CURDATE(), INTERVAL " + (months - 1) + " MONTH) " +
                    (extraWhere == null ? "" : extraWhere) +
                    " GROUP BY DATE_FORMAT(" + dateCol + ",'%Y-%m')";
            for (Map<String, Object> r : qList(sql)) {
                Object m = r.get("m");
                if (m == null) continue;
                Object v = r.get("v");
                map.put(m.toString(), v == null ? BigDecimal.ZERO : new BigDecimal(v.toString()));
            }
        } catch (Exception ignore) {}
        List<Map<String, Object>> list = new ArrayList<>();
        YearMonth ym = YearMonth.now();
        for (int i = months - 1; i >= 0; i--) {
            YearMonth ymi = ym.minusMonths(i);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("month", ymi.getMonthValue() + "月");
            r.put("ym", ymi.toString());
            r.put("value", map.getOrDefault(ymi.toString(), BigDecimal.ZERO));
            list.add(r);
        }
        return list;
    }

    /** 把多个 aggDaily/aggDailyBd 结果按 date 合并到一组 */
    private List<Map<String, Object>> mergeDaily(List<Map<String, Object>> base,
                                                  List<List<Map<String, Object>>> others,
                                                  String[] keys) {
        for (int i = 0; i < base.size(); i++) {
            Map<String, Object> b = base.get(i);
            b.put(keys[0], b.remove("value"));
            b.remove("d");
            for (int j = 0; j < others.size(); j++) {
                List<Map<String, Object>> o = others.get(j);
                if (i < o.size()) b.put(keys[j + 1], o.get(i).get("value"));
            }
        }
        return base;
    }

    /** 把多个 aggMonthly 结果按 month 合并 */
    private List<Map<String, Object>> mergeMonthly(List<Map<String, Object>> base,
                                                    List<List<Map<String, Object>>> others,
                                                    String[] keys) {
        for (int i = 0; i < base.size(); i++) {
            Map<String, Object> b = base.get(i);
            b.put(keys[0], b.remove("value"));
            b.remove("ym");
            for (int j = 0; j < others.size(); j++) {
                List<Map<String, Object>> o = others.get(j);
                if (i < o.size()) b.put(keys[j + 1], o.get(i).get("value"));
            }
        }
        return base;
    }

    private Map<String, String> msg(String color, String text) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("c", color);
        m.put("t", text);
        return m;
    }

    private Long q1L(String sql, Object... args) {
        try {
            Long v = jdbc.queryForObject(sql, Long.class, args);
            return v == null ? 0L : v;
        } catch (Exception e) {
            return 0L;
        }
    }

    private BigDecimal q1Bd(String sql, Object... args) {
        try {
            BigDecimal v = jdbc.queryForObject(sql, BigDecimal.class, args);
            return v == null ? BigDecimal.ZERO : v;
        } catch (Exception e) {
            return BigDecimal.ZERO;
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
