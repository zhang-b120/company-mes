<template>
  <div class="approval-progress" v-loading="loading">
    <template v-if="progressData">
      <div class="progress-header">
        <el-tag :type="statusTagType" size="small">{{ statusLabel }}</el-tag>
        <span class="flow-name">{{ progressData.flowName }}</span>
        <span class="applicant">申请人：{{ progressData.applicantName }}</span>
      </div>

      <el-steps :active="activeStep" finish-status="success" align-center style="margin-top: 16px;">
        <el-step
          v-for="(node, index) in progressData.nodes"
          :key="node.nodeId"
          :title="node.nodeName"
          :status="stepStatus(node)"
        >
          <template #description>
            <div class="node-desc">
              <div v-if="node.records && node.records.length > 0">
                <div v-for="rec in node.records" :key="rec.approverId" class="record-item">
                  <span class="approver-name">{{ rec.approverName }}</span>
                  <el-tag :type="rec.action === 'APPROVE' ? 'success' : 'danger'" size="small">
                    {{ rec.action === 'APPROVE' ? '通过' : '驳回' }}
                  </el-tag>
                  <span class="action-time">{{ formatTime(rec.actionTime) }}</span>
                  <div v-if="rec.remark" class="record-remark">{{ rec.remark }}</div>
                </div>
              </div>
              <div v-else-if="node.nodeStatus === 'pending'" class="pending-text">
                <el-text type="warning" size="small">等待审批中...</el-text>
              </div>
              <div v-else-if="node.nodeStatus === 'waiting'" class="waiting-text">
                <el-text type="info" size="small">未开始</el-text>
              </div>
              <div class="node-mode">
                <el-text type="info" size="small">
                  {{ node.approvalMode === 'AND' ? '会签（需全部通过）' : '或签（任一通过即可）' }}
                </el-text>
              </div>
            </div>
          </template>
        </el-step>
      </el-steps>
    </template>

    <el-empty v-else-if="!loading" description="暂无审批流程" :image-size="60" />
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { getApprovalProgress } from '@/api/approval'

const props = defineProps({
  businessType: { type: String, required: true },
  businessId: { type: [Number, String], required: true }
})

const loading = ref(false)
const progressData = ref(null)
const activeStep = ref(0)

const statusMap = {
  PENDING: { label: '审批中', type: 'warning' },
  APPROVED: { label: '已通过', type: 'success' },
  REJECTED: { label: '已驳回', type: 'danger' },
  CANCELLED: { label: '已撤回', type: 'info' }
}

const statusLabel = ref('')
const statusTagType = ref('')

function stepStatus(node) {
  if (node.nodeStatus === 'approved') return 'success'
  if (node.nodeStatus === 'rejected') return 'error'
  if (node.nodeStatus === 'pending') return 'process'
  return 'wait'
}

function formatTime(time) {
  if (!time) return ''
  return time.replace('T', ' ').substring(0, 16)
}

async function loadProgress() {
  if (!props.businessId) return
  loading.value = true
  try {
    const res = await getApprovalProgress(props.businessType, props.businessId)
    progressData.value = res.data
    if (res.data) {
      const s = statusMap[res.data.status] || { label: res.data.status, type: '' }
      statusLabel.value = s.label
      statusTagType.value = s.type

      // 计算 activeStep
      const nodes = res.data.nodes || []
      let step = 0
      for (let i = 0; i < nodes.length; i++) {
        if (nodes[i].nodeStatus === 'approved') step = i + 1
        else if (nodes[i].nodeStatus === 'pending') { step = i; break }
      }
      if (res.data.status === 'APPROVED') step = nodes.length
      activeStep.value = step
    }
  } catch {} finally { loading.value = false }
}

watch(() => props.businessId, () => { loadProgress() })
onMounted(() => { loadProgress() })

defineExpose({ refresh: loadProgress })
</script>

<style scoped lang="scss">
.approval-progress {
  padding: 8px 0;
}
.progress-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
  .flow-name { font-weight: 600; font-size: 14px; }
  .applicant { color: #909399; font-size: 13px; }
}
.node-desc {
  text-align: left;
  font-size: 12px;
}
.record-item {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
  flex-wrap: wrap;
  .approver-name { font-weight: 500; }
  .action-time { color: #909399; font-size: 11px; }
}
.record-remark {
  color: #606266;
  font-size: 11px;
  margin-top: 2px;
  padding-left: 4px;
}
.node-mode {
  margin-top: 4px;
}
.pending-text, .waiting-text {
  margin-bottom: 4px;
}
</style>
