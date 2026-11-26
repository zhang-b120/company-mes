package com.aimes.modules.order.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.modules.order.entity.BizOrderFieldConfig;
import com.aimes.modules.order.mapper.BizOrderFieldConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class BizOrderFieldConfigService extends ServiceImpl<BizOrderFieldConfigMapper, BizOrderFieldConfig> {

    /** 查询所有启用的字段配置，按排序升序 */
    public List<BizOrderFieldConfig> listEnabled() {
        return list(new LambdaQueryWrapper<BizOrderFieldConfig>()
                .eq(BizOrderFieldConfig::getEnabled, 1)
                .orderByAsc(BizOrderFieldConfig::getSortOrder)
                .orderByAsc(BizOrderFieldConfig::getId));
    }

    /** 查询全部字段配置（管理用） */
    public List<BizOrderFieldConfig> listAll() {
        return list(new LambdaQueryWrapper<BizOrderFieldConfig>()
                .orderByAsc(BizOrderFieldConfig::getSortOrder)
                .orderByAsc(BizOrderFieldConfig::getId));
    }

    public void create(BizOrderFieldConfig entity) {
        validateFieldKey(entity.getFieldKey(), null);
        if (!StringUtils.hasText(entity.getFieldType())) entity.setFieldType("text");
        if (entity.getShowInList() == null) entity.setShowInList(1);
        if (entity.getShowInForm() == null) entity.setShowInForm(1);
        if (entity.getRequired() == null) entity.setRequired(0);
        if (entity.getSortOrder() == null) entity.setSortOrder(0);
        if (entity.getEnabled() == null) entity.setEnabled(1);
        save(entity);
    }

    public void update(Long id, BizOrderFieldConfig entity) {
        BizOrderFieldConfig existing = getById(id);
        if (existing == null) throw new BusinessException("字段配置不存在");
        validateFieldKey(entity.getFieldKey(), id);
        entity.setId(id);
        updateById(entity);
    }

    public void delete(Long id) {
        if (getById(id) == null) throw new BusinessException("字段配置不存在");
        removeById(id);
    }

    private void validateFieldKey(String fieldKey, Long excludeId) {
        if (!StringUtils.hasText(fieldKey)) throw new BusinessException("字段Key不能为空");
        if (!fieldKey.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            throw new BusinessException("字段Key只能包含字母、数字和下划线，且不能以数字开头");
        }
        LambdaQueryWrapper<BizOrderFieldConfig> wrapper = new LambdaQueryWrapper<BizOrderFieldConfig>()
                .eq(BizOrderFieldConfig::getFieldKey, fieldKey);
        if (excludeId != null) wrapper.ne(BizOrderFieldConfig::getId, excludeId);
        if (count(wrapper) > 0) throw new BusinessException("字段Key已存在: " + fieldKey);
    }
}
