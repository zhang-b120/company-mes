package com.aimes.modules.order.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.PageResult;
import com.aimes.common.utils.CodeUtil;
import com.aimes.common.utils.SecurityUtil;
import com.aimes.modules.order.entity.BizSalesOrder;
import com.aimes.modules.order.entity.BizSalesOrderItem;
import com.aimes.modules.order.mapper.BizSalesOrderItemMapper;
import com.aimes.modules.order.mapper.BizSalesOrderMapper;
import com.aimes.modules.master.entity.BizCustomer;
import com.aimes.modules.master.entity.BizMaterial;
import com.aimes.modules.master.entity.BizProduct;
import com.aimes.modules.master.mapper.BizCustomerMapper;
import com.aimes.modules.master.mapper.BizMaterialMapper;
import com.aimes.modules.master.mapper.BizProductMapper;
import com.aimes.modules.finance.mapper.BizReceivableMapper;
import com.aimes.modules.finance.entity.BizReceivable;
import com.aimes.modules.finance.service.BizFinanceService;
import com.aimes.modules.production.entity.BizWorkOrder;
import com.aimes.modules.production.mapper.BizWorkOrderMapper;
import com.aimes.modules.bom.entity.BizBomVersion;
import com.aimes.modules.bom.entity.BizBomItem;
import com.aimes.modules.bom.mapper.BizBomVersionMapper;
import com.aimes.modules.bom.mapper.BizBomItemMapper;
import com.aimes.modules.outsource.entity.BizOutsourceRequest;
import com.aimes.modules.outsource.entity.BizOutsourceOrder;
import com.aimes.modules.outsource.mapper.BizOutsourceRequestMapper;
import com.aimes.modules.outsource.mapper.BizOutsourceOrderMapper;
import com.aimes.modules.order.entity.BizOrderLog;
import com.aimes.modules.order.mapper.BizOrderLogMapper;
import com.aimes.common.extfield.BizExtFieldConfig;
import com.aimes.common.extfield.BizExtFieldConfigMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aimes.modules.system.entity.SysUser;
import com.aimes.modules.system.mapper.SysUserMapper;
import com.aimes.modules.system.service.SysNotificationService;
import com.aimes.modules.system.service.SysBizConfigService;
import com.aimes.common.event.ApprovalCompletedEvent;
import com.aimes.common.event.ApprovalRejectedEvent;
import com.aimes.modules.approval.entity.BizApprovalInstance;
import com.aimes.modules.approval.service.BizApprovalService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 销售订单服务
 * 订单状态流转: draft(草稿) -> pending_approval(待审批) -> confirmed(已确认) -> in_production(生产中) -> completed(已完成)
 * 任意非终态可取消: cancelled(已取消)
 * 审批驳回: pending_approval -> draft
 */
@lombok.extern.slf4j.Slf4j
@Service
@RequiredArgsConstructor
public class BizSalesOrderService extends ServiceImpl<BizSalesOrderMapper, BizSalesOrder> {

    /** 内部 apply 模式: BizOrderChangeService.applyApproved 调 update() 时短期开启, 跳过 draft/unlock 校验 */
    public static final ThreadLocal<Boolean> INTERNAL_APPLY = ThreadLocal.withInitial(() -> false);

    private final BizSalesOrderItemMapper itemMapper;
    private final BizCustomerMapper customerMapper;
    private final BizMaterialMapper materialMapper;
    private final BizProductMapper productMapper;
    private final BizReceivableMapper receivableMapper;
    private final BizFinanceService financeService;
    private final com.aimes.modules.order.mapper.BizOrderInvoiceMapper orderInvoiceMapper;
    private final SysUserMapper userMapper;
    private final SysNotificationService notificationService;
    private final BizApprovalService approvalService;
    private final ApplicationContext applicationContext;
    private final SysBizConfigService bizConfigService;
    private final BizWorkOrderMapper workOrderMapper;
    private final BizBomVersionMapper bomVersionMapper;
    private final BizBomItemMapper bomItemMapper;
    private final BizOutsourceRequestMapper outsourceRequestMapper;
    private final BizOutsourceOrderMapper outsourceOrderMapper;
    private final BizOrderLogMapper orderLogMapper;
    private final BizExtFieldConfigMapper extFieldConfigMapper;
    private final com.aimes.modules.master.mapper.BizMaterialSpecMapper materialSpecMapper;
    private final com.aimes.modules.master.mapper.BizUnitMapper bizUnitMapper;

    @org.springframework.beans.factory.annotation.Value("${file.upload.path:./uploads}")
    private String fileUploadPath;
    /**
     * 订单状态流转定义
     * key: 当前状态, value: 允许流转到的下一个状态集合
     */
    private static final Map<String, Set<String>> STATUS_FLOW = new LinkedHashMap<>();
    static {
        STATUS_FLOW.put("draft", Set.of("pending_approval", "cancelled"));
        STATUS_FLOW.put("pending_approval", Set.of("confirmed", "draft", "cancelled"));
        STATUS_FLOW.put("confirmed", Set.of("mrp_planned", "in_production", "outsourcing", "cancelled"));
        STATUS_FLOW.put("mrp_planned", Set.of("in_production", "cancelled"));
        STATUS_FLOW.put("outsourcing", Set.of("partial_shipped", "completed", "cancelled"));
        STATUS_FLOW.put("in_production", Set.of("partial_shipped", "completed", "cancelled"));
        STATUS_FLOW.put("partial_shipped", Set.of("shipped", "partial_shipped"));
        STATUS_FLOW.put("shipped", Set.of("settled"));
        STATUS_FLOW.put("completed", Set.of("partial_shipped"));
        STATUS_FLOW.put("settled", Set.of());
        STATUS_FLOW.put("cancelled", Set.of());
    }

    /**
     * 分页查询订单列表
     * 支持按关键字（订单号/联系人）、状态、客户、业务员、日期范围筛选
     *
     * @param keyword    关键字（匹配订单号或联系人姓名）
     * @param status     订单状态
     * @param customerId 客户ID
     * @param salesmanId 业务员ID
     * @param startDate  开始日期
     * @param endDate    结束日期
     * @param pageNum    页码
     * @param pageSize   每页条数
     * @return 分页结果
     */
    public PageResult<Map<String, Object>> getPage(String keyword, String status, Long customerId,
                                                    Long salesmanId, LocalDate startDate, LocalDate endDate,
                                                    int pageNum, int pageSize) {
        return getPageAdvanced(keyword, status, customerId, salesmanId, startDate, endDate,
                null, null, null, null, null, null, null, null, null, pageNum, pageSize);
    }

    public PageResult<Map<String, Object>> getPageAdvanced(
            String keyword, String status, Long customerId, Long salesmanId,
            LocalDate startDate, LocalDate endDate,
            String orderNo, String contactName, Long createBy,
            BigDecimal totalAmountMin, BigDecimal totalAmountMax,
            String deliveryDateRange,
            String productName, String productCode,
            Map<String, String> extFieldFilters,
            int pageNum, int pageSize) {
        return getPageAdvanced(keyword, status, customerId, salesmanId, startDate, endDate,
                orderNo, contactName, createBy, totalAmountMin, totalAmountMax, deliveryDateRange,
                productName, productCode, null, false, extFieldFilters, null, pageNum, pageSize);
    }

    public PageResult<Map<String, Object>> getPageAdvanced(
            String keyword, String status, Long customerId, Long salesmanId,
            LocalDate startDate, LocalDate endDate,
            String orderNo, String contactName, Long createBy,
            BigDecimal totalAmountMin, BigDecimal totalAmountMax,
            String deliveryDateRange,
            String productName, String productCode,
            String customerName,
            Map<String, String> extFieldFilters,
            int pageNum, int pageSize) {
        return getPageAdvanced(keyword, status, customerId, salesmanId, startDate, endDate,
                orderNo, contactName, createBy, totalAmountMin, totalAmountMax, deliveryDateRange,
                productName, productCode, customerName, false, extFieldFilters, null, pageNum, pageSize);
    }

