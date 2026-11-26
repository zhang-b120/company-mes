package com.aimes.modules.mrp.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_plan_item_material")
public class BizPlanItemMaterial extends BaseEntity {
    private Long planId;
    private Long planItemId;
    private Long materialId;
    private Long warehouseId;
    private BigDecimal requiredQty;
    private BigDecimal lockedQty;
    private BigDecimal shortageQty;
    private BigDecimal releasedToWoQty;
}
