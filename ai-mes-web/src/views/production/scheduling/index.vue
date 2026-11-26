<template>
  <div class="page-container">
    <!-- 顶部工具栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="日期范围">
          <el-date-picker
            v-model="queryParams.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            :shortcuts="dateShortcuts"
            style="width: 260px"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button
            type="primary"
            @click="handleAiScheduling"
            :loading="aiLoading"
            style="background-color: #7c3aed; border-color: #7c3aed"
          >
            <el-icon v-if="!aiLoading" style="margin-right: 4px"><Cpu /></el-icon>
            AI排产
          </el-button>
          <el-button type="primary" plain @click="handleAdd">新建排产</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 甘特图区域 -->
    <el-card shadow="never" class="gantt-card" v-loading="loading">
      <template #header>
        <div class="gantt-header">
          <span class="gantt-title">排产甘特图</span>
          <div class="gantt-legend">
            <span class="legend-item"><span class="legend-dot" style="background: #409eff"></span>正常</span>
            <span class="legend-item"><span class="legend-dot" style="background: #e6a23c"></span>外协</span>
            <span class="legend-item"><span class="legend-dot" style="background: #f56c6c"></span>逾期</span>
            <span class="legend-item"><span class="legend-dot" style="background: #67c23a"></span>已完成</span>
          </div>
        </div>
      </template>
      <div ref="ganttContainer" class="gantt-container"></div>
    </el-card>

    <!-- 排产记录表格 -->
    <el-card shadow="never" class="table-card">
      <template #header>
        <div style="display: flex; align-items: center; justify-content: space-between">
          <span>排产记录</span>
          <el-button :icon="Setting" @click="colConfigDialogRef.open()">列配置</el-button>
        </div>
      </template>
      <el-table :data="tableData" v-loading="tableLoading" border stripe>
        <el-table-column v-for="col in visibleColumns" :key="col.key"
          :label="col.label" :min-width="col.minWidth || 100" :align="col.align || 'left'" show-overflow-tooltip>
          <template #default="{ row }">
            <template v-if="col.key === 'status'">
              <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            </template>
            <template v-else>{{ row[col.key] || '-' }}</template>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadTableData"
          @current-change="loadTableData"
        />
      </div>
    </el-card>

    <!-- 新增/编辑排产对话框 -->
    <el-dialog v-model="formVisible" :title="formMode === 'add' ? '新建排产' : '编辑排产'" width="620px" destroy-on-close>
      <el-form :model="formData" :rules="formRules" ref="formRef" label-width="100px">
        <el-form-item label="工单" prop="workOrderId">
          <el-select
            v-model="formData.workOrderId"
            placeholder="请选择工单"
            filterable
            remote
            :remote-method="searchWorkOrders"
            :loading="workOrderSearchLoading"
            :disabled="formMode === 'edit'"
            style="width: 100%"
            @change="onWorkOrderChange"
          >
            <el-option
              v-for="item in workOrderOptions"
              :key="item.id"
              :label="item.woNo + ' - ' + item.productName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="产品名称">
          <el-input v-model="formData.productName" disabled placeholder="选择工单后自动填充" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-input v-model="formData.assigneeName" placeholder="请输入负责人/班组" />
        </el-form-item>
        <el-form-item label="计划开始" prop="plannedStart">
          <el-date-picker
            v-model="formData.plannedStart"
            type="datetime"
            placeholder="选择计划开始时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="计划结束" prop="plannedEnd">
          <el-date-picker
            v-model="formData.plannedEnd"
            type="datetime"
            placeholder="选择计划结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="formData.remark" type="textarea" :rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleFormSubmit">确定</el-button>
      </template>
    </el-dialog>

    <ColumnConfigDialog ref="colConfigDialogRef" :all-columns="allColumns" :default-columns="SCHEDULING_DEFAULT_COLUMNS" @save="saveConfig" />

    <!-- AI排产结果对话框 -->
    <el-dialog v-model="aiResultVisible" title="AI排产建议" width="700px" destroy-on-close>
      <div v-loading="aiLoading">
        <el-alert
          v-if="aiResult"
          title="AI排产分析完成"
          type="success"
          :closable="false"
          show-icon
          style="margin-bottom: 16px"
        />
        <div v-if="aiResult" class="ai-result-content" v-html="aiResultHtml"></div>
        <el-empty v-if="!aiResult && !aiLoading" description="暂无分析结果" />
      </div>
      <template #footer>
        <el-button @click="aiResultVisible = false">关闭</el-button>
        <el-button type="primary" @click="applyAiResult" v-if="aiResult">应用建议</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Cpu, Setting } from '@element-plus/icons-vue'
