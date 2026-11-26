<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable>
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期范围">
          <el-date-picker
            v-model="queryParams.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格区域 -->
    <el-card shadow="never" class="table-card">
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="reportNo" label="报工单号" min-width="160" show-overflow-tooltip />
        <el-table-column prop="workOrderNo" label="工单号" min-width="150" show-overflow-tooltip />
        <el-table-column prop="stepName" label="工序名称" min-width="120" />
        <el-table-column prop="reporterName" label="报工人" width="100" />
        <el-table-column prop="reportType" label="报工类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.reportType === 'piece' ? '' : 'warning'">
              {{ reportTypeLabel(row.reportType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="qualifiedQty" label="合格数" width="80" align="center" />
        <el-table-column prop="defectQty" label="不良数" width="80" align="center">
          <template #default="{ row }">
            <span :style="{ color: row.defectQty > 0 ? '#F56C6C' : '' }">{{ row.defectQty }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="pieceWage" label="计件工资" width="100" align="right">
          <template #default="{ row }">
            {{ row.pieceWage != null ? `¥${Number(row.pieceWage).toFixed(2)}` : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" min-width="170">
          <template #default="{ row }">{{ $fmtDate(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">查看</el-button>
            <template v-if="row.status === 'submitted'">
              <el-button link type="success" @click="handleApprove(row)">审批</el-button>
              <el-button link type="danger" @click="handleReject(row)">驳回</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" title="报工详情" size="600px" destroy-on-close>
      <template v-if="detailData">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="报工单号">{{ detailData.reportNo }}</el-descriptions-item>
          <el-descriptions-item label="工单号">{{ detailData.workOrderNo }}</el-descriptions-item>
          <el-descriptions-item label="工序名称">{{ detailData.stepName }}</el-descriptions-item>
          <el-descriptions-item label="报工人">{{ detailData.reporterName }}</el-descriptions-item>
          <el-descriptions-item label="报工类型">{{ reportTypeLabel(detailData.reportType) }}</el-descriptions-item>
          <el-descriptions-item label="合格数量">{{ detailData.qualifiedQty }}</el-descriptions-item>
          <el-descriptions-item label="不良数量">
            <span :style="{ color: detailData.defectQty > 0 ? '#F56C6C' : '' }">{{ detailData.defectQty }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="计件工资">
            {{ detailData.pieceWage != null ? `¥${Number(detailData.pieceWage).toFixed(2)}` : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTagType(detailData.status)">{{ statusLabel(detailData.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detailData.createTime }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detailData.remark || '-' }}</el-descriptions-item>
        </el-descriptions>

        <!-- 不良原因 -->
        <template v-if="detailData.defectReasons && detailData.defectReasons.length">
          <h4 style="margin: 20px 0 12px">不良原因</h4>
          <el-table :data="detailData.defectReasons" border stripe size="small">
            <el-table-column prop="reason" label="原因描述" min-width="200" />
            <el-table-column prop="qty" label="数量" width="80" align="center" />
            <el-table-column prop="category" label="分类" width="120" />
          </el-table>
        </template>

        <!-- 照片 -->
        <template v-if="detailData.photos && detailData.photos.length">
          <h4 style="margin: 20px 0 12px">现场照片</h4>
          <div class="photo-list">
            <el-image
              v-for="(url, index) in detailData.photos"
              :key="index"
              :src="url"
              :preview-src-list="detailData.photos"
              :initial-index="index"
              fit="cover"
              class="photo-item"
            />
          </div>
        </template>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getWorkReportPage, approveWorkReport, rejectWorkReport } from '@/api/workreport'

// ==================== 状态选项 ====================
const statusOptions = [
  { label: '已提交', value: 'submitted' },
  { label: '待审批', value: 'pending_approval' },
  { label: '已通过', value: 'approved' },
  { label: '已驳回', value: 'rejected' }
]
const statusMap = Object.fromEntries(statusOptions.map(s => [s.value, s.label]))

function statusLabel(status) {
  return statusMap[status] || status || '-'
}

function statusTagType(status) {
  const map = {
    submitted: 'warning',
    pending_approval: 'warning',
    approved: 'success',
    rejected: 'danger'
  }
  return map[status] || 'info'
}

function reportTypeLabel(type) {
  const map = { piece: '计件', time: '计时', batch: '批次' }
  return map[type] || type || '-'
}

// ==================== 查询与表格 ====================
const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const queryParams = reactive({
  status: '',
  dateRange: null,
  pageNum: 1,
  pageSize: 20
})

async function loadData() {
  loading.value = true
  try {
    const params = {
      status: queryParams.status || undefined,
      startDate: queryParams.dateRange?.[0] || undefined,
      endDate: queryParams.dateRange?.[1] || undefined,
      pageNum: queryParams.pageNum,
      pageSize: queryParams.pageSize
    }
    const res = await getWorkReportPage(params)
    tableData.value = res.data?.records || res.data?.list || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  queryParams.pageNum = 1
  loadData()
}

function handleReset() {
  queryParams.status = ''
  queryParams.dateRange = null
  queryParams.pageNum = 1
  loadData()
}

// ==================== 详情 ====================
const detailVisible = ref(false)
const detailData = ref(null)

function handleView(row) {
  detailData.value = row
  detailVisible.value = true
}

// ==================== 审批/驳回 ====================
async function handleApprove(row) {
  await ElMessageBox.confirm('确认通过该报工单？', '审批确认', { type: 'warning' })
  try {
    await approveWorkReport(row.id)
    ElMessage.success('审批通过')
    loadData()
  } catch {
    // error handled by interceptor
  }
}

async function handleReject(row) {
  await ElMessageBox.confirm('确认驳回该报工单？', '驳回确认', { type: 'warning' })
  try {
    await rejectWorkReport(row.id)
    ElMessage.success('已驳回')
    loadData()
  } catch {
    // error handled by interceptor
  }
}

// ==================== 初始化 ====================
onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.page-container {
  padding: 16px;
}
.search-card {
  margin-bottom: 16px;
}
.table-card {
  .pagination-wrapper {
    display: flex;
    justify-content: flex-end;
    margin-top: 16px;
  }
}
.photo-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  .photo-item {
    width: 120px;
    height: 120px;
    border-radius: 4px;
    border: 1px solid #eee;
  }
}
</style>
