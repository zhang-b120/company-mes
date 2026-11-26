import request from '@/utils/request'

export function login(data) {
  return request.post('/auth/login', data)
}

export function refreshToken(data) {
  return request.post('/auth/refresh', data)
}

/** 工位机刷卡免密登录(传入读卡器读取的IC卡号,后端反查员工) */
export function cardLogin(icCardNo) {
  return request.post('/auth/card-login', { icCardNo })
}

/** 工位机手动账号免密登录(IC卡号过长不便手输,使用员工账号兜底) */
export function cardLoginByUsername(username) {
  return request.post('/auth/card-login', { username })
}

export function getUserInfo() {
  return request.get('/auth/userinfo')
}

export function changePassword(data) {
  return request.put('/auth/password', data)
}

/** SSO 回调：用 SSO token 换取系统 JWT */
export function ssoCallback(data) {
  return request.post('/auth/sso-callback', data)
}

/** SSO 登出：清除服务端 SSO 会话 */
export function ssoLogout() {
  return request.post('/auth/sso-logout')
}

/** 检查当前 SSO 认证状态（token/cookie 是否有效） */
export function checkSsoStatus() {
  return request.get('/auth/sso-status')
}
