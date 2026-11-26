<template>
  <div class="page-container">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="检验项名称">
          <el-input v-model="queryParams.itemName" placeholder="检验项名称" clearable style="width: 150px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="检验项编码">
          <el-input v-model="queryParams.itemCode" placeholder="检验项编码" clearable style="width: 150px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="queryParams.category" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="c in categoryOptions" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 110px">
            <el-option label="启用" value="active" />
            <el-option label="停用" value="inactive" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="primary" plain @click="handleAdd">新增项目</el-button>
          <el-button :icon="Setting" @click="colConfigDialogRef.open()">列配置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stat-row">
      <el-col :span="8">
        <div class="stat-card stat-card--clickable" :class="{ active: !queryParams.status }" @click="filterByStatus('')">
          <div class="stat-card__value" style="color: #409eff">{{ stats.total }}</div>
          <div class="stat-card__label">项目总数</div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="stat-card stat-card--clickable" :class="{ active: queryParams.status === 'active' }" @click="filterByStatus('active')">
          <div class="stat-card__value" style="color: #67c23a">{{ stats.active }}</div>
          <div class="stat-card__label">启用中</div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="stat-card stat-card--clickable" :class="{ active: queryParams.status === 'inactive' }" @click="filterByStatus('inactive')">
          <div class="stat-card__value" style="color: #909399">{{ stats.inactive }}</div>
          <div class="stat-card__label">已停用</div>
        </div>
      </el-col>
    </el-row>

    <!-- 数据表格 -->
    <el-card class="table-card" shadow="never">
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column v-for="col in visibleColumns" :key="col.key"
          :label="col.label" :min-width="col.minWidth || 100" :align="col.align || 'left'" show-overflow-tooltip>
          <template #default="{ row }">
            <template v-if="col.key === 'category'">
              <el-tag :type="categoryTag(row.category)" size="small" effect="plain">{{ categoryText(row.category) }}</el-tag>
            </template>
            <template v-else-if="col.key === 'itemType'">
              {{ { quantitative: '计量型', text: '文本型', radio: '单选项' }[row.itemType] || row.itemType || '-' }}
            </template>
            <template v-else-if="col.key === 'status'">
              <el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small">
                {{ row.status === 'active' ? '启用' : '停用' }}
              </el-tag>
            </template>
            <template v-else>{{ row[col.key] || '-' }}</template>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button :type="row.status === 'active' ? 'warning' : 'success'" link size="small" @click="handleToggle(row)">
              {{ row.status === 'active' ? '停用' : '启用' }}
            </el-button>
            <el-popconfirm title="确定删除该项目？" @confirm="handleDelete(row)">
              <template #reference>
                <el-button type="danger" link size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
          :total="total" :page-sizes="[10, 20, 50]" background layout="total, sizes, prev, pager, next"
          @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="680px" destroy-on-close @close="resetForm">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="项目名称" prop="itemName">
              <el-input v-model="formData.itemName" placeholder="请输入项目名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="项目编码" prop="itemCode">
              <el-input v-model="formData.itemCode" placeholder="留空自动生成" :disabled="!!formData.id" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="分类" prop="category">
              <el-select v-model="formData.category" placeholder="请选择分类" style="width: 100%">
                <el-option v-for="c in categoryOptions" :key="c.value" :label="c.label" :value="c.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="检验类型" prop="itemType">
              <el-radio-group v-model="formData.itemType">
                <el-radio value="quantitative">计量型</el-radio>
                <el-radio value="text">文本型</el-radio>
                <el-radio value="radio">单选项</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="状态">
          <el-switch v-model="formData.status" active-value="active" inactive-value="inactive" />
        </el-form-item>
        <el-form-item label="检验方法">
          <el-input v-model="formData.method" type="textarea" :rows="2" placeholder="描述检验方法" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="formData.remark" type="textarea" :rows="2" placeholder="备注信息" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <ColumnConfigDialog ref="colConfigDialogRef" :all-columns="allColumns" :default-columns="QUALITY_ITEM_DEFAULT_COLUMNS" @save="saveConfig" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Setting } from '@element-plus/icons-vue'
import { getQcItemPage, createQcItem, updateQcItem, toggleQcItemStatus, deleteQcItem } from '@/api/quality'
import { useColumnConfig } from '@/composables/useColumnConfig'
import ColumnConfigDialog from '@/components/ColumnConfigDialog.vue'

const QUALITY_ITEM_DEFAULT_COLUMNS = [
  { key: 'itemCode', label: '项目编码', minWidth: 130 },
  { key: 'itemName', label: '项目名称', minWidth: 160 },
  { key: 'category', label: '分类', minWidth: 100, align: 'center' },
  { key: 'itemType', label: '类型', minWidth: 90, align: 'center' },
  { key: 'method', label: '检验方法', minWidth: 180 },
  { key: 'status', label: '状态', minWidth: 80, align: 'center' },
]
const colConfigDialogRef = ref(null)
const { visibleColumns, allColumns, loadColumnConfig, saveConfig } = useColumnConfig('quality_item', QUALITY_ITEM_DEFAULT_COLUMNS)

