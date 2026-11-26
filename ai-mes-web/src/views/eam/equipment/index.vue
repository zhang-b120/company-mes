<template>
  <div class="page-container">
    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stat-row">
      <el-col :span="6">
        <div class="stat-card stat-card--clickable" :class="{ active: !queryParams.status }" @click="filterByStatus('')">
          <div class="stat-card__value" style="color:#409eff">{{ stats.total || 0 }}</div>
          <div class="stat-card__label">设备总数</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card stat-card--clickable" :class="{ active: queryParams.status === 'NORMAL' }" @click="filterByStatus('NORMAL')">
          <div class="stat-card__value" style="color:#67c23a">{{ stats.normal || 0 }}</div>
          <div class="stat-card__label">正常</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card stat-card--clickable" :class="{ active: queryParams.status === 'MAINTENANCE' }" @click="filterByStatus('MAINTENANCE')">
          <div class="stat-card__value" style="color:#e6a23c">{{ stats.maintenance || 0 }}</div>
          <div class="stat-card__label">维修中</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card stat-card--clickable" :class="{ active: queryParams.status === 'RETIRED' }" @click="filterByStatus('RETIRED')">
          <div class="stat-card__value" style="color:#909399">{{ stats.retired || 0 }}</div>
          <div class="stat-card__label">已报废</div>
        </div>
      </el-col>
    </el-row>

    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="设备编号">
          <el-input v-model="queryParams.equipmentNo" placeholder="设备编号" clearable style="width:160px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="设备名称">
          <el-input v-model="queryParams.equipmentName" placeholder="设备名称" clearable style="width:160px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="设备类型">
          <el-select v-model="queryParams.category" placeholder="全部" clearable style="width:160px">
            <el-option v-for="opt in EQUIPMENT_CATEGORY_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width:130px">
            <el-option label="正常" value="NORMAL" />
            <el-option label="维修中" value="MAINTENANCE" />
            <el-option label="已报废" value="RETIRED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="primary" plain @click="handleAdd">新增设备</el-button>
          <el-button type="success" plain @click="importDialogVisible = true">导入</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据表格 -->
    <el-card class="table-card" shadow="never">
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column label="设备编号" prop="equipmentNo" min-width="130" />
        <el-table-column label="设备名称" prop="equipmentName" min-width="140" />
        <el-table-column label="型号" prop="model" min-width="120" />
        <el-table-column label="设备类型" prop="category" min-width="120" />
        <el-table-column label="位置" prop="location" min-width="140" />
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="下次维护" prop="nextMaintenanceDate" width="120" />
        <el-table-column label="操作" width="240" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleView(row)">查看</el-button>
            <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.status !== 'NORMAL'" link type="success" size="small" @click="handleEnable(row)">启用</el-button>
            <el-button v-else link type="warning" size="small" @click="handleDisable(row)">停用</el-button>
            <el-popconfirm title="确定删除该设备？" @confirm="handleDelete(row)">
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

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="720px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="设备编号">
              <el-input v-model="formData.equipmentNo" :disabled="!!formData.id" placeholder="留空自动生成" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="设备名称" prop="equipmentName">
              <el-input v-model="formData.equipmentName" placeholder="设备名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="型号"><el-input v-model="formData.model" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="设备类型" prop="category">
              <el-select v-model="formData.category" placeholder="请选择设备类型" clearable style="width:100%">
                <el-option v-for="opt in EQUIPMENT_CATEGORY_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="位置"><el-input v-model="formData.location" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="购置日期">
              <el-date-picker v-model="formData.purchaseDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="启用日期">
              <el-date-picker v-model="formData.enableDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="维护周期(天)"><el-input-number v-model="formData.maintenanceCycle" :min="0" style="width:100%" /></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="设备价值"><el-input-number v-model="formData.equipmentValue" :min="0" :precision="2" style="width:100%" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="折旧年限"><el-input-number v-model="formData.depreciationYears" :min="0" style="width:100%" /></el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="规格"><el-input v-model="formData.specification" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="参数"><el-input v-model="formData.parameters" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="附件">
          <AttachmentUploader v-model="formData.attachmentUrls" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="formData.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 查看详情抽屉 -->
    <el-drawer v-model="detailVisible" :title="`设备详情 - ${detailData?.equipmentName || ''}`" size="640px" destroy-on-close>
      <template v-if="detailData">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="编号">{{ detailData.equipmentNo }}</el-descriptions-item>
          <el-descriptions-item label="名称">{{ detailData.equipmentName }}</el-descriptions-item>
          <el-descriptions-item label="型号">{{ detailData.model || '-' }}</el-descriptions-item>
          <el-descriptions-item label="设备类型">{{ detailData.category || '-' }}</el-descriptions-item>
          <el-descriptions-item label="位置">{{ detailData.location || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态"><el-tag :type="statusTagType(detailData.status)" size="small">{{ statusText(detailData.status) }}</el-tag></el-descriptions-item>
          <el-descriptions-item label="购置日期">{{ detailData.purchaseDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="启用日期">{{ detailData.enableDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="维护周期(天)">{{ detailData.maintenanceCycle || '-' }}</el-descriptions-item>
          <el-descriptions-item label="下次维护">{{ detailData.nextMaintenanceDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="设备价值">{{ detailData.equipmentValue || '-' }}</el-descriptions-item>
          <el-descriptions-item label="折旧年限">{{ detailData.depreciationYears || '-' }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="detailData.attachmentUrls" style="margin-top:12px">
          <div style="font-weight:600;margin-bottom:8px;color:#303133">附件</div>
          <AttachmentList :value="detailData.attachmentUrls" />
        </div>
      </template>
    </el-drawer>

    <!-- 更新运行状态 -->
    <el-dialog v-model="statusDialogVisible" title="更新运行状态" width="520px" destroy-on-close>
      <el-form :model="statusForm" label-width="100px">
        <el-form-item label="运行状态">
          <el-select v-model="statusForm.runningStatus" style="width:100%">
            <el-option label="运行" value="RUNNING" />
            <el-option label="停止" value="STOPPED" />
            <el-option label="维修" value="MAINTENANCE" />
          </el-select>
        </el-form-item>
        <el-form-item label="温度"><el-input-number v-model="statusForm.currentTemperature" :precision="2" style="width:100%" /></el-form-item>
        <el-form-item label="压力"><el-input-number v-model="statusForm.currentPressure" :precision="2" style="width:100%" /></el-form-item>
        <el-form-item label="转速"><el-input-number v-model="statusForm.currentSpeed" :precision="2" style="width:100%" /></el-form-item>
        <el-form-item label="负载"><el-input-number v-model="statusForm.currentLoad" :precision="2" style="width:100%" /></el-form-item>
        <el-form-item label="报警状态">
          <el-select v-model="statusForm.alarmStatus" style="width:100%">
            <el-option label="正常" value="NORMAL" />
            <el-option label="警告" value="WARNING" />
            <el-option label="严重" value="CRITICAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="报警信息"><el-input v-model="statusForm.alarmMessage" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="statusDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveStatus">保存</el-button>
      </template>
    </el-dialog>

    <!-- 导入对话框 -->
    <el-dialog v-model="importDialogVisible" title="导入设备" width="520px" destroy-on-close>
      <el-alert type="info" :closable="false" style="margin-bottom: 12px">请先下载模板，按模板格式填写后上传。设备编号必须唯一。</el-alert>
      <div style="margin-bottom: 16px;">
        <el-button type="primary" link @click="handleDownloadTemplate">
          <el-icon style="margin-right: 4px"><Download /></el-icon>下载导入模板
        </el-button>
      </div>
      <el-upload ref="uploadRef" :auto-upload="false" :limit="1" accept=".xlsx,.xls" :on-change="handleFileChange" drag>
        <el-icon :size="40" color="#c0c4cc"><Upload /></el-icon>
        <div style="margin-top: 8px; color: #909399">将文件拖到此处，或点击上传</div>
      </el-upload>
      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="importLoading" @click="handleImport">确认导入</el-button>
      </template>
    </el-dialog>

    <!-- 停用对话框 -->
    <el-dialog v-model="disableVisible" title="停用设备" width="380px">
      <el-form label-width="90px">
        <el-form-item label="目标状态">
          <el-radio-group v-model="disableStatus">
            <el-radio value="MAINTENANCE">维修中</el-radio>
            <el-radio value="RETIRED">已报废</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="disableVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmDisable">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Upload } from '@element-plus/icons-vue'
import AttachmentUploader from '@/components/AttachmentUploader.vue'
import AttachmentList from '@/components/AttachmentList.vue'
import {
  getEquipmentPage, createEquipment, updateEquipment, deleteEquipment,
  enableEquipment, disableEquipment, getEquipmentStatistics,
  getEquipmentDetail, getEquipmentStatus, updateEquipmentStatus,
  downloadEquipmentTemplate, importEquipment
} from '@/api/eam'
import { EQUIPMENT_CATEGORY_OPTIONS } from '../constants.js'

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const total = ref(0)
const stats = ref({})
const queryParams = reactive({ equipmentNo: '', equipmentName: '', category: '', status: '', pageNum: 1, pageSize: 10 })

function statusText(s) { return { NORMAL: '正常', MAINTENANCE: '维修中', RETIRED: '已报废' }[s] || s }
function statusTagType(s) { return { NORMAL: 'success', MAINTENANCE: 'warning', RETIRED: 'info' }[s] || '' }
function runningStatusText(s) { return { RUNNING: '运行中', STOPPED: '停止', MAINTENANCE: '维修中' }[s] || s || '-' }
function runningStatusType(s) { return { RUNNING: 'success', STOPPED: 'info', MAINTENANCE: 'warning' }[s] || '' }

async function loadData() {
  loading.value = true
  try {
    const res = await getEquipmentPage({ ...queryParams })
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally { loading.value = false }
}

async function loadStats() {
  const res = await getEquipmentStatistics()
  stats.value = res.data || {}
}

function handleSearch() { queryParams.pageNum = 1; loadData() }
function handleReset() { Object.assign(queryParams, { equipmentNo: '', equipmentName: '', category: '', status: '', pageNum: 1 }); loadData() }
function filterByStatus(status) {
  queryParams.status = queryParams.status === status ? '' : status
  queryParams.pageNum = 1
  loadData()
}

// 新增/编辑
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref(null)
const formData = reactive({})
const formRules = {
  equipmentName: [{ required: true, message: '请输入设备名称', trigger: 'blur' }],
  category: [{ required: true, message: '请选择设备类型', trigger: 'change' }]
}

function resetForm() {
  Object.keys(formData).forEach(k => delete formData[k])
  Object.assign(formData, {
    equipmentNo: '', equipmentName: '', model: '', category: '', location: '',
    purchaseDate: '', enableDate: '', maintenanceCycle: null, equipmentValue: null,
    depreciationYears: null, specification: '', parameters: '', remark: '', attachmentUrls: []
  })
}

function handleAdd() { dialogTitle.value = '新增设备'; resetForm(); dialogVisible.value = true }
function handleEdit(row) {
  dialogTitle.value = '编辑设备'
  resetForm()
  Object.assign(formData, row)
  dialogVisible.value = true
}

async function handleSubmit() {
  await formRef.value.validate()
  submitLoading.value = true
  try {
    if (formData.id) {
      await updateEquipment(formData.id, formData)
      ElMessage.success('更新成功')
    } else {
      await createEquipment(formData)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadData(); loadStats()
  } finally { submitLoading.value = false }
}

async function handleDelete(row) {
  await deleteEquipment(row.id)
  ElMessage.success('删除成功')
  loadData(); loadStats()
}

async function handleEnable(row) {
  await enableEquipment(row.id)
  ElMessage.success('已启用')
  loadData(); loadStats()
}

// 停用
const disableVisible = ref(false)
const disableStatus = ref('MAINTENANCE')
const disableRow = ref(null)
function handleDisable(row) { disableRow.value = row; disableStatus.value = 'MAINTENANCE'; disableVisible.value = true }
async function confirmDisable() {
  await disableEquipment(disableRow.value.id, disableStatus.value)
  ElMessage.success('已停用')
  disableVisible.value = false
  loadData(); loadStats()
}

// 查看详情
const detailVisible = ref(false)
const detailData = ref(null)
const statusData = ref(null)
async function handleView(row) {
  const res = await getEquipmentDetail(row.id)
  detailData.value = res.data
  const s = await getEquipmentStatus(row.id)
  statusData.value = s.data
  detailVisible.value = true
}

// 运行状态更新
const statusDialogVisible = ref(false)
const statusForm = reactive({})
function openStatusDialog() {
  Object.keys(statusForm).forEach(k => delete statusForm[k])
  Object.assign(statusForm, statusData.value || {})
  statusDialogVisible.value = true
}
async function saveStatus() {
  await updateEquipmentStatus(detailData.value.id, statusForm)
  ElMessage.success('已更新')
  statusDialogVisible.value = false
  const s = await getEquipmentStatus(detailData.value.id)
  statusData.value = s.data
}

// 导入
const importDialogVisible = ref(false)
const importLoading = ref(false)
const importFile = ref(null)
const uploadRef = ref(null)

function handleFileChange(file) { importFile.value = file.raw }

async function handleDownloadTemplate() {
  const res = await downloadEquipmentTemplate()
  const blob = new Blob([res], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = '设备导入模板.xlsx'
  link.click()
  window.URL.revokeObjectURL(url)
}

async function handleImport() {
  if (!importFile.value) { ElMessage.warning('请先选择文件'); return }
  importLoading.value = true
  try {
    const res = await importEquipment(importFile.value)
    ElMessage.success(res.data || '导入完成')
    importDialogVisible.value = false
    importFile.value = null
    uploadRef.value?.clearFiles()
    loadData()
    loadStats()
  } finally {
    importLoading.value = false
  }
}

onMounted(() => { loadData(); loadStats() })
</script>

<style scoped lang="scss">
.page-container { display: flex; flex-direction: column; gap: 16px; }
.stat-row { flex-shrink: 0; }
.stat-card {
  background: #fff; border-radius: 8px; padding: 16px; text-align: center;
  box-shadow: 0 1px 4px rgba(0,0,0,.06);
  border: 2px solid transparent;
  transition: all .2s;
  &__value { font-size: 28px; font-weight: 700; }
  &__label { font-size: 13px; color: #909399; margin-top: 4px; }
}
.stat-card--clickable { cursor: pointer; }
.stat-card--clickable:hover { box-shadow: 0 2px 12px rgba(64,158,255,.18); transform: translateY(-1px); }
.stat-card--clickable.active { border-color: #409eff; background: #ecf5ff; }
.search-card :deep(.el-card__body) { padding-bottom: 2px; }
.table-card { flex: 1; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
