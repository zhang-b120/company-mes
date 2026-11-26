package com.aimes.modules.eam.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eam_maintenance_template_item")
public class EamMaintenanceTemplateItem extends BaseEntity {
    private Long templateId;
    private String itemGroup;
    private String itemName;
    private String method;
    private String standard;
    private String uom;
    private Integer required;
    private Integer needPhoto;
    private Integer sortNo;
}
