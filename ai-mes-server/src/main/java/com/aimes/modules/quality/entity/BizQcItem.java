package com.aimes.modules.quality.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_qc_item")
public class BizQcItem extends BaseEntity {
    private String itemCode;
    private String itemName;
    private String category;       // dimension/appearance/performance/chemical/physical
    private String itemType;       // quantitative/qualitative
    private String method;
    private String status;         // active/inactive
    private String remark;
}
