import { useCallback, useEffect, useState } from 'react'
import { Download, ListOrdered } from 'lucide-react'
import { api, dateOf, download, rupees, timeOf } from '../../api'
import { Button, Card, Empty, ErrorNote, PageTitle, Select, Spinner, StatusBadge, useToast } from '../../components/ui'

const iso = (d) => new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10)
const STATUSES = ['', 'PLACED', 'PREPARING', 'READY', 'COLLECTED', 'CANCELLED', 'NO_SHOW']

export default function AdminOrders() {
  const toast = useToast()
  const today = iso(new Date())
  const [f, setF] = useState({ from: iso(new Date(Date.now() - 6 * 864e5)), to: today, status: '' })
  const [orders, setOrders] = useState(null)
  const [error, setError] = useState(null)
  const qs = `from=${f.from}&to=${f.to}${f.status ? `&status=${f.status}` : ''}`

  const load = useCallback(() => {
    setOrders(null)
    setError(null)
    api(`/api/admin/orders?${qs}`).then(setOrders).catch((e) => setError(e.message))
  }, [qs])
  useEffect(() => { load() }, [load])

  const total = (orders || []).filter((o) => !['CANCELLED'].includes(o.status)).reduce((s, o) => s + Number(o.total), 0)

  return (
    <>
      <PageTitle title="All orders"
        right={<Button variant="outline" size="sm" onClick={() => download(`/api/admin/orders.csv?${qs}`, 'orders.csv').catch((e) => toast(e.message, 'error'))}>
          <Download className="size-4" /> CSV
        </Button>} />

      <Card className="mb-4 grid grid-cols-2 gap-3 p-3 sm:grid-cols-3">
        <label className="block text-sm"><span className="mb-1 block font-medium text-ink-2">From</span>
          <input type="date" value={f.from} max={f.to} onChange={(e) => setF({ ...f, from: e.target.value })}
            className="h-11 w-full rounded-xl border border-line bg-card px-3 outline-none focus:border-brand" /></label>
        <label className="block text-sm"><span className="mb-1 block font-medium text-ink-2">To</span>
          <input type="date" value={f.to} min={f.from} max={today} onChange={(e) => setF({ ...f, to: e.target.value })}
            className="h-11 w-full rounded-xl border border-line bg-card px-3 outline-none focus:border-brand" /></label>
        <Select label="Status" value={f.status} onChange={(e) => setF({ ...f, status: e.target.value })} className="col-span-2 sm:col-span-1">
          {STATUSES.map((s) => <option key={s} value={s}>{s ? s.replace('_', ' ').toLowerCase() : 'All statuses'}</option>)}
        </Select>
      </Card>

      <ErrorNote>{error}</ErrorNote>
      {!orders && !error && <Spinner />}
      {orders && orders.length === 0 && <Empty icon={ListOrdered} title="No orders in this range" />}
      {orders && orders.length > 0 && (
        <>
          <p className="mb-2 text-sm text-muted">{orders.length} orders{orders.length === 500 ? ' (first 500 shown, use CSV for all)' : ''} · {rupees(total)} excluding cancelled</p>
          <Card className="overflow-x-auto">
            <table className="w-full min-w-[640px] text-left text-sm">
              <thead className="border-b border-line text-xs uppercase tracking-wide text-muted">
                <tr><th className="p-3">Order</th><th className="p-3">When</th><th className="p-3">Student</th><th className="p-3">Items</th><th className="p-3 text-right">Total</th><th className="p-3">Status</th></tr>
              </thead>
              <tbody className="divide-y divide-line">
                {orders.map((o) => (
                  <tr key={o.id} className="align-top hover:bg-card-2">
                    <td className="p-3 font-semibold">{o.code}</td>
                    <td className="whitespace-nowrap p-3 text-ink-2">{dateOf(o.createdAt)}, {timeOf(o.createdAt)}</td>
                    <td className="p-3">{o.customerName}<div className="text-xs text-muted">{o.customerRollNo}</div></td>
                    <td className="p-3 text-ink-2">{o.items.map((i) => `${i.quantity}× ${i.name}`).join(', ')}</td>
                    <td className="whitespace-nowrap p-3 text-right font-semibold">{rupees(o.total)}<div className="text-xs font-normal text-muted">{o.paymentMethod === 'WALLET' ? 'Wallet' : 'Cash'}</div></td>
                    <td className="p-3"><StatusBadge status={o.status} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </Card>
        </>
      )}
    </>
  )
}
