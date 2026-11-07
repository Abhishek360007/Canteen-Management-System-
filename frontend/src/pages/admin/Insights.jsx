import { useCallback, useEffect, useState } from 'react'
import { ArrowDown, ArrowUp, ChefHat, Info, ShoppingBasket } from 'lucide-react'
import { api, rupees } from '../../api'
import { ActualVsForecast, Heatmap } from '../../components/Charts'
import { Button, Card, Empty, ErrorNote, PageTitle, Spinner, useToast } from '../../components/ui'
import { Stat } from '../../components/Stat'

const TABS = [['forecast', 'Forecast'], ['accuracy', 'Accuracy'], ['peaks', 'Peak hours'], ['sellers', 'Best & least']]
const iso = (d) => new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10)

export default function Insights() {
  const [tab, setTab] = useState('forecast')
  return (
    <>
      <PageTitle title="Forecast & insights" sub="Built from your own sales: weekly patterns, trends and peak times." />
      <div className="no-scrollbar mb-4 flex gap-1 overflow-x-auto rounded-xl bg-card-2 p-1" role="tablist">
        {TABS.map(([k, label]) => (
          <button key={k} role="tab" aria-selected={tab === k} onClick={() => setTab(k)}
            className={`shrink-0 flex-1 rounded-lg px-3 py-2 text-sm font-semibold ${tab === k ? 'bg-card shadow-sm' : 'text-muted'}`}>{label}</button>
        ))}
      </div>
      {tab === 'forecast' && <ForecastTab admin />}
      {tab === 'accuracy' && <AccuracyTab />}
      {tab === 'peaks' && <PeaksTab />}
      {tab === 'sellers' && <SellersTab />}
    </>
  )
}

