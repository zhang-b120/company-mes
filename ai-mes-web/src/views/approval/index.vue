<template>
  <div class="page-container">
    <el-card shadow="never" class="table-card">
      <!-- Tab 切换 -->
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="待我审批" name="pending" />
        <el-tab-pane label="我的申请" name="mine" />
        <el-tab-pane label="审批记录" name="history" />
      </el-tabs>

      <!-- 列配置按钮 -->
      <div style="display: flex; justify-content: flex-end; margin-bottom: 10px">
        <el-button :icon="Setting" @click="colConfigDialogRef.open()">列配置</el-button>
      </div>

      <!-- 数据表格 -->
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column v-for="col in visibleColumns" :key="col.key"
          :label="col.label" :min-width="col.minWidth || 100" :align="col.align || 'left'" show-overflow-tooltip>
          <template #default="{ row }">
            <template v-if="col.key === 'businessType'">
              <el-tag size="small">{{ businessTypeLabel(row.businessType) }}</el-tag>
            </template>
            <template v-else-if="col.key === 'currentNodeName'">
              <span v-if="row.currentNodeName">{{ row.currentNodeName }}</span>
              <span v-else class="text-muted">—</span>
            </template>
            <template v-else-if="col.key === 'status'">
              <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            </template>
            <template v-else-if="col.key === 'createTime'">{{ $fmtDate(row.createTime) }}</template>
            <template v-else>{{ row[col.key] || '-' }}</template>
          </template>
        </el-table-column>
        <!-- 审批记录 tab 特有列 -->
        <el-table-column v-if="activeTab === 'history'" label="我的操作" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.myAction === 'APPROVE' ? 'success' : 'danger'" size="small">
              {{ row.myAction === 'APPROVE' ? '通过' : '驳回' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="activeTab === 'history'" prop="myRemark" label="审批意见" min-width="140" show-overflow-tooltip />
        <el-table-column v-if="activeTab === 'history'" prop="myActionTime" label="审批时间" min-width="160">
          <template #default="{ row }">{{ $fmtDate(row.myActionTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button type="info" size="small" link @click="viewDetail(row)">查看详情</el-button>
            <!-- 待审批 Tab：审批操作 -->
            <template v-if="activeTab === 'pending'">
              <el-button
                v-permission="'approval:task:approve'"
                type="primary"
                size="small"
                link
                @click="openApproveDialog(row, 'approve')"
              >通过</el-button>
              <el-button
                v-permission="'approval:task:reject'"
                type="danger"
                size="small"
                link
                @click="openApproveDialog(row, 'reject')"
              >驳回</el-button>
            </template>
            <el-button type="primary" size="small" link @click="openProgress(row)">查看进度</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          background
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
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
        <!-- BOM 类型审批: 显示查看 BOM 详情按钮 -->
        <div v-if="currentRow?.businessType === 'bom_version_activation'" style="margin-top: 10px; text-align: right;">
          <el-button type="primary" link @click="viewBomDetail(currentRow)">
            <el-icon><View /></el-icon>
            查看 BOM 详情
          </el-button>
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
                  <span class="action-time">{{ $fmtDate(rec.actionTime) }}</span>
                </div>
              </div>
              <div v-else-if="node.nodeStatus === 'pending'" class="waiting-text">等待审批中...</div>
            </div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无审批记录" />
      </div>
    </el-dialog>

    <!-- BOM 详情弹窗(审批时查看) -->
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
            <el-descriptions-item label="创建时间">{{ $fmtDate ? $fmtDate(bomDetailData.createTime) : bomDetailData.createTime }}</el-descriptions-item>
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

    <ColumnConfigDialog ref="colConfigDialogRef" :all-columns="allColumns" :visible-keys="visibleColumns.map(c=>c.key)" @save="saveConfig" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Setting, View } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getMyApprovalPage, approveInstance, rejectInstance, getApprovalProgress } from '@/api/approval'
