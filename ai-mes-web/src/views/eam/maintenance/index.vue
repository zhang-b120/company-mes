<template>
  <div class="page-container">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="保养计划" name="plan">
        <el-card class="search-card" shadow="never">
          <el-form :model="planQuery" inline>
            <el-form-item label="计划名称"><el-input v-model="planQuery.planName" clearable style="width:180px" @keyup.enter="loadPlans" /></el-form-item>
            <el-form-item label="类型">
              <el-select v-model="planQuery.maintenanceType" clearable style="width:140px">
                <el-option label="周期保养" :value="0" />
                <el-option label="预防性" :value="1" />
                <el-option label="状态保养" :value="2" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="planQuery.planStatus" clearable style="width:140px">
                <el-option label="草稿" :value="0" />
                <el-option label="已发布" :value="1" />
                <el-option label="已取消" :value="3" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadPlans">搜索</el-button>
              <el-button type="primary" plain @click="handleAddPlan">新增计划</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card class="table-card" shadow="never">
          <el-table :data="plans" v-loading="planLoading" border stripe>
            <el-table-column label="计划编号" prop="planCode" min-width="160" />
            <el-table-column label="计划名称" prop="planName" min-width="160" />
            <el-table-column label="类型" width="100" align="center">
              <template #default="{ row }">{{ ['周期', '预防性', '状态'][row.maintenanceType] || '-' }}</template>
            </el-table-column>
            <el-table-column label="周期(天)" prop="executionCycle" width="100" align="center" />
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }"><el-tag :type="planStatusType(row.planStatus)" size="small">{{ planStatusText(row.planStatus) }}</el-tag></template>
            </el-table-column>
            <el-table-column label="执行状态" width="110" align="center">
              <template #default="{ row }"><el-tag type="info" size="small">{{ execStatusText(row.execStatus) }}</el-tag></template>
            </el-table-column>
            <el-table-column label="操作" width="240" fixed="right" align="center">
              <template #default="{ row }">
                <el-button v-if="row.planStatus === 0" link type="success" size="small" @click="handlePublish(row)">发布</el-button>
                <el-button v-if="row.planStatus === 1 && row.execStatus === 0" link type="primary" size="small" @click="handleAssign(row)">下达任务</el-button>
                <el-button v-if="row.planStatus === 0" link type="primary" size="small" @click="handleEditPlan(row)">编辑</el-button>
                <el-button v-if="row.planStatus !== 3 && row.execStatus !== 3" link type="danger" size="small" @click="handleCancelPlan(row)">取消</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrapper">
            <el-pagination v-model:current-page="planQuery.pageNum" v-model:page-size="planQuery.pageSize" :total="planTotal"
              background layout="total, prev, pager, next" @current-change="loadPlans" />
          </div>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="保养任务" name="task">
        <el-card class="search-card" shadow="never">
          <el-form :model="taskQuery" inline>
            <el-form-item label="任务编号"><el-input v-model="taskQuery.taskCode" clearable style="width:180px" /></el-form-item>
            <el-form-item label="状态">
              <el-select v-model="taskQuery.taskStatus" clearable style="width:140px">
                <el-option label="待执行" :value="0" />
                <el-option label="执行中" :value="1" />
                <el-option label="已完成" :value="2" />
                <el-option label="已取消" :value="3" />
                <el-option label="已暂停" :value="4" />
              </el-select>
            </el-form-item>
            <el-form-item><el-button type="primary" @click="loadTasks">搜索</el-button></el-form-item>
          </el-form>
        </el-card>

        <el-row :gutter="16" class="stat-row">
          <el-col :span="4"><div class="stat-card"><div class="stat-card__value" style="color:#409eff">{{ taskStats.total || 0 }}</div><div class="stat-card__label">总数</div></div></el-col>
          <el-col :span="4"><div class="stat-card"><div class="stat-card__value" style="color:#909399">{{ taskStats.pending || 0 }}</div><div class="stat-card__label">待执行</div></div></el-col>
          <el-col :span="4"><div class="stat-card"><div class="stat-card__value" style="color:#e6a23c">{{ taskStats.inProgress || 0 }}</div><div class="stat-card__label">执行中</div></div></el-col>
          <el-col :span="4"><div class="stat-card"><div class="stat-card__value" style="color:#67c23a">{{ taskStats.completed || 0 }}</div><div class="stat-card__label">已完成</div></div></el-col>
          <el-col :span="4"><div class="stat-card"><div class="stat-card__value" style="color:#909399">{{ taskStats.cancelled || 0 }}</div><div class="stat-card__label">已取消</div></div></el-col>
          <el-col :span="4"><div class="stat-card"><div class="stat-card__value" style="color:#f56c6c">{{ taskStats.overdue || 0 }}</div><div class="stat-card__label">逾期</div></div></el-col>
        </el-row>

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
            <el-table-column label="计划开始" prop="plannedStartTime" min-width="170" />
            <el-table-column label="计划结束" prop="plannedEndTime" min-width="170" />
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }"><el-tag :type="taskStatusType(row.taskStatus)" size="small">{{ taskStatusText(row.taskStatus) }}</el-tag></template>
            </el-table-column>
            <el-table-column label="操作" width="220" fixed="right" align="center">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="handleViewTask(row)">明细</el-button>
                <el-button v-if="row.taskStatus === 0 || row.taskStatus === 4" link type="success" size="small" @click="handleStartTask(row)">开始</el-button>
                <el-button v-if="row.taskStatus === 1" link type="warning" size="small" @click="handlePauseTask(row)">暂停</el-button>
                <el-button v-if="row.taskStatus === 1" link type="primary" size="small" @click="openCompleteDialog(row)">完成</el-button>
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

    <!-- 计划编辑 -->
    <el-dialog v-model="planDialog" :title="planDialogTitle" width="680px" destroy-on-close>
      <el-form :model="planForm" label-width="110px">
        <el-form-item label="计划名称"><el-input v-model="planForm.planName" /></el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="保养类型">
              <el-select v-model="planForm.maintenanceType" style="width:100%">
                <el-option label="周期保养" :value="0" />
                <el-option label="预防性" :value="1" />
                <el-option label="状态保养" :value="2" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="执行周期(天)"><el-input-number v-model="planForm.executionCycle" :min="1" style="width:100%" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="计划开始时间">
              <el-date-picker v-model="planForm.plannedStartTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计划结束时间">
              <el-date-picker v-model="planForm.plannedEndTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="保养模板">
          <el-select v-model="planForm.templateId" filterable clearable placeholder="请选择保养模板" style="width:100%" @change="onTemplateChange">
            <el-option v-for="t in templateOptions" :key="t.id" :label="t.templateName" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联设备">
          <div v-if="planTemplateEquipments.length" style="display:flex;flex-wrap:wrap;gap:6px">
            <el-tag v-for="eq in planTemplateEquipments" :key="eq.id" type="info">{{ eq.equipmentNo }} - {{ eq.equipmentName }}</el-tag>
          </div>
          <span v-else style="color:#909399;font-size:13px">{{ planForm.templateId ? '该模板未关联设备' : '请先选择模板，设备将自动带出' }}</span>
        </el-form-item>
        <el-form-item label="保养内容"><el-input v-model="planForm.maintenanceContent" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="保养标准"><el-input v-model="planForm.maintenanceStandard" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="planForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="planDialog = false">取消</el-button>
        <el-button type="primary" @click="savePlan">确定</el-button>
      </template>
    </el-dialog>

    <!-- 任务明细 -->
    <el-drawer v-model="taskDetailVisible" title="任务明细" size="800px">
      <template v-if="taskDetail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="任务编号">{{ taskDetail.task?.taskCode }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ taskStatusText(taskDetail.task?.taskStatus) }}</el-descriptions-item>
          <el-descriptions-item label="计划开始">{{ taskDetail.task?.plannedStartTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="计划结束">{{ taskDetail.task?.plannedEndTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="实际开始">{{ taskDetail.task?.actualStartTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="实际结束">{{ taskDetail.task?.actualEndTime || '-' }}</el-descriptions-item>
        </el-descriptions>
        <el-divider content-position="left">检查项明细</el-divider>
        <el-alert v-if="isTaskReadonly" type="info" :closable="false" style="margin-bottom:8px"
          :title="taskDetail.task?.taskStatus === 2 ? '任务已完成，明细不可修改' : '任务已取消，明细不可修改'" />
        <el-table :data="taskDetail.details" border size="small">
          <el-table-column type="index" width="45" />
          <el-table-column label="项目" prop="itemName" min-width="140" />
          <el-table-column label="标准" prop="standard" min-width="120" />
          <el-table-column label="方法" prop="method" min-width="120" />
          <el-table-column label="结果值" min-width="140">
            <template #default="{ row }">
              <el-input v-if="!isTaskReadonly" v-model="row.resultValue" size="small" />
              <span v-else>{{ row.resultValue || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="130">
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

    <!-- 完成对话框 -->
    <el-dialog v-model="completeDialog" title="完成任务" width="600px" destroy-on-close>
      <el-form :model="completeForm" label-width="110px">
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
        <el-form-item label="维修结果"><el-input v-model="completeForm.maintenanceResult" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="发现问题"><el-input v-model="completeForm.issuesFound" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="使用耗材"><el-input v-model="completeForm.consumablesUsed" /></el-form-item>
        <el-form-item label="更换配件"><el-input v-model="completeForm.sparePartsReplaced" /></el-form-item>
        <el-form-item label="实际工时"><el-input-number v-model="completeForm.actualHours" :min="0" :precision="2" style="width:100%" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="completeDialog = false">取消</el-button>
        <el-button type="primary" @click="submitComplete">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getMaintenancePlanPage, createMaintenancePlan, updateMaintenancePlan,
  publishMaintenancePlan, cancelMaintenancePlan, assignMaintenanceTasks,
  getMaintenanceTaskPage, getMaintenanceTaskDetail, startMaintenanceTask, pauseMaintenanceTask,
  completeMaintenanceTask, updateMaintenanceTaskDetail, getMaintenanceTaskStatistics,
  getMaintenanceTemplateList, getMaintenanceTemplateDetail, getEquipmentList
} from '@/api/eam'

const activeTab = ref('plan')

// ========== 计划 ==========
const planQuery = reactive({ planName: '', maintenanceType: null, planStatus: null, pageNum: 1, pageSize: 10 })
const plans = ref([])
const planTotal = ref(0)
const planLoading = ref(false)

function planStatusText(s) { return ['草稿', '已发布', '已暂停', '已取消'][s] || '-' }
function planStatusType(s) { return ['info', 'success', 'warning', 'danger'][s] || '' }
function execStatusText(s) { return ['待下达', '已下达', '部分完成', '全部完成'][s] || '-' }

async function loadPlans() {
  planLoading.value = true
  try {
    const res = await getMaintenancePlanPage({ ...planQuery })
    plans.value = res.data?.records || []
    planTotal.value = res.data?.total || 0
  } finally { planLoading.value = false }
}

const planDialog = ref(false)
const planDialogTitle = ref('')
const planForm = reactive({ id: null, planName: '', maintenanceType: 0, executionCycle: 30, plannedStartTime: '', plannedEndTime: '', templateId: null, equipmentId: null, maintenanceContent: '', maintenanceStandard: '', remark: '' })
const templateOptions = ref([])
const planTemplateEquipments = ref([])

async function loadTemplateOptions() {
  const res = await getMaintenanceTemplateList()
  templateOptions.value = res.data || []
}

async function onTemplateChange(templateId) {
  planTemplateEquipments.value = []
  if (!templateId) return
  const res = await getMaintenanceTemplateDetail(templateId)
  const detail = res.data || {}
  const equipmentIds = detail.equipmentIds || []
  if (equipmentIds.length === 0) return
  // 加载所有设备并过滤(简化处理)
  const all = await getEquipmentList()
  planTemplateEquipments.value = (all.data || []).filter(e => equipmentIds.includes(e.id))
}

function resetPlanForm() {
  Object.assign(planForm, { id: null, planName: '', maintenanceType: 0, executionCycle: 30, plannedStartTime: '', plannedEndTime: '', templateId: null, equipmentId: null, maintenanceContent: '', maintenanceStandard: '', remark: '' })
  planTemplateEquipments.value = []
}
async function handleAddPlan() {
  planDialogTitle.value = '新增保养计划'
  resetPlanForm()
  await loadTemplateOptions()
  planDialog.value = true
}
async function handleEditPlan(row) {
  planDialogTitle.value = '编辑保养计划'
  resetPlanForm()
  await loadTemplateOptions()
  Object.assign(planForm, row)
  if (planForm.templateId) await onTemplateChange(planForm.templateId)
  planDialog.value = true
}

async function savePlan() {
  if (planForm.id) { await updateMaintenancePlan(planForm.id, planForm); ElMessage.success('更新成功') }
  else { await createMaintenancePlan(planForm); ElMessage.success('创建成功') }
  planDialog.value = false
  loadPlans()
}

async function handlePublish(row) { await publishMaintenancePlan(row.id); ElMessage.success('已发布'); loadPlans() }
async function handleCancelPlan(row) {
  await ElMessageBox.confirm('确定取消该计划？', '提示', { type: 'warning' })
  await cancelMaintenancePlan(row.id); ElMessage.success('已取消'); loadPlans()
}
async function handleAssign(row) {
  const res = await assignMaintenanceTasks(row.id)
  ElMessage.success(`已下达 ${res.data?.generatedTaskCount || 0} 个任务`)
  loadPlans()
}

// ========== 任务 ==========
const taskQuery = reactive({ taskCode: '', taskStatus: null, pageNum: 1, pageSize: 10 })
const tasks = ref([])
const taskTotal = ref(0)
const taskLoading = ref(false)
const taskStats = ref({})

function taskStatusText(s) { return ['待执行', '执行中', '已完成', '已取消', '已暂停'][s] || '-' }
function taskStatusType(s) { return ['info', 'warning', 'success', 'danger', 'info'][s] || '' }

async function loadTasks() {
  taskLoading.value = true
  try {
    const res = await getMaintenanceTaskPage({ ...taskQuery })
    tasks.value = res.data?.records || []
    taskTotal.value = res.data?.total || 0
  } finally { taskLoading.value = false }
  const stats = await getMaintenanceTaskStatistics()
  taskStats.value = stats.data || {}
}

async function handleStartTask(row) { await startMaintenanceTask(row.id); ElMessage.success('已开始'); loadTasks() }
async function handlePauseTask(row) { await pauseMaintenanceTask(row.id); ElMessage.success('已暂停'); loadTasks() }

const taskDetailVisible = ref(false)
const taskDetail = ref(null)
const isTaskReadonly = computed(() => {
  const s = taskDetail.value?.task?.taskStatus
  return s === 2 || s === 3
})
async function handleViewTask(row) {
  const res = await getMaintenanceTaskDetail(row.id)
  taskDetail.value = res.data
  taskDetailVisible.value = true
}
async function saveDetail(row) {
  await updateMaintenanceTaskDetail(row.id, row)
  ElMessage.success('已保存')
}

const completeDialog = ref(false)
const completeForm = reactive({ actualStartTime: '', actualEndTime: '', maintenanceResult: '', issuesFound: '', consumablesUsed: '', sparePartsReplaced: '', actualHours: null })
const completeRow = ref(null)
function openCompleteDialog(row) {
  completeRow.value = row
  Object.assign(completeForm, {
    actualStartTime: row.actualStartTime || row.plannedStartTime || '',
    actualEndTime: row.actualEndTime || row.plannedEndTime || '',
    maintenanceResult: '', issuesFound: '', consumablesUsed: '', sparePartsReplaced: '', actualHours: null
  })
  completeDialog.value = true
}
async function submitComplete() {
  await completeMaintenanceTask(completeRow.value.id, completeForm)
  ElMessage.success('已完成')
  completeDialog.value = false
  loadTasks()
}

watch(activeTab, (val) => {
  if (val === 'plan') loadPlans()
  else loadTasks()
})

onMounted(() => { loadPlans() })
</script>

<style scoped lang="scss">
.page-container { display: flex; flex-direction: column; gap: 16px; }
.stat-row { margin-bottom: 12px; }
.stat-card { background: #fff; border-radius: 8px; padding: 12px; text-align: center; box-shadow: 0 1px 4px rgba(0,0,0,.06); &__value { font-size: 22px; font-weight: 700; } &__label { font-size: 12px; color: #909399; margin-top: 4px; } }
.search-card :deep(.el-card__body) { padding-bottom: 2px; }
.table-card { flex: 1; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
