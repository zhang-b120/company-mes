package com.aimes.modules.dashboard.service;

import com.aimes.modules.finance.entity.BizReceivable;
import com.aimes.modules.finance.entity.BizWoCost;
import com.aimes.modules.finance.mapper.BizReceivableMapper;
import com.aimes.modules.finance.mapper.BizWoCostMapper;
import com.aimes.modules.inventory.entity.BizInventory;
import com.aimes.modules.inventory.entity.BizInvTransaction;
import com.aimes.modules.inventory.mapper.BizInventoryMapper;
import com.aimes.modules.inventory.mapper.BizInvTransactionMapper;
import com.aimes.modules.master.entity.BizMaterial;
import com.aimes.modules.master.mapper.BizMaterialMapper;
import com.aimes.modules.order.entity.BizSalesOrder;
import com.aimes.modules.order.mapper.BizSalesOrderMapper;
import com.aimes.modules.production.entity.BizWorkOrder;
import com.aimes.modules.production.mapper.BizWorkOrderMapper;
import com.aimes.modules.quality.entity.BizDefectRecord;
import com.aimes.modules.quality.entity.BizQcTask;
import com.aimes.modules.quality.mapper.BizDefectRecordMapper;
import com.aimes.modules.quality.mapper.BizQcTaskMapper;
import com.aimes.modules.workreport.entity.BizExceptionReport;
import com.aimes.modules.workreport.mapper.BizExceptionReportMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * 看板/仪表盘服务
 * 跨模块聚合查询，为首页提供汇总统计数据
 * 支持7种角色差异化看板
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final BizSalesOrderMapper salesOrderMapper;
    private final BizWorkOrderMapper workOrderMapper;
    private final BizInventoryMapper inventoryMapper;
    private final BizQcTaskMapper qcTaskMapper;
    private final BizReceivableMapper receivableMapper;
    private final BizWoCostMapper woCostMapper;
    private final BizDefectRecordMapper defectRecordMapper;
    private final BizInvTransactionMapper invTransactionMapper;
    private final BizExceptionReportMapper exceptionReportMapper;
    private final BizMaterialMapper materialMapper;

    /**
     * 通用首页汇总数据
     */
    public Map<String, Object> getSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        // 总订单数(不含草稿/取消/作废)
        long totalOrders = salesOrderMapper.selectCount(
                new LambdaQueryWrapper<BizSalesOrder>()
                        .notIn(BizSalesOrder::getStatus, "draft", "cancelled", "voided"));
        summary.put("totalOrders", totalOrders);

        long todayOrders = salesOrderMapper.selectCount(
                new LambdaQueryWrapper<BizSalesOrder>()
                        .ge(BizSalesOrder::getCreateTime, todayStart)
                        .le(BizSalesOrder::getCreateTime, todayEnd));
        summary.put("todayOrders", todayOrders);

        // 生产在制(进行中的工单)
        long productionWip = workOrderMapper.selectCount(
                new LambdaQueryWrapper<BizWorkOrder>()
                        .in(BizWorkOrder::getStatus, "in_progress", "pending"));
        summary.put("productionWip", productionWip);

        long todayProduction = workOrderMapper.selectCount(
                new LambdaQueryWrapper<BizWorkOrder>()
                        .eq(BizWorkOrder::getStatus, "completed")
                        .ge(BizWorkOrder::getUpdateTime, todayStart)
                        .le(BizWorkOrder::getUpdateTime, todayEnd));
        summary.put("todayProduction", todayProduction);

        // 本月收入
        List<BizSalesOrder> monthOrders = salesOrderMapper.selectList(
                new LambdaQueryWrapper<BizSalesOrder>()
                        .ge(BizSalesOrder::getCreateTime, monthStart)
                        .notIn(BizSalesOrder::getStatus, "draft", "cancelled", "voided"));
        BigDecimal revenueThisMonth = monthOrders.stream()
                .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        summary.put("revenueThisMonth", revenueThisMonth);

        long inventoryAlerts = inventoryMapper.selectCount(
                new LambdaQueryWrapper<BizInventory>()
                        .gt(BizInventory::getSafetyStock, BigDecimal.ZERO)
                        .apply("available_qty < safety_stock"));
        summary.put("inventoryAlerts", inventoryAlerts);

        long pendingTasks = qcTaskMapper.selectCount(
                new LambdaQueryWrapper<BizQcTask>()
                        .eq(BizQcTask::getResult, "pending"));
        summary.put("pendingTasks", pendingTasks);

        // 产量TOP5产品:按已完工工单的 completedQty 汇总,取前5
        List<BizWorkOrder> completedWos = workOrderMapper.selectList(
                new LambdaQueryWrapper<BizWorkOrder>()
                        .eq(BizWorkOrder::getStatus, "completed")
                        .gt(BizWorkOrder::getCompletedQty, BigDecimal.ZERO));
        Map<Long, BigDecimal> productQtyMap = new LinkedHashMap<>();
        for (BizWorkOrder w : completedWos) {
            if (w.getProductId() != null && w.getCompletedQty() != null) {
                productQtyMap.merge(w.getProductId(), w.getCompletedQty(), BigDecimal::add);
            }
        }
        List<Long> topProdIds = productQtyMap.entrySet().stream()
                .sorted(Map.Entry.<Long, BigDecimal>comparingByValue().reversed())
                .limit(5).map(Map.Entry::getKey).collect(java.util.stream.Collectors.toList());
        Map<Long, String> prodNameMap = topProdIds.isEmpty() ? Map.of() :
                materialMapper.selectBatchIds(topProdIds).stream()
                        .collect(java.util.stream.Collectors.toMap(BizMaterial::getId, BizMaterial::getMaterialName, (a, b) -> a));
        List<Map<String, Object>> topProducts = new ArrayList<>();
        for (Long pid : topProdIds) {
            Map<String, Object> tp = new LinkedHashMap<>();
            tp.put("productId", pid);
            tp.put("productName", prodNameMap.getOrDefault(pid, "未知"));
            tp.put("quantity", productQtyMap.get(pid));
            topProducts.add(tp);
        }
        summary.put("topProducts", topProducts);

        return summary;
    }

    /**
     * 近7天生产趋势
     */
    public List<Map<String, Object>> getProductionTrend() {
        List<Map<String, Object>> trend = new ArrayList<>();

        // 近30天每天的产量(completedQty 之和)和新工单数
        for (int i = 29; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            LocalDateTime dayStart = date.atStartOfDay();
            LocalDateTime dayEnd = date.atTime(LocalTime.MAX);

            // 当天完工的产量(所有当天更新为 completed 的工单的 completedQty 之和)
            List<BizWorkOrder> dayCompleted = workOrderMapper.selectList(
                    new LambdaQueryWrapper<BizWorkOrder>()
                            .eq(BizWorkOrder::getStatus, "completed")
                            .ge(BizWorkOrder::getUpdateTime, dayStart)
                            .le(BizWorkOrder::getUpdateTime, dayEnd));
            BigDecimal dayQty = dayCompleted.stream()
                    .map(w -> w.getCompletedQty() != null ? w.getCompletedQty() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            long newCount = workOrderMapper.selectCount(
                    new LambdaQueryWrapper<BizWorkOrder>()
                            .ge(BizWorkOrder::getCreateTime, dayStart)
                            .le(BizWorkOrder::getCreateTime, dayEnd));

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", date.toString());
            item.put("completed", dayQty);
            item.put("created", newCount);
            trend.add(item);
        }

        return trend;
    }

    /**
     * 订单状态分布
     */
    public Map<String, Object> getOrderStatus() {
        Map<String, Object> statusMap = new LinkedHashMap<>();

        String[] statuses = {"draft", "pending_approval", "confirmed", "mrp_planned", "outsourcing",
                "in_production", "partial_shipped", "shipped", "completed", "settled", "cancelled", "voided"};
        for (String status : statuses) {
            long count = salesOrderMapper.selectCount(
                    new LambdaQueryWrapper<BizSalesOrder>()
                            .eq(BizSalesOrder::getStatus, status));
            statusMap.put(status, count);
        }

        return statusMap;
    }

    /**
     * 角色差异化看板数据
     */
    public Map<String, Object> getRoleDashboard(String role) {
        switch (role != null ? role : "boss") {
            case "boss": return getBossDashboard();
            case "production_manager": return getProductionManagerDashboard();
            case "sales": return getSalesDashboard();
            case "warehouse": return getWarehouseDashboard();
            case "finance": return getFinanceDashboard();
            case "quality": return getQualityDashboard();
            case "worker": return getWorkerDashboard();
            default: return getBossDashboard();
        }
    }

    /**
     * 老板看板：全量KPI + 预警列表
     */
    private Map<String, Object> getBossDashboard() {
        Map<String, Object> data = new LinkedHashMap<>();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        // KPI卡片
        data.put("todayOrders", salesOrderMapper.selectCount(
                new LambdaQueryWrapper<BizSalesOrder>()
                        .ge(BizSalesOrder::getCreateTime, todayStart)
                        .le(BizSalesOrder::getCreateTime, todayEnd)));

        // 本月营收
        List<BizSalesOrder> monthOrders = salesOrderMapper.selectList(
                new LambdaQueryWrapper<BizSalesOrder>()
                        .ge(BizSalesOrder::getCreateTime, monthStart)
                        .notIn(BizSalesOrder::getStatus, "draft", "cancelled"));
        BigDecimal monthRevenue = monthOrders.stream()
                .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        data.put("monthRevenue", monthRevenue);

        // 进行中工单
        long activeWo = workOrderMapper.selectCount(
                new LambdaQueryWrapper<BizWorkOrder>()
                        .in(BizWorkOrder::getStatus, "in_progress", "pending"));
        data.put("activeWorkOrders", activeWo);

        // 逾期应收
        long overdueAr = receivableMapper.selectCount(
                new LambdaQueryWrapper<BizReceivable>()
                        .lt(BizReceivable::getDueDate, LocalDate.now())
                        .notIn(BizReceivable::getStatus, "received"));
        data.put("overdueReceivables", overdueAr);

        // 库存预警数
        long alerts = inventoryMapper.selectCount(
                new LambdaQueryWrapper<BizInventory>()
                        .gt(BizInventory::getSafetyStock, BigDecimal.ZERO)
                        .apply("available_qty < safety_stock"));
        data.put("inventoryAlerts", alerts);

        // 未处理异常
        long pendingExceptions = exceptionReportMapper.selectCount(
                new LambdaQueryWrapper<BizExceptionReport>()
                        .eq(BizExceptionReport::getStatus, "pending"));
        data.put("pendingExceptions", pendingExceptions);

        // 生产趋势
        data.put("productionTrend", getProductionTrend());
        data.put("orderStatus", getOrderStatus());

        return data;
    }

    /**
     * 生产主管看板：生产进度 + 排产负荷 + 质量统计
     */
    private Map<String, Object> getProductionManagerDashboard() {
        Map<String, Object> data = new LinkedHashMap<>();

        // 工单状态统计
        String[] woStatuses = {"pending", "in_progress", "completed", "cancelled"};
        Map<String, Long> woStats = new LinkedHashMap<>();
        for (String s : woStatuses) {
            woStats.put(s, workOrderMapper.selectCount(
                    new LambdaQueryWrapper<BizWorkOrder>().eq(BizWorkOrder::getStatus, s)));
        }
        data.put("workOrderStats", woStats);

        // 逾期工单
        long overdueWo = workOrderMapper.selectCount(
                new LambdaQueryWrapper<BizWorkOrder>()
                        .lt(BizWorkOrder::getDeliveryDate, LocalDate.now())
                        .notIn(BizWorkOrder::getStatus, "completed", "cancelled"));
        data.put("overdueWorkOrders", overdueWo);

        // 今日完工数
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);
        long todayCompleted = workOrderMapper.selectCount(
                new LambdaQueryWrapper<BizWorkOrder>()
                        .eq(BizWorkOrder::getStatus, "completed")
                        .ge(BizWorkOrder::getUpdateTime, todayStart)
                        .le(BizWorkOrder::getUpdateTime, todayEnd));
        data.put("todayCompleted", todayCompleted);

        // 待检验任务
        long pendingQc = qcTaskMapper.selectCount(
                new LambdaQueryWrapper<BizQcTask>().eq(BizQcTask::getResult, "pending"));
        data.put("pendingQcTasks", pendingQc);

        // 质量统计 - 近30天良品率
        LocalDateTime month30 = LocalDate.now().minusDays(30).atStartOfDay();
        List<BizQcTask> recentQc = qcTaskMapper.selectList(
                new LambdaQueryWrapper<BizQcTask>()
                        .ne(BizQcTask::getResult, "pending")
                        .ge(BizQcTask::getInspectedAt, month30));
        long passCount = recentQc.stream().filter(t -> "pass".equals(t.getResult())).count();
        BigDecimal yieldRate = recentQc.isEmpty() ? BigDecimal.valueOf(100)
                : BigDecimal.valueOf(passCount * 100).divide(BigDecimal.valueOf(recentQc.size()), 1, RoundingMode.HALF_UP);
        data.put("yieldRate", yieldRate);

        // 未处理异常
        long pendingExceptions = exceptionReportMapper.selectCount(
                new LambdaQueryWrapper<BizExceptionReport>().eq(BizExceptionReport::getStatus, "pending"));
        data.put("pendingExceptions", pendingExceptions);

        data.put("productionTrend", getProductionTrend());

        return data;
    }

    /**
     * 销售看板：订单统计 + 交期达成
     */
    private Map<String, Object> getSalesDashboard() {
        Map<String, Object> data = new LinkedHashMap<>();

        // 订单状态分布
        data.put("orderStatus", getOrderStatus());

        // 本月新订单数和金额
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        List<BizSalesOrder> monthOrders = salesOrderMapper.selectList(
                new LambdaQueryWrapper<BizSalesOrder>()
                        .ge(BizSalesOrder::getCreateTime, monthStart)
                        .notIn(BizSalesOrder::getStatus, "draft", "cancelled", "voided"));
        data.put("monthOrderCount", monthOrders.size());
        BigDecimal monthAmount = monthOrders.stream()
                .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        data.put("monthOrderAmount", monthAmount);

        // 待审批订单
        long pendingApproval = salesOrderMapper.selectCount(
                new LambdaQueryWrapper<BizSalesOrder>()
                        .eq(BizSalesOrder::getStatus, "pending_approval"));
        data.put("pendingApproval", pendingApproval);

        // 交期达成率 - 近30天已完成工单中按时交付的比例
        LocalDateTime day30 = LocalDate.now().minusDays(30).atStartOfDay();
        List<BizWorkOrder> completedWos = workOrderMapper.selectList(
                new LambdaQueryWrapper<BizWorkOrder>()
                        .eq(BizWorkOrder::getStatus, "completed")
                        .ge(BizWorkOrder::getUpdateTime, day30));
        long onTimeCount = completedWos.stream()
                .filter(wo -> wo.getDeliveryDate() != null && wo.getUpdateTime() != null
                        && !wo.getUpdateTime().toLocalDate().isAfter(wo.getDeliveryDate()))
                .count();
        BigDecimal onTimeRate = completedWos.isEmpty() ? BigDecimal.valueOf(100)
                : BigDecimal.valueOf(onTimeCount * 100).divide(BigDecimal.valueOf(completedWos.size()), 1, RoundingMode.HALF_UP);
        data.put("onTimeDeliveryRate", onTimeRate);

        // 逾期应收
        long overdueAr = receivableMapper.selectCount(
                new LambdaQueryWrapper<BizReceivable>()
                        .lt(BizReceivable::getDueDate, LocalDate.now())
                        .notIn(BizReceivable::getStatus, "received"));
        data.put("overdueReceivables", overdueAr);

        return data;
    }

    /**
     * 仓库看板：库存预警 + 出入库统计
     */
    private Map<String, Object> getWarehouseDashboard() {
        Map<String, Object> data = new LinkedHashMap<>();

        // 库存预警
        long alerts = inventoryMapper.selectCount(
                new LambdaQueryWrapper<BizInventory>()
                        .gt(BizInventory::getSafetyStock, BigDecimal.ZERO)
                        .apply("available_qty < safety_stock"));
        data.put("inventoryAlerts", alerts);

        // 缺货物料数
        long stockout = inventoryMapper.selectCount(
                new LambdaQueryWrapper<BizInventory>()
                        .le(BizInventory::getAvailableQty, BigDecimal.ZERO));
        data.put("stockoutCount", stockout);

        // 今日出入库统计
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);
        long todayIn = invTransactionMapper.selectCount(
                new LambdaQueryWrapper<BizInvTransaction>()
                        .in(BizInvTransaction::getTransactionType,
                                "purchase_in", "production_in", "semi_in", "outsource_in", "other_in")
                        .ge(BizInvTransaction::getCreateTime, todayStart)
                        .le(BizInvTransaction::getCreateTime, todayEnd));
        long todayOut = invTransactionMapper.selectCount(
                new LambdaQueryWrapper<BizInvTransaction>()
                        .in(BizInvTransaction::getTransactionType,
                                "production_out", "sales_out", "outsource_out", "scrap_out", "other_out")
                        .ge(BizInvTransaction::getCreateTime, todayStart)
                        .le(BizInvTransaction::getCreateTime, todayEnd));
        data.put("todayStockIn", todayIn);
        data.put("todayStockOut", todayOut);

        // 锁定物料数
        long lockedCount = inventoryMapper.selectCount(
                new LambdaQueryWrapper<BizInventory>()
                        .gt(BizInventory::getLockedQty, BigDecimal.ZERO));
        data.put("lockedMaterialCount", lockedCount);

        // 总SKU数
        long totalSku = inventoryMapper.selectCount(new LambdaQueryWrapper<>());
        data.put("totalSku", totalSku);

        return data;
    }

    /**
     * 财务看板：收支趋势 + 应收账龄
     */
    private Map<String, Object> getFinanceDashboard() {
        Map<String, Object> data = new LinkedHashMap<>();

        // 应收汇总
        List<BizReceivable> allAr = receivableMapper.selectList(
                new LambdaQueryWrapper<BizReceivable>().notIn(BizReceivable::getStatus, "received"));
        BigDecimal totalAr = allAr.stream()
                .map(r -> r.getUnreceivedAmount() != null ? r.getUnreceivedAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        data.put("totalReceivable", totalAr);

        // 逾期应收
        long overdueAr = allAr.stream()
                .filter(r -> r.getDueDate() != null && r.getDueDate().isBefore(LocalDate.now()))
                .count();
        data.put("overdueReceivableCount", overdueAr);

        // 应收账龄分布
        Map<String, Long> aging = new LinkedHashMap<>();
        long within30 = 0, within60 = 0, within90 = 0, over90 = 0;
        LocalDate today = LocalDate.now();
        for (BizReceivable r : allAr) {
            if (r.getDueDate() == null) { within30++; continue; }
            long days = java.time.temporal.ChronoUnit.DAYS.between(r.getDueDate(), today);
            if (days <= 0) within30++;
            else if (days <= 30) within30++;
            else if (days <= 60) within60++;
            else if (days <= 90) within90++;
            else over90++;
        }
        aging.put("0-30天", within30);
        aging.put("31-60天", within60);
        aging.put("61-90天", within90);
        aging.put("90天以上", over90);
        data.put("arAging", aging);

        // 本月营收
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        List<BizSalesOrder> monthOrders = salesOrderMapper.selectList(
                new LambdaQueryWrapper<BizSalesOrder>()
                        .ge(BizSalesOrder::getCreateTime, monthStart)
                        .notIn(BizSalesOrder::getStatus, "draft", "cancelled"));
        BigDecimal monthRevenue = monthOrders.stream()
                .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        data.put("monthRevenue", monthRevenue);

        // 本月成本
        BigDecimal monthCost = BigDecimal.ZERO;
        List<BizWorkOrder> monthWo = workOrderMapper.selectList(
                new LambdaQueryWrapper<BizWorkOrder>()
                        .ge(BizWorkOrder::getCreateTime, monthStart));
        for (BizWorkOrder wo : monthWo) {
            BizWoCost cost = woCostMapper.selectOne(
                    new LambdaQueryWrapper<BizWoCost>().eq(BizWoCost::getWorkOrderId, wo.getId()));
            if (cost != null) {
                monthCost = monthCost.add(cost.getTotalCost());
            }
        }
        data.put("monthCost", monthCost);
        data.put("monthProfit", monthRevenue.subtract(monthCost));

        return data;
    }

    /**
     * 质检看板：良品率趋势 + TOP10缺陷原因
     */
    private Map<String, Object> getQualityDashboard() {
        Map<String, Object> data = new LinkedHashMap<>();

        // 待检任务
        long pendingQc = qcTaskMapper.selectCount(
                new LambdaQueryWrapper<BizQcTask>().eq(BizQcTask::getResult, "pending"));
        data.put("pendingTasks", pendingQc);

        // 今日检验完成
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);
        long todayInspected = qcTaskMapper.selectCount(
                new LambdaQueryWrapper<BizQcTask>()
                        .ne(BizQcTask::getResult, "pending")
                        .ge(BizQcTask::getInspectedAt, todayStart)
                        .le(BizQcTask::getInspectedAt, todayEnd));
        data.put("todayInspected", todayInspected);

        // 近30天良品率趋势（按天）
        List<Map<String, Object>> yieldTrend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            LocalDateTime dayStart = date.atStartOfDay();
            LocalDateTime dayEnd = date.atTime(LocalTime.MAX);

            List<BizQcTask> dayTasks = qcTaskMapper.selectList(
                    new LambdaQueryWrapper<BizQcTask>()
                            .ne(BizQcTask::getResult, "pending")
                            .ge(BizQcTask::getInspectedAt, dayStart)
                            .le(BizQcTask::getInspectedAt, dayEnd));
            long pass = dayTasks.stream().filter(t -> "pass".equals(t.getResult())).count();
            BigDecimal rate = dayTasks.isEmpty() ? BigDecimal.valueOf(100)
                    : BigDecimal.valueOf(pass * 100).divide(BigDecimal.valueOf(dayTasks.size()), 1, RoundingMode.HALF_UP);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", date.toString());
            item.put("total", dayTasks.size());
            item.put("pass", pass);
            item.put("yieldRate", rate);
            yieldTrend.add(item);
        }
        data.put("yieldTrend", yieldTrend);

        // 待处理缺陷
        long pendingDefects = defectRecordMapper.selectCount(
                new LambdaQueryWrapper<BizDefectRecord>()
                        .eq(BizDefectRecord::getHandleStatus, "pending"));
        data.put("pendingDefects", pendingDefects);

        // TOP缺陷原因（取最近100条缺陷记录中的原因统计）
        List<BizDefectRecord> recentDefects = defectRecordMapper.selectList(
                new LambdaQueryWrapper<BizDefectRecord>()
                        .isNotNull(BizDefectRecord::getDefectReason)
                        .orderByDesc(BizDefectRecord::getCreateTime)
                        .last("LIMIT 100"));
        Map<String, Integer> reasonCounts = new LinkedHashMap<>();
        for (BizDefectRecord d : recentDefects) {
            if (d.getDefectReason() != null && !d.getDefectReason().isEmpty()) {
                reasonCounts.merge(d.getDefectReason(), 1, Integer::sum);
            }
        }
        // 按数量降序取TOP10
        List<Map<String, Object>> topReasons = new ArrayList<>();
        reasonCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .forEach(e -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("reason", e.getKey());
                    item.put("count", e.getValue());
                    topReasons.add(item);
                });
        data.put("topDefectReasons", topReasons);

        return data;
    }

    /**
     * 工人看板：个人产量/工资统计
     */
    private Map<String, Object> getWorkerDashboard() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("todayProduction", workOrderMapper.selectCount(
                new LambdaQueryWrapper<BizWorkOrder>()
                        .eq(BizWorkOrder::getStatus, "completed")
                        .ge(BizWorkOrder::getUpdateTime, LocalDate.now().atStartOfDay())));
        data.put("pendingTasks", qcTaskMapper.selectCount(
                new LambdaQueryWrapper<BizQcTask>().eq(BizQcTask::getResult, "pending")));
        return data;
    }

    /**
     * 预警列表
     */
    public Map<String, Object> getAlerts() {
        Map<String, Object> data = new LinkedHashMap<>();
        List<Map<String, Object>> alerts = new ArrayList<>();

        // 库存预警
        List<BizInventory> lowStock = inventoryMapper.selectList(
                new LambdaQueryWrapper<BizInventory>()
                        .gt(BizInventory::getSafetyStock, BigDecimal.ZERO)
                        .apply("available_qty < safety_stock")
                        .last("LIMIT 10"));
        for (BizInventory inv : lowStock) {
            Map<String, Object> alert = new LinkedHashMap<>();
            alert.put("type", "inventory");
            alert.put("level", "warning");
            alert.put("message", "物料(ID:" + inv.getMaterialId() + ") 库存不足，当前:" + inv.getAvailableQty() + "，安全库存:" + inv.getSafetyStock());
            alert.put("time", inv.getUpdateTime());
            alerts.add(alert);
        }

        // 逾期应收
        List<BizReceivable> overdueAr = receivableMapper.selectList(
                new LambdaQueryWrapper<BizReceivable>()
                        .lt(BizReceivable::getDueDate, LocalDate.now())
                        .notIn(BizReceivable::getStatus, "received")
                        .last("LIMIT 10"));
        for (BizReceivable r : overdueAr) {
            Map<String, Object> alert = new LinkedHashMap<>();
            alert.put("type", "finance");
            alert.put("level", "danger");
            alert.put("message", "应收账款 " + r.getReceivableNo() + " 已逾期，金额:" + r.getUnreceivedAmount());
            alert.put("time", r.getDueDate());
            alerts.add(alert);
        }

        // 未处理异常
        List<BizExceptionReport> exceptions = exceptionReportMapper.selectList(
                new LambdaQueryWrapper<BizExceptionReport>()
                        .eq(BizExceptionReport::getStatus, "pending")
                        .last("LIMIT 10"));
        for (BizExceptionReport e : exceptions) {
            Map<String, Object> alert = new LinkedHashMap<>();
            alert.put("type", "exception");
            alert.put("level", "warning");
            alert.put("message", "异常待处理: " + e.getDescription());
            alert.put("time", e.getCreateTime());
            alerts.add(alert);
        }

        data.put("alerts", alerts);
        data.put("total", alerts.size());
        return data;
    }

    /**
     * AI简报数据
     */
    public Map<String, Object> getAiBriefing() {
        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> summary = getSummary();
        data.put("summary", summary);
        data.put("briefing", "今日新增订单 " + summary.get("todayOrders") + " 单，"
                + "完成生产 " + summary.get("todayProduction") + " 单，"
                + "库存预警 " + summary.get("inventoryAlerts") + " 项，"
                + "待处理质检 " + summary.get("pendingTasks") + " 项。");
        data.put("generatedAt", LocalDateTime.now().toString());
        return data;
    }
}
