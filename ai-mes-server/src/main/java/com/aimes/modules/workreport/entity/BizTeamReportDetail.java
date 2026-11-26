package com.aimes.modules.workreport.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 班组报工明细实体
 * 记录班组报工中每个成员的权重和分配工资信息
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_team_report_detail")
public class BizTeamReportDetail extends BaseEntity {

    /** 班组报工记录ID */
    private Long teamReportId;

    /** 成员用户ID */
    private Long userId;

    /** 工资分配权重 */
    private BigDecimal weight;

    /** 分配后的工资金额 */
    private BigDecimal allocatedWage;
}
