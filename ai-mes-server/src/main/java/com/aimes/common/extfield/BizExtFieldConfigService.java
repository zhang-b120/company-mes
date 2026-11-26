package com.aimes.common.extfield;

import com.aimes.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class BizExtFieldConfigService extends ServiceImpl<BizExtFieldConfigMapper, BizExtFieldConfig> {

    /** 查询指定模块所有启用的字段配置 */
    public List<BizExtFieldConfig> listEnabled(String module) {
        return list(new LambdaQueryWrapper<BizExtFieldConfig>()
                .eq(BizExtFieldConfig::getModule, module)
                .eq(BizExtFieldConfig::getEnabled, 1)
                .orderByAsc(BizExtFieldConfig::getSortOrder)
                .orderByAsc(BizExtFieldConfig::getId));
    }

    /** 查询指定模块全部字段配置（管理用） */
    public List<BizExtFieldConfig> listAll(String module) {
        LambdaQueryWrapper<BizExtFieldConfig> wrapper = new LambdaQueryWrapper<BizExtFieldConfig>()
                .orderByAsc(BizExtFieldConfig::getSortOrder)
                .orderByAsc(BizExtFieldConfig::getId);
        if (StringUtils.hasText(module)) {
            wrapper.eq(BizExtFieldConfig::getModule, module);
        }
        return list(wrapper);
    }

    public void create(BizExtFieldConfig entity) {
        if (!StringUtils.hasText(entity.getModule())) throw new BusinessException("模块标识不能为空");
        validateFieldKey(entity.getModule(), entity.getFieldKey(), null);
        applyDefaults(entity);
        save(entity);
    }

    public void update(Long id, BizExtFieldConfig entity) {
        BizExtFieldConfig existing = getById(id);
        if (existing == null) throw new BusinessException("字段配置不存在");
        // 模块和 key 不允许修改
        entity.setModule(existing.getModule());
        entity.setFieldKey(existing.getFieldKey());
        entity.setId(id);
        updateById(entity);
    }

    public void delete(Long id) {
        if (getById(id) == null) throw new BusinessException("字段配置不存在");
        removeById(id);
    }

    private void validateFieldKey(String module, String fieldKey, Long excludeId) {
        if (!StringUtils.hasText(fieldKey)) throw new BusinessException("字段Key不能为空");
        if (!fieldKey.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            throw new BusinessException("字段Key只能包含字母、数字和下划线，且不能以数字开头");
        }
        LambdaQueryWrapper<BizExtFieldConfig> wrapper = new LambdaQueryWrapper<BizExtFieldConfig>()
                .eq(BizExtFieldConfig::getModule, module)
                .eq(BizExtFieldConfig::getFieldKey, fieldKey);
        if (excludeId != null) wrapper.ne(BizExtFieldConfig::getId, excludeId);
        if (count(wrapper) > 0) throw new BusinessException("该模块下字段Key已存在: " + fieldKey);

        // 销售订单头(sales_order)与销售订单明细(sales_order_item)字段会一起合并到工单 ext_fields,
        // 不允许两个模块共享同一个 fieldKey,避免合并时无法区分来源
        if ("sales_order".equals(module) || "sales_order_item".equals(module)) {
            String otherModule = "sales_order".equals(module) ? "sales_order_item" : "sales_order";
            LambdaQueryWrapper<BizExtFieldConfig> conflictWrapper = new LambdaQueryWrapper<BizExtFieldConfig>()
                    .eq(BizExtFieldConfig::getModule, otherModule)
                    .eq(BizExtFieldConfig::getFieldKey, fieldKey);
            if (count(conflictWrapper) > 0) {
                throw new BusinessException("字段Key '" + fieldKey + "' 已在 " + otherModule
                        + " 模块定义,销售订单头/明细不能共享同一 Key");
            }
        }
    }

    private void applyDefaults(BizExtFieldConfig entity) {
        if (!StringUtils.hasText(entity.getFieldType())) entity.setFieldType("text");
        if (entity.getShowInList() == null) entity.setShowInList(1);
        if (entity.getShowInForm() == null) entity.setShowInForm(1);
        if (entity.getRequired() == null) entity.setRequired(0);
        if (entity.getSortOrder() == null) entity.setSortOrder(0);
        if (entity.getEnabled() == null) entity.setEnabled(1);
    }
}
