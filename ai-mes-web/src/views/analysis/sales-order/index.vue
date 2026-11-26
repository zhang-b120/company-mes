<template>
  <div v-loading="loading" class="page-container">
    <!-- KPI 卡片 -->
    <el-row :gutter="16" class="kpi-row">
      <el-col :xs="12" :sm="4">
        <el-card shadow="hover" class="kpi-card kpi-blue">
          <div class="kpi-icon"><el-icon><Document /></el-icon></div>
          <div class="kpi-body">
            <div class="kpi-value">{{ summary.totalCount || 0 }}</div>
            <div class="kpi-label">总订单数</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="4">
        <el-card shadow="hover" class="kpi-card kpi-green">
          <div class="kpi-icon"><el-icon><Wallet /></el-icon></div>
          <div class="kpi-body">
            <div class="kpi-value">¥{{ formatMoney(summary.totalAmount) }}</div>
            <div class="kpi-label">总金额</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="4">
        <el-card shadow="hover" class="kpi-card kpi-orange">
          <div class="kpi-icon"><el-icon><Coin /></el-icon></div>
          <div class="kpi-body">
            <div class="kpi-value">¥{{ formatMoney(summary.avgAmount) }}</div>
            <div class="kpi-label">平均单价</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="4">
        <el-card shadow="hover" class="kpi-card kpi-purple">
          <div class="kpi-icon"><el-icon><TrendCharts /></el-icon></div>
          <div class="kpi-body">
            <div class="kpi-value">{{ summary.completionRate || 0 }}%</div>
            <div class="kpi-label">完成率</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="4">
        <el-card shadow="hover" class="kpi-card kpi-cyan">
          <div class="kpi-icon"><el-icon><Calendar /></el-icon></div>
          <div class="kpi-body">
            <div class="kpi-value">{{ summary.monthCount || 0 }}</div>
            <div class="kpi-label">本月订单</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="4">
        <el-card shadow="hover" class="kpi-card kpi-red">
          <div class="kpi-icon"><el-icon><Money /></el-icon></div>
          <div class="kpi-body">
            <div class="kpi-value">¥{{ formatMoney(summary.monthAmount) }}</div>
            <div class="kpi-label">本月金额</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 第一行：订单趋势 + 状态分布 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :xs="24" :sm="16">
        <el-card shadow="hover">
          <template #header><span class="card-title">订单趋势（近12个月）</span></template>
          <div ref="orderTrendRef" class="chart-box"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="8">
        <el-card shadow="hover">
          <template #header><span class="card-title">订单状态分布</span></template>
          <div ref="statusPieRef" class="chart-box"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 第二行：月度营收 + 交付准时率 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :xs="24" :sm="14">
        <el-card shadow="hover">
          <template #header><span class="card-title">月度营收与回款（近12个月）</span></template>
          <div ref="revenueRef" class="chart-box"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="10">
        <el-card shadow="hover">
          <template #header><span class="card-title">交付准时率</span></template>
          <div ref="deliveryRef" class="chart-box"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 第三行：Top10产品 + 业务员排名 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :xs="24" :sm="12">
        <el-card shadow="hover">
          <template #header><span class="card-title">Top10产品销量</span></template>
          <div ref="topProductsRef" class="chart-box chart-box-tall"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12">
        <el-card shadow="hover">
          <template #header><span class="card-title">业务员排名 Top10</span></template>
          <div ref="salesRankRef" class="chart-box chart-box-tall"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 第四行：客户排名 + 金额分布 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :xs="24" :sm="12">
        <el-card shadow="hover">
          <template #header><span class="card-title">客户排名 Top10</span></template>
          <div ref="customerRankRef" class="chart-box chart-box-tall"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12">
        <el-card shadow="hover">
          <template #header><span class="card-title">订单金额区间分布</span></template>
          <div ref="amountDistRef" class="chart-box chart-box-tall"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 第五行：每日订单 + 结算金额分布 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :xs="24" :sm="14">
        <el-card shadow="hover">
          <template #header><span class="card-title">近30日每日订单数</span></template>
          <div ref="dailyCountRef" class="chart-box"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="10">
        <el-card shadow="hover">
          <template #header><span class="card-title">结算方式金额分布</span></template>
          <div ref="settlementPieRef" class="chart-box"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts'
import { getSalesOrderAnalysisAll } from '@/api/analysis'

// --- state ---
const loading = ref(false)
const summary = ref({})

