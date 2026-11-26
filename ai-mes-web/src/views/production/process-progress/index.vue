<template>
  <div class="process-progress-page">
    <!-- 顶部搜索区 -->
    <el-card class="search-card" shadow="never">
      <div class="search-bar">
        <el-input
          v-model="searchForm.stepName"
          placeholder="搜索工序名称"
          clearable
          style="width: 260px"
          @keyup.enter="loadData"
          @clear="loadData"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button type="primary" @click="loadData">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
        <el-button :icon="Refresh" circle @click="loadData" title="刷新" />
      </div>
      <div class="summary-info" v-if="!loading && groups.length > 0">
        共 <b>{{ groups.length }}</b> 个工序，
        <b>{{ totalTasks }}</b> 条工序任务
      </div>
    </el-card>

    <!-- 加载中 -->
    <div v-if="loading" class="loading-wrap">
      <el-skeleton :rows="6" animated />
    </div>

    <!-- 空状态 -->
    <el-empty
      v-else-if="!loading && groups.length === 0"
      description="暂无未完成的工单工序数据"
      :image-size="120"
      style="margin-top: 60px"
    />

    <!-- 工序分组卡片区 -->
    <div v-else class="groups-wrap">
      <div v-for="group in groups" :key="group.stepName" class="process-group">
        <!-- 工序标题栏 -->
        <div class="group-header">
          <span class="group-name">
            <el-icon><Setting /></el-icon>
            {{ group.stepName }}
          </span>
          <el-tag type="info" size="small" round>{{ group.total }} 条</el-tag>
        </div>

        <!-- 该工序下的卡片列表 -->
        <div class="cards-grid">
          <div
            v-for="item in group.items"
            :key="item.taskId"
            class="progress-card"
            :class="getCardClass(item)"
          >
            <!-- 卡片头 -->
            <div class="card-header">
              <div class="card-wo-no">{{ item.woNo }}</div>
              <div class="card-tags">
                <el-tag :type="getTaskStatusType(item.taskStatus)" size="small" effect="light">
                  {{ getTaskStatusLabel(item.taskStatus) }}
                </el-tag>
                <el-tag :type="getWoStatusType(item.woStatus)" size="small" effect="plain">
                  {{ getWoStatusLabel(item.woStatus) }}
                </el-tag>
              </div>
            </div>

            <!-- 卡片主体 -->
            <div class="card-body">
              <div class="card-row product-name">
                <el-icon><Box /></el-icon>
                <span class="label">产品名称</span>
                <span class="value" :title="item.productName">{{ item.productName || '-' }}</span>
              </div>
              <div class="card-row">
                <el-icon><Document /></el-icon>
                <span class="label">产品编码</span>
                <span class="value">{{ item.productNo || '-' }}</span>
              </div>
              <div class="card-row">
                <el-icon><InfoFilled /></el-icon>
                <span class="label">产品规格</span>
                <span class="value spec-val" :title="$fmtSpec(item.productSpec)">{{ $fmtSpec(item.productSpec) }}</span>
              </div>
              <div class="card-row">
                <el-icon><Tickets /></el-icon>
                <span class="label">销售订单</span>
                <span class="value order-no">{{ item.orderNo || '无' }}</span>
              </div>
              <div class="card-row">
                <el-icon><Aim /></el-icon>
                <span class="label">计划数量</span>
                <span class="value qty">{{ item.planQty }}</span>
              </div>
              <div class="card-row">
                <el-icon><SuccessFilled /></el-icon>
                <span class="label">报工数量</span>
                <span class="value qty" :class="{ 'qty-done': item.qualifiedQty > 0 }">
                  {{ item.qualifiedQty }}
                </span>
              </div>
            </div>

            <!-- 进度条 -->
            <div class="card-footer">
              <div class="progress-label">
                <span>生产进度</span>
                <span class="progress-pct" :class="getProgressClass(item.progress)">{{ item.progress }}%</span>
              </div>
              <el-progress
                :percentage="item.progress"
                :stroke-width="8"
                :color="getProgressColor(item.progress)"
                :show-text="false"
              />
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Search, Refresh, Setting, Box, Document, InfoFilled, Tickets, Aim, SuccessFilled } from '@element-plus/icons-vue'
import { getProcessProgress } from '@/api/production'

