<template>
  <div class="page-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="产品编号">
          <el-input v-model="queryParams.productNo" placeholder="产品编号" clearable style="width: 160px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="产品名称">
          <el-input v-model="queryParams.productName" placeholder="产品名称" clearable style="width: 160px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="规格">
          <el-input v-model="queryParams.spec" placeholder="规格" clearable style="width: 160px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 工具栏 -->
    <el-card shadow="never" style="margin-bottom: 0;">
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px;">
        <div>
          <el-button type="primary" @click="handleCreateBom">新建BOM</el-button>
          <el-button type="success" plain @click="handleExportAllActive">导出激活版本</el-button>
          <span style="margin-left:12px; color:#909399; font-size:12px">点击产品查看BOM版本列表</span>
        </div>
      </div>

      <!-- 产品列表(只显示有 BOM 的成品) -->
      <el-table :data="productList" v-loading="loading" border stripe @row-click="handleProductClick" style="cursor: pointer;">
        <el-table-column prop="materialNo" label="产品编号" min-width="150" />
        <el-table-column prop="materialName" label="产品名称" min-width="180" />
        <el-table-column label="规格" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ $fmtSpec ? $fmtSpec(row.spec) : (row.spec || '-') }}</template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="80" align="center" />
        <el-table-column label="当前激活版本" width="140" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.activeVersionNo" type="success" size="small">{{ row.activeVersionNo }}</el-tag>
            <span v-else style="color:#c0c4cc">未激活</span>
          </template>
        </el-table-column>
        <el-table-column prop="versionCount" label="版本总数" width="100" align="center" />
        <el-table-column label="待审批" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.pendingApprovalCount > 0" type="warning" size="small">{{ row.pendingApprovalCount }}</el-tag>
            <span v-else style="color:#c0c4cc">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click.stop="handleProductClick(row)">查看BOM</el-button>
            <el-button type="success" link size="small" :disabled="!row.activeVersionId"
                       @click.stop="handleExportRow(row)">导出激活</el-button>
            <el-button type="warning" link size="small" :disabled="!row.activeVersionId"
                       @click.stop="openCopyToProductDialog(row)">复制</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 复制 BOM 到其他产品 -->
    <MaterialPickerDialog v-model="copyPickerVisible" title="选择目标产品" :multiple="false"
                          @select="onCopyTargetSelected" />

    <!-- 产品BOM抽屉(左=版本列表, 右=选中版本明细) -->
    <el-drawer v-model="drawerVisible" :title="`${drawerProduct?.materialName || ''} - BOM 管理`"
               size="80%" destroy-on-close>
      <div style="display: flex; height: 100%; gap: 12px;">
        <!-- 左:版本列表 -->
        <div style="width: 280px; border-right: 1px solid #ebeef5; padding-right: 12px; overflow: auto;">
          <div style="margin-bottom: 12px;">
            <el-button type="primary" size="small" @click="handleCreateVersionForProduct" style="width:100%">
              新建版本
            </el-button>
          </div>
          <div style="margin-bottom: 12px;">
            <el-input v-model="versionSearchKeyword" placeholder="搜索 BOM 版本号" clearable size="small" />
          </div>
          <div v-for="v in filteredVersionList" :key="v.id"
               :class="{ 'version-card': true, 'version-card--active': v.id === selectedVersionId }"
               @click="handleSelectVersion(v)">
            <div style="display: flex; justify-content: space-between; align-items: center;">
              <span style="font-weight: 600;">{{ v.versionNo }}</span>
              <el-tag :type="versionStatusTag(v.status)" size="small">{{ versionStatusLabel(v.status) }}</el-tag>
            </div>
            <div style="margin-top: 4px; color: #909399; font-size: 12px;">
              物料 {{ v.itemCount || 0 }} 项 · {{ (v.createTime || '').substring(0, 10) }}
            </div>
            <div style="margin-top: 6px;" @click.stop>
              <el-button v-if="['draft','deprecated','rejected'].includes(v.status)" type="success" link size="small"
                         @click="handleActivateVersion(v)">
                {{ v.status === 'rejected' ? '重新申请' : '申请激活' }}
              </el-button>
              <el-button v-if="v.status === 'pending_approval'" type="info" link size="small" disabled>
                审批中
              </el-button>
              <el-button v-if="v.status === 'active'" type="danger" link size="small"
                         @click="handleDeprecateVersion(v)">作废</el-button>
              <el-button type="primary" link size="small" @click="handleCopyVersion(v)">复制</el-button>
              <el-button v-if="v.status === 'draft'" type="danger" link size="small"
                         @click="handleDeleteVersion(v)">删除</el-button>
            </div>
          </div>
          <el-empty v-if="!filteredVersionList.length" description="暂无BOM版本" :image-size="60" />
        </div>

        <!-- 右:版本明细 -->
        <div style="flex: 1; overflow: auto;">
          <div v-if="selectedVersionId" style="margin-bottom: 12px; display: flex; justify-content: space-between; align-items: center;">
            <div>
              <span style="font-weight:600">当前版本:</span>
              <el-tag size="small" style="margin-left:8px">{{ versionList.find(x => x.id === selectedVersionId)?.versionNo }}</el-tag>
            </div>
            <div>
              <el-button type="success" size="small" :loading="costLoading" @click="handleCalcCost">成本卷积</el-button>
              <el-button type="warning" size="small" :loading="expandLoading" @click="handleExpandBom">BOM展开</el-button>
              <el-button type="primary" size="small" @click="handleAddItem(null)">添加物料</el-button>
            </div>
          </div>
          <el-table v-if="selectedVersionId" :data="bomItems" v-loading="itemLoading" border stripe row-key="id" default-expand-all
                    :tree-props="{ children: 'children', hasChildren: 'hasChildren' }" size="small">
            <el-table-column prop="sourceType" label="来源" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.sourceType === 'purchase' ? 'warning' : ''" size="small">
                  {{ row.sourceType === 'purchase' ? '外购' : '自制' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="编码" width="140" show-overflow-tooltip>
              <template #default="{ row }">{{ row.materialNo || row.productNo || '-' }}</template>
            </el-table-column>
            <el-table-column label="名称" min-width="180" show-overflow-tooltip>
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
          <el-empty v-else description="请先从左侧选择一个 BOM 版本" />
        </div>
      </div>
    </el-drawer>

    <!-- 旧 BOM明细弹窗已迁移到抽屉 -->


    <!-- 新建BOM弹窗（含产品选择） -->
    <el-dialog v-model="versionDialogVisible" :title="versionDialogTitle" width="500px" destroy-on-close @close="resetVersionForm">
      <el-form ref="versionFormRef" :model="versionForm" :rules="versionRules" label-width="100px">
        <el-form-item label="产品" prop="productId">
          <div style="display: flex; align-items: center; gap: 8px; width: 100%;">
            <el-input :value="versionForm.productName" readonly placeholder="请选择产品" style="flex: 1;" :disabled="!!versionForm.id" />
            <el-button type="primary" :disabled="!!versionForm.id" @click="openVersionProductPicker">选择产品</el-button>
          </div>
        </el-form-item>
        <el-form-item label="版本号" prop="versionNo">
          <el-input v-model="versionForm.versionNo" placeholder="请输入版本号，如 V1.0" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="versionForm.description" type="textarea" :rows="3" placeholder="请输入版本描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="versionDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleVersionSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 添加/编辑BOM项弹窗 -->
    <el-dialog v-model="itemDialogVisible" :title="itemDialogTitle" width="560px" destroy-on-close append-to-body @close="resetItemForm">
      <el-form ref="itemFormRef" :model="itemForm" :rules="itemRules" label-width="100px">
        <el-form-item label="来源" prop="sourceType">
          <el-radio-group v-model="itemForm.sourceType" @change="handleSourceTypeChange">
            <el-radio value="manufacture">自制</el-radio>
            <el-radio value="purchase">外购</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="itemForm.sourceType === 'purchase'" label="物料" prop="materialId">
          <div style="display: flex; align-items: center; gap: 8px; width: 100%;">
            <el-input :value="itemForm.materialName ? ((itemForm.materialNo ? itemForm.materialNo + ' ' : '') + itemForm.materialName) : ''" readonly placeholder="请选择物料" style="flex: 1;" />
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
          <el-input-number v-model="itemForm.quantity" :min="0.001" :precision="3" style="width: 100%" />
        </el-form-item>
        <el-form-item label="损耗率(%)" prop="wastageRate">
          <el-input-number v-model="itemForm.wastageRate" :min="0" :max="100" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="itemForm.remark" type="textarea" :rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleItemSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- BOM展开结果弹窗 -->
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
      <template #footer><el-button @click="expandDialogVisible = false">关闭</el-button></template>
    </el-dialog>

    <!-- 替代料管理弹窗 -->
    <el-dialog v-model="substituteDialogVisible" title="替代料管理" width="760px" destroy-on-close append-to-body>
      <div style="margin-bottom: 12px; display: flex; justify-content: space-between; align-items: center;">
        <span style="color: #606266; font-size: 14px;">物料: <b>{{ currentBomItemNo ? currentBomItemNo + ' ' : '' }}{{ currentBomItemName }}</b></span>
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
      <template #footer><el-button @click="substituteDialogVisible = false">关闭</el-button></template>
    </el-dialog>

    <!-- 替代料编辑弹窗 -->
    <el-dialog v-model="substituteFormVisible" :title="substituteFormTitle" width="500px" destroy-on-close append-to-body @close="resetSubstituteForm">
      <el-form ref="substituteFormRef" :model="substituteForm" :rules="substituteRules" label-width="100px">
        <el-form-item label="替代物料" prop="materialId">
          <div style="display: flex; align-items: center; gap: 8px; width: 100%;">
            <el-input :value="substituteForm.materialName ? ((substituteForm.materialNo ? substituteForm.materialNo + ' ' : '') + substituteForm.materialName) : ''" readonly placeholder="请选择替代物料" style="flex: 1;" />
            <el-button type="primary" @click="openSubstituteMaterialPicker">选择物料</el-button>
          </div>
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="substituteForm.priority" :min="1" :max="99" style="width: 100%" />
        </el-form-item>
        <el-form-item label="替代比例">
          <el-input-number v-model="substituteForm.substituteRatio" :min="0.01" :max="10" :precision="4" :step="0.1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="substituteForm.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="substituteFormVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubstituteSubmit">确定</el-button>
      </template>
    </el-dialog>

    <ColumnConfigDialog ref="colConfigDialogRef" :all-columns="allColumns" :visible-keys="visibleColumns.map(c=>c.key)" @save="saveConfig" />

    <!-- 物料选择弹窗（BOM项 & 替代料共用） -->
    <MaterialPickerDialog v-model="materialPickerVisible" title="选择物料" :multiple="false" @select="onBomMaterialSelected" />

    <!-- 新建BOM版本-产品选择弹窗 -->
    <MaterialPickerDialog v-model="versionProductPickerVisible" title="选择产品" :multiple="false"
                          @select="onVersionProductSelected" />

    <!-- 添加BOM项-自制产品选择弹窗 -->
    <MaterialPickerDialog v-model="itemProductPickerVisible" title="选择产品" :multiple="false"
                          @select="onItemProductSelected" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Setting } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getBomProducts, getBomVersions, getBomVersionDetail,
  createBomVersion, activateBomVersion, deprecateBomVersion, copyBomVersion, copyBomVersionToProduct, deleteBomVersion,
  addBomItem, updateBomItem, deleteBomItem,
  calculateBomCost, expandBom,
  getBomSubstitutes, addBomSubstitute, updateBomSubstitute, deleteBomSubstitute,
  exportBomActiveVersion, exportBomActiveVersionsAll
} from '@/api/bom'
import { useColumnConfig } from '@/composables/useColumnConfig'
import ColumnConfigDialog from '@/components/ColumnConfigDialog.vue'
import MaterialPickerDialog from '@/components/MaterialPickerDialog.vue'

