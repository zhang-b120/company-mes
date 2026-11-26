<template>
  <div class="page-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="关键词">
          <el-input
            v-model="queryParams.keyword"
            placeholder="物料名称"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="锁定类型">
          <el-select v-model="queryParams.reservationType" placeholder="全部" clearable style="width: 140px">
            <el-option label="工单锁定" value="wo_lock" />
            <el-option label="计划锁定" value="plan_lock" />
            <el-option label="订单锁定" value="order_lock" />
            <el-option label="手动锁定" value="manual_lock" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="锁定中" value="active" />
            <el-option label="已释放" value="released" />
            <el-option label="已消耗" value="consumed" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="info" plain @click="handleAddLock">手动锁定</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据表格 -->
    <el-card shadow="never" class="table-card">
      <el-table :data="tableData" v-loading="loading" border stripe size="small">
        <el-table-column prop="materialName" label="物料名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="warehouseName" label="仓库" width="120" show-overflow-tooltip />
        <el-table-column prop="reservationType" label="锁定类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="typeTag[row.reservationType]" size="small">
              {{ typeMap[row.reservationType] || row.reservationType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sourceDesc" label="来源单据" min-width="180" show-overflow-tooltip />
        <el-table-column prop="lockedQty" label="锁定量" width="90" align="right" />
        <el-table-column prop="consumedQty" label="已消耗" width="90" align="right" />
        <el-table-column prop="remainingQty" label="剩余量" width="90" align="right">
          <template #default="{ row }">
            <span :style="{ color: row.remainingQty > 0 ? '#e6a23c' : '#67c23a', fontWeight: 500 }">
              {{ row.remainingQty }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="锁定原因" min-width="140" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag[row.status]" size="small">{{ statusMap[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="锁定时间" width="160" />
        <el-table-column label="操作" width="80" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'active'"
              type="warning"
              link
              size="small"
              @click="handleUnlock(row)"
            >解锁</el-button>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          background
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- 手动锁定弹窗 -->
    <el-dialog v-model="dialogVisible" title="手动锁定库存" width="520px" destroy-on-close @close="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="物料" prop="materialId">
          <el-select v-model="form.materialId" placeholder="请选择物料" filterable remote
                     :remote-method="searchMaterials" :loading="materialSearching" style="width: 100%">
            <el-option
              v-for="item in materialOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="仓库" prop="warehouseId">
          <el-select v-model="form.warehouseId" placeholder="请选择仓库" style="width: 100%">
            <el-option
              v-for="item in warehouseOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="锁定数量" prop="lockedQty">
          <el-input-number v-model="form.lockedQty" :min="1" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="锁定原因" prop="reason">
          <el-input v-model="form.reason" type="textarea" :rows="3" placeholder="请输入锁定原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { lockStock, unlockStock, getReservationPage } from '@/api/inventory'
import { getWarehouseSelect } from '@/api/system'
import { getMaterialPage } from '@/api/master'

const typeMap = { wo_lock: '工单锁定', plan_lock: '计划锁定', order_lock: '订单锁定', manual_lock: '手动锁定' }
const typeTag = { wo_lock: 'primary', plan_lock: 'success', order_lock: 'warning', manual_lock: 'info' }
const statusMap = { active: '锁定中', released: '已释放', consumed: '已消耗' }
const statusTag = { active: 'warning', released: 'info', consumed: 'success' }

// 下拉选项
const materialOptions = ref([])
const warehouseOptions = ref([])

// 查询参数
const queryParams = reactive({
  keyword: '',
  reservationType: undefined,
  status: undefined,
  pageNum: 1,
  pageSize: 20
})

// 表格数据
const loading = ref(false)
const tableData = ref([])
const total = ref(0)

// 弹窗相关
const dialogVisible = ref(false)
const formRef = ref(null)
const submitLoading = ref(false)

const getDefaultForm = () => ({
  materialId: undefined,
  warehouseId: undefined,
  lockedQty: 1,
  reason: ''
})

const form = reactive(getDefaultForm())

const rules = {
  materialId: [{ required: true, message: '请选择物料', trigger: 'change' }],
  warehouseId: [{ required: true, message: '请选择仓库', trigger: 'change' }],
  lockedQty: [{ required: true, message: '请输入锁定数量', trigger: 'blur' }],
  reason: [{ required: true, message: '请输入锁定原因', trigger: 'blur' }]
}

async function loadData() {
  loading.value = true
  try {
    const params = { ...queryParams }
    // keyword 按物料ID筛选（如果有选项匹配的话），这里直接传给后端
    const res = await getReservationPage(params)
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } catch {
    // handled by interceptor
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  try {
    const whRes = await getWarehouseSelect()
    warehouseOptions.value = whRes.data || []
  } catch {}
}

const materialSearching = ref(false)
let _matSearchTimer = null
async function searchMaterials(keyword) {
  if (_matSearchTimer) clearTimeout(_matSearchTimer)
  if (!keyword || !keyword.trim()) { materialOptions.value = []; return }
  _matSearchTimer = setTimeout(async () => {
    materialSearching.value = true
    try {
      const res = await getMaterialPage({ materialName: keyword.trim(), pageNum: 1, pageSize: 30 })
      materialOptions.value = (res.data?.records || []).map(m => ({ id: m.id, name: m.materialName }))
    } catch {} finally { materialSearching.value = false }
  }, 300)
}

function handleSearch() {
  queryParams.pageNum = 1
  loadData()
}

function handleReset() {
  queryParams.keyword = ''
  queryParams.reservationType = undefined
  queryParams.status = undefined
  queryParams.pageNum = 1
  loadData()
}

function resetForm() {
  const defaults = getDefaultForm()
  Object.keys(defaults).forEach(key => {
    form[key] = defaults[key]
  })
}

function handleAddLock() {
  resetForm()
  dialogVisible.value = true
}

async function handleSubmit() {
  await formRef.value.validate()
  submitLoading.value = true
  try {
    await lockStock({ ...form })
    ElMessage.success('库存锁定成功')
    dialogVisible.value = false
    loadData()
  } catch {
    // handled by interceptor
  } finally {
    submitLoading.value = false
  }
}

function handleUnlock(row) {
  ElMessageBox.confirm(`确定解锁「${row.materialName}」的锁定库存吗？`, '提示', { type: 'warning' })
    .then(async () => {
      try {
        await unlockStock(row.id)
        ElMessage.success('解锁成功')
        loadData()
      } catch {
        // handled by interceptor
      }
    })
    .catch(() => {})
}

onMounted(() => {
  loadOptions()
  loadData()
})
</script>

<style scoped lang="scss">
.page-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.search-card {
  :deep(.el-card__body) {
    padding-bottom: 2px;
  }
}
.table-card {
  flex: 1;
}
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.text-muted {
  color: #c0c4cc;
}
</style>
