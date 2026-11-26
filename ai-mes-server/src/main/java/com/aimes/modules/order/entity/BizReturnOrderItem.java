package com.aimes.modules.order.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_return_order_item")
public class BizReturnOrderItem extends BaseEntity {
    private Long returnId;
    private Long orderItemId;
    private Long productId;
    private String productName;
    private String spec;
    private String unit;
    private BigDecimal returnQty;
    private BigDecimal receivedQty;
    private Long warehouseId;
    /** 含税退货单价 */
    private BigDecimal unitPrice;
    private String remark;
}
