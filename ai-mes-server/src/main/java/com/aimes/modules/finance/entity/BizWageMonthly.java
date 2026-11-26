package com.aimes.modules.finance.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_wage_monthly")
public class BizWageMonthly extends BaseEntity {
    private Long userId;
    private String month;
    private BigDecimal personalPieceWage;
    private BigDecimal teamAllocatedWage;
    private BigDecimal overtimeWage;
    private BigDecimal deduction;
    private BigDecimal bonus;
    private BigDecimal totalWage;
    private Integer workDays;
    private BigDecimal totalQty;
    private String status;
    private Long confirmedBy;
    private LocalDateTime confirmedAt;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private String remark;
}
