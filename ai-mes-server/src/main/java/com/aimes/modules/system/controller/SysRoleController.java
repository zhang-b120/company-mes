package com.aimes.modules.system.controller;

import com.aimes.common.result.R;
import com.aimes.common.result.PageResult;
import com.aimes.modules.system.entity.SysRole;
import com.aimes.modules.system.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system/roles")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService roleService;

    @GetMapping("/page")
    public R<PageResult<Map<String, Object>>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(roleService.getPage(keyword, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public R<SysRole> detail(@PathVariable Long id) {
        return R.ok(roleService.getById(id));
    }

    @PostMapping
    public R<Void> create(@RequestBody SysRole role) {
        roleService.createRole(role);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody SysRole role) {
        roleService.updateRole(id, role);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        roleService.deleteRole(id);
        return R.ok();
    }

    @GetMapping("/{id}/menus")
    public R<List<Long>> getRoleMenus(@PathVariable Long id) {
        return R.ok(roleService.getRoleMenuIds(id));
    }

    @PutMapping("/{id}/menus")
    public R<Void> saveRoleMenus(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        roleService.saveRoleMenus(id, body.get("menuIds"));
        return R.ok();
    }

    @GetMapping("/list")
    public R<List<SysRole>> list() {
        return R.ok(roleService.list());
    }

    // ==================== 数据权限 ====================

    @GetMapping("/{id}/data-scope")
    public R<Map<String, Object>> getDataScope(@PathVariable Long id) {
        return R.ok(roleService.getRoleDataScope(id));
    }

    @PutMapping("/{id}/data-scope")
    public R<Void> saveDataScope(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        roleService.saveRoleDataScope(id, body);
        return R.ok();
    }
}
