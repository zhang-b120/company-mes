<template>
  <div class="login-container">
    <!-- 左侧品牌区域 -->
    <div class="login-left">
      <div class="brand-content">
        <div class="brand-icon">
          <svg viewBox="0 0 48 48" width="56" height="56" fill="none" xmlns="http://www.w3.org/2000/svg">
            <rect width="48" height="48" rx="12" fill="rgba(255,255,255,0.15)"/>
            <path d="M14 34V18l10-6 10 6v16l-10 6-10-6z" stroke="#fff" stroke-width="2" fill="none"/>
            <path d="M24 12v12M14 18l10 6 10-6M14 34l10-6 10 6" stroke="#fff" stroke-width="1.5" opacity="0.6"/>
            <circle cx="24" cy="24" r="3" fill="#fff" opacity="0.8"/>
          </svg>
        </div>
        <h1 class="brand-title">AI-MES 智造云工单</h1>
        <p class="brand-desc">智能制造执行系统 · AI赋能生产管理</p>
        <div class="brand-features">
          <div class="feature-item">
            <div class="feature-dot"></div>
            <span>智能排产调度</span>
          </div>
          <div class="feature-item">
            <div class="feature-dot"></div>
            <span>全流程质量追溯</span>
          </div>
          <div class="feature-item">
            <div class="feature-dot"></div>
            <span>AI辅助决策分析</span>
          </div>
          <div class="feature-item">
            <div class="feature-dot"></div>
            <span>实时生产看板</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 右侧登录区域 -->
    <div class="login-right">
      <div class="login-card">
        <div class="login-header">
          <div class="login-logo">
            <svg viewBox="0 0 48 48" width="44" height="44" fill="none" xmlns="http://www.w3.org/2000/svg">
              <rect width="48" height="48" rx="12" fill="#1a1a2e"/>
              <path d="M14 34V18l10-6 10 6v16l-10 6-10-6z" stroke="#409eff" stroke-width="2" fill="none"/>
              <path d="M24 12v12M14 18l10 6 10-6M14 34l10-6 10 6" stroke="#409eff" stroke-width="1.5" opacity="0.5"/>
              <circle cx="24" cy="24" r="3" fill="#409eff" opacity="0.8"/>
            </svg>
          </div>
          <h2 class="login-title">智造云工单</h2>
          <p class="login-subtitle">请使用账号密码登录系统</p>
        </div>

        <div class="login-form-wrapper">
          <el-form
            :model="loginForm"
            :rules="rules"
            ref="formRef"
            @keyup.enter="handleLogin"
          >
            <el-form-item prop="username">
              <el-input
                v-model="loginForm.username"
                placeholder="请输入账号"
                prefix-icon="User"
                size="large"
              />
            </el-form-item>
            <el-form-item prop="password">
              <el-input
                v-model="loginForm.password"
                type="password"
                placeholder="请输入密码"
                prefix-icon="Lock"
                size="large"
                show-password
              />
            </el-form-item>
            <el-form-item>
              <el-button
                type="primary"
                :loading="loading"
                @click="handleLogin"
                size="large"
                class="login-btn"
              >
                登 录
              </el-button>
            </el-form-item>
          </el-form>
        </div>

        <div class="login-footer">
          <span class="footer-version">V4.0</span>
          <span class="footer-divider">|</span>
          <span class="footer-support">技术支持：智造云团队</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref(null)
const loading = ref(false)
const loginForm = ref({
  username: '',
  password: ''
})
const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  await formRef.value.validate()
  loading.value = true
  try {
    await userStore.doLogin(loginForm.value)
    ElMessage.success('登录成功')
    const target = route.query.redirect || '/'
    router.push(target)
  } catch {
    // 错误已由响应拦截器统一处理
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.login-container {
  height: 100vh;
  display: flex;
  overflow: hidden;
}

/* ========== 左侧品牌区域 ========== */

.login-left {
  flex: 1;
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    width: 600px;
    height: 600px;
    border-radius: 50%;
    background: radial-gradient(circle, rgba(64, 158, 255, 0.12) 0%, transparent 70%);
    top: -100px;
    right: -200px;
  }

  &::after {
    content: '';
    position: absolute;
    width: 400px;
    height: 400px;
    border-radius: 50%;
    background: radial-gradient(circle, rgba(103, 194, 58, 0.1) 0%, transparent 70%);
    bottom: -100px;
    left: -100px;
  }
}

.brand-content {
  text-align: center;
  color: #fff;
  z-index: 1;
  padding: 0 60px;
}

.brand-icon {
  margin-bottom: 24px;
}

.brand-title {
  font-size: 32px;
  font-weight: 700;
  margin-bottom: 12px;
  letter-spacing: 2px;
}

.brand-desc {
  font-size: 15px;
  color: rgba(255, 255, 255, 0.65);
  margin-bottom: 48px;
}

.brand-features {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px 32px;
  text-align: left;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.8);
}

.feature-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #409eff;
  flex-shrink: 0;
}

/* ========== 右侧登录区域 ========== */

.login-right {
  width: 480px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
}

.login-card {
  width: 360px;
  display: flex;
  flex-direction: column;
}

.login-header {
  text-align: center;
  margin-bottom: 36px;
}

.login-logo {
  margin-bottom: 16px;
  display: inline-block;
}

.login-title {
  font-size: 26px;
  font-weight: 600;
  color: #1a1a2e;
  margin-bottom: 8px;
}

.login-subtitle {
  font-size: 14px;
  color: #909399;
}

.login-form-wrapper {
  margin-bottom: 24px;
}

.login-btn {
  width: 100%;
  height: 48px;
  font-size: 16px;
  border-radius: 8px;
  letter-spacing: 4px;
}

/* ---- 底部 ---- */

.login-footer {
  text-align: center;
  font-size: 12px;
  color: #c0c4cc;
  padding-top: 16px;
  border-top: 1px solid #f5f5f5;
}

.footer-version {
  font-weight: 500;
}

.footer-divider {
  margin: 0 8px;
  color: #e4e7ed;
}

.footer-support {
  color: #c0c4cc;
}

/* ========== 响应式 ========== */

@media (max-width: 768px) {
  .login-left {
    display: none;
  }
  .login-right {
    width: 100%;
  }
}
</style>
