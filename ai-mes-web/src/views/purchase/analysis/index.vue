<template>
  <div class="page-container">
    <!-- 筛选栏 -->
    <el-card shadow="never" class="filter-card">
      <el-row :gutter="12" align="middle">
        <el-col :span="8">
          <el-date-picker
            v-model="dateRange"
            type="monthrange"
            range-separator="至"
            start-placeholder="开始月份"
            end-placeholder="结束月份"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-col>
        <el-col :span="5">
          <el-select
            v-if="activeTab === 'supplier'"
            v-model="filterSupplierId"
            placeholder="全部供应商"
            clearable
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="s in supplierOptions"
              :key="s.id"
              :label="s.name"
              :value="s.id"
            />
          </el-select>
          <el-select
            v-else
            v-model="filterMaterialId"
            placeholder="全部物料"
            clearable
            filterable
            remote
            :remote-method="searchMaterials"
            :loading="materialSearching"
            style="width: 100%"
          >
            <el-option
              v-for="m in materialOptions"
              :key="m.id"
              :label="m.materialName"
              :value="m.id"
            />
          </el-select>
        </el-col>
        <el-col :span="4">
          <el-button type="primary" :loading="loading" @click="loadData">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-col>
        <el-col :span="7" style="text-align: right">
          <el-button
            type="success"
            :icon="Download"
            :loading="exporting"
            @click="handleExport"
          >导出 Excel</el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- Tab 切换 -->
    <el-tabs v-model="activeTab" class="analysis-tabs" @tab-change="onTabChange">
      <!-- 供应商维度 -->
      <el-tab-pane label="供应商维度" name="supplier">
        <el-table
          v-loading="loading"
          :data="supplierData"
          row-key="supplierId"
          border
          stripe
          style="width: 100%"
        >
          <el-table-column type="expand">
            <template #default="{ row }">
              <div class="expand-detail">
                <el-table
                  :data="row.materialDetails"
                  size="small"
                  border
                  style="width: 90%; margin: 0 auto"
                >
                  <el-table-column prop="materialName" label="物料名称" min-width="140" />
                  <el-table-column label="规格" min-width="120">
                    <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
                  </el-table-column>
                  <el-table-column prop="unit" label="单位" width="70" align="center" />
                  <el-table-column prop="totalQty" label="采购数量" width="110" align="right">
                    <template #default="{ row: d }">{{ formatNum(d.totalQty) }}</template>
                  </el-table-column>
                  <el-table-column prop="avgUnitPrice" label="均价" width="110" align="right">
                    <template #default="{ row: d }">¥{{ formatNum(d.avgUnitPrice) }}</template>
                  </el-table-column>
                  <el-table-column prop="totalAmount" label="采购金额" width="130" align="right">
                    <template #default="{ row: d }">
                      <span style="color: #409eff; font-weight: 600">¥{{ formatMoney(d.totalAmount) }}</span>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="supplierName" label="供应商" min-width="160" />
          <el-table-column prop="orderCount" label="订单数" width="90" align="center" />
          <el-table-column prop="itemCount" label="品种数" width="90" align="center" />
          <el-table-column prop="totalQty" label="总采购量" width="120" align="right">
            <template #default="{ row }">{{ formatNum(row.totalQty) }}</template>
          </el-table-column>
          <el-table-column prop="avgUnitPrice" label="综合均价" width="120" align="right">
            <template #default="{ row }">¥{{ formatNum(row.avgUnitPrice) }}</template>
          </el-table-column>
          <el-table-column prop="totalAmount" label="采购总额" width="140" align="right">
            <template #default="{ row }">
              <span style="color: #409eff; font-weight: 700">¥{{ formatMoney(row.totalAmount) }}</span>
            </template>
          </el-table-column>
        </el-table>

        <!-- 供应商维度汇总 -->
        <el-row :gutter="16" class="summary-row" v-if="supplierData.length">
          <el-col :span="6">
            <div class="summary-card">
              <div class="s-label">供应商数</div>
              <div class="s-value">{{ supplierData.length }}</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="summary-card">
              <div class="s-label">订单总数</div>
              <div class="s-value">{{ supplierTotal.orderCount }}</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="summary-card">
              <div class="s-label">品种数</div>
              <div class="s-value">{{ supplierTotal.itemCount }}</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="summary-card">
              <div class="s-label">采购总额</div>
              <div class="s-value blue">¥{{ formatMoney(supplierTotal.totalAmount) }}</div>
            </div>
          </el-col>
        </el-row>
      </el-tab-pane>

      <!-- 物料维度 -->
      <el-tab-pane label="物料维度" name="material">
        <el-table
          v-loading="loading"
          :data="materialData"
          row-key="materialId"
          border
          stripe
          style="width: 100%"
        >
          <el-table-column type="expand">
            <template #default="{ row }">
              <div class="expand-detail">
                <el-table
                  :data="row.supplierDetails"
                  size="small"
                  border
                  style="width: 90%; margin: 0 auto"
                >
                  <el-table-column prop="supplierName" label="供应商" min-width="160" />
                  <el-table-column prop="orderCount" label="订单数" width="80" align="center" />
                  <el-table-column prop="totalQty" label="采购数量" width="120" align="right">
                    <template #default="{ row: d }">{{ formatNum(d.totalQty) }}</template>
                  </el-table-column>
                  <el-table-column prop="avgUnitPrice" label="均价" width="110" align="right">
                    <template #default="{ row: d }">¥{{ formatNum(d.avgUnitPrice) }}</template>
                  </el-table-column>
                  <el-table-column prop="totalAmount" label="采购金额" width="130" align="right">
                    <template #default="{ row: d }">
                      <span style="color: #409eff; font-weight: 600">¥{{ formatMoney(d.totalAmount) }}</span>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="materialName" label="物料名称" min-width="150" />
          <el-table-column label="规格" min-width="120">
            <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
          </el-table-column>
          <el-table-column prop="unit" label="单位" width="70" align="center" />
          <el-table-column prop="supplierCount" label="供应商数" width="90" align="center" />
          <el-table-column prop="orderCount" label="订单数" width="90" align="center" />
          <el-table-column prop="totalQty" label="总采购量" width="120" align="right">
            <template #default="{ row }">{{ formatNum(row.totalQty) }}</template>
          </el-table-column>
          <el-table-column prop="avgUnitPrice" label="综合均价" width="120" align="right">
            <template #default="{ row }">¥{{ formatNum(row.avgUnitPrice) }}</template>
          </el-table-column>
          <el-table-column prop="totalAmount" label="采购总额" width="140" align="right">
            <template #default="{ row }">
              <span style="color: #409eff; font-weight: 700">¥{{ formatMoney(row.totalAmount) }}</span>
            </template>
          </el-table-column>
        </el-table>

        <!-- 物料维度汇总 -->
        <el-row :gutter="16" class="summary-row" v-if="materialData.length">
          <el-col :span="6">
            <div class="summary-card">
              <div class="s-label">物料品种</div>
              <div class="s-value">{{ materialData.length }}</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="summary-card">
              <div class="s-label">订单总数</div>
              <div class="s-value">{{ materialTotal.orderCount }}</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="summary-card">
              <div class="s-label">供应商数</div>
              <div class="s-value">{{ materialTotal.supplierCount }}</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="summary-card">
              <div class="s-label">采购总额</div>
              <div class="s-value blue">¥{{ formatMoney(materialTotal.totalAmount) }}</div>
            </div>
          </el-col>
        </el-row>
      </el-tab-pane>
    </el-tabs>

    <!-- 空状态 -->
    <el-empty
      v-if="!loading && activeTab === 'supplier' && !supplierData.length"
      description="暂无数据，请调整筛选条件后查询"
    />
    <el-empty
      v-if="!loading && activeTab === 'material' && !materialData.length"
      description="暂无数据，请调整筛选条件后查询"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import {
  getPurchaseAnalysisSupplier,
  getPurchaseAnalysisMaterial,
  exportPurchaseAnalysis
} from '@/api/purchase'
import { getSupplierSelect, getMaterialPage } from '@/api/master'

