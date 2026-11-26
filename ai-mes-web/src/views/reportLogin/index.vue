<template>
  <div class="report-login-screen" :class="wsStore.theme">
    <!-- 顶部标题栏 -->
    <header class="rl-header">
      <div class="rl-header-left">
        <span class="rl-logo">AI-MES</span>
        <span class="rl-subtitle">工位报工系统</span>
      </div>
      <div class="rl-header-center">
        <div class="rl-time">{{ currentTime }}</div>
        <div class="rl-date">{{ currentDate }}</div>
      </div>
      <div class="rl-header-right">
        <div class="theme-switch" @click="wsStore.toggleTheme()" :title="wsStore.isDark ? '切换浅色' : '切换深色'">
          <span class="theme-icon">{{ wsStore.isDark ? '🌙' : '☀️' }}</span>
          <div class="theme-track" :class="{ 'is-light': !wsStore.isDark }">
            <div class="theme-thumb"></div>
          </div>
        </div>
      </div>
    </header>

    <!-- 主内容 -->
    <main class="rl-main">
      <!-- 左侧提示区 -->
      <div class="rl-hint-panel">
        <div class="rl-hint-icon">🪪</div>
        <div class="rl-hint-title">员工刷卡登录</div>
        <div class="rl-hint-desc">请将工卡靠近读卡器<br />系统将自动识别并登录</div>
        <div class="rl-hint-or">或手动输入账号</div>
        <!-- 刷卡动画指示 -->
        <div class="rl-card-wave" :class="{ 'wave-active': cardDetected }">
          <span></span><span></span><span></span>
        </div>
        <div v-if="cardDetected" class="rl-card-tip">✓ 已检测到刷卡</div>
      </div>

      <!-- 右侧登录表单 -->
      <div class="rl-form-panel">
        <div class="rl-form-title">员工登录</div>

        <div class="rl-form-item">
          <label class="rl-label">账号</label>
          <input
            ref="usernameRef"
            v-model="form.username"
            class="rl-input"
            placeholder="请输入员工账号"
            autocomplete="off"
            @keyup.enter="handleLogin"
          />
        </div>

        <div v-if="errMsg" class="rl-error">{{ errMsg }}</div>

        <button class="rl-btn" :class="{ 'is-loading': loading }" @click="handleLogin" :disabled="loading">
          <span v-if="loading" class="btn-spinner"></span>
          <span>{{ loading ? '登录中...' : '登 录' }}</span>
        </button>
      </div>
    </main>

    <!-- 底部信息 -->
    <footer class="rl-footer">
      <span>AI-MES 智造云工单</span>
      <span class="rl-footer-sep">|</span>
      <span>工位报工终端</span>
    </footer>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { useWorkstationStore } from '@/store/workstation'
import { useCardReader } from '@/composables/useCardReader'
import { cardLogin, cardLoginByUsername } from '@/api/auth'
import { setToken, setRefreshToken } from '@/utils/auth'

const router = useRouter()
const userStore = useUserStore()
const wsStore = useWorkstationStore()

// ===== 时钟 =====
const currentTime = ref('')
const currentDate = ref('')
let clockTimer = null

