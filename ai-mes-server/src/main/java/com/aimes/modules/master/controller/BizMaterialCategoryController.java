package com.aimes.modules.master.controller;

import com.aimes.common.result.R;
import com.aimes.modules.master.entity.BizMaterialCategory;
import com.aimes.modules.master.service.BizMaterialCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/master/material-categories")
@RequiredArgsConstructor
public class BizMaterialCategoryController {

    private final BizMaterialCategoryService categoryService;

    @GetMapping("/tree")
    public R<List<BizMaterialCategory>> tree() {
        return R.ok(categoryService.getTree());
    }

    @GetMapping("/{id}")
    public R<BizMaterialCategory> getById(@PathVariable Long id) {
        return R.ok(categoryService.getById(id));
    }

    @PostMapping
    public R<Void> create(@RequestBody BizMaterialCategory entity) {
        categoryService.create(entity);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizMaterialCategory entity) {
        categoryService.update(id, entity);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return R.ok();
    }
}
