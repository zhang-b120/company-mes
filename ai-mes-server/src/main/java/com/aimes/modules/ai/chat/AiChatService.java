package com.aimes.modules.ai.chat;

import com.aimes.common.utils.SecurityUtil;
import com.aimes.modules.ai.chat.dto.ChatRequest;
import com.aimes.modules.ai.chat.vo.ChatData;
import com.aimes.modules.ai.chat.vo.ChatResponse;
import com.aimes.modules.ai.dto.ConversationRequest;
import com.aimes.modules.ai.prompt.PromptTemplates;
import com.aimes.modules.ai.service.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * AI聊天服务 — 对话编排器
 *
 * 核心流程: 用户消息 → IntentRouter识别意图 → 调用对应场景引擎 → 返回Markdown回复
 * 支持连续对话: Session存储上次场景数据，追问时可引用
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatService {

    private final DeepSeekClient deepSeekClient;
    private final FilePreprocessor filePreprocessor;
    private final ChatSessionManager sessionManager;
    private final ModuleSchemaRegistry schemaRegistry;
    private final QueryExecutor queryExecutor;
    private final IntentRouter intentRouter;
    private final AiDashboardService aiDashboardService;
    private final SmartQueryExecutor smartQueryExecutor;
    private final ObjectMapper objectMapper;

    // ==================== 新版: 统一对话入口 ====================

    /**
     * 统一对话方法 — 连续对话的核心入口
     */
    public ChatResponse conversation(ConversationRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        ChatSession session = sessionManager.getOrCreate(
                request.getSessionId(), null, userId);

        String sessionId = session.getSessionId();

        // 更新页面上下文
        if (request.getCurrentPage() != null) {
            session.setCurrentPage(request.getCurrentPage());
        }

        try {
            String message = request.getMessage();
            if (message == null || message.isBlank()) {
                return ChatResponse.error(sessionId, "请输入消息");
            }

            // 1. 意图识别
            IntentResult intent = intentRouter.classify(message, session.getCurrentPage(), session);
            log.info("对话意图识别: intent={}, needsMoreInput={}", intent.getIntent(), intent.isNeedsMoreInput());

            // 2. 如果需要补充信息，直接回复追问
            if (intent.isNeedsMoreInput()) {
                session.setLastIntent(intent.getIntent());
                session.addMessage("user", message);
                String clarify = intent.getClarifyPrompt();
                session.addMessage("assistant", clarify);
                return ChatResponse.of(sessionId, intent.getIntent(), clarify);
            }

            // 3. 根据意图路由到对应处理逻辑
            ChatResponse response = routeToHandler(sessionId, message, intent, session);

            // 4. 更新会话上下文
            session.addMessage("user", message);
            session.addMessage("assistant", response.getReply());
            session.setLastIntent(intent.getIntent());

            return response;

        } catch (Exception e) {
            log.error("对话处理失败", e);
            return ChatResponse.error(sessionId, "处理失败，请稍后重试");
        }
    }

    /**
     * 根据意图路由到对应处理器
     */
    private ChatResponse routeToHandler(String sessionId, String message,
                                         IntentResult intent, ChatSession session) {
        String intentKey = intent.getIntent();

        return switch (intentKey) {
            // 数据驱动场景 — 自动收集数据 + AI分析
            case "daily_briefing" -> handleScenarioAnalysis(sessionId, "每日简报",
                    PromptTemplates.FRAMEWORK_DAILY_BRIEFING,
                    aiDashboardService.collectDailyStats(), session);
            case "quality_trend" -> handleScenarioAnalysis(sessionId, "质量趋势分析",
                    PromptTemplates.FRAMEWORK_QUALITY_TREND,
                    aiDashboardService.collectQualityStats(), session);
            case "production_efficiency" -> handleScenarioAnalysis(sessionId, "生产效率分析",
                    PromptTemplates.FRAMEWORK_PRODUCTION_EFFICIENCY,
                    aiDashboardService.collectProductionStats(), session);
            case "delivery_risk" -> handleScenarioAnalysis(sessionId, "交期风险预警",
                    PromptTemplates.FRAMEWORK_DELIVERY_RISK,
                    aiDashboardService.collectDeliveryRiskData(), session);
            case "purchase_suggestion" -> handleScenarioAnalysis(sessionId, "采购建议",
                    PromptTemplates.FRAMEWORK_PURCHASE_SUGGESTION,
                    aiDashboardService.collectInventoryStats(), session);
            case "cost_analysis" -> handleScenarioAnalysis(sessionId, "成本分析",
                    PromptTemplates.FRAMEWORK_COST_ANALYSIS,
                    aiDashboardService.collectCostData(), session);

            // 需要用户输入的场景 — 用户消息作为输入
            case "exception_analysis" -> handleScenarioAnalysis(sessionId, "异常分析",
                    PromptTemplates.FRAMEWORK_EXCEPTION_ANALYSIS,
                    aiDashboardService.collectExceptionContext() + "\n\n用户描述的异常:\n" + message, session);
            case "change_assessment" -> handleScenarioAnalysis(sessionId, "变更评估",
                    PromptTemplates.FRAMEWORK_CHANGE_ASSESSMENT,
                    "用户提交的变更信息:\n" + message, session);

            // 智能接单 / 产品建档 — 保留JSON输出用于表单填充
            case "smart_order" -> {
                session.setActiveModule("salesOrder");
                yield handleStructuredExtraction(sessionId, PromptTemplates.SMART_ORDER, message, session);
            }
            case "product_filing" -> {
                session.setActiveModule("product");
                yield handleStructuredExtraction(sessionId, PromptTemplates.AI_PRODUCT, message, session);
            }

            // 排产 — 特殊处理
            case "scheduling" -> handleScheduling(sessionId, message, session);

            // 智能查询（聚合/统计/计算）
            case "smart_query" -> handleSmartQuery(sessionId, message, session);

            // 数据查询
            case "query" -> handleQuery(sessionId, message, intent, session);

            // 数据录入
            case "input" -> handleInput(sessionId, message, intent, session);

            // 普通对话
            default -> handleChat(sessionId, message, session);
        };
    }

    /**
     * 处理场景分析 — 数据驱动 + AI Markdown输出
     */
    private ChatResponse handleScenarioAnalysis(String sessionId, String sceneName,
                                                 String framework, String factoryData, ChatSession session) {
        try {
            // 构建场景分析prompt
            String userPrompt = PromptTemplates.CONVERSATION_SCENARIO
                    .replace("{sceneName}", sceneName)
                    .replace("{analysisFramework}", framework)
                    .replace("{factoryData}", factoryData);

            // 构建system prompt（包含上下文）
            String systemPrompt = buildConversationSystemPrompt(session);

            // 调用AI（分析场景用 temperature=0.3）
            String result = deepSeekClient.chat(systemPrompt, userPrompt, 0.3);

            if (result == null || result.isBlank()) {
                return ChatResponse.error(sessionId, "AI服务暂时不可用，请稍后重试");
            }

            // 存储场景数据摘要（用于后续追问）
            Map<String, Object> scenarioData = new HashMap<>();
            scenarioData.put("sceneName", sceneName);
            scenarioData.put("factoryData", factoryData);
            scenarioData.put("analysisResult", truncate(result, 2000));
            session.updateScenarioContext("analysis", scenarioData);

            // 生成跟进建议
            List<String> suggestions = generateSuggestions(sceneName);

            ChatResponse response = ChatResponse.of(sessionId, "analysis", result);
            response.setSuggestions(suggestions);
            return response;

        } catch (Exception e) {
            log.error("场景分析[{}]失败", sceneName, e);
            return ChatResponse.error(sessionId, sceneName + "分析失败，请稍后重试");
        }
    }

    /**
     * 处理结构化数据提取（智能接单、产品建档）
     */
    private ChatResponse handleStructuredExtraction(String sessionId, String prompt,
                                                     String userInput, ChatSession session) {
        try {
            String result = deepSeekClient.chat(prompt, userInput, 0.1);
            if (result == null) {
                return ChatResponse.error(sessionId, "AI服务暂时不可用");
            }

            // 尝试解析JSON
            String json = deepSeekClient.cleanJsonResponse(result);
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<>() {});

            String module = session.getActiveModule();
            Map<String, String> columnLabels = buildColumnLabels(module);

            // 构建友好回复（使用中文字段名）
            String reply = "已为您解析出以下数据，请确认后可直接填入表单：\n\n" + formatDataAsMarkdown(parsed, columnLabels);

            ChatResponse response = ChatResponse.of(sessionId, "input", reply);
            ChatData data = ChatData.inputForm(module, List.of(parsed));
            if (!columnLabels.isEmpty()) data.setColumnLabels(columnLabels);
            response.setData(data);
            response.setSuggestions(List.of("确认并填入表单", "修改数据", "重新解析"));
            return response;

        } catch (Exception e) {
            log.warn("结构化提取失败，降级为文本分析: {}", e.getMessage());
            return handleChat(sessionId, userInput, session);
        }
    }

    /**
     * 处理排产请求
     */
    private ChatResponse handleScheduling(String sessionId, String message, ChatSession session) {
        try {
            // 收集待处理工单数据
            String workOrderData = aiDashboardService.collectProductionStats();

            String userPrompt = PromptTemplates.CONVERSATION_SCENARIO
                    .replace("{sceneName}", "AI排产建议")
                    .replace("{analysisFramework}",
                            "1. 📊 **工单概览** — 待排产工单列表\n" +
                            "2. 📋 **排产方案** — 建议的排产顺序和时间安排\n" +
                            "3. ⚠️ **风险提示** — 可能的瓶颈和风险\n" +
                            "4. 💡 **优化建议** — 排产优化建议")
                    .replace("{factoryData}", workOrderData + "\n\n用户需求: " + message);

            String systemPrompt = buildConversationSystemPrompt(session);
            String result = deepSeekClient.chat(systemPrompt, userPrompt, 0.3);

            if (result == null) {
                return ChatResponse.error(sessionId, "AI服务暂时不可用");
            }

            ChatResponse response = ChatResponse.of(sessionId, "analysis", result);
            response.setSuggestions(List.of("查看工单详情", "调整排产方案", "今日简报"));
            return response;

        } catch (Exception e) {
            log.error("排产处理失败", e);
            return ChatResponse.error(sessionId, "排产分析失败，请稍后重试");
        }
    }

    /**
     * 处理智能查询 — Text-to-SQL（聚合/统计/计算类问题）
     */
    private ChatResponse handleSmartQuery(String sessionId, String message, ChatSession session) {
        try {
            SmartQueryResult result = smartQueryExecutor.execute(message, session);

            if (!result.isSuccess()) {
                // 降级到普通查询
                log.info("Smart query failed ({}), falling back to handleQuery", result.getErrorMessage());
                IntentResult fallbackIntent = IntentResult.of("query");
                fallbackIntent.setExtractedInput(message);
                return handleQuery(sessionId, message, fallbackIntent, session);
            }

            // 构建回复
            ChatResponse response = ChatResponse.of(sessionId, "smart_query", result.getSummary());

            // 附加结构化数据（如果结果是列表形式）
            if (result.getResults() != null && !result.getResults().isEmpty()) {
                ChatData data = new ChatData();
                data.setType("smart_query_result");
                data.setModule("smart_query");
                data.setRecords(result.getResults());
                if (result.getColumnLabels() != null) {
                    data.setColumnLabels(result.getColumnLabels());
                }
                response.setData(data);
            }

            response.setSuggestions(List.of("查看更多详情", "按条件筛选", "导出数据"));

            // 存储查询上下文供追问
            Map<String, Object> ctx = new HashMap<>();
            ctx.put("sql", result.getSql());
            ctx.put("resultCount", result.getResults() != null ? result.getResults().size() : 0);
            ctx.put("question", message);
            session.updateScenarioContext("smart_query", ctx);

            return response;

        } catch (Exception e) {
            log.error("Smart query failed", e);
            return handleChat(sessionId, message, session);
        }
    }

    /**
     * 处理数据查询 — 优先走Text-to-SQL智能查询，失败降级到关键词查询
     */
    private ChatResponse handleQuery(String sessionId, String message,
                                      IntentResult intent, ChatSession session) {
        // 优先尝试智能查询（Text-to-SQL）
        try {
            SmartQueryResult smartResult = smartQueryExecutor.execute(message, session);
            if (smartResult.isSuccess()) {
                ChatResponse response = ChatResponse.of(sessionId, "smart_query", smartResult.getSummary());
                if (smartResult.getResults() != null && !smartResult.getResults().isEmpty()) {
                    ChatData data = new ChatData();
                    data.setType("smart_query_result");
                    data.setModule("smart_query");
                    data.setRecords(smartResult.getResults());
                    if (smartResult.getColumnLabels() != null) {
                        data.setColumnLabels(smartResult.getColumnLabels());
                    }
                    response.setData(data);
                }
                response.setSuggestions(List.of("查看更多详情", "按条件筛选", "导出数据"));

                Map<String, Object> ctx = new HashMap<>();
                ctx.put("sql", smartResult.getSql());
                ctx.put("resultCount", smartResult.getResults() != null ? smartResult.getResults().size() : 0);
                ctx.put("question", message);
                session.updateScenarioContext("smart_query", ctx);
                return response;
            }
            log.info("Smart query failed for handleQuery, falling back to keyword query: {}", smartResult.getErrorMessage());
        } catch (Exception e) {
            log.info("Smart query exception in handleQuery, falling back: {}", e.getMessage());
        }

        // 降级：旧的关键词查询
        try {
            String module = intent.getModule();
            if (module == null) {
                module = session.getActiveModule();
            }
            if (module == null) {
                module = inferModuleFromMessage(message);
            }

            if (module != null) {
                session.setActiveModule(module);

                Map<String, Object> queryParams = new HashMap<>();
                queryParams.put("keyword", extractKeyword(message));

                List<Map<String, Object>> results = queryExecutor.execute(module, queryParams);

                if (!results.isEmpty()) {
                    String reply = "为您查询到 **" + results.size() + "** 条记录：\n\n" +
                            formatQueryResultsAsMarkdown(results, 10);

                    ChatResponse response = ChatResponse.of(sessionId, "query", reply);
                    ChatData data = ChatData.queryResult(module, results);
                    response.setData(data);
                    response.setSuggestions(List.of("查看更多", "按条件筛选", "导出数据"));

                    Map<String, Object> scenarioData = new HashMap<>();
                    scenarioData.put("module", module);
                    scenarioData.put("resultCount", results.size());
                    scenarioData.put("results", results.subList(0, Math.min(5, results.size())));
                    session.updateScenarioContext("query", scenarioData);

                    return response;
                } else {
                    return ChatResponse.of(sessionId, "query", "未查询到匹配的记录，请尝试更换关键词。");
                }
            }

            return handleChat(sessionId, message, session);

        } catch (Exception e) {
            log.error("查询处理失败", e);
            return handleChat(sessionId, message, session);
        }
    }

    /**
     * 处理数据录入
     */
    private ChatResponse handleInput(String sessionId, String message,
                                      IntentResult intent, ChatSession session) {
        // 优先从消息内容推断模块，而不是依赖当前页面
        String module = intent.getModule();
        if (module == null) {
            module = inferModuleFromMessage(message);
        }
        if (module == null) {
            module = session.getActiveModule();
        }
        if (module == null) module = "通用";

        ChatRequest chatRequest = new ChatRequest();
        chatRequest.setSessionId(sessionId);
        chatRequest.setModule(module);
        chatRequest.setMessage(message);

        return chatLegacy(chatRequest, null);
    }

    /**
     * 处理普通对话 — 连续对话核心
     */
    private ChatResponse handleChat(String sessionId, String message, ChatSession session) {
        try {
            String systemPrompt = buildConversationSystemPrompt(session);

            // 构建对话历史
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt));

            // 限制最近15条历史消息
            List<Map<String, String>> history = session.getMessages();
            int start = Math.max(0, history.size() - 15);
            messages.addAll(history.subList(start, history.size()));

            // 添加当前消息
            messages.add(Map.of("role", "user", "content", message));

            // 对话用 temperature=0.5 更自然
            String result = deepSeekClient.chatMultiTurn(messages, 0.5);

            if (result == null || result.isBlank()) {
                return ChatResponse.error(sessionId, "AI服务暂时不可用，请稍后重试");
            }

            ChatResponse response = ChatResponse.of(sessionId, "chat", result);
            response.setSuggestions(List.of("今日简报", "查看交期风险", "质量趋势分析"));
            return response;

        } catch (Exception e) {
            log.error("对话处理失败", e);
            return ChatResponse.error(sessionId, "处理失败，请稍后重试");
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 构建对话系统prompt（包含上下文注入）
     */
    private String buildConversationSystemPrompt(ChatSession session) {
        String prompt = PromptTemplates.CONVERSATION_SYSTEM;

        // 注入时间
        prompt = prompt.replace("{currentTime}",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        // 注入页面
        String currentPage = session.getCurrentPage();
        prompt = prompt.replace("{currentPage}", currentPage != null ? currentPage : "AI助手页面");

        // 注入上次意图
        String lastIntent = session.getLastIntent();
        prompt = prompt.replace("{lastIntent}", lastIntent != null ? lastIntent : "无（首次对话）");

        // 注入上下文数据（上次场景结果摘要）
        StringBuilder contextData = new StringBuilder();
        Map<String, Object> lastData = session.getLastScenarioData();
        if (lastData != null) {
            contextData.append("## 上次分析的数据上下文\n");
            String sceneName = (String) lastData.get("sceneName");
            if (sceneName != null) {
                contextData.append("上次分析场景: ").append(sceneName).append("\n");
            }
            String analysisResult = (String) lastData.get("analysisResult");
            if (analysisResult != null) {
                contextData.append("分析结果摘要:\n").append(truncate(analysisResult, 800)).append("\n");
            }
        }
        prompt = prompt.replace("{contextData}", contextData.toString());

        return prompt;
    }

    /**
     * 根据场景生成跟进建议
     */
    private List<String> generateSuggestions(String sceneName) {
        return switch (sceneName) {
            case "每日简报" -> List.of("查看交期风险", "质量趋势分析", "哪些工单延期了？");
            case "质量趋势分析" -> List.of("不良品详情", "成本分析", "改善措施跟进");
            case "生产效率分析" -> List.of("查看瓶颈工序", "排产建议", "交期风险");
            case "交期风险预警" -> List.of("逾期工单详情", "赶工排产", "今日简报");
            case "采购建议" -> List.of("库存详情", "供应商信息", "成本分析");
            case "成本分析" -> List.of("不良品详情", "质量趋势", "降本方案");
            case "异常分析" -> List.of("处理进度", "类似异常历史", "预防措施");
            case "变更评估" -> List.of("调整排产", "采购建议", "通知相关人员");
            default -> List.of("今日简报", "质量趋势", "交期风险");
        };
    }

    /**
     * 将数据格式化为Markdown（使用中文字段标签）
     */
    private String formatDataAsMarkdown(Map<String, Object> data, Map<String, String> columnLabels) {
        StringBuilder sb = new StringBuilder();
        sb.append("| 字段 | 值 |\n|------|------|\n");
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (entry.getValue() != null) {
                String label = columnLabels.getOrDefault(entry.getKey(), entry.getKey());
                sb.append("| ").append(label).append(" | ").append(entry.getValue()).append(" |\n");
            }
        }
        return sb.toString();
    }

    /**
     * 从模块 schema 构建字段名→中文标签映射
     */
    private Map<String, String> buildColumnLabels(String module) {
        if (module == null) return Map.of();
        ModuleSchema schema = schemaRegistry.getSchema(module);
        if (schema == null) return Map.of();
        Map<String, String> labels = new LinkedHashMap<>();
        for (ModuleSchema.FieldDef f : schema.getFields()) {
            labels.put(f.getName(), f.getLabel());
        }
        return labels;
    }

    /**
     * 将查询结果格式化为Markdown表格
     */
    private String formatQueryResultsAsMarkdown(List<Map<String, Object>> results, int maxRows) {
        if (results.isEmpty()) return "无数据";

        // 过滤掉内部字段
        Set<String> excludeKeys = Set.of("id", "deleted", "tenantId", "tenant_id", "create_by", "update_by",
                "create_time", "update_time");

        // 取前几条的字段作为表头
        Map<String, Object> first = results.get(0);
        List<String> columns = first.keySet().stream()
                .filter(k -> !excludeKeys.contains(k))
                .limit(6)
                .toList();

        if (columns.isEmpty()) return "无可显示的字段";

        StringBuilder sb = new StringBuilder();
        // 表头
        sb.append("| ");
        columns.forEach(c -> sb.append(c).append(" | "));
        sb.append("\n|");
        columns.forEach(c -> sb.append("------|"));
        sb.append("\n");

        // 数据行
        int rows = Math.min(maxRows, results.size());
        for (int i = 0; i < rows; i++) {
            Map<String, Object> row = results.get(i);
            sb.append("| ");
            for (String col : columns) {
                Object val = row.get(col);
                sb.append(val != null ? truncate(val.toString(), 30) : "-").append(" | ");
            }
            sb.append("\n");
        }

        if (results.size() > maxRows) {
            sb.append("\n*... 还有 ").append(results.size() - maxRows).append(" 条记录*");
        }

        return sb.toString();
    }

    /**
     * 从消息中推断模块
     */
    private String inferModuleFromMessage(String message) {
        Map<String, String> moduleKeywords = Map.ofEntries(
                Map.entry("订单", "salesOrder"), Map.entry("客户", "customer"),
                Map.entry("供应商", "supplier"), Map.entry("物料", "material"),
                Map.entry("产品", "product"), Map.entry("工单", "workOrder"),
                Map.entry("库存", "inventory"), Map.entry("采购", "purchaseOrder"),
                Map.entry("外协", "outsourceOrder"), Map.entry("质检", "qcTask"),
                Map.entry("不良", "defect"), Map.entry("应收", "receivable"),
                Map.entry("应付", "payable"), Map.entry("团队", "team"),
                Map.entry("班组", "team"), Map.entry("员工", "user")
        );
        for (Map.Entry<String, String> entry : moduleKeywords.entrySet()) {
            if (message.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 从消息中提取搜索关键词（去掉意图关键词后的核心内容）
     */
    private String extractKeyword(String message) {
        String cleaned = message
                .replaceAll("(帮我|查一下|查询|搜索|有哪些|哪些|看看|找一下|多少)", "")
                .replaceAll("(的|了|吗|呢|吧|啊|呀)", "")
                .trim();
        return cleaned.isEmpty() ? message : cleaned;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    // ==================== 旧版兼容: chat 方法 ====================

    /**
     * 旧版聊天方法（保留兼容AiChatBot浮窗）
     */
    public ChatResponse chat(ChatRequest request, List<MultipartFile> files) {
        return chatLegacy(request, files);
    }

    private ChatResponse chatLegacy(ChatRequest request, List<MultipartFile> files) {
        Long userId = SecurityUtil.getCurrentUserId();
        ChatSession session = sessionManager.getOrCreate(
                request.getSessionId(), request.getModule(), userId);

        String sessionId = session.getSessionId();

        try {
            StringBuilder userContent = new StringBuilder();
            if (request.getMessage() != null && !request.getMessage().isBlank()) {
                userContent.append(request.getMessage());
            }
            if (files != null && !files.isEmpty()) {
                for (MultipartFile file : files) {
                    String extracted = filePreprocessor.extractText(file);
                    if (!extracted.isBlank()) {
                        userContent.append("\n\n[文件: ").append(file.getOriginalFilename()).append("]\n");
                        userContent.append(extracted);
                    }
                }
            }

            if (userContent.isEmpty()) {
                return ChatResponse.error(sessionId, "请输入消息或上传文件");
            }

            String module = session.getModule();
            String systemPrompt = buildLegacySystemPrompt(module);

            session.addMessage("user", userContent.toString());

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt));

            List<Map<String, String>> history = session.getMessages();
            int start = Math.max(0, history.size() - 20);
            messages.addAll(history.subList(start, history.size()));

            String aiResult = deepSeekClient.chatMultiTurn(messages, 0.1);
            if (aiResult == null || aiResult.isBlank()) {
                return ChatResponse.error(sessionId, "AI服务暂时不可用，请稍后重试");
            }

            ChatResponse response = parseLegacyAiResponse(sessionId, module, aiResult);
            session.addMessage("assistant", aiResult);

            if ("query".equals(response.getIntent()) && response.getData() != null
                    && response.getData().getRecords() != null && !response.getData().getRecords().isEmpty()) {
                executeLegacyQuery(module, response);
            }

            return response;

        } catch (Exception e) {
            log.error("AI聊天处理失败", e);
            return ChatResponse.error(sessionId, "处理失败: " + e.getMessage());
        }
    }

    private String buildLegacySystemPrompt(String module) {
        String prompt = PromptTemplates.CHAT_SYSTEM;
        String moduleName = "通用";
        ModuleSchema schema = schemaRegistry.getSchema(module);
        if (schema != null) moduleName = schema.getModuleName();
        prompt = prompt.replace("{moduleName}", moduleName);
        String schemaDesc = "";
        if (schema != null) schemaDesc = "当前模块的表单字段定义:\n" + schema.toPromptDescription();
        prompt = prompt.replace("{schemaDescription}", schemaDesc);
        prompt = prompt.replace("{relatedData}", "");
        return prompt;
    }

    private ChatResponse parseLegacyAiResponse(String sessionId, String module, String aiResult) {
        try {
            String json = deepSeekClient.cleanJsonResponse(aiResult);
            JsonNode root = objectMapper.readTree(json);
            ChatResponse response = new ChatResponse();
            response.setSessionId(sessionId);
            response.setIntent(root.path("intent").asText("chat"));
            response.setReply(root.path("reply").asText(""));

            JsonNode dataNode = root.path("data");
            if (!dataNode.isMissingNode() && !dataNode.isNull()) {
                ChatData data = new ChatData();
                data.setType(dataNode.path("type").asText(null));
                data.setReady(dataNode.path("ready").asBoolean(false));
                data.setModule(module);
                JsonNode recordsNode = dataNode.path("records");
                if (recordsNode.isArray()) {
                    List<Map<String, Object>> records = objectMapper.convertValue(
                            recordsNode, new TypeReference<>() {});
                    data.setRecords(records);
                }
                // 注入模块字段中文标签
                Map<String, String> labels = buildColumnLabels(module);
                if (!labels.isEmpty()) data.setColumnLabels(labels);
                response.setData(data);
            }

            JsonNode sugNode = root.path("suggestions");
            if (sugNode.isArray()) {
                List<String> suggestions = new ArrayList<>();
                sugNode.forEach(n -> suggestions.add(n.asText()));
                response.setSuggestions(suggestions);
            }
            return response;
        } catch (Exception e) {
            log.warn("AI响应JSON解析失败，作为纯文本处理: {}", e.getMessage());
            return ChatResponse.of(sessionId, "chat", aiResult);
        }
    }

    private void executeLegacyQuery(String module, ChatResponse response) {
        try {
            Map<String, Object> queryParams = response.getData().getRecords().get(0);
            List<Map<String, Object>> results = queryExecutor.execute(module, queryParams);
            if (!results.isEmpty()) {
                response.getData().setRecords(results);
                response.getData().setType("query_result");
                response.setReply(response.getReply() + "\n\n查询到 " + results.size() + " 条记录。");
            } else {
                response.setReply(response.getReply() + "\n\n未查询到匹配的记录。");
                response.getData().setRecords(List.of());
            }
        } catch (Exception e) {
            log.warn("查询执行失败: {}", e.getMessage());
        }
    }
}
