<template>
  <div>
    <el-tabs v-model="subTab">
      <el-tab-pane label="废料录入" name="entry">
        <!-- 工具栏 -->
        <div class="toolbar">
          <el-form :model="queryParams" inline>
            <el-form-item label="废料名称">
              <el-input v-model="queryParams.wasteName" placeholder="废料名称" clearable @keyup.enter="handleSearch" style="width: 150px" />
            </el-form-item>
            <el-form-item label="操作人">
              <el-input v-model="queryParams.operatorName" placeholder="操作人" clearable @keyup.enter="handleSearch" style="width: 130px" />
            </el-form-item>
            <el-form-item label="产品名称">
              <el-input v-model="queryParams.productName" placeholder="产品名称" clearable @keyup.enter="handleSearch" style="width: 150px" />
            </el-form-item>
            <el-form-item label="工单号">
              <el-input v-model="queryParams.workOrderNo" placeholder="工单号" clearable @keyup.enter="handleSearch" style="width: 150px" />
            </el-form-item>
            <el-form-item label="缺陷类型">
              <el-select v-model="queryParams.defectType" placeholder="全部" clearable style="width: 130px">
                <el-option v-for="t in defectTypes" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>
            <el-form-item label="日期">
              <el-date-picker
                v-model="dateRange"
                type="daterange"
                range-separator="至"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                value-format="YYYY-MM-DD"
                style="width: 220px"
                @change="handleSearch"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleSearch">搜索</el-button>
              <el-button @click="handleReset">重置</el-button>
              <el-button type="primary" plain @click="handleAdd">+ 新增废料记录</el-button>
              <el-button type="warning" :icon="Download" @click="handleExport">导出</el-button>
              <el-button :icon="Setting" @click="colConfigDialogRef.open()">列配置</el-button>
            </el-form-item>
          </el-form>
        </div>

        <!-- 表格 -->
        <el-table :data="tableData" v-loading="loading" border stripe size="small">
          <el-table-column v-for="col in visibleColumns" :key="col.key"
            :label="col.label" :min-width="col.minWidth || 100" :align="col.align || 'left'" show-overflow-tooltip>
            <template #default="{ row }">
              <template v-if="col.key === 'quantity'">{{ row.quantity }} {{ row.unit || '' }}</template>
              <template v-else-if="col.key === 'defectType'">
                <el-tag v-if="row.defectType" size="small" type="warning">{{ row.defectType }}</el-tag>
                <span v-else class="text-muted">-</span>
              </template>
              <template v-else>{{ row[col.key] || '-' }}</template>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right" align="center">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
              <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 分页 -->
        <div class="pagination-wrapper">
          <el-pagination
            v-model:current-page="queryParams.pageNum"
            v-model:page-size="queryParams.pageSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="total"
            layout="total, sizes, prev, pager, next, jumper"
            background
            @size-change="loadData"
            @current-change="loadData"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane label="统计分析" name="stats">
        <!-- 统计筛选 -->
        <div class="toolbar">
          <el-form inline>
            <el-form-item label="统计时段">
              <el-date-picker
                v-model="statsDateRange"
                type="daterange"
                range-separator="至"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                value-format="YYYY-MM-DD"
                style="width: 220px"
                @change="loadStats"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadStats">刷新统计</el-button>
            </el-form-item>
          </el-form>
        </div>

        <el-row :gutter="16" style="margin-bottom: 16px" v-loading="statsLoading">
          <!-- 汇总卡片 -->
          <el-col :span="6">
            <el-card shadow="never" class="stat-card">
              <div class="stat-label">累计废料记录</div>
              <div class="stat-value">{{ statsData.totalCount || 0 }} <span class="stat-unit">条</span></div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="never" class="stat-card">
              <div class="stat-label">累计废料数量</div>
              <div class="stat-value" style="color: #f56c6c">{{ statsData.totalQuantity || 0 }} <span class="stat-unit">件</span></div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="never" class="stat-card">
              <div class="stat-label">缺陷类型数</div>
              <div class="stat-value" style="color: #e6a23c">{{ Object.keys(statsData.byDefectType || {}).length }}</div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="never" class="stat-card">
              <div class="stat-label">涉及废料种类</div>
              <div class="stat-value" style="color: #409eff">{{ Object.keys(statsData.byWasteName || {}).length }}</div>
            </el-card>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <!-- 按缺陷类型分布 -->
          <el-col :span="12">
            <el-card shadow="never" header="按缺陷类型分布">
              <el-table :data="defectTypeRows" border stripe size="small">
                <el-table-column prop="name" label="缺陷类型" />
                <el-table-column prop="quantity" label="废料数量" align="right">
                  <template #default="{ row }">{{ row.quantity }}</template>
                </el-table-column>
                <el-table-column label="占比" width="120" align="right">
                  <template #default="{ row }">
                    <el-progress :percentage="row.pct" :stroke-width="8" :show-text="false" style="width: 80px; display: inline-block" />
                    <span style="margin-left: 6px; font-size: 12px">{{ row.pct }}%</span>
                  </template>
                </el-table-column>
              </el-table>
            </el-card>
          </el-col>

          <!-- 按废料名称分布 -->
          <el-col :span="12">
            <el-card shadow="never" header="按废料名称分布">
              <el-table :data="wasteNameRows" border stripe size="small">
                <el-table-column prop="name" label="废料名称" />
                <el-table-column prop="quantity" label="废料数量" align="right">
                  <template #default="{ row }">{{ row.quantity }}</template>
                </el-table-column>
                <el-table-column label="占比" width="120" align="right">
                  <template #default="{ row }">
                    <el-progress :percentage="row.pct" :stroke-width="8" :show-text="false" style="width: 80px; display: inline-block" />
                    <span style="margin-left: 6px; font-size: 12px">{{ row.pct }}%</span>
                  </template>
                </el-table-column>
              </el-table>
            </el-card>
          </el-col>
        </el-row>

        <!-- 按日期趋势 -->
        <el-card shadow="never" header="按日期趋势" style="margin-top: 16px">
          <el-table :data="dateRows" border stripe size="small">
            <el-table-column prop="date" label="日期" width="130" />
            <el-table-column prop="quantity" label="废料数量" align="right" />
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <ColumnConfigDialog ref="colConfigDialogRef" :all-columns="allColumns" :default-columns="QUALITY_WASTE_DEFAULT_COLUMNS" @save="saveConfig" />

    <!-- 新增/编辑弹窗 -->
    <el-dialog :title="dialogTitle" v-model="dialogVisible" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="记录日期" prop="recordDate">
              <el-date-picker v-model="form.recordDate" type="date" placeholder="选择日期" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="操作人" prop="operatorName">
              <el-input v-model="form.operatorName" placeholder="请输入操作人" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="废料名称" prop="wasteName">
              <el-input v-model="form.wasteName" placeholder="请输入废料名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="缺陷类型" prop="defectType">
              <el-select v-model="form.defectType" placeholder="请选择或输入" filterable allow-create default-first-option style="width: 100%">
                <el-option v-for="t in defectTypes" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="废料数量" prop="quantity">
              <el-input-number v-model="form.quantity" :min="0.001" :precision="3" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="单位" prop="unit">
              <el-select v-model="form.unit" placeholder="请选择" filterable allow-create default-first-option style="width: 100%">
                <el-option label="件" value="件" />
                <el-option label="kg" value="kg" />
                <el-option label="m" value="m" />
                <el-option label="m²" value="m²" />
                <el-option label="组" value="组" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="关联产品">
              <el-input v-model="form.productName" placeholder="产品名称（可选）" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="关联工单号">
              <el-input v-model="form.workOrderNo" placeholder="工单号（可选）" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="废料原因" prop="reason">
          <el-input v-model="form.reason" type="textarea" :rows="2" placeholder="请描述废料产生原因" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="备注（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Setting, Download } from '@element-plus/icons-vue'
