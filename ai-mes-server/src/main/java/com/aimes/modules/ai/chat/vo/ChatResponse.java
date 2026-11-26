package com.aimes.modules.ai.chat.vo;

import lombok.Data;

import java.util.List;

/**
 * AI聊天响应VO
 */
@Data
public class ChatResponse {

    /** 会话ID */
    private String sessionId;

    /** AI判定的意图: input/query/analysis/chat */
    private String intent;

    /** AI文字回复（聊天框展示） */
    private String reply;

    /** 结构化数据（可空） */
    private ChatData data;

    /** 建议操作按钮列表 */
    private List<String> suggestions;

    public static ChatResponse of(String sessionId, String intent, String reply) {
        ChatResponse resp = new ChatResponse();
        resp.setSessionId(sessionId);
        resp.setIntent(intent);
        resp.setReply(reply);
        return resp;
    }

    public static ChatResponse error(String sessionId, String message) {
        ChatResponse resp = new ChatResponse();
        resp.setSessionId(sessionId);
        resp.setIntent("chat");
        resp.setReply(message);
        return resp;
    }
}
