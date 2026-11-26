<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="物料编码">
          <el-input v-model="queryParams.materialNo" placeholder="物料编码" clearable style="width:150px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="物料名称">
          <el-input v-model="queryParams.materialName" placeholder="物料名称" clearable style="width:150px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="规格型号">
          <el-input v-model="queryParams.spec" placeholder="规格型号" clearable style="width:150px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="出入库类型">
          <el-select v-model="queryParams.invType" placeholder="全部" clearable style="width:140px">
            <el-option v-for="t in invTypeOptions" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width:110px">
            <el-option label="正常" value="normal" />
            <el-option label="已撤销" value="revoked" />
          </el-select>
        </el-form-item>
        <el-form-item label="历史单号">
          <el-input v-model="queryParams.historyNo" placeholder="历史单号" clearable style="width:160px" />
        </el-form-item>
        <el-form-item label="任务单号">
          <el-input v-model="queryParams.taskNo" placeholder="任务单号" clearable style="width:160px" />
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
          <el-button plain @click="handleExport">导出</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据表格（物料角度，每行 = 一条物料明细） -->
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
            <template v-else-if="col.key === 'customerName'">{{ row.customerName || '-' }}</template>
            <template v-else-if="col.key === 'relatedOrderNo'">
              <el-button v-if="row.relatedOrderNo" link type="primary" style="padding:0"
                @click="openRelatedOrder(row)">{{ row.relatedOrderNo }}</el-button>
              <span v-else style="color:#c0c4cc">-</span>
            </template>
            <template v-else-if="col.key === 'status'">
              <el-tag :type="row.status === 'normal' ? 'success' : 'info'" size="small">
                {{ row.status === 'normal' ? '正常' : '已撤销' }}
              </el-tag>
            </template>
            <template v-else-if="col.key === 'createTime'">{{ $fmtDate(row.createTime) }}</template>
            <template v-else-if="col.key === 'spec'">{{ $fmtSpec(row.spec) }}</template>
            <template v-else-if="col.key === 'unitPrice'">
              <span v-if="row.unitPrice != null">¥{{ Number(row.unitPrice).toFixed(2) }}</span>
              <span v-else style="color:#c0c4cc">-</span>
            </template>
            <template v-else-if="col.key === 'totalPrice'">
              <span v-if="row.totalPrice != null" style="font-weight:600; color:#303133">¥{{ Number(row.totalPrice).toFixed(2) }}</span>
              <span v-else style="color:#c0c4cc">-</span>
            </template>
            <template v-else>{{ row[col.key] || '-' }}</template>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看详情</el-button>
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
    <ColumnConfigDialog ref="colConfigDialogRef" :all-columns="allColumns" :default-columns="HISTORY_DEFAULT_COLUMNS" @save="saveConfig" />

    <!-- 关联单据弹窗 -->
    <PurchaseOrderDetailDialog v-model:visible="relatedOrderVisible" :relatedOrderNo="relatedOrderNo" :invType="relatedInvType" />

    <!-- 详情弹窗（展示整条历史单 + 所有物料明细） -->
    <el-dialog v-model="detailVisible" title="出入库历史详情" width="900px" destroy-on-close>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="历史单号">{{ detail.historyNo }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ invTypeLabel(detail.invType) }}</el-descriptions-item>
        <el-descriptions-item label="任务单号">{{ detail.taskNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="关联单据号">{{ detail.relatedOrderNo || '-' }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.invType === 'return_in'" label="退货客户">{{ detail.customerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="批次号">{{ detail.batchNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="仓库">{{ detail.warehouseName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="库位">{{ detail.locationName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="detail.status === 'normal' ? 'success' : 'info'" size="small">
            {{ detail.status === 'normal' ? '正常' : '已撤销' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="申请人">{{ detail.applicantName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ detail.operatorName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="操作时间">{{ $fmtDate(detail.createTime) }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.remark" label="备注" :span="2">{{ detail.remark }}</el-descriptions-item>
      </el-descriptions>
      <div style="margin-top:16px; font-weight:600; margin-bottom:8px">物料明细</div>
      <el-table :data="detail.items" border size="small">
        <el-table-column prop="materialNo" label="物料编号" width="120" />
        <el-table-column prop="materialName" label="物料名称" min-width="120" show-overflow-tooltip />
        <el-table-column label="规格" width="100" show-overflow-tooltip :formatter="(row) => formatSpec(row.spec)" />
        <el-table-column prop="unit" label="单位" width="55" align="center" />
        <el-table-column prop="quantity" label="本次数量" width="90" align="right" />
        <el-table-column prop="unitPrice" label="单价" width="85" align="right" />
        <el-table-column prop="totalPrice" label="总价" width="95" align="right" />
        <el-table-column prop="warehouseName" label="仓库" width="100" show-overflow-tooltip>
          <template #default="{ row }"><span>{{ row.warehouseName || '-' }}</span></template>
        </el-table-column>
        <el-table-column prop="locationName" label="库位" width="90" show-overflow-tooltip>
          <template #default="{ row }"><span>{{ row.locationName || '-' }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'revoked' ? 'info' : 'success'" size="small">
              {{ row.status === 'revoked' ? '已撤销' : '正常' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70" align="center">
          <template #default="{ row }">
            <el-button
              v-if="row.status !== 'revoked' && !isStocktakingOrTransfer(detail.invType)"
              v-permission="'inventory:history:revoke'"
              link type="danger" size="small"
              @click="handleRevokeItemInDetail(row)"
            >撤销</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer><el-button @click="detailVisible = false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Setting } from '@element-plus/icons-vue'
import { getInvHistoryPage, getInvHistoryDetail, revokeInvHistoryItem, exportInvHistory } from '@/api/inventory'
import { formatSpec } from '@/utils/format'
import PurchaseOrderDetailDialog from '@/components/PurchaseOrderDetailDialog.vue'
import { useColumnConfig } from '@/composables/useColumnConfig'
import ColumnConfigDialog from '@/components/ColumnConfigDialog.vue'


const route = useRoute()

// ==================== 列配置 ====================
const HISTORY_DEFAULT_COLUMNS = [
  { key: 'materialNo', label: '物料编码', width: 115 },
  { key: 'materialName', label: '物料名称', minWidth: 120 },
  { key: 'spec', label: '规格', width: 90 },
  { key: 'unit', label: '单位', width: 55, align: 'center' },
  { key: 'quantity', label: '数量', width: 80, align: 'right' },
  { key: 'unitPrice', label: '单价', width: 85, align: 'right' },
  { key: 'totalPrice', label: '金额', width: 95, align: 'right' },
  { key: 'secondaryUnit', label: '辅助单位', width: 70, align: 'center' },
  { key: 'secondaryQty', label: '辅助数量', width: 95, align: 'right' },
  { key: 'invType', label: '类型', width: 110, align: 'center' },
  { key: 'customerName', label: '退货客户', width: 120 },
  { key: 'historyNo', label: '历史单号', width: 165 },
  { key: 'relatedOrderNo', label: '关联单据号', minWidth: 175 },
  { key: 'batchNo', label: '批次号', width: 130 },
  { key: 'warehouseName', label: '仓库', width: 110 },
  { key: 'locationName', label: '库位', width: 100 },
  { key: 'status', label: '状态', width: 85, align: 'center' },
  { key: 'applicantName', label: '申请人', width: 100 },
  { key: 'operatorName', label: '操作人', width: 100 },
  { key: 'createTime', label: '操作时间', minWidth: 165 },
]
const colConfigDialogRef = ref(null)
const { visibleColumns, allColumns, loadColumnConfig, saveConfig } = useColumnConfig('inventory_history', HISTORY_DEFAULT_COLUMNS)

// ==================== 字典 ====================
const invTypeOptions = [
  { label: '采购入库', value: 'purchase_in' },
  { label: '生产入库', value: 'production_in' },
  { label: '半成品入库', value: 'semi_in' },
  { label: '外协收货', value: 'outsource_in' },
  { label: '外协来料', value: 'outsource_material_in' },
  { label: '退货入库', value: 'return_in' },
  { label: '其他入库', value: 'other_in' },
  { label: '生产领料', value: 'production_out' },
  { label: '销售发货', value: 'sales_out' },
  { label: '外协发料', value: 'outsource_out' },
  { label: '报废出库', value: 'scrap_out' },
  { label: '其他出库', value: 'other_out' },
  { label: '调拨出库', value: 'transfer_out' },
  { label: '调拨入库', value: 'transfer_in' },
  { label: '盘点入库', value: 'stocktaking_in' },
  { label: '盘点出库', value: 'stocktaking_out' },
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
const NO_REVOKE_TYPES = new Set(['transfer_in', 'transfer_out', 'stocktaking_in', 'stocktaking_out'])
function isStocktakingOrTransfer(invType) {
  return NO_REVOKE_TYPES.has(invType)
}

// ==================== 查询 ====================
const queryParams = reactive({
  historyNo: '', taskNo: '', relatedOrderNo: '', invType: '', status: '',
  materialNo: '', materialName: '', spec: '', dateStart: '', dateEnd: '', pageNum: 1, pageSize: 20
})
const dateRange = ref([])
const loading = ref(false)
const tableData = ref([])
const total = ref(0)

async function loadData() {
  loading.value = true
  try {
    const params = { ...queryParams, dateStart: dateRange.value?.[0] || '', dateEnd: dateRange.value?.[1] || '' }
    const res = await getInvHistoryPage(params)
    tableData.value = res.data?.records || res.data?.list || []
    total.value = res.data?.total || 0
  } finally { loading.value = false }
}
function handleSearch() { queryParams.pageNum = 1; loadData() }

async function handleExport() {
  try {
    const params = { ...queryParams, dateStart: dateRange.value?.[0] || '', dateEnd: dateRange.value?.[1] || '' }
    const res = await exportInvHistory(params)
    const blob = new Blob([res], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `出入库历史_${new Date().toISOString().slice(0, 10)}.xlsx`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch { ElMessage.error('导出失败') }
}

function handleReset() {
  Object.assign(queryParams, { historyNo: '', taskNo: '', relatedOrderNo: '', invType: '', status: '', materialNo: '', materialName: '', spec: '', pageNum: 1 })
  dateRange.value = []
  loadData()
}

// ==================== 详情（以 historyId 加载整条历史单） ====================
const detailVisible = ref(false)
const detail = ref({})

async function openDetail(row) {
  // row.historyId 是该物料行所属的历史单 id
  const res = await getInvHistoryDetail(row.historyId)
  detail.value = res.data || {}
  detailVisible.value = true
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

// ==================== 撤销（以物料行为维度撤销） ====================
async function handleRevokeItem(row) {
  await ElMessageBox.confirm(
    `撤销后将回滚【${row.materialName || row.materialNo}】的库存变更并恢复任务已处理数量。确定撤销此物料行？`,
    '撤销确认',
    { type: 'warning' }
  )
  try {
    await revokeInvHistoryItem(row.id)
    ElMessage.success('撤销成功')
    loadData()
  } catch {}
}

async function handleRevokeItemInDetail(itemRow) {
  await ElMessageBox.confirm(
    `撤销后将回滚【${itemRow.materialName || itemRow.materialNo}】的库存变更并恢复任务已处理数量。确定撤销此物料行？`,
    '撤销确认',
    { type: 'warning' }
  )
  try {
    await revokeInvHistoryItem(itemRow.id)
    ElMessage.success('撤销成功')
    // 刷新详情和列表
    const res = await getInvHistoryDetail(detail.value.id)
    detail.value = res.data || {}
    loadData()
  } catch {}
}

// ==================== 初始化 ====================
onMounted(() => {
  // 从库存台账跳转时，自动填入物料编码过滤
  if (route.query.materialNo) {
    queryParams.materialNo = route.query.materialNo
  }
  loadData()
  loadColumnConfig()
})
</script>

<style scoped lang="scss">
.page-container { padding: 16px; }
.search-card { margin-bottom: 16px; }
.table-card {
  .pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; }
}
</style>
