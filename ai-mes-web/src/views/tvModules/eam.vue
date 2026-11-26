<template>
  <div class="tve-root">
    <canvas ref="particles" id="particles"></canvas>
    <div class="grid-bg"></div>
    <!-- 顶部装饰光带 -->
    <div class="hex-glow"></div>
    <div class="app">
      <!-- ===== HEADER ===== -->
      <div class="header">
        <div class="header-scan"></div>
        <div class="logo">
          <svg class="logo-svg" viewBox="0 0 42 42" fill="none">
            <rect width="42" height="42" rx="8" fill="rgba(255,140,40,0.08)" stroke="rgba(255,180,80,0.3)" stroke-width="1"/>
            <!-- 齿轮 -->
            <g transform="translate(21 21)">
              <circle r="6" fill="none" stroke="#ffae35" stroke-width="1.5"/>
              <circle r="2.5" fill="#ffae35"/>
              <g v-for="i in 8" :key="i" :transform="`rotate(${(i-1)*45})`">
                <rect x="-1.5" y="-10.5" width="3" height="3" fill="#ffae35"/>
              </g>
            </g>
          </svg>
          <div class="logo-text"><h1>设备智控中心</h1><p>EAM ASSET MANAGEMENT</p></div>
        </div>

        <div style="display:flex;flex-direction:column;align-items:center;gap:5px">
          <div class="tabs">
            <div v-for="(t, i) in tabsData" :key="i"
                 :class="['tab', { active: curTab === i }]"
                 @click="onTabClick(i)">{{ t }}</div>
          </div>
          <div style="width:100%;height:2px;background:rgba(255,180,80,0.1);border-radius:2px;overflow:hidden">
            <div :style="{ height:'100%', width: progress + '%', background:'linear-gradient(90deg,var(--orange),var(--gold))', borderRadius:'2px' }"></div>
          </div>
        </div>

        <div class="hdr-right">
          <div class="clock">{{ clockStr }}</div>
          <div class="date-row">
            <span>{{ dateStr }}</span>
            <span class="sdot"></span>
            <span style="color:var(--green)">设备运行正常</span>
          </div>
        </div>
      </div>

      <!-- ===== CONTENT ===== -->
      <div class="content">
        <!-- TAB 0: 设备总览 -->
        <div :class="['tab-panel', { active: curTab === 0 }]">
          <div class="kpi-row">
            <div class="kpi-card c1">
              <div class="kpi-label"><span class="ldot" style="background:var(--cyan)"></span>设备总数</div>
              <div class="kpi-value"><span class="num" data-k="equipment.totalCount" data-t="0">0</span><span class="unit">台</span></div>
              <div class="kpi-trend up">↑ 资产基数稳定</div>
            </div>
            <div class="kpi-card c2">
              <div class="kpi-label"><span class="ldot" style="background:var(--green)"></span>正常运行</div>
              <div class="kpi-value"><span class="num" data-k="equipment.normalCount" data-t="0">0</span><span class="unit">台</span></div>
              <div class="kpi-trend up">↑ 状态健康</div>
            </div>
            <div class="kpi-card c3">
              <div class="kpi-label"><span class="ldot" style="background:var(--orange)"></span>维修中</div>
              <div class="kpi-value"><span class="num" data-k="equipment.maintenanceCount" data-t="0">0</span><span class="unit">台</span></div>
              <div class="kpi-trend eq">→ 维修中</div>
            </div>
            <div class="kpi-card c5">
              <div class="kpi-label"><span class="ldot" style="background:var(--red)"></span>已停用</div>
              <div class="kpi-value"><span class="num" data-k="equipment.retiredCount" data-t="0">0</span><span class="unit">台</span></div>
              <div class="kpi-trend dn">↓ 已退役设备</div>
            </div>
          </div>
          <div class="charts-row cr3">
            <div class="chart-card">
              <div class="ct"><div class="ct-l">设备类型分布</div><span class="ct-tag ok">实时</span></div>
              <div class="ci" ref="cEqCategory"></div>
            </div>
            <div class="chart-card">
              <div class="ct"><div class="ct-l">设备状态构成</div></div>
              <div class="ci" ref="cEqStatus"></div>
            </div>
            <div class="chart-card">
              <div class="ct"><div class="ct-l">即将到期保养 TOP 10</div><span class="ct-tag warn">{{ upcomingEqRows.length }}台</span></div>
              <div class="ci" style="overflow-y:auto">
                <table class="dtable">
                  <thead><tr><th>设备编号</th><th>设备名称</th><th>类型</th><th>位置</th><th>到期</th></tr></thead>
                  <tbody>
                    <tr v-for="(r, idx) in upcomingEqRows" :key="idx">
                      <td>{{ r.equipmentNo }}</td>
                      <td>{{ r.equipmentName }}</td>
                      <td>{{ r.category }}</td>
                      <td>{{ r.location }}</td>
                      <td><span class="badge" :class="r.cls">{{ r.txt }}</span></td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </div>

        <!-- TAB 1: 运行告警 -->
        <div :class="['tab-panel', { active: curTab === 1 }]">
          <div class="kpi-row">
            <div class="kpi-card c2">
              <div class="kpi-label"><span class="ldot" style="background:var(--green)"></span>运行中</div>
              <div class="kpi-value"><span class="num" data-k="alarm.runningCount" data-t="0">0</span><span class="unit">台</span></div>
              <div class="kpi-trend up">↑ 状态正常</div>
            </div>
            <div class="kpi-card c5">
              <div class="kpi-label"><span class="ldot" style="background:var(--red)"></span>当前告警</div>
              <div class="kpi-value"><span class="num" data-k="alarm.alarmCount" data-t="0">0</span><span class="unit">条</span></div>
              <div :class="['kpi-trend', alarmCount > 0 ? 'dn' : 'eq']">
                {{ alarmCount > 0 ? `⚠ 共 ${alarmCount} 条待处理` : '→ 无告警' }}
              </div>
            </div>
            <div class="kpi-card c4">
              <div class="kpi-label"><span class="ldot" style="background:var(--purple)"></span>空闲设备</div>
              <div class="kpi-value"><span class="num" data-k="alarm.idleCount" data-t="0">0</span><span class="unit">台</span></div>
              <div class="kpi-trend eq">→ 待激活</div>
            </div>
            <div class="kpi-card c3">
              <div class="kpi-label"><span class="ldot" style="background:var(--orange)"></span>近 7 日告警</div>
              <div class="kpi-value"><span class="num" data-k="alarm.totalAlarm7d" data-t="0">0</span><span class="unit">次</span></div>
              <div class="kpi-trend dn">↓ 累计触发</div>
            </div>
          </div>
          <div class="charts-row cr3">
            <div class="chart-card">
              <div class="ct"><div class="ct-l">近 7 日告警趋势</div><span class="ct-tag bad">告警监控</span></div>
              <div class="ci" ref="cAlarmTrend"></div>
            </div>
            <div class="chart-card">
              <div class="ct"><div class="ct-l">告警类型分布</div></div>
              <div class="ci" ref="cAlarmPie"></div>
            </div>
            <div class="chart-card">
              <div class="ct"><div class="ct-l">实时告警明细</div><span class="ct-tag warn">{{ alarmRows.length }}条</span></div>
              <div class="ci" style="overflow-y:auto">
                <table class="dtable">
                  <thead><tr><th>设备</th><th>位置</th><th>告警等级</th><th>告警内容</th><th>时间</th></tr></thead>
                  <tbody>
                    <tr v-for="(r, idx) in alarmRows" :key="idx">
                      <td>{{ r.equipmentName }}<br/><span class="sub">{{ r.equipmentNo }}</span></td>
                      <td>{{ r.location }}</td>
                      <td><span class="badge" :class="alarmCls(r.alarmStatus)">{{ r.alarmStatus }}</span></td>
                      <td>{{ r.alarmMessage }}</td>
                      <td class="sub">{{ formatDt(r.occurredAt) }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </div>

        <!-- TAB 2: 保养管理 -->
        <div :class="['tab-panel', { active: curTab === 2 }]">
          <div class="kpi-row">
            <div class="kpi-card c1">
              <div class="kpi-label"><span class="ldot" style="background:var(--cyan)"></span>待执行任务</div>
              <div class="kpi-value"><span class="num" data-k="maintenance.pendingCount" data-t="0">0</span><span class="unit">个</span></div>
              <div class="kpi-trend eq">→ 等待派工</div>
            </div>
            <div class="kpi-card c3">
              <div class="kpi-label"><span class="ldot" style="background:var(--orange)"></span>执行中</div>
              <div class="kpi-value"><span class="num" data-k="maintenance.inProgressCount" data-t="0">0</span><span class="unit">个</span></div>
              <div class="kpi-trend up">↑ 进行中</div>
            </div>
            <div class="kpi-card c2">
              <div class="kpi-label"><span class="ldot" style="background:var(--green)"></span>累计完成</div>
              <div class="kpi-value"><span class="num" data-k="maintenance.completedCount" data-t="0">0</span><span class="unit">个</span></div>
              <div class="kpi-trend up">↑ 完成情况良好</div>
            </div>
            <div class="kpi-card c5">
              <div class="kpi-label"><span class="ldot" style="background:var(--red)"></span>逾期任务</div>
              <div class="kpi-value"><span class="num" data-k="maintenance.overdueCount" data-t="0">0</span><span class="unit">个</span></div>
              <div :class="['kpi-trend', maintOverdue > 0 ? 'dn' : 'eq']">
                {{ maintOverdue > 0 ? `⚠ ${maintOverdue} 个已逾期` : '→ 无逾期' }}
              </div>
            </div>
          </div>
          <div class="charts-row cr3">
            <div class="chart-card">
              <div class="ct"><div class="ct-l">近 12 月保养完成趋势</div><span class="ct-tag ok">良好</span></div>
              <div class="ci" ref="cMaintTrend"></div>
            </div>
            <div class="chart-card">
              <div class="ct"><div class="ct-l">任务状态分布</div></div>
              <div class="ci" ref="cMaintPie"></div>
            </div>
            <div class="chart-card">
              <div class="ct"><div class="ct-l">即将到期保养任务</div><span class="ct-tag warn">{{ maintRows.length }}项</span></div>
              <div class="ci" style="overflow-y:auto">
                <table class="dtable">
                  <thead><tr><th>任务编号</th><th>设备</th><th>执行人</th><th>计划完成</th><th>状态</th></tr></thead>
                  <tbody>
                    <tr v-for="(r, idx) in maintRows" :key="idx">
                      <td class="sub">{{ r.taskCode }}</td>
                      <td>{{ r.equipmentName }}</td>
                      <td>{{ r.executorName }}</td>
                      <td class="sub">{{ formatDt(r.plannedEnd) }}</td>
                      <td><span class="badge" :class="taskCls(r.taskStatus)">{{ taskTxt(r.taskStatus) }}</span></td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </div>

        <!-- TAB 3: 点检管理 -->
        <div :class="['tab-panel', { active: curTab === 3 }]">
          <div class="kpi-row">
            <div class="kpi-card c1">
              <div class="kpi-label"><span class="ldot" style="background:var(--cyan)"></span>待执行</div>
              <div class="kpi-value"><span class="num" data-k="inspection.pendingCount" data-t="0">0</span><span class="unit">个</span></div>
              <div class="kpi-trend eq">→ 等待执行</div>
            </div>
            <div class="kpi-card c3">
              <div class="kpi-label"><span class="ldot" style="background:var(--orange)"></span>执行中</div>
              <div class="kpi-value"><span class="num" data-k="inspection.inProgressCount" data-t="0">0</span><span class="unit">个</span></div>
              <div class="kpi-trend up">↑ 巡检进行中</div>
            </div>
            <div class="kpi-card c2">
              <div class="kpi-label"><span class="ldot" style="background:var(--green)"></span>已完成</div>
              <div class="kpi-value"><span class="num" data-k="inspection.completedCount" data-t="0">0</span><span class="unit">个</span></div>
              <div class="kpi-trend up">↑ 完成情况良好</div>
            </div>
            <div class="kpi-card c5">
              <div class="kpi-label"><span class="ldot" style="background:var(--red)"></span>逾期</div>
              <div class="kpi-value"><span class="num" data-k="inspection.overdueCount" data-t="0">0</span><span class="unit">个</span></div>
              <div :class="['kpi-trend', inspOverdue > 0 ? 'dn' : 'eq']">
                {{ inspOverdue > 0 ? `⚠ ${inspOverdue} 个已逾期` : '→ 无逾期' }}
              </div>
            </div>
          </div>
          <div class="charts-row cr3">
            <div class="chart-card">
              <div class="ct"><div class="ct-l">近 14 日点检完成趋势</div><span class="ct-tag ok">实时更新</span></div>
              <div class="ci" ref="cInspTrend"></div>
            </div>
            <div class="chart-card">
              <div class="ct"><div class="ct-l">点检任务状态</div></div>
              <div class="ci" ref="cInspPie"></div>
            </div>
            <div class="chart-card">
              <div class="ct"><div class="ct-l">即将到期点检任务</div><span class="ct-tag warn">{{ inspRows.length }}项</span></div>
              <div class="ci" style="overflow-y:auto">
                <table class="dtable">
                  <thead><tr><th>任务编号</th><th>设备</th><th>点检人</th><th>计划完成</th><th>状态</th></tr></thead>
                  <tbody>
                    <tr v-for="(r, idx) in inspRows" :key="idx">
                      <td class="sub">{{ r.taskCode }}</td>
                      <td>{{ r.equipmentName }}</td>
                      <td>{{ r.inspectorName }}</td>
                      <td class="sub">{{ formatDt(r.plannedEnd) }}</td>
                      <td><span class="badge" :class="taskCls(r.taskStatus)">{{ taskTxt(r.taskStatus) }}</span></td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </div>

        <!-- TAB 4: 维修工单 -->
        <div :class="['tab-panel', { active: curTab === 4 }]">
          <div class="kpi-row">
            <div class="kpi-card c1">
              <div class="kpi-label"><span class="ldot" style="background:var(--cyan)"></span>待受理报修</div>
              <div class="kpi-value"><span class="num" data-k="repair.pendingRequest" data-t="0">0</span><span class="unit">单</span></div>
              <div class="kpi-trend eq">→ 等待受理</div>
            </div>
            <div class="kpi-card c3">
              <div class="kpi-label"><span class="ldot" style="background:var(--orange)"></span>处理中工单</div>
              <div class="kpi-value"><span class="num" data-k="repair.inProgressOrder" data-t="0">0</span><span class="unit">单</span></div>
              <div class="kpi-trend up">↑ 维修中</div>
            </div>
            <div class="kpi-card c2">
              <div class="kpi-label"><span class="ldot" style="background:var(--green)"></span>已完成工单</div>
              <div class="kpi-value"><span class="num" data-k="repair.completedOrder" data-t="0">0</span><span class="unit">单</span></div>
              <div class="kpi-trend up">↑ 累计完成</div>
            </div>
            <div class="kpi-card c5">
              <div class="kpi-label"><span class="ldot" style="background:var(--red)"></span>紧急/高优工单</div>
              <div class="kpi-value"><span class="num" data-k="repair.urgentOrder" data-t="0">0</span><span class="unit">单</span></div>
              <div :class="['kpi-trend', urgentOrder > 0 ? 'dn' : 'eq']">
                {{ urgentOrder > 0 ? `⚠ ${urgentOrder} 单需优先处理` : '→ 无紧急工单' }}
              </div>
            </div>
          </div>
          <div class="charts-row cr3">
            <div class="chart-card">
              <div class="ct"><div class="ct-l">近 12 月维修工单趋势</div><span class="ct-tag">单</span></div>
              <div class="ci" ref="cRepTrend"></div>
            </div>
            <div class="chart-card">
              <div class="ct"><div class="ct-l">工单优先级分布</div></div>
              <div class="ci" ref="cRepPie"></div>
            </div>
            <div class="chart-card">
              <div class="ct"><div class="ct-l">活跃维修工单</div><span class="ct-tag warn">{{ repairRows.length }}单</span></div>
              <div class="ci" style="overflow-y:auto">
                <table class="dtable">
                  <thead><tr><th>工单号</th><th>设备</th><th>责任人</th><th>优先级</th><th>状态</th></tr></thead>
                  <tbody>
                    <tr v-for="(r, idx) in repairRows" :key="idx">
                      <td class="sub">{{ r.workOrderNo }}</td>
                      <td>{{ r.equipmentName }}</td>
                      <td>{{ r.assigneeName }}</td>
                      <td><span class="badge" :class="priorityCls(r.priority)">{{ priorityTxt(r.priority) }}</span></td>
                      <td><span class="badge" :class="repairStatusCls(r.status)">{{ repairStatusTxt(r.status) }}</span></td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- FOOTER TICKER -->
      <div class="footer">
        <div class="tk-label">▶ 设备实时动态</div>
        <div class="tk-wrap">
          <div class="tk-inner">
            <span v-for="(m, i) in tickerAll" :key="i" class="tk-item">
              <span class="tk-dot" :style="{ background: m.c }"></span>{{ m.t }}
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts'
import { getModuleTv } from '@/api/tv'

// ========== 数据 ==========
const tabsData = ['🏭 设备总览', '🔔 运行告警', '🛠 保养管理', '📋 点检管理', '🔧 维修工单']
const curTab = ref(0)
const progress = ref(0)
const clockStr = ref('00:00:00')
const dateStr = ref('')
const DAYS = ['周日','周一','周二','周三','周四','周五','周六']

const apiData = ref(null)

// KPI 提示用
const alarmCount = computed(() => Number(apiData.value?.alarm?.alarmCount || 0))
const maintOverdue = computed(() => Number(apiData.value?.maintenance?.overdueCount || 0))
const inspOverdue = computed(() => Number(apiData.value?.inspection?.overdueCount || 0))
const urgentOrder = computed(() => Number(apiData.value?.repair?.urgentOrder || 0))

// 表格行
const upcomingEqRows = computed(() => (apiData.value?.equipment?.upcomingList || []).map(r => {
  const days = Number(r.daysLeft || 0)
  let cls, txt
  if (days < 0) { cls = 'be'; txt = `逾期 ${-days} 天` }
  else if (days <= 3) { cls = 'be'; txt = `${days} 天后` }
  else if (days <= 7) { cls = 'bw'; txt = `${days} 天后` }
  else { cls = 'bi'; txt = `${days} 天后` }
  return { ...r, cls, txt }
}))

const alarmRows = computed(() => apiData.value?.alarm?.alarmList || [])
const maintRows = computed(() => apiData.value?.maintenance?.upcomingList || [])
const inspRows = computed(() => apiData.value?.inspection?.upcomingList || [])
const repairRows = computed(() => apiData.value?.repair?.orderList || [])

// 跑马灯
const tickerAll = computed(() => {
  const msgs = apiData.value?.ticker || []
  if (!msgs.length) return [{ c: 'var(--green)', t: 'EAM 系统运行正常,暂无重要消息' }]
  return [...msgs, ...msgs]
})

// 任务状态码 → 中文 / 颜色
const TASK_TXT = { 0: '待执行', 1: '执行中', 2: '已完成', 3: '已取消', 4: '已暂停' }
const TASK_CLS = { 0: 'bi', 1: 'bw', 2: 'bo', 3: 'be', 4: 'bw' }
function taskTxt(s) { return TASK_TXT[s] || '-' }
function taskCls(s) { return TASK_CLS[s] || 'bi' }

// 维修工单状态
const REPAIR_TXT = { pending: '待处理', in_progress: '维修中', paused: '已暂停', completed: '已完成', cancelled: '已取消' }
const REPAIR_CLS = { pending: 'bi', in_progress: 'bw', paused: 'bw', completed: 'bo', cancelled: 'be' }
function repairStatusTxt(s) { return REPAIR_TXT[s] || s || '-' }
function repairStatusCls(s) { return REPAIR_CLS[s] || 'bi' }

// 优先级
const PRIO_TXT = { urgent: '紧急', high: '高', normal: '普通', low: '低' }
const PRIO_CLS = { urgent: 'be', high: 'bw', normal: 'bi', low: 'bo' }
function priorityTxt(p) { return PRIO_TXT[p] || p || '普通' }
function priorityCls(p) { return PRIO_CLS[p] || 'bi' }

// 告警等级 cls
function alarmCls(s) {
  const u = String(s || '').toUpperCase()
  if (u.includes('CRITICAL') || u.includes('FATAL') || u.includes('严重')) return 'be'
  if (u.includes('HIGH') || u.includes('ERROR') || u.includes('错误')) return 'be'
  if (u.includes('WARN') || u.includes('警告')) return 'bw'
  return 'bi'
}

// 时间格式化
function formatDt(s) {
  if (!s) return '-'
  const t = String(s).replace('T', ' ')
  return t.length > 16 ? t.substring(5, 16) : t
}

// ========== refs ==========
const particles = ref(null)
const cEqCategory = ref(null)
const cEqStatus = ref(null)
const cAlarmTrend = ref(null)
const cAlarmPie = ref(null)
const cMaintTrend = ref(null)
const cMaintPie = ref(null)
const cInspTrend = ref(null)
const cInspPie = ref(null)
const cRepTrend = ref(null)
const cRepPie = ref(null)

// ========== ECHARTS helpers ==========
const C = { c: '#00d4ff', g: '#00ff9d', o: '#ff8c28', p: '#a855f7', r: '#ff4466', y: '#ffd700' }
function base() {
  return {
    backgroundColor: 'transparent',
    textStyle: { color: '#a8b8c8' },
    tooltip: { backgroundColor: 'rgba(20,12,4,.92)', borderColor: 'rgba(255,180,80,.25)', textStyle: { color: '#fff5e0' }, extraCssText: 'border-radius:6px;padding:10px 14px' },
    grid: { top: 32, right: 16, bottom: 28, left: 46, containLabel: true }
  }
}
function xAxis(data) {
  return { type: 'category', data, axisLine: { lineStyle: { color: 'rgba(255,180,80,.15)' } }, axisTick: { show: false }, axisLabel: { color: '#a8b8c8', fontSize: 11 } }
}
function yAxis(fmt) {
  return { type: 'value', splitLine: { lineStyle: { color: 'rgba(255,180,80,.08)', type: 'dashed' } }, axisLabel: { color: '#a8b8c8', fontSize: 11, formatter: fmt || '{value}' }, axisLine: { show: false }, axisTick: { show: false } }
}
function pieItemStyle() { return { borderRadius: 4, borderColor: '#100805', borderWidth: 2 } }
function pieLabel() { return { color: '#a8b8c8', fontSize: 11, overflow: 'none', width: 200, lineHeight: 14 } }
function pieLabelLine() { return { length: 12, length2: 14 } }
function legendStyle() { return { bottom: 4, textStyle: { color: '#a8b8c8', fontSize: 11 }, itemWidth: 10, itemHeight: 10, itemGap: 10 } }

const COLORS = [C.o, C.y, C.c, C.g, C.p, C.r]
function pickColor(i) { return COLORS[i % COLORS.length] }
function gradBar(c) {
  return { type: 'linear', x: 0, y: 0, x2: 1, y2: 0,
    colorStops: [{ offset: 0, color: 'rgba(255,180,80,.18)' }, { offset: 1, color: c }] }
}

const chartInsts = []
const inited = {}

// ========== TABS ==========
const AUTO_INTERVAL = 8000
let autoTimer = null
let progressRaf = null
let progressStart = 0
let resumeTimer = null

function getByPath(obj, path) {
  if (!obj || !path) return undefined
  return path.split('.').reduce((a, k) => (a == null ? undefined : a[k]), obj)
}

function runCounters(panelEl) {
  if (!panelEl) return
  panelEl.querySelectorAll('.num').forEach(el => {
    const key = el.dataset.k
    const apiVal = key ? getByPath(apiData.value, key) : null
    const target = apiVal != null ? Number(apiVal) : (+el.dataset.t || 0)
    el.dataset.t = target
    const step = Math.max(target / 55, 1)
    let cur = 0
    const tm = setInterval(() => {
      cur = Math.min(cur + step, target)
      el.textContent = Math.round(cur).toLocaleString()
      if (cur >= target) {
        el.textContent = Math.round(target).toLocaleString()
        clearInterval(tm)
      }
    }, 18)
  })
}

function updateProgress() {
  progressStart = performance.now()
  if (progressRaf) cancelAnimationFrame(progressRaf)
  function step(now) {
    const pct = Math.min((now - progressStart) / AUTO_INTERVAL * 100, 100)
    progress.value = pct
    if (pct < 100) progressRaf = requestAnimationFrame(step)
  }
  progressRaf = requestAnimationFrame(step)
}

function switchTab(i) {
  curTab.value = i
  nextTick(() => {
    const panel = document.querySelectorAll('.tab-panel')[i]
    runCounters(panel)
    initTab(i)
    chartInsts.forEach(c => c && c.resize())
    updateProgress()
  })
}

function startAuto() {
  stopAuto()
  autoTimer = setInterval(() => switchTab((curTab.value + 1) % tabsData.length), AUTO_INTERVAL)
}
function stopAuto() { if (autoTimer) { clearInterval(autoTimer); autoTimer = null } }

function onTabClick(i) {
  stopAuto()
  switchTab(i)
  if (resumeTimer) clearTimeout(resumeTimer)
  resumeTimer = setTimeout(startAuto, 15000)
}

// ========== INIT TAB CHARTS ==========
function initTab(i) {
  if (inited[i]) return
  inited[i] = true
  const fns = [tab0, tab1, tab2, tab3, tab4]
  fns[i] && fns[i]()
}

// 设备状态英文 → 中文
const EQ_STATUS = { NORMAL: '正常运行', MAINTENANCE: '维修中', RETIRED: '已停用', IDLE: '空闲', RUNNING: '运行中', OFFLINE: '离线' }
const REPAIR_PIE_TXT = { urgent: '紧急', high: '高', normal: '普通', low: '低' }

// ====== TAB 0: 设备总览 ======
function tab0() {
  const eq = apiData.value?.equipment || {}
  // 设备类型(横向条)
  const cat = (eq.categoryBar || []).slice().reverse()
  const ch0 = echarts.init(cEqCategory.value); chartInsts.push(ch0)
  ch0.setOption({ ...base(),
    grid: { top: 12, right: 60, bottom: 12, left: 8, containLabel: true },
    xAxis: { type: 'value', splitLine: { lineStyle: { color: 'rgba(255,180,80,.08)', type: 'dashed' } }, axisLabel: { color: '#a8b8c8', fontSize: 10, formatter: '{value}台' } },
    yAxis: { type: 'category', data: cat.map(r => r.name || '-'),
      axisLabel: { color: '#a8b8c8', fontSize: 11 }, axisLine: { lineStyle: { color: 'rgba(255,180,80,.15)' } }, axisTick: { show: false } },
    series: [{ type: 'bar',
      data: cat.map((r, i) => ({ value: Number(r.value || 0), itemStyle: { color: gradBar(pickColor(i)) } })),
      barMaxWidth: 14, itemStyle: { borderRadius: [0,3,3,0] },
      label: { show: true, position: 'right', color: '#a8b8c8', fontSize: 11, formatter: '{c}台' },
      showBackground: true, backgroundStyle: { color: 'rgba(255,180,80,.04)', borderRadius: [0,3,3,0] }
    }]
  })

  // 状态饼
  const pieData = (eq.statusPie || []).map((r, i) => ({
    name: EQ_STATUS[r.name] || r.name || '未知', value: Number(r.value || 0),
    itemStyle: { color: pickColor(i) }
  }))
  const ch1 = echarts.init(cEqStatus.value); chartInsts.push(ch1)
  ch1.setOption({ ...base(), legend: legendStyle(),
    series: [{ type: 'pie', radius: ['38%','62%'], center: ['50%','46%'], data: pieData,
      label: { color: '#a8b8c8', fontSize: 11, formatter: '{b}\n{d}%' },
      labelLine: pieLabelLine(), itemStyle: pieItemStyle(), emphasis: { scaleSize: 6 } }]
  })
}

// ====== TAB 1: 运行告警 ======
function tab1() {
  const al = apiData.value?.alarm || {}
  const trend = al.trend || []
  const ch0 = echarts.init(cAlarmTrend.value); chartInsts.push(ch0)
  ch0.setOption({ ...base(),
    xAxis: xAxis(trend.map(r => r.date)), yAxis: yAxis('{value}次'),
    series: [{ name: '告警次数', type: 'line', smooth: true, data: trend.map(r => Number(r.count || 0)),
      lineStyle: { color: C.r, width: 2.5 }, itemStyle: { color: C.r }, symbol: 'circle', symbolSize: 6,
      areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(255,68,102,.35)' }, { offset: 1, color: 'rgba(255,68,102,0)' }] } } }]
  })

  const pieData = (al.alarmPie || []).map((r, i) => ({
    name: r.name || '-', value: Number(r.value || 0), itemStyle: { color: pickColor(i) }
  }))
  const ch1 = echarts.init(cAlarmPie.value); chartInsts.push(ch1)
  ch1.setOption({ ...base(), legend: legendStyle(),
    series: [{ type: 'pie', radius: ['35%','58%'], center: ['50%','46%'], data: pieData,
      label: pieLabel(), labelLine: pieLabelLine(), itemStyle: pieItemStyle() }]
  })
}

// ====== TAB 2: 保养管理 ======
function tab2() {
  const mt = apiData.value?.maintenance || {}
  const trend = mt.trend || []
  const ch0 = echarts.init(cMaintTrend.value); chartInsts.push(ch0)
  ch0.setOption({ ...base(),
    legend: { top: 4, right: 8, textStyle: { color: '#a8b8c8', fontSize: 11 }, data: ['计划','完成'] },
    xAxis: xAxis(trend.map(r => r.month)), yAxis: yAxis('{value}'),
    series: [
      { name: '计划', type: 'bar', barMaxWidth: 12, data: trend.map(r => Number(r.planned || 0)),
        itemStyle: { color: 'rgba(255,140,40,.45)', borderRadius: [3,3,0,0] } },
      { name: '完成', type: 'line', smooth: true, data: trend.map(r => Number(r.completed || 0)),
        lineStyle: { color: C.g, width: 2.5 }, itemStyle: { color: C.g }, symbol: 'circle', symbolSize: 6,
        areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(0,255,157,.25)' }, { offset: 1, color: 'rgba(0,255,157,0)' }] } } }
    ]
  })

  const pieData = (mt.statusPie || []).map((r, i) => ({
    name: TASK_TXT[r.name] || r.name || '其他', value: Number(r.value || 0),
    itemStyle: { color: pickColor(i) }
  }))
  const ch1 = echarts.init(cMaintPie.value); chartInsts.push(ch1)
  ch1.setOption({ ...base(), legend: legendStyle(),
    series: [{ type: 'pie', radius: ['38%','60%'], center: ['50%','46%'], data: pieData,
      label: { color: '#a8b8c8', fontSize: 11, formatter: '{b}\n{d}%' },
      labelLine: pieLabelLine(), itemStyle: pieItemStyle(), emphasis: { scaleSize: 4 } }]
  })
}

// ====== TAB 3: 点检管理 ======
function tab3() {
  const ip = apiData.value?.inspection || {}
  const trend = ip.trend || []
  const ch0 = echarts.init(cInspTrend.value); chartInsts.push(ch0)
  ch0.setOption({ ...base(),
    legend: { top: 4, right: 8, textStyle: { color: '#a8b8c8', fontSize: 11 }, data: ['计划','完成'] },
    xAxis: xAxis(trend.map(r => r.date)), yAxis: yAxis('{value}'),
    series: [
      { name: '计划', type: 'line', data: trend.map(r => Number(r.planned || 0)),
        lineStyle: { color: 'rgba(0,212,255,.55)', width: 1.5, type: 'dashed' }, itemStyle: { color: C.c }, symbol: 'none' },
      { name: '完成', type: 'line', smooth: true, data: trend.map(r => Number(r.completed || 0)),
        lineStyle: { color: C.g, width: 2 }, itemStyle: { color: C.g }, symbol: 'circle', symbolSize: 5,
        areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(0,255,157,.2)' }, { offset: 1, color: 'rgba(0,255,157,0)' }] } } }
    ]
  })

  const pieData = (ip.statusPie || []).map((r, i) => ({
    name: TASK_TXT[r.name] || r.name || '其他', value: Number(r.value || 0),
    itemStyle: { color: pickColor(i) }
  }))
  const ch1 = echarts.init(cInspPie.value); chartInsts.push(ch1)
  ch1.setOption({ ...base(), legend: legendStyle(),
    series: [{ type: 'pie', radius: ['38%','62%'], center: ['50%','46%'], data: pieData,
      label: { color: '#a8b8c8', fontSize: 11, formatter: '{b}\n{d}%' },
      labelLine: pieLabelLine(), itemStyle: pieItemStyle(), emphasis: { scaleSize: 4 } }]
  })
}

// ====== TAB 4: 维修工单 ======
function tab4() {
  const rp = apiData.value?.repair || {}
  const trend = rp.trend || []
  const ch0 = echarts.init(cRepTrend.value); chartInsts.push(ch0)
  ch0.setOption({ ...base(),
    legend: { top: 4, right: 8, textStyle: { color: '#a8b8c8', fontSize: 11 }, data: ['新建','完成'] },
    xAxis: xAxis(trend.map(r => r.month)), yAxis: yAxis('{value}单'),
    series: [
      { name: '新建', type: 'bar', barMaxWidth: 12, data: trend.map(r => Number(r.created || 0)),
        itemStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: C.o }, { offset: 1, color: 'rgba(255,140,40,.08)' }] }, borderRadius: [3,3,0,0] } },
      { name: '完成', type: 'bar', barMaxWidth: 12, data: trend.map(r => Number(r.completed || 0)),
        itemStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: C.g }, { offset: 1, color: 'rgba(0,255,157,.08)' }] }, borderRadius: [3,3,0,0] } }
    ]
  })

  const pieData = (rp.priorityPie || []).map((r, i) => ({
    name: REPAIR_PIE_TXT[r.name] || r.name || '其他', value: Number(r.value || 0),
    itemStyle: { color: pickColor(i) }
  }))
  const ch1 = echarts.init(cRepPie.value); chartInsts.push(ch1)
  ch1.setOption({ ...base(), legend: legendStyle(),
    series: [{ type: 'pie', radius: ['38%','60%'], center: ['50%','46%'], data: pieData,
      label: { color: '#a8b8c8', fontSize: 11, formatter: '{b}\n{d}%' },
      labelLine: pieLabelLine(), itemStyle: pieItemStyle(), emphasis: { scaleSize: 6 } }]
  })
}

