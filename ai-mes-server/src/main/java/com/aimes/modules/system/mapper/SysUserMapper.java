package com.aimes.modules.system.mapper;

import com.aimes.modules.system.entity.SysUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("SELECT r.role_code FROM sys_role r JOIN sys_user_role ur ON r.id = ur.role_id WHERE ur.user_id = #{userId} AND r.deleted = 0")
    List<String> selectRoleCodes(@Param("userId") Long userId);

    @Select("SELECT r.role_name FROM sys_role r JOIN sys_user_role ur ON r.id = ur.role_id WHERE ur.user_id = #{userId} AND r.deleted = 0")
    List<String> selectRoleNames(@Param("userId") Long userId);

    @Select("SELECT ur.role_id FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id WHERE ur.user_id = #{userId} AND r.deleted = 0")
    List<Long> selectRoleIds(@Param("userId") Long userId);

    @Select("SELECT DISTINCT m.permission FROM sys_menu m JOIN sys_role_menu rm ON m.id = rm.menu_id JOIN sys_user_role ur ON rm.role_id = ur.role_id WHERE ur.user_id = #{userId} AND m.menu_type = 'B' AND m.deleted = 0 AND m.permission IS NOT NULL AND m.permission != ''")
    List<String> selectPermissions(@Param("userId") Long userId);

    /** 检查用户名是否存在（包含已软删除记录，绕过 @TableLogic） */
    @Select("SELECT COUNT(*) FROM sys_user WHERE username = #{username}")
    int countByUsernameAll(@Param("username") String username);

    /** 查找已软删除的用户（用于重新入职恢复） */
    @Select("SELECT * FROM sys_user WHERE username = #{username} AND deleted = 1 LIMIT 1")
    SysUser selectDeletedByUsername(@Param("username") String username);

    /** 恢复已删除用户（将 deleted 置为 0） */
    @org.apache.ibatis.annotations.Update("UPDATE sys_user SET deleted = 0 WHERE id = #{id}")
    int restoreById(@Param("id") Long id);
}
