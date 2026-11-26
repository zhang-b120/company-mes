import request from '@/utils/request'

// 打印模板 CRUD
export function getPrintTemplates(params) {
  return request.get('/system/print-templates', { params })
}
export function getActivePrintTemplate(bizType) {
  return request.get('/system/print-templates/active', { params: { bizType } })
}
export function getPrintTemplateDetail(id) {
  return request.get(`/system/print-templates/${id}`)
}
export function createPrintTemplate(data) {
  return request.post('/system/print-templates', data)
}
export function updatePrintTemplate(id, data) {
  return request.put(`/system/print-templates/${id}`, data)
}
export function activatePrintTemplate(id) {
  return request.put(`/system/print-templates/${id}/activate`)
}
export function deletePrintTemplate(id) {
  return request.delete(`/system/print-templates/${id}`)
}
