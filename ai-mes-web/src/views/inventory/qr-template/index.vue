<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="模板名称">
          <el-input v-model="queryParams.keyword" placeholder="请输入模板名称" clearable style="width: 200px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="primary" plain @click="openDialog(null)">新增模板</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 模板列表 -->
    <el-card shadow="never" class="table-card">
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="templateName" label="模板名称" min-width="160" />
        <el-table-column label="标签尺寸" width="140" align="center">
          <template #default="{ row }">
            {{ row.labelWidth }} × {{ row.labelHeight }} mm
          </template>
        </el-table-column>
        <el-table-column prop="qrFields" label="二维码字段" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag v-for="f in parseFields(row.qrFields)" :key="f" size="small" style="margin: 2px;">{{ fieldLabel(f) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="printFields" label="打印字段" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag v-for="f in parseFields(row.printFields)" :key="f" type="info" size="small" style="margin: 2px;">{{ fieldLabel(f) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="isDefault" label="默认" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isDefault" type="success" size="small">默认</el-tag>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" min-width="170" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button v-if="!row.isDefault" link type="success" @click="handleSetDefault(row)">设为默认</el-button>
            <el-button link type="primary" @click="handlePreview(row)">预览</el-button>
            <el-button v-if="!row.isDefault" link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑模板' : '新增模板'" width="600px" destroy-on-close>
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="模板名称" prop="templateName">
          <el-input v-model="form.templateName" placeholder="请输入模板名称" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="标签宽度" prop="labelWidth">
              <el-input-number v-model="form.labelWidth" :min="20" :max="200" :step="5" style="width: 100%">
                <template #append>mm</template>
              </el-input-number>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="标签高度" prop="labelHeight">
              <el-input-number v-model="form.labelHeight" :min="10" :max="200" :step="5" style="width: 100%">
                <template #append>mm</template>
              </el-input-number>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="二维码字段" prop="qrFields">
          <el-checkbox-group v-model="form.qrFieldList">
            <el-checkbox v-for="f in allFields" :key="f.value" :label="f.value">{{ f.label }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="打印字段" prop="printFields">
          <el-checkbox-group v-model="form.printFieldList">
            <el-checkbox v-for="f in allFields" :key="f.value" :label="f.value">{{ f.label }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 预览弹窗 -->
    <el-dialog v-model="previewVisible" title="标签预览" width="420px">
      <div class="label-preview" :style="previewStyle">
        <div class="preview-qr">
          <div class="qr-placeholder">QR</div>
        </div>
        <div class="preview-info">
          <div v-for="f in parseFields(previewRow?.printFields)" :key="f" class="preview-field">
            <span class="preview-label">{{ fieldLabel(f) }}：</span>
            <span class="preview-value">示例数据</span>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getQrTemplateList,
  createQrTemplate,
  updateQrTemplate,
  deleteQrTemplate,
  setDefaultQrTemplate
} from '@/api/inventory'

const allFields = [
  { value: 'materialName', label: '物料名称' },
  { value: 'materialCode', label: '物料编码' },
  { value: 'spec', label: '规格型号' },
  { value: 'batchNo', label: '批次号' },
  { value: 'quantity', label: '数量' },
  { value: 'unit', label: '单位' },
  { value: 'warehouseName', label: '仓库' },
  { value: 'productionDate', label: '生产日期' },
  { value: 'expiryDate', label: '有效期' },
  { value: 'supplierName', label: '供应商' }
]

const fieldMap = {}
allFields.forEach(f => { fieldMap[f.value] = f.label })

function fieldLabel(val) {
  return fieldMap[val] || val
}

function parseFields(str) {
  if (!str) return []
  try { return JSON.parse(str) } catch { return str.split(',').filter(Boolean) }
}

// --- state ---
const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const queryParams = reactive({ keyword: '' })

const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref(null)
const formRef = ref(null)
const form = reactive({
  templateName: '',
  labelWidth: 60,
  labelHeight: 40,
  qrFieldList: [],
  printFieldList: []
})

const rules = {
  templateName: [{ required: true, message: '请输入模板名称', trigger: 'blur' }],
  labelWidth: [{ required: true, message: '请输入宽度', trigger: 'blur' }],
  labelHeight: [{ required: true, message: '请输入高度', trigger: 'blur' }]
}

const previewVisible = ref(false)
const previewRow = ref(null)
const previewStyle = computed(() => {
  if (!previewRow.value) return {}
  const scale = 3
  return {
    width: previewRow.value.labelWidth * scale + 'px',
    height: previewRow.value.labelHeight * scale + 'px'
  }
})

// --- methods ---
async function loadData() {
  loading.value = true
  try {
    const res = await getQrTemplateList(queryParams)
    tableData.value = res.data || []
  } catch {} finally {
    loading.value = false
  }
}

function handleSearch() { loadData() }
function handleReset() { queryParams.keyword = ''; loadData() }

function openDialog(row) {
  if (row) {
    isEdit.value = true
    editId.value = row.id
    form.templateName = row.templateName
    form.labelWidth = row.labelWidth
    form.labelHeight = row.labelHeight
    form.qrFieldList = parseFields(row.qrFields)
    form.printFieldList = parseFields(row.printFields)
  } else {
    isEdit.value = false
    editId.value = null
    form.templateName = ''
    form.labelWidth = 60
    form.labelHeight = 40
    form.qrFieldList = ['materialCode', 'batchNo']
    form.printFieldList = ['materialName', 'spec', 'batchNo', 'quantity']
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  await formRef.value.validate()
  submitLoading.value = true
  try {
    const payload = {
      templateName: form.templateName,
      labelWidth: form.labelWidth,
      labelHeight: form.labelHeight,
      qrFields: JSON.stringify(form.qrFieldList),
      printFields: JSON.stringify(form.printFieldList)
    }
    if (isEdit.value) {
      await updateQrTemplate(editId.value, payload)
      ElMessage.success('修改成功')
    } else {
      await createQrTemplate(payload)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadData()
  } catch {} finally {
    submitLoading.value = false
  }
}

async function handleSetDefault(row) {
  try {
    await setDefaultQrTemplate(row.id)
    ElMessage.success('已设为默认模板')
    loadData()
  } catch {}
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确定删除模板「${row.templateName}」？`, '提示', { type: 'warning' })
  try {
    await deleteQrTemplate(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch {}
}

function handlePreview(row) {
  previewRow.value = row
  previewVisible.value = true
}

onMounted(() => { loadData() })
</script>

<style scoped lang="scss">
.page-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.search-card {
  :deep(.el-card__body) { padding-bottom: 2px; }
}
.table-card { flex: 1; }
.text-muted { color: #c0c4cc; }
.label-preview {
  border: 2px dashed #409eff;
  border-radius: 4px;
  display: flex;
  align-items: center;
  padding: 12px;
  margin: 0 auto;
  gap: 12px;
  background: #fafafa;
}
.preview-qr {
  flex-shrink: 0;
  .qr-placeholder {
    width: 60px;
    height: 60px;
    border: 2px solid #333;
    display: flex;
    align-items: center;
    justify-content: center;
    font-weight: bold;
    font-size: 18px;
    color: #333;
  }
}
.preview-info {
  flex: 1;
  .preview-field {
    font-size: 12px;
    line-height: 1.8;
    .preview-label { color: #999; }
    .preview-value { color: #333; }
  }
}
</style>
