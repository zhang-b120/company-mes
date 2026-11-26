import request from '@/utils/request'

// BOM物料下拉（含编码、hasBom标识）
export function getBomMaterialSelect() {
  return request.get('/bom/material-select')
}

// BOM 产品列表(新UI用,只显示有BOM的成品)
export function getBomProducts(params) {
  // 兼容旧调用:传字符串当 keyword
  const query = typeof params === 'string' ? { keyword: params } : (params || {})
  return request.get('/bom/products', { params: query })
}

// BOM 导出: 单个产品的激活版本明细
export function exportBomActiveVersion(productId) {
  return request.get('/bom/versions/export-active', { params: { productId }, responseType: 'blob' })
}
// BOM 导出: 所有(可选筛选)产品的激活版本明细 — 多 sheet 或合并表
export function exportBomActiveVersionsAll(params) {
  return request.get('/bom/versions/export-active-all', { params, responseType: 'blob' })
}

// BOM版本
export function getBomVersions(productId) {
  return request.get('/bom/versions', { params: { productId } })
}
export function getBomVersionDetail(id) {
  return request.get(`/bom/versions/${id}`)
}
export function createBomVersion(data) {
  return request.post('/bom/versions', data)
}
export function activateBomVersion(id) {
  return request.put(`/bom/versions/${id}/activate`)
}
export function deprecateBomVersion(id) {
  return request.put(`/bom/versions/${id}/deprecate`)
}
export function copyBomVersion(id, body) {
  return request.post(`/bom/versions/${id}/copy`, body || {})
}
export function copyBomVersionToProduct(id, targetProductId) {
  return request.post(`/bom/versions/${id}/copy-to-product`, { targetProductId })
}
export function deleteBomVersion(id) {
  return request.delete(`/bom/versions/${id}`)
}
export function addBomItem(data) {
  return request.post('/bom/items', data)
}
export function updateBomItem(id, data) {
  return request.put(`/bom/items/${id}`, data)
}
export function deleteBomItem(id) {
  return request.delete(`/bom/items/${id}`)
}

// BOM成本卷积
export function calculateBomCost(id) {
  return request.post(`/bom/versions/${id}/calculate-cost`)
}

// BOM展开为最底层原料清单
export function expandBom(id) {
  return request.get(`/bom/versions/${id}/expand`)
}

// BOM替代料
export function getBomSubstitutes(bomItemId) {
  return request.get('/bom/substitutes', { params: { bomItemId } })
}
export function addBomSubstitute(data) {
  return request.post('/bom/substitutes', data)
}
export function updateBomSubstitute(id, data) {
  return request.put(`/bom/substitutes/${id}`, data)
}
export function deleteBomSubstitute(id) {
  return request.delete(`/bom/substitutes/${id}`)
}

// 工艺路线
export function getProcessRoutePage(params) {
  return request.get('/process/routes/page', { params })
}
export function getProcessRoutes(productId) {
  const params = {}
  if (productId) params.productId = productId
  return request.get('/process/routes', { params })
}
export function getProcessRouteDetail(id) {
  return request.get(`/process/routes/${id}`)
}
export function exportActiveProcessRoutes(params) {
  return request.get('/process/routes/export-active', { params, responseType: 'blob' })
}
export function createProcessRoute(data) {
  return request.post('/process/routes', data)
}
export function updateProcessRoute(id, data) {
  return request.put(`/process/routes/${id}`, data)
}
export function addProcessStep(data) {
  return request.post('/process/steps', data)
}
export function updateProcessStep(id, data) {
  return request.put(`/process/steps/${id}`, data)
}
export function deleteProcessStep(id) {
  return request.delete(`/process/steps/${id}`)
}
