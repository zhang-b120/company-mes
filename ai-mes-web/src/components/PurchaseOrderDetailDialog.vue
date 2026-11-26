<template>
  <el-dialog
    v-model="innerVisible"
    :title="dialogTitle"
    width="820px"
    destroy-on-close
    append-to-body
    @open="onOpen"
  >
    <div v-if="loading" style="text-align:center;padding:40px 0">
      <el-icon class="is-loading" size="32"><Loading /></el-icon>
      <div style="margin-top:8px;color:#909399;font-size:13px">加载中...</div>
    </div>

    <el-empty v-else-if="notSupported" description="该业务类型暂未接入关联单据查询" :image-size="80" />
    <el-empty v-else-if="notFound" :description="notFoundText" :image-size="80" />

    <!-- 采购订单详情 -->
    <template v-else-if="docType === 'purchase' && detail.poNo">
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="订单编号">{{ detail.poNo }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ detail.supplierName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="联系人">{{ detail.contactName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="总金额">
          <span style="color:#e6a23c;font-weight:600">{{ fmtAmount(detail.totalAmount) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="交货日期">{{ detail.deliveryDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="付款方式">{{ detail.paymentTerms || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="poStatusTagType(detail.status)" size="small">{{ poStatusText(detail.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="审批意见" :span="2">{{ detail.approveRemark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="3">{{ detail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-tabs style="margin-top:16px">
        <el-tab-pane label="订单明细">
          <el-table :data="detail.items || []" border stripe size="small">
            <el-table-column prop="materialNo" label="物料编码" width="110" show-overflow-tooltip />
            <el-table-column prop="materialName" label="物料名称" min-width="120" show-overflow-tooltip />
            <el-table-column label="规格" width="90" show-overflow-tooltip>
              <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
            </el-table-column>
            <el-table-column prop="unit" label="单位" width="55" align="center" />
            <el-table-column prop="quantity" label="数量" width="85" align="right" />
            <el-table-column label="换算数量" width="120" align="right">
              <template #default="{ row }">
                <span v-if="secondaryDisplay(row.quantity, row.unitConfig)" style="color: #909399; font-size: 12px">
                  {{ secondaryDisplay(row.quantity, row.unitConfig) }}
                </span>
                <span v-else style="color: #c0c4cc">-</span>
              </template>
            </el-table-column>
            <el-table-column prop="unitPrice" label="单价" width="85" align="right" />
            <el-table-column label="金额" width="100" align="right">
              <template #default="{ row }">{{ row.amount?.toFixed(2) ?? '-' }}</template>
            </el-table-column>
            <el-table-column prop="invTaskQty" label="任务数量" width="85" align="right" />
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="收货记录">
          <el-table :data="detail.receipts || []" border stripe size="small">
            <el-table-column prop="receiptNo" label="收货单号" min-width="140" />
            <el-table-column prop="actualQty" label="收货数量" width="95" align="right" />
            <el-table-column prop="batchNo" label="批次号" width="110" />
            <el-table-column label="质检状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="qcStatusTagType(row.qcStatus)" size="small">
                  {{ qcStatusText(row.qcStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="时间" min-width="155" />
          </el-table>
          <el-empty v-if="!(detail.receipts?.length)" description="暂无收货记录" :image-size="50" />
        </el-tab-pane>
      </el-tabs>
    </template>

    <!-- 工单详情 -->
    <template v-else-if="docType === 'workorder' && detail.woNo">
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="工单编号">{{ detail.woNo }}</el-descriptions-item>
        <el-descriptions-item label="产品名称">{{ detail.productName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="产品编号">{{ detail.productNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="计划数量">{{ detail.planQty ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="已完成">{{ detail.completedQty ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="交货日期">{{ detail.deliveryDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="woStatusTagType(detail.status)" size="small">{{ woStatusText(detail.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="关联订单">{{ detail.orderNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="优先级">{{ detail.priority || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="3">{{ detail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-tabs style="margin-top:16px">
        <el-tab-pane label="工序任务">
          <el-table :data="detail.tasks || []" border stripe size="small">
            <el-table-column prop="stepNo" label="序号" width="60" align="center" />
            <el-table-column prop="stepName" label="工序名称" min-width="120" show-overflow-tooltip />
            <el-table-column prop="planQty" label="计划数" width="80" align="right" />
            <el-table-column prop="qualifiedQty" label="合格数" width="80" align="right" />
            <el-table-column prop="defectQty" label="不良数" width="80" align="right" />
            <el-table-column prop="assignedTeamName" label="指派班组" width="100" show-overflow-tooltip />
            <el-table-column prop="assignedUserName" label="指派人员" width="100" show-overflow-tooltip />
            <el-table-column label="状态" width="85" align="center">
              <template #default="{ row }">
                <el-tag :type="woTaskStatusType(row.status)" size="small">{{ woTaskStatusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!(detail.tasks?.length)" description="暂无工序任务" :image-size="50" />
        </el-tab-pane>
        <el-tab-pane label="BOM快照">
          <el-table :data="detail.bomSnapshots || []" border stripe size="small">
            <el-table-column prop="materialNo" label="物料编码" width="120" show-overflow-tooltip />
            <el-table-column prop="materialName" label="物料名称" min-width="130" show-overflow-tooltip />
            <el-table-column label="规格" width="100" show-overflow-tooltip>
              <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
            </el-table-column>
            <el-table-column prop="unit" label="单位" width="60" align="center" />
            <el-table-column prop="quantity" label="单件用量" width="90" align="right" />
            <el-table-column prop="issuedQty" label="已领用" width="80" align="right" />
          </el-table>
          <el-empty v-if="!(detail.bomSnapshots?.length)" description="暂无BOM数据" :image-size="50" />
        </el-tab-pane>
      </el-tabs>
    </template>

    <!-- 销售订单详情 -->
    <template v-else-if="docType === 'salesorder' && detail.orderNo">
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="订单编号">{{ detail.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="客户名称">{{ detail.customerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="联系人">{{ detail.contactName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="总金额">
          <span style="color:#e6a23c;font-weight:600">{{ fmtAmount(detail.totalAmount) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="交货日期">{{ detail.deliveryDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="结算方式">{{ detail.settlementType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="soStatusTagType(detail.status)" size="small">{{ soStatusText(detail.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="业务员">{{ detail.salesmanName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="回款金额">{{ fmtAmount(detail.paymentAmount) }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="3">{{ detail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <h4 style="font-size:14px;margin:16px 0 8px;color:#303133">订单明细</h4>
      <el-table :data="detail.items || []" border stripe size="small">
        <el-table-column prop="productName" label="产品名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="productCode" label="产品编号" width="110" show-overflow-tooltip />
        <el-table-column label="规格" width="90" show-overflow-tooltip>
              <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
            </el-table-column>
        <el-table-column prop="unit" label="单位" width="55" align="center" />
        <el-table-column prop="quantity" label="数量" width="80" align="right" />
        <el-table-column label="换算数量" width="120" align="right">
          <template #default="{ row }">
            <span v-if="secondaryDisplay(row.quantity, row.unitConfig)" style="color: #909399; font-size: 12px">
              {{ secondaryDisplay(row.quantity, row.unitConfig) }}
            </span>
            <span v-else style="color: #c0c4cc">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="unitPrice" label="单价" width="85" align="right" />
        <el-table-column label="金额" width="100" align="right">
          <template #default="{ row }">{{ row.amount != null ? Number(row.amount).toFixed(2) : '-' }}</template>
        </el-table-column>
        <el-table-column prop="shippedQty" label="已发货" width="75" align="right" />
      </el-table>
    </template>

    <!-- 仓库调拨详情 -->
    <template v-else-if="docType === 'transfer' && detail.transferNo">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="调拨单号">{{ detail.transferNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="transferStatusType(detail.status)" size="small">{{ transferStatusText(detail.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="源仓库">{{ detail.fromWarehouseName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="目标仓库">{{ detail.toWarehouseName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="申请人">{{ detail.applicantName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail.createTime || '-' }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.approveRemark" label="审批意见" :span="2">{{ detail.approveRemark }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.remark" label="备注" :span="2">{{ detail.remark }}</el-descriptions-item>
      </el-descriptions>
      <h4 style="font-size:14px;margin:16px 0 8px;color:#303133">调拨明细</h4>
      <el-table :data="detail.items || []" border stripe size="small">
        <el-table-column prop="materialNo" label="物料编号" width="120" show-overflow-tooltip />
        <el-table-column prop="materialName" label="物料名称" min-width="140" show-overflow-tooltip />
        <el-table-column label="规格" width="100" show-overflow-tooltip>
          <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="60" align="center" />
        <el-table-column prop="quantity" label="调拨数量" width="100" align="right" />
      </el-table>
    </template>

    <!-- 仓库盘点详情 -->
    <template v-else-if="docType === 'stocktaking' && detail.stocktakingNo">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="盘点单号">{{ detail.stocktakingNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="stocktakingStatusType(detail.status)" size="small">{{ stocktakingStatusText(detail.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="盘点仓库">{{ detail.warehouseName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="申请人">{{ detail.applicantName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail.createTime || '-' }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.approveRemark" label="审批意见">{{ detail.approveRemark }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.remark" label="备注" :span="2">{{ detail.remark }}</el-descriptions-item>
      </el-descriptions>
      <h4 style="font-size:14px;margin:16px 0 8px;color:#303133">盘点明细</h4>
      <el-table :data="detail.items || []" border stripe size="small">
        <el-table-column prop="materialNo" label="物料编号" width="120" show-overflow-tooltip />
        <el-table-column prop="materialName" label="物料名称" min-width="120" show-overflow-tooltip />
        <el-table-column label="规格" width="100" show-overflow-tooltip>
          <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="55" align="center" />
        <el-table-column prop="systemQty" label="在库数量" width="90" align="right" />
        <el-table-column prop="actualQty" label="实盘数量" width="90" align="right" />
        <el-table-column label="差异" width="90" align="right">
          <template #default="{ row }">
            <span :style="{ color: row.diffQty > 0 ? '#67c23a' : row.diffQty < 0 ? '#f56c6c' : '#909399', fontWeight: 600 }">
              {{ row.diffQty > 0 ? '+' : '' }}{{ row.diffQty }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="结果" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.diffQty > 0" type="success" size="small">盘盈</el-tag>
            <el-tag v-else-if="row.diffQty < 0" type="danger" size="small">盘亏</el-tag>
            <el-tag v-else type="info" size="small">一致</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <!-- 入库/出库申请详情 -->
    <template v-else-if="docType === 'inv_request' && detail.requestNo">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="申请单号">{{ detail.requestNo }}</el-descriptions-item>
        <el-descriptions-item label="出入库类型">{{ invRequestTypeText(detail.invType) }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.customerName" label="退货客户">{{ detail.customerName }}</el-descriptions-item>
        <el-descriptions-item label="关联单据号">{{ detail.relatedOrderNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="批次号">{{ detail.batchNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="仓库">{{ detail.warehouseName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="invRequestStatusType(detail.status)" size="small">{{ invRequestStatusText(detail.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail.createTime || '-' }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.remark" label="备注" :span="2">{{ detail.remark }}</el-descriptions-item>
      </el-descriptions>
      <h4 style="font-size:14px;margin:16px 0 8px;color:#303133">物料明细</h4>
      <el-table :data="detail.items || []" border stripe size="small">
        <el-table-column prop="materialNo" label="物料编号" width="120" show-overflow-tooltip />
        <el-table-column prop="materialName" label="物料名称" min-width="130" show-overflow-tooltip />
        <el-table-column label="规格" width="100" show-overflow-tooltip>
          <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="55" align="center" />
        <el-table-column prop="quantity" label="数量" width="85" align="right" />
        <el-table-column prop="unitPrice" label="单价" width="85" align="right" />
        <el-table-column prop="totalPrice" label="总价" width="95" align="right" />
        <el-table-column prop="warehouseName" label="仓库" width="100" show-overflow-tooltip />
      </el-table>
    </template>

    <!-- 外协订单详情 -->
    <template v-else-if="docType === 'outsource' && detail.orderNo">
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="外协单号">{{ detail.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ detail.supplierName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="产品">{{ detail.productName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="工序">{{ detail.stepName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="数量">{{ detail.totalQty ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="单价">{{ detail.unitPrice != null ? `¥${Number(detail.unitPrice).toFixed(2)}` : '-' }}</el-descriptions-item>
        <el-descriptions-item label="总金额">
          <span style="color:#e6a23c;font-weight:600">{{ fmtAmount(detail.totalAmount) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="交货日期">{{ detail.deliveryDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="osStatusTagType(detail.status)" size="small">{{ osStatusText(detail.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="已发货">{{ detail.deliveredQty ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="已收货合格">{{ detail.qualifiedQty ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-tabs style="margin-top:16px">
        <el-tab-pane label="发货记录">
          <el-table :data="detail.deliveries || []" border stripe size="small">
            <el-table-column prop="deliveryNo" label="发货单号" min-width="140" />
            <el-table-column prop="deliveryType" label="类型" width="80" align="center">
              <template #default="{ row }">{{ row.deliveryType === 'material' ? '原料' : '半成品' }}</template>
            </el-table-column>
            <el-table-column prop="deliveryQty" label="发货数量" width="100" align="right" />
            <el-table-column prop="status" label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'completed' ? 'success' : 'warning'" size="small">{{ row.status === 'completed' ? '已完成' : '待处理' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="时间" min-width="155" />
          </el-table>
          <el-empty v-if="!(detail.deliveries?.length)" description="暂无发货记录" :image-size="50" />
        </el-tab-pane>
        <el-tab-pane label="收货记录">
          <el-table :data="detail.receipts || []" border stripe size="small">
            <el-table-column prop="receiptNo" label="收货单号" min-width="140" />
            <el-table-column prop="receivedQty" label="收货数量" width="100" align="right" />
            <el-table-column label="质检状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="qcStatusTagType(row.qcStatus)" size="small">
                  {{ qcStatusText(row.qcStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="时间" min-width="155" />
          </el-table>
          <el-empty v-if="!(detail.receipts?.length)" description="暂无收货记录" :image-size="50" />
        </el-tab-pane>
      </el-tabs>
    </template>

    <template #footer>
      <el-button @click="innerVisible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import { getPurchaseOrderDetailByNo } from '@/api/purchase'
import { getWorkOrderDetailByNo } from '@/api/production'
import { getSalesOrderDetailByNo } from '@/api/order'
import { getOutsourceOrderDetailByNo } from '@/api/outsource'
import { getTransferDetailByNo, getStocktakingDetailByNo, getInvRequestDetailByNo } from '@/api/inventory'

const props = defineProps({
  visible: { type: Boolean, default: false },
  relatedOrderNo: { type: String, default: '' },
  invType: { type: String, default: '' }
})
const emit = defineEmits(['update:visible'])

const innerVisible = ref(false)
const loading = ref(false)
const notSupported = ref(false)
const notFound = ref(false)
const detail = ref({})
const docType = ref('')

watch(() => props.visible, v => { innerVisible.value = v })
watch(innerVisible, v => { emit('update:visible', v) })

// invType → 文档类型映射
const TYPE_MAP = {
  purchase_in: 'purchase',
  production_in: 'workorder',
  semi_in: 'workorder',
  production_out: 'workorder',
  sales_out: 'salesorder',
  outsource_in: 'outsource',
  outsource_out: 'outsource',
  transfer_in: 'transfer',
  transfer_out: 'transfer',
  stocktaking_in: 'stocktaking',
  stocktaking_out: 'stocktaking'
}

const TITLE_MAP = {
  purchase: '采购订单详情',
  workorder: '生产工单详情',
  salesorder: '销售订单详情',
  outsource: '外协订单详情',
  transfer: '仓库调拨详情',
  stocktaking: '仓库盘点详情',
  inv_request: '出入库申请详情'
}

const dialogTitle = computed(() => TITLE_MAP[docType.value] || '关联单据详情')
const notFoundText = computed(() => {
  const nameMap = { purchase: '采购订单', workorder: '工单', salesorder: '销售订单', outsource: '外协订单', transfer: '调拨单', stocktaking: '盘点单', inv_request: '出入库申请' }
  return `未找到对应${nameMap[docType.value] || '单据'}`
})

async function onOpen() {
  detail.value = {}
  notSupported.value = false
  notFound.value = false

  // 关联单据号以 INR 开头 → 入库/出库申请
  if (props.relatedOrderNo && props.relatedOrderNo.startsWith('INR')) {
    docType.value = 'inv_request'
  } else {
    docType.value = TYPE_MAP[props.invType] || ''
  }

  if (!docType.value) {
    notSupported.value = true
    return
  }
  if (!props.relatedOrderNo) {
    notFound.value = true
    return
  }

  loading.value = true
  try {
    let res
    if (docType.value === 'purchase') {
      res = await getPurchaseOrderDetailByNo(props.relatedOrderNo)
      detail.value = res.data || {}
      if (!detail.value.poNo) notFound.value = true
    } else if (docType.value === 'workorder') {
      res = await getWorkOrderDetailByNo(props.relatedOrderNo)
      detail.value = res.data || {}
      if (!detail.value.woNo) notFound.value = true
    } else if (docType.value === 'salesorder') {
      res = await getSalesOrderDetailByNo(props.relatedOrderNo)
      detail.value = res.data || {}
      if (!detail.value.orderNo) notFound.value = true
    } else if (docType.value === 'outsource') {
      res = await getOutsourceOrderDetailByNo(props.relatedOrderNo)
      detail.value = res.data || {}
      if (!detail.value.orderNo) notFound.value = true
    } else if (docType.value === 'transfer') {
      res = await getTransferDetailByNo(props.relatedOrderNo)
      detail.value = res.data || {}
      if (!detail.value.transferNo) notFound.value = true
    } else if (docType.value === 'stocktaking') {
      res = await getStocktakingDetailByNo(props.relatedOrderNo)
      detail.value = res.data || {}
      if (!detail.value.stocktakingNo) notFound.value = true
    } else if (docType.value === 'inv_request') {
      res = await getInvRequestDetailByNo(props.relatedOrderNo)
      detail.value = res.data || {}
      if (!detail.value.requestNo) notFound.value = true
    }
  } catch {
    notFound.value = true
  } finally {
    loading.value = false
  }
}

function secondaryDisplay(qty, unitConfig) {
  if (!qty || !unitConfig) return ''
  try {
    const cfg = JSON.parse(unitConfig)
    const c = cfg.conversions?.[0]
    if (c) return `${(qty * c.ratio).toFixed(4)} ${c.unit}`
  } catch {}
  return ''
}

function fmtAmount(val) {
  return val != null ? `¥${Number(val).toFixed(2)}` : '-'
}

// 采购订单状态
function poStatusTagType(s) {
  return { draft: 'info', pending_approval: 'warning', confirmed: '', rejected: 'danger', partial_received: 'warning', received: 'success' }[s] || 'info'
}
function poStatusText(s) {
  return { draft: '草稿', pending_approval: '待审批', confirmed: '已确认', rejected: '已驳回', partial_received: '部分收货', received: '已收货' }[s] || s || '-'
}

// 工单状态
function woStatusTagType(s) {
  return { pending: 'info', scheduled: '', in_progress: 'warning', completed: 'success', pending_stock_in: 'warning', finished: 'success', closed: 'info', cancelled: 'danger' }[s] || 'info'
}
function woStatusText(s) {
  return { pending: '待排产', scheduled: '已排产', in_progress: '生产中', completed: '已完成', pending_stock_in: '待入库', finished: '已入库', closed: '已关闭', cancelled: '已取消' }[s] || s || '-'
}
function woTaskStatusType(s) {
  return { pending: 'info', ready: '', in_progress: 'warning', pending_qc: 'warning', completed: 'success', blocked: 'danger', cancelled: 'danger' }[s] || 'info'
}
function woTaskStatusText(s) {
  return { pending: '待开工', ready: '可开工', in_progress: '进行中', pending_qc: '待质检', completed: '已完成', blocked: '已阻塞', cancelled: '已取消' }[s] || s || '-'
}

// 销售订单状态
function soStatusTagType(s) {
  return { draft: 'info', pending_approval: 'warning', confirmed: '', mrp_planned: '', outsourcing: 'warning', in_production: '', partial_shipped: '', shipped: 'success', completed: 'success', settled: 'success', voided: 'info', cancelled: 'danger' }[s] || 'info'
}
function soStatusText(s) {
  return { draft: '草稿', pending_approval: '待审批', confirmed: '已确认', mrp_planned: '已排产', outsourcing: '外协中', in_production: '生产中', partial_shipped: '部分发货', shipped: '已发货', completed: '已完成', settled: '已结算', voided: '已作废', cancelled: '已取消' }[s] || s || '-'
}

// 入库/出库申请状态
function invRequestStatusType(s) {
  return { pending: 'warning', approved: 'success', rejected: 'danger', task_created: 'info' }[s] || 'info'
}
function invRequestStatusText(s) {
  return { pending: '待审核', approved: '已审批', rejected: '已驳回', task_created: '已生成任务' }[s] || s || '-'
}
function invRequestTypeText(v) {
  const map = {
    purchase_in: '采购入库', production_in: '生产入库', semi_in: '半成品入库',
    outsource_in: '外协收货', outsource_material_in: '外协来料', return_in: '退货入库', other_in: '其他入库',
    production_out: '生产领料', sales_out: '销售发货', outsource_out: '外协发料',
    scrap_out: '报废出库', other_out: '其他出库'
  }
  return map[v] || v || '-'
}

// 调拨单状态
function transferStatusType(s) {
  return { pending: 'warning', completed: 'success', rejected: 'danger' }[s] || 'info'
}
function transferStatusText(s) {
  return { pending: '待审批', completed: '已完成', rejected: '已驳回' }[s] || s || '-'
}

// 盘点单状态
function stocktakingStatusType(s) {
  return { pending: 'warning', approved: 'success', rejected: 'danger' }[s] || 'info'
}
function stocktakingStatusText(s) {
  return { pending: '待审批', approved: '已通过', rejected: '已驳回' }[s] || s || '-'
}

// 外协订单状态
function osStatusTagType(s) {
  return { draft: 'info', pending_approval: 'warning', confirmed: 'primary', material_sent: '', processing: 'warning',
           partial_received: 'warning', partial_receipt: 'warning', fully_receipt: 'success', received: '',
           completed: 'success', settled: 'success', cancelled: 'danger' }[s] || 'info'
}
function osStatusText(s) {
  return { draft: '草稿', pending_approval: '待审批', confirmed: '已确认', material_sent: '已发料', processing: '加工中',
           partial_received: '部分收货', partial_receipt: '部分收料', fully_receipt: '全部收料', received: '已收货',
           completed: '已完成', settled: '已结算', cancelled: '已取消' }[s] || s || '-'
}
// 质检状态
function qcStatusTagType(s) {
  return { none: 'info', pending: 'warning', pending_qc: 'warning', passed: 'success', qc_done: 'success', failed: 'danger' }[s] || 'info'
}
function qcStatusText(s) {
  return { none: '无质检', pending: '待质检', pending_qc: '待质检', passed: '已通过', qc_done: '已质检', failed: '不合格' }[s] || s || '-'
}
</script>
