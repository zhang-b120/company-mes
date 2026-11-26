<template>
  <div class="workbench">
    <!-- ── 问候横幅 ── -->
    <div class="greeting-banner">
      <div class="greeting-left">
        <div class="greeting-text">{{ greetingText }}，{{ userStore.userInfo?.realName || userStore.userInfo?.username }} 👋</div>
        <div class="greeting-sub">{{ todayStr }}，祝您工作顺利</div>
      </div>
      <div class="greeting-right">
        <el-button type="primary" plain size="small" @click="editMode = !editMode">
          <el-icon><Setting /></el-icon>
          {{ editMode ? '完成编辑' : '自定义快捷入口' }}
        </el-button>
      </div>
    </div>

    <!-- ── 快捷入口 ── -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <span class="section-title">快捷入口</span>
        <span v-if="editMode" class="section-hint">点击图标可取消固定，从下方选择添加</span>
      </template>

      <!-- 已固定快捷入口 -->
      <div class="shortcut-grid">
        <div
          v-for="sc in pinnedShortcuts"
          :key="sc.key"
          class="shortcut-item"
          @click="editMode ? unpin(sc.key) : $router.push(sc.path)"
        >
          <div class="shortcut-icon" :style="{ background: sc.color }">
            <el-icon :size="24"><component :is="sc.icon" /></el-icon>
            <el-icon v-if="editMode" class="unpin-badge"><CircleClose /></el-icon>
          </div>
          <span class="shortcut-name">{{ sc.name }}</span>
        </div>
      </div>

      <!-- 编辑模式：可添加的快捷入口 -->
      <template v-if="editMode">
        <el-divider>可添加的入口</el-divider>
        <div class="shortcut-grid">
          <div
            v-for="sc in unpinnedShortcuts"
            :key="sc.key"
            class="shortcut-item add-item"
            @click="pin(sc.key)"
          >
            <div class="shortcut-icon" :style="{ background: '#f0f2f5' }">
              <el-icon :size="24" style="color:#bbb"><component :is="sc.icon" /></el-icon>
              <el-icon class="add-badge"><CirclePlus /></el-icon>
            </div>
            <span class="shortcut-name" style="color:#bbb">{{ sc.name }}</span>
          </div>
        </div>
      </template>
    </el-card>

    <!-- ── 下方两栏 ── -->
    <div class="bottom-row">
      <!-- 我的待办 -->
      <el-card shadow="never" class="section-card todo-card">
        <template #header><span class="section-title">我的待办</span></template>
        <el-tabs v-model="todoTab" class="todo-tabs">
          <el-tab-pane
            v-for="tab in todoTabs"
            :key="tab.key"
            :name="tab.key"
          >
            <template #label>
              {{ tab.label }}
              <el-badge
                v-if="todoCounts[tab.key]"
                :value="todoCounts[tab.key]"
                class="tab-badge"
              />
            </template>
          </el-tab-pane>
        </el-tabs>
        <div class="todo-list-wrap" v-loading="todoLoading">
          <template v-if="currentTodoList.length > 0">
            <div
              v-for="item in currentTodoList"
              :key="item.id"
              class="todo-item"
              @click="$router.push(item.path)"
            >
              <div class="todo-title">{{ item.title }}</div>
              <div class="todo-meta">
                <span class="todo-info">{{ item.info }}</span>
                <span class="todo-time">{{ item.time }}</span>
              </div>
            </div>
          </template>
          <el-empty v-else description="暂无待办" :image-size="60" />
        </div>
      </el-card>

      <!-- 消息通知 -->
      <el-card shadow="never" class="section-card notif-card">
        <template #header>
          <span class="section-title">消息通知</span>
          <el-button link type="primary" size="small" @click="$router.push('/notification/list')">
            查看全部
          </el-button>
        </template>
        <div class="notif-list-wrap" v-loading="notifLoading">
          <template v-if="notifications.length > 0">
            <div
              v-for="n in notifications"
              :key="n.id"
              class="notif-item"
              :class="{ unread: !n.isRead }"
              @click="$router.push('/notification/list')"
            >
              <div class="notif-dot" v-if="!n.isRead"></div>
              <div class="notif-body">
                <div class="notif-title">{{ n.title }}</div>
                <div class="notif-time">{{ n.createTime }}</div>
              </div>
            </div>
          </template>
          <el-empty v-else description="暂无消息" :image-size="60" />
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import {
  Document, ShoppingCart, Box, Aim, Money, MagicStick,
  Setting, Operation, EditPen, Connection, Check,
  CircleClose, CirclePlus, Bell
} from '@element-plus/icons-vue'
import { getNotificationPage } from '@/api/system'
import request from '@/utils/request'

