package com.aimes.modules.production.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_wo_bom_snapshot")
public class BizWoBomSnapshot extends BaseEntity {
    private Long workOrderId;
    private Long materialId;
    /** BOM子件ID（便于查询替代料配置） */
    private Long bomItemId;
    private BigDecimal requiredQty;
    /** 已完成领料数量（库存任务处理后累加；含替代料按比例折算的主料等效量） */
    private BigDecimal issuedQty;
    /** 已生成领料任务数量 */
    private BigDecimal issuedTaskQty;
    /** 已生成退料任务数量 */
    private BigDecimal returnedTaskQty;
    /** 已完成退料数量（库存任务处理后累加） */
    private BigDecimal returnedQty;
    /** 主计划指定的优先替代料 ID（BizBomSubstitute.id），领料时默认选中该替代料 */
    private Long preferredSubstituteId;
}
