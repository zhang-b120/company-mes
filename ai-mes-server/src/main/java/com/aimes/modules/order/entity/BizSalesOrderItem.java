package com.aimes.modules.order.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_sales_order_item")
public class BizSalesOrderItem extends BaseEntity {
    private Long orderId;
    private Long productId;
    private BigDecimal quantity;
    /** 含税单价 */
    private BigDecimal unitPrice;
    /** 含税总价 = quantity × unitPrice */
    private BigDecimal amount;
    /** 税率% — 仅用于派生不含税金额展示 */
    private BigDecimal taxRate;
    private LocalDate deliveryDate;
    private BigDecimal producedQty;
    private BigDecimal shippedQty;
    private BigDecimal warehouseInQty;
    private BigDecimal taskQty;
    private String remark;
    /** 行级扩展字段 JSON(由 biz_ext_field_config module=sales_order_item 定义) */
    private String extFields;
}
