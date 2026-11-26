package com.aimes.modules.quality.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.PageResult;
import com.aimes.modules.quality.entity.BizQcScheme;
import com.aimes.modules.quality.mapper.BizQcSchemeMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BizQcSchemeService extends ServiceImpl<BizQcSchemeMapper, BizQcScheme> {

    public PageResult<Map<String, Object>> getPage(String schemeName, String schemeType, String status,
                                                    int pageNum, int pageSize) {
        LambdaQueryWrapper<BizQcScheme> wrapper = new LambdaQueryWrapper<BizQcScheme>()
                .like(StringUtils.hasText(schemeName), BizQcScheme::getSchemeName, schemeName)
                .eq(StringUtils.hasText(schemeType), BizQcScheme::getSchemeType, schemeType)
                .eq(StringUtils.hasText(status), BizQcScheme::getStatus, status)
                .orderByDesc(BizQcScheme::getCreateTime);
        Page<BizQcScheme> page = page(new Page<>(pageNum, pageSize), wrapper);
        List<Map<String, Object>> list = page.getRecords().stream().map(this::toMap).toList();
        return new PageResult<>(list, page.getTotal(), pageNum, pageSize);
    }

    public List<Map<String, Object>> getSelectList() {
        List<BizQcScheme> schemes = list(new LambdaQueryWrapper<BizQcScheme>()
                .eq(BizQcScheme::getStatus, "active")
                .orderByAsc(BizQcScheme::getSchemeName));
        return schemes.stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("schemeName", s.getSchemeName());
            m.put("schemeType", s.getSchemeType());
            m.put("samplingMode", s.getSamplingMode());
            return m;
        }).toList();
    }

    public Map<String, Object> getDetail(Long id) {
        BizQcScheme s = getById(id);
        if (s == null) throw new BusinessException("质检方案不存在");
        return toMap(s);
    }

    public void createScheme(BizQcScheme scheme) {
        validateScheme(scheme);
        save(scheme);
    }

    public void updateScheme(Long id, BizQcScheme scheme) {
        BizQcScheme existing = getById(id);
        if (existing == null) throw new BusinessException("质检方案不存在");
        validateScheme(scheme);
        scheme.setId(id);
        updateById(scheme);
    }

    public void deleteScheme(Long id) {
        removeById(id);
    }

    /**
     * 根据质检方案计算抽检数量
     */
    public int calculateSampleQty(Long schemeId, int totalQty) {
        if (schemeId == null) return totalQty;
        BizQcScheme scheme = getById(schemeId);
        if (scheme == null) return totalQty;
        return calculateSampleQty(scheme, totalQty);
    }

    public int calculateSampleQty(BizQcScheme scheme, int totalQty) {
        if ("full".equals(scheme.getSchemeType())) return totalQty;
        if (scheme.getSamplingMode() == null) return totalQty;
        switch (scheme.getSamplingMode()) {
            case "first_piece":
            case "last_piece":
                return 1;
            case "custom_qty":
                return scheme.getSamplingQty() != null ? Math.min(scheme.getSamplingQty(), totalQty) : totalQty;
            case "ratio":
                if (scheme.getSamplingRatio() == null) return totalQty;
                double raw = totalQty * scheme.getSamplingRatio().doubleValue() / 100.0;
                int result = (scheme.getRatioRoundUp() != null && scheme.getRatioRoundUp() == 1)
                        ? (int) Math.ceil(raw) : (int) raw;
                return Math.max(1, Math.min(result, totalQty));
            default:
                return totalQty;
        }
    }

    private void validateScheme(BizQcScheme scheme) {
        if (!StringUtils.hasText(scheme.getSchemeName())) throw new BusinessException("方案名称不能为空");
        if (!StringUtils.hasText(scheme.getSchemeType())) throw new BusinessException("方案类型不能为空");
        if ("sampling".equals(scheme.getSchemeType()) && !StringUtils.hasText(scheme.getSamplingMode())) {
            throw new BusinessException("抽检方案必须选择抽检模式");
        }
    }

    private Map<String, Object> toMap(BizQcScheme s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("schemeName", s.getSchemeName());
        m.put("schemeType", s.getSchemeType());
        m.put("samplingMode", s.getSamplingMode());
        m.put("samplingQty", s.getSamplingQty());
        m.put("samplingRatio", s.getSamplingRatio());
        m.put("ratioRoundUp", s.getRatioRoundUp());
        m.put("status", s.getStatus());
        m.put("remark", s.getRemark());
        m.put("createTime", s.getCreateTime());
        return m;
    }
}
