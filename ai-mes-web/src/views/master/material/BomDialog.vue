<template>
  <el-dialog
    v-model="visible"
    :title="`${productName} - BOM管理`"
    fullscreen
    destroy-on-close
    :close-on-click-modal="false"
    class="bom-fullscreen-dialog"
    @closed="$emit('update:modelValue', false)"
  >
    <div class="bom-layout">
      <!-- 左侧：版本列表 -->
      <el-card class="left-panel" shadow="never">
        <template #header>
          <div style="display:flex;justify-content:space-between;align-items:center">
            <span>BOM版本</span>
            <el-button type="primary" size="small" @click="handleCreateVersion">新建版本</el-button>
          </div>
        </template>
        <div v-loading="versionLoading" class="version-list">
          <div
            v-for="ver in versionList"
            :key="ver.id"
            :class="['version-item', { active: selectedVersionId === ver.id }]"
            @click="handleSelectVersion(ver)"
          >
            <div class="version-info">
              <span class="version-code">{{ ver.versionNo }}</span>
              <el-tag :type="ver.status === 'active' ? 'success' : ver.status === 'deprecated' ? 'danger' : 'info'" size="small">
                {{ ver.status === 'active' ? '激活' : ver.status === 'deprecated' ? '已废弃' : '草稿' }}
              </el-tag>
            </div>
            <div class="version-desc">{{ ver.description || '-' }}</div>
            <div class="version-actions">
              <el-button v-if="ver.status !== 'active'" type="success" link size="small" @click.stop="handleActivateVersion(ver)">激活</el-button>
              <el-button v-if="ver.status === 'active'" type="danger" link size="small" @click.stop="handleDeprecateVersion(ver)">作废</el-button>
              <el-button type="primary" link size="small" @click.stop="handleCopyVersion(ver)">复制</el-button>
            </div>
          </div>
          <el-empty v-if="!versionLoading && versionList.length === 0" description="暂无BOM版本" :image-size="60" />
        </div>
      </el-card>

      <!-- 右侧：BOM明细 -->
      <el-card class="right-panel" shadow="never">
        <template #header>
          <div class="right-panel-header">
            <span>BOM明细</span>
            <div class="right-panel-actions">
              <el-button v-if="selectedVersionId" type="success" size="small" :loading="costLoading" @click="handleCalcCost">成本卷积</el-button>
              <el-button v-if="selectedVersionId" type="warning" size="small" :loading="expandLoading" @click="handleExpandBom">BOM展开</el-button>
              <el-button v-if="selectedVersionId" type="primary" size="small" @click="handleAddItem(null)">添加物料</el-button>
            </div>
          </div>
        </template>
        <el-table
          v-if="selectedVersionId"
          :data="bomItems"
          v-loading="itemLoading"
          border stripe row-key="id" default-expand-all
          :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
        >
          <el-table-column label="来源" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.sourceType === 'purchase' ? 'warning' : ''" size="small">
                {{ row.sourceType === 'purchase' ? '外购' : '自制' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="编码" width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.materialNo || row.productNo || '-' }}</template>
          </el-table-column>
          <el-table-column label="名称" min-width="180">
            <template #default="{ row }">{{ row.materialName || row.productName || '-' }}</template>
          </el-table-column>
          <el-table-column prop="quantity" label="用量" width="100" align="right" />
          <el-table-column prop="wastageRate" label="损耗率(%)" width="110" align="right" />
          <el-table-column prop="actualQuantity" label="实际用量" width="110" align="right" />
          <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
          <el-table-column label="操作" width="260" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link size="small" @click="handleEditItem(row)">编辑</el-button>
              <el-button type="danger" link size="small" @click="handleDeleteItem(row)">删除</el-button>
              <el-button type="success" link size="small" @click="handleAddItem(row)">添加子项</el-button>
              <el-button type="warning" link size="small" @click="handleShowSubstitutes(row)">
                替代料<template v-if="row.substituteCount > 0">({{ row.substituteCount }})</template>
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="请从左侧选择一个BOM版本" :image-size="100" />
      </el-card>
    </div>

    <!-- 新建版本弹窗 -->
    <el-dialog v-model="versionDialogVisible" title="新建BOM版本" width="480px" destroy-on-close append-to-body @close="versionFormRef?.resetFields()">
      <el-form ref="versionFormRef" :model="versionForm" :rules="versionRules" label-width="100px">
        <el-form-item label="版本号" prop="versionNo">
          <el-input v-model="versionForm.versionNo" placeholder="如 V1.0" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="versionForm.description" type="textarea" :rows="3" placeholder="版本描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="versionDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleVersionSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 添加/编辑BOM项弹窗 -->
    <el-dialog v-model="itemDialogVisible" :title="itemDialogTitle" width="560px" destroy-on-close append-to-body @close="itemFormRef?.resetFields()">
      <el-form ref="itemFormRef" :model="itemForm" :rules="itemRules" label-width="100px">
        <el-form-item label="来源" prop="sourceType">
          <el-radio-group v-model="itemForm.sourceType" @change="handleSourceTypeChange">
            <el-radio value="manufacture">自制</el-radio>
            <el-radio value="purchase">外购</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="itemForm.sourceType === 'purchase'" label="物料" prop="materialId">
          <div style="display: flex; align-items: center; gap: 8px; width: 100%;">
            <el-input :value="itemForm.materialName" readonly placeholder="请选择物料" style="flex: 1;" />
            <el-button type="primary" @click="openItemMaterialPicker">选择物料</el-button>
          </div>
        </el-form-item>
        <el-form-item v-if="itemForm.sourceType === 'manufacture'" label="产品" prop="productId">
          <div style="display: flex; align-items: center; gap: 8px; width: 100%;">
            <el-input :value="itemForm.productName" readonly placeholder="请选择产品" style="flex: 1;" />
            <el-button type="primary" @click="openItemProductPicker">选择产品</el-button>
          </div>
        </el-form-item>
        <el-form-item label="用量" prop="quantity">
          <el-input-number v-model="itemForm.quantity" :min="0.001" :precision="3" style="width:100%" />
        </el-form-item>
        <el-form-item label="损耗率(%)">
          <el-input-number v-model="itemForm.wastageRate" :min="0" :max="100" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="itemForm.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleItemSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- BOM展开结果 -->
    <el-dialog v-model="expandDialogVisible" title="BOM展开 - 最底层原料清单" width="720px" destroy-on-close append-to-body>
      <el-table :data="expandList" border stripe size="small" max-height="480">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="materialName" label="物料名称" min-width="140" show-overflow-tooltip />
        <el-table-column label="规格型号" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="70" align="center" />
        <el-table-column prop="totalQty" label="总用量" width="100" align="right">
          <template #default="{ row }">{{ Number(row.totalQty || 0).toFixed(3) }}</template>
        </el-table-column>
        <el-table-column prop="path" label="来源路径" min-width="180" show-overflow-tooltip />
      </el-table>
      <template #footer>
        <el-button @click="expandDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 替代料管理 -->
    <el-dialog v-model="substituteDialogVisible" title="替代料管理" width="760px" destroy-on-close append-to-body>
      <div style="margin-bottom:12px;display:flex;justify-content:space-between;align-items:center">
        <span style="color:#606266;font-size:14px;">物料: <b>{{ currentBomItemNo ? currentBomItemNo + ' ' : '' }}{{ currentBomItemName }}</b></span>
        <el-button type="primary" size="small" @click="handleAddSubstitute">添加替代料</el-button>
      </div>
      <el-table :data="substituteList" v-loading="substituteLoading" border stripe size="small" max-height="400">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="materialNo" label="物料编号" width="130" show-overflow-tooltip />
        <el-table-column prop="materialName" label="替代物料" min-width="140" show-overflow-tooltip />
        <el-table-column prop="priority" label="优先级" width="80" align="center" />
        <el-table-column prop="substituteRatio" label="替代比例" width="100" align="right" />
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEditSubstitute(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDeleteSubstitute(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="substituteDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 替代料编辑 -->
    <el-dialog v-model="substituteFormVisible" :title="substituteFormTitle" width="500px" destroy-on-close append-to-body @close="substituteFormRef?.resetFields()">
      <el-form ref="substituteFormRef" :model="substituteForm" :rules="substituteRules" label-width="100px">
        <el-form-item label="替代物料" prop="materialId">
          <div style="display: flex; align-items: center; gap: 8px; width: 100%;">
            <el-input :value="substituteForm.materialName ? ((substituteForm.materialNo ? substituteForm.materialNo + ' ' : '') + substituteForm.materialName) : ''" readonly placeholder="请选择替代物料" style="flex: 1;" />
            <el-button type="primary" @click="openSubstituteMaterialPicker">选择物料</el-button>
          </div>
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="substituteForm.priority" :min="1" :max="99" style="width:100%" />
        </el-form-item>
        <el-form-item label="替代比例">
          <el-input-number v-model="substituteForm.substituteRatio" :min="0.01" :max="10" :precision="4" :step="0.1" style="width:100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="substituteForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="substituteFormVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubstituteSubmit">确定</el-button>
      </template>
    </el-dialog>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>

    <!-- BOM 项-外购物料选择弹窗 -->
    <MaterialPickerDialog v-model="itemMaterialPickerVisible" title="选择物料" :multiple="false"
                          @select="onItemMaterialSelected" />

    <!-- BOM 项-自制产品选择弹窗 -->
    <MaterialPickerDialog v-model="itemProductPickerVisible" title="选择产品" :multiple="false"
                          @select="onItemProductSelected" />

    <!-- 替代料选择弹窗 -->
    <MaterialPickerDialog v-model="substituteMaterialPickerVisible" title="选择替代物料" :multiple="false"
                          @select="onSubstituteMaterialSelected" />
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getBomVersions, getBomVersionDetail,
  createBomVersion, activateBomVersion, deprecateBomVersion, copyBomVersion,
  addBomItem, updateBomItem, deleteBomItem,
  calculateBomCost, expandBom,
  getBomSubstitutes, addBomSubstitute, updateBomSubstitute, deleteBomSubstitute
} from '@/api/bom'
import MaterialPickerDialog from '@/components/MaterialPickerDialog.vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  productId: { type: [Number, String], default: null },
  productName: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue'])

const visible = ref(false)
watch(() => props.modelValue, val => {
  visible.value = val
  if (val) init()
})
watch(visible, val => { if (!val) emit('update:modelValue', false) })

const submitLoading = ref(false)

function init() {
  // 物料/产品下拉改为弹窗按需加载,避免一开弹窗就拉全量数据
  loadVersions()
}

// ==================== 物料/产品弹窗选择 ====================
const itemMaterialPickerVisible = ref(false)
const itemProductPickerVisible = ref(false)
const substituteMaterialPickerVisible = ref(false)

function openItemMaterialPicker() { itemMaterialPickerVisible.value = true }
function openItemProductPicker() { itemProductPickerVisible.value = true }
function openSubstituteMaterialPicker() { substituteMaterialPickerVisible.value = true }

function onItemMaterialSelected(items) {
  if (!items || items.length === 0) return
  const m = items[0]
  itemForm.value.materialId = m.id
  itemForm.value.materialName = m.materialName || m.name || ''
}
function onItemProductSelected(items) {
  if (!items || items.length === 0) return
  const m = items[0]
  itemForm.value.productId = m.id
  itemForm.value.productName = m.materialName || m.name || ''
}
function onSubstituteMaterialSelected(items) {
  if (!items || items.length === 0) return
  const m = items[0]
  substituteForm.value.materialId = m.id
  substituteForm.value.materialNo = m.materialNo || ''
  substituteForm.value.materialName = m.materialName || m.name || ''
}

// ==================== 版本 ====================
const versionLoading = ref(false)
const versionList = ref([])
const selectedVersionId = ref(undefined)
const versionDialogVisible = ref(false)
const versionFormRef = ref(null)
const versionForm = ref({ versionNo: '', description: '' })
const versionRules = { versionNo: [{ required: true, message: '请输入版本号', trigger: 'blur' }] }

async function loadVersions() {
  if (!props.productId) return
  versionLoading.value = true
  selectedVersionId.value = undefined
  bomItems.value = []
  try {
    const res = await getBomVersions(props.productId)
    versionList.value = res.data || []
  } finally { versionLoading.value = false }
}

function handleSelectVersion(ver) {
  selectedVersionId.value = ver.id
  loadBomItems()
}

function handleCreateVersion() {
  versionForm.value = { versionNo: '', description: '' }
  versionDialogVisible.value = true
}

async function handleVersionSubmit() {
  await versionFormRef.value.validate()
  submitLoading.value = true
  try {
    await createBomVersion({ productId: props.productId, ...versionForm.value })
    ElMessage.success('BOM版本创建成功')
    versionDialogVisible.value = false
    loadVersions()
  } finally { submitLoading.value = false }
}

function handleActivateVersion(ver) {
  ElMessageBox.confirm(`确定激活版本「${ver.versionNo}」吗？激活后当前产品的其他版本将变为草稿状态。`, '提示', { type: 'warning' })
    .then(async () => { await activateBomVersion(ver.id); ElMessage.success('版本已激活'); loadVersions() })
    .catch(() => {})
}

function handleDeprecateVersion(ver) {
  ElMessageBox.confirm(`确定作废版本「${ver.versionNo}」吗？`, '提示', { type: 'warning' })
    .then(async () => { await deprecateBomVersion(ver.id); ElMessage.success('版本已作废'); loadVersions() })
    .catch(() => {})
}

function handleCopyVersion(ver) {
  ElMessageBox.confirm(`确定复制版本「${ver.versionNo}」吗？`, '提示', { type: 'info' })
    .then(async () => { await copyBomVersion(ver.id); ElMessage.success('版本复制成功'); loadVersions() })
    .catch(() => {})
}

// ==================== BOM 明细 ====================
const itemLoading = ref(false)
const bomItems = ref([])
const itemDialogVisible = ref(false)
const itemDialogTitle = ref('')
const itemFormRef = ref(null)
const itemForm = ref({ id: undefined, bomVersionId: undefined, parentItemId: null, sourceType: 'purchase', materialId: undefined, materialName: '', productId: undefined, productName: '', quantity: 1, wastageRate: 0, remark: '' })
const itemRules = computed(() => ({
  sourceType: [{ required: true, message: '请选择来源', trigger: 'change' }],
  materialId: itemForm.value.sourceType === 'purchase' ? [{ required: true, message: '请选择物料', trigger: 'change' }] : [],
  productId: itemForm.value.sourceType === 'manufacture' ? [{ required: true, message: '请选择产品', trigger: 'change' }] : [],
  quantity: [{ required: true, message: '请输入用量', trigger: 'blur' }]
}))

async function loadBomItems() {
  if (!selectedVersionId.value) { bomItems.value = []; return }
  itemLoading.value = true
  try {
    const res = await getBomVersionDetail(selectedVersionId.value)
    bomItems.value = res.data?.items || []
  } finally { itemLoading.value = false }
}

function handleAddItem(parentRow) {
  itemDialogTitle.value = '添加BOM项'
  itemForm.value = { id: undefined, bomVersionId: selectedVersionId.value, parentItemId: parentRow ? parentRow.id : 0, sourceType: 'purchase', materialId: undefined, materialName: '', productId: undefined, productName: '', quantity: 1, wastageRate: 0, remark: '' }
  itemDialogVisible.value = true
}

function handleEditItem(row) {
  itemDialogTitle.value = '编辑BOM项'
  itemForm.value = { id: row.id, bomVersionId: selectedVersionId.value, parentItemId: row.parentItemId, sourceType: row.sourceType || (row.productId ? 'manufacture' : 'purchase'), materialId: row.materialId, materialName: row.materialName || '', productId: row.productId, productName: row.productName || '', quantity: row.quantity, wastageRate: row.wastageRate || 0, remark: row.remark || '' }
  itemDialogVisible.value = true
}

function handleSourceTypeChange(val) {
  if (val === 'purchase') { itemForm.value.productId = undefined; itemForm.value.productName = '' }
  else { itemForm.value.materialId = undefined; itemForm.value.materialName = '' }
}

async function handleItemSubmit() {
  await itemFormRef.value.validate()
  submitLoading.value = true
  try {
    const data = { ...itemForm.value }
    data.itemType = data.sourceType === 'purchase' ? 'purchased' : 'raw_material'
    if (data.id) { await updateBomItem(data.id, data); ElMessage.success('BOM项更新成功') }
    else { await addBomItem(data); ElMessage.success('BOM项添加成功') }
    itemDialogVisible.value = false
    loadBomItems()
  } finally { submitLoading.value = false }
}

function handleDeleteItem(row) {
  ElMessageBox.confirm('确定删除该BOM项吗？', '提示', { type: 'warning' })
    .then(async () => { await deleteBomItem(row.id); ElMessage.success('删除成功'); loadBomItems() })
    .catch(() => {})
}

// ==================== 成本卷积 & BOM展开 ====================
const costLoading = ref(false)
const expandLoading = ref(false)
const expandDialogVisible = ref(false)
const expandList = ref([])

async function handleCalcCost() {
  costLoading.value = true
  try {
    const res = await calculateBomCost(selectedVersionId.value)
    const cost = res.data?.totalMaterialCost ?? res.data
    ElMessage.success(`成本卷积完成，物料总成本: ¥${Number(cost || 0).toFixed(2)}`)
    loadBomItems()
  } finally { costLoading.value = false }
}

async function handleExpandBom() {
  expandLoading.value = true
  try {
    const res = await expandBom(selectedVersionId.value)
    expandList.value = res.data || []
    expandDialogVisible.value = true
  } finally { expandLoading.value = false }
}

// ==================== 替代料 ====================
const substituteDialogVisible = ref(false)
const substituteLoading = ref(false)
const substituteList = ref([])
const currentBomItemId = ref(null)
const currentBomItemName = ref('')
const currentBomItemNo = ref('')
const substituteFormVisible = ref(false)
const substituteFormTitle = ref('')
const substituteFormRef = ref(null)
const substituteForm = ref({ id: undefined, bomItemId: undefined, materialId: undefined, materialNo: '', materialName: '', groupName: '', priority: 1, substituteRatio: 1.0, remark: '' })
const substituteRules = { materialId: [{ required: true, message: '请选择替代物料', trigger: 'change' }] }

async function handleShowSubstitutes(row) {
  currentBomItemId.value = row.id
  currentBomItemName.value = row.materialName || row.productName || '-'
  currentBomItemNo.value = row.materialNo || row.productNo || ''
  substituteDialogVisible.value = true
  substituteLoading.value = true
  try {
    const res = await getBomSubstitutes(row.id)
    substituteList.value = res.data || []
  } finally { substituteLoading.value = false }
}

function handleAddSubstitute() {
  substituteFormTitle.value = '添加替代料'
  substituteForm.value = { id: undefined, bomItemId: currentBomItemId.value, materialId: undefined, materialNo: '', materialName: '', groupName: '', priority: 1, substituteRatio: 1.0, remark: '' }
  substituteFormVisible.value = true
}

function handleEditSubstitute(row) {
  substituteFormTitle.value = '编辑替代料'
  substituteForm.value = { id: row.id, bomItemId: row.bomItemId, materialId: row.materialId, materialNo: row.materialNo || '', materialName: row.materialName || '', groupName: row.groupName || '', priority: row.priority || 1, substituteRatio: row.substituteRatio || 1.0, remark: row.remark || '' }
  substituteFormVisible.value = true
}

async function handleSubstituteSubmit() {
  await substituteFormRef.value.validate()
  submitLoading.value = true
  try {
    if (substituteForm.value.id) { await updateBomSubstitute(substituteForm.value.id, substituteForm.value); ElMessage.success('替代料更新成功') }
    else { await addBomSubstitute(substituteForm.value); ElMessage.success('替代料添加成功') }
    substituteFormVisible.value = false
    const res = await getBomSubstitutes(currentBomItemId.value)
    substituteList.value = res.data || []
    loadBomItems()
  } finally { submitLoading.value = false }
}

function handleDeleteSubstitute(row) {
  ElMessageBox.confirm('确定删除该替代料吗？', '提示', { type: 'warning' })
    .then(async () => {
      await deleteBomSubstitute(row.id)
      ElMessage.success('删除成功')
      const res = await getBomSubstitutes(currentBomItemId.value)
      substituteList.value = res.data || []
      loadBomItems()
    })
    .catch(() => {})
}
</script>

<style scoped lang="scss">
.bom-layout {
  display: flex;
  gap: 16px;
  height: calc(100vh - 120px);
}
.left-panel {
  width: 300px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  :deep(.el-card__body) { flex: 1; overflow-y: auto; display: flex; flex-direction: column; }
}
.right-panel {
  flex: 1;
  min-width: 0;
  overflow: auto;
}
.right-panel-header { display: flex; justify-content: space-between; align-items: center; }
.right-panel-actions { display: flex; gap: 8px; }
.version-list { flex: 1; overflow-y: auto; }
.version-item {
  padding: 10px 12px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: all 0.2s;
  &:hover { border-color: #409eff; background: #f5f7fa; }
  &.active { border-color: #409eff; background: #ecf5ff; }
}
.version-info { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.version-code { font-weight: 600; font-size: 14px; }
.version-desc { font-size: 12px; color: #909399; margin-bottom: 6px; }
.version-actions { display: flex; gap: 4px; }
</style>

<style>
.bom-fullscreen-dialog .el-dialog__body {
  padding: 16px !important;
  overflow: auto;
}
</style>