import { useColumnConfig } from '@/composables/useColumnConfig'
import ColumnConfigDialog from '@/components/ColumnConfigDialog.vue'

const SCHEDULING_DEFAULT_COLUMNS = [
  { key: 'workOrderNo', label: '工单号', minWidth: 160 },
  { key: 'productName', label: '产品名称', minWidth: 150 },
  { key: 'assigneeName', label: '负责人', minWidth: 100 },
  { key: 'plannedStart', label: '计划开始', minWidth: 170 },
  { key: 'plannedEnd', label: '计划结束', minWidth: 170 },
  { key: 'actualStart', label: '实际开始', minWidth: 170 },
  { key: 'actualEnd', label: '实际结束', minWidth: 170 },
  { key: 'status', label: '状态', minWidth: 100, align: 'center' },
]
const colConfigDialogRef = ref(null)
const { visibleColumns, allColumns, loadColumnConfig, saveConfig } = useColumnConfig('production_scheduling', SCHEDULING_DEFAULT_COLUMNS)
import { gantt } from 'dhtmlx-gantt'
import 'dhtmlx-gantt/codebase/dhtmlxgantt.css'
import {
  getSchedulingPage,
  getSchedulingList,
  createScheduling,
  updateScheduling,
  deleteScheduling,
  getWorkOrderPage
} from '@/api/production'
import { aiScheduling } from '@/api/ai'

// ==================== 常量 ====================
const statusOptions = [
  { label: '待开始', value: 'pending' },
  { label: '已排产', value: 'scheduled' },
  { label: '进行中', value: 'in_progress' },
  { label: '已完成', value: 'completed' },
  { label: '已取消', value: 'cancelled' }
]
const statusMap = Object.fromEntries(statusOptions.map(s => [s.value, s.label]))

const dateShortcuts = [
  {
    text: '本周',
    value: () => {
      const start = new Date()
      const day = start.getDay() || 7
      start.setDate(start.getDate() - day + 1)
      const end = new Date(start)
      end.setDate(end.getDate() + 6)
      return [start, end]
    }
  },
  {
    text: '本月',
    value: () => {
      const start = new Date()
      start.setDate(1)
      const end = new Date(start.getFullYear(), start.getMonth() + 1, 0)
      return [start, end]
    }
  },
  {
    text: '近30天',
    value: () => {
      const end = new Date()
      const start = new Date()
      start.setDate(start.getDate() - 30)
      return [start, end]
    }
  }
]

// ==================== 状态辅助函数 ====================
function statusLabel(status) {
  return statusMap[status] || status || '-'
}

function statusTagType(status) {
  const map = {
    pending: 'info',
    scheduled: 'warning',
    in_progress: '',
    completed: 'success',
    cancelled: 'danger'
  }
  return map[status] || 'info'
}

// ==================== 查询参数 ====================
const loading = ref(false)
const tableLoading = ref(false)
const tableData = ref([])
const total = ref(0)
const ganttContainer = ref(null)
let ganttInitialized = false

const queryParams = reactive({
  dateRange: null,
  status: '',
  pageNum: 1,
  pageSize: 20
})

// ==================== 甘特图原始数据 ====================
const allSchedulingData = ref([])

