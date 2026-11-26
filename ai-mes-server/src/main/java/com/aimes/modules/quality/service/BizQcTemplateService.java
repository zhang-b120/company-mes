package com.aimes.modules.quality.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.PageResult;
import com.aimes.modules.quality.entity.BizQcScheme;
import com.aimes.modules.quality.entity.BizQcTemplate;
import com.aimes.modules.quality.entity.BizQcTemplateItem;
import com.aimes.modules.quality.mapper.BizQcSchemeMapper;
import com.aimes.modules.quality.mapper.BizQcTemplateItemMapper;
import com.aimes.modules.quality.mapper.BizQcTemplateMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BizQcTemplateService extends ServiceImpl<BizQcTemplateMapper, BizQcTemplate> {

    private final BizQcTemplateItemMapper itemMapper;
    private final BizQcSchemeMapper schemeMapper;
    private final ObjectMapper objectMapper;

    /**
     * 分页查询质检模板
     */
    public PageResult<Map<String, Object>> getPage(String templateName, String templateCode, String applyType, int pageNum, int pageSize) {
        LambdaQueryWrapper<BizQcTemplate> wrapper = new LambdaQueryWrapper<BizQcTemplate>()
                .like(StringUtils.hasText(templateName), BizQcTemplate::getTemplateName, templateName)
                .like(StringUtils.hasText(templateCode), BizQcTemplate::getTemplateCode, templateCode)
                .eq(StringUtils.hasText(applyType), BizQcTemplate::getApplyType, applyType)
                .orderByDesc(BizQcTemplate::getCreateTime);

        Page<BizQcTemplate> page = page(new Page<>(pageNum, pageSize), wrapper);

        // 批量加载质检方案名称
        java.util.Set<Long> schemeIds = page.getRecords().stream()
                .map(BizQcTemplate::getSchemeId).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        java.util.Map<Long, String> schemeNameMap = schemeIds.isEmpty() ? java.util.Map.of() :
                schemeMapper.selectBatchIds(schemeIds).stream()
                        .collect(java.util.stream.Collectors.toMap(BizQcScheme::getId, BizQcScheme::getSchemeName, (a, b) -> a));

        List<Map<String, Object>> records = page.getRecords().stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("templateName", t.getTemplateName());
            m.put("templateCode", t.getTemplateCode());
            m.put("applyType", t.getApplyType());
            m.put("schemeId", t.getSchemeId());
            m.put("schemeName", t.getSchemeId() != null ? schemeNameMap.getOrDefault(t.getSchemeId(), "") : "");
            m.put("isDefault", t.getIsDefault());
            m.put("status", t.getStatus());
            m.put("createTime", t.getCreateTime());
            return m;
        }).collect(java.util.stream.Collectors.toList());

        return new PageResult<>(records, page.getTotal(), pageNum, pageSize);
    }

    /**
     * 简单列表（用于下拉选择）
     */
    public List<Map<String, Object>> getSelectList() {
        List<BizQcTemplate> list = list(new LambdaQueryWrapper<BizQcTemplate>()
                .eq(BizQcTemplate::getStatus, "active")
                .orderByDesc(BizQcTemplate::getCreateTime));
        return list.stream().map(t -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", t.getId());
            map.put("templateName", t.getTemplateName());
            map.put("templateCode", t.getTemplateCode());
            map.put("applyType", t.getApplyType());
            return map;
        }).collect(java.util.stream.Collectors.toList());
    }

    /**
     * 模板详情（含检验项）
     */
    public Map<String, Object> getDetail(Long id) {
        BizQcTemplate template = getById(id);
        if (template == null) {
            throw new BusinessException("质检模板不存在");
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", template.getId());
        detail.put("templateName", template.getTemplateName());
        detail.put("templateCode", template.getTemplateCode());
        detail.put("applyType", template.getApplyType());
        detail.put("applyProductId", template.getApplyProductId());
        detail.put("applyMaterialId", template.getApplyMaterialId());
        detail.put("schemeId", template.getSchemeId());
        detail.put("isDefault", template.getIsDefault());
        detail.put("status", template.getStatus());
        detail.put("remark", template.getRemark());
        detail.put("createTime", template.getCreateTime());

        // 检验项列表
        List<BizQcTemplateItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<BizQcTemplateItem>()
                        .eq(BizQcTemplateItem::getTemplateId, id)
                        .orderByAsc(BizQcTemplateItem::getSortOrder));
        detail.put("items", items);

        return detail;
    }

    /**
     * 创建模板（含检验项）
     */
    @Transactional
    public void createWithItems(Map<String, Object> body) {
        BizQcTemplate template = parseTemplate(body);
        if (template.getStatus() == null) {
            template.setStatus("active");
        }
        save(template);
        saveItems(template.getId(), body);
    }

    /**
     * 更新模板（含检验项）
     */
    @Transactional
    public void updateWithItems(Long id, Map<String, Object> body) {
        BizQcTemplate existing = getById(id);
        if (existing == null) {
            throw new BusinessException("质检模板不存在");
        }
        BizQcTemplate template = parseTemplate(body);
        template.setId(id);
        updateById(template);

        // 删除旧检验项，插入新检验项
        itemMapper.delete(new LambdaQueryWrapper<BizQcTemplateItem>()
                .eq(BizQcTemplateItem::getTemplateId, id));
        saveItems(id, body);
    }

    /**
     * 删除模板（含检验项）
     */
    @Transactional
    public void deleteTemplate(Long id) {
        BizQcTemplate existing = getById(id);
        if (existing == null) {
            throw new BusinessException("质检模板不存在");
        }
        itemMapper.delete(new LambdaQueryWrapper<BizQcTemplateItem>()
                .eq(BizQcTemplateItem::getTemplateId, id));
        removeById(id);
    }

    private BizQcTemplate parseTemplate(Map<String, Object> body) {
        BizQcTemplate template = new BizQcTemplate();
        template.setTemplateName((String) body.get("templateName"));
        template.setTemplateCode((String) body.get("templateCode"));
        template.setApplyType((String) body.get("applyType"));
        template.setStatus((String) body.get("status"));
        template.setRemark((String) body.get("remark"));

        Object schemeIdObj = body.get("schemeId");
        if (schemeIdObj != null) {
            template.setSchemeId(schemeIdObj instanceof Number ? ((Number) schemeIdObj).longValue() : Long.parseLong(schemeIdObj.toString()));
        }
        Object def = body.get("isDefault");
        if (def != null) {
            template.setIsDefault(def instanceof Number ? ((Number) def).intValue() : Integer.parseInt(def.toString()));
        }
        return template;
    }

    @SuppressWarnings("unchecked")
    private void saveItems(Long templateId, Map<String, Object> body) {
        Object itemsObj = body.get("items");
        if (itemsObj instanceof List) {
            List<?> list = (List<?>) itemsObj;
            int order = 0;
            for (Object obj : list) {
                if (obj instanceof Map) {
                    Map<?, ?> map = (Map<?, ?>) obj;
                    BizQcTemplateItem item = new BizQcTemplateItem();
                    item.setTemplateId(templateId);
                    item.setItemName((String) map.get("itemName"));
                    item.setItemType((String) map.get("itemType"));
                    item.setStandardValue((String) map.get("standardValue"));
                    item.setToleranceMin((String) map.get("toleranceMin"));
                    item.setToleranceMax((String) map.get("toleranceMax"));
                    item.setUnit((String) map.get("unit"));
                    item.setMethod((String) map.get("method"));
                    item.setDefaultValue((String) map.get("defaultValue"));
                    Object reqObj = map.get("isRequired");
                    if (reqObj != null) {
                        item.setIsRequired(reqObj instanceof Number ? ((Number) reqObj).intValue() : Integer.parseInt(reqObj.toString()));
                    }
                    Object critical = map.get("isCritical");
                    if (critical != null) {
                        item.setIsCritical(critical instanceof Number ? ((Number) critical).intValue() : Integer.parseInt(critical.toString()));
                    }
                    item.setSortOrder(order++);
                    itemMapper.insert(item);
                }
            }
        }
    }
}
