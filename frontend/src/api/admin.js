import request from './request'

// 发布活动，body 为 Activity 字段
export const createActivity = (data) => request.post('/admin/activity', data)

// 为活动添加票种，后端会把库存同步到 Redis
export const addTicket = (activityId, data) =>
  request.post(`/admin/activity/${activityId}/ticket`, data)

// status 传 OrderStatus 枚举名（PAID / VERIFIED ...），空字符串表示全部
export const listOrders = (status) =>
  request.get('/admin/orders', { params: { status } })
