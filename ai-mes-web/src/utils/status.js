/**
 * 通用状态标签映射工具
 * 统一各页面的状态显示颜色和文字
 * 注意：后端全部使用小写状态值，前端映射键也统一使用小写
 */

// 订单状态
export const orderStatusMap = {
  draft: { label: '草稿', type: 'info' },
  pending_approval: { label: '待审批', type: 'warning' },
  confirmed: { label: '已确认', type: '' },
  mrp_planned: { label: '已排产', type: '' },
  outsourcing: { label: '外协中', type: 'warning' },
  in_production: { label: '生产中', type: '' },
  partial_shipped: { label: '部分发货', type: '' },
  shipped: { label: '已发货', type: 'success' },
  completed: { label: '已完成', type: 'success' },
  settled: { label: '已结算', type: 'success' },
  cancelled: { label: '已取消', type: 'danger' },
  rejected: { label: '已驳回', type: 'danger' },
  voided: { label: '已作废', type: 'info' }
}

// 工单状态
export const workOrderStatusMap = {
  pending: { label: '待排产', type: 'info' },
  scheduled: { label: '已排产', type: '' },
  in_progress: { label: '生产中', type: 'warning' },
  completed: { label: '生产完成', type: 'success' },
  cancelled: { label: '已取消', type: 'danger' }
}

// 工单库存任务状态
export const woInvTaskStatusMap = {
  null: { label: '未生成', type: 'info' },
  partial: { label: '部分生成', type: 'warning' },
  generated: { label: '全部生成', type: 'success' }
}

// 排产状态
export const schedulingStatusMap = {
  pending: { label: '待开始', type: 'info' },
  scheduled: { label: '已排产', type: '' },
  in_progress: { label: '进行中', type: 'warning' },
  completed: { label: '已完成', type: 'success' },
  cancelled: { label: '已取消', type: 'danger' }
}

// 质检状态（后端使用小写 pending/pass/fail）
export const qcStatusMap = {
  pending: { label: '待检验', type: 'info' },
  inspecting: { label: '检验中', type: 'warning' },
  pass: { label: '合格', type: 'success' },
  passed: { label: '合格', type: 'success' },
  fail: { label: '不合格', type: 'danger' },
  failed: { label: '不合格', type: 'danger' }
}

// 采购申请状态
export const purchaseRequestStatusMap = {
  draft: { label: '草稿', type: 'info' },
  pending: { label: '待审批', type: 'warning' },
  approved: { label: '已批准', type: 'success' },
  rejected: { label: '已驳回', type: 'danger' },
  ordered: { label: '已下单', type: '' },
  issued: { label: '已下发', type: '' },
  completed: { label: '已完成', type: 'success' },
  cancelled: { label: '已取消', type: 'danger' }
}

// 采购订单状态
export const purchaseOrderStatusMap = {
  draft: { label: '草稿', type: 'info' },
  pending_approval: { label: '待审批', type: 'warning' },
  confirmed: { label: '已确认', type: '' },
  rejected: { label: '已驳回', type: 'danger' },
  receiving: { label: '收货中', type: 'warning' },
  partial_received: { label: '部分收货', type: 'warning' },
  received: { label: '已收货', type: 'success' },
  completed: { label: '已完成', type: 'success' },
  cancelled: { label: '已取消', type: 'danger' }
}

// 外协申请状态
export const outsourceRequestStatusMap = {
  pending: { label: '待审批', type: 'warning' },
  approved: { label: '已审批', type: 'success' },
  rejected: { label: '已驳回', type: 'danger' },
  ordered: { label: '已下单', type: '' },
  completed: { label: '已完成', type: 'success' },
  cancelled: { label: '已取消', type: 'danger' }
}

// 外协加工单状态
export const outsourceOrderStatusMap = {
  draft: { label: '草稿', type: 'info' },
  confirmed: { label: '已确认', type: '' },
  material_sent: { label: '已发料', type: '' },
  delivering: { label: '交付中', type: 'warning' },
  processing: { label: '加工中', type: 'warning' },
  inspecting: { label: '质检中', type: '' },
  partial_received: { label: '部分收货', type: '' },
  received: { label: '已收货', type: '' },
  completed: { label: '已完成', type: 'success' },
  settled: { label: '已结算', type: 'success' },
  cancelled: { label: '已取消', type: 'danger' }
}

