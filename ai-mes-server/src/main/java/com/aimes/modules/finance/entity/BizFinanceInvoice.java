package com.aimes.modules.finance.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_finance_invoice")
public class BizFinanceInvoice extends BaseEntity {
    private String invoiceNo;
    private String invoiceType;       // input(进项) / output(销项)
    private String invoiceCode;       // 发票代码
    private String invoiceNumber;     // 发票号码
    private LocalDate invoiceDate;    // 开票日期
    private BigDecimal amount;        // 金额(不含税)
    private BigDecimal taxRate;       // 税率
    private BigDecimal taxAmount;     // 税额
    private BigDecimal totalAmount;   // 价税合计
    private String buyerName;         // 购方名称
    private String buyerTaxNo;        // 购方税号
    private String sellerName;        // 销方名称
    private String sellerTaxNo;       // 销方税号
    private String relatedType;       // 关联单据类型: sales_order/purchase_order/outsource_order
    private Long relatedId;           // 关联单据ID
    private String relatedNo;         // 关联单据号
    private Long customerId;          // 客户ID(销项)
    private Long supplierId;          // 供应商ID(进项)
    private String attachmentUrl;     // 发票附件URL
    /** 多产品明细 JSON: [{productId,productName,spec,quantity,unitPrice,amountExTax,taxAmount,totalAmount}] */
    private String itemsJson;
    private String status;            // draft/confirmed/cancelled
    private String remark;
}
