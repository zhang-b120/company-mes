package com.aimes.modules.finance.service;

import com.aimes.common.exception.BusinessException;
import com.aimes.common.result.PageResult;
import com.aimes.common.utils.CodeUtil;
import com.aimes.modules.finance.entity.*;
import com.aimes.modules.finance.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.aimes.modules.master.entity.BizCustomer;
import com.aimes.modules.master.entity.BizMaterial;
import com.aimes.modules.master.entity.BizProduct;
import com.aimes.modules.master.entity.BizSupplier;
import com.aimes.modules.master.mapper.BizCustomerMapper;
import com.aimes.modules.master.mapper.BizMaterialMapper;
import com.aimes.modules.master.mapper.BizProductMapper;
import com.aimes.modules.master.mapper.BizSupplierMapper;
import com.aimes.modules.order.entity.BizSalesOrder;
import com.aimes.modules.order.entity.BizSalesOrderItem;
import com.aimes.modules.order.mapper.BizSalesOrderMapper;
import com.aimes.modules.order.mapper.BizSalesOrderItemMapper;
import com.aimes.modules.production.entity.BizWorkOrder;
import com.aimes.modules.production.mapper.BizWorkOrderMapper;
import com.aimes.modules.system.entity.SysUser;
import com.aimes.modules.system.mapper.SysUserMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 财务服务
 * 管理应收账款、应付账款、收付款记录、工单成本和月度工资
 * 应收状态: pending → partial_received → received / overdue
 * 应付状态: pending → partial_paid → paid / overdue
 * 工资状态: pending → confirmed → approved → paid
 */
