<template>
  <div class="page-container">
    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-form :model="queryParams" inline>
        <el-form-item label="客户名称">
          <el-input v-model="queryParams.customerName" placeholder="客户名称" clearable style="width: 150px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="联系人">
          <el-input v-model="queryParams.contactName" placeholder="联系人" clearable style="width: 120px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable>
            <el-option label="启用" value="active" />
            <el-option label="停用" value="inactive" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="info" plain @click="advancedQueryVisible = true">复杂查询</el-button>
          <el-button v-permission="'order:customer:add'" type="primary" plain @click="handleAdd">新增</el-button>
          <el-button type="success" :icon="Download" @click="handleExport">导出</el-button>
          <el-button type="warning" :icon="Upload" @click="importDialogVisible = true">导入</el-button>
          <el-button :icon="Setting" @click="colConfigDialogRef.open()">列配置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 批量操作栏 -->
    <div v-if="selectedIds.length > 0" class="batch-bar">
      <span>已选择 {{ selectedIds.length }} 项</span>
      <el-button v-permission="'order:customer:delete'" type="danger" size="small" @click="handleBatchDelete">批量删除</el-button>
      <el-button type="warning" size="small" @click="handleBatchStatus('inactive')">批量停用</el-button>
      <el-button type="success" size="small" @click="handleBatchStatus('active')">批量启用</el-button>
      <el-button size="small" @click="clearSelection">取消选择</el-button>
    </div>

    <!-- 数据表格 -->
    <el-table ref="tableRef" :data="tableData" v-loading="loading" border stripe
      @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="50" />
      <el-table-column
        v-for="col in visibleColumns" :key="col.key"
        :label="col.label"
        :min-width="col.minWidth || 100"
        :width="col.width"
        :align="col.align || 'left'"
        show-overflow-tooltip>
        <template #default="{ row }">
          <template v-if="col.key === 'customerName'">
            <el-button type="primary" link @click="goToDetail(row.id)">{{ row.customerName }}</el-button>
          </template>
          <template v-else-if="col.key === 'settlementType'">{{ row.settlementType || '-' }}</template>
          <template v-else-if="col.key === 'creditLimit'">{{ row.creditLimit != null ? Number(row.creditLimit).toFixed(2) : '-' }}</template>
          <template v-else-if="col.key === 'receivableBalance'">
            <span style="color: #f56c6c; font-weight: 500">¥{{ row.receivableBalance != null ? Number(row.receivableBalance).toFixed(2) : '0.00' }}</span>
          </template>
          <template v-else-if="col.key === 'status'">
            <el-tag :type="row.status === 'active' ? 'success' : 'danger'" size="small">
              {{ row.status === 'active' ? '启用' : '停用' }}
            </el-tag>
          </template>
          <template v-else-if="col.key === 'createTime'">{{ $fmtDate ? $fmtDate(row.createTime) : row.createTime }}</template>
          <template v-else>{{ row[col.key] || '-' }}</template>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right" align="center">
        <template #default="{ row }">
          <el-button v-permission="'order:customer:edit'" type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button :type="row.status === 'active' ? 'danger' : 'success'" link size="small" @click="handleToggleStatus(row)">
            {{ row.status === 'active' ? '停用' : '启用' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination-wrapper">
      <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
        :page-sizes="[10, 20, 50, 100]" :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadData" @current-change="loadData" />
    </div>

    <!-- ========== 复杂查询抽屉 ========== -->
    <el-drawer v-model="advancedQueryVisible" title="复杂查询" direction="rtl" size="400px">
      <el-form :model="advancedQuery" label-width="80px" label-position="top">
        <el-form-item label="客户编号">
          <el-input v-model="advancedQuery.customerNo" placeholder="支持模糊搜索" clearable />
        </el-form-item>
        <el-form-item label="联系人">
          <el-input v-model="advancedQuery.contactName" placeholder="支持模糊搜索" clearable />
        </el-form-item>
        <el-form-item label="电话">
          <el-input v-model="advancedQuery.phone" clearable />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="advancedQuery.email" clearable />
        </el-form-item>
        <el-form-item label="地址">
          <el-input v-model="advancedQuery.address" clearable />
        </el-form-item>
        <el-form-item label="结算方式">
          <el-select v-model="advancedQuery.settlementType" placeholder="全部" clearable filterable
            style="width: 100%">
            <el-option v-for="t in settlementTypeOptions" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="信用额度范围">
          <div style="display: flex; gap: 8px; align-items: center">
            <el-input-number v-model="advancedQuery.creditLimitMin" :min="0" :precision="2" controls-position="right" style="flex: 1" placeholder="最小" />
            <span>-</span>
            <el-input-number v-model="advancedQuery.creditLimitMax" :min="0" :precision="2" controls-position="right" style="flex: 1" placeholder="最大" />
          </div>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="advancedQuery.status" placeholder="全部" clearable style="width: 100%">
            <el-option label="启用" value="active" />
            <el-option label="停用" value="inactive" />
          </el-select>
        </el-form-item>
        <el-form-item label="创建时间范围">
          <el-date-picker v-model="advancedQuery.createTimeRange" type="daterange"
            range-separator="至" start-placeholder="开始" end-placeholder="结束"
            value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="创建人">
          <el-input v-model="advancedQuery.createByName" placeholder="创建人姓名" clearable />
        </el-form-item>
        <div style="display: flex; gap: 8px; justify-content: flex-end; margin-top: 16px">
          <el-button @click="resetAdvancedQuery">重置</el-button>
          <el-button type="primary" @click="applyAdvancedQuery">查询</el-button>
        </div>
      </el-form>
    </el-drawer>

    <!-- 新增/编辑弹窗 -->
    <el-dialog :title="dialogTitle" v-model="dialogVisible" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="客户名称" prop="customerName">
          <el-input v-model="form.customerName" placeholder="请输入客户名称" />
        </el-form-item>
        <el-form-item label="联系人">
          <el-input v-model="form.contactName" placeholder="请输入联系人" />
        </el-form-item>
        <el-form-item label="电话">
          <el-input v-model="form.phone" placeholder="请输入电话" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="地址">
          <el-input v-model="form.address" placeholder="请输入地址" />
        </el-form-item>
        <el-form-item label="结算方式" prop="settlementType">
          <el-select v-model="form.settlementType" placeholder="请选择或输入新结算方式" filterable allow-create
            default-first-option style="width: 100%">
            <el-option v-for="t in settlementTypeOptions" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="信用额度">
          <el-input-number v-model="form.creditLimit" :min="0" :precision="2" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 导入弹窗 -->
    <el-dialog v-model="importDialogVisible" title="导入客户" width="500px">
      <div style="margin-bottom: 16px;">
        <el-button type="primary" link @click="handleDownloadTemplate">下载导入模板</el-button>
      </div>
      <el-upload ref="uploadRef" :auto-upload="false" :limit="1" accept=".xlsx,.xls" :on-change="handleFileChange">
        <template #trigger><el-button type="primary">选择文件</el-button></template>
        <template #tip><div class="el-upload__tip">只能上传 xlsx/xls 文件</div></template>
      </el-upload>
      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="importLoading" @click="handleImport">确认导入</el-button>
      </template>
    </el-dialog>

    <ColumnConfigDialog
      ref="colConfigDialogRef"
      :all-columns="allColumns"
      :default-columns="ORDER_CUSTOMER_DEFAULT_COLUMNS"
      @save="saveConfig"
    />
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, Upload, Setting } from '@element-plus/icons-vue'
import { useColumnConfig } from '@/composables/useColumnConfig'
import ColumnConfigDialog from '@/components/ColumnConfigDialog.vue'
import {
  getCustomerPage, createCustomer, updateCustomer, updateCustomerStatus,
  exportCustomers,
  batchDeleteCustomers, batchUpdateCustomerStatus
} from '@/api/master'
import { downloadTemplate, importData } from '@/api/data-import'
import { getSettlementTypes, saveSettlementTypes } from '@/api/system'

import { useAiChatStore } from '@/store/ai-chat'

const router = useRouter()
const aiChatStore = useAiChatStore()

const ORDER_CUSTOMER_DEFAULT_COLUMNS = [
  { key: 'customerNo', label: '客户编号', minWidth: 120 },
  { key: 'customerName', label: '客户名称', minWidth: 160 },
  { key: 'contactName', label: '联系人', minWidth: 100 },
  { key: 'phone', label: '电话', minWidth: 120 },
  { key: 'settlementType', label: '结算方式', minWidth: 110 },
  { key: 'creditLimit', label: '信用额度', minWidth: 110, align: 'right' },
  { key: 'receivableBalance', label: '应收余额', minWidth: 120, align: 'right' },
  { key: 'createByName', label: '创建人', minWidth: 100 },
  { key: 'status', label: '状态', minWidth: 90, align: 'center' },
  { key: 'createTime', label: '创建时间', minWidth: 170 },
]
const colConfigDialogRef = ref(null)
const { visibleColumns, allColumns, loadColumnConfig, saveConfig } = useColumnConfig('order_customer', ORDER_CUSTOMER_DEFAULT_COLUMNS)

const settlementTypeOptions = ref(['现结', '月结30天', '月结60天', '月结90天'])

async function loadSettlementTypes() {
  try {
    const res = await getSettlementTypes()
    if (res.data?.configValue) {
      settlementTypeOptions.value = JSON.parse(res.data.configValue)
    }
  } catch {}
}

async function addSettlementType(val) {
  if (val && !settlementTypeOptions.value.includes(val)) {
    settlementTypeOptions.value.push(val)
    try { await saveSettlementTypes(settlementTypeOptions.value) } catch {}
  }
}

// AI填入表单
watch(() => aiChatStore.pendingFillData, (data) => {
  if (data?.module === 'customer' && data.records?.length) {
    dialogTitle.value = '新增客户'; editingId.value = null; resetForm()
    Object.keys(form).forEach(k => { if (data.records[0][k] !== undefined) form[k] = data.records[0][k] })
    dialogVisible.value = true
    aiChatStore.clearFillData()
    ElMessage.success('AI已填入客户表单，请确认后提交')
  }
})

// ==================== 查询 ====================
const queryParams = reactive({ customerName: '', contactName: '', status: undefined, pageNum: 1, pageSize: 20 })
const loading = ref(false)
const tableData = ref([])
const total = ref(0)

// 复杂查询
const advancedQueryVisible = ref(false)
const advancedQuery = reactive({
  customerNo: '', contactName: '', phone: '', email: '', address: '',
  settlementType: undefined, creditLimitMin: undefined, creditLimitMax: undefined,
  status: undefined, createTimeRange: null, createByName: ''
})

async function loadData() {
  loading.value = true
  try {
    const params = { ...queryParams }
    // Merge advanced query params
    if (advancedQuery.customerNo) params.customerNo = advancedQuery.customerNo
    if (advancedQuery.contactName) params.contactName = advancedQuery.contactName
    if (advancedQuery.phone) params.phone = advancedQuery.phone
    if (advancedQuery.email) params.email = advancedQuery.email
    if (advancedQuery.address) params.address = advancedQuery.address
    if (advancedQuery.settlementType) params.settlementType = advancedQuery.settlementType
    if (advancedQuery.creditLimitMin != null) params.creditLimitMin = advancedQuery.creditLimitMin
    if (advancedQuery.creditLimitMax != null) params.creditLimitMax = advancedQuery.creditLimitMax
    if (advancedQuery.status) params.status = advancedQuery.status
    if (advancedQuery.createTimeRange?.length === 2) {
      params.createTimeStart = advancedQuery.createTimeRange[0]
      params.createTimeEnd = advancedQuery.createTimeRange[1]
    }
    if (advancedQuery.createByName) params.createByName = advancedQuery.createByName
    const res = await getCustomerPage(params)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch {} finally { loading.value = false }
}

function handleSearch() { queryParams.pageNum = 1; loadData() }
function handleReset() {
  queryParams.customerName = ''; queryParams.contactName = ''; queryParams.status = undefined; queryParams.pageNum = 1
  resetAdvancedQuery(); loadData()
}
function resetAdvancedQuery() {
  advancedQuery.customerNo = ''; advancedQuery.contactName = ''; advancedQuery.phone = ''
  advancedQuery.email = ''; advancedQuery.address = ''; advancedQuery.settlementType = undefined
  advancedQuery.creditLimitMin = undefined; advancedQuery.creditLimitMax = undefined
  advancedQuery.status = undefined; advancedQuery.createTimeRange = null; advancedQuery.createByName = ''
}
function applyAdvancedQuery() {
  queryParams.pageNum = 1; advancedQueryVisible.value = false; loadData()
}

// ==================== 批量操作 ====================
const tableRef = ref(null)
const selectedIds = ref([])

function handleSelectionChange(rows) {
  selectedIds.value = rows.map(r => r.id)
}
function clearSelection() {
  tableRef.value?.clearSelection()
  selectedIds.value = []
}

async function handleBatchDelete() {
  try {
    await ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 个客户？此操作不可恢复。`, '提示', { type: 'warning' })
    await batchDeleteCustomers(selectedIds.value)
    ElMessage.success('批量删除成功'); clearSelection(); loadData()
  } catch {}
}

async function handleBatchStatus(status) {
  const label = status === 'active' ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(`确认${label}选中的 ${selectedIds.value.length} 个客户？`, '提示', { type: 'warning' })
    await batchUpdateCustomerStatus(selectedIds.value, status)
    ElMessage.success(`批量${label}成功`); clearSelection(); loadData()
  } catch {}
}

// ==================== 弹窗 ====================
const dialogVisible = ref(false)
const dialogTitle = ref('新增客户')
const formRef = ref(null)
const submitLoading = ref(false)
const editingId = ref(null)

const getDefaultForm = () => ({
  customerName: '', contactName: '', phone: '', email: '', address: '',
  settlementType: undefined, creditLimit: undefined, remark: ''
})
const form = reactive(getDefaultForm())
const rules = {
  customerName: [{ required: true, message: '请输入客户名称', trigger: 'blur' }],
  settlementType: [{ required: true, message: '请选择结算方式', trigger: 'change' }]
}

function resetForm() { const d = getDefaultForm(); Object.keys(d).forEach(k => { form[k] = d[k] }) }
function handleAdd() { resetForm(); editingId.value = null; dialogTitle.value = '新增客户'; dialogVisible.value = true }
function handleEdit(row) {
  resetForm(); editingId.value = row.id; dialogTitle.value = '编辑客户'
  Object.keys(form).forEach(k => { if (row[k] !== undefined) form[k] = row[k] })
  dialogVisible.value = true
}

async function handleSubmit() {
  await formRef.value.validate(); submitLoading.value = true
  try {
    if (editingId.value) { await updateCustomer(editingId.value, { ...form }); ElMessage.success('更新成功') }
    else { await createCustomer({ ...form }); ElMessage.success('新增成功') }
    // 如果输入了新的结算方式，自动保存到选项列表
    if (form.settlementType) addSettlementType(form.settlementType)
    dialogVisible.value = false; loadData()
  } catch {} finally { submitLoading.value = false }
}

async function handleToggleStatus(row) {
  const newStatus = row.status === 'active' ? 'inactive' : 'active'
  const action = newStatus === 'active' ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(`确认${action}客户「${row.customerName}」吗？`, '提示', { type: 'warning' })
    await updateCustomerStatus(row.id, newStatus)
    ElMessage.success(`${action}成功`); loadData()
  } catch {}
}

// ==================== 导入导出 ====================
const importDialogVisible = ref(false)
const importLoading = ref(false)
const importFile = ref(null)
const uploadRef = ref(null)

function handleFileChange(file) { importFile.value = file.raw }
function downloadBlob(data, filename) {
  const blob = new Blob([data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
  const url = window.URL.createObjectURL(blob); const link = document.createElement('a')
  link.href = url; link.download = filename; link.click(); window.URL.revokeObjectURL(url)
}
async function handleExport() {
  try { const res = await exportCustomers(); downloadBlob(res, '客户列表.xlsx'); ElMessage.success('导出成功') } catch {}
}
async function handleDownloadTemplate() {
  try {
    const res = await downloadTemplate('customer')
    downloadBlob(res, '客户导入模板.xlsx')
  } catch { ElMessage.error('下载模板失败') }
}
async function handleImport() {
  if (!importFile.value) { ElMessage.warning('请先选择文件'); return }
  importLoading.value = true
  try {
    const resp = await importData('customer', importFile.value)
    const data = resp.data || resp
    if (!data?.failed) ElMessage.success(`导入成功 ${data?.success || 0} 条`)
    else ElMessage.warning(`成功 ${data.success || 0} 条,失败 ${data.failed} 条`)
    importDialogVisible.value = false; importFile.value = null; loadData()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '导入失败')
  } finally { importLoading.value = false }
}

function goToDetail(id) { router.push({ path: '/order/customer/detail', query: { id } }) }

onMounted(() => { loadData(); loadSettlementTypes(); loadColumnConfig() })
</script>

<style scoped lang="scss">
.page-container { padding: 20px; }
.search-bar { margin-bottom: 16px; }
.batch-bar {
  display: flex; align-items: center; gap: 8px; padding: 8px 12px;
  background: #ecf5ff; border-radius: 4px; margin-bottom: 12px; font-size: 13px;
}
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
