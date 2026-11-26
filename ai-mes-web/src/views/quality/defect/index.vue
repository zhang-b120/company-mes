<template>
  <div class="page-container">
    <!-- 统计卡片 -->
    <el-row :gutter="16" style="margin-bottom: 16px">
      <el-col :span="6" v-for="s in statCards" :key="s.key">
        <div class="stat-card" @click="s.filterStatus && filterByStatus(s.filterStatus)">
          <el-icon class="icon" :style="{ color: s.color }"><component :is="s.icon" /></el-icon>
          <div class="info">
            <div class="label">{{ s.label }}</div>
            <div class="value" :style="{ color: s.color }">{{ stats[s.key] || 0 }}</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 搜索栏 -->
    <el-card shadow="never" style="margin-bottom: 12px">
      <el-form :model="queryParams" inline>
        <el-form-item label="缺陷编号">
          <el-input v-model="queryParams.defectNo" placeholder="缺陷编号" clearable style="width: 150px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="批次号">
          <el-input v-model="queryParams.batchNo" placeholder="批次号" clearable style="width: 150px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.handleStatus" placeholder="全部" clearable style="width: 130px" @change="handleSearch">
            <el-option label="待处理" value="pending" />
            <el-option label="处理中" value="handling" />
            <el-option label="已处理" value="completed" />
          </el-select>
        </el-form-item>
        <el-form-item label="不良类型">
          <el-select v-model="queryParams.defectType" placeholder="全部" clearable style="width: 130px" @change="handleSearch">
            <el-option v-for="t in defectTypeOptions" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="等级">
          <el-select v-model="queryParams.defectLevel" placeholder="全部" clearable style="width: 110px">
            <el-option label="轻微" value="MINOR" />
            <el-option label="严重" value="MAJOR" />
            <el-option label="致命" value="CRITICAL" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="primary" plain @click="handleAdd">新增记录</el-button>
          <el-button :icon="Setting" @click="colConfigDialogRef.open()">列配置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据表格 -->
    <el-card shadow="never">
      <el-table :data="tableData" v-loading="loading" border stripe size="small">
        <el-table-column v-for="col in visibleColumns" :key="col.key"
          :label="col.label" :min-width="col.minWidth || 100" :align="col.align || 'left'" show-overflow-tooltip>
          <template #default="{ row }">
            <template v-if="col.key === 'defectNo'">
              <el-button type="primary" link @click="openDetail(row)">{{ row.defectNo }}</el-button>
            </template>
            <template v-else-if="col.key === 'defectLevel'">
              <el-tag v-if="row.defectLevel" :type="levelTag[row.defectLevel] || 'info'" size="small">{{ levelMap[row.defectLevel] || row.defectLevel }}</el-tag>
              <span v-else style="color: #c0c4cc">-</span>
            </template>
            <template v-else-if="col.key === 'quantity'">
              <span style="color: #f56c6c; font-weight: 500">{{ row.quantity }}</span>
            </template>
            <template v-else-if="col.key === 'handleResult'">
              <el-tag v-if="row.handleResult" :type="resultTag[row.handleResultCode] || resultTag[row.handlePath] || 'info'" size="small">
                {{ row.handleResult }}
              </el-tag>
              <span v-else style="color: #c0c4cc">-</span>
            </template>
            <template v-else-if="col.key === 'status'">
              <el-tag :type="statusTag[row.handleStatus] || 'info'" size="small">{{ statusMap[row.handleStatus] || row.handleStatus || '-' }}</el-tag>
            </template>
            <template v-else>{{ row[col.key] || '-' }}</template>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.handleStatus === 'pending' || row.handleStatus === 'quarantined' || row.handleStatus === 'handling'" type="warning" link size="small"
              @click="openHandleDialog(row)">处理</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
          :total="total" :page-sizes="[10, 20, 50]" background layout="total, sizes, prev, pager, next"
          @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" :title="`不良品详情 ${detailData?.defectNo || ''}`" size="650px">
      <template v-if="detailData">
        <el-descriptions :column="2" border size="small" style="margin-bottom: 16px">
          <el-descriptions-item label="不良编号">{{ detailData.defectNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTag[detailData.handleStatus]">{{ statusMap[detailData.handleStatus] }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="工单号">{{ detailData.workOrderNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="产品">{{ detailData.productName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="工序">{{ detailData.stepName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="不良类型">{{ detailData.defectType }}</el-descriptions-item>
          <el-descriptions-item label="不良等级">
            <el-tag :type="levelTag[detailData.defectLevel]" size="small">{{ levelMap[detailData.defectLevel] }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="不良数量">
            <span style="color: #f56c6c; font-weight: 600; font-size: 16px">{{ detailData.quantity }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="来源">{{ sourceMap[detailData.sourceType] || detailData.sourceType || '手动录入' }}</el-descriptions-item>
          <el-descriptions-item label="发现时间">{{ detailData.createTime }}</el-descriptions-item>
          <el-descriptions-item label="不良原因" :span="2">{{ detailData.defectReason || detailData.remark || '-' }}</el-descriptions-item>
        </el-descriptions>

        <!-- 不良品照片 -->
        <div v-if="detailData.images?.length" style="margin-bottom: 16px">
          <h4 style="margin-bottom: 8px; color: #303133">不良品照片</h4>
          <div style="display: flex; gap: 8px; flex-wrap: wrap">
            <el-image v-for="(img, i) in detailData.images" :key="i" :src="img" :preview-src-list="detailData.images"
              fit="cover" style="width: 100px; height: 100px; border-radius: 4px" />
          </div>
        </div>

        <!-- 处理信息 -->
        <el-divider content-position="left">处理信息</el-divider>
        <template v-if="detailData.handlePath || detailData.handleResult">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="处理方式">
              <el-tag :type="resultTag[detailData.handleResultCode] || resultTag[detailData.handlePath] || 'info'">{{ detailData.handleResult || '-' }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="处理人">{{ detailData.handler || '-' }}</el-descriptions-item>
            <el-descriptions-item label="处理时间">{{ detailData.handleTime || '-' }}</el-descriptions-item>
            <el-descriptions-item label="处理数量">{{ detailData.handleQty || detailData.quantity }}</el-descriptions-item>
            <el-descriptions-item label="处理说明" :span="2">{{ detailData.handleRemark || '-' }}</el-descriptions-item>
          </el-descriptions>

          <!-- 处理结果详情 -->
          <div style="margin-top: 12px; padding: 12px; background: #f5f7fa; border-radius: 4px">
            <template v-if="['REWORK','repair','rework'].includes(detailData.handlePath || detailData.handleResultCode)">
              <p><strong>返工工序：</strong>{{ detailData.reworkStepName || detailData.reworkStep || '原工序' }}</p>
              <p><strong>返工要求：</strong>{{ detailData.reworkRequirement || '-' }}</p>
              <p><strong>预计完成：</strong>{{ detailData.reworkDeadline || '-' }}</p>
            </template>
            <template v-else-if="['SCRAP','scrap'].includes(detailData.handlePath || detailData.handleResultCode)">
              <p><strong>报废原因：</strong>{{ detailData.scrapReason || '-' }}</p>
              <p><strong>报废成本：</strong>¥{{ Number(detailData.scrapCost || 0).toFixed(2) }}</p>
            </template>
            <template v-else-if="['CONCESSION','concession'].includes(detailData.handlePath || detailData.handleResultCode)">
              <p><strong>让步条件：</strong>{{ detailData.concessionCondition || '-' }}</p>
              <p><strong>接收审批人：</strong>{{ detailData.concessionApprover || '-' }}</p>
            </template>
            <template v-else-if="['RETURN','return','return_material'].includes(detailData.handlePath || detailData.handleResultCode)">
              <p><strong>退货供应商：</strong>{{ detailData.returnSupplier || '-' }}</p>
              <p><strong>退货单号：</strong>{{ detailData.returnOrderNo || '-' }}</p>
            </template>
          </div>
        </template>
        <el-empty v-else description="暂未处理" />

        <div v-if="detailData.handleStatus !== 'completed' && detailData.handleStatus !== 'handled'" style="margin-top: 16px">
          <el-button type="warning" @click="openHandleDialog(detailData); detailVisible = false">立即处理</el-button>
        </div>
      </template>
    </el-drawer>

    <!-- 新增不良记录 -->
    <el-dialog v-model="addDialogVisible" title="新增不良记录" width="600px" destroy-on-close>
      <el-form ref="addFormRef" :model="addForm" :rules="addRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="工单号" prop="workOrderNo">
              <el-input v-model="addForm.workOrderNo" placeholder="请输入工单号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工序名称" prop="stepName">
              <el-input v-model="addForm.stepName" placeholder="请输入工序名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="不良类型" prop="defectType">
              <el-select v-model="addForm.defectType" placeholder="请选择" style="width: 100%">
                <el-option v-for="t in defectTypes" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="不良等级" prop="defectLevel">
              <el-select v-model="addForm.defectLevel" placeholder="请选择" style="width: 100%">
                <el-option label="轻微" value="MINOR" />
                <el-option label="严重" value="MAJOR" />
                <el-option label="致命" value="CRITICAL" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="数量" prop="quantity">
          <el-input-number v-model="addForm.quantity" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="不良原因" prop="defectReason">
          <el-input v-model="addForm.defectReason" type="textarea" :rows="2" placeholder="请描述不良原因" />
        </el-form-item>
        <el-form-item label="不良照片">
          <AttachmentUploader v-model="addForm.images" list-type="picture-card" accept="image/*"
            button-text="上传照片" tip="仅支持图片,最多 5 张,单张不超过 50MB" :limit="5" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="addForm.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleAddSubmit">确定</el-button>
      </template>
    </el-dialog>

    <ColumnConfigDialog ref="colConfigDialogRef" :all-columns="allColumns" :default-columns="QUALITY_DEFECT_DEFAULT_COLUMNS" @save="saveConfig" />

    <!-- 不良品处理弹窗 -->
    <el-dialog v-model="handleDialogVisible" title="不良品处理" width="640px" destroy-on-close>
      <el-descriptions :column="3" border size="small" style="margin-bottom: 16px">
        <el-descriptions-item label="不良编号">{{ handleForm.defectNo }}</el-descriptions-item>
        <el-descriptions-item label="不良类型">{{ handleForm.defectType }}</el-descriptions-item>
        <el-descriptions-item label="数量">{{ handleForm.quantity }}</el-descriptions-item>
      </el-descriptions>

      <el-form ref="handleFormRef" :model="handleForm" :rules="handleRules" label-width="100px">
        <!-- 处理路径选择 -->
        <el-form-item label="处理方式" prop="handleResult">
          <el-radio-group v-model="handleForm.handleResult" size="large">
            <el-radio-button v-for="opt in disposalOptions" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>

        <!-- 返工表单 -->
        <template v-if="handleForm.handleResult === 'REWORK'">
          <el-alert type="info" :closable="false" style="margin-bottom: 12px">
            将不良品退回指定工序重新加工，返工完成后重新进入质检流程。
          </el-alert>
          <el-form-item label="返工工序">
            <el-input v-model="handleForm.reworkStep" placeholder="返回哪个工序重做" />
          </el-form-item>
          <el-form-item label="返工要求">
            <el-input v-model="handleForm.reworkRequirement" type="textarea" :rows="2" placeholder="描述返工要求" />
          </el-form-item>
          <el-form-item label="预计完成">
            <el-date-picker v-model="handleForm.reworkDeadline" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
          </el-form-item>
        </template>

        <!-- 报废表单 -->
        <template v-if="handleForm.handleResult === 'SCRAP'">
          <el-alert type="warning" :closable="false" style="margin-bottom: 12px">
            不良品将进行报废处理，相关成本将计入工单成本。
          </el-alert>
          <el-form-item label="报废数量">
            <el-input-number v-model="handleForm.scrapQty" :min="1" :max="handleForm.quantity" style="width: 100%" />
          </el-form-item>
          <el-form-item label="报废原因">
            <el-select v-model="handleForm.scrapReason" placeholder="请选择" style="width: 100%">
              <el-option label="无法修复" value="irreparable" />
              <el-option label="材料缺陷" value="material_defect" />
              <el-option label="工艺失误" value="process_error" />
              <el-option label="其他" value="other" />
            </el-select>
          </el-form-item>
          <el-form-item label="报废成本">
            <el-input-number v-model="handleForm.scrapCost" :min="0" :precision="2" style="width: 100%" placeholder="预估报废损失" />
          </el-form-item>
        </template>

        <!-- 让步接收表单 -->
        <template v-if="handleForm.handleResult === 'CONCESSION'">
          <el-alert type="success" :closable="false" style="margin-bottom: 12px">
            产品虽有缺陷但在可接受范围内，经审批后放行入库。
          </el-alert>
          <el-form-item label="让步条件">
            <el-input v-model="handleForm.concessionCondition" type="textarea" :rows="2" placeholder="描述可接受的缺陷范围" />
          </el-form-item>
          <el-form-item label="接收数量">
            <el-input-number v-model="handleForm.concessionQty" :min="1" :max="handleForm.quantity" style="width: 100%" />
          </el-form-item>
          <el-form-item label="审批人">
            <el-input v-model="handleForm.concessionApprover" placeholder="输入审批人姓名" />
          </el-form-item>
        </template>

        <!-- 退货表单 -->
        <template v-if="handleForm.handleResult === 'RETURN'">
          <el-alert type="error" :closable="false" style="margin-bottom: 12px">
            将不良品退回供应商，需要填写退货信息以便跟踪。
          </el-alert>
          <el-form-item label="退货供应商">
            <el-input v-model="handleForm.returnSupplier" placeholder="退回哪个供应商" />
          </el-form-item>
          <el-form-item label="退货数量">
            <el-input-number v-model="handleForm.returnQty" :min="1" :max="handleForm.quantity" style="width: 100%" />
          </el-form-item>
          <el-form-item label="退货原因">
            <el-input v-model="handleForm.returnReason" type="textarea" :rows="2" placeholder="描述退货原因" />
          </el-form-item>
        </template>

        <el-form-item label="处理说明" prop="handleRemark">
          <el-input v-model="handleForm.handleRemark" type="textarea" :rows="3" placeholder="请输入处理说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleProcessSubmit">确认处理</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Document, WarningFilled, Loading, CircleCheck, Setting } from '@element-plus/icons-vue'
import AttachmentUploader from '@/components/AttachmentUploader.vue'
import { getDefectPage, createDefect, handleDefect, getDefectStats } from '@/api/quality'
import { useColumnConfig } from '@/composables/useColumnConfig'
import ColumnConfigDialog from '@/components/ColumnConfigDialog.vue'

const QUALITY_DEFECT_DEFAULT_COLUMNS = [
  { key: 'defectNo', label: '不良编号', minWidth: 160 },
  { key: 'workOrderNo', label: '工单号', minWidth: 160 },
  { key: 'productName', label: '产品', minWidth: 120 },
  { key: 'stepName', label: '工序', minWidth: 100 },
  { key: 'defectType', label: '不良类型', minWidth: 100 },
  { key: 'defectLevel', label: '等级', minWidth: 80, align: 'center' },
  { key: 'quantity', label: '数量', minWidth: 70, align: 'right' },
  { key: 'handleResult', label: '处理方式', minWidth: 100, align: 'center' },
  { key: 'status', label: '状态', minWidth: 80, align: 'center' },
  { key: 'createTime', label: '发现时间', minWidth: 165 },
]
const colConfigDialogRef = ref(null)
const { visibleColumns, allColumns, loadColumnConfig, saveConfig } = useColumnConfig('quality_defect', QUALITY_DEFECT_DEFAULT_COLUMNS)

const defectTypes = ['外观不良', '尺寸不良', '功能不良', '材料不良', '装配不良', '性能不良', '其他']
const defectTypeOptions = [
  { label: '尺寸偏差', value: 'size' }, { label: '外观缺陷', value: 'appearance' },
  { label: '功能异常', value: 'function' }, { label: '材料问题', value: 'material' },
  { label: '工艺问题', value: 'process' }, { label: '装配问题', value: 'assembly' },
  { label: '质检不合格', value: 'qc_fail' }, { label: '来料不良', value: 'incoming' },
  { label: '成品不良', value: 'final' }, { label: '外协不良', value: 'outsource' },
  { label: '其他', value: 'other' }
]
const levelMap = { MINOR: '轻微', MAJOR: '严重', CRITICAL: '致命' }
const levelTag = { MINOR: 'warning', MAJOR: 'danger', CRITICAL: 'danger' }
const statusMap = { pending: '待处理', quarantined: '待处理', handling: '处理中', completed: '已处理', handled: '已处理',
  PENDING: '待处理', HANDLING: '处理中', CLOSED: '已关闭' }
const statusTag = { pending: 'warning', quarantined: 'warning', handling: '', completed: 'success', handled: 'success',
  PENDING: 'warning', HANDLING: '', CLOSED: 'success' }
const resultMap = { REWORK: '返工', SCRAP: '报废', CONCESSION: '让步接收', RETURN: '退货',
  repair: '返工', rework: '返工', scrap: '报废', concession: '让步接收', return_material: '退货' }
// 给"处理方式"按钮组用的去重选项(只 4 项)
const disposalOptions = [
  { value: 'REWORK', label: '返工' },
  { value: 'SCRAP', label: '报废' },
  { value: 'CONCESSION', label: '让步接收' },
  { value: 'RETURN', label: '退货' }
]
const resultTag = { REWORK: 'warning', SCRAP: 'danger', CONCESSION: 'success', RETURN: 'info',
  repair: 'warning', rework: 'warning', scrap: 'danger', concession: 'success', return_material: 'info' }
const sourceMap = { work_report: '报工', qc_task: '质检', manual: '手动录入' }

const statCards = [
  { key: 'total', label: '不良品记录', color: '#409eff', icon: Document },
  { key: 'pending', label: '待处理', color: '#f56c6c', icon: WarningFilled, filterStatus: 'pending' },
  { key: 'handling', label: '处理中', color: '#e6a23c', icon: Loading, filterStatus: 'handling' },
  { key: 'closed', label: '已处理', color: '#67c23a', icon: CircleCheck, filterStatus: 'completed' }
]
const stats = reactive({ total: 0, pending: 0, handling: 0, closed: 0 })

function filterByStatus(status) { queryParams.handleStatus = status; queryParams.pageNum = 1; loadData() }

// 查询
const queryParams = reactive({ defectNo: '', batchNo: '', handleStatus: '', defectType: '', defectLevel: '', pageNum: 1, pageSize: 10 })
const loading = ref(false); const tableData = ref([]); const total = ref(0); const submitLoading = ref(false)

async function loadData() {
  loading.value = true
  try {
    const res = await getDefectPage({
      defectNo: queryParams.defectNo || undefined,
      batchNo: queryParams.batchNo || undefined,
      handleStatus: queryParams.handleStatus || undefined,
      defectType: queryParams.defectType || undefined,
      defectLevel: queryParams.defectLevel || undefined,
      pageNum: queryParams.pageNum,
      pageSize: queryParams.pageSize
    })
    tableData.value = res.data?.records || []; total.value = res.data?.total || 0
  } catch {} finally { loading.value = false }
}

function handleSearch() { queryParams.pageNum = 1; loadData() }
function handleReset() {
  queryParams.defectNo = ''; queryParams.batchNo = ''; queryParams.handleStatus = ''; queryParams.defectType = ''; queryParams.defectLevel = ''
  queryParams.pageNum = 1; loadData()
}

async function loadStats() {
  try {
    const res = await getDefectStats()
    const d = res.data || {}
    stats.total = d.total || 0; stats.pending = d.pending || 0
    stats.handling = d.handling || 0; stats.closed = d.closed || 0
  } catch {}
}

// 详情抽屉
const detailVisible = ref(false); const detailData = ref(null)
function openDetail(row) { detailData.value = row; detailVisible.value = true }

// 新增
const addDialogVisible = ref(false); const addFormRef = ref(null)
const addForm = reactive({
  workOrderNo: '', stepName: '', defectType: '', defectLevel: '', quantity: 1,
  defectReason: '', images: [], remark: ''
})
const addRules = {
  workOrderNo: [{ required: true, message: '请输入工单号', trigger: 'blur' }],
  defectType: [{ required: true, message: '请选择不良类型', trigger: 'change' }],
  defectLevel: [{ required: true, message: '请选择等级', trigger: 'change' }],
  quantity: [{ required: true, message: '请输入数量', trigger: 'blur' }]
}

function handleAdd() {
  addForm.workOrderNo = ''; addForm.stepName = ''; addForm.defectType = ''
  addForm.defectLevel = ''; addForm.quantity = 1; addForm.defectReason = ''
  addForm.images = []; addForm.remark = ''
  addDialogVisible.value = true
}

async function handleAddSubmit() {
  await addFormRef.value.validate()
  submitLoading.value = true
  try {
    // AttachmentUploader 数组模型返回 [{name,url}],后端仍按 URL 字符串数组存储,做一次转换
    const payload = { ...addForm }
    payload.images = (addForm.images || []).map(it => typeof it === 'string' ? it : it?.url).filter(Boolean)
    await createDefect(payload)
    ElMessage.success('创建成功'); addDialogVisible.value = false; loadData()
  } finally { submitLoading.value = false }
}

// 处理弹窗
const handleDialogVisible = ref(false); const handleFormRef = ref(null)
const handleForm = reactive({
  defectId: undefined, defectNo: '', defectType: '', quantity: 0,
  handleResult: '', handleRemark: '',
  reworkStep: '', reworkRequirement: '', reworkDeadline: '',
  scrapQty: 0, scrapReason: '', scrapCost: 0,
  concessionCondition: '', concessionQty: 0, concessionApprover: '',
  returnSupplier: '', returnQty: 0, returnReason: ''
})
const handleRules = {
  handleResult: [{ required: true, message: '请选择处理方式', trigger: 'change' }],
  handleRemark: [{ required: true, message: '请输入处理说明', trigger: 'blur' }]
}

function openHandleDialog(row) {
  handleForm.defectId = row.id; handleForm.defectNo = row.defectNo
  handleForm.defectType = row.defectType; handleForm.quantity = row.quantity
  handleForm.handleResult = ''; handleForm.handleRemark = ''
  handleForm.reworkStep = ''; handleForm.reworkRequirement = ''; handleForm.reworkDeadline = ''
  handleForm.scrapQty = row.quantity; handleForm.scrapReason = ''; handleForm.scrapCost = 0
  handleForm.concessionCondition = ''; handleForm.concessionQty = row.quantity; handleForm.concessionApprover = ''
  handleForm.returnSupplier = ''; handleForm.returnQty = row.quantity; handleForm.returnReason = ''
  handleDialogVisible.value = true
}

async function handleProcessSubmit() {
  await handleFormRef.value.validate()
  submitLoading.value = true
  try {
    const data = { ...handleForm, handlePath: handleForm.handleResult }
    await handleDefect(handleForm.defectId, data)
    ElMessage.success('处理成功'); handleDialogVisible.value = false; loadData(); loadStats()
  } finally { submitLoading.value = false }
}

onMounted(() => { loadData(); loadStats(); loadColumnConfig() })
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
</style>
