package com.aimes.modules.purchase.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_purchase_order_item")
public class BizPurchaseOrderItem extends BaseEntity {
    private Long purchaseOrderId;
    private Long materialId;
    private BigDecimal quantity;
    /** 含税单价 */
    private BigDecimal unitPrice;
    /** 税率% — 仅用于派生不含税金额展示 */
    private BigDecimal taxRate;
    /** 含税总价 = quantity × unitPrice */
    private BigDecimal amount;
    private BigDecimal invTaskQty;
    private BigDecimal stockInQty;
    private BigDecimal qualifiedQty;
    private BigDecimal receiptQty;
    private BigDecimal returnInitiatedQty = BigDecimal.ZERO;
    private BigDecimal returnOutQty = BigDecimal.ZERO;
    private Long requestId;
    private String remark;
}
