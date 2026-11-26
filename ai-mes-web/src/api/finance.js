import request from '@/utils/request'

export function getReceivablePage(params) {
  return request.get('/finance/receivables/page', { params })
}
export function getPayablePage(params) {
  return request.get('/finance/payables/page', { params })
}
export function exportReceivables(params) {
  return request.get('/finance/receivables/export', { params, responseType: 'blob' })
}
export function exportReceivablesByPeriod(params) {
  return request.get('/finance/receivables/export-by-period', { params, responseType: 'blob' })
}
export function exportPayables(params) {
  return request.get('/finance/payables/export', { params, responseType: 'blob' })
}
export function exportWoCosts(params) {
  return request.get('/finance/wo-costs/export', { params, responseType: 'blob' })
}
export function exportOrderProfits(params) {
  return request.get('/finance/order-profits/export', { params, responseType: 'blob' })
}
export function exportPayments(params) {
  return request.get('/finance/payments/export', { params, responseType: 'blob' })
}
export function createReceivable(data) {
  return request.post('/finance/receivables', data)
}
export function createPayable(data) {
  return request.post('/finance/payables', data)
}
export function addPayment(data) {
  return request.post('/finance/payments', data)
}
export function getWoCost(workOrderId) {
  return request.get(`/finance/wo-costs/${workOrderId}`)
}
export function getWagePage(params) {
  return request.get('/finance/wages/page', { params })
}
export function createWage(data) {
  return request.post('/finance/wages', data)
}
export function confirmWage(id) {
  return request.put(`/finance/wages/${id}/confirm`)
}
export function approveWage(id) {
  return request.put(`/finance/wages/${id}/approve`)
}
export function payWage(id) {
  return request.put(`/finance/wages/${id}/pay`)
}
export function rejectWage(id) {
  return request.put(`/finance/wages/${id}/reject`)
}
export function generateMonthlyWages(month) {
  return request.post('/finance/wages/generate', { month })
}
export function exportWages(params) {
  return request.get('/finance/wages/export', { params, responseType: 'blob' })
}
export function getWageDetail(id) {
  return request.get(`/finance/wages/${id}`)
}
export function batchConfirmWages(ids) {
  return request.put('/finance/wages/batch-confirm', { ids })
}
export function batchApproveWages(ids) {
  return request.put('/finance/wages/batch-approve', { ids })
}
export function batchPayWages(ids) {
  return request.put('/finance/wages/batch-pay', { ids })
}
export function getOrderProfit(orderId) {
  return request.get(`/finance/order-profit/${orderId}`)
}
export function getProfitAnalysis(params) {
  return request.get('/finance/profit-analysis', { params })
}
export function getWoCostPage(params) {
  return request.get('/finance/wo-costs/page', { params })
}
export function getWoCostDetail(id) {
  return request.get(`/finance/wo-costs/${id}`)
}
export function getOrderProfitPage(params) {
  return request.get('/finance/order-profits/page', { params })
}
export function getPaymentPage(params) {
  return request.get('/finance/payments/page', { params })
}
export function createPayment(data) {
  return request.post('/finance/payments', data)
}

// 会计科目
export function getAccountList(params) {
  return request.get('/finance/accounts', { params })
}
export function createAccount(data) {
  return request.post('/finance/accounts', data)
}
export function updateAccount(id, data) {
  return request.put(`/finance/accounts/${id}`, data)
}
export function deleteAccount(id) {
  return request.delete(`/finance/accounts/${id}`)
}

// 记账凭证
export function getVoucherPage(params) {
  return request.get('/finance/vouchers/page', { params })
}
export function getVoucherDetail(id) {
  return request.get(`/finance/vouchers/${id}`)
}
export function createVoucher(data) {
  return request.post('/finance/vouchers', data)
}
export function postVoucher(id) {
  return request.put(`/finance/vouchers/${id}/post`)
}
export function deleteVoucher(id) {
  return request.delete(`/finance/vouchers/${id}`)
}

// 账簿查询
export function getTrialBalance(params) {
  return request.get('/finance/trial-balance', { params })
}

// 财务报表
export function getIncomeStatement(params) {
  return request.get('/finance/statements/income', { params })
}
export function getBalanceSheet(params) {
  return request.get('/finance/statements/balance-sheet', { params })
}

// 凭证冲红
export function reverseVoucher(id) {
  return request.put(`/finance/vouchers/${id}/reverse`)
}

// 期初余额
export function setOpeningBalance(id, openingBalance) {
  return request.put(`/finance/accounts/${id}/opening-balance`, { openingBalance })
}

// 明细账
export function getLedger(params) {
  return request.get('/finance/ledger', { params })
}

// 期末结账
export function periodClose(period) {
  return request.post('/finance/period-close', { period })
}
export function getPeriodCloseList() {
  return request.get('/finance/period-close/list')
}

// ========== 往来发票 ==========
export function getInvoicePage(params) {
  return request.get('/finance/invoices/page', { params })
}
export function exportInvoices(params) {
  return request.get('/finance/invoices/export', { params, responseType: 'blob' })
}
export function createInvoice(data) {
  return request.post('/finance/invoices', data)
}
export function updateInvoice(id, data) {
  return request.put(`/finance/invoices/${id}`, data)
}
export function deleteInvoice(id) {
  return request.delete(`/finance/invoices/${id}`)
}

// 员工报工分析
export function getReportAnalysis(params) {
  return request.get('/finance/report-analysis', { params })
}
export function getReportAnalysisList(params) {
  return request.get('/finance/report-analysis/list', { params })
}
export function getReportAnalysisSummary(params) {
  return request.get('/finance/report-analysis/summary', { params })
}
export function exportReportAnalysis(params) {
  return request.get('/finance/report-analysis/export', { params, responseType: 'blob' })
}
