package com.aimes.modules.finance.controller;

import com.aimes.common.annotation.DataScope;
import com.aimes.common.result.PageResult;
import com.aimes.common.result.R;
import com.aimes.common.utils.ExcelUtil;
import com.aimes.common.utils.SecurityUtil;
import com.aimes.modules.finance.entity.*;
import com.aimes.modules.finance.mapper.BizWoCostMapper;
import com.aimes.modules.finance.service.BizFinanceService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.function.Function;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/finance")
@RequiredArgsConstructor
public class BizFinanceController {

    private final BizFinanceService financeService;
    private final BizWoCostMapper woCostMapper;

    @DataScope(mapperClass = "com.aimes.modules.finance.mapper.BizReceivableMapper")
    @GetMapping("/receivables/page")
    public R<PageResult<Map<String, Object>>> receivablePage(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String receivableNo,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) String dueStart,
            @RequestParam(required = false) String dueEnd,
            @RequestParam(required = false) String asOfDate,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(financeService.getReceivablePage(keyword, receivableNo, orderNo, customerName, status,
                customerId, orderId, dueStart, dueEnd, asOfDate, pageNum, pageSize));
    }

    @DataScope(mapperClass = "com.aimes.modules.finance.mapper.BizPayableMapper")
    @GetMapping("/payables/page")
    public R<PageResult<Map<String, Object>>> payablePage(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String payableNo,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String supplierName,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long sourceId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(financeService.getPayablePage(keyword, payableNo, orderNo, supplierName, sourceType, status, sourceId, pageNum, pageSize));
    }

    @PostMapping("/receivables")
    public R<Void> createReceivable(@RequestBody BizReceivable receivable) {
        financeService.createReceivable(receivable);
        return R.ok();
    }

    @PostMapping("/payables")
    public R<Void> createPayable(@RequestBody BizPayable payable) {
        financeService.createPayable(payable);
        return R.ok();
    }

    @PostMapping("/payments")
    public R<Void> addPayment(@RequestBody BizPaymentRecord payment) {
        financeService.addPayment(payment);
        return R.ok();
    }

    @DataScope(mapperClass = "com.aimes.modules.finance.mapper.BizPaymentRecordMapper")
    @GetMapping("/payments/page")
    public R<PageResult<Map<String, Object>>> paymentPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String relatedType,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(financeService.getPaymentPage(keyword, relatedType, pageNum, pageSize));
    }

    @GetMapping("/wo-cost/{workOrderId}")
    public R<BizWoCost> getWoCost(@PathVariable Long workOrderId) {
        return R.ok(financeService.getWoCost(workOrderId));
    }

    @GetMapping("/wo-costs/page")
    public R<PageResult<Map<String, Object>>> woCostPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(financeService.getWoCostPage(pageNum, pageSize));
    }

    @GetMapping("/wo-costs/{id}")
    public R<BizWoCost> woCostDetail(@PathVariable Long id) {
        return R.ok(woCostMapper.selectById(id));
    }

    @GetMapping("/wages/page")
    public R<PageResult<Map<String, Object>>> wagePage(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(financeService.getWagePage(month, keyword, status, pageNum, pageSize));
    }

    @GetMapping("/wages/{id}")
    public R<BizWageMonthly> wageDetail(@PathVariable Long id) {
        return R.ok(financeService.getWageDetail(id));
    }

    @PostMapping("/wages")
    public R<Void> createWage(@RequestBody BizWageMonthly wage) {
        financeService.createWage(wage);
        return R.ok();
    }

    @PutMapping("/wages/{id}/confirm")
    public R<Void> confirmWage(@PathVariable Long id) {
        financeService.confirmWage(id, SecurityUtil.getCurrentUserId());
        return R.ok();
    }

    @PutMapping("/wages/{id}/approve")
    public R<Void> approveWage(@PathVariable Long id) {
        financeService.approveWage(id, SecurityUtil.getCurrentUserId());
        return R.ok();
    }

    @PutMapping("/wages/{id}/pay")
    public R<Void> payWage(@PathVariable Long id) {
        financeService.payWage(id);
        return R.ok();
    }

    @PutMapping("/wages/{id}/reject")
    public R<Void> rejectWage(@PathVariable Long id) {
        financeService.rejectWage(id);
        return R.ok();
    }

    @PostMapping("/wages/generate")
    public R<Void> generateMonthlyWages(@RequestBody Map<String, String> body) {
        financeService.generateMonthlyWages(body.get("month"));
        return R.ok();
    }

    @GetMapping("/wages/export")
    public R<List<BizWageMonthly>> exportWages(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String status) {
        return R.ok(financeService.exportWages(month, status));
    }

    @PutMapping("/wages/batch-confirm")
    public R<Void> batchConfirmWages(@RequestBody Map<String, List<Long>> body) {
        List<Long> ids = body.get("ids");
        if (ids != null) {
            ids.forEach(id -> financeService.confirmWage(id, SecurityUtil.getCurrentUserId()));
        }
        return R.ok();
    }

    @PutMapping("/wages/batch-approve")
    public R<Void> batchApproveWages(@RequestBody Map<String, List<Long>> body) {
        List<Long> ids = body.get("ids");
        if (ids != null) {
            ids.forEach(id -> financeService.approveWage(id, SecurityUtil.getCurrentUserId()));
        }
        return R.ok();
    }

    @PutMapping("/wages/batch-pay")
    public R<Void> batchPayWages(@RequestBody Map<String, List<Long>> body) {
        List<Long> ids = body.get("ids");
        if (ids != null) {
            ids.forEach(financeService::payWage);
        }
        return R.ok();
    }

    @GetMapping("/order-profit/{orderId}")
    public R<Map<String, Object>> orderProfit(@PathVariable Long orderId) {
        return R.ok(financeService.getOrderProfit(orderId));
    }

    @GetMapping("/order-profits/page")
    public R<Map<String, Object>> orderProfitPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(financeService.getOrderProfitPage(pageNum, pageSize));
    }

    @GetMapping("/profit-analysis")
    public R<Map<String, Object>> profitAnalysis(
            @RequestParam(defaultValue = "month") String dimension,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return R.ok(financeService.getProfitAnalysis(dimension, startDate, endDate));
    }

    // ===================== 导出 =====================

    @DataScope(mapperClass = "com.aimes.modules.finance.mapper.BizReceivableMapper")
    @GetMapping("/receivables/export-by-period")
    public void exportReceivablesByPeriod(
            @RequestParam(defaultValue = "month") String granularity,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            HttpServletResponse response) throws Exception {
        financeService.exportReceivablesByPeriod(granularity, startDate, endDate, response);
    }

    @DataScope(mapperClass = "com.aimes.modules.finance.mapper.BizReceivableMapper")
    @GetMapping("/receivables/export")
    public void exportReceivables(@RequestParam(required = false) String keyword,
                                   @RequestParam(required = false) String receivableNo,
                                   @RequestParam(required = false) String orderNo,
                                   @RequestParam(required = false) String customerName,
                                   @RequestParam(required = false) String status,
                                   @RequestParam(required = false) Long customerId,
                                   @RequestParam(required = false) Long orderId,
                                   @RequestParam(required = false) String dueStart,
                                   @RequestParam(required = false) String dueEnd,
                                   @RequestParam(required = false) String asOfDate,
                                   HttpServletResponse response) throws Exception {
        var page = financeService.getReceivablePage(keyword, receivableNo, orderNo, customerName, status,
                customerId, orderId, dueStart, dueEnd, asOfDate, 1, 100000);
        List<Map<String, Object>> records = new java.util.ArrayList<>(page.getRecords());
        // 末行汇总: 应收 / 已收 / 未收
        java.math.BigDecimal sumA = java.math.BigDecimal.ZERO, sumR = java.math.BigDecimal.ZERO, sumU = java.math.BigDecimal.ZERO;
        for (var r : records) {
            Object a = r.get("amount"); Object rv = r.get("receivedAmount"); Object u = r.get("unreceivedAmount");
            if (a != null) sumA = sumA.add(new java.math.BigDecimal(a.toString()));
            if (rv != null) sumR = sumR.add(new java.math.BigDecimal(rv.toString()));
            if (u != null) sumU = sumU.add(new java.math.BigDecimal(u.toString()));
        }
        if (!records.isEmpty()) {
            Map<String, Object> total = new java.util.LinkedHashMap<>();
            total.put("receivableNo", "合计"); total.put("amount", sumA);
            total.put("receivedAmount", sumR); total.put("unreceivedAmount", sumU);
            records.add(total);
        }
        List<String> headers = List.of("应收单号", "关联订单", "客户", "应收金额", "已收金额", "未收金额", "到期日", "状态", "创建时间");
        List<Function<Map<String, Object>, Object>> ex = List.of(
                m -> m.get("receivableNo"), m -> m.get("orderNo"), m -> m.get("customerName"),
                m -> m.get("amount"), m -> m.get("receivedAmount"), m -> m.get("unreceivedAmount"),
                m -> m.get("dueDate"), m -> m.get("status"), m -> m.get("createTime"));
        ExcelUtil.export(response, "应收明细", headers, records, ex);
    }

    @DataScope(mapperClass = "com.aimes.modules.finance.mapper.BizPayableMapper")
    @GetMapping("/payables/export")
    public void exportPayables(@RequestParam(required = false) String keyword,
                                @RequestParam(required = false) String payableNo,
                                @RequestParam(required = false) String orderNo,
                                @RequestParam(required = false) String supplierName,
                                @RequestParam(required = false) String sourceType,
                                @RequestParam(required = false) String status,
                                @RequestParam(required = false) Long sourceId,
                                HttpServletResponse response) throws Exception {
        var page = financeService.getPayablePage(keyword, payableNo, orderNo, supplierName, sourceType, status, sourceId, 1, 100000);
        List<String> headers = List.of("应付单号", "关联单据", "供应商", "应付金额", "已付金额", "未付金额", "到期日", "状态", "创建时间");
        List<Function<Map<String, Object>, Object>> ex = List.of(
                m -> m.get("payableNo"), m -> m.get("sourceNo"), m -> m.get("supplierName"),
                m -> m.get("amount"), m -> m.get("paidAmount"), m -> m.get("unpaidAmount"),
                m -> m.get("dueDate"), m -> m.get("status"), m -> m.get("createTime"));
        ExcelUtil.export(response, "应付明细", headers, page.getRecords(), ex);
    }

    @DataScope(mapperClass = "com.aimes.modules.finance.mapper.BizPaymentRecordMapper")
    @GetMapping("/payments/export")
    public void exportPayments(@RequestParam(required = false) String keyword,
                                @RequestParam(required = false) String relatedType,
                                HttpServletResponse response) throws Exception {
        var page = financeService.getPaymentPage(keyword, relatedType, 1, 100000);
        List<String> headers = List.of("流水号", "关联类型", "关联单号", "金额", "付款方式", "付款日期", "备注", "创建时间");
        List<Function<Map<String, Object>, Object>> ex = List.of(
                m -> m.get("paymentNo"), m -> m.get("relatedType"), m -> m.get("relatedOrderNo"),
                m -> m.get("amount"), m -> m.get("paymentMethod"), m -> m.get("paymentDate"),
                m -> m.get("remark"), m -> m.get("createTime"));
        ExcelUtil.export(response, "收付款流水", headers, page.getRecords(), ex);
    }

    @GetMapping("/wo-costs/export")
    public void exportWoCosts(@RequestParam(required = false) String workOrderNo,
                               HttpServletResponse response) throws Exception {
        var page = financeService.getWoCostPage(1, 100000);
        List<String> headers = List.of("工单号", "产品名称", "材料成本", "人工成本", "外协成本", "制造费用", "合计成本");
        List<Function<Map<String, Object>, Object>> ex = List.of(
                m -> m.get("workOrderNo"), m -> m.get("productName"),
                m -> m.get("materialCost"), m -> m.get("laborCost"),
                m -> m.get("outsourceCost"), m -> m.get("overheadCost"), m -> m.get("totalCost"));
        List<Map<String, Object>> records = page.getRecords();
        if (workOrderNo != null && !workOrderNo.isBlank()) {
            String kw = workOrderNo.trim();
            records = records.stream()
                    .filter(m -> m.get("workOrderNo") != null && m.get("workOrderNo").toString().contains(kw))
                    .collect(java.util.stream.Collectors.toList());
        }
        ExcelUtil.export(response, "工单成本", headers, records, ex);
    }

    @GetMapping("/order-profits/export")
    public void exportOrderProfits(HttpServletResponse response) throws Exception {
        Map<String, Object> page = financeService.getOrderProfitPage(1, 100000);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) page.getOrDefault("records", List.of());
        List<String> headers = List.of("订单编号", "客户", "订单金额", "材料成本", "人工成本", "外协成本", "总成本", "毛利", "毛利率");
        List<Function<Map<String, Object>, Object>> ex = List.of(
                m -> m.get("orderNo"), m -> m.get("customerName"),
                m -> m.get("orderAmount"), m -> m.get("materialCost"), m -> m.get("laborCost"),
                m -> m.get("outsourceCost"), m -> m.get("totalCost"),
                m -> m.get("grossProfit"), m -> m.get("grossMargin"));
        ExcelUtil.export(response, "订单利润", headers, data, ex);
    }

    // =====================================================================
    // 会计科目
    // =====================================================================

    @GetMapping("/accounts")
    public R<List<Map<String, Object>>> accountList(
            @RequestParam(required = false) String accountName,
            @RequestParam(required = false) String accountCode) {
        return R.ok(financeService.getAccountList(accountName, accountCode));
    }

    @PostMapping("/accounts")
    public R<Void> createAccount(@RequestBody AccAccount account) {
        financeService.createAccount(account);
        return R.ok();
    }

    @PutMapping("/accounts/{id}")
    public R<Void> updateAccount(@PathVariable Long id, @RequestBody AccAccount account) {
        financeService.updateAccount(id, account);
        return R.ok();
    }

    @DeleteMapping("/accounts/{id}")
    public R<Void> deleteAccount(@PathVariable Long id) {
        financeService.deleteAccount(id);
        return R.ok();
    }

    // =====================================================================
    // 记账凭证
    // =====================================================================

    @GetMapping("/vouchers/page")
    public R<PageResult<Map<String, Object>>> voucherPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(financeService.getVoucherPage(keyword, status, startDate, endDate, pageNum, pageSize));
    }

    @GetMapping("/vouchers/{id}")
    public R<Map<String, Object>> voucherDetail(@PathVariable Long id) {
        return R.ok(financeService.getVoucherDetail(id));
    }

    @PostMapping("/vouchers")
    public R<Void> createVoucher(@RequestBody VoucherCreateRequest req) {
        financeService.createVoucher(req.getVoucher(), req.getLines());
        return R.ok();
    }

    @PutMapping("/vouchers/{id}/post")
    public R<Void> postVoucher(@PathVariable Long id) {
        financeService.postVoucher(id);
        return R.ok();
    }

    @DeleteMapping("/vouchers/{id}")
    public R<Void> deleteVoucher(@PathVariable Long id) {
        financeService.deleteVoucher(id);
        return R.ok();
    }

    // =====================================================================
    // 账簿查询
    // =====================================================================

    @GetMapping("/trial-balance")
    public R<List<Map<String, Object>>> trialBalance(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return R.ok(financeService.getTrialBalance(startDate, endDate));
    }

    // =====================================================================
    // 财务报表
    // =====================================================================

    @GetMapping("/statements/income")
    public R<Map<String, Object>> incomeStatement(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return R.ok(financeService.getIncomeStatement(startDate, endDate));
    }

    @GetMapping("/statements/balance-sheet")
    public R<Map<String, Object>> balanceSheet(
            @RequestParam(required = false) String asOfDate) {
        return R.ok(financeService.getBalanceSheet(asOfDate));
    }

    // =====================================================================
    // 凭证冲红
    // =====================================================================

    @PutMapping("/vouchers/{id}/reverse")
    public R<Void> reverseVoucher(@PathVariable Long id) {
        financeService.reverseVoucher(id);
        return R.ok();
    }

    // =====================================================================
    // 期初余额
    // =====================================================================

    @PutMapping("/accounts/{id}/opening-balance")
    public R<Void> setOpeningBalance(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        java.math.BigDecimal amount = new java.math.BigDecimal(body.get("openingBalance").toString());
        financeService.setOpeningBalance(id, amount);
        return R.ok();
    }

    // =====================================================================
    // 明细账
    // =====================================================================

    @GetMapping("/ledger")
    public R<List<Map<String, Object>>> getLedger(
            @RequestParam Long accountId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return R.ok(financeService.getLedgerDetail(accountId, startDate, endDate));
    }

    // =====================================================================
    // 期末结账
    // =====================================================================

    @PostMapping("/period-close")
    public R<Void> periodClose(@RequestBody Map<String, String> body) {
        financeService.periodClose(body.get("period"), SecurityUtil.getCurrentUserId());
        return R.ok();
    }

    @GetMapping("/period-close/list")
    public R<List<Map<String, Object>>> periodCloseList() {
        return R.ok(financeService.getPeriodCloseList());
    }

    // ==================== 往来发票 ====================

    @GetMapping("/invoices/page")
    public R<PageResult<Map<String, Object>>> getInvoicePage(
            @RequestParam(required = false) String invoiceType,
            @RequestParam(required = false) String invoiceNo,
            @RequestParam(required = false) String buyerName,
            @RequestParam(required = false) String sellerName,
            @RequestParam(required = false) String relatedType,
            @RequestParam(required = false) String relatedNo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dateStart,
            @RequestParam(required = false) String dateEnd,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(financeService.getInvoicePage(invoiceType, invoiceNo, buyerName, sellerName,
                relatedType, relatedNo, status, dateStart, dateEnd, pageNum, pageSize));
    }

    @GetMapping("/invoices/export")
    public void exportInvoices(
            jakarta.servlet.http.HttpServletResponse response,
            @RequestParam(required = false) String invoiceType,
            @RequestParam(required = false) String invoiceNo,
            @RequestParam(required = false) String buyerName,
            @RequestParam(required = false) String sellerName,
            @RequestParam(required = false) String relatedType,
            @RequestParam(required = false) String relatedNo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dateStart,
            @RequestParam(required = false) String dateEnd) throws java.io.IOException {
        financeService.exportInvoices(response, invoiceType, invoiceNo, buyerName, sellerName,
                relatedType, relatedNo, status, dateStart, dateEnd);
    }

    @PostMapping("/invoices")
    public R<Void> createInvoice(@RequestBody BizFinanceInvoice invoice) {
        financeService.createInvoice(invoice);
        return R.ok();
    }

    @PutMapping("/invoices/{id}")
    public R<Void> updateInvoice(@PathVariable Long id, @RequestBody BizFinanceInvoice invoice) {
        financeService.updateInvoice(id, invoice);
        return R.ok();
    }

    @DeleteMapping("/invoices/{id}")
    public R<Void> deleteInvoice(@PathVariable Long id) {
        financeService.deleteInvoice(id);
        return R.ok();
    }

    @GetMapping("/report-analysis")
    public R<Map<String, Object>> reportAnalysis(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(required = false) String employeeName,
            @RequestParam(required = false) String processName) {
        return R.ok(financeService.getReportAnalysis(startDate, endDate, employeeName, processName));
    }

    @GetMapping("/report-analysis/list")
    public R<com.aimes.common.result.PageResult<Map<String, Object>>> reportAnalysisList(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String employeeName,
            @RequestParam(required = false) String processName,
            @RequestParam(required = false) String materialName,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(financeService.getReportAnalysisList(startDate, endDate, employeeName, processName, materialName, pageNum, pageSize));
    }

    @GetMapping("/report-analysis/summary")
    public R<Map<String, Object>> reportAnalysisSummary(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String employeeName,
            @RequestParam(required = false) String processName,
            @RequestParam(required = false) String materialName) {
        return R.ok(financeService.getReportAnalysisSummary(startDate, endDate, employeeName, processName, materialName));
    }

    @GetMapping("/report-analysis/export")
    public void reportAnalysisExport(
            jakarta.servlet.http.HttpServletResponse response,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String employeeName,
            @RequestParam(required = false) String processName,
            @RequestParam(required = false) String materialName) throws java.io.IOException {
        financeService.exportReportAnalysis(response, startDate, endDate, employeeName, processName, materialName);
    }

    @Data
    static class VoucherCreateRequest {
        private AccVoucher voucher;
        private List<AccVoucherLine> lines;
    }
}