    public PageResult<Map<String, Object>> getPageAdvanced(
            String keyword, String status, Long customerId, Long salesmanId,
            LocalDate startDate, LocalDate endDate,
            String orderNo, String contactName, Long createBy,
            BigDecimal totalAmountMin, BigDecimal totalAmountMax,
            String deliveryDateRange,
            String productName, String productCode,
            String customerName,
            boolean overdueOnly,
            Map<String, String> extFieldFilters,
            Map<String, String> itemExtFieldFilters,
            int pageNum, int pageSize) {

        // 客户名称过滤: 先查匹配的客户ID → 再按 customerId IN 过滤
        Set<Long> customerFilterIds = null;
        if (StringUtils.hasText(customerName) && customerId == null) {
            List<Long> matchedCustomerIds = customerMapper.selectList(
                    new LambdaQueryWrapper<BizCustomer>().like(BizCustomer::getCustomerName, customerName))
                    .stream().map(BizCustomer::getId).collect(Collectors.toList());
            if (matchedCustomerIds.isEmpty()) {
                return new PageResult<>(Collections.emptyList(), 0);
            }
            customerFilterIds = new HashSet<>(matchedCustomerIds);
        }

        // 产品名称/编码过滤: 先查匹配的产品ID → 再查包含这些产品的订单ID
        Set<Long> productFilterOrderIds = null;
        if (StringUtils.hasText(productName) || StringUtils.hasText(productCode)) {
            LambdaQueryWrapper<BizProduct> pWrapper = new LambdaQueryWrapper<BizProduct>()
                    .like(StringUtils.hasText(productName), BizProduct::getProductName, productName)
                    .like(StringUtils.hasText(productCode), BizProduct::getProductNo, productCode);
            List<Long> matchedProductIds = productMapper.selectList(pWrapper).stream()
                    .map(BizProduct::getId).toList();
            if (matchedProductIds.isEmpty()) {
                return new PageResult<>(Collections.emptyList(), 0);
            }
            List<Long> matchedOrderIds = itemMapper.selectList(
                    new LambdaQueryWrapper<BizSalesOrderItem>().in(BizSalesOrderItem::getProductId, matchedProductIds)
                            .select(BizSalesOrderItem::getOrderId)
            ).stream().map(BizSalesOrderItem::getOrderId).distinct().toList();
            if (matchedOrderIds.isEmpty()) {
                return new PageResult<>(Collections.emptyList(), 0);
            }
            productFilterOrderIds = new HashSet<>(matchedOrderIds);
        }

        // status 支持逗号分隔多值,e.g. "completed,partial_shipped" → IN
        List<String> statusList = StringUtils.hasText(status)
                ? Arrays.stream(status.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList()
                : List.of();
        LambdaQueryWrapper<BizSalesOrder> wrapper = new LambdaQueryWrapper<BizSalesOrder>()
                .likeRight(StringUtils.hasText(keyword), BizSalesOrder::getOrderNo, keyword)
                .in(!statusList.isEmpty(), BizSalesOrder::getStatus, statusList)
                .eq(customerId != null, BizSalesOrder::getCustomerId, customerId)
                .eq(salesmanId != null, BizSalesOrder::getSalesmanId, salesmanId)
                .ge(startDate != null, BizSalesOrder::getCreateTime, startDate != null ? startDate.atStartOfDay() : null)
                .le(endDate != null, BizSalesOrder::getCreateTime, endDate != null ? endDate.atTime(LocalTime.MAX) : null)
                .like(StringUtils.hasText(orderNo), BizSalesOrder::getOrderNo, orderNo)
                .like(StringUtils.hasText(contactName), BizSalesOrder::getContactName, contactName)
                .eq(createBy != null, BizSalesOrder::getCreateBy, createBy)
                .ge(totalAmountMin != null, BizSalesOrder::getTotalAmount, totalAmountMin)
                .le(totalAmountMax != null, BizSalesOrder::getTotalAmount, totalAmountMax)
                .orderByDesc(BizSalesOrder::getCreateTime);

        if (productFilterOrderIds != null) {
            wrapper.in(BizSalesOrder::getId, productFilterOrderIds);
        }
        if (customerFilterIds != null) {
            wrapper.in(BizSalesOrder::getCustomerId, customerFilterIds);
        }

        // 交货日期范围
        if (StringUtils.hasText(deliveryDateRange)) {
            String[] parts = deliveryDateRange.split(",");
            if (parts.length == 2) {
                wrapper.ge(BizSalesOrder::getDeliveryDate, LocalDate.parse(parts[0]));
                wrapper.le(BizSalesOrder::getDeliveryDate, LocalDate.parse(parts[1]));
            }
        }

        // 仅查逾期订单:交货日期 < 今天 且 状态不在已结束类
        if (overdueOnly) {
            wrapper.lt(BizSalesOrder::getDeliveryDate, LocalDate.now());
            wrapper.notIn(BizSalesOrder::getStatus,
                    Arrays.asList("settled", "cancelled", "voided", "shipped", "completed"));
        }

        // 扩展字段过滤：JSON_UNQUOTE(JSON_EXTRACT(ext_fields, '$.key')) LIKE '%value%'
        if (extFieldFilters != null && !extFieldFilters.isEmpty()) {
            for (Map.Entry<String, String> entry : extFieldFilters.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                if (StringUtils.hasText(val)) {
                    wrapper.apply("JSON_UNQUOTE(JSON_EXTRACT(ext_fields, '$." + key + "')) LIKE {0}", "%" + val + "%");
                }
            }
        }

        // 行级扩展字段过滤: EXISTS (SELECT 1 FROM biz_sales_order_item WHERE order_id=主表.id AND JSON 字段匹配)
        // fieldKey 仅允许字母数字下划线 (BizExtFieldConfigService.validateFieldKey 已校验), 不存在 SQL 注入
        if (itemExtFieldFilters != null && !itemExtFieldFilters.isEmpty()) {
            for (Map.Entry<String, String> entry : itemExtFieldFilters.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                if (StringUtils.hasText(val) && key.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                    wrapper.apply(
                            "EXISTS (SELECT 1 FROM biz_sales_order_item soi " +
                            "WHERE soi.order_id = biz_sales_order.id AND soi.deleted = 0 " +
                            "AND JSON_UNQUOTE(JSON_EXTRACT(soi.ext_fields, '$." + key + "')) LIKE {0})",
                            "%" + val + "%");
                }
            }
        }

        Page<BizSalesOrder> page = page(new Page<>(pageNum, pageSize), wrapper);

        // 批量加载关联实体名称
        List<BizSalesOrder> records = page.getRecords();
        Set<Long> customerIds = records.stream().map(BizSalesOrder::getCustomerId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> salesmanIds = records.stream().map(BizSalesOrder::getSalesmanId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> createByIds = records.stream().map(BizSalesOrder::getCreateBy).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> allUserIds = new HashSet<>();
        allUserIds.addAll(salesmanIds);
        allUserIds.addAll(createByIds);

        Map<Long, String> customerNameMap = customerIds.isEmpty() ? Map.of() :
                customerMapper.selectBatchIds(customerIds).stream().collect(Collectors.toMap(BizCustomer::getId, BizCustomer::getCustomerName, (a, b) -> a));
        Map<Long, String> userNameMap = allUserIds.isEmpty() ? Map.of() :
                userMapper.selectBatchIds(allUserIds).stream().collect(Collectors.toMap(SysUser::getId, SysUser::getRealName, (a, b) -> a));

        // 批量查应收回款金额
        Set<Long> orderIds = records.stream().map(BizSalesOrder::getId).collect(Collectors.toSet());
        Map<Long, BigDecimal> paymentMap = new HashMap<>();
        if (!orderIds.isEmpty()) {
            try {
                List<BizReceivable> receivables = receivableMapper.selectList(
                        new LambdaQueryWrapper<BizReceivable>().in(BizReceivable::getOrderId, orderIds));
                for (BizReceivable r : receivables) {
                    BigDecimal received = r.getReceivedAmount() != null ? r.getReceivedAmount() : BigDecimal.ZERO;
                    paymentMap.merge(r.getOrderId(), received, BigDecimal::add);
                }
            } catch (Exception e) { /* skip */ }
        }

        // 批量查发票总额（按订单汇总,排除作废）
        Map<Long, BigDecimal> invoiceAmountMap = new HashMap<>();
        if (!orderIds.isEmpty()) {
            try {
                List<com.aimes.modules.order.entity.BizOrderInvoice> invoices = orderInvoiceMapper.selectList(
                        new LambdaQueryWrapper<com.aimes.modules.order.entity.BizOrderInvoice>()
                                .in(com.aimes.modules.order.entity.BizOrderInvoice::getOrderId, orderIds)
                                .ne(com.aimes.modules.order.entity.BizOrderInvoice::getStatus, "cancelled")
                                .eq(com.aimes.modules.order.entity.BizOrderInvoice::getDeleted, 0));
                for (var inv : invoices) {
                    BigDecimal amt = inv.getInvoiceAmount() != null ? inv.getInvoiceAmount() : BigDecimal.ZERO;
                    invoiceAmountMap.merge(inv.getOrderId(), amt, BigDecimal::add);
                }
            } catch (Exception e) { /* skip */ }
        }

        // 批量查所有订单行，用于计算 itemCount、生产进度、交货日期兜底
        Map<Long, List<BizSalesOrderItem>> itemsByOrder = orderIds.isEmpty() ? Map.of() :
                itemMapper.selectList(new LambdaQueryWrapper<BizSalesOrderItem>()
                        .in(BizSalesOrderItem::getOrderId, orderIds))
                        .stream().collect(Collectors.groupingBy(BizSalesOrderItem::getOrderId));

        // 批量查"已审批通过的变更"标记: 至少一条 biz_order_change.status='approved' → 列表上 orderNo 前面打"变"角标
        // 草稿/待审批 不标; 只有审批通过(已实际生效)才标
        Set<Long> orderIdsWithActiveChange = new HashSet<>();
        if (!orderIds.isEmpty()) {
            try {
                var changeMapper = applicationContext.getBean(
                        com.aimes.modules.order.mapper.BizOrderChangeMapper.class);
                List<com.aimes.modules.order.entity.BizOrderChange> changes = changeMapper.selectList(
                        new LambdaQueryWrapper<com.aimes.modules.order.entity.BizOrderChange>()
                                .in(com.aimes.modules.order.entity.BizOrderChange::getOrderId, orderIds)
                                .eq(com.aimes.modules.order.entity.BizOrderChange::getStatus, "approved"));
                for (var c : changes) {
                    if (c.getOrderId() != null) orderIdsWithActiveChange.add(c.getOrderId());
                }
            } catch (Exception ignore) { /* skip */ }
        }

        List<Map<String, Object>> list = records.stream().map(order -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", order.getId());
            map.put("orderNo", order.getOrderNo());
            map.put("customerId", order.getCustomerId());
            map.put("customerName", order.getCustomerId() != null ? customerNameMap.get(order.getCustomerId()) : "");
            map.put("contactName", order.getContactName());
            map.put("settlementType", order.getSettlementType());
            map.put("totalAmount", order.getTotalAmount());
            map.put("paymentAmount", paymentMap.getOrDefault(order.getId(), order.getPaymentAmount()));
            map.put("invoiceAmount", invoiceAmountMap.getOrDefault(order.getId(), BigDecimal.ZERO));
            map.put("deliveryDate", order.getDeliveryDate());
            map.put("originalDeliveryDate", order.getOriginalDeliveryDate());
            map.put("status", order.getStatus());
            // 列表也要返回 changeUnlocked, 让"编辑"按钮在变更解锁后能显示
            map.put("changeUnlocked", order.getChangeUnlocked());
            // 主计划标记 - 列表"转主计划"按钮判断用
            map.put("masterPlanAdded", order.getMasterPlanAdded());
            // hasActiveChange: 该订单是否有 draft/pending 的变更, 列表上 orderNo 前面打"变"角标
            map.put("hasActiveChange", orderIdsWithActiveChange.contains(order.getId()));
            map.put("salesmanId", order.getSalesmanId());
            map.put("salesmanName", order.getSalesmanId() != null ? userNameMap.get(order.getSalesmanId()) : "");
            map.put("createBy", order.getCreateBy());
            map.put("createByName", order.getCreateBy() != null ? userNameMap.get(order.getCreateBy()) : "");
            map.put("remark", order.getRemark());
            map.put("extFields", order.getExtFields());
            map.put("createTime", order.getCreateTime());

            List<BizSalesOrderItem> items = itemsByOrder.getOrDefault(order.getId(), List.of());
            map.put("itemCount", items.size());

            // 生产进度：sum(producedQty) / sum(quantity) * 100，保留整数
            BigDecimal totalQty = items.stream()
                    .map(i -> i.getQuantity() != null ? i.getQuantity() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalProduced = items.stream()
                    .map(i -> i.getProducedQty() != null ? i.getProducedQty() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            int progress = 0;
            if (totalQty.compareTo(BigDecimal.ZERO) > 0) {
                progress = totalProduced.multiply(BigDecimal.valueOf(100))
                        .divide(totalQty, 0, java.math.RoundingMode.HALF_UP).intValue();
                progress = Math.min(progress, 100);
            }
            map.put("productionProgress", progress);

            // 如果没有订单级交货日期，取明细最早交货日期
            if (order.getDeliveryDate() == null) {
                items.stream()
                        .map(BizSalesOrderItem::getDeliveryDate)
                        .filter(Objects::nonNull)
                        .min(Comparator.naturalOrder())
                        .ifPresent(d -> map.put("deliveryDate", d));
            }

            return map;
        }).collect(Collectors.toList());

        return new PageResult<>(list, page.getTotal(), pageNum, pageSize);
    }

    /**
     * 订单统计数据
     * 返回今日订单数、今日金额、待审批数、总订单数
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new LinkedHashMap<>();

        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd = today.atTime(LocalTime.MAX);
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDateTime monthStartTs = monthStart.atStartOfDay();
        LocalDateTime monthEndTs = today.atTime(LocalTime.MAX);

        // 今日新增订单数
        long todayCount = count(new LambdaQueryWrapper<BizSalesOrder>()
                .ge(BizSalesOrder::getCreateTime, todayStart)
                .le(BizSalesOrder::getCreateTime, todayEnd));
        stats.put("todayCount", todayCount);

        // 今日订单总金额
        List<BizSalesOrder> todayOrders = list(new LambdaQueryWrapper<BizSalesOrder>()
                .ge(BizSalesOrder::getCreateTime, todayStart)
                .le(BizSalesOrder::getCreateTime, todayEnd));
        BigDecimal todayAmount = todayOrders.stream()
                .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        stats.put("todayAmount", todayAmount);

        // 待审批订单数
        long pendingApproval = count(new LambdaQueryWrapper<BizSalesOrder>()
                .eq(BizSalesOrder::getStatus, "pending_approval"));
        stats.put("pendingApproval", pendingApproval);

        // 生产中订单数(in_production / mrp_planned / outsourcing 都算)
        long inProduction = count(new LambdaQueryWrapper<BizSalesOrder>()
                .in(BizSalesOrder::getStatus, List.of("in_production", "mrp_planned", "outsourcing")));
        stats.put("inProduction", inProduction);

        // 待发货订单数:生产完成(completed)+ 部分发货(partial_shipped)
        long pendingShip = count(new LambdaQueryWrapper<BizSalesOrder>()
                .in(BizSalesOrder::getStatus, List.of("completed", "partial_shipped")));
        stats.put("pendingShip", pendingShip);

        // 逾期订单数:交货日期 < 今天 且 状态未到 shipped/settled/cancelled/voided
        long overdue = count(new LambdaQueryWrapper<BizSalesOrder>()
                .lt(BizSalesOrder::getDeliveryDate, today)
                .notIn(BizSalesOrder::getStatus, List.of("shipped", "settled", "cancelled", "voided")));
        stats.put("overdue", overdue);

        // 本月订单总金额
        List<BizSalesOrder> monthOrders = list(new LambdaQueryWrapper<BizSalesOrder>()
                .ge(BizSalesOrder::getCreateTime, monthStartTs)
                .le(BizSalesOrder::getCreateTime, monthEndTs));
        BigDecimal monthlyAmount = monthOrders.stream()
                .map(o -> o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        stats.put("monthlyAmount", monthlyAmount);

        // 总订单数
        long totalCount = count();
        stats.put("totalCount", totalCount);

        return stats;
    }

    /**
     * 导出销售订单（ids为空时导出全部，否则导出指定ID）
     */
    public void exportOrders(jakarta.servlet.http.HttpServletResponse response, String ids) throws java.io.IOException {
        LambdaQueryWrapper<BizSalesOrder> wrapper = new LambdaQueryWrapper<BizSalesOrder>()
                .orderByDesc(BizSalesOrder::getCreateTime);
        if (org.springframework.util.StringUtils.hasText(ids)) {
            List<Long> idList = Arrays.stream(ids.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .map(Long::parseLong).collect(Collectors.toList());
            wrapper.in(BizSalesOrder::getId, idList);
        }
        List<BizSalesOrder> orders = list(wrapper);

        // 批量加载: 客户名称 / 明细行 / 物料
        Set<Long> customerIds = orders.stream().map(BizSalesOrder::getCustomerId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> customerNameMap = customerIds.isEmpty() ? Map.of() :
                customerMapper.selectBatchIds(customerIds).stream()
                        .collect(Collectors.toMap(BizCustomer::getId, BizCustomer::getCustomerName, (a, b) -> a));
        Set<Long> orderIds = orders.stream().map(BizSalesOrder::getId).collect(Collectors.toSet());
        List<BizSalesOrderItem> allItems = orderIds.isEmpty() ? List.of() :
                itemMapper.selectList(
                        new LambdaQueryWrapper<BizSalesOrderItem>()
                                .in(BizSalesOrderItem::getOrderId, orderIds)
                                .eq(BizSalesOrderItem::getDeleted, 0));
        Map<Long, List<BizSalesOrderItem>> itemsByOrder = allItems.stream()
                .collect(Collectors.groupingBy(BizSalesOrderItem::getOrderId));
        Set<Long> matIds = allItems.stream().map(BizSalesOrderItem::getProductId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, BizMaterial> matMap = matIds.isEmpty() ? Map.of() :
                materialMapper.selectBatchIds(matIds).stream()
                        .collect(Collectors.toMap(BizMaterial::getId, m -> m, (a, b) -> a));

        // 订单级 + 明细级扩展字段配置
        List<BizExtFieldConfig> orderExtConfigs = extFieldConfigMapper.selectList(
                new LambdaQueryWrapper<BizExtFieldConfig>()
                        .eq(BizExtFieldConfig::getModule, "sales_order")
                        .eq(BizExtFieldConfig::getEnabled, 1)
                        .orderByAsc(BizExtFieldConfig::getSortOrder));
        List<BizExtFieldConfig> itemExtConfigs = extFieldConfigMapper.selectList(
                new LambdaQueryWrapper<BizExtFieldConfig>()
                        .eq(BizExtFieldConfig::getModule, "sales_order_item")
                        .eq(BizExtFieldConfig::getEnabled, 1)
                        .orderByAsc(BizExtFieldConfig::getSortOrder));
        ObjectMapper jsonMapper = new ObjectMapper();

        Map<String, String> statusLabelMap = new LinkedHashMap<>();
        statusLabelMap.put("draft", "草稿"); statusLabelMap.put("pending_approval", "待审批");
        statusLabelMap.put("confirmed", "已确认"); statusLabelMap.put("mrp_planned", "已计划");
        statusLabelMap.put("in_production", "生产中"); statusLabelMap.put("partial_shipped", "部分发货");
        statusLabelMap.put("shipped", "已发货"); statusLabelMap.put("completed", "已完成");
        statusLabelMap.put("settled", "已结算"); statusLabelMap.put("voided", "已作废");
        statusLabelMap.put("cancelled", "已取消");

        // 表头: 订单字段 + 明细字段 + 订单级扩展 + 明细级扩展
        List<String> headers = new ArrayList<>(List.of(
                "订单号", "客户名称", "联系人", "收货地址", "结算方式",
                "订单金额", "已回款", "交货日期", "状态", "备注",
                "产品编号", "产品名称", "规格型号", "单位", "数量", "单价", "税率%", "金额", "明细备注"));
        for (BizExtFieldConfig fc : orderExtConfigs) headers.add(fc.getFieldLabel());
        for (BizExtFieldConfig fc : itemExtConfigs) headers.add("[明细]" + fc.getFieldLabel());

        // 每条明细一行(订单字段重复, 没明细的订单也输一行空明细以保留订单信息)
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BizSalesOrder o : orders) {
            Map<String, Object> orderExtVals = parseJsonAsMap(o.getExtFields(), jsonMapper);
            List<BizSalesOrderItem> items = itemsByOrder.getOrDefault(o.getId(), List.of());
            if (items.isEmpty()) {
                rows.add(buildExportRow(o, null, null, customerNameMap, statusLabelMap,
                        orderExtConfigs, orderExtVals, itemExtConfigs, null));
            } else {
                for (BizSalesOrderItem it : items) {
                    BizMaterial mat = it.getProductId() != null ? matMap.get(it.getProductId()) : null;
                    Map<String, Object> itemExtVals = parseJsonAsMap(it.getExtFields(), jsonMapper);
                    rows.add(buildExportRow(o, it, mat, customerNameMap, statusLabelMap,
                            orderExtConfigs, orderExtVals, itemExtConfigs, itemExtVals));
                }
            }
        }

        List<java.util.function.Function<Map<String, Object>, Object>> extractors = headers.stream()
                .map(h -> (java.util.function.Function<Map<String, Object>, Object>) (row -> row.get(h)))
                .collect(Collectors.toList());
        com.aimes.common.utils.ExcelUtil.export(response, "销售订单", headers, rows, extractors);
    }

    private Map<String, Object> parseJsonAsMap(String json, ObjectMapper jsonMapper) {
        if (json == null || json.isEmpty()) return Map.of();
        try { return jsonMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}); }
        catch (Exception e) { return Map.of(); }
    }

    private Map<String, Object> buildExportRow(BizSalesOrder o, BizSalesOrderItem it, BizMaterial mat,
                                                Map<Long, String> customerNameMap,
                                                Map<String, String> statusLabelMap,
                                                List<BizExtFieldConfig> orderExtConfigs,
                                                Map<String, Object> orderExtVals,
                                                List<BizExtFieldConfig> itemExtConfigs,
                                                Map<String, Object> itemExtVals) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("订单号", o.getOrderNo());
        row.put("客户名称", o.getCustomerId() != null ? customerNameMap.get(o.getCustomerId()) : "");
        row.put("联系人", o.getContactName());
        row.put("收货地址", o.getShippingAddress());
        row.put("结算方式", o.getSettlementType());
        row.put("订单金额", o.getTotalAmount());
        row.put("已回款", o.getPaymentAmount());
        row.put("交货日期", o.getDeliveryDate());
        row.put("状态", statusLabelMap.getOrDefault(o.getStatus(), o.getStatus()));
        row.put("备注", o.getRemark());
        row.put("产品编号", mat != null ? mat.getMaterialNo() : "");
        row.put("产品名称", mat != null ? mat.getMaterialName() : "");
        row.put("规格型号", mat != null ? mat.getSpec() : "");
        row.put("单位", mat != null ? mat.getUnit() : "");
        row.put("数量", it != null ? it.getQuantity() : "");
        row.put("单价", it != null ? it.getUnitPrice() : "");
        row.put("税率%", it != null ? it.getTaxRate() : "");
        row.put("金额", it != null ? it.getAmount() : "");
        row.put("明细备注", it != null ? it.getRemark() : "");
        // 订单级扩展字段
        for (BizExtFieldConfig fc : orderExtConfigs) {
            Object v = orderExtVals != null ? orderExtVals.get(fc.getFieldKey()) : null;
            row.put(fc.getFieldLabel(), v);
        }
        // 明细级扩展字段
        for (BizExtFieldConfig fc : itemExtConfigs) {
            Object v = itemExtVals != null ? itemExtVals.get(fc.getFieldKey()) : null;
            row.put("[明细]" + fc.getFieldLabel(), v);
        }
        return row;
    }

    /**
     * 通过订单编号查询详情（供关联单据跳转使用）
     */
    public Map<String, Object> getDetailByOrderNo(String orderNo) {
        BizSalesOrder order = getOne(new LambdaQueryWrapper<BizSalesOrder>()
                .eq(BizSalesOrder::getOrderNo, orderNo).last("LIMIT 1"));
        if (order == null) throw new BusinessException("销售订单不存在: " + orderNo);
        return getDetail(order.getId());
    }

    /**
     * 订单详情（含明细items）
     *
     * @param id 订单ID
     * @return 订单详情Map，包含items明细列表
     */
    public Map<String, Object> getDetail(Long id) {
        BizSalesOrder order = getById(id);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", order.getId());
        detail.put("orderNo", order.getOrderNo());
        detail.put("customerId", order.getCustomerId());
        // 关联客户名称
        if (order.getCustomerId() != null) {
            BizCustomer customer = customerMapper.selectById(order.getCustomerId());
            detail.put("customerName", customer != null ? customer.getCustomerName() : "");
        }
        detail.put("contactName", order.getContactName());
        detail.put("shippingAddress", order.getShippingAddress());
        detail.put("settlementType", order.getSettlementType());
        detail.put("totalAmount", order.getTotalAmount());
        detail.put("deliveryDate", order.getDeliveryDate());
        detail.put("originalDeliveryDate", order.getOriginalDeliveryDate());
        detail.put("paymentAmount", order.getPaymentAmount());
        detail.put("status", order.getStatus());
        detail.put("salesmanId", order.getSalesmanId());
        // 关联业务员名称
        if (order.getSalesmanId() != null) {
            SysUser salesman = userMapper.selectById(order.getSalesmanId());
            detail.put("salesmanName", salesman != null ? salesman.getRealName() : "");
        }
        detail.put("approvedBy", order.getApprovedBy());
        detail.put("approvedAt", order.getApprovedAt());
        detail.put("approveRemark", order.getApproveRemark());
        // 变更解锁标志: 1 = 变更审批通过, 前端"编辑订单"按钮据此显示
        detail.put("changeUnlocked", order.getChangeUnlocked());
        detail.put("outsourceType", order.getOutsourceType());
        detail.put("masterPlanAdded", order.getMasterPlanAdded());
        detail.put("masterPlanAddedAt", order.getMasterPlanAddedAt());
        detail.put("cancelReason", order.getCancelReason());
        detail.put("attachmentUrls", order.getAttachmentUrls());
        detail.put("remark", order.getRemark());
        // 扩展字段(JSON 字符串) — 不带回前端编辑时表单中的扩展字段会全部显示为空
        detail.put("extFields", order.getExtFields());
        detail.put("createBy", order.getCreateBy());
        detail.put("createTime", order.getCreateTime());

        // 查询订单明细列表，按ID升序排列
        List<BizSalesOrderItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<BizSalesOrderItem>()
                        .eq(BizSalesOrderItem::getOrderId, id)
                        .orderByAsc(BizSalesOrderItem::getId));

        // 批量查询物料信息，丰富明细展示字段
        if (items != null && !items.isEmpty()) {
            Set<Long> materialIds = items.stream()
                    .map(BizSalesOrderItem::getProductId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Map<Long, BizMaterial> materialMap = new HashMap<>();
            if (!materialIds.isEmpty()) {
                materialMapper.selectBatchIds(materialIds)
                        .forEach(m -> materialMap.put(m.getId(), m));
            }
            // 若物料表中找不到，再查产品表（兼容旧数据）
            Map<Long, BizProduct> productMap = new HashMap<>();
            Set<Long> missingIds = materialIds.stream()
                    .filter(mid -> !materialMap.containsKey(mid))
                    .collect(Collectors.toSet());
            if (!missingIds.isEmpty()) {
                productMapper.selectBatchIds(missingIds)
                        .forEach(p -> productMap.put(p.getId(), p));
            }

            List<Map<String, Object>> itemDetails = items.stream().map(item -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", item.getId());
                m.put("orderId", item.getOrderId());
                m.put("productId", item.getProductId());
                BizMaterial mat = item.getProductId() != null ? materialMap.get(item.getProductId()) : null;
                if (mat != null) {
                    m.put("productName", mat.getMaterialName());
                    m.put("productCode", mat.getMaterialNo());
                    m.put("spec", mat.getSpec());
                    m.put("unit", mat.getUnit());
                    m.put("unitConfig", mat.getUnitConfig());
                } else {
                    BizProduct prod = item.getProductId() != null ? productMap.get(item.getProductId()) : null;
                    m.put("productName", prod != null ? prod.getProductName() : "");
                    m.put("productCode", prod != null ? prod.getProductNo() : "");
                    m.put("spec", prod != null ? prod.getSpec() : "");
                    m.put("unit", prod != null ? prod.getUnit() : "");
                    m.put("unitConfig", prod != null ? prod.getUnitConfig() : null);
                }
                m.put("quantity", item.getQuantity());
                m.put("unitPrice", item.getUnitPrice());
                m.put("amount", item.getAmount());
                m.put("taxRate", item.getTaxRate());
                m.put("deliveryDate", item.getDeliveryDate());
                m.put("producedQty", item.getProducedQty());
                m.put("shippedQty", item.getShippedQty());
                m.put("warehouseInQty", item.getWarehouseInQty());
                m.put("taskQty", item.getTaskQty());
                m.put("remark", item.getRemark());
                m.put("extFields", item.getExtFields());
                // 未分配数量 = 订单数 - SUM(plan_order_rel.allocated_qty WHERE plan_item 未取消)
                // 给前端控制"转需求/转计划/转外协"按钮可见性: > 0 时按钮可点
                m.put("unallocatedQty", computeUnallocatedQty(item));
                return m;
            }).collect(Collectors.toList());
            detail.put("items", itemDetails);
        } else {
            detail.put("items", Collections.emptyList());
        }

        return detail;
    }

    /**
     * 创建订单
     * 自动生成订单编号(SO前缀)，初始状态为draft(草稿)
     * 自动计算每条明细金额和订单总金额
     *
     * @param order 订单主信息
     * @param items 订单明细列表
     */
    @Transactional
    public void create(BizSalesOrder order, List<BizSalesOrderItem> items) {
        // 根据配置决定是否自动生成订单号
        boolean autoNo = bizConfigService.getBoolValue("sales.order.auto_no", true);
        if (org.springframework.util.StringUtils.hasText(order.getOrderNo())) {
            long count = count(new LambdaQueryWrapper<BizSalesOrder>()
                    .eq(BizSalesOrder::getOrderNo, order.getOrderNo().trim()));
            if (count > 0) throw new com.aimes.common.exception.BusinessException("销售订单号[" + order.getOrderNo() + "]已存在");
            order.setOrderNo(order.getOrderNo().trim());
        } else if (autoNo) {
            order.setOrderNo(CodeUtil.generate("SO"));
        } else {
            throw new com.aimes.common.exception.BusinessException("系统配置要求手动填写销售订单号，请输入订单号");
        }
        order.setStatus("draft");
        // 记录初始交货日期(用于"延期"角标判定 - 后续工单等延后不更新此字段)
        if (order.getOriginalDeliveryDate() == null) {
            order.setOriginalDeliveryDate(order.getDeliveryDate());
        }

        // 计算各明细金额及订单总金额: 单价为含税单价, amount = qty * unitPrice 即含税总价
        BigDecimal totalAmount = BigDecimal.ZERO;
        if (items != null) {
            for (BizSalesOrderItem item : items) {
                BigDecimal amount = computeItemTaxIncludedAmount(item.getQuantity(), item.getUnitPrice(), item.getTaxRate());
                item.setAmount(amount);
                item.setProducedQty(BigDecimal.ZERO);
                item.setShippedQty(BigDecimal.ZERO);
                totalAmount = totalAmount.add(amount);
            }
        }
        order.setTotalAmount(totalAmount);
        save(order);

        // 保存订单明细，关联订单ID
        if (items != null) {
            for (BizSalesOrderItem item : items) {
                item.setOrderId(order.getId());
                itemMapper.insert(item);
            }
        }
        addLog(order.getId(), "create", "创建订单");
    }

    /**
     * 更新订单
     * 仅草稿状态的订单可以编辑，重新计算明细金额和总金额
     * 采用先删后插的方式更新明细列表
     *
     * @param id    订单ID
     * @param order 订单主信息
     * @param items 新的订单明细列表
     */
    @Transactional
    public void update(Long id, BizSalesOrder order, List<BizSalesOrderItem> items) {
        BizSalesOrder existing = getById(id);
        if (existing == null) {
            throw new BusinessException("订单不存在");
        }
        boolean isDraft = "draft".equals(existing.getStatus());
        boolean isChangeUnlocked = Integer.valueOf(1).equals(existing.getChangeUnlocked());
        // 新流程: 由 BizOrderChangeService.applyApproved 调用本方法时, 订单本身可能不在 draft 也没解锁;
        // 通过线程级临时开关 INTERNAL_APPLY 放行 (set-then-call-then-clear 模式确保短期一次性放行)
        if (!isDraft && !isChangeUnlocked && !INTERNAL_APPLY.get()) {
            throw new BusinessException("只有草稿或已解锁变更的订单可以编辑");
        }

        order.setId(id);
        // 草稿可以改订单编号; 非草稿(变更解锁)阶段不允许改, 保留原值
        if (isDraft && order.getOrderNo() != null && !order.getOrderNo().isBlank()
                && !order.getOrderNo().equals(existing.getOrderNo())) {
            String newNo = order.getOrderNo().trim();
            // 唯一性校验
            long dup = count(new LambdaQueryWrapper<BizSalesOrder>()
                    .eq(BizSalesOrder::getOrderNo, newNo)
                    .ne(BizSalesOrder::getId, id));
            if (dup > 0) throw new BusinessException("订单编号已存在: " + newNo);
            order.setOrderNo(newNo);
        } else {
            order.setOrderNo(existing.getOrderNo());
        }
        // 编辑后重置变更解锁标志
        order.setChangeUnlocked(0);

        // 重新计算明细金额和订单总金额: 单价为含税单价, amount = qty * unitPrice 即含税总价
        BigDecimal totalAmount = BigDecimal.ZERO;
        if (items != null) {
            for (BizSalesOrderItem item : items) {
                BigDecimal amount = computeItemTaxIncludedAmount(item.getQuantity(), item.getUnitPrice(), item.getTaxRate());
                item.setAmount(amount);
                totalAmount = totalAmount.add(amount);
            }
        }
        order.setTotalAmount(totalAmount);
        updateById(order);

        // === 级联策略 ===
        // 1) 先 diff 旧 vs 新, 在改动数据前做必要的级联(取消/缩减计划/工单/外协);
        // 2) 再走"保留 id 的 upsert": 旧有新无→delete + 级联取消; 旧无新有→新增; 旧新都有→updateById,
        //    再按 productId / quantity 变化触发对应级联
        List<BizSalesOrderItem> oldItems = itemMapper.selectList(
                new LambdaQueryWrapper<BizSalesOrderItem>().eq(BizSalesOrderItem::getOrderId, id));
        Map<Long, BizSalesOrderItem> oldById = oldItems.stream()
                .collect(Collectors.toMap(BizSalesOrderItem::getId, x -> x, (a, b) -> a));
        Set<Long> newIds = new HashSet<>();
        if (items != null) {
            items.stream().map(BizSalesOrderItem::getId).filter(Objects::nonNull).forEach(newIds::add);
        }

        // 收集订单变更对生产的影响 (有级联时通知生产 / 计划员)
        java.util.List<String> productionImpacts = new java.util.ArrayList<>();

        // 1a. 旧有新无 → 整行删除, 级联取消所有关联
        for (BizSalesOrderItem oldItem : oldItems) {
            if (!newIds.contains(oldItem.getId())) {
                cancelOrderItemCascade(oldItem.getId(), "订单明细被删除");
                productionImpacts.add("删除明细 (产品ID " + oldItem.getProductId() + ", 数量 " + oldItem.getQuantity() + ")");
                itemMapper.deleteById(oldItem.getId());
            }
        }

        // 1b. 旧新都在 / 全新增, 处理变更
        if (items != null) {
            for (BizSalesOrderItem newItem : items) {
                newItem.setOrderId(id);
                if (newItem.getId() == null || !oldById.containsKey(newItem.getId())) {
                    // 全新行: 直接 insert, 没有历史关联
                    newItem.setId(null);
                    itemMapper.insert(newItem);
                    continue;
                }
                BizSalesOrderItem oldItem = oldById.get(newItem.getId());
                boolean productChanged = !Objects.equals(newItem.getProductId(), oldItem.getProductId());
                BigDecimal oldQty = oldItem.getQuantity() != null ? oldItem.getQuantity() : BigDecimal.ZERO;
                BigDecimal newQty = newItem.getQuantity() != null ? newItem.getQuantity() : BigDecimal.ZERO;

                if (productChanged) {
                    // 产品变更: 旧产品所有关联工单/计划/外协一律作废
                    cancelOrderItemCascade(oldItem.getId(), "产品变更, 旧产品计划/工单/外协全部作废");
                    productionImpacts.add("产品变更 (产品ID " + oldItem.getProductId() + " → " + newItem.getProductId() + ")");
                } else if (newQty.compareTo(oldQty) < 0) {
                    BigDecimal produced = oldItem.getProducedQty() != null ? oldItem.getProducedQty() : BigDecimal.ZERO;
                    if (newQty.compareTo(produced) < 0) {
                        throw new BusinessException("已生产 " + produced.toPlainString()
                                + " 件, 新数量不能少于已生产数量");
                    }
                    BigDecimal diff = oldQty.subtract(newQty);
                    reduceOrderItemCascade(oldItem.getId(), diff);
                    productionImpacts.add("数量减少 (产品ID " + oldItem.getProductId() + ", " + oldQty + " → " + newQty + ")");
                } else if (newQty.compareTo(oldQty) > 0) {
                    productionImpacts.add("数量增加 (产品ID " + oldItem.getProductId() + ", " + oldQty + " → " + newQty + ")");
                }
                // 数量增加: 没级联, 差额由"转需求/转计划/转外协"按钮重新走; 但仍通知, 计划员需要安排

                // 保留 id 的 update; produced_qty / shipped_qty 等汇总字段不允许前端覆盖
                // 产品变更: 旧产品的产量/发货量/入库量/任务量都已作废, 新产品要从 0 重新累计
                if (productChanged) {
                    newItem.setProducedQty(BigDecimal.ZERO);
                    newItem.setShippedQty(BigDecimal.ZERO);
                    newItem.setWarehouseInQty(BigDecimal.ZERO);
                    newItem.setTaskQty(BigDecimal.ZERO);
                } else {
                    newItem.setProducedQty(oldItem.getProducedQty());
                    newItem.setShippedQty(oldItem.getShippedQty());
                    newItem.setWarehouseInQty(oldItem.getWarehouseInQty());
                    newItem.setTaskQty(oldItem.getTaskQty());
                }
                itemMapper.updateById(newItem);
            }
        }

        // 状态回退: 终态(completed/shipped) 时如果还有 item 没全产/没全发, 退到 confirmed 让用户重走流程
        // 触发场景: 产品变更后, 旧产品的产/发清零, completed 显然不再适用
        revertOrderStatusIfNeeded(id);

        // 订单变更通知 (有级联或量增时才发, 草稿/纯价格修改不打扰)
        // 走配置化场景 order_changed, 角色和开关在 [系统设置→通知配置] 配置
        if (!productionImpacts.isEmpty()) {
            try {
                String content = "销售订单[" + existing.getOrderNo() + "]已变更: "
                        + String.join("; ", productionImpacts)
                        + "。关联的生产计划/工单/外协已自动调整, 请到主计划或订单详情页查看待派发数量。";
                notificationService.notifyByScenario("order_changed", "销售订单变更", content,
                        "warn", "sales_order", id);
            } catch (Exception ignored) {}
        }

        addLog(id, "update", "编辑订单");
    }

    /** 终态订单的 item 不再"全部完成" → 回退到 confirmed (用户可再走 转需求/计划/外协 + 重新报工 + 发货) */
    private void revertOrderStatusIfNeeded(Long orderId) {
        BizSalesOrder ord = getById(orderId);
        if (ord == null) return;
        Set<String> terminal = Set.of("completed", "shipped");
        if (!terminal.contains(ord.getStatus())) return;
        List<BizSalesOrderItem> finalItems = itemMapper.selectList(
                new LambdaQueryWrapper<BizSalesOrderItem>().eq(BizSalesOrderItem::getOrderId, orderId));
        if (finalItems == null || finalItems.isEmpty()) return;
        boolean anyShortProduced = finalItems.stream().anyMatch(i -> {
            BigDecimal q = i.getQuantity() != null ? i.getQuantity() : BigDecimal.ZERO;
            BigDecimal p = i.getProducedQty() != null ? i.getProducedQty() : BigDecimal.ZERO;
            return p.compareTo(q) < 0;
        });
        boolean anyShortShipped = finalItems.stream().anyMatch(i -> {
            BigDecimal q = i.getQuantity() != null ? i.getQuantity() : BigDecimal.ZERO;
            BigDecimal s = i.getShippedQty() != null ? i.getShippedQty() : BigDecimal.ZERO;
            return s.compareTo(q) < 0;
        });
        if (anyShortProduced || anyShortShipped) {
            ord.setStatus("confirmed");
            updateById(ord);
            addLog(orderId, "revert_status", "订单变更后产/发未完成, 状态从终态回退到 confirmed");
        }
    }

    /**
     * 计算 order_item 的未分配数量:
     *   unallocated = item.quantity
     *               - SUM(plan_order_rel.allocated_qty WHERE plan_item.status != 'cancelled')
     *               - SUM(outsource_order.total_qty WHERE status != 'cancelled')
     * > 0 表示还有量没转计划/外协, 前端"转需求/转计划/转外协"按钮可点
     */
    private BigDecimal computeUnallocatedQty(BizSalesOrderItem item) {
        if (item == null || item.getQuantity() == null) return BigDecimal.ZERO;
        BigDecimal qty = item.getQuantity();
        try {
            var planItemMapperBean = applicationContext.getBean(
                    com.aimes.modules.mrp.mapper.BizProductionPlanItemMapper.class);
            var planRelMapperBean = applicationContext.getBean(
                    com.aimes.modules.mrp.mapper.BizProductionPlanOrderRelMapper.class);
            // 在用的 plan_item ids
            List<com.aimes.modules.mrp.entity.BizProductionPlanOrderRel> rels = planRelMapperBean.selectList(
                    new LambdaQueryWrapper<com.aimes.modules.mrp.entity.BizProductionPlanOrderRel>()
                            .eq(com.aimes.modules.mrp.entity.BizProductionPlanOrderRel::getOrderItemId, item.getId())
                            .eq(com.aimes.modules.mrp.entity.BizProductionPlanOrderRel::getDeleted, 0));
            BigDecimal allocated = BigDecimal.ZERO;
            for (com.aimes.modules.mrp.entity.BizProductionPlanOrderRel rel : rels) {
                if (rel.getPlanItemId() == null) continue;
                com.aimes.modules.mrp.entity.BizProductionPlanItem pi = planItemMapperBean.selectById(rel.getPlanItemId());
                if (pi == null || "cancelled".equals(pi.getStatus())) continue;
                BigDecimal a = rel.getAllocatedQty() != null ? rel.getAllocatedQty() : BigDecimal.ZERO;
                allocated = allocated.add(a);
            }
            // 整单外协占用
            BigDecimal outsourced = outsourceOrderMapper.selectList(
                    new LambdaQueryWrapper<BizOutsourceOrder>()
                            .eq(BizOutsourceOrder::getOrderItemId, item.getId())
                            .eq(BizOutsourceOrder::getDeleted, 0)
                            .ne(BizOutsourceOrder::getStatus, "cancelled"))
                    .stream().map(o -> o.getTotalQty() != null ? o.getTotalQty() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return qty.subtract(allocated).subtract(outsourced).max(BigDecimal.ZERO);
        } catch (Exception e) {
            return qty;
        }
    }

    /**
     * 取消该 order_item 关联的所有计划/工单/外协(产品变更或行被删除场景).
     * 已 completed 的工单也强制 cancel(用户明确要求"旧产品的生产工单应该作废").
     */
    private void cancelOrderItemCascade(Long orderItemId, String reason) {
        if (orderItemId == null) return;
        var planItemMapperBean = applicationContext.getBean(
                com.aimes.modules.mrp.mapper.BizProductionPlanItemMapper.class);
        var planRelMapperBean = applicationContext.getBean(
                com.aimes.modules.mrp.mapper.BizProductionPlanOrderRelMapper.class);
        var taskMapperBean = applicationContext.getBean(
                com.aimes.modules.production.mapper.BizWoProcessTaskMapper.class);

        // 1. 工单 + 工序任务 → cancelled
        List<BizWorkOrder> wos = workOrderMapper.selectList(
                new LambdaQueryWrapper<BizWorkOrder>()
                        .eq(BizWorkOrder::getOrderItemId, orderItemId)
                        .eq(BizWorkOrder::getDeleted, 0)
                        .ne(BizWorkOrder::getStatus, "cancelled"));
        for (BizWorkOrder wo : wos) {
            wo.setStatus("cancelled");
            wo.setRemark(StringUtils.hasText(wo.getRemark()) ? wo.getRemark() + " | " + reason : reason);
            workOrderMapper.updateById(wo);
            taskMapperBean.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<com.aimes.modules.production.entity.BizWoProcessTask>()
                            .eq(com.aimes.modules.production.entity.BizWoProcessTask::getWorkOrderId, wo.getId())
                            .ne(com.aimes.modules.production.entity.BizWoProcessTask::getStatus, "cancelled")
                            .set(com.aimes.modules.production.entity.BizWoProcessTask::getStatus, "cancelled"));
        }

        // 2. 计划行项 → cancelled (通过 plan_order_rel 关联)
        List<com.aimes.modules.mrp.entity.BizProductionPlanOrderRel> rels = planRelMapperBean.selectList(
                new LambdaQueryWrapper<com.aimes.modules.mrp.entity.BizProductionPlanOrderRel>()
                        .eq(com.aimes.modules.mrp.entity.BizProductionPlanOrderRel::getOrderItemId, orderItemId)
                        .eq(com.aimes.modules.mrp.entity.BizProductionPlanOrderRel::getDeleted, 0));
        Set<Long> piIds = rels.stream()
                .map(com.aimes.modules.mrp.entity.BizProductionPlanOrderRel::getPlanItemId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        for (Long piId : piIds) {
            com.aimes.modules.mrp.entity.BizProductionPlanItem pi = planItemMapperBean.selectById(piId);
            if (pi == null || "cancelled".equals(pi.getStatus())) continue;
            pi.setStatus("cancelled");
            planItemMapperBean.updateById(pi);
        }
        // 关联记录软删, 释放该 order_item 的"已分配"占位
        for (com.aimes.modules.mrp.entity.BizProductionPlanOrderRel rel : rels) {
            rel.setDeleted(1);
            planRelMapperBean.updateById(rel);
        }

        // 3. 外协订单 → cancelled (整单外协场景, order_item_id 直接挂在 outsource_order 上)
        List<BizOutsourceOrder> osos = outsourceOrderMapper.selectList(
                new LambdaQueryWrapper<BizOutsourceOrder>()
                        .eq(BizOutsourceOrder::getOrderItemId, orderItemId)
                        .eq(BizOutsourceOrder::getDeleted, 0)
                        .notIn(BizOutsourceOrder::getStatus, List.of("cancelled", "completed")));
        for (BizOutsourceOrder oso : osos) {
            oso.setStatus("cancelled");
            oso.setApproveRemark(reason);
            outsourceOrderMapper.updateById(oso);
        }
    }

    /**
     * 数量减少时, 反向缩减该 order_item 的工单/计划:
     *   1) 优先取消 pending 工单(整张作废, 释放 plan_qty)
     *   2) 不够再砍 in_progress 工单的"未完成槽位"(plan_qty 收到 completed_qty, 同步 process_task)
     *   3) 已 completed 的工单不动(已完成的就保留)
     *   4) plan_item.plan_qty / plan_order_rel.allocated_qty 同步收缩
     */
    private void reduceOrderItemCascade(Long orderItemId, BigDecimal diff) {
        if (orderItemId == null || diff == null || diff.compareTo(BigDecimal.ZERO) <= 0) return;
        var planItemMapperBean = applicationContext.getBean(
                com.aimes.modules.mrp.mapper.BizProductionPlanItemMapper.class);
        var planRelMapperBean = applicationContext.getBean(
                com.aimes.modules.mrp.mapper.BizProductionPlanOrderRelMapper.class);
        var taskMapperBean = applicationContext.getBean(
                com.aimes.modules.production.mapper.BizWoProcessTaskMapper.class);

        BigDecimal remaining = diff;

        // 1. pending 工单整张作废
        List<BizWorkOrder> pendingWos = workOrderMapper.selectList(
                new LambdaQueryWrapper<BizWorkOrder>()
                        .eq(BizWorkOrder::getOrderItemId, orderItemId)
                        .eq(BizWorkOrder::getDeleted, 0)
                        .eq(BizWorkOrder::getStatus, "pending")
                        .orderByDesc(BizWorkOrder::getPlanQty));
        for (BizWorkOrder wo : pendingWos) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal pq = wo.getPlanQty() != null ? wo.getPlanQty() : BigDecimal.ZERO;
            if (pq.compareTo(remaining) <= 0) {
                wo.setStatus("cancelled");
                wo.setRemark("订单数量减少, 工单作废");
                workOrderMapper.updateById(wo);
                taskMapperBean.update(null,
                        new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<com.aimes.modules.production.entity.BizWoProcessTask>()
                                .eq(com.aimes.modules.production.entity.BizWoProcessTask::getWorkOrderId, wo.getId())
                                .set(com.aimes.modules.production.entity.BizWoProcessTask::getStatus, "cancelled"));
                remaining = remaining.subtract(pq);
            } else {
                BigDecimal newPq = pq.subtract(remaining);
                wo.setPlanQty(newPq);
                workOrderMapper.updateById(wo);
                taskMapperBean.update(null,
                        new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<com.aimes.modules.production.entity.BizWoProcessTask>()
                                .eq(com.aimes.modules.production.entity.BizWoProcessTask::getWorkOrderId, wo.getId())
                                .ne(com.aimes.modules.production.entity.BizWoProcessTask::getStatus, "completed")
                                .set(com.aimes.modules.production.entity.BizWoProcessTask::getPlanQty, newPq));
                remaining = BigDecimal.ZERO;
            }
        }

        // 2. in_progress 工单只能砍"未完成槽位"
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            List<BizWorkOrder> inProgressWos = workOrderMapper.selectList(
                    new LambdaQueryWrapper<BizWorkOrder>()
                            .eq(BizWorkOrder::getOrderItemId, orderItemId)
                            .eq(BizWorkOrder::getDeleted, 0)
                            .eq(BizWorkOrder::getStatus, "in_progress"));
            for (BizWorkOrder wo : inProgressWos) {
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
                BigDecimal pq = wo.getPlanQty() != null ? wo.getPlanQty() : BigDecimal.ZERO;
                BigDecimal cq = wo.getCompletedQty() != null ? wo.getCompletedQty() : BigDecimal.ZERO;
                BigDecimal slot = pq.subtract(cq).max(BigDecimal.ZERO);
                if (slot.compareTo(BigDecimal.ZERO) <= 0) continue;
                BigDecimal cut = slot.min(remaining);
                BigDecimal newPq = pq.subtract(cut);
                wo.setPlanQty(newPq);
                if (newPq.compareTo(cq) <= 0 && cq.compareTo(BigDecimal.ZERO) > 0) {
                    wo.setStatus("completed");
                }
                workOrderMapper.updateById(wo);
                // 同步未完成的工序任务 plan_qty
                taskMapperBean.update(null,
                        new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<com.aimes.modules.production.entity.BizWoProcessTask>()
                                .eq(com.aimes.modules.production.entity.BizWoProcessTask::getWorkOrderId, wo.getId())
                                .ne(com.aimes.modules.production.entity.BizWoProcessTask::getStatus, "completed")
                                .set(com.aimes.modules.production.entity.BizWoProcessTask::getPlanQty, newPq));
                remaining = remaining.subtract(cut);
            }
        }

        // 3. 重算 plan_item.plan_qty / plan_order_rel.allocated_qty 与剩余工单数量对齐
        Set<Long> piIds = planRelMapperBean.selectList(
                new LambdaQueryWrapper<com.aimes.modules.mrp.entity.BizProductionPlanOrderRel>()
                        .eq(com.aimes.modules.mrp.entity.BizProductionPlanOrderRel::getOrderItemId, orderItemId)
                        .eq(com.aimes.modules.mrp.entity.BizProductionPlanOrderRel::getDeleted, 0))
                .stream().map(com.aimes.modules.mrp.entity.BizProductionPlanOrderRel::getPlanItemId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        for (Long piId : piIds) {
            BigDecimal newPlanQty = workOrderMapper.selectList(
                    new LambdaQueryWrapper<BizWorkOrder>()
                            .eq(BizWorkOrder::getPlanItemId, piId)
                            .eq(BizWorkOrder::getDeleted, 0)
                            .ne(BizWorkOrder::getStatus, "cancelled"))
                    .stream().map(w -> w.getPlanQty() != null ? w.getPlanQty() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            com.aimes.modules.mrp.entity.BizProductionPlanItem pi = planItemMapperBean.selectById(piId);
            if (pi == null) continue;
            pi.setPlanQty(newPlanQty);
            if (newPlanQty.compareTo(BigDecimal.ZERO) <= 0) pi.setStatus("cancelled");
            planItemMapperBean.updateById(pi);
            // rel.allocated_qty 同步: 简化为按比例(单 order_item 多 rel 时不太精确,
            // 但这里我们就是针对单个 order_item 缩减, 把它名下所有 rel 同步到 newPlanQty)
            planRelMapperBean.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<com.aimes.modules.mrp.entity.BizProductionPlanOrderRel>()
                            .eq(com.aimes.modules.mrp.entity.BizProductionPlanOrderRel::getPlanItemId, piId)
                            .eq(com.aimes.modules.mrp.entity.BizProductionPlanOrderRel::getOrderItemId, orderItemId)
                            .set(com.aimes.modules.mrp.entity.BizProductionPlanOrderRel::getAllocatedQty, newPlanQty));
        }
    }

    /**
     * 提交审批：draft -> pending_approval
     * 根据ID获取订单明细行
     */
    public BizSalesOrderItem getOrderItem(Long itemId) {
        return itemMapper.selectById(itemId);
    }

    /**
     * 累加订单明细已申请任务数（发货时生成出库任务后调用）
     */
    @Transactional
    public void addItemTaskQty(Long itemId, BigDecimal qty) {
        if (itemId == null || qty == null) return;
        BizSalesOrderItem item = itemMapper.selectById(itemId);
        if (item == null) return;
        BigDecimal current = item.getTaskQty() != null ? item.getTaskQty() : BigDecimal.ZERO;
        item.setTaskQty(current.add(qty));
        itemMapper.updateById(item);
    }

    /**
     * 校验订单必须有至少一条明细才能提交
     *
     * @param id 订单ID
     */
    @Transactional
    public void submit(Long id) {
        BizSalesOrder order = getById(id);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!"draft".equals(order.getStatus())) {
            throw new BusinessException("只有草稿状态的订单可以提交");
        }

        // 校验是否有明细，空订单不允许提交审批
        long itemCount = itemMapper.selectCount(
                new LambdaQueryWrapper<BizSalesOrderItem>().eq(BizSalesOrderItem::getOrderId, id));
        if (itemCount == 0) {
            throw new BusinessException("订单至少需要一条明细");
        }

        // 尝试启动审批流程
        Long approvalInstanceId = null;
        try {
            Long applicantId = SecurityUtil.getCurrentUserId();
            if (applicantId == null) applicantId = order.getCreateBy();
            approvalInstanceId = approvalService.startApproval("sales_order", id, applicantId);
        } catch (Exception e) {
            // 启动审批异常时忽略
        }

        if (approvalInstanceId != null) {
            // 有审批流程，进入待审批状态
            order.setStatus("pending_approval");
        } else {
            // 无审批流程或未启用，直接确认
            order.setStatus("confirmed");
        }
        updateById(order);
        addLog(id, "submit", "提交审批");
        if ("confirmed".equals(order.getStatus())) ensureReceivableForConfirmedOrder(order);
    }

    /**
     * 审批通过：pending_approval -> confirmed
     * 记录审批人、审批时间和审批备注
     *
     * @param id     订单ID
     * @param remark 审批备注
     */
    @Transactional
    public void approve(Long id, String remark) {
        BizSalesOrder order = getById(id);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!"pending_approval".equals(order.getStatus())) {
            throw new BusinessException("只有待审批的订单可以审批");
        }

        Long currentUserId = SecurityUtil.getCurrentUserId();
        // 优先通过审批实例流转，状态由事件驱动更新
        BizApprovalInstance instance = approvalService.getByBusiness("sales_order", id);
        if (instance != null) {
            approvalService.approve(instance.getId(), currentUserId, remark);
        } else {
            // 无审批流程配置时，直接更新订单状态（兼容旧逻辑）
            order.setStatus("confirmed");
            order.setApprovedBy(currentUserId);
            order.setApprovedAt(LocalDateTime.now());
            order.setApproveRemark(remark);
            updateById(order);
            ensureReceivableForConfirmedOrder(order);
        }
        addLog(id, "approve", StringUtils.hasText(remark) ? "审批通过：" + remark : "审批通过");
    }

