package com.aimes.modules.purchase.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_purchase_request")
public class BizPurchaseRequest extends BaseEntity {
    private String requestNo;
    private Long materialId;
    private String materialName;
    private String spec;
    private String unit;
    private BigDecimal quantity;
    private Long suggestedSupplierId;
    @JsonAlias("requiredDate")
    private LocalDate expectedDate;
    private String sourceType;
    /** 来源单号 (人工填写, 关联销售订单/生产计划/工单等的单号, 选填) */
    private String sourceNo;
    /** 用途说明 (例: 生产XX产品) */
    private String purpose;
    private BigDecimal estimatedPrice;
    private String status;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private String approveRemark;
    private String remark;
}
