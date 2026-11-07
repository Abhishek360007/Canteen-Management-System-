// Tula's Canteen service worker: shows push notifications and opens the right page when one is tapped.
self.addEventListener('install', () => self.skipWaiting())
self.addEventListener('activate', (event) => event.waitUntil(self.clients.claim()))

self.addEventListener('push', (event) => {
  let data = {}
  try { data = event.data ? event.data.json() : {} } catch { data = { body: event.data && event.data.text() } }
  event.waitUntil(self.registration.showNotification(data.title || "Tula's Canteen", {
    body: data.body || '',
    icon: '/favicon.svg',
    badge: '/favicon.svg',
    tag: data.tag || 'tulas',
    renotify: true,
    vibrate: [200, 100, 200],
    data: { link: data.link || '/' },
  }))
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const url = new URL(event.notification.data?.link || '/', self.location.origin).href
  event.waitUntil((async () => {
    const all = await self.clients.matchAll({ type: 'window', includeUncontrolled: true })
    for (const c of all) {
      if (c.url.startsWith(self.location.origin)) { await c.focus(); return c.navigate(url) }
    }
    return self.clients.openWindow(url)
  })())
})
