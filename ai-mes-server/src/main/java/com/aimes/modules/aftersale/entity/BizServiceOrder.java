package com.aimes.modules.aftersale.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_service_order")
public class BizServiceOrder extends BaseEntity {
    private String serviceNo;
    private Long salesOrderId;
    private String salesOrderNo;
    private Long customerId;
    private String customerName;
    private Long productId;
    private String productNo;
    private String productName;
    private String spec;
    /** 1=保内, 0=保外 */
    private Integer inWarranty;
    private String issueDescription;
    private String handlingMethod;
    /** draft/processing/completed/cancelled */
    private String status;
    private Long handlerId;
    private String handlerName;
    private BigDecimal totalPartCost;
    private BigDecimal totalChargeAmount;
    private LocalDateTime completeTime;
    private String remark;
}
