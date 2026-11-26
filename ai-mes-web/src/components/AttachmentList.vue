<template>
  <div v-if="items.length" class="attachment-list">
    <div v-for="(a, idx) in items" :key="idx" class="attach-item">
      <!-- 图片: 点击预览, 缩略图展示 -->
      <el-image v-if="isImage(a.name || a.url)"
        :src="a.url" fit="cover"
        :style="{ width: thumbSize + 'px', height: thumbSize + 'px', borderRadius: '4px', cursor: 'pointer' }"
        :preview-src-list="imageUrls"
        :initial-index="imageUrls.indexOf(a.url)"
        preview-teleported
        :hide-on-click-modal="true" />
      <!-- 其他: 下载/新窗口打开 -->
      <el-link v-else type="primary" :href="a.url" target="_blank" class="attach-link">
        📎 {{ a.name || extractFileName(a.url) }}
      </el-link>
    </div>
  </div>
  <span v-else-if="emptyText" style="color:#909399;font-size:13px">{{ emptyText }}</span>
</template>

<script setup>
import { computed } from 'vue'
import { isImage, parseAttachments, extractFileName } from '@/utils/attachment'

const props = defineProps({
  /** 附件数据: JSON 字符串、数组 [{name,url}]、或单个 URL 字符串用逗号分隔 */
  value: { type: [String, Array], default: '' },
  thumbSize: { type: Number, default: 80 },
  emptyText: { type: String, default: '' }
})

const items = computed(() => parseAttachments(props.value))
const imageUrls = computed(() => items.value.filter(a => isImage(a.name || a.url)).map(a => a.url))
</script>

<style scoped>
.attachment-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}
.attach-item {
  display: inline-block;
}
.attach-link {
  display: inline-block;
  max-width: 240px;
  padding: 4px 8px;
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  font-size: 13px;
}
</style>
