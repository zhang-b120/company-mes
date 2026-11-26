<template>
  <div class="page-container">
    <el-card shadow="never">
      <div class="search-bar">
        <el-input v-model="query.keyword" placeholder="班组名称" clearable style="width:140px" @keyup.enter="loadData" />
        <el-input v-model="query.workshop" placeholder="车间" clearable style="width:120px" @keyup.enter="loadData" />
        <el-button type="primary" @click="loadData">搜索</el-button>
        <el-button @click="resetQuery">重置</el-button>
        <el-button type="primary" plain @click="openDialog(null)">创建班组</el-button>
      </div>
    </el-card>

    <div v-loading="loading" style="margin-top:12px">
      <el-row :gutter="16" v-if="list.length">
        <el-col :xs="24" :sm="12" :md="8" :lg="6" v-for="item in list" :key="item.id" style="margin-bottom:16px">
          <el-card shadow="hover" class="team-card">
            <div class="team-card__header">
              <span class="team-card__name">{{ item.teamName }}</span>
              <el-tag size="small" type="success">{{ item.status === 'active' ? '启用' : '禁用' }}</el-tag>
            </div>
            <div class="team-card__body">
              <div class="team-card__row"><span class="team-card__label">负责人</span><span>{{ item.leaderName || '-' }}</span></div>
              <div class="team-card__row"><span class="team-card__label">成员数</span><span>{{ item.memberCount ?? 0 }} 人</span></div>
              <div class="team-card__row"><span class="team-card__label">当前状态</span><el-tag type="success" size="small">在岗</el-tag></div>
              <div class="team-card__row"><span class="team-card__label">创建时间</span><span>{{ item.createTime || '-' }}</span></div>
            </div>
            <div class="team-card__footer">
              <el-button link type="primary" @click="openMembers(item)">查看成员</el-button>
              <el-button link type="primary" @click="openDialog(item)">编辑</el-button>
              <el-button link type="danger" @click="handleDelete(item)">解散</el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>
      <el-empty v-else-if="!loading" description="暂无班组数据" />
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editId ? '编辑班组' : '新增班组'" width="500px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="80px">
        <el-form-item label="班组名称" prop="teamName">
          <el-input v-model="form.teamName" />
        </el-form-item>
        <el-form-item label="车间" prop="workshop">
          <el-input v-model="form.workshop" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-select v-model="form.leaderId" placeholder="请选择负责人" filterable clearable style="width:100%">
            <el-option v-for="user in allUsers" :key="user.id" :label="`${user.realName}（${user.username || '-'}）`" :value="user.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="生效日期">
          <el-date-picker v-model="form.effectiveDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 成员管理弹窗 -->
    <el-dialog v-model="memberVisible" :title="`${currentTeam?.teamName} - 成员管理`" width="600px">
      <div style="margin-bottom:12px">
        <el-button type="primary" size="small" @click="handleAddMember">添加成员</el-button>
        <el-button size="small" @click="equalizeWeights">一键均分权重</el-button>
      </div>
      <el-table :data="members" size="small">
        <el-table-column prop="userName" label="姓名">
          <template #default="{ row }">
            {{ row.userName }} <el-tag v-if="row.isLeader" size="small" type="warning" style="margin-left:4px">组长</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="weight" label="权重">
          <template #default="{ row }">
            <el-input-number v-model="row.weight" :min="0" :max="1" :step="0.01" :precision="4" size="small" style="width:120px" />
          </template>
        </el-table-column>
        <el-table-column prop="joinedAt" label="加入时间" />
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button link type="danger" size="small" @click="removeMember(row)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="memberVisible = false">关闭</el-button>
        <el-button type="primary" @click="saveWeights">保存权重</el-button>
      </template>
    </el-dialog>

    <!-- 添加成员弹窗 -->
    <el-dialog v-model="addDialogVisible" title="添加成员" width="500px" destroy-on-close append-to-body>
      <el-form label-width="80px">
        <el-form-item label="选择员工">
          <el-select v-model="selectedUserIds" placeholder="请选择要添加的员工" filterable multiple style="width:100%">
            <el-option v-for="user in availableUsers" :key="user.id" :label="`${user.realName}（${user.username || '-'}）`" :value="user.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="addLoading" :disabled="selectedUserIds.length === 0" @click="confirmAddMembers">确定添加</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getTeamList, createTeam, updateTeam, deleteTeam, getTeamMembers, addTeamMembers, removeTeamMember, saveTeamWeights } from '@/api/team'
import { useAiChatStore } from '@/store/ai-chat'

const aiChatStore = useAiChatStore()

