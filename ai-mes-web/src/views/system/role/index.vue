<template>
  <div class="role-page">
    <!-- 左侧：角色列表 -->
    <div class="role-list-panel">
      <el-card shadow="never" class="panel-card">
        <template #header>
          <div class="panel-header">
            <span class="panel-title">角色列表</span>
            <el-button v-permission="'sys:role:add'" type="primary" size="small" @click="handleAdd">新增</el-button>
          </div>
        </template>
        <el-input
          v-model="queryParams.keyword"
          placeholder="搜索角色名称/编码"
          clearable
          prefix-icon="Search"
          style="margin-bottom: 12px;"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <div v-loading="loading" class="role-items">
          <div
            v-for="role in tableData"
            :key="role.id"
            class="role-item"
            :class="{ active: currentRoleId === role.id }"
            @click="handleSelectRole(role)"
          >
            <div class="role-info">
              <div class="role-name">{{ role.roleName }}</div>
              <div class="role-code">{{ role.roleCode }}</div>
            </div>
            <div class="role-actions">
              <el-tag v-if="role.isPreset" type="warning" size="small">预设</el-tag>
              <el-tag v-else type="info" size="small">自定义</el-tag>
            </div>
          </div>
          <el-empty v-if="!loading && tableData.length === 0" description="暂无数据" :image-size="60" />
        </div>
        <div class="pagination-wrapper">
          <el-pagination
            v-model:current-page="queryParams.pageNum"
            v-model:page-size="queryParams.pageSize"
            :total="total"
            small
            :page-sizes="[10, 20, 50]"
            layout="total, prev, pager, next"
            @size-change="loadData"
            @current-change="loadData"
          />
        </div>
      </el-card>
    </div>

    <!-- 右侧：角色详情 + 权限设置 -->
    <div class="role-detail-panel">
      <!-- 角色信息卡片 -->
      <el-card shadow="never" class="panel-card" v-if="selectedRole">
        <template #header>
          <div class="panel-header">
            <span class="panel-title">角色信息</span>
            <div>
              <el-button type="primary" size="small" @click="handleEdit(selectedRole)">编辑</el-button>
              <el-button type="danger" size="small" :disabled="selectedRole.isPreset" @click="handleDelete(selectedRole)">删除</el-button>
            </div>
          </div>
        </template>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="角色名称">{{ selectedRole.roleName }}</el-descriptions-item>
          <el-descriptions-item label="角色编码">{{ selectedRole.roleCode }}</el-descriptions-item>
          <el-descriptions-item label="描述" :span="2">{{ selectedRole.description || '-' }}</el-descriptions-item>
          <el-descriptions-item label="类型">
            <el-tag :type="selectedRole.isPreset ? 'warning' : 'info'" size="small">
              {{ selectedRole.isPreset ? '预设角色' : '自定义角色' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="用户数">{{ selectedRole.userCount || 0 }}人</el-descriptions-item>
          <el-descriptions-item label="数据权限">
            <el-tag :type="dataScopeTagType(selectedRole.dataScope)" size="small">
              {{ dataScopeLabel(selectedRole.dataScope) }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 权限设置 Tabs -->
      <el-card shadow="never" class="panel-card perm-card" v-if="selectedRole">
        <template #header>
          <div class="panel-header">
            <span class="panel-title">权限设置</span>
          </div>
        </template>
        <el-tabs v-model="permTab">
          <el-tab-pane label="菜单权限" name="menu">
            <div class="tab-toolbar">
              <div style="display: flex; gap: 6px; align-items: center; flex: 1">
                <el-button size="small" @click="handleCheckAll">全选</el-button>
                <el-button size="small" @click="handleUncheckAll">全不选</el-button>
                <el-button size="small" @click="handleExpandAll">展开全部</el-button>
                <el-button size="small" @click="handleCollapseAll">折叠全部</el-button>
                <el-divider direction="vertical" />
                <el-radio-group v-model="platformFilter" size="small" @change="onPlatformFilterChange">
                  <el-radio-button value="">全部</el-radio-button>
                  <el-radio-button value="web">Web</el-radio-button>
                  <el-radio-button value="h5">H5</el-radio-button>
                </el-radio-group>
              </div>
              <el-button type="primary" size="small" :loading="permSubmitLoading" @click="handleSavePermission">保存菜单权限</el-button>
            </div>
            <div v-loading="permLoading" class="tree-wrapper">
              <el-tree
                ref="menuTreeRef"
                :data="filteredMenuTreeData"
                :props="{ label: 'menuName', children: 'children' }"
                show-checkbox
                node-key="id"
                default-expand-all
              >
                <template #default="{ data }">
                  <span class="tree-node-label">
                    <span>{{ data.menuName }}</span>
                    <el-tag v-if="data.platform === 'h5'" type="success" size="small" class="platform-tag">H5</el-tag>
                    <el-tag v-else-if="data.platform === 'web'" size="small" class="platform-tag">Web</el-tag>
                  </span>
                </template>
              </el-tree>
            </div>
          </el-tab-pane>
          <el-tab-pane label="数据权限" name="data">
            <div class="tab-toolbar">
              <el-button type="primary" size="small" :loading="dataScopeSubmitLoading" @click="handleSaveDataScope">保存数据权限</el-button>
            </div>
            <div v-loading="dataScopeLoading" class="data-scope-form">
              <el-form label-width="100px">
                <el-form-item label="数据范围">
                  <el-radio-group v-model="dataScopeForm.dataScope">
                    <el-radio value="all">全部数据</el-radio>
                    <el-radio value="dept">本部门数据</el-radio>
                    <el-radio value="self">仅本人数据</el-radio>
                    <el-radio value="custom">自定义</el-radio>
                  </el-radio-group>
                </el-form-item>
                <el-form-item label="说明">
                  <el-text type="info" size="small">
                    <template v-if="dataScopeForm.dataScope === 'all'">可以查看系统中所有数据</template>
                    <template v-else-if="dataScopeForm.dataScope === 'dept'">只能查看本人所在车间/部门的数据</template>
                    <template v-else-if="dataScopeForm.dataScope === 'self'">只能查看本人创建的数据</template>
                    <template v-else-if="dataScopeForm.dataScope === 'custom'">可以查看指定用户创建的数据</template>
                  </el-text>
                </el-form-item>
                <template v-if="dataScopeForm.dataScope === 'custom'">
                  <el-form-item label="可见用户">
                    <el-select
                      v-model="dataScopeForm.customUserIds"
                      multiple
                      filterable
                      placeholder="请选择可见用户"
                      style="width: 100%;"
                    >
                      <el-option v-for="u in userOptions" :key="u.id" :label="u.realName || u.username" :value="u.id" />
                    </el-select>
                  </el-form-item>
                </template>
              </el-form>
            </div>
          </el-tab-pane>
        </el-tabs>
      </el-card>

      <!-- 未选择角色 -->
      <el-card shadow="never" class="panel-card" v-if="!selectedRole">
        <el-empty description="请在左侧选择一个角色" :image-size="100" />
      </el-card>
    </div>

    <!-- add/edit dialog -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" destroy-on-close @close="resetForm">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="80px">
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="formData.roleName" placeholder="请输入角色名称" />
        </el-form-item>
        <el-form-item label="角色编码" prop="roleCode">
          <el-input v-model="formData.roleCode" placeholder="请输入角色编码" :disabled="!!formData.id" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="formData.description" type="textarea" :rows="3" placeholder="请输入描述" />
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="formData.sortOrder" :min="0" :max="9999" controls-position="right" />
        </el-form-item>
        <el-form-item label="数据权限" prop="dataScope">
          <el-select v-model="formData.dataScope" placeholder="请选择数据权限">
            <el-option label="全部数据" value="all" />
            <el-option label="本部门数据" value="dept" />
            <el-option label="仅本人数据" value="self" />
            <el-option label="自定义" value="custom" />
          </el-select>
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
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getRolePage,
  createRole,
  updateRole,
  deleteRole,
  getRoleMenus,
  saveRoleMenus,
  getMenuTree,
  getRoleDataScope,
  saveRoleDataScope,
  getUserSelect
} from '@/api/system'

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref(null)

const permLoading = ref(false)
const permSubmitLoading = ref(false)
const menuTreeRef = ref(null)
const menuTreeData = ref([])
const checkedMenuIds = ref([])
const currentRoleId = ref(null)
const selectedRole = ref(null)
const permTab = ref('menu')
const platformFilter = ref('')

// 按平台过滤菜单树
function filterTreeByPlatform(nodes, platform) {
  if (!platform) return nodes
  return nodes.reduce((acc, node) => {
    const nodeMatch = !node.platform || node.platform === platform || node.platform === 'both'
    const children = node.children?.length ? filterTreeByPlatform(node.children, platform) : []
    if (nodeMatch || children.length > 0) {
      acc.push({ ...node, children: children.length > 0 ? children : (node.children?.length ? [] : undefined) })
    }
    return acc
  }, [])
}

const filteredMenuTreeData = computed(() => {
  return filterTreeByPlatform(menuTreeData.value, platformFilter.value)
})

function onPlatformFilterChange() {
  // 切换筛选后，恢复当前角色的勾选状态
  nextTick(() => {
    menuTreeRef.value?.setCheckedKeys(checkedMenuIds.value)
  })
}

// 数据权限
const dataScopeLoading = ref(false)
const dataScopeSubmitLoading = ref(false)
const dataScopeForm = ref({ dataScope: 'all', customUserIds: [] })
const userOptions = ref([])

const queryParams = reactive({ keyword: '', pageNum: 1, pageSize: 20 })

const formData = ref({ id: undefined, roleName: '', roleCode: '', description: '', sortOrder: 0, dataScope: 'all' })

const formRules = {
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  roleCode: [{ required: true, message: '请输入角色编码', trigger: 'blur' }]
}

function dataScopeLabel(scope) {
  const map = { all: '全部数据', dept: '本部门', self: '仅本人', custom: '自定义' }
  return map[scope] || '全部数据'
}

function dataScopeTagType(scope) {
  const map = { all: '', dept: 'warning', self: 'info', custom: 'success' }
  return map[scope] || ''
}

async function loadData() {
  loading.value = true
  try {
    const res = await getRolePage(queryParams)
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } catch {} finally { loading.value = false }
}

function handleSearch() { queryParams.pageNum = 1; loadData() }

function handleSelectRole(role) {
  currentRoleId.value = role.id
  selectedRole.value = role
  permTab.value = 'menu'
  loadPermissions(role.id)
  loadDataScope(role.id)
}

async function loadPermissions(roleId) {
  permLoading.value = true
  try {
    // 先清空勾选，避免切换角色时残留上一个角色的勾选状态
    menuTreeRef.value?.setCheckedKeys([])
    // 菜单树是静态数据，只在首次加载时获取，切换角色时复用
    const requests = menuTreeData.value.length
      ? [Promise.resolve({ data: menuTreeData.value }), getRoleMenus(roleId)]
      : [getMenuTree(), getRoleMenus(roleId)]
    const [menuRes, roleMenuRes] = await Promise.all(requests)
    menuTreeData.value = menuRes.data || []
    const ids = roleMenuRes.data || []
    checkedMenuIds.value = ids
    // 等待树渲染完成后再设置勾选状态
    await nextTick()
    menuTreeRef.value?.setCheckedKeys(ids)
  } catch {} finally { permLoading.value = false }
}

function getAllTreeIds(tree) {
  const ids = []
  for (const node of tree) {
    ids.push(node.id)
    if (node.children?.length) ids.push(...getAllTreeIds(node.children))
  }
  return ids
}

// 构建 nodeId -> parentId 的映射，用于快速查找祖先节点
function buildParentMap(nodes, parentId = null, map = {}) {
  for (const node of nodes) {
    map[node.id] = parentId
    if (node.children?.length) buildParentMap(node.children, node.id, map)
  }
  return map
}

// 获取一组节点的所有祖先 ID（目录/菜单页），保证父级导航可访问
function getAncestorIds(nodeIds, tree) {
  const parentMap = buildParentMap(tree)
  const result = new Set()
  for (const id of nodeIds) {
    let pid = parentMap[id]
    while (pid !== null && pid !== undefined) {
      result.add(pid)
      pid = parentMap[pid]
    }
  }
  return [...result]
}

function handleCheckAll() {
  menuTreeRef.value?.setCheckedKeys(getAllTreeIds(menuTreeData.value))
}

function handleUncheckAll() {
  menuTreeRef.value?.setCheckedKeys([])
}

function handleExpandAll() {
  setTreeExpandAll(menuTreeRef.value?.store?.root, true)
}

function handleCollapseAll() {
  setTreeExpandAll(menuTreeRef.value?.store?.root, false)
}

function setTreeExpandAll(node, expanded) {
  if (!node) return
  node.expanded = expanded
  for (const child of node.childNodes || []) {
    setTreeExpandAll(child, expanded)
  }
}

async function loadDataScope(roleId) {
  dataScopeLoading.value = true
  try {
    const res = await getRoleDataScope(roleId)
    dataScopeForm.value = {
      dataScope: res.data.dataScope || 'all',
      customUserIds: (res.data.customUserIds || []).map(Number)
    }
  } catch {} finally { dataScopeLoading.value = false }
}

async function loadUserOptions() {
  try {
    const res = await getUserSelect()
    userOptions.value = res.data || []
  } catch {}
}

function handleAdd() {
  dialogTitle.value = '新增角色'
  formData.value = { id: undefined, roleName: '', roleCode: '', description: '', sortOrder: 0, dataScope: 'all' }
  dialogVisible.value = true
}

function handleEdit(row) {
  dialogTitle.value = '编辑角色'
  formData.value = { id: row.id, roleName: row.roleName, roleCode: row.roleCode, description: row.description || '', sortOrder: row.sortOrder || 0, dataScope: row.dataScope || 'all' }
  dialogVisible.value = true
}

async function handleSubmit() {
  await formRef.value.validate()
  submitLoading.value = true
  try {
    if (formData.value.id) {
      await updateRole(formData.value.id, formData.value)
      ElMessage.success('更新成功')
    } else {
      await createRole(formData.value)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadData()
  } catch {} finally { submitLoading.value = false }
}

function handleDelete(row) {
  if (row.isPreset) { ElMessage.warning('预设角色不允许删除'); return }
  ElMessageBox.confirm(`确定删除角色「${row.roleName}」吗？`, '提示', { type: 'warning' }).then(async () => {
    try { await deleteRole(row.id); ElMessage.success('删除成功'); if (currentRoleId.value === row.id) { selectedRole.value = null; currentRoleId.value = null }; loadData() } catch {}
  }).catch(() => {})
}

async function handleSavePermission() {
  const checkedKeys = menuTreeRef.value.getCheckedKeys()
  const halfCheckedKeys = menuTreeRef.value.getHalfCheckedKeys()
  // 确保用户勾选按钮权限时，其所在的父级页面和目录也被自动纳入权限，保证导航可达
  const ancestorIds = getAncestorIds(checkedKeys, menuTreeData.value)
  const menuIds = [...new Set([...checkedKeys, ...halfCheckedKeys, ...ancestorIds])]
  permSubmitLoading.value = true
  try {
    await saveRoleMenus(currentRoleId.value, menuIds)
    ElMessage.success('菜单权限设置成功')
    // 保存后重新拉一次后端真实数据回显, 防止 UI 与 DB 不同步
    await loadPermissions(currentRoleId.value)
  } catch {} finally { permSubmitLoading.value = false }
}

async function handleSaveDataScope() {
  dataScopeSubmitLoading.value = true
  try {
    await saveRoleDataScope(currentRoleId.value, dataScopeForm.value)
    ElMessage.success('数据权限设置成功')
    if (selectedRole.value) {
      selectedRole.value.dataScope = dataScopeForm.value.dataScope
    }
    loadData()
  } catch {} finally { dataScopeSubmitLoading.value = false }
}

function resetForm() { formRef.value?.resetFields() }

onMounted(() => {
  loadData()
  loadUserOptions()
})
</script>

<style scoped lang="scss">
.role-page {
  display: flex;
  gap: 16px;
  height: 100%;
}
.role-list-panel {
  width: 360px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
}
.role-detail-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}
.panel-card {
  :deep(.el-card__header) { padding: 12px 16px; }
  :deep(.el-card__body) { padding: 16px; }
}
.role-list-panel {
  > .panel-card {
    flex: 1;
    min-height: 0;
  }
}
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.panel-title { font-size: 15px; font-weight: 600; }
.role-items {
  max-height: calc(100vh - 320px);
  overflow-y: auto;
}
.role-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid transparent;
  margin-bottom: 4px;
  &:hover { background: #f5f7fa; }
  &.active {
    background: #ecf5ff;
    border-color: #409eff;
  }
}
.role-info {
  .role-name { font-size: 14px; font-weight: 500; color: #303133; }
  .role-code { font-size: 12px; color: #909399; margin-top: 2px; }
}
.pagination-wrapper {
  margin-top: 12px;
  display: flex;
  justify-content: center;
}
.perm-card {
  flex: 1;
  min-height: 0;
}
.tab-toolbar {
  margin-bottom: 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
.tree-wrapper {
  max-height: calc(100vh - 560px);
  overflow-y: auto;
}
.data-scope-form {
  max-height: calc(100vh - 560px);
  overflow-y: auto;
  padding-top: 8px;
}
.tree-node-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.platform-tag {
  margin-left: 2px;
  transform: scale(0.85);
}
</style>
