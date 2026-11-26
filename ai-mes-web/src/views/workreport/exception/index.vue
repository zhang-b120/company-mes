<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="异常类型">
          <el-select v-model="queryParams.exceptionType" placeholder="全部" clearable>
            <el-option v-for="item in exceptionTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable>
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格区域 -->
    <el-card shadow="never" class="table-card">
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="exceptionType" label="异常类型" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="exceptionTypeTagType(row.exceptionType)" size="small">
              {{ exceptionTypeLabel(row.exceptionType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="异常描述" min-width="220" show-overflow-tooltip />
        <el-table-column prop="urgency" label="紧急程度" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="urgencyTagType(row.urgency)" size="small">{{ urgencyLabel(row.urgency) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reporterName" label="上报人" width="100" />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="handlerName" label="处理人" width="100" />
        <el-table-column prop="createTime" label="创建时间" min-width="170">
          <template #default="{ row }">{{ $fmtDate(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              v-if="row.status === 'pending' || row.status === 'processing'"
              @click="handleProcess(row)"
            >处理</el-button>
            <el-button link type="info" v-else @click="handleViewResult(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- 处理对话框 -->
    <el-dialog v-model="handleVisible" title="异常处理" width="520px" destroy-on-close>
      <el-descriptions :column="1" border size="small" style="margin-bottom: 20px">
        <el-descriptions-item label="异常类型">
          <el-tag :type="exceptionTypeTagType(currentRow?.exceptionType)" size="small">
            {{ exceptionTypeLabel(currentRow?.exceptionType) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="异常描述">{{ currentRow?.description }}</el-descriptions-item>
        <el-descriptions-item label="紧急程度">
          <el-tag :type="urgencyTagType(currentRow?.urgency)" size="small">{{ urgencyLabel(currentRow?.urgency) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="上报人">{{ currentRow?.reporterName }}</el-descriptions-item>
      </el-descriptions>
      <el-form :model="handleForm" :rules="handleRules" ref="handleFormRef" label-width="90px">
        <el-form-item label="处理结果" prop="result">
          <el-input v-model="handleForm.result" type="textarea" :rows="4" placeholder="请输入处理结果" />
        </el-form-item>
        <el-form-item label="处理状态" prop="status">
          <el-select v-model="handleForm.status" placeholder="请选择" style="width: 100%">
            <el-option label="处理中" value="processing" />
            <el-option label="已解决" value="resolved" />
            <el-option label="已处理" value="handled" />
            <el-option label="已关闭" value="closed" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitHandle">确定</el-button>
      </template>
    </el-dialog>

    <!-- 查看结果对话框 -->
    <el-dialog v-model="viewVisible" title="处理详情" width="520px" destroy-on-close>
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="异常类型">
          <el-tag :type="exceptionTypeTagType(currentRow?.exceptionType)" size="small">
            {{ exceptionTypeLabel(currentRow?.exceptionType) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="异常描述">{{ currentRow?.description }}</el-descriptions-item>
        <el-descriptions-item label="紧急程度">
          <el-tag :type="urgencyTagType(currentRow?.urgency)" size="small">{{ urgencyLabel(currentRow?.urgency) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="上报人">{{ currentRow?.reporterName }}</el-descriptions-item>
        <el-descriptions-item label="处理人">{{ currentRow?.handlerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="处理结果">{{ currentRow?.handleResult || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTagType(currentRow?.status)">{{ statusLabel(currentRow?.status) }}</el-tag>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getExceptionPage, handleException } from '@/api/workreport'

// ==================== 异常类型选项 ====================
const exceptionTypeOptions = [
  { label: '设备故障', value: 'equipment' },
  { label: '质量异常', value: 'quality' },
  { label: '物料异常', value: 'material' },
  { label: '工艺异常', value: 'process' },
  { label: '安全异常', value: 'safety' },
  { label: '其他', value: 'other' }
]
const exceptionTypeMap = Object.fromEntries(exceptionTypeOptions.map(s => [s.value, s.label]))

function exceptionTypeLabel(type) {
  return exceptionTypeMap[type] || type || '-'
}

function exceptionTypeTagType(type) {
  const map = {
    equipment: 'danger',
    quality: 'warning',
    material: '',
    process: 'warning',
    safety: 'danger',
    other: 'info'
  }
  return map[type] || 'info'
}

// ==================== 紧急程度 ====================
function urgencyLabel(urgency) {
  const map = { low: '低', medium: '中', high: '高', critical: '紧急' }
  return map[urgency] || urgency || '-'
}

function urgencyTagType(urgency) {
  const map = { low: 'info', medium: '', high: 'warning', critical: 'danger' }
  return map[urgency] || 'info'
}

// ==================== 状态选项 ====================
const statusOptions = [
  { label: '待处理', value: 'pending' },
  { label: '处理中', value: 'processing' },
  { label: '已解决', value: 'resolved' },
  { label: '已处理', value: 'handled' },
  { label: '已关闭', value: 'closed' }
]
const statusMap = Object.fromEntries(statusOptions.map(s => [s.value, s.label]))

function statusLabel(status) {
  return statusMap[status] || status || '-'
}

function statusTagType(status) {
  const map = {
    pending: 'danger',
    processing: 'warning',
    resolved: 'success',
    handled: 'success',
    closed: 'info'
  }
  return map[status] || 'info'
}

// ==================== 查询与表格 ====================
const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const queryParams = reactive({
  exceptionType: '',
  status: '',
  pageNum: 1,
  pageSize: 20
})

async function loadData() {
  loading.value = true
  try {
    const params = {
      exceptionType: queryParams.exceptionType || undefined,
      status: queryParams.status || undefined,
      pageNum: queryParams.pageNum,
      pageSize: queryParams.pageSize
    }
    const res = await getExceptionPage(params)
    tableData.value = res.data?.records || res.data?.list || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  queryParams.pageNum = 1
  loadData()
}

function handleReset() {
  queryParams.exceptionType = ''
  queryParams.status = ''
  queryParams.pageNum = 1
  loadData()
}

// ==================== 处理异常 ====================
const handleVisible = ref(false)
const viewVisible = ref(false)
const submitLoading = ref(false)
const handleFormRef = ref(null)
const currentRow = ref(null)
const handleForm = reactive({
  result: '',
  status: 'resolved'
})

const handleRules = {
  result: [{ required: true, message: '请输入处理结果', trigger: 'blur' }],
  status: [{ required: true, message: '请选择处理状态', trigger: 'change' }]
}

function handleProcess(row) {
  currentRow.value = row
  handleForm.result = ''
  handleForm.status = 'resolved'
  handleVisible.value = true
}

function handleViewResult(row) {
  currentRow.value = row
  viewVisible.value = true
}

async function submitHandle() {
  await handleFormRef.value.validate()
  submitLoading.value = true
  try {
    await handleException(currentRow.value.id, {
      result: handleForm.result,
      status: handleForm.status
    })
    ElMessage.success('处理成功')
    handleVisible.value = false
    loadData()
  } finally {
    submitLoading.value = false
  }
}

// ==================== 初始化 ====================
onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.page-container {
  padding: 16px;
}
.search-card {
  margin-bottom: 16px;
}
.table-card {
  .pagination-wrapper {
    display: flex;
    justify-content: flex-end;
    margin-top: 16px;
  }
}
</style>
