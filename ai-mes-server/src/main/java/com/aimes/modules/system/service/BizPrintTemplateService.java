package com.aimes.modules.system.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.modules.system.entity.BizPrintTemplate;
import com.aimes.modules.system.mapper.BizPrintTemplateMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 打印模板服务: vue-plugin-hiprint 的模板 JSON 存储 + 激活管理.
 * 每个 biz_type 下可以有多份模板, 但同一时刻只有一份 active. 业务页面按 biz_type 加载 active 模板调 print().
 */
@Service
@RequiredArgsConstructor
public class BizPrintTemplateService extends ServiceImpl<BizPrintTemplateMapper, BizPrintTemplate> {

    private final BizPrintTemplateMapper templateMapper;

    /** 按业务类型列出所有模板 (含未激活) */
    public List<BizPrintTemplate> listByBizType(String bizType) {
        LambdaQueryWrapper<BizPrintTemplate> wrapper = new LambdaQueryWrapper<BizPrintTemplate>()
                .eq(BizPrintTemplate::getDeleted, 0)
                .eq(StringUtils.hasText(bizType), BizPrintTemplate::getBizType, bizType)
                .orderByDesc(BizPrintTemplate::getIsActive)
                .orderByDesc(BizPrintTemplate::getUpdateTime);
        return list(wrapper);
    }

    /** 按业务类型查 active 模板 (业务页面打印时调用) */
    public BizPrintTemplate getActive(String bizType) {
        return getOne(new LambdaQueryWrapper<BizPrintTemplate>()
                .eq(BizPrintTemplate::getDeleted, 0)
                .eq(BizPrintTemplate::getBizType, bizType)
                .eq(BizPrintTemplate::getIsActive, 1)
                .last("LIMIT 1"));
    }

    @Transactional
    public Long create(BizPrintTemplate t) {
        if (!StringUtils.hasText(t.getBizType())) throw new BusinessException("业务类型不能为空");
        if (!StringUtils.hasText(t.getTemplateName())) throw new BusinessException("模板名称不能为空");
        if (!StringUtils.hasText(t.getTemplateCode())) {
            t.setTemplateCode(t.getBizType() + "_" + System.currentTimeMillis());
        }
        // 默认不激活
        if (t.getIsActive() == null) t.setIsActive(0);
        save(t);
        return t.getId();
    }

    @Transactional
    public void update(Long id, BizPrintTemplate patch) {
        BizPrintTemplate cur = getById(id);
        if (cur == null) throw new BusinessException("模板不存在");
        if (StringUtils.hasText(patch.getTemplateName())) cur.setTemplateName(patch.getTemplateName());
        if (StringUtils.hasText(patch.getTemplateCode())) cur.setTemplateCode(patch.getTemplateCode());
        if (StringUtils.hasText(patch.getTemplateJson())) cur.setTemplateJson(patch.getTemplateJson());
        if (patch.getRemark() != null) cur.setRemark(patch.getRemark());
        updateById(cur);
    }

    /**
     * 激活某模板: 同 biz_type 下其他模板自动取消激活. 业务页面 getActive(bizType) 只能拿到这一份.
     */
    @Transactional
    public void activate(Long id) {
        BizPrintTemplate cur = getById(id);
        if (cur == null) throw new BusinessException("模板不存在");
        // 同 biz_type 其他模板取消激活
        update(new LambdaUpdateWrapper<BizPrintTemplate>()
                .eq(BizPrintTemplate::getBizType, cur.getBizType())
                .ne(BizPrintTemplate::getId, id)
                .set(BizPrintTemplate::getIsActive, 0));
        cur.setIsActive(1);
        updateById(cur);
    }

    @Transactional
    public void delete(Long id) {
        removeById(id);
    }
}
