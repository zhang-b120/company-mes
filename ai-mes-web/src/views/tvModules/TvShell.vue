<template>
  <div class="tvm-root" :style="cssVars">
    <canvas ref="particles" class="tvm-particles"></canvas>
    <div class="tvm-grid"></div>
    <div class="tvm-glow"></div>

    <div class="tvm-app">
      <!-- HEADER -->
      <div class="tvm-header">
        <div class="tvm-header-scan"></div>
        <div class="tvm-logo">
          <div class="tvm-logo-icon">{{ icon }}</div>
          <div class="tvm-logo-text">
            <h1>{{ title }}</h1>
            <p>{{ subtitle }}</p>
          </div>
        </div>

        <div class="tvm-tabs-wrap">
          <div class="tvm-tabs">
            <div v-for="(t, i) in tabs" :key="i"
                 :class="['tvm-tab', { active: curTab === i }]"
                 @click="onTabClick(i)">{{ t }}</div>
          </div>
          <div class="tvm-progress">
            <div class="tvm-progress-bar" :style="{ width: progress + '%' }"></div>
          </div>
        </div>

        <div class="tvm-hdr-right">
          <div class="tvm-clock">{{ clockStr }}</div>
          <div class="tvm-date-row">
            <span>{{ dateStr }}</span>
            <span class="tvm-sdot"></span>
            <span class="tvm-status">系统运行正常</span>
          </div>
        </div>
      </div>

      <!-- CONTENT -->
      <div class="tvm-content">
        <slot :curTab="curTab" :data="apiData" :runCounters="runCountersFn"></slot>
      </div>

      <!-- FOOTER TICKER -->
      <div class="tvm-footer">
        <div class="tvm-tk-label">▶ {{ tickerLabel }}</div>
        <div class="tvm-tk-wrap">
          <div class="tvm-tk-inner">
            <span v-for="(m, i) in tickerAll" :key="i" class="tvm-tk-item">
              <span class="tvm-tk-dot" :style="{ background: m.c }"></span>{{ m.t }}
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { getModuleTv } from '@/api/tv'

const props = defineProps({
  /** 模块标识 */
  module: { type: String, required: true },
  /** 大屏标题 */
  title: { type: String, required: true },
  subtitle: { type: String, default: '' },
  /** Tab 名称数组 */
  tabs: { type: Array, required: true },
  /** Tab 切换间隔(ms) */
  interval: { type: Number, default: 9000 },
  /** 跑马灯标题 */
  tickerLabel: { type: String, default: '实时动态' },
  /** 头部图标(emoji 或字符) */
  icon: { type: String, default: '⚡' },
  /** 主题: blue/orange/green/purple/red/gold/cyan/pink/teal */
  theme: { type: String, default: 'cyan' }
})

const emit = defineEmits(['ready', 'tab-change'])

// ========== 主题配色 ==========
const THEMES = {
  cyan:   { p: '#00d4ff', s: '#00ff9d', b: '#060d1f', g: '#0a1828' },
  blue:   { p: '#5b8def', s: '#9bb5ff', b: '#0a0f1f', g: '#101a2f' },
  orange: { p: '#ff8c28', s: '#ffd700', b: '#100805', g: '#1a0f06' },
  green:  { p: '#22e58d', s: '#7df0c5', b: '#031410', g: '#062118' },
  purple: { p: '#a855f7', s: '#e879f9', b: '#0e041d', g: '#180a30' },
  red:    { p: '#ff4466', s: '#ff8b6c', b: '#180405', g: '#240810' },
  gold:   { p: '#ffd700', s: '#ffae35', b: '#150f04', g: '#221908' },
  pink:   { p: '#ff4d8d', s: '#ff8fb1', b: '#15040c', g: '#23081a' },
  teal:   { p: '#14b8a6', s: '#5eead4', b: '#021414', g: '#062424' }
}
const cssVars = computed(() => {
  const t = THEMES[props.theme] || THEMES.cyan
  return {
    '--p': t.p,
    '--s': t.s,
    '--bg': t.b,
    '--bg2': t.g,
    '--rgb-p': hexToRgb(t.p)
  }
})
function hexToRgb(hex) {
  const h = hex.replace('#', '')
  const n = parseInt(h, 16)
  return `${(n >> 16) & 255},${(n >> 8) & 255},${n & 255}`
}

