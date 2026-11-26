<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <el-card shadow="never" class="search-card">
      <el-form inline>
        <el-form-item label="快照日期">
          <el-date-picker v-model="selectedDate" type="date" placeholder="选择日期" clearable
            value-format="YYYY-MM-DD" style="width:180px" @change="loadData" />
        </el-form-item>
        <el-form-item label="物料编号">
          <el-input v-model="filter.materialNo" placeholder="模糊搜索" clearable style="width:140px" />
        </el-form-item>
        <el-form-item label="物料名称">
          <el-input v-model="filter.materialName" placeholder="模糊搜索" clearable style="width:140px" />
        </el-form-item>
        <el-form-item label="仓库">
          <el-input v-model="filter.warehouseName" placeholder="模糊搜索" clearable style="width:140px" />
        </el-form-item>
        <el-form-item label="库位">
          <el-input v-model="filter.locationName" placeholder="模糊搜索" clearable style="width:140px" />
        </el-form-item>
        <el-form-item>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据表格 -->
    <el-card shadow="never" class="table-card">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <div>
            <span v-if="snapshotDate" class="snapshot-info">
              快照日期：<b>{{ snapshotDate }}</b>
              <span style="margin-left:24px">记录条数：<b>{{ allItems.length }}</b></span>
            </span>
            <span v-else-if="!loading" class="snapshot-info" style="color:#909399">暂无快照数据</span>
          </div>
          <div>
            <el-button type="success" :icon="Download" @click="handleExport">导出</el-button>
            <el-button :icon="Setting" @click="colConfigDialogRef.open()">列配置</el-button>
          </div>
        </div>
      </template>
      <el-table :data="filteredItems" v-loading="loading" border stripe>
        <el-table-column v-for="col in visibleColumns" :key="col.key"
          :label="col.label" :min-width="col.minWidth || 100" :width="col.width" :align="col.align || 'left'" show-overflow-tooltip>
          <template #default="{ row }">
            <template v-if="col.key === 'category'">{{ categoryLabel(row.category) }}</template>
            <template v-else-if="col.key === 'spec'">{{ $fmtSpec(row.spec) }}</template>
            <template v-else-if="col.key === 'locationName'">{{ row.locationName || '-' }}</template>
            <template v-else>{{ row[col.key] || '-' }}</template>
          </template>
        </el-table-column>
      </el-table>

      <!-- 列配置 -->
      <ColumnConfigDialog ref="colConfigDialogRef" :all-columns="allColumns" :default-columns="SNAPSHOT_DEFAULT_COLUMNS" @save="saveConfig" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { Setting, Download } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getSnapshotByDate, exportSnapshot } from '@/api/inventory'
import { useColumnConfig } from '@/composables/useColumnConfig'
import ColumnConfigDialog from '@/components/ColumnConfigDialog.vue'

const SNAPSHOT_DEFAULT_COLUMNS = [
  { key: 'materialNo', label: '物料编号', width: 120 },
  { key: 'materialName', label: '物料名称', minWidth: 140 },
  { key: 'spec', label: '规格型号', width: 120 },
  { key: 'unit', label: '单位', width: 60, align: 'center' },
  { key: 'category', label: '类别', width: 100, align: 'center' },
  { key: 'warehouseName', label: '仓库', width: 120 },
  { key: 'locationName', label: '库位', width: 100 },
  { key: 'totalQty', label: '在库数量', width: 110, align: 'right' },
]
const colConfigDialogRef = ref(null)
const { visibleColumns, allColumns, loadColumnConfig, saveConfig } = useColumnConfig('inventory_snapshot', SNAPSHOT_DEFAULT_COLUMNS)

const loading = ref(false)
const selectedDate = ref('')
const snapshotDate = ref('')
const allItems = ref([])

const filter = reactive({
  materialNo: '',
  materialName: '',
  warehouseName: '',
  locationName: ''
})

const categoryMap = {
  raw: '原材料', raw_material: '原材料',
  semi: '半成品', semi_finished: '半成品',
  finished: '成品', finished_goods: '成品',
  consumable: '辅料耗材',
  auxiliary: '辅料',
  packaging: '包装材料',
  tool: '工具',
  product: '成品', material: '原材料'
}
function categoryLabel(val) { return categoryMap[val] || val || '-' }

const filteredItems = computed(() => {
  let items = allItems.value
  if (filter.materialNo) {
    const kw = filter.materialNo.toLowerCase()
    items = items.filter(i => (i.materialNo || '').toLowerCase().includes(kw))
  }
  if (filter.materialName) {
    const kw = filter.materialName.toLowerCase()
    items = items.filter(i => (i.materialName || '').toLowerCase().includes(kw))
  }
  if (filter.warehouseName) {
    const kw = filter.warehouseName.toLowerCase()
    items = items.filter(i => (i.warehouseName || '').toLowerCase().includes(kw))
  }
  if (filter.locationName) {
    const kw = filter.locationName.toLowerCase()
    items = items.filter(i => (i.locationName || '').toLowerCase().includes(kw))
  }
  return items
})

async function loadData() {
  loading.value = true
  try {
    const res = await getSnapshotByDate(selectedDate.value || '')
    snapshotDate.value = res.data?.snapshotDate || ''
    allItems.value = res.data?.items || []
    // 同步选中日期
    if (!selectedDate.value && snapshotDate.value) {
      selectedDate.value = snapshotDate.value
    }
  } finally {
    loading.value = false
  }
}

function handleReset() {
  filter.materialNo = ''
  filter.materialName = ''
  filter.warehouseName = ''
  filter.locationName = ''
  selectedDate.value = ''
  loadData()
}

async function handleExport() {
  try {
    const blob = await exportSnapshot(selectedDate.value || '')
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `历史库存台账_${selectedDate.value || snapshotDate.value || '最新'}.xlsx`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (e) {
    ElMessage.error('导出失败')
  }
}

onMounted(() => { loadData(); loadColumnConfig() })
</script>

<style scoped lang="scss">
.page-container { padding: 16px; }
.search-card { margin-bottom: 16px; }
.snapshot-info {
  margin-bottom: 12px;
  font-size: 14px;
  color: #606266;
}
</style>
