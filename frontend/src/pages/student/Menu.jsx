import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useOutletContext } from 'react-router-dom'
import { Heart, Minus, Plus, Search, ShoppingBag, Star, ArrowRight, Clock } from 'lucide-react'
import { api, rupees } from '../../api'
import { useAuth } from '../../context/AuthContext'
import { useCart } from '../../context/CartContext'
import { useLive } from '../../context/LiveContext'
import { DishArt } from '../../components/Brand'
import { Empty, ErrorNote, Spinner, VegDot, useToast } from '../../components/ui'

const CATS = [
  ['ALL', 'All'], ['FAV', 'Favourites'], ['BREAKFAST', 'Breakfast'], ['MEALS', 'Meals'],
  ['SNACKS', 'Snacks'], ['BEVERAGES', 'Drinks'], ['DESSERTS', 'Desserts'],
]

const greeting = () => {
  const h = new Date().getHours()
  return h < 12 ? 'Good morning' : h < 17 ? 'Good afternoon' : 'Good evening'
}

export default function Menu() {
  const { user } = useAuth()
  const { canteen } = useOutletContext()
  const cart = useCart()
  const toast = useToast()
  const [menu, setMenu] = useState(null)
  const [error, setError] = useState(null)
  const [cat, setCat] = useState('ALL')
  const [q, setQ] = useState('')
  const [vegOnly, setVegOnly] = useState(false)

  const load = useCallback(() =>
    api('/api/menu').then((m) => { setMenu(m); cart.sync(m) }).catch((e) => setError(e.message)),
  // eslint-disable-next-line react-hooks/exhaustive-deps
  [])
  useEffect(() => { load() }, [load])
  useLive('menu', load)

  const shown = useMemo(() => {
    if (!menu) return []
    const needle = q.trim().toLowerCase()
    const order = CATS.map(([k]) => k)
    return [...menu].sort((a, b) => order.indexOf(a.category) - order.indexOf(b.category)).filter((m) =>
      m.available &&
      (cat === 'ALL' || (cat === 'FAV' ? m.favourite : m.category === cat)) &&
      (!vegOnly || m.veg) &&
      (!needle || m.name.toLowerCase().includes(needle) || (m.description || '').toLowerCase().includes(needle)))
  }, [menu, cat, q, vegOnly])

  const toggleFav = async (item) => {
    const on = !item.favourite
    setMenu((list) => list.map((m) => (m.id === item.id ? { ...m, favourite: on } : m)))
    try {
      await api(`/api/favourites/${item.id}`, { method: on ? 'PUT' : 'DELETE' })
    } catch (e) {
      toast(e.message, 'error')
      load()
    }
  }

  const closed = canteen && !canteen.openNow

  return (
    <>
      <div className="mb-4">
        <p className="text-sm text-muted">{greeting()}, {user.name.split(' ')[0]} 👋</p>
        <h1 className="text-2xl font-bold tracking-tight">What are you eating today?</h1>
      </div>

      {closed && (
        <div className="mb-4 flex items-start gap-3 rounded-2xl bg-bad-soft p-3 text-sm text-bad">
          <Clock className="mt-0.5 size-4 shrink-0" />
          <span>We're not taking orders right now. Opening hours: {canteen.openTime?.slice(0, 5)}–{canteen.closeTime?.slice(0, 5)}. You can still browse the menu.</span>
        </div>
      )}
      {canteen?.openNow && canteen.status === 'BUSY' && (
        <div className="mb-4 rounded-2xl bg-warn-soft p-3 text-sm text-warn">The kitchen is very busy. Orders may take a little longer than usual.</div>
      )}

      <div className="sticky top-16 z-20 -mx-4 bg-bg/95 px-4 pb-3 pt-1 backdrop-blur">
        <div className="relative">
          <Search className="pointer-events-none absolute left-3 top-1/2 size-4.5 -translate-y-1/2 text-muted" />
          <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search dosa, biryani, chai…" aria-label="Search the menu"
            className="h-11 w-full rounded-xl border border-line bg-card pl-10 pr-3 text-[15px] outline-none placeholder:text-muted focus:border-brand" />
        </div>
        <div className="no-scrollbar -mx-4 mt-3 flex gap-2 overflow-x-auto px-4">
          {CATS.map(([k, label]) => (
            <button key={k} onClick={() => setCat(k)}
              className={`shrink-0 rounded-full px-3.5 py-1.5 text-sm font-semibold transition ${cat === k ? 'bg-ink text-bg' : 'bg-card text-ink-2 border border-line'}`}>
              {label}
            </button>
          ))}
          <button onClick={() => setVegOnly(!vegOnly)} aria-pressed={vegOnly}
            className={`flex shrink-0 items-center gap-1.5 rounded-full border px-3.5 py-1.5 text-sm font-semibold ${vegOnly ? 'border-good bg-good-soft text-good' : 'border-line bg-card text-ink-2'}`}>
            <VegDot veg /> Veg only
          </button>
        </div>
      </div>

      <ErrorNote>{error}</ErrorNote>
      {!menu && !error && <Spinner label="Loading today's menu…" />}
      {menu && shown.length === 0 && (
        <Empty icon={Search} title={cat === 'FAV' ? 'No favourites yet' : 'Nothing matches'}>
          {cat === 'FAV' ? 'Tap the heart on a dish to save it here.' : 'Try another search or category.'}
        </Empty>
      )}

      <div className="grid gap-3 sm:grid-cols-2">
        {shown.map((item) => (
          <DishCard key={item.id} item={item} qty={cart.qtyOf(item.id)} disabled={closed}
            onAdd={() => {
              if (item.stockToday != null && cart.qtyOf(item.id) >= item.stockToday) {
                toast(`Only ${item.stockToday} left`, 'error')
                return
              }
              cart.add(item)
            }}
            onRemove={() => cart.remove(item.id)} onFav={() => toggleFav(item)} />
        ))}
      </div>

      {cart.count > 0 && (
        <Link to="/cart"
          className="animate-pop fixed inset-x-4 bottom-20 z-30 mx-auto flex max-w-2xl items-center justify-between rounded-2xl bg-brand px-4 py-3.5 font-semibold text-white shadow-lg">
          <span className="flex items-center gap-2"><ShoppingBag className="size-5" /> {cart.count} item{cart.count > 1 ? 's' : ''} · {rupees(cart.total)}</span>
          <span className="flex items-center gap-1">View cart <ArrowRight className="size-4" /></span>
        </Link>
      )}
    </>
  )
}

