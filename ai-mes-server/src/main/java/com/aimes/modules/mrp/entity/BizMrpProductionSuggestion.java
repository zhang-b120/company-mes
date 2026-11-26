package com.aimes.modules.mrp.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_mrp_production_suggestion")
public class BizMrpProductionSuggestion extends BaseEntity {
    private Long mrpRunId;
    private Long productId;
    private BigDecimal suggestQty;
    private LocalDate requiredDate;
    private LocalDate suggestStartDate;
    private Boolean isSemiFinished;
    private String semiHandleType;
    private Long parentSuggestionId;
    private String status;
    private Long planItemId;
    private BigDecimal grossQty;
    private BigDecimal maxProducibleQty;
    private BigDecimal actualQty;
    private String sourceSummary;
    private String remark;
}
