package com.aimes.modules.outsource.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_outsource_receipt")
public class BizOutsourceReceipt extends BaseEntity {
    private String receiptNo;
    private Long outsourceOrderId;
    private BigDecimal receivedQty;
    private String batchNo;
    private LocalDate receivedDate;
    private Long receiverId;
    private Long qcTaskId;
    private String status;          // pending_receive / receiving / received / closed
    private String qcStatus;        // none / pending_qc / qc_done
    private BigDecimal qualifiedQty;
    private BigDecimal defectQty;
    private BigDecimal invTaskQty;
    private BigDecimal stockInQty;
    private Long warehouseId;
    private String remark;
}
