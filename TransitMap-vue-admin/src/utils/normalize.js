export function ensureString(val) {
  if (val == null) return val
  return String(val)
}

export function normalizeList(list, idFields = ['id']) {
  if (!Array.isArray(list)) return list
  return list.map(item => {
    const copy = { ...item }
    idFields.forEach(f => {
      if (copy[f] != null) copy[f] = String(copy[f])
    })
    return copy
  })
}
