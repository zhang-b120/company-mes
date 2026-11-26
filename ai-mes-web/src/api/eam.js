import request from '@/utils/request'

// ========== 设备台账 F1 ==========
export function getEquipmentPage(params) { return request.get('/eam/equipment/page', { params }) }
export function getEquipmentList(category) { return request.get('/eam/equipment/list', { params: { category } }) }
export function getEquipmentDetail(id) { return request.get(`/eam/equipment/${id}`) }
export function createEquipment(data) { return request.post('/eam/equipment', data) }
export function updateEquipment(id, data) { return request.put(`/eam/equipment/${id}`, data) }
export function deleteEquipment(id) { return request.delete(`/eam/equipment/${id}`) }
export function enableEquipment(id) { return request.post(`/eam/equipment/${id}/enable`) }
export function disableEquipment(id, targetStatus) { return request.post(`/eam/equipment/${id}/disable`, { targetStatus }) }
export function getEquipmentReminder(days = 7) { return request.get('/eam/equipment/reminder', { params: { days } }) }
export function getEquipmentStatistics() { return request.get('/eam/equipment/statistics') }
export function downloadEquipmentTemplate() { return request.get('/eam/equipment/import-template', { responseType: 'blob' }) }
export function importEquipment(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/eam/equipment/import', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
}

// ========== 设备运行状态 F2 ==========
export function getEquipmentStatus(id) { return request.get(`/eam/equipment/${id}/status`) }
export function updateEquipmentStatus(id, data) { return request.put(`/eam/equipment/${id}/status`, data) }
export function getEquipmentAlarmLogs(id) { return request.get(`/eam/equipment/${id}/alarm-logs`) }

// ========== 保养模板 F3 ==========
export function getMaintenanceTemplatePage(params) { return request.get('/eam/maintenance/templates/page', { params }) }
export function getMaintenanceTemplateList() { return request.get('/eam/maintenance/templates/list') }
export function getMaintenanceTemplateDetail(id) { return request.get(`/eam/maintenance/templates/${id}`) }
export function createMaintenanceTemplate(data) { return request.post('/eam/maintenance/templates', data) }
export function updateMaintenanceTemplate(id, data) { return request.put(`/eam/maintenance/templates/${id}`, data) }
export function deleteMaintenanceTemplate(id) { return request.delete(`/eam/maintenance/templates/${id}`) }

// ========== 保养计划 F4 ==========
export function getMaintenancePlanPage(params) { return request.get('/eam/maintenance/plans/page', { params }) }
export function getMaintenancePlanDetail(id) { return request.get(`/eam/maintenance/plans/${id}`) }
export function createMaintenancePlan(data) { return request.post('/eam/maintenance/plans', data) }
export function updateMaintenancePlan(id, data) { return request.put(`/eam/maintenance/plans/${id}`, data) }
export function deleteMaintenancePlan(id) { return request.delete(`/eam/maintenance/plans/${id}`) }
export function publishMaintenancePlan(id) { return request.post(`/eam/maintenance/plans/${id}/publish`) }
export function pauseMaintenancePlan(id) { return request.post(`/eam/maintenance/plans/${id}/pause`) }
export function cancelMaintenancePlan(id) { return request.post(`/eam/maintenance/plans/${id}/cancel`) }
export function assignMaintenanceTasks(id) { return request.put(`/eam/maintenance/plans/${id}/tasks`) }

// ========== 保养任务 F5 ==========
export function getMaintenanceTaskPage(params) { return request.get('/eam/maintenance/tasks/page', { params }) }
export function getMaintenanceTaskDetail(id) { return request.get(`/eam/maintenance/tasks/${id}`) }
export function startMaintenanceTask(id) { return request.post(`/eam/maintenance/tasks/${id}/start`) }
export function pauseMaintenanceTask(id) { return request.post(`/eam/maintenance/tasks/${id}/pause`) }
export function cancelMaintenanceTask(id) { return request.post(`/eam/maintenance/tasks/${id}/cancel`) }
export function completeMaintenanceTask(id, data) { return request.post(`/eam/maintenance/tasks/${id}/complete`, data) }
export function updateMaintenanceTaskDetail(detailId, data) { return request.put(`/eam/maintenance/tasks/details/${detailId}`, data) }
export function getMaintenanceTaskStatistics() { return request.get('/eam/maintenance/tasks/statistics') }

