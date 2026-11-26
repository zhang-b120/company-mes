<template>
  <div class="page-container">
    <!-- search bar -->
    <el-card class="search-card" shadow="never">
      <el-form inline>
        <el-form-item>
          <el-button v-permission="'sys:menu:add'" type="primary" plain @click="handleAdd(null)">新增菜单</el-button>
          <el-button @click="toggleExpandAll">{{ isExpandAll ? '全部折叠' : '全部展开' }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- tree table -->
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
        <el-table-column prop="menuName" label="菜单名称" min-width="160" />
        <el-table-column prop="icon" label="图标" width="80" align="center">
          <template #default="{ row }">
            <el-icon v-if="row.icon"><component :is="row.icon" /></el-icon>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="menuType" label="类型" width="80" align="center">
          <template #default="{ row }">
            <el-tag
              :type="menuTypeTag(row.menuType)"
              size="small"
            >
              {{ menuTypeLabel(row.menuType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="platform" label="平台" width="80" align="center">
          <template #default="{ row }">
            {{ platformLabel(row.platform) }}
          </template>
        </el-table-column>
        <el-table-column prop="path" label="路由路径" min-width="140" show-overflow-tooltip />
        <el-table-column prop="component" label="组件路径" min-width="160" show-overflow-tooltip />
        <el-table-column prop="permission" label="权限标识" min-width="140" show-overflow-tooltip />
        <el-table-column prop="sortOrder" label="排序" width="70" align="center" />
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'sys:menu:edit'" type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button
              v-permission="'sys:menu:add'"
              v-if="row.menuType !== 'B'"
              type="primary"
              link
              size="small"
              @click="handleAdd(row)"
            >
              新增子项
            </el-button>
            <el-button v-permission="'sys:menu:delete'" type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="600px"
      destroy-on-close
      @close="resetForm"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="90px"
      >
        <el-form-item label="上级菜单" prop="parentId">
          <el-tree-select
            v-model="formData.parentId"
            :data="menuOptions"
            :props="{ label: 'menuName', value: 'id', children: 'children' }"
            placeholder="无（顶级菜单）"
            check-strictly
            clearable
            filterable
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="菜单类型" prop="menuType">
          <el-radio-group v-model="formData.menuType">
            <el-radio value="D">目录</el-radio>
            <el-radio value="M">菜单</el-radio>
            <el-radio value="B">按钮</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="菜单名称" prop="menuName">
          <el-input v-model="formData.menuName" placeholder="请输入菜单名称" />
        </el-form-item>
        <el-form-item label="平台" prop="platform">
          <el-select v-model="formData.platform" placeholder="请选择平台" style="width: 100%">
            <el-option label="Web端" value="web" />
            <el-option label="移动端" value="h5" />
            <el-option label="两端通用" value="both" />
          </el-select>
        </el-form-item>
        <el-form-item label="路由路径" prop="path">
          <el-input v-model="formData.path" placeholder="请输入路由路径" />
        </el-form-item>
        <el-form-item v-if="formData.menuType === 'M'" label="组件路径" prop="component">
          <el-input v-model="formData.component" placeholder="请输入组件路径，如 system/user/index" />
        </el-form-item>
        <el-form-item label="图标" prop="icon">
          <el-popover placement="bottom-start" :width="380" trigger="click">
            <template #reference>
              <el-input v-model="formData.icon" placeholder="点击选择图标" readonly>
                <template #prefix>
                  <el-icon v-if="formData.icon"><component :is="formData.icon" /></el-icon>
                </template>
                <template #suffix>
                  <el-icon v-if="formData.icon" class="icon-clear" @click.stop="formData.icon = ''"><CircleClose /></el-icon>
                </template>
              </el-input>
            </template>
            <el-input v-model="iconSearch" placeholder="搜索图标" clearable size="small" style="margin-bottom: 8px" />
            <el-scrollbar height="240px">
              <div class="icon-picker-grid">
                <div
                  v-for="name in filteredIcons"
                  :key="name"
                  class="icon-picker-item"
                  :class="{ active: formData.icon === name }"
                  @click="formData.icon = name"
                  :title="name"
                >
                  <el-icon :size="20"><component :is="name" /></el-icon>
                  <span class="icon-name">{{ name }}</span>
                </div>
              </div>
            </el-scrollbar>
          </el-popover>
        </el-form-item>
        <el-form-item v-if="formData.menuType === 'B'" label="权限标识" prop="permission">
          <el-input v-model="formData.permission" placeholder="请输入权限标识，如 system:user:add" />
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="formData.sortOrder" :min="0" :max="9999" controls-position="right" />
        </el-form-item>
        <el-form-item label="显示状态" prop="visible">
          <el-radio-group v-model="formData.visible">
            <el-radio :value="1">显示</el-radio>
            <el-radio :value="0">隐藏</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="菜单状态" prop="status">
          <el-radio-group v-model="formData.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
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
import { ref, reactive, computed, nextTick, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CircleClose } from '@element-plus/icons-vue'
import {
  getMenuTree,
  getMenuOptions,
  createMenu,
  updateMenu,
  deleteMenu
} from '@/api/system'

// state
const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref(null)
const menuOptions = ref([])
const isExpandAll = ref(false)
const refreshTable = ref(true)

const formData = ref({
  id: undefined,
  parentId: undefined,
  menuName: '',
  menuType: 'D',
  platform: 'web',
  path: '',
  component: '',
  icon: '',
  permission: '',
  sortOrder: 0,
  visible: 1,
  status: 1
})

const formRules = computed(() => ({
  menuName: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
  menuType: [{ required: true, message: '请选择菜单类型', trigger: 'change' }],
  platform: [{ required: true, message: '请选择平台', trigger: 'change' }],
  path: formData.value.menuType !== 'B'
    ? [{ required: true, message: '请输入路由路径', trigger: 'blur' }]
    : [],
  permission: formData.value.menuType === 'B'
    ? [{ required: true, message: '请输入权限标识', trigger: 'blur' }]
    : []
}))

// 图标选择器
const iconSearch = ref('')
const iconList = [
  'House', 'Setting', 'User', 'UserFilled', 'Lock', 'Key', 'Menu',
  'Document', 'DocumentCopy', 'Folder', 'FolderOpened', 'Files',
  'List', 'Grid', 'DataLine', 'DataAnalysis', 'DataBoard', 'TrendCharts',
  'Edit', 'EditPen', 'Delete', 'Plus', 'Minus', 'Close', 'Check',
  'Search', 'Refresh', 'Upload', 'Download', 'Share',
  'ShoppingCart', 'Goods', 'Box', 'Present', 'Money', 'Wallet', 'CreditCard', 'Coin',
  'Calendar', 'Clock', 'Timer', 'AlarmClock',
  'Bell', 'Message', 'ChatDotRound', 'Comment', 'Notification',
  'Star', 'StarFilled', 'Flag', 'Trophy', 'Medal',
  'Location', 'MapLocation', 'OfficeBuilding', 'School', 'Shop',
  'Phone', 'PhoneFilled', 'Cellphone', 'Monitor', 'Printer',
  'Tools', 'Wrench', 'Hammer', 'Brush', 'Scissors', 'MagicStick',
  'Connection', 'Link', 'Paperclip', 'Stamp',
  'PieChart', 'Histogram', 'Reading', 'Notebook',
  'VideoCamera', 'Camera', 'Picture', 'PictureFilled',
  'Sunny', 'Moon', 'Cloudy', 'Lightning',
  'Van', 'Ship', 'Bicycle',
  'CircleCheck', 'CircleCheckFilled', 'CircleClose', 'CircleCloseFilled',
  'WarningFilled', 'Warning', 'InfoFilled', 'QuestionFilled',
  'SuccessFilled', 'Help', 'Promotion', 'Opportunity',
  'Loading', 'View', 'Hide', 'FullScreen',
  'ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown',
  'DArrowLeft', 'DArrowRight', 'Sort', 'SortUp', 'SortDown', 'Rank',
  'Management', 'Operation', 'Platform', 'Service',
  'Cpu', 'Odometer', 'Stopwatch', 'SetUp',
  'SwitchButton', 'TurnOff', 'Open', 'Expand', 'Fold'
]
const filteredIcons = computed(() => {
  if (!iconSearch.value) return iconList
  const kw = iconSearch.value.toLowerCase()
  return iconList.filter(n => n.toLowerCase().includes(kw))
})

// helpers
function menuTypeLabel(type) {
  const map = { D: '目录', M: '菜单', B: '按钮' }
  return map[type] || type
}

function menuTypeTag(type) {
  const map = { D: 'primary', M: 'success', B: 'warning' }
  return map[type] || 'info'
}

function platformLabel(val) {
  const map = { web: 'Web', h5: 'H5', both: '通用' }
  return map[val] || val || '-'
}

// methods
async function loadData() {
  loading.value = true
  try {
    const res = await getMenuTree()
    tableData.value = res.data || []
  } catch {
    // error handled by interceptor
  } finally {
    loading.value = false
  }
}

async function loadMenuOptions() {
  try {
    const res = await getMenuOptions()
    menuOptions.value = res.data || []
  } catch {
    // error handled by interceptor
  }
}

function toggleExpandAll() {
  isExpandAll.value = !isExpandAll.value
  refreshTable.value = false
  nextTick(() => {
    refreshTable.value = true
  })
}

function getDefaultFormData() {
  return {
    id: undefined,
    parentId: undefined,
    menuName: '',
    menuType: 'D',
    platform: 'web',
    path: '',
    component: '',
    icon: '',
    permission: '',
    sortOrder: 0,
    visible: 1,
    status: 1
  }
}

function handleAdd(row) {
  dialogTitle.value = row ? '新增子项' : '新增菜单'
  formData.value = getDefaultFormData()
  if (row) {
    formData.value.parentId = row.id
  }
  loadMenuOptions()
  dialogVisible.value = true
}

function handleEdit(row) {
  dialogTitle.value = '编辑菜单'
  formData.value = {
    id: row.id,
    parentId: row.parentId || undefined,
    menuName: row.menuName,
    menuType: row.menuType,
    platform: row.platform || 'web',
    path: row.path || '',
    component: row.component || '',
    icon: row.icon || '',
    permission: row.permission || '',
    sortOrder: row.sortOrder || 0,
    visible: row.visible ?? 1,
    status: row.status ?? 1
  }
  loadMenuOptions()
  dialogVisible.value = true
}

async function handleSubmit() {
  await formRef.value.validate()
  submitLoading.value = true
  try {
    if (formData.value.id) {
      await updateMenu(formData.value.id, formData.value)
      ElMessage.success('更新成功')
    } else {
      await createMenu(formData.value)
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

function handleDelete(row) {
  ElMessageBox.confirm(
    `确定删除菜单「${row.menuName}」吗？删除后子菜单也将被删除。`,
    '提示',
    { type: 'warning' }
  ).then(async () => {
    try {
      await deleteMenu(row.id)
      ElMessage.success('删除成功')
      loadData()
    } catch {
      // error handled by interceptor
    }
  }).catch(() => {})
}

function resetForm() {
  formRef.value?.resetFields()
}

// onMounted
onMounted(() => {
  loadData()
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
.icon-clear {
  cursor: pointer;
  &:hover { color: #f56c6c; }
}
.icon-picker-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 4px;
}
.icon-picker-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 6px 2px;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
  &:hover { background: #f5f7fa; }
  &.active { background: #ecf5ff; color: #409eff; }
  .icon-name { font-size: 10px; color: #909399; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 56px; }
}
</style>
