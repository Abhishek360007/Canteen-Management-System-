import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ChevronRight, Receipt } from 'lucide-react'
import { api, dateOf, rupees, timeOf } from '../../api'
import { useLive } from '../../context/LiveContext'
import { Button, Card, Empty, ErrorNote, PageTitle, Spinner, StatusBadge } from '../../components/ui'

const ACTIVE = ['PLACED', 'PREPARING', 'READY']

export default function Orders() {
  const [orders, setOrders] = useState(null)
  const [error, setError] = useState(null)
  const load = useCallback(() => api('/api/orders').then(setOrders).catch((e) => setError(e.message)), [])
  useEffect(() => { load() }, [load])
  useLive('order', load)

  if (error) return <ErrorNote>{error}</ErrorNote>
  if (!orders) return <Spinner />
  if (!orders.length) {
    return (
      <Empty icon={Receipt} title="No orders yet">
        <p>Your orders and pickup codes will show up here.</p>
        <Link to="/menu"><Button className="mt-4">Order something</Button></Link>
      </Empty>
    )
  }

  const active = orders.filter((o) => ACTIVE.includes(o.status))
  const past = orders.filter((o) => !ACTIVE.includes(o.status))

  return (
    <>
      <PageTitle title="Your orders" />
      {active.length > 0 && (
        <section className="mb-6">
          <h2 className="mb-2 text-sm font-semibold uppercase tracking-wide text-muted">In progress</h2>
          <div className="space-y-3">
            {active.map((o) => (
              <Link key={o.id} to={`/orders/${o.id}`}>
                <Card className={`flex items-center gap-4 p-4 ${o.status === 'READY' ? 'border-good pulse-ready' : ''}`}>
                  <div className="text-center">
                    <div className="text-[11px] font-semibold uppercase text-muted">Order</div>
                    <div className="font-display text-2xl font-extrabold">{o.code}</div>
                  </div>
                  <div className="min-w-0 flex-1">
                    <StatusBadge status={o.status} />
                    <p className="mt-1 truncate text-sm text-ink-2">{o.items.map((i) => `${i.quantity}× ${i.name}`).join(', ')}</p>
                    {o.canteenName && <p className="truncate text-xs font-medium text-ink-2">at {o.canteenName}</p>}
                    <p className="text-xs text-muted">
                      {o.status === 'READY' ? 'Ready! Show your code at the counter.' : `About ${o.etaMinutes} min · ${o.ahead} order(s) ahead`}
                    </p>
                  </div>
                  <ChevronRight className="size-5 text-muted" />
                </Card>
              </Link>
            ))}
          </div>
        </section>
      )}

      {past.length > 0 && (
        <section>
          <h2 className="mb-2 text-sm font-semibold uppercase tracking-wide text-muted">Past orders</h2>
          <Card className="divide-y divide-line">
            {past.map((o) => (
              <Link key={o.id} to={`/orders/${o.id}`} className="flex items-center gap-3 p-3.5 hover:bg-card-2">
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <span className="font-semibold">{o.code}</span>
                    <span className="text-xs text-muted">{dateOf(o.createdAt)}, {timeOf(o.createdAt)}</span>
                  </div>
                  <p className="truncate text-sm text-ink-2">{o.items.map((i) => `${i.quantity}× ${i.name}`).join(', ')}</p>
                </div>
                <div className="text-right">
                  <div className="font-semibold">{rupees(o.total)}</div>
                  <StatusBadge status={o.status} />
                </div>
              </Link>
            ))}
          </Card>
        </section>
      )}
    </>
  )
}
