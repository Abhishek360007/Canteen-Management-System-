import { useCallback, useEffect, useState } from 'react'
import { CheckCircle2, Clock, Smartphone, XCircle } from 'lucide-react'
import { api, dateOf, rupees, timeOf } from '../api'
import { useAuth } from '../context/AuthContext'
import { useLive } from '../context/LiveContext'
import { QrImage } from './Qr'
import { Button, Card, ErrorNote, useToast } from './ui'

const STATUS = {
  PENDING: { icon: Clock, cls: 'bg-warn-soft text-warn', label: 'Waiting for approval' },
  APPROVED: { icon: CheckCircle2, cls: 'bg-good-soft text-good', label: 'Added to wallet' },
  REJECTED: { icon: XCircle, cls: 'bg-bad-soft text-bad', label: 'Rejected' },
}

/** Standard UPI payment link: opens GPay / PhonePe / Paytm with the amount filled in. */
function upiLink(upiId, name, amount, note) {
  const p = new URLSearchParams({ pa: upiId, pn: name || "Tula's Canteen", am: Number(amount).toFixed(2), cu: 'INR', tn: note })
  return `upi://pay?${p.toString()}`
}

/**
 * Wallet top-up by UPI: 1) pay the canteen's UPI ID, 2) paste the UPI reference, 3) admin approves.
 * Hidden when the canteen hasn't set a UPI ID.
 */
export default function TopupCard({ canteen }) {
  const { user } = useAuth()
  const toast = useToast()
  const [amount, setAmount] = useState('200')
  const [ref, setRef] = useState('')
  const [step, setStep] = useState(1)
  const [mine, setMine] = useState([])
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  const load = useCallback(() => api('/api/wallet/topups').then(setMine).catch(() => {}), [])
  useEffect(() => { load() }, [load])
  useLive('topup', load)

  if (!canteen?.upiId) return null
  const valid = Number(amount) >= 10 && Number(amount) <= 5000
  const link = upiLink(canteen.upiId, canteen.upiName, amount || 0, `Wallet ${user.rollNo || user.name}`.slice(0, 40))

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await api('/api/wallet/topups', { method: 'POST', body: { amount: Number(amount), upiRef: ref.trim() } })
      toast("Sent! We'll add it to your wallet once the canteen confirms the payment.", 'good')
      setRef('')
      setStep(1)
      load()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card className="mt-4 p-4">
      <h2 className="font-semibold">Add money with UPI</h2>
      <p className="mt-0.5 text-sm text-muted">Pay the canteen from any UPI app, then send us the payment reference.</p>

      {step === 1 ? (
        <div className="mt-4 space-y-3">
          <div className="flex gap-2">
            {[100, 200, 500].map((a) => (
              <button key={a} type="button" onClick={() => setAmount(String(a))}
                className={`flex-1 rounded-xl border py-2 text-sm font-semibold ${amount === String(a) ? 'border-brand bg-brand-soft text-brand-ink' : 'border-line'}`}>₹{a}</button>
            ))}
            <input type="number" min={10} max={5000} value={amount} onChange={(e) => setAmount(e.target.value)} aria-label="Other amount"
              className="w-24 rounded-xl border border-line bg-card px-3 text-center font-semibold outline-none focus:border-brand" />
          </div>
          <div className="flex flex-col items-center gap-3 rounded-2xl bg-card-2 p-4 sm:flex-row sm:items-start">
            <div className="hidden sm:block"><QrImage value={link} size={128} /></div>
            <div className="text-center text-sm sm:text-left">
              <p className="text-muted">Pay to UPI ID</p>
              <p className="font-display text-lg font-bold">{canteen.upiId}</p>
              {canteen.upiName && <p className="text-ink-2">{canteen.upiName}</p>}
              <p className="mt-2 hidden text-xs text-muted sm:block">Scan with any UPI app on your phone.</p>
            </div>
          </div>
          <a href={valid ? link : undefined} aria-disabled={!valid}
            className={`flex h-11 w-full items-center justify-center gap-2 rounded-xl bg-brand font-semibold text-white sm:hidden ${valid ? '' : 'pointer-events-none opacity-50'}`}>
            <Smartphone className="size-4" /> Pay {rupees(amount || 0)} in UPI app
          </a>
          <Button variant="soft" className="w-full" disabled={!valid} onClick={() => setStep(2)}>I've paid, next</Button>
        </div>
      ) : (
        <form onSubmit={submit} className="mt-4 space-y-3">
          <p className="rounded-xl bg-card-2 p-3 text-sm">Amount paid: <b>{rupees(amount)}</b> to <b>{canteen.upiId}</b></p>
          <label className="block">
            <span className="mb-1.5 block text-sm font-medium text-ink-2">UPI reference / UTR number</span>
            <input value={ref} onChange={(e) => setRef(e.target.value.replace(/[^A-Za-z0-9]/g, '').slice(0, 30))} required minLength={6}
              placeholder="e.g. 412345678901" inputMode="numeric" autoComplete="off"
              className="h-12 w-full rounded-xl border border-line bg-card px-3 font-mono text-lg tracking-wider outline-none focus:border-brand" />
            <span className="mt-1 block text-xs text-muted">Find it in your UPI app under the payment details (usually 12 digits).</span>
          </label>
          <ErrorNote>{error}</ErrorNote>
          <div className="flex gap-2">
            <Button type="button" variant="outline" onClick={() => setStep(1)}>Back</Button>
            <Button type="submit" className="flex-1" loading={busy} disabled={ref.length < 6}>Send for approval</Button>
          </div>
        </form>
      )}

      {mine.length > 0 && (
        <ul className="mt-4 divide-y divide-line border-t border-line">
          {mine.slice(0, 5).map((t) => {
            const s = STATUS[t.status]
            const Icon = s.icon
            return (
              <li key={t.id} className="flex items-center gap-3 py-2.5 text-sm">
                <span className={`grid size-8 place-items-center rounded-lg ${s.cls}`}><Icon className="size-4" /></span>
                <div className="min-w-0 flex-1">
                  <p className="font-medium">{rupees(t.amount)} · <span className="font-mono text-xs text-muted">{t.upiRef}</span></p>
                  <p className="truncate text-xs text-muted">{s.label}{t.reason ? ` · ${t.reason}` : ''} · {dateOf(t.createdAt)}, {timeOf(t.createdAt)}</p>
                </div>
              </li>
            )
          })}
        </ul>
      )}
    </Card>
  )
}
