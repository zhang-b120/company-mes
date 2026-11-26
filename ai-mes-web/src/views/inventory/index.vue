<template>
  <div class="page-container">
    <!-- search bar -->
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="物料名称">
          <el-input
            v-model="queryParams.keyword"
            placeholder="请输入物料名称"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="仓库">
          <el-select v-model="queryParams.warehouseId" placeholder="请选择仓库" clearable style="width: 180px">
            <el-option
              v-for="item in warehouseOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
        <el-form-item>
          <el-button type="info" plain @click="handleLock">锁定库存</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- tabs -->
    <el-card class="table-card" shadow="never">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <!-- Tab 1: 库存台账 -->
        <el-tab-pane label="库存台账" name="inventory">
          <el-table :data="inventoryData" v-loading="loading" border stripe>
            <el-table-column prop="materialName" label="物料名称" min-width="140" />
            <el-table-column label="规格" min-width="120">
              <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
            </el-table-column>
            <el-table-column prop="unit" label="单位" width="80" align="center" />
            <el-table-column prop="warehouseName" label="仓库" min-width="120" />
            <el-table-column prop="totalQty" label="总数量" width="100" align="right" />
            <el-table-column prop="lockedQty" label="锁定数量" width="100" align="right" />
            <el-table-column prop="availableQty" label="可用数量" width="100" align="right" />
            <el-table-column prop="safetyStock" label="安全库存" width="100" align="right" />
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag
                  :type="row.totalQty < row.safetyStock ? 'danger' : 'success'"
                  size="small"
                >
                  {{ row.totalQty < row.safetyStock ? '低于安全库存' : '正常' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-wrapper">
            <el-pagination
              v-model:current-page="queryParams.pageNum"
              v-model:page-size="queryParams.pageSize"
              :total="inventoryTotal"
              :page-sizes="[10, 20, 50, 100]"
              background
              layout="total, sizes, prev, pager, next, jumper"
              @size-change="loadInventory"
              @current-change="loadInventory"
            />
          </div>
        </el-tab-pane>

        <!-- Tab 2: 库存流水 -->
        <el-tab-pane label="库存流水" name="transactions">
          <el-table :data="transactionData" v-loading="transLoading" border stripe>
            <el-table-column prop="transactionNo" label="流水号" min-width="140" />
            <el-table-column prop="materialName" label="物料名称" min-width="140" />
            <el-table-column prop="warehouseName" label="仓库" min-width="120" />
            <el-table-column prop="transactionType" label="类型" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.transactionType === 'IN' ? 'success' : 'danger'" size="small">
                  {{ row.transactionType === 'IN' ? '入库' : '出库' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="quantity" label="数量" width="100" align="right" />
            <el-table-column prop="batchNo" label="批次号" min-width="120" />
            <el-table-column prop="operatorName" label="操作人" width="100" />
            <el-table-column prop="createTime" label="操作时间" min-width="160" />
            <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
          </el-table>

          <div class="pagination-wrapper">
            <el-pagination
              v-model:current-page="transQueryParams.pageNum"
              v-model:page-size="transQueryParams.pageSize"
              :total="transTotal"
              :page-sizes="[10, 20, 50, 100]"
              background
              layout="total, sizes, prev, pager, next, jumper"
              @size-change="loadTransactions"
              @current-change="loadTransactions"
            />
          </div>
        </el-tab-pane>

        <!-- Tab 3: 库存预警 -->
        <el-tab-pane label="库存预警" name="alerts">
          <el-table :data="alertData" v-loading="alertLoading" border stripe>
            <el-table-column prop="materialName" label="物料名称" min-width="140" />
            <el-table-column label="规格" min-width="120">
              <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
            </el-table-column>
            <el-table-column prop="warehouseName" label="仓库" min-width="120" />
            <el-table-column prop="totalQty" label="当前库存" width="100" align="right">
              <template #default="{ row }">
                <span style="color: #f56c6c; font-weight: bold;">{{ row.totalQty }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="safetyStock" label="安全库存" width="100" align="right" />
            <el-table-column label="缺口" width="100" align="right">
              <template #default="{ row }">
                <span style="color: #f56c6c;">{{ row.safetyStock - row.totalQty }}</span>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- stock-in dialog -->
    <el-dialog v-model="stockInVisible" title="入库" width="520px" destroy-on-close @close="resetStockInForm">
      <el-form ref="stockInFormRef" :model="stockInForm" :rules="stockRules" label-width="100px">
        <el-form-item label="物料" prop="materialId">
          <el-select v-model="stockInForm.materialId" placeholder="请选择物料" filterable remote
                     :remote-method="searchMaterials" :loading="materialSearching" style="width: 100%">
            <el-option
              v-for="item in materialOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="仓库" prop="warehouseId">
          <el-select v-model="stockInForm.warehouseId" placeholder="请选择仓库" style="width: 100%">
            <el-option
              v-for="item in warehouseOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="数量" prop="quantity">
          <el-input-number v-model="stockInForm.quantity" :min="1" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="批次号" prop="batchNo">
          <el-input v-model="stockInForm.batchNo" placeholder="请输入批次号" />
        </el-form-item>
        <el-form-item label="事务类型" prop="transactionType">
          <el-select v-model="stockInForm.transactionType" placeholder="请选择事务类型" style="width: 100%">
            <el-option label="采购入库" value="PURCHASE_IN" />
            <el-option label="生产入库" value="PRODUCTION_IN" />
            <el-option label="退货入库" value="RETURN_IN" />
            <el-option label="其他入库" value="OTHER_IN" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="stockInForm.remark" type="textarea" :rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="stockInVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleStockInSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- stock-out dialog -->
    <el-dialog v-model="stockOutVisible" title="出库" width="520px" destroy-on-close @close="resetStockOutForm">
      <el-form ref="stockOutFormRef" :model="stockOutForm" :rules="stockRules" label-width="100px">
        <el-form-item label="物料" prop="materialId">
          <el-select v-model="stockOutForm.materialId" placeholder="请选择物料" filterable remote
                     :remote-method="searchMaterials" :loading="materialSearching" style="width: 100%">
            <el-option
              v-for="item in materialOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="仓库" prop="warehouseId">
          <el-select v-model="stockOutForm.warehouseId" placeholder="请选择仓库" style="width: 100%">
            <el-option
              v-for="item in warehouseOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="数量" prop="quantity">
          <el-input-number v-model="stockOutForm.quantity" :min="1" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="批次号" prop="batchNo">
          <el-input v-model="stockOutForm.batchNo" placeholder="请输入批次号" />
        </el-form-item>
        <el-form-item label="事务类型" prop="transactionType">
          <el-select v-model="stockOutForm.transactionType" placeholder="请选择事务类型" style="width: 100%">
            <el-option label="生产领料" value="PRODUCTION_OUT" />
            <el-option label="销售出库" value="SALES_OUT" />
            <el-option label="外协发料" value="OUTSOURCE_OUT" />
            <el-option label="其他出库" value="OTHER_OUT" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="stockOutForm.remark" type="textarea" :rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="stockOutVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleStockOutSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- lock dialog -->
    <el-dialog v-model="lockVisible" title="锁定库存" width="520px" destroy-on-close @close="resetLockForm">
      <el-form ref="lockFormRef" :model="lockForm" :rules="lockRules" label-width="100px">
        <el-form-item label="物料" prop="materialId">
          <el-select v-model="lockForm.materialId" placeholder="请选择物料" filterable remote
                     :remote-method="searchMaterials" :loading="materialSearching" style="width: 100%">
            <el-option
              v-for="item in materialOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="仓库" prop="warehouseId">
          <el-select v-model="lockForm.warehouseId" placeholder="请选择仓库" style="width: 100%">
            <el-option
              v-for="item in warehouseOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="锁定数量" prop="lockedQty">
          <el-input-number v-model="lockForm.lockedQty" :min="1" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="锁定原因" prop="reason">
          <el-input v-model="lockForm.reason" type="textarea" :rows="3" placeholder="请输入锁定原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="lockVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleLockSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getInventoryPage,
  getInventoryTransactions,
  getInventoryAlerts,
  stockIn,
  stockOut,
  lockStock
} from '@/api/inventory'
import { getWarehouseSelect } from '@/api/system'
import { getMaterialPage } from '@/api/master'

// --- state ---
const loading = ref(false)
const transLoading = ref(false)
const alertLoading = ref(false)
const submitLoading = ref(false)
const activeTab = ref('inventory')

const warehouseOptions = ref([])
const materialOptions = ref([])

// inventory tab
const inventoryData = ref([])
const inventoryTotal = ref(0)
const queryParams = reactive({
  keyword: '',
  warehouseId: undefined,
  pageNum: 1,
  pageSize: 10
})

// transaction tab
const transactionData = ref([])
const transTotal = ref(0)
const transQueryParams = reactive({
  pageNum: 1,
  pageSize: 10
})

// alert tab
const alertData = ref([])

// stock-in dialog
const stockInVisible = ref(false)
const stockInFormRef = ref(null)
const stockInForm = ref({
  materialId: undefined,
  warehouseId: undefined,
  quantity: 1,
  batchNo: '',
  transactionType: undefined,
  remark: ''
})

// stock-out dialog
const stockOutVisible = ref(false)
const stockOutFormRef = ref(null)
const stockOutForm = ref({
  materialId: undefined,
  warehouseId: undefined,
  quantity: 1,
  batchNo: '',
  transactionType: undefined,
  remark: ''
})

// lock dialog
const lockVisible = ref(false)
const lockFormRef = ref(null)
const lockForm = ref({
  materialId: undefined,
  warehouseId: undefined,
  lockedQty: 1,
  reason: ''
})

// --- rules ---
const stockRules = {
  materialId: [{ required: true, message: '请选择物料', trigger: 'change' }],
  warehouseId: [{ required: true, message: '请选择仓库', trigger: 'change' }],
  quantity: [{ required: true, message: '请输入数量', trigger: 'blur' }],
  batchNo: [{ required: true, message: '请输入批次号', trigger: 'blur' }],
  transactionType: [{ required: true, message: '请选择事务类型', trigger: 'change' }]
}

const lockRules = {
  materialId: [{ required: true, message: '请选择物料', trigger: 'change' }],
  warehouseId: [{ required: true, message: '请选择仓库', trigger: 'change' }],
  lockedQty: [{ required: true, message: '请输入锁定数量', trigger: 'blur' }],
  reason: [{ required: true, message: '请输入锁定原因', trigger: 'blur' }]
}

// --- methods ---
async function loadInventory() {
  loading.value = true
  try {
    const res = await getInventoryPage(queryParams)
    inventoryData.value = res.data.records || []
    inventoryTotal.value = res.data.total || 0
  } catch {
    // handled by interceptor
  } finally {
    loading.value = false
  }
}

async function loadTransactions() {
  transLoading.value = true
  try {
    const res = await getInventoryTransactions(transQueryParams)
    transactionData.value = res.data.records || []
    transTotal.value = res.data.total || 0
  } catch {
    // handled by interceptor
  } finally {
    transLoading.value = false
  }
}

async function loadAlerts() {
  alertLoading.value = true
  try {
    const res = await getInventoryAlerts()
    alertData.value = res.data || []
  } catch {
    // handled by interceptor
  } finally {
    alertLoading.value = false
  }
}

// 仓库少量数据,正常加载;物料改 remote 按需搜索
async function loadOptions() {
  try {
    const whRes = await getWarehouseSelect()
    warehouseOptions.value = whRes.data || []
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
      materialOptions.value = (res.data?.records || []).map(m => ({ id: m.id, name: m.materialName, spec: m.spec }))
    } catch {} finally { materialSearching.value = false }
  }, 300)
}

function handleSearch() {
  queryParams.pageNum = 1
  loadInventory()
}

function handleReset() {
  queryParams.keyword = ''
  queryParams.warehouseId = undefined
  queryParams.pageNum = 1
  queryParams.pageSize = 10
  loadInventory()
}

function handleTabChange(tab) {
  if (tab === 'inventory') {
    loadInventory()
  } else if (tab === 'transactions') {
    loadTransactions()
  } else if (tab === 'alerts') {
    loadAlerts()
  }
}

// stock-in
function handleStockIn() {
  stockInForm.value = {
    materialId: undefined,
    warehouseId: undefined,
    quantity: 1,
    batchNo: '',
    transactionType: undefined,
    remark: ''
  }
  stockInVisible.value = true
}

async function handleStockInSubmit() {
  await stockInFormRef.value.validate()
  submitLoading.value = true
  try {
    await stockIn(stockInForm.value)
    ElMessage.success('入库成功')
    stockInVisible.value = false
    loadInventory()
  } catch {
    // handled by interceptor
  } finally {
    submitLoading.value = false
  }
}

function resetStockInForm() {
  stockInFormRef.value?.resetFields()
}

// stock-out
function handleStockOut() {
  stockOutForm.value = {
    materialId: undefined,
    warehouseId: undefined,
    quantity: 1,
    batchNo: '',
    transactionType: undefined,
    remark: ''
  }
  stockOutVisible.value = true
}

async function handleStockOutSubmit() {
  await stockOutFormRef.value.validate()
  submitLoading.value = true
  try {
    await stockOut(stockOutForm.value)
    ElMessage.success('出库成功')
    stockOutVisible.value = false
    loadInventory()
  } catch {
    // handled by interceptor
  } finally {
    submitLoading.value = false
  }
}

function resetStockOutForm() {
  stockOutFormRef.value?.resetFields()
}

// lock
function handleLock() {
  lockForm.value = {
    materialId: undefined,
    warehouseId: undefined,
    lockedQty: 1,
    reason: ''
  }
  lockVisible.value = true
}

async function handleLockSubmit() {
  await lockFormRef.value.validate()
  submitLoading.value = true
  try {
    await lockStock(lockForm.value)
    ElMessage.success('库存锁定成功')
    lockVisible.value = false
    loadInventory()
  } catch {
    // handled by interceptor
  } finally {
    submitLoading.value = false
  }
}

function resetLockForm() {
  lockFormRef.value?.resetFields()
}

// --- lifecycle ---
onMounted(() => {
  loadOptions()
  loadInventory()
})
</script>

<style scoped lang="scss">
.page-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.search-card {
  :deep(.el-card__body) {
    padding-bottom: 2px;
  }
}
.table-card {
  flex: 1;
}
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
