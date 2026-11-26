<!--
  BOM版本管理页面
  功能：
  1. 顶部产品信息卡片 — 展示当前选中产品的基本信息
  2. BOM版本选择下拉框 — 切换不同版本查看对应的BOM子件
  3. BOM子件树形表格 — 树状展示BOM物料清单层级
  4. 操作按钮 — 添加子件、版本对比、生效等
-->
<template>
  <div class="page-container">
    <!-- 顶部：产品选择 + 产品信息卡片 -->
    <el-card shadow="never" class="product-card">
      <template #header>
        <div class="card-header">
          <span>产品信息</span>
          <el-select
            v-model="selectedProductId"
            placeholder="请选择产品"
            filterable
            remote
            :remote-method="searchProducts"
            :loading="productSearching"
            style="width: 260px"
            @change="handleProductChange"
          >
            <el-option
              v-for="item in productOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </div>
      </template>
      <!-- 产品详情展示 -->
      <el-descriptions v-if="productDetail" :column="4" border size="small">
        <el-descriptions-item label="产品编号">{{ productDetail.code || '-' }}</el-descriptions-item>
        <el-descriptions-item label="产品名称">{{ productDetail.name || '-' }}</el-descriptions-item>
        <el-descriptions-item label="规格型号">{{ $fmtSpec(productDetail.spec) }}</el-descriptions-item>
        <el-descriptions-item label="单位">{{ productDetail.unit || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-empty v-else description="请选择一个产品" :image-size="60" />
    </el-card>

    <!-- 版本选择 + 操作按钮 -->
    <el-card shadow="never" style="margin-top: 12px">
      <div class="version-toolbar">
        <div class="version-toolbar__left">
          <span class="version-toolbar__label">BOM版本：</span>
          <el-select
            v-model="selectedVersionId"
            placeholder="请选择版本"
            :disabled="!selectedProductId"
            style="width: 240px"
            @change="handleVersionChange"
          >
            <el-option
              v-for="ver in versionList"
              :key="ver.id"
              :label="`${ver.versionCode}${ver.status === 'ACTIVE' ? '（当前激活）' : ''}`"
              :value="ver.id"
            />
          </el-select>
          <el-tag
            v-if="currentVersion"
            :type="currentVersion.status === 'ACTIVE' ? 'success' : 'info'"
            style="margin-left: 8px"
          >
            {{ currentVersion.status === 'ACTIVE' ? '已激活' : '草稿' }}
          </el-tag>
        </div>
        <div class="version-toolbar__right">
          <el-button
            type="primary"
            :disabled="!selectedVersionId"
            @click="handleAddItem(null)"
          >
            添加子件
          </el-button>
          <el-button
            :disabled="versionList.length < 2"
            @click="handleCompare"
          >
            版本对比
          </el-button>
          <el-button
            type="success"
            :disabled="!selectedVersionId || currentVersion?.status === 'ACTIVE'"
            @click="handleActivate"
          >
            生效
          </el-button>
          <el-button
            :disabled="!selectedVersionId"
            @click="handleCopy"
          >
            复制版本
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- BOM子件树形表格 -->
    <el-card shadow="never" style="margin-top: 12px">
      <el-table
        v-if="selectedVersionId"
        v-loading="itemLoading"
        :data="bomItems"
        border
        stripe
        row-key="id"
        default-expand-all
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
      >
        <el-table-column type="index" label="#" width="60" align="center" />
        <el-table-column prop="itemType" label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.itemType === 'MATERIAL' ? '' : 'success'" size="small">
              {{ row.itemType === 'MATERIAL' ? '物料' : '产品' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="编号" width="140" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.itemCode || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="名称" min-width="180">
          <template #default="{ row }">
            {{ row.itemType === 'MATERIAL' ? row.materialName : row.productName }}
          </template>
        </el-table-column>
        <el-table-column label="规格型号" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
        </el-table-column>
        <el-table-column prop="quantity" label="用量" width="100" align="right" />
        <el-table-column prop="unit" label="单位" width="70" align="center" />
        <el-table-column prop="wastageRate" label="损耗率(%)" width="110" align="right" />
        <el-table-column prop="actualQuantity" label="实际用量" width="110" align="right" />
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEditItem(row)">编辑</el-button>
            <el-button type="success" link size="small" @click="handleAddItem(row)">添加子项</el-button>
            <el-button type="danger" link size="small" @click="handleDeleteItem(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="请选择产品和BOM版本" :image-size="100" />
    </el-card>

    <!-- 添加/编辑子件弹窗 -->
    <el-dialog
      v-model="itemDialogVisible"
      :title="itemForm.id ? '编辑BOM子件' : '添加BOM子件'"
      width="560px"
      destroy-on-close
      @close="resetItemForm"
    >
      <el-form ref="itemFormRef" :model="itemForm" :rules="itemRules" label-width="100px">
        <el-form-item label="类型" prop="itemType">
          <el-radio-group v-model="itemForm.itemType">
            <el-radio value="MATERIAL">物料</el-radio>
            <el-radio value="PRODUCT">产品</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="itemForm.itemType === 'MATERIAL'" label="物料" prop="materialId">
          <div style="display: flex; align-items: center; gap: 8px; width: 100%;">
            <el-input :value="itemForm.materialName ? ((itemForm.materialNo ? itemForm.materialNo + ' ' : '') + itemForm.materialName) : ''" readonly placeholder="请选择物料" style="flex: 1;" />
            <el-button type="primary" @click="openMaterialPicker">选择物料</el-button>
          </div>
        </el-form-item>
        <el-form-item v-if="itemForm.itemType === 'PRODUCT'" label="产品" prop="productId">
          <el-select v-model="itemForm.productId" placeholder="请选择产品" filterable remote
                     :remote-method="searchProducts" :loading="productSearching" style="width: 100%">
            <el-option
              v-for="item in productOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
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

    <!-- 版本对比弹窗 -->
    <el-dialog v-model="compareVisible" title="BOM版本对比" width="900px" destroy-on-close>
      <el-row :gutter="16" style="margin-bottom: 16px">
        <el-col :span="12">
          <el-select v-model="compareVersionA" placeholder="版本A" style="width: 100%">
            <el-option
              v-for="ver in versionList"
              :key="ver.id"
              :label="ver.versionCode"
              :value="ver.id"
            />
          </el-select>
        </el-col>
        <el-col :span="12">
          <el-select v-model="compareVersionB" placeholder="版本B" style="width: 100%">
            <el-option
              v-for="ver in versionList"
              :key="ver.id"
              :label="ver.versionCode"
              :value="ver.id"
            />
          </el-select>
        </el-col>
      </el-row>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-table :data="compareDataA" border size="small" max-height="400">
            <el-table-column prop="materialName" label="名称" min-width="120" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.itemType === 'MATERIAL' ? row.materialName : row.productName }}
              </template>
            </el-table-column>
            <el-table-column prop="quantity" label="用量" width="80" align="right" />
            <el-table-column prop="wastageRate" label="损耗率(%)" width="100" align="right" />
          </el-table>
        </el-col>
        <el-col :span="12">
          <el-table :data="compareDataB" border size="small" max-height="400">
            <el-table-column prop="materialName" label="名称" min-width="120" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.itemType === 'MATERIAL' ? row.materialName : row.productName }}
              </template>
            </el-table-column>
            <el-table-column prop="quantity" label="用量" width="80" align="right" />
            <el-table-column prop="wastageRate" label="损耗率(%)" width="100" align="right" />
          </el-table>
        </el-col>
      </el-row>
      <template #footer>
        <el-button @click="compareVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 物料选择弹窗 -->
    <MaterialPickerDialog v-model="materialPickerVisible" title="选择物料" :multiple="false" @select="onMaterialSelected" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getBomVersions,
  getBomVersionDetail,
  activateBomVersion,
  copyBomVersion,
  addBomItem,
  updateBomItem,
  deleteBomItem
} from '@/api/bom'
import { getProductDetail, getMaterialPage } from '@/api/master'
import MaterialPickerDialog from '@/components/MaterialPickerDialog.vue'

