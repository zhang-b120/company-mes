package com.aimes.modules.production.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_rework_order")
public class BizReworkOrder extends BaseEntity {
    private String reworkNo;
    private Long qcTaskId;
    private Long qcDisposalId;
    private Long workOrderId;
    private Long processTaskId;
    private Long productId;
    private Long materialId;
    private BigDecimal quantity;
    private String status;          // pending / in_progress / completed
    private String remark;
}
