<template>
  <div class="page-container">
    <!-- 总览卡片 -->
    <el-row :gutter="16" class="stat-row">
      <el-col :span="3" v-for="s in overviewCards" :key="s.key">
        <div class="stat-card">
          <div class="stat-card__value" :style="{ color: s.color }">{{ overview[s.key] ?? '-' }}{{ s.suffix || '' }}</div>
          <div class="stat-card__label">{{ s.label }}</div>
        </div>
      </el-col>
    </el-row>

    <!-- 筛选条件 -->
    <el-card shadow="never" style="margin-bottom: 0">
      <el-form inline>
        <el-form-item label="时间范围">
          <el-select v-model="days" style="width: 120px" @change="loadAll">
            <el-option :value="7" label="近7天" />
            <el-option :value="30" label="近30天" />
            <el-option :value="90" label="近90天" />
            <el-option :value="180" label="近半年" />
          </el-select>
        </el-form-item>
        <el-form-item label="检验类型">
          <el-select v-model="qcType" placeholder="全部" clearable style="width: 130px" @change="loadPassRateTrend">
            <el-option label="来料检验" value="incoming" />
            <el-option label="过程检验" value="process" />
            <el-option label="成品检验" value="final" />
            <el-option label="外协检验" value="outsource" />
          </el-select>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 图表区域 -->
    <el-row :gutter="16">
      <!-- 合格率趋势 -->
      <el-col :span="16">
        <el-card shadow="never">
          <template #header>合格率趋势</template>
          <div ref="trendChartRef" style="height: 320px" />
        </el-card>
      </el-col>
      <!-- 缺陷类型分布 -->
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>缺陷类型分布</template>
          <div ref="defectTypeChartRef" style="height: 320px" />
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <!-- 缺陷等级分布 -->
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>缺陷等级分布</template>
          <div ref="defectLevelChartRef" style="height: 300px" />
        </el-card>
      </el-col>
      <!-- 产品质量排名 -->
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>产品质量排名 (TOP 10)</template>
          <el-table :data="productRanking" border size="small" max-height="300">
            <el-table-column type="index" label="#" width="40" align="center" />
            <el-table-column prop="productName" label="产品" min-width="120" show-overflow-tooltip />
            <el-table-column prop="totalTasks" label="检验数" width="70" align="right" />
            <el-table-column prop="passRate" label="合格率" width="80" align="center">
              <template #default="{ row }">
                <span :style="{ color: row.passRate >= 90 ? '#67c23a' : '#f56c6c', fontWeight: '600' }">
                  {{ row.passRate }}%
                </span>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <!-- 检验员绩效 -->
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>检验员绩效</template>
          <el-table :data="inspectorData" border size="small" max-height="300">
            <el-table-column prop="inspectorName" label="检验员" min-width="80" />
            <el-table-column prop="totalTasks" label="检验数" width="70" align="right" />
            <el-table-column prop="passCount" label="合格" width="60" align="right">
              <template #default="{ row }"><span style="color: #67c23a">{{ row.passCount }}</span></template>
            </el-table-column>
            <el-table-column prop="failCount" label="不合格" width="60" align="right">
              <template #default="{ row }"><span style="color: #f56c6c">{{ row.failCount }}</span></template>
            </el-table-column>
            <el-table-column prop="passRate" label="合格率" width="80" align="center">
              <template #default="{ row }">
                <el-progress :percentage="Number(row.passRate)" :stroke-width="6" :show-text="false"
                  :color="row.passRate >= 90 ? '#67c23a' : '#f56c6c'" style="width: 50px; display: inline-block; vertical-align: middle" />
                <span style="margin-left: 4px; font-size: 12px">{{ row.passRate }}%</span>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick, onBeforeUnmount, markRaw } from 'vue'
import { getQcAnalysisOverview, getPassRateTrend, getDefectDistribution, getProductQuality, getInspectorStats } from '@/api/quality'

const overviewCards = [
  { key: 'totalTasks', label: '总检验数', color: '#409eff' },
  { key: 'passCount', label: '合格', color: '#67c23a' },
  { key: 'failCount', label: '不合格', color: '#f56c6c' },
  { key: 'pendingCount', label: '待检验', color: '#e6a23c' },
  { key: 'passRate', label: '合格率', color: '#409eff', suffix: '%' },
  { key: 'defectTotal', label: '缺陷记录', color: '#909399' },
  { key: 'defectPending', label: '待处理缺陷', color: '#f56c6c' },
  { key: 'todayTasks', label: '今日检验', color: '#409eff' }
]

const days = ref(30)
const qcType = ref('')
const overview = reactive({})
const productRanking = ref([])
const inspectorData = ref([])

// echarts refs
const trendChartRef = ref(null)
const defectTypeChartRef = ref(null)
const defectLevelChartRef = ref(null)
let trendChart = null
let defectTypeChart = null
let defectLevelChart = null

