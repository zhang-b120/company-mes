package com.aimes.modules.system.controller;

import com.aimes.common.result.R;
import com.aimes.modules.system.entity.SysWarehouse;
import com.aimes.modules.system.service.SysWarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 仓库管理控制器
 * 路径: /api/system/warehouses
 */
@RestController
@RequestMapping("/api/system/warehouses")
@RequiredArgsConstructor
public class SysWarehouseController {

    private final SysWarehouseService warehouseService;

    /**
     * 仓库树（顶级仓库 + 子库位）
     */
    @GetMapping("/tree")
    public R<List<Map<String, Object>>> tree() {
        return R.ok(warehouseService.getTree());
    }

    /**
     * 仓库列表（含库位，兼容旧接口）
     */
    @GetMapping("/list")
    public R<List<SysWarehouse>> list() {
        return R.ok(warehouseService.listAll());
    }

    /**
     * 仓库下拉选择（仅顶级active状态）
     */
    @GetMapping("/select")
    public R<List<Map<String, Object>>> selectList() {
        return R.ok(warehouseService.getSelectList());
    }

    /**
     * 获取指定仓库的库位列表
     */
    @GetMapping("/{id}/locations")
    public R<List<Map<String, Object>>> locations(@PathVariable Long id) {
        return R.ok(warehouseService.getLocationsByWarehouseId(id));
    }

    /**
     * 新增仓库或库位
     */
    @PostMapping
    public R<Void> create(@RequestBody SysWarehouse warehouse) {
        if (warehouse.getParentId() == null) warehouse.setParentId(0L);
        warehouseService.save(warehouse);
        return R.ok();
    }

    /**
     * 在指定仓库下新增库位
     */
    @PostMapping("/{id}/locations")
    public R<Void> createLocation(@PathVariable Long id, @RequestBody SysWarehouse location) {
        location.setParentId(id);
        warehouseService.save(location);
        return R.ok();
    }

    /**
     * 更新仓库或库位
     */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody SysWarehouse warehouse) {
        warehouse.setId(id);
        warehouseService.updateById(warehouse);
        return R.ok();
    }

    /**
     * 删除仓库或库位
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        warehouseService.removeById(id);
        return R.ok();
    }
}
