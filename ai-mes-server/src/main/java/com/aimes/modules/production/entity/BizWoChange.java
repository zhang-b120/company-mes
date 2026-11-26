package com.aimes.modules.production.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_wo_change")
public class BizWoChange extends BaseEntity {
    private Long workOrderId;
    private String changeType;
    private String changeContent;
    private String impactAssessment;
    private String changeReason;
    private String status;
    private Long approvedBy;
}
