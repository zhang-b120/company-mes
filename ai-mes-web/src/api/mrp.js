import request from '@/utils/request'

// ====== 待运算订单 ======
export function getPlannedOrders() {
  return request.get('/mrp/planned-orders')
}

// ====== 需求池管理 ======
export function removeFromDemandPool(orderId) {
  return request.delete(`/mrp/demand-pool/${orderId}`)
}

// ====== MRP运算 ======
export function runMrp(data) {
  return request.post('/mrp/run', data)
}
export function getMrpRunPage(params) {
  return request.get('/mrp/runs', { params })
}
export function getMrpRunDetail(id) {
  return request.get(`/mrp/runs/${id}`)
}

// ====== 采购建议 ======
export function getPurchaseSuggestionPage(params) {
  return request.get('/mrp/purchase-suggestions', { params })
}
export function updatePurchaseSuggestion(id, data) {
  return request.put(`/mrp/purchase-suggestions/${id}`, data)
}
export function confirmPurchaseSuggestion(id) {
  return request.post(`/mrp/purchase-suggestions/${id}/confirm`)
}
export function batchConfirmPurchaseSuggestions(ids) {
  return request.post('/mrp/purchase-suggestions/batch-confirm', { ids })
}

// ====== 生产建议 ======
export function getProductionSuggestionPage(params) {
  return request.get('/mrp/production-suggestions', { params })
}
export function updateProductionSuggestion(id, data) {
  return request.put(`/mrp/production-suggestions/${id}`, data)
}

// ====== 忽略建议 ======
export function ignoreSuggestion(type, id) {
  return request.post(`/mrp/suggestions/${type}/${id}/ignore`)
}

// ====== 生产计划 ======
export function getProductionPlanPage(params) {
  return request.get('/production-plan/page', { params })
}
export function getProductionPlanDetail(id) {
  return request.get(`/production-plan/${id}`)
}
export function createProductionPlan(data) {
  return request.post('/production-plan', data)
}
export function updateProductionPlan(id, data) {
  return request.put(`/production-plan/${id}`, data)
}
export function confirmProductionPlan(id) {
  return request.post(`/production-plan/${id}/confirm`)
}
export function confirmAndStartPlan(id) {
  return request.post(`/production-plan/${id}/confirm-and-start`)
}
export function cancelProductionPlan(id) {
  return request.post(`/production-plan/${id}/cancel`)
}
export function batchConfirmAndStartPlans(ids) {
  return request.post('/production-plan/batch-confirm-and-start', { ids })
}
export function batchCancelPlans(ids) {
  return request.post('/production-plan/batch-cancel', { ids })
}

// ====== 计划行项 ======
export function addPlanItem(planId, data) {
  return request.post(`/production-plan/${planId}/items`, data)
}
export function updatePlanItem(itemId, data) {
  return request.put(`/production-plan/items/${itemId}`, data)
}
export function deletePlanItem(itemId) {
  return request.delete(`/production-plan/items/${itemId}`)
}

// ====== 工序指派配置 ======
export function saveItemStepConfigs(itemId, steps) {
  return request.post(`/production-plan/items/${itemId}/step-configs`, steps)
}
export function getItemStepConfigs(itemId) {
  return request.get(`/production-plan/items/${itemId}/step-configs`)
}

// ====== 从MRP生产建议创建计划 ======
export function createPlanFromSuggestions(data) {
  return request.post('/production-plan/from-suggestions', data)
}

// ====== 物料齐套检查 ======
export function checkPlanMaterial(planId) {
  return request.get(`/production-plan/${planId}/check-material`)
}

// ====== 行项物料锁定查询 ======
export function getPlanItemMaterials(itemId) {
  return request.get(`/production-plan/items/${itemId}/materials`)
}

// ====== 下达工单 ======
export function releaseWorkOrder(itemId, data) {
  return request.post(`/production-plan/items/${itemId}/release`, data)
}
export function batchReleaseWorkOrders(itemIds) {
  return request.post('/production-plan/items/batch-release', { itemIds })
}

// ====== 统计 ======
export function getMrpStatistics() {
  return request.get('/mrp/statistics')
}
export function getPlanStatistics() {
  return request.get('/production-plan/statistics')
}

// ====== 计划附件 ======
export function getPlanAttachments(planId) {
  return request.get(`/production-plan/${planId}/attachments`)
}
export function uploadPlanAttachment(planId, file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post(`/production-plan/${planId}/attachments`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
export function deletePlanAttachment(id) {
  return request.delete(`/production-plan/attachments/${id}`)
}

// ====== 导出 ======
export function exportPurchaseSuggestions(params) {
  return request.get('/mrp/purchase-suggestions/export', { params, responseType: 'blob' })
}
export function exportProductionSuggestions(params) {
  return request.get('/mrp/production-suggestions/export', { params, responseType: 'blob' })
}
export function exportProductionPlan(planId) {
  return request.get(`/production-plan/${planId}/export`, { responseType: 'blob' })
}
export function exportProductionPlansBatch(ids) {
  return request.get('/production-plan/export-batch', { params: { ids }, responseType: 'blob' })
}
