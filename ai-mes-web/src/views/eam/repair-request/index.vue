<template>
  <div class="page-container">
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="申请单号"><el-input v-model="queryParams.requestNo" clearable style="width:180px" @keyup.enter="loadData" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" clearable style="width:140px">
            <el-option label="待处理" value="PENDING" />
            <el-option label="已受理" value="ACCEPTED" />
            <el-option label="处理中" value="IN_PROGRESS" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">搜索</el-button>
          <el-button type="primary" plain @click="handleAdd">新增报修</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column label="申请单号" prop="requestNo" min-width="160" />
        <el-table-column label="设备名称" prop="equipmentName" min-width="140" />
        <el-table-column label="报修类型" width="110" align="center">
          <template #default="{ row }">{{ { FAULT: '故障', PREVENTIVE: '预防', OVERHAUL: '大修' }[row.repairType] || row.repairType || '-' }}</template>
        </el-table-column>
        <el-table-column label="优先级" width="100" align="center">
          <template #default="{ row }"><el-tag :type="priorityType(row.priority)" size="small">{{ priorityText(row.priority) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="故障描述" prop="faultDescription" min-width="200" show-overflow-tooltip />
        <el-table-column label="报修人" prop="reporterName" width="110" />
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }"><el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PENDING'" link type="primary" size="small" @click="handleAccept(row)">受理</el-button>
            <el-button v-if="row.status === 'PENDING' || row.status === 'ACCEPTED'" link type="success" size="small" @click="handleSubmit2(row)">生单</el-button>
            <el-button v-if="['PENDING', 'ACCEPTED'].includes(row.status)" link type="warning" size="small" @click="handleCancel(row)">取消</el-button>
            <el-button v-if="['COMPLETED', 'CANCELLED'].includes(row.status)" link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize" :total="total"
          background layout="total, prev, pager, next" @current-change="loadData" />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" title="新增报修申请" width="640px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="24">
            <el-form-item label="设备" prop="equipmentId">
              <el-select v-model="formData.equipmentId" filterable placeholder="请选择设备" style="width:100%" @change="onEquipmentChange">
                <el-option v-for="eq in equipmentOptions" :key="eq.id" :label="`${eq.equipmentNo} - ${eq.equipmentName}`" :value="eq.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="报修类型" prop="repairType">
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
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="计划开始时间">
              <el-date-picker v-model="formData.plannedStartTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计划结束时间">
              <el-date-picker v-model="formData.plannedEndTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="故障描述" prop="faultDescription"><el-input v-model="formData.faultDescription" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="报修人"><el-input v-model="formData.reporterName" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleFormSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getRepairRequestPage, createRepairRequest, acceptRepairRequest, submitRepairRequest, cancelRepairRequest, deleteRepairRequest, getEquipmentList } from '@/api/eam'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const queryParams = reactive({ requestNo: '', status: '', pageNum: 1, pageSize: 10 })

function statusText(s) { return { PENDING: '待处理', ACCEPTED: '已受理', IN_PROGRESS: '处理中', COMPLETED: '已完成', CANCELLED: '已取消' }[s] || s }
function statusType(s) { return { PENDING: 'info', ACCEPTED: 'warning', IN_PROGRESS: 'primary', COMPLETED: 'success', CANCELLED: 'danger' }[s] || '' }
function priorityText(s) { return { LOW: '低', MEDIUM: '中', HIGH: '高', URGENT: '紧急' }[s] || s }
function priorityType(s) { return { LOW: 'info', MEDIUM: 'warning', HIGH: 'warning', URGENT: 'danger' }[s] || '' }

async function loadData() {
  loading.value = true
  try {
    const res = await getRepairRequestPage({ ...queryParams })
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally { loading.value = false }
}

const dialogVisible = ref(false)
const formRef = ref(null)
const formData = reactive({ equipmentId: null, equipmentNo: '', equipmentName: '', repairType: 'FAULT', priority: 'MEDIUM', plannedStartTime: '', plannedEndTime: '', faultDescription: '', reporterName: '' })
const formRules = {
  equipmentId: [{ required: true, message: '请选择设备', trigger: 'change' }],
  repairType: [{ required: true, message: '请选择报修类型', trigger: 'change' }],
  priority: [{ required: true, message: '请选择优先级', trigger: 'change' }],
  faultDescription: [{ required: true, message: '请输入故障描述', trigger: 'blur' }]
}

const equipmentOptions = ref([])
async function loadEquipmentOptions() {
  const res = await getEquipmentList()
  equipmentOptions.value = res.data || []
}
function onEquipmentChange(eqId) {
  const eq = equipmentOptions.value.find(e => e.id === eqId)
  if (eq) {
    formData.equipmentNo = eq.equipmentNo
    formData.equipmentName = eq.equipmentName
  } else {
    formData.equipmentNo = ''
    formData.equipmentName = ''
  }
}

async function handleAdd() {
  Object.assign(formData, { equipmentId: null, equipmentNo: '', equipmentName: '', repairType: 'FAULT', priority: 'MEDIUM', plannedStartTime: '', plannedEndTime: '', faultDescription: '', reporterName: '' })
  await loadEquipmentOptions()
  // 从点检任务自动跳转时预填
  const autoData = sessionStorage.getItem('eam_auto_repair_from_inspection')
  if (autoData) {
    try {
      const d = JSON.parse(autoData)
      formData.equipmentId = d.equipmentId
      onEquipmentChange(d.equipmentId)
      formData.faultDescription = d.faultDescription
      sessionStorage.removeItem('eam_auto_repair_from_inspection')
    } catch {}
  }
  dialogVisible.value = true
}

async function handleFormSubmit() {
  await formRef.value.validate()
  await createRepairRequest(formData)
  ElMessage.success('提交成功')
  dialogVisible.value = false
  loadData()
}

async function handleAccept(row) {
  await acceptRepairRequest(row.id, { acceptorName: '当前用户' })
  ElMessage.success('已受理')
  loadData()
}

async function handleSubmit2(row) {
  await submitRepairRequest(row.id)
  ElMessage.success('已生成维修工单')
  loadData()
}

async function handleCancel(row) {
  await ElMessageBox.confirm('确定取消？', '提示', { type: 'warning' })
  await cancelRepairRequest(row.id); ElMessage.success('已取消'); loadData()
}

async function handleDelete(row) {
  await ElMessageBox.confirm('确定删除？', '提示', { type: 'warning' })
  await deleteRepairRequest(row.id); ElMessage.success('已删除'); loadData()
}

onMounted(() => {
  loadData()
  // 从点检任务跳过来时自动打开新增弹窗
  if (sessionStorage.getItem('eam_auto_repair_from_inspection')) {
    handleAdd()
  }
})
</script>

<style scoped lang="scss">
.page-container { display: flex; flex-direction: column; gap: 16px; }
.search-card :deep(.el-card__body) { padding-bottom: 2px; }
.table-card { flex: 1; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
