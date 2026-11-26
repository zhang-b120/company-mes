package com.aimes.modules.ai.chat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 聊天会话管理器 — 内存存储，30分钟超时自动清理
 */
@Slf4j
@Component
public class ChatSessionManager {

    private static final int SESSION_TIMEOUT_MINUTES = 30;
    private final ConcurrentHashMap<String, ChatSession> sessions = new ConcurrentHashMap<>();

    /**
     * 获取或创建会话
     */
    public ChatSession getOrCreate(String sessionId, String module, Long userId) {
        if (sessionId != null && sessions.containsKey(sessionId)) {
            ChatSession session = sessions.get(sessionId);
            session.touch();
            // 如果切换了模块，更新module
            if (module != null && !module.equals(session.getModule())) {
                session.setModule(module);
            }
            return session;
        }
        // 新建会话
        String newId = (sessionId != null && !sessionId.isBlank()) ? sessionId : UUID.randomUUID().toString();
        ChatSession session = new ChatSession(newId);
        session.setModule(module);
        session.setUserId(userId);
        sessions.put(newId, session);
        return session;
    }

    /**
     * 获取会话（不存在返回null）
     */
    public ChatSession get(String sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * 获取会话消息历史
     */
    public List<Map<String, String>> getMessages(String sessionId) {
        ChatSession session = sessions.get(sessionId);
        return session != null ? session.getMessages() : List.of();
    }

    /**
     * 删除会话
     */
    public void remove(String sessionId) {
        sessions.remove(sessionId);
    }

    /**
     * 定时清理过期会话（每5分钟执行）
     */
    @Scheduled(fixedRate = 300000)
    public void cleanup() {
        int before = sessions.size();
        sessions.entrySet().removeIf(e -> e.getValue().isExpired(SESSION_TIMEOUT_MINUTES));
        int removed = before - sessions.size();
        if (removed > 0) {
            log.info("清理过期AI聊天会话: {}个", removed);
        }
    }
}