const loading = ref(false)
const groups = ref([])
const searchForm = ref({ stepName: '' })

const totalTasks = computed(() => groups.value.reduce((sum, g) => sum + g.total, 0))

async function loadData() {
  loading.value = true
  try {
    const params = {}
    if (searchForm.value.stepName) params.stepName = searchForm.value.stepName
    const res = await getProcessProgress(params)
    groups.value = res.data || []
  } finally {
    loading.value = false
  }
}

function handleReset() {
  searchForm.value.stepName = ''
  loadData()
}

// 任务状态
function getTaskStatusLabel(status) {
  const map = { pending: '待开始', ready: '就绪', in_progress: '生产中', pending_qc: '待质检', completed: '已完成', cancelled: '已取消' }
  return map[status] || status
}
function getTaskStatusType(status) {
  const map = { pending: 'info', ready: '', in_progress: 'warning', pending_qc: 'danger', completed: 'success', cancelled: 'info' }
  return map[status] || 'info'
}

// 工单状态
function getWoStatusLabel(status) {
  const map = { pending: '待排产', scheduled: '已排产', in_progress: '生产中', completed: '生产完成', cancelled: '已取消' }
  return map[status] || status
}
function getWoStatusType(status) {
  const map = { pending: 'info', scheduled: '', in_progress: 'warning', completed: 'success', cancelled: 'info' }
  return map[status] || 'info'
}

// 进度相关
function getProgressColor(pct) {
  if (pct >= 100) return '#67c23a'
  if (pct >= 60) return '#409eff'
  if (pct >= 30) return '#e6a23c'
  return '#f56c6c'
}
function getProgressClass(pct) {
  if (pct >= 100) return 'pct-done'
  if (pct >= 60) return 'pct-good'
  if (pct >= 30) return 'pct-mid'
  return 'pct-low'
}
function getCardClass(item) {
  if (item.taskStatus === 'completed') return 'card-completed'
  if (item.taskStatus === 'in_progress') return 'card-inprogress'
  return ''
}

onMounted(() => loadData())
</script>

<style scoped>
.process-progress-page {
  padding: 16px;
  min-height: 100%;
  background: #f5f7fa;
}

.search-card {
  margin-bottom: 16px;
}
.search-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.summary-info {
  margin-top: 10px;
  font-size: 13px;
  color: #909399;
}

.loading-wrap {
  padding: 24px;
  background: #fff;
  border-radius: 8px;
}

.groups-wrap {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.process-group {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0,0,0,.08);
  overflow: hidden;
}

.group-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: linear-gradient(90deg, #f0f4ff 0%, #fff 100%);
  border-bottom: 1px solid #e4e7ed;
}
.group-name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 14px;
  padding: 14px 16px;
}

.progress-card {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 14px;
  background: #fafafa;
  transition: box-shadow .2s, transform .2s;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.progress-card:hover {
  box-shadow: 0 4px 16px rgba(64,158,255,.15);
  transform: translateY(-2px);
}
.progress-card.card-inprogress {
  border-color: #e6a23c;
  background: #fffdf5;
}
.progress-card.card-completed {
  border-color: #67c23a;
  background: #f7fff5;
  opacity: .85;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.card-wo-no {
  font-size: 13px;
  font-weight: 600;
  color: #409eff;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.card-tags {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}

.card-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.card-row {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  line-height: 1.4;
}
.card-row .el-icon {
  color: #909399;
  font-size: 14px;
  flex-shrink: 0;
}
.card-row .label {
  color: #909399;
  white-space: nowrap;
  flex-shrink: 0;
}
.card-row .value {
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}
.card-row.product-name .value {
  font-weight: 500;
}
.spec-val {
  color: #606266;
  font-size: 12px;
}
.order-no {
  color: #409eff;
}
.qty {
  font-weight: 600;
}
.qty-done {
  color: #409eff;
}

.card-footer {
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.progress-label {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: #909399;
}
.progress-pct {
  font-weight: 700;
  font-size: 13px;
}
.pct-done  { color: #67c23a; }
.pct-good  { color: #409eff; }
.pct-mid   { color: #e6a23c; }
.pct-low   { color: #f56c6c; }
</style>