// ==================== 甘特图颜色映射 ====================
function getTaskColor(item) {
  // 已完成 - 绿色
  if (item.status === 'completed') return '#67c23a'
  // 逾期判断 - 红色 (计划结束时间早于当前时间且未完成)
  if (item.plannedEnd && new Date(item.plannedEnd) < new Date() && item.status !== 'completed' && item.status !== 'cancelled') {
    return '#f56c6c'
  }
  // 外协 - 橙色 (通过remark字段包含"外协"判断)
  if (item.remark && item.remark.includes('外协')) return '#e6a23c'
  // 默认 - 蓝色
  return '#409eff'
}

// ==================== 甘特图配置与初始化 ====================
function initGantt() {
  if (!ganttContainer.value) return

  // 配置中文
  gantt.i18n.setLocale({
    date: {
      month_full: ['一月', '二月', '三月', '四月', '五月', '六月', '七月', '八月', '九月', '十月', '十一月', '十二月'],
      month_short: ['1月', '2月', '3月', '4月', '5月', '6月', '7月', '8月', '9月', '10月', '11月', '12月'],
      day_full: ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六'],
      day_short: ['日', '一', '二', '三', '四', '五', '六']
    },
    labels: {
      new_task: '新任务',
      icon_save: '保存',
      icon_cancel: '取消',
      icon_details: '详情',
      icon_edit: '编辑',
      icon_delete: '删除',
      confirm_closing: '',
      confirm_deleting: '确定删除吗？',
      section_description: '描述',
      section_time: '时间',
      confirm_link_deleting: '将被删除',
      link_from: '从',
      link_to: '到',
      link_start: '开始',
      link_end: '结束',
      minutes: '分',
      hours: '时',
      days: '天',
      weeks: '周',
      months: '月',
      years: '年'
    }
  })

  // 左侧列配置
  gantt.config.columns = [
    { name: 'text', label: '工单号', tree: true, width: 160, resize: true },
    { name: 'productName', label: '产品名称', width: 140, resize: true },
    { name: 'planQty', label: '计划数量', width: 80, align: 'center', resize: true },
    { name: 'assigneeName', label: '负责人', width: 100, resize: true }
  ]

  // 时间轴配置
  gantt.config.scale_unit = 'day'
  gantt.config.date_scale = '%m月%d日'
  gantt.config.scale_height = 60
  gantt.config.subscales = [
    { unit: 'hour', step: 6, date: '%H:00' }
  ]
  gantt.config.min_column_width = 50

  // 自适应
  gantt.config.autofit = false
  gantt.config.autosize = 'y'
  gantt.config.autosize_min_y = 400

  // 行高
  gantt.config.row_height = 36
  gantt.config.bar_height = 24

  // 允许拖拽和调整大小
  gantt.config.drag_move = true
  gantt.config.drag_resize = true
  gantt.config.drag_progress = false
  gantt.config.drag_links = false

  // 只读模式关闭（允许编辑）
  gantt.config.readonly = false

  // 禁用自带的弹窗编辑（使用自定义对话框）
  gantt.config.details_on_create = false
  gantt.config.details_on_dblclick = false

  // 日期格式
  gantt.config.date_format = '%Y-%m-%d %H:%i:%s'
  gantt.config.xml_date = '%Y-%m-%d %H:%i:%s'

  // Grid宽度
  gantt.config.grid_width = 490

  // 工具提示
  gantt.plugins({ tooltip: true })
  gantt.templates.tooltip_text = function (start, end, task) {
    return `<b>工单号:</b> ${task.text}<br/>
            <b>产品:</b> ${task.productName || '-'}<br/>
            <b>负责人:</b> ${task.assigneeName || '-'}<br/>
            <b>开始:</b> ${gantt.templates.tooltip_date_format(start)}<br/>
            <b>结束:</b> ${gantt.templates.tooltip_date_format(end)}<br/>
            <b>状态:</b> ${statusLabel(task.schedulingStatus)}`
  }

  // 任务栏样式 - 根据状态着色
  gantt.templates.task_class = function (start, end, task) {
    return 'custom-task-color'
  }

  // 通过task_row_class可以给行添加样式
  gantt.templates.task_row_class = function (start, end, task) {
    if (task.schedulingStatus === 'completed') return 'completed-row'
    return ''
  }

  // 任务文字
  gantt.templates.task_text = function (start, end, task) {
    return task.text || ''
  }

  // 初始化
  gantt.init(ganttContainer.value)
  ganttInitialized = true

  // 事件：单击任务打开编辑
  gantt.attachEvent('onTaskClick', function (id, e) {
    const task = gantt.getTask(id)
    if (task && task.rawData) {
      handleEdit(task.rawData)
    }
    return true
  })

  // 事件：拖拽结束后更新排产
  gantt.attachEvent('onAfterTaskDrag', function (id, mode, e) {
    const task = gantt.getTask(id)
    if (task && task.rawData) {
      const updatedStart = formatGanttDate(task.start_date)
      const updatedEnd = formatGanttDate(task.end_date)
      updateSchedulingDates(task.rawData.id, updatedStart, updatedEnd)
    }
  })
}

