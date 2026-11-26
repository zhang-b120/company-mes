package com.aimes.modules.outsource.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_outsource_delivery_item")
public class BizOutsourceDeliveryItem extends BaseEntity {
    private Long deliveryId;
    private Long materialId;
    private BigDecimal shouldQty;
    private BigDecimal actualQty;
    private String batchNo;
    private Long warehouseId;
    private String remark;
}
