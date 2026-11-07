import { useCallback, useEffect, useState } from 'react'
import { api, rupees } from '../../api'
import { useLive } from '../../context/LiveContext'
import { RankBars } from '../../components/Charts'
import { Card, ErrorNote, PageTitle, Spinner } from '../../components/ui'

const iso = (d) => new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10)

export default function Summary() {
  const [day, setDay] = useState(iso(new Date()))
  const [s, setS] = useState(null)
  const [error, setError] = useState(null)

  const load = useCallback(() => api(`/api/kitchen/summary?day=${day}`).then(setS).catch((e) => setError(e.message)), [day])
  useEffect(() => { setS(null); load() }, [load])
  useLive('order', load)

  return (
    <>
      <PageTitle title="Day summary" sub="Sales and the cash that should be in the drawer."
        right={<input type="date" value={day} max={iso(new Date())} onChange={(e) => setDay(e.target.value)} aria-label="Day"
          className="h-10 rounded-xl border border-line bg-card px-3 text-sm outline-none focus:border-brand" />} />
      <ErrorNote>{error}</ErrorNote>
      {!s ? <Spinner /> : (
        <>
          <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
            <Stat label="Cash collected" value={rupees(s.cashSales)} hint={Number(s.cashToCollect) > 0 ? `${rupees(s.cashToCollect)} still to collect` : 'All collected'} strong />
            <Stat label="Wallet sales" value={rupees(s.walletSales)} hint="Already paid" />
            <Stat label="Orders" value={s.orders} hint={`${s.collected} collected · ${s.pending} in progress`} />
            <Stat label="Not completed" value={s.cancelled + s.noShows} hint={`${s.cancelled} cancelled · ${s.noShows} not collected`} />
          </div>
          <Card className="mt-4 p-4">
            <h2 className="mb-3 font-semibold">Items sold</h2>
            {s.items.length ? (
              <RankBars rows={s.items.map((i) => ({ label: i.name, value: i.count, extra: rupees(i.revenue) }))} format={(v) => `${v} plates`} />
            ) : <p className="text-sm text-muted">No sales on this day.</p>}
          </Card>
        </>
      )}
    </>
  )
}

export function Stat({ label, value, hint, strong }) {
  return (
    <Card className={`p-4 ${strong ? 'border-brand bg-brand-soft' : ''}`}>
      <p className="text-xs font-medium text-muted">{label}</p>
      <p className="font-display text-2xl font-bold tracking-tight">{value}</p>
      {hint && <p className="mt-0.5 text-xs text-ink-2">{hint}</p>}
    </Card>
  )
}
