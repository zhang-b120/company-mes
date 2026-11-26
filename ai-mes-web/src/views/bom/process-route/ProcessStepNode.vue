<template>
  <div class="step-node" :class="{ outsource: data.isOutsource, selected: selected }">
    <Handle type="target" :position="Position.Left" />
    <div class="step-title">{{ data.stepName || '未命名工序' }}</div>
    <div class="step-info" v-if="data.standardTime">{{ data.standardTime }}天</div>
    <div class="step-tags">
      <span v-if="data.isOutsource" class="tag tag-outsource">外协</span>
      <span v-if="data.qcTemplateName" class="tag tag-qc">质检: {{ data.qcTemplateName }}</span>
    </div>
    <Handle type="source" :position="Position.Right" />
  </div>
</template>

<script setup>
import { Handle, Position } from '@vue-flow/core'

const props = defineProps({
  data: { type: Object, required: true },
  selected: { type: Boolean, default: false }
})
</script>

<style scoped>
.step-node {
  min-width: 120px;
  max-width: 200px;
  padding: 12px 16px;
  border: 2px solid #409eff;
  border-radius: 8px;
  background: #ecf5ff;
  text-align: center;
  cursor: pointer;
  transition: all 0.2s;
  font-family: inherit;
}
.step-node.outsource {
  border-color: #e6a23c;
  background: #fdf6ec;
}
.step-node.selected {
  border-color: #337ecc;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.3);
}
.step-node.outsource.selected {
  border-color: #b88230;
  box-shadow: 0 0 0 2px rgba(230, 162, 60, 0.3);
}
.step-title {
  font-weight: 600;
  font-size: 14px;
  color: #303133;
  margin-bottom: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.step-info {
  font-size: 12px;
  color: #606266;
  margin-bottom: 4px;
}
.step-tags {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 4px;
}
.tag {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 3px;
  line-height: 18px;
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
