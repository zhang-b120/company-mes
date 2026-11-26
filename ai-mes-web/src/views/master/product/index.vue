<template>
  <div class="page-container">
    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-form :model="queryParams" inline>
        <el-form-item label="产品名称">
          <el-input v-model="queryParams.productName" placeholder="产品名称" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="产品编号">
          <el-input v-model="queryParams.productNo" placeholder="产品编号" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="规格型号">
          <el-input v-model="queryParams.spec" placeholder="规格型号" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="queryParams.category" placeholder="全部" clearable>
            <el-option label="成品" value="finished" />
            <el-option label="半成品" value="semi_finished" />
          </el-select>
        </el-form-item>
        <el-form-item label="生产模式">
          <el-select v-model="queryParams.productionMode" placeholder="全部" clearable>
            <el-option label="按库存生产(MTS)" value="MTS" />
            <el-option label="按订单生产(MTO)" value="MTO" />
            <el-option label="混合模式" value="MIXED" />
          </el-select>
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
        </el-form-item>
      </el-form>
    </div>

    <!-- 工具栏 -->
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px">
      <div style="display: flex; gap: 8px">
        <el-button type="primary" @click="handleAdd">新增产品</el-button>
        <el-button type="success" :icon="Download" @click="handleExport">导出</el-button>
        <el-button type="warning" :icon="Upload" @click="importDialogVisible = true">导入</el-button>
        <el-button style="background: #7c3aed; border-color: #7c3aed; color: #fff" @click="aiFilingVisible = true">
          <el-icon style="margin-right: 4px"><MagicStick /></el-icon>AI建档
        </el-button>
      </div>
      <div style="display: flex; align-items: center; gap: 8px">
        <span style="color: #909399; font-size: 13px">共 {{ total }} 个产品</span>
        <el-radio-group v-model="viewMode" size="small">
          <el-radio-button value="table">
            <el-icon><List /></el-icon>
          </el-radio-button>
          <el-radio-button value="card">
            <el-icon><Grid /></el-icon>
          </el-radio-button>
        </el-radio-group>
      </div>
    </div>

    <!-- 表格视图 -->
    <template v-if="viewMode === 'table'">
      <el-table :data="tableData" v-loading="loading" border stripe size="small">
        <el-table-column prop="productNo" label="产品编号" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button type="primary" link @click="openDetail(row)">{{ row.productNo }}</el-button>
          </template>
        </el-table-column>
        <el-table-column label="图片" width="70" align="center">
          <template #default="{ row }">
            <el-image v-if="row.imageUrl" :src="row.imageUrl" :preview-src-list="[row.imageUrl]"
              fit="cover" style="width: 40px; height: 40px; border-radius: 4px" />
            <div v-else style="width: 40px; height: 40px; background: #f5f7fa; border-radius: 4px; display: flex; align-items: center; justify-content: center; margin: 0 auto">
              <el-icon :size="18" color="#c0c4cc"><Picture /></el-icon>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="productName" label="产品名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="规格" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="70" align="center" />
        <el-table-column prop="category" label="分类" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.category === 'finished' ? 'success' : 'warning'" size="small">
              {{ row.category === 'finished' ? '成品' : row.category === 'semi_finished' ? '半成品' : row.category }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="productionMode" label="生产模式" width="100" align="center">
          <template #default="{ row }">{{ productionModeMap[row.productionMode] || row.productionMode }}</template>
        </el-table-column>
        <el-table-column prop="standardCost" label="标准成本(含税)" width="100" align="right">
          <template #default="{ row }">
            <span v-if="row.standardCost">¥{{ Number(row.standardCost).toFixed(2) }}</span>
            <span v-else style="color: #c0c4cc">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="hasBom" label="BOM" width="60" align="center">
          <template #default="{ row }">
            <el-icon :color="row.hasBom ? '#67c23a' : '#c0c4cc'" :size="18">
              <CircleCheckFilled v-if="row.hasBom" /><CircleCloseFilled v-else />
            </el-icon>
          </template>
        </el-table-column>
        <el-table-column prop="hasProcess" label="工艺" width="60" align="center">
          <template #default="{ row }">
            <el-icon :color="row.hasProcess ? '#67c23a' : '#c0c4cc'" :size="18">
              <CircleCheckFilled v-if="row.hasProcess" /><CircleCloseFilled v-else />
            </el-icon>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="70" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="info" link size="small" @click="handleCopy(row)">复制</el-button>
            <el-button type="success" link size="small" @click="handleBom(row)">BOM</el-button>
            <el-button type="warning" link size="small" @click="handleProcess(row)">工艺</el-button>
            <el-dropdown trigger="click" @command="(cmd) => handleMore(cmd, row)" style="margin-left: 4px">
              <el-button type="info" link size="small">更多</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="toggleStatus">{{ row.status === 1 ? '禁用' : '启用' }}</el-dropdown-item>
                  <el-dropdown-item command="delete" divided style="color: #f56c6c">删除</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <!-- 卡片视图 -->
    <template v-else>
      <div v-loading="loading" class="card-grid">
        <el-card v-for="item in tableData" :key="item.id" shadow="hover" class="product-card"
          @click="openDetail(item)">
          <div class="card-image">
            <el-image v-if="item.imageUrl" :src="item.imageUrl" fit="cover" style="width: 100%; height: 140px" />
            <div v-else class="card-image-placeholder">
              <el-icon :size="40" color="#c0c4cc"><Picture /></el-icon>
            </div>
          </div>
          <div class="card-body">
            <div class="card-title">{{ item.productName }}</div>
            <div class="card-info">
              <span>{{ item.productNo }}</span>
              <el-tag :type="item.category === 'finished' ? 'success' : 'warning'" size="small">
                {{ item.category === 'finished' ? '成品' : '半成品' }}
              </el-tag>
            </div>
            <div class="card-spec">{{ $fmtSpec(item.spec) }} | {{ item.unit }}</div>
            <div class="card-footer">
              <span v-if="item.standardCost" style="color: #e6a23c; font-weight: 500">¥{{ Number(item.standardCost).toFixed(2) }}</span>
              <span v-else style="color: #c0c4cc">未定价</span>
              <div>
                <el-icon v-if="item.hasBom" color="#67c23a" :size="16" title="已有BOM"><CircleCheckFilled /></el-icon>
                <el-icon v-if="item.hasProcess" color="#409eff" :size="16" title="已有工艺" style="margin-left: 4px"><CircleCheckFilled /></el-icon>
              </div>
            </div>
          </div>
          <div class="card-actions" @click.stop>
            <el-button type="primary" link size="small" @click="handleEdit(item)">编辑</el-button>
            <el-button type="info" link size="small" @click="handleCopy(item)">复制</el-button>
            <el-button type="success" link size="small" @click="handleBom(item)">BOM</el-button>
          </div>
        </el-card>
      </div>
    </template>

    <!-- 分页 -->
    <div class="pagination-wrapper">
      <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
        :page-sizes="[10, 20, 50, 100]" :total="total"
        layout="total, sizes, prev, pager, next, jumper" background
        @size-change="loadData" @current-change="loadData" />
    </div>

    <!-- 产品详情抽屉 -->
    <el-drawer v-model="detailVisible" :title="detailData?.productName || '产品详情'" size="600px">
      <template v-if="detailData">
        <div style="text-align: center; margin-bottom: 16px" v-if="detailData.imageUrl">
          <el-image :src="detailData.imageUrl" :preview-src-list="[detailData.imageUrl]"
            fit="contain" style="max-height: 200px; border-radius: 8px" />
        </div>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="产品编号">{{ detailData.productNo }}</el-descriptions-item>
          <el-descriptions-item label="产品名称">{{ detailData.productName }}</el-descriptions-item>
          <el-descriptions-item label="规格">{{ $fmtSpec(detailData.spec) }}</el-descriptions-item>
          <el-descriptions-item label="单位">{{ detailData.unit }}</el-descriptions-item>
          <el-descriptions-item label="分类">
            <el-tag :type="detailData.category === 'finished' ? 'success' : 'warning'" size="small">
              {{ detailData.category === 'finished' ? '成品' : '半成品' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="生产模式">{{ productionModeMap[detailData.productionMode] || '-' }}</el-descriptions-item>
          <el-descriptions-item label="标准成本(含税)">
            {{ detailData.standardCost ? '¥' + Number(detailData.standardCost).toFixed(2) : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="detailData.status === 1 ? 'success' : 'danger'" size="small">
              {{ detailData.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="目标库存">{{ detailData.targetStock ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="再订货点">{{ detailData.reorderPoint ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="再订货量">{{ detailData.reorderQty ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="BOM状态">
            <el-tag :type="detailData.hasBom ? 'success' : 'info'" size="small">
              {{ detailData.hasBom ? '已配置' : '未配置' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="工艺路线">
            <el-tag :type="detailData.hasProcess ? 'success' : 'info'" size="small">
              {{ detailData.hasProcess ? '已配置' : '未配置' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detailData.createTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detailData.remark || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div style="margin-top: 16px; display: flex; gap: 8px">
          <el-button type="primary" @click="handleEdit(detailData); detailVisible = false">编辑</el-button>
          <el-button type="info" @click="handleCopy(detailData); detailVisible = false">复制</el-button>
          <el-button type="success" @click="handleBom(detailData); detailVisible = false">管理BOM</el-button>
          <el-button type="warning" @click="handleProcess(detailData); detailVisible = false">管理工艺</el-button>
        </div>
      </template>
    </el-drawer>

    <!-- 新增/编辑弹窗 -->
    <el-dialog :title="dialogTitle" v-model="dialogVisible" width="650px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="16">
            <el-form-item label="产品名称" prop="productName">
              <el-input v-model="form.productName" placeholder="请输入产品名称" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="单位" prop="unit">
              <el-input v-model="form.unit" placeholder="件/台/套" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="规格" prop="spec">
              <el-input v-model="form.spec" placeholder="请输入规格型号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="分类" prop="category">
              <el-select v-model="form.category" placeholder="请选择分类" style="width: 100%">
                <el-option label="成品" value="finished" />
                <el-option label="半成品" value="semi_finished" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="生产模式" prop="productionMode">
              <el-select v-model="form.productionMode" placeholder="请选择" style="width: 100%">
                <el-option label="按库存生产(MTS)" value="MTS" />
                <el-option label="按订单生产(MTO)" value="MTO" />
                <el-option label="混合模式(MIXED)" value="MIXED" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="标准成本(含税)" prop="standardCost">
              <el-input-number v-model="form.standardCost" :min="0" :precision="2" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 产品图片 -->
        <el-form-item label="产品图片">
          <el-upload
            :action="uploadUrl"
            :headers="uploadHeaders"
            :show-file-list="false"
            :on-success="onImageUploadSuccess"
            :before-upload="beforeImageUpload"
            accept="image/*"
          >
            <div v-if="form.imageUrl" style="position: relative; width: 120px; height: 120px">
              <el-image :src="form.imageUrl" fit="cover"
                :preview-src-list="[form.imageUrl]" preview-teleported :hide-on-click-modal="true"
                style="width: 120px; height: 120px; border-radius: 8px; cursor: pointer" @click.stop />
              <el-button type="danger" :icon="Delete" circle size="small"
                style="position: absolute; top: -8px; right: -8px" @click.stop="form.imageUrl = ''" />
            </div>
            <div v-else class="upload-placeholder">
              <el-icon :size="28"><Plus /></el-icon>
              <div style="font-size: 12px; color: #909399; margin-top: 4px">上传图片</div>
            </div>
          </el-upload>
        </el-form-item>

        <el-divider content-position="left">MRP参数</el-divider>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="生产提前期">
              <el-input-number v-model="form.productionLeadTime" :min="0" :precision="0" controls-position="right" style="width: 100%" />
              <div class="form-tip">单位：天</div>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="最小生产量">
              <el-input-number v-model="form.minProductionQty" :min="0" :precision="0" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="批量倍数">
              <el-input-number v-model="form.productionQtyMultiple" :min="0" :precision="0" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">库存策略 (MTS模式)</el-divider>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="目标库存">
              <el-input-number v-model="form.targetStock" :min="0" :precision="0" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="再订货点">
              <el-input-number v-model="form.reorderPoint" :min="0" :precision="0" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="再订货量">
              <el-input-number v-model="form.reorderQty" :min="0" :precision="0" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 导入弹窗 -->
    <el-dialog v-model="importDialogVisible" title="导入产品" width="500px">
      <el-alert type="info" :closable="false" style="margin-bottom: 12px">
        请先下载模板，按模板格式填写后上传。支持 xlsx/xls 格式。
      </el-alert>
      <div style="margin-bottom: 16px;">
        <el-button type="primary" link @click="handleDownloadTemplate">下载导入模板</el-button>
      </div>
      <el-upload ref="uploadRef" :auto-upload="false" :limit="1" accept=".xlsx,.xls"
        :on-change="handleFileChange" drag>
        <el-icon :size="40" color="#c0c4cc"><Upload /></el-icon>
        <div style="margin-top: 8px; color: #909399">将文件拖到此处，或点击上传</div>
      </el-upload>
      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="importLoading" @click="handleImport">确认导入</el-button>
      </template>
    </el-dialog>

    <!-- AI建档对话框 -->
    <el-dialog v-model="aiFilingVisible" title="AI智能建档" width="700px" destroy-on-close>
      <el-alert type="info" :closable="false" style="margin-bottom: 16px">
        输入产品描述信息，AI将自动提取产品属性并生成建档数据。支持自然语言描述、规格书粘贴等。
      </el-alert>
      <el-input v-model="aiDescription" type="textarea" :rows="5"
        placeholder="请输入产品描述，例如：&#10;这是一款不锈钢304法兰盘，DN50规格，适用于化工管道连接，按订单生产模式，单位为件。" />
      <div style="margin-top: 12px; text-align: right">
        <el-button type="primary" :loading="aiFilingLoading" :disabled="!aiDescription.trim()"
          style="background: #7c3aed; border-color: #7c3aed" @click="handleAiFiling">
          <el-icon style="margin-right: 4px"><MagicStick /></el-icon>AI生成
        </el-button>
      </div>
      <template v-if="aiFilingResult">
        <el-divider content-position="left">生成结果</el-divider>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="产品名称">{{ aiFilingResult.name || '-' }}</el-descriptions-item>
          <el-descriptions-item label="规格">{{ $fmtSpec(aiFilingResult.spec) }}</el-descriptions-item>
          <el-descriptions-item label="分类">{{ aiFilingResult.category || '-' }}</el-descriptions-item>
          <el-descriptions-item label="单位">{{ aiFilingResult.unit || '-' }}</el-descriptions-item>
          <el-descriptions-item label="生产模式">{{ productionModeMap[aiFilingResult.productionMode] || aiFilingResult.productionMode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="标准成本(含税)">{{ aiFilingResult.standardCost || '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ aiFilingResult.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
      </template>
      <template #footer>
        <el-button @click="aiFilingVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!aiFilingResult" style="background: #7c3aed; border-color: #7c3aed"
          @click="handleAiFilingConfirm">确认建档</el-button>
      </template>
    </el-dialog>

    <!-- ========== BOM弹窗 ========== -->
    <BomDialog
      v-model="bomDialogVisible"
      :product-id="bomDialogProduct?.id"
      :product-name="bomDialogProduct?.name || bomDialogProduct?.productName"
    />
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CircleCheckFilled, CircleCloseFilled, MagicStick, Download, Upload, Picture, Delete, Plus, List, Grid } from '@element-plus/icons-vue'
import { getProductPage, getProductDetail, createProduct, updateProduct, exportProducts, importProducts, downloadProductTemplate } from '@/api/master'
import { aiProductFiling } from '@/api/ai'
import { useAiChatStore } from '@/store/ai-chat'
import BomDialog from '@/views/master/material/BomDialog.vue'

const router = useRouter()
const aiChatStore = useAiChatStore()

// AI填入表单
watch(() => aiChatStore.pendingFillData, (data) => {
  if (data?.module === 'product' && data.records?.length) {
    dialogTitle.value = '新增产品'; editingId.value = null
    const defaults = getDefaultForm(); Object.keys(defaults).forEach(k => { form[k] = defaults[k] })
    Object.keys(form).forEach(k => { if (data.records[0][k] !== undefined) form[k] = data.records[0][k] })
    dialogVisible.value = true
    aiChatStore.clearFillData()
    ElMessage.success('AI已填入产品表单，请确认后提交')
  }
})
const uploadUrl = (import.meta.env.VITE_API_BASE || '/api') + '/common/upload'
const uploadHeaders = computed(() => ({ Authorization: 'Bearer ' + (localStorage?.getItem('token') || '') }))

const productionModeMap = { MTS: '按库存生产', MTO: '按订单生产', MIXED: '混合模式' }
const viewMode = ref('table')

// 查询
const queryParams = reactive({ productName: '', productNo: '', spec: '', category: undefined, productionMode: undefined, status: undefined, pageNum: 1, pageSize: 10 })
const loading = ref(false); const tableData = ref([]); const total = ref(0)

async function loadData() {
  loading.value = true
  try {
    const res = await getProductPage(queryParams)
    tableData.value = res.data?.records || []; total.value = res.data?.total || 0
  } catch {} finally { loading.value = false }
}

function handleSearch() { queryParams.pageNum = 1; loadData() }
function handleReset() {
  queryParams.productName = ''; queryParams.productNo = ''; queryParams.spec = ''; queryParams.category = undefined
  queryParams.productionMode = undefined; queryParams.status = undefined
  queryParams.pageNum = 1; loadData()
}

// 详情抽屉
const detailVisible = ref(false); const detailData = ref(null)
async function openDetail(row) {
  try {
    const res = await getProductDetail(row.id)
    detailData.value = res.data || row
  } catch { detailData.value = row }
  detailVisible.value = true
}

// 弹窗
const dialogVisible = ref(false); const dialogTitle = ref('新增产品')
const formRef = ref(null); const submitLoading = ref(false); const editingId = ref(null)

const getDefaultForm = () => ({
  productName: '', spec: '', unit: '', category: '', productionMode: undefined,
  productionLeadTime: undefined, minProductionQty: undefined, productionQtyMultiple: undefined,
  targetStock: undefined, reorderPoint: undefined, reorderQty: undefined,
  standardCost: undefined, imageUrl: '', remark: ''
})
const form = reactive(getDefaultForm())
const rules = {
  productName: [{ required: true, message: '请输入产品名称', trigger: 'blur' }],
  unit: [{ required: true, message: '请输入单位', trigger: 'blur' }],
  category: [{ required: true, message: '请选择分类', trigger: 'change' }],
  productionMode: [{ required: true, message: '请选择生产模式', trigger: 'change' }]
}

function resetForm() {
  const defaults = getDefaultForm()
  Object.keys(defaults).forEach(key => { form[key] = defaults[key] })
}

function handleAdd() {
  resetForm(); editingId.value = null
  dialogTitle.value = '新增产品'; dialogVisible.value = true
}

function handleEdit(row) {
  resetForm(); editingId.value = row.id; dialogTitle.value = '编辑产品'
  Object.keys(form).forEach(key => { if (row[key] !== undefined) form[key] = row[key] })
  dialogVisible.value = true
}

// 复制产品
function handleCopy(row) {
  resetForm(); editingId.value = null; dialogTitle.value = '复制产品（新建）'
  Object.keys(form).forEach(key => { if (row[key] !== undefined) form[key] = row[key] })
  form.productName = (row.productName || '') + ' (副本)'
  dialogVisible.value = true
  ElMessage.info('已复制产品信息，修改后保存为新产品')
}

async function handleSubmit() {
  await formRef.value.validate()
  submitLoading.value = true
  try {
    if (editingId.value) {
      await updateProduct(editingId.value, { ...form })
      ElMessage.success('更新成功')
    } else {
      await createProduct({ ...form })
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false; loadData()
  } finally { submitLoading.value = false }
}

// 更多操作
async function handleMore(cmd, row) {
  if (cmd === 'toggleStatus') {
    const newStatus = row.status === 1 ? 0 : 1
    const label = newStatus === 1 ? '启用' : '禁用'
    await ElMessageBox.confirm(`确认${label}产品「${row.productName}」？`, '提示', { type: 'warning' })
    try {
      await updateProduct(row.id, { ...row, status: newStatus })
      ElMessage.success(`${label}成功`); loadData()
    } catch {}
  } else if (cmd === 'delete') {
    await ElMessageBox.confirm(`确认删除产品「${row.productName}」？删除后不可恢复。`, '危险操作', { type: 'error', confirmButtonText: '确认删除' })
    try {
      await updateProduct(row.id, { ...row, deleted: 1 })
      ElMessage.success('删除成功'); loadData()
    } catch {}
  }
}

// 图片上传
function beforeImageUpload(file) {
  const isImage = file.type.startsWith('image/')
  const isLt5M = file.size / 1024 / 1024 < 5
  if (!isImage) { ElMessage.error('只能上传图片文件'); return false }
  if (!isLt5M) { ElMessage.error('图片大小不能超过5MB'); return false }
  return true
}
function onImageUploadSuccess(res) {
  if (res.code === 200 || res.code === 0) {
    form.imageUrl = res.data?.url || res.data || res.url || ''
    ElMessage.success('图片上传成功')
  } else { ElMessage.error(res.msg || '上传失败') }
}

// ==================== BOM弹窗 ====================
const bomDialogVisible = ref(false)
const bomDialogProduct = ref(null)
function handleBom(row) {
  bomDialogProduct.value = row
  bomDialogVisible.value = true
}
function handleProcess(row) { router.push({ path: '/master/process-route', query: { productId: row.id } }) }

// 导入导出
const importDialogVisible = ref(false); const importLoading = ref(false)
const importFile = ref(null); const uploadRef = ref(null)

function handleFileChange(file) { importFile.value = file.raw }
function downloadBlob(data, filename) {
  const blob = new Blob([data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a'); link.href = url; link.download = filename; link.click()
  window.URL.revokeObjectURL(url)
}
async function handleExport() {
  try { const res = await exportProducts(); downloadBlob(res, '产品列表.xlsx'); ElMessage.success('导出成功') } catch {}
}
async function handleDownloadTemplate() {
  try { const res = await downloadProductTemplate(); downloadBlob(res, '产品导入模板.xlsx') } catch {}
}
async function handleImport() {
  if (!importFile.value) { ElMessage.warning('请先选择文件'); return }
  importLoading.value = true
  try {
    await importProducts(importFile.value)
    ElMessage.success('导入成功'); importDialogVisible.value = false; importFile.value = null; loadData()
  } finally { importLoading.value = false }
}

// AI建档
const aiFilingVisible = ref(false); const aiDescription = ref('')
const aiFilingLoading = ref(false); const aiFilingResult = ref(null)

async function handleAiFiling() {
  if (!aiDescription.value.trim()) return
  aiFilingLoading.value = true; aiFilingResult.value = null
  try {
    const res = await aiProductFiling(aiDescription.value)
    aiFilingResult.value = res.data; ElMessage.success('AI生成完成，请核对信息')
  } catch { ElMessage.error('AI生成失败，请重试') } finally { aiFilingLoading.value = false }
}

function handleAiFilingConfirm() {
  if (!aiFilingResult.value) return
  const r = aiFilingResult.value
  resetForm(); editingId.value = null; dialogTitle.value = '新增产品'
  form.productName = r.name || ''; form.spec = r.spec || ''; form.category = r.category || ''
  form.unit = r.unit || ''; form.productionMode = r.productionMode || undefined
  form.standardCost = r.standardCost || undefined; form.remark = r.remark || ''
  aiFilingVisible.value = false; dialogVisible.value = true
  ElMessage.success('已填入产品信息，请补充并确认')
}

onMounted(() => { loadData() })
</script>

<style scoped lang="scss">
.page-container { padding: 16px; }
.search-bar { margin-bottom: 12px; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; }

.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 16px;
  min-height: 200px;
}
.product-card {
  cursor: pointer;
  transition: transform 0.2s;
  &:hover { transform: translateY(-2px); }
  :deep(.el-card__body) { padding: 0; }
}
.card-image-placeholder {
  width: 100%; height: 140px; background: #f5f7fa;
  display: flex; align-items: center; justify-content: center;
}
.card-body { padding: 12px; }
.card-title { font-weight: 600; font-size: 14px; margin-bottom: 6px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.card-info { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; font-size: 12px; color: #909399; }
.card-spec { font-size: 12px; color: #606266; margin-bottom: 8px; }
.card-footer { display: flex; justify-content: space-between; align-items: center; }
.card-actions { padding: 8px 12px; border-top: 1px solid #f0f0f0; display: flex; gap: 4px; }

.upload-placeholder {
  width: 120px; height: 120px; border: 1px dashed #d9d9d9; border-radius: 8px;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  cursor: pointer;
  &:hover { border-color: #409eff; }
}
</style>
