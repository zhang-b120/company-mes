package com.aimes.modules.quality.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_qc_disposal")
public class BizQcDisposal extends BaseEntity {
    private String disposalNo;
    private Long qcTaskId;
    private Long qcRecordId;
    private Long workOrderId;
    private Long processTaskId;
    private Long materialId;
    private String stepName;
    private BigDecimal quantity;
    private String disposalType;    // scrap / concession / rework
    private String disposalReason;
    private String status;          // pending / processed / closed
    private Long reworkOrderId;
    private Long invTaskId;
    private Long processedBy;
    private LocalDateTime processedAt;
    private String remark;
}
