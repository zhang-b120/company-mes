package com.aimes.modules.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class SysUserDTO {
    @NotBlank(message = "账号不能为空")
    private String username;
    @NotBlank(message = "姓名不能为空")
    private String realName;
    private String phone;
    private String password;
    /** 工位机IC卡号(刷卡免密登录使用,可选) */
    private String icCardNo;
    private List<Long> roleIds;
    private Long deptId;
    private List<Long> teamIds;
    private LocalDate hireDate;
    private String remark;
}
