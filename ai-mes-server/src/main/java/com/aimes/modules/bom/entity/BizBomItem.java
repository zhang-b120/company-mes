package com.aimes.modules.bom.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_bom_item")
public class BizBomItem extends BaseEntity {
    private Long bomVersionId;
    private Long parentItemId;
    private String itemType;
    private String sourceType;      // manufacture(自制) / purchase(外购)
    private Long materialId;
    private Long productId;
    private BigDecimal quantity;
    private BigDecimal wastageRate;
    private BigDecimal actualQuantity;
    private String substituteGroup;
    private Integer sortOrder;
    private String remark;
}
