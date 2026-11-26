<template>
  <div class="ws-report-screen" :class="wsTheme">
    <!-- 顶部导航栏 -->
    <header class="ws-report-header">
      <!-- 左上：返回任务列表 -->
      <button class="ws-btn-back" @click="goBack">
        ← 返回任务列表
      </button>

      <!-- 中间：标题（始终按个人报工处理,每人只报自己的部分） -->
      <div class="ws-report-title">
        <span>个人报工</span>
        <span class="ws-report-tag tag-person">{{ wsStore.displayName }}</span>
        <span v-if="isTeam" class="ws-report-tag tag-team" style="margin-left:8px">所属班组: {{ query.assignedTeamName || '-' }}</span>
      </div>

      <!-- 右上：退出 -->
      <button class="ws-btn-exit" @click="handleExit">退出</button>
    </header>

    <!-- 内容区 -->
    <main class="ws-report-main">
      <!-- 工序信息卡片 -->
      <div class="ws-info-card">
        <div class="info-row">
          <div class="info-item">
            <span class="info-label">工单号</span>
            <span class="info-value">{{ query.woNo }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">物料编码</span>
            <span class="info-value">{{ query.materialNo || '-' }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">物料名称</span>
            <span class="info-value">{{ query.productName || '-' }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">工序名称</span>
            <span class="info-value">{{ query.stepName }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">计划数量</span>
            <span class="info-value highlight">{{ query.planQty }} 件</span>
          </div>
          <div class="info-item">
            <span class="info-label">报工人</span>
            <span class="info-value">{{ wsStore.displayName }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">报工方式</span>
            <span class="info-value">
              <span class="ws-report-tag tag-person">个人</span>
              <span v-if="isTeam" class="ws-report-tag tag-team" style="margin-left:6px">班组{{ query.assignedTeamName || '' }}</span>
            </span>
          </div>
        </div>
      </div>

      <!-- 报工上限提示 -->
      <div v-if="limitInfo" class="ws-limit-alert" :class="limitInfo.maxReportableQty > 0 ? 'alert-warn' : 'alert-error'">
        {{ limitInfo.reason }}（最大可报: {{ limitInfo.maxReportableQty }}）
      </div>

      <!-- 报工表单 -->
      <div class="ws-form-card">
        <div class="ws-form-title">报工数据</div>

        <div class="ws-form-grid">
          <div class="ws-form-item">
            <label class="ws-form-label">合格数量 <span class="required">*</span></label>
            <div class="qty-control">
              <button class="qty-btn" @click="changeQty('qualifiedQty', -1)">−</button>
              <input
                v-model.number="form.qualifiedQty"
                type="number"
                class="qty-input"
                :max="limitInfo?.maxReportableQty ?? undefined"
                min="0"
                @input="onQtyChange"
              />
              <button class="qty-btn" @click="changeQty('qualifiedQty', 1)">＋</button>
            </div>
          </div>

          <div class="ws-form-item">
            <label class="ws-form-label">不良数量</label>
            <div class="qty-control">
              <button class="qty-btn" @click="changeQty('defectQty', -1)">−</button>
              <input
                v-model.number="form.defectQty"
                type="number"
                class="qty-input"
                min="0"
                @input="onQtyChange"
              />
              <button class="qty-btn" @click="changeQty('defectQty', 1)">＋</button>
            </div>
          </div>

          <div class="ws-form-item">
            <label class="ws-form-label">工时（天）</label>
            <input v-model.number="form.workHours" type="number" class="ws-input" placeholder="可选" min="0" step="0.5" />
          </div>

          <div class="ws-form-item">
            <label class="ws-form-label">备注</label>
            <input v-model="form.remark" type="text" class="ws-input" placeholder="可选备注" />
          </div>

          <div class="ws-form-item">
            <label class="ws-form-label">报工模式 <span class="required">*</span></label>
            <div class="ws-mode-radio">
              <label class="ws-radio-item" :class="{ active: form.wageMode === 'normal' }">
                <input type="radio" v-model="form.wageMode" value="normal" />
                <span>正常</span>
              </label>
              <label class="ws-radio-item" :class="{ active: form.wageMode === 'three_machine' }">
                <input type="radio" v-model="form.wageMode" value="three_machine" />
                <span>三机</span>
              </label>
            </div>
          </div>
        </div>

        <!-- 个人报工工资预估 -->
        <div class="ws-wage-preview">
          预计工资: <strong>¥{{ estimatedWage.toFixed(2) }}</strong>
          <span v-if="query.piecePrice" style="color:#6b8cb0;font-size:13px;margin-left:8px">
            (单价 ¥{{ Number(query.piecePrice).toFixed(2) }} × 合格数 × 倍率 {{ wageMultiplier.toFixed(2) }})
          </span>
        </div>

        <!-- 提交按钮 -->
        <div class="ws-submit-area">
          <button class="ws-submit-btn" :class="{ 'is-loading': submitting }" @click="handleSubmit" :disabled="submitting">
            <span v-if="submitting" class="submit-spinner"></span>
            <span>{{ submitting ? '提交中...' : '确认报工' }}</span>
          </button>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { useWorkstationStore } from '@/store/workstation'
import { getReportLimit } from '@/api/workstation'
import { reportTask } from '@/api/production'
import { resetRouter } from '@/router'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const wsStore = useWorkstationStore()
const wsTheme = computed(() => wsStore.theme)
const query = route.query

const isTeam = computed(() => query.assignType === 'team')

const form = reactive({ qualifiedQty: 0, defectQty: 0, workHours: undefined, remark: '', wageMode: '' })
const limitInfo = ref(null)
const submitting = ref(false)

// 工资倍率: 正常=1, 三机=工序在生产计划里配置的 wageRatio (空或 ≤0 则按 1)
const wageMultiplier = computed(() => {
  if (form.wageMode === 'three_machine') {
    const base = Number(query.wageRatio)
    return Number.isFinite(base) && base > 0 ? base : 1
  }
  return 1
})
const estimatedWage = computed(() => {
  if (!form.wageMode) return 0
  const qty = Number(form.qualifiedQty) || 0
  const price = Number(query.piecePrice) || 0
  return qty * price * wageMultiplier.value
})

function changeQty(field, delta) {
  const cur = form[field] || 0
  const next = Math.max(0, cur + delta)
  const max = field === 'qualifiedQty' && limitInfo.value?.maxReportableQty != null
    ? Number(limitInfo.value.maxReportableQty)
    : Infinity
  form[field] = Math.min(next, max)
}

// 直接输入时也做 clamp（合格数不能超过 maxReportableQty、不能为负数）
function onQtyChange() {
  const max = limitInfo.value?.maxReportableQty != null
    ? Number(limitInfo.value.maxReportableQty) : null
  let q = Number(form.qualifiedQty)
  if (!Number.isFinite(q) || q < 0) q = 0
  if (max != null && q > max) {
    q = max
    ElMessage.warning(`合格数量不能超过最大可报(${max})`)
  }
  form.qualifiedQty = q
  let d = Number(form.defectQty)
  if (!Number.isFinite(d) || d < 0) d = 0
  form.defectQty = d
}

async function loadReportLimit() {
  if (!query.workOrderId || !query.taskId) return
  try {
    const res = await getReportLimit(query.workOrderId, query.taskId)
    limitInfo.value = res.data
  } catch { /* ignore */ }
}

async function handleSubmit() {
  if (!form.wageMode) {
    return ElMessage.warning('请选择报工模式（正常 / 三机）')
  }
  if ((form.qualifiedQty || 0) <= 0 && (form.defectQty || 0) <= 0) {
    return ElMessage.warning('合格数量和不良数量不能都为0')
  }
  // 提交前再校验一次上限：合格数 + 不良数 均不得超过 maxReportableQty
  const max = limitInfo.value?.maxReportableQty != null
    ? Number(limitInfo.value.maxReportableQty) : null
  if (max != null) {
    const total = (Number(form.qualifiedQty) || 0) + (Number(form.defectQty) || 0)
    if (total > max) {
      return ElMessage.warning(`合格+不良合计(${total})不能超过最大可报(${max})`)
    }
  }
  submitting.value = true
  try {
    // 统一走个人报工，只记录当前工位机登录人的工量
    const data = {
      qualifiedQty: form.qualifiedQty,
      defectQty: form.defectQty,
      workHours: form.workHours || undefined,
      remark: form.remark || undefined,
      reporterId: Number(query.userId),
      wageMode: form.wageMode || 'normal'
    }
    await reportTask(query.workOrderId, query.taskId, data)
    ElMessage.success('报工成功')
    goBack()
  } catch {
    // handled by interceptor
  } finally {
    submitting.value = false
  }
}

function goBack() {
  router.push({ path: '/workstation/task-list', query: { username: query.username } })
}

function handleExit() {
  userStore.logout()
  wsStore.clear()
  resetRouter()
  router.replace('/reportLogin')
}

onMounted(() => {
  loadReportLimit()
})
</script>

<style scoped lang="scss">
.ws-report-screen {
  width: 100vw;
  min-height: 100vh;
  background: #0a0f1e;
  color: #e8eaf0;
  display: flex;
  flex-direction: column;
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

// ===== 顶部 =====
.ws-report-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 32px;
  background: linear-gradient(135deg, #0d1829 0%, #112040 100%);
  border-bottom: 1px solid #1e3a5f;
  flex-shrink: 0;
}
.ws-btn-back {
  background: #0d2040;
  border: 1px solid #1e3a5f;
  border-radius: 6px;
  color: #6b8cb0;
  font-size: 14px;
  padding: 7px 16px;
  cursor: pointer;
  transition: color 0.2s, border-color 0.2s;
  &:hover { color: #40a0ff; border-color: #40a0ff; }
}
.ws-report-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 18px;
  font-weight: 700;
  color: #c8d8f0;
}
.ws-btn-exit {
  background: #4a0f0f;
  border: 1px solid #7a1f1f;
  border-radius: 6px;
  color: #ff8080;
  font-size: 14px;
  font-weight: 600;
  padding: 7px 18px;
  cursor: pointer;
  transition: background 0.2s;
  &:hover { background: #5a1515; }
}

.ws-report-tag {
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 10px;
  font-weight: 500;
}
.tag-team { background: #0f2a50; color: #60a0e0; }
.tag-person { background: #0a2510; color: #60c060; }

// ===== 主内容 =====
.ws-report-main {
  flex: 1;
  padding: 20px 40px;
  max-width: 900px;
  width: 100%;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

// 工序信息卡片
.ws-info-card {
  background: #0d1829;
  border: 1px solid #1e3a5f;
  border-radius: 10px;
  padding: 20px 24px;
}
.info-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px 24px;
}
.info-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.info-label {
  font-size: 12px;
  color: #3a5070;
}
.info-value {
  font-size: 15px;
  color: #c8d8f0;
  &.highlight { color: #40a0ff; font-weight: 700; font-size: 18px; }
}

// 限制提示
.ws-limit-alert {
  padding: 10px 16px;
  border-radius: 8px;
  font-size: 14px;
  border-left: 3px solid;
  &.alert-warn { background: #1a1000; color: #ffa040; border-color: #ffa040; }
  &.alert-error { background: #200a0a; color: #ff6b6b; border-color: #ff4d4f; }
}

// 报工表单卡片
.ws-form-card {
  background: #0d1829;
  border: 1px solid #1e3a5f;
  border-radius: 10px;
  padding: 24px 28px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.ws-form-title {
  font-size: 16px;
  font-weight: 700;
  color: #c8d8f0;
  padding-bottom: 12px;
  border-bottom: 1px solid #1e3a5f;
}
.ws-form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px 32px;
}
.ws-form-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.ws-form-label {
  font-size: 13px;
  color: #6b8cb0;
  .required { color: #ff6b6b; }
}

// 数量步进控件
.qty-control {
  display: flex;
  align-items: center;
  gap: 0;
}
.qty-btn {
  width: 40px;
  height: 44px;
  background: #0a1525;
  border: 1px solid #1e3a5f;
  color: #6b8cb0;
  font-size: 20px;
  cursor: pointer;
  transition: background 0.2s, color 0.2s;
  &:first-child { border-radius: 6px 0 0 6px; border-right: none; }
  &:last-child { border-radius: 0 6px 6px 0; border-left: none; }
  &:hover { background: #1a3a6b; color: #40a0ff; }
}
.qty-input {
  width: 80px;
  height: 44px;
  background: #0a1525;
  border: 1px solid #1e3a5f;
  color: #e8eaf0;
  font-size: 20px;
  font-weight: 700;
  text-align: center;
  outline: none;
  &:focus { border-color: #40a0ff; }
  // 隐藏 number 箭头
  &::-webkit-inner-spin-button, &::-webkit-outer-spin-button { -webkit-appearance: none; }
}
.ws-input {
  height: 44px;
  background: #0a1525;
  border: 1px solid #1e3a5f;
  border-radius: 6px;
  color: #e8eaf0;
  font-size: 15px;
  padding: 0 14px;
  outline: none;
  &::placeholder { color: #2a4a6a; }
  &:focus { border-color: #40a0ff; }
}
.ws-mode-radio {
  display: flex;
  gap: 12px;
  align-items: center;
  height: 44px;
}
.ws-radio-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 16px;
  border: 1px solid #1e3a5f;
  border-radius: 6px;
  background: #0a1525;
  color: #6b8cb0;
  font-size: 15px;
  cursor: pointer;
  user-select: none;
  input[type="radio"] { accent-color: #40a0ff; }
  &.active { color: #40a0ff; border-color: #40a0ff; }
}

// 分割线
.ws-divider {
  font-size: 14px;
  color: #6b8cb0;
  padding: 8px 0;
  border-top: 1px solid #1e3a5f;
  border-bottom: 1px solid #1e3a5f;
  font-weight: 600;
}

// 班组成员表格
.ws-member-table {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.member-thead {
  display: grid;
  grid-template-columns: 2fr 1fr 1.5fr 1.5fr 1.5fr;
  gap: 8px;
  padding: 6px 10px;
  font-size: 12px;
  color: #3a5070;
}
.member-row {
  display: grid;
  grid-template-columns: 2fr 1fr 1.5fr 1.5fr 1.5fr;
  gap: 8px;
  align-items: center;
  padding: 8px 10px;
  background: #0a1525;
  border-radius: 6px;
  border: 1px solid #0f2035;
  font-size: 14px;

  &.is-leader { background: #0f2a10; border-color: #1a4020; }
}
.member-name {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #c8d8f0;
}
.leader-tag {
  font-size: 11px;
  background: #2a4a10;
  color: #80d060;
  padding: 1px 6px;
  border-radius: 8px;
}
.member-weight { color: #6b8cb0; }
.member-input {
  width: 80px;
  height: 32px;
  background: #0a1525;
  border: 1px solid #1e3a5f;
  border-radius: 4px;
  color: #e8eaf0;
  font-size: 14px;
  text-align: center;
  outline: none;
  padding: 0 6px;
  &:focus { border-color: #40a0ff; }
  &::-webkit-inner-spin-button, &::-webkit-outer-spin-button { -webkit-appearance: none; }
}
.member-wage { color: #40c080; font-weight: 600; }

.member-check {
  font-size: 13px;
  color: #6b8cb0;
  padding: 8px 10px;
  &.check-fail { color: #ff8040; }
}

// 工资预估
.ws-wage-preview {
  margin: 16px 0;
  padding: 16px 20px;
  background: linear-gradient(135deg, #13233a, #1a3a5f);
  border-radius: 8px;
  border: 1px solid #2a4a6a;
  font-size: 18px;
  color: #c8d8f0;
  strong {
    color: #40c080;
    font-size: 24px;
    margin-left: 6px;
  }
}

// 提交区域
.ws-submit-area {
  display: flex;
  justify-content: center;
  padding-top: 8px;
  border-top: 1px solid #1e3a5f;
}
.ws-submit-btn {
  min-width: 200px;
  height: 50px;
  background: linear-gradient(135deg, #1a5fa0, #40a0ff);
  border: none;
  border-radius: 8px;
  color: #fff;
  font-size: 17px;
  font-weight: 600;
  letter-spacing: 3px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  transition: opacity 0.2s;
  &:hover:not(:disabled) { opacity: 0.9; }
  &:disabled { opacity: 0.6; cursor: not-allowed; }
}
.submit-spinner {
  width: 18px;
  height: 18px;
  border: 2px solid rgba(255,255,255,0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

// ===== 浅色主题 =====
.ws-report-screen.light {
  background: linear-gradient(135deg, #e8f4fd 0%, #f0f6ff 40%, #fff 100%);
  color: #1a1a2e;

  .ws-report-header { background: linear-gradient(135deg, #fff, #eaf3fc); border-bottom-color: #d0dfe8; }
  .ws-btn-back { background: #f0f6ff; border-color: #c0d8f0; color: #4a6a8a; &:hover { color: #1a6fba; border-color: #1a6fba; } }
  .ws-report-title { color: #1a1a2e; }
  .ws-btn-exit { background: #fff0f0; border-color: #e0a0a0; color: #c04040; &:hover { background: #ffe0e0; } }
  .tag-team { background: #e0ecf6; color: #1a6fba; }
  .tag-person { background: #e8f8e8; color: #2a8a2a; }

  .ws-info-card { background: #fff; border-color: #d0dfe8; }
  .info-label { color: #5a7a9a; }
  .info-value { color: #1a1a2e; &.highlight { color: #1a6fba; } }

  .ws-limit-alert {
    &.alert-warn { background: #fff8e8; color: #b07020; border-color: #e0a030; }
    &.alert-error { background: #fff0f0; color: #c04040; border-color: #d04040; }
  }

  .ws-form-card { background: #fff; border-color: #d0dfe8; }
  .ws-form-title { color: #1a1a2e; border-bottom-color: #e8f0f8; }
  .ws-form-label { color: #5a7a9a; }
  .qty-btn { background: #f0f6ff; border-color: #c0d8f0; color: #4a6a8a; &:hover { background: #d0e8ff; color: #1a6fba; } }
  .qty-input { background: #f5f9ff; border-color: #c0d8f0; color: #1a1a2e; &:focus { border-color: #1a6fba; } }
  .ws-input { background: #f5f9ff; border-color: #c0d8f0; color: #1a1a2e; &::placeholder { color: #a0b8d0; } &:focus { border-color: #1a6fba; } }

  .ws-divider { color: #4a6a8a; border-color: #d0dfe8; }
  .member-thead { color: #5a7a9a; }
  .member-row { background: #f5f9ff; border-color: #e8f0f8; &.is-leader { background: #f0fff0; border-color: #c0e0c0; } }
  .member-name { color: #1a1a2e; }
  .leader-tag { background: #e0f8d0; color: #2a8a2a; }
  .member-weight { color: #5a7a9a; }
  .member-input { background: #fff; border-color: #c0d8f0; color: #1a1a2e; &:focus { border-color: #1a6fba; } }
  .member-wage { color: #1a8a4a; }
  .member-check { color: #5a7a9a; &.check-fail { color: #d06020; } }

  .ws-submit-area { border-top-color: #d0dfe8; }
  .ws-submit-btn { background: linear-gradient(135deg, #1a6fba, #3a90e0); }
}
</style>
