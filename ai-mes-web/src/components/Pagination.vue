<!--
  通用分页组件
  封装 Element Plus el-pagination，提供统一的分页交互
  用法:
    <Pagination v-model:pageNum="pageNum" v-model:pageSize="pageSize" :total="total" @change="loadData" />
-->
<template>
  <div class="pagination-wrapper">
    <el-pagination
      v-model:current-page="currentPage"
      v-model:page-size="currentPageSize"
      :page-sizes="pageSizes"
      :total="total"
      :background="background"
      layout="total, sizes, prev, pager, next, jumper"
      @size-change="handleChange"
      @current-change="handleChange"
    />
  </div>
</template>

<script setup>
/**
 * @prop {number} total        - 数据总条数
 * @prop {number} pageNum      - 当前页码（v-model）
 * @prop {number} pageSize     - 每页条数（v-model）
 * @prop {number[]} pageSizes  - 每页条数选项
 * @prop {boolean} background  - 是否为分页按钮添加背景色
 */
import { computed } from 'vue'

const props = defineProps({
  total: {
    type: Number,
    default: 0
  },
  pageNum: {
    type: Number,
    default: 1
  },
  pageSize: {
    type: Number,
    default: 20
  },
  pageSizes: {
    type: Array,
    default: () => [10, 20, 50, 100]
  },
  background: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits(['update:pageNum', 'update:pageSize', 'change'])

/** 当前页码，双向绑定 */
const currentPage = computed({
  get: () => props.pageNum,
  set: (val) => emit('update:pageNum', val)
})

/** 每页条数，双向绑定 */
const currentPageSize = computed({
  get: () => props.pageSize,
  set: (val) => emit('update:pageSize', val)
})

/** 页码或每页条数变化时触发 */
function handleChange() {
  emit('change')
}
</script>

<style scoped>
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
