package com.aimes.modules.ai.chat;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.*;

/**
 * AI聊天会话实体 — 支持连续对话上下文
 */
@Data
public class ChatSession {

    private String sessionId;
    private String module;
    private Long userId;
    private List<Map<String, String>> messages = new ArrayList<>();
    private Map<String, Object> lastParsedData;
    private LocalDateTime lastActiveTime;

    /** 用户当前页面路由 */
    private String currentPage;

    /** 上一次识别的意图 (如 daily_briefing / query / chat) */
    private String lastIntent;

    /** 上一次场景执行的结果数据摘要（用于连续追问） */
    private Map<String, Object> lastScenarioData;

    /** 推断的活跃业务模块 */
    private String activeModule;

    public ChatSession(String sessionId) {
        this.sessionId = sessionId;
        this.lastActiveTime = LocalDateTime.now();
    }

    public void addMessage(String role, String content) {
        Map<String, String> msg = new HashMap<>();
        msg.put("role", role);
        msg.put("content", content);
        messages.add(msg);
        this.lastActiveTime = LocalDateTime.now();
    }

    public void touch() {
        this.lastActiveTime = LocalDateTime.now();
    }

    public boolean isExpired(int timeoutMinutes) {
        return lastActiveTime.plusMinutes(timeoutMinutes).isBefore(LocalDateTime.now());
    }

    /**
     * 更新场景上下文（用于连续对话追问）
     */
    public void updateScenarioContext(String intent, Map<String, Object> scenarioData) {
        this.lastIntent = intent;
        this.lastScenarioData = scenarioData;
    }
}
