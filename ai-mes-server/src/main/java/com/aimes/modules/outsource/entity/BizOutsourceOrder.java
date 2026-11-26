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
@TableName("biz_outsource_order")
public class BizOutsourceOrder extends BaseEntity {
    private String orderNo;
    private Long supplierId;
    private Long productId;
    private String stepName;
    private BigDecimal totalQty;
    /** 含税单价 */
    private BigDecimal unitPrice;
    /** 含税总金额(价税合计) = totalQty × unitPrice */
    private BigDecimal totalAmount;
    private LocalDate deliveryDate;
    private BigDecimal deliveredQty;
    private BigDecimal qualifiedQty;
    /** 税率% */
    private BigDecimal taxRate;
    /** 税额 */
    private BigDecimal taxAmount;
    /** 关联工单ID */
    private Long workOrderId;
    /** 关联工序任务ID */
    private Long taskId;
    /** 关联外协申请ID */
    private Long requestId;
    /** 发料物料ID(前道工序虚拟半成品) */
    private Long virtualMaterialId;
    /** 收料物料ID(外协工序输出虚拟半成品) */
    private Long receiptMaterialId;
    /** 发货数量 */
    private BigDecimal deliveryQty;
    private String status;
    private String remark;
    /** 关联销售订单ID(整单外协) */
    private Long orderId;
    /** 关联销售订单行ID(整单外协) */
    private Long orderItemId;

    /** 审批人ID */
    private Long approvedBy;
    /** 审批时间 */
    private LocalDateTime approvedAt;
    /** 审批备注 */
    private String approveRemark;
    /** 发料任务数 */
    private BigDecimal deliveryTaskQty;
    /** 发料出库数（库存任务处理后累加） */
    private BigDecimal deliveryOutQty;
    /** 收货任务数 */
    private BigDecimal receiptTaskQty;
    /** 收货入库数（库存任务处理后累加） */
    private BigDecimal receiptInQty;
    /** 已生成收料单数量 */
    private BigDecimal receiptQty;
    /** 工艺要求 */
    private String processRequirement;
    /** 图纸URLs */
    private String drawingUrls;
}
