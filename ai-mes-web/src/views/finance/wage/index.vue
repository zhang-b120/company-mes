<template>
  <div class="page-container">
    <!-- 统计卡片 -->
    <el-row :gutter="16" style="margin-bottom: 16px">
      <el-col :span="6" v-for="s in statCards" :key="s.key">
        <div class="stat-card" @click="s.filterStatus && filterByStatus(s.filterStatus)">
          <el-icon class="icon" :style="{ color: s.color }"><component :is="s.icon" /></el-icon>
          <div class="info">
            <div class="label">{{ s.label }}</div>
            <div class="value" :style="{ color: s.color }">
              {{ s.prefix || '' }}{{ typeof stats[s.key] === 'number' ? (s.isMoney ? stats[s.key].toFixed(2) : stats[s.key]) : 0 }}
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 搜索栏 -->
    <el-card shadow="never" style="margin-bottom: 12px">
      <el-form :model="queryParams" inline>
        <el-form-item label="月份">
          <el-date-picker v-model="queryParams.month" type="month" placeholder="请选择" value-format="YYYY-MM" style="width: 150px" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="queryParams.realName" placeholder="员工姓名" clearable style="width: 150px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已确认" value="CONFIRMED" />
            <el-option label="已审批" value="APPROVED" />
            <el-option label="已发放" value="PAID" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 工具栏 -->
    <el-card shadow="never">
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px">
        <div style="display: flex; gap: 8px">
          <el-button type="primary" @click="openCreateDialog">新增</el-button>
          <el-button type="success" @click="openGenerateDialog">
            <el-icon style="margin-right: 4px"><Cpu /></el-icon>生成月度工资
          </el-button>
          <el-button type="warning" @click="handleExport">
            <el-icon style="margin-right: 4px"><Download /></el-icon>导出工资条
          </el-button>
          <el-divider direction="vertical" />
          <el-button :icon="Setting" @click="colConfigDialogRef.open()">列配置</el-button>
          <el-divider direction="vertical" />
          <el-button :disabled="!selectedIds.length" @click="handleBatchConfirm">批量确认 ({{ selectedIds.length }})</el-button>
          <el-button :disabled="!selectedIds.length" @click="handleBatchApprove">批量审批</el-button>
          <el-button :disabled="!selectedIds.length" type="success" @click="handleBatchPay">批量发放</el-button>
        </div>
      </div>

      <el-table :data="tableData" v-loading="loading" border stripe size="small" show-summary
        :summary-method="getSummary" @selection-change="onSelectionChange">
        <el-table-column type="selection" width="45" />
        <el-table-column v-for="col in visibleColumns" :key="col.key"
          :label="col.label" :min-width="col.minWidth || 100" :align="col.align || 'left'" show-overflow-tooltip>
          <template #default="{ row }">
            <template v-if="col.key === 'realName'">
              <el-button type="primary" link @click="openDetail(row)">{{ row.realName }}</el-button>
            </template>
            <template v-else-if="col.key === 'pieceWage'">{{ fmt(row.pieceWage) }}</template>
            <template v-else-if="col.key === 'teamAllocatedWage'">{{ fmt(row.teamAllocatedWage) }}</template>
            <template v-else-if="col.key === 'overtimeWage'">{{ fmt(row.overtimeWage) }}</template>
            <template v-else-if="col.key === 'bonus'">{{ fmt(row.bonus) }}</template>
            <template v-else-if="col.key === 'deduction'">
              <span :style="{ color: row.deduction > 0 ? '#f56c6c' : '' }">{{ fmt(row.deduction) }}</span>
            </template>
            <template v-else-if="col.key === 'totalWage'">
              <span style="font-weight: 600; color: #409eff">{{ fmt(row.totalWage) }}</span>
            </template>
            <template v-else-if="col.key === 'status'">
              <el-tag :type="statusTag[row.status]" size="small">{{ statusMap[row.status] || row.status }}</el-tag>
            </template>
            <template v-else>{{ row[col.key] || '-' }}</template>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'DRAFT'">
              <el-button type="primary" link size="small" @click="handleConfirm(row)">确认</el-button>
              <el-button type="info" link size="small" @click="openEdit(row)">编辑</el-button>
            </template>
            <template v-else-if="row.status === 'CONFIRMED'">
              <el-button type="success" link size="small" @click="handleApprove(row)">审批</el-button>
              <el-button type="danger" link size="small" @click="handleReject(row)">退回</el-button>
            </template>
            <template v-else-if="row.status === 'APPROVED'">
              <el-button type="success" link size="small" @click="handlePay(row)">发放</el-button>
              <el-button type="danger" link size="small" @click="handleReject(row)">退回</el-button>
            </template>
            <template v-else>
              <el-button type="info" link size="small" @click="openDetail(row)">查看</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
          :total="total" :page-sizes="[10, 20, 50, 100]" background layout="total, sizes, prev, pager, next"
          @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <!-- 个人工资明细抽屉 -->
    <el-drawer v-model="detailVisible" :title="`${detailData?.realName || ''} - ${detailData?.month || ''} 工资明细`" size="700px">
      <template v-if="detailData">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="员工姓名">{{ detailData.realName }}</el-descriptions-item>
          <el-descriptions-item label="月份">{{ detailData.month }}</el-descriptions-item>
          <el-descriptions-item label="出勤天数">{{ detailData.workDays || '-' }} 天</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTag[detailData.status]">{{ statusMap[detailData.status] }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <el-tabs v-model="detailTab" style="margin-top: 16px">
          <el-tab-pane label="工资构成" name="breakdown">
            <div class="wage-breakdown">
              <div class="wage-item">
                <span class="wage-label">计件工资</span>
                <span class="wage-value">{{ fmt(detailData.pieceWage) }}</span>
              </div>
              <div class="wage-item" v-if="detailData.personalPieceWage">
                <span class="wage-label sub">  - 个人计件</span>
                <span class="wage-value">{{ fmt(detailData.personalPieceWage) }}</span>
              </div>
              <div class="wage-item" v-if="detailData.teamAllocatedWage">
                <span class="wage-label sub">  - 班组分配</span>
                <span class="wage-value">{{ fmt(detailData.teamAllocatedWage) }}</span>
              </div>
              <div class="wage-item">
                <span class="wage-label">加班工资</span>
                <span class="wage-value">{{ fmt(detailData.overtimeWage) }}</span>
              </div>
              <div class="wage-item">
                <span class="wage-label">奖金</span>
                <span class="wage-value" style="color: #67c23a">{{ fmt(detailData.bonus) }}</span>
              </div>
              <div class="wage-item">
                <span class="wage-label">扣款</span>
                <span class="wage-value" style="color: #f56c6c">-{{ fmt(detailData.deduction) }}</span>
              </div>
              <el-divider />
              <div class="wage-item total">
                <span class="wage-label">合计应发</span>
                <span class="wage-value" style="color: #409eff; font-size: 20px">{{ fmt(detailData.totalWage) }}</span>
              </div>
            </div>

            <div v-if="detailData.remark" style="margin-top: 16px">
              <div style="font-size: 13px; color: #909399; margin-bottom: 4px">备注</div>
              <div style="color: #606266">{{ detailData.remark }}</div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="报工明细" name="reports">
            <div style="margin-bottom: 8px; font-size: 13px; color: #909399">
              共 <strong style="color:#409eff">{{ reportRows.length }}</strong> 条报工记录，合格 <strong style="color:#67c23a">{{ reportTotals.qty.toFixed(2) }}</strong> 件，金额 <strong style="color:#e6a23c">¥{{ reportTotals.amount.toFixed(2) }}</strong>
            </div>
            <el-table :data="reportRows" v-loading="reportLoading" border stripe size="small" max-height="500">
              <el-table-column prop="reportTime" label="报工时间" width="140" />
              <el-table-column prop="woNo" label="工单号" width="140" show-overflow-tooltip />
              <el-table-column prop="materialName" label="物料" min-width="120" show-overflow-tooltip />
              <el-table-column prop="processName" label="工序" width="90" show-overflow-tooltip />
              <el-table-column prop="piecePrice" label="单价" width="70" align="right">
                <template #default="{ row }">{{ Number(row.piecePrice || 0).toFixed(2) }}</template>
              </el-table-column>
              <el-table-column prop="qualifiedQty" label="合格数" width="70" align="right" />
              <el-table-column prop="amount" label="金额" width="85" align="right">
                <template #default="{ row }">
                  <span style="color:#e6a23c">¥{{ Number(row.amount || 0).toFixed(2) }}</span>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-if="!reportLoading && !reportRows.length" description="暂无报工记录" :image-size="60" />
          </el-tab-pane>
        </el-tabs>
      </template>
    </el-drawer>

    <!-- 生成月度工资弹窗 -->
    <el-dialog v-model="generateDialogVisible" title="生成月度工资" width="450px" destroy-on-close>
      <el-alert type="info" :closable="false" style="margin-bottom: 16px">
        根据员工当月报工记录自动汇总计件工资、班组分配等，生成月度工资草稿。
      </el-alert>
      <el-form label-width="80px">
        <el-form-item label="月份">
          <el-date-picker v-model="generateMonth" type="month" placeholder="请选择" value-format="YYYY-MM" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="generateDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="generateLoading" :disabled="!generateMonth" @click="doGenerate">确认生成</el-button>
      </template>
    </el-dialog>

    <!-- 新增/编辑工资弹窗 -->
    <el-dialog v-model="createDialogVisible" :title="editingId ? '编辑工资' : '新增工资'" width="560px" destroy-on-close>
      <el-form ref="createFormRef" :model="createForm" :rules="createFormRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="月份" prop="month">
              <el-date-picker v-model="createForm.month" type="month" value-format="YYYY-MM" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="员工" prop="userId">
              <el-select v-model="createForm.userId" filterable placeholder="选择员工" style="width: 100%">
                <el-option v-for="u in userOptions" :key="u.id" :label="u.realName || u.username" :value="u.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="计件工资">
              <el-input-number v-model="createForm.personalPieceWage" :min="0" :precision="2" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="班组分配">
              <el-input-number v-model="createForm.teamAllocatedWage" :min="0" :precision="2" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="加班工资">
              <el-input-number v-model="createForm.overtimeWage" :min="0" :precision="2" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="奖金">
              <el-input-number v-model="createForm.bonus" :min="0" :precision="2" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="扣款">
              <el-input-number v-model="createForm.deduction" :min="0" :precision="2" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="出勤天数">
              <el-input-number v-model="createForm.workDays" :min="0" :max="31" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleCreateSubmit">确定</el-button>
      </template>
    </el-dialog>

    <ColumnConfigDialog ref="colConfigDialogRef" :all-columns="allColumns" :visible-keys="visibleColumns.map(c=>c.key)" @save="saveConfig" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Document, Clock, CircleCheck, Wallet, Download, Cpu, Setting } from '@element-plus/icons-vue'
