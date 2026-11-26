<template>
  <TvShell module="mrp" title="MRP 智能运算中心" subtitle="MRP INTELLIGENT ENGINE"
           icon="🧠" theme="purple" :tabs="tabs" ticker-label="MRP 实时动态" @ready="onReady">
    <template #default="{ curTab, data }">
      <!-- TAB 0: 运算总览 -->
      <div :class="['tvm-panel', { active: curTab === 0 }]">
        <div class="tvm-kpi-row">
          <Kpi label="运算总次数" :value="data?.overview?.totalRun" unit="次" k="k1" data-k="overview.totalRun"/>
          <Kpi label="本月运算" :value="data?.overview?.monthRun" unit="次" k="k4" data-k="overview.monthRun"/>
          <Kpi label="成功次数" :value="data?.overview?.successRun" unit="次" k="k2" data-k="overview.successRun"/>
          <Kpi label="失败次数" :value="data?.overview?.failedRun" unit="次" k="k5" data-k="overview.failedRun"/>
        </div>
        <div class="tvm-charts tvm-cr3">
          <Card title="运算状态分布"><div class="tvm-ci" ref="c00"></div></Card>
          <Card title="运算类型分布"><div class="tvm-ci" ref="c01"></div></Card>
          <Card title="近期运算记录" :tag="(data?.overview?.recentRun?.length||0)+'次'" tagCls="ok">
            <div class="tvm-ci" style="overflow-y:auto">
              <table class="tvm-table">
                <thead><tr><th>编号</th><th>类型</th><th>状态</th><th>订单</th><th>建议</th><th>时间</th></tr></thead>
                <tbody>
                  <tr v-for="(r, i) in (data?.overview?.recentRun || [])" :key="i">
                    <td class="tvm-sub">{{ r.runNo }}</td>
                    <td>{{ tr(MRP_RUN_TYPE, r.runType) }}</td>
                    <td><span class="tvm-badge" :class="runCls(r.status)">{{ tr(MRP_RUN_STATUS, r.status) }}</span></td>
                    <td>{{ r.orderCount || 0 }}</td>
                    <td>{{ r.resultCount || 0 }}</td>
                    <td class="tvm-sub">{{ formatDt(r.runTime) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </Card>
        </div>
      </div>

      <!-- TAB 1: 需求建议 -->
      <div :class="['tvm-panel', { active: curTab === 1 }]">
        <div class="tvm-kpi-row">
          <Kpi label="需求项总数" :value="data?.demand?.totalDemand" unit="项" k="k1" data-k="demand.totalDemand"/>
          <Kpi label="采购建议" :value="data?.demand?.totalPurchaseSug" unit="条" k="k4" data-k="demand.totalPurchaseSug"/>
          <Kpi label="生产建议" :value="data?.demand?.totalProductionSug" unit="条" k="k3" data-k="demand.totalProductionSug"/>
          <Kpi label="待确认采购" :value="data?.demand?.pendingPurchaseSug" unit="条" k="k5" data-k="demand.pendingPurchaseSug"/>
        </div>
        <div class="tvm-charts tvm-cr2">
          <Card title="采购建议状态分布"><div class="tvm-ci" ref="c10"></div></Card>
          <Card title="生产建议状态分布"><div class="tvm-ci" ref="c11"></div></Card>
        </div>
      </div>

      <!-- TAB 2: 生产计划 -->
      <div :class="['tvm-panel', { active: curTab === 2 }]">
        <div class="tvm-kpi-row">
          <Kpi label="计划总数" :value="data?.plan?.totalPlan" unit="项" k="k1" data-k="plan.totalPlan"/>
          <Kpi label="草稿计划" :value="data?.plan?.draftPlan" unit="项" k="k4" data-k="plan.draftPlan"/>
          <Kpi label="已发布" :value="data?.plan?.releasedPlan" unit="项" k="k3" data-k="plan.releasedPlan"/>
          <Kpi label="已完成" :value="data?.plan?.completedPlan" unit="项" k="k2" data-k="plan.completedPlan"/>
        </div>
        <div class="tvm-charts tvm-cr2">
          <Card title="计划状态分布"><div class="tvm-ci" ref="c20"></div></Card>
          <Card title="近期计划" :tag="(data?.plan?.recentPlan?.length||0)+'项'" tagCls="">
            <div class="tvm-ci" style="overflow-y:auto">
              <table class="tvm-table">
                <thead><tr><th>计划号</th><th>状态</th><th>创建时间</th></tr></thead>
                <tbody>
                  <tr v-for="(r, i) in (data?.plan?.recentPlan || [])" :key="i">
                    <td class="tvm-sub">{{ r.planNo }}</td>
                    <td><span class="tvm-badge" :class="planCls(r.status)">{{ planTxt(r.status) }}</span></td>
                    <td class="tvm-sub">{{ formatDt(r.createTime) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </Card>
        </div>
      </div>

      <!-- TAB 3: TOP 物料 -->
      <div :class="['tvm-panel', { active: curTab === 3 }]">
        <div class="tvm-kpi-row">
          <Kpi label="采购建议条数" :value="data?.demand?.totalPurchaseSug" unit="条" k="k1" data-k="demand.totalPurchaseSug"/>
          <Kpi label="生产建议条数" :value="data?.demand?.totalProductionSug" unit="条" k="k3" data-k="demand.totalProductionSug"/>
          <Kpi label="待确认采购" :value="data?.demand?.pendingPurchaseSug" unit="条" k="k5" data-k="demand.pendingPurchaseSug"/>
          <Kpi label="计划总数" :value="data?.plan?.totalPlan" unit="项" k="k2" data-k="plan.totalPlan"/>
        </div>
        <div class="tvm-charts tvm-cr2">
          <Card title="采购物料 TOP 8" tag="件" tagCls=""><div class="tvm-ci" ref="c30"></div></Card>
          <Card title="生产产品 TOP 8"><div class="tvm-ci" ref="c31"></div></Card>
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
import { echarts, donut, horizontalBar } from './chartHelpers'
import { tr, MRP_RUN_STATUS, MRP_RUN_TYPE, MRP_SUG_STATUS, PLAN_STATUS } from './dicts'
import './tv-shared.scss'

const tabs = ['🧠 运算总览', '💡 需求建议', '📋 生产计划', '🏆 TOP 物料']

const c00 = ref(null), c01 = ref(null)
const c10 = ref(null), c11 = ref(null)
const c20 = ref(null), c30 = ref(null), c31 = ref(null)
let charts = []

const planTxt = s => tr(PLAN_STATUS, s)
const planCls = s => ({ draft:'bi', released:'bw', in_progress:'bw', completed:'bo', cancelled:'be' }[s] || 'bi')
const runCls = s => ({ success:'bo', failed:'be', running:'bw', cancelled:'be', pending:'bi' }[s] || 'bi')
function formatDt(s) { return s ? String(s).replace('T',' ').substring(5,16) : '-' }

function onReady(data) {
  charts.forEach(c => c && c.dispose()); charts = []
  nextTick(() => render(data))
}
function render(data) {
  const ov = data?.overview || {}
  if (c00.value) { const c = echarts.init(c00.value); charts.push(c); c.setOption(donut(ov.statusPie, n => tr(MRP_RUN_STATUS, n))) }
  if (c01.value) { const c = echarts.init(c01.value); charts.push(c); c.setOption(donut(ov.typePie, n => tr(MRP_RUN_TYPE, n))) }

  const dm = data?.demand || {}
  if (c10.value) { const c = echarts.init(c10.value); charts.push(c); c.setOption(donut(dm.purchaseStatusPie, n => tr(MRP_SUG_STATUS, n))) }
  if (c11.value) { const c = echarts.init(c11.value); charts.push(c); c.setOption(donut(dm.productionStatusPie, n => tr(MRP_SUG_STATUS, n))) }

  const pl = data?.plan || {}
  if (c20.value) { const c = echarts.init(c20.value); charts.push(c); c.setOption(donut(pl.statusPie, n => tr(PLAN_STATUS, n))) }

  const sg = data?.suggestion || {}
  if (c30.value) { const c = echarts.init(c30.value); charts.push(c); c.setOption(horizontalBar(sg.topPurchaseList, '件')) }
  if (c31.value) { const c = echarts.init(c31.value); charts.push(c); c.setOption(horizontalBar(sg.topProductionList, '件')) }

  window.addEventListener('resize', () => charts.forEach(c => c && c.resize()))
}
</script>
