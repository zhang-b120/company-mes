import request from '@/utils/request'

// 采购申请
export function getPurchaseRequestPage(params) {
  return request.get('/purchase/requests/page', { params })
}
export function createPurchaseRequest(data) {
  return request.post('/purchase/requests', data)
}
export function approvePurchaseRequest(id, data) {
  return request.put(`/purchase/requests/${id}/approve`, data)
}
export function rejectPurchaseRequest(id, data) {
  return request.put(`/purchase/requests/${id}/reject`, data)
}
export function deletePurchaseRequest(id) {
  return request.delete(`/purchase/requests/${id}`)
}
export function updatePurchaseRequestDraft(id, data) {
  return request.put(`/purchase/requests/${id}`, data)
}
export function submitPurchaseRequestDraft(id) {
  return request.put(`/purchase/requests/${id}/submit`)
}
export function issueToOrder(data) {
  return request.post('/purchase/requests/issue-to-order', data)
}

export function getPurchaseRequestDetail(id) {
  return request.get(`/purchase/requests/${id}`)
}

// 采购订单
export function getPurchaseOrderPage(params) {
  return request.get('/purchase/orders/page', { params })
}
export function getPurchaseOrderDetail(id) {
  return request.get(`/purchase/orders/${id}`)
}
export function getPurchaseOrderDetailByNo(poNo) {
  return request.get('/purchase/orders/by-no', { params: { poNo } })
}
export function getPurchasePriceHistory(params) {
  return request.get('/purchase/orders/price-history', { params })
}
export function createPurchaseOrder(data) {
  return request.post('/purchase/orders', data)
}
export function updatePurchaseOrder(id, data) {
  return request.put(`/purchase/orders/${id}`, data)
}
export function addPurchaseOrderItem(id, data) {
  return request.post(`/purchase/orders/${id}/items`, data)
}
export function submitPurchaseOrder(id) {
  return request.put(`/purchase/orders/${id}/submit`)
}
export function approvePurchaseOrder(id, data) {
  return request.put(`/purchase/orders/${id}/approve`, data)
}
export function rejectPurchaseOrder(id, data) {
  return request.put(`/purchase/orders/${id}/reject`, data)
}
export function createPurchaseReceipt(id, data) {
  return request.post(`/purchase/orders/${id}/receipt`, data)
}
export function createBatchPurchaseReceipt(id, data) {
  return request.post(`/purchase/orders/${id}/batch-receipt`, data)
}
export function createPOInvRequest(id) {
  return request.post(`/purchase/orders/${id}/inv-request`)
}
export function createPOAutoTask(id) {
  return request.post(`/purchase/orders/${id}/auto-task`)
}
export function closePurchaseOrder(id) {
  return request.put(`/purchase/orders/${id}/close`)
}
export function voidPurchaseOrder(id) {
  return request.put(`/purchase/orders/${id}/void`)
}
export function createReturnFromOrder(id, data) {
  return request.post(`/purchase/orders/${id}/return`, data)
}
export function previewReturnFromOrder(orderId) {
  return request.get(`/purchase/returns/preview-from-order/${orderId}`)
}

// 采购收料单
export function getPurchaseReceiptPage(params) {
  return request.get('/purchase/receipts/page', { params })
}
export function exportPurchaseReceipts(params) {
  return request.get('/purchase/receipts/export', { params, responseType: 'blob' })
}
export function getReceiptDetail(id) {
  return request.get(`/purchase/receipts/${id}`)
}
export function getReceiptsByGroup(groupId) {
  return request.get(`/purchase/receipts/group/${groupId}`)
}
export function printReceiptGroup(groupId) {
  return request.get(`/purchase/receipts/group/${groupId}/print`, { responseType: 'blob' })
}
// hiprint 用: 取批次组打印数据 JSON
export function getReceiptGroupPrintData(groupId) {
  return request.get(`/purchase/receipts/group/${groupId}/print-data`)
}
// 整组汇总收料: body = { warehouseId, needQc }
export function batchReceiveByGroup(groupId, data) {
  return request.post(`/purchase/receipts/group/${groupId}/batch-receive`, data)
}
export function receiveReceipt(id, data) {
  return request.post(`/purchase/receipts/${id}/receive`, data)
}
export function closeReceipt(id) {
  return request.put(`/purchase/receipts/${id}/close`)
}
export function getReceiptQcTasks(id) {
  return request.get(`/purchase/receipts/${id}/qc-tasks`)
}
export function getReceiptInvTasks(id) {
  return request.get(`/purchase/receipts/${id}/inv-tasks`)
}
// 生成采购收料单
export function generatePurchaseReceipts(orderId, data) {
  return request.post(`/purchase/orders/${orderId}/generate-receipts`, data)
}

export function confirmPurchaseReceipt(data) {
  return request.post(`/purchase/receipts/${data.id}/receive`, data)
}
export function handleReceiptQcFail(id, data) {
  return request.post(`/purchase/receipts/${id}/qc-fail`, data)
}

// 采购退货
export function getPurchaseReturnPage(params) {
  return request.get('/purchase/returns/page', { params })
}
export function getPurchaseReturnDetail(id) {
  return request.get(`/purchase/returns/${id}`)
}
export function createPurchaseReturn(data) {
  return request.post('/purchase/returns', data)
}
export function createPurchaseReturnFromOrder(orderId) {
  return request.post(`/purchase/returns/from-purchase-order/${orderId}`)
}
export function updatePurchaseReturn(id, data) {
  return request.put(`/purchase/returns/${id}`, data)
}
export function submitPurchaseReturn(id) {
  return request.put(`/purchase/returns/${id}/submit`)
}
export function approvePurchaseReturn(id, data) {
  return request.put(`/purchase/returns/${id}/approve`, data)
}
export function rejectPurchaseReturn(id, data) {
  return request.put(`/purchase/returns/${id}/reject`, data)
}
export function generateReturnInvTask(id, data) {
  return request.post(`/purchase/returns/${id}/inv-task`, data)
}
export function batchGenerateReturnInvTask(data) {
  return request.post('/purchase/returns/batch-inv-task', data)
}

// 采购分析
export function getPurchaseAnalysisSupplier(params) {
  return request.get('/purchase/analysis/supplier', { params })
}
export function getPurchaseAnalysisMaterial(params) {
  return request.get('/purchase/analysis/material', { params })
}
export function exportPurchaseAnalysis(params) {
  return request.get('/purchase/analysis/export', { params, responseType: 'blob' })
}
