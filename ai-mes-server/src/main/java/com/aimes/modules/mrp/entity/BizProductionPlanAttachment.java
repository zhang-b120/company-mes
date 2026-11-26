package com.aimes.modules.mrp.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_production_plan_attachment")
public class BizProductionPlanAttachment extends BaseEntity {
    private Long planId;
    private String planNo;
    private String originalName;
    private String serverName;
    private String filePath;
    private String fileUrl;
    private Long fileSize;
    private String fileType;
}