// ==================== 列配置（应用于版本列表） ====================
const BOM_LIST_COLUMNS = [
  { key: 'productName', label: '产品', minWidth: 200 },
  { key: 'versionNo', label: '版本号', minWidth: 100 },
  { key: 'status', label: '状态', minWidth: 90, align: 'center' },
  { key: 'description', label: '描述', minWidth: 160 },
  { key: 'createTime', label: '创建时间', minWidth: 150 },
]
const colConfigDialogRef = ref(null)
const { visibleColumns, allColumns, loadColumnConfig, saveConfig } = useColumnConfig('bom', BOM_LIST_COLUMNS)

// ==================== 基础数据 ====================
const submitLoading = ref(false)

// ==================== 产品列表(新主列表) ====================
const queryParams = ref({ productNo: '', productName: '', spec: '' })
const loading = ref(false)
const productList = ref([])

async function loadData() {
  loading.value = true
  try {
    const res = await getBomProducts(queryParams.value)
    productList.value = res.data || []
  } catch {} finally { loading.value = false }
}

function handleSearch() { loadData() }
function handleReset() { queryParams.value = { productNo: '', productName: '', spec: '' }; loadData() }

function downloadBlob(res, name) {
  const url = window.URL.createObjectURL(new Blob([res.data || res]))
  const a = document.createElement('a'); a.href = url; a.download = name; a.click()
  window.URL.revokeObjectURL(url)
}
async function handleExportRow(row) {
  if (!row.activeVersionId) return ElMessage.warning('该产品无激活版本')
  try {
    const res = await exportBomActiveVersion(row.id)
    downloadBlob(res, `BOM_${row.materialNo || row.materialName}_${row.activeVersionNo || 'active'}.xlsx`)
  } catch { ElMessage.error('导出失败') }
}
async function handleExportAllActive() {
  try {
    const res = await exportBomActiveVersionsAll(queryParams.value)
    downloadBlob(res, `BOM_激活版本_${new Date().toISOString().slice(0,10)}.xlsx`)
  } catch { ElMessage.error('导出失败') }
}

