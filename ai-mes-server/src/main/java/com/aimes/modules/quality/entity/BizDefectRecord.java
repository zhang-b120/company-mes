package com.aimes.modules.quality.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_defect_record")
public class BizDefectRecord extends BaseEntity {
    private String defectNo;
    private String sourceType;
    private Long sourceId;
    private Long productId;
    private Long materialId;
    private Long workOrderId;
    private String batchNo;
    private BigDecimal quantity;
    private String defectType;
    private String defectReason;
    private String defectLevel;
    private String handlePath;
    private String handleStatus;
    private String handleResult;
    private Long reworkWoId;
    private BigDecimal scrapQty;
    private BigDecimal scrapCost;
    private Long handlerId;
    private LocalDateTime handledAt;
    private String photoUrls;
    /** 关联质检任务ID */
    private Long qcTaskId;
    /** 关联工序任务ID */
    private Long processTaskId;
    /** 返工目标工序号 */
    private Integer reworkStepNo;
    /** 返工目标工序名称 */
    private String reworkStepName;
    /** 报废原因 */
    private String scrapReason;
    /** 处置备注 */
    private String handleRemark;
}
