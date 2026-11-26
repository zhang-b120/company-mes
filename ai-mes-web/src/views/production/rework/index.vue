<template>
  <div class="page-container">
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="返修单号">
          <el-input v-model="queryParams.reworkNo" placeholder="返修单号" clearable style="width: 150px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="工单号">
          <el-input v-model="queryParams.workOrderNo" placeholder="工单号" clearable style="width: 150px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="质检单号">
          <el-input v-model="queryParams.qcTaskNo" placeholder="质检单号" clearable style="width: 150px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="物料编号">
          <el-input v-model="queryParams.materialNo" placeholder="物料编号" clearable style="width: 140px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="物料名称">
          <el-input v-model="queryParams.materialName" placeholder="物料名称" clearable style="width: 140px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="规格型号">
          <el-input v-model="queryParams.spec" placeholder="规格型号" clearable style="width: 140px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="待处理" value="pending" />
            <el-option label="进行中" value="in_progress" />
            <el-option label="已完成" value="completed" />
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
        <el-table-column prop="reworkNo" label="返修单号" min-width="140" show-overflow-tooltip />
        <el-table-column label="工单号" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button v-if="row.woNo" type="primary" link @click="openWoDetail(row.workOrderId)">{{ row.woNo }}</el-button>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="质检单号" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button v-if="row.qcTaskNo" type="primary" link @click="openQcDetail(row.qcTaskId)">{{ row.qcTaskNo }}</el-button>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="materialNo" label="物料编码" min-width="120" show-overflow-tooltip />
        <el-table-column prop="materialName" label="物料名称" min-width="130" show-overflow-tooltip />
        <el-table-column prop="spec" label="规格型号" min-width="100" show-overflow-tooltip>
          <template #default="{ row }">{{ $fmtSpec(row.spec) || '-' }}</template>
        </el-table-column>
        <el-table-column prop="quantity" label="返修数量" width="90" align="right" />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag[row.status]" size="small">{{ statusText[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="160">
          <template #default="{ row }">{{ $fmtDate(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.status !== 'completed'" type="success" link size="small" @click="handleComplete(row)">整单完成</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50]" :total="total" background layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" title="返修工单详情" size="700px" destroy-on-close>
      <template v-if="detailData">
        <el-descriptions :column="2" border size="small" style="margin-bottom: 16px">
          <el-descriptions-item label="返修单号">{{ detailData.reworkNo }}</el-descriptions-item>
          <el-descriptions-item label="工单号">{{ detailData.woNo }}</el-descriptions-item>
          <el-descriptions-item label="物料">{{ detailData.materialName }}</el-descriptions-item>
          <el-descriptions-item label="返修数量">{{ detailData.quantity }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTag[detailData.status]" size="small">{{ statusText[detailData.status] }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <div style="font-weight: 600; margin-bottom: 8px">返修工序</div>
        <el-table :data="detailData.steps || []" border stripe size="small">
          <el-table-column prop="sortOrder" label="序号" width="60" align="center" />
          <el-table-column prop="stepName" label="工序名称" min-width="130" />
          <el-table-column prop="assignedUserName" label="指定报工人" min-width="100" />
          <el-table-column prop="status" label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.status === 'completed'" type="success" size="small">已完成</el-tag>
              <el-tag v-else-if="row.status === 'qc_pending'" type="warning" size="small">待质检</el-tag>
              <el-tag v-else type="info" size="small">待完成</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140" align="center">
            <template #default="{ row }">
              <el-button v-if="row.status === 'pending' && detailData.status !== 'completed'"
                type="success" link size="small" @click="handleCompleteStep(row)">完成</el-button>
              <el-button v-if="row.status === 'qc_pending' && row.qcTaskId"
                type="warning" link size="small" @click="openQcDetail(row.qcTaskId)">查看质检</el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-drawer>

    <!-- 工单详情弹窗 -->
    <el-dialog v-model="woDetailVisible" title="生产工单详情" width="750px" destroy-on-close>
      <div v-loading="woDetailLoading">
        <template v-if="woDetailData">
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item label="工单号">{{ woDetailData.woNo }}</el-descriptions-item>
            <el-descriptions-item label="产品名称">{{ woDetailData.productName }}</el-descriptions-item>
            <el-descriptions-item label="产品编码">{{ woDetailData.productNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="规格型号">{{ $fmtSpec(woDetailData.productSpec) || '-' }}</el-descriptions-item>
            <el-descriptions-item label="计划数量">{{ woDetailData.planQty }}</el-descriptions-item>
            <el-descriptions-item label="完工数量">{{ woDetailData.completedQty || 0 }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="{ pending: 'info', scheduled: 'warning', in_progress: '', completed: 'success', cancelled: 'danger' }[woDetailData.status]" size="small">
                {{ { pending: '待排产', scheduled: '已排产', in_progress: '生产中', completed: '已完工', cancelled: '已取消' }[woDetailData.status] || woDetailData.status }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="计划开始">{{ woDetailData.planStartDate || '-' }}</el-descriptions-item>
            <el-descriptions-item label="计划结束">{{ woDetailData.planEndDate || '-' }}</el-descriptions-item>
            <el-descriptions-item label="备注" :span="3">{{ woDetailData.remark || '-' }}</el-descriptions-item>
          </el-descriptions>
          <!-- 工序列表 -->
          <div v-if="woDetailData.tasks && woDetailData.tasks.length" style="margin-top: 16px">
            <h4 style="font-size:14px;margin-bottom:8px">工艺路线</h4>
            <el-table :data="woDetailData.tasks" border stripe size="small">
              <el-table-column prop="stepNo" label="序号" width="60" align="center" />
              <el-table-column prop="stepName" label="工序名称" min-width="120" />
              <el-table-column prop="status" label="状态" width="90" align="center">
                <template #default="{ row }">
                  <el-tag :type="{ ready: 'info', in_progress: '', pending_qc: 'warning', completed: 'success', blocked: 'danger' }[row.status]" size="small">
                    {{ { ready: '就绪', in_progress: '进行中', pending_qc: '待质检', completed: '已完成', blocked: '已阻断' }[row.status] || row.status }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="planQty" label="计划数" width="80" align="right" />
              <el-table-column prop="qualifiedQty" label="合格数" width="80" align="right" />
              <el-table-column prop="defectQty" label="不良数" width="80" align="right" />
            </el-table>
          </div>
        </template>
      </div>
    </el-dialog>

    <!-- 质检详情弹窗 -->
    <el-dialog v-model="qcDetailVisible" title="质检任务详情" width="700px" destroy-on-close>
      <div v-loading="qcDetailLoading">
        <template v-if="qcDetailData">
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item label="任务编号">{{ qcDetailData.taskNo }}</el-descriptions-item>
            <el-descriptions-item label="检验类型">
              <el-tag :type="{ incoming: '', process: 'success', outsource: 'warning' }[qcDetailData.qcType]" size="small">
                {{ { incoming: '来料检', process: '过程检', outsource: '外协检' }[qcDetailData.qcType] || qcDetailData.qcType }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="{ pending: 'info', inspecting: '', completed: 'success' }[qcDetailData.result]" size="small">
                {{ { pending: '待检', inspecting: '检验中', completed: '检测完成' }[qcDetailData.result] || qcDetailData.result }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="产品名称">{{ qcDetailData.productName || qcDetailData.materialName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="工序">{{ qcDetailData.stepName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="检验模板">{{ qcDetailData.templateName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="总数量">{{ qcDetailData.planQty }}</el-descriptions-item>
            <el-descriptions-item label="质检数量">{{ qcDetailData.sampleQty || qcDetailData.planQty }}</el-descriptions-item>
            <el-descriptions-item label="批次号">{{ qcDetailData.batchNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="合格数">
              <span style="color:#67c23a;font-weight:600">{{ qcDetailData.passQty || 0 }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="不合格数">
              <span style="color:#f56c6c;font-weight:600">{{ qcDetailData.failQty || 0 }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="处置状态">
              <el-tag v-if="qcDetailData.disposalStatus" :type="qcDetailData.disposalStatus === 'disposed' ? 'success' : 'warning'" size="small">
                {{ qcDetailData.disposalStatus === 'disposed' ? '已处置' : '未处置' }}
              </el-tag>
              <span v-else>-</span>
            </el-descriptions-item>
            <el-descriptions-item label="检验员">{{ qcDetailData.inspectorName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="检验时间">{{ $fmtDate(qcDetailData.inspectedAt) }}</el-descriptions-item>
            <el-descriptions-item label="结论">{{ qcDetailData.conclusion || '-' }}</el-descriptions-item>
          </el-descriptions>
          <!-- 检验记录 -->
          <div v-if="qcDetailData.records && qcDetailData.records.length" style="margin-top:16px">
            <h4 style="font-size:14px;margin-bottom:8px">检验记录</h4>
            <el-table :data="qcDetailData.records" border stripe size="small">
              <el-table-column label="类型" width="90" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.recordType === 'unqualified' ? 'danger' : 'success'" size="small">
                    {{ row.recordType === 'unqualified' ? '不合格' : '合格' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="quantity" label="数量" width="100" align="right" />
              <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />
            </el-table>
          </div>
          <!-- 处置记录 -->
          <div v-if="qcDetailData.disposalRecords && qcDetailData.disposalRecords.length" style="margin-top:16px">
            <h4 style="font-size:14px;margin-bottom:8px">处置记录</h4>
            <el-table :data="qcDetailData.disposalRecords" border stripe size="small">
              <el-table-column label="处置方式" width="100" align="center">
                <template #default="{ row }">
                  <el-tag :type="{ repair: 'primary', concession: 'warning', scrap: 'danger' }[row.disposalType]" size="small">
                    {{ { repair: '返修', concession: '让步接收', scrap: '报废' }[row.disposalType] || row.disposalType }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="quantity" label="处置数量" width="90" align="right" />
              <el-table-column prop="disposalReason" label="处置原因" min-width="150" show-overflow-tooltip />
              <el-table-column label="状态" width="80" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.status === 'processed' ? 'success' : 'info'" size="small">{{ row.status === 'processed' ? '已处置' : '待处置' }}</el-tag>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </template>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getReworkOrderPage, getReworkOrderDetail, completeReworkStep, completeReworkOrder, getWorkOrderDetail } from '@/api/production'
import { getQcTaskDetail } from '@/api/quality'

const statusText = { pending: '待处理', in_progress: '进行中', completed: '已完成' }
const statusTag = { pending: 'info', in_progress: 'warning', completed: 'success' }

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const queryParams = reactive({ reworkNo: '', workOrderNo: '', qcTaskNo: '', materialNo: '', materialName: '', spec: '', status: '', pageNum: 1, pageSize: 20 })

async function loadData() {
  loading.value = true
  try {
    const res = await getReworkOrderPage(queryParams)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch {} finally { loading.value = false }
}

function handleSearch() { queryParams.pageNum = 1; loadData() }
function handleReset() {
  queryParams.reworkNo = ''
  queryParams.workOrderNo = ''
  queryParams.qcTaskNo = ''
  queryParams.materialNo = ''
  queryParams.materialName = ''
  queryParams.spec = ''
  queryParams.status = ''
  queryParams.pageNum = 1
  loadData()
}

const detailVisible = ref(false)
const detailData = ref(null)

async function openDetail(row) {
  detailVisible.value = true
  try {
    const res = await getReworkOrderDetail(row.id)
    detailData.value = res.data
  } catch {}
}

async function handleCompleteStep(step) {
  try {
    await completeReworkStep(detailData.value.id, { stepId: step.id })
    ElMessage.success('工序完成')
    const res = await getReworkOrderDetail(detailData.value.id)
    detailData.value = res.data
    loadData()
  } catch {}
}

async function handleComplete(row) {
  try {
    await ElMessageBox.confirm(`确定整单完成返修工单「${row.reworkNo}」？完成后将自动恢复工单合格数量。`, '提示', { type: 'warning' })
    await completeReworkOrder(row.id)
    ElMessage.success('返修完成，合格数量已恢复')
    loadData()
  } catch {}
}

// 工单详情
const woDetailVisible = ref(false)
const woDetailLoading = ref(false)
const woDetailData = ref(null)
async function openWoDetail(woId) {
  if (!woId) return
  woDetailVisible.value = true
  woDetailLoading.value = true
  try {
    const res = await getWorkOrderDetail(woId)
    woDetailData.value = res.data
  } catch {} finally { woDetailLoading.value = false }
}

// 质检详情
const qcDetailVisible = ref(false)
const qcDetailLoading = ref(false)
const qcDetailData = ref(null)
async function openQcDetail(qcId) {
  if (!qcId) return
  qcDetailVisible.value = true
  qcDetailLoading.value = true
  try {
    const res = await getQcTaskDetail(qcId)
    qcDetailData.value = res.data
  } catch {} finally { qcDetailLoading.value = false }
}

onMounted(() => loadData())
</script>

<style scoped lang="scss">
.page-container { display: flex; flex-direction: column; gap: 12px; }
.search-card { :deep(.el-card__body) { padding-bottom: 2px; } }
.table-card { flex: 1; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 12px; }
</style>
