package com.aimes.modules.quality.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_qc_scheme")
public class BizQcScheme extends BaseEntity {
    private String schemeName;
    private String schemeType;       // sampling(抽检) / full(全检)
    private String samplingMode;     // first_piece / last_piece / custom_qty / ratio
    private Integer samplingQty;     // 抽检数量
    private BigDecimal samplingRatio;// 抽检比例(%)
    private Integer ratioRoundUp;    // 1=向上取整
    private String status;
    private String remark;
}
