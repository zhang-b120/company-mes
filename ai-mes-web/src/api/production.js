import request from '@/utils/request'

// 生产工单
export function getWorkOrderPage(params) {
  return request.get('/production/work-orders/page', { params })
}
export function getWorkOrderDetail(id) {
  return request.get(`/production/work-orders/${id}`)
}
export function getWorkOrderDetailByNo(woNo) {
  return request.get('/production/work-orders/by-no', { params: { woNo } })
}
export function createWorkOrder(data) {
  return request.post('/production/work-orders', data)
}
export function appendWorkOrder(id, data) {
  return request.post(`/production/work-orders/${id}/append`, data)
}
export function updateWorkOrderStatus(id, status) {
  return request.put(`/production/work-orders/${id}/status`, { status })
}
export function startWorkOrder(id) {
  return request.post(`/production/work-orders/${id}/start`)
}
export function cancelWorkOrder(id) {
  return request.post(`/production/work-orders/${id}/cancel`)
}

// 工单统计
export function getWorkOrderStatistics() {
  return request.get('/production/work-orders/statistics')
}
export function getWorkOrderStats() {
  return request.get('/production/work-orders/stats')
}
// 工序任务列表
export function getWorkOrderTasks(woId) {
  return request.get(`/production/work-orders/${woId}/tasks`)
}
// 派工
export function assignTask(woId, taskId, data) {
  return request.put(`/production/work-orders/${woId}/tasks/${taskId}/assign`, data)
}
// 工序任务CRUD
export function addWorkOrderTask(woId, data) {
  return request.post(`/production/work-orders/${woId}/tasks`, data)
}
export function updateWorkOrderTask(woId, taskId, data) {
  return request.put(`/production/work-orders/${woId}/tasks/${taskId}`, data)
}
export function deleteWorkOrderTask(woId, taskId) {
  return request.delete(`/production/work-orders/${woId}/tasks/${taskId}`)
}
export function reorderWorkOrderTasks(woId, taskIds) {
  return request.put(`/production/work-orders/${woId}/tasks/reorder`, taskIds)
}
// BOM快照
export function getWorkOrderBom(woId) {
  return request.get(`/production/work-orders/${woId}/bom`)
}
// 变更历史
export function getWorkOrderChangeHistory(woId) {
  return request.get(`/production/work-orders/${woId}/changes`)
}
// 工单外协状态
export function getWorkOrderOutsourceStatus(woId) {
  return request.get(`/production/work-orders/${woId}/outsource-status`)
}
// 领料状态
export function getIssueStatus(woId) {
  return request.get(`/production/work-orders/${woId}/issue-status`)
}
// 领料
export function issueMaterial(woId, items) {
  return request.post(`/production/work-orders/${woId}/issue-material`, items)
}
// 无 BOM 工单缺料预检 (不写库)
export function checkIssueShortage(woId, items) {
  return request.post(`/production/work-orders/${woId}/issue-shortage-check`, items)
}
// 无 BOM 工单缺料后生成采购申请: body = { items, expectedDate, purpose }
export function generatePurchaseRequestsForShortage(woId, data) {
  return request.post(`/production/work-orders/${woId}/generate-purchase-requests`, data)
}
// 工单关联的采购进度: 采购申请 + 采购订单 + 到货汇总
export function getWorkOrderPurchaseProgress(woId) {
  return request.get(`/production/work-orders/${woId}/purchase-progress`)
}
// 退料
export function returnMaterial(woId, items) {
  return request.post(`/production/work-orders/${woId}/return-material`, items)
}
// 工序报工(Web端)
export function reportTask(woId, taskId, data) {
  return request.post(`/production/work-orders/${woId}/tasks/${taskId}/report`, data)
}
// 报工数量限制
export function getReportLimit(woId, taskId) {
  return request.get(`/production/work-orders/${woId}/tasks/${taskId}/report-limit`)
}
// 工序质检要求
export function getQcRequirements(woId, taskId) {
  return request.get(`/production/work-orders/${woId}/tasks/${taskId}/qc-requirements`)
}
// 生产计划附件
export function getPlanAttachments(woId) {
  return request.get(`/production/work-orders/${woId}/plan-attachments`)
}
// 可派工人员列表
export function getAssigneeOptions() {
  return request.get('/production/assignee-options')
}

// 工序进度卡
export function getProcessProgress(params) {
  return request.get('/production/work-orders/process-progress', { params })
}

// 排产
export function getSchedulingList(params) {
  return request.get('/production/scheduling', { params })
}
export function getSchedulingPage(params) {
  return request.get('/production/scheduling/page', { params })
}
export function createScheduling(data) {
  return request.post('/production/scheduling', data)
}
export function updateScheduling(id, data) {
  return request.put(`/production/scheduling/${id}`, data)
}
export function deleteScheduling(id) {
  return request.delete(`/production/scheduling/${id}`)
}


// 成品入库
export function createWorkOrderStockInTask(woId, data) {
  return request.post(`/production/work-orders/${woId}/stock-in-task`, data)
}

// ========== 返修工单 ==========
export function getReworkOrderPage(params) {
  return request.get('/production/rework-orders/page', { params })
}
export function getReworkOrderDetail(id) {
  return request.get(`/production/rework-orders/${id}`)
}
export function createReworkOrder(data) {
  return request.post('/production/rework-orders', data)
}
export function completeReworkStep(id, data) {
  return request.put(`/production/rework-orders/${id}/complete-step`, data)
}
export function completeReworkOrder(id) {
  return request.put(`/production/rework-orders/${id}/complete`)
}

// ==================== 工序图纸（版本化） ====================
// 排产场景：按 planItemStepId 查图纸
export function listProcessDrawingsByPlanStep(planItemStepId) {
  return request.get('/production/drawings/by-plan-step', { params: { planItemStepId } })
}
// 工单场景：按 processTaskId 查图纸（含所有版本）
export function listProcessDrawingsByTask(processTaskId) {
  return request.get('/production/drawings/by-task', { params: { processTaskId } })
}
// 计划工序是否可编辑（false 表示已派发）
export function isPlanStepEditable(planItemStepId) {
  return request.get('/production/drawings/plan-step-editable', { params: { planItemStepId } })
}
export function uploadProcessDrawing(data) {
  return request.post('/production/drawings', data)
}
export function deleteProcessDrawing(id) {
  return request.delete(`/production/drawings/${id}`)
}
// 工单图纸摘要：传 processTaskIds(数组或逗号分隔字符串)，返回 {taskId: {latest, total, hasNewVersion}}
export function getWoDrawingSummary(processTaskIds) {
  return request.get('/production/drawings/wo-summary', {
    params: { processTaskIds: Array.isArray(processTaskIds) ? processTaskIds.join(',') : processTaskIds }
  })
}