// ========== 时钟 ==========
const clockStr = ref('00:00:00')
const dateStr = ref('')
const DAYS = ['周日','周一','周二','周三','周四','周五','周六']
let clockTimer = null
function tickClock() {
  const n = new Date()
  clockStr.value = [n.getHours(), n.getMinutes(), n.getSeconds()].map(v => String(v).padStart(2, '0')).join(':')
  dateStr.value = `${n.getFullYear()}-${String(n.getMonth() + 1).padStart(2, '0')}-${String(n.getDate()).padStart(2, '0')} ${DAYS[n.getDay()]}`
}

// ========== 数据 ==========
const apiData = ref({})
async function loadApiData() {
  try {
    const res = await getModuleTv(props.module)
    apiData.value = res.data || {}
  } catch (e) { /* keep prev data */ }
}

const tickerAll = computed(() => {
  const msgs = apiData.value?.ticker || []
  if (!msgs.length) return [{ c: 'var(--s)', t: '系统运行正常,暂无重要消息' }]
  return [...msgs, ...msgs]
})

// ========== Tabs ==========
const curTab = ref(0)
const progress = ref(0)
let autoTimer = null
let progressRaf = null
let progressStart = 0
let resumeTimer = null

function updateProgress() {
  progressStart = performance.now()
  if (progressRaf) cancelAnimationFrame(progressRaf)
  function step(now) {
    const pct = Math.min((now - progressStart) / props.interval * 100, 100)
    progress.value = pct
    if (pct < 100) progressRaf = requestAnimationFrame(step)
  }
  progressRaf = requestAnimationFrame(step)
}

function switchTab(i) {
  curTab.value = i
  emit('tab-change', i)
  nextTick(() => {
    runCountersFn(i)
    updateProgress()
  })
}

function startAuto() {
  stopAuto()
  autoTimer = setInterval(() => switchTab((curTab.value + 1) % props.tabs.length), props.interval)
}
function stopAuto() { if (autoTimer) { clearInterval(autoTimer); autoTimer = null } }

function onTabClick(i) {
  stopAuto()
  switchTab(i)
  if (resumeTimer) clearTimeout(resumeTimer)
  resumeTimer = setTimeout(startAuto, 15000)
}

// ========== 数字滚动 ==========
function getByPath(obj, path) {
  if (!obj || !path) return undefined
  return path.split('.').reduce((a, k) => (a == null ? undefined : a[k]), obj)
}
function runCountersFn(tabIdx) {
  const idx = tabIdx == null ? curTab.value : tabIdx
  nextTick(() => {
    const panel = document.querySelectorAll('.tvm-content .tvm-panel')[idx]
    if (!panel) return
    panel.querySelectorAll('.num').forEach(el => {
      const key = el.dataset.k
      const apiVal = key ? getByPath(apiData.value, key) : null
      const target = apiVal != null ? Number(apiVal) : (+el.dataset.t || 0)
      el.dataset.t = target
      const step = Math.max(target / 55, 1)
      let cur = 0
      const tm = setInterval(() => {
        cur = Math.min(cur + step, target)
        el.textContent = formatNum(Math.round(cur))
        if (cur >= target) {
          el.textContent = formatNum(Math.round(target))
          clearInterval(tm)
        }
      }, 18)
    })
  })
}
function formatNum(n) {
  if (n >= 100000000) return (n / 100000000).toFixed(2) + '亿'
  if (n >= 10000) return (n / 10000).toFixed(1) + '万'
  return Number(n).toLocaleString()
}

