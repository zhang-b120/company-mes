package com.aimes.modules.eam.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eam_inspection_plan")
public class EamInspectionPlan extends BaseEntity {
    private String planCode;
    private String planName;
    private Long templateId;
    /** 0日常 1定期 2专项 */
    private Integer inspectionType;
    private Integer executionCycle;
    private LocalDateTime nextExecutionTime;
    private LocalDateTime plannedStartTime;
    private LocalDateTime plannedEndTime;
    /** 预估时长(分钟) */
    private Integer estimatedDuration;
    private Long assigneeId;
    private String assigneeName;
    /** 0草稿 1已发布 3已取消 */
    private Integer planStatus;
    private Integer execStatus;
    private String remark;
}
