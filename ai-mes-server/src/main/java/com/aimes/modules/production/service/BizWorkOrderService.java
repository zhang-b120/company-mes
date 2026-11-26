package com.aimes.modules.production.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.PageResult;
import com.aimes.common.utils.CodeUtil;
import com.aimes.modules.purchase.entity.BizPurchaseOrder;
import com.aimes.modules.purchase.entity.BizPurchaseOrderItem;
import com.aimes.modules.bom.entity.BizBomItem;
import com.aimes.modules.bom.entity.BizBomSubstitute;
import com.aimes.modules.bom.entity.BizBomVersion;
import com.aimes.modules.bom.entity.BizProcess;
import com.aimes.modules.bom.entity.BizProcessRoute;
import com.aimes.modules.bom.entity.BizProcessRouteStep;
import com.aimes.modules.bom.mapper.BizBomItemMapper;
import com.aimes.modules.bom.mapper.BizBomSubstituteMapper;
import com.aimes.modules.bom.mapper.BizBomVersionMapper;
import com.aimes.modules.bom.mapper.BizProcessMapper;
import com.aimes.modules.bom.mapper.BizProcessRouteMapper;
import com.aimes.modules.bom.mapper.BizProcessRouteStepMapper;
import com.aimes.modules.mrp.entity.BizProductionPlanItemStep;
import com.aimes.modules.mrp.mapper.BizProductionPlanItemStepMapper;
import com.aimes.modules.inventory.entity.BizInvHistory;
import com.aimes.modules.inventory.entity.BizInvHistoryItem;
import com.aimes.modules.inventory.entity.BizInvReservation;
import com.aimes.modules.inventory.entity.BizInvTask;
import com.aimes.modules.inventory.entity.BizInvTaskItem;
import com.aimes.modules.inventory.mapper.BizInvHistoryItemMapper;
import com.aimes.modules.inventory.mapper.BizInvHistoryMapper;
import com.aimes.modules.inventory.mapper.BizInvReservationMapper;
import com.aimes.modules.inventory.service.InventoryService;
import com.aimes.modules.master.entity.BizMaterial;
import com.aimes.modules.master.entity.BizProduct;
import com.aimes.modules.master.mapper.BizMaterialMapper;
import com.aimes.modules.master.mapper.BizProductMapper;
import com.aimes.modules.order.service.BizSalesOrderService;
import com.aimes.modules.team.entity.BizTeam;
import com.aimes.modules.team.entity.BizTeamMember;
import com.aimes.modules.team.mapper.BizTeamMapper;
import com.aimes.modules.team.mapper.BizTeamMemberMapper;
import com.aimes.modules.system.entity.SysUser;
import com.aimes.modules.system.mapper.SysUserMapper;
import com.aimes.modules.production.entity.BizWoBomSnapshot;
import com.aimes.modules.production.entity.BizWoChange;
import com.aimes.modules.production.entity.BizWoProcessTask;
import com.aimes.modules.production.entity.BizWorkOrder;
import com.aimes.modules.production.mapper.BizWoBomSnapshotMapper;
import com.aimes.modules.production.mapper.BizWoProcessTaskMapper;
import com.aimes.modules.production.mapper.BizWorkOrderMapper;
import com.aimes.modules.system.service.SysNotificationService;
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

