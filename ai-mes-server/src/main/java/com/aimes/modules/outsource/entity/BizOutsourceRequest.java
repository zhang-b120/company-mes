package com.aimes.modules.outsource.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_outsource_request")
public class BizOutsourceRequest extends BaseEntity {
    private String requestNo;
    private Long workOrderId;
    private Long taskId;
    private Long productId;
    private String stepName;
    private BigDecimal quantity;
    private String processRequirement;
    private String drawingUrls;
    private LocalDate requiredDate;
    private String status;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private String approveRemark;
    private String remark;
    /** 最大可申请数量(=工单计划数) */
    private BigDecimal maxQty;
    /** 前道工序累计合格数 */
    private BigDecimal prevQualifiedQty;
    /** 已转为加工单数量(累计) */
    private BigDecimal requestedQty;
    /** 发料物料ID(前道工序虚拟半成品) */
    private Long virtualMaterialId;
    /** 发料仓库ID(默认999) */
    private Long deliveryWarehouseId;
    /** 收料物料ID(外协工序输出虚拟半成品) */
    private Long receiptMaterialId;
    /** 是否报工自动创建 0=手动 1=自动 */
    private Integer isAutoCreated;
    /** 关联销售订单ID(整单外协) */
    private Long orderId;
    /** 关联销售订单行ID(整单外协) */
    private Long orderItemId;
}