// --- chart refs ---
const orderTrendRef = ref(null)
const statusPieRef = ref(null)
const revenueRef = ref(null)
const deliveryRef = ref(null)
const topProductsRef = ref(null)
const salesRankRef = ref(null)
const customerRankRef = ref(null)
const amountDistRef = ref(null)
const dailyCountRef = ref(null)
const settlementPieRef = ref(null)

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

const statusMap = {
  draft: '草稿', pending_approval: '待审批', confirmed: '已确认',
  mrp_planned: 'MRP已计划', in_production: '生产中', partial_shipped: '部分发货',
  shipped: '已发货', completed: '已完成', settled: '已结算',
  cancelled: '已取消', void: '已作废', voided: '已作废'
}

// --- data load & render ---
async function loadData() {
  loading.value = true
  try {
    const res = await getSalesOrderAnalysisAll()
    const data = res.data || {}
    summary.value = data.summary || {}
    await nextTick()
    renderOrderTrend(data.orderTrend || [])
    renderStatusPie(data.statusDistribution || [])
    renderRevenue(data.monthlyRevenue || [])
    renderDelivery(data.deliveryPerformance || {})
    renderTopProducts(data.topProducts || [])
    renderSalesRank(data.salesRanking || [])
    renderCustomerRank(data.customerRanking || [])
    renderAmountDist(data.amountDistribution || [])
    renderDailyCount(data.dailyOrderCount || [])
    renderSettlementPie(data.settlementAmountDistribution || [])
  } catch {
    // handled by interceptor
  } finally {
    loading.value = false
  }
}

// 订单趋势 - 双轴折线图
function renderOrderTrend(data) {
  const chart = initChart(orderTrendRef.value)
  if (!chart) return
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['订单数', '订单金额'], bottom: 0 },
    grid: { left: 50, right: 60, bottom: 40, top: 20 },
    xAxis: { type: 'category', data: data.map(d => d.month), boundaryGap: false },
    yAxis: [
      { type: 'value', name: '订单数', minInterval: 1 },
      { type: 'value', name: '金额(元)', position: 'right' }
    ],
    series: [
      {
        name: '订单数',
        type: 'line',
        data: data.map(d => d.orderCount),
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

// 订单状态分布 - 环形饼图
function renderStatusPie(data) {
  const chart = initChart(statusPieRef.value)
  if (!chart) return
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

// 月度营收与回款 - 双柱状图
function renderRevenue(data) {
  const chart = initChart(revenueRef.value)
  if (!chart) return
  chart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { data: ['营收', '回款'], bottom: 0 },
    grid: { left: 60, right: 20, bottom: 40, top: 20 },
    xAxis: { type: 'category', data: data.map(d => d.month) },
    yAxis: { type: 'value' },
    series: [
      {
        name: '营收',
        type: 'bar',
        data: data.map(d => Number(d.revenue || 0)),
        itemStyle: { color: '#409eff' },
        barMaxWidth: 30
      },
      {
        name: '回款',
        type: 'bar',
        data: data.map(d => Number(d.received || 0)),
        itemStyle: { color: '#67c23a' },
        barMaxWidth: 30
      }
    ]
  })
}

// 交付准时率 - 仪表盘 + 指标
function renderDelivery(data) {
  const chart = initChart(deliveryRef.value)
  if (!chart) return
  const onTime = Number(data.onTime || 0)
  const late = Number(data.late || 0)
  const overdue = Number(data.overdue || 0)
  // 分母仅包含已判定的订单(已发货/逾期),未到期未发货的不计入
  const judged = onTime + late + overdue
  const rate = judged > 0 ? Number(((onTime / judged) * 100).toFixed(1)) : 0
  chart.setOption({
    series: [
      {
        type: 'gauge',
        center: ['50%', '55%'],
        radius: '70%',
        startAngle: 200,
        endAngle: -20,
        min: 0,
        max: 100,
        axisLine: {
          lineStyle: {
            width: 20,
            color: [[0.6, '#f56c6c'], [0.8, '#e6a23c'], [1, '#67c23a']]
          }
        },
        pointer: { length: '60%', width: 5, itemStyle: { color: '#303133' } },
        axisTick: { show: false },
        splitLine: { show: false },
        axisLabel: { show: false },
        detail: {
          valueAnimation: true,
          formatter: '{value}%',
          fontSize: 24,
          fontWeight: 'bold',
          offsetCenter: [0, '30%'],
          color: rate >= 80 ? '#67c23a' : rate >= 60 ? '#e6a23c' : '#f56c6c'
        },
        title: { show: false },
        data: [{ value: rate }]
      }
    ],
    graphic: [
      { type: 'text', left: 'center', bottom: 10, style: { text: `准时: ${onTime}  延迟: ${late}  逾期: ${overdue}`, fill: '#909399', fontSize: 12 } }
    ]
  })
}

// Top10产品 - 横向柱状图
function renderTopProducts(data) {
  const chart = initChart(topProductsRef.value)
  if (!chart) return
  const sorted = [...data].reverse()
  chart.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter(params) {
        const p = params[0]
        const item = sorted[p.dataIndex]
        return `${p.name}<br/>金额: ¥${Number(item.totalAmount || 0).toLocaleString()}<br/>数量: ${item.totalQty || 0}`
      }
    },
    grid: { left: 110, right: 30, bottom: 20, top: 10 },
    xAxis: { type: 'value' },
    yAxis: { type: 'category', data: sorted.map(d => d.name), axisLabel: { width: 90, overflow: 'truncate' } },
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

// 业务员排名 - 横向柱状图
function renderSalesRank(data) {
  const chart = initChart(salesRankRef.value)
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
    grid: { left: 80, right: 30, bottom: 20, top: 10 },
    xAxis: { type: 'value' },
    yAxis: { type: 'category', data: sorted.map(d => d.name) },
    series: [{
      type: 'bar',
      data: sorted.map(d => Number(d.totalAmount || 0)),
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
          { offset: 0, color: '#e6a23c' },
          { offset: 1, color: '#f56c6c' }
        ])
      },
      barMaxWidth: 20
    }]
  })
}

