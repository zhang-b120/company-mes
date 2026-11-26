<template>
  <TvShell module="sales" title="销售战情中心" subtitle="SALES BATTLE CENTER"
           icon="📈" theme="blue" :tabs="tabs" ticker-label="销售实时动态" @ready="onReady">
    <template #default="{ curTab, data }">
      <!-- TAB 0: 销售概览 -->
      <div :class="['tvm-panel', { active: curTab === 0 }]">
        <div class="tvm-kpi-row">
          <Kpi label="本月订单总额" :value="data?.overview?.monthAmount" prefix="¥" unit="元" k="k1" data-k="overview.monthAmount"/>
          <Kpi label="本月订单数" :value="data?.overview?.monthCount" unit="笔" k="k2" data-k="overview.monthCount"/>
          <Kpi label="未收款总额" :value="data?.overview?.totalUnreceived" prefix="¥" unit="元" k="k3" data-k="overview.totalUnreceived" trendType="dn" trend="↓ 待回款"/>
          <Kpi label="活跃客户" :value="data?.overview?.activeCustomer" unit="家" k="k4" data-k="overview.activeCustomer"/>
        </div>
        <div class="tvm-charts tvm-cr3">
          <Card title="近 12 月销售趋势" tag="元" tagCls="ok"><div class="tvm-ci" ref="c00"></div></Card>
          <Card title="订单状态分布"><div class="tvm-ci" ref="c01"></div></Card>
          <Card title="近 12 月订单笔数"><div class="tvm-ci" ref="c02"></div></Card>
        </div>
      </div>

      <!-- TAB 1: 客户分析 -->
      <div :class="['tvm-panel', { active: curTab === 1 }]">
        <div class="tvm-kpi-row">
          <Kpi label="客户总数" :value="data?.customer?.totalCustomer" unit="家" k="k1" data-k="customer.totalCustomer"/>
          <Kpi label="本月活跃" :value="data?.customer?.activeCustomer" unit="家" k="k2" data-k="customer.activeCustomer"/>
          <Kpi label="本月新增" :value="data?.customer?.newCustomer" unit="家" k="k3" data-k="customer.newCustomer"/>
          <Kpi label="贡献客户" :value="data?.customer?.topCount" unit="家" k="k4" data-k="customer.topCount"/>
        </div>
        <div class="tvm-charts tvm-cr2">
          <Card title="客户销售额 TOP 8" tag="¥" tagCls=""><div class="tvm-ci" ref="c10"></div></Card>
          <Card title="客户订单笔数 TOP 8"><div class="tvm-ci" ref="c11"></div></Card>
        </div>
      </div>

      <!-- TAB 2: 发货与履约 -->
      <div :class="['tvm-panel', { active: curTab === 2 }]">
        <div class="tvm-kpi-row">
          <Kpi label="待发货" :value="data?.shipment?.toShip" unit="单" k="k3" data-k="shipment.toShip"/>
          <Kpi label="部分发货" :value="data?.shipment?.partialShipped" unit="单" k="k4" data-k="shipment.partialShipped"/>
          <Kpi label="已发货" :value="data?.shipment?.shipped" unit="单" k="k2" data-k="shipment.shipped"/>
          <Kpi label="逾期订单" :value="data?.shipment?.overdue" unit="单" k="k5" data-k="shipment.overdue"
               :trendType="(data?.shipment?.overdue>0)?'dn':'eq'"
               :trend="(data?.shipment?.overdue>0)?`⚠ ${data.shipment.overdue} 单已逾期`:'→ 无逾期'"/>
        </div>
        <div class="tvm-charts" style="grid-template-columns:1fr">
          <Card title="近期交期订单" :tag="(data?.shipment?.upcomingList?.length||0) + '单'" tagCls="warn">
            <div class="tvm-ci" style="overflow-y:auto">
              <table class="tvm-table">
                <thead><tr><th>订单号</th><th>客户</th><th>交期</th><th>金额</th><th>状态</th></tr></thead>
                <tbody>
                  <tr v-for="(r, i) in (data?.shipment?.upcomingList || [])" :key="i">
                    <td class="tvm-sub">{{ r.orderNo }}</td>
                    <td>{{ r.customerName }}</td>
                    <td><span class="tvm-badge" :class="dueCls(r.deliveryDate)">{{ r.deliveryDate }}</span></td>
                    <td>¥ {{ formatAmt(r.amount) }}</td>
                    <td><span class="tvm-badge" :class="statusCls(r.status)">{{ statusTxt(r.status) }}</span></td>
                  </tr>
                </tbody>
              </table>
            </div>
          </Card>
        </div>
      </div>

      <!-- TAB 3: 利润 -->
      <div :class="['tvm-panel', { active: curTab === 3 }]">
        <div class="tvm-kpi-row">
          <Kpi label="本月营收" :value="data?.profit?.monthRevenue" prefix="¥" unit="元" k="k2" data-k="profit.monthRevenue"/>
          <Kpi label="本月成本" :value="data?.profit?.monthCost" prefix="¥" unit="元" k="k3" data-k="profit.monthCost"/>
          <Kpi label="本月毛利" :value="data?.profit?.monthProfit" prefix="¥" unit="元" k="k1" data-k="profit.monthProfit"/>
          <Kpi label="毛利率" :value="data?.profit?.profitRate" unit="%" k="k4" data-k="profit.profitRate" trend="↑ 健康水平"/>
        </div>
        <div class="tvm-charts" style="grid-template-columns:1fr">
          <Card title="产品销售额 TOP 8" tag="¥" tagCls="ok"><div class="tvm-ci" ref="c30"></div></Card>
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
import { echarts, base, xAxis, yAxis, donut, horizontalBar, singleBar } from './chartHelpers'
import './tv-shared.scss'

