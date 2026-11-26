<template>
  <div class="page-container">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="任务编号">
          <el-input v-model="queryParams.taskNo" placeholder="任务编号" clearable style="width: 140px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="批次号">
          <el-input v-model="queryParams.batchNo" placeholder="批次号" clearable style="width: 130px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="检验类型">
          <el-select v-model="queryParams.qcType" placeholder="全部" clearable style="width: 130px">
            <el-option label="来料检验" value="incoming" />
            <el-option label="过程检验" value="process" />
            <el-option label="成品检验" value="final" />
            <el-option label="外协检验" value="outsource" />
          </el-select>
        </el-form-item>
        <el-form-item label="检验结果">
          <el-select v-model="queryParams.result" placeholder="全部" clearable style="width: 110px">
            <el-option label="合格" value="pass" />
            <el-option label="不合格" value="fail" />
          </el-select>
        </el-form-item>
        <el-form-item label="检验日期">
          <el-date-picker v-model="dateRange" type="daterange" range-separator="至" start-placeholder="开始" end-placeholder="结束" value-format="YYYY-MM-DD" style="width: 240px" @change="onDateChange" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="success" @click="openCreateDialog">新增质检录入</el-button>
          <el-button type="warning" :icon="Download" @click="handleExport">导出</el-button>
          <el-button :icon="Setting" @click="colConfigDialogRef.open()">列配置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stat-row">
      <el-col :span="6">
        <div class="stat-card stat-card--clickable" :class="{ active: !queryParams.result }" @click="filterByResult('')">
          <div class="stat-card__value" style="color: #409eff">{{ stats.total }}</div>
          <div class="stat-card__label">检验记录</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card stat-card--clickable" :class="{ active: queryParams.result === 'pass' }" @click="filterByResult('pass')">
          <div class="stat-card__value" style="color: #67c23a">{{ stats.pass }}</div>
          <div class="stat-card__label">合格</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card stat-card--clickable" :class="{ active: queryParams.result === 'fail' }" @click="filterByResult('fail')">
          <div class="stat-card__value" style="color: #f56c6c">{{ stats.fail }}</div>
          <div class="stat-card__label">不合格</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-card__value" :style="{ color: stats.passRate >= 90 ? '#67c23a' : '#f56c6c' }">
            {{ stats.passRate }}%
          </div>
          <div class="stat-card__label">合格率</div>
        </div>
      </el-col>
    </el-row>

    <!-- 数据表格 -->
    <el-card class="table-card" shadow="never">
      <el-table :data="tableData" v-loading="loading" border stripe size="small">
        <el-table-column v-for="col in visibleColumns" :key="col.key"
          :label="col.label" :min-width="col.minWidth || 100" :align="col.align || 'left'" show-overflow-tooltip>
          <template #default="{ row }">
            <template v-if="col.key === 'taskNo'">
              <el-button type="primary" link @click="openDetail(row)">{{ row.taskNo }}</el-button>
            </template>
            <template v-else-if="col.key === 'qcType'">
              <el-tag :type="qcTypeTag(row.qcType)" size="small" effect="plain">{{ qcTypeText(row.qcType) }}</el-tag>
            </template>
            <template v-else-if="col.key === 'productName'">{{ row.productName || row.materialName }}</template>
            <template v-else-if="col.key === 'relatedOrderNo'">
              <template v-if="row.qcType === 'incoming'">
                <el-button v-if="row.purchaseOrderNo" type="primary" link size="small" @click="openDocDetail('purchase', row)">{{ row.purchaseOrderNo }}</el-button>
                <span v-else class="text-muted">-</span>
              </template>
              <template v-else-if="row.qcType === 'outsource'">
                <el-button v-if="row.outsourceOrderNo" type="primary" link size="small" @click="openDocDetail('outsource', row)">{{ row.outsourceOrderNo }}</el-button>
                <span v-else class="text-muted">-</span>
              </template>
              <template v-else>
                <el-button v-if="row.workOrderNo" type="primary" link size="small" @click="openDocDetail('workOrder', row)">{{ row.workOrderNo }}</el-button>
                <span v-else class="text-muted">-</span>
              </template>
            </template>
            <template v-else-if="col.key === 'passQty'">
              <span style="color: #67c23a; font-weight: 500">{{ row.passQty }}</span>
            </template>
            <template v-else-if="col.key === 'failQty'">
              <span :style="{ color: row.failQty > 0 ? '#f56c6c' : '#c0c4cc', fontWeight: row.failQty > 0 ? '500' : '400' }">{{ row.failQty }}</span>
            </template>
            <template v-else-if="col.key === 'result'">
              <el-tag :type="(row.result === 'pass' || (row.result === 'completed' && !(row.failQty > 0))) ? 'success' : 'danger'" size="small">
                {{ (row.result === 'pass' || (row.result === 'completed' && !(row.failQty > 0))) ? '合格' : '不合格' }}
              </el-tag>
            </template>
            <template v-else-if="col.key === 'conclusion'">{{ fmtConclusion(row.conclusion) }}</template>
            <template v-else>{{ row[col.key] || '-' }}</template>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
          :total="total" :page-sizes="[10, 20, 50, 100]" background layout="total, sizes, prev, pager, next"
          @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" :title="`检验记录详情 ${detailData?.taskNo || ''}`" size="600px">
      <template v-if="detailData">
        <el-descriptions :column="2" border size="small" style="margin-bottom: 16px">
          <el-descriptions-item label="任务编号">{{ detailData.taskNo }}</el-descriptions-item>
          <el-descriptions-item label="检验类型">
            <el-tag :type="qcTypeTag(detailData.qcType)" size="small">{{ qcTypeText(detailData.qcType) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="产品/物料">{{ detailData.productName || detailData.materialName }}</el-descriptions-item>
          <el-descriptions-item label="关联单号">
            <template v-if="detailData.qcType === 'incoming'">
              <el-button v-if="detailData.purchaseOrderNo" type="primary" link @click="openDocDetail('purchase', detailData)">{{ detailData.purchaseOrderNo }}</el-button>
              <span v-else>-</span>
            </template>
            <template v-else-if="detailData.qcType === 'outsource'">
              <el-button v-if="detailData.outsourceOrderNo" type="primary" link @click="openDocDetail('outsource', detailData)">{{ detailData.outsourceOrderNo }}</el-button>
              <span v-else>-</span>
            </template>
            <template v-else>
              <el-button v-if="detailData.workOrderNo" type="primary" link @click="openDocDetail('workOrder', detailData)">{{ detailData.workOrderNo }}</el-button>
              <span v-else>-</span>
            </template>
          </el-descriptions-item>
          <el-descriptions-item label="批次号">{{ detailData.batchNo }}</el-descriptions-item>
          <el-descriptions-item label="送检数量">{{ detailData.planQty }}</el-descriptions-item>
          <el-descriptions-item label="实检数量">{{ detailData.sampleQty }}</el-descriptions-item>
          <el-descriptions-item label="合格数">
            <span style="color: #67c23a; font-weight: 600">{{ detailData.passQty }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="不合格数">
            <span style="color: #f56c6c; font-weight: 600">{{ detailData.failQty }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="检验结果">
            <el-tag :type="(detailData.result === 'pass' || (detailData.result === 'completed' && !(detailData.failQty > 0))) ? 'success' : 'danger'">
              {{ (detailData.result === 'pass' || (detailData.result === 'completed' && !(detailData.failQty > 0))) ? '合格' : '不合格' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="检验员">{{ detailData.inspectorName }}</el-descriptions-item>
          <el-descriptions-item label="检验时间" :span="2">{{ detailData.inspectedAt }}</el-descriptions-item>
          <el-descriptions-item label="结论" :span="2">{{ fmtConclusion(detailData.conclusion) }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detailData.remark || '-' }}</el-descriptions-item>
        </el-descriptions>

        <h4 v-if="detailResults.length" style="margin-bottom: 8px">检验明细</h4>
        <el-table v-if="detailResults.length" :data="detailResults" border size="small">
          <el-table-column prop="itemName" label="项目" min-width="120" />
          <el-table-column prop="standardValue" label="标准值" width="90" align="center" />
          <el-table-column label="公差" width="110" align="center">
            <template #default="{ row }">{{ row.toleranceMin || '-' }} ~ {{ row.toleranceMax || '-' }}</template>
          </el-table-column>
          <el-table-column prop="actualValue" label="实测值" width="90" align="center">
            <template #default="{ row }">
              <span :style="{ color: row.isPass ? '#67c23a' : '#f56c6c', fontWeight: '500' }">{{ row.actualValue }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="isPass" label="判定" width="70" align="center">
            <template #default="{ row }">
              <el-tag :type="row.isPass ? 'success' : 'danger'" size="small">{{ row.isPass ? '合格' : '不合格' }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-drawer>

    <!-- ========== 关联单据详情抽屉 ========== -->
    <el-drawer v-model="docDetailVisible" :title="docDetailTitle" size="700px" destroy-on-close>
      <div v-loading="docDetailLoading">
        <template v-if="docDetail">
          <!-- 工单详情 -->
          <template v-if="docDetailType === 'workOrder'">
            <el-descriptions :column="2" border>
              <el-descriptions-item label="工单号">{{ docDetail.workOrderNo || docDetail.woNo }}</el-descriptions-item>
              <el-descriptions-item label="状态">{{ woStatusText(docDetail.status) }}</el-descriptions-item>
              <el-descriptions-item label="产品名称">{{ docDetail.productName }}</el-descriptions-item>
              <el-descriptions-item label="计划数量">{{ docDetail.planQty }}</el-descriptions-item>
              <el-descriptions-item label="完工数量">{{ docDetail.completedQty }}</el-descriptions-item>
              <el-descriptions-item label="开始日期">{{ docDetail.startDate }}</el-descriptions-item>
              <el-descriptions-item label="结束日期">{{ docDetail.endDate }}</el-descriptions-item>
              <el-descriptions-item label="客户">{{ docDetail.customerName || '--' }}</el-descriptions-item>
              <el-descriptions-item label="备注" :span="2">{{ docDetail.remark || '--' }}</el-descriptions-item>
            </el-descriptions>
          </template>
          <!-- 采购单详情 -->
          <template v-else-if="docDetailType === 'purchase'">
            <el-descriptions :column="2" border>
              <el-descriptions-item label="采购单号">{{ docDetail.poNo || docDetail.orderNo }}</el-descriptions-item>
              <el-descriptions-item label="状态">{{ docDetail.statusText || docDetail.status }}</el-descriptions-item>
              <el-descriptions-item label="供应商">{{ docDetail.supplierName }}</el-descriptions-item>
              <el-descriptions-item label="总金额">{{ docDetail.totalAmount }}</el-descriptions-item>
              <el-descriptions-item label="创建时间">{{ docDetail.createTime }}</el-descriptions-item>
              <el-descriptions-item label="备注">{{ docDetail.remark || '--' }}</el-descriptions-item>
            </el-descriptions>
            <div v-if="docDetail.items && docDetail.items.length" style="margin-top: 16px">
              <h4 style="margin-bottom: 8px">采购明细</h4>
              <el-table :data="docDetail.items" border size="small">
                <el-table-column prop="materialName" label="物料名称" min-width="140" show-overflow-tooltip />
                <el-table-column prop="quantity" label="数量" width="80" align="center" />
                <el-table-column prop="unitPrice" label="单价" width="100" align="center" />
                <el-table-column prop="amount" label="金额" width="100" align="center" />
              </el-table>
            </div>
          </template>
          <!-- 外协单详情 -->
          <template v-else-if="docDetailType === 'outsource'">
            <el-descriptions :column="2" border>
              <el-descriptions-item label="加工单号">{{ docDetail.orderNo }}</el-descriptions-item>
              <el-descriptions-item label="供应商">{{ docDetail.supplierName }}</el-descriptions-item>
              <el-descriptions-item label="产品名称">{{ docDetail.productName }}</el-descriptions-item>
              <el-descriptions-item label="工序名称">{{ docDetail.stepName }}</el-descriptions-item>
              <el-descriptions-item label="总数量">{{ docDetail.totalQty }}</el-descriptions-item>
              <el-descriptions-item label="合格数量">{{ docDetail.qualifiedQty || 0 }}</el-descriptions-item>
              <el-descriptions-item label="交付日期">{{ docDetail.deliveryDate }}</el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag :type="outsourceStatusType(docDetail.status)" size="small">{{ outsourceStatusText(docDetail.status) }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="备注" :span="2">{{ docDetail.remark || '--' }}</el-descriptions-item>
            </el-descriptions>
          </template>
        </template>
        <el-empty v-else-if="!docDetailLoading" description="暂无数据" />
      </div>
    </el-drawer>

    <ColumnConfigDialog ref="colConfigDialogRef" :all-columns="allColumns" :default-columns="QUALITY_RECORD_DEFAULT_COLUMNS" @save="saveConfig" />

    <!-- ========== 新增质检录入弹窗 ========== -->
    <el-dialog v-model="createVisible" title="新增质检录入" width="820px" destroy-on-close>
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="检验类型" prop="qcType">
              <el-select v-model="createForm.qcType" placeholder="请选择" style="width: 100%" @change="onQcTypeChange">
                <el-option label="来料检验" value="incoming" />
                <el-option label="过程检验" value="process" />
                <el-option label="成品检验" value="final" />
                <el-option label="外协检验" value="outsource" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="质检模板" prop="templateId">
              <el-select v-model="createForm.templateId" placeholder="选择模板自动带出检验项" clearable filterable style="width: 100%" @change="onTemplateChange">
                <el-option v-for="t in templateOptions" :key="t.id" :label="t.templateName" :value="t.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="24">
            <el-form-item :label="relatedOrderLabel">
              <el-select
                v-model="createForm.relatedOrderId"
                :placeholder="`请选择${relatedOrderLabel}（可自动带出产品和数量）`"
                clearable filterable
                :loading="relatedOrderLoading"
                style="width: 100%"
                @change="onRelatedOrderChange"
              >
                <el-option v-for="o in relatedOrderOptions" :key="o.id" :label="o.label" :value="o.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="createForm.qcType === 'incoming' ? '物料' : '产品/物料'" prop="productId">
              <div style="display: flex; align-items: center; gap: 8px; width: 100%;">
                <el-input :value="createForm.productName" readonly :placeholder="createForm.qcType === 'incoming' ? '请选择物料' : '请选择产品'" style="flex: 1;" />
                <el-button type="primary" @click="recordProductPickerVisible = true">选择</el-button>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="批次号">
              <el-input v-model="createForm.batchNo" placeholder="请输入批次号" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="送检数量" prop="planQty">
              <el-input-number v-model="createForm.planQty" :min="1" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="实检数量">
              <el-input-number v-model="createForm.sampleQty" :min="1" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="质检结论" prop="conclusion">
              <el-select v-model="createForm.conclusion" placeholder="请选择质检结论" style="width: 100%">
                <el-option label="合格" value="合格" />
                <el-option label="不合格" value="不合格" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="备注">
              <el-input v-model="createForm.remark" placeholder="备注信息（选填）" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 检验项录入 -->
        <el-divider content-position="left">检验项目录入</el-divider>
        <div style="margin-bottom: 8px">
          <el-button type="primary" plain size="small" @click="addResultRow">+ 添加检验项</el-button>
        </div>
        <el-table :data="createResults" border size="small" v-if="createResults.length">
          <el-table-column label="项目名称" min-width="130">
            <template #default="{ row }">
              <el-input v-model="row.itemName" placeholder="项目名称" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="标准值" width="100">
            <template #default="{ row }">
              <el-input v-model="row.standardValue" placeholder="标准值" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="公差下限" width="90">
            <template #default="{ row }">
              <el-input v-model="row.toleranceMin" placeholder="下限" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="公差上限" width="90">
            <template #default="{ row }">
              <el-input v-model="row.toleranceMax" placeholder="上限" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="实测值" width="100">
            <template #default="{ row }">
              <el-input v-model="row.actualValue" placeholder="实测值" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="判定" width="100" align="center">
            <template #default="{ row }">
              <el-select v-model="row.isPass" size="small" style="width: 80px">
                <el-option label="合格" :value="1" />
                <el-option label="不合格" :value="0" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="60" align="center">
            <template #default="{ $index }">
              <el-button type="danger" link size="small" @click="createResults.splice($index, 1)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div v-else style="text-align: center; color: #909399; padding: 20px 0">
          选择质检模板自动带出检验项，或手动添加
        </div>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createLoading" @click="handleCreate">提交质检记录</el-button>
      </template>
    </el-dialog>

    <!-- 产品/物料选择弹窗 -->
    <MaterialPickerDialog v-model="recordProductPickerVisible" title="选择产品/物料" :multiple="false"
                          @select="onRecordProductSelected" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Setting, Download } from '@element-plus/icons-vue'
import { getQcRecordPage, getQcRecordStats, getQcTaskDetail, createQcTask, submitQcResult, getQcTemplateSelect, getQcTemplateDetail, exportQcRecords } from '@/api/quality'
import { useColumnConfig } from '@/composables/useColumnConfig'
import ColumnConfigDialog from '@/components/ColumnConfigDialog.vue'
import MaterialPickerDialog from '@/components/MaterialPickerDialog.vue'

const QUALITY_RECORD_DEFAULT_COLUMNS = [
  { key: 'taskNo', label: '任务编号', minWidth: 160 },
  { key: 'qcType', label: '检验类型', minWidth: 100, align: 'center' },
  { key: 'productName', label: '产品/物料', minWidth: 130 },
  { key: 'relatedOrderNo', label: '关联单号', minWidth: 175 },
  { key: 'batchNo', label: '批次号', minWidth: 120 },
  { key: 'planQty', label: '送检数', minWidth: 80, align: 'right' },
  { key: 'sampleQty', label: '实检数', minWidth: 80, align: 'right' },
  { key: 'passQty', label: '合格数', minWidth: 80, align: 'right' },
  { key: 'failQty', label: '不合格数', minWidth: 80, align: 'right' },
  { key: 'result', label: '结果', minWidth: 80, align: 'center' },
  { key: 'inspectorName', label: '检验员', minWidth: 100 },
  { key: 'inspectedAt', label: '检验时间', minWidth: 160 },
  { key: 'conclusion', label: '结论', minWidth: 120 },
  { key: 'remark', label: '备注', minWidth: 180 },
]
const colConfigDialogRef = ref(null)
const { visibleColumns, allColumns, loadColumnConfig, saveConfig } = useColumnConfig('quality_record', QUALITY_RECORD_DEFAULT_COLUMNS)
import { getWorkOrderDetail, getWorkOrderPage } from '@/api/production'
import { getOutsourceOrderDetail, getOutsourceOrderPage } from '@/api/outsource'
import { getPurchaseOrderDetail, getPurchaseOrderDetailByNo, getPurchaseOrderPage } from '@/api/purchase'

const qcTypeMap = { incoming: '来料检验', process: '过程检验', final: '成品检验', outsource: '外协检验' }
const qcTypeTagMap = { incoming: '', process: 'success', final: 'warning', outsource: 'danger' }
function qcTypeText(val) { return qcTypeMap[val] || val || '--' }
function qcTypeTag(val) { return qcTypeTagMap[val] || 'info' }
const conclusionMap = { pass: '合格', fail: '不合格', passed: '合格', failed: '不合格', PASS: '合格', FAIL: '不合格', PASSED: '合格', FAILED: '不合格' }
function fmtConclusion(v) { return conclusionMap[v] || v || '-' }

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const dateRange = ref(null)
const queryParams = reactive({ taskNo: '', batchNo: '', qcType: '', result: '', startDate: '', endDate: '', pageNum: 1, pageSize: 20 })

// stats 由后端按全量过滤条件计算返回(避免只统计当前页 20 条)
const statsData = ref({ total: 0, pass: 0, fail: 0, passRate: 0 })
const stats = computed(() => statsData.value)

async function loadStats() {
  try {
    const res = await getQcRecordStats({
      taskNo: queryParams.taskNo || undefined,
      batchNo: queryParams.batchNo || undefined,
      qcType: queryParams.qcType || undefined,
      startDate: queryParams.startDate || undefined,
      endDate: queryParams.endDate || undefined
    })
    statsData.value = {
      total: res.data?.total || 0,
      pass: res.data?.pass || 0,
      fail: res.data?.fail || 0,
      passRate: res.data?.passRate || 0
    }
  } catch {}
}

function onDateChange(val) {
  queryParams.startDate = val ? val[0] : ''
  queryParams.endDate = val ? val[1] : ''
}

async function loadData() {
  loading.value = true
  try {
    const res = await getQcRecordPage({
      taskNo: queryParams.taskNo || undefined,
      batchNo: queryParams.batchNo || undefined,
      qcType: queryParams.qcType || undefined,
      result: queryParams.result || undefined,
      startDate: queryParams.startDate || undefined,
      endDate: queryParams.endDate || undefined,
      pageNum: queryParams.pageNum,
      pageSize: queryParams.pageSize
    })
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch {} finally { loading.value = false }
  // 同时刷新全量统计(后端按相同过滤条件算总数 / 合格 / 不合格 / 合格率)
  loadStats()
}

function handleSearch() { queryParams.pageNum = 1; loadData() }
function filterByResult(result) {
  queryParams.result = queryParams.result === result ? '' : result
  queryParams.pageNum = 1
  loadData()
}
function handleReset() {
  queryParams.taskNo = ''; queryParams.batchNo = ''; queryParams.qcType = ''; queryParams.result = ''
  queryParams.startDate = ''; queryParams.endDate = ''; dateRange.value = null
  queryParams.pageNum = 1; loadData()
}

function downloadBlob(data, filename) {
  const blob = new Blob([data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url; link.download = filename; link.click()
  window.URL.revokeObjectURL(url)
}
async function handleExport() {
  try {
    const res = await exportQcRecords({
      taskNo: queryParams.taskNo || undefined,
      batchNo: queryParams.batchNo || undefined,
      qcType: queryParams.qcType || undefined,
      result: queryParams.result || undefined,
      startDate: queryParams.startDate || undefined,
      endDate: queryParams.endDate || undefined
    })
    downloadBlob(res, `质检记录_${new Date().toISOString().slice(0, 10)}.xlsx`)
    ElMessage.success('导出成功')
  } catch { ElMessage.error('导出失败') }
}

// ==================== 详情 ====================
const detailVisible = ref(false)
const detailData = ref(null)
const detailResults = ref([])

async function openDetail(row) {
  detailData.value = row
  detailResults.value = []
  detailVisible.value = true
  try {
    const res = await getQcTaskDetail(row.id)
    const d = res.data || {}
    detailData.value = { ...row, ...d }
    detailResults.value = d.results || []
  } catch {}
}

// ==================== 新增质检录入 ====================
const createVisible = ref(false)
const createLoading = ref(false)
const createFormRef = ref(null)
const templateOptions = ref([])
const relatedOrderOptions = ref([])
const relatedOrderLoading = ref(false)

const createForm = reactive({
  qcType: 'final',
  relatedOrderId: null,
  templateId: null,
  productId: null,
  productName: '',
  batchNo: '',
  planQty: 1,
  sampleQty: 1,
  conclusion: '合格',
  remark: ''
})

// 产品/物料选择弹窗
const recordProductPickerVisible = ref(false)
function onRecordProductSelected(items) {
  if (!items || items.length === 0) return
  const m = items[0]
  createForm.productId = m.id
  createForm.productName = m.materialName || m.name || ''
}
const createResults = ref([])

const relatedOrderLabel = computed(() => {
  return { incoming: '关联采购单', process: '关联工单', final: '关联工单', outsource: '关联外协单' }[createForm.qcType] || '关联单号'
})

const createRules = {
  qcType: [{ required: true, message: '请选择检验类型', trigger: 'change' }],
  productId: [{ required: true, message: '请选择产品/物料', trigger: 'change' }],
  planQty: [{ required: true, message: '请输入送检数量', trigger: 'blur' }],
  conclusion: [{ required: true, message: '请选择质检结论', trigger: 'change' }]
}

function addResultRow() {
  createResults.value.push({
    itemName: '', standardValue: '', toleranceMin: '', toleranceMax: '',
    actualValue: '', isPass: 1, unit: '', method: '', isCritical: 0
  })
}

async function onTemplateChange(templateId) {
  if (!templateId) { createResults.value = []; return }
  try {
    const res = await getQcTemplateDetail(templateId)
    const items = res.data?.items || []
    createResults.value = items.map(item => ({
      templateItemId: item.id,
      itemName: item.itemName || '',
      standardValue: item.standardValue || '',
      toleranceMin: item.toleranceMin || '',
      toleranceMax: item.toleranceMax || '',
      unit: item.unit || '',
      method: item.method || '',
      isCritical: item.isCritical || 0,
      actualValue: '',
      isPass: 1
    }))
  } catch { createResults.value = [] }
}

async function loadRelatedOrderOptions() {
  const type = createForm.qcType
  if (!type) return
  relatedOrderLoading.value = true
  relatedOrderOptions.value = []
  try {
    if (type === 'incoming') {
      const res = await getPurchaseOrderPage({ pageNum: 1, pageSize: 200 })
      relatedOrderOptions.value = (res.data?.records || []).map(o => ({
        id: o.id, label: `${o.poNo}${o.supplierName ? ' - ' + o.supplierName : ''}`, raw: o
      }))
    } else if (type === 'process' || type === 'final') {
      const res = await getWorkOrderPage({ pageNum: 1, pageSize: 200 })
      relatedOrderOptions.value = (res.data?.records || []).map(o => ({
        id: o.id, label: `${o.woNo}${o.productName ? ' - ' + o.productName : ''}`, raw: o
      }))
    } else if (type === 'outsource') {
      const res = await getOutsourceOrderPage({ pageNum: 1, pageSize: 200 })
      relatedOrderOptions.value = (res.data?.records || []).map(o => ({
        id: o.id, label: `${o.orderNo}${o.productName ? ' - ' + o.productName : ''}`, raw: o
      }))
    }
  } catch {} finally { relatedOrderLoading.value = false }
}

async function onQcTypeChange() {
  createForm.relatedOrderId = null
  createForm.productId = null
  relatedOrderOptions.value = []
  await loadRelatedOrderOptions()
}

async function onRelatedOrderChange(id) {
  if (!id) return
  const opt = relatedOrderOptions.value.find(o => o.id === id)
  const raw = opt?.raw
  if (!raw) return
  try {
    if (createForm.qcType === 'incoming') {
      // 加载采购单详情获取物料信息
      const res = await getPurchaseOrderDetail(id)
      const detail = res.data || {}
      const firstItem = (detail.items || [])[0]
      if (firstItem?.materialId) createForm.productId = firstItem.materialId
      if (firstItem?.quantity) createForm.planQty = firstItem.quantity
    } else if (createForm.qcType === 'process' || createForm.qcType === 'final') {
      if (raw.productId) createForm.productId = raw.productId
      if (raw.planQty) createForm.planQty = raw.planQty
    } else if (createForm.qcType === 'outsource') {
      if (raw.productId) createForm.productId = raw.productId
      if (raw.totalQty) createForm.planQty = raw.totalQty
    }
  } catch {}
}

async function openCreateDialog() {
  createForm.qcType = 'final'
  createForm.relatedOrderId = null
  createForm.templateId = null
  createForm.productId = null
  createForm.batchNo = ''
  createForm.planQty = 1
  createForm.sampleQty = 1
  createForm.conclusion = '合格'
  createForm.remark = ''
  createResults.value = []
  relatedOrderOptions.value = []
  createVisible.value = true
  // 加载下拉数据
  try { const res = await getQcTemplateSelect(); templateOptions.value = res.data || [] } catch {}
  await loadRelatedOrderOptions()
}

async function handleCreate() {
  await createFormRef.value.validate()
  if (createResults.value.length === 0) {
    ElMessage.warning('请至少添加一项检验项目'); return
  }
  if (createResults.value.some(r => !r.itemName)) {
    ElMessage.warning('检验项目名称不能为空'); return
  }

  createLoading.value = true
  try {
    // 1. 创建质检任务
    const payload = {
      qcType: createForm.qcType,
      templateId: createForm.templateId,
      batchNo: createForm.batchNo || undefined,
      planQty: createForm.planQty,
      sampleQty: createForm.sampleQty
    }
    if (createForm.qcType === 'incoming') {
      payload.materialId = createForm.productId
      payload.relatedOrderId = createForm.relatedOrderId || undefined
      payload.relatedOrderType = 'purchase'
    } else if (createForm.qcType === 'process' || createForm.qcType === 'final') {
      payload.productId = createForm.productId
      payload.workOrderId = createForm.relatedOrderId || undefined
    } else if (createForm.qcType === 'outsource') {
      payload.productId = createForm.productId
      payload.relatedOrderId = createForm.relatedOrderId || undefined
      payload.relatedOrderType = 'outsource'
    } else {
      payload.productId = createForm.productId
    }
    const taskRes = await createQcTask(payload)
    const taskId = taskRes.data?.id ?? taskRes.data

    // 2. 提交检验结果（直接完成）
    await submitQcResult(taskId, {
      results: createResults.value.map(r => ({
        templateItemId: r.templateItemId || undefined,
        itemName: r.itemName,
        standardValue: r.standardValue || undefined,
        toleranceMin: r.toleranceMin || undefined,
        toleranceMax: r.toleranceMax || undefined,
        unit: r.unit || undefined,
        method: r.method || undefined,
        isCritical: r.isCritical || 0,
        actualValue: r.actualValue,
        isPass: r.isPass
      })),
      conclusion: createForm.conclusion || undefined,
      remark: createForm.remark || undefined
    })

    ElMessage.success('质检记录创建成功')
    createVisible.value = false
    loadData()
  } catch {} finally { createLoading.value = false }
}

// ==================== 关联单据详情 ====================
const docDetailVisible = ref(false)
const docDetailLoading = ref(false)
const docDetailType = ref('')
const docDetail = ref(null)
const docDetailTitle = computed(() => {
  const map = { workOrder: '工单详情', purchase: '采购单详情', outsource: '外协单详情' }
  return map[docDetailType.value] || '单据详情'
})

function woStatusText(s) {
  return { draft: '草稿', confirmed: '已确认', in_progress: '生产中', completed: '已完成', cancelled: '已取消' }[s] || s || '--'
}
function outsourceStatusType(s) {
  return { draft: 'info', confirmed: '', processing: 'warning', completed: 'success', cancelled: 'danger' }[s] || 'info'
}
function outsourceStatusText(s) {
  return { draft: '草稿', confirmed: '已确认', processing: '加工中', completed: '已完成', cancelled: '已取消' }[s] || s || '--'
}

async function openDocDetail(type, row) {
  docDetailType.value = type
  docDetail.value = null
  docDetailVisible.value = true
  docDetailLoading.value = true
  try {
    let res
    if (type === 'workOrder') {
      if (!row.workOrderId) { ElMessage.warning('工单ID不存在'); docDetailVisible.value = false; return }
      res = await getWorkOrderDetail(row.workOrderId)
    } else if (type === 'outsource') {
      if (!row.relatedOrderId) { ElMessage.warning('外协单ID不存在'); docDetailVisible.value = false; return }
      res = await getOutsourceOrderDetail(row.relatedOrderId)
    } else if (type === 'purchase') {
      if (row.relatedOrderId) {
        res = await getPurchaseOrderDetail(row.relatedOrderId)
      } else if (row.purchaseOrderNo) {
        res = await getPurchaseOrderDetailByNo(row.purchaseOrderNo)
      } else {
        ElMessage.warning('采购单信息不存在'); docDetailVisible.value = false; return
      }
    }
    docDetail.value = res?.data || null
  } catch {
    ElMessage.error('获取单据详情失败')
  } finally {
    docDetailLoading.value = false
  }
}

onMounted(() => { loadData(); loadColumnConfig() })
</script>

<style scoped lang="scss">
.page-container { display: flex; flex-direction: column; gap: 16px; }
.text-muted { color: #c0c4cc; font-size: 12px; }
.search-card { :deep(.el-card__body) { padding-bottom: 2px; } }
.table-card { flex: 1; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 16px; }
.stat-row { flex-shrink: 0; }
.stat-card {
  background: #fff; border-radius: 8px; padding: 16px; text-align: center;
  box-shadow: 0 1px 4px rgba(0,0,0,.06);
  border: 2px solid transparent;
  transition: all .2s;
  &__value { font-size: 28px; font-weight: 700; }
  &__label { font-size: 13px; color: #909399; margin-top: 4px; }
}
.stat-card--clickable { cursor: pointer; }
.stat-card--clickable:hover { box-shadow: 0 2px 12px rgba(64,158,255,.18); transform: translateY(-1px); }
.stat-card--clickable.active { border-color: #409eff; background: #ecf5ff; }
</style>