// 版本状态显示
function versionStatusTag(s) {
  return ({ draft: 'info', pending_approval: 'warning', active: 'success', deprecated: 'danger', rejected: 'danger' })[s] || 'info'
}
function versionStatusLabel(s) {
  return ({ draft: '草稿', pending_approval: '审批中', active: '激活', deprecated: '已废弃', rejected: '已驳回' })[s] || s
}

// ==================== 抽屉: 版本列表 + 明细 ====================
const drawerVisible = ref(false)
const drawerProduct = ref(null)
const versionList = ref([])
const versionSearchKeyword = ref('')
const filteredVersionList = computed(() => {
  const kw = (versionSearchKeyword.value || '').trim().toLowerCase()
  if (!kw) return versionList.value
  return versionList.value.filter(v => (v.versionNo || '').toLowerCase().includes(kw))
})
const selectedVersionId = ref(null)
const selectedVersionProductId = ref(null)
const itemLoading = ref(false)
const bomItems = ref([])

async function handleProductClick(row) {
  drawerProduct.value = row
  drawerVisible.value = true
  selectedVersionProductId.value = row.productId
  await loadVersionsForProduct(row.productId)
  // 默认选中激活版本,没有就选第一条
  const active = versionList.value.find(v => v.status === 'active')
  const first = active || versionList.value[0]
  if (first) {
    await handleSelectVersion(first)
  } else {
    selectedVersionId.value = null
    bomItems.value = []
  }
}

