<template>
  <div class="hub-root">
    <canvas ref="particles" class="hub-particles"></canvas>
    <div class="hub-grid"></div>

    <div class="hub-header">
      <div class="hub-logo">
        <div class="hub-logo-icon">⚡</div>
        <div>
          <h1>AI-MES 数据大屏中心</h1>
          <p>SMART MANUFACTURING DASHBOARD HUB</p>
        </div>
      </div>
      <div class="hub-time">{{ clockStr }}<span class="hub-date">{{ dateStr }}</span></div>
    </div>

    <div class="hub-grid-area">
      <div v-for="card in cards" :key="card.path"
           class="hub-card" :style="{'--c': card.color}"
           @click="$router.push(card.path)">
        <div class="hub-card-glow"></div>
        <div class="hub-card-icon">{{ card.icon }}</div>
        <div class="hub-card-title">{{ card.title }}</div>
        <div class="hub-card-sub">{{ card.subtitle }}</div>
        <div class="hub-card-tag">{{ card.tag }}</div>
        <div class="hub-card-arrow">→</div>
      </div>
    </div>

    <div class="hub-footer">
      <span>共 {{ cards.length }} 个数据大屏 · 点击任意卡片进入</span>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'

const cards = [
  { path: '/tv',           title: 'TV 大屏 V1',          subtitle: '订单·入库·交期·质量', icon: '📺', color: '#00d4ff', tag: '综合' },
  { path: '/tv2',          title: 'TV 大屏 V2',          subtitle: '5 模块 KPI 总览',   icon: '🎯', color: '#5b8def', tag: '综合' },
  { path: '/tv/production',title: '生产指挥中心',         subtitle: '工单·工序·产能·异常', icon: '⚙', color: '#00d4ff', tag: '生产' },
  { path: '/tv/sales',     title: '销售战情中心',         subtitle: '订单·客户·发货·利润', icon: '📈', color: '#5b8def', tag: '销售' },
  { path: '/tv/inventory', title: '库存调度中心',         subtitle: '台账·流水·预警·任务', icon: '📦', color: '#14b8a6', tag: '库存' },
  { path: '/tv/purchase',  title: '采购作战中心',         subtitle: '订单·申请·供应商·收货', icon: '🚚', color: '#a855f7', tag: '采购' },
  { path: '/tv/quality',   title: '质量管控中心',         subtitle: '质检·趋势·缺陷·任务', icon: '✓', color: '#22e58d', tag: '质检' },
  { path: '/tv/finance',   title: '财务驾驶舱',           subtitle: '资金·应收·应付·成本', icon: '💰', color: '#ffd700', tag: '财务' },
  { path: '/tv/outsource', title: '外协协同中心',         subtitle: '申请·订单·供应商·跟踪', icon: '🤝', color: '#ff4d8d', tag: '外协' },
  { path: '/tv/mrp',       title: 'MRP 智能运算中心',     subtitle: '运算·建议·计划·物料', icon: '🧠', color: '#a855f7', tag: '计划' },
  { path: '/tv/workreport',title: '班组报工战报',         subtitle: '产出·工资·班组·异常', icon: '📝', color: '#22e58d', tag: '报工' },
  { path: '/tv/eam',       title: 'EAM 设备资产大屏',     subtitle: '设备·告警·保养·点检·维修', icon: '🏭', color: '#ff8c28', tag: '设备' }
]

const clockStr = ref('00:00:00')
const dateStr = ref('')
const DAYS = ['周日','周一','周二','周三','周四','周五','周六']
let timer = null
function tick() {
  const n = new Date()
  clockStr.value = [n.getHours(), n.getMinutes(), n.getSeconds()].map(v => String(v).padStart(2, '0')).join(':')
  dateStr.value = `${n.getFullYear()}-${String(n.getMonth() + 1).padStart(2, '0')}-${String(n.getDate()).padStart(2, '0')} ${DAYS[n.getDay()]}`
}

const particles = ref(null)
let raf = null
function startParticles() {
  const canvas = particles.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  const resize = () => { canvas.width = innerWidth; canvas.height = innerHeight }
  resize()
  window.addEventListener('resize', resize)
  const pts = []
  for (let i = 0; i < 80; i++) {
    pts.push({ x: Math.random() * innerWidth, y: Math.random() * innerHeight,
      vx: (Math.random() - .5) * .25, vy: (Math.random() - .5) * .25,
      r: Math.random() * 1.4 + .4, a: Math.random() * .35 + .08 })
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
      if (d < 100) { ctx.beginPath(); ctx.strokeStyle = `rgba(0,180,255,${.06 * (1 - d / 100)})`
        ctx.lineWidth = .5; ctx.moveTo(pts[i].x, pts[i].y); ctx.lineTo(pts[j].x, pts[j].y); ctx.stroke() }
    }
    raf = requestAnimationFrame(loop)
  }
  loop()
}