import { getWagePage, createWage, confirmWage, approveWage, payWage, rejectWage, generateMonthlyWages, exportWages, batchConfirmWages, batchApproveWages, batchPayWages, getReportAnalysisList } from '@/api/finance'
import { getUserPage } from '@/api/system'
import { useColumnConfig } from '@/composables/useColumnConfig'
import ColumnConfigDialog from '@/components/ColumnConfigDialog.vue'

const FINANCE_WAGE_DEFAULT_COLUMNS = [
  { key: 'realName', label: '姓名', minWidth: 100 },
  { key: 'month', label: '月份', minWidth: 90, align: 'center' },
  { key: 'workDays', label: '出勤(天)', minWidth: 85, align: 'center' },
  { key: 'pieceWage', label: '计件工资', minWidth: 100, align: 'right' },
  { key: 'teamAllocatedWage', label: '班组分配', minWidth: 100, align: 'right' },
  { key: 'overtimeWage', label: '加班工资', minWidth: 100, align: 'right' },
  { key: 'bonus', label: '奖金', minWidth: 90, align: 'right' },
  { key: 'deduction', label: '扣款', minWidth: 90, align: 'right' },
  { key: 'totalWage', label: '合计', minWidth: 110, align: 'right' },
  { key: 'status', label: '状态', minWidth: 85, align: 'center' },
]
const colConfigDialogRef = ref(null)
const { visibleColumns, allColumns, loadColumnConfig, saveConfig } = useColumnConfig('finance_wage', FINANCE_WAGE_DEFAULT_COLUMNS)

