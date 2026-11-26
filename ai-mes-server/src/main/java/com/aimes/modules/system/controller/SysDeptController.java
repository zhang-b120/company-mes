package com.aimes.modules.system.controller;

import com.aimes.common.result.R;
import com.aimes.modules.system.entity.SysDept;
import com.aimes.modules.system.service.SysDeptService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system/depts")
@RequiredArgsConstructor
public class SysDeptController {

    private final SysDeptService deptService;

    /** 获取部门树（管理页面用） */
    @GetMapping("/tree")
    public R<List<Map<String, Object>>> tree() {
        return R.ok(deptService.getDeptTree());
    }

    /** 获取部门扁平列表（下拉选择用） */
    @GetMapping("/options")
    public R<List<Map<String, Object>>> options() {
        return R.ok(deptService.getDeptOptions());
    }

    /** 新增部门 */
    @PostMapping
    public R<Void> create(@RequestBody SysDept dept) {
        deptService.createDept(dept);
        return R.ok();
    }

    /** 修改部门 */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody SysDept dept) {
        deptService.updateDept(id, dept);
        return R.ok();
    }

    /** 删除部门 */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        deptService.deleteDept(id);
        return R.ok();
    }
}
