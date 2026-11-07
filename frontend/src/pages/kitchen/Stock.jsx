import { useCallback, useEffect, useState } from 'react'
import { Infinity as InfinityIcon, Minus, Plus } from 'lucide-react'
import { api, rupees } from '../../api'
import { useLive } from '../../context/LiveContext'
import { DishArt } from '../../components/Brand'
import { Card, ErrorNote, PageTitle, Spinner, Toggle, VegDot, useToast } from '../../components/ui'

/** Quick stock control for the counter: plates left today and on/off switch per dish. */
export default function Stock() {
  const toast = useToast()
  const [menu, setMenu] = useState(null)
  const [error, setError] = useState(null)

  const load = useCallback(() => api('/api/menu').then(setMenu).catch((e) => setError(e.message)), [])
  useEffect(() => { load() }, [load])
  useLive('menu', load)

  const save = async (item, patch) => {
    const next = { stockToday: item.stockToday, available: item.available, ...patch }
    setMenu((list) => list.map((m) => (m.id === item.id ? { ...m, ...next, soldOut: next.stockToday != null && next.stockToday <= 0 } : m)))
    try {
      await api(`/api/kitchen/menu/${item.id}/stock`, { method: 'PUT', body: next })
    } catch (e) {
      toast(e.message, 'error')
      load()
    }
  }

  if (error) return <ErrorNote>{error}</ErrorNote>
  if (!menu) return <Spinner />

  return (
    <>
      <PageTitle title="Today's stock" sub="Plates left update by themselves as students order. Resets every morning at 5 AM." />
      <Card className="divide-y divide-line">
        {menu.map((item) => (
          <div key={item.id} className={`flex flex-wrap items-center gap-3 p-3 ${item.available ? '' : 'opacity-60'}`}>
            <DishArt item={item} className="size-12 rounded-xl [&>span]:text-2xl" />
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-1.5"><VegDot veg={item.veg} /><p className="truncate font-semibold">{item.name}</p></div>
              <p className="text-xs text-muted">{rupees(item.price)} · {item.soldOut ? <span className="font-semibold text-bad">Sold out</span> : item.stockToday == null ? 'No limit' : `${item.stockToday} left`}</p>
            </div>
            <div className="flex items-center gap-2">
              {item.stockToday == null ? (
                <button onClick={() => save(item, { stockToday: item.defaultStock ?? 20 })}
                  className="flex h-9 items-center gap-1 rounded-xl border border-line px-3 text-sm text-ink-2" title="Set a plate limit">
                  <InfinityIcon className="size-4" /> Unlimited
                </button>
              ) : (
                <div className="flex h-9 items-center rounded-xl border border-line">
                  <button onClick={() => save(item, { stockToday: Math.max(0, item.stockToday - 1) })} className="grid size-9 place-items-center" aria-label="One plate less"><Minus className="size-4" /></button>
                  <input type="number" min={0} max={9999} value={item.stockToday} aria-label={`Plates left of ${item.name}`}
                    onChange={(e) => save(item, { stockToday: Math.max(0, Math.min(9999, Number(e.target.value) || 0)) })}
                    className="w-12 bg-transparent text-center font-bold outline-none [appearance:textfield] [&::-webkit-inner-spin-button]:appearance-none" />
                  <button onClick={() => save(item, { stockToday: item.stockToday + 1 })} className="grid size-9 place-items-center" aria-label="One plate more"><Plus className="size-4" /></button>
                  <button onClick={() => save(item, { stockToday: null })} className="grid size-9 place-items-center border-l border-line text-muted" title="No limit" aria-label="Remove plate limit"><InfinityIcon className="size-4" /></button>
                </div>
              )}
              <Toggle checked={item.available} onChange={(v) => save(item, { available: v })} label={`${item.name} on today's menu`} />
            </div>
          </div>
        ))}
      </Card>
    </>
  )
}
