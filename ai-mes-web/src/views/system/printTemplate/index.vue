<template>
  <div class="pt-page">
    <!-- Hero header -->
    <div class="pt-hero">
      <div class="pt-hero-left">
        <div class="pt-hero-title">
          <el-icon class="pt-hero-icon"><Document /></el-icon>
          打印模板管理
        </div>
        <div class="pt-hero-sub">为每种业务单据维护可视化打印模板, 设计、复用、一键启用</div>
      </div>
      <div class="pt-hero-stats">
        <div class="pt-stat">
          <div class="pt-stat-num">{{ tableData.length }}</div>
          <div class="pt-stat-lbl">模板总数</div>
        </div>
        <div class="pt-stat">
          <div class="pt-stat-num pt-stat-num-active">{{ tableData.filter(r => r.isActive).length }}</div>
          <div class="pt-stat-lbl">已启用</div>
        </div>
        <div class="pt-stat">
          <div class="pt-stat-num">{{ uniqueBizCount }}</div>
          <div class="pt-stat-lbl">业务类型</div>
        </div>
      </div>
    </div>

    <!-- 工具栏 + 筛选 -->
    <div class="pt-toolbar">
      <div class="pt-toolbar-left">
        <el-radio-group v-model="queryParams.bizType" size="default" @change="loadData">
          <el-radio-button label="">全部</el-radio-button>
          <el-radio-button v-for="b in bizTypeOptions" :key="b.value" :label="b.value">{{ b.label }}</el-radio-button>
        </el-radio-group>
      </div>
      <div class="pt-toolbar-right">
        <el-button type="primary" :icon="Plus" @click="handleAdd">新建模板</el-button>
        <el-button :icon="Upload" @click="handleImportOpen">导入 JSON</el-button>
        <el-dropdown @command="handleApplyPreset">
          <el-button :icon="MagicStick">内置模板<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="purchase_receipt_reconciliation_a4">采购收料对账单 (A4 横向)</el-dropdown-item>
              <el-dropdown-item command="purchase_receipt_reconciliation_portrait">采购收料对账单 (A4 竖向)</el-dropdown-item>
              <el-dropdown-item command="purchase_receipt_reconciliation_slip">采购收料对账单 (80mm 小票)</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <!-- 模板卡片网格 -->
    <div v-loading="loading" class="pt-grid">
      <div v-for="row in tableData" :key="row.id" class="pt-card" :class="{ 'pt-card-active': row.isActive }">
        <div class="pt-card-header">
          <div class="pt-card-title-wrap">
            <div class="pt-card-title">{{ row.templateName }}</div>
            <div class="pt-card-code">{{ row.templateCode }}</div>
          </div>
          <el-tag v-if="row.isActive" type="success" effect="dark" size="small">已启用</el-tag>
          <el-tag v-else type="info" effect="plain" size="small">未启用</el-tag>
        </div>
        <div class="pt-card-body">
          <div class="pt-card-meta">
            <el-tag :type="bizTypeTag(row.bizType)" size="small" effect="light">{{ bizTypeLabel(row.bizType) }}</el-tag>
            <span class="pt-card-time">{{ $fmtDate(row.updateTime) }}</span>
          </div>
          <div v-if="row.remark" class="pt-card-remark" :title="row.remark">{{ row.remark }}</div>
          <div v-else class="pt-card-remark pt-card-remark-empty">未填写备注</div>
        </div>
        <div class="pt-card-actions">
          <el-button type="primary" link @click="handleDesign(row)">
            <el-icon><EditPen /></el-icon> 设计
          </el-button>
          <el-button type="primary" link @click="handlePreview(row)">
            <el-icon><View /></el-icon> 预览
          </el-button>
          <el-button type="primary" link @click="handleCopy(row)">
            <el-icon><CopyDocument /></el-icon> 复制
          </el-button>
          <el-button v-if="!row.isActive" type="success" link @click="handleActivate(row)">
            <el-icon><Check /></el-icon> 启用
          </el-button>
          <el-button type="warning" link @click="handleEditMeta(row)">
            <el-icon><Setting /></el-icon> 编辑
          </el-button>
          <el-button type="danger" link @click="handleDelete(row)">
            <el-icon><Delete /></el-icon> 删除
          </el-button>
        </div>
      </div>
      <el-empty v-if="!loading && tableData.length === 0" description="还没有模板, 点右上角新建或导入" />
    </div>

    <!-- 新建 / 编辑 元数据弹窗 -->
    <el-dialog v-model="metaDialogVisible" :title="metaDialogTitle" width="560px" destroy-on-close>
      <el-form :model="metaForm" label-width="100px">
        <el-form-item label="模板名称" required>
          <el-input v-model="metaForm.templateName" placeholder="如: 采购收料对账单 A4" />
        </el-form-item>
        <el-form-item label="业务类型" required>
          <el-select v-model="metaForm.bizType" placeholder="选择业务类型" style="width: 100%" :disabled="metaForm.id">
            <el-option v-for="b in bizTypeOptions" :key="b.value" :label="b.label" :value="b.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务键">
          <el-input v-model="metaForm.templateCode" placeholder="留空自动生成" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="metaForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="metaDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="metaSaving" @click="submitMeta">确定</el-button>
      </template>
    </el-dialog>

    <!-- 设计器弹窗 -->
    <el-dialog v-model="designVisible" :title="`模板设计: ${designingRow?.templateName || ''}`"
               width="95%" top="2vh" destroy-on-close @closed="onDesignClosed">
      <div class="hiprint-wrap">
        <div class="left-panel">
          <div class="left-panel-title">拖拽组件到画布</div>
          <div class="hiprint-provider">
            <div v-for="g in DEFAULT_TIDS" :key="g.group" class="ep-group">
              <div class="ep-group-title">{{ g.group }}</div>
              <div class="ep-group-items">
                <span v-for="i in g.items" :key="i.tid"
                      class="ep-draggable-item" :tid="i.tid">{{ i.title }}</span>
              </div>
            </div>
          </div>
          <div class="left-panel-tips">
            <div>· 文本/HTML/表格 — 拖到画布定位</div>
            <div>· 表格组件 — 在右侧"列设置"配 field, 对应数据 items 行字段</div>
            <div>· 顶部独立字段用 field 指向 header.xxx</div>
          </div>
        </div>
        <div class="right-panel">
          <div class="toolbar">
            <el-button size="small" @click="designerPreview">预览</el-button>
            <el-button size="small" type="primary" @click="saveDesign" :loading="designSaving">保存模板</el-button>
            <span class="data-tip">字段示例:
              <b>header.*</b>: groupId / poNo / supplierName / receivedDate / itemCount / totalExpected / totalActual / totalDiff;
              <b>items[].*</b>: seq / materialNo / materialName / spec / unit / expectedQty / actualQty / diffQty / remark
            </span>
          </div>
          <!-- 设计画布 + 元素参数面板 + 分页容器 (PrintTemplate 构造时引用) -->
          <div id="hiprint-design" class="hiprint-design"></div>
          <div id="PrintElementOptionSetting"></div>
          <div class="hiprint-printPagination"></div>
        </div>
      </div>
    </el-dialog>

    <!-- 复制模板弹窗 -->
    <el-dialog v-model="copyDialogVisible" title="复制模板" width="500px" destroy-on-close>
      <el-form :model="copyForm" label-width="100px">
        <el-form-item label="源模板">
          <el-input :model-value="copyForm.sourceName" disabled />
        </el-form-item>
        <el-form-item label="新模板名称" required>
          <el-input v-model="copyForm.newName" placeholder="给副本一个新名字" />
        </el-form-item>
        <el-form-item label="业务类型">
          <el-select v-model="copyForm.bizType" placeholder="选择业务类型" style="width: 100%">
            <el-option v-for="b in bizTypeOptions" :key="b.value" :label="b.label" :value="b.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="copyDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="copySaving" @click="submitCopy">确认复制</el-button>
      </template>
    </el-dialog>

    <!-- 导入 JSON 弹窗 -->
    <el-dialog v-model="importDialogVisible" title="导入模板 JSON" width="700px" destroy-on-close>
      <el-form :model="importForm" label-width="100px">
        <el-form-item label="模板名称" required>
          <el-input v-model="importForm.templateName" placeholder="如: 采购收料对账单 A4" />
        </el-form-item>
        <el-form-item label="业务类型" required>
          <el-select v-model="importForm.bizType" placeholder="选择业务类型" style="width: 100%">
            <el-option v-for="b in bizTypeOptions" :key="b.value" :label="b.label" :value="b.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="模板 JSON" required>
          <el-input v-model="importForm.templateJson" type="textarea" :rows="12"
                    placeholder='粘贴 hiprint 模板 JSON, 例如 { "panels": [...] }' />
          <div style="color:#909399; font-size:12px; margin-top:4px">
            可在其他模板的设计器里点"保存模板"后, 从数据库取 template_json 字段; 或从合作伙伴/官方示例库获取.
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="importSaving" @click="submitImport">确认导入</el-button>
      </template>
    </el-dialog>

    <!-- 预览弹窗 -->
    <el-dialog v-model="previewVisible" title="模板预览(用示例数据)" width="900px" top="3vh" destroy-on-close>
      <div id="hiprint-preview" class="hiprint-preview"></div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, nextTick, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowDown, Plus, Upload, MagicStick, Document,
  EditPen, View, CopyDocument, Check, Setting, Delete
} from '@element-plus/icons-vue'
import { ensureHiprintInit, hiprint, DEFAULT_TIDS } from '@/utils/hiprint'
import {
  getPrintTemplates, getPrintTemplateDetail, createPrintTemplate,
  updatePrintTemplate, activatePrintTemplate, deletePrintTemplate
} from '@/api/printTemplate'
// 内置预设模板
import purchaseReceiptReconciliationPreset from './presets/purchaseReceiptReconciliation.json'
import purchaseReceiptReconciliationPortraitPreset from './presets/purchaseReceiptReconciliationPortrait.json'
import purchaseReceiptReconciliationSlipPreset from './presets/purchaseReceiptReconciliationSlip.json'

