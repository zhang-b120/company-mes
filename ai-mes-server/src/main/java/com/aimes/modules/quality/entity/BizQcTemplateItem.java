package com.aimes.modules.quality.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_qc_template_item")
public class BizQcTemplateItem extends BaseEntity {
    private Long templateId;
    private String itemName;
    /** 检验类型: quantitative=计量型, text=文本型, radio=单选项 */
    private String itemType;
    /** 计量型: 标准值 */
    private String standardValue;
    /** 计量型: 下限值 */
    private String toleranceMin;
    /** 计量型: 上限值 */
    private String toleranceMax;
    /** 计量型: 单位 */
    private String unit;
    /** 检验要求/方法 */
    private String method;
    /** 默认值(计量型/文本型使用) */
    private String defaultValue;
    /** 是否必填: 0=否 1=是 */
    private Integer isRequired;
    private Integer isCritical;
    private Integer sortOrder;
}
