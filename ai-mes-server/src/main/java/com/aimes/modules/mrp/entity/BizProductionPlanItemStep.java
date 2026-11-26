package com.aimes.modules.mrp.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产计划行项-工序指派
 * 持久化每个计划行项的工序配置：指派班组/人员、质检标记、时间排程、质检要求
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_production_plan_item_step")
public class BizProductionPlanItemStep extends BaseEntity {
    private Long planItemId;
    private Long stepId;
    private Integer stepNo;
    private String stepName;
    private Long teamId;
    private String teamName;
    private Long assigneeId;
    private String assigneeName;
    private Boolean needQc;
    private Integer standardTime;
    private BigDecimal piecePrice;
    /** 处理方式: self(自制)/outsource(外协) */
    private String handleType;
    private Long supplierId;
    private String supplierName;
    /** 工序计划开始时间 */
    private LocalDateTime planStartTime;
    /** 工序计划结束时间 */
    private LocalDateTime planEndTime;
    /** 质检模板ID */
    private Long qcTemplateId;
    /** 质检要求JSON: [{itemName, itemType, standardValue, toleranceMin, toleranceMax, requirement}] */
    private String qcRequirements;
    /** 工艺路线ID */
    private Long routeId;
    /** 工资比例(默认 1.0;三机模式下作为基础比例) */
    private BigDecimal wageRatio;
    /** 班组指派时允许报工的班组成员 userId JSON 数组(如 [1,2,3]);空/null = 全部成员 */
    private String assignedMemberIds;
}
