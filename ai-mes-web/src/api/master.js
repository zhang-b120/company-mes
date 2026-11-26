import request from '@/utils/request'

// ====== 客户 ======
export function getCustomerPage(params) {
  return request.get('/master/customers/page', { params })
}
export function getCustomerDetail(id) {
  return request.get(`/master/customers/${id}`)
}
export function createCustomer(data) {
  return request.post('/master/customers', data)
}
export function updateCustomer(id, data) {
  return request.put(`/master/customers/${id}`, data)
}
export function updateCustomerStatus(id, status) {
  return request.put(`/master/customers/${id}/status`, { status })
}
export function getCustomerSelect() {
  return request.get('/master/customers/select')
}

export function batchDeleteCustomers(ids) {
  return request.post('/master/customers/batch-delete', { ids })
}
export function batchUpdateCustomerStatus(ids, status) {
  return request.put('/master/customers/batch-status', { ids, status })
}

export function exportCustomers() {
  return request.get('/master/customers/export', { responseType: 'blob' })
}
export function importCustomers(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/master/customers/import', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
}
export function downloadCustomerTemplate() {
  return request.get('/master/customers/import-template', { responseType: 'blob' })
}

// ====== 客户跟进记录 ======
export function getCustomerFollowups(customerId) {
  return request.get(`/master/customers/${customerId}/followups`)
}
export function createCustomerFollowup(customerId, data) {
  return request.post(`/master/customers/${customerId}/followups`, data)
}
export function deleteCustomerFollowup(customerId, id) {
  return request.delete(`/master/customers/${customerId}/followups/${id}`)
}

// ====== 供应商 ======
export function getSupplierPage(params) {
  return request.get('/master/suppliers/page', { params })
}
export function getSupplierDetail(id) {
  return request.get(`/master/suppliers/${id}`)
}
export function createSupplier(data) {
  return request.post('/master/suppliers', data)
}
export function updateSupplier(id, data) {
  return request.put(`/master/suppliers/${id}`, data)
}
export function updateSupplierScore(id, score) {
  return request.put(`/master/suppliers/${id}/score`, { score })
}
export function getSupplierSelect() {
  return request.get('/master/suppliers/select')
}
export function exportSuppliers() {
  return request.get('/master/suppliers/export', { responseType: 'blob' })
}
export function importSuppliers(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/master/suppliers/import', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
}
export function downloadSupplierTemplate() {
  return request.get('/master/suppliers/import-template', { responseType: 'blob' })
}

// ====== 供应商类别 ======
export function getSupplierCategoryTree() {
  return request.get('/master/supplier-categories/tree')
}
export function createSupplierCategory(data) {
  return request.post('/master/supplier-categories', data)
}
export function updateSupplierCategory(id, data) {
  return request.put(`/master/supplier-categories/${id}`, data)
}
export function deleteSupplierCategory(id) {
  return request.delete(`/master/supplier-categories/${id}`)
}

// ====== 物料 ======
export function getMaterialPage(params) {
  return request.get('/master/materials/page', { params })
}
export function getMaterialDetail(id) {
  return request.get(`/master/materials/${id}`)
}
export function createMaterial(data) {
  return request.post('/master/materials', data)
}
export function updateMaterial(id, data) {
  return request.put(`/master/materials/${id}`, data)
}
export function deleteMaterial(id) {
  return request.delete(`/master/materials/${id}`)
}
export function getMaterialSelect(params) {
  return request.get('/master/materials/select', { params })
}
export function batchCreateMaterials(list) {
  return request.post('/master/materials/batch', list)
}

export function exportMaterials() {
  return request.get('/master/materials/export', { responseType: 'blob' })
}
export function importMaterials(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/master/materials/import', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
}
export function downloadMaterialTemplate() {
  return request.get('/master/materials/import-template', { responseType: 'blob' })
}

// ====== 产品 ======
export function getProductPage(params) {
  return request.get('/master/products/page', { params })
}
export function getProductDetail(id) {
  return request.get(`/master/products/${id}`)
}
export function createProduct(data) {
  return request.post('/master/products', data)
}
export function updateProduct(id, data) {
  return request.put(`/master/products/${id}`, data)
}
export function deleteProduct(id) {
  return request.delete(`/master/products/${id}`)
}
export function getProductSelect(params) {
  return request.get('/master/products/select', { params })
}
export function exportProducts() {
  return request.get('/master/products/export', { responseType: 'blob' })
}
export function importProducts(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/master/products/import', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
}
export function downloadProductTemplate() {
  return request.get('/master/products/import-template', { responseType: 'blob' })
}

// ====== 计量单位 ======
export function getUnitPage(params) {
  return request.get('/master/units/page', { params })
}
export function createUnit(data) {
  return request.post('/master/units', data)
}
export function updateUnit(id, data) {
  return request.put(`/master/units/${id}`, data)
}
export function deleteUnit(id) {
  return request.delete(`/master/units/${id}`)
}
export function getUnitSelect() {
  return request.get('/master/units/select')
}

// ====== 物料类别 ======
export function getMaterialCategoryTree() {
  return request.get('/master/material-categories/tree')
}
export function createMaterialCategory(data) {
  return request.post('/master/material-categories', data)
}
export function updateMaterialCategory(id, data) {
  return request.put(`/master/material-categories/${id}`, data)
}
export function deleteMaterialCategory(id) {
  return request.delete(`/master/material-categories/${id}`)
}

// ====== 工序库 ======
export function getProcessLibraryPage(params) {
  return request.get('/master/processes/page', { params })
}
export function createProcess(data) {
  return request.post('/master/processes', data)
}
export function updateProcess(id, data) {
  return request.put(`/master/processes/${id}`, data)
}
export function getProcessSelect() {
  return request.get('/master/processes/select')
}
export function getProcessReferences(id) {
  return request.get(`/master/processes/${id}/references`)
}
export function exportProcesses() {
  return request.get('/master/processes/export', { responseType: 'blob' })
}
export function importProcesses(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/master/processes/import', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
}
export function downloadProcessTemplate() {
  return request.get('/master/processes/import-template', { responseType: 'blob' })
}

// ==================== 规格库 ====================

export function getMaterialSpecPage(params) {
  return request.get('/master/material-specs/page', { params })
}
export function getMaterialSpecSelect() {
  return request.get('/master/material-specs/select')
}
export function createMaterialSpec(data) {
  return request.post('/master/material-specs', data)
}
export function updateMaterialSpec(id, data) {
  return request.put(`/master/material-specs/${id}`, data)
}
export function deleteMaterialSpec(id) {
  return request.delete(`/master/material-specs/${id}`)
}
export function exportMaterialSpecs() {
  return request.get('/master/material-specs/export', { responseType: 'blob' })
}
export function importMaterialSpecs(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/master/material-specs/import', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
}
export function downloadMaterialSpecTemplate() {
  return request.get('/master/material-specs/import-template', { responseType: 'blob' })
}
