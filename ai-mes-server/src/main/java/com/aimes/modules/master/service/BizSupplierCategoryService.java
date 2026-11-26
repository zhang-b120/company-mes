package com.aimes.modules.master.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.modules.master.entity.BizSupplierCategory;
import com.aimes.modules.master.mapper.BizSupplierCategoryMapper;
import com.aimes.modules.master.mapper.BizSupplierMapper;
import com.aimes.modules.master.entity.BizSupplier;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BizSupplierCategoryService extends ServiceImpl<BizSupplierCategoryMapper, BizSupplierCategory> {

    private final BizSupplierMapper supplierMapper;

    public List<BizSupplierCategory> getTree() {
        List<BizSupplierCategory> all = list(new LambdaQueryWrapper<BizSupplierCategory>()
                .orderByAsc(BizSupplierCategory::getSortOrder)
                .orderByAsc(BizSupplierCategory::getId));
        return buildTree(all, 0L);
    }

    private List<BizSupplierCategory> buildTree(List<BizSupplierCategory> all, Long parentId) {
        List<BizSupplierCategory> children = all.stream()
                .filter(c -> Objects.equals(c.getParentId(), parentId))
                .collect(Collectors.toList());
        for (BizSupplierCategory child : children) {
            child.setChildren(buildTree(all, child.getId()));
        }
        return children;
    }

    public void create(BizSupplierCategory entity) {
        if (StringUtils.hasText(entity.getCategoryCode())) {
            long codeCount = count(new LambdaQueryWrapper<BizSupplierCategory>()
                    .eq(BizSupplierCategory::getCategoryCode, entity.getCategoryCode()));
            if (codeCount > 0) {
                throw new BusinessException("类别编码已存在: " + entity.getCategoryCode());
            }
        }
        if (entity.getParentId() != null && entity.getParentId() != 0) {
            BizSupplierCategory parent = getById(entity.getParentId());
            if (parent == null) {
                throw new BusinessException("父级分类不存在");
            }
        } else {
            entity.setParentId(0L);
        }
        if (entity.getStatus() == null) entity.setStatus("active");
        if (entity.getSortOrder() == null) entity.setSortOrder(0);
        save(entity);
    }

    public void update(Long id, BizSupplierCategory entity) {
        BizSupplierCategory existing = getById(id);
        if (existing == null) {
            throw new BusinessException("分类不存在");
        }
        if (StringUtils.hasText(entity.getCategoryCode())) {
            long codeCount = count(new LambdaQueryWrapper<BizSupplierCategory>()
                    .eq(BizSupplierCategory::getCategoryCode, entity.getCategoryCode())
                    .ne(BizSupplierCategory::getId, id));
            if (codeCount > 0) {
                throw new BusinessException("类别编码已存在: " + entity.getCategoryCode());
            }
        }
        if (entity.getParentId() != null && entity.getParentId() != 0) {
            if (entity.getParentId().equals(id)) {
                throw new BusinessException("不能将自己设为父级");
            }
            Set<Long> descendantIds = getDescendantIds(id);
            if (descendantIds.contains(entity.getParentId())) {
                throw new BusinessException("不能将下级分类设为父级");
            }
        }
        entity.setId(id);
        updateById(entity);
    }

    public void delete(Long id) {
        BizSupplierCategory existing = getById(id);
        if (existing == null) {
            throw new BusinessException("分类不存在");
        }
        long childCount = count(new LambdaQueryWrapper<BizSupplierCategory>()
                .eq(BizSupplierCategory::getParentId, id));
        if (childCount > 0) {
            throw new BusinessException("该分类下有子分类，不能删除");
        }
        long supplierCount = supplierMapper.selectCount(new LambdaQueryWrapper<BizSupplier>()
                .eq(BizSupplier::getSupplierCategoryId, id));
        if (supplierCount > 0) {
            throw new BusinessException("该分类已被供应商引用，不能删除");
        }
        removeById(id);
    }

    public Set<Long> getDescendantIds(Long categoryId) {
        Set<Long> result = new HashSet<>();
        result.add(categoryId);
        List<BizSupplierCategory> all = list();
        collectDescendants(all, categoryId, result);
        return result;
    }

    private void collectDescendants(List<BizSupplierCategory> all, Long parentId, Set<Long> result) {
        for (BizSupplierCategory c : all) {
            if (Objects.equals(c.getParentId(), parentId) && !result.contains(c.getId())) {
                result.add(c.getId());
                collectDescendants(all, c.getId(), result);
            }
        }
    }
}
