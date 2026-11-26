package com.aimes.modules.ai.chat;

import com.aimes.modules.ai.service.DeepSeekClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 意图路由器 — 两阶段意图识别
 *
 * 阶段A: 规则快速匹配（零延迟，覆盖80%场景）
 * 阶段B: LLM轻量分类（规则匹配失败时，<200 token输出）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntentRouter {

    private final DeepSeekClient deepSeekClient;
    private final ObjectMapper objectMapper;

    /**
     * 规则匹配表：关键词列表 -> 意图
     * 使用 LinkedHashMap 保持优先级顺序
     */
    private static final LinkedHashMap<List<String>, String> KEYWORD_RULES = new LinkedHashMap<>();

    static {
        // S04: 每日简报
        KEYWORD_RULES.put(List.of("今日简报", "日报", "每日简报", "工厂情况", "运营情况", "运营概况",
                "今天情况", "生产情况", "工厂简报", "当日简报", "今日总结"), "daily_briefing");
        // S08: 质量趋势
        KEYWORD_RULES.put(List.of("质量趋势", "质检分析", "合格率", "不良品趋势", "质量分析",
                "质检情况", "质量报告", "质量怎么样", "不良率"), "quality_trend");
        // S09: 生产效率
        KEYWORD_RULES.put(List.of("生产效率", "产能分析", "OEE", "产能", "效率分析",
                "产能利用", "生产瓶颈", "瓶颈分析", "效率怎么样"), "production_efficiency");
        // S10: 交期风险
        KEYWORD_RULES.put(List.of("交期风险", "逾期", "到期", "延期", "交期预警", "交期分析",
                "快到期", "要逾期", "交期怎么样", "交货风险"), "delivery_risk");
        // S06: 采购建议
        KEYWORD_RULES.put(List.of("采购建议", "补货", "库存不足", "该买什么", "采购计划",
                "缺货", "要采购", "采购分析", "补货建议"), "purchase_suggestion");
        // S11: 成本分析
        KEYWORD_RULES.put(List.of("成本分析", "损耗分析", "报废成本", "成本", "损耗",
                "降本", "成本优化", "报废分析"), "cost_analysis");
        // S03: 排产
        KEYWORD_RULES.put(List.of("排产", "排个计划", "排个生产计划", "怎么排", "赶工",
                "排产建议", "排工", "排一下"), "scheduling");
        // S01: 智能接单
        KEYWORD_RULES.put(List.of("接单", "解析订单", "订单文本", "订单解析", "识别订单",
                "解析一下订单", "帮我接单"), "smart_order");
        // S02: 产品建档
        KEYWORD_RULES.put(List.of("建档", "新建产品", "产品信息", "产品建档", "帮我建档",
                "创建产品", "录入产品"), "product_filing");
        // S05: 变更评估
        KEYWORD_RULES.put(List.of("变更评估", "改单影响", "变更影响", "订单变更",
                "改单评估", "评估影响"), "change_assessment");
        // S07: 异常分析
        KEYWORD_RULES.put(List.of("异常分析", "异常原因", "根因分析", "根因", "异常处理",
                "分析异常", "异常怎么处理"), "exception_analysis");
    }

    /** 智能查询意图的关键词 — 需要聚合/统计/计算的问题 */
    private static final List<String> SMART_QUERY_KEYWORDS = List.of(
            "总金额", "总额", "总计", "合计", "汇总", "总量",
            "平均", "均价", "平均值",
            "最大", "最小", "最高", "最低", "最多", "最少",
            "排名", "排行", "前几", "TOP",
            "统计", "占比", "比例", "百分比",
            "总共", "一共", "累计", "共有多少",
            "同比", "环比", "增长", "下降",
            "每个", "各个", "分别", "按.*分"
    );

    /** 查询意图的关键词 */
    private static final List<String> QUERY_KEYWORDS = List.of(
            "有哪些", "查一下", "多少", "列表", "哪些", "查询", "找一下",
            "帮我查", "看看有", "搜索", "搜一下", "有没有"
    );

    /** 录入意图的关键词 */
    private static final List<String> INPUT_KEYWORDS = List.of(
            "录入", "帮我添加", "新增", "导入", "创建", "帮我加"
    );

    /**
     * 识别用户意图
     *
     * @param message     用户消息
     * @param currentPage 当前页面路由
     * @param session     当前会话（用于上下文感知）
     * @return 意图识别结果
     */
    public IntentResult classify(String message, String currentPage, ChatSession session) {
        if (message == null || message.isBlank()) {
            return IntentResult.of("chat");
        }

        String msg = message.trim();

        // 阶段A: 规则快速匹配
        IntentResult ruleResult = ruleBasedMatch(msg, session);
        if (ruleResult != null) {
            log.debug("规则匹配命中意图: {}", ruleResult.getIntent());
            return ruleResult;
        }

        // 智能查询意图（聚合/统计/计算 — 优先于普通查询）
        for (String kw : SMART_QUERY_KEYWORDS) {
            if (msg.contains(kw)) {
                IntentResult r = IntentResult.of("smart_query");
                r.setExtractedInput(msg);
                return r;
            }
        }

        // 查询意图
        for (String kw : QUERY_KEYWORDS) {
            if (msg.contains(kw)) {
                IntentResult r = IntentResult.of("query");
                r.setExtractedInput(msg);
                // 尝试从页面推断模块
                r.setModule(inferModuleFromPage(currentPage));
                return r;
            }
        }

        // 录入意图
        for (String kw : INPUT_KEYWORDS) {
            if (msg.contains(kw)) {
                IntentResult r = IntentResult.of("input");
                r.setExtractedInput(msg);
                r.setModule(inferModuleFromPage(currentPage));
                return r;
            }
        }

        // 阶段B: LLM轻量分类
        IntentResult llmResult = llmClassify(msg, currentPage, session);
        if (llmResult != null) {
            return llmResult;
        }

        // 兜底: 普通对话
        return IntentResult.of("chat");
    }

    /**
     * 阶段A: 规则快速匹配
     */
    private IntentResult ruleBasedMatch(String message, ChatSession session) {
        for (Map.Entry<List<String>, String> entry : KEYWORD_RULES.entrySet()) {
            for (String keyword : entry.getKey()) {
                if (message.contains(keyword)) {
                    String intent = entry.getValue();
                    IntentResult result = IntentResult.of(intent);

                    // 需要用户输入的场景：检查是否已提供足够信息
                    if ("scheduling".equals(intent) && !hasSchedulingContext(message, session)) {
                        return IntentResult.needInput(intent,
                                "请告诉我需要排产的工单，或者我可以帮你排待处理的工单。要排哪些？");
                    }
                    if ("exception_analysis".equals(intent) && message.length() < 15) {
                        return IntentResult.needInput(intent,
                                "请详细描述一下遇到的异常情况，包括发生时间、位置、现象等信息。");
                    }
                    if ("change_assessment".equals(intent) && message.length() < 15) {
                        return IntentResult.needInput(intent,
                                "请描述具体的变更内容，比如：哪个订单、改了什么（数量/交期/规格）。");
                    }
                    if ("smart_order".equals(intent) && message.length() < 20) {
                        return IntentResult.needInput(intent,
                                "请粘贴客户的订单文本或上传订单文件，我来帮你解析。");
                    }
                    if ("product_filing".equals(intent) && message.length() < 15) {
                        return IntentResult.needInput(intent,
                                "请描述产品信息，比如：名称、规格、材质、用途等。");
                    }

                    result.setExtractedInput(message);
                    return result;
                }
            }
        }
        return null;
    }

    /**
     * 阶段B: LLM轻量分类 — 用小prompt快速判断意图
     */
    private IntentResult llmClassify(String message, String currentPage, ChatSession session) {
        try {
            String lastIntent = session != null ? session.getLastIntent() : null;
            String contextInfo = "";
            if (lastIntent != null) {
                contextInfo = "上次对话意图: " + lastIntent + "\n";
            }
            if (currentPage != null) {
                contextInfo += "用户当前页面: " + currentPage + "\n";
            }

            String prompt = "你是意图分类器。根据用户消息判断意图。\n\n" +
                    contextInfo +
                    "意图类型(选一个):\n" +
                    "- daily_briefing: 想看工厂日报/运营概况\n" +
                    "- quality_trend: 想看质量分析/趋势\n" +
                    "- production_efficiency: 想看生产效率/产能\n" +
                    "- delivery_risk: 想看交期风险/逾期\n" +
                    "- purchase_suggestion: 想要采购建议/补货\n" +
                    "- cost_analysis: 想看成本/损耗分析\n" +
                    "- scheduling: 想排产/排计划\n" +
                    "- smart_order: 想解析订单文本\n" +
                    "- product_filing: 想建档/创建产品\n" +
                    "- change_assessment: 想评估变更影响\n" +
                    "- exception_analysis: 想分析异常\n" +
                    "- smart_query: 需要统计/聚合/计算的数据问题(如总金额、平均值、排名、占比、增长率等)\n" +
                    "- query: 想查看数据列表/记录\n" +
                    "- input: 想录入/新增数据\n" +
                    "- chat: 普通闲聊/帮助/其他\n\n" +
                    "只返回JSON: {\"intent\":\"...\",\"module\":\"...\"}";

            String result = deepSeekClient.chatQuick(prompt, "用户消息: " + message, 0.0, 100);
            if (result != null) {
                String json = deepSeekClient.cleanJsonResponse(result);
                JsonNode node = objectMapper.readTree(json);
                String intent = node.path("intent").asText("chat");
                String module = node.path("module").asText(null);
                return IntentResult.of(intent, module);
            }
        } catch (Exception e) {
            log.warn("LLM意图分类失败，降级为chat: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 从页面路由推断模块
     */
    private String inferModuleFromPage(String currentPage) {
        if (currentPage == null) return null;
        Map<String, String> routeModuleMap = Map.ofEntries(
                Map.entry("/order", "salesOrder"),
                Map.entry("/production/work-order", "workOrder"),
                Map.entry("/master/customer", "customer"),
                Map.entry("/master/supplier", "supplier"),
                Map.entry("/master/material", "material"),
                Map.entry("/master/product", "product"),
                Map.entry("/inventory", "inventory"),
                Map.entry("/purchase", "purchaseOrder"),
                Map.entry("/outsource", "outsourceOrder"),
                Map.entry("/quality", "qcTask"),
                Map.entry("/finance", "receivable"),
                Map.entry("/team", "team"),
                Map.entry("/bom", "bomVersion")
        );
        for (Map.Entry<String, String> entry : routeModuleMap.entrySet()) {
            if (currentPage.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private boolean hasSchedulingContext(String message, ChatSession session) {
        // 如果消息足够长或上次意图有相关数据，认为有上下文
        if (message.length() > 30) return true;
        if (session != null && session.getLastScenarioData() != null) return true;
        // 包含"待处理"、"所有"等词认为是要排全部
        return message.contains("待处理") || message.contains("所有") || message.contains("全部");
    }
}
