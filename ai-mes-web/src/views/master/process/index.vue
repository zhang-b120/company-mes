<template>
  <div class="page-container">
    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-form :model="queryParams" inline>
        <el-form-item label="关键词">
          <el-input v-model="queryParams.keyword" placeholder="工序名称" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 工具栏 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <el-button type="primary" @click="handleAdd">新增工序</el-button>
        <el-button type="success" :icon="Download" @click="handleExport">导出</el-button>
        <el-button type="warning" :icon="Upload" @click="importDialogVisible = true">导入</el-button>
        <el-button :icon="Setting" @click="colConfigDialogRef.open()">列配置</el-button>
      </div>
      <div class="toolbar-right">
        <span style="color: #909399; font-size: 13px">共 {{ total }} 条</span>
      </div>
    </div>

    <!-- 数据表格 -->
    <el-table :data="tableData" v-loading="loading" border stripe size="small">
      <el-table-column
        v-for="col in visibleColumns" :key="col.key"
        :label="col.label"
        :min-width="col.minWidth || 100"
        :width="col.width"
        :align="col.align || 'left'"
        show-overflow-tooltip>
        <template #default="{ row }">
          <template v-if="col.key === 'standardTime'">{{ row.standardTime != null ? row.standardTime : '-' }}</template>
          <template v-else-if="col.key === 'piecePrice'">
            <span v-if="row.piecePrice != null">{{ Number(row.piecePrice).toFixed(2) }}</span>
            <span v-else style="color: #c0c4cc">-</span>
          </template>
          <template v-else-if="col.key === 'bindInfo'">
            <template v-if="row.bindType === 'team'">
              <el-tag size="small" type="primary">班组</el-tag>
              <span style="margin-left:4px">{{ getBindNames(row) }}</span>
            </template>
            <template v-else-if="row.bindType === 'employee'">
              <el-tag size="small" type="success">员工</el-tag>
              <span style="margin-left:4px">{{ getBindNames(row) }}</span>
            </template>
            <span v-else style="color:#c0c4cc">未绑定</span>
          </template>
          <template v-else-if="col.key === 'sopDescription'">{{ row.sopDescription || '-' }}</template>
          <template v-else>{{ row[col.key] || '-' }}</template>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right" align="center">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button type="info" link size="small" @click="handleCopy(row)">复制</el-button>
          <el-button type="warning" link size="small" @click="handleViewReferences(row)">查看引用</el-button>
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
        layout="total, sizes, prev, pager, next, jumper"
        background
        @size-change="loadData"
        @current-change="loadData"
      />
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog :title="dialogTitle" v-model="dialogVisible" width="700px" destroy-on-close @closed="onDialogClosed">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="130px">
        <el-form-item label="工序名称" prop="processName">
          <el-input v-model="form.processName" placeholder="请输入工序名称（2-20字符）" maxlength="20" show-word-limit />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="默认标准工时/天" prop="standardTime">
              <el-input-number
                v-model="form.standardTime"
                :min="0"
                :precision="0"
                :step="1"
                controls-position="right"
                style="width: 100%"
                placeholder="请输入"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="默认计件单价/元" prop="piecePrice">
              <el-input-number
                v-model="form.piecePrice"
                :min="0"
                :precision="2"
                controls-position="right"
                style="width: 100%"
                placeholder="请输入"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="绑定类型">
          <el-radio-group v-model="form.bindType" @change="onBindTypeChange">
            <el-radio value="none">不绑定</el-radio>
            <el-radio value="team">班组</el-radio>
            <el-radio value="employee">员工</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.bindType === 'team'" label="绑定班组">
          <el-select v-model="form.bindIdList" multiple placeholder="请选择班组" filterable style="width: 100%">
            <el-option v-for="t in teamOptions" :key="t.id" :label="t.teamName" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.bindType === 'employee'" label="绑定员工">
          <el-select v-model="form.bindIdList" multiple placeholder="请选择员工" filterable style="width: 100%">
            <el-option v-for="u in userOptions" :key="u.id" :label="u.realName || u.username" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="SOP描述" prop="sopDescription">
          <el-input
            v-model="form.sopDescription"
            type="textarea"
            :rows="4"
            placeholder="请输入SOP描述"
          />
        </el-form-item>
        <el-form-item label="SOP附件" prop="sopAttachmentUrls">
          <AttachmentUploader v-model="form.sopAttachmentUrls" accept="image/*,.pdf" :limit="5"
            tip="支持图片和 PDF 格式,最多 5 个,单个不超过 50MB" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 查看引用弹窗 -->
    <el-dialog v-model="refDialogVisible" title="工序引用详情" width="600px" destroy-on-close>
      <template v-if="refTarget">
        <p style="margin-bottom: 12px; color: #606266">
          工序「<strong>{{ refTarget.processName }}</strong>」被以下工艺路线引用：
        </p>
        <el-table :data="refList" v-loading="refLoading" border stripe size="small" max-height="400">
          <el-table-column prop="processName" label="工序名称" min-width="140" show-overflow-tooltip />
          <el-table-column prop="stepNo" label="工序位置" width="120" align="center">
            <template #default="{ row }">
              第 {{ row.stepNo ?? '-' }} 道工序
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!refLoading && refList.length === 0" description="暂无引用" />
      </template>
      <template #footer>
        <el-button @click="refDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 导入弹窗 -->
    <el-dialog v-model="importDialogVisible" title="导入工序" width="500px" destroy-on-close>
      <el-alert type="info" :closable="false" style="margin-bottom: 12px">
        请先下载模板，按模板格式填写后上传。支持 xlsx/xls 格式。
      </el-alert>
      <div style="margin-bottom: 16px">
        <el-button type="primary" link @click="handleDownloadTemplate">下载导入模板</el-button>
      </div>
      <el-upload
        ref="importUploadRef"
        :auto-upload="false"
        :limit="1"
        accept=".xlsx,.xls"
        :on-change="handleImportFileChange"
        drag
      >
        <el-icon :size="40" color="#c0c4cc"><Upload /></el-icon>
        <div style="margin-top: 8px; color: #909399">将文件拖到此处，或点击上传</div>
      </el-upload>
      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="importLoading" @click="handleImport">确认导入</el-button>
      </template>
    </el-dialog>
    <ColumnConfigDialog
      ref="colConfigDialogRef"
      :all-columns="allColumns"
      :default-columns="MASTER_PROCESS_DEFAULT_COLUMNS"
      @save="saveConfig"
    />

    <!-- 班组成员弹窗 -->
    <el-dialog v-model="teamMemberVisible" :title="`${teamMemberName} - 成员列表`" width="500px" destroy-on-close>
      <el-table :data="teamMembers" v-loading="teamMemberLoading" border stripe size="small">
        <el-table-column prop="userName" label="姓名" min-width="100">
          <template #default="{ row }">
            {{ row.userName }} <el-tag v-if="row.isLeader" size="small" type="warning" style="margin-left:4px">组长</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="weight" label="权重" width="80" align="center">
          <template #default="{ row }">{{ row.weight ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="joinedAt" label="加入时间" width="160" />
      </el-table>
      <el-empty v-if="!teamMemberLoading && teamMembers.length === 0" description="暂无成员" />
      <template #footer>
        <el-button @click="teamMemberVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Upload, Setting } from '@element-plus/icons-vue'