const router = useRouter()
const userStore = useUserStore()

// ── 问候 ──────────────────────────────────────────────
const hour = new Date().getHours()
const greetingText = hour < 6 ? '夜深了' : hour < 12 ? '早上好' : hour < 18 ? '下午好' : '晚上好'
const todayStr = new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' })

// ── 全部快捷入口定义 ────────────────────────────────────
const ALL_SHORTCUTS = [
  { key: 'order',      name: '销售订单',  path: '/order/list',               icon: Document,     color: '#409eff' },
  { key: 'work-order', name: '生产工单',  path: '/production/work-orders',   icon: Operation,    color: '#67c23a' },
  { key: 'purchase',   name: '采购管理',  path: '/purchase/orders',          icon: ShoppingCart, color: '#e6a23c' },
  { key: 'inventory',  name: '库存管理',  path: '/inventory/stock',          icon: Box,          color: '#909399' },
  { key: 'quality',    name: '质量管理',  path: '/quality/tasks',            icon: Aim,          color: '#f56c6c' },
  { key: 'approval',   name: '审批中心',  path: '/approval-manage/tasks',    icon: Check,        color: '#9b59b6' },
  { key: 'finance',    name: '财务成本',  path: '/finance/finance-main',     icon: Money,        color: '#1abc9c' },
  { key: 'ai',         name: 'AI助手',   path: '/ai/workspace',             icon: MagicStick,   color: '#3498db' },
  { key: 'workreport', name: '报工记录',  path: '/production/workreports',   icon: EditPen,      color: '#e67e22' },
  { key: 'outsource',  name: '外协管理',  path: '/production/outsource',     icon: Connection,   color: '#16a085' },
  { key: 'notify',     name: '消息通知',  path: '/notification/list',        icon: Bell,         color: '#8e44ad' },
  { key: 'settings',   name: '系统设置',  path: '/system/settings',          icon: Setting,      color: '#7f8c8d' },
]

const DEFAULT_PINNED = ['order', 'work-order', 'purchase', 'inventory', 'quality', 'approval', 'finance', 'ai']

const storageKey = computed(() => `wb_shortcuts_${userStore.userInfo?.id || 'default'}`)

const pinnedKeys = ref(
  JSON.parse(localStorage.getItem(storageKey.value) || 'null') || [...DEFAULT_PINNED]
)

const editMode = ref(false)

const pinnedShortcuts = computed(() =>
  pinnedKeys.value.map(k => ALL_SHORTCUTS.find(s => s.key === k)).filter(Boolean)
)
const unpinnedShortcuts = computed(() =>
  ALL_SHORTCUTS.filter(s => !pinnedKeys.value.includes(s.key))
)

function pin(key) {
  pinnedKeys.value = [...pinnedKeys.value, key]
  localStorage.setItem(storageKey.value, JSON.stringify(pinnedKeys.value))
}
function unpin(key) {
  pinnedKeys.value = pinnedKeys.value.filter(k => k !== key)
  localStorage.setItem(storageKey.value, JSON.stringify(pinnedKeys.value))
}

