import * as echarts from 'echarts'

/** 公共调色板 */
export const COLORS = ['#00d4ff', '#00ff9d', '#ff8c28', '#a855f7', '#ffd700', '#ff4466', '#5b8def', '#22e58d']
export const pickColor = i => COLORS[i % COLORS.length]

/** 公共 base 配置 */
export function base() {
  return {
    backgroundColor: 'transparent',
    textStyle: { color: '#a8b8c8' },
    tooltip: {
      backgroundColor: 'rgba(6,13,31,.92)',
      borderColor: 'rgba(0,180,255,.25)',
      textStyle: { color: '#dff0ff' },
      extraCssText: 'border-radius:6px;padding:10px 14px'
    },
    grid: { top: 32, right: 16, bottom: 28, left: 46, containLabel: true }
  }
}

export function xAxis(data) {
  return {
    type: 'category', data,
    axisLine: { lineStyle: { color: 'rgba(120,160,200,.18)' } },
    axisTick: { show: false },
    axisLabel: { color: '#8aa0b8', fontSize: 11 }
  }
}

export function yAxis(fmt) {
  return {
    type: 'value',
    splitLine: { lineStyle: { color: 'rgba(120,160,200,.08)', type: 'dashed' } },
    axisLabel: { color: '#8aa0b8', fontSize: 11, formatter: fmt || '{value}' },
    axisLine: { show: false }, axisTick: { show: false }
  }
}

export function pieItem() { return { borderRadius: 4, borderColor: 'rgba(0,0,0,0.5)', borderWidth: 2 } }
export function pieLabel() { return { color: '#a8b8c8', fontSize: 11, overflow: 'none', width: 200, lineHeight: 14 } }
export function pieLabelLine() { return { length: 12, length2: 14 } }
export function legend() { return { bottom: 4, textStyle: { color: '#a8b8c8', fontSize: 11 }, itemWidth: 10, itemHeight: 10, itemGap: 10 } }

export function gradBar(c) {
  return {
    type: 'linear', x: 0, y: 0, x2: 1, y2: 0,
    colorStops: [{ offset: 0, color: 'rgba(120,160,200,.15)' }, { offset: 1, color: c }]
  }
}

export function gradArea(c) {
  return {
    type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
    colorStops: [{ offset: 0, color: c + '55' }, { offset: 1, color: c + '00' }]
  }
}

/** 横向条 helper */
export function horizontalBar(items, fmtSuffix = '') {
  const data = (items || []).slice().reverse()
  return {
    ...base(),
    grid: { top: 12, right: 60, bottom: 12, left: 8, containLabel: true },
    xAxis: { type: 'value', splitLine: { lineStyle: { color: 'rgba(120,160,200,.08)', type: 'dashed' } }, axisLabel: { color: '#8aa0b8', fontSize: 10, formatter: '{value}' + fmtSuffix } },
    yAxis: {
      type: 'category', data: data.map(r => r.name || '-'),
      axisLabel: { color: '#a8b8c8', fontSize: 11 },
      axisLine: { lineStyle: { color: 'rgba(120,160,200,.18)' } }, axisTick: { show: false }
    },
    series: [{
      type: 'bar',
      data: data.map((r, i) => ({ value: Number(r.value || 0), itemStyle: { color: gradBar(pickColor(i)) } })),
      barMaxWidth: 14, itemStyle: { borderRadius: [0, 3, 3, 0] },
      label: { show: true, position: 'right', color: '#a8b8c8', fontSize: 11, formatter: '{c}' + fmtSuffix },
      showBackground: true, backgroundStyle: { color: 'rgba(120,160,200,.04)', borderRadius: [0, 3, 3, 0] }
    }]
  }
}

