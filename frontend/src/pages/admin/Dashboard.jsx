import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ChevronRight } from 'lucide-react'
import { api, rupees } from '../../api'
import { useLive } from '../../context/LiveContext'
import { BarChart, RankBars } from '../../components/Charts'
import { Card, ErrorNote, PageTitle, Spinner } from '../../components/ui'
import { Stat } from '../kitchen/Summary'

export default function Dashboard() {
  const [d, setD] = useState(null)
  const [error, setError] = useState(null)
  const load = useCallback(() => api('/api/admin/dashboard').then(setD).catch((e) => setError(e.message)), [])
  useEffect(() => { load() }, [load])
  useLive('order', load)
  useLive('topup', load)

  if (error) return <ErrorNote>{error}</ErrorNote>
  if (!d) return <Spinner />

  const days = d.last7Days.map((p) => {
    const dt = new Date(p.day + 'T00:00:00')
    return {
      label: dt.toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'short' }),
      short: dt.toLocaleDateString('en-IN', { weekday: 'short' }),
      value: Number(p.revenue),
      extra: `${p.orders} orders`,
    }
  })
  const hr = (h) => `${h % 12 || 12} ${h < 12 || h === 24 ? 'AM' : 'PM'}`
  const hours = d.busyHours.map((h) => ({
    label: `${hr(h.hour)} – ${hr(h.hour + 1)}`,
    short: `${h.hour % 12 || 12}${h.hour < 12 ? 'a' : 'p'}`,
    value: h.orders,
  }))
  const weekRevenue = d.last7Days.reduce((s, p) => s + Number(p.revenue), 0)

  return (
    <>
      <PageTitle title="Dashboard" sub="Today at Tula's Canteen" />
      {d.pendingTopups > 0 && (
        <Link to="/admin/topups" className="mb-4 flex items-center justify-between gap-3 rounded-2xl bg-warn-soft p-3.5 text-sm font-medium text-warn">
          <span>{d.pendingTopups} UPI top-up{d.pendingTopups > 1 ? 's' : ''} waiting for approval</span>
          <span className="flex items-center gap-1 font-semibold">Review <ChevronRight className="size-4" /></span>
        </Link>
      )}
      <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
        <Stat label="Sales today" value={rupees(d.revenueToday)} hint={`${d.ordersToday} order${d.ordersToday === 1 ? '' : 's'}`} strong />
        <Stat label="In progress now" value={d.activeNow} hint={`${d.cancelledToday} cancelled · ${d.noShowsToday} not collected`} />
        <Stat label="Students" value={d.studentCount} hint={`${rupees(d.walletFloat)} held in wallets`} />
        <Stat label="Avg rating (7 days)" value={d.avgRating ? `${d.avgRating} ★` : '—'} hint="From collected orders" />
      </div>

      <div className="mt-4 grid gap-4 lg:grid-cols-3">
        <Card className="p-4 lg:col-span-2">
          <div className="mb-2 flex items-baseline justify-between">
            <h2 className="font-semibold">Sales, last 7 days</h2>
            <span className="text-sm text-muted">{rupees(weekRevenue)} total</span>
          </div>
          <BarChart data={days} format={rupees} label="Sales per day for the last 7 days" />
        </Card>
        <Card className="p-4">
          <h2 className="mb-3 font-semibold">Top dishes (7 days)</h2>
          {d.topItems.length
            ? <RankBars rows={d.topItems.map((t) => ({ label: t.name, value: t.count, extra: rupees(t.revenue) }))} format={(v) => `${v} sold`} />
            : <p className="text-sm text-muted">No sales yet.</p>}
        </Card>
        <Card className="p-4 lg:col-span-3">
          <h2 className="mb-2 font-semibold">Busiest hours (orders placed, 7 days)</h2>
          <BarChart data={hours} format={(v) => `${v} orders`} height={150} label="Orders placed per hour of the day" />
        </Card>
      </div>
    </>
  )
}
