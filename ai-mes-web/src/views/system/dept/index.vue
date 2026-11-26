<template>
  <div class="page-container">
    <!-- 操作栏 -->
    <el-card class="search-card" shadow="never">
      <el-form inline>
        <el-form-item>
          <el-button v-permission="'sys:dept:add'" type="primary" plain @click="handleAdd(null)">新增部门</el-button>
          <el-button @click="toggleExpandAll">{{ isExpandAll ? '全部折叠' : '全部展开' }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 树形表格 -->
    <el-card class="table-card" shadow="never">
      <el-table
        v-if="refreshTable"
        :data="tableData"
        v-loading="loading"
        row-key="id"
        border
        :default-expand-all="isExpandAll"
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
      >
        <el-table-column prop="deptName" label="部门名称" min-width="200" />
        <el-table-column prop="managerName" label="负责人" min-width="100">
          <template #default="{ row }">{{ row.managerName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small">
              {{ row.status === 'active' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" min-width="160">
          <template #default="{ row }">{{ $fmtDate(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'sys:dept:add'" type="primary" link size="small" @click="handleAdd(row)">新增子部门</el-button>
            <el-button v-permission="'sys:dept:edit'" type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button v-permission="'sys:dept:delete'" type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="500px"
      destroy-on-close
      @close="resetForm"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="90px"
      >
        <el-form-item label="上级部门" prop="parentId">
          <el-tree-select
            v-model="formData.parentId"
            :data="deptTreeOptions"
            :props="{ label: 'deptName', value: 'id', children: 'children' }"
            placeholder="请选择上级部门（不选则为顶级）"
            clearable
            check-strictly
            style="width: 100%"
            :disabled="formData.id != null && formData.parentId === 0"
          />
        </el-form-item>
        <el-form-item label="部门名称" prop="deptName">
          <el-input v-model="formData.deptName" placeholder="请输入部门名称" />
        </el-form-item>
        <el-form-item label="负责人" prop="managerId">
          <el-select v-model="formData.managerId" placeholder="请选择负责人" clearable filterable style="width: 100%">
            <el-option
              v-for="u in userOptions"
              :key="u.id"
              :label="u.realName"
              :value="u.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="formData.sortOrder" :min="0" :max="999" style="width: 160px" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="formData.status">
            <el-radio value="active">启用</el-radio>
            <el-radio value="disabled">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="formData.remark" type="textarea" :rows="3" placeholder="请输入备注" />
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
import { ref, nextTick, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getDeptTree, createDept, updateDept, deleteDept } from '@/api/system'
import { getUserSelect } from '@/api/system'

// state
const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const deptTreeOptions = ref([])
const userOptions = ref([])
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref(null)
const isExpandAll = ref(true)
const refreshTable = ref(true)

const formData = ref({
  id: undefined,
  parentId: undefined,
  deptName: '',
  managerId: undefined,
  sortOrder: 0,
  status: 'active',
  remark: ''
})

const formRules = {
  deptName: [{ required: true, message: '请输入部门名称', trigger: 'blur' }]
}

// methods
async function loadData() {
  loading.value = true
  try {
    const res = await getDeptTree()
    tableData.value = res.data || []
    deptTreeOptions.value = res.data || []
  } catch {
    // handled by interceptor
  } finally {
    loading.value = false
  }
}

async function loadUserOptions() {
  try {
    const res = await getUserSelect()
    userOptions.value = res.data || []
  } catch {
    // handled by interceptor
  }
}

async function toggleExpandAll() {
  isExpandAll.value = !isExpandAll.value
  refreshTable.value = false
  await nextTick()
  refreshTable.value = true
}

function handleAdd(parent) {
  dialogTitle.value = parent ? `新增子部门（${parent.deptName}）` : '新增部门'
  formData.value = {
    id: undefined,
    parentId: parent ? parent.id : undefined,
    deptName: '',
    managerId: undefined,
    sortOrder: 0,
    status: 'active',
    remark: ''
  }
  dialogVisible.value = true
}

function handleEdit(row) {
  dialogTitle.value = '编辑部门'
  formData.value = {
    id: row.id,
    parentId: row.parentId || undefined,
    deptName: row.deptName,
    managerId: row.managerId || undefined,
    sortOrder: row.sortOrder || 0,
    status: row.status || 'active',
    remark: row.remark || ''
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  await formRef.value.validate()
  submitLoading.value = true
  const payload = {
    ...formData.value,
    parentId: formData.value.parentId || 0
  }
  try {
    if (formData.value.id) {
      await updateDept(formData.value.id, payload)
      ElMessage.success('更新成功')
    } else {
      await createDept(payload)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadData()
  } catch {
    // handled by interceptor
  } finally {
    submitLoading.value = false
  }
}

function handleDelete(row) {
  ElMessageBox.confirm(
    `确定删除部门「${row.deptName}」吗？删除前请确保该部门下无子部门和用户。`,
    '警告',
    { type: 'warning' }
  ).then(async () => {
    try {
      await deleteDept(row.id)
      ElMessage.success('删除成功')
      loadData()
    } catch {
      // handled by interceptor
    }
  }).catch(() => {})
}

function resetForm() {
  formRef.value?.resetFields()
}

onMounted(() => {
  loadData()
  loadUserOptions()
})
</script>

<style scoped lang="scss">
.page-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.search-card {
  :deep(.el-card__body) {
    padding-bottom: 2px;
  }
}
.table-card {
  flex: 1;
}
</style>