    /**
     * 审批驳回：pending_approval -> draft
     * 驳回后订单回到草稿状态，可重新编辑后再次提交
     *
     * @param id     订单ID
     * @param remark 驳回原因
     */
    @Transactional
    public void reject(Long id, String remark) {
        BizSalesOrder order = getById(id);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!"pending_approval".equals(order.getStatus())) {
            throw new BusinessException("只有待审批的订单可以驳回");
        }

        Long currentUserId = SecurityUtil.getCurrentUserId();
        // 优先通过审批实例流转，状态由事件驱动更新
        BizApprovalInstance instance = approvalService.getByBusiness("sales_order", id);
        if (instance != null) {
            approvalService.reject(instance.getId(), currentUserId, remark);
        } else {
            // 无审批流程配置时，直接更新订单状态（兼容旧逻辑）
            order.setStatus("draft");
            order.setApproveRemark(remark);
            order.setApprovedBy(null);
            order.setApprovedAt(null);
            updateById(order);
        }
        addLog(id, "reject", StringUtils.hasText(remark) ? "审批驳回：" + remark : "审批驳回");
    }

    /**
     * 监听审批完成事件，将销售订单推进到已确认状态
     */
    @EventListener
    @Transactional
    public void onApprovalCompleted(ApprovalCompletedEvent event) {
        if (!"sales_order".equals(event.getBusinessType())) return;
        BizSalesOrder order = getById(event.getBusinessId());
        if (order == null || !"pending_approval".equals(order.getStatus())) return;
        order.setStatus("confirmed");
        order.setApprovedBy(event.getApproverId());
        order.setApprovedAt(LocalDateTime.now());
        order.setApproveRemark(event.getRemark());
        updateById(order);
        ensureReceivableForConfirmedOrder(order);
    }

    /**
     * 订单状态变为 confirmed 时,自动生成应收(若该订单尚未生成过应收)。
     * - amount = order.totalAmount(含税)
     * - dueDate 按结算方式推算: 现结=今天; 月结30/60/90 = 今天 + N 天; 其它默认 +30 天
     */
    private void ensureReceivableForConfirmedOrder(BizSalesOrder order) {
        if (order == null || order.getId() == null) return;
        if (order.getTotalAmount() == null || order.getTotalAmount().signum() <= 0) return;
        long exists = receivableMapper.selectCount(new LambdaQueryWrapper<BizReceivable>()
                .eq(BizReceivable::getOrderId, order.getId()));
        if (exists > 0) return;
        BizReceivable r = new BizReceivable();
        r.setOrderId(order.getId());
        r.setCustomerId(order.getCustomerId());
        r.setAmount(order.getTotalAmount());
        r.setReceivedAmount(BigDecimal.ZERO);
        r.setDueDate(java.time.LocalDate.now().plusDays(settlementToDays(order.getSettlementType())));
        r.setRemark("订单确认自动生成应收-" + order.getOrderNo());
        try {
            financeService.createReceivable(r);
        } catch (Exception e) {
            log.warn("订单 {} 自动生成应收失败: {}", order.getOrderNo(), e.getMessage());
        }
    }

    private int settlementToDays(String s) {
        if (s == null) return 30;
        if (s.contains("现结") || "cash".equalsIgnoreCase(s)) return 0;
        if (s.contains("90")) return 90;
        if (s.contains("60")) return 60;
        if (s.contains("30")) return 30;
        return 30;
    }

    /**
     * 单价为含税单价,返回含税总价: qty * unitPrice
     * taxRate 仅用于派生不含税口径(展示),不参与含税总价计算
     */
    private static BigDecimal computeItemTaxIncludedAmount(BigDecimal qty, BigDecimal unitPrice, BigDecimal taxRate) {
        if (qty == null || unitPrice == null) return BigDecimal.ZERO;
        return qty.multiply(unitPrice).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * 监听审批驳回事件，将销售订单回退到草稿状态
     */
    @EventListener
    @Transactional
    public void onApprovalRejected(ApprovalRejectedEvent event) {
        if (!"sales_order".equals(event.getBusinessType())) return;
        BizSalesOrder order = getById(event.getBusinessId());
        if (order == null || !"pending_approval".equals(order.getStatus())) return;
        order.setStatus("draft");
        order.setApproveRemark(event.getRemark());
        order.setApprovedBy(null);
        order.setApprovedAt(null);
        updateById(order);
    }

    /**
     * 取消订单
     * 已完成和已取消的订单不可取消；生产中的订单不可直接取消（需先取消工单）
     *
     * @param id     订单ID
     * @param reason 取消原因
     */
    @Transactional
    public void cancel(Long id, String reason) {
        BizSalesOrder order = getById(id);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        // 校验状态：已完成和已取消的订单不可取消
        String currentStatus = order.getStatus();
        if ("cancelled".equals(currentStatus)) {
            throw new BusinessException("订单已取消");
        }
        if ("completed".equals(currentStatus)) {
            throw new BusinessException("已完成的订单不可取消");
        }
        if ("in_production".equals(currentStatus)) {
            throw new BusinessException("生产中的订单不可直接取消, 请先取消关联工单");
        }

        order.setStatus("cancelled");
        order.setCancelReason(reason);
        updateById(order);
        addLog(id, "cancel", StringUtils.hasText(reason) ? "取消订单：" + reason : "取消订单");

        // 同步删除MRP需求池中的记录
        removeMrpDemandPool(id);
    }

    /**
     * 更新订单状态（内部调用）
     * 严格校验状态流转合法性，防止非法状态跳转
     * 状态流转规则：draft -> pending_approval -> confirmed -> in_production -> completed
     *
     * @param id     订单ID
     * @param status 目标状态
     */
    @Transactional
    public void updateStatus(Long id, String status) {
        BizSalesOrder order = getById(id);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        // 校验状态流转合法性
        String currentStatus = order.getStatus();
        Set<String> allowedNextStatuses = STATUS_FLOW.getOrDefault(currentStatus, Set.of());
        if (!allowedNextStatuses.contains(status)) {
            throw new BusinessException("订单状态不允许从[" + currentStatus + "]变更为[" + status + "]");
        }

        order.setStatus(status);
        updateById(order);
    }

    // ==================== 新增：跨模块联动方法 ====================

    /**
     * 转计划单：confirmed → mrp_planned
     * 销售订单确认后，手动点击"转计划单"派发到MRP进行物料需求计划
     * 同时在MRP需求订单池中创建记录
     */
    @Transactional
    public void convertToPlan(Long id) {
        BizSalesOrder order = getById(id);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        // 已加入主计划: 由主计划统一拆分(转生产计划/转采购单), 不允许走老的转换路径
        if (order.getMasterPlanAdded() != null && order.getMasterPlanAdded() == 1) {
            throw new BusinessException("订单已加入主计划, 请到主计划页面操作");
        }
        // 允许在 confirmed/mrp_planned/in_production/outsourcing/partial_shipped 时转(订单变更后重新派发场景)
        Set<String> allowed = Set.of("confirmed", "mrp_planned", "in_production", "outsourcing", "partial_shipped");
        if (!allowed.contains(order.getStatus())) {
            throw new BusinessException("当前状态不允许转计划单");
        }
        // 仅在 confirmed 时推进状态; 已经在生产中保持当前状态
        if ("confirmed".equals(order.getStatus())) {
            order.setStatus("mrp_planned");
            updateById(order);
        }

        // 将订单数据流转到MRP需求订单池
        try {
            var mrpService = applicationContext.getBean(
                    com.aimes.modules.mrp.service.BizMrpService.class);
            mrpService.addToDemandPool(id);
        } catch (Exception e) {
            // 记录日志但不阻断转计划单操作
        }
    }

    /**
     * 直接转生产计划：跳过MRP，每个订单行项（产品）独立生成一个生产计划
     */
    @Transactional
    public void convertToProductionPlan(Long id) {
        BizSalesOrder order = getById(id);
        if (order == null) throw new BusinessException("订单不存在");
        if (order.getMasterPlanAdded() != null && order.getMasterPlanAdded() == 1) {
            throw new BusinessException("订单已加入主计划, 请到主计划页面操作");
        }
        Set<String> allowed = Set.of("confirmed", "mrp_planned", "in_production", "outsourcing", "partial_shipped");
        if (!allowed.contains(order.getStatus()))
            throw new BusinessException("当前状态不允许转生产计划");

        List<BizSalesOrderItem> items = itemMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BizSalesOrderItem>()
                        .eq(BizSalesOrderItem::getOrderId, id));
        if (items == null || items.isEmpty()) throw new BusinessException("订单无产品行项，无法生成计划");

        var planService = applicationContext.getBean(
                com.aimes.modules.mrp.service.BizProductionPlanService.class);
        var relMapper = applicationContext.getBean(
                com.aimes.modules.mrp.mapper.BizProductionPlanOrderRelMapper.class);

        // 预加载物料名称
        Set<Long> productIds = items.stream().map(BizSalesOrderItem::getProductId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, BizMaterial> materialMap = productIds.isEmpty() ? new HashMap<>()
                : materialMapper.selectBatchIds(productIds).stream()
                        .collect(Collectors.toMap(BizMaterial::getId, m -> m));

        java.time.LocalDate planEnd = order.getDeliveryDate() != null
                ? order.getDeliveryDate() : java.time.LocalDate.now().plusDays(30);

        int convertedItems = 0;
        // 仅给"未分配数量 > 0"的行项生成新计划; 已分配过的不重复发(支持订单变更后重新转换)
        for (BizSalesOrderItem item : items) {
            BigDecimal unallocated = computeUnallocatedQty(item);
            if (unallocated.compareTo(BigDecimal.ZERO) <= 0) continue;

            BizMaterial material = item.getProductId() != null ? materialMap.get(item.getProductId()) : null;
            String productName = material != null ? material.getMaterialName() : ("产品" + item.getProductId());

            com.aimes.modules.mrp.entity.BizProductionPlan plan = new com.aimes.modules.mrp.entity.BizProductionPlan();
            plan.setPlanName("销售订单 " + order.getOrderNo() + " - " + productName);
            plan.setCreateType("from_order");
            plan.setPlanDateFrom(java.time.LocalDate.now());
            plan.setPlanDateTo(planEnd);
            plan.setExtFields(order.getExtFields());

            com.aimes.modules.mrp.entity.BizProductionPlanItem planItem = new com.aimes.modules.mrp.entity.BizProductionPlanItem();
            planItem.setProductId(item.getProductId());
            planItem.setPlanQty(unallocated);
            planItem.setPlanStartDate(java.time.LocalDate.now());
            planItem.setPlanEndDate(planEnd);
            planItem.setSourceType("sales_order");
            planItem.setPriority(1);

            planService.createPlan(plan, java.util.List.of(planItem));

            // 建立计划行项与订单的追溯关系
            com.aimes.modules.mrp.entity.BizProductionPlanOrderRel rel = new com.aimes.modules.mrp.entity.BizProductionPlanOrderRel();
            rel.setPlanItemId(planItem.getId());
            rel.setOrderId(id);
            rel.setOrderItemId(item.getId());
            rel.setAllocatedQty(unallocated);
            relMapper.insert(rel);
            convertedItems++;
        }

        if (convertedItems == 0) {
            throw new BusinessException("订单所有行项已分配生产计划, 无新增需要转换");
        }

        // 仅在 confirmed 时推进到 in_production; 已生产中保持当前状态
        if ("confirmed".equals(order.getStatus())) {
            order.setStatus("in_production");
            updateById(order);
        }
    }

    /**
     * 工单创建时自动将订单状态推进到 in_production
     * 仅当订单处于 confirmed 或 mrp_planned 状态时才推进
     */
    public void updateOrderProductionStatus(Long orderId) {
        if (orderId == null) return;
        BizSalesOrder order = getById(orderId);
        if (order == null) return;
        if ("confirmed".equals(order.getStatus()) || "mrp_planned".equals(order.getStatus())) {
            order.setStatus("in_production");
            updateById(order);
        }
    }

    /**
     * 发货后检查是否全部发完，更新订单发货状态
     * 全部发完 → shipped, 部分发完 → partial_shipped
     */
    public void updateOrderShipmentStatus(Long orderId) {
        if (orderId == null) return;
        BizSalesOrder order = getById(orderId);
        if (order == null) return;

        List<BizSalesOrderItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<BizSalesOrderItem>()
                        .eq(BizSalesOrderItem::getOrderId, orderId));

        boolean allShipped = true;
        boolean anyShipped = false;
        for (BizSalesOrderItem item : items) {
            BigDecimal shipped = item.getShippedQty() != null ? item.getShippedQty() : BigDecimal.ZERO;
            if (shipped.compareTo(item.getQuantity()) >= 0) {
                anyShipped = true;
            } else {
                allShipped = false;
                if (shipped.compareTo(BigDecimal.ZERO) > 0) {
                    anyShipped = true;
                }
            }
        }

        if (allShipped && !items.isEmpty()) {
            order.setStatus("shipped");
            // 全部发货完成 → 自动从订单主计划移除(如果之前加入过)
            if (order.getMasterPlanAdded() != null && order.getMasterPlanAdded() == 1) {
                order.setMasterPlanAdded(0);
                order.setMasterPlanAddedAt(null);
            }
            updateById(order);
        } else if (anyShipped) {
            if (!"partial_shipped".equals(order.getStatus()) && !"shipped".equals(order.getStatus())) {
                order.setStatus("partial_shipped");
                updateById(order);
            }
        }
    }

    /**
     * 客户信用额度校验
     * @return 信用检查结果Map(isOverLimit, creditLimit, currentOutstanding, newTotal)
     */
    public Map<String, Object> checkCreditLimit(Long customerId, BigDecimal orderAmount) {
        Map<String, Object> result = new LinkedHashMap<>();
        BizCustomer customer = customerMapper.selectById(customerId);
        if (customer == null) {
            result.put("isOverLimit", false);
            result.put("creditLimit", BigDecimal.ZERO);
            result.put("currentOutstanding", BigDecimal.ZERO);
            result.put("newTotal", orderAmount);
            return result;
        }

        BigDecimal creditLimit = customer.getCreditLimit() != null ? customer.getCreditLimit() : BigDecimal.ZERO;
        if (creditLimit.compareTo(BigDecimal.ZERO) <= 0) {
            // 无信用额度限制
            result.put("isOverLimit", false);
            result.put("creditLimit", creditLimit);
            result.put("currentOutstanding", BigDecimal.ZERO);
            result.put("newTotal", orderAmount);
            return result;
        }

        // 查询当前未收应收总额
        List<BizReceivable> receivables = receivableMapper.selectList(
                new LambdaQueryWrapper<BizReceivable>()
                        .eq(BizReceivable::getCustomerId, customerId)
                        .ne(BizReceivable::getStatus, "settled"));
        BigDecimal outstanding = receivables.stream()
                .map(r -> {
                    BigDecimal amt = r.getAmount() != null ? r.getAmount() : BigDecimal.ZERO;
                    BigDecimal received = r.getReceivedAmount() != null ? r.getReceivedAmount() : BigDecimal.ZERO;
                    return amt.subtract(received);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal newTotal = outstanding.add(orderAmount != null ? orderAmount : BigDecimal.ZERO);
        boolean isOverLimit = newTotal.compareTo(creditLimit) > 0;

        result.put("isOverLimit", isOverLimit);
        result.put("creditLimit", creditLimit);
        result.put("currentOutstanding", outstanding);
        result.put("newTotal", newTotal);
        return result;
    }

    /**
     * 取消订单（增强版：级联取消关联工单）
     * 由外部调用，传入 workOrderService 避免循环依赖
     */
    @Transactional
    public void cancelWithCascade(Long id, String reason,
                                   com.aimes.modules.production.service.BizWorkOrderService workOrderService) {
        BizSalesOrder order = getById(id);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        String currentStatus = order.getStatus();
        if ("cancelled".equals(currentStatus)) {
            throw new BusinessException("订单已取消");
        }
        if ("shipped".equals(currentStatus) || "settled".equals(currentStatus)) {
            throw new BusinessException("已发货/已结算的订单不可取消");
        }

        order.setStatus("cancelled");
        order.setCancelReason(reason);
        updateById(order);

        // 同步删除MRP需求池中的记录
        removeMrpDemandPool(id);

        // 级联取消关联工单
        if (workOrderService != null) {
            try {
                workOrderService.cancelByOrderId(id, "订单取消联动");
            } catch (Exception e) {
                // 记录日志但不阻断订单取消
            }
        }
    }

    // ==================== V5.0 新增方法 ====================

    /**
     * 物理删除订单（仅草稿状态）
     */
    @Transactional
    public void physicalDelete(Long id) {
        BizSalesOrder order = getById(id);
        if (order == null) throw new BusinessException("订单不存在");
        if (!"draft".equals(order.getStatus())) {
            throw new BusinessException("只有草稿状态的订单可以删除");
        }
        // 物理删除明细
        itemMapper.delete(new LambdaQueryWrapper<BizSalesOrderItem>()
                .eq(BizSalesOrderItem::getOrderId, id));
        baseMapper.deleteById(id);
    }

    /**
     * 作废订单
     */
    @Transactional
    public void voidOrder(Long id, String reason) {
        BizSalesOrder order = getById(id);
        if (order == null) throw new BusinessException("订单不存在");
        String status = order.getStatus();
        if ("cancelled".equals(status) || "voided".equals(status)) {
            throw new BusinessException("订单已作废/取消");
        }
        if ("shipped".equals(status) || "settled".equals(status)) {
            throw new BusinessException("已发货/已结算的订单不可作废");
        }
        order.setStatus("voided");
        order.setCancelReason(reason);
        updateById(order);
        addLog(id, "void", StringUtils.hasText(reason) ? "作废订单：" + reason : "作废订单");

        // 同步删除MRP需求池中的记录
        removeMrpDemandPool(id);
    }

    /**
     * 订单完成（弱校验）
     */
    @Transactional
    public void complete(Long id) {
        BizSalesOrder order = getById(id);
        if (order == null) throw new BusinessException("订单不存在");
        String status = order.getStatus();
        if ("cancelled".equals(status) || "voided".equals(status)) {
            throw new BusinessException("已取消/作废的订单不可标记完成");
        }
        if ("completed".equals(status)) {
            throw new BusinessException("订单已完成");
        }
        order.setStatus("completed");
        updateById(order);
        addLog(id, "complete", "订单完成");

        // 同步删除MRP需求池中的记录
        removeMrpDemandPool(id);
    }

    /**
     * 添加操作日志（内部方法，失败不影响主流程）
     */
    public void addLog(Long orderId, String operation, String desc) {
        try {
            BizOrderLog log = new BizOrderLog();
            log.setOrderId(orderId);
            log.setOperation(operation);
            log.setOperationDesc(desc);
            try {
                Long userId = SecurityUtil.getCurrentUserId();
                if (userId != null) {
                    SysUser u = userMapper.selectById(userId);
                    log.setOperatorName(u != null ? u.getRealName() : "");
                }
            } catch (Exception ignored) {}
            orderLogMapper.insert(log);
        } catch (Exception ignored) {}
    }

    /**
     * 从MRP需求池中移除订单（内部方法）
     */
    private void removeMrpDemandPool(Long orderId) {
        try {
            var mrpService = applicationContext.getBean(
                    com.aimes.modules.mrp.service.BizMrpService.class);
            mrpService.removeFromDemandPool(orderId);
        } catch (Exception e) {
            // 记录日志但不阻断主流程
        }
    }

    /**
     * 查询历史价格
     */
    public List<Map<String, Object>> getPriceHistory(Long productId, Long customerId) {
        LambdaQueryWrapper<BizSalesOrderItem> itemWrapper = new LambdaQueryWrapper<BizSalesOrderItem>()
                .eq(BizSalesOrderItem::getProductId, productId)
                .orderByDesc(BizSalesOrderItem::getCreateTime)
                .last("LIMIT 20");

        List<BizSalesOrderItem> items = itemMapper.selectList(itemWrapper);
        if (items.isEmpty()) return Collections.emptyList();

        // 获取关联的订单信息用于按客户过滤
        Set<Long> orderIds = items.stream().map(BizSalesOrderItem::getOrderId).collect(Collectors.toSet());
        Map<Long, BizSalesOrder> orderMap = new HashMap<>();
        if (!orderIds.isEmpty()) {
            List<BizSalesOrder> orders = listByIds(orderIds);
            for (BizSalesOrder o : orders) orderMap.put(o.getId(), o);
        }

        return items.stream()
                .filter(item -> {
                    if (customerId == null) return true;
                    BizSalesOrder o = orderMap.get(item.getOrderId());
                    return o != null && customerId.equals(o.getCustomerId());
                })
                .map(item -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    BizSalesOrder o = orderMap.get(item.getOrderId());
                    map.put("orderNo", o != null ? o.getOrderNo() : "");
                    map.put("unitPrice", item.getUnitPrice());
                    map.put("quantity", item.getQuantity());
                    map.put("orderDate", o != null ? o.getCreateTime() : null);
                    return map;
                })
                .collect(Collectors.toList());
    }

    /**
     * 批量提交
     */
    @Transactional
    public void batchSubmit(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        for (Long id : ids) {
            try {
                submit(id);
            } catch (BusinessException e) {
                throw new BusinessException("订单ID=" + id + "提交失败: " + e.getMessage());
            }
        }
    }

    /**
     * 批量物理删除（仅草稿）
     */
    @Transactional
    public void batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        for (Long id : ids) {
            try {
                physicalDelete(id);
            } catch (BusinessException e) {
                throw new BusinessException("订单ID=" + id + "删除失败: " + e.getMessage());
            }
        }
    }

    /**
     * 批量作废
     */
    @Transactional
    public void batchVoid(List<Long> ids, String reason) {
        if (ids == null || ids.isEmpty()) return;
        for (Long id : ids) {
            try {
                voidOrder(id, reason);
            } catch (BusinessException e) {
                throw new BusinessException("订单ID=" + id + "作废失败: " + e.getMessage());
            }
        }
    }

    /**
     * 批量将已确认订单直接转工单（跳过MRP流程）
     * 每个订单的每个产品行创建一条工单
     * @return 创建的工单总数
     */
    @Transactional
    public int batchConvertToWorkOrder(List<Long> orderIds, com.aimes.modules.production.service.BizWorkOrderService workOrderService) {
        if (orderIds == null || orderIds.isEmpty()) return 0;
        int count = 0;
        for (Long orderId : orderIds) {
            BizSalesOrder order = getById(orderId);
            if (order == null || !"confirmed".equals(order.getStatus())) continue;
            List<BizSalesOrderItem> items = itemMapper.selectList(
                    new LambdaQueryWrapper<BizSalesOrderItem>().eq(BizSalesOrderItem::getOrderId, orderId));
            for (BizSalesOrderItem item : items) {
                com.aimes.modules.production.entity.BizWorkOrder wo = new com.aimes.modules.production.entity.BizWorkOrder();
                wo.setOrderId(orderId);
                wo.setOrderItemId(item.getId());
                wo.setProductId(item.getProductId());
                wo.setPlanQty(item.getQuantity());
                wo.setDeliveryDate(item.getDeliveryDate() != null ? item.getDeliveryDate() : order.getDeliveryDate());
                wo.setStatus("pending");
                workOrderService.createWorkOrder(wo);
                count++;
            }
        }
        return count;
    }

    /**
     * 获取订单生产进度详情
     */
    public Map<String, Object> getProductionDetail(Long id) {
        BizSalesOrder order = getById(id);
        if (order == null) throw new BusinessException("订单不存在");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", id);
        result.put("orderNo", order.getOrderNo());
        result.put("status", order.getStatus());

        // 查询订单明细
        List<BizSalesOrderItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<BizSalesOrderItem>().eq(BizSalesOrderItem::getOrderId, id));

        // 批量查询产品名称
        Set<Long> materialIds = items.stream().map(BizSalesOrderItem::getProductId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, BizMaterial> materialMap = new HashMap<>();
        if (!materialIds.isEmpty()) {
            materialMapper.selectBatchIds(materialIds).forEach(m -> materialMap.put(m.getId(), m));
        }

        // 获取工单和工序任务的 mapper
        var woMapper = applicationContext.getBean(com.aimes.modules.production.mapper.BizWorkOrderMapper.class);
        var taskMapper = applicationContext.getBean(com.aimes.modules.production.mapper.BizWoProcessTaskMapper.class);

        // 尝试获取关联工单信息
        List<Map<String, Object>> itemDetails = new ArrayList<>();
        for (BizSalesOrderItem item : items) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("productId", item.getProductId());
            BizMaterial mat = item.getProductId() != null ? materialMap.get(item.getProductId()) : null;
            detail.put("productName", mat != null ? mat.getMaterialName() : "未知产品");
            detail.put("spec", mat != null ? mat.getSpec() : null);
            detail.put("quantity", item.getQuantity());
            detail.put("shippedQty", item.getShippedQty());
            detail.put("warehouseInQty", item.getWarehouseInQty());
            detail.put("taskQty", item.getTaskQty());
            detail.put("progress", item.getQuantity() != null && item.getQuantity().compareTo(BigDecimal.ZERO) > 0
                    ? (item.getWarehouseInQty() != null ? item.getWarehouseInQty() : BigDecimal.ZERO)
                        .multiply(new BigDecimal(100)).divide(item.getQuantity(), 0, java.math.RoundingMode.HALF_UP).intValue()
                    : 0);

            // 查关联工单及工序任务
            try {
                var wos = woMapper.selectList(new LambdaQueryWrapper<com.aimes.modules.production.entity.BizWorkOrder>()
                        .eq(com.aimes.modules.production.entity.BizWorkOrder::getOrderId, id)
                        .eq(com.aimes.modules.production.entity.BizWorkOrder::getProductId, item.getProductId()));

                // 收集所有工单ID，批量查询工序任务
                List<Long> woIds = wos.stream().map(com.aimes.modules.production.entity.BizWorkOrder::getId).toList();
                Map<Long, List<com.aimes.modules.production.entity.BizWoProcessTask>> tasksByWo = new HashMap<>();
                Set<Long> allUserIds = new HashSet<>();
                if (!woIds.isEmpty()) {
                    var allTasks = taskMapper.selectList(
                            new LambdaQueryWrapper<com.aimes.modules.production.entity.BizWoProcessTask>()
                                    .in(com.aimes.modules.production.entity.BizWoProcessTask::getWorkOrderId, woIds)
                                    .orderByAsc(com.aimes.modules.production.entity.BizWoProcessTask::getStepGroup)
                                    .orderByAsc(com.aimes.modules.production.entity.BizWoProcessTask::getStepNo));
                    for (var t : allTasks) {
                        tasksByWo.computeIfAbsent(t.getWorkOrderId(), k -> new ArrayList<>()).add(t);
                        if (t.getAssignedUserId() != null) allUserIds.add(t.getAssignedUserId());
                    }
                }
                // 批量查询报工人姓名
                Map<Long, String> userNameMap = allUserIds.isEmpty() ? Map.of() :
                        userMapper.selectBatchIds(allUserIds).stream()
                                .collect(Collectors.toMap(SysUser::getId, SysUser::getRealName, (a, b) -> a));

                // 汇总所有工单的工序任务（同名工序合并数量）
                Map<String, Map<String, Object>> mergedProcesses = new LinkedHashMap<>();
                BigDecimal totalPlanQty = BigDecimal.ZERO;
                for (var wo : wos) {
                    totalPlanQty = totalPlanQty.add(wo.getPlanQty() != null ? wo.getPlanQty() : BigDecimal.ZERO);
                    List<com.aimes.modules.production.entity.BizWoProcessTask> woTasks =
                            tasksByWo.getOrDefault(wo.getId(), Collections.emptyList());
                    for (var task : woTasks) {
                        String stepName = task.getStepName();
                        Map<String, Object> pm = mergedProcesses.computeIfAbsent(stepName, k -> {
                            Map<String, Object> m = new LinkedHashMap<>();
                            m.put("stepName", stepName);
                            m.put("orderQty", BigDecimal.ZERO);
                            m.put("planQty", BigDecimal.ZERO);
                            m.put("completedQty", BigDecimal.ZERO);
                            m.put("reporters", new LinkedHashSet<String>());
                            return m;
                        });
                        pm.put("planQty", ((BigDecimal) pm.get("planQty"))
                                .add(task.getPlanQty() != null ? task.getPlanQty() : BigDecimal.ZERO));
                        pm.put("completedQty", ((BigDecimal) pm.get("completedQty"))
                                .add(task.getQualifiedQty() != null ? task.getQualifiedQty() : BigDecimal.ZERO));
                        // 收集报工人
                        @SuppressWarnings("unchecked")
                        Set<String> reporters = (Set<String>) pm.get("reporters");
                        if (task.getAssignedUserName() != null) {
                            reporters.add(task.getAssignedUserName());
                        } else if (task.getAssignedUserId() != null) {
                            String name = userNameMap.get(task.getAssignedUserId());
                            if (name != null) reporters.add(name);
                        }
                        if (task.getAssignedTeamName() != null) {
                            reporters.add(task.getAssignedTeamName());
                        }
                    }
                }
                // 设置每个工序的订单数量 = item.quantity
                BigDecimal orderQty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
                List<Map<String, Object>> processList = new ArrayList<>();
                for (Map<String, Object> pm : mergedProcesses.values()) {
                    pm.put("orderQty", orderQty);
                    @SuppressWarnings("unchecked")
                    Set<String> reporters = (Set<String>) pm.get("reporters");
                    pm.put("reporters", String.join("、", reporters));
                    processList.add(pm);
                }
                detail.put("totalPlanQty", totalPlanQty);
                detail.put("processes", processList);
            } catch (Exception e) {
                detail.put("totalPlanQty", BigDecimal.ZERO);
                detail.put("processes", Collections.emptyList());
            }
            itemDetails.add(detail);
        }
        result.put("items", itemDetails);
        return result;
    }

    /**
     * 导入订单 (Excel)
     * 模板列: 客户名称* | 联系人 | 收货地址 | 订单交货日期 | 产品编号 | 产品名称* | 规格型号 | 计量单位 | 数量* | 单价* | 税率% | 明细备注
     * - 客户不存在时自动创建
     * - 产品不存在时自动创建物料档案（类型=成品，属性=销售+生产）
     * - 规格型号含多个键值对(如"材质:304,尺寸:M8")时自动识别为多规格JSON
     * - 计量单位含换算(如"箱(1箱=12个)")时自动识别为多单位换算
     */
    /**
     * 导入订单 — 按订单级别导入，单个订单失败不影响其他订单
     * 失败的订单行会被收集，返回失败数据的Excel文件路径供前端下载
     */
    public Map<String, Object> importOrders(org.springframework.web.multipart.MultipartFile file) {
        Map<String, Object> result = new LinkedHashMap<>();
        int successCount = 0;
        int failCount = 0;
        List<String> autoCreated = new ArrayList<>();
        // 失败行数据：每行 = [失败原因, 原始各列值...]
        List<String[]> failedRows = new ArrayList<>();

        List<BizExtFieldConfig> extConfigs = extFieldConfigMapper.selectList(
                new LambdaQueryWrapper<BizExtFieldConfig>()
                        .eq(BizExtFieldConfig::getModule, "sales_order")
                        .eq(BizExtFieldConfig::getEnabled, 1)
                        .orderByAsc(BizExtFieldConfig::getSortOrder));
        // 明细级扩展字段(sales_order_item), 排在订单级扩展字段后面
        List<BizExtFieldConfig> itemExtConfigs = extFieldConfigMapper.selectList(
                new LambdaQueryWrapper<BizExtFieldConfig>()
                        .eq(BizExtFieldConfig::getModule, "sales_order_item")
                        .eq(BizExtFieldConfig::getEnabled, 1)
                        .orderByAsc(BizExtFieldConfig::getSortOrder));
        ObjectMapper jsonMapper = new ObjectMapper();
        // 模板: 第一列订单编号 + 原 12 列业务字段 + 订单级扩展字段 + 明细级扩展字段
        int totalCols = 13 + extConfigs.size() + itemExtConfigs.size();

        Map<String, BizCustomer> customerCache = new HashMap<>();
        Map<String, BizMaterial> materialCache = new HashMap<>();

        try (var workbook = org.apache.poi.ss.usermodel.WorkbookFactory.create(file.getInputStream())) {
            var sheet = workbook.getSheetAt(0);
            // 按分组键收集行，同时保留每行的原始字符串值用于失败导出
            Map<String, List<Map<String, Object>>> orderGroups = new LinkedHashMap<>();

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                var row = sheet.getRow(i);
                if (row == null) continue;
                try {
                    String orderNo = getCellString(row, 0);
                    String customerName = getCellString(row, 1);
                    if (customerName == null || customerName.isEmpty()) continue;

                    Map<String, Object> rowData = new LinkedHashMap<>();
                    rowData.put("orderNo", orderNo);
                    rowData.put("customerName", customerName);
                    rowData.put("contactName", getCellString(row, 2));
                    rowData.put("shippingAddress", getCellString(row, 3));
                    rowData.put("orderDeliveryDate", getCellString(row, 4));
                    rowData.put("productNo", getCellString(row, 5));
                    rowData.put("productName", getCellString(row, 6));
                    rowData.put("spec", getCellString(row, 7));
                    rowData.put("unit", getCellString(row, 8));
                    rowData.put("quantity", getCellNumeric(row, 9));
                    rowData.put("unitPrice", getCellNumeric(row, 10));
                    rowData.put("taxRate", getCellNumeric(row, 11));
                    rowData.put("itemRemark", getCellString(row, 12));
                    rowData.put("_rowNum", i + 1);
                    // 保留原始行值用于失败导出
                    String[] rawCells = new String[totalCols];
                    for (int c = 0; c < totalCols; c++) rawCells[c] = getCellString(row, c);
                    rowData.put("_rawCells", rawCells);

                    if (!extConfigs.isEmpty()) {
                        Map<String, Object> extMap = new LinkedHashMap<>();
                        for (int j = 0; j < extConfigs.size(); j++) {
                            String val = getCellString(row, 13 + j);
                            if (val != null && !val.isEmpty()) {
                                extMap.put(extConfigs.get(j).getFieldKey(), val);
                            }
                        }
                        if (!extMap.isEmpty()) rowData.put("_extFields", extMap);
                    }
                    // 明细级扩展字段, 列位置 = 13 + 订单级扩展列数 + j
                    if (!itemExtConfigs.isEmpty()) {
                        Map<String, Object> itemExtMap = new LinkedHashMap<>();
                        int base = 13 + extConfigs.size();
                        for (int j = 0; j < itemExtConfigs.size(); j++) {
                            String val = getCellString(row, base + j);
                            if (val != null && !val.isEmpty()) {
                                itemExtMap.put(itemExtConfigs.get(j).getFieldKey(), val);
                            }
                        }
                        if (!itemExtMap.isEmpty()) rowData.put("_itemExtFields", itemExtMap);
                    }

                    // 分组键: 优先用订单编号(NO:xxx), 否则按客户+联系人+收货+交货日期组合分组
                    String groupKey;
                    if (orderNo != null && !orderNo.trim().isEmpty()) {
                        groupKey = "NO:" + orderNo.trim();
                    } else {
                        groupKey = customerName + "|"
                                + nullToEmpty(getCellString(row, 2)) + "|"
                                + nullToEmpty(getCellString(row, 3)) + "|"
                                + nullToEmpty(getCellString(row, 4));
                    }
                    orderGroups.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(rowData);
                } catch (Exception e) {
                    // 行解析失败：收集原始行数据
                    String[] rawCells = new String[totalCols];
                    if (row != null) for (int c = 0; c < totalCols; c++) rawCells[c] = getCellString(row, c);
                    addFailedRow(failedRows, "第" + (i + 1) + "行解析失败: " + e.getMessage(), rawCells);
                    failCount++;
                }
            }

            // 按分组创建订单（每个订单独立，失败跳过）
            for (Map.Entry<String, List<Map<String, Object>>> entry : orderGroups.entrySet()) {
                List<Map<String, Object>> rows = entry.getValue();
                String orderError = null;
                try {
                    orderError = importSingleOrder(rows, customerCache, materialCache,
                            autoCreated, extConfigs, jsonMapper);
                } catch (Exception e) {
                    orderError = "订单创建失败: " + e.getMessage();
                }
                if (orderError != null) {
                    // 整个订单失败：所有行记入失败文件
                    for (Map<String, Object> rowData : rows) {
                        String[] rawCells = (String[]) rowData.get("_rawCells");
                        addFailedRow(failedRows, orderError, rawCells != null ? rawCells : new String[totalCols]);
                    }
                    failCount++;
                } else {
                    successCount++;
                }
            }
        } catch (Exception e) {
            throw new BusinessException("文件解析失败: " + e.getMessage());
        }

        result.put("successCount", successCount);
        result.put("failCount", failCount);
        result.put("autoCreated", autoCreated);

        // 生成失败数据Excel文件
        if (!failedRows.isEmpty()) {
            try {
                String failFilePath = generateFailedExcel(failedRows, extConfigs);
                result.put("failFileUrl", failFilePath);
            } catch (Exception e) {
                log.warn("生成失败文件异常: {}", e.getMessage());
            }
        }
        return result;
    }

    /**
     * 导入单个订单（独立事务），返回null表示成功，否则返回错误原因
     */
    @Transactional(rollbackFor = Exception.class)
    public String importSingleOrder(List<Map<String, Object>> rows,
                                     Map<String, BizCustomer> customerCache,
                                     Map<String, BizMaterial> materialCache,
                                     List<String> autoCreated,
                                     List<BizExtFieldConfig> extConfigs,
                                     ObjectMapper jsonMapper) {
        Map<String, Object> firstRow = rows.get(0);
        String customerName = getCellStr(firstRow, "customerName");

        // 查找或创建客户
        BizCustomer customer = customerCache.get(customerName);
        if (customer == null) {
            customer = customerMapper.selectOne(
                    new LambdaQueryWrapper<BizCustomer>()
                            .eq(BizCustomer::getCustomerName, customerName)
                            .last("LIMIT 1"));
            if (customer == null) {
                customer = new BizCustomer();
                customer.setCustomerName(customerName);
                customer.setContactName(getCellStr(firstRow, "contactName"));
                customer.setAddress(getCellStr(firstRow, "shippingAddress"));
                customer.setCustomerNo(CodeUtil.generate("C"));
                customer.setStatus("active");
                customerMapper.insert(customer);
                autoCreated.add("自动创建客户: " + customerName + " (" + customer.getCustomerNo() + ")");
            }
            customerCache.put(customerName, customer);
        }

        BizSalesOrder order = new BizSalesOrder();
        // 客户填写了订单编号则使用,否则留空由 create() 自动生成
        String orderNo = getCellStr(firstRow, "orderNo");
        if (orderNo != null && !orderNo.trim().isEmpty()) {
            order.setOrderNo(orderNo.trim());
        }
        order.setCustomerId(customer.getId());
        String contactName = getCellStr(firstRow, "contactName");
        order.setContactName(contactName != null ? contactName : customer.getContactName());
        String shippingAddr = getCellStr(firstRow, "shippingAddress");
        order.setShippingAddress(shippingAddr != null ? shippingAddr : customer.getAddress());
        String orderDeliveryStr = getCellStr(firstRow, "orderDeliveryDate");
        if (orderDeliveryStr != null && !orderDeliveryStr.isEmpty()) {
            try { order.setDeliveryDate(LocalDate.parse(orderDeliveryStr)); } catch (Exception ignored) {}
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> extFieldsMap = (Map<String, Object>) firstRow.get("_extFields");
        if (extFieldsMap != null && !extFieldsMap.isEmpty()) {
            try { order.setExtFields(jsonMapper.writeValueAsString(extFieldsMap)); } catch (Exception ignored) {}
        }

        List<BizSalesOrderItem> items = new ArrayList<>();
        List<String> itemErrors = new ArrayList<>();
        for (Map<String, Object> rowData : rows) {
            int rowNum = rowData.get("_rowNum") != null ? (int) rowData.get("_rowNum") : 0;
            String productNo = getCellStr(rowData, "productNo");
            String productName = getCellStr(rowData, "productName");

            if ((productNo == null || productNo.isEmpty()) && (productName == null || productName.isEmpty())) {
                itemErrors.add("第" + rowNum + "行产品编号和名称均为空");
                continue;
            }

            // 查找或创建物料
            String materialKey = productNo != null && !productNo.isEmpty() ? productNo : ("name:" + productName);
            BizMaterial material = materialCache.get(materialKey);
            if (material == null) {
                if (productNo != null && !productNo.isEmpty()) {
                    // 先查未删除的
                    material = materialMapper.selectOne(
                            new LambdaQueryWrapper<BizMaterial>()
                                    .eq(BizMaterial::getMaterialNo, productNo)
                                    .last("LIMIT 1"));
                    // 再查已软删除的(唯一索引占位),找到就恢复
                    if (material == null) {
                        material = materialMapper.selectOne(
                                new LambdaQueryWrapper<BizMaterial>()
                                        .eq(BizMaterial::getMaterialNo, productNo)
                                        .eq(BizMaterial::getDeleted, 1)
                                        .last("LIMIT 1"));
                        if (material != null) {
                            material.setDeleted(0);
                            material.setStatus("active");
                            if (productName != null) material.setMaterialName(productName);
                            materialMapper.updateById(material);
                            autoCreated.add("恢复已删除物料: " + material.getMaterialName() + " (" + material.getMaterialNo() + ")");
                        }
                    }
                }
                if (material == null && productName != null && !productName.isEmpty()) {
                    material = materialMapper.selectOne(
                            new LambdaQueryWrapper<BizMaterial>()
                                    .eq(BizMaterial::getMaterialName, productName)
                                    .last("LIMIT 1"));
                }
                if (material == null) {
                    // 新建前再次确认 materialNo 不冲突(含 deleted=1)
                    String newNo = productNo != null && !productNo.isEmpty()
                            ? productNo : CodeUtil.generate("M");
                    BizMaterial conflict = materialMapper.selectOne(
                            new LambdaQueryWrapper<BizMaterial>()
                                    .eq(BizMaterial::getMaterialNo, newNo)
                                    .eq(BizMaterial::getDeleted, 1)
                                    .last("LIMIT 1"));
                    if (conflict != null) {
                        // 恢复已删除的记录
                        conflict.setDeleted(0);
                        conflict.setStatus("active");
                        conflict.setMaterialName(productName != null ? productName : newNo);
                        materialMapper.updateById(conflict);
                        material = conflict;
                        autoCreated.add("恢复已删除物料: " + material.getMaterialName() + " (" + material.getMaterialNo() + ")");
                    } else {
                        material = new BizMaterial();
                        material.setMaterialNo(newNo);
                        material.setMaterialName(productName != null ? productName : newNo);
                        material.setCategory("finished");
                        material.setMaterialAttributes("production,sales");
                        String specStr = getCellStr(rowData, "spec");
                        if (specStr != null) material.setSpec(parseSpec(specStr));
                        String unitStr = getCellStr(rowData, "unit");
                        if (unitStr != null) parseAndSetUnit(material, unitStr);
                        material.setStatus("active");
                        material.setAllowStockLock(true);
                        materialMapper.insert(material);
                        autoCreated.add("自动创建物料: " + material.getMaterialName() + " (" + material.getMaterialNo() + ")");
                    }
                }
                materialCache.put(materialKey, material);
                if (productNo != null) materialCache.putIfAbsent("name:" + material.getMaterialName(), material);
                if (productName != null) materialCache.putIfAbsent(material.getMaterialNo(), material);
            }

            BizSalesOrderItem item = new BizSalesOrderItem();
            item.setProductId(material.getId());
            BigDecimal qty = rowData.get("quantity") != null ? new BigDecimal(rowData.get("quantity").toString()) : null;
            if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
                itemErrors.add("第" + rowNum + "行数量无效");
                continue;
            }
            item.setQuantity(qty);
            item.setUnitPrice(rowData.get("unitPrice") != null ? new BigDecimal(rowData.get("unitPrice").toString()) : BigDecimal.ZERO);
            if (rowData.get("taxRate") != null) item.setTaxRate(new BigDecimal(rowData.get("taxRate").toString()));
            String itemRemark = getCellStr(rowData, "itemRemark");
            if (itemRemark != null) item.setRemark(itemRemark);
            // 明细级扩展字段
            @SuppressWarnings("unchecked")
            Map<String, Object> itemExtFieldsMap = (Map<String, Object>) rowData.get("_itemExtFields");
            if (itemExtFieldsMap != null && !itemExtFieldsMap.isEmpty()) {
                try { item.setExtFields(jsonMapper.writeValueAsString(itemExtFieldsMap)); } catch (Exception ignored) {}
            }
            items.add(item);
        }

        if (items.isEmpty()) {
            String reason = "没有有效的产品明细";
            if (!itemErrors.isEmpty()) reason += "（" + String.join("; ", itemErrors) + "）";
            return reason;
        }

        create(order, items);
        return null; // 成功
    }

    private void addFailedRow(List<String[]> failedRows, String reason, String[] rawCells) {
        String[] row = new String[rawCells.length + 1];
        row[0] = reason;
        System.arraycopy(rawCells, 0, row, 1, rawCells.length);
        failedRows.add(row);
    }

    /**
     * 生成导入失败数据的Excel文件，第一列为失败原因
     */
    private String generateFailedExcel(List<String[]> failedRows, List<BizExtFieldConfig> extConfigs) throws IOException {
        List<String> headers = new ArrayList<>();
        headers.add("失败原因");
        headers.addAll(Arrays.asList(
                "订单编号", "客户名称", "联系人", "收货地址", "订单交货日期",
                "产品编号", "产品名称", "规格型号", "计量单位",
                "数量", "单价", "税率%", "明细备注"));
        for (BizExtFieldConfig fc : extConfigs) headers.add(fc.getFieldLabel());

        org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        var sheet = workbook.createSheet("导入失败数据");
        // 表头
        var headerRow = sheet.createRow(0);
        var headerStyle = workbook.createCellStyle();
        var font = workbook.createFont();
        font.setBold(true);
        headerStyle.setFont(font);
        headerStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.LIGHT_YELLOW.getIndex());
        headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
        for (int i = 0; i < headers.size(); i++) {
            var cell = headerRow.createCell(i);
            cell.setCellValue(headers.get(i));
            cell.setCellStyle(headerStyle);
        }
        // 失败原因列标红
        var redStyle = workbook.createCellStyle();
        var redFont = workbook.createFont();
        redFont.setColor(org.apache.poi.ss.usermodel.IndexedColors.RED.getIndex());
        redStyle.setFont(redFont);

        // 数据行
        for (int r = 0; r < failedRows.size(); r++) {
            var row = sheet.createRow(r + 1);
            String[] cells = failedRows.get(r);
            for (int c = 0; c < cells.length && c < headers.size(); c++) {
                var cell = row.createCell(c);
                cell.setCellValue(cells[c] != null ? cells[c] : "");
                if (c == 0) cell.setCellStyle(redStyle);
            }
        }
        // 自动列宽（前几列）
        for (int i = 0; i < Math.min(headers.size(), 5); i++) sheet.autoSizeColumn(i);
        sheet.setColumnWidth(0, 15000); // 失败原因列加宽

        // 写入临时文件
        String fileName = "导入失败_" + System.currentTimeMillis() + ".xlsx";
        java.io.File dir = new java.io.File(fileUploadPath + "/import-errors");
        if (!dir.exists()) dir.mkdirs();
        java.io.File outFile = new java.io.File(dir, fileName);
        try (var fos = new java.io.FileOutputStream(outFile)) {
            workbook.write(fos);
        }
        workbook.close();
        return "/api/order/sales-orders/import-errors/" + fileName;
    }

    /**
     * 解析规格型号：判断单规格还是多规格
     * 多规格格式: "材质:304,尺寸:M8" 或 "材质:304;尺寸:M8" → JSON {"材质":"304","尺寸":"M8"}
     * 单规格: 直接返回原文
     */
    private String parseSpec(String specStr) {
        if (specStr == null || specStr.isEmpty()) return specStr;
        // 判断是否为多规格（含冒号分隔的键值对）
        if (specStr.contains(":") || specStr.contains("：")) {
            try {
                String normalized = specStr.replace("：", ":").replace("；", ";");
                String[] pairs = normalized.split("[,;，]");
                if (pairs.length > 0 && pairs[0].contains(":")) {
                    // 输出数组格式: [{"name":"材质","value":"NBR"}] — 与前端多规格模式兼容
                    List<Map<String, String>> specList = new ArrayList<>();
                    for (String pair : pairs) {
                        String[] kv = pair.split(":", 2);
                        if (kv.length == 2) {
                            String specName = kv[0].trim();
                            Map<String, String> m = new LinkedHashMap<>();
                            m.put("name", specName);
                            m.put("value", kv[1].trim());
                            specList.add(m);
                            ensureSpecExists(specName);
                        }
                    }
                    if (!specList.isEmpty()) {
                        ObjectMapper om = new ObjectMapper();
                        return om.writeValueAsString(specList);
                    }
                }
            } catch (Exception ignored) {}
        }
        return specStr;
    }

    /**
     * 解析计量单位：判断单一单位还是多单位换算
     * 多单位格式: "箱(1箱=12个)" → unit="箱", unitConfig={"units":[{"unit":"箱","rate":1,"isBase":true},{"unit":"个","rate":12,"isBase":false}]}
     * 单一单位: "个" → unit="个", unitConfig=null
     */
    private void parseAndSetUnit(BizMaterial material, String unitStr) {
        if (unitStr == null || unitStr.isEmpty()) return;
        // 检测多单位换算格式: "主单位(1主单位=N副单位)"
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("^(.+?)\\s*[（(]\\s*1\\s*\\1\\s*=\\s*(\\d+)\\s*(.+?)\\s*[）)]$")
                .matcher(unitStr);
        if (m.matches()) {
            String mainUnit = m.group(1).trim();
            int rate = Integer.parseInt(m.group(2));
            String subUnit = m.group(3).trim();
            material.setUnit(mainUnit);
            // 格式A: {"baseUnit":"箱","conversions":[{"unit":"个","ratio":100}]} — 与前端 secondaryDisplay 兼容
            material.setUnitConfig(String.format(
                    "{\"baseUnit\":\"%s\",\"conversions\":[{\"unit\":\"%s\",\"ratio\":%d}]}",
                    mainUnit, subUnit, rate));
            ensureUnitExists(mainUnit);
            ensureUnitExists(subUnit);
        } else {
            material.setUnit(unitStr.trim());
            ensureUnitExists(unitStr.trim());
        }
    }

    private void ensureSpecExists(String specName) {
        if (specName == null || specName.isEmpty()) return;
        Long exists = materialSpecMapper.selectCount(new LambdaQueryWrapper<com.aimes.modules.master.entity.BizMaterialSpec>()
                .eq(com.aimes.modules.master.entity.BizMaterialSpec::getSpecName, specName)
                .eq(com.aimes.modules.master.entity.BizMaterialSpec::getDeleted, 0));
        if (exists == 0) {
            com.aimes.modules.master.entity.BizMaterialSpec spec = new com.aimes.modules.master.entity.BizMaterialSpec();
            spec.setSpecName(specName);
            spec.setStatus("active");
            materialSpecMapper.insert(spec);
        }
    }

    private void ensureUnitExists(String unitName) {
        if (unitName == null || unitName.isEmpty()) return;
        Long exists = bizUnitMapper.selectCount(new LambdaQueryWrapper<com.aimes.modules.master.entity.BizUnit>()
                .eq(com.aimes.modules.master.entity.BizUnit::getUnitName, unitName)
                .eq(com.aimes.modules.master.entity.BizUnit::getDeleted, 0));
        if (exists == 0) {
            com.aimes.modules.master.entity.BizUnit unit = new com.aimes.modules.master.entity.BizUnit();
            unit.setUnitName(unitName);
            unit.setUnitCode(unitName.toUpperCase());
            unit.setStatus("active");
            bizUnitMapper.insert(unit);
        }
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    private String getCellStr(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return null;
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private String getCellString(org.apache.poi.ss.usermodel.Row row, int idx) {
        var cell = row.getCell(idx);
        if (cell == null) return null;
        if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
            // 日期格式单元格转为 yyyy-MM-dd 字符串
            if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toLocalDate()
                        .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE);
            }
            return String.valueOf((long) cell.getNumericCellValue());
        }
        return cell.getStringCellValue().trim();
    }

    private BigDecimal getCellNumeric(org.apache.poi.ss.usermodel.Row row, int idx) {
        var cell = row.getCell(idx);
        if (cell == null) return null;
        try {
            if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
                return BigDecimal.valueOf(cell.getNumericCellValue());
            }
            String val = cell.getStringCellValue().trim();
            return val.isEmpty() ? null : new BigDecimal(val);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 工单完工后同步销售订单行项的生产进度（producedQty）
     * 汇总同一订单行下所有工单的 completedQty 更新到 producedQty；
     * 若所有行项均已完产，检查是否需要更新订单状态
     */
    public void syncOrderItemProducedQty(Long orderId, Long orderItemId) {
        if (orderId == null) return;

        // 汇总该订单行下所有未取消工单的 completedQty
        LambdaQueryWrapper<BizWorkOrder> wrapper = new LambdaQueryWrapper<BizWorkOrder>()
                .eq(BizWorkOrder::getOrderId, orderId)
                .ne(BizWorkOrder::getStatus, "cancelled");
        if (orderItemId != null) {
            wrapper.eq(BizWorkOrder::getOrderItemId, orderItemId);
        }
        List<BizWorkOrder> workOrders = workOrderMapper.selectList(wrapper);
        BigDecimal totalProduced = workOrders.stream()
                .map(wo -> wo.getCompletedQty() != null ? wo.getCompletedQty() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 更新订单行的生产完工数
        if (orderItemId != null) {
            BizSalesOrderItem item = itemMapper.selectById(orderItemId);
            if (item != null) {
                item.setProducedQty(totalProduced);
                itemMapper.updateById(item);
            }
        }
    }

    // ==================== 整单外协 ====================

    /**
     * 批量将已确认订单转为整单外协
     * 每个订单行创建一条外协申请 + 外协加工单（自动确认）
     * BOM原材料作为发料清单，成品作为收料物料
     *
     * @param orderIds   订单ID列表
     * @param supplierId 外协供应商ID
     * @param unitPrices 各订单行的外协单价（key=orderItemId, value=单价），可为null则用订单行原单价
     * @param taxRate    税率(%)
     * @param deliveryDate 交付日期（可null则沿用订单交付日期）
     * @param remark     备注
     * @return 创建的外协加工单数量
     */
    @Transactional(rollbackFor = Exception.class)
    public int batchConvertToOutsource(List<Long> orderIds, Long supplierId,
                                        Map<Long, BigDecimal> unitPrices,
                                        BigDecimal taxRate, LocalDate deliveryDate, String remark) {
        if (orderIds == null || orderIds.isEmpty()) return 0;
        if (supplierId == null) throw new BusinessException("请选择外协供应商");

        Set<String> allowedStatuses = Set.of("confirmed", "mrp_planned", "in_production", "outsourcing", "partial_shipped");
        int count = 0;
        for (Long orderId : orderIds) {
            BizSalesOrder order = getById(orderId);
            if (order == null || !allowedStatuses.contains(order.getStatus())) continue;
            // 已加入主计划的订单跳过 — 主计划负责拆分, 外协走主计划入口
            if (order.getMasterPlanAdded() != null && order.getMasterPlanAdded() == 1) continue;

            List<BizSalesOrderItem> items = itemMapper.selectList(
                    new LambdaQueryWrapper<BizSalesOrderItem>().eq(BizSalesOrderItem::getOrderId, orderId));

            for (BizSalesOrderItem item : items) {
                // 仅给"未分配数量 > 0"的行项创建外协(支持订单变更后重新外协)
                BigDecimal unallocated = computeUnallocatedQty(item);
                if (unallocated.compareTo(BigDecimal.ZERO) <= 0) continue;

                // 1. 查询产品信息
                Long productId = item.getProductId();
                BizMaterial product = materialMapper.selectById(productId);

                // 2. 计算外协单价
                BigDecimal itemUnitPrice = unitPrices != null && unitPrices.containsKey(item.getId())
                        ? unitPrices.get(item.getId())
                        : (item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO);

                // 3. 直接创建外协加工单（draft 状态，不再创建申请）
                BizOutsourceOrder outsourceOrder = new BizOutsourceOrder();
                outsourceOrder.setOrderNo(CodeUtil.generate("OO"));
                outsourceOrder.setSupplierId(supplierId);
                outsourceOrder.setProductId(productId);
                outsourceOrder.setStepName("整单外协");
                outsourceOrder.setTotalQty(unallocated);
                outsourceOrder.setUnitPrice(itemUnitPrice);
                outsourceOrder.setTaxRate(taxRate != null ? taxRate : BigDecimal.ZERO);
                BigDecimal totalAmount = itemUnitPrice.multiply(unallocated);
                outsourceOrder.setTotalAmount(totalAmount);
                BigDecimal rate = outsourceOrder.getTaxRate();
                BigDecimal tax = totalAmount.multiply(rate)
                        .divide(rate.add(new BigDecimal("100")), 2, BigDecimal.ROUND_HALF_UP);
                outsourceOrder.setTaxAmount(tax);
                outsourceOrder.setDeliveryDate(deliveryDate != null ? deliveryDate
                        : (item.getDeliveryDate() != null ? item.getDeliveryDate() : order.getDeliveryDate()));
                outsourceOrder.setDeliveredQty(BigDecimal.ZERO);
                outsourceOrder.setQualifiedQty(BigDecimal.ZERO);
                outsourceOrder.setDeliveryQty(BigDecimal.ZERO);
                outsourceOrder.setDeliveryTaskQty(BigDecimal.ZERO);
                outsourceOrder.setDeliveryOutQty(BigDecimal.ZERO);
                outsourceOrder.setReceiptTaskQty(BigDecimal.ZERO);
                outsourceOrder.setReceiptInQty(BigDecimal.ZERO);
                outsourceOrder.setReceiptMaterialId(productId);  // 收料=成品
                outsourceOrder.setOrderId(orderId);
                outsourceOrder.setOrderItemId(item.getId());
                outsourceOrder.setStatus("draft");
                outsourceOrder.setRemark(remark);
                outsourceOrderMapper.insert(outsourceOrder);

                count++;
            }

            // 6. 更新订单状态和外协类型(仅在 confirmed 时推进; 已生产中保持当前状态)
            if ("confirmed".equals(order.getStatus())) {
                order.setStatus("outsourcing");
                order.setOutsourceType("full");
                updateById(order);
            }

            notificationService.notifyRole("production",
                    "整单外协",
                    "订单[" + order.getOrderNo() + "]已转为整单外协，共" + items.size() + "个产品行",
                    "alert", "sales_order", orderId);
        }
        return count;
    }

    /**
     * 查询产品的BOM一级子件列表（原材料）
     */
    public List<BizBomItem> getBomItemsForProduct(Long productId) {
        // 查找产品的active BOM版本
        BizBomVersion activeVersion = bomVersionMapper.selectOne(
                new LambdaQueryWrapper<BizBomVersion>()
                        .eq(BizBomVersion::getProductId, productId)
                        .eq(BizBomVersion::getStatus, "active")
                        .last("LIMIT 1"));
        if (activeVersion == null) return Collections.emptyList();

        // 查询一级子件
        return bomItemMapper.selectList(
                new LambdaQueryWrapper<BizBomItem>()
                        .eq(BizBomItem::getBomVersionId, activeVersion.getId())
                        .isNull(BizBomItem::getParentItemId)
                        .orderByAsc(BizBomItem::getSortOrder));
    }

    /**
     * 整单外协收货完成后同步订单行项进度
     * 由OutsourceChainService调用
     */
    public void syncOutsourceOrderItemProgress(Long orderId, Long orderItemId, BigDecimal qualifiedQty) {
        if (orderItemId != null) {
            BizSalesOrderItem item = itemMapper.selectById(orderItemId);
            if (item != null) {
                BigDecimal currentProduced = item.getProducedQty() != null ? item.getProducedQty() : BigDecimal.ZERO;
                item.setProducedQty(currentProduced.add(qualifiedQty));
                // 同时更新入库数（外协收货=入库）
                BigDecimal currentWarehouseIn = item.getWarehouseInQty() != null ? item.getWarehouseInQty() : BigDecimal.ZERO;
                item.setWarehouseInQty(currentWarehouseIn.add(qualifiedQty));
                itemMapper.updateById(item);
            }
        }

        // 检查订单所有行是否都已完成
        if (orderId != null) {
            BizSalesOrder order = getById(orderId);
            if (order != null && "outsourcing".equals(order.getStatus())) {
                List<BizSalesOrderItem> allItems = itemMapper.selectList(
                        new LambdaQueryWrapper<BizSalesOrderItem>().eq(BizSalesOrderItem::getOrderId, orderId));
                boolean allCompleted = allItems.stream().allMatch(i -> {
                    BigDecimal qty = i.getQuantity() != null ? i.getQuantity() : BigDecimal.ZERO;
                    BigDecimal produced = i.getProducedQty() != null ? i.getProducedQty() : BigDecimal.ZERO;
                    return produced.compareTo(qty) >= 0;
                });
                // 此处不自动完成订单，仅通知
                if (allCompleted) {
                    notificationService.notifyRole("sales",
                            "外协生产完成",
                            "订单[" + order.getOrderNo() + "]所有外协产品已收货入库，可安排发货",
                            "alert", "sales_order", orderId);
                }
            }
        }
    }

    /**
     * 外协加工单完成后同步销售订单进度并推进状态
     * 由 OutsourceReceiptService.checkAndCompleteOutsourceOrder 调用
     */
    public void syncOutsourceOrderCompleted(Long orderId, Long orderItemId, BigDecimal qualifiedQty) {
        if (orderItemId != null) {
            BizSalesOrderItem item = itemMapper.selectById(orderItemId);
            if (item != null) {
                BigDecimal currentProduced = item.getProducedQty() != null ? item.getProducedQty() : BigDecimal.ZERO;
                BigDecimal currentWarehouseIn = item.getWarehouseInQty() != null ? item.getWarehouseInQty() : BigDecimal.ZERO;
                BigDecimal itemQty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
                // 以订单数量封顶，避免重复累加
                item.setProducedQty(currentProduced.add(qualifiedQty).min(itemQty));
                item.setWarehouseInQty(currentWarehouseIn.add(qualifiedQty).min(itemQty));
                itemMapper.updateById(item);
            }
        }

        if (orderId != null) {
            BizSalesOrder order = getById(orderId);
            if (order != null && "outsourcing".equals(order.getStatus())) {
                List<BizSalesOrderItem> allItems = itemMapper.selectList(
                        new LambdaQueryWrapper<BizSalesOrderItem>().eq(BizSalesOrderItem::getOrderId, orderId));
                boolean allCompleted = !allItems.isEmpty() && allItems.stream().allMatch(i -> {
                    BigDecimal qty = i.getQuantity() != null ? i.getQuantity() : BigDecimal.ZERO;
                    BigDecimal produced = i.getProducedQty() != null ? i.getProducedQty() : BigDecimal.ZERO;
                    return produced.compareTo(qty) >= 0;
                });
                if (allCompleted) {
                    order.setStatus("completed");
                    updateById(order);
                    log.info("外协加工完成，销售订单状态推进为 completed: orderId={}", orderId);
                    try {
                        notificationService.notifyRole("sales",
                                "外协订单完成",
                                "订单[" + order.getOrderNo() + "]所有外协产品已收货入库，可安排发货",
                                "alert", "sales_order", orderId);
                    } catch (Exception ignored) {}
                }
            }
        }
    }
}
