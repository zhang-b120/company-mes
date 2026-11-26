<template>
  <div class="page-container">
    <!-- 顶部操作栏 -->
    <el-card shadow="never" class="search-card">
      <el-form inline>
        <el-form-item>
          <el-button type="primary" plain @click="openWarehouseDialog(null)">新增仓库</el-button>
        </el-form-item>
        <el-form-item>
          <el-button @click="loadTree">刷新</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 树形表格 -->
    <el-card shadow="never" class="table-card">
      <el-table
        :data="treeData"
        v-loading="loading"
        border
        row-key="id"
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
        default-expand-all
      >
        <el-table-column prop="warehouseName" label="名称" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag v-if="row.parentId > 0" type="info" size="small" style="margin-right:6px">库位</el-tag>
            <span>{{ row.warehouseName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="warehouseType" label="类型" width="130" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.parentId === 0" :type="typeTagType(row.warehouseType)" size="small">
              {{ typeLabel(row.warehouseType) }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="管理员" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.parentId === 0">{{ row.managerNames || '-' }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.parentId === 0" :type="row.status === 'active' ? 'success' : 'danger'" size="small">
              {{ row.status === 'active' ? '启用' : '禁用' }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <template v-if="row.parentId === 0">
              <el-button link type="primary" @click="openWarehouseDialog(row)">编辑</el-button>
              <el-button link type="success" @click="openLocationDialog(row)">新增库位</el-button>
              <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
            </template>
            <template v-else>
              <el-button link type="primary" @click="openLocationEditDialog(row)">编辑</el-button>
              <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑仓库弹窗 -->
    <el-dialog v-model="whDialogVisible" :title="editWhId ? '编辑仓库' : '新增仓库'" width="540px" destroy-on-close>
      <el-form :model="whForm" :rules="whRules" ref="whFormRef" label-width="90px">
        <el-form-item label="仓库名称" prop="warehouseName">
          <el-input v-model="whForm.warehouseName" placeholder="请输入仓库名称" />
        </el-form-item>
        <el-form-item label="仓库类型" prop="warehouseType">
          <el-select v-model="whForm.warehouseType" placeholder="请选择仓库类型" style="width:100%">
            <el-option v-for="item in typeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="管理员">
          <el-select
            v-model="whForm.managerIdList"
            multiple
            filterable
            placeholder="请选择管理员（可多选）"
            style="width:100%"
          >
            <el-option v-for="u in userOptions" :key="u.id" :label="u.realName || u.username" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="whForm.status" style="width:100%">
            <el-option label="启用" value="active" />
            <el-option label="禁用" value="disabled" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="whForm.remark" type="textarea" :rows="2" placeholder="备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="whDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleWhSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 新增/编辑库位弹窗 -->
    <el-dialog v-model="locDialogVisible" :title="editLocId ? '编辑库位' : '新增库位'" width="420px" destroy-on-close>
      <el-form :model="locForm" :rules="locRules" ref="locFormRef" label-width="90px">
        <el-form-item v-if="!editLocId" label="所属仓库">
          <span>{{ currentWarehouseName }}</span>
        </el-form-item>
        <el-form-item label="库位名称" prop="warehouseName">
          <el-input v-model="locForm.warehouseName" placeholder="请输入库位名称" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="locDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleLocSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getWarehouseTree,
  createWarehouse,
  updateWarehouse,
  deleteWarehouse,
  createWarehouseLocation,
  getUserSelect
} from '@/api/system'

// ==================== 类型选项 ====================
const typeOptions = [
  { label: '原材料仓', value: 'raw_material' },
  { label: '半成品仓', value: 'semi_finished' },
  { label: '成品仓', value: 'finished' },
  { label: '综合仓', value: 'general' }
]
const typeMap = Object.fromEntries(typeOptions.map(t => [t.value, t.label]))

function typeLabel(type) { return typeMap[type] || type || '-' }
function typeTagType(type) {
  return { raw_material: '', semi_finished: 'warning', finished: 'success', general: 'info' }[type] || 'info'
}

// ==================== 树形数据 ====================
const loading = ref(false)
const treeData = ref([])
const userOptions = ref([])

async function loadTree() {
  loading.value = true
  try {
    const res = await getWarehouseTree()
    treeData.value = res.data || []
  } finally {
    loading.value = false
  }
}

async function loadUsers() {
  try {
    const res = await getUserSelect()
    userOptions.value = res.data || []
  } catch {
    // ignore
  }
}

// ==================== 仓库 新增/编辑 ====================
const whDialogVisible = ref(false)
const submitLoading = ref(false)
const editWhId = ref(null)
const whFormRef = ref(null)
const whForm = ref({
  warehouseName: '',
  warehouseType: '',
  managerIdList: [],
  status: 'active',
  remark: ''
})
const whRules = {
  warehouseName: [{ required: true, message: '请输入仓库名称', trigger: 'blur' }],
  warehouseType: [{ required: true, message: '请选择仓库类型', trigger: 'change' }]
}

function openWarehouseDialog(row) {
  if (row) {
    editWhId.value = row.id
    whForm.value = {
      warehouseName: row.warehouseName,
      warehouseType: row.warehouseType,
      managerIdList: row.managerIds ? row.managerIds.split(',').map(Number).filter(Boolean) : [],
      status: row.status || 'active',
      remark: row.remark || ''
    }
  } else {
    editWhId.value = null
    whForm.value = { warehouseName: '', warehouseType: '', managerIdList: [], status: 'active', remark: '' }
  }
  whDialogVisible.value = true
}

async function handleWhSubmit() {
  await whFormRef.value.validate()
  submitLoading.value = true
  try {
    const payload = {
      warehouseName: whForm.value.warehouseName,
      warehouseType: whForm.value.warehouseType,
      managerIds: whForm.value.managerIdList.join(','),
      status: whForm.value.status,
      remark: whForm.value.remark
    }
    if (editWhId.value) {
      await updateWarehouse(editWhId.value, payload)
      ElMessage.success('修改成功')
    } else {
      await createWarehouse(payload)
      ElMessage.success('新增成功')
    }
    whDialogVisible.value = false
    loadTree()
  } finally {
    submitLoading.value = false
  }
}

// ==================== 库位 新增/编辑 ====================
const locDialogVisible = ref(false)
const editLocId = ref(null)
const currentWarehouseId = ref(null)
const currentWarehouseName = ref('')
const locFormRef = ref(null)
const locForm = ref({ warehouseName: '' })
const locRules = {
  warehouseName: [{ required: true, message: '请输入库位名称', trigger: 'blur' }]
}

function openLocationDialog(warehouseRow) {
  editLocId.value = null
  currentWarehouseId.value = warehouseRow.id
  currentWarehouseName.value = warehouseRow.warehouseName
  locForm.value = { warehouseName: '' }
  locDialogVisible.value = true
}

function openLocationEditDialog(row) {
  editLocId.value = row.id
  currentWarehouseId.value = row.parentId
  locForm.value = { warehouseName: row.warehouseName }
  locDialogVisible.value = true
}

async function handleLocSubmit() {
  await locFormRef.value.validate()
  submitLoading.value = true
  try {
    if (editLocId.value) {
      await updateWarehouse(editLocId.value, { warehouseName: locForm.value.warehouseName })
      ElMessage.success('修改成功')
    } else {
      await createWarehouseLocation(currentWarehouseId.value, { warehouseName: locForm.value.warehouseName })
      ElMessage.success('新增成功')
    }
    locDialogVisible.value = false
    loadTree()
  } finally {
    submitLoading.value = false
  }
}

// ==================== 删除 ====================
async function handleDelete(row) {
  const label = row.parentId > 0 ? '库位' : '仓库'
  await ElMessageBox.confirm(`确定删除该${label}？`, '提示', { type: 'warning' })
  try {
    await deleteWarehouse(row.id)
    ElMessage.success('删除成功')
    loadTree()
  } catch {
    // error handled by interceptor
  }
}

// ==================== 初始化 ====================
onMounted(() => {
  loadTree()
  loadUsers()
})
</script>

<style scoped lang="scss">
.page-container {
  padding: 16px;
}
.search-card {
  margin-bottom: 16px;
}
</style>
