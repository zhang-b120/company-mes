import request from '@/utils/request'

// ====== 用户管理 ======
export function getUserPage(params) {
  return request.get('/system/users/page', { params })
}
export function createUser(data) {
  return request.post('/system/users', data)
}
export function updateUser(id, data) {
  return request.put(`/system/users/${id}`, data)
}
export function updateUserStatus(id, status) {
  return request.put(`/system/users/${id}/status`, { status })
}
export function resetPassword(id) {
  return request.put(`/system/users/${id}/reset-password`)
}
export function deleteUser(id) {
  return request.delete(`/system/users/${id}`)
}

export function exportUsers() {
  return request.get('/system/users/export', { responseType: 'blob' })
}
export function importUsers(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/system/users/import', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
}
export function downloadUserTemplate() {
  return request.get('/system/users/import-template', { responseType: 'blob' })
}

export function getUserSelect() {
  return request.get('/system/users/select')
}

// ====== 部门管理 ======
export function getDeptTree() {
  return request.get('/system/depts/tree')
}
export function getDeptOptions() {
  return request.get('/system/depts/options')
}
export function createDept(data) {
  return request.post('/system/depts', data)
}
export function updateDept(id, data) {
  return request.put(`/system/depts/${id}`, data)
}
export function deleteDept(id) {
  return request.delete(`/system/depts/${id}`)
}

// ====== 角色管理 ======
export function getRolePage(params) {
  return request.get('/system/roles/page', { params })
}
export function getRoleDetail(id) {
  return request.get(`/system/roles/${id}`)
}
export function createRole(data) {
  return request.post('/system/roles', data)
}
export function updateRole(id, data) {
  return request.put(`/system/roles/${id}`, data)
}
export function deleteRole(id) {
  return request.delete(`/system/roles/${id}`)
}
export function getRoleMenus(id) {
  return request.get(`/system/roles/${id}/menus`)
}
export function saveRoleMenus(id, menuIds) {
  return request.put(`/system/roles/${id}/menus`, { menuIds })
}
export function getRoleList() {
  return request.get('/system/roles/list')
}
export function getRoleDataScope(id) {
  return request.get(`/system/roles/${id}/data-scope`)
}
export function saveRoleDataScope(id, data) {
  return request.put(`/system/roles/${id}/data-scope`, data)
}

// ====== 菜单管理 ======
export function getMenuTree(platform) {
  return request.get('/system/menus/tree', { params: { platform } })
}
export function getMenuOptions() {
  return request.get('/system/menus/options')
}
export function createMenu(data) {
  return request.post('/system/menus', data)
}
export function updateMenu(id, data) {
  return request.put(`/system/menus/${id}`, data)
}
export function deleteMenu(id) {
  return request.delete(`/system/menus/${id}`)
}

// ====== 仓库 ======
export function getWarehouseList(params) {
  return request.get('/system/warehouses/list', { params })
}
export function getWarehouseSelect() {
  return request.get('/system/warehouses/select')
}
export function getWarehouseTree() {
  return request.get('/system/warehouses/tree')
}
export function getWarehouseLocationList(id) {
  return request.get(`/system/warehouses/${id}/locations`)
}
export function createWarehouseLocation(id, data) {
  return request.post(`/system/warehouses/${id}/locations`, data)
}
export function createWarehouse(data) {
  return request.post('/system/warehouses', data)
}
export function updateWarehouse(id, data) {
  return request.put(`/system/warehouses/${id}`, data)
}
export function deleteWarehouse(id) {
  return request.delete(`/system/warehouses/${id}`)
}

// ====== 系统配置 ======
export function getConfigList(params) {
  return request.get('/system/configs/list', { params })
}
export function updateConfig(key, data) {
  return request.put(`/system/configs/${key}`, data)
}
export function createConfig(data) {
  return request.post('/system/configs', data)
}
export function deleteConfig(key) {
  return request.delete(`/system/configs/${key}`)
}

export function getConfigByGroup(group) {
  return request.get(`/system/configs/group/${group}`)
}
export function saveConfigBatch(group, data) {
  return request.post(`/system/configs/group/${group}`, data)
}

// ====== 结算方式选项 ======
export function getSettlementTypes() {
  return request.get('/system/configs/settlement_types')
}
export function saveSettlementTypes(types) {
  return request.put('/system/configs/settlement_types', { configValue: JSON.stringify(types) })
}

// ====== 编号规则 ======
export function getNumberingRules() {
  return request.get('/system/numbering-rules')
}
export function updateNumberingRule(id, data) {
  return request.put(`/system/numbering-rules/${id}`, data)
}

// ====== 通知配置 ======
export function getNotificationConfigs() {
  return request.get('/system/notification-configs')
}
export function updateNotificationConfig(id, data) {
  return request.put(`/system/notification-configs/${id}`, data)
}

// ====== 数据备份 ======
export function getBackupRecords(params) {
  return request.get('/system/backups/page', { params })
}
export function createBackup() {
  return request.post('/system/backups')
}
export function restoreBackup(id) {
  return request.post(`/system/backups/${id}/restore`)
}
export function deleteBackup(id) {
  return request.delete(`/system/backups/${id}`)
}
export function downloadBackup(id) {
  return request.get(`/system/backups/${id}/download`, { responseType: 'blob' })
}
export function getBackupConfig() {
  return request.get('/system/backups/config')
}
export function saveBackupConfig(data) {
  return request.put('/system/backups/config', data)
}

// ====== 业务配置 ======
export function getBizConfigList(module) {
  return request.get('/system/biz-configs', { params: { module } })
}
export function batchUpdateBizConfig(data) {
  return request.put('/system/biz-configs/batch', data)
}

// ====== 通知 ======
export function getNotificationPage(params) {
  return request.get('/system/notifications/page', { params })
}
export function getUnreadCount() {
  return request.get('/system/notifications/unread-count')
}
export function markNotificationRead(id) {
  return request.put(`/system/notifications/${id}/read`)
}
export function markAllRead() {
  return request.put('/system/notifications/read-all')
}