const PRESET_MAP = {
  purchase_receipt_reconciliation_a4: {
    name: '采购收料对账单 (A4 横向)',
    bizType: 'purchase_receipt_reconciliation',
    json: purchaseReceiptReconciliationPreset
  },
  purchase_receipt_reconciliation_portrait: {
    name: '采购收料对账单 (A4 竖向)',
    bizType: 'purchase_receipt_reconciliation',
    json: purchaseReceiptReconciliationPortraitPreset
  },
  purchase_receipt_reconciliation_slip: {
    name: '采购收料对账单 (80mm 小票)',
    bizType: 'purchase_receipt_reconciliation',
    json: purchaseReceiptReconciliationSlipPreset
  }
}

// 业务类型选项 - 加新场景时扩这里
const bizTypeOptions = [
  { value: 'purchase_receipt_reconciliation', label: '采购收料对账单' },
  { value: 'purchase_order', label: '采购订单' },
  { value: 'sales_order', label: '销售订单' },
  { value: 'work_order', label: '生产工单' },
  { value: 'outsource_order', label: '委外订单' },
  { value: 'inv_in', label: '入库单' },
  { value: 'inv_out', label: '出库单' }
]
function bizTypeLabel(v) {
  return bizTypeOptions.find(b => b.value === v)?.label || v
}
// 业务类型 tag 颜色 (循环 element-plus 内置色)
const BIZ_TAG_TYPES = ['primary', 'success', 'warning', 'info', 'danger']
function bizTypeTag(v) {
  const idx = bizTypeOptions.findIndex(b => b.value === v)
  return idx >= 0 ? BIZ_TAG_TYPES[idx % BIZ_TAG_TYPES.length] : 'info'
}

