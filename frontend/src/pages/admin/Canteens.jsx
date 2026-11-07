import { useCallback, useEffect, useState } from 'react'
import { MapPin, Pencil, Plus, Store } from 'lucide-react'
import { api, rupees } from '../../api'
import { useCanteens } from '../../context/CanteenContext'
import { useLive } from '../../context/LiveContext'
import { Button, Card, ErrorNote, Input, Modal, PageTitle, Spinner, Toggle, useToast } from '../../components/ui'
import { RankBars } from '../../components/Charts'

const EMPTY = { name: '', code: '', location: '', openTime: '08:00', closeTime: '18:00', maxActiveOrders: 3, maxQtyPerItem: 6, noShowMinutes: 45, cashAllowed: true, active: true }

/** Admin: every canteen on campus, how they compare, and their hours and rules. */
export default function Canteens() {
  const toast = useToast()
  const { reload } = useCanteens()
  const [list, setList] = useState(null)
  const [stats, setStats] = useState([])
  const [edit, setEdit] = useState(null)
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  const load = useCallback(() => {
    api('/api/admin/canteens').then(setList).catch((e) => setError(e.message))
    api('/api/admin/canteens/compare').then(setStats).catch(() => {})
  }, [])
  useEffect(() => { load() }, [load])
  useLive('canteen', load)
  useLive('order', () => api('/api/admin/canteens/compare').then(setStats).catch(() => {}))

  const open = (c) => {
    setError(null)
    setEdit(c ? { ...c, location: c.location || '', openTime: c.openTime.slice(0, 5), closeTime: c.closeTime.slice(0, 5) } : { ...EMPTY })
  }

  const save = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    const body = { ...edit, code: edit.code.toUpperCase(), maxActiveOrders: Number(edit.maxActiveOrders), maxQtyPerItem: Number(edit.maxQtyPerItem), noShowMinutes: Number(edit.noShowMinutes) }
    try {
      await api(edit.id ? `/api/admin/canteens/${edit.id}` : '/api/admin/canteens', { method: edit.id ? 'PUT' : 'POST', body })
      toast(edit.id ? 'Canteen saved' : 'Canteen added. Add its menu and kitchen staff next.', 'good')
      setEdit(null)
      load()
      reload()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  const set = (k) => (e) => setEdit({ ...edit, [k]: e.target.value })
  const statsOf = (id) => stats.find((s) => s.id === id)

  return (
    <>
      <PageTitle title="Canteens" sub="Each canteen has its own menu, stock, kitchen staff, hours and order numbers."
        right={<Button size="sm" onClick={() => open(null)}><Plus className="size-4" /> Add canteen</Button>} />
      {!list ? <Spinner /> : (
        <div className="grid gap-3 md:grid-cols-2">
          {list.map((c) => {
            const s = statsOf(c.id)
            return (
              <Card key={c.id} className={`p-4 ${c.active ? '' : 'opacity-60'}`}>
                <div className="flex items-start justify-between gap-3">
                  <div className="flex items-start gap-3">
                    <span className="grid size-11 place-items-center rounded-xl bg-brand-soft font-display text-lg font-bold text-brand-ink">{c.code}</span>
                    <div>
                      <p className="font-display text-lg font-bold">{c.name} {!c.active && <span className="rounded bg-card-2 px-1.5 text-xs font-normal text-muted">hidden</span>}</p>
                      <p className="flex items-center gap-1 text-sm text-muted"><MapPin className="size-3.5" />{c.location || 'No location set'}</p>
                      <p className="text-xs text-muted">{c.openTime.slice(0, 5)}–{c.closeTime.slice(0, 5)} · {c.openNow ? (c.status === 'BUSY' ? 'Busy' : 'Open now') : 'Closed now'} · orders {c.code}-001…</p>
                    </div>
                  </div>
                  <button onClick={() => open(c)} className="rounded-lg p-2 text-ink-2 hover:bg-card-2" aria-label={`Edit ${c.name}`}><Pencil className="size-4" /></button>
                </div>
                {s && (
                  <div className="mt-3 grid grid-cols-3 gap-2 border-t border-line pt-3 text-center text-sm">
                    <div><p className="text-xs text-muted">Today</p><p className="font-semibold">{rupees(s.revenueToday)}</p><p className="text-xs text-muted">{s.ordersToday} order{s.ordersToday === 1 ? '' : 's'}</p></div>
                    <div><p className="text-xs text-muted">30 days</p><p className="font-semibold">{rupees(s.revenue30d)}</p><p className="text-xs text-muted">avg {rupees(s.avgOrderValue)}</p></div>
                    <div><p className="text-xs text-muted">Rating</p><p className="font-semibold">{s.avgRating ? `${s.avgRating} ★` : '—'}</p><p className="truncate text-xs text-muted">{s.topDish ? `Top: ${s.topDish}` : 'No sales yet'}</p></div>
                  </div>
                )}
              </Card>
            )
          })}
        </div>
      )}

      {stats.length > 1 && (
        <Card className="mt-4 p-4">
          <h2 className="mb-3 font-semibold">Sales, last 30 days</h2>
          <RankBars rows={stats.map((s) => ({ label: s.name, value: Number(s.revenue30d), extra: `${s.orders30d} orders` }))} format={rupees} />
        </Card>
      )}

      <Modal open={!!edit} onClose={() => setEdit(null)} title={edit?.id ? `Edit ${edit.name}` : 'Add a canteen'}>
        {edit && (
          <form onSubmit={save} className="space-y-3">
            <div className="grid grid-cols-[1fr_5rem] gap-3">
              <Input label="Name" required maxLength={60} placeholder="Food Court" value={edit.name} onChange={set('name')} />
              <Input label="Code" required maxLength={3} pattern="[A-Za-z]{1,3}" title="1-3 letters" placeholder="F" value={edit.code}
                onChange={(e) => setEdit({ ...edit, code: e.target.value.toUpperCase() })} />
            </div>
            <p className="-mt-1 text-xs text-muted">The code starts this canteen's order numbers, e.g. {(edit.code || 'F').toUpperCase()}-017.</p>
            <Input label="Pickup location" maxLength={120} placeholder="Near the library" value={edit.location} onChange={set('location')} />
            <div className="grid grid-cols-2 gap-3">
              <Input label="Opens at" type="time" required value={edit.openTime} onChange={set('openTime')} />
              <Input label="Closes at" type="time" required value={edit.closeTime} onChange={set('closeTime')} />
            </div>
            <div className="grid grid-cols-3 gap-3">
              <Input label="Orders/student" type="number" min={1} max={10} required value={edit.maxActiveOrders} onChange={set('maxActiveOrders')} />
              <Input label="Max per dish" type="number" min={1} max={20} required value={edit.maxQtyPerItem} onChange={set('maxQtyPerItem')} />
              <Input label="Hold (min)" type="number" min={10} max={240} required value={edit.noShowMinutes} onChange={set('noShowMinutes')} />
            </div>
            <div className="flex items-center justify-between rounded-xl bg-card-2 p-3">
              <span className="text-sm font-medium">Allow cash / UPI at counter</span>
              <Toggle checked={edit.cashAllowed} onChange={(v) => setEdit({ ...edit, cashAllowed: v })} label="Allow cash at counter" />
            </div>
            <div className="flex items-center justify-between rounded-xl bg-card-2 p-3">
              <span><span className="block text-sm font-medium">Visible to students</span><span className="block text-xs text-muted">Turn off to hide it (history is kept)</span></span>
              <Toggle checked={edit.active} onChange={(v) => setEdit({ ...edit, active: v })} label="Visible to students" />
            </div>
            <ErrorNote>{error}</ErrorNote>
            <Button type="submit" className="w-full" loading={busy}><Store className="size-4" /> {edit.id ? 'Save canteen' : 'Add canteen'}</Button>
          </form>
        )}
      </Modal>
    </>
  )
}
