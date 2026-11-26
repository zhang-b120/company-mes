package com.aimes.modules.inventory.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 仓库盘点单明细
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_inv_stocktaking_item")
public class BizInvStocktakingItem extends BaseEntity {
    /** 盘点单ID */
    private Long stocktakingId;
    /** 物料ID */
    private Long materialId;
    /** 物料编号 */
    private String materialNo;
    /** 物料名称 */
    private String materialName;
    /** 规格型号 */
    private String spec;
    /** 单位 */
    private String unit;
    /** 在库数量(创建盘点单时快照) */
    private BigDecimal systemQty;
    /** 锁定数量(创建盘点单时快照) */
    private BigDecimal lockedQty;
    /** 可用数量(创建盘点单时快照) */
    private BigDecimal availableQty;
    /** 实盘数量 */
    private BigDecimal actualQty;
    /** 差异数量(actualQty - systemQty)，正=盘盈，负=盘亏 */
    private BigDecimal diffQty;
}
