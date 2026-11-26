<template>
  <div class="ai-markdown" v-html="renderedHtml"></div>
</template>

<script setup>
import { computed } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

const props = defineProps({
  content: { type: String, default: '' }
})

// 配置 marked
marked.setOptions({
  breaks: true,
  gfm: true
})

const renderedHtml = computed(() => {
  if (!props.content) return ''
  try {
    const html = marked.parse(props.content)
    return DOMPurify.sanitize(html)
  } catch {
    return props.content.replace(/\n/g, '<br>')
  }
})
</script>

<style scoped>
.ai-markdown {
  line-height: 1.7;
  font-size: 14px;
  color: #303133;
  word-break: break-word;
}
.ai-markdown :deep(h1) { font-size: 20px; font-weight: 600; margin: 16px 0 8px; }
.ai-markdown :deep(h2) { font-size: 17px; font-weight: 600; margin: 14px 0 6px; }
.ai-markdown :deep(h3) { font-size: 15px; font-weight: 600; margin: 12px 0 4px; }
.ai-markdown :deep(p) { margin: 6px 0; }
.ai-markdown :deep(ul), .ai-markdown :deep(ol) { padding-left: 20px; margin: 6px 0; }
.ai-markdown :deep(li) { margin: 3px 0; }
.ai-markdown :deep(strong) { font-weight: 600; color: #1a1a2e; }
.ai-markdown :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 10px 0;
  font-size: 13px;
}
.ai-markdown :deep(th) {
  background: #f5f7fa;
  font-weight: 600;
  text-align: left;
  padding: 8px 12px;
  border: 1px solid #ebeef5;
}
.ai-markdown :deep(td) {
  padding: 6px 12px;
  border: 1px solid #ebeef5;
}
.ai-markdown :deep(tr:hover td) {
  background: #fafafa;
}
.ai-markdown :deep(code) {
  background: #f0f0f0;
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 13px;
}
.ai-markdown :deep(pre) {
  background: #f5f5f5;
  padding: 12px;
  border-radius: 6px;
  overflow-x: auto;
}
.ai-markdown :deep(blockquote) {
  border-left: 3px solid #7B61FF;
  padding-left: 12px;
  color: #606266;
  margin: 8px 0;
}
.ai-markdown :deep(hr) {
  border: none;
  border-top: 1px solid #ebeef5;
  margin: 12px 0;
}
</style>