function DishCard({ item, qty, onAdd, onRemove, onFav, disabled }) {
  const low = item.stockToday != null && item.stockToday > 0 && item.stockToday <= 5
  return (
    <div className={`flex gap-3 rounded-2xl border border-line bg-card p-3 ${item.soldOut ? 'opacity-60' : ''}`}>
      <div className="relative shrink-0">
        <DishArt item={item} className="size-24 rounded-xl" />
        {item.soldOut && <span className="absolute inset-x-1 bottom-1 rounded-md bg-ink/80 py-0.5 text-center text-[11px] font-bold text-bg">SOLD OUT</span>}
      </div>
      <div className="flex min-w-0 flex-1 flex-col">
        <div className="flex items-start gap-2">
          <VegDot veg={item.veg} />
          <h3 className="flex-1 text-[15px] font-semibold leading-snug">{item.name}</h3>
          <button onClick={onFav} aria-label={item.favourite ? 'Remove from favourites' : 'Add to favourites'} className="-m-1 p-1">
            <Heart className={`size-4.5 ${item.favourite ? 'fill-bad text-bad' : 'text-muted'}`} />
          </button>
        </div>
        {item.description && <p className="mt-0.5 line-clamp-2 text-xs text-muted">{item.description}</p>}
        <div className="mt-1 flex flex-wrap items-center gap-x-2 text-xs text-ink-2">
          {item.rating && <span className="flex items-center gap-0.5"><Star className="size-3 fill-warn text-warn" />{item.rating} ({item.ratingCount})</span>}
          <span>~{item.prepMinutes} min</span>
          {low && <span className="font-semibold text-bad">Only {item.stockToday} left</span>}
        </div>
        <div className="mt-auto flex items-center justify-between pt-2">
          <span className="font-display text-lg font-bold">{rupees(item.price)}</span>
          {item.soldOut ? null : qty === 0 ? (
            <button onClick={onAdd} disabled={disabled}
              className="h-9 rounded-xl border-2 border-brand px-5 text-sm font-bold text-brand transition hover:bg-brand-soft disabled:opacity-40">
              ADD
            </button>
          ) : (
            <div className="flex h-9 items-center rounded-xl bg-brand text-white">
              <button onClick={onRemove} className="grid h-9 w-9 place-items-center" aria-label={`One less ${item.name}`}><Minus className="size-4" /></button>
              <span className="w-6 text-center font-bold" aria-live="polite">{qty}</span>
              <button onClick={onAdd} className="grid h-9 w-9 place-items-center" aria-label={`One more ${item.name}`}><Plus className="size-4" /></button>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
