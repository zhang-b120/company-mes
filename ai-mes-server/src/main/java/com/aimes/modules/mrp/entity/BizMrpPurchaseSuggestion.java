package com.aimes.modules.mrp.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_mrp_purchase_suggestion")
public class BizMrpPurchaseSuggestion extends BaseEntity {
    private Long mrpRunId;
    private Long materialId;
    private BigDecimal suggestQty;
    private LocalDate requiredDate;
    private LocalDate suggestOrderDate;
    private Long supplierId;
    private String status;
    private Long purchaseRequestId;
    private String sourceSummary;
    private String remark;
}
