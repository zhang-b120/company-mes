package com.aimes.modules.dataimport.service.simulation;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.utils.CodeUtil;
import com.aimes.modules.inventory.entity.*;
import com.aimes.modules.inventory.mapper.*;
import com.aimes.modules.master.entity.BizMaterial;
import com.aimes.modules.master.mapper.BizMaterialMapper;
import com.aimes.modules.system.entity.SysWarehouse;
import com.aimes.modules.system.mapper.SysWarehouseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ⑦ 库存台账重建
 * 1. 重建库存台账 (biz_inventory)
 * 2. 为已有的 biz_inv_task 生成出入库历史 (biz_inv_history + biz_inv_history_item)
 * 3. 生成历史库存快照 (biz_inv_snapshot)
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class InventoryRebuildGenerator {

    private final BizInvTransactionMapper invTransactionMapper;
    private final BizInventoryMapper inventoryMapper;
    private final BizInvTaskMapper invTaskMapper;
    private final BizInvTaskItemMapper invTaskItemMapper;
    private final BizInvHistoryMapper invHistoryMapper;
    private final BizInvHistoryItemMapper invHistoryItemMapper;
    private final BizInvSnapshotMapper invSnapshotMapper;
    private final BizMaterialMapper materialMapper;
    private final SysWarehouseMapper warehouseMapper;

    public Map<String, Object> generate(SimConfig cfg) {
        List<BizInvTransaction> allTx = invTransactionMapper.selectList(
                new LambdaQueryWrapper<BizInvTransaction>().eq(BizInvTransaction::getDeleted, 0));
        List<BizInvTask> allTasks = invTaskMapper.selectList(
                new LambdaQueryWrapper<BizInvTask>().eq(BizInvTask::getDeleted, 0));

        // 预加载物料信息用于仓库回填
        Map<Long, BizMaterial> matMap = materialMapper.selectList(
                new LambdaQueryWrapper<BizMaterial>().eq(BizMaterial::getDeleted, 0))
                .stream().collect(Collectors.toMap(BizMaterial::getId, m -> m, (a, b) -> a));

        // 按 materialId + warehouseId 汇总库存（对null仓库做回填）
        Map<String, BigDecimal> qtyMap = new HashMap<>();
        for (BizInvTransaction tx : allTx) {
            if (tx.getMaterialId() == null) continue;
            Long whId = resolveWarehouseId(tx.getWarehouseId(), tx.getMaterialId(), matMap);
            if (whId == null) continue;
            String key = tx.getMaterialId() + "_" + whId;
            qtyMap.merge(key, tx.getQuantity() != null ? tx.getQuantity() : BigDecimal.ZERO, BigDecimal::add);
        }

        // 找最早的流水时间（用于生成历史快照）
        LocalDate earliest = allTx.stream()
                .map(BizInvTransaction::getCreateTime)
                .filter(Objects::nonNull)
                .map(LocalDateTime::toLocalDate)
                .min(LocalDate::compareTo)
                .orElse(LocalDate.now());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalTransactions", allTx.size());
        result.put("totalTasks", allTasks.size());
        result.put("inventoryRecords", qtyMap.size());
        result.put("earliestDate", earliest.toString());
        result.put("totalRows", allTx.size() + allTasks.size() + qtyMap.size());
        return result;
    }

    public void exportZip(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        throw new BusinessException("库存台账重建不需要导出，请直接执行导入");
    }

    @Transactional
    public Map<String, Object> importData() {
        return importData(null);
    }

    @Transactional
    public Map<String, Object> importData(SimConfig cfg) {
        int historyCount = 0, snapshotCount = 0, inventoryCount = 0;
        List<String> errors = new ArrayList<>();

        // 预加载物料信息
        Map<Long, BizMaterial> matMap = materialMapper.selectList(
                new LambdaQueryWrapper<BizMaterial>().eq(BizMaterial::getDeleted, 0))
                .stream().collect(Collectors.toMap(BizMaterial::getId, m -> m, (a, b) -> a));

        // 预加载仓库名称
        Map<Long, String> whNameMap = warehouseMapper.selectList(
                new LambdaQueryWrapper<SysWarehouse>().eq(SysWarehouse::getDeleted, 0))
                .stream().collect(Collectors.toMap(SysWarehouse::getId, SysWarehouse::getWarehouseName, (a, b) -> a));

        // ========== 1. 为已有的 biz_inv_task 生成出入库历史 ==========
        // 按 createTime 升序处理,让 history 的 id 跟时间顺序一致,
        // 前端分页按 id desc 查出来的结果才是正确的时间倒序
        List<BizInvTask> allTasks = invTaskMapper.selectList(
                new LambdaQueryWrapper<BizInvTask>().eq(BizInvTask::getDeleted, 0)
                        .eq(BizInvTask::getStatus, "completed")
                        .orderByAsc(BizInvTask::getCreateTime));

        for (BizInvTask task : allTasks) {
            try {
                // 检查是否已有历史记录（避免重复生成）
                long existingCount = invHistoryMapper.selectCount(new LambdaQueryWrapper<BizInvHistory>()
                        .eq(BizInvHistory::getTaskId, task.getId())
                        .eq(BizInvHistory::getDeleted, 0));
                if (existingCount > 0) continue;

                // 获取任务明细，用于确定仓库
                List<BizInvTaskItem> taskItems = invTaskItemMapper.selectList(
                        new LambdaQueryWrapper<BizInvTaskItem>()
                                .eq(BizInvTaskItem::getTaskId, task.getId()));

                // 解析仓库ID：优先task→明细→物料默认仓库
                Long taskWarehouseId = task.getWarehouseId();
                if (taskWarehouseId == null || taskWarehouseId == 0) {
                    // 从明细中取第一个有效的仓库ID
                    for (BizInvTaskItem ti : taskItems) {
                        if (ti.getWarehouseId() != null && ti.getWarehouseId() != 0) {
                            taskWarehouseId = ti.getWarehouseId();
                            break;
                        }
                    }
                }
                if (taskWarehouseId == null || taskWarehouseId == 0) {
                    // 从物料的默认仓库取
                    for (BizInvTaskItem ti : taskItems) {
                        if (ti.getMaterialId() != null) {
                            BizMaterial mat = matMap.get(ti.getMaterialId());
                            if (mat != null && mat.getDefaultWarehouseId() != null) {
                                taskWarehouseId = mat.getDefaultWarehouseId();
                                break;
                            }
                        }
                    }
                }

                // 处理时间 = 任务创建时间 + 处理延迟,落到未来就跳过(事件还没发生,不体现)
                int delayHours = cfg != null ? cfg.randomInvProcessHours() : 2;
                LocalDateTime processTime = task.getCreateTime() != null
                        ? task.getCreateTime().plusHours(delayHours) : LocalDateTime.now();
                if (processTime.isAfter(LocalDateTime.now())) continue;

                // 创建出入库历史主表
                BizInvHistory history = new BizInvHistory();
                history.setHistoryNo(CodeUtil.generate("INH"));
                history.setTaskId(task.getId());
                history.setTaskNo(task.getTaskNo());
                history.setRelatedOrderNo(task.getRelatedOrderNo());
                history.setInvType(task.getInvType());
                history.setWarehouseId(taskWarehouseId);
                history.setStatus("normal");
                // 申请人 / 操作人兜底:task.applicantId → task.createBy → admin(1)
                Long applicantId = task.getApplicantId();
                if (applicantId == null) applicantId = task.getCreateBy();
                if (applicantId == null) applicantId = 1L;
                history.setApplicantId(applicantId);
                history.setOperatorId(applicantId);
                history.setCreateTime(processTime);
                history.setUpdateTime(processTime);
                Long createById = task.getCreateBy() != null ? task.getCreateBy() : applicantId;
                history.setCreateBy(createById);
                history.setUpdateBy(createById);
                invHistoryMapper.insert(history);

                // 生成历史明细
                for (BizInvTaskItem ti : taskItems) {
                    BizMaterial mat = ti.getMaterialId() != null ? matMap.get(ti.getMaterialId()) : null;

                    // 解析明细仓库：优先明细→任务→物料默认
                    Long itemWhId = ti.getWarehouseId();
                    if (itemWhId == null || itemWhId == 0) {
                        itemWhId = taskWarehouseId;
                    }
                    if ((itemWhId == null || itemWhId == 0) && mat != null && mat.getDefaultWarehouseId() != null) {
                        itemWhId = mat.getDefaultWarehouseId();
                    }

                    BizInvHistoryItem hi = new BizInvHistoryItem();
                    hi.setHistoryId(history.getId());
                    hi.setTaskItemId(ti.getId());
                    hi.setMaterialId(ti.getMaterialId());
                    hi.setMaterialNo(ti.getMaterialNo() != null ? ti.getMaterialNo() : (mat != null ? mat.getMaterialNo() : ""));
                    hi.setMaterialName(ti.getMaterialName() != null ? ti.getMaterialName() : (mat != null ? mat.getMaterialName() : ""));
                    hi.setSpec(mat != null ? mat.getSpec() : "");
                    hi.setUnit(ti.getUnit() != null ? ti.getUnit() : (mat != null ? mat.getUnit() : ""));
                    hi.setQuantity(ti.getProcessedQty() != null ? ti.getProcessedQty() : ti.getQuantity());
                    hi.setUnitPrice(ti.getUnitPrice());
                    hi.setTotalPrice(ti.getTotalPrice());
                    hi.setWarehouseId(itemWhId);
                    hi.setStatus("normal");
                    hi.setSecondaryUnit(ti.getSecondaryUnit());
                    hi.setSecondaryQty(ti.getSecondaryProcessedQty());
                    invHistoryItemMapper.insert(hi);
                }

                historyCount++;
            } catch (Exception e) {
                errors.add("历史记录生成失败(taskId=" + task.getId() + "): " + e.getMessage());
            }
        }

        // ========== 1.5 期初备料（临时方案 A）==========
        // 为每个"有默认仓库"的物料生成一条 initial_in 流水，作为最早流水之前的期初库存
        // 数量按"历史最大累计净消耗 + 20% 缓冲"计算，保证整段历史的累计余额始终 >= 0，
        // 避免历史快照因负余额跳过该物料
        // 幂等：同一(物料,仓库)已有 initial_in 时跳过
        int initialStockCount = 0;
        try {
            // 加载所有非期初流水，用于计算每个 (物料,仓库) 的"最低累计点"
            List<BizInvTransaction> txForCalc = invTransactionMapper.selectList(
                    new LambdaQueryWrapper<BizInvTransaction>()
                            .eq(BizInvTransaction::getDeleted, 0)
                            .ne(BizInvTransaction::getTransactionType, "initial_in")
                            .orderByAsc(BizInvTransaction::getCreateTime));

            // 期初时间点：比当前最早流水再早 30 天；没流水就按今天 - 180 天
            LocalDate earliestTxDate = txForCalc.stream()
                    .map(BizInvTransaction::getCreateTime).filter(Objects::nonNull)
                    .map(LocalDateTime::toLocalDate).min(LocalDate::compareTo)
                    .orElse(LocalDate.now().minusDays(180));
            LocalDateTime initialTime = earliestTxDate.minusDays(30).atTime(8, 0);

            // 按 (物料,仓库) 走时间顺序累加，记录最低点（即最大净消耗的绝对值）
            // key = "matId_whId"，value = 最低累计余额（通常 ≤ 0）
            Map<String, BigDecimal> runningQty = new HashMap<>();
            Map<String, BigDecimal> minQty = new HashMap<>();
            for (BizInvTransaction tx : txForCalc) {
                if (tx.getMaterialId() == null) continue;
                Long whId = resolveWarehouseId(tx.getWarehouseId(), tx.getMaterialId(), matMap);
                if (whId == null) continue;
                String key = tx.getMaterialId() + "_" + whId;
                BigDecimal qty = tx.getQuantity() != null ? tx.getQuantity() : BigDecimal.ZERO;
                BigDecimal running = runningQty.getOrDefault(key, BigDecimal.ZERO).add(qty);
                runningQty.put(key, running);
                BigDecimal min = minQty.getOrDefault(key, BigDecimal.ZERO);
                if (running.compareTo(min) < 0) minQty.put(key, running);
            }

            for (BizMaterial mat : matMap.values()) {
                if (mat.getDefaultWarehouseId() == null || mat.getDefaultWarehouseId() == 0) continue;

                long existing = invTransactionMapper.selectCount(new LambdaQueryWrapper<BizInvTransaction>()
                        .eq(BizInvTransaction::getMaterialId, mat.getId())
                        .eq(BizInvTransaction::getWarehouseId, mat.getDefaultWarehouseId())
                        .eq(BizInvTransaction::getTransactionType, "initial_in")
                        .eq(BizInvTransaction::getDeleted, 0));
                if (existing > 0) continue;

                // 该物料的最低累计点（负值）
                String key = mat.getId() + "_" + mat.getDefaultWarehouseId();
                BigDecimal lowest = minQty.getOrDefault(key, BigDecimal.ZERO);
                // 期初数量 = max(|最低点| × 1.2, 随机兜底)
                //   - 有流水(lowest<0)的物料: 至少要覆盖最低点,避免历史负余额
                //   - 无流水/历史净流入物料: 走随机区间,默认 50~500,避免所有"建档物料"都显示同一个 100
                BigDecimal needed = lowest.abs().multiply(new BigDecimal("1.2"))
                        .setScale(0, java.math.RoundingMode.CEILING);
                int randomFallback = cfg != null ? cfg.randomInitialStock() : 50 + (int)(Math.random() * 451);
                BigDecimal initialQty = needed.max(BigDecimal.valueOf(randomFallback));

                BizInvTransaction tx = new BizInvTransaction();
                tx.setMaterialId(mat.getId());
                tx.setWarehouseId(mat.getDefaultWarehouseId());
                tx.setTransactionType("initial_in");
                tx.setQuantity(initialQty);
                tx.setRelatedOrderType("initial");
                tx.setRelatedOrderId(0L);
                tx.setRemark("期初备料（临时）");
                tx.setCreateTime(initialTime);
                tx.setUpdateTime(initialTime);
                invTransactionMapper.insert(tx);
                initialStockCount++;
            }
            log.info("期初备料完成：{} 个物料生成 initial_in 流水（按历史最低点+20%缓冲），日期={}",
                    initialStockCount, initialTime);
        } catch (Exception e) {
            log.error("期初备料失败: {}", e.getMessage(), e);
            errors.add("期初备料失败: " + e.getMessage());
        }

        // ========== 2. 重建库存台账 ==========
        List<BizInvTransaction> allTx = invTransactionMapper.selectList(
                new LambdaQueryWrapper<BizInvTransaction>().eq(BizInvTransaction::getDeleted, 0));

        log.info("库存台账重建: 共查询到 {} 条库存流水", allTx.size());

        // 对warehouseId为null的流水，回填物料的默认仓库
        for (BizInvTransaction tx : allTx) {
            if (tx.getMaterialId() != null && (tx.getWarehouseId() == null || tx.getWarehouseId() == 0)) {
                BizMaterial mat = matMap.get(tx.getMaterialId());
                if (mat != null && mat.getDefaultWarehouseId() != null) {
                    tx.setWarehouseId(mat.getDefaultWarehouseId());
                    // 同步更新到DB，后续快照查询也能用到
                    invTransactionMapper.updateById(tx);
                }
            }
        }

        Map<String, BigDecimal> qtyMap = new HashMap<>();
        int skippedTx = 0;
        for (BizInvTransaction tx : allTx) {
            if (tx.getMaterialId() == null || tx.getWarehouseId() == null || tx.getWarehouseId() == 0) {
                skippedTx++;
                continue;
            }
            String key = tx.getMaterialId() + "_" + tx.getWarehouseId();
            qtyMap.merge(key, tx.getQuantity() != null ? tx.getQuantity() : BigDecimal.ZERO, BigDecimal::add);
        }

        if (skippedTx > 0) {
            log.warn("库存台账重建: 跳过 {} 条无效流水(缺少materialId或warehouseId)", skippedTx);
        }
        log.info("库存台账重建: 汇总出 {} 个 物料+仓库 组合", qtyMap.size());

        for (Map.Entry<String, BigDecimal> e : qtyMap.entrySet()) {
            String[] parts = e.getKey().split("_");
            Long materialId = Long.parseLong(parts[0]);
            Long warehouseId = Long.parseLong(parts[1]);
            BigDecimal totalQty = e.getValue().max(BigDecimal.ZERO);

            BizInventory inv = inventoryMapper.selectOne(new LambdaQueryWrapper<BizInventory>()
                    .eq(BizInventory::getMaterialId, materialId)
                    .eq(BizInventory::getWarehouseId, warehouseId));

            if (inv == null) {
                inv = new BizInventory();
                inv.setMaterialId(materialId);
                inv.setWarehouseId(warehouseId);
                inv.setTotalQty(totalQty);
                inv.setLockedQty(BigDecimal.ZERO);
                inv.setAvailableQty(totalQty);
                inv.setSafetyStock(BigDecimal.ZERO);
                inventoryMapper.insert(inv);
            } else {
                inv.setTotalQty(totalQty);
                inv.setAvailableQty(totalQty.subtract(inv.getLockedQty() != null ? inv.getLockedQty() : BigDecimal.ZERO));
                inventoryMapper.updateById(inv);
            }
            inventoryCount++;
        }

        // ========== 3. 生成历史库存快照 ==========
        try {
            // 找最早的库存流水时间
            LocalDate earliest = allTx.stream()
                    .map(BizInvTransaction::getCreateTime)
                    .filter(Objects::nonNull)
                    .map(LocalDateTime::toLocalDate)
                    .min(LocalDate::compareTo)
                    .orElse(LocalDate.now());

            // 从最早日期到今天，每天生成一个快照
            LocalDate today = LocalDate.now();
            LocalDate snapshotDate = earliest;

            log.info("库存快照生成: 时间范围 {} ~ {}", earliest, today);

            ObjectMapper objectMapper = new ObjectMapper();

            while (!snapshotDate.isAfter(today)) {
                // 计算截止到该日期的累计库存（按日期过滤流水）
                final LocalDate currentDate = snapshotDate;
                Map<String, BigDecimal> dailyQtyMap = new HashMap<>();
                for (BizInvTransaction tx : allTx) {
                    if (tx.getCreateTime() == null || tx.getMaterialId() == null
                            || tx.getWarehouseId() == null || tx.getWarehouseId() == 0) continue;
                    if (!tx.getCreateTime().toLocalDate().isAfter(currentDate)) {
                        String key = tx.getMaterialId() + "_" + tx.getWarehouseId();
                        dailyQtyMap.merge(key, tx.getQuantity() != null ? tx.getQuantity() : BigDecimal.ZERO, BigDecimal::add);
                    }
                }

                if (!dailyQtyMap.isEmpty()) {
                    // 构建快照JSON数据（与BizInvSnapshotService.createSnapshot格式一致）
                    List<Map<String, Object>> snapshotItems = new ArrayList<>();
                    for (Map.Entry<String, BigDecimal> entry : dailyQtyMap.entrySet()) {
                        String[] p = entry.getKey().split("_");
                        Long matId = Long.parseLong(p[0]);
                        Long whId = Long.parseLong(p[1]);
                        BigDecimal qty = entry.getValue().max(BigDecimal.ZERO);
                        if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;

                        BizMaterial mat = matMap.get(matId);
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("materialId", matId);
                        item.put("materialNo", mat != null ? mat.getMaterialNo() : "");
                        item.put("materialName", mat != null ? mat.getMaterialName() : "");
                        item.put("spec", mat != null ? mat.getSpec() : "");
                        item.put("unit", mat != null ? mat.getUnit() : "");
                        item.put("category", mat != null ? mat.getCategory() : "");
                        item.put("warehouseId", whId);
                        item.put("warehouseName", whNameMap.getOrDefault(whId, ""));
                        item.put("totalQty", qty);
                        snapshotItems.add(item);
                    }

                    if (!snapshotItems.isEmpty()) {
                        // 检查是否已存在该日期的快照
                        BizInvSnapshot existing = invSnapshotMapper.selectOne(
                                new LambdaQueryWrapper<BizInvSnapshot>()
                                        .eq(BizInvSnapshot::getSnapshotDate, currentDate)
                                        .last("LIMIT 1"));

                        String jsonData = objectMapper.writeValueAsString(snapshotItems);

                        if (existing != null) {
                            existing.setRecordCount(snapshotItems.size());
                            existing.setSnapshotData(jsonData);
                            invSnapshotMapper.updateById(existing);
                        } else {
                            BizInvSnapshot snapshot = new BizInvSnapshot();
                            snapshot.setSnapshotDate(currentDate);
                            snapshot.setRecordCount(snapshotItems.size());
                            snapshot.setSnapshotData(jsonData);
                            invSnapshotMapper.insert(snapshot);
                        }
                        snapshotCount++;
                    }
                }

                snapshotDate = snapshotDate.plusDays(1);
            }
        } catch (Exception e) {
            log.error("生成库存快照失败: {}", e.getMessage(), e);
            errors.add("库存快照生成失败: " + e.getMessage());
        }

        log.info("库存台账重建完成: 历史记录{}条, 库存{}条, 快照{}天", historyCount, inventoryCount, snapshotCount);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("historyCount", historyCount);
        result.put("inventoryCount", inventoryCount);
        result.put("snapshotCount", snapshotCount);
        result.put("success", historyCount + inventoryCount + snapshotCount);
        result.put("errors", errors);
        return result;
    }

    /**
     * 解析仓库ID：优先使用原始值，为null/0时回填物料的默认仓库
     */
    private Long resolveWarehouseId(Long warehouseId, Long materialId, Map<Long, BizMaterial> matMap) {
        if (warehouseId != null && warehouseId != 0) {
            return warehouseId;
        }
        if (materialId != null) {
            BizMaterial mat = matMap.get(materialId);
            if (mat != null && mat.getDefaultWarehouseId() != null) {
                return mat.getDefaultWarehouseId();
            }
        }
        return null;
    }
}
