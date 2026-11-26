package com.aimes.modules.inventory.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.modules.inventory.entity.BizQrTemplate;
import com.aimes.modules.inventory.mapper.BizQrTemplateMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BizQrTemplateService {

    private final BizQrTemplateMapper qrTemplateMapper;

    public List<BizQrTemplate> list(String keyword) {
        LambdaQueryWrapper<BizQrTemplate> wrapper = new LambdaQueryWrapper<BizQrTemplate>()
                .like(StringUtils.hasText(keyword), BizQrTemplate::getTemplateName, keyword)
                .orderByDesc(BizQrTemplate::getIsDefault)
                .orderByDesc(BizQrTemplate::getCreateTime);
        return qrTemplateMapper.selectList(wrapper);
    }

    public BizQrTemplate getById(Long id) {
        return qrTemplateMapper.selectById(id);
    }

    public void create(BizQrTemplate template) {
        if (template.getIsDefault() != null && template.getIsDefault() == 1) {
            clearDefault();
        }
        qrTemplateMapper.insert(template);
    }

    public void update(Long id, BizQrTemplate template) {
        BizQrTemplate existing = qrTemplateMapper.selectById(id);
        if (existing == null) throw new BusinessException("模板不存在");

        if (template.getIsDefault() != null && template.getIsDefault() == 1) {
            clearDefault();
        }
        template.setId(id);
        qrTemplateMapper.updateById(template);
    }

    public void delete(Long id) {
        BizQrTemplate existing = qrTemplateMapper.selectById(id);
        if (existing != null && existing.getIsDefault() != null && existing.getIsDefault() == 1) {
            throw new BusinessException("不能删除默认模板");
        }
        qrTemplateMapper.deleteById(id);
    }

    public void setDefault(Long id) {
        BizQrTemplate template = qrTemplateMapper.selectById(id);
        if (template == null) throw new BusinessException("模板不存在");

        clearDefault();
        template.setIsDefault(1);
        qrTemplateMapper.updateById(template);
    }

    private void clearDefault() {
        List<BizQrTemplate> defaults = qrTemplateMapper.selectList(
                new LambdaQueryWrapper<BizQrTemplate>().eq(BizQrTemplate::getIsDefault, 1));
        for (BizQrTemplate t : defaults) {
            t.setIsDefault(0);
            qrTemplateMapper.updateById(t);
        }
    }
}
