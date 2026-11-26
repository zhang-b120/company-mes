package com.aimes.modules.system.service;

import com.aimes.modules.system.entity.SysConfig;
import com.aimes.modules.system.mapper.SysConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 系统配置服务
 * 管理系统配置项的查询、新增、更新和删除
 */
@Service
@RequiredArgsConstructor
public class SysConfigService extends ServiceImpl<SysConfigMapper, SysConfig> {

    /**
     * 配置列表（支持按key或value模糊搜索）
     */
    public List<SysConfig> listConfigs(String keyword) {
        return list(new LambdaQueryWrapper<SysConfig>()
                .like(StringUtils.hasText(keyword), SysConfig::getConfigKey, keyword)
                .orderByAsc(SysConfig::getConfigKey));
    }

    /**
     * 根据key获取配置值
     */
    public SysConfig getByKey(String key) {
        return getOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, key));
    }

    /**
     * 保存配置（存在则更新，不存在则新建）
     */
    public void saveConfig(String key, String value) {
        SysConfig existing = getByKey(key);
        if (existing != null) {
            existing.setConfigValue(value);
            updateById(existing);
        } else {
            SysConfig config = new SysConfig();
            config.setConfigKey(key);
            config.setConfigValue(value);
            save(config);
        }
    }

    /**
     * 根据key删除配置
     */
    public void deleteByKey(String key) {
        remove(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, key));
    }

    /**
     * 按分组获取配置列表
     */
    public List<SysConfig> listByGroup(String group) {
        return list(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigGroup, group)
                .orderByAsc(SysConfig::getConfigKey));
    }

    /**
     * 批量保存配置（同一分组）
     */
    public void saveBatch(String group, java.util.Map<String, String> configs) {
        configs.forEach((key, value) -> {
            SysConfig existing = getByKey(key);
            if (existing != null) {
                existing.setConfigValue(value);
                existing.setConfigGroup(group);
                updateById(existing);
            } else {
                SysConfig config = new SysConfig();
                config.setConfigKey(key);
                config.setConfigValue(value);
                config.setConfigGroup(group);
                save(config);
            }
        });
    }
}
