<template>
  <div class="supplier-layout">
    <!-- 左侧类别树 -->
    <div class="category-panel">
      <div class="category-header">
        <span class="category-title">供应商类别</span>
        <el-button link type="primary" size="small" @click="openCategoryMgr">管理</el-button>
      </div>
      <el-tree
        ref="catTreeRef"
        :data="categoryTree"
        :props="{ label: 'categoryName', children: 'children' }"
        node-key="id"
        highlight-current
        :expand-on-click-node="false"
        @node-click="handleCategoryClick"
        style="flex: 1; overflow-y: auto"
      >
        <template #default="{ node, data }">
          <span class="tree-node-label">{{ node.label }}</span>
        </template>
      </el-tree>
      <div class="category-all" :class="{ active: !queryParams.supplierCategoryId }" @click="handleCategoryClick(null)">全部供应商</div>
    </div>

    <!-- 右侧内容 -->
    <div class="page-container">
      <!-- 搜索栏 -->
      <div class="search-bar">
        <el-form :model="queryParams" inline>
          <el-form-item label="供应商名称">
            <el-input v-model="queryParams.supplierName" placeholder="供应商名称" clearable @keyup.enter="handleSearch" />
          </el-form-item>
          <el-form-item label="供应商编号">
            <el-input v-model="queryParams.supplierNo" placeholder="供应商编号" clearable @keyup.enter="handleSearch" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="queryParams.status" placeholder="全部" clearable>
              <el-option label="启用" :value="1" />
              <el-option label="禁用" :value="0" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleSearch">搜索</el-button>
            <el-button @click="handleReset">重置</el-button>
            <el-button @click="advancedDrawer = true">高级搜索</el-button>
            <el-button v-permission="'purchase:supplier:add'" type="primary" plain @click="handleAdd">新增供应商</el-button>
            <el-button type="success" plain @click="importDialogVisible = true">导入</el-button>
            <el-button type="success" @click="handleExport">导出</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 高级搜索抽屉 -->
      <el-drawer v-model="advancedDrawer" title="高级搜索" size="360px" direction="rtl">
        <el-form :model="queryParams" label-width="90px">
          <el-form-item label="联系人">
            <el-input v-model="queryParams.contactName" placeholder="联系人姓名" clearable />
          </el-form-item>
          <el-form-item label="电话">
            <el-input v-model="queryParams.phone" placeholder="联系电话" clearable />
          </el-form-item>
          <el-form-item label="结算方式">
            <el-select v-model="queryParams.settlementType" placeholder="全部" clearable style="width: 100%">
              <el-option v-for="t in settlementTypeOptions" :key="t" :label="t" :value="t" />
            </el-select>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="advancedDrawer = false; handleReset()">重置</el-button>
          <el-button type="primary" @click="advancedDrawer = false; handleSearch()">查询</el-button>
        </template>
      </el-drawer>

      <!-- 数据表格 -->
      <el-table :data="tableData" v-loading="loading" border stripe size="small">
        <el-table-column prop="supplierNo" label="供应商编号" min-width="120" show-overflow-tooltip />
        <el-table-column prop="supplierName" label="供应商名称" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button type="primary" link @click="openDetail(row)">{{ row.supplierName }}</el-button>
          </template>
        </el-table-column>
        <el-table-column label="类别" width="130">
          <template #default="{ row }">
            <span v-if="row.supplierCategoryId">{{ getCategoryName(row.supplierCategoryId) }}</span>
            <span v-else-if="row.supplierType" class="text-muted">{{ row.supplierType }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="contactName" label="联系人" width="100" />
        <el-table-column prop="phone" label="电话" width="130" />
        <el-table-column prop="settlementType" label="结算方式" width="110">
          <template #default="{ row }">{{ row.settlementType || '-' }}</template>
        </el-table-column>
        <el-table-column prop="score" label="评分" width="140" align="center">
          <template #default="{ row }">
            <template v-if="row.score">
              <el-rate :model-value="row.score" disabled :max="5" size="small" style="display: inline-flex" />
            </template>
            <span v-else style="color: #c0c4cc">未评分</span>
          </template>
        </el-table-column>
        <el-table-column prop="processingAbility" label="加工能力" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.processingAbility || '-' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-switch :model-value="row.status === 'active'" size="small"
              @change="(val) => toggleStatus(row, val)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-permission="'purchase:supplier:edit'" type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="warning" link size="small" @click="handleScore(row)">评分</el-button>
            <el-button type="info" link size="small" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50, 100]" :total="total"
          layout="total, sizes, prev, pager, next, jumper" background
          @size-change="loadData" @current-change="loadData" />
      </div>
    </div>
  </div>

  <!-- 供应商详情抽屉 -->
  <el-drawer v-model="detailVisible" :title="detailData?.supplierName || '供应商详情'" size="650px">
    <template v-if="detailData">
      <el-tabs v-model="detailTab">
        <el-tab-pane label="基本信息" name="basic">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="供应商编号">{{ detailData.supplierNo }}</el-descriptions-item>
            <el-descriptions-item label="供应商名称">{{ detailData.supplierName }}</el-descriptions-item>
            <el-descriptions-item label="类别">
              <span v-if="detailData.supplierCategoryId">{{ getCategoryName(detailData.supplierCategoryId) }}</span>
              <span v-else-if="detailData.supplierType" class="text-muted">{{ detailData.supplierType }}</span>
              <span v-else>-</span>
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="detailData.status === 1 ? 'success' : 'danger'" size="small">
                {{ detailData.status === 1 ? '启用' : '禁用' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="联系人">{{ detailData.contactName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="电话">{{ detailData.phone || '-' }}</el-descriptions-item>
            <el-descriptions-item label="地址" :span="2">{{ detailData.address || '-' }}</el-descriptions-item>
            <el-descriptions-item label="结算方式">{{ detailData.settlementType || '-' }}</el-descriptions-item>
            <el-descriptions-item label="评分">
              <el-rate v-if="detailData.score" :model-value="detailData.score" disabled :max="5" size="small" />
              <span v-else style="color: #c0c4cc">未评分</span>
            </el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ detailData.createTime || '-' }}</el-descriptions-item>
            <el-descriptions-item label="备注" :span="2">{{ detailData.remark || '-' }}</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>

        <el-tab-pane label="银行信息" name="bank">
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="开户银行">{{ detailData.bankName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="银行账号">{{ detailData.bankAccount || '-' }}</el-descriptions-item>
            <el-descriptions-item label="税号">{{ detailData.taxNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="发票类型">{{ detailData.invoiceType || '-' }}</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>

        <el-tab-pane label="加工能力" name="ability">
          <div v-if="detailData.processingAbility" style="white-space: pre-wrap; line-height: 1.8; color: #606266">
            {{ detailData.processingAbility }}
          </div>
          <el-empty v-else description="暂无加工能力描述" />

          <el-divider content-position="left">供应物料</el-divider>
          <el-table :data="supplyMaterials" size="small" border stripe>
            <el-table-column prop="materialName" label="物料名称" />
            <el-table-column label="规格">
              <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
            </el-table-column>
            <el-table-column prop="unitPrice" label="单价" width="100" align="right">
              <template #default="{ row }">{{ row.unitPrice ? '¥' + row.unitPrice : '-' }}</template>
            </el-table-column>
            <el-table-column prop="leadTime" label="交货周期(天)" width="110" align="center" />
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="往来记录" name="transactions">
          <el-descriptions :column="3" border size="small" style="margin-bottom: 16px">
            <el-descriptions-item label="采购总额">
              <span style="color: #e6a23c; font-weight: 600">¥{{ txStats.totalPurchase?.toFixed(2) || '0.00' }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="未结金额">
              <span style="color: #f56c6c; font-weight: 600">¥{{ txStats.unpaid?.toFixed(2) || '0.00' }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="订单数">{{ txStats.orderCount || 0 }}</el-descriptions-item>
          </el-descriptions>

          <el-table :data="txList" size="small" border stripe v-loading="txLoading">
            <el-table-column prop="orderNo" label="单据号" min-width="140" show-overflow-tooltip />
            <el-table-column prop="type" label="类型" width="90">
              <template #default="{ row }">
                <el-tag :type="row.type === 'purchase' ? '' : 'warning'" size="small">
                  {{ row.type === 'purchase' ? '采购单' : '外协单' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="totalAmount" label="金额" width="100" align="right">
              <template #default="{ row }">¥{{ Number(row.totalAmount || 0).toFixed(2) }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 'completed' ? 'success' : 'info'">
                  {{ txStatusMap[row.status] || row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="时间" min-width="155" />
          </el-table>
        </el-tab-pane>
      </el-tabs>

      <div style="margin-top: 16px; display: flex; gap: 8px">
        <el-button type="primary" @click="handleEdit(detailData); detailVisible = false">编辑</el-button>
        <el-button type="warning" @click="handleScore(detailData); detailVisible = false">评分</el-button>
      </div>
    </template>
  </el-drawer>

  <!-- 新增/编辑弹窗 -->
  <el-dialog :title="dialogTitle" v-model="dialogVisible" width="700px" destroy-on-close>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-divider content-position="left">基本信息</el-divider>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="供应商名称" prop="supplierName">
            <el-input v-model="form.supplierName" placeholder="请输入供应商名称" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="供应商类别" prop="supplierCategoryId">
            <el-tree-select
              v-model="form.supplierCategoryId"
              :data="categoryTree"
              :props="{ label: 'categoryName', children: 'children', value: 'id' }"
              node-key="id"
              placeholder="请选择类别"
              clearable
              check-strictly
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="联系人" prop="contactName">
            <el-input v-model="form.contactName" placeholder="请输入联系人" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="电话" prop="phone">
            <el-input v-model="form.phone" placeholder="请输入电话" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="地址" prop="address">
        <el-input v-model="form.address" placeholder="请输入地址" />
      </el-form-item>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="结算方式" prop="settlementType">
            <el-select v-model="form.settlementType" placeholder="请选择或输入新结算方式" filterable allow-create
              default-first-option style="width: 100%">
              <el-option v-for="t in settlementTypeOptions" :key="t" :label="t" :value="t" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-divider content-position="left">银行信息</el-divider>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="开户银行" prop="bankName">
            <el-input v-model="form.bankName" placeholder="请输入开户银行" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="银行账号" prop="bankAccount">
            <el-input v-model="form.bankAccount" placeholder="请输入银行账号" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="税号">
            <el-input v-model="form.taxNo" placeholder="请输入税号" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="发票类型">
            <el-select v-model="form.invoiceType" placeholder="请选择" style="width: 100%" clearable>
              <el-option label="增值税专用发票" value="special" />
              <el-option label="增值税普通发票" value="normal" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-divider content-position="left">加工能力</el-divider>
      <el-form-item label="加工能力" prop="processingAbility">
        <el-input v-model="form.processingAbility" type="textarea" :rows="3" placeholder="请输入加工能力描述，如：CNC加工、钣金、焊接等" />
      </el-form-item>
      <el-form-item label="备注" prop="remark">
        <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入备注" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>

  <!-- 评分弹窗 -->
  <el-dialog title="供应商评分" v-model="scoreDialogVisible" width="480px" destroy-on-close>
    <div style="padding: 20px 0;">
      <p style="margin-bottom: 16px; color: #606266; text-align: center">
        供应商：<strong>{{ scoringSupplier?.supplierName }}</strong>
      </p>
      <div style="text-align: center; margin-bottom: 16px">
        <el-rate v-model="scoreValue" :max="5" :texts="['很差', '较差', '一般', '较好', '很好']" show-text size="large" />
      </div>
      <el-divider content-position="left">评分维度（可选）</el-divider>
      <el-form label-width="100px" size="small">
        <el-form-item label="质量">
          <el-rate v-model="scoreDimensions.quality" :max="5" />
        </el-form-item>
        <el-form-item label="交期">
          <el-rate v-model="scoreDimensions.delivery" :max="5" />
        </el-form-item>
        <el-form-item label="价格">
          <el-rate v-model="scoreDimensions.price" :max="5" />
        </el-form-item>
        <el-form-item label="服务">
          <el-rate v-model="scoreDimensions.service" :max="5" />
        </el-form-item>
      </el-form>
    </div>
    <template #footer>
      <el-button @click="scoreDialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="scoreLoading" @click="handleSubmitScore">确定</el-button>
    </template>
  </el-dialog>

  <!-- 导入弹窗(复用数据导入中心的供应商导入接口) -->
  <el-dialog v-model="importDialogVisible" title="导入供应商" width="520px" destroy-on-close>
    <div style="margin-bottom: 12px">
      <el-button type="primary" link @click="handleDownloadTemplate">下载导入模板</el-button>
      <span style="color:#909399;font-size:12px;margin-left:8px">按模板格式填写后上传</span>
    </div>
    <input ref="importFileInputRef" type="file" accept=".xlsx,.xls" style="font-size:13px"
           @change="onImportFileChange" />
    <div v-if="importResult" style="margin-top:12px;font-size:13px">
      <span style="color:#67C23A">成功 {{ importResult.success || 0 }}</span>
      <span v-if="(importResult.failed || 0) > 0" style="color:#F56C6C;margin-left:12px">失败 {{ importResult.failed }}</span>
    </div>
    <div v-if="importResult?.errors?.length" style="margin-top:8px;max-height:200px;overflow:auto;font-size:12px;color:#F56C6C;background:#fef0f0;padding:8px;border-radius:4px">
      <div v-for="(err, idx) in importResult.errors" :key="idx">{{ err }}</div>
    </div>
    <template #footer>
      <el-button @click="importDialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="importLoading" @click="handleImport">开始导入</el-button>
    </template>
  </el-dialog>


  <!-- 类别管理弹窗 -->
  <el-dialog title="供应商类别管理" v-model="categoryMgrVisible" width="760px" destroy-on-close>
    <div class="category-mgr">
      <div class="category-mgr-left">
        <div class="category-mgr-header">
          <span>类别树</span>
          <el-button type="primary" size="small" @click="openCategoryForm(null)">+ 新增根节点</el-button>
        </div>
        <el-tree
          :data="categoryTree"
          :props="{ label: 'categoryName', children: 'children' }"
          node-key="id"
          highlight-current
          default-expand-all
          :expand-on-click-node="false"
          @node-click="handleCatMgrNodeClick"
        >
          <template #default="{ node, data }">
            <span class="cat-node">
              <span>{{ node.label }}</span>
              <span class="cat-node-actions">
                <el-button link size="small" @click.stop="openCategoryForm(data)">子级</el-button>
                <el-button link size="small" @click.stop="editCategoryNode(data)">编辑</el-button>
                <el-button link size="small" type="danger" @click.stop="deleteCategoryNode(data)">删除</el-button>
              </span>
            </span>
          </template>
        </el-tree>
      </div>
      <div class="category-mgr-right">
        <el-form v-if="catForm.visible" ref="catFormRef" :model="catForm" :rules="catRules" label-width="80px">
          <el-form-item label="上级类别">
            <el-tree-select
              v-model="catForm.parentId"
              :data="categoryTree"
              :props="{ label: 'categoryName', children: 'children', value: 'id' }"
              node-key="id"
              placeholder="无（根节点）"
              clearable
              check-strictly
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="类别名称" prop="categoryName">
            <el-input v-model="catForm.categoryName" placeholder="请输入类别名称" />
          </el-form-item>
          <el-form-item label="类别编码">
            <el-input v-model="catForm.categoryCode" placeholder="可选，唯一编码" />
          </el-form-item>
          <el-form-item label="排序">
            <el-input-number v-model="catForm.sortOrder" :min="0" controls-position="right" style="width: 100%" />
          </el-form-item>
          <div style="text-align: right; margin-top: 16px">
            <el-button @click="catForm.visible = false">取消</el-button>
            <el-button type="primary" :loading="catForm.loading" @click="submitCategoryForm">保存</el-button>
          </div>
        </el-form>
        <el-empty v-else description="点击左侧按钮新增或编辑类别" :image-size="80" />
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getSupplierPage, getSupplierDetail, createSupplier, updateSupplier, updateSupplierScore,
  getSupplierCategoryTree, createSupplierCategory, updateSupplierCategory, deleteSupplierCategory,
  exportSuppliers
} from '@/api/master'
import { downloadTemplate, importData } from '@/api/data-import'
import { getSettlementTypes, saveSettlementTypes } from '@/api/system'
import { getPurchaseOrderPage } from '@/api/purchase'
import { getOutsourceOrderPage } from '@/api/outsource'

const txStatusMap = { draft: '草稿', pending: '待确认', pending_approval: '待审批', confirmed: '已确认', material_sent: '已发料', processing: '加工中', partial_received: '部分收货', partial_receipt: '部分收货', received: '已收货', fully_receipt: '已全部收货', closed: '已关闭', settled: '已结算', completed: '已完成', cancelled: '已取消', rejected: '已驳回' }

// 结算方式
const settlementTypeOptions = ref(['现结', '月结30天', '月结60天', '月结90天'])
async function loadSettlementTypes() {
  try {
    const res = await getSettlementTypes()
    if (res.data?.configValue) settlementTypeOptions.value = JSON.parse(res.data.configValue)
  } catch {}
}
async function addSettlementType(val) {
  if (val && !settlementTypeOptions.value.includes(val)) {
    settlementTypeOptions.value.push(val)
    try { await saveSettlementTypes(settlementTypeOptions.value) } catch {}
  }
}

// ===== 类别树 =====
const categoryTree = ref([])
const catTreeRef = ref(null)
const categoryMgrVisible = ref(false)

// 展平类别为 Map（id -> name）
const categoryMap = computed(() => {
  const map = {}
  function flatten(list) {
    list.forEach(c => { map[c.id] = c.categoryName; if (c.children?.length) flatten(c.children) })
  }
  flatten(categoryTree.value)
  return map
})
function getCategoryName(id) { return categoryMap.value[id] || String(id) }

async function loadCategoryTree() {
  try {
    const res = await getSupplierCategoryTree()
    categoryTree.value = res.data || []
  } catch {}
}
function handleCategoryClick(data) {
  queryParams.supplierCategoryId = data ? data.id : undefined
  queryParams.pageNum = 1
  loadData()
}
function openCategoryMgr() { categoryMgrVisible.value = true }

// 类别表单
const catFormRef = ref(null)
const catForm = reactive({ visible: false, id: null, parentId: null, categoryName: '', categoryCode: '', sortOrder: 0, loading: false })
const catRules = { categoryName: [{ required: true, message: '请输入类别名称', trigger: 'blur' }] }

function openCategoryForm(parentData) {
  catForm.id = null
  catForm.parentId = parentData ? parentData.id : null
  catForm.categoryName = ''
  catForm.categoryCode = ''
  catForm.sortOrder = 0
  catForm.visible = true
}
function editCategoryNode(data) {
  catForm.id = data.id
  catForm.parentId = data.parentId || null
  catForm.categoryName = data.categoryName
  catForm.categoryCode = data.categoryCode || ''
  catForm.sortOrder = data.sortOrder || 0
  catForm.visible = true
}
async function submitCategoryForm() {
  await catFormRef.value.validate()
  catForm.loading = true
  try {
    const payload = { parentId: catForm.parentId || 0, categoryName: catForm.categoryName, categoryCode: catForm.categoryCode, sortOrder: catForm.sortOrder }
    if (catForm.id) {
      await updateSupplierCategory(catForm.id, payload)
    } else {
      await createSupplierCategory(payload)
    }
    ElMessage.success('保存成功')
    catForm.visible = false
    await loadCategoryTree()
  } finally { catForm.loading = false }
}
function handleCatMgrNodeClick(data) { editCategoryNode(data) }
async function deleteCategoryNode(data) {
  await ElMessageBox.confirm(`确认删除类别「${data.categoryName}」？`, '提示', { type: 'warning' })
  try {
    await deleteSupplierCategory(data.id)
    ElMessage.success('删除成功')
    await loadCategoryTree()
  } catch {}
}

// ===== 供应商列表 =====
const advancedDrawer = ref(false)
const queryParams = reactive({ supplierName: '', supplierNo: '', supplierCategoryId: undefined, status: undefined, contactName: '', phone: '', settlementType: undefined, pageNum: 1, pageSize: 10 })
const loading = ref(false); const tableData = ref([]); const total = ref(0)

async function loadData() {
  loading.value = true
  try {
    const res = await getSupplierPage(queryParams)
    tableData.value = res.data?.records || []; total.value = res.data?.total || 0
  } catch {} finally { loading.value = false }
}
function handleSearch() { queryParams.pageNum = 1; loadData() }
function handleReset() {
  queryParams.supplierName = ''; queryParams.supplierNo = ''; queryParams.supplierCategoryId = undefined; queryParams.status = undefined
  queryParams.contactName = ''; queryParams.phone = ''; queryParams.settlementType = undefined
  queryParams.pageNum = 1; loadData()
}

// 状态切换
async function toggleStatus(row, val) {
  const newStatus = val ? 'active' : 'disabled'
  const label = val ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(`确认${label}供应商「${row.supplierName}」？`, '提示', { type: 'warning' })
    await updateSupplier(row.id, { ...row, status: newStatus })
    ElMessage.success(`${label}成功`); loadData()
  } catch {}
}

// 详情抽屉
const detailVisible = ref(false); const detailData = ref(null); const detailTab = ref('basic')
const supplyMaterials = ref([]); const txList = ref([]); const txLoading = ref(false)
const txStats = reactive({ totalPurchase: 0, unpaid: 0, orderCount: 0 })

async function openDetail(row) {
  detailTab.value = 'basic'
  try {
    const res = await getSupplierDetail(row.id)
    detailData.value = res.data || row
  } catch { detailData.value = row }
  supplyMaterials.value = detailData.value?.supplyMaterials || []
  detailVisible.value = true
  loadTransactions(row.id)
}
async function loadTransactions(supplierId) {
  txLoading.value = true
  try {
    const [poRes, osRes] = await Promise.all([
      getPurchaseOrderPage({ supplierId, pageNum: 1, pageSize: 20 }).catch(() => ({ data: { records: [] } })),
      getOutsourceOrderPage({ supplierId, pageNum: 1, pageSize: 20 }).catch(() => ({ data: { records: [] } }))
    ])
    const poList = (poRes.data?.records || []).map(r => ({ ...r, type: 'purchase', orderNo: r.poNo || r.orderNo }))
    const osList = (osRes.data?.records || []).map(r => ({ ...r, type: 'outsource' }))
    txList.value = [...poList, ...osList].sort((a, b) => (b.createTime || '').localeCompare(a.createTime || ''))
    txStats.orderCount = txList.value.length
    txStats.totalPurchase = txList.value.reduce((s, r) => s + Number(r.totalAmount || 0), 0)
    txStats.unpaid = txList.value.filter(r => r.status !== 'completed' && r.status !== 'cancelled')
      .reduce((s, r) => s + Number(r.totalAmount || 0), 0)
  } catch {} finally { txLoading.value = false }
}

// 编辑弹窗
const dialogVisible = ref(false); const dialogTitle = ref('新增供应商')
const formRef = ref(null); const submitLoading = ref(false); const editingId = ref(null)

const getDefaultForm = () => ({
  supplierName: '', supplierCategoryId: null, contactName: '', phone: '', address: '',
  settlementType: undefined, bankName: '', bankAccount: '', taxNo: '', invoiceType: '',
  processingAbility: '', remark: ''
})
const form = reactive(getDefaultForm())
const rules = {
  supplierName: [{ required: true, message: '请输入供应商名称', trigger: 'blur' }]
}

function resetForm() { const d = getDefaultForm(); Object.keys(d).forEach(k => { form[k] = d[k] }) }
function handleAdd() { resetForm(); editingId.value = null; dialogTitle.value = '新增供应商'; dialogVisible.value = true }
function handleEdit(row) {
  resetForm(); editingId.value = row.id; dialogTitle.value = '编辑供应商'
  Object.keys(form).forEach(key => { if (row[key] !== undefined) form[key] = row[key] })
  dialogVisible.value = true
}
async function handleSubmit() {
  await formRef.value.validate()
  submitLoading.value = true
  try {
    if (editingId.value) {
      await updateSupplier(editingId.value, { ...form }); ElMessage.success('更新成功')
    } else {
      await createSupplier({ ...form }); ElMessage.success('新增成功')
    }
    if (form.settlementType) addSettlementType(form.settlementType)
    dialogVisible.value = false; loadData()
  } finally { submitLoading.value = false }
}

// 评分
const scoreDialogVisible = ref(false); const scoringSupplier = ref(null)
const scoreValue = ref(0); const scoreLoading = ref(false)
const scoreDimensions = reactive({ quality: 0, delivery: 0, price: 0, service: 0 })
function handleScore(row) {
  scoringSupplier.value = row; scoreValue.value = row.score || 0
  scoreDimensions.quality = 0; scoreDimensions.delivery = 0; scoreDimensions.price = 0; scoreDimensions.service = 0
  scoreDialogVisible.value = true
}
async function handleSubmitScore() {
  if (!scoreValue.value) { ElMessage.warning('请选择评分'); return }
  scoreLoading.value = true
  try {
    await updateSupplierScore(scoringSupplier.value.id, scoreValue.value)
    ElMessage.success('评分成功'); scoreDialogVisible.value = false; loadData()
  } finally { scoreLoading.value = false }
}

// ===== 导入(复用数据导入中心的 supplier 接口,弹窗内完成) =====
const importDialogVisible = ref(false)
const importLoading = ref(false)
const importFile = ref(null)
const importFileInputRef = ref(null)
const importResult = ref(null)

function onImportFileChange(e) {
  importFile.value = e.target.files?.[0] || null
  importResult.value = null
}
function downloadBlobFile(data, filename) {
  const blob = data instanceof Blob ? data : new Blob([data])
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url; a.download = filename; a.click()
  URL.revokeObjectURL(url)
}
async function handleDownloadTemplate() {
  try {
    const res = await downloadTemplate('supplier')
    downloadBlobFile(res, '供应商导入模板.xlsx')
  } catch { ElMessage.error('下载模板失败') }
}
async function handleImport() {
  if (!importFile.value) { ElMessage.warning('请先选择文件'); return }
  importLoading.value = true
  importResult.value = null
  try {
    const resp = await importData('supplier', importFile.value)
    importResult.value = resp.data || resp
    if (!importResult.value?.failed) ElMessage.success(`导入成功 ${importResult.value?.success || 0} 条`)
    else ElMessage.warning(`成功 ${importResult.value.success || 0} 条,失败 ${importResult.value.failed} 条`)
    loadData()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '导入失败')
  } finally { importLoading.value = false }
}

// ===== 导出 =====
async function handleExport() {
  try {
    const res = await exportSuppliers()
    downloadBlobFile(res, '供应商列表.xlsx')
    ElMessage.success('导出成功')
  } catch { ElMessage.error('导出失败') }
}

onMounted(() => { loadCategoryTree(); loadData(); loadSettlementTypes() })
</script>

<style scoped lang="scss">
.supplier-layout {
  display: flex;
  height: 100%;
  gap: 0;
}
.category-panel {
  width: 200px;
  flex-shrink: 0;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  background: #fafafa;
  padding: 8px 0;
}
.category-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 12px 8px;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}
.tree-node-label {
  font-size: 13px;
}
.category-all {
  padding: 6px 20px;
  font-size: 13px;
  color: #606266;
  cursor: pointer;
  border-top: 1px solid #e4e7ed;
  margin-top: 4px;
  &:hover { background: #ecf5ff; color: #409eff; }
  &.active { color: #409eff; font-weight: 600; background: #ecf5ff; }
}
.page-container {
  flex: 1;
  padding: 16px;
  overflow: auto;
  min-width: 0;
}
.search-bar { margin-bottom: 12px; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; }
.text-muted { color: #c0c4cc; }

.category-mgr {
  display: flex;
  gap: 16px;
  min-height: 360px;
}
.category-mgr-left {
  width: 280px;
  flex-shrink: 0;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  overflow: auto;
}
.category-mgr-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-bottom: 1px solid #e4e7ed;
  font-size: 13px;
  font-weight: 600;
}
.category-mgr-right {
  flex: 1;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 16px;
}
.cat-node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}
.cat-node-actions {
  display: none;
  gap: 2px;
}
.el-tree-node__content:hover .cat-node-actions {
  display: flex;
}
</style>
