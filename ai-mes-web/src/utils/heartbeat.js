/**
 * Token 心跳保活
 * 长跑请求(数据生成/批量导入等可能跑 1+ 小时的操作)期间, 每 N 分钟刷一次 token,
 * 保证响应回来时 token 还有效。基于"作用域引用计数": startHeartbeat 增 1,
 * stopHeartbeat 减 1, 全部 stop 才停止 timer (支持多个长跑请求并发触发)。
 */
import { useUserStore } from '@/store/user'

let timer = null
let activeCount = 0
const REFRESH_INTERVAL_MS = 10 * 60 * 1000  // 10 分钟刷一次

async function tick() {
  try {
    const userStore = useUserStore()
    if (!userStore.refreshTokenValue) return
    await userStore.doRefreshToken()
  } catch {
    // 刷新失败不阻塞, 等下一轮; 真过期了 axios 拦截器会兜底
  }
}

/** 开始心跳 — 长跑请求发起前调一次 */
export function startHeartbeat() {
  activeCount += 1
  if (timer) return
  timer = setInterval(tick, REFRESH_INTERVAL_MS)
}

/** 停止心跳 — 长跑请求结束(成功/失败)都调一次 */
export function stopHeartbeat() {
  activeCount = Math.max(0, activeCount - 1)
  if (activeCount === 0 && timer) {
    clearInterval(timer)
    timer = null
  }
}

/** 包装一个 promise: 自动 start/stop 心跳, 不用手动 try/finally */
export function withHeartbeat(promise) {
  startHeartbeat()
  return Promise.resolve(promise).finally(stopHeartbeat)
}
