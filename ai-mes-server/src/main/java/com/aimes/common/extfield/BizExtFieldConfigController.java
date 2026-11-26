package com.aimes.common.extfield;

import com.aimes.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 通用扩展字段配置接口
 * GET  /api/ext-field-configs?module=sales_order          查询启用的字段（前端使用）
 * GET  /api/ext-field-configs/all?module=sales_order      查询全部字段（管理使用）
 * POST /api/ext-field-configs                             新增
 * PUT  /api/ext-field-configs/{id}                        更新
 * DEL  /api/ext-field-configs/{id}                        删除
 */
@RestController
@RequestMapping("/api/ext-field-configs")
@RequiredArgsConstructor
public class BizExtFieldConfigController {

    private final BizExtFieldConfigService extFieldConfigService;

    @GetMapping
    public R<List<BizExtFieldConfig>> listEnabled(@RequestParam String module) {
        return R.ok(extFieldConfigService.listEnabled(module));
    }

    @GetMapping("/all")
    public R<List<BizExtFieldConfig>> listAll(@RequestParam(required = false) String module) {
        return R.ok(extFieldConfigService.listAll(module));
    }

    @PostMapping
    public R<Void> create(@RequestBody BizExtFieldConfig entity) {
        extFieldConfigService.create(entity);
        return R.ok();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BizExtFieldConfig entity) {
        extFieldConfigService.update(id, entity);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        extFieldConfigService.delete(id);
        return R.ok();
    }
}
