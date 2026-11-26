<template>
  <div class="page-container">
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="工单号"><el-input v-model="queryParams.workOrderNo" clearable style="width:180px" @keyup.enter="loadData" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" clearable style="width:140px">
            <el-option label="待开始" value="PENDING" />
            <el-option label="进行中" value="IN_PROGRESS" />
            <el-option label="已暂停" value="PAUSED" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="queryParams.priority" clearable style="width:130px">
            <el-option label="低" value="LOW" />
            <el-option label="中" value="MEDIUM" />
            <el-option label="高" value="HIGH" />
            <el-option label="紧急" value="URGENT" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">搜索</el-button>
          <el-button type="primary" plain @click="handleAdd">新建工单</el-button>
          <el-button type="success" plain :disabled="!selectedIds.length" @click="handleBatchStart">批量开始</el-button>
          <el-button type="warning" plain :disabled="!selectedIds.length" @click="handleBatchPause">批量暂停</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <el-table :data="tableData" v-loading="loading" border stripe @selection-change="onSelect">
        <el-table-column type="selection" width="42" />
        <el-table-column label="工单号" prop="workOrderNo" min-width="160" />
        <el-table-column label="设备名称" prop="equipmentName" min-width="140" />
        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">{{ { FAULT: '故障', PREVENTIVE: '预防', OVERHAUL: '大修' }[row.repairType] || '-' }}</template>
        </el-table-column>
        <el-table-column label="优先级" width="100" align="center">
          <template #default="{ row }"><el-tag :type="priorityType(row.priority)" size="small">{{ priorityText(row.priority) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="维修人员" prop="assigneeName" width="110" />
        <el-table-column label="计划开始" prop="plannedStartTime" min-width="170" />
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }"><el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="290" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PENDING' || row.status === 'PAUSED'" link type="success" size="small" @click="handleStart(row)">{{ row.status === 'PAUSED' ? '恢复' : '开始' }}</el-button>
            <el-button v-if="row.status === 'IN_PROGRESS'" link type="warning" size="small" @click="handlePause(row)">暂停</el-button>
            <el-button v-if="row.status === 'IN_PROGRESS'" link type="primary" size="small" @click="openComplete(row)">完成</el-button>
            <el-button v-if="!['COMPLETED', 'CANCELLED'].includes(row.status)" link type="danger" size="small" @click="handleCancel(row)">取消</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize" :total="total"
          background layout="total, prev, pager, next" @current-change="loadData" />
      </div>
    </el-card>

    <!-- 新建工单 -->
    <el-dialog v-model="dialogVisible" title="新建维修工单" width="640px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="120px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="设备ID" prop="equipmentId"><el-input-number v-model="formData.equipmentId" :min="0" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="设备名称"><el-input v-model="formData.equipmentName" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="类型" prop="repairType">
              <el-select v-model="formData.repairType" style="width:100%">
                <el-option label="故障" value="FAULT" />
                <el-option label="预防" value="PREVENTIVE" />
                <el-option label="大修" value="OVERHAUL" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="优先级" prop="priority">
              <el-select v-model="formData.priority" style="width:100%">
                <el-option label="低" value="LOW" />
                <el-option label="中" value="MEDIUM" />
                <el-option label="高" value="HIGH" />
                <el-option label="紧急" value="URGENT" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="维修人员"><el-input v-model="formData.assigneeName" /></el-form-item>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="计划开始时间"><el-date-picker v-model="formData.plannedStartTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="计划结束时间"><el-date-picker v-model="formData.plannedEndTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="故障描述" prop="faultDescription"><el-input v-model="formData.faultDescription" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleFormSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 完成对话框 -->
    <el-dialog v-model="completeDialog" title="完成工单" width="640px" destroy-on-close>
      <el-form :model="completeForm" :rules="completeRules" ref="completeRef" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="实际开始时间">
              <el-date-picker v-model="completeForm.actualStartTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="实际结束时间">
              <el-date-picker v-model="completeForm.actualEndTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="维修方案" prop="repairPlan"><el-input v-model="completeForm.repairPlan" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="维修结果" prop="repairResult"><el-input v-model="completeForm.repairResult" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="实际工时" prop="actualHours"><el-input-number v-model="completeForm.actualHours" :min="0" :precision="2" style="width:100%" /></el-form-item>
        <el-form-item label="维修费用"><el-input-number v-model="completeForm.repairCost" :min="0" :precision="2" style="width:100%" /></el-form-item>
        <el-form-item label="更换配件"><el-input v-model="completeForm.replacedParts" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="completeDialog = false">取消</el-button>
        <el-button type="primary" @click="submitComplete">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getRepairOrderPage, createRepairOrder, startRepairOrder, pauseRepairOrder, resumeRepairOrder,
  completeRepairOrder, cancelRepairOrder, batchStartRepairOrders, batchPauseRepairOrders
} from '@/api/eam'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const queryParams = reactive({ workOrderNo: '', status: '', priority: '', pageNum: 1, pageSize: 10 })

