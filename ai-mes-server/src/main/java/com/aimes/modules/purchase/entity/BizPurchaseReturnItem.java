package com.aimes.modules.purchase.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("biz_purchase_return_item")
public class BizPurchaseReturnItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long returnId;
    private Long materialId;
    private BigDecimal quantity;
    /** 含税退货单价 */
    private BigDecimal unitPrice;
    private String remark;
    @TableLogic
    private Integer deleted;
}
