package com.aimes.modules.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("biz_inv_task_item")
public class BizInvTaskItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long materialId;
    private String materialNo;
    private String materialName;
    private String spec;
    private String unit;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
    private BigDecimal processedQty;
    private Long warehouseId;
    private Long locationId;
    private Long receiptId;
    private String secondaryUnit;
    private BigDecimal secondaryQty;
    private BigDecimal secondaryProcessedQty;
    /** 替代料：被替代的主料ID（为空表示本行是主料本身） */
    private Long substituteForMaterialId;
    /** 替代料：每1主料需领取的替代料数量（用于任务完成时把已领量按比例折算回主料） */
    private BigDecimal substituteRatio;
    @TableLogic
    private Integer deleted;
}
