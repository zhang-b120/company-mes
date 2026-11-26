package com.aimes.modules.system.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {
    private String username;
    private String phone;
    private String password;
    private String realName;
    /** 工位机IC卡号(刷卡免密登录使用,全局唯一) */
    private String icCardNo;
    private String avatarUrl;
    private Integer sex;
    /** 所属部门ID（关联 sys_dept.id） */
    private Long deptId;
    private String workshop;
    private LocalDate hireDate;
    private String status;
    private Integer loginFailCount;
    private LocalDateTime lockUntil;
    private LocalDateTime lastLoginAt;
    private String remark;
}
