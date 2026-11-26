<template>
  <div class="page-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="收料单号">
          <el-input v-model="queryParams.receiptNo" placeholder="收料单号" clearable style="width: 150px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="加工单号">
          <el-input v-model="queryParams.orderNo" placeholder="加工单号" clearable style="width: 150px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="供应商">
          <el-input v-model="queryParams.supplierName" placeholder="供应商名称" clearable style="width: 140px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="产品名称">
          <el-input v-model="queryParams.productName" placeholder="产品名称" clearable style="width: 160px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="收料状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="待收料" value="pending_receive" />
            <el-option label="收料中" value="receiving" />
            <el-option label="已收料" value="received" />
            <el-option label="办结" value="closed" />
          </el-select>
        </el-form-item>
        <el-form-item label="质检状态">
          <el-select v-model="queryParams.qcStatus" placeholder="全部" clearable style="width: 120px">
            <el-option label="无质检" value="none" />
            <el-option label="待质检" value="pending_qc" />
            <el-option label="已质检" value="qc_done" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据表格 -->
    <el-card shadow="never" class="table-card">
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="receiptNo" label="收料单号" min-width="140" show-overflow-tooltip />
        <el-table-column prop="orderNo" label="加工单号" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button v-if="row.orderNo" type="primary" link @click="openOrderDetail(row.orderNo)">{{ row.orderNo }}</el-button>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="supplierName" label="供应商" min-width="130" show-overflow-tooltip />
        <el-table-column prop="productName" label="产品名称" min-width="130" show-overflow-tooltip />
        <el-table-column prop="stepName" label="工序" width="100" show-overflow-tooltip />
        <el-table-column prop="receivedQty" label="收料数量" width="100" align="right" />
        <el-table-column prop="batchNo" label="批次号" width="120" show-overflow-tooltip />
        <el-table-column prop="status" label="收料状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag[row.status]" size="small">{{ statusText[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="qcStatus" label="质检状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="qcStatusTag[row.qcStatus]" size="small">{{ qcStatusText[row.qcStatus] || row.qcStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="qualifiedQty" label="合格数" width="80" align="right" />
        <el-table-column prop="defectQty" label="不合格数" width="80" align="right" />
        <el-table-column prop="invTaskQty" label="库存任务数" width="90" align="right" />
        <el-table-column prop="stockInQty" label="入库数" width="80" align="right" />
        <el-table-column prop="createTime" label="创建时间" width="160">
          <template #default="{ row }">{{ $fmtDate(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'pending_receive'" type="primary" link size="small" @click="openReceiveDialog(row)">收料</el-button>
            <el-button type="primary" link size="small" @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.status !== 'closed'" type="warning" link size="small" @click="handleClose(row)">办结</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50, 100]" :total="total" background layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <!-- 收料弹窗 -->
    <el-dialog v-model="receiveVisible" title="收料确认" width="500px" destroy-on-close>
      <el-descriptions :column="1" border size="small" style="margin-bottom: 16px">
        <el-descriptions-item label="收料单号">{{ receiveRow.receiptNo }}</el-descriptions-item>
        <el-descriptions-item label="物料编码">{{ receiveRow.materialNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="产品名称">{{ receiveRow.productName }}</el-descriptions-item>
        <el-descriptions-item label="规格">{{ $fmtSpec(receiveRow.spec) || '-' }}</el-descriptions-item>
        <el-descriptions-item label="单位">{{ receiveRow.unit || '-' }}</el-descriptions-item>
        <el-descriptions-item label="收料数量">{{ receiveRow.receivedQty }}</el-descriptions-item>
        <el-descriptions-item label="批次号">{{ receiveRow.batchNo || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-form label-width="80px">
        <el-form-item label="目标仓库" required>
          <el-select v-model="receiveForm.warehouseId" placeholder="请选择仓库" style="width: 100%">
            <el-option v-for="w in warehouseOptions" :key="w.id" :label="w.warehouseName" :value="w.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="质检选项">
          <el-radio-group v-model="receiveForm.needQc">
            <el-radio :label="true">报检</el-radio>
            <el-radio :label="false">放行</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="receiveVisible = false">取消</el-button>
        <el-button type="primary" :loading="receiveLoading" @click="doReceive">确认收料</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" title="外协收料单详情" size="700px" destroy-on-close>
      <template v-if="detailData">
        <el-descriptions :column="2" border size="small" style="margin-bottom: 16px">
          <el-descriptions-item label="收料单号">{{ detailData.receiptNo }}</el-descriptions-item>
          <el-descriptions-item label="加工单号">{{ detailData.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="供应商">{{ detailData.supplierName }}</el-descriptions-item>
          <el-descriptions-item label="产品名称">{{ detailData.productName }}</el-descriptions-item>
          <el-descriptions-item label="规格">{{ $fmtSpec(detailData.spec) || '-' }}</el-descriptions-item>
          <el-descriptions-item label="单位">{{ detailData.unit || '-' }}</el-descriptions-item>
          <el-descriptions-item label="收料数量">{{ detailData.receivedQty }}</el-descriptions-item>
          <el-descriptions-item label="批次号">{{ detailData.batchNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="收料状态">
            <el-tag :type="statusTag[detailData.status]" size="small">{{ statusText[detailData.status] }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="质检状态">
            <el-tag :type="qcStatusTag[detailData.qcStatus]" size="small">{{ qcStatusText[detailData.qcStatus] }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="合格数">{{ detailData.qualifiedQty }}</el-descriptions-item>
          <el-descriptions-item label="不合格数">{{ detailData.defectQty }}</el-descriptions-item>
          <el-descriptions-item label="库存任务数">{{ detailData.invTaskQty }}</el-descriptions-item>
          <el-descriptions-item label="入库数">{{ detailData.stockInQty }}</el-descriptions-item>
        </el-descriptions>

        <el-tabs v-model="detailTab">
          <el-tab-pane label="质检任务" name="qc">
            <el-table :data="detailQcTasks" v-loading="detailLoading" border stripe size="small">
              <el-table-column prop="taskNo" label="任务编号" min-width="140" />
              <el-table-column prop="result" label="状态" width="100" align="center">
                <template #default="{ row }">
                  <el-tag v-if="row.result === 'completed'" type="success" size="small">检测完成</el-tag>
                  <el-tag v-else-if="row.result === 'inspecting'" size="small">检验中</el-tag>
                  <el-tag v-else type="info" size="small">{{ row.result === 'pending' ? '待检' : row.result }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="planQty" label="总数量" width="80" align="right" />
              <el-table-column prop="passQty" label="合格数" width="80" align="right" />
              <el-table-column prop="failQty" label="不合格数" width="80" align="right" />
              <el-table-column prop="inspectedAt" label="检验时间" width="160">
                <template #default="{ row }">{{ $fmtDate(row.inspectedAt) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="80" align="center">
                <template #default="{ row }">
                  <el-button type="primary" link size="small" @click="openQcDetail(row)">详情</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="库存任务" name="inv">
            <el-table :data="detailInvTasks" v-loading="detailLoading" border stripe size="small">
              <el-table-column prop="taskNo" label="任务编号" min-width="140" />
              <el-table-column prop="invType" label="任务类型" width="160">
                <template #default="{ row }">{{ invTypeText[row.invType] || row.invType }}</template>
              </el-table-column>
              <el-table-column prop="status" label="状态" width="90" align="center">
                <template #default="{ row }">
                  <el-tag :type="invStatusTag[row.status]" size="small">{{ invStatusText[row.status] || row.status }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="quantity" label="数量" width="80" align="right" />
              <el-table-column prop="processedQty" label="已处理" width="80" align="right" />
              <el-table-column prop="createTime" label="创建时间" width="160">
                <template #default="{ row }">{{ $fmtDate(row.createTime) }}</template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </template>
    </el-drawer>

    <!-- 质检详情弹窗 -->
    <el-dialog v-model="qcDetailVisible" title="质检任务详情" width="700px" destroy-on-close>
      <div v-loading="qcDetailLoading">
        <template v-if="qcDetailData">
          <el-descriptions :column="2" border size="small" style="margin-bottom:12px">
            <el-descriptions-item label="任务编号">{{ qcDetailData.taskNo }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="qcDetailData.result === 'completed' ? 'success' : 'info'" size="small">
                {{ { pending: '待检', inspecting: '检验中', completed: '检测完成' }[qcDetailData.result] || qcDetailData.result }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="总数量">{{ qcDetailData.planQty }}</el-descriptions-item>
            <el-descriptions-item label="质检数量">{{ qcDetailData.sampleQty || qcDetailData.planQty }}</el-descriptions-item>
            <el-descriptions-item label="合格数">{{ qcDetailData.passQty || 0 }}</el-descriptions-item>
            <el-descriptions-item label="不合格数">{{ qcDetailData.failQty || 0 }}</el-descriptions-item>
            <el-descriptions-item label="检验员">{{ qcDetailData.inspectorName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="检验时间">{{ $fmtDate(qcDetailData.inspectedAt) }}</el-descriptions-item>
          </el-descriptions>
          <div v-if="qcDetailData.records && qcDetailData.records.length" style="margin-top:12px">
            <h4 style="font-size:14px;margin-bottom:8px">检验记录</h4>
            <el-table :data="qcDetailData.records" border stripe size="small">
              <el-table-column label="类型" width="80" align="center">
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
        </template>
      </div>
    </el-dialog>

    <!-- 关联外协订单详情弹窗 -->
    <PurchaseOrderDetailDialog v-model:visible="orderDetailVisible" :related-order-no="orderDetailNo" inv-type="outsource_in" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PurchaseOrderDetailDialog from '@/components/PurchaseOrderDetailDialog.vue'
import { getOutsourceReceiptPage, getOutsourceReceiptDetail, receiveOutsourceReceipt, closeOutsourceReceipt, getOutsourceReceiptQcTasks, getOutsourceReceiptInvTasks } from '@/api/outsource'
import { getQcTaskDetail } from '@/api/quality'
import { getWarehouseList } from '@/api/system'

const statusText = { pending_receive: '待收料', receiving: '收料中', received: '已收料', closed: '办结' }
const statusTag = { pending_receive: 'info', receiving: 'warning', received: 'success', closed: '' }
const qcStatusText = { none: '无质检', pending_qc: '待质检', qc_done: '已质检' }
const qcStatusTag = { none: 'info', pending_qc: 'warning', qc_done: 'success' }
const invTypeText = { outsource_in: '外协入库', outsource_in_qualified: '外协入库-质检合格', outsource_in_unqualified: '外协入库-质检不合格', purchase_in: '采购入库' }
const invStatusText = { pending: '待处理', partial: '部分处理', completed: '已完成' }
const invStatusTag = { pending: 'info', partial: 'warning', completed: 'success' }

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const queryParams = reactive({ receiptNo: '', orderNo: '', supplierName: '', productName: '', status: '', qcStatus: '', pageNum: 1, pageSize: 20 })

async function loadData() {
  loading.value = true
  try {
    const res = await getOutsourceReceiptPage(queryParams)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch {} finally { loading.value = false }
}

function handleSearch() { queryParams.pageNum = 1; loadData() }
function handleReset() {
  queryParams.receiptNo = ''; queryParams.orderNo = ''; queryParams.supplierName = ''; queryParams.productName = ''
  queryParams.status = ''; queryParams.qcStatus = ''; queryParams.pageNum = 1
  loadData()
}

const warehouseOptions = ref([])
async function loadWarehouseOptions() {
  try { const res = await getWarehouseList(); warehouseOptions.value = res.data || [] } catch {}
}

const receiveVisible = ref(false)
const receiveLoading = ref(false)
const receiveRow = ref({})
const receiveForm = reactive({ warehouseId: null, needQc: false })

function openReceiveDialog(row) {
  receiveRow.value = row
  receiveForm.warehouseId = row.warehouseId || null
  receiveForm.needQc = false
  getOutsourceReceiptDetail(row.id).then(res => {
    const d = res.data || {}
    if (d.defaultWarehouseId && !receiveForm.warehouseId) receiveForm.warehouseId = d.defaultWarehouseId
  }).catch(() => {})
  receiveVisible.value = true
}

async function doReceive() {
  if (!receiveForm.warehouseId) { ElMessage.warning('请选择仓库'); return }
  receiveLoading.value = true
  try {
    await receiveOutsourceReceipt(receiveRow.value.id, { warehouseId: receiveForm.warehouseId, needQc: receiveForm.needQc })
    ElMessage.success(receiveForm.needQc ? '已创建质检任务' : '已创建库存任务')
    receiveVisible.value = false
    loadData()
  } catch {} finally { receiveLoading.value = false }
}

async function handleClose(row) {
  try {
    await ElMessageBox.confirm(`确定办结收料单「${row.receiptNo}」？办结后不可再操作。`, '提示', { type: 'warning' })
    await closeOutsourceReceipt(row.id)
    ElMessage.success('办结成功')
    loadData()
  } catch {}
}

const detailVisible = ref(false)
const detailLoading = ref(false)
const detailData = ref(null)
const detailTab = ref('qc')
const detailQcTasks = ref([])
const detailInvTasks = ref([])

async function openDetail(row) {
  detailVisible.value = true
  detailLoading.value = true
  detailTab.value = 'qc'
  try {
    const [detailRes, qcRes, invRes] = await Promise.all([
      getOutsourceReceiptDetail(row.id),
      getOutsourceReceiptQcTasks(row.id),
      getOutsourceReceiptInvTasks(row.id)
    ])
    detailData.value = detailRes.data
    detailQcTasks.value = qcRes.data || []
    detailInvTasks.value = invRes.data || []
  } catch {} finally { detailLoading.value = false }
}

// 关联订单详情
const orderDetailVisible = ref(false)
const orderDetailNo = ref('')
function openOrderDetail(orderNo) {
  orderDetailNo.value = orderNo
  orderDetailVisible.value = true
}

const qcDetailVisible = ref(false)
const qcDetailData = ref(null)
const qcDetailLoading = ref(false)
async function openQcDetail(row) {
  if (!row.id) return
  qcDetailVisible.value = true
  qcDetailLoading.value = true
  try {
    const res = await getQcTaskDetail(row.id)
    qcDetailData.value = res.data || {}
  } catch {} finally { qcDetailLoading.value = false }
}

onMounted(() => { loadData(); loadWarehouseOptions() })
</script>

<style scoped lang="scss">
.page-container { display: flex; flex-direction: column; gap: 12px; }
.search-card { :deep(.el-card__body) { padding-bottom: 2px; } }
.table-card { flex: 1; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 12px; }
</style>
