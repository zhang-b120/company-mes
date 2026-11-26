<template>
  <div class="page-container">
    <!-- 顶部统计 -->
    <el-row :gutter="12" class="stat-row">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value" style="color: #409eff">{{ statsData.totalSku ?? '-' }}</div>
          <div class="stat-label">物料种类总数</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value" style="color: #67c23a">{{ statsData.totalValue != null ? (statsData.totalValue > 0 ? `¥${(statsData.totalValue/10000).toFixed(1)}万` : '¥0') : '暂无定价数据' }}</div>
          <div class="stat-label">总库存金额</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card stat-card--clickable"
          :class="{ 'stat-card--active': queryParams.stockStatus === 'alert' }"
          @click="filterByAlert">
          <div class="stat-value" style="color: #f56c6c">{{ statsData.alertCount ?? '-' }}</div>
          <div class="stat-label">
            预警物料
            <el-tag v-if="queryParams.stockStatus === 'alert'" size="small" type="danger" style="margin-left:4px;vertical-align:middle">已筛选</el-tag>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value" style="color: #e6a23c">{{ statsData.lockedCount ?? '-' }}</div>
          <div class="stat-label">锁定物料种类</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 主体：左侧仓库面板 + 右侧内容 -->
    <div class="main-layout">
      <!-- 左侧仓库面板 -->
      <div class="warehouse-panel">
        <div class="panel-header">仓库</div>
        <div
          class="panel-all-item"
          :class="{ active: selectedWarehouseIds.length === 0 }"
          @click="clearWarehouseFilter"
        >全部</div>
        <el-checkbox-group v-model="selectedWarehouseIds" @change="handleWarehouseChange" class="warehouse-checkbox-group">
          <div v-for="wh in warehouseList" :key="wh.id" class="warehouse-checkbox-item">
            <el-checkbox :value="wh.id">{{ wh.name }}</el-checkbox>
          </div>
        </el-checkbox-group>
        <el-button
          v-if="selectedWarehouseIds.length > 0"
          link type="primary"
          class="clear-filter-btn"
          @click="clearWarehouseFilter"
        >清除筛选</el-button>
      </div>

      <!-- 右侧内容 -->
      <div class="content-panel">
        <!-- 分类Tab + 搜索 -->
        <el-card shadow="never" class="search-card">
          <el-tabs v-model="queryParams.category" @tab-change="handleSearch">
            <el-tab-pane label="全部" name="" />
            <el-tab-pane label="原材料" name="raw_material" />
            <el-tab-pane label="半成品" name="semi_finished" />
            <el-tab-pane label="成品" name="finished" />
          </el-tabs>
          <el-form :model="queryParams" inline>
            <el-form-item label="物料编号">
              <el-input v-model="queryParams.materialNo" placeholder="物料编号" clearable style="width: 150px" @keyup.enter="handleSearch" />
            </el-form-item>
            <el-form-item label="名称">
              <el-input v-model="queryParams.materialName" placeholder="物料名称" clearable style="width: 150px" @keyup.enter="handleSearch" />
            </el-form-item>
            <el-form-item label="规格">
              <el-input v-model="queryParams.spec" placeholder="规格型号" clearable style="width: 150px" @keyup.enter="handleSearch" />
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="queryParams.stockStatus" placeholder="全部" clearable style="width: 120px"
                @change="handleSearch">
                <el-option label="全部预警" value="alert" />
                <el-option label="正常" value="normal" />
                <el-option label="缺货" value="shortage" />
                <el-option label="超量" value="excess" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleSearch">搜索</el-button>
              <el-button @click="handleReset">重置</el-button>
              <el-button type="success" @click="handleExport">导出</el-button>
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
                <template v-if="col.key === 'materialNo'">
                  <el-button link type="primary" style="padding:0" @click="goToHistory(row)">{{ row.materialNo }}</el-button>
                </template>
                <template v-else-if="col.key === 'category'">
                  <el-tag :type="catTagType[row.category]" size="small">{{ catMap[row.category] || row.category || '-' }}</el-tag>
                </template>
                <template v-else-if="col.key === 'totalQty'">
                  <span style="font-weight: 500">{{ row.totalQty ?? 0 }}</span>
                </template>
                <template v-else-if="col.key === 'lockedQty'">
                  <span :style="{ color: row.lockedQty > 0 ? '#e6a23c' : '' }">{{ row.lockedQty ?? 0 }}</span>
                </template>
                <template v-else-if="col.key === 'availableQty'">
                  <span :style="{ color: isShortage(row) ? '#f56c6c' : isExcess(row) ? '#e6a23c' : '#67c23a', fontWeight: '600' }">{{ row.availableQty ?? 0 }}</span>
                </template>
                <template v-else-if="col.key === 'minStock'">
                  <span :style="isShortage(row) ? 'color:#f56c6c;font-weight:600' : ''">{{ row.minStock != null ? row.minStock : '-' }}</span>
                </template>
                <template v-else-if="col.key === 'maxStock'">
                  <span :style="isExcess(row) ? 'color:#e6a23c;font-weight:600' : ''">{{ row.maxStock != null ? row.maxStock : '-' }}</span>
                </template>
                <template v-else-if="col.key === 'stockStatus'">
                  <el-tag v-if="isShortage(row)" type="danger" size="small">缺货</el-tag>
                  <el-tag v-else-if="isExcess(row)" type="warning" size="small">超量</el-tag>
                  <el-tag v-else type="success" size="small">正常</el-tag>
                </template>
                <template v-else-if="col.key === 'spec'">{{ $fmtSpec(row.spec) }}</template>
                <template v-else>{{ row[col.key] || '-' }}</template>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="viewDetail(row)">明细</el-button>
                <el-button link type="warning" size="small" @click="viewLockDetail(row)">锁定</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrapper">
            <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
              :page-sizes="[10, 20, 50, 100]" :total="total" background layout="total, sizes, prev, pager, next, jumper"
              @size-change="loadData" @current-change="loadData" />
          </div>
        </el-card>
      </div>
    </div>

    <!-- 库存流水明细弹窗 -->
    <el-dialog v-model="detailVisible" :title="`${detailMaterial} - 库存流水`" width="800px" destroy-on-close>
      <el-table :data="detailList" v-loading="detailLoading" border size="small" max-height="400">
        <el-table-column prop="transactionNo" label="流水号" width="150" />
        <el-table-column prop="transactionType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.transactionType === 'IN' || row.transactionType?.includes('in') ? 'success' : 'danger'" size="small">
              {{ txTypeMap[row.transactionType] || row.transactionType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="quantity" label="数量" width="80" align="right" />
        <el-table-column prop="costPrice" label="参考价格" width="90" align="right">
          <template #default="{ row }">{{ row.costPrice ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="batchNo" label="批次" width="120" />
        <el-table-column prop="relatedOrderNo" label="关联单据" min-width="140">
          <template #default="{ row }">
            <el-button v-if="row.relatedOrderNo" link type="primary" style="padding:0"
              @click="openRelatedOrder(row)">{{ row.relatedOrderNo }}</el-button>
            <span v-else style="color:#c0c4cc">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="operatorName" label="操作人" width="80" />
        <el-table-column prop="createTime" label="时间" width="160" />
        <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.remark">{{ row.remark }}</span>
            <span v-else style="color:#c0c4cc">-</span>
          </template>
        </el-table-column>
      </el-table>
      <template #footer><el-button @click="detailVisible = false">关闭</el-button></template>
    </el-dialog>

    <!-- 关联单据弹窗 -->
    <PurchaseOrderDetailDialog v-model:visible="relatedOrderVisible" :relatedOrderNo="relatedOrderNo" :invType="relatedInvType" />

    <!-- 锁定明细弹窗 -->
    <el-dialog v-model="lockVisible" :title="`${lockMaterial} - 锁定明细`" width="700px" destroy-on-close>
      <el-table :data="lockList" v-loading="lockLoading" border size="small">
        <el-table-column prop="lockType" label="锁定类型" width="100">
          <template #default="{ row }">{{ lockTypeMap[row.lockType] || row.lockType || '工单锁料' }}</template>
        </el-table-column>
        <el-table-column prop="relatedOrderNo" label="关联单据" min-width="140" />
        <el-table-column prop="lockedQty" label="锁定数量" width="90" align="right" />
        <el-table-column prop="remainingQty" label="剩余锁定" width="90" align="right" />
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'warning' : 'info'" size="small">{{ row.status === 'active' ? '锁定中' : '已释放' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="锁定时间" width="160" />
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ row }">
            <el-button v-if="row.status === 'active'" link type="danger" size="small" @click="handleUnlock(row)">释放</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer><el-button @click="lockVisible = false">关闭</el-button></template>
    </el-dialog>

    <!-- 列配置 -->
    <ColumnConfigDialog ref="colConfigDialogRef" :all-columns="allColumns" :default-columns="STOCK_DEFAULT_COLUMNS" @save="saveConfig" />

    <!-- 库存调整弹窗 -->
    <el-dialog v-model="adjustVisible" title="库存调整" width="460px" destroy-on-close>
      <el-form :model="adjustForm" :rules="adjustRules" ref="adjustRef" label-width="100px">
        <el-form-item label="物料">
          <el-input :model-value="adjustForm.materialName" disabled />
        </el-form-item>
        <el-form-item label="当前数量">
          <el-input :model-value="adjustForm.currentQty" disabled />
        </el-form-item>
        <el-form-item label="调整后数量" prop="newQty">
          <el-input-number v-model="adjustForm.newQty" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="调整原因" prop="reason">
          <el-input v-model="adjustForm.reason" type="textarea" :rows="3" placeholder="请输入调整原因（必填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustVisible = false">取消</el-button>
        <el-button type="primary" :loading="adjustLoading" @click="submitAdjust">提交调整</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Setting } from '@element-plus/icons-vue'

