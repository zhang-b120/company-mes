package com.aimes.modules.outsource.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_outsource_delivery")
public class BizOutsourceDelivery extends BaseEntity {
    private String deliveryNo;
    private Long outsourceOrderId;
    private String deliveryType;
    /** 发料数量(虚拟半成品发料时使用) */
    private BigDecimal deliveryQty;
    private String status;
    private String remark;
}
