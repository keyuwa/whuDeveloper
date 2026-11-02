export function formatTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', { hour12: false })
}

export function routeId(value: unknown): number | null {
  const raw = Array.isArray(value) ? value[0] : value
  if (typeof raw !== 'string' || raw.length === 0) return null
  const id = Number(raw)
  return Number.isInteger(id) && id > 0 ? id : null
}
