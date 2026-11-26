<template>
  <TvShell module="inventory" title="库存调度中心" subtitle="INVENTORY DISPATCH CENTER"
           icon="📦" theme="teal" :tabs="tabs" ticker-label="库存实时动态" @ready="onReady">
    <template #default="{ curTab, data }">
      <!-- TAB 0: 库存总览 -->
      <div :class="['tvm-panel', { active: curTab === 0 }]">
        <div class="tvm-kpi-row">
          <Kpi label="物料种类" :value="data?.overview?.materialKinds" unit="种" k="k1" data-k="overview.materialKinds"/>
          <Kpi label="仓库数量" :value="data?.overview?.warehouseCount" unit="个" k="k2" data-k="overview.warehouseCount"/>
          <Kpi label="总库存量" :value="data?.overview?.totalStock" unit="件" k="k4" data-k="overview.totalStock"/>
          <Kpi label="锁定库存" :value="data?.overview?.lockedStock" unit="件" k="k3" data-k="overview.lockedStock"/>
        </div>
        <div class="tvm-charts tvm-cr2">
          <Card title="按仓库库存占比"><div class="tvm-ci" ref="c00"></div></Card>
          <Card title="按物料类别占比"><div class="tvm-ci" ref="c01"></div></Card>
        </div>
      </div>

      <!-- TAB 1: 出入库流水 -->
      <div :class="['tvm-panel', { active: curTab === 1 }]">
        <div class="tvm-kpi-row">
          <Kpi label="今日入库" :value="data?.flow?.todayIn" unit="笔" k="k2" data-k="flow.todayIn"/>
          <Kpi label="今日出库" :value="data?.flow?.todayOut" unit="笔" k="k3" data-k="flow.todayOut"/>
          <Kpi label="本月入库" :value="data?.flow?.monthIn" unit="笔" k="k1" data-k="flow.monthIn"/>
          <Kpi label="本月出库" :value="data?.flow?.monthOut" unit="笔" k="k4" data-k="flow.monthOut"/>
        </div>
        <div class="tvm-charts tvm-cr2">
          <Card title="近 30 日出入库趋势" tag="笔" tagCls="ok"><div class="tvm-ci" ref="c10"></div></Card>
          <Card title="本月业务类型分布"><div class="tvm-ci" ref="c11"></div></Card>
        </div>
      </div>

      <!-- TAB 2: 库存预警 -->
      <div :class="['tvm-panel', { active: curTab === 2 }]">
        <div class="tvm-kpi-row">
          <Kpi label="不足预警" :value="data?.warn?.shortageCount" unit="项" k="k5" data-k="warn.shortageCount"
               trendType="dn" trend="↓ 需补货"/>
          <Kpi label="超储预警" :value="data?.warn?.excessCount" unit="项" k="k3" data-k="warn.excessCount"/>
          <Kpi label="零库存" :value="data?.warn?.zeroCount" unit="项" k="k4" data-k="warn.zeroCount"/>
          <Kpi label="预留中" :value="data?.warn?.activeReservation" unit="条" k="k1" data-k="warn.activeReservation"/>
        </div>
        <div class="tvm-charts" style="grid-template-columns:1fr">
          <Card title="预警物料明细" :tag="(data?.warn?.warnList?.length||0) + '项'" tagCls="warn">
            <div class="tvm-ci" style="overflow-y:auto">
              <table class="tvm-table">
                <thead><tr><th>物料</th><th>编号</th><th>仓库</th><th>当前</th><th>安全/最大</th><th>状态</th></tr></thead>
                <tbody>
                  <tr v-for="(r, i) in (data?.warn?.warnList || [])" :key="i">
                    <td>{{ r.name }}</td>
                    <td class="tvm-sub">{{ r.code }}</td>
                    <td>{{ r.warehouse }}</td>
                    <td :style="{color: warnLevel(r) === 'be' ? 'var(--red)' : 'var(--orange)'}">{{ r.qty }}</td>
                    <td class="tvm-sub">{{ formatThreshold(r) }}</td>
                    <td><span class="tvm-badge" :class="warnLevel(r)">{{ warnTxt(r) }}</span></td>
                  </tr>
                </tbody>
              </table>
            </div>
          </Card>
        </div>
      </div>

      <!-- TAB 3: 库存任务 -->
      <div :class="['tvm-panel', { active: curTab === 3 }]">
        <div class="tvm-kpi-row">
          <Kpi label="待执行任务" :value="data?.task?.pendingTask" unit="个" k="k1" data-k="task.pendingTask"/>
          <Kpi label="执行中任务" :value="data?.task?.inProgressTask" unit="个" k="k3" data-k="task.inProgressTask"/>
          <Kpi label="已完成任务" :value="data?.task?.completedTask" unit="个" k="k2" data-k="task.completedTask"/>
          <Kpi label="待审申请" :value="data?.task?.requestPending" unit="个" k="k4" data-k="task.requestPending"/>
        </div>
        <div class="tvm-charts tvm-cr2">
          <Card title="任务状态分布"><div class="tvm-ci" ref="c30"></div></Card>
          <Card title="最新库存任务" :tag="(data?.task?.recentList?.length||0) + '项'" tagCls="ok">
            <div class="tvm-ci" style="overflow-y:auto">
              <table class="tvm-table">
                <thead><tr><th>任务号</th><th>类型</th><th>状态</th><th>批次</th><th>时间</th></tr></thead>
                <tbody>
                  <tr v-for="(r, i) in (data?.task?.recentList || [])" :key="i">
                    <td class="tvm-sub">{{ r.taskNo }}</td>
                    <td>{{ tr(INV_TYPE, r.invType) }}</td>
                    <td><span class="tvm-badge" :class="taskStatusCls(r.status)">{{ tr(INV_TASK_STATUS, r.status) }}</span></td>
                    <td class="tvm-sub">{{ r.batchNo || '-' }}</td>
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
import { echarts, donut, dualBar, rose } from './chartHelpers'
import { tr, INV_TYPE, INV_TASK_STATUS, MATERIAL_CATEGORY } from './dicts'
import './tv-shared.scss'

