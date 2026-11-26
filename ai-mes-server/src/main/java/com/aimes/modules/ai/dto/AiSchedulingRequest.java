package com.aimes.modules.ai.dto;

import lombok.Data;

import java.util.List;

/**
 * AI排产建议请求DTO
 * 指定需要排产的工单ID列表和期望的排产时间范围
 */
@Data
public class AiSchedulingRequest {

    /** 需要排产的工单ID列表 */
    private List<Long> workOrderIds;

    /** 排产开始日期（格式：yyyy-MM-dd） */
    private String startDate;

    /** 排产结束日期（格式：yyyy-MM-dd） */
    private String endDate;
}