// ========== PARTICLES ==========
let particleRaf = null
function startParticles() {
  const canvas = particles.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  const resize = () => { canvas.width = innerWidth; canvas.height = innerHeight }
  resize()
  window.addEventListener('resize', resize)
  const pts = []
  for (let i = 0; i < 70; i++) {
    pts.push({ x: Math.random() * innerWidth, y: Math.random() * innerHeight, vx: (Math.random() - .5) * .25, vy: (Math.random() - .5) * .25, r: Math.random() * 1.2 + .4, a: Math.random() * .35 + .08 })
  }
  function loop() {
    ctx.clearRect(0, 0, canvas.width, canvas.height)
    pts.forEach(p => {
      p.x += p.vx; p.y += p.vy
      if (p.x < 0 || p.x > canvas.width) p.vx *= -1
      if (p.y < 0 || p.y > canvas.height) p.vy *= -1
      ctx.beginPath(); ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2)
      ctx.fillStyle = `rgba(255,180,80,${p.a})`; ctx.fill()
    })
    for (let i = 0; i < pts.length; i++) for (let j = i + 1; j < pts.length; j++) {
      const dx = pts[i].x - pts[j].x, dy = pts[i].y - pts[j].y, d = Math.sqrt(dx * dx + dy * dy)
      if (d < 90) { ctx.beginPath(); ctx.strokeStyle = `rgba(255,180,80,${.06 * (1 - d / 90)})`; ctx.lineWidth = .5; ctx.moveTo(pts[i].x, pts[i].y); ctx.lineTo(pts[j].x, pts[j].y); ctx.stroke() }
    }
    particleRaf = requestAnimationFrame(loop)
  }
  loop()
}

