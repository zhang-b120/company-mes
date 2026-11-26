<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="申请单号">
          <el-input v-model="queryParams.requestNo" placeholder="申请单号" clearable style="width:160px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="入库类型">
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
        <el-form-item label="日期">
          <el-date-picker v-model="dateRange" type="daterange" range-separator="-" start-placeholder="开始" end-placeholder="结束"
            value-format="YYYY-MM-DD" style="width:240px" @change="handleSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
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
              <el-button v-permission="'inventory:request:approve'" link type="success" @click="handleApprove(row)">审批通过</el-button>
              <el-button v-permission="'inventory:request:approve'" link type="danger" @click="openReject(row)">驳回</el-button>
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
    <el-dialog v-model="createVisible" :title="editingId ? '编辑入库申请' : '新增入库申请'" width="1100px" destroy-on-close>
      <el-form :model="createForm" :rules="createRules" ref="createFormRef" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="申请单号">
              <el-input v-model="createForm.requestNo" placeholder="不填则自动生成（INR+时间戳）" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="入库类型" prop="invType">
              <el-select v-model="createForm.invType" placeholder="请选择入库类型" style="width:100%" :disabled="!!editingId" @change="onInvTypeChange">
                <el-option v-for="t in invTypeOptions" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col v-if="createForm.invType === 'return_in'" :span="12">
            <el-form-item label="退货客户">
              <el-select v-model="createForm.customerId" placeholder="请选择客户" clearable filterable style="width:100%"
                @change="onCustomerChange">
                <el-option v-for="c in customerOptions" :key="c.id" :label="c.name" :value="c.id" />
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
              <el-input v-model="createForm.batchNo" placeholder="不填则自动生成（IT+日期+序号）" />
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
              <el-table-column label="在库数量" width="90" align="right">
                <template #default="{ row }">
                  <span style="color:#606266;font-size:12px">{{ row.onHandQty ?? '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="可用数量" width="90" align="right">
                <template #default="{ row }">
                  <span :style="{ color: row.availableQty > 0 ? '#67c23a' : '#909399', fontSize: '12px' }">{{ row.availableQty ?? '-' }}</span>
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
              <el-table-column label="单价" width="120">
                <template #default="{ row }">
                  <el-input-number v-model="row.unitPrice" :min="0" :precision="4" size="small" controls-position="right" style="width:100%" />
                </template>
              </el-table-column>
              <el-table-column label="仓库" min-width="130">
                <template #default="{ row }">
                  <el-select v-model="row.warehouseId" size="small" style="width:100%" placeholder="请选择仓库"
                    @change="onItemWarehouseChange(row)">
                    <el-option v-for="w in warehouseOptions" :key="w.id" :label="w.name" :value="w.id" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="库位" min-width="120">
                <template #default="{ row }">
                  <el-select v-if="row.warehouseId" v-model="row.locationId" size="small"
                    style="width:100%" placeholder="可选" clearable>
                    <el-option v-for="l in (row.locationOptions||[])" :key="l.id" :label="l.name" :value="l.id" />
                  </el-select>
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
        <el-form-item label="附件">
          <AttachmentUploader v-model="createForm.attachmentUrls" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleCreate">提交</el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="入库申请详情" width="900px" destroy-on-close>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="申请单号">{{ detail.requestNo }}</el-descriptions-item>
        <el-descriptions-item label="入库类型">{{ invTypeLabel(detail.invType) }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.invType === 'return_in'" label="退货客户">{{ detail.customerName || '-' }}</el-descriptions-item>
        <el-descriptions-item :span="detail.invType === 'return_in' ? 1 : 2" label="关联单据号">{{ detail.relatedOrderNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="批次号">{{ detail.batchNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="目标仓库">{{ detail.warehouseName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="库位">{{ detail.locationName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTagType(detail.status)" size="small">{{ statusLabel(detail.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="申请人">{{ detail.applicantName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间" :span="2">{{ $fmtDate(detail.createTime) }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.remark" label="备注" :span="2">{{ detail.remark }}</el-descriptions-item>
      </el-descriptions>

      <!-- 附件列表 -->
      <div v-if="detail.attachmentUrls" style="margin-top:12px">
        <div style="font-weight:600; margin-bottom:8px">附件</div>
        <AttachmentList :value="detail.attachmentUrls" />
      </div>
      <div style="margin-top:16px; font-weight:600; margin-bottom:8px">物料明细</div>
      <el-table :data="detail.items" border size="small">
        <el-table-column prop="materialNo" label="物料编号" width="120" />
        <el-table-column prop="materialName" label="物料名称" min-width="120" show-overflow-tooltip />
        <el-table-column label="规格" width="100" show-overflow-tooltip>
          <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="55" align="center" />
        <el-table-column prop="quantity" label="数量" width="85" align="right" />
        <el-table-column prop="unitPrice" label="单价" width="85" align="right" />
        <el-table-column prop="totalPrice" label="总价" width="95" align="right" />
        <el-table-column prop="warehouseName" label="仓库" width="100" show-overflow-tooltip>
          <template #default="{ row }"><span>{{ row.warehouseName || '-' }}</span></template>
        </el-table-column>
        <el-table-column prop="locationName" label="库位" width="90" show-overflow-tooltip>
          <template #default="{ row }"><span>{{ row.locationName || '-' }}</span></template>
        </el-table-column>
      </el-table>
      <template #footer><el-button @click="detailVisible = false">关闭</el-button></template>
    </el-dialog>

    <!-- 列配置 -->
    <ColumnConfigDialog ref="colConfigDialogRef" :all-columns="allColumns" :default-columns="REQUEST_DEFAULT_COLUMNS" @save="saveConfig" />

    <!-- 关联单据弹窗 -->
    <PurchaseOrderDetailDialog v-model:visible="relatedOrderVisible" :relatedOrderNo="relatedOrderNo" :invType="relatedInvType" />

    <!-- 物料选择弹窗 -->
    <MaterialPickerDialog v-model="materialPickerVisible" title="选择物料" @select="onMaterialSelected" />

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
  approveInvRequest, rejectInvRequest, getMaterialStocks
} from '@/api/inventory'
import { getWarehouseSelect, getWarehouseLocationList } from '@/api/system'
import { getCustomerSelect } from '@/api/master'
import PurchaseOrderDetailDialog from '@/components/PurchaseOrderDetailDialog.vue'
import { useColumnConfig } from '@/composables/useColumnConfig'
import ColumnConfigDialog from '@/components/ColumnConfigDialog.vue'
import MaterialPickerDialog from '@/components/MaterialPickerDialog.vue'
import AttachmentUploader from '@/components/AttachmentUploader.vue'
import AttachmentList from '@/components/AttachmentList.vue'

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

const REQUEST_DEFAULT_COLUMNS = [
  { key: 'requestNo', label: '申请单号', width: 180 },
  { key: 'invType', label: '入库类型', width: 120, align: 'center' },
  { key: 'relatedOrderNo', label: '关联单据号', minWidth: 175 },
  { key: 'batchNo', label: '批次号', width: 120 },
  { key: 'warehouseName', label: '仓库', width: 110 },
  { key: 'locationName', label: '库位', width: 110 },
  { key: 'status', label: '状态', width: 110, align: 'center' },
  { key: 'applicantName', label: '申请人', width: 100 },
  { key: 'createTime', label: '创建时间', minWidth: 165 },
]
const colConfigDialogRef = ref(null)
const { visibleColumns, allColumns, loadColumnConfig, saveConfig } = useColumnConfig('inventory_request', REQUEST_DEFAULT_COLUMNS)

// ==================== 字典 ====================
const invTypeOptions = [
  { label: '采购入库', value: 'purchase_in' },
  { label: '生产入库', value: 'production_in' },
  { label: '半成品入库', value: 'semi_in' },
  { label: '外协收货', value: 'outsource_in' },
  { label: '外协来料', value: 'outsource_material_in' },
  { label: '退货入库', value: 'return_in' },
  { label: '其他入库', value: 'other_in' }
]
const invTypeMap = Object.fromEntries(invTypeOptions.map(t => [t.value, t.label]))
const allInvTypeMap = {
  ...invTypeMap,
  production_out: '生产领料', sales_out: '销售发货', outsource_out: '外协发料',
  scrap_out: '报废出库', other_out: '其他出库', transfer_out: '调拨出库', transfer_in: '调拨入库',
  purchase_return: '采购退货', outsource_material_in: '外协来料'
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
const queryParams = reactive({ requestNo: '', invType: '', status: '', relatedOrderNo: '', dateStart: '', dateEnd: '', pageNum: 1, pageSize: 20 })
const dateRange = ref([])
const loading = ref(false)
const tableData = ref([])
const total = ref(0)

async function loadData() {
  loading.value = true
  try {
    // 入库申请页面 — 限定 invTypeGroup=in,只返回入库类申请,避免出库类混进来
    const params = { ...queryParams, invTypeGroup: 'in',
      dateStart: dateRange.value?.[0] || '', dateEnd: dateRange.value?.[1] || '' }
    const res = await getInvRequestPage(params)
    tableData.value = res.data?.records || res.data?.list || []
    total.value = res.data?.total || 0
  } finally { loading.value = false }
}
function handleSearch() { queryParams.pageNum = 1; loadData() }
function handleReset() {
  Object.assign(queryParams, { requestNo: '', invType: '', status: '', relatedOrderNo: '', pageNum: 1 })
  dateRange.value = []
  loadData()
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
const createForm = reactive({
  requestNo: '',
  invType: '',
  customerId: null,
  customerName: '',
  relatedOrderNo: '',
  batchNo: '',
  remark: '',
  attachmentUrls: '',
  items: []
})

const customerOptions = ref([])

function onInvTypeChange(val) {
  if (val !== 'return_in') { createForm.customerId = null; createForm.customerName = '' }
}
function onCustomerChange(id) {
  const c = customerOptions.value.find(c => c.id === id)
  createForm.customerName = c ? c.name : ''
}
const createRules = {
  invType: [{ required: true, message: '请选择入库类型', trigger: 'change' }]
}

function addItem() {
  createForm.items.push({ materialId: null, materialNo: '', materialName: '', spec: '', unit: '', quantity: 1, unitPrice: null, totalPrice: null, warehouseId: undefined, locationId: undefined, locationOptions: [], onHandQty: null, availableQty: null })
}
function removeItem(idx) { createForm.items.splice(idx, 1) }

const materialPickerVisible = ref(false)
let currentPickerRow = null
function openMaterialPicker(row) {
  currentPickerRow = row
  materialPickerVisible.value = true
}
async function onMaterialSelected(mats) {
  if (!currentPickerRow || !mats?.length) return
  mats.forEach((mat, idx) => {
    const target = idx === 0 ? currentPickerRow : (() => {
      const row = { materialId: null, materialNo: '', materialName: '', spec: '', unit: '', quantity: 1, unitPrice: null, totalPrice: null, warehouseId: undefined, locationId: undefined, locationOptions: [], onHandQty: null, availableQty: null }
      createForm.items.push(row)
      return row
    })()
    target.materialId = mat.id
    target.materialName = mat.materialName || mat.name || ''
    target.spec = mat.spec || ''
    target.unit = mat.unit || ''
    target.materialNo = mat.materialNo || ''
    target.unitConfig = mat.unitConfig || ''
    if (mat.defaultWarehouseId) {
      target.warehouseId = mat.defaultWarehouseId
      target.locationId = undefined
      loadRowLocations(target)
    }
  })
  // 查询库存数量
  const ids = mats.map(m => m.id).filter(Boolean)
  if (ids.length) {
    try {
      const res = await getMaterialStocks(ids)
      const stockMap = {}
      ;(res.data || []).forEach(s => { stockMap[s.materialId] = s })
      createForm.items.forEach(item => {
        if (item.materialId && stockMap[item.materialId]) {
          item.onHandQty = stockMap[item.materialId].onHandQty ?? 0
          item.availableQty = stockMap[item.materialId].availableQty ?? 0
        }
      })
    } catch {}
  }
}

async function loadRowLocations(row) {
  if (!row.warehouseId) { row.locationOptions = []; return }
  try {
    const res = await getWarehouseLocationList(row.warehouseId)
    row.locationOptions = res.data || []
  } catch { row.locationOptions = [] }
}
async function onItemWarehouseChange(row) {
  row.locationId = undefined
  row.locationOptions = []
  await loadRowLocations(row)
}
const editingId = ref(null)

function openCreate() {
  editingId.value = null
  Object.assign(createForm, { requestNo: '', invType: '', customerId: null, customerName: '', relatedOrderNo: '', batchNo: '', remark: '', attachmentUrls: '' })
  createForm.items = []
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
      customerId: d.customerId || null,
      customerName: d.customerName || '',
      relatedOrderNo: d.relatedOrderNo || '',
      batchNo: d.batchNo || '',
      remark: d.remark || '',
      attachmentUrls: d.attachmentUrls || ''
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
    // 异步加载每行库位选项
    for (const it of createForm.items) { if (it.warehouseId) loadRowLocations(it) }
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
    // attachmentUrls 由 AttachmentUploader 通过 v-model 自动同步 JSON 字符串
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
  await ElMessageBox.confirm('确定审批通过该入库申请？审批通过后将自动生成库存任务。', '审批确认', { type: 'warning' })
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

// ==================== 关联单据 ====================
const relatedOrderVisible = ref(false)
const relatedOrderNo = ref('')
const relatedInvType = ref('')

function openRelatedOrder(row) {
  relatedOrderNo.value = row.relatedOrderNo || ''
  relatedInvType.value = row.invType || ''
  relatedOrderVisible.value = true
}

// ==================== 初始化 ====================
async function loadOptions() {
  try {
    const [wRes, cRes] = await Promise.all([getWarehouseSelect(), getCustomerSelect()])
    warehouseOptions.value = wRes.data || []
    customerOptions.value = cRes.data || []
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
</style>