const tabs = ['💰 销售概览', '👥 客户分析', '🚚 发货履约', '📊 产品利润']

const c00 = ref(null), c01 = ref(null), c02 = ref(null)
const c10 = ref(null), c11 = ref(null)
const c30 = ref(null)
let charts = []

const SO_STATUS = { draft:'草稿', pending_approval:'待审批', confirmed:'已确认', mrp_planned:'已转计划',
  in_production:'生产中', partial_shipped:'部分发货', shipped:'已发货', settled:'已结算',
  cancelled:'已取消', voided:'已作废', completed:'已完成' }
const statusTxt = s => SO_STATUS[s] || s || '-'
const statusCls = s => {
  if (['shipped','settled','completed'].includes(s)) return 'bo'
  if (['draft','pending_approval'].includes(s)) return 'bi'
  if (['cancelled','voided'].includes(s)) return 'be'
  return 'bw'
}
function dueCls(d) {
  if (!d) return 'bi'
  const today = new Date(); today.setHours(0,0,0,0)
  const dd = new Date(d)
  const days = (dd - today) / 86400000
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
    c.setOption(singleBar(ov.monthTrend || [], 'month', 'amount', '订单金额', '#5b8def', '元')) }
  if (c01.value) { const c = echarts.init(c01.value); charts.push(c); c.setOption(donut(ov.statusPie, n => SO_STATUS[n])) }
  if (c02.value) { const c = echarts.init(c02.value); charts.push(c)
    c.setOption(singleBar(ov.monthTrend || [], 'month', 'count', '订单笔数', '#00ff9d', '笔')) }

  const cu = data?.customer || {}
  if (c10.value) { const c = echarts.init(c10.value); charts.push(c); c.setOption(horizontalBar(cu.amountTop, '元')) }
  if (c11.value) { const c = echarts.init(c11.value); charts.push(c); c.setOption(horizontalBar(cu.countTop, '笔')) }

  const pf = data?.profit || {}
  if (c30.value) { const c = echarts.init(c30.value); charts.push(c); c.setOption(horizontalBar(pf.productAmountTop, '元')) }

  window.addEventListener('resize', () => charts.forEach(c => c && c.resize()))
}
</script>
