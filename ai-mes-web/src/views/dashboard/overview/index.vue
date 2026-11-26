<template>
  <div class="page-container">
    <!-- AI每日简报按钮 -->
    <div class="ai-briefing-bar">
      <el-button type="primary" style="background: #7c3aed; border-color: #7c3aed" @click="openAiBriefing">
        <el-icon style="margin-right: 4px"><MagicStick /></el-icon>AI每日简报
      </el-button>
    </div>

    <!-- summary cards -->
    <el-row :gutter="16" class="summary-row">
      <el-col :span="6">
        <el-card shadow="hover" class="summary-card">
          <div class="summary-icon" style="background: #409eff"><el-icon><Document /></el-icon></div>
          <div class="summary-info">
            <div class="summary-value">{{ summary.totalOrders || 0 }}</div>
            <div class="summary-label">总订单数</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="summary-card">
          <div class="summary-icon" style="background: #67c23a"><el-icon><SetUp /></el-icon></div>
          <div class="summary-info">
            <div class="summary-value">{{ summary.productionWip || 0 }}</div>
            <div class="summary-label">生产在制</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="summary-card">
          <div class="summary-icon" style="background: #e6a23c"><el-icon><WarningFilled /></el-icon></div>
          <div class="summary-info">
            <div class="summary-value">{{ summary.inventoryAlerts || 0 }}</div>
            <div class="summary-label">库存预警</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="summary-card">
          <div class="summary-icon" style="background: #f56c6c"><el-icon><Coin /></el-icon></div>
          <div class="summary-info">
            <div class="summary-value">¥{{ formatMoney(summary.revenueThisMonth) }}</div>
            <div class="summary-label">本月收入</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- charts row 1: production trend -->
    <el-row :gutter="16">
      <el-col :span="16">
        <el-card shadow="hover">
          <template #header>近30日产量趋势</template>
          <div ref="trendChartRef" style="height: 340px"></div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <template #header>订单状态分布</template>
          <div ref="orderPieRef" style="height: 340px"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- charts row 2: top products -->
    <el-row :gutter="16" style="margin-top: 16px;">
      <el-col :span="24">
        <el-card shadow="hover">
          <template #header>产量TOP5产品</template>
          <div ref="topBarRef" style="height: 320px"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- AI每日简报对话框 -->
    <el-dialog v-model="aiBriefingVisible" title="AI每日简报" width="700px" destroy-on-close>
      <div v-if="!aiBriefingContent" style="text-align: center; padding: 20px 0">
        <p style="color: #666; margin-bottom: 16px">将基于当前工厂数据自动生成今日简报</p>
        <el-button type="primary" :loading="aiBriefingLoading" style="background: #7c3aed; border-color: #7c3aed" @click="handleGenerateBriefing">
          <el-icon style="margin-right: 4px"><MagicStick /></el-icon>生成简报
        </el-button>
      </div>
      <template v-if="aiBriefingContent">
        <el-card shadow="never" class="briefing-card">
          <template #header>
            <div style="display: flex; align-items: center; justify-content: space-between">
              <span style="font-weight: bold; font-size: 16px">{{ aiBriefingContent.title || '今日工厂简报' }}</span>
              <span style="color: #999; font-size: 13px">{{ aiBriefingContent.date || new Date().toLocaleDateString() }}</span>
            </div>
          </template>
          <!-- 关键指标 -->
          <div v-if="aiBriefingContent.metrics" style="margin-bottom: 16px">
            <h4 style="margin-bottom: 8px; color: #333">关键指标</h4>
            <el-row :gutter="12">
              <el-col :span="8" v-for="(metric, idx) in aiBriefingContent.metrics" :key="idx">
                <div class="briefing-metric">
                  <div class="briefing-metric-value">{{ metric.value }}</div>
                  <div class="briefing-metric-label">{{ metric.label }}</div>
                </div>
              </el-col>
            </el-row>
          </div>
          <!-- 摘要 -->
          <div v-if="aiBriefingContent.summary" style="margin-bottom: 16px">
            <h4 style="margin-bottom: 8px; color: #333">总体概况</h4>
            <p style="color: #555; line-height: 1.8; white-space: pre-wrap">{{ aiBriefingContent.summary }}</p>
          </div>
          <!-- 风险提醒 -->
          <div v-if="aiBriefingContent.risks && aiBriefingContent.risks.length">
            <h4 style="margin-bottom: 8px; color: #f56c6c">风险提醒</h4>
            <ul style="padding-left: 20px; color: #555">
              <li v-for="(risk, idx) in aiBriefingContent.risks" :key="idx" style="margin-bottom: 6px; line-height: 1.6">{{ risk }}</li>
            </ul>
          </div>
          <!-- 建议 -->
          <div v-if="aiBriefingContent.suggestions && aiBriefingContent.suggestions.length" style="margin-top: 16px">
            <h4 style="margin-bottom: 8px; color: #67c23a">改进建议</h4>
            <ul style="padding-left: 20px; color: #555">
              <li v-for="(sug, idx) in aiBriefingContent.suggestions" :key="idx" style="margin-bottom: 6px; line-height: 1.6">{{ sug }}</li>
            </ul>
          </div>
        </el-card>
      </template>
      <template #footer>
        <el-button @click="aiBriefingVisible = false">关闭</el-button>
        <el-button v-if="aiBriefingContent" type="primary" style="background: #7c3aed; border-color: #7c3aed" @click="handleGenerateBriefing">重新生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { MagicStick } from '@element-plus/icons-vue'
