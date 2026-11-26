<template>
  <div>
    <el-tabs v-model="subTab" type="border-card">
      <el-tab-pane label="进项发票" name="input" />
      <el-tab-pane label="销项发票" name="output" />
    </el-tabs>

    <!-- 搜索 -->
    <el-form :model="queryParams" inline size="small" style="margin: 12px 0">
      <el-form-item label="发票号">
        <el-input v-model="queryParams.invoiceNo" placeholder="发票号" clearable style="width: 150px" @keyup.enter="handleSearch" />
      </el-form-item>
      <el-form-item label="单位名称">
        <el-input v-model="queryParams.unitName" placeholder="购/销方名称" clearable style="width: 150px" @keyup.enter="handleSearch" />
      </el-form-item>
      <el-form-item label="关联单据号">
        <el-input v-model="queryParams.relatedNo" placeholder="关联单据号" clearable style="width: 150px" @keyup.enter="handleSearch" />
      </el-form-item>
      <el-form-item label="关联类型">
        <el-select v-model="queryParams.relatedType" placeholder="全部" clearable style="width: 140px">
          <el-option v-if="subTab === 'output'" label="销售订单" value="sales_order" />
          <el-option v-if="subTab === 'input'" label="采购订单" value="purchase_order" />
          <el-option v-if="subTab === 'input'" label="外协订单" value="outsource_order" />
        </el-select>
      </el-form-item>
      <el-form-item label="开票日期">
        <el-date-picker v-model="queryParams.dateRange" type="daterange" range-separator="至"
          start-placeholder="开始" end-placeholder="结束" value-format="YYYY-MM-DD" style="width: 240px" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 100px">
          <el-option label="草稿" value="draft" />
          <el-option label="已确认" value="confirmed" />
          <el-option label="已作废" value="cancelled" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">搜索</el-button>
        <el-button @click="handleReset">重置</el-button>
        <el-button type="success" @click="openCreateDialog">新增发票</el-button>
        <el-button type="warning" :loading="exportLoading" @click="handleExport">导出明细</el-button>
      </el-form-item>
    </el-form>

    <!-- 表格 -->
    <el-table :data="tableData" v-loading="loading" border stripe size="small">
      <el-table-column prop="invoiceNo" label="系统编号" width="140" show-overflow-tooltip />
      <el-table-column prop="invoiceNumber" label="发票号码" width="130" show-overflow-tooltip />
      <el-table-column prop="invoiceCode" label="发票代码" width="120" show-overflow-tooltip />
      <el-table-column prop="invoiceDate" label="开票日期" width="110" />
      <el-table-column :label="subTab === 'output' ? '购方' : '销方'" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ subTab === 'output' ? row.buyerName : row.sellerName }}</template>
      </el-table-column>
      <el-table-column label="关联单据" min-width="130" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.relatedNo">{{ relatedTypeText[row.relatedType] || '' }} {{ row.relatedNo }}</span>
          <span v-else class="text-muted">-</span>
        </template>
      </el-table-column>
      <el-table-column prop="amount" label="金额(不含税)" width="120" align="right">
        <template #default="{ row }">¥{{ formatMoney(row.amount) }}</template>
      </el-table-column>
      <el-table-column prop="taxAmount" label="税额" width="100" align="right">
        <template #default="{ row }">¥{{ formatMoney(row.taxAmount) }}</template>
      </el-table-column>
      <el-table-column prop="totalAmount" label="价税合计" width="120" align="right">
        <template #default="{ row }"><span style="font-weight: 600; color: #e6a23c">¥{{ formatMoney(row.totalAmount) }}</span></template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="statusTagMap[row.status]" size="small">{{ statusTextMap[row.status] || row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="附件" width="70" align="center">
        <template #default="{ row }">
          <el-button v-if="row.attachmentUrl" type="primary" link size="small" @click="previewAttachment(row.attachmentUrl)">查看</el-button>
          <span v-else class="text-muted">-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" align="center" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="openFinanceInvoiceDetail(row)">查看</el-button>
          <el-button type="info" link size="small" @click="openEditDialog(row)">编辑</el-button>
          <el-button v-if="row.status === 'draft'" type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="pagination-wrapper">
      <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
        :total="total" :page-sizes="[10, 20, 50]" background layout="total, sizes, prev, pager, next"
        @size-change="loadData" @current-change="loadData" />
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="formVisible" :title="editingId ? '编辑发票' : '新增发票'" width="700px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="发票类型">
              <el-radio-group v-model="form.invoiceType" :disabled="!!editingId">
                <el-radio value="input">进项发票</el-radio>
                <el-radio value="output">销项发票</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="开票日期" prop="invoiceDate">
              <el-date-picker v-model="form.invoiceDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="发票代码">
              <el-input v-model="form.invoiceCode" placeholder="发票代码" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="发票号码" prop="invoiceNumber">
              <el-input v-model="form.invoiceNumber" placeholder="发票号码" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="购方名称">
              <el-input v-model="form.buyerName" placeholder="购方名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="购方税号">
              <el-input v-model="form.buyerTaxNo" placeholder="购方税号" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="销方名称">
              <el-input v-model="form.sellerName" placeholder="销方名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="销方税号">
              <el-input v-model="form.sellerTaxNo" placeholder="销方税号" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="金额(不含税)" prop="amount">
              <el-input-number v-model="form.amount" :min="0" :precision="2" controls-position="right" style="width: 100%" @change="calcTotal" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="税率(%)">
              <el-input-number v-model="form.taxRate" :min="0" :max="100" :precision="2" controls-position="right" style="width: 100%" @change="calcTax" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="税额">
              <el-input-number v-model="form.taxAmount" :min="0" :precision="2" controls-position="right" style="width: 100%" @change="calcTotal" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="价税合计">
          <el-input :model-value="'¥ ' + formatMoney((form.amount || 0) + (form.taxAmount || 0))" disabled style="font-weight: 600; color: #e6a23c" />
        </el-form-item>
        <el-divider content-position="left">关联单据</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="关联类型">
              <el-select v-model="form.relatedType" placeholder="请选择" clearable style="width: 100%">
                <el-option v-if="form.invoiceType === 'output'" label="销售订单" value="sales_order" />
                <el-option v-if="form.invoiceType === 'input'" label="采购订单" value="purchase_order" />
                <el-option v-if="form.invoiceType === 'input'" label="外协订单" value="outsource_order" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="关联单号">
              <el-input v-model="form.relatedNo" placeholder="输入关联单号" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="发票附件">
          <el-upload :action="uploadUrl" :headers="uploadHeaders"
            :show-file-list="false" :on-success="onUploadSuccess" accept="image/*,.pdf">
            <el-button type="primary" plain size="small">上传发票</el-button>
          </el-upload>
          <div v-if="form.attachmentUrl" style="margin-top: 8px">
            <el-button type="primary" link size="small" @click="previewAttachment(form.attachmentUrl)">预览附件</el-button>
            <el-button type="danger" link size="small" @click="form.attachmentUrl = ''">删除</el-button>
          </div>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="form.status" style="width: 100%">
                <el-option label="草稿" value="draft" />
                <el-option label="已确认" value="confirmed" />
                <el-option label="已作废" value="cancelled" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="form.remark" placeholder="备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 发票详情抽屉 -->
    <el-drawer v-model="detailDrawerVisible" :title="`发票详情 ${detailData.invoiceNo || ''}`" size="760px">
      <template v-if="detailData.id">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="发票号">{{ detailData.invoiceNo }}</el-descriptions-item>
          <el-descriptions-item label="发票类型">{{ detailData.invoiceType === 'input' ? '进项' : '销项' }}</el-descriptions-item>
          <el-descriptions-item label="发票代码">{{ detailData.invoiceCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="发票号码">{{ detailData.invoiceNumber || '-' }}</el-descriptions-item>
          <el-descriptions-item label="开票日期">{{ detailData.invoiceDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="税率">{{ detailData.taxRate != null ? `${detailData.taxRate}%` : '-' }}</el-descriptions-item>
          <el-descriptions-item label="不含税金额">¥{{ formatMoney(detailData.amount) }}</el-descriptions-item>
          <el-descriptions-item label="税额">¥{{ formatMoney(detailData.taxAmount) }}</el-descriptions-item>
          <el-descriptions-item label="价税合计" :span="2">
            <span style="color:#e6a23c; font-weight:600; font-size:16px">¥{{ formatMoney(detailData.totalAmount) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="购方名称">{{ detailData.buyerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="购方税号">{{ detailData.buyerTaxNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="销方名称">{{ detailData.sellerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="销方税号">{{ detailData.sellerTaxNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="关联单据" :span="2">
            {{ relatedTypeMap[detailData.relatedType] || detailData.relatedType || '-' }}
            <span v-if="detailData.relatedNo" style="margin-left:6px; color:#409eff">{{ detailData.relatedNo }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="状态" :span="2">
            <el-tag :type="statusTagMap[detailData.status]" size="small">{{ statusTextMap[detailData.status] || detailData.status }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="附件" :span="2">
            <el-button v-if="detailData.attachmentUrl" type="primary" link size="small" @click="previewAttachment(detailData.attachmentUrl)">查看附件</el-button>
            <span v-else style="color:#c0c4cc">-</span>
          </el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detailData.remark || '-' }}</el-descriptions-item>
        </el-descriptions>

        <!-- 产品明细表 -->
        <div v-if="detailItems.length" style="margin: 16px 0 8px; font-weight: 600">产品明细</div>
        <el-table v-if="detailItems.length" :data="detailItems" border stripe size="small">
          <el-table-column type="index" label="#" width="50" align="center" />
          <el-table-column prop="productName" label="产品" min-width="140" show-overflow-tooltip />
          <el-table-column label="规格" min-width="100" show-overflow-tooltip>
            <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
          </el-table-column>
          <el-table-column prop="quantity" label="数量" width="80" align="right" />
          <el-table-column prop="unitPrice" label="含税单价" width="100" align="right">
            <template #default="{ row }">¥{{ Number(row.unitPrice || 0).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="不含税金额" width="105" align="right">
            <template #default="{ row }">¥{{ Number(row.amountExTax || 0).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="税额" width="90" align="right">
            <template #default="{ row }">¥{{ Number(row.taxAmount || 0).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="含税金额" width="100" align="right">
            <template #default="{ row }">
              <span style="color:#e6a23c;font-weight:500">¥{{ Number(row.totalAmount || 0).toFixed(2) }}</span>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-drawer>

    <!-- 发票图片预览 -->
    <el-image-viewer v-if="previewVisible" :url-list="previewUrls" @close="previewVisible = false" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getInvoicePage, exportInvoices, createInvoice, updateInvoice, deleteInvoice } from '@/api/finance'
import { isImage } from '@/utils/attachment'

const subTab = ref('input')
const formatMoney = (v) => v != null ? Number(v).toFixed(2) : '0.00'

const uploadUrl = (import.meta.env.VITE_API_BASE || '/api') + '/common/upload'
const uploadHeaders = computed(() => ({ Authorization: 'Bearer ' + (localStorage?.getItem('token') || '') }))

const relatedTypeText = { sales_order: '销售订单', purchase_order: '采购订单', outsource_order: '外协订单' }
const relatedTypeMap = relatedTypeText
const statusTextMap = { draft: '草稿', confirmed: '已确认', cancelled: '已作废' }
const statusTagMap = { draft: 'info', confirmed: 'success', cancelled: 'danger' }

// 详情抽屉
const detailDrawerVisible = ref(false)
const detailData = ref({})
const detailItems = computed(() => {
  const raw = detailData.value.itemsJson
  if (!raw) return []
  try { return JSON.parse(raw) || [] } catch { return [] }
})
function openFinanceInvoiceDetail(row) {
  detailData.value = row
  detailDrawerVisible.value = true
}

// ===== 查询 =====
const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const queryParams = reactive({ invoiceNo: '', unitName: '', relatedType: '', relatedNo: '', dateRange: null, status: '', pageNum: 1, pageSize: 20 })
const exportLoading = ref(false)

watch(subTab, () => { queryParams.relatedType = ''; queryParams.pageNum = 1; loadData() })

function buildQueryPayload() {
  const unitNameParam = queryParams.unitName || undefined
  return {
    invoiceType: subTab.value,
    invoiceNo: queryParams.invoiceNo || undefined,
    buyerName: subTab.value === 'output' ? unitNameParam : undefined,
    sellerName: subTab.value === 'input' ? unitNameParam : undefined,
    relatedType: queryParams.relatedType || undefined,
    relatedNo: queryParams.relatedNo || undefined,
    dateStart: queryParams.dateRange?.[0] || undefined,
    dateEnd: queryParams.dateRange?.[1] || undefined,
    status: queryParams.status || undefined
  }
}

async function loadData() {
  loading.value = true
  try {
    const res = await getInvoicePage({
      ...buildQueryPayload(),
      pageNum: queryParams.pageNum,
      pageSize: queryParams.pageSize
    })
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch {} finally { loading.value = false }
}

function handleSearch() { queryParams.pageNum = 1; loadData() }
function handleReset() {
  queryParams.invoiceNo = ''; queryParams.unitName = ''
  queryParams.relatedType = ''; queryParams.relatedNo = ''
  queryParams.dateRange = null; queryParams.status = ''
  queryParams.pageNum = 1; loadData()
}

async function handleExport() {
  exportLoading.value = true
  try {
    const blob = await exportInvoices(buildQueryPayload())
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    const now = new Date()
    const tag = `${now.getFullYear()}${String(now.getMonth()+1).padStart(2,'0')}${String(now.getDate()).padStart(2,'0')}`
    a.download = `${subTab.value === 'output' ? '销项' : '进项'}发票明细_${tag}.xlsx`
    a.click()
    window.URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch {
    ElMessage.error('导出失败')
  } finally { exportLoading.value = false }
}

// ===== 新增/编辑 =====
const formVisible = ref(false)
const submitLoading = ref(false)
const editingId = ref(null)
const formRef = ref(null)
const form = reactive({
  invoiceType: 'input', invoiceCode: '', invoiceNumber: '', invoiceDate: '',
  amount: 0, taxRate: 13, taxAmount: 0,
  buyerName: '', buyerTaxNo: '', sellerName: '', sellerTaxNo: '',
  relatedType: '', relatedNo: '', relatedId: null,
  customerId: null, supplierId: null,
  attachmentUrl: '', status: 'draft', remark: ''
})
const formRules = {
  invoiceDate: [{ required: true, message: '请选择开票日期', trigger: 'change' }],
  invoiceNumber: [{ required: true, message: '请输入发票号码', trigger: 'blur' }],
  amount: [{ required: true, message: '请输入金额', trigger: 'blur' }]
}

function calcTax() {
  if (form.amount && form.taxRate) {
    form.taxAmount = Math.round(form.amount * form.taxRate) / 100
  }
}
function calcTotal() { /* 显示用 computed 已处理 */ }

function openCreateDialog() {
  editingId.value = null
  Object.assign(form, {
    invoiceType: subTab.value, invoiceCode: '', invoiceNumber: '', invoiceDate: new Date().toISOString().slice(0, 10),
    amount: 0, taxRate: 13, taxAmount: 0,
    buyerName: '', buyerTaxNo: '', sellerName: '', sellerTaxNo: '',
    relatedType: '', relatedNo: '', relatedId: null,
    customerId: null, supplierId: null,
    attachmentUrl: '', status: 'draft', remark: ''
  })
  formVisible.value = true
}

function openEditDialog(row) {
  editingId.value = row.id
  Object.keys(form).forEach(k => { if (row[k] !== undefined) form[k] = row[k] })
  formVisible.value = true
}

async function handleSubmit() {
  await formRef.value.validate()
  submitLoading.value = true
  try {
    const data = { ...form }
    data.totalAmount = (data.amount || 0) + (data.taxAmount || 0)
    if (editingId.value) {
      await updateInvoice(editingId.value, data)
      ElMessage.success('更新成功')
    } else {
      await createInvoice(data)
      ElMessage.success('新增成功')
    }
    formVisible.value = false
    loadData()
  } catch {} finally { submitLoading.value = false }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确定删除发票「${row.invoiceNumber || row.invoiceNo}」？`, '提示', { type: 'warning' })
  try { await deleteInvoice(row.id); ElMessage.success('删除成功'); loadData() } catch {}
}

function onUploadSuccess(res) {
  if (res.code === 200 || res.code === 0) {
    form.attachmentUrl = res.data?.url || res.data || ''
    ElMessage.success('上传成功')
  } else {
    ElMessage.error(res.msg || '上传失败')
  }
}

const previewVisible = ref(false)
const previewUrls = ref([])
function previewAttachment(url) {
  if (!url) return
  if (isImage(url)) {
    previewUrls.value = [url]
    previewVisible.value = true
  } else {
    window.open(url, '_blank')
  }
}

onMounted(() => { loadData() })
</script>

<style scoped>
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; }
.text-muted { color: #c0c4cc; font-size: 12px; }
</style>
