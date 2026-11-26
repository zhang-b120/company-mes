<template>
  <div class="page-container">
    <!-- 统计卡片 -->
    <el-row :gutter="16" style="margin-bottom: 16px">
      <el-col :span="6" v-for="s in statCards" :key="s.key">
        <div class="stat-card" @click="s.filterStatus && filterByStatus(s.filterStatus)">
          <div class="info">
            <div class="label">{{ s.label }}</div>
            <div class="value" :style="{ color: s.color }">
              {{ s.prefix || '' }}{{ typeof stats[s.key] === 'number' ? (s.isMoney ? stats[s.key].toFixed(2) : stats[s.key]) : 0 }}
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 账龄分析 -->
    <el-card shadow="never" style="margin-bottom: 12px">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>账龄分析</span>
          <el-button type="warning" size="small" @click="sendReminder" :disabled="!overdueList.length">
            一键催款提醒 ({{ overdueList.length }})
          </el-button>
        </div>
      </template>
      <el-row :gutter="16">
        <el-col :span="6" v-for="a in agingBuckets" :key="a.key">
          <div style="text-align: center; padding: 8px 0">
            <div style="font-size: 13px; color: #909399; margin-bottom: 4px">{{ a.label }}</div>
            <div :style="{ fontSize: '20px', fontWeight: 600, color: a.color }">
              ¥{{ (aging[a.key] || 0).toFixed(2) }}
            </div>
            <div style="font-size: 12px; color: #c0c4cc">{{ agingCount[a.key] || 0 }} 笔</div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <!-- 搜索栏 -->
    <el-card shadow="never" style="margin-bottom: 12px">
      <el-form :model="queryParams" inline>
        <el-form-item label="应收编号">
          <el-input v-model="queryParams.receivableNo" placeholder="请输入" clearable style="width: 160px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="订单号">
          <el-input v-model="queryParams.orderNo" placeholder="请输入" clearable style="width: 160px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="客户名称">
          <el-input v-model="queryParams.customerName" placeholder="请输入" clearable style="width: 160px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="待收款" value="PENDING" />
            <el-option label="部分收款" value="PARTIAL" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="已逾期" value="OVERDUE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="primary" plain @click="handleAdd">新增应收</el-button>
          <el-dropdown trigger="click" @command="handleExportCommand" style="margin: 0 8px">
            <el-button type="success" plain>
              导出<el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="detail">明细导出</el-dropdown-item>
                <el-dropdown-item command="day" divided>按天汇总</el-dropdown-item>
                <el-dropdown-item command="month">按月汇总</el-dropdown-item>
                <el-dropdown-item command="quarter">按季汇总</el-dropdown-item>
                <el-dropdown-item command="year">按年汇总</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button :icon="Setting" @click="colConfigDialogRef.open()">列配置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据表格 -->
    <el-card shadow="never">
      <el-table :data="tableData" v-loading="loading" border stripe size="small"
        :row-class-name="({ row }) => isOverdue(row) ? 'overdue-row' : ''">
        <el-table-column v-for="col in visibleColumns" :key="col.key"
          :label="col.label" :min-width="col.minWidth || 100" :align="col.align || 'left'" show-overflow-tooltip>
          <template #default="{ row }">
            <template v-if="col.key === 'totalAmount'">
              <span style="font-weight: 500">¥{{ Number(row.totalAmount || 0).toFixed(2) }}</span>
            </template>
            <template v-else-if="col.key === 'payProgress'">
              <el-progress :percentage="getPercent(row)" :stroke-width="14" :color="getPercent(row) >= 100 ? '#67c23a' : '#409eff'" />
            </template>
            <template v-else-if="col.key === 'receivedAmount'">
              <span style="color: #67c23a">¥{{ Number(row.receivedAmount || 0).toFixed(2) }}</span>
            </template>
            <template v-else-if="col.key === 'remainingAmount'">
              <span :style="{ color: row.remainingAmount > 0 ? '#f56c6c' : '#67c23a', fontWeight: 500 }">
                ¥{{ Number(row.remainingAmount || 0).toFixed(2) }}
              </span>
            </template>
            <template v-else-if="col.key === 'dueDate'">
              <span :style="{ color: isOverdue(row) ? '#f56c6c' : '' }">{{ row.dueDate || '-' }}</span>
              <el-tag v-if="isOverdue(row)" type="danger" size="small" style="margin-left: 4px">逾期</el-tag>
            </template>
            <template v-else-if="col.key === 'status'">
              <el-tag :type="statusTag[row.status]" size="small">{{ statusMap[row.status] || row.status }}</el-tag>
            </template>
            <template v-else>{{ row[col.key] || '-' }}</template>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-if="row.status !== 'COMPLETED'" type="primary" link size="small" @click="handlePayment(row)">收款</el-button>
            <el-button v-if="isOverdue(row)" type="warning" link size="small" @click="sendSingleReminder(row)">催款</el-button>
            <span v-if="row.status === 'COMPLETED'" style="color: #c0c4cc">已结清</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
          :total="total" :page-sizes="[10, 20, 50]" background layout="total, sizes, prev, pager, next"
          @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <ColumnConfigDialog ref="colConfigDialogRef" :all-columns="allColumns" :default-columns="FINANCE_RECEIVABLE_DEFAULT_COLUMNS" @save="saveConfig" />

    <!-- 新增应收 -->
    <el-dialog v-model="addDialogVisible" title="新增应收账款" width="560px" destroy-on-close>
      <el-form ref="addFormRef" :model="addForm" :rules="addRules" label-width="100px">
        <el-form-item label="订单号" prop="orderNo">
          <el-input v-model="addForm.orderNo" placeholder="请输入订单号" />
        </el-form-item>
        <el-form-item label="客户名称" prop="customerName">
          <el-input v-model="addForm.customerName" placeholder="请输入客户名称" />
        </el-form-item>
        <el-form-item label="应收金额" prop="totalAmount">
          <el-input-number v-model="addForm.totalAmount" :min="0.01" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="到期日" prop="dueDate">
          <el-date-picker v-model="addForm.dueDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="addForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleAddSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 收款登记 -->
    <el-dialog v-model="payDialogVisible" title="收款登记" width="520px" destroy-on-close>
      <el-form ref="payFormRef" :model="payForm" :rules="payRules" label-width="100px">
        <el-descriptions :column="2" border size="small" style="margin-bottom: 16px">
          <el-descriptions-item label="订单号">{{ payForm.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="客户">{{ payForm.customerName }}</el-descriptions-item>
          <el-descriptions-item label="应收金额">¥{{ Number(payForm.totalAmount || 0).toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="未收金额">
            <span style="color: #f56c6c; font-weight: 600">¥{{ Number(payForm.maxAmount || 0).toFixed(2) }}</span>
          </el-descriptions-item>
        </el-descriptions>
        <el-form-item label="收款金额" prop="amount">
          <el-input-number v-model="payForm.amount" :min="0.01" :max="payForm.maxAmount" :precision="2" style="width: 100%" />
          <div style="margin-top: 4px">
            <el-button type="primary" link size="small" @click="payForm.amount = payForm.maxAmount">全额收款</el-button>
          </div>
        </el-form-item>
        <el-form-item label="收款日期" prop="paymentDate">
          <el-date-picker v-model="payForm.paymentDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="收款方式" prop="paymentMethod">
          <el-select v-model="payForm.paymentMethod" placeholder="请选择" style="width: 100%">
            <el-option label="银行转账" value="BANK_TRANSFER" />
            <el-option label="现金" value="CASH" />
            <el-option label="支票" value="CHECK" />
            <el-option label="承兑汇票" value="ACCEPTANCE" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="payForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="payDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handlePaySubmit">确认收款</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Setting, ArrowDown } from '@element-plus/icons-vue'
import { getReceivablePage, createReceivable, addPayment, exportReceivables, exportReceivablesByPeriod } from '@/api/finance'
import { useColumnConfig } from '@/composables/useColumnConfig'
import ColumnConfigDialog from '@/components/ColumnConfigDialog.vue'

const FINANCE_RECEIVABLE_DEFAULT_COLUMNS = [
  { key: 'receivableNo', label: '应收编号', minWidth: 160 },
  { key: 'orderNo', label: '订单号', minWidth: 160 },
  { key: 'customerName', label: '客户', minWidth: 140 },
  { key: 'totalAmount', label: '应收金额', minWidth: 110, align: 'right' },
  { key: 'payProgress', label: '回款进度', minWidth: 140 },
  { key: 'receivedAmount', label: '已收', minWidth: 100, align: 'right' },
  { key: 'remainingAmount', label: '未收', minWidth: 100, align: 'right' },
  { key: 'dueDate', label: '到期日', minWidth: 110 },
  { key: 'status', label: '状态', minWidth: 90, align: 'center' },
]
const colConfigDialogRef = ref(null)
const { visibleColumns, allColumns, loadColumnConfig, saveConfig } = useColumnConfig('finance_receivable', FINANCE_RECEIVABLE_DEFAULT_COLUMNS)

const statusMap = { PENDING: '待收款', PARTIAL: '部分收款', COMPLETED: '已完成', OVERDUE: '已逾期', pending: '待收款', partial_received: '部分收款', received: '已收款' }
const statusTag = { PENDING: 'warning', PARTIAL: '', COMPLETED: 'success', OVERDUE: 'danger', pending: 'warning', partial_received: '', received: 'success' }

const statCards = [
  { key: 'totalAmount', label: '应收总额', color: '#409eff', prefix: '¥', isMoney: true },
  { key: 'pendingAmount', label: '未收金额', color: '#f56c6c', prefix: '¥', isMoney: true },
  { key: 'overdueAmount', label: '逾期金额', color: '#e6a23c', prefix: '¥', isMoney: true, filterStatus: 'OVERDUE' },
  { key: 'collectionRate', label: '回收率(%)', color: '#67c23a' }
]
const stats = reactive({ totalAmount: 0, pendingAmount: 0, overdueAmount: 0, collectionRate: 0 })

const agingBuckets = [
  { key: 'a30', label: '0-30天', color: '#67c23a' },
  { key: 'a60', label: '31-60天', color: '#e6a23c' },
  { key: 'a90', label: '61-90天', color: '#f56c6c' },
  { key: 'a90p', label: '90天以上', color: '#c45656' }
]
const aging = reactive({ a30: 0, a60: 0, a90: 0, a90p: 0 })
const agingCount = reactive({ a30: 0, a60: 0, a90: 0, a90p: 0 })

function filterByStatus(status) { queryParams.status = status; queryParams.pageNum = 1; loadData() }
function isOverdue(row) { return row.dueDate && row.status !== 'COMPLETED' && new Date(row.dueDate) < new Date() }
function getPercent(row) {
  if (!row.totalAmount || row.totalAmount <= 0) return 0
  return Math.min(100, Math.round((row.receivedAmount || 0) / row.totalAmount * 100))
}

// 计算账龄
function calcAging() {
  const now = new Date()
  const buckets = { a30: 0, a60: 0, a90: 0, a90p: 0 }
  const counts = { a30: 0, a60: 0, a90: 0, a90p: 0 }
  tableData.value.filter(r => r.status !== 'COMPLETED').forEach(r => {
    const days = Math.floor((now - new Date(r.createTime || r.dueDate)) / 86400000)
    const amt = Number(r.remainingAmount || 0)
    if (days <= 30) { buckets.a30 += amt; counts.a30++ }
    else if (days <= 60) { buckets.a60 += amt; counts.a60++ }
    else if (days <= 90) { buckets.a90 += amt; counts.a90++ }
    else { buckets.a90p += amt; counts.a90p++ }
  })
  Object.assign(aging, buckets); Object.assign(agingCount, counts)
}

const overdueList = computed(() => tableData.value.filter(r => isOverdue(r)))

// 查询
const queryParams = reactive({ receivableNo: '', orderNo: '', customerName: '', status: '', pageNum: 1, pageSize: 10 })
const loading = ref(false); const tableData = ref([]); const total = ref(0); const submitLoading = ref(false)

async function loadData() {
  loading.value = true
  try {
    const res = await getReceivablePage({
      receivableNo: queryParams.receivableNo || undefined, orderNo: queryParams.orderNo || undefined,
      customerName: queryParams.customerName || undefined,
      status: queryParams.status || undefined, pageNum: queryParams.pageNum, pageSize: queryParams.pageSize
    })
    tableData.value = res.data?.records || []; total.value = res.data?.total || 0
    // 汇总
    const all = tableData.value
    stats.totalAmount = all.reduce((s, r) => s + Number(r.totalAmount || 0), 0)
    stats.pendingAmount = all.reduce((s, r) => s + Number(r.remainingAmount || 0), 0)
    stats.overdueAmount = overdueList.value.reduce((s, r) => s + Number(r.remainingAmount || 0), 0)
    stats.collectionRate = stats.totalAmount > 0 ? Math.round((stats.totalAmount - stats.pendingAmount) / stats.totalAmount * 100) : 0
    calcAging()
  } catch {} finally { loading.value = false }
}

function handleSearch() { queryParams.pageNum = 1; loadData() }
function handleReset() { queryParams.receivableNo = ''; queryParams.orderNo = ''; queryParams.customerName = ''; queryParams.status = ''; queryParams.pageNum = 1; loadData() }
async function handleExport() {
  try {
    const res = await exportReceivables({ receivableNo: queryParams.receivableNo || undefined, customerName: queryParams.customerName || undefined, status: queryParams.status || undefined, orderNo: queryParams.orderNo || undefined })
    const url = window.URL.createObjectURL(new Blob([res.data || res]))
    const a = document.createElement('a'); a.href = url; a.download = `应收_${new Date().toISOString().slice(0,10)}.xlsx`; a.click()
    window.URL.revokeObjectURL(url)
  } catch { ElMessage.error('导出失败') }
}

// 按粒度汇总导出 (按到期日聚合, 与列表查询条件无关 — 汇总要全量)
const granularityLabel = { day: '按天', month: '按月', quarter: '按季', year: '按年' }
async function handleExportCommand(command) {
  if (command === 'detail') return handleExport()
  try {
    const res = await exportReceivablesByPeriod({ granularity: command })
    const url = window.URL.createObjectURL(new Blob([res.data || res]))
    const a = document.createElement('a')
    a.href = url
    a.download = `应收欠款_${granularityLabel[command]}汇总_${new Date().toISOString().slice(0,10)}.xlsx`
    a.click()
    window.URL.revokeObjectURL(url)
  } catch { ElMessage.error('导出失败') }
}

// 催款
function sendReminder() { ElMessage.success(`已向 ${overdueList.value.length} 笔逾期应收的客户发送催款提醒`) }
function sendSingleReminder(row) { ElMessage.success(`已向客户「${row.customerName}」发送催款提醒`) }

// 新增应收
const addDialogVisible = ref(false); const addFormRef = ref(null)
const addForm = reactive({ orderNo: '', customerName: '', totalAmount: 0, dueDate: '', remark: '' })
const addRules = {
  orderNo: [{ required: true, message: '请输入订单号', trigger: 'blur' }],
  customerName: [{ required: true, message: '请输入客户名称', trigger: 'blur' }],
  totalAmount: [{ required: true, message: '请输入金额', trigger: 'blur' }],
  dueDate: [{ required: true, message: '请选择到期日', trigger: 'change' }]
}
function handleAdd() {
  addForm.orderNo = ''; addForm.customerName = ''; addForm.totalAmount = 0
  addForm.dueDate = ''; addForm.remark = ''
  addDialogVisible.value = true
}
async function handleAddSubmit() {
  await addFormRef.value.validate()
  submitLoading.value = true
  try {
    await createReceivable({ ...addForm }); ElMessage.success('创建成功'); addDialogVisible.value = false; loadData()
  } finally { submitLoading.value = false }
}

// 收款登记
const payDialogVisible = ref(false); const payFormRef = ref(null)
const payForm = reactive({
  receivableId: undefined, orderNo: '', customerName: '', totalAmount: 0, maxAmount: 0,
  type: 'RECEIVE', amount: 0, paymentDate: '', paymentMethod: '', remark: ''
})
const payRules = {
  amount: [{ required: true, message: '请输入金额', trigger: 'blur' }],
  paymentDate: [{ required: true, message: '请选择日期', trigger: 'change' }],
  paymentMethod: [{ required: true, message: '请选择方式', trigger: 'change' }]
}
function handlePayment(row) {
  payForm.receivableId = row.id; payForm.orderNo = row.orderNo
  payForm.customerName = row.customerName; payForm.totalAmount = row.totalAmount
  payForm.maxAmount = row.remainingAmount || 0; payForm.type = 'RECEIVE'
  payForm.amount = row.remainingAmount || 0; payForm.paymentDate = ''; payForm.paymentMethod = ''; payForm.remark = ''
  payDialogVisible.value = true
}
async function handlePaySubmit() {
  await payFormRef.value.validate()
  submitLoading.value = true
  try {
    await addPayment({ relatedType: 'receivable', relatedId: payForm.receivableId, amount: payForm.amount, paymentDate: payForm.paymentDate, paymentMethod: payForm.paymentMethod, remark: payForm.remark })
    ElMessage.success('收款成功'); payDialogVisible.value = false; loadData()
  } finally { submitLoading.value = false }
}

onMounted(() => { loadData(); loadColumnConfig() })
</script>

<style scoped lang="scss">
.page-container { padding: 16px; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; }
.stat-card {
  background: #fff; border-radius: 8px; padding: 16px 20px;
  box-shadow: 0 1px 4px rgba(0,0,0,.08); cursor: pointer; transition: box-shadow .3s;
  &:hover { box-shadow: 0 4px 12px rgba(0,0,0,.15); }
  .info .label { font-size: 13px; color: #909399; }
  .info .value { font-size: 22px; font-weight: 600; margin-top: 4px; }
}
:deep(.overdue-row) { background-color: #fef0f0 !important; }
</style>
