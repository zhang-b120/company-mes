<template>
  <TvShell module="finance" title="财务驾驶舱" subtitle="FINANCE COCKPIT"
           icon="💰" theme="gold" :tabs="tabs" ticker-label="财务实时动态" @ready="onReady">
    <template #default="{ curTab, data }">
      <!-- TAB 0: 资金概览 -->
      <div :class="['tvm-panel', { active: curTab === 0 }]">
        <div class="tvm-kpi-row">
          <Kpi label="本月营收" :value="data?.overview?.monthRevenue" prefix="¥" unit="元" k="k2" data-k="overview.monthRevenue"/>
          <Kpi label="本月支出" :value="data?.overview?.monthExpense" prefix="¥" unit="元" k="k3" data-k="overview.monthExpense"/>
          <Kpi label="应收余额" :value="data?.overview?.totalReceivable" prefix="¥" unit="元" k="k1" data-k="overview.totalReceivable"/>
          <Kpi label="应付余额" :value="data?.overview?.totalPayable" prefix="¥" unit="元" k="k5" data-k="overview.totalPayable"/>
        </div>
        <div class="tvm-charts" style="grid-template-columns:1fr">
          <Card title="近 12 月营收 vs 支出" tag="元" tagCls="ok"><div class="tvm-ci" ref="c00"></div></Card>
        </div>
      </div>

      <!-- TAB 1: 应收账款 -->
      <div :class="['tvm-panel', { active: curTab === 1 }]">
        <div class="tvm-kpi-row">
          <Kpi label="应收单数" :value="data?.ar?.totalAr" unit="单" k="k1" data-k="ar.totalAr"/>
          <Kpi label="待收款" :value="data?.ar?.pendingAr" unit="单" k="k4" data-k="ar.pendingAr"/>
          <Kpi label="部分收款" :value="data?.ar?.partialAr" unit="单" k="k3" data-k="ar.partialAr"/>
          <Kpi label="逾期单数" :value="data?.ar?.overdueAr" unit="单" k="k5" data-k="ar.overdueAr"
               :trendType="(data?.ar?.overdueAr>0)?'dn':'eq'"/>
        </div>
        <div class="tvm-charts tvm-cr2">
          <Card title="应收状态分布"><div class="tvm-ci" ref="c10"></div></Card>
          <Card title="应收金额 TOP" :tag="(data?.ar?.topList?.length||0)+'单'" tagCls="warn">
            <div class="tvm-ci" style="overflow-y:auto">
              <table class="tvm-table">
                <thead><tr><th>应收号</th><th>客户</th><th>未收</th><th>到期</th><th>状态</th></tr></thead>
                <tbody>
                  <tr v-for="(r, i) in (data?.ar?.topList || [])" :key="i">
                    <td class="tvm-sub">{{ r.rno }}</td>
                    <td>{{ r.customerName }}</td>
                    <td style="color:var(--orange)">¥ {{ formatAmt(r.unreceived) }}</td>
                    <td><span class="tvm-badge" :class="dueCls(r.dueDate)">{{ r.dueDate || '-' }}</span></td>
                    <td><span class="tvm-badge" :class="arCls(r.status)">{{ arTxt(r.status) }}</span></td>
                  </tr>
                </tbody>
              </table>
            </div>
          </Card>
        </div>
      </div>

      <!-- TAB 2: 应付账款 -->
      <div :class="['tvm-panel', { active: curTab === 2 }]">
        <div class="tvm-kpi-row">
          <Kpi label="应付单数" :value="data?.ap?.totalAp" unit="单" k="k1" data-k="ap.totalAp"/>
          <Kpi label="待付款" :value="data?.ap?.pendingAp" unit="单" k="k4" data-k="ap.pendingAp"/>
          <Kpi label="部分付款" :value="data?.ap?.partialAp" unit="单" k="k3" data-k="ap.partialAp"/>
          <Kpi label="逾期单数" :value="data?.ap?.overdueAp" unit="单" k="k5" data-k="ap.overdueAp"
               :trendType="(data?.ap?.overdueAp>0)?'dn':'eq'"/>
        </div>
        <div class="tvm-charts tvm-cr2">
          <Card title="应付状态分布"><div class="tvm-ci" ref="c20"></div></Card>
          <Card title="应付金额 TOP" :tag="(data?.ap?.topList?.length||0)+'单'" tagCls="warn">
            <div class="tvm-ci" style="overflow-y:auto">
              <table class="tvm-table">
                <thead><tr><th>应付号</th><th>供应商</th><th>未付</th><th>到期</th><th>状态</th></tr></thead>
                <tbody>
                  <tr v-for="(r, i) in (data?.ap?.topList || [])" :key="i">
                    <td class="tvm-sub">{{ r.pno }}</td>
                    <td>{{ r.supplierName }}</td>
                    <td style="color:var(--orange)">¥ {{ formatAmt(r.unpaid) }}</td>
                    <td><span class="tvm-badge" :class="dueCls(r.dueDate)">{{ r.dueDate || '-' }}</span></td>
                    <td><span class="tvm-badge" :class="apCls(r.status)">{{ apTxt(r.status) }}</span></td>
                  </tr>
                </tbody>
              </table>
            </div>
          </Card>
        </div>
      </div>

      <!-- TAB 3: 成本分析 -->
      <div :class="['tvm-panel', { active: curTab === 3 }]">
        <div class="tvm-kpi-row">
          <Kpi label="累计总成本" :value="data?.cost?.totalCost" prefix="¥" unit="元" k="k5" data-k="cost.totalCost"/>
          <Kpi label="物料成本" :value="data?.cost?.materialCost" prefix="¥" unit="元" k="k1" data-k="cost.materialCost"/>
          <Kpi label="人工成本" :value="data?.cost?.laborCost" prefix="¥" unit="元" k="k2" data-k="cost.laborCost"/>
          <Kpi label="外协成本" :value="data?.cost?.outsourceCost" prefix="¥" unit="元" k="k4" data-k="cost.outsourceCost"/>
        </div>
        <div class="tvm-charts tvm-cr2">
          <Card title="成本结构占比"><div class="tvm-ci" ref="c30"></div></Card>
          <Card title="高成本工单 TOP" tag="¥" tagCls="warn">
            <div class="tvm-ci" style="overflow-y:auto">
              <table class="tvm-table">
                <thead><tr><th>工单</th><th>产品</th><th>总成本</th></tr></thead>
                <tbody>
                  <tr v-for="(r, i) in (data?.cost?.highCostWo || [])" :key="i">
                    <td class="tvm-sub">{{ r.woNo }}</td>
                    <td>{{ r.productName }}</td>
                    <td style="color:var(--gold)">¥ {{ formatAmt(r.totalCost) }}</td>
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
import { echarts, base, xAxis, yAxis, donut, dualBar, gradArea } from './chartHelpers'
import './tv-shared.scss'

