<template>
  <!-- 外层弹窗：显示该物料的工艺路线列表 -->
  <el-dialog
    v-model="visible"
    :title="`${productName} - 工艺路线管理`"
    width="900px"
    destroy-on-close
    :close-on-click-modal="false"
    @closed="$emit('update:modelValue', false)"
  >
    <div class="route-list-toolbar">
      <el-button type="primary" plain size="small" @click="openEditor(null)">+ 新增工艺路线</el-button>
    </div>
    <el-table :data="tableData" v-loading="loading" border stripe size="small">
      <el-table-column prop="routeName" label="路线名称" min-width="160" show-overflow-tooltip />
      <el-table-column prop="version" label="版本" width="90" align="center" />
      <el-table-column prop="status" label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="stepCount" label="工序数" width="75" align="center" />
      <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEditor(row)">编辑</el-button>
          <el-button link type="success" size="small" @click="openPreview(row)">预览</el-button>
        </template>
      </el-table-column>
    </el-table>
    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>

  <!-- 图形化编辑器弹窗 (全屏) -->
  <el-dialog v-model="editorVisible" :title="editId ? '编辑工艺路线' : '新增工艺路线'" fullscreen destroy-on-close
    :close-on-click-modal="false" class="process-editor-dialog">
    <div class="route-header">
      <el-form :model="form" :rules="rules" ref="formRef" inline>
        <el-form-item label="路线名称" prop="routeName">
          <el-input v-model="form.routeName" placeholder="请输入路线名称" style="width: 160px" />
        </el-form-item>
        <el-form-item label="产品">
          <el-input :value="productName" disabled style="width: 180px" />
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
    <div class="flow-toolbar">
      <el-button type="primary" plain size="small" @click="addNode(false)">+ 添加工序</el-button>
      <el-button type="warning" plain size="small" @click="addNode(true)">+ 添加外协</el-button>
      <el-button plain size="small" @click="autoLayout">自动布局</el-button>
      <el-button plain size="small" @click="fitView">适应画布</el-button>
    </div>
    <div class="flow-editor">
      <div class="flow-canvas">
        <VueFlow
          id="mat-editor-flow"
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
            <el-input-number v-model="selectedNode.data.standardTime" :min="0" :precision="1"
              controls-position="right" style="width: 100%" />
            <span style="font-size: 12px; color: #909399; margin-left: 4px">分</span>
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

  <!-- 预览弹窗 -->
  <el-dialog v-model="previewVisible" :title="`${previewRoute?.routeName} - 工序流程预览`" fullscreen destroy-on-close
    class="process-preview-dialog">
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
      <el-form-item label="标准工时(分)">
        <el-input-number v-model="quickProcessForm.standardTime" :min="0" :precision="1" controls-position="right" style="width: 100%" />
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
</template>

