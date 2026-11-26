import request from '@/utils/request'

export function getInventoryPage(params) {
  return request.get('/inventory/page', { params })
}
export function exportInventory(params) {
  return request.get('/inventory/export', { params, responseType: 'blob' })
}
export function getInventoryTransactions(params) {
  return request.get('/inventory/transactions', { params })
}
export function getInventoryAlerts() {
  return request.get('/inventory/alerts')
}
export function getAvailableQty(materialId, warehouseId) {
  return request.get('/inventory/available', { params: { materialId, warehouseId } })
}
export function getStockQty(materialId, warehouseId) {
  return request.get('/inventory/stock', { params: { materialId, warehouseId } })
}
export function stockIn(data) {
  return request.post('/inventory/stock-in', data)
}
export function stockOut(data) {
  return request.post('/inventory/stock-out', data)
}
export function lockStock(data) {
  return request.post('/inventory/lock', data)
}
export function unlockStock(id) {
  return request.post(`/inventory/${id}/unlock`)
}
export function getReservationPage(params) {
  return request.get('/inventory/reservations/page', { params })
}

// 待入库工单列表
export function getPendingStockIn() {
  return request.get('/inventory/pending-stock-in')
}
// 确认成品入库
export function confirmProductionIn(data) {
  return request.post('/inventory/confirm-production-in', data)
}

// 二维码标签模板
export function getQrTemplateList(params) {
  return request.get('/inventory/qr-templates/list', { params })
}
export function getQrTemplateDetail(id) {
  return request.get(`/inventory/qr-templates/${id}`)
}
export function createQrTemplate(data) {
  return request.post('/inventory/qr-templates', data)
}
export function updateQrTemplate(id, data) {
  return request.put(`/inventory/qr-templates/${id}`, data)
}
export function deleteQrTemplate(id) {
  return request.delete(`/inventory/qr-templates/${id}`)
}
export function setDefaultQrTemplate(id) {
  return request.put(`/inventory/qr-templates/${id}/set-default`)
}

// ====== 入库申请 ======
export function getInvRequestPage(params) {
  return request.get('/inventory/requests/page', { params })
}
export function exportInvRequests(params) {
  return request.get('/inventory/requests/export', { params, responseType: 'blob' })
}
export function getInvRequestDetail(id) {
  return request.get(`/inventory/requests/${id}`)
}
export function createInvRequest(data) {
  return request.post('/inventory/requests', data)
}
export function updateInvRequest(id, data) {
  return request.put(`/inventory/requests/${id}`, data)
}
export function approveInvRequest(id) {
  return request.put(`/inventory/requests/${id}/approve`)
}
export function rejectInvRequest(id, data) {
  return request.put(`/inventory/requests/${id}/reject`, data)
}
export function getInvRequestDetailByNo(requestNo) {
  return request.get('/inventory/requests/by-no', { params: { requestNo } })
}

// ====== 库存任务 ======
export function getInvTaskPage(params) {
  return request.get('/inventory/tasks/page', { params })
}
export function getInvTaskDetail(id) {
  return request.get(`/inventory/tasks/${id}`)
}
export function getInvTaskBySource(sourceType, sourceId) {
  return request.get('/inventory/tasks/by-source', { params: { sourceType, sourceId } })
}
export function updateInvTaskWarehouse(id, data) {
  return request.put(`/inventory/tasks/${id}/warehouse`, data)
}
export function processInvTask(id, data) {
  return request.post(`/inventory/tasks/${id}/process`, data)
}
export function createInvTaskFromWorkOrder(woId, data) {
  return request.post(`/inventory/tasks/from-work-order/${woId}`, data)
}
export function returnInvTask(id, data) {
  return request.put(`/inventory/tasks/${id}/return`, data)
}
export function getInvTasksByWorkOrder(woId) {
  return request.get(`/inventory/tasks/by-work-order/${woId}`)
}
export function getInvTasksByPurchaseOrder(orderId) {
  return request.get(`/inventory/tasks/by-purchase-order/${orderId}`)
}

// ====== 出入库历史 ======
export function getInvHistoryPage(params) {
  return request.get('/inventory/history/page', { params })
}
export function getInvHistoryDetail(id) {
  return request.get(`/inventory/history/${id}`)
}
export function revokeInvHistory(id) {
  return request.put(`/inventory/history/${id}/revoke`)
}
export function revokeInvHistoryItem(itemId) {
  return request.put(`/inventory/history/items/${itemId}/revoke`)
}

export function exportInvHistory(params) {
  return request.get('/inventory/history/export', { params, responseType: 'blob' })
}

// 批量查询物料库存汇总（物料档案用）
export function getMaterialStocks(materialIds) {
  return request.get('/inventory/material-stocks', { params: { materialIds: materialIds.join(',') } })
}

// ====== 仓库调拨 ======
export function getTransferPage(params) {
  return request.get('/inventory/transfer/page', { params })
}
export function getTransferDetail(id) {
  return request.get(`/inventory/transfer/${id}`)
}
export function createTransfer(data) {
  return request.post('/inventory/transfer', data)
}
export function approveTransfer(id, data) {
  return request.post(`/inventory/transfer/${id}/approve`, data)
}
export function rejectTransfer(id, data) {
  return request.post(`/inventory/transfer/${id}/reject`, data)
}

// ====== 仓库盘点 ======
export function getStocktakingPage(params) {
  return request.get('/inventory/stocktaking/page', { params })
}
export function getStocktakingDetail(id) {
  return request.get(`/inventory/stocktaking/${id}`)
}
export function createStocktaking(data) {
  return request.post('/inventory/stocktaking', data)
}
export function approveStocktaking(id, data) {
  return request.post(`/inventory/stocktaking/${id}/approve`, data)
}
export function rejectStocktaking(id, data) {
  return request.post(`/inventory/stocktaking/${id}/reject`, data)
}
export function getTransferDetailByNo(transferNo) {
  return request.get('/inventory/transfer/by-no', { params: { transferNo } })
}
export function getStocktakingDetailByNo(stocktakingNo) {
  return request.get('/inventory/stocktaking/by-no', { params: { stocktakingNo } })
}

// ====== 历史库存台账 ======
export function getSnapshotPage(params) {
  return request.get('/inventory/snapshots/page', { params })
}
export function getSnapshotDetail(id) {
  return request.get(`/inventory/snapshots/${id}`)
}
export function getSnapshotByDate(date) {
  return request.get('/inventory/snapshots/by-date', { params: { date } })
}
export function getSnapshotDates() {
  return request.get('/inventory/snapshots/dates')
}
export function exportSnapshot(date) {
  return request.get('/inventory/snapshots/export', {
    params: { date },
    responseType: 'blob'
  })
}
