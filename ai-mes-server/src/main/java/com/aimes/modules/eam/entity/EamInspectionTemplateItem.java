package com.aimes.modules.eam.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eam_inspection_template_item")
public class EamInspectionTemplateItem extends BaseEntity {
    private Long templateId;
    private Long itemId;
    private String itemName;
    private String standard;
    private String method;
    private Integer isRequired;
    private Integer sortOrder;
}
