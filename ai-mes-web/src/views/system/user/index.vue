<template>
  <div class="page-container">
    <!-- search bar -->
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="姓名">
          <el-input v-model="queryParams.realName" placeholder="姓名" clearable style="width: 130px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="queryParams.phone" placeholder="手机号" clearable style="width: 140px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="账号">
          <el-input v-model="queryParams.username" placeholder="账号" clearable style="width: 130px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="queryParams.roleId" placeholder="请选择角色" clearable style="width: 160px">
            <el-option
              v-for="item in roleOptions"
              :key="item.id"
              :label="item.roleName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
        <el-form-item>
          <el-button v-permission="'sys:user:add'" type="primary" plain @click="handleAdd">新增用户</el-button>
          <el-button type="success" :icon="Download" @click="handleExport">导出</el-button>
          <el-button type="warning" :icon="Upload" @click="importDialogVisible = true">导入</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- table -->
    <el-card class="table-card" shadow="never">
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="username" label="账号" min-width="110" />
        <el-table-column prop="realName" label="姓名" min-width="100" />
        <el-table-column prop="phone" label="手机号" min-width="120" />
        <el-table-column prop="icCardNo" label="IC卡号" min-width="130">
          <template #default="{ row }">{{ row.icCardNo || '-' }}</template>
        </el-table-column>
        <el-table-column prop="roleNames" label="角色" min-width="140">
          <template #default="{ row }">
            <template v-if="row.roleNames && row.roleNames.length">
              <el-tag v-for="name in row.roleNames" :key="name" size="small" style="margin-right: 4px">
                {{ name }}
              </el-tag>
            </template>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="deptName" label="部门" min-width="110">
          <template #default="{ row }">{{ row.deptName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="teamName" label="所属班组" min-width="100">
          <template #default="{ row }">{{ row.teamName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="hireDate" label="入职日期" width="120">
          <template #default="{ row }">{{ row.hireDate || '-' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 'active' || row.status === 1"
              @change="handleToggleStatus(row)"
              inline-prompt
              active-text="启"
              inactive-text="停"
            />
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" min-width="160">
          <template #default="{ row }">{{ $fmtDate(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'sys:user:edit'" type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button v-permission="'sys:user:reset-pwd'" type="warning" link size="small" @click="handleResetPassword(row)">重置密码</el-button>
            <el-button type="danger" link size="small" @click="handleDeleteUser(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- pagination -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          background
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="560px"
      destroy-on-close
      @close="resetForm"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="80px"
      >
        <el-form-item label="账号" prop="username">
          <el-input v-model="formData.username" placeholder="请输入账号" />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="formData.realName" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="formData.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="IC卡号" prop="icCardNo">
          <el-input v-model="formData.icCardNo" placeholder="工位机刷卡免密登录使用" clearable />
        </el-form-item>
        <el-form-item v-if="!formData.id" label="密码" prop="password">
          <el-input v-model="formData.password" type="password" placeholder="请输入密码" show-password />
        </el-form-item>
        <el-form-item label="角色" prop="roleIds">
          <el-select v-model="formData.roleIds" multiple placeholder="请选择角色" style="width: 100%">
            <el-option
              v-for="item in roleOptions"
              :key="item.id"
              :label="item.roleName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="所属部门" prop="deptId">
          <el-tree-select
            v-model="formData.deptId"
            :data="deptOptions"
            :props="{ label: 'deptName', value: 'id', children: 'children' }"
            placeholder="请选择所属部门"
            clearable
            check-strictly
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="所属班组">
          <el-select v-model="formData.teamIds" placeholder="请选择所属班组（可多选）" clearable filterable multiple style="width: 100%">
            <el-option v-for="t in teamOptions" :key="t.id" :label="t.teamName" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="入职日期" prop="hireDate">
          <el-date-picker
            v-model="formData.hireDate"
            type="date"
            placeholder="请选择入职日期"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
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

    <!-- import dialog -->
    <el-dialog v-model="importDialogVisible" title="导入用户" width="500px">
      <div style="margin-bottom: 16px;">
        <el-button type="primary" link @click="handleDownloadTemplate">下载导入模板</el-button>
      </div>
      <el-upload
        ref="uploadRef"
        :auto-upload="false"
        :limit="1"
        accept=".xlsx,.xls"
        :on-change="handleFileChange"
      >
        <template #trigger>
          <el-button type="primary">选择文件</el-button>
        </template>
        <template #tip>
          <div class="el-upload__tip">只能上传 xlsx/xls 文件</div>
        </template>
      </el-upload>
      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="importLoading" @click="handleImport">确认导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getUserPage,
  createUser,
  updateUser,
  updateUserStatus,
  resetPassword,
  deleteUser,
  getRoleList,
  getDeptTree,
  exportUsers,
  importUsers,
  downloadUserTemplate
} from '@/api/system'
import { getTeamList } from '@/api/team'
import { Download, Upload } from '@element-plus/icons-vue'
import { useAiChatStore } from '@/store/ai-chat'

const aiChatStore = useAiChatStore()

// AI填入表单
watch(() => aiChatStore.pendingFillData, (data) => {
  if (data?.module === 'user' && data.records?.length) {
    formData.value = { id: undefined, username: '', realName: '', phone: '', icCardNo: '', password: '', roleIds: [], deptId: undefined, teamIds: [], hireDate: '', remark: '', ...data.records[0] }
    dialogTitle.value = '新增用户'; dialogVisible.value = true
    aiChatStore.clearFillData()
    ElMessage.success('AI已填入用户表单，请确认后提交')
  }
})

