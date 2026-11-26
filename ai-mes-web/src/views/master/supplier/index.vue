<template>
  <div class="page-container">
    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-form :model="queryParams" inline>
        <el-form-item label="供应商名称">
          <el-input v-model="queryParams.supplierName" placeholder="供应商名称" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="供应商编号">
          <el-input v-model="queryParams.supplierNo" placeholder="供应商编号" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="类别">
          <el-tree-select
            v-model="queryParams.supplierCategoryId"
            :data="categoryTree"
            :props="{ value: 'id', label: 'categoryName', children: 'children' }"
            check-strictly node-key="id" placeholder="全部" clearable
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable>
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="primary" plain @click="handleAdd">新增供应商</el-button>
          <el-button plain @click="importDialogVisible = true">导入</el-button>
          <el-button plain @click="handleExport">导出</el-button>
          <el-button :icon="Setting" @click="colConfigDialogRef.open()">列配置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 数据表格 -->
    <el-table :data="tableData" v-loading="loading" border stripe size="small">
      <el-table-column
        v-for="col in visibleColumns" :key="col.key"
        :label="col.label"
        :min-width="col.minWidth || 100"
        :width="col.width"
        :align="col.align || 'left'"
        show-overflow-tooltip>
        <template #default="{ row }">
          <template v-if="col.key === 'supplierName'">
            <el-button type="primary" link @click="openDetail(row)">{{ row.supplierName }}</el-button>
          </template>
          <template v-else-if="col.key === 'supplierCategoryId'">
            <span v-if="row.supplierCategoryId">{{ getCategoryName(row.supplierCategoryId) }}</span>
            <span v-else style="color:#c0c4cc">-</span>
          </template>
          <template v-else-if="col.key === 'settlementType'">{{ row.settlementType || '-' }}</template>
          <template v-else-if="col.key === 'score'">
            <template v-if="row.score">
              <el-rate :model-value="row.score" disabled :max="5" size="small" style="display: inline-flex" />
            </template>
            <span v-else style="color: #c0c4cc">未评分</span>
          </template>
          <template v-else-if="col.key === 'processingAbility'">{{ row.processingAbility || '-' }}</template>
          <template v-else-if="col.key === 'status'">
            <el-switch :model-value="row.status === 'active'" size="small"
              @change="(val) => toggleStatus(row, val)" />
          </template>
          <template v-else>{{ row[col.key] || '-' }}</template>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right" align="center">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button type="warning" link size="small" @click="handleScore(row)">评分</el-button>
          <el-button type="info" link size="small" @click="openDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination-wrapper">
      <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
        :page-sizes="[10, 20, 50, 100]" :total="total"
        layout="total, sizes, prev, pager, next, jumper" background
        @size-change="loadData" @current-change="loadData" />
    </div>

    <!-- 供应商详情抽屉 -->
    <el-drawer v-model="detailVisible" :title="detailData?.supplierName || '供应商详情'" size="650px">
      <template v-if="detailData">
        <el-tabs v-model="detailTab">
          <!-- 基本信息 -->
          <el-tab-pane label="基本信息" name="basic">
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item label="供应商编号">{{ detailData.supplierNo }}</el-descriptions-item>
              <el-descriptions-item label="供应商名称">{{ detailData.supplierName }}</el-descriptions-item>
              <el-descriptions-item label="类别">
                <span v-if="detailData.supplierCategoryId">{{ getCategoryName(detailData.supplierCategoryId) }}</span>
                <span v-else style="color:#c0c4cc">-</span>
              </el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag :type="detailData.status === 1 ? 'success' : 'danger'" size="small">
                  {{ detailData.status === 1 ? '启用' : '禁用' }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="联系人">{{ detailData.contactName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="电话">{{ detailData.phone || '-' }}</el-descriptions-item>
              <el-descriptions-item label="地址" :span="2">{{ detailData.address || '-' }}</el-descriptions-item>
              <el-descriptions-item label="结算方式">{{ detailData.settlementType || '-' }}</el-descriptions-item>
              <el-descriptions-item label="评分">
                <el-rate v-if="detailData.score" :model-value="detailData.score" disabled :max="5" size="small" />
                <span v-else style="color: #c0c4cc">未评分</span>
              </el-descriptions-item>
              <el-descriptions-item label="创建时间">{{ detailData.createTime || '-' }}</el-descriptions-item>
              <el-descriptions-item label="备注" :span="2">{{ detailData.remark || '-' }}</el-descriptions-item>
            </el-descriptions>
          </el-tab-pane>

          <!-- 银行信息 -->
          <el-tab-pane label="银行信息" name="bank">
            <el-descriptions :column="1" border size="small">
              <el-descriptions-item label="开户银行">{{ detailData.bankName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="银行账号">{{ detailData.bankAccount || '-' }}</el-descriptions-item>
              <el-descriptions-item label="税号">{{ detailData.taxNo || '-' }}</el-descriptions-item>
              <el-descriptions-item label="发票类型">{{ detailData.invoiceType || '-' }}</el-descriptions-item>
            </el-descriptions>
          </el-tab-pane>

          <!-- 加工能力 -->
          <el-tab-pane label="加工能力" name="ability">
            <div v-if="detailData.processingAbility" style="white-space: pre-wrap; line-height: 1.8; color: #606266">
              {{ detailData.processingAbility }}
            </div>
            <el-empty v-else description="暂无加工能力描述" />

            <el-divider content-position="left">供应物料</el-divider>
            <el-table :data="supplyMaterials" size="small" border stripe>
              <el-table-column prop="materialName" label="物料名称" />
              <el-table-column label="规格">
                <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
              </el-table-column>
              <el-table-column prop="unitPrice" label="单价" width="100" align="right">
                <template #default="{ row }">{{ row.unitPrice ? '¥' + row.unitPrice : '-' }}</template>
              </el-table-column>
              <el-table-column prop="leadTime" label="交货周期(天)" width="110" align="center" />
            </el-table>
          </el-tab-pane>

          <!-- 往来记录 -->
          <el-tab-pane label="往来记录" name="transactions">
            <el-descriptions :column="3" border size="small" style="margin-bottom: 16px">
              <el-descriptions-item label="采购总额">
                <span style="color: #e6a23c; font-weight: 600">¥{{ txStats.totalPurchase?.toFixed(2) || '0.00' }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="未结金额">
                <span style="color: #f56c6c; font-weight: 600">¥{{ txStats.unpaid?.toFixed(2) || '0.00' }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="订单数">{{ txStats.orderCount || 0 }}</el-descriptions-item>
            </el-descriptions>

            <el-table :data="txList" size="small" border stripe v-loading="txLoading">
              <el-table-column prop="orderNo" label="单据号" min-width="140" show-overflow-tooltip />
              <el-table-column prop="type" label="类型" width="90">
                <template #default="{ row }">
                  <el-tag :type="row.type === 'purchase' ? '' : 'warning'" size="small">
                    {{ row.type === 'purchase' ? '采购单' : '外协单' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="totalAmount" label="金额" width="100" align="right">
                <template #default="{ row }">¥{{ Number(row.totalAmount || 0).toFixed(2) }}</template>
              </el-table-column>
              <el-table-column prop="status" label="状态" width="80" align="center">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.status === 'completed' ? 'success' : 'info'">
                    {{ txStatusMap[row.status] || row.status }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="createTime" label="时间" min-width="155" />
            </el-table>
          </el-tab-pane>
        </el-tabs>

        <div style="margin-top: 16px; display: flex; gap: 8px">
          <el-button type="primary" @click="handleEdit(detailData); detailVisible = false">编辑</el-button>
          <el-button type="warning" @click="handleScore(detailData); detailVisible = false">评分</el-button>
        </div>
      </template>
    </el-drawer>

    <!-- 新增/编辑弹窗 -->
    <el-dialog :title="dialogTitle" v-model="dialogVisible" width="700px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-divider content-position="left">基本信息</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="供应商名称" prop="supplierName">
              <el-input v-model="form.supplierName" placeholder="请输入供应商名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="供应商类别" prop="supplierCategoryId">
              <el-tree-select
                v-model="form.supplierCategoryId"
                :data="categoryTree"
                :props="{ value: 'id', label: 'categoryName', children: 'children' }"
                check-strictly node-key="id" placeholder="请选择类别" clearable
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="联系人" prop="contactName">
              <el-input v-model="form.contactName" placeholder="请输入联系人" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="电话" prop="phone">
              <el-input v-model="form.phone" placeholder="请输入电话" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="地址" prop="address">
          <el-input v-model="form.address" placeholder="请输入地址" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="结算方式" prop="settlementType">
              <el-select v-model="form.settlementType" placeholder="请选择或输入新结算方式" filterable allow-create
                default-first-option style="width: 100%">
                <el-option v-for="t in settlementTypeOptions" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">银行信息</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="开户银行" prop="bankName">
              <el-input v-model="form.bankName" placeholder="请输入开户银行" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="银行账号" prop="bankAccount">
              <el-input v-model="form.bankAccount" placeholder="请输入银行账号" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="税号">
              <el-input v-model="form.taxNo" placeholder="请输入税号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="发票类型">
              <el-select v-model="form.invoiceType" placeholder="请选择" style="width: 100%" clearable>
                <el-option label="增值税专用发票" value="special" />
                <el-option label="增值税普通发票" value="normal" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">加工能力</el-divider>
        <el-form-item label="加工能力" prop="processingAbility">
          <el-input v-model="form.processingAbility" type="textarea" :rows="3" placeholder="请输入加工能力描述，如：CNC加工、钣金、焊接等" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <ColumnConfigDialog
      ref="colConfigDialogRef"
      :all-columns="allColumns"
      :default-columns="MASTER_SUPPLIER_DEFAULT_COLUMNS"
      @save="saveConfig"
    />

    <!-- 评分弹窗 -->
    <el-dialog title="供应商评分" v-model="scoreDialogVisible" width="480px" destroy-on-close>
      <div style="padding: 20px 0;">
        <p style="margin-bottom: 16px; color: #606266; text-align: center">
          供应商：<strong>{{ scoringSupplier?.supplierName }}</strong>
        </p>
        <div style="text-align: center; margin-bottom: 16px">
          <el-rate v-model="scoreValue" :max="5" :texts="['很差', '较差', '一般', '较好', '很好']" show-text size="large" />
        </div>
        <el-divider content-position="left">评分维度（可选）</el-divider>
        <el-form label-width="100px" size="small">
          <el-form-item label="质量">
            <el-rate v-model="scoreDimensions.quality" :max="5" />
          </el-form-item>
          <el-form-item label="交期">
            <el-rate v-model="scoreDimensions.delivery" :max="5" />
          </el-form-item>
          <el-form-item label="价格">
            <el-rate v-model="scoreDimensions.price" :max="5" />
          </el-form-item>
          <el-form-item label="服务">
            <el-rate v-model="scoreDimensions.service" :max="5" />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="scoreDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="scoreLoading" @click="handleSubmitScore">确定</el-button>
      </template>
    </el-dialog>

    <!-- 导入弹窗 -->
    <el-dialog v-model="importDialogVisible" title="导入供应商" width="520px">
      <el-alert type="info" :closable="false" style="margin-bottom: 12px">请先下载模板，按模板格式填写后上传。</el-alert>
      <div style="margin-bottom: 16px;">
        <el-button type="primary" link @click="handleDownloadTemplate">下载导入模板</el-button>
      </div>
      <el-upload ref="importUploadRef" :auto-upload="false" :limit="1" accept=".xlsx,.xls" :on-change="handleImportFileChange" drag>
        <el-icon :size="40" color="#c0c4cc"><Upload /></el-icon>
        <div style="margin-top: 8px; color: #909399">将文件拖到此处，或点击上传</div>
      </el-upload>
      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="importLoading" @click="handleImport">确认导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Setting, Upload } from '@element-plus/icons-vue'
import { useColumnConfig } from '@/composables/useColumnConfig'
import ColumnConfigDialog from '@/components/ColumnConfigDialog.vue'
import { getSupplierPage, getSupplierDetail, createSupplier, updateSupplier, updateSupplierScore, exportSuppliers, importSuppliers, downloadSupplierTemplate, getSupplierCategoryTree } from '@/api/master'
import { getSettlementTypes, saveSettlementTypes } from '@/api/system'
import { getPurchaseOrderPage } from '@/api/purchase'
import { getOutsourceOrderPage } from '@/api/outsource'

const MASTER_SUPPLIER_DEFAULT_COLUMNS = [
  { key: 'supplierNo', label: '供应商编号', minWidth: 120 },
  { key: 'supplierName', label: '供应商名称', minWidth: 160 },
  { key: 'supplierCategoryId', label: '类别', width: 130 },
  { key: 'contactName', label: '联系人', width: 100 },
  { key: 'phone', label: '电话', width: 130 },
  { key: 'settlementType', label: '结算方式', width: 110 },
  { key: 'score', label: '评分', width: 140, align: 'center' },
  { key: 'processingAbility', label: '加工能力', minWidth: 140 },
  { key: 'status', label: '状态', width: 80, align: 'center' },
]
const colConfigDialogRef = ref(null)
const { visibleColumns, allColumns, loadColumnConfig, saveConfig } = useColumnConfig('master_supplier', MASTER_SUPPLIER_DEFAULT_COLUMNS)

// 供应商类别树（替代旧的供应商类型枚举）
const categoryTree = ref([])
const categoryFlatMap = ref({})
function flattenCategories(list) {
  const map = {}
  const walk = nodes => nodes?.forEach(n => { map[n.id] = n.categoryName; if (n.children?.length) walk(n.children) })
  walk(list)
  return map
}
function getCategoryName(id) { return categoryFlatMap.value[id] || '' }
async function loadCategoryTree() {
  try {
    const res = await getSupplierCategoryTree()
    categoryTree.value = res.data || []
    categoryFlatMap.value = flattenCategories(categoryTree.value)
  } catch {}
}

const settlementTypeOptions = ref(['现结', '月结30天', '月结60天', '月结90天'])
async function loadSettlementTypes() {
  try {
    const res = await getSettlementTypes()
    if (res.data?.configValue) settlementTypeOptions.value = JSON.parse(res.data.configValue)
  } catch {}
}
async function addSettlementType(val) {
  if (val && !settlementTypeOptions.value.includes(val)) {
    settlementTypeOptions.value.push(val)
    try { await saveSettlementTypes(settlementTypeOptions.value) } catch {}
  }
}
const txStatusMap = { draft: '草稿', pending: '待确认', confirmed: '已确认', completed: '已完成', cancelled: '已取消' }

// 查询
const queryParams = reactive({ supplierName: '', supplierNo: '', supplierCategoryId: undefined, status: undefined, pageNum: 1, pageSize: 10 })
const loading = ref(false); const tableData = ref([]); const total = ref(0)

async function loadData() {
  loading.value = true
  try {
    const res = await getSupplierPage(queryParams)
    tableData.value = res.data?.records || []; total.value = res.data?.total || 0
  } catch {} finally { loading.value = false }
}

function handleSearch() { queryParams.pageNum = 1; loadData() }
function handleReset() {
  queryParams.supplierName = ''; queryParams.supplierNo = ''; queryParams.supplierCategoryId = undefined; queryParams.status = undefined
  queryParams.pageNum = 1; loadData()
}

// 状态切换
async function toggleStatus(row, val) {
  const newStatus = val ? 'active' : 'disabled'
  const label = val ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(`确认${label}供应商「${row.supplierName}」？`, '提示', { type: 'warning' })
    await updateSupplier(row.id, { ...row, status: newStatus })
    ElMessage.success(`${label}成功`); loadData()
  } catch {}
}

// 详情抽屉
const detailVisible = ref(false); const detailData = ref(null); const detailTab = ref('basic')
const supplyMaterials = ref([]); const txList = ref([]); const txLoading = ref(false)
const txStats = reactive({ totalPurchase: 0, unpaid: 0, orderCount: 0 })

async function openDetail(row) {
  detailTab.value = 'basic'
  try {
    const res = await getSupplierDetail(row.id)
    detailData.value = res.data || row
  } catch { detailData.value = row }
  supplyMaterials.value = detailData.value?.supplyMaterials || []
  detailVisible.value = true
  loadTransactions(row.id)
}

async function loadTransactions(supplierId) {
  txLoading.value = true
  try {
    const [poRes, osRes] = await Promise.all([
      getPurchaseOrderPage({ supplierId, pageNum: 1, pageSize: 20 }).catch(() => ({ data: { records: [] } })),
      getOutsourceOrderPage({ supplierId, pageNum: 1, pageSize: 20 }).catch(() => ({ data: { records: [] } }))
    ])
    const poList = (poRes.data?.records || []).map(r => ({ ...r, type: 'purchase' }))
    const osList = (osRes.data?.records || []).map(r => ({ ...r, type: 'outsource' }))
    txList.value = [...poList, ...osList].sort((a, b) => (b.createTime || '').localeCompare(a.createTime || ''))
    txStats.orderCount = txList.value.length
    txStats.totalPurchase = txList.value.reduce((s, r) => s + Number(r.totalAmount || 0), 0)
    txStats.unpaid = txList.value.filter(r => r.status !== 'completed' && r.status !== 'cancelled')
      .reduce((s, r) => s + Number(r.totalAmount || 0), 0)
  } catch {} finally { txLoading.value = false }
}

// 编辑弹窗
const dialogVisible = ref(false); const dialogTitle = ref('新增供应商')
const formRef = ref(null); const submitLoading = ref(false); const editingId = ref(null)

const getDefaultForm = () => ({
  supplierName: '', supplierCategoryId: undefined, contactName: '', phone: '', address: '',
  settlementType: undefined, bankName: '', bankAccount: '', taxNo: '', invoiceType: '',
  processingAbility: '', remark: ''
})
const form = reactive(getDefaultForm())
const rules = {
  supplierName: [{ required: true, message: '请输入供应商名称', trigger: 'blur' }],
  supplierCategoryId: [{ required: true, message: '请选择供应商类别', trigger: 'change' }],
  contactName: [{ required: true, message: '请输入联系人', trigger: 'blur' }],
  phone: [{ required: true, message: '请输入电话', trigger: 'blur' }]
}

function resetForm() {
  const defaults = getDefaultForm()
  Object.keys(defaults).forEach(key => { form[key] = defaults[key] })
}
function handleAdd() { resetForm(); editingId.value = null; dialogTitle.value = '新增供应商'; dialogVisible.value = true }
function handleEdit(row) {
  resetForm(); editingId.value = row.id; dialogTitle.value = '编辑供应商'
  Object.keys(form).forEach(key => { if (row[key] !== undefined) form[key] = row[key] })
  dialogVisible.value = true
}

async function handleSubmit() {
  await formRef.value.validate()
  submitLoading.value = true
  try {
    if (editingId.value) {
      await updateSupplier(editingId.value, { ...form }); ElMessage.success('更新成功')
    } else {
      await createSupplier({ ...form }); ElMessage.success('新增成功')
    }
    if (form.settlementType) addSettlementType(form.settlementType)
    dialogVisible.value = false; loadData()
  } finally { submitLoading.value = false }
}

// 评分
const scoreDialogVisible = ref(false); const scoringSupplier = ref(null)
const scoreValue = ref(0); const scoreLoading = ref(false)
const scoreDimensions = reactive({ quality: 0, delivery: 0, price: 0, service: 0 })

function handleScore(row) {
  scoringSupplier.value = row; scoreValue.value = row.score || 0
  scoreDimensions.quality = 0; scoreDimensions.delivery = 0
  scoreDimensions.price = 0; scoreDimensions.service = 0
  scoreDialogVisible.value = true
}

async function handleSubmitScore() {
  if (!scoreValue.value) { ElMessage.warning('请选择评分'); return }
  scoreLoading.value = true
  try {
    await updateSupplierScore(scoringSupplier.value.id, scoreValue.value)
    ElMessage.success('评分成功'); scoreDialogVisible.value = false; loadData()
  } finally { scoreLoading.value = false }
}

// ==================== 导入导出 ====================
const importDialogVisible = ref(false)
const importLoading = ref(false)
const importUploadRef = ref(null)
const importFile = ref(null)

function handleImportFileChange(file) { importFile.value = file.raw }

async function handleDownloadTemplate() {
  try { const res = await downloadSupplierTemplate(); downloadBlob(res, '供应商导入模板.xlsx') } catch {}
}

async function handleImport() {
  if (!importFile.value) { ElMessage.warning('请先选择文件'); return }
  importLoading.value = true
  try {
    await importSuppliers(importFile.value)
    ElMessage.success('导入成功'); importDialogVisible.value = false; importFile.value = null; loadData()
  } catch {} finally { importLoading.value = false }
}

async function handleExport() {
  try { const res = await exportSuppliers(); downloadBlob(res, '供应商列表.xlsx'); ElMessage.success('导出成功') } catch {}
}

function downloadBlob(res, filename) {
  const url = window.URL.createObjectURL(new Blob([res.data || res]))
  const a = document.createElement('a'); a.href = url; a.download = filename
  document.body.appendChild(a); a.click(); document.body.removeChild(a); window.URL.revokeObjectURL(url)
}

onMounted(() => { loadData(); loadSettlementTypes(); loadColumnConfig(); loadCategoryTree() })
</script>

<style scoped lang="scss">
.page-container { padding: 16px; }
.search-bar { margin-bottom: 12px; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
