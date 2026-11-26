package com.aimes.modules.inventory.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 二维码标签模板实体
 * 用于定义库存物料二维码标签的打印模板，包含标签尺寸、二维码字段和打印字段等配置
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_qr_template")
public class BizQrTemplate extends BaseEntity {

    /** 模板名称 */
    private String templateName;

    /** 标签宽度（mm） */
    private Integer labelWidth;

    /** 标签高度（mm） */
    private Integer labelHeight;

    /** 二维码包含的字段配置（JSON格式） */
    private String qrFields;

    /** 打印显示的字段配置（JSON格式） */
    private String printFields;

    /** 是否默认模板：1-是，0-否 */
    private Integer isDefault;
}