import { getDashboardSummary, getProductionTrend, getOrderStatus } from '@/api/dashboard'
import { dailyBriefing } from '@/api/ai'

// --- state ---
const summary = ref({})
const trendChartRef = ref(null)
const orderPieRef = ref(null)
const topBarRef = ref(null)

let trendChart = null
let orderPieChart = null
let topBarChart = null

// --- helpers ---
function formatMoney(val) {
  if (val == null) return '0.00'
  return Number(val).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

// --- load summary ---
async function loadSummary() {
  try {
    const res = await getDashboardSummary()
    summary.value = res.data || {}
  } catch {
    // handled by interceptor
  }
}

// --- production trend (line chart) ---
async function loadTrend() {
  try {
    const res = await getProductionTrend()
    const data = res.data || []
    await nextTick()
    if (!trendChart && trendChartRef.value) {
      trendChart = echarts.init(trendChartRef.value)
    }
    if (trendChart) {
      trendChart.setOption({
        tooltip: { trigger: 'axis' },
        legend: { data: ['完成数量', '创建工单数'], top: 0 },
        xAxis: {
          type: 'category',
          data: data.map(d => d.date),
          boundaryGap: false
        },
        yAxis: { type: 'value' },
        series: [
          {
            name: '完成数量',
            type: 'line',
            data: data.map(d => d.completed ?? d.quantity ?? 0),
            smooth: true,
            areaStyle: {
              color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                { offset: 0, color: 'rgba(64, 158, 255, 0.3)' },
                { offset: 1, color: 'rgba(64, 158, 255, 0.05)' }
              ])
            },
            itemStyle: { color: '#409eff' }
          },
          {
            name: '创建工单数',
            type: 'line',
            data: data.map(d => d.created ?? 0),
            smooth: true,
            itemStyle: { color: '#67c23a' }
          }
        ],
        grid: { left: 50, right: 20, bottom: 30, top: 40 }
      })
    }
  } catch {
    // handled by interceptor
  }
}

