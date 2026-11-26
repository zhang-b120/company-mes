<template>
  <div class="page-container">
    <!-- 统计卡片 -->
    <div class="stat-cards">
      <el-row :gutter="16">
        <el-col :span="6">
          <div class="stat-card" @click="handleCardClick('')">
            <el-icon class="icon" color="#409EFF"><Document /></el-icon>
            <div class="info">
              <div class="label">全部订单</div>
              <div class="value" style="color: #409EFF">{{ statistics.total }}</div>
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card" @click="handleCardClick('pending_approval')">
            <el-icon class="icon" color="#E6A23C"><Clock /></el-icon>
            <div class="info">
              <div class="label">待审核</div>
              <div class="value" style="color: #E6A23C">{{ statistics.pendingApproval }}</div>
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card" @click="handleCardClick('in_production')">
            <el-icon class="icon" color="#909399"><Loading /></el-icon>
            <div class="info">
              <div class="label">生产中</div>
              <div class="value" style="color: #909399">{{ statistics.inProduction }}</div>
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card" @click="handleCardClick('completed')">
            <el-icon class="icon" color="#67C23A"><CircleCheck /></el-icon>
            <div class="info">
              <div class="label">已完成</div>
              <div class="value" style="color: #67C23A">{{ statistics.completed }}</div>
            </div>
          </div>
        </el-col>
      </el-row>
    </div>

    <!-- 搜索区域 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="订单号">
          <el-input v-model="queryParams.orderNo" placeholder="订单号" clearable @keyup.enter="handleSearch" style="width: 150px" />
        </el-form-item>
        <el-form-item label="客户名称">
          <el-input v-model="queryParams.customerName" placeholder="客户名称" clearable @keyup.enter="handleSearch" style="width: 150px" />
        </el-form-item>
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
          <el-button type="primary" plain @click="handleAdd">新增</el-button>
          <el-button type="success" :icon="Download" @click="handleExport">导出</el-button>
          <el-button type="primary" plain style="background: #7c3aed; border-color: #7c3aed; color: #fff" @click="aiDialogVisible = true">
            <el-icon style="margin-right: 4px"><MagicStick /></el-icon>AI接单
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格区域 -->
    <el-card shadow="never" class="table-card">
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="orderNo" label="订单号" min-width="160" show-overflow-tooltip />
        <el-table-column prop="customerName" label="客户名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="totalAmount" label="总金额" min-width="120" align="right">
          <template #default="{ row }">
            {{ row.totalAmount != null ? `¥${Number(row.totalAmount).toFixed(2)}` : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" min-width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="deliveryDate" label="交期" min-width="120">
          <template #default="{ row }">{{ row.deliveryDate || '-' }}</template>
        </el-table-column>
        <el-table-column prop="salesmanName" label="业务员" min-width="100" />
        <el-table-column prop="createTime" label="创建时间" min-width="170">
          <template #default="{ row }">{{ $fmtDate(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">查看</el-button>
            <el-button link type="primary" v-if="row.status === 'draft'" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="success" v-if="row.status === 'draft'" @click="handleSubmit(row)">提交</el-button>
            <el-button link type="warning" v-if="row.status === 'pending_approval'" @click="handleApprove(row)">审批</el-button>
            <el-button link type="warning" v-if="row.status === 'confirmed' && row.masterPlanAdded !== 1" @click="handleToOutsource(row)">转外协</el-button>
            <el-button link type="danger" v-if="row.status !== 'cancelled' && row.status !== 'settled'" @click="handleCancel(row)">取消</el-button>
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
    <el-drawer v-model="detailVisible" title="订单详情" size="720px" destroy-on-close>
      <template v-if="detailData">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="订单号">{{ detailData.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="客户名称">{{ detailData.customerName }}</el-descriptions-item>
          <el-descriptions-item label="联系人">{{ detailData.contactName }}</el-descriptions-item>
          <el-descriptions-item label="收货地址">{{ detailData.shippingAddress }}</el-descriptions-item>
          <el-descriptions-item label="结算方式">{{ detailData.settlementType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="业务员">{{ detailData.salesmanName }}</el-descriptions-item>
          <el-descriptions-item label="总金额">
            {{ detailData.totalAmount != null ? `¥${Number(detailData.totalAmount).toFixed(2)}` : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTagType(detailData.status)">{{ statusLabel(detailData.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detailData.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
        <h4 style="margin: 20px 0 12px">订单明细</h4>
        <el-table :data="detailData.items || []" border stripe size="small">
          <el-table-column prop="productName" label="产品名称" min-width="140" />
          <el-table-column prop="quantity" label="数量" width="80" align="center" />
          <el-table-column prop="unitPrice" label="单价" width="100" align="right">
            <template #default="{ row }">{{ row.unitPrice != null ? `¥${Number(row.unitPrice).toFixed(2)}` : '-' }}</template>
          </el-table-column>
          <el-table-column prop="amount" label="金额" width="110" align="right">
            <template #default="{ row }">{{ row.amount != null ? `¥${Number(row.amount).toFixed(2)}` : '-' }}</template>
          </el-table-column>
          <el-table-column prop="deliveryDate" label="交货日期" width="120" />
          <el-table-column prop="producedQty" label="已生产数" width="90" align="center" />
          <el-table-column prop="shippedQty" label="已发货数" width="90" align="center" />
        </el-table>
      </template>
    </el-drawer>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="formVisible" :title="formData.id ? '编辑订单' : '新增订单'" width="900px" destroy-on-close>
      <el-form :model="formData" :rules="formRules" ref="formRef" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="客户" prop="customerId">
              <el-select v-model="formData.customerId" placeholder="请选择客户" filterable style="width: 100%">
                <el-option v-for="item in customerOptions" :key="item.id" :label="item.name" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系人" prop="contactName">
              <el-input v-model="formData.contactName" placeholder="请输入联系人" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="收货地址" prop="shippingAddress">
              <el-input v-model="formData.shippingAddress" placeholder="请输入收货地址" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结算方式" prop="settlementType">
              <el-select v-model="formData.settlementType" placeholder="请选择或输入新结算方式" filterable allow-create
                default-first-option style="width: 100%">
                <el-option v-for="t in settlementTypeOptions" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="业务员" prop="salesman">
              <el-input v-model="formData.salesman" placeholder="请输入业务员" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="备注">
              <el-input v-model="formData.remark" placeholder="请输入备注" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-divider content-position="left">订单明细</el-divider>
        <el-table :data="formData.items" border size="small">
          <el-table-column label="产品" min-width="180">
            <template #default="{ row }">
              <el-select v-model="row.productId" placeholder="请选择产品" filterable remote
                         :remote-method="searchProducts" :loading="productSearching"
                         size="small" style="width: 100%">
                <el-option v-for="p in productOptions" :key="p.id" :label="p.name" :value="p.id" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="数量" width="100">
            <template #default="{ row }">
              <el-input-number v-model="row.quantity" :min="1" size="small" controls-position="right" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="单价" width="120">
            <template #default="{ row }">
              <el-input-number v-model="row.unitPrice" :min="0" :precision="2" size="small" controls-position="right" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="交货日期" width="170">
            <template #default="{ row }">
              <el-date-picker v-model="row.deliveryDate" type="date" placeholder="选择日期" value-format="YYYY-MM-DD" size="small" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="70" align="center">
            <template #default="{ $index }">
              <el-button link type="danger" size="small" @click="formData.items.splice($index, 1)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-button type="primary" plain size="small" style="margin-top: 10px" @click="addItem">+ 添加明细</el-button>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleFormSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- AI接单对话框 -->
    <el-dialog v-model="aiDialogVisible" title="AI智能接单" width="750px" destroy-on-close>
      <el-alert type="info" :closable="false" style="margin-bottom: 16px">
        支持上传图纸(JPG/PNG/PDF)、Excel报价单、或直接粘贴文字描述，AI将自动解析出订单信息。
      </el-alert>
      <el-upload :auto-upload="false" multiple :limit="3"
        accept=".pdf,.doc,.docx,.xls,.xlsx,.jpg,.jpeg,.png"
        :on-change="onAiFileChange" :on-remove="onAiFileRemove" :file-list="aiFileList"
        style="margin-bottom: 12px">
        <el-button plain><el-icon style="margin-right:4px"><Upload /></el-icon>上传文件</el-button>
        <template #tip><div class="el-upload__tip">支持 PDF / Word / Excel / 图片，最多3个</div></template>
      </el-upload>
      <el-divider style="margin: 8px 0">或粘贴文字</el-divider>
      <el-input
        v-model="aiOrderText"
        type="textarea"
        :rows="5"
        placeholder="请粘贴客户的订单信息，例如：&#10;张总好，我们需要订购以下产品：&#10;1. A型号零件 500个，3月15日前交货&#10;2. B型号组件 200套，3月20日前交货"
      />
      <div style="margin-top: 12px; text-align: right">
        <el-button type="primary" :loading="aiParsing" :disabled="!aiOrderText.trim() && aiFileList.length === 0" style="background: #7c3aed; border-color: #7c3aed" @click="handleAiParse">
          <el-icon style="margin-right: 4px"><MagicStick /></el-icon>开始解析
        </el-button>
      </div>

      <!-- 解析结果预览 -->
      <template v-if="aiParsedResult">
        <el-divider content-position="left">解析结果</el-divider>
        <el-descriptions :column="2" border size="small" style="margin-bottom: 12px">
          <el-descriptions-item label="客户名称">{{ aiParsedResult.customer?.name || aiParsedResult.customerName || '未识别' }}</el-descriptions-item>
          <el-descriptions-item label="联系人">{{ aiParsedResult.contactName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="收货地址">{{ aiParsedResult.shippingAddress || '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注">{{ aiParsedResult.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="aiParsedResult.items || []" border stripe size="small">
          <el-table-column prop="productName" label="产品名称" min-width="160" />
          <el-table-column prop="qty" label="数量" width="100" align="center" />
          <el-table-column prop="deliveryDate" label="交货日期" width="130" />
        </el-table>
      </template>
      <template #footer>
        <el-button @click="aiDialogVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!aiParsedResult" style="background: #7c3aed; border-color: #7c3aed" @click="handleAiConfirm">确认创建</el-button>
      </template>
    </el-dialog>

    <!-- 审批对话框 -->
    <el-dialog v-model="approveVisible" title="订单审批" width="480px" destroy-on-close>
      <el-form :model="approveForm" label-width="80px">
        <el-form-item label="审批意见">
          <el-input v-model="approveForm.remark" type="textarea" :rows="3" placeholder="请输入审批意见" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approveVisible = false">取消</el-button>
        <el-button type="danger" :loading="approveLoading" @click="doReject">驳回</el-button>
        <el-button type="primary" :loading="approveLoading" @click="doApprove">通过</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MagicStick, Document, Clock, Loading, CircleCheck, Download, Upload } from '@element-plus/icons-vue'
import {
  getSalesOrderPage,
  getSalesOrderDetail,
  createSalesOrder,
  updateSalesOrder,
  submitSalesOrder,
  approveSalesOrder,
  rejectSalesOrder,
  cancelSalesOrder,
  getSalesOrderStatistics,
  exportSalesOrders
} from '@/api/order'
import { getCustomerSelect, getProductSelect, getMaterialPage } from '@/api/master'
import { getSettlementTypes, saveSettlementTypes } from '@/api/system'
import { aiParseOrder } from '@/api/ai'

const router = useRouter()

// ==================== 状态选项 ====================
const statusOptions = [
  { label: '草稿', value: 'draft' },
  { label: '待审批', value: 'pending_approval' },
  { label: '已确认', value: 'confirmed' },
  { label: '已转计划', value: 'mrp_planned' },
  { label: '外协中', value: 'outsourcing' },
  { label: '生产中', value: 'in_production' },
  { label: '部分发货', value: 'partial_shipped' },
  { label: '已发货', value: 'shipped' },
  { label: '已完成', value: 'completed' },
  { label: '已结算', value: 'settled' },
  { label: '已取消', value: 'cancelled' },
  { label: '已驳回', value: 'rejected' },
  { label: '已作废', value: 'voided' }
]
const statusMap = Object.fromEntries(statusOptions.map(s => [s.value, s.label]))

function statusLabel(status) {
  return statusMap[status] || status || '-'
}

function statusTagType(status) {
  const map = {
    draft: 'info',
    pending_approval: 'warning',
    confirmed: '',
    mrp_planned: 'primary',
    outsourcing: 'warning',
    in_production: '',
    partial_shipped: 'success',
    shipped: 'success',
    completed: 'success',
    settled: 'success',
    cancelled: 'danger',
    rejected: 'danger',
    voided: 'danger'
  }
  return map[status] || 'info'
}

// ==================== 统计卡片 ====================
const statistics = ref({
  total: 0,
  pendingApproval: 0,
  inProduction: 0,
  completed: 0
})

async function loadStatistics() {
  try {
    const res = await getSalesOrderStatistics()
    if (res.data) {
      statistics.value = {
        total: res.data.total || 0,
        pendingApproval: res.data.pendingApproval || 0,
        inProduction: res.data.inProduction || 0,
        completed: res.data.completed || 0
      }
    }
  } catch {
    // ignore
  }
}

function handleCardClick(status) {
  queryParams.status = status
  queryParams.pageNum = 1
  loadData()
}

// ==================== 查询与表格 ====================
const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const queryParams = reactive({
  orderNo: '',
  customerName: '',
  status: '',
  dateRange: null,
  pageNum: 1,
  pageSize: 20
})

async function loadData() {
  loading.value = true
  try {
    const params = {
      orderNo: queryParams.orderNo || undefined,
      customerName: queryParams.customerName || undefined,
      status: queryParams.status || undefined,
      startDate: queryParams.dateRange?.[0] || undefined,
      endDate: queryParams.dateRange?.[1] || undefined,
      pageNum: queryParams.pageNum,
      pageSize: queryParams.pageSize
    }
    const res = await getSalesOrderPage(params)
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
  queryParams.orderNo = ''
  queryParams.customerName = ''
  queryParams.status = ''
  queryParams.dateRange = null
  queryParams.pageNum = 1
  loadData()
}

// ==================== 导出 ====================
async function handleExport() {
  try {
    const res = await exportSalesOrders()
    const blob = new Blob([res], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = '订单列表.xlsx'
    link.click()
    window.URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (e) {
    ElMessage.error('导出失败')
  }
}

// ==================== 下拉选项 ====================
const customerOptions = ref([])
const productOptions = ref([])
const settlementTypeOptions = ref(['现结', '月结30天', '月结60天', '月结90天'])

async function loadSelectOptions() {
  try {
    const [cusRes, stRes] = await Promise.all([getCustomerSelect(), getSettlementTypes()])
    customerOptions.value = cusRes.data || []
    if (stRes.data?.configValue) settlementTypeOptions.value = JSON.parse(stRes.data.configValue)
  } catch {}
}

// 产品 remote 搜索
const productSearching = ref(false)
let _prodSearchTimer = null
async function searchProducts(keyword) {
  if (_prodSearchTimer) clearTimeout(_prodSearchTimer)
  if (!keyword || !keyword.trim()) { productOptions.value = []; return }
  _prodSearchTimer = setTimeout(async () => {
    productSearching.value = true
    try {
      const res = await getMaterialPage({ materialName: keyword.trim(), pageNum: 1, pageSize: 30 })
      const list = (res.data?.records || []).map(m => ({ id: m.id, name: m.materialName }))
      const map = new Map()
      productOptions.value.forEach(p => map.set(p.id, p))
      list.forEach(p => map.set(p.id, p))
      productOptions.value = Array.from(map.values())
    } catch {} finally { productSearching.value = false }
  }, 300)
}

// AI 匹配按需
async function loadProductsLazy() {
  if (productOptions.value.length > 0) return
  try {
    const res = await getProductSelect()
    productOptions.value = res.data || []
  } catch {}
}

// ==================== 详情 ====================
const detailVisible = ref(false)
const detailData = ref(null)

async function handleView(row) {
  try {
    const res = await getSalesOrderDetail(row.id)
    detailData.value = res.data
    detailVisible.value = true
  } catch {
    // error handled by interceptor
  }
}

// ==================== 新增/编辑 ====================
const formVisible = ref(false)
const submitLoading = ref(false)
const formRef = ref(null)
const formData = reactive({
  id: null,
  customerId: null,
  contactName: '',
  shippingAddress: '',
  settlementType: '',
  salesman: '',
  remark: '',
  items: []
})

const formRules = {
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  contactName: [{ required: true, message: '请输入联系人', trigger: 'blur' }],
  shippingAddress: [{ required: true, message: '请输入收货地址', trigger: 'blur' }],
  settlementType: [{ required: true, message: '请选择结算方式', trigger: 'change' }]
}

function resetForm() {
  formData.id = null
  formData.customerId = null
  formData.contactName = ''
  formData.shippingAddress = ''
  formData.settlementType = ''
  formData.salesman = ''
  formData.remark = ''
  formData.items = []
}

function handleAdd() {
  resetForm()
  formVisible.value = true
}

async function handleEdit(row) {
  try {
    const res = await getSalesOrderDetail(row.id)
    const d = res.data
    formData.id = d.id
    formData.customerId = d.customerId
    formData.contactName = d.contactName
    formData.shippingAddress = d.shippingAddress
    formData.settlementType = d.settlementType
    formData.salesman = d.salesman || d.salesmanName
    formData.remark = d.remark
    formData.items = (d.items || []).map(item => ({
      productId: item.productId,
      quantity: item.quantity,
      unitPrice: item.unitPrice,
      deliveryDate: item.deliveryDate
    }))
    formVisible.value = true
  } catch {
    // error handled by interceptor
  }
}

function addItem() {
  formData.items.push({ productId: null, quantity: 1, unitPrice: 0, deliveryDate: '' })
}

async function handleFormSubmit() {
  await formRef.value.validate()
  submitLoading.value = true
  try {
    const payload = {
      customerId: formData.customerId,
      contactName: formData.contactName,
      shippingAddress: formData.shippingAddress,
      settlementType: formData.settlementType,
      salesman: formData.salesman,
      remark: formData.remark,
      items: formData.items
    }
    if (formData.id) {
      await updateSalesOrder(formData.id, payload)
      ElMessage.success('更新成功')
    } else {
      await createSalesOrder(payload)
      ElMessage.success('创建成功')
    }
    formVisible.value = false
    loadData()
  } finally {
    submitLoading.value = false
  }
}

// ==================== 提交 ====================
async function handleSubmit(row) {
  await ElMessageBox.confirm('确认提交该订单？', '提示', { type: 'warning' })
  try {
    await submitSalesOrder(row.id)
    ElMessage.success('提交成功')
    loadData()
  } catch {
    // error handled by interceptor
  }
}

// ==================== 审批 ====================
const approveVisible = ref(false)
const approveLoading = ref(false)
const approveForm = reactive({ id: null, remark: '' })

function handleApprove(row) {
  approveForm.id = row.id
  approveForm.remark = ''
  approveVisible.value = true
}

async function doApprove() {
  approveLoading.value = true
  try {
    await approveSalesOrder(approveForm.id, approveForm.remark)
    ElMessage.success('审批通过')
    approveVisible.value = false
    loadData()
  } finally {
    approveLoading.value = false
  }
}

async function doReject() {
  if (!approveForm.remark) {
    ElMessage.warning('请输入驳回原因')
    return
  }
  approveLoading.value = true
  try {
    await rejectSalesOrder(approveForm.id, approveForm.remark)
    ElMessage.success('已驳回')
    approveVisible.value = false
    loadData()
  } finally {
    approveLoading.value = false
  }
}

// ==================== 取消 ====================
async function handleCancel(row) {
  const { value: reason } = await ElMessageBox.prompt('请输入取消原因', '取消订单', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputPattern: /\S+/,
    inputErrorMessage: '取消原因不能为空'
  })
  try {
    await cancelSalesOrder(row.id, reason)
    ElMessage.success('订单已取消')
    loadData()
  } catch {
    // error handled by interceptor
  }
}

// ==================== AI智能接单 ====================
const aiDialogVisible = ref(false)
const aiOrderText = ref('')
const aiParsing = ref(false)
const aiParsedResult = ref(null)
const aiFileList = ref([])
function onAiFileChange(file) { aiFileList.value.push(file) }
function onAiFileRemove(file) { aiFileList.value = aiFileList.value.filter(f => f.uid !== file.uid) }

async function handleAiParse() {
  if (!aiOrderText.value.trim() && aiFileList.value.length === 0) return
  aiParsing.value = true
  aiParsedResult.value = null
  try {
    const rawFiles = aiFileList.value.map(f => f.raw).filter(Boolean)
    const res = await aiParseOrder(aiOrderText.value, rawFiles)
    aiParsedResult.value = res.data
    ElMessage.success('解析完成，请核对信息')
  } catch {
    ElMessage.error('AI解析失败，请重试')
  } finally {
    aiParsing.value = false
  }
}

async function handleAiConfirm() {
  if (!aiParsedResult.value) return
  await loadProductsLazy()
  const parsed = aiParsedResult.value
  resetForm()
  // 尝试匹配客户 (后端返回 customer.name 嵌套结构, 兼容旧字段 customerName)
  const customerName = parsed.customer?.name || parsed.customerName
  if (customerName) {
    const matchedCustomer = customerOptions.value.find(c =>
      (c.name && c.name.includes(customerName)) ||
      (c.customerName && c.customerName.includes(customerName)))
    if (matchedCustomer) {
      formData.customerId = matchedCustomer.id
    }
  }
  formData.contactName = parsed.contactName || ''
  formData.shippingAddress = parsed.shippingAddress || ''
  formData.remark = parsed.remark || ''
  // 填入订单明细
  formData.items = (parsed.items || []).map(item => {
    const matchedProduct = productOptions.value.find(p => p.name === item.productName)
    return {
      productId: matchedProduct ? matchedProduct.id : null,
      quantity: item.qty || item.quantity || 1,
      unitPrice: item.unitPrice || 0,
      deliveryDate: item.deliveryDate || ''
    }
  })
  aiDialogVisible.value = false
  formVisible.value = true
  ElMessage.success('已填入订单信息，请补充并确认')
}

// ==================== 转外协 ====================
function handleToOutsource(row) {
  router.push({ path: '/order/detail', query: { id: row.id, action: 'outsource' } })
}

// ==================== 初始化 ====================
onMounted(() => {
  loadData()
  loadSelectOptions()
  loadStatistics()
})
</script>

<style scoped lang="scss">
.page-container {
  padding: 16px;
}
.stat-cards {
  margin-bottom: 16px;
}
.stat-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px 20px;
  display: flex;
  align-items: center;
  gap: 12px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  transition: box-shadow 0.3s;
  cursor: pointer;
}
.stat-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}
.stat-card .icon {
  font-size: 36px;
}
.stat-card .info .label {
  font-size: 13px;
  color: #909399;
}
.stat-card .info .value {
  font-size: 24px;
  font-weight: 600;
  margin-top: 4px;
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
</style>