function updateClock() {
  const now = new Date()
  const pad = n => String(n).padStart(2, '0')
  currentTime.value = `${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
  const days = ['日', '一', '二', '三', '四', '五', '六']
  currentDate.value = `${now.getFullYear()}年${now.getMonth() + 1}月${now.getDate()}日 星期${days[now.getDay()]}`
}

// ===== 表单 =====
const form = ref({ username: '' })
const loading = ref(false)
const errMsg = ref('')
const cardDetected = ref(false)

/**
 * 登录成功后的统一处理：保存工位信息 + 跳转
 * 用户信息由后端返回，前端只负责落库 + 跳转
 */
function onLoginSuccess(loginData) {
  setToken(loginData.accessToken)
  setRefreshToken(loginData.refreshToken)
  userStore.token = loginData.accessToken
  userStore.refreshTokenValue = loginData.refreshToken
  userStore.userInfo = loginData.userInfo

  const username = loginData.userInfo?.username
  wsStore.setUserInfo({
    userId: loginData.userInfo?.id,
    username,
    realName: loginData.userInfo?.realName || username
  })

  router.push({ path: '/workstation/task-list', query: { username } })
}

/** 手动登录（员工账号免密） */
async function handleLogin() {
  if (!form.value.username) {
    errMsg.value = '请输入账号'
    return
  }
  errMsg.value = ''
  loading.value = true
  try {
    const res = await cardLoginByUsername(form.value.username)
    onLoginSuccess(res.data)
  } catch (e) {
    errMsg.value = e.message || '登录失败'
  } finally {
    loading.value = false
  }
}

/** 刷卡登录（IC卡号免密） */
async function doCardLogin(icCardNo) {
  errMsg.value = ''
  loading.value = true
  try {
    const res = await cardLogin(icCardNo)
    onLoginSuccess(res.data)
  } catch (e) {
    errMsg.value = e.message || '登录失败'
  } finally {
    loading.value = false
  }
}

// ===== 刷卡监听 =====
useCardReader(({ icCardNo }) => {
  cardDetected.value = true
  setTimeout(() => {
    cardDetected.value = false
    doCardLogin(icCardNo)
  }, 600)
})

onMounted(() => {
  updateClock()
  clockTimer = setInterval(updateClock, 1000)
  // 清空上一次工位机登录状态
  wsStore.clear()
})

onBeforeUnmount(() => {
  clearInterval(clockTimer)
})
</script>

<style scoped lang="scss">
.report-login-screen {
  width: 100vw;
  height: 100vh;
  background: #0a0f1e;
  color: #e8eaf0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
  user-select: none;
}

// ===== 顶部 =====
.rl-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 40px;
  background: linear-gradient(135deg, #0d1829 0%, #112040 100%);
  border-bottom: 1px solid #1e3a5f;
  flex-shrink: 0;
}
.rl-header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.rl-logo {
  font-size: 22px;
  font-weight: 800;
  color: #40a0ff;
  letter-spacing: 2px;
}
.rl-subtitle {
  font-size: 14px;
  color: #6b8cb0;
}
.rl-header-center {
  text-align: center;
}
.rl-time {
  font-size: 38px;
  font-weight: 700;
  color: #fff;
  letter-spacing: 4px;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}
.rl-date {
  font-size: 13px;
  color: #6b8cb0;
  margin-top: 4px;
}
.rl-header-right {
  width: 180px;
}

// ===== 主内容 =====
.rl-main {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 80px;
  padding: 40px;
}

// ===== 左侧提示区 =====
.rl-hint-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  min-width: 260px;
}
.rl-hint-icon {
  font-size: 72px;
  filter: drop-shadow(0 0 20px rgba(64, 160, 255, 0.5));
}
.rl-hint-title {
  font-size: 24px;
  font-weight: 700;
  color: #c8d8f0;
}
.rl-hint-desc {
  font-size: 15px;
  color: #6b8cb0;
  text-align: center;
  line-height: 1.7;
}
.rl-hint-or {
  font-size: 13px;
  color: #3a5070;
  margin-top: 4px;
}

// 刷卡波纹动画
.rl-card-wave {
  display: flex;
  gap: 6px;
  align-items: center;
  height: 30px;

  span {
    display: block;
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: #1e3a5f;
    transition: background 0.3s;
  }

  &.wave-active span {
    background: #40a0ff;
    &:nth-child(1) { animation: wavePulse 0.6s ease 0s infinite; }
    &:nth-child(2) { animation: wavePulse 0.6s ease 0.15s infinite; }
    &:nth-child(3) { animation: wavePulse 0.6s ease 0.3s infinite; }
  }
}
@keyframes wavePulse {
  0%, 100% { transform: scale(1); opacity: 0.5; }
  50% { transform: scale(1.8); opacity: 1; }
}
.rl-card-tip {
  font-size: 14px;
  color: #40c080;
  font-weight: 600;
  letter-spacing: 1px;
  animation: fadeIn 0.3s ease;
}
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(4px); }
  to { opacity: 1; transform: translateY(0); }
}

// ===== 右侧登录表单 =====
.rl-form-panel {
  background: #0d1829;
  border: 1px solid #1e3a5f;
  border-radius: 12px;
  padding: 40px 44px;
  min-width: 360px;
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.4);
}
.rl-form-title {
  font-size: 22px;
  font-weight: 700;
  color: #c8d8f0;
  margin-bottom: 32px;
  text-align: center;
}
.rl-form-item {
  margin-bottom: 20px;
}
.rl-label {
  display: block;
  font-size: 13px;
  color: #6b8cb0;
  margin-bottom: 8px;
}
.rl-input {
  width: 100%;
  height: 46px;
  background: #0a1525;
  border: 1px solid #1e3a5f;
  border-radius: 8px;
  color: #e8eaf0;
  font-size: 16px;
  padding: 0 16px;
  outline: none;
  box-sizing: border-box;
  transition: border-color 0.2s;

  &::placeholder { color: #2a4a6a; }
  &:focus { border-color: #40a0ff; }
}
.rl-error {
  color: #ff6b6b;
  font-size: 13px;
  margin-bottom: 12px;
  padding: 8px 12px;
  background: #200a0a;
  border-radius: 6px;
  border-left: 3px solid #ff4d4f;
}
.rl-btn {
  width: 100%;
  height: 48px;
  margin-top: 8px;
  background: linear-gradient(135deg, #1a5fa0, #40a0ff);
  border: none;
  border-radius: 8px;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 4px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  transition: opacity 0.2s;

  &:hover:not(:disabled) { opacity: 0.9; }
  &:disabled { opacity: 0.6; cursor: not-allowed; }
  &.is-loading { opacity: 0.8; }
}
.btn-spinner {
  width: 18px;
  height: 18px;
  border: 2px solid rgba(255,255,255,0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

// ===== 底部 =====
.rl-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 8px 32px;
  background: #0d1829;
  border-top: 1px solid #1e3a5f;
  font-size: 12px;
  color: #3a5070;
  flex-shrink: 0;
}
.rl-footer-sep { color: #1e3a5f; }

// 主题开关
.theme-switch {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  user-select: none;
}
.theme-icon { font-size: 18px; }
.theme-track {
  width: 40px;
  height: 22px;
  background: #1e3a5f;
  border-radius: 11px;
  position: relative;
  transition: background 0.3s;

  .theme-thumb {
    position: absolute;
    top: 3px;
    left: 3px;
    width: 16px;
    height: 16px;
    background: #e8eaf0;
    border-radius: 50%;
    transition: transform 0.3s;
  }

  &.is-light {
    background: #a0c8f0;
    .theme-thumb { transform: translateX(18px); background: #fff; }
  }
}

// ===== 浅色主题覆盖 =====
.report-login-screen.light {
  background: linear-gradient(135deg, #e8f4fd 0%, #f0f6ff 40%, #ffffff 100%);
  color: #1a1a2e;

  .rl-header {
    background: linear-gradient(135deg, #ffffff, #eaf3fc);
    border-bottom-color: #d0dfe8;
  }
  .rl-logo { color: #1a6fba; }
  .rl-subtitle { color: #5a7a9a; }
  .rl-time { color: #1a1a2e; }
  .rl-date { color: #5a7a9a; }
  .theme-track { background: #c0d8f0; }

  .rl-hint-title { color: #1a1a2e; }
  .rl-hint-desc { color: #5a7a9a; }
  .rl-hint-or { color: #8a9ab0; }
  .rl-hint-icon { filter: none; }
  .rl-card-wave span { background: #c0d8f0; }
  .rl-card-wave.wave-active span { background: #1a6fba; }
  .rl-card-tip { color: #1a8a4a; }

  .rl-form-panel {
    background: #ffffff;
    border-color: #d0dfe8;
    box-shadow: 0 8px 40px rgba(0, 60, 120, 0.08);
  }
  .rl-form-title { color: #1a1a2e; }
  .rl-label { color: #5a7a9a; }
  .rl-input {
    background: #f5f9ff;
    border-color: #d0dfe8;
    color: #1a1a2e;
    &::placeholder { color: #a0b8d0; }
    &:focus { border-color: #1a6fba; }
  }
  .rl-error {
    background: #fff0f0;
    color: #d04040;
    border-color: #d04040;
  }
  .rl-btn {
    background: linear-gradient(135deg, #1a6fba, #3a90e0);
  }

  .rl-footer {
    background: #ffffff;
    border-top-color: #d0dfe8;
    color: #8a9ab0;
  }
  .rl-footer-sep { color: #d0dfe8; }
}
</style>
