package com.aimes.modules.system.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.PageResult;
import com.aimes.modules.system.entity.SysRole;
import com.aimes.modules.system.entity.SysRoleDataScope;
import com.aimes.modules.system.entity.SysRoleMenu;
import com.aimes.modules.system.mapper.SysRoleDataScopeMapper;
import com.aimes.modules.system.mapper.SysRoleMapper;
import com.aimes.modules.system.mapper.SysRoleMenuMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysRoleService extends ServiceImpl<SysRoleMapper, SysRole> {

    private final SysRoleMenuMapper roleMenuMapper;
    private final SysRoleDataScopeMapper roleDataScopeMapper;

    public PageResult<Map<String, Object>> getPage(String keyword, int pageNum, int pageSize) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<SysRole>()
                .like(StringUtils.hasText(keyword), SysRole::getRoleName, keyword)
                .orderByAsc(SysRole::getSortOrder);
        Page<SysRole> page = page(new Page<>(pageNum, pageSize), wrapper);

        List<Map<String, Object>> list = page.getRecords().stream().map(r -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", r.getId());
            map.put("roleName", r.getRoleName());
            map.put("roleCode", r.getRoleCode());
            map.put("description", r.getDescription());
            map.put("isPreset", r.getIsPreset());
            map.put("status", r.getStatus());
            map.put("dataScope", r.getDataScope());
            map.put("userCount", baseMapper.countUsersByRoleId(r.getId()));
            return map;
        }).collect(Collectors.toList());

        return new PageResult<>(list, page.getTotal(), pageNum, pageSize);
    }

    public void createRole(SysRole role) {
        long count = count(new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, role.getRoleCode()));
        if (count > 0) {
            throw new BusinessException("角色编码已存在");
        }
        if (role.getDataScope() == null) role.setDataScope("all");
        save(role);
    }

    public void updateRole(Long id, SysRole role) {
        SysRole existing = getById(id);
        if (existing == null) throw new BusinessException("角色不存在");
        if (existing.getIsPreset() == 1 && !existing.getRoleCode().equals(role.getRoleCode())) {
            throw new BusinessException("预设角色不可修改编码");
        }
        role.setId(id);
        updateById(role);
    }

    public void deleteRole(Long id) {
        SysRole role = getById(id);
        if (role == null) throw new BusinessException("角色不存在");
        if (role.getIsPreset() == 1) throw new BusinessException("预设角色不可删除");
        int userCount = baseMapper.countUsersByRoleId(id);
        if (userCount > 0) {
            throw new BusinessException("该角色下有" + userCount + "名用户, 请先转移用户");
        }
        removeById(id);
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, id));
        roleDataScopeMapper.delete(new LambdaQueryWrapper<SysRoleDataScope>().eq(SysRoleDataScope::getRoleId, id));
    }

    public List<Long> getRoleMenuIds(Long roleId) {
        return roleMenuMapper.selectList(
                new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId))
                .stream().map(SysRoleMenu::getMenuId).collect(Collectors.toList());
    }

    @Transactional
    public void saveRoleMenus(Long roleId, List<Long> menuIds) {
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        if (menuIds != null && !menuIds.isEmpty()) {
            for (Long menuId : menuIds) {
                SysRoleMenu rm = new SysRoleMenu();
                rm.setRoleId(roleId);
                rm.setMenuId(menuId);
                roleMenuMapper.insert(rm);
            }
        }
    }

    // ==================== 数据权限 ====================

    public Map<String, Object> getRoleDataScope(Long roleId) {
        SysRole role = getById(roleId);
        if (role == null) throw new BusinessException("角色不存在");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dataScope", role.getDataScope() != null ? role.getDataScope() : "all");

        List<SysRoleDataScope> scopes = roleDataScopeMapper.selectList(
                new LambdaQueryWrapper<SysRoleDataScope>().eq(SysRoleDataScope::getRoleId, roleId));

        Map<String, List<String>> scopeMap = scopes.stream()
                .collect(Collectors.groupingBy(SysRoleDataScope::getScopeType,
                        Collectors.mapping(SysRoleDataScope::getScopeValue, Collectors.toList())));
        result.put("customUserIds", scopeMap.getOrDefault("user", Collections.emptyList()));
        result.put("customWorkshops", scopeMap.getOrDefault("workshop", Collections.emptyList()));

        return result;
    }

    @Transactional
    public void saveRoleDataScope(Long roleId, Map<String, Object> data) {
        SysRole role = getById(roleId);
        if (role == null) throw new BusinessException("角色不存在");

        String dataScope = data.get("dataScope") != null ? data.get("dataScope").toString() : "all";
        role.setDataScope(dataScope);
        updateById(role);

        roleDataScopeMapper.delete(new LambdaQueryWrapper<SysRoleDataScope>().eq(SysRoleDataScope::getRoleId, roleId));

        if ("custom".equals(dataScope)) {
            @SuppressWarnings("unchecked")
            List<Object> userIds = (List<Object>) data.get("customUserIds");
            if (userIds != null) {
                for (Object uid : userIds) {
                    SysRoleDataScope scope = new SysRoleDataScope();
                    scope.setRoleId(roleId);
                    scope.setScopeType("user");
                    scope.setScopeValue(uid.toString());
                    roleDataScopeMapper.insert(scope);
                }
            }
            @SuppressWarnings("unchecked")
            List<Object> workshops = (List<Object>) data.get("customWorkshops");
            if (workshops != null) {
                for (Object ws : workshops) {
                    SysRoleDataScope scope = new SysRoleDataScope();
                    scope.setRoleId(roleId);
                    scope.setScopeType("workshop");
                    scope.setScopeValue(ws.toString());
                    roleDataScopeMapper.insert(scope);
                }
            }
        }
    }
}