// ========== 点检项目库 ==========
export function getInspectionItemPage(params) { return request.get('/eam/inspection/items/page', { params }) }
export function getInspectionItemList() { return request.get('/eam/inspection/items/list') }
export function createInspectionItem(data) { return request.post('/eam/inspection/items', data) }
export function updateInspectionItem(id, data) { return request.put(`/eam/inspection/items/${id}`, data) }
export function deleteInspectionItem(id) { return request.delete(`/eam/inspection/items/${id}`) }

// ========== 点检模板 F6 ==========
export function getInspectionTemplatePage(params) { return request.get('/eam/inspection/templates/page', { params }) }
export function getInspectionTemplateList() { return request.get('/eam/inspection/templates/page', { params: { pageSize: 200 } }) }
export function getInspectionTemplateDetail(id) { return request.get(`/eam/inspection/templates/${id}`) }
export function createInspectionTemplate(data) { return request.post('/eam/inspection/templates', data) }
export function updateInspectionTemplate(id, data) { return request.put(`/eam/inspection/templates/${id}`, data) }
export function deleteInspectionTemplate(id) { return request.delete(`/eam/inspection/templates/${id}`) }

// ========== 点检计划 F7 ==========
export function getInspectionPlanPage(params) { return request.get('/eam/inspection/plans/page', { params }) }
export function createInspectionPlan(data) { return request.post('/eam/inspection/plans', data) }
export function updateInspectionPlan(id, data) { return request.put(`/eam/inspection/plans/${id}`, data) }
export function deleteInspectionPlan(id) { return request.delete(`/eam/inspection/plans/${id}`) }
export function publishInspectionPlan(id) { return request.post(`/eam/inspection/plans/${id}/publish`) }
export function cancelInspectionPlan(id) { return request.post(`/eam/inspection/plans/${id}/cancel`) }
export function assignInspectionTasks(id) { return request.put(`/eam/inspection/plans/${id}/tasks`) }

// ========== 点检任务 F8 ==========
export function getInspectionTaskPage(params) { return request.get('/eam/inspection/tasks/page', { params }) }
export function getInspectionTaskDetail(id) { return request.get(`/eam/inspection/tasks/${id}`) }
export function startInspectionTask(id) { return request.post(`/eam/inspection/tasks/${id}/start`) }
export function cancelInspectionTask(id) { return request.post(`/eam/inspection/tasks/${id}/cancel`) }
export function completeInspectionTask(id, data) { return request.post(`/eam/inspection/tasks/${id}/complete`, data || {}) }
export function updateInspectionTaskDetail(detailId, data) { return request.put(`/eam/inspection/tasks/details/${detailId}`, data) }

// ========== 报修申请 F9 ==========
export function getRepairRequestPage(params) { return request.get('/eam/repair/requests/page', { params }) }
export function getRepairRequestDetail(id) { return request.get(`/eam/repair/requests/${id}`) }
export function createRepairRequest(data) { return request.post('/eam/repair/requests', data) }
export function acceptRepairRequest(id, data) { return request.post(`/eam/repair/requests/${id}/accept`, data) }
export function submitRepairRequest(id) { return request.post(`/eam/repair/requests/${id}/submit`) }
export function cancelRepairRequest(id) { return request.post(`/eam/repair/requests/${id}/cancel`) }
export function deleteRepairRequest(id) { return request.delete(`/eam/repair/requests/${id}`) }
export function getRepairRequestOrders(id) { return request.get(`/eam/repair/requests/${id}/orders`) }

// ========== 维修工单 F10 ==========
export function getRepairOrderPage(params) { return request.get('/eam/repair/orders/page', { params }) }
export function getRepairOrderDetail(id) { return request.get(`/eam/repair/orders/${id}`) }
export function createRepairOrder(data) { return request.post('/eam/repair/orders', data) }
export function updateRepairOrder(id, data) { return request.put(`/eam/repair/orders/${id}`, data) }
export function startRepairOrder(id) { return request.post(`/eam/repair/orders/${id}/start`) }
export function pauseRepairOrder(id) { return request.post(`/eam/repair/orders/${id}/pause`) }
export function resumeRepairOrder(id) { return request.post(`/eam/repair/orders/${id}/resume`) }
export function completeRepairOrder(id, data) { return request.post(`/eam/repair/orders/${id}/complete`, data) }
export function cancelRepairOrder(id) { return request.post(`/eam/repair/orders/${id}/cancel`) }
export function batchStartRepairOrders(ids) { return request.post('/eam/repair/orders/batch-start', { ids }) }
export function batchPauseRepairOrders(ids) { return request.post('/eam/repair/orders/batch-pause', { ids }) }
