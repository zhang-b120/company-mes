package com.aimes.modules.inventory.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_inv_task")
public class BizInvTask extends BaseEntity {
    private String taskNo;
    private String batchNo;
    private String relatedOrderNo;
    private String invType;
    private Long warehouseId;
    private Long locationId;
    private String status;
    private String sourceType;
    private Long sourceId;
    private Long applicantId;
    private String remark;
    private String returnReason;
}
