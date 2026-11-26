import request from '@/utils/request'

// 售后订单 CRUD
export function getServiceOrderPage(params) {
  return request.get('/aftersale/service-orders/page', { params })
}
export function getServiceOrderDetail(id) {
  return request.get(`/aftersale/service-orders/${id}`)
}
export function createServiceOrder(data) {
  return request.post('/aftersale/service-orders', data)
}
export function updateServiceOrder(id, data) {
  return request.put(`/aftersale/service-orders/${id}`, data)
}
export function cancelServiceOrder(id) {
  return request.put(`/aftersale/service-orders/${id}/cancel`)
}
export function completeServiceOrder(id) {
  return request.put(`/aftersale/service-orders/${id}/complete`)
}

// 配件
export function getServiceOrderParts(id) {
  return request.get(`/aftersale/service-orders/${id}/parts`)
}
export function addServiceOrderPart(id, data) {
  return request.post(`/aftersale/service-orders/${id}/parts`, data)
}
export function updateServiceOrderPart(partId, data) {
  return request.put(`/aftersale/service-orders/parts/${partId}`, data)
}
export function deleteServiceOrderPart(partId) {
  return request.delete(`/aftersale/service-orders/parts/${partId}`)
}

// 配件批量出库
export function issueServiceOrderParts(id, data) {
  return request.post(`/aftersale/service-orders/${id}/issue-parts`, data)
}

// 给配件关联生产工单 (workOrderId=null 解除关联)
export function linkServiceOrderPartWorkOrder(partId, workOrderId) {
  return request.put(`/aftersale/service-orders/parts/${partId}/link-work-order`, { workOrderId })
}

// 给配件新建生产工单 (一键, 自动关联回该配件)
export function createWorkOrderForServiceOrderPart(partId, data) {
  return request.post(`/aftersale/service-orders/parts/${partId}/create-work-order`, data || {})
}

// 该售后单的关联任务: 库存出库 + 生产工单
export function getServiceOrderRelatedTasks(id) {
  return request.get(`/aftersale/service-orders/${id}/related-tasks`)
}

// 客户统计
export function getServiceCustomerStatistics(params) {
  return request.get('/aftersale/service-orders/statistics/customer', { params })
}
