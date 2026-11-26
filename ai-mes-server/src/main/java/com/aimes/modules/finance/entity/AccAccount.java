package com.aimes.modules.finance.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 会计科目
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("acc_account")
public class AccAccount extends BaseEntity {
    /** 科目编码 */
    private String accountCode;
    /** 科目名称 */
    private String accountName;
    /** 科目类型: asset/liability/equity/income/expense/cost */
    private String accountType;
    /** 余额方向: debit/credit */
    private String direction;
    /** 父科目ID */
    private Long parentId;
    /** 层级 1/2/3 */
    private Integer level;
    /** 是否末级 */
    private Boolean isLeaf;
    /** 是否启用 */
    private Boolean isEnabled;
    /** 期初余额 */
    private BigDecimal openingBalance;
    /** 备注 */
    private String remark;
}
