<template>
  <div class="page-container">
    <!-- 顶部 KPI 卡 -->
    <div class="kpi-grid">
      <div class="kpi-card kpi-blue">
        <div class="kpi-num">{{ totalOrders }}</div>
        <div class="kpi-lbl">售后总单数</div>
      </div>
      <div class="kpi-card kpi-green">
        <div class="kpi-num">{{ totalInWarranty }}</div>
        <div class="kpi-lbl">保内单数</div>
      </div>
      <div class="kpi-card kpi-orange">
        <div class="kpi-num">{{ totalOutWarranty }}</div>
        <div class="kpi-lbl">保外单数</div>
      </div>
      <div class="kpi-card kpi-purple">
        <div class="kpi-num">¥{{ totalCharge.toFixed(2) }}</div>
        <div class="kpi-lbl">保外总收费</div>
      </div>
    </div>

    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="客户">
          <el-input v-model="queryParams.customerName" placeholder="客户名称" clearable style="width: 200px" @keyup.enter="loadData" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column label="排名" type="index" width="60" align="center" />
        <el-table-column prop="customerName" label="客户名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="totalCount" label="售后单数" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small">{{ row.totalCount }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="inWarrantyCount" label="保内" width="80" align="center">
          <template #default="{ row }">
            <span style="color: #67c23a; font-weight: 600">{{ row.inWarrantyCount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="outWarrantyCount" label="保外" width="80" align="center">
          <template #default="{ row }">
            <span style="color: #e6a23c; font-weight: 600">{{ row.outWarrantyCount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="completedCount" label="已完成" width="80" align="center" />
        <el-table-column label="保外比例" width="120" align="center">
          <template #default="{ row }">
            <el-progress :percentage="row.totalCount ? Math.round(row.outWarrantyCount * 100 / row.totalCount) : 0"
                         :stroke-width="14" />
          </template>
        </el-table-column>
        <el-table-column label="配件成本" width="120" align="right">
          <template #default="{ row }">¥{{ Number(row.totalPartCost || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="保外收费" width="120" align="right">
          <template #default="{ row }">
            <span style="color: #e6a23c; font-weight: 600">¥{{ Number(row.totalCharge || 0).toFixed(2) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="最近售后时间" width="170">
          <template #default="{ row }">{{ $fmtDate(row.lastServiceTime) }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { getServiceCustomerStatistics } from '@/api/aftersale'

const loading = ref(false)
const tableData = ref([])
const queryParams = reactive({ customerName: '' })

async function loadData() {
  loading.value = true
  try {
    const res = await getServiceCustomerStatistics(queryParams)
    tableData.value = res.data || []
  } catch {} finally { loading.value = false }
}
function handleReset() {
  queryParams.customerName = ''
  loadData()
}

const totalOrders = computed(() => tableData.value.reduce((s, r) => s + Number(r.totalCount || 0), 0))
const totalInWarranty = computed(() => tableData.value.reduce((s, r) => s + Number(r.inWarrantyCount || 0), 0))
const totalOutWarranty = computed(() => tableData.value.reduce((s, r) => s + Number(r.outWarrantyCount || 0), 0))
const totalCharge = computed(() => tableData.value.reduce((s, r) => s + Number(r.totalCharge || 0), 0))

onMounted(loadData)
</script>

<style scoped lang="scss">
.page-container { display: flex; flex-direction: column; gap: 14px; }
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
}
.kpi-card {
  border-radius: 10px;
  padding: 16px 20px;
  color: #fff;
  box-shadow: 0 2px 10px rgba(0,0,0,.08);
  .kpi-num { font-size: 26px; font-weight: 700; line-height: 1.1; }
  .kpi-lbl { font-size: 13px; opacity: .85; margin-top: 6px; }
}
.kpi-blue { background: linear-gradient(135deg, #5b73e8 0%, #6e8bff 100%); }
.kpi-green { background: linear-gradient(135deg, #43a047 0%, #66bb6a 100%); }
.kpi-orange { background: linear-gradient(135deg, #f59e0b 0%, #fbbf24 100%); }
.kpi-purple { background: linear-gradient(135deg, #7c3aed 0%, #a78bfa 100%); }
.search-card :deep(.el-card__body) { padding-bottom: 2px; }
.table-card { flex: 1; }
</style>