const statusMap = { DRAFT: '草稿', CONFIRMED: '已确认', APPROVED: '已审批', PAID: '已发放' }
const statusTag = { DRAFT: 'info', CONFIRMED: 'warning', APPROVED: '', PAID: 'success' }
function fmt(v) { return v != null ? `¥${Number(v).toFixed(2)}` : '-' }

const statCards = [
  { key: 'count', label: '工资记录', color: '#409eff', icon: Document },
  { key: 'pending', label: '待确认', color: '#e6a23c', icon: Clock, filterStatus: 'DRAFT' },
  { key: 'confirmed', label: '已确认', color: '#67c23a', icon: CircleCheck, filterStatus: 'CONFIRMED' },
  { key: 'totalWage', label: '工资总额', color: '#409eff', icon: Wallet, prefix: '¥', isMoney: true }
]
const stats = reactive({ count: 0, pending: 0, confirmed: 0, totalWage: 0 })

function filterByStatus(status) { queryParams.status = status; queryParams.pageNum = 1; loadData() }

// 查询
const queryParams = reactive({ month: '', realName: '', status: '', pageNum: 1, pageSize: 20 })
const loading = ref(false); const tableData = ref([]); const total = ref(0); const submitLoading = ref(false)
const selectedIds = ref([]); const userOptions = ref([])

