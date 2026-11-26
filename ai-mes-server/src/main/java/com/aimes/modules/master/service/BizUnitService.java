package com.aimes.modules.master.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.PageResult;
import com.aimes.modules.master.entity.BizUnit;
import com.aimes.modules.master.mapper.BizUnitMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BizUnitService extends ServiceImpl<BizUnitMapper, BizUnit> {

    public PageResult<BizUnit> getPage(String unitName, String unitCode, int pageNum, int pageSize) {
        LambdaQueryWrapper<BizUnit> wrapper = new LambdaQueryWrapper<BizUnit>()
                .like(StringUtils.hasText(unitName), BizUnit::getUnitName, unitName)
                .like(StringUtils.hasText(unitCode), BizUnit::getUnitCode, unitCode)
                .orderByAsc(BizUnit::getSortOrder)
                .orderByAsc(BizUnit::getId);
        Page<BizUnit> page = page(new Page<>(pageNum, pageSize), wrapper);
        return new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    public void create(BizUnit entity) {
        // 唯一校验
        long nameCount = count(new LambdaQueryWrapper<BizUnit>()
                .eq(BizUnit::getUnitName, entity.getUnitName()));
        if (nameCount > 0) {
            throw new BusinessException("单位名称已存在: " + entity.getUnitName());
        }
        long codeCount = count(new LambdaQueryWrapper<BizUnit>()
                .eq(BizUnit::getUnitCode, entity.getUnitCode()));
        if (codeCount > 0) {
            throw new BusinessException("单位编码已存在: " + entity.getUnitCode());
        }
        if (entity.getStatus() == null) entity.setStatus("active");
        if (entity.getSortOrder() == null) entity.setSortOrder(0);
        save(entity);
    }

    public void update(Long id, BizUnit entity) {
        BizUnit existing = getById(id);
        if (existing == null) {
            throw new BusinessException("单位不存在");
        }
        // 唯一校验（排除自己）
        long nameCount = count(new LambdaQueryWrapper<BizUnit>()
                .eq(BizUnit::getUnitName, entity.getUnitName())
                .ne(BizUnit::getId, id));
        if (nameCount > 0) {
            throw new BusinessException("单位名称已存在: " + entity.getUnitName());
        }
        long codeCount = count(new LambdaQueryWrapper<BizUnit>()
                .eq(BizUnit::getUnitCode, entity.getUnitCode())
                .ne(BizUnit::getId, id));
        if (codeCount > 0) {
            throw new BusinessException("单位编码已存在: " + entity.getUnitCode());
        }
        entity.setId(id);
        updateById(entity);
    }

    public void delete(Long id) {
        BizUnit unit = getById(id);
        if (unit == null) {
            throw new BusinessException("单位不存在");
        }
        // 注意：这里暂不做物料引用检查，因为物料表的unit字段存的是字符串名称
        // 后续如果改为unit_id关联，需要在此加引用检查
        removeById(id);
    }

    public List<Map<String, Object>> getSelectList() {
        List<BizUnit> list = list(new LambdaQueryWrapper<BizUnit>()
                .eq(BizUnit::getStatus, "active")
                .orderByAsc(BizUnit::getSortOrder));
        return list.stream().map(u -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", u.getId());
            map.put("unitName", u.getUnitName());
            map.put("unitCode", u.getUnitCode());
            return map;
        }).collect(Collectors.toList());
    }
}
