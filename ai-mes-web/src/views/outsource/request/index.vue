<template>
  <div class="page-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="关键词">
          <el-input
            v-model="queryParams.keyword"
            placeholder="申请编号"
            clearable
            style="width: 160px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="工单编号">
          <el-input
            v-model="queryParams.workOrderNo"
            placeholder="工单编号"
            clearable
            style="width: 160px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="工序名称">
          <el-input
            v-model="queryParams.stepName"
            placeholder="工序名称"
            clearable
            style="width: 140px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="产品">
          <el-select v-model="queryParams.productId" placeholder="全部" clearable filterable remote
                     :remote-method="searchProducts" :loading="productSearching" style="width: 180px">
            <el-option v-for="item in productOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="待审批" value="pending" />
            <el-option label="已审批" value="approved" />
            <el-option label="已驳回" value="rejected" />
            <el-option label="已下单" value="ordered" />
            <el-option label="已完成" value="completed" />
            <el-option label="已取消" value="cancelled" />
          </el-select>
        </el-form-item>
        <el-form-item label="需求日期">
          <el-date-picker
            v-model="queryParams.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 240px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="primary" plain @click="handleAdd">新增申请</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据表格 -->
    <el-card shadow="never" class="table-card">
      <div style="display: flex; justify-content: flex-end; margin-bottom: 10px">
        <el-button :icon="Setting" @click="colConfigDialogRef.open()">列配置</el-button>
      </div>
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column v-for="col in visibleColumns" :key="col.key"
          :label="col.label" :min-width="col.minWidth || 100" :align="col.align || 'left'" show-overflow-tooltip>
          <template #default="{ row }">
            <template v-if="col.key === 'status'">
              <el-tag :type="statusTagType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
            </template>
            <template v-else>{{ row[col.key] || '-' }}</template>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleView(row)">查看</el-button>
            <el-button v-if="row.status === 'pending'" type="success" link size="small" @click="handleApprove(row)">审批</el-button>
            <el-button v-if="row.status === 'pending'" type="danger" link size="small" @click="handleReject(row)">驳回</el-button>
            <el-button v-if="row.status === 'pending'" type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          background
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- 新增弹窗 -->
    <el-dialog v-model="dialogVisible" title="新增外协申请" width="560px" destroy-on-close @close="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="工单ID" prop="workOrderId">
          <el-input v-model="form.workOrderId" placeholder="请输入工单ID" />
        </el-form-item>
        <el-form-item label="任务ID" prop="taskId">
          <el-input v-model="form.taskId" placeholder="请输入任务ID" />
        </el-form-item>
        <el-form-item label="数量" prop="quantity">
          <el-input-number v-model="form.quantity" :min="1" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="加工要求" prop="processRequirement">
          <el-input v-model="form.processRequirement" type="textarea" :rows="3" placeholder="请输入加工要求" />
        </el-form-item>
        <el-form-item label="需求日期" prop="requiredDate">
          <el-date-picker
            v-model="form.requiredDate"
            type="date"
            placeholder="请选择需求日期"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <ColumnConfigDialog ref="colConfigDialogRef" :all-columns="allColumns" :visible-keys="visibleColumns.map(c=>c.key)" @save="saveConfig" />
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted } from 'vue'
import { Setting } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getOutsourceRequestPage,
  createOutsourceRequest,
  approveOutsourceRequest,
  rejectOutsourceRequest
} from '@/api/outsource'
import { getMaterialPage } from '@/api/master'
import { useAiChatStore } from '@/store/ai-chat'
import { useColumnConfig } from '@/composables/useColumnConfig'
import ColumnConfigDialog from '@/components/ColumnConfigDialog.vue'

// 状态映射（后端使用小写状态值）
import { outsourceRequestStatusMap, getStatusInfo } from '@/utils/status'

const OUTSOURCE_REQUEST_DEFAULT_COLUMNS = [
  { key: 'requestNo', label: '申请编号', minWidth: 160 },
  { key: 'workOrderNo', label: '工单编号', minWidth: 160 },
  { key: 'stepName', label: '工序名称', minWidth: 120 },
  { key: 'productName', label: '产品名称', minWidth: 140 },
  { key: 'quantity', label: '数量', minWidth: 100, align: 'right' },
  { key: 'requiredDate', label: '需求日期', minWidth: 120 },
  { key: 'status', label: '状态', minWidth: 100, align: 'center' },
]
const colConfigDialogRef = ref(null)
const { visibleColumns, allColumns, loadColumnConfig, saveConfig } = useColumnConfig('outsource_request', OUTSOURCE_REQUEST_DEFAULT_COLUMNS)

const aiChatStore = useAiChatStore()

// AI填入表单
watch(() => aiChatStore.pendingFillData, (data) => {
  if (data?.module === 'outsource' && data.records?.length) {
    const defaults = getDefaultForm()
    Object.keys(defaults).forEach(k => { form[k] = defaults[k] })
    Object.keys(form).forEach(k => { if (data.records[0][k] !== undefined) form[k] = data.records[0][k] })
    dialogVisible.value = true
    aiChatStore.clearFillData()
    ElMessage.success('AI已填入外协申请表单，请确认后提交')
  }
})
import { formatDateTime } from '@/utils/format'

function statusTagType(status) {
  return getStatusInfo(outsourceRequestStatusMap, status).type
}
function statusText(status) {
  return getStatusInfo(outsourceRequestStatusMap, status).label
}

// 产品下拉
const productOptions = ref([])