const queryParams = reactive({ bizType: '' })
const loading = ref(false)
const tableData = ref([])
const uniqueBizCount = computed(() => new Set(tableData.value.map(r => r.bizType)).size)

async function loadData() {
  loading.value = true
  try {
    const res = await getPrintTemplates({ bizType: queryParams.bizType || undefined })
    tableData.value = res.data || []
  } catch {} finally {
    loading.value = false
  }
}

// ===== 元数据弹窗 (新建/编辑名称等) =====
const metaDialogVisible = ref(false)
const metaSaving = ref(false)
const metaForm = reactive({ id: null, templateName: '', bizType: '', templateCode: '', remark: '' })
const metaDialogTitle = ref('新建模板')

function handleAdd() {
  metaDialogTitle.value = '新建模板'
  Object.assign(metaForm, { id: null, templateName: '', bizType: '', templateCode: '', remark: '' })
  metaDialogVisible.value = true
}
function handleEditMeta(row) {
  metaDialogTitle.value = '编辑模板'
  Object.assign(metaForm, {
    id: row.id, templateName: row.templateName, bizType: row.bizType,
    templateCode: row.templateCode, remark: row.remark || ''
  })
  metaDialogVisible.value = true
}
async function submitMeta() {
  if (!metaForm.templateName?.trim()) return ElMessage.warning('请输入模板名称')
  if (!metaForm.bizType) return ElMessage.warning('请选择业务类型')
  metaSaving.value = true
  try {
    if (metaForm.id) {
      await updatePrintTemplate(metaForm.id, {
        templateName: metaForm.templateName, templateCode: metaForm.templateCode, remark: metaForm.remark
      })
      ElMessage.success('已保存')
    } else {
      // 新建时附带默认空白模板 JSON
      await createPrintTemplate({
        templateName: metaForm.templateName, bizType: metaForm.bizType,
        templateCode: metaForm.templateCode, remark: metaForm.remark,
        templateJson: JSON.stringify({ panels: [{ index: 0, name: '默认面板', height: 297, width: 210, paperHeader: 49.5, paperFooter: 780, printElements: [] }] })
      })
      ElMessage.success('已创建, 点"设计"开始编辑')
    }
    metaDialogVisible.value = false
    loadData()
  } catch {} finally {
    metaSaving.value = false
  }
}

