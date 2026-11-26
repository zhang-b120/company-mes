package com.aimes.common.columnconfig;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户表格列配置
 * 每个用户可针对每个业务模块自定义列的显示/隐藏和排列顺序
 */
@Data
@TableName("biz_table_column_config")
public class BizTableColumnConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 用户ID */
    private Long userId;
    /** 业务模块：sales_order / work_order / purchase_order / customer / supplier / ... */
    private String module;
    /** 列标识（标准列如 orderNo，扩展列如 ext_customField） */
    private String columnKey;
    /** 列显示名称 */
    private String columnLabel;
    /** 列类型：standard / extension */
    private String columnType;
    /** 是否显示 */
    private Integer visible;
    /** 列顺序 */
    private Integer sortOrder;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
