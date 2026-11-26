package com.aimes.modules.workreport.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_work_report")
public class BizWorkReport extends BaseEntity {
    private String reportNo;
    private Long taskId;
    private Long workOrderId;
    private Long reporterId;
    private String reportType;
    private BigDecimal qualifiedQty;
    private BigDecimal defectQty;
    private String defectReasons;
    private String equipmentId;
    private String batchNo;
    private BigDecimal workHours;
    private String photoUrls;
    private BigDecimal pieceWage;
    /** 工资模式: normal=正常(比例1), three_machine=三机(按工序工资比例×0.9) */
    private String wageMode;
    private String status;
    private String remark;
    /** 驳回原因 */
    private String rejectReason;
    /** 关联班组报工ID（reportType=team时有值） */
    private Long teamReportId;
}
