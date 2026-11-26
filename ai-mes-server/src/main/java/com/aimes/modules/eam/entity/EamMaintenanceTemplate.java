package com.aimes.modules.eam.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eam_maintenance_template")
public class EamMaintenanceTemplate extends BaseEntity {
    private String templateName;
    private String equipmentType;
    /** DAY/WEEK/MONTH/HOUR */
    private String cycleType;
    private Integer cycleValue;
    private String remark;
}
