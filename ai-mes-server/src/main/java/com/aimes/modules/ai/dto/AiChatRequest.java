package com.aimes.modules.ai.dto;

import lombok.Data;

import java.util.Map;

/**
 * AI通用聊天请求DTO
 * 用于统一接收各种AI场景的请求参数
 */
@Data
public class AiChatRequest {

    /**
     * AI场景类型：
     * smart_order - 智能接单
     * ai_product - AI产品建档
     * ai_scheduling - AI排产建议
     * daily_report - 每日简报
     * change_assessment - 变更影响评估
     * purchase_suggestion - 采购建议
     * exception_analysis - 异常分析
     */
    private String scenario;

    /** 用户输入内容 */
    private String content;

    /** 额外参数（根据不同场景传入不同的业务数据） */
    private Map<String, Object> params;
}
