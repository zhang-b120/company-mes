package com.aimes.modules.production.service;

import com.aimes.modules.inventory.entity.BizInventory;
import com.aimes.modules.inventory.service.InventoryService;
import com.aimes.modules.master.entity.BizMaterial;
import com.aimes.modules.master.mapper.BizMaterialMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 虚拟半成品管理服务
 * 负责外协链路中虚拟半成品的创建和库存管理
 * 命名规则: {物料名称}{工序名}，编码: VSM_{物料编码}_{工序名}
 * 虚拟半成品不在物料档案中展示（isVirtualSemi=1，物料列表已过滤）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VirtualSemiProductService {

    /** 外协半成品默认仓库ID */
    private static final Long DEFAULT_OUTSOURCE_WAREHOUSE_ID = 999L;

    private final BizMaterialMapper materialMapper;
    private final InventoryService inventoryService;

    /**
     * 获取或创建虚拟半成品物料记录
     * 命名: {物料名称}{工序名}  例如: 铝合金外壳下料
     * 编码: VSM_{物料编码}_{工序名}
     * 不在物料档案展示（isVirtualSemi=1）
     *
     * @param productId 产品(物料)ID
     * @param stepName  工序名称
     * @return 虚拟半成品物料
     */
    public BizMaterial getOrCreateVirtualMaterial(Long productId, String stepName) {
        BizMaterial product = materialMapper.selectById(productId);
        String productCode = product != null ? product.getMaterialNo() : "P" + productId;
        String productName = product != null ? product.getMaterialName() : "产品" + productId;
        String virtualCode = "VSM_" + productCode + "_" + stepName;

        // 检查是否已存在
        BizMaterial existing = materialMapper.selectOne(
                new LambdaQueryWrapper<BizMaterial>()
                        .eq(BizMaterial::getMaterialNo, virtualCode)
                        .eq(BizMaterial::getDeleted, 0)
                        .last("LIMIT 1"));
        if (existing != null) return existing;

        // 创建虚拟半成品: 物料名称+工序名
        BizMaterial virtualMat = new BizMaterial();
        virtualMat.setMaterialNo(virtualCode);
        virtualMat.setMaterialName(productName + stepName);
        virtualMat.setSpec(product != null ? product.getSpec() : null);
        virtualMat.setCategory("semi_finished");
        virtualMat.setCategoryId(2L);
        virtualMat.setUnit(product != null ? product.getUnit() : "个");
        virtualMat.setIsVirtualSemi(1);
        virtualMat.setDefaultWarehouseId(DEFAULT_OUTSOURCE_WAREHOUSE_ID);
        virtualMat.setStatus("active");
        virtualMat.setSafetyStock(BigDecimal.ZERO);
        virtualMat.setMrpEnabled(false);
        materialMapper.insert(virtualMat);

        log.info("创建虚拟半成品: name={}, code={}", productName + stepName, virtualCode);
        return virtualMat;
    }

    /**
     * 虚拟半成品入库（指定仓库）
     */
    public void stockIn(Long virtualMaterialId, Long warehouseId, BigDecimal qty, String relatedType, Long relatedId) {
        Long wid = warehouseId;
        if (wid == null) {
            BizMaterial mat = materialMapper.selectById(virtualMaterialId);
            wid = mat != null && mat.getDefaultWarehouseId() != null ? mat.getDefaultWarehouseId() : DEFAULT_OUTSOURCE_WAREHOUSE_ID;
        }
        inventoryService.stockIn(virtualMaterialId, wid, qty, "semi_in", null, relatedType, relatedId);
    }

    /**
     * 虚拟半成品入库（使用物料默认仓库）
     */
    public void stockIn(Long virtualMaterialId, BigDecimal qty, String relatedType, Long relatedId) {
        stockIn(virtualMaterialId, null, qty, relatedType, relatedId);
    }

    /**
     * 虚拟半成品出库
     */
    public void stockOut(Long virtualMaterialId, BigDecimal qty, String type, String relatedType, Long relatedId) {
        BizMaterial mat = materialMapper.selectById(virtualMaterialId);
        Long warehouseId = mat != null && mat.getDefaultWarehouseId() != null ? mat.getDefaultWarehouseId() : DEFAULT_OUTSOURCE_WAREHOUSE_ID;
        inventoryService.stockOut(virtualMaterialId, warehouseId, qty, type, null, relatedType, relatedId);
    }

    /**
     * 查询虚拟半成品可用库存
     */
    public BigDecimal getAvailableQty(Long virtualMaterialId) {
        BizMaterial mat = materialMapper.selectById(virtualMaterialId);
        if (mat == null) return BigDecimal.ZERO;

        Long warehouseId = mat.getDefaultWarehouseId() != null ? mat.getDefaultWarehouseId() : DEFAULT_OUTSOURCE_WAREHOUSE_ID;
        BizInventory inv = inventoryService.getOne(
                new LambdaQueryWrapper<BizInventory>()
                        .eq(BizInventory::getMaterialId, virtualMaterialId)
                        .eq(BizInventory::getWarehouseId, warehouseId));
        return inv != null ? inv.getAvailableQty() : BigDecimal.ZERO;
    }
}
