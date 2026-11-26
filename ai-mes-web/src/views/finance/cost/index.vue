<template>
  <div class="page-container">
    <!-- search bar -->
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="工单号">
          <el-input
            v-model="queryParams.workOrderNo"
            placeholder="请输入工单号"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- table -->
    <el-card class="table-card" shadow="never">
      <div style="display: flex; justify-content: flex-end; gap: 8px; margin-bottom: 10px">
        <el-button type="primary" plain @click="handleExport">导出</el-button>
        <el-button :icon="Setting" @click="colConfigDialogRef.open()">列配置</el-button>
      </div>
      <el-table :data="tableData" v-loading="loading" border stripe show-summary>
        <el-table-column v-for="col in visibleColumns" :key="col.key"
          :label="col.label" :min-width="col.minWidth || 100" :align="col.align || 'left'" show-overflow-tooltip>
          <template #default="{ row }">
            <template v-if="col.key === 'materialCost'">{{ row.materialCost != null ? `¥${Number(row.materialCost).toFixed(2)}` : '-' }}</template>
            <template v-else-if="col.key === 'laborCost'">{{ row.laborCost != null ? `¥${Number(row.laborCost).toFixed(2)}` : '-' }}</template>
            <template v-else-if="col.key === 'outsourceCost'">{{ row.outsourceCost != null ? `¥${Number(row.outsourceCost).toFixed(2)}` : '-' }}</template>
            <template v-else-if="col.key === 'overheadCost'">{{ row.overheadCost != null ? `¥${Number(row.overheadCost).toFixed(2)}` : '-' }}</template>
            <template v-else-if="col.key === 'totalCost'">
              <span style="font-weight: bold; color: #f56c6c;">{{ row.totalCost != null ? `¥${Number(row.totalCost).toFixed(2)}` : '-' }}</span>
            </template>
            <template v-else>{{ row[col.key] || '-' }}</template>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleDetail(row)">明细</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          background
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- cost detail dialog -->
    <el-dialog
      v-model="detailVisible"
      title="成本明细"
      width="720px"
      destroy-on-close
    >
      <template v-if="detailData">
        <el-descriptions :column="2" border style="margin-bottom: 20px;">
          <el-descriptions-item label="工单号">{{ detailData.workOrderNo }}</el-descriptions-item>
          <el-descriptions-item label="产品名称">{{ detailData.productName }}</el-descriptions-item>
          <el-descriptions-item label="合计成本">
            <span style="font-weight: bold; color: #f56c6c;">
              ¥{{ Number(detailData.totalCost || 0).toFixed(2) }}
            </span>
          </el-descriptions-item>
        </el-descriptions>

        <h4 style="margin-bottom: 12px;">材料成本明细</h4>
        <el-table :data="detailData.materialDetails || []" border size="small" style="margin-bottom: 20px;">
          <el-table-column prop="materialName" label="物料名称" min-width="140" />
          <el-table-column prop="quantity" label="用量" width="80" align="right" />
          <el-table-column prop="unitPrice" label="单价" width="100" align="right">
            <template #default="{ row }">¥{{ Number(row.unitPrice || 0).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column prop="amount" label="金额" width="120" align="right">
            <template #default="{ row }">¥{{ Number(row.amount || 0).toFixed(2) }}</template>
          </el-table-column>
        </el-table>

        <h4 style="margin-bottom: 12px;">人工成本明细</h4>
        <el-table :data="detailData.laborDetails || []" border size="small" style="margin-bottom: 20px;">
          <el-table-column prop="stepName" label="工序" min-width="120" />
          <el-table-column prop="workerName" label="人员" width="100" />
          <el-table-column prop="workHours" label="工时" width="80" align="right" />
          <el-table-column prop="amount" label="金额" width="120" align="right">
            <template #default="{ row }">¥{{ Number(row.amount || 0).toFixed(2) }}</template>
          </el-table-column>
        </el-table>

        <h4 style="margin-bottom: 12px;">其他成本</h4>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="外协成本">¥{{ Number(detailData.outsourceCost || 0).toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="制造费用">¥{{ Number(detailData.overheadCost || 0).toFixed(2) }}</el-descriptions-item>
        </el-descriptions>
      </template>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <ColumnConfigDialog ref="colConfigDialogRef" :all-columns="allColumns" :visible-keys="visibleColumns.map(c=>c.key)" @save="saveConfig" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Setting } from '@element-plus/icons-vue'
import { getWoCostDetail } from '@/api/finance'
import request from '@/utils/request'
import { useColumnConfig } from '@/composables/useColumnConfig'
import ColumnConfigDialog from '@/components/ColumnConfigDialog.vue'

const FINANCE_COST_DEFAULT_COLUMNS = [
  { key: 'workOrderNo', label: '工单号', minWidth: 160 },
  { key: 'productName', label: '产品名称', minWidth: 140 },
  { key: 'materialCost', label: '材料成本', minWidth: 120, align: 'right' },
  { key: 'laborCost', label: '人工成本', minWidth: 120, align: 'right' },
  { key: 'outsourceCost', label: '外协成本', minWidth: 120, align: 'right' },
  { key: 'overheadCost', label: '制造费用', minWidth: 120, align: 'right' },
  { key: 'totalCost', label: '合计成本', minWidth: 130, align: 'right' },
]
const colConfigDialogRef = ref(null)
const { visibleColumns, allColumns, loadColumnConfig, saveConfig } = useColumnConfig('finance_cost', FINANCE_COST_DEFAULT_COLUMNS)

// --- state ---
const loading = ref(false)
const tableData = ref([])
const total = ref(0)

const queryParams = reactive({
  workOrderNo: '',
  pageNum: 1,
  pageSize: 10
})

// --- load data ---
async function loadData() {
  loading.value = true
  try {
    const params = {
      workOrderNo: queryParams.workOrderNo || undefined,
      pageNum: queryParams.pageNum,
      pageSize: queryParams.pageSize
    }
    const res = await request.get('/finance/wo-costs/page', { params })
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } catch {
    // handled by interceptor
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  queryParams.pageNum = 1
  loadData()
}

function handleReset() {
  queryParams.workOrderNo = ''
  queryParams.pageNum = 1
  loadData()
}

async function handleExport() {
  try {
    const params = { workOrderNo: queryParams.workOrderNo || undefined }
    const res = await request.get('/finance/wo-costs/export', { params, responseType: 'blob' })
    const url = window.URL.createObjectURL(new Blob([res.data || res]))
    const a = document.createElement('a')
    a.href = url
    a.download = `财务成本_${new Date().toISOString().slice(0,10)}.xlsx`
    a.click()
    window.URL.revokeObjectURL(url)
  } catch { /* handled */ }
}

// --- detail dialog ---
const detailVisible = ref(false)
const detailData = ref(null)

async function handleDetail(row) {
  try {
    const res = await getWoCostDetail(row.id)
    detailData.value = res.data || null
    detailVisible.value = true
  } catch {
    // handled by interceptor
  }
}

// --- lifecycle ---
onMounted(() => {
  loadData()
  loadColumnConfig()
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
