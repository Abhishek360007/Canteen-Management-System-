import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { ArrowLeft, Check, Star } from 'lucide-react'
import { api, rupees, slotLabel, timeOf } from '../../api'
import { useLive } from '../../context/LiveContext'
import { QrImage, qrPayload } from '../../components/Qr'
import PushToggle from '../../components/PushToggle'
import { Button, Card, ErrorNote, Spinner, StatusBadge, useToast } from '../../components/ui'

const STEPS = [['PLACED', 'Placed'], ['PREPARING', 'Cooking'], ['READY', 'Ready'], ['COLLECTED', 'Collected']]

function notifyReady(order) {
  try {
    navigator.vibrate?.([200, 100, 200])
    if ('Notification' in window && Notification.permission === 'granted') {
      new Notification("Your food is ready! 🍽️", { body: `Order ${order.code} — show your code at the counter.`, icon: '/favicon.svg' })
    }
  } catch { /* not supported */ }
}

export default function OrderPass() {
  const { id } = useParams()
  const { state } = useLocation()
  const toast = useToast()
  const [order, setOrder] = useState(null)
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)
  const lastStatus = useRef(null)

  const load = useCallback(() =>
    api(`/api/orders/${id}`).then((o) => {
      if (lastStatus.current && lastStatus.current !== 'READY' && o.status === 'READY') notifyReady(o)
      lastStatus.current = o.status
      setOrder(o)
    }).catch((e) => setError(e.message)), [id])

  useEffect(() => { load() }, [load])
  useLive('order', (ev) => { if (String(ev?.id) === String(id)) load() })

  // Keep the ETA ticking even without events.
  useEffect(() => {
    const t = setInterval(load, 60000)
    return () => clearInterval(t)
  }, [load])

  const cancel = async () => {
    if (!confirm('Cancel this order? Wallet payments are refunded right away.')) return
    setBusy(true)
    try {
      setOrder(await api(`/api/orders/${id}/cancel`, { method: 'POST' }))
      toast('Order cancelled', 'good')
    } catch (e) {
      toast(e.message, 'error')
    } finally {
      setBusy(false)
    }
  }

  const rate = async (stars) => {
    try {
      setOrder(await api(`/api/orders/${id}/rate`, { method: 'POST', body: { stars } }))
      toast('Thanks for rating!', 'good')
    } catch (e) {
      toast(e.message, 'error')
    }
  }

  if (error) return <ErrorNote>{error}</ErrorNote>
  if (!order) return <Spinner />

  const active = ['PLACED', 'PREPARING', 'READY'].includes(order.status)
  const stepIndex = STEPS.findIndex(([s]) => s === order.status)

  return (
    <>
      <Link to="/orders" className="mb-3 inline-flex items-center gap-1 text-sm font-medium text-muted hover:text-ink">
        <ArrowLeft className="size-4" /> All orders
      </Link>

      {state?.fresh && active && (
        <div className="animate-pop mb-4 flex items-center gap-2 rounded-2xl bg-good-soft p-3 text-sm font-medium text-good">
          <Check className="size-5" /> Order placed! We'll tell you when it's ready.
        </div>
      )}

      <Card className={`overflow-hidden ${order.status === 'READY' ? 'border-good pulse-ready' : ''}`}>
        <div className={`px-5 py-4 text-center ${order.status === 'READY' ? 'bg-good text-white dark:text-black' : 'bg-brand text-white'}`}>
          <p className="text-xs font-semibold uppercase tracking-widest opacity-85">Pickup pass</p>
          <p className="font-display text-5xl font-extrabold tracking-tight">{order.code}</p>
          <p className="mt-1 text-sm opacity-90">
            {order.status === 'READY' ? 'Ready now — show this at the counter'
              : active ? (order.pickupSlot ? `Pickup at ${slotLabel(order.pickupSlot.slice(0, 5))}` : `About ${order.etaMinutes} min · ${order.ahead} ahead of you`)
                : <StatusBadge status={order.status} />}
          </p>
        </div>

        {active && order.otp && (
          <div className="flex flex-col items-center gap-4 border-b border-dashed border-line px-5 py-5 sm:flex-row sm:justify-center sm:gap-8">
            <div className="text-center">
              <p className="text-xs font-semibold uppercase tracking-wide text-muted">Pickup code</p>
              <div className="mt-1 flex gap-2" aria-label={`Pickup code ${order.otp.split('').join(' ')}`}>
                {order.otp.split('').map((d, i) => (
                  <span key={i} className="grid h-14 w-11 place-items-center rounded-xl bg-card-2 font-display text-3xl font-bold">{d}</span>
                ))}
              </div>
              <p className="mt-2 max-w-[14rem] text-xs text-muted">Only tell this code to the counter staff when you collect.</p>
            </div>
            <QrImage value={qrPayload(order.code, order.otp)} size={150} />
          </div>
        )}

        {order.status !== 'CANCELLED' && order.status !== 'NO_SHOW' && (
          <ol className="flex items-center justify-between px-5 py-4">
            {STEPS.map(([s, label], i) => (
              <li key={s} className="flex flex-1 flex-col items-center text-center">
                <div className="flex w-full items-center">
                  <span className={`h-0.5 flex-1 ${i === 0 ? 'opacity-0' : i <= stepIndex ? 'bg-brand' : 'bg-line'}`} />
                  <span className={`grid size-7 place-items-center rounded-full text-xs font-bold ${i <= stepIndex ? 'bg-brand text-white' : 'bg-card-2 text-muted'}`}>
                    {i < stepIndex ? <Check className="size-4" /> : i + 1}
                  </span>
                  <span className={`h-0.5 flex-1 ${i === STEPS.length - 1 ? 'opacity-0' : i < stepIndex ? 'bg-brand' : 'bg-line'}`} />
                </div>
                <span className={`mt-1 text-xs font-medium ${i <= stepIndex ? 'text-ink' : 'text-muted'}`}>{label}</span>
              </li>
            ))}
          </ol>
        )}

        {order.cancelReason && <p className="mx-5 mb-4 rounded-xl bg-bad-soft p-3 text-sm text-bad">{order.cancelReason}{order.status === 'CANCELLED' && order.paymentMethod === 'WALLET' ? ' · Refunded to your wallet' : ''}</p>}

        <div className="border-t border-line px-5 py-4">
          <ul className="space-y-1.5 text-sm">
            {order.items.map((i) => (
              <li key={i.menuItemId} className="flex justify-between gap-3"><span>{i.quantity} × {i.name}</span><span className="text-ink-2">{rupees(i.lineTotal)}</span></li>
            ))}
          </ul>
          {order.note && <p className="mt-2 text-xs text-muted">Note: {order.note}</p>}
          {Number(order.discount) > 0 && (
            <div className="mt-2 flex justify-between text-sm text-good"><span>Reward points ({order.pointsRedeemed})</span><span>−{rupees(order.discount)}</span></div>
          )}
          <div className="mt-3 flex justify-between border-t border-line pt-3 font-semibold">
            <span>{order.paymentMethod === 'WALLET' ? 'Paid from wallet' : 'Pay at counter'}</span>
            <span className="font-display text-lg">{rupees(order.total)}</span>
          </div>
          <p className="mt-1 text-xs text-muted">Placed at {timeOf(order.createdAt)}</p>
          {order.pointsEarned > 0 && <p className="mt-2 rounded-xl bg-good-soft px-3 py-2 text-sm font-medium text-good">🎉 You earned {order.pointsEarned} reward points on this order</p>}
        </div>
      </Card>

      {active && <div className="mt-4"><PushToggle compact /></div>}

      {order.status === 'PLACED' && (
        <Button variant="danger" className="mt-3 w-full" loading={busy} onClick={cancel}>Cancel order</Button>
      )}

      {order.status === 'COLLECTED' && (
        <Card className="mt-4 p-4 text-center">
          <p className="font-semibold">{order.rating ? 'Thanks for your rating' : 'How was your food?'}</p>
          <div className="mt-2 flex justify-center gap-1">
            {[1, 2, 3, 4, 5].map((n) => (
              <button key={n} disabled={!!order.rating} onClick={() => rate(n)} aria-label={`${n} star${n > 1 ? 's' : ''}`} className="p-1">
                <Star className={`size-8 ${n <= (order.rating || 0) ? 'fill-warn text-warn' : 'text-line'}`} />
              </button>
            ))}
          </div>
        </Card>
      )}
    </>
  )
}
