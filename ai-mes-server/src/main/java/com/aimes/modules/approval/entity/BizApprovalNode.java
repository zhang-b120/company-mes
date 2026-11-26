package com.aimes.modules.approval.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_approval_node")
public class BizApprovalNode extends BaseEntity {
    private Long flowId;
    private String nodeName;
    private Integer nodeOrder;
    /** OR=或签(任一通过即可), AND=会签(全部通过) */
    private String approvalMode;
    private String remark;
}