// 查询参数
const queryParams = reactive({
  keyword: '',
  status: undefined,
  workOrderNo: '',
  stepName: '',
  productId: undefined,
  dateRange: null,
  pageNum: 1,
  pageSize: 10
})

// 表格数据
const loading = ref(false)
const tableData = ref([])
const total = ref(0)

// 弹窗相关
const dialogVisible = ref(false)
const formRef = ref(null)
const submitLoading = ref(false)

const getDefaultForm = () => ({
  workOrderId: '',
  taskId: '',
  quantity: 1,
  processRequirement: '',
  requiredDate: ''
})

const form = reactive(getDefaultForm())

const rules = {
  workOrderId: [{ required: true, message: '请输入工单ID', trigger: 'blur' }],
  taskId: [{ required: true, message: '请输入任务ID', trigger: 'blur' }],
  quantity: [{ required: true, message: '请输入数量', trigger: 'blur' }],
  requiredDate: [{ required: true, message: '请选择需求日期', trigger: 'change' }]
}

// 加载数据
async function loadData() {
  loading.value = true
  try {
    const params = {
      keyword: queryParams.keyword,
      status: queryParams.status,
      workOrderNo: queryParams.workOrderNo,
      stepName: queryParams.stepName,
      productId: queryParams.productId,
      startDate: queryParams.dateRange?.[0] || undefined,
      endDate: queryParams.dateRange?.[1] || undefined,
      pageNum: queryParams.pageNum,
      pageSize: queryParams.pageSize
    }
    const res = await getOutsourceRequestPage(params)
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } catch {
    // handled by interceptor
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  queryParams.pageNum = 1
  loadData()
}

function handleReset() {
  queryParams.keyword = ''
  queryParams.status = undefined
  queryParams.workOrderNo = ''
  queryParams.stepName = ''
  queryParams.productId = undefined
  queryParams.dateRange = null
  queryParams.pageNum = 1
  loadData()
}

function resetForm() {
  const defaults = getDefaultForm()
  Object.keys(defaults).forEach(key => {
    form[key] = defaults[key]
  })
}

function handleAdd() {
  resetForm()
  dialogVisible.value = true
}

async function handleSubmit() {
  await formRef.value.validate()
  submitLoading.value = true
  try {
    await createOutsourceRequest({ ...form })
    ElMessage.success('外协申请创建成功')
    dialogVisible.value = false
    loadData()
  } catch {
    // handled by interceptor
  } finally {
    submitLoading.value = false
  }
}

function handleView(row) {
  ElMessageBox.alert(
    `<div style="line-height: 2">
      <div><b>申请编号:</b> ${row.requestNo}</div>
      <div><b>工单编号:</b> ${row.workOrderNo || '-'}</div>
      <div><b>工序名称:</b> ${row.stepName || '-'}</div>
      <div><b>产品名称:</b> ${row.productName || '-'}</div>
      <div><b>数量:</b> ${row.quantity}</div>
      <div><b>加工要求:</b> ${row.processRequirement || '-'}</div>
      <div><b>需求日期:</b> ${row.requiredDate || '-'}</div>
      <div><b>状态:</b> ${statusText(row.status)}</div>
      <div><b>审批备注:</b> ${row.approveRemark || '-'}</div>
    </div>`,
    '外协申请详情',
    { dangerouslyUseHTMLString: true, confirmButtonText: '关闭' }
  )
}

function handleDelete(row) {
  ElMessageBox.confirm(`确定删除申请「${row.requestNo}」吗？`, '提示', { type: 'warning' })
    .then(async () => {
      try {
        await rejectOutsourceRequest(row.id) // 复用reject接口或后续增加delete接口
        ElMessage.success('已删除')
        loadData()
      } catch {
        // handled by interceptor
      }
    })
    .catch(() => {})
}

function handleApprove(row) {
  ElMessageBox.confirm(`确定审批通过申请「${row.requestNo}」吗？`, '提示', { type: 'warning' })
    .then(async () => {
      try {
        await approveOutsourceRequest(row.id)
        ElMessage.success('审批通过')
        loadData()
      } catch {
        // handled by interceptor
      }
    })
    .catch(() => {})
}

function handleReject(row) {
  ElMessageBox.confirm(`确定驳回申请「${row.requestNo}」吗？`, '提示', { type: 'warning' })
    .then(async () => {
      try {
        await rejectOutsourceRequest(row.id)
        ElMessage.success('已驳回')
        loadData()
      } catch {
        // handled by interceptor
      }
    })
    .catch(() => {})
}

async function loadOptions() {
  // 产品下拉改 remote 按需搜索,首屏不加载
}

const productSearching = ref(false)
let _prodSearchTimer = null
async function searchProducts(keyword) {
  if (_prodSearchTimer) clearTimeout(_prodSearchTimer)
  if (!keyword || !keyword.trim()) { productOptions.value = []; return }
  _prodSearchTimer = setTimeout(async () => {
    productSearching.value = true
    try {
      const res = await getMaterialPage({ materialName: keyword.trim(), pageNum: 1, pageSize: 30 })
      productOptions.value = (res.data?.records || []).map(m => ({ id: m.id, name: m.materialName }))
    } catch {} finally { productSearching.value = false }
  }, 300)
}

onMounted(() => {
  loadOptions()
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
.search-card {
  :deep(.el-card__body) {
    padding-bottom: 2px;
  }
}
.table-card {
  flex: 1;
}
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.text-muted {
  color: #c0c4cc;
}
</style>
