package com.aimes.modules.system.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.modules.system.entity.SysDept;
import com.aimes.modules.system.entity.SysUser;
import com.aimes.modules.system.mapper.SysDeptMapper;
import com.aimes.modules.system.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysDeptService extends ServiceImpl<SysDeptMapper, SysDept> {

    private final SysUserMapper userMapper;

    /**
     * 获取部门树（前端树形展示用）
     */
    public List<Map<String, Object>> getDeptTree() {
        List<SysDept> all = list(new LambdaQueryWrapper<SysDept>().orderByAsc(SysDept::getSortOrder));
        // 批量加载负责人名称
        Set<Long> managerIds = all.stream().map(SysDept::getManagerId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> managerNameMap = new HashMap<>();
        if (!managerIds.isEmpty()) {
            userMapper.selectBatchIds(managerIds).forEach(u -> managerNameMap.put(u.getId(), u.getRealName()));
        }
        return buildTree(all, 0L, managerNameMap);
    }

    /**
     * 获取扁平列表（下拉选择用）
     */
    public List<Map<String, Object>> getDeptOptions() {
        List<SysDept> all = list(new LambdaQueryWrapper<SysDept>()
                .eq(SysDept::getStatus, "active")
                .orderByAsc(SysDept::getSortOrder));
        return all.stream().map(d -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("parentId", d.getParentId());
            m.put("deptName", d.getDeptName());
            return m;
        }).collect(Collectors.toList());
    }

    /**
     * 新增部门
     */
    @Transactional
    public void createDept(SysDept dept) {
        dept.setStatus("active");
        save(dept);
    }

    /**
     * 修改部门
     */
    @Transactional
    public void updateDept(Long id, SysDept dept) {
        SysDept existing = getById(id);
        if (existing == null) throw new BusinessException("部门不存在");
        // 防止将父部门设置为自身或子部门，避免循环
        if (id.equals(dept.getParentId())) throw new BusinessException("父部门不能是自身");
        dept.setId(id);
        updateById(dept);
    }

    /**
     * 删除部门（校验是否有子部门或关联用户）
     */
    @Transactional
    public void deleteDept(Long id) {
        // 是否有子部门
        long childCount = count(new LambdaQueryWrapper<SysDept>().eq(SysDept::getParentId, id));
        if (childCount > 0) throw new BusinessException("该部门下有子部门，不能删除");
        // 是否有关联用户
        long userCount = userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getDeptId, id));
        if (userCount > 0) throw new BusinessException("该部门下有用户，不能删除");
        removeById(id);
    }

    /**
     * 获取某部门及其所有子孙部门的ID列表（用于数据权限计算）
     */
    public List<Long> getAllDescendantIds(Long deptId) {
        if (deptId == null) return Collections.emptyList();
        return baseMapper.selectAllDescendantIds(deptId);
    }

    // ==================== 私有方法 ====================

    private List<Map<String, Object>> buildTree(List<SysDept> all, Long parentId,
                                                  Map<Long, String> managerNameMap) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysDept dept : all) {
            if (Objects.equals(dept.getParentId(), parentId)) {
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("id", dept.getId());
                node.put("parentId", dept.getParentId());
                node.put("deptName", dept.getDeptName());
                node.put("managerId", dept.getManagerId());
                node.put("managerName", managerNameMap.getOrDefault(dept.getManagerId(), ""));
                node.put("sortOrder", dept.getSortOrder());
                node.put("status", dept.getStatus());
                node.put("remark", dept.getRemark());
                node.put("createTime", dept.getCreateTime());
                List<Map<String, Object>> children = buildTree(all, dept.getId(), managerNameMap);
                node.put("children", children);
                result.add(node);
            }
        }
        return result;
    }
}
