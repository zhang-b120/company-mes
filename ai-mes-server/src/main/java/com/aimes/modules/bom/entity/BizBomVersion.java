package com.aimes.modules.bom.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_bom_version")
public class BizBomVersion extends BaseEntity {
    private Long productId;
    private String versionNo;
    private String status;
    private BigDecimal totalMaterialCost;
}
