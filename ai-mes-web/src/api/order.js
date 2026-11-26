import request from '@/utils/request'

export function getSalesOrderStatistics() {
  return request.get('/order/sales-orders/statistics')
}
export function getSalesOrderPage(params) {
  return request.get('/order/sales-orders/page', { params })
}
export function getSalesOrderDetail(id) {
  return request.get(`/order/sales-orders/${id}`)
}
export function getSalesOrderDetailByNo(orderNo) {
  return request.get('/order/sales-orders/by-no', { params: { orderNo } })
}
export function createSalesOrder(data) {
  return request.post('/order/sales-orders', data)
}
export function updateSalesOrder(id, data) {
  return request.put(`/order/sales-orders/${id}`, data)
}
export function updateSalesOrderStatus(id, status) {
  return request.put(`/order/sales-orders/${id}/status`, { status })
}
export function approveSalesOrder(id, remark) {
  return request.put(`/order/sales-orders/${id}/approve`, { remark })
}
export function submitSalesOrder(id) {
  return request.put(`/order/sales-orders/${id}/submit`)
}
export function rejectSalesOrder(id, remark) {
  return request.put(`/order/sales-orders/${id}/reject`, { remark })
}
export function cancelSalesOrder(id, reason) {
  return request.put(`/order/sales-orders/${id}/cancel`, { reason })
}
export function deleteSalesOrder(id) {
  return request.delete(`/order/sales-orders/${id}`)
}
export function voidSalesOrder(id, reason) {
  return request.put(`/order/sales-orders/${id}/void`, { reason })
}
export function completeSalesOrder(id) {
  return request.put(`/order/sales-orders/${id}/complete`)
}
export function convertSalesOrderToPlan(id) {
  return request.put(`/order/sales-orders/${id}/convert-to-plan`)
}
export function convertSalesOrderToProductionPlan(id) {
  return request.put(`/order/sales-orders/${id}/convert-to-production-plan`)
}

export function exportSalesOrders(ids) {
  const params = ids && ids.length ? { ids: ids.join(',') } : {}
  return request.get('/order/sales-orders/export', { params, responseType: 'blob' })
}

export function importSalesOrders(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/order/sales-orders/import', formData)
}

export function downloadOrderImportTemplate() {
  return request.get('/order/sales-orders/import-template', { responseType: 'blob' })
}

export function getProductionDetail(id) {
  return request.get(`/order/sales-orders/${id}/production-detail`)
}

// 信用额度校验
export function checkCreditLimit(customerId, amount) {
  return request.get('/order/sales-orders/credit-check', { params: { customerId, amount } })
}

// 价格历史
export function getPriceHistory(productId, customerId) {
  return request.get('/order/sales-orders/price-history', { params: { productId, customerId } })
}

// 批量操作
export function batchSubmitOrders(ids) {
  return request.post('/order/sales-orders/batch-submit', { ids })
}
export function batchDeleteOrders(ids) {
  return request.post('/order/sales-orders/batch-delete', { ids })
}
export function batchVoidOrders(ids, reason) {
  return request.post('/order/sales-orders/batch-void', { ids, reason })
}
export function batchConvertToWorkOrder(ids) {
  return request.post('/order/sales-orders/batch-to-work-order', { ids })
}
export function batchConvertToOutsource(data) {
  return request.post('/order/sales-orders/batch-to-outsource', data)
}

// 发货记录
export function getShipmentRecords(orderId) {
  return request.get(`/order/sales-orders/${orderId}/shipments`)
}
export function createShipmentRecord(orderId, data) {
  return request.post(`/order/sales-orders/${orderId}/shipments`, data)
}
export function deleteShipmentRecord(shipmentId) {
  return request.delete(`/order/sales-orders/shipments/${shipmentId}`)
}

// 发货出库任务
export function createShipmentTask(orderId, data) {
  return request.post(`/order/sales-orders/${orderId}/shipment-task`, data)
}

