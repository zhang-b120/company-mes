<!--
  班组成员管理页面
  功能：
  1. 班组信息卡片 — 展示当前班组的基本信息（名称、车间、组长等）
  2. 成员表格 — 展示成员列表，支持权重编辑
  3. 操作按钮 — 添加成员、均分权重、保存权重
-->
<template>
  <div class="page-container">
    <!-- 返回按钮 -->
    <div class="page-header">
      <el-button @click="goBack">
        <el-icon><ArrowLeft /></el-icon>返回班组列表
      </el-button>
    </div>

    <!-- 班组信息卡片 -->
    <el-card shadow="never" class="team-info-card">
      <template #header>
        <div class="card-header">
          <span>班组信息</span>
          <el-tag :type="teamInfo.status === 'active' ? 'success' : 'danger'" v-if="teamInfo.status">
            {{ teamInfo.status === 'active' ? '启用' : '禁用' }}
          </el-tag>
        </div>
      </template>
      <el-descriptions v-if="teamInfo.id" :column="4" border size="small">
        <el-descriptions-item label="班组名称">{{ teamInfo.teamName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="车间">{{ teamInfo.workshop || '-' }}</el-descriptions-item>
        <el-descriptions-item label="负责人">{{ teamInfo.leaderName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="成员数量">{{ members.length }}人</el-descriptions-item>
        <el-descriptions-item label="生效日期">{{ teamInfo.effectiveDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ teamInfo.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-empty v-else description="加载中..." :image-size="60" />
    </el-card>

    <!-- 成员管理 -->
    <el-card shadow="never" style="margin-top: 12px">
      <template #header>
        <div class="card-header">
          <span>成员列表</span>
          <div class="card-header__actions">
            <el-button type="primary" size="small" @click="handleAddMember">添加成员</el-button>
            <el-button size="small" @click="handleEqualizeWeights">均分权重</el-button>
            <el-button type="success" size="small" :loading="saveLoading" @click="handleSaveWeights">保存权重</el-button>
          </div>
        </div>
      </template>

      <!-- 权重合计提示 -->
      <el-alert
        v-if="members.length > 0"
        :title="`当前权重合计: ${weightSum.toFixed(4)}${Math.abs(weightSum - 1) > 0.001 ? '（建议调整为1.0）' : ''}`"
        :type="Math.abs(weightSum - 1) > 0.001 ? 'warning' : 'success'"
        show-icon
        :closable="false"
        style="margin-bottom: 12px"
      />

      <el-table v-loading="loading" :data="members" border stripe>
        <el-table-column type="index" label="#" width="60" align="center" />
        <el-table-column prop="userName" label="姓名" min-width="120">
          <template #default="{ row }">
            {{ row.userName }} <el-tag v-if="row.isLeader" size="small" type="warning" style="margin-left:4px">组长</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="employeeNo" label="工号" min-width="120" />
        <el-table-column prop="position" label="岗位" min-width="120" />
        <el-table-column prop="phone" label="联系电话" min-width="130" />
        <el-table-column label="权重" width="180">
          <template #default="{ row }">
            <el-input-number
              v-model="row.weight"
              :min="0"
              :max="1"
              :step="0.01"
              :precision="4"
              size="small"
              style="width: 140px"
            />
          </template>
        </el-table-column>
        <el-table-column prop="joinedAt" label="加入时间" width="170" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="danger" size="small" @click="handleRemoveMember(row)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && members.length === 0" description="暂无成员，请点击'添加成员'按钮" />
    </el-card>

    <!-- 添加成员弹窗 -->
    <el-dialog v-model="addDialogVisible" title="添加成员" width="500px" destroy-on-close>
      <el-form label-width="80px">
        <el-form-item label="选择员工">
          <el-select
            v-model="selectedUserIds"
            placeholder="请选择要添加的员工"
            filterable
            multiple
            style="width: 100%"
          >
            <el-option
              v-for="user in availableUsers"
              :key="user.id"
              :label="`${user.realName}（${user.username || '-'}）`"
              :value="user.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="addLoading"
          :disabled="selectedUserIds.length === 0"
          @click="confirmAddMembers"
        >
          确定添加
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import {
  getTeamList,
  getTeamMembers,
  addTeamMembers,
  removeTeamMember,
  saveTeamWeights
} from '@/api/team'

const route = useRoute()
const router = useRouter()

// 从路由参数获取班组ID（支持 query 和 params 两种方式）
const teamId = computed(() => route.query.teamId || route.params.teamId)

// ==================== 班组信息 ====================
const teamInfo = ref({})

/** 加载班组信息 */
async function loadTeamInfo() {
  try {
    const res = await getTeamList({ id: teamId.value })
    // 从列表中找到当前班组
    const list = res.data || []
    teamInfo.value = list.find(t => String(t.id) === String(teamId.value)) || list[0] || {}
  } catch {
    // 由拦截器统一处理
  }
}

// ==================== 成员列表 ====================
const loading = ref(false)
const members = ref([])

/** 权重合计 */
const weightSum = computed(() => {
  return members.value.reduce((sum, m) => sum + (m.weight || 0), 0)
})

/** 加载成员列表 */
async function loadMembers() {
  if (!teamId.value) return
  loading.value = true
  try {
    const res = await getTeamMembers(teamId.value)
    members.value = (res.data || []).map(m => ({
      ...m,
      weight: m.weight ?? 0
    }))
  } catch {
    // 由拦截器统一处理
  } finally {
    loading.value = false
  }
}

// ==================== 均分权重 ====================
/** 一键均分所有成员权重 */
function handleEqualizeWeights() {
  const count = members.value.length
  if (count === 0) {
    ElMessage.warning('暂无成员，无法均分权重')
    return
  }
  const w = Math.floor(10000 / count) / 10000
  members.value.forEach((m, i) => {
    // 最后一个成员补齐误差，确保合计为 1
    m.weight = i === count - 1 ? Number((1 - w * (count - 1)).toFixed(4)) : w
  })
  ElMessage.success('权重已均分')
}

// ==================== 保存权重 ====================
const saveLoading = ref(false)

/** 保存所有成员的权重设置 */
async function handleSaveWeights() {
  if (members.value.length === 0) {
    ElMessage.warning('暂无成员')
    return
  }
  // 校验权重合计
  if (Math.abs(weightSum.value - 1) > 0.01) {
    await ElMessageBox.confirm(
      `当前权重合计为 ${weightSum.value.toFixed(4)}，不等于1.0，是否仍要保存？`,
      '提示',
      { type: 'warning' }
    )
  }
  saveLoading.value = true
  try {
    const weights = members.value.map(m => ({ memberId: m.id, weight: m.weight }))
    await saveTeamWeights(teamId.value, weights)
    ElMessage.success('权重保存成功')
  } catch {
    // 由拦截器统一处理
  } finally {
    saveLoading.value = false
  }
}

// ==================== 添加成员 ====================
const addDialogVisible = ref(false)
const addLoading = ref(false)
const selectedUserIds = ref([])
const availableUsers = ref([])

/** 打开添加成员弹窗 */
async function handleAddMember() {
  selectedUserIds.value = []
  // 加载可用员工列表
  try {
    const { getUserPage } = await import('@/api/system')
    const res = await getUserPage({ pageNum: 1, pageSize: 500 })
    const list = res.data?.records || res.data?.list || []
    const memberIds = members.value.map(m => m.userId || m.id)
    // 过滤掉已经是成员的用户
    availableUsers.value = list.filter(u => !memberIds.includes(u.id))
  } catch {
    availableUsers.value = []
  }
  addDialogVisible.value = true
}

/** 确认添加成员 */
async function confirmAddMembers() {
  addLoading.value = true
  try {
    await addTeamMembers(teamId.value, selectedUserIds.value)
    ElMessage.success('成员添加成功')
    addDialogVisible.value = false
    loadMembers()
  } catch {
    // 由拦截器统一处理
  } finally {
    addLoading.value = false
  }
}

// ==================== 移除成员 ====================
/** 移除指定成员 */
async function handleRemoveMember(row) {
  await ElMessageBox.confirm(
    `确定移除成员「${row.userName}」吗？`,
    '提示',
    { type: 'warning' }
  )
  try {
    await removeTeamMember(teamId.value, row.id)
    ElMessage.success('成员已移除')
    loadMembers()
  } catch {
    // 由拦截器统一处理
  }
}

// ==================== 导航 ====================
/** 返回班组列表 */
function goBack() {
  router.push('/team')
}

// ==================== 初始化 ====================
onMounted(() => {
  if (teamId.value) {
    loadTeamInfo()
    loadMembers()
  } else {
    ElMessage.warning('缺少班组ID参数')
  }
})
</script>

<style scoped lang="scss">
.page-container {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.page-header {
  margin-bottom: 12px;
}

.team-info-card {
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;

  &__actions {
    display: flex;
    gap: 8px;
  }
}
</style>
