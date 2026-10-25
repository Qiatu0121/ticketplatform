import request from './request'

// 返回 List<Activity>（不含票种）
export const listActivities = () => request.get('/activity/list')

// 返回 { activity, tickets }，后端走 Redis 缓存
export const getActivityDetail = (id) => request.get(`/activity/${id}`)
