package com.aimes.modules.inventory.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.PageResult;
import com.aimes.common.utils.CodeUtil;
import com.aimes.modules.inventory.entity.BizInvReservation;
import com.aimes.modules.inventory.entity.BizInvTransaction;
import com.aimes.modules.inventory.entity.BizInventory;
import com.aimes.modules.inventory.mapper.BizInvReservationMapper;
import com.aimes.modules.inventory.mapper.BizInvTransactionMapper;
import com.aimes.modules.inventory.mapper.BizInventoryMapper;
import com.aimes.common.utils.UnitConversionUtil;
import com.aimes.modules.master.entity.BizMaterial;
import com.aimes.modules.master.mapper.BizMaterialMapper;

import com.aimes.modules.finance.service.BizFinanceService;
import com.aimes.modules.order.entity.BizSalesOrder;
import com.aimes.modules.order.entity.BizSalesOrderItem;
import com.aimes.modules.order.mapper.BizSalesOrderItemMapper;
import com.aimes.modules.order.mapper.BizSalesOrderMapper;
import com.aimes.modules.order.service.BizSalesOrderService;
import com.aimes.modules.production.entity.BizWoBomSnapshot;
import com.aimes.modules.production.mapper.BizWoBomSnapshotMapper;
import com.aimes.modules.purchase.entity.BizPurchaseRequest;
import com.aimes.modules.purchase.entity.BizPurchaseOrderItem;
import com.aimes.modules.purchase.mapper.BizPurchaseRequestMapper;
import com.aimes.modules.purchase.mapper.BizPurchaseOrderItemMapper;
import com.aimes.modules.outsource.entity.BizOutsourceOrder;
import com.aimes.modules.outsource.mapper.BizOutsourceOrderMapper;
import com.aimes.modules.system.entity.SysUser;
import com.aimes.modules.system.entity.SysWarehouse;
import com.aimes.modules.system.mapper.SysUserMapper;
import com.aimes.modules.system.mapper.SysWarehouseMapper;
import com.aimes.modules.system.service.SysNotificationService;
import com.aimes.modules.system.service.SysBizConfigService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 库存服务
 * 管理物料的入库、出库、锁库、库存台账和库存预警
 * 入库类型: purchase_in/production_in/semi_in/outsource_in/return_in/other_in
 * 出库类型: production_out/sales_out/outsource_out/scrap_out/other_out
 * 库存公式: available_qty = total_qty - locked_qty
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService extends ServiceImpl<BizInventoryMapper, BizInventory> {

    private final BizInvTransactionMapper transactionMapper;
    private final BizInvReservationMapper reservationMapper;
    private final BizWoBomSnapshotMapper bomSnapshotMapper;
    private final BizMaterialMapper materialMapper;
    private final com.aimes.modules.master.mapper.BizMaterialCategoryMapper materialCategoryMapper;
    private final SysWarehouseMapper warehouseMapper;
    private final SysUserMapper userMapper;
    private final BizPurchaseRequestMapper purchaseRequestMapper;
    private final BizPurchaseOrderItemMapper purchaseOrderItemMapper;
    private final BizOutsourceOrderMapper outsourceOrderMapper;
    private final SysNotificationService notificationService;
    private final BizSalesOrderMapper salesOrderMapper;
    private final BizSalesOrderItemMapper salesOrderItemMapper;
    private final BizSalesOrderService salesOrderService;
    private final SysBizConfigService bizConfigService;
    private final com.aimes.modules.inventory.mapper.BizInvTaskMapper invTaskMapper;
    private final com.aimes.modules.inventory.mapper.BizInvTaskItemMapper invTaskItemMapper;
    private final com.aimes.modules.inventory.mapper.BizInvRequestMapper invRequestMapper;
    private final com.aimes.modules.inventory.mapper.BizInvHistoryMapper invHistoryMapper;
    private final com.aimes.modules.production.mapper.BizWoProcessTaskMapper woProcessTaskMapper;
    private final com.aimes.modules.production.mapper.BizWorkOrderMapper workOrderMapper;
    private final com.aimes.modules.purchase.mapper.BizPurchaseOrderMapper purchaseOrderMapper;

    /** 出库类库存任务类型(待处理时占用可用库存) */
    private static final java.util.Set<String> OUTBOUND_INV_TYPES = java.util.Set.of(
            "production_out", "sales_out", "outsource_out", "scrap_out", "other_out", "service_out"
    );

    /** null → ZERO 安全取值 */
    private static BigDecimal nz(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }

    private record SecondaryStock(String unit, BigDecimal qty) {}

    private SecondaryStock resolveSecondaryStock(Long materialId, BigDecimal qty, String secondaryUnit, BigDecimal secondaryQty) {
        if (StringUtils.hasText(secondaryUnit) && secondaryQty != null) {
            return new SecondaryStock(secondaryUnit, secondaryQty);
        }
        if (materialId == null || qty == null) {
            return new SecondaryStock(secondaryUnit, secondaryQty);
        }
        BizMaterial material = materialMapper.selectById(materialId);
        if (material == null || !StringUtils.hasText(material.getUnitConfig())) {
            return new SecondaryStock(secondaryUnit, secondaryQty);
        }
        String resolvedUnit = StringUtils.hasText(secondaryUnit) ? secondaryUnit : UnitConversionUtil.getSecondaryUnit(material.getUnitConfig());
        BigDecimal resolvedQty = secondaryQty != null ? secondaryQty : UnitConversionUtil.toSecondary(qty, material.getUnitConfig());
        return new SecondaryStock(resolvedUnit, resolvedQty);
    }

    /**
     * 分页查询库存（含物料名称、仓库名称）
     */
    public PageResult<Map<String, Object>> getPage(String materialNo, String materialName, String spec, String warehouseIds, String category, String stockStatus, int pageNum, int pageSize) {
        // 物料编号/名称/规格/类别预过滤
        List<Long> matIdFilter = null;
        if (StringUtils.hasText(materialNo) || StringUtils.hasText(materialName) || StringUtils.hasText(category) || StringUtils.hasText(spec)) {
            LambdaQueryWrapper<BizMaterial> matWrapper = new LambdaQueryWrapper<BizMaterial>()
                    .like(StringUtils.hasText(materialNo), BizMaterial::getMaterialNo, materialNo)
                    .like(StringUtils.hasText(materialName), BizMaterial::getMaterialName, materialName);
            // spec 支持单值与复合输入(按 token 拆分,AND 命中)
            com.aimes.common.utils.SpecQueryUtil.applyToBizMaterial(matWrapper, spec);
            // 分类过滤:按 category_code 匹配 + 包含其子分类(通过 categoryId)
            if (StringUtils.hasText(category)) {
                List<com.aimes.modules.master.entity.BizMaterialCategory> allCats = materialCategoryMapper.selectList(
                        new LambdaQueryWrapper<com.aimes.modules.master.entity.BizMaterialCategory>()
                                .eq(com.aimes.modules.master.entity.BizMaterialCategory::getDeleted, 0));
                // 找到 code 匹配的父分类
                Set<Long> matchIds = new java.util.HashSet<>();
                for (var cat : allCats) {
                    if (category.equals(cat.getCategoryCode())) matchIds.add(cat.getId());
                }
                // 递归找子分类
                boolean changed = true;
                while (changed) {
                    changed = false;
                    for (var cat : allCats) {
                        if (cat.getParentId() != null && matchIds.contains(cat.getParentId()) && matchIds.add(cat.getId()))
                            changed = true;
                    }
                }
                if (!matchIds.isEmpty()) {
                    matWrapper.and(w -> w.eq(BizMaterial::getCategory, category)
                            .or().in(BizMaterial::getCategoryId, matchIds));
                } else {
                    matWrapper.eq(BizMaterial::getCategory, category);
                }
            }
            List<BizMaterial> materials = materialMapper.selectList(matWrapper);
            if (materials.isEmpty()) return new PageResult<>(List.of(), 0L, pageNum, pageSize);
            matIdFilter = materials.stream().map(BizMaterial::getId).collect(Collectors.toList());
        }
        // 仓库多选过滤
        List<Long> whIdList = null;
        if (StringUtils.hasText(warehouseIds)) {
            whIdList = Arrays.stream(warehouseIds.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .map(Long::parseLong).collect(Collectors.toList());
        }

        // 库存状态过滤（shortage/excess/normal 需两阶段处理，因为 minStock/maxStock 在物料档案中）
        List<Long> statusFilteredInvIds = null;
        if ("shortage".equals(stockStatus) || "excess".equals(stockStatus) || "normal".equals(stockStatus) || "alert".equals(stockStatus)) {
            final List<Long> preMatIds = matIdFilter;
            final List<Long> preWhIds = whIdList;
            List<BizInventory> allInv = list(new LambdaQueryWrapper<BizInventory>()
                    .in(preMatIds != null && !preMatIds.isEmpty(), BizInventory::getMaterialId, preMatIds)
                    .in(preWhIds != null && !preWhIds.isEmpty(), BizInventory::getWarehouseId, preWhIds));
            Set<Long> invMatIds = allInv.stream().map(BizInventory::getMaterialId).filter(Objects::nonNull).collect(Collectors.toSet());
            Map<Long, BizMaterial> matForStatus = invMatIds.isEmpty() ? new HashMap<>() :
                    materialMapper.selectBatchIds(invMatIds).stream().collect(Collectors.toMap(BizMaterial::getId, m -> m, (a, b) -> a));
            statusFilteredInvIds = allInv.stream()
                    .filter(inv -> {
                        BizMaterial m2 = inv.getMaterialId() != null ? matForStatus.get(inv.getMaterialId()) : null;
                        // 预警阈值：优先 safetyStock，兜底 minStock
                        BigDecimal threshold = null;
                        if (m2 != null) {
                            threshold = m2.getSafetyStock();
                            if (threshold == null || threshold.compareTo(BigDecimal.ZERO) <= 0) {
                                threshold = m2.getMinStock();
                            }
                        }
                        return matchesStockStatus(
                                nz(inv.getAvailableQty()),
                                threshold,
                                m2 != null ? m2.getMaxStock() : null,
                                stockStatus);
                    })
                    .map(BizInventory::getId)
                    .collect(Collectors.toList());
            if (statusFilteredInvIds.isEmpty()) return new PageResult<>(List.of(), 0L, pageNum, pageSize);
        }

        final List<Long> finalMatIds = matIdFilter;
        final List<Long> finalWhIds = whIdList;
        final List<Long> finalInvIds = statusFilteredInvIds;
        LambdaQueryWrapper<BizInventory> wrapper = new LambdaQueryWrapper<BizInventory>()
                .in(finalMatIds != null && !finalMatIds.isEmpty(), BizInventory::getMaterialId, finalMatIds)
                .in(finalWhIds != null && !finalWhIds.isEmpty(), BizInventory::getWarehouseId, finalWhIds)
                .in(finalInvIds != null, BizInventory::getId, finalInvIds)
                .orderByDesc(BizInventory::getUpdateTime);
        // 零库存单独过滤（直接用库存字段）
        if ("stockout".equals(stockStatus)) {
            wrapper.le(BizInventory::getAvailableQty, BigDecimal.ZERO);
        }

        Page<BizInventory> page = page(new Page<>(pageNum, pageSize), wrapper);

        // 批量加载物料名称、仓库名称、库位名称
        List<BizInventory> records = page.getRecords();
        Set<Long> matIds = records.stream().map(BizInventory::getMaterialId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> allLocIds = new HashSet<>();
        records.forEach(inv -> {
            if (inv.getWarehouseId() != null) allLocIds.add(inv.getWarehouseId());
            if (inv.getLocationId() != null) allLocIds.add(inv.getLocationId());
        });
        Map<Long, BizMaterial> matMap = matIds.isEmpty() ? new HashMap<>() :
                materialMapper.selectBatchIds(matIds).stream().collect(Collectors.toMap(BizMaterial::getId, m -> m, (a, b) -> a));
        Map<Long, String> whNameMap = allLocIds.isEmpty() ? new HashMap<>() :
                warehouseMapper.selectBatchIds(allLocIds).stream().collect(Collectors.toMap(SysWarehouse::getId, SysWarehouse::getWarehouseName, (a, b) -> a));

        // 无 standardCost 的物料，从最近采购价/外协价回退
        Set<Long> missingCostMatIds = matIds.stream()
                .filter(id -> {
                    BizMaterial mm = matMap.get(id);
                    return mm != null && mm.getStandardCost() == null;
                }).collect(Collectors.toSet());
        Map<Long, BigDecimal> fallbackCostMap = new HashMap<>();
        if (!missingCostMatIds.isEmpty()) {
            // 1) 采购订单最近价
            // 加 LIMIT — 只需要每物料一条最近价做回退,fallbackCostMap.putIfAbsent 后续都是浪费。
            // 远程 MySQL 慢网络下,无 LIMIT 拉全表 ORDER BY 经常 5 分钟 socket 读超时。
            // 取 missingCostMatIds.size() * 5 条已经远超"每物料一条"所需,且单页一般不超过 100 物料
            int poFetchLimit = Math.max(500, missingCostMatIds.size() * 5);
            List<BizPurchaseOrderItem> items = purchaseOrderItemMapper.selectList(
                    new LambdaQueryWrapper<BizPurchaseOrderItem>()
                            .in(BizPurchaseOrderItem::getMaterialId, missingCostMatIds)
                            .isNotNull(BizPurchaseOrderItem::getUnitPrice)
                            .orderByDesc(BizPurchaseOrderItem::getCreateTime)
                            .last("LIMIT " + poFetchLimit));
            for (BizPurchaseOrderItem it : items) {
                fallbackCostMap.putIfAbsent(it.getMaterialId(), it.getUnitPrice());
            }
            // 2) 外协订单最近价（针对无采购价的物料）
            Set<Long> stillMissing = missingCostMatIds.stream()
                    .filter(id -> !fallbackCostMap.containsKey(id)).collect(Collectors.toSet());
            if (!stillMissing.isEmpty()) {
                int ooFetchLimit = Math.max(500, stillMissing.size() * 5);
                List<BizOutsourceOrder> ooItems = outsourceOrderMapper.selectList(
                        new LambdaQueryWrapper<BizOutsourceOrder>()
                                .and(w -> w.in(BizOutsourceOrder::getProductId, stillMissing)
                                        .or().in(BizOutsourceOrder::getReceiptMaterialId, stillMissing))
                                .isNotNull(BizOutsourceOrder::getUnitPrice)
                                .orderByDesc(BizOutsourceOrder::getCreateTime)
                                .last("LIMIT " + ooFetchLimit));
                for (BizOutsourceOrder oo : ooItems) {
                    Long mid = oo.getReceiptMaterialId() != null ? oo.getReceiptMaterialId() : oo.getProductId();
                    if (mid != null) fallbackCostMap.putIfAbsent(mid, oo.getUnitPrice());
                }
            }
        }

        List<Map<String, Object>> list = records.stream().map(inv -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", inv.getId());
            m.put("materialId", inv.getMaterialId());
            BizMaterial mat = matMap.get(inv.getMaterialId());
            m.put("materialName", mat != null ? mat.getMaterialName() : "");
            m.put("materialNo", mat != null ? mat.getMaterialNo() : "");
            m.put("spec", mat != null ? mat.getSpec() : "");
            m.put("unit", mat != null ? mat.getUnit() : "");
            m.put("category", mat != null ? mat.getCategory() : "");
            m.put("warehouseId", inv.getWarehouseId());
            m.put("warehouseName", whNameMap.getOrDefault(inv.getWarehouseId(), ""));
            m.put("locationId", inv.getLocationId());
            m.put("locationName", whNameMap.getOrDefault(inv.getLocationId(), ""));
            m.put("totalQty", inv.getTotalQty());
            m.put("lockedQty", inv.getLockedQty());
            m.put("availableQty", inv.getAvailableQty());
            m.put("minStock", mat != null ? mat.getMinStock() : null);
            m.put("maxStock", mat != null ? mat.getMaxStock() : null);
            m.put("safetyStock", mat != null ? mat.getSafetyStock() : null);
            BigDecimal cost = mat != null ? mat.getStandardCost() : null;
            if (cost == null && mat != null) cost = fallbackCostMap.get(mat.getId());
            m.put("costPrice", cost);
            m.put("secondaryUnit", inv.getSecondaryUnit());
            m.put("secondaryTotalQty", inv.getSecondaryTotalQty());
            m.put("secondaryAvailableQty", inv.getSecondaryAvailableQty());
            m.put("secondaryLockedQty", inv.getSecondaryLockedQty());
            m.put("unitConfig", mat != null ? mat.getUnitConfig() : null);
            m.put("updateTime", inv.getUpdateTime());
            return m;
        }).collect(Collectors.toList());

        return new PageResult<>(list, page.getTotal(), pageNum, pageSize);
    }

    /**
     * 判断可用库存是否符合指定状态（基于物料最小/最大库存）
     * shortage: 可用量 < 最小库存（且最小库存已设置）
     * excess:   可用量 > 最大库存（且最大库存已设置）
     * normal:   不满足 shortage 也不满足 excess
     */
    private boolean matchesStockStatus(BigDecimal avail, BigDecimal minStock, BigDecimal maxStock, String status) {
        BigDecimal min = minStock != null ? minStock : BigDecimal.ZERO;
        boolean hasMin = min.compareTo(BigDecimal.ZERO) > 0;
        boolean hasMax = maxStock != null && maxStock.compareTo(BigDecimal.ZERO) > 0;
        boolean shortage = hasMin && avail.compareTo(min) < 0;
        boolean excess = hasMax && avail.compareTo(maxStock) > 0;
        return switch (status) {
            case "shortage" -> shortage;
            case "excess" -> excess;
            case "alert" -> shortage || excess;
            case "normal" -> !shortage && !excess;
            default -> true;
        };
    }

    /**
     * 入库操作
     * 1. 创建入库流水记录(biz_inv_transaction)
     * 2. 更新库存台账: total_qty += qty, available_qty += qty
     * 3. 如果库存记录不存在则自动创建
     *
     * @param materialId  物料ID
     * @param warehouseId 仓库ID
     * @param qty         入库数量（必须 > 0）
     * @param type        入库类型(purchase_in/production_in/semi_in/outsource_in/return_in/other_in)
     * @param batchNo     批次号
     * @param relatedType 关联单据类型
     * @param relatedId   关联单据ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void stockIn(Long materialId, Long warehouseId, BigDecimal qty,
                        String type, String batchNo, String relatedType, Long relatedId) {
        stockIn(materialId, warehouseId, qty, type, batchNo, relatedType, relatedId, null, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void stockIn(Long materialId, Long warehouseId, BigDecimal qty,
                        String type, String batchNo, String relatedType, Long relatedId,
                        String secondaryUnit, BigDecimal secondaryQty) {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("入库数量必须大于0");
        }
        SecondaryStock secondaryStock = resolveSecondaryStock(materialId, qty, secondaryUnit, secondaryQty);

        // 创建入库流水记录，数量为正值
        BizInvTransaction transaction = new BizInvTransaction();
        transaction.setMaterialId(materialId);
        transaction.setWarehouseId(warehouseId);
        transaction.setTransactionType(type); // 使用具体入库类型
        transaction.setQuantity(qty);
        transaction.setBatchNo(batchNo);
        transaction.setRelatedOrderType(relatedType);
        transaction.setRelatedOrderId(relatedId);
        transaction.setSecondaryUnit(secondaryStock.unit());
        transaction.setSecondaryQty(secondaryStock.qty());
        transactionMapper.insert(transaction);

        // 更新库存台账
        BizInventory inventory = getOrCreateInventory(materialId, warehouseId, null);
        inventory.setTotalQty(inventory.getTotalQty().add(qty));
        inventory.setAvailableQty(inventory.getAvailableQty().add(qty));
        // 辅助单位库存
        if (secondaryStock.unit() != null && secondaryStock.qty() != null) {
            if (inventory.getSecondaryUnit() == null) {
                inventory.setSecondaryUnit(secondaryStock.unit());
            }
            inventory.setSecondaryTotalQty(nz(inventory.getSecondaryTotalQty()).add(secondaryStock.qty()));
            inventory.setSecondaryAvailableQty(nz(inventory.getSecondaryAvailableQty()).add(secondaryStock.qty()));
        }
        updateById(inventory);
    }

    /**
     * 出库操作
     * 1. 校验可用库存是否充足
     * 2. 创建出库流水记录(biz_inv_transaction)，数量为负值
     * 3. 更新库存台账: total_qty -= qty, available_qty -= qty
     *
     * @param materialId  物料ID
     * @param warehouseId 仓库ID
     * @param qty         出库数量（必须 > 0）
     * @param type        出库类型(production_out/sales_out/outsource_out/scrap_out/other_out)
     * @param batchNo     批次号
     * @param relatedType 关联单据类型
     * @param relatedId   关联单据ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void stockOut(Long materialId, Long warehouseId, BigDecimal qty,
                         String type, String batchNo, String relatedType, Long relatedId) {
        stockOut(materialId, warehouseId, qty, type, batchNo, relatedType, relatedId, null, null, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void stockOut(Long materialId, Long warehouseId, BigDecimal qty,
                         String type, String batchNo, String relatedType, Long relatedId,
                         String secondaryUnit, BigDecimal secondaryQty) {
        stockOut(materialId, warehouseId, qty, type, batchNo, relatedType, relatedId,
                secondaryUnit, secondaryQty, null);
    }

    /**
     * 出库 — 带 excludeInvTaskItemId 重载。
     * 仓管在处理某条 pending 出库任务行时,把该 item.id 传进来,这样校验"真实可用"时不会
     * 把当前正在处理的这条任务自身算成预占,避免"自己卡死自己"。
     */
    @Transactional(rollbackFor = Exception.class)
    public void stockOut(Long materialId, Long warehouseId, BigDecimal qty,
                         String type, String batchNo, String relatedType, Long relatedId,
                         String secondaryUnit, BigDecimal secondaryQty, Long excludeInvTaskItemId) {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("出库数量必须大于0");
        }
        SecondaryStock secondaryStock = resolveSecondaryStock(materialId, qty, secondaryUnit, secondaryQty);

        // 生产领料时：先释放本工单的锁料预留，恢复 availableQty，再检查并出库
        if ("production_out".equals(type) && "work_order".equals(relatedType) && relatedId != null) {
            consumeReservationBeforeIssue(materialId, warehouseId, relatedId, qty);
        }

        BizInventory inventory = getOne(new LambdaQueryWrapper<BizInventory>()
                .eq(BizInventory::getMaterialId, materialId)
                .eq(warehouseId != null, BizInventory::getWarehouseId, warehouseId));

        if (inventory == null) {
            throw new BusinessException("库存记录不存在");
        }
        // 出库校验只看台账可用(= total_qty - locked_qty), 不再减 pendingOut
        // pendingOut 是计划层面的虚占, 不应该卡住实物出库
        BigDecimal realAvailable = inventory.getAvailableQty() != null ? inventory.getAvailableQty() : BigDecimal.ZERO;
        if (realAvailable.compareTo(qty) < 0) {
            // 允许负库存时跳过此校验，否则抛出库存不足异常
            boolean allowNegative = bizConfigService.getBoolValue("warehouse.allow_negative", false);
            if (!allowNegative) {
                throw new BusinessException("可用库存不足, 可用: " + realAvailable + ", 需要: " + qty);
            }
        }

        // 创建出库流水记录，数量存储为负值（表示出库）
        BizInvTransaction transaction = new BizInvTransaction();
        transaction.setMaterialId(materialId);
        transaction.setWarehouseId(warehouseId);
        transaction.setTransactionType(type); // 使用具体出库类型
        transaction.setQuantity(qty.negate()); // 出库为负数
        transaction.setBatchNo(batchNo);
        transaction.setRelatedOrderType(relatedType);
        transaction.setRelatedOrderId(relatedId);
        transaction.setSecondaryUnit(secondaryStock.unit());
        transaction.setSecondaryQty(secondaryStock.qty() != null ? secondaryStock.qty().negate() : null);
        transactionMapper.insert(transaction);

        // 更新库存台账
        inventory.setTotalQty(inventory.getTotalQty().subtract(qty));
        inventory.setAvailableQty(inventory.getAvailableQty().subtract(qty));
        // 辅助单位库存
        if (secondaryStock.unit() != null && secondaryStock.qty() != null) {
            if (inventory.getSecondaryUnit() == null) {
                inventory.setSecondaryUnit(secondaryStock.unit());
            }
            inventory.setSecondaryTotalQty(nz(inventory.getSecondaryTotalQty()).subtract(secondaryStock.qty()));
            inventory.setSecondaryAvailableQty(nz(inventory.getSecondaryAvailableQty()).subtract(secondaryStock.qty()));
        }
        updateById(inventory);

        // 生产领料 → 更新BOM快照已领量（锁料已在前面预释放）
        if ("production_out".equals(type) && "work_order".equals(relatedType) && relatedId != null) {
            updateBomSnapshotIssuedQty(materialId, relatedId, qty);
        }

        // 销售出库 → 生成应收 + 更新订单发货状态
        if ("sales_out".equals(type) && "sales_order".equals(relatedType) && relatedId != null) {
            handleSalesOut(relatedId, materialId, qty);
        }

        // 安全库存预警 → 自动生成采购申请
        checkSafetyStock(inventory, materialId);
    }

    /**
     * 生产领料前：先释放本工单的锁料预留，恢复 availableQty
     * 解决问题：工单创建时自动锁料导致 availableQty=0，stockOut 检查 availableQty 时失败
     * 此方法在 stockOut 的 availableQty 检查之前调用
     */
    private void consumeReservationBeforeIssue(Long materialId, Long warehouseId, Long workOrderId, BigDecimal qty) {
        try {
            List<BizInvReservation> reservations = reservationMapper.selectList(
                    new LambdaQueryWrapper<BizInvReservation>()
                            .eq(BizInvReservation::getReservationType, "wo_lock")
                            .eq(BizInvReservation::getRelatedOrderType, "work_order")
                            .eq(BizInvReservation::getRelatedOrderId, workOrderId)
                            .eq(BizInvReservation::getMaterialId, materialId)
                            .eq(BizInvReservation::getWarehouseId, warehouseId)
                            .eq(BizInvReservation::getStatus, "active"));

            if (reservations.isEmpty()) return;

            BigDecimal remaining = qty;
            for (BizInvReservation reservation : reservations) {
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;

                BigDecimal canConsume = reservation.getRemainingQty().min(remaining);
                reservation.setConsumedQty(reservation.getConsumedQty().add(canConsume));
                reservation.setRemainingQty(reservation.getRemainingQty().subtract(canConsume));
                if (reservation.getRemainingQty().compareTo(BigDecimal.ZERO) <= 0) {
                    reservation.setStatus("consumed");
                }
                reservationMapper.updateById(reservation);
                remaining = remaining.subtract(canConsume);

                // 恢复库存：lockedQty 减少，availableQty 增加（释放锁定回可用）
                BizInventory inventory = getOne(new LambdaQueryWrapper<BizInventory>()
                        .eq(BizInventory::getMaterialId, materialId)
                        .eq(warehouseId != null, BizInventory::getWarehouseId, warehouseId));
                if (inventory != null) {
                    inventory.setLockedQty(inventory.getLockedQty().subtract(canConsume));
                    inventory.setAvailableQty(inventory.getAvailableQty().add(canConsume));
                    updateById(inventory);
                }
            }
        } catch (Exception e) {
            log.warn("预释放锁料失败: materialId={}, workOrderId={}", materialId, workOrderId, e);
        }
    }

    /**
     * 更新BOM快照已领量
     */
    private void updateBomSnapshotIssuedQty(Long materialId, Long workOrderId, BigDecimal qty) {
        try {
            BizWoBomSnapshot snapshot = bomSnapshotMapper.selectOne(
                    new LambdaQueryWrapper<BizWoBomSnapshot>()
                            .eq(BizWoBomSnapshot::getWorkOrderId, workOrderId)
                            .eq(BizWoBomSnapshot::getMaterialId, materialId));
            if (snapshot != null) {
                BigDecimal issued = snapshot.getIssuedQty() != null ? snapshot.getIssuedQty() : BigDecimal.ZERO;
                snapshot.setIssuedQty(issued.add(qty));
                bomSnapshotMapper.updateById(snapshot);
            }
        } catch (Exception e) {
            log.warn("更新BOM快照已领量失败: materialId={}, workOrderId={}", materialId, workOrderId, e);
        }
    }

    /**
     * 安全库存检查：出库后如果可用库存低于安全库存，自动生成采购申请
     */
    private void checkSafetyStock(BizInventory inventory, Long materialId) {
        try {
            if (inventory.getSafetyStock() != null
                    && inventory.getSafetyStock().compareTo(BigDecimal.ZERO) > 0
                    && inventory.getAvailableQty().compareTo(inventory.getSafetyStock()) < 0) {

                // 检查是否已有该物料的待处理采购申请
                long pendingCount = purchaseRequestMapper.selectCount(
                        new LambdaQueryWrapper<BizPurchaseRequest>()
                                .eq(BizPurchaseRequest::getMaterialId, materialId)
                                .eq(BizPurchaseRequest::getStatus, "pending"));
                if (pendingCount > 0) return;

                // 计算建议采购量: 安全库存×2 - 当前可用
                BigDecimal suggestedQty = inventory.getSafetyStock().multiply(new BigDecimal("2"))
                        .subtract(inventory.getAvailableQty());
                if (suggestedQty.compareTo(BigDecimal.ZERO) <= 0) {
                    suggestedQty = inventory.getSafetyStock();
                }

                BizMaterial material = materialMapper.selectById(materialId);
                BizPurchaseRequest request = new BizPurchaseRequest();
                request.setRequestNo(CodeUtil.generate("PR"));
                request.setMaterialId(materialId);
                request.setMaterialName(material != null ? material.getMaterialName() : null);
                request.setSpec(material != null ? material.getSpec() : null);
                request.setUnit(material != null ? material.getUnit() : null);
                request.setQuantity(suggestedQty);
                request.setSuggestedSupplierId(material != null ? material.getDefaultSupplierId() : null);
                request.setSourceType("safety_trigger");
                request.setStatus("pending");
                request.setRemark("安全库存预警自动生成");
                purchaseRequestMapper.insert(request);

                // 通知仓库角色
                notificationService.notifyRole("warehouse", "安全库存预警",
                        "物料[" + (material != null ? material.getMaterialName() : materialId) + "]库存不足, 已自动生成采购申请",
                        "alert", "purchase_request", request.getId());

                log.info("安全库存预警自动生成采购申请: materialId={}, suggestedQty={}", materialId, suggestedQty);
            }
        } catch (Exception e) {
            log.warn("安全库存检查失败: materialId={}", materialId, e);
        }
    }

    /**
     * 锁定库存（锁库锁料）- 简化重载，无计划行项关联
     */
    @Transactional(rollbackFor = Exception.class)
    public void lockStock(Long materialId, Long warehouseId, BigDecimal qty,
                          String type, String relatedType, Long relatedId, String reason) {
        lockStock(materialId, warehouseId, qty, type, relatedType, relatedId, null, reason, null);
    }

    /**
     * 锁定库存（锁库锁料）
     * 1. 校验可用库存是否充足
     * 2. 创建预留记录(biz_inv_reservation)
     * 3. 更新库存: locked_qty += qty, available_qty -= qty
     *
     * @param materialId     物料ID
     * @param warehouseId    仓库ID
     * @param qty            锁定数量
     * @param type           预留类型(wo_lock/order_lock/manual_lock/plan_lock)
     * @param relatedType    关联单据类型(work_order/sales_order/production_plan等)
     * @param relatedId      关联单据ID
     * @param relatedOrderNo 关联单据号(如WO202501001，便于追溯展示)
     * @param reason         锁定原因
     * @param planItemId     生产计划行项ID（plan_lock时使用，其他类型传null）
     */
    @Transactional(rollbackFor = Exception.class)
    public void lockStock(Long materialId, Long warehouseId, BigDecimal qty,
                          String type, String relatedType, Long relatedId,
                          String relatedOrderNo, String reason, Long planItemId) {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("锁定数量必须大于0");
        }

        BizInventory inventory = getOne(new LambdaQueryWrapper<BizInventory>()
                .eq(BizInventory::getMaterialId, materialId)
                .eq(BizInventory::getWarehouseId, warehouseId));

        if (inventory == null) {
            throw new BusinessException("库存记录不存在");
        }
        if (nz(inventory.getAvailableQty()).compareTo(qty) < 0) {
            throw new BusinessException("可用库存不足，无法锁定");
        }

        // 创建预留记录
        BizInvReservation reservation = new BizInvReservation();
        reservation.setMaterialId(materialId);
        reservation.setWarehouseId(warehouseId);
        reservation.setReservationType(type);
        reservation.setLockedQty(qty);
        reservation.setConsumedQty(BigDecimal.ZERO);
        reservation.setRemainingQty(qty);
        reservation.setRelatedOrderType(relatedType);
        reservation.setRelatedOrderId(relatedId);
        reservation.setRelatedOrderNo(relatedOrderNo);
        reservation.setPlanItemId(planItemId);
        reservation.setStatus("active");
        reservation.setReason(reason);
        reservationMapper.insert(reservation);

        // 更新库存锁定数量
        inventory.setLockedQty(nz(inventory.getLockedQty()).add(qty));
        inventory.setAvailableQty(nz(inventory.getAvailableQty()).subtract(qty));
        updateById(inventory);
    }

    /**
     * 释放库存锁定
     * 将预留记录标记为released，归还剩余锁定量到可用库存
     * 场景: 工单取消时释放关联锁料、手动释放
     *
     * @param reservationId 预留记录ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void unlockStock(Long reservationId) {
        BizInvReservation reservation = reservationMapper.selectById(reservationId);
        if (reservation == null) {
            throw new BusinessException("预留记录不存在");
        }
        if (!"active".equals(reservation.getStatus())) {
            throw new BusinessException("该预留已释放或已消耗");
        }

        // 释放剩余锁定量
        BizInventory inventory = getOne(new LambdaQueryWrapper<BizInventory>()
                .eq(BizInventory::getMaterialId, reservation.getMaterialId())
                .eq(reservation.getWarehouseId() != null, BizInventory::getWarehouseId, reservation.getWarehouseId()));

        if (inventory != null) {
            inventory.setLockedQty(inventory.getLockedQty().subtract(reservation.getRemainingQty()));
            inventory.setAvailableQty(inventory.getAvailableQty().add(reservation.getRemainingQty()));
            updateById(inventory);
        }

        reservation.setStatus("released");
        reservationMapper.updateById(reservation);
    }

    /**
     * 获取指定物料在指定仓库的可用库存
     */
    public BigDecimal getStockQty(Long materialId, Long warehouseId) {
        BizInventory inventory = getOne(new LambdaQueryWrapper<BizInventory>()
                .eq(BizInventory::getMaterialId, materialId)
                .eq(warehouseId != null, BizInventory::getWarehouseId, warehouseId));
        return inventory != null ? inventory.getTotalQty() : BigDecimal.ZERO;
    }

    /**
     * 获取指定物料在指定仓库的库存信息 [在库数量, 锁定数量, 可用数量]
     */
    public BigDecimal[] getStockInfo(Long materialId, Long warehouseId) {
        BizInventory inventory = getOne(new LambdaQueryWrapper<BizInventory>()
                .eq(BizInventory::getMaterialId, materialId)
                .eq(warehouseId != null, BizInventory::getWarehouseId, warehouseId));
        if (inventory == null) {
            return new BigDecimal[]{ BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO };
        }
        return new BigDecimal[]{
                inventory.getTotalQty() != null ? inventory.getTotalQty() : BigDecimal.ZERO,
                inventory.getLockedQty() != null ? inventory.getLockedQty() : BigDecimal.ZERO,
                inventory.getAvailableQty() != null ? inventory.getAvailableQty() : BigDecimal.ZERO
        };
    }

    /**
     * 获取指定物料在指定仓库的可用库存
     * = 台账 availableQty(=totalQty - lockedQty)
     *   - 该物料该仓库下"未完成的出库类库存任务"剩余未处理量(quantity - processedQty)
     *
     * 这样领料/发货/外协等场景下,已经创建但未实际出库的任务量也会从可用中扣除,
     * 避免"显示 1300 可用,但其中 200 已被另一张未处理的发货任务占用"导致的不一致。
     */
    public BigDecimal getAvailableQty(Long materialId, Long warehouseId) {
        return getAvailableQty(materialId, warehouseId, null);
    }

    /**
     * 同 {@link #getAvailableQty(Long, Long)} —— 现统一为台账可用 (= total_qty - locked_qty)
     * 不再减 pending 出库任务预占, 与出库实际校验语义保持一致。excludeInvTaskItemId 入参保留以兼容历史调用。
     */
    public BigDecimal getAvailableQty(Long materialId, Long warehouseId, Long excludeInvTaskItemId) {
        if (warehouseId == null) return getTotalAvailableQty(materialId, excludeInvTaskItemId);
        BizInventory inventory = getOne(new LambdaQueryWrapper<BizInventory>()
                .eq(BizInventory::getMaterialId, materialId)
                .eq(BizInventory::getWarehouseId, warehouseId));
        return inventory != null && inventory.getAvailableQty() != null
                ? inventory.getAvailableQty() : BigDecimal.ZERO;
    }

    /** 汇总该物料在所有仓库的台账可用库存 */
    public BigDecimal getTotalAvailableQty(Long materialId) {
        return getTotalAvailableQty(materialId, null);
    }

    /**
     * 汇总该物料在所有仓库的"在库总量"(total_qty), 不扣 locked_qty.
     * 给"无 BOM 工单临时领料判缺"等场景用 —— 锁库存只是别的单据的预约,
     * 不应阻止工人按实物库存判定是否需要采购.
     */
    public BigDecimal getTotalStockQty(Long materialId) {
        if (materialId == null) return BigDecimal.ZERO;
        List<BizInventory> invs = list(new LambdaQueryWrapper<BizInventory>()
                .eq(BizInventory::getMaterialId, materialId)
                .eq(BizInventory::getDeleted, 0));
        return invs.stream()
                .map(i -> i.getTotalQty() != null ? i.getTotalQty() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getTotalAvailableQty(Long materialId, Long excludeInvTaskItemId) {
        if (materialId == null) return BigDecimal.ZERO;
        List<BizInventory> invs = list(new LambdaQueryWrapper<BizInventory>()
                .eq(BizInventory::getMaterialId, materialId)
                .eq(BizInventory::getDeleted, 0));
        return invs.stream()
                .map(i -> i.getAvailableQty() != null ? i.getAvailableQty() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 公开重载: 该物料下所有未完成出库类任务的剩余量之和(全仓汇总)。
     * 主计划/排产等口径计算"真正可用"时, 应从可用库存里再扣这部分 —
     * 否则别的销售订单已派发未发货 / 别的工单已派发未领料的库存, 会被当成可用重复占用。
     */
    public BigDecimal getPendingOutboundQty(Long materialId) {
        return getPendingOutboundQty(materialId, null, null);
    }

    /**
     * 该物料(可选限定仓库) 下,所有未完成出库类库存任务的剩余未处理量之和。
     * 出库类: production_out / sales_out / outsource_out / scrap_out / other_out。
     * 未完成: status != completed && status != cancelled。
     * 当传入 excludeInvTaskItemId 时,跳过该 item — 用于仓管处理自身任务时的库存校验。
     */
    private BigDecimal getPendingOutboundQty(Long materialId, Long warehouseId, Long excludeInvTaskItemId) {
        try {
            List<com.aimes.modules.inventory.entity.BizInvTask> tasks = invTaskMapper.selectList(
                    new LambdaQueryWrapper<com.aimes.modules.inventory.entity.BizInvTask>()
                            .in(com.aimes.modules.inventory.entity.BizInvTask::getInvType, OUTBOUND_INV_TYPES)
                            .notIn(com.aimes.modules.inventory.entity.BizInvTask::getStatus,
                                    java.util.List.of("completed", "cancelled"))
                            .select(com.aimes.modules.inventory.entity.BizInvTask::getId));
            if (tasks.isEmpty()) return BigDecimal.ZERO;
            java.util.Set<Long> taskIds = tasks.stream()
                    .map(com.aimes.modules.inventory.entity.BizInvTask::getId)
                    .collect(java.util.stream.Collectors.toSet());

            LambdaQueryWrapper<com.aimes.modules.inventory.entity.BizInvTaskItem> iw =
                    new LambdaQueryWrapper<com.aimes.modules.inventory.entity.BizInvTaskItem>()
                            .in(com.aimes.modules.inventory.entity.BizInvTaskItem::getTaskId, taskIds)
                            .eq(com.aimes.modules.inventory.entity.BizInvTaskItem::getMaterialId, materialId);
            if (warehouseId != null) {
                iw.eq(com.aimes.modules.inventory.entity.BizInvTaskItem::getWarehouseId, warehouseId);
            }
            if (excludeInvTaskItemId != null) {
                iw.ne(com.aimes.modules.inventory.entity.BizInvTaskItem::getId, excludeInvTaskItemId);
            }
            List<com.aimes.modules.inventory.entity.BizInvTaskItem> items = invTaskItemMapper.selectList(iw);
            return items.stream().map(it -> {
                BigDecimal q = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ZERO;
                BigDecimal p = it.getProcessedQty() != null ? it.getProcessedQty() : BigDecimal.ZERO;
                BigDecimal remain = q.subtract(p);
                return remain.compareTo(BigDecimal.ZERO) > 0 ? remain : BigDecimal.ZERO;
            }).reduce(BigDecimal.ZERO, BigDecimal::add);
        } catch (Exception e) {
            log.warn("计算未完成出库任务量失败: materialId={}, warehouseId={}", materialId, warehouseId, e);
            return BigDecimal.ZERO;
        }
    }

    /**
     * 查询出入库记录（分页）
     */
    public PageResult<Map<String, Object>> getTransactions(Long materialId, Long warehouseId,
                                                          String type, int pageNum, int pageSize) {
        return getTransactions(materialId, warehouseId, type, null, null, pageNum, pageSize);
    }

    public PageResult<Map<String, Object>> getTransactions(Long materialId, Long warehouseId,
                                                          String type, String keyword, String materialName,
                                                          int pageNum, int pageSize) {
        // 按物料名称筛选：先查匹配的物料ID
        Set<Long> materialIdFilter = null;
        if (StringUtils.hasText(materialName)) {
            List<Long> matchedIds = materialMapper.selectList(
                    new LambdaQueryWrapper<BizMaterial>().like(BizMaterial::getMaterialName, materialName))
                    .stream().map(BizMaterial::getId).collect(java.util.stream.Collectors.toList());
            if (matchedIds.isEmpty()) {
                return new PageResult<>(List.of(), 0L, pageNum, pageSize);
            }
            materialIdFilter = new java.util.HashSet<>(matchedIds);
        }
        // keyword 兼容：按物料名称模糊匹配
        if (StringUtils.hasText(keyword) && materialIdFilter == null) {
            List<Long> matchedIds = materialMapper.selectList(
                    new LambdaQueryWrapper<BizMaterial>().like(BizMaterial::getMaterialName, keyword))
                    .stream().map(BizMaterial::getId).collect(java.util.stream.Collectors.toList());
            if (!matchedIds.isEmpty()) {
                materialIdFilter = new java.util.HashSet<>(matchedIds);
            }
        }

        LambdaQueryWrapper<BizInvTransaction> wrapper = new LambdaQueryWrapper<BizInvTransaction>()
                .eq(materialId != null, BizInvTransaction::getMaterialId, materialId)
                .eq(warehouseId != null, BizInvTransaction::getWarehouseId, warehouseId)
                .eq(StringUtils.hasText(type), BizInvTransaction::getTransactionType, type)
                .orderByDesc(BizInvTransaction::getCreateTime);
        if (materialIdFilter != null) {
            wrapper.in(BizInvTransaction::getMaterialId, materialIdFilter);
        }

        Page<BizInvTransaction> page = transactionMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        // 批量加载物料和仓库名称
        List<BizInvTransaction> records = page.getRecords();
        Set<Long> matIds = records.stream().map(BizInvTransaction::getMaterialId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> whIds = records.stream().map(BizInvTransaction::getWarehouseId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, BizMaterial> matMap2 = matIds.isEmpty() ? new HashMap<>() :
                materialMapper.selectBatchIds(matIds).stream().collect(Collectors.toMap(BizMaterial::getId, m2 -> m2, (a, b) -> a));
        Map<Long, String> matNameMap = matMap2.values().stream().collect(Collectors.toMap(BizMaterial::getId, BizMaterial::getMaterialName, (a, b) -> a));
        Map<Long, String> whNameMap = whIds.isEmpty() ? new HashMap<>() :
                warehouseMapper.selectBatchIds(whIds).stream().collect(Collectors.toMap(SysWarehouse::getId, SysWarehouse::getWarehouseName, (a, b) -> a));
        Set<Long> userIds = records.stream().map(BizInvTransaction::getCreateBy).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> userNameMap = userIds.isEmpty() ? new HashMap<>() :
                userMapper.selectBatchIds(userIds).stream().collect(Collectors.toMap(SysUser::getId, SysUser::getRealName, (a, b) -> a));

        // 流水自己的 remark 多数为空 (stockIn 时未填). 兜底用关联源单的 remark:
        //   关联 inv_request → request.remark
        //   关联 inv_task → task.remark
        //   关联 inv_history → history.remark
        Map<Long, String> requestRemarkMap = new HashMap<>();
        Map<Long, String> taskRemarkMap = new HashMap<>();
        Map<Long, String> historyRemarkMap = new HashMap<>();
        // 单号回查表 (用于"关联单据"列)
        Map<Long, String> requestNoMap = new HashMap<>();
        Map<Long, String> taskNoMap = new HashMap<>();
        Map<Long, String> historyNoMap = new HashMap<>();
        // wo_process_task: 关联工序任务 → 工单号; work_order: 直接工单号
        Map<Long, String> processTaskWoNoMap = new HashMap<>();
        Map<Long, String> workOrderNoMap = new HashMap<>();
        // 业务源单号
        Map<Long, String> salesOrderNoMap = new HashMap<>();
        Map<Long, String> purchaseOrderNoMap = new HashMap<>();
        Map<Long, String> outsourceOrderNoMap = new HashMap<>();
        Set<Long> reqIds = records.stream()
                .filter(t -> "inv_request".equals(t.getRelatedOrderType()) && t.getRelatedOrderId() != null)
                .map(BizInvTransaction::getRelatedOrderId).collect(Collectors.toSet());
        Set<Long> taskIds = records.stream()
                .filter(t -> "inv_task".equals(t.getRelatedOrderType()) && t.getRelatedOrderId() != null)
                .map(BizInvTransaction::getRelatedOrderId).collect(Collectors.toSet());
        Set<Long> hisIds = records.stream()
                .filter(t -> "inv_history".equals(t.getRelatedOrderType()) && t.getRelatedOrderId() != null)
                .map(BizInvTransaction::getRelatedOrderId).collect(Collectors.toSet());
        Set<Long> processTaskIds = records.stream()
                .filter(t -> "wo_process_task".equals(t.getRelatedOrderType()) && t.getRelatedOrderId() != null)
                .map(BizInvTransaction::getRelatedOrderId).collect(Collectors.toSet());
        Set<Long> directWoIds = records.stream()
                .filter(t -> "work_order".equals(t.getRelatedOrderType()) && t.getRelatedOrderId() != null)
                .map(BizInvTransaction::getRelatedOrderId).collect(Collectors.toSet());
        Set<Long> soIds = records.stream()
                .filter(t -> "sales_order".equals(t.getRelatedOrderType()) && t.getRelatedOrderId() != null)
                .map(BizInvTransaction::getRelatedOrderId).collect(Collectors.toSet());
        Set<Long> poIds = records.stream()
                .filter(t -> "purchase_order".equals(t.getRelatedOrderType()) && t.getRelatedOrderId() != null)
                .map(BizInvTransaction::getRelatedOrderId).collect(Collectors.toSet());
        Set<Long> ooIds = records.stream()
                .filter(t -> "outsource_order".equals(t.getRelatedOrderType()) && t.getRelatedOrderId() != null)
                .map(BizInvTransaction::getRelatedOrderId).collect(Collectors.toSet());
        try {
            if (!reqIds.isEmpty()) {
                invRequestMapper.selectBatchIds(reqIds).forEach(r -> {
                    requestRemarkMap.put(r.getId(), r.getRemark());
                    requestNoMap.put(r.getId(), r.getRequestNo());
                });
            }
            if (!taskIds.isEmpty()) {
                invTaskMapper.selectBatchIds(taskIds).forEach(r -> {
                    taskRemarkMap.put(r.getId(), r.getRemark());
                    // 优先用 task.relatedOrderNo (业务源单号, 如 PO/WO/SO/收料单号), 没有则用 task.taskNo
                    String no = r.getRelatedOrderNo() != null && !r.getRelatedOrderNo().isEmpty()
                            ? r.getRelatedOrderNo() : r.getTaskNo();
                    taskNoMap.put(r.getId(), no);
                });
            }
            if (!hisIds.isEmpty()) {
                invHistoryMapper.selectBatchIds(hisIds).forEach(r -> {
                    historyRemarkMap.put(r.getId(), r.getRemark());
                    String no = r.getRelatedOrderNo() != null && !r.getRelatedOrderNo().isEmpty()
                            ? r.getRelatedOrderNo() : r.getHistoryNo();
                    historyNoMap.put(r.getId(), no);
                });
            }
            // wo_process_task → 通过工序任务找到所属工单, 取工单号
            if (!processTaskIds.isEmpty()) {
                var processTasks = woProcessTaskMapper.selectBatchIds(processTaskIds);
                Set<Long> taskWoIds = processTasks.stream()
                        .map(com.aimes.modules.production.entity.BizWoProcessTask::getWorkOrderId)
                        .filter(Objects::nonNull).collect(Collectors.toSet());
                Map<Long, String> woNoMap = taskWoIds.isEmpty() ? new HashMap<>() :
                        workOrderMapper.selectBatchIds(taskWoIds).stream()
                                .collect(Collectors.toMap(
                                        com.aimes.modules.production.entity.BizWorkOrder::getId,
                                        com.aimes.modules.production.entity.BizWorkOrder::getWoNo,
                                        (a, b) -> a));
                processTasks.forEach(pt -> {
                    if (pt.getWorkOrderId() != null) {
                        processTaskWoNoMap.put(pt.getId(), woNoMap.get(pt.getWorkOrderId()));
                    }
                });
            }
            // work_order → 直接取工单号
            if (!directWoIds.isEmpty()) {
                workOrderMapper.selectBatchIds(directWoIds).forEach(w ->
                        workOrderNoMap.put(w.getId(), w.getWoNo()));
            }
            if (!soIds.isEmpty()) {
                salesOrderMapper.selectBatchIds(soIds).forEach(o ->
                        salesOrderNoMap.put(o.getId(), o.getOrderNo()));
            }
            if (!poIds.isEmpty()) {
                purchaseOrderMapper.selectBatchIds(poIds).forEach(o ->
                        purchaseOrderNoMap.put(o.getId(), o.getPoNo()));
            }
            if (!ooIds.isEmpty()) {
                outsourceOrderMapper.selectBatchIds(ooIds).forEach(o ->
                        outsourceOrderNoMap.put(o.getId(), o.getOrderNo()));
            }
        } catch (Exception ignored) {}

        List<Map<String, Object>> list = records.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("materialId", t.getMaterialId());
            m.put("materialName", matNameMap.getOrDefault(t.getMaterialId(), ""));
            m.put("warehouseId", t.getWarehouseId());
            m.put("warehouseName", whNameMap.getOrDefault(t.getWarehouseId(), ""));
            m.put("transactionType", t.getTransactionType());
            m.put("quantity", t.getQuantity());
            BizMaterial mat2 = matMap2.get(t.getMaterialId());
            m.put("costPrice", mat2 != null ? mat2.getStandardCost() : null);
            m.put("batchNo", t.getBatchNo());
            m.put("relatedOrderType", t.getRelatedOrderType());
            m.put("relatedOrderId", t.getRelatedOrderId());
            // 关联单号: 按 relatedOrderType 反查源单的单号(transaction 表没存 relatedOrderNo)
            String orderNo = null;
            if (t.getRelatedOrderId() != null) {
                String rt = t.getRelatedOrderType();
                if ("inv_request".equals(rt)) orderNo = requestNoMap.get(t.getRelatedOrderId());
                else if ("inv_task".equals(rt)) orderNo = taskNoMap.get(t.getRelatedOrderId());
                else if ("inv_history".equals(rt)) orderNo = historyNoMap.get(t.getRelatedOrderId());
                else if ("wo_process_task".equals(rt)) orderNo = processTaskWoNoMap.get(t.getRelatedOrderId());
                else if ("work_order".equals(rt)) orderNo = workOrderNoMap.get(t.getRelatedOrderId());
                else if ("sales_order".equals(rt)) orderNo = salesOrderNoMap.get(t.getRelatedOrderId());
                else if ("purchase_order".equals(rt)) orderNo = purchaseOrderNoMap.get(t.getRelatedOrderId());
                else if ("outsource_order".equals(rt)) orderNo = outsourceOrderNoMap.get(t.getRelatedOrderId());
            }
            m.put("relatedOrderNo", orderNo);
            // 优先用流水自己的 remark, 没有则按 relatedOrderType 回查源单
            String rk = t.getRemark();
            if (!StringUtils.hasText(rk) && t.getRelatedOrderId() != null) {
                String rt = t.getRelatedOrderType();
                if ("inv_request".equals(rt)) rk = requestRemarkMap.get(t.getRelatedOrderId());
                else if ("inv_task".equals(rt)) rk = taskRemarkMap.get(t.getRelatedOrderId());
                else if ("inv_history".equals(rt)) rk = historyRemarkMap.get(t.getRelatedOrderId());
            }
            m.put("remark", rk);
            m.put("operatorName", userNameMap.getOrDefault(t.getCreateBy(), ""));
            m.put("createTime", t.getCreateTime());
            return m;
        }).collect(Collectors.toList());

        return new PageResult<>(list, page.getTotal(), pageNum, pageSize);
    }

    /**
     * 批量查询物料库存汇总（当前库存=totalQty之和，可用库存=availableQty之和）
     * 直接基于台账(biz_inventory.total_qty / available_qty)汇总, 不再扣 pending 出库任务,
     * 与出库实际校验语义保持一致, 显示与可拨数对得上。
     */
    public Map<Long, Map<String, Object>> getMaterialStockSummary(List<Long> materialIds) {
        if (materialIds == null || materialIds.isEmpty()) return Map.of();
        List<BizInventory> invList = list(new LambdaQueryWrapper<BizInventory>()
                .in(BizInventory::getMaterialId, materialIds));

        Map<Long, Map<String, Object>> result = new HashMap<>();
        for (BizInventory inv : invList) {
            Map<String, Object> m = result.computeIfAbsent(inv.getMaterialId(), k -> {
                Map<String, Object> map = new HashMap<>();
                map.put("totalQty", BigDecimal.ZERO);
                map.put("availableQty", BigDecimal.ZERO);
                return map;
            });
            m.put("totalQty", ((BigDecimal) m.get("totalQty")).add(nz(inv.getTotalQty())));
            m.put("availableQty", ((BigDecimal) m.get("availableQty")).add(nz(inv.getAvailableQty())));
        }
        return result;
    }

    /**
     * 批量: 给定一组物料ID与一组仓库ID,返回 (matId+"_"+whId) -> 未完成出库类任务剩余量。
     */
    private Map<String, BigDecimal> batchPendingOutboundByMatWh(Set<Long> matIds, Set<Long> whIds) {
        Map<String, BigDecimal> result = new HashMap<>();
        if (matIds == null || matIds.isEmpty() || whIds == null || whIds.isEmpty()) return result;
        try {
            List<com.aimes.modules.inventory.entity.BizInvTask> tasks = invTaskMapper.selectList(
                    new LambdaQueryWrapper<com.aimes.modules.inventory.entity.BizInvTask>()
                            .in(com.aimes.modules.inventory.entity.BizInvTask::getInvType, OUTBOUND_INV_TYPES)
                            .notIn(com.aimes.modules.inventory.entity.BizInvTask::getStatus,
                                    java.util.List.of("completed", "cancelled"))
                            .select(com.aimes.modules.inventory.entity.BizInvTask::getId));
            if (tasks.isEmpty()) return result;
            Set<Long> taskIds = tasks.stream()
                    .map(com.aimes.modules.inventory.entity.BizInvTask::getId)
                    .collect(Collectors.toSet());

            List<com.aimes.modules.inventory.entity.BizInvTaskItem> items = invTaskItemMapper.selectList(
                    new LambdaQueryWrapper<com.aimes.modules.inventory.entity.BizInvTaskItem>()
                            .in(com.aimes.modules.inventory.entity.BizInvTaskItem::getTaskId, taskIds)
                            .in(com.aimes.modules.inventory.entity.BizInvTaskItem::getMaterialId, matIds)
                            .in(com.aimes.modules.inventory.entity.BizInvTaskItem::getWarehouseId, whIds));
            for (var it : items) {
                BigDecimal remain = nz(it.getQuantity()).subtract(nz(it.getProcessedQty()));
                if (remain.signum() <= 0) continue;
                String key = it.getMaterialId() + "_" + it.getWarehouseId();
                result.merge(key, remain, BigDecimal::add);
            }
        } catch (Exception e) {
            log.warn("批量计算未完成出库任务量失败(按物料+仓库)", e);
        }
        return result;
    }

    /**
     * 获取低于安全库存的预警列表
     */
    public List<BizInventory> getAlerts() {
        return list(new LambdaQueryWrapper<BizInventory>()
                .gt(BizInventory::getSafetyStock, BigDecimal.ZERO)
                .apply("available_qty < safety_stock")
                .orderByAsc(BizInventory::getAvailableQty));
    }

    /**
     * 销售出库联动（公开方法）：更新订单明细已发货数量 + 生成应收 + 更新订单发货状态
     * 由 BizInvTaskService.process() 在处理 sales_out 任务时调用
     */
    public void onSalesStockOut(Long orderId, Long materialId, BigDecimal qty) {
        handleSalesOut(orderId, materialId, qty);
    }

    /**
     * 销售出库 → 更新订单明细发货数量 + 更新订单发货状态
     * 注：应收账款已改为在库存任务处理(process)时按实际出库数量*单价生成
     */
    private void handleSalesOut(Long orderId, Long materialId, BigDecimal qty) {
        try {
            BizSalesOrder order = salesOrderMapper.selectById(orderId);
            if (order == null) return;

            // 更新订单明细的shippedQty（找到匹配产品的明细）
            List<BizSalesOrderItem> items = salesOrderItemMapper.selectList(
                    new LambdaQueryWrapper<BizSalesOrderItem>()
                            .eq(BizSalesOrderItem::getOrderId, orderId)
                            .eq(BizSalesOrderItem::getProductId, materialId));
            for (BizSalesOrderItem item : items) {
                BigDecimal shipped = item.getShippedQty() != null ? item.getShippedQty() : BigDecimal.ZERO;
                BigDecimal toShip = qty.min(item.getQuantity().subtract(shipped));
                if (toShip.compareTo(BigDecimal.ZERO) > 0) {
                    item.setShippedQty(shipped.add(toShip));
                    salesOrderItemMapper.updateById(item);
                    qty = qty.subtract(toShip);
                }
            }

            // 更新订单发货状态
            salesOrderService.updateOrderShipmentStatus(orderId);
        } catch (Exception e) {
            log.warn("销售出库联动失败: orderId={}", orderId, e);
        }
    }

    /**
     * 获取或创建库存记录（按物料唯一）
     */
    private BizInventory getOrCreateInventory(Long materialId, Long warehouseId, Long locationId) {
        BizInventory inventory = getOne(new LambdaQueryWrapper<BizInventory>()
                .eq(BizInventory::getMaterialId, materialId)
                .eq(warehouseId != null, BizInventory::getWarehouseId, warehouseId));

        if (inventory == null) {
            inventory = new BizInventory();
            inventory.setMaterialId(materialId);
            inventory.setWarehouseId(warehouseId);
            inventory.setLocationId(locationId);
            inventory.setTotalQty(BigDecimal.ZERO);
            inventory.setLockedQty(BigDecimal.ZERO);
            inventory.setAvailableQty(BigDecimal.ZERO);
            inventory.setSafetyStock(BigDecimal.ZERO);
            save(inventory);
        }
        return inventory;
    }

    /**
     * 新任务体系入口：通过库存任务处理时更新库存
     * isInbound=true → total_qty+qty, available_qty+qty
     * isInbound=false → total_qty-qty, available_qty-qty
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStock(Long materialId, BigDecimal qty, boolean isInbound, Long warehouseId, Long locationId) {
        updateStock(materialId, qty, isInbound, warehouseId, locationId, null, null, null);
    }

    public void updateStock(Long materialId, BigDecimal qty, boolean isInbound, Long warehouseId, Long locationId,
                            String secondaryUnit, BigDecimal secondaryQty) {
        updateStock(materialId, qty, isInbound, warehouseId, locationId, secondaryUnit, secondaryQty, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStock(Long materialId, BigDecimal qty, boolean isInbound, Long warehouseId, Long locationId,
                            String secondaryUnit, BigDecimal secondaryQty, String invType) {
        updateStock(materialId, qty, isInbound, warehouseId, locationId, secondaryUnit, secondaryQty, invType,
                null, null, null, null);
    }

    /**
     * 完整签名: 带 relatedOrderType / relatedOrderId / batchNo / remark, 写入 transaction 时不再丢失关联.
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStock(Long materialId, BigDecimal qty, boolean isInbound, Long warehouseId, Long locationId,
                            String secondaryUnit, BigDecimal secondaryQty, String invType,
                            String relatedOrderType, Long relatedOrderId, String batchNo, String remark) {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) return;
        SecondaryStock secondaryStock = resolveSecondaryStock(materialId, qty, secondaryUnit, secondaryQty);

        // 创建库存流水(biz_inv_transaction) — 入库正数,出库负数
        BizInvTransaction tx = new BizInvTransaction();
        tx.setMaterialId(materialId);
        tx.setWarehouseId(warehouseId);
        tx.setTransactionType(invType != null ? invType : (isInbound ? "stock_in" : "stock_out"));
        tx.setQuantity(isInbound ? qty : qty.negate());
        tx.setBatchNo(batchNo);
        tx.setRelatedOrderType(relatedOrderType);
        tx.setRelatedOrderId(relatedOrderId);
        tx.setRemark(remark);
        tx.setSecondaryUnit(secondaryStock.unit());
        tx.setSecondaryQty(secondaryStock.qty() != null ? (isInbound ? secondaryStock.qty() : secondaryStock.qty().negate()) : null);
        transactionMapper.insert(tx);

        // 更新库存台账
        BizInventory inventory = getOrCreateInventory(materialId, warehouseId, locationId);
        if (locationId != null) inventory.setLocationId(locationId);
        if (isInbound) {
            inventory.setTotalQty(inventory.getTotalQty().add(qty));
            inventory.setAvailableQty(inventory.getAvailableQty().add(qty));
        } else {
            inventory.setTotalQty(inventory.getTotalQty().subtract(qty));
            inventory.setAvailableQty(inventory.getAvailableQty().subtract(qty));
        }
        // 辅助单位库存
        if (secondaryStock.unit() != null && secondaryStock.qty() != null) {
            if (inventory.getSecondaryUnit() == null) {
                inventory.setSecondaryUnit(secondaryStock.unit());
            }
            if (isInbound) {
                inventory.setSecondaryTotalQty(nz(inventory.getSecondaryTotalQty()).add(secondaryStock.qty()));
                inventory.setSecondaryAvailableQty(nz(inventory.getSecondaryAvailableQty()).add(secondaryStock.qty()));
            } else {
                inventory.setSecondaryTotalQty(nz(inventory.getSecondaryTotalQty()).subtract(secondaryStock.qty()));
                inventory.setSecondaryAvailableQty(nz(inventory.getSecondaryAvailableQty()).subtract(secondaryStock.qty()));
            }
        }
        updateById(inventory);
    }
}
