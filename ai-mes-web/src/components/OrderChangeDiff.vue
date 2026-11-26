<template>
  <div v-loading="loading">
    <template v-if="diff">
      <el-alert v-if="!hasProposed" type="info" :closable="false" show-icon style="margin-bottom: 12px;">
        尚未保存任何变更草稿, 仅显示当前订单原始内容
      </el-alert>

      <!-- 变更摘要清单: 一行一条"X: 旧 → 新", 让审批人一眼扫过 -->
      <h4 class="section-title">变更摘要</h4>
      <div v-if="summaryLines.length === 0" class="summary-empty">无任何变更</div>
      <ul v-else class="summary-list">
        <li v-for="(line, i) in summaryLines" :key="i" :class="line.kind">
          <el-tag :type="lineTagType(line.kind)" size="small" effect="light" style="margin-right: 8px;">
            {{ lineTagText(line.kind) }}
          </el-tag>
          <span class="summary-label">{{ line.label }}:</span>
          <template v-if="line.kind === 'added'">
            <span class="val-new">{{ formatVal(line.newVal) }}</span>
          </template>
          <template v-else-if="line.kind === 'removed'">
            <span class="val-old">{{ formatVal(line.oldVal) }}</span>
          </template>
          <template v-else>
            <span class="val-old">{{ formatVal(line.oldVal) }}</span>
            <span class="arrow">→</span>
            <span class="val-new">{{ formatVal(line.newVal) }}</span>
          </template>
        </li>
      </ul>

      <!-- 订单级字段对比 -->
      <h4 class="section-title" style="margin-top: 16px;">订单字段</h4>
      <el-table :data="orderFieldRows" border stripe size="small" :row-class-name="orderRowClass">
        <el-table-column prop="label" label="字段" width="120" />
        <el-table-column label="原值" min-width="180">
          <template #default="{ row }">
            <span :class="{ 'val-old': row.changed }">{{ formatVal(row.oldVal) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="新值" min-width="180">
          <template #default="{ row }">
            <span :class="{ 'val-new': row.changed }">{{ formatVal(row.newVal) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.changed" type="warning" size="small">已修改</el-tag>
            <span v-else style="color: #c0c4cc">不变</span>
          </template>
        </el-table-column>
      </el-table>

      <!-- 订单明细对比 -->
      <h4 class="section-title" style="margin-top: 16px;">订单明细</h4>
      <el-table :data="itemRows" border stripe size="small" :row-class-name="itemRowClass">
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.diffStatus === 'added'" type="success" size="small">新增</el-tag>
            <el-tag v-else-if="row.diffStatus === 'removed'" type="danger" size="small">删除</el-tag>
            <el-tag v-else-if="row.diffStatus === 'modified'" type="warning" size="small">修改</el-tag>
            <span v-else style="color: #c0c4cc">不变</span>
          </template>
        </el-table-column>
        <el-table-column label="产品名称" min-width="180">
          <template #default="{ row }">
            <div v-if="row.productChanged">
              <div class="val-old">原: {{ row.oldProductName || '-' }}</div>
              <div class="val-new">新: {{ row.newProductName || '-' }}</div>
            </div>
            <div v-else>{{ row.newProductName || row.oldProductName || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="产品编号" min-width="160">
          <template #default="{ row }">
            <div v-if="row.productChanged">
              <div class="val-old">原: {{ row.oldProductNo || '-' }}</div>
              <div class="val-new">新: {{ row.newProductNo || '-' }}</div>
            </div>
            <div v-else>{{ row.newProductNo || row.oldProductNo || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="规格" min-width="180">
          <template #default="{ row }">
            <div v-if="row.productChanged">
              <div class="val-old">原: {{ formatSpec(row.oldProductSpec) || '-' }}</div>
              <div class="val-new">新: {{ formatSpec(row.newProductSpec) || '-' }}</div>
            </div>
            <div v-else>{{ formatSpec(row.newProductSpec || row.oldProductSpec) || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="数量" width="160">
          <template #default="{ row }">
            <div v-if="row.qtyChanged">
              <span class="val-old">{{ formatVal(row.oldQty) }}</span>
              <span style="margin: 0 6px; color: #909399;">→</span>
              <span class="val-new">{{ formatVal(row.newQty) }}</span>
            </div>
            <span v-else>{{ formatVal(row.newQty || row.oldQty) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="单价" width="140">
          <template #default="{ row }">
            <div v-if="row.priceChanged">
              <span class="val-old">{{ formatVal(row.oldPrice) }}</span>
              <span style="margin: 0 6px; color: #909399;">→</span>
              <span class="val-new">{{ formatVal(row.newPrice) }}</span>
            </div>
            <span v-else>{{ formatVal(row.newPrice || row.oldPrice) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="交期" width="140">
          <template #default="{ row }">
            <div v-if="row.deliveryChanged">
              <span class="val-old">{{ formatVal(row.oldDelivery) }}</span>
              <span style="margin: 0 6px; color: #909399;">→</span>
              <span class="val-new">{{ formatVal(row.newDelivery) }}</span>
            </div>
            <span v-else>{{ formatVal(row.newDelivery || row.oldDelivery) }}</span>
          </template>
        </el-table-column>
      </el-table>

      <!-- 变更元信息 -->
      <div class="meta-row" v-if="change">
        <span><b>变更类型:</b> {{ changeTypeLabel(change.changeType) }}</span>
        <span><b>变更原因:</b> {{ change.changeReason || '-' }}</span>
        <span v-if="change.approvedAt"><b>审批时间:</b> {{ change.approvedAt }}</span>
        <span v-if="change.approveRemark"><b>审批意见:</b> {{ change.approveRemark }}</span>
      </div>
    </template>
    <el-empty v-else-if="!loading" description="暂无变更对比数据" />
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { getOrderChangeDiff } from '@/api/order'
import { formatSpec } from '@/utils/format'

const props = defineProps({
  changeId: { type: [Number, String], default: null },
  // 也支持传入已加载的 diff data, 不重复请求
  diffData: { type: Object, default: null }
})

const loading = ref(false)
const diff = ref(null)

watch(() => props.changeId, async (id) => {
  if (props.diffData) { diff.value = props.diffData; return }
  if (!id) { diff.value = null; return }
  loading.value = true
  try {
    const res = await getOrderChangeDiff(id)
    diff.value = res.data || null
  } finally { loading.value = false }
}, { immediate: true })

watch(() => props.diffData, (d) => { if (d) diff.value = d })

const change = computed(() => diff.value?.change)
const original = computed(() => diff.value?.original || {})
// 提议态没保存时, 用 original 作为兜底, 整张表显示"不变"
const hasProposed = computed(() => !!diff.value?.proposed)
const proposed = computed(() => diff.value?.proposed || diff.value?.original || {})

// === 订单级字段对比 ===
const ORDER_FIELD_DEFS = [
  { key: 'orderNo', label: '订单号' },
  { key: 'customerId', label: '客户ID' },
  { key: 'contactName', label: '联系人' },
  { key: 'shippingAddress', label: '收货地址' },
  { key: 'settlementType', label: '结算方式' },
  { key: 'salesmanId', label: '业务员ID' },
  { key: 'deliveryDate', label: '交货日期' },
  { key: 'remark', label: '备注' }
]

const orderFieldRows = computed(() => {
  const o = original.value.order || {}
  const n = proposed.value.order || {}
  return ORDER_FIELD_DEFS.map(def => {
    const oldVal = o[def.key]
    const newVal = n[def.key]
    return {
      key: def.key, label: def.label,
      oldVal, newVal,
      changed: !valueEqual(oldVal, newVal)
    }
  })
})

// === 订单明细对比 ===
const itemRows = computed(() => {
  const oldItems = original.value.items || []
  const newItems = proposed.value.items || []
  const oldById = new Map(oldItems.filter(i => i.id != null).map(i => [i.id, i]))
  const newById = new Map(newItems.filter(i => i.id != null).map(i => [i.id, i]))
  // union of all ids
  const allIds = new Set([...oldById.keys(), ...newById.keys()])
  const rows = []
  for (const id of allIds) {
    const oi = oldById.get(id)
    const ni = newById.get(id)
    rows.push(buildItemRow(oi, ni))
  }
  // 加上"新增行"(没 id 的新 item)
  newItems.filter(i => i.id == null).forEach(ni => rows.push(buildItemRow(null, ni)))
  return rows
})

function buildItemRow(oi, ni) {
  if (!oi && ni) {
    return {
      diffStatus: 'added',
      newProductId: ni.productId, newProductName: ni.productName || ni.materialName,
      newProductNo: ni.productNo || ni.productCode || ni.materialNo,
      newProductSpec: ni.productSpec || ni.spec,
      newQty: ni.quantity, newPrice: ni.unitPrice, newDelivery: ni.deliveryDate,
      productChanged: false, qtyChanged: false, priceChanged: false, deliveryChanged: false
    }
  }
  if (oi && !ni) {
    return {
      diffStatus: 'removed',
      oldProductId: oi.productId, oldProductName: oi.productName || oi.materialName,
      oldProductNo: oi.productNo || oi.productCode || oi.materialNo,
      oldProductSpec: oi.productSpec || oi.spec,
      oldQty: oi.quantity, oldPrice: oi.unitPrice, oldDelivery: oi.deliveryDate,
      productChanged: false, qtyChanged: false, priceChanged: false, deliveryChanged: false
    }
  }
  // both exist
  const productChanged = !valueEqual(oi.productId, ni.productId)
  const qtyChanged = !valueEqual(oi.quantity, ni.quantity)
  const priceChanged = !valueEqual(oi.unitPrice, ni.unitPrice)
  const deliveryChanged = !valueEqual(oi.deliveryDate, ni.deliveryDate)
  const anyChanged = productChanged || qtyChanged || priceChanged || deliveryChanged
  return {
    diffStatus: anyChanged ? 'modified' : 'unchanged',
    oldProductId: oi.productId, oldProductName: oi.productName || oi.materialName,
    oldProductNo: oi.productNo || oi.productCode || oi.materialNo,
    oldProductSpec: oi.productSpec || oi.spec,
    newProductId: ni.productId, newProductName: ni.productName || ni.materialName,
    newProductNo: ni.productNo || ni.productCode || ni.materialNo,
    newProductSpec: ni.productSpec || ni.spec,
    oldQty: oi.quantity, newQty: ni.quantity,
    oldPrice: oi.unitPrice, newPrice: ni.unitPrice,
    oldDelivery: oi.deliveryDate, newDelivery: ni.deliveryDate,
    productChanged, qtyChanged, priceChanged, deliveryChanged
  }
}

function valueEqual(a, b) {
  if (a === b) return true
  if (a == null && b == null) return true
  if (a == null || b == null) return false
  // 数字宽容比较
  const na = Number(a), nb = Number(b)
  if (!isNaN(na) && !isNaN(nb)) return na === nb
  return String(a) === String(b)
}

// === 变更摘要清单 ===
// 每行 { kind: 'modified'|'added'|'removed', label: '...', oldVal, newVal }
const summaryLines = computed(() => {
  const lines = []
  // 1. 订单级字段
  for (const row of orderFieldRows.value) {
    if (row.changed) {
      lines.push({ kind: 'modified', label: row.label, oldVal: row.oldVal, newVal: row.newVal })
    }
  }
  // 2. 明细
  for (const row of itemRows.value) {
    const productName = row.newProductName || row.oldProductName || '产品'
    if (row.diffStatus === 'added') {
      lines.push({
        kind: 'added',
        label: `新增明细 [${productName}]`,
        newVal: `数量 ${formatVal(row.newQty)}, 单价 ${formatVal(row.newPrice)}`
      })
    } else if (row.diffStatus === 'removed') {
      lines.push({
        kind: 'removed',
        label: `删除明细 [${row.oldProductName || '产品'}]`,
        oldVal: `数量 ${formatVal(row.oldQty)}`
      })
    } else if (row.diffStatus === 'modified') {
      // 单条 item 内可能多列变化, 都打成独立 line
      // 产品变更: 用 "原产品" 作为锚点更直观
      const anchor = row.oldProductName || row.newProductName || '产品'
      if (row.productChanged) {
        lines.push({ kind: 'modified', label: `明细 [${anchor}] 产品替换`, oldVal: row.oldProductName, newVal: row.newProductName })
      }
      if (row.qtyChanged) {
        lines.push({ kind: 'modified', label: `[${productName}] 数量`, oldVal: row.oldQty, newVal: row.newQty })
      }
      if (row.priceChanged) {
        lines.push({ kind: 'modified', label: `[${productName}] 单价`, oldVal: row.oldPrice, newVal: row.newPrice })
      }
      if (row.deliveryChanged) {
        lines.push({ kind: 'modified', label: `[${productName}] 交期`, oldVal: row.oldDelivery, newVal: row.newDelivery })
      }
    }
  }
  return lines
})

function lineTagType(kind) {
  return { added: 'success', removed: 'danger', modified: 'warning' }[kind] || ''
}
function lineTagText(kind) {
  return { added: '新增', removed: '删除', modified: '修改' }[kind] || '变更'
}

function orderRowClass({ row }) { return row.changed ? 'changed-row' : '' }
function itemRowClass({ row }) {
  if (row.diffStatus === 'added') return 'row-added'
  if (row.diffStatus === 'removed') return 'row-removed'
  if (row.diffStatus === 'modified') return 'row-modified'
  return ''
}

function formatVal(v) {
  if (v === null || v === undefined || v === '') return '-'
  return String(v)
}

const CHANGE_TYPE_LABEL = {
  qty_increase: '数量增加', qty_decrease: '数量减少',
  date_change: '交期变更', cancel: '订单取消',
  product_replace: '产品替换', add_product: '新增产品行', add_item: '新增产品行',
  quantity: '数量变更', delivery_date: '交期变更', price: '价格变更',
  product: '产品变更', other: '其他变更'
}
function changeTypeLabel(t) { return CHANGE_TYPE_LABEL[t] || t || '订单变更' }
</script>

<style scoped>
.section-title {
  margin: 0 0 8px 0;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.summary-list {
  margin: 0 0 12px 0;
  padding: 12px 16px;
  background: #fafcff;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  list-style: none;
  font-size: 13px;
  line-height: 2;
}
.summary-list li { display: flex; align-items: center; flex-wrap: wrap; }
.summary-label { font-weight: 600; color: #303133; margin-right: 8px; }
.arrow { margin: 0 8px; color: #909399; font-weight: bold; }
.summary-empty {
  margin: 0 0 12px 0;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 4px;
  color: #909399;
  font-size: 13px;
  text-align: center;
}
.val-old {
  color: #f56c6c;
  text-decoration: line-through;
}
.val-new {
  color: #67c23a;
  font-weight: 600;
}
:deep(.changed-row) { background: #fef6ed !important; }
:deep(.row-added) { background: #f0f9eb !important; }
:deep(.row-removed) { background: #fef0f0 !important; }
:deep(.row-modified) { background: #fef6ed !important; }
.meta-row {
  margin-top: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 4px;
  font-size: 13px;
  color: #606266;
}
.meta-row b { color: #303133; margin-right: 4px; }
</style>