// AI填入表单
watch(() => aiChatStore.pendingFillData, (data) => {
  if (data?.module === 'team' && data.records?.length) {
    editId.value = null
    form.value = { teamName: '', workshop: '', leaderId: null, effectiveDate: '', remark: '', ...data.records[0] }
    dialogVisible.value = true
    aiChatStore.clearFillData()
    ElMessage.success('AI已填入班组表单，请确认后提交')
  }
})

const loading = ref(false)
const list = ref([])
const query = ref({ keyword: '', workshop: '' })

const dialogVisible = ref(false)
const editId = ref(null)
const formRef = ref(null)
const form = ref({ teamName: '', workshop: '', leaderId: null, effectiveDate: '', remark: '' })
const allUsers = ref([])
const rules = {
  teamName: [{ required: true, message: '请输入班组名称', trigger: 'blur' }]
}

const memberVisible = ref(false)
const currentTeam = ref(null)
const members = ref([])
const addDialogVisible = ref(false)
const addLoading = ref(false)
const selectedUserIds = ref([])
const availableUsers = ref([])

async function loadData() {
  loading.value = true
  try {
    const res = await getTeamList(query.value)
    list.value = res.data || []
  } catch {} finally { loading.value = false }
}

function resetQuery() {
  query.value = { keyword: '', workshop: '' }
  loadData()
}

async function openDialog(row) {
  if (allUsers.value.length === 0) {
    try {
      const { getUserPage } = await import('@/api/system')
      const res = await getUserPage({ pageNum: 1, pageSize: 500 })
      allUsers.value = res.data?.records || res.data?.list || []
    } catch { allUsers.value = [] }
  }
  if (row) {
    editId.value = row.id
    form.value = { teamName: row.teamName, workshop: row.workshop, leaderId: row.leaderId, effectiveDate: row.effectiveDate, remark: row.remark }
  } else {
    editId.value = null
    form.value = { teamName: '', workshop: '', leaderId: null, effectiveDate: '', remark: '' }
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  await formRef.value.validate()
  if (editId.value) {
    await updateTeam(editId.value, form.value)
    ElMessage.success('修改成功')
  } else {
    await createTeam(form.value)
    ElMessage.success('新增成功')
  }
  dialogVisible.value = false
  loadData()
}

async function handleDelete(row) {
  await ElMessageBox.confirm('确定解散该班组？', '提示', { type: 'warning' })
  await deleteTeam(row.id)
  ElMessage.success('解散成功')
  loadData()
}

async function openMembers(row) {
  currentTeam.value = row
  try {
    const res = await getTeamMembers(row.id)
    members.value = res.data || []
  } catch {}
  memberVisible.value = true
}

async function removeMember(row) {
  await ElMessageBox.confirm('确定移除该成员？', '提示', { type: 'warning' })
  await removeTeamMember(currentTeam.value.id, row.id)
  ElMessage.success('已移除')
  openMembers(currentTeam.value)
}

function equalizeWeights() {
  const count = members.value.length
  if (count === 0) return
  const w = Math.floor(10000 / count) / 10000
  members.value.forEach((m, i) => {
    m.weight = i === count - 1 ? Number((1 - w * (count - 1)).toFixed(4)) : w
  })
}

async function saveWeights() {
  await saveTeamWeights(currentTeam.value.id, members.value.map(m => ({ memberId: m.id, weight: m.weight })))
  ElMessage.success('权重已保存')
  memberVisible.value = false
}

async function handleAddMember() {
  selectedUserIds.value = []
  try {
    const { getUserPage } = await import('@/api/system')
    const res = await getUserPage({ pageNum: 1, pageSize: 500 })
    const list = res.data?.records || res.data?.list || []
    const memberIds = members.value.map(m => m.userId || m.id)
    availableUsers.value = list.filter(u => !memberIds.includes(u.id))
  } catch {
    availableUsers.value = []
  }
  addDialogVisible.value = true
}

async function confirmAddMembers() {
  addLoading.value = true
  try {
    await addTeamMembers(currentTeam.value.id, selectedUserIds.value)
    ElMessage.success('成员添加成功')
    addDialogVisible.value = false
    openMembers(currentTeam.value)
  } catch {} finally {
    addLoading.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.team-card {
  border-radius: 8px;
  min-height: 180px;
  cursor: pointer;
}
.team-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid #ebeef5;
}
.team-card__name {
  font-size: 16px;
  font-weight: bold;
  padding-left: 10px;
  border-left: 4px solid #409eff;
  line-height: 1;
}
.team-card__body {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.team-card__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  color: #606266;
}
.team-card__label {
  color: #909399;
}
.team-card__footer {
  display: flex;
  justify-content: flex-end;
  gap: 4px;
  margin-top: 14px;
  padding-top: 10px;
  border-top: 1px solid #ebeef5;
}
</style>
