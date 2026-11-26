<template>
  <div class="page-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="方案名称">
          <el-input v-model="queryParams.schemeName" placeholder="方案名称" clearable style="width: 150px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="方案类型">
          <el-select v-model="queryParams.schemeType" placeholder="全部" clearable style="width: 120px">
            <el-option label="抽检" value="sampling" />
            <el-option label="全检" value="full" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="primary" plain @click="openDialog(null)">新增方案</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never" class="table-card">
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="schemeName" label="方案名称" min-width="150" />
        <el-table-column prop="schemeType" label="方案类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.schemeType === 'full' ? 'success' : 'primary'" size="small">
              {{ row.schemeType === 'full' ? '全检' : '抽检' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="抽检方式" width="120" align="center">
          <template #default="{ row }">
            <template v-if="row.schemeType === 'sampling'">{{ samplingModeText[row.samplingMode] || '-' }}</template>
            <span v-else style="color:#c0c4cc">—</span>
          </template>
        </el-table-column>
        <el-table-column label="抽检数量/比例" width="130" align="center">
          <template #default="{ row }">
            <template v-if="row.samplingMode === 'first_piece' || row.samplingMode === 'last_piece'">1</template>
            <template v-else-if="row.samplingMode === 'custom_qty'">{{ row.samplingQty }}</template>
            <template v-else-if="row.samplingMode === 'ratio'">{{ row.samplingRatio }}%{{ row.ratioRoundUp ? '(向上取整)' : '' }}</template>
            <span v-else style="color:#c0c4cc">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small">{{ row.status === 'active' ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="160">
          <template #default="{ row }">{{ $fmtDate(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openDialog(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50]" :total="total" background layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑质检方案' : '新增质检方案'" width="550px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="方案名称" prop="schemeName">
          <el-input v-model="form.schemeName" placeholder="请输入方案名称" />
        </el-form-item>
        <el-form-item label="方案类型" prop="schemeType">
          <el-radio-group v-model="form.schemeType">
            <el-radio label="sampling">抽检</el-radio>
            <el-radio label="full">全检</el-radio>
          </el-radio-group>
        </el-form-item>
        <template v-if="form.schemeType === 'sampling'">
          <el-form-item label="抽检模式" prop="samplingMode">
            <el-select v-model="form.samplingMode" placeholder="请选择" style="width: 100%">
              <el-option label="首件检（数量固定为1）" value="first_piece" />
              <el-option label="末件检（数量固定为1）" value="last_piece" />
              <el-option label="自选数量" value="custom_qty" />
              <el-option label="比例" value="ratio" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="form.samplingMode === 'custom_qty'" label="抽检数量">
            <el-input-number v-model="form.samplingQty" :min="1" :precision="0" style="width: 100%" />
          </el-form-item>
          <el-form-item v-if="form.samplingMode === 'ratio'" label="抽检比例(%)">
            <el-input-number v-model="form.samplingRatio" :min="0.01" :max="100" :precision="2" style="width: 200px" />
          </el-form-item>
          <el-form-item v-if="form.samplingMode === 'ratio'" label="向上取整">
            <el-switch v-model="form.ratioRoundUp" :active-value="1" :inactive-value="0" />
          </el-form-item>
        </template>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio label="active">启用</el-radio>
            <el-radio label="inactive">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="备注" />
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
import { getQcSchemePage, createQcScheme, updateQcScheme, deleteQcScheme } from '@/api/quality'

const samplingModeText = { first_piece: '首件检', last_piece: '末件检', custom_qty: '自选数量', ratio: '比例' }

// ==================== 列表 ====================
const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const queryParams = reactive({ schemeName: '', schemeType: '', pageNum: 1, pageSize: 20 })

async function loadData() {
  loading.value = true
  try {
    const res = await getQcSchemePage(queryParams)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch {} finally { loading.value = false }
}

function handleSearch() { queryParams.pageNum = 1; loadData() }
function handleReset() { queryParams.schemeName = ''; queryParams.schemeType = ''; queryParams.pageNum = 1; loadData() }

// ==================== 弹窗 ====================
const dialogVisible = ref(false)
const submitLoading = ref(false)
const editingId = ref(null)
const formRef = ref(null)
const form = reactive({
  schemeName: '', schemeType: 'sampling', samplingMode: '', samplingQty: 5,
  samplingRatio: 10, ratioRoundUp: 1, status: 'active', remark: ''
})
const rules = {
  schemeName: [{ required: true, message: '请输入方案名称', trigger: 'blur' }],
  schemeType: [{ required: true, message: '请选择方案类型', trigger: 'change' }]
}

function openDialog(row) {
  editingId.value = row?.id || null
  if (row) {
    Object.assign(form, { schemeName: row.schemeName, schemeType: row.schemeType, samplingMode: row.samplingMode || '',
      samplingQty: row.samplingQty || 5, samplingRatio: row.samplingRatio || 10,
      ratioRoundUp: row.ratioRoundUp ?? 1, status: row.status || 'active', remark: row.remark || '' })
  } else {
    Object.assign(form, { schemeName: '', schemeType: 'sampling', samplingMode: '', samplingQty: 5,
      samplingRatio: 10, ratioRoundUp: 1, status: 'active', remark: '' })
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    if (editingId.value) {
      await updateQcScheme(editingId.value, form)
      ElMessage.success('修改成功')
    } else {
      await createQcScheme(form)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadData()
  } catch {} finally { submitLoading.value = false }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除质检方案「${row.schemeName}」？`, '提示', { type: 'warning' })
    await deleteQcScheme(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch {}
}

onMounted(() => loadData())
</script>

<style scoped lang="scss">
.page-container { display: flex; flex-direction: column; gap: 12px; }
.search-card { :deep(.el-card__body) { padding-bottom: 2px; } }
.table-card { flex: 1; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 12px; }
</style>