import { useColumnConfig } from '@/composables/useColumnConfig'
import ColumnConfigDialog from '@/components/ColumnConfigDialog.vue'
import AttachmentUploader from '@/components/AttachmentUploader.vue'
import {
  getProcessLibraryPage,
  createProcess,
  updateProcess,
  getProcessReferences,
  exportProcesses,
  importProcesses,
  downloadProcessTemplate
} from '@/api/master'
import { getTeamList, getTeamMembers } from '@/api/team'
import { getUserSelect, getBizConfigList } from '@/api/system'

// ==================== 常量 ====================

const MASTER_PROCESS_DEFAULT_COLUMNS = [
  { key: 'processName', label: '工序名称', minWidth: 140 },
  { key: 'standardTime', label: '默认标准工时(天)', width: 160, align: 'right' },
  { key: 'piecePrice', label: '默认计件单价(元)', width: 150, align: 'right' },
  { key: 'bindInfo', label: '绑定信息', minWidth: 160 },
  { key: 'sopDescription', label: 'SOP描述', minWidth: 180 },
]
const colConfigDialogRef = ref(null)
const { visibleColumns, allColumns, loadColumnConfig, saveConfig } = useColumnConfig('master_process', MASTER_PROCESS_DEFAULT_COLUMNS)


// ==================== 班组 ====================

