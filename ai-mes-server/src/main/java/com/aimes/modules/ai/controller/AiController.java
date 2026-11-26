package com.aimes.modules.ai.controller;

import com.aimes.common.result.R;
import com.aimes.modules.ai.chat.AiChatService;
import com.aimes.modules.ai.chat.ChatSessionManager;
import com.aimes.modules.ai.chat.dto.ChatRequest;
import com.aimes.modules.ai.chat.vo.ChatResponse;
import com.aimes.modules.ai.dto.AiSchedulingRequest;
import com.aimes.modules.ai.dto.ConversationRequest;
import com.aimes.modules.ai.service.*;
import com.aimes.modules.ai.vo.AiParseOrderVO;
import com.aimes.modules.ai.vo.AiReportVO;
import com.aimes.modules.ai.vo.AiSchedulingVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * AI智能功能控制器
 *
 * 接口总览（12个AI场景 + 4个聊天接口）:
 *
 * S01 智能接单:   POST /api/ai/smart-order-intake, POST /api/ai/parse-order
 * S02 AI建档:     POST /api/ai/product-filing
 * S03 AI排产:     POST /api/ai/scheduling, POST /api/ai/scheduling/smart
 * S04 每日简报:   POST /api/ai/daily-briefing  (数据驱动，自动拉取)
 * S05 变更评估:   POST /api/ai/change-assessment
 * S06 采购建议:   POST /api/ai/purchase-suggestion (数据驱动，自动拉取)
 * S07 异常分析:   POST /api/ai/exception-analysis
 * S08 质量趋势:   POST /api/ai/quality-trend (数据驱动，自动拉取)
 * S09 生产效率:   POST /api/ai/production-efficiency (数据驱动)
 * S10 交期风险:   POST /api/ai/delivery-risk (数据驱动)
 * S11 成本分析:   POST /api/ai/cost-analysis (数据驱动)
 * S12 AI对话:     POST /api/ai/chat, POST /api/ai/chat/text
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;
    private final AiOrderService aiOrderService;
    private final AiProductService aiProductService;
    private final AiSchedulingService aiSchedulingService;
    private final AiDashboardService aiDashboardService;
    private final AiChatService aiChatService;
    private final ChatSessionManager chatSessionManager;
    private final FilePreprocessor filePreprocessor;
    private final ObjectMapper objectMapper;

    // ==================== 统一对话接口（新版） ====================

    @PostMapping("/conversation")
    public R<ChatResponse> conversation(@RequestBody ConversationRequest request) {
        ChatResponse response = aiChatService.conversation(request);
        return R.ok(response);
    }

    // ==================== S12: 聊天机器人接口（旧版兼容） ====================

    @PostMapping("/chat")
    public R<ChatResponse> chat(
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @RequestPart("request") String requestJson) {
        try {
            ChatRequest request = objectMapper.readValue(requestJson, ChatRequest.class);
            ChatResponse response = aiChatService.chat(request, files);
            return R.ok(response);
        } catch (Exception e) {
            return R.fail("聊天请求处理失败: " + e.getMessage());
        }
    }

    @PostMapping("/chat/text")
    public R<ChatResponse> chatText(@RequestBody ChatRequest request) {
        ChatResponse response = aiChatService.chat(request, null);
        return R.ok(response);
    }

    @GetMapping("/chat/sessions/{sessionId}")
    public R<List<Map<String, String>>> getChatHistory(@PathVariable String sessionId) {
        return R.ok(chatSessionManager.getMessages(sessionId));
    }

    @DeleteMapping("/chat/sessions/{sessionId}")
    public R<Void> clearSession(@PathVariable String sessionId) {
        chatSessionManager.remove(sessionId);
        return R.ok();
    }

    // ==================== S01: 智能接单 ====================

    @PostMapping("/smart-order-intake")
    public R<String> smartOrderIntake(@RequestBody Map<String, String> body) {
        return R.ok(aiService.smartOrderIntake(body.get("orderText")));
    }

    @PostMapping("/parse-order")
    public R<AiParseOrderVO> parseOrder(
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @RequestPart(value = "content", required = false) String content) {
        StringBuilder sb = new StringBuilder();
        log.info("[parse-order] 收到请求 content={}, files={}",
                content != null ? content.length() + "字符" : "null",
                files != null ? files.size() + "个" : "null");
        if (content != null && !content.isBlank()) {
            sb.append(content);
        }
        if (files != null) {
            for (MultipartFile file : files) {
                log.info("[parse-order] 处理文件: name={}, size={}字节, contentType={}",
                        file.getOriginalFilename(), file.getSize(), file.getContentType());
                String extracted = filePreprocessor.extractText(file);
                log.info("[parse-order] 文件 {} 提取出 {} 字符, 预览: {}",
                        file.getOriginalFilename(), extracted.length(),
                        extracted.length() > 500 ? extracted.substring(0, 500) + "..." : extracted);
                if (!extracted.isBlank()) {
                    sb.append("\n\n[文件: ").append(file.getOriginalFilename()).append("]\n").append(extracted);
                }
            }
        }
        String fullPrompt = sb.toString();
        log.info("[parse-order] 最终 AI 输入长度: {} 字符", fullPrompt.length());
        if (fullPrompt.length() <= 2000) {
            log.info("[parse-order] AI 输入完整内容:\n{}", fullPrompt);
        } else {
            log.info("[parse-order] AI 输入前 2000 字:\n{}", fullPrompt.substring(0, 2000));
        }
        AiParseOrderVO vo = aiOrderService.smartOrderIntake(fullPrompt);
        log.info("[parse-order] AI 解析结果: {}", vo);
        return R.ok(vo);
    }

    // ==================== S02: AI建档 ====================

    @PostMapping("/product-filing")
    public R<String> productFiling(@RequestBody Map<String, String> body) {
        return R.ok(aiProductService.aiProductFiling(body.get("description")));
    }

    // ==================== S03: AI排产 ====================

    @PostMapping("/scheduling")
    public R<String> scheduling(@RequestBody Map<String, String> body) {
        return R.ok(aiService.aiScheduling(body.get("workOrderInfo")));
    }

    @PostMapping("/scheduling/smart")
    public R<AiSchedulingVO> schedulingSmart(@RequestBody AiSchedulingRequest request) {
        return R.ok(aiSchedulingService.aiScheduling(request));
    }

    // ==================== S04: 每日简报（数据驱动） ====================

    @PostMapping("/daily-briefing")
    public R<AiReportVO> dailyBriefing() {
        return R.ok(aiDashboardService.dailyBriefing());
    }

    // ==================== S05: 变更评估 ====================

    @PostMapping("/change-assessment")
    public R<String> changeAssessment(@RequestBody Map<String, String> body) {
        return R.ok(aiOrderService.changeAssessment(body.get("changeInfo")));
    }

    // ==================== S06: 采购建议（数据驱动） ====================

    @PostMapping("/purchase-suggestion")
    public R<String> purchaseSuggestion() {
        return R.ok(aiDashboardService.purchaseSuggestion());
    }

    // ==================== S07: 异常分析 ====================

    @PostMapping("/exception-analysis")
    public R<AiReportVO> exceptionAnalysis(@RequestBody Map<String, String> body) {
        return R.ok(aiDashboardService.exceptionAnalysis(body.get("exceptionInfo")));
    }

    // ==================== S08: 质量趋势分析（新增，数据驱动） ====================

    @PostMapping("/quality-trend")
    public R<AiReportVO> qualityTrend() {
        return R.ok(aiDashboardService.qualityTrendAnalysis());
    }

    // ==================== S09: 生产效率分析（新增，数据驱动） ====================

    @PostMapping("/production-efficiency")
    public R<AiReportVO> productionEfficiency() {
        return R.ok(aiDashboardService.productionEfficiencyAnalysis());
    }

    // ==================== S10: 交期风险预警（新增，数据驱动） ====================

    @PostMapping("/delivery-risk")
    public R<AiReportVO> deliveryRisk() {
        return R.ok(aiDashboardService.deliveryRiskAnalysis());
    }

    // ==================== S11: 成本分析（新增，数据驱动） ====================

    @PostMapping("/cost-analysis")
    public R<AiReportVO> costAnalysis() {
        return R.ok(aiDashboardService.costAnalysis());
    }
}