const tabs = ['💰 资金概览', '📥 应收账款', '📤 应付账款', '🏷 成本分析']

const c00 = ref(null)
const c10 = ref(null), c20 = ref(null), c30 = ref(null)
let charts = []

const AR_STATUS = { pending:'待收款', partial:'部分收款', received:'已收清' }
const AP_STATUS = { pending:'待付款', partial:'部分付款', paid:'已付清' }
const arTxt = s => AR_STATUS[s] || s || '-'
const apTxt = s => AP_STATUS[s] || s || '-'
const arCls = s => ({ pending:'bw', partial:'bi', received:'bo' }[s] || 'bw')
const apCls = s => ({ pending:'bw', partial:'bi', paid:'bo' }[s] || 'bw')
function dueCls(d) {
  if (!d) return 'bi'
  const today = new Date(); today.setHours(0,0,0,0)
  const days = (new Date(d) - today) / 86400000
  if (days < 0) return 'be'
  if (days <= 7) return 'bw'
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
    c.setOption(dualBar(ov.monthTrend || [], 'month', 'revenue', 'expense', '营收', '支出', '#22e58d', '#ff8c28')) }

  const ar = data?.ar || {}
  if (c10.value) { const c = echarts.init(c10.value); charts.push(c); c.setOption(donut(ar.statusPie, n => AR_STATUS[n])) }

  const ap = data?.ap || {}
  if (c20.value) { const c = echarts.init(c20.value); charts.push(c); c.setOption(donut(ap.statusPie, n => AP_STATUS[n])) }

  const co = data?.cost || {}
  if (c30.value) { const c = echarts.init(c30.value); charts.push(c); c.setOption(donut(co.costPie)) }

  window.addEventListener('resize', () => charts.forEach(c => c && c.resize()))
}
</script>