function statusText(s) { return { PENDING: '待开始', IN_PROGRESS: '进行中', PAUSED: '已暂停', COMPLETED: '已完成', CANCELLED: '已取消' }[s] || s }
function statusType(s) { return { PENDING: 'info', IN_PROGRESS: 'warning', PAUSED: 'info', COMPLETED: 'success', CANCELLED: 'danger' }[s] || '' }
function priorityText(s) { return { LOW: '低', MEDIUM: '中', HIGH: '高', URGENT: '紧急' }[s] || s }
function priorityType(s) { return { LOW: 'info', MEDIUM: 'warning', HIGH: 'warning', URGENT: 'danger' }[s] || '' }

async function loadData() {
  loading.value = true
  try {
    const res = await getRepairOrderPage({ ...queryParams })
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally { loading.value = false }
}

const selectedIds = ref([])
function onSelect(rows) { selectedIds.value = rows.map(r => r.id) }

const dialogVisible = ref(false)
const formRef = ref(null)
const formData = reactive({ equipmentId: null, equipmentName: '', repairType: 'FAULT', priority: 'MEDIUM', assigneeName: '', plannedStartTime: '', plannedEndTime: '', faultDescription: '' })
const formRules = {
  equipmentId: [{ required: true, message: '请输入设备ID', trigger: 'blur' }],
  repairType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  priority: [{ required: true, message: '请选择优先级', trigger: 'change' }],
  faultDescription: [{ required: true, message: '请输入故障描述', trigger: 'blur' }]
}

function handleAdd() {
  Object.assign(formData, { equipmentId: null, equipmentName: '', repairType: 'FAULT', priority: 'MEDIUM', assigneeName: '', plannedStartTime: '', plannedEndTime: '', faultDescription: '' })
  dialogVisible.value = true
}

async function handleFormSubmit() {
  await formRef.value.validate()
  await createRepairOrder(formData)
  ElMessage.success('创建成功')
  dialogVisible.value = false
  loadData()
}

async function handleStart(row) {
  if (row.status === 'PAUSED') await resumeRepairOrder(row.id)
  else await startRepairOrder(row.id)
  ElMessage.success('已开始'); loadData()
}
async function handlePause(row) { await pauseRepairOrder(row.id); ElMessage.success('已暂停'); loadData() }
async function handleCancel(row) {
  await ElMessageBox.confirm('确定取消？', '提示', { type: 'warning' })
  await cancelRepairOrder(row.id); ElMessage.success('已取消'); loadData()
}

const completeDialog = ref(false)
const completeRef = ref(null)
const completeRow = ref(null)
const completeForm = reactive({ actualStartTime: '', actualEndTime: '', repairPlan: '', repairResult: '', actualHours: null, repairCost: null, replacedParts: '' })
const completeRules = {
  repairPlan: [{ required: true, message: '维修方案必填', trigger: 'blur' }],
  repairResult: [{ required: true, message: '维修结果必填', trigger: 'blur' }],
  actualHours: [{ required: true, message: '实际工时必填', trigger: 'blur' }]
}
function openComplete(row) {
  completeRow.value = row
  Object.assign(completeForm, {
    actualStartTime: row.actualStartTime || row.plannedStartTime || '',
    actualEndTime: row.actualEndTime || row.plannedEndTime || '',
    repairPlan: '', repairResult: '', actualHours: null, repairCost: null, replacedParts: ''
  })
  completeDialog.value = true
}
async function submitComplete() {
  await completeRef.value.validate()
  await completeRepairOrder(completeRow.value.id, completeForm)
  ElMessage.success('已完成')
  completeDialog.value = false
  loadData()
}

async function handleBatchStart() {
  await batchStartRepairOrders(selectedIds.value)
  ElMessage.success('批量开始成功')
  loadData()
}

async function handleBatchPause() {
  await batchPauseRepairOrders(selectedIds.value)
  ElMessage.success('批量暂停成功')
  loadData()
}

onMounted(() => { loadData() })
</script>

<style scoped lang="scss">
.page-container { display: flex; flex-direction: column; gap: 16px; }
.search-card :deep(.el-card__body) { padding-bottom: 2px; }
.table-card { flex: 1; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