const teamOptions = ref([])
const teamMap = computed(() => {
  const map = {}
  teamOptions.value.forEach(t => { map[t.id] = t.teamName })
  return map
})

async function loadTeamOptions() {
  try {
    const res = await getTeamList()
    teamOptions.value = res.data || []
  } catch { /* ignore */ }
}

const userOptions = ref([])

async function loadUserOptions() {
  try {
    const res = await getUserSelect()
    userOptions.value = res.data || []
  } catch { /* ignore */ }
}

function onBindTypeChange() {
  form.bindIdList = []
}

function getBindNames(row) {
  if (!row.bindIds) return ''
  try {
    const ids = JSON.parse(row.bindIds)
    if (row.bindType === 'team') {
      return ids.map(id => teamOptions.value.find(t => t.id === id)?.teamName || id).join(', ')
    } else if (row.bindType === 'employee') {
      return ids.map(id => userOptions.value.find(u => u.id === id)?.realName || id).join(', ')
    }
  } catch { return '' }
  return ''
}

// ==================== 查询 ====================

const queryParams = reactive({
  keyword: '',
  pageNum: 1,
  pageSize: 10
})

const loading = ref(false)
const tableData = ref([])
const total = ref(0)

async function loadData() {
  loading.value = true
  try {
    const res = await getProcessLibraryPage(queryParams)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch {
    // handled by interceptor
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  queryParams.pageNum = 1
  loadData()
}

function handleReset() {
  queryParams.keyword = ''
  queryParams.pageNum = 1
  loadData()
}

// ==================== 新增/编辑弹窗 ====================

const dialogVisible = ref(false)
const dialogTitle = ref('新增工序')
const formRef = ref(null)
const submitLoading = ref(false)
const editingId = ref(null)

const getDefaultForm = () => ({
  processName: '',
  standardTime: undefined,
  piecePrice: undefined,
  teamId: null,
  bindType: 'none',
  bindIdList: [],
  sopDescription: '',
  sopAttachmentUrls: ''
})

const form = reactive(getDefaultForm())

const reportStatisticsEnabled = ref(false)

const rules = computed(() => ({
  processName: [
    { required: true, message: '请输入工序名称', trigger: 'blur' },
    { min: 2, max: 20, message: '工序名称长度为2-20个字符', trigger: 'blur' }
  ],
  piecePrice: reportStatisticsEnabled.value ? [{ required: true, message: '员工报工统计已启用，计件单价为必填项', trigger: 'blur' }] : []
}))

function resetForm() {
  const defaults = getDefaultForm()
  Object.keys(defaults).forEach(key => {
    form[key] = defaults[key]
  })
}

function onDialogClosed() {
  formRef.value?.resetFields()
}

function handleAdd() {
  resetForm()
  editingId.value = null
  dialogTitle.value = '新增工序'
  dialogVisible.value = true
}

function handleEdit(row) {
  resetForm()
  editingId.value = row.id
  dialogTitle.value = '编辑工序'
  form.processName = row.processName || ''
  form.standardTime = row.standardTime ?? undefined
  form.piecePrice = row.piecePrice ?? undefined
  form.teamId = row.teamId ?? undefined
  form.bindType = row.bindType || 'none'
  form.bindIdList = row.bindIds ? JSON.parse(row.bindIds) : []
  form.sopDescription = row.sopDescription || ''
  form.sopAttachmentUrls = row.sopAttachmentUrls || ''
  dialogVisible.value = true
}

// 复制工序
function handleCopy(row) {
  resetForm()
  editingId.value = null
  dialogTitle.value = '复制工序（新建）'
  form.processName = (row.processName || '') + '-副本'
  form.standardTime = row.standardTime ?? undefined
  form.piecePrice = row.piecePrice ?? undefined
  form.teamId = row.teamId ?? undefined
  form.bindType = row.bindType || 'none'
  form.bindIdList = row.bindIds ? JSON.parse(row.bindIds) : []
  form.sopDescription = row.sopDescription || ''
  form.sopAttachmentUrls = row.sopAttachmentUrls || ''
  dialogVisible.value = true
  ElMessage.info('已复制工序信息，修改后保存为新工序')
}

async function handleSubmit() {
  await formRef.value.validate()
  submitLoading.value = true
  try {
    const payload = {
      processName: form.processName,
      standardTime: form.standardTime,
      piecePrice: form.piecePrice,
      teamId: form.bindType === 'team' && form.bindIdList.length > 0 ? form.bindIdList[0] : null,
      bindType: form.bindType,
      bindIds: form.bindIdList.length > 0 ? JSON.stringify(form.bindIdList) : null,
      sopDescription: form.sopDescription,
      sopAttachmentUrls: form.sopAttachmentUrls
    }
    if (editingId.value) {
      await updateProcess(editingId.value, payload)
      ElMessage.success('更新成功')
    } else {
      await createProcess(payload)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadData()
  } catch {
    // handled by interceptor
  } finally {
    submitLoading.value = false
  }
}

// ==================== 查看引用 ====================

const refDialogVisible = ref(false)
const refTarget = ref(null)
const refList = ref([])
const refLoading = ref(false)

async function handleViewReferences(row) {
  refTarget.value = row
  refList.value = []
  refDialogVisible.value = true
  refLoading.value = true
  try {
    const res = await getProcessReferences(row.id)
    refList.value = res.data || []
  } catch {
    // handled by interceptor
  } finally {
    refLoading.value = false
  }
}

// ==================== 导入导出 ====================

const importDialogVisible = ref(false)
const importLoading = ref(false)
const importFile = ref(null)
const importUploadRef = ref(null)

function handleImportFileChange(file) {
  importFile.value = file.raw
}

function downloadBlob(data, filename) {
  const blob = new Blob([data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  window.URL.revokeObjectURL(url)
}

async function handleExport() {
  try {
    const res = await exportProcesses()
    downloadBlob(res, '工序库列表.xlsx')
    ElMessage.success('导出成功')
  } catch {
    // handled by interceptor
  }
}

async function handleDownloadTemplate() {
  try {
    const res = await downloadProcessTemplate()
    downloadBlob(res, '工序导入模板.xlsx')
  } catch {
    // handled by interceptor
  }
}

async function handleImport() {
  if (!importFile.value) {
    ElMessage.warning('请先选择文件')
    return
  }
  importLoading.value = true
  try {
    await importProcesses(importFile.value)
    ElMessage.success('导入成功')
    importDialogVisible.value = false
    importFile.value = null
    loadData()
  } catch {
    // handled by interceptor
  } finally {
    importLoading.value = false
  }
}

// ==================== 班组成员查看 ====================

const teamMemberVisible = ref(false)
const teamMemberName = ref('')
const teamMembers = ref([])
const teamMemberLoading = ref(false)

async function handleViewTeamMembers(teamId) {
  teamMemberName.value = teamMap.value[teamId] || '班组'
  teamMembers.value = []
  teamMemberVisible.value = true
  teamMemberLoading.value = true
  try {
    const res = await getTeamMembers(teamId)
    teamMembers.value = res.data || []
  } catch { /* ignore */ }
  finally { teamMemberLoading.value = false }
}

// ==================== 初始化 ====================

onMounted(() => {
  loadData()
  loadTeamOptions()
  loadUserOptions()
  loadColumnConfig()
  // 加载业务配置：是否启用报工统计
  getBizConfigList('production').then(res => {
    const list = res.data || []
    const cfg = list.find(c => c.configKey === 'production.report_statistics')
    reportStatisticsEnabled.value = cfg && (cfg.configValue || cfg.defaultValue) === 'true'
  }).catch(() => {})
})
</script>

<style scoped lang="scss">
.page-container {
  padding: 16px;
}
.search-bar {
  margin-bottom: 12px;
}
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.toolbar-left {
  display: flex;
  gap: 8px;
}
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
