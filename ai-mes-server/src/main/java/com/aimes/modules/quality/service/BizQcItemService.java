package com.aimes.modules.quality.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.PageResult;
import com.aimes.common.utils.CodeUtil;
import com.aimes.modules.quality.entity.BizQcItem;
import com.aimes.modules.quality.mapper.BizQcItemMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BizQcItemService extends ServiceImpl<BizQcItemMapper, BizQcItem> {

    public PageResult<BizQcItem> getPage(String itemName, String itemCode, String category, String status, int pageNum, int pageSize) {
        LambdaQueryWrapper<BizQcItem> wrapper = new LambdaQueryWrapper<BizQcItem>()
                .like(StringUtils.hasText(itemName), BizQcItem::getItemName, itemName)
                .like(StringUtils.hasText(itemCode), BizQcItem::getItemCode, itemCode)
                .eq(StringUtils.hasText(category), BizQcItem::getCategory, category)
                .eq(StringUtils.hasText(status), BizQcItem::getStatus, status)
                .orderByDesc(BizQcItem::getCreateTime);

        Page<BizQcItem> page = page(new Page<>(pageNum, pageSize), wrapper);
        return new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    public List<BizQcItem> listActive(String category) {
        LambdaQueryWrapper<BizQcItem> wrapper = new LambdaQueryWrapper<BizQcItem>()
                .eq(BizQcItem::getStatus, "active")
                .eq(StringUtils.hasText(category), BizQcItem::getCategory, category)
                .orderByAsc(BizQcItem::getCategory)
                .orderByAsc(BizQcItem::getItemName);
        return list(wrapper);
    }

    public void create(BizQcItem item) {
        if (item.getItemCode() == null || item.getItemCode().isEmpty()) {
            item.setItemCode(CodeUtil.generate("QI"));
        }
        if (item.getStatus() == null) {
            item.setStatus("active");
        }
        save(item);
    }

    public void update(Long id, BizQcItem item) {
        BizQcItem existing = getById(id);
        if (existing == null) {
            throw new BusinessException("质检项目不存在");
        }
        item.setId(id);
        updateById(item);
    }

    public void toggleStatus(Long id) {
        BizQcItem item = getById(id);
        if (item == null) {
            throw new BusinessException("质检项目不存在");
        }
        item.setStatus("active".equals(item.getStatus()) ? "inactive" : "active");
        updateById(item);
    }
}
