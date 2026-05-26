import { get, post } from '@/utils/request'

export const createTicketOrder = (data) => post('/ticket/create', data)
export const payTicketOrder = (data) => post('/ticket/pay', data)
export const cancelTicketOrder = (data) => post('/ticket/cancel', data)
export const refundTicketOrder = (data) => post('/ticket/refund', data)
export const getMyTicketOrders = () => get('/ticket/my-orders')
export const getTicketOrderDetail = (id) => get('/ticket/detail/' + id)
export const refreshQrCode = (data) => post('/ticket/refresh-qr', data)
export const getQrInfo = (qrCode) => get('/ticket/qr/' + qrCode)
