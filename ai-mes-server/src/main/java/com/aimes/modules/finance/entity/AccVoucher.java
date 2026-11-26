package com.aimes.modules.finance.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 记账凭证
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("acc_voucher")
public class AccVoucher extends BaseEntity {
    /** 凭证编号 */
    private String voucherNo;
    /** 凭证类型: receipt/payment/transfer */
    private String voucherType;
    /** 凭证日期 */
    private LocalDate voucherDate;
    /** 摘要 */
    private String summary;
    /** 借方合计 */
    private BigDecimal totalDebit;
    /** 贷方合计 */
    private BigDecimal totalCredit;
    /** 状态: draft/posted */
    private String status;
    /** 是否已冲红 */
    private Boolean isReversed;
    /** 冲红凭证ID */
    private Long reversedBy;
    /** 自动来源类型 */
    private String autoSourceType;
    /** 自动来源ID */
    private Long autoSourceId;
    /** 所属期间 YYYY-MM */
    private String period;
    /** 备注 */
    private String remark;
}
