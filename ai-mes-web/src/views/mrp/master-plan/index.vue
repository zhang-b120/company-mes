<template>
  <div class="master-plan-page">
    <el-card shadow="never">
      <!-- 顶部操作区 -->
      <div class="top-bar">
        <div class="left">
          <el-input v-model="keyword" placeholder="按订单号 / 客户 / 物料 搜索"
            clearable style="width: 280px" :disabled="!calculated" />
          <el-tag v-if="!calculated" type="warning" effect="plain" style="margin-left: 12px">
            请先点击「计算」加载物料展开数据
          </el-tag>
        </div>
        <div class="right">
          <el-button type="primary" :icon="Refresh" :loading="calcLoading" @click="doCalculate">
            {{ calculated ? '重新计算' : '计算' }}
          </el-button>
        </div>
      </div>

      <!-- 订单列表 -->
      <el-table :data="filteredOrders" v-loading="listLoading" border stripe size="small"
        row-key="id" style="margin-top: 12px">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand-tree-wrapper">
              <div v-if="!calculated" class="expand-hint">请先点击上方「计算」按钮</div>
              <div v-else-if="row._loading" class="expand-hint">计算中...</div>
              <div v-else-if="!row._tree?.length" class="expand-hint">该订单无可展开物料</div>
              <el-table v-else :data="filterTreeByKeyword(row._tree)" row-key="rowKey"
                :tree-props="{ children: 'children' }" default-expand-all border size="small">
                <el-table-column prop="materialNo" label="物料编号" width="170" show-overflow-tooltip />
                <el-table-column prop="materialName" label="物料名称" min-width="150" show-overflow-tooltip />
                <el-table-column label="规格" width="110" show-overflow-tooltip>
                  <template #default="{ row: n }">{{ $fmtSpec(n.spec) }}</template>
                </el-table-column>
                <el-table-column prop="unit" label="单位" width="55" align="center" />
                <el-table-column label="类型" width="70" align="center">
                  <template #default="{ row: n }">
                    <el-tag size="small" :type="n.sourceType === 'manufacture' ? 'warning' : 'info'">
                      {{ n.sourceType === 'manufacture' ? '自制' : '外购' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="需求量" width="90" align="right">
                  <template #default="{ row: n }">{{ fmtQty(n.requiredQty) }}</template>
                </el-table-column>
                <el-table-column label="可用库存" width="90" align="right">
                  <template #default="{ row: n }">
                    <span :style="{ color: Number(n.inventoryQty) > 0 ? '#67c23a' : '#909399' }">
                      {{ fmtQty(n.inventoryQty) }}
                    </span>
                  </template>
                </el-table-column>
                <el-table-column label="在途" width="80" align="right">
                  <template #default="{ row: n }">
                    <el-tooltip :content="n.sourceType === 'manufacture' ? '生产在途' : '采购在途'">
                      <span>{{ fmtQty(n.inTransitQty) }}</span>
                    </el-tooltip>
                  </template>
                </el-table-column>
                <el-table-column label="缺口" width="90" align="right">
                  <template #default="{ row: n }">
                    <span :style="{ color: Number(n.shortageQty) > 0 ? '#f56c6c' : '#909399', fontWeight: 500 }">
                      {{ fmtQty(n.shortageQty) }}
                    </span>
                  </template>
                </el-table-column>
                <el-table-column label="状态" width="160" align="center">
                  <template #default="{ row: n }">
                    <el-tag size="small" :type="statusTagType(n)">{{ n.statusLabel }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="290" align="center" fixed="right">
                  <template #default="{ row: n }">
                    <el-button v-if="n.canConvertPo" link type="primary" size="small"
                      @click="goConvertToPo(row, n)">转采购申请</el-button>
                    <el-button v-if="n.canConvertWo" link type="success" size="small"
                      @click="goConvertToWo(row, n)">转车间计划</el-button>
                    <el-button
                      v-if="n.substituteOptions?.length"
                      link type="warning" size="small"
                      @click="openSubstituteDialog(row, n)">
                      代替料{{ n._selectedSubstitute ? '(已选)' : '' }}
                    </el-button>
                    <el-button v-if="n._selectedSubstitute" link type="info" size="small"
                      @click="clearSubstitute(n)">取消代替料</el-button>
                    <span v-if="!n.canConvertPo && !n.canConvertWo && !n.substituteOptions?.length && !n._selectedSubstitute" style="color:#c0c4cc">-</span>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="orderNo" label="订单号" min-width="150">
          <template #default="{ row }">
            <el-button link type="primary" @click="goOrderDetail(row)">{{ row.orderNo }}</el-button>
          </template>
        </el-table-column>
        <el-table-column prop="customerName" label="客户名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="订单总额" width="120" align="right">
          <template #default="{ row }">¥{{ Number(row.totalAmount || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="deliveryDate" label="交货日期" width="120" align="center" />
        <el-table-column label="订单状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small">{{ statusMap[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="加入时间" width="160" align="center">
          <template #default="{ row }">{{ (row.masterPlanAddedAt || '').replace('T', ' ').substring(0, 16) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="danger" size="small" @click="onRemove(row)">移出主计划</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!listLoading && !orderList.length" description="暂无订单, 请到销售订单详情页点击「转主计划」加入" />
    </el-card>

    <!-- ========== 转车间计划 / 转采购申请 弹窗 ========== -->
    <el-dialog v-model="convertVisible"
      :title="convertType === 'wo' ? '转车间计划' : '转采购申请'"
      width="600px" destroy-on-close
      append-to-body>
      <div v-if="convertNode" class="convert-dialog">
        <!-- 物料概览 -->
        <el-descriptions :column="2" border size="small" class="convert-desc">
          <el-descriptions-item label="物料编号">
            <span style="font-weight: 500">{{ convertNode.materialNo }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="物料名称">{{ convertNode.materialName }}</el-descriptions-item>
          <el-descriptions-item label="规格">{{ $fmtSpec(convertNode.spec) }}</el-descriptions-item>
          <el-descriptions-item label="单位">{{ convertNode.unit || '-' }}</el-descriptions-item>
          <el-descriptions-item label="原始需求">{{ fmtQty(convertNode.requiredQty) }}</el-descriptions-item>
          <el-descriptions-item label="缺口">
            <span style="color:#f56c6c; font-weight: 500">{{ fmtQty(convertNode.shortageQty) }}</span>
          </el-descriptions-item>
        </el-descriptions>

        <!-- 自制件: 子件支撑分解 -->
        <div v-if="convertType === 'wo' && convertChildBreakdown.length" class="convert-section">
          <div class="convert-section-title">
            子件支撑能力
            <el-tag v-if="convertMaxQty > 0" type="warning" size="small" effect="plain">
              受限于{{ convertMaxReason }}, 最大可转 {{ fmtQty(convertMaxQty) }}
            </el-tag>
          </div>
          <el-table :data="convertChildBreakdown" border stripe size="small" class="convert-table">
            <el-table-column label="子件" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">
                <span>{{ row.materialNo }}</span>
                <span style="color: #909399; margin-left: 6px">{{ row.materialName }}</span>
              </template>
            </el-table-column>
            <el-table-column label="单耗" width="70" align="right">
              <template #default="{ row }">{{ row.perUnit }}</template>
            </el-table-column>
            <el-table-column label="库存" width="80" align="right">
              <template #default="{ row }">
                <span style="color:#67c23a">{{ fmtQty(row.inventoryQty) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="在途" width="70" align="right">
              <template #default="{ row }">
                <span :style="{ color: Number(row.inTransitQty) > 0 ? '#e6a23c' : '#c0c4cc' }">
                  {{ fmtQty(row.inTransitQty) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="可支撑数" width="110" align="right">
              <template #default="{ row }">
                <span :style="{ color: row.isBottleneck ? '#f56c6c' : '#909399', fontWeight: 500 }">
                  {{ fmtQty(row.maxByChild) }}
                </span>
                <el-tag v-if="row.isBottleneck" type="danger" size="small" effect="plain"
                  style="margin-left: 6px">瓶颈</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 数量录入 -->
        <div class="convert-section">
          <div class="convert-section-title">本次创建数量</div>
          <div class="convert-qty-row">
            <el-input-number v-model="convertQty"
              :min="0.0001"
              :max="convertType === 'wo' && convertMaxQty > 0 ? convertMaxQty : undefined"
              :precision="4" :step="1"
              controls-position="right" style="width: 220px" />
            <div class="convert-qty-hint">
              <el-tag size="small" type="info" effect="plain">
                需求 {{ fmtQty(convertNode.requiredQty) }}
              </el-tag>
              <el-tag v-if="convertType === 'wo' && convertMaxQty > 0"
                size="small" type="warning" effect="plain">
                上限 {{ fmtQty(convertMaxQty) }}
              </el-tag>
              <el-button v-if="convertType === 'wo' && convertMaxQty > 0
                            && Number(convertQty) !== convertMaxQty"
                link type="primary" size="small" @click="convertQty = convertMaxQty">取最大</el-button>
            </div>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="convertVisible = false">取消</el-button>
        <el-button type="primary" :loading="convertSubmitting" @click="submitConvert">确认创建</el-button>
      </template>
    </el-dialog>

    <!-- ========== 代替料选择弹窗 ========== -->
    <el-dialog v-model="subDialogVisible" title="代替料选择" width="700px" destroy-on-close append-to-body>
      <div v-if="subNode" style="margin-bottom:12px; color:#606266; font-size:13px">
        主料：<b>{{ subNode.materialNo }} {{ subNode.materialName }}</b>
        &nbsp;|&nbsp;需求：<b>{{ fmtQty(subNode.requiredQty) }}</b>
        &nbsp;|&nbsp;缺口：<b style="color:#f56c6c">{{ fmtQty(subNode._origShortage ?? subNode.shortageQty) }}</b>
        <el-button v-if="subNode._selectedSubstitute" link type="danger" size="small"
          style="margin-left:12px" @click="clearSubstitute(subNode)">取消代替料</el-button>
      </div>
      <el-table :data="subNode?.substituteOptions || []" border stripe size="small">
        <el-table-column prop="priority" label="优先级" width="70" align="center" />
        <el-table-column prop="materialNo" label="物料编号" width="130" show-overflow-tooltip />
        <el-table-column prop="materialName" label="代替料名称" min-width="130" show-overflow-tooltip />
        <el-table-column label="规格" min-width="100" show-overflow-tooltip>
          <template #default="{ row }">{{ $fmtSpec(row.spec) }}</template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="60" align="center" />
        <el-table-column prop="substituteRatio" label="替代比例" width="90" align="right">
          <template #default="{ row }">1 : {{ row.substituteRatio }}</template>
        </el-table-column>
        <el-table-column label="可用库存" width="100" align="right">
          <template #default="{ row }">
            <span :style="{ color: Number(row.inventoryQty) > 0 ? '#67c23a' : '#f56c6c' }">
              {{ fmtQty(row.inventoryQty) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="在途" width="80" align="right">
          <template #default="{ row }">{{ fmtQty(row.inTransitQty) }}</template>
        </el-table-column>
        <el-table-column label="可支撑(主料换算)" width="130" align="right">
          <template #default="{ row }">
            <b>{{ fmtQty((Number(row.inventoryQty) + Number(row.inTransitQty)) / Number(row.substituteRatio)) }}</b>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="applySubstitute(subNode, row)">
              {{ subNode?._selectedSubstitute?.substituteId === row.substituteId ? '已选' : '选择' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="subDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { listMasterPlanOrders, removeOrderFromMasterPlan, calculateMasterPlan,
         convertToWo as convertToWoApi, convertToPo as convertToPoApi } from '@/api/masterPlan'

const router = useRouter()

// ==================== 订单列表 ====================
const orderList = ref([])
const listLoading = ref(false)

async function loadList() {
  listLoading.value = true
  try {
    const res = await listMasterPlanOrders()
    orderList.value = res.data || []
  } catch (e) {
    ElMessage.error('加载主计划订单失败')
  } finally { listLoading.value = false }
}

const statusMap = {
  draft: '草稿', pending_approval: '待审批', confirmed: '已确认',
  in_production: '生产中', partial_shipped: '部分发货', shipped: '已发货',
  completed: '已完成', settled: '已结算', voided: '已作废', cancelled: '已取消'
}

// ==================== 计算 ====================
const calculated = ref(false)
const calcLoading = ref(false)

// ==================== 代替料选择持久化 (localStorage) ====================
const MRP_SUBS_KEY = 'mrp-master-plan-subs'

function loadSubsFromStorage() {
  try { return JSON.parse(localStorage.getItem(MRP_SUBS_KEY) || '{}') } catch { return {} }
}

function saveSubsToStorage() {
  const all = loadSubsFromStorage()
  for (const order of orderList.value) {
    const map = collectSubstituteSelections(order._tree || [])
    if (Object.keys(map).length) {
      all[order.id] = map
    } else {
      delete all[order.id]
    }
  }
  localStorage.setItem(MRP_SUBS_KEY, JSON.stringify(all))
}

async function doCalculate() {
  if (!orderList.value.length) {
    ElMessage.warning('暂无订单可计算')
    return
  }
  calcLoading.value = true
  try {
    // 串行调,避免并发把后端打爆 (订单数多时)
    for (const order of orderList.value) {
      order._loading = true
      try {
        const res = await calculateMasterPlan(order.id)
        order._tree = (res.data || []).map((n, i) => assignRowKey(n, `o${order.id}-${i}`))
        // 从 localStorage 还原选择(覆盖重算和页面重新进入两种场景)
        const saved = loadSubsFromStorage()[order.id] || {}
        if (Object.keys(saved).length) {
          restoreSubstituteSelections(order._tree, saved)
        }
      } catch {
        order._tree = []
      } finally { order._loading = false }
    }
    calculated.value = true
    ElMessage.success(`已计算 ${orderList.value.length} 个订单`)
  } finally { calcLoading.value = false }
}

// 遍历树收集已选代替料: rowKey → substituteId
function collectSubstituteSelections(nodes) {
  const map = {}
  function walk(list) {
    for (const n of list) {
      if (n._selectedSubstitute?.substituteId != null) {
        map[n.rowKey] = n._selectedSubstitute.substituteId
      }
      if (n.children?.length) walk(n.children)
    }
  }
  walk(nodes)
  return map
}

// 新树生成后静默还原选择 (rowKey 稳定,因为 BOM 结构未变)
function restoreSubstituteSelections(nodes, savedSelections) {
  function walk(list) {
    for (const n of list) {
      const subId = savedSelections[n.rowKey]
      if (subId != null && n.substituteOptions?.length) {
        const sub = n.substituteOptions.find(s => s.substituteId === subId)
        if (sub) applySubstituteToNode(n, sub) // 用新库存数据静默重算
      }
      if (n.children?.length) walk(n.children)
    }
  }
  walk(nodes)
}

function assignRowKey(node, prefix) {
  node.rowKey = prefix
  if (Array.isArray(node.children)) {
    node.children.forEach((c, i) => assignRowKey(c, `${prefix}-${i}`))
  }
  return node
}

// ==================== 搜索过滤 ====================
const keyword = ref('')
// 订单层过滤: 订单号 / 客户名匹配, 或者订单的 BOM 树里有物料匹配
const filteredOrders = computed(() => {
  if (!keyword.value || !keyword.value.trim()) return orderList.value
  const kw = keyword.value.trim().toLowerCase()
  return orderList.value.filter(o => {
    if ((o.orderNo || '').toLowerCase().includes(kw)) return true
    if ((o.customerName || '').toLowerCase().includes(kw)) return true
    // 物料级搜索: 树里任何节点匹配
    return treeMatches(o._tree || [], kw)
  })
})
function treeMatches(nodes, kw) {
  for (const n of nodes) {
    if ((n.materialNo || '').toLowerCase().includes(kw)) return true
    if ((n.materialName || '').toLowerCase().includes(kw)) return true
    if (n.children && treeMatches(n.children, kw)) return true
  }
  return false
}
// 单个订单展开后, 树自身按 keyword 过滤(保留祖先链路)
function filterTreeByKeyword(tree) {
  if (!keyword.value || !keyword.value.trim()) return tree
  const kw = keyword.value.trim().toLowerCase()
  function f(node) {
    const matched = (node.materialNo || '').toLowerCase().includes(kw)
                  || (node.materialName || '').toLowerCase().includes(kw)
    const matchedChildren = (node.children || []).map(f).filter(Boolean)
    if (matched || matchedChildren.length) return { ...node, children: matchedChildren }
    return null
  }
  return tree.map(f).filter(Boolean)
}

// ==================== 工具 ====================
function fmtQty(v) {
  if (v == null) return '-'
  const n = Number(v)
  if (isNaN(n)) return v
  return n === Math.floor(n) ? n.toString() : n.toFixed(4).replace(/0+$/, '').replace(/\.$/, '')
}
function statusTagType(n) {
  if (n.status === 'sufficient') return 'success'
  if (n.canConvertWo || n.canConvertPo) return 'warning'
  return 'danger'
}

// ==================== 操作 ====================
async function onRemove(row) {
  try {
    await ElMessageBox.confirm(`确认将订单 ${row.orderNo} 移出主计划?`, '提示', { type: 'warning' })
    await removeOrderFromMasterPlan(row.id)
    ElMessage.success('已移出')
    loadList()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e?.message || '操作失败')
  }
}

function goOrderDetail(row) {
  router.push({ path: '/order/detail', query: { id: row.id } })
}


// ==================== 转车间计划 / 转采购申请 弹窗 ====================
const convertVisible = ref(false)
const convertType = ref('') // 'wo' | 'po'
const convertOrder = ref(null)
const convertNode = ref(null)
const convertQty = ref(0)
const convertSubmitting = ref(false)
// 自制件最大可转数量 (受子件可用库存约束), 0 表示不限制
const convertMaxQty = ref(0)
const convertMaxReason = ref('父级缺口')      // "父级缺口" 或 "子件库存"
const convertChildBreakdown = ref([])

function fmtPerUnit(v) {
  if (v == null || isNaN(v)) return '-'
  const n = Number(v)
  return n === Math.floor(n) ? n.toString() : n.toFixed(4).replace(/0+$/, '').replace(/\.$/, '')
}

// 子件可支撑的最大父级"缺口数量" = floor((child.inventory + child.inTransit) / perUnit)
// perUnit = child.requiredQty / parent.shortageQty
//   后端按 shortage × BOM单耗 算 child.requiredQty, 反推也用 shortage 做分母
//   父级 requiredQty 是订单原始需求, 不参与子件需求量推导, 不能拿来做分母
// 在途也算进供应能力 — 子件已下采购单/工单的部分会到货, 应一并视为可用资源
function computeChildBreakdown(node) {
  const parentShortage = Number(node.shortageQty || 0)
  const children = Array.isArray(node.children) ? node.children : []
  const breakdown = children.map(c => {
    const childReq = Number(c.requiredQty || 0)
    const inv = Number(c.inventoryQty || 0)
    const transit = Number(c.inTransitQty || 0)
    const supply = inv + transit
    const perUnit = parentShortage > 0 ? (childReq / parentShortage) : 0
    const maxRaw = perUnit > 0 ? (supply / perUnit) : Infinity
    return {
      materialNo: c.materialNo,
      materialName: c.materialName,
      perUnit: fmtPerUnit(perUnit),
      inventoryQty: inv,
      inTransitQty: transit,
      supplyQty: supply,
      maxByChild: Number.isFinite(maxRaw) ? Number(maxRaw.toFixed(4)) : '-',
      _maxRaw: maxRaw,
      isBottleneck: false
    }
  })
  // 找最小可支撑数, 但只在它真正卡住父级缺口时才标"瓶颈"
  // (子件能撑 99, 父级只缺 16 → 99 不是瓶颈, 标了反而误导)
  let minMax = Infinity
  for (const b of breakdown) {
    if (b._maxRaw < minMax) minMax = b._maxRaw
  }
  const childCapped = Number.isFinite(minMax) && minMax < parentShortage
  if (childCapped) {
    for (const b of breakdown) {
      if (b._maxRaw === minMax) b.isBottleneck = true
    }
  }
  return { breakdown, max: minMax }
}

function goConvertToWo(order, node) {
  convertType.value = 'wo'
  convertOrder.value = order
  convertNode.value = node

  const { breakdown, max } = computeChildBreakdown(node)
  convertChildBreakdown.value = breakdown
  // 上限取 min(父级缺口, 子件支撑能力)
  //  - 父级缺口 shortage 是真正的天花板: 已有库存 + 在途已经覆盖了非缺口部分, 没必要多产
  //  - 子件支撑能力是次要约束: 即使想多做, 子件也撑不住
  const shortage = Number(node.shortageQty || 0)
  const childMax = Number.isFinite(max) ? max : Infinity
  const cap = Math.min(shortage, childMax)
  // 文案根据真实约束源动态切换 — 谁更小就是谁限制
  convertMaxReason.value = (childMax < shortage) ? '子件库存' : '父级缺口'
  convertMaxQty.value = Number(cap.toFixed(4))
  convertQty.value = cap > 0 ? cap : 1
  convertVisible.value = true
}

function goConvertToPo(order, node) {
  convertType.value = 'po'
  convertOrder.value = order
  convertNode.value = node
  convertChildBreakdown.value = []
  convertMaxQty.value = 0
  convertQty.value = Number(node.shortageQty || 0) || 1
  convertVisible.value = true
}

async function submitConvert() {
  const qty = Number(convertQty.value)
  if (!qty || qty <= 0) { ElMessage.warning('数量必须大于 0'); return }
  if (convertType.value === 'wo' && convertMaxQty.value > 0 && qty > convertMaxQty.value) {
    ElMessage.warning(`数量不能超过最大可转 ${convertMaxQty.value} (受子件库存限制)`)
    return
  }
  convertSubmitting.value = true
  try {
    if (convertType.value === 'wo') {
      // 收集直接子件的替代料选择: 原始物料ID → substituteId
      const substituteSelections = {}
      for (const child of (convertNode.value.children || [])) {
        if (child._selectedSubstitute?.substituteId != null) {
          const origMatId = child._origMaterialId ?? child.materialId
          if (origMatId != null) substituteSelections[origMatId] = child._selectedSubstitute.substituteId
        }
      }
      const res = await convertToWoApi({
        orderId: convertOrder.value.id,
        productId: convertNode.value.materialId,
        qty,
        ...(Object.keys(substituteSelections).length ? { substituteSelections } : {})
      })
      ElMessage.success(`生产计划创建成功(草稿状态), 计划 ID: ${res.data?.planId}`)
    } else {
      const res = await convertToPoApi({
        orderId: convertOrder.value.id,
        materialId: convertNode.value.materialId,
        qty
      })
      ElMessage.success(`采购申请创建成功(草稿状态, 请到"采购申请"页面编辑/提交), ID: ${res.data?.prId || res.data?.poId}`)
    }
    convertVisible.value = false
    await doCalculate()
  } catch (e) {
    ElMessage.error(e?.message || '创建失败')
  } finally { convertSubmitting.value = false }
}

// ==================== 代替料选择 ====================
const subDialogVisible = ref(false)
const subNode = ref(null)

function openSubstituteDialog(order, node) {
  subNode.value = node
  subDialogVisible.value = true
}

// 纯数据函数: 将代替料应用到节点上(重算后静默还原时也调这个)
function applySubstituteToNode(node, sub) {
  if (!node) return
  // 第一次选时保存所有原始值(切换代替料不覆盖)
  if (node._origShortage == null) {
    node._origShortage = node.shortageQty
    node._origInventoryQty = node.inventoryQty
    node._origInTransitQty = node.inTransitQty
    node._origStatus = node.status
    node._origStatusLabel = node.statusLabel
    node._origCanConvertWo = node.canConvertWo
    node._origCanConvertPo = node.canConvertPo
    node._origMaterialId = node.materialId
    node._origMaterialNo = node.materialNo
    node._origMaterialName = node.materialName
    node._origSpec = node.spec
    node._origUnit = node.unit
    node._origSourceType = node.sourceType
    node._origChildren = node.children
  }
  const ratio = Number(sub.substituteRatio) || 1
  const effectiveInv = Number(sub.inventoryQty) / ratio
  const effectiveTransit = Number(sub.inTransitQty) / ratio
  const required = Number(node.requiredQty) || 0
  const newShortage = Math.max(0, required - effectiveInv - effectiveTransit)
  const subSourceType = sub.sourceType || 'purchase'
  const subIsManufacture = subSourceType === 'manufacture'

  node._selectedSubstitute = sub
  node.inventoryQty = effectiveInv.toFixed(4)
  node.inTransitQty = effectiveTransit.toFixed(4)
  node.shortageQty = newShortage.toFixed(4)
  // 换成代替料的物料信息,转单时使用代替料
  node.materialId = sub.materialId
  node.materialNo = sub.materialNo
  node.materialName = sub.materialName
  node.spec = sub.spec
  node.unit = sub.unit
  node.sourceType = subSourceType
  // 代替料无已知子件BOM,转单时不展示错误的子件支撑数据
  node.children = []
  if (newShortage <= 0) {
    node.status = 'sufficient'
    node.statusLabel = `充足(代替: ${sub.materialNo || sub.materialName})`
    node.canConvertWo = false
    node.canConvertPo = false
  } else {
    node.statusLabel = `缺口${fmtQty(newShortage)}(代替: ${sub.materialNo || sub.materialName})`
    node.canConvertWo = subIsManufacture
    node.canConvertPo = !subIsManufacture
  }
}

function applySubstitute(node, sub) {
  if (!node) return
  applySubstituteToNode(node, sub)
  subDialogVisible.value = false
  saveSubsToStorage()
  ElMessage.success(`已选代替料 ${sub.materialNo} ${sub.materialName}, 缺口更新为 ${fmtQty(node.shortageQty)}`)
}

function clearSubstitute(node) {
  if (!node || node._origShortage == null) return
  node.shortageQty = node._origShortage
  node.inventoryQty = node._origInventoryQty
  node.inTransitQty = node._origInTransitQty
  node.status = node._origStatus
  node.statusLabel = node._origStatusLabel
  node.canConvertWo = node._origCanConvertWo
  node.canConvertPo = node._origCanConvertPo
  node.materialId = node._origMaterialId
  node.materialNo = node._origMaterialNo
  node.materialName = node._origMaterialName
  node.spec = node._origSpec
  node.unit = node._origUnit
  node.sourceType = node._origSourceType
  node.children = node._origChildren
  node._selectedSubstitute = null
  node._origShortage = null
  saveSubsToStorage()
}

// ==================== 初始化 ====================
loadList()
</script>

<style scoped>
.master-plan-page { padding: 16px; }
.top-bar {
  display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px;
}
.top-bar .left { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.expand-tree-wrapper { padding: 8px 12px; background: #fafafa; }
.expand-hint { padding: 12px; color: #909399; text-align: center; font-size: 13px; }

/* 转单弹窗 */
.convert-dialog { display: flex; flex-direction: column; gap: 18px; }
.convert-desc :deep(.el-descriptions__label) { width: 80px; }
.convert-section { display: flex; flex-direction: column; gap: 10px; }
.convert-section-title {
  font-size: 14px; font-weight: 500; color: #303133;
  display: flex; align-items: center; gap: 10px;
}
.convert-table :deep(.el-table__row) { font-size: 13px; }
.convert-qty-row { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; }
.convert-qty-hint { display: flex; align-items: center; gap: 8px; }
</style>