// ==================== 日期格式化 ====================
function formatGanttDate(date) {
  if (!date) return ''
  const d = new Date(date)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function parseDate(str) {
  if (!str) return null
  return new Date(str.replace(/-/g, '/'))
}

// ==================== 拖拽更新排产日期 ====================
async function updateSchedulingDates(id, plannedStart, plannedEnd) {
  try {
    await updateScheduling(id, { plannedStart, plannedEnd })
    ElMessage.success('排产时间已更新')
    loadTableData()
  } catch {
    // 失败则重新加载甘特图恢复原状
    ElMessage.error('更新失败，正在恢复')
    loadGanttData()
  }
}

// ==================== 加载甘特图数据 ====================
async function loadGanttData() {
  loading.value = true
  try {
    const params = {}
    if (queryParams.dateRange && queryParams.dateRange.length === 2) {
      params.startDate = queryParams.dateRange[0]
      params.endDate = queryParams.dateRange[1]
    }
    if (queryParams.status) {
      params.status = queryParams.status
    }
    const res = await getSchedulingList(params)
    const data = res.data
    let list = []
    if (Array.isArray(data)) {
      list = data
    } else {
      list = data?.records || data?.list || []
    }
    allSchedulingData.value = list
    renderGantt(list)
  } finally {
    loading.value = false
  }
}

// ==================== 渲染甘特图 ====================
function renderGantt(list) {
  if (!ganttInitialized) return

  gantt.clearAll()

  const tasks = list
    .filter(item => item.plannedStart && item.plannedEnd)
    .map((item, index) => {
      const color = getTaskColor(item)
      return {
        id: item.id || index + 1,
        text: item.workOrderNo || `排产-${item.id}`,
        start_date: item.plannedStart,
        end_date: item.plannedEnd,
        productName: item.productName || '-',
        planQty: item.planQty || '-',
        assigneeName: item.assigneeName || '-',
        schedulingStatus: item.status,
        color: color,
        rawData: item
      }
    })

  gantt.parse({ data: tasks, links: [] })

  // 如果有数据，自动滚动到第一条任务的开始日期
  if (tasks.length > 0) {
    const firstStart = tasks.reduce((min, t) => {
      const d = parseDate(t.start_date)
      return d && d < min ? d : min
    }, parseDate(tasks[0].start_date) || new Date())
    if (firstStart) {
      gantt.showDate(firstStart)
    }
  }
}

// ==================== 加载表格数据 ====================
async function loadTableData() {
  tableLoading.value = true
  try {
    const params = {
      pageNum: queryParams.pageNum,
      pageSize: queryParams.pageSize
    }
    if (queryParams.dateRange && queryParams.dateRange.length === 2) {
      params.startDate = queryParams.dateRange[0]
      params.endDate = queryParams.dateRange[1]
    }
    if (queryParams.status) {
      params.status = queryParams.status
    }
    const res = await getSchedulingPage(params)
    const data = res.data
    if (Array.isArray(data)) {
      tableData.value = data
      total.value = data.length
    } else {
      tableData.value = data?.records || data?.list || []
      total.value = data?.total || 0
    }
  } finally {
    tableLoading.value = false
  }
}

// ==================== 搜索与重置 ====================
function handleSearch() {
  queryParams.pageNum = 1
  loadGanttData()
  loadTableData()
}

function handleReset() {
  queryParams.dateRange = null
  queryParams.status = ''
  queryParams.pageNum = 1
  loadGanttData()
  loadTableData()
}

// ==================== 工单搜索（用于新增对话框） ====================
const workOrderSearchLoading = ref(false)
const workOrderOptions = ref([])

async function searchWorkOrders(keyword) {
  if (!keyword) {
    workOrderOptions.value = []
    return
  }
  workOrderSearchLoading.value = true
  try {
    const res = await getWorkOrderPage({ keyword, pageNum: 1, pageSize: 20 })
    workOrderOptions.value = res.data?.records || res.data?.list || []
  } finally {
    workOrderSearchLoading.value = false
  }
}

function onWorkOrderChange(workOrderId) {
  if (!workOrderId) {
    formData.productName = ''
    formData.workOrderNo = ''
    return
  }
  const selected = workOrderOptions.value.find(w => w.id === workOrderId)
  if (selected) {
    formData.productName = selected.productName || ''
    formData.workOrderNo = selected.woNo || ''
  }
}

// ==================== 新增/编辑排产 ====================
const formVisible = ref(false)
const formMode = ref('add') // 'add' | 'edit'
const submitLoading = ref(false)
const formRef = ref(null)
const editingId = ref(null)

const formData = reactive({
  workOrderId: null,
  workOrderNo: '',
  productName: '',
  assigneeName: '',
  plannedStart: '',
  plannedEnd: '',
  remark: ''
})

const formRules = {
  workOrderId: [{ required: true, message: '请选择工单', trigger: 'change' }],
  plannedStart: [{ required: true, message: '请选择计划开始时间', trigger: 'change' }],
  plannedEnd: [{ required: true, message: '请选择计划结束时间', trigger: 'change' }]
}

function resetForm() {
  formData.workOrderId = null
  formData.workOrderNo = ''
  formData.productName = ''
  formData.assigneeName = ''
  formData.plannedStart = ''
  formData.plannedEnd = ''
  formData.remark = ''
  editingId.value = null
  workOrderOptions.value = []
}

function handleAdd() {
  resetForm()
  formMode.value = 'add'
  formVisible.value = true
}

function handleEdit(row) {
  resetForm()
  formMode.value = 'edit'
  editingId.value = row.id
  formData.workOrderId = row.workOrderId
  formData.workOrderNo = row.workOrderNo || ''
  formData.productName = row.productName || ''
  formData.assigneeName = row.assigneeName || ''
  formData.plannedStart = row.plannedStart || ''
  formData.plannedEnd = row.plannedEnd || ''
  formData.remark = row.remark || ''
  // 将当前工单加入下拉选项以便显示
  if (row.workOrderId) {
    workOrderOptions.value = [{
      id: row.workOrderId,
      woNo: row.workOrderNo || '',
      productName: row.productName || ''
    }]
  }
  formVisible.value = true
}

async function handleFormSubmit() {
  await formRef.value.validate()
  submitLoading.value = true
  try {
    const payload = {
      workOrderId: formData.workOrderId,
      workOrderNo: formData.workOrderNo || undefined,
      productName: formData.productName || undefined,
      assigneeName: formData.assigneeName || undefined,
      plannedStart: formData.plannedStart,
      plannedEnd: formData.plannedEnd,
      remark: formData.remark || undefined
    }
    if (formMode.value === 'add') {
      await createScheduling(payload)
      ElMessage.success('排产创建成功')
    } else {
      await updateScheduling(editingId.value, payload)
      ElMessage.success('排产更新成功')
    }
    formVisible.value = false
    loadGanttData()
    loadTableData()
  } finally {
    submitLoading.value = false
  }
}

// ==================== 删除排产 ====================
async function handleDelete(row) {
  await ElMessageBox.confirm('确定删除该排产记录吗？删除后不可恢复。', '提示', { type: 'warning' })
  try {
    await deleteScheduling(row.id)
    ElMessage.success('删除成功')
    loadGanttData()
    loadTableData()
  } catch {
    // error handled by interceptor
  }
}

// ==================== AI排产 ====================
const aiLoading = ref(false)
const aiResultVisible = ref(false)
const aiResult = ref('')

const aiResultHtml = computed(() => {
  if (!aiResult.value) return ''
  // 简单将换行转为<br>，保留基本格式
  return aiResult.value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\n/g, '<br/>')
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
})

