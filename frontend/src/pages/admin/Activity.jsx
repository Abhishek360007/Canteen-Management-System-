import { useCallback, useEffect, useState } from 'react'
import { History, Search } from 'lucide-react'
import { api, dateOf, timeOf } from '../../api'
import { Button, Card, Empty, ErrorNote, PageTitle, Spinner } from '../../components/ui'

const LABEL = {
  WALLET_RECHARGE: 'Wallet recharged', TOPUP_APPROVED: 'UPI top-up approved', TOPUP_REJECTED: 'UPI top-up rejected',
  USER_BLOCKED: 'User blocked', USER_UNBLOCKED: 'User unblocked', PASSWORD_RESET: 'Password reset',
  USER_LOGGED_OUT: 'Logged out everywhere', STAFF_CREATED: 'Staff login created',
  DISH_ADDED: 'Dish added', DISH_UPDATED: 'Dish edited', DISH_REMOVED: 'Dish removed', MENU_IMPORTED: 'Standard menu added',
  ORDER_CANCELLED: 'Order cancelled', ORDER_MANUAL_HANDOVER: 'Handed over without code', ORDER_NO_SHOW: 'Marked not collected',
  CANTEEN_STATUS: 'Canteen status', SETTINGS_CHANGED: 'Settings changed', ANNOUNCEMENT_POSTED: 'Announcement posted',
}
const MONEY = new Set(['WALLET_RECHARGE', 'TOPUP_APPROVED', 'ORDER_CANCELLED', 'ORDER_MANUAL_HANDOVER'])

/** Who did what and when: money, users, menu and order exceptions. */
export default function Activity() {
  const [q, setQ] = useState('')
  const [page, setPage] = useState(0)
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)

  const load = useCallback(() => {
    const p = new URLSearchParams({ page })
    if (q.trim()) p.set('q', q.trim())
    api(`/api/admin/audit?${p}`).then(setData).catch((e) => setError(e.message))
  }, [q, page])
  useEffect(() => {
    const t = setTimeout(load, 250)
    return () => clearTimeout(t)
  }, [load])

  return (
    <>
      <PageTitle title="Activity log" sub="Every money, user and menu action by admins and kitchen staff." />
      <div className="relative mb-3">
        <Search className="pointer-events-none absolute left-3 top-1/2 size-4.5 -translate-y-1/2 text-muted" />
        <input value={q} onChange={(e) => { setQ(e.target.value); setPage(0) }} placeholder="Search person, action or student" aria-label="Search activity"
          className="h-11 w-full rounded-xl border border-line bg-card pl-10 pr-3 outline-none focus:border-brand" />
      </div>
      <ErrorNote>{error}</ErrorNote>
      {!data ? <Spinner /> : data.items.length === 0 ? <Empty icon={History} title="No activity yet" /> : (
        <>
          <Card className="divide-y divide-line">
            {data.items.map((a) => (
              <div key={a.id} className="flex gap-3 p-3.5">
                <span className={`mt-1.5 size-2 shrink-0 rounded-full ${MONEY.has(a.action) ? 'bg-brand' : 'bg-muted'}`} aria-hidden="true" />
                <div className="min-w-0 flex-1">
                  <p className="text-sm"><span className="font-semibold">{LABEL[a.action] || a.action}</span>{a.target ? <span className="text-ink-2"> · {a.target}</span> : null}</p>
                  {a.details && <p className="truncate text-sm text-ink-2">{a.details}</p>}
                  <p className="text-xs text-muted">by {a.actorName} · {dateOf(a.createdAt)}, {timeOf(a.createdAt)}</p>
                </div>
              </div>
            ))}
          </Card>
          {data.totalPages > 1 && (
            <div className="mt-3 flex items-center justify-center gap-3 text-sm">
              <Button size="sm" variant="outline" disabled={page === 0} onClick={() => setPage(page - 1)}>Newer</Button>
              <span className="text-muted">Page {page + 1} of {data.totalPages}</span>
              <Button size="sm" variant="outline" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>Older</Button>
            </div>
          )}
        </>
      )}
    </>
  )
}
