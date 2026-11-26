package com.aimes.modules.master.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 物料规格名称表
 * 存储规格维度的名称（如：材质、尺寸、颜色等）
 * 导入物料时，Excel 动态表头中的规格名称与此表进行匹配
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_material_spec")
public class BizMaterialSpec extends BaseEntity {
    /** 规格名称（如：材质、尺寸、颜色） */
    private String specName;
    /** 排序号 */
    private Integer sortOrder;
    /** 状态 active/disabled */
    private String status;
}