async function loadData() {
  loading.value = true
  try {
    const res = await getWagePage({
      month: queryParams.month || undefined, realName: queryParams.realName || undefined,
      status: queryParams.status || undefined, pageNum: queryParams.pageNum, pageSize: queryParams.pageSize
    })
    tableData.value = res.data?.records || []; total.value = res.data?.total || 0
    stats.count = total.value
    stats.pending = tableData.value.filter(r => r.status === 'DRAFT').length
    stats.confirmed = tableData.value.filter(r => r.status === 'CONFIRMED').length
    stats.totalWage = tableData.value.reduce((s, r) => s + Number(r.totalWage || 0), 0)
  } catch {} finally { loading.value = false }
}

function handleSearch() { queryParams.pageNum = 1; loadData() }
function handleReset() { queryParams.month = ''; queryParams.realName = ''; queryParams.status = ''; queryParams.pageNum = 1; loadData() }
function onSelectionChange(rows) { selectedIds.value = rows.map(r => r.id) }

// 合计行
function getSummary({ columns, data }) {
  const sums = []
  columns.forEach((col, i) => {
    if (i === 0) { sums[i] = ''; return }
    if (i === 1) { sums[i] = '合计'; return }
    const prop = col.property
    if (['pieceWage', 'teamAllocatedWage', 'overtimeWage', 'bonus', 'deduction', 'totalWage'].includes(prop)) {
      sums[i] = `¥${data.reduce((s, r) => s + Number(r[prop] || 0), 0).toFixed(2)}`
    } else { sums[i] = '' }
  })
  return sums
}

// 详情
const detailVisible = ref(false); const detailData = ref(null)
const detailTab = ref('breakdown')
const reportRows = ref([])
const reportLoading = ref(false)
const reportTotals = reactive({ qty: 0, amount: 0 })

async function loadReportDetails(row) {
  if (!row?.realName || !row?.month) { reportRows.value = []; return }
  const [y, m] = row.month.split('-')
  const lastDay = new Date(Number(y), Number(m), 0).getDate()
  reportLoading.value = true
  try {
    const res = await getReportAnalysisList({
      startDate: `${row.month}-01`,
      endDate: `${row.month}-${String(lastDay).padStart(2, '0')}`,
      employeeName: row.realName,
      pageNum: 1,
      pageSize: 1000
    })
    reportRows.value = res.data?.records || []
    reportTotals.qty = reportRows.value.reduce((s, r) => s + Number(r.qualifiedQty || 0), 0)
    reportTotals.amount = reportRows.value.reduce((s, r) => s + Number(r.amount || 0), 0)
  } catch {
    reportRows.value = []; reportTotals.qty = 0; reportTotals.amount = 0
  } finally { reportLoading.value = false }
}

function openDetail(row) {
  detailData.value = row
  detailTab.value = 'breakdown'
  reportRows.value = []
  reportTotals.qty = 0; reportTotals.amount = 0
  detailVisible.value = true
  loadReportDetails(row)
}

// 生成月度工资
const generateDialogVisible = ref(false); const generateMonth = ref(''); const generateLoading = ref(false)
function openGenerateDialog() {
  const now = new Date()
  generateMonth.value = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
  generateDialogVisible.value = true
}
async function doGenerate() {
  if (!generateMonth.value) return
  generateLoading.value = true
  try {
    await generateMonthlyWages(generateMonth.value)
    ElMessage.success('月度工资生成成功'); generateDialogVisible.value = false; loadData()
  } catch { ElMessage.error('生成失败，请重试') } finally { generateLoading.value = false }
}

