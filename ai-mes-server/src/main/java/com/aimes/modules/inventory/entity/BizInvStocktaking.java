package com.aimes.modules.inventory.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 仓库盘点单
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_inv_stocktaking")
public class BizInvStocktaking extends BaseEntity {
    /** 盘点单号 */
    private String stocktakingNo;
    /** 仓库ID */
    private Long warehouseId;
    /** 状态: pending-待审批, approved-已通过, rejected-已驳回 */
    private String status;
    /** 申请人ID */
    private Long applicantId;
    /** 审批备注 */
    private String approveRemark;
    /** 备注 */
    private String remark;
}
