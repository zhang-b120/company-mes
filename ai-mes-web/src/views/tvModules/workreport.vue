<template>
  <TvShell module="workreport" title="班组报工战报" subtitle="WORK REPORT BATTLE BOARD"
           icon="📝" theme="green" :tabs="tabs" ticker-label="报工实时动态" @ready="onReady">
    <template #default="{ curTab, data }">
      <!-- TAB 0: 报工概览 -->
      <div :class="['tvm-panel', { active: curTab === 0 }]">
        <div class="tvm-kpi-row">
          <Kpi label="今日报工" :value="data?.overview?.todayReport" unit="次" k="k1" data-k="overview.todayReport"/>
          <Kpi label="本月报工" :value="data?.overview?.monthReport" unit="次" k="k4" data-k="overview.monthReport"/>
          <Kpi label="今日合格" :value="data?.overview?.todayQualified" unit="件" k="k2" data-k="overview.todayQualified"/>
          <Kpi label="本月合格" :value="data?.overview?.monthQualified" unit="件" k="k3" data-k="overview.monthQualified"/>
        </div>
        <div class="tvm-charts tvm-cr2">
          <Card title="近 14 日合格 vs 不良" tag="件" tagCls="ok"><div class="tvm-ci" ref="c00"></div></Card>
          <Card title="报工类型分布"><div class="tvm-ci" ref="c01"></div></Card>
        </div>
      </div>

      <!-- TAB 1: 个人 -->
      <div :class="['tvm-panel', { active: curTab === 1 }]">
        <div class="tvm-kpi-row">
          <Kpi label="本月活跃员工" :value="data?.personal?.activeReporter" unit="人" k="k1" data-k="personal.activeReporter"/>
          <Kpi label="本月计件工资" :value="data?.personal?.totalWageMonth" prefix="¥" unit="元" k="k2" data-k="personal.totalWageMonth"/>
          <Kpi label="人均产出" :value="data?.personal?.avgPersonOutput" unit="件" k="k4" data-k="personal.avgPersonOutput"/>
          <Kpi label="平均工时" :value="data?.personal?.avgWorkHours" unit="h" k="k3" data-k="personal.avgWorkHours"/>
        </div>
        <div class="tvm-charts tvm-cr2">
          <Card title="本月产出之星 TOP 8" tag="件" tagCls=""><div class="tvm-ci" ref="c10"></div></Card>
          <Card title="本月计件工资 TOP 8" tag="¥" tagCls="ok"><div class="tvm-ci" ref="c11"></div></Card>
        </div>
      </div>

      <!-- TAB 2: 班组 -->
      <div :class="['tvm-panel', { active: curTab === 2 }]">
        <div class="tvm-kpi-row">
          <Kpi label="班组总数" :value="data?.team?.teamCount" unit="个" k="k1" data-k="team.teamCount"/>
          <Kpi label="本月班组报工" :value="data?.team?.teamReportCount" unit="次" k="k4" data-k="team.teamReportCount"/>
          <Kpi label="班组成员" :value="data?.team?.totalMember" unit="人" k="k3" data-k="team.totalMember"/>
          <Kpi label="本月班组工资" :value="data?.team?.teamWageMonth" prefix="¥" unit="元" k="k2" data-k="team.teamWageMonth"/>
        </div>
        <div class="tvm-charts" style="grid-template-columns:1fr">
          <Card title="班组产出 TOP 8" tag="件" tagCls=""><div class="tvm-ci" ref="c20"></div></Card>
        </div>
      </div>

      <!-- TAB 3: 异常上报 -->
      <div :class="['tvm-panel', { active: curTab === 3 }]">
        <div class="tvm-kpi-row">
          <Kpi label="未处理异常" :value="data?.exception?.openException" unit="条" k="k5" data-k="exception.openException"
               :trendType="(data?.exception?.openException>0)?'dn':'eq'"/>
          <Kpi label="已升级异常" :value="data?.exception?.escalatedCount" unit="条" k="k3" data-k="exception.escalatedCount"/>
          <Kpi label="班组总数" :value="data?.team?.teamCount" unit="个" k="k1" data-k="team.teamCount"/>
          <Kpi label="班组成员" :value="data?.team?.totalMember" unit="人" k="k2" data-k="team.totalMember"/>
        </div>
        <div class="tvm-charts tvm-cr3">
          <Card title="异常紧急度"><div class="tvm-ci" ref="c30"></div></Card>
          <Card title="异常类型"><div class="tvm-ci" ref="c31"></div></Card>
          <Card title="最新异常上报" :tag="(data?.exception?.recentList?.length||0)+'条'" tagCls="bad">
            <div class="tvm-ci" style="overflow-y:auto">
              <table class="tvm-table">
                <thead><tr><th>编号</th><th>类型</th><th>紧急度</th><th>状态</th><th>描述</th></tr></thead>
                <tbody>
                  <tr v-for="(r, i) in (data?.exception?.recentList || [])" :key="i">
                    <td class="tvm-sub">{{ r.reportNo }}</td>
                    <td>{{ tr(EXCEPTION_TYPE, r.exceptionType) }}</td>
                    <td><span class="tvm-badge" :class="urgencyCls(r.urgency)">{{ urgencyTxt(r.urgency) }}</span></td>
                    <td><span class="tvm-badge" :class="r.status==='open'?'bw':'bo'">{{ r.status==='open'?'未处理':'已处理' }}</span></td>
                    <td class="tvm-sub" style="max-width:180px;overflow:hidden;text-overflow:ellipsis">{{ r.description }}</td>
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
import { echarts, donut, horizontalBar, dualBar } from './chartHelpers'
import { tr, EXCEPTION_TYPE } from './dicts'
import './tv-shared.scss'

