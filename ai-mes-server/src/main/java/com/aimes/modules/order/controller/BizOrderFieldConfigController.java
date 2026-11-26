package com.aimes.modules.order.controller;

import com.aimes.common.result.R;
import com.aimes.modules.order.entity.BizOrderFieldConfig;
import com.aimes.modules.order.service.BizOrderFieldConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/order/field-configs")
@RequiredArgsConstructor
public class BizOrderFieldConfigController {

    private final BizOrderFieldConfigService fieldConfigService;

    /** 前端获取启用的字段配置（列表+表单使用） */
    @GetMapping("/enabled")
    public R<List<BizOrderFieldConfig>> listEnabled() {
        return R.ok(fieldConfigService.listEnabled());
    }

    /** 管理：获取所有配置 */
    @GetMapping
    public R<List<BizOrderFieldConfig>> listAll() {
        return R.ok(fieldConfigService.listAll());
    }

    @PostMapping
    public R<Void> create(@RequestBody BizOrderFieldConfig entity) {
        fieldConfigService.create(entity);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizOrderFieldConfig entity) {
        fieldConfigService.update(id, entity);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        fieldConfigService.delete(id);
        return R.ok();
    }
}
