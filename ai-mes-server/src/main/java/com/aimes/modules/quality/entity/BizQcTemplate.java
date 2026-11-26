package com.aimes.modules.quality.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_qc_template")
public class BizQcTemplate extends BaseEntity {
    private String templateName;
    private String templateCode;
    private String applyType;
    private Long applyProductId;
    private Long applyMaterialId;
    private Long schemeId;              // 关联质检方案ID
    @TableField(exist = false)
    private String sampleMode;          // 弃用，改用质检方案
    @TableField(exist = false)
    private BigDecimal sampleRate;      // 弃用
    @TableField(exist = false)
    private String aqlLevel;            // 弃用
    private Integer isDefault;
    private String status;
    private String remark;
}
