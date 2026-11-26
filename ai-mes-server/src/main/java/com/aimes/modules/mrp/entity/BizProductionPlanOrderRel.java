package com.aimes.modules.mrp.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_production_plan_order_rel")
public class BizProductionPlanOrderRel extends BaseEntity {
    private Long planItemId;
    private Long orderId;
    private Long orderItemId;
    private BigDecimal allocatedQty;
}