async function loadVersionsForProduct(productId) {
  try {
    const res = await getBomVersions(productId)
    versionList.value = res.data || []
  } catch {
    versionList.value = []
  }
}

async function handleSelectVersion(v) {
  selectedVersionId.value = v.id
  await loadBomItems()
}

// 保持原详情弹窗变量以兼容原有方法(但默认不用它)
const detailDialogVisible = ref(false)
const detailDialogTitle = ref('')

function handleViewDetail(row) {
  // 兼容旧入口,直接委托到 handleProductClick
  const p = { productId: row.productId, materialName: row.productName }
  handleProductClick(p)
}

function handleCreateVersionForProduct() {
  // 在当前产品下新建一个版本
  if (!drawerProduct.value) return
  versionForm.value = {
    id: null,
    productId: drawerProduct.value.productId,
    versionNo: '',
    description: ''
  }
  versionDialogTitle.value = '新建BOM版本'
  versionDialogVisible.value = true
}

async function loadBomItems() {
  if (!selectedVersionId.value) { bomItems.value = []; return }
  itemLoading.value = true
  try {
    const res = await getBomVersionDetail(selectedVersionId.value)
    bomItems.value = res.data?.items || []
  } catch {} finally { itemLoading.value = false }
}

// ==================== 新建/编辑BOM版本 ====================
const versionDialogVisible = ref(false)
const versionDialogTitle = ref('新建BOM')
const versionFormRef = ref(null)
const versionForm = ref({ id: undefined, productId: undefined, productName: '', versionNo: '', description: '' })
const versionRules = {
  productId: [{ required: true, message: '请选择产品', trigger: 'change' }],
  versionNo: [{ required: true, message: '请输入版本号', trigger: 'blur' }]
}

