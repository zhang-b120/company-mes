package com.aimes.common.event;

import org.springframework.context.ApplicationEvent;

/**
 * 审批流程驳回事件
 * 当审批实例被驳回时发布，各业务服务监听并将对应单据回退到草稿状态
 */
public class ApprovalRejectedEvent extends ApplicationEvent {

    private final String businessType;
    private final Long businessId;
    private final Long rejecterId;
    private final String remark;

    public ApprovalRejectedEvent(Object source, String businessType, Long businessId,
                                  Long rejecterId, String remark) {
        super(source);
        this.businessType = businessType;
        this.businessId = businessId;
        this.rejecterId = rejecterId;
        this.remark = remark;
    }

    public String getBusinessType() { return businessType; }
    public Long getBusinessId() { return businessId; }
    public Long getRejecterId() { return rejecterId; }
    public String getRemark() { return remark; }
}
