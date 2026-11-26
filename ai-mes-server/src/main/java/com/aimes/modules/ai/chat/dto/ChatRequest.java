package com.aimes.modules.ai.chat.dto;

import lombok.Data;

import java.util.Map;

/**
 * AI聊天请求DTO
 */
@Data
public class ChatRequest {

    /** 会话ID，首次为空则自动创建 */
    private String sessionId;

    /** 当前页面模块标识 (customer/salesOrder/...) */
    private String module;

    /** 用户消息文字 */
    private String message;

    /** 附加上下文（如当前选中的记录ID等） */
    private Map<String, Object> context;
}
