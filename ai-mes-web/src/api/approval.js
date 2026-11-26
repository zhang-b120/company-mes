import request from '@/utils/request'

// ====== 审批流程定义 ======
export function getApprovalFlowPage(params) {
  return request.get('/approval/flows/page', { params })
}
export function getApprovalFlowDetail(id) {
  return request.get(`/approval/flows/${id}`)
}
export function createApprovalFlow(data) {
  return request.post('/approval/flows', data)
}
export function updateApprovalFlow(id, data) {
  return request.put(`/approval/flows/${id}`, data)
}
export function deleteApprovalFlow(id) {
  return request.delete(`/approval/flows/${id}`)
}
export function toggleApprovalFlow(id) {
  return request.put(`/approval/flows/${id}/toggle`)
}

// ====== 审批操作 ======
export function startApproval(data) {
  return request.post('/approval/start', data)
}
export function approveInstance(instanceId, remark) {
  return request.put(`/approval/${instanceId}/approve`, { remark })
}
export function rejectInstance(instanceId, remark) {
  return request.put(`/approval/${instanceId}/reject`, { remark })
}
export function cancelApproval(instanceId) {
  return request.put(`/approval/${instanceId}/cancel`)
}
export function getApprovalProgress(businessType, businessId) {
  return request.get('/approval/progress', { params: { businessType, businessId } })
}
export function getMyPendingTasks() {
  return request.get('/approval/my-tasks')
}
export function getMyApprovalPage(params) {
  return request.get('/approval/my-tasks/page', { params })
}
