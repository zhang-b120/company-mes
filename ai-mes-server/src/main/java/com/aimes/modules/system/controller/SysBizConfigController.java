package com.aimes.modules.system.controller;

import com.aimes.common.result.R;
import com.aimes.modules.system.entity.SysBizConfig;
import com.aimes.modules.system.service.SysBizConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 业务配置接口
 * GET  /api/system/biz-configs?module=sales   按模块获取配置列表
 * GET  /api/system/biz-configs/all            获取所有配置
 * PUT  /api/system/biz-configs/{key}          更新单项配置值
 * PUT  /api/system/biz-configs/batch          批量保存 { configKey: value, ... }
 */
@RestController
@RequestMapping("/api/system/biz-configs")
@RequiredArgsConstructor
public class SysBizConfigController {

    private final SysBizConfigService bizConfigService;

    /** 按模块查询配置列表 */
    @GetMapping
    public R<List<SysBizConfig>> list(@RequestParam(required = false) String module) {
        if (module != null && !module.isBlank()) {
            return R.ok(bizConfigService.listByModule(module));
        }
        return R.ok(bizConfigService.listAll());
    }

    /** 获取全部配置（前端全量缓存用） */
    @GetMapping("/all")
    public R<List<SysBizConfig>> all() {
        return R.ok(bizConfigService.listAll());
    }

    /** 更新单项配置值 */
    @PutMapping("/{key}")
    public R<Void> updateOne(@PathVariable String key,
                             @RequestBody Map<String, String> body) {
        bizConfigService.updateValue(key, body.get("value"));
        return R.ok();
    }

    /** 批量保存 */
    @PutMapping("/batch")
    public R<Void> batch(@RequestBody Map<String, String> kvMap) {
        bizConfigService.batchUpdate(kvMap);
        return R.ok();
    }
}
