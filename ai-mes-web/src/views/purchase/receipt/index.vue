<template>
  <div class="page-container">
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="收料单号">
          <el-input v-model="queryParams.receiptNo" placeholder="收料单号" clearable style="width: 150px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="采购订单号">
          <el-input v-model="queryParams.poNo" placeholder="采购订单号" clearable style="width: 150px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="供应商">
          <el-input v-model="queryParams.supplierName" placeholder="供应商名称" clearable style="width: 140px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="物料名称">
          <el-input v-model="queryParams.materialName" placeholder="物料名称" clearable style="width: 140px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="收料状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="待收料" value="pending_receive" />
            <el-option label="收料中" value="receiving" />
            <el-option label="已收料" value="received" />
            <el-option label="办结" value="closed" />
          </el-select>
        </el-form-item>
        <el-form-item label="质检状态">
          <el-select v-model="queryParams.qcStatus" placeholder="全部" clearable style="width: 120px">
            <el-option label="无质检" value="none" />
            <el-option label="待质检" value="pending_qc" />
            <el-option label="已质检" value="qc_done" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="success" @click="handleExport">导出</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据表格 -->
    <el-card shadow="never" class="table-card">
      <!-- 折叠开关 -->
      <div style="display: flex; justify-content: flex-end; margin-bottom: 8px; align-items: center; gap: 8px">
        <span style="font-size: 13px; color: #606266">按批次合并显示</span>
        <el-switch v-model="groupedMode" @change="onGroupedModeChange" />
      </div>

      <!-- 折叠模式: 按 group 聚合显示, type=expand 可展开看物料明细 -->
      <el-table v-if="groupedMode" :data="displayGroupedData" v-loading="loading" border stripe row-key="_key">
        <el-table-column type="expand">
          <template #default="{ row }">
            <el-table :data="row.items" border size="small" style="margin: 4px 12px; width: calc(100% - 24px)">
              <el-table-column prop="receiptNo" label="收料单号" min-width="140" />
              <el-table-column prop="materialNo" label="物料编号" width="120" />
              <el-table-column prop="materialName" label="物料名称" min-width="130" show-overflow-tooltip />
              <el-table-column label="规格" min-width="110" show-overflow-tooltip>
                <template #default="{ row: m }">{{ $fmtSpec(m.spec) }}</template>
              </el-table-column>
              <el-table-column prop="actualQty" label="收料数量" width="90" align="right" />
              <el-table-column prop="qualifiedQty" label="合格数" width="80" align="right" />
              <el-table-column prop="defectQty" label="不合格数" width="80" align="right" />
              <el-table-column label="收料状态" width="100" align="center">
                <template #default="{ row: m }">
                  <el-tag :type="statusTag[m.status]" size="small">{{ statusText[m.status] || m.status }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="质检状态" width="100" align="center">
                <template #default="{ row: m }">
                  <el-tag :type="qcStatusTag[m.qcStatus]" size="small">{{ qcStatusText[m.qcStatus] || m.qcStatus }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="180" align="center">
                <template #default="{ row: m }">
                  <el-button v-if="m.status === 'pending_receive'" type="primary" link size="small" @click="openReceiveDialog(m)">收料</el-button>
                  <el-button type="primary" link size="small" @click="openDetail(m)">详情</el-button>
                  <el-button v-if="m.status !== 'closed'" type="warning" link size="small" @click="handleClose(m)">办结</el-button>
                </template>
              </el-table-column>
            </el-table>
          </template>
        </el-table-column>
        <el-table-column label="批次组号" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.receiptGroupId">{{ row.receiptGroupId }}</span>
            <el-tag v-else type="info" size="small">老数据(无批次)</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="采购订单号" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button v-if="row.poNo" type="primary" link @click="openOrderDetail(row.poNo)">{{ row.poNo }}</el-button>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="supplierName" label="供应商" min-width="130" show-overflow-tooltip />
        <el-table-column label="物料数" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small">{{ row.materialCount }} 种</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="实收合计" width="100" align="right">
          <template #default="{ row }">{{ row.actualTotal.toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="合格合计" width="100" align="right">
          <template #default="{ row }">{{ row.qualifiedTotal.toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="入库合计" width="100" align="right">
          <template #default="{ row }">{{ row.stockInTotal.toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="状态汇总" min-width="140" align="center">
          <template #default="{ row }">
            <span style="font-size: 12px; color: #606266">{{ row.statusSummary }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="160">
          <template #default="{ row }">{{ $fmtDate(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.receiptGroupId && row.pendingCount > 0" type="primary" link size="small"
              @click="openBatchReceive(row)">
              批量收料({{ row.pendingCount }})
            </el-button>
            <el-button v-if="row.receiptGroupId" type="success" link size="small"
              @click="handlePrintGroup({ receiptGroupId: row.receiptGroupId, poNo: row.poNo, receiptNo: row.items[0]?.receiptNo })">
              打印对账单
            </el-button>
            <span v-if="!row.receiptGroupId" style="color: #c0c4cc; font-size: 12px">不可打印</span>
          </template>
        </el-table-column>
      </el-table>

      <!-- 平铺模式: 每物料一行 (原状) -->
      <el-table v-else :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="receiptNo" label="收料单号" min-width="140" show-overflow-tooltip />
        <el-table-column prop="poNo" label="采购订单号" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">
            <el-button v-if="row.poNo" type="primary" link @click="openOrderDetail(row.poNo)">{{ row.poNo }}</el-button>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="supplierName" label="供应商" min-width="130" show-overflow-tooltip />
        <el-table-column prop="materialName" label="物料名称" min-width="130" show-overflow-tooltip />
        <el-table-column label="规格" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
        </el-table-column>
        <el-table-column prop="actualQty" label="收料数量" width="100" align="right" />
        <el-table-column prop="status" label="收料状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag[row.status]" size="small">{{ statusText[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="qcStatus" label="质检状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="qcStatusTag[row.qcStatus]" size="small">{{ qcStatusText[row.qcStatus] || row.qcStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="qualifiedQty" label="合格数" width="80" align="right" />
        <el-table-column prop="defectQty" label="不合格数" width="80" align="right" />
        <el-table-column prop="invTaskQty" label="库存任务数" width="90" align="right" />
        <el-table-column prop="stockInQty" label="入库数" width="80" align="right" />
        <el-table-column prop="createTime" label="创建时间" width="160">
          <template #default="{ row }">{{ $fmtDate(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="280" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'pending_receive'" type="primary" link size="small" @click="openReceiveDialog(row)">收料</el-button>
            <el-button type="primary" link size="small" @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.receiptGroupId" type="success" link size="small" @click="handlePrintGroup(row)">打印对账单</el-button>
            <el-button v-if="row.status !== 'closed'" type="warning" link size="small" @click="handleClose(row)">办结</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50, 100]" :total="total" background layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <!-- 收料弹窗 -->
    <el-dialog v-model="receiveVisible" title="收料确认" width="500px" destroy-on-close>
      <el-descriptions :column="1" border size="small" style="margin-bottom: 16px">
        <el-descriptions-item label="收料单号">{{ receiveRow.receiptNo }}</el-descriptions-item>
        <el-descriptions-item label="物料编码">{{ receiveRow.materialNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="物料名称">{{ receiveRow.materialName }}</el-descriptions-item>
        <el-descriptions-item label="规格">{{ $fmtSpec(receiveRow.spec) }}</el-descriptions-item>
        <el-descriptions-item label="单位">{{ receiveRow.unit || '-' }}</el-descriptions-item>
        <el-descriptions-item label="收料数量">{{ receiveRow.actualQty }}</el-descriptions-item>
        <el-descriptions-item label="批次号">{{ receiveRow.batchNo || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-form label-width="80px">
        <el-form-item label="目标仓库" required>
          <el-select v-model="receiveForm.warehouseId" placeholder="请选择仓库" style="width: 100%">
            <el-option v-for="w in warehouseOptions" :key="w.id" :label="w.warehouseName" :value="w.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="质检选项">
          <el-radio-group v-model="receiveForm.needQc">
            <el-radio :label="true">报检</el-radio>
            <el-radio :label="false">放行</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="receiveVisible = false">取消</el-button>
        <el-button type="primary" :loading="receiveLoading" @click="doReceive">确认收料</el-button>
      </template>
    </el-dialog>

    <!-- 整组汇总收料弹窗 -->
    <el-dialog v-model="batchReceiveVisible" title="批量收料(整组汇总)" width="720px" destroy-on-close>
      <el-descriptions :column="2" border size="small" style="margin-bottom: 12px">
        <el-descriptions-item label="批次组号">{{ batchReceiveGroup.receiptGroupId }}</el-descriptions-item>
        <el-descriptions-item label="采购订单">{{ batchReceiveGroup.poNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ batchReceiveGroup.supplierName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="待收料行数">{{ batchReceivePendingItems.length }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="batchReceivePendingItems" border size="small" style="margin-bottom: 12px" max-height="280">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="materialNo" label="物料编号" width="120" />
        <el-table-column prop="materialName" label="物料名称" min-width="140" show-overflow-tooltip />
        <el-table-column label="规格" min-width="120" show-overflow-tooltip>
          <template #default="{ row: m }">{{ $fmtSpec(m.spec) }}</template>
        </el-table-column>
        <el-table-column prop="actualQty" label="收料数量" width="90" align="right" />
        <el-table-column prop="unit" label="单位" width="60" align="center" />
      </el-table>
      <el-form label-width="100px">
        <el-form-item label="目标仓库" required>
          <el-select v-model="batchReceiveForm.warehouseId" placeholder="所有物料统一入此仓库" style="width: 100%">
            <el-option v-for="w in warehouseOptions" :key="w.id" :label="w.warehouseName" :value="w.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="质检选项">
          <el-radio-group v-model="batchReceiveForm.needQc">
            <el-radio :label="true">全部报检</el-radio>
            <el-radio :label="false">全部放行</el-radio>
          </el-radio-group>
          <div style="color:#909399; font-size:12px; margin-top:4px">
            统一处理本组内所有待收料行;若需逐行选择,请展开后单独操作。
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchReceiveVisible = false">取消</el-button>
        <el-button type="primary" :loading="batchReceiveLoading" @click="doBatchReceive">确认收料</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" title="收料单详情" size="700px" destroy-on-close>
      <template v-if="detailData">
        <el-descriptions :column="2" border size="small" style="margin-bottom: 16px">
          <el-descriptions-item label="收料单号">{{ detailData.receiptNo }}</el-descriptions-item>
          <el-descriptions-item label="采购订单号">{{ detailData.poNo }}</el-descriptions-item>
          <el-descriptions-item label="供应商">{{ detailData.supplierName }}</el-descriptions-item>
          <el-descriptions-item label="物料名称">{{ detailData.materialName }}</el-descriptions-item>
          <el-descriptions-item label="规格">{{ $fmtSpec(detailData.spec) }}</el-descriptions-item>
          <el-descriptions-item label="单位">{{ detailData.unit || '-' }}</el-descriptions-item>
          <el-descriptions-item label="收料数量">{{ detailData.actualQty }}</el-descriptions-item>
          <el-descriptions-item label="批次号">{{ detailData.batchNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="收料状态">
            <el-tag :type="statusTag[detailData.status]" size="small">{{ statusText[detailData.status] }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="质检状态">
            <el-tag :type="qcStatusTag[detailData.qcStatus]" size="small">{{ qcStatusText[detailData.qcStatus] }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="合格数">{{ detailData.qualifiedQty }}</el-descriptions-item>
          <el-descriptions-item label="不合格数">{{ detailData.defectQty }}</el-descriptions-item>
          <el-descriptions-item label="库存任务数">{{ detailData.invTaskQty }}</el-descriptions-item>
          <el-descriptions-item label="入库数">{{ detailData.stockInQty }}</el-descriptions-item>
        </el-descriptions>

        <el-tabs v-model="detailTab">
          <el-tab-pane label="质检任务" name="qc">
            <el-table :data="detailQcTasks" v-loading="detailLoading" border stripe size="small">
              <el-table-column prop="taskNo" label="任务编号" min-width="140" />
              <el-table-column prop="result" label="状态" width="100" align="center">
                <template #default="{ row }">
                  <el-tag v-if="row.result === 'completed'" type="success" size="small">检测完成</el-tag>
                  <el-tag v-else-if="row.result === 'inspecting'" size="small">检验中</el-tag>
                  <el-tag v-else type="info" size="small">{{ row.result === 'pending' ? '待检' : row.result }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="planQty" label="总数量" width="80" align="right" />
              <el-table-column prop="passQty" label="合格数" width="80" align="right" />
              <el-table-column prop="failQty" label="不合格数" width="80" align="right" />
              <el-table-column prop="inspectedAt" label="检验时间" width="160">
                <template #default="{ row }">{{ $fmtDate(row.inspectedAt) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="80" align="center">
                <template #default="{ row }">
                  <el-button type="primary" link size="small" @click="openQcDetail(row)">
                    详情
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="库存任务" name="inv">
            <el-table :data="detailInvTasks" v-loading="detailLoading" border stripe size="small">
              <el-table-column prop="taskNo" label="任务编号" min-width="140" />
              <el-table-column prop="invType" label="任务类型" width="160">
                <template #default="{ row }">{{ invTypeText[row.invType] || row.invType }}</template>
              </el-table-column>
              <el-table-column prop="status" label="状态" width="90" align="center">
                <template #default="{ row }">
                  <el-tag :type="invStatusTag[row.status]" size="small">{{ invStatusText[row.status] || row.status }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="quantity" label="数量" width="80" align="right" />
              <el-table-column prop="processedQty" label="已处理" width="80" align="right" />
              <el-table-column prop="createTime" label="创建时间" width="160">
                <template #default="{ row }">{{ $fmtDate(row.createTime) }}</template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </template>
    </el-drawer>

    <!-- 质检详情弹窗 -->
    <el-dialog v-model="qcDetailVisible" title="质检任务详情" width="700px" destroy-on-close>
      <div v-loading="qcDetailLoading">
        <template v-if="qcDetailData">
          <el-descriptions :column="2" border size="small" style="margin-bottom:12px">
            <el-descriptions-item label="任务编号">{{ qcDetailData.taskNo }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="qcDetailData.result === 'completed' ? 'success' : 'info'" size="small">
                {{ { pending: '待检', inspecting: '检验中', completed: '检测完成' }[qcDetailData.result] || qcDetailData.result }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="总数量">{{ qcDetailData.planQty }}</el-descriptions-item>
            <el-descriptions-item label="质检数量">{{ qcDetailData.sampleQty || qcDetailData.planQty }}</el-descriptions-item>
            <el-descriptions-item label="合格数">{{ qcDetailData.passQty || 0 }}</el-descriptions-item>
            <el-descriptions-item label="不合格数">{{ qcDetailData.failQty || 0 }}</el-descriptions-item>
            <el-descriptions-item label="检验员">{{ qcDetailData.inspectorName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="检验时间">{{ $fmtDate(qcDetailData.inspectedAt) }}</el-descriptions-item>
          </el-descriptions>
          <div v-if="qcDetailData.records && qcDetailData.records.length" style="margin-top:12px">
            <h4 style="font-size:14px;margin-bottom:8px">检验记录</h4>
            <el-table :data="qcDetailData.records" border stripe size="small">
              <el-table-column label="类型" width="80" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.recordType === 'unqualified' ? 'danger' : 'success'" size="small">
                    {{ row.recordType === 'unqualified' ? '不合格' : '合格' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="quantity" label="数量" width="100" align="right" />
              <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />
            </el-table>
          </div>
        </template>
      </div>
    </el-dialog>

    <!-- 关联采购订单详情弹窗 -->
    <PurchaseOrderDetailDialog v-model:visible="orderDetailVisible" :related-order-no="orderDetailNo" inv-type="purchase_in" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PurchaseOrderDetailDialog from '@/components/PurchaseOrderDetailDialog.vue'
import { getPurchaseReceiptPage, getReceiptDetail, receiveReceipt, closeReceipt, getReceiptQcTasks, getReceiptInvTasks, exportPurchaseReceipts, printReceiptGroup, batchReceiveByGroup, getReceiptGroupPrintData } from '@/api/purchase'
import { getActivePrintTemplate } from '@/api/printTemplate'
import { ensureHiprintInit, hiprint } from '@/utils/hiprint'
import { getQcTaskDetail } from '@/api/quality'
import { getWarehouseList } from '@/api/system'

// ==================== 状态映射 ====================
const statusText = { pending_receive: '待收料', receiving: '收料中', received: '已收料', closed: '办结' }
const statusTag = { pending_receive: 'info', receiving: 'warning', received: 'success', closed: '' }
const qcStatusText = { none: '无质检', pending_qc: '待质检', qc_done: '已质检' }
const qcStatusTag = { none: 'info', pending_qc: 'warning', qc_done: 'success' }
const invTypeText = { purchase_in: '采购入库', purchase_in_qualified: '采购入库-质检合格', purchase_in_unqualified: '采购入库-质检不合格' }
const invStatusText = { pending: '待处理', partial: '部分处理', completed: '已完成' }
const invStatusTag = { pending: 'info', partial: 'warning', completed: 'success' }

// ==================== 列表 ====================
const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const queryParams = reactive({ receiptNo: '', poNo: '', supplierName: '', materialName: '', status: '', qcStatus: '', pageNum: 1, pageSize: 20 })

// ==================== 折叠模式 ====================
// 默认开启折叠. 开启时后端按 group 分页, total 与展示组数一致
const groupedMode = ref(true)
function onGroupedModeChange() {
  queryParams.pageNum = 1
  loadData()
}

const displayGroupedData = computed(() => {
  const groups = {}
  for (const r of tableData.value) {
    // 没有 groupId 的老数据按 receipt id 独立成组
    const key = r.receiptGroupId || `__single_${r.id}`
    if (!groups[key]) {
      groups[key] = {
        _key: key,
        receiptGroupId: r.receiptGroupId,
        poNo: r.poNo,
        purchaseOrderId: r.purchaseOrderId,
        supplierName: r.supplierName,
        createTime: r.createTime,
        items: []
      }
    }
    groups[key].items.push(r)
    // 取组内最早的 createTime
    if (r.createTime && r.createTime < groups[key].createTime) {
      groups[key].createTime = r.createTime
    }
  }
  return Object.values(groups).map(g => {
    const items = g.items
    const num = arr => arr.reduce((s, x) => s + Number(x || 0), 0)
    return {
      ...g,
      materialCount: items.length,
      pendingCount: items.filter(x => x.status === 'pending_receive').length,
      expectedTotal: num(items.map(x => x.expectedQty)),
      actualTotal: num(items.map(x => x.actualQty)),
      qualifiedTotal: num(items.map(x => x.qualifiedQty)),
      defectTotal: num(items.map(x => x.defectQty)),
      stockInTotal: num(items.map(x => x.stockInQty)),
      statusSummary: summarizeStatuses(items)
    }
  })
})

function summarizeStatuses(items) {
  const counts = {}
  for (const i of items) counts[i.status] = (counts[i.status] || 0) + 1
  return Object.entries(counts)
    .map(([s, c]) => `${statusText[s] || s} ${c}`)
    .join(' / ')
}

async function loadData() {
  loading.value = true
  try {
    const res = await getPurchaseReceiptPage({ ...queryParams, grouped: groupedMode.value })
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch {} finally { loading.value = false }
}

function handleSearch() { queryParams.pageNum = 1; loadData() }
function handleReset() {
  queryParams.receiptNo = ''; queryParams.poNo = ''; queryParams.supplierName = ''; queryParams.materialName = ''
  queryParams.status = ''; queryParams.qcStatus = ''; queryParams.pageNum = 1
  loadData()
}

async function handleExport() {
  try {
    const { receiptNo, poNo, supplierName, materialName, status, qcStatus } = queryParams
    const res = await exportPurchaseReceipts({ receiptNo, poNo, supplierName, materialName, status, qcStatus })
    const blob = res instanceof Blob ? res : new Blob([res])
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url; a.download = '采购收料单.xlsx'; a.click()
    URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('导出失败')
  }
}

// ==================== 仓库选项 ====================
const warehouseOptions = ref([])
async function loadWarehouseOptions() {
  try {
    const res = await getWarehouseList()
    warehouseOptions.value = res.data || []
  } catch {}
}

// ==================== 收料弹窗 ====================
const receiveVisible = ref(false)
const receiveLoading = ref(false)
const receiveRow = ref({})
const receiveForm = reactive({ warehouseId: null, needQc: false })

function openReceiveDialog(row) {
  receiveRow.value = row
  receiveForm.warehouseId = row.warehouseId || null
  receiveForm.needQc = false
  // 加载详情获取默认仓库
  getReceiptDetail(row.id).then(res => {
    const d = res.data || {}
    if (d.defaultWarehouseId && !receiveForm.warehouseId) {
      receiveForm.warehouseId = d.defaultWarehouseId
    }
  }).catch(() => {})
  receiveVisible.value = true
}

async function doReceive() {
  if (!receiveForm.warehouseId) { ElMessage.warning('请选择仓库'); return }
  receiveLoading.value = true
  try {
    await receiveReceipt(receiveRow.value.id, { warehouseId: receiveForm.warehouseId, needQc: receiveForm.needQc })
    ElMessage.success(receiveForm.needQc ? '已创建质检任务' : '已创建库存任务')
    receiveVisible.value = false
    loadData()
  } catch {} finally { receiveLoading.value = false }
}

// ==================== 整组汇总收料 ====================
const batchReceiveVisible = ref(false)
const batchReceiveLoading = ref(false)
const batchReceiveGroup = ref({})
const batchReceivePendingItems = ref([])
const batchReceiveForm = reactive({ warehouseId: null, needQc: false })

function openBatchReceive(group) {
  batchReceiveGroup.value = group
  batchReceivePendingItems.value = (group.items || []).filter(i => i.status === 'pending_receive')
  // 默认仓库取第一行物料的 warehouseId(若有)
  batchReceiveForm.warehouseId = batchReceivePendingItems.value[0]?.warehouseId || null
  batchReceiveForm.needQc = false
  batchReceiveVisible.value = true
}

async function doBatchReceive() {
  if (!batchReceiveForm.warehouseId) { ElMessage.warning('请选择目标仓库'); return }
  if (!batchReceiveGroup.value.receiptGroupId) { ElMessage.warning('该组无 group_id, 无法批量收料'); return }
  batchReceiveLoading.value = true
  try {
    const res = await batchReceiveByGroup(batchReceiveGroup.value.receiptGroupId, {
      warehouseId: batchReceiveForm.warehouseId,
      needQc: batchReceiveForm.needQc
    })
    ElMessage.success(`已${batchReceiveForm.needQc ? '创建质检任务' : '创建库存任务'} ${res.data || 0} 条`)
    batchReceiveVisible.value = false
    loadData()
  } catch {} finally { batchReceiveLoading.value = false }
}

// ==================== 打印对账单 (hiprint) ====================
async function handlePrintGroup(row) {
  if (!row.receiptGroupId) { ElMessage.warning('该收料单无关联批次组, 无法生成对账单'); return }
  try {
    // 1. 取启用的模板
    const tplRes = await getActivePrintTemplate('purchase_receipt_reconciliation')
    const tpl = tplRes.data
    if (!tpl || !tpl.templateJson) {
      ElMessage.warning('未配置"采购收料对账单"模板, 请到 系统设置→打印模板 新建并启用')
      return
    }
    // 2. 取业务数据
    const dataRes = await getReceiptGroupPrintData(row.receiptGroupId)
    const printData = dataRes.data || {}
    // 3. 渲染并调浏览器打印
    ensureHiprintInit()
    const t = new hiprint.PrintTemplate({ template: JSON.parse(tpl.templateJson) })
    const flat = { ...(printData.header || {}), items: printData.items || [] }

    // 合计行作为一行伪数据追加, 自动落到末页表格末尾
    flat.items = [
      ...flat.items,
      {
        seq: '', materialNo: '', materialName: '合 计', spec: '', unit: '',
        expectedQty: flat.totalExpected,
        actualQty: flat.totalActual,
        diffQty: flat.totalDiff
      }
    ]

    // 用绝对 URL 引 print-lock.css (hiprint iframe 是 about:blank, 相对路径无法解析).
    // vite base = '/mes-front-qianrui/', public 资源真实路径要带这个前缀.
    const lockCssUrl = window.location.origin + import.meta.env.BASE_URL + 'print-lock.css'

    // 用 :nth-last-child(n+2) 选所有"非最后一个" panel — 比 :not(:last-of-type) 在
    // 父级有混杂 tag 时更稳. 用户已确认签字栏元素 inline style 含 "top: 560pt".
    const styleHack = `
      <link href="${lockCssUrl}" media="print,screen" rel="stylesheet">
      <style>
        /* 强制每个 panel 之间分页 (覆盖 hiprint absolute 定位导致 page-break 失效) */
        .hiprint-printPanel,
        .hiprint-printPaper {
          position: relative !important;
          page-break-after: always !important;
          break-after: page !important;
        }
        .hiprint-printPanel:last-child,
        .hiprint-printPaper:last-child {
          page-break-after: auto !important;
          break-after: auto !important;
        }
        .hiprint-printElement-tableTarget td,
        .hiprint-printElement-tableTarget th {
          border: 0.75pt solid #cbd5e1 !important;
          padding: 5pt 8pt !important;
        }
        .hiprint-printElement-tableTarget thead th,
        .hiprint-printElement-tableTarget thead td {
          border-color: #1d4ed8 !important;
          background-color: #1d4ed8 !important;
          color: #fff !important;
          font-weight: 700 !important;
        }
        /* 签字栏只在末页显示: 列举三个模板各自签字栏的 top 坐标 */
        @media print, screen {
          /* A4 横向 (paperFooter 520) */
          .hiprint-printPanel:nth-last-child(n+2) [style*="top: 525pt"],
          .hiprint-printPanel:nth-last-child(n+2) [style*="top: 535pt"],
          .hiprint-printPanel:nth-last-child(n+2) [style*="top: 558pt"],
          /* A4 竖向 (paperFooter 780) */
          .hiprint-printPanel:nth-last-child(n+2) [style*="top: 785pt"],
          .hiprint-printPanel:nth-last-child(n+2) [style*="top: 792pt"],
          .hiprint-printPanel:nth-last-child(n+2) [style*="top: 812pt"],
          /* 80mm 小票 (paperFooter 480) */
          .hiprint-printPanel:nth-last-child(n+2) [style*="top: 485pt"],
          .hiprint-printPanel:nth-last-child(n+2) [style*="top: 493pt"],
          .hiprint-printPanel:nth-last-child(n+2) [style*="top: 510pt"],
          .hiprint-printPanel:nth-last-child(n+2) [style*="top: 527pt"],
          .hiprint-printPanel:nth-last-child(n+2) [style*="top: 547pt"] {
            display: none !important;
          }
        }
      </style>`
    // JS 手动切片分页 (绕开 hiprint 0.0.60 内部分页 bug):
    // 把 items 按 CHUNK_SIZE 切成多块, 每块单独走 t.getHtml 生成一个 panel, 合并后用 hiwprint 打印
    const CHUNK_SIZE = 15
    const items = flat.items || []
    let combined = window.jQuery()
    if (items.length === 0) {
      combined = t.getHtml(flat)
    } else {
      for (let i = 0; i < items.length; i += CHUNK_SIZE) {
        const chunk = { ...flat, items: items.slice(i, i + CHUNK_SIZE) }
        const $h = t.getHtml(chunk)
        combined = combined.add($h)
      }
    }
    const totalPages = Math.ceil(items.length / CHUNK_SIZE) || 1
    console.log('[hiprint debug] chunks=', totalPages,
                'panels=', combined.filter('.hiprint-printPanel').length + combined.find('.hiprint-printPanel').length)
    // hiwprint 内部用 jq.html() 只取第一个元素的 innerHTML, 多 panel 会丢失.
    // 用 wrapper div 包一层, 让所有 panel 作为 wrapper 的 children, .html() 返回拼接后的 HTML.
    const wrapper = window.jQuery('<div class="hiprint-printTemplate"></div>').append(combined)
    // 每个 chunk 独立 getHtml, 页码都是 "1-1". 这里手动重写为 "当前页 / 总页数"
    wrapper.find('.hiprint-paperNumber').each(function (i) {
      window.jQuery(this).text(`${i + 1} / ${totalPages}`)
    })
    wrapper.hiwprint({ styleHandler: () => styleHack })
  } catch (e) {
    ElMessage.error('打印失败: ' + (e?.message || ''))
  }
}

// 下载 Excel 备份 (旧逻辑保留, 当前 UI 不显示, 需要时可绑按钮)
// eslint-disable-next-line no-unused-vars
async function handleDownloadGroupExcel(row) {
  if (!row.receiptGroupId) return
  const blob = await printReceiptGroup(row.receiptGroupId)
  const url = window.URL.createObjectURL(new Blob([blob.data || blob]))
  const a = document.createElement('a')
  a.href = url
  a.download = `到货对账单_${row.poNo || row.receiptNo}_${row.receiptGroupId}.xlsx`
  a.click()
  window.URL.revokeObjectURL(url)
}

// ==================== 办结 ====================
async function handleClose(row) {
  try {
    await ElMessageBox.confirm(`确定办结收料单「${row.receiptNo}」？办结后不可再操作。`, '提示', { type: 'warning' })
    await closeReceipt(row.id)
    ElMessage.success('办结成功')
    loadData()
  } catch {}
}

// ==================== 详情抽屉 ====================
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailData = ref(null)
const detailTab = ref('qc')
const detailQcTasks = ref([])
const detailInvTasks = ref([])

async function openDetail(row) {
  detailVisible.value = true
  detailLoading.value = true
  detailTab.value = 'qc'
  try {
    const [detailRes, qcRes, invRes] = await Promise.all([
      getReceiptDetail(row.id),
      getReceiptQcTasks(row.id),
      getReceiptInvTasks(row.id)
    ])
    detailData.value = detailRes.data
    detailQcTasks.value = qcRes.data || []
    detailInvTasks.value = invRes.data || []
  } catch {} finally { detailLoading.value = false }
}

// 查看质检详情 — 弹窗展示
const qcDetailVisible = ref(false)
const qcDetailData = ref(null)
const qcDetailLoading = ref(false)
async function openQcDetail(row) {
  if (!row.id) return
  qcDetailVisible.value = true
  qcDetailLoading.value = true
  try {
    const res = await getQcTaskDetail(row.id)
    qcDetailData.value = res.data || {}
  } catch {} finally { qcDetailLoading.value = false }
}

// ==================== 初始化 ====================
// 关联订单详情
const orderDetailVisible = ref(false)
const orderDetailNo = ref('')
function openOrderDetail(poNo) {
  orderDetailNo.value = poNo
  orderDetailVisible.value = true
}

onMounted(() => { loadData(); loadWarehouseOptions() })
</script>

<style scoped lang="scss">
.page-container { display: flex; flex-direction: column; gap: 12px; }
.search-card { :deep(.el-card__body) { padding-bottom: 2px; } }
.table-card { flex: 1; }
.pagination-wrapper { display: flex; justify-content: flex-end; margin-top: 12px; }
</style>
