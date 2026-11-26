package com.aimes.modules.inventory.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_inv_request")
public class BizInvRequest extends BaseEntity {
    private String requestNo;
    private String batchNo;
    private String relatedOrderNo;
    private String invType;
    private Long customerId;
    private String customerName;
    private Long warehouseId;
    private Long locationId;
    private String status;
    private Long applicantId;
    private String remark;
    /** 附件 URL 列表,JSON 数组 [{name, url}] */
    private String attachmentUrls;
}