// ==================== 下拉选项 ====================
const productOptions = ref([])
const materialOptions = ref([])

async function loadOptions() {
  // 产品/物料下拉改 remote 按需搜索,首屏不加载全量
}

const productSearching = ref(false)
let _prodSearchTimer = null
async function searchProducts(keyword) {
  if (_prodSearchTimer) clearTimeout(_prodSearchTimer)
  if (!keyword || !keyword.trim()) { productOptions.value = []; return }
  _prodSearchTimer = setTimeout(async () => {
    productSearching.value = true
    try {
      const res = await getMaterialPage({ materialName: keyword.trim(), pageNum: 1, pageSize: 30 })
      const list = (res.data?.records || []).map(m => ({ id: m.id, name: m.materialName, spec: m.spec }))
      const map = new Map()
      productOptions.value.forEach(p => map.set(p.id, p))
      list.forEach(p => map.set(p.id, p))
      productOptions.value = Array.from(map.values())
    } catch {} finally { productSearching.value = false }
  }, 300)
}

// 编辑时按 ID 加载产品(用于回显当前选中产品)
async function ensureProductInOptions(id, fallbackName) {
  if (!id || productOptions.value.find(p => p.id === id)) return
  productOptions.value = [...productOptions.value, { id, name: fallbackName || `产品#${id}` }]
}

