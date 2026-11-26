package com.aimes.modules.aftersale.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.PageResult;
import com.aimes.common.utils.CodeUtil;
import com.aimes.common.utils.SecurityUtil;
import com.aimes.modules.aftersale.entity.BizServiceOrder;
import com.aimes.modules.aftersale.entity.BizServiceOrderPart;
import com.aimes.modules.aftersale.mapper.BizServiceOrderMapper;
import com.aimes.modules.aftersale.mapper.BizServiceOrderPartMapper;
import com.aimes.modules.inventory.entity.BizInvTask;
import com.aimes.modules.inventory.entity.BizInvTaskItem;
import com.aimes.modules.inventory.mapper.BizInvTaskItemMapper;
import com.aimes.modules.inventory.mapper.BizInvTaskMapper;
import com.aimes.modules.master.entity.BizMaterial;
import com.aimes.modules.master.mapper.BizMaterialMapper;
import com.aimes.modules.finance.entity.BizReceivable;
import com.aimes.modules.finance.service.BizFinanceService;
import com.aimes.modules.mrp.entity.BizProductionPlan;
import com.aimes.modules.mrp.entity.BizProductionPlanItem;
import com.aimes.modules.mrp.mapper.BizProductionPlanItemMapper;
import com.aimes.modules.mrp.service.BizProductionPlanService;
import com.aimes.modules.production.entity.BizWorkOrder;
import com.aimes.modules.production.mapper.BizWorkOrderMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BizServiceOrderService extends ServiceImpl<BizServiceOrderMapper, BizServiceOrder> {

    private final BizServiceOrderMapper orderMapper;
    private final BizServiceOrderPartMapper partMapper;
    private final BizMaterialMapper materialMapper;
    private final BizInvTaskMapper invTaskMapper;
    private final BizInvTaskItemMapper invTaskItemMapper;
    private final BizWorkOrderMapper workOrderMapper;
    @Autowired
    @Lazy
    private BizFinanceService financeService;
    @Autowired
    @Lazy
    private BizProductionPlanService productionPlanService;
    @Autowired
    @Lazy
    private BizProductionPlanItemMapper planItemMapper;

    // ============ 列表 ============
    public PageResult<Map<String, Object>> getPage(String serviceNo, String customerName,
                                                    String productName, Integer inWarranty,
                                                    String status, int pageNum, int pageSize) {
        LambdaQueryWrapper<BizServiceOrder> wrapper = new LambdaQueryWrapper<BizServiceOrder>()
                .like(StringUtils.hasText(serviceNo), BizServiceOrder::getServiceNo, serviceNo)
                .like(StringUtils.hasText(customerName), BizServiceOrder::getCustomerName, customerName)
                .like(StringUtils.hasText(productName), BizServiceOrder::getProductName, productName)
                .eq(inWarranty != null, BizServiceOrder::getInWarranty, inWarranty)
                .eq(StringUtils.hasText(status), BizServiceOrder::getStatus, status)
                .orderByDesc(BizServiceOrder::getCreateTime);
        Page<BizServiceOrder> page = page(new Page<>(pageNum, pageSize), wrapper);
        List<Map<String, Object>> list = page.getRecords().stream().map(this::toMap).toList();
        return new PageResult<>(list, page.getTotal(), pageNum, pageSize);
    }

    public Map<String, Object> getDetail(Long id) {
        BizServiceOrder order = getById(id);
        if (order == null) throw new BusinessException("售后订单不存在");
        Map<String, Object> map = toMap(order);
        map.put("parts", listParts(id));
        return map;
    }

    public List<Map<String, Object>> listParts(Long orderId) {
        List<BizServiceOrderPart> parts = partMapper.selectList(
                new LambdaQueryWrapper<BizServiceOrderPart>()
                        .eq(BizServiceOrderPart::getServiceOrderId, orderId)
                        .eq(BizServiceOrderPart::getDeleted, 0)
                        .orderByAsc(BizServiceOrderPart::getId));

        // 批量查计划行，获取计划编号用于展示
        Set<Long> planItemIds = parts.stream().map(BizServiceOrderPart::getPlanItemId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> planNoMap = new HashMap<>();
        if (!planItemIds.isEmpty()) {
            List<BizProductionPlanItem> planItems = planItemMapper.selectBatchIds(planItemIds);
            Set<Long> planIds = planItems.stream().map(BizProductionPlanItem::getPlanId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            Map<Long, String> idToNo = planIds.isEmpty() ? Map.of() :
                    productionPlanService.listByIds(planIds).stream()
                            .collect(Collectors.toMap(BizProductionPlan::getId, BizProductionPlan::getPlanNo, (a, b) -> a));
            for (BizProductionPlanItem pi : planItems) {
                planNoMap.put(pi.getId(), idToNo.getOrDefault(pi.getPlanId(), ""));
            }
        }

        return parts.stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("serviceOrderId", p.getServiceOrderId());
            m.put("materialId", p.getMaterialId());
            m.put("materialNo", p.getMaterialNo());
            m.put("materialName", p.getMaterialName());
            m.put("spec", p.getSpec());
            m.put("unit", p.getUnit());
            m.put("quantity", p.getQuantity());
            m.put("unitPrice", p.getUnitPrice());
            m.put("totalPrice", p.getTotalPrice());
            m.put("invTaskId", p.getInvTaskId());
            m.put("workOrderId", p.getWorkOrderId());
            m.put("planItemId", p.getPlanItemId());
            m.put("planNo", planNoMap.getOrDefault(p.getPlanItemId(), ""));
            m.put("issued", p.getIssued());
            m.put("remark", p.getRemark());
            return m;
        }).toList();
    }

    // ============ 关联生产工单 ============
    @Transactional
    public void linkWorkOrder(Long partId, Long workOrderId) {
        BizServiceOrderPart part = partMapper.selectById(partId);
        if (part == null) throw new BusinessException("配件明细不存在");
        if (workOrderId != null && workOrderMapper.selectById(workOrderId) == null) {
            throw new BusinessException("工单不存在");
        }
        part.setWorkOrderId(workOrderId);
        partMapper.updateById(part);
    }

    // ============ 关联任务列表 (库存任务 + 生产工单) ============
    public Map<String, Object> getRelatedTasks(Long orderId) {
        BizServiceOrder order = getById(orderId);
        if (order == null) throw new BusinessException("售后订单不存在");

        // 库存出库任务: sourceType=service_order, sourceId=orderId
        List<BizInvTask> invTasks = invTaskMapper.selectList(
                new LambdaQueryWrapper<BizInvTask>()
                        .eq(BizInvTask::getSourceType, "service_order")
                        .eq(BizInvTask::getSourceId, orderId)
                        .orderByDesc(BizInvTask::getCreateTime));
        List<Map<String, Object>> invList = invTasks.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("taskNo", t.getTaskNo());
            m.put("invType", t.getInvType());
            m.put("warehouseId", t.getWarehouseId());
            m.put("status", t.getStatus());
            m.put("createTime", t.getCreateTime());
            return m;
        }).toList();

        // 关联工单: 从 service_order_part.work_order_id 聚合
        List<BizServiceOrderPart> parts = partMapper.selectList(
                new LambdaQueryWrapper<BizServiceOrderPart>()
                        .eq(BizServiceOrderPart::getServiceOrderId, orderId)
                        .eq(BizServiceOrderPart::getDeleted, 0)
                        .isNotNull(BizServiceOrderPart::getWorkOrderId));
        Set<Long> woIds = parts.stream().map(BizServiceOrderPart::getWorkOrderId).collect(Collectors.toSet());
        List<Map<String, Object>> woList = new ArrayList<>();
        if (!woIds.isEmpty()) {
            List<BizWorkOrder> wos = workOrderMapper.selectBatchIds(woIds);
            Map<Long, BizWorkOrder> woMap = wos.stream().collect(Collectors.toMap(BizWorkOrder::getId, w -> w));
            // 用 parts 顺序 (按配件展示, 一行配件 → 一行工单)
            for (BizServiceOrderPart p : parts) {
                BizWorkOrder wo = woMap.get(p.getWorkOrderId());
                if (wo == null) continue;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("workOrderId", wo.getId());
                m.put("woNo", wo.getWoNo());
                m.put("status", wo.getStatus());
                m.put("planQty", wo.getPlanQty());
                m.put("completedQty", wo.getCompletedQty());
                m.put("deliveryDate", wo.getDeliveryDate());
                m.put("forPartId", p.getId());
                m.put("forPartName", p.getMaterialName());
                woList.add(m);
            }
        }

        // 关联车间计划: 从 service_order_part.plan_item_id 聚合
        List<BizServiceOrderPart> planLinkedParts = partMapper.selectList(
                new LambdaQueryWrapper<BizServiceOrderPart>()
                        .eq(BizServiceOrderPart::getServiceOrderId, orderId)
                        .eq(BizServiceOrderPart::getDeleted, 0)
                        .isNotNull(BizServiceOrderPart::getPlanItemId));
        List<Map<String, Object>> planList = new ArrayList<>();
        if (!planLinkedParts.isEmpty()) {
            Set<Long> piIds = planLinkedParts.stream().map(BizServiceOrderPart::getPlanItemId).collect(Collectors.toSet());
            List<BizProductionPlanItem> planItems = planItemMapper.selectBatchIds(piIds);
            Set<Long> planIds = planItems.stream().map(BizProductionPlanItem::getPlanId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            Map<Long, BizProductionPlan> planMap = planIds.isEmpty() ? Map.of() :
                    productionPlanService.listByIds(planIds).stream()
                            .collect(Collectors.toMap(BizProductionPlan::getId, pp -> pp));
            Map<Long, BizProductionPlanItem> itemMap = planItems.stream()
                    .collect(Collectors.toMap(BizProductionPlanItem::getId, pi -> pi));
            for (BizServiceOrderPart p : planLinkedParts) {
                BizProductionPlanItem pi = itemMap.get(p.getPlanItemId());
                if (pi == null) continue;
                BizProductionPlan pp = planMap.get(pi.getPlanId());
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("planId", pi.getPlanId());
                m.put("planNo", pp != null ? pp.getPlanNo() : "");
                m.put("planStatus", pp != null ? pp.getStatus() : "");
                m.put("planQty", pi.getPlanQty());
                m.put("releasedQty", pi.getReleasedQty());
                m.put("completedQty", pi.getCompletedQty());
                m.put("planEndDate", pi.getPlanEndDate());
                m.put("forPartId", p.getId());
                m.put("forPartName", p.getMaterialName());
                planList.add(m);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("invTasks", invList);
        result.put("workOrders", woList);
        result.put("productionPlans", planList);
        return result;
    }

    private Map<String, Object> toMap(BizServiceOrder o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId());
        m.put("serviceNo", o.getServiceNo());
        m.put("salesOrderId", o.getSalesOrderId());
        m.put("salesOrderNo", o.getSalesOrderNo());
        m.put("customerId", o.getCustomerId());
        m.put("customerName", o.getCustomerName());
        m.put("productId", o.getProductId());
        m.put("productNo", o.getProductNo());
        m.put("productName", o.getProductName());
        m.put("spec", o.getSpec());
        m.put("inWarranty", o.getInWarranty());
        m.put("issueDescription", o.getIssueDescription());
        m.put("handlingMethod", o.getHandlingMethod());
        m.put("status", o.getStatus());
        m.put("handlerId", o.getHandlerId());
        m.put("handlerName", o.getHandlerName());
        m.put("totalPartCost", o.getTotalPartCost());
        m.put("totalChargeAmount", o.getTotalChargeAmount());
        m.put("completeTime", o.getCompleteTime());
        m.put("remark", o.getRemark());
        m.put("createTime", o.getCreateTime());
        return m;
    }

    // ============ 新建 / 编辑 ============
    @Transactional
    public Long create(BizServiceOrder order) {
        if (order.getCustomerId() == null) throw new BusinessException("请关联客户");
        if (order.getProductId() == null) throw new BusinessException("请选择产品");
        order.setServiceNo(CodeUtil.generate("SV"));
        if (order.getInWarranty() == null) order.setInWarranty(1);
        if (!StringUtils.hasText(order.getStatus())) order.setStatus("draft");
        if (order.getTotalPartCost() == null) order.setTotalPartCost(BigDecimal.ZERO);
        if (order.getTotalChargeAmount() == null) order.setTotalChargeAmount(BigDecimal.ZERO);
        save(order);
        return order.getId();
    }

    @Transactional
    public void update(Long id, BizServiceOrder patch) {
        BizServiceOrder cur = getById(id);
        if (cur == null) throw new BusinessException("售后订单不存在");
        if ("completed".equals(cur.getStatus()) || "cancelled".equals(cur.getStatus())) {
            throw new BusinessException("已完成/已取消的售后单不可修改");
        }
        if (patch.getSalesOrderId() != null) { cur.setSalesOrderId(patch.getSalesOrderId()); cur.setSalesOrderNo(patch.getSalesOrderNo()); }
        if (patch.getCustomerId() != null) { cur.setCustomerId(patch.getCustomerId()); cur.setCustomerName(patch.getCustomerName()); }
        if (patch.getProductId() != null) { cur.setProductId(patch.getProductId()); cur.setProductNo(patch.getProductNo()); cur.setProductName(patch.getProductName()); cur.setSpec(patch.getSpec()); }
        if (patch.getInWarranty() != null) cur.setInWarranty(patch.getInWarranty());
        if (patch.getIssueDescription() != null) cur.setIssueDescription(patch.getIssueDescription());
        if (patch.getHandlingMethod() != null) cur.setHandlingMethod(patch.getHandlingMethod());
        if (patch.getHandlerId() != null) { cur.setHandlerId(patch.getHandlerId()); cur.setHandlerName(patch.getHandlerName()); }
        if (patch.getRemark() != null) cur.setRemark(patch.getRemark());
        updateById(cur);
    }

    @Transactional
    public void cancel(Long id) {
        BizServiceOrder cur = getById(id);
        if (cur == null) throw new BusinessException("售后订单不存在");
        if ("completed".equals(cur.getStatus())) throw new BusinessException("已完成的售后单不可取消");
        cur.setStatus("cancelled");
        updateById(cur);
    }

    @Transactional
    public void complete(Long id) {
        BizServiceOrder cur = getById(id);
        if (cur == null) throw new BusinessException("售后订单不存在");
        if ("completed".equals(cur.getStatus())) throw new BusinessException("已完成");
        cur.setStatus("completed");
        cur.setCompleteTime(LocalDateTime.now());
        updateById(cur);

        // 保外 + 配件金额>0 → 生成应收账款 (走标准收款流程)
        boolean isOutWarranty = cur.getInWarranty() != null && cur.getInWarranty() == 0;
        BigDecimal charge = cur.getTotalChargeAmount() != null ? cur.getTotalChargeAmount() : BigDecimal.ZERO;
        if (isOutWarranty && charge.compareTo(BigDecimal.ZERO) > 0) {
            try {
                BizReceivable rec = new BizReceivable();
                rec.setOrderId(cur.getSalesOrderId()); // 关联到原销售订单(可空)
                rec.setCustomerId(cur.getCustomerId());
                rec.setAmount(charge);
                rec.setReceivedAmount(BigDecimal.ZERO);
                rec.setDueDate(java.time.LocalDate.now().plusDays(30));
                rec.setRemark("售后服务 [" + cur.getServiceNo() + "] - "
                        + (cur.getCustomerName() != null ? cur.getCustomerName() : ""));
                financeService.createReceivable(rec);
            } catch (Exception e) {
                // 不阻断售后完成. 应收账款异常单独记录, 财务可手动补.
                log.error("[Aftersale] 生成应收账款失败: serviceNo={}, amount={}", cur.getServiceNo(), charge, e);
            }
        }
    }

    // ============ 给配件转车间计划 ============
    /**
     * 配件需新做时, 一键创建车间计划行关联到这个配件.
     * body = { deliveryDate?: "YYYY-MM-DD" }
     * 返回 planId (biz_production_plan.id)
     */
    @Transactional
    public Long createWorkOrderForPart(Long partId, Map<String, Object> body) {
        BizServiceOrderPart part = partMapper.selectById(partId);
        if (part == null) throw new BusinessException("配件明细不存在");
        if (part.getPlanItemId() != null) throw new BusinessException("该配件已关联车间计划");
        if (part.getMaterialId() == null || part.getQuantity() == null
                || part.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("配件物料/数量不合法, 无法创建计划");
        }
        BizServiceOrder order = getById(part.getServiceOrderId());

        java.time.LocalDate deliveryDate;
        Object d = body != null ? body.get("deliveryDate") : null;
        if (d != null && !d.toString().isEmpty()) {
            try { deliveryDate = java.time.LocalDate.parse(d.toString()); }
            catch (Exception e) { throw new BusinessException("交货日期格式错误"); }
        } else {
            deliveryDate = java.time.LocalDate.now().plusDays(7);
        }

        String serviceNo = order != null ? order.getServiceNo() : "";
        String remark = "售后单[" + serviceNo + "] 配件 [" + part.getMaterialName() + "] 新做";

        BizProductionPlan plan = new BizProductionPlan();
        plan.setPlanName("售后-" + part.getMaterialName());
        plan.setRemark(remark);

        BizProductionPlanItem item = new BizProductionPlanItem();
        item.setProductId(part.getMaterialId());
        item.setPlanQty(part.getQuantity());
        item.setPlanEndDate(deliveryDate);
        item.setSourceType("aftersale");
        item.setRemark(remark);

        productionPlanService.createPlan(plan, java.util.List.of(item));

        // 关联计划行 ID 回 part（item.getId() 由 MyBatis-Plus insert 后回填）
        part.setPlanItemId(item.getId());
        partMapper.updateById(part);

        return plan.getId();
    }

    // ============ 配件管理 ============
    @Transactional
    public Long addPart(Long orderId, BizServiceOrderPart part) {
        BizServiceOrder order = getById(orderId);
        if (order == null) throw new BusinessException("售后订单不存在");
        if (part.getMaterialId() == null) throw new BusinessException("请选择配件");
        if (part.getQuantity() == null || part.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("配件数量必须大于 0");
        }
        BizMaterial mat = materialMapper.selectById(part.getMaterialId());
        if (mat == null) throw new BusinessException("配件物料不存在");

        part.setServiceOrderId(orderId);
        part.setMaterialNo(mat.getMaterialNo());
        part.setMaterialName(mat.getMaterialName());
        part.setSpec(mat.getSpec());
        part.setUnit(mat.getUnit());
        if (part.getUnitPrice() == null) part.setUnitPrice(BigDecimal.ZERO);
        part.setTotalPrice(part.getQuantity().multiply(part.getUnitPrice()));
        part.setIssued(0);
        partMapper.insert(part);

        recomputeTotals(orderId);
        return part.getId();
    }

    @Transactional
    public void updatePart(Long partId, BizServiceOrderPart patch) {
        BizServiceOrderPart cur = partMapper.selectById(partId);
        if (cur == null) throw new BusinessException("配件明细不存在");
        if (cur.getIssued() != null && cur.getIssued() == 1) {
            throw new BusinessException("已出库的配件不可修改");
        }
        if (patch.getQuantity() != null) cur.setQuantity(patch.getQuantity());
        if (patch.getUnitPrice() != null) cur.setUnitPrice(patch.getUnitPrice());
        cur.setTotalPrice(cur.getQuantity().multiply(cur.getUnitPrice() != null ? cur.getUnitPrice() : BigDecimal.ZERO));
        if (patch.getRemark() != null) cur.setRemark(patch.getRemark());
        partMapper.updateById(cur);
        recomputeTotals(cur.getServiceOrderId());
    }

    @Transactional
    public void deletePart(Long partId) {
        BizServiceOrderPart cur = partMapper.selectById(partId);
        if (cur == null) return;
        if (cur.getIssued() != null && cur.getIssued() == 1) {
            throw new BusinessException("已出库的配件不可删除, 请先撤销出库任务");
        }
        partMapper.deleteById(partId);
        recomputeTotals(cur.getServiceOrderId());
    }

    private void recomputeTotals(Long orderId) {
        List<BizServiceOrderPart> parts = partMapper.selectList(
                new LambdaQueryWrapper<BizServiceOrderPart>()
                        .eq(BizServiceOrderPart::getServiceOrderId, orderId)
                        .eq(BizServiceOrderPart::getDeleted, 0));
        BizServiceOrder order = getById(orderId);
        if (order == null) return;
        BigDecimal cost = parts.stream()
                .map(p -> p.getTotalPrice() != null ? p.getTotalPrice() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setTotalPartCost(cost);
        // 保外才向客户收费
        order.setTotalChargeAmount(order.getInWarranty() != null && order.getInWarranty() == 0
                ? cost : BigDecimal.ZERO);
        updateById(order);
    }

    // ============ 配件出库: 给所有未出库配件创建 service_out 任务 ============
    @Transactional
    public int issueParts(Long orderId, Long warehouseId) {
        if (warehouseId == null) throw new BusinessException("请选择出库仓库");
        BizServiceOrder order = getById(orderId);
        if (order == null) throw new BusinessException("售后订单不存在");
        List<BizServiceOrderPart> pending = partMapper.selectList(
                new LambdaQueryWrapper<BizServiceOrderPart>()
                        .eq(BizServiceOrderPart::getServiceOrderId, orderId)
                        .eq(BizServiceOrderPart::getIssued, 0)
                        .eq(BizServiceOrderPart::getDeleted, 0));
        if (pending.isEmpty()) throw new BusinessException("没有待出库的配件");

        // 一张售后单一张库存任务, 各配件作为 task item
        BizInvTask task = new BizInvTask();
        task.setTaskNo(CodeUtil.generate("INK"));
        task.setInvType("service_out");
        task.setRelatedOrderNo(order.getServiceNo());
        task.setWarehouseId(warehouseId);
        task.setStatus("pending");
        task.setSourceType("service_order");
        task.setSourceId(orderId);
        task.setApplicantId(SecurityUtil.getCurrentUserId());
        task.setRemark("售后[" + order.getServiceNo() + "] 配件出库");
        invTaskMapper.insert(task);

        for (BizServiceOrderPart p : pending) {
            BizInvTaskItem item = new BizInvTaskItem();
            item.setTaskId(task.getId());
            item.setMaterialId(p.getMaterialId());
            item.setMaterialNo(p.getMaterialNo());
            item.setMaterialName(p.getMaterialName());
            item.setSpec(p.getSpec());
            item.setUnit(p.getUnit());
            item.setQuantity(p.getQuantity());
            item.setUnitPrice(p.getUnitPrice());
            item.setTotalPrice(p.getTotalPrice());
            item.setProcessedQty(BigDecimal.ZERO);
            item.setWarehouseId(warehouseId);
            invTaskItemMapper.insert(item);

            p.setInvTaskId(task.getId());
            p.setIssued(1);
            partMapper.updateById(p);
        }

        // 状态推进
        if ("draft".equals(order.getStatus())) {
            order.setStatus("processing");
            updateById(order);
        }
        return pending.size();
    }

    // ============ 客户统计 ============
    public List<Map<String, Object>> getCustomerStatistics(String customerName) {
        LambdaQueryWrapper<BizServiceOrder> wrapper = new LambdaQueryWrapper<BizServiceOrder>()
                .eq(BizServiceOrder::getDeleted, 0)
                .like(StringUtils.hasText(customerName), BizServiceOrder::getCustomerName, customerName);
        List<BizServiceOrder> all = list(wrapper);
        Map<Long, List<BizServiceOrder>> grouped = all.stream()
                .filter(o -> o.getCustomerId() != null)
                .collect(Collectors.groupingBy(BizServiceOrder::getCustomerId));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Long, List<BizServiceOrder>> e : grouped.entrySet()) {
            List<BizServiceOrder> orders = e.getValue();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("customerId", e.getKey());
            row.put("customerName", orders.get(0).getCustomerName());
            row.put("totalCount", orders.size());
            row.put("inWarrantyCount", orders.stream().filter(o -> o.getInWarranty() != null && o.getInWarranty() == 1).count());
            row.put("outWarrantyCount", orders.stream().filter(o -> o.getInWarranty() != null && o.getInWarranty() == 0).count());
            row.put("completedCount", orders.stream().filter(o -> "completed".equals(o.getStatus())).count());
            row.put("totalPartCost", orders.stream().map(o -> o.getTotalPartCost() != null ? o.getTotalPartCost() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add));
            row.put("totalCharge", orders.stream().map(o -> o.getTotalChargeAmount() != null ? o.getTotalChargeAmount() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add));
            row.put("lastServiceTime", orders.stream().map(BizServiceOrder::getCreateTime).filter(Objects::nonNull)
                    .max(java.time.LocalDateTime::compareTo).orElse(null));
            result.add(row);
        }
        // 按总单数倒序
        result.sort((a, b) -> Integer.compare((int) b.get("totalCount"), (int) a.get("totalCount")));
        return result;
    }
}
