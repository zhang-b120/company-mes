<template>
  <div class="tv2-root">
    <canvas ref="particles" id="particles"></canvas>
    <div class="grid-bg"></div>
    <div class="app">
      <!-- ===== HEADER ===== -->
      <div class="header">
        <div class="header-scan"></div>
        <div class="logo">
          <svg class="logo-svg" viewBox="0 0 42 42" fill="none">
            <rect width="42" height="42" rx="8" fill="rgba(0,150,255,0.08)" stroke="rgba(0,180,255,0.3)" stroke-width="1"/>
            <circle cx="21" cy="21" r="10" stroke="rgba(0,212,255,0.5)" stroke-width="1.5" fill="none"/>
            <circle cx="21" cy="21" r="4" fill="#00d4ff"/>
            <line x1="21" y1="8" x2="21" y2="11" stroke="#00d4ff" stroke-width="1.5"/>
            <line x1="21" y1="31" x2="21" y2="34" stroke="#00d4ff" stroke-width="1.5"/>
            <line x1="8" y1="21" x2="11" y2="21" stroke="#00d4ff" stroke-width="1.5"/>
            <line x1="31" y1="21" x2="34" y2="21" stroke="#00d4ff" stroke-width="1.5"/>
            <circle cx="21" cy="21" r="7" stroke="rgba(0,212,255,0.2)" stroke-width="4" fill="none"/>
          </svg>
          <div class="logo-text"><h1>xxx</h1><p>智慧工厂数据大屏</p></div>
        </div>

        <div style="display:flex;flex-direction:column;align-items:center;gap:5px">
          <div class="tabs">
            <div v-for="(t, i) in tabsData" :key="i"
                 :class="['tab', { active: curTab === i }]"
                 @click="onTabClick(i)">{{ t }}</div>
          </div>
          <div style="width:100%;height:2px;background:rgba(0,180,255,0.1);border-radius:2px;overflow:hidden">
            <div :style="{ height:'100%', width: progress + '%', background:'linear-gradient(90deg,var(--cyan),var(--green))', borderRadius:'2px' }"></div>
          </div>
        </div>

        <div class="hdr-right">
          <div class="clock">{{ clockStr }}</div>
          <div class="date-row">
            <span>{{ dateStr }}</span>
            <span class="sdot"></span>
            <span style="color:var(--green)">系统运行正常</span>
          </div>
        </div>
      </div>

      <!-- ===== CONTENT ===== -->
      <div class="content">
        <!-- TAB 0: 生产总览 -->
        <div :class="['tab-panel', { active: curTab === 0 }]">
          <div class="kpi-row">
            <div class="kpi-card c1">
              <div class="kpi-label"><span class="ldot" style="background:var(--cyan)"></span>在手工单总数</div>
              <div class="kpi-value"><span class="num" data-k="production.totalWo" data-t="0">0</span><span class="unit">个</span></div>
              <div class="kpi-trend up">↑ 较上月增加</div>
            </div>
            <div class="kpi-card c2">
              <div class="kpi-label"><span class="ldot" style="background:var(--green)"></span>已完成工单</div>
              <div class="kpi-value"><span class="num" data-k="production.completedWo" data-t="0">0</span><span class="unit">个</span></div>
              <div class="kpi-trend up">↑ 完成率 76.9%</div>
            </div>
            <div class="kpi-card c3">
              <div class="kpi-label"><span class="ldot" style="background:var(--orange)"></span>进行中工单</div>
              <div class="kpi-value"><span class="num" data-k="production.inProgressWo" data-t="0">0</span><span class="unit">个</span></div>
              <div class="kpi-trend eq">→ 正常生产中</div>
            </div>
            <div class="kpi-card c4">
              <div class="kpi-label"><span class="ldot" style="background:var(--purple)"></span>累计生产数量</div>
              <div class="kpi-value"><span class="num" data-k="production.totalQty" data-t="0">0</span><span class="unit">件</span></div>
              <div class="kpi-trend up">↑ 按计划推进</div>
            </div>
          </div>
          <div class="charts-row cr3">
            <div class="chart-card">
              <div class="ct"><div class="ct-l">近7日工单完成趋势</div><span class="ct-tag ok">实时更新</span></div>
              <div class="ci" ref="cProdTrend"></div>
            </div>
            <div class="chart-card">
              <div class="ct"><div class="ct-l">工单状态分布</div></div>
              <div class="ci" ref="cProdPie"></div>
            </div>
            <div class="chart-card">
              <div class="ct"><div class="ct-l">各产品线工单数量</div></div>
              <div class="ci" ref="cProdEquip"></div>
            </div>
          </div>
        </div>

        <!-- TAB 1: 销售订单 -->
        <div :class="['tab-panel', { active: curTab === 1 }]">
          <div class="kpi-row">
            <div class="kpi-card c1">
              <div class="kpi-label"><span class="ldot" style="background:var(--cyan)"></span>本月订单总额</div>
              <div class="kpi-value"><span class="prefix">¥</span><span class="num" data-k="sales.monthAmount" data-t="0">0</span><span class="unit">元</span></div>
              <div class="kpi-trend up">↑ 共 2 笔在手订单</div>
            </div>
            <div class="kpi-card c2">
              <div class="kpi-label"><span class="ldot" style="background:var(--green)"></span>待发货订单</div>
              <div class="kpi-value"><span class="num" data-k="sales.toShip" data-t="0">0</span><span class="unit">个</span></div>
              <div class="kpi-trend eq">→ 生产完成待发货</div>
            </div>
            <div class="kpi-card c5">
              <div class="kpi-label"><span class="ldot" style="background:var(--red)"></span>逾期订单预警</div>
              <div class="kpi-value"><span class="num" data-k="sales.overdue" data-t="0">0</span><span class="unit">个</span></div>
              <div :class="['kpi-trend', salesOverdue > 0 ? 'dn' : 'eq']">
                {{ salesOverdue > 0 ? `⚠ ${salesOverdue} 个订单已逾期` : '→ 暂无逾期订单' }}
              </div>
            </div>
            <div class="kpi-card c4">
              <div class="kpi-label"><span class="ldot" style="background:var(--purple)"></span>活跃客户数</div>
              <div class="kpi-value"><span class="num" data-k="sales.activeCustomer" data-t="0">0</span><span class="unit">家</span></div>
              <div class="kpi-trend up">↑ 本月在手订单客户</div>
            </div>
          </div>
          <div class="charts-row cr3">
            <div class="chart-card">
              <div class="ct"><div class="ct-l">近12月销售订单趋势</div><span class="ct-tag">元</span></div>
              <div class="ci" ref="cSalesTrend"></div>
            </div>
            <div class="chart-card">
              <div class="ct"><div class="ct-l">订单状态分布</div></div>
              <div class="ci" ref="cSalesPie"></div>
            </div>
            <div class="chart-card">
              <div class="ct"><div class="ct-l">TOP 5 客户销售额</div></div>
              <div class="ci" ref="cSalesTop"></div>
            </div>
          </div>
        </div>

        <!-- TAB 2: 库存管理 -->
        <div :class="['tab-panel', { active: curTab === 2 }]">
          <div class="kpi-row">
            <div class="kpi-card c1">
              <div class="kpi-label"><span class="ldot" style="background:var(--cyan)"></span>库存物料种数</div>
              <div class="kpi-value"><span class="num" data-k="inventory.materialKinds" data-t="0">0</span><span class="unit">种</span></div>
              <div class="kpi-trend eq">→ 涵盖原料与成品</div>
            </div>
            <div class="kpi-card c3">
              <div class="kpi-label"><span class="ldot" style="background:var(--orange)"></span>预警物料种数</div>
              <div class="kpi-value"><span class="num" data-k="inventory.warnKinds" data-t="0">0</span><span class="unit">种</span></div>
              <div class="kpi-trend dn">↓ 需及时补货</div>
            </div>
            <div class="kpi-card c2">
              <div class="kpi-label"><span class="ldot" style="background:var(--green)"></span>本期采购入库</div>
              <div class="kpi-value"><span class="num" data-k="inventory.inBatch" data-t="0">0</span><span class="unit">批</span></div>
              <div class="kpi-trend up">↑ 全部入库完成</div>
            </div>
            <div class="kpi-card c4">
              <div class="kpi-label"><span class="ldot" style="background:var(--purple)"></span>本期生产出库</div>
              <div class="kpi-value"><span class="num" data-k="inventory.outBatch" data-t="0">0</span><span class="unit">批</span></div>
              <div class="kpi-trend up">↑ 完工工单已出库</div>
            </div>
          </div>
          <div class="charts-row cr3">
            <div class="chart-card">
              <div class="ct"><div class="ct-l">近30日库存出入趋势</div><span class="ct-tag">件</span></div>
              <div class="ci" ref="cInvTrend"></div>
            </div>
            <div class="chart-card">
              <div class="ct"><div class="ct-l">仓库库存占比</div></div>
              <div class="ci" ref="cInvPie"></div>
            </div>
            <div class="chart-card">
              <div class="ct"><div class="ct-l">库存预警物料</div><span class="ct-tag warn">{{ warnRows.length }}种预警</span></div>
              <div class="ci" style="overflow-y:auto">
                <table class="dtable">
                  <thead><tr><th>物料名称</th><th>仓库</th><th>当前库存</th><th>安全/最大</th><th>状态</th></tr></thead>
                  <tbody>
                    <tr v-for="(r, idx) in warnRows" :key="idx">
                      <td>{{ r.name }}</td>
                      <td>{{ r.warehouse }}</td>
                      <td :style="{ color: r.cls === 'be' ? 'var(--red)' : 'var(--orange)' }">{{ r.qty }}</td>
                      <td>{{ r.threshold }}</td>
                      <td><span class="badge" :class="r.cls">{{ r.txt }}</span></td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </div>

        <!-- TAB 3: 采购管理 -->
        <div :class="['tab-panel', { active: curTab === 3 }]">
          <div class="kpi-row">
            <div class="kpi-card c1">
              <div class="kpi-label"><span class="ldot" style="background:var(--cyan)"></span>本月采购金额</div>
              <div class="kpi-value"><span class="prefix">¥</span><span class="num" data-k="purchase.monthAmount" data-t="0">0</span><span class="unit">元</span></div>
              <div class="kpi-trend up">↑ 共 2 笔采购订单</div>
            </div>
            <div class="kpi-card c3">
              <div class="kpi-label"><span class="ldot" style="background:var(--orange)"></span>待到货订单</div>
              <div class="kpi-value"><span class="num" data-k="purchase.toReceive" data-t="0">0</span><span class="unit">个</span></div>
              <div :class="['kpi-trend', purchaseToReceive > 0 ? 'dn' : 'up']">
                {{ purchaseToReceive > 0 ? `⏳ 共 ${purchaseToReceive} 个待入库` : '↑ 全部入库完成' }}
              </div>
            </div>
            <div class="kpi-card c2">
              <div class="kpi-label"><span class="ldot" style="background:var(--green)"></span>合作供应商数</div>
              <div class="kpi-value"><span class="num" data-k="purchase.supplierCount" data-t="0">0</span><span class="unit">家</span></div>
              <div class="kpi-trend up">↑ 全部按时交货</div>
            </div>
            <div class="kpi-card c4">
              <div class="kpi-label"><span class="ldot" style="background:var(--purple)"></span>准时到货率</div>
              <div class="kpi-value"><span class="num" data-k="purchase.onTimeRate" data-t="0">0</span><span class="unit">%</span></div>
              <div class="kpi-trend up">↑ 全部入库完成</div>
            </div>
          </div>
          <div class="charts-row cr3">
            <div class="chart-card">
              <div class="ct"><div class="ct-l">月度采购订单数量</div><span class="ct-tag">笔</span></div>
              <div class="ci" ref="cPurTrend"></div>
            </div>
            <div class="chart-card">
              <div class="ct"><div class="ct-l">采购金额 TOP 5 供应商</div><span class="ct-tag">元</span></div>
              <div class="ci" ref="cPurRadar"></div>
            </div>
            <div class="chart-card">
              <div class="ct"><div class="ct-l">采购品类占比</div></div>
              <div class="ci" ref="cPurCat"></div>
            </div>
          </div>
        </div>

        <!-- TAB 4: 质量管理 -->
        <div :class="['tab-panel', { active: curTab === 4 }]">
          <div class="kpi-row">
            <div class="kpi-card c2">
              <div class="kpi-label"><span class="ldot" style="background:var(--green)"></span>产品综合合格率</div>
              <div class="kpi-value"><span class="num" data-k="quality.passRate" data-t="0">0</span><span class="unit">%</span></div>
              <div class="kpi-trend up">↑ 达标良好</div>
            </div>
            <div class="kpi-card c1">
              <div class="kpi-label"><span class="ldot" style="background:var(--cyan)"></span>累计检验批次</div>
              <div class="kpi-value"><span class="num" data-k="quality.totalBatch" data-t="0">0</span><span class="unit">批</span></div>
              <div class="kpi-trend up">↑ 与完工工单一致</div>
            </div>
            <div class="kpi-card c3">
              <div class="kpi-label"><span class="ldot" style="background:var(--orange)"></span>不合格品数量</div>
              <div class="kpi-value"><span class="num" data-k="quality.defectQty" data-t="0">0</span><span class="unit">件</span></div>
              <div class="kpi-trend dn">↓ 已返工处理</div>
            </div>
            <div class="kpi-card c5">
              <div class="kpi-label"><span class="ldot" style="background:var(--red)"></span>返修工单数</div>
              <div class="kpi-value"><span class="num" data-k="quality.reworkCount" data-t="0">0</span><span class="unit">单</span></div>
              <div :class="['kpi-trend', reworkCount > 0 ? 'dn' : 'eq']">
                {{ reworkCount > 0 ? `⚠ 共 ${reworkCount} 单返修中` : '→ 暂无返修工单' }}
              </div>
            </div>
          </div>
          <div class="charts-row cr3">
            <div class="chart-card">
              <div class="ct"><div class="ct-l">近7周检验批次趋势</div><span class="ct-tag ok">良好</span></div>
              <div class="ci" ref="cQuaTrend"></div>
            </div>
            <div class="chart-card">
              <div class="ct"><div class="ct-l">不合格品缺陷分类</div></div>
              <div class="ci" ref="cQuaPie"></div>
            </div>
            <div class="chart-card">
              <div class="ct"><div class="ct-l">质检类型分布</div></div>
              <div class="ci" ref="cQuaBar"></div>
            </div>
          </div>
        </div>
      </div>

      <!-- FOOTER TICKER -->
      <div class="footer">
        <div class="tk-label">▶ 实时消息</div>
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
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts'
import { getTv2All } from '@/api/tv'

