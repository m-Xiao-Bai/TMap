/**
 * 路线查询：BFS 找最短路径
 */
export function bfsShortestPath(allStations, startId, endId) {
    if (!startId || !endId || startId === endId) return null
    const adj = {}
    for (const s of allStations) {
        const sid = String(s.id)
        if (!adj[sid]) adj[sid] = []
        try {
            for (const p of JSON.parse(s.prevStationIds || '[]')) {
                const pid = String(p)
                if (!adj[sid].includes(pid)) adj[sid].push(pid)
            }
            for (const n of JSON.parse(s.nextStationIds || '[]')) {
                const nid = String(n)
                if (!adj[sid].includes(nid)) adj[sid].push(nid)
            }
        } catch {}
    }
    const queue = [[String(startId)]]
    const visited = new Set([String(startId)])
    while (queue.length) {
        const path = queue.shift()
        const cur = path[path.length - 1]
        if (cur === String(endId)) return path
        for (const nb of (adj[cur] || [])) {
            if (!visited.has(nb)) {
                visited.add(nb)
                queue.push([...path, nb])
            }
        }
    }
    return null
}

/**
 * 给定线路下的有序站点（按线路相邻关系排序）
 */
export function orderStationsByAdjacency(lineStations, lineId) {
    if (!lineStations || lineStations.length <= 1) return [...(lineStations || [])]
    const graph = new Map()
    for (const s of lineStations) {
        const sid = String(s.id)
        if (!graph.has(sid)) graph.set(sid, { station: s, neighbors: new Set() })
        let lineIds = []
        try { lineIds = JSON.parse(s.lineIds || '[]') } catch {}
        const idx = lineIds.findIndex(id => String(id) === String(lineId))
        if (idx === -1) continue
        let prevIds = [], nextIds = []
        try { prevIds = JSON.parse(s.prevStationIds || '[]') } catch {}
        try { nextIds = JSON.parse(s.nextStationIds || '[]') } catch {}
        const prevId = prevIds[idx] != null ? String(prevIds[idx]) : null
        const nextId = nextIds[idx] != null ? String(nextIds[idx]) : null
        if (prevId && lineStations.some(x => String(x.id) === prevId)) {
            graph.get(sid).neighbors.add(prevId)
            if (!graph.has(prevId)) graph.set(prevId, { station: lineStations.find(x => String(x.id) === prevId), neighbors: new Set() })
            graph.get(prevId).neighbors.add(sid)
        }
        if (nextId && lineStations.some(x => String(x.id) === nextId)) {
            graph.get(sid).neighbors.add(nextId)
            if (!graph.has(nextId)) graph.set(nextId, { station: lineStations.find(x => String(x.id) === nextId), neighbors: new Set() })
            graph.get(nextId).neighbors.add(sid)
        }
    }
    const endpoints = []
    for (const [sid, node] of graph) { if (node.neighbors.size === 1) endpoints.push(sid) }
    const startId = endpoints.length > 0 ? endpoints[0] : graph.keys().next().value
    const visited = new Set()
    const ordered = []
    let current = startId
    while (current && !visited.has(current)) {
        visited.add(current)
        const node = graph.get(current)
        if (node?.station) ordered.push(node.station)
        let next = null
        for (const n of node.neighbors) { if (!visited.has(n)) { next = n; break } }
        current = next
    }
    return ordered
}