function handleCreateBom() {
  versionDialogTitle.value = '新建BOM'
  versionForm.value = { id: undefined, productId: undefined, productName: '', versionNo: '', description: '' }
  versionDialogVisible.value = true
}

function handleEditVersion(row) {
  versionDialogTitle.value = '编辑BOM版本'
  versionForm.value = { id: row.id, productId: row.productId, productName: row.productName || row.materialName || '', versionNo: row.versionNo, description: row.description || '' }
  versionDialogVisible.value = true
}

async function handleVersionSubmit() {
  await versionFormRef.value.validate()
  submitLoading.value = true
  try {
    if (versionForm.value.id) {
      ElMessage.info('版本信息已保存')
    } else {
      await createBomVersion(versionForm.value)
      ElMessage.success('BOM版本创建成功')
    }
    versionDialogVisible.value = false
    await refreshDrawer()
  } catch {} finally { submitLoading.value = false }
}

function resetVersionForm() { versionFormRef.value?.resetFields() }

// ==================== 版本操作 ====================
async function refreshDrawer() {
  if (drawerProduct.value) {
    await loadVersionsForProduct(drawerProduct.value.productId)
  }
  loadData()
}

function handleActivateVersion(ver) {
  ElMessageBox.confirm(
    `申请激活版本「${ver.versionNo}」,需审批通过后生效。确定提交?`,
    '申请激活',
    { type: 'warning' }
  ).then(async () => {
    try {
      await activateBomVersion(ver.id)
      ElMessage.success('激活申请已提交,等待审批')
      await refreshDrawer()
    } catch {}
  }).catch(() => {})
}

function handleDeprecateVersion(ver) {
  ElMessageBox.confirm(`确定作废版本「${ver.versionNo}」吗？`, '提示', { type: 'warning' })
    .then(async () => {
      try { await deprecateBomVersion(ver.id); ElMessage.success('版本已作废'); await refreshDrawer() } catch {}
    }).catch(() => {})
}

