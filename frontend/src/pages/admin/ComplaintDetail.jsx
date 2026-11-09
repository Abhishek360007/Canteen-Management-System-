import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, Check, X } from 'lucide-react'
import { api, dateOf, rupees, timeOf } from '../../api'
import { useLive } from '../../context/LiveContext'
import { ComplaintStatus, ReplyBox, Thread, categoryLabel } from '../../components/Feedback'
import { Button, Card, ErrorNote, Modal, Spinner, StatusBadge, useToast } from '../../components/ui'

const QUICK_REPLIES = [
  'Sorry about this! Could you tell us roughly what time you collected it?',
  "Thanks for telling us. We've passed this on to the kitchen.",
]
const REJECT_REASONS = ['The order was checked and handed over complete', 'Reported too long after pickup', 'Not something the canteen can refund']

/** Admin: one complaint, its order, the conversation, and resolve (with an optional refund) or close. */
export default function ComplaintDetail() {
  const { id } = useParams()
  const toast = useToast()
  const [c, setC] = useState(null)
  const [error, setError] = useState(null)
  const [mode, setMode] = useState(null) // 'resolve' | 'reject'
  const [refund, setRefund] = useState('')
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)

  const load = useCallback(() => api(`/api/admin/complaints/${id}`).then(setC).catch((e) => setError(e.message)), [id])
  useEffect(() => { load() }, [load])
  useLive('complaint', (ev) => { if (String(ev?.id) === String(id)) load() })

  const reply = async (text) => {
    try {
      setC(await api(`/api/admin/complaints/${id}/messages`, { method: 'POST', body: { text } }))
    } catch (e) {
      toast(e.message, 'error')
      throw e
    }
  }

  const close = async () => {
    const amount = Number(refund || 0)
    if (mode === 'resolve' && amount > 0 && !confirm(`Refund ${rupees(amount)} to ${c.studentName}'s wallet? This can't be undone.`)) return
    setBusy(true)
    try {
      const body = mode === 'resolve' ? { refund: amount, note: note.trim() || null } : { note: note.trim() }
      setC(await api(`/api/admin/complaints/${id}/${mode}`, { method: 'POST', body }))
      toast(mode === 'resolve' ? (amount > 0 ? `${rupees(amount)} refunded` : 'Marked resolved') : 'Complaint closed', 'good')
      setMode(null)
    } catch (e) {
      toast(e.message, 'error')
      load()
    } finally {
      setBusy(false)
    }
  }

  if (error) return <ErrorNote>{error}</ErrorNote>
  if (!c) return <Spinner />

  const max = Number(c.maxRefund)
  const amount = Number(refund || 0)
  const refundOk = !Number.isNaN(amount) && amount >= 0 && amount <= max

  return (
    <>
      <Link to="/admin/complaints" className="mb-3 inline-flex items-center gap-1 text-sm font-medium text-muted hover:text-ink">
        <ArrowLeft className="size-4" /> All complaints
      </Link>

      <div className="mb-4 flex flex-wrap items-center gap-2">
        <h1 className="text-2xl font-bold tracking-tight">{categoryLabel(c.category)}</h1>
        <ComplaintStatus status={c.status} lastFromStaff={c.lastFromStaff} staffView />
      </div>

      <div className="grid gap-4 md:grid-cols-[1fr_18rem]">
        <Card className="p-4">
          <Thread c={c} staffView />
          {c.status === 'OPEN' && (
            <>
              <div className="mt-4 flex flex-wrap gap-1.5">
                {QUICK_REPLIES.map((q) => (
                  <button key={q} onClick={() => reply(q).catch(() => {})} className="rounded-full border border-line px-2.5 py-1 text-xs text-ink-2 hover:bg-card-2">{q}</button>
                ))}
              </div>
              <ReplyBox onSend={reply} placeholder={`Reply to ${c.studentName.split(' ')[0]}…`} />
              <div className="mt-4 flex gap-2 border-t border-line pt-4">
                <Button variant="danger" className="flex-1" onClick={() => { setMode('reject'); setNote('') }}><X className="size-4" /> Close, no refund</Button>
                <Button variant="good" className="flex-1" onClick={() => { setMode('resolve'); setRefund(''); setNote('') }}><Check className="size-4" /> Resolve</Button>
              </div>
            </>
          )}
        </Card>

        <div className="space-y-4">
          <Card className="p-4 text-sm">
            <h2 className="mb-2 font-semibold">Student</h2>
            <p>{c.studentName}</p>
            <p className="text-muted">{c.studentEmail}{c.studentRollNo ? ` · ${c.studentRollNo}` : ''}</p>
          </Card>
          <Card className="p-4 text-sm">
            <div className="mb-2 flex items-center justify-between">
              <h2 className="font-semibold">Order {c.orderCode}</h2>
              <StatusBadge status={c.orderStatus} />
            </div>
            <p className="mb-2 text-xs text-muted">{c.canteenName ? `${c.canteenName} · ` : ''}{dateOf(c.orderDate)} · reported {dateOf(c.createdAt)}, {timeOf(c.createdAt)}</p>
            <ul className="space-y-1">
              {c.items?.map((i) => (
                <li key={i.menuItemId} className="flex justify-between gap-2"><span>{i.quantity} × {i.name}</span><span className="text-ink-2">{rupees(i.lineTotal)}</span></li>
              ))}
            </ul>
            <div className="mt-2 flex justify-between border-t border-line pt-2 font-semibold">
              <span>{c.paymentMethod === 'WALLET' ? 'Paid from wallet' : 'Cash at counter'}</span><span>{rupees(c.orderTotal)}</span>
            </div>
            {max === 0 && <p className="mt-2 text-xs text-muted">Not collected and paid in cash: nothing was paid, so there is nothing to refund.</p>}
          </Card>
        </div>
      </div>

      <Modal open={mode === 'resolve'} onClose={() => setMode(null)} title="Resolve complaint">
        {max > 0 ? (
          <>
            <p className="mb-2 text-sm text-ink-2">Refund to {c.studentName}'s wallet (optional, up to {rupees(max)} paid).</p>
            <div className="mb-2 flex flex-wrap gap-2">
              {c.items?.map((i) => (
                <button key={i.menuItemId} onClick={() => setRefund(String(Math.min(max, Number(i.lineTotal))))}
                  className="rounded-full border border-line px-3 py-1 text-sm hover:bg-card-2">{i.name} · {rupees(Math.min(max, Number(i.lineTotal)))}</button>
              ))}
              <button onClick={() => setRefund(String(max))} className="rounded-full border border-line px-3 py-1 text-sm hover:bg-card-2">Full order · {rupees(max)}</button>
              <button onClick={() => setRefund('')} className="rounded-full border border-line px-3 py-1 text-sm hover:bg-card-2">No refund</button>
            </div>
            <label className="mb-3 block">
              <span className="sr-only">Refund amount</span>
              <div className="flex h-11 items-center rounded-xl border border-line bg-card px-3 focus-within:border-brand">
                <span className="text-muted">₹</span>
                <input inputMode="decimal" value={refund} onChange={(e) => setRefund(e.target.value.replace(/[^\d.]/g, ''))} placeholder="0"
                  className="ml-1 w-full bg-transparent outline-none" aria-label="Refund amount" />
              </div>
              {!refundOk && <span className="mt-1 block text-xs text-bad">Up to {rupees(max)}</span>}
            </label>
          </>
        ) : <p className="mb-3 text-sm text-ink-2">Nothing was paid for this order, so it can be resolved without a refund.</p>}
        <textarea value={note} onChange={(e) => setNote(e.target.value)} maxLength={300} rows={3}
          placeholder="Message to the student, e.g. Sorry! We've refunded the thali and spoken to the kitchen."
          aria-label="Message to the student" className="mb-4 w-full rounded-xl border border-line bg-card px-3 py-2 outline-none placeholder:text-muted focus:border-brand" />
        <Button variant="good" className="w-full" loading={busy} disabled={!refundOk} onClick={close}>
          {amount > 0 ? `Refund ${rupees(amount)} & resolve` : 'Resolve without refund'}
        </Button>
      </Modal>

      <Modal open={mode === 'reject'} onClose={() => setMode(null)} title="Close without a refund?">
        <p className="mb-3 text-sm text-ink-2">{c.studentName} will see this reason.</p>
        <div className="mb-3 flex flex-wrap gap-2">
          {REJECT_REASONS.map((r) => (
            <button key={r} onClick={() => setNote(r)} className={`rounded-full border px-3 py-1 text-sm ${note === r ? 'border-brand bg-brand-soft text-brand-ink' : 'border-line'}`}>{r}</button>
          ))}
        </div>
        <textarea value={note} onChange={(e) => setNote(e.target.value)} maxLength={300} rows={3} placeholder="Reason"
          aria-label="Reason" className="mb-4 w-full rounded-xl border border-line bg-card px-3 py-2 outline-none placeholder:text-muted focus:border-brand" />
        <Button variant="danger" className="w-full" loading={busy} disabled={!note.trim()} onClick={close}>Close complaint</Button>
      </Modal>
    </>
  )
}