const tabs = ['📊 报工概览', '🏆 个人战报', '👥 班组战报', '⚠ 异常上报']

const c00 = ref(null), c01 = ref(null)
const c10 = ref(null), c11 = ref(null), c20 = ref(null)
const c30 = ref(null), c31 = ref(null)
let charts = []

const RPT_TYPE = { individual:'个人报工', team:'班组报工' }
const URG = { urgent:'紧急', high:'高', normal:'普通', low:'低' }
const urgencyTxt = u => URG[u] || u || '普通'
const urgencyCls = u => ({ urgent:'be', high:'bw', normal:'bi', low:'bo' }[u] || 'bi')

function onReady(data) {
  charts.forEach(c => c && c.dispose()); charts = []
  nextTick(() => render(data))
}
function render(data) {
  const ov = data?.overview || {}
  if (c00.value) { const c = echarts.init(c00.value); charts.push(c)
    c.setOption(dualBar(ov.trend || [], 'date', 'qualified', 'defect', '合格', '不良', '#22e58d', '#ff4466')) }
  if (c01.value) { const c = echarts.init(c01.value); charts.push(c); c.setOption(donut(ov.typePie, n => RPT_TYPE[n])) }

  const ps = data?.personal || {}
  if (c10.value) { const c = echarts.init(c10.value); charts.push(c); c.setOption(horizontalBar(ps.personOutputTop, '件')) }
  if (c11.value) { const c = echarts.init(c11.value); charts.push(c); c.setOption(horizontalBar(ps.personWageTop, '元')) }

  const tm = data?.team || {}
  if (c20.value) { const c = echarts.init(c20.value); charts.push(c); c.setOption(horizontalBar(tm.teamOutputTop, '件')) }

  const ex = data?.exception || {}
  if (c30.value) { const c = echarts.init(c30.value); charts.push(c); c.setOption(donut(ex.urgencyPie, n => URG[n])) }
  if (c31.value) { const c = echarts.init(c31.value); charts.push(c); c.setOption(donut(ex.typePie, n => tr(EXCEPTION_TYPE, n))) }

  window.addEventListener('resize', () => charts.forEach(c => c && c.resize()))
}
</script>
