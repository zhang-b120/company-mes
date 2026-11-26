/**
 * 用户状态管理 Store
 * 负责管理用户登录状态、Token、用户信息、菜单和权限数据
 */
import { defineStore } from 'pinia'
import { login, getUserInfo, refreshToken } from '@/api/auth'
import {
  getToken,
  setToken,
  removeToken,
  getRefreshToken,
  setRefreshToken,
  clearAuth
} from '@/utils/auth'

export const useUserStore = defineStore('user', {
  state: () => ({
    /** 访问令牌，优先从 auth 工具函数读取 */
    token: getToken() || '',
    /** 刷新令牌 */
    refreshTokenValue: getRefreshToken() || '',
    /** 当前登录用户信息 */
    userInfo: null,
    /** 后端返回的菜单树（用于侧边栏渲染和动态路由生成） */
    menus: [],
    /** 用户拥有的权限标识列表（如 system:user:add） */
    permissions: []
  }),

  actions: {
    /**
     * 执行登录操作
     * @param {Object} loginForm - 登录表单数据，包含 username 和 password
     * @returns {Object} 登录接口响应数据
     */
    async doLogin(loginForm) {
      const res = await login(loginForm)
      // 保存 token 到 store 和 localStorage（通过 auth 工具函数）
      this.token = res.data.accessToken
      this.refreshTokenValue = res.data.refreshToken
      setToken(this.token)
      setRefreshToken(this.refreshTokenValue)
      // 保存登录时返回的基础用户信息
      this.userInfo = res.data.userInfo
      return res
    },

    /**
     * 获取当前用户详情（包括菜单和权限）
     * 通常在路由守卫中调用，用于动态生成路由
     * @returns {Object} 用户信息数据
     */
    async fetchUserInfo() {
      const res = await getUserInfo()
      this.userInfo = res.data
      // 解析菜单树数据，用于侧边栏渲染和动态路由生成
      this.menus = res.data.menus || []
      // 解析权限标识列表，用于按钮级别权限控制
      this.permissions = res.data.permissions || []
      return res.data
    },

    /**
     * 刷新访问令牌
     * 使用 refreshToken 换取新的 accessToken
     * @returns {boolean} 刷新是否成功
     */
    async doRefreshToken() {
      try {
        const res = await refreshToken({ refreshToken: this.refreshTokenValue })
        // 更新 store 和 localStorage 中的 token
        this.token = res.data.accessToken
        this.refreshTokenValue = res.data.refreshToken
        setToken(this.token)
        setRefreshToken(this.refreshTokenValue)
        return true
      } catch {
        // 刷新失败则执行登出
        this.logout()
        return false
      }
    },

    /**
     * 退出登录
     * 清除所有用户状态和本地存储的认证信息
     */
    logout() {
      this.token = ''
      this.refreshTokenValue = ''
      this.userInfo = null
      this.menus = []
      this.permissions = []
      // 使用 auth 工具函数统一清除本地存储
      clearAuth()
    },

    /**
     * 检查当前用户是否拥有指定权限
     * @param {string} perm - 权限标识，如 'system:user:add'
     * @returns {boolean} 是否拥有该权限
     */
    hasPermission(perm) {
      return this.permissions.includes(perm)
    }
  }
})
