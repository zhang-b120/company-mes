package com.aimes.modules.system.controller;

import com.aimes.common.result.R;
import com.aimes.modules.system.entity.SysConfig;
import com.aimes.modules.system.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 系统配置控制器
 * 提供配置的列表查询、按Key查询、新增、更新、删除等接口
 * 路径: /api/system/configs
 */
@RestController
@RequestMapping("/api/system/configs")
@RequiredArgsConstructor
public class SysConfigController {

    private final SysConfigService configService;

    /**
     * 配置列表（支持关键词搜索）
     */
    @GetMapping("/list")
    public R<List<SysConfig>> list(@RequestParam(required = false) String keyword) {
        return R.ok(configService.listConfigs(keyword));
    }

    /**
     * 根据key获取配置
     */
    @GetMapping("/{key}")
    public R<SysConfig> getByKey(@PathVariable String key) {
        return R.ok(configService.getByKey(key));
    }

    /**
     * 新增配置
     */
    @PostMapping
    public R<Void> create(@RequestBody SysConfig config) {
        configService.saveConfig(config.getConfigKey(), config.getConfigValue());
        return R.ok();
    }

    /**
     * 更新配置
     */
    @PutMapping("/{key}")
    public R<Void> update(@PathVariable String key, @RequestBody SysConfig config) {
        configService.saveConfig(key, config.getConfigValue());
        return R.ok();
    }

    /**
     * 删除配置
     */
    @DeleteMapping("/{key}")
    public R<Void> delete(@PathVariable String key) {
        configService.deleteByKey(key);
        return R.ok();
    }

    /**
     * 按分组获取配置
     */
    @GetMapping("/group/{group}")
    public R<List<SysConfig>> listByGroup(@PathVariable String group) {
        return R.ok(configService.listByGroup(group));
    }

    /**
     * 批量保存配置（同一分组）
     */
    @PostMapping("/group/{group}")
    public R<Void> saveBatch(@PathVariable String group, @RequestBody Map<String, String> configs) {
        configService.saveBatch(group, configs);
        return R.ok();
    }
}
