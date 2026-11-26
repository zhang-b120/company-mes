package com.aimes.modules.ai.chat;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Text-to-SQL 智能查询结果
 */
@Data
public class SmartQueryResult {

    private boolean success;
    private String sql;
    private List<Map<String, Object>> results;
    private String summary;
    private Map<String, String> columnLabels;
    private String errorMessage;

    public static SmartQueryResult success(String sql, List<Map<String, Object>> results,
                                           String summary, Map<String, String> columnLabels) {
        SmartQueryResult r = new SmartQueryResult();
        r.setSuccess(true);
        r.setSql(sql);
        r.setResults(results);
        r.setSummary(summary);
        r.setColumnLabels(columnLabels);
        return r;
    }

    public static SmartQueryResult failed(String errorMessage) {
        SmartQueryResult r = new SmartQueryResult();
        r.setSuccess(false);
        r.setErrorMessage(errorMessage);
        return r;
    }
}