// ========== 数据 ==========
const tabsData = ['⚙ 生产总览', '📦 销售订单', '🏭 库存管理', '🚚 采购管理', '✅ 质量管理']
const curTab = ref(0)
const progress = ref(0)
const clockStr = ref('00:00:00')
const dateStr = ref('')
const DAYS = ['周日','周一','周二','周三','周四','周五','周六']

import { computed } from 'vue'

// 用于 KPI trend 文字判定
const salesOverdue = computed(() => Number(apiData.value?.sales?.overdue || 0))
const purchaseToReceive = computed(() => Number(apiData.value?.purchase?.toReceive || 0))
const reworkCount = computed(() => Number(apiData.value?.quality?.reworkCount || 0))

const warnRows = computed(() => {
  const list = apiData.value?.inventory?.warnList || []
  return list.map(r => {
    const qty = Number(r.qty || 0)
    const safe = Number(r.safe || 0)
    const maxStock = Number(r.maxStock || 0)
    const isExcess = maxStock > 0 && qty > maxStock
    const isShortage = safe > 0 && qty < safe
    let cls, txt, threshold
    if (isExcess) {
      cls = 'bw'
      txt = '超储'
      threshold = `最大 ${maxStock}`
    } else if (isShortage) {
      const ratio = safe > 0 ? qty / safe : 1
      cls = ratio < 0.4 ? 'be' : 'bw'
      txt = ratio < 0.4 ? '严重不足' : '库存偏低'
      threshold = `安全 ${safe}`
    } else {
      cls = 'bi'; txt = '正常'; threshold = '-'
    }
    return {
      name: `${r.name || ''}${r.code ? ' (' + r.code + ')' : ''}`,
      warehouse: r.warehouse || '-',
      qty, safe, threshold, cls, txt
    }
  })
})

