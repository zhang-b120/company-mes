<template>
  <div class="page-container">
    <!-- 搜索区域 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="路线名称">
          <el-input v-model="queryParams.routeName" placeholder="路线名称" clearable style="width: 150px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="物料编码">
          <el-input v-model="queryParams.productNo" placeholder="物料编码" clearable style="width: 150px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="产品名称">
          <el-input v-model="queryParams.productName" placeholder="产品名称" clearable style="width: 150px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable>
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="primary" plain @click="openEditor(null)">新增工艺路线</el-button>
          <el-button type="success" plain @click="handleExportActive">导出激活版本</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格区域 -->
    <el-card shadow="never" class="table-card">
      <div style="display: flex; justify-content: flex-end; margin-bottom: 10px">
        <el-button :icon="Setting" @click="colConfigDialogRef.open()">列配置</el-button>
      </div>
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column v-for="col in visibleColumns" :key="col.key"
          :label="col.label" :min-width="col.minWidth || 100" :align="col.align || 'left'" show-overflow-tooltip>
          <template #default="{ row }">
            <template v-if="col.key === 'status'">
              <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
            </template>
            <template v-else-if="col.key === 'createTime'">{{ $fmtDate(row.createTime) }}</template>
            <template v-else>{{ row[col.key] || '-' }}</template>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEditor(row)">编辑</el-button>
            <el-button link type="success" @click="openPreview(row)">预览</el-button>
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
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- 图形化编辑器弹窗 (全屏) -->
    <el-dialog v-model="editorVisible" :title="editId ? '编辑工艺路线' : '新增工艺路线'" fullscreen destroy-on-close
      :close-on-click-modal="false" class="editor-dialog">
      <!-- 路线头部表单 -->
      <div class="route-header">
        <el-form :model="form" :rules="rules" ref="formRef" inline>
          <el-form-item label="路线名称" prop="routeName">
            <el-input v-model="form.routeName" placeholder="请输入路线名称" style="width: 160px" />
          </el-form-item>
          <el-form-item label="产品编号">
            <el-input :value="selectedProductNo" readonly placeholder="-" style="width: 150px" />
          </el-form-item>
          <el-form-item label="产品名称" prop="productId">
            <div style="display: flex; align-items: center; gap: 4px">
              <el-input :value="selectedProductName" readonly :placeholder="reportStatisticsEnabled ? '请选择产品（必选）' : '请选择产品（可选）'" style="width: 180px" />
              <el-button type="primary" link @click="productPickerVisible = true">选择</el-button>
              <el-button v-if="form.productId" type="info" link @click="clearProduct">清除</el-button>
            </div>
          </el-form-item>
          <el-form-item label="版本" prop="versionNo">
            <el-input v-model="form.versionNo" placeholder="如 V1.0" style="width: 100px" />
          </el-form-item>
          <el-form-item label="状态" prop="status">
            <el-select v-model="form.status" style="width: 100px">
              <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="form.remark" placeholder="备注" style="width: 140px" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="submitLoading" @click="handleSubmit">保存工艺路线</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 工具栏 -->
      <div class="flow-toolbar">
        <el-button type="primary" plain size="small" @click="addNode(false)">+ 添加工序</el-button>
        <el-button type="warning" plain size="small" @click="addNode(true)">+ 添加外协</el-button>
        <el-button plain size="small" @click="autoLayout">自动布局</el-button>
        <el-button plain size="small" @click="fitView">适应画布</el-button>
      </div>

      <!-- 画布 + 属性面板 -->
      <div class="flow-editor">
        <div class="flow-canvas">
          <VueFlow
            id="editor-flow"
            v-model:nodes="nodes"
            v-model:edges="edges"
            :node-types="nodeTypes"
            :default-edge-options="defaultEdgeOptions"
            :connection-line-style="{ stroke: '#b1b1b7' }"
            :snap-to-grid="true"
            :snap-grid="[15, 15]"
            fit-view-on-init
            @node-click="onNodeClick"
            @pane-click="onPaneClick"
            @connect="onConnect"
            @edge-click="onEdgeClick"
          >
            <Background :gap="15" />
            <Controls :show-fit-view="false" :show-interactive="false" />
          </VueFlow>
        </div>

        <!-- 右侧属性面板 -->
        <div class="flow-panel" v-if="selectedNode">
          <h4>工序属性</h4>
          <el-form label-width="80px" size="small">
            <el-form-item label="工序名称">
              <el-select v-if="!selectedNode.data.isOutsource" v-model="selectedNode.data.stepName"
                placeholder="搜索选择工序" filterable allow-create style="width: 100%"
                @change="(val) => onProcessSelect(selectedNode, val)">
                <el-option v-for="item in processOptions" :key="item.id" :label="item.name" :value="item.name" />
                <template #empty>
                  <div style="padding: 8px; text-align: center">
                    <div style="color: #909399; margin-bottom: 8px">未找到匹配工序</div>
                    <el-button type="primary" link size="small" @click="handleQuickAddProcess">快速新增工序</el-button>
                  </div>
                </template>
              </el-select>
              <el-input v-else v-model="selectedNode.data.stepName" placeholder="外协工序名称" />
            </el-form-item>
            <el-form-item label="标准工时">
              <el-input-number v-model="selectedNode.data.standardTime" :min="0" :precision="0" :step="1"
                controls-position="right" style="width: 100%" />
              <span style="font-size: 12px; color: #909399; margin-left: 4px">天</span>
            </el-form-item>
            <el-form-item label="计件单价">
              <el-input-number v-model="selectedNode.data.piecePrice" :min="0" :precision="2"
                controls-position="right" style="width: 100%" />
            </el-form-item>
            <el-form-item label="SOP说明">
              <el-input v-model="selectedNode.data.sopDescription" type="textarea" :rows="2" placeholder="SOP说明" />
            </el-form-item>
            <el-form-item label="质检模板">
              <el-select v-model="selectedNode.data.qcTemplateId" placeholder="选择质检模板" filterable clearable
                style="width: 100%" @change="onQcTemplateChange">
                <el-option v-for="t in qcTemplateOptions" :key="t.id" :label="t.templateName" :value="t.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="类型">
              <el-switch v-model="selectedNode.data.isOutsource" active-text="外协" inactive-text="自制" />
            </el-form-item>
            <el-divider />
            <el-button type="danger" plain size="small" style="width: 100%" @click="deleteSelectedNode">删除此工序</el-button>
          </el-form>
        </div>
        <div class="flow-panel flow-panel-empty" v-else>
          <el-empty description="点击工序节点编辑属性" :image-size="80" />
        </div>
      </div>
    </el-dialog>

    <!-- 预览弹窗 (只读流程图) -->
    <el-dialog v-model="previewVisible" :title="previewTitle" fullscreen destroy-on-close
      class="preview-dialog">
      <div class="preview-canvas">
        <VueFlow
          v-model:nodes="previewNodes"
          v-model:edges="previewEdges"
          :node-types="nodeTypes"
          :nodes-draggable="false"
          :nodes-connectable="false"
          :edges-updatable="false"
          :elements-selectable="false"
          fit-view-on-init
        >
          <Background :gap="15" />
          <Controls :show-interactive="false" />
        </VueFlow>
      </div>
    </el-dialog>

    <!-- 快速新增工序弹窗 -->
    <el-dialog v-model="quickProcessVisible" title="快速新增工序" width="500px" destroy-on-close>
      <el-form :model="quickProcessForm" ref="quickProcessFormRef" :rules="quickProcessRules" label-width="100px">
        <el-form-item label="工序名称" prop="processName">
          <el-input v-model="quickProcessForm.processName" placeholder="请输入工序名称" />
        </el-form-item>
        <el-form-item label="标准工时(天)">
          <el-input-number v-model="quickProcessForm.standardTime" :min="0" :precision="0" :step="1" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="计件单价">
          <el-input-number v-model="quickProcessForm.piecePrice" :min="0" :precision="2" controls-position="right" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="quickProcessVisible = false">取消</el-button>
        <el-button type="primary" :loading="quickProcessLoading" @click="handleQuickProcessSubmit">保存并选择</el-button>
      </template>
    </el-dialog>

    <ColumnConfigDialog ref="colConfigDialogRef" :all-columns="allColumns" :visible-keys="visibleColumns.map(c=>c.key)" @save="saveConfig" />
    <MaterialPickerDialog v-model="productPickerVisible" title="选择产品" materialAttribute="production" :multiple="false" @select="onProductSelected" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, markRaw, onMounted, nextTick } from 'vue'
