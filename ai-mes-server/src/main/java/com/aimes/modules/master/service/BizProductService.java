package com.aimes.modules.master.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.PageResult;
import com.aimes.common.utils.CodeUtil;
import com.aimes.modules.master.entity.BizProduct;
import com.aimes.modules.master.mapper.BizProductMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BizProductService extends ServiceImpl<BizProductMapper, BizProduct> {

    private final ApplicationContext applicationContext;

    public PageResult<BizProduct> getPage(String productName, String productNo, String spec,
                                          String category, String status,
                                          Long categoryId, int pageNum, int pageSize) {
        LambdaQueryWrapper<BizProduct> wrapper = new LambdaQueryWrapper<BizProduct>()
                .like(StringUtils.hasText(productName), BizProduct::getProductName, productName)
                .like(StringUtils.hasText(productNo), BizProduct::getProductNo, productNo)
                .eq(StringUtils.hasText(category), BizProduct::getCategory, category)
                .eq(StringUtils.hasText(status), BizProduct::getStatus, status)
                .orderByDesc(BizProduct::getCreateTime);
        // spec 支持单值与复合输入(按 token 拆分,AND 命中)
        com.aimes.common.utils.SpecQueryUtil.apply(wrapper, BizProduct::getSpec, spec);

        if (categoryId != null) {
            BizMaterialCategoryService catService = applicationContext.getBean(BizMaterialCategoryService.class);
            Set<Long> categoryIds = catService.getDescendantIds(categoryId);
            wrapper.in(BizProduct::getCategoryId, categoryIds);
        }

        Page<BizProduct> page = page(new Page<>(pageNum, pageSize), wrapper);
        return new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    /** 兼容旧调用 */
    public PageResult<BizProduct> getPage(String productName, String productNo, String spec,
                                          String category, String status, int pageNum, int pageSize) {
        return getPage(productName, productNo, spec, category, status, null, pageNum, pageSize);
    }

    public void create(BizProduct entity) {
        entity.setProductNo(CodeUtil.generate("P"));
        entity.setStatus("active");
        if (entity.getAllowStockLock() == null) entity.setAllowStockLock(true);
        save(entity);
    }

    public void update(Long id, BizProduct entity) {
        BizProduct existing = getById(id);
        if (existing == null) {
            throw new BusinessException("产品不存在");
        }
        entity.setId(id);
        entity.setProductNo(null);
        updateById(entity);
    }

    public void delete(Long id) {
        BizProduct product = getById(id);
        if (product == null) {
            throw new BusinessException("产品不存在");
        }
        checkProductReferences(id, product.getProductName());
        baseMapper.deleteById(id);
    }

    private void checkProductReferences(Long productId, String productName) {
        try {
            // 检查BOM版本
            var bomMapper = applicationContext.getBean(com.aimes.modules.bom.mapper.BizBomVersionMapper.class);
            long bomCount = bomMapper.selectCount(new LambdaQueryWrapper<com.aimes.modules.bom.entity.BizBomVersion>()
                    .eq(com.aimes.modules.bom.entity.BizBomVersion::getProductId, productId));
            if (bomCount > 0) throw new BusinessException("产品「" + productName + "」已有BOM版本，无法删除");

            // 检查工单
            var woMapper = applicationContext.getBean(com.aimes.modules.production.mapper.BizWorkOrderMapper.class);
            long woCount = woMapper.selectCount(new LambdaQueryWrapper<com.aimes.modules.production.entity.BizWorkOrder>()
                    .eq(com.aimes.modules.production.entity.BizWorkOrder::getProductId, productId));
            if (woCount > 0) throw new BusinessException("产品「" + productName + "」存在工单记录，无法删除");

            // 检查销售订单明细
            var soiMapper = applicationContext.getBean(com.aimes.modules.order.mapper.BizSalesOrderItemMapper.class);
            long soiCount = soiMapper.selectCount(new LambdaQueryWrapper<com.aimes.modules.order.entity.BizSalesOrderItem>()
                    .eq(com.aimes.modules.order.entity.BizSalesOrderItem::getProductId, productId));
            if (soiCount > 0) throw new BusinessException("产品「" + productName + "」存在订单记录，无法删除");

            // 检查库存
            var inventoryMapper = applicationContext.getBean(com.aimes.modules.inventory.mapper.BizInventoryMapper.class);
            long invCount = inventoryMapper.selectCount(new LambdaQueryWrapper<com.aimes.modules.inventory.entity.BizInventory>()
                    .eq(com.aimes.modules.inventory.entity.BizInventory::getMaterialId, productId));
            if (invCount > 0) throw new BusinessException("产品「" + productName + "」存在库存记录，无法删除");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // Mapper not available, skip check
        }
    }

    public List<Map<String, Object>> getSelectList(String attribute) {
        // 产品实际存储在 biz_material（category=finished），biz_product 已弃用
        com.aimes.modules.master.mapper.BizMaterialMapper materialMapper =
                applicationContext.getBean(com.aimes.modules.master.mapper.BizMaterialMapper.class);
        LambdaQueryWrapper<com.aimes.modules.master.entity.BizMaterial> wrapper =
                new LambdaQueryWrapper<com.aimes.modules.master.entity.BizMaterial>()
                        .eq(com.aimes.modules.master.entity.BizMaterial::getDeleted, 0)
                        .eq(com.aimes.modules.master.entity.BizMaterial::getStatus, "active")
                        .and(w -> w.isNull(com.aimes.modules.master.entity.BizMaterial::getIsVirtualSemi)
                                .or().ne(com.aimes.modules.master.entity.BizMaterial::getIsVirtualSemi, 1))
                        // 仅成品类物料可作为工单产品
                        .in(com.aimes.modules.master.entity.BizMaterial::getCategory, "finished", "finished_goods", "成品")
                        .orderByAsc(com.aimes.modules.master.entity.BizMaterial::getMaterialName);

        if (StringUtils.hasText(attribute)) {
            wrapper.like(com.aimes.modules.master.entity.BizMaterial::getMaterialAttributes, attribute);
        }

        List<com.aimes.modules.master.entity.BizMaterial> list = materialMapper.selectList(wrapper);
        return list.stream().map(m -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", m.getId());
            map.put("name", m.getMaterialName());
            map.put("materialNo", m.getMaterialNo());
            map.put("productNo", m.getMaterialNo());
            map.put("materialName", m.getMaterialName());
            map.put("productName", m.getMaterialName());
            map.put("spec", m.getSpec());
            map.put("unit", m.getUnit());
            map.put("unitConfig", m.getUnitConfig());
            return map;
        }).collect(Collectors.toList());
    }

    /** 兼容旧调用 */
    public List<Map<String, Object>> getSelectList() {
        return getSelectList(null);
    }
}