// 实时消息(从后端 apiData.ticker 取真实业务事件)
const tickerAll = computed(() => {
  const msgs = apiData.value?.ticker || []
  if (!msgs.length) return [{ c: 'var(--green)', t: '系统运行正常,暂无重要消息' }]
  // 拼两份保证滚动循环不留白
  return [...msgs, ...msgs]
})

// ========== refs ==========
const particles = ref(null)
const cProdTrend = ref(null)
const cProdPie = ref(null)
const cProdEquip = ref(null)
const cSalesTrend = ref(null)
const cSalesPie = ref(null)
const cSalesTop = ref(null)
const cInvTrend = ref(null)
const cInvPie = ref(null)
const cPurTrend = ref(null)
const cPurRadar = ref(null)
const cPurCat = ref(null)
const cQuaTrend = ref(null)
const cQuaPie = ref(null)
const cQuaBar = ref(null)

// ========== ECHART helpers ==========
const C = { c: '#00d4ff', g: '#00ff9d', o: '#ff6b35', p: '#a855f7', r: '#ff4466', y: '#ffd700' }
function base() {
  return {
    backgroundColor: 'transparent',
    textStyle: { color: '#5a8aaa' },
    tooltip: { backgroundColor: 'rgba(6,13,31,.92)', borderColor: 'rgba(0,180,255,.25)', textStyle: { color: '#dff0ff' }, extraCssText: 'border-radius:6px;padding:10px 14px' },
    grid: { top: 32, right: 16, bottom: 28, left: 46, containLabel: true }
  }
}
function xAxis(data) {
  return { type: 'category', data, axisLine: { lineStyle: { color: 'rgba(0,180,255,.15)' } }, axisTick: { show: false }, axisLabel: { color: '#5a8aaa', fontSize: 11 } }
}
function yAxis(fmt) {
  return { type: 'value', splitLine: { lineStyle: { color: 'rgba(0,180,255,.08)', type: 'dashed' } }, axisLabel: { color: '#5a8aaa', fontSize: 11, formatter: fmt || '{value}' }, axisLine: { show: false }, axisTick: { show: false } }
}
function pieItemStyle() { return { borderRadius: 4, borderColor: '#060d1f', borderWidth: 2 } }
// 饼图 label 完整显示(不截断)
function pieLabel() { return { color: '#5a8aaa', fontSize: 11, overflow: 'none', width: 200, lineHeight: 14 } }
function pieLabelLine() { return { length: 12, length2: 14 } }
function legendStyle() { return { bottom: 4, textStyle: { color: '#5a8aaa', fontSize: 11 }, itemWidth: 10, itemHeight: 10, itemGap: 10 } }