// state
const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref(null)
const roleOptions = ref([])
const deptOptions = ref([])
const teamOptions = ref([])
const importDialogVisible = ref(false)
const importLoading = ref(false)
const importFile = ref(null)
const uploadRef = ref(null)

const queryParams = reactive({
  realName: '',
  phone: '',
  username: '',
  roleId: undefined,
  status: undefined,
  pageNum: 1,
  pageSize: 10
})

const formData = ref({
  id: undefined,
  username: '',
  realName: '',
  phone: '',
  icCardNo: '',
  password: '',
  roleIds: [],
  deptId: undefined,
  teamIds: [],
  hireDate: '',
  remark: ''
})

const formRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  phone: [
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度6-20位', trigger: 'blur' }
  ],
  roleIds: [{ required: true, message: '请选择角色', trigger: 'change' }]
}

// methods
async function loadData() {
  loading.value = true
  try {
    const res = await getUserPage(queryParams)
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } catch {
    // error handled by interceptor
  } finally {
    loading.value = false
  }
}

async function loadRoleOptions() {
  try {
    const res = await getRoleList()
    roleOptions.value = res.data || []
  } catch {
    // error handled by interceptor
  }
}

async function loadDeptOptions() {
  try {
    const res = await getDeptTree()
    deptOptions.value = res.data || []
  } catch {
    // error handled by interceptor
  }
}

async function loadTeamOptions() {
  try {
    const res = await getTeamList({})
    teamOptions.value = res.data || []
  } catch {
    // error handled by interceptor
  }
}

function handleSearch() {
  queryParams.pageNum = 1
  loadData()
}

function handleReset() {
  queryParams.realName = ''
  queryParams.phone = ''
  queryParams.username = ''
  queryParams.roleId = undefined
  queryParams.status = undefined
  queryParams.pageNum = 1
  queryParams.pageSize = 10
  loadData()
}

function handleAdd() {
  dialogTitle.value = '新增用户'
  formData.value = {
    id: undefined,
    username: '',
    realName: '',
    phone: '',
    icCardNo: '',
    password: '',
    roleIds: [],
    deptId: undefined,
    teamIds: [],
    hireDate: '',
    remark: ''
  }
  dialogVisible.value = true
}

function handleEdit(row) {
  dialogTitle.value = '编辑用户'
  formData.value = {
    id: row.id,
    username: row.username,
    realName: row.realName,
    phone: row.phone,
    icCardNo: row.icCardNo || '',
    password: '',
    roleIds: row.roleIds || [],
    deptId: row.deptId || undefined,
    teamIds: row.teamIds || [],
    hireDate: row.hireDate || '',
    remark: row.remark || ''
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  await formRef.value.validate()
  submitLoading.value = true
  try {
    if (formData.value.id) {
      await updateUser(formData.value.id, formData.value)
      ElMessage.success('更新成功')
    } else {
      await createUser(formData.value)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadData()
  } catch {
    // error handled by interceptor
  } finally {
    submitLoading.value = false
  }
}

function handleToggleStatus(row) {
  const newStatus = row.status === 1 ? 0 : 1
  const actionText = newStatus === 1 ? '启用' : '禁用'
  ElMessageBox.confirm(
    `确定${actionText}用户「${row.realName || row.username}」吗？`,
    '提示',
    { type: 'warning' }
  ).then(async () => {
    try {
      await updateUserStatus(row.id, newStatus)
      ElMessage.success(`${actionText}成功`)
      loadData()
    } catch {
      // error handled by interceptor
    }
  }).catch(() => {})
}

function handleResetPassword(row) {
  ElMessageBox.confirm(
    `确定重置用户「${row.realName || row.username}」的密码吗？`,
    '提示',
    { type: 'warning' }
  ).then(async () => {
    try {
      await resetPassword(row.id)
      ElMessage.success('密码重置成功')
    } catch {
      // error handled by interceptor
    }
  }).catch(() => {})
}

function handleDeleteUser(row) {
  ElMessageBox.confirm(`确定删除用户「${row.realName || row.username}」吗？删除后不可恢复。`, '提示', { type: 'warning' })
    .then(async () => {
      try {
        await deleteUser(row.id)
        ElMessage.success('删除成功')
        loadData()
      } catch {}
    }).catch(() => {})
}

function resetForm() {
  formRef.value?.resetFields()
}

function handleFileChange(file) {
  importFile.value = file.raw
}

async function handleExport() {
  try {
    const res = await exportUsers()
    const blob = new Blob([res], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = '用户列表.xlsx'
    link.click()
    window.URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (e) {
    ElMessage.error('导出失败')
  }
}

async function handleDownloadTemplate() {
  try {
    const res = await downloadUserTemplate()
    const blob = new Blob([res], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = '用户导入模板.xlsx'
    link.click()
    window.URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error('下载模板失败')
  }
}

async function handleImport() {
  if (!importFile.value) {
    ElMessage.warning('请选择文件')
    return
  }
  importLoading.value = true
  try {
    const res = await importUsers(importFile.value)
    ElMessage.success(res.data || '导入成功')
    importDialogVisible.value = false
    importFile.value = null
    loadData()
  } catch (e) {
    ElMessage.error(e.response?.data?.msg || '导入失败')
  } finally {
    importLoading.value = false
  }
}

// onMounted
onMounted(() => {
  loadData()
  loadRoleOptions()
  loadDeptOptions()
  loadTeamOptions()
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
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