import { getInventoryPage, getInventoryTransactions, getReservationPage, unlockStock, stockIn, exportInventory } from '@/api/inventory'
import { getWarehouseSelect } from '@/api/system'
import PurchaseOrderDetailDialog from '@/components/PurchaseOrderDetailDialog.vue'
import { useColumnConfig } from '@/composables/useColumnConfig'
import ColumnConfigDialog from '@/components/ColumnConfigDialog.vue'

const router = useRouter()

const STOCK_DEFAULT_COLUMNS = [
  { key: 'materialNo', label: '物料编号', width: 120 },
  { key: 'materialName', label: '名称', minWidth: 140 },
  { key: 'spec', label: '规格', minWidth: 110 },
  { key: 'category', label: '类别', width: 90, align: 'center' },
  { key: 'unit', label: '单位', width: 60, align: 'center' },
  { key: 'totalQty', label: '在库数量', width: 95, align: 'right' },
  { key: 'lockedQty', label: '锁定数量', width: 95, align: 'right' },
  { key: 'availableQty', label: '可用数量', width: 95, align: 'right' },
  { key: 'costPrice', label: '参考价格', width: 95, align: 'right' },
  { key: 'secondaryUnit', label: '辅助单位', width: 70, align: 'center' },
  { key: 'secondaryTotalQty', label: '辅助在库', width: 95, align: 'right' },
  { key: 'secondaryAvailableQty', label: '辅助可用', width: 95, align: 'right' },
  { key: 'minStock', label: '最小库存', width: 90, align: 'right' },
  { key: 'maxStock', label: '最大库存', width: 90, align: 'right' },
  { key: 'stockStatus', label: '状态', width: 80, align: 'center' },
  { key: 'warehouseName', label: '仓库', width: 100 },
  { key: 'locationName', label: '库位', width: 90 },
]
const colConfigDialogRef = ref(null)
const { visibleColumns, allColumns, loadColumnConfig, saveConfig } = useColumnConfig('inventory_stock', STOCK_DEFAULT_COLUMNS)

