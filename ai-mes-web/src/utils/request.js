/**
 * Axios 请求封装
 * 统一处理请求拦截（添加 Token）、响应拦截（错误处理）、Token 无感刷新
 */
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import router from '@/router'
import { resetRouter } from '@/router'

/** 创建 Axios 实例，设置基础路径和超时时间 */
const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 30000
})

/** 是否正在刷新 Token 的标记，防止并发刷新 */
let isRefreshing = false
/** 等待 Token 刷新完成的请求队列 */
let pendingRequests = []

/**
 * 处理等待队列中的请求
 * 刷新 Token 成功后，重新发起队列中挂起的请求
 * @param {string} newToken - 新的访问令牌
 */
function processPendingRequests(newToken) {
  pendingRequests.forEach(callback => callback(newToken))
  pendingRequests = []
}

/**
 * 处理登出逻辑
 * 清除用户状态、重置路由、跳转到登录页
 */
function handleLogout() {
  const userStore = useUserStore()
  userStore.logout()
  resetRouter()
  router.push('/login')
}

/**
 * 请求拦截器
 * 在每个请求发出前，自动附加 Authorization 头
 */
service.interceptors.request.use(
  config => {
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers.Authorization = `Bearer ${userStore.token}`
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

/**
 * 响应拦截器
 * 处理业务状态码、HTTP 错误、Token 过期自动刷新
 */
service.interceptors.response.use(
  async response => {
    // blob 响应（文件下载）直接返回数据
    if (response.config.responseType === 'blob') {
      return response.data
    }

    const res = response.data

    // 业务状态码非 200 表示业务逻辑错误
    if (res.code !== 200) {
      // noAuth 请求(大屏等)出错时不弹错、不登出,避免污染观看体验
      if (response.config?.noAuth) {
        return Promise.reject(new Error(res.msg || '请求失败'))
      }
      // 业务层面返回 401 (HTTP 200 但 body 里 code=401, 长跑请求结束后常见)
      // 走和 HTTP 401 一样的无感 refresh + 重试链路, 不直接踢下线
      if (res.code === 401) {
        const originalRequest = response.config
        if (!originalRequest._retried) {
          if (isRefreshing) {
            return new Promise(resolve => {
              pendingRequests.push(newToken => {
                originalRequest.headers.Authorization = `Bearer ${newToken}`
                resolve(service(originalRequest))
              })
            })
          }
          originalRequest._retried = true
          isRefreshing = true
          try {
            const userStore = useUserStore()
            const ok = await userStore.doRefreshToken()
            if (ok) {
              const newToken = userStore.token
              originalRequest.headers.Authorization = `Bearer ${newToken}`
              processPendingRequests(newToken)
              return service(originalRequest)
            } else {
              pendingRequests = []
              handleLogout()
              return Promise.reject(new Error(res.msg || 'Token 已过期'))
            }
          } catch (e) {
            pendingRequests = []
            handleLogout()
            return Promise.reject(e)
          } finally {
            isRefreshing = false
          }
        }
        // 已经重试过还失败 → 真的踢下线
        handleLogout()
        return Promise.reject(new Error(res.msg || '请求失败'))
      }
      ElMessage.error(res.msg || '请求失败')
      return Promise.reject(new Error(res.msg || '请求失败'))
    }
    return res
  },
  async error => {
    const originalRequest = error.config

    // noAuth 请求(大屏)直接拒绝,不走刷新 token / 不登出 / 不弹全局错误
    if (originalRequest?.noAuth) {
      return Promise.reject(error)
    }

    // HTTP 401：Token 过期，尝试无感刷新
    if (error.response?.status === 401 && !originalRequest._retried) {
      // 如果当前正在刷新 Token，将请求加入等待队列
      if (isRefreshing) {
        return new Promise(resolve => {
          pendingRequests.push(newToken => {
            originalRequest.headers.Authorization = `Bearer ${newToken}`
            resolve(service(originalRequest))
          })
        })
      }

      // 标记正在刷新，防止并发刷新
      originalRequest._retried = true
      isRefreshing = true

      try {
        const userStore = useUserStore()
        const success = await userStore.doRefreshToken()
        if (success) {
          const newToken = userStore.token
          // 刷新成功，重新发起原始请求
          originalRequest.headers.Authorization = `Bearer ${newToken}`
          // 处理等待队列中的其他请求
          processPendingRequests(newToken)
          return service(originalRequest)
        } else {
          // 刷新失败，清空队列并登出
          pendingRequests = []
          handleLogout()
          return Promise.reject(error)
        }
      } catch {
        // 刷新异常，清空队列并登出
        pendingRequests = []
        handleLogout()
        return Promise.reject(error)
      } finally {
        isRefreshing = false
      }
    }

    // 非 401 错误或刷新后仍失败，显示错误提示
    ElMessage.error(error.response?.data?.msg || error.message || '网络错误')
    return Promise.reject(error)
  }
)

export default service
