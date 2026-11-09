import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Bell, BellRing, CircleAlert, Gift, IndianRupee, MessageSquareWarning, PackageX, RotateCcw, Wallet } from 'lucide-react'
import { api, dateOf, timeOf } from '../api'
import { useLive } from '../context/LiveContext'
import { Card, Empty, ErrorNote, PageTitle, Spinner } from '../components/ui'

const ICON = { ORDER_READY: BellRing, REFUND: RotateCcw, PAYMENT: IndianRupee, TOPUP: Wallet, WALLET: Wallet, POINTS: Gift, LOW_STOCK: PackageX, NO_SHOW: CircleAlert, COMPLAINT: MessageSquareWarning }

export default function Notifications() {
  const nav = useNavigate()
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)

  // Opening the page marks everything read (the bell badge clears).
  const load = useCallback(() => api('/api/notifications').then((d) => {
    setData(d)
    if (d.unread > 0) {
      api('/api/notifications/read-all', { method: 'POST' })
        .then(() => window.dispatchEvent(new Event('tc-unread')))
        .catch(() => {})
    }
  }).catch((e) => setError(e.message)), [])
  useEffect(() => { load() }, [load])
  useLive('notification', load)

  if (error) return <ErrorNote>{error}</ErrorNote>
  if (!data) return <Spinner />
  return (
    <>
      <PageTitle title="Notifications" />
      {data.items.length === 0 ? (
        <Empty icon={Bell} title="Nothing yet">Order updates, refunds and rewards will show up here.</Empty>
      ) : (
        <Card className="divide-y divide-line">
          {data.items.map((n) => {
            const Icon = ICON[n.type] || Bell
            const alert = n.type === 'LOW_STOCK' || n.type === 'NO_SHOW'
            return (
              <button key={n.id} onClick={() => n.link && nav(n.link)}
                className={`flex w-full gap-3 p-3.5 text-left hover:bg-card-2 ${n.read ? '' : 'bg-brand-soft/40'}`}>
                <span className={`grid size-9 shrink-0 place-items-center rounded-xl ${alert ? 'bg-bad-soft text-bad' : 'bg-brand-soft text-brand-ink'}`}>
                  <Icon className="size-4.5" />
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block font-semibold">{n.title}</span>
                  {n.body && <span className="block text-sm text-ink-2">{n.body}</span>}
                  <span className="block text-xs text-muted">{dateOf(n.createdAt)}, {timeOf(n.createdAt)}</span>
                </span>
                {!n.read && <span className="mt-1.5 size-2 shrink-0 rounded-full bg-brand" aria-label="Unread" />}
              </button>
            )
          })}
        </Card>
      )}
    </>
  )
}