/** Also used on the kitchen's Summary page (read-only). */
export function ForecastTab({ admin = false, compact = false }) {
  const toast = useToast()
  const days = [...Array(7)].map((_, i) => { const d = new Date(); d.setDate(d.getDate() + i + 1); return d })
  const [date, setDate] = useState(iso(days[0]))
  const [f, setF] = useState(null)
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)
  const base = admin ? '/api/admin/insights/forecast' : '/api/kitchen/forecast'

  const load = useCallback(() => { setF(null); api(`${base}?date=${date}`).then(setF).catch((e) => setError(e.message)) }, [base, date])
  useEffect(() => { load() }, [load])

  const apply = async () => {
    if (!confirm('Set the daily plates of limited dishes to this forecast? The 5 AM reset will use them. Dishes made to order are not changed.')) return
    setBusy(true)
    try {
      const r = await api(`/api/admin/insights/forecast/apply?date=${date}`, { method: 'POST' })
      toast(`${r.changed} dish${r.changed === 1 ? '' : 'es'} updated`, 'good')
      load()
    } catch (e) {
      toast(e.message, 'error')
    } finally {
      setBusy(false)
    }
  }

  if (error) return <ErrorNote>{error}</ErrorNote>
  const top = f ? f.dishes.filter((d) => d.recommendedPlates > 0) : []
  const shown = compact ? top.slice(0, 8) : top

  return (
    <div className="space-y-4">
      <div className="no-scrollbar -mx-4 flex gap-2 overflow-x-auto px-4">
        {days.map((d) => (
          <button key={iso(d)} onClick={() => setDate(iso(d))}
            className={`shrink-0 rounded-xl border px-3 py-1.5 text-center text-sm ${date === iso(d) ? 'border-brand bg-brand-soft font-semibold text-brand-ink' : 'border-line bg-card text-ink-2'}`}>
            <span className="block text-[11px] uppercase">{d.toLocaleDateString('en-IN', { weekday: 'short' })}</span>
            {d.getDate()}
          </button>
        ))}
      </div>

      {!f ? <Spinner label="Forecasting…" /> : f.openDaysOfHistory === 0 ? (
        <Card><Empty icon={ChefHat} title="Not enough sales yet">Forecasts start after the first day of orders and get sharper over 3–4 weeks.</Empty></Card>
      ) : f.totalPlates === 0 ? (
        <Card><Empty icon={ChefHat} title="No sales expected">{f.dishes[0]?.basis?.startsWith('Usually closed') ? f.dishes[0].basis + ', so nothing to prepare.' : 'Nothing is expected to sell on this day.'}</Empty></Card>
      ) : (
        <>
          <div className="grid grid-cols-2 gap-3 md:grid-cols-3">
            <Stat label={`Plates to prepare · ${new Date(f.date + 'T00:00:00').toLocaleDateString('en-IN', { weekday: 'long' })}`} value={f.totalPlates} hint="Forecast + 10% safety margin" strong />
            <Stat label="Dishes expected to sell" value={top.length} hint={`of ${f.dishes.length} on the menu`} />
            <Stat label="History used" value={`${f.openDaysOfHistory} days`} hint="Last 8 weeks of open days" />
          </div>

          <Card className="overflow-x-auto">
            <table className="w-full min-w-[520px] text-left text-sm">
              <thead className="border-b border-line text-xs uppercase tracking-wide text-muted">
                <tr><th className="p-3">Dish</th><th className="p-3 text-right">Expected</th><th className="p-3 text-right">Likely range</th><th className="p-3 text-right">Prepare</th>{admin && <th className="p-3 text-right">Daily limit now</th>}<th className="p-3">Based on</th></tr>
              </thead>
              <tbody className="divide-y divide-line">
                {shown.map((d) => (
                  <tr key={d.id}>
                    <td className="p-3 font-medium">{d.emoji} {d.name}</td>
                    <td className="p-3 text-right">{d.predicted}</td>
                    <td className="p-3 text-right text-ink-2">{d.low}–{d.high}</td>
                    <td className="p-3 text-right font-display text-base font-bold">{d.recommendedPlates}</td>
                    {admin && <td className="p-3 text-right text-ink-2">{d.currentDailyStock ?? 'No limit'}</td>}
                    <td className="p-3 text-xs text-muted">{d.basis}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </Card>
          {admin && <Button variant="soft" loading={busy} onClick={apply}>Use as daily plates for limited dishes</Button>}

          <Card className="p-4">
            <h2 className="mb-1 flex items-center gap-2 font-semibold"><ShoppingBasket className="size-4 text-brand" /> Ingredients needed</h2>
            <p className="mb-3 text-xs text-muted">Plates to prepare × each dish's recipe. {admin ? 'Edit recipes in Menu → edit dish.' : ''}</p>
            {f.ingredients.length === 0 ? <p className="text-sm text-muted">No recipes set for these dishes yet.</p> : (
              <ul className="grid gap-x-6 gap-y-1.5 text-sm sm:grid-cols-2">
                {f.ingredients.map((i) => (
                  <li key={i.name + i.unit} className="flex justify-between border-b border-dashed border-line py-1">
                    <span>{i.name}</span><span className="font-semibold">{Number(i.quantity)} {i.unit}</span>
                  </li>
                ))}
              </ul>
            )}
          </Card>
          {!compact && <p className="flex gap-2 text-xs text-muted"><Info className="mt-0.5 size-3.5 shrink-0" /> {f.method}</p>}
        </>
      )}
    </div>
  )
}

function AccuracyTab() {
  const [a, setA] = useState(null)
  const [error, setError] = useState(null)
  useEffect(() => { api('/api/admin/insights/accuracy').then(setA).catch((e) => setError(e.message)) }, [])
  if (error) return <ErrorNote>{error}</ErrorNote>
  if (!a) return <Spinner />
  if (!a.days.length) return <Card><Empty icon={Info} title="No past days to check yet">After a few days of orders you'll see forecast vs actual here.</Empty></Card>
  const data = a.days.map((d) => {
    const dt = new Date(d.date + 'T00:00:00')
    return { label: dt.toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric', month: 'short' }), short: dt.toLocaleDateString('en-IN', { day: 'numeric' }), actual: d.actual, predicted: d.predicted }
  })
  return (
    <div className="space-y-4">
      <div className="grid grid-cols-2 gap-3 md:grid-cols-3">
        <Stat label="Forecast accuracy" value={a.accuracyPct == null ? '—' : `${a.accuracyPct}%`} hint="100% − average % error per day" strong />
        <Stat label="Average miss" value={`${a.mae} plates`} hint="per day, all dishes" />
        <Stat label="Days checked" value={a.days.length} hint="Last 14 open days" />
      </div>
      <Card className="p-4">
        <h2 className="mb-2 font-semibold">Forecast vs what actually sold</h2>
        <ActualVsForecast data={data} label="Plates sold per day compared with the forecast made the evening before" />
        <p className="mt-2 text-xs text-muted">Each day's forecast uses only the data available before that day, exactly as it would have been made at the time.</p>
      </Card>
      {a.note && <p className="flex gap-2 text-xs text-muted"><Info className="mt-0.5 size-3.5 shrink-0" /> {a.note}</p>}
    </div>
  )
}

function PeaksTab() {
  const [p, setP] = useState(null)
  const [error, setError] = useState(null)
  useEffect(() => { api('/api/admin/insights/peaks').then(setP).catch((e) => setError(e.message)) }, [])
  if (error) return <ErrorNote>{error}</ErrorNote>
  if (!p) return <Spinner />
  return (
    <Card className="p-4">
      <h2 className="font-semibold">Average orders per hour</h2>
      <p className="mb-3 text-sm text-muted">{p.busiest ? <>Busiest: <b className="text-ink">{p.busiest}</b> · </> : null}last 4 weeks ({p.openDays} open days)</p>
      <Heatmap rows={p.weekdays} cols={p.hours.map((h) => `${h % 12 || 12}${h < 12 ? 'a' : 'p'}`)} values={p.avgOrders}
        format={(v) => `${v} orders on average`} label="Average orders per hour for each day of the week" />
    </Card>
  )
}

function SellersTab() {
  const [s, setS] = useState(null)
  const [error, setError] = useState(null)
  useEffect(() => { api('/api/admin/insights/sellers').then(setS).catch((e) => setError(e.message)) }, [])
  if (error) return <ErrorNote>{error}</ErrorNote>
  if (!s) return <Spinner />
  const List = ({ title, rows, sub }) => (
    <Card className="p-4">
      <h2 className="font-semibold">{title}</h2>
      <p className="mb-2 text-xs text-muted">{sub}</p>
      <ul className="divide-y divide-line">
        {rows.map((r) => (
          <li key={r.id} className="flex items-center justify-between gap-3 py-2 text-sm">
            <span className="min-w-0 truncate">{r.emoji} {r.name}</span>
            <span className="flex shrink-0 items-center gap-3">
              <span className="text-ink-2">{rupees(r.revenue)}</span>
              <span className="w-16 text-right font-semibold">{r.qty} sold</span>
              <span className={`flex w-14 items-center justify-end gap-0.5 text-xs font-semibold ${r.changePct == null ? 'text-muted' : r.changePct >= 0 ? 'text-good' : 'text-bad'}`}>
                {r.changePct == null ? 'new' : <>{r.changePct >= 0 ? <ArrowUp className="size-3" /> : <ArrowDown className="size-3" />}{Math.abs(r.changePct)}%</>}
              </span>
            </span>
          </li>
        ))}
      </ul>
    </Card>
  )
  return (
    <div className="grid gap-4 md:grid-cols-2">
      <List title="Best sellers" rows={s.best} sub="Last 30 days · change vs the 30 days before" />
      <List title="Least sellers" rows={s.least} sub="Consider a price change, a combo, or removing them" />
    </div>
  )
}