// ======================== 状态 ========================
const activeTab = ref('supplier')
const loading = ref(false)
const exporting = ref(false)

// 筛选
const dateRange = ref(getDefaultDateRange())
const filterSupplierId = ref(null)
const filterMaterialId = ref(null)

// 数据
const supplierData = ref([])
const materialData = ref([])

// 下拉选项
const supplierOptions = ref([])
const materialOptions = ref([])

// ======================== 计算属性：汇总行 ========================
const supplierTotal = computed(() => {
  const list = supplierData.value
  return {
    orderCount: list.reduce((s, r) => s + (r.orderCount || 0), 0),
    itemCount: list.reduce((s, r) => s + (r.itemCount || 0), 0),
    totalAmount: list.reduce((s, r) => s + (r.totalAmount || 0), 0)
  }
})

const materialTotal = computed(() => {
  const list = materialData.value
  const supplierSet = new Set()
  list.forEach(r => r.supplierDetails?.forEach(d => supplierSet.add(d.supplierName)))
  return {
    orderCount: list.reduce((s, r) => s + (r.orderCount || 0), 0),
    supplierCount: supplierSet.size,
    totalAmount: list.reduce((s, r) => s + (r.totalAmount || 0), 0)
  }
})

// ======================== 工具函数 ========================
function getDefaultDateRange() {
  const now = new Date()
  const start = new Date(now.getFullYear(), now.getMonth() - 5, 1)
  const pad = n => String(n).padStart(2, '0')
  const fmt = d => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-01`
  // end: first day of current month
  const end = new Date(now.getFullYear(), now.getMonth(), 1)
  return [fmt(start), fmt(end)]
}

function formatMoney(val) {
  if (!val && val !== 0) return '0.00'
  return Number(val).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function formatNum(val) {
  if (!val && val !== 0) return '0.00'
  return Number(val).toFixed(2)
}

function buildParams() {
  const params = {}
  if (dateRange.value && dateRange.value[0]) params.dateStart = dateRange.value[0]
  if (dateRange.value && dateRange.value[1]) {
    // end of the selected month
    const d = new Date(dateRange.value[1])
    d.setMonth(d.getMonth() + 1)
    d.setDate(0)
    const pad = n => String(n).padStart(2, '0')
    params.dateEnd = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
  }
  return params
}

// ======================== 数据加载 ========================
async function loadData() {
  if (activeTab.value === 'supplier') {
    await loadSupplier()
  } else {
    await loadMaterial()
  }
}

async function loadSupplier() {
  loading.value = true
  try {
    const params = buildParams()
    if (filterSupplierId.value) params.supplierId = filterSupplierId.value
    const res = await getPurchaseAnalysisSupplier(params)
    supplierData.value = res.data || []
  } catch (e) {
    ElMessage.error('加载供应商分析数据失败')
  } finally {
    loading.value = false
  }
}

async function loadMaterial() {
  loading.value = true
  try {
    const params = buildParams()
    if (filterMaterialId.value) params.materialId = filterMaterialId.value
    const res = await getPurchaseAnalysisMaterial(params)
    materialData.value = res.data || []
  } catch (e) {
    ElMessage.error('加载物料分析数据失败')
  } finally {
    loading.value = false
  }
}

function onTabChange(tab) {
  activeTab.value = tab
  loadData()
}

function handleReset() {
  dateRange.value = getDefaultDateRange()
  filterSupplierId.value = null
  filterMaterialId.value = null
  loadData()
}

// ======================== 导出 ========================
async function handleExport() {
  exporting.value = true
  try {
    const params = buildParams()
    params.dimension = activeTab.value
    if (activeTab.value === 'supplier' && filterSupplierId.value) {
      params.filterId = filterSupplierId.value
    } else if (activeTab.value === 'material' && filterMaterialId.value) {
      params.filterId = filterMaterialId.value
    }
    const res = await exportPurchaseAnalysis(params)
    const blob = new Blob([res], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    const dimension = activeTab.value === 'supplier' ? '供应商' : '物料'
    link.download = `采购分析_${dimension}维度_${new Date().toLocaleDateString('zh-CN').replaceAll('/', '')}.xlsx`
    link.click()
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (e) {
    ElMessage.error('导出失败')
  } finally {
    exporting.value = false
  }
}

// ======================== 初始化下拉 ========================
async function loadOptions() {
  try {
    const res = await getSupplierSelect()
    supplierOptions.value = res.data || []
  } catch {}
}

// 物料 remote 搜索
const materialSearching = ref(false)
let _matSearchTimer = null
async function searchMaterials(keyword) {
  if (_matSearchTimer) clearTimeout(_matSearchTimer)
  if (!keyword || !keyword.trim()) { materialOptions.value = []; return }
  _matSearchTimer = setTimeout(async () => {
    materialSearching.value = true
    try {
      const res = await getMaterialPage({ materialName: keyword.trim(), pageNum: 1, pageSize: 30 })
      materialOptions.value = res.data?.records || []
    } catch {} finally { materialSearching.value = false }
  }, 300)
}

onMounted(() => {
  loadOptions()
  loadData()
})
</script>

<style scoped>
.page-container {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.filter-card {
  flex-shrink: 0;
}

.analysis-tabs {
  background: #fff;
  border-radius: 4px;
  padding: 0 16px 16px;
}

:deep(.el-tabs__header) {
  margin-bottom: 16px;
}

.expand-detail {
  padding: 12px 0;
  background: #fafafa;
}

.summary-row {
  margin-top: 16px;
}

.summary-card {
  background: #f5f7fa;
  border-radius: 8px;
  padding: 14px 20px;
  text-align: center;
  border: 1px solid #e4e7ed;
}

.s-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 6px;
}

.s-value {
  font-size: 22px;
  font-weight: 700;
  color: #303133;
}

.s-value.blue {
  color: #409eff;
}
</style>