import { getWasteRecordPage, createWasteRecord, updateWasteRecord, deleteWasteRecord, getWasteStats, exportWasteRecords } from '@/api/quality'
import { useColumnConfig } from '@/composables/useColumnConfig'
import ColumnConfigDialog from '@/components/ColumnConfigDialog.vue'

const QUALITY_WASTE_DEFAULT_COLUMNS = [
  { key: 'recordDate', label: '记录日期', minWidth: 110 },
  { key: 'wasteName', label: '废料名称', minWidth: 120 },
  { key: 'quantity', label: '数量', minWidth: 90, align: 'right' },
  { key: 'defectType', label: '缺陷类型', minWidth: 110 },
  { key: 'reason', label: '原因', minWidth: 150 },
  { key: 'operatorName', label: '操作人', minWidth: 100 },
  { key: 'productName', label: '产品', minWidth: 120 },
  { key: 'workOrderNo', label: '工单号', minWidth: 160 },
  { key: 'remark', label: '备注', minWidth: 120 },
]
const colConfigDialogRef = ref(null)
const { visibleColumns, allColumns, loadColumnConfig, saveConfig } = useColumnConfig('quality_waste', QUALITY_WASTE_DEFAULT_COLUMNS)

// 缺陷类型常用选项
const defectTypes = ['尺寸超差', '外观缺陷', '材料缺陷', '加工错误', '装配错误', '其他']

// ===== 废料录入列表 =====
const subTab = ref('entry')
const dateRange = ref([])
const queryParams = reactive({ wasteName: '', operatorName: '', productName: '', workOrderNo: '', defectType: undefined, dateStart: undefined, dateEnd: undefined, pageNum: 1, pageSize: 10 })
const loading = ref(false)
const tableData = ref([])
const total = ref(0)

