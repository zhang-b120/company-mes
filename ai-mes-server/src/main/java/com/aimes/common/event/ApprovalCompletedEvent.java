package com.aimes.common.event;

import org.springframework.context.ApplicationEvent;

/**
 * 审批流程通过事件
 * 当审批实例所有节点全部通过时发布，各业务服务监听并更新对应单据状态
 */
public class ApprovalCompletedEvent extends ApplicationEvent {

    private final String businessType;
    private final Long businessId;
    private final Long approverId;
    private final String remark;

    public ApprovalCompletedEvent(Object source, String businessType, Long businessId,
                                   Long approverId, String remark) {
        super(source);
        this.businessType = businessType;
        this.businessId = businessId;
        this.approverId = approverId;
        this.remark = remark;
    }

    public String getBusinessType() { return businessType; }
    public Long getBusinessId() { return businessId; }
    public Long getApproverId() { return approverId; }
    public String getRemark() { return remark; }
}
