package com.aimes.modules.ai.chat;

import com.aimes.modules.ai.prompt.PromptTemplates;
import com.aimes.modules.ai.service.DeepSeekClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Text-to-SQL 智能查询执行器
 * <p>
 * 流程: 用户问题 → 选取表结构 → LLM生成SQL → 安全校验 → JdbcTemplate执行 → LLM总结结果
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmartQueryExecutor {

    private final JdbcTemplate jdbcTemplate;
    private final DeepSeekClient deepSeekClient;
    private final SqlSafetyChecker sqlSafetyChecker;
    private final SchemaContextBuilder schemaContextBuilder;
    private final ObjectMapper objectMapper;

    /**
     * 执行智能查询
     *
     * @param userQuestion 用户的自然语言问题
     * @param session      当前对话会话
     * @return 查询结果（包含SQL、数据、AI总结）
     */
    public SmartQueryResult execute(String userQuestion, ChatSession session) {
        try {
            // 1. 构建相关表结构上下文
            String schemaContext = schemaContextBuilder.buildForQuestion(userQuestion);

            // 2. 调用LLM生成SQL
            String userPrompt = schemaContext + "\n用户问题: " + userQuestion;
            String llmResponse = deepSeekClient.chatQuick(
                    PromptTemplates.SMART_QUERY_SYSTEM,
                    userPrompt,
                    0.0,
                    500
            );

            if (llmResponse == null || llmResponse.isBlank()) {
                return SmartQueryResult.failed("AI服务暂时不可用");
            }

            // 3. 解析LLM返回的JSON
            SqlParseResult parsed = parseSqlResponse(llmResponse);
            if (parsed == null || parsed.sql == null || parsed.sql.isBlank()) {
                log.warn("LLM未能生成有效SQL, response={}", llmResponse);
                return SmartQueryResult.failed("无法理解您的查询意图，请换个方式描述");
            }

            log.info("Smart Query SQL: {}", parsed.sql);

            // 4. 安全校验
            if (!sqlSafetyChecker.isSafe(parsed.sql)) {
                log.warn("Unsafe SQL rejected: {}", parsed.sql);
                return SmartQueryResult.failed("生成的查询包含不安全操作，已被拒绝");
            }

            // 5. 确保有LIMIT
            String safeSql = sqlSafetyChecker.ensureLimit(parsed.sql, 200);

            // 6. 执行查询
            List<Map<String, Object>> results;
            try {
                jdbcTemplate.setQueryTimeout(10);
                results = jdbcTemplate.queryForList(safeSql);
            } catch (Exception e) {
                log.error("Smart query execution failed: sql={}", safeSql, e);
                return SmartQueryResult.failed("查询执行失败，请尝试换个问法");
            }

            // 7. 调用LLM总结结果
            String summary = summarizeResults(userQuestion, parsed, results);

            return SmartQueryResult.success(parsed.sql, results, summary, parsed.columnLabels);

        } catch (Exception e) {
            log.error("SmartQueryExecutor failed", e);
            return SmartQueryResult.failed("智能查询处理失败: " + e.getMessage());
        }
    }

    /**
     * 解析LLM返回的SQL JSON
     */
    private SqlParseResult parseSqlResponse(String llmResponse) {
        try {
            String json = deepSeekClient.cleanJsonResponse(llmResponse);
            JsonNode root = objectMapper.readTree(json);

            SqlParseResult result = new SqlParseResult();
            result.sql = root.path("sql").asText(null);
            result.explanation = root.path("explanation").asText("");

            JsonNode labelsNode = root.path("columnLabels");
            if (labelsNode.isObject()) {
                result.columnLabels = objectMapper.convertValue(
                        labelsNode, new TypeReference<Map<String, String>>() {});
            } else {
                result.columnLabels = new LinkedHashMap<>();
            }

            return result;
        } catch (Exception e) {
            log.warn("Failed to parse SQL response: {}", e.getMessage());
            // 尝试直接提取SQL（兼容LLM不返回标准JSON的情况）
            String cleaned = llmResponse.trim();
            if (cleaned.toUpperCase().startsWith("SELECT")) {
                SqlParseResult result = new SqlParseResult();
                result.sql = cleaned;
                result.columnLabels = new LinkedHashMap<>();
                return result;
            }
            return null;
        }
    }

    /**
     * 调用LLM对查询结果做自然语言总结
     */
    private String summarizeResults(String question, SqlParseResult parsed,
                                     List<Map<String, Object>> results) {
        try {
            String dataStr = formatResultsForLlm(results, 50);
            String labelsStr = parsed.columnLabels.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining(", "));
            if (labelsStr.isBlank()) labelsStr = "（同列名）";

            String prompt = PromptTemplates.SMART_QUERY_SUMMARIZE
                    .replace("{question}", question)
                    .replace("{sql}", parsed.sql)
                    .replace("{rowCount}", String.valueOf(results.size()))
                    .replace("{data}", dataStr)
                    .replace("{columnLabels}", labelsStr);

            String summary = deepSeekClient.chat(
                    "你是数据分析助手，用简洁的中文回答用户的数据查询问题。使用Markdown格式，重要数字加粗。",
                    prompt,
                    0.3
            );

            return summary != null ? summary : "查询到 " + results.size() + " 条数据。";
        } catch (Exception e) {
            log.warn("Failed to summarize results: {}", e.getMessage());
            // 降级：直接拼接简单描述
            return buildFallbackSummary(results);
        }
    }

    /**
     * 将查询结果格式化为紧凑文本给LLM
     */
    private String formatResultsForLlm(List<Map<String, Object>> results, int maxRows) {
        if (results.isEmpty()) return "（空结果集）";

        StringBuilder sb = new StringBuilder();
        int rows = Math.min(maxRows, results.size());

        // 取列名
        List<String> columns = new ArrayList<>(results.get(0).keySet());

        // 表头
        sb.append(String.join(" | ", columns)).append("\n");

        // 数据行
        for (int i = 0; i < rows; i++) {
            Map<String, Object> row = results.get(i);
            List<String> values = columns.stream()
                    .map(col -> {
                        Object val = row.get(col);
                        if (val == null) return "null";
                        String s = val.toString();
                        return s.length() > 50 ? s.substring(0, 50) + "..." : s;
                    })
                    .collect(Collectors.toList());
            sb.append(String.join(" | ", values)).append("\n");
        }

        if (results.size() > maxRows) {
            sb.append("... 还有 ").append(results.size() - maxRows).append(" 行");
        }

        return sb.toString();
    }

    /**
     * 降级总结（LLM不可用时）
     */
    private String buildFallbackSummary(List<Map<String, Object>> results) {
        if (results.isEmpty()) return "暂无相关数据。";
        if (results.size() == 1 && results.get(0).size() <= 3) {
            // 单行少列，直接列出
            StringBuilder sb = new StringBuilder();
            results.get(0).forEach((k, v) ->
                    sb.append("**").append(k).append("**: ").append(v).append("\n"));
            return sb.toString();
        }
        return "查询到 **" + results.size() + "** 条记录。";
    }

    /** 内部解析结果 */
    private static class SqlParseResult {
        String sql;
        String explanation;
        Map<String, String> columnLabels;
    }
}
