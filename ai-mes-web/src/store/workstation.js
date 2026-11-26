import { defineStore } from 'pinia'

const WS_USER_KEY = 'ws_user_info'
const WS_THEME_KEY = 'ws_theme'

/**
 * 工位机专用 Store
 * 存储刷卡登录后的员工信息及主题设置
 */
export const useWorkstationStore = defineStore('workstation', {
  state: () => ({
    userInfo: JSON.parse(localStorage.getItem(WS_USER_KEY) || 'null'),
    // 主题: 'dark'(深色/默认) | 'light'(浅色蓝白)
    theme: localStorage.getItem(WS_THEME_KEY) || 'dark'
  }),

  getters: {
    isLoggedIn: (state) => !!state.userInfo,
    displayName: (state) => state.userInfo?.realName || state.userInfo?.username || '',
    isDark: (state) => state.theme === 'dark'
  },

  actions: {
    setUserInfo(info) {
      this.userInfo = info
      if (info) {
        localStorage.setItem(WS_USER_KEY, JSON.stringify(info))
      } else {
        localStorage.removeItem(WS_USER_KEY)
      }
    },

    setTheme(theme) {
      this.theme = theme
      localStorage.setItem(WS_THEME_KEY, theme)
    },

    toggleTheme() {
      this.setTheme(this.theme === 'dark' ? 'light' : 'dark')
    },

    clear() {
      this.userInfo = null
      localStorage.removeItem(WS_USER_KEY)
      // 主题设置不清除，保持用户偏好
    }
  }
})
