<template>
  <div v-loading="loading" class="page-container">
    <!-- KPI 卡片 -->
    <el-row :gutter="16" class="kpi-row">
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="kpi-card kpi-blue">
          <div class="kpi-icon"><el-icon><User /></el-icon></div>
          <div class="kpi-body">
            <div class="kpi-value">{{ summary.total || 0 }}</div>
            <div class="kpi-label">客户总数</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="kpi-card kpi-green">
          <div class="kpi-icon"><el-icon><CircleCheck /></el-icon></div>
          <div class="kpi-body">
            <div class="kpi-value">{{ summary.activeCount || 0 }}</div>
            <div class="kpi-label">活跃客户</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="kpi-card kpi-orange">
          <div class="kpi-icon"><el-icon><Plus /></el-icon></div>
          <div class="kpi-body">
            <div class="kpi-value">{{ summary.newThisMonth || 0 }}</div>
            <div class="kpi-label">本月新增</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="kpi-card kpi-purple">
          <div class="kpi-icon"><el-icon><Wallet /></el-icon></div>
          <div class="kpi-body">
            <div class="kpi-value">¥{{ formatMoney(summary.totalCredit) }}</div>
            <div class="kpi-label">信用总额</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 第一行：状态分布 + 增长趋势 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :xs="24" :sm="8">
        <el-card shadow="hover">
          <template #header><span class="card-title">客户状态分布</span></template>
          <div ref="statusPieRef" class="chart-box"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="16">
        <el-card shadow="hover">
          <template #header><span class="card-title">客户增长趋势（近12个月）</span></template>
          <div ref="growthTrendRef" class="chart-box"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 第二行：结算方式分布 + 下单频次 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :xs="24" :sm="12">
        <el-card shadow="hover">
          <template #header><span class="card-title">结算方式分布</span></template>
          <div ref="settlementPieRef" class="chart-box"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12">
        <el-card shadow="hover">
          <template #header><span class="card-title">下单频次分布</span></template>
          <div ref="frequencyBarRef" class="chart-box"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 第三行：Top10客户 + 信用额度使用率 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :xs="24" :sm="12">
        <el-card shadow="hover">
          <template #header><span class="card-title">Top10客户（按订单金额）</span></template>
          <div ref="topAmountRef" class="chart-box chart-box-tall"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12">
        <el-card shadow="hover">
          <template #header><span class="card-title">信用额度使用率 Top10</span></template>
          <div ref="creditUsageRef" class="chart-box chart-box-tall"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 第四行：月度订单趋势 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :span="24">
        <el-card shadow="hover">
          <template #header><span class="card-title">月度订单趋势（近6个月）</span></template>
          <div ref="monthlyTrendRef" class="chart-box"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts'
import { getCustomerAnalysisAll } from '@/api/analysis'

// --- state ---
const loading = ref(false)
const summary = ref({})

// --- chart refs ---
const statusPieRef = ref(null)
const growthTrendRef = ref(null)
const settlementPieRef = ref(null)
const frequencyBarRef = ref(null)
const topAmountRef = ref(null)
const creditUsageRef = ref(null)
const monthlyTrendRef = ref(null)

let chartInstances = []

// --- helpers ---
function formatMoney(val) {
  const num = Number(val || 0)
  if (num >= 10000) return (num / 10000).toFixed(2) + '万'
  return num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function initChart(refEl) {
  if (!refEl) return null
  const instance = echarts.init(refEl)
  chartInstances.push(instance)
  return instance
}

const colors = ['#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#9b59b6', '#00b4d8', '#ff6b6b', '#ffa94d', '#51cf66', '#845ef7']

// --- data load & render ---
async function loadData() {
  loading.value = true
  try {
    const res = await getCustomerAnalysisAll()
    const data = res.data || {}
    summary.value = data.summary || {}
    await nextTick()
    renderStatusPie(data.statusDistribution || [])
    renderGrowthTrend(data.growthTrend || [])
    renderSettlementPie(data.settlementDistribution || [])
    renderFrequencyBar(data.orderFrequency || [])
    renderTopAmount(data.topByAmount || [])
    renderCreditUsage(data.creditUsage || [])
    renderMonthlyTrend(data.monthlyOrderTrend || [])
  } catch {
    // handled by interceptor
  } finally {
    loading.value = false
  }
}

// 客户状态分布 - 环形图
function renderStatusPie(data) {
  const chart = initChart(statusPieRef.value)
  if (!chart) return
  const statusMap = { active: '活跃', inactive: '停用', blacklist: '黑名单' }
  chart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0 },
    color: colors,
    series: [{
      type: 'pie',
      radius: ['40%', '65%'],
      center: ['50%', '45%'],
      data: data.map(d => ({ name: statusMap[d.name] || d.name, value: d.value })),
      label: { formatter: '{b}\n{d}%' }
    }]
  })
}

// 客户增长趋势 - 折线图
function renderGrowthTrend(data) {
  const chart = initChart(growthTrendRef.value)
  if (!chart) return
  chart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 50, right: 20, bottom: 30, top: 20 },
    xAxis: { type: 'category', data: data.map(d => d.month), boundaryGap: false },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{
      name: '新增客户',
      type: 'line',
      data: data.map(d => d.count),
      smooth: true,
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(64,158,255,0.3)' },
          { offset: 1, color: 'rgba(64,158,255,0.05)' }
        ])
      },
      itemStyle: { color: '#409eff' }
    }]
  })
}

