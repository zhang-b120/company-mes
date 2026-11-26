package com.aimes.common.extfield;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通用扩展字段配置
 * 支持任意业务模块通过 JSON ext_fields 字段进行动态扩展
 */
@Data
@TableName("biz_ext_field_config")
public class BizExtFieldConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 业务模块标识：sales_order / customer / supplier / purchase_order / work_order */
    private String module;
    /** 字段Key（英文，存入 ext_fields 的 JSON key） */
    private String fieldKey;
    /** 字段显示名称 */
    private String fieldLabel;
    /** 字段类型：text / number / date / select */
    private String fieldType;
    /** 下拉选项 JSON 数组字符串，如 ["A","B"]（仅 select 类型使用） */
    private String fieldOptions;
    /** 是否在列表中显示为列 */
    private Integer showInList;
    /** 是否在新增/编辑表单中显示 */
    private Integer showInForm;
    /** 是否作为查询条件 */
    private Integer showInSearch;
    /** 是否必填 */
    private Integer required;
    /** 排序 */
    private Integer sortOrder;
    /** 是否启用 */
    private Integer enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
