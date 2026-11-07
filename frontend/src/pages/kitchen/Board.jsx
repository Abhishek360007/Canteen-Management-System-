import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate, useOutletContext } from 'react-router-dom'
import { Banknote, ChefHat, Clock, StickyNote, Volume2, VolumeX } from 'lucide-react'
import { api, minutesAgo, rupees, slotLabel } from '../../api'
import { useLive } from '../../context/LiveContext'
import { Button, Card, Empty, ErrorNote, Modal, Spinner, useToast } from '../../components/ui'

const COLS = [
  { status: 'PLACED', title: 'New', tone: 'text-warn' },
  { status: 'PREPARING', title: 'Cooking', tone: 'text-brand-ink' },
  { status: 'READY', title: 'Ready to collect', tone: 'text-good' },
]

/** Short two-tone chime for a new order, made with Web Audio (no sound file needed). */
function chime() {
  try {
    const ctx = new (window.AudioContext || window.webkitAudioContext)()
    ;[880, 1320].forEach((f, i) => {
      const o = ctx.createOscillator()
      const g = ctx.createGain()
      o.frequency.value = f
      g.gain.setValueAtTime(0.0001, ctx.currentTime + i * 0.18)
      g.gain.exponentialRampToValueAtTime(0.25, ctx.currentTime + i * 0.18 + 0.02)
      g.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + i * 0.18 + 0.25)
      o.connect(g).connect(ctx.destination)
      o.start(ctx.currentTime + i * 0.18)
      o.stop(ctx.currentTime + i * 0.18 + 0.3)
    })
  } catch { /* audio blocked */ }
}

export function CanteenSwitch({ canteen }) {
  const toast = useToast()
  const [busy, setBusy] = useState(null)
  const set = async (status) => {
    setBusy(status)
    try {
      await api('/api/kitchen/status', { method: 'PUT', body: { status } })
    } catch (e) {
      toast(e.message, 'error')
    } finally {
      setBusy(null)
    }
  }
  const opts = [['OPEN', 'Open', 'bg-good text-white dark:text-black'], ['BUSY', 'Busy', 'bg-warn text-white dark:text-black'], ['CLOSED', 'Closed', 'bg-bad text-white dark:text-black']]
  return (
    <div className="inline-flex rounded-xl border border-line bg-card p-1" role="radiogroup" aria-label="Canteen status">
      {opts.map(([s, label, on]) => (
        <button key={s} role="radio" aria-checked={canteen?.status === s} disabled={!!busy} onClick={() => set(s)}
          className={`rounded-lg px-3 py-1.5 text-sm font-semibold transition ${canteen?.status === s ? on : 'text-ink-2 hover:bg-card-2'}`}>
          {label}
        </button>
      ))}
    </div>
  )
}

