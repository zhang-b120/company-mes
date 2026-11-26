package com.aimes.modules.inventory.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_inv_reservation")
public class BizInvReservation extends BaseEntity {
    private Long materialId;
    private Long warehouseId;
    private String reservationType;
    private BigDecimal lockedQty;
    private BigDecimal consumedQty;
    private BigDecimal remainingQty;
    private String relatedOrderType;
    private Long relatedOrderId;
    /** 来源单据号（如工单号WO202501001、订单号SO202501001等，便于展示追溯） */
    private String relatedOrderNo;
    private Long lockedBy;
    private LocalDate expireDate;
    private String status;
    private String reason;
    private Long planItemId;
}
