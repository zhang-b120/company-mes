import request from '@/utils/request'

export function getMyTasks(params) {
  return request.get('/workreport/my-tasks', { params })
}
export function getWorkReportPage(params) {
  return request.get('/workreport/personal/page', { params })
}
export function createWorkReport(data) {
  return request.post('/workreport/personal', data)
}
export function getWorkReportDetail(id) {
  return request.get(`/workreport/personal/${id}`)
}
export function approveWorkReport(id) {
  return request.put(`/workreport/personal/${id}/approve`)
}
export function rejectWorkReport(id, reason) {
  return request.put(`/workreport/personal/${id}/reject`, { reason })
}
export function getWorkReportFullDetail(id) {
  return request.get(`/workreport/personal/${id}/full`)
}

export function getExceptionPage(params) {
  return request.get('/workreport/exceptions/page', { params })
}
export function createException(data) {
  return request.post('/workreport/exceptions', data)
}
export function handleException(id, data) {
  return request.put(`/workreport/exceptions/${id}/handle`, data)
}

// ==================== 工序半成品入库 / 领料 ====================
export function semiStockIn(taskId, data) {
  return request.post(`/production/semi-stock/stock-in/${taskId}`, data)
}
export function semiStockRevoke(taskId, data) {
  return request.post(`/production/semi-stock/revoke/${taskId}`, data)
}
export function semiStockIssue(taskId, data) {
  return request.post(`/production/semi-stock/issue/${taskId}`, data)
}
export function semiStockCheckReport(taskId) {
  return request.get(`/production/semi-stock/check-report/${taskId}`)
}
