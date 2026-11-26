package com.aimes.modules.eam.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eam_repair_request")
public class EamRepairRequest extends BaseEntity {
    private String requestNo;
    private Long equipmentId;
    private String equipmentNo;
    private String equipmentName;
    /** FAULT/PREVENTIVE/OVERHAUL */
    private String repairType;
    /** LOW/MEDIUM/HIGH/URGENT */
    private String priority;
    private LocalDateTime plannedStartTime;
    private LocalDateTime plannedEndTime;
    private String faultDescription;
    /** JSON数组 */
    private String attachments;
    private Long reporterId;
    private String reporterName;
    private LocalDateTime reportTime;
    private Long acceptorId;
    private String acceptorName;
    private LocalDateTime acceptTime;
    /** PENDING/ACCEPTED/IN_PROGRESS/COMPLETED/CANCELLED */
    private String status;
}
