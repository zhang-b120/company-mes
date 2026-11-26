<template>
  <div class="page-container">
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="模板名称">
          <el-input v-model="queryParams.templateName" placeholder="模板名称" clearable style="width:180px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="设备类型">
          <el-select v-model="queryParams.equipmentType" placeholder="全部" clearable style="width:160px">
            <el-option v-for="opt in EQUIPMENT_CATEGORY_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="primary" plain @click="handleAdd">新增模板</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column label="模板名称" prop="templateName" min-width="180" />
        <el-table-column label="设备类型" prop="equipmentType" min-width="140" />
        <el-table-column label="周期类型" width="110" align="center">
          <template #default="{ row }">{{ { DAY: '天', WEEK: '周', MONTH: '月', HOUR: '小时' }[row.cycleType] || '-' }}</template>
        </el-table-column>
        <el-table-column label="周期值" prop="cycleValue" width="100" align="center" />
        <el-table-column label="备注" prop="remark" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="160" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-popconfirm title="确定删除？" @confirm="handleDelete(row)">
              <template #reference><el-button link type="danger" size="small">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
          :total="total" :page-sizes="[10, 20, 50]" background layout="total, sizes, prev, pager, next"
          @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <!-- 编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="860px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="模板名称" prop="templateName"><el-input v-model="formData.templateName" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="设备类型">
              <el-select v-model="formData.equipmentType" placeholder="请选择" clearable style="width:100%" @change="onEquipmentTypeChange">
                <el-option v-for="opt in EQUIPMENT_CATEGORY_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="关联设备">
          <el-select v-model="formData.equipmentIds" multiple filterable collapse-tags collapse-tags-tooltip
            :placeholder="formData.equipmentType ? '请选择关联设备' : '请先选择设备类型'"
            :disabled="!formData.equipmentType"
            style="width:100%">
            <el-option v-for="eq in equipmentOptions" :key="eq.id" :label="`${eq.equipmentNo} - ${eq.equipmentName}`" :value="eq.id" />
          </el-select>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="周期类型" prop="cycleType">
              <el-select v-model="formData.cycleType" style="width:100%">
                <el-option label="天" value="DAY" />
                <el-option label="周" value="WEEK" />
                <el-option label="月" value="MONTH" />
                <el-option label="小时" value="HOUR" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="周期值" prop="cycleValue"><el-input-number v-model="formData.cycleValue" :min="1" style="width:100%" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="备注"><el-input v-model="formData.remark" type="textarea" :rows="2" /></el-form-item>

        <el-divider content-position="left">检查项</el-divider>
        <div style="margin-bottom:8px">
          <el-button size="small" @click="addItem">+ 添加检查项</el-button>
        </div>
        <el-table :data="formData.items" border size="small">
          <el-table-column type="index" width="45" />
          <el-table-column label="分组"><template #default="{ row }"><el-input v-model="row.itemGroup" size="small" /></template></el-table-column>
          <el-table-column label="项目名称"><template #default="{ row }"><el-input v-model="row.itemName" size="small" /></template></el-table-column>
          <el-table-column label="方法"><template #default="{ row }"><el-input v-model="row.method" size="small" /></template></el-table-column>
          <el-table-column label="标准"><template #default="{ row }"><el-input v-model="row.standard" size="small" /></template></el-table-column>
          <el-table-column label="单位" width="80"><template #default="{ row }"><el-input v-model="row.uom" size="small" /></template></el-table-column>
          <el-table-column label="必检" width="55" align="center"><template #default="{ row }"><el-checkbox v-model="row.required" :true-value="1" :false-value="0" /></template></el-table-column>
          <el-table-column label="照片" width="55" align="center"><template #default="{ row }"><el-checkbox v-model="row.needPhoto" :true-value="1" :false-value="0" /></template></el-table-column>
          <el-table-column label="操作" width="55" align="center"><template #default="{ $index }"><el-button link type="danger" size="small" @click="formData.items.splice($index, 1)">删除</el-button></template></el-table-column>
        </el-table>
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
import { ElMessage } from 'element-plus'
import {
  getMaintenanceTemplatePage, getMaintenanceTemplateDetail,
  createMaintenanceTemplate, updateMaintenanceTemplate, deleteMaintenanceTemplate,
  getEquipmentList
} from '@/api/eam'
import { EQUIPMENT_CATEGORY_OPTIONS } from '../constants.js'

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const total = ref(0)
const queryParams = reactive({ templateName: '', equipmentType: '', pageNum: 1, pageSize: 10 })

async function loadData() {
  loading.value = true
  try {
    const res = await getMaintenanceTemplatePage({ ...queryParams })
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally { loading.value = false }
}
function handleSearch() { queryParams.pageNum = 1; loadData() }
function handleReset() { Object.assign(queryParams, { templateName: '', equipmentType: '', pageNum: 1 }); loadData() }

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref(null)
const formData = reactive({ id: null, templateName: '', equipmentType: '', cycleType: 'DAY', cycleValue: 30, remark: '', items: [], equipmentIds: [] })
const formRules = {
  templateName: [{ required: true, message: '请输入模板名称', trigger: 'blur' }],
  cycleType: [{ required: true, message: '请选择周期类型', trigger: 'change' }],
  cycleValue: [{ required: true, message: '请输入周期值', trigger: 'blur' }]
}

// 关联设备下拉(按设备类型联动)
const equipmentOptions = ref([])
async function loadEquipmentOptions(category) {
  if (!category) { equipmentOptions.value = []; return }
  const res = await getEquipmentList(category)
  equipmentOptions.value = res.data || []
}
function onEquipmentTypeChange(val) {
  // 切换设备类型时清空已选关联设备
  formData.equipmentIds = []
  loadEquipmentOptions(val)
}

function resetForm() {
  Object.assign(formData, { id: null, templateName: '', equipmentType: '', cycleType: 'DAY', cycleValue: 30, remark: '', items: [], equipmentIds: [] })
  equipmentOptions.value = []
}

function handleAdd() { dialogTitle.value = '新增保养模板'; resetForm(); dialogVisible.value = true }

async function handleEdit(row) {
  dialogTitle.value = '编辑保养模板'
  resetForm()
  const res = await getMaintenanceTemplateDetail(row.id)
  const d = res.data || {}
  Object.assign(formData, d.template || {})
  formData.items = d.items || []
  formData.equipmentIds = (d.equipmentIds || []).map(i => Number(i))
  if (formData.equipmentType) await loadEquipmentOptions(formData.equipmentType)
  dialogVisible.value = true
}

function addItem() {
  formData.items.push({ itemGroup: '', itemName: '', method: '', standard: '', uom: '', required: 1, needPhoto: 0 })
}

async function handleSubmit() {
  await formRef.value.validate()
  submitLoading.value = true
  try {
    if (formData.id) {
      await updateMaintenanceTemplate(formData.id, formData)
      ElMessage.success('更新成功')
    } else {
      await createMaintenanceTemplate(formData)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadData()
  } finally { submitLoading.value = false }
}

async function handleDelete(row) {
  await deleteMaintenanceTemplate(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(() => { loadData() })
</script>

<style scoped lang="scss">
.page-container { display: flex; flex-direction: column; gap: 16px; }
.search-card :deep(.el-card__body) { padding-bottom: 2px; }
.table-card { flex: 1; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
