<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="申请单号">
          <el-input v-model="queryParams.requestNo" placeholder="申请单号" clearable style="width:160px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="出库类型">
          <el-select v-model="queryParams.invType" placeholder="全部" clearable style="width:140px">
            <el-option v-for="t in invTypeOptions" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width:130px">
            <el-option label="待审核" value="pending" />
            <el-option label="已审批" value="approved" />
            <el-option label="已驳回" value="rejected" />
            <el-option label="已生成任务" value="task_created" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联单据号">
          <el-input v-model="queryParams.relatedOrderNo" placeholder="关联单据号" clearable style="width:160px" />
        </el-form-item>
        <el-form-item label="客户">
          <el-select v-model="queryParams.customerId" placeholder="请选择客户" filterable clearable style="width:180px"
            @change="handleSearch">
            <el-option v-for="c in customerOptions" :key="c.id" :label="c.customerName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker v-model="dateRange" type="daterange" range-separator="-" start-placeholder="开始" end-placeholder="结束"
            value-format="YYYY-MM-DD" style="width:240px" @change="handleSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="success" @click="handleExport">导出</el-button>
          <el-button type="primary" plain @click="openCreate">新增申请</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据表格 -->
    <el-card shadow="never" class="table-card">
      <template #header>
        <div style="display:flex;justify-content:flex-end">
          <el-button :icon="Setting" @click="colConfigDialogRef.open()">列配置</el-button>
        </div>
      </template>
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column v-for="col in visibleColumns" :key="col.key"
          :label="col.label" :min-width="col.minWidth || 100" :width="col.width" :align="col.align || 'left'" show-overflow-tooltip>
          <template #default="{ row }">
            <template v-if="col.key === 'invType'">
              <el-tag :type="invTypeTagType(row.invType)" size="small">{{ invTypeLabel(row.invType) }}</el-tag>
            </template>
            <template v-else-if="col.key === 'relatedOrderNo'">
              <el-button v-if="row.relatedOrderNo" link type="primary" style="padding:0"
                @click="openRelatedOrder(row)">{{ row.relatedOrderNo }}</el-button>
              <span v-else style="color:#c0c4cc">-</span>
            </template>
            <template v-else-if="col.key === 'status'">
              <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            </template>
            <template v-else-if="col.key === 'createTime'">{{ $fmtDate(row.createTime) }}</template>
            <template v-else>{{ row[col.key] || '-' }}</template>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看</el-button>
            <template v-if="row.status === 'pending'">
              <el-button v-permission="'inventory:out-request:approve'" link type="success" @click="handleApprove(row)">审批通过</el-button>
              <el-button v-permission="'inventory:out-request:approve'" link type="danger" @click="openReject(row)">驳回</el-button>
            </template>
            <el-button v-if="row.status === 'rejected'" link type="warning" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
          :page-sizes="[10,20,50,100]" :total="total" layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <!-- 新增/编辑申请弹窗 -->
    <el-dialog v-model="createVisible" :title="editingId ? '编辑出库申请' : '新增出库申请'" width="1100px" destroy-on-close>
      <el-form :model="createForm" :rules="createRules" ref="createFormRef" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="申请单号">
              <el-input v-model="createForm.requestNo" placeholder="不填则自动生成（INR+时间戳）" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="出库类型" prop="invType">
              <el-select v-model="createForm.invType" placeholder="请选择出库类型" style="width:100%" :disabled="!!editingId">
                <el-option v-for="t in invTypeOptions" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="关联单据号">
              <el-input v-model="createForm.relatedOrderNo" placeholder="选填" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="批次号">
              <el-input v-model="createForm.batchNo" placeholder="不填则自动生成（OT+日期+序号）" />
            </el-form-item>
          </el-col>
          <el-col :span="12" v-if="createForm.invType === 'sales_out'">
            <el-form-item label="客户名称">
              <el-select v-model="createForm.customerId" placeholder="请选择客户" filterable clearable style="width:100%"
                @change="onCustomerChange">
                <el-option v-for="c in customerOptions" :key="c.id" :label="c.customerName" :value="c.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="申请人">
              <el-select v-model="createForm.applicantId" placeholder="请选择申请人" filterable clearable style="width:100%">
                <el-option v-for="u in userOptions" :key="u.id" :label="u.realName || u.username" :value="u.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="出库仓库">
              <el-select v-model="createForm.warehouseId" placeholder="请选择仓库" clearable style="width:100%" @change="onWarehouseChange">
                <el-option v-for="w in warehouseOptions" :key="w.id" :label="w.name" :value="w.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="库位">
              <el-select v-model="createForm.locationId" placeholder="请选择库位（可选）" clearable style="width:100%">
                <el-option v-for="l in locationOptions" :key="l.id" :label="l.name" :value="l.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="物料明细" required>
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
                  <span style="color:#606266;font-size:12px;padding:0 4px">{{ $fmtSpec(row.spec) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="单位" width="60" align="center">
                <template #default="{ row }">
                  <span style="color:#606266;font-size:12px">{{ row.unit || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="数量" width="120">
                <template #default="{ row }">
                  <el-input-number v-model="row.quantity" :min="0.001" :precision="4" size="small" controls-position="right" style="width:100%" />
                </template>
              </el-table-column>
              <el-table-column label="换算数量" width="110">
                <template #default="{ row }">
                  <span v-if="secondaryDisplay(row.quantity, row.unitConfig)" style="color:#909399;font-size:12px">{{ secondaryDisplay(row.quantity, row.unitConfig) }}</span>
                  <span v-else style="color:#c0c4cc">-</span>
                </template>
              </el-table-column>
              <el-table-column label="在库数量" width="90" align="right">
                <template #default="{ row }">
                  <span style="color:#606266;font-size:12px">{{ row.onHandQty ?? '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="可用库存" width="100" align="right">
                <template #default="{ row }">
                  <span :style="{ color: row.availableQty > 0 ? '#67c23a' : '#909399' }">{{ row.availableQty ?? '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="单价" width="120">
                <template #default="{ row }">
                  <el-input-number v-model="row.unitPrice" :min="0" :precision="4" size="small" controls-position="right" style="width:100%" />
                </template>
              </el-table-column>
              <el-table-column label="小计" width="82" align="right">
                <template #default="{ row }">
                  <span style="font-size:12px">{{ ((row.quantity || 0) * (row.unitPrice || 0)).toFixed(2) }}</span>
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
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleCreate">提交</el-button>
      </template>
    </el-dialog>

    <!-- 列配置 -->
    <ColumnConfigDialog ref="colConfigDialogRef" :all-columns="allColumns" :default-columns="OUT_REQUEST_DEFAULT_COLUMNS" @save="saveConfig" />

    <!-- 关联单据弹窗 -->
    <PurchaseOrderDetailDialog v-model:visible="relatedOrderVisible" :relatedOrderNo="relatedOrderNo" :invType="relatedInvType" />

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="出库申请详情" width="760px" destroy-on-close>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="申请单号">{{ detail.requestNo }}</el-descriptions-item>
        <el-descriptions-item label="出库类型">{{ invTypeLabel(detail.invType) }}</el-descriptions-item>
        <el-descriptions-item label="关联单据号">{{ detail.relatedOrderNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="批次号">{{ detail.batchNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="出库仓库">{{ detail.warehouseName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="库位">{{ detail.locationName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTagType(detail.status)" size="small">{{ statusLabel(detail.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="申请人">{{ detail.applicantName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间" :span="2">{{ $fmtDate(detail.createTime) }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.remark" label="备注" :span="2">{{ detail.remark }}</el-descriptions-item>
      </el-descriptions>
      <div style="margin-top:16px; font-weight:600; margin-bottom:8px">物料明细</div>
      <el-table :data="detail.items" border size="small">
        <el-table-column prop="materialNo" label="物料编号" width="120" />
        <el-table-column prop="materialName" label="物料名称" min-width="140" />
        <el-table-column label="规格" width="120">
          <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="60" align="center" />
        <el-table-column prop="quantity" label="数量" width="90" align="right" />
        <el-table-column prop="unitPrice" label="单价" width="90" align="right" />
        <el-table-column prop="totalPrice" label="总价" width="100" align="right" />
      </el-table>
      <template #footer><el-button @click="detailVisible = false">关闭</el-button></template>
    </el-dialog>

    <!-- 物料选择弹窗 — 通用组件按出库仓库库存过滤 -->
    <MaterialPickerDialog
      v-model="materialPickerVisible"
      title="选择物料（出库仓库库存）"
      :multiple="true"
      :warehouse-id="createForm.warehouseId"
      @select="onMaterialPickerSelect"
    />

    <!-- 驳回弹窗 -->
    <el-dialog v-model="rejectVisible" title="驳回申请" width="420px" destroy-on-close>
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
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Setting } from '@element-plus/icons-vue'
import {
  getInvRequestPage, getInvRequestDetail, createInvRequest, updateInvRequest,
  approveInvRequest, rejectInvRequest, exportInvRequests
} from '@/api/inventory'
import { getWarehouseSelect, getWarehouseLocationList, getUserSelect } from '@/api/system'
import { getCustomerSelect } from '@/api/master'
import PurchaseOrderDetailDialog from '@/components/PurchaseOrderDetailDialog.vue'
import MaterialPickerDialog from '@/components/MaterialPickerDialog.vue'
import { useColumnConfig } from '@/composables/useColumnConfig'
import ColumnConfigDialog from '@/components/ColumnConfigDialog.vue'

// ==================== 列配置 ====================
function secondaryDisplay(qty, unitConfig) {
  if (!qty || !unitConfig) return ''
  try {
    const cfg = JSON.parse(unitConfig)
    const c = cfg.conversions?.[0]
    if (c) return `${(qty * c.ratio).toFixed(4)} ${c.unit}`
  } catch {}
  return ''
}

const OUT_REQUEST_DEFAULT_COLUMNS = [
  { key: 'requestNo', label: '申请单号', width: 180 },
  { key: 'invType', label: '出库类型', width: 120, align: 'center' },
  { key: 'customerName', label: '客户名称', width: 120 },
  { key: 'relatedOrderNo', label: '关联单据号', minWidth: 175 },
  { key: 'batchNo', label: '批次号', width: 120 },
  { key: 'warehouseName', label: '出库仓库', width: 110 },
  { key: 'locationName', label: '库位', width: 110 },
  { key: 'status', label: '状态', width: 110, align: 'center' },
  { key: 'applicantName', label: '申请人', width: 100 },
  { key: 'createTime', label: '创建时间', minWidth: 165 },
]
const colConfigDialogRef = ref(null)
const { visibleColumns, allColumns, loadColumnConfig, saveConfig } = useColumnConfig('inventory_out_request', OUT_REQUEST_DEFAULT_COLUMNS)

// ==================== 字典 ====================
const invTypeOptions = [
  { label: '生产领料', value: 'production_out' },
  { label: '销售发货', value: 'sales_out' },
  { label: '外协发料', value: 'outsource_out' },
  { label: '报废出库', value: 'scrap_out' },
  { label: '其他出库', value: 'other_out' }
]
const invTypeMap = Object.fromEntries(invTypeOptions.map(t => [t.value, t.label]))
const allInvTypeMap = {
  ...invTypeMap,
  purchase_in: '采购入库', production_in: '生产入库', semi_in: '半成品入库',
  outsource_in: '外协收货', outsource_material_in: '外协来料', return_in: '退货入库', other_in: '其他入库',
  transfer_out: '调拨出库', transfer_in: '调拨入库', purchase_return: '采购退货'
}
function invTypeLabel(v) { return allInvTypeMap[v] || v || '-' }
function invTypeTagType(v) {
  if (v === 'purchase_return') return 'danger'
  return v?.endsWith('_in') ? 'success' : v?.endsWith('_out') ? 'danger' : 'info'
}
function statusLabel(s) {
  return { pending: '待审核', approved: '已审批', rejected: '已驳回', task_created: '已生成任务' }[s] || s || '-'
}
function statusTagType(s) {
  return { pending: 'warning', approved: 'success', rejected: 'danger', task_created: 'info' }[s] || 'info'
}

// ==================== 查询 ====================
const queryParams = reactive({
  requestNo: '', invType: '', invTypeGroup: 'out', status: '',
  relatedOrderNo: '', customerId: undefined,
  dateStart: '', dateEnd: '', pageNum: 1, pageSize: 20
})
const dateRange = ref([])
const loading = ref(false)
const tableData = ref([])
const total = ref(0)

async function loadData() {
  loading.value = true
  try {
    // 出库申请页面 — 限定 invTypeGroup=out,只返回出库类申请,避免入库类混进来
    const params = { ...queryParams, invTypeGroup: 'out',
      dateStart: dateRange.value?.[0] || '', dateEnd: dateRange.value?.[1] || '' }
    const res = await getInvRequestPage(params)
    tableData.value = res.data?.records || res.data?.list || []
    total.value = res.data?.total || 0
  } finally { loading.value = false }
}
function handleSearch() { queryParams.pageNum = 1; loadData() }
function handleReset() {
  Object.assign(queryParams, { requestNo: '', invType: '', status: '', relatedOrderNo: '', customerId: undefined, pageNum: 1 })
  dateRange.value = []
  loadData()
}

async function handleExport() {
  try {
    const params = {
      ...queryParams,
      dateStart: dateRange.value?.[0] || '',
      dateEnd: dateRange.value?.[1] || ''
    }
    delete params.pageNum
    delete params.pageSize
    const res = await exportInvRequests(params)
    const blob = res instanceof Blob ? res : new Blob([res])
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url; a.download = '出库申请.xlsx'; a.click()
    URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('导出失败')
  }
}

// ==================== 关联单据 ====================
const relatedOrderVisible = ref(false)
const relatedOrderNo = ref('')
const relatedInvType = ref('')

function openRelatedOrder(row) {
  relatedOrderNo.value = row.relatedOrderNo || ''
  relatedInvType.value = row.invType || ''
  relatedOrderVisible.value = true
}

// ==================== 详情 ====================
const detailVisible = ref(false)
const detail = ref({})

async function openDetail(row) {
  const res = await getInvRequestDetail(row.id)
  detail.value = res.data || {}
  detailVisible.value = true
}

// ==================== 新增 ====================
const createVisible = ref(false)
const submitLoading = ref(false)
const createFormRef = ref(null)
const warehouseOptions = ref([])
const locationOptions = ref([])
const customerOptions = ref([])
const userOptions = ref([])

const createForm = reactive({
  requestNo: '',
  invType: '',
  relatedOrderNo: '',
  batchNo: '',
  customerId: null,
  customerName: '',
  applicantId: null,
  warehouseId: null,
  locationId: null,
  remark: '',
  items: []
})

function onCustomerChange(id) {
  const c = customerOptions.value.find(c => c.id === id)
  createForm.customerName = c ? c.customerName : ''
}
const createRules = {
  invType: [{ required: true, message: '请选择出库类型', trigger: 'change' }]
}

function addItem() {
  if (!createForm.warehouseId) {
    ElMessage.warning('请先选择出库仓库')
    return
  }
  createForm.items.push({ materialId: null, materialNo: '', materialName: '', spec: '', unit: '', quantity: 1, unitPrice: null, totalPrice: null, onHandQty: null, availableQty: null })
}
function removeItem(idx) { createForm.items.splice(idx, 1) }

const materialPickerVisible = ref(false)
let currentPickerRow = null

function openMaterialPicker(row) {
  if (!createForm.warehouseId) {
    ElMessage.warning('请先选择出库仓库')
    return
  }
  currentPickerRow = row
  materialPickerVisible.value = true
}

// 通用 MaterialPickerDialog 选完回调: selection 已是当次选的若干物料
function onMaterialPickerSelect(selection) {
  if (!selection?.length) return
  selection.forEach((mat, idx) => {
    const target = idx === 0 && currentPickerRow ? currentPickerRow : (() => {
      const row = { materialId: null, materialNo: '', materialName: '', spec: '', unit: '', quantity: 1, unitPrice: null, totalPrice: null, unitConfig: '', onHandQty: null, availableQty: null }
      createForm.items.push(row)
      return row
    })()
    target.materialId = mat.materialId || mat.id
    target.materialName = mat.materialName || ''
    target.spec = mat.spec || ''
    target.unit = mat.unit || ''
    target.materialNo = mat.materialNo || ''
    target.unitConfig = mat.unitConfig || ''
    target.onHandQty = mat.onHandQty ?? mat.availableQty ?? 0
    target.availableQty = mat.availableQty || 0
  })
  currentPickerRow = null
}
async function onWarehouseChange(id) {
  createForm.locationId = null
  locationOptions.value = []
  // 仓库变更时清空已选物料（库存范围已变）
  createForm.items = []
  if (id) {
    try {
      const res = await getWarehouseLocationList(id)
      locationOptions.value = res.data || []
    } catch {}
  }
}

const editingId = ref(null)

function openCreate() {
  editingId.value = null
  Object.assign(createForm, { requestNo: '', invType: '', relatedOrderNo: '', batchNo: '', warehouseId: null, locationId: null, remark: '' })
  createForm.items = []
  locationOptions.value = []
  createVisible.value = true
}

async function openEdit(row) {
  try {
    const res = await getInvRequestDetail(row.id)
    const d = res.data || {}
    editingId.value = row.id
    Object.assign(createForm, {
      requestNo: d.requestNo || '',
      invType: d.invType || '',
      relatedOrderNo: d.relatedOrderNo || '',
      batchNo: d.batchNo || '',
      warehouseId: d.warehouseId || null,
      locationId: d.locationId || null,
      remark: d.remark || ''
    })
    createForm.items = (d.items || []).map(it => ({
      materialId: it.materialId,
      materialNo: it.materialNo,
      materialName: it.materialName,
      spec: it.spec,
      unit: it.unit,
      unitConfig: it.unitConfig,
      quantity: it.quantity,
      unitPrice: it.unitPrice,
      warehouseId: it.warehouseId,
      locationId: it.locationId,
      locationOptions: []
    }))
    createVisible.value = true
  } catch {}
}

async function handleCreate() {
  await createFormRef.value.validate()
  const validItems = (createForm.items || []).filter(i => i.materialId)
  if (validItems.length === 0) { ElMessage.warning('请至少添加一条有效的物料明细'); return }
  const skipped = createForm.items.length - validItems.length
  if (skipped > 0) ElMessage.info(`已自动忽略 ${skipped} 条未选物料的明细`)
  submitLoading.value = true
  try {
    if (editingId.value) {
      await updateInvRequest(editingId.value, { ...createForm, items: validItems })
      ElMessage.success('已保存,重新进入待审核')
    } else {
      await createInvRequest({ ...createForm, items: validItems })
      ElMessage.success('新增成功')
    }
    createVisible.value = false
    loadData()
  } finally { submitLoading.value = false }
}

// ==================== 审批 ====================
async function handleApprove(row) {
  await ElMessageBox.confirm('确定审批通过该出库申请？审批通过后将自动生成库存任务。', '审批确认', { type: 'warning' })
  try {
    await approveInvRequest(row.id)
    ElMessage.success('审批通过，已生成库存任务')
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
    await rejectInvRequest(rejectForm.id, { remark: rejectForm.remark })
    ElMessage.success('已驳回')
    rejectVisible.value = false
    loadData()
  } finally { submitLoading.value = false }
}

// ==================== 初始化 ====================
async function loadOptions() {
  try {
    const [wRes, cRes, uRes] = await Promise.all([getWarehouseSelect(), getCustomerSelect(), getUserSelect()])
    warehouseOptions.value = wRes.data || []
    customerOptions.value = cRes.data || []
    userOptions.value = uRes.data || []
  } catch {}
}
onMounted(() => { loadOptions(); loadData(); loadColumnConfig() })
</script>

<style scoped lang="scss">
.page-container { padding: 16px; }
.search-card { margin-bottom: 16px; }
.table-card {
  .pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; }
}
/* 物料选择弹窗:长文本单元格允许自动换行,不截断为 ... */
:deep(.cell-wrap .cell) {
  white-space: normal;
  word-break: break-all;
  line-height: 1.5;
}
</style>
