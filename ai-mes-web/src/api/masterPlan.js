import request from '@/utils/request'

/** 已加入主计划的销售订单列表 */
export function listMasterPlanOrders() {
  return request.get('/master-plan/orders')
}

/** 销售订单加入主计划 */
export function addOrderToMasterPlan(orderId) {
  return request.post(`/master-plan/add-order/${orderId}`)
}

/** 从主计划移除 */
export function removeOrderFromMasterPlan(orderId) {
  return request.delete(`/master-plan/orders/${orderId}`)
}

/** 触发订单主计划计算, 返回 BOM 多级展开树 */
export function calculateMasterPlan(orderId) {
  return request.get('/master-plan/calculate', { params: { orderId } })
}

/** 转车间计划: 后端创建草稿车间计划单 */
export function convertToWo(payload) {
  return request.post('/master-plan/convert-to-wo', payload)
}

/** 转采购单: 后端创建草稿采购订单 */
export function convertToPo(payload) {
  return request.post('/master-plan/convert-to-po', payload)
}
