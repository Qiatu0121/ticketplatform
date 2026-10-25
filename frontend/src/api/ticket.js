import request from './request'

// 返回 { activityName, id, name, price, totalStock, stock }
export const getTicketDetail = (id) => request.get(`/ticket/${id}`)
