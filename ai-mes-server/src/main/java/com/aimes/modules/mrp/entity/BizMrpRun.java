package com.aimes.modules.mrp.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_mrp_run")
public class BizMrpRun extends BaseEntity {
    private String runNo;
    private String runType;
    private LocalDateTime runTime;
    private String status;
    private Integer orderCount;
    private Integer resultCount;
    private String errorMsg;
    private String remark;
}
