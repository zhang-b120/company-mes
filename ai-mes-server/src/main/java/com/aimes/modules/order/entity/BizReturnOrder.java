package com.aimes.modules.order.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_return_order")
public class BizReturnOrder extends BaseEntity {
    private String returnNo;
    private Long orderId;
    private String orderNo;
    private Long customerId;
    private String customerName;
    private LocalDate returnDate;
    private String returnReason;
    /** 状态: pending=待审批, approved=已审批, received=已收货, completed=已完成, rejected=已驳回 */
    private String status;
    private String remark;
}
