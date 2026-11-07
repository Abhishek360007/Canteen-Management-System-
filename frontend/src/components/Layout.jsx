import { useCallback, useEffect, useState } from 'react'
import { Link, NavLink, Outlet } from 'react-router-dom'
import {
  UtensilsCrossed, ShoppingBag, Receipt, Wallet, User, ChefHat, ScanLine, Boxes, IndianRupee,
  LayoutDashboard, ListOrdered, Users, Menu as MenuIcon, Moon, Sun, Megaphone, Bell,
} from 'lucide-react'
import { api } from '../api'
import { useAuth } from '../context/AuthContext'
import { useCart } from '../context/CartContext'
import { useLive, useLiveStatus } from '../context/LiveContext'
import { useTheme } from '../context/ThemeContext'
import { useCanteens } from '../context/CanteenContext'
import { Brand } from './Brand'
import { Spinner } from './ui'

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
  const { selectedId } = useCanteens()
  const [canteen, setCanteen] = useState(null)
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const load = useCallback(() => api('/api/canteen').then(setCanteen).catch(() => {}), [selectedId])
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

/** Bell with unread count; live-updated, cleared when the notifications page is opened. */
function NotificationBell() {
  const [unread, setUnread] = useState(0)
  const load = useCallback(() => api('/api/notifications/unread').then((r) => setUnread(r.unread)).catch(() => {}), [])
  useEffect(() => {
    load()
    window.addEventListener('tc-unread', load)
    return () => window.removeEventListener('tc-unread', load)
  }, [load])
  useLive('notification', load)
  return (
    <Link to="/notifications" className="relative rounded-xl p-2 text-ink-2 hover:bg-card-2" aria-label={`Notifications${unread ? `, ${unread} unread` : ''}`}>
      <Bell className="size-5" />
      {unread > 0 && (
        <span className="absolute right-0.5 top-0.5 grid h-4.5 min-w-4.5 place-items-center rounded-full bg-brand px-1 text-[10px] font-bold text-white">
          {unread > 9 ? '9+' : unread}
        </span>
      )}
    </Link>
  )
}

export default function Layout() {
  const { user } = useAuth()
  const cart = useCart()
  const { dark, toggle } = useTheme()
  const connected = useLiveStatus()
  const [canteen] = useCanteen()
  const { canteens, selectedId, select } = useCanteens()
  const nav = NAV[user.role] || NAV.STUDENT
  const wide = user.role !== 'STUDENT'

  return (
    <div className="min-h-dvh pb-24">
      <header className="sticky top-0 z-30 border-b border-line bg-bg/85 backdrop-blur">
        <div className={`mx-auto flex h-16 items-center justify-between gap-3 px-4 ${wide ? 'max-w-5xl' : 'max-w-2xl'}`}>
          <Brand sub={user.role === 'STAFF' ? `Kitchen${canteen ? ' · ' + canteen.name : ''}` : user.role === 'ADMIN' ? 'Admin' : null} />
          <div className="flex items-center gap-2">
            <CanteenPill canteen={canteen} />
            <span title={connected ? 'Live updates on' : 'Reconnecting…'}
              className={`size-2 rounded-full ${connected ? 'bg-good' : 'bg-muted'}`} />
            <NotificationBell />
            <button onClick={toggle} className="rounded-xl p-2 text-ink-2 hover:bg-card-2" aria-label="Toggle dark mode">
              {dark ? <Sun className="size-5" /> : <Moon className="size-5" />}
            </button>
          </div>
        </div>
      </header>
      {user.role === 'STUDENT' && <AnnouncementBar />}
      {user.role === 'ADMIN' && canteens.length > 1 && (
        <div className="border-b border-line bg-card-2">
          <label className={`mx-auto flex items-center gap-2 px-4 py-1.5 text-sm ${wide ? 'max-w-5xl' : 'max-w-2xl'}`}>
            <span className="text-muted">Canteen:</span>
            <select value={selectedId ?? ''} onChange={(e) => select(e.target.value)}
              className="rounded-lg border border-line bg-card px-2 py-1 font-semibold text-ink outline-none focus:border-brand">
              {canteens.map((c) => <option key={c.id} value={c.id}>{c.name}{c.active ? '' : ' (hidden)'}</option>)}
            </select>
            <span className="hidden text-xs text-muted sm:inline">Menu, kitchen, stock and forecasts follow this choice.</span>
          </label>
        </div>
      )}

      <main className={`mx-auto px-4 pt-5 ${wide ? 'max-w-5xl' : 'max-w-2xl'}`}>
        {/* Remount pages when the canteen changes, so every screen reloads for the new canteen. */}
        {canteens.length ? <Outlet key={selectedId ?? 'none'} context={{ canteen }} /> : <Spinner />}
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
