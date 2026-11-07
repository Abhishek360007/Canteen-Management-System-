import { useCallback, useEffect, useState } from 'react'
import { Check, IndianRupee, X } from 'lucide-react'
import { api, dateOf, rupees, timeOf } from '../../api'
import { useLive } from '../../context/LiveContext'
import { Button, Card, Empty, ErrorNote, Modal, PageTitle, Spinner, useToast } from '../../components/ui'

const TABS = [['PENDING', 'Waiting'], ['', 'All']]

/** Admin: match each request with the canteen's UPI app, then approve (credits the wallet) or reject. */
export default function Topups() {
  const toast = useToast()
  const [tab, setTab] = useState('PENDING')
  const [list, setList] = useState(null)
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(null)
  const [rejecting, setRejecting] = useState(null)
  const [reason, setReason] = useState('')

  const load = useCallback(() =>
    api(`/api/admin/topups${tab ? `?status=${tab}` : ''}`).then(setList).catch((e) => setError(e.message)), [tab])
  useEffect(() => { setList(null); load() }, [load])
  useLive('topup', load)

  const approve = async (t) => {
    if (!confirm(`Approve ${rupees(t.amount)} for ${t.studentName}?\n\nOnly approve if UPI ref ${t.upiRef} shows a received payment of ${rupees(t.amount)} in the canteen's UPI app.`)) return
    setBusy(t.id)
    try {
      await api(`/api/admin/topups/${t.id}/approve`, { method: 'POST' })
      toast(`${rupees(t.amount)} added to ${t.studentName}'s wallet`, 'good')
    } catch (e) {
      toast(e.message, 'error')
    } finally {
      setBusy(null)
      load()
    }
  }

  const reject = async () => {
    setBusy(rejecting.id)
    try {
      await api(`/api/admin/topups/${rejecting.id}/reject`, { method: 'POST', body: { reason } })
      setRejecting(null)
    } catch (e) {
      toast(e.message, 'error')
    } finally {
      setBusy(null)
      load()
    }
  }

  return (
    <>
      <PageTitle title="UPI top-ups" sub="Check each reference in the canteen's UPI app before approving." />
      <div className="mb-3 inline-flex rounded-xl bg-card-2 p-1">
        {TABS.map(([k, label]) => (
          <button key={k} onClick={() => setTab(k)}
            className={`rounded-lg px-4 py-1.5 text-sm font-semibold ${tab === k ? 'bg-card shadow-sm' : 'text-muted'}`}>{label}</button>
        ))}
      </div>
      <ErrorNote>{error}</ErrorNote>
      {!list ? <Spinner /> : list.length === 0 ? (
        <Empty icon={IndianRupee} title={tab ? 'Nothing waiting' : 'No top-ups yet'}>Requests from students show up here instantly.</Empty>
      ) : (
        <Card className="divide-y divide-line">
          {list.map((t) => (
            <div key={t.id} className="flex flex-wrap items-center gap-3 p-4">
              <div className="min-w-0 basis-full sm:flex-1 sm:basis-auto">
                <p className="font-display text-xl font-bold">{rupees(t.amount)}</p>
                <p className="text-sm">{t.studentName}{t.studentRollNo ? <span className="text-muted"> · {t.studentRollNo}</span> : null}</p>
                <p className="text-xs text-muted">UPI ref <span className="font-mono font-semibold text-ink">{t.upiRef}</span> · {dateOf(t.createdAt)}, {timeOf(t.createdAt)}</p>
                {t.status !== 'PENDING' && (
                  <p className={`mt-1 text-xs font-semibold ${t.status === 'APPROVED' ? 'text-good' : 'text-bad'}`}>
                    {t.status === 'APPROVED' ? 'Approved' : 'Rejected'} by {t.decidedBy}{t.reason ? ` · ${t.reason}` : ''}
                  </p>
                )}
              </div>
              {t.status === 'PENDING' && (
                <div className="flex gap-2">
                  <Button size="sm" variant="danger" onClick={() => { setRejecting(t); setReason('') }}><X className="size-4" /> Reject</Button>
                  <Button size="sm" variant="good" loading={busy === t.id} onClick={() => approve(t)}><Check className="size-4" /> Approve</Button>
                </div>
              )}
            </div>
          ))}
        </Card>
      )}

      <Modal open={!!rejecting} onClose={() => setRejecting(null)} title="Reject top-up?">
        <p className="mb-3 text-sm text-ink-2">{rejecting?.studentName} will see the reason. No money is added.</p>
        <div className="mb-3 flex flex-wrap gap-2">
          {['Payment not found', 'Amount does not match', 'Duplicate request'].map((r) => (
            <button key={r} onClick={() => setReason(r)} className={`rounded-full border px-3 py-1 text-sm ${reason === r ? 'border-brand bg-brand-soft text-brand-ink' : 'border-line'}`}>{r}</button>
          ))}
        </div>
        <input value={reason} onChange={(e) => setReason(e.target.value)} maxLength={200} placeholder="Reason"
          className="mb-4 h-11 w-full rounded-xl border border-line bg-card px-3 outline-none focus:border-brand" />
        <Button variant="danger" className="w-full" loading={busy === rejecting?.id} onClick={reject}>Reject</Button>
      </Modal>
    </>
  )
}
