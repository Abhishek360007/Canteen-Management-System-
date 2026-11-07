export const API_BASE = import.meta.env.VITE_API_URL || ''

const TOKEN_KEY = 'tc_token'

export const tokenStore = {
  get() {
    try { return localStorage.getItem(TOKEN_KEY) } catch { return null }
  },
  set(t) {
    try { t ? localStorage.setItem(TOKEN_KEY, t) : localStorage.removeItem(TOKEN_KEY) } catch { /* private mode */ }
  },
}

export class ApiError extends Error {
  constructor(status, message, data) {
    super(message)
    this.status = status
    this.data = data
  }
}

// The canteen the user is looking at; added as ?canteenId= to per-canteen endpoints (menu, kitchen, insights…).
let canteenScope = null
export const setCanteenScope = (id) => { canteenScope = id }
const SCOPED = /^\/api\/(menu|canteen(\?|$)|recommendations(\?|$)|kitchen\/|admin\/insights|admin\/menu(\?|$|\/import))/

function scoped(path) {
  if (!canteenScope || !SCOPED.test(path) || /[?&]canteenId=/.test(path)) return path
  return path + (path.includes('?') ? '&' : '?') + 'canteenId=' + canteenScope
}

/** fetch wrapper: adds the token, parses JSON, turns error bodies into ApiError with a readable message. */
export async function api(path, { method = 'GET', body, headers = {}, raw = false } = {}) {
  path = scoped(path)
  const token = tokenStore.get()
  let res
  try {
    res = await fetch(API_BASE + path, {
      method,
      headers: {
        ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...headers,
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    })
  } catch {
    throw new ApiError(0, "Can't reach Tula's Canteen. Check your internet and try again.")
  }

  if (res.status === 401 && token && !path.startsWith('/api/auth/login')) {
    tokenStore.set(null)
    window.dispatchEvent(new Event('tc-logout'))
  }
  if (raw && res.ok) return res

  const text = await res.text()
  let data = null
  if (text) {
    try { data = JSON.parse(text) } catch { data = text }
  }
  if (!res.ok) {
    throw new ApiError(res.status, (data && data.message) || `Request failed (${res.status})`, data)
  }
  return data
}

/** Download a file from an authenticated endpoint. */
export async function download(path, fallbackName) {
  const res = await api(path, { raw: true })
  const cd = res.headers.get('Content-Disposition') || ''
  const name = /filename="?([^"]+)"?/.exec(cd)?.[1] || fallbackName
  const url = URL.createObjectURL(await res.blob())
  const a = Object.assign(document.createElement('a'), { href: url, download: name })
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

/** ₹60, ₹1,250, ₹10.50 (two decimals only when there are paise). */
export const rupees = (n) => {
  const v = Number(n ?? 0)
  const paise = Math.round(v * 100) % 100 !== 0
  return '₹' + v.toLocaleString('en-IN', { minimumFractionDigits: paise ? 2 : 0, maximumFractionDigits: 2 })
}

export const timeOf = (iso) =>
  iso ? new Date(iso).toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit' }) : ''

export const dateOf = (iso) =>
  iso ? new Date(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' }) : ''

export const slotLabel = (hhmm) => {
  if (!hhmm) return 'As soon as possible'
  const [h, m] = hhmm.split(':').map(Number)
  const d = new Date()
  d.setHours(h, m, 0, 0)
  return d.toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit' })
}

export function minutesAgo(iso) {
  const m = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 60000))
  if (m < 1) return 'just now'
  if (m < 60) return `${m} min ago`
  return `${Math.floor(m / 60)}h ${m % 60}m ago`
}

export const newKey = () =>
  (crypto.randomUUID ? crypto.randomUUID() : Date.now().toString(36) + Math.random().toString(36).slice(2))
