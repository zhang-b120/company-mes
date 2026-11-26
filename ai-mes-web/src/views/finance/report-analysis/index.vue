<template>
  <div class="page-container">
    <el-card shadow="never" style="margin-bottom: 12px">
      <el-form :model="queryParams" inline>
        <el-form-item label="报工时间">
          <el-date-picker v-model="dateRange" type="daterange" range-separator="至"
            start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD"
            style="width: 260px" />
        </el-form-item>
        <el-form-item label="报工人">
          <el-input v-model="queryParams.employeeName" placeholder="请输入" clearable style="width: 130px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="物料名称">
          <el-input v-model="queryParams.materialName" placeholder="请输入" clearable style="width: 150px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="工序名称">
          <el-input v-model="queryParams.processName" placeholder="请输入" clearable style="width: 130px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="success" :icon="Download" :loading="exportLoading" @click="handleExport">导出</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 合计统计 -->
    <el-row :gutter="16" style="margin-bottom: 12px">
      <el-col :span="8">
        <div class="stat-card">
          <div class="stat-label">记录条数</div>
          <div class="stat-value" style="color:#409eff">{{ summary.totalCount || 0 }}</div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="stat-card">
          <div class="stat-label">合格总数量</div>
          <div class="stat-value" style="color:#67c23a">{{ Math.round(Number(summary.totalQty || 0)) }}</div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="stat-card">
          <div class="stat-label">工资总额</div>
          <div class="stat-value" style="color:#e6a23c">¥{{ Number(summary.totalAmount || 0).toFixed(2) }}</div>
        </div>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <el-table :data="tableData" v-loading="loading" border stripe size="small" show-summary :summary-method="getSummary">
        <el-table-column prop="reporterName" label="报工人" width="100" />
        <el-table-column prop="reportTime" label="报工时间" width="160" />
        <el-table-column prop="woNo" label="生产单号" min-width="140" show-overflow-tooltip />
        <el-table-column prop="materialNo" label="物料编号" width="130" show-overflow-tooltip />
        <el-table-column prop="materialName" label="物料名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="processName" label="工序名称" width="130" show-overflow-tooltip />
        <el-table-column prop="piecePrice" label="单价" width="100" align="right">
          <template #default="{ row }">
            {{ row.piecePrice != null ? Number(row.piecePrice).toFixed(2) : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="qualifiedQty" label="合格数量" width="90" align="right">
          <template #default="{ row }">
            {{ Math.round(Number(row.qualifiedQty || 0)) }}
          </template>
        </el-table-column>
        <el-table-column label="报工模式" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.wageMode === 'three_machine'" type="warning" size="small">三机</el-tag>
            <el-tag v-else type="info" size="small">正常</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="工资倍率" width="90" align="right">
          <template #default="{ row }">
            {{ row.wageRatio != null ? Number(row.wageRatio).toFixed(2) : '1.00' }}
          </template>
        </el-table-column>
        <el-table-column prop="amount" label="金额" width="100" align="right">
          <template #default="{ row }">
            <span style="color:#e6a23c;font-weight:500">¥{{ Number(row.amount || 0).toFixed(2) }}</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[20, 50, 100, 200]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import { getReportAnalysisList, getReportAnalysisSummary, exportReportAnalysis } from '@/api/finance'

const loading = ref(false)
const exportLoading = ref(false)
const tableData = ref([])
const total = ref(0)
const summary = ref({ totalCount: 0, totalQty: 0, totalAmount: 0 })
const dateRange = ref([])
const queryParams = reactive({
  employeeName: '',
  processName: '',
  materialName: '',
  pageNum: 1,
  pageSize: 20
})

function buildParams(extra = {}) {
  return {
    startDate: dateRange.value?.[0] || undefined,
    endDate: dateRange.value?.[1] || undefined,
    employeeName: queryParams.employeeName || undefined,
    processName: queryParams.processName || undefined,
    materialName: queryParams.materialName || undefined,
    ...extra
  }
}

async function loadData() {
  loading.value = true
  try {
    const res = await getReportAnalysisList(buildParams({
      pageNum: queryParams.pageNum,
      pageSize: queryParams.pageSize
    }))
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch {} finally { loading.value = false }
}

async function loadSummary() {
  try {
    const res = await getReportAnalysisSummary(buildParams())
    summary.value = res.data || { totalCount: 0, totalQty: 0, totalAmount: 0 }
  } catch {}
}

function handleSearch() { queryParams.pageNum = 1; loadData(); loadSummary() }
function handleReset() {
  dateRange.value = []
  queryParams.employeeName = ''
  queryParams.processName = ''
  queryParams.materialName = ''
  queryParams.pageNum = 1
  loadData(); loadSummary()
}

async function handleExport() {
  exportLoading.value = true
  try {
    const blob = await exportReportAnalysis(buildParams())
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    const now = new Date()
    const fileName = `员工报工分析_${now.getFullYear()}${String(now.getMonth()+1).padStart(2,'0')}${String(now.getDate()).padStart(2,'0')}.xlsx`
    a.download = fileName
    a.click()
    window.URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch {
    ElMessage.error('导出失败')
  } finally { exportLoading.value = false }
}

// 表格底部合计行 - 当前页合计
function getSummary({ columns, data }) {
  const sums = []
  columns.forEach((col, idx) => {
    if (idx === 0) { sums[idx] = '本页合计'; return }
    if (col.property === 'qualifiedQty') {
      const s = data.reduce((acc, r) => acc + Number(r.qualifiedQty || 0), 0)
      sums[idx] = String(Math.round(s))
    } else if (col.property === 'amount') {
      const s = data.reduce((acc, r) => acc + Number(r.amount || 0), 0)
      sums[idx] = `¥${s.toFixed(2)}`
    } else {
      sums[idx] = ''
    }
  })
  return sums
}

onMounted(() => {
  const now = new Date()
  const y = now.getFullYear()
  const m = String(now.getMonth() + 1).padStart(2, '0')
  dateRange.value = [`${y}-${m}-01`, `${y}-${m}-${new Date(y, now.getMonth() + 1, 0).getDate()}`]
  loadData()
  loadSummary()
})
</script>

<style scoped>
.page-container { padding: 16px; }
.pagination-wrapper { margin-top: 12px; display: flex; justify-content: flex-end; }
.stat-card {
  background: #fff; border-radius: 8px; padding: 16px 20px;
  box-shadow: 0 1px 4px rgba(0,0,0,.06);
}
.stat-label { font-size: 13px; color: #909399; }
.stat-value { font-size: 24px; font-weight: 700; margin-top: 4px; }
</style>
