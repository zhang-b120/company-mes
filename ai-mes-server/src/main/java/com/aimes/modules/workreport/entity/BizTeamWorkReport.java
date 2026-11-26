package com.aimes.modules.workreport.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 班组报工记录实体
 * 用于记录以班组为单位的集体报工信息，包括合格数量、缺陷数量和总工资等
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_team_work_report")
public class BizTeamWorkReport extends BaseEntity {

    /** 报工单号 */
    private String reportNo;

    /** 工序任务ID */
    private Long taskId;

    /** 工单ID */
    private Long workOrderId;

    /** 班组ID */
    private Long teamId;

    /** 班组长ID */
    private Long leaderId;

    /** 合格数量 */
    private BigDecimal qualifiedQty;

    /** 缺陷数量 */
    private BigDecimal defectQty;

    /** 总工资 */
    private BigDecimal totalWage;
}
