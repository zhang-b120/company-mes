package com.aimes.modules.quality.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_qc_result")
public class BizQcResult extends BaseEntity {
    private Long qcTaskId;
    private Long templateItemId;
    private String itemName;
    private String standardValue;
    private String toleranceMin;
    private String toleranceMax;
    private String unit;
    private String method;
    private String itemType;
    private String defaultValue;
    private Integer isRequired;
    private Integer isCritical;
    private String actualValue;
    private Integer isPass;
    private String photoUrl;
    private String remark;
}
