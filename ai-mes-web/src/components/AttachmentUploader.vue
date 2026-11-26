<template>
  <div class="attachment-uploader">
    <el-upload
      :action="uploadUrl"
      :headers="uploadHeaders"
      v-model:file-list="fileList"
      :on-success="onSuccess"
      :on-remove="onRemove"
      :before-upload="beforeUpload"
      :on-preview="onPreview"
      :list-type="listType"
      :accept="accept"
      :multiple="multiple"
      :limit="limit">
      <el-button type="primary" plain size="small">
        <el-icon style="margin-right:4px"><Upload /></el-icon>{{ buttonText }}
      </el-button>
      <template v-if="tip" #tip>
        <div style="color:#909399;font-size:12px">{{ tip }}</div>
      </template>
    </el-upload>

    <!-- 图片预览 -->
    <el-image-viewer v-if="previewVisible" :url-list="previewUrls"
      :initial-index="previewIndex" @close="previewVisible = false" />
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import { isImage, parseAttachments, extractFileName } from '@/utils/attachment'

const props = defineProps({
  /** v-model:modelValue - 可接受：JSON 字符串、数组 [{name,url}]、或 null/空 */
  modelValue: { type: [String, Array], default: '' },
  buttonText: { type: String, default: '上传附件' },
  listType: { type: String, default: 'picture' },    // picture | text | picture-card
  accept: { type: String, default: '' },              // 比如 'image/*' 或 '.pdf,.jpg'
  multiple: { type: Boolean, default: true },
  limit: { type: Number, default: 10 },
  maxMB: { type: Number, default: 50 },
  tip: { type: String, default: '支持任意类型,图片可直接预览,单文件不超过 50MB' }
})
const emit = defineEmits(['update:modelValue'])

const uploadUrl = (import.meta.env.VITE_API_BASE || '/api') + '/common/upload'
const uploadHeaders = computed(() => ({
  Authorization: 'Bearer ' + (localStorage?.getItem('token') || '')
}))

const fileList = ref([])
// 标记本次 modelValue 变化是组件内部 emit 出去的, watch 看到这个标记就不回写 fileList,
// 否则多文件并发上传时, 第 1 张完成 emit 触发 watch 重设 fileList, 把第 2 张(还在传)清掉了
let internalUpdate = false

// 记录 modelValue 的初始类型(字符串 or 数组 or 字符串数组),用于确定输出格式
function detectMode(val) {
  if (Array.isArray(val)) {
    if (val.length > 0 && typeof val[0] === 'string') return 'array-of-strings'
    return 'array-of-objects'
  }
  return 'string'
}

// 初始化和外部更新
watch(() => props.modelValue, (val) => {
  if (internalUpdate) {
    internalUpdate = false
    return
  }
  const arr = parseAttachments(val)
  fileList.value = arr.map((a, i) => ({
    name: a.name || extractFileName(a.url) || `附件${i + 1}`,
    url: a.url
  }))
}, { immediate: true })

function syncOut() {
  // 优先用上传响应里的真实 URL, 防止 file.url 是 el-upload 自动生成的 blob: 预览 URL
  // (多文件并发上传时, 某文件 onSuccess 还没触发 file.url 仍是 blob, 被同步出去会导致存到 DB 后失效)
  const list = fileList.value
    .map(f => {
      const realUrl = f.response?.data?.url || f.response?.url
      return { name: f.name, url: realUrl || f.url }
    })
    .filter(a => a.url && !String(a.url).startsWith('blob:'))
  const mode = detectMode(props.modelValue)
  internalUpdate = true
  if (mode === 'array-of-strings') {
    emit('update:modelValue', list.map(a => a.url))
  } else if (mode === 'array-of-objects') {
    emit('update:modelValue', list)
  } else {
    emit('update:modelValue', list.length ? JSON.stringify(list) : '')
  }
}

function beforeUpload(file) {
  if (file.size / 1024 / 1024 >= props.maxMB) {
    ElMessage.error(`文件大小不能超过 ${props.maxMB}MB`)
    return false
  }
  return true
}

function onSuccess(res, file) {
  if (res.code === 200 || res.code === 0) {
    file.url = res.data?.url || res.data || ''
    ElMessage.success('上传成功')
    syncOut()
  } else {
    ElMessage.error(res.msg || '上传失败')
    const i = fileList.value.indexOf(file)
    if (i > -1) fileList.value.splice(i, 1)
  }
}

function onRemove(file) {
  const i = fileList.value.indexOf(file)
  if (i > -1) fileList.value.splice(i, 1)
  syncOut()
}

// 图片预览
const previewVisible = ref(false)
const previewUrls = ref([])
const previewIndex = ref(0)

function onPreview(file) {
  const url = file.url || file.response?.data?.url
  if (!url) return
  if (isImage(file.name || url)) {
    const imgs = fileList.value
      .map(f => f.url || f.response?.data?.url)
      .filter(u => u && isImage(u))
    previewUrls.value = imgs
    previewIndex.value = Math.max(0, imgs.indexOf(url))
    previewVisible.value = true
  } else {
    window.open(url, '_blank')
  }
}
</script>
