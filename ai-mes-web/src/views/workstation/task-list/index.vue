<template>
  <div class="ws-task-screen" :class="wsTheme">
    <!-- 顶部标题栏 -->
    <header class="ws-header">
      <div class="ws-header-left">
        <span class="ws-logo">AI-MES</span>
        <span class="ws-subtitle">工位报工</span>
      </div>
      <div class="ws-header-center">
        <div class="ws-user-info" v-if="employeeInfo.realName || employeeInfo.username">
          <span class="ws-user-icon">👤</span>
          <span class="ws-user-name">{{ employeeInfo.realName || employeeInfo.username }}</span>
          <span class="ws-user-account">{{ employeeInfo.username }}</span>
        </div>
        <div class="ws-task-count" v-if="!loading">
          共 <strong>{{ tasks.length }}</strong> 条待报工任务
        </div>
      </div>
      <div class="ws-header-right">
        <button class="ws-btn-refresh" @click="loadTasks" :disabled="loading">
          <span v-if="loading" class="ws-spinner"></span>
          <span v-else>🔄</span>
          刷新
        </button>
        <button class="ws-btn-exit" @click="handleExit">退出</button>
      </div>
    </header>

    <!-- 任务卡片区域 -->
    <main class="ws-main">
      <!-- 加载状态 -->
      <div v-if="loading" class="ws-loading">
        <div class="ws-loading-spinner"></div>
        <span>加载任务中...</span>
      </div>

      <!-- 空状态 -->
      <div v-else-if="tasks.length === 0" class="ws-empty">
        <div class="ws-empty-icon">✓</div>
        <div class="ws-empty-text">暂无待报工任务</div>
        <div class="ws-empty-sub">所有工序均已完成或尚未分配</div>
      </div>

      <!-- 任务卡片网格 -->
      <div v-else class="ws-card-grid">
        <div
          v-for="task in tasks"
          :key="task.id"
          class="ws-task-card"
          :class="{ 'is-inprogress': task.status === 'in_progress' }"
          @click="handleReport(task)"
        >
          <!-- 卡片顶部：工单号 + 状态 -->
          <div class="card-top">
            <span class="card-wo-no">{{ task.woNo }}</span>
            <span class="card-status" :class="task.status === 'in_progress' ? 'status-progress' : 'status-ready'">
              {{ task.status === 'in_progress' ? '生产中' : '待生产' }}
            </span>
          </div>

          <!-- 物料信息 -->
          <div class="card-material">
            <span class="card-material-no">{{ task.materialNo || '-' }}</span>
            <span class="card-material-name">{{ task.productName || '-' }}</span>
          </div>

          <!-- 工序名称 -->
          <div class="card-step">
            <span class="card-step-label">工序</span>
            <span class="card-step-name">{{ task.stepName }}</span>
          </div>

          <!-- 分配方式 -->
          <div class="card-assign">
            <span v-if="task.assignType === 'team'" class="assign-tag assign-team">
              👥 {{ task.assignedTeamName || '班组' }}
            </span>
            <span v-else class="assign-tag assign-person">👤 个人</span>
          </div>

          <!-- 进度条 -->
          <div class="card-progress">
            <div class="progress-bar">
              <div
                class="progress-fill"
                :style="{ width: getProgressWidth(task) + '%' }"
              ></div>
            </div>
            <div class="progress-text">
              {{ task.qcTemplateId ? (task.reportedQty ?? 0) : (task.qualifiedQty ?? 0) }}
              / {{ task.planQty }}
              <span class="progress-unit">件</span>
            </div>
          </div>

          <!-- 交期 -->
          <div class="card-footer">
            <span class="card-delivery" :class="getDeliveryClass(task.deliveryDate)">
              📅 {{ task.deliveryDate || '暂无交期' }}
            </span>
            <span class="card-action">去报工 →</span>
          </div>
        </div>
      </div>
    </main>

    <!-- 底部信息栏 -->
    <footer class="ws-footer">
      <span>每 30 秒自动刷新</span>
      <span class="ws-footer-sep">|</span>
      <span>上次刷新: {{ lastRefreshTime }}</span>
      <span class="ws-footer-sep">|</span>
      <span>AI-MES 智造云工单</span>
    </footer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { useWorkstationStore } from '@/store/workstation'
