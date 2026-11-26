package com.aimes.common.columnconfig;

import com.aimes.common.utils.SecurityUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BizTableColumnConfigService extends ServiceImpl<BizTableColumnConfigMapper, BizTableColumnConfig> {

    /**
     * 获取当前用户指定模块的列配置，按 sort_order 升序返回
     * 若该用户从未配置过，返回空列表（前端使用默认列）
     */
    public List<BizTableColumnConfig> getUserColumnConfig(String module) {
        Long userId = SecurityUtil.getCurrentUserId();
        return list(new LambdaQueryWrapper<BizTableColumnConfig>()
                .eq(BizTableColumnConfig::getUserId, userId)
                .eq(BizTableColumnConfig::getModule, module)
                .orderByAsc(BizTableColumnConfig::getSortOrder));
    }

    /**
     * 批量保存当前用户的列配置（全量替换）
     */
    @Transactional
    public void saveUserColumnConfig(String module, List<BizTableColumnConfig> columns) {
        Long userId = SecurityUtil.getCurrentUserId();
        // 删除旧配置
        remove(new LambdaQueryWrapper<BizTableColumnConfig>()
                .eq(BizTableColumnConfig::getUserId, userId)
                .eq(BizTableColumnConfig::getModule, module));
        // 写入新配置
        if (columns != null && !columns.isEmpty()) {
            for (int i = 0; i < columns.size(); i++) {
                BizTableColumnConfig col = columns.get(i);
                col.setId(null);
                col.setUserId(userId);
                col.setModule(module);
                col.setSortOrder(i);
            }
            saveBatch(columns);
        }
    }
}
