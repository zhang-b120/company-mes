package com.aimes.modules.eam.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eam_inspection_task")
public class EamInspectionTask extends BaseEntity {
    private String taskCode;
    private Long planId;
    private Long templateId;
    private Long equipmentId;
    private Long inspectorId;
    private String inspectorName;
    private LocalDateTime plannedStartTime;
    private LocalDateTime plannedEndTime;
    private LocalDateTime startTime;
    private LocalDateTime submitTime;
    /** 实际开始时间(完成时可编辑) */
    private LocalDateTime actualStartTime;
    /** 实际结束时间(完成时可编辑) */
    private LocalDateTime actualEndTime;
    /** 0待执行 1执行中 2已完成 3已取消 4已暂停 */
    private Integer taskStatus;
    private String remark;
}
