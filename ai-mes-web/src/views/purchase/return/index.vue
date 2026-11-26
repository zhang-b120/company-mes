<template>
  <div class="page-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="关键词">
          <el-input v-model="queryParams.keyword" placeholder="退货单号" clearable style="width: 200px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 130px">
            <el-option label="草稿" value="draft" />
            <el-option label="待审批" value="pending_approval" />
            <el-option label="已确认" value="confirmed" />
            <el-option label="已驳回" value="rejected" />
            <el-option label="办结" value="closed" />
          </el-select>
        </el-form-item>
        <el-form-item label="供应商">
          <el-select v-model="queryParams.supplierId" placeholder="全部" clearable filterable style="width: 160px">
            <el-option v-for="s in supplierOptions" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button v-permission="'purchase:return:add'" type="primary" plain @click="handleAdd">新增退货</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据表格 -->
    <el-card shadow="never" class="table-card">
      <div class="table-toolbar">
        <div class="toolbar-left">
          <span v-if="selectedRows.length > 0" class="selection-info">已选 {{ selectedRows.length }} 条</span>
        </div>
        <div class="toolbar-right">
          <el-button
            v-permission="'purchase:return:inv-task'"
            type="primary"
            :disabled="!selectedRows.some(r => r.status === 'confirmed' && !r.invTaskId)"
            @click="handleBatchGenTask"
          >批量生成库存任务</el-button>
        </div>
      </div>
      <el-table :data="tableData" v-loading="loading" border stripe @selection-change="selectedRows = $event">
        <el-table-column type="selection" width="40" />
        <el-table-column prop="returnNo" label="退货单号" min-width="170" show-overflow-tooltip />
        <el-table-column prop="supplierName" label="供应商" min-width="140" show-overflow-tooltip />
        <el-table-column prop="poNo" label="关联采购订单" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.poNo && row.poNo !== '-'" style="color: #409eff; cursor: pointer" @click="handleViewOrder(row)">{{ row.poNo }}</span>
            <span v-else style="color: #c0c4cc">—</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="库存任务" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.invTaskId" type="success" size="small">已生成</el-tag>
            <span v-else style="color: #c0c4cc; font-size: 12px">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="returnReason" label="退货原因" min-width="140" show-overflow-tooltip />
        <el-table-column prop="createTime" label="创建时间" min-width="165">
          <template #default="{ row }">{{ $fmtDate(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleViewDetail(row)">查看</el-button>
            <el-button v-permission="'purchase:return:add'"
              v-if="row.status === 'draft'"
              type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button v-permission="'purchase:return:add'"
              v-if="row.status === 'draft'"
              type="warning" link size="small" @click="handleSubmitApproval(row)">提交审批</el-button>
            <el-button v-permission="'purchase:return:approve'"
              v-if="row.status === 'pending_approval'"
              type="success" link size="small" @click="handleApprove(row)">审批通过</el-button>
            <el-button v-permission="'purchase:return:approve'"
              v-if="row.status === 'pending_approval'"
              type="danger" link size="small" @click="handleReject(row)">审批驳回</el-button>
            <el-button v-permission="'purchase:return:inv-task'"
              v-if="row.status === 'confirmed' && !row.invTaskId"
              type="success" link size="small" @click="handleGenTask(row)">生成库存任务</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50, 100]" :total="total" background
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="820px" destroy-on-close @close="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="供应商" prop="supplierId">
              <el-select v-model="form.supplierId" placeholder="请选择供应商" filterable style="width: 100%">
                <el-option v-for="s in supplierOptions" :key="s.id" :label="s.name" :value="s.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="关联单据号">
          <el-input v-model="form.relatedOrderNo" placeholder="如采购订单号、其他关联单据号（选填）" clearable />
        </el-form-item>
        <el-form-item label="退货原因">
          <el-input v-model="form.returnReason" placeholder="请填写退货原因" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="退货明细" required>
          <div style="width: 100%">
            <el-table :data="form.items" border size="small" style="margin-bottom: 10px">
              <el-table-column label="物料编号" width="140" show-overflow-tooltip>
                <template #default="{ row }">
                  <span v-if="row.materialNo">{{ row.materialNo }}</span>
                  <span v-else style="color:#c0c4cc">-</span>
                </template>
              </el-table-column>
              <el-table-column label="物料" min-width="180">
                <template #default="{ row }">
                  <div style="display:flex;gap:4px;align-items:center">
                    <el-input
                      :value="row.materialName || ''"
                      placeholder="请选择物料" readonly size="small" style="flex:1" />
                    <el-button size="small" type="primary" plain @click="openMaterialPicker(row)">选</el-button>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="规格" width="90">
                <template #default="{ row }">
                  <span style="color:#606266;font-size:12px">{{ $fmtSpec(row.spec) || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="单位" width="60" align="center">
                <template #default="{ row }">
                  <span style="color:#606266;font-size:12px">{{ row.unit || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="数量" width="120">
                <template #default="{ row }">
                  <el-input-number v-model="row.quantity" :min="0.0001" :precision="4" size="small" controls-position="right" style="width:100%" />
                </template>
              </el-table-column>
              <el-table-column label="换算数量" width="120">
                <template #default="{ row }">
                  <span v-if="secondaryDisplay(row.quantity, row.unitConfig)" style="color: #909399; font-size: 12px">
                    {{ secondaryDisplay(row.quantity, row.unitConfig) }}
                  </span>
                  <span v-else style="color: #c0c4cc">-</span>
                </template>
              </el-table-column>
              <el-table-column label="单价" width="120">
                <template #default="{ row }">
                  <el-input-number v-model="row.unitPrice" :min="0" :precision="4" size="small" controls-position="right" style="width:100%" />
                </template>
              </el-table-column>
              <el-table-column label="备注" min-width="100">
                <template #default="{ row }">
                  <el-input v-model="row.remark" size="small" placeholder="备注" />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="60" align="center">
                <template #default="{ $index }">
                  <el-button type="danger" link size="small" @click="removeItem($index)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-button size="small" @click="addItem">+ 添加明细</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 审批备注弹窗 -->
    <el-dialog v-model="remarkDialogVisible" :title="remarkDialogTitle" width="440px">
      <el-input v-model="remarkInput" type="textarea" :rows="3" placeholder="请输入审批意见（选填）" />
      <template #footer>
        <el-button @click="remarkDialogVisible = false">取消</el-button>
        <el-button :type="remarkDialogType" :loading="remarkLoading" @click="confirmRemark">确定</el-button>
      </template>
    </el-dialog>


    <!-- 详情抽屉 -->
    <el-drawer v-model="detailDrawerVisible" title="采购退货单详情" size="760px" direction="rtl" destroy-on-close>
      <div v-if="detailLoading" style="text-align:center;padding:60px 0">
        <el-icon class="is-loading" size="28"><Loading /></el-icon>
      </div>
      <template v-else-if="detailData">
        <el-descriptions :column="2" border size="small" style="margin-bottom:16px">
          <el-descriptions-item label="退货单号" :span="2">
            <span style="font-weight:600">{{ detailData.returnNo }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="供应商">{{ detailData.supplierName }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTagType(detailData.status)" size="small">{{ statusText(detailData.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="关联采购订单">{{ detailData.poNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="关联单据号">{{ detailData.relatedOrderNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="库存任务">
            <el-tag v-if="detailData.invTaskId" type="success" size="small">已生成(#{{ detailData.invTaskId }})</el-tag>
            <span v-else style="color:#c0c4cc">未生成</span>
          </el-descriptions-item>
          <el-descriptions-item label="退货原因" :span="2">{{ detailData.returnReason || '-' }}</el-descriptions-item>
          <el-descriptions-item label="审批意见" :span="2">{{ detailData.approveRemark || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ $fmtDate(detailData.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="备注">{{ detailData.remark || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div style="font-weight:600;margin-bottom:8px">退货明细</div>
        <el-table :data="detailData.items" border stripe size="small">
          <el-table-column prop="materialNo" label="物料编码" width="120" show-overflow-tooltip />
          <el-table-column prop="materialName" label="物料名称" min-width="130" show-overflow-tooltip />
          <el-table-column label="规格" width="100" show-overflow-tooltip>
            <template #default="{ row }">{{ $fmtSpec(row.spec) || '-' }}</template>
          </el-table-column>
          <el-table-column prop="unit" label="单位" width="60" align="center" />
          <el-table-column prop="quantity" label="退货数量" width="100" align="right" />
          <el-table-column label="换算数量" width="120" align="right">
            <template #default="{ row }">
              <span v-if="secondaryDisplay(row.quantity, row.unitConfig)" style="color: #909399; font-size: 12px">
                {{ secondaryDisplay(row.quantity, row.unitConfig) }}
              </span>
              <span v-else style="color: #c0c4cc">-</span>
            </template>
          </el-table-column>
          <el-table-column label="单价" width="100" align="right">
            <template #default="{ row }">
              <span v-if="row.unitPrice != null">¥{{ Number(row.unitPrice).toFixed(2) }}</span>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="100" show-overflow-tooltip />
        </el-table>
      </template>
    </el-drawer>

    <MaterialPickerDialog v-model="materialPickerVisible" title="选择物料" @select="onMaterialSelected" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import MaterialPickerDialog from '@/components/MaterialPickerDialog.vue'
import {
  getPurchaseReturnPage,
  getPurchaseReturnDetail,
  createPurchaseReturn,
  updatePurchaseReturn,
  submitPurchaseReturn,
  approvePurchaseReturn,
  rejectPurchaseReturn,
  generateReturnInvTask,
  batchGenerateReturnInvTask
} from '@/api/purchase'
import { getSupplierSelect } from '@/api/master'

function secondaryDisplay(qty, unitConfig) {
  if (!qty || !unitConfig) return ''
  try {
    const cfg = JSON.parse(unitConfig)
    const c = cfg.conversions?.[0]
    if (c) return `${(qty * c.ratio).toFixed(4)} ${c.unit}`
  } catch {}
  return ''
}

function statusTagType(s) {
  return { draft: 'info', pending_approval: 'warning', confirmed: 'primary', rejected: 'danger', closed: 'success' }[s] || 'info'
}
function statusText(s) {
  return { draft: '草稿', pending_approval: '待审批', confirmed: '已确认', rejected: '已驳回', closed: '办结' }[s] || s || '-'
}

const supplierOptions = ref([])
const selectedRows = ref([])

const queryParams = reactive({ keyword: '', status: undefined, supplierId: undefined, pageNum: 1, pageSize: 10 })
const loading = ref(false)
const tableData = ref([])
const total = ref(0)

async function loadData() {
  loading.value = true
  try {
    const res = await getPurchaseReturnPage(queryParams)
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } finally { loading.value = false }
}

async function loadOptions() {
  try {
    const res = await getSupplierSelect()
    supplierOptions.value = res.data || []
  } catch {}
}

onMounted(() => { loadData(); loadOptions() })

function handleSearch() { queryParams.pageNum = 1; loadData() }
function handleReset() {
  Object.assign(queryParams, { keyword: '', status: undefined, supplierId: undefined, pageNum: 1 })
  loadData()
}

// ==================== 新增/编辑 ====================
const dialogVisible = ref(false)
const dialogTitle = ref('新增采购退货')
const editingId = ref(null)
const formRef = ref(null)
const submitLoading = ref(false)

const getDefaultForm = () => ({
  supplierId: undefined,
  relatedOrderNo: '',
  returnReason: '',
  remark: '',
  items: [{ materialId: undefined, materialName: '', spec: '', unit: '', quantity: 1, unitPrice: 0, remark: '' }]
})
const form = reactive(getDefaultForm())
const rules = {
  supplierId: [{ required: true, message: '请选择供应商', trigger: 'change' }]
}

function addItem() {
  form.items.push({ materialId: undefined, materialName: '', spec: '', unit: '', quantity: 1, unitPrice: 0, remark: '' })
}
function removeItem(index) {
  if (form.items.length <= 1) { ElMessage.warning('至少保留一条明细'); return }
  form.items.splice(index, 1)
}
function resetForm() {
  editingId.value = null
  dialogTitle.value = '新增采购退货'
  const d = getDefaultForm(); Object.keys(d).forEach(k => { form[k] = d[k] })
}
function handleAdd() { resetForm(); dialogVisible.value = true }

async function handleEdit(row) {
  resetForm()
  editingId.value = row.id
  dialogTitle.value = '编辑采购退货'
  try {
    const res = await getPurchaseReturnDetail(row.id)
    const d = res.data || {}
    form.supplierId = d.supplierId || undefined
    form.relatedOrderNo = d.relatedOrderNo || ''
    form.returnReason = d.returnReason || ''
    form.remark = d.remark || ''
    form.items = (d.items || []).map(item => ({
      materialId: item.materialId,
      materialName: item.materialName || '',
      spec: item.spec || '',
      unit: item.unit || '',
      unitConfig: item.unitConfig || '',
      quantity: item.quantity || 1,
      unitPrice: item.unitPrice || 0,
      remark: item.remark || ''
    }))
    if (!form.items.length) form.items.push({ materialId: undefined, materialName: '', spec: '', unit: '', quantity: 1, unitPrice: 0, remark: '' })
    dialogVisible.value = true
  } catch { ElMessage.error('加载退货单信息失败') }
}

async function handleSubmit() {
  await formRef.value.validate()
  const validItems = form.items.filter(i => i.materialId)
  if (!validItems.length) { ElMessage.warning('请至少添加一条退货明细'); return }
  submitLoading.value = true
  try {
    const payload = { supplierId: form.supplierId, relatedOrderNo: form.relatedOrderNo || undefined, returnReason: form.returnReason, remark: form.remark, items: validItems }
    if (editingId.value) {
      await updatePurchaseReturn(editingId.value, payload)
      ElMessage.success('退货单更新成功')
    } else {
      await createPurchaseReturn(payload)
      ElMessage.success('退货单创建成功')
    }
    dialogVisible.value = false; loadData()
  } finally { submitLoading.value = false }
}

// ==================== 物料选择 ====================
const materialPickerVisible = ref(false)
let currentPickerRow = null
function openMaterialPicker(row) { currentPickerRow = row; materialPickerVisible.value = true }
function onMaterialSelected(mats) {
  if (!currentPickerRow || !mats?.length) return
  mats.forEach((mat, idx) => {
    const target = idx === 0 ? currentPickerRow : (() => {
      const row = { materialId: undefined, materialNo: '', materialName: '', spec: '', unit: '', quantity: 1, unitPrice: 0, remark: '' }
      form.items.push(row)
      return row
    })()
    target.materialId = mat.id
    target.materialNo = mat.materialNo || ''
    target.materialName = mat.materialName
    target.spec = mat.spec || ''
    target.unit = mat.unit || ''
  })
}

// ==================== 提交审批 ====================
function handleSubmitApproval(row) {
  ElMessageBox.confirm(`确定提交退货单「${row.returnNo}」审批？`, '提示', { type: 'warning' })
    .then(async () => {
      await submitPurchaseReturn(row.id); ElMessage.success('已提交审批'); loadData()
    }).catch(() => {})
}

// ==================== 审批 ====================
const remarkDialogVisible = ref(false)
const remarkDialogTitle = ref('')
const remarkDialogType = ref('primary')
const remarkInput = ref('')
const remarkLoading = ref(false)
const remarkRow = ref(null)
const remarkAction = ref('')

function handleApprove(row) {
  remarkRow.value = row; remarkAction.value = 'approve'
  remarkInput.value = ''; remarkDialogTitle.value = '审批通过'; remarkDialogType.value = 'success'
  remarkDialogVisible.value = true
}
function handleReject(row) {
  remarkRow.value = row; remarkAction.value = 'reject'
  remarkInput.value = ''; remarkDialogTitle.value = '审批驳回'; remarkDialogType.value = 'danger'
  remarkDialogVisible.value = true
}
async function confirmRemark() {
  remarkLoading.value = true
  try {
    const payload = { remark: remarkInput.value }
    if (remarkAction.value === 'approve') {
      await approvePurchaseReturn(remarkRow.value.id, payload)
      ElMessage.success('已审批通过')
    } else {
      await rejectPurchaseReturn(remarkRow.value.id, payload)
      ElMessage.success('已驳回')
    }
    remarkDialogVisible.value = false; loadData()
  } finally { remarkLoading.value = false }
}

// ==================== 生成库存任务 ====================
function handleGenTask(row) {
  ElMessageBox.confirm(`确定为退货单「${row.returnNo}」生成库存任务？每张退货单只能生成一次。`, '提示', { type: 'warning' })
    .then(async () => {
      try {
        await generateReturnInvTask(row.id, {})
        ElMessage.success('库存任务生成成功')
        loadData()
      } catch (e) {
        ElMessage.error(e?.response?.data?.msg || '生成失败')
      }
    }).catch(() => {})
}
function handleBatchGenTask() {
  const eligible = selectedRows.value.filter(r => r.status === 'confirmed' && !r.invTaskId)
  if (!eligible.length) { ElMessage.warning('没有符合条件的退货单（已确认且未生成任务）'); return }
  ElMessageBox.confirm(`确定为选中的 ${eligible.length} 张退货单批量生成库存任务？`, '提示', { type: 'warning' })
    .then(async () => {
      try {
        await batchGenerateReturnInvTask({ ids: eligible.map(r => r.id) })
        ElMessage.success('批量生成完成')
        loadData()
      } catch (e) {
        ElMessage.error(e?.response?.data?.msg || '生成失败')
      }
    }).catch(() => {})
}

// ==================== 详情 ====================
const detailDrawerVisible = ref(false)
const detailLoading = ref(false)
const detailData = ref(null)

async function handleViewDetail(row) {
  detailData.value = null
  detailDrawerVisible.value = true
  detailLoading.value = true
  try {
    const res = await getPurchaseReturnDetail(row.id)
    detailData.value = res.data
  } finally { detailLoading.value = false }
}

function handleViewOrder(_row) {
  // 可扩展为跳转采购订单页面
}
</script>

<style scoped>
.page-container { padding: 16px; }
.search-card { margin-bottom: 12px; }
.table-card { }
.table-toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.toolbar-left { display: flex; align-items: center; gap: 8px; }
.toolbar-right { display: flex; align-items: center; gap: 8px; }
.selection-info { color: #409eff; font-size: 13px; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 12px; }
</style>
