<template>
  <TvShell module="quality" title="质量管控中心" subtitle="QUALITY CONTROL CENTER"
           icon="✓" theme="green" :tabs="tabs" ticker-label="质检实时动态" @ready="onReady">
    <template #default="{ curTab, data }">
      <!-- TAB 0: 质检概览 -->
      <div :class="['tvm-panel', { active: curTab === 0 }]">
        <div class="tvm-kpi-row">
          <Kpi label="累计批次" :value="data?.overview?.totalTask" unit="批" k="k1" data-k="overview.totalTask"/>
          <Kpi label="合格批次" :value="data?.overview?.passCount" unit="批" k="k2" data-k="overview.passCount"/>
          <Kpi label="不合格" :value="data?.overview?.failCount" unit="批" k="k5" data-k="overview.failCount"
               :trendType="(data?.overview?.failCount>0)?'dn':'eq'"/>
          <Kpi label="综合合格率" :value="data?.overview?.passRate" unit="%" k="k4" data-k="overview.passRate" trend="↑ 良好"/>
        </div>
        <div class="tvm-charts tvm-cr2">
          <Card title="质检类型分布"><div class="tvm-ci" ref="c00"></div></Card>
          <Card title="质检结果分布"><div class="tvm-ci" ref="c01"></div></Card>
        </div>
      </div>

      <!-- TAB 1: 趋势分析 -->
      <div :class="['tvm-panel', { active: curTab === 1 }]">
        <div class="tvm-kpi-row">
          <Kpi label="本月批次" :value="data?.trend?.monthBatch" unit="批" k="k1" data-k="trend.monthBatch"/>
          <Kpi label="本月合格" :value="data?.trend?.monthPass" unit="批" k="k2" data-k="trend.monthPass"/>
          <Kpi label="本月不合格" :value="data?.trend?.monthFail" unit="批" k="k5" data-k="trend.monthFail"/>
          <Kpi label="本月合格率" :value="data?.trend?.monthPassRate" unit="%" k="k4" data-k="trend.monthPassRate"/>
        </div>
        <div class="tvm-charts" style="grid-template-columns:1fr">
          <Card title="近 8 周检验批次 + 合格率" tag="实时" tagCls="ok"><div class="tvm-ci" ref="c10"></div></Card>
        </div>
      </div>

      <!-- TAB 2: 不良缺陷 -->
      <div :class="['tvm-panel', { active: curTab === 2 }]">
        <div class="tvm-kpi-row">
          <Kpi label="不良记录" :value="data?.defect?.totalDefect" unit="条" k="k1" data-k="defect.totalDefect"/>
          <Kpi label="不良数量" :value="data?.defect?.totalDefectQty" unit="件" k="k3" data-k="defect.totalDefectQty"/>
          <Kpi label="报废数量" :value="data?.defect?.scrapQty" unit="件" k="k5" data-k="defect.scrapQty"/>
          <Kpi label="报废损失" :value="data?.defect?.scrapCost" prefix="¥" unit="元" k="k4" data-k="defect.scrapCost"/>
        </div>
        <div class="tvm-charts tvm-cr2">
          <Card title="缺陷类型分布"><div class="tvm-ci" ref="c20"></div></Card>
          <Card title="缺陷等级分布"><div class="tvm-ci" ref="c21"></div></Card>
        </div>
      </div>

      <!-- TAB 3: 质检任务 -->
      <div :class="['tvm-panel', { active: curTab === 3 }]">
        <div class="tvm-kpi-row">
          <Kpi label="待检任务" :value="data?.task?.toInspect" unit="个" k="k1" data-k="task.toInspect"/>
          <Kpi label="待处置不良" :value="data?.task?.undisposed" unit="个" k="k5" data-k="task.undisposed"/>
          <Kpi label="活跃返修单" :value="data?.task?.reworkOrder" unit="单" k="k3" data-k="task.reworkOrder"/>
          <Kpi label="质检模板" :value="data?.task?.templateCount" unit="个" k="k2" data-k="task.templateCount"/>
        </div>
        <div class="tvm-charts" style="grid-template-columns:1fr">
          <Card title="最新质检任务" :tag="(data?.task?.recentList?.length||0) + '项'" tagCls="ok">
            <div class="tvm-ci" style="overflow-y:auto">
              <table class="tvm-table">
                <thead><tr><th>任务号</th><th>类型</th><th>结果</th><th>批次</th><th>物料</th><th>时间</th></tr></thead>
                <tbody>
                  <tr v-for="(r, i) in (data?.task?.recentList || [])" :key="i">
                    <td class="tvm-sub">{{ r.taskNo }}</td>
                    <td>{{ qcTypeTxt(r.qcType) }}</td>
                    <td><span class="tvm-badge" :class="resultCls(r.result)">{{ resultTxt(r.result) }}</span></td>
                    <td class="tvm-sub">{{ r.batchNo || '-' }}</td>
                    <td>{{ r.materialName }}</td>
                    <td class="tvm-sub">{{ formatDt(r.createTime) }}</td>
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
import { echarts, base, xAxis, donut } from './chartHelpers'
import { tr, DEFECT_LEVEL } from './dicts'
import './tv-shared.scss'

