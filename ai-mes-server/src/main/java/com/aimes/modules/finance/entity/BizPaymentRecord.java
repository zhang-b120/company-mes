package com.aimes.modules.finance.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_payment_record")
public class BizPaymentRecord extends BaseEntity {
    private String paymentNo;
    private String relatedType;
    private Long relatedId;
    private BigDecimal amount;
    private LocalDate paymentDate;
    private String paymentMethod;
    private String transactionNo;
    private String attachmentUrl;
    private String remark;
}
