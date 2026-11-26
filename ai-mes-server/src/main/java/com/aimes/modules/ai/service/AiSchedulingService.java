package com.aimes.modules.ai.service;

import com.aimes.modules.ai.dto.AiSchedulingRequest;
import com.aimes.modules.ai.prompt.PromptTemplates;
import com.aimes.modules.ai.vo.AiSchedulingVO;
import com.aimes.modules.production.entity.BizWorkOrder;
import com.aimes.modules.production.mapper.BizSchedulingMapper;
import com.aimes.modules.production.mapper.BizWorkOrderMapper;
import com.aimes.modules.team.mapper.BizTeamMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * AI排产场景服务
 * 提供AI辅助排产建议功能，根据工单信息和产能数据生成最优排产方案
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiSchedulingService {

    private final DeepSeekClient deepSeekClient;
    private final BizWorkOrderMapper workOrderMapper;
    private final BizSchedulingMapper schedulingMapper;
    private final BizTeamMapper teamMapper;
    private final ObjectMapper objectMapper;

    /**
     * AI排产建议：根据工单列表和时间范围生成排产方案
     * 综合考虑产能、交期、优先级等因素，给出合理的排产建议
     *
     * @param request 排产请求（包含工单ID列表和时间范围）
     * @return AI生成的排产建议
     */
    public AiSchedulingVO aiScheduling(AiSchedulingRequest request) {
        // 查询工单详情，组装AI输入数据
        List<BizWorkOrder> workOrders = request.getWorkOrderIds().stream()
                .map(workOrderMapper::selectById)
                .filter(wo -> wo != null)
                .collect(Collectors.toList());

        // 构建工单信息摘要供AI分析
        StringBuilder workOrderInfo = new StringBuilder();
        workOrderInfo.append("排产时间范围: ").append(request.getStartDate())
                .append(" 至 ").append(request.getEndDate()).append("\n");
        workOrderInfo.append("待排产工单:\n");

        for (BizWorkOrder wo : workOrders) {
            workOrderInfo.append(String.format("- 工单号: %s, 产品ID: %d, 计划数量: %s, 交货日期: %s, 优先级: %d, 状态: %s\n",
                    wo.getWoNo(), wo.getProductId(), wo.getPlanQty(),
                    wo.getDeliveryDate(), wo.getPriority(), wo.getStatus()));
        }

        String result = deepSeekClient.chat(PromptTemplates.AI_SCHEDULING, workOrderInfo.toString());
        try {
            return objectMapper.readValue(result, AiSchedulingVO.class);
        } catch (Exception e) {
            log.warn("AI排产结果解析失败: {}", e.getMessage());
            AiSchedulingVO vo = new AiSchedulingVO();
            vo.setSummary(result);
            return vo;
        }
    }
}
