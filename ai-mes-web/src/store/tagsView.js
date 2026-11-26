import { defineStore } from 'pinia'

/** 首页标签（固定，不可关闭） */
const HOME_TAG = {
  path: '/dashboard',
  fullPath: '/dashboard',
  title: '首页',
  name: 'dashboard',
  affix: true
}

export const useTagsViewStore = defineStore('tagsView', {
  state: () => ({
    /** 已打开的标签列表 */
    visitedViews: [{ ...HOME_TAG }],
    /**
     * 各路径的版本号，用于 keep-alive 缓存失效
     * 关闭标签时将对应 path 的版本 +1，
     * 使得下次打开该页面时 key 变化，强制重新创建组件
     */
    keyMap: {}
  }),

  getters: {
    /** 获取指定路径的 keep-alive key */
    cacheKey: (state) => (path) => {
      return `${path}_v${state.keyMap[path] || 0}`
    }
  },

  actions: {
    /** 添加标签 - 已存在则更新 fullPath（保留最新的 query 参数,避免刷新后丢失） */
    addView(route) {
      const existing = this.visitedViews.find(v => v.path === route.path)
      if (existing) {
        // 更新 fullPath 为最新（如带 ?id=xxx 的详情/编辑页）
        existing.fullPath = route.fullPath || route.path
        return
      }
      this.visitedViews.push({
        path: route.path,
        fullPath: route.fullPath || route.path,
        title: route.meta?.title || route.name || route.path,
        name: route.name,
        affix: false
      })
    },

    /** 关闭指定标签，返回关闭后应跳转的路径 */
    removeView(path) {
      const idx = this.visitedViews.findIndex(v => v.path === path)
      if (idx < 0 || this.visitedViews[idx].affix) return null
      this.visitedViews.splice(idx, 1)
      // 版本号 +1，使该路径的缓存失效
      this.keyMap[path] = (this.keyMap[path] || 0) + 1
      // 返回紧邻的标签路径（优先右侧，否则左侧）
      const next = this.visitedViews[idx] || this.visitedViews[idx - 1]
      return next?.fullPath || next?.path || '/dashboard'
    },

    /** 关闭其他标签（保留固定和当前） */
    closeOthers(path) {
      const closed = this.visitedViews.filter(v => !v.affix && v.path !== path)
      closed.forEach(v => { this.keyMap[v.path] = (this.keyMap[v.path] || 0) + 1 })
      this.visitedViews = this.visitedViews.filter(v => v.affix || v.path === path)
    },

    /** 关闭当前右侧所有标签 */
    closeRight(path) {
      const idx = this.visitedViews.findIndex(v => v.path === path)
      if (idx < 0) return
      const closed = this.visitedViews.filter((v, i) => !v.affix && i > idx)
      closed.forEach(v => { this.keyMap[v.path] = (this.keyMap[v.path] || 0) + 1 })
      this.visitedViews = this.visitedViews.filter((v, i) => v.affix || i <= idx)
    },

    /** 关闭全部（保留固定标签） */
    closeAll() {
      const closed = this.visitedViews.filter(v => !v.affix)
      closed.forEach(v => { this.keyMap[v.path] = (this.keyMap[v.path] || 0) + 1 })
      this.visitedViews = this.visitedViews.filter(v => v.affix)
    }
  }
})
