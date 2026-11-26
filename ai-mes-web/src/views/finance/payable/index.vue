<template>
  <div class="page-container">
    <!-- 统计卡片 -->
    <el-row :gutter="16" style="margin-bottom: 16px">
      <el-col :span="6">
        <div class="stat-card">
          <div class="info">
            <div class="label">应付总额</div>
            <div class="value" style="color: #409eff">¥{{ stats.totalAmount.toFixed(2) }}</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="info">
            <div class="label">未付金额</div>
            <div class="value" style="color: #f56c6c">¥{{ stats.pendingAmount.toFixed(2) }}</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card" @click="filterByStatus('OVERDUE')">
          <div class="info">
            <div class="label">逾期金额</div>
            <div class="value" style="color: #e6a23c">¥{{ stats.overdueAmount.toFixed(2) }}</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="info">
            <div class="label">本月到期</div>
            <div class="value" style="color: #909399">¥{{ stats.dueSoon.toFixed(2) }}</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 搜索栏 -->
    <el-card shadow="never" style="margin-bottom: 12px">
      <el-form :model="queryParams" inline>
        <el-form-item label="应付编号">
          <el-input v-model="queryParams.payableNo" placeholder="请输入" clearable style="width: 160px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="单据号">
          <el-input v-model="queryParams.orderNo" placeholder="请输入" clearable style="width: 160px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="供应商名称">
          <el-input v-model="queryParams.supplierName" placeholder="请输入" clearable style="width: 160px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="来源类型">
          <el-select v-model="queryParams.sourceType" placeholder="全部" clearable style="width: 120px">
            <el-option label="采购" value="purchase_order" />
            <el-option label="外协" value="outsource_order" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="待付款" value="PENDING" />
            <el-option label="部分付款" value="PARTIAL_PAID" />
            <el-option label="已付清" value="PAID" />
            <el-option label="已完成" value="COMPLETED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="primary" plain @click="handleAdd">新增应付</el-button>
          <el-button type="success" plain @click="handleExport">导出</el-button>
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
            <template v-if="col.key === 'sourceType'">
              <el-tag :type="sourceTag[row.sourceType]" size="small">{{ sourceMap[row.sourceType] || row.sourceType || '其他' }}</el-tag>
            </template>
            <template v-else-if="col.key === 'totalAmount'">
              <span style="font-weight: 500">¥{{ Number(row.totalAmount || 0).toFixed(2) }}</span>
            </template>
            <template v-else-if="col.key === 'payProgress'">
              <el-progress :percentage="getPercent(row)" :stroke-width="14" :color="getPercent(row) >= 100 ? '#67c23a' : '#e6a23c'" />
            </template>
            <template v-else-if="col.key === 'paidAmount'">
              <span style="color: #67c23a">¥{{ Number(row.paidAmount || 0).toFixed(2) }}</span>
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
              <el-tag :type="statusTagMap[row.status]" size="small">{{ statusMap[row.status] || row.status }}</el-tag>
            </template>
            <template v-else>{{ row[col.key] || '-' }}</template>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-if="row.status !== 'COMPLETED'" type="primary" link size="small" @click="handlePayment(row)">付款</el-button>
            <span v-else style="color: #c0c4cc">已结清</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
          :total="total" :page-sizes="[10, 20, 50]" background layout="total, sizes, prev, pager, next"
          @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <ColumnConfigDialog ref="colConfigDialogRef" :all-columns="allColumns" :default-columns="FINANCE_PAYABLE_DEFAULT_COLUMNS" @save="saveConfig" />

    <!-- 新增应付 -->
    <el-dialog v-model="addDialogVisible" title="新增应付账款" width="560px" destroy-on-close>
      <el-form ref="addFormRef" :model="addForm" :rules="addRules" label-width="100px">
        <el-form-item label="来源类型" prop="sourceType">
          <el-select v-model="addForm.sourceType" placeholder="请选择" style="width: 100%">
            <el-option label="采购" value="purchase" />
            <el-option label="外协" value="outsource" />
            <el-option label="其他" value="other" />
          </el-select>
        </el-form-item>
        <el-form-item label="单据号" prop="orderNo">
          <el-input v-model="addForm.orderNo" placeholder="采购单号或外协单号" />
        </el-form-item>
        <el-form-item label="供应商" prop="supplierName">
          <el-input v-model="addForm.supplierName" placeholder="请输入供应商名称" />
        </el-form-item>
        <el-form-item label="应付金额" prop="totalAmount">
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

    <!-- 付款登记 -->
    <el-dialog v-model="payDialogVisible" title="付款登记" width="520px" destroy-on-close>
      <el-form ref="payFormRef" :model="payForm" :rules="payRules" label-width="100px">
        <el-descriptions :column="2" border size="small" style="margin-bottom: 16px">
          <el-descriptions-item label="单据号">{{ payForm.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="供应商">{{ payForm.supplierName }}</el-descriptions-item>
          <el-descriptions-item label="应付金额">¥{{ Number(payForm.totalAmount || 0).toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="未付金额">
            <span style="color: #f56c6c; font-weight: 600">¥{{ Number(payForm.maxAmount || 0).toFixed(2) }}</span>
          </el-descriptions-item>
        </el-descriptions>
        <el-form-item label="付款金额" prop="amount">
          <el-input-number v-model="payForm.amount" :min="0.01" :max="payForm.maxAmount" :precision="2" style="width: 100%" />
          <div style="margin-top: 4px">
            <el-button type="primary" link size="small" @click="payForm.amount = payForm.maxAmount">全额付款</el-button>
          </div>
        </el-form-item>
        <el-form-item label="付款日期" prop="paymentDate">
          <el-date-picker v-model="payForm.paymentDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="付款方式" prop="paymentMethod">
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
        <el-button type="primary" :loading="submitLoading" @click="handlePaySubmit">确认付款</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Setting } from '@element-plus/icons-vue'
import { getPayablePage, createPayable, addPayment, exportPayables } from '@/api/finance'
import { useColumnConfig } from '@/composables/useColumnConfig'
import ColumnConfigDialog from '@/components/ColumnConfigDialog.vue'

const FINANCE_PAYABLE_DEFAULT_COLUMNS = [
  { key: 'payableNo', label: '应付编号', minWidth: 175 },
  { key: 'orderNo', label: '单据号', minWidth: 175 },
  { key: 'sourceType', label: '来源', minWidth: 90, align: 'center' },
  { key: 'supplierName', label: '供应商', minWidth: 140 },
  { key: 'totalAmount', label: '应付金额', minWidth: 110, align: 'right' },
  { key: 'payProgress', label: '付款进度', minWidth: 130 },
  { key: 'paidAmount', label: '已付', minWidth: 100, align: 'right' },
  { key: 'remainingAmount', label: '未付', minWidth: 100, align: 'right' },
  { key: 'dueDate', label: '到期日', minWidth: 115 },
  { key: 'status', label: '状态', minWidth: 100, align: 'center' },
]
const colConfigDialogRef = ref(null)
const { visibleColumns, allColumns, loadColumnConfig, saveConfig } = useColumnConfig('finance_payable', FINANCE_PAYABLE_DEFAULT_COLUMNS)

const statusMap = { PENDING: '待付款', PARTIAL: '部分付款', PARTIAL_PAID: '部分付款', COMPLETED: '已完成', PAID: '已付清', pending: '待付款', partial_paid: '部分付款', paid: '已付清' }
const statusTagMap = { PENDING: 'warning', PARTIAL: '', PARTIAL_PAID: '', COMPLETED: 'success', PAID: 'success', pending: 'warning', partial_paid: '', paid: 'success' }
const sourceMap = { purchase: '采购', outsource: '外协', purchase_order: '采购', outsource_order: '外协', other: '其他' }
const sourceTag = { purchase: '', outsource: 'warning', purchase_order: '', outsource_order: 'warning', other: 'info' }

const stats = reactive({ totalAmount: 0, pendingAmount: 0, overdueAmount: 0, dueSoon: 0 })

function filterByStatus(status) { queryParams.status = status; queryParams.pageNum = 1; loadData() }
function isOverdue(row) { return row.dueDate && row.status !== 'COMPLETED' && new Date(row.dueDate) < new Date() }
function getPercent(row) {
  if (!row.totalAmount || row.totalAmount <= 0) return 0
  return Math.min(100, Math.round((row.paidAmount || 0) / row.totalAmount * 100))
}

// 查询
const queryParams = reactive({ payableNo: '', orderNo: '', supplierName: '', sourceType: '', status: '', pageNum: 1, pageSize: 10 })
const loading = ref(false); const tableData = ref([]); const total = ref(0); const submitLoading = ref(false)

async function loadData() {
  loading.value = true
  try {
    const res = await getPayablePage({
      payableNo: queryParams.payableNo || undefined, orderNo: queryParams.orderNo || undefined,
      supplierName: queryParams.supplierName || undefined,
      sourceType: queryParams.sourceType || undefined, status: queryParams.status || undefined,
      pageNum: queryParams.pageNum, pageSize: queryParams.pageSize
    })
    tableData.value = res.data?.records || []; total.value = res.data?.total || 0
    // 汇总
    const now = new Date(); const monthEnd = new Date(now.getFullYear(), now.getMonth() + 1, 0)
    stats.totalAmount = tableData.value.reduce((s, r) => s + Number(r.totalAmount || 0), 0)
    stats.pendingAmount = tableData.value.reduce((s, r) => s + Number(r.remainingAmount || 0), 0)
    stats.overdueAmount = tableData.value.filter(r => isOverdue(r)).reduce((s, r) => s + Number(r.remainingAmount || 0), 0)
    stats.dueSoon = tableData.value.filter(r => r.dueDate && r.status !== 'COMPLETED' && new Date(r.dueDate) <= monthEnd && new Date(r.dueDate) >= now).reduce((s, r) => s + Number(r.remainingAmount || 0), 0)
  } catch {} finally { loading.value = false }
}

function handleSearch() { queryParams.pageNum = 1; loadData() }
function handleReset() { queryParams.payableNo = ''; queryParams.orderNo = ''; queryParams.supplierName = ''; queryParams.sourceType = ''; queryParams.status = ''; queryParams.pageNum = 1; loadData() }
async function handleExport() {
  try {
    const res = await exportPayables({ payableNo: queryParams.payableNo || undefined, supplierName: queryParams.supplierName || undefined, status: queryParams.status || undefined, orderNo: queryParams.orderNo || undefined, sourceType: queryParams.sourceType || undefined })
    const url = window.URL.createObjectURL(new Blob([res.data || res]))
    const a = document.createElement('a'); a.href = url; a.download = `应付_${new Date().toISOString().slice(0,10)}.xlsx`; a.click()
    window.URL.revokeObjectURL(url)
  } catch { ElMessage.error('导出失败') }
}

// 新增
const addDialogVisible = ref(false); const addFormRef = ref(null)
const addForm = reactive({ sourceType: '', orderNo: '', supplierName: '', totalAmount: 0, dueDate: '', remark: '' })
const addRules = {
  sourceType: [{ required: true, message: '请选择来源类型', trigger: 'change' }],
  orderNo: [{ required: true, message: '请输入单据号', trigger: 'blur' }],
  supplierName: [{ required: true, message: '请输入供应商', trigger: 'blur' }],
  totalAmount: [{ required: true, message: '请输入金额', trigger: 'blur' }],
  dueDate: [{ required: true, message: '请选择到期日', trigger: 'change' }]
}
function handleAdd() {
  addForm.sourceType = ''; addForm.orderNo = ''; addForm.supplierName = ''
  addForm.totalAmount = 0; addForm.dueDate = ''; addForm.remark = ''
  addDialogVisible.value = true
}
async function handleAddSubmit() {
  await addFormRef.value.validate()
  submitLoading.value = true
  try {
    await createPayable({ ...addForm }); ElMessage.success('创建成功'); addDialogVisible.value = false; loadData()
  } finally { submitLoading.value = false }
}

// 付款
const payDialogVisible = ref(false); const payFormRef = ref(null)
const payForm = reactive({
  payableId: undefined, orderNo: '', supplierName: '', totalAmount: 0, maxAmount: 0,
  type: 'PAY', amount: 0, paymentDate: '', paymentMethod: '', remark: ''
})
const payRules = {
  amount: [{ required: true, message: '请输入金额', trigger: 'blur' }],
  paymentDate: [{ required: true, message: '请选择日期', trigger: 'change' }],
  paymentMethod: [{ required: true, message: '请选择方式', trigger: 'change' }]
}
function handlePayment(row) {
  payForm.payableId = row.id; payForm.orderNo = row.orderNo; payForm.supplierName = row.supplierName
  payForm.totalAmount = row.totalAmount; payForm.maxAmount = row.remainingAmount || 0
  payForm.type = 'PAY'; payForm.amount = row.remainingAmount || 0
  payForm.paymentDate = ''; payForm.paymentMethod = ''; payForm.remark = ''
  payDialogVisible.value = true
}
async function handlePaySubmit() {
  await payFormRef.value.validate()
  submitLoading.value = true
  try {
    await addPayment({ relatedType: 'payable', relatedId: payForm.payableId, amount: payForm.amount, paymentDate: payForm.paymentDate, paymentMethod: payForm.paymentMethod, remark: payForm.remark })
    ElMessage.success('付款成功'); payDialogVisible.value = false; loadData()
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
