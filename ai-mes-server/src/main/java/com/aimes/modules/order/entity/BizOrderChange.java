package com.aimes.modules.order.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_order_change")
public class BizOrderChange extends BaseEntity {
    private Long orderId;
    private String changeType;
    private String changeContent;
    private String impactAssessment;
    private String changeReason;
    private String status;            // draft / pending / approved / rejected / failed
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private String approveRemark;
    private String originalSnapshot;  // 创建变更时的原始订单 JSON (审批时 diff 用)
}
