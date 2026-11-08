import { useState } from 'react'
import { CreditCard, ShieldCheck } from 'lucide-react'
import { rupees } from '../api'
import { payOnline } from '../razorpay'
import { Button, Card, ErrorNote, useToast } from './ui'

/** "Add money online" (Razorpay: UPI, cards, netbanking, wallets). Credited instantly once Razorpay confirms. */
export default function OnlineTopup({ config, onDone }) {
  const toast = useToast()
  const [amount, setAmount] = useState('200')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  if (!config?.online) return null

  const value = Number(amount)
  const valid = value >= config.minTopup && value <= config.maxTopup

  const pay = async () => {
    setBusy(true)
    setError(null)
    try {
      const r = await payOnline({ amount: value })
      if (r) {
        toast(`${rupees(r.amount)} added to your wallet`, 'good')
        onDone?.()
      }
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card className="mt-4 p-4">
      <div className="flex items-start justify-between gap-2">
        <div>
          <h2 className="flex items-center gap-2 font-semibold"><CreditCard className="size-4 text-brand" /> Add money online</h2>
          <p className="mt-0.5 text-sm text-muted">UPI, cards, netbanking or wallets. Added instantly.</p>
        </div>
        {config.mode === 'TEST' && <span className="shrink-0 rounded-full bg-warn-soft px-2 py-0.5 text-xs font-semibold text-warn">Test mode</span>}
      </div>
      <div className="mt-3 flex gap-2">
        {[100, 200, 500].map((a) => (
          <button key={a} type="button" onClick={() => setAmount(String(a))}
            className={`flex-1 rounded-xl border py-2 text-sm font-semibold ${amount === String(a) ? 'border-brand bg-brand-soft text-brand-ink' : 'border-line'}`}>₹{a}</button>
        ))}
        <input type="number" min={config.minTopup} max={config.maxTopup} value={amount} onChange={(e) => setAmount(e.target.value)} aria-label="Other amount"
          className="w-24 rounded-xl border border-line bg-card px-3 text-center font-semibold outline-none focus:border-brand" />
      </div>
      {!valid && amount !== '' && <p className="mt-2 text-xs text-bad">Between ₹{config.minTopup} and ₹{config.maxTopup}.</p>}
      <ErrorNote>{error}</ErrorNote>
      <Button className="mt-3 w-full" loading={busy} disabled={!valid} onClick={pay}>Pay {valid ? rupees(value) : ''} securely</Button>
      <p className="mt-2 flex items-center justify-center gap-1 text-xs text-muted"><ShieldCheck className="size-3.5" /> Payments by Razorpay. Your card or UPI details never reach Tula's Canteen.</p>
      {config.mode === 'TEST' && <p className="mt-1 text-center text-xs text-warn">Test mode: no real money is charged. Use Razorpay's test UPI ID success@razorpay.</p>}
    </Card>
  )
}