// 报工类型
export const reportTypeMap = {
  personal: { label: '个人报工', type: 'warning' },
  team: { label: '班组报工', type: '' },
  piece: { label: '计件', type: '' },
  time: { label: '计时', type: 'info' },
  batch: { label: '批次', type: 'info' }
}

// 报工状态
export const workReportStatusMap = {
  pending_approval: { label: '待审批', type: 'warning' },
  submitted: { label: '已提交', type: 'warning' },
  approved: { label: '已通过', type: 'success' },
  rejected: { label: '已驳回', type: 'danger' }
}

// 工资状态（后端使用大写）
export const wageStatusMap = {
  pending: { label: '待确认', type: 'info' },
  draft: { label: '草稿', type: 'info' },
  confirmed: { label: '已确认', type: 'warning' },
  approved: { label: '已审批', type: '' },
  paid: { label: '已发放', type: 'success' },
  DRAFT: { label: '草稿', type: 'info' },
  CONFIRMED: { label: '已确认', type: 'warning' },
  APPROVED: { label: '已审批', type: '' },
  PAID: { label: '已发放', type: 'success' }
}

// 应收账款状态（后端使用大写）
export const receivableStatusMap = {
  pending: { label: '待收款', type: 'warning' },
  partial_received: { label: '部分收款', type: '' },
  received: { label: '已收款', type: 'success' },
  completed: { label: '已完成', type: 'success' },
  overdue: { label: '已逾期', type: 'danger' },
  PENDING: { label: '待收款', type: 'warning' },
  PARTIAL: { label: '部分收款', type: '' },
  PARTIAL_RECEIVED: { label: '部分收款', type: '' },
  COMPLETED: { label: '已完成', type: 'success' },
  OVERDUE: { label: '已逾期', type: 'danger' }
}

// 应付账款状态（后端使用大写）
export const payableStatusMap = {
  pending: { label: '待付款', type: 'warning' },
  partial_paid: { label: '部分付款', type: '' },
  paid: { label: '已付款', type: 'success' },
  completed: { label: '已完成', type: 'success' },
  overdue: { label: '已逾期', type: 'danger' },
  PENDING: { label: '待付款', type: 'warning' },
  PARTIAL: { label: '部分付款', type: '' },
  PARTIAL_PAID: { label: '部分付款', type: '' },
  PAID: { label: '已付款', type: 'success' },
  COMPLETED: { label: '已完成', type: 'success' },
  OVERDUE: { label: '已逾期', type: 'danger' }
}

// 兼容旧的 financeStatusMap 引用
export const financeStatusMap = {
  pending: { label: '待确认', type: 'info' },
  partial: { label: '部分收款', type: 'warning' },
  partial_received: { label: '部分收款', type: 'warning' },
  partial_paid: { label: '部分付款', type: 'warning' },
  received: { label: '已收款', type: 'success' },
  paid: { label: '已付款', type: 'success' },
  completed: { label: '已完成', type: 'success' },
  overdue: { label: '已逾期', type: 'danger' },
  settled: { label: '已结算', type: 'success' },
  PENDING: { label: '待确认', type: 'info' },
  PARTIAL: { label: '部分收款', type: 'warning' },
  COMPLETED: { label: '已完成', type: 'success' },
  OVERDUE: { label: '已逾期', type: 'danger' }
}

// 工序任务状态
export const processTaskStatusMap = {
  pending: { label: '待开工', type: 'info' },
  ready: { label: '可开工', type: '' },
  in_progress: { label: '进行中', type: 'warning' },
  pending_qc: { label: '待质检', type: 'warning' },
  completed: { label: '已完成', type: 'success' },
  blocked: { label: '已阻塞', type: 'danger' },
  cancelled: { label: '已取消', type: 'danger' }
}

// 工序外协状态
export const taskOutsourceStatusMap = {
  requested: { label: '已申请', type: 'warning' },
  ordered: { label: '已下单', type: '' },
  processing: { label: '加工中', type: 'warning' },
  completed: { label: '已完成', type: 'success' }
}

// 通用启用/禁用（兼容多种命名）
export const activeStatusMap = {
  active: { label: '启用', type: 'success' },
  enabled: { label: '启用', type: 'success' },
  disabled: { label: '停用', type: 'danger' },
  inactive: { label: '停用', type: 'danger' }
}