<script setup>
import { ref, reactive, markRaw, watch, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { VueFlow, useVueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'
import dagre from 'dagre'
import ProcessStepNode from '@/views/bom/process-route/ProcessStepNode.vue'
import { getProcessRoutePage, getProcessRouteDetail, createProcessRoute, updateProcessRoute } from '@/api/bom'
import { getProcessSelect, createProcess } from '@/api/master'
import { getQcTemplateSelect } from '@/api/quality'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  productId: { type: [Number, String], default: null },
  productName: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue'])

const visible = ref(false)
watch(() => props.modelValue, val => {
  visible.value = val
  if (val) loadData()
})
watch(visible, val => { if (!val) emit('update:modelValue', false) })

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

const { fitView: doFitView, getNodes: getFlowNodes } = useVueFlow({ id: 'mat-editor-flow' })

// ==================== 下拉选项 ====================
const processOptions = ref([])
const qcTemplateOptions = ref([])
async function loadProcessOptions() { try { processOptions.value = (await getProcessSelect()).data || [] } catch {} }
async function loadQcTemplateOptions() { try { qcTemplateOptions.value = (await getQcTemplateSelect()).data || [] } catch {} }

// ==================== 路线列表 ====================
const loading = ref(false)
const tableData = ref([])

async function loadData() {
  if (!props.productId) return
  loading.value = true
  try {
    const res = await getProcessRoutePage({ productId: props.productId, pageNum: 1, pageSize: 100 })
    tableData.value = res.data?.records || res.data?.list || []
  } finally { loading.value = false }
  loadProcessOptions()
  loadQcTemplateOptions()
}

// ==================== 图形编辑器 ====================
const editorVisible = ref(false)
const submitLoading = ref(false)
const editId = ref(null)
const formRef = ref(null)
let nodeIdCounter = 0

const form = reactive({ routeName: '', versionNo: '', status: 'draft', remark: '' })
const rules = {
  routeName: [{ required: true, message: '请输入路线名称', trigger: 'blur' }],
  versionNo: [{ required: true, message: '请输入版本号', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

const nodes = ref([])
const edges = ref([])
const selectedNode = ref(null)

function openEditor(row) {
  selectedNode.value = null
  if (row) {
    editId.value = row.id
    form.routeName = row.routeName
    form.versionNo = row.version || row.versionNo
    form.status = row.status
    form.remark = row.remark || ''
    loadRouteForEditor(row.id)
  } else {
    editId.value = null
    form.routeName = ''
    form.versionNo = ''
    form.status = 'draft'
    form.remark = ''
    nodes.value = []
    edges.value = []
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

function onNodeClick({ node }) { selectedNode.value = node }
function onPaneClick() { selectedNode.value = null }

function onConnect(params) {
  const exists = edges.value.some(e => e.source === params.source && e.target === params.target)
  if (exists) return
  edges.value = [...edges.value, {
    id: `e-${params.source}-${params.target}`,
    source: params.source,
    target: params.target,
    type: 'smoothstep',
    style: { stroke: '#b1b1b7', strokeWidth: 2 }
  }]
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

function fitView() { doFitView({ padding: 0.2, duration: 300 }) }

function onEdgeClick({ edge }) {
  ElMessageBox.confirm('确定删除此连线？', '提示', { type: 'warning' })
    .then(() => { edges.value = edges.value.filter(e => e.id !== edge.id) })
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

// ==================== 数据转换 ====================
function stepsToGraph(steps) {
  if (!steps.length) return { newNodes: [], newEdges: [] }
  const groupMap = new Map()
  steps.forEach(s => {
    const g = s.stepGroup ?? 1
    if (!groupMap.has(g)) groupMap.set(g, [])
    groupMap.get(g).push(s)
  })
  const sortedGroups = [...groupMap.entries()].sort(([a], [b]) => a - b)
  const newNodes = steps.map(s => ({
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
  }))
  const groupNodeMap = new Map()
  newNodes.forEach(n => {
    const g = n.data._stepGroup
    if (!groupNodeMap.has(g)) groupNodeMap.set(g, [])
    groupNodeMap.get(g).push(n.id)
  })
  const newEdges = []
  for (let i = 0; i < sortedGroups.length - 1; i++) {
    const currentIds = groupNodeMap.get(sortedGroups[i][0]) || []
    const nextIds = groupNodeMap.get(sortedGroups[i + 1][0]) || []
    for (const src of currentIds) {
      for (const tgt of nextIds) {
        newEdges.push({ id: `e-${src}-${tgt}`, source: src, target: tgt, type: 'smoothstep', style: { stroke: '#b1b1b7', strokeWidth: 2 } })
      }
    }
  }
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

function graphToSteps(nodeList, edgeList) {
  if (nodeList.length === 0) return []
  const outEdges = new Map()
  const inEdges = new Map()
  nodeList.forEach(n => { outEdges.set(n.id, []); inEdges.set(n.id, []) })
  edgeList.forEach(e => {
    outEdges.get(e.source)?.push(e.target)
    inEdges.get(e.target)?.push(e.source)
  })
  const nodeLevel = new Map()
  const queue = []
  nodeList.forEach(n => {
    if ((inEdges.get(n.id) || []).length === 0) { nodeLevel.set(n.id, 1); queue.push(n.id) }
  })
  if (queue.length === 0) { nodeList.forEach(n => { nodeLevel.set(n.id, 1); queue.push(n.id) }) }
  while (queue.length > 0) {
    const current = queue.shift()
    const currentLvl = nodeLevel.get(current)
    for (const next of (outEdges.get(current) || [])) {
      const newLvl = currentLvl + 1
      if (newLvl > (nodeLevel.get(next) || 0)) { nodeLevel.set(next, newLvl); queue.push(next) }
    }
  }
  nodeList.forEach(n => {
    if (!nodeLevel.has(n.id)) nodeLevel.set(n.id, Math.max(1, Math.round(n.position.x / 200) + 1))
  })
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
  steps.sort((a, b) => a.stepGroup - b.stepGroup)
  return steps
}

// ==================== 保存 ====================
async function handleSubmit() {
  await formRef.value.validate()
  if (nodes.value.length === 0) { ElMessage.warning('请至少添加一道工序'); return }
  const unnamed = nodes.value.find(n => !n.data.stepName)
  if (unnamed) { selectedNode.value = unnamed; ElMessage.warning('存在未命名的工序，请填写名称'); return }
  submitLoading.value = true
  try {
    const flowNodes = getFlowNodes.value || nodes.value
    const steps = graphToSteps(flowNodes, edges.value)
    const payload = { routeName: form.routeName, productId: props.productId, versionNo: form.versionNo, status: form.status, remark: form.remark, steps }
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
</script>

<style scoped lang="scss">
.route-list-toolbar {
  margin-bottom: 12px;
}

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
  h4 { margin: 0 0 12px 0; color: #303133; font-size: 15px; }
}
.flow-panel-empty {
  display: flex;
  align-items: center;
  justify-content: center;
}
.preview-canvas {
  height: calc(100vh - 80px);
}
</style>

<style>
.process-editor-dialog .el-dialog__body {
  padding: 0 !important;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  flex: 1;
}
.process-editor-dialog .el-dialog__header {
  padding: 12px 20px;
  margin: 0;
}
.process-editor-dialog.el-dialog {
  display: flex;
  flex-direction: column;
}
.process-preview-dialog .el-dialog__body {
  padding: 0 !important;
  flex: 1;
}
.process-preview-dialog.el-dialog {
  display: flex;
  flex-direction: column;
}
.process-preview-dialog .el-dialog__header {
  padding: 12px 20px;
  margin: 0;
}
</style>
