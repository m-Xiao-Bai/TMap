/**
 * 通用格式化工具
 */

export function formatTime(time) {
    if (!time) return ''
    if (typeof time === 'string' && time.includes('T')) {
        return time.replace('T', ' ').slice(0, 16)
    }
    return time
}

export function formatDate(time) {
    if (!time) return ''
    return formatTime(time).slice(0, 10)
}

export function formatDateTime(time) {
    if (!time) return ''
    return formatTime(time).slice(0, 16)
}

export function formatRelativeTime(time) {
    if (!time) return ''
    const date = typeof time === 'string' ? new Date(time.replace(' ', 'T')) : new Date(time)
    const diff = Date.now() - date.getTime()
    const minutes = Math.floor(diff / 60000)
    if (minutes < 1) return '刚刚'
    if (minutes < 60) return minutes + ' 分钟前'
    const hours = Math.floor(minutes / 60)
    if (hours < 24) return hours + ' 小时前'
    const days = Math.floor(hours / 24)
    if (days < 30) return days + ' 天前'
    return formatDate(time)
}

export function formatPrice(price) {
    if (price == null) return '0.00'
    return Number(price).toFixed(2)
}

export const ORDER_STATUS_MAP = {
    0: { text: '待支付', color: '#f59e0b', bg: 'rgba(245,158,11,0.12)' },
    1: { text: '已支付', color: '#1a73e8', bg: 'rgba(26,115,232,0.12)' },
    2: { text: '已使用', color: '#10b981', bg: 'rgba(16,185,129,0.12)' },
    3: { text: '已过期', color: '#94a3b8', bg: 'rgba(148,163,184,0.18)' },
    4: { text: '已退票', color: '#f43f5e', bg: 'rgba(244,63,94,0.12)' },
    5: { text: '退票审核中', color: '#f59e0b', bg: 'rgba(245,158,11,0.12)' }
}

export function getOrderStatus(status) {
    return ORDER_STATUS_MAP[status] || { text: '未知', color: '#909399', bg: '#f5f7fa' }
}

export const MESSAGE_TYPE_MAP = {
    ORDER_CREATED: { text: '下单', color: '#1a73e8', bg: '#e8f0fe' },
    ORDER_PAID: { text: '支付', color: '#1a73e8', bg: '#e8f0fe' },
    ORDER_USED: { text: '核销', color: '#10b981', bg: '#d1fae5' },
    ORDER_EXPIRED: { text: '过期', color: '#94a3b8', bg: '#f1f5f9' },
    ORDER_REFUNDED: { text: '退票', color: '#f43f5e', bg: '#fee2e2' },
    REFUND_PENDING: { text: '退票申请', color: '#f59e0b', bg: '#fef3c7' },
    REFUND_APPROVED: { text: '退票通过', color: '#10b981', bg: '#d1fae5' },
    REFUND_REJECTED: { text: '退票驳回', color: '#f43f5e', bg: '#fee2e2' },
    USER_CONTACT: { text: '我的来信', color: '#8b5cf6', bg: '#ede9fe' },
    SYSTEM_ERROR: { text: '系统', color: '#f43f5e', bg: '#fee2e2' }
}

export function getMessageType(type) {
    return MESSAGE_TYPE_MAP[type] || { text: type, color: '#909399', bg: '#f5f7fa' }
}

export function fullAvatarUrl(avatar) {
    if (!avatar) return ''
    if (avatar.startsWith('http')) return avatar
    return 'http://localhost:8888/transitMap' + (avatar.startsWith('/') ? avatar : '/' + avatar)
}