// ========== CLOCK ==========
let clockTimer = null
function tickClock() {
  const n = new Date()
  clockStr.value = [n.getHours(), n.getMinutes(), n.getSeconds()].map(v => String(v).padStart(2, '0')).join(':')
  dateStr.value = `${n.getFullYear()}-${String(n.getMonth() + 1).padStart(2, '0')}-${String(n.getDate()).padStart(2, '0')} ${DAYS[n.getDay()]}`
}

// ========== LIFECYCLE ==========
let resizeHandler = null
async function loadApiData() {
  try {
    const res = await getModuleTv('eam')
    apiData.value = res.data || null
  } catch (e) {
    apiData.value = null
  }
}

let refreshTimer = null

onMounted(async () => {
  startParticles()
  tickClock()
  clockTimer = setInterval(tickClock, 1000)
  await loadApiData()
  switchTab(0)
  startAuto()
  refreshTimer = setInterval(async () => {
    await loadApiData()
    Object.keys(inited).forEach(k => delete inited[k])
    chartInsts.forEach(c => c && c.dispose())
    chartInsts.length = 0
    initTab(curTab.value)
    nextTick(() => {
      const panel = document.querySelectorAll('.tab-panel')[curTab.value]
      runCounters(panel)
    })
  }, 60000)
  resizeHandler = () => chartInsts.forEach(c => c && c.resize())
  window.addEventListener('resize', resizeHandler)
})
onBeforeUnmount(() => {
  stopAuto()
  if (resumeTimer) clearTimeout(resumeTimer)
  if (clockTimer) clearInterval(clockTimer)
  if (refreshTimer) clearInterval(refreshTimer)
  if (progressRaf) cancelAnimationFrame(progressRaf)
  if (particleRaf) cancelAnimationFrame(particleRaf)
  if (resizeHandler) window.removeEventListener('resize', resizeHandler)
  chartInsts.forEach(c => c && c.dispose())
})
</script>

