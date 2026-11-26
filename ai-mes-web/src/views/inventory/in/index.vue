<template>
  <div class="page-container">
    <!-- 入库表单 -->
    <el-card shadow="never">
      <template #header><span>入库操作</span></template>

      <!-- 入库类型Tab -->
      <el-tabs v-model="form.stockInType" @tab-change="onTypeChange">
        <el-tab-pane label="采购入库" name="purchase_in" />
        <el-tab-pane label="生产入库(成品)" name="production_in" />
        <el-tab-pane label="半成品入库" name="semi_in" />
        <el-tab-pane label="外协收货" name="outsource_in" />
        <el-tab-pane label="外协来料" name="outsource_material_in" />
        <el-tab-pane label="退货入库" name="return_in" />
        <el-tab-pane label="其他入库" name="other_in" />
      </el-tabs>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" style="max-width: 640px">
        <!-- 关联单据（根据类型动态显示） -->
        <el-form-item v-if="form.stockInType === 'purchase_in'" label="采购订单" prop="relatedOrderId">
          <el-select v-model="form.relatedOrderId" placeholder="请选择采购订单" filterable style="width: 100%">
            <el-option v-for="item in purchaseOrders" :key="item.id" :label="item.orderNo || item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="['production_in', 'semi_in'].includes(form.stockInType)" label="关联工单" prop="relatedOrderId">
          <el-select v-model="form.relatedOrderId" placeholder="请选择工单" filterable style="width: 100%">
            <el-option v-for="item in workOrders" :key="item.id" :label="item.orderNo || item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.stockInType === 'outsource_in'" label="外协加工单" prop="relatedOrderId">
          <el-select v-model="form.relatedOrderId" placeholder="请选择外协加工单" filterable style="width: 100%">
            <el-option v-for="item in outsourceOrders" :key="item.id" :label="item.orderNo || item.name" :value="item.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="物料" prop="materialId">
          <el-select v-model="form.materialId" placeholder="搜索或扫码选择物料" filterable remote
                     :remote-method="searchMaterials" :loading="materialSearching" style="width: 100%">
            <el-option v-for="item in materialOptions" :key="item.id"
              :label="`${item.name}${item.spec ? ' [' + item.spec + ']' : ''}`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="仓库" prop="warehouseId">
          <el-select v-model="form.warehouseId" placeholder="请选择仓库" style="width: 100%">
            <el-option v-for="item in warehouseOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="数量" prop="quantity">
          <el-input-number v-model="form.quantity" :min="0.01" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="批次号" prop="batchNo">
          <div style="display: flex; gap: 8px">
            <el-input v-model="form.batchNo" placeholder="输入或自动生成" style="flex: 1" />
            <el-button type="primary" plain size="small" @click="generateBatchNo">自动生成</el-button>
          </div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确认入库</el-button>
          <el-button @click="resetForm">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 待入库工单（仅生产入库Tab下显示） -->
    <el-card v-if="form.stockInType === 'production_in'" shadow="never" style="margin-top: 12px">
      <template #header>
        <div style="display: flex; align-items: center; justify-content: space-between">
          <span>待入库工单</span>
          <el-button type="primary" link @click="loadPendingList">刷新</el-button>
        </div>
      </template>
      <el-alert v-if="pendingList.length" title="以下工单已通过成品质检，待仓库确认入库" type="info" :closable="false" show-icon style="margin-bottom: 12px" />
      <el-table :data="pendingList" v-loading="pendingLoading" border stripe size="small">
        <el-table-column prop="woNo" label="工单号" min-width="140" show-overflow-tooltip />
        <el-table-column prop="productName" label="产品名称" min-width="130" />
        <el-table-column prop="planQty" label="计划数" width="80" align="right" />
        <el-table-column prop="passQty" label="合格数" width="80" align="right" />
        <el-table-column label="质检结论" width="90" align="center">
          <template #default>
            <el-tag type="success" size="small">合格</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="openConfirmStockIn(row)">确认入库</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!pendingLoading && !pendingList.length" description="暂无待入库工单" :image-size="60" />
    </el-card>

    <!-- 列配置 -->
    <ColumnConfigDialog ref="colConfigDialogRef" :all-columns="allColumns" :default-columns="IN_DEFAULT_COLUMNS" @save="saveConfig" />

    <!-- 确认入库弹窗 -->
    <el-dialog v-model="confirmVisible" title="成品入库确认" width="480px" destroy-on-close>
      <el-descriptions :column="1" border size="small" style="margin-bottom: 16px">
        <el-descriptions-item label="工单号">{{ confirmRow.woNo }}</el-descriptions-item>
        <el-descriptions-item label="产品名称">{{ confirmRow.productName }}</el-descriptions-item>
        <el-descriptions-item label="质检合格数">{{ confirmRow.passQty }}</el-descriptions-item>
      </el-descriptions>
      <el-form ref="confirmFormRef" :model="confirmForm" :rules="confirmRules" label-width="80px">
        <el-form-item label="入库数量" prop="qty">
          <el-input-number v-model="confirmForm.qty" :min="0.01" :max="confirmRow.passQty || 99999" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="入库仓库" prop="warehouseId">
          <el-select v-model="confirmForm.warehouseId" placeholder="请选择仓库" style="width: 100%">
            <el-option v-for="item in warehouseOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="批次号" prop="batchNo">
          <div style="display: flex; gap: 8px">
            <el-input v-model="confirmForm.batchNo" placeholder="输入或自动生成" style="flex: 1" />
            <el-button type="primary" plain size="small" @click="generateConfirmBatchNo">自动生成</el-button>
          </div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="confirmForm.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="confirmVisible = false">取消</el-button>
        <el-button type="primary" :loading="confirmLoading" @click="handleConfirmStockIn">确认入库</el-button>
      </template>
    </el-dialog>

    <!-- 最近入库记录 -->
    <el-card shadow="never" style="margin-top: 12px">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span>最近入库记录</span>
          <el-button :icon="Setting" @click="colConfigDialogRef.open()">列配置</el-button>
        </div>
      </template>
      <el-table :data="tableData" v-loading="loading" border stripe size="small">
        <el-table-column v-for="col in visibleColumns" :key="col.key"
          :label="col.label" :min-width="col.minWidth || 100" :width="col.width" :align="col.align || 'left'" show-overflow-tooltip>
          <template #default="{ row }">
            <template v-if="col.key === 'transactionType'">
              <el-tag size="small">{{ typeMap[row.transactionType] || row.transactionType || '入库' }}</el-tag>
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
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Setting } from '@element-plus/icons-vue'
import { stockIn, getInventoryTransactions, getPendingStockIn, confirmProductionIn } from '@/api/inventory'
import { getWarehouseSelect } from '@/api/system'
import { getMaterialPage } from '@/api/master'
import { useAiChatStore } from '@/store/ai-chat'
import { useColumnConfig } from '@/composables/useColumnConfig'
import ColumnConfigDialog from '@/components/ColumnConfigDialog.vue'