const chartInsts = []
const inited = {}

// ========== TABS ==========
const AUTO_INTERVAL = 8000
let autoTimer = null
let progressRaf = null
let progressStart = 0
let resumeTimer = null

// API 数据
const apiData = ref(null)
function getByPath(obj, path) {
  if (!obj || !path) return undefined
  return path.split('.').reduce((a, k) => (a == null ? undefined : a[k]), obj)
}

function runCounters(panelEl) {
  if (!panelEl) return
  panelEl.querySelectorAll('.num').forEach(el => {
    // 优先用 API 数据,fallback 到 data-t
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

// 状态值 → 中文映射
const WO_STATUS = { pending: '待排产', scheduled: '已排产', in_progress: '生产中', completed: '已完工', cancelled: '已取消' }
const SO_STATUS = { draft: '草稿', pending_approval: '待审批', confirmed: '已确认', mrp_planned: '已转计划',
  in_production: '生产中', partial_shipped: '部分发货', shipped: '已发货', settled: '已结算',
  cancelled: '已取消', voided: '已作废', completed: '已完成',
  outsourcing: '外协中', outsourced: '已外协', outsource: '外协中',
  rejected: '已驳回', closed: '已关闭', pending: '待处理' }
const COLORS = [C.c, C.g, C.o, C.p, C.y, C.r]
function pickColor(i) { return COLORS[i % COLORS.length] }
function gradBar(c) {
  return { type: 'linear', x: 0, y: 0, x2: 1, y2: 0,
    colorStops: [{ offset: 0, color: 'rgba(0,180,255,.2)' }, { offset: 1, color: c }] }
}

function tab0() {
  const p = apiData.value?.production || {}
  const trend = p.trend || []
  const days = trend.map(r => r.date)
  const planned = trend.map(r => Number(r.planned || 0))
  const completed = trend.map(r => Number(r.completed || 0))
  const ch0 = echarts.init(cProdTrend.value); chartInsts.push(ch0)
  ch0.setOption({ ...base(),
    legend: { top: 4, right: 8, textStyle: { color: '#5a8aaa', fontSize: 11 }, data: ['计划工单','完成工单'] },
    xAxis: xAxis(days), yAxis: yAxis('{value}个'),
    series: [
      { name: '计划工单', type: 'line', data: planned, lineStyle: { color: 'rgba(0,212,255,.45)', width: 1.5, type: 'dashed' }, itemStyle: { color: C.c }, symbol: 'none' },
      { name: '完成工单', type: 'line', data: completed, lineStyle: { color: C.g, width: 2 }, itemStyle: { color: C.g }, symbol: 'circle', symbolSize: 5,
        areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(0,255,157,.18)' }, { offset: 1, color: 'rgba(0,255,157,0)' }] } } }
    ]
  })

  const pieData = (p.statusPie || []).map((r, i) => ({
    name: WO_STATUS[r.name] || r.name || '未知', value: Number(r.value || 0),
    itemStyle: { color: pickColor(i) }
  }))
  const ch1 = echarts.init(cProdPie.value); chartInsts.push(ch1)
  ch1.setOption({ ...base(), legend: legendStyle(),
    series: [{ type: 'pie', radius: ['38%','60%'], center: ['50%','46%'], data: pieData,
      label: pieLabel(), labelLine: pieLabelLine(), itemStyle: pieItemStyle(), emphasis: { scaleSize: 4 } }]
  })

  const bar = (p.productBar || []).slice().reverse()
  const ch2 = echarts.init(cProdEquip.value); chartInsts.push(ch2)
  ch2.setOption({ ...base(),
    grid: { top: 12, right: 55, bottom: 12, left: 8, containLabel: true },
    xAxis: { type: 'value', splitLine: { lineStyle: { color: 'rgba(0,180,255,.08)', type: 'dashed' } }, axisLabel: { color: '#5a8aaa', fontSize: 10, formatter: '{value}个' } },
    yAxis: { type: 'category', data: bar.map(r => r.name || '-'),
      axisLabel: { color: '#5a8aaa', fontSize: 11 }, axisLine: { lineStyle: { color: 'rgba(0,180,255,.15)' } }, axisTick: { show: false } },
    series: [{ type: 'bar',
      data: bar.map((r, i) => ({ value: Number(r.value || 0), itemStyle: { color: gradBar(pickColor(i)) } })),
      barMaxWidth: 14, itemStyle: { borderRadius: [0,3,3,0] },
      label: { show: true, position: 'right', color: '#5a8aaa', fontSize: 11, formatter: '{c}个' },
      showBackground: true, backgroundStyle: { color: 'rgba(0,180,255,.04)', borderRadius: [0,3,3,0] }
    }]
  })
}

function tab1() {
  const s = apiData.value?.sales || {}
  const trend = s.trend || []
  const ch0 = echarts.init(cSalesTrend.value); chartInsts.push(ch0)
  ch0.setOption({ ...base(),
    xAxis: xAxis(trend.map(r => r.month)), yAxis: yAxis(v => v + '元'),
    series: [{ name: '订单金额', type: 'bar', barMaxWidth: 18,
      data: trend.map(r => Number(r.amount || 0)),
      itemStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: C.c }, { offset: 1, color: 'rgba(0,212,255,.08)' }] }, borderRadius: [3,3,0,0] }
    }]
  })

  const pieData = (s.statusPie || []).map((r, i) => ({
    name: SO_STATUS[r.name] || r.name || '未知',
    value: Number(r.value || 0),
    itemStyle: { color: pickColor(i) }
  }))
  const ch1 = echarts.init(cSalesPie.value); chartInsts.push(ch1)
  ch1.setOption({ ...base(), legend: legendStyle(),
    series: [{ type: 'pie', radius: ['35%','58%'], center: ['50%','46%'], data: pieData,
      label: pieLabel(), labelLine: pieLabelLine(), itemStyle: pieItemStyle() }]
  })

  const top = (s.customerTop || []).slice().reverse()
  const ch2 = echarts.init(cSalesTop.value); chartInsts.push(ch2)
  ch2.setOption({ ...base(),
    grid: { top: 12, right: 60, bottom: 12, left: 8, containLabel: true },
    xAxis: { type: 'value', splitLine: { lineStyle: { color: 'rgba(0,180,255,.08)', type: 'dashed' } }, axisLabel: { color: '#5a8aaa', fontSize: 10, formatter: v => v + '元' } },
    yAxis: { type: 'category', data: top.map(r => r.name || '-'),
      axisLabel: { color: '#5a8aaa', fontSize: 10 }, axisLine: { lineStyle: { color: 'rgba(0,180,255,.15)' } }, axisTick: { show: false } },
    series: [{ type: 'bar',
      data: top.map((r, i) => ({ value: Number(r.value || 0), itemStyle: { color: gradBar(pickColor(i)) } })),
      barMaxWidth: 14, itemStyle: { borderRadius: [0,3,3,0] },
      label: { show: true, position: 'right', color: '#5a8aaa', fontSize: 10, formatter: '{c}元' },
      showBackground: true, backgroundStyle: { color: 'rgba(0,180,255,.04)', borderRadius: [0,3,3,0] }
    }]
  })
}