// ========== 粒子 ==========
const particles = ref(null)
let particleRaf = null
function startParticles() {
  const canvas = particles.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  const resize = () => { canvas.width = innerWidth; canvas.height = innerHeight }
  resize()
  window.addEventListener('resize', resize)
  const pts = []
  for (let i = 0; i < 60; i++) {
    pts.push({ x: Math.random() * innerWidth, y: Math.random() * innerHeight,
      vx: (Math.random() - .5) * .25, vy: (Math.random() - .5) * .25,
      r: Math.random() * 1.2 + .4, a: Math.random() * .3 + .08 })
  }
  const t = THEMES[props.theme] || THEMES.cyan
  const rgb = hexToRgb(t.p)
  function loop() {
    ctx.clearRect(0, 0, canvas.width, canvas.height)
    pts.forEach(p => {
      p.x += p.vx; p.y += p.vy
      if (p.x < 0 || p.x > canvas.width) p.vx *= -1
      if (p.y < 0 || p.y > canvas.height) p.vy *= -1
      ctx.beginPath(); ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2)
      ctx.fillStyle = `rgba(${rgb},${p.a})`; ctx.fill()
    })
    for (let i = 0; i < pts.length; i++) for (let j = i + 1; j < pts.length; j++) {
      const dx = pts[i].x - pts[j].x, dy = pts[i].y - pts[j].y, d = Math.sqrt(dx * dx + dy * dy)
      if (d < 90) {
        ctx.beginPath()
        ctx.strokeStyle = `rgba(${rgb},${.06 * (1 - d / 90)})`
        ctx.lineWidth = .5
        ctx.moveTo(pts[i].x, pts[i].y); ctx.lineTo(pts[j].x, pts[j].y); ctx.stroke()
      }
    }
    particleRaf = requestAnimationFrame(loop)
  }
  loop()
}

// ========== 生命周期 ==========
let refreshTimer = null
onMounted(async () => {
  startParticles()
  tickClock()
  clockTimer = setInterval(tickClock, 1000)
  await loadApiData()
  emit('ready', apiData.value)
  switchTab(0)
  startAuto()
  refreshTimer = setInterval(async () => {
    await loadApiData()
    emit('ready', apiData.value)
    runCountersFn(curTab.value)
  }, 60000)
})
onBeforeUnmount(() => {
  stopAuto()
  if (resumeTimer) clearTimeout(resumeTimer)
  if (clockTimer) clearInterval(clockTimer)
  if (refreshTimer) clearInterval(refreshTimer)
  if (progressRaf) cancelAnimationFrame(progressRaf)
  if (particleRaf) cancelAnimationFrame(particleRaf)
})

defineExpose({ apiData, runCountersFn })
</script>

