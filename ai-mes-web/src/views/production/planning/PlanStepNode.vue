<template>
  <div class="plan-step-node" :class="{ outsource: step?._handleType === 'outsource', selected }">
    <Handle type="target" :position="Position.Left" />
    <div class="node-header">
      <span class="step-badge">{{ step?.stepNo }}</span>
      <span class="step-name">{{ step?.stepName || '未命名' }}</span>
    </div>
    <div class="node-body">
      <div v-if="step?.standardTime" class="info-row">{{ step.standardTime }} 天</div>
      <div v-if="assignLabel" class="info-row assign-row">{{ assignLabel }}</div>
      <div v-if="timeLabel" class="info-row time-row">{{ timeLabel }}</div>
    </div>
    <div class="node-tags" v-if="step?._handleType === 'outsource' || step?._qcTemplateId || step?.qcTemplateId">
      <span v-if="step._handleType === 'outsource'" class="tag tag-outsource">外协</span>
      <span v-if="step._qcTemplateId || step.qcTemplateId" class="tag tag-qc">质检</span>
    </div>
    <Handle type="source" :position="Position.Right" />
  </div>
</template>

<script setup>
import { computed, inject } from 'vue'
import { Handle, Position } from '@vue-flow/core'

const props = defineProps({
  data: { type: Object, required: true },
  selected: { type: Boolean, default: false }
})

const configSteps = inject('planConfigSteps', { value: [] })
const teams = inject('planTeamOptions', { value: [] })
const assignees = inject('planAssigneeOptions', { value: [] })

const step = computed(() => configSteps.value?.[props.data._stepIndex])

const assignLabel = computed(() => {
  const s = step.value
  if (!s) return ''
  if (s._assignType === 'person' && s._assigneeId) {
    return assignees.value?.find(u => u.id === s._assigneeId)?.realName || ''
  }
  if (s._teamId) {
    return teams.value?.find(t => t.id === s._teamId)?.teamName || ''
  }
  return ''
})

const timeLabel = computed(() => {
  const s = step.value
  if (!s?._planStartTime && !s?._planEndTime) return ''
  const fmt = (t) => t ? t.replace('T', ' ').substring(5, 16) : ''
  return `${fmt(s._planStartTime)} ~ ${fmt(s._planEndTime)}`
})
</script>

<style scoped>
.plan-step-node {
  min-width: 140px;
  max-width: 220px;
  padding: 10px 14px;
  border: 2px solid #409eff;
  border-radius: 8px;
  background: #ecf5ff;
  text-align: center;
  cursor: pointer;
  transition: all 0.2s;
  font-family: inherit;
}
.plan-step-node.outsource {
  border-color: #e6a23c;
  background: #fdf6ec;
}
.plan-step-node.selected {
  border-color: #337ecc;
  box-shadow: 0 0 0 3px rgba(64, 158, 255, 0.3);
}
.plan-step-node.outsource.selected {
  border-color: #b88230;
  box-shadow: 0 0 0 3px rgba(230, 162, 60, 0.3);
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
.outsource .step-badge {
  background: #e6a23c;
}
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
.assign-row {
  color: #409eff;
  font-weight: 500;
}
.time-row {
  font-size: 11px;
  color: #909399;
}
.node-tags {
  display: flex;
  justify-content: center;
  gap: 4px;
  margin-top: 4px;
}
.tag {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 3px;
  line-height: 16px;
}
.tag-outsource {
  background: #faecd8;
  color: #e6a23c;
  border: 1px solid #e6a23c;
}
.tag-qc {
  background: #e1f3d8;
  color: #67c23a;
  border: 1px solid #67c23a;
}
</style>
