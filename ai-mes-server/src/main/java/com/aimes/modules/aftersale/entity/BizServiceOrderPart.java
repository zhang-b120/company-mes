package com.aimes.modules.aftersale.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_service_order_part")
public class BizServiceOrderPart extends BaseEntity {
    private Long serviceOrderId;
    private Long materialId;
    private String materialNo;
    private String materialName;
    private String spec;
    private String unit;
    private BigDecimal quantity;
    /** 保外时对客户的单价 */
    private BigDecimal unitPrice;
    /** quantity * unitPrice */
    private BigDecimal totalPrice;
    /** 关联出库任务 biz_inv_task.id */
    private Long invTaskId;
    /** 关联生产工单 biz_work_order.id (配件新做的场景) */
    private Long workOrderId;
    /** 关联车间计划行 biz_production_plan_item.id (配件转生产计划) */
    private Long planItemId;
    private Integer issued;
    private String remark;
}
