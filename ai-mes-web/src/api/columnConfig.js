import request from '@/utils/request'

/** 获取当前用户指定模块的列配置 */
export function getColumnConfig(module) {
  return request.get(`/table-column-config/${module}`)
}

/** 保存当前用户指定模块的列配置（全量替换） */
export function saveColumnConfig(module, columns) {
  return request.post(`/table-column-config/${module}`, columns)
}
