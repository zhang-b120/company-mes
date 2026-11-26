package com.aimes.modules.eam.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eam_equipment_alarm_log")
public class EamEquipmentAlarmLog extends BaseEntity {
    private Long equipmentId;
    private String alarmStatus;
    private String alarmMessage;
    private LocalDateTime occurredAt;
}
