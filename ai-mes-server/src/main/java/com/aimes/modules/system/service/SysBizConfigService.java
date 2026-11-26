package com.aimes.modules.system.service;

import com.aimes.common.utils.SecurityUtil;
import com.aimes.modules.system.entity.SysBizConfig;
import com.aimes.modules.system.mapper.SysBizConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class SysBizConfigService extends ServiceImpl<SysBizConfigMapper, SysBizConfig> {

    /** 按模块查询（按 sort_order 排序，已删除除外） */
    public List<SysBizConfig> listByModule(String module) {
        return list(new LambdaQueryWrapper<SysBizConfig>()
                .eq(SysBizConfig::getModule, module)
                .eq(SysBizConfig::getDeleted, 0)
                .orderByAsc(SysBizConfig::getSortOrder));
    }

    /** 查询所有配置（用于前端全量缓存） */
    public List<SysBizConfig> listAll() {
        return list(new LambdaQueryWrapper<SysBizConfig>()
                .eq(SysBizConfig::getDeleted, 0)
                .orderByAsc(SysBizConfig::getModule)
                .orderByAsc(SysBizConfig::getSortOrder));
    }

    /** 按 key 查询有效值（configValue 优先，fallback defaultValue） */
    public String getValue(String configKey) {
        SysBizConfig cfg = getOne(new LambdaQueryWrapper<SysBizConfig>()
                .eq(SysBizConfig::getConfigKey, configKey)
                .eq(SysBizConfig::getDeleted, 0));
        if (cfg == null) return null;
        return cfg.getEffectiveValue();
    }

    /** 查询 boolean 型配置（switch 类型专用） */
    public boolean getBoolValue(String configKey, boolean defaultVal) {
        String val = getValue(configKey);
        if (val == null) return defaultVal;
        return "true".equalsIgnoreCase(val) || "1".equals(val);
    }

    /** 更新单项配置值 */
    public void updateValue(String configKey, String value) {
        String operator = currentUsername();
        update(new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SysBizConfig>()
                .eq(SysBizConfig::getConfigKey, configKey)
                .eq(SysBizConfig::getDeleted, 0)
                .set(SysBizConfig::getConfigValue, value)
                .set(SysBizConfig::getUpdateBy, operator)
                .set(SysBizConfig::getUpdateTime, LocalDateTime.now()));
    }

    /** 批量保存某模块配置 */
    @Transactional
    public void batchUpdate(Map<String, String> kvMap) {
        if (kvMap == null || kvMap.isEmpty()) return;
        String operator = currentUsername();
        LocalDateTime now = LocalDateTime.now();
        kvMap.forEach((key, value) ->
                update(new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SysBizConfig>()
                        .eq(SysBizConfig::getConfigKey, key)
                        .eq(SysBizConfig::getDeleted, 0)
                        .set(SysBizConfig::getConfigValue, value)
                        .set(SysBizConfig::getUpdateBy, operator)
                        .set(SysBizConfig::getUpdateTime, now))
        );
    }

    private String currentUsername() {
        Long userId = SecurityUtil.getCurrentUserId();
        return userId != null ? String.valueOf(userId) : "system";
    }
}
