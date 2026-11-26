package com.aimes.modules.eam.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eam_inspection_template")
public class EamInspectionTemplate extends BaseEntity {
    private String templateName;
    private String equipmentType;
    /** 0日常 1定期 2专项 */
    private Integer inspectionType;
    private String status;
    private String remark;
}