import { getWorkstationTasks } from '@/api/workstation'
import { resetRouter } from '@/router'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const wsStore = useWorkstationStore()
const wsTheme = computed(() => wsStore.theme)

const loading = ref(false)
const tasks = ref([])
const lastRefreshTime = ref('-')
const employeeInfo = reactive({ userId: null, username: '', realName: '' })

let refreshTimer = null

async function loadTasks() {
  const username = route.query.username || wsStore.userInfo?.username
  if (!username) return
  loading.value = true
  try {
    const res = await getWorkstationTasks(username)
    const data = res.data
    employeeInfo.userId = data.userId
    employeeInfo.username = data.username
    employeeInfo.realName = data.realName
    // 同步到 workstation store
    wsStore.setUserInfo({ userId: data.userId, username: data.username, realName: data.realName })
    tasks.value = data.tasks || []
    const now = new Date()
    const pad = n => String(n).padStart(2, '0')
    lastRefreshTime.value = `${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
  } catch (e) {
    tasks.value = []
  } finally {
    loading.value = false
  }
}

function handleReport(task) {
  router.push({
    path: '/workstation/report',
    query: {
      taskId: task.id,
      workOrderId: task.workOrderId,
      woNo: task.woNo,
      stepName: task.stepName,
      assignType: task.assignType,
      assignedTeamId: task.assignedTeamId || '',
      assignedTeamName: task.assignedTeamName || '',
      planQty: task.planQty,
      piecePrice: task.piecePrice || '',
      wageRatio: task.wageRatio != null ? task.wageRatio : 1,
      materialNo: task.materialNo || '',
      productName: task.productName || '',
      username: employeeInfo.username,
      userId: employeeInfo.userId
    }
  })
}

function handleExit() {
  userStore.logout()
  wsStore.clear()
  resetRouter()
  router.replace('/reportLogin')
}

function getProgressWidth(task) {
  const done = task.qcTemplateId ? (task.reportedQty ?? 0) : (task.qualifiedQty ?? 0)
  const plan = task.planQty || 1
  return Math.min(100, Math.round((done / plan) * 100))
}

function getDeliveryClass(dateStr) {
  if (!dateStr) return ''
  const days = Math.ceil((new Date(dateStr).getTime() - Date.now()) / 86400000)
  if (days < 0) return 'delivery-overdue'
  if (days <= 1) return 'delivery-urgent'
  if (days <= 3) return 'delivery-soon'
  return ''
}

// 监听 query 变化（再次刷卡时重新加载）
watch(() => [route.query.username, route.query.t], () => {
  if (route.path === '/workstation/task-list') loadTasks()
})

onMounted(() => {
  // 初始化员工信息（从 store 或 query）
  if (wsStore.userInfo) {
    employeeInfo.userId = wsStore.userInfo.userId
    employeeInfo.username = wsStore.userInfo.username
    employeeInfo.realName = wsStore.userInfo.realName
  }
  loadTasks()
  refreshTimer = setInterval(loadTasks, 30000)
})

onBeforeUnmount(() => {
  if (refreshTimer) clearInterval(refreshTimer)
})
</script>

<style scoped lang="scss">
.ws-task-screen {
  width: 100vw;
  height: 100vh;
  background: #0a0f1e;
  color: #e8eaf0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

// ===== 顶部 =====
.ws-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 32px;
  background: linear-gradient(135deg, #0d1829 0%, #112040 100%);
  border-bottom: 1px solid #1e3a5f;
  flex-shrink: 0;
}
.ws-header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 160px;
}
.ws-logo {
  font-size: 20px;
  font-weight: 800;
  color: #40a0ff;
  letter-spacing: 2px;
}
.ws-subtitle {
  font-size: 14px;
  color: #6b8cb0;
}
.ws-header-center {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}
.ws-user-info {
  display: flex;
  align-items: center;
  gap: 8px;
}
.ws-user-icon { font-size: 18px; }
.ws-user-name {
  font-size: 20px;
  font-weight: 700;
  color: #fff;
}
.ws-user-account {
  font-size: 13px;
  color: #6b8cb0;
  background: #0d2040;
  padding: 2px 8px;
  border-radius: 10px;
}
.ws-task-count {
  font-size: 13px;
  color: #6b8cb0;
  strong { color: #40a0ff; font-size: 16px; }
}
.ws-header-right {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 160px;
  justify-content: flex-end;
}
.ws-btn-refresh {
  display: flex;
  align-items: center;
  gap: 4px;
  background: #0d2040;
  border: 1px solid #1e3a5f;
  border-radius: 6px;
  color: #6b8cb0;
  font-size: 13px;
  padding: 6px 14px;
  cursor: pointer;
  transition: color 0.2s;
  &:hover:not(:disabled) { color: #40a0ff; border-color: #40a0ff; }
  &:disabled { opacity: 0.5; cursor: default; }
}
.ws-btn-exit {
  background: #4a0f0f;
  border: 1px solid #7a1f1f;
  border-radius: 6px;
  color: #ff8080;
  font-size: 14px;
  font-weight: 600;
  padding: 7px 18px;
  cursor: pointer;
  transition: background 0.2s;
  &:hover { background: #5a1515; }
}
.ws-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid #2a4a6a;
  border-top-color: #40a0ff;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

// ===== 主内容 =====
.ws-main {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
  scrollbar-width: thin;
  scrollbar-color: #1e3a5f transparent;
  &::-webkit-scrollbar { width: 6px; }
  &::-webkit-scrollbar-thumb { background: #1e3a5f; border-radius: 3px; }
}

// 加载
.ws-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 300px;
  gap: 16px;
  color: #3a5070;
  font-size: 15px;
}
.ws-loading-spinner {
  width: 40px;
  height: 40px;
  border: 3px solid #1e3a5f;
  border-top-color: #40a0ff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

// 空状态
.ws-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 300px;
  gap: 12px;
}
.ws-empty-icon { font-size: 48px; color: #1e6b3f; }
.ws-empty-text { font-size: 18px; color: #4a6a8a; font-weight: 600; }
.ws-empty-sub { font-size: 13px; color: #2a4a6a; }

// 卡片网格
.ws-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

// 任务卡片
.ws-task-card {
  background: #0d1829;
  border: 1px solid #1e3a5f;
  border-radius: 10px;
  padding: 18px 20px;
  cursor: pointer;
  transition: border-color 0.2s, transform 0.15s, box-shadow 0.2s;
  display: flex;
  flex-direction: column;
  gap: 10px;

  &:hover {
    border-color: #40a0ff;
    transform: translateY(-2px);
    box-shadow: 0 6px 24px rgba(64, 160, 255, 0.15);
  }

  &.is-inprogress {
    border-color: #2a5020;
    background: linear-gradient(135deg, #0d1829, #0d2010);
  }
}

.card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.card-wo-no {
  font-size: 13px;
  color: #5a8ab0;
  font-family: monospace;
}
.card-status {
  font-size: 12px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 10px;
}
.status-progress { background: #1a3a0a; color: #60d060; }
.status-ready { background: #1a2a3a; color: #6090c0; }

.card-material {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.card-material-no {
  font-size: 12px;
  color: #5a8ab0;
  font-family: monospace;
}
.card-material-name {
  font-size: 17px;
  font-weight: 700;
  color: #c8d8f0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.card-step {
  display: flex;
  align-items: center;
  gap: 8px;
}
.card-step-label {
  font-size: 12px;
  color: #3a5070;
  background: #0a1525;
  padding: 2px 6px;
  border-radius: 4px;
}
.card-step-name {
  font-size: 14px;
  color: #8aaaca;
}

.card-assign { }
.assign-tag {
  font-size: 13px;
  padding: 3px 10px;
  border-radius: 12px;
  font-weight: 500;
}
.assign-team { background: #0f2a50; color: #60a0e0; }
.assign-person { background: #0a2510; color: #60c060; }

// 进度条
.card-progress {
  display: flex;
  align-items: center;
  gap: 10px;
}
.progress-bar {
  flex: 1;
  height: 6px;
  background: #0a1525;
  border-radius: 3px;
  overflow: hidden;
}
.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #1a5fa0, #40a0ff);
  border-radius: 3px;
  transition: width 0.3s ease;
}
.progress-text {
  font-size: 13px;
  color: #6b8cb0;
  min-width: 70px;
  text-align: right;
}
.progress-unit { font-size: 11px; color: #3a5070; }

// 卡片底部
.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 2px;
  padding-top: 10px;
  border-top: 1px solid #0f2035;
}
.card-delivery {
  font-size: 13px;
  color: #5a7a9a;
  &.delivery-overdue { color: #ff4d4f; }
  &.delivery-urgent { color: #ff8040; }
  &.delivery-soon { color: #ffc040; }
}
.card-action {
  font-size: 13px;
  font-weight: 600;
  color: #2a5a9a;
  transition: color 0.2s;
  .ws-task-card:hover & { color: #40a0ff; }
}

// ===== 底部 =====
.ws-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 6px 32px;
  background: #0d1829;
  border-top: 1px solid #1e3a5f;
  font-size: 12px;
  color: #3a5070;
  flex-shrink: 0;
}
.ws-footer-sep { color: #1e3a5f; }

// ===== 浅色主题 =====
.ws-task-screen.light {
  background: linear-gradient(135deg, #e8f4fd 0%, #f0f6ff 40%, #fff 100%);
  color: #1a1a2e;

  .ws-header { background: linear-gradient(135deg, #fff, #eaf3fc); border-bottom-color: #d0dfe8; }
  .ws-logo { color: #1a6fba; }
  .ws-subtitle { color: #5a7a9a; }
  .ws-user-name { color: #1a1a2e; }
  .ws-user-account { color: #5a7a9a; background: #e0ecf6; }
  .ws-task-count { color: #5a7a9a; strong { color: #1a6fba; } }
  .ws-btn-refresh { background: #f0f6ff; border-color: #c0d8f0; color: #4a6a8a; &:hover:not(:disabled) { color: #1a6fba; border-color: #1a6fba; } }
  .ws-btn-exit { background: #fff0f0; border-color: #e0a0a0; color: #c04040; &:hover { background: #ffe0e0; } }

  .ws-empty-icon { color: #1a8a4a; }
  .ws-empty-text { color: #4a6a8a; }
  .ws-empty-sub { color: #8a9ab0; }

  .ws-task-card {
    background: #fff;
    border-color: #d0dfe8;
    &:hover { border-color: #1a6fba; box-shadow: 0 6px 24px rgba(26, 111, 186, 0.1); }
    &.is-inprogress { background: linear-gradient(135deg, #fff, #f0fff0); border-color: #a0d0a0; }
  }
  .card-wo-no { color: #5a7a9a; }
  .status-progress { background: #e8f8e8; color: #2a8a2a; }
  .status-ready { background: #e0ecf6; color: #3a6a9a; }
  .card-material-no { color: #5a7a9a; }
  .card-material-name { color: #1a1a2e; }
  .card-step-label { color: #5a7a9a; background: #f0f6ff; }
  .card-step-name { color: #3a6a9a; }
  .assign-team { background: #e0ecf6; color: #1a6fba; }
  .assign-person { background: #e8f8e8; color: #2a8a2a; }
  .progress-bar { background: #e0ecf6; }
  .progress-fill { background: linear-gradient(90deg, #1a6fba, #3a90e0); }
  .progress-text { color: #5a7a9a; }
  .progress-unit { color: #8a9ab0; }
  .card-footer { border-top-color: #e8f0f8; }
  .card-delivery { color: #5a7a9a; }
  .card-action { color: #1a6fba; }

  .ws-footer { background: #fff; border-top-color: #d0dfe8; color: #8a9ab0; }
  .ws-footer-sep { color: #d0dfe8; }
}
</style>