function tab2() {
  const inv = apiData.value?.inventory || {}
  const trend = inv.trend || []
  const ch0 = echarts.init(cInvTrend.value); chartInsts.push(ch0)
  ch0.setOption({ ...base(),
    legend: { top: 4, right: 8, textStyle: { color: '#5a8aaa', fontSize: 11 }, data: ['入库','出库'] },
    xAxis: xAxis(trend.map(r => r.date)),
    yAxis: yAxis('{value}笔'),
    series: [
      { name: '入库', type: 'bar', barMaxWidth: 7, data: trend.map(r => Number(r.in || 0)),
        itemStyle: { color: 'rgba(0,255,157,.5)', borderRadius: [2,2,0,0] } },
      { name: '出库', type: 'bar', barMaxWidth: 7, data: trend.map(r => Number(r.out || 0)),
        itemStyle: { color: 'rgba(255,107,53,.5)', borderRadius: [2,2,0,0] } }
    ]
  })

  const pieData = (inv.warehousePie || []).map((r, i) => ({
    name: r.name || '-', value: Number(r.value || 0),
    itemStyle: { color: pickColor(i) }
  }))
  const ch1 = echarts.init(cInvPie.value); chartInsts.push(ch1)
  ch1.setOption({ ...base(), legend: legendStyle(),
    series: [{ type: 'pie', radius: ['35%','58%'], center: ['50%','46%'], data: pieData,
      label: pieLabel(), labelLine: pieLabelLine(), itemStyle: pieItemStyle() }]
  })
}

