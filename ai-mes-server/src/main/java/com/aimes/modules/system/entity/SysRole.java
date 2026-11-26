package com.aimes.modules.system.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class SysRole extends BaseEntity {
    private String roleName;
    private String roleCode;
    private String description;
    private Integer isPreset;
    private Integer sortOrder;
    private String status;
    /** 数据权限范围: all-全部, dept-本部门, self-仅自己, custom-自定义 */
    private String dataScope;
}