const categoryOptions = [
  { value: 'dimension', label: '尺寸检验' },
  { value: 'appearance', label: '外观检验' },
  { value: 'performance', label: '性能检验' },
  { value: 'chemical', label: '化学检验' },
  { value: 'physical', label: '物理检验' }
]
const categoryMap = Object.fromEntries(categoryOptions.map(c => [c.value, c.label]))
const categoryTagMap = { dimension: '', appearance: 'success', performance: 'warning', chemical: 'danger', physical: 'info' }

function categoryText(val) { return categoryMap[val] || val || '--' }
function categoryTag(val) { return categoryTagMap[val] || 'info' }

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const total = ref(0)
const queryParams = reactive({ itemName: '', itemCode: '', category: '', status: '', pageNum: 1, pageSize: 10 })

const statsList = ref([])
const stats = computed(() => {
  const list = statsList.value
  return {
    total: list.length,
    active: list.filter(r => r.status === 'active').length,
    inactive: list.filter(r => r.status !== 'active').length
  }
})

async function loadStats() {
  try {
    const res = await getQcItemPage({ pageNum: 1, pageSize: 99999 })
    statsList.value = res.data?.records || []
  } catch { statsList.value = [] }
}

async function loadData() {
  loading.value = true
  try {
    const res = await getQcItemPage({
      itemName: queryParams.itemName || undefined,
      itemCode: queryParams.itemCode || undefined,
      category: queryParams.category || undefined,
      status: queryParams.status || undefined,
      pageNum: queryParams.pageNum,
      pageSize: queryParams.pageSize
    })
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch {} finally { loading.value = false }
}

function handleSearch() { queryParams.pageNum = 1; loadData() }
function filterByStatus(status) {
  queryParams.status = queryParams.status === status ? '' : status
  queryParams.pageNum = 1
  loadData()
}
function handleReset() {
  queryParams.itemName = ''; queryParams.itemCode = ''; queryParams.category = ''; queryParams.status = ''
  queryParams.pageNum = 1; loadData()
}

// 弹窗
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref(null)
const formData = ref({
  id: undefined, itemCode: '', itemName: '', category: '', itemType: 'quantitative',
  method: '', status: 'active', remark: ''
})
const formRules = {
  itemName: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
  category: [{ required: true, message: '请选择分类', trigger: 'change' }],
  itemType: [{ required: true, message: '请选择类型', trigger: 'change' }]
}

function handleAdd() {
  dialogTitle.value = '新增质检项目'
  formData.value = {
    id: undefined, itemCode: '', itemName: '', category: '', itemType: 'quantitative',
    method: '', status: 'active', remark: ''
  }
  dialogVisible.value = true
}

function handleEdit(row) {
  dialogTitle.value = '编辑质检项目'
  formData.value = { ...row }
  dialogVisible.value = true
}

async function handleSubmit() {
  await formRef.value.validate()
  submitLoading.value = true
  try {
    if (formData.value.id) {
      await updateQcItem(formData.value.id, formData.value)
      ElMessage.success('更新成功')
    } else {
      await createQcItem(formData.value)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadData()
  } catch {} finally { submitLoading.value = false }
}

async function handleToggle(row) {
  try {
    await toggleQcItemStatus(row.id)
    ElMessage.success(row.status === 'active' ? '已停用' : '已启用')
    loadData(); loadStats()
  } catch {}
}

async function handleDelete(row) {
  try {
    await deleteQcItem(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch {}
}

function resetForm() { formRef.value?.resetFields() }

onMounted(() => { loadData(); loadStats(); loadColumnConfig() })
</script>

<style scoped lang="scss">
.page-container { display: flex; flex-direction: column; gap: 16px; }
.search-card { :deep(.el-card__body) { padding-bottom: 2px; } }
.table-card { flex: 1; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; }
.text-muted { color: #c0c4cc; }
.stat-row { flex-shrink: 0; }
.stat-card {
  background: #fff; border-radius: 8px; padding: 16px; text-align: center;
  box-shadow: 0 1px 4px rgba(0,0,0,.06);
  border: 2px solid transparent;
  transition: all .2s;
  &__value { font-size: 28px; font-weight: 700; }
  &__label { font-size: 13px; color: #909399; margin-top: 4px; }
}
.stat-card--clickable { cursor: pointer; }
.stat-card--clickable:hover { box-shadow: 0 2px 12px rgba(64,158,255,.18); transform: translateY(-1px); }
.stat-card--clickable.active { border-color: #409eff; background: #ecf5ff; }
</style>