function handleCopyVersion(ver) {
  ElMessageBox.prompt(`复制版本「${ver.versionNo}」,请输入新版本号:`, '复制 BOM 版本', {
    confirmButtonText: '确定复制',
    cancelButtonText: '取消',
    inputPlaceholder: '请输入新版本号,例如: V2.0',
    inputPattern: /\S+/,
    inputErrorMessage: '版本号不能为空',
    inputValue: ''
  }).then(async ({ value }) => {
    try {
      await copyBomVersion(ver.id, { versionNo: (value || '').trim() })
      ElMessage.success('版本复制成功')
      await refreshDrawer()
    } catch {}
  }).catch(() => {})
}

// ==================== 复制 BOM 到其他产品 ====================
const copyPickerVisible = ref(false)
const copySourceRow = ref(null)

function openCopyToProductDialog(row) {
  if (!row.activeVersionId) {
    ElMessage.warning('该产品暂无激活的BOM版本,只能复制已激活版本')
    return
  }
  copySourceRow.value = row
  copyPickerVisible.value = true
}

async function onCopyTargetSelected(mats) {
  const target = Array.isArray(mats) ? mats[0] : mats
  if (!target || !target.id) return
  const src = copySourceRow.value
  if (!src) return
  if (target.id === src.productId) {
    ElMessage.warning('目标产品不能与源产品相同')
    return
  }
  const srcName = src.materialName || src.name || ''
  const tgtName = target.materialName || target.name || ''
  try {
    await ElMessageBox.confirm(
      `确定将「${srcName}」的 BOM(激活版本 ${src.activeVersionNo || ''}) 复制到「${tgtName}」吗？\n复制后新 BOM 为草稿状态，可在目标产品下继续编辑。`,
      '确认复制 BOM',
      { type: 'warning', confirmButtonText: '确认复制', cancelButtonText: '取消' }
    )
  } catch {
    return // 用户取消,保持弹窗打开让其重新选择
  }
  try {
    await copyBomVersionToProduct(src.activeVersionId, target.id)
    ElMessage.success(`已复制 BOM 到「${tgtName}」(草稿状态)`)
    copyPickerVisible.value = false
    loadData()
  } catch {
    // handled by interceptor
  }
}

function handleDeleteVersion(ver) {
  ElMessageBox.confirm(`确定删除BOM版本「${ver.versionNo}」吗？删除后不可恢复。`, '提示', { type: 'warning' })
    .then(async () => {
      try { await deleteBomVersion(ver.id); ElMessage.success('版本已删除'); await refreshDrawer() } catch {}
    }).catch(() => {})
}

// ==================== BOM明细项操作 ====================
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
  } catch {} finally { submitLoading.value = false }
}

function resetItemForm() { itemFormRef.value?.resetFields() }

function handleDeleteItem(row) {
  ElMessageBox.confirm('确定删除该BOM项吗？', '提示', { type: 'warning' })
    .then(async () => { try { await deleteBomItem(row.id); ElMessage.success('删除成功'); loadBomItems() } catch {} })
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
  } catch {} finally { costLoading.value = false }
}

async function handleExpandBom() {
  expandLoading.value = true
  try {
    const res = await expandBom(selectedVersionId.value)
    expandList.value = res.data || []
    expandDialogVisible.value = true
  } catch {} finally { expandLoading.value = false }
}

// ==================== 替代料管理 ====================
const substituteDialogVisible = ref(false)
const substituteLoading = ref(false)
const substituteList = ref([])
const currentBomItemId = ref(null)
const currentBomItemName = ref('')
const currentBomItemNo = ref('')
const substituteFormVisible = ref(false)
const substituteFormTitle = ref('')
const substituteFormRef = ref(null)
const substituteForm = ref({ id: undefined, bomItemId: undefined, materialId: undefined, materialName: '', groupName: '', priority: 1, substituteRatio: 1.0, remark: '' })
const substituteRules = { materialId: [{ required: true, message: '请选择替代物料', trigger: 'change' }] }

