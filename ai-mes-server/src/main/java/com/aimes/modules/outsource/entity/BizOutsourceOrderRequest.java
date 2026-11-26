package com.aimes.modules.outsource.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_outsource_order_request")
public class BizOutsourceOrderRequest extends BaseEntity {
    private Long outsourceOrderId;
    private Long outsourceRequestId;
}