import com.aimes.modules.order.entity.BizSalesOrder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 生产工单服务
 * 工单状态流转: pending(待排产) -> scheduled(已排产) -> in_progress(生产中) -> completed(已完工) / cancelled(已取消)
 * 创建工单时自动快照BOM(计算物料需求)和生成工序任务(来自工艺路线)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BizWorkOrderService extends ServiceImpl<BizWorkOrderMapper, BizWorkOrder> {

    private final BizWoProcessTaskMapper taskMapper;
    private final BizWoBomSnapshotMapper bomSnapshotMapper;
    private final BizProcessRouteMapper processRouteMapper;
    private final BizProcessMapper processMapper;
    private final BizProcessRouteStepMapper routeStepMapper;
    private final BizBomVersionMapper bomVersionMapper;
    private final BizBomItemMapper bomItemMapper;
    private final BizBomSubstituteMapper bomSubstituteMapper;
    private final InventoryService inventoryService;
    private final BizMaterialMapper materialMapper;
    private final BizProductMapper productMapper;
    private final BizInvReservationMapper reservationMapper;
    private final com.aimes.modules.inventory.mapper.BizInvTaskItemMapper invTaskItemMapper;
    private final BizInvHistoryMapper invHistoryMapper;
    private final BizInvHistoryItemMapper invHistoryItemMapper;
    @Autowired
    @Lazy
    private com.aimes.modules.inventory.service.BizInvTaskService invTaskService;
    private final SysNotificationService notificationService;
    private final BizSalesOrderService salesOrderService;
    private final com.aimes.modules.order.mapper.BizOrderChangeMapper orderChangeMapper;
    private final BizTeamMapper teamMapper;
    private final BizTeamMemberMapper teamMemberMapper;
    private final SysUserMapper userMapper;
    private final com.aimes.modules.production.mapper.BizWoChangeMapper woChangeMapper;
    private final SysBizConfigService bizConfigService;
    @Autowired
    @Lazy
    private com.aimes.modules.purchase.service.PurchaseRequestService purchaseRequestService;
    private final com.aimes.modules.system.service.SysConfigService sysConfigService;
    @Autowired
    @Lazy
    private com.aimes.modules.outsource.service.OutsourceOrderService outsourceOrderService;
    @Autowired
    @Lazy
    private com.aimes.modules.outsource.service.OutsourceChainService outsourceChainService;
    private final com.aimes.modules.master.mapper.BizSupplierMapper supplierMapper;
    private final BizProductionPlanItemStepMapper planItemStepMapper;
    private final com.aimes.modules.mrp.mapper.BizProductionPlanItemMapper planItemMapper;
    private final com.aimes.modules.mrp.mapper.BizProductionPlanMapper productionPlanMapper;
    private final com.aimes.modules.aftersale.mapper.BizServiceOrderPartMapper serviceOrderPartMapper;
    private final com.aimes.modules.production.mapper.BizSchedulingMapper schedulingMapper;
    private final com.aimes.modules.purchase.mapper.BizPurchaseRequestMapper purchaseRequestMapper;
    private final com.aimes.modules.purchase.mapper.BizPurchaseOrderItemMapper purchaseOrderItemMapper;
    private final com.aimes.modules.purchase.mapper.BizPurchaseOrderMapper purchaseOrderMapper;
    private final com.aimes.modules.purchase.mapper.BizPurchaseReceiptMapper purchaseReceiptMapper;

    /**
     * 分页查询
     *
     * @param assignedUserId 不为 null 时只返回该用户被指派了工序任务的工单（数据权限 self 场景）
     */
    public PageResult<Map<String, Object>> getPage(String keyword, String status, String spec, String orderNo, Long assignedUserId, Map<String, String> extFieldFilters, int pageNum, int pageSize) {
        return getPage(keyword, null, null, status, spec, orderNo, assignedUserId, extFieldFilters, pageNum, pageSize);
    }

    public PageResult<Map<String, Object>> getPage(String keyword, String woNo, String productName, String status, String spec, String orderNo, Long assignedUserId, Map<String, String> extFieldFilters, int pageNum, int pageSize) {
        return getPage(keyword, woNo, null, productName, status, spec, orderNo, null, assignedUserId, extFieldFilters, pageNum, pageSize);
    }

    public PageResult<Map<String, Object>> getPage(String keyword, String woNo, String productName, String status, String spec, String orderNo, Long orderId, Long assignedUserId, Map<String, String> extFieldFilters, int pageNum, int pageSize) {
        return getPage(keyword, woNo, null, productName, status, spec, orderNo, orderId, null, assignedUserId, extFieldFilters, pageNum, pageSize);
    }

    public PageResult<Map<String, Object>> getPage(String keyword, String woNo, String productNo, String productName, String status, String spec, String orderNo, Long orderId, Long assignedUserId, Map<String, String> extFieldFilters, int pageNum, int pageSize) {
        return getPage(keyword, woNo, productNo, productName, status, spec, orderNo, orderId, null, assignedUserId, extFieldFilters, pageNum, pageSize);
    }

    public PageResult<Map<String, Object>> getPage(String keyword, String woNo, String productNo, String productName, String status, String spec, String orderNo, Long orderId, Boolean overdue, Long assignedUserId, Map<String, String> extFieldFilters, int pageNum, int pageSize) {
        LambdaQueryWrapper<BizWorkOrder> wrapper = new LambdaQueryWrapper<BizWorkOrder>()
                .and(StringUtils.hasText(keyword), w -> w
                        .like(BizWorkOrder::getWoNo, keyword))
                .like(StringUtils.hasText(woNo), BizWorkOrder::getWoNo, woNo)
                .eq(StringUtils.hasText(status), BizWorkOrder::getStatus, status)
                .eq(orderId != null, BizWorkOrder::getOrderId, orderId)
                .orderByDesc(BizWorkOrder::getCreateTime);

        // 按销售订单号模糊筛选
        if (StringUtils.hasText(orderNo)) {
            List<Long> matchedOrderIds = salesOrderService.list(
                    new LambdaQueryWrapper<BizSalesOrder>().like(BizSalesOrder::getOrderNo, orderNo))
                    .stream().map(BizSalesOrder::getId).collect(Collectors.toList());
            if (matchedOrderIds.isEmpty()) {
                return new PageResult<>(List.of(), 0L, pageNum, pageSize);
            }
            wrapper.in(BizWorkOrder::getOrderId, matchedOrderIds);
        }

        // 按规格型号筛选：先查物料表匹配的ID，再用 IN 过滤
        // 支持单值(NBR / Ø15mm) 与 复合(压力等级:300 材质:NBR) 两种输入
        if (StringUtils.hasText(spec)) {
            LambdaQueryWrapper<BizMaterial> sw = new LambdaQueryWrapper<>();
            com.aimes.common.utils.SpecQueryUtil.applyToBizMaterial(sw, spec);
            List<Long> matchedProductIds = materialMapper.selectList(sw)
                    .stream().map(BizMaterial::getId).collect(Collectors.toList());
            if (matchedProductIds.isEmpty()) {
                return new PageResult<>(List.of(), 0L, pageNum, pageSize);
            }
            wrapper.in(BizWorkOrder::getProductId, matchedProductIds);
        }
        // 按产品名称筛选：先查物料表匹配的ID，再用 IN 过滤
        if (StringUtils.hasText(productName)) {
            List<Long> matchedProductIds = materialMapper.selectList(
                    new LambdaQueryWrapper<BizMaterial>().like(BizMaterial::getMaterialName, productName))
                    .stream().map(BizMaterial::getId).collect(Collectors.toList());
            if (matchedProductIds.isEmpty()) {
                return new PageResult<>(List.of(), 0L, pageNum, pageSize);
            }
            wrapper.in(BizWorkOrder::getProductId, matchedProductIds);
        }
        // 延期/逾期过滤:当前交期 < 今天 且 未完工/未取消
        if (Boolean.TRUE.equals(overdue)) {
            wrapper.lt(BizWorkOrder::getDeliveryDate, java.time.LocalDate.now())
                   .notIn(BizWorkOrder::getStatus, java.util.List.of("completed", "cancelled"));
        }
        // 按产品编号筛选
        if (StringUtils.hasText(productNo)) {
            List<Long> matchedProductIds = materialMapper.selectList(
                    new LambdaQueryWrapper<BizMaterial>().like(BizMaterial::getMaterialNo, productNo))
                    .stream().map(BizMaterial::getId).collect(Collectors.toList());
            if (matchedProductIds.isEmpty()) {
                return new PageResult<>(List.of(), 0L, pageNum, pageSize);
            }
            wrapper.in(BizWorkOrder::getProductId, matchedProductIds);
        }
        if (extFieldFilters != null && !extFieldFilters.isEmpty()) {
            extFieldFilters.forEach((k, v) -> {
                if (StringUtils.hasText(v)) {
                    wrapper.apply("JSON_UNQUOTE(JSON_EXTRACT(ext_fields, '$." + k + "')) LIKE {0}", "%" + v + "%");
                }
            });
        }

        // 数据权限：只查被指派到该用户（直接指派 OR 班组成员）的工单
        if (assignedUserId != null) {
            List<Long> woIds = getAssignedWorkOrderIds(assignedUserId);
            if (woIds.isEmpty()) {
                return new PageResult<>(List.of(), 0L, pageNum, pageSize);
            }
            wrapper.in(BizWorkOrder::getId, woIds);
        }

        Page<BizWorkOrder> page = page(new Page<>(pageNum, pageSize), wrapper);

        // 批量加载关联实体名称
        List<BizWorkOrder> records = page.getRecords();
        Set<Long> productIds = records.stream().map(BizWorkOrder::getProductId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> orderIds = records.stream().map(BizWorkOrder::getOrderId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<BizMaterial> productList = productIds.isEmpty() ? List.of() : materialMapper.selectBatchIds(productIds);
        Map<Long, String> productNameMap = productList.stream().collect(Collectors.toMap(BizMaterial::getId, BizMaterial::getMaterialName, (a, b) -> a));
        Map<Long, String> productNoMap = productList.stream().collect(Collectors.toMap(BizMaterial::getId, m -> m.getMaterialNo() != null ? m.getMaterialNo() : "", (a, b) -> a));
        Map<Long, String> productSpecMap = productList.stream().collect(Collectors.toMap(BizMaterial::getId, m -> m.getSpec() != null ? m.getSpec() : "", (a, b) -> a));
        Map<Long, Long> productDefaultWhMap = productList.stream().filter(m -> m.getDefaultWarehouseId() != null)
                .collect(Collectors.toMap(BizMaterial::getId, BizMaterial::getDefaultWarehouseId, (a, b) -> a));
        Map<Long, String> orderNoMap = orderIds.isEmpty() ? Map.of() :
                salesOrderService.listByIds(orderIds).stream().collect(Collectors.toMap(o -> o.getId(), o -> o.getOrderNo(), (a, b) -> a));

        List<Map<String, Object>> list = records.stream().map(wo -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", wo.getId());
            map.put("woNo", wo.getWoNo());
            map.put("orderId", wo.getOrderId());
            map.put("orderNo", wo.getOrderId() != null ? orderNoMap.get(wo.getOrderId()) : "");
            map.put("orderItemId", wo.getOrderItemId());
            map.put("productId", wo.getProductId());
            map.put("productName", productNameMap.getOrDefault(wo.getProductId(), ""));
            map.put("productNo", productNoMap.getOrDefault(wo.getProductId(), ""));
            map.put("productSpec", productSpecMap.getOrDefault(wo.getProductId(), ""));
            map.put("productDefaultWarehouseId", productDefaultWhMap.get(wo.getProductId()));
            map.put("planQty", wo.getPlanQty());
            map.put("completedQty", wo.getCompletedQty());
            map.put("stockInTaskQty", wo.getStockInTaskQty());
            map.put("stockInQty", wo.getStockInQty());
            map.put("invTaskStatus", wo.getInvTaskStatus());
            map.put("productionMode", wo.getProductionMode());
            map.put("deliveryDate", wo.getDeliveryDate());
            map.put("originalDeliveryDate", wo.getOriginalDeliveryDate());
            map.put("priority", wo.getPriority());
            map.put("status", wo.getStatus());
            map.put("remark", wo.getRemark());
            map.put("extFields", wo.getExtFields());
            map.put("createTime", wo.getCreateTime());

            // 工序任务数
            long taskCount = taskMapper.selectCount(
                    new LambdaQueryWrapper<BizWoProcessTask>().eq(BizWoProcessTask::getWorkOrderId, wo.getId()));
            map.put("taskCount", taskCount);

            return map;
        }).collect(Collectors.toList());

        return new PageResult<>(list, page.getTotal(), pageNum, pageSize);
    }

    /**
     * 统计数据（待排产/生产中/已完工/延期）
     *
     * @param assignedUserId 不为 null 时只统计该用户相关工单（数据权限 self 场景）
     */
    public Map<String, Object> getStatistics(Long assignedUserId) {
        Map<String, Object> stats = new LinkedHashMap<>();

        // 数据权限过滤
        List<Long> scopedWoIds = null;
        if (assignedUserId != null) {
            scopedWoIds = getAssignedWorkOrderIds(assignedUserId);
            if (scopedWoIds.isEmpty()) {
                stats.put("pending", 0L);
                stats.put("inProgress", 0L);
                stats.put("completed", 0L);
                stats.put("overdue", 0L);
                return stats;
            }
        }
        final List<Long> woIds = scopedWoIds;

        long pending = count(new LambdaQueryWrapper<BizWorkOrder>()
                .eq(BizWorkOrder::getStatus, "pending")
                .in(woIds != null, BizWorkOrder::getId, woIds != null ? woIds : List.of()));
        stats.put("pending", pending);

        long inProgress = count(new LambdaQueryWrapper<BizWorkOrder>()
                .eq(BizWorkOrder::getStatus, "in_progress")
                .in(woIds != null, BizWorkOrder::getId, woIds != null ? woIds : List.of()));
        stats.put("inProgress", inProgress);

        long completed = count(new LambdaQueryWrapper<BizWorkOrder>()
                .eq(BizWorkOrder::getStatus, "completed")
                .in(woIds != null, BizWorkOrder::getId, woIds != null ? woIds : List.of()));
        stats.put("completed", completed);

        // 延期：未完工/未取消且交期 < 今日
        long overdue = count(new LambdaQueryWrapper<BizWorkOrder>()
                .notIn(BizWorkOrder::getStatus, List.of("completed", "cancelled"))
                .lt(BizWorkOrder::getDeliveryDate, LocalDate.now())
                .in(woIds != null, BizWorkOrder::getId, woIds != null ? woIds : List.of()));
        stats.put("overdue", overdue);

        return stats;
    }

    /**
     * 获取用户相关工单 ID：直接指派给该用户（assignType=person）OR 指派给其所在班组（assignType=team）
     */
    private List<Long> getAssignedWorkOrderIds(Long userId) {
        // 用户所在班组
        List<Long> userTeamIds = teamMemberMapper.selectList(
                new LambdaQueryWrapper<BizTeamMember>()
                        .eq(BizTeamMember::getUserId, userId)
                        .select(BizTeamMember::getTeamId))
                .stream().map(BizTeamMember::getTeamId)
                .filter(Objects::nonNull).distinct()
                .collect(Collectors.toList());

        // 工序任务：直接指派 OR 班组指派
        final List<Long> teamIds = userTeamIds;
        LambdaQueryWrapper<BizWoProcessTask> taskWrapper = new LambdaQueryWrapper<BizWoProcessTask>()
                .and(w -> {
                    w.eq(BizWoProcessTask::getAssignType, "person")
                            .eq(BizWoProcessTask::getAssignedUserId, userId);
                    if (!teamIds.isEmpty()) {
                        w.or(tw -> tw.eq(BizWoProcessTask::getAssignType, "team")
                                .in(BizWoProcessTask::getAssignedTeamId, teamIds));
                    }
                })
                .select(BizWoProcessTask::getWorkOrderId);

        return taskMapper.selectList(taskWrapper).stream()
                .map(BizWoProcessTask::getWorkOrderId)
                .filter(Objects::nonNull).distinct()
                .collect(Collectors.toList());
    }

    /**
     * 通过工单编号查询详情（供关联单据跳转使用）
     */
    public Map<String, Object> getDetailByWoNo(String woNo) {
        BizWorkOrder wo = getOne(new LambdaQueryWrapper<BizWorkOrder>()
                .eq(BizWorkOrder::getWoNo, woNo).last("LIMIT 1"));
        if (wo == null) throw new BusinessException("工单不存在: " + woNo);
        return getDetail(wo.getId());
    }

    /**
     * 工单详情（含工序任务 + BOM快照）
     */
    public Map<String, Object> getDetail(Long id) {
        BizWorkOrder wo = getById(id);
        if (wo == null) {
            throw new BusinessException("工单不存在");
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", wo.getId());
        detail.put("woNo", wo.getWoNo());
        detail.put("orderId", wo.getOrderId());
        detail.put("extFields", wo.getExtFields());
        if (wo.getOrderId() != null) {
            BizSalesOrder so = salesOrderService.getById(wo.getOrderId());
            detail.put("orderNo", so != null ? so.getOrderNo() : "");
        }
        detail.put("orderItemId", wo.getOrderItemId());
        detail.put("productId", wo.getProductId());
        if (wo.getProductId() != null) {
            BizMaterial product = materialMapper.selectById(wo.getProductId());
            if (product != null) {
                detail.put("productName", product.getMaterialName());
                detail.put("productNo", product.getMaterialNo());
                detail.put("productSpec", product.getSpec());
                detail.put("productDefaultWarehouseId", product.getDefaultWarehouseId());
                detail.put("productImages", product.getImages());
                detail.put("productAttachmentUrls", product.getAttachmentUrls());
            } else {
                // 回退查 biz_product（兼容旧数据）
                BizProduct bizProduct = productMapper.selectById(wo.getProductId());
                detail.put("productName", bizProduct != null ? bizProduct.getProductName() : "");
                detail.put("productNo", bizProduct != null ? bizProduct.getProductNo() : "");
                detail.put("productSpec", bizProduct != null ? bizProduct.getSpec() : "");
                detail.put("productDefaultWarehouseId", null);
                detail.put("productImages", bizProduct != null ? parseJsonList(bizProduct.getImageUrls()) : null);
                detail.put("productAttachmentUrls", bizProduct != null ? parseJsonList(bizProduct.getDrawingUrls()) : null);
            }
        }
        detail.put("planQty", wo.getPlanQty());
        detail.put("completedQty", wo.getCompletedQty());
        detail.put("stockInTaskQty", wo.getStockInTaskQty());
        detail.put("stockInQty", wo.getStockInQty());
        detail.put("invTaskStatus", wo.getInvTaskStatus());
        detail.put("productionMode", wo.getProductionMode());
        detail.put("deliveryDate", wo.getDeliveryDate());
        detail.put("priority", wo.getPriority());
        detail.put("status", wo.getStatus());
        detail.put("remark", wo.getRemark());
        detail.put("createBy", wo.getCreateBy());
        detail.put("createTime", wo.getCreateTime());

        // 工序任务列表（补充指派人/班组名称）
        List<BizWoProcessTask> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<BizWoProcessTask>()
                        .eq(BizWoProcessTask::getWorkOrderId, id)
                        .orderByAsc(BizWoProcessTask::getStepGroup)
                        .orderByAsc(BizWoProcessTask::getStepNo));

        // 批量查询关联的班组和用户名称（兼容旧数据：表中无冗余名称时回退查询）
        Set<Long> teamIds = tasks.stream()
                .filter(t -> t.getAssignedTeamId() != null && t.getAssignedTeamName() == null)
                .map(BizWoProcessTask::getAssignedTeamId).collect(Collectors.toSet());
        Set<Long> userIds = tasks.stream()
                .filter(t -> t.getAssignedUserId() != null && t.getAssignedUserName() == null)
                .map(BizWoProcessTask::getAssignedUserId).collect(Collectors.toSet());
        Map<Long, String> teamNameMap = teamIds.isEmpty() ? Map.of() :
                teamMapper.selectBatchIds(teamIds).stream()
                        .collect(Collectors.toMap(BizTeam::getId, BizTeam::getTeamName, (a, b) -> a));
        Map<Long, String> userNameMap = userIds.isEmpty() ? Map.of() :
                userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, SysUser::getRealName, (a, b) -> a));

        List<Map<String, Object>> enrichedTasks = new ArrayList<>();
        for (BizWoProcessTask task : tasks) {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("id", task.getId());
            t.put("workOrderId", task.getWorkOrderId());
            t.put("stepNo", task.getStepNo());
            t.put("stepGroup", task.getStepGroup());
            t.put("stepName", task.getStepName());
            t.put("standardTime", task.getStandardTime());
            t.put("piecePrice", task.getPiecePrice());
            t.put("planQty", task.getPlanQty());
            t.put("reportedQty", task.getReportedQty());
            t.put("qualifiedQty", task.getQualifiedQty());
            t.put("defectQty", task.getDefectQty());
            t.put("qcTemplateId", task.getQcTemplateId());
            t.put("assignedTeamId", task.getAssignedTeamId());
            t.put("assignedUserId", task.getAssignedUserId());
            // 优先使用表中冗余名称，旧数据回退动态查询
            t.put("assignedTeamName", task.getAssignedTeamName() != null
                    ? task.getAssignedTeamName()
                    : (task.getAssignedTeamId() != null ? teamNameMap.getOrDefault(task.getAssignedTeamId(), "") : null));
            t.put("assignedUserName", task.getAssignedUserName() != null
                    ? task.getAssignedUserName()
                    : (task.getAssignedUserId() != null ? userNameMap.getOrDefault(task.getAssignedUserId(), "") : null));
            // 指派方式: 优先使用表中存储值，旧数据回退推断
            t.put("assignType", task.getAssignType() != null
                    ? task.getAssignType()
                    : (task.getAssignedTeamId() != null ? "team" : task.getAssignedUserId() != null ? "person" : null));
            t.put("status", task.getStatus());
            t.put("isOutsourced", task.getIsOutsourced());
            t.put("isSemiStock", task.getIsSemiStock());
            t.put("isOutsourceable", task.getIsOutsourceable());
            t.put("virtualMaterialId", task.getVirtualMaterialId());
            t.put("outsourceRequestId", task.getOutsourceRequestId());
            t.put("outsourceOrderId", task.getOutsourceOrderId());
            t.put("outsourceStatus", task.getOutsourceStatus());
            t.put("qcTemplateId", task.getQcTemplateId());
            t.put("qcStatus", task.getQcStatus());
            t.put("qcTaskId", task.getQcTaskId());
            t.put("startTime", task.getStartTime());
            t.put("endTime", task.getEndTime());
            enrichedTasks.add(t);
        }
        detail.put("tasks", enrichedTasks);

        // BOM快照列表（附加物料的unitConfig用于前端换算展示）
        List<BizWoBomSnapshot> bomSnapshots = bomSnapshotMapper.selectList(
                new LambdaQueryWrapper<BizWoBomSnapshot>()
                        .eq(BizWoBomSnapshot::getWorkOrderId, id));
        List<Map<String, Object>> enrichedBom = new ArrayList<>();
        for (BizWoBomSnapshot bom : bomSnapshots) {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("id", bom.getId());
            b.put("workOrderId", bom.getWorkOrderId());
            b.put("materialId", bom.getMaterialId());
            b.put("requiredQty", bom.getRequiredQty());
            b.put("issuedQty", bom.getIssuedQty());
            b.put("issuedTaskQty", bom.getIssuedTaskQty());
            b.put("returnedTaskQty", bom.getReturnedTaskQty());
            b.put("returnedQty", bom.getReturnedQty());
            if (bom.getMaterialId() != null) {
                BizMaterial mat = materialMapper.selectById(bom.getMaterialId());
                if (mat != null) {
                    b.put("materialName", mat.getMaterialName());
                    b.put("materialNo", mat.getMaterialNo());
                    b.put("spec", mat.getSpec());
                    b.put("unit", mat.getUnit());
                    b.put("unitConfig", mat.getUnitConfig());
                }
            }
            enrichedBom.add(b);
        }
        detail.put("bomSnapshots", enrichedBom);

        return detail;
    }

    /**
     * 创建工单
     * 1. 生成工单编号(WO前缀)
     * 2. 根据产品的生效BOM展开，创建BOM快照(biz_wo_bom_snapshot)，记录物料需求
     * 3. 根据产品的生效工艺路线，创建工序任务(biz_wo_process_task)
     * 4. 自动锁定BOM物料库存
     * 5. 将关联订单状态推进到 in_production
     *
     * @param wo 工单信息（需包含productId和planQty）
     */
    @Transactional
    public Map<String, Object> createWorkOrder(BizWorkOrder wo) {
        // 根据配置决定是否自动生成工单号
        boolean autoNo = bizConfigService.getBoolValue("production.wo.auto_no", true);
        if (StringUtils.hasText(wo.getWoNo())) {
            long cnt = count(new LambdaQueryWrapper<BizWorkOrder>()
                    .eq(BizWorkOrder::getWoNo, wo.getWoNo().trim()));
            if (cnt > 0) throw new com.aimes.common.exception.BusinessException("工单号[" + wo.getWoNo() + "]已存在");
            wo.setWoNo(wo.getWoNo().trim());
        } else if (autoNo) {
            wo.setWoNo(CodeUtil.generate("WO"));
        } else {
            throw new com.aimes.common.exception.BusinessException("系统配置要求手动填写工单号，请输入工单号");
        }
        wo.setCompletedQty(BigDecimal.ZERO);
        if (wo.getStatus() == null) {
            wo.setStatus("pending");
        }
        if (wo.getPriority() == null) {
            wo.setPriority(0);
        }
        // 生产模式按是否关联销售订单强制校正: 关联了订单一律按订单生产, 没关联则备库生产
        wo.setProductionMode(wo.getOrderId() != null ? "MTO" : "MTS");
        // 记录初始交期(用于"延期"角标判定 — 后续 modify_date 不更新此字段)
        if (wo.getOriginalDeliveryDate() == null) {
            wo.setOriginalDeliveryDate(wo.getDeliveryDate());
        }
        save(wo);

        // 快照BOM：查询产品的生效BOM版本，展开一级子件作为物料需求
        snapshotBom(wo);
        long bomCount = bomSnapshotMapper.selectCount(
                new LambdaQueryWrapper<BizWoBomSnapshot>().eq(BizWoBomSnapshot::getWorkOrderId, wo.getId()));

        // 生成工序任务：根据产品工艺路线的各工序步骤创建任务
        generateProcessTasks(wo);
        long stepCount = taskMapper.selectCount(
                new LambdaQueryWrapper<BizWoProcessTask>().eq(BizWoProcessTask::getWorkOrderId, wo.getId()));

        // 自动锁料：仅手动创建的工单（非计划下达）才自动锁料
        // 计划下达的工单，锁料在 BizProductionPlanService.transferPlanLocksToWorkOrder 中完成
        if (wo.getPlanItemId() == null) {
            lockMaterialsForWorkOrder(wo);
        }

        // 将关联订单状态推进到 in_production
        salesOrderService.updateOrderProductionStatus(wo.getOrderId());

        Map<String, Object> ret = new HashMap<>();
        ret.put("id", wo.getId());
        ret.put("woNo", wo.getWoNo());
        ret.put("bomCount", bomCount);
        ret.put("stepCount", stepCount);
        ret.put("hasBom", bomCount > 0);
        ret.put("hasRoute", stepCount > 0);
        return ret;
    }

    /**
     * 追加工单：复制源工单的初始状态数据，计划数量由用户填写
     * 工单号格式: 源工单号 + "追" + 三位序号(001, 002, ...)
     */
    @Transactional(rollbackFor = Exception.class)
    public Long appendWorkOrder(Long sourceId, BigDecimal planQty) {
        BizWorkOrder source = getById(sourceId);
        if (source == null) throw new BusinessException("源工单不存在");
        if (planQty == null || planQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("追加工单的计划数量必须大于0");
        }

        // 计算追加序号
        String baseWoNo = source.getWoNo();
        // 如果源工单本身就是追加工单，提取原始工单号
        int idx = baseWoNo.indexOf("追");
        if (idx > 0) {
            baseWoNo = baseWoNo.substring(0, idx);
        }
        long existCount = count(new LambdaQueryWrapper<BizWorkOrder>()
                .likeRight(BizWorkOrder::getWoNo, baseWoNo + "追"));
        String seqNo = String.format("%03d", existCount + 1);
        String newWoNo = baseWoNo + "追" + seqNo;

        // 检查工单号唯一性
        long cnt = count(new LambdaQueryWrapper<BizWorkOrder>()
                .eq(BizWorkOrder::getWoNo, newWoNo));
        if (cnt > 0) throw new BusinessException("工单号[" + newWoNo + "]已存在，请重试");

        // 复制源工单的初始数据
        BizWorkOrder newWo = new BizWorkOrder();
        newWo.setWoNo(newWoNo);
        newWo.setOrderId(source.getOrderId());
        newWo.setOrderItemId(source.getOrderItemId());
        newWo.setProductId(source.getProductId());
        newWo.setPlanQty(planQty);
        newWo.setCompletedQty(BigDecimal.ZERO);
        newWo.setProductionMode(source.getProductionMode());
        newWo.setDeliveryDate(source.getDeliveryDate());
        newWo.setOriginalDeliveryDate(source.getDeliveryDate()); // 追加工单也以源工单交期为初始
        newWo.setPriority(source.getPriority());
        newWo.setStatus("pending");
        newWo.setWorkOrderType(source.getWorkOrderType());
        newWo.setSourceWorkOrderId(sourceId);
        newWo.setRouteId(source.getRouteId()); // 复制工艺路线ID
        newWo.setExtFields(source.getExtFields());
        newWo.setRemark("追加自工单[" + source.getWoNo() + "]");
        save(newWo);

        // 快照BOM + 生成工序任务 + 自动锁料
        snapshotBom(newWo);
        generateProcessTasks(newWo);

        // 检查是否生成了工序任务,没有则直接从源工单复制
        List<BizWoProcessTask> sourceTasks = taskMapper.selectList(
                new LambdaQueryWrapper<BizWoProcessTask>()
                        .eq(BizWoProcessTask::getWorkOrderId, sourceId)
                        .ne(BizWoProcessTask::getStatus, "cancelled")
                        .orderByAsc(BizWoProcessTask::getStepNo));
        List<BizWoProcessTask> newTasks = taskMapper.selectList(
                new LambdaQueryWrapper<BizWoProcessTask>()
                        .eq(BizWoProcessTask::getWorkOrderId, newWo.getId()));

        if (newTasks.isEmpty() && !sourceTasks.isEmpty()) {
            // 无工艺路线 → 直接从源工单复制工序任务(重置状态和数量)
            for (BizWoProcessTask src : sourceTasks) {
                BizWoProcessTask copy = new BizWoProcessTask();
                copy.setWorkOrderId(newWo.getId());
                copy.setStepNo(src.getStepNo());
                copy.setStepGroup(src.getStepGroup());
                copy.setStepName(src.getStepName());
                copy.setStandardTime(src.getStandardTime());
                copy.setPiecePrice(src.getPiecePrice());
                copy.setPlanQty(newWo.getPlanQty());
                copy.setQualifiedQty(BigDecimal.ZERO);
                copy.setDefectQty(BigDecimal.ZERO);
                copy.setReportedQty(BigDecimal.ZERO);
                copy.setAssignType(src.getAssignType());
                copy.setAssignedUserId(src.getAssignedUserId());
                copy.setAssignedUserName(src.getAssignedUserName());
                copy.setAssignedTeamId(src.getAssignedTeamId());
                copy.setAssignedTeamName(src.getAssignedTeamName());
                copy.setStatus("pending");
                copy.setIsSemiStock(src.getIsSemiStock());
                copy.setIsOutsourceable(src.getIsOutsourceable());
                copy.setQcTemplateId(src.getQcTemplateId());
                copy.setQcRequirements(src.getQcRequirements());
                copy.setStartTime(src.getStartTime());
                copy.setEndTime(src.getEndTime());
                taskMapper.insert(copy);
            }
            newTasks = taskMapper.selectList(
                    new LambdaQueryWrapper<BizWoProcessTask>()
                            .eq(BizWoProcessTask::getWorkOrderId, newWo.getId()));
        }

        // 从源工单工序任务补齐质检要求(generateProcessTasks 生成的任务可能缺质检配置)
        Map<Integer, BizWoProcessTask> sourceByStepNo = sourceTasks.stream()
                .filter(t -> t.getStepNo() != null)
                .collect(Collectors.toMap(BizWoProcessTask::getStepNo, t -> t, (a, b) -> a));
        Map<String, BizWoProcessTask> sourceByName = sourceTasks.stream()
                .filter(t -> t.getStepName() != null)
                .collect(Collectors.toMap(BizWoProcessTask::getStepName, t -> t, (a, b) -> a));
        for (BizWoProcessTask nt : newTasks) {
            BizWoProcessTask src = nt.getStepNo() != null ? sourceByStepNo.get(nt.getStepNo()) : null;
            if (src == null && nt.getStepName() != null) {
                src = sourceByName.get(nt.getStepName());
            }
            if (src != null) {
                boolean updated = false;
                if (src.getQcRequirements() != null && !src.getQcRequirements().isBlank()) {
                    nt.setQcRequirements(src.getQcRequirements());
                    updated = true;
                }
                if (src.getQcTemplateId() != null && nt.getQcTemplateId() == null) {
                    nt.setQcTemplateId(src.getQcTemplateId());
                    updated = true;
                }
                if (src.getQcStatus() != null && nt.getQcStatus() == null) {
                    nt.setQcStatus(src.getQcStatus());
                    updated = true;
                }
                if (updated) {
                    taskMapper.updateById(nt);
                }
            }
        }

        lockMaterialsForWorkOrder(newWo);

        // 将关联订单状态推进
        if (newWo.getOrderId() != null) {
            salesOrderService.updateOrderProductionStatus(newWo.getOrderId());
        }

        return newWo.getId();
    }

    /**
     * 自动锁料：遍历BOM快照，为每种物料锁定库存
     * 库存不足时记录日志并通知仓库角色，但不阻断工单创建
     */
    private void lockMaterialsForWorkOrder(BizWorkOrder wo) {
        List<BizWoBomSnapshot> snapshots = bomSnapshotMapper.selectList(
                new LambdaQueryWrapper<BizWoBomSnapshot>()
                        .eq(BizWoBomSnapshot::getWorkOrderId, wo.getId()));

        List<String> shortages = new ArrayList<>();

        for (BizWoBomSnapshot snapshot : snapshots) {
            BizMaterial material = materialMapper.selectById(snapshot.getMaterialId());
            if (material == null) continue;

            Long warehouseId = material.getDefaultWarehouseId();
            if (warehouseId == null) {
                shortages.add(material.getMaterialName() + "(未设默认仓库)");
                continue;
            }

            // 先检查可用库存，避免lockStock抛异常导致事务标记rollback-only
            BigDecimal available = inventoryService.getAvailableQty(snapshot.getMaterialId(), warehouseId);
            if (available == null || available.compareTo(snapshot.getRequiredQty()) < 0) {
                shortages.add(material.getMaterialName() + "(库存不足)");
                log.warn("工单[{}]锁料跳过: materialId={}, 需求={}, 可用={}",
                        wo.getWoNo(), snapshot.getMaterialId(), snapshot.getRequiredQty(), available);
                continue;
            }
            try {
                inventoryService.lockStock(
                        snapshot.getMaterialId(), warehouseId, snapshot.getRequiredQty(),
                        "wo_lock", "work_order", wo.getId(),
                        wo.getWoNo(), "工单[" + wo.getWoNo() + "]自动锁料", null);
            } catch (Exception e) {
                shortages.add(material.getMaterialName() + "(锁料失败)");
                log.warn("工单[{}]锁料失败: materialId={}, 原因: {}",
                        wo.getWoNo(), snapshot.getMaterialId(), e.getMessage());
            }
        }

        // 如有缺料，通知仓库角色
        if (!shortages.isEmpty()) {
            String content = "工单[" + wo.getWoNo() + "]以下物料库存不足: " + String.join(", ", shortages);
            notificationService.notifyRole("warehouse", "物料锁定不足提醒", content,
                    "alert", "work_order", wo.getId());
        }
    }

    /**
     * BOM快照: 工单仅领"直接子件",不穿透嵌套
     * 配方来源两种:
     *   1) 产品自己有 active BOM 版本 → 取该 BOM 的顶层(parent_item_id=0)
     *   2) 产品没自己的 BOM → 在别的 BOM 里反查,找一个 materialId/productId 匹配且其下有嵌套子项的
     *      BOM_item, 取它的嵌套子项作为本工单的领料配方
     * 第二种支持: "子件没独立 BOM, 配方写在父件 BOM 里嵌套" — 子件自己也能生成 WO 独立生产
     */
    private void snapshotBom(BizWorkOrder wo) {
        // 1. 找配方来源: bomVersionId + 父项 id (为 0 = 顶层, 否则 = 嵌套父项 id)
        Long bomVersionId = null;
        Long bomItemParentId = null;

        BizBomVersion activeBom = bomVersionMapper.selectOne(
                new LambdaQueryWrapper<BizBomVersion>()
                        .eq(BizBomVersion::getProductId, wo.getProductId())
                        .eq(BizBomVersion::getStatus, "active")
                        .last("LIMIT 1"));
        if (activeBom != null) {
            bomVersionId = activeBom.getId();
            bomItemParentId = 0L;       // 顶层
        } else {
            // 反查: 别的 BOM 里把它当子件嵌套, 取嵌套的下一层做配方
            List<BizBomItem> candidates = bomItemMapper.selectList(
                    new LambdaQueryWrapper<BizBomItem>()
                            .and(w -> w.eq(BizBomItem::getMaterialId, wo.getProductId())
                                    .or().eq(BizBomItem::getProductId, wo.getProductId())));
            for (BizBomItem c : candidates) {
                long childCount = bomItemMapper.selectCount(
                        new LambdaQueryWrapper<BizBomItem>()
                                .eq(BizBomItem::getParentItemId, c.getId()));
                if (childCount > 0) {
                    bomVersionId = c.getBomVersionId();
                    bomItemParentId = c.getId();
                    log.info("snapshotBom: 用嵌套配方, productId={}, bomVerId={}, parentItemId={}, woId={}",
                            wo.getProductId(), bomVersionId, bomItemParentId, wo.getId());
                    break;
                }
            }
        }

        if (bomVersionId == null) {
            log.warn("snapshotBom: 未找到 BOM 配方(自己无 active BOM 也未在任何 BOM 中嵌套), productId={}, woId={}",
                    wo.getProductId(), wo.getId());
            return;
        }

        // 2. 取直接子件 (一层, 不穿透)
        LambdaQueryWrapper<BizBomItem> w = new LambdaQueryWrapper<BizBomItem>()
                .eq(BizBomItem::getBomVersionId, bomVersionId)
                .orderByAsc(BizBomItem::getSortOrder);
        if (bomItemParentId == 0L) {
            w.and(x -> x.eq(BizBomItem::getParentItemId, 0L).or().isNull(BizBomItem::getParentItemId));
        } else {
            w.eq(BizBomItem::getParentItemId, bomItemParentId);
        }
        List<BizBomItem> directChildren = bomItemMapper.selectList(w);

        // 3. 累加同物料的需求量, 写快照
        // 自制半成品在 BOM 里用 productId 配置 (materialId 为 null), 必须 fallback 到 productId,
        // 否则会被漏掉. 主计划展开 buildNode 里也是这个口径.
        Map<Long, BigDecimal> required = new LinkedHashMap<>();
        Map<Long, Long> bomItemIdMap = new HashMap<>();
        for (BizBomItem child : directChildren) {
            Long matId = child.getMaterialId() != null ? child.getMaterialId() : child.getProductId();
            if (matId == null) continue;
            BigDecimal qty = child.getActualQuantity() != null ? child.getActualQuantity()
                    : (child.getQuantity() != null ? child.getQuantity() : BigDecimal.ONE);
            required.merge(matId, qty, BigDecimal::add);
            bomItemIdMap.putIfAbsent(matId, child.getId());
        }
        for (Map.Entry<Long, BigDecimal> e : required.entrySet()) {
            BizWoBomSnapshot snapshot = new BizWoBomSnapshot();
            snapshot.setWorkOrderId(wo.getId());
            snapshot.setMaterialId(e.getKey());
            snapshot.setBomItemId(bomItemIdMap.get(e.getKey()));
            snapshot.setRequiredQty(e.getValue().multiply(wo.getPlanQty()));
            snapshot.setIssuedQty(BigDecimal.ZERO);
            // 应用主计划替代料偏好
            if (wo.getSubstituteSelections() != null) {
                Long preferredSubId = wo.getSubstituteSelections().get(e.getKey());
                if (preferredSubId != null) snapshot.setPreferredSubstituteId(preferredSubId);
            }
            bomSnapshotMapper.insert(snapshot);
        }
    }

    /**
     * 生成工序任务：根据产品的生效工艺路线创建工序任务
     * 每道工序对应一个工序任务，继承标准工时和计件单价
     */
    private void generateProcessTasks(BizWorkOrder wo) {
        BizProcessRoute route = null;

        // 优先使用指定的工艺路线ID（计划下达时从计划工序配置中传入）
        if (wo.getRouteId() != null) {
            route = processRouteMapper.selectById(wo.getRouteId());
            if (route != null) {
                log.info("generateProcessTasks: 使用指定routeId={}, woId={}", wo.getRouteId(), wo.getId());
            }
        }

        // 回退：按产品ID查询生效工艺路线
        if (route == null) {
            route = processRouteMapper.selectOne(
                    new LambdaQueryWrapper<BizProcessRoute>()
                            .eq(BizProcessRoute::getProductId, wo.getProductId())
                            .eq(BizProcessRoute::getStatus, "active")
                            .last("LIMIT 1"));
        }

        if (route == null) {
            log.warn("generateProcessTasks: 未找到工艺路线, productId={}, routeId={}, woId={}",
                    wo.getProductId(), wo.getRouteId(), wo.getId());
            return;
        }

        // 如果是计划下达的工单，查询计划行项的工序指派配置，在创建任务时直接合入
        Map<Integer, BizProductionPlanItemStep> planStepByNo = new HashMap<>();
        Map<String, BizProductionPlanItemStep> planStepByName = new HashMap<>();
        if (wo.getPlanItemId() != null) {
            List<BizProductionPlanItemStep> planSteps = planItemStepMapper.selectList(
                    new LambdaQueryWrapper<BizProductionPlanItemStep>()
                            .eq(BizProductionPlanItemStep::getPlanItemId, wo.getPlanItemId()));
            log.info("【指派传递】generateProcessTasks: woId={}, planItemId={}, 查到计划工序配置 {} 条",
                    wo.getId(), wo.getPlanItemId(), planSteps.size());
            for (BizProductionPlanItemStep ps : planSteps) {
                log.info("【指派传递】  计划配置: stepNo={}, stepName={}, teamId={}, assigneeId={}",
                        ps.getStepNo(), ps.getStepName(), ps.getTeamId(), ps.getAssigneeId());
                if (ps.getStepNo() != null) planStepByNo.put(ps.getStepNo(), ps);
                if (ps.getStepName() != null) planStepByName.put(ps.getStepName(), ps);
            }
        } else {
            log.info("【指派传递】generateProcessTasks: woId={}, planItemId为空(非计划下达), 跳过指派合入", wo.getId());
        }

        // 查询工艺路线工序关联
        List<BizProcessRouteStep> routeSteps = routeStepMapper.selectList(
                new LambdaQueryWrapper<BizProcessRouteStep>()
                        .eq(BizProcessRouteStep::getRouteId, route.getId())
                        .orderByAsc(BizProcessRouteStep::getStepNo));

        // 批量加载工序库，用于值解析（覆盖值 > 工序库默认值）
        Set<Long> processIds = routeSteps.stream()
                .map(BizProcessRouteStep::getProcessId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, BizProcess> processMap = processIds.isEmpty() ? Map.of() :
                processMapper.selectBatchIds(processIds).stream()
                        .collect(Collectors.toMap(BizProcess::getId, p -> p, (a, b) -> a));

        // 仅当所有工序的 stepGroup 都为 null 时,才自动按顺序分配独立组号
        // 若用户明确设置了分组(即使全部相同表示并行),尊重原设置
        boolean allStepGroupNull = routeSteps.stream().allMatch(s -> s.getStepGroup() == null);

        int autoGroupSeq = 0;
        for (BizProcessRouteStep rs : routeSteps) {
            autoGroupSeq++;
            BizProcess proc = processMap.get(rs.getProcessId());
            String resolvedName = proc != null ? proc.getProcessName() : "";
            Integer resolvedTime = rs.getStandardTime() != null ? rs.getStandardTime() : (proc != null ? proc.getStandardTime() : null);
            BigDecimal resolvedPrice = rs.getPiecePrice() != null ? rs.getPiecePrice() : (proc != null ? proc.getPiecePrice() : null);
            Integer resolvedSemiStock = rs.getIsSemiStock() != null ? rs.getIsSemiStock() : (proc != null ? proc.getIsSemiStock() : 0);
            Integer resolvedOutsourceable = rs.getIsOutsourceable() != null ? rs.getIsOutsourceable() : (proc != null ? proc.getIsOutsourceable() : 0);
            Long resolvedQcTemplateId = rs.getQcTemplateId() != null ? rs.getQcTemplateId() : (proc != null ? proc.getQcTemplateId() : null);

            BizWoProcessTask task = new BizWoProcessTask();
            task.setWorkOrderId(wo.getId());
            task.setStepNo(rs.getStepNo());
            task.setStepGroup(allStepGroupNull ? autoGroupSeq : rs.getStepGroup());
            task.setStepName(resolvedName);
            task.setStandardTime(resolvedTime);
            task.setPiecePrice(resolvedPrice);
            task.setPlanQty(wo.getPlanQty());
            task.setQualifiedQty(BigDecimal.ZERO);
            task.setDefectQty(BigDecimal.ZERO);
            task.setStatus("pending");
            task.setIsOutsourced(0);
            task.setIsSemiStock(resolvedSemiStock != null ? resolvedSemiStock : 0);
            task.setIsOutsourceable(resolvedOutsourceable != null ? resolvedOutsourceable : 0);
            task.setQcTemplateId(resolvedQcTemplateId);
            task.setWageRatio(BigDecimal.ONE); // 默认 1,下面若计划有配置会覆盖

            // 合入计划行项工序配置（指派、工时、质检、外协等）
            BizProductionPlanItemStep config = planStepByNo.get(rs.getStepNo());
            if (config == null && resolvedName != null) {
                config = planStepByName.get(resolvedName);
            }
            if (config != null) {
                log.info("【指派传递】匹配成功 stepNo={}, 将设置 assignedTeamId={}, assignedUserId={}",
                        rs.getStepNo(), config.getTeamId(), config.getAssigneeId());
                task.setAssignedTeamId(config.getTeamId());
                task.setAssignedTeamName(config.getTeamName());
                task.setAssignedUserId(config.getAssigneeId());
                task.setAssignedUserName(config.getAssigneeName());
                if (config.getAssigneeId() != null) {
                    task.setAssignType("person");
                } else if (config.getTeamId() != null) {
                    task.setAssignType("team");
                }
                if (config.getStandardTime() != null) {
                    task.setStandardTime(config.getStandardTime());
                }
                if (config.getPiecePrice() != null) {
                    task.setPiecePrice(config.getPiecePrice());
                }
                if (config.getWageRatio() != null) {
                    task.setWageRatio(config.getWageRatio());
                }
                if (config.getAssignedMemberIds() != null) {
                    task.setAssignedMemberIds(config.getAssignedMemberIds());
                }
                if (config.getPlanStartTime() != null) {
                    task.setStartTime(config.getPlanStartTime());
                }
                if (config.getPlanEndTime() != null) {
                    task.setEndTime(config.getPlanEndTime());
                }
                if (config.getNeedQc() != null && config.getNeedQc()) {
                    if (config.getQcTemplateId() != null) {
                        task.setQcTemplateId(config.getQcTemplateId());
                    }
                    task.setQcStatus("pending");
                }
                if ("outsource".equals(config.getHandleType())) {
                    task.setIsOutsourced(1);
                    task.setIsOutsourceable(1);
                }
                // 有指派信息时，状态直接设为ready
                if (config.getTeamId() != null || config.getAssigneeId() != null) {
                    task.setStatus("ready");
                }
            }

            taskMapper.insert(task);
            log.info("【指派传递】已创建工序任务: taskId={}, stepNo={}, stepName={}, assignedTeamId={}, assignedUserId={}, status={}",
                    task.getId(), task.getStepNo(), task.getStepName(),
                    task.getAssignedTeamId(), task.getAssignedUserId(), task.getStatus());
        }
    }

    /**
     * 更新工单状态
     */
    @Transactional
    public void updateStatus(Long id, String status) {
        BizWorkOrder wo = getById(id);
        if (wo == null) {
            throw new BusinessException("工单不存在");
        }
        wo.setStatus(status);
        updateById(wo);
    }

    /**
     * 开始生产
     * 将工单状态从pending/scheduled变更为in_progress
     * 工序任务已在创建工单时生成，此处仅更新状态
     * 将第一道工序任务状态设为ready（可开始报工）
     *
     * @param id 工单ID
     */
    @Transactional
    public void startProduction(Long id) {
        BizWorkOrder wo = getById(id);
        if (wo == null) {
            throw new BusinessException("工单不存在");
        }
        if (!"pending".equals(wo.getStatus()) && !"scheduled".equals(wo.getStatus())) {
            throw new BusinessException("只有待排产或已排产的工单可以开始生产");
        }

        wo.setStatus("in_progress");
        updateById(wo);

        // 将第一组工序任务状态设为ready，表示可以开始报工（同组并行）
        List<BizWoProcessTask> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<BizWoProcessTask>()
                        .eq(BizWoProcessTask::getWorkOrderId, id)
                        .eq(BizWoProcessTask::getStatus, "pending")
                        .orderByAsc(BizWoProcessTask::getStepGroup)
                        .orderByAsc(BizWoProcessTask::getStepNo));
        if (!tasks.isEmpty()) {
            Integer firstGroup = tasks.get(0).getStepGroup();
            for (BizWoProcessTask task : tasks) {
                if (!task.getStepGroup().equals(firstGroup)) break;
                // 外协工序不在此处激活，由外协链路或工单变更统一管理
                if (task.getIsOutsourceable() != null && task.getIsOutsourceable() == 1) continue;
                task.setStatus("ready");
                task.setStartTime(LocalDateTime.now());
                taskMapper.updateById(task);
            }
        }
    }

    /**
     * 取消工单
     * 同时释放所有关联的物料锁定
     * 副作用:
     *  - (a) 回扣计划行项 releasedQty (planQty - completedQty 部分退回, 已完工部分不退)
     *  - (b) 解绑售后单配件 (清空 service_order_part.work_order_id)
     *  - (c) 系统通知场景 work_order_cancelled (可在 [系统设置→通知配置] 配置)
     */
    @Transactional
    public void cancelWorkOrder(Long id, String reason) {
        BizWorkOrder wo = getById(id);
        if (wo == null) {
            throw new BusinessException("工单不存在");
        }
        if ("cancelled".equals(wo.getStatus())) {
            throw new BusinessException("工单已取消");
        }
        if ("completed".equals(wo.getStatus())) {
            throw new BusinessException("已完成的工单不可取消");
        }

        wo.setStatus("cancelled");
        wo.setRemark(reason);
        updateById(wo);

        // 取消关联的工序任务
        List<BizWoProcessTask> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<BizWoProcessTask>()
                        .eq(BizWoProcessTask::getWorkOrderId, id)
                        .ne(BizWoProcessTask::getStatus, "completed"));
        for (BizWoProcessTask task : tasks) {
            task.setStatus("cancelled");
            taskMapper.updateById(task);
        }

        // 释放所有关联的活跃物料锁定
        releaseMaterialLocks(id);

        // (a) 回扣计划行项 releasedQty: 未完工部分退回, 重置 status; 若计划下所有行项都回 pending, 主表 in_progress → confirmed
        if (wo.getPlanItemId() != null) {
            try {
                com.aimes.modules.mrp.entity.BizProductionPlanItem item = planItemMapper.selectById(wo.getPlanItemId());
                if (item != null && !"cancelled".equals(item.getStatus())) {
                    BigDecimal planQty = wo.getPlanQty() != null ? wo.getPlanQty() : BigDecimal.ZERO;
                    BigDecimal completedQty = wo.getCompletedQty() != null ? wo.getCompletedQty() : BigDecimal.ZERO;
                    BigDecimal refund = planQty.subtract(completedQty);
                    if (refund.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal cur = item.getReleasedQty() != null ? item.getReleasedQty() : BigDecimal.ZERO;
                        BigDecimal next = cur.subtract(refund);
                        if (next.compareTo(BigDecimal.ZERO) < 0) next = BigDecimal.ZERO;
                        item.setReleasedQty(next);
                        BigDecimal itemPlanQty = item.getPlanQty() != null ? item.getPlanQty() : BigDecimal.ZERO;
                        if (next.compareTo(BigDecimal.ZERO) == 0) item.setStatus("pending");
                        else if (next.compareTo(itemPlanQty) >= 0) item.setStatus("fully_released");
                        else item.setStatus("partial_released");
                        planItemMapper.updateById(item);
                        log.info("工单取消回扣计划行项 releasedQty: planItemId={}, refund={}, 剩余={}",
                                item.getId(), refund, next);

                        // 主表状态回退: 计划下所有非取消行项都是 pending 时, in_progress → confirmed
                        if (item.getPlanId() != null) {
                            com.aimes.modules.mrp.entity.BizProductionPlan plan = productionPlanMapper.selectById(item.getPlanId());
                            if (plan != null && "in_progress".equals(plan.getStatus())) {
                                List<com.aimes.modules.mrp.entity.BizProductionPlanItem> allItems = planItemMapper.selectList(
                                        new LambdaQueryWrapper<com.aimes.modules.mrp.entity.BizProductionPlanItem>()
                                                .eq(com.aimes.modules.mrp.entity.BizProductionPlanItem::getPlanId, plan.getId())
                                                .ne(com.aimes.modules.mrp.entity.BizProductionPlanItem::getStatus, "cancelled"));
                                boolean allPending = !allItems.isEmpty() && allItems.stream()
                                        .allMatch(it -> "pending".equals(it.getStatus()));
                                if (allPending) {
                                    plan.setStatus("scheduled");
                                    productionPlanMapper.updateById(plan);
                                    log.info("工单取消后计划主表状态回退: planId={}, in_progress → scheduled", plan.getId());
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("工单取消回扣 releasedQty 失败: woId={}", id, e);
            }
        }

        // (b) 解绑售后单配件: 清空 work_order_id, 让售后可重新关联
        try {
            List<com.aimes.modules.aftersale.entity.BizServiceOrderPart> linkedParts = serviceOrderPartMapper.selectList(
                    new LambdaQueryWrapper<com.aimes.modules.aftersale.entity.BizServiceOrderPart>()
                            .eq(com.aimes.modules.aftersale.entity.BizServiceOrderPart::getWorkOrderId, id));
            for (com.aimes.modules.aftersale.entity.BizServiceOrderPart p : linkedParts) {
                p.setWorkOrderId(null);
                serviceOrderPartMapper.updateById(p);
            }
            if (!linkedParts.isEmpty()) {
                log.info("工单取消解绑售后配件: woId={}, 解绑 {} 条", id, linkedParts.size());
            }
        } catch (Exception e) {
            log.warn("工单取消解绑售后配件失败: woId={}", id, e);
        }

        // (c) 场景化通知: 可在 [系统设置→通知配置] 切换开关 / 调整接收角色
        try {
            String content = "生产工单[" + wo.getWoNo() + "] 已取消"
                    + (reason != null && !reason.isEmpty() ? " (原因: " + reason + ")" : "");
            notificationService.notifyByScenario("work_order_cancelled", "生产工单取消", content,
                    "warn", "work_order", wo.getId());
        } catch (Exception ignored) {}
    }

    /**
     * 释放工单关联的所有活跃物料锁定
     */
    private void releaseMaterialLocks(Long workOrderId) {
        List<BizInvReservation> reservations = reservationMapper.selectList(
                new LambdaQueryWrapper<BizInvReservation>()
                        .eq(BizInvReservation::getRelatedOrderType, "work_order")
                        .eq(BizInvReservation::getRelatedOrderId, workOrderId)
                        .eq(BizInvReservation::getStatus, "active"));
        for (BizInvReservation reservation : reservations) {
            try {
                inventoryService.unlockStock(reservation.getId());
            } catch (Exception e) {
                log.warn("释放锁料失败: reservationId={}, 原因: {}", reservation.getId(), e.getMessage());
            }
        }
    }

    /**
     * 按订单ID级联取消所有关联工单
     */
    @Transactional
    public void cancelByOrderId(Long orderId, String reason) {
        List<BizWorkOrder> workOrders = list(new LambdaQueryWrapper<BizWorkOrder>()
                .eq(BizWorkOrder::getOrderId, orderId)
                .notIn(BizWorkOrder::getStatus, "completed", "cancelled"));
        for (BizWorkOrder wo : workOrders) {
            cancelWorkOrder(wo.getId(), reason);
        }
    }

    /**
     * 获取工序任务列表
     */
    public List<BizWoProcessTask> getTasks(Long woId) {
        BizWorkOrder wo = getById(woId);
        if (wo == null) {
            throw new BusinessException("工单不存在");
        }

        List<BizWoProcessTask> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<BizWoProcessTask>()
                        .eq(BizWoProcessTask::getWorkOrderId, woId)
                        .orderByAsc(BizWoProcessTask::getStepGroup)
                        .orderByAsc(BizWoProcessTask::getStepNo));
        if (tasks.isEmpty()) return tasks;

        // 注入工序级计划完成时间 (来自 biz_scheduling 排产记录)
        // 同 task 多次排产时取最晚的 endTime
        java.util.Set<Long> taskIds = tasks.stream()
                .map(BizWoProcessTask::getId).collect(java.util.stream.Collectors.toSet());
        java.util.List<com.aimes.modules.production.entity.BizScheduling> schedulings = schedulingMapper.selectList(
                new LambdaQueryWrapper<com.aimes.modules.production.entity.BizScheduling>()
                        .in(com.aimes.modules.production.entity.BizScheduling::getTaskId, taskIds)
                        .ne(com.aimes.modules.production.entity.BizScheduling::getStatus, "cancelled"));
        java.util.Map<Long, java.time.LocalDateTime> planEndMap = new java.util.HashMap<>();
        for (var s : schedulings) {
            if (s.getEndTime() == null || s.getTaskId() == null) continue;
            var existing = planEndMap.get(s.getTaskId());
            if (existing == null || s.getEndTime().isAfter(existing)) {
                planEndMap.put(s.getTaskId(), s.getEndTime());
            }
        }
        for (BizWoProcessTask t : tasks) {
            t.setPlanEndTime(planEndMap.get(t.getId()));
        }
        return tasks;
    }

    /**
     * 派工：分配工序任务给指定工人或班组
     * 文档定义的任务状态: pending(待开始) / ready(就绪) / in_progress(进行中) / completed(已完成)
     *
     * @param taskId 工序任务ID
     * @param userId 指定工人ID（与teamId二选一）
     * @param teamId 指定班组ID（与userId二选一）
     */
    @Transactional
    public void assignTask(Long taskId, Long userId, Long teamId) {
        BizWoProcessTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("工序任务不存在");
        }
        if ("completed".equals(task.getStatus()) || "cancelled".equals(task.getStatus())) {
            throw new BusinessException("已完成或已取消的任务不可分配");
        }

        if (userId != null) {
            task.setAssignedUserId(userId);
        }
        if (teamId != null) {
            task.setAssignedTeamId(teamId);
        }
        // 分配后状态变为ready（就绪，可开始报工）
        task.setStatus("ready");
        taskMapper.updateById(task);
    }

    // ==================== 工序任务CRUD ====================

    /**
     * 新增工序任务
     */
    @Transactional
    public void addTask(Long woId, BizWoProcessTask task) {
        BizWorkOrder wo = getById(woId);
        if (wo == null) throw new BusinessException("工单不存在");

        task.setWorkOrderId(woId);
        if (task.getPlanQty() == null) task.setPlanQty(wo.getPlanQty());
        if (task.getQualifiedQty() == null) task.setQualifiedQty(BigDecimal.ZERO);
        if (task.getDefectQty() == null) task.setDefectQty(BigDecimal.ZERO);
        if (task.getStatus() == null) task.setStatus("pending");
        if (task.getIsOutsourced() == null) task.setIsOutsourced(0);
        if (task.getIsSemiStock() == null) task.setIsSemiStock(0);
        if (task.getIsOutsourceable() == null) task.setIsOutsourceable(0);
        taskMapper.insert(task);
    }

    /**
     * 更新工序任务
     */
    @Transactional
    public void updateTask(Long taskId, BizWoProcessTask task) {
        BizWoProcessTask existing = taskMapper.selectById(taskId);
        if (existing == null) throw new BusinessException("工序任务不存在");
        if ("completed".equals(existing.getStatus())) {
            throw new BusinessException("已完成的任务不可修改");
        }
        task.setId(taskId);
        task.setWorkOrderId(existing.getWorkOrderId());
        taskMapper.updateById(task);
    }

    /**
     * 删除工序任务（仅pending/ready状态可删除）
     */
    @Transactional
    public void deleteTask(Long taskId) {
        BizWoProcessTask existing = taskMapper.selectById(taskId);
        if (existing == null) throw new BusinessException("工序任务不存在");
        if (!"pending".equals(existing.getStatus()) && !"ready".equals(existing.getStatus())) {
            throw new BusinessException("仅待开始或就绪状态的任务可删除");
        }
        taskMapper.deleteById(taskId);
    }

    /**
     * 批量更新工序顺序
     */
    @Transactional
    public void reorderTasks(Long woId, List<Long> taskIds) {
        for (int i = 0; i < taskIds.size(); i++) {
            BizWoProcessTask task = taskMapper.selectById(taskIds.get(i));
            if (task != null && task.getWorkOrderId().equals(woId)) {
                task.setStepNo(i + 1);
                taskMapper.updateById(task);
            }
        }
    }

    // ==================== 领料 ====================

    /**
     * 获取领料状态：BOM快照 + 物料信息 + 仓库信息 + 锁库信息 + 是否已领齐
     */
    public Map<String, Object> getIssueStatus(Long woId) {
        BizWorkOrder wo = getById(woId);
        if (wo == null) throw new BusinessException("工单不存在");

        List<BizWoBomSnapshot> snapshots = bomSnapshotMapper.selectList(
                new LambdaQueryWrapper<BizWoBomSnapshot>()
                        .eq(BizWoBomSnapshot::getWorkOrderId, woId)
                        .orderByAsc(BizWoBomSnapshot::getId));

        // 查询该工单的所有活跃锁库预留，按物料汇总
        List<BizInvReservation> reservations = reservationMapper.selectList(
                new LambdaQueryWrapper<BizInvReservation>()
                        .eq(BizInvReservation::getRelatedOrderType, "work_order")
                        .eq(BizInvReservation::getRelatedOrderId, woId)
                        .eq(BizInvReservation::getStatus, "active"));

        // 按物料ID汇总锁库信息: materialId -> {lockedQty, remainingQty, warehouseId, orderNo}
        Map<Long, BigDecimal> lockedQtyMap = new HashMap<>();
        Map<Long, BigDecimal> lockedRemainingMap = new HashMap<>();
        Map<Long, Long> lockedWarehouseMap = new HashMap<>();
        Map<Long, String> lockedOrderNoMap = new HashMap<>();
        Map<Long, String> lockedOrderTypeMap = new HashMap<>();
        for (BizInvReservation r : reservations) {
            lockedQtyMap.merge(r.getMaterialId(),
                    r.getLockedQty() != null ? r.getLockedQty() : BigDecimal.ZERO, BigDecimal::add);
            lockedRemainingMap.merge(r.getMaterialId(),
                    r.getRemainingQty() != null ? r.getRemainingQty() : BigDecimal.ZERO, BigDecimal::add);
            // 记录锁库仓库和来源单据（取第一个）
            lockedWarehouseMap.putIfAbsent(r.getMaterialId(), r.getWarehouseId());
            lockedOrderNoMap.putIfAbsent(r.getMaterialId(), r.getRelatedOrderNo());
            lockedOrderTypeMap.putIfAbsent(r.getMaterialId(), r.getRelatedOrderType());
        }

        // 构建 materialId → 所有相关 bomItemId 的映射：
        // 既包含快照记录的 bomItemId（工单创建时对应的 BOM 子件），
        // 也包含当前生效 BOM 中相同物料的子件，覆盖"工单创建后 BOM 被改版"导致的替代料归属差异
        Map<Long, Set<Long>> materialToBomItemIds = new HashMap<>();
        for (BizWoBomSnapshot s : snapshots) {
            if (s.getMaterialId() != null && s.getBomItemId() != null) {
                materialToBomItemIds.computeIfAbsent(s.getMaterialId(), k -> new HashSet<>()).add(s.getBomItemId());
            }
        }
        if (wo.getProductId() != null) {
            BizBomVersion activeBom = bomVersionMapper.selectOne(
                    new LambdaQueryWrapper<BizBomVersion>()
                            .eq(BizBomVersion::getProductId, wo.getProductId())
                            .eq(BizBomVersion::getStatus, "active")
                            .last("LIMIT 1"));
            if (activeBom != null) {
                List<BizBomItem> bomItems = bomItemMapper.selectList(
                        new LambdaQueryWrapper<BizBomItem>()
                                .eq(BizBomItem::getBomVersionId, activeBom.getId()));
                for (BizBomItem bi : bomItems) {
                    if (bi.getMaterialId() != null) {
                        materialToBomItemIds.computeIfAbsent(bi.getMaterialId(), k -> new HashSet<>()).add(bi.getId());
                    }
                }
            }
        }

        // 批量查询所有相关 bomItemId 的替代料配置
        Set<Long> allBomItemIds = new HashSet<>();
        materialToBomItemIds.values().forEach(allBomItemIds::addAll);
        Map<Long, List<BizBomSubstitute>> substitutesByBomItem = new HashMap<>();
        if (!allBomItemIds.isEmpty()) {
            List<BizBomSubstitute> allSubs = bomSubstituteMapper.selectList(
                    new LambdaQueryWrapper<BizBomSubstitute>()
                            .in(BizBomSubstitute::getBomItemId, allBomItemIds)
                            .orderByAsc(BizBomSubstitute::getPriority));
            for (BizBomSubstitute s : allSubs) {
                substitutesByBomItem.computeIfAbsent(s.getBomItemId(), k -> new ArrayList<>()).add(s);
            }
        }

        // 无BOM时不应显示已领齐，允许无限制手动领料
        boolean allIssued = !snapshots.isEmpty();
        List<Map<String, Object>> items = new ArrayList<>();
        for (BizWoBomSnapshot snap : snapshots) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("snapshotId", snap.getId());
            item.put("materialId", snap.getMaterialId());
            item.put("bomItemId", snap.getBomItemId());
            item.put("preferredSubstituteId", snap.getPreferredSubstituteId());
            item.put("requiredQty", snap.getRequiredQty());
            item.put("issuedQty", snap.getIssuedQty());
            item.put("issuedTaskQty", snap.getIssuedTaskQty());
            item.put("returnedTaskQty", snap.getReturnedTaskQty());
            item.put("returnedQty", snap.getReturnedQty());

            BigDecimal remaining = snap.getRequiredQty().subtract(
                    snap.getIssuedQty() != null ? snap.getIssuedQty() : BigDecimal.ZERO);
            item.put("remainingQty", remaining.max(BigDecimal.ZERO));

            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                allIssued = false;
            }

            // 锁库信息
            BigDecimal matLockedQty = lockedQtyMap.getOrDefault(snap.getMaterialId(), BigDecimal.ZERO);
            BigDecimal matLockedRemaining = lockedRemainingMap.getOrDefault(snap.getMaterialId(), BigDecimal.ZERO);
            Long lockedWarehouseId = lockedWarehouseMap.get(snap.getMaterialId());
            item.put("locked", matLockedQty.compareTo(BigDecimal.ZERO) > 0);
            item.put("lockedQty", matLockedQty);
            item.put("lockedRemainingQty", matLockedRemaining);
            if (lockedWarehouseId != null) {
                item.put("lockedWarehouseId", lockedWarehouseId);
            }
            item.put("lockedOrderNo", lockedOrderNoMap.get(snap.getMaterialId()));
            item.put("lockedOrderType", lockedOrderTypeMap.get(snap.getMaterialId()));

            // 物料信息
            BizMaterial mat = materialMapper.selectById(snap.getMaterialId());
            if (mat != null) {
                item.put("materialName", mat.getMaterialName());
                item.put("materialNo", mat.getMaterialNo());
                item.put("spec", mat.getSpec());
                item.put("unit", mat.getUnit());
                item.put("defaultWarehouseId", mat.getDefaultWarehouseId());
                // 查可用库存（优先锁库仓库→默认仓库→全仓汇总）
                Long queryWarehouseId = lockedWarehouseId != null ? lockedWarehouseId : mat.getDefaultWarehouseId();
                try {
                    BigDecimal available = queryWarehouseId != null
                            ? inventoryService.getAvailableQty(snap.getMaterialId(), queryWarehouseId)
                            : inventoryService.getTotalAvailableQty(snap.getMaterialId());
                    item.put("availableQty", available != null ? available : BigDecimal.ZERO);
                } catch (Exception e) {
                    item.put("availableQty", BigDecimal.ZERO);
                }
            }

            // 替代料：主料可能不足时，前端可切换到替代料领取
            // 汇总"该主料"下所有相关 BOM 子件配置的替代料，按 materialId 去重（保留优先级最小的一条）
            Set<Long> relatedBomItemIds = materialToBomItemIds.getOrDefault(snap.getMaterialId(), Set.of());
            Map<Long, BizBomSubstitute> uniqueSubs = new LinkedHashMap<>();
            for (Long biId : relatedBomItemIds) {
                for (BizBomSubstitute s2 : substitutesByBomItem.getOrDefault(biId, List.of())) {
                    if (s2.getMaterialId() == null) continue;
                    BizBomSubstitute existing = uniqueSubs.get(s2.getMaterialId());
                    Integer ep = existing != null && existing.getPriority() != null ? existing.getPriority() : Integer.MAX_VALUE;
                    Integer np = s2.getPriority() != null ? s2.getPriority() : Integer.MAX_VALUE;
                    if (existing == null || np < ep) uniqueSubs.put(s2.getMaterialId(), s2);
                }
            }
            List<BizBomSubstitute> subs = new ArrayList<>(uniqueSubs.values());
            subs.sort(Comparator.comparing(s2 -> s2.getPriority() != null ? s2.getPriority() : Integer.MAX_VALUE));
            List<Map<String, Object>> subList = new ArrayList<>();
            for (BizBomSubstitute sub : subs) {
                if (sub.getMaterialId() == null) continue;
                BizMaterial subMat = materialMapper.selectById(sub.getMaterialId());
                if (subMat == null) continue;
                BigDecimal ratio = sub.getSubstituteRatio() != null && sub.getSubstituteRatio().compareTo(BigDecimal.ZERO) > 0
                        ? sub.getSubstituteRatio() : BigDecimal.ONE;
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("substituteId", sub.getId());
                s.put("materialId", sub.getMaterialId());
                s.put("materialNo", subMat.getMaterialNo());
                s.put("materialName", subMat.getMaterialName());
                s.put("spec", subMat.getSpec());
                s.put("unit", subMat.getUnit());
                s.put("priority", sub.getPriority());
                s.put("ratio", ratio);
                s.put("groupName", sub.getGroupName());
                s.put("defaultWarehouseId", subMat.getDefaultWarehouseId());
                try {
                    BigDecimal subAvail = subMat.getDefaultWarehouseId() != null
                            ? inventoryService.getAvailableQty(sub.getMaterialId(), subMat.getDefaultWarehouseId())
                            : inventoryService.getTotalAvailableQty(sub.getMaterialId());
                    s.put("availableQty", subAvail != null ? subAvail : BigDecimal.ZERO);
                } catch (Exception e) {
                    s.put("availableQty", BigDecimal.ZERO);
                }
                subList.add(s);
            }
            item.put("substitutes", subList);
            items.add(item);
        }

        // 无 BOM 快照时,从已完成的领料任务(production_out)反查实际领料明细,支持退料
        if (snapshots.isEmpty()) {
            List<BizInvTask> issueTasks = invTaskService.list(
                    new LambdaQueryWrapper<BizInvTask>()
                            .eq(BizInvTask::getSourceType, "work_order")
                            .eq(BizInvTask::getSourceId, woId)
                            .eq(BizInvTask::getInvType, "production_out")
                            .in(BizInvTask::getStatus, "completed", "partial"));
            if (!issueTasks.isEmpty()) {
                Set<Long> taskIds = issueTasks.stream().map(BizInvTask::getId).collect(Collectors.toSet());
                List<BizInvTaskItem> taskItems = invTaskItemMapper.selectList(
                        new LambdaQueryWrapper<BizInvTaskItem>().in(BizInvTaskItem::getTaskId, taskIds));
                // 按物料汇总已领数量
                Map<Long, BigDecimal> issuedMap = new HashMap<>();
                Map<Long, Long> whMap = new HashMap<>();
                for (BizInvTaskItem ti : taskItems) {
                    if (ti.getMaterialId() == null) continue;
                    BigDecimal qty = ti.getProcessedQty() != null ? ti.getProcessedQty()
                            : (ti.getQuantity() != null ? ti.getQuantity() : BigDecimal.ZERO);
                    issuedMap.merge(ti.getMaterialId(), qty, BigDecimal::add);
                    whMap.putIfAbsent(ti.getMaterialId(), ti.getWarehouseId());
                }
                // 查已退料数量(production_return 任务)
                List<BizInvTask> returnTasks = invTaskService.list(
                        new LambdaQueryWrapper<BizInvTask>()
                                .eq(BizInvTask::getSourceType, "work_order")
                                .eq(BizInvTask::getSourceId, woId)
                                .eq(BizInvTask::getInvType, "production_return"));
                Map<Long, BigDecimal> returnedMap = new HashMap<>();
                if (!returnTasks.isEmpty()) {
                    Set<Long> rtIds = returnTasks.stream().map(BizInvTask::getId).collect(Collectors.toSet());
                    List<BizInvTaskItem> rtItems = invTaskItemMapper.selectList(
                            new LambdaQueryWrapper<BizInvTaskItem>().in(BizInvTaskItem::getTaskId, rtIds));
                    for (BizInvTaskItem ti : rtItems) {
                        if (ti.getMaterialId() == null) continue;
                        BigDecimal qty = ti.getQuantity() != null ? ti.getQuantity() : BigDecimal.ZERO;
                        returnedMap.merge(ti.getMaterialId(), qty, BigDecimal::add);
                    }
                }
                for (Map.Entry<Long, BigDecimal> e : issuedMap.entrySet()) {
                    Long matId = e.getKey();
                    BigDecimal issued = e.getValue();
                    BigDecimal returned = returnedMap.getOrDefault(matId, BigDecimal.ZERO);
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("materialId", matId);
                    item.put("issuedQty", issued);
                    item.put("issuedTaskQty", issued);
                    item.put("returnedQty", returned);
                    item.put("returnedTaskQty", returned);
                    item.put("requiredQty", BigDecimal.ZERO);
                    item.put("remainingQty", BigDecimal.ZERO);
                    BizMaterial mat = materialMapper.selectById(matId);
                    if (mat != null) {
                        item.put("materialName", mat.getMaterialName());
                        item.put("materialNo", mat.getMaterialNo());
                        item.put("spec", mat.getSpec());
                        item.put("unit", mat.getUnit());
                        item.put("defaultWarehouseId", whMap.get(matId) != null ? whMap.get(matId) : mat.getDefaultWarehouseId());
                    }
                    items.add(item);
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("woId", woId);
        result.put("woNo", wo.getWoNo());
        result.put("hasBom", !snapshots.isEmpty());
        result.put("allIssued", allIssued);
        result.put("items", items);
        return result;
    }

    /**
     * 批量领料：生成 production_out 库存任务，记录已生成任务数
     * @param items [{materialId, warehouseId, qty, substituteForMaterialId?, substituteRatio?}]
     *              当 substituteForMaterialId 存在时，表示本行是替代料，materialId 是替代料ID；
     *              领取数量按替代料原单位，主料 BOM 快照按 qty / ratio 折算。
     */
    @Transactional
    public void issueMaterial(Long woId, List<Map<String, Object>> items) {
        BizWorkOrder wo = getById(woId);
        if (wo == null) throw new BusinessException("工单不存在");
        if (!"in_progress".equals(wo.getStatus())) {
            throw new BusinessException("只有生产中的工单可以领料");
        }

        // 预加载物料信息，补充 materialNo/materialName/spec/unit 供任务明细使用
        Set<Long> matIds = items.stream()
                .map(i -> Long.valueOf(i.get("materialId").toString()))
                .collect(Collectors.toSet());
        Map<Long, BizMaterial> matMap = matIds.isEmpty() ? Map.of() :
                materialMapper.selectBatchIds(matIds).stream()
                        .collect(Collectors.toMap(BizMaterial::getId, m -> m, (a, b) -> a));

        // 无 BOM 工单的库存兜底: 工单产品没有 active BOM 时, 工人手动选的物料如有库存不足 → 自动生成采购申请
        // (有 BOM 的工单由 BOM 快照 + MRP 流程管, 这里不再插手)
        boolean hasBom = wo.getProductId() != null && bomVersionMapper.selectCount(
                new LambdaQueryWrapper<BizBomVersion>()
                        .eq(BizBomVersion::getProductId, wo.getProductId())
                        .eq(BizBomVersion::getStatus, "active")
                        .eq(BizBomVersion::getDeleted, 0)) > 0;
        if (!hasBom) {
            // 无 BOM 工单缺料: 只检测, 不自动生成 PR. 让前端弹"缺料请购"对话框,
            // 由生产人员确认需求日期 + 用途后再调 generatePurchaseRequestsForShortage 生成.
            List<Map<String, Object>> shortageList = detectShortage(wo, items, matMap);
            if (!shortageList.isEmpty()) {
                String detail = shortageList.stream()
                        .map(s -> s.get("displayLine").toString())
                        .collect(Collectors.joining("; "));
                throw new BusinessException("以下物料在库不足, 请先发起缺料请购: " + detail);
            }
        }

        // 构造任务明细参数，并校验领料数量
        List<Map<String, Object>> taskItems = new ArrayList<>();
        for (Map<String, Object> item : items) {
            Long materialId = Long.valueOf(item.get("materialId").toString());
            BigDecimal qty = new BigDecimal(item.get("qty").toString());
            if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;

            // 替代料：校验配置合法性，必须属于主料对应 BOM 子件的已配置替代集合
            // 宽容策略：把快照记录的 bomItemId + 产品生效 BOM 中同物料的 bomItemId 合并后一起查
            Long subForId = item.get("substituteForMaterialId") != null
                    ? Long.valueOf(item.get("substituteForMaterialId").toString()) : null;
            BigDecimal subRatio = null;
            if (subForId != null) {
                BizWoBomSnapshot primarySnap = bomSnapshotMapper.selectOne(
                        new LambdaQueryWrapper<BizWoBomSnapshot>()
                                .eq(BizWoBomSnapshot::getWorkOrderId, woId)
                                .eq(BizWoBomSnapshot::getMaterialId, subForId)
                                .last("LIMIT 1"));
                if (primarySnap == null) {
                    throw new BusinessException("替代料对应的主料未在 BOM 快照中找到");
                }
                Set<Long> candidateBomItemIds = new HashSet<>();
                if (primarySnap.getBomItemId() != null) candidateBomItemIds.add(primarySnap.getBomItemId());
                if (wo.getProductId() != null) {
                    BizBomVersion activeBom = bomVersionMapper.selectOne(
                            new LambdaQueryWrapper<BizBomVersion>()
                                    .eq(BizBomVersion::getProductId, wo.getProductId())
                                    .eq(BizBomVersion::getStatus, "active")
                                    .last("LIMIT 1"));
                    if (activeBom != null) {
                        List<BizBomItem> bis = bomItemMapper.selectList(
                                new LambdaQueryWrapper<BizBomItem>()
                                        .eq(BizBomItem::getBomVersionId, activeBom.getId())
                                        .eq(BizBomItem::getMaterialId, subForId));
                        for (BizBomItem bi : bis) candidateBomItemIds.add(bi.getId());
                    }
                }
                if (candidateBomItemIds.isEmpty()) {
                    throw new BusinessException("主料所在 BOM 子件找不到，无法校验替代料配置");
                }
                BizBomSubstitute cfg = bomSubstituteMapper.selectOne(
                        new LambdaQueryWrapper<BizBomSubstitute>()
                                .in(BizBomSubstitute::getBomItemId, candidateBomItemIds)
                                .eq(BizBomSubstitute::getMaterialId, materialId)
                                .orderByAsc(BizBomSubstitute::getPriority)
                                .last("LIMIT 1"));
                if (cfg == null) {
                    throw new BusinessException("该物料未配置为对应主料的替代料");
                }
                subRatio = cfg.getSubstituteRatio() != null && cfg.getSubstituteRatio().compareTo(BigDecimal.ZERO) > 0
                        ? cfg.getSubstituteRatio() : BigDecimal.ONE;
            }

            BizMaterial mat = matMap.get(materialId);
            Map<String, Object> ti = new LinkedHashMap<>();
            ti.put("materialId", materialId);
            ti.put("materialNo", mat != null ? mat.getMaterialNo() : null);
            ti.put("materialName", mat != null ? mat.getMaterialName() : null);
            ti.put("spec", mat != null ? mat.getSpec() : null);
            ti.put("unit", mat != null ? mat.getUnit() : null);
            ti.put("quantity", qty);
            if (item.get("warehouseId") != null) ti.put("warehouseId", Long.valueOf(item.get("warehouseId").toString()));
            if (subForId != null) {
                ti.put("substituteForMaterialId", subForId);
                ti.put("substituteRatio", subRatio);
            }
            taskItems.add(ti);
        }
        if (taskItems.isEmpty()) throw new BusinessException("领料明细不能为空");

        // 创建领料库存任务
        invTaskService.createFromWorkOrderIssue(woId, wo.getWoNo(), taskItems,
                com.aimes.common.utils.SecurityUtil.getCurrentUserId());

        // 更新BOM快照的已生成任务数（替代料按比例折算到主料）
        for (Map<String, Object> ti : taskItems) {
            Long materialId = Long.valueOf(ti.get("materialId").toString());
            BigDecimal qty = new BigDecimal(ti.get("quantity").toString());
            Long subForId = ti.get("substituteForMaterialId") != null
                    ? Long.valueOf(ti.get("substituteForMaterialId").toString()) : null;
            Long snapMatId = subForId != null ? subForId : materialId;
            BigDecimal snapQty = qty;
            if (subForId != null) {
                BigDecimal ratio = ti.get("substituteRatio") != null
                        ? new BigDecimal(ti.get("substituteRatio").toString()) : BigDecimal.ONE;
                if (ratio.compareTo(BigDecimal.ZERO) <= 0) ratio = BigDecimal.ONE;
                snapQty = qty.divide(ratio, 4, java.math.RoundingMode.HALF_UP);
            }
            BizWoBomSnapshot snap = bomSnapshotMapper.selectOne(
                    new LambdaQueryWrapper<BizWoBomSnapshot>()
                            .eq(BizWoBomSnapshot::getWorkOrderId, woId)
                            .eq(BizWoBomSnapshot::getMaterialId, snapMatId)
                            .last("LIMIT 1"));
            if (snap != null) {
                snap.setIssuedTaskQty((snap.getIssuedTaskQty() != null ? snap.getIssuedTaskQty() : BigDecimal.ZERO).add(snapQty));
                bomSnapshotMapper.updateById(snap);
            }
        }
    }

    /**
     * 无 BOM 工单缺料检测: 不写库, 只返回缺料明细.
     * @return [{materialId, materialNo, materialName, spec, unit, needQty, stockQty, shortageQty, alreadyRequested, displayLine}]
     *         alreadyRequested=true 表示同工单+同物料已经有未办结的采购申请, 前端可以提示但允许跳过.
     */
    public List<Map<String, Object>> checkIssueShortage(Long woId, List<Map<String, Object>> items) {
        BizWorkOrder wo = getById(woId);
        if (wo == null) throw new BusinessException("工单不存在");
        Set<Long> matIds = items.stream()
                .map(i -> Long.valueOf(i.get("materialId").toString()))
                .collect(Collectors.toSet());
        Map<Long, BizMaterial> matMap = matIds.isEmpty() ? Map.of() :
                materialMapper.selectBatchIds(matIds).stream()
                        .collect(Collectors.toMap(BizMaterial::getId, m -> m, (a, b) -> a));
        return detectShortage(wo, items, matMap);
    }

    /**
     * 无 BOM 工单缺料确认后, 由生产人员填写需求日期 + 用途, 批量生成采购申请.
     * body = { items: [{materialId, shortageQty, ...}], expectedDate: "YYYY-MM-DD", purpose: "用途说明" }
     * @return 生成的采购申请数量
     */
    @Transactional(rollbackFor = Exception.class)
    public int generatePurchaseRequestsForShortage(Long woId, Map<String, Object> body) {
        BizWorkOrder wo = getById(woId);
        if (wo == null) throw new BusinessException("工单不存在");

        Object expectedDateObj = body.get("expectedDate");
        if (expectedDateObj == null || !StringUtils.hasText(expectedDateObj.toString())) {
            throw new BusinessException("请填写需求日期");
        }
        java.time.LocalDate expectedDate;
        try {
            expectedDate = java.time.LocalDate.parse(expectedDateObj.toString());
        } catch (Exception e) {
            throw new BusinessException("需求日期格式错误");
        }
        if (expectedDate.isBefore(java.time.LocalDate.now())) {
            throw new BusinessException("需求日期不能早于今天");
        }
        String purpose = body.get("purpose") != null ? body.get("purpose").toString().trim() : "";
        if (!StringUtils.hasText(purpose)) throw new BusinessException("请填写用途");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> shortageItems = (List<Map<String, Object>>) body.get("items");
        if (shortageItems == null || shortageItems.isEmpty()) throw new BusinessException("缺料清单为空");

        int created = 0;
        for (Map<String, Object> s : shortageItems) {
            Long materialId = Long.valueOf(s.get("materialId").toString());
            BigDecimal shortage = new BigDecimal(s.get("shortageQty").toString());
            if (shortage.compareTo(BigDecimal.ZERO) <= 0) continue;

            // 去重: 同工单 + 同物料 已有未办结的采购申请, 跳过
            long existing = purchaseRequestService.count(
                    new LambdaQueryWrapper<com.aimes.modules.purchase.entity.BizPurchaseRequest>()
                            .eq(com.aimes.modules.purchase.entity.BizPurchaseRequest::getSourceType, "work_order")
                            .eq(com.aimes.modules.purchase.entity.BizPurchaseRequest::getSourceNo, wo.getWoNo())
                            .eq(com.aimes.modules.purchase.entity.BizPurchaseRequest::getMaterialId, materialId)
                            .in(com.aimes.modules.purchase.entity.BizPurchaseRequest::getStatus, java.util.List.of("draft", "pending", "approved"))
                            .eq(com.aimes.modules.purchase.entity.BizPurchaseRequest::getDeleted, 0));
            if (existing > 0) continue;

            BizMaterial mat = materialMapper.selectById(materialId);
            com.aimes.modules.purchase.entity.BizPurchaseRequest pr = new com.aimes.modules.purchase.entity.BizPurchaseRequest();
            pr.setMaterialId(materialId);
            pr.setMaterialName(mat != null ? mat.getMaterialName() : null);
            pr.setSpec(mat != null ? mat.getSpec() : null);
            pr.setUnit(mat != null ? mat.getUnit() : null);
            pr.setQuantity(shortage);
            pr.setSourceType("work_order");
            pr.setSourceNo(wo.getWoNo());
            pr.setExpectedDate(expectedDate);
            pr.setPurpose(purpose);
            purchaseRequestService.create(pr);
            created++;
        }
        if (created == 0) throw new BusinessException("所选缺料均已存在未办结的采购申请, 未重复生成");
        return created;
    }

    /**
     * 工单采购进度: 列出该工单触发的所有采购申请, 每条带上转单后的 PO 状态、收料量 / 合格量 / 入库量.
     * 关联链: BizPurchaseRequest (sourceType=work_order, sourceNo=woNo)
     *      → BizPurchaseOrderItem (requestId=pr.id)
     *      → BizPurchaseOrder
     *      → BizPurchaseReceipt (purchaseOrderId=po.id, materialId 同 PR)
     */
    public List<Map<String, Object>> getPurchaseProgress(Long woId) {
        BizWorkOrder wo = getById(woId);
        if (wo == null) throw new BusinessException("工单不存在");

        List<com.aimes.modules.purchase.entity.BizPurchaseRequest> prs = purchaseRequestMapper.selectList(
                new LambdaQueryWrapper<com.aimes.modules.purchase.entity.BizPurchaseRequest>()
                        .eq(com.aimes.modules.purchase.entity.BizPurchaseRequest::getSourceType, "work_order")
                        .eq(com.aimes.modules.purchase.entity.BizPurchaseRequest::getSourceNo, wo.getWoNo())
                        .eq(com.aimes.modules.purchase.entity.BizPurchaseRequest::getDeleted, 0)
                        .orderByDesc(com.aimes.modules.purchase.entity.BizPurchaseRequest::getCreateTime));
        if (prs.isEmpty()) return List.of();

        // 批量预加载物料 / PO item / PO / receipt
        Set<Long> prIds = prs.stream().map(com.aimes.modules.purchase.entity.BizPurchaseRequest::getId).collect(Collectors.toSet());
        Set<Long> matIds = prs.stream().map(com.aimes.modules.purchase.entity.BizPurchaseRequest::getMaterialId).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, BizMaterial> matMap = matIds.isEmpty() ? Map.of() :
                materialMapper.selectBatchIds(matIds).stream()
                        .collect(Collectors.toMap(BizMaterial::getId, m -> m, (a, b) -> a));

        // PR → PO item (一对一, 通常 PR 只生成一条 PO item)
        List<BizPurchaseOrderItem> poItems = purchaseOrderItemMapper.selectList(
                new LambdaQueryWrapper<BizPurchaseOrderItem>()
                        .in(BizPurchaseOrderItem::getRequestId, prIds)
                        .eq(BizPurchaseOrderItem::getDeleted, 0));
        Map<Long, BizPurchaseOrderItem> prToPoItem = poItems.stream()
                .collect(Collectors.toMap(BizPurchaseOrderItem::getRequestId, i -> i, (a, b) -> a));

        Set<Long> poIds = poItems.stream().map(BizPurchaseOrderItem::getPurchaseOrderId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, BizPurchaseOrder> poMap = poIds.isEmpty() ? Map.of() :
                purchaseOrderMapper.selectBatchIds(poIds).stream()
                        .collect(Collectors.toMap(BizPurchaseOrder::getId, p -> p, (a, b) -> a));

        // 收料: 按 PO + 物料 维度汇总
        List<com.aimes.modules.purchase.entity.BizPurchaseReceipt> receipts = poIds.isEmpty() ? List.of() :
                purchaseReceiptMapper.selectList(
                        new LambdaQueryWrapper<com.aimes.modules.purchase.entity.BizPurchaseReceipt>()
                                .in(com.aimes.modules.purchase.entity.BizPurchaseReceipt::getPurchaseOrderId, poIds)
                                .eq(com.aimes.modules.purchase.entity.BizPurchaseReceipt::getDeleted, 0));

        // key: poId|materialId
        Map<String, BigDecimal[]> receiptSumMap = new HashMap<>();
        for (com.aimes.modules.purchase.entity.BizPurchaseReceipt r : receipts) {
            String key = r.getPurchaseOrderId() + "|" + r.getMaterialId();
            BigDecimal[] sum = receiptSumMap.computeIfAbsent(key, k -> new BigDecimal[]{
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
            });
            // [actual, qualified, stockIn, expected]
            sum[0] = sum[0].add(r.getActualQty() != null ? r.getActualQty() : BigDecimal.ZERO);
            sum[1] = sum[1].add(r.getQualifiedQty() != null ? r.getQualifiedQty() : BigDecimal.ZERO);
            sum[2] = sum[2].add(r.getStockInQty() != null ? r.getStockInQty() : BigDecimal.ZERO);
            sum[3] = sum[3].add(r.getExpectedQty() != null ? r.getExpectedQty() : BigDecimal.ZERO);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (com.aimes.modules.purchase.entity.BizPurchaseRequest pr : prs) {
            BizMaterial mat = matMap.get(pr.getMaterialId());
            BizPurchaseOrderItem poItem = prToPoItem.get(pr.getId());
            BizPurchaseOrder po = poItem != null ? poMap.get(poItem.getPurchaseOrderId()) : null;
            BigDecimal[] sum = (po != null && pr.getMaterialId() != null)
                    ? receiptSumMap.get(po.getId() + "|" + pr.getMaterialId())
                    : null;

            BigDecimal actual = sum != null ? sum[0] : BigDecimal.ZERO;
            BigDecimal qualified = sum != null ? sum[1] : BigDecimal.ZERO;
            BigDecimal stockIn = sum != null ? sum[2] : BigDecimal.ZERO;
            BigDecimal qty = pr.getQuantity() != null ? pr.getQuantity() : BigDecimal.ZERO;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("requestId", pr.getId());
            row.put("requestNo", pr.getRequestNo());
            row.put("materialId", pr.getMaterialId());
            row.put("materialNo", mat != null ? mat.getMaterialNo() : "");
            row.put("materialName", mat != null ? mat.getMaterialName() : pr.getMaterialName());
            row.put("spec", mat != null ? mat.getSpec() : pr.getSpec());
            row.put("unit", mat != null ? mat.getUnit() : pr.getUnit());
            row.put("quantity", qty);
            row.put("expectedDate", pr.getExpectedDate());
            row.put("purpose", pr.getPurpose());
            row.put("requestStatus", pr.getStatus());
            row.put("createTime", pr.getCreateTime());
            // 关联的采购订单
            row.put("poId", po != null ? po.getId() : null);
            row.put("poNo", po != null ? po.getPoNo() : null);
            row.put("poStatus", po != null ? po.getStatus() : null);
            row.put("poItemQty", poItem != null ? poItem.getQuantity() : null);
            // 到货汇总
            row.put("actualQty", actual);
            row.put("qualifiedQty", qualified);
            row.put("stockInQty", stockIn);
            // 整体阶段标签 — 给前端展示用
            row.put("stage", calcPurchaseStage(pr.getStatus(), po, stockIn, qty));
            result.add(row);
        }
        return result;
    }

    private String calcPurchaseStage(String prStatus, BizPurchaseOrder po, BigDecimal stockIn, BigDecimal qty) {
        if (stockIn != null && qty != null && qty.compareTo(BigDecimal.ZERO) > 0 && stockIn.compareTo(qty) >= 0) return "已到货";
        if (po != null && stockIn != null && stockIn.compareTo(BigDecimal.ZERO) > 0) return "部分到货";
        if (po != null) return "已下采购单";
        if ("approved".equals(prStatus)) return "待下采购单";
        if ("pending".equals(prStatus)) return "待审批";
        if ("draft".equals(prStatus)) return "草稿";
        if ("rejected".equals(prStatus)) return "已驳回";
        if ("cancelled".equals(prStatus)) return "已取消";
        return prStatus;
    }

    /**
     * 公共缺料检测: 用全仓在库总量(忽略锁库) 对比领料需求, 返回缺料行明细.
     * 同步检查同工单+同物料是否已有未办结的采购申请, alreadyRequested 字段提示前端.
     */
    private List<Map<String, Object>> detectShortage(BizWorkOrder wo, List<Map<String, Object>> items, Map<Long, BizMaterial> matMap) {
        List<Map<String, Object>> shortageList = new ArrayList<>();
        for (Map<String, Object> item : items) {
            Long materialId = Long.valueOf(item.get("materialId").toString());
            BigDecimal needQty = new BigDecimal(item.get("qty").toString());
            if (needQty.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal stock = inventoryService.getTotalStockQty(materialId);
            if (needQty.compareTo(stock) <= 0) continue;
            BigDecimal shortage = needQty.subtract(stock);
            BizMaterial mat = matMap.get(materialId);
            String matName = mat != null ? mat.getMaterialName() : ("物料#" + materialId);
            String matNo = mat != null ? mat.getMaterialNo() : "";
            String unit = mat != null && mat.getUnit() != null ? mat.getUnit() : "";

            long existing = purchaseRequestService.count(
                    new LambdaQueryWrapper<com.aimes.modules.purchase.entity.BizPurchaseRequest>()
                            .eq(com.aimes.modules.purchase.entity.BizPurchaseRequest::getSourceType, "work_order")
                            .eq(com.aimes.modules.purchase.entity.BizPurchaseRequest::getSourceNo, wo.getWoNo())
                            .eq(com.aimes.modules.purchase.entity.BizPurchaseRequest::getMaterialId, materialId)
                            .in(com.aimes.modules.purchase.entity.BizPurchaseRequest::getStatus, java.util.List.of("draft", "pending", "approved"))
                            .eq(com.aimes.modules.purchase.entity.BizPurchaseRequest::getDeleted, 0));

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("materialId", materialId);
            row.put("materialNo", matNo);
            row.put("materialName", matName);
            row.put("spec", mat != null ? mat.getSpec() : "");
            row.put("unit", unit);
            row.put("needQty", needQty);
            row.put("stockQty", stock);
            row.put("shortageQty", shortage);
            row.put("alreadyRequested", existing > 0);
            row.put("displayLine", matName + (StringUtils.hasText(matNo) ? "[" + matNo + "]" : "")
                    + " 需求" + needQty.stripTrailingZeros().toPlainString()
                    + " / 在库" + stock.stripTrailingZeros().toPlainString()
                    + " / 缺 " + shortage.stripTrailingZeros().toPlainString() + unit
                    + (existing > 0 ? "(已有未办结申请)" : ""));
            shortageList.add(row);
        }
        return shortageList;
    }

    /**
     * 批量退料：生成 production_return 库存任务，记录已生成退料任务数
     * @param items [{materialId, warehouseId, qty}]
     */
    @Transactional
    public void returnMaterial(Long woId, List<Map<String, Object>> items) {
        BizWorkOrder wo = getById(woId);
        if (wo == null) throw new BusinessException("工单不存在");
        if (!"in_progress".equals(wo.getStatus())) {
            throw new BusinessException("只有生产中的工单可以退料");
        }

        Set<Long> matIds = items.stream()
                .map(i -> Long.valueOf(i.get("materialId").toString()))
                .collect(Collectors.toSet());
        Map<Long, BizMaterial> matMap = matIds.isEmpty() ? Map.of() :
                materialMapper.selectBatchIds(matIds).stream()
                        .collect(Collectors.toMap(BizMaterial::getId, m -> m, (a, b) -> a));

        List<Map<String, Object>> taskItems = new ArrayList<>();
        for (Map<String, Object> item : items) {
            Long materialId = Long.valueOf(item.get("materialId").toString());
            BigDecimal qty = new BigDecimal(item.get("qty").toString());
            if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;

            BizMaterial mat = matMap.get(materialId);
            Map<String, Object> ti = new LinkedHashMap<>();
            ti.put("materialId", materialId);
            ti.put("materialNo", mat != null ? mat.getMaterialNo() : null);
            ti.put("materialName", mat != null ? mat.getMaterialName() : null);
            ti.put("spec", mat != null ? mat.getSpec() : null);
            ti.put("unit", mat != null ? mat.getUnit() : null);
            ti.put("quantity", qty);
            if (item.get("warehouseId") != null) ti.put("warehouseId", Long.valueOf(item.get("warehouseId").toString()));
            taskItems.add(ti);
        }
        if (taskItems.isEmpty()) throw new BusinessException("退料明细不能为空");

        invTaskService.createFromWorkOrderReturn(woId, wo.getWoNo(), taskItems,
                com.aimes.common.utils.SecurityUtil.getCurrentUserId());

        // 更新BOM快照的已生成退料任务数
        for (Map<String, Object> ti : taskItems) {
            Long materialId = Long.valueOf(ti.get("materialId").toString());
            BigDecimal qty = new BigDecimal(ti.get("quantity").toString());
            BizWoBomSnapshot snap = bomSnapshotMapper.selectOne(
                    new LambdaQueryWrapper<BizWoBomSnapshot>()
                            .eq(BizWoBomSnapshot::getWorkOrderId, woId)
                            .eq(BizWoBomSnapshot::getMaterialId, materialId)
                            .last("LIMIT 1"));
            if (snap != null) {
                snap.setReturnedTaskQty((snap.getReturnedTaskQty() != null ? snap.getReturnedTaskQty() : BigDecimal.ZERO).add(qty));
                bomSnapshotMapper.updateById(snap);
            }
        }
    }

    // ==================== 工序进度卡 ====================

    /**
     * 按工序分组查询所有未完成工单的工序生产进度
     *
     * @param stepName 工序名称（可选，模糊搜索）
     */
    public List<Map<String, Object>> getProcessProgress(String stepName) {
        // 1. 查询所有未完成工单
        List<BizWorkOrder> workOrders = list(new LambdaQueryWrapper<BizWorkOrder>()
                .notIn(BizWorkOrder::getStatus, List.of("completed", "cancelled")));
        if (workOrders.isEmpty()) return List.of();

        List<Long> woIds = workOrders.stream().map(BizWorkOrder::getId).collect(Collectors.toList());

        // 2. 查询相关工序任务
        LambdaQueryWrapper<BizWoProcessTask> taskWrapper = new LambdaQueryWrapper<BizWoProcessTask>()
                .in(BizWoProcessTask::getWorkOrderId, woIds)
                .orderByAsc(BizWoProcessTask::getStepGroup)
                .orderByAsc(BizWoProcessTask::getStepNo);
        if (StringUtils.hasText(stepName)) {
            taskWrapper.like(BizWoProcessTask::getStepName, stepName);
        }
        List<BizWoProcessTask> tasks = taskMapper.selectList(taskWrapper);
        if (tasks.isEmpty()) return List.of();

        // 3. 批量加载产品信息
        Set<Long> productIds = workOrders.stream().map(BizWorkOrder::getProductId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, BizMaterial> productMap = productIds.isEmpty() ? Map.of() :
                materialMapper.selectBatchIds(productIds).stream()
                        .collect(Collectors.toMap(BizMaterial::getId, m -> m, (a, b) -> a));

        // 4. 批量加载订单编号
        Set<Long> orderIds = workOrders.stream().map(BizWorkOrder::getOrderId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> orderNoMap = orderIds.isEmpty() ? Map.of() :
                salesOrderService.listByIds(orderIds).stream()
                        .collect(Collectors.toMap(o -> o.getId(), o -> o.getOrderNo(), (a, b) -> a));

        // 5. 构建工单查找 Map
        Map<Long, BizWorkOrder> woMap = workOrders.stream()
                .collect(Collectors.toMap(BizWorkOrder::getId, w -> w, (a, b) -> a));

        // 6. 按工序名称分组
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (BizWoProcessTask task : tasks) {
            BizWorkOrder wo = woMap.get(task.getWorkOrderId());
            if (wo == null) continue;

            BizMaterial product = productMap.get(wo.getProductId());
            String orderNo = wo.getOrderId() != null ? orderNoMap.getOrDefault(wo.getOrderId(), "") : "";

            java.math.BigDecimal planQty = task.getPlanQty() != null ? task.getPlanQty() : java.math.BigDecimal.ZERO;
            java.math.BigDecimal qualifiedQty = task.getQualifiedQty() != null ? task.getQualifiedQty() : java.math.BigDecimal.ZERO;
            int progress = planQty.compareTo(java.math.BigDecimal.ZERO) > 0
                    ? qualifiedQty.multiply(java.math.BigDecimal.valueOf(100))
                            .divide(planQty, 0, java.math.RoundingMode.HALF_UP).intValue()
                    : 0;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("taskId", task.getId());
            item.put("woId", wo.getId());
            item.put("orderNo", orderNo);
            item.put("woNo", wo.getWoNo());
            item.put("productName", product != null ? product.getMaterialName() : "");
            item.put("productSpec", product != null && product.getSpec() != null ? product.getSpec() : "");
            item.put("productNo", product != null && product.getMaterialNo() != null ? product.getMaterialNo() : "");
            item.put("planQty", planQty);
            item.put("qualifiedQty", qualifiedQty);
            item.put("progress", Math.min(progress, 100));
            item.put("taskStatus", task.getStatus());
            item.put("woStatus", wo.getStatus());
            item.put("stepName", task.getStepName());
            item.put("stepNo", task.getStepNo());
            item.put("assignType", task.getAssignType());
            item.put("assignedUserName", task.getAssignedUserName());
            item.put("assignedTeamName", task.getAssignedTeamName());

            grouped.computeIfAbsent(task.getStepName() != null ? task.getStepName() : "未命名工序",
                    k -> new ArrayList<>()).add(item);
        }

        // 7. 转换为分组列表
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : grouped.entrySet()) {
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("stepName", entry.getKey());
            group.put("items", entry.getValue());
            group.put("total", entry.getValue().size());
            result.add(group);
        }
        return result;
    }

    // ==================== 工单变更 ====================

    /**
     * 查询工单变更历史
     */
    public List<BizWoChange> getChanges(Long workOrderId) {
        return woChangeMapper.selectList(
                new LambdaQueryWrapper<BizWoChange>()
                        .eq(BizWoChange::getWorkOrderId, workOrderId)
                        .orderByDesc(BizWoChange::getCreateTime));
    }

    /**
     * 创建工单变更记录
     * 参数变更和责任人变更直接执行，其他类型需要审批
     */
    @Transactional
    public void createChange(BizWoChange change) {
        String type = change.getChangeType();
        // 读"系统设置 → 审批流"里的"工单变更"开关:
        // 配置存在 sys_config 表 group=approval, key=approval_wo_change,
        // 值是 JSON {"enabled":true/false,...}
        boolean needApproval = isWoChangeApprovalEnabled();
        // 参数变更和责任人变更始终直接执行
        boolean directExecute = !needApproval
                || "modify_params".equals(type)
                || "modify_person".equals(type);
        change.setStatus(directExecute ? "approved" : "pending");
        // 把 changeContent 里的 JSON 转成中文摘要(给前端"变更历史"友好显示);
        // 后续 applyChangeToWorkOrder 会重新解析原 JSON 取值,所以原始 JSON 在执行前已经处理完毕
        String chineseSummary = buildChangeSummary(change);
        if (chineseSummary != null && !chineseSummary.isBlank()) {
            // 先执行变更(用原 JSON 解析),再覆盖 changeContent 为中文
            String originalJson = change.getChangeContent();
            woChangeMapper.insert(change);
            if (directExecute) {
                applyChangeToWorkOrder(change);
            } else {
                notificationService.notifyRole("admin", "工单变更待审批",
                        "工单变更申请,变更类型:" + woChangeTypeLabel(type) + ",请审批",
                        "warning", "work_order", change.getWorkOrderId());
            }
            // 落库后改成中文摘要
            change.setChangeContent(chineseSummary);
            woChangeMapper.updateById(change);
            return;
        }
        woChangeMapper.insert(change);

        if (directExecute) {
            // 直接执行:把变更应用到工单实体
            applyChangeToWorkOrder(change);
        } else {
            // 需审批:发通知
            notificationService.notifyRole("admin", "工单变更待审批",
                    "工单变更申请，变更类型：" + type + "，请审批",
                    "warning", "work_order", change.getWorkOrderId());
        }
    }

    /** 直接执行场景:根据 changeContent(JSON) 应用变更到 BizWorkOrder */
    private void applyChangeToWorkOrder(BizWoChange change) {
        if (change.getChangeContent() == null || change.getChangeContent().isBlank()) return;
        BizWorkOrder wo = getById(change.getWorkOrderId());
        if (wo == null) return;
        try {
            com.fasterxml.jackson.databind.JsonNode body =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(change.getChangeContent());
            String type = change.getChangeType();
            switch (type) {
                case "modify_qty" -> {
                    java.math.BigDecimal newQty = body.has("newPlanQty") && !body.get("newPlanQty").isNull()
                            ? new java.math.BigDecimal(body.get("newPlanQty").asText()) : null;
                    if (newQty != null && newQty.compareTo(java.math.BigDecimal.ZERO) > 0) {
                        java.math.BigDecimal oldQty = wo.getPlanQty() != null ? wo.getPlanQty() : java.math.BigDecimal.ONE;
                        wo.setPlanQty(newQty);
                        updateById(wo);
                        // 同步更新所有工序任务的 planQty
                        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<BizWoProcessTask> upd =
                                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<BizWoProcessTask>()
                                        .eq(BizWoProcessTask::getWorkOrderId, wo.getId())
                                        .set(BizWoProcessTask::getPlanQty, newQty);
                        taskMapper.update(null, upd);
                        // 按比例同步 BOM 快照的最大领料数(requiredQty)
                        if (oldQty.compareTo(java.math.BigDecimal.ZERO) > 0) {
                            java.math.BigDecimal ratio = newQty.divide(oldQty, 6, java.math.RoundingMode.HALF_UP);
                            List<BizWoBomSnapshot> snaps = bomSnapshotMapper.selectList(
                                    new LambdaQueryWrapper<BizWoBomSnapshot>()
                                            .eq(BizWoBomSnapshot::getWorkOrderId, wo.getId()));
                            for (BizWoBomSnapshot s : snaps) {
                                if (s.getRequiredQty() == null) continue;
                                java.math.BigDecimal newRequired = s.getRequiredQty().multiply(ratio).setScale(4, java.math.RoundingMode.HALF_UP);
                                s.setRequiredQty(newRequired);
                                bomSnapshotMapper.updateById(s);
                            }
                        }
                    }
                }
                case "modify_date" -> {
                    String newDate = body.has("newDeliveryDate") && !body.get("newDeliveryDate").isNull()
                            ? body.get("newDeliveryDate").asText(null) : null;
                    log.info("工单变更交期: woId={} woNo={} orderId={} newDate={}",
                            wo.getId(), wo.getWoNo(), wo.getOrderId(), newDate);
                    if (newDate != null && !newDate.isBlank()) {
                        java.time.LocalDate newDd = java.time.LocalDate.parse(newDate);
                        wo.setDeliveryDate(newDd);
                        updateById(wo);
                        // 联动: 工单交期延后时, 同步把关联销售订单的交期也往后推; 提前时不动
                        if (wo.getOrderId() != null) {
                            try {
                                BizSalesOrder so = salesOrderService.getById(wo.getOrderId());
                                log.info("工单交期变更 → 查到销售订单: {}", so == null ? "null" : (so.getOrderNo() + " 当前交期=" + so.getDeliveryDate()));
                                if (so != null) {
                                    if (so.getOriginalDeliveryDate() == null) {
                                        so.setOriginalDeliveryDate(so.getDeliveryDate());
                                    }
                                    java.time.LocalDate origSoDd = so.getDeliveryDate();
                                    if (origSoDd == null || newDd.isAfter(origSoDd)) {
                                        so.setDeliveryDate(newDd);
                                        boolean ok = salesOrderService.updateById(so);
                                        log.info("工单变更交期 → 同步销售订单 {} 交期 {} -> {} (updateById={})",
                                                so.getOrderNo(), origSoDd, newDd, ok);
                                        // 在销售订单"操作日志"里加一条延期记录
                                        salesOrderService.addLog(so.getId(), "delay_delivery",
                                                "工单 " + wo.getWoNo() + " 交期变更触发延期: " + origSoDd + " → " + newDd);
                                        // 在销售订单"变更记录"里也写一条 (auto approved, 不走审批)
                                        try {
                                            com.aimes.modules.order.entity.BizOrderChange oc =
                                                    new com.aimes.modules.order.entity.BizOrderChange();
                                            oc.setOrderId(so.getId());
                                            oc.setChangeType("delay_delivery");
                                            oc.setStatus("approved");
                                            oc.setChangeReason("工单 " + wo.getWoNo() + " 交期变更触发");
                                            String json = String.format(
                                                    "{\"oldDeliveryDate\":\"%s\",\"newDeliveryDate\":\"%s\",\"sourceWoNo\":\"%s\",\"sourceWoId\":%d}",
                                                    origSoDd, newDd, wo.getWoNo(), wo.getId());
                                            oc.setChangeContent(json);
                                            oc.setApprovedAt(java.time.LocalDateTime.now());
                                            orderChangeMapper.insert(oc);
                                        } catch (Exception ignore) {
                                            log.warn("写入销售订单变更记录失败: soId={}, err={}", so.getId(), ignore.getMessage());
                                        }
                                    } else {
                                        log.info("工单变更交期: 新交期 {} 未晚于销售订单原交期 {},不同步", newDd, origSoDd);
                                    }
                                }
                            } catch (Exception e) {
                                log.error("工单交期变更同步销售订单交期失败: woId=" + wo.getId(), e);
                            }
                        } else {
                            log.info("工单变更交期: 工单未关联销售订单(备库生产), 跳过同步");
                        }
                    }
                }
                case "outsource" -> {
                    // 工序转外协:一步建外协订单
                    Long processTaskId = body.has("processTaskId") && !body.get("processTaskId").isNull()
                            ? body.get("processTaskId").asLong() : null;
                    if (processTaskId == null) throw new BusinessException("请选择需要转外协的工序");
                    Long supplierId = body.has("outsourceSupplierId") && !body.get("outsourceSupplierId").isNull()
                            ? body.get("outsourceSupplierId").asLong() : null;
                    if (supplierId == null) throw new BusinessException("请选择外协供应商");
                    java.math.BigDecimal totalQty = body.has("outsourceQty") && !body.get("outsourceQty").isNull()
                            ? new java.math.BigDecimal(body.get("outsourceQty").asText()) : null;
                    if (totalQty == null || totalQty.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                        throw new BusinessException("加工数量必须大于 0");
                    }
                    java.math.BigDecimal unitPrice = body.has("outsourceUnitPrice") && !body.get("outsourceUnitPrice").isNull()
                            ? new java.math.BigDecimal(body.get("outsourceUnitPrice").asText()) : java.math.BigDecimal.ZERO;
                    java.math.BigDecimal taxRate = body.has("outsourceTaxRate") && !body.get("outsourceTaxRate").isNull()
                            ? new java.math.BigDecimal(body.get("outsourceTaxRate").asText()) : new java.math.BigDecimal("13");
                    String deliveryDateStr = body.has("outsourceDeliveryDate") && !body.get("outsourceDeliveryDate").isNull()
                            ? body.get("outsourceDeliveryDate").asText(null) : null;
                    if (deliveryDateStr == null || deliveryDateStr.isBlank()) {
                        throw new BusinessException("请选择交付日期");
                    }

                    BizWoProcessTask task = taskMapper.selectById(processTaskId);
                    if (task == null || !wo.getId().equals(task.getWorkOrderId())) {
                        throw new BusinessException("工序任务不存在或不属于本工单");
                    }

                    // 1) 创建外协订单(直接 confirmed 跳过草稿)
                    com.aimes.modules.outsource.entity.BizOutsourceOrder oo =
                            new com.aimes.modules.outsource.entity.BizOutsourceOrder();
                    oo.setSupplierId(supplierId);
                    oo.setProductId(wo.getProductId());
                    oo.setStepName(task.getStepName());
                    oo.setTotalQty(totalQty);
                    oo.setUnitPrice(unitPrice);
                    oo.setTaxRate(taxRate);
                    oo.setDeliveryDate(java.time.LocalDate.parse(deliveryDateStr));
                    oo.setWorkOrderId(wo.getId());
                    oo.setTaskId(task.getId());
                    if (change.getChangeReason() != null) oo.setRemark(change.getChangeReason());
                    outsourceOrderService.create(oo, null);

                    // 2) 把工序任务标记为外协 + 关联订单
                    task.setIsOutsourced(1);
                    task.setIsOutsourceable(1); // 同步置可外协,工艺路线列显示状态而非"-"
                    task.setOutsourceOrderId(oo.getId());
                    task.setOutsourceStatus("ordered");
                    taskMapper.updateById(task);

                    log.info("工单[{}] 工序[{}] 已转外协,生成外协订单[{}]",
                            wo.getWoNo(), task.getStepName(), oo.getOrderNo());

                    // 若前道工序组已有合格报工，补触发外协虚拟半成品入库
                    // (避免因工单变更晚于前道报工导致外协发料无物料可用)
                    Integer currentTaskGroup = task.getStepGroup();
                    if (currentTaskGroup != null) {
                        List<BizWoProcessTask> allWoTasks = taskMapper.selectList(
                                new LambdaQueryWrapper<BizWoProcessTask>()
                                        .eq(BizWoProcessTask::getWorkOrderId, wo.getId())
                                        .ne(BizWoProcessTask::getStatus, "cancelled"));
                        Integer prevGroup = allWoTasks.stream()
                                .map(BizWoProcessTask::getStepGroup)
                                .filter(g -> g != null && g < currentTaskGroup)
                                .max(Integer::compareTo)
                                .orElse(null);
                        if (prevGroup != null) {
                            final Integer prevGroupFinal = prevGroup;
                            List<BizWoProcessTask> prevGroupTasks = allWoTasks.stream()
                                    .filter(t -> prevGroupFinal.equals(t.getStepGroup()))
                                    .toList();
                            BigDecimal minQualified = prevGroupTasks.stream()
                                    .map(t -> t.getQualifiedQty() != null ? t.getQualifiedQty() : BigDecimal.ZERO)
                                    .min(BigDecimal::compareTo)
                                    .orElse(BigDecimal.ZERO);
                            if (minQualified.compareTo(BigDecimal.ZERO) > 0 && !prevGroupTasks.isEmpty()) {
                                BizWoProcessTask prevTask = prevGroupTasks.get(0);
                                try {
                                    outsourceChainService.handlePrevStepReported(wo.getId(), prevTask, minQualified);
                                    log.info("转外协后补触发外协链路: workOrderId={}, taskId={}, prevMinQty={}",
                                            wo.getId(), task.getId(), minQualified);
                                } catch (Exception e) {
                                    log.warn("转外协后补触发外协链路失败: taskId={}", task.getId(), e);
                                }
                            }
                        }
                    }
                }
                default -> {
                    // modify_params / modify_person 暂未定义具体字段,留待业务扩展
                }
            }
        } catch (Exception e) {
            log.error("应用工单变更失败 changeId={}", change.getId(), e);
            throw new BusinessException("应用变更失败: " + e.getMessage());
        }
    }

    /** 把变更类型转成中文标签 */
    private String woChangeTypeLabel(String type) {
        if (type == null) return "";
        return switch (type) {
            case "outsource" -> "工序转外协";
            case "modify_qty" -> "修改计划数量";
            case "modify_date" -> "修改交货日期";
            case "modify_params" -> "参数变更";
            case "modify_person" -> "责任人变更";
            default -> type;
        };
    }

    /** 把 changeContent JSON 转成中文摘要 */
    private String buildChangeSummary(BizWoChange change) {
        String type = change.getChangeType();
        String content = change.getChangeContent();
        if (content == null || content.isBlank()) return null;
        try {
            com.fasterxml.jackson.databind.JsonNode body =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(content);
            StringBuilder sb = new StringBuilder();
            sb.append("【").append(woChangeTypeLabel(type)).append("】");
            switch (type) {
                case "modify_qty" -> {
                    if (body.has("newPlanQty")) sb.append("新计划数量: ").append(body.get("newPlanQty").asText());
                }
                case "modify_date" -> {
                    if (body.has("newDeliveryDate")) sb.append("新交货日期: ").append(body.get("newDeliveryDate").asText());
                }
                case "outsource" -> {
                    if (body.has("processTaskId")) {
                        try {
                            BizWoProcessTask t = taskMapper.selectById(body.get("processTaskId").asLong());
                            if (t != null) sb.append("工序: ").append(t.getStepName()).append(";");
                        } catch (Exception ignore) {}
                    }
                    if (body.has("outsourceSupplierId")) {
                        try {
                            com.aimes.modules.master.entity.BizSupplier sup =
                                    supplierMapper.selectById(body.get("outsourceSupplierId").asLong());
                            if (sup != null) sb.append("供应商: ").append(sup.getSupplierName()).append(";");
                        } catch (Exception ignore) {}
                    }
                    if (body.has("outsourceQty")) sb.append("数量: ").append(body.get("outsourceQty").asText()).append(";");
                    if (body.has("outsourceUnitPrice")) sb.append("单价: ¥").append(body.get("outsourceUnitPrice").asText()).append(";");
                    if (body.has("outsourceTaxRate")) sb.append("税率: ").append(body.get("outsourceTaxRate").asText()).append("%;");
                    if (body.has("outsourceDeliveryDate")) sb.append("交付日期: ").append(body.get("outsourceDeliveryDate").asText());
                }
                default -> { return null; }
            }
            String reason = body.has("reason") && !body.get("reason").isNull() ? body.get("reason").asText() : null;
            if (reason != null && !reason.isBlank()) sb.append(" / 原因: ").append(reason);
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** 解析系统设置中的"工单变更需要审批"开关,默认 false */
    private boolean isWoChangeApprovalEnabled() {
        try {
            com.aimes.modules.system.entity.SysConfig cfg = sysConfigService.getByKey("approval_wo_change");
            if (cfg == null || cfg.getConfigValue() == null || cfg.getConfigValue().isBlank()) {
                return false;
            }
            String v = cfg.getConfigValue().trim();
            // 兼容纯布尔字符串
            if ("true".equalsIgnoreCase(v)) return true;
            if ("false".equalsIgnoreCase(v)) return false;
            // JSON 形式 {"enabled":true,...}
            com.fasterxml.jackson.databind.JsonNode node =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(v);
            return node.path("enabled").asBoolean(false);
        } catch (Exception e) {
            return false;
        }
    }

    /** 将 JSON 字符串解析为 List，解析失败则返回 null */
    @SuppressWarnings("unchecked")
    private List<Object> parseJsonList(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, List.class);
        } catch (Exception e) {
            return null;
        }
    }
}
