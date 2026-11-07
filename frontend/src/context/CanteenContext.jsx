import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import { api, setCanteenScope } from '../api'
import { useAuth } from './AuthContext'

const CanteenContext = createContext(null)
const KEY = 'tc_canteen'

const saved = () => { try { return Number(localStorage.getItem(KEY)) || null } catch { return null } }

/**
 * Which canteen the app is looking at.
 * - Students pick one (it's their pickup location); remembered on this device.
 * - Kitchen staff are fixed to their own canteen.
 * - Admins switch between canteens from the header.
 * The choice is added to API calls automatically (see setCanteenScope in api.js).
 */
export function CanteenProvider({ children }) {
  const { user } = useAuth()
  const [canteens, setCanteens] = useState([])
  const [selectedId, setSelectedId] = useState(saved)

  const load = useCallback(async () => {
    if (!user) return
    try {
      const list = await api(user.role === 'ADMIN' ? '/api/admin/canteens' : '/api/canteens')
      setCanteens(list)
      setSelectedId((cur) => {
        if (user.role === 'STAFF') return user.canteenId ?? list[0]?.id ?? null
        return list.some((c) => c.id === cur) ? cur : list[0]?.id ?? null
      })
    } catch { /* offline: keep what we have */ }
  }, [user])

  useEffect(() => { load() }, [load])

  // Set during render (not in an effect): pages remount on a canteen change and their effects run before
  // this provider's effects, so their first request must already see the new canteen.
  setCanteenScope(selectedId)
  useEffect(() => {
    try { if (selectedId) localStorage.setItem(KEY, String(selectedId)) } catch { /* ignore */ }
  }, [selectedId])

  const select = (id) => setSelectedId(Number(id))
  const selected = canteens.find((c) => c.id === selectedId) || null

  return (
    <CanteenContext.Provider value={{ canteens, selectedId, selected, select, reload: load, ready: !!selectedId }}>
      {children}
    </CanteenContext.Provider>
  )
}

export const useCanteens = () => useContext(CanteenContext)
