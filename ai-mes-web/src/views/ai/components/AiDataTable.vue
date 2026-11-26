<template>
  <div class="ai-data-table" v-if="visibleRecords.length > 0">
    <div class="table-header">
      <span class="table-title">
        {{ data.type === 'input_form' ? '解析数据' : '查询结果' }}
        ({{ data.records.length }}条)
      </span>
      <el-button
        v-if="data.type === 'input_form'"
        size="small"
        type="primary"
        @click="$emit('fill', data)"
      >填入表单</el-button>
    </div>
    <el-table :data="visibleRecords" size="small" border stripe max-height="280">
      <el-table-column
        v-for="col in columns"
        :key="col"
        :prop="col"
        :label="col"
        :min-width="100"
        show-overflow-tooltip
      />
    </el-table>
    <div v-if="data.records.length > 10" class="table-footer">
      共 {{ data.records.length }} 条，仅显示前10条
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  data: { type: Object, required: true }
})

defineEmits(['fill'])

const excludeKeys = new Set(['id', 'deleted', 'tenantId', 'tenant_id', 'create_by', 'update_by', 'create_time', 'update_time'])

const columns = computed(() => {
  if (!props.data?.records?.length) return []
  return Object.keys(props.data.records[0])
    .filter(k => !excludeKeys.has(k))
    .slice(0, 8)
})

const visibleRecords = computed(() => {
  if (!props.data?.records) return []
  return props.data.records.slice(0, 10)
})
</script>

<style scoped>
.ai-data-table {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  overflow: hidden;
}
.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: #f5f7fa;
  font-size: 13px;
}
.table-title {
  font-weight: 500;
  color: #606266;
}
.table-footer {
  text-align: center;
  padding: 6px;
  font-size: 12px;
  color: #909399;
  background: #fafafa;
}
</style>
