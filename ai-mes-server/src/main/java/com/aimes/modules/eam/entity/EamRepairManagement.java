package com.aimes.modules.eam.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eam_repair_management")
public class EamRepairManagement extends BaseEntity {
    private String workOrderNo;
    private Long repairRequestId;
    private Long equipmentId;
    private String equipmentNo;
    private String equipmentName;
    private String repairType;
    private String priority;
    private Long assigneeId;
    private String assigneeName;
    private LocalDateTime plannedStartTime;
    private LocalDateTime plannedEndTime;
    private LocalDateTime actualStartTime;
    private LocalDateTime actualEndTime;
    private BigDecimal estimatedHours;
    private BigDecimal actualHours;
    private String faultDescription;
    private String repairPlan;
    private String repairResult;
    private BigDecimal repairCost;
    private String replacedParts;
    /** PENDING/IN_PROGRESS/PAUSED/COMPLETED/CANCELLED */
    private String status;
}
