<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="任务编号">
          <el-input v-model="queryParams.taskNo" placeholder="任务编号" clearable style="width:160px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="queryParams.invType" placeholder="全部" clearable style="width:140px">
            <el-option v-for="t in invTypeOptions" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width:120px">
            <el-option label="待处理" value="pending" />
            <el-option label="部分完成" value="partial" />
            <el-option label="已完成" value="completed" />
            <el-option label="已退回" value="returned" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联单据号">
          <el-input v-model="queryParams.relatedOrderNo" placeholder="关联单据号" clearable style="width:160px" />
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker v-model="dateRange" type="daterange" range-separator="-" start-placeholder="开始" end-placeholder="结束"
            value-format="YYYY-MM-DD" style="width:240px" @change="handleSearch" />
        </el-form-item>
        <el-form-item label="显示已完成">
          <el-switch v-model="queryParams.includeCompleted" @change="handleSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
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
            <template v-else-if="col.key === 'sourceType'">
              <el-tag type="info" size="small">{{ sourceTypeLabel(row.sourceType, row.invType) }}</el-tag>
            </template>
            <template v-else-if="col.key === 'relatedOrderNo'">
              <el-button v-if="row.relatedOrderNo" link type="primary" style="padding:0"
                @click="openRelatedOrder(row)">{{ row.relatedOrderNo }}</el-button>
              <span v-else style="color:#c0c4cc">-</span>
            </template>
            <template v-else-if="col.key === 'status'">
              <el-tag :type="taskStatusTagType(row.status)" size="small">{{ taskStatusLabel(row.status) }}</el-tag>
            </template>
            <template v-else-if="col.key === 'createTime'">{{ $fmtDate(row.createTime) }}</template>
            <template v-else>{{ row[col.key] || '-' }}</template>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看</el-button>
            <el-button v-if="row.status !== 'completed' && row.status !== 'returned'" v-permission="'inventory:task:process'" link type="success" @click="openProcess(row)">处理</el-button>
            <el-button v-if="canReturn(row)" v-permission="'inventory:task:process'" link type="danger" @click="openReturn(row)">退回</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
          :page-sizes="[10,20,50,100]" :total="total" layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <!-- 列配置 -->
    <ColumnConfigDialog ref="colConfigDialogRef" :all-columns="allColumns" :default-columns="TASK_DEFAULT_COLUMNS" @save="saveConfig" />

    <!-- 关联单据弹窗（采购订单/外协订单等） -->
    <PurchaseOrderDetailDialog v-model:visible="relatedOrderVisible" :relatedOrderNo="relatedOrderNo" :invType="relatedInvType" />

    <!-- 来源详情弹窗（收料单/质检任务） -->
    <el-dialog v-model="sourceDetailVisible" :title="{ purchase_receipt: '采购收料单详情', outsource_receipt: '外协收料单详情', qc_task: '质检任务详情' }[sourceDetailType] || '详情'" width="650px" destroy-on-close>
      <div v-loading="sourceDetailLoading">
        <template v-if="sourceDetailData">
          <!-- 采购收料单 -->
          <template v-if="sourceDetailType === 'purchase_receipt'">
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item label="收料单号">{{ sourceDetailData.receiptNo }}</el-descriptions-item>
              <el-descriptions-item label="物料名称">{{ sourceDetailData.materialName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="收料数量">{{ sourceDetailData.actualQty }}</el-descriptions-item>
              <el-descriptions-item label="状态">{{ { pending_receive: '待收料', receiving: '收料中', received: '已收料', closed: '办结' }[sourceDetailData.status] || sourceDetailData.status }}</el-descriptions-item>
              <el-descriptions-item label="质检状态">{{ { none: '无质检', pending_qc: '待质检', qc_done: '已质检' }[sourceDetailData.qcStatus] || sourceDetailData.qcStatus }}</el-descriptions-item>
              <el-descriptions-item label="合格数">{{ sourceDetailData.qualifiedQty || 0 }}</el-descriptions-item>
              <el-descriptions-item label="不合格数">{{ sourceDetailData.defectQty || 0 }}</el-descriptions-item>
              <el-descriptions-item label="入库数">{{ sourceDetailData.stockInQty || 0 }}</el-descriptions-item>
            </el-descriptions>
          </template>
          <!-- 外协收料单 -->
          <template v-else-if="sourceDetailType === 'outsource_receipt'">
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item label="收料单号">{{ sourceDetailData.receiptNo }}</el-descriptions-item>
              <el-descriptions-item label="产品名称">{{ sourceDetailData.productName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="收料数量">{{ sourceDetailData.receivedQty }}</el-descriptions-item>
              <el-descriptions-item label="状态">{{ { pending_receive: '待收料', receiving: '收料中', received: '已收料', closed: '办结' }[sourceDetailData.status] || sourceDetailData.status }}</el-descriptions-item>
              <el-descriptions-item label="质检状态">{{ { none: '无质检', pending_qc: '待质检', qc_done: '已质检' }[sourceDetailData.qcStatus] || sourceDetailData.qcStatus }}</el-descriptions-item>
              <el-descriptions-item label="合格数">{{ sourceDetailData.qualifiedQty || 0 }}</el-descriptions-item>
              <el-descriptions-item label="不合格数">{{ sourceDetailData.defectQty || 0 }}</el-descriptions-item>
              <el-descriptions-item label="入库数">{{ sourceDetailData.stockInQty || 0 }}</el-descriptions-item>
            </el-descriptions>
          </template>
          <!-- 质检任务 -->
          <template v-else-if="sourceDetailType === 'qc_task'">
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item label="任务编号">{{ sourceDetailData.taskNo }}</el-descriptions-item>
              <el-descriptions-item label="状态">{{ { pending: '待检', inspecting: '检验中', completed: '检测完成' }[sourceDetailData.result] || sourceDetailData.result }}</el-descriptions-item>
              <el-descriptions-item label="总数量">{{ sourceDetailData.planQty }}</el-descriptions-item>
              <el-descriptions-item label="质检数量">{{ sourceDetailData.sampleQty || sourceDetailData.planQty }}</el-descriptions-item>
              <el-descriptions-item label="合格数">{{ sourceDetailData.passQty || 0 }}</el-descriptions-item>
              <el-descriptions-item label="不合格数">{{ sourceDetailData.failQty || 0 }}</el-descriptions-item>
              <el-descriptions-item label="检验员">{{ sourceDetailData.inspectorName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="结论">{{ sourceDetailData.conclusion || '-' }}</el-descriptions-item>
            </el-descriptions>
            <div v-if="sourceDetailData.records && sourceDetailData.records.length" style="margin-top:12px">
              <h4 style="font-size:14px;margin-bottom:8px">检验记录</h4>
              <el-table :data="sourceDetailData.records" border stripe size="small">
                <el-table-column label="类型" width="80" align="center">
                  <template #default="{ row }">
                    <el-tag :type="row.recordType === 'unqualified' ? 'danger' : 'success'" size="small">{{ row.recordType === 'unqualified' ? '不合格' : '合格' }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="quantity" label="数量" width="100" align="right" />
                <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />
              </el-table>
            </div>
          </template>
        </template>
      </div>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="库存任务详情" width="860px" destroy-on-close>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="任务编号">{{ detail.taskNo }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ invTypeLabel(detail.invType) }}</el-descriptions-item>
        <el-descriptions-item label="关联单据号">{{ detail.relatedOrderNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="批次号">{{ detail.batchNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="taskStatusTagType(detail.status)" size="small">{{ taskStatusLabel(detail.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="申请人">{{ detail.applicantName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间" :span="2">{{ $fmtDate(detail.createTime) }}</el-descriptions-item>
      </el-descriptions>
      <div style="margin-top:16px; font-weight:600; margin-bottom:8px">物料明细</div>
      <el-table :data="detail.items" border size="small">
        <el-table-column prop="materialNo" label="物料编号" width="120" />
        <el-table-column prop="materialName" label="物料名称" min-width="130" />
        <el-table-column prop="spec" label="规格" width="100">
          <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="55" align="center" />
        <el-table-column label="在库数量" width="90" align="right">
          <template #default="{ row }">{{ row.onHandQty ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="可用数量" width="90" align="right">
          <template #default="{ row }">
            <span :style="{ color: row.availableQty > 0 ? '#67c23a' : '#909399' }">{{ row.availableQty ?? '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="进度" width="150" align="center">
          <template #default="{ row }">
            <el-progress
              :percentage="calcProgress(row)"
              :status="row.processedQty >= row.quantity ? 'success' : ''"
              :stroke-width="8"
            />
          </template>
        </el-table-column>
        <el-table-column label="已处理/总数" width="120" align="right">
          <template #default="{ row }">{{ row.processedQty ?? 0 }} / {{ row.quantity }}</template>
        </el-table-column>
        <el-table-column label="辅助单位" width="70" align="center">
          <template #default="{ row }">{{ row.secondaryUnit || '-' }}</template>
        </el-table-column>
        <el-table-column label="已处理/辅助总数" width="120" align="right">
          <template #default="{ row }">
            <template v-if="row.secondaryUnit">{{ row.secondaryProcessedQty ?? 0 }} / {{ row.secondaryQty ?? '-' }}</template>
            <template v-else>-</template>
          </template>
        </el-table-column>
        <el-table-column prop="unitPrice" label="单价" width="90" align="right" />
      </el-table>
      <template #footer><el-button @click="detailVisible = false">关闭</el-button></template>
    </el-dialog>

    <!-- 处理弹窗 -->
    <el-dialog v-model="processVisible" title="处理任务" width="1200px" destroy-on-close>
      <el-alert type="info" :closable="false" style="margin-bottom:12px">
        请为每种物料选择仓库/库位，并填写本次处理数量（不超过剩余数量）
      </el-alert>
      <el-table :data="processItems" border size="small">
        <el-table-column prop="materialNo" label="物料编码" width="130" show-overflow-tooltip />
        <el-table-column prop="materialName" label="物料名称" min-width="130" />
        <el-table-column prop="spec" label="规格" width="90">
          <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="55" align="center" />
        <el-table-column label="在库数量" width="85" align="right">
          <template #default="{ row }">{{ row.onHandQty ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="可用数量" width="85" align="right">
          <template #default="{ row }">
            <span :style="{ color: row.availableQty > 0 ? '#67c23a' : '#909399' }">{{ row.availableQty ?? '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="辅助单位" width="70" align="center">
          <template #default="{ row }">{{ row.secondaryUnit || '-' }}</template>
        </el-table-column>
        <el-table-column label="剩余数量" width="90" align="right">
          <template #default="{ row }">{{ (row.quantity - (row.processedQty || 0)).toFixed(4) }}</template>
        </el-table-column>
        <el-table-column label="辅助剩余" width="90" align="right">
          <template #default="{ row }">
            <template v-if="row.secondaryUnit">{{ ((row.secondaryQty || 0) - (row.secondaryProcessedQty || 0)).toFixed(4) }}</template>
            <template v-else>-</template>
          </template>
        </el-table-column>
        <el-table-column width="130">
          <template #header><span style="color:#F56C6C">* </span>本次数量</template>
          <template #default="{ row }">
            <el-input-number v-model="row.processQty" :min="1" :max="row.quantity - (row.processedQty || 0)"
              :precision="4" size="small" style="width:100%" @change="onProcessQtyChange(row)" />
          </template>
        </el-table-column>
        <el-table-column width="130">
          <template #header>辅助数量</template>
          <template #default="{ row }">
            <el-input-number v-if="row.secondaryUnit" v-model="row.secondaryProcessQty" :min="0"
              :precision="4" size="small" style="width:100%" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column min-width="130">
          <template #header><span style="color:#F56C6C">* </span>仓库</template>
          <template #default="{ row }">
            <el-select v-model="row.warehouseId" size="small" style="width:100%" placeholder="请选择仓库"
              @change="onProcessItemWhChange(row)">
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
      </el-table>
      <el-form style="margin-top:12px" label-width="80px">
        <el-form-item label="历史单号">
          <el-input v-model="processHistoryNo" placeholder="不填则自动生成（INH+时间戳）" clearable style="width:300px" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="processRemark" type="textarea" :rows="2" placeholder="本次处理备注（选填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="processVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleProcessSubmit">提交</el-button>
      </template>
    </el-dialog>
    <!-- 退回弹窗 -->
    <el-dialog v-model="returnVisible" title="退回库存任务" width="480px" destroy-on-close>
      <el-alert type="warning" :closable="false" style="margin-bottom:16px">
        退回后任务状态变为"已退回"，相关采购订单/入库申请状态将同步更新，此操作不可撤销。
      </el-alert>
      <el-form label-width="80px">
        <el-form-item label="退回原因" required>
          <el-input v-model="returnReason" type="textarea" :rows="3" placeholder="请填写退回原因（必填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="returnVisible = false">取消</el-button>
        <el-button type="danger" :loading="returnLoading" @click="handleReturnSubmit">确认退回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Setting } from '@element-plus/icons-vue'
import { getInvTaskPage, getInvTaskDetail, processInvTask, returnInvTask, getMaterialStocks, getStockQty } from '@/api/inventory'
import { getReceiptDetail } from '@/api/purchase'
import { getOutsourceReceiptDetail } from '@/api/outsource'
import { getQcTaskDetail } from '@/api/quality'
import { getWarehouseSelect, getWarehouseLocationList } from '@/api/system'
import PurchaseOrderDetailDialog from '@/components/PurchaseOrderDetailDialog.vue'
import { useColumnConfig } from '@/composables/useColumnConfig'
import ColumnConfigDialog from '@/components/ColumnConfigDialog.vue'

// ==================== 列配置 ====================
const TASK_DEFAULT_COLUMNS = [
  { key: 'taskNo', label: '任务编号', width: 175 },
  { key: 'invType', label: '类型', width: 120, align: 'center' },
  { key: 'sourceType', label: '来源', width: 110, align: 'center' },
  { key: 'relatedOrderNo', label: '关联单据号', minWidth: 175 },
  { key: 'status', label: '状态', width: 110, align: 'center' },
  { key: 'applicantName', label: '申请人', width: 100 },
  { key: 'createTime', label: '创建时间', minWidth: 165 },
]
const colConfigDialogRef = ref(null)
const { visibleColumns, allColumns, loadColumnConfig, saveConfig } = useColumnConfig('inventory_task', TASK_DEFAULT_COLUMNS)

// ==================== 字典 ====================
const invTypeOptions = [
  { label: '采购入库', value: 'purchase_in' },
  { label: '采购入库-质检合格', value: 'purchase_in_qualified' },
  { label: '采购入库-质检不合格', value: 'purchase_in_unqualified' },
  { label: '生产入库', value: 'production_in' },
  { label: '半成品入库', value: 'semi_in' },
  { label: '外协入库', value: 'outsource_in' },
  { label: '外协入库-质检合格', value: 'outsource_in_qualified' },
  { label: '外协入库-质检不合格', value: 'outsource_in_unqualified' },
  { label: '退货入库', value: 'return_in' },
  { label: '报废入库', value: 'scrap_in' },
  { label: '其他入库', value: 'other_in' },
  { label: '生产领料', value: 'production_out' },
  { label: '销售发货', value: 'sales_out' },
  { label: '外协发料', value: 'outsource_out' },
  { label: '报废出库', value: 'scrap_out' },
  { label: '其他出库', value: 'other_out' },
  { label: '调拨出库', value: 'transfer_out' },
  { label: '调拨入库', value: 'transfer_in' },
  { label: '采购退货', value: 'purchase_return' },
  { label: '生产退料', value: 'production_return' }
]
const invTypeMap = Object.fromEntries(invTypeOptions.map(t => [t.value, t.label]))
function invTypeLabel(v) { return invTypeMap[v] || v || '-' }
function invTypeTagType(v) {
  if (v === 'purchase_return') return 'danger'
  if (v === 'production_return') return 'warning'
  return v?.endsWith('_in') ? 'success' : v?.endsWith('_out') ? 'danger' : 'info'
}
function taskStatusLabel(s) {
  return { pending: '待处理', partial: '部分完成', completed: '已完成', returned: '已退回' }[s] || s || '-'
}
function taskStatusTagType(s) {
  return { pending: 'warning', partial: '', completed: 'success', returned: 'danger' }[s] || 'info'
}
const SOURCE_TYPE_LABELS = {
  inv_request: '库存申请',
  purchase_order: '采购订单',
  purchase_receipt: '采购收料',
  purchase_return: '采购退货',
  sales_order: '销售订单',
  work_order: '生产工单',
  outsource_order: '外协订单',
  outsource_receipt: '外协收料',
  qc_task: '质检任务',
  safety_trigger: '安全库存',
  manual: '手动创建',
}
// production_return 不可被退回
const NON_RETURNABLE_TYPES = new Set([
  'production_return', 'purchase_return', 'production_in',
  'purchase_in_qualified', 'purchase_in_unqualified',
  'outsource_in_qualified', 'outsource_in_unqualified', 'scrap_in'
])
function sourceTypeLabel(v, invType) {
  // inv_request 同时包含入库申请和出库申请, 按 invType 后缀区分
  if (v === 'inv_request') {
    if (invType && invType.endsWith('_out')) return '出库申请'
    if (invType && invType.endsWith('_in')) return '入库申请'
    return '库存申请'
  }
  return SOURCE_TYPE_LABELS[v] || v || '-'
}

function canReturn(row) {
  if (row.status !== 'pending') return false
  // 采购入库：仅来源为采购收料可退回，来源为质检任务不可退回
  if (row.invType === 'purchase_in') return row.sourceType === 'purchase_receipt'
  // 外协入库：仅来源为外协收料可退回
  if (row.invType === 'outsource_in') return row.sourceType === 'outsource_receipt'
  // 其他可退回类型
  if (['production_out', 'outsource_out'].includes(row.invType)) return true
  return false
}
function calcProgress(row) {
  if (!row.quantity || row.quantity === 0) return 0
  return Math.min(100, Math.round((row.processedQty || 0) / row.quantity * 100))
}

// ==================== 查询 ====================
const queryParams = reactive({ taskNo: '', invType: '', status: '', relatedOrderNo: '', includeCompleted: false, dateStart: '', dateEnd: '', pageNum: 1, pageSize: 20 })
const dateRange = ref([])
const loading = ref(false)
const tableData = ref([])
const total = ref(0)

async function loadData() {
  loading.value = true
  try {
    const params = { ...queryParams, dateStart: dateRange.value?.[0] || '', dateEnd: dateRange.value?.[1] || '' }
    const res = await getInvTaskPage(params)
    tableData.value = res.data?.records || res.data?.list || []
    total.value = res.data?.total || 0
  } finally { loading.value = false }
}
function handleSearch() { queryParams.pageNum = 1; loadData() }
function handleReset() {
  Object.assign(queryParams, { taskNo: '', invType: '', status: '', relatedOrderNo: '', includeCompleted: false, pageNum: 1 })
  dateRange.value = []
  loadData()
}

// ==================== 详情 ====================
const detailVisible = ref(false)
const detail = ref({})

async function openDetail(row) {
  const res = await getInvTaskDetail(row.id)
  detail.value = res.data || {}
  detailVisible.value = true
  // 加载库存数据
  const ids = (detail.value.items || []).map(i => i.materialId).filter(Boolean)
  if (ids.length) {
    try {
      const stockRes = await getMaterialStocks(ids)
      // 后端返回 { materialId: { totalQty, availableQty } }, 是对象不是数组
      const stockMap = stockRes.data || {}
      detail.value.items.forEach(item => {
        const s = item.materialId ? stockMap[item.materialId] : null
        if (s) {
          item.onHandQty = s.totalQty ?? s.onHandQty ?? 0
          item.availableQty = s.availableQty ?? 0
        }
      })
    } catch {}
  }
}

// ==================== 处理 ====================
const processVisible = ref(false)
const submitLoading = ref(false)
const processTaskId = ref(null)
const processItems = ref([])
const processRemark = ref('')
const processHistoryNo = ref('')
const warehouseOptions = ref([])

function getConversionRatio(unitConfig) {
  if (!unitConfig) return null
  try {
    const cfg = JSON.parse(unitConfig)
    return cfg.conversions?.[0]?.ratio || null
  } catch { return null }
}

function onProcessQtyChange(row) {
  const ratio = getConversionRatio(row.unitConfig)
  if (ratio) {
    row.secondaryProcessQty = parseFloat((row.processQty * ratio).toFixed(4))
  }
}

async function openProcess(row) {
  const res = await getInvTaskDetail(row.id)
  const d = res.data || {}
  processTaskId.value = row.id
  processRemark.value = ''
  processHistoryNo.value = ''

  // 为每个 item 加载库位选项
  const itemsWithWh = await Promise.all((d.items || []).map(async item => {
    let locationOptions = []
    const whId = item.warehouseId || null
    if (whId) {
      try { const r = await getWarehouseLocationList(whId); locationOptions = r.data || [] } catch {}
    }
    const secondaryRemaining = item.secondaryUnit
      ? Math.max(0, (item.secondaryQty || 0) - (item.secondaryProcessedQty || 0))
      : null
    return {
      ...item,
      warehouseId: whId,
      locationId: item.locationId || null,
      locationOptions,
      processQty: Math.max(0, (item.quantity || 0) - (item.processedQty || 0)),
      secondaryProcessQty: secondaryRemaining,
      unitConfig: item.unitConfig || null
    }
  }))
  // 加载库存数据: 优先按 item 自己的 warehouseId 拿单仓库存; 没指定仓库的回退跨仓汇总
  const matIds = itemsWithWh.map(i => i.materialId).filter(Boolean)
  if (matIds.length) {
    try {
      const stockRes = await getMaterialStocks(matIds)
      const stockMap = stockRes.data || {}
      itemsWithWh.forEach(item => {
        const s = item.materialId ? stockMap[item.materialId] : null
        if (s) {
          item.onHandQty = Number(s.totalQty ?? s.onHandQty ?? 0)
          item.availableQty = Number(s.availableQty ?? 0)
        }
      })
    } catch {}
    // 已经选了仓库的 item: 单独刷新成单仓库存(与出库校验口径一致)
    await Promise.all(itemsWithWh.filter(i => i.materialId && i.warehouseId).map(async item => {
      try {
        const r = await getStockQty(item.materialId, item.warehouseId)
        const s = r.data || {}
        item.onHandQty = Number(s.totalQty ?? 0)
        item.availableQty = Number(s.availableQty ?? 0)
      } catch {}
    }))
  }
  processItems.value = itemsWithWh
  processVisible.value = true
}

async function onProcessItemWhChange(row) {
  row.locationId = null
  row.locationOptions = []
  if (row.warehouseId) {
    try { const r = await getWarehouseLocationList(row.warehouseId); row.locationOptions = r.data || [] } catch {}
    // 切换仓库时同步刷新该仓库下的可用库存(原显示是跨仓库合计, 与单仓出库校验不一致)
    if (row.materialId) {
      try {
        const stockRes = await getStockQty(row.materialId, row.warehouseId)
        const s = stockRes.data || {}
        row.onHandQty = Number(s.totalQty ?? 0)
        row.availableQty = Number(s.availableQty ?? 0)
      } catch {}
    }
  }
}

// 全量处理（单条）：自动将所有物料设置为剩余数量后打开处理弹窗
async function openFullProcess(row) {
  await openProcess(row)
  processItems.value.forEach(item => {
    item.processQty = Math.max(0, (item.quantity || 0) - (item.processedQty || 0))
  })
}

async function handleProcessSubmit() {
  const items = processItems.value
    .filter(i => i.processQty > 0)
    .map(i => ({ itemId: i.id, processQty: i.processQty, secondaryProcessQty: i.secondaryProcessQty, warehouseId: i.warehouseId, locationId: i.locationId }))
  if (!items.length) { ElMessage.warning('请至少填写一项处理数量'); return }
  const noWh = items.find(i => !i.warehouseId)
  if (noWh) {
    const item = processItems.value.find(p => p.id === noWh.itemId)
    ElMessage.warning(`物料[${item?.materialName || ''}]未选择仓库，请为每个物料指定仓库`)
    return
  }
  submitLoading.value = true
  try {
    await processInvTask(processTaskId.value, { items, remark: processRemark.value, historyNo: processHistoryNo.value || undefined })
    ElMessage.success('处理成功')
    processVisible.value = false
    loadData()
  } finally { submitLoading.value = false }
}

// ==================== 退回 ====================
const returnVisible = ref(false)
const returnLoading = ref(false)
const returnTaskId = ref(null)
const returnReason = ref('')

function openReturn(row) {
  returnTaskId.value = row.id
  returnReason.value = ''
  returnVisible.value = true
}

async function handleReturnSubmit() {
  if (!returnReason.value.trim()) { ElMessage.warning('请填写退回原因'); return }
  returnLoading.value = true
  try {
    await returnInvTask(returnTaskId.value, { returnReason: returnReason.value })
    ElMessage.success('退回成功')
    returnVisible.value = false
    loadData()
  } finally { returnLoading.value = false }
}

// ==================== 关联单据 ====================
const relatedOrderVisible = ref(false)
const relatedOrderNo = ref('')
const relatedInvType = ref('')

const sourceDetailVisible = ref(false)
const sourceDetailData = ref(null)
const sourceDetailLoading = ref(false)
const sourceDetailType = ref('')

async function openRelatedOrder(row) {
  const sourceType = row.sourceType || ''
  const sourceId = row.sourceId

  if (sourceType === 'purchase_receipt' && sourceId) {
    sourceDetailType.value = 'purchase_receipt'
    sourceDetailVisible.value = true
    sourceDetailLoading.value = true
    try {
      const res = await getReceiptDetail(sourceId)
      sourceDetailData.value = res.data
    } catch {} finally { sourceDetailLoading.value = false }
    return
  }
  if (sourceType === 'outsource_receipt' && sourceId) {
    sourceDetailType.value = 'outsource_receipt'
    sourceDetailVisible.value = true
    sourceDetailLoading.value = true
    try {
      const res = await getOutsourceReceiptDetail(sourceId)
      sourceDetailData.value = res.data
    } catch {} finally { sourceDetailLoading.value = false }
    return
  }
  if (sourceType === 'qc_task' && sourceId) {
    sourceDetailType.value = 'qc_task'
    sourceDetailVisible.value = true
    sourceDetailLoading.value = true
    try {
      const res = await getQcTaskDetail(sourceId)
      sourceDetailData.value = res.data
    } catch {} finally { sourceDetailLoading.value = false }
    return
  }
  // 其他类型走原来的弹窗
  relatedOrderNo.value = row.relatedOrderNo || ''
  relatedInvType.value = row.invType || ''
  relatedOrderVisible.value = true
}

// ==================== 初始化 ====================
async function loadOptions() {
  try { const res = await getWarehouseSelect(); warehouseOptions.value = res.data || [] } catch {}
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