// 客户排名 - 横向柱状图
function renderCustomerRank(data) {
  const chart = initChart(customerRankRef.value)
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
    yAxis: { type: 'category', data: sorted.map(d => d.name), axisLabel: { width: 80, overflow: 'truncate' } },
    series: [{
      type: 'bar',
      data: sorted.map(d => Number(d.totalAmount || 0)),
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
          { offset: 0, color: '#9b59b6' },
          { offset: 1, color: '#409eff' }
        ])
      },
      barMaxWidth: 20
    }]
  })
}

// 订单金额区间分布 - 柱状图
function renderAmountDist(data) {
  const chart = initChart(amountDistRef.value)
  if (!chart) return
  chart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 50, right: 20, bottom: 30, top: 20 },
    xAxis: { type: 'category', data: data.map(d => d.name), axisLabel: { rotate: 15 } },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{
      type: 'bar',
      data: data.map((d, i) => ({
        value: d.value,
        itemStyle: { color: colors[i % colors.length] }
      })),
      barMaxWidth: 50
    }]
  })
}

// 近30日每日订单数 - 折线图
function renderDailyCount(data) {
  const chart = initChart(dailyCountRef.value)
  if (!chart) return
  chart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 50, right: 20, bottom: 30, top: 20 },
    xAxis: {
      type: 'category',
      data: data.map(d => d.date ? d.date.substring(5) : ''),
      boundaryGap: false
    },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{
      name: '订单数',
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

// 结算方式金额分布 - 饼图
function renderSettlementPie(data) {
  const chart = initChart(settlementPieRef.value)
  if (!chart) return
  chart.setOption({
    tooltip: {
      trigger: 'item',
      formatter(params) {
        return `${params.name}: ¥${Number(params.value || 0).toLocaleString()} (${params.percent}%)`
      }
    },
    legend: { bottom: 0 },
    color: colors,
    series: [{
      type: 'pie',
      radius: ['40%', '65%'],
      center: ['50%', '45%'],
      data: data.map(d => ({ name: d.name, value: Number(d.value || 0) })),
      label: { formatter: '{b}\n{d}%' }
    }]
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
    gap: 12px;
    padding: 16px;
  }
}
.kpi-blue { border-left-color: #409eff; }
.kpi-green { border-left-color: #67c23a; }
.kpi-orange { border-left-color: #e6a23c; }
.kpi-purple { border-left-color: #9b59b6; }
.kpi-cyan { border-left-color: #00b4d8; }
.kpi-red { border-left-color: #f56c6c; }

.kpi-icon {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  color: #fff;
  flex-shrink: 0;
}
.kpi-blue .kpi-icon { background: #409eff; }
.kpi-green .kpi-icon { background: #67c23a; }
.kpi-orange .kpi-icon { background: #e6a23c; }
.kpi-purple .kpi-icon { background: #9b59b6; }
.kpi-cyan .kpi-icon { background: #00b4d8; }
.kpi-red .kpi-icon { background: #f56c6c; }

.kpi-value {
  font-size: 24px;
  font-weight: bold;
  color: #333;
  line-height: 1.2;
}
.kpi-label {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
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
