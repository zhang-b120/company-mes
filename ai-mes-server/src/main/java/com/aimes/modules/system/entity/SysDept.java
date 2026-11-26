package com.aimes.modules.system.entity;

import com.aimes.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dept")
public class SysDept extends BaseEntity {
    /** 父部门ID，0=顶级 */
    private Long parentId;
    /** 部门名称 */
    private String deptName;
    /** 部门负责人ID */
    private Long managerId;
    /** 显示排序 */
    private Integer sortOrder;
    /** 状态 active/disabled */
    private String status;
    /** 备注 */
    private String remark;
}
