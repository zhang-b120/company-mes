package com.aimes.modules.bom.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 工序库（工序定义主数据）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_process")
public class BizProcess extends BaseEntity {
    private String processName;
    private Integer standardTime;
    private BigDecimal piecePrice;
    private String equipmentIds;
    private Long teamId;
    private String bindType;  // none/team/employee
    private String bindIds;   // JSON array of IDs
    private String sopDescription;
    private String sopAttachmentUrls;
    private Integer isSemiStock;
    private Integer isOutsourceable;
    private Long qcTemplateId;
}
