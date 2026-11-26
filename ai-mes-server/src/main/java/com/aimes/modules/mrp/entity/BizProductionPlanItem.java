package com.aimes.modules.mrp.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_production_plan_item")
public class BizProductionPlanItem extends BaseEntity {
    private Long planId;
    private Long productId;
    private BigDecimal planQty;
    private BigDecimal releasedQty;
    private BigDecimal completedQty;
    private LocalDate planStartDate;
    private LocalDate planEndDate;
    private Integer priority;
    private String sourceType;
    private Long mrpSuggestionId;
    private String status;
    private String remark;
    /** 主计划替代料选择 JSON，格式: {"materialId": substituteId, ...} */
    private String substituteSelections;
}
