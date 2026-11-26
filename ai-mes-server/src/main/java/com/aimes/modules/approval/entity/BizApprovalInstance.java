package com.aimes.modules.approval.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_approval_instance")
public class BizApprovalInstance extends BaseEntity {
    private Long flowId;
    private String businessType;
    private Long businessId;
    /** PENDING=审批中, APPROVED=已通过, REJECTED=已驳回, CANCELLED=已撤回 */
    private String status;
    private Long currentNodeId;
    private Long applicantId;
    private String remark;
}
