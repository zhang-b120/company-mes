<template>
  <div class="page-container">
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="处理单号">
          <el-input v-model="queryParams.disposalNo" placeholder="处理单号" clearable style="width: 150px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="待处理" value="pending" />
            <el-option label="审批中" value="pending_approval" />
            <el-option label="已处理" value="processed" />
          </el-select>
        </el-form-item>
        <el-form-item label="处理方式">
          <el-select v-model="queryParams.disposalType" placeholder="全部" clearable style="width: 120px">
            <el-option label="报废" value="scrap" />
            <el-option label="让步接收" value="concession" />
            <el-option label="返修" value="rework" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="disposalNo" label="处理单号" min-width="140" show-overflow-tooltip />
        <el-table-column prop="qcTaskNo" label="质检任务" min-width="130" show-overflow-tooltip />
        <el-table-column prop="woNo" label="工单号" min-width="130" show-overflow-tooltip />
        <el-table-column prop="materialName" label="物料" min-width="120" show-overflow-tooltip />
        <el-table-column prop="stepName" label="工序" width="100" />
        <el-table-column prop="quantity" label="不合格数" width="90" align="right" />
        <el-table-column prop="disposalType" label="处理方式" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.disposalType" :type="typeTag[row.disposalType]" size="small">{{ typeText[row.disposalType] }}</el-tag>
            <span v-else style="color:#c0c4cc">待处理</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'processed' ? 'success' : row.status === 'pending_approval' ? 'info' : 'warning'" size="small">{{ row.status === 'processed' ? '已处理' : row.status === 'pending_approval' ? '审批中' : '待处理' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="160">
          <template #default="{ row }">{{ $fmtDate(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'pending'" type="primary" link size="small" @click="openProcessDialog(row)">处理</el-button>
            <el-button type="primary" link size="small" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50]" :total="total" background layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <!-- 处理弹窗 -->
    <el-dialog v-model="processVisible" title="质检处理" width="600px" destroy-on-close>
      <el-descriptions :column="2" border size="small" style="margin-bottom: 16px">
        <el-descriptions-item label="处理单号">{{ processRow.disposalNo }}</el-descriptions-item>
        <el-descriptions-item label="不合格数量">{{ processRow.quantity }}</el-descriptions-item>
        <el-descriptions-item label="物料">{{ processRow.materialName }}</el-descriptions-item>
        <el-descriptions-item label="工序">{{ processRow.stepName || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-form label-width="80px">
        <el-form-item label="处理方式" required>
          <el-radio-group v-model="processForm.disposalType">
            <el-radio value="scrap">报废</el-radio>
            <el-radio value="concession">让步接收</el-radio>
            <el-radio value="rework">返修</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="原因">
          <el-input v-model="processForm.reason" type="textarea" :rows="2" placeholder="处理原因" />
        </el-form-item>
        <template v-if="processForm.disposalType === 'rework'">
          <el-form-item label="返修工序">
            <div v-if="!woTaskOptions.length && !woTaskLoading" style="display:flex;align-items:center;gap:8px">
              <span style="color:#909399;font-size:13px">未加载工序</span>
              <el-button type="primary" plain size="small" @click="loadWoTasks(processRow.workOrderId)">加载原工单工序</el-button>
            </div>
            <div v-else-if="woTaskLoading" style="color:#909399;font-size:13px">加载中...</div>
            <el-table v-else :data="reworkSteps" border size="small" style="width:100%">
              <el-table-column width="45" align="center">
                <template #default="{ row }">
                  <el-checkbox v-model="row.selected" />
                </template>
              </el-table-column>
              <el-table-column label="工序" min-width="120">
                <template #default="{ row }">
                  <span>{{ row.stepNo ? row.stepNo + '. ' : '' }}{{ row.stepName }}</span>
                </template>
              </el-table-column>
              <el-table-column label="质检" width="60" align="center">
                <template #default="{ row }">
                  <el-tag v-if="row.hasQc" type="warning" size="small">有</el-tag>
                  <span v-else style="color:#c0c4cc">无</span>
                </template>
              </el-table-column>
              <el-table-column label="指派人" width="150">
                <template #default="{ row }">
                  <el-select v-model="row.assignedUserId" placeholder="指派人" filterable clearable size="small" :disabled="!row.selected">
                    <el-option v-for="u in userOptions" :key="u.id" :label="u.realName || u.username" :value="u.id" />
                  </el-select>
                </template>
              </el-table-column>
            </el-table>
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="processVisible = false">取消</el-button>
        <el-button type="primary" :loading="processLoading" @click="doProcess">确认处理</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" title="质检处理单详情" size="600px" destroy-on-close>
      <template v-if="detailData">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="处理单号">{{ detailData.disposalNo }}</el-descriptions-item>
          <el-descriptions-item label="质检任务ID">{{ detailData.qcTaskId }}</el-descriptions-item>
          <el-descriptions-item label="工单号">{{ detailData.woNo }}</el-descriptions-item>
          <el-descriptions-item label="物料">{{ detailData.materialName }}</el-descriptions-item>
          <el-descriptions-item label="工序">{{ detailData.stepName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="不合格数量">{{ detailData.quantity }}</el-descriptions-item>
          <el-descriptions-item label="处理方式">{{ typeText[detailData.disposalType] || '待处理' }}</el-descriptions-item>
          <el-descriptions-item label="处理原因">{{ detailData.disposalReason || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ detailData.status === 'processed' ? '已处理' : detailData.status === 'pending_approval' ? '审批中' : '待处理' }}</el-descriptions-item>
          <el-descriptions-item label="返修工单ID">{{ detailData.reworkOrderId || '-' }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getQcDisposalPage, getQcDisposalDetail, processQcDisposal } from '@/api/quality'
import { getUserSelect } from '@/api/system'
import { getWorkOrderTasks } from '@/api/production'

const typeText = { scrap: '报废', concession: '让步接收', rework: '返修' }
const typeTag = { scrap: 'danger', concession: 'warning', rework: 'primary' }

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const queryParams = reactive({ disposalNo: '', status: '', disposalType: '', pageNum: 1, pageSize: 20 })

async function loadData() {
  loading.value = true
  try {
    const res = await getQcDisposalPage(queryParams)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch {} finally { loading.value = false }
}

function handleSearch() { queryParams.pageNum = 1; loadData() }
function handleReset() { queryParams.disposalNo = ''; queryParams.status = ''; queryParams.disposalType = ''; queryParams.pageNum = 1; loadData() }

const processVisible = ref(false)
const processLoading = ref(false)
const processRow = ref({})
const processForm = reactive({ disposalType: 'scrap', reason: '' })
const reworkSteps = ref([])
const woTaskOptions = ref([])   // 原工单工序列表
const woTaskLoading = ref(false)
const userOptions = ref([])

async function loadUserOptions() {
  if (userOptions.value.length > 0) return
  try {
    const res = await getUserSelect()
    userOptions.value = res.data || []
  } catch {}
}

async function loadWoTasks(workOrderId) {
  if (!workOrderId) { woTaskOptions.value = []; return }
  woTaskLoading.value = true
  try {
    const res = await getWorkOrderTasks(workOrderId)
    let tasks = (res.data || []).map(t => ({
      id: t.id,
      stepNo: t.stepNo,
      stepName: t.stepName,
      hasQc: !!t.qcTemplateId,
      assignedUserId: null
    }))
    // 若处理单关联了具体工序，只显示该工序及其后续工序
    if (processRow.value.processTaskId) {
      const idx = tasks.findIndex(t => t.id === processRow.value.processTaskId)
      if (idx >= 0) tasks = tasks.slice(idx)
    }
    woTaskOptions.value = tasks
    reworkSteps.value = woTaskOptions.value.map(t => ({ ...t, selected: true }))
  } catch {
    woTaskOptions.value = []
    reworkSteps.value = []
  } finally { woTaskLoading.value = false }
}

function openProcessDialog(row) {
  processRow.value = row
  processForm.disposalType = 'scrap'
  processForm.reason = ''
  reworkSteps.value = []
  woTaskOptions.value = []
  processVisible.value = true
  loadUserOptions()
}

// 切换到"返修"时自动加载原工单工序
watch(() => processForm.disposalType, val => {
  if (val === 'rework' && !woTaskOptions.value.length) {
    loadWoTasks(processRow.value.workOrderId)
  }
})

async function doProcess() {
  if (!processForm.disposalType) { ElMessage.warning('请选择处理方式'); return }
  processLoading.value = true
  try {
    const body = { disposalType: processForm.disposalType, reason: processForm.reason }
    if (processForm.disposalType === 'rework') {
      body.steps = reworkSteps.value
        .filter(s => s.selected)
        .map(s => ({ processTaskId: s.id, stepName: s.stepName, assignedUserId: s.assignedUserId }))
      if (!body.steps.length) { ElMessage.warning('请至少选择一道返修工序'); processLoading.value = false; return }
    }
    await processQcDisposal(processRow.value.id, body)
    ElMessage.success('处理成功')
    processVisible.value = false
    loadData()
  } catch {} finally { processLoading.value = false }
}

const detailVisible = ref(false)
const detailData = ref(null)
async function openDetail(row) {
  detailVisible.value = true
  try {
    const res = await getQcDisposalDetail(row.id)
    detailData.value = res.data
  } catch {}
}

onMounted(() => loadData())
</script>

<style scoped lang="scss">
.page-container { display: flex; flex-direction: column; gap: 12px; }
.search-card { :deep(.el-card__body) { padding-bottom: 2px; } }
.table-card { flex: 1; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 12px; }
</style>
