package com.aimes.modules.inventory.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_inv_transaction")
public class BizInvTransaction extends BaseEntity {
    private Long materialId;
    private Long warehouseId;
    private String transactionType;
    private BigDecimal quantity;
    private String batchNo;
    private String relatedOrderType;
    private Long relatedOrderId;
    private String remark;
    private String secondaryUnit;
    private BigDecimal secondaryQty;
}
