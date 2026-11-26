<template>
  <div class="page-container">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="模板名称">
          <el-input v-model="queryParams.templateName" placeholder="模板名称" clearable style="width: 150px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="模板编码">
          <el-input v-model="queryParams.templateCode" placeholder="模板编码" clearable style="width: 150px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="适用类型">
          <el-select v-model="queryParams.applyType" placeholder="全部" clearable style="width: 140px">
            <el-option label="来料检验" value="incoming" />
            <el-option label="过程检验" value="process" />
            <el-option label="外协检验" value="outsource" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="primary" plain @click="handleAdd">新增模板</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据表格 -->
    <el-card class="table-card" shadow="never">
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="templateName" label="模板名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="templateCode" label="模板编码" min-width="130" show-overflow-tooltip />
        <el-table-column prop="applyType" label="适用类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="applyTypeTag(row.applyType)" size="small" effect="plain">
              {{ applyTypeText(row.applyType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="schemeName" label="质检方案" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.schemeName">{{ row.schemeName }}</span>
            <span v-else class="text-muted">未关联</span>
          </template>
        </el-table-column>
        <el-table-column prop="isDefault" label="默认" width="70" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isDefault" type="success" size="small">是</el-tag>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'danger'" size="small">
              {{ row.status === 'active' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" min-width="160" />
        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="info" link size="small" @click="handleCopy(row)">复制</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
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
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="960px" destroy-on-close @close="resetForm">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="模板名称" prop="templateName">
              <el-input v-model="formData.templateName" placeholder="请输入模板名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="模板编码" prop="templateCode">
              <el-input v-model="formData.templateCode" placeholder="请输入模板编码" :disabled="!!formData.id" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="适用类型" prop="applyType">
              <el-select v-model="formData.applyType" placeholder="请选择" style="width: 100%">
                <el-option label="来料检验" value="incoming" />
                <el-option label="过程检验" value="process" />
                <el-option label="外协检验" value="outsource" />
                <el-option label="通用" value="all" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="质检方案" prop="schemeId">
              <el-select v-model="formData.schemeId" placeholder="请选择质检方案" filterable style="width: 100%">
                <el-option v-for="s in schemeOptions" :key="s.id" :label="s.schemeName" :value="s.id">
                  <span>{{ s.schemeName }}</span>
                  <span style="float:right;color:#909399;font-size:12px">{{ s.schemeType === 'full' ? '全检' : '抽检' }}</span>
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="设为默认">
              <el-switch v-model="formData.isDefault" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="状态">
              <el-switch v-model="formData.status" active-value="active" inactive-value="inactive" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="formData.remark" placeholder="备注信息" />
        </el-form-item>

        <el-divider content-position="left">质量特性</el-divider>
        <div style="margin-bottom: 12px;">
          <el-button type="primary" plain size="small" @click="openItemPicker">从项目库选择</el-button>
        </div>
        <div style="max-height: 420px; overflow-y: auto;">
          <div v-for="(item, idx) in formData.items" :key="idx" class="qc-item-card">
            <div class="qc-item-card__header">
              <span class="qc-item-card__title">{{ item.itemName || '未命名项目' }}</span>
              <div style="display:flex;align-items:center;gap:12px">
                <span style="font-size:13px;color:#606266">数据类型</span>
                <el-select v-model="item.itemType" size="small" style="width:100px">
                  <el-option label="计量型" value="quantitative" />
                  <el-option label="文本型" value="text" />
                  <el-option label="单选项" value="radio" />
                </el-select>
                <span style="font-size:13px;color:#606266">必填</span>
                <el-switch v-model="item.isRequired" :active-value="1" :inactive-value="0" size="small" />
                <el-button type="danger" link size="small" @click="formData.items.splice(idx, 1)">删除</el-button>
              </div>
            </div>
            <!-- 计量型专属字段 -->
            <el-row v-if="item.itemType === 'quantitative'" :gutter="16" style="margin-top:8px">
              <el-col :span="8">
                <div class="qc-item-card__field">
                  <label><span class="required-star">* </span>检验要求：</label>
                  <el-input v-model="item.method" size="small" placeholder="请输入" />
                </div>
              </el-col>
              <el-col :span="8">
                <div class="qc-item-card__field">
                  <label>上限值：</label>
                  <el-input v-model="item.toleranceMax" size="small" placeholder="请输入" />
                </div>
              </el-col>
              <el-col :span="8">
                <div class="qc-item-card__field">
                  <label><span class="required-star">* </span>标准值：</label>
                  <el-input v-model="item.standardValue" size="small" placeholder="请输入" />
                </div>
              </el-col>
            </el-row>
            <el-row v-if="item.itemType === 'quantitative'" :gutter="16" style="margin-top:8px">
              <el-col :span="8">
                <div class="qc-item-card__field">
                  <label>下限值：</label>
                  <el-input v-model="item.toleranceMin" size="small" placeholder="请输入" />
                </div>
              </el-col>
              <el-col :span="8">
                <div class="qc-item-card__field">
                  <label>默认值：</label>
                  <el-input v-model="item.defaultValue" size="small" placeholder="请输入" />
                </div>
              </el-col>
              <el-col :span="8">
                <div class="qc-item-card__field">
                  <label>单位：</label>
                  <el-input v-model="item.unit" size="small" placeholder="请输入" />
                </div>
              </el-col>
            </el-row>
            <!-- 文本型字段 -->
            <el-row v-if="item.itemType === 'text'" :gutter="16" style="margin-top:8px">
              <el-col :span="8">
                <div class="qc-item-card__field">
                  <label><span class="required-star">* </span>检验要求：</label>
                  <el-input v-model="item.method" size="small" placeholder="请输入" />
                </div>
              </el-col>
              <el-col :span="8">
                <div class="qc-item-card__field">
                  <label>默认值：</label>
                  <el-input v-model="item.defaultValue" size="small" placeholder="请输入" />
                </div>
              </el-col>
            </el-row>
            <!-- 单选项字段 -->
            <el-row v-if="item.itemType === 'radio'" :gutter="16" style="margin-top:8px">
              <el-col :span="8">
                <div class="qc-item-card__field">
                  <label><span class="required-star">* </span>检验要求：</label>
                  <el-input v-model="item.method" size="small" placeholder="请输入" />
                </div>
              </el-col>
              <el-col :span="8">
                <div class="qc-item-card__field">
                  <label><span class="required-star">* </span>选项1：</label>
                  <div style="display:flex;align-items:center;gap:4px">
                    <el-input value="合格" size="small" disabled style="flex:1" />
                    <el-tag type="success" size="small" effect="dark">合格</el-tag>
                  </div>
                </div>
              </el-col>
              <el-col :span="8">
                <div class="qc-item-card__field">
                  <label>选项2：</label>
                  <div style="display:flex;align-items:center;gap:4px">
                    <el-input value="不合格" size="small" disabled style="flex:1" />
                    <el-tag type="danger" size="small" effect="plain">不合格</el-tag>
                  </div>
                </div>
              </el-col>
            </el-row>
          </div>
          <el-empty v-if="!formData.items.length" description="暂无质量特性，请添加" :image-size="48" />
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 从项目库选择弹窗 -->
    <el-dialog v-model="pickerVisible" title="从项目库选择" width="700px" append-to-body destroy-on-close>
      <el-form inline style="margin-bottom: 12px;">
        <el-form-item label="分类">
          <el-select v-model="pickerCategory" placeholder="全部" clearable style="width: 130px" @change="loadPickerItems">
            <el-option label="尺寸检验" value="dimension" />
            <el-option label="外观检验" value="appearance" />
            <el-option label="性能检验" value="performance" />
            <el-option label="化学检验" value="chemical" />
            <el-option label="物理检验" value="physical" />
          </el-select>
        </el-form-item>
      </el-form>
      <el-table :data="pickerItems" border size="small" max-height="400" @selection-change="onPickerSelect">
        <el-table-column type="selection" width="45" />
        <el-table-column prop="itemCode" label="编码" width="120" />
        <el-table-column prop="itemName" label="名称" min-width="140" />
        <el-table-column prop="itemType" label="类型" width="80" align="center">
          <template #default="{ row }">{{ { quantitative: '计量型', text: '文本型', radio: '单选项' }[row.itemType] || '-' }}</template>
        </el-table-column>
        <el-table-column prop="category" label="分类" width="100" align="center">
          <template #default="{ row }">{{ categoryMap[row.category] || row.category || '--' }}</template>
        </el-table-column>
        <el-table-column prop="method" label="检验方法" min-width="140" show-overflow-tooltip />
      </el-table>
      <template #footer>
        <el-button @click="pickerVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmPicker">确定添加 ({{ pickerSelected.length }})</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { ElMessageBox } from 'element-plus'
import { getQcTemplatePage, getQcTemplateDetail, createQcTemplate, updateQcTemplate, deleteQcTemplate, getQcItemList, getQcSchemeSelect } from '@/api/quality'

const categoryMap = { dimension: '尺寸检验', appearance: '外观检验', performance: '性能检验', chemical: '化学检验', physical: '物理检验' }
const applyTypeMap = { incoming: '来料检验', process: '过程检验', outsource: '外协检验', all: '通用' }
const applyTypeTagMap = { incoming: '', process: 'success', outsource: 'danger', all: 'info' }
function applyTypeText(val) { return applyTypeMap[val] || val || '--' }
function applyTypeTag(val) { return applyTypeTagMap[val] || 'info' }

// 质检方案选项
const schemeOptions = ref([])
async function loadSchemeOptions() {
  try {
    const res = await getQcSchemeSelect()
    schemeOptions.value = res.data || []
  } catch {}
}

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const total = ref(0)
const queryParams = reactive({ templateName: '', templateCode: '', applyType: '', pageNum: 1, pageSize: 10 })

async function loadData() {
  loading.value = true
  try {
    const res = await getQcTemplatePage({
      templateName: queryParams.templateName || undefined,
      templateCode: queryParams.templateCode || undefined,
      applyType: queryParams.applyType || undefined,
      pageNum: queryParams.pageNum,
      pageSize: queryParams.pageSize
    })
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch {} finally { loading.value = false }
}

function handleSearch() { queryParams.pageNum = 1; loadData() }
function handleReset() { queryParams.templateName = ''; queryParams.templateCode = ''; queryParams.applyType = ''; queryParams.pageNum = 1; loadData() }

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref(null)
const formData = ref(getEmptyForm())
const formRules = {
  templateName: [{ required: true, message: '请输入模板名称', trigger: 'blur' }],
  templateCode: [{ required: true, message: '请输入模板编码', trigger: 'blur' }],
  applyType: [{ required: true, message: '请选择适用类型', trigger: 'change' }],
  schemeId: [{ required: true, message: '请选择质检方案', trigger: 'change' }]
}

function getEmptyForm() {
  return {
    id: undefined, templateName: '', templateCode: '', applyType: '', schemeId: null,
    isDefault: 0, status: 'active', remark: '', items: []
  }
}

function handleAdd() { dialogTitle.value = '新增检验模板'; formData.value = getEmptyForm(); dialogVisible.value = true }

async function handleEdit(row) {
  dialogTitle.value = '编辑检验模板'
  try {
    const res = await getQcTemplateDetail(row.id)
    const d = res.data || {}
    formData.value = {
      id: d.id, templateName: d.templateName, templateCode: d.templateCode,
      applyType: d.applyType, schemeId: d.schemeId || null,
      isDefault: d.isDefault || 0, status: d.status || 'active', remark: d.remark || '',
      items: (d.items || []).map(mapItemFields)
    }
    dialogVisible.value = true
  } catch {}
}

async function handleCopy(row) {
  try {
    const res = await getQcTemplateDetail(row.id)
    const d = res.data || {}
    dialogTitle.value = '复制检验模板'
    formData.value = {
      id: undefined, templateName: d.templateName + '_副本', templateCode: '',
      applyType: d.applyType, sampleMode: d.sampleMode || 'full',
      sampleRate: d.sampleRate, aqlLevel: d.aqlLevel || '',
      isDefault: 0, status: 'active', remark: d.remark || '',
      items: (d.items || []).map(mapItemFields)
    }
    dialogVisible.value = true
  } catch {}
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除模板「${row.templateName}」？删除后不可恢复`, '确认删除', { type: 'warning' })
    await deleteQcTemplate(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch {}
}

function addManualItem() {
  formData.value.items.push({
    itemName: '', itemType: 'quantitative', category: '', standardValue: '', toleranceMin: '', toleranceMax: '',
    unit: '', method: '', defaultValue: '', isRequired: 0, isCritical: 0
  })
}

async function handleSubmit() {
  await formRef.value.validate()
  submitLoading.value = true
  try {
    if (formData.value.id) {
      await updateQcTemplate(formData.value.id, formData.value)
      ElMessage.success('更新成功')
    } else {
      await createQcTemplate(formData.value)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false; loadData()
  } catch {} finally { submitLoading.value = false }
}

function resetForm() { formRef.value?.resetFields() }

// 项目库选择
const pickerVisible = ref(false)
const pickerCategory = ref('')
const pickerItems = ref([])
const pickerSelected = ref([])

async function openItemPicker() {
  pickerCategory.value = ''; pickerSelected.value = []
  await loadPickerItems(); pickerVisible.value = true
}

async function loadPickerItems() {
  try {
    const res = await getQcItemList({ category: pickerCategory.value || undefined })
    pickerItems.value = res.data || []
  } catch {}
}

function onPickerSelect(rows) { pickerSelected.value = rows }

function mapItemFields(i) {
  return {
    itemName: i.itemName || '', itemType: i.itemType || 'quantitative', category: i.category || '',
    standardValue: i.standardValue || '', toleranceMin: i.toleranceMin || '',
    toleranceMax: i.toleranceMax || '', unit: i.unit || '',
    method: i.method || '', defaultValue: i.defaultValue || '',
    isRequired: i.isRequired || 0, isCritical: i.isCritical || 0
  }
}

function confirmPicker() {
  for (const item of pickerSelected.value) {
    formData.value.items.push(mapItemFields(item))
  }
  pickerVisible.value = false
  ElMessage.success(`已添加 ${pickerSelected.value.length} 个检验项目`)
}

onMounted(() => { loadData(); loadSchemeOptions() })
</script>

<style scoped lang="scss">
.page-container { display: flex; flex-direction: column; gap: 16px; }
.search-card { :deep(.el-card__body) { padding-bottom: 2px; } }
.table-card { flex: 1; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; }
.text-muted { color: #c0c4cc; }

.qc-item-card {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 12px;
  background: #fafafa;
  &__header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;
    padding-bottom: 8px;
    border-bottom: 1px solid #ebeef5;
  }
  &__title {
    font-weight: 600;
    font-size: 14px;
    color: #303133;
  }
  &__field {
    margin-bottom: 4px;
    label {
      font-size: 13px;
      color: #606266;
      margin-bottom: 4px;
      display: block;
    }
  }
}
.required-star { color: #f56c6c; }
</style>
