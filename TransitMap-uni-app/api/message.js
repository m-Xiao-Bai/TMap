import { get, post, put } from '@/utils/request'

export const getMessageList = (params) => get('/message/list', params)
export const getMessageUnreadCount = () => get('/message/unread-count')
export const markMessageRead = (id) => put('/message/read/' + id)
export const markAllMessagesRead = () => put('/message/read-all')
export const sendContactMessage = (content) => post('/message/contact', { content })