<style scoped>
.tve-root {
  --bg:#100805;--bg2:#1a0f06;--card:rgba(255,180,80,0.04);--border:rgba(255,180,80,0.2);
  --cyan:#00d4ff;--green:#00ff9d;--orange:#ff8c28;--purple:#a855f7;--red:#ff4466;--gold:#ffd700;
  --text:#fff5e0;--text2:#a8b8c8;
  width:100%;height:100vh;background:radial-gradient(ellipse at top,#1f1108 0%,#100805 60%,#0a0503 100%);
  color:var(--text);
  font-family:'Microsoft YaHei','PingFang SC',sans-serif;overflow:hidden;position:fixed;top:0;left:0;
}
#particles{position:fixed;top:0;left:0;width:100%;height:100%;z-index:0;pointer-events:none}
.grid-bg{position:fixed;top:0;left:0;width:100%;height:100%;z-index:0;pointer-events:none;
  background-image:linear-gradient(rgba(255,180,80,0.03) 1px,transparent 1px),
    linear-gradient(90deg,rgba(255,180,80,0.03) 1px,transparent 1px);
  background-size:60px 60px}
.hex-glow{position:fixed;top:-200px;left:50%;transform:translateX(-50%);
  width:1200px;height:400px;z-index:0;pointer-events:none;
  background:radial-gradient(ellipse,rgba(255,140,40,0.18) 0%,transparent 70%);
  filter:blur(40px)}
.app{position:relative;z-index:1;width:100%;height:100vh;display:flex;flex-direction:column}

.header{height:68px;flex-shrink:0;
  background:linear-gradient(180deg,rgba(120,50,10,0.4) 0%,rgba(0,0,0,0) 100%);
  border-bottom:1px solid var(--border);
  display:flex;align-items:center;justify-content:space-between;padding:0 20px;
  position:relative;overflow:hidden}
.header-scan{position:absolute;bottom:0;left:-100%;width:100%;height:1px;
  background:linear-gradient(90deg,transparent,var(--orange),transparent);
  animation:scan 4s linear infinite}
@keyframes scan{to{left:100%}}

.logo{display:flex;align-items:center;gap:12px;width:280px;flex-shrink:0}
.logo-svg{width:42px;height:42px;flex-shrink:0;animation:rotateGear 14s linear infinite}
@keyframes rotateGear{from{transform:rotate(0)}to{transform:rotate(360deg)}}
.logo-text h1{font-size:18px;font-weight:700;letter-spacing:2px;
  background:linear-gradient(90deg,var(--orange),var(--gold),#fff);-webkit-background-clip:text;-webkit-text-fill-color:transparent}
.logo-text p{font-size:9px;color:var(--text2);letter-spacing:3px;margin-top:2px}

.tabs{display:flex;gap:3px;background:rgba(0,0,0,0.4);padding:5px;
  border-radius:40px;border:1px solid var(--border)}
.tab{padding:7px 18px;border-radius:30px;cursor:pointer;font-size:13px;color:var(--text2);
  transition:all .3s;white-space:nowrap;user-select:none}
.tab:hover{color:var(--orange)}
.tab.active{background:linear-gradient(135deg,rgba(255,140,40,0.3),rgba(200,80,20,0.25));
  color:#fff5e0;border:1px solid rgba(255,180,80,0.45);
  box-shadow:0 0 18px rgba(255,140,40,0.35),inset 0 0 12px rgba(255,180,80,0.08)}

.hdr-right{width:280px;flex-shrink:0;display:flex;flex-direction:column;align-items:flex-end;gap:3px}
.clock{font-size:26px;font-weight:700;color:var(--orange);letter-spacing:3px;
  font-variant-numeric:tabular-nums;text-shadow:0 0 20px rgba(255,140,40,0.5)}
.date-row{display:flex;align-items:center;gap:8px;font-size:11px;color:var(--text2)}
.sdot{width:7px;height:7px;border-radius:50%;background:var(--green);
  animation:pulse 2s ease-in-out infinite}
@keyframes pulse{0%,100%{box-shadow:0 0 0 0 rgba(0,255,157,.5)}50%{box-shadow:0 0 0 5px rgba(0,255,157,0)}}

.content{flex:1;overflow:hidden;padding:10px 14px;display:flex;flex-direction:column}
.tab-panel{display:none;flex:1;flex-direction:column;gap:10px;min-height:0;animation:fadeIn .35s ease}
.tab-panel.active{display:flex}
@keyframes fadeIn{from{opacity:0;transform:translateY(8px)}to{opacity:1;transform:translateY(0)}}

.kpi-row{display:grid;grid-template-columns:repeat(4,1fr);gap:10px;flex-shrink:0}
.kpi-card{background:linear-gradient(135deg,rgba(255,180,80,0.06),rgba(0,0,0,0.2));
  border:1px solid var(--border);border-radius:8px;
  padding:14px 18px;position:relative;overflow:hidden;transition:all .3s;cursor:default}
.kpi-card:hover{border-color:rgba(255,180,80,.5);transform:translateY(-2px);
  box-shadow:0 8px 28px rgba(0,0,0,.4)}
.kpi-card::before{content:'';position:absolute;top:0;left:0;width:3px;height:100%;border-radius:2px}
.kpi-card.c1::before{background:linear-gradient(180deg,var(--cyan),transparent)}
.kpi-card.c2::before{background:linear-gradient(180deg,var(--green),transparent)}
.kpi-card.c3::before{background:linear-gradient(180deg,var(--orange),transparent)}
.kpi-card.c4::before{background:linear-gradient(180deg,var(--purple),transparent)}
.kpi-card.c5::before{background:linear-gradient(180deg,var(--red),transparent)}

.kpi-label{font-size:11px;color:var(--text2);margin-bottom:7px;display:flex;align-items:center;gap:5px}
.kpi-label .ldot{width:5px;height:5px;border-radius:50%}
.kpi-value{font-size:28px;font-weight:700;line-height:1.1;margin-bottom:7px;color:var(--text)}
.kpi-value .unit{font-size:13px;color:var(--text2);margin-left:3px;font-weight:400}
.kpi-trend{font-size:11px}
.kpi-trend.up{color:var(--green)}.kpi-trend.dn{color:var(--red)}.kpi-trend.eq{color:var(--text2)}

.charts-row{flex:1;display:grid;gap:10px;min-height:0}
.cr3{grid-template-columns:5fr 3fr 4fr}

.chart-card{background:linear-gradient(135deg,rgba(255,180,80,0.04),rgba(0,0,0,0.2));
  border:1px solid var(--border);border-radius:8px;
  padding:12px;display:flex;flex-direction:column;position:relative;overflow:hidden}
.chart-card:hover{border-color:rgba(255,180,80,.4)}
.chart-card::before,.chart-card::after{content:'';position:absolute;width:18px;height:18px;
  pointer-events:none;opacity:.6;z-index:0}
.chart-card::before{top:0;left:0;border-top:1px solid var(--orange);border-left:1px solid var(--orange)}
.chart-card::after{bottom:0;right:0;border-bottom:1px solid var(--orange);border-right:1px solid var(--orange)}

.ct{font-size:12px;color:var(--text2);margin-bottom:8px;flex-shrink:0;
  display:flex;align-items:center;justify-content:space-between;z-index:1}
.ct-l{display:flex;align-items:center;gap:6px;color:var(--text)}
.ct-l::before{content:'';width:3px;height:13px;background:var(--orange);border-radius:2px;flex-shrink:0}
.ct-tag{font-size:10px;padding:2px 8px;border-radius:10px;
  background:rgba(255,180,80,.1);color:var(--orange);border:1px solid rgba(255,180,80,.25)}
.ct-tag.warn{background:rgba(255,107,53,.12);color:var(--orange);border-color:rgba(255,107,53,.3)}
.ct-tag.ok{background:rgba(0,255,157,.1);color:var(--green);border-color:rgba(0,255,157,.25)}
.ct-tag.bad{background:rgba(255,68,102,.12);color:var(--red);border-color:rgba(255,68,102,.3)}

.ci{flex:1;min-height:0;z-index:1}

.dtable{width:100%;border-collapse:collapse;font-size:11.5px}
.dtable th{color:var(--text2);padding:5px 8px;text-align:left;
  border-bottom:1px solid var(--border);font-weight:400;white-space:nowrap;
  position:sticky;top:0;background:rgba(20,12,4,0.92);z-index:1}
.dtable td{padding:6px 8px;border-bottom:1px solid rgba(255,180,80,.06);color:var(--text)}
.dtable tr:hover td{background:rgba(255,180,80,.05)}
.sub{color:var(--text2);font-size:10.5px}

.badge{display:inline-block;padding:1px 7px;border-radius:10px;font-size:10px;white-space:nowrap}
.bw{background:rgba(255,140,40,.15);color:var(--orange)}
.bo{background:rgba(0,255,157,.12);color:var(--green)}
.be{background:rgba(255,68,102,.15);color:var(--red)}
.bi{background:rgba(0,212,255,.12);color:var(--cyan)}

.footer{height:34px;flex-shrink:0;background:rgba(0,0,0,.55);
  border-top:1px solid var(--border);display:flex;align-items:center;overflow:hidden}
.tk-label{background:linear-gradient(90deg,rgba(255,140,40,.85),rgba(200,80,20,.8));
  padding:0 14px;height:100%;display:flex;align-items:center;
  font-size:11px;font-weight:700;letter-spacing:1px;color:#fff;flex-shrink:0}
.tk-wrap{flex:1;overflow:hidden}
.tk-inner{display:flex;gap:50px;white-space:nowrap;animation:tkroll 50s linear infinite;
  font-size:11.5px;color:var(--text2)}
.tk-item{display:flex;align-items:center;gap:7px}
.tk-dot{width:5px;height:5px;border-radius:50%;flex-shrink:0}
@keyframes tkroll{0%{transform:translateX(0)}100%{transform:translateX(-50%)}}
</style>
