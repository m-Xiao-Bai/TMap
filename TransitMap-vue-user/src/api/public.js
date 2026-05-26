import request from '@/utils/request'

/** 获取公开系统配置（首页内容等） */
export const getPublicConfigs = () => request.get('/utils/config/public')

/** 获取所有运营中的地铁线路 */
export const getMetroLines = () => request.get('/utils/metro/lines')

/** 获取所有运营中的地铁站点 */
export const getMetroStations = () => request.get('/utils/metro/stations')

/** 获取地铁统计概览 */
export const getMetroStats = () => request.get('/utils/metro/stats')

/** 获取有运营线路的城市列表 */
export const getPublicCities = () => request.get('/utils/cities')

/** 获取有运营线路的国家列表 */
export const getPublicCountries = () => request.get('/utils/countries')

/** 获取指定线路的有序站点列表 */
export const getLineOrderedStations = (lineId) => request.get(`/utils/metro/lines/${lineId}/stations-ordered`)