export default function Board() {
  const { canteen } = useOutletContext()
  const nav = useNavigate()
  const toast = useToast()
  const [orders, setOrders] = useState(null)
  const [error, setError] = useState(null)
  const [tab, setTab] = useState('PLACED')
  const [sound, setSound] = useState(() => { try { return localStorage.getItem('tc_sound') !== 'off' } catch { return true } })
  const [cancelling, setCancelling] = useState(null)
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(null)
  const seen = useRef(null)

  const load = useCallback(() =>
    api('/api/kitchen/queue').then((list) => {
      const ids = new Set(list.map((o) => o.id))
      if (seen.current && sound && list.some((o) => o.status === 'PLACED' && !seen.current.has(o.id))) chime()
      seen.current = ids
      setOrders(list)
      setError(null)
    }).catch((e) => setError(e.message)), [sound])

  useEffect(() => { load() }, [load])
  useLive('order', load)
  // Refresh "x min ago" labels.
  useEffect(() => {
    const t = setInterval(() => setOrders((o) => (o ? [...o] : o)), 30000)
    return () => clearInterval(t)
  }, [])

  const move = async (o, status, why) => {
    setBusy(o.id + status)
    try {
      await api(`/api/kitchen/orders/${o.id}/status`, { method: 'POST', body: { status, reason: why } })
      load()
    } catch (e) {
      toast(e.message, 'error')
      load()
    } finally {
      setBusy(null)
    }
  }

  const toggleSound = () => {
    const next = !sound
    setSound(next)
    try { localStorage.setItem('tc_sound', next ? 'on' : 'off') } catch { /* ignore */ }
    if (next) chime()
  }

  if (error && !orders) return <ErrorNote>{error}</ErrorNote>
  if (!orders) return <Spinner />

  const by = (s) => orders.filter((o) => o.status === s)

  return (
    <>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Live orders</h1>
          <p className="text-sm text-muted">{orders.length} in progress · updates automatically</p>
        </div>
        <div className="flex items-center gap-2">
          <CanteenSwitch canteen={canteen} />
          <button onClick={toggleSound} className="rounded-xl border border-line bg-card p-2.5 text-ink-2" aria-label={sound ? 'Mute new-order sound' : 'Turn on new-order sound'}>
            {sound ? <Volume2 className="size-5" /> : <VolumeX className="size-5" />}
          </button>
        </div>
      </div>

      <div className="mb-3 grid grid-cols-3 gap-1 rounded-xl bg-card-2 p-1 md:hidden">
        {COLS.map((c) => (
          <button key={c.status} onClick={() => setTab(c.status)}
            className={`rounded-lg py-2 text-sm font-semibold ${tab === c.status ? 'bg-card shadow-sm' : 'text-muted'}`}>
            {c.title.split(' ')[0]} <span className="ml-0.5 text-xs">({by(c.status).length})</span>
          </button>
        ))}
      </div>

      <div className="grid gap-4 md:grid-cols-3">
        {COLS.map((c) => (
          <section key={c.status} className={`${tab === c.status ? '' : 'hidden'} md:block`}>
            <h2 className={`mb-2 hidden items-center gap-2 text-sm font-bold uppercase tracking-wide md:flex ${c.tone}`}>
              {c.title} <span className="rounded-full bg-card-2 px-2 text-xs text-ink-2">{by(c.status).length}</span>
            </h2>
            <div className="space-y-3">
              {by(c.status).length === 0 && <Card className="border-dashed"><Empty icon={ChefHat} title="Nothing here" /></Card>}
              {by(c.status).map((o) => (
                <Card key={o.id} className="animate-pop p-3.5">
                  <div className="flex items-start justify-between gap-2">
                    <div>
                      <p className="font-display text-2xl font-extrabold leading-none">{o.code}</p>
                      <p className="mt-1 text-sm font-medium">{o.customerName}{o.customerRollNo ? <span className="text-muted"> · {o.customerRollNo}</span> : null}</p>
                    </div>
                    <div className="text-right text-xs text-muted">
                      <p className="flex items-center justify-end gap-1"><Clock className="size-3.5" />{minutesAgo(o.createdAt)}</p>
                      {o.pickupSlot && <p className="mt-0.5 font-semibold text-brand-ink">Pickup {slotLabel(o.pickupSlot.slice(0, 5))}</p>}
                    </div>
                  </div>
                  <ul className="my-3 space-y-1 rounded-xl bg-card-2 p-2.5 text-[15px]">
                    {o.items.map((i) => <li key={i.menuItemId}><span className="font-bold">{i.quantity}×</span> {i.name}</li>)}
                  </ul>
                  {o.note && <p className="mb-2 flex gap-1.5 rounded-lg bg-warn-soft p-2 text-sm text-warn"><StickyNote className="mt-0.5 size-4 shrink-0" />{o.note}</p>}
                  {o.paymentMethod === 'CASH' && (
                    <p className="mb-2 flex items-center gap-1.5 text-sm font-semibold text-bad"><Banknote className="size-4" /> Collect {rupees(o.total)} at counter</p>
                  )}
                  <div className="flex gap-2">
                    {o.status === 'PLACED' && <Button className="flex-1" loading={busy === o.id + 'PREPARING'} onClick={() => move(o, 'PREPARING')}>Start cooking</Button>}
                    {o.status !== 'READY' && <Button variant={o.status === 'PLACED' ? 'outline' : 'good'} className="flex-1" loading={busy === o.id + 'READY'} onClick={() => move(o, 'READY')}>Mark ready</Button>}
                    {o.status === 'READY' && <Button variant="good" className="flex-1" onClick={() => nav('/kitchen/verify', { state: { code: o.code } })}>Hand over</Button>}
                  </div>
                  <div className="mt-2 flex justify-end gap-3 text-xs">
                    {o.status === 'READY' && (
                      <button className="font-medium text-muted hover:text-bad" onClick={() => confirm(`Mark ${o.code} as not collected? No refund is given.`) && move(o, 'NO_SHOW')}>Not collected</button>
                    )}
                    <button className="font-medium text-muted hover:text-bad" onClick={() => { setCancelling(o); setReason('') }}>Cancel order</button>
                  </div>
                </Card>
              ))}
            </div>
          </section>
        ))}
      </div>

      <Modal open={!!cancelling} onClose={() => setCancelling(null)} title={`Cancel ${cancelling?.code}?`}>
        <p className="mb-3 text-sm text-ink-2">The student is told why{cancelling?.paymentMethod === 'WALLET' ? ' and refunded to their wallet' : ''}.</p>
        <div className="mb-3 flex flex-wrap gap-2">
          {['Item ran out', 'Kitchen closing', 'Duplicate order'].map((r) => (
            <button key={r} onClick={() => setReason(r)} className={`rounded-full border px-3 py-1 text-sm ${reason === r ? 'border-brand bg-brand-soft text-brand-ink' : 'border-line'}`}>{r}</button>
          ))}
        </div>
        <input value={reason} onChange={(e) => setReason(e.target.value)} maxLength={200} placeholder="Reason"
          className="mb-4 h-11 w-full rounded-xl border border-line bg-card px-3 outline-none focus:border-brand" />
        <div className="flex gap-2">
          <Button variant="outline" className="flex-1" onClick={() => setCancelling(null)}>Keep order</Button>
          <Button variant="danger" className="flex-1" loading={busy === cancelling?.id + 'CANCELLED'}
            onClick={async () => { await move(cancelling, 'CANCELLED', reason); setCancelling(null) }}>Cancel order</Button>
        </div>
      </Modal>
    </>
  )
}
