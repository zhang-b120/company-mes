package com.aimes.modules.approval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("biz_approval_node_approver")
public class BizApprovalNodeApprover {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long nodeId;
    /** user=指定用户, role=指定角色 */
    private String approverType;
    private Long approverId;
}
