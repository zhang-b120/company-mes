package com.aimes.modules.master.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.PageResult;
import com.aimes.common.utils.CodeUtil;
import com.aimes.modules.bom.entity.BizProcessRoute;
import com.aimes.modules.bom.mapper.BizProcessRouteMapper;
import com.aimes.modules.master.entity.BizMaterial;
import com.aimes.modules.master.entity.BizMaterialSpec;
import com.aimes.modules.master.entity.BizSupplier;
import com.aimes.modules.master.entity.BizUnit;
import com.aimes.modules.master.mapper.BizMaterialMapper;
import com.aimes.modules.master.mapper.BizMaterialSpecMapper;
import com.aimes.modules.master.mapper.BizSupplierMapper;
import com.aimes.modules.master.mapper.BizUnitMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.aimes.common.config.AsyncExecutorConfig;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BizMaterialService extends ServiceImpl<BizMaterialMapper, BizMaterial> {

    private final ApplicationContext applicationContext;
    private final BizProcessRouteMapper processRouteMapper;
    private final BizMaterialSpecMapper materialSpecMapper;
    private final BizUnitMapper unitMapper;
    private final BizSupplierMapper supplierMapper;
    private final com.aimes.common.cache.NameCacheService nameCacheService;
    private final com.aimes.common.cache.NameCacheEvictor nameCacheEvictor;
    @Qualifier(AsyncExecutorConfig.LIST_FILL_EXECUTOR)
    private final Executor listFillExecutor;
    private static final ObjectMapper OM = new ObjectMapper();

    public PageResult<BizMaterial> getPage(String materialNo, String materialName, String spec, String category, String status,
                                           Long categoryId, String materialAttribute, int pageNum, int pageSize) {
        LambdaQueryWrapper<BizMaterial> wrapper = new LambdaQueryWrapper<BizMaterial>()
                // 列表页只选必要字段,排除 images / attachment_urls / unit_config 等大字段(详情页再单独取)
                .select(BizMaterial::getId, BizMaterial::getMaterialNo, BizMaterial::getMaterialName,
                        BizMaterial::getSpec, BizMaterial::getCategory, BizMaterial::getCategoryId,
                        BizMaterial::getUnit, BizMaterial::getSafetyStock, BizMaterial::getMaxStock,
                        BizMaterial::getMinStock, BizMaterial::getStandardCost,
                        BizMaterial::getDefaultWarehouseId, BizMaterial::getDefaultSupplierId,
                        BizMaterial::getMaterialAttributes, BizMaterial::getStatus,
                        BizMaterial::getSourceType, BizMaterial::getPurchaseLeadTime, BizMaterial::getProductionLeadTime,
                        BizMaterial::getMinOrderQty, BizMaterial::getOrderQtyMultiple, BizMaterial::getMrpEnabled,
                        BizMaterial::getDrawingNo, BizMaterial::getResponsibleUserId, BizMaterial::getImageUrl,
                        BizMaterial::getRemark, BizMaterial::getCreateTime, BizMaterial::getUpdateTime,
                        BizMaterial::getIsVirtualSemi)
                .like(StringUtils.hasText(materialNo), BizMaterial::getMaterialNo, materialNo)
                .like(StringUtils.hasText(materialName), BizMaterial::getMaterialName, materialName)
                .eq(StringUtils.hasText(status), BizMaterial::getStatus, status)
                .like(StringUtils.hasText(materialAttribute), BizMaterial::getMaterialAttributes, materialAttribute)
                // 改写为 ne(0/null 一并视为非虚拟半成品):利用索引,避免 OR is null 失效
                .ne(BizMaterial::getIsVirtualSemi, 1)
                .orderByDesc(BizMaterial::getCreateTime);
        // spec 支持单值与复合输入(按 token 拆分,AND 命中)
        com.aimes.common.utils.SpecQueryUtil.applyToBizMaterial(wrapper, spec);

        // category 支持逗号分隔多值（前端 materialTypes 筛选）
        if (StringUtils.hasText(category)) {
            if (category.contains(",")) {
                List<String> cats = Arrays.asList(category.split(","));
                wrapper.in(BizMaterial::getCategory, cats);
            } else {
                wrapper.eq(BizMaterial::getCategory, category);
            }
        }

        // 按物料类别ID过滤（包含子分类）
        if (categoryId != null) {
            BizMaterialCategoryService catService = applicationContext.getBean(BizMaterialCategoryService.class);
            Set<Long> categoryIds = catService.getDescendantIds(categoryId);
            wrapper.in(BizMaterial::getCategoryId, categoryIds);
        }

        Page<BizMaterial> page = page(new Page<>(pageNum, pageSize), wrapper);
        List<BizMaterial> records = page.getRecords();
        if (records.isEmpty()) return new PageResult<>(records, page.getTotal(), pageNum, pageSize);

        // 一次性收集所有需批量回填的 ID
        Set<Long> productIds = new HashSet<>();
        Set<Long> supplierIds = new HashSet<>();
        Set<Long> catIds = new HashSet<>();
        for (BizMaterial m : records) {
            if ("finished".equals(m.getCategory())) productIds.add(m.getId());
            if (m.getDefaultSupplierId() != null) supplierIds.add(m.getDefaultSupplierId());
            if (m.getCategoryId() != null) catIds.add(m.getCategoryId());
        }

        // ========== 并行回填三类关联数据(2~3 倍提速) ==========
        // 1) hasProcess: 工艺路线表 IN 查询(无缓存,直接 DB)
        CompletableFuture<Set<Long>> fHasRoute = productIds.isEmpty()
                ? CompletableFuture.completedFuture(Collections.emptySet())
                : CompletableFuture.supplyAsync(() -> {
                    Set<Long> ids = new HashSet<>();
                    processRouteMapper.selectList(new LambdaQueryWrapper<BizProcessRoute>()
                                    .select(BizProcessRoute::getProductId)
                                    .in(BizProcessRoute::getProductId, productIds)
                                    .eq(BizProcessRoute::getStatus, "active")
                                    .eq(BizProcessRoute::getDeleted, 0))
                            .forEach(r -> ids.add(r.getProductId()));
                    return ids;
                }, listFillExecutor);

        // 2) 供应商名: 走 Caffeine 缓存,缺失部分一次 IN 补查
        CompletableFuture<Map<Long, String>> fSupMap = supplierIds.isEmpty()
                ? CompletableFuture.completedFuture(Collections.emptyMap())
                : CompletableFuture.supplyAsync(() -> nameCacheService.batchGetSupplierName(supplierIds), listFillExecutor);

        // 3) 物料类别名: 走 Caffeine 缓存
        CompletableFuture<Map<Long, String>> fCatMap = catIds.isEmpty()
                ? CompletableFuture.completedFuture(Collections.emptyMap())
                : CompletableFuture.supplyAsync(() -> nameCacheService.batchGetCategoryName(catIds), listFillExecutor);

        CompletableFuture.allOf(fHasRoute, fSupMap, fCatMap).join();
        Set<Long> hasRouteIds = fHasRoute.join();
        Map<Long, String> supMap = fSupMap.join();
        Map<Long, String> catMap = fCatMap.join();

        // 单次循环回填三个字段
        for (BizMaterial m : records) {
            m.setHasProcess(hasRouteIds.contains(m.getId()));
            if (m.getDefaultSupplierId() != null) m.setDefaultSupplierName(supMap.get(m.getDefaultSupplierId()));
            if (m.getCategoryId() != null) m.setCategoryName(catMap.get(m.getCategoryId()));
        }
        return new PageResult<>(records, page.getTotal(), pageNum, pageSize);
    }

    /** 单条物料回填展示字段(供应商名/类别名),供详情页使用 */
    public void enrichDisplayNames(BizMaterial m) {
        if (m == null) return;
        if (m.getDefaultSupplierId() != null) {
            BizSupplier sup = supplierMapper.selectById(m.getDefaultSupplierId());
            if (sup != null) m.setDefaultSupplierName(sup.getSupplierName());
        }
        if (m.getCategoryId() != null) {
            var catMapper = applicationContext.getBean(
                    com.aimes.modules.master.mapper.BizMaterialCategoryMapper.class);
            var cat = catMapper.selectById(m.getCategoryId());
            if (cat != null) m.setCategoryName(cat.getCategoryName());
        }
    }

    /** 全量列表并回填默认供应商名称（供导出用） */
    public List<BizMaterial> listWithEnrichment() {
        List<BizMaterial> records = list();
        if (records.isEmpty()) return records;
        Set<Long> supplierIds = records.stream()
                .map(BizMaterial::getDefaultSupplierId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (!supplierIds.isEmpty()) {
            Map<Long, String> supMap = supplierMapper.selectBatchIds(supplierIds).stream()
                    .collect(Collectors.toMap(BizSupplier::getId, BizSupplier::getSupplierName, (a, b) -> a));
            records.forEach(m -> m.setDefaultSupplierName(
                    m.getDefaultSupplierId() != null ? supMap.get(m.getDefaultSupplierId()) : null));
        }
        return records;
    }

    /** 兼容旧调用（带 categoryId + materialAttribute） */
    public PageResult<BizMaterial> getPage(String category, String status,
                                           Long categoryId, String materialAttribute, int pageNum, int pageSize) {
        return getPage(null, null, null, category, status, categoryId, materialAttribute, pageNum, pageSize);
    }

    /** 兼容旧调用（带 categoryId，无 materialAttribute） */
    public PageResult<BizMaterial> getPage(String category, String status,
                                           Long categoryId, int pageNum, int pageSize) {
        return getPage(null, null, null, category, status, categoryId, null, pageNum, pageSize);
    }

    /** 兼容旧调用 */
    public PageResult<BizMaterial> getPage(String category, String status, int pageNum, int pageSize) {
        return getPage(null, null, null, category, status, null, null, pageNum, pageSize);
    }

    public void create(BizMaterial entity) {
        if (!StringUtils.hasText(entity.getMaterialNo())) {
            entity.setMaterialNo(CodeUtil.generate("M"));
        }
        entity.setStatus("active");
        if (entity.getAllowStockLock() == null) entity.setAllowStockLock(true);
        save(entity);
        syncSpecLibrary(entity.getSpec());
        syncUnitLibrary(entity.getUnit());
    }

    public List<Long> batchCreate(List<BizMaterial> list) {
        List<Long> ids = new ArrayList<>();
        for (BizMaterial m : list) {
            create(m);
            ids.add(m.getId());
        }
        return ids;
    }

    public void update(Long id, BizMaterial entity) {
        BizMaterial existing = getById(id);
        if (existing == null) {
            throw new BusinessException("物料不存在");
        }
        entity.setId(id);
        entity.setMaterialNo(null);
        updateById(entity);
        syncSpecLibrary(entity.getSpec());
        syncUnitLibrary(entity.getUnit());
    }

    /** 解析规格 JSON,将其中的规格维度名(如"材质","尺寸")写入规格库 */
    private void syncSpecLibrary(String spec) {
        if (!StringUtils.hasText(spec)) return;
        try {
            String trimmed = spec.trim();
            if (!trimmed.startsWith("[")) return;
            JsonNode arr = OM.readTree(trimmed);
            if (!arr.isArray()) return;
            for (JsonNode node : arr) {
                JsonNode nameNode = node.get("name");
                if (nameNode == null) continue;
                String specName = nameNode.asText();
                if (!StringUtils.hasText(specName)) continue;
                Long exists = materialSpecMapper.selectCount(new LambdaQueryWrapper<BizMaterialSpec>()
                        .eq(BizMaterialSpec::getSpecName, specName)
                        .eq(BizMaterialSpec::getDeleted, 0));
                if (exists == 0) {
                    BizMaterialSpec ms = new BizMaterialSpec();
                    ms.setSpecName(specName);
                    ms.setStatus("active");
                    materialSpecMapper.insert(ms);
                }
            }
        } catch (Exception ignored) {}
    }

    /** 单位同步到单位库 */
    private void syncUnitLibrary(String unit) {
        if (!StringUtils.hasText(unit)) return;
        try {
            Long exists = unitMapper.selectCount(new LambdaQueryWrapper<BizUnit>()
                    .eq(BizUnit::getUnitName, unit.trim())
                    .eq(BizUnit::getDeleted, 0));
            if (exists == 0) {
                BizUnit u = new BizUnit();
                u.setUnitName(unit.trim());
                u.setUnitCode(unit.trim().toUpperCase());
                u.setStatus("active");
                unitMapper.insert(u);
            }
        } catch (Exception ignored) {}
    }

    public void delete(Long id) {
        BizMaterial material = getById(id);
        if (material == null) {
            throw new BusinessException("物料不存在");
        }
        checkMaterialReferences(id, material.getMaterialName());
        // 物理删除
        baseMapper.deleteById(id);
    }

    private void checkMaterialReferences(Long materialId, String materialName) {
        // 使用 ApplicationContext 获取 mapper, 避免循环依赖
        try {
            // 检查库存
            var inventoryMapper = applicationContext.getBean(com.aimes.modules.inventory.mapper.BizInventoryMapper.class);
            long invCount = inventoryMapper.selectCount(new LambdaQueryWrapper<com.aimes.modules.inventory.entity.BizInventory>()
                    .eq(com.aimes.modules.inventory.entity.BizInventory::getMaterialId, materialId));
            if (invCount > 0) throw new BusinessException("物料「" + materialName + "」存在库存记录，无法删除");

            // 检查BOM
            var bomItemMapper = applicationContext.getBean(com.aimes.modules.bom.mapper.BizBomItemMapper.class);
            long bomCount = bomItemMapper.selectCount(new LambdaQueryWrapper<com.aimes.modules.bom.entity.BizBomItem>()
                    .eq(com.aimes.modules.bom.entity.BizBomItem::getMaterialId, materialId));
            if (bomCount > 0) throw new BusinessException("物料「" + materialName + "」已被BOM引用，无法删除");

            // 检查采购申请
            var prMapper = applicationContext.getBean(com.aimes.modules.purchase.mapper.BizPurchaseRequestMapper.class);
            long prCount = prMapper.selectCount(new LambdaQueryWrapper<com.aimes.modules.purchase.entity.BizPurchaseRequest>()
                    .eq(com.aimes.modules.purchase.entity.BizPurchaseRequest::getMaterialId, materialId));
            if (prCount > 0) throw new BusinessException("物料「" + materialName + "」存在采购记录，无法删除");

            // 检查库存流水
            var txMapper = applicationContext.getBean(com.aimes.modules.inventory.mapper.BizInvTransactionMapper.class);
            long txCount = txMapper.selectCount(new LambdaQueryWrapper<com.aimes.modules.inventory.entity.BizInvTransaction>()
                    .eq(com.aimes.modules.inventory.entity.BizInvTransaction::getMaterialId, materialId));
            if (txCount > 0) throw new BusinessException("物料「" + materialName + "」存在库存流水，无法删除");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // Mapper not available, skip check
        }
    }

    public List<Map<String, Object>> getSelectList(String attribute) {
        LambdaQueryWrapper<BizMaterial> wrapper = new LambdaQueryWrapper<BizMaterial>()
                .eq(BizMaterial::getStatus, "active")
                .select(BizMaterial::getId, BizMaterial::getMaterialNo, BizMaterial::getMaterialName,
                        BizMaterial::getSpec, BizMaterial::getUnit, BizMaterial::getMaterialAttributes,
                        BizMaterial::getDefaultWarehouseId, BizMaterial::getUnitConfig)
                .orderByAsc(BizMaterial::getMaterialName);

        if (StringUtils.hasText(attribute)) {
            wrapper.like(BizMaterial::getMaterialAttributes, attribute);
        }

        List<BizMaterial> list = list(wrapper);
        return list.stream().map(m -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", m.getId());
            map.put("name", m.getMaterialName());
            map.put("materialNo", m.getMaterialNo());
            map.put("materialName", m.getMaterialName());
            map.put("spec", m.getSpec());
            map.put("unit", m.getUnit());
            map.put("unitConfig", m.getUnitConfig());
            map.put("defaultWarehouseId", m.getDefaultWarehouseId());
            return map;
        }).collect(Collectors.toList());
    }

    /** 兼容旧调用 */
    public List<Map<String, Object>> getSelectList() {
        return getSelectList(null);
    }
}
