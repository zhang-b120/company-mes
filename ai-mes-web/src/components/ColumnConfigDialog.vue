<template>
  <el-drawer
    v-model="visible"
    title="列配置"
    direction="rtl"
    size="340px"
    :close-on-click-modal="true"
    @opened="initSortable"
  >
    <div class="col-config-tip">拖动 <el-icon><Grid /></el-icon> 调整顺序，开关控制显示/隐藏</div>
    <ul ref="listRef" class="col-config-list">
      <li
        v-for="col in localColumns"
        :key="col.key"
        class="col-config-item"
        :data-key="col.key"
      >
        <el-icon class="drag-handle"><Grid /></el-icon>
        <span class="col-label">
          {{ col.label }}
          <el-tag v-if="col.type === 'extension'" size="small" type="success" class="ext-tag">扩展</el-tag>
        </span>
        <el-switch v-model="col.visible" />
      </li>
    </ul>

    <template #footer>
      <el-button @click="handleReset">重置默认</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
    </template>
  </el-drawer>
</template>

<script setup>
import { ref } from 'vue'
import Sortable from 'sortablejs'
import { Grid } from '@element-plus/icons-vue'

const props = defineProps({
  allColumns:     { type: Array, default: () => [] },
  defaultColumns: { type: Array, default: () => [] }
})

const emit = defineEmits(['save'])

const visible = ref(false)
const saving = ref(false)
const localColumns = ref([])
const listRef = ref(null)
let sortable = null

function open() {
  localColumns.value = props.allColumns.map(c => ({ ...c }))
  visible.value = true
}

// @opened 保证抽屉动画完成后 DOM 已就绪
function initSortable() {
  if (sortable) { sortable.destroy(); sortable = null }
  if (!listRef.value) return
  sortable = Sortable.create(listRef.value, {
    handle: '.drag-handle',
    animation: 150,
    onEnd({ oldIndex, newIndex }) {
      if (oldIndex === newIndex) return
      const moved = localColumns.value.splice(oldIndex, 1)[0]
      localColumns.value.splice(newIndex, 0, moved)
    }
  })
}

function handleReset() {
  localColumns.value = props.defaultColumns.map((col, i) => ({
    ...col, visible: true, sortOrder: i
  }))
}

async function handleSave() {
  saving.value = true
  try {
    emit('save', localColumns.value)
    visible.value = false
  } finally {
    saving.value = false
  }
}

defineExpose({ open })
</script>

<style scoped>
.col-config-tip {
  font-size: 12px;
  color: #909399;
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 4px;
}
.col-config-list {
  list-style: none;
  padding: 0;
  margin: 0;
}
.col-config-item {
  display: flex;
  align-items: center;
  padding: 10px 4px;
  border-bottom: 1px solid #f0f0f0;
  gap: 10px;
  background: #fff;
}
.col-config-item:last-child {
  border-bottom: none;
}
.drag-handle {
  cursor: grab;
  color: #c0c4cc;
  font-size: 16px;
  flex-shrink: 0;
}
.drag-handle:active {
  cursor: grabbing;
}
.col-label {
  flex: 1;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.ext-tag {
  flex-shrink: 0;
}
</style>
