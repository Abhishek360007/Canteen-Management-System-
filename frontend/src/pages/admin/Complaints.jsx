import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ChevronRight, MessageSquareWarning, Star } from 'lucide-react'
import { api, dateOf, minutesAgo, rupees } from '../../api'
import { useLive } from '../../context/LiveContext'
import { useCanteens } from '../../context/CanteenContext'
import { RankBars } from '../../components/Charts'
import { Stat } from '../../components/Stat'
import { ComplaintStatus, categoryLabel } from '../../components/Feedback'
import { Card, Empty, ErrorNote, PageTitle, Spinner } from '../../components/ui'

/** "12 min ago" today-ish, "9 Nov" for older. */
const ago = (iso) => (Date.now() - new Date(iso).getTime() < 86400e3 ? minutesAgo(iso) : dateOf(iso))

const TABS = [['OPEN', 'Open'], ['RESOLVED', 'Resolved'], ['REJECTED', 'Closed'], ['', 'All']]

/** Admin: every complaint, newest activity first, with a 30-day summary of what goes wrong. */
export default function Complaints() {
  const { canteens, selected } = useCanteens()
  const [tab, setTab] = useState('OPEN')
  const [all, setAll] = useState(true)
  const [list, setList] = useState(null)
  const [stats, setStats] = useState(null)
  const [error, setError] = useState(null)

  const scope = all || !selected ? '' : `canteenId=${selected.id}`
  const load = useCallback(() => {
    const q = [tab && `status=${tab}`, scope].filter(Boolean).join('&')
    api(`/api/admin/complaints${q ? `?${q}` : ''}`).then(setList).catch((e) => setError(e.message))
    api(`/api/admin/complaints/stats${scope ? `?${scope}` : ''}`).then(setStats).catch(() => {})
  }, [tab, scope])
  useEffect(() => { setList(null); load() }, [load])
  useLive('complaint', load)

  return (
    <>
      <PageTitle title="Complaints" sub="Problems students reported with their orders." />
      {canteens.length > 1 && (
        <div className="mb-3 inline-flex rounded-xl bg-card-2 p-1 text-sm" role="tablist">
          <button role="tab" aria-selected={all} onClick={() => setAll(true)} className={`rounded-lg px-3 py-1.5 font-semibold ${all ? 'bg-card shadow-sm' : 'text-muted'}`}>All canteens</button>
          <button role="tab" aria-selected={!all} onClick={() => setAll(false)} className={`rounded-lg px-3 py-1.5 font-semibold ${!all ? 'bg-card shadow-sm' : 'text-muted'}`}>{selected?.name || 'This canteen'}</button>
        </div>
      )}

      {stats && (
        <>
          <div className="mb-3 grid grid-cols-2 gap-3 md:grid-cols-4">
            <Stat label="Open now" value={stats.open} strong={stats.open > 0} />
            <Stat label="Last 30 days" value={stats.total30d} hint={`${stats.resolved30d} resolved · ${stats.rejected30d} closed`} />
            <Stat label="Refunded (30 days)" value={rupees(stats.refunded30d)} />
            <Stat label="Time to close" value={stats.avgHoursToClose == null ? '–' : stats.avgHoursToClose < 1 ? `${Math.round(stats.avgHoursToClose * 60)} min` : `${stats.avgHoursToClose} h`} hint="average" />
          </div>
          {stats.flaggedReviews > 0 && (
            <Link to="/admin/reviews" className="mb-3 flex items-center justify-between gap-3 rounded-2xl bg-warn-soft p-3.5 text-sm font-medium text-warn">
              <span className="flex items-center gap-2"><Star className="size-4" /> {stats.flaggedReviews} dish review{stats.flaggedReviews > 1 ? 's' : ''} waiting for a check</span>
              <ChevronRight className="size-4" />
            </Link>
          )}
          {stats.total30d > 0 && (
            <div className="mb-5 grid gap-3 md:grid-cols-2">
              <Card className="p-4">
                <h2 className="mb-3 text-sm font-semibold">What goes wrong</h2>
                <RankBars rows={stats.byCategory.map((c) => ({ label: categoryLabel(c.category), value: c.count }))} />
              </Card>
              <Card className="p-4">
                <h2 className="mb-3 text-sm font-semibold">Dishes named most</h2>
                {stats.topDishes.length ? <RankBars rows={stats.topDishes.map((d) => ({ label: d.name, value: d.count }))} />
                  : <p className="text-sm text-muted">No dish was named in the last 30 days.</p>}
              </Card>
            </div>
          )}
        </>
      )}

      <div className="mb-3 inline-flex rounded-xl bg-card-2 p-1">
        {TABS.map(([k, label]) => (
          <button key={k} onClick={() => setTab(k)}
            className={`rounded-lg px-3.5 py-1.5 text-sm font-semibold ${tab === k ? 'bg-card shadow-sm' : 'text-muted'}`}>{label}</button>
        ))}
      </div>
      <ErrorNote>{error}</ErrorNote>
      {!list ? <Spinner /> : list.length === 0 ? (
        <Empty icon={MessageSquareWarning} title={tab === 'OPEN' ? 'No open complaints' : 'Nothing here'}>New complaints show up here instantly.</Empty>
      ) : (
        <Card className="divide-y divide-line">
          {list.map((c) => (
            <Link key={c.id} to={`/admin/complaints/${c.id}`} className="flex items-center gap-3 p-4 hover:bg-card-2">
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-semibold">{c.orderCode}</span>
                  <span className="text-sm text-ink-2">{categoryLabel(c.category)}</span>
                  <ComplaintStatus status={c.status} lastFromStaff={c.lastFromStaff} staffView />
                </div>
                <p className="mt-0.5 truncate text-sm text-ink-2">{c.dishes ? `${c.dishes}: ` : ''}{c.details}</p>
                <p className="text-xs text-muted">
                  {c.studentName}{c.studentRollNo ? ` · ${c.studentRollNo}` : ''}{canteens.length > 1 && c.canteenName ? ` · ${c.canteenName}` : ''} · {ago(c.updatedAt)}
                  {c.refundAmount ? ` · refunded ${rupees(c.refundAmount)}` : ''}
                </p>
              </div>
              <ChevronRight className="size-5 shrink-0 text-muted" />
            </Link>
          ))}
        </Card>
      )}
    </>
  )
}
