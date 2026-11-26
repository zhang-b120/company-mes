package com.aimes.modules.eam.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eam_inspection_template_equipment")
public class EamInspectionTemplateEquipment extends BaseEntity {
    private Long templateId;
    private Long equipmentId;
}
