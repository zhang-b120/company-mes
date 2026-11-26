<template>
  <TvShell module="purchase" title="采购作战中心" subtitle="PROCUREMENT BATTLE CENTER"
           icon="🚚" theme="purple" :tabs="tabs" ticker-label="采购实时动态" @ready="onReady">
    <template #default="{ curTab, data }">
      <!-- TAB 0: 采购概览 -->
      <div :class="['tvm-panel', { active: curTab === 0 }]">
        <div class="tvm-kpi-row">
          <Kpi label="本月采购额" :value="data?.overview?.monthAmount" prefix="¥" unit="元" k="k1" data-k="overview.monthAmount"/>
          <Kpi label="本月笔数" :value="data?.overview?.monthCount" unit="笔" k="k2" data-k="overview.monthCount"/>
          <Kpi label="待到货" :value="data?.overview?.toReceive" unit="单" k="k3" data-k="overview.toReceive"/>
          <Kpi label="未付款" :value="data?.overview?.totalUnpaid" prefix="¥" unit="元" k="k5" data-k="overview.totalUnpaid"
               trendType="dn" trend="↓ 待支付"/>
        </div>
        <div class="tvm-charts tvm-cr3">
          <Card title="近 12 月采购金额" tag="元" tagCls="ok"><div class="tvm-ci" ref="c00"></div></Card>
          <Card title="采购订单状态"><div class="tvm-ci" ref="c01"></div></Card>
          <Card title="近 12 月采购笔数"><div class="tvm-ci" ref="c02"></div></Card>
        </div>
      </div>

      <!-- TAB 1: 采购申请 -->
      <div :class="['tvm-panel', { active: curTab === 1 }]">
        <div class="tvm-kpi-row">
          <Kpi label="待审批申请" :value="data?.request?.pendingReq" unit="个" k="k1" data-k="request.pendingReq"/>
          <Kpi label="已审批" :value="data?.request?.approvedReq" unit="个" k="k2" data-k="request.approvedReq"/>
          <Kpi label="已下发" :value="data?.request?.issuedReq" unit="个" k="k4" data-k="request.issuedReq"/>
          <Kpi label="已驳回" :value="data?.request?.rejectedReq" unit="个" k="k5" data-k="request.rejectedReq"/>
        </div>
        <div class="tvm-charts tvm-cr2">
          <Card title="申请状态分布"><div class="tvm-ci" ref="c10"></div></Card>
          <Card title="最新申请明细" :tag="(data?.request?.recentList?.length||0) + '项'" tagCls="">
            <div class="tvm-ci" style="overflow-y:auto">
              <table class="tvm-table">
                <thead><tr><th>申请号</th><th>物料</th><th>数量</th><th>建议供应商</th><th>预计到货</th><th>状态</th></tr></thead>
                <tbody>
                  <tr v-for="(r, i) in (data?.request?.recentList || [])" :key="i">
                    <td class="tvm-sub">{{ r.requestNo }}</td>
                    <td>{{ r.materialName }}</td>
                    <td>{{ r.quantity }}</td>
                    <td>{{ r.supplierName }}</td>
                    <td class="tvm-sub">{{ r.expectedDate || '-' }}</td>
                    <td><span class="tvm-badge" :class="reqStatusCls(r.status)">{{ reqStatusTxt(r.status) }}</span></td>
                  </tr>
                </tbody>
              </table>
            </div>
          </Card>
        </div>
      </div>

      <!-- TAB 2: 供应商分析 -->
      <div :class="['tvm-panel', { active: curTab === 2 }]">
        <div class="tvm-kpi-row">
          <Kpi label="供应商总数" :value="data?.supplier?.totalSupplier" unit="家" k="k1" data-k="supplier.totalSupplier"/>
          <Kpi label="本月活跃" :value="data?.supplier?.activeSupplier" unit="家" k="k2" data-k="supplier.activeSupplier"/>
          <Kpi label="准时到货率" :value="data?.supplier?.onTimeRate" unit="%" k="k4" data-k="supplier.onTimeRate" trend="↑ 良好"/>
          <Kpi label="退货次数" :value="data?.supplier?.returnCount" unit="次" k="k5" data-k="supplier.returnCount"/>
        </div>
        <div class="tvm-charts tvm-cr2">
          <Card title="采购金额 TOP 8" tag="¥" tagCls=""><div class="tvm-ci" ref="c20"></div></Card>
          <Card title="采购笔数 TOP 8"><div class="tvm-ci" ref="c21"></div></Card>
        </div>
      </div>

      <!-- TAB 3: 收货跟踪 -->
      <div :class="['tvm-panel', { active: curTab === 3 }]">
        <div class="tvm-kpi-row">
          <Kpi label="本月收货" :value="data?.receipt?.monthReceipt" unit="单" k="k2" data-k="receipt.monthReceipt"/>
          <Kpi label="累计收货量" :value="data?.receipt?.totalReceived" unit="件" k="k1" data-k="receipt.totalReceived"/>
          <Kpi label="近 7 日到货" :value="data?.receipt?.upcomingPo" unit="单" k="k4" data-k="receipt.upcomingPo"/>
          <Kpi label="逾期采购单" :value="data?.receipt?.overduePo" unit="单" k="k5" data-k="receipt.overduePo"
               :trendType="(data?.receipt?.overduePo>0)?'dn':'eq'"
               :trend="(data?.receipt?.overduePo>0)?'⚠ 已逾期':'→ 无逾期'"/>
        </div>
        <div class="tvm-charts" style="grid-template-columns:1fr">
          <Card title="近期到货采购单" :tag="(data?.receipt?.upcomingList?.length||0) + '单'" tagCls="warn">
            <div class="tvm-ci" style="overflow-y:auto">
              <table class="tvm-table">
                <thead><tr><th>采购单</th><th>供应商</th><th>预计到货</th><th>金额</th><th>状态</th></tr></thead>
                <tbody>
                  <tr v-for="(r, i) in (data?.receipt?.upcomingList || [])" :key="i">
                    <td class="tvm-sub">{{ r.poNo }}</td>
                    <td>{{ r.supplierName }}</td>
                    <td><span class="tvm-badge" :class="dueCls(r.deliveryDate)">{{ r.deliveryDate || '-' }}</span></td>
                    <td>¥ {{ formatAmt(r.amount) }}</td>
                    <td><span class="tvm-badge" :class="poStatusCls(r.status)">{{ poStatusTxt(r.status) }}</span></td>
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

