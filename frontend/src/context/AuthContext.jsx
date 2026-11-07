import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import { api, tokenStore } from '../api'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(!!tokenStore.get())

  const refresh = useCallback(async () => {
    if (!tokenStore.get()) { setUser(null); setLoading(false); return }
    try {
      setUser(await api('/api/auth/me'))
    } catch {
      setUser(null)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { refresh() }, [refresh])

  useEffect(() => {
    const onLogout = () => setUser(null)
    window.addEventListener('tc-logout', onLogout)
    return () => window.removeEventListener('tc-logout', onLogout)
  }, [])

  const login = async (email, password) => {
    const res = await api('/api/auth/login', { method: 'POST', body: { email, password } })
    tokenStore.set(res.token)
    setUser(res.user)
    return res.user
  }

  const register = async (form) => {
    const res = await api('/api/auth/register', { method: 'POST', body: form })
    tokenStore.set(res.token)
    setUser(res.user)
    return res.user
  }

  const logout = () => {
    tokenStore.set(null)
    setUser(null)
  }

  return (
    <AuthContext.Provider value={{ user, setUser, loading, login, register, logout, refresh }}>
      {children}
    </AuthContext.Provider>
  )
}

export const useAuth = () => useContext(AuthContext)

export const homeFor = (role) => (role === 'ADMIN' ? '/admin' : role === 'STAFF' ? '/kitchen' : '/menu')