// ===== 设计器 =====
const designVisible = ref(false)
const designSaving = ref(false)
const designingRow = ref(null)
let hiprintTemplate = null

async function handleDesign(row) {
  designingRow.value = row
  designVisible.value = true
  // 等弹窗 DOM 挂上再 init
  await nextTick()
  setTimeout(() => initDesigner(row), 100)
}

function initDesigner(row) {
  ensureHiprintInit()
  // 给左侧 .ep-draggable-item 节点绑拖拽 (这些节点是 Vue 模板渲染的)
  hiprint.PrintElementTypeManager.buildByHtml(window.jQuery('.ep-draggable-item'))
  // 构造模板, 解析已有 JSON (没有就空白)
  let json = null
  try { json = row.templateJson ? JSON.parse(row.templateJson) : null } catch {}
  hiprintTemplate = new hiprint.PrintTemplate({
    template: json || { panels: [] },
    settingContainer: '#PrintElementOptionSetting',
    paginationContainer: '.hiprint-printPagination',
    dataMode: 1,
    history: true
  })
  const $designEl = window.jQuery('#hiprint-design')
  $designEl.empty()
  hiprintTemplate.design('#hiprint-design')
}

function onDesignClosed() {
  hiprintTemplate = null
}

async function saveDesign() {
  if (!hiprintTemplate) return
  const json = JSON.stringify(hiprintTemplate.getJson())
  designSaving.value = true
  try {
    await updatePrintTemplate(designingRow.value.id, { templateJson: json })
    ElMessage.success('模板已保存')
    designVisible.value = false
    loadData()
  } catch {} finally {
    designSaving.value = false
  }
}

function designerPreview() {
  if (!hiprintTemplate) return
  hiprintTemplate.print(buildSampleData(designingRow.value.bizType))
}

// 示例数据 (设计 / 预览时用, 让设计者看到效果)
function buildSampleData(bizType) {
  if (bizType === 'purchase_receipt_reconciliation') {
    return {
      groupId: 'RG20260513001',
      poNo: 'PO20260513001',
      supplierName: '示例供应商有限公司',
      receivedDate: '2026-05-13',
      itemCount: 2,
      totalActual: 30,
      totalQualified: 28,
      totalDefect: 2,
      items: [
        { seq: 1, materialNo: 'M001', materialName: '物料A', spec: '规格1', unit: '件', expectedQty: 10, actualQty: 10, diffQty: 0, qualifiedQty: 10, defectQty: 0, batchNo: 'B001', qcStatus: '已质检' },
        { seq: 2, materialNo: 'M002', materialName: '物料B', spec: '规格2', unit: '件', expectedQty: 20, actualQty: 20, diffQty: 0, qualifiedQty: 18, defectQty: 2, batchNo: 'B002', qcStatus: '已质检' }
      ]
    }
  }
  return { items: [] }
}

// ===== 预览 =====
const previewVisible = ref(false)
async function handlePreview(row) {
  if (!row.templateJson) return ElMessage.warning('模板还没设计内容, 请先"设计"')
  previewVisible.value = true
  await nextTick()
  setTimeout(() => {
    ensureHiprintInit()
    const t = new hiprint.PrintTemplate({ template: JSON.parse(row.templateJson) })
    const $el = window.jQuery('#hiprint-preview')
    $el.empty()
    $el.append(t.getHtml(buildSampleData(row.bizType)))
  }, 100)
}

// ===== 复制模板 =====
const copyDialogVisible = ref(false)
const copySaving = ref(false)
const copyForm = reactive({ sourceId: null, sourceName: '', newName: '', bizType: '', sourceJson: '' })

