import request from '@/utils/request'

// ====== 外协申请（已废弃，保留兼容，勿删除——其他页面仍有引用） ======
export function getOutsourceRequestPage(params) {
  return request.get('/outsource/requests/page', { params })
}
export function createOutsourceRequest(data) {
  return request.post('/outsource/requests', data)
}
export function approveOutsourceRequest(id, data) {
  return request.put(`/outsource/requests/${id}/approve`, data)
}
export function rejectOutsourceRequest(id, data) {
  return request.put(`/outsource/requests/${id}/reject`, data)
}
export function getRequestConvertData(id) {
  return request.get(`/outsource/requests/${id}/convert-data`)
}

// ====== 外协加工单 ======
export function getOutsourceOrderPage(params) {
  return request.get('/outsource/orders/page', { params })
}
export function getOutsourceOrderDetail(id) {
  return request.get(`/outsource/orders/${id}`)
}
export function getOutsourceOrderDetailByNo(orderNo) {
  return request.get('/outsource/orders/by-no', { params: { orderNo } })
}
export function createOutsourceOrder(data) {
  return request.post('/outsource/orders', data)
}
export function updateOutsourceOrder(id, data) {
  return request.put(`/outsource/orders/${id}`, data)
}
export function submitOutsourceOrder(id) {
  return request.put(`/outsource/orders/${id}/submit`)
}
export function approveOutsourceOrder(id, data) {
  return request.put(`/outsource/orders/${id}/approve`, data)
}
export function rejectOutsourceOrder(id, data) {
  return request.put(`/outsource/orders/${id}/reject`, data)
}
export function confirmOutsourceOrder(id) {
  return request.put(`/outsource/orders/${id}/confirm`)
}
export function cancelOutsourceOrder(id) {
  return request.put(`/outsource/orders/${id}/cancel`)
}
export function createOutsourceDelivery(id, data) {
  return request.post(`/outsource/orders/${id}/delivery`, data)
}
export function createOutsourceReceipt(id, data) {
  return request.post(`/outsource/orders/${id}/receipt`, data)
}

// ====== 外协收料单 ======
export function getOutsourceReceiptPage(params) {
  return request.get('/outsource/receipts/page', { params })
}
export function getOutsourceReceiptDetail(id) {
  return request.get(`/outsource/receipts/${id}`)
}
export function receiveOutsourceReceipt(id, data) {
  return request.post(`/outsource/receipts/${id}/receive`, data)
}
export function closeOutsourceReceipt(id) {
  return request.put(`/outsource/receipts/${id}/close`)
}
export function getOutsourceReceiptQcTasks(id) {
  return request.get(`/outsource/receipts/${id}/qc-tasks`)
}
export function getOutsourceReceiptInvTasks(id) {
  return request.get(`/outsource/receipts/${id}/inv-tasks`)
}
// 生成外协收料单
export function generateOutsourceReceipts(orderId, data) {
  return request.post(`/outsource/orders/${orderId}/generate-receipts`, data)
}
