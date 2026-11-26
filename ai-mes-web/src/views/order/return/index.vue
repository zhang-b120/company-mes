<template>
  <div class="page-container">
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="退货单号">
          <el-input v-model="queryParams.returnNo" placeholder="退货单号" clearable style="width:150px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="客户名称">
          <el-input v-model="queryParams.customerName" placeholder="客户名称" clearable style="width:150px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="订单号">
          <el-input v-model="queryParams.orderNo" placeholder="销售订单号" clearable style="width:150px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width:130px">
            <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <div class="action-bar">
        <el-button type="primary" @click="openCreate">申请退货</el-button>
        <el-button :icon="Setting" @click="colConfigDialogRef.open()">列配置</el-button>
      </div>
      <el-table :data="tableData" v-loading="loading" border stripe size="small">
        <el-table-column
          v-for="col in visibleColumns" :key="col.key"
          :label="col.label"
          :min-width="col.minWidth || 100"
          :width="col.width"
          :align="col.align || 'left'"
          show-overflow-tooltip>
          <template #default="{ row }">
            <template v-if="col.key === 'returnNo'">
              <el-button link type="primary" @click="openDetail(row)">{{ row.returnNo }}</el-button>
            </template>
            <template v-else-if="col.key === 'status'">
              <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            </template>
            <template v-else-if="col.key === 'createTime'">{{ (row.createTime || '').substring(0, 16) }}</template>
            <template v-else>{{ row[col.key] || '-' }}</template>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看</el-button>
            <el-button v-if="row.status === 'pending'" link type="success" @click="handleApprove(row)">审批</el-button>
            <el-button v-if="row.status === 'pending'" link type="warning" @click="handleReject(row)">驳回</el-button>
            <el-button v-if="row.status === 'approved'" link type="primary" @click="openReceive(row)">确认收货</el-button>
            <el-button v-if="row.status === 'received'" link type="success" @click="handleComplete(row)">完成</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
          :total="total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next"
          @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <!-- 申请退货对话框 -->
    <el-dialog v-model="createVisible" title="申请退货" width="860px" destroy-on-close>
      <el-form :model="createForm" :rules="createRules" ref="createFormRef" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="关联订单" prop="orderId">
              <el-select v-model="createForm.orderId" filterable remote :remote-method="searchOrders"
                placeholder="搜索销售订单号" style="width:100%" @change="onOrderChange">
                <el-option v-for="o in orderOptions" :key="o.id" :label="`${o.orderNo} - ${o.customerName}`" :value="o.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="退货日期" prop="returnDate">
              <el-date-picker v-model="createForm.returnDate" type="date" value-format="YYYY-MM-DD"
                placeholder="选择日期" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="退货原因" prop="returnReason">
          <el-input v-model="createForm.returnReason" type="textarea" :rows="2" placeholder="请填写退货原因" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" type="textarea" :rows="1" placeholder="可选" />
        </el-form-item>
        <div style="font-weight:600;color:#606266;margin:8px 0">退货明细</div>
        <el-table :data="createForm.items" border size="small">
          <el-table-column prop="productName" label="产品名称" min-width="140" show-overflow-tooltip />
          <el-table-column label="规格" min-width="100" show-overflow-tooltip>
            <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
          </el-table-column>
          <el-table-column prop="unit" label="单位" width="60" align="center" />
          <el-table-column prop="shippedQty" label="已发货" width="80" align="center" />
          <el-table-column label="退货数量" width="120">
            <template #default="{ row }">
              <el-input-number v-model="row.returnQty" :min="0" :max="row.shippedQty || 999999"
                :precision="2" size="small" style="width:100%" controls-position="right" />
            </template>
          </el-table-column>
          <el-table-column label="退货仓库" min-width="140">
            <template #default="{ row }">
              <el-select v-model="row.warehouseId" placeholder="选择仓库" clearable size="small" style="width:100%">
                <el-option v-for="w in warehouseOptions" :key="w.id" :label="w.warehouseName" :value="w.id" />
              </el-select>
            </template>
          </el-table-column>
        </el-table>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createLoading" @click="submitCreate">提交申请</el-button>
      </template>
    </el-dialog>

    <!-- 退货单详情对话框 -->
    <el-dialog v-model="detailVisible" :title="`退货单详情 - ${detailData.returnNo || ''}`" width="860px">
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="退货单号">{{ detailData.returnNo }}</el-descriptions-item>
        <el-descriptions-item label="销售订单">{{ detailData.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="客户名称">{{ detailData.customerName }}</el-descriptions-item>
        <el-descriptions-item label="退货日期">{{ detailData.returnDate }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTagType(detailData.status)" size="small">{{ statusLabel(detailData.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ (detailData.createTime || '').substring(0, 16) }}</el-descriptions-item>
        <el-descriptions-item label="退货原因" :span="3">{{ detailData.returnReason || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="3">{{ detailData.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <div style="font-weight:600;color:#606266;margin:12px 0 8px">退货明细</div>
      <el-table :data="detailData.items || []" border size="small">
        <el-table-column prop="productName" label="产品名称" min-width="140" show-overflow-tooltip />
        <el-table-column label="规格" min-width="100" show-overflow-tooltip>
          <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="60" align="center" />
        <el-table-column prop="returnQty" label="退货数量" width="90" align="center" />
        <el-table-column prop="receivedQty" label="已收货数量" width="100" align="center" />
        <el-table-column prop="warehouseName" label="退货仓库" min-width="120" />
      </el-table>
    </el-dialog>

    <ColumnConfigDialog
      ref="colConfigDialogRef"
      :all-columns="allColumns"
      :default-columns="ORDER_RETURN_DEFAULT_COLUMNS"
      @save="saveConfig"
    />

    <!-- 确认收货对话框 -->
    <el-dialog v-model="receiveVisible" title="确认收货" width="760px" destroy-on-close>
      <div style="color:#909399;margin-bottom:12px;font-size:13px">请填写实际收货数量和入库仓库</div>
      <el-table :data="receiveItems" border size="small">
        <el-table-column prop="productName" label="产品名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="returnQty" label="申请退货数量" width="110" align="center" />
        <el-table-column label="实际收货数量" width="140">
          <template #default="{ row }">
            <el-input-number v-model="row.receivedQty" :min="0" :max="row.returnQty"
              :precision="2" size="small" style="width:100%" controls-position="right" />
          </template>
        </el-table-column>
        <el-table-column label="入库仓库" min-width="150">
          <template #default="{ row }">
            <el-select v-model="row.warehouseId" placeholder="选择仓库" size="small" style="width:100%">
              <el-option v-for="w in warehouseOptions" :key="w.id" :label="w.warehouseName" :value="w.id" />
            </el-select>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="receiveVisible = false">取消</el-button>
        <el-button type="primary" :loading="receiveLoading" @click="submitReceive">确认收货</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Setting } from '@element-plus/icons-vue'
import { useColumnConfig } from '@/composables/useColumnConfig'
import ColumnConfigDialog from '@/components/ColumnConfigDialog.vue'
import {
  getReturnOrderPage, getReturnOrderDetail, createReturnOrder,
  approveReturnOrder, rejectReturnOrder, receiveReturnOrder, completeReturnOrder,
  getSalesOrderPage
} from '@/api/order'
import { getWarehouseSelect } from '@/api/system'

const ORDER_RETURN_DEFAULT_COLUMNS = [
  { key: 'returnNo', label: '退货单号', minWidth: 160 },
  { key: 'orderNo', label: '销售订单号', minWidth: 160 },
  { key: 'customerName', label: '客户名称', minWidth: 140 },
  { key: 'itemCount', label: '产品数', width: 80, align: 'center' },
  { key: 'totalReturnQty', label: '退货总量', width: 100, align: 'center' },
  { key: 'returnDate', label: '退货日期', width: 115, align: 'center' },
  { key: 'returnReason', label: '退货原因', minWidth: 150 },
  { key: 'status', label: '状态', width: 100, align: 'center' },
  { key: 'createTime', label: '创建时间', minWidth: 165 },
]
const colConfigDialogRef = ref(null)
const { visibleColumns, allColumns, loadColumnConfig, saveConfig } = useColumnConfig('order_return', ORDER_RETURN_DEFAULT_COLUMNS)

const statusOptions = [
  { label: '待审批', value: 'pending' },
  { label: '已审批', value: 'approved' },
  { label: '已收货', value: 'received' },
  { label: '已完成', value: 'completed' },
  { label: '已驳回', value: 'rejected' }
]
const statusMap = Object.fromEntries(statusOptions.map(s => [s.value, s.label]))
function statusLabel(status) { return statusMap[status] || status || '-' }
function statusTagType(status) {
  const map = { pending: 'warning', approved: 'primary', received: '', completed: 'success', rejected: 'info' }
  return map[status] || 'info'
}

const queryParams = reactive({ returnNo: '', customerName: '', orderNo: '', status: '', pageNum: 1, pageSize: 20 })
const loading = ref(false)
const tableData = ref([])
const total = ref(0)

async function loadData() {
  loading.value = true
  try {
    const res = await getReturnOrderPage({ ...queryParams })
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch {} finally { loading.value = false }
}
function handleSearch() { queryParams.pageNum = 1; loadData() }
function handleReset() { queryParams.returnNo = ''; queryParams.customerName = ''; queryParams.orderNo = ''; queryParams.status = ''; queryParams.pageNum = 1; loadData() }

onMounted(() => { loadData(); loadColumnConfig() })

// 仓库选项
const warehouseOptions = ref([])
async function loadWarehouseOptions() {
  if (warehouseOptions.value.length > 0) return
  try {
    const res = await getWarehouseSelect()
    warehouseOptions.value = res.data || []
  } catch {}
}

// ==================== 申请退货 ====================
const createVisible = ref(false)
const createLoading = ref(false)
const createFormRef = ref(null)
const createForm = reactive({ orderId: null, returnDate: null, returnReason: '', remark: '', items: [] })
const createRules = {
  orderId: [{ required: true, message: '请选择关联销售订单', trigger: 'change' }],
  returnReason: [{ required: true, message: '请填写退货原因', trigger: 'blur' }]
}
const orderOptions = ref([])

async function openCreate() {
  createForm.orderId = null; createForm.returnDate = null; createForm.returnReason = ''
  createForm.remark = ''; createForm.items = []
  await loadWarehouseOptions()
  createVisible.value = true
}

async function searchOrders(query) {
  if (!query) return
  try {
    const res = await getSalesOrderPage({ keyword: query, pageNum: 1, pageSize: 20 })
    orderOptions.value = (res.data?.records || []).filter(o =>
      ['confirmed', 'in_production', 'outsourcing', 'partial_shipped', 'shipped', 'completed'].includes(o.status))
  } catch {}
}

async function onOrderChange(orderId) {
  createForm.items = []
  if (!orderId) return
  const order = orderOptions.value.find(o => o.id === orderId)
  if (!order) return
  try {
    const { getSalesOrderDetail } = await import('@/api/order')
    const res = await getSalesOrderDetail(orderId)
    const items = res.data?.items || []
    createForm.items = items.map(item => ({
      orderItemId: item.id,
      productId: item.productId,
      productName: item.productName,
      spec: item.spec || '-',
      unit: item.unit || '-',
      shippedQty: item.shippedQty || 0,
      returnQty: 0,
      warehouseId: null
    }))
  } catch {}
}

async function submitCreate() {
  await createFormRef.value.validate()
  const hasItem = createForm.items.some(i => i.returnQty > 0)
  if (!hasItem) { ElMessage.warning('请填写至少一个产品的退货数量'); return }
  createLoading.value = true
  try {
    const items = createForm.items.filter(i => i.returnQty > 0)
    await createReturnOrder({
      orderId: createForm.orderId,
      returnDate: createForm.returnDate,
      returnReason: createForm.returnReason,
      remark: createForm.remark,
      items
    })
    ElMessage.success('退货申请已提交')
    createVisible.value = false
    loadData()
  } catch {} finally { createLoading.value = false }
}

// ==================== 详情 ====================
const detailVisible = ref(false)
const detailData = ref({})

async function openDetail(row) {
  try {
    const res = await getReturnOrderDetail(row.id)
    detailData.value = res.data || {}
    detailVisible.value = true
  } catch {}
}

// ==================== 审批/驳回/完成 ====================
async function handleApprove(row) {
  await ElMessageBox.confirm(`确定审批通过退货单 ${row.returnNo}？`, '审批确认', { type: 'info' })
  try { await approveReturnOrder(row.id); ElMessage.success('审批通过'); loadData() } catch {}
}

async function handleReject(row) {
  const { value: reason } = await ElMessageBox.prompt('请填写驳回原因', '驳回',
    { inputPattern: /\S+/, inputErrorMessage: '原因不能为空', type: 'warning' })
  try { await rejectReturnOrder(row.id, reason); ElMessage.success('已驳回'); loadData() } catch {}
}

async function handleComplete(row) {
  await ElMessageBox.confirm(`确定完成退货单 ${row.returnNo}？`, '完成确认', { type: 'info' })
  try { await completeReturnOrder(row.id); ElMessage.success('已完成'); loadData() } catch {}
}

// ==================== 确认收货 ====================
const receiveVisible = ref(false)
const receiveLoading = ref(false)
const receiveItems = ref([])
let receiveOrderId = null

async function openReceive(row) {
  receiveOrderId = row.id
  await loadWarehouseOptions()
  try {
    const res = await getReturnOrderDetail(row.id)
    const items = (res.data?.items || []).map(item => ({
      id: item.id,
      productName: item.productName,
      returnQty: item.returnQty,
      receivedQty: item.returnQty,
      warehouseId: item.warehouseId || (warehouseOptions.value[0]?.id ?? null)
    }))
    receiveItems.value = items
    receiveVisible.value = true
  } catch {}
}

async function submitReceive() {
  for (const item of receiveItems.value) {
    if (!item.warehouseId) { ElMessage.warning(`请为"${item.productName}"选择入库仓库`); return }
  }
  receiveLoading.value = true
  try {
    await receiveReturnOrder(receiveOrderId, receiveItems.value)
    ElMessage.success('收货确认成功，已触发退货入库')
    receiveVisible.value = false
    loadData()
  } catch {} finally { receiveLoading.value = false }
}
</script>

<style scoped>
.page-container { padding: 16px; }
.search-card { margin-bottom: 12px; }
.action-bar { margin-bottom: 12px; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
