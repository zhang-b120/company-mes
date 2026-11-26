<template>
  <div class="page-container">
    <!-- 时间筛选 -->
    <el-card shadow="never" class="filter-card">
      <div style="display:flex;align-items:center;gap:12px">
        <span style="font-size:14px;color:#606266;font-weight:500">统计周期：</span>
        <el-radio-group v-model="period" size="small" @change="loadData">
          <el-radio-button value="month">近1月</el-radio-button>
          <el-radio-button value="quarter">近3月</el-radio-button>
          <el-radio-button value="halfYear">近6月</el-radio-button>
          <el-radio-button value="year">近1年</el-radio-button>
          <el-radio-button value="all">全部</el-radio-button>
        </el-radio-group>
        <el-button :loading="loading" type="primary" size="small" plain @click="loadData">刷新</el-button>
      </div>
    </el-card>

    <div v-loading="loading">
      <!-- KPI 卡片 -->
      <el-row :gutter="16" style="margin-bottom:16px">
        <el-col :span="6">
          <div class="kpi-card" style="border-left:4px solid #f56c6c">
            <div class="kpi-val">{{ summary.totalCount || 0 }}</div>
            <div class="kpi-label">退货单数</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="kpi-card" style="border-left:4px solid #e6a23c">
            <div class="kpi-val">¥{{ fmtMoney(summary.totalAmount) }}</div>
            <div class="kpi-label">退货金额</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="kpi-card" style="border-left:4px solid #409eff">
            <div class="kpi-val">{{ summary.customerCount || 0 }}</div>
            <div class="kpi-label">涉及客户</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="kpi-card" style="border-left:4px solid #67c23a">
            <div class="kpi-val">{{ summary.productCount || 0 }}</div>
            <div class="kpi-label">涉及产品</div>
          </div>
        </el-col>
      </el-row>

      <!-- 月度趋势 -->
      <el-card shadow="never" style="margin-bottom:16px">
        <template #header><span style="font-weight:600">月度退货趋势（近12个月）</span></template>
        <div ref="trendRef" style="height:240px"></div>
      </el-card>

      <!-- 客户 + 产品维度图表 -->
      <el-row :gutter="16" style="margin-bottom:16px">
        <el-col :span="12">
          <el-card shadow="never">
            <template #header>
              <div style="display:flex;align-items:center;justify-content:space-between">
                <span style="font-weight:600">按客户分析（TOP 10）</span>
                <el-radio-group v-model="customerDim" size="small" @change="renderCustomerChart">
                  <el-radio-button value="count">退货次数</el-radio-button>
                  <el-radio-button value="amount">退货金额</el-radio-button>
                </el-radio-group>
              </div>
            </template>
            <div ref="customerRef" style="height:300px"></div>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card shadow="never">
            <template #header>
              <div style="display:flex;align-items:center;justify-content:space-between">
                <span style="font-weight:600">按产品分析（TOP 10）</span>
                <el-radio-group v-model="productDim" size="small" @change="renderProductChart">
                  <el-radio-button value="qty">退货数量</el-radio-button>
                  <el-radio-button value="amount">退货金额</el-radio-button>
                </el-radio-group>
              </div>
            </template>
            <div ref="productRef" style="height:300px"></div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 明细表格 -->
      <el-row :gutter="16">
        <el-col :span="12">
          <el-card shadow="never">
            <template #header>
              <div style="display:flex;align-items:center;justify-content:space-between">
                <span style="font-weight:600">客户退货明细</span>
                <el-button size="small" plain @click="exportTable(byCustomer, ['客户名称','退货次数','退货数量','退货金额','最近退货日期'], ['name','returnCount','returnQty','returnAmount','lastReturnDate'], '客户退货明细')">导出</el-button>
              </div>
            </template>
            <el-table :data="byCustomer" border stripe size="small" max-height="300">
              <el-table-column type="index" label="#" width="45" align="center" />
              <el-table-column prop="name" label="客户名称" min-width="110" show-overflow-tooltip />
              <el-table-column prop="returnCount" label="退货次数" width="75" align="center" />
              <el-table-column prop="returnQty" label="退货数量" width="75" align="center" />
              <el-table-column label="退货金额" width="100" align="right">
                <template #default="{ row }">¥{{ fmtMoney(row.returnAmount) }}</template>
              </el-table-column>
              <el-table-column prop="lastReturnDate" label="最近退货" width="95" align="center" />
            </el-table>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card shadow="never">
            <template #header>
              <div style="display:flex;align-items:center;justify-content:space-between">
                <span style="font-weight:600">产品退货明细</span>
                <el-button size="small" plain @click="exportTable(byProduct, ['产品名称','退货次数','退货数量','退货金额','最近退货日期'], ['name','returnCount','returnQty','returnAmount','lastReturnDate'], '产品退货明细')">导出</el-button>
              </div>
            </template>
            <el-table :data="byProduct" border stripe size="small" max-height="300">
              <el-table-column type="index" label="#" width="45" align="center" />
              <el-table-column prop="name" label="产品名称" min-width="110" show-overflow-tooltip />
              <el-table-column prop="returnCount" label="退货次数" width="75" align="center" />
              <el-table-column prop="returnQty" label="退货数量" width="75" align="center" />
              <el-table-column label="退货金额" width="100" align="right">
                <template #default="{ row }">¥{{ fmtMoney(row.returnAmount) }}</template>
              </el-table-column>
              <el-table-column prop="lastReturnDate" label="最近退货" width="95" align="center" />
            </el-table>
          </el-card>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { getReturnAnalysis } from '@/api/analysis'

