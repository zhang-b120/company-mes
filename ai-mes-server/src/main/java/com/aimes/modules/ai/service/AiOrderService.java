package com.aimes.modules.ai.service;

import com.aimes.modules.ai.prompt.PromptTemplates;
import com.aimes.modules.ai.vo.AiParseOrderVO;
import com.aimes.modules.master.mapper.BizCustomerMapper;
import com.aimes.modules.master.mapper.BizProductMapper;
import com.aimes.modules.order.mapper.BizOrderChangeMapper;
import com.aimes.modules.order.mapper.BizSalesOrderMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * AI订单场景服务
 * 提供智能接单解析和订单变更影响评估功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiOrderService {

    private final DeepSeekClient deepSeekClient;
    private final BizCustomerMapper customerMapper;
    private final BizProductMapper productMapper;
    private final BizSalesOrderMapper salesOrderMapper;
    private final BizOrderChangeMapper orderChangeMapper;
    private final ObjectMapper objectMapper;

    /**
     * 智能接单：解析订单文本为结构化数据
     * 调用AI解析用户输入的订单文本，返回结构化的订单信息
     *
     * @param orderText 用户输入的订单文本
     * @return AI解析后的结构化订单数据
     */
    public AiParseOrderVO smartOrderIntake(String orderText) {
        log.info("[smartOrderIntake] 调用 AI, 输入长度: {} 字符", orderText != null ? orderText.length() : 0);
        String result = deepSeekClient.chat(PromptTemplates.SMART_ORDER, orderText);
        log.info("[smartOrderIntake] AI 原始返回: {}", result);
        try {
            AiParseOrderVO vo = objectMapper.readValue(result, AiParseOrderVO.class);
            // 向后兼容: 若 AI 仅返回了旧字段(customer + items), 把它包装成单元素 orders
            if ((vo.getOrders() == null || vo.getOrders().isEmpty())
                    && (vo.getCustomer() != null || (vo.getItems() != null && !vo.getItems().isEmpty()))) {
                AiParseOrderVO.OrderGroup g = new AiParseOrderVO.OrderGroup();
                g.setCustomer(vo.getCustomer());
                g.setItems(vo.getItems());
                g.setTotalAmount(vo.getTotalAmount());
                vo.setOrders(java.util.Collections.singletonList(g));
            }
            // 反向: 把 orders[0] 同步回旧字段, 让老前端也能读到
            if (vo.getOrders() != null && !vo.getOrders().isEmpty() && vo.getCustomer() == null) {
                AiParseOrderVO.OrderGroup first = vo.getOrders().get(0);
                vo.setCustomer(first.getCustomer());
                vo.setItems(first.getItems());
                vo.setTotalAmount(first.getTotalAmount());
            }
            return vo;
        } catch (Exception e) {
            log.warn("智能接单结果解析失败，返回原始文本包装: {}", e.getMessage());
            return new AiParseOrderVO();
        }
    }

    /**
     * 变更影响评估：评估订单变更对生产计划、物料、交期、成本的影响
     *
     * @param changeInfo 变更信息描述
     * @return AI生成的变更影响评估报告（JSON字符串）
     */
    public String changeAssessment(String changeInfo) {
        return deepSeekClient.chat(PromptTemplates.CHANGE_ASSESSMENT, changeInfo);
    }
}
