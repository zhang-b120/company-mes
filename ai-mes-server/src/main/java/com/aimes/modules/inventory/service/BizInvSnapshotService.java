package com.aimes.modules.inventory.service;

import com.aimes.common.result.PageResult;
import com.aimes.modules.inventory.entity.BizInvSnapshot;
import com.aimes.modules.inventory.entity.BizInvTransaction;
import com.aimes.modules.inventory.entity.BizInventory;
import com.aimes.modules.inventory.mapper.BizInvSnapshotMapper;
import com.aimes.modules.inventory.mapper.BizInvTransactionMapper;
import com.aimes.modules.inventory.mapper.BizInventoryMapper;
import com.aimes.modules.master.entity.BizMaterial;
import com.aimes.modules.master.mapper.BizMaterialMapper;
import com.aimes.modules.system.entity.SysWarehouse;
import com.aimes.modules.system.mapper.SysWarehouseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BizInvSnapshotService extends ServiceImpl<BizInvSnapshotMapper, BizInvSnapshot> {

    private final BizInventoryMapper inventoryMapper;
    private final BizInvTransactionMapper transactionMapper;
    private final BizMaterialMapper materialMapper;
    private final SysWarehouseMapper warehouseMapper;
    private final ObjectMapper objectMapper;

    /**
     * 创建库存快照
     */
    public void createSnapshot(LocalDate date) {
        // 基于库存流水回放历史快照：汇总 transaction_time <= 当天23:59:59 的所有流水
        java.time.LocalDateTime dayEnd = date.atTime(23, 59, 59);
        List<BizInvTransaction> transactions = transactionMapper.selectList(
                new LambdaQueryWrapper<BizInvTransaction>()
                        .le(BizInvTransaction::getCreateTime, dayEnd)
                        .eq(BizInvTransaction::getDeleted, 0));

        // 按 (materialId, warehouseId) 分组累加 quantity
        Map<String, BigDecimal> stockMap = new LinkedHashMap<>();
        Map<String, long[]> keyToIds = new LinkedHashMap<>(); // key -> [materialId, warehouseId]
        for (BizInvTransaction tx : transactions) {
            if (tx.getMaterialId() == null || tx.getWarehouseId() == null || tx.getQuantity() == null) continue;
            String key = tx.getMaterialId() + "_" + tx.getWarehouseId();
            stockMap.merge(key, tx.getQuantity(), BigDecimal::add);
            keyToIds.putIfAbsent(key, new long[]{tx.getMaterialId(), tx.getWarehouseId()});
        }

        // 批量加载物料和仓库名称
        Set<Long> matIds = keyToIds.values().stream().map(a -> a[0]).collect(Collectors.toSet());
        Set<Long> whIds = keyToIds.values().stream().map(a -> a[1]).collect(Collectors.toSet());
        Map<Long, BizMaterial> matMap = matIds.isEmpty() ? new HashMap<>() :
                materialMapper.selectBatchIds(matIds).stream()
                        .collect(Collectors.toMap(BizMaterial::getId, m -> m, (a, b) -> a));
        Map<Long, String> whNameMap = whIds.isEmpty() ? new HashMap<>() :
                warehouseMapper.selectBatchIds(whIds).stream()
                        .collect(Collectors.toMap(SysWarehouse::getId, SysWarehouse::getWarehouseName, (a, b) -> a));

        // 构建快照数据（只保留数量>0的）
        List<Map<String, Object>> snapshotItems = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : stockMap.entrySet()) {
            if (e.getValue().compareTo(BigDecimal.ZERO) <= 0) continue;
            long[] ids = keyToIds.get(e.getKey());
            Long materialId = ids[0];
            Long warehouseId = ids[1];
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("materialId", materialId);
            BizMaterial mat = matMap.get(materialId);
            m.put("materialNo", mat != null ? mat.getMaterialNo() : "");
            m.put("materialName", mat != null ? mat.getMaterialName() : "");
            m.put("spec", mat != null ? mat.getSpec() : "");
            m.put("unit", mat != null ? mat.getUnit() : "");
            m.put("category", mat != null ? mat.getCategory() : "");
            m.put("warehouseId", warehouseId);
            m.put("warehouseName", whNameMap.getOrDefault(warehouseId, ""));
            m.put("locationId", null);
            m.put("locationName", "");
            m.put("totalQty", e.getValue());
            snapshotItems.add(m);
        }

        // 序列化为JSON
        String json;
        try {
            json = objectMapper.writeValueAsString(snapshotItems);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("库存快照JSON序列化失败", e);
        }

        // 保存或更新（同一天只保留一条）
        BizInvSnapshot existing = getOne(new LambdaQueryWrapper<BizInvSnapshot>()
                .eq(BizInvSnapshot::getSnapshotDate, date).last("LIMIT 1"));
        if (existing != null) {
            existing.setRecordCount(snapshotItems.size());
            existing.setSnapshotData(json);
            updateById(existing);
            log.info("更新库存快照: date={}, count={}", date, snapshotItems.size());
        } else {
            BizInvSnapshot snapshot = new BizInvSnapshot();
            snapshot.setSnapshotDate(date);
            snapshot.setRecordCount(snapshotItems.size());
            snapshot.setSnapshotData(json);
            save(snapshot);
            log.info("新建库存快照: date={}, count={}", date, snapshotItems.size());
        }
    }

    /**
     * 分页查询快照列表（不含snapshotData大字段）
     */
    public PageResult<Map<String, Object>> getPage(String dateStart, String dateEnd, int pageNum, int pageSize) {
        LambdaQueryWrapper<BizInvSnapshot> wrapper = new LambdaQueryWrapper<BizInvSnapshot>()
                .select(BizInvSnapshot::getId, BizInvSnapshot::getSnapshotDate,
                        BizInvSnapshot::getRecordCount, BizInvSnapshot::getCreateTime)
                .ge(StringUtils.hasText(dateStart), BizInvSnapshot::getSnapshotDate, dateStart)
                .le(StringUtils.hasText(dateEnd), BizInvSnapshot::getSnapshotDate, dateEnd)
                .orderByDesc(BizInvSnapshot::getSnapshotDate);

        Page<BizInvSnapshot> page = page(new Page<>(pageNum, pageSize), wrapper);

        List<Map<String, Object>> list = page.getRecords().stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("snapshotDate", s.getSnapshotDate());
            m.put("recordCount", s.getRecordCount());
            m.put("createTime", s.getCreateTime());
            return m;
        }).collect(Collectors.toList());

        return new PageResult<>(list, page.getTotal(), pageNum, pageSize);
    }

    /**
     * 查看快照详情（解析JSON）
     */
    public Map<String, Object> getDetail(Long id) {
        BizInvSnapshot snapshot = getById(id);
        if (snapshot == null) {
            throw new RuntimeException("快照不存在");
        }
        return parseSnapshot(snapshot);
    }

    /**
     * 按日期查询快照，date为空时取最新快照
     */
    public Map<String, Object> getByDate(String date) {
        // 指定日期时：直接从流水回放，保证和库存流水对齐
        if (StringUtils.hasText(date)) {
            return calcByDate(LocalDate.parse(date));
        }
        // 未指定日期：返回最新的快照记录
        BizInvSnapshot snapshot = getOne(new LambdaQueryWrapper<BizInvSnapshot>()
                .orderByDesc(BizInvSnapshot::getSnapshotDate).last("LIMIT 1"));
        if (snapshot == null) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("snapshotDate", null);
            empty.put("recordCount", 0);
            empty.put("createTime", null);
            empty.put("items", new ArrayList<>());
            return empty;
        }
        return parseSnapshot(snapshot);
    }

    /** 根据库存流水即时回放指定日期的库存状态 */
    public Map<String, Object> calcByDate(LocalDate date) {
        java.time.LocalDateTime dayEnd = date.atTime(23, 59, 59);
        List<BizInvTransaction> transactions = transactionMapper.selectList(
                new LambdaQueryWrapper<BizInvTransaction>()
                        .le(BizInvTransaction::getCreateTime, dayEnd)
                        .eq(BizInvTransaction::getDeleted, 0));

        Map<String, java.math.BigDecimal> stockMap = new LinkedHashMap<>();
        Map<String, long[]> keyToIds = new LinkedHashMap<>();
        for (BizInvTransaction tx : transactions) {
            if (tx.getMaterialId() == null || tx.getWarehouseId() == null || tx.getQuantity() == null) continue;
            String key = tx.getMaterialId() + "_" + tx.getWarehouseId();
            stockMap.merge(key, tx.getQuantity(), java.math.BigDecimal::add);
            keyToIds.putIfAbsent(key, new long[]{tx.getMaterialId(), tx.getWarehouseId()});
        }

        Set<Long> matIds = keyToIds.values().stream().map(a -> a[0]).collect(Collectors.toSet());
        Set<Long> whIds = keyToIds.values().stream().map(a -> a[1]).collect(Collectors.toSet());
        Map<Long, BizMaterial> matMap = matIds.isEmpty() ? new HashMap<>() :
                materialMapper.selectBatchIds(matIds).stream()
                        .collect(Collectors.toMap(BizMaterial::getId, m -> m, (a, b) -> a));
        Map<Long, String> whNameMap = whIds.isEmpty() ? new HashMap<>() :
                warehouseMapper.selectBatchIds(whIds).stream()
                        .collect(Collectors.toMap(SysWarehouse::getId, SysWarehouse::getWarehouseName, (a, b) -> a));

        List<Map<String, Object>> items = new ArrayList<>();
        for (Map.Entry<String, java.math.BigDecimal> e : stockMap.entrySet()) {
            if (e.getValue().compareTo(java.math.BigDecimal.ZERO) <= 0) continue;
            long[] ids = keyToIds.get(e.getKey());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("materialId", ids[0]);
            BizMaterial mat = matMap.get(ids[0]);
            m.put("materialNo", mat != null ? mat.getMaterialNo() : "");
            m.put("materialName", mat != null ? mat.getMaterialName() : "");
            m.put("spec", mat != null ? mat.getSpec() : "");
            m.put("unit", mat != null ? mat.getUnit() : "");
            m.put("category", mat != null ? mat.getCategory() : "");
            m.put("warehouseId", ids[1]);
            m.put("warehouseName", whNameMap.getOrDefault(ids[1], ""));
            m.put("locationId", null);
            m.put("locationName", "");
            m.put("totalQty", e.getValue());
            items.add(m);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("snapshotDate", date.toString());
        result.put("recordCount", items.size());
        result.put("createTime", null);
        result.put("items", items);
        return result;
    }

    /**
     * 获取所有可用的快照日期列表（供前端下拉选择）
     */
    public List<String> getAvailableDates() {
        List<BizInvSnapshot> list = list(new LambdaQueryWrapper<BizInvSnapshot>()
                .select(BizInvSnapshot::getSnapshotDate)
                .orderByDesc(BizInvSnapshot::getSnapshotDate));
        return list.stream().map(s -> s.getSnapshotDate().toString()).collect(Collectors.toList());
    }

    private Map<String, Object> parseSnapshot(BizInvSnapshot snapshot) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", snapshot.getId());
        result.put("snapshotDate", snapshot.getSnapshotDate());
        result.put("recordCount", snapshot.getRecordCount());
        result.put("createTime", snapshot.getCreateTime());

        List<Map<String, Object>> items = new ArrayList<>();
        if (StringUtils.hasText(snapshot.getSnapshotData())) {
            try {
                items = objectMapper.readValue(snapshot.getSnapshotData(),
                        new TypeReference<List<Map<String, Object>>>() {});
            } catch (JsonProcessingException e) {
                log.error("快照JSON解析失败: id={}", snapshot.getId(), e);
            }
        }
        result.put("items", items);
        return result;
    }
}
