<template>
  <div class="wo-step-node" :class="{ outsource: step?.isOutsourced, qc: step?.qcStatus === 'pending' }">
    <Handle type="target" :position="Position.Left" />
    <div class="node-header">
      <span class="step-badge">{{ step?.stepNo }}</span>
      <span class="step-name">{{ step?.stepName || '未命名' }}</span>
    </div>
    <div class="node-body">
      <div v-if="step?.standardTime" class="info-row">{{ step.standardTime }} 天</div>
      <div v-if="assignLabel" class="info-row assign-row">{{ assignLabel }}</div>
      <div class="info-row status-row">
        <span class="status-dot" :class="step?.status" />
        {{ statusText }}
      </div>
    </div>
    <div class="node-tags">
      <span v-if="step?.isOutsourced" class="tag tag-outsource">外协</span>
      <span v-if="step?.isOutsourceable && !step?.isOutsourced" class="tag tag-outsourceable">可外协</span>
      <span v-if="step?.qcStatus === 'pending'" class="tag tag-qc">待检</span>
      <span v-if="step?.qcStatus === 'pass'" class="tag tag-qc-pass">合格</span>
      <span v-if="step?.qcStatus === 'fail'" class="tag tag-qc-fail">不合格</span>
    </div>
    <Handle type="source" :position="Position.Right" />
  </div>
</template>

<script setup>
import { computed, inject } from 'vue'
import { Handle, Position } from '@vue-flow/core'

const props = defineProps({
  data: { type: Object, required: true }
})

const woTasks = inject('woTasks', { value: [] })

const step = computed(() => woTasks.value?.[props.data._taskIndex])

const assignLabel = computed(() => {
  const s = step.value
  if (!s) return ''
  if (s.assignType === 'team' && s.assignedTeamName) return s.assignedTeamName
  if (s.assignType === 'person' && s.assignedUserName) return s.assignedUserName
  return ''
})

const statusMap = {
  pending: '待开工',
  ready: '可开工',
  in_progress: '进行中',
  completed: '已完成',
  paused: '已暂停',
  pending_qc: '待质检',
  blocked: '已阻塞',
  skipped: '已跳过',
  cancelled: '已取消'
}

const statusText = computed(() => statusMap[step.value?.status] || step.value?.status || '-')
</script>

<style scoped>
.wo-step-node {
  min-width: 140px;
  max-width: 220px;
  padding: 10px 14px;
  border: 2px solid #409eff;
  border-radius: 8px;
  background: #ecf5ff;
  text-align: center;
  cursor: default;
  font-family: inherit;
}
.wo-step-node.outsource {
  border-color: #e6a23c;
  background: #fdf6ec;
}
.wo-step-node.qc {
  border-color: #67c23a;
  background: #f0f9eb;
}
.node-header {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-bottom: 6px;
}
.step-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #409eff;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  flex-shrink: 0;
}
.outsource .step-badge { background: #e6a23c; }
.step-name {
  font-weight: 600;
  font-size: 13px;
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.node-body {
  font-size: 12px;
  color: #606266;
}
.info-row {
  margin-bottom: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.assign-row { color: #409eff; font-weight: 500; }
.status-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}
.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #909399;
}
.status-dot.pending { background: #909399; }
.status-dot.ready { background: #409eff; }
.status-dot.in_progress { background: #e6a23c; }
.status-dot.completed { background: #67c23a; }
.status-dot.paused { background: #f56c6c; }
.node-tags {
  display: flex;
  justify-content: center;
  gap: 4px;
  margin-top: 4px;
  flex-wrap: wrap;
}
.tag {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 3px;
  line-height: 16px;
}
.tag-outsource { background: #faecd8; color: #e6a23c; border: 1px solid #e6a23c; }
.tag-outsourceable { background: #f4f4f5; color: #909399; border: 1px solid #dcdfe6; }
.tag-qc { background: #fdf6ec; color: #e6a23c; border: 1px solid #e6a23c; }
.tag-qc-pass { background: #e1f3d8; color: #67c23a; border: 1px solid #67c23a; }
.tag-qc-fail { background: #fde2e2; color: #f56c6c; border: 1px solid #f56c6c; }
</style>
