<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="盘点单号">
          <el-input v-model="queryParams.stocktakingNo" placeholder="盘点单号" clearable style="width:150px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="盘点仓库">
          <el-select v-model="queryParams.warehouseId" placeholder="全部" clearable style="width:130px">
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
            <el-option label="已通过" value="approved" />
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
          <el-button type="primary" plain @click="openCreate">新增盘点</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据表格 -->
    <el-card shadow="never" class="table-card">
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="stocktakingNo" label="盘点单号" width="200" />
        <el-table-column prop="warehouseName" label="盘点仓库" min-width="140" />
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

    <!-- 新增盘点弹窗 -->
    <el-dialog v-model="createVisible" title="新增仓库盘点" width="1200px" destroy-on-close>
      <el-form :model="createForm" :rules="createRules" ref="createFormRef" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="盘点仓库" prop="warehouseId">
              <el-select v-model="createForm.warehouseId" placeholder="请选择仓库" style="width:100%"
                @change="onWarehouseChange">
                <el-option v-for="w in warehouseOptions" :key="w.id" :label="w.name" :value="w.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="备注">
              <el-input v-model="createForm.remark" placeholder="请输入备注" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="盘点明细" required>
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
              <el-table-column label="单位" width="60" align="center">
                <template #default="{ row }">{{ row.unit || '-' }}</template>
              </el-table-column>
              <el-table-column label="在库数量" width="100" align="right">
                <template #default="{ row }">
                  <span style="color:#909399">{{ row.totalQty ?? '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="锁定数量" width="100" align="right">
                <template #default="{ row }">
                  <span style="color:#e6a23c">{{ row.lockedQty ?? '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="可用数量" width="100" align="right">
                <template #default="{ row }">
                  <span style="color:#67c23a">{{ row.availableQty ?? '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="实盘数量" width="150">
                <template #default="{ row }">
                  <el-input-number v-model="row.actualQty" :min="row.lockedQty ?? 0" :precision="4" size="small"
                    controls-position="right" style="width:100%" @change="calcDiff(row)" />
                </template>
              </el-table-column>
              <el-table-column label="差异" width="100" align="right">
                <template #default="{ row }">
                  <span v-if="row.diffQty != null" :style="{ color: row.diffQty > 0 ? '#67c23a' : row.diffQty < 0 ? '#f56c6c' : '#909399', fontWeight: 600 }">
                    {{ row.diffQty > 0 ? '+' : '' }}{{ row.diffQty }}
                  </span>
                  <span v-else style="color:#909399">-</span>
                </template>
              </el-table-column>
              <el-table-column label="盘点结果" width="90" align="center">
                <template #default="{ row }">
                  <el-tag v-if="row.diffQty > 0" type="success" size="small">盘盈</el-tag>
                  <el-tag v-else-if="row.diffQty < 0" type="danger" size="small">盘亏</el-tag>
                  <el-tag v-else-if="row.diffQty === 0" type="info" size="small">一致</el-tag>
                  <span v-else>-</span>
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
    <el-dialog v-model="detailVisible" title="盘点单详情" width="1000px" destroy-on-close>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="盘点单号">{{ detail.stocktakingNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTagType(detail.status)" size="small">{{ statusLabel(detail.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="盘点仓库">{{ detail.warehouseName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="申请人">{{ detail.applicantName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ $fmtDate(detail.createTime) }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.approveRemark" label="审批意见">{{ detail.approveRemark }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.remark" label="备注" :span="2">{{ detail.remark }}</el-descriptions-item>
      </el-descriptions>
      <div style="margin-top:16px; font-weight:600; margin-bottom:8px">盘点明细</div>
      <el-table :data="detail.items" border size="small">
        <el-table-column prop="materialNo" label="物料编号" width="120" />
        <el-table-column prop="materialName" label="物料名称" min-width="140" show-overflow-tooltip />
        <el-table-column label="规格" width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="60" align="center" />
        <el-table-column prop="systemQty" label="在库数量" width="100" align="right" />
        <el-table-column prop="lockedQty" label="锁定数量" width="100" align="right" />
        <el-table-column prop="availableQty" label="可用数量" width="100" align="right" />
        <el-table-column prop="actualQty" label="实盘数量" width="100" align="right" />
        <el-table-column label="差异" width="100" align="right">
          <template #default="{ row }">
            <span :style="{ color: row.diffQty > 0 ? '#67c23a' : row.diffQty < 0 ? '#f56c6c' : '#909399', fontWeight: 600 }">
              {{ row.diffQty > 0 ? '+' : '' }}{{ row.diffQty }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="盘点结果" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.diffQty > 0" type="success" size="small">盘盈</el-tag>
            <el-tag v-else-if="row.diffQty < 0" type="danger" size="small">盘亏</el-tag>
            <el-tag v-else type="info" size="small">一致</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <template #footer><el-button @click="detailVisible = false">关闭</el-button></template>
    </el-dialog>

    <!-- 驳回弹窗 -->
    <el-dialog v-model="rejectVisible" title="驳回盘点" width="420px" destroy-on-close>
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

    <!-- 物料选择: 复用通用组件 (多选 + 按盘点仓库库存) -->
    <MaterialPickerDialog v-model="materialPickerVisible" title="选择物料（盘点仓库库存）"
      :multiple="true" :warehouse-id="createForm.warehouseId" @select="onMaterialsSelected" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getStocktakingPage, getStocktakingDetail, createStocktaking,
  approveStocktaking, rejectStocktaking
} from '@/api/inventory'
import { getWarehouseSelect, getUserSelect } from '@/api/system'
import MaterialPickerDialog from '@/components/MaterialPickerDialog.vue'

function statusLabel(s) {
  return { pending: '待审批', approved: '已通过', rejected: '已驳回' }[s] || s || '-'
}
function statusTagType(s) {
  return { pending: 'warning', approved: 'success', rejected: 'danger' }[s] || 'info'
}

// ==================== 查询 ====================
const queryParams = reactive({ stocktakingNo: '', warehouseId: null, applicantId: null, status: '', dateStart: '', dateEnd: '', pageNum: 1, pageSize: 20 })
const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const dateRange = ref([])
const userOptions = ref([])

function onDateChange(val) {
  queryParams.dateStart = val ? val[0] : ''
  queryParams.dateEnd = val ? val[1] : ''
}

async function loadData() {
  loading.value = true
  try {
    const res = await getStocktakingPage(queryParams)
    tableData.value = res.data?.records || res.data?.list || []
    total.value = res.data?.total || 0
  } finally { loading.value = false }
}
function handleSearch() { queryParams.pageNum = 1; loadData() }
function handleReset() {
  Object.assign(queryParams, { stocktakingNo: '', warehouseId: null, applicantId: null, status: '', dateStart: '', dateEnd: '', pageNum: 1 })
  dateRange.value = []
  loadData()
}

// ==================== 详情 ====================
const detailVisible = ref(false)
const detail = ref({})

async function openDetail(row) {
  const res = await getStocktakingDetail(row.id)
  detail.value = res.data || {}
  detailVisible.value = true
}

// ==================== 新增 ====================
const createVisible = ref(false)
const submitLoading = ref(false)
const createFormRef = ref(null)
const warehouseOptions = ref([])

const createForm = reactive({
  warehouseId: null,
  remark: '',
  items: []
})
const createRules = {
  warehouseId: [{ required: true, message: '请选择盘点仓库', trigger: 'change' }]
}

function calcDiff(row) {
  // 实盘数量不能小于锁定数量
  if (row.lockedQty != null && row.actualQty != null && row.actualQty < row.lockedQty) {
    row.actualQty = row.lockedQty
    ElMessage.warning('实盘数量不能小于锁定数量')
  }
  if (row.totalQty != null && row.actualQty != null) {
    row.diffQty = parseFloat((row.actualQty - row.totalQty).toFixed(4))
  } else {
    row.diffQty = null
  }
}

function addItem() {
  if (!createForm.warehouseId) {
    ElMessage.warning('请先选择盘点仓库')
    return
  }
  createForm.items.push({
    materialId: null, materialNo: '', materialName: '', spec: '', unit: '',
    totalQty: null, lockedQty: null, availableQty: null, actualQty: 0, diffQty: null
  })
}
function removeItem(idx) { createForm.items.splice(idx, 1) }

const materialPickerVisible = ref(false)
let currentPickerRow = null

function openMaterialPicker(row) {
  if (!createForm.warehouseId) {
    ElMessage.warning('请先选择盘点仓库')
    return
  }
  currentPickerRow = row
  materialPickerVisible.value = true
}

// 通用组件 @select 回调: 多选返回数组. 已在明细的物料会被自动去重.
// 第一条填到 currentPickerRow (用户点"选"的那行占位), 其余追加新明细.
function onMaterialsSelected(rows) {
  if (!rows || !rows.length) return
  const existingIds = new Set(
    createForm.items.filter(i => i !== currentPickerRow && i.materialId).map(i => i.materialId)
  )
  let firstAssigned = false
  let added = 0
  for (const row of rows) {
    const mid = row.materialId || row.id
    if (existingIds.has(mid)) continue
    const target = (!firstAssigned && currentPickerRow)
      ? currentPickerRow
      : (() => {
          const item = {
            materialId: null, materialNo: '', materialName: '', spec: '', unit: '',
            totalQty: null, lockedQty: null, availableQty: null, actualQty: 0, diffQty: null
          }
          createForm.items.push(item)
          return item
        })()
    target.materialId = mid
    target.materialNo = row.materialNo || ''
    target.materialName = row.materialName || ''
    target.spec = row.spec || ''
    target.unit = row.unit || ''
    target.totalQty = row.totalQty ?? row.availableQty ?? 0
    target.lockedQty = row.lockedQty ?? 0
    target.availableQty = row.availableQty ?? 0
    target.actualQty = target.totalQty
    calcDiff(target)
    existingIds.add(mid)
    firstAssigned = true
    added++
  }
  if (added > 1) ElMessage.success(`已添加 ${added} 条物料`)
  currentPickerRow = null
  materialPickerVisible.value = false
}


function onWarehouseChange() {
  // 仓库变更时清空已选物料（库存范围已变）
  createForm.items = []
}

function openCreate() {
  Object.assign(createForm, { warehouseId: null, remark: '' })
  createForm.items = []
  createVisible.value = true
}

async function handleCreate() {
  await createFormRef.value.validate()
  // 自动过滤掉没选物料的空行 (用户加了行但没选物料), 全部空就提示
  const validItems = createForm.items.filter(i => i.materialId)
  if (validItems.length === 0) { ElMessage.warning('请至少添加一条有效的盘点明细'); return }
  const skipped = createForm.items.length - validItems.length
  if (skipped > 0) ElMessage.info(`已自动忽略 ${skipped} 条未选物料的明细`)
  // 校验实盘数量不能小于锁定数量
  const invalid = validItems.find(i => i.lockedQty != null && i.actualQty < i.lockedQty)
  if (invalid) {
    ElMessage.warning(`物料 ${invalid.materialName} 的实盘数量不能小于锁定数量`)
    return
  }
  submitLoading.value = true
  try {
    await createStocktaking({ ...createForm, items: validItems })
    ElMessage.success('盘点单创建成功，请等待审批')
    createVisible.value = false
    loadData()
  } finally { submitLoading.value = false }
}

// ==================== 审批 ====================
async function handleApprove(row) {
  await ElMessageBox.confirm(
    '确定审批通过该盘点单？通过后将自动调整库存数据并生成出入库记录。',
    '审批确认', { type: 'warning' }
  )
  try {
    await approveStocktaking(row.id, {})
    ElMessage.success('审批通过，库存已调整')
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
    await rejectStocktaking(rejectForm.id, { remark: rejectForm.remark })
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
