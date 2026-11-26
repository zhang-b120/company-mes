package com.aimes.modules.order.service;

import com.aimes.common.event.ApprovalCompletedEvent;
import com.aimes.common.event.ApprovalRejectedEvent;
import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.PageResult;
import com.aimes.common.utils.SecurityUtil;
import com.aimes.modules.approval.entity.BizApprovalInstance;
import com.aimes.modules.approval.service.BizApprovalService;
import com.aimes.modules.order.entity.BizOrderChange;
import com.aimes.modules.master.entity.BizCustomer;
import com.aimes.modules.master.entity.BizMaterial;
import com.aimes.modules.master.mapper.BizCustomerMapper;
import com.aimes.modules.master.mapper.BizMaterialMapper;
import com.aimes.modules.order.entity.BizSalesOrder;
import com.aimes.modules.order.entity.BizSalesOrderItem;
import com.aimes.modules.order.mapper.BizOrderChangeMapper;
import com.aimes.modules.order.mapper.BizSalesOrderItemMapper;
import com.aimes.modules.order.mapper.BizSalesOrderMapper;
import com.aimes.modules.system.service.SysNotificationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单变更服务 (新流程):
 *   1) 用户点"申请变更"填类型+原因 → create() 拍原快照 status=draft, NOT 启动审批流
 *   2) 用户进编辑页改产品/数量 → saveDraft() 多次覆盖 change_content
 *   3) 用户点"提交变更审批" → submitChange() 启动通用审批流, status=pending
 *   4) 审批人在我的待办看 diff → approve/reject
 *   5) approve → applyApproved() 调 BizSalesOrderService.update() 真正改订单 + 级联;
 *      若失败 → status=failed, 通知申请人
 *   6) reject → applyRejected() 状态=rejected, 订单数据没动
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BizOrderChangeService extends ServiceImpl<BizOrderChangeMapper, BizOrderChange> {

    public static final String BUSINESS_TYPE = "order_change";

    /** 变更类型 → 中文标签, 通知/审批单号显示用 */
    private static final Map<String, String> CHANGE_TYPE_LABEL = Map.ofEntries(
            Map.entry("qty_increase", "数量增加"),
            Map.entry("qty_decrease", "数量减少"),
            Map.entry("date_change", "交期变更"),
            Map.entry("cancel", "订单取消"),
            Map.entry("product_replace", "产品替换"),
            Map.entry("add_product", "新增产品行"),
            Map.entry("add_item", "新增产品行"),
            Map.entry("quantity", "数量变更"),
            Map.entry("delivery_date", "交期变更"),
            Map.entry("price", "价格变更"),
            Map.entry("product", "产品变更"),
            Map.entry("other", "其他变更")
    );

    public static String changeTypeLabel(String t) {
        if (t == null || t.isEmpty()) return "订单变更";
        return CHANGE_TYPE_LABEL.getOrDefault(t, t);
    }

    private final BizSalesOrderMapper salesOrderMapper;
    private final BizSalesOrderItemMapper salesOrderItemMapper;
    private final BizCustomerMapper customerMapper;
    private final BizMaterialMapper materialMapper;
    private final BizApprovalService approvalService;
    private final SysNotificationService notificationService;
    // 用 Spring 容器里那个已注册 JavaTimeModule 的 ObjectMapper, 否则 LocalDateTime 序列化报错
    private final ObjectMapper objectMapper;

    // BizSalesOrderService 通过 @Lazy 注入避免循环依赖 (它也注入了 approvalService 等本服务依赖的东西)
    @Lazy
    @Autowired
    private BizSalesOrderService salesOrderService;

    public PageResult<BizOrderChange> getPage(Long orderId, String status, int pageNum, int pageSize) {
        LambdaQueryWrapper<BizOrderChange> wrapper = new LambdaQueryWrapper<BizOrderChange>()
                .eq(orderId != null, BizOrderChange::getOrderId, orderId)
                .eq(StringUtils.hasText(status), BizOrderChange::getStatus, status)
                .orderByDesc(BizOrderChange::getCreateTime);
        Page<BizOrderChange> page = page(new Page<>(pageNum, pageSize), wrapper);
        return new PageResult<>(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    public List<BizOrderChange> listByOrderId(Long orderId) {
        return list(new LambdaQueryWrapper<BizOrderChange>()
                .eq(BizOrderChange::getOrderId, orderId)
                .orderByDesc(BizOrderChange::getCreateTime));
    }

    /**
     * 1. 创建变更 (申请变更) — 拍原快照, 进 draft 态, NOT 启动审批
     */
    @Transactional
    public Long create(BizOrderChange change) {
        if (change.getOrderId() == null) throw new BusinessException("订单 id 不能为空");
        // 同一订单已存在 draft 或 pending 时, 拒绝再开
        long ongoing = count(new LambdaQueryWrapper<BizOrderChange>()
                .eq(BizOrderChange::getOrderId, change.getOrderId())
                .in(BizOrderChange::getStatus, List.of("draft", "pending")));
        if (ongoing > 0) throw new BusinessException("该订单已有进行中的变更, 请先处理完再开新变更");

        change.setStatus("draft");
        change.setOriginalSnapshot(buildSnapshot(change.getOrderId()));
        change.setChangeContent(null);
        save(change);
        return change.getId();
    }

    /**
     * 2. 草稿保存 — 用户在编辑页修改后写入 change_content; 允许多次覆盖
     * @param payload {"order": {...}, "items": [{...}]}
     */
    @Transactional
    public void saveDraft(Long changeId, Map<String, Object> payload) {
        BizOrderChange change = getById(changeId);
        if (change == null) throw new BusinessException("变更不存在");
        if (!"draft".equals(change.getStatus()))
            throw new BusinessException("只有草稿状态的变更可以保存; 当前状态: " + change.getStatus());
        try {
            change.setChangeContent(objectMapper.writeValueAsString(payload));
            updateById(change);
        } catch (Exception e) {
            throw new BusinessException("保存草稿失败: " + e.getMessage());
        }
    }

    /**
     * 3. 提交审批 — 草稿 → pending, 启动通用审批流, 推送通知
     */
    @Transactional
    public Long submitChange(Long changeId) {
        BizOrderChange change = getById(changeId);
        if (change == null) throw new BusinessException("变更不存在");
        if (!"draft".equals(change.getStatus()))
            throw new BusinessException("只有草稿状态的变更可以提交审批");
        if (!StringUtils.hasText(change.getChangeContent()))
            throw new BusinessException("尚未保存变更内容, 请先编辑后再提交");

        change.setStatus("pending");
        updateById(change);

        Long instanceId = null;
        try {
            Long applicantId = SecurityUtil.getCurrentUserId();
            if (applicantId == null) applicantId = change.getCreateBy();
            instanceId = approvalService.startApproval(BUSINESS_TYPE, change.getId(), applicantId);

            if (instanceId != null) {
                BizSalesOrder order = salesOrderMapper.selectById(change.getOrderId());
                String orderNo = order != null ? order.getOrderNo() : ("OC-" + change.getId());
                String type = change.getChangeType() != null ? change.getChangeType() : "";
                String title = "订单变更审批";
                String content = "订单 [" + orderNo + "] 申请变更"
                        + (type.isEmpty() ? "" : " (" + changeTypeLabel(type) + ")")
                        + "; 请到「我的待办」查看变更明细并审批";
                java.util.Set<Long> approverIds = approvalService.resolveCurrentApproverUserIds(instanceId);
                if (approverIds.isEmpty()) {
                    notificationService.notifyRole("admin", title, content, "approval", BUSINESS_TYPE, instanceId);
                } else {
                    for (Long uid : approverIds) {
                        notificationService.notifyUser(uid, title, content, "approval", BUSINESS_TYPE, instanceId);
                    }
                }
            }
        } catch (Exception ignore) {
            // 审批流启动失败 / 通知失败不阻塞提交; 没配 flow 时 startApproval 返回 null
        }
        return instanceId;
    }

    /**
     * 4a. 草稿放弃 — 直接删除变更记录
     */
    @Transactional
    public void discardDraft(Long changeId) {
        BizOrderChange change = getById(changeId);
        if (change == null) return;
        if (!"draft".equals(change.getStatus()))
            throw new BusinessException("只能放弃草稿状态的变更");
        removeById(changeId);
    }

    /**
     * 4b. 直接审批 (旧路径兼容): 通过订单详情页"变更记录"tab 上的 approve 按钮调用
     */
    @Transactional
    public void approve(Long id, String remark) {
        BizOrderChange change = getById(id);
        if (change == null) throw new BusinessException("变更不存在");
        if (!"pending".equals(change.getStatus())) throw new BusinessException("只有待审批的变更可以审批");
        Long currentUserId = SecurityUtil.getCurrentUserId();
        BizApprovalInstance instance = approvalService.getByBusiness(BUSINESS_TYPE, id);
        if (instance != null) {
            approvalService.approve(instance.getId(), currentUserId, remark);
        } else {
            applyApproved(change, currentUserId, remark);
        }
    }

    @Transactional
    public void reject(Long id, String remark) {
        BizOrderChange change = getById(id);
        if (change == null) throw new BusinessException("变更不存在");
        if (!"pending".equals(change.getStatus())) throw new BusinessException("只有待审批的变更可以驳回");
        Long currentUserId = SecurityUtil.getCurrentUserId();
        BizApprovalInstance instance = approvalService.getByBusiness(BUSINESS_TYPE, id);
        if (instance != null) {
            approvalService.reject(instance.getId(), currentUserId, remark);
        } else {
            applyRejected(change, remark);
        }
    }

    /**
     * 5. diff 视图: 返回原快照 + 提议状态 + 简化对比
     */
    public Map<String, Object> getDiff(Long changeId) {
        BizOrderChange change = getById(changeId);
        if (change == null) throw new BusinessException("变更不存在");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("change", change);
        try {
            if (StringUtils.hasText(change.getOriginalSnapshot())) {
                result.put("original", objectMapper.readValue(change.getOriginalSnapshot(), Map.class));
            }
            if (StringUtils.hasText(change.getChangeContent())) {
                result.put("proposed", objectMapper.readValue(change.getChangeContent(), Map.class));
            }
        } catch (Exception e) {
            log.warn("getDiff parse failed for change#{}: {}", changeId, e.getMessage());
        }
        return result;
    }

    // ============== 事件监听 ==============

    @EventListener
    @Transactional
    public void onApprovalCompleted(ApprovalCompletedEvent event) {
        if (!BUSINESS_TYPE.equals(event.getBusinessType())) return;
        log.info("[OrderChange] ApprovalCompletedEvent received: businessId={}, approverId={}",
                event.getBusinessId(), event.getApproverId());
        BizOrderChange change = getById(event.getBusinessId());
        if (change == null || !"pending".equals(change.getStatus())) return;
        applyApproved(change, event.getApproverId(), event.getRemark());
    }

    @EventListener
    @Transactional
    public void onApprovalRejected(ApprovalRejectedEvent event) {
        if (!BUSINESS_TYPE.equals(event.getBusinessType())) return;
        log.info("[OrderChange] ApprovalRejectedEvent received: businessId={}", event.getBusinessId());
        BizOrderChange change = getById(event.getBusinessId());
        if (change == null || !"pending".equals(change.getStatus())) return;
        applyRejected(change, event.getRemark());
    }

    // ============== 终态处理 ==============

    /**
     * 审批通过: 解析 change_content, 调 BizSalesOrderService.update() 真正改订单 + 级联.
     * cascade 异常 (例如 newQty < producedQty) → 改成 failed + 通知申请人, 不抛出
     */
    private void applyApproved(BizOrderChange change, Long approverId, String remark) {
        try {
            applyChangeContent(change);
            change.setStatus("approved");
            change.setApprovedAt(LocalDateTime.now());
            if (approverId != null) change.setApprovedBy(approverId);
            if (StringUtils.hasText(remark)) change.setApproveRemark(remark);
            updateById(change);
            log.info("[OrderChange] change#{} approved & applied to order#{}", change.getId(), change.getOrderId());
        } catch (Exception e) {
            log.error("[OrderChange] change#{} approved but cascade failed: {}", change.getId(), e.getMessage(), e);
            change.setStatus("failed");
            change.setApprovedAt(LocalDateTime.now());
            change.setApproveRemark("审批通过但应用失败: " + e.getMessage());
            updateById(change);
            // 通知申请人
            if (change.getCreateBy() != null) {
                notificationService.notifyUser(change.getCreateBy(),
                        "订单变更应用失败",
                        "变更已审批通过但应用到订单时失败: " + e.getMessage()
                                + "; 请重新发起变更或联系管理员",
                        "alert", BUSINESS_TYPE, change.getId());
            }
        }
    }

    private void applyRejected(BizOrderChange change, String remark) {
        change.setStatus("rejected");
        change.setApprovedAt(LocalDateTime.now());
        if (StringUtils.hasText(remark)) change.setApproveRemark(remark);
        updateById(change);
        // 通知申请人
        if (change.getCreateBy() != null) {
            notificationService.notifyUser(change.getCreateBy(),
                    "订单变更已驳回",
                    "你的订单变更被驳回" + (StringUtils.hasText(remark) ? ": " + remark : ""),
                    "alert", BUSINESS_TYPE, change.getId());
        }
    }

    // ============== 辅助 ==============

    /** 拍当前订单 + items 的快照 JSON; 同步带上客户名/物料名等 join 字段, 让 diff 视图显示友好 */
    private String buildSnapshot(Long orderId) {
        try {
            BizSalesOrder order = salesOrderMapper.selectById(orderId);
            if (order == null) throw new BusinessException("订单不存在");
            List<BizSalesOrderItem> items = salesOrderItemMapper.selectList(
                    new LambdaQueryWrapper<BizSalesOrderItem>().eq(BizSalesOrderItem::getOrderId, orderId));

            // 用 LinkedHashMap 装订单字段, 方便加 customerName 这类非 entity 字段
            Map<String, Object> orderMap = objectMapper.convertValue(order, LinkedHashMap.class);
            if (order.getCustomerId() != null) {
                BizCustomer cust = customerMapper.selectById(order.getCustomerId());
                if (cust != null) orderMap.put("customerName", cust.getCustomerName());
            }

            // items 的 productName/productCode/spec/unit 也补上
            java.util.Set<Long> productIds = items.stream()
                    .map(BizSalesOrderItem::getProductId).filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toSet());
            Map<Long, BizMaterial> materialMap = productIds.isEmpty()
                    ? new java.util.HashMap<>()
                    : materialMapper.selectBatchIds(productIds).stream()
                            .collect(java.util.stream.Collectors.toMap(BizMaterial::getId, m -> m));
            List<Map<String, Object>> itemMaps = new java.util.ArrayList<>();
            for (BizSalesOrderItem it : items) {
                Map<String, Object> m = objectMapper.convertValue(it, LinkedHashMap.class);
                BizMaterial mat = it.getProductId() != null ? materialMap.get(it.getProductId()) : null;
                if (mat != null) {
                    m.put("productName", mat.getMaterialName());
                    m.put("productCode", mat.getMaterialNo());
                    m.put("productNo", mat.getMaterialNo());
                    m.put("spec", mat.getSpec());
                    m.put("productSpec", mat.getSpec());
                    m.put("unit", mat.getUnit());
                }
                itemMaps.add(m);
            }

            Map<String, Object> snap = new LinkedHashMap<>();
            snap.put("order", orderMap);
            snap.put("items", itemMaps);
            return objectMapper.writeValueAsString(snap);
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            throw new BusinessException("生成订单快照失败: " + e.getMessage());
        }
    }

    /** 解析 change_content 并调 BizSalesOrderService.update() 应用变更 (复用既有级联) */
    @SuppressWarnings("unchecked")
    private void applyChangeContent(BizOrderChange change) {
        if (!StringUtils.hasText(change.getChangeContent())) {
            throw new BusinessException("变更内容为空, 无法应用");
        }
        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(change.getChangeContent(), Map.class);
        } catch (Exception e) {
            throw new BusinessException("变更内容 JSON 解析失败: " + e.getMessage());
        }
        Map<String, Object> orderMap = (Map<String, Object>) payload.get("order");
        List<Map<String, Object>> itemsMap = (List<Map<String, Object>>) payload.get("items");
        if (orderMap == null || itemsMap == null) throw new BusinessException("变更内容缺少 order 或 items 字段");

        BizSalesOrder order = objectMapper.convertValue(orderMap, BizSalesOrder.class);
        List<BizSalesOrderItem> items = new java.util.ArrayList<>();
        for (Map<String, Object> im : itemsMap) {
            items.add(objectMapper.convertValue(im, BizSalesOrderItem.class));
        }
        // 强制为目标订单 id, 防止前端把 id 改了打错; 开 INTERNAL_APPLY 放行 update() 内的 draft/unlock 校验
        BizSalesOrderService.INTERNAL_APPLY.set(true);
        try {
            salesOrderService.update(change.getOrderId(), order, items);
        } finally {
            BizSalesOrderService.INTERNAL_APPLY.remove();
        }
    }
}