async function handleShowSubstitutes(row) {
  currentBomItemId.value = row.id
  currentBomItemName.value = row.materialName || row.productName || '-'
  currentBomItemNo.value = row.materialNo || row.productNo || ''
  substituteDialogVisible.value = true
  substituteLoading.value = true
  try { const res = await getBomSubstitutes(row.id); substituteList.value = res.data || [] }
  catch {} finally { substituteLoading.value = false }
}

function handleAddSubstitute() {
  substituteFormTitle.value = '添加替代料'
  substituteForm.value = { id: undefined, bomItemId: currentBomItemId.value, materialId: undefined, materialName: '', groupName: '', priority: 1, substituteRatio: 1.0, remark: '' }
  substituteFormVisible.value = true
}

function handleEditSubstitute(row) {
  substituteFormTitle.value = '编辑替代料'
  substituteForm.value = { id: row.id, bomItemId: row.bomItemId, materialId: row.materialId, materialName: row.materialName || '', groupName: row.groupName || '', priority: row.priority || 1, substituteRatio: row.substituteRatio || 1.0, remark: row.remark || '' }
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
  } catch {} finally { submitLoading.value = false }
}

function resetSubstituteForm() { substituteFormRef.value?.resetFields() }

function handleDeleteSubstitute(row) {
  ElMessageBox.confirm('确定删除该替代料吗？', '提示', { type: 'warning' })
    .then(async () => {
      try {
        await deleteBomSubstitute(row.id); ElMessage.success('删除成功')
        const res = await getBomSubstitutes(currentBomItemId.value); substituteList.value = res.data || []
        loadBomItems()
      } catch {}
    }).catch(() => {})
}

// ==================== 物料选择弹窗 ====================
const materialPickerVisible = ref(false)
const materialPickerTarget = ref('item') // 'item' | 'substitute'

function openItemMaterialPicker() {
  materialPickerTarget.value = 'item'
  materialPickerVisible.value = true
}
function openSubstituteMaterialPicker() {
  materialPickerTarget.value = 'substitute'
  materialPickerVisible.value = true
}
// 新建BOM版本-产品选择
const versionProductPickerVisible = ref(false)
function openVersionProductPicker() {
  versionProductPickerVisible.value = true
}
function onVersionProductSelected(items) {
  if (!items || items.length === 0) return
  const m = items[0]
  versionForm.value.productId = m.id
  versionForm.value.productName = m.materialName || m.name || ''
}

// BOM项-自制产品选择
const itemProductPickerVisible = ref(false)
function openItemProductPicker() {
  itemProductPickerVisible.value = true
}
function onItemProductSelected(items) {
  if (!items || items.length === 0) return
  const m = items[0]
  itemForm.value.productId = m.id
  itemForm.value.productName = m.materialName || m.name || ''
}

function onBomMaterialSelected(items) {
  if (!items || items.length === 0) return
  const m = items[0]
  if (materialPickerTarget.value === 'item') {
    itemForm.value.materialId = m.id
    itemForm.value.materialNo = m.materialNo || ''
    itemForm.value.materialName = m.materialName
  } else {
    substituteForm.value.materialId = m.id
    substituteForm.value.materialNo = m.materialNo || ''
    substituteForm.value.materialName = m.materialName
  }
}

// ==================== 生命周期 ====================
onMounted(() => {
  loadData()
  loadColumnConfig()
})
</script>

<style scoped lang="scss">
.page-container { padding: 16px; }
.search-card { margin-bottom: 12px; }
.version-card {
  padding: 12px;
  margin-bottom: 8px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
  &:hover { border-color: #409eff; background: #ecf5ff; }
  &--active { border-color: #409eff; background: #ecf5ff; box-shadow: 0 0 0 2px rgba(64,158,255,0.2); }
}
</style>
