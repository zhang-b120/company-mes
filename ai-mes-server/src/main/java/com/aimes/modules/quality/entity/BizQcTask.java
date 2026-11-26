package com.aimes.modules.quality.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_qc_task")
public class BizQcTask extends BaseEntity {
    private String taskNo;
    private String qcType;
    private Long templateId;
    private Long productId;
    private Long materialId;
    private String batchNo;
    private String relatedOrderType;
    private Long relatedOrderId;
    private Long workOrderId;
    /** 关联工序任务ID(工序质检时) */
    private Long taskId;
    private BigDecimal planQty;
    private BigDecimal sampleQty;
    private BigDecimal passQty;
    private BigDecimal failQty;
    private String result;
    private Long inspectorId;
    private LocalDateTime inspectedAt;
    private String conclusion;
    private String disposalStatus;  // undisposed(未处置) / disposed(已处置)
    private String photoUrls;
    private String remark;
}
