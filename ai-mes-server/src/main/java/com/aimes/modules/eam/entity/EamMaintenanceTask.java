package com.aimes.modules.eam.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eam_maintenance_task")
public class EamMaintenanceTask extends BaseEntity {
    private String taskCode;
    private Long planId;
    private Long equipmentId;
    private String maintenanceContent;
    private Long executorId;
    private String executorName;
    private LocalDateTime plannedStartTime;
    private LocalDateTime plannedEndTime;
    private LocalDateTime actualStartTime;
    private LocalDateTime actualEndTime;
    /** 0待执行 1执行中 2已完成 3已取消 4已暂停 */
    private Integer taskStatus;
    private String maintenanceResult;
    private String issuesFound;
    private String consumablesUsed;
    private String sparePartsReplaced;
    private BigDecimal actualHours;
    private String maintenanceStandard;
    private String remark;
}
