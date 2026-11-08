import { useCallback, useEffect, useState } from 'react'
import { CheckCircle2, Copy, CreditCard, XCircle } from 'lucide-react'
import { api, dateOf, rupees, timeOf } from '../../api'
import { useLive } from '../../context/LiveContext'
import { Button, Card, ErrorNote, Input, PageTitle, Spinner, Toggle, useToast } from '../../components/ui'

const STATUS = { PAID: 'bg-good-soft text-good', CREATED: 'bg-card-2 text-ink-2', FAILED: 'bg-bad-soft text-bad' }
const LABEL = { PAID: 'Paid', CREATED: 'Not completed', FAILED: 'Failed / closed' }

function Check({ ok, children }) {
  const Icon = ok ? CheckCircle2 : XCircle
  return <li className="flex items-start gap-2 text-sm"><Icon className={`mt-0.5 size-4 shrink-0 ${ok ? 'text-good' : 'text-bad'}`} /><span>{children}</span></li>
}

/** Admin: online payment (Razorpay) settings, setup status and the list of payments for reconciliation. */
export default function Payments() {
  const toast = useToast()
  const [s, setS] = useState(null)
  const [form, setForm] = useState(null)
  const [list, setList] = useState(null)
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)
  const webhookUrl = `${window.location.origin}/api/payments/razorpay/webhook`

  const load = useCallback(() => {
    api('/api/admin/payments/settings').then((r) => { setS(r); setForm({ onlineEnabled: r.onlineEnabled, minTopup: r.minTopup, maxTopup: r.maxTopup }) })
      .catch((e) => setError(e.message))
    api('/api/admin/payments').then(setList).catch(() => {})
  }, [])
  useEffect(() => { load() }, [load])
  useLive('topup', load)

  const save = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      setS(await api('/api/admin/payments/settings', { method: 'PUT', body: { ...form, minTopup: Number(form.minTopup), maxTopup: Number(form.maxTopup) } }))
      toast('Payment settings saved', 'good')
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  if (!s || !form) return error ? <ErrorNote>{error}</ErrorNote> : <Spinner />
  const paidToday = (list || []).filter((p) => p.status === 'PAID' && new Date(p.paidAt).toDateString() === new Date().toDateString())

  return (
    <>
      <PageTitle title="Payments" sub="Online payment with Razorpay goes straight into students' wallets." />

      <div className="grid gap-4 md:grid-cols-2">
        <Card className="p-4">
          <div className="flex items-center justify-between">
            <h2 className="flex items-center gap-2 font-semibold"><CreditCard className="size-4 text-brand" /> Razorpay</h2>
            <span className={`rounded-full px-2.5 py-0.5 text-xs font-bold ${s.mode === 'LIVE' ? 'bg-good-soft text-good' : s.mode === 'TEST' ? 'bg-warn-soft text-warn' : 'bg-card-2 text-muted'}`}>
              {s.mode === 'LIVE' ? 'LIVE · real money' : s.mode === 'TEST' ? 'TEST · no real money' : 'NOT SET UP'}
            </span>
          </div>
          <ul className="mt-3 space-y-1.5">
            <Check ok={s.keysConfigured}>API keys {s.keysConfigured ? 'set on the server' : <>missing: add <code>RAZORPAY_KEY_ID</code> and <code>RAZORPAY_KEY_SECRET</code> in Render → Environment</>}</Check>
            <Check ok={s.webhookConfigured}>Webhook secret {s.webhookConfigured ? 'set' : <>missing: add <code>RAZORPAY_WEBHOOK_SECRET</code> (credits students even if they close the app mid-payment)</>}</Check>
            <Check ok={s.onlineEnabled && s.keysConfigured}>Students can pay online {s.onlineEnabled ? '' : '(switched off below)'}</Check>
          </ul>
          <div className="mt-3 rounded-xl bg-card-2 p-3 text-xs text-ink-2">
            <p className="font-semibold text-ink">Webhook (Razorpay Dashboard → Settings → Webhooks → Add)</p>
            <div className="mt-1.5 flex items-center gap-2">
              <code className="min-w-0 flex-1 truncate rounded-lg bg-card px-2 py-1.5">{webhookUrl}</code>
              <button onClick={() => { navigator.clipboard?.writeText(webhookUrl); toast('Copied', 'good') }} className="rounded-lg p-1.5 hover:bg-card" aria-label="Copy webhook URL"><Copy className="size-4" /></button>
            </div>
            <p className="mt-1.5">Events: <b>payment.captured</b>, <b>payment.failed</b>, <b>order.paid</b>. Use the same secret as <code>RAZORPAY_WEBHOOK_SECRET</code>.</p>
          </div>
        </Card>

        <Card className="p-4">
          <form onSubmit={save} className="space-y-3">
            <h2 className="font-semibold">Settings</h2>
            <div className="flex items-center justify-between rounded-xl bg-card-2 p-3">
              <span><span className="block text-sm font-medium">Online payment</span><span className="block text-xs text-muted">Wallet top-ups and paying the difference at checkout</span></span>
              <Toggle checked={form.onlineEnabled} onChange={(v) => setForm({ ...form, onlineEnabled: v })} label="Online payment" />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <Input label="Min top-up (₹)" type="number" min={1} max={100000} required value={form.minTopup} onChange={(e) => setForm({ ...form, minTopup: e.target.value })} />
              <Input label="Max top-up (₹)" type="number" min={1} max={100000} required value={form.maxTopup} onChange={(e) => setForm({ ...form, maxTopup: e.target.value })} />
            </div>
            <p className="text-xs text-muted">Cash at the counter is set per canteen (More → Canteens). Manual UPI requests use the UPI ID in Settings.</p>
            <ErrorNote>{error}</ErrorNote>
            <Button type="submit" loading={busy}>Save</Button>
          </form>
        </Card>
      </div>

      <div className="mb-2 mt-6 flex items-baseline justify-between">
        <h2 className="text-sm font-semibold uppercase tracking-wide text-muted">Recent online payments</h2>
        <span className="text-sm text-muted">Today: {rupees(paidToday.reduce((t, p) => t + Number(p.amount), 0))} from {paidToday.length}</span>
      </div>
      {!list ? <Spinner /> : list.length === 0 ? <Card className="p-6 text-center text-sm text-muted">No online payments yet.</Card> : (
        <Card className="overflow-x-auto">
          <table className="w-full min-w-[640px] text-left text-sm">
            <thead className="border-b border-line text-xs uppercase tracking-wide text-muted">
              <tr><th className="p-3">When</th><th className="p-3">Student</th><th className="p-3 text-right">Amount</th><th className="p-3">Status</th><th className="p-3">Razorpay</th></tr>
            </thead>
            <tbody className="divide-y divide-line">
              {list.map((p) => (
                <tr key={p.id}>
                  <td className="whitespace-nowrap p-3 text-ink-2">{dateOf(p.createdAt)}, {timeOf(p.createdAt)}</td>
                  <td className="p-3">{p.studentName}<div className="text-xs text-muted">{p.studentEmail}</div></td>
                  <td className="p-3 text-right font-semibold">{rupees(p.amount)}<div className="text-xs font-normal text-muted">{p.purpose === 'checkout' ? 'at checkout' : 'wallet'}</div></td>
                  <td className="p-3"><span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${STATUS[p.status]}`}>{LABEL[p.status]}</span>
                    {p.confirmedBy && <div className="mt-0.5 text-xs text-muted">via {p.confirmedBy === 'webhook' ? 'webhook' : 'app'}</div>}</td>
                  <td className="p-3 font-mono text-xs text-ink-2">{p.razorpayPaymentId || p.razorpayOrderId}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}
    </>
  )
}
