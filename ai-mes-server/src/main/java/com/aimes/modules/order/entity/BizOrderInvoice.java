package com.aimes.modules.order.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_order_invoice")
public class BizOrderInvoice extends BaseEntity {
    private Long orderId;
    private String invoiceType;
    private String invoiceNo;
    /** 已发货数量(单行场景,兼容旧数据) */
    private BigDecimal shippedQty;
    /** 单价(单行场景,兼容旧数据) */
    private BigDecimal unitPrice;
    /** 税率(%),默认 13 */
    private BigDecimal taxRate;
    /** 多产品明细 JSON: [{productId,productName,spec,quantity,unitPrice,amountExTax,taxAmount,totalAmount}] */
    private String itemsJson;
    /** 发票总额(含税合计) */
    private BigDecimal invoiceAmount;
    /** 税额合计 */
    private BigDecimal taxAmount;
    private LocalDate invoiceDate;
    private String attachmentUrl;
    private String status;
    private String remark;
}
