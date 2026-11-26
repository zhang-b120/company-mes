package com.aimes.modules.purchase.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_purchase_return")
public class BizPurchaseReturn extends BaseEntity {
    private String returnNo;
    private Long purchaseOrderId;
    private String relatedOrderNo;
    private Long supplierId;
    private String returnReason;
    private String status;
    private Long invTaskId;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private String approveRemark;
    private String remark;
}
