package com.aimes.modules.inventory.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_inv_transfer")
public class BizInvTransfer extends BaseEntity {
    private String transferNo;
    private Long fromWarehouseId;
    private Long fromLocationId;
    private Long toWarehouseId;
    private Long toLocationId;
    private String status;
    private Long applicantId;
    private String approveRemark;
    private String remark;
}
