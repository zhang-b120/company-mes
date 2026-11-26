package com.aimes.modules.ai.service;

import com.aimes.modules.ai.prompt.PromptTemplates;
import com.aimes.modules.ai.vo.AiReportVO;
import com.aimes.modules.inventory.entity.BizInventory;
import com.aimes.modules.inventory.mapper.BizInventoryMapper;
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
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * AI看板/仪表盘场景服务
 * 自动查询真实业务数据再调用AI分析，实现数据驱动的智能报告
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiDashboardService {

    private final DeepSeekClient deepSeekClient;
    private final BizSalesOrderMapper salesOrderMapper;
    private final BizWorkOrderMapper workOrderMapper;
    private final BizInventoryMapper inventoryMapper;
    private final BizQcTaskMapper qcTaskMapper;
    private final BizDefectRecordMapper defectRecordMapper;
    private final BizExceptionReportMapper exceptionReportMapper;
    private final ObjectMapper objectMapper;

    /**
     * 每日简报：自动拉取工厂当日运营数据，AI生成结构化日报
     */
    public AiReportVO dailyBriefing() {
        String factoryData = collectDailyStats();
        String result = deepSeekClient.chat(PromptTemplates.DAILY_REPORT, factoryData);
        return parseReport(result, "每日简报");
    }

    /**
     * 采购建议：自动拉取库存数据，AI生成采购计划
     */
    public String purchaseSuggestion() {
        String inventoryData = collectInventoryStats();
        return deepSeekClient.chat(PromptTemplates.PURCHASE_SUGGESTION, inventoryData);
    }

    /**
     * 异常分析：基于真实异常记录进行深度分析
     */
    public AiReportVO exceptionAnalysis(String exceptionInfo) {
        // 附加近期异常统计上下文
        String context = collectExceptionContext() + "\n\n当前需要分析的异常:\n" + exceptionInfo;
        String result = deepSeekClient.chat(PromptTemplates.EXCEPTION_ANALYSIS, context);
        return parseReport(result, "异常分析");
    }

    /**
     * 质量趋势分析：拉取近期质检和不良品数据，AI生成趋势报告
     */
    public AiReportVO qualityTrendAnalysis() {
        String qualityData = collectQualityStats();
        String result = deepSeekClient.chat(PromptTemplates.QUALITY_TREND, qualityData);
        return parseReport(result, "质量趋势分析");
    }

    /**
     * 生产效率分析：拉取工单完成情况，AI生成效率分析报告
     */
    public AiReportVO productionEfficiencyAnalysis() {
        String productionData = collectProductionStats();
        String result = deepSeekClient.chat(PromptTemplates.PRODUCTION_EFFICIENCY, productionData);
        return parseReport(result, "生产效率分析");
    }

    /**
     * 交期风险预警：分析即将到期的订单和工单
     */
    public AiReportVO deliveryRiskAnalysis() {
        String deliveryData = collectDeliveryRiskData();
        String result = deepSeekClient.chat(PromptTemplates.DELIVERY_RISK, deliveryData);
        return parseReport(result, "交期风险预警");
    }

    /**
     * 成本分析：拉取不良品损失、工时等数据进行成本分析
     */
    public AiReportVO costAnalysis() {
        String costData = collectCostData();
        String result = deepSeekClient.chat(PromptTemplates.COST_ANALYSIS, costData);
        return parseReport(result, "成本分析");
    }

    // ==================== 数据采集方法 ====================

    public String collectDailyStats() {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(LocalTime.MAX);
        StringBuilder sb = new StringBuilder();
        sb.append("=== 工厂每日运营数据 (").append(today).append(") ===\n\n");

        // 订单统计
        try {
            long totalOrders = salesOrderMapper.selectCount(new QueryWrapper<BizSalesOrder>());
            long todayOrders = salesOrderMapper.selectCount(
                    new QueryWrapper<BizSalesOrder>().between("create_time", startOfDay, endOfDay));
            long pendingOrders = salesOrderMapper.selectCount(
                    new QueryWrapper<BizSalesOrder>().eq("status", "pending_approval"));
            sb.append("【订单情况】\n");
            sb.append("- 订单总数: ").append(totalOrders).append("\n");
            sb.append("- 今日新增订单: ").append(todayOrders).append("\n");
            sb.append("- 待审批订单: ").append(pendingOrders).append("\n\n");
        } catch (Exception e) {
            sb.append("【订单情况】数据获取失败\n\n");
        }

        // 工单统计
        try {
            long totalWo = workOrderMapper.selectCount(new QueryWrapper<BizWorkOrder>());
            long inProgressWo = workOrderMapper.selectCount(
                    new QueryWrapper<BizWorkOrder>().eq("status", "in_progress"));
            long completedWo = workOrderMapper.selectCount(
                    new QueryWrapper<BizWorkOrder>().eq("status", "completed"));
            long pendingWo = workOrderMapper.selectCount(
                    new QueryWrapper<BizWorkOrder>().eq("status", "pending"));
            sb.append("【生产工单】\n");
            sb.append("- 工单总数: ").append(totalWo).append("\n");
            sb.append("- 进行中: ").append(inProgressWo).append("\n");
            sb.append("- 已完成: ").append(completedWo).append("\n");
            sb.append("- 待处理: ").append(pendingWo).append("\n\n");
        } catch (Exception e) {
            sb.append("【生产工单】数据获取失败\n\n");
        }

        // 库存预警
        try {
            List<BizInventory> lowStockItems = inventoryMapper.selectList(
                    new LambdaQueryWrapper<BizInventory>()
                            .gt(BizInventory::getSafetyStock, 0)
                            .apply("available_qty < safety_stock"));
            sb.append("【库存预警】\n");
            sb.append("- 低于安全库存的物料数量: ").append(lowStockItems.size()).append("\n");
            for (BizInventory inv : lowStockItems.subList(0, Math.min(10, lowStockItems.size()))) {
                sb.append("  · 物料ID=").append(inv.getMaterialId())
                        .append(", 可用数量=").append(inv.getAvailableQty())
                        .append(", 安全库存=").append(inv.getSafetyStock()).append("\n");
            }
            sb.append("\n");
        } catch (Exception e) {
            sb.append("【库存预警】数据获取失败\n\n");
        }

        // 质量统计
        try {
            long totalQc = qcTaskMapper.selectCount(new LambdaQueryWrapper<>());
            long passedQc = qcTaskMapper.selectCount(
                    new LambdaQueryWrapper<BizQcTask>().eq(BizQcTask::getResult, "pass"));
            long failedQc = qcTaskMapper.selectCount(
                    new LambdaQueryWrapper<BizQcTask>().eq(BizQcTask::getResult, "fail"));
            long pendingQc = qcTaskMapper.selectCount(
                    new LambdaQueryWrapper<BizQcTask>().eq(BizQcTask::getResult, "pending"));
            sb.append("【质量情况】\n");
            sb.append("- 质检任务总数: ").append(totalQc).append("\n");
            sb.append("- 合格: ").append(passedQc).append(", 不合格: ").append(failedQc)
                    .append(", 待检: ").append(pendingQc).append("\n");
            if (totalQc > 0) {
                double passRate = passedQc * 100.0 / Math.max(1, passedQc + failedQc);
                sb.append("- 质检合格率: ").append(String.format("%.1f%%", passRate)).append("\n");
            }
            sb.append("\n");
        } catch (Exception e) {
            sb.append("【质量情况】数据获取失败\n\n");
        }

        // 异常统计
        try {
            long totalExceptions = exceptionReportMapper.selectCount(new LambdaQueryWrapper<>());
            long pendingExceptions = exceptionReportMapper.selectCount(
                    new LambdaQueryWrapper<BizExceptionReport>().eq(BizExceptionReport::getStatus, "pending"));
            sb.append("【异常情况】\n");
            sb.append("- 异常报告总数: ").append(totalExceptions).append("\n");
            sb.append("- 待处理异常: ").append(pendingExceptions).append("\n");
        } catch (Exception e) {
            sb.append("【异常情况】数据获取失败\n\n");
        }

        return sb.toString();
    }

    public String collectInventoryStats() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 当前库存状况 ===\n\n");

        try {
            // 所有库存记录
            List<BizInventory> allInventory = inventoryMapper.selectList(
                    new LambdaQueryWrapper<BizInventory>().gt(BizInventory::getSafetyStock, 0));
            sb.append("设有安全库存的物料总数: ").append(allInventory.size()).append("\n\n");

            // 低于安全库存的物料
            List<BizInventory> lowStock = allInventory.stream()
                    .filter(inv -> inv.getAvailableQty().compareTo(inv.getSafetyStock()) < 0)
                    .toList();
            sb.append("低于安全库存的物料:\n");
            for (BizInventory inv : lowStock) {
                BigDecimal shortage = inv.getSafetyStock().subtract(inv.getAvailableQty());
                sb.append("- 物料ID=").append(inv.getMaterialId())
                        .append(", 仓库ID=").append(inv.getWarehouseId())
                        .append(", 可用=").append(inv.getAvailableQty())
                        .append(", 安全库存=").append(inv.getSafetyStock())
                        .append(", 缺口=").append(shortage).append("\n");
            }
            sb.append("\n库存充足的物料数: ").append(allInventory.size() - lowStock.size()).append("\n");
        } catch (Exception e) {
            sb.append("库存数据获取失败: ").append(e.getMessage()).append("\n");
        }

        return sb.toString();
    }

    public String collectExceptionContext() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 近期异常记录汇总 ===\n\n");

        try {
            long total = exceptionReportMapper.selectCount(new LambdaQueryWrapper<>());
            long pending = exceptionReportMapper.selectCount(
                    new LambdaQueryWrapper<BizExceptionReport>().eq(BizExceptionReport::getStatus, "pending"));
            sb.append("异常总数: ").append(total).append(", 待处理: ").append(pending).append("\n");

            // 近10条异常
            List<BizExceptionReport> recentExceptions = exceptionReportMapper.selectList(
                    new LambdaQueryWrapper<BizExceptionReport>()
                            .orderByDesc(BizExceptionReport::getCreateTime)
                            .last("LIMIT 10"));
            sb.append("\n最近10条异常:\n");
            for (BizExceptionReport ex : recentExceptions) {
                sb.append("- 类型=").append(ex.getExceptionType())
                        .append(", 紧急度=").append(ex.getUrgency())
                        .append(", 状态=").append(ex.getStatus())
                        .append(", 描述=").append(truncate(ex.getDescription(), 80)).append("\n");
            }
        } catch (Exception e) {
            sb.append("异常数据获取失败\n");
        }

        return sb.toString();
    }

    public String collectQualityStats() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 质量数据统计 ===\n\n");

        try {
            // 质检任务统计
            long totalQc = qcTaskMapper.selectCount(new LambdaQueryWrapper<>());
            long passQc = qcTaskMapper.selectCount(
                    new LambdaQueryWrapper<BizQcTask>().eq(BizQcTask::getResult, "pass"));
            long failQc = qcTaskMapper.selectCount(
                    new LambdaQueryWrapper<BizQcTask>().eq(BizQcTask::getResult, "fail"));
            sb.append("【质检任务统计】\n");
            sb.append("- 总数: ").append(totalQc).append(", 合格: ").append(passQc)
                    .append(", 不合格: ").append(failQc).append("\n");
            if (passQc + failQc > 0) {
                sb.append("- 合格率: ").append(String.format("%.1f%%", passQc * 100.0 / (passQc + failQc))).append("\n");
            }

            // 按质检类型分组
            for (String type : List.of("incoming", "process", "final", "outsource")) {
                long count = qcTaskMapper.selectCount(
                        new LambdaQueryWrapper<BizQcTask>().eq(BizQcTask::getQcType, type));
                long fail = qcTaskMapper.selectCount(
                        new LambdaQueryWrapper<BizQcTask>().eq(BizQcTask::getQcType, type)
                                .eq(BizQcTask::getResult, "fail"));
                if (count > 0) {
                    sb.append("- ").append(type).append(": 共").append(count)
                            .append("次, 不合格").append(fail).append("次\n");
                }
            }

            // 不良品统计
            long totalDefects = defectRecordMapper.selectCount(new LambdaQueryWrapper<>());
            long pendingDefects = defectRecordMapper.selectCount(
                    new LambdaQueryWrapper<BizDefectRecord>().eq(BizDefectRecord::getHandleStatus, "pending"));
            sb.append("\n【不良品统计】\n");
            sb.append("- 不良品记录总数: ").append(totalDefects).append(", 待处理: ").append(pendingDefects).append("\n");

            // 近期不良品
            List<BizDefectRecord> recentDefects = defectRecordMapper.selectList(
                    new LambdaQueryWrapper<BizDefectRecord>()
                            .orderByDesc(BizDefectRecord::getCreateTime)
                            .last("LIMIT 10"));
            sb.append("\n最近10条不良品记录:\n");
            for (BizDefectRecord d : recentDefects) {
                sb.append("- 编号=").append(d.getDefectNo())
                        .append(", 类型=").append(d.getDefectType())
                        .append(", 原因=").append(d.getDefectReason())
                        .append(", 等级=").append(d.getDefectLevel())
                        .append(", 数量=").append(d.getQuantity())
                        .append(", 处理路径=").append(d.getHandlePath()).append("\n");
            }
        } catch (Exception e) {
            sb.append("质量数据获取失败: ").append(e.getMessage()).append("\n");
        }

        return sb.toString();
    }

    public String collectProductionStats() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 生产效率数据 ===\n\n");

        try {
            long total = workOrderMapper.selectCount(new QueryWrapper<BizWorkOrder>());
            long completed = workOrderMapper.selectCount(
                    new QueryWrapper<BizWorkOrder>().eq("status", "completed"));
            long inProgress = workOrderMapper.selectCount(
                    new QueryWrapper<BizWorkOrder>().eq("status", "in_progress"));
            long pending = workOrderMapper.selectCount(
                    new QueryWrapper<BizWorkOrder>().eq("status", "pending"));
            long delayed = workOrderMapper.selectCount(
                    new QueryWrapper<BizWorkOrder>().ne("status", "completed")
                            .lt("delivery_date", LocalDate.now()));

            sb.append("【工单状态分布】\n");
            sb.append("- 总数: ").append(total).append("\n");
            sb.append("- 已完成: ").append(completed).append("\n");
            sb.append("- 进行中: ").append(inProgress).append("\n");
            sb.append("- 待处理: ").append(pending).append("\n");
            sb.append("- 已逾期(未完成且超过交期): ").append(delayed).append("\n");
            if (total > 0) {
                sb.append("- 完成率: ").append(String.format("%.1f%%", completed * 100.0 / total)).append("\n");
            }
        } catch (Exception e) {
            sb.append("生产数据获取失败: ").append(e.getMessage()).append("\n");
        }

        return sb.toString();
    }

    public String collectDeliveryRiskData() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 交期风险数据 ===\n\n");
        LocalDate today = LocalDate.now();
        LocalDate nextWeek = today.plusDays(7);
        LocalDate nextTwoWeeks = today.plusDays(14);

        try {
            // 已逾期工单
            long overdueWo = workOrderMapper.selectCount(
                    new QueryWrapper<BizWorkOrder>().ne("status", "completed")
                            .ne("status", "cancelled")
                            .lt("delivery_date", today));

            // 7天内到期
            long dueSoon = workOrderMapper.selectCount(
                    new QueryWrapper<BizWorkOrder>().ne("status", "completed")
                            .ne("status", "cancelled")
                            .ge("delivery_date", today)
                            .le("delivery_date", nextWeek));

            // 14天内到期
            long dueTwoWeeks = workOrderMapper.selectCount(
                    new QueryWrapper<BizWorkOrder>().ne("status", "completed")
                            .ne("status", "cancelled")
                            .ge("delivery_date", nextWeek)
                            .le("delivery_date", nextTwoWeeks));

            sb.append("【交期风险概览】日期: ").append(today).append("\n");
            sb.append("- 已逾期(未完成): ").append(overdueWo).append("个工单\n");
            sb.append("- 7天内到期: ").append(dueSoon).append("个工单\n");
            sb.append("- 7-14天内到期: ").append(dueTwoWeeks).append("个工单\n\n");

            // 已逾期的订单
            long overdueOrders = salesOrderMapper.selectCount(
                    new QueryWrapper<BizSalesOrder>().ne("status", "completed")
                            .ne("status", "cancelled")
                            .lt("delivery_date", today));
            sb.append("- 已逾期订单: ").append(overdueOrders).append("个\n");

        } catch (Exception e) {
            sb.append("交期数据获取失败: ").append(e.getMessage()).append("\n");
        }

        return sb.toString();
    }

    public String collectCostData() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 成本损耗数据 ===\n\n");

        try {
            // 不良品报废成本
            List<BizDefectRecord> defects = defectRecordMapper.selectList(
                    new LambdaQueryWrapper<BizDefectRecord>()
                            .isNotNull(BizDefectRecord::getScrapCost)
                            .gt(BizDefectRecord::getScrapCost, 0));
            BigDecimal totalScrapCost = defects.stream()
                    .map(BizDefectRecord::getScrapCost)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalScrapQty = defects.stream()
                    .filter(d -> d.getScrapQty() != null)
                    .map(BizDefectRecord::getScrapQty)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            sb.append("【不良品损失】\n");
            sb.append("- 有报废成本的不良品记录: ").append(defects.size()).append("条\n");
            sb.append("- 累计报废数量: ").append(totalScrapQty).append("\n");
            sb.append("- 累计报废成本: ").append(totalScrapCost).append("元\n\n");

            // 按不良类型分组
            sb.append("【不良类型分布】\n");
            List<BizDefectRecord> allDefects = defectRecordMapper.selectList(new LambdaQueryWrapper<>());
            allDefects.stream()
                    .filter(d -> d.getDefectType() != null)
                    .collect(java.util.stream.Collectors.groupingBy(BizDefectRecord::getDefectType,
                            java.util.stream.Collectors.counting()))
                    .forEach((type, count) -> sb.append("- ").append(type).append(": ").append(count).append("次\n"));

        } catch (Exception e) {
            sb.append("成本数据获取失败: ").append(e.getMessage()).append("\n");
        }

        return sb.toString();
    }

    // ==================== 工具方法 ====================

    private AiReportVO parseReport(String result, String sceneName) {
        if (result == null) {
            AiReportVO vo = new AiReportVO();
            vo.setSummary("AI服务暂时不可用，请稍后重试");
            return vo;
        }
        try {
            String json = deepSeekClient.cleanJsonResponse(result);
            return objectMapper.readValue(json, AiReportVO.class);
        } catch (Exception e) {
            log.warn("{}结果解析失败: {}", sceneName, e.getMessage());
            AiReportVO vo = new AiReportVO();
            vo.setSummary(result);
            return vo;
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
