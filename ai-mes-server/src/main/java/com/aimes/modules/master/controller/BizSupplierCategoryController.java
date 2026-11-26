package com.aimes.modules.master.controller;

import com.aimes.common.result.R;
import com.aimes.modules.master.entity.BizSupplierCategory;
import com.aimes.modules.master.service.BizSupplierCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/master/supplier-categories")
@RequiredArgsConstructor
public class BizSupplierCategoryController {

    private final BizSupplierCategoryService categoryService;

    @GetMapping("/tree")
    public R<List<BizSupplierCategory>> tree() {
        return R.ok(categoryService.getTree());
    }

    @GetMapping("/{id}")
    public R<BizSupplierCategory> getById(@PathVariable Long id) {
        return R.ok(categoryService.getById(id));
    }

    @PostMapping
    public R<Void> create(@RequestBody BizSupplierCategory entity) {
        categoryService.create(entity);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizSupplierCategory entity) {
        categoryService.update(id, entity);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return R.ok();
    }
}
