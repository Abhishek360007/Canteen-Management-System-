import { useCallback, useEffect, useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import {
  UtensilsCrossed, ShoppingBag, Receipt, Wallet, User, ChefHat, ScanLine, Boxes, IndianRupee,
  LayoutDashboard, ListOrdered, Users, Menu as MenuIcon, Moon, Sun, Megaphone,
} from 'lucide-react'
import { api } from '../api'
import { useAuth } from '../context/AuthContext'
import { useCart } from '../context/CartContext'
import { useLive, useLiveStatus } from '../context/LiveContext'
import { useTheme } from '../context/ThemeContext'
import { Brand } from './Brand'

const NAV = {
  STUDENT: [
    { to: '/menu', label: 'Menu', icon: UtensilsCrossed },
    { to: '/cart', label: 'Cart', icon: ShoppingBag, badge: 'cart' },
    { to: '/orders', label: 'Orders', icon: Receipt },
    { to: '/wallet', label: 'Wallet', icon: Wallet },
    { to: '/account', label: 'Account', icon: User },
  ],
  STAFF: [
    { to: '/kitchen', label: 'Orders', icon: ChefHat, end: true },
    { to: '/kitchen/verify', label: 'Hand over', icon: ScanLine },
    { to: '/kitchen/stock', label: 'Stock', icon: Boxes },
    { to: '/kitchen/summary', label: 'Summary', icon: IndianRupee },
    { to: '/account', label: 'Account', icon: User },
  ],
  ADMIN: [
    { to: '/admin', label: 'Dashboard', icon: LayoutDashboard, end: true },
    { to: '/admin/orders', label: 'Orders', icon: ListOrdered },
    { to: '/admin/menu', label: 'Menu', icon: UtensilsCrossed },
    { to: '/admin/users', label: 'Users', icon: Users },
    { to: '/admin/more', label: 'More', icon: MenuIcon },
  ],
}

export function useCanteen() {
  const [canteen, setCanteen] = useState(null)
  const load = useCallback(() => api('/api/canteen').then(setCanteen).catch(() => {}), [])
  useEffect(() => { load() }, [load])
  useLive('canteen', load)
  return [canteen, load]
}

export function CanteenPill({ canteen }) {
  if (!canteen) return null
  const { status, openNow } = canteen
  const s = !openNow
    ? { t: 'Closed', c: 'bg-bad-soft text-bad', d: 'bg-bad' }
    : status === 'BUSY'
      ? { t: 'Busy', c: 'bg-warn-soft text-warn', d: 'bg-warn' }
      : { t: 'Open', c: 'bg-good-soft text-good', d: 'bg-good' }
  return (
    <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold ${s.c}`}>
      <span className={`size-1.5 rounded-full ${s.d}`} /> {s.t}
    </span>
  )
}

function AnnouncementBar() {
  const [items, setItems] = useState([])
  const load = useCallback(() => api('/api/announcements').then(setItems).catch(() => {}), [])
  useEffect(() => { load() }, [load])
  useLive('announcement', load)
  if (!items.length) return null
  return (
    <div className="bg-brand-soft text-brand-ink">
      <div className="mx-auto flex max-w-5xl items-start gap-2 px-4 py-2 text-sm">
        <Megaphone className="mt-0.5 size-4 shrink-0" />
        <p className="line-clamp-2">{items[0].message}</p>
      </div>
    </div>
  )
}

export default function Layout() {
  const { user } = useAuth()
  const cart = useCart()
  const { dark, toggle } = useTheme()
  const connected = useLiveStatus()
  const [canteen] = useCanteen()
  const nav = NAV[user.role] || NAV.STUDENT
  const wide = user.role !== 'STUDENT'

  return (
    <div className="min-h-dvh pb-24">
      <header className="sticky top-0 z-30 border-b border-line bg-bg/85 backdrop-blur">
        <div className={`mx-auto flex h-16 items-center justify-between gap-3 px-4 ${wide ? 'max-w-5xl' : 'max-w-2xl'}`}>
          <Brand sub={user.role === 'STAFF' ? 'Kitchen' : user.role === 'ADMIN' ? 'Admin' : null} />
          <div className="flex items-center gap-2">
            <CanteenPill canteen={canteen} />
            <span title={connected ? 'Live updates on' : 'Reconnecting…'}
              className={`size-2 rounded-full ${connected ? 'bg-good' : 'bg-muted'}`} />
            <button onClick={toggle} className="rounded-xl p-2 text-ink-2 hover:bg-card-2" aria-label="Toggle dark mode">
              {dark ? <Sun className="size-5" /> : <Moon className="size-5" />}
            </button>
          </div>
        </div>
      </header>
      {user.role === 'STUDENT' && <AnnouncementBar />}

      <main className={`mx-auto px-4 pt-5 ${wide ? 'max-w-5xl' : 'max-w-2xl'}`}>
        <Outlet context={{ canteen }} />
      </main>

      <nav className="pb-safe fixed inset-x-0 bottom-0 z-30 border-t border-line bg-card/95 backdrop-blur">
        <div className="mx-auto grid max-w-2xl grid-cols-5">
          {nav.map(({ to, label, icon: Icon, badge, end }) => (
            <NavLink key={to} to={to} end={end}
              className={({ isActive }) =>
                `relative flex flex-col items-center gap-0.5 py-2.5 text-[11px] font-semibold transition ${isActive ? 'text-brand' : 'text-muted hover:text-ink-2'}`}>
              <Icon className="size-[22px]" />
              {label}
              {badge === 'cart' && cart.count > 0 && (
                <span className="absolute right-[calc(50%-20px)] top-1.5 grid h-4.5 min-w-4.5 place-items-center rounded-full bg-brand px-1 text-[10px] font-bold text-white">
                  {cart.count}
                </span>
              )}
            </NavLink>
          ))}
        </div>
      </nav>
    </div>
  )
}