function goToHistory(row) {
  router.push({ path: '/inventory/history', query: { materialNo: row.materialNo } })
}

const catMap = { raw_material: '原材料', semi_finished: '半成品', finished: '成品', auxiliary: '辅料', packaging: '包材', purchased: '外购件' }
const catTagType = { raw_material: '', semi_finished: 'warning', finished: 'success', auxiliary: 'info', packaging: 'info', purchased: '' }
// ==================== 关联单据弹窗 ====================
const relatedOrderVisible = ref(false)
const relatedOrderNo = ref('')
const relatedInvType = ref('')

function openRelatedOrder(row) {
  relatedOrderNo.value = row.relatedOrderNo || ''
  relatedInvType.value = row.transactionType || ''
  relatedOrderVisible.value = true
}

const txTypeMap = { IN: '入库', OUT: '出库', stock_in: '入库', stock_out: '出库',
  purchase_in: '采购入库', production_in: '成品入库', semi_in: '半成品入库',
  outsource_in: '外协收货', outsource_material_in: '外协来料', return_in: '退货入库',
  production_out: '生产领料', sales_out: '销售出库', outsource_out: '外协发料',
  production_return: '生产退料', purchase_return: '采购退货', scrap_out: '报废出库',
  other_in: '其他入库', other_out: '其他出库',
  transfer_out: '调拨出库', transfer_in: '调拨入库',
  PRODUCTION_OUT: '生产领料', SALES_OUT: '销售出库', OUTSOURCE_OUT: '外协发料', OTHER_OUT: '其他出库' }
const lockTypeMap = { wo_lock: '工单锁料', order_lock: '订单锁库', manual: '手动锁定' }

