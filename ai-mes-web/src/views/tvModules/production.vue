<template>
  <TvShell module="production" title="生产指挥中心" subtitle="PRODUCTION COMMAND CENTER"
           icon="⚙" theme="cyan" :tabs="tabs" ticker-label="生产实时动态" @ready="onReady">
    <template #default="{ curTab, data }">
      <!-- TAB 0: 工单概览 -->
      <div :class="['tvm-panel', { active: curTab === 0 }]">
        <div class="tvm-kpi-row">
          <Kpi label="工单总数" :value="data?.overview?.totalWo" unit="个" k="k1" data-k="overview.totalWo" trend="↑ 在制总量"/>
          <Kpi label="生产中" :value="data?.overview?.inProgressWo" unit="个" k="k3" data-k="overview.inProgressWo" trend="→ 正在生产"/>
          <Kpi label="已完工" :value="data?.overview?.completedWo" unit="个" k="k2" data-k="overview.completedWo" trend="↑ 累计完成"/>
          <Kpi label="待排产" :value="data?.overview?.scheduledWo" unit="个" k="k4" data-k="overview.scheduledWo" trend="→ 等待开工"/>
        </div>
        <div class="tvm-charts tvm-cr3">
          <Card title="近 7 日工单趋势" tag="实时" tagCls="ok"><div class="tvm-ci" ref="c00"></div></Card>
          <Card title="工单状态分布"><div class="tvm-ci" ref="c01"></div></Card>
          <Card title="产品工单 TOP 8"><div class="tvm-ci" ref="c02"></div></Card>
        </div>
      </div>

      <!-- TAB 1: 工序任务 -->
      <div :class="['tvm-panel', { active: curTab === 1 }]">
        <div class="tvm-kpi-row">
          <Kpi label="待执行" :value="data?.process?.pendingTask" unit="个" k="k1" data-k="process.pendingTask"/>
          <Kpi label="可执行" :value="data?.process?.readyTask" unit="个" k="k4" data-k="process.readyTask"/>
          <Kpi label="执行中" :value="data?.process?.inProgressTask" unit="个" k="k3" data-k="process.inProgressTask"/>
          <Kpi label="已完成" :value="data?.process?.completedTask" unit="个" k="k2" data-k="process.completedTask"/>
        </div>
        <div class="tvm-charts tvm-cr3">
          <Card title="工序任务状态分布"><div class="tvm-ci" ref="c10"></div></Card>
          <Card title="高频工序 TOP 8"><div class="tvm-ci" ref="c11"></div></Card>
          <Card title="正在生产的工序" :tag="(data?.process?.currentTaskList?.length||0) + '项'" tagCls="warn">
            <div class="tvm-ci" style="overflow-y:auto">
              <table class="tvm-table">
                <thead><tr><th>工单</th><th>工序</th><th>责任人/班组</th><th>计划/已报</th></tr></thead>
                <tbody>
                  <tr v-for="(r, i) in (data?.process?.currentTaskList || [])" :key="i">
                    <td class="tvm-sub">{{ r.woNo }}</td>
                    <td>{{ r.stepName }}</td>
                    <td>{{ r.assignee || '-' }}</td>
                    <td>{{ r.reportedQty || 0 }} / {{ r.planQty || 0 }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </Card>
        </div>
      </div>

      <!-- TAB 2: 产能效率 -->
      <div :class="['tvm-panel', { active: curTab === 2 }]">
        <div class="tvm-kpi-row">
          <Kpi label="今日产出" :value="data?.capacity?.todayOutput" unit="件" k="k1" data-k="capacity.todayOutput"/>
          <Kpi label="本月产出" :value="data?.capacity?.monthOutput" unit="件" k="k2" data-k="capacity.monthOutput"/>
          <Kpi label="平均工时" :value="data?.capacity?.avgCycleHours" unit="h" k="k4" data-k="capacity.avgCycleHours"/>
          <Kpi label="逾期工单" :value="data?.capacity?.delayedWo" unit="个" k="k5" data-k="capacity.delayedWo"
               :trendType="(data?.capacity?.delayedWo>0)?'dn':'eq'"
               :trend="(data?.capacity?.delayedWo>0)?`⚠ ${data.capacity.delayedWo} 个已逾期`:'→ 无逾期'"/>
        </div>
        <div class="tvm-charts tvm-cr2">
          <Card title="近 14 日合格 vs 不良产出" tag="件" tagCls="ok"><div class="tvm-ci" ref="c20"></div></Card>
          <Card title="近 7 日工单计划 vs 完成"><div class="tvm-ci" ref="c21"></div></Card>
        </div>
      </div>

      <!-- TAB 3: 异常监控 -->
      <div :class="['tvm-panel', { active: curTab === 3 }]">
        <div class="tvm-kpi-row">
          <Kpi label="异常总数" :value="data?.exception?.totalException" unit="条" k="k1" data-k="exception.totalException"/>
          <Kpi label="未处理" :value="data?.exception?.openException" unit="条" k="k5" data-k="exception.openException"
               :trendType="(data?.exception?.openException>0)?'dn':'eq'"
               :trend="(data?.exception?.openException>0)?'⚠ 待处理':'→ 已清零'"/>
          <Kpi label="已升级" :value="data?.exception?.escalatedCount" unit="条" k="k3" data-k="exception.escalatedCount"/>
          <Kpi label="逾期工单" :value="data?.exception?.delayedWo" unit="个" k="k4" data-k="exception.delayedWo"/>
        </div>
        <div class="tvm-charts tvm-cr2">
          <Card title="异常类型分布"><div class="tvm-ci" ref="c30"></div></Card>
          <Card title="异常上报最新动态" :tag="(data?.exception?.recentList?.length||0) + '条'" tagCls="bad">
            <div class="tvm-ci" style="overflow-y:auto">
              <table class="tvm-table">
                <thead><tr><th>编号</th><th>类型</th><th>紧急度</th><th>状态</th><th>描述</th></tr></thead>
                <tbody>
                  <tr v-for="(r, i) in (data?.exception?.recentList || [])" :key="i">
                    <td class="tvm-sub">{{ r.reportNo }}</td>
                    <td>{{ tr(EXCEPTION_TYPE, r.exceptionType) }}</td>
                    <td><span class="tvm-badge" :class="urgencyCls(r.urgency)">{{ urgencyTxt(r.urgency) }}</span></td>
                    <td><span class="tvm-badge" :class="r.status==='open'?'bw':'bo'">{{ r.status==='open'?'未处理':'已处理' }}</span></td>
                    <td class="tvm-sub" style="max-width:200px;overflow:hidden;text-overflow:ellipsis">{{ r.description }}</td>
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
import { echarts, base, xAxis, yAxis, donut, horizontalBar, dualLine, dualBar, gradArea, pickColor, pieItem, pieLabelLine, legend } from './chartHelpers'
import { tr, EXCEPTION_TYPE } from './dicts'
import './tv-shared.scss'

const tabs = ['⚙ 工单概览', '🔩 工序任务', '📊 产能效率', '⚠ 异常监控']

const c00 = ref(null), c01 = ref(null), c02 = ref(null)
const c10 = ref(null), c11 = ref(null)
const c20 = ref(null), c21 = ref(null)
const c30 = ref(null)
let charts = []

const WO_STATUS = { pending: '待排产', scheduled: '已排产', in_progress: '生产中', completed: '已完工', cancelled: '已取消' }
const TASK_STATUS = { pending: '待执行', ready: '可执行', in_progress: '执行中', completed: '已完成' }
const URGENCY = { urgent: '紧急', high: '高', normal: '普通', low: '低' }
const urgencyTxt = u => URGENCY[u] || u || '普通'
const urgencyCls = u => ({ urgent: 'be', high: 'bw', normal: 'bi', low: 'bo' }[u] || 'bi')

function onReady(data) {
  charts.forEach(c => c && c.dispose()); charts = []
  nextTick(() => render(data))
}

function render(data) {
  const ov = data?.overview || {}
  if (c00.value) { const c = echarts.init(c00.value); charts.push(c)
    c.setOption(dualLine(ov.trend || [], 'date', 'planned', 'completed', '新建工单', '完成工单')) }
  if (c01.value) { const c = echarts.init(c01.value); charts.push(c); c.setOption(donut(ov.statusPie, n => WO_STATUS[n])) }
  if (c02.value) { const c = echarts.init(c02.value); charts.push(c); c.setOption(horizontalBar(ov.productTop, '个')) }

  const pr = data?.process || {}
  if (c10.value) { const c = echarts.init(c10.value); charts.push(c); c.setOption(donut(pr.statusPie, n => TASK_STATUS[n])) }
  if (c11.value) { const c = echarts.init(c11.value); charts.push(c); c.setOption(horizontalBar(pr.stepTop, '个')) }

  const cap = data?.capacity || {}
  if (c20.value) { const c = echarts.init(c20.value); charts.push(c)
    c.setOption(dualBar(cap.outputTrend || [], 'date', 'qualified', 'defect', '合格', '不良', '#00ff9d', '#ff4466')) }
  if (c21.value) { const c = echarts.init(c21.value); charts.push(c)
    c.setOption(dualLine(ov.trend || [], 'date', 'planned', 'completed', '新建', '完成')) }

  const ex = data?.exception || {}
  if (c30.value) { const c = echarts.init(c30.value); charts.push(c); c.setOption(donut(ex.typePie, n => tr(EXCEPTION_TYPE, n))) }

  const handler = () => charts.forEach(c => c && c.resize())
  window.addEventListener('resize', handler, { once: false })
}
</script>
