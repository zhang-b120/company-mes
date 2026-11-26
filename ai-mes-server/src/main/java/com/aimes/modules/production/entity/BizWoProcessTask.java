package com.aimes.modules.production.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_wo_process_task")
public class BizWoProcessTask extends BaseEntity {
    private Long workOrderId;
    private Integer stepNo;
    private Integer stepGroup;
    private String stepName;
    private Integer standardTime;
    private BigDecimal piecePrice;
    private BigDecimal planQty;
    /** 已报工数量（含待质检），有质检模板时使用；合格数在质检通过后累入 qualifiedQty */
    private BigDecimal reportedQty;
    private BigDecimal qualifiedQty;
    private BigDecimal defectQty;
    private Long assignedUserId;
    private Long assignedTeamId;
    /** 指派方式: team=班组, person=个人 */
    private String assignType;
    /** 指派班组名称(冗余) */
    private String assignedTeamName;
    /** 指派人姓名(冗余) */
    private String assignedUserName;
    private String status;
    private Integer isOutsourced;
    /** 是否入半成品库(来自工艺路线) 0=否 1=是 */
    private Integer isSemiStock;
    /** 是否可外协(来自工艺路线) 0=否 1=是 */
    private Integer isOutsourceable;
    /** 虚拟半成品物料ID(外协链路关联) */
    private Long virtualMaterialId;
    /** 工序半成品物料ID(惰性: 首次"半成品入库"时建档绑定, 之后下道工序按此 ID 领料) */
    private Long semiMaterialId;
    /** 累计已入半成品库数量 */
    private BigDecimal semiStockedQty;
    /** 累计已被下道工序领走的半成品数量 (= 实际消耗) */
    private BigDecimal semiIssuedQty;
    /** 外协申请ID */
    private Long outsourceRequestId;
    /** 外协加工单ID */
    private Long outsourceOrderId;
    /** 外协状态: requested/ordered/processing/completed */
    private String outsourceStatus;
    /** 质检模板ID(来自工艺路线) */
    private Long qcTemplateId;
    /** 质检状态: null=无需质检, pending=待检, pass=合格, fail=不合格 */
    private String qcStatus;
    /** 关联的质检任务ID */
    private Long qcTaskId;
    /** 质检要求JSON(来自生产计划): [{itemName, itemType, standardValue, toleranceMin, toleranceMax, requirement, isCritical}] */
    private String qcRequirements;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    /** 工序计划完成时间 (从 biz_scheduling 排产记录注入, 不持久化) */
    @TableField(exist = false)
    private LocalDateTime planEndTime;
    /** 工资比例(默认 1.0;三机模式下作为基础比例) */
    private BigDecimal wageRatio;
    /** 班组指派时允许报工的班组成员 userId JSON 数组(如 [1,2,3]);空/null = 全部成员 */
    private String assignedMemberIds;
}
