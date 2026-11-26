import request from '@/utils/request'

// ==================== S01: 智能接单 ====================
export function smartOrderIntake(orderText) {
  return request.post('/ai/smart-order-intake', { orderText })
}
export function aiParseOrder(content, files) {
  const formData = new FormData()
  if (content) formData.append('content', content)
  if (files && files.length > 0) {
    files.forEach(f => formData.append('files', f))
  }
  return request.post('/ai/parse-order', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// ==================== S02: AI建档 ====================
export function aiProductFiling(description) {
  return request.post('/ai/product-filing', { description })
}

// ==================== S03: AI排产 ====================
export function aiScheduling(workOrderInfo) {
  return request.post('/ai/scheduling', { workOrderInfo })
}

// ==================== S04: 每日简报（数据驱动，无需传参） ====================
export function dailyBriefing() {
  return request.post('/ai/daily-briefing')
}

// ==================== S05: 变更评估 ====================
export function changeAssessment(changeInfo) {
  return request.post('/ai/change-assessment', { changeInfo })
}

// ==================== S06: 采购建议（数据驱动，无需传参） ====================
export function purchaseSuggestion() {
  return request.post('/ai/purchase-suggestion')
}

// ==================== S07: 异常分析 ====================
export function exceptionAnalysis(exceptionInfo) {
  return request.post('/ai/exception-analysis', { exceptionInfo })
}

// ==================== S08: 质量趋势（数据驱动，无需传参） ====================
export function qualityTrend() {
  return request.post('/ai/quality-trend')
}

// ==================== S09: 生产效率（数据驱动，无需传参） ====================
export function productionEfficiency() {
  return request.post('/ai/production-efficiency')
}

// ==================== S10: 交期风险（数据驱动，无需传参） ====================
export function deliveryRisk() {
  return request.post('/ai/delivery-risk')
}

// ==================== S11: 成本分析（数据驱动，无需传参） ====================
export function costAnalysis() {
  return request.post('/ai/cost-analysis')
}

// ==================== 统一对话接口（新版） ====================
export function aiConversation(data) {
  return request.post('/ai/conversation', data, { timeout: 90000 })
}

// ==================== S12: AI聊天机器人（旧版兼容） ====================
export function aiChat(formData) {
  return request.post('/ai/chat', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 90000
  })
}
export function getChatHistory(sessionId) {
  return request.get(`/ai/chat/sessions/${sessionId}`)
}
export function clearChatSession(sessionId) {
  return request.delete(`/ai/chat/sessions/${sessionId}`)
}
