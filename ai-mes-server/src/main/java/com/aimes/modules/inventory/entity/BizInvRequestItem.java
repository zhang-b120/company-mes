package com.aimes.modules.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("biz_inv_request_item")
public class BizInvRequestItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long requestId;
    private Long materialId;
    private String materialNo;
    private String materialName;
    private String spec;
    private String unit;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
    private Long warehouseId;
    private Long locationId;
    @TableLogic
    private Integer deleted;
}
