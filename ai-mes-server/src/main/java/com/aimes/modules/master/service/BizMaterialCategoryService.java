package com.aimes.modules.master.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.modules.master.entity.BizMaterialCategory;
import com.aimes.modules.master.mapper.BizMaterialCategoryMapper;
import com.aimes.modules.master.mapper.BizMaterialMapper;
import com.aimes.modules.master.mapper.BizProductMapper;
import com.aimes.modules.master.entity.BizMaterial;
import com.aimes.modules.master.entity.BizProduct;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BizMaterialCategoryService extends ServiceImpl<BizMaterialCategoryMapper, BizMaterialCategory> {

    private final BizMaterialMapper materialMapper;
    private final BizProductMapper productMapper;
    private final com.aimes.common.cache.NameCacheEvictor nameCacheEvictor;

    /**
     * 获取分类树
     */
    public List<BizMaterialCategory> getTree() {
        List<BizMaterialCategory> all = list(new LambdaQueryWrapper<BizMaterialCategory>()
                .orderByAsc(BizMaterialCategory::getSortOrder)
                .orderByAsc(BizMaterialCategory::getId));
        return buildTree(all, 0L);
    }

    private List<BizMaterialCategory> buildTree(List<BizMaterialCategory> all, Long parentId) {
        List<BizMaterialCategory> children = all.stream()
                .filter(c -> Objects.equals(c.getParentId(), parentId))
                .collect(Collectors.toList());
        for (BizMaterialCategory child : children) {
            child.setChildren(buildTree(all, child.getId()));
        }
        return children;
    }

    public void create(BizMaterialCategory entity) {
        // 校验编码唯一
        long codeCount = count(new LambdaQueryWrapper<BizMaterialCategory>()
                .eq(BizMaterialCategory::getCategoryCode, entity.getCategoryCode()));
        if (codeCount > 0) {
            throw new BusinessException("类别编码已存在: " + entity.getCategoryCode());
        }
        // 校验父级存在
        if (entity.getParentId() != null && entity.getParentId() != 0) {
            BizMaterialCategory parent = getById(entity.getParentId());
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

    public void update(Long id, BizMaterialCategory entity) {
        BizMaterialCategory existing = getById(id);
        if (existing == null) {
            throw new BusinessException("分类不存在");
        }
        // 编码唯一校验(排除自己)
        long codeCount = count(new LambdaQueryWrapper<BizMaterialCategory>()
                .eq(BizMaterialCategory::getCategoryCode, entity.getCategoryCode())
                .ne(BizMaterialCategory::getId, id));
        if (codeCount > 0) {
            throw new BusinessException("类别编码已存在: " + entity.getCategoryCode());
        }
        // 防止循环引用: parent不能是自己或后代
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
        nameCacheEvictor.evictCategory(id);
    }

    public void delete(Long id) {
        BizMaterialCategory existing = getById(id);
        if (existing == null) {
            throw new BusinessException("分类不存在");
        }
        // 检查是否有子分类
        long childCount = count(new LambdaQueryWrapper<BizMaterialCategory>()
                .eq(BizMaterialCategory::getParentId, id));
        if (childCount > 0) {
            throw new BusinessException("该分类下有子分类，不能删除");
        }
        // 检查是否被物料引用
        long matCount = materialMapper.selectCount(new LambdaQueryWrapper<BizMaterial>()
                .eq(BizMaterial::getCategoryId, id));
        long prodCount = productMapper.selectCount(new LambdaQueryWrapper<BizProduct>()
                .eq(BizProduct::getCategoryId, id));
        if (matCount + prodCount > 0) {
            throw new BusinessException("该分类已被物料引用，不能删除");
        }
        removeById(id);
        nameCacheEvictor.evictCategory(id);
    }

    /**
     * 获取指定分类及其所有子分类ID
     */
    public Set<Long> getDescendantIds(Long categoryId) {
        Set<Long> result = new HashSet<>();
        result.add(categoryId);
        List<BizMaterialCategory> all = list();
        collectDescendants(all, categoryId, result);
        return result;
    }

    private void collectDescendants(List<BizMaterialCategory> all, Long parentId, Set<Long> result) {
        for (BizMaterialCategory c : all) {
            if (Objects.equals(c.getParentId(), parentId) && !result.contains(c.getId())) {
                result.add(c.getId());
                collectDescendants(all, c.getId(), result);
            }
        }
    }
}
