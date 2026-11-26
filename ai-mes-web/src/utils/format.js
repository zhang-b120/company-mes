/**
 * 通用格式化工具函数
 */

/**
 * 格式化日期时间
 * 将 ISO 格式（如 "2025-02-18T17:00:00"）转为 "YYYY-MM-DD HH:mm"
 * @param {string} dateStr 日期字符串
 * @param {string} fmt 格式模板，默认 'YYYY-MM-DD HH:mm'
 * @returns {string} 格式化后的字符串
 */
export function formatDateTime(dateStr, fmt = 'YYYY-MM-DD HH:mm') {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  if (isNaN(d.getTime())) return dateStr
  const pad = (n) => String(n).padStart(2, '0')
  const map = {
    YYYY: d.getFullYear(),
    MM: pad(d.getMonth() + 1),
    DD: pad(d.getDate()),
    HH: pad(d.getHours()),
    mm: pad(d.getMinutes()),
    ss: pad(d.getSeconds())
  }
  return fmt.replace(/YYYY|MM|DD|HH|mm|ss/g, m => map[m])
}

/**
 * 格式化日期（不含时间）
 * @param {string} dateStr 日期字符串
 * @returns {string} YYYY-MM-DD
 */
export function formatDate(dateStr) {
  return formatDateTime(dateStr, 'YYYY-MM-DD')
}

/**
 * 格式化物料规格型号
 * 单规格：直接返回字符串；多规格：JSON 数组 [{name, values}] → "颜色:红/蓝 尺寸:M/L"
 * @param {string} spec 规格字段原始值
 * @returns {string}
 */
export function formatSpec(spec) {
  if (!spec) return '-'
  try {
    const p = JSON.parse(spec)
    if (Array.isArray(p) && p.length > 0 && p[0].name) {
      return p.map(d => `${d.name}:${(d.values || [d.value]).filter(Boolean).join('/')}`).join(' ')
    }
  } catch {}
  return spec
}

/**
 * 格式化金额
 * @param {number|string} amount 金额
 * @param {number} decimals 小数位，默认2
 * @returns {string} 格式化后的金额字符串，如 "¥1,234.56"
 */
export function formatMoney(amount, decimals = 2) {
  if (amount == null || amount === '') return '-'
  const num = Number(amount)
  if (isNaN(num)) return '-'
  return '¥' + num.toLocaleString('zh-CN', {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals
  })
}
