package com.aimes.modules.system.service;

import com.aimes.modules.system.entity.SysWarehouse;
import com.aimes.modules.system.mapper.SysUserMapper;
import com.aimes.modules.system.mapper.SysWarehouseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysWarehouseService extends ServiceImpl<SysWarehouseMapper, SysWarehouse> {

    private final SysUserMapper userMapper;

    /**
     * 仓库树（顶级仓库 + 其下库位）
     */
    public List<Map<String, Object>> getTree() {
        List<SysWarehouse> all = list(new LambdaQueryWrapper<SysWarehouse>()
                .orderByAsc(SysWarehouse::getCreateTime));

        // 批量加载管理员姓名
        Set<Long> mgrIds = new HashSet<>();
        for (SysWarehouse w : all) {
            if (w.getManagerIds() != null && !w.getManagerIds().isBlank()) {
                for (String s : w.getManagerIds().split(",")) {
                    try { mgrIds.add(Long.parseLong(s.trim())); } catch (Exception ignored) {}
                }
            }
        }
        Map<Long, String> userNameMap = new HashMap<>();
        if (!mgrIds.isEmpty()) {
            userMapper.selectBatchIds(mgrIds).forEach(u -> userNameMap.put(u.getId(), u.getRealName()));
        }

        return buildTree(all, 0L, userNameMap);
    }

    private List<Map<String, Object>> buildTree(List<SysWarehouse> all, Long parentId,
                                                Map<Long, String> userNameMap) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysWarehouse w : all) {
            if (Objects.equals(w.getParentId() == null ? 0L : w.getParentId(), parentId)) {
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("id", w.getId());
                node.put("parentId", w.getParentId() == null ? 0L : w.getParentId());
                node.put("warehouseName", w.getWarehouseName());
                node.put("warehouseType", w.getWarehouseType());
                node.put("address", w.getAddress());
                node.put("managerId", w.getManagerId());
                node.put("managerIds", w.getManagerIds());
                // 解析 managerIds 得到姓名列表
                String managerNames = "";
                if (StringUtils.hasText(w.getManagerIds())) {
                    managerNames = Arrays.stream(w.getManagerIds().split(","))
                            .map(s -> {
                                try { return userNameMap.getOrDefault(Long.parseLong(s.trim()), s.trim()); }
                                catch (Exception e) { return s.trim(); }
                            })
                            .collect(Collectors.joining(", "));
                }
                node.put("managerNames", managerNames);
                node.put("status", w.getStatus());
                node.put("remark", w.getRemark());
                node.put("createTime", w.getCreateTime());
                // 递归子级
                List<Map<String, Object>> children = buildTree(all, w.getId(), userNameMap);
                node.put("children", children.isEmpty() ? null : children);
                result.add(node);
            }
        }
        return result;
    }

    /**
     * 仓库下拉列表（只返回顶级仓库，parentId=0）
     */
    public List<Map<String, Object>> getSelectList() {
        List<SysWarehouse> warehouses = list(new LambdaQueryWrapper<SysWarehouse>()
                .eq(SysWarehouse::getStatus, "active")
                .eq(SysWarehouse::getParentId, 0L)
                .orderByAsc(SysWarehouse::getWarehouseName));

        return warehouses.stream().map(w -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", w.getId());
            map.put("name", w.getWarehouseName());
            map.put("warehouseName", w.getWarehouseName());
            return map;
        }).collect(Collectors.toList());
    }

    /**
     * 获取指定仓库的库位列表
     */
    public List<Map<String, Object>> getLocationsByWarehouseId(Long warehouseId) {
        List<SysWarehouse> locations = list(new LambdaQueryWrapper<SysWarehouse>()
                .eq(SysWarehouse::getParentId, warehouseId)
                .orderByAsc(SysWarehouse::getCreateTime));

        return locations.stream().map(w -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", w.getId());
            map.put("name", w.getWarehouseName());
            map.put("warehouseName", w.getWarehouseName());
            return map;
        }).collect(Collectors.toList());
    }

    /**
     * 仓库旧列表接口（兼容，返回全部含库位）
     */
    public List<SysWarehouse> listAll() {
        return list(new LambdaQueryWrapper<SysWarehouse>()
                .orderByAsc(SysWarehouse::getCreateTime));
    }
}
