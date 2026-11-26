package com.aimes.modules.eam.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eam_inspection_task_detail")
public class EamInspectionTaskDetail extends BaseEntity {
    private Long taskId;
    private Long templateItemId;
    private String itemName;
    private String standard;
    private String method;
    private Integer isRequired;
    private Integer sortOrder;
    /** 0待检 1已完成 2异常 */
    private Integer status;
    private String resultValue;
    private String photoUrl;
    private String remark;
}
