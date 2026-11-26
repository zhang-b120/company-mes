package com.aimes.modules.inventory.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.PageResult;
import com.aimes.common.utils.CodeUtil;
import com.aimes.common.utils.SecurityUtil;
import com.aimes.modules.inventory.entity.BizInvTransfer;
import com.aimes.modules.inventory.entity.BizInvTransferItem;
import com.aimes.modules.inventory.entity.BizInvHistory;
import com.aimes.modules.inventory.entity.BizInvHistoryItem;
import com.aimes.modules.inventory.mapper.BizInvTransferMapper;
import com.aimes.modules.inventory.mapper.BizInvTransferItemMapper;
import com.aimes.modules.inventory.mapper.BizInvHistoryMapper;
import com.aimes.modules.inventory.mapper.BizInvHistoryItemMapper;
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

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BizInvTransferService extends ServiceImpl<BizInvTransferMapper, BizInvTransfer> {

    private final BizInvTransferItemMapper itemMapper;
    private final InventoryService inventoryService;
    private final BizInvHistoryMapper historyMapper;
    private final BizInvHistoryItemMapper historyItemMapper;
    private final SysWarehouseMapper warehouseMapper;
    private final SysUserMapper userMapper;

    /**
     * 分页查询调拨单
     */
    public PageResult<Map<String, Object>> getPage(Map<String, Object> params) {
        int pageNum = Integer.parseInt(params.getOrDefault("pageNum", "1").toString());
        int pageSize = Integer.parseInt(params.getOrDefault("pageSize", "20").toString());
        String status = (String) params.get("status");
        String transferNo = (String) params.get("transferNo");
        String fromWarehouseId = params.get("fromWarehouseId") != null ? params.get("fromWarehouseId").toString() : null;
        String toWarehouseId = params.get("toWarehouseId") != null ? params.get("toWarehouseId").toString() : null;
        String applicantId = params.get("applicantId") != null ? params.get("applicantId").toString() : null;
        String dateStart = (String) params.get("dateStart");
        String dateEnd = (String) params.get("dateEnd");

        LambdaQueryWrapper<BizInvTransfer> wrapper = new LambdaQueryWrapper<BizInvTransfer>()
                .like(StringUtils.hasText(transferNo), BizInvTransfer::getTransferNo, transferNo)
                .eq(StringUtils.hasText(status), BizInvTransfer::getStatus, status)
                .eq(StringUtils.hasText(fromWarehouseId), BizInvTransfer::getFromWarehouseId, fromWarehouseId != null && !fromWarehouseId.isEmpty() ? Long.valueOf(fromWarehouseId) : null)
                .eq(StringUtils.hasText(toWarehouseId), BizInvTransfer::getToWarehouseId, toWarehouseId != null && !toWarehouseId.isEmpty() ? Long.valueOf(toWarehouseId) : null)
                .eq(StringUtils.hasText(applicantId), BizInvTransfer::getApplicantId, applicantId != null && !applicantId.isEmpty() ? Long.valueOf(applicantId) : null)
                .ge(StringUtils.hasText(dateStart), BizInvTransfer::getCreateTime, dateStart)
                .le(StringUtils.hasText(dateEnd), BizInvTransfer::getCreateTime, dateEnd != null && !dateEnd.isEmpty() ? dateEnd + " 23:59:59" : null)
                .orderByDesc(BizInvTransfer::getCreateTime);

        Page<BizInvTransfer> page = page(new Page<>(pageNum, pageSize), wrapper);

        // 补充仓库名称和申请人名称
        Set<Long> whIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        for (BizInvTransfer t : page.getRecords()) {
            whIds.add(t.getFromWarehouseId());
            whIds.add(t.getToWarehouseId());
            if (t.getFromLocationId() != null) whIds.add(t.getFromLocationId());
            if (t.getToLocationId() != null) whIds.add(t.getToLocationId());
            if (t.getApplicantId() != null) userIds.add(t.getApplicantId());
        }
        whIds.remove(null);
        Map<Long, String> whNameMap = whIds.isEmpty() ? Map.of() :
                warehouseMapper.selectBatchIds(whIds).stream()
                        .collect(Collectors.toMap(SysWarehouse::getId, SysWarehouse::getWarehouseName, (a, b) -> a));
        Map<Long, String> userNameMap = userIds.isEmpty() ? Map.of() :
                userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, u -> u.getRealName() != null ? u.getRealName() : u.getUsername(), (a, b) -> a));

        List<Map<String, Object>> records = page.getRecords().stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("transferNo", t.getTransferNo());
            m.put("fromWarehouseId", t.getFromWarehouseId());
            m.put("fromWarehouseName", t.getFromWarehouseId() != null ? whNameMap.getOrDefault(t.getFromWarehouseId(), "") : "");
            m.put("fromLocationId", t.getFromLocationId());
            m.put("fromLocationName", t.getFromLocationId() != null ? whNameMap.getOrDefault(t.getFromLocationId(), "") : "");
            m.put("toWarehouseId", t.getToWarehouseId());
            m.put("toWarehouseName", t.getToWarehouseId() != null ? whNameMap.getOrDefault(t.getToWarehouseId(), "") : "");
            m.put("toLocationId", t.getToLocationId());
            m.put("toLocationName", t.getToLocationId() != null ? whNameMap.getOrDefault(t.getToLocationId(), "") : "");
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
     * 获取调拨单详情
     */
    public Map<String, Object> getDetail(Long id) {
        BizInvTransfer transfer = getById(id);
        if (transfer == null) throw new BusinessException("调拨单不存在");

        List<BizInvTransferItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<BizInvTransferItem>()
                        .eq(BizInvTransferItem::getTransferId, id)
                        .eq(BizInvTransferItem::getDeleted, 0));

        // 查仓库名
        Set<Long> whIds = new HashSet<>();
        whIds.add(transfer.getFromWarehouseId());
        whIds.add(transfer.getToWarehouseId());
        if (transfer.getFromLocationId() != null) whIds.add(transfer.getFromLocationId());
        if (transfer.getToLocationId() != null) whIds.add(transfer.getToLocationId());
        Map<Long, String> whNameMap = warehouseMapper.selectBatchIds(whIds).stream()
                .collect(Collectors.toMap(SysWarehouse::getId, SysWarehouse::getWarehouseName, (a, b) -> a));

        String applicantName = "";
        if (transfer.getApplicantId() != null) {
            SysUser user = userMapper.selectById(transfer.getApplicantId());
            if (user != null) applicantName = user.getRealName() != null ? user.getRealName() : user.getUsername();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", transfer.getId());
        result.put("transferNo", transfer.getTransferNo());
        result.put("fromWarehouseId", transfer.getFromWarehouseId());
        result.put("fromWarehouseName", whNameMap.getOrDefault(transfer.getFromWarehouseId(), ""));
        result.put("fromLocationId", transfer.getFromLocationId());
        result.put("fromLocationName", transfer.getFromLocationId() != null ? whNameMap.getOrDefault(transfer.getFromLocationId(), "") : "");
        result.put("toWarehouseId", transfer.getToWarehouseId());
        result.put("toWarehouseName", whNameMap.getOrDefault(transfer.getToWarehouseId(), ""));
        result.put("toLocationId", transfer.getToLocationId());
        result.put("toLocationName", transfer.getToLocationId() != null ? whNameMap.getOrDefault(transfer.getToLocationId(), "") : "");
        result.put("status", transfer.getStatus());
        result.put("applicantName", applicantName);
        result.put("approveRemark", transfer.getApproveRemark());
        result.put("remark", transfer.getRemark());
        result.put("createTime", transfer.getCreateTime());
        result.put("items", items);
        return result;
    }

    /**
     * 根据调拨单号查询详情
     */
    public Map<String, Object> getDetailByNo(String transferNo) {
        BizInvTransfer transfer = getOne(new LambdaQueryWrapper<BizInvTransfer>()
                .eq(BizInvTransfer::getTransferNo, transferNo));
        if (transfer == null) return null;
        return getDetail(transfer.getId());
    }

    /**
     * 创建调拨单
     */
    @Transactional(rollbackFor = Exception.class)
    public void create(Map<String, Object> params) {
        Long fromWarehouseId = Long.valueOf(params.get("fromWarehouseId").toString());
        Long toWarehouseId = Long.valueOf(params.get("toWarehouseId").toString());
        if (fromWarehouseId.equals(toWarehouseId)) {
            throw new BusinessException("源仓库和目标仓库不能相同");
        }

        BizInvTransfer transfer = new BizInvTransfer();
        transfer.setTransferNo(CodeUtil.generate("TF"));
        transfer.setFromWarehouseId(fromWarehouseId);
        transfer.setFromLocationId(params.get("fromLocationId") != null ? Long.valueOf(params.get("fromLocationId").toString()) : null);
        transfer.setToWarehouseId(toWarehouseId);
        transfer.setToLocationId(params.get("toLocationId") != null ? Long.valueOf(params.get("toLocationId").toString()) : null);
        transfer.setStatus("pending");
        transfer.setApplicantId(SecurityUtil.getCurrentUserId());
        transfer.setRemark(params.get("remark") != null ? params.get("remark").toString() : null);
        save(transfer);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) params.get("items");
        if (items == null || items.isEmpty()) {
            throw new BusinessException("请至少添加一条调拨明细");
        }
        for (Map<String, Object> item : items) {
            BizInvTransferItem ti = new BizInvTransferItem();
            ti.setTransferId(transfer.getId());
            ti.setMaterialId(Long.valueOf(item.get("materialId").toString()));
            ti.setMaterialNo(item.get("materialNo") != null ? item.get("materialNo").toString() : null);
            ti.setMaterialName(item.get("materialName") != null ? item.get("materialName").toString() : null);
            ti.setSpec(item.get("spec") != null ? item.get("spec").toString() : null);
            ti.setUnit(item.get("unit") != null ? item.get("unit").toString() : null);
            ti.setQuantity(new java.math.BigDecimal(item.get("quantity").toString()));
            itemMapper.insert(ti);
        }
    }

    /**
     * 审批通过调拨单 → 自动执行库存变动
     */
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, String approveRemark) {
        BizInvTransfer transfer = getById(id);
        if (transfer == null) throw new BusinessException("调拨单不存在");
        if (!"pending".equals(transfer.getStatus())) throw new BusinessException("只有待审批的调拨单才能审批");

        List<BizInvTransferItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<BizInvTransferItem>()
                        .eq(BizInvTransferItem::getTransferId, id)
                        .eq(BizInvTransferItem::getDeleted, 0));

        // 执行调拨：先出后入
        for (BizInvTransferItem item : items) {
            // 调拨出库
            inventoryService.stockOut(
                    item.getMaterialId(), transfer.getFromWarehouseId(), item.getQuantity(),
                    "transfer_out", null, "transfer", transfer.getId());
            // 调拨入库
            inventoryService.stockIn(
                    item.getMaterialId(), transfer.getToWarehouseId(), item.getQuantity(),
                    "transfer_in", null, "transfer", transfer.getId());
        }

        // 生成出入库历史记录
        createTransferHistory(transfer, items, "transfer_out", transfer.getFromWarehouseId(), transfer.getFromLocationId());
        createTransferHistory(transfer, items, "transfer_in", transfer.getToWarehouseId(), transfer.getToLocationId());

        // 更新调拨单状态
        transfer.setStatus("completed");
        transfer.setApproveRemark(approveRemark);
        updateById(transfer);
    }

    /**
     * 驳回调拨单
     */
    public void reject(Long id, String approveRemark) {
        BizInvTransfer transfer = getById(id);
        if (transfer == null) throw new BusinessException("调拨单不存在");
        if (!"pending".equals(transfer.getStatus())) throw new BusinessException("只有待审批的调拨单才能驳回");

        transfer.setStatus("rejected");
        transfer.setApproveRemark(approveRemark);
        updateById(transfer);
    }

    /**
     * 创建调拨出入库历史
     */
    private void createTransferHistory(BizInvTransfer transfer, List<BizInvTransferItem> items,
                                        String invType, Long warehouseId, Long locationId) {
        BizInvHistory history = new BizInvHistory();
        history.setHistoryNo(CodeUtil.generate("INH"));
        history.setRelatedOrderNo(transfer.getTransferNo());
        history.setInvType(invType);
        history.setWarehouseId(warehouseId);
        history.setLocationId(locationId);
        history.setStatus("normal");
        history.setApplicantId(transfer.getApplicantId());
        history.setOperatorId(SecurityUtil.getCurrentUserId());
        history.setRemark("仓库调拨 " + transfer.getTransferNo());
        historyMapper.insert(history);

        for (BizInvTransferItem item : items) {
            BizInvHistoryItem hi = new BizInvHistoryItem();
            hi.setHistoryId(history.getId());
            hi.setMaterialId(item.getMaterialId());
            hi.setMaterialNo(item.getMaterialNo());
            hi.setMaterialName(item.getMaterialName());
            hi.setSpec(item.getSpec());
            hi.setUnit(item.getUnit());
            hi.setQuantity(item.getQuantity());
            hi.setWarehouseId(warehouseId);
            hi.setLocationId(locationId);
            hi.setStatus("normal");
            historyItemMapper.insert(hi);
        }
    }
}
