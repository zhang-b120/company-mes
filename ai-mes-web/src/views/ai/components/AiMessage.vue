<template>
  <div :class="['ai-message', msg.role]">
    <!-- 用户消息 -->
    <div v-if="msg.role === 'user'" class="message-bubble user-bubble">
      <div class="bubble-content">{{ msg.content }}</div>
      <div class="bubble-time">{{ msg.time }}</div>
    </div>

    <!-- AI消息 -->
    <div v-else class="message-bubble ai-bubble">
      <div class="ai-avatar">
        <el-icon :size="20"><MagicStick /></el-icon>
      </div>
      <div class="ai-content-wrap">
        <!-- Markdown 文本内容 -->
        <AiMarkdown v-if="msg.content" :content="msg.content" />

        <!-- 结构化数据表格 -->
        <AiDataTable
          v-if="msg.data && msg.data.records && msg.data.records.length > 0"
          :data="msg.data"
          class="embedded-table"
        />

        <!-- 跟进建议 -->
        <div v-if="msg.suggestions && msg.suggestions.length" class="suggestions">
          <el-button
            v-for="s in msg.suggestions"
            :key="s"
            size="small"
            round
            @click="$emit('suggestion', s)"
          >{{ s }}</el-button>
        </div>

        <div class="bubble-time">{{ msg.time }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { MagicStick } from '@element-plus/icons-vue'
import AiMarkdown from './AiMarkdown.vue'
import AiDataTable from './AiDataTable.vue'

defineProps({
  msg: { type: Object, required: true }
})

defineEmits(['suggestion'])
</script>

<style scoped>
.ai-message {
  display: flex;
  margin-bottom: 16px;
}
.ai-message.user {
  justify-content: flex-end;
}
.ai-message.assistant {
  justify-content: flex-start;
}

.message-bubble {
  max-width: 85%;
  border-radius: 12px;
  padding: 12px 16px;
}

.user-bubble {
  background: linear-gradient(135deg, #7B61FF, #5B8DEF);
  color: #fff;
  border-bottom-right-radius: 4px;
}
.user-bubble .bubble-content {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.5;
}

.ai-bubble {
  display: flex;
  gap: 10px;
  max-width: 90%;
}
.ai-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, #7B61FF, #5B8DEF);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}
.ai-content-wrap {
  background: #f8f9fc;
  border-radius: 12px;
  border-bottom-left-radius: 4px;
  padding: 12px 16px;
  flex: 1;
  min-width: 0;
}

.embedded-table {
  margin-top: 10px;
}

.suggestions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid #ebeef5;
}
.suggestions .el-button {
  --el-button-bg-color: #fff;
  --el-button-border-color: #dcdfe6;
  --el-button-hover-bg-color: #f0edff;
  --el-button-hover-border-color: #7B61FF;
  --el-button-hover-text-color: #7B61FF;
  font-size: 12px;
}

.bubble-time {
  font-size: 11px;
  color: rgba(255,255,255,0.7);
  margin-top: 6px;
  text-align: right;
}
.ai-content-wrap .bubble-time {
  color: #c0c4cc;
}
</style>
