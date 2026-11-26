import { defineStore } from 'pinia'

export const useAppStore = defineStore('app', {
  state: () => ({
    sidebar: {
      collapsed: false
    },
    cachedViews: []
  }),
  actions: {
    toggleSidebar() {
      this.sidebar.collapsed = !this.sidebar.collapsed
    },
    addCachedView(name) {
      if (!this.cachedViews.includes(name)) {
        this.cachedViews.push(name)
      }
    },
    removeCachedView(name) {
      const index = this.cachedViews.indexOf(name)
      if (index > -1) {
        this.cachedViews.splice(index, 1)
      }
    }
  }
})