function handleCopy(row) {
  copyForm.sourceId = row.id
  copyForm.sourceName = row.templateName + ' (' + bizTypeLabel(row.bizType) + ')'
  copyForm.newName = row.templateName + ' - 副本'
  copyForm.bizType = row.bizType
  copyForm.sourceJson = row.templateJson || ''
  copyDialogVisible.value = true
}

async function submitCopy() {
  if (!copyForm.newName?.trim()) return ElMessage.warning('请输入新模板名称')
  if (!copyForm.bizType) return ElMessage.warning('请选择业务类型')
  copySaving.value = true
  try {
    // 如果源模板的 json 没在列表行里, 单独 detail 拉一遍, 保险
    let json = copyForm.sourceJson
    if (!json) {
      const detailRes = await getPrintTemplateDetail(copyForm.sourceId)
      json = detailRes.data?.templateJson || ''
    }
    await createPrintTemplate({
      templateName: copyForm.newName,
      bizType: copyForm.bizType,
      templateJson: json,
      remark: '复制自模板 #' + copyForm.sourceId
    })
    ElMessage.success('已复制, 点"设计"继续编辑')
    copyDialogVisible.value = false
    loadData()
  } catch {} finally {
    copySaving.value = false
  }
}

// ===== 导入 JSON =====
const importDialogVisible = ref(false)
const importSaving = ref(false)
const importForm = reactive({ templateName: '', bizType: '', templateJson: '' })

function handleImportOpen() {
  Object.assign(importForm, { templateName: '', bizType: '', templateJson: '' })
  importDialogVisible.value = true
}

async function submitImport() {
  if (!importForm.templateName?.trim()) return ElMessage.warning('请输入模板名称')
  if (!importForm.bizType) return ElMessage.warning('请选择业务类型')
  if (!importForm.templateJson?.trim()) return ElMessage.warning('请粘贴模板 JSON')
  // 简单校验 JSON 格式
  try {
    const parsed = JSON.parse(importForm.templateJson)
    if (!parsed || typeof parsed !== 'object') throw new Error('not object')
  } catch {
    return ElMessage.warning('模板 JSON 格式不合法')
  }
  importSaving.value = true
  try {
    await createPrintTemplate({
      templateName: importForm.templateName,
      bizType: importForm.bizType,
      templateJson: importForm.templateJson,
      remark: '导入自外部 JSON'
    })
    ElMessage.success('已导入, 点"设计"或"预览"查看效果')
    importDialogVisible.value = false
    loadData()
  } catch {} finally {
    importSaving.value = false
  }
}

// ===== 应用内置预设 =====
async function handleApplyPreset(presetKey) {
  const preset = PRESET_MAP[presetKey]
  if (!preset) return
  try {
    await ElMessageBox.confirm(
      `将创建一份内置「${preset.name}」模板,创建后可在设计器中微调。继续?`,
      '应用内置模板',
      { type: 'info' }
    )
    await createPrintTemplate({
      templateName: preset.name + ' (内置)',
      bizType: preset.bizType,
      templateJson: JSON.stringify(preset.json),
      remark: '从内置预设创建'
    })
    ElMessage.success('已创建, 可直接"启用"使用, 或点"设计"调整')
    loadData()
  } catch {}
}

// ===== 启用 =====
async function handleActivate(row) {
  await ElMessageBox.confirm(`将"${row.templateName}"设为该业务类型的当前模板?`, '提示', { type: 'info' })
  await activatePrintTemplate(row.id)
  ElMessage.success('已启用')
  loadData()
}

// ===== 删除 =====
async function handleDelete(row) {
  await ElMessageBox.confirm(`确定删除模板"${row.templateName}"?`, '提示', { type: 'warning' })
  await deletePrintTemplate(row.id)
  ElMessage.success('已删除')
  loadData()
}

onMounted(() => loadData())
</script>

<style scoped lang="scss">
.pt-page { display: flex; flex-direction: column; gap: 16px; padding: 4px; }