// 异常状态
export const exceptionStatusMap = {
  pending: { label: '待处理', type: 'warning' },
  processing: { label: '处理中', type: '' },
  resolved: { label: '已解决', type: 'success' },
  handled: { label: '已处理', type: 'success' }
}

// 退货单状态
export const returnOrderStatusMap = {
  pending: { label: '待审批', type: 'warning' },
  approved: { label: '已审批', type: 'success' },
  received: { label: '已收货', type: '' },
  completed: { label: '已完成', type: 'success' },
  rejected: { label: '已驳回', type: 'danger' }
}

// 入库/出库申请状态
export const invRequestStatusMap = {
  pending: { label: '待审批', type: 'warning' },
  approved: { label: '已审批', type: 'success' },
  rejected: { label: '已驳回', type: 'danger' },
  task_created: { label: '已建任务', type: '' }
}

// 库存任务状态
export const invTaskStatusMap = {
  pending: { label: '待执行', type: 'warning' },
  partial: { label: '部分完成', type: '' },
  completed: { label: '已完成', type: 'success' }
}

// 审批状态
export const approvalStatusMap = {
  pending: { label: '待审批', type: 'warning' },
  approved: { label: '已通过', type: 'success' },
  rejected: { label: '已驳回', type: 'danger' },
  cancelled: { label: '已取消', type: 'info' },
  PENDING: { label: '待审批', type: 'warning' },
  APPROVED: { label: '已通过', type: 'success' },
  REJECTED: { label: '已驳回', type: 'danger' },
  CANCELLED: { label: '已取消', type: 'info' }
}

// BOM版本状态
export const bomVersionStatusMap = {
  draft: { label: '草稿', type: 'info' },
  active: { label: '激活', type: 'success' },
  deprecated: { label: '已废弃', type: 'danger' }
}

// 工艺路线状态
export const processRouteStatusMap = {
  draft: { label: '草稿', type: 'info' },
  active: { label: '激活', type: 'success' },
  deprecated: { label: '已废弃', type: 'danger' }
}

// 生产计划状态
export const productionPlanStatusMap = {
  draft: { label: '草稿', type: 'info' },
  confirmed: { label: '已确认', type: '' },
  in_progress: { label: '执行中', type: 'warning' },
  completed: { label: '已完成', type: 'success' },
  cancelled: { label: '已取消', type: 'danger' }
}

// 生产计划明细状态
export const planItemStatusMap = {
  pending: { label: '待下达', type: 'info' },
  partial_released: { label: '部分下达', type: 'warning' },
  fully_released: { label: '已下达', type: '' },
  completed: { label: '已完成', type: 'success' }
}

// MRP运行状态
export const mrpRunStatusMap = {
  running: { label: '运行中', type: 'warning' },
  completed: { label: '已完成', type: 'success' },
  failed: { label: '失败', type: 'danger' }
}

// MRP建议状态
export const mrpSuggestionStatusMap = {
  pending: { label: '待处理', type: 'warning' },
  confirmed: { label: '已确认', type: '' },
  transferred: { label: '已转单', type: 'success' },
  ignored: { label: '已忽略', type: 'info' }
}

// 发货记录状态
export const shipmentStatusMap = {
  pending: { label: '待确认', type: 'warning' },
  shipped: { label: '已发货', type: 'success' },
  delivered: { label: '已送达', type: 'success' }
}

// 缺陷状态
export const defectStatusMap = {
  pending: { label: '待处理', type: 'warning' },
  handled: { label: '已处理', type: 'success' }
}

// 订单变更状态
export const orderChangeStatusMap = {
  pending: { label: '待审批', type: 'warning' },
  approved: { label: '已通过', type: 'success' },
  rejected: { label: '已驳回', type: 'danger' }
}

/**
 * 获取状态标签信息（大小写不敏感）
 * @param {Object} map 状态映射
 * @param {string} status 状态值
 * @returns {{ label: string, type: string }}
 */
export function getStatusInfo(map, status) {
  if (!status) return { label: '-', type: 'info' }
  // 先精确匹配，再尝试小写匹配
  return map[status] || map[status.toLowerCase()] || { label: status, type: 'info' }
}