function isShortage(row) {
  return row.minStock != null && Number(row.minStock) > 0 && Number(row.availableQty ?? 0) < Number(row.minStock)
}
function isExcess(row) {
  return row.maxStock != null && Number(row.maxStock) > 0 && Number(row.availableQty ?? 0) > Number(row.maxStock)
}

// ==================== 统计 ====================
const statsData = ref({})
function computeStats(records, totalCount) {
  let totalValue = 0
  let alertCount = 0
  let lockedCount = 0
  let hasPriceData = false
  if (records && records.length) {
    records.forEach(row => {
      if (row.costPrice != null && row.totalQty != null) {
        totalValue += (row.totalQty || 0) * (row.costPrice || 0)
        hasPriceData = true
      }
      if (isShortage(row) || isExcess(row)) {
        alertCount++
      }
      if ((row.lockedQty || 0) > 0) {
        lockedCount++
      }
    })
  }
  statsData.value = {
    totalSku: totalCount || 0,
    totalValue: hasPriceData ? totalValue : null,
    alertCount,
    lockedCount
  }
}
async function loadStats() {
  try {
    // 卡片统计随仓库筛选切换重算: 没选仓库 → 全仓汇总; 选了某些仓库 → 只算这些
    const params = { pageNum: 1, pageSize: 9999 }
    if (selectedWarehouseIds.value.length > 0) {
      params.warehouseIds = selectedWarehouseIds.value.join(',')
    }
    const res = await getInventoryPage(params)
    const d = res.data || {}
    const records = d.records || d.list || []
    if (d.totalValue != null || d.alertCount != null || d.lockedCount != null) {
      statsData.value = { totalSku: d.total || 0, totalValue: d.totalValue || 0, alertCount: d.alertCount || 0, lockedCount: d.lockedCount || 0 }
    } else {
      computeStats(records, d.total || records.length)
    }
  } catch {}
}

// ==================== 左侧仓库面板 ====================
const warehouseList = ref([])
const selectedWarehouseIds = ref([])

function handleWarehouseChange() {
  queryParams.pageNum = 1
  loadData()
  loadStats()
}

function clearWarehouseFilter() {
  selectedWarehouseIds.value = []
  queryParams.pageNum = 1
  loadData()
  loadStats()
}

// ==================== 查询 ====================
const queryParams = reactive({ materialNo: '', materialName: '', spec: '', category: '', stockStatus: '', pageNum: 1, pageSize: 20 })
const loading = ref(false)
const tableData = ref([])
const total = ref(0)

async function loadData() {
  loading.value = true
  try {
    const params = {
      ...queryParams,
      warehouseIds: selectedWarehouseIds.value.length > 0 ? selectedWarehouseIds.value.join(',') : undefined
    }
    const res = await getInventoryPage(params)
    tableData.value = res.data?.records || res.data?.list || []
    total.value = res.data?.total || 0
  } catch {} finally { loading.value = false }
}

function filterByAlert() {
  queryParams.stockStatus = queryParams.stockStatus === 'alert' ? '' : 'alert'
  queryParams.pageNum = 1
  loadData()
}

function handleSearch() { queryParams.pageNum = 1; loadData() }
function handleReset() {
  queryParams.materialNo = ''; queryParams.materialName = ''; queryParams.spec = ''
  queryParams.category = ''; queryParams.stockStatus = ''
  queryParams.pageNum = 1
  selectedWarehouseIds.value = []
  loadData()
}

async function handleExport() {
  try {
    const params = {
      ...queryParams,
      warehouseIds: selectedWarehouseIds.value.length > 0 ? selectedWarehouseIds.value.join(',') : undefined
    }
    delete params.pageNum
    delete params.pageSize
    const res = await exportInventory(params)
    const blob = res instanceof Blob ? res : new Blob([res])
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url; a.download = '库存台账.xlsx'; a.click()
    URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('导出失败')
  }
}

// ==================== 流水明细 ====================
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailMaterial = ref('')
const detailList = ref([])

async function viewDetail(row) {
  detailMaterial.value = row.materialName + (row.warehouseName ? ` (${row.warehouseName})` : '')
  detailVisible.value = true; detailLoading.value = true
  try {
    const res = await getInventoryTransactions({ materialId: row.materialId || row.id, warehouseId: row.warehouseId, pageNum: 1, pageSize: 50 })
    detailList.value = res.data?.records || res.data?.list || []
  } catch {} finally { detailLoading.value = false }
}

// ==================== 锁定明细 ====================
const lockVisible = ref(false)
const lockLoading = ref(false)
const lockMaterial = ref('')
const lockList = ref([])