const tabs = ['📦 库存总览', '🔄 出入库流水', '⚠ 库存预警', '🛠 库存任务']

const c00 = ref(null), c01 = ref(null)
const c10 = ref(null), c11 = ref(null)
const c30 = ref(null)
let charts = []

function warnLevel(r) {
  const qty = Number(r.qty || 0), safe = Number(r.safe || 0), max = Number(r.maxStock || 0)
  if (max > 0 && qty > max) return 'bw'
  if (safe > 0 && qty < safe) {
    return (qty / safe) < 0.4 ? 'be' : 'bw'
  }
  return 'bi'
}
function warnTxt(r) {
  const qty = Number(r.qty || 0), safe = Number(r.safe || 0), max = Number(r.maxStock || 0)
  if (max > 0 && qty > max) return '超储'
  if (safe > 0 && qty < safe) {
    return (qty / safe) < 0.4 ? '严重不足' : '偏低'
  }
  return '正常'
}
function formatThreshold(r) {
  const safe = Number(r.safe || 0), max = Number(r.maxStock || 0)
  if (max > 0 && Number(r.qty) > max) return `最大 ${max}`
  if (safe > 0) return `安全 ${safe}`
  return '-'
}
function taskStatusCls(s) { return ({ pending: 'bi', in_progress: 'bw', completed: 'bo', cancelled: 'be' })[s] || 'bi' }
function formatDt(s) { return s ? String(s).replace('T',' ').substring(5,16) : '-' }

function onReady(data) {
  charts.forEach(c => c && c.dispose()); charts = []
  nextTick(() => render(data))
}
function render(data) {
  const ov = data?.overview || {}
  if (c00.value) { const c = echarts.init(c00.value); charts.push(c); c.setOption(donut(ov.warehousePie)) }
  if (c01.value) { const c = echarts.init(c01.value); charts.push(c); c.setOption(rose(ov.categoryPie, n => tr(MATERIAL_CATEGORY, n))) }

  const fl = data?.flow || {}
  if (c10.value) { const c = echarts.init(c10.value); charts.push(c)
    c.setOption(dualBar(fl.trend || [], 'date', 'in', 'out', '入库', '出库', '#00ff9d', '#ff8c28')) }
  if (c11.value) { const c = echarts.init(c11.value); charts.push(c); c.setOption(donut(fl.typePie, n => tr(INV_TYPE, n))) }

  const tk = data?.task || {}
  if (c30.value) { const c = echarts.init(c30.value); charts.push(c); c.setOption(donut(tk.statusPie, n => tr(INV_TASK_STATUS, n))) }

  window.addEventListener('resize', () => charts.forEach(c => c && c.resize()))
}
</script>
