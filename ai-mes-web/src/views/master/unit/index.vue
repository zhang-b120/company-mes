<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>计量单位管理</span>
          <el-button v-permission="'master:unit:add'" type="primary" @click="handleAdd">新增单位</el-button>
        </div>
      </template>

      <!-- 搜索栏 -->
      <el-form :inline="true" :model="queryParams" class="search-form">
        <el-form-item label="单位名称">
          <el-input v-model="queryParams.unitName" placeholder="单位名称" clearable @keyup.enter="handleSearch" @clear="handleSearch" />
        </el-form-item>
        <el-form-item label="单位编码">
          <el-input v-model="queryParams.unitCode" placeholder="单位编码" clearable @keyup.enter="handleSearch" @clear="handleSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 列表 -->
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="unitName" label="单位名称" min-width="140" />
        <el-table-column prop="unitCode" label="单位编码" min-width="120" />
        <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small">
              {{ row.status === 'active' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="center">
          <template #default="{ row }">
            <el-button v-permission="'master:unit:edit'" type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button v-permission="'master:unit:delete'" type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="480px" destroy-on-close @close="resetForm">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="80px">
        <el-form-item label="单位名称" prop="unitName">
          <el-input v-model="formData.unitName" placeholder="如：个、件、千克" />
        </el-form-item>
        <el-form-item label="单位编码" prop="unitCode">
          <el-input v-model="formData.unitCode" placeholder="如：PCS、KG" :disabled="!!formData.id" />
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="formData.sortOrder" :min="0" :max="9999" controls-position="right" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="formData.status">
            <el-radio value="active">启用</el-radio>
            <el-radio value="inactive">停用</el-radio>
          </el-radio-group>
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
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getUnitPage, createUnit, updateUnit, deleteUnit } from '@/api/master'

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref(null)

const queryParams = reactive({ unitName: '', unitCode: '', pageNum: 1, pageSize: 20 })

const formData = ref({ id: undefined, unitName: '', unitCode: '', sortOrder: 0, status: 'active' })

const formRules = {
  unitName: [{ required: true, message: '请输入单位名称', trigger: 'blur' }],
  unitCode: [{ required: true, message: '请输入单位编码', trigger: 'blur' }]
}

async function loadData() {
  loading.value = true
  try {
    const res = await getUnitPage(queryParams)
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } catch {} finally { loading.value = false }
}

function handleSearch() { queryParams.pageNum = 1; loadData() }
function handleReset() { queryParams.unitName = ''; queryParams.unitCode = ''; handleSearch() }

function handleAdd() {
  dialogTitle.value = '新增单位'
  formData.value = { id: undefined, unitName: '', unitCode: '', sortOrder: 0, status: 'active' }
  dialogVisible.value = true
}

function handleEdit(row) {
  dialogTitle.value = '编辑单位'
  formData.value = { id: row.id, unitName: row.unitName, unitCode: row.unitCode, sortOrder: row.sortOrder || 0, status: row.status || 'active' }
  dialogVisible.value = true
}

async function handleSubmit() {
  await formRef.value.validate()
  submitLoading.value = true
  try {
    if (formData.value.id) {
      await updateUnit(formData.value.id, formData.value)
      ElMessage.success('更新成功')
    } else {
      await createUnit(formData.value)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadData()
  } catch {} finally { submitLoading.value = false }
}

function handleDelete(row) {
  ElMessageBox.confirm(`确定删除单位「${row.unitName}」吗？`, '提示', { type: 'warning' }).then(async () => {
    try { await deleteUnit(row.id); ElMessage.success('删除成功'); loadData() } catch {}
  }).catch(() => {})
}

function resetForm() { formRef.value?.resetFields() }

onMounted(() => { loadData() })
</script>

<style scoped lang="scss">
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.search-form {
  margin-bottom: 16px;
}
.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
