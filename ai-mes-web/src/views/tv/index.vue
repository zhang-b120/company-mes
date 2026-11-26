<template>
  <div class="tv-screen" :class="{ 'is-fullscreen': isFullscreen }">
    <!-- 顶部标题栏 -->
    <header class="tv-header">
      <div class="tv-header-left">
        <span class="tv-logo">AI-MES</span>
        <span class="tv-subtitle">智造云工单 · 生产大屏</span>
      </div>
      <div class="tv-header-center">
        <div class="tv-time">{{ currentTime }}</div>
        <div class="tv-date">{{ currentDate }}</div>
      </div>
      <div class="tv-header-right">
        <span class="tv-badge" v-if="totalAlerts > 0">{{ totalAlerts }} 项预警</span>
        <el-button link class="tv-btn" @click="toggleFullscreen">
          <span>{{ isFullscreen ? '退出全屏' : '全屏显示' }}</span>
        </el-button>
        <el-button link class="tv-btn" @click="loadAll">
          <span>刷新</span>
        </el-button>
      </div>
    </header>

    <!-- 主内容区域 - 4格布局 -->
    <main class="tv-main">
      <!-- 面板1: 今日成品入库 -->
      <div class="tv-panel panel-inbound">
        <div class="panel-header">
          <span class="panel-icon">📦</span>
          <span class="panel-title">今日成品入库</span>
          <span class="panel-badge badge-blue">{{ todayInboundTotal }} 件</span>
        </div>
        <div class="panel-body">
          <div v-if="loadingInbound" class="panel-loading">
            <div class="loading-dot"></div>
          </div>
          <template v-else-if="todayInbound.length > 0">
            <div class="inbound-summary">
              <div
                v-for="item in todayInbound"
                :key="item.materialId || item.materialName"
                class="inbound-row"
              >
                <span class="inbound-name">{{ item.materialName }}</span>
                <span class="inbound-spec" v-if="item.spec">{{ item.spec }}</span>
                <span class="inbound-qty">+{{ item.totalQty }} {{ item.unit || '' }}</span>
              </div>
            </div>
          </template>
          <div v-else class="panel-empty">
            <span class="empty-icon">✓</span>
            <span>今日暂无入库记录</span>
          </div>
        </div>
      </div>

      <!-- 面板2: 逾期订单预警 -->
      <div class="tv-panel panel-overdue">
        <div class="panel-header">
          <span class="panel-icon">⚠️</span>
          <span class="panel-title">逾期订单预警</span>
          <span class="panel-badge badge-red">{{ overdueOrders.length }} 单</span>
        </div>
        <div class="panel-body">
          <div v-if="loadingOrders" class="panel-loading">
            <div class="loading-dot"></div>
          </div>
          <template v-else-if="overdueOrders.length > 0">
            <div class="order-list">
              <div v-for="order in overdueOrders" :key="order.id" class="order-row overdue-row">
                <div class="order-no">{{ order.orderNo }}</div>
                <div class="order-customer">{{ order.customerName || '-' }}</div>
                <div class="order-date overdue-date">{{ order.deliveryDate }}</div>
                <div class="order-overdue-days">逾期 {{ getOverdueDays(order.deliveryDate) }} 天</div>
              </div>
            </div>
          </template>
          <div v-else class="panel-empty">
            <span class="empty-icon">✓</span>
            <span>暂无逾期订单</span>
          </div>
        </div>
      </div>

      <!-- 面板3: 3天内发货截止 -->
      <div class="tv-panel panel-deadline">
        <div class="panel-header">
          <span class="panel-icon">🚚</span>
          <span class="panel-title">3天内发货截止</span>
          <span class="panel-badge badge-orange">{{ deadlineOrders.length }} 单</span>
        </div>
        <div class="panel-body">
          <div v-if="loadingOrders" class="panel-loading">
            <div class="loading-dot"></div>
          </div>
          <template v-else-if="deadlineOrders.length > 0">
            <div class="order-list">
              <div v-for="order in deadlineOrders" :key="order.id" class="order-row deadline-row">
                <div class="order-no">{{ order.orderNo }}</div>
                <div class="order-customer">{{ order.customerName || '-' }}</div>
                <div class="order-date">{{ order.deliveryDate }}</div>
                <div class="order-remaining" :class="getRemainingClass(order.deliveryDate)">
                  {{ getRemainingText(order.deliveryDate) }}
                </div>
              </div>
            </div>
          </template>
          <div v-else class="panel-empty">
            <span class="empty-icon">✓</span>
            <span>3天内无发货截止订单</span>
          </div>
        </div>
      </div>

      <!-- 面板4: 安全库存预警 -->
      <div class="tv-panel panel-stock">
        <div class="panel-header">
          <span class="panel-icon">📊</span>
          <span class="panel-title">库存安全预警</span>
          <span class="panel-badge badge-yellow">{{ stockAlerts.length }} 项</span>
        </div>
        <div class="panel-body">
          <div v-if="loadingStock" class="panel-loading">
            <div class="loading-dot"></div>
          </div>
          <template v-else-if="stockAlerts.length > 0">
            <div class="stock-list">
              <div v-for="item in stockAlerts" :key="item.materialId || item.id" class="stock-row"
                :class="item._alertType === 'excess' ? 'stock-row-excess' : ''">
                <div class="stock-name">
                  {{ item.materialName }}
                  <span v-if="item._alertType === 'shortage'" class="stock-tag stock-tag-danger">不足</span>
                  <span v-else-if="item._alertType === 'excess'" class="stock-tag stock-tag-warn">超储</span>
                </div>
                <div class="stock-detail">
                  <span class="stock-current" :class="{ 'stock-danger': Number(item.availableQty) <= 0 }">
                    库存: {{ item.availableQty ?? 0 }} {{ item.unit || '' }}
                  </span>
                  <span v-if="item._alertType === 'excess'" class="stock-safe">/ 最大: {{ item.maxStock }}</span>
                  <span v-else class="stock-safe">/ 安全: {{ item.safetyStock ?? item.minStock ?? '-' }}</span>
                </div>
              </div>
            </div>
          </template>
          <div v-else class="panel-empty">
            <span class="empty-icon">✓</span>
            <span>库存状态正常</span>
          </div>
        </div>
      </div>
    </main>

    <!-- 底部信息栏 -->
    <footer class="tv-footer">
      <span>数据每 {{ refreshInterval / 60 }} 分钟自动刷新</span>
      <span class="tv-footer-sep">|</span>
      <span>上次刷新: {{ lastRefreshTime }}</span>
      <span class="tv-footer-sep">|</span>
      <span>AI-MES 智造云工单</span>
    </footer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
