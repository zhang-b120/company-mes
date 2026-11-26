import request from '@/utils/request'

/* ---------- V3 legacy (kept for backward compat) ---------- */
export function getDashboardSummary() {
  return request.get('/dashboard/summary')
}
export function getProductionTrend() {
  return request.get('/dashboard/production-trend')
}
export function getOrderStatus() {
  return request.get('/dashboard/order-status')
}
export function getRoleDashboard(role) {
  return request.get('/dashboard/role', { params: { role } })
}

/* ---------- V4.0 DB-ALL endpoints ---------- */

/**
 * Unified dashboard data endpoint.
 * @param {Object} params - { role, dateRange, startDate, endDate }
 */
export function getDashboardData(params) {
  return request.get('/dashboard/data', { params })
}

/**
 * Dashboard alert list (boss / admin).
 */
export function getDashboardAlerts() {
  return request.get('/dashboard/alerts')
}

/**
 * AI-generated daily briefing (boss / admin).
 */
export function getAiBriefing() {
  return request.get('/dashboard/ai-briefing')
}