// ── 待办 ──────────────────────────────────────────────
const todoTab = ref('approval')
const todoTabs = [
  { key: 'approval',  label: '待审批' },
  { key: 'workorder', label: '生产工单' },
  { key: 'purchase',  label: '采购申请' },
  { key: 'inbound',   label: '入库申请' },
  { key: 'quality',   label: '质检任务' },
]
const todoLoading = ref(false)
const todoCounts = ref({})
const todoData = ref({})

const currentTodoList = computed(() => todoData.value[todoTab.value] || [])

async function loadTodos() {
  todoLoading.value = true
  const apis = [
    request.get('/approval/my-tasks', { params: { pageSize: 20 } }).catch(() => null),
    request.get('/production/work-orders/page', { params: { pageSize: 20, status: 'processing' } }).catch(() => null),
    request.get('/purchase/requests/page', { params: { pageSize: 20, status: 'pending' } }).catch(() => null),
    request.get('/inventory/requests/page', { params: { pageSize: 20, status: 'pending' } }).catch(() => null),
    request.get('/quality/tasks/page', { params: { pageSize: 20, status: 'pending' } }).catch(() => null),
  ]
  const [approval, workorder, purchase, inbound, quality] = await Promise.all(apis)

  // 兜底标签 (后端返回 flowName 时优先用 flowName, 没配 flowName 才落到这里)
  const BUSINESS_TYPE_MAP = {
    sales_order: '销售订单审批', purchase_order: '采购订单审批', purchase_request: '采购申请审批',
    work_order: '生产工单审批', outsource_order: '委外订单审批', outsource_request: '外协申请审批',
    bom_version_activation: 'BOM版本激活审批', order_change: '订单变更审批',
    inv_request: '出入库申请审批', return_order: '退货单审批', shipment: '发货单审批'
  }
  const businessTypeLabel = (t) => BUSINESS_TYPE_MAP[t] || (t ? `${t}审批` : '审批')

  const buildList = (res, titleField, infoField, pathPrefix, isApproval = false) => {
    const rows = res?.data?.records || res?.data?.list || res?.data || []
    return rows.slice(0, 20).map(r => {
      // 标题: titleField 优先, 然后通用业务单号, 最后退到任意 id, 仍然为空就给 '-'
      const baseTitle = r[titleField] || r.title || r.businessNo
        || r.orderNo || r.woNo || r.requestNo || r.taskNo
        || r.id || r.instanceId
      // 审批 Tab: 直接按业务类型映射 — 例如 "销售订单审批 SO..." / "订单变更审批 OC..."
      const prefix = isApproval ? businessTypeLabel(r.businessType) : ''
      const t = prefix
        ? `${prefix} ${baseTitle ?? ''}`.trim()
        : baseTitle
      // 副信息: infoField 优先, 兜底 applicantName, 后接当前审批节点
      const baseInfo = r[infoField] || r.applicantName || ''
      const i = isApproval && r.currentNodeName
        ? `${baseInfo}${baseInfo ? ' · ' : ''}${r.currentNodeName}`
        : baseInfo
      return {
        id: r.id || r.instanceId || r.businessId,
        title: t == null || t === '' ? '-' : String(t),
        info: i,
        time: r.createTime || r.applyTime || '',
        path: pathPrefix,
      }
    })
  }

  todoData.value = {
    approval:  buildList(approval,  'businessNo', 'applicantName', '/approval-manage/tasks', true),
    workorder: buildList(workorder, 'woNo',        'productName',   '/production/work-orders'),
    purchase:  buildList(purchase,  'requestNo',   'supplierName',  '/purchase/requests'),
    inbound:   buildList(inbound,   'requestNo',   'warehouseName', '/inventory/request'),
    quality:   buildList(quality,   'taskNo',      'materialName',  '/quality/tasks'),
  }
  todoCounts.value = Object.fromEntries(
    Object.entries(todoData.value).map(([k, v]) => [k, v.length || 0])
  )
  todoLoading.value = false
}

