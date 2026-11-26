package com.aimes.modules.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class DeepSeekClient {

    @Value("${ai.deepseek.api-key:}")
    private String apiKey;

    @Value("${ai.deepseek.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${ai.deepseek.model:deepseek-chat}")
    private String model;

    @Value("${ai.deepseek.timeout:60000}")
    private int timeout;

    @Value("${ai.deepseek.max-tokens:4096}")
    private int maxTokens;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile boolean available = true;

    @jakarta.annotation.PostConstruct
    public void validate() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("AI功能未启用: DEEPSEEK_API_KEY 未配置");
            available = false;
        } else {
            log.info("DeepSeek AI 已配置, model={}", model);
        }
    }

    public boolean isAvailable() {
        return available;
    }

    private OkHttpClient getClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(timeout, TimeUnit.MILLISECONDS)
                .readTimeout(timeout, TimeUnit.MILLISECONDS)
                .writeTimeout(timeout, TimeUnit.MILLISECONDS)
                .build();
    }

    /**
     * 单轮对话（默认 temperature=0.1）
     */
    public String chat(String systemPrompt, String userPrompt) {
        return chat(systemPrompt, userPrompt, 0.1);
    }

    /**
     * 单轮对话（可指定 temperature）
     */
    public String chat(String systemPrompt, String userPrompt, double temperature) {
        return callApi(List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ), temperature, maxTokens, true);
    }

    /**
     * 快速轻量调用 — 用于意图分类等场景（低 maxTokens）
     */
    public String chatQuick(String systemPrompt, String userPrompt, double temperature, int quickMaxTokens) {
        return callApi(List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ), temperature, quickMaxTokens, false);
    }

    /**
     * 多轮对话调用 — 支持完整的messages历史
     */
    public String chatMultiTurn(List<Map<String, String>> messages, double temperature) {
        return callApi(messages, temperature, maxTokens, true);
    }

    /**
     * 统一API调用，支持重试
     */
    private String callApi(List<Map<String, String>> messages, double temperature, int tokens, boolean retry) {
        if (!available) {
            log.warn("DeepSeek API不可用，未配置API Key");
            return null;
        }

        for (int attempt = 0; attempt <= (retry ? 1 : 0); attempt++) {
            try {
                Map<String, Object> body = new java.util.HashMap<>();
                body.put("model", model);
                body.put("messages", messages);
                body.put("temperature", temperature);
                body.put("max_tokens", tokens);

                RequestBody requestBody = RequestBody.create(
                        objectMapper.writeValueAsString(body),
                        MediaType.parse("application/json")
                );

                Request request = new Request.Builder()
                        .url(baseUrl + "/v1/chat/completions")
                        .addHeader("Authorization", "Bearer " + apiKey)
                        .addHeader("Content-Type", "application/json")
                        .post(requestBody)
                        .build();

                try (Response response = getClient().newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        String errorBody = response.body() != null ? response.body().string() : "no body";
                        log.error("DeepSeek API error {}: {}", response.code(), errorBody);
                        if (attempt == 0 && retry && response.code() >= 500) {
                            log.info("DeepSeek API 5xx 错误，1秒后重试...");
                            Thread.sleep(1000);
                            continue;
                        }
                        return null;
                    }
                    String responseBody = response.body().string();
                    JsonNode root = objectMapper.readTree(responseBody);
                    return root.path("choices").path(0).path("message").path("content").asText();
                }
            } catch (Exception e) {
                log.error("DeepSeek API call failed (attempt {})", attempt, e);
                if (attempt == 0 && retry) {
                    try { Thread.sleep(1000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                    continue;
                }
                return null;
            }
        }
        return null;
    }

    /**
     * 清理AI返回中的markdown JSON包裹
     */
    public String cleanJsonResponse(String aiResult) {
        if (aiResult == null) return null;
        String json = aiResult.trim();
        if (json.contains("```json")) {
            json = json.substring(json.indexOf("```json") + 7);
            json = json.substring(0, json.indexOf("```"));
        } else if (json.contains("```")) {
            json = json.substring(json.indexOf("```") + 3);
            if (json.contains("```")) {
                json = json.substring(0, json.indexOf("```"));
            }
        }
        return json.trim();
    }

    public String chatWithImage(String systemPrompt, String userPrompt, String base64Image) {
        // For now, use text-only mode; image OCR preprocessing would happen before this call
        return chat(systemPrompt, userPrompt);
    }
}
