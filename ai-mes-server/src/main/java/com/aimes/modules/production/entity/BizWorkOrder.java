package com.aimes.modules.production.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_work_order")
public class BizWorkOrder extends BaseEntity {
    private String woNo;
    private Long orderId;
    private Long orderItemId;
    private Long productId;
    private BigDecimal planQty;
    private BigDecimal completedQty;
    private String productionMode;
    private LocalDate deliveryDate;
    /** 工单创建时的原始交货日期(用于"延期"角标判定:current > original 即为延期) */
    private LocalDate originalDeliveryDate;
    private Integer priority;
    private String status;
    private String remark;
    /** 工单类型: normal=正常, repair=返修 */
    private String workOrderType;
    /** 源工单ID(返修工单关联) */
    private Long sourceWorkOrderId;
    /** 源缺陷记录ID */
    private Long sourceDefectId;
    private Long planItemId;

    /** 已生成成品入库任务数量 */
    private BigDecimal stockInTaskQty;
    /** 已确认入库数量（库存任务处理后累计） */
    private BigDecimal stockInQty;
    /** 库存任务状态: null=未生成, partial=部分生成, generated=全部生成 */
    private String invTaskStatus;

    /** 扩展字段(继承自销售订单) */
    private String extFields;
    /** 工艺路线ID（不持久化，由计划下达时传入，供生成工序任务使用） */
    @TableField(exist = false)
    private Long routeId;
    /** 替代料选择（不持久化，由主计划下达时传入，供 snapshotBom 写入 preferredSubstituteId） */
    @TableField(exist = false)
    private java.util.Map<Long, Long> substituteSelections;
}
