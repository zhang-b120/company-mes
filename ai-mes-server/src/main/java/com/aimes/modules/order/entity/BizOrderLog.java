package com.aimes.modules.order.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_order_log")
public class BizOrderLog extends BaseEntity {
    /** 订单ID */
    private Long orderId;
    /** 操作类型：create/update/submit/approve/reject/void/complete/cancel/change/shipment */
    private String operation;
    /** 操作描述 */
    private String operationDesc;
    /** 操作人姓名（冗余存储） */
    private String operatorName;
}
