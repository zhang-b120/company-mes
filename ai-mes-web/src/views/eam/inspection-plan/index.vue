<template>
  <div class="page-container">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="点检计划" name="plan">
        <el-card class="search-card" shadow="never">
          <el-form :model="queryParams" inline>
            <el-form-item label="计划名称"><el-input v-model="queryParams.planName" clearable style="width:180px" @keyup.enter="loadData" /></el-form-item>
            <el-form-item label="状态">
              <el-select v-model="queryParams.planStatus" clearable style="width:140px">
                <el-option label="草稿" :value="0" />
                <el-option label="已发布" :value="1" />
                <el-option label="已取消" :value="3" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadData">搜索</el-button>
              <el-button type="primary" plain @click="handleAdd">新增计划</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card class="table-card" shadow="never">
          <el-table :data="tableData" v-loading="loading" border stripe>
            <el-table-column label="计划编号" prop="planCode" min-width="160" />
            <el-table-column label="计划名称" prop="planName" min-width="160" />
            <el-table-column label="类型" width="100" align="center">
              <template #default="{ row }">{{ ['日常', '定期', '专项'][row.inspectionType] || '-' }}</template>
            </el-table-column>
            <el-table-column label="周期(天)" prop="executionCycle" width="100" align="center" />
            <el-table-column label="预估时长(分钟)" prop="estimatedDuration" width="120" align="center" />
            <el-table-column label="负责人" prop="assigneeName" width="110" />
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }"><el-tag :type="planStatusTagType(row.planStatus)" size="small">{{ planStatusText(row.planStatus) }}</el-tag></template>
            </el-table-column>
            <el-table-column label="执行状态" width="110" align="center">
              <template #default="{ row }"><el-tag type="info" size="small">{{ execStatusText(row.execStatus) }}</el-tag></template>
            </el-table-column>
            <el-table-column label="操作" width="260" fixed="right" align="center">
              <template #default="{ row }">
                <el-button v-if="row.planStatus === 0" link type="success" size="small" @click="handlePublish(row)">发布</el-button>
                <el-button v-if="row.planStatus === 1 && row.execStatus === 0" link type="primary" size="small" @click="handleAssign(row)">下达任务</el-button>
                <el-button v-if="row.planStatus === 0" link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
                <el-button v-if="row.planStatus !== 3 && row.execStatus !== 3" link type="danger" size="small" @click="handleCancel(row)">取消</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrapper">
            <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize" :total="total"
              background layout="total, prev, pager, next" @current-change="loadData" />
          </div>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="点检任务" name="task">
        <el-card class="search-card" shadow="never">
          <el-form :model="taskQuery" inline>
            <el-form-item label="任务编号"><el-input v-model="taskQuery.taskCode" clearable style="width:180px" @keyup.enter="loadTasks" /></el-form-item>
            <el-form-item label="状态">
              <el-select v-model="taskQuery.taskStatus" clearable style="width:140px">
                <el-option label="待执行" :value="0" />
                <el-option label="执行中" :value="1" />
                <el-option label="已完成" :value="2" />
                <el-option label="已取消" :value="3" />
              </el-select>
            </el-form-item>
            <el-form-item><el-button type="primary" @click="loadTasks">搜索</el-button></el-form-item>
          </el-form>
        </el-card>

        <el-card class="table-card" shadow="never">
          <el-table :data="tasks" v-loading="taskLoading" border stripe>
            <el-table-column label="任务编号" prop="taskCode" min-width="160" />
            <el-table-column label="计划编号" prop="planCode" min-width="160" show-overflow-tooltip />
            <el-table-column label="设备" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="row.equipmentNo || row.equipmentName">{{ row.equipmentNo }} - {{ row.equipmentName }}</span>
                <span v-else class="text-muted">-</span>
              </template>
            </el-table-column>
            <el-table-column label="点检员" prop="inspectorName" width="110" />
            <el-table-column label="计划开始" prop="plannedStartTime" min-width="170" />
            <el-table-column label="计划结束" prop="plannedEndTime" min-width="170" />
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }"><el-tag :type="taskStatusType(row.taskStatus)" size="small">{{ taskStatusText(row.taskStatus) }}</el-tag></template>
            </el-table-column>
            <el-table-column label="操作" width="220" fixed="right" align="center">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="handleViewTask(row)">明细</el-button>
                <el-button v-if="row.taskStatus === 0" link type="success" size="small" @click="handleStartTask(row)">开始</el-button>
                <el-button v-if="row.taskStatus === 1" link type="primary" size="small" @click="handleCompleteTask(row)">完成</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrapper">
            <el-pagination v-model:current-page="taskQuery.pageNum" v-model:page-size="taskQuery.pageSize" :total="taskTotal"
              background layout="total, prev, pager, next" @current-change="loadTasks" />
          </div>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 计划新增/编辑 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="640px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="120px">
        <el-form-item label="计划名称" prop="planName"><el-input v-model="formData.planName" /></el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="点检类型">
              <el-select v-model="formData.inspectionType" style="width:100%">
                <el-option label="日常" :value="0" /><el-option label="定期" :value="1" /><el-option label="专项" :value="2" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="点检模板">
              <el-select v-model="formData.templateId" filterable clearable placeholder="请选择点检模板" style="width:100%" @change="onTemplateChange">
                <el-option v-for="t in templateOptions" :key="t.id" :label="t.templateName" :value="t.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="关联设备">
          <div v-if="planTemplateEquipments.length" style="display:flex;flex-wrap:wrap;gap:6px">
            <el-tag v-for="eq in planTemplateEquipments" :key="eq.id" type="info">{{ eq.equipmentNo }} - {{ eq.equipmentName }}</el-tag>
          </div>
          <span v-else style="color:#909399;font-size:13px">{{ formData.templateId ? '该模板未关联设备' : '请先选择模板，设备将自动带出' }}</span>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="执行周期(天)"><el-input-number v-model="formData.executionCycle" :min="1" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="预估时长(分)"><el-input-number v-model="formData.estimatedDuration" :min="0" style="width:100%" /></el-form-item></el-col>
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
        <el-form-item label="负责人姓名"><el-input v-model="formData.assigneeName" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="formData.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 任务明细抽屉 -->
    <el-drawer v-model="taskDetailVisible" title="点检明细" size="780px" destroy-on-close>
      <template v-if="taskDetail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="任务编号">{{ taskDetail.task?.taskCode }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ taskStatusText(taskDetail.task?.taskStatus) }}</el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ taskDetail.task?.startTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ taskDetail.task?.submitTime || '-' }}</el-descriptions-item>
        </el-descriptions>
        <el-divider content-position="left">点检项明细</el-divider>
        <el-alert v-if="isTaskReadonly" type="info" :closable="false" style="margin-bottom:8px"
          :title="taskDetail.task?.taskStatus === 2 ? '任务已完成，明细不可修改' : '任务已取消，明细不可修改'" />
        <el-table :data="taskDetail.details" border size="small">
          <el-table-column type="index" width="45" />
          <el-table-column label="项目" prop="itemName" min-width="140" />
          <el-table-column label="标准" prop="standard" min-width="120" />
          <el-table-column label="必检" width="60" align="center"><template #default="{ row }">{{ row.isRequired === 1 ? '是' : '否' }}</template></el-table-column>
          <el-table-column label="结果值" min-width="140">
            <template #default="{ row }">
              <el-input v-if="!isTaskReadonly" v-model="row.resultValue" size="small" />
              <span v-else>{{ row.resultValue || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="140">
            <template #default="{ row }">
              <el-select v-if="!isTaskReadonly" v-model="row.status" size="small">
                <el-option label="待检" :value="0" />
                <el-option label="已完成" :value="1" />
                <el-option label="异常" :value="2" />
              </el-select>
              <el-tag v-else :type="row.status === 1 ? 'success' : row.status === 2 ? 'danger' : 'info'" size="small">
                {{ ['待检', '已完成', '异常'][row.status] || '-' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column v-if="!isTaskReadonly" label="操作" width="80" align="center">
            <template #default="{ row }"><el-button link type="primary" size="small" @click="saveDetail(row)">保存</el-button></template>
          </el-table-column>
        </el-table>
      </template>
    </el-drawer>

    <!-- 任务完成对话框 -->
    <el-dialog v-model="completeTaskDialog" title="完成点检任务" width="560px" destroy-on-close>
      <el-form :model="completeTaskForm" label-width="120px">
        <el-form-item label="实际开始时间">
          <el-date-picker v-model="completeTaskForm.actualStartTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" />
        </el-form-item>
        <el-form-item label="实际结束时间">
          <el-date-picker v-model="completeTaskForm.actualEndTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="completeTaskDialog = false">取消</el-button>
        <el-button type="primary" @click="submitCompleteTask">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  getInspectionPlanPage, createInspectionPlan, updateInspectionPlan, deleteInspectionPlan,
  publishInspectionPlan, cancelInspectionPlan, assignInspectionTasks,
  getInspectionTemplateList, getInspectionTemplateDetail, getEquipmentList,
  getInspectionTaskPage, getInspectionTaskDetail, startInspectionTask,
  completeInspectionTask, updateInspectionTaskDetail
} from '@/api/eam'

const router = useRouter()
const activeTab = ref('plan')

// ========== 计划 ==========
const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const queryParams = reactive({ planName: '', planStatus: null, pageNum: 1, pageSize: 10 })

function planStatusText(s) { return ['草稿', '已发布', '', '已取消'][s] || '-' }
function planStatusTagType(s) { return ['info', 'success', '', 'danger'][s] || '' }
function execStatusText(s) { return ['待下达', '已下达', '部分完成', '全部完成'][s] || '-' }

async function loadData() {
  loading.value = true
  try {
    const res = await getInspectionPlanPage({ ...queryParams })
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally { loading.value = false }
}

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref(null)
const formData = reactive({ id: null, planName: '', inspectionType: 0, templateId: null, executionCycle: 1, estimatedDuration: null, plannedStartTime: '', plannedEndTime: '', assigneeName: '', remark: '' })
const formRules = { planName: [{ required: true, message: '请输入计划名称', trigger: 'blur' }] }

const templateOptions = ref([])
const planTemplateEquipments = ref([])

async function loadTemplateOptions() {
  const res = await getInspectionTemplateList()
  templateOptions.value = res.data?.records || res.data || []
}

async function onTemplateChange(templateId) {
  planTemplateEquipments.value = []
  if (!templateId) return
  const res = await getInspectionTemplateDetail(templateId)
  const detail = res.data || {}
  const equipmentIds = detail.equipmentIds || []
  if (equipmentIds.length === 0) return
  const all = await getEquipmentList()
  planTemplateEquipments.value = (all.data || []).filter(e => equipmentIds.includes(e.id))
}

function resetForm() {
  Object.assign(formData, { id: null, planName: '', inspectionType: 0, templateId: null, executionCycle: 1, estimatedDuration: null, plannedStartTime: '', plannedEndTime: '', assigneeName: '', remark: '' })
  planTemplateEquipments.value = []
}

async function handleAdd() {
  dialogTitle.value = '新增点检计划'
  resetForm()
  await loadTemplateOptions()
  dialogVisible.value = true
}

async function handleEdit(row) {
  dialogTitle.value = '编辑点检计划'
  resetForm()
  await loadTemplateOptions()
  Object.assign(formData, row)
  if (formData.templateId) await onTemplateChange(formData.templateId)
  dialogVisible.value = true
}

async function handleSubmit() {
  await formRef.value.validate()
  if (formData.id) { await updateInspectionPlan(formData.id, formData); ElMessage.success('更新成功') }
  else { await createInspectionPlan(formData); ElMessage.success('创建成功') }
  dialogVisible.value = false
  loadData()
}

async function handlePublish(row) { await publishInspectionPlan(row.id); ElMessage.success('已发布'); loadData() }
async function handleCancel(row) {
  await ElMessageBox.confirm('确定取消？', '提示', { type: 'warning' })
  await cancelInspectionPlan(row.id); ElMessage.success('已取消'); loadData()
}
async function handleAssign(row) {
  const res = await assignInspectionTasks(row.id)
  ElMessage.success(`已下达 ${res.data?.generatedTaskCount || 0} 个任务`)
  loadData()
}

// ========== 任务 ==========
const tasks = ref([])
const taskTotal = ref(0)
const taskLoading = ref(false)
const taskQuery = reactive({ taskCode: '', taskStatus: null, pageNum: 1, pageSize: 10 })

function taskStatusText(s) { return ['待执行', '执行中', '已完成', '已取消', '已暂停'][s] || '-' }
function taskStatusType(s) { return ['info', 'warning', 'success', 'danger', 'info'][s] || '' }

async function loadTasks() {
  taskLoading.value = true
  try {
    const res = await getInspectionTaskPage({ ...taskQuery })
    tasks.value = res.data?.records || []
    taskTotal.value = res.data?.total || 0
  } finally { taskLoading.value = false }
}

const taskDetailVisible = ref(false)
const taskDetail = ref(null)
const isTaskReadonly = computed(() => {
  const s = taskDetail.value?.task?.taskStatus
  return s === 2 || s === 3
})

async function handleViewTask(row) {
  const res = await getInspectionTaskDetail(row.id)
  taskDetail.value = res.data
  taskDetailVisible.value = true
}

async function saveDetail(row) {
  await updateInspectionTaskDetail(row.id, row)
  ElMessage.success('已保存')
}

async function handleStartTask(row) { await startInspectionTask(row.id); ElMessage.success('已开始'); loadTasks() }

const completeTaskDialog = ref(false)
const completeTaskRow = ref(null)
const completeTaskForm = reactive({ actualStartTime: '', actualEndTime: '' })

function handleCompleteTask(row) {
  completeTaskRow.value = row
  Object.assign(completeTaskForm, {
    actualStartTime: row.actualStartTime || row.plannedStartTime || '',
    actualEndTime: row.actualEndTime || row.plannedEndTime || ''
  })
  completeTaskDialog.value = true
}

async function submitCompleteTask() {
  const res = await completeInspectionTask(completeTaskRow.value.id, completeTaskForm)
  const r = res.data || {}
  ElMessage.success('已完成')
  completeTaskDialog.value = false
  loadTasks()
  if (r.hasAbnormal && r.abnormals?.length) {
    try {
      await ElMessageBox.confirm('发现异常项，是否基于异常创建报修申请？', '提示', { type: 'warning' })
      const faultDescription = r.abnormals.map(a => `${a.itemName}: ${a.remark || a.resultValue || '异常'}`).join('; ')
      sessionStorage.setItem('eam_auto_repair_from_inspection', JSON.stringify({
        equipmentId: r.equipmentId,
        faultDescription
      }))
      router.push('/eam/repair-request')
    } catch {}
  }
}

watch(activeTab, (val) => {
  if (val === 'plan') loadData()
  else loadTasks()
})

onMounted(() => { loadData() })
</script>

<style scoped lang="scss">
.page-container { display: flex; flex-direction: column; gap: 16px; }
.search-card :deep(.el-card__body) { padding-bottom: 2px; }
.table-card { flex: 1; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; }
.text-muted { color: #c0c4cc; }
</style>
