<template>
  <!-- 主布局容器：左侧边栏 + 右侧内容区 -->
  <el-container class="layout-container">
    <!-- 左侧边栏：根据折叠状态切换宽度 -->
    <el-aside :width="appStore.sidebar.collapsed ? '64px' : '220px'" class="layout-aside">
      <!-- Logo 区域 -->
      <div class="logo-container">
        <img src="/vite.svg" alt="logo" class="logo-img" />
        <span v-show="!appStore.sidebar.collapsed" class="logo-text">xxx</span>
      </div>
      <!-- 侧边栏菜单（支持滚动） -->
      <el-scrollbar>
        <SideMenu :menus="userStore.menus" :collapse="appStore.sidebar.collapsed" />
      </el-scrollbar>
    </el-aside>

    <!-- 右侧区域：顶部栏 + 标签页 + 主内容 -->
    <el-container>
      <!-- 顶部栏 -->
      <el-header class="layout-header">
        <!-- 左侧：折叠按钮 + 面包屑导航 -->
        <div class="header-left">
          <el-icon class="collapse-btn" @click="appStore.toggleSidebar">
            <Fold v-if="!appStore.sidebar.collapsed" />
            <Expand v-else />
          </el-icon>
          <!-- 面包屑：递归遍历菜单树，展示完整路径 -->
          <el-breadcrumb separator="/">
            <el-breadcrumb-item v-for="item in breadcrumbs" :key="item.path">
              {{ item.title }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <!-- 右侧：通知铃铛 + 用户下拉菜单 -->
        <div class="header-right">
          <!-- 未读通知徽标 -->
          <el-badge :value="unreadCount" :hidden="unreadCount === 0" class="notification-badge">
            <el-icon class="header-icon" @click="$router.push('/notification/list')"><Bell /></el-icon>
          </el-badge>
          <!-- 用户下拉菜单 -->
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-avatar :size="28" icon="UserFilled" />
              <span class="username">{{ userStore.userInfo?.realName || userStore.userInfo?.username }}</span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="password">修改密码</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 多页签导航栏 -->
      <TagsView />

      <!-- 主内容区域：每次导航重建组件，确保数据始终刷新 -->
      <el-main class="layout-main">
        <router-view :key="route.fullPath" />
      </el-main>
    </el-container>
  </el-container>

  <!-- AI聊天机器人 -->
  <AiChatBot />

  <!-- 修改密码弹窗 -->
  <el-dialog v-model="showPasswordDialog" title="修改密码" width="400px">
    <el-form :model="passwordForm" :rules="passwordRules" ref="passwordFormRef" label-width="80px">
      <el-form-item label="旧密码" prop="oldPassword">
        <el-input v-model="passwordForm.oldPassword" type="password" show-password />
      </el-form-item>
      <el-form-item label="新密码" prop="newPassword">
        <el-input v-model="passwordForm.newPassword" type="password" show-password />
      </el-form-item>
      <el-form-item label="确认密码" prop="confirmPassword">
        <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="showPasswordDialog = false">取消</el-button>
      <el-button type="primary" @click="doChangePassword">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import { useAppStore } from '@/store/app'
import { changePassword } from '@/api/auth'
import { getUnreadCount } from '@/api/system'
import { useCardReader } from '@/composables/useCardReader'
import { getWorkstationTasks } from '@/api/workstation'
import { resetRouter } from '@/router'
import SideMenu from './SideMenu.vue'
import TagsView from './TagsView.vue'
import AiChatBot from '@/components/AiChatBot.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()

/** 只展示 Web 端菜单，过滤 H5 专属菜单 */
const webMenus = computed(() => {
  function filterWeb(menus) {
    return menus
      .filter(m => m.platform !== 'h5')
      .map(m => ({ ...m, children: m.children?.length ? filterWeb(m.children) : [] }))
  }
  return filterWeb(userStore.menus || [])
})

/** 工位机刷卡监听：识别码匹配后跳转工位报工任务页 */
useCardReader(async ({ username }) => {
  try {
    const res = await getWorkstationTasks(username)
    const { realName, tasks } = res.data
    if (!tasks || tasks.length === 0) {
      ElMessage.info(`${realName || username} 暂无待报工任务`)
      return
    }
    // 如果已在工位任务页，刷新数据而非重复跳转
    if (route.path === '/workstation/task-list' && route.query.username === username) {
      router.replace({ path: '/workstation/task-list', query: { username, t: Date.now() } })
    } else {
      router.push({ path: '/workstation/task-list', query: { username } })
    }
  } catch (e) {
    ElMessage.warning(e.message || '刷卡识别失败')
  }
})

/** 未读通知数量 */
const unreadCount = ref(0)
/** 修改密码弹窗可见性 */
const showPasswordDialog = ref(false)
/** 密码表单引用 */
const passwordFormRef = ref(null)
/** 密码表单数据 */
const passwordForm = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })
/** 定时器 ID，用于组件卸载时清除 */
let unreadTimer = null

