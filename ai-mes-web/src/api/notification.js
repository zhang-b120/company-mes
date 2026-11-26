import request from '@/utils/request'

/** 通知消息分页列表 */
export function getNotificationPage(params) {
  return request.get('/system/notifications/page', { params })
}

/** 未读消息数量 */
export function getUnreadCount() {
  return request.get('/system/notifications/unread-count')
}

/** 标记单条已读 */
export function markRead(id) {
  return request.put(`/system/notifications/${id}/read`)
}

/** 标记全部已读 */
export function markAllRead() {
  return request.put('/system/notifications/read-all')
}