import { getBomVersionDetail } from '@/api/bom'
import { useColumnConfig } from '@/composables/useColumnConfig'
import ColumnConfigDialog from '@/components/ColumnConfigDialog.vue'
import OrderChangeDiff from '@/components/OrderChangeDiff.vue'

const APPROVAL_DEFAULT_COLUMNS = [
  { key: 'businessType', label: '业务类型', minWidth: 110 },
  { key: 'businessNo', label: '业务单号', minWidth: 160 },
  { key: 'applicantName', label: '申请人', minWidth: 100 },
  { key: 'flowName', label: '流程名称', minWidth: 120 },
  { key: 'currentNodeName', label: '当前节点', minWidth: 120 },
  { key: 'status', label: '状态', minWidth: 100 },
  { key: 'createTime', label: '提交时间', minWidth: 160 },
]
const colConfigDialogRef = ref(null)
const { visibleColumns, allColumns, loadColumnConfig, saveConfig } = useColumnConfig('approval', APPROVAL_DEFAULT_COLUMNS)

const activeTab = ref('pending')

const queryParams = reactive({
  pageNum: 1,
  pageSize: 20
})

const loading = ref(false)
const tableData = ref([])
const total = ref(0)

// 审批操作
const approveDialogVisible = ref(false)
const approveAction = ref('approve') // 'approve' | 'reject'
const approveRemark = ref('')
const currentRow = ref(null)
const submitLoading = ref(false)

// 审批进度
const progressDialogVisible = ref(false)
const progressLoading = ref(false)
const progressData = ref(null)

// BOM 详情查看 (审批时用)
const bomDetailVisible = ref(false)
const bomDetailLoading = ref(false)
const bomDetailData = ref(null)

function bomVersionStatusLabel(s) {
  return ({ draft: '草稿', pending_approval: '审批中', active: '激活', deprecated: '已废弃', rejected: '已驳回' })[s] || s || '-'
}

async function viewBomDetail(row) {
  if (!row || !row.businessId) {
    ElMessage.warning('业务ID为空,无法加载 BOM 详情')
    return
  }
  bomDetailVisible.value = true
  bomDetailLoading.value = true
  bomDetailData.value = null
  try {
    const res = await getBomVersionDetail(row.businessId)
    bomDetailData.value = res.data || null
  } catch (e) {
    ElMessage.error('加载 BOM 详情失败')
  } finally {
    bomDetailLoading.value = false
  }
}

async function loadData() {
  loading.value = true
  try {
    const res = await getMyApprovalPage({
      tab: activeTab.value,
      pageNum: queryParams.pageNum,
      pageSize: queryParams.pageSize
    })
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } catch {
    // handled by interceptor
  } finally {
    loading.value = false
  }
}

function handleTabChange() {
  queryParams.pageNum = 1
  loadData()
}

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
    loadData()
  } catch {
    // handled by interceptor
  } finally {
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
  } catch {
    // handled by interceptor
  } finally {
    progressLoading.value = false
  }
}

// ===== 订单变更详情(diff) 弹窗状态 =====
const orderChangeDetailVisible = ref(false)
const orderChangeBusinessId = ref(null)
function viewDetail(row) {
  if (!row || !row.businessId) {
    ElMessage.warning('业务ID为空,无法加载详情')
    return
  }
  if (row.businessType === 'order_change') {
    orderChangeBusinessId.value = row.businessId
    orderChangeDetailVisible.value = true
    return
  }
  // 其他类型(目前只有 BOM): 复用原 BOM 弹窗
  viewBomDetail(row)
}

// ===== 工具函数 =====
const BUSINESS_TYPE_MAP = {
  sales_order: '销售订单',
  purchase_order: '采购订单',
  work_order: '生产工单',
  outsource_order: '委外订单',
  bom_version_activation: 'BOM版本激活',
  order_change: '订单变更',
  qc_concession: '质检让步接收'
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

onMounted(() => {
  loadData()
  loadColumnConfig()
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
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.approve-info {
  margin-bottom: 4px;
}
.text-muted {
  color: #909399;
}
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
