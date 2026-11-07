import { useCallback, useRef, useState } from 'react'
import { useLocation } from 'react-router-dom'
import { CheckCircle2, QrCode, XCircle } from 'lucide-react'
import { api, rupees } from '../../api'
import { QrScanner } from '../../components/Qr'
import { Button, Card, PageTitle } from '../../components/ui'

export default function Verify() {
  const { state } = useLocation()
  const [code, setCode] = useState(state?.code || '')
  const [otp, setOtp] = useState('')
  const [scan, setScan] = useState(false)
  const [result, setResult] = useState(null)
  const [busy, setBusy] = useState(false)
  const otpRef = useRef(null)

  const check = useCallback(async (c, o) => {
    setBusy(true)
    setResult(null)
    try {
      const res = await api('/api/kitchen/verify', { method: 'POST', body: { code: c, otp: o } })
      setResult({ ok: true, ...res })
      setCode('')
      setOtp('')
    } catch (e) {
      setResult({ ok: false, message: e.message, order: e.data?.order })
      setOtp('')
      otpRef.current?.focus()
    } finally {
      setBusy(false)
    }
  }, [])

  const onScan = useCallback((hit) => {
    setScan(false)
    setCode(hit.code)
    setOtp(hit.otp)
    check(hit.code, hit.otp)
  }, [check])

  const manual = async () => {
    if (!result?.order || !confirm(`Hand over ${result.order.code} without the code? Only do this after checking the student's pass.`)) return
    setBusy(true)
    try {
      const order = await api(`/api/kitchen/orders/${result.order.id}/status`, { method: 'POST', body: { status: 'COLLECTED' } })
      setResult({ ok: true, message: `Handed over ${order.code} to ${order.customerName}.`, order })
    } catch (e) {
      setResult({ ok: false, message: e.message, order: result.order })
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <PageTitle title="Hand over" sub="Scan the student's QR, or type the order number and 4-digit code." />

      <div className="grid gap-4 md:grid-cols-2">
        <Card className="p-4">
          {scan ? (
            <QrScanner onResult={onScan} onClose={() => setScan(false)} />
          ) : (
            <form onSubmit={(e) => { e.preventDefault(); check(code, otp) }} className="space-y-4">
              <Button type="button" variant="soft" size="lg" className="w-full" onClick={() => setScan(true)}>
                <QrCode className="size-5" /> Scan pickup QR
              </Button>
              <div className="flex items-center gap-3 text-xs font-semibold uppercase text-muted"><span className="h-px flex-1 bg-line" />or type<span className="h-px flex-1 bg-line" /></div>
              <label className="block">
                <span className="mb-1.5 block text-sm font-medium text-ink-2">Order number</span>
                <input value={code} onChange={(e) => setCode(e.target.value.toUpperCase())} required placeholder="T-017" autoComplete="off"
                  className="h-14 w-full rounded-xl border border-line bg-card px-4 font-display text-2xl font-bold tracking-wide outline-none focus:border-brand" />
              </label>
              <label className="block">
                <span className="mb-1.5 block text-sm font-medium text-ink-2">Pickup code</span>
                <input ref={otpRef} value={otp} onChange={(e) => setOtp(e.target.value.replace(/\D/g, '').slice(0, 4))} required
                  inputMode="numeric" pattern="\d{4}" placeholder="••••" autoComplete="off"
                  className="h-14 w-full rounded-xl border border-line bg-card px-4 text-center font-display text-3xl font-bold tracking-[0.5em] outline-none focus:border-brand" />
              </label>
              <Button type="submit" size="lg" className="w-full" loading={busy} disabled={otp.length !== 4 || !code}>Verify &amp; hand over</Button>
            </form>
          )}
        </Card>

        <div aria-live="polite">
          {result && (
            <Card className={`animate-pop p-5 ${result.ok ? 'border-good bg-good-soft' : 'border-bad bg-bad-soft'}`}>
              <div className="flex items-start gap-3">
                {result.ok ? <CheckCircle2 className="size-10 shrink-0 text-good" /> : <XCircle className="size-10 shrink-0 text-bad" />}
                <div className="min-w-0 flex-1">
                  <p className={`text-lg font-bold ${result.ok ? 'text-good' : 'text-bad'}`}>{result.ok ? 'Verified' : "Don't hand over yet"}</p>
                  <p className="text-sm text-ink">{result.message}</p>
                </div>
              </div>
              {result.order && (
                <div className="mt-4 rounded-xl bg-card p-3">
                  <p className="font-display text-xl font-bold">{result.order.code} <span className="text-base font-medium text-ink-2">· {result.order.customerName}</span></p>
                  <ul className="mt-1 text-sm">{result.order.items.map((i) => <li key={i.menuItemId}><b>{i.quantity}×</b> {i.name}</li>)}</ul>
                  {result.order.paymentMethod === 'CASH' && <p className="mt-2 font-semibold text-bad">Collect {rupees(result.order.total)} cash / UPI</p>}
                </div>
              )}
              {!result.ok && result.order?.status === 'READY' && (
                <Button variant="outline" size="sm" className="mt-3" loading={busy} onClick={manual}>Hand over manually</Button>
              )}
            </Card>
          )}
        </div>
      </div>
    </>
  )
}
