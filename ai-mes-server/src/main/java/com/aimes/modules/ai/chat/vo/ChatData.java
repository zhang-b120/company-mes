package com.aimes.modules.ai.chat.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * AI聊天结构化数据
 */
@Data
public class ChatData {

    /** 数据类型: input_form / query_result / analysis_report */
    private String type;

    /** 数据是否就绪可填入表单 */
    private boolean ready;

    /** 数据记录列表 */
    private List<Map<String, Object>> records;

    /** 模块标识 */
    private String module;

    /** 列名→中文标签映射（用于smart_query_result） */
    private Map<String, String> columnLabels;

    public static ChatData inputForm(String module, List<Map<String, Object>> records) {
        ChatData d = new ChatData();
        d.setType("input_form");
        d.setReady(true);
        d.setModule(module);
        d.setRecords(records);
        return d;
    }

    public static ChatData queryResult(String module, List<Map<String, Object>> records) {
        ChatData d = new ChatData();
        d.setType("query_result");
        d.setReady(false);
        d.setModule(module);
        d.setRecords(records);
        return d;
    }

    public static ChatData analysisReport(String module) {
        ChatData d = new ChatData();
        d.setType("analysis_report");
        d.setReady(false);
        d.setModule(module);
        return d;
    }
}
