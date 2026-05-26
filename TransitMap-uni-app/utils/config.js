/**
 * 系统配置：从后端 /utils/config/public 拉取，缓存到内存
 * 默认值兜底，避免管理端忘记设置导致前端崩溃。
 */
import { getPublicConfigs } from '@/api/public'

const DEFAULT_CONFIG = {
    'ticket.price_tiers': '3,2;6,3;9,4;12,5;18,6;999,7',
    'ticket.qr_validity_minutes': '15',
    'ticket.payment_timeout_hours': '2',
    'ticket.estimated_minutes_per_stop': '3',
    'ticket.estimated_km_per_stop': '1.8',
    // 地图响应式尺寸：N=高倍率，M=宽倍率（占屏幕的比例）
    'map.height_ratio': '0.50',
    'map.width_ratio': '1.00',
    // 高德 key（mp-weixin 原生 <map> 组件本身只能用腾讯地图，此 key 暂留给 webview/SDK 服务调用）
    'map.amap_key': '',
    // 全屏地图 HTML URL（后端服务的 metro-map.html，需在管理端系统配置中设置实际地址）
    'map.fullscreen_url': ''
}

let _config = null
let _loadingPromise = null

export async function loadConfig() {
    if (_config) return _config
    if (_loadingPromise) return _loadingPromise
    _loadingPromise = (async () => {
        try {
            const res = await getPublicConfigs()
            if (res.code === 200) {
                const map = {}
                const list = res.data || []
                for (const item of list) {
                    map[item.configKey] = item.configValue
                }
                _config = { ...DEFAULT_CONFIG, ...map }
            } else {
                _config = { ...DEFAULT_CONFIG }
            }
        } catch {
            _config = { ...DEFAULT_CONFIG }
        }
        return _config
    })()
    return _loadingPromise
}

export function getConfig(key, fallback) {
    if (!_config) return fallback != null ? fallback : DEFAULT_CONFIG[key]
    return _config[key] != null ? _config[key] : (fallback != null ? fallback : DEFAULT_CONFIG[key])
}

export function getPriceTiers() {
    const raw = getConfig('ticket.price_tiers', DEFAULT_CONFIG['ticket.price_tiers'])
    return raw.split(';').map(seg => {
        const [m, p] = seg.split(',').map(Number)
        return { m, p }
    })
}

export function calcPrice(stops) {
    const tiers = getPriceTiers()
    for (const t of tiers) {
        if (stops <= t.m) return t.p
    }
    return tiers[tiers.length - 1].p
}

export function calcMinutes(stops) {
    const per = Number(getConfig('ticket.estimated_minutes_per_stop', 3))
    return Math.max(2, stops * per)
}

export function calcKm(stops) {
    const per = Number(getConfig('ticket.estimated_km_per_stop', 1.8))
    return (stops * per).toFixed(1)
}

export function getQrValidityMinutes() {
    return Number(getConfig('ticket.qr_validity_minutes', 15))
}

export function getPaymentTimeoutHours() {
    return Number(getConfig('ticket.payment_timeout_hours', 2))
}

/**
 * 计算地图实际像素尺寸：根据手机窗口尺寸 + 配置中的倍率
 */
export function getMapDimensions() {
    let windowWidth = 375
    let windowHeight = 667
    try {
        const sys = uni.getSystemInfoSync()
        windowWidth = sys.windowWidth || 375
        windowHeight = sys.windowHeight || 667
    } catch {}
    const heightRatio = Number(getConfig('map.height_ratio', '0.40')) || 0.40
    const widthRatio = Number(getConfig('map.width_ratio', '1.00')) || 1.00
    // 倍率限制在合理区间，避免异常配置
    const safeH = Math.max(0.2, Math.min(0.8, heightRatio))
    const safeW = Math.max(0.5, Math.min(1.0, widthRatio))
    return {
        width: Math.floor(windowWidth * safeW),
        height: Math.floor(windowHeight * safeH),
        windowWidth,
        windowHeight,
        heightRatio: safeH,
        widthRatio: safeW
    }
}

export function getAmapKey() {
    return getConfig('map.amap_key', '')
}

export function getFullscreenMapUrl() {
    return getConfig('map.fullscreen_url', '')
}

