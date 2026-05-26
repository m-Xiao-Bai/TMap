import { get } from '@/utils/request'

export const getPublicConfigs = () => get('/utils/config/public')
export const getMetroLines = () => get('/utils/metro/lines')
export const getMetroStations = () => get('/utils/metro/stations')
export const getMetroStats = () => get('/utils/metro/stats')
export const getPublicCities = () => get('/utils/cities')
export const getPublicCountries = () => get('/utils/countries')
export const getLineOrderedStations = (lineId) => get('/utils/metro/lines/' + lineId + '/stations-ordered')
