package com.aimes.modules.system.controller;

import com.aimes.common.result.R;
import com.aimes.modules.system.entity.SysMenu;
import com.aimes.modules.system.service.SysMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system/menus")
@RequiredArgsConstructor
public class SysMenuController {

    private final SysMenuService menuService;

    @GetMapping("/tree")
    public R<List<Map<String, Object>>> tree(@RequestParam(required = false) String platform) {
        return R.ok(menuService.getMenuTree(platform));
    }

    @GetMapping("/options")
    public R<List<Map<String, Object>>> options() {
        return R.ok(menuService.getMenuOptions());
    }

    @PostMapping
    public R<Void> create(@RequestBody SysMenu menu) {
        menuService.createMenu(menu);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody SysMenu menu) {
        menuService.updateMenu(id, menu);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        menuService.deleteMenu(id);
        return R.ok();
    }
}
