package com.aimes.modules.eam.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eam_maintenance_task_detail")
public class EamMaintenanceTaskDetail extends BaseEntity {
    private Long taskId;
    private Long equipmentId;
    private Long templateItemId;
    private String itemGroup;
    private String itemName;
    private String method;
    private String standard;
    private String uom;
    private Integer required;
    private Integer needPhoto;
    private Integer sortNo;
    /** 0待检 1已完成 2异常 */
    private Integer status;
    private String resultValue;
    private String photoUrl;
    private String remark;
}
