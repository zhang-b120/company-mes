import request from '@/utils/request'

/** 客户看板 - 获取全部分析数据 */
export function getCustomerAnalysisAll() {
  return request.get('/analysis/customer/all')
}

/** 退货分析 */
export function getReturnAnalysis(period) {
  return request.get('/analysis/customer/return-analysis', { params: { period } })
}

/** 客户看板(新) - 汇总表格+排名+地区分布 */
export function getCustomerKanbanAll() {
  return request.get('/analysis/customer/kanban')
}

/** 销售看板 - 获取全部分析数据 */
export function getSalesOrderAnalysisAll() {
  return request.get('/analysis/sales-order/all')
}