function tab3() {
  const pu = apiData.value?.purchase || {}
  const trend = pu.trend || []
  const ch0 = echarts.init(cPurTrend.value); chartInsts.push(ch0)
  ch0.setOption({ ...base(),
    xAxis: xAxis(trend.map(r => r.month)),
    yAxis: { type: 'value', splitLine: { lineStyle: { color: 'rgba(0,180,255,.08)', type: 'dashed' } }, axisLabel: { color: '#5a8aaa', fontSize: 11, formatter: '{value}笔' }, axisLine: { show: false }, axisTick: { show: false } },
    series: [{ name: '采购笔数', type: 'bar', barMaxWidth: 32, data: trend.map(r => Number(r.count || 0)),
      itemStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: C.p }, { offset: 1, color: 'rgba(168,85,247,.08)' }] }, borderRadius: [3,3,0,0] },
      label: { show: true, position: 'top', color: '#5a8aaa', fontSize: 10, formatter: p => p.value || '' }
    }]
  })

  // 采购金额 TOP 5 供应商(横向条)
  const supAmount = (pu.supplierAmountTop || []).slice().reverse()
  const ch1 = echarts.init(cPurRadar.value); chartInsts.push(ch1)
  ch1.setOption({ ...base(),
    grid: { top: 12, right: 70, bottom: 12, left: 8, containLabel: true },
    xAxis: { type: 'value', splitLine: { lineStyle: { color: 'rgba(0,180,255,.08)', type: 'dashed' } }, axisLabel: { color: '#5a8aaa', fontSize: 10, formatter: v => v + '元' } },
    yAxis: { type: 'category', data: supAmount.map(r => r.name || '-'),
      axisLabel: { color: '#5a8aaa', fontSize: 10 }, axisLine: { lineStyle: { color: 'rgba(0,180,255,.15)' } }, axisTick: { show: false } },
    series: [{ type: 'bar',
      data: supAmount.map((r, i) => ({ value: Number(r.value || 0), itemStyle: { color: gradBar(pickColor(i)) } })),
      barMaxWidth: 14, itemStyle: { borderRadius: [0,3,3,0] },
      label: { show: true, position: 'right', color: '#5a8aaa', fontSize: 10, formatter: '¥{c}' },
      showBackground: true, backgroundStyle: { color: 'rgba(0,180,255,.04)', borderRadius: [0,3,3,0] }
    }]
  })

  const supPie = (pu.supplierPie || []).map((r, i) => ({
    name: r.name || '-', value: Number(r.value || 0), itemStyle: { color: pickColor(i) }
  }))
  const ch2 = echarts.init(cPurCat.value); chartInsts.push(ch2)
  ch2.setOption({ ...base(), legend: legendStyle(),
    series: [{ type: 'pie', radius: ['35%','58%'], center: ['50%','46%'], data: supPie,
      label: pieLabel(), labelLine: pieLabelLine(), itemStyle: pieItemStyle() }]
  })
}

