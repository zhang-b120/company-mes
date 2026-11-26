package com.aimes.modules.inventory.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_inventory")
public class BizInventory extends BaseEntity {
    private Long materialId;
    private Long warehouseId;
    private Long locationId;
    private BigDecimal totalQty;
    private BigDecimal lockedQty;
    private BigDecimal availableQty;
    private BigDecimal safetyStock;
    private String secondaryUnit;
    private BigDecimal secondaryTotalQty;
    private BigDecimal secondaryAvailableQty;
    private BigDecimal secondaryLockedQty;
}
