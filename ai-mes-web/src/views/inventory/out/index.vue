<template>
  <div class="page-container">
    <!-- 出库表单 -->
    <el-card shadow="never">
      <template #header><span>出库操作</span></template>

      <!-- 出库类型Tab -->
      <el-tabs v-model="form.stockOutType" @tab-change="onTypeChange">
        <el-tab-pane label="生产领料" name="production_out" />
        <el-tab-pane label="销售发货" name="sales_out" />
        <el-tab-pane label="外协发料" name="outsource_out" />
        <el-tab-pane label="报废出库" name="scrap_out" />
        <el-tab-pane label="其他出库" name="other_out" />
      </el-tabs>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" style="max-width: 640px">
        <!-- 关联单据（根据类型动态显示） -->
        <el-form-item v-if="form.stockOutType === 'production_out'" label="生产工单" prop="relatedOrderId">
          <el-select v-model="form.relatedOrderId" placeholder="请选择工单" filterable style="width: 100%"
            @change="onWorkOrderChange">
            <el-option v-for="item in workOrders" :key="item.id"
              :label="`${item.orderNo || item.workOrderNo || ''} - ${item.productName || item.name || ''}`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.stockOutType === 'sales_out'" label="销售订单" prop="relatedOrderId">
          <el-select v-model="form.relatedOrderId" placeholder="请选择销售订单" filterable style="width: 100%">
            <el-option v-for="item in salesOrders" :key="item.id"
              :label="`${item.orderNo || ''} - ${item.customerName || item.name || ''}`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.stockOutType === 'outsource_out'" label="外协加工单" prop="relatedOrderId">
          <el-select v-model="form.relatedOrderId" placeholder="请选择外协加工单" filterable style="width: 100%">
            <el-option v-for="item in outsourceOrders" :key="item.id"
              :label="item.orderNo || item.name" :value="item.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="物料" prop="materialId">
          <el-select v-model="form.materialId" placeholder="搜索或扫码选择物料" filterable remote
                     :remote-method="searchMaterials" :loading="materialSearching" style="width: 100%"
                     @change="onMaterialOrWarehouseChange">
            <el-option v-for="item in materialOptions" :key="item.id"
              :label="`${item.name}${item.spec ? ' [' + item.spec + ']' : ''}`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="仓库" prop="warehouseId">
          <el-select v-model="form.warehouseId" placeholder="请选择仓库" style="width: 100%"
            @change="onMaterialOrWarehouseChange">
            <el-option v-for="item in warehouseOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>

        <!-- 可用库存提示 -->
        <el-form-item v-if="stockInfo.checked" label="可用库存">
          <div style="display: flex; align-items: center; gap: 12px">
            <el-tag :type="stockInfo.available > 0 ? 'success' : 'danger'" size="large">
              {{ stockInfo.available }}
            </el-tag>
            <span style="color: #909399; font-size: 12px">
              (总库存: {{ stockInfo.total }} | 锁定: {{ stockInfo.locked }})
            </span>
          </div>
        </el-form-item>

        <el-form-item label="数量" prop="quantity">
          <el-input-number v-model="form.quantity" :min="0.01" :precision="2"
            :max="stockInfo.checked ? stockInfo.available : 999999" style="width: 100%" />
        </el-form-item>
        <el-form-item label="批次号" prop="batchNo">
          <el-select v-model="form.batchNo" placeholder="选择批次号" filterable allow-create style="width: 100%">
            <el-option v-for="b in batchOptions" :key="b" :label="b" :value="b" />
          </el-select>
        </el-form-item>

        <!-- 报废原因（仅报废出库） -->
        <el-form-item v-if="form.stockOutType === 'scrap_out'" label="报废原因" prop="scrapReason">
          <el-select v-model="form.scrapReason" placeholder="请选择报废原因" style="width: 100%">
            <el-option label="质量不合格" value="quality_fail" />
            <el-option label="过期变质" value="expired" />
            <el-option label="损坏丢失" value="damaged" />
            <el-option label="技术淘汰" value="obsolete" />
            <el-option label="其他" value="other" />
          </el-select>
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
        <el-form-item>
          <el-button type="warning" :loading="submitLoading" @click="handleSubmit">
            确认出库
          </el-button>
          <el-button @click="resetForm">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 最近出库记录 -->
    <el-card shadow="never" style="margin-top: 12px">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>最近出库记录</span>
          <div style="display:flex;gap:8px;align-items:center">
            <el-input v-model="queryParams.materialName" placeholder="物料名称" clearable
              style="width: 200px" @clear="loadData" @keyup.enter="loadData">
              <template #append>
                <el-button @click="loadData">搜索</el-button>
              </template>
            </el-input>
            <el-button :icon="Setting" @click="colConfigDialogRef.open()">列配置</el-button>
          </div>
        </div>
      </template>
      <el-table :data="tableData" v-loading="loading" border stripe size="small">
        <el-table-column v-for="col in visibleColumns" :key="col.key"
          :label="col.label" :min-width="col.minWidth || 100" :width="col.width" :align="col.align || 'left'" show-overflow-tooltip>
          <template #default="{ row }">
            <template v-if="col.key === 'transactionType'">
              <el-tag size="small" type="warning">{{ typeMap[row.transactionType] || row.transactionType || '出库' }}</el-tag>
            </template>
            <template v-else-if="col.key === 'quantity'">
              <span style="color: #e6a23c; font-weight: 500">{{ row.quantity }}</span>
            </template>
            <template v-else>{{ row[col.key] || '-' }}</template>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50]" :total="total" layout="total, sizes, prev, pager, next" background
          @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <!-- 列配置 -->
    <ColumnConfigDialog ref="colConfigDialogRef" :all-columns="allColumns" :default-columns="OUT_DEFAULT_COLUMNS" @save="saveConfig" />
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Setting } from '@element-plus/icons-vue'
import { stockOut, getInventoryTransactions, getInventoryPage } from '@/api/inventory'
import { getWarehouseSelect } from '@/api/system'
import { getMaterialPage } from '@/api/master'
import { getWorkOrderPage } from '@/api/production'
import { getSalesOrderPage } from '@/api/order'
import { getOutsourceOrderPage } from '@/api/outsource'
import { useAiChatStore } from '@/store/ai-chat'
import { useColumnConfig } from '@/composables/useColumnConfig'
import ColumnConfigDialog from '@/components/ColumnConfigDialog.vue'

