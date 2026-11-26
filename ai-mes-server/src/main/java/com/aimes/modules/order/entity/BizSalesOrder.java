package com.aimes.modules.order.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_sales_order")
public class BizSalesOrder extends BaseEntity {
    private String orderNo;
    private Long customerId;
    private String contactName;
    private String shippingAddress;
    private String settlementType;
    private BigDecimal totalAmount;
    private LocalDate deliveryDate;
    /** 原始交货日期: 创建时记录, 后续延期变更不更新; 用于"延期"角标判定 */
    private LocalDate originalDeliveryDate;
    private BigDecimal paymentAmount;
    private String status;
    private Long salesmanId;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private String approveRemark;
    private String cancelReason;
    private String attachmentUrls;
    private String remark;
    private Integer changeUnlocked;
    /** 外协类型: null=自制, full=整单外协 */
    private String outsourceType;
    /** 扩展字段（JSON格式，由biz_order_field_config定义） */
    private String extFields;
    /** 是否已加入订单主计划: 0=未加入 1=已加入 */
    private Integer masterPlanAdded;
    /** 加入主计划时间 */
    private LocalDateTime masterPlanAddedAt;
}
