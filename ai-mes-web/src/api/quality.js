import request from '@/utils/request'

// ========== 废料统计 ==========
export function getWasteRecordPage(params) {
  return request.get('/quality/waste-records/page', { params })
}
export function createWasteRecord(data) {
  return request.post('/quality/waste-records', data)
}
export function updateWasteRecord(id, data) {
  return request.put(`/quality/waste-records/${id}`, data)
}
export function deleteWasteRecord(id) {
  return request.delete(`/quality/waste-records/${id}`)
}
export function getWasteStats(params) {
  return request.get('/quality/waste-records/stats', { params })
}
export function exportWasteRecords(params) {
  return request.get('/quality/waste-records/export', { params, responseType: 'blob' })
}

// ========== 质检项目管理 ==========
export function getQcItemPage(params) {
  return request.get('/quality/items/page', { params })
}
export function getQcItemList(params) {
  return request.get('/quality/items/list', { params })
}
export function createQcItem(data) {
  return request.post('/quality/items', data)
}
export function updateQcItem(id, data) {
  return request.put(`/quality/items/${id}`, data)
}
export function toggleQcItemStatus(id) {
  return request.put(`/quality/items/${id}/toggle-status`)
}
export function deleteQcItem(id) {
  return request.delete(`/quality/items/${id}`)
}

// ========== 质检模板管理 ==========
export function getQcTemplatePage(params) {
  return request.get('/quality/templates/page', { params })
}
export function getQcTemplateDetail(id) {
  return request.get(`/quality/templates/${id}`)
}
export function createQcTemplate(data) {
  return request.post('/quality/templates', data)
}
export function updateQcTemplate(id, data) {
  return request.put(`/quality/templates/${id}`, data)
}
export function deleteQcTemplate(id) {
  return request.delete(`/quality/templates/${id}`)
}
export function getQcTemplateSelect() {
  return request.get('/quality/templates/select')
}

// ========== 质检任务 ==========
export function getQcTaskPage(params) {
  return request.get('/quality/tasks/page', { params })
}
export function getQcTaskDetail(id) {
  return request.get(`/quality/tasks/${id}`)
}
export function createQcTask(data) {
  return request.post('/quality/tasks', data)
}
export function submitQcResult(id, data) {
  return request.put(`/quality/tasks/${id}/submit`, data)
}
export function disposeQcTask(id, data) {
  return request.put(`/quality/tasks/${id}/dispose`, data)
}

// ========== 质检记录 ==========
export function getQcRecordPage(params) {
  return request.get('/quality/tasks/records', { params })
}
export function getQcRecordStats(params) {
  return request.get('/quality/tasks/records/stats', { params })
}
export function exportQcRecords(params) {
  return request.get('/quality/tasks/records/export', { params, responseType: 'blob' })
}

// ========== 不良品/缺陷 ==========
export function getDefectPage(params) {
  return request.get('/quality/defects/page', { params })
}
export function createDefect(data) {
  return request.post('/quality/defects', data)
}
export function handleDefect(id, data) {
  return request.put(`/quality/defects/${id}/handle`, data)
}
export function getDefectStats() {
  return request.get('/quality/defects/stats')
}

// ========== 质量追溯 ==========
export function getQualityTraceability(params) {
  return request.get('/quality/tasks/traceability', { params })
}

// ========== 质检分析 ==========
export function getQcAnalysisOverview() {
  return request.get('/quality/analysis/overview')
}
export function getPassRateTrend(params) {
  return request.get('/quality/analysis/pass-rate-trend', { params })
}
export function getDefectDistribution(params) {
  return request.get('/quality/analysis/defect-distribution', { params })
}
export function getProductQuality(params) {
  return request.get('/quality/analysis/product-quality', { params })
}
export function getInspectorStats(params) {
  return request.get('/quality/analysis/inspector-stats', { params })
}

// ========== 质检方案 ==========
export function getQcSchemePage(params) {
  return request.get('/quality/schemes/page', { params })
}
export function getQcSchemeSelect() {
  return request.get('/quality/schemes/select')
}
export function getQcSchemeDetail(id) {
  return request.get(`/quality/schemes/${id}`)
}
export function createQcScheme(data) {
  return request.post('/quality/schemes', data)
}
export function updateQcScheme(id, data) {
  return request.put(`/quality/schemes/${id}`, data)
}
export function deleteQcScheme(id) {
  return request.delete(`/quality/schemes/${id}`)
}

// ========== 质检处理单 ==========
export function getQcDisposalPage(params) {
  return request.get('/quality/disposals/page', { params })
}
export function getQcDisposalDetail(id) {
  return request.get(`/quality/disposals/${id}`)
}
export function processQcDisposal(id, data) {
  return request.put(`/quality/disposals/${id}/process`, data)
}
