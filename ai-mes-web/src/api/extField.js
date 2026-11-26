import request from '@/utils/request'

/**
 * 通用扩展字段配置 API
 * module 可选值：sales_order / customer / supplier / purchase_order / work_order
 */

/** 获取指定模块启用的字段配置（列表/表单页使用） */
export function getExtFieldConfigs(module) {
  return request.get('/ext-field-configs', { params: { module } })
}

/** 获取指定模块全部字段配置（管理页使用，不传 module 则返回全部） */
export function getExtFieldConfigsAll(module) {
  return request.get('/ext-field-configs/all', { params: module ? { module } : {} })
}

export function createExtFieldConfig(data) {
  return request.post('/ext-field-configs', data)
}

export function updateExtFieldConfig(id, data) {
  return request.put(`/ext-field-configs/${id}`, data)
}

export function deleteExtFieldConfig(id) {
  return request.delete(`/ext-field-configs/${id}`)
}
