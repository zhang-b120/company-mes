package com.aimes.modules.bom.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_bom_substitute")
public class BizBomSubstitute extends BaseEntity {
    private String groupName;
    private Long bomItemId;
    private Long materialId;
    private Integer priority;
    private BigDecimal substituteRatio;
    private String remark;
}
