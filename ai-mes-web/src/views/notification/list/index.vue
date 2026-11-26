<template>
  <div class="page-container">
    <el-card class="table-card" shadow="never">
      <!-- 分类Tab -->
      <el-tabs v-model="activeCategory" @tab-change="handleCategoryChange">
        <el-tab-pane name="all">
          <template #label>全部 <el-badge :value="unreadCounts.all" :hidden="!unreadCounts.all" :max="99" /></template>
        </el-tab-pane>
        <el-tab-pane name="approval">
          <template #label>审批 <el-badge :value="approvalTotal" :hidden="!approvalTotal" :max="99" /></template>
        </el-tab-pane>
        <el-tab-pane name="system">
          <template #label>系统 <el-badge :value="unreadCounts.system" :hidden="!unreadCounts.system" :max="99" /></template>
        </el-tab-pane>
        <el-tab-pane name="alert">
          <template #label>预警 <el-badge :value="unreadCounts.alert" :hidden="!unreadCounts.alert" :max="99" /></template>
        </el-tab-pane>
      </el-tabs>

      <!-- 工具栏（非审批Tab显示全部已读） -->
      <div v-if="activeCategory !== 'approval'" class="tab-toolbar">
        <el-button type="warning" plain size="small" @click="handleMarkAllRead">全部已读</el-button>
      </div>

      <!-- ======= 审批Tab：使用审批流程API ======= -->
      <template v-if="activeCategory === 'approval'">
        <!-- 审批子Tab -->
        <div class="approval-sub-tabs">
          <el-radio-group v-model="approvalTab" size="small" @change="loadApprovalData">
            <el-radio-button value="pending">待我审批</el-radio-button>
            <el-radio-button value="mine">我的申请</el-radio-button>
            <el-radio-button value="history">审批记录</el-radio-button>
          </el-radio-group>
        </div>

        <el-table :data="approvalData" v-loading="approvalLoading" border stripe>
          <el-table-column prop="businessType" label="业务类型" width="110">
            <template #default="{ row }">
              <el-tag size="small">{{ businessTypeLabel(row.businessType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="businessNo" label="业务单号" min-width="140" show-overflow-tooltip />
          <el-table-column prop="applicantName" label="申请人" width="100" />
          <el-table-column prop="flowName" label="流程名称" min-width="120" show-overflow-tooltip />
          <el-table-column prop="currentNodeName" label="当前节点" width="120">
            <template #default="{ row }">
              <span v-if="row.currentNodeName">{{ row.currentNodeName }}</span>
              <span v-else class="text-muted">—</span>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="提交时间" min-width="160" />
          <!-- 审批记录 tab 特有:我的操作 + 审批时间 -->
          <el-table-column v-if="approvalTab === 'history'" label="我的操作" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.myAction === 'APPROVE' ? 'success' : 'danger'" size="small">
                {{ row.myAction === 'APPROVE' ? '通过' : '驳回' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column v-if="approvalTab === 'history'" prop="myRemark" label="审批意见" min-width="140" show-overflow-tooltip />
          <el-table-column v-if="approvalTab === 'history'" prop="myActionTime" label="审批时间" min-width="160" />
          <el-table-column label="操作" width="240" fixed="right">
            <template #default="{ row }">
              <el-button type="info" size="small" link @click="viewBusinessDetail(row)">查看详情</el-button>
              <template v-if="approvalTab === 'pending'">
                <el-button type="primary" size="small" link @click="openApproveDialog(row, 'approve')">通过</el-button>
                <el-button type="danger" size="small" link @click="openApproveDialog(row, 'reject')">驳回</el-button>
              </template>
              <el-button type="primary" size="small" link @click="openProgress(row)">查看进度</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-wrapper">
          <el-pagination
            v-model:current-page="approvalParams.pageNum"
            v-model:page-size="approvalParams.pageSize"
            :total="approvalTotal"
            :page-sizes="[10, 20, 50]"
            background
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="loadApprovalData"
            @current-change="loadApprovalData"
          />
        </div>
      </template>

      <!-- ======= 非审批Tab：使用通知API ======= -->
      <template v-else>
        <el-table :data="tableData" v-loading="loading" border stripe :row-class-name="rowClassName">
          <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">
              <span :style="{ fontWeight: row.isRead ? 'normal' : 'bold' }">{{ row.title }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="content" label="内容" min-width="280" show-overflow-tooltip />
          <el-table-column prop="notificationType" label="类型" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="typeTagType(row.notificationType)" size="small">
                {{ typeText(row.notificationType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="isRead" label="状态" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.isRead ? 'info' : 'danger'" size="small">
                {{ row.isRead ? '已读' : '未读' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="时间" min-width="170" />
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link size="small" @click="handleViewNotification(row)">查看</el-button>
              <el-button v-if="!row.isRead" type="warning" link size="small" @click="handleMarkRead(row)">已读</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-wrapper">
          <el-pagination
            v-model:current-page="queryParams.pageNum"
            v-model:page-size="queryParams.pageSize"
            :total="total"
            :page-sizes="[10, 20, 50, 100]"
            background
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="loadData"
            @current-change="loadData"
          />
        </div>
      </template>
    </el-card>

    <!-- 审批操作弹窗 -->
    <el-dialog
      v-model="approveDialogVisible"
      :title="approveAction === 'approve' ? '审批通过' : '审批驳回'"
      width="460px"
      destroy-on-close
    >
      <div class="approve-info">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="业务类型">{{ businessTypeLabel(currentRow?.businessType) }}</el-descriptions-item>
          <el-descriptions-item label="业务单号">{{ currentRow?.businessNo }}</el-descriptions-item>
          <el-descriptions-item label="申请人">{{ currentRow?.applicantName }}</el-descriptions-item>
          <el-descriptions-item label="流程节点">{{ currentRow?.currentNodeName }}</el-descriptions-item>
        </el-descriptions>
        <div style="margin-top: 10px; text-align: right;">
          <el-button type="primary" link @click="viewBusinessDetail(currentRow)">查看详情</el-button>
        </div>
      </div>
      <el-form style="margin-top: 16px">
        <el-form-item label="审批意见">
          <el-input
            v-model="approveRemark"
            type="textarea"
            :rows="3"
            :placeholder="approveAction === 'approve' ? '请输入审批意见（可选）' : '请输入驳回原因（可选）'"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approveDialogVisible = false">取消</el-button>
        <el-button
          :type="approveAction === 'approve' ? 'primary' : 'danger'"
          :loading="submitLoading"
          @click="handleApprove"
        >{{ approveAction === 'approve' ? '确认通过' : '确认驳回' }}</el-button>
      </template>
    </el-dialog>

    <!-- BOM 版本详情弹窗 (审批前查看) -->
    <el-dialog v-model="bomDetailVisible" title="BOM 版本详情" width="820px" destroy-on-close append-to-body>
      <div v-loading="bomDetailLoading">
        <template v-if="bomDetailData">
          <el-descriptions :column="2" border size="small" style="margin-bottom:12px">
            <el-descriptions-item label="产品编号">{{ bomDetailData.productNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="产品名称">{{ bomDetailData.productName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="规格">{{ ($fmtSpec ? $fmtSpec(bomDetailData.productSpec) : bomDetailData.productSpec) || '-' }}</el-descriptions-item>
            <el-descriptions-item label="版本号">{{ bomDetailData.versionNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag size="small" :type="bomDetailData.status === 'pending_approval' ? 'warning' : 'info'">
                {{ bomVersionStatusLabel(bomDetailData.status) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="总物料成本">{{ bomDetailData.totalMaterialCost ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ bomDetailData.createTime || '-' }}</el-descriptions-item>
          </el-descriptions>
          <el-table :data="bomDetailData.items || []" border stripe size="small" row-key="id"
                    default-expand-all :tree-props="{ children: 'children' }" max-height="420">
            <el-table-column prop="sourceType" label="来源" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.sourceType === 'purchase' ? 'warning' : ''" size="small">
                  {{ row.sourceType === 'purchase' ? '外购' : '自制' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="物料名称" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ row.materialName || row.productName || '-' }}</template>
            </el-table-column>
            <el-table-column prop="quantity" label="用量" width="90" align="right" />
            <el-table-column prop="wastageRate" label="损耗率(%)" width="100" align="right" />
            <el-table-column prop="actualQuantity" label="实际用量" width="100" align="right" />
            <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
          </el-table>
        </template>
        <el-empty v-else description="暂无数据" />
      </div>
      <template #footer>
        <el-button @click="bomDetailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 订单变更详情弹窗 -->
    <el-dialog v-model="orderChangeDetailVisible" title="订单变更详情" width="980px" destroy-on-close append-to-body>
      <OrderChangeDiff v-if="orderChangeDetailVisible" :change-id="orderChangeBusinessId" />
      <template #footer>
        <el-button @click="orderChangeDetailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 通知详情弹窗 (非审批类点"查看"显示) -->
    <el-dialog v-model="detailDialogVisible" title="通知详情" width="560px" destroy-on-close>
      <template v-if="detailRow">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="标题">{{ detailRow.title }}</el-descriptions-item>
          <el-descriptions-item label="类型">
            <el-tag :type="typeTagType(detailRow.notificationType)" size="small">
              {{ typeText(detailRow.notificationType) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="时间">{{ detailRow.createTime }}</el-descriptions-item>
          <el-descriptions-item label="内容">
            <div style="white-space: pre-wrap; line-height: 1.6; word-break: break-word">{{ detailRow.content }}</div>
          </el-descriptions-item>
          <el-descriptions-item v-if="detailRow.relatedType" label="关联业务">
            {{ relatedTypeLabel(detailRow.relatedType) }}
            <span v-if="detailRow.relatedId" style="color:#909399; margin-left:8px">#{{ detailRow.relatedId }}</span>
          </el-descriptions-item>
        </el-descriptions>
      </template>
      <template #footer>
        <el-button v-if="detailRow && relatedRoute(detailRow)" type="primary" @click="jumpToRelated(detailRow)">
          查看业务详情
        </el-button>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 审批进度弹窗 -->
    <el-dialog v-model="progressDialogVisible" title="审批进度" width="560px" destroy-on-close>
      <div v-loading="progressLoading">
        <el-timeline v-if="progressData">
          <el-timeline-item
            v-for="node in progressData.nodes"
            :key="node.nodeId"
            :type="nodeTimelineType(node.nodeStatus)"
            :hollow="node.nodeStatus === 'waiting'"
          >
            <div class="timeline-node">
              <div class="node-title">
                <span class="node-name">{{ node.nodeName }}</span>
                <el-tag :type="nodeTagType(node.nodeStatus)" size="small" style="margin-left: 8px">
                  {{ nodeStatusLabel(node.nodeStatus) }}
                </el-tag>
              </div>
              <div v-if="node.records && node.records.length" class="node-records">
                <div v-for="(rec, idx) in node.records" :key="idx" class="record-item">
                  <span class="approver-name">{{ rec.approverName }}</span>
                  <el-tag :type="rec.action === 'APPROVE' ? 'success' : 'danger'" size="small">
                    {{ rec.action === 'APPROVE' ? '通过' : '驳回' }}
                  </el-tag>
                  <span v-if="rec.remark" class="remark">：{{ rec.remark }}</span>
                  <span class="action-time">{{ rec.actionTime }}</span>
                </div>
              </div>
              <div v-else-if="node.nodeStatus === 'pending'" class="waiting-text">等待审批中...</div>
            </div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无审批记录" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { getNotificationPage, markNotificationRead, markAllRead } from '@/api/system'
import { getMyApprovalPage, approveInstance, rejectInstance, getApprovalProgress } from '@/api/approval'
import { getBomVersionDetail } from '@/api/bom'
import OrderChangeDiff from '@/components/OrderChangeDiff.vue'

// ===== 通知相关 =====
const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const activeCategory = ref('all')
const unreadCounts = ref({ all: 0, system: 0, alert: 0 })

const queryParams = reactive({
  notificationType: undefined,
  pageNum: 1,
  pageSize: 10
})

// ===== 审批相关 =====
const approvalTab = ref('pending')
const approvalLoading = ref(false)
const approvalData = ref([])
const approvalTotal = ref(0)
const approvalParams = reactive({ pageNum: 1, pageSize: 20 })

const approveDialogVisible = ref(false)
const approveAction = ref('approve')
const approveRemark = ref('')
const currentRow = ref(null)
const submitLoading = ref(false)

const progressDialogVisible = ref(false)
const progressLoading = ref(false)
const progressData = ref(null)

// BOM 详情查看
const bomDetailVisible = ref(false)
const orderChangeDetailVisible = ref(false)
const orderChangeBusinessId = ref(null)
const bomDetailLoading = ref(false)
const bomDetailData = ref(null)

// 通知详情弹窗
const router = useRouter()
const detailDialogVisible = ref(false)
const detailRow = ref(null)

// 关联业务类型 → 路由跳转 (path)
const RELATED_ROUTE_MAP = {
  production_plan: '/production/planning',
  sales_order: '/order/list',
  purchase_order: '/purchase/order',
  purchase_request: '/purchase/request',
  purchase_receipt: '/purchase/receipt',
  work_order: '/production/work-order',
  outsource_order: '/outsource/order',
  qc_task: '/quality/task',
  inv_task: '/inventory/task',
  master_plan: '/mrp/master-plan'
}
const RELATED_TYPE_LABEL = {
  production_plan: '生产计划',
  sales_order: '销售订单',
  purchase_order: '采购订单',
  purchase_request: '采购申请',
  purchase_receipt: '采购收料',
  work_order: '生产工单',
  outsource_order: '委外订单',
  qc_task: '质检任务',
  inv_task: '库存任务',
  master_plan: '主计划',
  bom_version_activation: 'BOM 版本激活',
  order_change: '订单变更'
}
function relatedTypeLabel(type) {
  return RELATED_TYPE_LABEL[type] || type
}
function relatedRoute(row) {
  if (!row || !row.relatedType) return null
  return RELATED_ROUTE_MAP[row.relatedType] || null
}
function jumpToRelated(row) {
  const path = relatedRoute(row)
  if (!path) return
  detailDialogVisible.value = false
  router.push(path)
}

function bomVersionStatusLabel(s) {
  return ({ draft: '草稿', pending_approval: '审批中', active: '激活', deprecated: '已废弃', rejected: '已驳回' })[s] || s || '-'
}

// 各业务类型审批时"查看详情"的跳转目标 (相对 layout 根路径)
// 销售订单 / 采购订单等有独立详情页 → 带 query id 跳详情
// 其他列表型(采购申请/工单/外协)目前跳列表, 用户在列表里能找到对应行
const BUSINESS_DETAIL_NAV = {
  sales_order:     id => ({ path: '/order/detail', query: { id } }),
  purchase_order:  id => ({ path: '/purchase/order', query: { id } }),
  purchase_request: id => ({ path: '/purchase/request', query: { id } }),
  work_order:      id => ({ path: '/production/work-order', query: { id } }),
  outsource_order: id => ({ path: '/outsource/order', query: { id } })
}

async function viewBusinessDetail(row) {
  if (!row || !row.businessId) {
    ElMessage.warning('业务ID为空,无法加载详情')
    return
  }
  if (row.businessType === 'bom_version_activation') {
    bomDetailVisible.value = true
    bomDetailLoading.value = true
    bomDetailData.value = null
    try {
      const res = await getBomVersionDetail(row.businessId)
      bomDetailData.value = res.data || null
    } catch {
      ElMessage.error('加载 BOM 详情失败')
    } finally {
      bomDetailLoading.value = false
    }
    return
  }
  if (row.businessType === 'order_change') {
    orderChangeBusinessId.value = row.businessId
    orderChangeDetailVisible.value = true
    return
  }
  // 其余业务类型: 用路由 map 跳详情/列表
  const nav = BUSINESS_DETAIL_NAV[row.businessType]
  if (nav) {
    // 审批弹窗如果开着, 关一下避免遮挡
    approveDialogVisible.value = false
    router.push(nav(row.businessId))
    return
  }
  ElMessage.info('该业务类型暂未支持详情查看')
}

// ===== 通知类型工具函数 =====
function typeTagType(type) {
  const map = { approval: 'warning', system: '', alert: 'danger' }
  return map[type] || 'info'
}
function typeText(type) {
  const map = { approval: '审批', system: '系统', alert: '预警' }
  return map[type] || type || '消息'
}
function rowClassName({ row }) {
  return row.isRead ? '' : 'unread-row'
}

// ===== 审批工具函数 =====
const BUSINESS_TYPE_MAP = {
  sales_order: '销售订单',
  purchase_order: '采购订单',
  outsource_order: '委外订单',
  work_order: '生产工单',
  bom_version_activation: 'BOM版本激活'
}
function businessTypeLabel(type) {
  return BUSINESS_TYPE_MAP[type] || type || '—'
}

const STATUS_MAP = {
  PENDING: { label: '审批中', type: 'warning' },
  APPROVED: { label: '已通过', type: 'success' },
  REJECTED: { label: '已驳回', type: 'danger' },
  CANCELLED: { label: '已撤回', type: 'info' }
}
function statusLabel(s) { return STATUS_MAP[s]?.label || s }
function statusTagType(s) { return STATUS_MAP[s]?.type || '' }

const NODE_STATUS_MAP = {
  approved: { label: '已通过', type: 'success', timeline: 'success' },
  rejected: { label: '已驳回', type: 'danger', timeline: 'danger' },
  pending:  { label: '审批中', type: 'warning', timeline: 'warning' },
  waiting:  { label: '待审批', type: 'info',    timeline: '' }
}
function nodeStatusLabel(s) { return NODE_STATUS_MAP[s]?.label || s }
function nodeTagType(s) { return NODE_STATUS_MAP[s]?.type || '' }
function nodeTimelineType(s) { return NODE_STATUS_MAP[s]?.timeline || '' }

// ===== 通知数据加载 =====
async function loadData() {
  loading.value = true
  try {
    const params = {
      notificationType: activeCategory.value === 'all' ? undefined : activeCategory.value,
      pageNum: queryParams.pageNum,
      pageSize: queryParams.pageSize
    }
    const res = await getNotificationPage(params)
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } catch {} finally {
    loading.value = false
  }
  updateUnreadCounts()
}

function updateUnreadCounts() {
  const unread = tableData.value.filter(r => !r.isRead)
  unreadCounts.value.all = unread.length
  unreadCounts.value.system = unread.filter(r => r.notificationType === 'system').length
  unreadCounts.value.alert = unread.filter(r => r.notificationType === 'alert').length
}

async function handleMarkRead(row) {
  try {
    await markNotificationRead(row.id)
    row.isRead = true
    updateUnreadCounts()
  } catch {}
}

/**
 * 点查看:自动标记已读 + 按通知类型跳转相应位置
 * - 审批通知 → 切换到"审批" tab,如果关联 BOM 版本还自动打开 BOM 详情
 * - 系统/预警通知 → 目前只标记已读(无明确跳转目标)
 */
async function handleViewNotification(row) {
  // 1. 未读的先标记已读
  if (!row.isRead) {
    try {
      await markNotificationRead(row.id)
      row.isRead = true
      updateUnreadCounts()
    } catch {}
  }

  // 2. 审批类通知 → 切换到 审批 tab
  if (row.notificationType === 'approval') {
    activeCategory.value = 'approval'
    approvalTab.value = 'pending'
    approvalParams.pageNum = 1
    await loadApprovalData()
    // 3. BOM 版本审批 → 直接打开 BOM 详情弹窗
    if (row.relatedType === 'bom_version_activation' && row.relatedId) {
      // 在审批列表里找到对应 instance
      const instanceId = row.relatedId
      const instance = approvalData.value.find(x => x.instanceId === instanceId)
      if (instance) {
        viewBusinessDetail(instance)
      } else {
        // 通过 notification 的 relatedId 是 instance id,但 bom detail 需要 version id
        // 简化处理:通知用户切换到审批 tab 后自己点"查看详情"
        ElMessage.info('已切换到审批 tab,请在列表中找到对应任务点"查看详情"')
      }
    }
    return
  }

  // 4. 其他类型: 弹窗显示完整内容 (列表里的内容被 show-overflow-tooltip 截断了)
  detailRow.value = row
  detailDialogVisible.value = true
}

async function handleMarkAllRead() {
  try {
    await markAllRead()
    ElMessage.success('已全部标记为已读')
    loadData()
  } catch {}
}

// ===== 审批数据加载 =====
async function loadApprovalData() {
  approvalLoading.value = true
  try {
    const res = await getMyApprovalPage({
      tab: approvalTab.value,
      pageNum: approvalParams.pageNum,
      pageSize: approvalParams.pageSize
    })
    approvalData.value = res.data.records || []
    approvalTotal.value = res.data.total || 0
  } catch {} finally {
    approvalLoading.value = false
  }
}

// ===== 审批操作 =====
function openApproveDialog(row, action) {
  currentRow.value = row
  approveAction.value = action
  approveRemark.value = ''
  approveDialogVisible.value = true
}

async function handleApprove() {
  submitLoading.value = true
  try {
    const instanceId = currentRow.value.instanceId
    const remark = approveRemark.value
    if (approveAction.value === 'approve') {
      await approveInstance(instanceId, remark)
      ElMessage.success('审批通过')
    } else {
      await rejectInstance(instanceId, remark)
      ElMessage.success('已驳回')
    }
    approveDialogVisible.value = false
    loadApprovalData()
  } catch {} finally {
    submitLoading.value = false
  }
}

async function openProgress(row) {
  progressDialogVisible.value = true
  progressLoading.value = true
  progressData.value = null
  try {
    const res = await getApprovalProgress(row.businessType, row.businessId)
    progressData.value = res.data
  } catch {} finally {
    progressLoading.value = false
  }
}

// ===== Tab切换 =====
function handleCategoryChange() {
  if (activeCategory.value === 'approval') {
    approvalParams.pageNum = 1
    loadApprovalData()
  } else {
    queryParams.pageNum = 1
    queryParams.notificationType = activeCategory.value === 'all' ? undefined : activeCategory.value
    loadData()
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.page-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.table-card {
  flex: 1;
}
.tab-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}
.approval-sub-tabs {
  margin-bottom: 16px;
}
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.text-muted {
  color: #c0c4cc;
}
.approve-info {
  margin-bottom: 4px;
}
:deep(.unread-row) {
  background-color: #ecf5ff !important;
}
:deep(.el-badge) {
  margin-left: 4px;
}
// 审批进度样式
.timeline-node {
  .node-title {
    display: flex;
    align-items: center;
    margin-bottom: 8px;
    .node-name {
      font-weight: 500;
    }
  }
  .node-records {
    .record-item {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 13px;
      color: #606266;
      margin-bottom: 4px;
      .approver-name {
        font-weight: 500;
      }
      .remark {
        color: #909399;
      }
      .action-time {
        color: #c0c4cc;
        margin-left: auto;
      }
    }
  }
  .waiting-text {
    font-size: 13px;
    color: #e6a23c;
  }
}
</style>
