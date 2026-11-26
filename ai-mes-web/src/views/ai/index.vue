<template>
  <div class="ai-conversation">
    <!-- 顶部栏 -->
    <div class="conv-header">
      <div class="header-left">
        <el-icon :size="22" color="#7B61FF"><MagicStick /></el-icon>
        <span class="header-title">AI 智能助手</span>
      </div>
      <div class="header-right">
        <el-button text @click="clearSession">
          <el-icon><RefreshRight /></el-icon>
          新对话
        </el-button>
      </div>
    </div>

    <!-- 消息区域 -->
    <div ref="messageArea" class="conv-messages">
      <!-- 欢迎状态 -->
      <div v-if="chatStore.messages.length === 0" class="welcome-area">
        <div class="welcome-icon">
          <el-icon :size="48" color="#7B61FF"><MagicStick /></el-icon>
        </div>
        <h2 class="welcome-title">你好，我是小智</h2>
        <p class="welcome-desc">AI-MES 智造云工单系统智能助手，我可以帮你分析工厂运营数据、查询业务信息、处理订单等。试试下面的快捷操作：</p>

        <!-- 快捷操作chips -->
        <div class="quick-chips">
          <div
            v-for="chip in quickChips"
            :key="chip.text"
            class="chip"
            @click="sendQuickMessage(chip.text)"
          >
            <span class="chip-icon">{{ chip.icon }}</span>
            <span class="chip-text">{{ chip.text }}</span>
          </div>
        </div>
      </div>

      <!-- 对话消息列表 -->
      <AiMessage
        v-for="(msg, idx) in chatStore.messages"
        :key="idx"
        :msg="msg"
        @suggestion="sendQuickMessage"
      />

      <!-- 加载中 -->
      <div v-if="chatStore.loading" class="loading-indicator">
        <div class="typing-dots">
          <span></span><span></span><span></span>
        </div>
        <span class="loading-text">小智正在思考...</span>
      </div>
    </div>

    <!-- 输入区域 -->
    <div class="conv-input">
      <div class="input-wrapper">
        <el-input
          v-model="inputText"
          type="textarea"
          :autosize="{ minRows: 1, maxRows: 4 }"
          placeholder="输入消息，例如：帮我看看今天的生产情况..."
          resize="none"
          @keydown.enter.exact.prevent="sendMessage"
          :disabled="chatStore.loading"
        />
        <el-button
          class="send-btn"
          type="primary"
          circle
          :icon="Promotion"
          :loading="chatStore.loading"
          @click="sendMessage"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { MagicStick, RefreshRight, Promotion } from '@element-plus/icons-vue'
import { useAiChatStore } from '@/store/ai-chat'
import AiMessage from './components/AiMessage.vue'

const chatStore = useAiChatStore()
const route = useRoute()
const messageArea = ref(null)
const inputText = ref('')

// 快捷操作chips
const quickChips = [
  { icon: '📊', text: '今日简报' },
  { icon: '🔍', text: '质量趋势分析' },
  { icon: '⚡', text: '生产效率分析' },
  { icon: '⏰', text: '交期风险预警' },
  { icon: '📦', text: '采购建议' },
  { icon: '💰', text: '成本分析' },
]

// 设置当前页面上下文
onMounted(() => {
  chatStore.setCurrentPage(route.path)
})

// 发送消息
async function sendMessage() {
  const text = inputText.value.trim()
  if (!text || chatStore.loading) return
  inputText.value = ''
  await chatStore.sendConversation(text)
  scrollToBottom()
}

// 快捷消息
function sendQuickMessage(text) {
  inputText.value = text
  sendMessage()
}

// 清空会话
async function clearSession() {
  await chatStore.clearSession()
}

// 自动滚动到底部
function scrollToBottom() {
  nextTick(() => {
    if (messageArea.value) {
      messageArea.value.scrollTop = messageArea.value.scrollHeight
    }
  })
}

// 监听消息变化自动滚动
watch(() => chatStore.messages.length, () => {
  scrollToBottom()
})
watch(() => chatStore.loading, () => {
  scrollToBottom()
})
</script>

<style scoped>
.ai-conversation {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 90px);
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

/* 顶部栏 */
.conv-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 20px;
  border-bottom: 1px solid #f0f0f0;
  background: #fff;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}
.header-title {
  font-size: 17px;
  font-weight: 600;
  color: #1a1a2e;
}

/* 消息区域 */
.conv-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
  background: #fafbfd;
}

/* 欢迎状态 */
.welcome-area {
  text-align: center;
  padding: 40px 20px;
}
.welcome-icon {
  margin-bottom: 16px;
}
.welcome-title {
  font-size: 22px;
  font-weight: 600;
  color: #1a1a2e;
  margin-bottom: 8px;
}
.welcome-desc {
  font-size: 14px;
  color: #909399;
  max-width: 480px;
  margin: 0 auto 28px;
  line-height: 1.6;
}

/* 快捷chips */
.quick-chips {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  max-width: 560px;
  margin: 0 auto;
}
.chip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
}
.chip:hover {
  border-color: #7B61FF;
  background: #f8f6ff;
  box-shadow: 0 2px 8px rgba(123, 97, 255, 0.12);
  transform: translateY(-1px);
}
.chip-icon {
  font-size: 20px;
}
.chip-text {
  font-size: 13px;
  color: #303133;
  font-weight: 500;
}

/* 加载动画 */
.loading-indicator {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  margin-bottom: 16px;
}
.typing-dots {
  display: flex;
  gap: 4px;
}
.typing-dots span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #7B61FF;
  animation: typing 1.2s infinite;
}
.typing-dots span:nth-child(2) { animation-delay: 0.2s; }
.typing-dots span:nth-child(3) { animation-delay: 0.4s; }
@keyframes typing {
  0%, 60%, 100% { opacity: 0.3; transform: scale(0.8); }
  30% { opacity: 1; transform: scale(1); }
}
.loading-text {
  font-size: 13px;
  color: #909399;
}

/* 输入区域 */
.conv-input {
  padding: 16px 24px;
  border-top: 1px solid #f0f0f0;
  background: #fff;
}
.input-wrapper {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  max-width: 800px;
  margin: 0 auto;
}
.input-wrapper :deep(.el-textarea__inner) {
  border-radius: 12px;
  padding: 10px 16px;
  font-size: 14px;
  border-color: #dcdfe6;
  box-shadow: 0 1px 4px rgba(0,0,0,0.04);
}
.input-wrapper :deep(.el-textarea__inner:focus) {
  border-color: #7B61FF;
  box-shadow: 0 0 0 2px rgba(123, 97, 255, 0.15);
}
.send-btn {
  --el-button-bg-color: #7B61FF;
  --el-button-border-color: #7B61FF;
  --el-button-hover-bg-color: #6a52e0;
  --el-button-hover-border-color: #6a52e0;
  flex-shrink: 0;
  width: 40px;
  height: 40px;
}
</style>
