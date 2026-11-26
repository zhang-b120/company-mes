package com.aimes.modules.finance.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 凭证明细行
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("acc_voucher_line")
public class AccVoucherLine extends BaseEntity {
    /** 凭证ID */
    private Long voucherId;
    /** 行号 */
    private Integer lineNo;
    /** 摘要 */
    private String summary;
    /** 科目ID */
    private Long accountId;
    /** 科目编码（冗余） */
    private String accountCode;
    /** 科目名称（冗余） */
    private String accountName;
    /** 借方金额 */
    private BigDecimal debitAmount;
    /** 贷方金额 */
    private BigDecimal creditAmount;
}
