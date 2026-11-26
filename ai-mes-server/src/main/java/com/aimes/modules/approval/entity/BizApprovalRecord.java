package com.aimes.modules.approval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_approval_record")
public class BizApprovalRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long instanceId;
    private Long nodeId;
    private Long approverId;
    /** APPROVE=通过, REJECT=驳回 */
    private String action;
    private String remark;
    private LocalDateTime actionTime;
}
