<template>
  <div class="page-container">
    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-form :model="queryParams" inline>
        <el-form-item label="关键词">
          <el-input v-model="queryParams.keyword" placeholder="规格名称" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 工具栏 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <el-button type="primary" @click="handleAdd">新增规格</el-button>
        <el-button type="success" :icon="Download" @click="handleExport">导出</el-button>
        <el-button type="warning" :icon="Upload" @click="importDialogVisible = true">导入</el-button>
      </div>
      <div class="toolbar-right">
        <span style="color: #909399; font-size: 13px">共 {{ total }} 条</span>
      </div>
    </div>

    <!-- 数据表格 -->
    <el-table :data="tableData" v-loading="loading" border stripe size="small">
      <el-table-column prop="specName" label="规格名称" min-width="200" show-overflow-tooltip />
      <el-table-column prop="sortOrder" label="排序号" width="100" align="center" />
      <el-table-column prop="status" label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small">
            {{ row.status === 'active' ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170" show-overflow-tooltip>
        <template #default="{ row }">{{ $fmtDate(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right" align="center">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button :type="row.status === 'active' ? 'warning' : 'success'" link size="small" @click="handleToggleStatus(row)">
            {{ row.status === 'active' ? '禁用' : '启用' }}
          </el-button>
          <el-popconfirm title="确定删除该规格吗？" @confirm="handleDelete(row)">
            <template #reference>
              <el-button type="danger" link size="small">删除</el-button>
            </template>
          </el-popconfirm>
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

    <!-- 新增/编辑弹窗 -->
    <el-dialog :title="dialogTitle" v-model="dialogVisible" width="500px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="规格名称" prop="specName">
          <el-input v-model="form.specName" placeholder="如：材质、尺寸、颜色" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="排序号" prop="sortOrder">
          <el-input-number v-model="form.sortOrder" :min="0" :precision="0" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="active">启用</el-radio>
            <el-radio value="disabled">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
    <!-- 导入弹窗 -->
    <el-dialog v-model="importDialogVisible" title="导入规格" width="500px" destroy-on-close>
      <el-alert type="info" :closable="false" style="margin-bottom: 12px">
        请先下载模板，按模板格式填写后上传。支持 xlsx/xls 格式。
      </el-alert>
      <div style="margin-bottom: 16px">
        <el-button type="primary" link @click="handleDownloadTemplate">下载导入模板</el-button>
      </div>
      <el-upload ref="importUploadRef" :auto-upload="false" :limit="1" accept=".xlsx,.xls" :on-change="handleImportFileChange" drag>
        <el-icon :size="40" color="#c0c4cc"><Upload /></el-icon>
        <div style="margin-top: 8px; color: #909399">将文件拖到此处，或点击上传</div>
      </el-upload>
      <div v-if="importResult" style="margin-top: 12px">
        <div style="display: flex; gap: 16px; margin-bottom: 6px">
          <span>总计: <b>{{ importResult.total }}</b></span>
          <span style="color: #67C23A">成功: <b>{{ importResult.success }}</b></span>
          <span style="color: #F56C6C">失败: <b>{{ importResult.failed }}</b></span>
        </div>
        <div v-if="importResult.errors && importResult.errors.length" style="max-height:150px;overflow-y:auto;font-size:12px;color:#F56C6C">
          <div v-for="(err, idx) in importResult.errors" :key="idx">{{ err }}</div>
        </div>
      </div>
      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="importLoading" @click="handleImport">确认导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Upload } from '@element-plus/icons-vue'
import {
  getMaterialSpecPage,
  createMaterialSpec,
  updateMaterialSpec,
  deleteMaterialSpec,
  exportMaterialSpecs,
  importMaterialSpecs,
  downloadMaterialSpecTemplate
} from '@/api/master'

// ==================== 列表 ====================

const queryParams = reactive({ keyword: '', pageNum: 1, pageSize: 20 })
const tableData = ref([])
const total = ref(0)
const loading = ref(false)

async function loadData() {
  loading.value = true
  try {
    const res = await getMaterialSpecPage(queryParams)
    const data = res.data || res
    tableData.value = data.records || []
    total.value = data.total || 0
  } catch { /* ignore */ } finally {
    loading.value = false
  }
}

function handleSearch() { queryParams.pageNum = 1; loadData() }
function handleReset() { queryParams.keyword = ''; handleSearch() }

onMounted(() => loadData())

// ==================== 新增/编辑 ====================

const dialogVisible = ref(false)
const dialogTitle = ref('新增规格')
const submitLoading = ref(false)
const formRef = ref(null)
const editingId = ref(null)

const form = reactive({
  specName: '',
  sortOrder: 0,
  status: 'active'
})

const rules = {
  specName: [{ required: true, message: '请输入规格名称', trigger: 'blur' }]
}

function handleAdd() {
  editingId.value = null
  dialogTitle.value = '新增规格'
  form.specName = ''
  form.sortOrder = 0
  form.status = 'active'
  dialogVisible.value = true
}

function handleEdit(row) {
  editingId.value = row.id
  dialogTitle.value = '编辑规格'
  form.specName = row.specName
  form.sortOrder = row.sortOrder || 0
  form.status = row.status || 'active'
  dialogVisible.value = true
}

async function handleSubmit() {
  try {
    await formRef.value.validate()
  } catch { return }

  submitLoading.value = true
  try {
    if (editingId.value) {
      await updateMaterialSpec(editingId.value, form)
      ElMessage.success('更新成功')
    } else {
      await createMaterialSpec(form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadData()
  } catch (e) {
    ElMessage.error(e.response?.data?.msg || e.message || '操作失败')
  } finally {
    submitLoading.value = false
  }
}

// ==================== 状态切换 ====================

async function handleToggleStatus(row) {
  try {
    const newStatus = row.status === 'active' ? 'disabled' : 'active'
    await updateMaterialSpec(row.id, { status: newStatus })
    ElMessage.success(newStatus === 'active' ? '已启用' : '已禁用')
    loadData()
  } catch (e) {
    ElMessage.error(e.response?.data?.msg || e.message || '操作失败')
  }
}

// ==================== 删除 ====================

async function handleDelete(row) {
  try {
    await deleteMaterialSpec(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) {
    ElMessage.error(e.response?.data?.msg || e.message || '删除失败')
  }
}

// ==================== 导出 ====================

async function handleExport() {
  try {
    const blob = await exportMaterialSpecs()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = '规格库.xlsx'
    a.click()
    URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('导出失败')
  }
}

// ==================== 导入 ====================

const importDialogVisible = ref(false)
const importLoading = ref(false)
const importResult = ref(null)
const importFile = ref(null)

async function handleDownloadTemplate() {
  try {
    const blob = await downloadMaterialSpecTemplate()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = '规格库导入模板.xlsx'
    a.click()
    URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('下载模板失败')
  }
}

function handleImportFileChange(file) {
  importFile.value = file.raw
}

async function handleImport() {
  if (!importFile.value) {
    ElMessage.warning('请先选择文件')
    return
  }
  importLoading.value = true
  importResult.value = null
  try {
    const resp = await importMaterialSpecs(importFile.value)
    const res = resp.data || resp
    importResult.value = res
    if (res.failed === 0) {
      ElMessage.success(`导入完成：成功 ${res.success} 条`)
    } else {
      ElMessage.warning(`导入完成：成功 ${res.success} 条，失败 ${res.failed} 条`)
    }
    loadData()
  } catch (e) {
    ElMessage.error(e.response?.data?.msg || e.message || '导入失败')
  } finally {
    importLoading.value = false
  }
}
</script>

<style scoped>
.page-container {
  padding: 0;
}
.search-bar {
  margin-bottom: 12px;
}
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
