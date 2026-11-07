import { createContext, useContext, useEffect, useRef, useState } from 'react'
import { API_BASE, tokenStore } from '../api'
import { useAuth } from './AuthContext'

const LiveContext = createContext(null)
const EVENTS = ['order', 'menu', 'wallet', 'canteen', 'announcement', 'topup', 'points']

/**
 * One Server-Sent Events connection per logged-in tab. Screens subscribe with useLive('order', fn)
 * and re-fetch when something changes, so nothing has to poll.
 */
export function LiveProvider({ children }) {
  const { user } = useAuth()
  const listeners = useRef(new Map())
  const [connected, setConnected] = useState(false)

  useEffect(() => {
    if (!user) return
    const token = tokenStore.get()
    if (!token) return
    let es
    let retry
    let closed = false

    const open = () => {
      es = new EventSource(`${API_BASE}/api/live?token=${encodeURIComponent(token)}`)
      es.onopen = () => setConnected(true)
      es.onerror = () => {
        setConnected(false)
        if (es.readyState === EventSource.CLOSED && !closed) {
          retry = setTimeout(open, 5000)
        }
      }
      EVENTS.forEach((name) =>
        es.addEventListener(name, (e) => {
          let data = null
          try { data = JSON.parse(e.data) } catch { /* keep null */ }
          listeners.current.get(name)?.forEach((fn) => fn(data))
        }),
      )
    }
    open()
    return () => {
      closed = true
      clearTimeout(retry)
      es?.close()
      setConnected(false)
    }
  }, [user])

  const subscribe = (name, fn) => {
    if (!listeners.current.has(name)) listeners.current.set(name, new Set())
    listeners.current.get(name).add(fn)
    return () => listeners.current.get(name)?.delete(fn)
  }

  return <LiveContext.Provider value={{ subscribe, connected }}>{children}</LiveContext.Provider>
}

export function useLive(name, handler) {
  const ctx = useContext(LiveContext)
  const ref = useRef(handler)
  ref.current = handler
  useEffect(() => ctx?.subscribe(name, (d) => ref.current(d)), [ctx, name])
}

export const useLiveStatus = () => useContext(LiveContext)?.connected
