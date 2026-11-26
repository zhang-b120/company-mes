package com.aimes.modules.system.mapper;

import com.aimes.modules.system.entity.SysDept;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysDeptMapper extends BaseMapper<SysDept> {

    /** 查询某部门下所有子部门ID（含自身），用于数据权限范围计算 */
    @Select("""
        WITH RECURSIVE dept_tree AS (
            SELECT id FROM sys_dept WHERE id = #{deptId} AND deleted = 0
            UNION ALL
            SELECT d.id FROM sys_dept d
            INNER JOIN dept_tree dt ON d.parent_id = dt.id
            WHERE d.deleted = 0
        )
        SELECT id FROM dept_tree
        """)
    List<Long> selectAllDescendantIds(Long deptId);
}
