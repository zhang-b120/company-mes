package com.aimes.modules.eam.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eam_inspection_item")
public class EamInspectionItem extends BaseEntity {
    private String itemCode;
    private String itemName;
    private String itemType;
    private String standard;
    private String method;
    private Integer isRequired;
    private String status;
    private String remark;
}