// 导出
async function handleExport() {
  try {
    const res = await exportWages({ month: queryParams.month || undefined })
    const blob = new Blob([res], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url; link.download = `工资表_${queryParams.month || '全部'}.xlsx`; link.click()
    window.URL.revokeObjectURL(url); ElMessage.success('导出成功')
  } catch {}
}

// 新增/编辑
const createDialogVisible = ref(false); const createFormRef = ref(null); const editingId = ref(null)
const createForm = reactive({
  month: '', userId: null, personalPieceWage: null, teamAllocatedWage: null,
  overtimeWage: null, deduction: null, bonus: null, workDays: null, remark: ''
})
const createFormRules = {
  month: [{ required: true, message: '请选择月份', trigger: 'change' }],
  userId: [{ required: true, message: '请选择员工', trigger: 'change' }]
}

function openCreateDialog() {
  editingId.value = null
  Object.assign(createForm, {
    month: '', userId: null, personalPieceWage: null, teamAllocatedWage: null,
    overtimeWage: null, deduction: null, bonus: null, workDays: null, remark: ''
  })
  createDialogVisible.value = true
}
function openEdit(row) {
  editingId.value = row.id
  Object.assign(createForm, {
    month: row.month, userId: row.userId,
    personalPieceWage: row.personalPieceWage, teamAllocatedWage: row.teamAllocatedWage,
    overtimeWage: row.overtimeWage, deduction: row.deduction, bonus: row.bonus,
    workDays: row.workDays, remark: row.remark
  })
  createDialogVisible.value = true
}

async function handleCreateSubmit() {
  await createFormRef.value.validate()
  submitLoading.value = true
  try {
    await createWage({ ...createForm, id: editingId.value })
    ElMessage.success(editingId.value ? '更新成功' : '新增成功')
    createDialogVisible.value = false; loadData()
  } finally { submitLoading.value = false }
}

// 操作
async function handleConfirm(row) {
  await ElMessageBox.confirm('确认该工资记录？', '提示')
  await confirmWage(row.id); ElMessage.success('确认成功'); loadData()
}
async function handleApprove(row) {
  await ElMessageBox.confirm('审批通过？', '提示')
  await approveWage(row.id); ElMessage.success('审批成功'); loadData()
}
async function handlePay(row) {
  await ElMessageBox.confirm('确认发放？', '提示')
  await payWage(row.id); ElMessage.success('发放成功'); loadData()
}
async function handleReject(row) {
  await ElMessageBox.confirm('退回该记录？', '提示')
  await rejectWage(row.id); ElMessage.success('已退回'); loadData()
}

// 批量操作
async function handleBatchConfirm() {
  await ElMessageBox.confirm(`批量确认 ${selectedIds.value.length} 条记录？`, '提示')
  try { await batchConfirmWages(selectedIds.value); ElMessage.success('批量确认成功'); loadData() } catch {}
}
async function handleBatchApprove() {
  await ElMessageBox.confirm(`批量审批 ${selectedIds.value.length} 条记录？`, '提示')
  try { await batchApproveWages(selectedIds.value); ElMessage.success('批量审批成功'); loadData() } catch {}
}
async function handleBatchPay() {
  await ElMessageBox.confirm(`批量发放 ${selectedIds.value.length} 条记录？`, '提示')
  try { await batchPayWages(selectedIds.value); ElMessage.success('批量发放成功'); loadData() } catch {}
}

async function loadUserOptions() {
  try {
    const res = await getUserPage({ pageNum: 1, pageSize: 200 })
    userOptions.value = res.data?.records || res.data || []
  } catch {}
}

onMounted(() => { loadData(); loadUserOptions(); loadColumnConfig() })
</script>

<style scoped lang="scss">
.page-container { padding: 16px; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; }
.stat-card {
  background: #fff; border-radius: 8px; padding: 16px 20px;
  display: flex; align-items: center; gap: 12px;
  box-shadow: 0 1px 4px rgba(0,0,0,.08); cursor: pointer; transition: box-shadow .3s;
  &:hover { box-shadow: 0 4px 12px rgba(0,0,0,.15); }
  .icon { font-size: 36px; }
  .info .label { font-size: 13px; color: #909399; }
  .info .value { font-size: 24px; font-weight: 600; margin-top: 4px; }
}
.wage-breakdown {
  .wage-item {
    display: flex; justify-content: space-between; padding: 8px 0;
    border-bottom: 1px dashed #f0f0f0;
    .wage-label { color: #606266; }
    .wage-label.sub { color: #909399; font-size: 13px; }
    .wage-value { font-weight: 500; }
    &.total { border-bottom: none; padding-top: 12px; .wage-label { font-weight: 600; font-size: 15px; } }
  }
}
</style>
