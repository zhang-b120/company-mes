/**
 * Token 管理工具
 * 提供统一的 token 存取方法，与 store/user.js 中的 localStorage key 保持一致
 */

const TOKEN_KEY = 'token'
const REFRESH_TOKEN_KEY = 'refreshToken'

/** 获取访问令牌 */
export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

/** 设置访问令牌 */
export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token)
}

/** 移除访问令牌 */
export function removeToken() {
  localStorage.removeItem(TOKEN_KEY)
}

/** 获取刷新令牌 */
export function getRefreshToken() {
  return localStorage.getItem(REFRESH_TOKEN_KEY)
}

/** 设置刷新令牌 */
export function setRefreshToken(token) {
  localStorage.setItem(REFRESH_TOKEN_KEY, token)
}

/** 移除刷新令牌 */
export function removeRefreshToken() {
  localStorage.removeItem(REFRESH_TOKEN_KEY)
}

/** 清除所有认证信息 */
export function clearAuth() {
  removeToken()
  removeRefreshToken()
}
