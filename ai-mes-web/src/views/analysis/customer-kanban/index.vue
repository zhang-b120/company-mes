<template>
  <div v-loading="loading" class="page-container">
    <!-- 汇总表格 -->
    <el-card shadow="hover" class="table-card">
      <template #header><span class="card-title">客户统计汇总</span></template>
      <el-table :data="summaryTable" border size="small" max-height="360" stripe
        :default-sort="{ prop: 'orderAmount', order: 'descending' }">
        <el-table-column prop="customerNo" label="客户编号" width="120" />
        <el-table-column prop="name" label="客户名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="orderAmount" label="销售额" width="120" sortable align="right">
          <template #default="{ row }">¥{{ formatMoney(row.orderAmount) }}</template>
        </el-table-column>
        <el-table-column prop="paymentRate" label="回款率" width="100" sortable align="center">
          <template #default="{ row }">
            <span :style="{ color: row.paymentRate >= 80 ? '#67c23a' : row.paymentRate >= 50 ? '#e6a23c' : '#f56c6c' }">
              {{ row.paymentRate }}%
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="monthPayment" label="月收款" width="120" sortable align="right">
          <template #default="{ row }">¥{{ formatMoney(row.monthPayment) }}</template>
        </el-table-column>
        <el-table-column prop="yearPayment" label="年收款" width="120" sortable align="right">
          <template #default="{ row }">¥{{ formatMoney(row.yearPayment) }}</template>
        </el-table-column>
        <el-table-column prop="debtAmount" label="欠款" width="120" sortable align="right">
          <template #default="{ row }">
            <span :style="{ color: row.debtAmount > 0 ? '#f56c6c' : '#67c23a' }">¥{{ formatMoney(row.debtAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="invoiceAmount" label="开票金额" width="120" sortable align="right">
          <template #default="{ row }">¥{{ formatMoney(row.invoiceAmount) }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 第一行：销售额Top10 + 欠款Top10 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :xs="24" :sm="12">
        <el-card shadow="hover">
          <template #header><span class="card-title">客户销售额排名 Top10</span></template>
          <div ref="topAmountRef" class="chart-box"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12">
        <el-card shadow="hover">
          <template #header><span class="card-title">客户欠款排名 Top10</span></template>
          <div ref="topDebtRef" class="chart-box"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 第二行：回款Top10 + 销售额饼图 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :xs="24" :sm="12">
        <el-card shadow="hover">
          <template #header><span class="card-title">客户回款排名 Top10</span></template>
          <div ref="topPaymentRef" class="chart-box"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12">
        <el-card shadow="hover">
          <template #header><span class="card-title">客户销售额分布</span></template>
          <div ref="salesPieRef" class="chart-box"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 第三行：地区分布 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :span="24">
        <el-card shadow="hover">
          <template #header><span class="card-title">客户地区分布</span></template>
          <div ref="regionRef" class="chart-box chart-box-lg"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts'
import { getCustomerKanbanAll } from '@/api/analysis'

const loading = ref(false)
const summaryTable = ref([])

const topAmountRef = ref(null)
const topDebtRef = ref(null)
const topPaymentRef = ref(null)
const salesPieRef = ref(null)
const regionRef = ref(null)

let chartInstances = []
const colors = ['#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#9b59b6', '#00b4d8', '#ff6b6b', '#ffa94d', '#51cf66', '#845ef7']

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

async function loadData() {
  loading.value = true
  try {
    const res = await getCustomerKanbanAll()
    const data = res.data || {}
    summaryTable.value = data.summaryTable || []
    await nextTick()
    renderHorizontalBar(topAmountRef.value, data.topByAmount || [], 'totalAmount', '#409eff')
    renderHorizontalBar(topDebtRef.value, data.topByDebt || [], 'value', '#f56c6c')
    renderHorizontalBar(topPaymentRef.value, data.topByPayment || [], 'value', '#67c23a')
    renderSalesPie(data.topByAmount || [])
    renderRegionBar(data.regionDistribution || [])
  } catch {
    // handled by interceptor
  } finally {
    loading.value = false
  }
}

function renderHorizontalBar(refEl, data, valueKey, color) {
  const chart = initChart(refEl)
  if (!chart || data.length === 0) return
  const reversed = [...data].reverse()
  chart.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: params => {
        const p = params[0]
        return `${p.name}<br/>${p.seriesName}: ¥${formatMoney(p.value)}`
      }
    },
    grid: { left: 120, right: 40, bottom: 20, top: 10 },
    xAxis: { type: 'value', axisLabel: { formatter: v => v >= 10000 ? (v / 10000).toFixed(0) + '万' : v } },
    yAxis: { type: 'category', data: reversed.map(d => d.name), axisLabel: { width: 100, overflow: 'truncate' } },
    series: [{
      name: '金额',
      type: 'bar',
      data: reversed.map(d => Number(d[valueKey] || 0)),
      barWidth: 20,
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
          { offset: 0, color: color + '88' },
          { offset: 1, color: color }
        ]),
        borderRadius: [0, 4, 4, 0]
      }
    }]
  })
}

function renderSalesPie(data) {
  const chart = initChart(salesPieRef.value)
  if (!chart || data.length === 0) return
  chart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: ¥{c} ({d}%)' },
    legend: { bottom: 0, type: 'scroll' },
    color: colors,
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      center: ['50%', '45%'],
      data: data.map(d => ({ name: d.name, value: Number(d.totalAmount || 0) })),
      label: { formatter: '{b}\n{d}%' },
      emphasis: { itemStyle: { shadowBlur: 10, shadowColor: 'rgba(0,0,0,0.2)' } }
    }]
  })
}

function renderRegionBar(data) {
  const chart = initChart(regionRef.value)
  if (!chart || data.length === 0) return
  chart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 80, right: 40, bottom: 30, top: 10 },
    xAxis: { type: 'category', data: data.map(d => d.name), axisLabel: { rotate: 30 } },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{
      name: '客户数',
      type: 'bar',
      data: data.map(d => Number(d.value || 0)),
      barWidth: 30,
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#409eff' },
          { offset: 1, color: '#409eff44' }
        ]),
        borderRadius: [4, 4, 0, 0]
      }
    }]
  })
}

function handleResize() {
  chartInstances.forEach(c => c?.resize())
}

onMounted(() => {
  loadData()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  chartInstances.forEach(c => c?.dispose())
  chartInstances = []
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped lang="scss">
.page-container { padding: 16px; }
.table-card { margin-bottom: 16px; }
.chart-row { margin-bottom: 16px; }
.card-title { font-weight: 600; font-size: 15px; }
.chart-box { height: 340px; }
.chart-box-lg { height: 300px; }
</style>
