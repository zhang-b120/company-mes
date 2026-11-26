package com.aimes.modules.purchase.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_purchase_receipt")
public class BizPurchaseReceipt extends BaseEntity {
    private String receiptNo;
    /** 收料批次组ID: 同一次批量收料的多条 receipt 共享同一个值, 用于列表折叠 + 打印对账单 */
    private String receiptGroupId;
    private Long purchaseOrderId;
    private Long orderItemId;
    private Long materialId;
    private BigDecimal expectedQty;
    private BigDecimal actualQty;
    private String batchNo;
    private LocalDate receivedDate;
    private Long receiverId;
    private Long warehouseId;
    private Long qcTaskId;
    private String status;          // pending_receive, receiving, received, closed
    private String qcStatus;        // none, pending_qc, qc_done
    private BigDecimal qualifiedQty;
    private BigDecimal defectQty;
    private BigDecimal invTaskQty;
    private BigDecimal stockInQty;
    private String remark;
}
