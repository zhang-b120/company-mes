package com.aimes.modules.finance.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 期末结账记录
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("acc_period_close")
public class AccPeriodClose extends BaseEntity {
    /** 期间 YYYY-MM */
    private String period;
    /** 结账时间 */
    private LocalDateTime closeTime;
    /** 结账人 */
    private Long closeBy;
    /** 结账凭证ID */
    private Long voucherId;
    /** 备注 */
    private String remark;
}
