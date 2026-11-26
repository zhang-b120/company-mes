<!--
  通用状态标签组件
  根据 status 值自动映射颜色和中文名称
  用法:
    <StatusTag status="active" />
    <StatusTag status="pending" type="warning" />  手动覆盖类型
-->
<template>
  <el-tag :type="tagType" size="small">
    {{ tagLabel }}
  </el-tag>
</template>

<script setup>
/**
 * @prop {string} status - 状态值（如 active, disabled, pending 等）
 * @prop {string} type   - 手动指定 el-tag 的类型，优先级高于内置映射
 */
import { computed } from 'vue'

const props = defineProps({
  status: {
    type: String,
    default: ''
  },
  type: {
    type: String,
    default: ''
  }
})

/**
 * 状态 → el-tag type 映射表
 * success(绿): 激活、通过、已审批、已确认、已完成
 * danger(红):  禁用、失败、已取消、已驳回、已逾期
 * info(灰):    待处理、草稿
 * primary(蓝): 进行中、处理中、检验中
 * warning(黄): 待审批
 */
const typeMap = {
  // 成功/绿色状态
  active: 'success',
  pass: 'success',
  approved: 'success',
  confirmed: 'success',
  completed: 'success',
  settled: 'success',
  shipped: 'success',
  enabled: 'success',
  received: 'success',
  paid: 'success',
  resolved: 'success',

  // 危险/红色状态
  disabled: 'danger',
  fail: 'danger',
  cancelled: 'danger',
  rejected: 'danger',
  overdue: 'danger',

  // 信息/灰色状态
  pending: 'info',
  draft: 'info',
  inactive: 'info',
  closed: 'info',

  // 主要/蓝色状态
  scheduled: '',
  in_progress: '',
  processing: '',
  inspecting: '',
  in_production: '',
  delivering: '',
  receiving: '',
  ready: '',
  ordered: '',

  // 警告/黄色状态
  pending_approval: 'warning',
  reviewing: 'warning',
  submitted: 'warning',
  partial: 'warning',
  partial_shipped: 'warning'
}

/**
 * 状态 → 中文名称映射表
 */
const labelMap = {
  active: '启用',
  disabled: '停用',
  enabled: '启用',
  inactive: '停用',
  pass: '合格',
  fail: '不合格',
  pending: '待处理',
  draft: '草稿',
  in_progress: '进行中',
  processing: '处理中',
  inspecting: '质检中',
  in_production: '生产中',
  approved: '已审批',
  confirmed: '已确认',
  completed: '已完成',
  cancelled: '已取消',
  rejected: '已驳回',
  overdue: '已逾期',
  settled: '已结算',
  shipped: '已发货',
  closed: '已关闭',
  pending_approval: '待审批',
  reviewing: '审核中',
  submitted: '已提交',
  scheduled: '已排产',
  received: '已收货',
  receiving: '收货中',
  delivering: '交付中',
  ordered: '已下单',
  ready: '可开工',
  paid: '已发放',
  partial: '部分收款',
  partial_shipped: '部分发货',
  resolved: '已解决'
}

/** 计算 el-tag 的 type 属性 */
const tagType = computed(() => {
  // 手动传入的 type 优先
  if (props.type) return props.type
  return typeMap[props.status] ?? 'info'
})

/** 计算显示的标签文本 */
const tagLabel = computed(() => {
  return labelMap[props.status] || props.status || '-'
})
</script>
