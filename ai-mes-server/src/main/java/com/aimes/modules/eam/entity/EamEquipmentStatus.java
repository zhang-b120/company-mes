package com.aimes.modules.eam.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eam_equipment_status")
public class EamEquipmentStatus extends BaseEntity {
    private Long equipmentId;
    /** RUNNING/STOPPED/MAINTENANCE */
    private String runningStatus;
    private LocalDateTime lastRunningTime;
    private BigDecimal totalRunningHours;
    private BigDecimal currentTemperature;
    private BigDecimal currentPressure;
    private BigDecimal currentSpeed;
    private BigDecimal currentLoad;
    /** NORMAL/WARNING/CRITICAL */
    private String alarmStatus;
    private String alarmMessage;
}
