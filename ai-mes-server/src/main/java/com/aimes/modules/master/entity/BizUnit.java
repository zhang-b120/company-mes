package com.aimes.modules.master.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_unit")
public class BizUnit extends BaseEntity {
    private String unitName;
    private String unitCode;
    private Integer sortOrder;
    private String status;
}