const period = ref('halfYear')
const loading = ref(false)
const summary = ref({})
const byCustomer = ref([])
const byProduct = ref([])
const customerDim = ref('count')
const productDim = ref('qty')

const trendRef = ref(null)
const customerRef = ref(null)
const productRef = ref(null)

let trendChart = null
let customerChart = null
let productChart = null

async function loadData() {
  loading.value = true
  try {
    const res = await getReturnAnalysis(period.value)
    const d = res.data || {}
    summary.value = d.summary || {}
    byCustomer.value = d.byCustomer || []
    byProduct.value = d.byProduct || []
    await nextTick()
    renderTrendChart(d.monthlyTrend || [])
    renderCustomerChart()
    renderProductChart()
  } catch {} finally { loading.value = false }
}

function renderTrendChart(data) {
  if (!trendRef.value) return
  if (!trendChart) trendChart = echarts.init(trendRef.value)
  const months = data.map(d => d.month)
  const counts = data.map(d => d.returnCount)
  const amounts = data.map(d => Number(d.returnAmount || 0))
  trendChart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'cross' } },
    legend: { data: ['退货单数', '退货金额'], top: 0 },
    grid: { left: 50, right: 70, top: 32, bottom: 30 },
    xAxis: { type: 'category', data: months, axisLabel: { fontSize: 11 } },
    yAxis: [
      { type: 'value', name: '单数', minInterval: 1 },
      { type: 'value', name: '金额(元)', axisLabel: { formatter: v => v >= 10000 ? (v / 10000).toFixed(1) + 'w' : v } }
    ],
    series: [
      { name: '退货单数', type: 'bar', data: counts, barMaxWidth: 40, itemStyle: { color: '#f56c6c' } },
      { name: '退货金额', type: 'line', yAxisIndex: 1, data: amounts, smooth: true, itemStyle: { color: '#e6a23c' }, lineStyle: { width: 2 } }
    ]
  })
}

function renderCustomerChart() {
  if (!customerRef.value) return
  if (!customerChart) customerChart = echarts.init(customerRef.value)
  const data = [...byCustomer.value].reverse()
  const names = data.map(d => d.name)
  const values = customerDim.value === 'count' ? data.map(d => d.returnCount) : data.map(d => Number(d.returnAmount || 0))
  const label = customerDim.value === 'count' ? '退货次数' : '退货金额'
  customerChart.setOption({
    tooltip: { trigger: 'axis', formatter: params => `${params[0].name}<br/>${label}: ${params[0].value}` },
    grid: { left: 110, right: 20, top: 10, bottom: 30 },
    xAxis: { type: 'value' },
    yAxis: { type: 'category', data: names, axisLabel: { fontSize: 11, width: 100, overflow: 'truncate' } },
    series: [{ name: label, type: 'bar', data: values, barMaxWidth: 24, itemStyle: { color: '#409eff' } }]
  })
}

function renderProductChart() {
  if (!productRef.value) return
  if (!productChart) productChart = echarts.init(productRef.value)
  const data = [...byProduct.value].reverse()
  const names = data.map(d => d.name)
  const values = productDim.value === 'qty' ? data.map(d => d.returnQty) : data.map(d => Number(d.returnAmount || 0))
  const label = productDim.value === 'qty' ? '退货数量' : '退货金额'
  productChart.setOption({
    tooltip: { trigger: 'axis', formatter: params => `${params[0].name}<br/>${label}: ${params[0].value}` },
    grid: { left: 110, right: 20, top: 10, bottom: 30 },
    xAxis: { type: 'value' },
    yAxis: { type: 'category', data: names, axisLabel: { fontSize: 11, width: 100, overflow: 'truncate' } },
    series: [{ name: label, type: 'bar', data: values, barMaxWidth: 24, itemStyle: { color: '#67c23a' } }]
  })
}

function fmtMoney(val) {
  const n = Number(val || 0)
  return n >= 10000 ? (n / 10000).toFixed(2) + '万' : n.toFixed(2)
}

function exportTable(data, headers, keys, filename) {
  const rows = [headers, ...data.map(row => keys.map(k => row[k] ?? ''))]
  const csv = rows.map(r => r.map(v => `"${String(v).replace(/"/g, '""')}"`).join(',')).join('\n')
  const blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `${filename}.csv`
  a.click()
  URL.revokeObjectURL(a.href)
}

onMounted(loadData)
</script>

<style scoped>
.page-container { padding: 16px; }
.filter-card { margin-bottom: 16px; }
.kpi-card {
  background: #fff; border: 1px solid #ebeef5; border-radius: 6px;
  padding: 20px; text-align: center;
}
.kpi-val { font-size: 24px; font-weight: 700; color: #303133; }
.kpi-label { font-size: 13px; color: #909399; margin-top: 6px; }
</style>
