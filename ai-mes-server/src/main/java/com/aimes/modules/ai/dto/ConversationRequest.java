package com.aimes.modules.ai.dto;

import lombok.Data;

/**
 * 统一对话请求DTO
 */
@Data
public class ConversationRequest {

    /** 会话ID，首次对话为null，后续对话传入 */
    private String sessionId;

    /** 用户消息文字 */
    private String message;

    /** 当前页面路由，如 "/production/work-order" */
    private String currentPage;
}