async function viewLockDetail(row) {
  lockMaterial.value = row.materialName
  lockVisible.value = true; lockLoading.value = true
  try {
    const res = await getReservationPage({ materialId: row.materialId || row.id, pageNum: 1, pageSize: 50 })
    lockList.value = res.data?.records || res.data?.list || []
  } catch {} finally { lockLoading.value = false }
}

async function handleUnlock(row) {
  await ElMessageBox.confirm(`确定释放 ${lockMaterial.value} 的锁定 ${row.lockedQty} 件？`, '释放确认', { type: 'warning' })
  try { await unlockStock(row.id); ElMessage.success('已释放'); viewLockDetail({ materialName: lockMaterial.value, materialId: row.materialId }); loadData() } catch {}
}

// ==================== 库存调整 ====================
const adjustVisible = ref(false)
const adjustLoading = ref(false)
const adjustRef = ref(null)
const adjustForm = reactive({ materialId: null, materialName: '', currentQty: 0, newQty: 0, reason: '' })
const adjustRules = {
  newQty: [{ required: true, message: '请输入调整后数量', trigger: 'blur' }],
  reason: [{ required: true, message: '请输入调整原因', trigger: 'blur' }]
}

function openAdjust(row) {
  adjustForm.materialId = row.materialId || row.id; adjustForm.materialName = row.materialName
  adjustForm.currentQty = row.totalQty || 0; adjustForm.newQty = row.totalQty || 0; adjustForm.reason = ''
  adjustVisible.value = true
}

async function submitAdjust() {
  await adjustRef.value.validate()
  const diff = adjustForm.newQty - adjustForm.currentQty
  if (diff === 0) { ElMessage.warning('调整后数量与当前相同'); return }
  adjustLoading.value = true
  try {
    await stockIn({ materialId: adjustForm.materialId, quantity: Math.abs(diff), batchNo: 'ADJ-' + Date.now(),
      remark: `库存调整: ${adjustForm.currentQty} → ${adjustForm.newQty}, 原因: ${adjustForm.reason}`,
      transactionType: diff > 0 ? 'adjust_in' : 'adjust_out' })
    ElMessage.success('库存调整成功'); adjustVisible.value = false; loadData()
  } finally { adjustLoading.value = false }
}

// ==================== 初始化 ====================
async function loadOptions() {
  try {
    const res = await getWarehouseSelect()
    warehouseList.value = res.data || []
  } catch {}
}
onMounted(() => { loadOptions(); loadData(); loadStats(); loadColumnConfig() })
</script>

<style scoped lang="scss">
.page-container { padding: 16px; }
.stat-row { margin-bottom: 12px; }
.stat-card {
  text-align: center;
  .stat-value { font-size: 24px; font-weight: 700; line-height: 1.4; }
  .stat-label { font-size: 13px; color: #909399; margin-top: 4px; }
  &--clickable {
    cursor: pointer;
    transition: box-shadow 0.2s, border-color 0.2s;
    &:hover { box-shadow: 0 4px 16px rgba(245, 108, 108, 0.25) !important; }
  }
  &--active {
    border-color: #f56c6c !important;
    box-shadow: 0 0 0 2px rgba(245, 108, 108, 0.2) !important;
  }
}

.main-layout {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.warehouse-panel {
  width: 150px;
  min-width: 150px;
  background: #fff;
  border-radius: 8px;
  padding: 14px 12px;
  box-shadow: 0 0 12px rgba(0, 0, 0, 0.06);

  .panel-header {
    font-size: 14px;
    font-weight: 600;
    color: #303133;
    padding-bottom: 10px;
    border-bottom: 1px solid #f0f0f0;
    margin-bottom: 8px;
  }

  .panel-all-item {
    padding: 6px 6px;
    font-size: 13px;
    color: #606266;
    border-radius: 4px;
    cursor: pointer;
    margin-bottom: 2px;
    &:hover { background: #f5f7fa; }
    &.active { color: #409eff; font-weight: 500; }
  }

  .warehouse-checkbox-group {
    display: flex;
    flex-direction: column;
  }

  .warehouse-checkbox-item {
    padding: 5px 0;
    font-size: 13px;
    :deep(.el-checkbox__label) {
      font-size: 13px;
      color: #606266;
      white-space: normal;
      line-height: 1.4;
    }
  }

  .clear-filter-btn {
    margin-top: 10px;
    font-size: 12px;
    padding: 0;
  }
}

.content-panel {
  flex: 1;
  min-width: 0;
}

.search-card { margin-bottom: 12px; }
.table-card { .pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; } }
</style>