const tabs = ['✓ 质检概览', '📈 趋势分析', '⚠ 不良缺陷', '📋 质检任务']

const c00 = ref(null), c01 = ref(null)
const c10 = ref(null)
const c20 = ref(null), c21 = ref(null)
let charts = []

const QC_TYPE = { incoming:'来料检', process:'过程检', final:'成品检', outgoing:'出货检', outsource:'外协检' }
const QC_RESULT = { pending:'待检', pass:'合格', fail:'不合格' }
const qcTypeTxt = t => QC_TYPE[t] || t || '-'
const resultTxt = r => QC_RESULT[r] || r || '-'
const resultCls = r => ({ pending:'bi', pass:'bo', fail:'be' }[r] || 'bi')
function formatDt(s) { return s ? String(s).replace('T',' ').substring(5,16) : '-' }

function onReady(data) {
  charts.forEach(c => c && c.dispose()); charts = []
  nextTick(() => render(data))
}
function render(data) {
  const ov = data?.overview || {}
  if (c00.value) { const c = echarts.init(c00.value); charts.push(c); c.setOption(donut(ov.typePie)) }
  if (c01.value) { const c = echarts.init(c01.value); charts.push(c); c.setOption(donut(ov.resultPie, r => QC_RESULT[r])) }

  const tr = data?.trend || {}
  if (c10.value) { const c = echarts.init(c10.value); charts.push(c)
    const wt = tr.weekTrend || []
    c.setOption({
      ...base(),
      legend: { top: 4, right: 8, textStyle: { color: '#a8b8c8', fontSize: 11 }, data: ['合格率%','检验批次'] },
      xAxis: xAxis(wt.map(r => r.week)),
      yAxis: [
        { type:'value', min:80, max:100, splitLine: { lineStyle: { color: 'rgba(120,160,200,.08)', type: 'dashed' } },
          axisLabel: { color:'#8aa0b8', fontSize:10, formatter:v=>v+'%' }, axisLine: {show:false}, axisTick:{show:false} },
        { type:'value', name:'批', nameTextStyle:{color:'#8aa0b8',fontSize:10}, splitLine:{show:false},
          axisLabel:{color:'#8aa0b8',fontSize:10}, axisLine:{show:false}, axisTick:{show:false} }
      ],
      series: [
        { name:'合格率%', type:'line', smooth:true, data: wt.map(r=>Number(r.passRate||0)),
          lineStyle:{color:'#00ff9d',width:2.5}, itemStyle:{color:'#00ff9d'}, symbol:'circle', symbolSize:6,
          areaStyle:{color:{type:'linear',x:0,y:0,x2:0,y2:1,colorStops:[{offset:0,color:'rgba(0,255,157,.3)'},{offset:1,color:'rgba(0,255,157,0)'}]}} },
        { name:'检验批次', type:'bar', yAxisIndex:1, barMaxWidth:18, data: wt.map(r=>Number(r.batch||0)),
          itemStyle:{color:'rgba(0,212,255,.5)', borderRadius:[3,3,0,0]} }
      ]
    })
  }

  const df = data?.defect || {}
  if (c20.value) { const c = echarts.init(c20.value); charts.push(c); c.setOption(donut(df.defectPie)) }
  if (c21.value) { const c = echarts.init(c21.value); charts.push(c); c.setOption(donut(df.levelPie, n => tr(DEFECT_LEVEL, n))) }

  window.addEventListener('resize', () => charts.forEach(c => c && c.resize()))
}
</script>
