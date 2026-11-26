package com.aimes.modules.system.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_warehouse")
public class SysWarehouse extends BaseEntity {
    private Long parentId;
    private String warehouseName;
    private String warehouseType;
    private String address;
    private Long managerId;
    private String managerIds;
    private String status;
    private String remark;
}
