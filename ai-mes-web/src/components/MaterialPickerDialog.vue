<template>
  <el-dialog
    v-model="visible"
    :title="title"
    width="980px"
    destroy-on-close
    append-to-body
    @open="handleOpen"
  >
    <div style="display: flex; gap: 12px; height: 460px;">
      <!-- 左侧分类树 -->
      <div style="width: 200px; min-width: 200px; border: 1px solid #ebeef5; border-radius: 4px; padding: 10px; overflow-y: auto; display: flex; flex-direction: column; gap: 8px;">
        <div style="font-size: 13px; font-weight: 600; color: #303133;">物料类别</div>
        <el-input v-model="categoryFilter" placeholder="搜索类别" clearable size="small" @input="onCategoryFilterInput" />
        <el-tree
          ref="categoryTreeRef"
          :data="categoryTree"
          :props="{ children: 'children', label: 'categoryName' }"
          node-key="id"
          highlight-current
          default-expand-all
          :filter-node-method="filterCategoryNode"
          style="flex: 1;"
          @node-click="handleCategoryClick"
        />
        <el-button v-if="query.categoryId" type="info" link size="small" @click="clearCategory">清除筛选</el-button>
      </div>

      <!-- 右侧内容 -->
      <div style="flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 10px;">
        <!-- 搜索栏 -->
        <div style="display: flex; gap: 8px; flex-wrap: wrap; align-items: center;">
          <el-input
            v-model="query.materialNo"
            placeholder="物料编号"
            clearable
            style="width: 150px"
            @keyup.enter="handleSearch"
          />
          <el-input
            v-model="query.materialName"
            placeholder="物料名称"
            clearable
            style="width: 150px"
            @keyup.enter="handleSearch"
          />
          <el-input
            v-model="query.spec"
            placeholder="规格(单值或 压力等级:300 材质:NBR)"
            clearable
            style="width: 250px"
            @keyup.enter="handleSearch"
          />
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>

        <!-- 表格 -->
        <el-table
          ref="tableRef"
          :data="list"
          border
          size="small"
          v-loading="loading"
          style="flex: 1;"
          :highlight-current-row="!multiple"
          :row-key="row => row.id"
          :reserve-selection="multiple"
          @selection-change="handleSelectionChange"
          @current-change="handleCurrentChange"
          @row-dblclick="handleRowDblclick"
        >
          <el-table-column v-if="multiple" type="selection" width="40" reserve-selection />
          <el-table-column prop="materialNo" label="物料编号" width="160" class-name="cell-wrap" />
          <el-table-column prop="materialName" label="物料名称" min-width="160" class-name="cell-wrap" />
          <el-table-column label="规格型号" min-width="150" class-name="cell-wrap">
            <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
          </el-table-column>
          <el-table-column prop="unit" label="单位" width="65" align="center" />
          <el-table-column v-if="showStock || warehouseId" label="可用库存" width="90" align="right">
            <template #default="{ row }">
              <span :style="{ color: (row.availableQty || 0) > 0 ? '#67c23a' : '#f56c6c', fontWeight: 600 }">
                {{ row.availableQty ?? '-' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="类型" width="100" align="center">
            <template #default="{ row }">
              {{ row.categoryName || materialTypeLabel(row.materialType || row.category) }}
            </template>
          </el-table-column>
          <el-table-column v-if="!multiple" label="操作" width="70" align="center" fixed="right">
            <template #default="{ row }">
              <el-button size="small" type="primary" link @click="handleRowDblclick(row)">选择</el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 分页 + 操作 -->
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <el-pagination
            v-model:current-page="query.pageNum"
            v-model:page-size="query.pageSize"
            :page-sizes="[10, 20, 50]"
            :total="total"
            layout="total, sizes, prev, pager, next"
            small
            @size-change="loadData"
            @current-change="loadData"
          />
          <div v-if="multiple" style="display: flex; align-items: center; gap: 8px;">
            <span style="font-size: 13px; color: #606266;">已选 {{ selectedRows.length }} 条</span>
            <el-button @click="visible = false">取消</el-button>
            <el-button type="primary" :disabled="selectedRows.length === 0" @click="handleConfirm">确认选择</el-button>
          </div>
          <el-button v-else @click="visible = false">关闭</el-button>
        </div>
      </div>
    </div>

    <template #footer></template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { getMaterialPage, getMaterialCategoryTree } from '@/api/master'
import { getMaterialStocks, getInventoryPage } from '@/api/inventory'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '选择物料' },
  materialAttribute: { type: String, default: undefined },
  multiple: { type: Boolean, default: true },
  // 领料场景: 多显示一列"可用库存", 帮助选择
  showStock: { type: Boolean, default: false },
  // 出库场景: 传指定仓库 ID, 列表只显示该仓库有库存的物料(且 showStock 自动启用)
  warehouseId: { type: [Number, String], default: null }
})

const emit = defineEmits(['update:modelValue', 'select'])

