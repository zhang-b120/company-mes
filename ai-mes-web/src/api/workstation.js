import request from '@/utils/request'

/**
 * 工位机报工 API
 */

/** 获取工位报工任务列表（根据刷卡员工账号） */
export function getWorkstationTasks(username) {
  return request.get('/workreport/workstation/tasks', { params: { username } })
}

/** 获取报工上限 */
export function getReportLimit(workOrderId, taskId) {
  return request.get(`/production/work-orders/${workOrderId}/tasks/${taskId}/report-limit`)
}