const aiChatStore = useAiChatStore()

const OUT_DEFAULT_COLUMNS = [
  { key: 'transactionNo', label: '流水号', minWidth: 160 },
  { key: 'transactionType', label: '出库类型', width: 100, align: 'center' },
  { key: 'materialName', label: '物料名称', minWidth: 130 },
  { key: 'materialSpec', label: '规格', width: 100 },
  { key: 'warehouseName', label: '仓库', width: 100 },
  { key: 'quantity', label: '数量', width: 80, align: 'right' },
  { key: 'batchNo', label: '批次号', width: 130 },
  { key: 'relatedOrderNo', label: '关联单据', minWidth: 175 },
  { key: 'operatorName', label: '操作人', width: 100 },
  { key: 'createTime', label: '时间', minWidth: 165 },
]
const colConfigDialogRef = ref(null)
const { visibleColumns, allColumns, loadColumnConfig, saveConfig } = useColumnConfig('inventory_out', OUT_DEFAULT_COLUMNS)

// AI填入表单
watch(() => aiChatStore.pendingFillData, (data) => {
  if (data?.module === 'stockOut' && data.records?.length) {
    const rec = data.records[0]
    Object.keys(form).forEach(k => { if (rec[k] !== undefined) form[k] = rec[k] })
    aiChatStore.clearFillData()
    ElMessage.success('AI已填入出库表单，请确认后提交')
  }
})

const typeMap = {
  production_out: '生产领料', sales_out: '销售发货', outsource_out: '外协发料',
  scrap_out: '报废出库', other_out: '其他出库', OUT: '出库'
}

const materialOptions = ref([])
const warehouseOptions = ref([])
const workOrders = ref([])
const salesOrders = ref([])
const outsourceOrders = ref([])
const batchOptions = ref([])

// 可用库存信息
const stockInfo = reactive({ checked: false, total: 0, locked: 0, available: 0 })

const formRef = ref(null)
const submitLoading = ref(false)
const form = reactive({
  stockOutType: 'production_out', materialId: undefined, warehouseId: undefined,
  quantity: 1, batchNo: '', relatedOrderId: undefined, remark: '', scrapReason: ''
})
const rules = {
  materialId: [{ required: true, message: '请选择物料', trigger: 'change' }],
  warehouseId: [{ required: true, message: '请选择仓库', trigger: 'change' }],
  quantity: [{ required: true, message: '请输入数量', trigger: 'blur' }],
  relatedOrderId: [{ required: true, message: '请选择关联单据', trigger: 'change' }],
  scrapReason: [{ required: true, message: '请选择报废原因', trigger: 'change' }]
}

function onTypeChange() {
  form.relatedOrderId = undefined
  form.scrapReason = ''
}