import { Setting } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { VueFlow, useVueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'
import dagre from 'dagre'
import ProcessStepNode from './ProcessStepNode.vue'
import { getProcessRoutePage, getProcessRouteDetail, createProcessRoute, updateProcessRoute, exportActiveProcessRoutes } from '@/api/bom'
import { getProcessSelect, createProcess } from '@/api/master'
import { getBizConfigList } from '@/api/system'
import MaterialPickerDialog from '@/components/MaterialPickerDialog.vue'
import { getQcTemplateSelect } from '@/api/quality'
import { useColumnConfig } from '@/composables/useColumnConfig'
import ColumnConfigDialog from '@/components/ColumnConfigDialog.vue'

const BOM_PROCESS_ROUTE_DEFAULT_COLUMNS = [
  { key: 'routeName', label: '路线名称', minWidth: 160 },
  { key: 'productNo', label: '物料编号', minWidth: 140 },
  { key: 'productName', label: '物料名称', minWidth: 140 },
  { key: 'version', label: '版本', minWidth: 100, align: 'center' },
  { key: 'status', label: '状态', minWidth: 100, align: 'center' },
  { key: 'stepCount', label: '工序数', minWidth: 80, align: 'center' },
  { key: 'remark', label: '备注', minWidth: 160 },
  { key: 'createTime', label: '创建时间', minWidth: 170 },
]
const colConfigDialogRef = ref(null)
const { visibleColumns, allColumns, loadColumnConfig, saveConfig } = useColumnConfig('bom_process_route', BOM_PROCESS_ROUTE_DEFAULT_COLUMNS)

// ==================== 常量 ====================
const statusOptions = [
  { label: '草稿', value: 'draft' },
  { label: '激活', value: 'active' },
  { label: '废弃', value: 'deprecated' }
]
const statusMap = Object.fromEntries(statusOptions.map(s => [s.value, s.label]))
function statusLabel(status) { return statusMap[status] || status || '-' }
function statusTagType(status) {
  return { draft: 'info', active: 'success', deprecated: 'danger' }[status] || 'info'
}

const nodeTypes = { processStep: markRaw(ProcessStepNode) }
const defaultEdgeOptions = { type: 'smoothstep', animated: false, style: { stroke: '#b1b1b7', strokeWidth: 2 } }

// Vue Flow composable (for fitView, getNodes etc.)
const { fitView: doFitView, getNodes: getFlowNodes } = useVueFlow({ id: 'editor-flow' })

// ==================== 下拉选项 ====================
const processOptions = ref([])
const qcTemplateOptions = ref([])

async function loadQcTemplateOptions() { try { qcTemplateOptions.value = (await getQcTemplateSelect()).data || [] } catch {} }
async function loadProcessOptions() { try { processOptions.value = (await getProcessSelect()).data || [] } catch {} }

// ==================== 产品选择（MaterialPickerDialog） ====================
const productPickerVisible = ref(false)
const selectedProductNo = ref('')
const selectedProductName = ref('')
const reportStatisticsEnabled = ref(false)

function onProductSelected(items) {
  if (items && items.length > 0) {
    const m = items[0]
    form.productId = m.id
    selectedProductNo.value = m.materialNo || ''
    selectedProductName.value = m.materialName + (m.spec ? ' (' + m.spec + ')' : '')
  }
  productPickerVisible.value = false
}
function clearProduct() {
  form.productId = null
  selectedProductNo.value = ''
  selectedProductName.value = ''
}

// ==================== 查询与表格 ====================
const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const queryParams = reactive({ routeName: '', productName: '', productNo: '', status: '', pageNum: 1, pageSize: 20 })

async function loadData() {
  loading.value = true
  try {
    const params = {
      routeName: queryParams.routeName || undefined,
      productName: queryParams.productName || undefined,
      productNo: queryParams.productNo || undefined,
      status: queryParams.status || undefined,
      pageNum: queryParams.pageNum,
      pageSize: queryParams.pageSize
    }
    const res = await getProcessRoutePage(params)
    tableData.value = res.data?.records || res.data?.list || []
    total.value = res.data?.total || 0
  } finally { loading.value = false }
}

function handleSearch() { queryParams.pageNum = 1; loadData() }
function handleReset() { queryParams.routeName = ''; queryParams.productName = ''; queryParams.productNo = ''; queryParams.status = ''; queryParams.pageNum = 1; loadData() }

async function handleExportActive() {
  try {
    const res = await exportActiveProcessRoutes({
      routeName: queryParams.routeName || undefined,
      productName: queryParams.productName || undefined
    })
    const url = window.URL.createObjectURL(new Blob([res.data || res]))
    const a = document.createElement('a'); a.href = url; a.download = `工艺路线激活版_${new Date().toISOString().slice(0,10)}.xlsx`; a.click()
    window.URL.revokeObjectURL(url)
  } catch { ElMessage.error('导出失败') }
}

// ==================== 图形编辑器 ====================
const editorVisible = ref(false)
const submitLoading = ref(false)
const editId = ref(null)
const formRef = ref(null)
let nodeIdCounter = 0

const form = reactive({ routeName: '', productId: null, versionNo: '', status: 'draft', remark: '' })
const rules = computed(() => ({
  routeName: [{ required: true, message: '请输入路线名称', trigger: 'blur' }],
  productId: reportStatisticsEnabled.value ? [{ required: true, message: '请选择产品', trigger: 'change' }] : [],
  versionNo: [{ required: true, message: '请输入版本号', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}))

const nodes = ref([])
const edges = ref([])
const selectedNode = ref(null)

function openEditor(row) {
  selectedNode.value = null
  if (row) {
    editId.value = row.id
    form.routeName = row.routeName; form.productId = row.productId
    form.versionNo = row.version || row.versionNo; form.status = row.status; form.remark = row.remark || ''
    selectedProductNo.value = row.productNo || ''
    selectedProductName.value = row.productName || ''
    loadRouteForEditor(row.id)
  } else {
    editId.value = null
    form.routeName = ''; form.productId = null; form.versionNo = ''
    form.status = 'draft'; form.remark = ''
    selectedProductNo.value = ''
    selectedProductName.value = ''
    nodes.value = []; edges.value = []
  }
  editorVisible.value = true
}

async function loadRouteForEditor(id) {
  try {
    const res = await getProcessRouteDetail(id)
    const steps = res.data?.steps || []
    const { newNodes, newEdges } = stepsToGraph(steps)
    nodes.value = newNodes
    edges.value = newEdges
  } catch { nodes.value = []; edges.value = [] }
}

// ==================== 节点操作 ====================
function addNode(isOutsource) {
  const id = `step-${++nodeIdCounter}`
  // 计算新节点位置：放在画布最右侧
  const maxX = nodes.value.length > 0 ? Math.max(...nodes.value.map(n => n.position.x)) : 0
  const maxY = nodes.value.length > 0 ? Math.max(...nodes.value.map(n => n.position.y)) : 0
  const newNode = {
    id,
    type: 'processStep',
    position: { x: maxX + 200, y: maxY > 200 ? 100 : maxY },
    data: {
      processId: null, stepName: '', standardTime: 0, piecePrice: 0, sopDescription: '',
      isOutsource: !!isOutsource, qcTemplateId: null, qcTemplateName: ''
    }
  }
  nodes.value = [...nodes.value, newNode]
  selectedNode.value = newNode
}

function deleteSelectedNode() {
  if (!selectedNode.value) return
  const nodeId = selectedNode.value.id
  nodes.value = nodes.value.filter(n => n.id !== nodeId)
  edges.value = edges.value.filter(e => e.source !== nodeId && e.target !== nodeId)
  selectedNode.value = null
}

function onNodeClick({ node }) {
  selectedNode.value = node
}

function onPaneClick() {
  selectedNode.value = null
}

function onConnect(params) {
  // 防止重复连线
  const exists = edges.value.some(e => e.source === params.source && e.target === params.target)
  if (exists) return
  const newEdge = {
    id: `e-${params.source}-${params.target}`,
    source: params.source,
    target: params.target,
    type: 'smoothstep',
    style: { stroke: '#b1b1b7', strokeWidth: 2 }
  }
  edges.value = [...edges.value, newEdge]
}

function onProcessSelect(node, selectedName) {
  const proc = processOptions.value.find(p => p.name === selectedName)
  if (proc) {
    node.data.processId = proc.id
    node.data.stepName = proc.name
    node.data.standardTime = proc.standardTime ?? node.data.standardTime
    node.data.piecePrice = proc.piecePrice ?? node.data.piecePrice
  } else {
    node.data.processId = null
  }
}

function onQcTemplateChange(templateId) {
  if (!selectedNode.value) return
  const tpl = qcTemplateOptions.value.find(t => t.id === templateId)
  selectedNode.value.data.qcTemplateName = tpl ? tpl.templateName : ''
}

function fitView() {
  doFitView({ padding: 0.2, duration: 300 })
}

function onEdgeClick({ edge }) {
  ElMessageBox.confirm('确定删除此连线？', '提示', { type: 'warning' })
    .then(() => {
      edges.value = edges.value.filter(e => e.id !== edge.id)
    })
    .catch(() => {})
}

// ==================== dagre 自动布局 ====================
const NODE_WIDTH = 160
const NODE_HEIGHT = 80

function autoLayout() {
  if (nodes.value.length === 0) return
  const g = new dagre.graphlib.Graph()
  g.setGraph({ rankdir: 'LR', nodesep: 60, ranksep: 140, marginx: 40, marginy: 40 })
  g.setDefaultEdgeLabel(() => ({}))

  nodes.value.forEach(n => g.setNode(n.id, { width: NODE_WIDTH, height: NODE_HEIGHT }))
  edges.value.forEach(e => g.setEdge(e.source, e.target))

  dagre.layout(g)

  nodes.value = nodes.value.map(n => {
    const pos = g.node(n.id)
    return { ...n, position: { x: pos.x - NODE_WIDTH / 2, y: pos.y - NODE_HEIGHT / 2 } }
  })

  nextTick(() => doFitView({ padding: 0.2, duration: 300 }))
}

// ==================== 数据转换: steps → graph ====================
function stepsToGraph(steps) {
  if (!steps.length) return { newNodes: [], newEdges: [] }

  // 按 stepGroup 分组
  const groupMap = new Map()
  steps.forEach(s => {
    const g = s.stepGroup ?? 1
    if (!groupMap.has(g)) groupMap.set(g, [])
    groupMap.get(g).push(s)
  })
  const sortedGroups = [...groupMap.entries()].sort(([a], [b]) => a - b)

  // 为每个 step 创建 node
  const newNodes = []
  steps.forEach(s => {
    newNodes.push({
      id: `step-${++nodeIdCounter}`,
      type: 'processStep',
      position: { x: 0, y: 0 },
      data: {
        processId: s.processId || null,
        stepName: s.processName || s.stepName || '',
        standardTime: s.standardTime ?? 0,
        piecePrice: s.piecePrice ?? 0,
        sopDescription: s.sopDescription || '',
        isOutsource: !!(s.isOutsourceable),
        qcTemplateId: s.qcTemplateId || null,
        qcTemplateName: s.qcTemplateName || '',
        _stepGroup: s.stepGroup ?? 1,
        _backendId: s.id
      }
    })
  })

  // 建立 stepGroup → node ids 映射
  const groupNodeMap = new Map()
  newNodes.forEach(n => {
    const g = n.data._stepGroup
    if (!groupNodeMap.has(g)) groupNodeMap.set(g, [])
    groupNodeMap.get(g).push(n.id)
  })

  // 相邻组之间创建边
  const newEdges = []
  for (let i = 0; i < sortedGroups.length - 1; i++) {
    const currentGroupNo = sortedGroups[i][0]
    const nextGroupNo = sortedGroups[i + 1][0]
    const currentIds = groupNodeMap.get(currentGroupNo) || []
    const nextIds = groupNodeMap.get(nextGroupNo) || []
    for (const src of currentIds) {
      for (const tgt of nextIds) {
        newEdges.push({
          id: `e-${src}-${tgt}`,
          source: src,
          target: tgt,
          type: 'smoothstep',
          style: { stroke: '#b1b1b7', strokeWidth: 2 }
        })
      }
    }
  }

  // dagre 自动布局
  const g = new dagre.graphlib.Graph()
  g.setGraph({ rankdir: 'LR', nodesep: 60, ranksep: 140, marginx: 40, marginy: 40 })
  g.setDefaultEdgeLabel(() => ({}))
  newNodes.forEach(n => g.setNode(n.id, { width: NODE_WIDTH, height: NODE_HEIGHT }))
  newEdges.forEach(e => g.setEdge(e.source, e.target))
  dagre.layout(g)

  newNodes.forEach(n => {
    const pos = g.node(n.id)
    n.position = { x: pos.x - NODE_WIDTH / 2, y: pos.y - NODE_HEIGHT / 2 }
  })

  return { newNodes, newEdges }
}

// ==================== 数据转换: graph → steps ====================
function graphToSteps(nodeList, edgeList) {
  if (nodeList.length === 0) return []

  // 构建邻接表
  const outEdges = new Map()
  const inEdges = new Map()
  nodeList.forEach(n => { outEdges.set(n.id, []); inEdges.set(n.id, []) })
  edgeList.forEach(e => {
    outEdges.get(e.source)?.push(e.target)
    inEdges.get(e.target)?.push(e.source)
  })

  // 拓扑排序确定层级 (BFS)
  const nodeLevel = new Map()
  const queue = []

  // 没有入边的节点 = 第一层
  nodeList.forEach(n => {
    if ((inEdges.get(n.id) || []).length === 0) {
      nodeLevel.set(n.id, 1)
      queue.push(n.id)
    }
  })

  // 如果所有节点都有入边（孤立节点除外），为没有入边的设为层级1
  if (queue.length === 0) {
    nodeList.forEach(n => {
      nodeLevel.set(n.id, 1)
      queue.push(n.id)
    })
  }

  while (queue.length > 0) {
    const current = queue.shift()
    const currentLvl = nodeLevel.get(current)
    for (const next of (outEdges.get(current) || [])) {
      const existingLvl = nodeLevel.get(next) || 0
      const newLvl = currentLvl + 1
      if (newLvl > existingLvl) {
        nodeLevel.set(next, newLvl)
        queue.push(next)
      }
    }
  }

  // 没有被分配层级的孤立节点，按 x 位置分配
  nodeList.forEach(n => {
    if (!nodeLevel.has(n.id)) {
      const xLevel = Math.round(n.position.x / 200) + 1
      nodeLevel.set(n.id, Math.max(1, xLevel))
    }
  })

  // 按层级分配 stepGroup
  const steps = nodeList.map(n => ({
    processId: n.data.processId || null,
    processName: n.data.stepName,
    standardTime: n.data.standardTime,
    piecePrice: n.data.piecePrice,
    sopDescription: n.data.sopDescription,
    isOutsourceable: n.data.isOutsource ? 1 : 0,
    qcTemplateId: n.data.qcTemplateId || null,
    stepGroup: nodeLevel.get(n.id) || 1
  }))

  // 按 stepGroup 排序
  steps.sort((a, b) => a.stepGroup - b.stepGroup)
  return steps
}

// ==================== 保存 ====================
async function handleSubmit() {
  await formRef.value.validate()
  if (nodes.value.length === 0) {
    ElMessage.warning('请至少添加一道工序')
    return
  }
  // 检查是否有未命名工序
  const unnamed = nodes.value.find(n => !n.data.stepName)
  if (unnamed) {
    selectedNode.value = unnamed
    ElMessage.warning('存在未命名的工序，请填写名称')
    return
  }
  // 业务配置校验：报工统计启用时，计件单价必填
  if (reportStatisticsEnabled.value) {
    const nodesWithoutPrice = nodes.value.filter(n => n.type === 'processStep' && (!n.data.piecePrice || Number(n.data.piecePrice) <= 0))
    if (nodesWithoutPrice.length > 0) {
      ElMessage.warning('员工报工统计已启用，所有工序的计件单价为必填项')
      return
    }
  }

  submitLoading.value = true
  try {
    // 使用 Vue Flow 内部节点（确保拿到最新数据）
    const flowNodes = getFlowNodes.value || nodes.value
    const steps = graphToSteps(flowNodes, edges.value)
    const payload = {
      routeName: form.routeName,
      productId: form.productId,
      versionNo: form.versionNo,
      status: form.status,
      remark: form.remark,
      steps
    }
    if (editId.value) {
      await updateProcessRoute(editId.value, payload)
      ElMessage.success('修改成功')
    } else {
      await createProcessRoute(payload)
      ElMessage.success('新增成功')
    }
    editorVisible.value = false
    loadData()
  } finally { submitLoading.value = false }
}

// ==================== 预览 ====================
const previewVisible = ref(false)
const previewRoute = ref(null)
const previewNodes = ref([])
const previewEdges = ref([])
// 预览标题: 路线名 - 产品编号 产品名 - 工序流程预览
const previewTitle = computed(() => {
  const r = previewRoute.value
  if (!r) return '工序流程预览'
  const product = [r.productNo, r.productName].filter(Boolean).join(' ')
  return [r.routeName, product, '工序流程预览'].filter(Boolean).join(' - ')
})

async function openPreview(row) {
  previewRoute.value = row
  try {
    const res = await getProcessRouteDetail(row.id)
    const steps = res.data?.steps || []
    const { newNodes, newEdges } = stepsToGraph(steps)
    previewNodes.value = newNodes
    previewEdges.value = newEdges
  } catch { previewNodes.value = []; previewEdges.value = [] }
  previewVisible.value = true
}

// ==================== 快速新增工序 ====================
const quickProcessVisible = ref(false)
const quickProcessLoading = ref(false)
const quickProcessFormRef = ref(null)
const quickProcessForm = reactive({ processName: '', standardTime: undefined, piecePrice: undefined })
const quickProcessRules = { processName: [{ required: true, message: '请输入工序名称', trigger: 'blur' }] }

function handleQuickAddProcess() {
  quickProcessForm.processName = ''; quickProcessForm.standardTime = undefined; quickProcessForm.piecePrice = undefined
  quickProcessVisible.value = true
}

async function handleQuickProcessSubmit() {
  await quickProcessFormRef.value.validate()
  quickProcessLoading.value = true
  try {
    await createProcess(quickProcessForm)
    ElMessage.success('工序创建成功')
    quickProcessVisible.value = false
    await loadProcessOptions()
  } finally { quickProcessLoading.value = false }
}

// ==================== 初始化 ====================
onMounted(() => {
  loadData()
  loadProcessOptions()
  loadQcTemplateOptions()
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
.page-container { padding: 16px; }
.search-card { margin-bottom: 16px; }
.table-card {
  .pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; }
}

/* 编辑器弹窗 */
.route-header {
  padding: 0 8px;
  border-bottom: 1px solid #ebeef5;
  background: #fafafa;
}
.flow-toolbar {
  padding: 8px 12px;
  display: flex;
  gap: 8px;
  border-bottom: 1px solid #ebeef5;
}
.flow-editor {
  display: flex;
  flex: 1;
  height: calc(100vh - 200px);
  overflow: hidden;
}
.flow-canvas {
  flex: 1;
  position: relative;
}
.flow-panel {
  width: 280px;
  padding: 16px;
  border-left: 1px solid #ebeef5;
  overflow-y: auto;
  background: #fff;

  h4 {
    margin: 0 0 12px 0;
    color: #303133;
    font-size: 15px;
  }
}
.flow-panel-empty {
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 预览弹窗 */
.preview-canvas {
  height: calc(100vh - 80px);
}


</style>

<!-- 全局样式（el-dialog teleported to body，scoped 无法覆盖） -->
<style>
.editor-dialog .el-dialog__body {
  padding: 0 !important;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  flex: 1;
}
.editor-dialog .el-dialog__header {
  padding: 12px 20px;
  margin: 0;
}
.editor-dialog.el-dialog {
  display: flex;
  flex-direction: column;
}
.preview-dialog .el-dialog__body {
  padding: 0 !important;
  flex: 1;
}
.preview-dialog.el-dialog {
  display: flex;
  flex-direction: column;
}
.preview-dialog .el-dialog__header {
  padding: 12px 20px;
  margin: 0;
}
</style>
