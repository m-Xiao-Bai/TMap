import request from '@/utils/request'

export const createTicketOrder = (data) => request.post('/ticket/create', data)
export const payTicketOrder = (data) => request.post('/ticket/pay', data)
export const refundTicketOrder = (data) => request.post('/ticket/refund', data)
export const getMyTicketOrders = () => request.get('/ticket/my-orders')
export const refreshQrCode = (data) => request.post('/ticket/refresh-qr', data)
export const getQrInfo = (qrCode) => request.get(`/ticket/qr/${qrCode}`)
export const verifyQrCode = (data) => request.post('/ticket/verify', data)
