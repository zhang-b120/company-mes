<template>
  <TvShell module="outsource" title="外协协同中心" subtitle="OUTSOURCE COLLAB CENTER"
           icon="🤝" theme="pink" :tabs="tabs" ticker-label="外协实时动态" @ready="onReady">
    <template #default="{ curTab, data }">
      <!-- TAB 0: 外协概览 -->
      <div :class="['tvm-panel', { active: curTab === 0 }]">
        <div class="tvm-kpi-row">
          <Kpi label="外协申请总数" :value="data?.overview?.totalRequest" unit="项" k="k1" data-k="overview.totalRequest"/>
          <Kpi label="待审批申请" :value="data?.overview?.pendingRequest" unit="项" k="k4" data-k="overview.pendingRequest"/>
          <Kpi label="活跃订单" :value="data?.overview?.inProgressOrder" unit="单" k="k3" data-k="overview.inProgressOrder"/>
          <Kpi label="本月外协额" :value="data?.overview?.monthAmount" prefix="¥" unit="元" k="k2" data-k="overview.monthAmount"/>
        </div>
        <div class="tvm-charts tvm-cr2">
          <Card title="申请状态分布"><div class="tvm-ci" ref="c00"></div></Card>
          <Card title="订单状态分布"><div class="tvm-ci" ref="c01"></div></Card>
        </div>
      </div>

      <!-- TAB 1: 趋势 -->
      <div :class="['tvm-panel', { active: curTab === 1 }]">
        <div class="tvm-kpi-row">
          <Kpi label="本月外协额" :value="data?.overview?.monthAmount" prefix="¥" unit="元" k="k1" data-k="overview.monthAmount"/>
          <Kpi label="活跃订单" :value="data?.overview?.inProgressOrder" unit="单" k="k3" data-k="overview.inProgressOrder"/>
          <Kpi label="活跃供应商" :value="data?.supplier?.supplierCount" unit="家" k="k2" data-k="supplier.supplierCount"/>
          <Kpi label="待审申请" :value="data?.overview?.pendingRequest" unit="项" k="k4" data-k="overview.pendingRequest"/>
        </div>
        <div class="tvm-charts tvm-cr2">
          <Card title="近 12 月外协金额" tag="元" tagCls="ok"><div class="tvm-ci" ref="c10"></div></Card>
          <Card title="近 12 月外协订单数"><div class="tvm-ci" ref="c11"></div></Card>
        </div>
      </div>

      <!-- TAB 2: 供应商 -->
      <div :class="['tvm-panel', { active: curTab === 2 }]">
        <div class="tvm-kpi-row">
          <Kpi label="活跃供应商" :value="data?.supplier?.supplierCount" unit="家" k="k1" data-k="supplier.supplierCount"/>
          <Kpi label="本月外协额" :value="data?.overview?.monthAmount" prefix="¥" unit="元" k="k2" data-k="overview.monthAmount"/>
          <Kpi label="待审申请" :value="data?.overview?.pendingRequest" unit="项" k="k4" data-k="overview.pendingRequest"/>
          <Kpi label="活跃订单" :value="data?.overview?.inProgressOrder" unit="单" k="k3" data-k="overview.inProgressOrder"/>
        </div>
        <div class="tvm-charts tvm-cr2">
          <Card title="外协金额 TOP 8" tag="¥" tagCls=""><div class="tvm-ci" ref="c20"></div></Card>
          <Card title="外协笔数 TOP 8"><div class="tvm-ci" ref="c21"></div></Card>
        </div>
      </div>

      <!-- TAB 3: 活跃订单 -->
      <div :class="['tvm-panel', { active: curTab === 3 }]">
        <div class="tvm-kpi-row">
          <Kpi label="待发料" :value="data?.active?.toDeliver" unit="单" k="k1" data-k="active.toDeliver"/>
          <Kpi label="加工中" :value="data?.active?.processing" unit="单" k="k3" data-k="active.processing"/>
          <Kpi label="待收货" :value="data?.active?.toReceive" unit="单" k="k4" data-k="active.toReceive"/>
          <Kpi label="逾期订单" :value="data?.active?.overdue" unit="单" k="k5" data-k="active.overdue"
               :trendType="(data?.active?.overdue>0)?'dn':'eq'"
               :trend="(data?.active?.overdue>0)?'⚠ 已逾期':'→ 无逾期'"/>
        </div>
        <div class="tvm-charts" style="grid-template-columns:1fr">
          <Card title="活跃外协订单" :tag="(data?.active?.activeList?.length||0)+'单'" tagCls="warn">
            <div class="tvm-ci" style="overflow-y:auto">
              <table class="tvm-table">
                <thead><tr><th>订单号</th><th>供应商</th><th>工序</th><th>数量</th><th>交期</th><th>状态</th></tr></thead>
                <tbody>
                  <tr v-for="(r, i) in (data?.active?.activeList || [])" :key="i">
                    <td class="tvm-sub">{{ r.orderNo }}</td>
                    <td>{{ r.supplierName }}</td>
                    <td>{{ r.stepName }}</td>
                    <td>{{ r.totalQty }}</td>
                    <td><span class="tvm-badge" :class="dueCls(r.deliveryDate)">{{ r.deliveryDate || '-' }}</span></td>
                    <td><span class="tvm-badge" :class="ooCls(r.status)">{{ ooTxt(r.status) }}</span></td>
                  </tr>
                </tbody>
              </table>
            </div>
          </Card>
        </div>
      </div>
    </template>
  </TvShell>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import TvShell from './TvShell.vue'
