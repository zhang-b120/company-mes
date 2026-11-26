package com.aimes.modules.purchase.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.PageResult;
import com.aimes.common.utils.CodeUtil;
import com.aimes.common.utils.SecurityUtil;
import com.aimes.modules.inventory.service.BizInvTaskService;
import com.aimes.modules.master.entity.BizMaterial;
import com.aimes.modules.master.entity.BizSupplier;
import com.aimes.modules.master.mapper.BizMaterialMapper;
import com.aimes.modules.master.mapper.BizSupplierMapper;
import com.aimes.modules.purchase.entity.BizPurchaseOrder;
import com.aimes.modules.purchase.entity.BizPurchaseOrderItem;
import com.aimes.modules.purchase.entity.BizPurchaseReturn;
import com.aimes.modules.purchase.entity.BizPurchaseReturnItem;
import com.aimes.modules.purchase.mapper.BizPurchaseOrderItemMapper;
import com.aimes.modules.purchase.mapper.BizPurchaseOrderMapper;
import com.aimes.modules.purchase.mapper.BizPurchaseReturnItemMapper;
import com.aimes.modules.purchase.mapper.BizPurchaseReturnMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PurchaseReturnService extends ServiceImpl<BizPurchaseReturnMapper, BizPurchaseReturn> {

    private final BizPurchaseReturnItemMapper returnItemMapper;
    private final BizPurchaseOrderMapper purchaseOrderMapper;
    private final BizPurchaseOrderItemMapper purchaseOrderItemMapper;
    private final BizSupplierMapper supplierMapper;
    private final BizMaterialMapper materialMapper;
    private final BizInvTaskService invTaskService;

    // ==================== 分页查询 ====================
    public PageResult<Map<String, Object>> getPage(String keyword, String status, Long supplierId,
                                                    String returnNo, Long purchaseOrderId,
                                                    LocalDateTime createTimeStart,
                                                    LocalDateTime createTimeEnd, int pageNum, int pageSize) {
        Page<BizPurchaseReturn> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<BizPurchaseReturn> wrapper = new LambdaQueryWrapper<BizPurchaseReturn>()
                .orderByDesc(BizPurchaseReturn::getCreateTime);

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(BizPurchaseReturn::getReturnNo, keyword));
        }
        if (StringUtils.hasText(status)) wrapper.eq(BizPurchaseReturn::getStatus, status);
        if (supplierId != null) wrapper.eq(BizPurchaseReturn::getSupplierId, supplierId);
        if (StringUtils.hasText(returnNo)) wrapper.like(BizPurchaseReturn::getReturnNo, returnNo);
        if (purchaseOrderId != null) wrapper.eq(BizPurchaseReturn::getPurchaseOrderId, purchaseOrderId);
        if (createTimeStart != null) wrapper.ge(BizPurchaseReturn::getCreateTime, createTimeStart);
        if (createTimeEnd != null) wrapper.le(BizPurchaseReturn::getCreateTime, createTimeEnd);

        page(page, wrapper);

        // 批量加载供应商名称
        List<Long> supplierIds = page.getRecords().stream()
                .map(BizPurchaseReturn::getSupplierId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, String> supplierNameMap = new HashMap<>();
        if (!supplierIds.isEmpty()) {
            supplierMapper.selectBatchIds(supplierIds)
                    .forEach(s -> supplierNameMap.put(s.getId(), s.getSupplierName()));
        }

        // 批量加载关联PO号
        List<Long> poIds = page.getRecords().stream()
                .map(BizPurchaseReturn::getPurchaseOrderId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, String> poNoMap = new HashMap<>();
        if (!poIds.isEmpty()) {
            purchaseOrderMapper.selectBatchIds(poIds)
                    .forEach(po -> poNoMap.put(po.getId(), po.getPoNo()));
        }

        List<Map<String, Object>> records = page.getRecords().stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("returnNo", r.getReturnNo());
            m.put("supplierId", r.getSupplierId());
            m.put("supplierName", supplierNameMap.getOrDefault(r.getSupplierId(), "-"));
            m.put("purchaseOrderId", r.getPurchaseOrderId());
            m.put("poNo", r.getPurchaseOrderId() != null ? poNoMap.getOrDefault(r.getPurchaseOrderId(), "-") : "-");
            m.put("relatedOrderNo", r.getRelatedOrderNo());
            m.put("returnReason", r.getReturnReason());
            m.put("status", r.getStatus());
            m.put("invTaskId", r.getInvTaskId());
            m.put("remark", r.getRemark());
            m.put("createTime", r.getCreateTime());
            return m;
        }).collect(Collectors.toList());

        return new PageResult<>(records, page.getTotal());
    }

    // ==================== 详情 ====================
    public Map<String, Object> getDetail(Long id) {
        BizPurchaseReturn ret = getById(id);
        if (ret == null) throw new BusinessException("退货单不存在");

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", ret.getId());
        detail.put("returnNo", ret.getReturnNo());
        detail.put("supplierId", ret.getSupplierId());
        detail.put("purchaseOrderId", ret.getPurchaseOrderId());
        detail.put("relatedOrderNo", ret.getRelatedOrderNo());
        detail.put("returnReason", ret.getReturnReason());
        detail.put("status", ret.getStatus());
        detail.put("invTaskId", ret.getInvTaskId());
        detail.put("approvedBy", ret.getApprovedBy());
        detail.put("approvedAt", ret.getApprovedAt());
        detail.put("approveRemark", ret.getApproveRemark());
        detail.put("remark", ret.getRemark());
        detail.put("createTime", ret.getCreateTime());

        BizSupplier supplier = ret.getSupplierId() != null ? supplierMapper.selectById(ret.getSupplierId()) : null;
        detail.put("supplierName", supplier != null ? supplier.getSupplierName() : "-");

        if (ret.getPurchaseOrderId() != null) {
            BizPurchaseOrder po = purchaseOrderMapper.selectById(ret.getPurchaseOrderId());
            detail.put("poNo", po != null ? po.getPoNo() : "-");
        } else {
            detail.put("poNo", "-");
        }

        // 加载明细及物料信息
        List<BizPurchaseReturnItem> items = returnItemMapper.selectList(
                new LambdaQueryWrapper<BizPurchaseReturnItem>()
                        .eq(BizPurchaseReturnItem::getReturnId, id));

        List<Long> matIds = items.stream().map(BizPurchaseReturnItem::getMaterialId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, BizMaterial> matMap = new HashMap<>();
        if (!matIds.isEmpty()) {
            materialMapper.selectBatchIds(matIds).forEach(m -> matMap.put(m.getId(), m));
        }

        List<Map<String, Object>> itemList = items.stream().map(item -> {
            Map<String, Object> im = new LinkedHashMap<>();
            im.put("id", item.getId());
            im.put("materialId", item.getMaterialId());
            BizMaterial mat = item.getMaterialId() != null ? matMap.get(item.getMaterialId()) : null;
            im.put("materialNo", mat != null ? mat.getMaterialNo() : "");
            im.put("materialName", mat != null ? mat.getMaterialName() : "");
            im.put("spec", mat != null ? mat.getSpec() : "");
            im.put("unit", mat != null ? mat.getUnit() : "");
            im.put("quantity", item.getQuantity());
            im.put("unitPrice", item.getUnitPrice());
            im.put("remark", item.getRemark());
            return im;
        }).collect(Collectors.toList());
        detail.put("items", itemList);

        return detail;
    }

    // ==================== 新建草稿 ====================
    @Transactional(rollbackFor = Exception.class)
    public void create(BizPurchaseReturn ret, List<BizPurchaseReturnItem> items) {
        if (ret.getSupplierId() == null) throw new BusinessException("供应商不能为空");
        if (items == null || items.isEmpty()) throw new BusinessException("退货明细不能为空");
        ret.setReturnNo(CodeUtil.generate("PRT"));
        ret.setStatus("draft");
        save(ret);
        for (BizPurchaseReturnItem item : items) {
            item.setReturnId(ret.getId());
            returnItemMapper.insert(item);
        }
    }

    // ==================== 查询PO明细供退货预览 ====================
    public List<Map<String, Object>> getReturnPreview(Long orderId) {
        BizPurchaseOrder po = purchaseOrderMapper.selectById(orderId);
        if (po == null) throw new BusinessException("采购订单不存在");
        if (!"closed".equals(po.getStatus())) throw new BusinessException("只有办结状态的采购订单可以发起退货");

        List<BizPurchaseOrderItem> poItems = purchaseOrderItemMapper.selectList(
                new LambdaQueryWrapper<BizPurchaseOrderItem>()
                        .eq(BizPurchaseOrderItem::getPurchaseOrderId, orderId));

        List<Long> matIds = poItems.stream().map(BizPurchaseOrderItem::getMaterialId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, BizMaterial> matMap = new HashMap<>();
        if (!matIds.isEmpty()) materialMapper.selectBatchIds(matIds).forEach(m -> matMap.put(m.getId(), m));

        return poItems.stream().map(pi -> {
            BizMaterial mat = pi.getMaterialId() != null ? matMap.get(pi.getMaterialId()) : null;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("materialId", pi.getMaterialId());
            m.put("materialNo", mat != null ? mat.getMaterialNo() : "");
            m.put("materialName", mat != null ? mat.getMaterialName() : "");
            m.put("spec", mat != null ? mat.getSpec() : "");
            m.put("unit", mat != null ? mat.getUnit() : "");
            // 可退最大数量：实际入库量（stockInQty），未入库则用订单量
            BigDecimal maxQty = pi.getStockInQty() != null && pi.getStockInQty().compareTo(BigDecimal.ZERO) > 0
                    ? pi.getStockInQty() : pi.getQuantity();
            m.put("maxQty", maxQty);
            m.put("quantity", maxQty);
            m.put("unitPrice", pi.getUnitPrice());
            return m;
        }).collect(Collectors.toList());
    }

    // ==================== 由采购订单创建（直接待审批，支持传入明细） ====================
    @Transactional(rollbackFor = Exception.class)
    public Long createFromPurchaseOrder(Long orderId, String returnReason, String remark,
                                        List<BizPurchaseReturnItem> items) {
        BizPurchaseOrder po = purchaseOrderMapper.selectById(orderId);
        if (po == null) throw new BusinessException("采购订单不存在");
        if (!"closed".equals(po.getStatus())) throw new BusinessException("只有办结状态的采购订单可以发起退货");

        // 若未传入明细，则使用全部PO明细
        if (items == null || items.isEmpty()) {
            List<BizPurchaseOrderItem> poItems = purchaseOrderItemMapper.selectList(
                    new LambdaQueryWrapper<BizPurchaseOrderItem>()
                            .eq(BizPurchaseOrderItem::getPurchaseOrderId, orderId));
            if (poItems.isEmpty()) throw new BusinessException("采购订单无明细，无法创建退货单");
            items = poItems.stream().map(pi -> {
                BizPurchaseReturnItem item = new BizPurchaseReturnItem();
                item.setMaterialId(pi.getMaterialId());
                item.setQuantity(pi.getStockInQty() != null && pi.getStockInQty().compareTo(BigDecimal.ZERO) > 0
                        ? pi.getStockInQty() : pi.getQuantity());
                item.setUnitPrice(pi.getUnitPrice());
                return item;
            }).collect(Collectors.toList());
        }

        BizPurchaseReturn ret = new BizPurchaseReturn();
        ret.setReturnNo(CodeUtil.generate("PRT"));
        ret.setSupplierId(po.getSupplierId());
        ret.setPurchaseOrderId(orderId);
        ret.setStatus("pending_approval");
        ret.setReturnReason(returnReason);
        ret.setRemark(StringUtils.hasText(remark) ? remark : "由采购订单[" + po.getPoNo() + "]生成");
        save(ret);

        for (BizPurchaseReturnItem item : items) {
            if (item.getMaterialId() == null || item.getQuantity() == null
                    || item.getQuantity().compareTo(BigDecimal.ZERO) <= 0) continue;
            item.setReturnId(ret.getId());
            returnItemMapper.insert(item);
            // 累加发起退货数量到采购订单明细
            updatePOItemReturnInitiatedQty(orderId, item.getMaterialId(), item.getQuantity());
        }
        return ret.getId();
    }

    // 更新采购订单明细的发起退货数量（delta 为正数时累加，负数时扣减）
    private void updatePOItemReturnInitiatedQty(Long orderId, Long materialId, BigDecimal delta) {
        if (orderId == null || materialId == null || delta == null) return;
        BizPurchaseOrderItem poi = purchaseOrderItemMapper.selectOne(
                new LambdaQueryWrapper<BizPurchaseOrderItem>()
                        .eq(BizPurchaseOrderItem::getPurchaseOrderId, orderId)
                        .eq(BizPurchaseOrderItem::getMaterialId, materialId)
                        .last("LIMIT 1"));
        if (poi == null) return;
        BigDecimal current = poi.getReturnInitiatedQty() != null ? poi.getReturnInitiatedQty() : BigDecimal.ZERO;
        BigDecimal newQty = current.add(delta);
        poi.setReturnInitiatedQty(newQty.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : newQty);
        purchaseOrderItemMapper.updateById(poi);
    }

    // ==================== 编辑（草稿状态） ====================
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, BizPurchaseReturn ret, List<BizPurchaseReturnItem> items) {
        BizPurchaseReturn existing = getById(id);
        if (existing == null) throw new BusinessException("退货单不存在");
        if (!"draft".equals(existing.getStatus())) throw new BusinessException("只有草稿状态的退货单可以编辑");

        if (ret.getSupplierId() != null) existing.setSupplierId(ret.getSupplierId());
        existing.setReturnReason(ret.getReturnReason());
        existing.setRemark(ret.getRemark());
        existing.setRelatedOrderNo(ret.getRelatedOrderNo());
        updateById(existing);

        if (items != null && !items.isEmpty()) {
            returnItemMapper.delete(new LambdaQueryWrapper<BizPurchaseReturnItem>()
                    .eq(BizPurchaseReturnItem::getReturnId, id));
            for (BizPurchaseReturnItem item : items) {
                item.setReturnId(id);
                returnItemMapper.insert(item);
            }
        }
    }

    // ==================== 提交审批（仅草稿状态可提交） ====================
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        BizPurchaseReturn ret = getById(id);
        if (ret == null) throw new BusinessException("退货单不存在");
        if (!"draft".equals(ret.getStatus())) throw new BusinessException("只有草稿状态的退货单可以提交审批");
        ret.setStatus("pending_approval");
        updateById(ret);
    }

    // ==================== 审批通过 ====================
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, String remark) {
        BizPurchaseReturn ret = getById(id);
        if (ret == null) throw new BusinessException("退货单不存在");
        if (!"pending_approval".equals(ret.getStatus())) throw new BusinessException("当前状态不可审批");
        ret.setStatus("confirmed");
        ret.setApprovedBy(SecurityUtil.getCurrentUserId());
        ret.setApprovedAt(LocalDateTime.now());
        ret.setApproveRemark(remark);
        updateById(ret);
    }

    // ==================== 审批驳回（恢复PO明细的发起退货数量） ====================
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String remark) {
        BizPurchaseReturn ret = getById(id);
        if (ret == null) throw new BusinessException("退货单不存在");
        if (!"pending_approval".equals(ret.getStatus())) throw new BusinessException("当前状态不可驳回");
        ret.setStatus("rejected");
        ret.setApproveRemark(remark);
        updateById(ret);

        // 恢复关联采购订单明细的发起退货数量
        if (ret.getPurchaseOrderId() != null) {
            List<BizPurchaseReturnItem> items = returnItemMapper.selectList(
                    new LambdaQueryWrapper<BizPurchaseReturnItem>()
                            .eq(BizPurchaseReturnItem::getReturnId, id));
            for (BizPurchaseReturnItem item : items) {
                if (item.getMaterialId() != null && item.getQuantity() != null) {
                    updatePOItemReturnInitiatedQty(ret.getPurchaseOrderId(), item.getMaterialId(),
                            item.getQuantity().negate());
                }
            }
        }
    }

    // ==================== 生成库存任务（出库） ====================
    @Transactional(rollbackFor = Exception.class)
    public void generateInvTask(Long id, Long warehouseId) {
        BizPurchaseReturn ret = getById(id);
        if (ret == null) throw new BusinessException("退货单不存在");
        if (!"confirmed".equals(ret.getStatus())) throw new BusinessException("只有已确认状态可以生成库存任务");
        if (ret.getInvTaskId() != null) throw new BusinessException("该退货单已生成库存任务，无法重复生成");

        List<BizPurchaseReturnItem> items = returnItemMapper.selectList(
                new LambdaQueryWrapper<BizPurchaseReturnItem>()
                        .eq(BizPurchaseReturnItem::getReturnId, id));
        if (items.isEmpty()) throw new BusinessException("退货单无明细");

        // 加载物料信息
        List<Long> matIds = items.stream().map(BizPurchaseReturnItem::getMaterialId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, BizMaterial> matMap = new HashMap<>();
        if (!matIds.isEmpty()) {
            materialMapper.selectBatchIds(matIds).forEach(m -> matMap.put(m.getId(), m));
        }

        // 构建任务明细列表
        List<Map<String, Object>> taskItems = new ArrayList<>();
        for (BizPurchaseReturnItem item : items) {
            BizMaterial mat = item.getMaterialId() != null ? matMap.get(item.getMaterialId()) : null;
            Map<String, Object> ti = new HashMap<>();
            ti.put("materialId", item.getMaterialId());
            ti.put("materialNo", mat != null ? mat.getMaterialNo() : null);
            ti.put("materialName", mat != null ? mat.getMaterialName() : null);
            ti.put("spec", mat != null ? mat.getSpec() : null);
            ti.put("unit", mat != null ? mat.getUnit() : null);
            ti.put("quantity", item.getQuantity());
            ti.put("unitPrice", item.getUnitPrice());
            ti.put("warehouseId", warehouseId);
            taskItems.add(ti);
        }

        // 获取关联PO号
        String poNo = null;
        if (ret.getPurchaseOrderId() != null) {
            BizPurchaseOrder po = purchaseOrderMapper.selectById(ret.getPurchaseOrderId());
            if (po != null) poNo = po.getPoNo();
        }

        Long taskId = invTaskService.createFromPurchaseReturn(
                ret.getId(), ret.getReturnNo(), poNo,
                warehouseId, SecurityUtil.getCurrentUserId(), taskItems);

        ret.setInvTaskId(taskId);
        updateById(ret);
    }

    // ==================== 批量生成库存任务 ====================
    @Transactional(rollbackFor = Exception.class)
    public void batchGenerateInvTask(List<Long> ids, Long warehouseId) {
        List<String> errors = new ArrayList<>();
        for (Long id : ids) {
            try {
                generateInvTask(id, warehouseId);
            } catch (Exception e) {
                errors.add(e.getMessage());
            }
        }
        if (!errors.isEmpty() && errors.size() == ids.size()) {
            throw new BusinessException("全部失败: " + String.join("; ", errors));
        }
    }

    // ==================== 办结（由库存任务完成触发） ====================
    @Transactional(rollbackFor = Exception.class)
    public void closeReturn(Long id) {
        BizPurchaseReturn ret = getById(id);
        if (ret == null) return;
        if ("closed".equals(ret.getStatus())) return;
        ret.setStatus("closed");
        updateById(ret);
    }
}
