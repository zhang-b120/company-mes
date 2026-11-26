package com.aimes.modules.ai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class AiService {

    private final DeepSeekClient deepSeekClient;

    /**
     * 智能接单：解析订单文本为结构化数据
     */
    public String smartOrderIntake(String orderText) {
        String systemPrompt = "你是一个专业的MES系统订单解析助手。请将用户提供的订单文本解析为结构化JSON数据，" +
                "包含以下字段：customerName（客户名称）、items（明细列表，每项包含productName产品名称、qty数量、deliveryDate交货日期）。" +
                "请严格按照JSON格式返回，不要添加额外说明。";
        return deepSeekClient.chat(systemPrompt, orderText);
    }

    /**
     * AI产品建档：根据描述生成产品信息
     */
    public String aiProductFiling(String description) {
        String systemPrompt = "你是一个专业的MES系统产品建档助手。请根据用户提供的产品描述，生成结构化的产品信息JSON，" +
                "包含以下字段：name（产品名称）、spec（规格型号）、category（分类）、unit（单位）、" +
                "productionMode（生产模式：make_to_order按单生产/make_to_stock备库生产）、remark（备注）。" +
                "请严格按照JSON格式返回，不要添加额外说明。";
        return deepSeekClient.chat(systemPrompt, description);
    }

    /**
     * AI排产建议：生成排产方案
     */
    public String aiScheduling(String workOrderInfo) {
        String systemPrompt = "你是一个专业的MES系统生产排产助手。请根据用户提供的工单信息，生成排产建议JSON，" +
                "包含以下字段：suggestions（建议列表，每项包含assignee分配人员/班组、startTime建议开始时间、" +
                "endTime建议结束时间、reason排产理由）。" +
                "请综合考虑产能、交期、优先级等因素，给出合理的排产建议。请严格按照JSON格式返回。";
        return deepSeekClient.chat(systemPrompt, workOrderInfo);
    }

    /**
     * 每日简报：生成工厂日报摘要
     */
    public String dailyBriefing(String factoryData) {
        String systemPrompt = "你是一个专业的MES系统每日简报生成助手。请根据用户提供的工厂当日数据，生成一份简洁明了的每日简报。" +
                "简报应包含：生产概况、订单情况、库存预警、质量情况、待办事项等关键信息。" +
                "请用简洁的中文描述，条理清晰，重点突出异常和需要关注的事项。";
        return deepSeekClient.chat(systemPrompt, factoryData);
    }

    /**
     * 变更影响评估：评估订单变更的影响
     */
    public String changeAssessment(String changeInfo) {
        String systemPrompt = "你是一个专业的MES系统订单变更影响评估助手。请根据用户提供的变更信息，评估变更对以下方面的影响：" +
                "1. 生产计划影响（是否需要调整排产）" +
                "2. 物料影响（是否需要追加采购或退料）" +
                "3. 交期影响（是否影响交货时间）" +
                "4. 成本影响（预估成本变化）" +
                "5. 建议处理方案。" +
                "请以结构化的方式返回评估结果。";
        return deepSeekClient.chat(systemPrompt, changeInfo);
    }

    /**
     * 采购建议：根据库存数据生成采购计划
     */
    public String purchaseSuggestion(String inventoryData) {
        String systemPrompt = "你是一个专业的MES系统采购建议助手。请根据用户提供的库存和需求数据，生成采购建议JSON，" +
                "包含以下字段：suggestions（建议列表，每项包含materialName物料名称、currentStock当前库存、" +
                "suggestedQty建议采购数量、urgency紧急程度high/medium/low、reason采购原因）。" +
                "请综合考虑安全库存、在途订单、生产需求等因素。请严格按照JSON格式返回。";
        return deepSeekClient.chat(systemPrompt, inventoryData);
    }

    /**
     * 异常分析：分析异常原因并给出解决建议
     */
    public String exceptionAnalysis(String exceptionInfo) {
        String systemPrompt = "你是一个专业的MES系统异常分析助手。请根据用户提供的异常信息，进行以下分析：" +
                "1. 异常原因分析（可能的根本原因）" +
                "2. 影响范围评估" +
                "3. 紧急处理建议" +
                "4. 长期预防措施。" +
                "请以结构化的方式返回分析结果，重点给出可操作的建议。";
        return deepSeekClient.chat(systemPrompt, exceptionInfo);
    }

    /**
     * 解析订单文本为结构化数据（供AI接单功能使用）
     */
    public Map<String, Object> parseOrder(String content) {
        String systemPrompt = "你是一个专业的MES系统订单解析助手。请将用户提供的订单文本解析为结构化JSON数据。" +
                "必须包含以下字段：customerName（客户名称），confidence（解析置信度0-1），" +
                "items（明细数组，每项包含productName产品名称、spec规格、quantity数量、unitPrice单价、deliveryDate交货日期）。" +
                "请严格按照JSON格式返回，不要添加额外说明。";
        String aiResult = deepSeekClient.chat(systemPrompt, content);

        // 尝试解析AI返回的JSON，失败则返回默认结构
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            // 清理可能的markdown包裹
            String json = aiResult;
            if (json.contains("```json")) {
                json = json.substring(json.indexOf("```json") + 7);
                json = json.substring(0, json.indexOf("```"));
            } else if (json.contains("```")) {
                json = json.substring(json.indexOf("```") + 3);
                json = json.substring(0, json.indexOf("```"));
            }
            result = mapper.readValue(json.trim(), Map.class);
        } catch (Exception e) {
            result.put("customerName", "未识别");
            result.put("confidence", 0.3);
            result.put("items", List.of());
            result.put("rawText", aiResult);
        }
        return result;
    }
}
