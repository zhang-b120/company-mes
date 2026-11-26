package com.aimes.modules.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_order_field_config")
public class BizOrderFieldConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 字段Key（英文，存入ext_fields的JSON key） */
    private String fieldKey;
    /** 字段显示名称 */
    private String fieldLabel;
    /** 字段类型：text / number / date / select */
    private String fieldType;
    /** 下拉选项，JSON数组字符串，如 ["A","B"] */
    private String fieldOptions;
    /** 是否在列表中显示为列 */
    private Integer showInList;
    /** 是否在新增/编辑表单中显示 */
    private Integer showInForm;
    /** 是否必填 */
    private Integer required;
    /** 排序 */
    private Integer sortOrder;
    /** 是否启用 */
    private Integer enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
