package com.aimes.modules.inventory.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.PageResult;
import com.aimes.common.utils.CodeUtil;
import com.aimes.common.utils.SecurityUtil;
import com.aimes.modules.inventory.entity.*;
import com.aimes.modules.inventory.mapper.BizInvHistoryItemMapper;
import com.aimes.modules.inventory.mapper.BizInvHistoryMapper;
import com.aimes.modules.inventory.mapper.BizInvStocktakingItemMapper;
import com.aimes.modules.inventory.mapper.BizInvStocktakingMapper;
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
public class BizInvStocktakingService extends ServiceImpl<BizInvStocktakingMapper, BizInvStocktaking> {

    private final BizInvStocktakingItemMapper itemMapper;
    private final InventoryService inventoryService;
    private final BizInvHistoryMapper historyMapper;
    private final BizInvHistoryItemMapper historyItemMapper;
    private final SysWarehouseMapper warehouseMapper;
    private final SysUserMapper userMapper;

    /**
     * 分页查询盘点单
     */
    public PageResult<Map<String, Object>> getPage(Map<String, Object> params) {
        int pageNum = Integer.parseInt(params.getOrDefault("pageNum", "1").toString());
        int pageSize = Integer.parseInt(params.getOrDefault("pageSize", "20").toString());
        String status = (String) params.get("status");
        String stocktakingNo = (String) params.get("stocktakingNo");
        String warehouseId = params.get("warehouseId") != null ? params.get("warehouseId").toString() : null;
        String applicantId = params.get("applicantId") != null ? params.get("applicantId").toString() : null;
        String dateStart = (String) params.get("dateStart");
        String dateEnd = (String) params.get("dateEnd");

        LambdaQueryWrapper<BizInvStocktaking> wrapper = new LambdaQueryWrapper<BizInvStocktaking>()
                .like(StringUtils.hasText(stocktakingNo), BizInvStocktaking::getStocktakingNo, stocktakingNo)
                .eq(StringUtils.hasText(status), BizInvStocktaking::getStatus, status)
                .eq(StringUtils.hasText(warehouseId), BizInvStocktaking::getWarehouseId, warehouseId != null && !warehouseId.isEmpty() ? Long.valueOf(warehouseId) : null)
                .eq(StringUtils.hasText(applicantId), BizInvStocktaking::getApplicantId, applicantId != null && !applicantId.isEmpty() ? Long.valueOf(applicantId) : null)
                .ge(StringUtils.hasText(dateStart), BizInvStocktaking::getCreateTime, dateStart)
                .le(StringUtils.hasText(dateEnd), BizInvStocktaking::getCreateTime, dateEnd != null && !dateEnd.isEmpty() ? dateEnd + " 23:59:59" : null)
                .orderByDesc(BizInvStocktaking::getCreateTime);

        Page<BizInvStocktaking> page = page(new Page<>(pageNum, pageSize), wrapper);

        // 补充仓库名称和申请人名称
        Set<Long> whIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        for (BizInvStocktaking t : page.getRecords()) {
            if (t.getWarehouseId() != null) whIds.add(t.getWarehouseId());
            if (t.getApplicantId() != null) userIds.add(t.getApplicantId());
        }
        Map<Long, String> whNameMap = whIds.isEmpty() ? Map.of() :
                warehouseMapper.selectBatchIds(whIds).stream()
                        .collect(Collectors.toMap(SysWarehouse::getId, SysWarehouse::getWarehouseName, (a, b) -> a));
        Map<Long, String> userNameMap = userIds.isEmpty() ? Map.of() :
                userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, u -> u.getRealName() != null ? u.getRealName() : u.getUsername(), (a, b) -> a));

        List<Map<String, Object>> records = page.getRecords().stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("stocktakingNo", t.getStocktakingNo());
            m.put("warehouseId", t.getWarehouseId());
            m.put("warehouseName", t.getWarehouseId() != null ? whNameMap.getOrDefault(t.getWarehouseId(), "") : "");
            m.put("status", t.getStatus());
            m.put("applicantId", t.getApplicantId());
            m.put("applicantName", t.getApplicantId() != null ? userNameMap.getOrDefault(t.getApplicantId(), "") : "");
            m.put("approveRemark", t.getApproveRemark());
            m.put("remark", t.getRemark());
            m.put("createTime", t.getCreateTime());
            return m;
        }).collect(Collectors.toList());

        return new PageResult<>(records, page.getTotal());
    }

    /**
     * 根据盘点单号查询详情
     */
    public Map<String, Object> getDetailByNo(String stocktakingNo) {
        BizInvStocktaking stocktaking = getOne(new LambdaQueryWrapper<BizInvStocktaking>()
                .eq(BizInvStocktaking::getStocktakingNo, stocktakingNo));
        if (stocktaking == null) return null;
        return getDetail(stocktaking.getId());
    }

    /**
     * 获取盘点单详情
     */
    public Map<String, Object> getDetail(Long id) {
        BizInvStocktaking stocktaking = getById(id);
        if (stocktaking == null) throw new BusinessException("盘点单不存在");

        List<BizInvStocktakingItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<BizInvStocktakingItem>()
                        .eq(BizInvStocktakingItem::getStocktakingId, id)
                        .eq(BizInvStocktakingItem::getDeleted, 0));

        String warehouseName = "";
        if (stocktaking.getWarehouseId() != null) {
            SysWarehouse wh = warehouseMapper.selectById(stocktaking.getWarehouseId());
            if (wh != null) warehouseName = wh.getWarehouseName();
        }

        String applicantName = "";
        if (stocktaking.getApplicantId() != null) {
            SysUser user = userMapper.selectById(stocktaking.getApplicantId());
            if (user != null) applicantName = user.getRealName() != null ? user.getRealName() : user.getUsername();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", stocktaking.getId());
        result.put("stocktakingNo", stocktaking.getStocktakingNo());
        result.put("warehouseId", stocktaking.getWarehouseId());
        result.put("warehouseName", warehouseName);
        result.put("status", stocktaking.getStatus());
        result.put("applicantName", applicantName);
        result.put("approveRemark", stocktaking.getApproveRemark());
        result.put("remark", stocktaking.getRemark());
        result.put("createTime", stocktaking.getCreateTime());
        result.put("items", items);
        return result;
    }

    /**
     * 创建盘点单 — 自动快照当前库存数量
     */
    @Transactional(rollbackFor = Exception.class)
    public void create(Map<String, Object> params) {
        Long warehouseId = Long.valueOf(params.get("warehouseId").toString());

        BizInvStocktaking stocktaking = new BizInvStocktaking();
        stocktaking.setStocktakingNo(CodeUtil.generate("ST"));
        stocktaking.setWarehouseId(warehouseId);
        stocktaking.setStatus("pending");
        stocktaking.setApplicantId(SecurityUtil.getCurrentUserId());
        stocktaking.setRemark(params.get("remark") != null ? params.get("remark").toString() : null);
        save(stocktaking);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) params.get("items");
        if (items == null || items.isEmpty()) {
            throw new BusinessException("请至少添加一条盘点明细");
        }

        for (Map<String, Object> item : items) {
            Long materialId = Long.valueOf(item.get("materialId").toString());
            BigDecimal actualQty = new BigDecimal(item.get("actualQty").toString());

            // 获取当前库存快照（在库/锁定/可用）
            BigDecimal[] stockInfo = inventoryService.getStockInfo(materialId, warehouseId);
            BigDecimal totalQty = stockInfo[0];
            BigDecimal lockedQty = stockInfo[1];
            BigDecimal availableQty = stockInfo[2];

            // 实盘数量不能小于锁定数量
            if (actualQty.compareTo(lockedQty) < 0) {
                throw new BusinessException("物料 " + (item.get("materialName") != null ? item.get("materialName").toString() : materialId) + " 的实盘数量不能小于锁定数量");
            }

            BizInvStocktakingItem si = new BizInvStocktakingItem();
            si.setStocktakingId(stocktaking.getId());
            si.setMaterialId(materialId);
            si.setMaterialNo(item.get("materialNo") != null ? item.get("materialNo").toString() : null);
            si.setMaterialName(item.get("materialName") != null ? item.get("materialName").toString() : null);
            si.setSpec(item.get("spec") != null ? item.get("spec").toString() : null);
            si.setUnit(item.get("unit") != null ? item.get("unit").toString() : null);
            si.setSystemQty(totalQty);
            si.setLockedQty(lockedQty);
            si.setAvailableQty(availableQty);
            si.setActualQty(actualQty);
            si.setDiffQty(actualQty.subtract(totalQty));
            itemMapper.insert(si);
        }
    }

    /**
     * 审批通过盘点单 → 自动执行库存调整 + 生成出入库历史
     */
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, String approveRemark) {
        BizInvStocktaking stocktaking = getById(id);
        if (stocktaking == null) throw new BusinessException("盘点单不存在");
        if (!"pending".equals(stocktaking.getStatus())) throw new BusinessException("只有待审批的盘点单才能审批");

        List<BizInvStocktakingItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<BizInvStocktakingItem>()
                        .eq(BizInvStocktakingItem::getStocktakingId, id)
                        .eq(BizInvStocktakingItem::getDeleted, 0));

        // 按盘盈和盘亏分组
        List<BizInvStocktakingItem> profitItems = new ArrayList<>();  // 盘盈(diff > 0) → 入库
        List<BizInvStocktakingItem> lossItems = new ArrayList<>();    // 盘亏(diff < 0) → 出库

        for (BizInvStocktakingItem item : items) {
            if (item.getDiffQty() == null) continue;
            int cmp = item.getDiffQty().compareTo(BigDecimal.ZERO);
            if (cmp > 0) {
                profitItems.add(item);
            } else if (cmp < 0) {
                lossItems.add(item);
            }
            // diff == 0 无需处理
        }

        // 盘盈 → 入库
        for (BizInvStocktakingItem item : profitItems) {
            inventoryService.stockIn(
                    item.getMaterialId(), stocktaking.getWarehouseId(), item.getDiffQty(),
                    "stocktaking_in", null, "stocktaking", stocktaking.getId());
        }
        if (!profitItems.isEmpty()) {
            createStocktakingHistory(stocktaking, profitItems, "stocktaking_in");
        }

        // 盘亏 → 出库（取绝对值）
        for (BizInvStocktakingItem item : lossItems) {
            inventoryService.stockOut(
                    item.getMaterialId(), stocktaking.getWarehouseId(), item.getDiffQty().abs(),
                    "stocktaking_out", null, "stocktaking", stocktaking.getId());
        }
        if (!lossItems.isEmpty()) {
            createStocktakingHistory(stocktaking, lossItems, "stocktaking_out");
        }

        // 更新盘点单状态
        stocktaking.setStatus("approved");
        stocktaking.setApproveRemark(approveRemark);
        updateById(stocktaking);
    }

    /**
     * 驳回盘点单
     */
    public void reject(Long id, String approveRemark) {
        BizInvStocktaking stocktaking = getById(id);
        if (stocktaking == null) throw new BusinessException("盘点单不存在");
        if (!"pending".equals(stocktaking.getStatus())) throw new BusinessException("只有待审批的盘点单才能驳回");

        stocktaking.setStatus("rejected");
        stocktaking.setApproveRemark(approveRemark);
        updateById(stocktaking);
    }

    /**
     * 创建盘点出入库历史
     */
    private void createStocktakingHistory(BizInvStocktaking stocktaking,
                                           List<BizInvStocktakingItem> items, String invType) {
        BizInvHistory history = new BizInvHistory();
        history.setHistoryNo(CodeUtil.generate("INH"));
        history.setRelatedOrderNo(stocktaking.getStocktakingNo());
        history.setInvType(invType);
        history.setWarehouseId(stocktaking.getWarehouseId());
        history.setStatus("normal");
        history.setApplicantId(stocktaking.getApplicantId());
        history.setOperatorId(SecurityUtil.getCurrentUserId());
        history.setRemark("仓库盘点 " + stocktaking.getStocktakingNo());
        historyMapper.insert(history);

        for (BizInvStocktakingItem item : items) {
            BizInvHistoryItem hi = new BizInvHistoryItem();
            hi.setHistoryId(history.getId());
            hi.setMaterialId(item.getMaterialId());
            hi.setMaterialNo(item.getMaterialNo());
            hi.setMaterialName(item.getMaterialName());
            hi.setSpec(item.getSpec());
            hi.setUnit(item.getUnit());
            // 历史记录中数量取绝对值
            hi.setQuantity(item.getDiffQty().abs());
            hi.setWarehouseId(stocktaking.getWarehouseId());
            hi.setStatus("normal");
            historyItemMapper.insert(hi);
        }
    }
}
