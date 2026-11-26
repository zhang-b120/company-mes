package com.aimes.modules.inventory.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.PageResult;
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
import com.aimes.modules.master.entity.BizMaterial;
import com.aimes.modules.master.mapper.BizMaterialMapper;
import com.aimes.modules.system.entity.SysUser;
import com.aimes.modules.system.entity.SysWarehouse;
import com.aimes.modules.system.mapper.SysUserMapper;
import com.aimes.modules.system.mapper.SysWarehouseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BizInvHistoryService extends ServiceImpl<BizInvHistoryMapper, BizInvHistory> {

    private final BizInvHistoryItemMapper itemMapper;
    private final BizInvTaskMapper taskMapper;
    private final BizInvTaskItemMapper taskItemMapper;
    private final BizInvRequestMapper invRequestMapper;
    private final SysWarehouseMapper warehouseMapper;
    private final SysUserMapper userMapper;
    private final InventoryService inventoryService;
    private final BizMaterialMapper materialMapper;

    private static final Set<String> INBOUND_TYPES = Set.of(
            "purchase_in", "production_in", "semi_in", "outsource_in", "return_in", "other_in", "transfer_in", "stocktaking_in");

    /**
     * 以物料角度分页查询出入库历史
     * 每行 = biz_inv_history_item 一条明细，关联 biz_inv_history 头部信息
     */
    private static final List<String> IN_TYPES = List.of(
            "purchase_in", "production_in", "semi_in", "outsource_in", "return_in", "other_in");
    private static final List<String> OUT_TYPES = List.of(
            "production_out", "sales_out", "outsource_out", "scrap_out", "other_out");

    // 旧签名(无 categoryCode), 保持兼容
    public PageResult<Map<String, Object>> getPage(String historyNo, String taskNo, String relatedOrderNo,
                                                    String invType, String direction, String status, Long materialId,
                                                    String materialNo, String materialName, String spec,
                                                    String dateStart, String dateEnd,
                                                    int pageNum, int pageSize) {
        return getPage(historyNo, taskNo, relatedOrderNo, invType, direction, status, materialId,
                materialNo, materialName, spec, null, dateStart, dateEnd, pageNum, pageSize);
    }

    public PageResult<Map<String, Object>> getPage(String historyNo, String taskNo, String relatedOrderNo,
                                                    String invType, String direction, String status, Long materialId,
                                                    String materialNo, String materialName, String spec,
                                                    String categoryCode,
                                                    String dateStart, String dateEnd,
                                                    int pageNum, int pageSize) {
        // Step1: 通过头部条件过滤出符合的 history_id 集合
        List<String> dirTypes = null;
        if ("in".equals(direction)) dirTypes = IN_TYPES;
        else if ("out".equals(direction)) dirTypes = OUT_TYPES;

        boolean hasHeaderFilter = StringUtils.hasText(historyNo) || StringUtils.hasText(taskNo)
                || StringUtils.hasText(relatedOrderNo) || StringUtils.hasText(invType)
                || dirTypes != null
                || StringUtils.hasText(dateStart) || StringUtils.hasText(dateEnd);
        Set<Long> headerHistoryIds = null;
        if (hasHeaderFilter) {
            LambdaQueryWrapper<BizInvHistory> hw = new LambdaQueryWrapper<BizInvHistory>()
                    .like(StringUtils.hasText(historyNo), BizInvHistory::getHistoryNo, historyNo)
                    .like(StringUtils.hasText(taskNo), BizInvHistory::getTaskNo, taskNo)
                    .like(StringUtils.hasText(relatedOrderNo), BizInvHistory::getRelatedOrderNo, relatedOrderNo)
                    .eq(StringUtils.hasText(invType), BizInvHistory::getInvType, invType)
                    .ge(StringUtils.hasText(dateStart), BizInvHistory::getCreateTime, dateStart + " 00:00:00")
                    .le(StringUtils.hasText(dateEnd), BizInvHistory::getCreateTime, dateEnd + " 23:59:59")
                    .select(BizInvHistory::getId);
            if (dirTypes != null) hw.in(BizInvHistory::getInvType, dirTypes);
            List<BizInvHistory> matched = list(hw);
            if (matched.isEmpty()) return new PageResult<>(List.of(), 0L, pageNum, pageSize);
            headerHistoryIds = matched.stream().map(BizInvHistory::getId).collect(Collectors.toSet());
        }

        // 按 category 过滤: 先查物料表得到匹配 ID 集合, 再 in 过滤 item
        Set<Long> categoryMatIds = null;
        if (StringUtils.hasText(categoryCode)) {
            List<BizMaterial> mats = materialMapper.selectList(
                    new LambdaQueryWrapper<BizMaterial>()
                            .eq(BizMaterial::getCategory, categoryCode)
                            .select(BizMaterial::getId));
            if (mats.isEmpty()) return new PageResult<>(List.of(), 0L, pageNum, pageSize);
            categoryMatIds = mats.stream().map(BizMaterial::getId).collect(Collectors.toSet());
        }

        // Step2: 在 history_item 表上构建物料级查询（直接分页）
        // status 过滤在物料行级别进行（per-item revoke）
        // 按 history_id desc 排序,对齐 history.createTime desc(要求 history 插入时按时间升序)
        LambdaQueryWrapper<BizInvHistoryItem> itemWrapper = new LambdaQueryWrapper<BizInvHistoryItem>()
                .eq(materialId != null, BizInvHistoryItem::getMaterialId, materialId)
                .like(StringUtils.hasText(materialNo), BizInvHistoryItem::getMaterialNo, materialNo)
                .like(StringUtils.hasText(materialName), BizInvHistoryItem::getMaterialName, materialName)
                .eq(StringUtils.hasText(status), BizInvHistoryItem::getStatus, status)
                .orderByDesc(BizInvHistoryItem::getHistoryId)
                .orderByDesc(BizInvHistoryItem::getId);
        // spec 支持单值与复合输入(按 token 拆分,AND 命中)
        com.aimes.common.utils.SpecQueryUtil.apply(itemWrapper, BizInvHistoryItem::getSpec, spec);
        if (headerHistoryIds != null) {
            itemWrapper.in(BizInvHistoryItem::getHistoryId, headerHistoryIds);
        }
        if (categoryMatIds != null) {
            itemWrapper.in(BizInvHistoryItem::getMaterialId, categoryMatIds);
        }
        Page<BizInvHistoryItem> itemPage = itemMapper.selectPage(new Page<>(pageNum, pageSize), itemWrapper);
        List<BizInvHistoryItem> items = itemPage.getRecords();
        if (items.isEmpty()) return new PageResult<>(List.of(), 0L, pageNum, pageSize);

        // Step3: 批量加载关联的 history 头部
        Set<Long> historyIds = items.stream().map(BizInvHistoryItem::getHistoryId).collect(Collectors.toSet());
        Map<Long, BizInvHistory> historyMap = listByIds(historyIds).stream()
                .collect(Collectors.toMap(BizInvHistory::getId, h -> h, (a, b) -> a));

        // Step4: 批量加载仓库/操作人
        Set<Long> allWhIds = new HashSet<>();
        items.forEach(i -> {
            if (i.getWarehouseId() != null) allWhIds.add(i.getWarehouseId());
            if (i.getLocationId() != null) allWhIds.add(i.getLocationId());
        });
        historyMap.values().forEach(h -> {
            if (h.getWarehouseId() != null) allWhIds.add(h.getWarehouseId());
            if (h.getLocationId() != null) allWhIds.add(h.getLocationId());
        });
        Map<Long, String> whNameMap = allWhIds.isEmpty() ? new HashMap<>() :
                warehouseMapper.selectBatchIds(allWhIds).stream()
                        .collect(Collectors.toMap(SysWarehouse::getId, SysWarehouse::getWarehouseName, (a, b) -> a));

        // 批量加载所有 history.taskId 对应的 task (给 relatedOrderNo / applicantId 兜底用,
        // 老数据 history.related_order_no / applicant_id 字段可能为空)
        Set<Long> taskIds = historyMap.values().stream()
                .map(BizInvHistory::getTaskId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, BizInvTask> taskMapForApplicant = taskIds.isEmpty() ? new HashMap<>() :
                taskMapper.selectBatchIds(taskIds).stream()
                        .collect(Collectors.toMap(BizInvTask::getId, t -> t, (a, b) -> a));

        Set<Long> opIds = new HashSet<>();
        historyMap.values().stream().map(BizInvHistory::getOperatorId).filter(Objects::nonNull).forEach(opIds::add);
        historyMap.values().stream().map(BizInvHistory::getApplicantId).filter(Objects::nonNull).forEach(opIds::add);
        taskMapForApplicant.values().stream().map(BizInvTask::getApplicantId).filter(Objects::nonNull).forEach(opIds::add);
        Map<Long, String> userNameMap = opIds.isEmpty() ? new HashMap<>() :
                userMapper.selectBatchIds(opIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, SysUser::getRealName, (a, b) -> a));

        // Step4b: 对 return_in 类型，通过 task.sourceId 查退货客户名
        Map<Long, String> customerNameByHistoryId = new HashMap<>();
        Set<Long> returnInTaskIds = historyMap.values().stream()
                .filter(h -> "return_in".equals(h.getInvType()) && h.getTaskId() != null)
                .map(BizInvHistory::getTaskId).collect(Collectors.toSet());
        if (!returnInTaskIds.isEmpty()) {
            taskMapper.selectBatchIds(returnInTaskIds).forEach(t -> {
                if ("inv_request".equals(t.getSourceType()) && t.getSourceId() != null) {
                    BizInvRequest req = invRequestMapper.selectById(t.getSourceId());
                    if (req != null && req.getCustomerName() != null) {
                        historyMap.values().stream()
                                .filter(h -> t.getId().equals(h.getTaskId()))
                                .forEach(h -> customerNameByHistoryId.put(h.getId(), req.getCustomerName()));
                    }
                }
            });
        }

        // Step5: 组装结果（每行 = 一条物料明细 + 头部信息）
        List<Map<String, Object>> list = items.stream().map(i -> {
            BizInvHistory h = historyMap.get(i.getHistoryId());
            // 仓库/库位：优先 item 级，回退到 history 级
            Long itemWhId = i.getWarehouseId() != null ? i.getWarehouseId() : (h != null ? h.getWarehouseId() : null);
            Long itemLocId = i.getLocationId() != null ? i.getLocationId() : (h != null ? h.getLocationId() : null);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", i.getId());          // item id（本行唯一标识）
            m.put("historyId", i.getHistoryId()); // 用于撤销/详情
            m.put("historyNo", h != null ? h.getHistoryNo() : "");
            m.put("taskNo", h != null ? h.getTaskNo() : "");
            // relatedOrderNo: 优先 history 自己, 为空时回查 task
            String relOrderNo = h != null ? h.getRelatedOrderNo() : "";
            if ((relOrderNo == null || relOrderNo.isEmpty()) && h != null && h.getTaskId() != null) {
                BizInvTask relTask = taskMapForApplicant.get(h.getTaskId());
                if (relTask != null && relTask.getRelatedOrderNo() != null) relOrderNo = relTask.getRelatedOrderNo();
            }
            m.put("relatedOrderNo", relOrderNo);
            m.put("invType", h != null ? h.getInvType() : "");
            m.put("batchNo", h != null ? h.getBatchNo() : "");
            m.put("customerName", customerNameByHistoryId.getOrDefault(i.getHistoryId(), ""));
            m.put("materialId", i.getMaterialId());
            m.put("materialNo", i.getMaterialNo());
            m.put("materialName", i.getMaterialName());
            m.put("spec", i.getSpec());
            m.put("unit", i.getUnit());
            m.put("quantity", i.getQuantity());
            m.put("unitPrice", i.getUnitPrice());
            m.put("totalPrice", i.getTotalPrice());
            m.put("secondaryUnit", i.getSecondaryUnit());
            m.put("secondaryQty", i.getSecondaryQty());
            m.put("warehouseId", itemWhId);
            m.put("warehouseName", whNameMap.getOrDefault(itemWhId, ""));
            m.put("locationId", itemLocId);
            m.put("locationName", whNameMap.getOrDefault(itemLocId, ""));
            m.put("status", i.getStatus() != null ? i.getStatus() : "normal"); // 物料行状态
            m.put("operatorId", h != null ? h.getOperatorId() : null);
            m.put("operatorName", h != null ? userNameMap.getOrDefault(h.getOperatorId(), "") : "");
            // 申请人：优先取 history.applicantId（新数据），旧数据回退到关联任务的 applicantId
            Long applicantId = h != null ? h.getApplicantId() : null;
            if (applicantId == null && h != null && h.getTaskId() != null) {
                BizInvTask relTask = taskMapForApplicant.get(h.getTaskId());
                if (relTask != null) applicantId = relTask.getApplicantId();
            }
            m.put("applicantId", applicantId);
            m.put("applicantName", applicantId != null ? userNameMap.getOrDefault(applicantId, "") : "");
            m.put("createTime", h != null ? h.getCreateTime() : null);
            return m;
        }).collect(Collectors.toList());

        return new PageResult<>(list, itemPage.getTotal(), pageNum, pageSize);
    }

    public Map<String, Object> getDetail(Long id) {
        BizInvHistory history = getById(id);
        if (history == null) throw new BusinessException("出入库历史不存在");

        List<BizInvHistoryItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<BizInvHistoryItem>().eq(BizInvHistoryItem::getHistoryId, id));

        // 收集主表+明细中的仓库/库位id
        Set<Long> whIds = new HashSet<>();
        if (history.getWarehouseId() != null) whIds.add(history.getWarehouseId());
        if (history.getLocationId() != null) whIds.add(history.getLocationId());
        items.forEach(i -> {
            if (i.getWarehouseId() != null) whIds.add(i.getWarehouseId());
            if (i.getLocationId() != null) whIds.add(i.getLocationId());
        });
        Map<Long, String> whNameMap = whIds.isEmpty() ? new HashMap<>() :
                warehouseMapper.selectBatchIds(whIds).stream()
                        .collect(Collectors.toMap(SysWarehouse::getId, SysWarehouse::getWarehouseName, (a, b) -> a));

        String operatorName = "";
        if (history.getOperatorId() != null) {
            SysUser u = userMapper.selectById(history.getOperatorId());
            if (u != null) operatorName = u.getRealName();
        }

        // 申请人：优先从 history.applicantId 直接取，旧数据回退到关联任务的 applicantId
        String customerName = "";
        String applicantName = "";
        Long applicantId = history.getApplicantId();
        if (applicantId != null) {
            SysUser applicant = userMapper.selectById(applicantId);
            if (applicant != null) applicantName = applicant.getRealName();
        }
        if (history.getTaskId() != null) {
            BizInvTask task = taskMapper.selectById(history.getTaskId());
            if (task != null) {
                if (applicantId == null && task.getApplicantId() != null) {
                    SysUser applicant = userMapper.selectById(task.getApplicantId());
                    if (applicant != null) applicantName = applicant.getRealName();
                }
                if ("return_in".equals(history.getInvType()) && "inv_request".equals(task.getSourceType()) && task.getSourceId() != null) {
                    BizInvRequest req = invRequestMapper.selectById(task.getSourceId());
                    if (req != null && req.getCustomerName() != null) customerName = req.getCustomerName();
                }
            }
        }

        // 明细转 map，附带 warehouseName/locationName
        List<Map<String, Object>> itemMaps = items.stream().map(i -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", i.getId());
            m.put("materialId", i.getMaterialId());
            m.put("materialNo", i.getMaterialNo());
            m.put("materialName", i.getMaterialName());
            m.put("spec", i.getSpec());
            m.put("unit", i.getUnit());
            m.put("quantity", i.getQuantity());
            m.put("unitPrice", i.getUnitPrice());
            m.put("totalPrice", i.getTotalPrice());
            m.put("warehouseId", i.getWarehouseId());
            m.put("warehouseName", whNameMap.getOrDefault(i.getWarehouseId(), ""));
            m.put("locationId", i.getLocationId());
            m.put("locationName", whNameMap.getOrDefault(i.getLocationId(), ""));
            m.put("status", i.getStatus() != null ? i.getStatus() : "normal");
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", history.getId());
        detail.put("historyNo", history.getHistoryNo());
        detail.put("taskNo", history.getTaskNo());
        detail.put("relatedOrderNo", history.getRelatedOrderNo());
        detail.put("invType", history.getInvType());
        detail.put("customerName", customerName);
        detail.put("batchNo", history.getBatchNo());
        detail.put("warehouseId", history.getWarehouseId());
        detail.put("warehouseName", whNameMap.getOrDefault(history.getWarehouseId(), ""));
        detail.put("locationId", history.getLocationId());
        detail.put("locationName", whNameMap.getOrDefault(history.getLocationId(), ""));
        detail.put("status", history.getStatus());
        detail.put("operatorName", operatorName);
        detail.put("applicantName", applicantName);
        detail.put("remark", history.getRemark());
        detail.put("createTime", history.getCreateTime());
        detail.put("items", itemMaps);
        return detail;
    }

    @Transactional(rollbackFor = Exception.class)
    public void revoke(Long id) {
        BizInvHistory history = getById(id);
        if (history == null) throw new BusinessException("出入库历史不存在");
        if (!"normal".equals(history.getStatus())) throw new BusinessException("该记录已撤销");

        history.setStatus("revoked");
        updateById(history);

        List<BizInvHistoryItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<BizInvHistoryItem>().eq(BizInvHistoryItem::getHistoryId, id));

        boolean isInbound = INBOUND_TYPES.contains(history.getInvType());

        for (BizInvHistoryItem hi : items) {
            if (hi.getQuantity() == null || hi.getMaterialId() == null) continue;
            if ("revoked".equals(hi.getStatus())) continue; // 已单独撤销的物料行跳过
            // 标记明细行为已撤销
            hi.setStatus("revoked");
            itemMapper.updateById(hi);
            // 反向更新库存（优先 item 级仓库，回退到历史主表仓库）
            Long revokeWhId = hi.getWarehouseId() != null ? hi.getWarehouseId() : history.getWarehouseId();
            Long revokeLocId = hi.getLocationId() != null ? hi.getLocationId() : history.getLocationId();
            inventoryService.updateStock(hi.getMaterialId(), hi.getQuantity(), !isInbound, revokeWhId, revokeLocId,
                    hi.getSecondaryUnit(), hi.getSecondaryQty());

            // 恢复任务已处理数量（优先按 taskItemId 精准重算，回退到 materialId 汇总）
            if (hi.getTaskItemId() != null) {
                recalculateByTaskItemId(hi.getTaskItemId());
            } else if (history.getTaskId() != null && hi.getMaterialId() != null) {
                recalculateTaskItemProcessedQty(history.getTaskId(), hi.getMaterialId());
            }
        }

        // 更新任务状态
        if (history.getTaskId() != null) {
            BizInvTask task = taskMapper.selectById(history.getTaskId());
            if (task != null && !"completed".equals(task.getStatus()) || task != null) {
                List<BizInvTaskItem> allTaskItems = taskItemMapper.selectList(
                        new LambdaQueryWrapper<BizInvTaskItem>().eq(BizInvTaskItem::getTaskId, history.getTaskId()));
                boolean allZero = allTaskItems.stream().allMatch(i ->
                        i.getProcessedQty() == null || i.getProcessedQty().compareTo(BigDecimal.ZERO) <= 0);
                task.setStatus(allZero ? "pending" : "partial");
                taskMapper.updateById(task);
            }
        }
    }

    /**
     * 按物料维度撤销：仅撤销单条历史明细行，回滚该物料的库存和任务进度。
     * 若该历史单下所有明细均已撤销，则同步将历史单头状态标记为 revoked。
     */
    @Transactional(rollbackFor = Exception.class)
    public void revokeItem(Long itemId) {
        BizInvHistoryItem item = itemMapper.selectById(itemId);
        if (item == null) throw new BusinessException("出入库历史明细不存在");
        if ("revoked".equals(item.getStatus())) throw new BusinessException("该物料行已撤销");

        BizInvHistory history = getById(item.getHistoryId());
        if (history == null) throw new BusinessException("出入库历史单不存在");
        if ("revoked".equals(history.getStatus())) throw new BusinessException("该历史单已全部撤销");

        // 1. 标记本条明细为已撤销
        item.setStatus("revoked");
        itemMapper.updateById(item);

        boolean isInbound = INBOUND_TYPES.contains(history.getInvType());

        // 2. 反向更新库存
        if (item.getQuantity() != null && item.getMaterialId() != null) {
            Long revokeWhId = item.getWarehouseId() != null ? item.getWarehouseId() : history.getWarehouseId();
            Long revokeLocId = item.getLocationId() != null ? item.getLocationId() : history.getLocationId();
            inventoryService.updateStock(item.getMaterialId(), item.getQuantity(), !isInbound, revokeWhId, revokeLocId,
                    item.getSecondaryUnit(), item.getSecondaryQty());
        }

        // 3. 重新计算任务已处理数量（优先按 taskItemId 精准重算，回退到 materialId 汇总）
        if (item.getTaskItemId() != null) {
            recalculateByTaskItemId(item.getTaskItemId());
        } else if (history.getTaskId() != null && item.getMaterialId() != null) {
            recalculateTaskItemProcessedQty(history.getTaskId(), item.getMaterialId());
        }

        // 4. 检查该历史单下是否所有明细均已撤销，是则将历史单头也标记为 revoked
        List<BizInvHistoryItem> allItems = itemMapper.selectList(
                new LambdaQueryWrapper<BizInvHistoryItem>().eq(BizInvHistoryItem::getHistoryId, history.getId()));
        boolean allRevoked = allItems.stream().allMatch(i -> "revoked".equals(i.getStatus()));
        if (allRevoked) {
            history.setStatus("revoked");
            updateById(history);
        }

        // 5. 更新任务状态
        if (history.getTaskId() != null) {
            BizInvTask task = taskMapper.selectById(history.getTaskId());
            if (task != null) {
                List<BizInvTaskItem> allTaskItems = taskItemMapper.selectList(
                        new LambdaQueryWrapper<BizInvTaskItem>().eq(BizInvTaskItem::getTaskId, history.getTaskId()));
                boolean allZero = allTaskItems.stream().allMatch(i ->
                        i.getProcessedQty() == null || i.getProcessedQty().compareTo(BigDecimal.ZERO) <= 0);
                task.setStatus(allZero ? "pending" : "partial");
                taskMapper.updateById(task);
            }
        }
    }

    /**
     * 精准重算：按 taskItemId 汇总该明细行下所有未撤销历史记录的数量。
     * 解决同一任务中存在多条相同物料时，按 materialId 汇总会相互干扰的问题。
     */
    private void recalculateByTaskItemId(Long taskItemId) {
        List<BizInvHistoryItem> histItems = itemMapper.selectList(
                new LambdaQueryWrapper<BizInvHistoryItem>()
                        .eq(BizInvHistoryItem::getTaskItemId, taskItemId));
        BigDecimal total = histItems.stream()
                .filter(hi -> !"revoked".equals(hi.getStatus())) // null 视为未撤销
                .map(hi -> hi.getQuantity() != null ? hi.getQuantity() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal secondaryTotal = histItems.stream()
                .filter(hi -> !"revoked".equals(hi.getStatus()))
                .map(hi -> hi.getSecondaryQty() != null ? hi.getSecondaryQty() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BizInvTaskItem ti = taskItemMapper.selectById(taskItemId);
        if (ti != null) {
            ti.setProcessedQty(total);
            if (ti.getSecondaryUnit() != null || secondaryTotal.compareTo(BigDecimal.ZERO) > 0) {
                ti.setSecondaryProcessedQty(secondaryTotal);
            }
            taskItemMapper.updateById(ti);
        }
    }

    /**
     * 兜底重算：按 taskId + materialId 汇总（用于 taskItemId 为 null 的旧数据）。
     * 注意：同任务中存在多条相同物料时此方法不精准，新数据应使用 recalculateByTaskItemId。
     */
    private void recalculateTaskItemProcessedQty(Long taskId, Long materialId) {
        List<Long> taskHistoryIds = list(new LambdaQueryWrapper<BizInvHistory>()
                .eq(BizInvHistory::getTaskId, taskId)
                .select(BizInvHistory::getId))
                .stream().map(BizInvHistory::getId).collect(Collectors.toList());

        BigDecimal totalProcessed = BigDecimal.ZERO;
        BigDecimal totalSecondaryProcessed = BigDecimal.ZERO;
        if (!taskHistoryIds.isEmpty()) {
            List<BizInvHistoryItem> histItems = itemMapper.selectList(
                    new LambdaQueryWrapper<BizInvHistoryItem>()
                            .in(BizInvHistoryItem::getHistoryId, taskHistoryIds)
                            .eq(BizInvHistoryItem::getMaterialId, materialId));
            totalProcessed = histItems.stream()
                    .filter(hi -> !"revoked".equals(hi.getStatus())) // null 视为未撤销
                    .map(hi -> hi.getQuantity() != null ? hi.getQuantity() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            totalSecondaryProcessed = histItems.stream()
                    .filter(hi -> !"revoked".equals(hi.getStatus()))
                    .map(hi -> hi.getSecondaryQty() != null ? hi.getSecondaryQty() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        BigDecimal finalTotal = totalProcessed;
        BigDecimal finalSecondaryTotal = totalSecondaryProcessed;
        taskItemMapper.selectList(new LambdaQueryWrapper<BizInvTaskItem>()
                        .eq(BizInvTaskItem::getTaskId, taskId)
                        .eq(BizInvTaskItem::getMaterialId, materialId))
                .forEach(ti -> {
                    ti.setProcessedQty(finalTotal);
                    if (ti.getSecondaryUnit() != null || finalSecondaryTotal.compareTo(BigDecimal.ZERO) > 0) {
                        ti.setSecondaryProcessedQty(finalSecondaryTotal);
                    }
                    taskItemMapper.updateById(ti);
                });
    }
}
