import request from './request'

// 同步创建订单，保留作为回退入口
export const grabTicket = (ticketId, count = 1) =>
  request.post('/order/create', null, { params: { ticketId, count } })

// 异步创建订单：先预留库存，消费者再创建订单
export const submitAsyncOrder = (ticketId, count, requestId) =>
  request.post('/order/async-create', null, { params: { requestId, ticketId, count } })

// 轮询请求状态；轮询失败使用 silent，避免反复弹错误提示
export const getOrderRequest = (requestId) =>
  request.get(`/order/request/${requestId}`, { silent: true })

export const payOrder = (orderId) =>
  request.post('/order/pay', null, { params: { orderId } })

export const verifyOrder = (orderId) =>
  request.post('/order/verify', null, { params: { orderId } })

export const myOrders = () => request.get('/order/my')