async function handleAiScheduling() {
  // 构建工单信息
  let workOrderInfo = ''
  if (allSchedulingData.value.length > 0) {
    workOrderInfo = allSchedulingData.value.map(item => {
      return `工单号:${item.workOrderNo || '-'}, 产品:${item.productName || '-'}, 负责人:${item.assigneeName || '-'}, 计划开始:${item.plannedStart || '-'}, 计划结束:${item.plannedEnd || '-'}, 状态:${statusLabel(item.status)}`
    }).join('\n')
  } else {
    // 没有数据时也可以请求AI给出建议
    workOrderInfo = '当前暂无排产数据，请根据一般MES系统排产规则给出排产建议。'
  }

  aiLoading.value = true
  aiResultVisible.value = true
  aiResult.value = ''
  try {
    const res = await aiScheduling(workOrderInfo)
    aiResult.value = res.data || res.msg || '分析完成，但未返回具体内容。'
  } catch (err) {
    aiResult.value = ''
    ElMessage.error('AI排产分析失败: ' + (err.message || '未知错误'))
  } finally {
    aiLoading.value = false
  }
}

function applyAiResult() {
  ElMessage.info('已收到AI建议，请根据建议手动调整排产计划。')
  aiResultVisible.value = false
  // 重新加载数据以防后端已做了变更
  loadGanttData()
  loadTableData()
}