// 物料类型英文 → 中文映射
const MATERIAL_TYPE_MAP = {
  raw_material: '原材料',
  semi_finished: '半成品',
  finished: '成品',
  auxiliary: '辅料',
  packaging: '包材',
  purchase: '外购件'
}
function materialTypeLabel(v) {
  if (!v) return '-'
  return MATERIAL_TYPE_MAP[v] || v
}

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const loading = ref(false)
const list = ref([])
const total = ref(0)
const tableRef = ref(null)
const selectedRows = ref([])
const categoryTree = ref([])
const categoryTreeRef = ref(null)
const categoryFilter = ref('')
const query = reactive({ materialNo: '', materialName: '', spec: '', categoryId: undefined, pageNum: 1, pageSize: 20 })

async function handleOpen() {
  query.materialNo = ''
  query.materialName = ''
  query.spec = ''
  query.categoryId = undefined
  query.pageNum = 1
  selectedRows.value = []
  categoryFilter.value = ''
  // 跨页多选: 清掉历史保留的选中状态, 防上次选的脏数据残留
  if (tableRef.value?.clearSelection) tableRef.value.clearSelection()
  if (!categoryTree.value.length) {
    const res = await getMaterialCategoryTree()
    categoryTree.value = res.data || []
  }
  loadData()
}

async function loadData() {
  loading.value = true
  try {
    // 仓库过滤模式: 直接走 getInventoryPage, 列表来自该仓库的库存台账
    // 字段映射:库存表里 id 是台账 id, 物料 id 用 materialId,要 alias 成 id 让选择和 row-key 一致
    if (props.warehouseId) {
      // 后端 /api/inventory 接受的是 materialNo / materialName 分开两个字段(都走 LIKE 模糊),
      // 不要再混用 keyword (后端没这个字段, 传了等于没过滤)
      const stockRes = await getInventoryPage({
        warehouseIds: String(props.warehouseId),
        materialNo: query.materialNo || undefined,
        materialName: query.materialName || undefined,
        spec: query.spec || undefined,
        categoryId: query.categoryId || undefined,
        pageNum: query.pageNum,
        pageSize: query.pageSize
      })
      const records = stockRes.data?.records || stockRes.data?.list || []
      list.value = records.map(r => ({
        ...r,
        // 通用组件 row-key 用 id, 但仓库台账 id 是台账 id, 物料 id 是 materialId
        // 改成用 materialId 当 id, 避免不同仓库下同物料 id 冲突
        id: r.materialId || r.id,
        availableQty: Number(r.availableQty ?? r.totalQty ?? 0)
      }))
      total.value = stockRes.data?.total || 0
      return
    }
    const params = {
      materialNo: query.materialNo || undefined,
      materialName: query.materialName || undefined,
      spec: query.spec || undefined,
      categoryId: query.categoryId || undefined,
      pageNum: query.pageNum,
      pageSize: query.pageSize
    }
    if (props.materialAttribute) params.materialAttribute = props.materialAttribute
    const res = await getMaterialPage(params)
    list.value = res.data?.records || res.data?.list || []
    total.value = res.data?.total || 0
    // 显示可用库存: 批量查这一页所有物料的库存汇总, 注入 availableQty
    if (props.showStock && list.value.length > 0) {
      try {
        const ids = list.value.map(m => m.id).filter(Boolean)
        const stockRes = await getMaterialStocks(ids)
        const stockMap = stockRes.data || {}
        list.value.forEach(m => {
          const s = stockMap[m.id]
          m.availableQty = s ? Number(s.availableQty ?? s.totalQty ?? 0) : 0
        })
      } catch {
        // 库存查询失败不阻塞列表显示
      }
    }
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.pageNum = 1
  loadData()
}

function handleReset() {
  query.materialNo = ''
  query.materialName = ''
  query.spec = ''
  query.categoryId = undefined
  query.pageNum = 1
  categoryFilter.value = ''
  categoryTreeRef.value?.setCurrentKey(null)
  loadData()
}

function onCategoryFilterInput(val) {
  categoryTreeRef.value?.filter(val)
}

function filterCategoryNode(value, data) {
  if (!value) return true
  return data.categoryName.includes(value)
}

function handleCategoryClick(data) {
  query.categoryId = data.id
  query.pageNum = 1
  loadData()
}

function clearCategory() {
  query.categoryId = undefined
  query.pageNum = 1
  categoryTreeRef.value?.setCurrentKey(null)
  loadData()
}

function handleSelectionChange(rows) {
  selectedRows.value = rows
}

function handleCurrentChange(row) {
  if (!props.multiple && row) selectedRows.value = [row]
}

function handleRowDblclick(row) {
  emit('select', [row])
  visible.value = false
}

function handleConfirm() {
  if (selectedRows.value.length === 0) return
  emit('select', selectedRows.value)
  visible.value = false
}
</script>

<style scoped>
/* 物料编号 / 名称 / 规格 等列允许换行, 不再用 ... 截断 */
:deep(.cell-wrap .cell) {
  white-space: normal;
  word-break: break-all;
  line-height: 1.5;
}
</style>
