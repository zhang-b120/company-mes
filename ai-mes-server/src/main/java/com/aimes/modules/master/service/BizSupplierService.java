package com.aimes.modules.master.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.PageResult;
import com.aimes.common.utils.CodeUtil;
import com.aimes.modules.master.entity.BizSupplier;
import com.aimes.modules.master.mapper.BizSupplierMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BizSupplierService extends ServiceImpl<BizSupplierMapper, BizSupplier> {

    @Lazy
    private final BizSupplierCategoryService supplierCategoryService;
    private final com.aimes.common.cache.NameCacheEvictor nameCacheEvictor;

    public PageResult<BizSupplier> getPage(String supplierName, String supplierNo, String status,
                                           String supplierType, Long supplierCategoryId,
                                           String contactName, String phone, String settlementType,
                                           int pageNum, int pageSize) {
        Set<Long> categoryIds = supplierCategoryId != null
                ? supplierCategoryService.getDescendantIds(supplierCategoryId)
                : null;

        LambdaQueryWrapper<BizSupplier> wrapper = new LambdaQueryWrapper<BizSupplier>()
                .like(StringUtils.hasText(supplierName), BizSupplier::getSupplierName, supplierName)
                .like(StringUtils.hasText(supplierNo), BizSupplier::getSupplierNo, supplierNo)
                .eq(StringUtils.hasText(status), BizSupplier::getStatus, status)
                .eq(StringUtils.hasText(supplierType), BizSupplier::getSupplierType, supplierType)
                .in(categoryIds != null, BizSupplier::getSupplierCategoryId, categoryIds != null ? categoryIds : List.of())
                .like(StringUtils.hasText(contactName), BizSupplier::getContactName, contactName)
                .like(StringUtils.hasText(phone), BizSupplier::getPhone, phone)
                .eq(StringUtils.hasText(settlementType), BizSupplier::getSettlementType, settlementType)
                .orderByDesc(BizSupplier::getCreateTime);

        Page<BizSupplier> page = page(new Page<>(pageNum, pageSize), wrapper);
        return new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    public void create(BizSupplier entity) {
        entity.setSupplierNo(CodeUtil.generate("S"));
        entity.setStatus("active");
        save(entity);
    }

    public void update(Long id, BizSupplier entity) {
        BizSupplier existing = getById(id);
        if (existing == null) {
            throw new BusinessException("供应商不存在");
        }
        entity.setId(id);
        entity.setSupplierNo(null);
        updateById(entity);
        nameCacheEvictor.evictSupplier(id);
    }

    public void updateStatus(Long id, String status) {
        update(new LambdaUpdateWrapper<BizSupplier>()
                .eq(BizSupplier::getId, id)
                .set(BizSupplier::getStatus, status));
        nameCacheEvictor.evictSupplier(id);
    }

    public void updateScore(Long id, BigDecimal score) {
        BizSupplier existing = getById(id);
        if (existing == null) {
            throw new BusinessException("供应商不存在");
        }
        update(new LambdaUpdateWrapper<BizSupplier>()
                .eq(BizSupplier::getId, id)
                .set(BizSupplier::getScore, score));
        nameCacheEvictor.evictSupplier(id);
    }

    public List<Map<String, Object>> getSelectList() {
        List<BizSupplier> list = list(new LambdaQueryWrapper<BizSupplier>()
                .eq(BizSupplier::getStatus, "active")
                .select(BizSupplier::getId, BizSupplier::getSupplierName,
                        BizSupplier::getSupplierType, BizSupplier::getContactName,
                        BizSupplier::getSettlementType)
                .orderByAsc(BizSupplier::getSupplierName));

        return list.stream().map(s -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", s.getId());
            map.put("name", s.getSupplierName());
            map.put("supplierType", s.getSupplierType());
            map.put("contactName", s.getContactName());
            map.put("settlementType", s.getSettlementType());
            return map;
        }).collect(Collectors.toList());
    }
}
