import request from '@/utils/request'

export const getMessageList = (params) => request.get('/manage/message/list', { params })
export const getMessageUnreadCount = () => request.get('/manage/message/unread-count')
export const markMessageRead = (id) => request.put(`/manage/message/read/${id}`)
export const markAllMessagesRead = () => request.put('/manage/message/read-all')
export const deleteMessage = (id) => request.delete(`/manage/message/${id}`)