/** 修改密码表单校验规则 */
const passwordRules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度6-20位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== passwordForm.value.newPassword) {
          callback(new Error('两次密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

/**
 * 递归遍历菜单树，找到目标路径的完整祖先链
 */
function findMenuTrail(menus, targetPath, trail = []) {
  for (const menu of menus) {
    const current = [...trail, { title: menu.menuName, path: menu.path }]
    if (menu.path === targetPath) return current
    if (menu.children?.length) {
      const found = findMenuTrail(menu.children, targetPath, current)
      if (found) return found
    }
  }
  return null
}

/**
 * 面包屑：从菜单树中找到当前路由的完整路径层级
 */
const breadcrumbs = computed(() => {
  const path = '/' + route.path.replace(/^\//, '')
  const trail = findMenuTrail(userStore.menus || [], path)
  return trail || []
})

/**
 * 顶部下拉菜单命令处理
 */
function handleCommand(cmd) {
  if (cmd === 'logout') {
    ElMessageBox.confirm('确定退出登录？', '提示', { type: 'warning' }).then(() => {
      userStore.logout()
      resetRouter()
      router.push('/login')
    }).catch(() => {})
  } else if (cmd === 'password') {
    passwordForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
    showPasswordDialog.value = true
  }
}

/**
 * 提交修改密码
 */
async function doChangePassword() {
  await passwordFormRef.value.validate()
  await changePassword({
    oldPassword: passwordForm.value.oldPassword,
    newPassword: passwordForm.value.newPassword
  })
  ElMessage.success('密码修改成功，请重新登录')
  showPasswordDialog.value = false
  userStore.logout()
  resetRouter()
  router.push('/login')
}

/**
 * 获取未读通知数量
 */
async function fetchUnread() {
  try {
    const res = await getUnreadCount()
    unreadCount.value = res.data || 0
  } catch {
    // 静默处理，不影响主流程
  }
}

/** 组件挂载：获取未读数并启动定时轮询（每60秒） */
onMounted(() => {
  fetchUnread()
  unreadTimer = setInterval(fetchUnread, 60000)
})

/** 组件卸载：清除定时器，防止内存泄漏 */
onBeforeUnmount(() => {
  if (unreadTimer) {
    clearInterval(unreadTimer)
    unreadTimer = null
  }
})
</script>

<style scoped lang="scss">
.layout-container {
  height: 100vh;
}
.layout-aside {
  background: #001529;
  transition: width 0.3s;
  overflow: hidden;
}
.logo-container {
  height: 50px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 16px;
  .logo-img {
    width: 28px;
    height: 28px;
  }
  .logo-text {
    color: #fff;
    font-size: 18px;
    font-weight: bold;
    margin-left: 10px;
    white-space: nowrap;
  }
}
.layout-header {
  background: #fff;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #f0f0f0;
  padding: 0 20px;
  height: 50px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.collapse-btn {
  font-size: 20px;
  cursor: pointer;
  color: #333;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 20px;
}
.header-icon {
  font-size: 20px;
  cursor: pointer;
  color: #666;
}
.notification-badge {
  line-height: 1;
}
.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  .username {
    font-size: 14px;
    color: #333;
  }
}
.layout-main {
  background: #f5f5f5;
  padding: 16px;
  overflow-y: auto;
}
.fade-enter-active, .fade-leave-active {
  transition: opacity 0.2s;
}
.fade-enter-from, .fade-leave-to {
  opacity: 0;
}
</style>
