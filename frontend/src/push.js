import { api } from './api'

/** Browser push needs a service worker, the Push API and HTTPS (or localhost). */
export const pushSupported = () =>
  'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window && window.isSecureContext

const toBytes = (b64) => {
  const s = atob((b64 + '='.repeat((4 - (b64.length % 4)) % 4)).replace(/-/g, '+').replace(/_/g, '/'))
  return Uint8Array.from(s, (c) => c.charCodeAt(0))
}

export async function registerWorker() {
  if (!pushSupported()) return null
  try { return await navigator.serviceWorker.register('/push-sw.js') } catch { return null }
}

/** 'on' | 'off' | 'blocked' | 'unsupported' */
export async function pushState() {
  if (!pushSupported()) return 'unsupported'
  if (Notification.permission === 'denied') return 'blocked'
  const reg = await navigator.serviceWorker.getRegistration()
  const sub = reg && (await reg.pushManager.getSubscription())
  return sub && Notification.permission === 'granted' ? 'on' : 'off'
}

/** Ask permission, subscribe this browser and register it with the server. Returns the new state. */
export async function enablePush() {
  if (!pushSupported()) return 'unsupported'
  const permission = await Notification.requestPermission()
  if (permission !== 'granted') return permission === 'denied' ? 'blocked' : 'off'
  const reg = (await registerWorker()) || (await navigator.serviceWorker.ready)
  const { publicKey } = await api('/api/push/key')
  let sub = await reg.pushManager.getSubscription()
  if (!sub) sub = await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: toBytes(publicKey) })
  const json = sub.toJSON()
  await api('/api/push/subscribe', { method: 'POST', body: { endpoint: json.endpoint, keys: json.keys } })
  return 'on'
}

export async function disablePush() {
  const reg = await navigator.serviceWorker.getRegistration()
  const sub = reg && (await reg.pushManager.getSubscription())
  if (sub) {
    await api('/api/push/unsubscribe', { method: 'POST', body: { endpoint: sub.endpoint } }).catch(() => {})
    await sub.unsubscribe()
  }
  return 'off'
}