async function loadData() {
  loading.value = true
  try {
    const params = { ...queryParams }
    if (dateRange.value?.length === 2) {
      params.dateStart = dateRange.value[0]
      params.dateEnd = dateRange.value[1]
    }
    const res = await getWasteRecordPage(params)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch {} finally { loading.value = false }
}
function handleSearch() { queryParams.pageNum = 1; loadData() }
function handleReset() {
  queryParams.wasteName = ''; queryParams.operatorName = ''; queryParams.productName = ''; queryParams.workOrderNo = ''; queryParams.defectType = undefined
  dateRange.value = []; queryParams.pageNum = 1; loadData()
}

function downloadBlob(data, filename) {
  const blob = new Blob([data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url; link.download = filename; link.click()
  window.URL.revokeObjectURL(url)
}
async function handleExport() {
  try {
    const params = {
      wasteName: queryParams.wasteName || undefined,
      operatorName: queryParams.operatorName || undefined,
      productName: queryParams.productName || undefined,
      workOrderNo: queryParams.workOrderNo || undefined,
      defectType: queryParams.defectType || undefined
    }
    if (dateRange.value?.length === 2) {
      params.dateStart = dateRange.value[0]
      params.dateEnd = dateRange.value[1]
    }
    const res = await exportWasteRecords(params)
    downloadBlob(res, `废料统计_${new Date().toISOString().slice(0, 10)}.xlsx`)
    ElMessage.success('导出成功')
  } catch { ElMessage.error('导出失败') }
}

// ===== 弹窗 =====
const dialogVisible = ref(false)
const dialogTitle = ref('新增废料记录')
const formRef = ref(null)
const submitLoading = ref(false)
const editingId = ref(null)

function today() {
  const d = new Date()
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())}`
}
const getDefaultForm = () => ({
  recordDate: today(), wasteName: '', quantity: undefined, unit: '件',
  defectType: undefined, reason: '', operatorName: '', productName: '', workOrderNo: '', remark: ''
})
const form = reactive(getDefaultForm())
const rules = {
  recordDate: [{ required: true, message: '请选择记录日期', trigger: 'change' }],
  wasteName: [{ required: true, message: '请输入废料名称', trigger: 'blur' }],
  quantity: [{ required: true, message: '请输入废料数量', trigger: 'change' },
             { type: 'number', min: 0.001, message: '数量必须大于0', trigger: 'change' }]
}

function resetForm() { const d = getDefaultForm(); Object.keys(d).forEach(k => { form[k] = d[k] }) }
function handleAdd() { resetForm(); editingId.value = null; dialogTitle.value = '新增废料记录'; dialogVisible.value = true }
function handleEdit(row) {
  resetForm(); editingId.value = row.id; dialogTitle.value = '编辑废料记录'
  Object.keys(form).forEach(k => { if (row[k] !== undefined) form[k] = row[k] })
  dialogVisible.value = true
}
async function handleSubmit() {
  await formRef.value.validate()
  submitLoading.value = true
  try {
    if (editingId.value) {
      await updateWasteRecord(editingId.value, { ...form }); ElMessage.success('更新成功')
    } else {
      await createWasteRecord({ ...form }); ElMessage.success('新增成功')
    }
    dialogVisible.value = false; loadData(); loadStats()
  } finally { submitLoading.value = false }
}
async function handleDelete(row) {
  await ElMessageBox.confirm(`确认删除废料记录「${row.wasteName}」？`, '提示', { type: 'warning' })
  try {
    await deleteWasteRecord(row.id); ElMessage.success('删除成功'); loadData(); loadStats()
  } catch {}
}

// ===== 统计分析 =====
const statsDateRange = ref([])
const statsLoading = ref(false)
const statsData = ref({ totalCount: 0, totalQuantity: 0, byDate: {}, byDefectType: {}, byWasteName: {} })

const defectTypeRows = computed(() => {
  const map = statsData.value.byDefectType || {}
  const total = Object.values(map).reduce((s, v) => s + Number(v), 0)
  return Object.entries(map).map(([name, quantity]) => ({
    name,
    quantity,
    pct: total > 0 ? Math.round(Number(quantity) / total * 100) : 0
  })).sort((a, b) => b.quantity - a.quantity)
})
const wasteNameRows = computed(() => {
  const map = statsData.value.byWasteName || {}
  const total = Object.values(map).reduce((s, v) => s + Number(v), 0)
  return Object.entries(map).map(([name, quantity]) => ({
    name,
    quantity,
    pct: total > 0 ? Math.round(Number(quantity) / total * 100) : 0
  })).sort((a, b) => b.quantity - a.quantity)
})
const dateRows = computed(() => {
  return Object.entries(statsData.value.byDate || {}).map(([date, quantity]) => ({ date, quantity }))
})

async function loadStats() {
  statsLoading.value = true
  try {
    const params = {}
    if (statsDateRange.value?.length === 2) {
      params.dateStart = statsDateRange.value[0]
      params.dateEnd = statsDateRange.value[1]
    }
    const res = await getWasteStats(params)
    statsData.value = res.data || {}
  } catch {} finally { statsLoading.value = false }
}

onMounted(() => { loadData(); loadStats(); loadColumnConfig() })
</script>

<style scoped lang="scss">
.toolbar { margin-bottom: 12px; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 12px; }
.text-muted { color: #c0c4cc; }
.stat-card {
  text-align: center;
  .stat-label { font-size: 13px; color: #909399; margin-bottom: 8px; }
  .stat-value { font-size: 28px; font-weight: 700; color: #303133; }
  .stat-unit { font-size: 14px; color: #909399; }
}
</style>