// 发票信息
export function getOrderInvoices(orderId) {
  return request.get(`/order/sales-orders/${orderId}/invoices`)
}
export function createOrderInvoice(orderId, data) {
  return request.post(`/order/sales-orders/${orderId}/invoices`, data)
}
export function updateOrderInvoice(invoiceId, data) {
  return request.put(`/order/sales-orders/invoices/${invoiceId}`, data)
}
export function deleteOrderInvoice(invoiceId) {
  return request.delete(`/order/sales-orders/invoices/${invoiceId}`)
}
export function uploadInvoiceAttachment(file) {
  const form = new FormData()
  form.append('file', file)
  return request.post('/order/sales-orders/invoices/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// 订单变更 (新流程: create → saveDraft+ → submit → approve)
export function getOrderChanges(params) {
  return request.get('/order/changes', { params })
}
export function getOrderChangeDetail(id) {
  return request.get(`/order/changes/${id}`)
}
export function createOrderChange(data) {
  // 创建变更; 后端拍快照 status=draft, 返回 changeId, 前端跳编辑页继续
  return request.post('/order/changes', data)
}
export function saveOrderChangeDraft(id, payload) {
  // 编辑页"保存草稿"用; payload = { order: {...}, items: [...] }; 允许多次覆盖
  return request.put(`/order/changes/${id}/draft`, payload)
}
export function submitOrderChange(id) {
  // 编辑完点"提交变更审批"调用此接口; status draft→pending, 启动通用审批流
  return request.put(`/order/changes/${id}/submit`)
}
export function discardOrderChangeDraft(id) {
  return request.delete(`/order/changes/${id}/draft`)
}
export function getOrderChangeDiff(id) {
  // 审批页 / 变更记录 tab 用: 返回 original 快照 + proposed 新状态
  return request.get(`/order/changes/${id}/diff`)
}
export function approveOrderChange(id) {
  return request.put(`/order/changes/${id}/approve`)
}
export function rejectOrderChange(id, remark) {
  return request.put(`/order/changes/${id}/reject`, { remark })
}

// ==================== 订单附件 ====================
export function getOrderAttachments(orderId) {
  return request.get(`/order/sales-orders/${orderId}/attachments`)
}
export function uploadOrderAttachment(orderId, file) {
  const form = new FormData()
  form.append('file', file)
  return request.post(`/order/sales-orders/${orderId}/attachments`, form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
export function deleteOrderAttachment(attachmentId) {
  return request.delete(`/order/sales-orders/attachments/${attachmentId}`)
}

// ====== 订单跟进记录 ======
export function getOrderFollowups(orderId) {
  return request.get(`/order/sales-orders/${orderId}/followups`)
}
export function createOrderFollowup(orderId, data) {
  return request.post(`/order/sales-orders/${orderId}/followups`, data)
}
export function deleteOrderFollowup(followupId) {
  return request.delete(`/order/sales-orders/followups/${followupId}`)
}

// ====== 发货统计 ======
export function getShipmentStatisticsPage(params) {
  return request.get('/order/shipment-statistics/page', { params })
}
export function exportShipmentStatistics(params) {
  return request.get('/order/shipment-statistics/export', { params, responseType: 'blob' })
}

// ====== 订单操作日志 ======
export function getOrderLogs(orderId) {
  return request.get(`/order/sales-orders/${orderId}/logs`)
}

// ====== 退货单 ======
export function getReturnOrderPage(params) {
  return request.get('/order/return-orders/page', { params })
}
export function getReturnOrderDetail(id) {
  return request.get(`/order/return-orders/${id}`)
}
export function createReturnOrder(data) {
  return request.post('/order/return-orders', data)
}
export function approveReturnOrder(id) {
  return request.put(`/order/return-orders/${id}/approve`)
}
export function rejectReturnOrder(id, reason) {
  return request.put(`/order/return-orders/${id}/reject`, { reason })
}
export function receiveReturnOrder(id, items) {
  return request.put(`/order/return-orders/${id}/receive`, { items })
}
export function completeReturnOrder(id) {
  return request.put(`/order/return-orders/${id}/complete`)
}