/** 饼图 (donut style) */
export function donut(items, mapper) {
  const data = (items || []).map((r, i) => ({
    name: mapper ? (mapper(r.name) || r.name) : (r.name || '-'),
    value: Number(r.value || 0),
    itemStyle: { color: pickColor(i) }
  }))
  return {
    ...base(), legend: legend(),
    series: [{
      type: 'pie', radius: ['38%', '60%'], center: ['50%', '46%'], data,
      label: { color: '#a8b8c8', fontSize: 11, formatter: '{b}\n{d}%' },
      labelLine: pieLabelLine(), itemStyle: pieItem(), emphasis: { scaleSize: 4 }
    }]
  }
}

/** 玫瑰图 */
export function rose(items, mapper) {
  const data = (items || []).map((r, i) => ({
    name: mapper ? (mapper(r.name) || r.name) : (r.name || '-'),
    value: Number(r.value || 0),
    itemStyle: { color: pickColor(i) }
  }))
  return {
    ...base(), legend: legend(),
    series: [{
      type: 'pie', radius: ['18%', '60%'], center: ['50%', '46%'], roseType: 'radius',
      data, label: { color: '#a8b8c8', fontSize: 11, formatter: '{b}' },
      labelLine: pieLabelLine(), itemStyle: pieItem(), emphasis: { scaleSize: 4 }
    }]
  }
}

/** 双线图 */
export function dualLine(trend, xKey, k1, k2, n1, n2, c1 = '#00d4ff', c2 = '#00ff9d') {
  return {
    ...base(),
    legend: { top: 4, right: 8, textStyle: { color: '#a8b8c8', fontSize: 11 }, data: [n1, n2] },
    xAxis: xAxis(trend.map(r => r[xKey])),
    yAxis: yAxis(),
    series: [
      { name: n1, type: 'line', smooth: true, data: trend.map(r => Number(r[k1] || 0)),
        lineStyle: { color: c1, width: 2 }, itemStyle: { color: c1 }, symbol: 'circle', symbolSize: 5,
        areaStyle: { color: gradArea(c1) } },
      { name: n2, type: 'line', smooth: true, data: trend.map(r => Number(r[k2] || 0)),
        lineStyle: { color: c2, width: 2 }, itemStyle: { color: c2 }, symbol: 'circle', symbolSize: 5,
        areaStyle: { color: gradArea(c2) } }
    ]
  }
}

/** 双柱图 */
export function dualBar(trend, xKey, k1, k2, n1, n2, c1 = '#00d4ff', c2 = '#ff8c28') {
  return {
    ...base(),
    legend: { top: 4, right: 8, textStyle: { color: '#a8b8c8', fontSize: 11 }, data: [n1, n2] },
    xAxis: xAxis(trend.map(r => r[xKey])),
    yAxis: yAxis(),
    series: [
      { name: n1, type: 'bar', barMaxWidth: 10, data: trend.map(r => Number(r[k1] || 0)),
        itemStyle: { color: c1 + 'cc', borderRadius: [3, 3, 0, 0] } },
      { name: n2, type: 'bar', barMaxWidth: 10, data: trend.map(r => Number(r[k2] || 0)),
        itemStyle: { color: c2 + 'cc', borderRadius: [3, 3, 0, 0] } }
    ]
  }
}

/** 单柱图 */
export function singleBar(trend, xKey, k, name, c = '#00d4ff', suffix = '') {
  return {
    ...base(),
    xAxis: xAxis(trend.map(r => r[xKey])),
    yAxis: yAxis(v => v + suffix),
    series: [{
      name, type: 'bar', barMaxWidth: 24, data: trend.map(r => Number(r[k] || 0)),
      itemStyle: {
        color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [{ offset: 0, color: c }, { offset: 1, color: c + '15' }] },
        borderRadius: [3, 3, 0, 0]
      },
      label: { show: true, position: 'top', color: '#a8b8c8', fontSize: 10 }
    }]
  }
}

/** 同步多个图表 resize */
export function bindResize(charts) {
  const handler = () => charts.forEach(c => c && c.resize())
  window.addEventListener('resize', handler)
  return () => window.removeEventListener('resize', handler)
}

export { echarts }
