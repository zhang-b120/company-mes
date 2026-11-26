package com.aimes.modules.eam.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.PageResult;
import com.aimes.modules.eam.entity.EamMaintenanceTemplate;
import com.aimes.modules.eam.entity.EamMaintenanceTemplateEquipment;
import com.aimes.modules.eam.entity.EamMaintenanceTemplateItem;
import com.aimes.modules.eam.mapper.EamMaintenanceTemplateEquipmentMapper;
import com.aimes.modules.eam.mapper.EamMaintenanceTemplateItemMapper;
import com.aimes.modules.eam.mapper.EamMaintenanceTemplateMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;

@Service
@RequiredArgsConstructor
public class EamMaintenanceTemplateService extends ServiceImpl<EamMaintenanceTemplateMapper, EamMaintenanceTemplate> {

    private final EamMaintenanceTemplateItemMapper itemMapper;
    private final EamMaintenanceTemplateEquipmentMapper equipmentMapper;

    public PageResult<EamMaintenanceTemplate> getPage(String templateName, String equipmentType, int pageNum, int pageSize) {
        LambdaQueryWrapper<EamMaintenanceTemplate> wrapper = new LambdaQueryWrapper<EamMaintenanceTemplate>()
                .like(StringUtils.hasText(templateName), EamMaintenanceTemplate::getTemplateName, templateName)
                .eq(StringUtils.hasText(equipmentType), EamMaintenanceTemplate::getEquipmentType, equipmentType)
                .orderByDesc(EamMaintenanceTemplate::getCreateTime);
        Page<EamMaintenanceTemplate> page = page(new Page<>(pageNum, pageSize), wrapper);
        return new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    public Map<String, Object> getDetail(Long id) {
        EamMaintenanceTemplate tpl = getById(id);
        if (tpl == null) throw new BusinessException("保养模板不存在");
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("template", tpl);
        detail.put("items", itemMapper.selectList(new LambdaQueryWrapper<EamMaintenanceTemplateItem>()
                .eq(EamMaintenanceTemplateItem::getTemplateId, id)
                .orderByAsc(EamMaintenanceTemplateItem::getSortNo)));
        detail.put("equipmentIds", equipmentMapper.selectList(new LambdaQueryWrapper<EamMaintenanceTemplateEquipment>()
                .eq(EamMaintenanceTemplateEquipment::getTemplateId, id))
                .stream().map(EamMaintenanceTemplateEquipment::getEquipmentId).toList());
        return detail;
    }

    @Transactional(rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public void createWithItems(Map<String, Object> body) {
        EamMaintenanceTemplate tpl = parseTemplate(body);
        long cnt = count(new LambdaQueryWrapper<EamMaintenanceTemplate>()
                .eq(EamMaintenanceTemplate::getTemplateName, tpl.getTemplateName()));
        if (cnt > 0) throw new BusinessException("模板名称已存在");
        save(tpl);
        saveItems(tpl.getId(), (List<Map<String, Object>>) body.get("items"));
        saveEquipment(tpl.getId(), (List<Number>) body.get("equipmentIds"));
    }

    @Transactional(rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public void updateWithItems(Long id, Map<String, Object> body) {
        EamMaintenanceTemplate existing = getById(id);
        if (existing == null) throw new BusinessException("保养模板不存在");
        EamMaintenanceTemplate tpl = parseTemplate(body);
        tpl.setId(id);
        updateById(tpl);
        itemMapper.delete(new LambdaQueryWrapper<EamMaintenanceTemplateItem>()
                .eq(EamMaintenanceTemplateItem::getTemplateId, id));
        equipmentMapper.delete(new LambdaQueryWrapper<EamMaintenanceTemplateEquipment>()
                .eq(EamMaintenanceTemplateEquipment::getTemplateId, id));
        saveItems(id, (List<Map<String, Object>>) body.get("items"));
        saveEquipment(id, (List<Number>) body.get("equipmentIds"));
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteTemplate(Long id) {
        itemMapper.delete(new LambdaQueryWrapper<EamMaintenanceTemplateItem>()
                .eq(EamMaintenanceTemplateItem::getTemplateId, id));
        equipmentMapper.delete(new LambdaQueryWrapper<EamMaintenanceTemplateEquipment>()
                .eq(EamMaintenanceTemplateEquipment::getTemplateId, id));
        removeById(id);
    }

    private EamMaintenanceTemplate parseTemplate(Map<String, Object> body) {
        EamMaintenanceTemplate tpl = new EamMaintenanceTemplate();
        tpl.setTemplateName((String) body.get("templateName"));
        tpl.setEquipmentType((String) body.get("equipmentType"));
        tpl.setCycleType((String) body.get("cycleType"));
        Object cv = body.get("cycleValue");
        if (cv != null) tpl.setCycleValue(cv instanceof Number ? ((Number) cv).intValue() : Integer.parseInt(cv.toString()));
        tpl.setRemark((String) body.get("remark"));
        return tpl;
    }

    private void saveItems(Long templateId, List<Map<String, Object>> items) {
        if (items == null) return;
        int order = 0;
        for (Map<String, Object> m : items) {
            EamMaintenanceTemplateItem item = new EamMaintenanceTemplateItem();
            item.setTemplateId(templateId);
            item.setItemGroup((String) m.get("itemGroup"));
            item.setItemName((String) m.get("itemName"));
            item.setMethod((String) m.get("method"));
            item.setStandard((String) m.get("standard"));
            item.setUom((String) m.get("uom"));
            Object req = m.get("required");
            item.setRequired(req instanceof Number ? ((Number) req).intValue() : (req != null ? 1 : 1));
            Object np = m.get("needPhoto");
            item.setNeedPhoto(np instanceof Number ? ((Number) np).intValue() : 0);
            Object sn = m.get("sortNo");
            item.setSortNo(sn instanceof Number ? ((Number) sn).intValue() : order++);
            itemMapper.insert(item);
        }
    }

    private void saveEquipment(Long templateId, List<Number> equipmentIds) {
        if (equipmentIds == null) return;
        for (Number eid : equipmentIds) {
            EamMaintenanceTemplateEquipment te = new EamMaintenanceTemplateEquipment();
            te.setTemplateId(templateId);
            te.setEquipmentId(eid.longValue());
            equipmentMapper.insert(te);
        }
    }

    // 供计划服务使用
    public List<Long> getEquipmentIdsByTemplate(Long templateId) {
        return equipmentMapper.selectList(new LambdaQueryWrapper<EamMaintenanceTemplateEquipment>()
                .eq(EamMaintenanceTemplateEquipment::getTemplateId, templateId))
                .stream().map(EamMaintenanceTemplateEquipment::getEquipmentId).toList();
    }

    public List<EamMaintenanceTemplateItem> getItemsByTemplate(Long templateId) {
        return itemMapper.selectList(new LambdaQueryWrapper<EamMaintenanceTemplateItem>()
                .eq(EamMaintenanceTemplateItem::getTemplateId, templateId)
                .orderByAsc(EamMaintenanceTemplateItem::getSortNo));
    }
}
