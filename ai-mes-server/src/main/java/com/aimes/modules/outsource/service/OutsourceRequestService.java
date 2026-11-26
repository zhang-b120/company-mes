package com.aimes.modules.outsource.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.PageResult;
import com.aimes.common.utils.CodeUtil;
import com.aimes.modules.master.entity.BizMaterial;
import com.aimes.modules.master.mapper.BizMaterialMapper;
import com.aimes.modules.order.entity.BizSalesOrder;
import com.aimes.modules.order.mapper.BizSalesOrderMapper;
import com.aimes.modules.outsource.entity.BizOutsourceOrder;
import com.aimes.modules.outsource.entity.BizOutsourceRequest;
import com.aimes.modules.outsource.mapper.BizOutsourceRequestMapper;
import com.aimes.modules.production.entity.BizWorkOrder;
import com.aimes.modules.production.mapper.BizWorkOrderMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OutsourceRequestService extends ServiceImpl<BizOutsourceRequestMapper, BizOutsourceRequest> {

    private final BizWorkOrderMapper workOrderMapper;
    private final BizMaterialMapper materialMapper;
    private final BizSalesOrderMapper salesOrderMapper;
    @Lazy
    private final OutsourceOrderService outsourceOrderService;

    /**
     * 分页查询外协申请
     */
    public PageResult<Map<String, Object>> getPage(String keyword, String status, String workOrderNo,
                                                    String stepName, Long productId,
                                                    String startDate, String endDate,
                                                    int pageNum, int pageSize) {
        // 如果按工单编号筛选，先查出匹配的工单ID集合
        Set<Long> matchedWoIds = null;
        if (StringUtils.hasText(workOrderNo)) {
            matchedWoIds = workOrderMapper.selectList(
                    new LambdaQueryWrapper<BizWorkOrder>()
                            .like(BizWorkOrder::getWoNo, workOrderNo)
            ).stream().map(BizWorkOrder::getId).collect(Collectors.toSet());
            if (matchedWoIds.isEmpty()) {
                return new PageResult<>(List.of(), 0L, pageNum, pageSize);
            }
        }

        LambdaQueryWrapper<BizOutsourceRequest> wrapper = new LambdaQueryWrapper<BizOutsourceRequest>()
                .like(StringUtils.hasText(keyword), BizOutsourceRequest::getRequestNo, keyword)
                .eq(StringUtils.hasText(status), BizOutsourceRequest::getStatus, status)
                .like(StringUtils.hasText(stepName), BizOutsourceRequest::getStepName, stepName)
                .eq(productId != null, BizOutsourceRequest::getProductId, productId)
                .in(matchedWoIds != null, BizOutsourceRequest::getWorkOrderId, matchedWoIds)
                .ge(StringUtils.hasText(startDate), BizOutsourceRequest::getRequiredDate, StringUtils.hasText(startDate) ? LocalDate.parse(startDate) : null)
                .le(StringUtils.hasText(endDate), BizOutsourceRequest::getRequiredDate, StringUtils.hasText(endDate) ? LocalDate.parse(endDate) : null)
                .orderByDesc(BizOutsourceRequest::getCreateTime);

        Page<BizOutsourceRequest> page = page(new Page<>(pageNum, pageSize), wrapper);

        // 批量加载关联实体名称
        List<BizOutsourceRequest> records = page.getRecords();
        Set<Long> woIds = records.stream().map(BizOutsourceRequest::getWorkOrderId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> productIds = records.stream().map(BizOutsourceRequest::getProductId).filter(Objects::nonNull).collect(Collectors.toSet());
        // 收集所有物料ID（发料物料 + 收料物料）
        Set<Long> allMaterialIds = new HashSet<>(productIds);
        records.stream().map(BizOutsourceRequest::getVirtualMaterialId).filter(Objects::nonNull).forEach(allMaterialIds::add);
        records.stream().map(BizOutsourceRequest::getReceiptMaterialId).filter(Objects::nonNull).forEach(allMaterialIds::add);

        Set<Long> orderIds = records.stream().map(BizOutsourceRequest::getOrderId).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, String> woNoMap = woIds.isEmpty() ? Map.of() :
                workOrderMapper.selectBatchIds(woIds).stream().collect(Collectors.toMap(BizWorkOrder::getId, BizWorkOrder::getWoNo, (a, b) -> a));
        Map<Long, String> materialNameMap = allMaterialIds.isEmpty() ? Map.of() :
                materialMapper.selectBatchIds(allMaterialIds).stream().collect(Collectors.toMap(BizMaterial::getId, BizMaterial::getMaterialName, (a, b) -> a));
        Map<Long, String> orderNoMap = orderIds.isEmpty() ? Map.of() :
                salesOrderMapper.selectBatchIds(orderIds).stream().collect(Collectors.toMap(BizSalesOrder::getId, BizSalesOrder::getOrderNo, (a, b) -> a));

        List<Map<String, Object>> list = records.stream().map(r -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", r.getId());
            map.put("requestNo", r.getRequestNo());
            map.put("workOrderId", r.getWorkOrderId());
            map.put("workOrderNo", r.getWorkOrderId() != null ? woNoMap.get(r.getWorkOrderId()) : null);
            map.put("taskId", r.getTaskId());
            map.put("productId", r.getProductId());
            map.put("productName", r.getProductId() != null ? materialNameMap.get(r.getProductId()) : null);
            map.put("stepName", r.getStepName());
            map.put("quantity", r.getQuantity());
            map.put("maxQty", r.getMaxQty());
            map.put("prevQualifiedQty", r.getPrevQualifiedQty());
            map.put("requestedQty", r.getRequestedQty());
            // 可申请数量 = 前道累计合格数 - 已转单数量
            BigDecimal prev = r.getPrevQualifiedQty() != null ? r.getPrevQualifiedQty() : BigDecimal.ZERO;
            BigDecimal requested = r.getRequestedQty() != null ? r.getRequestedQty() : BigDecimal.ZERO;
            BigDecimal requestableQty = prev.subtract(requested);
            if (requestableQty.compareTo(BigDecimal.ZERO) < 0) requestableQty = BigDecimal.ZERO;
            map.put("requestableQty", requestableQty);
            map.put("isAutoCreated", r.getIsAutoCreated());
            map.put("virtualMaterialId", r.getVirtualMaterialId());
            map.put("virtualMaterialName", r.getVirtualMaterialId() != null ? materialNameMap.get(r.getVirtualMaterialId()) : null);
            map.put("deliveryWarehouseId", r.getDeliveryWarehouseId());
            map.put("receiptMaterialId", r.getReceiptMaterialId());
            map.put("receiptMaterialName", r.getReceiptMaterialId() != null ? materialNameMap.get(r.getReceiptMaterialId()) : null);
            map.put("processRequirement", r.getProcessRequirement());
            map.put("drawingUrls", r.getDrawingUrls());
            map.put("requiredDate", r.getRequiredDate());
            map.put("status", r.getStatus());
            map.put("approvedBy", r.getApprovedBy());
            map.put("approvedAt", r.getApprovedAt());
            map.put("approveRemark", r.getApproveRemark());
            map.put("remark", r.getRemark());
            map.put("orderId", r.getOrderId());
            map.put("orderItemId", r.getOrderItemId());
            map.put("salesOrderNo", r.getOrderId() != null ? orderNoMap.get(r.getOrderId()) : null);
            map.put("isFullOutsource", r.getOrderId() != null && r.getWorkOrderId() == null);
            map.put("createTime", r.getCreateTime());
            map.put("updateTime", r.getUpdateTime());
            return map;
        }).collect(Collectors.toList());

        return new PageResult<>(list, page.getTotal(), pageNum, pageSize);
    }

    /**
     * 创建外协申请
     */
    public void create(BizOutsourceRequest request) {
        request.setRequestNo(CodeUtil.generate("OR"));
        request.setStatus("pending");
        save(request);
    }

    /**
     * 审批通过并自动创建外协加工单
     *
     * @param id            外协申请ID
     * @param approvedBy    审批人ID
     * @param approveRemark 审批备注
     * @param supplierId    外协供应商ID
     * @param unitPrice     单价
     * @param taxRate       税率(%)
     * @param deliveryDate  交付日期
     */
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, Long approvedBy, String approveRemark,
                        Long supplierId, BigDecimal unitPrice, BigDecimal taxRate, LocalDate deliveryDate) {
        BizOutsourceRequest request = getById(id);
        if (request == null) {
            throw new BusinessException("外协申请不存在");
        }
        if (!"pending".equals(request.getStatus())) {
            throw new BusinessException("该申请已审批");
        }
        if (supplierId == null) {
            throw new BusinessException("请选择外协供应商");
        }
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("请填写正确的单价");
        }

        // 1. 审批通过
        request.setApprovedBy(approvedBy);
        request.setApprovedAt(LocalDateTime.now());
        request.setApproveRemark(approveRemark);
        request.setStatus("approved");
        updateById(request);

        // 2. 自动创建外协加工单
        BizOutsourceOrder order = new BizOutsourceOrder();
        order.setSupplierId(supplierId);
        order.setProductId(request.getProductId());
        order.setStepName(request.getStepName());
        order.setTotalQty(request.getQuantity());
        order.setUnitPrice(unitPrice);
        order.setTaxRate(taxRate != null ? taxRate : BigDecimal.ZERO);
        order.setDeliveryDate(deliveryDate != null ? deliveryDate : request.getRequiredDate());
        order.setWorkOrderId(request.getWorkOrderId());
        order.setTaskId(request.getTaskId());
        order.setVirtualMaterialId(request.getVirtualMaterialId());
        order.setReceiptMaterialId(request.getReceiptMaterialId());
        order.setRequestId(id);

        outsourceOrderService.create(order, List.of(id));
    }

    /**
     * 审批驳回
     */
    public void reject(Long id, Long approvedBy, String approveRemark) {
        BizOutsourceRequest request = getById(id);
        if (request == null) {
            throw new BusinessException("外协申请不存在");
        }
        if (!"pending".equals(request.getStatus())) {
            throw new BusinessException("该申请已审批");
        }
        request.setApprovedBy(approvedBy);
        request.setApprovedAt(LocalDateTime.now());
        request.setApproveRemark(approveRemark);
        request.setStatus("rejected");
        updateById(request);
    }
}
