/**
 * 模块大屏共享中文字典
 * 用法:
 *   import { tr, INV_TYPE } from './dicts'
 *   donut(items, n => tr(INV_TYPE, n))   // 饼图标签
 *   {{ tr(INV_TYPE, row.invType) }}      // 表格单元格
 *
 * 未命中字典时返回原值,便于发现缺漏(不要静默替换为"其他")
 */

/** 通用 fallback 翻译 */
export function tr(dict, value) {
  if (value == null || value === '') return '-'
  const key = String(value).toLowerCase()
  return dict[key] || dict[String(value)] || value
}

// ============= 库存 =============
/** biz_inv_history.inv_type / biz_inv_task.inv_type */
export const INV_TYPE = {
  purchase_in: '采购入库',
  production_in: '生产入库',
  finished_in: '成品入库',
  semi_in: '半成品入库',
  return_in: '退货入库',
  rework_in: '返修入库',
  outsource_in: '外协入库',
  transfer_in: '调拨入库',
  adjust_in: '盘盈入库',
  stock_in: '其他入库',
  qc_in: '质检入库',
  sales_out: '销售出库',
  production_out: '生产领料',
  rework_out: '返修出库',
  outsource_out: '外协发料',
  transfer_out: '调拨出库',
  adjust_out: '盘亏出库',
  scrap_out: '报废出库',
  stock_out: '其他出库',
  return_out: '退料出库',
  qc_out: '质检出库'
}

/** biz_inv_task.status / biz_inv_request.status */
export const INV_TASK_STATUS = {
  pending: '待执行',
  approved: '已审批',
  task_created: '已生成任务',
  in_progress: '执行中',
  completed: '已完成',
  cancelled: '已取消',
  rejected: '已驳回'
}

// ============= 通用工序/工单/任务状态 =============
export const TASK_STATUS = {
  pending: '待执行',
  ready: '可执行',
  in_progress: '执行中',
  completed: '已完成',
  cancelled: '已取消',
  paused: '已暂停'
}

export const WO_STATUS = {
  pending: '待排产',
  scheduled: '已排产',
  in_progress: '生产中',
  completed: '已完工',
  cancelled: '已取消',
  pending_stock_in: '待入库',
  finished: '已入库'
}

// ============= 异常上报 =============
/** biz_exception_report.exception_type */
export const EXCEPTION_TYPE = {
  quality: '质量异常',
  equipment: '设备故障',
  material: '物料缺失',
  safety: '安全事故',
  environment: '环境异常',
  plan: '计划变更',
  process: '工艺异常',
  manpower: '人力不足',
  delivery: '交期异常',
  other: '其他异常',
  urgent: '紧急异常'
}

export const EXCEPTION_URGENCY = {
  urgent: '紧急',
  high: '高',
  normal: '普通',
  low: '低'
}

// ============= 质量 =============
export const DEFECT_LEVEL = {
  fatal: '致命',
  critical: '严重',
  major: '重要',
  minor: '一般',
  slight: '轻微'
}

export const QC_RESULT = { pending: '待检', pass: '合格', fail: '不合格' }
export const QC_TYPE = {
  incoming: '来料检 IQC',
  process: '过程检 IPQC',
  final: '成品检 FQC',
  outgoing: '出货检 OQC',
  outsource: '外协检',
  semi: '半成品检'
}

// ============= MRP =============
/** biz_mrp_run.status */
export const MRP_RUN_STATUS = {
  pending: '待运算',
  running: '运算中',
  success: '成功',
  failed: '失败',
  cancelled: '已取消'
}

/** biz_mrp_run.run_type */
export const MRP_RUN_TYPE = {
  manual: '手动运算',
  scheduled: '定时运算',
  auto: '自动运算',
  full: '全量运算',
  incremental: '增量运算'
}

/** biz_mrp_purchase_suggestion.status / biz_mrp_production_suggestion.status */
export const MRP_SUG_STATUS = {
  pending: '待确认',
  confirmed: '已确认',
  converted: '已转单',
  rejected: '已驳回',
  cancelled: '已取消'
}

/** biz_production_plan.status */
export const PLAN_STATUS = {
  draft: '草稿',
  released: '已发布',
  in_progress: '执行中',
  completed: '已完成',
  cancelled: '已取消'
}

// ============= 主数据 =============
/** biz_material.category */
export const MATERIAL_CATEGORY = {
  raw: '原材料',
  semi: '半成品',
  finished: '成品',
  packaging: '包装材料',
  consumable: '辅料/耗材',
  spare: '备件',
  scrap: '废料',
  product: '成品',
  material: '原材料'
}

// ============= 报工 =============
/** biz_work_report.report_type */
export const REPORT_TYPE = {
  individual: '个人报工',
  team: '班组报工',
  personal: '个人报工'
}

// ============= 销售/采购订单 =============
export const SO_STATUS = {
  draft: '草稿',
  pending_approval: '待审批',
  confirmed: '已确认',
  mrp_planned: '已转计划',
  in_production: '生产中',
  partial_shipped: '部分发货',
  shipped: '已发货',
  settled: '已结算',
  cancelled: '已取消',
  voided: '已作废',
  completed: '已完成'
}

export const PO_STATUS = {
  draft: '草稿',
  pending_approval: '待审批',
  confirmed: '已确认',
  partial_received: '部分到货',
  received: '已到货',
  rejected: '已驳回',
  closed: '已关闭',
  returned: '已退货'
}

export const REQ_STATUS = {
  pending: '待审批',
  approved: '已审批',
  issued: '已下发',
  rejected: '已驳回'
}

// ============= 财务 =============
export const AR_STATUS = { pending: '待收款', partial: '部分收款', received: '已收清' }
export const AP_STATUS = { pending: '待付款', partial: '部分付款', paid: '已付清' }

// ============= 外协 =============
export const OUTSOURCE_REQUEST_STATUS = {
  pending: '待审批',
  approved: '已审批',
  rejected: '已驳回',
  cancelled: '已取消'
}
export const OUTSOURCE_ORDER_STATUS = {
  draft: '草稿',
  confirmed: '已确认',
  material_sent: '已发料',
  processing: '加工中',
  partial_received: '部分收货',
  received: '已收货',
  settled: '已结算',
  cancelled: '已取消'
}

// ============= 优先级 =============
export const PRIORITY = { urgent: '紧急', high: '高', normal: '普通', low: '低' }

// ============= EAM =============
export const EAM_TASK_STATUS = { 0: '待执行', 1: '执行中', 2: '已完成', 3: '已取消', 4: '已暂停' }
export const EAM_REPAIR_STATUS = {
  pending: '待处理', in_progress: '维修中', paused: '已暂停', completed: '已完成', cancelled: '已取消'
}
export const EAM_EQ_STATUS = {
  NORMAL: '正常运行', MAINTENANCE: '维修中', RETIRED: '已停用',
  IDLE: '空闲', RUNNING: '运行中', OFFLINE: '离线',
  normal: '正常运行', maintenance: '维修中', retired: '已停用'
}
