package com.aimes.modules.master.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_material_category")
public class BizMaterialCategory extends BaseEntity {
    private Long parentId;
    private String categoryName;
    private String categoryCode;
    private Integer sortOrder;
    private String status;

    @TableField(exist = false)
    private List<BizMaterialCategory> children;
}
