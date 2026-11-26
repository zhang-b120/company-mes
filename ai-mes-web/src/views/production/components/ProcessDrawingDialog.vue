<template>
  <el-dialog v-model="visible" :title="title" width="800px" destroy-on-close @open="loadData">
    <div style="margin-bottom: 12px; display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
      <el-tag size="small">产品: {{ productName || '-' }}</el-tag>
      <el-tag size="small" type="info">工序: {{ stepName || '-' }} (#{{ stepNo }})</el-tag>
      <el-tag v-if="mode === 'plan'" size="small" type="warning">排产场景</el-tag>
      <el-tag v-else size="small" type="warning">工单场景 - 当前最新版本: v{{ latestVersion }}</el-tag>
    </div>

    <el-alert v-if="mode === 'plan' && !planEditable" type="warning" :closable="false"
      title="该计划已派发工单，图纸不可再修改，请前往工单详情管理" style="margin-bottom: 12px" />

    <el-card v-if="canEdit" shadow="never" style="margin-bottom: 12px">
      <template #header>{{ mode === 'plan' ? '上传图纸（计划阶段，所有图纸版本均为 v1）' : '上传新图纸' }}</template>
      <el-form :model="uploadForm" label-width="100px" inline>
        <el-form-item v-if="mode === 'wo'" label="版本号">
          <el-input-number v-model="uploadForm.version" :min="latestVersion + 1" :step="1" :precision="0"
            style="width: 130px" />
          <span style="color:#909399;margin-left:8px;font-size:12px">默认 {{ latestVersion + 1 }}，可修改</span>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="uploadForm.remark" placeholder="说明 (可选)" style="width: 220px" />
        </el-form-item>
        <el-form-item label="文件">
          <el-upload :action="uploadUrl" :headers="uploadHeaders" :on-success="onUploadSuccess"
            :before-upload="beforeUpload" :show-file-list="false" :limit="1">
            <el-button type="primary" plain size="small">
              <el-icon style="margin-right:4px"><Upload /></el-icon>选择文件并上传
            </el-button>
          </el-upload>
          <span v-if="uploadForm.fileName" style="margin-left:8px;color:#67c23a;font-size:12px">
            ✓ {{ uploadForm.fileName }}
          </span>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" :disabled="!uploadForm.fileUrl" @click="handleSubmit">保存图纸</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>{{ mode === 'plan' ? '已上传图纸' : '历史版本' }} ({{ list.length }})</template>
      <el-table :data="list" v-loading="loading" border size="small">
        <el-table-column label="版本" width="70" align="center">
          <template #default="{ row }">
            <el-tag :type="mode === 'wo' && row.version === latestVersion ? 'success' : 'info'" size="small">v{{ row.version }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="文件名" prop="fileName" min-width="160" show-overflow-tooltip />
        <el-table-column label="备注" prop="remark" min-width="120" show-overflow-tooltip />
        <el-table-column label="上传时间" prop="uploadedAt" min-width="160" />
        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="downloadFile(row)">下载</el-button>
            <el-popconfirm v-if="canEdit" title="确定删除该图纸？" @confirm="handleDelete(row)">
              <template #reference><el-button link type="danger" size="small">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import {
  listProcessDrawingsByPlanStep,
  listProcessDrawingsByTask,
  uploadProcessDrawing,
  deleteProcessDrawing,
  isPlanStepEditable
} from '@/api/production'

const props = defineProps({
  modelValue: Boolean,
  title: { type: String, default: '工序图纸' },
  // 'plan' 排产 | 'wo' 工单
  mode: { type: String, default: 'plan' },
  productId: [Number, null],
  productName: String,
  stepNo: [Number, null],
  stepName: String,
  // 排产场景必传
  planItemStepId: [Number, null],
  // 工单场景必传
  workOrderId: [Number, null],
  processTaskId: [Number, null]
})
const emit = defineEmits(['update:modelValue', 'changed'])
const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const uploadUrl = (import.meta.env.VITE_API_BASE || '/api') + '/common/upload'
const uploadHeaders = computed(() => ({ Authorization: 'Bearer ' + (localStorage?.getItem('token') || '') }))

const list = ref([])
const loading = ref(false)
const saving = ref(false)
const planEditable = ref(true)
const uploadForm = ref({ version: 1, fileName: '', fileUrl: '', fileSize: null, remark: '' })

const latestVersion = computed(() => {
  if (!list.value.length) return 0
  return Math.max(...list.value.map(d => d.version || 0))
})

const canEdit = computed(() => {
  if (props.mode === 'plan') return planEditable.value
  return true // 工单场景始终可编辑
})

watch(latestVersion, (v) => {
  if (uploadForm.value.fileUrl) return
  uploadForm.value.version = (v || 0) + 1
})

async function loadData() {
  loading.value = true
  try {
    if (props.mode === 'plan') {
      if (!props.planItemStepId) { list.value = []; return }
      const [listRes, editRes] = await Promise.all([
        listProcessDrawingsByPlanStep(props.planItemStepId),
        isPlanStepEditable(props.planItemStepId)
      ])
      list.value = listRes.data || []
      planEditable.value = !!editRes.data
    } else {
      if (!props.processTaskId) { list.value = []; return }
      const res = await listProcessDrawingsByTask(props.processTaskId)
      list.value = res.data || []
    }
    uploadForm.value = { version: latestVersion.value + 1, fileName: '', fileUrl: '', fileSize: null, remark: '' }
  } finally { loading.value = false }
}

function beforeUpload(file) {
  if (file.size / 1024 / 1024 >= 50) { ElMessage.error('文件不能超过50MB'); return false }
  return true
}

function onUploadSuccess(res, file) {
  if (res.code === 200 || res.code === 0) {
    uploadForm.value.fileName = file.name
    uploadForm.value.fileUrl = res.data?.url || res.data || ''
    uploadForm.value.fileSize = file.size
    ElMessage.success('文件上传成功，请点击「保存图纸」入库')
  } else {
    ElMessage.error(res.msg || '上传失败')
  }
}

async function handleSubmit() {
  if (!uploadForm.value.fileUrl) { ElMessage.warning('请先选择文件'); return }
  saving.value = true
  try {
    const payload = {
      productId: props.productId,
      stepNo: props.stepNo,
      stepName: props.stepName,
      fileName: uploadForm.value.fileName,
      fileUrl: uploadForm.value.fileUrl,
      fileSize: uploadForm.value.fileSize,
      remark: uploadForm.value.remark
    }
    if (props.mode === 'plan') {
      payload.planItemStepId = props.planItemStepId
      // 计划场景版本固定为1，由后端强制
    } else {
      payload.processTaskId = props.processTaskId
      payload.workOrderId = props.workOrderId
      payload.version = uploadForm.value.version
    }
    await uploadProcessDrawing(payload)
    ElMessage.success('图纸已保存')
    emit('changed')
    await loadData()
  } finally { saving.value = false }
}

async function handleDelete(row) {
  await deleteProcessDrawing(row.id)
  ElMessage.success('已删除')
  emit('changed')
  await loadData()
}

function downloadFile(row) {
  window.open(row.fileUrl, '_blank')
}
</script>
