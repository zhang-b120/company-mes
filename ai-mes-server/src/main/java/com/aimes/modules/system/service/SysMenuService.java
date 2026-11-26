package com.aimes.modules.system.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.modules.system.entity.SysMenu;
import com.aimes.modules.system.mapper.SysMenuMapper;
import com.aimes.modules.system.mapper.SysRoleMenuMapper;
import com.aimes.modules.system.entity.SysRoleMenu;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysMenuService extends ServiceImpl<SysMenuMapper, SysMenu> {

    private final SysRoleMenuMapper roleMenuMapper;

    public List<Map<String, Object>> getMenuTree(String platform) {
        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<SysMenu>()
                .orderByAsc(SysMenu::getSortOrder);
        if (platform != null && !platform.isEmpty()) {
            wrapper.and(w -> w.eq(SysMenu::getPlatform, platform).or().eq(SysMenu::getPlatform, "both"));
        }
        List<SysMenu> menus = list(wrapper);
        return buildTree(menus, 0L);
    }

    private List<Map<String, Object>> buildTree(List<SysMenu> menus, Long parentId) {
        return menus.stream()
                .filter(m -> Objects.equals(m.getParentId(), parentId))
                .map(m -> {
                    Map<String, Object> node = new LinkedHashMap<>();
                    node.put("id", m.getId());
                    node.put("parentId", m.getParentId());
                    node.put("menuName", m.getMenuName());
                    node.put("menuType", m.getMenuType());
                    node.put("platform", m.getPlatform());
                    node.put("path", m.getPath());
                    node.put("component", m.getComponent());
                    node.put("icon", m.getIcon());
                    node.put("permission", m.getPermission());
                    node.put("sortOrder", m.getSortOrder());
                    node.put("visible", m.getVisible());
                    node.put("status", m.getStatus());
                    node.put("children", buildTree(menus, m.getId()));
                    return node;
                }).collect(Collectors.toList());
    }

    public List<Map<String, Object>> getMenuOptions() {
        List<SysMenu> menus = list(new LambdaQueryWrapper<SysMenu>()
                .in(SysMenu::getMenuType, "D", "M")
                .orderByAsc(SysMenu::getSortOrder));
        return buildOptionTree(menus, 0L);
    }

    private List<Map<String, Object>> buildOptionTree(List<SysMenu> menus, Long parentId) {
        return menus.stream()
                .filter(m -> Objects.equals(m.getParentId(), parentId))
                .map(m -> {
                    Map<String, Object> node = new LinkedHashMap<>();
                    node.put("id", m.getId());
                    node.put("menuName", m.getMenuName());
                    node.put("children", buildOptionTree(menus, m.getId()));
                    return node;
                }).collect(Collectors.toList());
    }

    public void createMenu(SysMenu menu) {
        // 校验同层级菜单名不重复
        long count = count(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getParentId, menu.getParentId())
                .eq(SysMenu::getMenuName, menu.getMenuName()));
        if (count > 0) {
            throw new BusinessException("同层级已存在相同名称的菜单");
        }
        save(menu);
    }

    public void updateMenu(Long id, SysMenu menu) {
        menu.setId(id);
        long count = count(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getParentId, menu.getParentId())
                .eq(SysMenu::getMenuName, menu.getMenuName())
                .ne(SysMenu::getId, id));
        if (count > 0) {
            throw new BusinessException("同层级已存在相同名称的菜单");
        }
        updateById(menu);
    }

    @Transactional
    public void deleteMenu(Long id) {
        long childCount = count(new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getParentId, id));
        if (childCount > 0) {
            throw new BusinessException("请先删除子菜单");
        }
        removeById(id);
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getMenuId, id));
    }
}
