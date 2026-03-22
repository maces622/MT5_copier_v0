export function formatDateTime(value?: string | null) {
  if (!value) {
    return 'n/a'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).format(date)
}

export function formatNumber(value?: number | null, digits = 2) {
  if (value === null || value === undefined) {
    return 'n/a'
  }
  return new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: 0,
    maximumFractionDigits: digits,
  }).format(value)
}

export function formatBoolean(value?: boolean | null) {
  if (value === null || value === undefined) {
    return 'n/a'
  }
  return value ? 'Yes' : 'No'
}

export function statusTone(value?: string | null) {
  const normalized = value?.toUpperCase() ?? ''
  if (normalized.includes('ACTIVE') || normalized.includes('ONLINE') || normalized.includes('ACK') || normalized.includes('CONNECTED')) {
    return 'good'
  }
  if (normalized.includes('PENDING') || normalized.includes('PAUSED') || normalized.includes('STALE')) {
    return 'warn'
  }
  if (normalized.includes('FAIL') || normalized.includes('REJECT') || normalized.includes('DISCONNECTED')) {
    return 'bad'
  }
  return 'neutral'
}
