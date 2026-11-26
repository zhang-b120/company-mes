package com.aimes.modules.order.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_shipment_record")
public class BizShipmentRecord extends BaseEntity {
    private Long orderId;
    private Long orderItemId;
    private Long productId;
    private BigDecimal shippedQty;
    private String shipmentNo;
    private String logisticsCompany;
    private String trackingNo;
    private LocalDateTime shippedAt;
    private String status;
    private String remark;
}