// --- order status (pie chart) ---
async function loadOrderStatus() {
  try {
    const res = await getOrderStatus()
    const data = res.data || {}
    await nextTick()
    if (!orderPieChart && orderPieRef.value) {
      orderPieChart = echarts.init(orderPieRef.value)
    }
    const statusLabels = {
      draft: '草稿',
      pending_approval: '待审批',
      confirmed: '已确认',
      mrp_planned: '已转计划',
      outsourcing: '外协中',
      in_production: '生产中',
      partial_shipped: '部分发货',
      shipped: '已发货',
      completed: '已完成',
      settled: '已结算',
      cancelled: '已取消',
      voided: '已作废'
    }
    const pieData = Object.entries(data).map(([key, val]) => ({
      name: statusLabels[key] || key,
      value: val
    }))
    if (orderPieChart) {
      orderPieChart.setOption({
        tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
        series: [{
          type: 'pie',
          radius: ['40%', '65%'],
          data: pieData,
          label: { formatter: '{b}: {c}' }
        }]
      })
    }
  } catch {
    // handled by interceptor
  }
}

// --- top 5 products (bar chart) ---
async function loadTopProducts() {
  try {
    const res = await getDashboardSummary()
    const topProducts = res.data?.topProducts || []
    await nextTick()
    if (!topBarChart && topBarRef.value) {
      topBarChart = echarts.init(topBarRef.value)
    }
    if (topBarChart) {
      topBarChart.setOption({
        tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
        xAxis: {
          type: 'category',
          data: topProducts.map(d => d.productName),
          axisLabel: {
            rotate: 15,
            interval: 0
          }
        },
        yAxis: { type: 'value' },
        series: [{
          name: '产量',
          type: 'bar',
          data: topProducts.map(d => d.quantity),
          itemStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: '#67c23a' },
              { offset: 1, color: '#b3e19d' }
            ])
          },
          barMaxWidth: 60
        }],
        grid: { left: 60, right: 20, bottom: 40, top: 20 }
      })
    }
  } catch {
    // handled by interceptor
  }
}

function handleResize() {
  trendChart?.resize()
  orderPieChart?.resize()
  topBarChart?.resize()
}

// --- AI每日简报 ---
const aiBriefingVisible = ref(false)
const aiBriefingLoading = ref(false)
const aiBriefingContent = ref(null)

function openAiBriefing() {
  aiBriefingContent.value = null
  aiBriefingVisible.value = true
}

async function handleGenerateBriefing() {
  aiBriefingLoading.value = true
  try {
    const factoryData = {
      totalOrders: summary.value.totalOrders,
      productionWip: summary.value.productionWip,
      inventoryAlerts: summary.value.inventoryAlerts,
      revenueThisMonth: summary.value.revenueThisMonth,
      topProducts: summary.value.topProducts,
      date: new Date().toISOString().split('T')[0]
    }
    const res = await dailyBriefing(factoryData)
    aiBriefingContent.value = res.data
  } catch {
    aiBriefingContent.value = null
    ElMessage.error('简报生成失败，请重试')
  } finally {
    aiBriefingLoading.value = false
  }
}

// --- lifecycle ---
onMounted(() => {
  loadSummary()
  loadTrend()
  loadOrderStatus()
  loadTopProducts()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose()
  orderPieChart?.dispose()
  topBarChart?.dispose()
  trendChart = null
  orderPieChart = null
  topBarChart = null
})
</script>

<style scoped lang="scss">
.page-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
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
  font-size: 28px;
  font-weight: bold;
  color: #333;
}
.summary-label {
  font-size: 13px;
  color: #999;
  margin-top: 4px;
}
.ai-briefing-bar {
  display: flex;
  justify-content: flex-end;
}
.briefing-card {
  :deep(.el-card__body) {
    padding: 20px;
  }
}
.briefing-metric {
  text-align: center;
  padding: 12px 8px;
  background: #f5f7fa;
  border-radius: 8px;
  margin-bottom: 8px;
}
.briefing-metric-value {
  font-size: 22px;
  font-weight: bold;
  color: #333;
}
.briefing-metric-label {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}
</style>
