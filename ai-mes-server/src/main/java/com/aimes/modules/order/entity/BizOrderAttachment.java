package com.aimes.modules.order.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_order_attachment")
public class BizOrderAttachment extends BaseEntity {
    private Long orderId;
    private String orderNo;
    private String originalName;
    private String serverName;
    private String filePath;
    private String fileUrl;
    private Long fileSize;
    private String fileType;
}
