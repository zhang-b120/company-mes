import request from '@/utils/request'

/** 大屏销售订单 */
export function getTvSalesOrders(params) {
  return request.get('/tv/sales-orders', { params, noAuth: true })
}

/** 大屏库存 */
export function getTvInventory(params) {
  return request.get('/tv/inventory', { params, noAuth: true })
}

/** 大屏库存历史 */
export function getTvInvHistory(params) {
  return request.get('/tv/inv-history', { params, noAuth: true })
}

/** TV2 大屏聚合数据(5 个 Tab 一次返回) */
export function getTv2All() {
  return request.get('/tv2/all', { noAuth: true, timeout: 90000 })
}

/**
 * 模块独立大屏聚合数据(统一入口)
 * @param {string} module production|sales|inventory|purchase|quality|finance|outsource|mrp|workreport|eam
 *
 * 大屏后端聚合查询较多,远程 MySQL 时延会放大,这里给 90s 超时
 */
export function getModuleTv(module) {
  return request.get(`/module-tv/${module}`, { noAuth: true, timeout: 90000 })
}
