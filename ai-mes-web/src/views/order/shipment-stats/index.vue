<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="客户名称">
          <el-input v-model="queryParams.customerName" placeholder="客户名称" clearable style="width:150px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="物料编码">
          <el-input v-model="queryParams.materialNo" placeholder="物料编码" clearable style="width:150px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="物料名称">
          <el-input v-model="queryParams.materialName" placeholder="物料名称" clearable style="width:150px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="发货时间">
          <el-date-picker v-model="dateRange" type="daterange" range-separator="-" start-placeholder="开始" end-placeholder="结束"
            value-format="YYYY-MM-DD" style="width:240px" @change="handleSearch" />
        </el-form-item>
        <el-form-item label="申请人">
          <el-input v-model="queryParams.applicantName" placeholder="申请人" clearable style="width:120px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button plain @click="handleExport">导出</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据表格 -->
    <el-card shadow="never" class="table-card">
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="historyNo" label="历史单号" width="165" show-overflow-tooltip />
        <el-table-column prop="relatedOrderNo" label="关联单据号" min-width="175" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button v-if="row.relatedOrderNo" link type="primary" style="padding:0"
              @click="openRelatedOrder(row)">{{ row.relatedOrderNo }}</el-button>
            <span v-else style="color:#c0c4cc">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="customerName" label="客户名称" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.customerName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="materialNo" label="物料编码" width="115" show-overflow-tooltip />
        <el-table-column prop="materialName" label="物料名称" min-width="120" show-overflow-tooltip />
        <el-table-column label="规格" width="90" show-overflow-tooltip>
          <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="55" align="center" />
        <el-table-column prop="quantity" label="数量" width="80" align="right" />
        <el-table-column prop="secondaryUnit" label="辅助单位" width="70" align="center">
          <template #default="{ row }">{{ row.secondaryUnit || '-' }}</template>
        </el-table-column>
        <el-table-column prop="secondaryQty" label="辅助数量" width="95" align="right">
          <template #default="{ row }">{{ row.secondaryQty || '-' }}</template>
        </el-table-column>
        <el-table-column prop="warehouseName" label="仓库" width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row.warehouseName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="applicantName" label="申请人" width="100">
          <template #default="{ row }">{{ row.applicantName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="operatorName" label="操作人" width="100">
          <template #default="{ row }">{{ row.operatorName || '-' }}</template>
        </el-table-column>
        <el-table-column label="发货时间" min-width="165">
          <template #default="{ row }">{{ $fmtDate(row.createTime) }}</template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
          :page-sizes="[10,20,50,100]" :total="total" layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <!-- 关联单据详情弹窗 -->
    <PurchaseOrderDetailDialog v-model:visible="relatedOrderVisible" :relatedOrderNo="relatedOrderNo" :invType="relatedInvType" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getShipmentStatisticsPage, exportShipmentStatistics } from '@/api/order'
import PurchaseOrderDetailDialog from '@/components/PurchaseOrderDetailDialog.vue'

// ==================== 查询 ====================
const queryParams = reactive({
  customerName: '', materialNo: '', materialName: '', applicantName: '',
  dateStart: '', dateEnd: '', pageNum: 1, pageSize: 20
})
const dateRange = ref([])
const loading = ref(false)
const tableData = ref([])
const total = ref(0)

async function loadData() {
  loading.value = true
  try {
    const params = { ...queryParams, dateStart: dateRange.value?.[0] || '', dateEnd: dateRange.value?.[1] || '' }
    const res = await getShipmentStatisticsPage(params)
    tableData.value = res.data?.records || res.data?.list || []
    total.value = res.data?.total || 0
  } finally { loading.value = false }
}

function handleSearch() { queryParams.pageNum = 1; loadData() }

function handleReset() {
  Object.assign(queryParams, { customerName: '', materialNo: '', materialName: '', applicantName: '', pageNum: 1 })
  dateRange.value = []
  loadData()
}

// ==================== 关联单据详情 ====================
const relatedOrderVisible = ref(false)
const relatedOrderNo = ref('')
const relatedInvType = ref('')

function openRelatedOrder(row) {
  relatedOrderNo.value = row.relatedOrderNo || ''
  relatedInvType.value = 'sales_out'
  relatedOrderVisible.value = true
}

async function handleExport() {
  try {
    const params = { ...queryParams, dateStart: dateRange.value?.[0] || '', dateEnd: dateRange.value?.[1] || '' }
    const res = await exportShipmentStatistics(params)
    const blob = new Blob([res], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `发货统计_${new Date().toISOString().slice(0, 10)}.xlsx`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch { ElMessage.error('导出失败') }
}

onMounted(() => { loadData() })
</script>

<style scoped lang="scss">
.page-container { padding: 16px; }
.search-card { margin-bottom: 16px; }
.table-card {
  .pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; }
}
</style>
