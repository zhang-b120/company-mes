package com.aimes.modules.master.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_product")
public class BizProduct extends BaseEntity {
    private String productNo;
    private String productName;
    private String spec;
    private String unit;
    private String unitConfig;
    private String category;
    private Long categoryId;
    private String productionMode;
    private BigDecimal targetStock;
    private BigDecimal reorderPoint;
    private BigDecimal reorderQty;
    private BigDecimal maxStock;
    private BigDecimal minStock;
    private BigDecimal standardCost;
    private String imageUrls;
    private String drawingUrls;
    private Boolean hasBom;
    private Boolean hasProcess;
    private Boolean allowStockLock;
    private String materialAttributes;
    private String status;
    private String remark;

    // MRP相关字段
    private Integer productionLeadTime;
    private BigDecimal minProductionQty;
    private BigDecimal productionQtyMultiple;
}
