package com.aimes.modules.workreport.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_wage_record")
public class BizWageRecord extends BaseEntity {
    private Long userId;
    private Long workOrderId;
    private Long taskId;
    private Long reportId;
    private String wageType;
    private BigDecimal amount;
    private LocalDate reportDate;
}