// 大屏专用接口：无需登录
import { getTvSalesOrders as getSalesOrderPage, getTvInventory as getInventoryPage, getTvInvHistory as getInvHistoryPage } from '@/api/tv'

// ===== 时间 =====
const currentTime = ref('')
const currentDate = ref('')
const lastRefreshTime = ref('')

function updateClock() {
  const now = new Date()
  const pad = n => String(n).padStart(2, '0')
  currentTime.value = `${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
  const days = ['日', '一', '二', '三', '四', '五', '六']
  currentDate.value = `${now.getFullYear()}年${now.getMonth() + 1}月${now.getDate()}日 星期${days[now.getDay()]}`
}

function formatDate(date) {
  const pad = n => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

// ===== 全屏 =====
const isFullscreen = ref(false)
function toggleFullscreen() {
  if (!document.fullscreenElement) {
    document.documentElement.requestFullscreen().catch(() => {})
    isFullscreen.value = true
  } else {
    document.exitFullscreen().catch(() => {})
    isFullscreen.value = false
  }
}
function onFullscreenChange() {
  isFullscreen.value = !!document.fullscreenElement
}

// ===== 数据 =====
const todayInbound = ref([])
const loadingInbound = ref(false)
const todayInboundTotal = computed(() => todayInbound.value.reduce((s, r) => s + Number(r.totalQty || 0), 0))

async function loadTodayInbound() {
  loadingInbound.value = true
  try {
    const today = formatDate(new Date())
    // 大屏"今日成品入库" — 只看物料分类是"成品"(category=finished)的, 半成品/辅料/原料/包材不算
    const res = await getInvHistoryPage({ dateStart: today, dateEnd: today, direction: 'in', invType: 'production_in', categoryCode: 'finished', pageNum: 1, pageSize: 200 })
    const records = res.data?.records || []
    // 按物料聚合
    const grouped = {}
    records.forEach(r => {
      const key = r.materialId || r.materialName
      if (!grouped[key]) {
        grouped[key] = { materialId: r.materialId, materialName: r.materialName, spec: r.spec, unit: r.unit, totalQty: 0 }
      }
      grouped[key].totalQty += Number(r.quantity || r.qty || 0)
    })
    todayInbound.value = Object.values(grouped).sort((a, b) => b.totalQty - a.totalQty)
  } catch {
    todayInbound.value = []
  } finally {
    loadingInbound.value = false
  }
}

const overdueOrders = ref([])
const deadlineOrders = ref([])
const loadingOrders = ref(false)

async function loadOrders() {
  loadingOrders.value = true
  try {
    const yesterday = formatDate(new Date(Date.now() - 86400000))
    const today = formatDate(new Date())
    const plus3 = formatDate(new Date(Date.now() + 3 * 86400000))

    // 在途订单（未完成未取消）中，交货日期已过 = 逾期
    // 交货日期在今天到3天后 = 即将截止
    const [overdueRes, deadlineRes] = await Promise.all([
      getSalesOrderPage({ deliveryDateRange: `2000-01-01,${yesterday}`, pageNum: 1, pageSize: 50 }).catch(() => ({ data: { records: [] } })),
      getSalesOrderPage({ deliveryDateRange: `${today},${plus3}`, pageNum: 1, pageSize: 50 }).catch(() => ({ data: { records: [] } }))
    ])
    const activeStatuses = new Set(['confirmed', 'mrp_planned', 'in_production', 'outsourcing', 'partial_shipped'])
    overdueOrders.value = (overdueRes.data?.records || []).filter(r => activeStatuses.has(r.status))
    deadlineOrders.value = (deadlineRes.data?.records || []).filter(r => activeStatuses.has(r.status))
  } catch {
    overdueOrders.value = []
    deadlineOrders.value = []
  } finally {
    loadingOrders.value = false
  }
}

const stockAlerts = ref([])
const loadingStock = ref(false)

async function loadStockAlerts() {
  loadingStock.value = true
  try {
    // 拉所有库存,客户端严格判定(避免后端过滤器误判)
    const res = await getInventoryPage({ pageNum: 1, pageSize: 500 })
    stockAlerts.value = (res.data?.records || [])
      .map(r => {
        const safety = Number(r.safetyStock ?? r.minStock ?? 0)
        const max = Number(r.maxStock ?? 0)
        const avail = Number(r.availableQty ?? 0)
        const shortage = safety > 0 && avail < safety
        const excess = max > 0 && avail > max
        return { ...r, _alertType: shortage ? 'shortage' : (excess ? 'excess' : null) }
      })
      .filter(r => r._alertType !== null)
  } catch {
    stockAlerts.value = []
  } finally {
    loadingStock.value = false
  }
}

const totalAlerts = computed(() => overdueOrders.value.length + stockAlerts.value.length)

async function loadAll() {
  await Promise.all([loadTodayInbound(), loadOrders(), loadStockAlerts()])
  const now = new Date()
  const pad = n => String(n).padStart(2, '0')
  lastRefreshTime.value = `${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
}

// ===== 日期计算 =====
function getOverdueDays(dateStr) {
  if (!dateStr) return '?'
  const diff = Math.floor((Date.now() - new Date(dateStr).getTime()) / 86400000)
  return diff > 0 ? diff : 0
}
function getRemainingDays(dateStr) {
  if (!dateStr) return 999
  return Math.ceil((new Date(dateStr).getTime() - Date.now()) / 86400000)
}
function getRemainingText(dateStr) {
  const days = getRemainingDays(dateStr)
  if (days === 0) return '今天截止'
  if (days === 1) return '明天截止'
  return `还剩 ${days} 天`
}
function getRemainingClass(dateStr) {
  const days = getRemainingDays(dateStr)
  if (days <= 0) return 'remaining-today'
  if (days === 1) return 'remaining-tomorrow'
  return 'remaining-normal'
}

// ===== 自动滚动 =====
const scrollCleanups = []

function setupAutoScroll() {
  cleanupAutoScroll()
  nextTick(() => {
    const panels = document.querySelectorAll('.panel-body')
    panels.forEach(panel => {
      if (panel.scrollHeight <= panel.clientHeight) return
      let paused = false
      let rafId = null
      let waitingAtBottom = false

      const step = () => {
        if (!paused && !waitingAtBottom) {
          panel.scrollTop += 0.5
          if (panel.scrollTop + panel.clientHeight >= panel.scrollHeight - 1) {
            waitingAtBottom = true
            setTimeout(() => {
              panel.scrollTop = 0
              waitingAtBottom = false
            }, 2000)
          }
        }
        rafId = requestAnimationFrame(step)
      }

      const onEnter = () => { paused = true }
      const onLeave = () => { paused = false }
      panel.addEventListener('mouseenter', onEnter)
      panel.addEventListener('mouseleave', onLeave)

      rafId = requestAnimationFrame(step)
      scrollCleanups.push(() => {
        cancelAnimationFrame(rafId)
        panel.removeEventListener('mouseenter', onEnter)
        panel.removeEventListener('mouseleave', onLeave)
      })
    })
  })
}

function cleanupAutoScroll() {
  scrollCleanups.forEach(fn => fn())
  scrollCleanups.length = 0
}

// ===== 定时刷新 =====
const refreshInterval = 300 // 5分钟
let clockTimer = null
let dataTimer = null

onMounted(async () => {
  updateClock()
  clockTimer = setInterval(updateClock, 1000)
  await loadAll()
  setupAutoScroll()
  dataTimer = setInterval(async () => {
    await loadAll()
    setupAutoScroll()
  }, refreshInterval * 1000)
  document.addEventListener('fullscreenchange', onFullscreenChange)
})

onBeforeUnmount(() => {
  cleanupAutoScroll()
  clearInterval(clockTimer)
  clearInterval(dataTimer)
  document.removeEventListener('fullscreenchange', onFullscreenChange)
})
</script>

<style scoped lang="scss">
.tv-screen {
  width: 100vw;
  height: 100vh;
  background: #0a0f1e;
  color: #e8eaf0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

// 顶部标题栏
.tv-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 32px;
  background: linear-gradient(135deg, #0d1829 0%, #112040 100%);
  border-bottom: 1px solid #1e3a5f;
  flex-shrink: 0;
}
.tv-header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.tv-logo {
  font-size: 22px;
  font-weight: 800;
  color: #40a0ff;
  letter-spacing: 2px;
}
.tv-subtitle {
  font-size: 14px;
  color: #6b8cb0;
}
.tv-header-center {
  text-align: center;
}
.tv-time {
  font-size: 36px;
  font-weight: 700;
  color: #fff;
  letter-spacing: 4px;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}
.tv-date {
  font-size: 13px;
  color: #6b8cb0;
  margin-top: 2px;
}
.tv-header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.tv-badge {
  background: #ff4d4f;
  color: #fff;
  padding: 3px 10px;
  border-radius: 12px;
  font-size: 13px;
  font-weight: 600;
  animation: blink 2s infinite;
}
@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}
.tv-btn {
  color: #6b8cb0 !important;
  font-size: 13px;
  &:hover { color: #40a0ff !important; }
}

// 主内容区
.tv-main {
  flex: 1;
  display: grid;
  grid-template-columns: 1fr 1fr;
  grid-template-rows: 1fr 1fr;
  gap: 12px;
  padding: 12px 16px;
  overflow: hidden;
}

// 面板通用
.tv-panel {
  background: #0d1829;
  border-radius: 8px;
  border: 1px solid #1e3a5f;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.panel-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  border-bottom: 1px solid #1e3a5f;
  flex-shrink: 0;
}
.panel-icon { font-size: 18px; }
.panel-title {
  font-size: 16px;
  font-weight: 600;
  color: #c8d8f0;
  flex: 1;
}
.panel-badge {
  padding: 2px 10px;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 700;
}
.badge-blue { background: #1a3a6b; color: #40a0ff; }
.badge-red { background: #4a0f0f; color: #ff6b6b; }
.badge-orange { background: #4a2800; color: #ffa040; }
.badge-yellow { background: #3a2c00; color: #ffc040; }

.panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
  scrollbar-width: none;
  &::-webkit-scrollbar { display: none; }
}

.panel-loading {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100%;
}
.loading-dot {
  width: 32px;
  height: 32px;
  border: 3px solid #1e3a5f;
  border-top-color: #40a0ff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

.panel-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: 8px;
  color: #3a5070;
  font-size: 14px;
}
.empty-icon { font-size: 32px; color: #1e6b3f; }

// 成品入库
.inbound-summary { display: flex; flex-direction: column; gap: 6px; }
.inbound-row {
  display: flex;
  align-items: center;
  padding: 8px 10px;
  background: #0f2035;
  border-radius: 6px;
  border-left: 3px solid #40a0ff;
  gap: 8px;
}
.inbound-name { font-size: 15px; font-weight: 600; color: #c8d8f0; flex: 1; }
.inbound-spec { font-size: 12px; color: #5a7a9a; }
.inbound-qty { font-size: 18px; font-weight: 700; color: #40c080; min-width: 60px; text-align: right; }

// 订单列表
.order-list { display: flex; flex-direction: column; gap: 5px; }
.order-row {
  display: grid;
  grid-template-columns: auto 1fr auto auto;
  align-items: center;
  padding: 8px 10px;
  border-radius: 6px;
  gap: 12px;
}
.overdue-row {
  background: #200a0a;
  border-left: 3px solid #ff4d4f;
}
.deadline-row {
  background: #1a1000;
  border-left: 3px solid #ffa040;
}
.order-no { font-size: 13px; color: #8a9ab0; font-family: monospace; }
.order-customer { font-size: 14px; color: #c8d8f0; }
.order-date { font-size: 13px; color: #6b8cb0; }
.overdue-date { color: #ff6b6b !important; }
.order-overdue-days { font-size: 13px; font-weight: 700; color: #ff4d4f; min-width: 70px; text-align: right; }
.order-remaining { font-size: 13px; font-weight: 700; min-width: 80px; text-align: right; }
.remaining-today { color: #ff4d4f; }
.remaining-tomorrow { color: #ffa040; }
.remaining-normal { color: #40c080; }

// 库存预警
.stock-list { display: flex; flex-direction: column; gap: 5px; }
.stock-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  background: #1a1500;
  border-radius: 6px;
  border-left: 3px solid #ffc040;
}
.stock-name { font-size: 14px; color: #c8d8f0; }
.stock-tag {
  display: inline-block;
  padding: 1px 6px;
  margin-left: 6px;
  font-size: 11px;
  border-radius: 3px;
  vertical-align: middle;
}
.stock-tag-danger { background: #4a0f0f; color: #ff8080; border: 1px solid #ff4d4f; }
.stock-tag-warn { background: #3a2800; color: #ffc040; border: 1px solid #ffa040; }
.stock-row-excess { border-left-color: #ff4d4f; background: #2a0a0a; }
.stock-detail { display: flex; align-items: center; gap: 4px; }
.stock-current { font-size: 14px; font-weight: 700; color: #ffa040; }
.stock-danger { color: #ff4d4f !important; }
.stock-safe { font-size: 12px; color: #5a7a9a; }

// 底部
.tv-footer {
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
.tv-footer-sep { color: #1e3a5f; }

// 面板颜色差异
.panel-inbound { border-color: #1a3a6b; }
.panel-overdue { border-color: #4a0f0f; }
.panel-deadline { border-color: #4a2800; }
.panel-stock { border-color: #3a2c00; }
</style>
