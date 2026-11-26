package com.aimes.modules.inventory.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.PageResult;
import com.aimes.modules.inventory.entity.BizInvReservation;
import com.aimes.modules.inventory.entity.BizInventory;
import com.aimes.modules.inventory.mapper.BizInvReservationMapper;
import com.aimes.modules.inventory.mapper.BizInventoryMapper;
import com.aimes.modules.master.entity.BizMaterial;
import com.aimes.modules.master.mapper.BizMaterialMapper;
import com.aimes.modules.production.entity.BizWorkOrder;
import com.aimes.modules.production.mapper.BizWorkOrderMapper;
import com.aimes.modules.system.entity.SysWarehouse;
import com.aimes.modules.system.mapper.SysWarehouseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReservationService extends ServiceImpl<BizInvReservationMapper, BizInvReservation> {

    private final BizInventoryMapper inventoryMapper;
    private final BizMaterialMapper materialMapper;
    private final SysWarehouseMapper warehouseMapper;
    private final BizWorkOrderMapper workOrderMapper;

    /**
     * 分页查询预留（含物料名称、仓库名称、来源单据号）
     */
    public PageResult<Map<String, Object>> getPage(Long materialId, String keyword, String reservationType, String status, int pageNum, int pageSize) {
        // 如果有keyword，先按物料名称模糊查找materialId列表
        Set<Long> matchedMatIds = null;
        if (StringUtils.hasText(keyword)) {
            List<BizMaterial> mats = materialMapper.selectList(new LambdaQueryWrapper<BizMaterial>()
                    .like(BizMaterial::getMaterialName, keyword));
            matchedMatIds = mats.stream().map(BizMaterial::getId).collect(Collectors.toSet());
            if (matchedMatIds.isEmpty()) {
                return new PageResult<>(List.of(), 0L, pageNum, pageSize);
            }
        }

        LambdaQueryWrapper<BizInvReservation> wrapper = new LambdaQueryWrapper<BizInvReservation>()
                .eq(materialId != null, BizInvReservation::getMaterialId, materialId)
                .in(matchedMatIds != null, BizInvReservation::getMaterialId, matchedMatIds)
                .eq(StringUtils.hasText(reservationType), BizInvReservation::getReservationType, reservationType)
                .eq(StringUtils.hasText(status), BizInvReservation::getStatus, status)
                .orderByDesc(BizInvReservation::getCreateTime);

        Page<BizInvReservation> page = page(new Page<>(pageNum, pageSize), wrapper);

        // 批量加载物料名称、仓库名称、工单号
        List<BizInvReservation> records = page.getRecords();
        Set<Long> matIds = records.stream().map(BizInvReservation::getMaterialId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> whIds = records.stream().map(BizInvReservation::getWarehouseId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> woIds = records.stream()
                .filter(r -> "work_order".equals(r.getRelatedOrderType()) && r.getRelatedOrderId() != null)
                .map(BizInvReservation::getRelatedOrderId).collect(Collectors.toSet());

        Map<Long, String> matNameMap = matIds.isEmpty() ? new HashMap<>() :
                materialMapper.selectBatchIds(matIds).stream().collect(Collectors.toMap(BizMaterial::getId, BizMaterial::getMaterialName, (a, b) -> a));
        Map<Long, String> whNameMap = whIds.isEmpty() ? new HashMap<>() :
                warehouseMapper.selectBatchIds(whIds).stream().collect(Collectors.toMap(SysWarehouse::getId, SysWarehouse::getWarehouseName, (a, b) -> a));
        Map<Long, String> woNoMap = woIds.isEmpty() ? new HashMap<>() :
                workOrderMapper.selectBatchIds(woIds).stream().collect(Collectors.toMap(BizWorkOrder::getId, BizWorkOrder::getWoNo, (a, b) -> a));

        List<Map<String, Object>> list = records.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("materialId", r.getMaterialId());
            m.put("materialName", matNameMap.getOrDefault(r.getMaterialId(), ""));
            m.put("warehouseId", r.getWarehouseId());
            m.put("warehouseName", whNameMap.getOrDefault(r.getWarehouseId(), ""));
            m.put("reservationType", r.getReservationType());
            m.put("lockedQty", r.getLockedQty());
            m.put("consumedQty", r.getConsumedQty());
            m.put("remainingQty", r.getRemainingQty());
            m.put("relatedOrderType", r.getRelatedOrderType());
            m.put("relatedOrderId", r.getRelatedOrderId());
            m.put("relatedOrderNo", r.getRelatedOrderNo());
            // 组装来源单据描述: 优先使用relatedOrderNo字段，否则按类型查找单据号
            String orderNo = r.getRelatedOrderNo();
            if (!StringUtils.hasText(orderNo) && "work_order".equals(r.getRelatedOrderType())) {
                orderNo = woNoMap.getOrDefault(r.getRelatedOrderId(), "");
            }
            String typeLabel = "work_order".equals(r.getRelatedOrderType()) ? "工单" :
                    "plan_item".equals(r.getRelatedOrderType()) ? "生产计划" :
                    "sales_order".equals(r.getRelatedOrderType()) ? "销售订单" :
                    r.getRelatedOrderType() != null ? r.getRelatedOrderType() : "手动";
            m.put("sourceDesc", StringUtils.hasText(orderNo) ? typeLabel + "[" + orderNo + "]" : typeLabel);
            m.put("lockedBy", r.getLockedBy());
            m.put("expireDate", r.getExpireDate());
            m.put("status", r.getStatus());
            m.put("reason", r.getReason());
            m.put("createTime", r.getCreateTime());
            m.put("updateTime", r.getUpdateTime());
            return m;
        }).collect(Collectors.toList());

        return new PageResult<>(list, page.getTotal(), pageNum, pageSize);
    }

    /**
     * 创建库存预留
     */
    @Transactional(rollbackFor = Exception.class)
    public void create(BizInvReservation reservation) {
        // 校验物料是否允许锁库
        BizMaterial material = materialMapper.selectById(reservation.getMaterialId());
        if (material != null && Boolean.FALSE.equals(material.getAllowStockLock())) {
            throw new BusinessException("该物料不允许库存锁定");
        }

        // 校验可用库存
        BizInventory inventory = inventoryMapper.selectOne(new LambdaQueryWrapper<BizInventory>()
                .eq(BizInventory::getMaterialId, reservation.getMaterialId())
                .eq(BizInventory::getWarehouseId, reservation.getWarehouseId()));

        if (inventory == null) {
            throw new BusinessException("库存记录不存在");
        }
        if (inventory.getAvailableQty().compareTo(reservation.getLockedQty()) < 0) {
            throw new BusinessException("可用库存不足，无法预留");
        }

        reservation.setConsumedQty(java.math.BigDecimal.ZERO);
        reservation.setRemainingQty(reservation.getLockedQty());
        reservation.setStatus("active");
        save(reservation);

        // 更新库存锁定数量
        inventory.setLockedQty(inventory.getLockedQty().add(reservation.getLockedQty()));
        inventory.setAvailableQty(inventory.getAvailableQty().subtract(reservation.getLockedQty()));
        inventoryMapper.updateById(inventory);
    }

    /**
     * 释放库存预留
     */
    @Transactional(rollbackFor = Exception.class)
    public void release(Long id) {
        BizInvReservation reservation = getById(id);
        if (reservation == null) {
            throw new BusinessException("预留记录不存在");
        }
        if (!"active".equals(reservation.getStatus())) {
            throw new BusinessException("该预留已释放或已消耗");
        }

        // 释放剩余锁定量
        BizInventory inventory = inventoryMapper.selectOne(new LambdaQueryWrapper<BizInventory>()
                .eq(BizInventory::getMaterialId, reservation.getMaterialId())
                .eq(BizInventory::getWarehouseId, reservation.getWarehouseId()));

        if (inventory != null) {
            inventory.setLockedQty(inventory.getLockedQty().subtract(reservation.getRemainingQty()));
            inventory.setAvailableQty(inventory.getAvailableQty().add(reservation.getRemainingQty()));
            inventoryMapper.updateById(inventory);
        }

        reservation.setStatus("released");
        updateById(reservation);
    }
}
