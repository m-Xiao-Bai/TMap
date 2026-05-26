import request from '@/utils/request'

export const getMessageList = (params) => request.get('/message/list', { params })
export const getMessageUnreadCount = () => request.get('/message/unread-count')
export const markMessageRead = (id) => request.put(`/message/read/${id}`)
export const markAllMessagesRead = () => request.put('/message/read-all')
export const sendContactMessage = (content) => request.post('/message/contact', { content })