// 结算方式分布 - 饼图
function renderSettlementPie(data) {
  const chart = initChart(settlementPieRef.value)
  if (!chart) return
  const nameMap = { cash: '现金', monthly_30: '月结30天', monthly_60: '月结60天', monthly_90: '月结90天' }
  chart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0 },
    color: colors,
    series: [{
      type: 'pie',
      radius: ['40%', '65%'],
      center: ['50%', '45%'],
      data: data.map(d => ({ name: nameMap[d.name] || d.name, value: d.value })),
      label: { formatter: '{b}\n{d}%' }
    }]
  })
}

// 下单频次分布 - 柱状图
function renderFrequencyBar(data) {
  const chart = initChart(frequencyBarRef.value)
  if (!chart) return
  chart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 50, right: 20, bottom: 30, top: 20 },
    xAxis: { type: 'category', data: data.map(d => d.name) },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{
      type: 'bar',
      data: data.map(d => d.value),
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#409eff' },
          { offset: 1, color: '#a0cfff' }
        ])
      },
      barMaxWidth: 50
    }]
  })
}

// Top10客户按金额 - 横向柱状图
function renderTopAmount(data) {
  const chart = initChart(topAmountRef.value)
  if (!chart) return
  const sorted = [...data].reverse()
  chart.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter(params) {
        const p = params[0]
        const item = sorted[p.dataIndex]
        return `${p.name}<br/>金额: ¥${Number(item.totalAmount || 0).toLocaleString()}<br/>订单数: ${item.orderCount || 0}`
      }
    },
    grid: { left: 100, right: 30, bottom: 20, top: 10 },
    xAxis: { type: 'value' },
    yAxis: { type: 'category', data: sorted.map(d => d.name) },
    series: [{
      type: 'bar',
      data: sorted.map(d => Number(d.totalAmount || 0)),
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
          { offset: 0, color: '#409eff' },
          { offset: 1, color: '#67c23a' }
        ])
      },
      barMaxWidth: 20
    }]
  })
}

// 信用额度使用率 - 横向柱状图
function renderCreditUsage(data) {
  const chart = initChart(creditUsageRef.value)
  if (!chart) return
  const sorted = [...data].reverse()
  chart.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter(params) {
        const idx = params[0].dataIndex
        const item = sorted[idx]
        const rate = item.creditLimit > 0 ? ((item.creditUsed / item.creditLimit) * 100).toFixed(1) : 0
        return `${item.name}<br/>额度: ¥${Number(item.creditLimit).toLocaleString()}<br/>已用: ¥${Number(item.creditUsed).toLocaleString()}<br/>使用率: ${rate}%`
      }
    },
    grid: { left: 100, right: 30, bottom: 20, top: 10 },
    xAxis: { type: 'value', max: 100, axisLabel: { formatter: '{value}%' } },
    yAxis: { type: 'category', data: sorted.map(d => d.name) },
    series: [{
      type: 'bar',
      data: sorted.map(d => d.creditLimit > 0 ? Number(((d.creditUsed / d.creditLimit) * 100).toFixed(1)) : 0),
      itemStyle: {
        color(params) {
          const v = params.value
          if (v >= 80) return '#f56c6c'
          if (v >= 50) return '#e6a23c'
          return '#67c23a'
        }
      },
      barMaxWidth: 20
    }]
  })
}

// 月度订单趋势 - 双轴折线图
function renderMonthlyTrend(data) {
  const chart = initChart(monthlyTrendRef.value)
  if (!chart) return
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['下单客户数', '订单金额'], bottom: 0 },
    grid: { left: 50, right: 60, bottom: 40, top: 20 },
    xAxis: { type: 'category', data: data.map(d => d.month), boundaryGap: false },
    yAxis: [
      { type: 'value', name: '客户数', minInterval: 1 },
      { type: 'value', name: '金额(元)', position: 'right' }
    ],
    series: [
      {
        name: '下单客户数',
        type: 'line',
        data: data.map(d => d.customerCount),
        smooth: true,
        itemStyle: { color: '#409eff' }
      },
      {
        name: '订单金额',
        type: 'line',
        yAxisIndex: 1,
        data: data.map(d => Number(d.totalAmount || 0)),
        smooth: true,
        itemStyle: { color: '#67c23a' }
      }
    ]
  })
}

// --- resize ---
function handleResize() {
  chartInstances.forEach(c => c?.resize())
}

// --- lifecycle ---
onMounted(() => {
  loadData()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chartInstances.forEach(c => c?.dispose())
  chartInstances = []
})
</script>

<style scoped lang="scss">
.page-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.kpi-row {
  .el-col { margin-bottom: 0; }
}

.kpi-card {
  border-left: 4px solid;
  :deep(.el-card__body) {
    display: flex;
    align-items: center;
    gap: 16px;
    padding: 20px;
  }
}
.kpi-blue { border-left-color: #409eff; }
.kpi-green { border-left-color: #67c23a; }
.kpi-orange { border-left-color: #e6a23c; }
.kpi-purple { border-left-color: #9b59b6; }

.kpi-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  color: #fff;
  flex-shrink: 0;
}
.kpi-blue .kpi-icon { background: #409eff; }
.kpi-green .kpi-icon { background: #67c23a; }
.kpi-orange .kpi-icon { background: #e6a23c; }
.kpi-purple .kpi-icon { background: #9b59b6; }

.kpi-value {
  font-size: 28px;
  font-weight: bold;
  color: #333;
  line-height: 1.2;
}
.kpi-label {
  font-size: 13px;
  color: #909399;
  margin-top: 4px;
}

.card-title {
  font-weight: 600;
  font-size: 15px;
}

.chart-box {
  height: 320px;
  width: 100%;
}
.chart-box-tall {
  height: 400px;
}
</style>
