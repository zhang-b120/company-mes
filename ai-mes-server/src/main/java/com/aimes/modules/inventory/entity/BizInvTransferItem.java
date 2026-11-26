package com.aimes.modules.inventory.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_inv_transfer_item")
public class BizInvTransferItem extends BaseEntity {
    private Long transferId;
    private Long materialId;
    private String materialNo;
    private String materialName;
    private String spec;
    private String unit;
    private BigDecimal quantity;
}