@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class BizFinanceService {

    private final BizReceivableMapper receivableMapper;
    private final BizPayableMapper payableMapper;
    private final BizPaymentRecordMapper paymentRecordMapper;
    private final BizFinanceInvoiceMapper invoiceMapper;
    private final BizWoCostMapper woCostMapper;
    private final BizWageMonthlyMapper wageMonthlyMapper;
    private final BizSalesOrderMapper salesOrderMapper;
    private final BizSalesOrderItemMapper salesOrderItemMapper;
    private final BizWorkOrderMapper workOrderMapper;
    private final BizCustomerMapper customerMapper;
    private final BizProductMapper productMapper;
    private final BizMaterialMapper materialMapper;
    private final BizSupplierMapper supplierMapper;
    private final SysUserMapper userMapper;
    private final AccAccountMapper accAccountMapper;
    private final AccVoucherMapper accVoucherMapper;
    private final AccVoucherLineMapper accVoucherLineMapper;
    private final AccPeriodCloseMapper accPeriodCloseMapper;
    private final ApplicationContext applicationContext;

    /**
     * 应收账款分页查询（支持拆分查询条件：应收编号、订单号、客户名称）
     */
    public PageResult<Map<String, Object>> getReceivablePage(String keyword, String receivableNo, String orderNo, String customerName,
                                                              String status, Long customerId, Long orderId, int pageNum, int pageSize) {
        return getReceivablePage(keyword, receivableNo, orderNo, customerName, status, customerId, orderId, null, null, null, pageNum, pageSize);
    }

    /**
     * 重载: 支持"截至日期"快照。asOfDate 非空时:
     *   - 只显示该日期前已创建的应收单 (create_time <= asOfDate)
     *   - 该单的"已收金额" 按 payment_date <= asOfDate 累计
     *   - 未收 = 应收 - 重算后的已收
     *   - 状态字段也按重算结果回推 (received / partial_received / pending)
     */
    public PageResult<Map<String, Object>> getReceivablePage(String keyword, String receivableNo, String orderNo, String customerName,
                                                              String status, Long customerId, Long orderId,
                                                              String dueStart, String dueEnd, String asOfDate,
                                                              int pageNum, int pageSize) {
        // 先按订单号/客户名称反查ID集合
        Set<Long> matchOrderIds = null;
        Set<Long> matchCustomerIds = null;

        if (StringUtils.hasText(orderNo)) {
            matchOrderIds = salesOrderMapper.selectList(
                    new LambdaQueryWrapper<BizSalesOrder>().like(BizSalesOrder::getOrderNo, orderNo))
                    .stream().map(BizSalesOrder::getId).collect(Collectors.toSet());
            if (matchOrderIds.isEmpty()) return new PageResult<>(List.of(), 0, pageNum, pageSize);
        }
        if (StringUtils.hasText(customerName)) {
            matchCustomerIds = customerMapper.selectList(
                    new LambdaQueryWrapper<BizCustomer>().like(BizCustomer::getCustomerName, customerName))
                    .stream().map(BizCustomer::getId).collect(Collectors.toSet());
            if (matchCustomerIds.isEmpty()) return new PageResult<>(List.of(), 0, pageNum, pageSize);
        }

        // asOfDate 模式: 截至该日的快照视图 (仅取该日前已创建的应收, 已收按 payment_date <= 截至日重算)
        LocalDate snapDate = StringUtils.hasText(asOfDate) ? LocalDate.parse(asOfDate) : null;

        LambdaQueryWrapper<BizReceivable> wrapper = new LambdaQueryWrapper<BizReceivable>()
                .like(StringUtils.hasText(keyword), BizReceivable::getReceivableNo, keyword)
                .like(StringUtils.hasText(receivableNo), BizReceivable::getReceivableNo, receivableNo)
                .eq(customerId != null, BizReceivable::getCustomerId, customerId)
                .eq(orderId != null, BizReceivable::getOrderId, orderId)
                .in(matchOrderIds != null, BizReceivable::getOrderId, matchOrderIds)
                .in(matchCustomerIds != null, BizReceivable::getCustomerId, matchCustomerIds)
                .ge(StringUtils.hasText(dueStart), BizReceivable::getDueDate, StringUtils.hasText(dueStart) ? LocalDate.parse(dueStart) : null)
                .le(StringUtils.hasText(dueEnd), BizReceivable::getDueDate, StringUtils.hasText(dueEnd) ? LocalDate.parse(dueEnd) : null)
                .le(snapDate != null, BizReceivable::getCreateTime, snapDate != null ? snapDate.plusDays(1).atStartOfDay() : null)
                .orderByDesc(BizReceivable::getCreateTime);
        // 快照模式下 status 不直接走 DB 过滤, 后面按重算后状态在内存里过滤;
        // 非快照模式仍走原 DB 状态过滤
        if (snapDate == null) {
            wrapper.eq(StringUtils.hasText(status), BizReceivable::getStatus, status);
        }

        Page<BizReceivable> page = receivableMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        // 批量加载客户名称
        Set<Long> cids = page.getRecords().stream().map(BizReceivable::getCustomerId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> cNameMap = cids.isEmpty() ? Map.of() :
                customerMapper.selectBatchIds(cids).stream().collect(Collectors.toMap(BizCustomer::getId, BizCustomer::getCustomerName, (a, b) -> a));

        // 批量加载关联订单号
        Set<Long> loadOrderIds = page.getRecords().stream().map(BizReceivable::getOrderId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> orderNoMap = loadOrderIds.isEmpty() ? Map.of() :
                salesOrderMapper.selectBatchIds(loadOrderIds).stream().collect(Collectors.toMap(BizSalesOrder::getId, BizSalesOrder::getOrderNo, (a, b) -> a));

        // 快照模式: 按 payment_date <= asOfDate 累计已收金额
        Map<Long, BigDecimal> paidByReceivable = new HashMap<>();
        if (snapDate != null && !page.getRecords().isEmpty()) {
            Set<Long> rids = page.getRecords().stream().map(BizReceivable::getId).collect(Collectors.toSet());
            List<BizPaymentRecord> payments = paymentRecordMapper.selectList(
                    new LambdaQueryWrapper<BizPaymentRecord>()
                            .eq(BizPaymentRecord::getDeleted, 0)
                            .eq(BizPaymentRecord::getRelatedType, "receivable")
                            .in(BizPaymentRecord::getRelatedId, rids)
                            .le(BizPaymentRecord::getPaymentDate, snapDate));
            for (BizPaymentRecord p : payments) {
                paidByReceivable.merge(p.getRelatedId(),
                        p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO,
                        BigDecimal::add);
            }
        }

        List<Map<String, Object>> list = page.getRecords().stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            BigDecimal amount = r.getAmount() != null ? r.getAmount() : BigDecimal.ZERO;
            BigDecimal received, unreceived;
            String effStatus;
            if (snapDate != null) {
                received = paidByReceivable.getOrDefault(r.getId(), BigDecimal.ZERO);
                unreceived = amount.subtract(received).max(BigDecimal.ZERO);
                if (received.compareTo(BigDecimal.ZERO) <= 0) effStatus = "pending";
                else if (received.compareTo(amount) >= 0) effStatus = "received";
                else effStatus = "partial_received";
            } else {
                received = r.getReceivedAmount() != null ? r.getReceivedAmount() : BigDecimal.ZERO;
                unreceived = r.getUnreceivedAmount() != null ? r.getUnreceivedAmount() : BigDecimal.ZERO;
                effStatus = r.getStatus();
            }
            m.put("id", r.getId());
            m.put("receivableNo", r.getReceivableNo());
            m.put("orderId", r.getOrderId());
            m.put("orderNo", orderNoMap.getOrDefault(r.getOrderId(), ""));
            m.put("customerId", r.getCustomerId());
            m.put("customerName", cNameMap.getOrDefault(r.getCustomerId(), ""));
            m.put("amount", amount);
            m.put("receivedAmount", received);
            m.put("unreceivedAmount", unreceived);
            m.put("dueDate", r.getDueDate());
            m.put("status", effStatus);
            m.put("remark", r.getRemark());
            m.put("createTime", r.getCreateTime());
            return m;
        }).collect(Collectors.toList());

        // 快照模式下的 status 过滤 (在内存)
        if (snapDate != null && StringUtils.hasText(status)) {
            list = list.stream().filter(m -> status.equals(m.get("status"))).collect(Collectors.toList());
        }

        return new PageResult<>(list, page.getTotal(), pageNum, pageSize);
    }

    /**
     * 应收欠款按时间粒度(day/month/quarter/year)聚合导出。
     * 分组键基于 due_date (应收到期日), startDate/endDate 用于限定 due_date 范围。
     * 列: 期间 / 应收笔数 / 应收总额 / 已收总额 / 未收总额(欠款) / 回收率
     */
    public void exportReceivablesByPeriod(String granularity, String startDate, String endDate,
                                          jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        String g = granularity != null ? granularity.toLowerCase() : "month";
        if (!java.util.Set.of("day", "month", "quarter", "year").contains(g)) {
            g = "month";
        }
        LambdaQueryWrapper<BizReceivable> w = new LambdaQueryWrapper<BizReceivable>()
                .eq(BizReceivable::getDeleted, 0)
                .isNotNull(BizReceivable::getDueDate);
        if (StringUtils.hasText(startDate)) {
            w.ge(BizReceivable::getDueDate, LocalDate.parse(startDate));
        }
        if (StringUtils.hasText(endDate)) {
            w.le(BizReceivable::getDueDate, LocalDate.parse(endDate));
        }
        List<BizReceivable> all = receivableMapper.selectList(w);

        // 聚合: period -> {笔数, 应收, 已收, 未收}
        java.util.Map<String, BigDecimal[]> grouped = new java.util.TreeMap<>();
        for (BizReceivable r : all) {
            if (r.getDueDate() == null) continue;
            String key = receivablePeriodKey(r.getDueDate(), g);
            BigDecimal[] arr = grouped.computeIfAbsent(key,
                    k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            arr[0] = arr[0].add(BigDecimal.ONE);
            arr[1] = arr[1].add(r.getAmount() != null ? r.getAmount() : BigDecimal.ZERO);
            arr[2] = arr[2].add(r.getReceivedAmount() != null ? r.getReceivedAmount() : BigDecimal.ZERO);
            arr[3] = arr[3].add(r.getUnreceivedAmount() != null ? r.getUnreceivedAmount() : BigDecimal.ZERO);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal sumTotal = BigDecimal.ZERO, sumRecv = BigDecimal.ZERO, sumUnrecv = BigDecimal.ZERO;
        int sumCount = 0;
        for (var e : grouped.entrySet()) {
            BigDecimal[] arr = e.getValue();
            BigDecimal rate = arr[1].compareTo(BigDecimal.ZERO) > 0
                    ? arr[2].multiply(BigDecimal.valueOf(100)).divide(arr[1], 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("period", e.getKey());
            m.put("count", arr[0].intValue());
            m.put("totalAmount", arr[1]);
            m.put("receivedAmount", arr[2]);
            m.put("unreceivedAmount", arr[3]);
            m.put("rate", rate);
            rows.add(m);
            sumCount += arr[0].intValue();
            sumTotal = sumTotal.add(arr[1]);
            sumRecv = sumRecv.add(arr[2]);
            sumUnrecv = sumUnrecv.add(arr[3]);
        }
        // 末行加合计
        if (!rows.isEmpty()) {
            BigDecimal totalRate = sumTotal.compareTo(BigDecimal.ZERO) > 0
                    ? sumRecv.multiply(BigDecimal.valueOf(100)).divide(sumTotal, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            Map<String, Object> sumRow = new LinkedHashMap<>();
            sumRow.put("period", "合计");
            sumRow.put("count", sumCount);
            sumRow.put("totalAmount", sumTotal);
            sumRow.put("receivedAmount", sumRecv);
            sumRow.put("unreceivedAmount", sumUnrecv);
            sumRow.put("rate", totalRate);
            rows.add(sumRow);
        }

        String label = "按" + receivableGranularityLabel(g) + "汇总";
        List<String> headers = List.of("期间", "应收笔数", "应收总额", "已收总额", "未收总额(欠款)", "回收率(%)");
        List<java.util.function.Function<Map<String, Object>, Object>> ex = List.of(
                m -> m.get("period"),
                m -> m.get("count"),
                m -> m.get("totalAmount"),
                m -> m.get("receivedAmount"),
                m -> m.get("unreceivedAmount"),
                m -> m.get("rate")
        );
        com.aimes.common.utils.ExcelUtil.export(response, "应收欠款" + label, headers, rows, ex);
    }

    private String receivablePeriodKey(LocalDate d, String g) {
        switch (g) {
            case "day": return d.toString();
            case "quarter": return d.getYear() + "-Q" + ((d.getMonthValue() - 1) / 3 + 1);
            case "year": return String.valueOf(d.getYear());
            case "month":
            default: return d.getYear() + "-" + String.format("%02d", d.getMonthValue());
        }
    }

    private String receivableGranularityLabel(String g) {
        switch (g) {
            case "day": return "天";
            case "quarter": return "季";
            case "year": return "年";
            case "month":
            default: return "月";
        }
    }

    /**
     * 应付账款分页查询（支持拆分查询条件：应付编号、单据号、供应商名称）
     */
    public PageResult<Map<String, Object>> getPayablePage(String keyword, String payableNo, String orderNo, String supplierName,
                                                           String sourceType, String status, Long sourceId, int pageNum, int pageSize) {
        // 先按供应商名称反查ID集合
        Set<Long> matchSupplierIds = null;
        if (StringUtils.hasText(supplierName)) {
            matchSupplierIds = supplierMapper.selectList(
                    new LambdaQueryWrapper<BizSupplier>().like(BizSupplier::getSupplierName, supplierName))
                    .stream().map(BizSupplier::getId).collect(Collectors.toSet());
            if (matchSupplierIds.isEmpty()) return new PageResult<>(List.of(), 0, pageNum, pageSize);
        }

        // 按单据号反查sourceId集合（仅限purchase_order类型）
        Set<Long> matchSourceIds = null;
        if (StringUtils.hasText(orderNo)) {
            Set<Long> poMatchIds = new HashSet<>();
            try {
                var poMapper = applicationContext.getBean(com.aimes.modules.purchase.mapper.BizPurchaseOrderMapper.class);
                poMapper.selectList(new LambdaQueryWrapper<com.aimes.modules.purchase.entity.BizPurchaseOrder>()
                        .like(com.aimes.modules.purchase.entity.BizPurchaseOrder::getPoNo, orderNo))
                        .forEach(po -> poMatchIds.add(po.getId()));
            } catch (Exception ignored) {}
            try {
                var ooMapper = applicationContext.getBean(com.aimes.modules.outsource.mapper.BizOutsourceOrderMapper.class);
                ooMapper.selectList(new LambdaQueryWrapper<com.aimes.modules.outsource.entity.BizOutsourceOrder>()
                        .like(com.aimes.modules.outsource.entity.BizOutsourceOrder::getOrderNo, orderNo))
                        .forEach(oo -> poMatchIds.add(oo.getId()));
            } catch (Exception ignored) {}
            if (poMatchIds.isEmpty()) return new PageResult<>(List.of(), 0, pageNum, pageSize);
            matchSourceIds = poMatchIds;
        }

        LambdaQueryWrapper<BizPayable> wrapper = new LambdaQueryWrapper<BizPayable>()
                .like(StringUtils.hasText(keyword), BizPayable::getPayableNo, keyword)
                .like(StringUtils.hasText(payableNo), BizPayable::getPayableNo, payableNo)
                .eq(StringUtils.hasText(sourceType), BizPayable::getSourceType, sourceType)
                .eq(StringUtils.hasText(status), BizPayable::getStatus, status)
                .eq(sourceId != null, BizPayable::getSourceId, sourceId)
                .in(matchSupplierIds != null, BizPayable::getSupplierId, matchSupplierIds)
                .in(matchSourceIds != null, BizPayable::getSourceId, matchSourceIds)
                .orderByDesc(BizPayable::getCreateTime);

        Page<BizPayable> page = payableMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        // 批量加载供应商名称
        Set<Long> sids = page.getRecords().stream().map(BizPayable::getSupplierId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> sNameMap = sids.isEmpty() ? Map.of() :
                supplierMapper.selectBatchIds(sids).stream().collect(Collectors.toMap(BizSupplier::getId, BizSupplier::getSupplierName, (a, b) -> a));

        // 批量加载关联单号（采购单/外协单）
        Map<Long, String> poNoMap = new HashMap<>();
        Map<Long, String> ooNoMap = new HashMap<>();
        Set<Long> poIds = page.getRecords().stream()
                .filter(p -> "purchase_order".equals(p.getSourceType()) && p.getSourceId() != null)
                .map(BizPayable::getSourceId).collect(Collectors.toSet());
        Set<Long> ooIds = page.getRecords().stream()
                .filter(p -> "outsource_order".equals(p.getSourceType()) && p.getSourceId() != null)
                .map(BizPayable::getSourceId).collect(Collectors.toSet());
        if (!poIds.isEmpty()) {
            try {
                var poMapper = applicationContext.getBean(com.aimes.modules.purchase.mapper.BizPurchaseOrderMapper.class);
                poMapper.selectBatchIds(poIds).forEach(po -> poNoMap.put(po.getId(), po.getPoNo()));
            } catch (Exception ignored) {}
        }
        if (!ooIds.isEmpty()) {
            try {
                var ooMapper = applicationContext.getBean(com.aimes.modules.outsource.mapper.BizOutsourceOrderMapper.class);
                ooMapper.selectBatchIds(ooIds).forEach(oo -> ooNoMap.put(oo.getId(), oo.getOrderNo()));
            } catch (Exception ignored) {}
        }

        List<Map<String, Object>> list = page.getRecords().stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("payableNo", p.getPayableNo());
            m.put("supplierId", p.getSupplierId());
            m.put("supplierName", sNameMap.getOrDefault(p.getSupplierId(), ""));
            m.put("amount", p.getAmount());
            m.put("paidAmount", p.getPaidAmount());
            m.put("unpaidAmount", p.getUnpaidAmount());
            m.put("sourceType", p.getSourceType());
            m.put("sourceId", p.getSourceId());
            // 关联单号
            if ("purchase_order".equals(p.getSourceType())) {
                m.put("sourceNo", poNoMap.getOrDefault(p.getSourceId(), ""));
            } else if ("outsource_order".equals(p.getSourceType())) {
                m.put("sourceNo", ooNoMap.getOrDefault(p.getSourceId(), ""));
            } else {
                m.put("sourceNo", "");
            }
            m.put("dueDate", p.getDueDate());
            m.put("status", p.getStatus());
            m.put("remark", p.getRemark());
            m.put("createTime", p.getCreateTime());
            return m;
        }).collect(Collectors.toList());

        return new PageResult<>(list, page.getTotal(), pageNum, pageSize);
    }

    /**
     * 创建应收账款（自动生成编号，并生成销售收入凭证）
     * 借:应收账款(1122) 贷:主营业务收入(6001)
     */
    @Transactional
    public void createReceivable(BizReceivable receivable) {
        receivable.setReceivableNo(CodeUtil.generate("AR"));
        if (receivable.getStatus() == null) receivable.setStatus("pending");
        if (receivable.getReceivedAmount() == null) receivable.setReceivedAmount(BigDecimal.ZERO);
        receivable.setUnreceivedAmount(receivable.getAmount().subtract(receivable.getReceivedAmount()));
        receivableMapper.insert(receivable);
        // 自动凭证: 借:应收账款(1122) 贷:主营业务收入(6001)
        tryCreateAutoVoucher("receipt", LocalDate.now(),
                "销售应收-" + receivable.getReceivableNo(), receivable.getAmount(),
                "1122", "6001",
                "receivable", receivable.getId());
    }

    /**
     * 创建应付账款（自动生成编号，并生成采购成本凭证）
     * 借:原材料(1401) 贷:应付账款(2202)
     */
    @Transactional
    public void createPayable(BizPayable payable) {
        payable.setPayableNo(CodeUtil.generate("AP"));
        if (payable.getStatus() == null) payable.setStatus("pending");
        if (payable.getPaidAmount() == null) payable.setPaidAmount(BigDecimal.ZERO);
        payable.setUnpaidAmount(payable.getAmount().subtract(payable.getPaidAmount()));
        payableMapper.insert(payable);
        // 自动凭证: 借:原材料(1401) 贷:应付账款(2202)
        tryCreateAutoVoucher("transfer", LocalDate.now(),
                "采购应付-" + payable.getPayableNo(), payable.getAmount(),
                "1401", "2202",
                "payable", payable.getId());
    }

    /**
     * 添加收付款记录（自动生成编号，并更新关联的应收/应付金额，同时自动生成会计凭证）
     */
    @Transactional
    public void addPayment(BizPaymentRecord payment) {
        payment.setPaymentNo(CodeUtil.generate("PM"));
        paymentRecordMapper.insert(payment);

        // 更新关联的应收或应付金额
        if ("receivable".equals(payment.getRelatedType())) {
            BizReceivable receivable = receivableMapper.selectById(payment.getRelatedId());
            if (receivable == null) throw new BusinessException("应收账款不存在");
            receivable.setReceivedAmount(receivable.getReceivedAmount().add(payment.getAmount()));
            receivable.setUnreceivedAmount(receivable.getAmount().subtract(receivable.getReceivedAmount()));
            if (receivable.getUnreceivedAmount().compareTo(BigDecimal.ZERO) <= 0) {
                receivable.setStatus("received");
                receivable.setUnreceivedAmount(BigDecimal.ZERO);
            } else {
                receivable.setStatus("partial_received");
            }
            receivableMapper.updateById(receivable);

            // 同步更新销售订单已回款金额
            if (receivable.getOrderId() != null) {
                BizSalesOrder order = salesOrderMapper.selectById(receivable.getOrderId());
                if (order != null) {
                    BigDecimal currentPayment = order.getPaymentAmount() != null ? order.getPaymentAmount() : BigDecimal.ZERO;
                    order.setPaymentAmount(currentPayment.add(payment.getAmount()));
                    salesOrderMapper.updateById(order);
                }
            }

            // 自动凭证: 借:银行存款(1002) 贷:应收账款(1122)
            tryCreateAutoVoucher("receipt", LocalDate.now(),
                    "收款-" + payment.getPaymentNo(), payment.getAmount(),
                    "1002", "1122",
                    "receivable_payment", payment.getId());
        } else if ("payable".equals(payment.getRelatedType())) {
            BizPayable payable = payableMapper.selectById(payment.getRelatedId());
            if (payable == null) throw new BusinessException("应付账款不存在");
            payable.setPaidAmount(payable.getPaidAmount().add(payment.getAmount()));
            payable.setUnpaidAmount(payable.getAmount().subtract(payable.getPaidAmount()));
            if (payable.getUnpaidAmount().compareTo(BigDecimal.ZERO) <= 0) {
                payable.setStatus("paid");
                payable.setUnpaidAmount(BigDecimal.ZERO);
            } else {
                payable.setStatus("partial_paid");
            }
            payableMapper.updateById(payable);

            // 同步更新采购订单已付款金额
            if ("purchase_order".equals(payable.getSourceType()) && payable.getSourceId() != null) {
                try {
                    var poMapper = applicationContext.getBean(
                            com.aimes.modules.purchase.mapper.BizPurchaseOrderMapper.class);
                    var po = poMapper.selectById(payable.getSourceId());
                    if (po != null) {
                        BigDecimal currentPaid = po.getPaidAmount() != null ? po.getPaidAmount() : BigDecimal.ZERO;
                        po.setPaidAmount(currentPaid.add(payment.getAmount()));
                        poMapper.updateById(po);
                    }
                } catch (Exception ignored) {}
            }

            // 自动凭证: 借:应付账款(2202) 贷:银行存款(1002)
            tryCreateAutoVoucher("payment", LocalDate.now(),
                    "付款-" + payment.getPaymentNo(), payment.getAmount(),
                    "2202", "1002",
                    "payable_payment", payment.getId());
        }
    }

    /**
     * 获取工单成本（不存在则创建）
     */
    public BizWoCost getWoCost(Long workOrderId) {
        BizWoCost cost = woCostMapper.selectOne(
                new LambdaQueryWrapper<BizWoCost>()
                        .eq(BizWoCost::getWorkOrderId, workOrderId));

        if (cost == null) {
            cost = new BizWoCost();
            cost.setWorkOrderId(workOrderId);
            cost.setMaterialCost(BigDecimal.ZERO);
            cost.setLaborCost(BigDecimal.ZERO);
            cost.setOutsourceCost(BigDecimal.ZERO);
            cost.setReworkCost(BigDecimal.ZERO);
            cost.setScrapLoss(BigDecimal.ZERO);
            cost.setOverhead(BigDecimal.ZERO);
            cost.setTotalCost(BigDecimal.ZERO);
            woCostMapper.insert(cost);
        }
        return cost;
    }

    /**
     * 更新工单成本
     */
    public void updateWoCost(BizWoCost cost) {
        cost.setTotalCost(cost.getMaterialCost().add(cost.getLaborCost())
                .add(cost.getOutsourceCost()).add(cost.getReworkCost())
                .add(cost.getScrapLoss()).add(cost.getOverhead()));
        woCostMapper.updateById(cost);
    }

    /**
     * 工单成本分页查询（含工单编号、产品名、计划数量）
     */
    public PageResult<Map<String, Object>> getWoCostPage(int pageNum, int pageSize) {
        Page<BizWoCost> page = woCostMapper.selectPage(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<BizWoCost>().orderByDesc(BizWoCost::getCreateTime));

        // 批量加载关联工单
        Set<Long> woIds = page.getRecords().stream()
                .map(BizWoCost::getWorkOrderId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, BizWorkOrder> woMap = woIds.isEmpty() ? Map.of() :
                workOrderMapper.selectBatchIds(woIds).stream()
                        .collect(Collectors.toMap(BizWorkOrder::getId, w -> w, (a, b) -> a));

        // 批量加载产品名称
        Set<Long> productIds = woMap.values().stream()
                .map(BizWorkOrder::getProductId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> productNameMap = productIds.isEmpty() ? Map.of() :
                productMapper.selectBatchIds(productIds).stream()
                        .collect(Collectors.toMap(BizProduct::getId, BizProduct::getProductName, (a, b) -> a));

        List<Map<String, Object>> list = page.getRecords().stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("workOrderId", c.getWorkOrderId());
            BizWorkOrder wo = woMap.get(c.getWorkOrderId());
            m.put("workOrderNo", wo != null ? wo.getWoNo() : "");
            m.put("productName", wo != null && wo.getProductId() != null
                    ? productNameMap.getOrDefault(wo.getProductId(), "") : "");
            m.put("planQty", wo != null ? wo.getPlanQty() : null);
            m.put("materialCost", c.getMaterialCost());
            m.put("laborCost", c.getLaborCost());
            m.put("outsourceCost", c.getOutsourceCost());
            m.put("reworkCost", c.getReworkCost());
            m.put("scrapLoss", c.getScrapLoss());
            m.put("overhead", c.getOverhead());
            m.put("totalCost", c.getTotalCost());
            m.put("createTime", c.getCreateTime());
            return m;
        }).collect(Collectors.toList());

        return new PageResult<>(list, page.getTotal(), pageNum, pageSize);
    }

    /**
     * 收付款记录分页查询（含关联单号、客户/供应商名、操作人）
     */
    public PageResult<Map<String, Object>> getPaymentPage(String keyword, String relatedType, int pageNum, int pageSize) {
        LambdaQueryWrapper<BizPaymentRecord> wrapper = new LambdaQueryWrapper<BizPaymentRecord>()
                .like(StringUtils.hasText(keyword), BizPaymentRecord::getPaymentNo, keyword)
                .eq(StringUtils.hasText(relatedType), BizPaymentRecord::getRelatedType, relatedType)
                .orderByDesc(BizPaymentRecord::getCreateTime);
        Page<BizPaymentRecord> page = paymentRecordMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        // 分组收集 receivable / payable 的 relatedId
        Set<Long> receivableIds = new HashSet<>();
        Set<Long> payableIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        for (BizPaymentRecord r : page.getRecords()) {
            if ("receivable".equals(r.getRelatedType()) && r.getRelatedId() != null) {
                receivableIds.add(r.getRelatedId());
            } else if ("payable".equals(r.getRelatedType()) && r.getRelatedId() != null) {
                payableIds.add(r.getRelatedId());
            }
            if (r.getCreateBy() != null) {
                userIds.add(r.getCreateBy());
            }
        }

        // 批量加载应收 → 客户名
        Map<Long, BizReceivable> receivableMap = receivableIds.isEmpty() ? Map.of() :
                receivableMapper.selectBatchIds(receivableIds).stream()
                        .collect(Collectors.toMap(BizReceivable::getId, x -> x, (a, b) -> a));
        Set<Long> customerIds = receivableMap.values().stream()
                .map(BizReceivable::getCustomerId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> customerNameMap = customerIds.isEmpty() ? Map.of() :
                customerMapper.selectBatchIds(customerIds).stream()
                        .collect(Collectors.toMap(BizCustomer::getId, BizCustomer::getCustomerName, (a, b) -> a));

        // 批量加载应付 → 供应商名
        Map<Long, BizPayable> payableMap = payableIds.isEmpty() ? Map.of() :
                payableMapper.selectBatchIds(payableIds).stream()
                        .collect(Collectors.toMap(BizPayable::getId, x -> x, (a, b) -> a));
        Set<Long> supplierIds = payableMap.values().stream()
                .map(BizPayable::getSupplierId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> supplierNameMap = supplierIds.isEmpty() ? Map.of() :
                supplierMapper.selectBatchIds(supplierIds).stream()
                        .collect(Collectors.toMap(BizSupplier::getId, BizSupplier::getSupplierName, (a, b) -> a));

        // 批量加载操作人名称
        Map<Long, String> userNameMap = userIds.isEmpty() ? Map.of() :
                userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, SysUser::getRealName, (a, b) -> a));

        List<Map<String, Object>> list = page.getRecords().stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("paymentNo", r.getPaymentNo());
            m.put("relatedType", r.getRelatedType());
            m.put("relatedId", r.getRelatedId());
            m.put("amount", r.getAmount());
            m.put("paymentDate", r.getPaymentDate());
            m.put("paymentMethod", r.getPaymentMethod());
            m.put("transactionNo", r.getTransactionNo());
            m.put("attachmentUrl", r.getAttachmentUrl());
            m.put("remark", r.getRemark());
            m.put("operatorName", r.getCreateBy() != null ? userNameMap.get(r.getCreateBy()) : "");
            m.put("createTime", r.getCreateTime());

            if ("receivable".equals(r.getRelatedType())) {
                BizReceivable recv = receivableMap.get(r.getRelatedId());
                m.put("receivableNo", recv != null ? recv.getReceivableNo() : "");
                m.put("customerName", recv != null && recv.getCustomerId() != null
                        ? customerNameMap.getOrDefault(recv.getCustomerId(), "") : "");
            } else if ("payable".equals(r.getRelatedType())) {
                BizPayable pay = payableMap.get(r.getRelatedId());
                m.put("payableNo", pay != null ? pay.getPayableNo() : "");
                m.put("supplierName", pay != null && pay.getSupplierId() != null
                        ? supplierNameMap.getOrDefault(pay.getSupplierId(), "") : "");
            }
            return m;
        }).collect(Collectors.toList());

        return new PageResult<>(list, page.getTotal(), pageNum, pageSize);
    }

    /**
     * 月度工资分页查询
     */
    public PageResult<Map<String, Object>> getWagePage(String month, String keyword, String status, int pageNum, int pageSize) {
        LambdaQueryWrapper<BizWageMonthly> wrapper = new LambdaQueryWrapper<BizWageMonthly>()
                .eq(StringUtils.hasText(month), BizWageMonthly::getMonth, month)
                .eq(StringUtils.hasText(status), BizWageMonthly::getStatus, status)
                .orderByDesc(BizWageMonthly::getCreateTime);

        Page<BizWageMonthly> page = wageMonthlyMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        // 批量加载员工名称
        Set<Long> uids = page.getRecords().stream().map(BizWageMonthly::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> uNameMap = uids.isEmpty() ? Map.of() :
                userMapper.selectBatchIds(uids).stream().collect(Collectors.toMap(SysUser::getId, SysUser::getRealName, (a, b) -> a));

        List<Map<String, Object>> list = page.getRecords().stream().map(w -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", w.getId());
            m.put("userId", w.getUserId());
            m.put("employeeName", uNameMap.getOrDefault(w.getUserId(), ""));
            m.put("month", w.getMonth());
            m.put("personalPieceWage", w.getPersonalPieceWage());
            m.put("teamAllocatedWage", w.getTeamAllocatedWage());
            m.put("overtimeWage", w.getOvertimeWage());
            m.put("bonus", w.getBonus());
            m.put("deduction", w.getDeduction());
            m.put("totalWage", w.getTotalWage());
            m.put("status", w.getStatus());
            m.put("confirmedBy", w.getConfirmedBy());
            m.put("confirmedAt", w.getConfirmedAt());
            m.put("approvedBy", w.getApprovedBy());
            m.put("approvedAt", w.getApprovedAt());
            m.put("remark", w.getRemark());
            m.put("createTime", w.getCreateTime());
            return m;
        }).collect(Collectors.toList());

        return new PageResult<>(list, page.getTotal(), pageNum, pageSize);
    }

    /**
     * 创建月度工资记录
     */
    public void createWage(BizWageMonthly wage) {
        wage.setStatus("DRAFT");
        // 自动计算总工资
        BigDecimal total = BigDecimal.ZERO;
        if (wage.getPersonalPieceWage() != null) total = total.add(wage.getPersonalPieceWage());
        if (wage.getTeamAllocatedWage() != null) total = total.add(wage.getTeamAllocatedWage());
        if (wage.getOvertimeWage() != null) total = total.add(wage.getOvertimeWage());
        if (wage.getBonus() != null) total = total.add(wage.getBonus());
        if (wage.getDeduction() != null) total = total.subtract(wage.getDeduction());
        wage.setTotalWage(total);
        wageMonthlyMapper.insert(wage);
    }

    /**
     * 确认工资: DRAFT → CONFIRMED
     */
    public void confirmWage(Long id, Long operatorId) {
        BizWageMonthly wage = wageMonthlyMapper.selectById(id);
        if (wage == null) throw new BusinessException("工资记录不存在");
        if (!"DRAFT".equals(wage.getStatus())) throw new BusinessException("只有草稿状态可以确认");
        wage.setStatus("CONFIRMED");
        wage.setConfirmedBy(operatorId);
        wage.setConfirmedAt(LocalDateTime.now());
        wageMonthlyMapper.updateById(wage);
    }

    /**
     * 审批工资: CONFIRMED → APPROVED，同时生成工资计提凭证
     * 借:管理费用(6602) 贷:应付职工薪酬(2211)
     */
    @Transactional
    public void approveWage(Long id, Long operatorId) {
        BizWageMonthly wage = wageMonthlyMapper.selectById(id);
        if (wage == null) throw new BusinessException("工资记录不存在");
        if (!"CONFIRMED".equals(wage.getStatus())) throw new BusinessException("只有已确认状态可以审批");
        wage.setStatus("APPROVED");
        wage.setApprovedBy(operatorId);
        wage.setApprovedAt(LocalDateTime.now());
        wageMonthlyMapper.updateById(wage);
        // 自动凭证: 借:管理费用(6602) 贷:应付职工薪酬(2211)
        if (wage.getTotalWage() != null && wage.getTotalWage().compareTo(BigDecimal.ZERO) > 0) {
            tryCreateAutoVoucher("transfer", LocalDate.now(),
                    "工资计提-" + wage.getMonth(), wage.getTotalWage(),
                    "6602", "2211",
                    "wage_approve", wage.getId());
        }
    }

    /**
     * 发放工资: APPROVED → PAID，同时生成工资发放凭证
     * 借:应付职工薪酬(2211) 贷:银行存款(1002)
     */
    @Transactional
    public void payWage(Long id) {
        BizWageMonthly wage = wageMonthlyMapper.selectById(id);
        if (wage == null) throw new BusinessException("工资记录不存在");
        if (!"APPROVED".equals(wage.getStatus())) throw new BusinessException("只有已审批状态可以发放");
        wage.setStatus("PAID");
        wageMonthlyMapper.updateById(wage);
        // 自动凭证: 借:应付职工薪酬(2211) 贷:银行存款(1002)
        if (wage.getTotalWage() != null && wage.getTotalWage().compareTo(BigDecimal.ZERO) > 0) {
            tryCreateAutoVoucher("payment", LocalDate.now(),
                    "工资发放-" + wage.getMonth(), wage.getTotalWage(),
                    "2211", "1002",
                    "wage_pay", wage.getId());
        }
    }

    /**
     * 退回工资: CONFIRMED → DRAFT
     */
    public void rejectWage(Long id) {
        BizWageMonthly wage = wageMonthlyMapper.selectById(id);
        if (wage == null) throw new BusinessException("工资记录不存在");
        if (!"CONFIRMED".equals(wage.getStatus()) && !"APPROVED".equals(wage.getStatus())) {
            throw new BusinessException("当前状态不可退回");
        }
        wage.setStatus("DRAFT");
        wage.setConfirmedBy(null);
        wage.setConfirmedAt(null);
        wage.setApprovedBy(null);
        wage.setApprovedAt(null);
        wageMonthlyMapper.updateById(wage);
    }

    /**
     * 工资详情
     */
    public BizWageMonthly getWageDetail(Long id) {
        return wageMonthlyMapper.selectById(id);
    }

    /**
     * 生成月度工资：按月汇总报工记录生成每人计件工资草稿
     * 规则：
     *  - 按 report.createTime 落在 month (YYYY-MM) 的工作报表
     *  - 展开到实际工作人员（班组报工/班组指派展开到成员）
     *  - personalPieceWage: 个人报工累加
     *  - teamAllocatedWage: 班组分配累加
     *  - 已存在当月记录且状态非 DRAFT 的跳过，DRAFT 的覆盖更新
     */
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void generateMonthlyWages(String month) {
        if (!StringUtils.hasText(month)) throw new BusinessException("月份不能为空");
        java.time.YearMonth ym;
        try { ym = java.time.YearMonth.parse(month); }
        catch (Exception e) { throw new BusinessException("月份格式错误,应为 YYYY-MM"); }

        String startDate = ym.atDay(1).toString();
        String endDate = ym.atEndOfMonth().toString();

        // 复用报工分析逻辑获取全部展开后的报工记录
        PageResult<Map<String, Object>> all = getReportAnalysisList(
                startDate, endDate, null, null, null, 1, Integer.MAX_VALUE);
        List<Map<String, Object>> rows = all.getRecords();

        // 按用户名聚合（复用 reporterName 反查 userId）
        var userMapper = applicationContext.getBean(com.aimes.modules.system.mapper.SysUserMapper.class);
        Map<String, Long> nameToId = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getDeleted, 0))
                .stream()
                .collect(Collectors.toMap(
                        u -> u.getRealName() != null ? u.getRealName() : u.getUsername(),
                        SysUser::getId, (a, b) -> a));

        // 数据结构: userId -> [personalWage, teamWage, totalQty, workDaySet]
        Map<Long, BigDecimal[]> wageAgg = new LinkedHashMap<>();
        Map<Long, Set<String>> workDaysMap = new LinkedHashMap<>();

        for (var row : rows) {
            String name = (String) row.get("reporterName");
            Long uid = nameToId.get(name);
            if (uid == null) continue;

            BigDecimal amount = row.get("amount") instanceof BigDecimal ? (BigDecimal) row.get("amount") : BigDecimal.ZERO;
            BigDecimal qty = row.get("qualifiedQty") instanceof BigDecimal ? (BigDecimal) row.get("qualifiedQty") : BigDecimal.ZERO;
            String reportType = (String) row.get("reportType");

            BigDecimal[] agg = wageAgg.computeIfAbsent(uid, k -> new BigDecimal[]{ BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO });
            if ("team".equals(reportType)) {
                agg[1] = agg[1].add(amount); // 班组分配
            } else {
                agg[0] = agg[0].add(amount); // 个人计件
            }
            agg[2] = agg[2].add(qty);

            // 统计出勤日
            Object t = row.get("reportTime");
            if (t instanceof java.time.LocalDateTime) {
                String day = ((java.time.LocalDateTime) t).toLocalDate().toString();
                workDaysMap.computeIfAbsent(uid, k -> new HashSet<>()).add(day);
            }
        }

        int created = 0;
        int updated = 0;
        for (var entry : wageAgg.entrySet()) {
            Long uid = entry.getKey();
            BigDecimal[] agg = entry.getValue();
            BigDecimal personal = agg[0];
            BigDecimal teamAlloc = agg[1];
            BigDecimal totalQty = agg[2];
            int workDays = workDaysMap.getOrDefault(uid, Set.of()).size();

            // 查已有记录
            BizWageMonthly existing = wageMonthlyMapper.selectOne(new LambdaQueryWrapper<BizWageMonthly>()
                    .eq(BizWageMonthly::getUserId, uid)
                    .eq(BizWageMonthly::getMonth, month)
                    .eq(BizWageMonthly::getDeleted, 0));

            if (existing != null && !"DRAFT".equals(existing.getStatus())) {
                continue; // 已确认/审批/发放的不动
            }

            BizWageMonthly wage = existing != null ? existing : new BizWageMonthly();
            wage.setUserId(uid);
            wage.setMonth(month);
            wage.setPersonalPieceWage(personal);
            wage.setTeamAllocatedWage(teamAlloc);
            if (wage.getOvertimeWage() == null) wage.setOvertimeWage(BigDecimal.ZERO);
            if (wage.getBonus() == null) wage.setBonus(BigDecimal.ZERO);
            if (wage.getDeduction() == null) wage.setDeduction(BigDecimal.ZERO);
            BigDecimal total = personal.add(teamAlloc)
                    .add(wage.getOvertimeWage())
                    .add(wage.getBonus())
                    .subtract(wage.getDeduction());
            wage.setTotalWage(total);
            wage.setTotalQty(totalQty);
            wage.setWorkDays(workDays);
            wage.setStatus("DRAFT");

            if (existing == null) {
                wageMonthlyMapper.insert(wage);
                created++;
            } else {
                wageMonthlyMapper.updateById(wage);
                updated++;
            }
        }
        log.info("生成月度工资完成: month={}, 新建={}, 更新={}", month, created, updated);
    }

    /**
     * 导出工资列表
     */
    public List<BizWageMonthly> exportWages(String month, String status) {
        LambdaQueryWrapper<BizWageMonthly> wrapper = new LambdaQueryWrapper<BizWageMonthly>()
                .eq(StringUtils.hasText(month), BizWageMonthly::getMonth, month)
                .eq(StringUtils.hasText(status), BizWageMonthly::getStatus, status)
                .orderByDesc(BizWageMonthly::getCreateTime);
        return wageMonthlyMapper.selectList(wrapper);
    }

    /**
     * 订单利润分页列表
     */
    public Map<String, Object> getOrderProfitPage(int pageNum, int pageSize) {
        Map<String, Object> result = new LinkedHashMap<>();
        Page<BizSalesOrder> page = salesOrderMapper.selectPage(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<BizSalesOrder>()
                        .notIn(BizSalesOrder::getStatus, "draft", "cancelled")
                        .orderByDesc(BizSalesOrder::getCreateTime));

        // 批量加载客户名称
        Set<Long> customerIds = page.getRecords().stream()
                .map(BizSalesOrder::getCustomerId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> customerNameMap = customerIds.isEmpty() ? Map.of() :
                customerMapper.selectBatchIds(customerIds).stream()
                        .collect(Collectors.toMap(BizCustomer::getId, BizCustomer::getCustomerName, (a, b) -> a));

        List<Map<String, Object>> records = new ArrayList<>();
        for (BizSalesOrder order : page.getRecords()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("orderId", order.getId());
            item.put("orderNo", order.getOrderNo());
            item.put("customerId", order.getCustomerId());
            item.put("customerName", customerNameMap.getOrDefault(order.getCustomerId(), ""));
            BigDecimal revenue = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;
            item.put("revenue", revenue);

            // 计算成本
            List<BizWorkOrder> wos = workOrderMapper.selectList(
                    new LambdaQueryWrapper<BizWorkOrder>().eq(BizWorkOrder::getOrderId, order.getId()));
            BigDecimal totalCost = BigDecimal.ZERO;
            for (BizWorkOrder wo : wos) {
                BizWoCost cost = woCostMapper.selectOne(
                        new LambdaQueryWrapper<BizWoCost>().eq(BizWoCost::getWorkOrderId, wo.getId()));
                if (cost != null) totalCost = totalCost.add(cost.getTotalCost());
            }
            item.put("totalCost", totalCost);
            BigDecimal profit = revenue.subtract(totalCost);
            item.put("profit", profit);
            BigDecimal marginRate = revenue.compareTo(BigDecimal.ZERO) > 0
                    ? profit.multiply(BigDecimal.valueOf(100)).divide(revenue, 2, java.math.RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            item.put("marginRate", marginRate);
            item.put("status", order.getStatus());
            item.put("createTime", order.getCreateTime());
            records.add(item);
        }
        result.put("records", records);
        result.put("total", page.getTotal());
        return result;
    }

    /**
     * 订单级利润分析：订单金额 - 关联工单总成本
     */
    public Map<String, Object> getOrderProfit(Long orderId) {
        Map<String, Object> result = new LinkedHashMap<>();
        BizSalesOrder order = salesOrderMapper.selectById(orderId);
        if (order == null) throw new BusinessException("订单不存在");

        BigDecimal revenue = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;
        result.put("orderId", orderId);
        result.put("orderNo", order.getOrderNo());
        result.put("revenue", revenue);

        // 汇总关联工单成本
        List<BizWorkOrder> workOrders = workOrderMapper.selectList(
                new LambdaQueryWrapper<BizWorkOrder>().eq(BizWorkOrder::getOrderId, orderId));
        BigDecimal totalCost = BigDecimal.ZERO;
        List<Map<String, Object>> woCosts = new ArrayList<>();
        for (BizWorkOrder wo : workOrders) {
            BizWoCost cost = woCostMapper.selectOne(
                    new LambdaQueryWrapper<BizWoCost>().eq(BizWoCost::getWorkOrderId, wo.getId()));
            Map<String, Object> costItem = new LinkedHashMap<>();
            costItem.put("woNo", wo.getWoNo());
            costItem.put("workOrderId", wo.getId());
            if (cost != null) {
                costItem.put("materialCost", cost.getMaterialCost());
                costItem.put("laborCost", cost.getLaborCost());
                costItem.put("outsourceCost", cost.getOutsourceCost());
                costItem.put("totalCost", cost.getTotalCost());
                totalCost = totalCost.add(cost.getTotalCost());
            } else {
                costItem.put("totalCost", BigDecimal.ZERO);
            }
            woCosts.add(costItem);
        }

        BigDecimal profit = revenue.subtract(totalCost);
        BigDecimal marginRate = revenue.compareTo(BigDecimal.ZERO) > 0
                ? profit.multiply(BigDecimal.valueOf(100)).divide(revenue, 2, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        result.put("totalCost", totalCost);
        result.put("profit", profit);
        result.put("marginRate", marginRate);
        result.put("isLowMargin", marginRate.compareTo(BigDecimal.TEN) < 0);
        result.put("workOrderCosts", woCosts);

        // 应收信息
        List<BizReceivable> receivables = receivableMapper.selectList(
                new LambdaQueryWrapper<BizReceivable>().eq(BizReceivable::getOrderId, orderId));
        BigDecimal totalReceivable = BigDecimal.ZERO;
        BigDecimal totalReceived = BigDecimal.ZERO;
        for (BizReceivable r : receivables) {
            totalReceivable = totalReceivable.add(r.getAmount());
            totalReceived = totalReceived.add(r.getReceivedAmount() != null ? r.getReceivedAmount() : BigDecimal.ZERO);
        }
        result.put("totalReceivable", totalReceivable);
        result.put("totalReceived", totalReceived);
        result.put("receivableCount", receivables.size());

        return result;
    }

    /**
     * 多维利润分析：按客户/产品/月份维度
     */
    public Map<String, Object> getProfitAnalysis(String dimension, String startDate, String endDate) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dimension", dimension);

        LocalDate start = StringUtils.hasText(startDate) ? LocalDate.parse(startDate) : LocalDate.now().minusMonths(3);
        LocalDate end = StringUtils.hasText(endDate) ? LocalDate.parse(endDate) : LocalDate.now();
        result.put("startDate", start.toString());
        result.put("endDate", end.toString());

        // 查询时间范围内已确认的订单
        List<BizSalesOrder> orders = salesOrderMapper.selectList(
                new LambdaQueryWrapper<BizSalesOrder>()
                        .ge(BizSalesOrder::getCreateTime, start.atStartOfDay())
                        .le(BizSalesOrder::getCreateTime, end.atTime(23, 59, 59))
                        .notIn(BizSalesOrder::getStatus, "draft", "cancelled"));

        // 批量加载客户名称（用于customer维度）
        Set<Long> allCids = orders.stream().map(BizSalesOrder::getCustomerId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> allCNameMap = allCids.isEmpty() ? Map.of() :
                customerMapper.selectBatchIds(allCids).stream().collect(Collectors.toMap(BizCustomer::getId, BizCustomer::getCustomerName, (a, b) -> a));

        // 按维度分组
        Map<String, BigDecimal> revenueByDim = new LinkedHashMap<>();
        Map<String, BigDecimal> costByDim = new LinkedHashMap<>();
        Map<String, Integer> countByDim = new LinkedHashMap<>();

        for (BizSalesOrder order : orders) {
            String key;
            switch (dimension != null ? dimension : "month") {
                case "customer":
                    key = order.getCustomerId() != null ? allCNameMap.getOrDefault(order.getCustomerId(), "客户" + order.getCustomerId()) : "未知客户";
                    break;
                case "product":
                    // 按订单归类，每个订单算一个产品组合
                    key = order.getOrderNo();
                    break;
                default: // month
                    key = order.getCreateTime().toLocalDate().withDayOfMonth(1).toString();
                    break;
            }

            BigDecimal revenue = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;
            revenueByDim.merge(key, revenue, BigDecimal::add);
            countByDim.merge(key, 1, Integer::sum);

            // 计算该订单的成本
            List<BizWorkOrder> wos = workOrderMapper.selectList(
                    new LambdaQueryWrapper<BizWorkOrder>().eq(BizWorkOrder::getOrderId, order.getId()));
            BigDecimal orderCost = BigDecimal.ZERO;
            for (BizWorkOrder wo : wos) {
                BizWoCost cost = woCostMapper.selectOne(
                        new LambdaQueryWrapper<BizWoCost>().eq(BizWoCost::getWorkOrderId, wo.getId()));
                if (cost != null) {
                    orderCost = orderCost.add(cost.getTotalCost());
                }
            }
            costByDim.merge(key, orderCost, BigDecimal::add);
        }

        // 构建结果列表
        List<Map<String, Object>> items = new ArrayList<>();
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalCostAll = BigDecimal.ZERO;
        for (String key : revenueByDim.keySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", key);
            BigDecimal rev = revenueByDim.get(key);
            BigDecimal cost = costByDim.getOrDefault(key, BigDecimal.ZERO);
            BigDecimal profit = rev.subtract(cost);
            BigDecimal rate = rev.compareTo(BigDecimal.ZERO) > 0
                    ? profit.multiply(BigDecimal.valueOf(100)).divide(rev, 2, java.math.RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            item.put("orderCount", countByDim.getOrDefault(key, 0));
            item.put("revenue", rev);
            item.put("cost", cost);
            item.put("profit", profit);
            item.put("marginRate", rate);
            item.put("isLowMargin", rate.compareTo(BigDecimal.TEN) < 0);
            items.add(item);
            totalRevenue = totalRevenue.add(rev);
            totalCostAll = totalCostAll.add(cost);
        }

        result.put("items", items);
        result.put("totalRevenue", totalRevenue);
        result.put("totalCost", totalCostAll);
        BigDecimal totalProfit = totalRevenue.subtract(totalCostAll);
        result.put("totalProfit", totalProfit);
        BigDecimal totalMarginRate = totalRevenue.compareTo(BigDecimal.ZERO) > 0
                ? totalProfit.multiply(BigDecimal.valueOf(100)).divide(totalRevenue, 2, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        result.put("totalMarginRate", totalMarginRate);
        // aliases for profit/index.vue
        result.put("grossProfit", totalProfit);
        result.put("profitMargin", totalMarginRate.toPlainString());

        // monthlyTrend: always aggregate by month regardless of dimension
        Map<String, BigDecimal> monthRevenue = new LinkedHashMap<>();
        Map<String, BigDecimal> monthCost = new LinkedHashMap<>();
        for (BizSalesOrder order : orders) {
            String month = order.getCreateTime().toLocalDate().withDayOfMonth(1).toString().substring(0, 7);
            BigDecimal rev = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;
            monthRevenue.merge(month, rev, BigDecimal::add);
            List<BizWorkOrder> wos2 = workOrderMapper.selectList(
                    new LambdaQueryWrapper<BizWorkOrder>().eq(BizWorkOrder::getOrderId, order.getId()));
            BigDecimal cost2 = BigDecimal.ZERO;
            for (BizWorkOrder wo : wos2) {
                BizWoCost c2 = woCostMapper.selectOne(
                        new LambdaQueryWrapper<BizWoCost>().eq(BizWoCost::getWorkOrderId, wo.getId()));
                if (c2 != null) cost2 = cost2.add(c2.getTotalCost());
            }
            monthCost.merge(month, cost2, BigDecimal::add);
        }
        List<Map<String, Object>> monthlyTrend = new ArrayList<>();
        for (String m : monthRevenue.keySet()) {
            Map<String, Object> mt = new LinkedHashMap<>();
            BigDecimal mr = monthRevenue.get(m);
            BigDecimal mc = monthCost.getOrDefault(m, BigDecimal.ZERO);
            mt.put("month", m);
            mt.put("revenue", mr);
            mt.put("cost", mc);
            mt.put("profit", mr.subtract(mc));
            monthlyTrend.add(mt);
        }
        result.put("monthlyTrend", monthlyTrend);

        // costBreakdown: aggregate from biz_wo_cost records for all work orders in period
        BigDecimal sumMaterial = BigDecimal.ZERO, sumLabor = BigDecimal.ZERO,
                sumOutsource = BigDecimal.ZERO, sumOverhead = BigDecimal.ZERO;
        for (BizSalesOrder order : orders) {
            List<BizWorkOrder> wos3 = workOrderMapper.selectList(
                    new LambdaQueryWrapper<BizWorkOrder>().eq(BizWorkOrder::getOrderId, order.getId()));
            for (BizWorkOrder wo : wos3) {
                BizWoCost c3 = woCostMapper.selectOne(
                        new LambdaQueryWrapper<BizWoCost>().eq(BizWoCost::getWorkOrderId, wo.getId()));
                if (c3 != null) {
                    if (c3.getMaterialCost() != null) sumMaterial = sumMaterial.add(c3.getMaterialCost());
                    if (c3.getLaborCost() != null) sumLabor = sumLabor.add(c3.getLaborCost());
                    if (c3.getOutsourceCost() != null) sumOutsource = sumOutsource.add(c3.getOutsourceCost());
                    if (c3.getOverhead() != null) sumOverhead = sumOverhead.add(c3.getOverhead());
                }
            }
        }
        Map<String, Object> costBreakdown = new LinkedHashMap<>();
        costBreakdown.put("materialCost", sumMaterial);
        costBreakdown.put("laborCost", sumLabor);
        costBreakdown.put("outsourceCost", sumOutsource);
        costBreakdown.put("overheadCost", sumOverhead);
        result.put("costBreakdown", costBreakdown);

        return result;
    }

    // =====================================================================
    // 会计科目管理
    // =====================================================================

    public List<Map<String, Object>> getAccountList(String accountName, String accountCode) {
        LambdaQueryWrapper<AccAccount> wrapper = new LambdaQueryWrapper<AccAccount>()
                .like(StringUtils.hasText(accountName), AccAccount::getAccountName, accountName)
                .like(StringUtils.hasText(accountCode), AccAccount::getAccountCode, accountCode)
                .orderByAsc(AccAccount::getAccountCode);
        List<AccAccount> list = accAccountMapper.selectList(wrapper);
        return list.stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("accountCode", a.getAccountCode());
            m.put("accountName", a.getAccountName());
            m.put("accountType", a.getAccountType());
            m.put("direction", a.getDirection());
            m.put("parentId", a.getParentId());
            m.put("level", a.getLevel());
            m.put("isLeaf", a.getIsLeaf());
            m.put("isEnabled", a.getIsEnabled());
            m.put("openingBalance", a.getOpeningBalance() != null ? a.getOpeningBalance() : java.math.BigDecimal.ZERO);
            m.put("remark", a.getRemark());
            return m;
        }).collect(Collectors.toList());
    }

    @Transactional
    public void createAccount(AccAccount account) {
        // 校验编码唯一
        long cnt = accAccountMapper.selectCount(new LambdaQueryWrapper<AccAccount>()
                .eq(AccAccount::getAccountCode, account.getAccountCode()));
        if (cnt > 0) throw new BusinessException("科目编码已存在");
        if (account.getIsEnabled() == null) account.setIsEnabled(true);
        if (account.getIsLeaf() == null) account.setIsLeaf(true);
        if (account.getLevel() == null) account.setLevel(1);
        accAccountMapper.insert(account);
        // 若有父科目，更新父科目为非末级
        if (account.getParentId() != null) {
            AccAccount parent = accAccountMapper.selectById(account.getParentId());
            if (parent != null && Boolean.TRUE.equals(parent.getIsLeaf())) {
                parent.setIsLeaf(false);
                accAccountMapper.updateById(parent);
            }
        }
    }

    @Transactional
    public void updateAccount(Long id, AccAccount account) {
        AccAccount existing = accAccountMapper.selectById(id);
        if (existing == null) throw new BusinessException("科目不存在");
        account.setId(id);
        accAccountMapper.updateById(account);
    }

    @Transactional
    public void deleteAccount(Long id) {
        long childCount = accAccountMapper.selectCount(new LambdaQueryWrapper<AccAccount>()
                .eq(AccAccount::getParentId, id));
        if (childCount > 0) throw new BusinessException("该科目有子科目，不能删除");
        long voucherLineCount = accVoucherLineMapper.selectCount(new LambdaQueryWrapper<AccVoucherLine>()
                .eq(AccVoucherLine::getAccountId, id));
        if (voucherLineCount > 0) throw new BusinessException("该科目已有凭证，不能删除");
        accAccountMapper.deleteById(id);
    }

    // =====================================================================
    // 记账凭证
    // =====================================================================

    public PageResult<Map<String, Object>> getVoucherPage(String keyword, String status, String startDate, String endDate, int pageNum, int pageSize) {
        LambdaQueryWrapper<AccVoucher> wrapper = new LambdaQueryWrapper<AccVoucher>()
                .likeRight(StringUtils.hasText(keyword), AccVoucher::getVoucherNo, keyword)
                .eq(StringUtils.hasText(status), AccVoucher::getStatus, status)
                .ge(StringUtils.hasText(startDate), AccVoucher::getVoucherDate, startDate)
                .le(StringUtils.hasText(endDate), AccVoucher::getVoucherDate, endDate)
                .orderByDesc(AccVoucher::getVoucherDate).orderByDesc(AccVoucher::getId);
        Page<AccVoucher> page = accVoucherMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<Map<String, Object>> rows = page.getRecords().stream().map(v -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", v.getId());
            m.put("voucherNo", v.getVoucherNo());
            m.put("voucherType", v.getVoucherType());
            m.put("voucherDate", v.getVoucherDate());
            m.put("summary", v.getSummary());
            m.put("totalDebit", v.getTotalDebit());
            m.put("totalCredit", v.getTotalCredit());
            m.put("status", v.getStatus());
            m.put("isReversed", Boolean.TRUE.equals(v.getIsReversed()));
            m.put("reversedBy", v.getReversedBy());
            m.put("autoSourceType", v.getAutoSourceType());
            m.put("period", v.getPeriod());
            m.put("remark", v.getRemark());
            m.put("createTime", v.getCreateTime());
            return m;
        }).collect(Collectors.toList());
        return new PageResult<>(rows, page.getTotal());
    }

    public Map<String, Object> getVoucherDetail(Long id) {
        AccVoucher voucher = accVoucherMapper.selectById(id);
        if (voucher == null) throw new BusinessException("凭证不存在");
        List<AccVoucherLine> lines = accVoucherLineMapper.selectList(
                new LambdaQueryWrapper<AccVoucherLine>()
                        .eq(AccVoucherLine::getVoucherId, id)
                        .orderByAsc(AccVoucherLine::getLineNo));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", voucher.getId());
        m.put("voucherNo", voucher.getVoucherNo());
        m.put("voucherType", voucher.getVoucherType());
        m.put("voucherDate", voucher.getVoucherDate());
        m.put("summary", voucher.getSummary());
        m.put("totalDebit", voucher.getTotalDebit());
        m.put("totalCredit", voucher.getTotalCredit());
        m.put("status", voucher.getStatus());
        m.put("remark", voucher.getRemark());
        m.put("createTime", voucher.getCreateTime());
        m.put("lines", lines.stream().map(l -> {
            Map<String, Object> lm = new LinkedHashMap<>();
            lm.put("id", l.getId());
            lm.put("lineNo", l.getLineNo());
            lm.put("summary", l.getSummary());
            lm.put("accountId", l.getAccountId());
            lm.put("accountCode", l.getAccountCode());
            lm.put("accountName", l.getAccountName());
            lm.put("debitAmount", l.getDebitAmount());
            lm.put("creditAmount", l.getCreditAmount());
            return lm;
        }).collect(Collectors.toList()));
        return m;
    }

    @Transactional
    public void createVoucher(AccVoucher voucher, List<AccVoucherLine> lines) {
        if (lines == null || lines.isEmpty()) throw new BusinessException("凭证行不能为空");
        BigDecimal totalDebit = lines.stream()
                .map(l -> l.getDebitAmount() != null ? l.getDebitAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = lines.stream()
                .map(l -> l.getCreditAmount() != null ? l.getCreditAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalDebit.compareTo(totalCredit) != 0) throw new BusinessException("借贷方金额不平衡");
        if (totalDebit.compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException("借方合计必须大于0");

        // 生成凭证号: 记-YYYYMM-NNNN
        String period = LocalDate.now().toString().substring(0, 7); // YYYY-MM
        String periodKey = "记-" + period + "-";
        long seqNo = accVoucherMapper.selectCount(new LambdaQueryWrapper<AccVoucher>()
                .likeRight(AccVoucher::getVoucherNo, periodKey)) + 1;
        voucher.setVoucherNo(periodKey + String.format("%04d", seqNo));
        voucher.setPeriod(period);
        voucher.setTotalDebit(totalDebit);
        voucher.setTotalCredit(totalCredit);
        if (voucher.getStatus() == null) voucher.setStatus("draft");
        if (voucher.getIsReversed() == null) voucher.setIsReversed(false);
        accVoucherMapper.insert(voucher);

        // 填充行信息
        for (int i = 0; i < lines.size(); i++) {
            AccVoucherLine line = lines.get(i);
            line.setVoucherId(voucher.getId());
            line.setLineNo(i + 1);
            // 补充科目名称
            if (line.getAccountId() != null && (line.getAccountCode() == null || line.getAccountName() == null)) {
                AccAccount acc = accAccountMapper.selectById(line.getAccountId());
                if (acc != null) {
                    line.setAccountCode(acc.getAccountCode());
                    line.setAccountName(acc.getAccountName());
                }
            }
            if (line.getDebitAmount() == null) line.setDebitAmount(BigDecimal.ZERO);
            if (line.getCreditAmount() == null) line.setCreditAmount(BigDecimal.ZERO);
            accVoucherLineMapper.insert(line);
        }
    }

    @Transactional
    public void postVoucher(Long id) {
        AccVoucher voucher = accVoucherMapper.selectById(id);
        if (voucher == null) throw new BusinessException("凭证不存在");
        if (!"draft".equals(voucher.getStatus())) throw new BusinessException("只有草稿状态的凭证才能过账");
        voucher.setStatus("posted");
        accVoucherMapper.updateById(voucher);
    }

    @Transactional
    public void deleteVoucher(Long id) {
        AccVoucher voucher = accVoucherMapper.selectById(id);
        if (voucher == null) throw new BusinessException("凭证不存在");
        if (!"draft".equals(voucher.getStatus())) throw new BusinessException("只有草稿状态的凭证才能删除");
        accVoucherLineMapper.delete(new LambdaQueryWrapper<AccVoucherLine>().eq(AccVoucherLine::getVoucherId, id));
        accVoucherMapper.deleteById(id);
    }

    // =====================================================================
    // 账簿查询：科目余额表
    // =====================================================================

    public List<Map<String, Object>> getTrialBalance(String startDate, String endDate) {
        // 获取所有末级科目
        List<AccAccount> accounts = accAccountMapper.selectList(new LambdaQueryWrapper<AccAccount>()
                .eq(AccAccount::getIsLeaf, true)
                .eq(AccAccount::getIsEnabled, true)
                .orderByAsc(AccAccount::getAccountCode));

        if (accounts.isEmpty()) return List.of();

        List<Long> accountIds = accounts.stream().map(AccAccount::getId).collect(Collectors.toList());

        // 查询指定期间已过账凭证的借贷方合计
        LambdaQueryWrapper<AccVoucherLine> lineWrapper = new LambdaQueryWrapper<AccVoucherLine>()
                .in(AccVoucherLine::getAccountId, accountIds);
        List<AccVoucherLine> allLines = accVoucherLineMapper.selectList(lineWrapper);

        // 关联过账凭证过滤日期
        List<Long> voucherIds = allLines.stream().map(AccVoucherLine::getVoucherId).distinct().collect(Collectors.toList());
        if (voucherIds.isEmpty()) {
            // 无凭证，直接返回科目列表（余额全0）
            return accounts.stream().map(a -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("accountCode", a.getAccountCode());
                m.put("accountName", a.getAccountName());
                m.put("accountType", a.getAccountType());
                m.put("direction", a.getDirection());
                m.put("debitTotal", BigDecimal.ZERO);
                m.put("creditTotal", BigDecimal.ZERO);
                m.put("balance", BigDecimal.ZERO);
                return m;
            }).collect(Collectors.toList());
        }

        LambdaQueryWrapper<AccVoucher> voucherWrapper = new LambdaQueryWrapper<AccVoucher>()
                .in(AccVoucher::getId, voucherIds)
                .eq(AccVoucher::getStatus, "posted")
                .ge(StringUtils.hasText(startDate), AccVoucher::getVoucherDate, startDate)
                .le(StringUtils.hasText(endDate), AccVoucher::getVoucherDate, endDate);
        Set<Long> postedVoucherIds = accVoucherMapper.selectList(voucherWrapper).stream()
                .map(AccVoucher::getId).collect(Collectors.toSet());

        // 按科目汇总
        Map<Long, BigDecimal> debitMap = new HashMap<>();
        Map<Long, BigDecimal> creditMap = new HashMap<>();
        for (AccVoucherLine line : allLines) {
            if (!postedVoucherIds.contains(line.getVoucherId())) continue;
            debitMap.merge(line.getAccountId(), line.getDebitAmount() != null ? line.getDebitAmount() : BigDecimal.ZERO, BigDecimal::add);
            creditMap.merge(line.getAccountId(), line.getCreditAmount() != null ? line.getCreditAmount() : BigDecimal.ZERO, BigDecimal::add);
        }

        return accounts.stream().map(a -> {
            BigDecimal debit = debitMap.getOrDefault(a.getId(), BigDecimal.ZERO);
            BigDecimal credit = creditMap.getOrDefault(a.getId(), BigDecimal.ZERO);
            BigDecimal opening = a.getOpeningBalance() != null ? a.getOpeningBalance() : BigDecimal.ZERO;
            // 余额方向：借方科目余额=期初余额+借-贷，贷方科目余额=期初余额+贷-借
            BigDecimal periodBalance = "debit".equals(a.getDirection()) ? debit.subtract(credit) : credit.subtract(debit);
            BigDecimal closingBalance = opening.add(periodBalance);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("accountCode", a.getAccountCode());
            m.put("accountName", a.getAccountName());
            m.put("accountType", a.getAccountType());
            m.put("direction", a.getDirection());
            m.put("openingBalance", opening);
            m.put("debitTotal", debit);
            m.put("creditTotal", credit);
            m.put("balance", closingBalance);
            return m;
        }).collect(Collectors.toList());
    }

    // =====================================================================
    // 财务报表：利润表（简化版）
    // =====================================================================

    public Map<String, Object> getIncomeStatement(String startDate, String endDate) {
        List<Map<String, Object>> balance = getTrialBalance(startDate, endDate);
        BigDecimal revenue = balance.stream()
                .filter(m -> "income".equals(m.get("accountType")))
                .map(m -> (BigDecimal) m.get("balance"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cost = balance.stream()
                .filter(m -> "cost".equals(m.get("accountType")))
                .map(m -> (BigDecimal) m.get("balance"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expense = balance.stream()
                .filter(m -> "expense".equals(m.get("accountType")))
                .map(m -> (BigDecimal) m.get("balance"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal grossProfit = revenue.subtract(cost);
        BigDecimal operatingProfit = grossProfit.subtract(expense);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("revenue", revenue);
        result.put("cost", cost);
        result.put("grossProfit", grossProfit);
        result.put("expense", expense);
        result.put("operatingProfit", operatingProfit);
        result.put("netProfit", operatingProfit);
        result.put("details", balance);
        return result;
    }

    // =====================================================================
    // 财务报表：资产负债表（简化版）
    // =====================================================================

    public Map<String, Object> getBalanceSheet(String asOfDate) {
        List<Map<String, Object>> balance = getTrialBalance(null, asOfDate);
        BigDecimal totalAsset = balance.stream()
                .filter(m -> "asset".equals(m.get("accountType")))
                .map(m -> (BigDecimal) m.get("balance"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalLiability = balance.stream()
                .filter(m -> "liability".equals(m.get("accountType")))
                .map(m -> (BigDecimal) m.get("balance"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalEquity = balance.stream()
                .filter(m -> "equity".equals(m.get("accountType")))
                .map(m -> (BigDecimal) m.get("balance"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalAsset", totalAsset);
        result.put("totalLiability", totalLiability);
        result.put("totalEquity", totalEquity);
        result.put("liabilityAndEquity", totalLiability.add(totalEquity));
        result.put("details", balance);
        return result;
    }

    // =====================================================================
    // 自动凭证生成引擎（内部方法，失败时静默处理，不影响业务操作）
    // =====================================================================

    private void tryCreateAutoVoucher(String voucherType, LocalDate date, String summary,
                                      BigDecimal amount, String debitCode, String creditCode,
                                      String sourceType, Long sourceId) {
        try {
            AccAccount debitAcc = findAccountByCode(debitCode);
            AccAccount creditAcc = findAccountByCode(creditCode);
            if (debitAcc == null || creditAcc == null) return; // 科目未配置，静默跳过

            AccVoucher voucher = new AccVoucher();
            voucher.setVoucherType(voucherType);
            voucher.setVoucherDate(date);
            voucher.setSummary(summary);
            voucher.setAutoSourceType(sourceType);
            voucher.setAutoSourceId(sourceId);
            voucher.setIsReversed(false);

            AccVoucherLine debitLine = new AccVoucherLine();
            debitLine.setSummary(summary);
            debitLine.setAccountId(debitAcc.getId());
            debitLine.setAccountCode(debitAcc.getAccountCode());
            debitLine.setAccountName(debitAcc.getAccountName());
            debitLine.setDebitAmount(amount);
            debitLine.setCreditAmount(BigDecimal.ZERO);

            AccVoucherLine creditLine = new AccVoucherLine();
            creditLine.setSummary(summary);
            creditLine.setAccountId(creditAcc.getId());
            creditLine.setAccountCode(creditAcc.getAccountCode());
            creditLine.setAccountName(creditAcc.getAccountName());
            creditLine.setDebitAmount(BigDecimal.ZERO);
            creditLine.setCreditAmount(amount);

            createVoucher(voucher, List.of(debitLine, creditLine));
            // 自动凭证直接过账
            postVoucher(voucher.getId());
        } catch (Exception ignored) {
            // 自动凭证失败不影响主业务，静默忽略
        }
    }

    private AccAccount findAccountByCode(String code) {
        return accAccountMapper.selectOne(new LambdaQueryWrapper<AccAccount>()
                .eq(AccAccount::getAccountCode, code)
                .eq(AccAccount::getIsEnabled, true));
    }

    // =====================================================================
    // 凭证冲红
    // =====================================================================

    @Transactional
    public void reverseVoucher(Long id) {
        AccVoucher original = accVoucherMapper.selectById(id);
        if (original == null) throw new BusinessException("凭证不存在");
        if (!"posted".equals(original.getStatus())) throw new BusinessException("只有已过账的凭证才能冲红");
        if (Boolean.TRUE.equals(original.getIsReversed())) throw new BusinessException("该凭证已被冲红");

        List<AccVoucherLine> lines = accVoucherLineMapper.selectList(
                new LambdaQueryWrapper<AccVoucherLine>().eq(AccVoucherLine::getVoucherId, id));

        // 创建红字凭证（借贷方互换，金额取负或直接对调）
        AccVoucher reversal = new AccVoucher();
        reversal.setVoucherType(original.getVoucherType());
        reversal.setVoucherDate(LocalDate.now());
        reversal.setSummary("【冲红】" + original.getSummary());
        reversal.setIsReversed(false);

        List<AccVoucherLine> reversalLines = lines.stream().map(l -> {
            AccVoucherLine rl = new AccVoucherLine();
            rl.setSummary(l.getSummary());
            rl.setAccountId(l.getAccountId());
            rl.setAccountCode(l.getAccountCode());
            rl.setAccountName(l.getAccountName());
            // 借贷方对调实现冲红
            rl.setDebitAmount(l.getCreditAmount());
            rl.setCreditAmount(l.getDebitAmount());
            return rl;
        }).collect(Collectors.toList());

        createVoucher(reversal, reversalLines);
        postVoucher(reversal.getId());

        // 标记原凭证为已冲红
        original.setIsReversed(true);
        original.setReversedBy(reversal.getId());
        accVoucherMapper.updateById(original);
    }

    // =====================================================================
    // 期初余额设置
    // =====================================================================

    @Transactional
    public void setOpeningBalance(Long accountId, BigDecimal openingBalance) {
        AccAccount account = accAccountMapper.selectById(accountId);
        if (account == null) throw new BusinessException("科目不存在");
        account.setOpeningBalance(openingBalance != null ? openingBalance : BigDecimal.ZERO);
        accAccountMapper.updateById(account);
    }

    // =====================================================================
    // 明细账查询
    // =====================================================================

    public List<Map<String, Object>> getLedgerDetail(Long accountId, String startDate, String endDate) {
        AccAccount account = accAccountMapper.selectById(accountId);
        if (account == null) throw new BusinessException("科目不存在");

        List<AccVoucherLine> lines = accVoucherLineMapper.selectList(
                new LambdaQueryWrapper<AccVoucherLine>()
                        .eq(AccVoucherLine::getAccountId, accountId)
                        .orderByAsc(AccVoucherLine::getVoucherId));

        if (lines.isEmpty()) return List.of();

        List<Long> voucherIds = lines.stream().map(AccVoucherLine::getVoucherId).distinct().collect(Collectors.toList());
        LambdaQueryWrapper<AccVoucher> vq = new LambdaQueryWrapper<AccVoucher>()
                .in(AccVoucher::getId, voucherIds)
                .eq(AccVoucher::getStatus, "posted")
                .ge(StringUtils.hasText(startDate), AccVoucher::getVoucherDate, startDate)
                .le(StringUtils.hasText(endDate), AccVoucher::getVoucherDate, endDate)
                .orderByAsc(AccVoucher::getVoucherDate);
        Map<Long, AccVoucher> voucherMap = accVoucherMapper.selectList(vq).stream()
                .collect(Collectors.toMap(AccVoucher::getId, v -> v, (a, b) -> a));

        BigDecimal runningBalance = account.getOpeningBalance() != null ? account.getOpeningBalance() : BigDecimal.ZERO;
        List<Map<String, Object>> result = new ArrayList<>();
        // 期初余额行
        Map<String, Object> openingRow = new LinkedHashMap<>();
        openingRow.put("type", "opening");
        openingRow.put("summary", "期初余额");
        openingRow.put("debitAmount", null);
        openingRow.put("creditAmount", null);
        openingRow.put("balance", runningBalance);
        result.add(openingRow);

        for (AccVoucherLine line : lines) {
            AccVoucher voucher = voucherMap.get(line.getVoucherId());
            if (voucher == null) continue; // 跳过未过账或不在日期范围的
            BigDecimal debit = line.getDebitAmount() != null ? line.getDebitAmount() : BigDecimal.ZERO;
            BigDecimal credit = line.getCreditAmount() != null ? line.getCreditAmount() : BigDecimal.ZERO;
            if ("debit".equals(account.getDirection())) {
                runningBalance = runningBalance.add(debit).subtract(credit);
            } else {
                runningBalance = runningBalance.add(credit).subtract(debit);
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", "entry");
            row.put("voucherNo", voucher.getVoucherNo());
            row.put("voucherDate", voucher.getVoucherDate());
            row.put("summary", line.getSummary());
            row.put("debitAmount", debit.compareTo(BigDecimal.ZERO) > 0 ? debit : null);
            row.put("creditAmount", credit.compareTo(BigDecimal.ZERO) > 0 ? credit : null);
            row.put("balance", runningBalance);
            result.add(row);
        }
        return result;
    }

    // =====================================================================
    // 期末结账
    // =====================================================================

    @Transactional
    public void periodClose(String period, Long operatorId) {
        // 检查是否已结账
        long existed = accPeriodCloseMapper.selectCount(new LambdaQueryWrapper<AccPeriodClose>()
                .eq(AccPeriodClose::getPeriod, period));
        if (existed > 0) throw new BusinessException("期间 " + period + " 已结账，不可重复结账");

        // 获取本期损益类科目余额（收入/费用/成本）
        List<AccAccount> pnlAccounts = accAccountMapper.selectList(new LambdaQueryWrapper<AccAccount>()
                .in(AccAccount::getAccountType, List.of("income", "expense", "cost"))
                .eq(AccAccount::getIsLeaf, true)
                .eq(AccAccount::getIsEnabled, true));

        if (pnlAccounts.isEmpty()) throw new BusinessException("未找到损益科目，请先设置会计科目");

        // 查询本期已过账凭证明细
        String startDate = period + "-01";
        // 本期结束日取月末（简单处理：用下月1日前）
        String endDate = period + "-31";
        List<Map<String, Object>> trialBalance = getTrialBalance(startDate, endDate);

        // 找到本年利润科目（4103）
        AccAccount profitAccount = findAccountByCode("4103");
        if (profitAccount == null) throw new BusinessException("未找到本年利润科目(4103)，请先设置");

        // 构建结账凭证行：将收入/费用/成本余额结转到本年利润
        List<AccVoucherLine> closingLines = new ArrayList<>();
        BigDecimal netProfit = BigDecimal.ZERO;

        for (AccAccount acc : pnlAccounts) {
            // 找到该科目余额
            BigDecimal balance = trialBalance.stream()
                    .filter(m -> acc.getAccountCode().equals(m.get("accountCode")))
                    .map(m -> (BigDecimal) m.get("balance"))
                    .findFirst().orElse(BigDecimal.ZERO);

            if (balance.compareTo(BigDecimal.ZERO) == 0) continue;

            AccVoucherLine line = new AccVoucherLine();
            line.setAccountId(acc.getId());
            line.setAccountCode(acc.getAccountCode());
            line.setAccountName(acc.getAccountName());
            line.setSummary("期末结转-" + period);

            if ("income".equals(acc.getAccountType())) {
                // 收入科目贷方余额→结转：借:收入科目 贷:本年利润
                line.setDebitAmount(balance);
                line.setCreditAmount(BigDecimal.ZERO);
                netProfit = netProfit.add(balance);
            } else {
                // 费用/成本科目借方余额→结转：借:本年利润 贷:费用科目
                line.setDebitAmount(BigDecimal.ZERO);
                line.setCreditAmount(balance);
                netProfit = netProfit.subtract(balance);
            }
            closingLines.add(line);
        }

        if (closingLines.isEmpty()) throw new BusinessException("本期无损益发生额，无需结账");

        // 本年利润行（平衡行）
        AccVoucherLine profitLine = new AccVoucherLine();
        profitLine.setAccountId(profitAccount.getId());
        profitLine.setAccountCode(profitAccount.getAccountCode());
        profitLine.setAccountName(profitAccount.getAccountName());
        profitLine.setSummary("期末结转净利润-" + period);
        if (netProfit.compareTo(BigDecimal.ZERO) >= 0) {
            profitLine.setDebitAmount(BigDecimal.ZERO);
            profitLine.setCreditAmount(netProfit);
        } else {
            profitLine.setDebitAmount(netProfit.negate());
            profitLine.setCreditAmount(BigDecimal.ZERO);
        }
        closingLines.add(profitLine);

        // 创建结账凭证
        AccVoucher closingVoucher = new AccVoucher();
        closingVoucher.setVoucherType("transfer");
        closingVoucher.setVoucherDate(LocalDate.parse(period + "-01").withDayOfMonth(
                LocalDate.parse(period + "-01").lengthOfMonth()));
        closingVoucher.setSummary("期末结账-" + period);
        closingVoucher.setPeriod(period);
        createVoucher(closingVoucher, closingLines);
        postVoucher(closingVoucher.getId());

        // 记录结账
        AccPeriodClose closeRecord = new AccPeriodClose();
        closeRecord.setPeriod(period);
        closeRecord.setCloseTime(LocalDateTime.now());
        closeRecord.setCloseBy(operatorId);
        closeRecord.setVoucherId(closingVoucher.getId());
        accPeriodCloseMapper.insert(closeRecord);
    }

    public List<Map<String, Object>> getPeriodCloseList() {
        return accPeriodCloseMapper.selectList(
                new LambdaQueryWrapper<AccPeriodClose>().orderByDesc(AccPeriodClose::getPeriod))
                .stream().map(pc -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", pc.getId());
                    m.put("period", pc.getPeriod());
                    m.put("closeTime", pc.getCloseTime());
                    m.put("closeBy", pc.getCloseBy());
                    m.put("voucherId", pc.getVoucherId());
                    return m;
                }).collect(Collectors.toList());
    }

    // ==================== 往来发票 ====================

    public PageResult<Map<String, Object>> getInvoicePage(String invoiceType, String invoiceNo,
                                                          String buyerName, String sellerName,
                                                          String relatedType, String relatedNo,
                                                          String status,
                                                          String dateStart, String dateEnd,
                                                          int pageNum, int pageSize) {
        LambdaQueryWrapper<BizFinanceInvoice> wrapper = new LambdaQueryWrapper<BizFinanceInvoice>()
                .eq(StringUtils.hasText(invoiceType), BizFinanceInvoice::getInvoiceType, invoiceType)
                .eq(StringUtils.hasText(relatedType), BizFinanceInvoice::getRelatedType, relatedType)
                .eq(StringUtils.hasText(status), BizFinanceInvoice::getStatus, status)
                .like(StringUtils.hasText(invoiceNo), BizFinanceInvoice::getInvoiceNo, invoiceNo)
                .like(StringUtils.hasText(buyerName), BizFinanceInvoice::getBuyerName, buyerName)
                .like(StringUtils.hasText(sellerName), BizFinanceInvoice::getSellerName, sellerName)
                .like(StringUtils.hasText(relatedNo), BizFinanceInvoice::getRelatedNo, relatedNo)
                .ge(StringUtils.hasText(dateStart), BizFinanceInvoice::getInvoiceDate, dateStart)
                .le(StringUtils.hasText(dateEnd), BizFinanceInvoice::getInvoiceDate, dateEnd)
                .orderByDesc(BizFinanceInvoice::getCreateTime);

        Page<BizFinanceInvoice> page = invoiceMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        // 补充客户/供应商名称
        List<Map<String, Object>> list = page.getRecords().stream().map(inv -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", inv.getId());
            m.put("invoiceNo", inv.getInvoiceNo());
            m.put("invoiceType", inv.getInvoiceType());
            m.put("invoiceCode", inv.getInvoiceCode());
            m.put("invoiceNumber", inv.getInvoiceNumber());
            m.put("invoiceDate", inv.getInvoiceDate());
            m.put("amount", inv.getAmount());
            m.put("taxRate", inv.getTaxRate());
            m.put("taxAmount", inv.getTaxAmount());
            m.put("totalAmount", inv.getTotalAmount());
            m.put("buyerName", inv.getBuyerName());
            m.put("buyerTaxNo", inv.getBuyerTaxNo());
            m.put("sellerName", inv.getSellerName());
            m.put("sellerTaxNo", inv.getSellerTaxNo());
            m.put("relatedType", inv.getRelatedType());
            m.put("relatedId", inv.getRelatedId());
            m.put("relatedNo", inv.getRelatedNo());
            m.put("customerId", inv.getCustomerId());
            m.put("supplierId", inv.getSupplierId());
            m.put("attachmentUrl", inv.getAttachmentUrl());
            m.put("itemsJson", inv.getItemsJson());
            m.put("status", inv.getStatus());
            m.put("remark", inv.getRemark());
            m.put("createTime", inv.getCreateTime());

            // 补充名称
            if (inv.getCustomerId() != null) {
                var c = customerMapper.selectById(inv.getCustomerId());
                if (c != null) m.put("customerName", c.getCustomerName());
            }
            if (inv.getSupplierId() != null) {
                var s = supplierMapper.selectById(inv.getSupplierId());
                if (s != null) m.put("supplierName", s.getSupplierName());
            }
            return m;
        }).collect(Collectors.toList());

        return new PageResult<>(list, page.getTotal(), pageNum, pageSize);
    }

    /**
     * 导出发票明细 Excel (按订单号聚合)
     * 列: 销售订单号 / 产品编号 / 产品名称 / 单价 / 数量 / 已开票金额(不含税) / 税额 / 已开票金额(含税)
     * 同一订单号 + 同一产品 的多张发票数据会累加
     */
    public void exportInvoices(jakarta.servlet.http.HttpServletResponse response,
                               String invoiceType, String invoiceNo,
                               String buyerName, String sellerName,
                               String relatedType, String relatedNo,
                               String status, String dateStart, String dateEnd) throws java.io.IOException {
        PageResult<Map<String, Object>> all = getInvoicePage(invoiceType, invoiceNo, buyerName, sellerName,
                relatedType, relatedNo, status, dateStart, dateEnd, 1, Integer.MAX_VALUE);

        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();

        // 展开每张发票的明细行,并收集产品id用于查编码
        List<Map<String, Object>> rows = new ArrayList<>();
        java.util.Set<Long> productIds = new java.util.HashSet<>();

        for (Map<String, Object> inv : all.getRecords()) {
            // 已作废的发票不参与导出统计
            if ("cancelled".equals(inv.get("status"))) continue;
            String relNo = (String) inv.get("relatedNo");
            Object itemsJsonObj = inv.get("itemsJson");
            List<Map<String, Object>> items = null;
            if (itemsJsonObj instanceof String && !((String) itemsJsonObj).isBlank()) {
                try {
                    items = om.readValue((String) itemsJsonObj,
                            om.getTypeFactory().constructCollectionType(List.class, Map.class));
                } catch (Exception ignored) {}
            }
            if (items == null || items.isEmpty()) {
                // 无明细: 按整张发票作为一行导出(产品相关字段留空)
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("relatedNo", relNo);
                row.put("productNo", "");
                row.put("productName", "");
                row.put("unitPrice", null);
                row.put("quantity", null);
                row.put("amountExTax", inv.get("amount"));
                row.put("taxAmount", inv.get("taxAmount"));
                row.put("totalAmount", inv.get("totalAmount"));
                rows.add(row);
            } else {
                for (Map<String, Object> it : items) {
                    Long pid = it.get("productId") instanceof Number ? ((Number) it.get("productId")).longValue() : null;
                    if (pid != null) productIds.add(pid);
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("relatedNo", relNo);
                    row.put("_productId", pid);
                    row.put("productName", it.get("productName"));
                    row.put("unitPrice", it.get("unitPrice"));
                    row.put("quantity", it.get("quantity"));
                    row.put("amountExTax", it.get("amountExTax"));
                    row.put("taxAmount", it.get("taxAmount"));
                    row.put("totalAmount", it.get("totalAmount"));
                    rows.add(row);
                }
            }
        }

        // 批量查产品编号
        java.util.Map<Long, String> productNoMap = productIds.isEmpty() ? Map.of() :
                materialMapper.selectBatchIds(productIds).stream()
                        .collect(java.util.stream.Collectors.toMap(BizMaterial::getId,
                                m -> m.getMaterialNo() != null ? m.getMaterialNo() : "", (a, b) -> a));
        for (Map<String, Object> row : rows) {
            Object pid = row.get("_productId");
            if (pid instanceof Long) row.put("productNo", productNoMap.getOrDefault((Long) pid, ""));
            if (!row.containsKey("productNo")) row.put("productNo", "");
            row.remove("_productId");
        }

        // 按"订单号+产品编号+产品名称"聚合: 跨多张发票的同订单同产品合并
        // 单价保留(取第一次出现的值),数量/金额累加
        java.util.Map<String, Map<String, Object>> grouped = new java.util.LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            String key = (r.get("relatedNo") == null ? "" : r.get("relatedNo"))
                    + "|" + r.get("productNo") + "|" + r.get("productName");
            Map<String, Object> agg = grouped.get(key);
            if (agg == null) {
                agg = new LinkedHashMap<>(r);
                grouped.put(key, agg);
            } else {
                agg.put("quantity", addBD(agg.get("quantity"), r.get("quantity")));
                agg.put("amountExTax", addBD(agg.get("amountExTax"), r.get("amountExTax")));
                agg.put("taxAmount", addBD(agg.get("taxAmount"), r.get("taxAmount")));
                agg.put("totalAmount", addBD(agg.get("totalAmount"), r.get("totalAmount")));
            }
        }

        com.aimes.common.utils.ExcelUtil.export(response, "发票明细",
                List.of("销售订单号", "产品编号", "产品名称", "单价",
                        "数量", "已开票金额(不含税)", "税额", "已开票金额(含税)"),
                new ArrayList<>(grouped.values()),
                List.of(
                        r -> r.get("relatedNo"),
                        r -> r.get("productNo"),
                        r -> r.get("productName"),
                        r -> r.get("unitPrice"),
                        r -> r.get("quantity"),
                        r -> r.get("amountExTax"),
                        r -> r.get("taxAmount"),
                        r -> r.get("totalAmount")
                ));
    }

    private BigDecimal addBD(Object a, Object b) {
        BigDecimal x = a instanceof BigDecimal ? (BigDecimal) a : (a == null ? BigDecimal.ZERO : new BigDecimal(a.toString()));
        BigDecimal y = b instanceof BigDecimal ? (BigDecimal) b : (b == null ? BigDecimal.ZERO : new BigDecimal(b.toString()));
        return x.add(y);
    }

    /** 防止财务发票 ↔ 订单发票互相同步造成死循环的标记 */
    private static final ThreadLocal<Boolean> SYNC_LOCK = ThreadLocal.withInitial(() -> false);
    public static void setSyncLock(boolean v) { SYNC_LOCK.set(v); }

    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void createInvoice(BizFinanceInvoice invoice) {
        invoice.setInvoiceNo(CodeUtil.generate("INV"));
        if (invoice.getStatus() == null) invoice.setStatus("draft");
        // 计算价税合计
        if (invoice.getAmount() != null && invoice.getTaxAmount() != null) {
            invoice.setTotalAmount(invoice.getAmount().add(invoice.getTaxAmount()));
        }
        invoiceMapper.insert(invoice);
        // 若来自订单发票同步,不再反向同步; 否则根据 relatedType 同步到对应源表
        if (!SYNC_LOCK.get()) {
            syncToSource(invoice);
        }
    }

    /** 将财务发票反向同步到源单据(如销售订单发票表) */
    private void syncToSource(BizFinanceInvoice fi) {
        if (!"sales_order".equals(fi.getRelatedType()) || fi.getRelatedId() == null) return;
        try {
            var orderInvoiceMapper = applicationContext.getBean(
                    com.aimes.modules.order.mapper.BizOrderInvoiceMapper.class);
            // 若该财务发票的 relatedId 已经是 biz_order_invoice.id（来自订单侧同步），跳过
            com.aimes.modules.order.entity.BizOrderInvoice existing = orderInvoiceMapper.selectById(fi.getRelatedId());
            if (existing != null) return;

            com.aimes.modules.order.entity.BizOrderInvoice oi = new com.aimes.modules.order.entity.BizOrderInvoice();
            oi.setOrderId(fi.getRelatedId());     // 财务表新建时 relatedId 存的是 sales_order.id
            oi.setInvoiceNo(fi.getInvoiceNumber());
            oi.setInvoiceAmount(fi.getTotalAmount());
            oi.setTaxAmount(fi.getTaxAmount());
            oi.setTaxRate(fi.getTaxRate());
            oi.setInvoiceDate(fi.getInvoiceDate());
            oi.setAttachmentUrl(fi.getAttachmentUrl());
            oi.setItemsJson(fi.getItemsJson());
            oi.setRemark(fi.getRemark());
            oi.setStatus(mapFinanceToOrderStatus(fi.getStatus()));
            orderInvoiceMapper.insert(oi);
            // 回写 relatedId 使其指向新建的 order_invoice.id,形成双向关联
            fi.setRelatedId(oi.getId());
            invoiceMapper.updateById(fi);
        } catch (Exception e) {
            log.warn("财务发票反向同步到销售订单失败: financeInvoiceId={}", fi.getId(), e);
        }
    }

    private String mapFinanceToOrderStatus(String financeStatus) {
        if ("confirmed".equals(financeStatus)) return "issued";
        if ("cancelled".equals(financeStatus)) return "cancelled";
        return "pending";
    }

    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void updateInvoice(Long id, BizFinanceInvoice invoice) {
        BizFinanceInvoice existing = invoiceMapper.selectById(id);
        if (existing == null) throw new BusinessException("发票不存在");
        invoice.setId(id);
        invoice.setInvoiceNo(null); // 不允许修改编号
        if (invoice.getAmount() != null && invoice.getTaxAmount() != null) {
            invoice.setTotalAmount(invoice.getAmount().add(invoice.getTaxAmount()));
        }
        invoiceMapper.updateById(invoice);
        // 同步更新订单发票(非死循环场景下)
        if (!SYNC_LOCK.get()) {
            syncUpdateToSource(id);
        }
    }

    private void syncUpdateToSource(Long financeInvoiceId) {
        try {
            BizFinanceInvoice fi = invoiceMapper.selectById(financeInvoiceId);
            if (fi == null || !"sales_order".equals(fi.getRelatedType()) || fi.getRelatedId() == null) return;
            var orderInvoiceMapper = applicationContext.getBean(
                    com.aimes.modules.order.mapper.BizOrderInvoiceMapper.class);
            com.aimes.modules.order.entity.BizOrderInvoice oi = orderInvoiceMapper.selectById(fi.getRelatedId());
            if (oi == null) return;
            oi.setInvoiceAmount(fi.getTotalAmount());
            oi.setTaxAmount(fi.getTaxAmount());
            if (fi.getInvoiceDate() != null) oi.setInvoiceDate(fi.getInvoiceDate());
            if (fi.getAttachmentUrl() != null) oi.setAttachmentUrl(fi.getAttachmentUrl());
            if (fi.getItemsJson() != null) oi.setItemsJson(fi.getItemsJson());
            if (fi.getRemark() != null) oi.setRemark(fi.getRemark());
            oi.setStatus(mapFinanceToOrderStatus(fi.getStatus()));
            orderInvoiceMapper.updateById(oi);
        } catch (Exception e) {
            log.warn("财务发票更新同步到订单发票失败: financeInvoiceId={}", financeInvoiceId, e);
        }
    }

    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void deleteInvoice(Long id) {
        BizFinanceInvoice fi = invoiceMapper.selectById(id);
        invoiceMapper.deleteById(id);
        // 同步软删订单发票
        if (fi != null && !SYNC_LOCK.get() && "sales_order".equals(fi.getRelatedType()) && fi.getRelatedId() != null) {
            try {
                var orderInvoiceMapper = applicationContext.getBean(
                        com.aimes.modules.order.mapper.BizOrderInvoiceMapper.class);
                orderInvoiceMapper.deleteById(fi.getRelatedId());
            } catch (Exception e) {
                log.warn("财务发票删除同步到订单发票失败: financeInvoiceId={}", id, e);
            }
        }
    }

    /**
     * 员工报工分析：按员工+产品+工序 聚合报工数据
     * 返回动态列格式，前端根据 processes 列表动态渲染表头
     */
    /**
     * 员工报工分析：按实际工作人员+产品+工序聚合报工数据
     * - 班组报工：展开到班组每个成员，按权重分配数量
     * - 个人报工：取工序任务的指派员工(assignedUserId)，若为班组指派则展开班组成员
     */
    public Map<String, Object> getReportAnalysis(String startDate, String endDate, String employeeName, String processName) {
        var reportMapper = applicationContext.getBean(com.aimes.modules.workreport.mapper.BizWorkReportMapper.class);
        var taskMapper = applicationContext.getBean(com.aimes.modules.production.mapper.BizWoProcessTaskMapper.class);
        var teamReportDetailMapper = applicationContext.getBean(com.aimes.modules.workreport.mapper.BizTeamReportDetailMapper.class);
        var teamMemberMapper = applicationContext.getBean(com.aimes.modules.team.mapper.BizTeamMemberMapper.class);

        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        LocalDateTime startTime = start.atStartOfDay();
        LocalDateTime endTime = end.plusDays(1).atStartOfDay();

        // 查询报工记录
        LambdaQueryWrapper<com.aimes.modules.workreport.entity.BizWorkReport> wrapper =
            new LambdaQueryWrapper<com.aimes.modules.workreport.entity.BizWorkReport>()
                .between(com.aimes.modules.workreport.entity.BizWorkReport::getCreateTime, startTime, endTime)
                .ne(com.aimes.modules.workreport.entity.BizWorkReport::getStatus, "rejected");
        List<com.aimes.modules.workreport.entity.BizWorkReport> reports = reportMapper.selectList(wrapper);
        if (reports.isEmpty()) return Map.of("processes", List.of(), "rows", List.of());

        // 加载工序任务
        Set<Long> taskIds = reports.stream().map(r -> r.getTaskId()).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, com.aimes.modules.production.entity.BizWoProcessTask> taskMap = taskIds.isEmpty() ? Map.of() :
            taskMapper.selectBatchIds(taskIds).stream().collect(Collectors.toMap(t -> t.getId(), t -> t, (a, b) -> a));

        // 按工序名称过滤
        if (StringUtils.hasText(processName)) {
            Set<Long> matchTaskIds = taskMap.entrySet().stream()
                .filter(e -> e.getValue().getStepName() != null && e.getValue().getStepName().contains(processName))
                .map(Map.Entry::getKey).collect(Collectors.toSet());
            reports = reports.stream().filter(r -> matchTaskIds.contains(r.getTaskId())).collect(Collectors.toList());
            if (reports.isEmpty()) return Map.of("processes", List.of(), "rows", List.of());
        }

        // 加载工单->产品名称
        Set<Long> woIds = reports.stream().map(r -> r.getWorkOrderId()).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, BizWorkOrder> woMap = woIds.isEmpty() ? Map.of() :
            workOrderMapper.selectBatchIds(woIds).stream().collect(Collectors.toMap(w -> w.getId(), w -> w, (a, b) -> a));
        Set<Long> productIds = woMap.values().stream().map(w -> w.getProductId()).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> productNameMap = productIds.isEmpty() ? Map.of() :
            productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(BizProduct::getId, p -> p.getProductName() != null ? p.getProductName() : "", (a, b) -> a));

        // 加载班组报工明细（用于展开班组成员）
        Set<Long> teamReportIds = reports.stream()
            .filter(r -> "team".equals(r.getReportType()) && r.getTeamReportId() != null)
            .map(r -> r.getTeamReportId()).collect(Collectors.toSet());
        Map<Long, List<com.aimes.modules.workreport.entity.BizTeamReportDetail>> teamDetailMap = new HashMap<>();
        if (!teamReportIds.isEmpty()) {
            var allDetails = teamReportDetailMapper.selectList(
                new LambdaQueryWrapper<com.aimes.modules.workreport.entity.BizTeamReportDetail>()
                    .in(com.aimes.modules.workreport.entity.BizTeamReportDetail::getTeamReportId, teamReportIds));
            for (var d : allDetails) {
                teamDetailMap.computeIfAbsent(d.getTeamReportId(), k -> new ArrayList<>()).add(d);
            }
        }

        // 构建展开记录：每条记录 = (userId, qty, report, task)
        // 内部结构：userId -> productName -> stepName -> [qty, piecePrice]
        Set<String> allProcesses = new TreeSet<>();
        Map<String, Map<String, BigDecimal[]>> pivotMap = new LinkedHashMap<>();
        Map<String, Long> rowUserIdMap = new HashMap<>();
        Set<Long> allUserIds = new HashSet<>();

        for (var report : reports) {
            var task = taskMap.get(report.getTaskId());
            if (task == null) continue;
            String stepName = task.getStepName() != null ? task.getStepName() : "未知工序";
            BigDecimal piecePrice = task.getPiecePrice() != null ? task.getPiecePrice() : BigDecimal.ZERO;
            BigDecimal qty = report.getQualifiedQty() != null ? report.getQualifiedQty() : BigDecimal.ZERO;

            var wo = woMap.get(report.getWorkOrderId());
            String prodName = wo != null && wo.getProductId() != null ? productNameMap.getOrDefault(wo.getProductId(), "") : "";
            allProcesses.add(stepName);

            // 展开到实际工作人员
            List<long[]> workerEntries = new ArrayList<>(); // [userId, 分子, 分母] 用于按比例分配

            if ("team".equals(report.getReportType()) && report.getTeamReportId() != null) {
                // 班组报工：展开到班组每个成员，按权重分配
                List<com.aimes.modules.workreport.entity.BizTeamReportDetail> details = teamDetailMap.get(report.getTeamReportId());
                if (details != null && !details.isEmpty()) {
                    BigDecimal totalWeight = details.stream().map(d -> d.getWeight() != null ? d.getWeight() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                    for (var d : details) {
                        if (d.getUserId() == null) continue;
                        BigDecimal w = d.getWeight() != null ? d.getWeight() : BigDecimal.ZERO;
                        BigDecimal memberQty = totalWeight.compareTo(BigDecimal.ZERO) > 0
                            ? qty.multiply(w).divide(totalWeight, 4, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO;
                        addToPivot(pivotMap, rowUserIdMap, d.getUserId(), prodName, stepName, memberQty, piecePrice);
                        allUserIds.add(d.getUserId());
                    }
                    continue; // 已处理
                }
            }

            // 个人报工：取工序任务的指派人员
            if ("team".equals(task.getAssignType()) && task.getAssignedTeamId() != null) {
                // 任务指派给班组，展开班组成员
                List<com.aimes.modules.team.entity.BizTeamMember> members = teamMemberMapper.selectList(
                    new LambdaQueryWrapper<com.aimes.modules.team.entity.BizTeamMember>()
                        .eq(com.aimes.modules.team.entity.BizTeamMember::getTeamId, task.getAssignedTeamId()));
                if (!members.isEmpty()) {
                    BigDecimal totalWeight = members.stream().map(m -> m.getWeight() != null ? m.getWeight() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                    for (var m : members) {
                        if (m.getUserId() == null) continue;
                        BigDecimal w = m.getWeight() != null ? m.getWeight() : BigDecimal.ZERO;
                        BigDecimal memberQty = totalWeight.compareTo(BigDecimal.ZERO) > 0
                            ? qty.multiply(w).divide(totalWeight, 4, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO;
                        addToPivot(pivotMap, rowUserIdMap, m.getUserId(), prodName, stepName, memberQty, piecePrice);
                        allUserIds.add(m.getUserId());
                    }
                    continue;
                }
            }

            // 个人指派或无指派：使用assignedUserId，兜底使用reporterId
            Long actualUserId = task.getAssignedUserId() != null ? task.getAssignedUserId() : report.getReporterId();
            if (actualUserId != null) {
                addToPivot(pivotMap, rowUserIdMap, actualUserId, prodName, stepName, qty, piecePrice);
                allUserIds.add(actualUserId);
            }
        }

        // 加载所有涉及的用户名称
        Map<Long, String> userNameMap = allUserIds.isEmpty() ? Map.of() :
            userMapper.selectBatchIds(allUserIds).stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u.getRealName() != null ? u.getRealName() : u.getUsername(), (a, b) -> a));

        // 按员工名称过滤
        if (StringUtils.hasText(employeeName)) {
            Set<Long> matchUserIds = userNameMap.entrySet().stream()
                .filter(e -> e.getValue().contains(employeeName))
                .map(Map.Entry::getKey).collect(Collectors.toSet());
            pivotMap.entrySet().removeIf(e -> !matchUserIds.contains(rowUserIdMap.get(e.getKey())));
            if (pivotMap.isEmpty()) return Map.of("processes", List.of(), "rows", List.of());
        }

        // 构建返回结果
        List<String> processList = new ArrayList<>(allProcesses);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (var entry : pivotMap.entrySet()) {
            String[] parts = entry.getKey().split("\\|", 2);
            Long userId = rowUserIdMap.get(entry.getKey());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", userId);
            row.put("realName", userNameMap.getOrDefault(userId, ""));
            row.put("productName", parts.length > 1 ? parts[1] : "");
            Map<String, Object> processData = new LinkedHashMap<>();
            BigDecimal totalWage = BigDecimal.ZERO;
            for (var pe : entry.getValue().entrySet()) {
                BigDecimal q = pe.getValue()[0];
                BigDecimal p = pe.getValue()[1];
                BigDecimal subtotal = q.multiply(p);
                totalWage = totalWage.add(subtotal);
                Map<String, Object> pd = new LinkedHashMap<>();
                pd.put("qty", q);
                pd.put("piecePrice", p);
                pd.put("subtotal", subtotal);
                processData.put(pe.getKey(), pd);
            }
            row.put("processData", processData);
            row.put("totalWage", totalWage);
            rows.add(row);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("processes", processList);
        result.put("rows", rows);
        return result;
    }

    /**
     * 员工报工分析（平铺列表）：按实际工作人员展开
     * - 班组报工：展开到班组每个成员（BizTeamReportDetail），按权重分配数量
     * - 个人报工（班组指派）：展开班组成员（BizTeamMember），按权重分配数量
     * - 个人报工（个人指派）：取工序任务的assignedUserId
     */
    public PageResult<Map<String, Object>> getReportAnalysisList(
            String startDate, String endDate,
            String employeeName, String processName, String materialName,
            int pageNum, int pageSize) {
        var reportMapper = applicationContext.getBean(com.aimes.modules.workreport.mapper.BizWorkReportMapper.class);
        var taskMapper = applicationContext.getBean(com.aimes.modules.production.mapper.BizWoProcessTaskMapper.class);

        LambdaQueryWrapper<com.aimes.modules.workreport.entity.BizWorkReport> wrapper =
                new LambdaQueryWrapper<com.aimes.modules.workreport.entity.BizWorkReport>()
                        .ne(com.aimes.modules.workreport.entity.BizWorkReport::getStatus, "rejected")
                        .orderByDesc(com.aimes.modules.workreport.entity.BizWorkReport::getCreateTime);

        if (StringUtils.hasText(startDate) && StringUtils.hasText(endDate)) {
            LocalDateTime startTime = LocalDate.parse(startDate).atStartOfDay();
            LocalDateTime endTime = LocalDate.parse(endDate).plusDays(1).atStartOfDay();
            wrapper.between(com.aimes.modules.workreport.entity.BizWorkReport::getCreateTime, startTime, endTime);
        }

        List<com.aimes.modules.workreport.entity.BizWorkReport> reports = reportMapper.selectList(wrapper);
        if (reports.isEmpty()) return new PageResult<>(List.of(), 0L, pageNum, pageSize);

        // 批量加载关联数据
        Set<Long> taskIds = reports.stream().map(r -> r.getTaskId()).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, com.aimes.modules.production.entity.BizWoProcessTask> taskMap = taskIds.isEmpty() ? Map.of() :
                taskMapper.selectBatchIds(taskIds).stream()
                        .collect(Collectors.toMap(t -> t.getId(), t -> t, (a, b) -> a));

        Set<Long> woIds = reports.stream().map(r -> r.getWorkOrderId()).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, BizWorkOrder> woMap = woIds.isEmpty() ? Map.of() :
                workOrderMapper.selectBatchIds(woIds).stream()
                        .collect(Collectors.toMap(w -> w.getId(), w -> w, (a, b) -> a));

        // 工单的 productId 实际指向 biz_material,不是 biz_product
        Set<Long> productIds = woMap.values().stream().map(BizWorkOrder::getProductId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, BizMaterial> matMap = productIds.isEmpty() ? Map.of() :
                materialMapper.selectBatchIds(productIds).stream()
                        .collect(Collectors.toMap(BizMaterial::getId, m -> m, (a, b) -> a));

        // 展开到实际工作人员，构建平铺记录
        Set<Long> allUserIds = new HashSet<>();
        // 临时记录列表: [userId, reporterName(待填), ...]
        List<Map<String, Object>> allRows = new ArrayList<>();

        for (var r : reports) {
            var task = taskMap.get(r.getTaskId());
            String stepName = task != null && task.getStepName() != null ? task.getStepName() : "";
            BigDecimal piecePrice = task != null && task.getPiecePrice() != null ? task.getPiecePrice() : BigDecimal.ZERO;
            BigDecimal qty = r.getQualifiedQty() != null ? r.getQualifiedQty() : BigDecimal.ZERO;
            String wageMode = r.getWageMode() != null ? r.getWageMode() : "normal";
            BigDecimal taskWageRatio = task != null && task.getWageRatio() != null ? task.getWageRatio() : BigDecimal.ONE;

            var wo = woMap.get(r.getWorkOrderId());
            String woNo = wo != null ? wo.getWoNo() : "";
            var material = wo != null && wo.getProductId() != null ? matMap.get(wo.getProductId()) : null;
            String productNo = material != null && material.getMaterialNo() != null ? material.getMaterialNo() : "";
            String productName = material != null && material.getMaterialName() != null ? material.getMaterialName() : "";

            // 过滤工序和物料
            if (StringUtils.hasText(processName) && !stepName.contains(processName)) continue;
            if (StringUtils.hasText(materialName) && !productName.contains(materialName)) continue;
            // 单价为 0 / null 的工序不进报工分析 (无工资可算, 列表/汇总/导出一并过滤)
            if (piecePrice == null || piecePrice.compareTo(BigDecimal.ZERO) <= 0) continue;

            // 报工已统一为 personal 模式 (老 team 报工也是逐成员一行 reporterId+整数 qty),
            // 这里直接用 r.reporterId / r.qualifiedQty, 不再按 team_report_detail 二次按权重分摊
            // (那会把整数 qty 重新除以 totalWeight 产生 0.6666 这种浮点, 且重复计工)
            Long actualUserId = r.getReporterId() != null ? r.getReporterId()
                    : (task != null ? task.getAssignedUserId() : null);
            if (actualUserId != null) {
                allUserIds.add(actualUserId);
                allRows.add(buildReportRow(actualUserId, r.getCreateTime(), woNo, productNo, productName,
                        stepName, piecePrice, qty, r.getReportType(), wageMode, taskWageRatio));
            }
        }

        // 批量加载用户名称并回填
        Map<Long, String> userNameMap = allUserIds.isEmpty() ? Map.of() :
                userMapper.selectBatchIds(allUserIds).stream()
                        .collect(Collectors.toMap(SysUser::getId,
                                u -> u.getRealName() != null ? u.getRealName() : u.getUsername(), (a, b) -> a));
        for (var row : allRows) {
            Long uid = (Long) row.get("_userId");
            row.put("reporterName", userNameMap.getOrDefault(uid, ""));
            row.remove("_userId");
        }

        // 按员工名称过滤
        if (StringUtils.hasText(employeeName)) {
            allRows.removeIf(row -> {
                String name = (String) row.getOrDefault("reporterName", "");
                return !name.contains(employeeName);
            });
        }

        long total = allRows.size();
        int fromIdx = (pageNum - 1) * pageSize;
        int toIdx = Math.min(fromIdx + pageSize, (int) total);
        List<Map<String, Object>> pageRows = fromIdx >= total ? List.of() : allRows.subList(fromIdx, toIdx);
        return new PageResult<>(pageRows, total, pageNum, pageSize);
    }

    /** 构建单条报工分析平铺行 */
    private Map<String, Object> buildReportRow(Long userId, Object reportTime, String woNo,
            String materialNo, String materialName, String processName,
            BigDecimal piecePrice, BigDecimal qualifiedQty, String reportType,
            String wageMode, BigDecimal taskWageRatio) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("_userId", userId); // 临时字段，后续替换为reporterName
        row.put("reportTime", reportTime);
        row.put("woNo", woNo);
        row.put("materialNo", materialNo);
        row.put("materialName", materialName);
        row.put("processName", processName);
        row.put("piecePrice", piecePrice);
        row.put("qualifiedQty", qualifiedQty);
        // 工资倍率: normal=1, three_machine=工序在生产计划里配置的 wageRatio (空或 ≤0 则按 1)
        BigDecimal multiplier;
        if ("three_machine".equals(wageMode)) {
            multiplier = taskWageRatio != null && taskWageRatio.compareTo(BigDecimal.ZERO) > 0
                    ? taskWageRatio : BigDecimal.ONE;
        } else {
            multiplier = BigDecimal.ONE;
        }
        // 本行工资金额 = 单价 × 合格数 × 倍率
        BigDecimal amount = (piecePrice != null ? piecePrice : BigDecimal.ZERO)
                .multiply(qualifiedQty != null ? qualifiedQty : BigDecimal.ZERO)
                .multiply(multiplier)
                .setScale(2, java.math.RoundingMode.HALF_UP);
        row.put("amount", amount);
        row.put("reportType", reportType);
        row.put("wageMode", wageMode);
        row.put("wageRatio", multiplier);
        return row;
    }

    /**
     * 查询报工分析合计(全部过滤记录)
     */
    public Map<String, Object> getReportAnalysisSummary(
            String startDate, String endDate,
            String employeeName, String processName, String materialName) {
        // 复用列表查询但不分页 - 传大pageSize获取全部
        PageResult<Map<String, Object>> all = getReportAnalysisList(
                startDate, endDate, employeeName, processName, materialName, 1, Integer.MAX_VALUE);
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (var row : all.getRecords()) {
            Object q = row.get("qualifiedQty");
            Object a = row.get("amount");
            if (q instanceof BigDecimal) totalQty = totalQty.add((BigDecimal) q);
            if (a instanceof BigDecimal) totalAmount = totalAmount.add((BigDecimal) a);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalCount", all.getTotal());
        result.put("totalQty", totalQty);
        result.put("totalAmount", totalAmount);
        return result;
    }

    /**
     * 导出报工分析 Excel
     */
    public void exportReportAnalysis(jakarta.servlet.http.HttpServletResponse response,
            String startDate, String endDate,
            String employeeName, String processName, String materialName) throws java.io.IOException {
        PageResult<Map<String, Object>> all = getReportAnalysisList(
                startDate, endDate, employeeName, processName, materialName, 1, Integer.MAX_VALUE);
        List<Map<String, Object>> records = all.getRecords();
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        com.aimes.common.utils.ExcelUtil.export(response, "员工报工分析",
                List.of("报工人", "报工时间", "生产单号", "物料编号", "物料名称", "工序名称", "单价", "合格数量",
                        "报工模式", "工资倍率", "金额"),
                records,
                List.of(
                        row -> row.get("reporterName"),
                        row -> {
                            Object t = row.get("reportTime");
                            if (t instanceof java.time.LocalDateTime) return ((java.time.LocalDateTime) t).format(fmt);
                            return t != null ? t.toString() : "";
                        },
                        row -> row.get("woNo"),
                        row -> row.get("materialNo"),
                        row -> row.get("materialName"),
                        row -> row.get("processName"),
                        row -> row.get("piecePrice"),
                        row -> {
                            Object q = row.get("qualifiedQty");
                            if (q instanceof BigDecimal) return ((BigDecimal) q).setScale(0, java.math.RoundingMode.HALF_UP);
                            if (q instanceof Number) return Math.round(((Number) q).doubleValue());
                            return q;
                        },
                        row -> "three_machine".equals(row.get("wageMode")) ? "三机" : "正常",
                        row -> row.get("wageRatio"),
                        row -> row.get("amount")
                ));
    }

    /** 辅助方法：向pivot表中累加数据 */
    private void addToPivot(Map<String, Map<String, BigDecimal[]>> pivotMap, Map<String, Long> rowUserIdMap,
                            Long userId, String prodName, String stepName, BigDecimal qty, BigDecimal piecePrice) {
        String rowKey = userId + "|" + prodName;
        rowUserIdMap.put(rowKey, userId);
        pivotMap.computeIfAbsent(rowKey, k -> new LinkedHashMap<>());
        BigDecimal[] existing = pivotMap.get(rowKey).get(stepName);
        if (existing == null) {
            existing = new BigDecimal[]{ BigDecimal.ZERO, piecePrice };
        }
        existing[0] = existing[0].add(qty);
        if (piecePrice.compareTo(existing[1]) > 0) existing[1] = piecePrice;
        pivotMap.get(rowKey).put(stepName, existing);
    }

}
