<template>
  <div>
    <el-tabs v-model="subTab" type="border-card">
      <el-tab-pane label="应收管理" name="receivable">
        <!-- 搜索 -->
        <el-form :model="recvQuery" inline size="small" style="margin-bottom: 12px">
          <el-form-item label="应收单号">
            <el-input v-model="recvQuery.receivableNo" placeholder="应收单号" clearable style="width: 140px" @keyup.enter="loadReceivables" />
          </el-form-item>
          <el-form-item label="关联订单号">
            <el-input v-model="recvQuery.orderNo" placeholder="销售订单号" clearable style="width: 140px" @keyup.enter="loadReceivables" />
          </el-form-item>
          <el-form-item label="客户名称">
            <el-input v-model="recvQuery.customerName" placeholder="客户名称" clearable style="width: 140px" @keyup.enter="loadReceivables" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="recvQuery.status" placeholder="全部" clearable style="width: 120px">
              <el-option label="待收款" value="pending" />
              <el-option label="部分收款" value="partial_received" />
              <el-option label="已收款" value="received" />
              <el-option label="逾期" value="overdue" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadReceivables">搜索</el-button>
            <el-button type="success" plain @click="handleExportRecv">导出</el-button>
          </el-form-item>
        </el-form>

        <el-table :data="recvData" v-loading="recvLoading" border stripe size="small">
          <el-table-column prop="receivableNo" label="应收单号" min-width="140" show-overflow-tooltip />
          <el-table-column prop="orderNo" label="关联订单" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">
              <el-button v-if="row.orderNo" type="primary" link size="small" @click="goSalesOrder(row)">{{ row.orderNo }}</el-button>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column prop="customerName" label="客户" min-width="130" show-overflow-tooltip />
          <el-table-column prop="amount" label="应收金额" width="110" align="right">
            <template #default="{ row }"><span style="font-weight: 500">¥{{ formatMoney(row.amount) }}</span></template>
          </el-table-column>
          <el-table-column prop="receivedAmount" label="已收金额" width="110" align="right">
            <template #default="{ row }"><span style="color: #67c23a">¥{{ formatMoney(row.receivedAmount) }}</span></template>
          </el-table-column>
          <el-table-column prop="unreceivedAmount" label="未收金额" width="110" align="right">
            <template #default="{ row }"><span :style="{ color: row.unreceivedAmount > 0 ? '#f56c6c' : '#c0c4cc' }">¥{{ formatMoney(row.unreceivedAmount) }}</span></template>
          </el-table-column>
          <el-table-column prop="dueDate" label="到期日" width="110" />
          <el-table-column prop="status" label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="recvStatusTag[row.status]" size="small">{{ recvStatusText[row.status] || row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80" align="center">
            <template #default="{ row }">
              <el-button v-if="row.status !== 'received'" type="success" link size="small" @click="openPaymentDialog('receivable', row)">收款</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pagination-wrapper">
          <el-pagination v-model:current-page="recvQuery.pageNum" v-model:page-size="recvQuery.pageSize"
            :total="recvTotal" :page-sizes="[10, 20, 50]" background layout="total, sizes, prev, pager, next"
            @size-change="loadReceivables" @current-change="loadReceivables" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="应付管理" name="payable">
        <!-- 搜索 -->
        <el-form :model="payQuery" inline size="small" style="margin-bottom: 12px">
          <el-form-item label="应付单号">
            <el-input v-model="payQuery.payableNo" placeholder="应付单号" clearable style="width: 140px" @keyup.enter="loadPayables" />
          </el-form-item>
          <el-form-item label="供应商">
            <el-input v-model="payQuery.supplierName" placeholder="供应商名称" clearable style="width: 140px" @keyup.enter="loadPayables" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="payQuery.status" placeholder="全部" clearable style="width: 120px">
              <el-option label="待付款" value="pending" />
              <el-option label="部分付款" value="partial_paid" />
              <el-option label="已付款" value="paid" />
              <el-option label="逾期" value="overdue" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadPayables">搜索</el-button>
            <el-button type="success" plain @click="handleExportPay">导出</el-button>
          </el-form-item>
        </el-form>

        <el-table :data="payData" v-loading="payLoading" border stripe size="small">
          <el-table-column prop="payableNo" label="应付单号" min-width="140" show-overflow-tooltip />
          <el-table-column prop="sourceNo" label="关联单据" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">
              <el-button v-if="row.sourceNo" type="primary" link size="small" @click="goSourceDoc(row)">{{ row.sourceNo }}</el-button>
              <span v-else>{{ row.sourceType === 'purchase_order' ? '采购单' : row.sourceType === 'outsource_order' ? '外协单' : '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="supplierName" label="供应商" min-width="130" show-overflow-tooltip />
          <el-table-column prop="amount" label="应付金额" width="110" align="right">
            <template #default="{ row }"><span style="font-weight: 500">¥{{ formatMoney(row.amount) }}</span></template>
          </el-table-column>
          <el-table-column prop="paidAmount" label="已付金额" width="110" align="right">
            <template #default="{ row }"><span style="color: #67c23a">¥{{ formatMoney(row.paidAmount) }}</span></template>
          </el-table-column>
          <el-table-column prop="unpaidAmount" label="未付金额" width="110" align="right">
            <template #default="{ row }"><span :style="{ color: row.unpaidAmount > 0 ? '#f56c6c' : '#c0c4cc' }">¥{{ formatMoney(row.unpaidAmount) }}</span></template>
          </el-table-column>
          <el-table-column prop="dueDate" label="到期日" width="110" />
          <el-table-column prop="status" label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="payStatusTag[row.status]" size="small">{{ payStatusText[row.status] || row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80" align="center">
            <template #default="{ row }">
              <el-button v-if="row.status !== 'paid'" type="warning" link size="small" @click="openPaymentDialog('payable', row)">付款</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pagination-wrapper">
          <el-pagination v-model:current-page="payQuery.pageNum" v-model:page-size="payQuery.pageSize"
            :total="payTotal" :page-sizes="[10, 20, 50]" background layout="total, sizes, prev, pager, next"
            @size-change="loadPayables" @current-change="loadPayables" />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 收付款弹窗 -->
    <el-dialog v-model="paymentVisible" :title="paymentType === 'receivable' ? '收款' : '付款'" width="500px" destroy-on-close>
      <el-form ref="paymentFormRef" :model="paymentForm" :rules="paymentRules" label-width="90px">
        <el-form-item :label="paymentType === 'receivable' ? '应收单号' : '应付单号'">
          <el-input :model-value="paymentRow?.receivableNo || paymentRow?.payableNo" disabled />
        </el-form-item>
        <el-form-item :label="paymentType === 'receivable' ? '待收金额' : '待付金额'">
          <el-input :model-value="'¥' + formatMoney(paymentRow?.unreceivedAmount || paymentRow?.unpaidAmount)" disabled />
        </el-form-item>
        <el-form-item :label="paymentType === 'receivable' ? '收款金额' : '付款金额'" prop="amount">
          <el-input-number v-model="paymentForm.amount" :min="0.01" :precision="2" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="付款方式" prop="paymentMethod">
          <el-select v-model="paymentForm.paymentMethod" placeholder="请选择" style="width: 100%">
            <el-option label="银行转账" value="wire" />
            <el-option label="支票" value="check" />
            <el-option label="现金" value="cash" />
            <el-option label="承兑汇票" value="acceptance" />
            <el-option label="其他" value="other" />
          </el-select>
        </el-form-item>
        <el-form-item label="付款日期" prop="paymentDate">
          <el-date-picker v-model="paymentForm.paymentDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="paymentForm.remark" placeholder="备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="paymentVisible = false">取消</el-button>
        <el-button type="primary" :loading="paymentLoading" @click="handlePayment">确认</el-button>
      </template>
    </el-dialog>

    <!-- 关联单据详情弹窗 -->
    <PurchaseOrderDetailDialog v-model:visible="relatedOrderVisible" :relatedOrderNo="relatedOrderNo" :invType="relatedInvType" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getReceivablePage, getPayablePage, addPayment, exportReceivables, exportPayables } from '@/api/finance'
import PurchaseOrderDetailDialog from '@/components/PurchaseOrderDetailDialog.vue'

// 关联单据弹窗
const relatedOrderVisible = ref(false)
const relatedOrderNo = ref('')
const relatedInvType = ref('')

function goSalesOrder(row) {
  if (!row.orderNo) return ElMessage.warning('未关联销售订单')
  relatedOrderNo.value = row.orderNo
  relatedInvType.value = 'sales_out'
  relatedOrderVisible.value = true
}

function goSourceDoc(row) {
  if (!row.sourceNo) return ElMessage.warning('未关联源单据')
  relatedOrderNo.value = row.sourceNo
  if (row.sourceType === 'purchase_order') relatedInvType.value = 'purchase_in'
  else if (row.sourceType === 'outsource_order') relatedInvType.value = 'outsource_in'
  else { ElMessage.info('未知的源单据类型'); return }
  relatedOrderVisible.value = true
}

const subTab = ref('receivable')
const formatMoney = (v) => v != null ? Number(v).toFixed(2) : '0.00'

const recvStatusText = { pending: '待收款', partial_received: '部分收款', received: '已收款', overdue: '逾期' }
const recvStatusTag = { pending: 'warning', partial_received: '', received: 'success', overdue: 'danger' }
const payStatusText = { pending: '待付款', partial_paid: '部分付款', paid: '已付款', overdue: '逾期' }
const payStatusTag = { pending: 'warning', partial_paid: '', paid: 'success', overdue: 'danger' }

// ===== 应收 =====
const recvLoading = ref(false)
const recvData = ref([])
const recvTotal = ref(0)
const recvQuery = reactive({ receivableNo: '', orderNo: '', customerName: '', status: '', pageNum: 1, pageSize: 20 })

async function loadReceivables() {
  recvLoading.value = true
  try {
    const res = await getReceivablePage({ ...recvQuery,
      receivableNo: recvQuery.receivableNo || undefined,
      orderNo: recvQuery.orderNo || undefined,
      customerName: recvQuery.customerName || undefined,
      status: recvQuery.status || undefined })
    recvData.value = res.data?.records || []
    recvTotal.value = res.data?.total || 0
  } catch {} finally { recvLoading.value = false }
}

// ===== 应付 =====
const payLoading = ref(false)
const payData = ref([])
const payTotal = ref(0)
const payQuery = reactive({ payableNo: '', supplierName: '', status: '', pageNum: 1, pageSize: 20 })

async function loadPayables() {
  payLoading.value = true
  try {
    const res = await getPayablePage({ ...payQuery, payableNo: payQuery.payableNo || undefined, supplierName: payQuery.supplierName || undefined, status: payQuery.status || undefined })
    payData.value = res.data?.records || []
    payTotal.value = res.data?.total || 0
  } catch {} finally { payLoading.value = false }
}

function downloadBlob(res, name) {
  const url = window.URL.createObjectURL(new Blob([res.data || res]))
  const a = document.createElement('a')
  a.href = url
  a.download = name
  a.click()
  window.URL.revokeObjectURL(url)
}

async function handleExportRecv() {
  try {
    const res = await exportReceivables({ receivableNo: recvQuery.receivableNo || undefined, orderNo: recvQuery.orderNo || undefined, customerName: recvQuery.customerName || undefined, status: recvQuery.status || undefined })
    downloadBlob(res, `应收_${new Date().toISOString().slice(0,10)}.xlsx`)
  } catch { ElMessage.error('导出失败') }
}

async function handleExportPay() {
  try {
    const res = await exportPayables({ payableNo: payQuery.payableNo || undefined, supplierName: payQuery.supplierName || undefined, status: payQuery.status || undefined })
    downloadBlob(res, `应付_${new Date().toISOString().slice(0,10)}.xlsx`)
  } catch { ElMessage.error('导出失败') }
}

// ===== 收付款弹窗 =====
const paymentVisible = ref(false)
const paymentLoading = ref(false)
const paymentType = ref('')
const paymentRow = ref(null)
const paymentFormRef = ref(null)
const paymentForm = reactive({ amount: 0, paymentMethod: 'wire', paymentDate: '', remark: '' })
const paymentRules = {
  amount: [{ required: true, message: '请输入金额', trigger: 'blur' }],
  paymentMethod: [{ required: true, message: '请选择方式', trigger: 'change' }],
  paymentDate: [{ required: true, message: '请选择日期', trigger: 'change' }]
}

function openPaymentDialog(type, row) {
  paymentType.value = type
  paymentRow.value = row
  paymentForm.amount = Number(row.unreceivedAmount || row.unpaidAmount || 0)
  paymentForm.paymentMethod = 'wire'
  paymentForm.paymentDate = new Date().toISOString().slice(0, 10)
  paymentForm.remark = ''
  paymentVisible.value = true
}

async function handlePayment() {
  await paymentFormRef.value.validate()
  paymentLoading.value = true
  try {
    await addPayment({
      relatedType: paymentType.value,
      relatedId: paymentRow.value.id,
      amount: paymentForm.amount,
      paymentDate: paymentForm.paymentDate,
      paymentMethod: paymentForm.paymentMethod,
      remark: paymentForm.remark
    })
    ElMessage.success(paymentType.value === 'receivable' ? '收款成功' : '付款成功')
    paymentVisible.value = false
    if (paymentType.value === 'receivable') loadReceivables()
    else loadPayables()
  } catch {} finally { paymentLoading.value = false }
}

onMounted(() => { loadReceivables(); loadPayables() })
</script>

<style scoped>
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
