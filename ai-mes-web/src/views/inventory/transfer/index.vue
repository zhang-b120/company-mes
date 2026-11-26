<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="调拨单号">
          <el-input v-model="queryParams.transferNo" placeholder="调拨单号" clearable style="width:150px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="源仓库">
          <el-select v-model="queryParams.fromWarehouseId" placeholder="全部" clearable style="width:130px">
            <el-option v-for="w in warehouseOptions" :key="w.id" :label="w.name" :value="w.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标仓库">
          <el-select v-model="queryParams.toWarehouseId" placeholder="全部" clearable style="width:130px">
            <el-option v-for="w in warehouseOptions" :key="w.id" :label="w.name" :value="w.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="申请人">
          <el-select v-model="queryParams.applicantId" placeholder="全部" clearable filterable style="width:130px">
            <el-option v-for="u in userOptions" :key="u.id" :label="u.realName || u.username" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width:110px">
            <el-option label="待审批" value="pending" />
            <el-option label="已完成" value="completed" />
            <el-option label="已驳回" value="rejected" />
          </el-select>
        </el-form-item>
        <el-form-item label="创建时间">
          <el-date-picker v-model="dateRange" type="daterange" range-separator="至"
            start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD"
            style="width:240px" @change="onDateChange" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="primary" plain @click="openCreate">新增调拨</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据表格 -->
    <el-card shadow="never" class="table-card">
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="transferNo" label="调拨单号" width="180" />
        <el-table-column prop="fromWarehouseName" label="源仓库" min-width="120" />
        <el-table-column prop="toWarehouseName" label="目标仓库" min-width="120" />
        <el-table-column prop="applicantName" label="申请人" width="100" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="160">
          <template #default="{ row }">{{ $fmtDate(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看</el-button>
            <template v-if="row.status === 'pending'">
              <el-button link type="success" @click="handleApprove(row)">审批通过</el-button>
              <el-button link type="danger" @click="openReject(row)">驳回</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
          :page-sizes="[10,20,50,100]" :total="total" layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <!-- 新增调拨弹窗 -->
    <el-dialog v-model="createVisible" title="新增仓库调拨" width="1000px" destroy-on-close>
      <el-form :model="createForm" :rules="createRules" ref="createFormRef" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="源仓库" prop="fromWarehouseId">
              <el-select v-model="createForm.fromWarehouseId" placeholder="请选择源仓库" style="width:100%"
                @change="onFromWarehouseChange">
                <el-option v-for="w in warehouseOptions" :key="w.id" :label="w.name" :value="w.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="源库位">
              <el-select v-model="createForm.fromLocationId" placeholder="可选" clearable style="width:100%">
                <el-option v-for="l in fromLocationOptions" :key="l.id" :label="l.name" :value="l.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="目标仓库" prop="toWarehouseId">
              <el-select v-model="createForm.toWarehouseId" placeholder="请选择目标仓库" style="width:100%"
                @change="onToWarehouseChange">
                <el-option v-for="w in warehouseOptions" :key="w.id" :label="w.name" :value="w.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="目标库位">
              <el-select v-model="createForm.toLocationId" placeholder="可选" clearable style="width:100%">
                <el-option v-for="l in toLocationOptions" :key="l.id" :label="l.name" :value="l.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="createForm.remark" placeholder="请输入备注" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="调拨明细" required>
          <div style="width:100%">
            <el-table :data="createForm.items" border size="small" style="margin-bottom:8px">
              <el-table-column label="物料编号" width="140" show-overflow-tooltip>
                <template #default="{ row }">
                  <span v-if="row.materialNo">{{ row.materialNo }}</span>
                  <span v-else style="color:#c0c4cc">-</span>
                </template>
              </el-table-column>
              <el-table-column label="物料" min-width="180">
                <template #default="{ row }">
                  <div style="display:flex;gap:4px;align-items:center">
                    <el-input
                      :value="row.materialName || ''"
                      placeholder="请选择物料" readonly size="small" style="flex:1" />
                    <el-button size="small" type="primary" plain @click="openMaterialPicker(row)">选</el-button>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="规格" width="100">
                <template #default="{ row }">
                  <span style="color:#606266;font-size:12px">{{ $fmtSpec(row.spec) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="单位" width="60" align="center">
                <template #default="{ row }">{{ row.unit || '-' }}</template>
              </el-table-column>
              <el-table-column label="可用库存" width="90" align="right">
                <template #default="{ row }">
                  <span style="color:#909399;font-size:12px">{{ row.availableQty ?? '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="调拨数量" width="130">
                <template #default="{ row }">
                  <el-input-number v-model="row.quantity" :min="0.001" :max="row.availableQty || undefined" :precision="4" size="small" controls-position="right" style="width:100%" />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="60" align="center">
                <template #default="{ $index }">
                  <el-button link type="danger" size="small" @click="removeItem($index)">删</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-button type="primary" link size="small" @click="addItem">+ 添加明细</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleCreate">提交</el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="调拨单详情" width="800px" destroy-on-close>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="调拨单号">{{ detail.transferNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTagType(detail.status)" size="small">{{ statusLabel(detail.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="源仓库">{{ detail.fromWarehouseName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="源库位">{{ detail.fromLocationName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="目标仓库">{{ detail.toWarehouseName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="目标库位">{{ detail.toLocationName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="申请人">{{ detail.applicantName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ $fmtDate(detail.createTime) }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.approveRemark" label="审批意见" :span="2">{{ detail.approveRemark }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.remark" label="备注" :span="2">{{ detail.remark }}</el-descriptions-item>
      </el-descriptions>
      <div style="margin-top:16px; font-weight:600; margin-bottom:8px">调拨明细</div>
      <el-table :data="detail.items" border size="small">
        <el-table-column prop="materialNo" label="物料编号" width="120" />
        <el-table-column prop="materialName" label="物料名称" min-width="140" show-overflow-tooltip />
        <el-table-column label="规格" width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="60" align="center" />
        <el-table-column prop="quantity" label="调拨数量" width="100" align="right" />
      </el-table>
      <template #footer><el-button @click="detailVisible = false">关闭</el-button></template>
    </el-dialog>

    <!-- 驳回弹窗 -->
    <el-dialog v-model="rejectVisible" title="驳回调拨" width="420px" destroy-on-close>
      <el-form :model="rejectForm" ref="rejectFormRef" label-width="70px">
        <el-form-item label="驳回原因" prop="remark" :rules="[{required:true,message:'请填写驳回原因',trigger:'blur'}]">
          <el-input v-model="rejectForm.remark" type="textarea" :rows="3" placeholder="请输入驳回原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="submitLoading" @click="handleReject">确认驳回</el-button>
      </template>
    </el-dialog>

    <!-- 物料选择: 复用通用组件, 按源仓库库存过滤 -->
    <MaterialPickerDialog v-model="materialPickerVisible" title="选择物料（源仓库库存）"
      :multiple="false" :warehouse-id="createForm.fromWarehouseId" @select="onMaterialSelected" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getTransferPage, getTransferDetail, createTransfer,
  approveTransfer, rejectTransfer
} from '@/api/inventory'
import { getWarehouseSelect, getWarehouseLocationList, getUserSelect } from '@/api/system'
import MaterialPickerDialog from '@/components/MaterialPickerDialog.vue'

function statusLabel(s) {
  return { pending: '待审批', completed: '已完成', rejected: '已驳回' }[s] || s || '-'
}
function statusTagType(s) {
  return { pending: 'warning', completed: 'success', rejected: 'danger' }[s] || 'info'
}

// ==================== 查询 ====================
const queryParams = reactive({ transferNo: '', fromWarehouseId: null, toWarehouseId: null, applicantId: null, status: '', dateStart: '', dateEnd: '', pageNum: 1, pageSize: 20 })
const loading = ref(false)
const dateRange = ref([])
const userOptions = ref([])

function onDateChange(val) {
  queryParams.dateStart = val ? val[0] : ''
  queryParams.dateEnd = val ? val[1] : ''
}
const tableData = ref([])
const total = ref(0)

async function loadData() {
  loading.value = true
  try {
    const res = await getTransferPage(queryParams)
    tableData.value = res.data?.records || res.data?.list || []
    total.value = res.data?.total || 0
  } finally { loading.value = false }
}
function handleSearch() { queryParams.pageNum = 1; loadData() }
function handleReset() {
  Object.assign(queryParams, { transferNo: '', fromWarehouseId: null, toWarehouseId: null, applicantId: null, status: '', dateStart: '', dateEnd: '', pageNum: 1 })
  dateRange.value = []
  loadData()
}

// ==================== 详情 ====================
const detailVisible = ref(false)
const detail = ref({})

async function openDetail(row) {
  const res = await getTransferDetail(row.id)
  detail.value = res.data || {}
  detailVisible.value = true
}

// ==================== 新增 ====================
const createVisible = ref(false)
const submitLoading = ref(false)
const createFormRef = ref(null)
const warehouseOptions = ref([])
const fromLocationOptions = ref([])
const toLocationOptions = ref([])

const createForm = reactive({
  fromWarehouseId: null,
  fromLocationId: null,
  toWarehouseId: null,
  toLocationId: null,
  remark: '',
  items: []
})
const createRules = {
  fromWarehouseId: [{ required: true, message: '请选择源仓库', trigger: 'change' }],
  toWarehouseId: [{ required: true, message: '请选择目标仓库', trigger: 'change' }]
}

function addItem() {
  if (!createForm.fromWarehouseId) {
    ElMessage.warning('请先选择源仓库')
    return
  }
  createForm.items.push({ materialId: null, materialNo: '', materialName: '', spec: '', unit: '', quantity: 1, availableQty: null })
}
function removeItem(idx) { createForm.items.splice(idx, 1) }

const materialPickerVisible = ref(false)
let currentPickerRow = null

function openMaterialPicker(row) {
  if (!createForm.fromWarehouseId) {
    ElMessage.warning('请先选择源仓库')
    return
  }
  currentPickerRow = row
  materialPickerVisible.value = true
}

// 通用 MaterialPickerDialog 在 warehouseId 模式下:
//   - row.id = materialId (组件内部已对齐)
//   - row.availableQty 已经填好
//   - 单选时 @select 接收 [row], 数组长度 1
function onMaterialSelected(rows) {
  if (!currentPickerRow || !rows || !rows.length) return
  const row = rows[0]
  currentPickerRow.materialId = row.materialId || row.id
  currentPickerRow.materialName = row.materialName || ''
  currentPickerRow.spec = row.spec || ''
  currentPickerRow.unit = row.unit || ''
  currentPickerRow.materialNo = row.materialNo || ''
  currentPickerRow.availableQty = Number(row.availableQty || 0)
  materialPickerVisible.value = false
}

async function onFromWarehouseChange(id) {
  createForm.fromLocationId = null
  fromLocationOptions.value = []
  // 源仓库变更时清空已选物料（库存范围已变）
  createForm.items = []
  if (id) {
    try {
      const res = await getWarehouseLocationList(id)
      fromLocationOptions.value = res.data || []
    } catch {}
  }
}
async function onToWarehouseChange(id) {
  createForm.toLocationId = null
  toLocationOptions.value = []
  if (id) {
    try {
      const res = await getWarehouseLocationList(id)
      toLocationOptions.value = res.data || []
    } catch {}
  }
}

function openCreate() {
  Object.assign(createForm, {
    fromWarehouseId: null, fromLocationId: null,
    toWarehouseId: null, toLocationId: null, remark: ''
  })
  createForm.items = []
  fromLocationOptions.value = []
  toLocationOptions.value = []
  createVisible.value = true
}

async function handleCreate() {
  await createFormRef.value.validate()
  if (createForm.fromWarehouseId === createForm.toWarehouseId) {
    ElMessage.warning('源仓库和目标仓库不能相同')
    return
  }
  // 自动过滤掉没选物料的空行 (用户加了行但没选物料), 全部空就提示
  const validItems = createForm.items.filter(i => i.materialId)
  if (validItems.length === 0) { ElMessage.warning('请至少添加一条有效的调拨明细'); return }
  const skipped = createForm.items.length - validItems.length
  if (skipped > 0) ElMessage.info(`已自动忽略 ${skipped} 条未选物料的明细`)
  submitLoading.value = true
  try {
    await createTransfer({ ...createForm, items: validItems })
    ElMessage.success('调拨单创建成功')
    createVisible.value = false
    loadData()
  } finally { submitLoading.value = false }
}

// ==================== 审批 ====================
async function handleApprove(row) {
  await ElMessageBox.confirm('确定审批通过该调拨单？通过后将立即执行库存调拨。', '审批确认', { type: 'warning' })
  try {
    await approveTransfer(row.id, {})
    ElMessage.success('审批通过，库存已调拨')
    loadData()
  } catch {}
}

// ==================== 驳回 ====================
const rejectVisible = ref(false)
const rejectFormRef = ref(null)
const rejectForm = reactive({ id: null, remark: '' })

function openReject(row) {
  rejectForm.id = row.id
  rejectForm.remark = ''
  rejectVisible.value = true
}
async function handleReject() {
  await rejectFormRef.value.validate()
  submitLoading.value = true
  try {
    await rejectTransfer(rejectForm.id, { remark: rejectForm.remark })
    ElMessage.success('已驳回')
    rejectVisible.value = false
    loadData()
  } finally { submitLoading.value = false }
}

// ==================== 初始化 ====================
async function loadOptions() {
  try {
    const [whRes, userRes] = await Promise.all([getWarehouseSelect(), getUserSelect()])
    warehouseOptions.value = whRes.data || []
    userOptions.value = userRes.data || []
  } catch {}
}
onMounted(() => { loadOptions(); loadData() })
</script>

<style scoped lang="scss">
.page-container { padding: 16px; }
.search-card { margin-bottom: 16px; }
.table-card {
  .pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; }
}
:deep(.cell-wrap .cell) {
  white-space: normal;
  word-break: break-all;
  line-height: 1.5;
}
</style>
