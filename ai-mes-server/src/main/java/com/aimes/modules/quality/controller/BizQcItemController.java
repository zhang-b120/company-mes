package com.aimes.modules.quality.controller;

import com.aimes.common.result.PageResult;
import com.aimes.common.result.R;
import com.aimes.modules.quality.entity.BizQcItem;
import com.aimes.modules.quality.service.BizQcItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/quality/items")
@RequiredArgsConstructor
public class BizQcItemController {

    private final BizQcItemService itemService;

    @GetMapping("/page")
    public R<PageResult<BizQcItem>> page(
            @RequestParam(required = false) String itemName,
            @RequestParam(required = false) String itemCode,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(itemService.getPage(itemName, itemCode, category, status, pageNum, pageSize));
    }

    @GetMapping("/list")
    public R<List<BizQcItem>> listActive(@RequestParam(required = false) String category) {
        return R.ok(itemService.listActive(category));
    }

    @GetMapping("/{id}")
    public R<BizQcItem> detail(@PathVariable Long id) {
        return R.ok(itemService.getById(id));
    }

    @PostMapping
    public R<Void> create(@RequestBody BizQcItem item) {
        itemService.create(item);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizQcItem item) {
        itemService.update(id, item);
        return R.ok();
    }

    @PutMapping("/{id}/toggle-status")
    public R<Void> toggleStatus(@PathVariable Long id) {
        itemService.toggleStatus(id);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        itemService.removeById(id);
        return R.ok();
    }
}