/* ===== Hero ===== */
.pt-hero {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: linear-gradient(135deg, #667eea 0%, #5b73e8 100%);
  color: #fff;
  border-radius: 12px;
  padding: 20px 28px;
  box-shadow: 0 4px 18px rgba(91, 115, 232, .25);
}
.pt-hero-left .pt-hero-title {
  display: flex; align-items: center;
  font-size: 20px; font-weight: 600;
  .pt-hero-icon { font-size: 24px; margin-right: 8px; }
}
.pt-hero-left .pt-hero-sub {
  margin-top: 6px; font-size: 13px; opacity: .85;
}
.pt-hero-stats {
  display: flex; gap: 32px;
  .pt-stat { text-align: center; }
  .pt-stat-num { font-size: 26px; font-weight: 700; line-height: 1; }
  .pt-stat-num-active { color: #ffd54f; }
  .pt-stat-lbl { font-size: 12px; opacity: .8; margin-top: 4px; }
}

/* ===== Toolbar ===== */
.pt-toolbar {
  display: flex; justify-content: space-between; align-items: center;
  flex-wrap: wrap; gap: 12px;
  background: #fff; padding: 12px 16px;
  border: 1px solid #ebeef5; border-radius: 8px;
}
.pt-toolbar-left :deep(.el-radio-button__inner) { padding: 8px 14px; }
.pt-toolbar-right { display: flex; gap: 8px; }

/* ===== Cards Grid ===== */
.pt-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 14px;
  min-height: 240px;
}
.pt-card {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  padding: 16px;
  display: flex; flex-direction: column;
  transition: all .2s;
  box-shadow: 0 1px 3px rgba(0,0,0,.04);
  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 6px 16px rgba(64, 158, 255, .15);
    border-color: #c0d8f3;
  }
  &.pt-card-active {
    border-color: #67c23a;
    background: linear-gradient(180deg, #f7fdf3 0%, #fff 60%);
  }
}
.pt-card-header {
  display: flex; justify-content: space-between; align-items: flex-start; gap: 10px;
  margin-bottom: 10px;
}
.pt-card-title-wrap { flex: 1; min-width: 0; }
.pt-card-title {
  font-size: 15px; font-weight: 600; color: #303133;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.pt-card-code { font-size: 12px; color: #909399; margin-top: 2px;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.pt-card-body { flex: 1; }
.pt-card-meta {
  display: flex; align-items: center; gap: 8px;
  margin-bottom: 8px;
  .pt-card-time { color: #909399; font-size: 12px; margin-left: auto; }
}
.pt-card-remark {
  font-size: 12px; color: #606266;
  background: #f5f7fa; padding: 6px 8px; border-radius: 4px;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  &.pt-card-remark-empty { color: #c0c4cc; background: transparent; padding: 6px 0; }
}
.pt-card-actions {
  display: flex; flex-wrap: wrap; gap: 0;
  margin-top: 12px; padding-top: 10px;
  border-top: 1px dashed #ebeef5;
  :deep(.el-button) { padding: 4px 8px; margin: 0 4px 0 0; font-size: 13px; }
}
.hiprint-wrap {
  display: flex;
  gap: 12px;
  height: calc(92vh - 120px);
}
.left-panel {
  width: 220px;
  flex-shrink: 0;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 8px;
  overflow-y: auto;
  .left-panel-title { font-weight: 600; margin-bottom: 8px; color: #303133; }
  .left-panel-tips { margin-top: 12px; font-size: 12px; color: #909399; line-height: 1.6; }
}
.hiprint-provider {
  min-height: 200px;
  .ep-group { margin-bottom: 12px; }
  .ep-group-title { font-size: 12px; color: #909399; margin-bottom: 6px; }
  .ep-group-items { display: flex; flex-wrap: wrap; gap: 6px; }
  .ep-draggable-item {
    display: inline-block;
    padding: 4px 10px;
    border: 1px dashed #b0c4de;
    border-radius: 4px;
    background: #f4f7fb;
    font-size: 13px;
    color: #303133;
    cursor: move;
    user-select: none;
    &:hover { border-color: #409eff; color: #409eff; background: #ecf5ff; }
  }
}
#PrintElementOptionSetting {
  border-top: 1px solid #ebeef5;
  padding: 8px;
  max-height: 220px;
  overflow: auto;
  background: #fff;
}
.hiprint-printPagination { padding: 4px 12px; background: #fafafa; }
.right-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  overflow: hidden;
  .toolbar {
    padding: 8px 12px;
    border-bottom: 1px solid #ebeef5;
    background: #fafafa;
    display: flex;
    gap: 8px;
    align-items: center;
    flex-wrap: wrap;
  }
  .data-tip {
    margin-left: 12px;
    color: #909399;
    font-size: 12px;
    line-height: 1.5;
  }
}
.hiprint-design { flex: 1; overflow: auto; background: #f5f7fa; padding: 16px; }
.hiprint-preview { background: #f5f7fa; padding: 16px; min-height: 400px; overflow: auto; }
</style>
