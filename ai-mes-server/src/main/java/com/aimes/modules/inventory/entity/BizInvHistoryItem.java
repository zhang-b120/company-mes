package com.aimes.modules.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("biz_inv_history_item")
public class BizInvHistoryItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long historyId;
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
    private Long taskItemId; // 关联库存任务明细ID，用于精确撤销还原
    private String secondaryUnit;
    private BigDecimal secondaryQty;
    private String status; // normal / revoked
    @TableLogic
    private Integer deleted;
}