// 物料或仓库变化时查询可用库存
async function onMaterialOrWarehouseChange() {
  if (!form.materialId || !form.warehouseId) {
    stockInfo.checked = false
    return
  }
  try {
    const res = await getInventoryPage({
      materialId: form.materialId, warehouseId: form.warehouseId, pageNum: 1, pageSize: 1
    })
    const record = res.data?.records?.[0]
    if (record) {
      stockInfo.total = record.quantity || 0
      stockInfo.locked = record.lockedQty || 0
      stockInfo.available = (record.quantity || 0) - (record.lockedQty || 0)
    } else {
      stockInfo.total = 0; stockInfo.locked = 0; stockInfo.available = 0
    }
    stockInfo.checked = true
    // 加载该物料仓库的批次号列表
    loadBatchOptions()
  } catch { stockInfo.checked = false }
}

// 加载批次号选项
async function loadBatchOptions() {
  try {
    const res = await getInventoryTransactions({
      materialId: form.materialId, warehouseId: form.warehouseId,
      type: 'IN', pageNum: 1, pageSize: 50
    })
    const batches = (res.data?.records || []).map(r => r.batchNo).filter(Boolean)
    batchOptions.value = [...new Set(batches)]
  } catch { batchOptions.value = [] }
}

// 工单选择后自动填充物料（如果是BOM领料）
function onWorkOrderChange(woId) {
  const wo = workOrders.value.find(w => w.id === woId)
  if (wo && wo.productId) {
    // 工单关联的产品不一定是领料物料，这里仅做提示
  }
}

function resetForm() {
  form.materialId = undefined; form.warehouseId = undefined; form.quantity = 1
  form.batchNo = ''; form.relatedOrderId = undefined; form.remark = ''; form.scrapReason = ''
  stockInfo.checked = false
  formRef.value?.resetFields()
}

async function handleSubmit() {
  await formRef.value.validate()
  // 库存校验
  if (stockInfo.checked && form.quantity > stockInfo.available) {
    ElMessage.warning(`可用库存不足！当前可用: ${stockInfo.available}`)
    return
  }
  submitLoading.value = true
  try {
    await stockOut({
      materialId: form.materialId, warehouseId: form.warehouseId, quantity: form.quantity,
      batchNo: form.batchNo, transactionType: form.stockOutType,
      relatedOrderId: form.relatedOrderId, remark: form.remark,
      scrapReason: form.scrapReason || undefined
    })
    ElMessage.success('出库成功'); resetForm(); loadData()
  } finally { submitLoading.value = false }
}

// 出库记录
const queryParams = reactive({ type: 'OUT', pageNum: 1, pageSize: 10, materialName: '' })
const loading = ref(false); const tableData = ref([]); const total = ref(0)

async function loadData() {
  loading.value = true
  try {
    const res = await getInventoryTransactions(queryParams)
    tableData.value = res.data?.records || []; total.value = res.data?.total || 0
  } catch {} finally { loading.value = false }
}

// 物料 remote 搜索(避免一次性拉全量)
const materialSearching = ref(false)
let _matSearchTimer = null
async function searchMaterials(keyword) {
  if (_matSearchTimer) clearTimeout(_matSearchTimer)
  if (!keyword || !keyword.trim()) { materialOptions.value = []; return }
  _matSearchTimer = setTimeout(async () => {
    materialSearching.value = true
    try {
      const res = await getMaterialPage({ materialName: keyword.trim(), pageNum: 1, pageSize: 30 })
      materialOptions.value = (res.data?.records || []).map(m => ({ id: m.id, name: m.materialName, spec: m.spec, unit: m.unit }))
    } catch {} finally { materialSearching.value = false }
  }, 300)
}

async function loadOptions() {
  try {
    const [whRes, matRes] = await Promise.all([getWarehouseSelect(), Promise.resolve({ data: [] })])
    warehouseOptions.value = whRes.data || []; materialOptions.value = matRes.data || []
  } catch {}
}

async function loadRelatedOrders() {
  try {
    const [woRes, soRes, osRes] = await Promise.all([
      getWorkOrderPage({ pageNum: 1, pageSize: 200, status: 'in_progress' }),
      getSalesOrderPage({ pageNum: 1, pageSize: 200, status: 'confirmed' }),
      getOutsourceOrderPage({ pageNum: 1, pageSize: 200, status: 'confirmed' })
    ])
    workOrders.value = woRes.data?.records || []
    salesOrders.value = soRes.data?.records || []
    outsourceOrders.value = osRes.data?.records || []
  } catch {}
}

onMounted(() => { loadOptions(); loadRelatedOrders(); loadData(); loadColumnConfig() })
</script>

<style scoped lang="scss">
.page-container { padding: 16px; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
