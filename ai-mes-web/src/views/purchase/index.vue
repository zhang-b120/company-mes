<template>
  <div class="page-container">
    <el-card class="table-card" shadow="never">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <!-- Tab 1: 采购申请 -->
        <el-tab-pane label="采购申请" name="requests">
          <div class="tab-toolbar">
            <el-button type="primary" plain @click="handleAddRequest">新增申请</el-button>
          </div>

          <el-table :data="requestData" v-loading="requestLoading" border stripe>
            <el-table-column prop="requestNo" label="申请编号" min-width="140" />
            <el-table-column prop="materialName" label="物料名称" min-width="140" />
            <el-table-column label="规格" min-width="120">
              <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
            </el-table-column>
            <el-table-column prop="quantity" label="数量" width="90" align="right" />
            <el-table-column prop="unit" label="单位" width="70" align="center" />
            <el-table-column prop="supplierName" label="建议供应商" min-width="140" />
            <el-table-column prop="expectedDate" label="期望日期" width="120" />
            <el-table-column prop="sourceType" label="来源" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="sourceTypeTag(row.sourceType)" size="small">
                  {{ sourceTypeText(row.sourceType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="来源订单" min-width="150">
              <template #default="{ row }">
                <template v-if="row.sourceOrders && row.sourceOrders.length">
                  <span v-for="(o, i) in row.sourceOrders" :key="o.id">
                    <el-button link type="primary" size="small" @click.stop="handleViewSourceOrder(o.id)">{{ o.orderNo }}</el-button>
                    <span v-if="i < row.sourceOrders.length - 1" class="text-muted">, </span>
                  </span>
                </template>
                <span v-else-if="row.sourceOrderNos" class="text-muted">{{ row.sourceOrderNos }}</span>
                <span v-else class="text-muted">-</span>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="requestStatusType(row.status)" size="small">
                  {{ requestStatusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="审批人" width="100" align="center">
              <template #default="{ row }">
                <span v-if="row.approverName">{{ row.approverName }}</span>
                <span v-else class="text-muted">-</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <template v-if="row.status === 'pending'">
                  <el-button type="success" link size="small" @click="handleApproveRequest(row)">审批</el-button>
                  <el-button type="danger" link size="small" @click="handleRejectRequest(row)">驳回</el-button>
                </template>
                <span v-else class="text-muted">-</span>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-wrapper">
            <el-pagination
              v-model:current-page="requestQuery.pageNum"
              v-model:page-size="requestQuery.pageSize"
              :total="requestTotal"
              :page-sizes="[10, 20, 50, 100]"
              background
              layout="total, sizes, prev, pager, next, jumper"
              @size-change="loadRequests"
              @current-change="loadRequests"
            />
          </div>
        </el-tab-pane>

        <!-- Tab 2: 采购订单 -->
        <el-tab-pane label="采购订单" name="orders">
          <div class="tab-toolbar">
            <el-button type="primary" plain @click="handleAddOrder">新增订单</el-button>
          </div>

          <el-table :data="orderData" v-loading="orderLoading" border stripe>
            <el-table-column prop="poNo" label="订单编号" min-width="140" />
            <el-table-column prop="supplierName" label="供应商" min-width="140" />
            <el-table-column prop="totalAmount" label="总金额" width="120" align="right">
              <template #default="{ row }">
                {{ row.totalAmount?.toFixed(2) }}
              </template>
            </el-table-column>
            <el-table-column prop="deliveryDate" label="交付日期" width="120" />
            <el-table-column prop="status" label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="orderStatusType(row.status)" size="small">
                  {{ orderStatusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="创建时间" min-width="160">
              <template #default="{ row }">{{ $fmtDate(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="handleViewOrder(row)">查看</el-button>
                <el-button
                  v-if="row.status === 'draft'"
                  type="success"
                  link
                  size="small"
                  @click="handleConfirmOrder(row)"
                >提交审批</el-button>
                <el-button
                  v-if="['confirmed', 'partial_received'].includes(row.status)"
                  type="warning"
                  link
                  size="small"
                  @click="handleReceipt(row)"
                >签收</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-wrapper">
            <el-pagination
              v-model:current-page="orderQuery.pageNum"
              v-model:page-size="orderQuery.pageSize"
              :total="orderTotal"
              :page-sizes="[10, 20, 50, 100]"
              background
              layout="total, sizes, prev, pager, next, jumper"
              @size-change="loadOrders"
              @current-change="loadOrders"
            />
          </div>
        </el-tab-pane>

        <!-- Tab 3: 到货签收 -->
        <el-tab-pane label="到货签收" name="receipts">
          <el-table :data="receiptData" v-loading="receiptListLoading" border stripe>
            <el-table-column prop="poNo" label="PO编号" min-width="140" />
            <el-table-column prop="materialName" label="物料" min-width="140" />
            <el-table-column prop="expectedQty" label="应到" width="90" align="right" />
            <el-table-column prop="receivedQty" label="实到" width="90" align="right" />
            <el-table-column prop="batchNo" label="批次号" min-width="130" />
            <el-table-column prop="receiverName" label="签收人" width="100" align="center" />
            <el-table-column prop="qcStatus" label="质检状态" width="110" align="center">
              <template #default="{ row }">
                <el-tag :type="qcStatusType(row.qcStatus)" size="small">
                  {{ qcStatusText(row.qcStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="!row.receivedQty || row.receivedQty === 0"
                  type="primary"
                  link
                  size="small"
                  @click="handleSignReceipt(row)"
                >签收</el-button>
                <el-button
                  v-if="row.qcStatus === 'fail' || row.qcStatus === 'failed'"
                  type="warning"
                  link
                  size="small"
                  @click="handleQcFail(row)"
                >处理</el-button>
                <span v-if="(row.receivedQty && row.receivedQty > 0) && row.qcStatus !== 'fail' && row.qcStatus !== 'failed'" class="text-muted">-</span>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-wrapper">
            <el-pagination
              v-model:current-page="receiptQuery.pageNum"
              v-model:page-size="receiptQuery.pageSize"
              :total="receiptTotal"
              :page-sizes="[10, 20, 50, 100]"
              background
              layout="total, sizes, prev, pager, next, jumper"
              @size-change="loadReceipts"
              @current-change="loadReceipts"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- add request dialog -->
    <el-dialog v-model="requestDialogVisible" title="新增采购申请" width="560px" destroy-on-close @close="resetRequestForm">
      <el-form ref="requestFormRef" :model="requestForm" :rules="requestRules" label-width="100px">
        <el-form-item label="物料" prop="materialId">
          <el-select v-model="requestForm.materialId" placeholder="请选择物料" filterable remote
                     :remote-method="searchMaterials" :loading="materialSearching" style="width: 100%">
            <el-option
              v-for="item in materialOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="数量" prop="quantity">
          <el-input-number v-model="requestForm.quantity" :min="1" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="建议供应商" prop="suggestedSupplierId">
          <el-select v-model="requestForm.suggestedSupplierId" placeholder="请选择供应商" filterable clearable style="width: 100%">
            <el-option
              v-for="item in supplierOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="期望日期" prop="expectedDate">
          <el-date-picker
            v-model="requestForm.expectedDate"
            type="date"
            placeholder="请选择期望日期"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="预估价格" prop="estimatedPrice">
          <el-input-number v-model="requestForm.estimatedPrice" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="requestForm.remark" type="textarea" :rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="requestDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleRequestSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- add order dialog -->
    <el-dialog v-model="orderDialogVisible" title="新增采购订单" width="560px" destroy-on-close @close="resetOrderForm">
      <el-form ref="orderFormRef" :model="orderForm" :rules="orderRules" label-width="100px">
        <el-form-item label="供应商" prop="supplierId">
          <el-select v-model="orderForm.supplierId" placeholder="请选择供应商" filterable style="width: 100%">
            <el-option
              v-for="item in supplierOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="交付日期" prop="deliveryDate">
          <el-date-picker
            v-model="orderForm.deliveryDate"
            type="date"
            placeholder="请选择交付日期"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="orderForm.remark" type="textarea" :rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="orderDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleOrderSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- order detail drawer -->
    <el-drawer v-model="detailDrawerVisible" title="采购订单详情" size="700px" destroy-on-close>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="订单编号">{{ orderDetail.poNo }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ orderDetail.supplierName }}</el-descriptions-item>
        <el-descriptions-item label="总金额">{{ orderDetail.totalAmount?.toFixed(2) }}</el-descriptions-item>
        <el-descriptions-item label="交付日期">{{ orderDetail.deliveryDate }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="orderStatusType(orderDetail.status)" size="small">
            {{ orderStatusText(orderDetail.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ orderDetail.createTime }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ orderDetail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <h4 style="margin: 20px 0 12px;">订单明细</h4>
      <el-table :data="orderDetail.items || []" border stripe>
        <el-table-column prop="materialName" label="物料名称" min-width="140" />
        <el-table-column label="规格" min-width="100">
          <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
        </el-table-column>
        <el-table-column prop="quantity" label="数量" width="90" align="right" />
        <el-table-column prop="unit" label="单位" width="70" align="center" />
        <el-table-column prop="unitPrice" label="单价" width="90" align="right" />
        <el-table-column prop="amount" label="金额" width="100" align="right">
          <template #default="{ row }">
            {{ row.amount?.toFixed(2) }}
          </template>
        </el-table-column>
        <el-table-column prop="receivedQty" label="已签收" width="80" align="right" />
      </el-table>
    </el-drawer>

    <!-- receipt dialog (from order tab) -->
    <el-dialog v-model="receiptDialogVisible" title="签收" width="480px" destroy-on-close>
      <el-form ref="receiptFormRef" :model="receiptForm" :rules="receiptRules" label-width="100px">
        <el-form-item label="签收数量" prop="quantity">
          <el-input-number v-model="receiptForm.quantity" :min="1" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="receiptForm.remark" type="textarea" :rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="receiptDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleReceiptSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- sign receipt dialog (from receipts tab) -->
    <el-dialog v-model="signReceiptDialogVisible" title="确认签收" width="520px" destroy-on-close @close="resetSignReceiptForm">
      <el-form ref="signReceiptFormRef" :model="signReceiptForm" :rules="signReceiptRules" label-width="100px">
        <el-form-item label="PO编号">
          <el-input v-model="signReceiptForm.poNo" disabled />
        </el-form-item>
        <el-form-item label="物料">
          <el-input v-model="signReceiptForm.materialName" disabled />
        </el-form-item>
        <el-form-item label="实到数量" prop="actualQty">
          <el-input-number v-model="signReceiptForm.actualQty" :min="0.01" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="批次号" prop="batchNo">
          <el-input v-model="signReceiptForm.batchNo" placeholder="自动生成或手动输入" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="signReceiptDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSignReceiptSubmit">确认签收</el-button>
      </template>
    </el-dialog>

    <!-- QC fail handling dialog -->
    <el-dialog v-model="qcFailDialogVisible" title="质检不合格处理" width="520px" destroy-on-close @close="resetQcFailForm">
      <el-form ref="qcFailFormRef" :model="qcFailForm" :rules="qcFailRules" label-width="100px">
        <el-form-item label="处理方式" prop="handleType">
          <el-radio-group v-model="qcFailForm.handleType">
            <el-radio value="RETURN">退货</el-radio>
            <el-radio value="CONCESSION">让步接收</el-radio>
            <el-radio value="PARTIAL">部分接收</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item
          v-if="qcFailForm.handleType === 'RETURN' || qcFailForm.handleType === 'PARTIAL'"
          label="退货数量"
          prop="returnQty"
        >
          <el-input-number v-model="qcFailForm.returnQty" :min="0.01" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="原因" prop="reason">
          <el-input v-model="qcFailForm.reason" type="textarea" :rows="3" placeholder="请输入处理原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="qcFailDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleQcFailSubmit">确认处理</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getPurchaseRequestPage,
  createPurchaseRequest,
  approvePurchaseRequest,
  rejectPurchaseRequest,
  getPurchaseOrderPage,
  getPurchaseOrderDetail,
  createPurchaseOrder,
  submitPurchaseOrder,
  createPurchaseReceipt,
  getPurchaseReceiptPage,
  confirmPurchaseReceipt,
  handleReceiptQcFail
} from '@/api/purchase'
import { getMaterialPage, getSupplierSelect } from '@/api/master'

const router = useRouter()

// --- state ---
const activeTab = ref('requests')
const submitLoading = ref(false)

const materialOptions = ref([])
const supplierOptions = ref([])

// request tab
const requestLoading = ref(false)
const requestData = ref([])
const requestTotal = ref(0)
const requestQuery = reactive({ pageNum: 1, pageSize: 10 })

// order tab
const orderLoading = ref(false)
const orderData = ref([])
const orderTotal = ref(0)
const orderQuery = reactive({ pageNum: 1, pageSize: 10 })

// receipt tab
const receiptListLoading = ref(false)
const receiptData = ref([])
const receiptTotal = ref(0)
const receiptQuery = reactive({ pageNum: 1, pageSize: 10 })

// request dialog
const requestDialogVisible = ref(false)
const requestFormRef = ref(null)
const requestForm = ref({
  materialId: undefined,
  quantity: 1,
  suggestedSupplierId: undefined,
  expectedDate: '',
  estimatedPrice: 0,
  remark: ''
})
const requestRules = {
  materialId: [{ required: true, message: '请选择物料', trigger: 'change' }],
  quantity: [{ required: true, message: '请输入数量', trigger: 'blur' }],
  expectedDate: [{ required: true, message: '请选择期望日期', trigger: 'change' }]
}

// order dialog
const orderDialogVisible = ref(false)
const orderFormRef = ref(null)
const orderForm = ref({
  supplierId: undefined,
  deliveryDate: '',
  remark: ''
})
const orderRules = {
  supplierId: [{ required: true, message: '请选择供应商', trigger: 'change' }],
  deliveryDate: [{ required: true, message: '请选择交付日期', trigger: 'change' }]
}

// detail drawer
const detailDrawerVisible = ref(false)
const orderDetail = ref({})

// receipt dialog (order tab)
const receiptDialogVisible = ref(false)
const receiptFormRef = ref(null)
const receiptForm = ref({ orderId: undefined, quantity: 1, remark: '' })
const receiptRules = {
  quantity: [{ required: true, message: '请输入签收数量', trigger: 'blur' }]
}

// sign receipt dialog (receipts tab)
const signReceiptDialogVisible = ref(false)
const signReceiptFormRef = ref(null)
const signReceiptForm = ref({
  id: undefined,
  poNo: '',
  materialName: '',
  actualQty: 1,
  batchNo: ''
})
const signReceiptRules = {
  actualQty: [{ required: true, message: '请输入实到数量', trigger: 'blur' }],
  batchNo: [{ required: true, message: '请输入批次号', trigger: 'blur' }]
}

// QC fail handling dialog
const qcFailDialogVisible = ref(false)
const qcFailFormRef = ref(null)
const qcFailForm = ref({
  id: undefined,
  handleType: 'RETURN',
  returnQty: 0,
  reason: ''
})
const qcFailRules = {
  handleType: [{ required: true, message: '请选择处理方式', trigger: 'change' }],
  returnQty: [{ required: true, message: '请输入退货数量', trigger: 'blur' }],
  reason: [{ required: true, message: '请输入原因', trigger: 'blur' }]
}

// --- helpers ---
function sourceTypeTag(type) {
  if (!type) return 'info'
  const map = { manual: '', mrp: 'success', safety_stock: 'warning', ai: 'primary' }
  return map[type.toLowerCase()] || 'info'
}
function sourceTypeText(type) {
  if (!type) return '-'
  const map = { manual: '手动', mrp: 'MRP计算', safety_stock: '安全库存', ai: 'AI建议' }
  return map[type.toLowerCase()] || type
}
function requestStatusType(status) {
  if (!status) return 'info'
  const map = { pending: 'warning', approved: 'success', rejected: 'danger', ordered: '', completed: 'success', cancelled: 'danger' }
  return map[status.toLowerCase()] || 'info'
}
function requestStatusText(status) {
  if (!status) return '-'
  const map = { pending: '待审批', approved: '已审批', rejected: '已驳回', ordered: '已下单', completed: '已完成', cancelled: '已取消' }
  return map[status.toLowerCase()] || status
}
function orderStatusType(status) {
  if (!status) return 'info'
  const map = { draft: 'info', pending_approval: 'warning', confirmed: 'primary', rejected: 'danger', partial_received: 'warning', received: 'success' }
  return map[status.toLowerCase()] || 'info'
}
function orderStatusText(status) {
  if (!status) return '-'
  const map = { draft: '草稿', pending_approval: '待审批', confirmed: '已确认', rejected: '已驳回', partial_received: '部分收货', received: '已收货' }
  return map[status.toLowerCase()] || status
}
function qcStatusType(status) {
  if (!status) return 'info'
  const map = { pending: 'info', inspecting: 'warning', pass: 'success', passed: 'success', fail: 'danger', failed: 'danger' }
  return map[status.toLowerCase()] || 'info'
}
function qcStatusText(status) {
  if (!status) return '未签收'
  const map = { pending: '待质检', inspecting: '质检中', pass: '合格', passed: '合格', fail: '不合格', failed: '不合格' }
  return map[status.toLowerCase()] || status
}

function handleViewSourceOrder(orderId) {
  if (orderId) router.push(`/order/detail/${orderId}`)
}

// --- methods ---
async function loadRequests() {
  requestLoading.value = true
  try {
    const res = await getPurchaseRequestPage(requestQuery)
    requestData.value = res.data.records || []
    requestTotal.value = res.data.total || 0
  } catch {
    // handled by interceptor
  } finally {
    requestLoading.value = false
  }
}

async function loadOrders() {
  orderLoading.value = true
  try {
    const res = await getPurchaseOrderPage(orderQuery)
    orderData.value = res.data.records || []
    orderTotal.value = res.data.total || 0
  } catch {
    // handled by interceptor
  } finally {
    orderLoading.value = false
  }
}

async function loadReceipts() {
  receiptListLoading.value = true
  try {
    const res = await getPurchaseReceiptPage(receiptQuery)
    receiptData.value = res.data.records || []
    receiptTotal.value = res.data.total || 0
  } catch {
    // handled by interceptor
  } finally {
    receiptListLoading.value = false
  }
}

async function loadOptions() {
  try {
    const supRes = await getSupplierSelect()
    supplierOptions.value = supRes.data || []
  } catch {}
}

const materialSearching = ref(false)
let _matSearchTimer = null
async function searchMaterials(keyword) {
  if (_matSearchTimer) clearTimeout(_matSearchTimer)
  if (!keyword || !keyword.trim()) { materialOptions.value = []; return }
  _matSearchTimer = setTimeout(async () => {
    materialSearching.value = true
    try {
      const res = await getMaterialPage({ materialName: keyword.trim(), pageNum: 1, pageSize: 30 })
      materialOptions.value = (res.data?.records || []).map(m => ({ id: m.id, name: m.materialName }))
    } catch {} finally { materialSearching.value = false }
  }, 300)
}

async function _unusedLoadOptionsLegacy() {
  try {
    // legacy fallback - 不再调用
  } catch {
    // handled by interceptor
  }
}

function handleTabChange(tab) {
  if (tab === 'requests') {
    loadRequests()
  } else if (tab === 'orders') {
    loadOrders()
  } else if (tab === 'receipts') {
    loadReceipts()
  }
}

// request operations
function handleAddRequest() {
  requestForm.value = { materialId: undefined, quantity: 1, suggestedSupplierId: undefined, expectedDate: '', estimatedPrice: 0, remark: '' }
  requestDialogVisible.value = true
}

async function handleRequestSubmit() {
  await requestFormRef.value.validate()
  submitLoading.value = true
  try {
    await createPurchaseRequest(requestForm.value)
    ElMessage.success('采购申请创建成功')
    requestDialogVisible.value = false
    loadRequests()
  } catch {
    // handled by interceptor
  } finally {
    submitLoading.value = false
  }
}

function resetRequestForm() {
  requestFormRef.value?.resetFields()
}

function handleApproveRequest(row) {
  ElMessageBox.confirm(`确定审批通过申请「${row.requestNo}」吗？`, '提示', { type: 'warning' })
    .then(async () => {
      try {
        await approvePurchaseRequest(row.id, {})
        ElMessage.success('审批通过')
        loadRequests()
      } catch {
        // handled by interceptor
      }
    })
    .catch(() => {})
}

function handleRejectRequest(row) {
  ElMessageBox.confirm(`确定驳回申请「${row.requestNo}」吗？`, '提示', { type: 'warning' })
    .then(async () => {
      try {
        await rejectPurchaseRequest(row.id, {})
        ElMessage.success('已驳回')
        loadRequests()
      } catch {
        // handled by interceptor
      }
    })
    .catch(() => {})
}

// order operations
function handleAddOrder() {
  orderForm.value = { supplierId: undefined, deliveryDate: '', remark: '' }
  orderDialogVisible.value = true
}

async function handleOrderSubmit() {
  await orderFormRef.value.validate()
  submitLoading.value = true
  try {
    await createPurchaseOrder(orderForm.value)
    ElMessage.success('采购订单创建成功')
    orderDialogVisible.value = false
    loadOrders()
  } catch {
    // handled by interceptor
  } finally {
    submitLoading.value = false
  }
}

function resetOrderForm() {
  orderFormRef.value?.resetFields()
}

async function handleViewOrder(row) {
  try {
    const res = await getPurchaseOrderDetail(row.id)
    orderDetail.value = res.data || {}
    detailDrawerVisible.value = true
  } catch {
    // handled by interceptor
  }
}

function handleConfirmOrder(row) {
  ElMessageBox.confirm(`确定确认订单「${row.poNo}」吗？`, '提示', { type: 'warning' })
    .then(async () => {
      try {
        await submitPurchaseOrder(row.id)
        ElMessage.success('确认成功')
        loadOrders()
      } catch {
        // handled by interceptor
      }
    })
    .catch(() => {})
}

function handleReceipt(row) {
  receiptForm.value = { orderId: row.id, quantity: 1, remark: '' }
  receiptDialogVisible.value = true
}

async function handleReceiptSubmit() {
  await receiptFormRef.value.validate()
  submitLoading.value = true
  try {
    await createPurchaseReceipt(receiptForm.value.orderId, receiptForm.value)
    ElMessage.success('签收成功')
    receiptDialogVisible.value = false
    loadOrders()
  } catch {
    // handled by interceptor
  } finally {
    submitLoading.value = false
  }
}

// receipt tab operations
function handleSignReceipt(row) {
  signReceiptForm.value = {
    id: row.id,
    poNo: row.poNo,
    materialName: row.materialName,
    actualQty: row.expectedQty || 1,
    batchNo: row.batchNo || ''
  }
  signReceiptDialogVisible.value = true
}

async function handleSignReceiptSubmit() {
  await signReceiptFormRef.value.validate()
  submitLoading.value = true
  try {
    await confirmPurchaseReceipt({
      id: signReceiptForm.value.id,
      actualQty: signReceiptForm.value.actualQty,
      batchNo: signReceiptForm.value.batchNo
    })
    ElMessage.success('签收成功，已自动创建质检任务')
    signReceiptDialogVisible.value = false
    loadReceipts()
  } catch {
    // handled by interceptor
  } finally {
    submitLoading.value = false
  }
}

function resetSignReceiptForm() {
  signReceiptFormRef.value?.resetFields()
}

function handleQcFail(row) {
  qcFailForm.value = {
    id: row.id,
    handleType: 'RETURN',
    returnQty: row.receivedQty || 0,
    reason: ''
  }
  qcFailDialogVisible.value = true
}

async function handleQcFailSubmit() {
  await qcFailFormRef.value.validate()
  submitLoading.value = true
  try {
    await handleReceiptQcFail(qcFailForm.value.id, {
      handleType: qcFailForm.value.handleType,
      returnQty: qcFailForm.value.returnQty,
      reason: qcFailForm.value.reason
    })
    ElMessage.success('处理成功')
    qcFailDialogVisible.value = false
    loadReceipts()
  } catch {
    // handled by interceptor
  } finally {
    submitLoading.value = false
  }
}

function resetQcFailForm() {
  qcFailFormRef.value?.resetFields()
}

// --- lifecycle ---
onMounted(() => {
  loadOptions()
  loadRequests()
})
</script>

<style scoped lang="scss">
.page-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.table-card {
  flex: 1;
}
.tab-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 16px;
}
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.text-muted {
  color: #c0c4cc;
}
</style>