import Kpi from './Kpi.vue'
import Card from './Card.vue'
import { echarts, donut, horizontalBar, singleBar } from './chartHelpers'
import './tv-shared.scss'

const tabs = ['🤝 外协概览', '📈 趋势', '🏭 供应商', '📋 活跃订单']

const c00 = ref(null), c01 = ref(null)
const c10 = ref(null), c11 = ref(null)
const c20 = ref(null), c21 = ref(null)
let charts = []

const REQ_S = { pending:'待审批', approved:'已审批', rejected:'已驳回' }
const OO_S = { draft:'草稿', confirmed:'已确认', material_sent:'已发料', processing:'加工中',
  partial_received:'部分收货', received:'已收货', settled:'已结算', cancelled:'已取消' }
const ooTxt = s => OO_S[s] || s || '-'
const ooCls = s => {
  if (['received','settled'].includes(s)) return 'bo'
  if (s === 'cancelled') return 'be'
  return 'bw'
}
function dueCls(d) {
  if (!d) return 'bi'
  const today = new Date(); today.setHours(0,0,0,0)
  const days = (new Date(d) - today) / 86400000
  if (days < 0) return 'be'
  if (days <= 3) return 'bw'
  return 'bi'
}

function onReady(data) {
  charts.forEach(c => c && c.dispose()); charts = []
  nextTick(() => render(data))
}
function render(data) {
  const ov = data?.overview || {}
  if (c00.value) { const c = echarts.init(c00.value); charts.push(c); c.setOption(donut(ov.requestStatusPie, n => REQ_S[n])) }
  if (c01.value) { const c = echarts.init(c01.value); charts.push(c); c.setOption(donut(ov.orderStatusPie, n => OO_S[n])) }

  const tr = data?.trend || {}
  if (c10.value) { const c = echarts.init(c10.value); charts.push(c)
    c.setOption(singleBar(tr.monthTrend || [], 'month', 'amount', '外协金额', '#ff4d8d', '元')) }
  if (c11.value) { const c = echarts.init(c11.value); charts.push(c)
    c.setOption(singleBar(tr.monthTrend || [], 'month', 'count', '外协笔数', '#a855f7', '笔')) }

  const sp = data?.supplier || {}
  if (c20.value) { const c = echarts.init(c20.value); charts.push(c); c.setOption(horizontalBar(sp.amountTop, '元')) }
  if (c21.value) { const c = echarts.init(c21.value); charts.push(c); c.setOption(horizontalBar(sp.countTop, '笔')) }

  window.addEventListener('resize', () => charts.forEach(c => c && c.resize()))
}
</script>