// ==================== 产品选择 ====================
const selectedProductId = ref(undefined)
const productDetail = ref(null)

/** 切换产品时加载产品详情和版本列表 */
async function handleProductChange() {
  selectedVersionId.value = undefined
  bomItems.value = []

  if (!selectedProductId.value) {
    productDetail.value = null
    versionList.value = []
    return
  }

  // 从选项中获取产品基本信息
  const product = productOptions.value.find(p => p.id === selectedProductId.value)
  productDetail.value = product || null

  // 尝试加载完整产品详情
  try {
    const res = await getProductDetail(selectedProductId.value)
    productDetail.value = res.data || product
  } catch {
    // 如果详情接口失败，保留select中的基本信息
  }

  loadVersions()
}

// ==================== 版本管理 ====================
const versionLoading = ref(false)
const versionList = ref([])
const selectedVersionId = ref(undefined)

/** 当前选中的版本对象 */
const currentVersion = computed(() => {
  return versionList.value.find(v => v.id === selectedVersionId.value) || null
})

/** 加载版本列表 */
async function loadVersions() {
  if (!selectedProductId.value) {
    versionList.value = []
    return
  }
  versionLoading.value = true
  try {
    const res = await getBomVersions(selectedProductId.value)
    versionList.value = res.data || []
    // 如果存在激活版本则默认选中
    const activeVer = versionList.value.find(v => v.status === 'ACTIVE')
    if (activeVer) {
      selectedVersionId.value = activeVer.id
      loadBomItems()
    }
  } catch {
    // 由拦截器统一处理
  } finally {
    versionLoading.value = false
  }
}

/** 切换版本时加载BOM子件 */
function handleVersionChange() {
  loadBomItems()
}

/** 激活当前版本 */
function handleActivate() {
  if (!currentVersion.value) return
  ElMessageBox.confirm(
    `确定激活版本「${currentVersion.value.versionCode}」吗？激活后当前产品的其他版本将变为草稿状态。`,
    '提示',
    { type: 'warning' }
  ).then(async () => {
    try {
      await activateBomVersion(selectedVersionId.value)
      ElMessage.success('版本已激活')
      loadVersions()
    } catch {
      // 由拦截器统一处理
    }
  }).catch(() => {})
}

/** 复制当前版本 */
function handleCopy() {
  if (!currentVersion.value) return
  ElMessageBox.confirm(
    `确定复制版本「${currentVersion.value.versionCode}」吗？`,
    '提示',
    { type: 'info' }
  ).then(async () => {
    try {
      await copyBomVersion(selectedVersionId.value)
      ElMessage.success('版本复制成功')
      loadVersions()
    } catch {
      // 由拦截器统一处理
    }
  }).catch(() => {})
}

// ==================== BOM子件 ====================
const itemLoading = ref(false)
const bomItems = ref([])

/** 加载BOM子件树 */
async function loadBomItems() {
  if (!selectedVersionId.value) {
    bomItems.value = []
    return
  }
  itemLoading.value = true
  try {
    const res = await getBomVersionDetail(selectedVersionId.value)
    bomItems.value = res.data?.items || []
  } catch {
    // 由拦截器统一处理
  } finally {
    itemLoading.value = false
  }
}

// ==================== 子件增删改 ====================
const submitLoading = ref(false)
const itemDialogVisible = ref(false)
const itemFormRef = ref(null)
const itemForm = ref({
  id: undefined,
  versionId: undefined,
  parentId: undefined,
  itemType: 'MATERIAL',
  materialId: undefined,
  materialName: '',
  productId: undefined,
  quantity: 1,
  wastageRate: 0,
  remark: ''
})
const itemRules = {
  itemType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  materialId: [{ required: true, message: '请选择物料', trigger: 'change' }],
  productId: [{ required: true, message: '请选择产品', trigger: 'change' }],
  quantity: [{ required: true, message: '请输入用量', trigger: 'blur' }]
}

