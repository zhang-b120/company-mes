<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="报工单号">
          <el-input v-model="queryParams.reportNo" placeholder="报工单号模糊查询" clearable style="width:160px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="工单号">
          <el-input v-model="queryParams.woNo" placeholder="工单号模糊查询" clearable style="width:160px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="工序名称">
          <el-input v-model="queryParams.processName" placeholder="工序名称" clearable style="width:160px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="报工人">
          <el-input v-model="queryParams.reporterName" placeholder="报工人" clearable style="width:140px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width:120px">
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
            style="width:240px"
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
        <el-table-column label="工单号" min-width="150">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleViewWorkOrder(row)">{{ row.workOrderNo || '-' }}</el-button>
          </template>
        </el-table-column>
        <el-table-column prop="stepName" label="工序名称" min-width="120" show-overflow-tooltip />
        <el-table-column prop="employeeName" label="报工人" min-width="100" />
        <el-table-column label="报工类型" min-width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.reportType === 'team' ? 'warning' : ''">
              {{ reportTypeLabel(row.reportType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="qualifiedQty" label="合格数" min-width="80" align="center" />
        <el-table-column label="不良数" min-width="80" align="center">
          <template #default="{ row }">
            <span :style="{ color: row.defectQty > 0 ? '#F56C6C' : '' }">{{ row.defectQty }}</span>
          </template>
        </el-table-column>
        <el-table-column label="计件工资" min-width="100" align="right">
          <template #default="{ row }">{{ row.pieceWage != null ? `¥${Number(row.pieceWage).toFixed(2)}` : '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" min-width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">查看</el-button>
            <el-button v-if="row.status === 'submitted'" link type="danger" @click="handleReject(row)">驳回</el-button>
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
    <el-drawer v-model="detailVisible" title="报工详情" size="680px" destroy-on-close>
      <div v-loading="detailLoading" v-if="detailData">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="报工单号">{{ detailData.reportNo }}</el-descriptions-item>
          <el-descriptions-item label="工单号">
            <el-button link type="primary" @click="handleViewWorkOrder(detailData)">{{ detailData.workOrderNo }}</el-button>
          </el-descriptions-item>
          <el-descriptions-item label="工序名称">{{ detailData.stepName }}</el-descriptions-item>
          <el-descriptions-item label="报工人">{{ detailData.employeeName }}</el-descriptions-item>
          <el-descriptions-item label="报工类型">
            <el-tag size="small" :type="detailData.reportType === 'team' ? 'warning' : ''">
              {{ reportTypeLabel(detailData.reportType) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTagType(detailData.status)">{{ statusLabel(detailData.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="合格数量">{{ detailData.qualifiedQty }}</el-descriptions-item>
          <el-descriptions-item label="不良数量">
            <span :style="{ color: detailData.defectQty > 0 ? '#F56C6C' : '' }">{{ detailData.defectQty }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="计件工资">
            {{ detailData.pieceWage != null ? `¥${Number(detailData.pieceWage).toFixed(2)}` : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDateTime(detailData.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detailData.remark || '-' }}</el-descriptions-item>
          <el-descriptions-item v-if="detailData.status === 'rejected'" label="驳回原因" :span="2">
            <span style="color:#F56C6C">{{ detailData.rejectReason || '-' }}</span>
          </el-descriptions-item>
        </el-descriptions>

        <!-- 物料清单 -->
        <template v-if="detailData.bomItems && detailData.bomItems.length">
          <h4 style="margin: 20px 0 10px">物料清单</h4>
          <el-table :data="detailData.bomItems" border size="small">
            <el-table-column prop="materialNo" label="物料编码" width="110" show-overflow-tooltip />
            <el-table-column prop="materialName" label="物料名称" min-width="130" show-overflow-tooltip />
            <el-table-column prop="spec" label="规格" width="100" show-overflow-tooltip />
            <el-table-column prop="unit" label="单位" width="60" align="center" />
            <el-table-column prop="requiredQty" label="需求数" width="80" align="center" />
            <el-table-column prop="issuedQty" label="已领数" width="80" align="center">
              <template #default="{ row }">
                <span :style="(row.issuedQty||0) > 0 ? 'color:#67C23A;font-weight:600' : 'color:#c0c4cc'">
                  {{ row.issuedQty ?? 0 }}
                </span>
              </template>
            </el-table-column>
          </el-table>
        </template>

        <!-- 人员明细（个人报工 + 班组报工均展示） -->
        <template v-if="detailData.teamMembers && detailData.teamMembers.length">
          <h4 style="margin: 20px 0 10px">
            {{ detailData.reportType === 'team' ? '班组成员明细' : '人员明细' }}
            <span v-if="detailData.totalWage" style="font-weight:normal;font-size:13px;color:#606266;margin-left:12px">
              总工资：¥{{ Number(detailData.totalWage).toFixed(2) }}
            </span>
          </h4>
          <el-table :data="detailData.teamMembers" border size="small">
            <el-table-column prop="userName" label="成员姓名" min-width="100" />
            <el-table-column prop="weight" label="权重" width="70" align="center" />
            <el-table-column label="合格数量" width="90" align="right">
              <template #default="{ row }">{{ row.qualifiedQty ?? 0 }}</template>
            </el-table-column>
            <el-table-column label="不合格数量" width="100" align="right">
              <template #default="{ row }">
                <span :style="(row.defectQty || 0) > 0 ? 'color:#F56C6C' : ''">{{ row.defectQty ?? 0 }}</span>
              </template>
            </el-table-column>
            <el-table-column label="分配工资" width="100" align="right">
              <template #default="{ row }">¥{{ Number(row.allocatedWage || 0).toFixed(2) }}</template>
            </el-table-column>
          </el-table>
        </template>

        <!-- 不良原因 -->
        <template v-if="detailData.defectReasons && detailData.defectReasons.length">
          <h4 style="margin: 20px 0 10px">不良原因</h4>
          <el-table :data="detailData.defectReasons" border size="small">
            <el-table-column prop="reason" label="原因描述" min-width="200" />
            <el-table-column prop="qty" label="数量" width="80" align="center" />
            <el-table-column prop="category" label="分类" width="120" />
          </el-table>
        </template>

        <!-- 照片 -->
        <template v-if="detailData.photos && detailData.photos.length">
          <h4 style="margin: 20px 0 10px">现场照片</h4>
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
      </div>
    </el-drawer>

    <!-- 驳回对话框 -->
    <el-dialog v-model="rejectVisible" title="驳回报工" width="440px" destroy-on-close>
      <el-form ref="rejectFormRef" :model="rejectForm" label-width="80px">
        <el-form-item label="驳回原因" prop="reason" :rules="[{ required: true, message: '请输入驳回原因' }]">
          <el-input v-model="rejectForm.reason" type="textarea" :rows="3" placeholder="请输入驳回原因" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="rejectSubmitting" @click="submitReject">确认驳回</el-button>
      </template>
    </el-dialog>

    <!-- 工单详情对话框 -->
    <el-dialog v-model="woDialogVisible" title="工单详情" width="600px" destroy-on-close>
      <div v-loading="woDialogLoading">
        <el-descriptions v-if="woDetail" :column="2" border size="small">
          <el-descriptions-item label="工单号">{{ woDetail.woNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ woStatusLabel(woDetail.status) }}</el-descriptions-item>
          <el-descriptions-item label="产品名称">{{ woDetail.productName }}</el-descriptions-item>
          <el-descriptions-item label="计划数量">{{ woDetail.planQty }}</el-descriptions-item>
          <el-descriptions-item label="完工数量">{{ woDetail.completedQty ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="交货日期">{{ woDetail.deliveryDate ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="销售订单号">{{ woDetail.salesOrderNo ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注">{{ woDetail.remark ?? '-' }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getWorkReportPage, rejectWorkReport, getWorkReportFullDetail } from '@/api/workreport'
import { getWorkOrderDetail } from '@/api/production'
import { formatDateTime } from '@/utils/format'

// ==================== 状态选项 ====================
const statusOptions = [
  { label: '已报工', value: 'submitted' },
  { label: '已驳回', value: 'rejected' }
]

function statusLabel(status) {
  const map = { submitted: '已报工', rejected: '已驳回' }
  return map[status] || status || '-'
}

function statusTagType(status) {
  return status === 'rejected' ? 'danger' : 'success'
}

function reportTypeLabel(type) {
  const map = { personal: '个人报工', team: '班组报工' }
  return map[type] || type || '-'
}

function woStatusLabel(status) {
  const map = {
    pending: '待排产', scheduled: '已排产', in_progress: '生产中',
    finished: '已完工', completed: '已完工', cancelled: '已取消'
  }
  return map[status] || status || '-'
}

// ==================== 查询与表格 ====================
const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const queryParams = reactive({
  reportNo: '',
  woNo: '',
  processName: '',
  reporterName: '',
  status: 'submitted',
  dateRange: null,
  pageNum: 1,
  pageSize: 20
})

async function loadData() {
  loading.value = true
  try {
    const params = {
      keyword: queryParams.reportNo || undefined,
      woNo: queryParams.woNo || undefined,
      processName: queryParams.processName || undefined,
      reporterName: queryParams.reporterName || undefined,
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
  queryParams.reportNo = ''
  queryParams.woNo = ''
  queryParams.processName = ''
  queryParams.reporterName = ''
  queryParams.status = 'submitted'
  queryParams.dateRange = null
  queryParams.pageNum = 1
  loadData()
}

// ==================== 详情 ====================
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailData = ref(null)

async function handleView(row) {
  detailVisible.value = true
  detailLoading.value = true
  detailData.value = null
  try {
    const res = await getWorkReportFullDetail(row.id)
    const data = res.data || {}
    // 解析 defectReasons JSON
    if (data.defectReasons && typeof data.defectReasons === 'string') {
      try { data.defectReasons = JSON.parse(data.defectReasons) } catch { data.defectReasons = [] }
    }
    // 解析 photoUrls JSON
    if (data.photoUrls && typeof data.photoUrls === 'string') {
      try { data.photos = JSON.parse(data.photoUrls) } catch { data.photos = [] }
    } else {
      data.photos = Array.isArray(data.photoUrls) ? data.photoUrls : []
    }
    detailData.value = data
  } catch {
    detailData.value = row
  } finally {
    detailLoading.value = false
  }
}

// ==================== 驳回 ====================
const rejectVisible = ref(false)
const rejectSubmitting = ref(false)
const rejectFormRef = ref(null)
const rejectForm = reactive({ reason: '', targetRow: null })

function handleReject(row) {
  rejectForm.reason = ''
  rejectForm.targetRow = row
  rejectVisible.value = true
}

async function submitReject() {
  await rejectFormRef.value.validate()
  rejectSubmitting.value = true
  try {
    await rejectWorkReport(rejectForm.targetRow.id, rejectForm.reason)
    ElMessage.success('已驳回')
    rejectVisible.value = false
    loadData()
  } finally {
    rejectSubmitting.value = false
  }
}

// ==================== 工单详情弹窗 ====================
const woDialogVisible = ref(false)
const woDialogLoading = ref(false)
const woDetail = ref(null)

async function handleViewWorkOrder(row) {
  if (!row.workOrderId) return
  woDialogVisible.value = true
  woDialogLoading.value = true
  woDetail.value = null
  try {
    const res = await getWorkOrderDetail(row.workOrderId)
    woDetail.value = res.data || {}
  } catch {
    woDialogVisible.value = false
  } finally {
    woDialogLoading.value = false
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
