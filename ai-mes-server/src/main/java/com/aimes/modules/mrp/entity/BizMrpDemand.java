package com.aimes.modules.mrp.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_mrp_demand")
public class BizMrpDemand extends BaseEntity {
    private Long mrpRunId;
    private Long materialId;
    private Long productId;
    private String demandType;
    private String itemType;
    private BigDecimal grossQty;
    private BigDecimal onHandQty;
    private BigDecimal inTransitQty;
    private BigDecimal inProductionQty;
    private BigDecimal netQty;
    private BigDecimal adjustedQty;
    private LocalDate requiredDate;
    private Long sourceOrderId;
    private Long sourceOrderItemId;
    private Long parentDemandId;
}