const aiChatStore = useAiChatStore()

const IN_DEFAULT_COLUMNS = [
  { key: 'transactionNo', label: '流水号', minWidth: 160 },
  { key: 'transactionType', label: '入库类型', width: 100, align: 'center' },
  { key: 'materialName', label: '物料名称', minWidth: 130 },
  { key: 'warehouseName', label: '仓库', width: 100 },
  { key: 'quantity', label: '数量', width: 80, align: 'right' },
  { key: 'batchNo', label: '批次号', width: 130 },
  { key: 'relatedOrderNo', label: '关联单据', minWidth: 175 },
  { key: 'operatorName', label: '操作人', width: 100 },
  { key: 'createTime', label: '时间', minWidth: 165 },
]
const colConfigDialogRef = ref(null)
const { visibleColumns, allColumns, loadColumnConfig, saveConfig } = useColumnConfig('inventory_in', IN_DEFAULT_COLUMNS)

// AI填入表单
watch(() => aiChatStore.pendingFillData, (data) => {
  if (data?.module === 'stockIn' && data.records?.length) {
    const rec = data.records[0]
    Object.keys(form).forEach(k => { if (rec[k] !== undefined) form[k] = rec[k] })
    aiChatStore.clearFillData()
    ElMessage.success('AI已填入入库表单，请确认后提交')
  }
})

const typeMap = { purchase_in: '采购入库', production_in: '生产入库', semi_in: '半成品入库', outsource_in: '外协收货', outsource_material_in: '外协来料', return_in: '退货入库', other_in: '其他入库', transfer_in: '调拨入库', IN: '入库' }

const materialOptions = ref([])
const warehouseOptions = ref([])
const purchaseOrders = ref([])
const workOrders = ref([])
const outsourceOrders = ref([])