// ==================== 生命周期 ====================
onMounted(async () => {
  await nextTick()
  initGantt()
  loadGanttData()
  loadTableData()
  loadColumnConfig()
})

onUnmounted(() => {
  if (ganttInitialized) {
    gantt.clearAll()
    ganttInitialized = false
  }
})
</script>

<style scoped lang="scss">
.page-container {
  padding: 16px;
}

.search-card {
  margin-bottom: 16px;
}

.gantt-card {
  margin-bottom: 16px;

  .gantt-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .gantt-title {
    font-size: 16px;
    font-weight: 600;
  }

  .gantt-legend {
    display: flex;
    align-items: center;
    gap: 16px;
    font-size: 13px;
    color: #606266;

    .legend-item {
      display: flex;
      align-items: center;
      gap: 4px;
    }

    .legend-dot {
      display: inline-block;
      width: 12px;
      height: 12px;
      border-radius: 2px;
    }
  }

  .gantt-container {
    width: 100%;
    height: 480px;
  }
}

.table-card {
  .pagination-wrapper {
    display: flex;
    justify-content: flex-end;
    margin-top: 16px;
  }
}

.ai-result-content {
  padding: 16px;
  background: #f5f7fa;
  border-radius: 8px;
  line-height: 1.8;
  font-size: 14px;
  color: #303133;
  max-height: 400px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>

<style>
/* 甘特图全局样式覆盖（不可scoped，否则无法影响dhtmlx-gantt内部DOM） */
.gantt_task_line.custom-task-color {
  border-radius: 4px;
}

.gantt_grid_head_cell {
  font-weight: 600 !important;
  color: #303133 !important;
  font-size: 13px !important;
}

.gantt_cell {
  font-size: 13px !important;
  color: #606266 !important;
}

.gantt_task_content {
  font-size: 12px !important;
  color: #fff !important;
}

.completed-row {
  background-color: #f0f9eb !important;
}

.gantt_tooltip {
  padding: 10px 14px !important;
  font-size: 13px !important;
  line-height: 1.6 !important;
  border-radius: 6px !important;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15) !important;
}
</style>
