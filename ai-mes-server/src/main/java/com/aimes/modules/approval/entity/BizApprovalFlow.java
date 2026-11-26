package com.aimes.modules.approval.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_approval_flow")
public class BizApprovalFlow extends BaseEntity {
    private String flowName;
    private String flowCode;
    private String businessType;
    private Integer enabled;
    private String remark;
}