// ── 通知 ──────────────────────────────────────────────
const notifLoading = ref(false)
const notifications = ref([])

async function loadNotifications() {
  notifLoading.value = true
  try {
    const res = await getNotificationPage({ pageNum: 1, pageSize: 10 })
    notifications.value = res?.data?.records || []
  } catch {
    // 静默
  } finally {
    notifLoading.value = false
  }
}

onMounted(() => {
  loadTodos()
  loadNotifications()
})
</script>

<style scoped lang="scss">
.workbench {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

// ── 问候 ──
.greeting-banner {
  background: linear-gradient(135deg, #1677ff 0%, #4096ff 100%);
  border-radius: 12px;
  padding: 20px 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #fff;
  .greeting-text {
    font-size: 20px;
    font-weight: 600;
  }
  .greeting-sub {
    font-size: 13px;
    margin-top: 4px;
    opacity: 0.85;
  }
}

// ── 卡片 ──
.section-card {
  border-radius: 10px;
  :deep(.el-card__header) {
    padding: 12px 16px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    border-bottom: 1px solid #f0f0f0;
  }
}
.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #1a1a1a;
}
.section-hint {
  font-size: 12px;
  color: #999;
  margin-left: 8px;
}

// ── 快捷入口 ──
.shortcut-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  padding: 4px 0;
}
.shortcut-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  width: 72px;
  cursor: pointer;
  transition: transform 0.15s;
  &:hover { transform: translateY(-2px); }
  &.add-item { opacity: 0.6; &:hover { opacity: 1; } }
}
.shortcut-icon {
  position: relative;
  width: 52px;
  height: 52px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  .unpin-badge {
    position: absolute;
    top: -6px;
    right: -6px;
    font-size: 16px;
    color: #f56c6c;
    background: #fff;
    border-radius: 50%;
  }
  .add-badge {
    position: absolute;
    top: -6px;
    right: -6px;
    font-size: 16px;
    color: #67c23a;
    background: #fff;
    border-radius: 50%;
  }
}
.shortcut-name {
  font-size: 12px;
  color: #444;
  text-align: center;
  white-space: nowrap;
}

// ── 下方两栏 ──
.bottom-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

// ── 待办 ──
.todo-tabs {
  :deep(.el-tabs__header) { margin-bottom: 0; }
}
.tab-badge {
  margin-left: 4px;
  :deep(.el-badge__content) { font-size: 10px; }
}
.todo-list-wrap {
  max-height: 420px;
  overflow-y: auto;
  margin-top: 8px;
  min-height: 80px;
}
.todo-item {
  padding: 10px 4px;
  border-bottom: 1px solid #f5f5f5;
  cursor: pointer;
  transition: background 0.15s;
  border-radius: 6px;
  &:hover { background: #f5f7fa; }
  &:last-child { border-bottom: none; }
}
.todo-title {
  font-size: 13px;
  color: #1a1a1a;
  font-weight: 500;
}
.todo-meta {
  display: flex;
  justify-content: space-between;
  margin-top: 3px;
}
.todo-info { font-size: 12px; color: #999; }
.todo-time { font-size: 12px; color: #bbb; }

// ── 通知 ──
.notif-list-wrap {
  max-height: 420px;
  overflow-y: auto;
  min-height: 80px;
}
.notif-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 4px;
  border-bottom: 1px solid #f5f5f5;
  cursor: pointer;
  border-radius: 6px;
  &:hover { background: #f5f7fa; }
  &:last-child { border-bottom: none; }
  &.unread .notif-title { font-weight: 600; color: #1a1a1a; }
}
.notif-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #f56c6c;
  flex-shrink: 0;
  margin-top: 6px;
}
.notif-body { flex: 1; min-width: 0; }
.notif-title {
  font-size: 13px;
  color: #444;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.notif-time { font-size: 12px; color: #bbb; margin-top: 2px; }
</style>