/** 打开添加子件弹窗 */
function handleAddItem(parentRow) {
  itemForm.value = {
    id: undefined,
    versionId: selectedVersionId.value,
    parentId: parentRow ? parentRow.id : null,
    itemType: 'MATERIAL',
    materialId: undefined,
    materialName: '',
    productId: undefined,
    quantity: 1,
    wastageRate: 0,
    remark: ''
  }
  itemDialogVisible.value = true
}

/** 打开编辑子件弹窗 */
function handleEditItem(row) {
  itemForm.value = {
    id: row.id,
    versionId: selectedVersionId.value,
    parentId: row.parentId,
    itemType: row.itemType || 'MATERIAL',
    materialId: row.materialId,
    materialName: row.materialName || '',
    productId: row.productId,
    quantity: row.quantity,
    wastageRate: row.wastageRate || 0,
    remark: row.remark || ''
  }
  itemDialogVisible.value = true
}

/** 提交子件表单 */
async function handleItemSubmit() {
  await itemFormRef.value.validate()
  submitLoading.value = true
  try {
    if (itemForm.value.id) {
      await updateBomItem(itemForm.value.id, itemForm.value)
      ElMessage.success('BOM子件更新成功')
    } else {
      await addBomItem(itemForm.value)
      ElMessage.success('BOM子件添加成功')
    }
    itemDialogVisible.value = false
    loadBomItems()
  } catch {
    // 由拦截器统一处理
  } finally {
    submitLoading.value = false
  }
}

/** 重置子件表单 */
function resetItemForm() {
  itemFormRef.value?.resetFields()
}

// ==================== 物料选择弹窗 ====================
const materialPickerVisible = ref(false)
function openMaterialPicker() {
  materialPickerVisible.value = true
}
function onMaterialSelected(items) {
  if (!items || items.length === 0) return
  const m = items[0]
  itemForm.value.materialId = m.id
  itemForm.value.materialNo = m.materialNo || ''
  itemForm.value.materialName = m.materialName || m.name || ''
}

/** 删除子件 */
function handleDeleteItem(row) {
  ElMessageBox.confirm('确定删除该BOM子件吗？', '提示', { type: 'warning' })
    .then(async () => {
      try {
        await deleteBomItem(row.id)
        ElMessage.success('删除成功')
        loadBomItems()
      } catch {
        // 由拦截器统一处理
      }
    })
    .catch(() => {})
}

// ==================== 版本对比 ====================
const compareVisible = ref(false)
const compareVersionA = ref(undefined)
const compareVersionB = ref(undefined)
const compareDataA = ref([])
const compareDataB = ref([])

/** 打开版本对比弹窗 */
function handleCompare() {
  if (versionList.value.length < 2) {
    ElMessage.warning('至少需要两个版本才能对比')
    return
  }
  // 默认选中前两个版本
  compareVersionA.value = versionList.value[0]?.id
  compareVersionB.value = versionList.value[1]?.id
  compareDataA.value = []
  compareDataB.value = []
  compareVisible.value = true
  loadCompareData()
}

/** 加载对比数据 */
async function loadCompareData() {
  try {
    const [resA, resB] = await Promise.all([
      compareVersionA.value ? getBomVersionDetail(compareVersionA.value) : Promise.resolve({ data: {} }),
      compareVersionB.value ? getBomVersionDetail(compareVersionB.value) : Promise.resolve({ data: {} })
    ])
    compareDataA.value = flattenItems(resA.data?.items || [])
    compareDataB.value = flattenItems(resB.data?.items || [])
  } catch {
    // 由拦截器统一处理
  }
}

/** 将树形BOM展平为列表，便于对比展示 */
function flattenItems(items, result = []) {
  for (const item of items) {
    result.push(item)
    if (item.children && item.children.length > 0) {
      flattenItems(item.children, result)
    }
  }
  return result
}

/** 监听对比版本变化，自动重新加载数据 */
watch([compareVersionA, compareVersionB], () => {
  if (compareVisible.value) {
    loadCompareData()
  }
})

// ==================== 初始化 ====================
onMounted(() => {
  loadOptions()
})
</script>

<style scoped lang="scss">
.page-container {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.product-card {
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }
}

.version-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;

  &__left {
    display: flex;
    align-items: center;
  }

  &__label {
    font-size: 14px;
    font-weight: 500;
    color: #606266;
    white-space: nowrap;
  }

  &__right {
    display: flex;
    gap: 8px;
  }
}
</style>
