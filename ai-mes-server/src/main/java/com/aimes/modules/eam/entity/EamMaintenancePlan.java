package com.aimes.modules.eam.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eam_maintenance_plan")
public class EamMaintenancePlan extends BaseEntity {
    private String planCode;
    private String planName;
    /** 0周期 1预防性 2状态 */
    private Integer maintenanceType;
    private Long templateId;
    private Long equipmentId;
    private Integer executionCycle;
    private LocalDateTime nextExecutionTime;
    private LocalDateTime plannedStartTime;
    private LocalDateTime plannedEndTime;
    private String maintenanceContent;
    private String maintenanceStandard;
    private BigDecimal estimatedHours;
    private String requiredSpareParts;
    /** 0草稿 1已发布 2已暂停 3已取消 */
    private Integer planStatus;
    /** 0待下达 1已下达 2部分完成 3全部完成 */
    private Integer execStatus;
    private String remark;
}