<style scoped lang="scss">
.tvm-root {
  --text:#dff0ff;--text2:#7a96b6;--card:rgba(var(--rgb-p),0.05);--border:rgba(var(--rgb-p),0.22);
  --green:#00ff9d;--red:#ff4466;--orange:#ff8c28;--cyan:#00d4ff;--purple:#a855f7;--gold:#ffd700;
  width:100%;height:100vh;
  background:radial-gradient(ellipse at top,var(--bg2) 0%,var(--bg) 60%,#000 100%);
  color:var(--text);
  font-family:'Microsoft YaHei','PingFang SC',sans-serif;overflow:hidden;position:fixed;top:0;left:0;
}
.tvm-particles{position:fixed;top:0;left:0;width:100%;height:100%;z-index:0;pointer-events:none}
.tvm-grid{position:fixed;top:0;left:0;width:100%;height:100%;z-index:0;pointer-events:none;
  background-image:linear-gradient(rgba(var(--rgb-p),0.03) 1px,transparent 1px),
    linear-gradient(90deg,rgba(var(--rgb-p),0.03) 1px,transparent 1px);
  background-size:60px 60px}
.tvm-glow{position:fixed;top:-200px;left:50%;transform:translateX(-50%);
  width:1200px;height:400px;z-index:0;pointer-events:none;
  background:radial-gradient(ellipse,rgba(var(--rgb-p),0.18) 0%,transparent 70%);
  filter:blur(40px)}
.tvm-app{position:relative;z-index:1;width:100%;height:100vh;display:flex;flex-direction:column}

.tvm-header{height:68px;flex-shrink:0;
  background:linear-gradient(180deg,rgba(var(--rgb-p),0.18) 0%,rgba(0,0,0,0) 100%);
  border-bottom:1px solid var(--border);
  display:flex;align-items:center;justify-content:space-between;padding:0 20px;
  position:relative;overflow:hidden}
.tvm-header-scan{position:absolute;bottom:0;left:-100%;width:100%;height:1px;
  background:linear-gradient(90deg,transparent,var(--p),transparent);
  animation:tvmScan 4s linear infinite}
@keyframes tvmScan{to{left:100%}}

.tvm-logo{display:flex;align-items:center;gap:12px;width:300px;flex-shrink:0}
.tvm-logo-icon{font-size:32px;width:42px;height:42px;display:flex;align-items:center;justify-content:center;
  background:rgba(var(--rgb-p),0.12);border:1px solid rgba(var(--rgb-p),0.35);
  border-radius:8px;box-shadow:0 0 14px rgba(var(--rgb-p),0.3)}
.tvm-logo-text h1{font-size:18px;font-weight:700;letter-spacing:2px;
  background:linear-gradient(90deg,var(--p),var(--s),#fff);
  -webkit-background-clip:text;-webkit-text-fill-color:transparent}
.tvm-logo-text p{font-size:9.5px;color:var(--text2);letter-spacing:3px;margin-top:2px}

.tvm-tabs-wrap{display:flex;flex-direction:column;align-items:center;gap:5px}
.tvm-tabs{display:flex;gap:3px;background:rgba(0,0,0,0.4);padding:5px;
  border-radius:40px;border:1px solid var(--border)}
.tvm-tab{padding:7px 18px;border-radius:30px;cursor:pointer;font-size:13px;color:var(--text2);
  transition:all .3s;white-space:nowrap;user-select:none}
.tvm-tab:hover{color:var(--p)}
.tvm-tab.active{background:linear-gradient(135deg,rgba(var(--rgb-p),0.3),rgba(var(--rgb-p),0.15));
  color:#fff;border:1px solid rgba(var(--rgb-p),0.45);
  box-shadow:0 0 18px rgba(var(--rgb-p),0.35),inset 0 0 12px rgba(var(--rgb-p),0.08)}
.tvm-progress{width:100%;height:2px;background:rgba(var(--rgb-p),0.1);border-radius:2px;overflow:hidden}
.tvm-progress-bar{height:100%;background:linear-gradient(90deg,var(--p),var(--s));border-radius:2px;
  transition:width .1s linear}

.tvm-hdr-right{width:300px;flex-shrink:0;display:flex;flex-direction:column;align-items:flex-end;gap:3px}
.tvm-clock{font-size:26px;font-weight:700;color:var(--p);letter-spacing:3px;
  font-variant-numeric:tabular-nums;text-shadow:0 0 20px rgba(var(--rgb-p),0.6)}
.tvm-date-row{display:flex;align-items:center;gap:8px;font-size:11px;color:var(--text2)}
.tvm-status{color:var(--green)}
.tvm-sdot{width:7px;height:7px;border-radius:50%;background:var(--green);
  animation:tvmPulse 2s ease-in-out infinite}
@keyframes tvmPulse{0%,100%{box-shadow:0 0 0 0 rgba(0,255,157,.5)}50%{box-shadow:0 0 0 5px rgba(0,255,157,0)}}

.tvm-content{flex:1;overflow:hidden;padding:10px 14px;display:flex;flex-direction:column}

.tvm-footer{height:34px;flex-shrink:0;background:rgba(0,0,0,.5);
  border-top:1px solid var(--border);display:flex;align-items:center;overflow:hidden}
.tvm-tk-label{background:linear-gradient(90deg,rgba(var(--rgb-p),.85),rgba(var(--rgb-p),.5));
  padding:0 14px;height:100%;display:flex;align-items:center;
  font-size:11px;font-weight:700;letter-spacing:1px;color:#fff;flex-shrink:0}
.tvm-tk-wrap{flex:1;overflow:hidden}
.tvm-tk-inner{display:flex;gap:50px;white-space:nowrap;animation:tvmTkRoll 50s linear infinite;
  font-size:11.5px;color:var(--text2)}
.tvm-tk-item{display:flex;align-items:center;gap:7px}
.tvm-tk-dot{width:5px;height:5px;border-radius:50%;flex-shrink:0}
@keyframes tvmTkRoll{0%{transform:translateX(0)}100%{transform:translateX(-50%)}}
</style>
