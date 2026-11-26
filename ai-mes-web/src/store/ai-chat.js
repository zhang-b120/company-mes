import { defineStore } from 'pinia'
import { aiConversation, aiChat, clearChatSession } from '@/api/ai'

export const useAiChatStore = defineStore('aiChat', {
  state: () => ({
    visible: false,          // 浮窗显隐
    sessionId: null,         // 对话session
    messages: [],            // 消息列表: {role, content, intent, data, suggestions, time}
    pendingFillData: null,   // 待填入表单的数据
    loading: false,          // 加载状态
    currentPage: '',         // 当前页面路由
  }),
  actions: {
    toggle() {
      this.visible = !this.visible
    },
    show() {
      this.visible = true
    },
    hide() {
      this.visible = false
    },

    setCurrentPage(page) {
      this.currentPage = page
    },

    /**
     * 新版: 统一对话发送
     */
    async sendConversation(message) {
      if (this.loading || !message?.trim()) return
      this.loading = true

      // 添加用户消息
      this.messages.push({
        role: 'user',
        content: message,
        time: new Date().toLocaleTimeString()
      })

      try {
        const res = await aiConversation({
          sessionId: this.sessionId,
          message,
          currentPage: this.currentPage
        })
        const data = res.data

        this.sessionId = data.sessionId

        // 添加AI回复
        this.messages.push({
          role: 'assistant',
          content: data.reply,
          intent: data.intent,
          data: data.data,
          suggestions: data.suggestions,
          time: new Date().toLocaleTimeString()
        })

        return data
      } catch (e) {
        this.messages.push({
          role: 'assistant',
          content: '抱歉，AI服务暂时不可用，请稍后重试。',
          intent: 'chat',
          time: new Date().toLocaleTimeString()
        })
      } finally {
        this.loading = false
      }
    },

    /**
     * 旧版: 兼容AiChatBot浮窗的multipart发送
     */
    async sendMessage(message, module, files) {
      if (this.loading) return
      this.loading = true

      this.messages.push({
        role: 'user',
        content: message,
        files: files ? files.map(f => f.name) : [],
        time: new Date().toLocaleTimeString()
      })

      try {
        const formData = new FormData()
        const requestData = {
          sessionId: this.sessionId,
          module: module || 'general',
          message
        }
        formData.append('request', new Blob([JSON.stringify(requestData)], { type: 'application/json' }))

        if (files && files.length > 0) {
          files.forEach(file => {
            formData.append('files', file.raw || file)
          })
        }

        const res = await aiChat(formData)
        const data = res.data

        this.sessionId = data.sessionId

        this.messages.push({
          role: 'assistant',
          content: data.reply,
          intent: data.intent,
          data: data.data,
          suggestions: data.suggestions,
          time: new Date().toLocaleTimeString()
        })

        return data
      } catch (e) {
        this.messages.push({
          role: 'assistant',
          content: 'AI服务暂时不可用，请稍后重试',
          intent: 'chat',
          time: new Date().toLocaleTimeString()
        })
      } finally {
        this.loading = false
      }
    },

    setFillData(module, records) {
      this.pendingFillData = { module, records }
    },

    clearFillData() {
      this.pendingFillData = null
    },

    async clearSession() {
      if (this.sessionId) {
        try {
          await clearChatSession(this.sessionId)
        } catch { /* ignore */ }
      }
      this.sessionId = null
      this.messages = []
    }
  }
})
