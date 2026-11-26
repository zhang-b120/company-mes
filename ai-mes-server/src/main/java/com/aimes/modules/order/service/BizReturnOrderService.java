package com.aimes.modules.order.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.PageResult;
import com.aimes.common.utils.CodeUtil;
import com.aimes.modules.inventory.entity.BizInvHistory;
import com.aimes.modules.inventory.entity.BizInvHistoryItem;
import com.aimes.modules.inventory.mapper.BizInvHistoryMapper;
import com.aimes.modules.inventory.mapper.BizInvHistoryItemMapper;
import com.aimes.modules.inventory.service.InventoryService;
import com.aimes.modules.master.entity.BizMaterial;
import com.aimes.modules.master.mapper.BizMaterialMapper;
import com.aimes.modules.order.entity.BizReturnOrder;
import com.aimes.modules.order.entity.BizReturnOrderItem;
import com.aimes.modules.order.entity.BizSalesOrder;
import com.aimes.modules.order.entity.BizSalesOrderItem;
import com.aimes.modules.order.mapper.BizReturnOrderItemMapper;
import com.aimes.modules.order.mapper.BizReturnOrderMapper;
import com.aimes.modules.order.mapper.BizSalesOrderItemMapper;
import com.aimes.modules.order.mapper.BizSalesOrderMapper;
import com.aimes.modules.system.entity.SysWarehouse;
import com.aimes.modules.system.mapper.SysWarehouseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 销售退货单服务
 * 状态流转: pending(待审批) -> approved(已审批) -> received(已收货) -> completed(已完成)
 *          pending -> rejected(已驳回)
 * 收货后自动触发库存入库(return_in)，并反向更新销售订单已发货数量
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BizReturnOrderService extends ServiceImpl<BizReturnOrderMapper, BizReturnOrder> {

    private final BizReturnOrderItemMapper itemMapper;
    private final BizSalesOrderMapper salesOrderMapper;
    private final BizSalesOrderItemMapper salesOrderItemMapper;
    private final SysWarehouseMapper warehouseMapper;
    private final BizInvHistoryMapper invHistoryMapper;
    private final BizInvHistoryItemMapper invHistoryItemMapper;
    private final BizMaterialMapper materialMapper;
    private final InventoryService inventoryService;

    /**
     * 分页查询退货单列表
     */
    public PageResult<Map<String, Object>> getPage(String returnNo, String customerName, String orderNo,
                                                    String status, Long orderId,
                                                    int pageNum, int pageSize) {
        LambdaQueryWrapper<BizReturnOrder> wrapper = new LambdaQueryWrapper<BizReturnOrder>()
                .eq(orderId != null, BizReturnOrder::getOrderId, orderId)
                .like(StringUtils.hasText(returnNo), BizReturnOrder::getReturnNo, returnNo)
                .like(StringUtils.hasText(customerName), BizReturnOrder::getCustomerName, customerName)
                .like(StringUtils.hasText(orderNo), BizReturnOrder::getOrderNo, orderNo)
                .eq(StringUtils.hasText(status), BizReturnOrder::getStatus, status)
                .orderByDesc(BizReturnOrder::getCreateTime);
        Page<BizReturnOrder> page = new Page<>(pageNum, pageSize);
        page(page, wrapper);
        List<Map<String, Object>> list = page.getRecords().stream().map(r -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", r.getId());
            map.put("returnNo", r.getReturnNo());
            map.put("orderId", r.getOrderId());
            map.put("orderNo", r.getOrderNo());
            map.put("customerId", r.getCustomerId());
            map.put("customerName", r.getCustomerName());
            map.put("returnDate", r.getReturnDate());
            map.put("returnReason", r.getReturnReason());
            map.put("status", r.getStatus());
            map.put("remark", r.getRemark());
            map.put("createTime", r.getCreateTime());
            // 明细汇总
            List<BizReturnOrderItem> items = itemMapper.selectList(
                    new LambdaQueryWrapper<BizReturnOrderItem>().eq(BizReturnOrderItem::getReturnId, r.getId()));
            map.put("itemCount", items.size());
            map.put("totalReturnQty", items.stream().map(BizReturnOrderItem::getReturnQty)
                    .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));
            return map;
        }).collect(Collectors.toList());
        return new PageResult<>(list, page.getTotal(), pageNum, pageSize);
    }

    /**
     * 查询退货单详情
     */
    public Map<String, Object> getDetail(Long id) {
        BizReturnOrder order = getById(id);
        if (order == null) throw new BusinessException("退货单不存在");
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", order.getId());
        detail.put("returnNo", order.getReturnNo());
        detail.put("orderId", order.getOrderId());
        detail.put("orderNo", order.getOrderNo());
        detail.put("customerId", order.getCustomerId());
        detail.put("customerName", order.getCustomerName());
        detail.put("returnDate", order.getReturnDate());
        detail.put("returnReason", order.getReturnReason());
        detail.put("status", order.getStatus());
        detail.put("remark", order.getRemark());
        detail.put("createTime", order.getCreateTime());
        detail.put("updateTime", order.getUpdateTime());
        List<BizReturnOrderItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<BizReturnOrderItem>().eq(BizReturnOrderItem::getReturnId, id));
        // 仓库名称
        Set<Long> whIds = items.stream().map(BizReturnOrderItem::getWarehouseId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> whNameMap = whIds.isEmpty() ? Map.of() :
                warehouseMapper.selectBatchIds(whIds).stream()
                        .collect(Collectors.toMap(SysWarehouse::getId, SysWarehouse::getWarehouseName, (a, b) -> a));
        List<Map<String, Object>> itemList = items.stream().map(item -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", item.getId());
            m.put("returnId", item.getReturnId());
            m.put("orderItemId", item.getOrderItemId());
            m.put("productId", item.getProductId());
            m.put("productName", item.getProductName());
            m.put("spec", item.getSpec());
            m.put("unit", item.getUnit());
            m.put("returnQty", item.getReturnQty());
            m.put("receivedQty", item.getReceivedQty());
            m.put("warehouseId", item.getWarehouseId());
            m.put("warehouseName", item.getWarehouseId() != null ? whNameMap.getOrDefault(item.getWarehouseId(), "") : "");
            m.put("unitPrice", item.getUnitPrice());
            m.put("remark", item.getRemark());
            return m;
        }).collect(Collectors.toList());
        detail.put("items", itemList);
        return detail;
    }

    /**
     * 创建退货单（关联销售订单）
     */
    @Transactional
    public void create(Long orderId, String returnReason, LocalDate returnDate, String remark,
                       List<Map<String, Object>> itemData) {
        BizSalesOrder salesOrder = salesOrderMapper.selectById(orderId);
        if (salesOrder == null) throw new BusinessException("销售订单不存在");
        List<String> validStatuses = List.of("partial_shipped", "shipped", "completed", "settled");
        if (!validStatuses.contains(salesOrder.getStatus())) {
            throw new BusinessException("只有已发货的订单才能申请退货（当前状态：" + salesOrder.getStatus() + "）");
        }

        // 校验退货数量不超过已发货数量
        List<BizSalesOrderItem> orderItems = salesOrderItemMapper.selectList(
                new LambdaQueryWrapper<BizSalesOrderItem>().eq(BizSalesOrderItem::getOrderId, orderId));
        Map<Long, BizSalesOrderItem> orderItemMap = orderItems.stream()
                .collect(Collectors.toMap(BizSalesOrderItem::getId, i -> i, (a, b) -> a));

        for (Map<String, Object> item : itemData) {
            if (item.get("orderItemId") == null) continue;
            Long orderItemId = Long.valueOf(item.get("orderItemId").toString());
            BigDecimal returnQty = new BigDecimal(item.get("returnQty").toString());
            BizSalesOrderItem oi = orderItemMap.get(orderItemId);
            if (oi == null) continue;
            BigDecimal shippedQty = oi.getShippedQty() != null ? oi.getShippedQty() : BigDecimal.ZERO;
            // 已退货数量（pending/approved/received/completed 状态均算在内）
            BigDecimal alreadyReturned = calcAlreadyReturnedQty(orderItemId);
            BigDecimal maxReturnQty = shippedQty.subtract(alreadyReturned);
            if (returnQty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("产品[" + item.get("productName") + "]退货数量必须大于0");
            }
            if (returnQty.compareTo(maxReturnQty) > 0) {
                throw new BusinessException("产品[" + item.get("productName") + "]退货数量(" + returnQty +
                        ")超过可退数量(" + maxReturnQty + ")，已发货" + shippedQty + "，已申请退货" + alreadyReturned);
            }
        }

        BizReturnOrder ro = new BizReturnOrder();
        ro.setReturnNo(CodeUtil.generate("RT"));
        ro.setOrderId(orderId);
        ro.setOrderNo(salesOrder.getOrderNo());
        ro.setCustomerId(salesOrder.getCustomerId());
        ro.setReturnDate(returnDate != null ? returnDate : LocalDate.now());
        ro.setReturnReason(returnReason);
        ro.setRemark(remark);
        ro.setStatus("pending");
        save(ro);
        for (Map<String, Object> item : itemData) {
            BizReturnOrderItem ri = new BizReturnOrderItem();
            ri.setReturnId(ro.getId());
            if (item.get("orderItemId") != null) ri.setOrderItemId(Long.valueOf(item.get("orderItemId").toString()));
            ri.setProductId(Long.valueOf(item.get("productId").toString()));
            ri.setProductName(item.get("productName") != null ? item.get("productName").toString() : null);
            ri.setSpec(item.get("spec") != null ? item.get("spec").toString() : null);
            ri.setUnit(item.get("unit") != null ? item.get("unit").toString() : null);
            ri.setReturnQty(new BigDecimal(item.get("returnQty").toString()));
            ri.setReceivedQty(BigDecimal.ZERO);
            if (item.get("warehouseId") != null) ri.setWarehouseId(Long.valueOf(item.get("warehouseId").toString()));
            if (item.get("unitPrice") != null) ri.setUnitPrice(new BigDecimal(item.get("unitPrice").toString()));
            if (item.get("remark") != null) ri.setRemark(item.get("remark").toString());
            itemMapper.insert(ri);
        }
    }

    /**
     * 审批通过
     */
    @Transactional
    public void approve(Long id) {
        BizReturnOrder ro = getById(id);
        if (ro == null) throw new BusinessException("退货单不存在");
        if (!"pending".equals(ro.getStatus())) throw new BusinessException("只有待审批状态才能审批");
        ro.setStatus("approved");
        updateById(ro);
    }

    /**
     * 驳回
     */
    @Transactional
    public void reject(Long id, String reason) {
        BizReturnOrder ro = getById(id);
        if (ro == null) throw new BusinessException("退货单不存在");
        if (!"pending".equals(ro.getStatus())) throw new BusinessException("只有待审批状态才能驳回");
        ro.setStatus("rejected");
        ro.setRemark((ro.getRemark() != null ? ro.getRemark() + " | " : "") + "驳回原因: " + reason);
        updateById(ro);
    }

    /**
     * 确认收货：触发退货入库，更新销售订单已发货数量
     */
    @Transactional
    public void receive(Long id, List<Map<String, Object>> receiveData) {
        BizReturnOrder ro = getById(id);
        if (ro == null) throw new BusinessException("退货单不存在");
        if (!"approved".equals(ro.getStatus())) throw new BusinessException("只有已审批状态才能确认收货");
        List<BizReturnOrderItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<BizReturnOrderItem>().eq(BizReturnOrderItem::getReturnId, id));
        Map<Long, BizReturnOrderItem> itemMap = items.stream().collect(Collectors.toMap(BizReturnOrderItem::getId, i -> i));
        for (Map<String, Object> rd : receiveData) {
            Long itemId = Long.valueOf(rd.get("id").toString());
            BigDecimal receivedQty = new BigDecimal(rd.get("receivedQty").toString());
            Long warehouseId = rd.get("warehouseId") != null ? Long.valueOf(rd.get("warehouseId").toString()) : null;
            BizReturnOrderItem item = itemMap.get(itemId);
            if (item == null) continue;
            if (warehouseId == null) throw new BusinessException("产品[" + item.getProductName() + "]请选择入库仓库");
            item.setReceivedQty(receivedQty);
            item.setWarehouseId(warehouseId);
            itemMapper.updateById(item);
            // 触发退货入库
            if (receivedQty.compareTo(BigDecimal.ZERO) > 0) {
                try {
                    inventoryService.stockIn(item.getProductId(), warehouseId, receivedQty,
                            "return_in", null, "return_order", id);
                    // 同步创建出入库历史记录(让出入库历史页面能查到退货入库)
                    createReturnInHistory(ro, item, warehouseId, receivedQty);
                } catch (Exception e) {
                    log.warn("退货入库失败 returnId={}, itemId={}: {}", id, itemId, e.getMessage());
                }
                // 反向更新销售订单已发货数量（减少）
                if (item.getOrderItemId() != null) {
                    BizSalesOrderItem oi = salesOrderItemMapper.selectById(item.getOrderItemId());
                    if (oi != null) {
                        BigDecimal newShipped = (oi.getShippedQty() != null ? oi.getShippedQty() : BigDecimal.ZERO)
                                .subtract(receivedQty).max(BigDecimal.ZERO);
                        oi.setShippedQty(newShipped);
                        salesOrderItemMapper.updateById(oi);
                    }
                }
            }
        }
        ro.setStatus("received");
        updateById(ro);
    }

    /**
     * 计算某订单行项已申请退货的数量（排除已驳回的单据）
     */
    private BigDecimal calcAlreadyReturnedQty(Long orderItemId) {
        List<BizReturnOrderItem> existItems = itemMapper.selectList(
                new LambdaQueryWrapper<BizReturnOrderItem>()
                        .eq(BizReturnOrderItem::getOrderItemId, orderItemId));
        if (existItems.isEmpty()) return BigDecimal.ZERO;
        Set<Long> returnIds = existItems.stream().map(BizReturnOrderItem::getReturnId).collect(Collectors.toSet());
        Set<Long> rejectedIds = list(new LambdaQueryWrapper<BizReturnOrder>()
                .in(BizReturnOrder::getId, returnIds)
                .eq(BizReturnOrder::getStatus, "rejected"))
                .stream().map(BizReturnOrder::getId).collect(Collectors.toSet());
        return existItems.stream()
                .filter(i -> !rejectedIds.contains(i.getReturnId()))
                .map(i -> i.getReturnQty() != null ? i.getReturnQty() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 创建退货入库出入库历史记录
     */
    private void createReturnInHistory(BizReturnOrder ro, BizReturnOrderItem item,
                                       Long warehouseId, BigDecimal qty) {
        BizMaterial mat = item.getProductId() != null ? materialMapper.selectById(item.getProductId()) : null;
        SysWarehouse wh = warehouseId != null ? warehouseMapper.selectById(warehouseId) : null;

        BizInvHistory history = new BizInvHistory();
        history.setHistoryNo(CodeUtil.generate("INH"));
        history.setInvType("return_in");
        history.setRelatedOrderNo(ro.getReturnNo());
        history.setWarehouseId(warehouseId);
        history.setStatus("normal");
        history.setApplicantId(ro.getCreateBy());
        history.setOperatorId(com.aimes.common.utils.SecurityUtil.getCurrentUserId());
        invHistoryMapper.insert(history);

        BizInvHistoryItem hi = new BizInvHistoryItem();
        hi.setHistoryId(history.getId());
        hi.setMaterialId(item.getProductId());
        hi.setMaterialNo(mat != null ? mat.getMaterialNo() : "");
        hi.setMaterialName(item.getProductName() != null ? item.getProductName() : (mat != null ? mat.getMaterialName() : ""));
        hi.setSpec(item.getSpec() != null ? item.getSpec() : (mat != null ? mat.getSpec() : ""));
        hi.setUnit(item.getUnit() != null ? item.getUnit() : (mat != null ? mat.getUnit() : ""));
        hi.setQuantity(qty);
        hi.setUnitPrice(item.getUnitPrice());
        hi.setWarehouseId(warehouseId);
        hi.setStatus("normal");
        invHistoryItemMapper.insert(hi);
    }

    /**
     * 完成退货单（已收货后手动完成）
     */
    @Transactional
    public void complete(Long id) {
        BizReturnOrder ro = getById(id);
        if (ro == null) throw new BusinessException("退货单不存在");
        if (!"received".equals(ro.getStatus())) throw new BusinessException("只有已收货状态才能完成");
        ro.setStatus("completed");
        updateById(ro);
    }
}