const levelMap = { MINOR: '轻微', MAJOR: '严重', CRITICAL: '致命' }

async function loadOverview() {
  try {
    const res = await getQcAnalysisOverview()
    Object.assign(overview, res.data || {})
  } catch {}
}

async function loadPassRateTrend() {
  try {
    const res = await getPassRateTrend({ days: days.value, qcType: qcType.value || undefined })
    const d = res.data || {}
    renderTrendChart(d.dates || [], d.passRates || [], d.taskCounts || [])
  } catch {}
}

async function loadDefectDistribution() {
  try {
    const res = await getDefectDistribution({ days: days.value })
    const d = res.data || {}
    renderDefectTypeChart(d.byType || {})
    renderDefectLevelChart(d.byLevel || {})
  } catch {}
}

async function loadProductQuality() {
  try {
    const res = await getProductQuality({ days: days.value, top: 10 })
    productRanking.value = res.data?.ranking || []
  } catch {}
}

async function loadInspectorStats() {
  try {
    const res = await getInspectorStats({ days: days.value })
    inspectorData.value = res.data?.stats || []
  } catch {}
}

function loadAll() {
  loadOverview()
  loadPassRateTrend()
  loadDefectDistribution()
  loadProductQuality()
  loadInspectorStats()
}

// ECharts rendering (lazy import)
let echarts = null

async function ensureEcharts() {
  if (!echarts) {
    try {
      echarts = await import('echarts')
    } catch {
      // echarts not installed, use placeholder
      return false
    }
  }
  return true
}

async function renderTrendChart(dates, rates, counts) {
  if (!(await ensureEcharts()) || !trendChartRef.value) return
  if (!trendChart) trendChart = markRaw(echarts.init(trendChartRef.value))
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['合格率', '检验数量'] },
    grid: { left: 50, right: 50, top: 40, bottom: 30 },
    xAxis: { type: 'category', data: dates },
    yAxis: [
      { type: 'value', name: '合格率(%)', min: 0, max: 100 },
      { type: 'value', name: '数量', min: 0 }
    ],
    series: [
      { name: '合格率', type: 'line', smooth: true, data: rates, itemStyle: { color: '#67c23a' }, areaStyle: { color: 'rgba(103,194,58,0.1)' } },
      { name: '检验数量', type: 'bar', yAxisIndex: 1, data: counts, itemStyle: { color: 'rgba(64,158,255,0.6)' }, barMaxWidth: 20 }
    ]
  }, true)
}

async function renderDefectTypeChart(byType) {
  if (!(await ensureEcharts()) || !defectTypeChartRef.value) return
  if (!defectTypeChart) defectTypeChart = markRaw(echarts.init(defectTypeChartRef.value))
  const data = Object.entries(byType).map(([name, value]) => ({ name, value }))
  defectTypeChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { orient: 'vertical', right: 10, top: 'center' },
    series: [{
      type: 'pie', radius: ['40%', '65%'], center: ['35%', '50%'],
      label: { show: false },
      data,
      emphasis: { label: { show: true, fontSize: 14, fontWeight: 'bold' } }
    }]
  }, true)
}

async function renderDefectLevelChart(byLevel) {
  if (!(await ensureEcharts()) || !defectLevelChartRef.value) return
  if (!defectLevelChart) defectLevelChart = markRaw(echarts.init(defectLevelChartRef.value))
  const names = Object.keys(byLevel).map(k => levelMap[k] || k)
  const values = Object.values(byLevel)
  const colors = Object.keys(byLevel).map(k => k === 'CRITICAL' ? '#f56c6c' : k === 'MAJOR' ? '#e6a23c' : '#409eff')
  defectLevelChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 60, right: 20, top: 20, bottom: 30 },
    xAxis: { type: 'category', data: names },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{
      type: 'bar', data: values.map((v, i) => ({ value: v, itemStyle: { color: colors[i] } })),
      barMaxWidth: 40
    }]
  }, true)
}

function handleResize() {
  trendChart?.resize()
  defectTypeChart?.resize()
  defectLevelChart?.resize()
}

onMounted(async () => {
  await nextTick()
  loadAll()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose()
  defectTypeChart?.dispose()
  defectLevelChart?.dispose()
})
</script>

<style scoped lang="scss">
.page-container { display: flex; flex-direction: column; gap: 16px; }
.stat-row { flex-shrink: 0; }
.stat-card {
  background: #fff; border-radius: 8px; padding: 12px 8px; text-align: center;
  box-shadow: 0 1px 4px rgba(0,0,0,.06);
  &__value { font-size: 22px; font-weight: 700; }
  &__label { font-size: 12px; color: #909399; margin-top: 2px; }
}
</style>