const tabs = ['📊 采购概览', '📋 采购申请', '🏭 供应商分析', '📦 收货跟踪']

const c00 = ref(null), c01 = ref(null), c02 = ref(null)
const c10 = ref(null), c20 = ref(null), c21 = ref(null)
let charts = []

const REQ_STATUS = { pending:'待审批', approved:'已审批', issued:'已下发', rejected:'已驳回' }
const PO_STATUS = { draft:'草稿', pending_approval:'待审批', confirmed:'已确认',
  partial_received:'部分到货', received:'已到货', rejected:'已驳回', closed:'已关闭', returned:'已退货' }
const reqStatusTxt = s => REQ_STATUS[s] || s || '-'
const reqStatusCls = s => ({ pending:'bi', approved:'bw', issued:'bo', rejected:'be' }[s] || 'bi')
const poStatusTxt = s => PO_STATUS[s] || s || '-'
const poStatusCls = s => {
  if (['received','closed'].includes(s)) return 'bo'
  if (s === 'rejected' || s === 'returned') return 'be'
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
function formatAmt(v) {
  const n = Number(v || 0)
  if (n >= 100000000) return (n/100000000).toFixed(2) + '亿'
  if (n >= 10000) return (n/10000).toFixed(1) + '万'
  return n.toLocaleString()
}

function onReady(data) {
  charts.forEach(c => c && c.dispose()); charts = []
  nextTick(() => render(data))
}
function render(data) {
  const ov = data?.overview || {}
  if (c00.value) { const c = echarts.init(c00.value); charts.push(c)
    c.setOption(singleBar(ov.monthTrend || [], 'month', 'amount', '采购金额', '#a855f7', '元')) }
  if (c01.value) { const c = echarts.init(c01.value); charts.push(c); c.setOption(donut(ov.statusPie, n => PO_STATUS[n])) }
  if (c02.value) { const c = echarts.init(c02.value); charts.push(c)
    c.setOption(singleBar(ov.monthTrend || [], 'month', 'count', '采购笔数', '#00d4ff', '笔')) }

  const rq = data?.request || {}
  if (c10.value) { const c = echarts.init(c10.value); charts.push(c); c.setOption(donut(rq.statusPie, n => REQ_STATUS[n])) }

  const sp = data?.supplier || {}
  if (c20.value) { const c = echarts.init(c20.value); charts.push(c); c.setOption(horizontalBar(sp.amountTop, '元')) }
  if (c21.value) { const c = echarts.init(c21.value); charts.push(c); c.setOption(horizontalBar(sp.countTop, '笔')) }

  window.addEventListener('resize', () => charts.forEach(c => c && c.resize()))
}
</script>