const formRef = ref(null)
const submitLoading = ref(false)
const form = reactive({
  stockInType: 'purchase_in', materialId: undefined, warehouseId: undefined,
  quantity: 1, batchNo: '', relatedOrderId: undefined, remark: ''
})
const rules = {
  materialId: [{ required: true, message: '请选择物料', trigger: 'change' }],
  warehouseId: [{ required: true, message: '请选择仓库', trigger: 'change' }],
  quantity: [{ required: true, message: '请输入数量', trigger: 'blur' }],
  batchNo: [{ required: true, message: '请输入批次号', trigger: 'blur' }]
}

function onTypeChange(val) {
  form.relatedOrderId = undefined
  if (val === 'production_in') loadPendingList()
}

function generateBatchNo() {
  const now = new Date()
  const d = now.toISOString().replace(/[-T:]/g, '').substring(0, 8)
  form.batchNo = d + String(Math.floor(Math.random() * 900) + 100)
}

function resetForm() {
  form.materialId = undefined; form.warehouseId = undefined; form.quantity = 1
  form.batchNo = ''; form.relatedOrderId = undefined; form.remark = ''
  formRef.value?.resetFields()
}

async function handleSubmit() {
  await formRef.value.validate()
  submitLoading.value = true
  try {
    await stockIn({ materialId: form.materialId, warehouseId: form.warehouseId, quantity: form.quantity,
      batchNo: form.batchNo, transactionType: form.stockInType, relatedOrderId: form.relatedOrderId, remark: form.remark })
    ElMessage.success('入库成功'); resetForm(); loadData()
  } finally { submitLoading.value = false }
}

// 记录
const queryParams = reactive({ type: 'IN', pageNum: 1, pageSize: 10 })
const loading = ref(false); const tableData = ref([]); const total = ref(0)

async function loadData() {
  loading.value = true
  try {
    const res = await getInventoryTransactions(queryParams)
    tableData.value = res.data?.records || []; total.value = res.data?.total || 0
  } catch {} finally { loading.value = false }
}

// 仅加载轻量数据(仓库),物料改为 remote 按需加载
async function loadOptions() {
  try {
    const whRes = await getWarehouseSelect()
    warehouseOptions.value = whRes.data || []
  } catch {}
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

// 待入库工单
const pendingList = ref([])
const pendingLoading = ref(false)

async function loadPendingList() {
  pendingLoading.value = true
  try {
    const res = await getPendingStockIn()
    pendingList.value = res.data || []
  } catch {} finally { pendingLoading.value = false }
}

// 确认入库弹窗
const confirmVisible = ref(false)
const confirmLoading = ref(false)
const confirmFormRef = ref(null)
const confirmRow = ref({})
const confirmForm = reactive({ qty: 0, warehouseId: undefined, batchNo: '', remark: '' })
const confirmRules = {
  qty: [{ required: true, message: '请输入入库数量', trigger: 'blur' }],
  warehouseId: [{ required: true, message: '请选择仓库', trigger: 'change' }],
  batchNo: [{ required: true, message: '请输入批次号', trigger: 'blur' }]
}

function openConfirmStockIn(row) {
  confirmRow.value = row
  confirmForm.qty = row.passQty || 0
  confirmForm.warehouseId = row.defaultWarehouseId || undefined
  confirmForm.batchNo = ''
  confirmForm.remark = ''
  generateConfirmBatchNo()
  confirmVisible.value = true
}

function generateConfirmBatchNo() {
  const now = new Date()
  const d = now.toISOString().replace(/[-T:]/g, '').substring(0, 8)
  confirmForm.batchNo = d + String(Math.floor(Math.random() * 900) + 100)
}

async function handleConfirmStockIn() {
  await confirmFormRef.value.validate()
  confirmLoading.value = true
  try {
    await confirmProductionIn({
      workOrderId: confirmRow.value.workOrderId,
      warehouseId: confirmForm.warehouseId,
      qty: confirmForm.qty,
      batchNo: confirmForm.batchNo,
      remark: confirmForm.remark
    })
    ElMessage.success('成品入库成功')
    confirmVisible.value = false
    loadPendingList()
    loadData()
  } finally { confirmLoading.value = false }
}

onMounted(() => { loadOptions(); loadData(); loadPendingList(); loadColumnConfig() })
</script>

<style scoped lang="scss">
.page-container { padding: 16px; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
