import { onMounted, onBeforeUnmount } from 'vue'

/**
 * 工位机刷卡监听 Composable
 * USB刷卡器模拟键盘快速输入（按键间隔<80ms），输入完毕后超时自动触发。
 *
 * 刷卡器只输出"原始IC卡号"（不再携带任何用户信息），
 * 卡号→员工的映射由后端通过 sys_user.ic_card_no 反查。
 *
 * @param {Function} onCardSwipe 回调函数，参数 { icCardNo }
 */

// 刷卡器按键间隔阈值（毫秒），超过此间隔视为人工输入
const MAX_KEY_INTERVAL = 80
// 输入完毕后等待触发的静默期（毫秒）
const FLUSH_DELAY = 150
// 卡号最小有效长度（过短视为误触）
const MIN_BUFFER_LENGTH = 5

export function useCardReader(onCardSwipe) {
  let buffer = ''
  let lastKeyTime = 0
  let flushTimer = null

  function tryFlush() {
    const raw = buffer.trim()
    buffer = ''
    if (raw.length < MIN_BUFFER_LENGTH) return
    onCardSwipe({ icCardNo: raw })
  }

  function onKeyDown(e) {
    const now = Date.now()
    if (now - lastKeyTime > MAX_KEY_INTERVAL) {
      buffer = ''
    }
    lastKeyTime = now

    if (flushTimer) {
      clearTimeout(flushTimer)
      flushTimer = null
    }

    if (e.key === 'Enter') {
      tryFlush()
      e.preventDefault()
      e.stopPropagation()
      return
    }

    if (e.key.length === 1) {
      buffer += e.key
      flushTimer = setTimeout(tryFlush, FLUSH_DELAY)
    }
  }

  onMounted(() => {
    document.addEventListener('keydown', onKeyDown, true)
  })

  onBeforeUnmount(() => {
    document.removeEventListener('keydown', onKeyDown, true)
    if (flushTimer) {
      clearTimeout(flushTimer)
      flushTimer = null
    }
  })
}
