package com.aimes.modules.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_role_data_scope")
public class SysRoleDataScope {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long roleId;
    private String scopeType;
    private String scopeValue;
}