onMounted(() => { tick(); timer = setInterval(tick, 1000); startParticles() })
onBeforeUnmount(() => { if (timer) clearInterval(timer); if (raf) cancelAnimationFrame(raf) })
</script>

<style scoped>
.hub-root{
  width:100%;height:100vh;
  background:radial-gradient(ellipse at top,#0a1828 0%,#060d1f 60%,#000 100%);
  color:#dff0ff;font-family:'Microsoft YaHei','PingFang SC',sans-serif;
  overflow:hidden;position:fixed;top:0;left:0;display:flex;flex-direction:column
}
.hub-particles{position:fixed;top:0;left:0;width:100%;height:100%;z-index:0;pointer-events:none}
.hub-grid{position:fixed;top:0;left:0;width:100%;height:100%;z-index:0;pointer-events:none;
  background-image:linear-gradient(rgba(0,180,255,0.025) 1px,transparent 1px),
    linear-gradient(90deg,rgba(0,180,255,0.025) 1px,transparent 1px);background-size:60px 60px}

.hub-header{position:relative;z-index:1;height:88px;padding:0 40px;
  display:flex;align-items:center;justify-content:space-between;
  border-bottom:1px solid rgba(0,180,255,0.18);
  background:linear-gradient(180deg,rgba(0,80,180,0.18) 0%,rgba(0,0,0,0) 100%)}
.hub-logo{display:flex;align-items:center;gap:18px}
.hub-logo-icon{font-size:42px;width:60px;height:60px;display:flex;align-items:center;justify-content:center;
  background:rgba(0,180,255,0.12);border:1px solid rgba(0,180,255,0.4);border-radius:12px;
  box-shadow:0 0 24px rgba(0,180,255,0.3)}
.hub-logo h1{font-size:24px;font-weight:800;letter-spacing:3px;
  background:linear-gradient(90deg,#00d4ff,#fff);-webkit-background-clip:text;-webkit-text-fill-color:transparent}
.hub-logo p{font-size:11px;color:#5a8aaa;letter-spacing:5px;margin-top:4px}

.hub-time{font-size:34px;font-weight:700;color:#00d4ff;letter-spacing:4px;
  font-variant-numeric:tabular-nums;text-shadow:0 0 22px rgba(0,212,255,0.6);
  display:flex;flex-direction:column;align-items:flex-end;gap:6px;line-height:1}
.hub-date{font-size:13px;color:#5a8aaa;letter-spacing:2px}

.hub-grid-area{position:relative;z-index:1;flex:1;padding:36px 48px;
  display:grid;grid-template-columns:repeat(4,1fr);grid-auto-rows:1fr;gap:22px;
  overflow:auto}
.hub-card{position:relative;border-radius:14px;cursor:pointer;
  background:linear-gradient(135deg,rgba(0,30,60,0.5),rgba(0,12,32,0.7));
  border:1px solid rgba(0,180,255,0.22);overflow:hidden;
  padding:28px 28px 24px;display:flex;flex-direction:column;justify-content:space-between;
  transition:transform .35s ease,border-color .35s ease,box-shadow .35s ease;
  min-height:160px}
.hub-card:hover{transform:translateY(-6px);
  border-color:var(--c);box-shadow:0 14px 40px rgba(0,0,0,0.5),0 0 28px var(--c)}
.hub-card-glow{position:absolute;top:-50%;left:-50%;width:200%;height:200%;
  background:radial-gradient(circle at 30% 30%,var(--c) 0%,transparent 30%);
  opacity:0.06;pointer-events:none;transition:opacity .35s ease}
.hub-card:hover .hub-card-glow{opacity:0.18}
.hub-card-icon{font-size:38px;line-height:1;
  filter:drop-shadow(0 0 12px var(--c))}
.hub-card-title{font-size:19px;font-weight:700;color:#fff;letter-spacing:1px;margin-top:14px}
.hub-card-sub{font-size:11.5px;color:#7a96b6;margin-top:6px;letter-spacing:.5px}
.hub-card-tag{position:absolute;top:14px;right:14px;font-size:10.5px;padding:3px 10px;
  border-radius:12px;background:rgba(0,180,255,0.12);color:var(--c);
  border:1px solid var(--c);text-shadow:0 0 8px var(--c)}
.hub-card-arrow{position:absolute;bottom:18px;right:24px;font-size:22px;color:var(--c);
  opacity:0;transform:translateX(-8px);transition:all .35s ease}
.hub-card:hover .hub-card-arrow{opacity:1;transform:translateX(0)}

.hub-footer{position:relative;z-index:1;height:42px;display:flex;align-items:center;justify-content:center;
  font-size:12px;color:#5a8aaa;letter-spacing:2px;border-top:1px solid rgba(0,180,255,0.15)}

@media (max-width:1280px){.hub-grid-area{grid-template-columns:repeat(3,1fr)}}
@media (max-width:960px){.hub-grid-area{grid-template-columns:repeat(2,1fr)}}
</style>
