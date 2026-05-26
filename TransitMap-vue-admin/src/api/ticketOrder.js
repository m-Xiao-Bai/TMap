import request from '@/utils/request'

export const getTicketOrderList = (params) => request.get('/manage/ticket-order/list', { params })
export const getTicketOrderDetail = (id) => request.get(`/manage/ticket-order/${id}`)
export const getTicketOrderStats = () => request.get('/manage/ticket-order/stats')
export const updateTicketOrder = (id, data) => request.put(`/manage/ticket-order/${id}`, data)
export const deleteTicketOrder = (id) => request.delete(`/manage/ticket-order/${id}`)
export const approveRefund = (id, action) => request.post(`/manage/ticket-order/${id}/refund-approve`, { action })
