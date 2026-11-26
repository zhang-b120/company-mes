package com.aimes.modules.inventory.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.PageResult;
import com.aimes.common.utils.CodeUtil;
import com.aimes.common.utils.SecurityUtil;
import com.aimes.common.utils.UnitConversionUtil;
import com.aimes.modules.inventory.entity.BizInvHistory;
import com.aimes.modules.inventory.entity.BizInvHistoryItem;
import com.aimes.modules.inventory.entity.BizInvRequest;
import com.aimes.modules.inventory.entity.BizInvTask;
import com.aimes.modules.inventory.entity.BizInvTaskItem;
import com.aimes.modules.inventory.mapper.BizInvHistoryItemMapper;
import com.aimes.modules.inventory.mapper.BizInvHistoryMapper;
import com.aimes.modules.inventory.mapper.BizInvRequestMapper;
import com.aimes.modules.inventory.mapper.BizInvTaskItemMapper;
import com.aimes.modules.inventory.mapper.BizInvTaskMapper;
import com.aimes.modules.purchase.entity.BizPurchaseOrder;
import com.aimes.modules.purchase.entity.BizPurchaseOrderItem;
import com.aimes.modules.purchase.entity.BizPurchaseReceipt;
import com.aimes.modules.purchase.mapper.BizPurchaseOrderItemMapper;
import com.aimes.modules.purchase.mapper.BizPurchaseOrderMapper;
import com.aimes.modules.purchase.mapper.BizPurchaseReceiptMapper;
import com.aimes.modules.purchase.mapper.BizPurchaseReturnMapper;
import com.aimes.modules.master.entity.BizMaterial;
import com.aimes.modules.master.mapper.BizMaterialMapper;
import com.aimes.modules.order.entity.BizSalesOrder;
import com.aimes.modules.order.entity.BizSalesOrderItem;
import com.aimes.modules.order.mapper.BizSalesOrderItemMapper;
import com.aimes.modules.order.mapper.BizSalesOrderMapper;
import com.aimes.modules.outsource.entity.BizOutsourceOrder;
import com.aimes.modules.outsource.mapper.BizOutsourceOrderMapper;
import com.aimes.modules.production.entity.BizWoBomSnapshot;
import com.aimes.modules.production.entity.BizWorkOrder;
import com.aimes.modules.production.mapper.BizWoBomSnapshotMapper;
import com.aimes.modules.production.mapper.BizWorkOrderMapper;
import com.aimes.modules.system.entity.SysUser;
import com.aimes.modules.system.entity.SysWarehouse;
import com.aimes.modules.system.mapper.SysUserMapper;
import com.aimes.modules.system.mapper.SysWarehouseMapper;
import com.aimes.modules.system.service.SysBizConfigService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BizInvTaskService extends ServiceImpl<BizInvTaskMapper, BizInvTask> {

    private final BizInvTaskItemMapper itemMapper;
    private final BizInvHistoryMapper historyMapper;
    private final BizInvHistoryItemMapper historyItemMapper;
    private final SysWarehouseMapper warehouseMapper;
    private final SysUserMapper userMapper;
    private final InventoryService inventoryService;
    private final BizWorkOrderMapper workOrderMapper;
    private final BizMaterialMapper materialMapper;
    private final BizOutsourceOrderMapper outsourceOrderMapper;
    private final BizSalesOrderMapper salesOrderMapper;
    private final BizSalesOrderItemMapper salesOrderItemMapper;
    @Autowired
    @Lazy
    private ProductionStockInService productionStockInService;
    @Autowired
    @Lazy
    private com.aimes.modules.production.service.SemiStockService semiStockService;
    @org.springframework.beans.factory.annotation.Autowired
    private com.aimes.common.utils.BatchInsertHelper batchInsertHelper;
    @org.springframework.beans.factory.annotation.Autowired
    private com.aimes.modules.production.mapper.BizWoProcessTaskMapper woProcessTaskMapper;
    private final BizPurchaseOrderMapper purchaseOrderMapper;
    private final BizPurchaseOrderItemMapper purchaseOrderItemMapper;
    private final BizPurchaseReceiptMapper purchaseReceiptMapper;
    private final BizPurchaseReturnMapper purchaseReturnMapper;
    private final BizInvRequestMapper invRequestMapper;
    private final BizWoBomSnapshotMapper bomSnapshotMapper;
    private final com.aimes.modules.quality.mapper.BizQcTaskMapper qcTaskMapper;
    @Autowired @Lazy
    private SysBizConfigService bizConfigService;
    private final org.springframework.context.ApplicationContext applicationContext;
    @Autowired @Lazy
    private com.aimes.modules.outsource.service.OutsourceChainService outsourceChainService;
    @Autowired @Lazy
    private com.aimes.modules.finance.service.BizFinanceService financeService;

    /**
     * 库存任务自动处理：根据任务明细确定仓库后直接调用process完成处理。
     * 仓库优先级：任务明细仓库 > 任务头仓库 > 物料默认仓库。
     * 如果所有物料都无法确定仓库，则跳过自动处理，留给用户手动操作。
     */
    public void tryAutoProcess(Long taskId) {
        try {
            BizInvTask task = getById(taskId);
            if (task == null || !"pending".equals(task.getStatus())) return;

            List<BizInvTaskItem> items = itemMapper.selectList(
                    new LambdaQueryWrapper<BizInvTaskItem>().eq(BizInvTaskItem::getTaskId, taskId));
            if (items.isEmpty()) return;

            // 确定每个明细的仓库
            List<Map<String, Object>> itemUpdates = new ArrayList<>();
            boolean allHaveWarehouse = true;
            for (BizInvTaskItem item : items) {
                Long whId = item.getWarehouseId();
                if (whId == null) whId = task.getWarehouseId();
                if (whId == null) {
                    // 从物料档案获取默认仓库
                    BizMaterial mat = materialMapper.selectById(item.getMaterialId());
                    if (mat != null) whId = mat.getDefaultWarehouseId();
                }
                if (whId == null) {
                    allHaveWarehouse = false;
                    break;
                }
                Map<String, Object> upd = new LinkedHashMap<>();
                upd.put("itemId", item.getId());
                upd.put("processQty", item.getQuantity());
                upd.put("warehouseId", whId);
                itemUpdates.add(upd);
            }

            if (!allHaveWarehouse) {
                log.info("库存任务[{}]部分物料无法确定仓库，跳过自动处理", task.getTaskNo());
                return;
            }

            // 取第一个明细的仓库作为任务级仓库
            Long processWarehouseId = (Long) itemUpdates.get(0).get("warehouseId");
            process(taskId, processWarehouseId, null, itemUpdates, "系统自动处理", null);
            log.info("库存任务[{}]已自动处理完成", task.getTaskNo());
        } catch (Exception e) {
            log.warn("库存任务[{}]自动处理失败，将由用户手动处理: {}", taskId, e.getMessage());
        }
    }

    private static final Set<String> INBOUND_TYPES = Set.of(
            "purchase_in", "purchase_in_qualified", "purchase_in_unqualified",
            "outsource_in_qualified", "outsource_in_unqualified",
            "production_in", "production_return", "semi_in", "outsource_in", "return_in", "other_in", "transfer_in",
            "scrap_in");

    /**
     * 根据物料ID填充任务明细行的辅助单位字段（secondaryUnit / secondaryQty / secondaryProcessedQty）
     * 如果物料没有配置换算关系，则不设置任何辅助字段。
     */
    private void populateSecondaryUnit(BizInvTaskItem item, Long materialId, BigDecimal quantity) {
        if (materialId == null || quantity == null) return;
        BizMaterial mat = materialMapper.selectById(materialId);
        if (mat == null || mat.getUnitConfig() == null || mat.getUnitConfig().isBlank()) return;
        String secUnit = UnitConversionUtil.getSecondaryUnit(mat.getUnitConfig());
        BigDecimal secQty = UnitConversionUtil.toSecondary(quantity, mat.getUnitConfig());
        if (secUnit != null && secQty != null) {
            item.setSecondaryUnit(secUnit);
            item.setSecondaryQty(secQty);
            item.setSecondaryProcessedQty(BigDecimal.ZERO);
        }
    }

    public PageResult<Map<String, Object>> getPage(String taskNo, String relatedOrderNo, String invType,
                                                    String status, Long warehouseId, String keyword,
                                                    Boolean includeCompleted,
                                                    String dateStart, String dateEnd,
                                                    int pageNum, int pageSize) {
        // 未指定 status 过滤时，默认隐藏已完成任务（除非 includeCompleted=true）
        boolean hideCompleted = !StringUtils.hasText(status) && !Boolean.TRUE.equals(includeCompleted);
        LambdaQueryWrapper<BizInvTask> wrapper = new LambdaQueryWrapper<BizInvTask>()
                .like(StringUtils.hasText(taskNo), BizInvTask::getTaskNo, taskNo)
                .like(StringUtils.hasText(relatedOrderNo), BizInvTask::getRelatedOrderNo, relatedOrderNo)
                .eq(StringUtils.hasText(invType), BizInvTask::getInvType, invType)
                .eq(StringUtils.hasText(status), BizInvTask::getStatus, status)
                .notIn(hideCompleted, BizInvTask::getStatus, List.of("completed", "returned"))
                .eq(warehouseId != null, BizInvTask::getWarehouseId, warehouseId)
                .ge(StringUtils.hasText(dateStart), BizInvTask::getCreateTime, dateStart + " 00:00:00")
                .le(StringUtils.hasText(dateEnd), BizInvTask::getCreateTime, dateEnd + " 23:59:59")
                .orderByDesc(BizInvTask::getCreateTime);

        Page<BizInvTask> page = page(new Page<>(pageNum, pageSize), wrapper);
        List<BizInvTask> records = page.getRecords();

        Set<Long> allWhIds = new HashSet<>();
        records.forEach(t -> {
            if (t.getWarehouseId() != null) allWhIds.add(t.getWarehouseId());
            if (t.getLocationId() != null) allWhIds.add(t.getLocationId());
        });
        Map<Long, String> whNameMap = allWhIds.isEmpty() ? new HashMap<>() :
                warehouseMapper.selectBatchIds(allWhIds).stream()
                        .collect(Collectors.toMap(SysWarehouse::getId, SysWarehouse::getWarehouseName, (a, b) -> a));

        Set<Long> userIds = records.stream().map(BizInvTask::getApplicantId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> userNameMap = userIds.isEmpty() ? new HashMap<>() :
                userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, SysUser::getRealName, (a, b) -> a));

        List<Map<String, Object>> list = records.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("taskNo", t.getTaskNo());
            m.put("invType", t.getInvType());
            m.put("relatedOrderNo", t.getRelatedOrderNo());
            m.put("batchNo", t.getBatchNo());
            m.put("warehouseId", t.getWarehouseId());
            m.put("warehouseName", whNameMap.getOrDefault(t.getWarehouseId(), ""));
            m.put("locationId", t.getLocationId());
            m.put("locationName", whNameMap.getOrDefault(t.getLocationId(), ""));
            m.put("status", t.getStatus());
            m.put("sourceType", t.getSourceType());
            m.put("sourceId", t.getSourceId());
            m.put("applicantId", t.getApplicantId());
            m.put("applicantName", userNameMap.getOrDefault(t.getApplicantId(), ""));
            m.put("createTime", t.getCreateTime());
            return m;
        }).collect(Collectors.toList());

        return new PageResult<>(list, page.getTotal(), pageNum, pageSize);
    }

    public Map<String, Object> getBySource(String sourceType, Long sourceId) {
        BizInvTask task = getOne(new LambdaQueryWrapper<BizInvTask>()
                .eq(BizInvTask::getSourceType, sourceType)
                .eq(BizInvTask::getSourceId, sourceId)
                .orderByDesc(BizInvTask::getCreateTime)
                .last("LIMIT 1"));
        if (task == null) return null;
        return getDetail(task.getId());
    }

    public Map<String, Object> getDetail(Long id) {
        BizInvTask task = getById(id);
        if (task == null) throw new BusinessException("库存任务不存在");

        List<BizInvTaskItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<BizInvTaskItem>().eq(BizInvTaskItem::getTaskId, id));

        Map<Long, String> whNameMap = new HashMap<>();
        Set<Long> whIds = new HashSet<>();
        if (task.getWarehouseId() != null) whIds.add(task.getWarehouseId());
        if (task.getLocationId() != null) whIds.add(task.getLocationId());
        items.forEach(i -> {
            if (i.getWarehouseId() != null) whIds.add(i.getWarehouseId());
            if (i.getLocationId() != null) whIds.add(i.getLocationId());
        });
        if (!whIds.isEmpty()) {
            warehouseMapper.selectBatchIds(whIds).forEach(w -> whNameMap.put(w.getId(), w.getWarehouseName()));
        }

        String applicantName = "";
        if (task.getApplicantId() != null) {
            SysUser u = userMapper.selectById(task.getApplicantId());
            if (u != null) applicantName = u.getRealName();
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", task.getId());
        detail.put("taskNo", task.getTaskNo());
        detail.put("invType", task.getInvType());
        detail.put("relatedOrderNo", task.getRelatedOrderNo());
        detail.put("batchNo", task.getBatchNo());
        detail.put("warehouseId", task.getWarehouseId());
        detail.put("warehouseName", whNameMap.getOrDefault(task.getWarehouseId(), ""));
        detail.put("locationId", task.getLocationId());
        detail.put("locationName", whNameMap.getOrDefault(task.getLocationId(), ""));
        detail.put("status", task.getStatus());
        detail.put("sourceType", task.getSourceType());
        detail.put("applicantName", applicantName);
        detail.put("remark", task.getRemark());
        detail.put("createTime", task.getCreateTime());
        // 批量加载物料信息以获取 unitConfig
        Set<Long> matIds = items.stream().map(BizInvTaskItem::getMaterialId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, BizMaterial> matMap = matIds.isEmpty() ? new HashMap<>() :
                materialMapper.selectBatchIds(matIds).stream().collect(Collectors.toMap(BizMaterial::getId, m -> m, (a, b) -> a));

        List<Map<String, Object>> itemMaps = items.stream().map(i -> {
            Map<String, Object> im = new LinkedHashMap<>();
            im.put("id", i.getId());
            im.put("materialId", i.getMaterialId());
            im.put("materialNo", i.getMaterialNo());
            im.put("materialName", i.getMaterialName());
            im.put("spec", i.getSpec());
            im.put("unit", i.getUnit());
            im.put("quantity", i.getQuantity());
            im.put("processedQty", i.getProcessedQty());
            im.put("unitPrice", i.getUnitPrice());
            im.put("warehouseId", i.getWarehouseId());
            im.put("warehouseName", whNameMap.getOrDefault(i.getWarehouseId(), ""));
            im.put("locationId", i.getLocationId());
            im.put("locationName", whNameMap.getOrDefault(i.getLocationId(), ""));
            // 辅助单位：优先用任务项已存储的值，兼容旧数据从物料档案动态补充
            String secUnit = i.getSecondaryUnit();
            BigDecimal secQty = i.getSecondaryQty();
            BigDecimal secProcessedQty = i.getSecondaryProcessedQty();
            BizMaterial mat = i.getMaterialId() != null ? matMap.get(i.getMaterialId()) : null;
            if (secUnit == null && mat != null && mat.getUnitConfig() != null) {
                secUnit = UnitConversionUtil.getSecondaryUnit(mat.getUnitConfig());
                if (secUnit != null) {
                    secQty = UnitConversionUtil.toSecondary(i.getQuantity(), mat.getUnitConfig());
                    secProcessedQty = UnitConversionUtil.toSecondary(i.getProcessedQty(), mat.getUnitConfig());
                }
            }
            im.put("secondaryUnit", secUnit);
            im.put("secondaryQty", secQty);
            im.put("secondaryProcessedQty", secProcessedQty);
            im.put("unitConfig", mat != null ? mat.getUnitConfig() : null);
            return im;
        }).collect(Collectors.toList());
        detail.put("items", itemMaps);
        return detail;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateWarehouseLocation(Long id, Long warehouseId, Long locationId) {
        BizInvTask task = getById(id);
        if (task == null) throw new BusinessException("库存任务不存在");
        if ("completed".equals(task.getStatus())) throw new BusinessException("已完成任务不可修改仓库");
        task.setWarehouseId(warehouseId);
        task.setLocationId(locationId);
        updateById(task);
    }

    @Transactional(rollbackFor = Exception.class)
    public void process(Long id, Long processWarehouseId, Long processLocationId,
                        List<Map<String, Object>> itemUpdates, String remark, String historyNo) {
        BizInvTask task = getById(id);
        if (task == null) throw new BusinessException("库存任务不存在");
        if ("completed".equals(task.getStatus())) throw new BusinessException("任务已完成");

        boolean isInbound = INBOUND_TYPES.contains(task.getInvType());
        // 入库时若批次号为空则自动生成
        String batchNo = task.getBatchNo();
        if (isInbound && !StringUtils.hasText(batchNo)) {
            batchNo = generateInboundBatchNo();
            task.setBatchNo(batchNo);
            updateById(task);
        }
        // 优先使用处理时指定的仓库/库位，否则使用任务默认值
        Long effWarehouseId = processWarehouseId != null ? processWarehouseId : task.getWarehouseId();
        Long effLocationId = processLocationId != null ? processLocationId : task.getLocationId();

        // 处理时若用户没填备注, 用任务本身的 remark 兜底 (任务的 remark 通常来自源头: 申请单 remark)
        if (!StringUtils.hasText(remark) && StringUtils.hasText(task.getRemark())) {
            remark = task.getRemark();
        }

        // 处理时若指定了新仓库/库位 → 回写到任务,使列表展示与实际入库/出库仓库一致
        boolean taskWhChanged = false;
        if (processWarehouseId != null && !processWarehouseId.equals(task.getWarehouseId())) {
            task.setWarehouseId(processWarehouseId);
            taskWhChanged = true;
        }
        if (processLocationId != null && !processLocationId.equals(task.getLocationId())) {
            task.setLocationId(processLocationId);
            taskWhChanged = true;
        }
        if (taskWhChanged) {
            updateById(task);
        }

        // 处理历史单号：用户填写则校验唯一性，否则自动生成
        String finalHistoryNo;
        if (StringUtils.hasText(historyNo)) {
            long count = historyMapper.selectCount(
                    new LambdaQueryWrapper<BizInvHistory>().eq(BizInvHistory::getHistoryNo, historyNo));
            if (count > 0) throw new BusinessException("历史单号[" + historyNo + "]已存在，请使用其他单号");
            finalHistoryNo = historyNo;
        } else {
            finalHistoryNo = CodeUtil.generate("INH");
        }

        // 创建历史记录主表
        BizInvHistory history = new BizInvHistory();
        history.setHistoryNo(finalHistoryNo);
        history.setTaskId(task.getId());
        history.setTaskNo(task.getTaskNo());
        history.setRelatedOrderNo(task.getRelatedOrderNo());
        history.setInvType(task.getInvType());
        history.setBatchNo(batchNo);
        history.setWarehouseId(effWarehouseId);
        history.setLocationId(effLocationId);
        history.setStatus("normal");
        history.setApplicantId(task.getApplicantId());
        history.setOperatorId(SecurityUtil.getCurrentUserId());
        history.setRemark(remark);
        historyMapper.insert(history);

        // 性能优化: 一次 IN 查询预加载所有 item, 替代 N 次 selectById (大批量任务处理时降一个数量级)
        java.util.Set<Long> _allItemIds = itemUpdates.stream()
                .map(u -> Long.valueOf(u.get("itemId").toString()))
                .collect(java.util.stream.Collectors.toSet());
        Map<Long, BizInvTaskItem> _itemMap = _allItemIds.isEmpty() ? Map.of()
                : itemMapper.selectBatchIds(_allItemIds).stream()
                        .collect(java.util.stream.Collectors.toMap(BizInvTaskItem::getId, i -> i));
        // history 明细在循环里 build, 循环结束统一 batch insert (而非每条单独 insert)
        List<BizInvHistoryItem> _historyItemsToInsert = new ArrayList<>();

        for (Map<String, Object> update : itemUpdates) {
            Long itemId = Long.valueOf(update.get("itemId").toString());
            BigDecimal processQty = new BigDecimal(update.get("processQty").toString());
            if (processQty.compareTo(BigDecimal.ZERO) <= 0) continue;

            BizInvTaskItem item = _itemMap.get(itemId);
            if (item == null || !item.getTaskId().equals(id)) throw new BusinessException("任务明细不存在: " + itemId);

            BigDecimal remaining = item.getQuantity().subtract(item.getProcessedQty() != null ? item.getProcessedQty() : BigDecimal.ZERO);
            if (processQty.compareTo(remaining) > 0) {
                throw new BusinessException("处理数量[" + processQty + "]超过剩余数量[" + remaining + "]，物料: " + item.getMaterialName());
            }
            // 物料级仓库优先级：请求体 > DB存储 > 任务级
            Long reqItemWhId = update.get("warehouseId") != null ? Long.valueOf(update.get("warehouseId").toString()) : null;
            Long reqItemLocId = update.get("locationId") != null ? Long.valueOf(update.get("locationId").toString()) : null;
            Long itemWarehouseId = reqItemWhId != null ? reqItemWhId : (item.getWarehouseId() != null ? item.getWarehouseId() : effWarehouseId);
            Long itemLocationId = reqItemLocId != null ? reqItemLocId : (item.getLocationId() != null ? item.getLocationId() : effLocationId);

            // 仓库必选校验
            if (itemWarehouseId == null) {
                throw new BusinessException("物料[" + (item.getMaterialName() != null ? item.getMaterialName() : item.getMaterialId()) + "]未选择仓库，请指定入库/出库仓库");
            }

            // 出库时校验台账可用是否充足(只看 biz_inventory.available_qty = total - locked)
            // 不再减 pendingOut, 避免别的未处理出库任务把当前实际可拨库存吞掉
            if (!isInbound && item.getMaterialId() != null) {
                BigDecimal[] info = inventoryService.getStockInfo(item.getMaterialId(), itemWarehouseId);
                BigDecimal availableQty = info[2]; // [totalQty, lockedQty, availableQty]
                if (availableQty.compareTo(processQty) < 0) {
                    throw new BusinessException("库存不足：物料[" + item.getMaterialName() + "]可用库存[" + availableQty + "]，出库数量[" + processQty + "]");
                }
            }

            // 辅助单位处理数量（从请求中获取，可选）
            BigDecimal secondaryProcessQty = update.get("secondaryProcessQty") != null
                    ? new BigDecimal(update.get("secondaryProcessQty").toString()) : null;
            String itemSecondaryUnit = item.getSecondaryUnit();
            // 如果请求未提供辅助数量，但物料有换算配置，则自动计算
            if (item.getMaterialId() != null && (!StringUtils.hasText(itemSecondaryUnit) || secondaryProcessQty == null)) {
                BizMaterial mat = materialMapper.selectById(item.getMaterialId());
                if (mat != null) {
                    if (!StringUtils.hasText(itemSecondaryUnit)) {
                        itemSecondaryUnit = UnitConversionUtil.getSecondaryUnit(mat.getUnitConfig());
                    }
                    if (secondaryProcessQty == null) {
                        secondaryProcessQty = UnitConversionUtil.toSecondary(processQty, mat.getUnitConfig());
                    }
                }
            }

            // 创建历史明细
            BizInvHistoryItem hi = new BizInvHistoryItem();
            hi.setHistoryId(history.getId());
            hi.setMaterialId(item.getMaterialId());
            hi.setMaterialNo(item.getMaterialNo());
            hi.setMaterialName(item.getMaterialName());
            hi.setSpec(item.getSpec());
            hi.setUnit(item.getUnit());
            hi.setQuantity(processQty);
            hi.setUnitPrice(item.getUnitPrice());
            if (item.getUnitPrice() != null) hi.setTotalPrice(processQty.multiply(item.getUnitPrice()));
            hi.setWarehouseId(itemWarehouseId);
            hi.setLocationId(itemLocationId);
            hi.setTaskItemId(item.getId()); // 精确关联任务明细行，支持同物料多行场景
            hi.setStatus("normal");
            hi.setSecondaryUnit(itemSecondaryUnit);
            hi.setSecondaryQty(secondaryProcessQty);
            _historyItemsToInsert.add(hi); // 累积, 循环外批量 insert

            // 更新任务明细已处理数量
            BigDecimal newProcessed = (item.getProcessedQty() != null ? item.getProcessedQty() : BigDecimal.ZERO).add(processQty);
            item.setProcessedQty(newProcessed);
            if (secondaryProcessQty != null) {
                BigDecimal newSecProcessed = (item.getSecondaryProcessedQty() != null ? item.getSecondaryProcessedQty() : BigDecimal.ZERO)
                        .add(secondaryProcessQty);
                item.setSecondaryProcessedQty(newSecProcessed);
            }
            // 处理时若指定了新仓库/库位,同步回写到明细行,避免列表/详情显示与实际入库不一致
            if (reqItemWhId != null && !reqItemWhId.equals(item.getWarehouseId())) {
                item.setWarehouseId(reqItemWhId);
            }
            if (reqItemLocId != null && !reqItemLocId.equals(item.getLocationId())) {
                item.setLocationId(reqItemLocId);
            }
            itemMapper.updateById(item);

            // 采购入库：更新订单明细的入库数量 + 收料单回调
            if ("purchase_in".equals(task.getInvType()) && "purchase_receipt".equals(task.getSourceType()) && task.getSourceId() != null) {
                // 新流程：通过收料单服务回调
                try {
                    com.aimes.modules.purchase.service.PurchaseReceiptService receiptService =
                            applicationContext.getBean(com.aimes.modules.purchase.service.PurchaseReceiptService.class);
                    receiptService.onInvTaskProcess(task.getSourceId(), processQty);
                } catch (Exception e) {
                    log.warn("采购入库更新收料单失败: receiptId={}", task.getSourceId(), e);
                }
            } else if ("purchase_in".equals(task.getInvType()) && item.getReceiptId() != null) {
                // 旧流程兼容
                BizPurchaseReceipt receipt = purchaseReceiptMapper.selectById(item.getReceiptId());
                if (receipt != null && receipt.getOrderItemId() != null) {
                    BizPurchaseOrderItem oi = purchaseOrderItemMapper.selectById(receipt.getOrderItemId());
                    if (oi != null) {
                        oi.setStockInQty((oi.getStockInQty() != null ? oi.getStockInQty() : BigDecimal.ZERO).add(processQty));
                        purchaseOrderItemMapper.updateById(oi);
                    }
                }
            }

            // 采购入库-质检合格/不合格：通过QC任务→收料单回调
            if (("purchase_in_qualified".equals(task.getInvType()) || "purchase_in_unqualified".equals(task.getInvType()))
                    && "qc_task".equals(task.getSourceType()) && task.getSourceId() != null) {
                try {
                    // 通过QC任务找到收料单
                    com.aimes.modules.quality.entity.BizQcTask qcTask = qcTaskMapper.selectById(task.getSourceId());
                    if (qcTask != null && "purchase_receipt".equals(qcTask.getRelatedOrderType()) && qcTask.getRelatedOrderId() != null) {
                        com.aimes.modules.purchase.service.PurchaseReceiptService receiptService =
                                applicationContext.getBean(com.aimes.modules.purchase.service.PurchaseReceiptService.class);
                        receiptService.onInvTaskProcess(qcTask.getRelatedOrderId(), processQty);
                    }
                } catch (Exception e) {
                    log.warn("质检库存任务处理更新收料单失败: qcTaskId={}", task.getSourceId(), e);
                }
            }

            // 外协入库-质检合格/不合格：通过QC任务→收料单回调
            if (("outsource_in_qualified".equals(task.getInvType()) || "outsource_in_unqualified".equals(task.getInvType()))
                    && "qc_task".equals(task.getSourceType()) && task.getSourceId() != null) {
                try {
                    com.aimes.modules.quality.entity.BizQcTask qcTask2 = qcTaskMapper.selectById(task.getSourceId());
                    if (qcTask2 != null && "outsource_receipt".equals(qcTask2.getRelatedOrderType()) && qcTask2.getRelatedOrderId() != null) {
                        com.aimes.modules.outsource.service.OutsourceReceiptService outsourceReceiptService =
                                applicationContext.getBean(com.aimes.modules.outsource.service.OutsourceReceiptService.class);
                        outsourceReceiptService.onInvTaskProcess(qcTask2.getRelatedOrderId(), processQty);
                    }
                } catch (Exception e) {
                    log.warn("外协质检库存任务处理更新收料单失败: qcTaskId={}", task.getSourceId(), e);
                }
            }

            // 外协入库(无质检): sourceType=outsource_receipt → 回调收料单
            if ("outsource_in".equals(task.getInvType()) && "outsource_receipt".equals(task.getSourceType()) && task.getSourceId() != null) {
                try {
                    com.aimes.modules.outsource.service.OutsourceReceiptService outsourceReceiptService =
                            applicationContext.getBean(com.aimes.modules.outsource.service.OutsourceReceiptService.class);
                    outsourceReceiptService.onInvTaskProcess(task.getSourceId(), processQty);
                } catch (Exception e) {
                    log.warn("外协入库更新收料单失败: receiptId={}", task.getSourceId(), e);
                }
            }

            // 外协入库-质检合格也生成应付
            if ("outsource_in_qualified".equals(task.getInvType()) && item.getMaterialId() != null) {
                try {
                    generatePayableOnPurchaseIn(task, item.getMaterialId(), processQty);
                } catch (Exception e) {
                    log.warn("外协入库生成应付失败: taskId={}, materialId={}", id, item.getMaterialId(), e);
                }
            }

            // 生产领料出库：更新BOM快照的已领数量
            // 替代料：回写到主料快照，并按 substituteRatio 折算 processQty → 主料等效量
            if ("production_out".equals(task.getInvType()) && "work_order".equals(task.getSourceType())
                    && task.getSourceId() != null && item.getMaterialId() != null) {
                Long snapMatId = item.getSubstituteForMaterialId() != null
                        ? item.getSubstituteForMaterialId() : item.getMaterialId();
                BigDecimal snapQty = processQty;
                if (item.getSubstituteForMaterialId() != null) {
                    BigDecimal ratio = item.getSubstituteRatio() != null
                            && item.getSubstituteRatio().compareTo(BigDecimal.ZERO) > 0
                            ? item.getSubstituteRatio() : BigDecimal.ONE;
                    snapQty = processQty.divide(ratio, 4, java.math.RoundingMode.HALF_UP);
                }
                BizWoBomSnapshot snap = bomSnapshotMapper.selectOne(
                        new LambdaQueryWrapper<BizWoBomSnapshot>()
                                .eq(BizWoBomSnapshot::getWorkOrderId, task.getSourceId())
                                .eq(BizWoBomSnapshot::getMaterialId, snapMatId)
                                .last("LIMIT 1"));
                if (snap != null) {
                    snap.setIssuedQty((snap.getIssuedQty() != null ? snap.getIssuedQty() : BigDecimal.ZERO).add(snapQty));
                    bomSnapshotMapper.updateById(snap);
                }
            }

            // 生产退料入库：更新BOM快照的已退数量
            if ("production_return".equals(task.getInvType()) && "work_order".equals(task.getSourceType())
                    && task.getSourceId() != null && item.getMaterialId() != null) {
                BizWoBomSnapshot snap = bomSnapshotMapper.selectOne(
                        new LambdaQueryWrapper<BizWoBomSnapshot>()
                                .eq(BizWoBomSnapshot::getWorkOrderId, task.getSourceId())
                                .eq(BizWoBomSnapshot::getMaterialId, item.getMaterialId())
                                .last("LIMIT 1"));
                if (snap != null) {
                    snap.setReturnedQty((snap.getReturnedQty() != null ? snap.getReturnedQty() : BigDecimal.ZERO).add(processQty));
                    bomSnapshotMapper.updateById(snap);
                }
            }

            // 采购退货出库：更新关联采购订单明细的已退货出库数量
            if ("purchase_return".equals(task.getInvType()) && "purchase_return".equals(task.getSourceType())
                    && task.getSourceId() != null && item.getMaterialId() != null) {
                com.aimes.modules.purchase.entity.BizPurchaseReturn ret =
                        purchaseReturnMapper.selectById(task.getSourceId());
                if (ret != null && ret.getPurchaseOrderId() != null) {
                    BizPurchaseOrderItem oi = purchaseOrderItemMapper.selectOne(
                            new LambdaQueryWrapper<BizPurchaseOrderItem>()
                                    .eq(BizPurchaseOrderItem::getPurchaseOrderId, ret.getPurchaseOrderId())
                                    .eq(BizPurchaseOrderItem::getMaterialId, item.getMaterialId())
                                    .last("LIMIT 1"));
                    if (oi != null) {
                        oi.setReturnOutQty((oi.getReturnOutQty() != null ? oi.getReturnOutQty() : BigDecimal.ZERO).add(processQty));
                        purchaseOrderItemMapper.updateById(oi);
                    }
                }
            }

            // 更新库存(传入实际业务类型 + 关联单据信息, 流水记录可回查源头)
            // 关联单据: 优先用 task.sourceType / sourceId (业务源, 如 work_order / sales_order / inv_request);
            // 没有时退回到 inv_task / task.id, 至少能从流水跳回任务详情
            String relType = task.getSourceType() != null && !task.getSourceType().isEmpty()
                    ? task.getSourceType() : "inv_task";
            Long relId = task.getSourceId() != null ? task.getSourceId() : task.getId();
            inventoryService.updateStock(item.getMaterialId(), processQty, isInbound, itemWarehouseId, itemLocationId,
                    itemSecondaryUnit, secondaryProcessQty, task.getInvType(),
                    relType, relId, task.getBatchNo(), task.getRemark());

            // 销售出库联动：更新订单发货数量 + 更新订单状态
            // 注: 应收账款不再在销售出库时生成,统一改在订单审核通过(approve)时按订单总额一次性生成
            if ("sales_out".equals(task.getInvType()) && "sales_order".equals(task.getSourceType())
                    && task.getSourceId() != null) {
                try {
                    inventoryService.onSalesStockOut(task.getSourceId(), item.getMaterialId(), processQty);
                } catch (Exception e) {
                    log.warn("销售出库联动失败: taskId={}, orderId={}, materialId={}", id, task.getSourceId(), item.getMaterialId(), e);
                }
            }

            // 采购入库联动：按实际入库数量 × 采购订单单价生成应付账款（含质检合格入库）
            if (("purchase_in".equals(task.getInvType()) || "purchase_in_qualified".equals(task.getInvType()))
                    && item.getMaterialId() != null) {
                try {
                    generatePayableOnPurchaseIn(task, item.getMaterialId(), processQty);
                } catch (Exception e) {
                    log.warn("采购入库生成应付失败: taskId={}, materialId={}", id, item.getMaterialId(), e);
                }
            }
        }

        // 批量 flush 累积的 history items (替代 N 次单条 insert, 大批量场景显著降低 round trip)
        if (!_historyItemsToInsert.isEmpty()) {
            batchInsertHelper.insertAll(_historyItemsToInsert,
                    com.aimes.modules.inventory.mapper.BizInvHistoryItemMapper.class);
        }

        // 检查是否全部完成
        List<BizInvTaskItem> allItems = itemMapper.selectList(
                new LambdaQueryWrapper<BizInvTaskItem>().eq(BizInvTaskItem::getTaskId, id));
        boolean allDone = allItems.stream().allMatch(i ->
                i.getQuantity() != null && i.getProcessedQty() != null
                        && i.getProcessedQty().compareTo(i.getQuantity()) >= 0);
        task.setStatus(allDone ? "completed" : "partial");
        updateById(task);

        // 采购入库：任务处理后更新PO的invTaskProcessStatus，并检查是否自动办结
        if ("purchase_in".equals(task.getInvType())) {
            Long orderId = getOrderIdFromTask(task);
            if (orderId != null) {
                updateInvTaskGenStatus(orderId);
                updatePOInvTaskProcessStatus(orderId);
                checkAndAutoClose(orderId);
            }
        }

        // 工单成品入库任务处理后，触发批次入库联动（每次处理都累计，支持分批入库）
        if ("production_in".equals(task.getInvType()) && "work_order".equals(task.getSourceType())
                && task.getSourceId() != null) {
            try {
                BigDecimal batchQty = itemUpdates.stream()
                        .map(u -> new BigDecimal(u.get("processQty").toString()))
                        .filter(q -> q.compareTo(BigDecimal.ZERO) > 0)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                if (batchQty.compareTo(BigDecimal.ZERO) > 0) {
                    productionStockInService.onBatchStockIn(task.getSourceId(), batchQty);
                }
            } catch (Exception e) {
                log.error("批次入库联动工单失败: taskId={}, workOrderId={}", id, task.getSourceId(), e);
            }
        }

        // 工序半成品入库任务处理后, 累加工序的 semi_stocked_qty
        if ("semi_in".equals(task.getInvType()) && "wo_process_task".equals(task.getSourceType())
                && task.getSourceId() != null) {
            try {
                BigDecimal batchQty = itemUpdates.stream()
                        .map(u -> new BigDecimal(u.get("processQty").toString()))
                        .filter(q -> q.compareTo(BigDecimal.ZERO) > 0)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                if (batchQty.compareTo(BigDecimal.ZERO) > 0) {
                    semiStockService.onSemiStockInCompleted(task.getSourceId(), batchQty);
                }
            } catch (Exception e) {
                log.error("半成品入库回调失败: taskId={}, processTaskId={}", id, task.getSourceId(), e);
            }
        }

        // 工序半成品领料任务处理后, 累加上道工序的 semi_issued_qty
        if ("production_out".equals(task.getInvType())
                && "wo_process_task_issue".equals(task.getSourceType())
                && task.getSourceId() != null) {
            try {
                BigDecimal batchQty = itemUpdates.stream()
                        .map(u -> new BigDecimal(u.get("processQty").toString()))
                        .filter(q -> q.compareTo(BigDecimal.ZERO) > 0)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                if (batchQty.compareTo(BigDecimal.ZERO) > 0) {
                    semiStockService.onSemiIssueCompleted(task.getSourceId(), batchQty);
                }
            } catch (Exception e) {
                log.error("半成品领料回调失败: taskId={}, processTaskId={}", id, task.getSourceId(), e);
            }
        }

        // 外协任务回调（发料/收料）
        if ("outsource_order".equals(task.getSourceType()) && task.getSourceId() != null) {
            BigDecimal thisBatchQty = itemUpdates.stream()
                    .map(u -> new BigDecimal(u.get("processQty").toString()))
                    .filter(q -> q.compareTo(BigDecimal.ZERO) > 0)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if ("outsource_out".equals(task.getInvType()) && thisBatchQty.compareTo(BigDecimal.ZERO) > 0) {
                try {
                    onOutsourceOutTaskProcessed(task.getSourceId(), thisBatchQty);
                } catch (Exception e) {
                    log.error("外协发料任务联动失败: taskId={}, outsourceOrderId={}", id, task.getSourceId(), e);
                }
            }
            if ("outsource_in".equals(task.getInvType()) && thisBatchQty.compareTo(BigDecimal.ZERO) > 0) {
                try {
                    onOutsourceInTaskProcessed(task.getSourceId(), thisBatchQty, allDone);
                } catch (Exception e) {
                    log.error("外协收料任务联动失败: taskId={}, outsourceOrderId={}", id, task.getSourceId(), e);
                }
            }
        }

        // 采购退货出库：全部完成后关闭退货单
        if (allDone && "purchase_return".equals(task.getInvType()) && "purchase_return".equals(task.getSourceType())
                && task.getSourceId() != null) {
            try {
                com.aimes.modules.purchase.entity.BizPurchaseReturn ret =
                        purchaseReturnMapper.selectById(task.getSourceId());
                if (ret != null && !"closed".equals(ret.getStatus())) {
                    ret.setStatus("closed");
                    purchaseReturnMapper.updateById(ret);
                }
            } catch (Exception e) {
                log.error("采购退货任务完成后关闭退货单失败: taskId={}, returnId={}", id, task.getSourceId(), e);
            }
        }
    }

    /**
     * 采购入库库存任务处理后：按实际入库数量 × 采购订单明细单价生成应付账款
     */
    private void generatePayableOnPurchaseIn(BizInvTask task, Long materialId, BigDecimal processQty) {
        Long orderId = getOrderIdFromTask(task);
        if (orderId == null) return;

        BizPurchaseOrder po = purchaseOrderMapper.selectById(orderId);
        if (po == null) return;

        // 查找匹配的采购订单明细获取单价
        List<BizPurchaseOrderItem> poItems = purchaseOrderItemMapper.selectList(
                new LambdaQueryWrapper<BizPurchaseOrderItem>()
                        .eq(BizPurchaseOrderItem::getPurchaseOrderId, orderId)
                        .eq(BizPurchaseOrderItem::getMaterialId, materialId));
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (BizPurchaseOrderItem poItem : poItems) {
            if (poItem.getUnitPrice() != null) {
                totalAmount = totalAmount.add(processQty.multiply(poItem.getUnitPrice()));
                break; // 取第一个匹配的单价
            }
        }
        if (totalAmount.compareTo(BigDecimal.ZERO) <= 0) return;

        com.aimes.modules.finance.entity.BizPayable payable = new com.aimes.modules.finance.entity.BizPayable();
        payable.setSupplierId(po.getSupplierId());
        payable.setAmount(totalAmount);
        payable.setSourceType("purchase_order");
        payable.setSourceId(orderId);
        payable.setDueDate(LocalDate.now().plusDays(30));
        payable.setRemark("采购入库自动生成应付-" + po.getPoNo());
        financeService.createPayable(payable);
    }

    /**
     * 根据采购收料单创建库存任务
     */
    /**
     * 根据外协收料单创建库存任务（无需质检场景）
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createFromOutsourceReceipt(
            Long receiptId, String receiptNo, String orderNo, String batchNo,
            Long warehouseId, Long applicantId,
            Long materialId, String materialNo, String materialName,
            String spec, String unit, BigDecimal quantity, BigDecimal unitPrice) {
        BizInvTask task = new BizInvTask();
        task.setTaskNo(CodeUtil.generate("INK"));
        task.setInvType("outsource_in");
        task.setRelatedOrderNo(receiptNo);
        task.setBatchNo(batchNo);
        task.setWarehouseId(warehouseId);
        task.setStatus("pending");
        task.setSourceType("outsource_receipt");
        task.setSourceId(receiptId);
        task.setApplicantId(applicantId);
        task.setRemark("外协收料单[" + receiptNo + "]自动创建");
        save(task);

        BizInvTaskItem taskItem = new BizInvTaskItem();
        taskItem.setTaskId(task.getId());
        taskItem.setMaterialId(materialId);
        taskItem.setMaterialNo(materialNo);
        taskItem.setMaterialName(materialName);
        taskItem.setSpec(spec);
        taskItem.setUnit(unit);
        taskItem.setQuantity(quantity);
        taskItem.setUnitPrice(unitPrice);
        if (unitPrice != null && quantity != null) {
            taskItem.setTotalPrice(quantity.multiply(unitPrice));
        }
        taskItem.setProcessedQty(BigDecimal.ZERO);
        taskItem.setWarehouseId(warehouseId);
        taskItem.setReceiptId(receiptId);
        populateSecondaryUnit(taskItem, materialId, quantity);
        itemMapper.insert(taskItem);

        return task.getId();
    }

    /**
     * 根据采购收料单创建库存任务
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createFromPurchaseReceipt(
            Long receiptId, String receiptNo, String poNo, String batchNo,
            Long warehouseId, Long applicantId,
            Long materialId, String materialNo, String materialName,
            String spec, String unit, BigDecimal quantity, BigDecimal unitPrice) {
        BizInvTask task = new BizInvTask();
        task.setTaskNo(CodeUtil.generate("INK"));
        task.setInvType("purchase_in");
        task.setRelatedOrderNo(receiptNo);
        task.setBatchNo(batchNo);
        task.setWarehouseId(warehouseId);
        task.setStatus("pending");
        task.setSourceType("purchase_receipt");
        task.setSourceId(receiptId);
        task.setApplicantId(applicantId);
        task.setRemark("采购收料单[" + receiptNo + "]自动创建");
        save(task);

        BizInvTaskItem taskItem = new BizInvTaskItem();
        taskItem.setTaskId(task.getId());
        taskItem.setMaterialId(materialId);
        taskItem.setMaterialNo(materialNo);
        taskItem.setMaterialName(materialName);
        taskItem.setSpec(spec);
        taskItem.setUnit(unit);
        taskItem.setQuantity(quantity);
        taskItem.setUnitPrice(unitPrice);
        if (unitPrice != null && quantity != null) {
            taskItem.setTotalPrice(quantity.multiply(unitPrice));
        }
        taskItem.setProcessedQty(BigDecimal.ZERO);
        taskItem.setWarehouseId(warehouseId);
        taskItem.setReceiptId(receiptId);
        populateSecondaryUnit(taskItem, materialId, quantity);
        itemMapper.insert(taskItem);

        // 根据业务配置判断是否需要自动处理
        if (!bizConfigService.getBoolValue("warehouse.purchase_in.need_process", true)) {
            tryAutoProcess(task.getId());
        }
        return task.getId();
    }

    /**
     * 根据质检任务创建库存任务（采购收料单质检完成后调用）
     * @param invType purchase_in_qualified 或 purchase_in_unqualified
     * @param relatedOrderNo 质检任务编号
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createFromQcTask(
            Long qcTaskId, String receiptNo, String poNo, String batchNo,
            Long warehouseId, Long applicantId,
            Long materialId, String materialNo, String materialName,
            String spec, String unit, BigDecimal quantity, BigDecimal unitPrice,
            String invType, String relatedOrderNo) {
        BizInvTask task = new BizInvTask();
        task.setTaskNo(CodeUtil.generate("INK"));
        task.setInvType(invType);
        task.setRelatedOrderNo(relatedOrderNo);
        task.setBatchNo(batchNo);
        task.setWarehouseId(warehouseId);
        task.setStatus("pending");
        task.setSourceType("qc_task");
        task.setSourceId(qcTaskId);
        task.setApplicantId(applicantId);
        task.setRemark("收料单[" + receiptNo + "]质检后创建-" + invType);
        save(task);

        BizInvTaskItem taskItem = new BizInvTaskItem();
        taskItem.setTaskId(task.getId());
        taskItem.setMaterialId(materialId);
        taskItem.setMaterialNo(materialNo);
        taskItem.setMaterialName(materialName);
        taskItem.setSpec(spec);
        taskItem.setUnit(unit);
        taskItem.setQuantity(quantity);
        taskItem.setUnitPrice(unitPrice);
        if (unitPrice != null && quantity != null) {
            taskItem.setTotalPrice(quantity.multiply(unitPrice));
        }
        taskItem.setProcessedQty(BigDecimal.ZERO);
        taskItem.setWarehouseId(warehouseId);
        populateSecondaryUnit(taskItem, materialId, quantity);
        itemMapper.insert(taskItem);

        return task.getId();
    }

    /**
     * 根据来源查询库存任务列表（供采购收料单详情使用）
     */
    public List<Map<String, Object>> findBySource(String sourceType, Long sourceId) {
        List<BizInvTask> tasks = list(new LambdaQueryWrapper<BizInvTask>()
                .eq(BizInvTask::getSourceType, sourceType)
                .eq(BizInvTask::getSourceId, sourceId)
                .ne(BizInvTask::getStatus, "returned")
                .orderByDesc(BizInvTask::getCreateTime));
        return tasks.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("taskNo", t.getTaskNo());
            m.put("invType", t.getInvType());
            m.put("status", t.getStatus());
            m.put("relatedOrderNo", t.getRelatedOrderNo());
            m.put("batchNo", t.getBatchNo());
            // 加载明细汇总
            List<BizInvTaskItem> items = itemMapper.selectList(
                    new LambdaQueryWrapper<BizInvTaskItem>().eq(BizInvTaskItem::getTaskId, t.getId()));
            BigDecimal totalQty = items.stream().map(i -> i.getQuantity() != null ? i.getQuantity() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalProcessed = items.stream().map(i -> i.getProcessedQty() != null ? i.getProcessedQty() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
            m.put("quantity", totalQty);
            m.put("processedQty", totalProcessed);
            m.put("createTime", t.getCreateTime());
            return m;
        }).toList();
    }

    /**
     * 质检通过后自动完成库存任务（不重复更新库存，库存已由 stockIn 处理）
     * 优先按 receipt_id 精准匹配 task_item，不存在时回退到 source_type=purchase_receipt 整体匹配
     */
    @Transactional(rollbackFor = Exception.class)
    public void autoProcessFromStockIn(Long receiptId, BigDecimal passQty, Long warehouseId) {
        // 优先按 receipt_id 精准匹配（批量收料场景：多收料单共用一个 inv_task）
        BizInvTaskItem targetItem = itemMapper.selectOne(new LambdaQueryWrapper<BizInvTaskItem>()
                .eq(BizInvTaskItem::getReceiptId, receiptId));

        if (targetItem != null) {
            BizInvTask task = getById(targetItem.getTaskId());
            if (task == null || "completed".equals(task.getStatus())) return;
            processOneItemFromQc(task, targetItem, passQty, warehouseId);
            return;
        }

        // 回退：按 source_type=purchase_receipt 整体匹配（单条收料场景）
        BizInvTask task = getOne(new LambdaQueryWrapper<BizInvTask>()
                .eq(BizInvTask::getSourceType, "purchase_receipt")
                .eq(BizInvTask::getSourceId, receiptId));
        if (task == null || "completed".equals(task.getStatus())) return;

        List<BizInvTaskItem> taskItems = itemMapper.selectList(
                new LambdaQueryWrapper<BizInvTaskItem>().eq(BizInvTaskItem::getTaskId, task.getId()));
        if (taskItems.isEmpty()) return;

        BizInvHistory history = buildHistory(task, warehouseId, "质检通过自动入库");
        historyMapper.insert(history);

        BigDecimal remaining = passQty;
        for (BizInvTaskItem item : taskItems) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal itemRemaining = item.getQuantity().subtract(
                    item.getProcessedQty() != null ? item.getProcessedQty() : BigDecimal.ZERO);
            if (itemRemaining.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal processQty = remaining.min(itemRemaining);
            insertHistoryItem(history.getId(), item, processQty, warehouseId);
            item.setProcessedQty((item.getProcessedQty() != null ? item.getProcessedQty() : BigDecimal.ZERO).add(processQty));
            itemMapper.updateById(item);
            // 采购入库：更新采购订单明细的入库数量
            if ("purchase_in".equals(task.getInvType()) && item.getReceiptId() != null) {
                BizPurchaseReceipt receipt = purchaseReceiptMapper.selectById(item.getReceiptId());
                if (receipt != null && receipt.getOrderItemId() != null) {
                    BizPurchaseOrderItem oi = purchaseOrderItemMapper.selectById(receipt.getOrderItemId());
                    if (oi != null) {
                        oi.setStockInQty((oi.getStockInQty() != null ? oi.getStockInQty() : BigDecimal.ZERO).add(processQty));
                        purchaseOrderItemMapper.updateById(oi);
                    }
                }
            }
            remaining = remaining.subtract(processQty);
        }
        refreshTaskStatus(task);

        // 采购入库：刷新采购订单的库存任务处理状态，并检查是否自动办结
        Long orderId = getOrderIdFromTask(task);
        if (orderId != null) {
            updatePOInvTaskProcessStatus(orderId);
            checkAndAutoClose(orderId);
        }
    }

    /** 处理单条 task_item（批量收料场景下 QC 通过调用） */
    private void processOneItemFromQc(BizInvTask task, BizInvTaskItem item, BigDecimal passQty, Long warehouseId) {
        BigDecimal itemRemaining = item.getQuantity().subtract(
                item.getProcessedQty() != null ? item.getProcessedQty() : BigDecimal.ZERO);
        if (itemRemaining.compareTo(BigDecimal.ZERO) <= 0) return;
        BigDecimal processQty = passQty.min(itemRemaining);

        Long effWarehouse = item.getWarehouseId() != null ? item.getWarehouseId() : warehouseId;
        Long effLocation  = item.getLocationId();

        BizInvHistory history = buildHistory(task, effWarehouse, "质检通过自动入库");
        history.setLocationId(effLocation);
        historyMapper.insert(history);

        insertHistoryItem(history.getId(), item, processQty, effWarehouse);
        item.setProcessedQty((item.getProcessedQty() != null ? item.getProcessedQty() : BigDecimal.ZERO).add(processQty));
        itemMapper.updateById(item);

        // 采购入库：更新采购订单明细的入库数量
        if ("purchase_in".equals(task.getInvType()) && item.getReceiptId() != null) {
            BizPurchaseReceipt receipt = purchaseReceiptMapper.selectById(item.getReceiptId());
            if (receipt != null && receipt.getOrderItemId() != null) {
                BizPurchaseOrderItem oi = purchaseOrderItemMapper.selectById(receipt.getOrderItemId());
                if (oi != null) {
                    oi.setStockInQty((oi.getStockInQty() != null ? oi.getStockInQty() : BigDecimal.ZERO).add(processQty));
                    purchaseOrderItemMapper.updateById(oi);
                }
            }
        }

        refreshTaskStatus(task);

        // 采购入库：刷新采购订单的库存任务处理状态，并检查是否自动办结
        Long orderId = getOrderIdFromTask(task);
        if (orderId != null) {
            updatePOInvTaskProcessStatus(orderId);
            checkAndAutoClose(orderId);
        }
    }

    private BizInvHistory buildHistory(BizInvTask task, Long warehouseId, String remark) {
        BizInvHistory h = new BizInvHistory();
        h.setHistoryNo(CodeUtil.generate("INH"));
        h.setTaskId(task.getId());
        h.setTaskNo(task.getTaskNo());
        h.setRelatedOrderNo(task.getRelatedOrderNo());
        h.setInvType(task.getInvType());
        h.setBatchNo(task.getBatchNo());
        h.setWarehouseId(warehouseId);
        h.setStatus("normal");
        h.setApplicantId(task.getApplicantId());
        h.setOperatorId(SecurityUtil.getCurrentUserId());
        h.setRemark(remark);
        return h;
    }

    private void insertHistoryItem(Long historyId, BizInvTaskItem item, BigDecimal qty, Long warehouseId) {
        BizInvHistoryItem hi = new BizInvHistoryItem();
        hi.setHistoryId(historyId);
        hi.setMaterialId(item.getMaterialId());
        hi.setMaterialNo(item.getMaterialNo());
        hi.setMaterialName(item.getMaterialName());
        hi.setSpec(item.getSpec());
        hi.setUnit(item.getUnit());
        hi.setQuantity(qty);
        hi.setUnitPrice(item.getUnitPrice());
        if (item.getUnitPrice() != null) hi.setTotalPrice(qty.multiply(item.getUnitPrice()));
        hi.setWarehouseId(item.getWarehouseId() != null ? item.getWarehouseId() : warehouseId);
        hi.setLocationId(item.getLocationId());
        hi.setTaskItemId(item.getId());
        hi.setStatus("normal");
        // 辅助单位：按比例换算处理数量对应的辅助数量
        hi.setSecondaryUnit(item.getSecondaryUnit());
        if (item.getSecondaryUnit() != null && item.getMaterialId() != null) {
            BizMaterial mat = materialMapper.selectById(item.getMaterialId());
            if (mat != null) {
                hi.setSecondaryQty(UnitConversionUtil.toSecondary(qty, mat.getUnitConfig()));
            }
        }
        historyItemMapper.insert(hi);
    }

    private void refreshTaskStatus(BizInvTask task) {
        List<BizInvTaskItem> allItems = itemMapper.selectList(
                new LambdaQueryWrapper<BizInvTaskItem>().eq(BizInvTaskItem::getTaskId, task.getId()));
        boolean allDone = allItems.stream().allMatch(i ->
                i.getQuantity() != null && i.getProcessedQty() != null
                        && i.getProcessedQty().compareTo(i.getQuantity()) >= 0);
        task.setStatus(allDone ? "completed" : "partial");
        updateById(task);
    }

    /**
     * 自动生成入库批次号：IT + yyyyMMdd + 三位序号（001起，当日全局递增）
     * 仅在未手动填写批次号时调用。synchronized 防止同一 JVM 内并发重号。
     */
    public synchronized String generateInboundBatchNo() {
        String prefix = "IT" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        List<BizInvHistory> todayHistories = historyMapper.selectList(
                new LambdaQueryWrapper<BizInvHistory>()
                        .likeRight(BizInvHistory::getBatchNo, prefix)
                        .select(BizInvHistory::getBatchNo));
        int maxSeq = todayHistories.stream()
                .map(BizInvHistory::getBatchNo)
                .filter(no -> no != null && no.length() == prefix.length() + 3)
                .mapToInt(no -> {
                    try { return Integer.parseInt(no.substring(prefix.length())); }
                    catch (NumberFormatException e) { return 0; }
                })
                .max().orElse(0);
        return prefix + String.format("%03d", maxSeq + 1);
    }

    /**
     * 工单成品质检通过 → 创建 production_in 入库任务
     * 同一工单已存在未完成(pending/inspecting/...)的入库任务时,把本次数量追加到该任务的明细行,
     * 避免分批/并行末道工序报工时新增多条 pending 任务或丢失数量。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createFromWorkOrder(Long workOrderId, String woNo, Long productId,
                                     String materialNo, String materialName, String spec, String unit,
                                     BigDecimal qty, Long defaultWarehouseId, Long applicantId) {
        // 已有未完成的同源入库任务 → 把本批数量追加到首条明细
        BizInvTask existing = getOne(new LambdaQueryWrapper<BizInvTask>()
                .eq(BizInvTask::getSourceType, "work_order")
                .eq(BizInvTask::getSourceId, workOrderId)
                .eq(BizInvTask::getInvType, "production_in")
                .ne(BizInvTask::getStatus, "completed"));
        if (existing != null) {
            if (qty != null && qty.compareTo(BigDecimal.ZERO) > 0) {
                BizInvTaskItem item = itemMapper.selectOne(new LambdaQueryWrapper<BizInvTaskItem>()
                        .eq(BizInvTaskItem::getTaskId, existing.getId())
                        .last("LIMIT 1"));
                if (item != null) {
                    BigDecimal cur = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
                    item.setQuantity(cur.add(qty));
                    populateSecondaryUnit(item, item.getMaterialId(), item.getQuantity());
                    itemMapper.updateById(item);
                }
                BizWorkOrder wo = workOrderMapper.selectById(workOrderId);
                if (wo != null) {
                    BigDecimal already = wo.getStockInTaskQty() != null ? wo.getStockInTaskQty() : BigDecimal.ZERO;
                    wo.setStockInTaskQty(already.add(qty));
                    BigDecimal completedQty = wo.getCompletedQty() != null ? wo.getCompletedQty() : BigDecimal.ZERO;
                    wo.setInvTaskStatus(wo.getStockInTaskQty().compareTo(completedQty) >= 0 ? "generated" : "partial");
                    workOrderMapper.updateById(wo);
                }
            }
            return existing.getId();
        }

        BizInvTask task = new BizInvTask();
        task.setTaskNo(CodeUtil.generate("INK"));
        task.setInvType("production_in");
        task.setRelatedOrderNo(woNo);
        task.setWarehouseId(defaultWarehouseId);
        task.setStatus("pending");
        task.setSourceType("work_order");
        task.setSourceId(workOrderId);
        task.setApplicantId(applicantId);
        task.setRemark("工单[" + woNo + "]成品入库任务");
        save(task);

        BizInvTaskItem taskItem = new BizInvTaskItem();
        taskItem.setTaskId(task.getId());
        taskItem.setMaterialId(productId);
        taskItem.setMaterialNo(materialNo);
        taskItem.setMaterialName(materialName);
        taskItem.setSpec(spec);
        taskItem.setUnit(unit);
        taskItem.setQuantity(qty);
        taskItem.setProcessedQty(BigDecimal.ZERO);
        taskItem.setWarehouseId(defaultWarehouseId);
        populateSecondaryUnit(taskItem, productId, qty);
        itemMapper.insert(taskItem);

        // 回写工单已生成入库任务数量
        BizWorkOrder wo = workOrderMapper.selectById(workOrderId);
        if (wo != null) {
            BigDecimal alreadyTasked = wo.getStockInTaskQty() != null ? wo.getStockInTaskQty() : BigDecimal.ZERO;
            wo.setStockInTaskQty(alreadyTasked.add(qty));
            // 更新库存任务状态
            BigDecimal completedQty = wo.getCompletedQty() != null ? wo.getCompletedQty() : BigDecimal.ZERO;
            if (wo.getStockInTaskQty().compareTo(completedQty) >= 0) {
                wo.setInvTaskStatus("generated");
            } else {
                wo.setInvTaskStatus("partial");
            }
            workOrderMapper.updateById(wo);
        }

        return task.getId();
    }

    /**
     * 采购订单批量收料 → 创建一条多物料 inv_task
     * items 每个元素包含: receiptId, receiptNo, batchNo, materialId, materialNo, materialName,
     *                      spec, unit, quantity, unitPrice, warehouseId, locationId
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createBatchReceiptsTask(Long orderId, String poNo, Long applicantId,
                                        List<Map<String, Object>> receiptItems) {
        // 取第一个非空 batchNo 作为任务级批次号（采购批量收料时各行共用一个批次）
        String taskBatchNo = receiptItems.stream()
                .map(ri -> ri.get("batchNo") != null ? ri.get("batchNo").toString().trim() : "")
                .filter(StringUtils::hasText)
                .findFirst().orElse(null);

        BizInvTask task = new BizInvTask();
        task.setTaskNo(CodeUtil.generate("INK"));
        task.setInvType("purchase_in");
        task.setRelatedOrderNo(poNo);
        task.setBatchNo(taskBatchNo);
        task.setStatus("pending");
        task.setSourceType("purchase_order");
        task.setSourceId(orderId);
        task.setApplicantId(applicantId);
        task.setRemark("采购订单[" + poNo + "]批量收料自动创建");
        save(task);

        for (Map<String, Object> ri : receiptItems) {
            BizInvTaskItem ti = new BizInvTaskItem();
            ti.setTaskId(task.getId());
            if (ri.get("receiptId") != null) ti.setReceiptId(Long.valueOf(ri.get("receiptId").toString()));
            if (ri.get("materialId") != null) ti.setMaterialId(Long.valueOf(ri.get("materialId").toString()));
            if (ri.get("materialNo") != null) ti.setMaterialNo(ri.get("materialNo").toString());
            if (ri.get("materialName") != null) ti.setMaterialName(ri.get("materialName").toString());
            if (ri.get("spec") != null) ti.setSpec(ri.get("spec").toString());
            if (ri.get("unit") != null) ti.setUnit(ri.get("unit").toString());
            if (ri.get("quantity") != null) ti.setQuantity(new BigDecimal(ri.get("quantity").toString()));
            if (ri.get("unitPrice") != null) ti.setUnitPrice(new BigDecimal(ri.get("unitPrice").toString()));
            if (ti.getQuantity() != null && ti.getUnitPrice() != null)
                ti.setTotalPrice(ti.getQuantity().multiply(ti.getUnitPrice()));
            if (ri.get("warehouseId") != null) ti.setWarehouseId(Long.valueOf(ri.get("warehouseId").toString()));
            if (ri.get("locationId") != null) ti.setLocationId(Long.valueOf(ri.get("locationId").toString()));
            ti.setProcessedQty(BigDecimal.ZERO);
            populateSecondaryUnit(ti, ti.getMaterialId(), ti.getQuantity());
            itemMapper.insert(ti);
        }

        // 根据业务配置判断是否需要自动处理
        if (!bizConfigService.getBoolValue("warehouse.purchase_in.need_process", true)) {
            tryAutoProcess(task.getId());
        }
        return task.getId();
    }

    /**
     * 销售订单发货 → 直接创建 sales_out 出库任务（跳过出库申请审批流程）
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createFromSalesOrder(Long orderId, String orderNo,
                                      Long productId, String materialNo, String materialName,
                                      String spec, String unit, BigDecimal qty, BigDecimal unitPrice,
                                      Long warehouseId, Long locationId, String batchNo,
                                      Long applicantId, String remark) {
        BizInvTask task = new BizInvTask();
        task.setTaskNo(CodeUtil.generate("INK"));
        task.setInvType("sales_out");
        task.setRelatedOrderNo(orderNo);
        task.setWarehouseId(warehouseId);
        task.setLocationId(locationId);
        task.setBatchNo(batchNo);
        task.setStatus("pending");
        task.setSourceType("sales_order");
        task.setSourceId(orderId);
        task.setApplicantId(applicantId);
        task.setRemark(remark != null ? remark : "销售订单[" + orderNo + "]发货出库任务");
        save(task);

        BizInvTaskItem taskItem = new BizInvTaskItem();
        taskItem.setTaskId(task.getId());
        taskItem.setMaterialId(productId);
        taskItem.setMaterialNo(materialNo);
        taskItem.setMaterialName(materialName);
        taskItem.setSpec(spec);
        taskItem.setUnit(unit);
        taskItem.setQuantity(qty);
        taskItem.setUnitPrice(unitPrice);
        if (unitPrice != null && qty != null) {
            taskItem.setTotalPrice(qty.multiply(unitPrice));
        }
        taskItem.setProcessedQty(BigDecimal.ZERO);
        taskItem.setWarehouseId(warehouseId);
        taskItem.setLocationId(locationId);
        populateSecondaryUnit(taskItem, productId, qty);
        itemMapper.insert(taskItem);

        // 根据业务配置判断是否需要自动处理
        if (!bizConfigService.getBoolValue("warehouse.sales_out.need_process", true)) {
            tryAutoProcess(task.getId());
        }
        return task.getId();
    }

    /**
     * 整单外协发料 → 创建 outsource_out 库存任务（多行：BOM各原材料）
     * 仅整单外协调用，工序外协保持原直接出库逻辑。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createFromOutsourceDelivery(Long outsourceOrderId, String orderNo,
                                             List<Map<String, Object>> materialItems, Long applicantId) {
        BizInvTask task = new BizInvTask();
        task.setTaskNo(CodeUtil.generate("INK"));
        task.setInvType("outsource_out");
        task.setRelatedOrderNo(orderNo);
        task.setStatus("pending");
        task.setSourceType("outsource_order");
        task.setSourceId(outsourceOrderId);
        task.setApplicantId(applicantId);
        task.setRemark("外协加工单[" + orderNo + "]发料出库任务");
        save(task);

        for (Map<String, Object> mat : materialItems) {
            BizInvTaskItem ti = new BizInvTaskItem();
            ti.setTaskId(task.getId());
            if (mat.get("materialId") != null) ti.setMaterialId(Long.valueOf(mat.get("materialId").toString()));
            if (mat.get("materialNo") != null) ti.setMaterialNo(mat.get("materialNo").toString());
            if (mat.get("materialName") != null) ti.setMaterialName(mat.get("materialName").toString());
            if (mat.get("spec") != null) ti.setSpec(mat.get("spec").toString());
            if (mat.get("unit") != null) ti.setUnit(mat.get("unit").toString());
            if (mat.get("quantity") != null) ti.setQuantity(new BigDecimal(mat.get("quantity").toString()));
            ti.setProcessedQty(BigDecimal.ZERO);
            if (mat.get("warehouseId") != null) ti.setWarehouseId(Long.valueOf(mat.get("warehouseId").toString()));
            populateSecondaryUnit(ti, ti.getMaterialId(), ti.getQuantity());
            itemMapper.insert(ti);
        }
        return task.getId();
    }

    /**
     * 整单外协收料 → 创建 outsource_in 库存任务（单行：成品）
     * 仅整单外协调用，工序外协保持原直接入库逻辑。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createFromOutsourceReceipt(Long outsourceOrderId, String orderNo,
                                            Long productId, String materialNo, String materialName,
                                            String spec, String unit, BigDecimal qty,
                                            Long warehouseId, Long applicantId) {
        // 防止重复创建（同一收料数量不重复建任务）
        BizInvTask task = new BizInvTask();
        task.setTaskNo(CodeUtil.generate("INK"));
        task.setInvType("outsource_in");
        task.setRelatedOrderNo(orderNo);
        task.setWarehouseId(warehouseId);
        task.setStatus("pending");
        task.setSourceType("outsource_order");
        task.setSourceId(outsourceOrderId);
        task.setApplicantId(applicantId);
        task.setRemark("外协加工单[" + orderNo + "]收料入库任务");
        save(task);

        BizInvTaskItem ti = new BizInvTaskItem();
        ti.setTaskId(task.getId());
        ti.setMaterialId(productId);
        ti.setMaterialNo(materialNo);
        ti.setMaterialName(materialName);
        ti.setSpec(spec);
        ti.setUnit(unit);
        ti.setQuantity(qty);
        ti.setProcessedQty(BigDecimal.ZERO);
        ti.setWarehouseId(warehouseId);
        populateSecondaryUnit(ti, productId, qty);
        itemMapper.insert(ti);
        return task.getId();
    }

    /**
     * 外协发料出库任务处理回调：更新加工单发料出库数
     */
    private void onOutsourceOutTaskProcessed(Long outsourceOrderId, BigDecimal deltaQty) {
        BizOutsourceOrder order = outsourceOrderMapper.selectById(outsourceOrderId);
        if (order == null) return;
        order.setDeliveryOutQty(
                (order.getDeliveryOutQty() != null ? order.getDeliveryOutQty() : BigDecimal.ZERO).add(deltaQty));
        outsourceOrderMapper.updateById(order);
        log.info("外协发料出库: orderId={}, delta={}, total={}", outsourceOrderId, deltaQty, order.getDeliveryOutQty());
    }

    /**
     * 外协收料入库任务处理回调（工序外协+整单外协通用）：
     * 1. 更新加工单 receiptInQty + qualifiedQty
     * 2. receiptInQty >= totalQty → status=completed
     * 3. 工序外协：调用 OutsourceChainService 处理末道/非末道逻辑
     * 4. 整单外协：更新销售订单进度
     */
    private void onOutsourceInTaskProcessed(Long outsourceOrderId, BigDecimal deltaQty, boolean allDone) {
        BizOutsourceOrder order = outsourceOrderMapper.selectById(outsourceOrderId);
        if (order == null) return;

        // 1. 更新收料入库数 + 合格数
        order.setReceiptInQty(
                (order.getReceiptInQty() != null ? order.getReceiptInQty() : BigDecimal.ZERO).add(deltaQty));
        BigDecimal totalQualified = (order.getQualifiedQty() != null ? order.getQualifiedQty() : BigDecimal.ZERO)
                .add(deltaQty);
        order.setQualifiedQty(totalQualified);

        // 2. 检查是否全部收料完成
        if (order.getReceiptInQty().compareTo(order.getTotalQty()) >= 0) {
            order.setStatus("completed");
        }
        outsourceOrderMapper.updateById(order);

        // 3. 工序外协：调用 ChainService 处理工序衔接
        if (order.getTaskId() != null) {
            try {
                outsourceChainService.onOutsourceReceiptQcPassed(outsourceOrderId, deltaQty);
            } catch (Exception e) {
                log.error("工序外协收料联动失败: outsourceOrderId={}", outsourceOrderId, e);
            }
        }
        // 4. 整单外协：更新销售订单行项进度
        else if (order.getOrderId() != null) {
            if (order.getOrderItemId() != null) {
                BizSalesOrderItem orderItem = salesOrderItemMapper.selectById(order.getOrderItemId());
                if (orderItem != null) {
                    orderItem.setProducedQty(
                            (orderItem.getProducedQty() != null ? orderItem.getProducedQty() : BigDecimal.ZERO).add(deltaQty));
                    orderItem.setWarehouseInQty(
                            (orderItem.getWarehouseInQty() != null ? orderItem.getWarehouseInQty() : BigDecimal.ZERO).add(deltaQty));
                    salesOrderItemMapper.updateById(orderItem);
                }
            }
            if ("completed".equals(order.getStatus())) {
                long unfinished = outsourceOrderMapper.selectCount(
                        new LambdaQueryWrapper<BizOutsourceOrder>()
                                .eq(BizOutsourceOrder::getOrderId, order.getOrderId())
                                .notIn(BizOutsourceOrder::getStatus, "completed", "cancelled"));
                if (unfinished == 0) {
                    BizSalesOrder salesOrder = salesOrderMapper.selectById(order.getOrderId());
                    if (salesOrder != null && "outsourcing".equals(salesOrder.getStatus())) {
                        salesOrder.setStatus("in_production");
                        salesOrderMapper.updateById(salesOrder);
                        log.info("整单外协全部完成: orderId={}", order.getOrderId());
                    }
                }
            }
        }
    }

    /**
     * 按来源批量取消 pending 状态的库存任务
     */
    public void cancelTasksBySource(String sourceType, Long sourceId) {
        List<BizInvTask> tasks = list(new LambdaQueryWrapper<BizInvTask>()
                .eq(BizInvTask::getSourceType, sourceType)
                .eq(BizInvTask::getSourceId, sourceId)
                .eq(BizInvTask::getStatus, "pending"));
        for (BizInvTask task : tasks) {
            task.setStatus("cancelled");
            task.setRemark((task.getRemark() != null ? task.getRemark() + "；" : "") + "来源单据取消");
            updateById(task);
        }
        if (!tasks.isEmpty()) {
            log.info("取消库存任务: sourceType={}, sourceId={}, count={}", sourceType, sourceId, tasks.size());
        }
    }

    /**
     * 工单列表"生成库存任务"按钮入口：直接创建 production_in 库存任务（不经过入库申请审批流）。
     * 若该工单已有未完成任务则返回已有任务 ID；否则新建。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createOrGetForWorkOrder(Long woId, Long warehouseId) {
        BizWorkOrder wo = workOrderMapper.selectById(woId);
        if (wo == null) throw new BusinessException("工单不存在");

        BizMaterial product = wo.getProductId() != null ? materialMapper.selectById(wo.getProductId()) : null;
        Long effectiveWarehouseId = warehouseId != null ? warehouseId
                : (product != null ? product.getDefaultWarehouseId() : null);

        Long taskId = createFromWorkOrder(
                wo.getId(), wo.getWoNo(), wo.getProductId(),
                product != null ? product.getMaterialNo() : null,
                product != null ? product.getMaterialName() : null,
                product != null ? product.getSpec() : null,
                product != null ? product.getUnit() : null,
                wo.getPlanQty(),
                effectiveWarehouseId,
                SecurityUtil.getCurrentUserId());

        // 更新工单库存任务状态
        BigDecimal completedQty = wo.getCompletedQty() != null ? wo.getCompletedQty() : BigDecimal.ZERO;
        BigDecimal taskQty = wo.getStockInTaskQty() != null ? wo.getStockInTaskQty() : BigDecimal.ZERO;
        productionStockInService.syncInvTaskStatus(wo, completedQty, taskQty);
        workOrderMapper.updateById(wo);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", taskId);
        return result;
    }

    /**
     * 采购入库任务退回（仅 pending 状态可退回，退回原因必填）
     * 4.1 source=inv_request → 入库申请状态改为 rejected
     * 4.2 source=purchase_order/purchase_receipt → PO invTaskProcessStatus=returned
     */
    @Transactional(rollbackFor = Exception.class)
    public void returnTask(Long id, String returnReason) {
        BizInvTask task = getById(id);
        if (task == null) throw new BusinessException("库存任务不存在");
        if ("production_return".equals(task.getInvType())) throw new BusinessException("生产退料任务不允许退回");
        if ("production_in".equals(task.getInvType())) throw new BusinessException("成品入库任务不允许退回");
        if ("purchase_in_qualified".equals(task.getInvType())) throw new BusinessException("采购入库-质检合格任务不允许退回");
        if ("purchase_in_unqualified".equals(task.getInvType())) throw new BusinessException("采购入库-质检不合格任务不允许退回");
        if ("outsource_in_qualified".equals(task.getInvType())) throw new BusinessException("外协入库-质检合格任务不允许退回");
        if ("outsource_in_unqualified".equals(task.getInvType())) throw new BusinessException("外协入库-质检不合格任务不允许退回");
        if ("scrap_in".equals(task.getInvType())) throw new BusinessException("报废入库任务不允许退回");
        // 采购入库/外协入库：来源为质检任务的不可退回
        if (("purchase_in".equals(task.getInvType()) || "outsource_in".equals(task.getInvType()))
                && "qc_task".equals(task.getSourceType())) {
            throw new BusinessException("质检生成的入库任务不允许退回");
        }
        Set<String> RETURNABLE_TYPES = Set.of("purchase_in", "production_out", "outsource_out", "outsource_in");
        if (!RETURNABLE_TYPES.contains(task.getInvType()))
            throw new BusinessException("该类型任务不支持退回");
        if (!"pending".equals(task.getStatus())) throw new BusinessException("只有待处理状态的任务才能退回");
        if (!StringUtils.hasText(returnReason)) throw new BusinessException("退回原因不能为空");

        task.setStatus("returned");
        task.setReturnReason(returnReason);
        updateById(task);

        // 生产领料任务退回：恢复BOM快照的已生成任务数
        if ("production_out".equals(task.getInvType()) && "work_order".equals(task.getSourceType())
                && task.getSourceId() != null) {
            List<BizInvTaskItem> taskItems = itemMapper.selectList(
                    new LambdaQueryWrapper<BizInvTaskItem>().eq(BizInvTaskItem::getTaskId, task.getId()));
            for (BizInvTaskItem item : taskItems) {
                if (item.getMaterialId() == null || item.getQuantity() == null) continue;
                BizWoBomSnapshot snap = bomSnapshotMapper.selectOne(
                        new LambdaQueryWrapper<BizWoBomSnapshot>()
                                .eq(BizWoBomSnapshot::getWorkOrderId, task.getSourceId())
                                .eq(BizWoBomSnapshot::getMaterialId, item.getMaterialId())
                                .last("LIMIT 1"));
                if (snap != null) {
                    BigDecimal restored = (snap.getIssuedTaskQty() != null ? snap.getIssuedTaskQty() : BigDecimal.ZERO)
                            .subtract(item.getQuantity());
                    snap.setIssuedTaskQty(restored.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : restored);
                    bomSnapshotMapper.updateById(snap);
                }
            }
            return;
        }

        // 外协发料/收料任务退回：回退外协加工单数量
        if (("outsource_out".equals(task.getInvType()) || "outsource_in".equals(task.getInvType()))
                && "outsource_order".equals(task.getSourceType()) && task.getSourceId() != null) {
            BizOutsourceOrder oo = outsourceOrderMapper.selectById(task.getSourceId());
            if (oo != null) {
                List<BizInvTaskItem> taskItems = itemMapper.selectList(
                        new LambdaQueryWrapper<BizInvTaskItem>().eq(BizInvTaskItem::getTaskId, task.getId()));
                BigDecimal taskTotalQty = taskItems.stream()
                        .map(i -> i.getQuantity() != null ? i.getQuantity() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                if ("outsource_out".equals(task.getInvType())) {
                    // 回退发料任务数和发货数量
                    oo.setDeliveryTaskQty(maxZero(
                            (oo.getDeliveryTaskQty() != null ? oo.getDeliveryTaskQty() : BigDecimal.ZERO).subtract(taskTotalQty)));
                    oo.setDeliveryQty(maxZero(
                            (oo.getDeliveryQty() != null ? oo.getDeliveryQty() : BigDecimal.ZERO).subtract(taskTotalQty)));
                } else {
                    // 回退收料任务数和收料数量
                    oo.setReceiptTaskQty(maxZero(
                            (oo.getReceiptTaskQty() != null ? oo.getReceiptTaskQty() : BigDecimal.ZERO).subtract(taskTotalQty)));
                    oo.setDeliveredQty(maxZero(
                            (oo.getDeliveredQty() != null ? oo.getDeliveredQty() : BigDecimal.ZERO).subtract(taskTotalQty)));
                }
                outsourceOrderMapper.updateById(oo);
                log.info("外协任务退回，回退加工单数量: outsourceOrderId={}, invType={}, qty={}", oo.getId(), task.getInvType(), taskTotalQty);
            }
            return;
        }

        if ("inv_request".equals(task.getSourceType()) && task.getSourceId() != null) {
            BizInvRequest request = invRequestMapper.selectById(task.getSourceId());
            if (request != null) {
                request.setStatus("rejected");
                request.setRemark(returnReason);
                invRequestMapper.updateById(request);
            }
        } else if ("purchase_receipt".equals(task.getSourceType()) && task.getSourceId() != null) {
            // 新流程：退回库存任务 → 通知收料单服务
            Long receiptId = task.getSourceId();
            List<BizInvTaskItem> taskItems = itemMapper.selectList(
                    new LambdaQueryWrapper<BizInvTaskItem>().eq(BizInvTaskItem::getTaskId, task.getId()));
            BigDecimal taskTotalQty = taskItems.stream()
                    .map(i -> i.getQuantity() != null ? i.getQuantity() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            try {
                com.aimes.modules.purchase.service.PurchaseReceiptService receiptService =
                        applicationContext.getBean(com.aimes.modules.purchase.service.PurchaseReceiptService.class);
                receiptService.onInvTaskReturn(receiptId, taskTotalQty);
            } catch (Exception e) {
                log.warn("退回库存任务更新收料单失败: receiptId={}", receiptId, e);
            }
        } else if ("outsource_receipt".equals(task.getSourceType()) && task.getSourceId() != null) {
            // 外协收料单退回
            Long outsourceReceiptId = task.getSourceId();
            List<BizInvTaskItem> taskItems2 = itemMapper.selectList(
                    new LambdaQueryWrapper<BizInvTaskItem>().eq(BizInvTaskItem::getTaskId, task.getId()));
            BigDecimal taskTotalQty2 = taskItems2.stream()
                    .map(i -> i.getQuantity() != null ? i.getQuantity() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            try {
                com.aimes.modules.outsource.service.OutsourceReceiptService outsourceReceiptService =
                        applicationContext.getBean(com.aimes.modules.outsource.service.OutsourceReceiptService.class);
                outsourceReceiptService.onInvTaskReturn(outsourceReceiptId, taskTotalQty2);
            } catch (Exception e) {
                log.warn("退回库存任务更新外协收料单失败: receiptId={}", outsourceReceiptId, e);
            }
        } else if ("purchase_order".equals(task.getSourceType()) && task.getSourceId() != null) {
            // 旧流程兼容
            Long orderId = task.getSourceId();
            List<BizInvTaskItem> taskItems = itemMapper.selectList(
                    new LambdaQueryWrapper<BizInvTaskItem>().eq(BizInvTaskItem::getTaskId, task.getId()));
            for (BizInvTaskItem item : taskItems) {
                if (item.getReceiptId() == null) continue;
                BizPurchaseReceipt receipt = purchaseReceiptMapper.selectById(item.getReceiptId());
                if (receipt == null || receipt.getOrderItemId() == null) continue;
                BizPurchaseOrderItem oi = purchaseOrderItemMapper.selectById(receipt.getOrderItemId());
                if (oi == null) continue;
                BigDecimal current = oi.getInvTaskQty() != null ? oi.getInvTaskQty() : BigDecimal.ZERO;
                BigDecimal restored = current.subtract(item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO);
                oi.setInvTaskQty(restored.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : restored);
                purchaseOrderItemMapper.updateById(oi);
            }
            updateInvTaskGenStatus(orderId);
        }
    }

    /**
     * 从库存任务获取关联的采购订单ID
     */
    private BigDecimal maxZero(BigDecimal val) {
        return val.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : val;
    }

    private Long getOrderIdFromTask(BizInvTask task) {
        if ("purchase_order".equals(task.getSourceType())) {
            return task.getSourceId();
        } else if ("purchase_receipt".equals(task.getSourceType())) {
            BizPurchaseReceipt receipt = purchaseReceiptMapper.selectById(task.getSourceId());
            return receipt != null ? receipt.getPurchaseOrderId() : null;
        }
        return null;
    }

    /**
     * 更新采购订单的库存任务处理状态（根据所有明细的 stockInQty vs invTaskQty）
     */
    private void updatePOInvTaskProcessStatus(Long orderId) {
        List<BizPurchaseOrderItem> items = purchaseOrderItemMapper.selectList(
                new LambdaQueryWrapper<BizPurchaseOrderItem>()
                        .eq(BizPurchaseOrderItem::getPurchaseOrderId, orderId));
        if (items.isEmpty()) return;

        BigDecimal totalInvTaskQty = items.stream()
                .map(i -> i.getInvTaskQty() != null ? i.getInvTaskQty() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalStockInQty = items.stream()
                .map(i -> i.getStockInQty() != null ? i.getStockInQty() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BizPurchaseOrder order = purchaseOrderMapper.selectById(orderId);
        if (order == null || "closed".equals(order.getStatus())) return;

        if (totalStockInQty.compareTo(BigDecimal.ZERO) <= 0) {
            order.setInvTaskProcessStatus(null);
        } else if (totalInvTaskQty.compareTo(BigDecimal.ZERO) > 0 && totalStockInQty.compareTo(totalInvTaskQty) >= 0) {
            order.setInvTaskProcessStatus("fully_processed");
        } else {
            order.setInvTaskProcessStatus("partial_processed");
        }
        purchaseOrderMapper.updateById(order);
    }

    /**
     * 更新采购订单的库存任务生成状态（根据所有明细的 invTaskQty vs quantity 计算）
     */
    private void updateInvTaskGenStatus(Long orderId) {
        List<BizPurchaseOrderItem> items = purchaseOrderItemMapper.selectList(
                new LambdaQueryWrapper<BizPurchaseOrderItem>()
                        .eq(BizPurchaseOrderItem::getPurchaseOrderId, orderId));
        if (items.isEmpty()) return;

        BigDecimal totalQty = items.stream()
                .map(i -> i.getQuantity() != null ? i.getQuantity() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalInvTaskQty = items.stream()
                .map(i -> i.getInvTaskQty() != null ? i.getInvTaskQty() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BizPurchaseOrder order = purchaseOrderMapper.selectById(orderId);
        if (order == null) return;

        if (totalInvTaskQty.compareTo(BigDecimal.ZERO) <= 0) {
            order.setInvTaskGenStatus(null);
        } else if (totalInvTaskQty.compareTo(totalQty) >= 0) {
            order.setInvTaskGenStatus("fully_generated");
        } else {
            order.setInvTaskGenStatus("partial_generated");
        }
        purchaseOrderMapper.updateById(order);
    }

    /**
     * 查询采购订单关联的所有库存任务（来源于 purchase_order 或 purchase_receipt）
     */
    public List<Map<String, Object>> getByPurchaseOrder(Long orderId) {
        List<BizInvTask> allTasks = new ArrayList<>(list(new LambdaQueryWrapper<BizInvTask>()
                .eq(BizInvTask::getSourceType, "purchase_order")
                .eq(BizInvTask::getSourceId, orderId)));

        List<BizPurchaseReceipt> receipts = purchaseReceiptMapper.selectList(
                new LambdaQueryWrapper<BizPurchaseReceipt>()
                        .eq(BizPurchaseReceipt::getPurchaseOrderId, orderId));
        if (!receipts.isEmpty()) {
            Set<Long> receiptIds = receipts.stream().map(BizPurchaseReceipt::getId).collect(Collectors.toSet());
            Set<Long> existingIds = allTasks.stream().map(BizInvTask::getId).collect(Collectors.toSet());
            list(new LambdaQueryWrapper<BizInvTask>()
                    .eq(BizInvTask::getSourceType, "purchase_receipt")
                    .in(BizInvTask::getSourceId, receiptIds))
                    .stream().filter(t -> !existingIds.contains(t.getId()))
                    .forEach(allTasks::add);
        }

        if (allTasks.isEmpty()) return Collections.emptyList();

        Set<Long> taskIds = allTasks.stream().map(BizInvTask::getId).collect(Collectors.toSet());
        Map<Long, List<BizInvTaskItem>> itemsByTask = itemMapper.selectList(
                new LambdaQueryWrapper<BizInvTaskItem>().in(BizInvTaskItem::getTaskId, taskIds))
                .stream().collect(Collectors.groupingBy(BizInvTaskItem::getTaskId));

        return allTasks.stream()
                .sorted(Comparator.comparing(BizInvTask::getCreateTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(task -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", task.getId());
                    m.put("taskNo", task.getTaskNo());
                    m.put("status", task.getStatus());
                    m.put("returnReason", task.getReturnReason());
                    m.put("batchNo", task.getBatchNo());
                    m.put("createTime", task.getCreateTime());
                    List<BizInvTaskItem> items = itemsByTask.getOrDefault(task.getId(), Collections.emptyList());
                    String summary = items.stream()
                            .map(i -> (i.getMaterialName() != null ? i.getMaterialName()
                                    : (i.getMaterialNo() != null ? i.getMaterialNo() : ""))
                                    + " ×" + (i.getQuantity() != null ? i.getQuantity().stripTrailingZeros().toPlainString() : "0"))
                            .collect(Collectors.joining("、"));
                    m.put("materialSummary", summary);
                    return m;
                }).collect(Collectors.toList());
    }

    /**
     * 查询工单关联的所有库存任务（领料production_out、退料production_return、成品入库production_in）
     */
    public List<Map<String, Object>> getByWorkOrder(Long woId) {
        // 查工单级任务 (production_in / production_out / production_return) + 工序级任务 (semi_in / 领料)
        List<Long> processTaskIds = woProcessTaskMapper.selectList(
                new LambdaQueryWrapper<com.aimes.modules.production.entity.BizWoProcessTask>()
                        .eq(com.aimes.modules.production.entity.BizWoProcessTask::getWorkOrderId, woId)
                        .eq(com.aimes.modules.production.entity.BizWoProcessTask::getDeleted, 0))
                .stream().map(com.aimes.modules.production.entity.BizWoProcessTask::getId).collect(Collectors.toList());
        List<BizInvTask> tasks = list(new LambdaQueryWrapper<BizInvTask>()
                .and(wq -> {
                    wq.or(o -> o.eq(BizInvTask::getSourceType, "work_order").eq(BizInvTask::getSourceId, woId));
                    if (!processTaskIds.isEmpty()) {
                        wq.or(o -> o.in(BizInvTask::getSourceType,
                                Arrays.asList("wo_process_task", "wo_process_task_issue"))
                                .in(BizInvTask::getSourceId, processTaskIds));
                    }
                })
                .orderByDesc(BizInvTask::getCreateTime));
        if (tasks.isEmpty()) return Collections.emptyList();

        Set<Long> taskIds = tasks.stream().map(BizInvTask::getId).collect(Collectors.toSet());
        Map<Long, List<BizInvTaskItem>> itemsByTask = itemMapper.selectList(
                new LambdaQueryWrapper<BizInvTaskItem>().in(BizInvTaskItem::getTaskId, taskIds))
                .stream().collect(Collectors.groupingBy(BizInvTaskItem::getTaskId));

        // 批量加载仓库名称
        Set<Long> whIds = tasks.stream().map(BizInvTask::getWarehouseId).filter(Objects::nonNull).collect(Collectors.toSet());
        itemsByTask.values().forEach(items -> items.forEach(i -> { if (i.getWarehouseId() != null) whIds.add(i.getWarehouseId()); }));
        Map<Long, String> whNameMap = whIds.isEmpty() ? Map.of() :
                warehouseMapper.selectBatchIds(whIds).stream()
                        .collect(Collectors.toMap(w -> w.getId(), w -> w.getWarehouseName(), (a, b) -> a));

        return tasks.stream().map(task -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", task.getId());
            m.put("taskNo", task.getTaskNo());
            m.put("invType", task.getInvType());
            m.put("status", task.getStatus());
            m.put("batchNo", task.getBatchNo());
            m.put("relatedOrderNo", task.getRelatedOrderNo());
            m.put("warehouseId", task.getWarehouseId());
            m.put("warehouseName", task.getWarehouseId() != null ? whNameMap.get(task.getWarehouseId()) : null);
            m.put("remark", task.getRemark());
            m.put("createTime", task.getCreateTime());
            List<BizInvTaskItem> items = itemsByTask.getOrDefault(task.getId(), Collections.emptyList());
            List<Map<String, Object>> itemList = items.stream().map(i -> {
                Map<String, Object> im = new LinkedHashMap<>();
                im.put("materialName", i.getMaterialName());
                im.put("materialNo", i.getMaterialNo());
                im.put("spec", i.getSpec());
                im.put("unit", i.getUnit());
                im.put("quantity", i.getQuantity());
                im.put("processedQty", i.getProcessedQty());
                im.put("warehouseId", i.getWarehouseId());
                im.put("warehouseName", i.getWarehouseId() != null ? whNameMap.get(i.getWarehouseId()) : null);
                return im;
            }).collect(Collectors.toList());
            m.put("items", itemList);
            String summary = items.stream()
                    .map(i -> (i.getMaterialName() != null ? i.getMaterialName() : "")
                            + " ×" + (i.getQuantity() != null ? i.getQuantity().stripTrailingZeros().toPlainString() : "0"))
                    .collect(Collectors.joining("、"));
            m.put("materialSummary", summary);
            return m;
        }).collect(Collectors.toList());
    }

    /**
     * 采购退货单 → 创建出库任务（purchase_return，不可退回）
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createFromPurchaseReturn(Long returnId, String returnNo, String poNo,
                                          Long warehouseId, Long applicantId,
                                          List<Map<String, Object>> items) {
        // 防止重复创建
        long existing = count(new LambdaQueryWrapper<BizInvTask>()
                .eq(BizInvTask::getSourceType, "purchase_return")
                .eq(BizInvTask::getSourceId, returnId)
                .ne(BizInvTask::getStatus, "returned"));
        if (existing > 0) throw new BusinessException("该退货单已生成库存任务，不可重复生成");

        BizInvTask task = new BizInvTask();
        task.setTaskNo(CodeUtil.generate("INK"));
        task.setInvType("purchase_return");
        task.setRelatedOrderNo(StringUtils.hasText(poNo) ? poNo : returnNo);
        task.setWarehouseId(warehouseId);
        task.setStatus("pending");
        task.setSourceType("purchase_return");
        task.setSourceId(returnId);
        task.setApplicantId(applicantId);
        task.setRemark("采购退货单[" + returnNo + "]退货出库任务");
        save(task);

        for (Map<String, Object> item : items) {
            BizInvTaskItem taskItem = new BizInvTaskItem();
            taskItem.setTaskId(task.getId());
            if (item.get("materialId") != null) taskItem.setMaterialId(Long.valueOf(item.get("materialId").toString()));
            if (item.get("materialNo") != null) taskItem.setMaterialNo(item.get("materialNo").toString());
            if (item.get("materialName") != null) taskItem.setMaterialName(item.get("materialName").toString());
            if (item.get("spec") != null) taskItem.setSpec(item.get("spec").toString());
            if (item.get("unit") != null) taskItem.setUnit(item.get("unit").toString());
            if (item.get("quantity") != null) taskItem.setQuantity(new BigDecimal(item.get("quantity").toString()));
            if (item.get("unitPrice") != null) taskItem.setUnitPrice(new BigDecimal(item.get("unitPrice").toString()));
            if (item.get("warehouseId") != null) taskItem.setWarehouseId(Long.valueOf(item.get("warehouseId").toString()));
            taskItem.setProcessedQty(BigDecimal.ZERO);
            populateSecondaryUnit(taskItem, taskItem.getMaterialId(), taskItem.getQuantity());
            itemMapper.insert(taskItem);
        }
        return task.getId();
    }

    /**
     * 工单领料 → 创建 production_out 出库任务（多物料行）
     * items 每个元素包含: materialId, materialNo, materialName, spec, unit, quantity, warehouseId
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createFromWorkOrderIssue(Long woId, String woNo, List<Map<String, Object>> items, Long applicantId) {
        BizInvTask task = new BizInvTask();
        task.setTaskNo(CodeUtil.generate("INK"));
        task.setInvType("production_out");
        task.setRelatedOrderNo(woNo);
        task.setStatus("pending");
        task.setSourceType("work_order");
        task.setSourceId(woId);
        task.setApplicantId(applicantId);
        task.setRemark("工单[" + woNo + "]生产领料出库任务");
        save(task);

        for (Map<String, Object> item : items) {
            BizInvTaskItem ti = new BizInvTaskItem();
            ti.setTaskId(task.getId());
            if (item.get("materialId") != null) ti.setMaterialId(Long.valueOf(item.get("materialId").toString()));
            if (item.get("materialNo") != null) ti.setMaterialNo(item.get("materialNo").toString());
            if (item.get("materialName") != null) ti.setMaterialName(item.get("materialName").toString());
            if (item.get("spec") != null) ti.setSpec(item.get("spec").toString());
            if (item.get("unit") != null) ti.setUnit(item.get("unit").toString());
            if (item.get("quantity") != null) ti.setQuantity(new BigDecimal(item.get("quantity").toString()));
            if (item.get("warehouseId") != null) ti.setWarehouseId(Long.valueOf(item.get("warehouseId").toString()));
            if (item.get("substituteForMaterialId") != null) {
                ti.setSubstituteForMaterialId(Long.valueOf(item.get("substituteForMaterialId").toString()));
            }
            if (item.get("substituteRatio") != null) {
                ti.setSubstituteRatio(new BigDecimal(item.get("substituteRatio").toString()));
            }
            ti.setProcessedQty(BigDecimal.ZERO);
            populateSecondaryUnit(ti, ti.getMaterialId(), ti.getQuantity());
            itemMapper.insert(ti);
        }
        return task.getId();
    }

    /**
     * 工单退料 → 创建 production_return 入库任务（多物料行）
     * items 每个元素包含: materialId, materialNo, materialName, spec, unit, quantity, warehouseId
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createFromWorkOrderReturn(Long woId, String woNo, List<Map<String, Object>> items, Long applicantId) {
        BizInvTask task = new BizInvTask();
        task.setTaskNo(CodeUtil.generate("INK"));
        task.setInvType("production_return");
        task.setRelatedOrderNo(woNo);
        task.setStatus("pending");
        task.setSourceType("work_order");
        task.setSourceId(woId);
        task.setApplicantId(applicantId);
        task.setRemark("工单[" + woNo + "]生产退料入库任务");
        save(task);

        for (Map<String, Object> item : items) {
            BizInvTaskItem ti = new BizInvTaskItem();
            ti.setTaskId(task.getId());
            if (item.get("materialId") != null) ti.setMaterialId(Long.valueOf(item.get("materialId").toString()));
            if (item.get("materialNo") != null) ti.setMaterialNo(item.get("materialNo").toString());
            if (item.get("materialName") != null) ti.setMaterialName(item.get("materialName").toString());
            if (item.get("spec") != null) ti.setSpec(item.get("spec").toString());
            if (item.get("unit") != null) ti.setUnit(item.get("unit").toString());
            if (item.get("quantity") != null) ti.setQuantity(new BigDecimal(item.get("quantity").toString()));
            if (item.get("warehouseId") != null) ti.setWarehouseId(Long.valueOf(item.get("warehouseId").toString()));
            ti.setProcessedQty(BigDecimal.ZERO);
            populateSecondaryUnit(ti, ti.getMaterialId(), ti.getQuantity());
            itemMapper.insert(ti);
        }
        return task.getId();
    }

    /**
     * 检查采购订单是否满足自动办结条件：全部生成 + 全部处理 → 自动办结
     */
    private void checkAndAutoClose(Long orderId) {
        BizPurchaseOrder order = purchaseOrderMapper.selectById(orderId);
        if (order == null || !"confirmed".equals(order.getStatus())) return;
        if ("fully_generated".equals(order.getInvTaskGenStatus())
                && "fully_processed".equals(order.getInvTaskProcessStatus())) {
            order.setStatus("closed");
            purchaseOrderMapper.updateById(order);
        }
    }
}
