package com.aimes.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业务模块配置项
 */
@Data
@TableName("sys_biz_config")
public class SysBizConfig {

    private Long id;

    /** 业务模块: sales / purchase / production / workreport / warehouse / quality */
    private String module;

    /** 全局唯一配置键，如 sales.order.auto_no */
    private String configKey;

    /** 配置项名称（前端展示） */
    private String configName;

    /** 配置说明 */
    private String description;

    /** 值类型: switch / radio / checkbox / number / text */
    private String valueType;

    /** 选项列表 JSON，radio/checkbox 时使用，格式: [{"label":"...","value":"..."}] */
    private String options;

    /** 默认值 */
    private String defaultValue;

    /** 当前配置值（null 时取 defaultValue） */
    private String configValue;

    /** 排序 */
    private Integer sortOrder;

    /** 最后修改人 */
    private String updateBy;

    /** 最后修改时间 */
    private LocalDateTime updateTime;

    private LocalDateTime createTime;

    private Integer deleted;

    /** 获取有效值（优先 configValue，fallback defaultValue） */
    public String getEffectiveValue() {
        return configValue != null ? configValue : defaultValue;
    }
}
