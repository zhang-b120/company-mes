package com.aimes.modules.finance.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_receivable")
public class BizReceivable extends BaseEntity {
    private String receivableNo;
    private Long orderId;
    private Long customerId;
    private BigDecimal amount;
    private BigDecimal receivedAmount;
    private BigDecimal unreceivedAmount;
    private LocalDate dueDate;
    private String status;
    private String remark;
}
