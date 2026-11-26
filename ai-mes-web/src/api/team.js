import request from '@/utils/request'

export function getTeamList(params) {
  return request.get('/team/teams', { params })
}
export function createTeam(data) {
  return request.post('/team/teams', data)
}
export function updateTeam(id, data) {
  return request.put(`/team/teams/${id}`, data)
}
export function deleteTeam(id) {
  return request.delete(`/team/teams/${id}`)
}
export function getTeamMembers(teamId) {
  return request.get(`/team/teams/${teamId}/members`)
}
export function addTeamMembers(teamId, userIds) {
  return request.post(`/team/teams/${teamId}/members`, { userIds })
}
export function removeTeamMember(teamId, memberId) {
  return request.delete(`/team/teams/${teamId}/members/${memberId}`)
}
export function saveTeamWeights(teamId, weights) {
  return request.put(`/team/teams/${teamId}/members/weights`, weights)
}
export function equalizeWeights(teamId) {
  return request.put(`/team/teams/${teamId}/members/weights/equalize`)
}
