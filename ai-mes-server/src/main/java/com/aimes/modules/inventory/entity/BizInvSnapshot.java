package com.aimes.modules.inventory.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_inv_snapshot")
public class BizInvSnapshot extends BaseEntity {
    private LocalDate snapshotDate;
    private Integer recordCount;
    private String snapshotData;
}