function tab4() {
  const q = apiData.value?.quality || {}
  const trend = q.trend || []
  const ch0 = echarts.init(cQuaTrend.value); chartInsts.push(ch0)
  ch0.setOption({ ...base(),
    legend: { top: 4, right: 8, textStyle: { color: '#5a8aaa', fontSize: 11 }, data: ['合格率%','检验批次'] },
    xAxis: xAxis(trend.map(r => r.week)),
    yAxis: [
      { type: 'value', min: 90, max: 100, splitLine: { lineStyle: { color: 'rgba(0,180,255,.08)', type: 'dashed' } }, axisLabel: { color: '#5a8aaa', fontSize: 10, formatter: v => v + '%' }, axisLine: { show: false }, axisTick: { show: false } },
      { type: 'value', name: '批', nameTextStyle: { color: '#5a8aaa', fontSize: 10 }, splitLine: { show: false }, axisLabel: { color: '#5a8aaa', fontSize: 10 }, axisLine: { show: false }, axisTick: { show: false } }
    ],
    series: [
      { name: '合格率%', type: 'line', smooth: true, data: trend.map(r => Number(r.passRate || 0)),
        lineStyle: { color: C.g, width: 2 }, itemStyle: { color: C.g }, symbol: 'circle', symbolSize: 5,
        areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(0,255,157,.2)' }, { offset: 1, color: 'rgba(0,255,157,0)' }] } } },
      { name: '检验批次', type: 'bar', yAxisIndex: 1, barMaxWidth: 10, data: trend.map(r => Number(r.batch || 0)),
        itemStyle: { color: 'rgba(0,212,255,.35)', borderRadius: [2,2,0,0] } }
    ]
  })

  // 缺陷分类:把英文 defect_type 翻译为中文展示(数据库存的是英文)
  const DEFECT_CN = {
    'appearance': '外观缺陷', 'dimensional': '尺寸超差', 'dimension': '尺寸超差',
    'performance': '性能缺陷', 'material': '材料缺陷',
    'process': '工艺缺陷', 'assembly': '装配缺陷',
    'function': '功能缺陷', 'package': '包装缺陷', 'packaging': '包装缺陷',
    'other': '其他', 'unknown': '其他', '': '其他'
  }
  const pieData = (q.defectPie || []).map((r, i) => {
    const raw = String(r.name || '').toLowerCase()
    return { name: DEFECT_CN[raw] || r.name || '其他', value: Number(r.value || 0), itemStyle: { color: pickColor(i) } }
  })
  const ch1 = echarts.init(cQuaPie.value); chartInsts.push(ch1)
  ch1.setOption({ ...base(), legend: legendStyle(),
    series: [{ type: 'pie', radius: ['32%','50%'], center: ['50%','46%'], data: pieData,
      label: pieLabel(), labelLine: pieLabelLine(), itemStyle: pieItemStyle() }]
  })

  // 质检类型分布(替代原"各产品线合格率")
  const QC_TYPE = {
    incoming: '来料检 IQC', process: '过程检 IPQC', final: '成品检 FQC',
    outgoing: '出货检 OQC', outsource: '外协检', semi: '半成品检',
    qc_pass: '质检通过', qc_fail: '质检不通过',
    pending: '待质检', completed: '已完成', unknown: '其他'
  }
  const qcPie = (q.qcTypePie || []).map((r, i) => {
    const k = String(r.name || '').toLowerCase()
    return { name: QC_TYPE[k] || r.name || '其他', value: Number(r.value || 0),
      itemStyle: { color: pickColor(i) } }
  })
  const ch2 = echarts.init(cQuaBar.value); chartInsts.push(ch2)
  ch2.setOption({ ...base(), legend: legendStyle(),
    series: [{ type: 'pie', radius: ['38%','60%'], center: ['50%','46%'], data: qcPie,
      label: { color: '#5a8aaa', fontSize: 11, formatter: '{b}\n{d}%' },
      itemStyle: pieItemStyle(), emphasis: { scaleSize: 4 } }]
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
      ctx.fillStyle = `rgba(0,180,255,${p.a})`; ctx.fill()
    })
    for (let i = 0; i < pts.length; i++) for (let j = i + 1; j < pts.length; j++) {
      const dx = pts[i].x - pts[j].x, dy = pts[i].y - pts[j].y, d = Math.sqrt(dx * dx + dy * dy)
      if (d < 90) { ctx.beginPath(); ctx.strokeStyle = `rgba(0,180,255,${.06 * (1 - d / 90)})`; ctx.lineWidth = .5; ctx.moveTo(pts[i].x, pts[i].y); ctx.lineTo(pts[j].x, pts[j].y); ctx.stroke() }
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
    const res = await getTv2All()
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
  // 数据回来后再初始化首个 tab,确保图表能用真实数据
  switchTab(0)
  startAuto()
  // 60 秒刷新一次数据(只刷数据,不重新 init 图表;下次 switchTab 会用新数据)
  refreshTimer = setInterval(async () => {
    await loadApiData()
    // 标记所有 tab 重新初始化,使图表下次切换或刷新时取新值
    Object.keys(inited).forEach(k => delete inited[k])
    // 立即重画当前 tab
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
.tv2-root {
  --bg:#060d1f;--bg2:#0a1628;--card:rgba(0,180,255,0.04);--border:rgba(0,180,255,0.18);
  --cyan:#00d4ff;--green:#00ff9d;--orange:#ff6b35;--purple:#a855f7;--red:#ff4466;--gold:#ffd700;
  --text:#dff0ff;--text2:#5a8aaa;
  width:100%;height:100vh;background:var(--bg);color:var(--text);
  font-family:'Microsoft YaHei','PingFang SC',sans-serif;overflow:hidden;position:fixed;top:0;left:0;
}
#particles{position:fixed;top:0;left:0;width:100%;height:100%;z-index:0;pointer-events:none}
.grid-bg{position:fixed;top:0;left:0;width:100%;height:100%;z-index:0;pointer-events:none;
  background-image:linear-gradient(rgba(0,180,255,0.025) 1px,transparent 1px),
    linear-gradient(90deg,rgba(0,180,255,0.025) 1px,transparent 1px);
  background-size:60px 60px}
.app{position:relative;z-index:1;width:100%;height:100vh;display:flex;flex-direction:column}

.header{height:68px;flex-shrink:0;
  background:linear-gradient(180deg,rgba(0,80,180,0.18) 0%,rgba(0,0,0,0) 100%);
  border-bottom:1px solid var(--border);
  display:flex;align-items:center;justify-content:space-between;padding:0 20px;
  position:relative;overflow:hidden}
.header-scan{position:absolute;bottom:0;left:-100%;width:100%;height:1px;
  background:linear-gradient(90deg,transparent,var(--cyan),transparent);
  animation:scan 4s linear infinite}
@keyframes scan{to{left:100%}}

.logo{display:flex;align-items:center;gap:12px;width:260px;flex-shrink:0}
.logo-svg{width:42px;height:42px;flex-shrink:0}
.logo-text h1{font-size:17px;font-weight:700;letter-spacing:2px;
  background:linear-gradient(90deg,var(--cyan),#fff);-webkit-background-clip:text;-webkit-text-fill-color:transparent}
.logo-text p{font-size:10px;color:var(--text2);letter-spacing:4px;margin-top:2px}

.tabs{display:flex;gap:3px;background:rgba(0,0,0,0.35);padding:5px;
  border-radius:40px;border:1px solid var(--border)}
.tab{padding:7px 18px;border-radius:30px;cursor:pointer;font-size:13px;color:var(--text2);
  transition:all .3s;white-space:nowrap;user-select:none}
.tab:hover{color:var(--cyan)}
.tab.active{background:linear-gradient(135deg,rgba(0,180,255,0.25),rgba(0,80,200,0.2));
  color:var(--cyan);border:1px solid rgba(0,180,255,0.35);
  box-shadow:0 0 18px rgba(0,180,255,0.25),inset 0 0 12px rgba(0,180,255,0.08)}

.hdr-right{width:260px;flex-shrink:0;display:flex;flex-direction:column;align-items:flex-end;gap:3px}
.clock{font-size:26px;font-weight:700;color:var(--cyan);letter-spacing:3px;
  font-variant-numeric:tabular-nums;text-shadow:0 0 20px rgba(0,212,255,0.5)}
.date-row{display:flex;align-items:center;gap:8px;font-size:11px;color:var(--text2)}
.sdot{width:7px;height:7px;border-radius:50%;background:var(--green);
  animation:pulse 2s ease-in-out infinite}
@keyframes pulse{0%,100%{box-shadow:0 0 0 0 rgba(0,255,157,.5)}50%{box-shadow:0 0 0 5px rgba(0,255,157,0)}}

.content{flex:1;overflow:hidden;padding:10px 14px;display:flex;flex-direction:column}
.tab-panel{display:none;flex:1;flex-direction:column;gap:10px;min-height:0;animation:fadeIn .35s ease}
.tab-panel.active{display:flex}
@keyframes fadeIn{from{opacity:0;transform:translateY(8px)}to{opacity:1;transform:translateY(0)}}

.kpi-row{display:grid;grid-template-columns:repeat(4,1fr);gap:10px;flex-shrink:0}
.kpi-card{background:var(--card);border:1px solid var(--border);border-radius:8px;
  padding:14px 18px;position:relative;overflow:hidden;transition:all .3s;cursor:default}
.kpi-card:hover{border-color:rgba(0,180,255,.4);transform:translateY(-2px);
  box-shadow:0 8px 28px rgba(0,0,0,.35)}
.kpi-card::before{content:'';position:absolute;top:0;left:0;width:3px;height:100%;border-radius:2px}
.kpi-card.c1::before{background:linear-gradient(180deg,var(--cyan),transparent)}
.kpi-card.c2::before{background:linear-gradient(180deg,var(--green),transparent)}
.kpi-card.c3::before{background:linear-gradient(180deg,var(--orange),transparent)}
.kpi-card.c4::before{background:linear-gradient(180deg,var(--purple),transparent)}
.kpi-card.c5::before{background:linear-gradient(180deg,var(--red),transparent)}
.kpi-card.c6::before{background:linear-gradient(180deg,var(--gold),transparent)}

.kpi-label{font-size:11px;color:var(--text2);margin-bottom:7px;display:flex;align-items:center;gap:5px}
.kpi-label .ldot{width:5px;height:5px;border-radius:50%}
.kpi-value{font-size:26px;font-weight:700;line-height:1.1;margin-bottom:7px}
.kpi-value .unit{font-size:13px;color:var(--text2);margin-left:3px;font-weight:400}
.kpi-value .prefix{font-size:16px;font-weight:400;color:var(--text2)}
.kpi-trend{font-size:11px}
.kpi-trend.up{color:var(--green)}.kpi-trend.dn{color:var(--red)}.kpi-trend.eq{color:var(--text2)}

.charts-row{flex:1;display:grid;gap:10px;min-height:0}
.cr3{grid-template-columns:5fr 3fr 3fr}

.chart-card{background:var(--card);border:1px solid var(--border);border-radius:8px;
  padding:12px;display:flex;flex-direction:column;position:relative;overflow:hidden}
.chart-card:hover{border-color:rgba(0,180,255,.3)}
.chart-card::before,.chart-card::after{content:'';position:absolute;width:18px;height:18px;
  pointer-events:none;opacity:.5;z-index:0}
.chart-card::before{top:0;left:0;border-top:1px solid var(--cyan);border-left:1px solid var(--cyan)}
.chart-card::after{bottom:0;right:0;border-bottom:1px solid var(--cyan);border-right:1px solid var(--cyan)}

.ct{font-size:12px;color:var(--text2);margin-bottom:8px;flex-shrink:0;
  display:flex;align-items:center;justify-content:space-between;z-index:1}
.ct-l{display:flex;align-items:center;gap:6px}
.ct-l::before{content:'';width:3px;height:13px;background:var(--cyan);border-radius:2px;flex-shrink:0}
.ct-tag{font-size:10px;padding:2px 8px;border-radius:10px;
  background:rgba(0,180,255,.08);color:var(--cyan);border:1px solid rgba(0,180,255,.2)}
.ct-tag.warn{background:rgba(255,107,53,.1);color:var(--orange);border-color:rgba(255,107,53,.25)}
.ct-tag.ok{background:rgba(0,255,157,.08);color:var(--green);border-color:rgba(0,255,157,.2)}
.ct-tag.bad{background:rgba(255,68,102,.1);color:var(--red);border-color:rgba(255,68,102,.25)}

.ci{flex:1;min-height:0;z-index:1}

.dtable{width:100%;border-collapse:collapse;font-size:11.5px}
.dtable th{color:var(--text2);padding:5px 8px;text-align:left;
  border-bottom:1px solid var(--border);font-weight:400;white-space:nowrap}
.dtable td{padding:6px 8px;border-bottom:1px solid rgba(0,180,255,.05);color:var(--text)}
.dtable tr:hover td{background:rgba(0,180,255,.04)}
.badge{display:inline-block;padding:1px 7px;border-radius:10px;font-size:10px}
.bw{background:rgba(255,107,53,.12);color:var(--orange)}
.bo{background:rgba(0,255,157,.1);color:var(--green)}
.be{background:rgba(255,68,102,.12);color:var(--red)}
.bi{background:rgba(0,180,255,.1);color:var(--cyan)}

.footer{height:34px;flex-shrink:0;background:rgba(0,0,0,.45);
  border-top:1px solid var(--border);display:flex;align-items:center;overflow:hidden}
.tk-label{background:linear-gradient(90deg,rgba(0,180,255,.8),rgba(0,80,200,.8));
  padding:0 14px;height:100%;display:flex;align-items:center;
  font-size:11px;font-weight:700;letter-spacing:1px;color:#fff;flex-shrink:0}
.tk-wrap{flex:1;overflow:hidden}
.tk-inner{display:flex;gap:50px;white-space:nowrap;animation:tkroll 50s linear infinite;
  font-size:11.5px;color:var(--text2)}
.tk-item{display:flex;align-items:center;gap:7px}
.tk-dot{width:5px;height:5px;border-radius:50%;flex-shrink:0}
@keyframes tkroll{0%{transform:translateX(0)}100%{transform:translateX(-50%)}}
</style>
