package com.aimes.modules.finance.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_wo_cost")
public class BizWoCost extends BaseEntity {
    private Long workOrderId;
    private BigDecimal materialCost;
    private BigDecimal laborCost;
    private BigDecimal outsourceCost;
    private BigDecimal reworkCost;
    private BigDecimal scrapLoss;
    private BigDecimal overhead;
    private BigDecimal totalCost;
}
