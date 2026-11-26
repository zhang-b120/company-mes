package com.aimes.modules.quality.controller;

import com.aimes.common.result.PageResult;
import com.aimes.common.result.R;
import com.aimes.modules.quality.entity.BizQcScheme;
import com.aimes.modules.quality.service.BizQcSchemeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/quality/schemes")
@RequiredArgsConstructor
public class BizQcSchemeController {

    private final BizQcSchemeService schemeService;

    @GetMapping("/page")
    public R<PageResult<Map<String, Object>>> page(
            @RequestParam(required = false) String schemeName,
            @RequestParam(required = false) String schemeType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(schemeService.getPage(schemeName, schemeType, status, pageNum, pageSize));
    }

    @GetMapping("/select")
    public R<List<Map<String, Object>>> select() {
        return R.ok(schemeService.getSelectList());
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(schemeService.getDetail(id));
    }

    @PostMapping
    public R<Void> create(@RequestBody BizQcScheme scheme) {
        schemeService.createScheme(scheme);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizQcScheme scheme) {
        schemeService.updateScheme(id, scheme);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        schemeService.deleteScheme(id);
        return R.ok();
    }
}
