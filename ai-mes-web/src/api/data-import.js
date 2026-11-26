import request from '@/utils/request'

const TYPES = [
  'material-spec', 'material', 'team', 'process', 'process-route', 'bom',
  'sales-order', 'purchase-order', 'user', 'customer', 'supplier'
]

export function downloadTemplate(type) {
  return request.get(`/data-import/${type}/template`, { responseType: 'blob' })
}

export function downloadAllPlainTemplates() {
  return request.get('/data-import/plain-templates', { responseType: 'blob' })
}

export function importData(type, file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post(`/data-import/${type}/import`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 300000 // 5分钟超时，大文件导入
  })
}

export function getUserOptions() {
  return request.get('/data-import/users')
}

// ====== 数据模拟生成 ======

export function getSimModules() {
  return request.get('/sim/modules')
}

export function getSimUsers() {
  return request.get('/sim/users')
}

// 数据模拟相关接口大数据量场景下耗时不可预测,前端不设超时(timeout: 0 = 永不超时),
// 让浏览器一直等到后端返回。后端要在合理时间内自己处理或异步化。
export function generateSimData(module, config) {
  return request.post(`/sim/${module}/generate`, config, { timeout: 0 })
}

export function exportSimData(module) {
  return request.get(`/sim/${module}/export`, { responseType: 'blob', timeout: 0 })
}

export function importSimData(module, files) {
  const formData = new FormData()
  files.forEach(f => formData.append('files', f))
  return request.post(`/sim/${module}/import`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }, timeout: 0
  })
}

export { TYPES }
