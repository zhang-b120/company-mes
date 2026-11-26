<template>
  <div class="page-container">
    <!-- search bar -->
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="分析周期">
          <el-date-picker
            v-model="queryParams.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 280px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询分析</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="success" plain @click="handleExport">导出</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- summary cards -->
    <el-row :gutter="16" class="summary-row">
      <el-col :span="6">
        <el-card shadow="hover" class="summary-card">
          <div class="summary-icon" style="background: #409eff"><el-icon><Coin /></el-icon></div>
          <div class="summary-info">
            <div class="summary-value">¥{{ formatMoney(summaryData.totalRevenue) }}</div>
            <div class="summary-label">总收入</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="summary-card">
          <div class="summary-icon" style="background: #e6a23c"><el-icon><Wallet /></el-icon></div>
          <div class="summary-info">
            <div class="summary-value">¥{{ formatMoney(summaryData.totalCost) }}</div>
            <div class="summary-label">总成本</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="summary-card">
          <div class="summary-icon" style="background: #67c23a"><el-icon><TrendCharts /></el-icon></div>
          <div class="summary-info">
            <div class="summary-value">¥{{ formatMoney(summaryData.grossProfit) }}</div>
            <div class="summary-label">毛利润</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="summary-card">
          <div class="summary-icon" style="background: #f56c6c"><el-icon><DataAnalysis /></el-icon></div>
          <div class="summary-info">
            <div class="summary-value">{{ summaryData.profitMargin || '0.00' }}%</div>
            <div class="summary-label">利润率</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- charts -->
    <el-row :gutter="16">
      <el-col :span="14">
        <el-card shadow="hover">
          <template #header>月度利润趋势</template>
          <div ref="barChartRef" style="height: 380px" v-loading="loading"></div>
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="hover">
          <template #header>成本构成分析</template>
          <div ref="pieChartRef" style="height: 380px" v-loading="loading"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'
import { exportOrderProfits } from '@/api/finance'

// --- state ---
const loading = ref(false)
const barChartRef = ref(null)
const pieChartRef = ref(null)
let barChart = null
let pieChart = null

const queryParams = reactive({
  dateRange: null
})

const summaryData = ref({
  totalRevenue: 0,
  totalCost: 0,
  grossProfit: 0,
  profitMargin: '0.00'
})

// --- helpers ---
function formatMoney(val) {
  if (val == null) return '0.00'
  return Number(val).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

// --- methods ---
async function loadData() {
  loading.value = true
  try {
    const params = {
      startDate: queryParams.dateRange?.[0] || undefined,
      endDate: queryParams.dateRange?.[1] || undefined
    }
    const res = await request.get('/finance/profit-analysis', { params })
    const data = res.data || {}

    // summary
    summaryData.value = {
      totalRevenue: data.totalRevenue || 0,
      totalCost: data.totalCost || 0,
      grossProfit: data.grossProfit || 0,
      profitMargin: data.profitMargin || '0.00'
    }

    await nextTick()
    renderBarChart(data.monthlyTrend || [])
    renderPieChart(data.costBreakdown || {})
  } catch {
    // handled by interceptor
  } finally {
    loading.value = false
  }
}

function renderBarChart(trendData) {
  if (!barChart && barChartRef.value) {
    barChart = echarts.init(barChartRef.value)
  }
  if (barChart) {
    barChart.setOption({
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' }
      },
      legend: { data: ['收入', '成本', '利润'] },
      xAxis: {
        type: 'category',
        data: trendData.map(d => d.month)
      },
      yAxis: { type: 'value' },
      series: [
        {
          name: '收入',
          type: 'bar',
          data: trendData.map(d => d.revenue),
          itemStyle: { color: '#409eff' }
        },
        {
          name: '成本',
          type: 'bar',
          data: trendData.map(d => d.cost),
          itemStyle: { color: '#e6a23c' }
        },
        {
          name: '利润',
          type: 'bar',
          data: trendData.map(d => d.profit),
          itemStyle: { color: '#67c23a' }
        }
      ],
      grid: { left: 60, right: 20, bottom: 30, top: 40 }
    })
  }
}

function renderPieChart(costData) {
  if (!pieChart && pieChartRef.value) {
    pieChart = echarts.init(pieChartRef.value)
  }
  const pieItems = [
    { name: '材料成本', value: costData.materialCost || 0 },
    { name: '人工成本', value: costData.laborCost || 0 },
    { name: '外协成本', value: costData.outsourceCost || 0 },
    { name: '制造费用', value: costData.overheadCost || 0 }
  ]
  if (pieChart) {
    pieChart.setOption({
      tooltip: { trigger: 'item', formatter: '{b}: ¥{c} ({d}%)' },
      legend: {
        orient: 'vertical',
        left: 'left',
        top: 'middle'
      },
      series: [{
        type: 'pie',
        radius: ['40%', '65%'],
        center: ['60%', '50%'],
        data: pieItems,
        label: { formatter: '{b}\n{d}%' },
        emphasis: {
          itemStyle: {
            shadowBlur: 10,
            shadowOffsetX: 0,
            shadowColor: 'rgba(0, 0, 0, 0.5)'
          }
        }
      }]
    })
  }
}

function handleSearch() {
  loadData()
}

function handleReset() {
  queryParams.dateRange = null
  loadData()
}

async function handleExport() {
  try {
    const res = await exportOrderProfits()
    const url = window.URL.createObjectURL(new Blob([res.data || res]))
    const a = document.createElement('a'); a.href = url; a.download = `订单利润_${new Date().toISOString().slice(0,10)}.xlsx`; a.click()
    window.URL.revokeObjectURL(url)
  } catch { ElMessage.error('导出失败') }
}

function handleResize() {
  barChart?.resize()
  pieChart?.resize()
}

// --- lifecycle ---
onMounted(() => {
  loadData()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  barChart?.dispose()
  pieChart?.dispose()
  barChart = null
  pieChart = null
})
</script>

<style scoped lang="scss">
.page-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.search-card {
  :deep(.el-card__body) {
    padding-bottom: 2px;
  }
}
.summary-row {
  // margin included via gap
}
.summary-card {
  :deep(.el-card__body) {
    display: flex;
    align-items: center;
    gap: 16px;
    padding: 20px;
  }
}
.summary-icon {
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
.summary-value {
  font-size: 24px;
  font-weight: bold;
  color: #333;
}
.summary-label {
  font-size: 13px;
  color: #999;
  margin-top: 4px;
}
</style>
