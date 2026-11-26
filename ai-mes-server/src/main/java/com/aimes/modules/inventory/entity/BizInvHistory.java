package com.aimes.modules.inventory.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_inv_history")
public class BizInvHistory extends BaseEntity {
    private String historyNo;
    private Long taskId;
    private Long applicantId;
    private String taskNo;
    private String relatedOrderNo;
    private String invType;
    private String batchNo;
    private Long warehouseId;
    private Long locationId;
    private String status;
    private Long operatorId;
    private String remark;
}
