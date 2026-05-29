import request from '@/utils/request'

// 获取消息列表（支持分类过滤）
export const getMessageList = (params) => request.get('/manage/message/list', { params })

// 获取未读消息数
export const getMessageUnreadCount = () => request.get('/manage/message/unread-count')

// 获取各分类未读数
export const getMessageUnreadByCategory = () => request.get('/manage/message/unread-by-category')

// 标记单条消息已读
export const markMessageRead = (id) => request.put(`/manage/message/read/${id}`)

// 标记全部已读
export const markAllMessagesRead = () => request.put('/manage/message/read-all')

// 标记分类已读
export const markCategoryRead = (types) => request.put('/manage/message/read-category', { types })

// 删除消息
export const deleteMessage = (id) => request.delete(`/manage/message/${id}`)
