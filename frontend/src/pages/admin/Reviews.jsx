import { useCallback, useEffect, useState } from 'react'
import { Eye, EyeOff, Star } from 'lucide-react'
import { api, dateOf } from '../../api'
import { Stars } from '../../components/Feedback'
import { Button, Card, Empty, ErrorNote, PageTitle, Spinner, useToast } from '../../components/ui'

const TABS = [['FLAGGED', 'Needs a check'], ['', 'All reviews']]
const STATUS = {
  VISIBLE: ['Shown', 'bg-good-soft text-good'],
  FLAGGED: ['Held', 'bg-warn-soft text-warn'],
  HIDDEN: ['Hidden', 'bg-card-2 text-ink-2'],
}

/**
 * Admin: dish reviews. Words that look like a link, phone number, email or abuse wait here before anyone sees them.
 * Hiding a review only hides its words; its stars still count, so the rating can't be gamed by moderation.
 */
export default function Reviews() {
  const toast = useToast()
  const [tab, setTab] = useState('FLAGGED')
  const [list, setList] = useState(null)
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(null)

  const load = useCallback(() => api(`/api/admin/reviews${tab ? `?status=${tab}` : ''}`).then(setList).catch((e) => setError(e.message)), [tab])
  useEffect(() => { setList(null); load() }, [load])

  const moderate = async (r, status) => {
    let reason = null
    if (status === 'HIDDEN') {
      reason = prompt('Why hide it? (only the student sees this)', 'Not about the food')
      if (reason === null) return
    }
    setBusy(r.id)
    try {
      await api(`/api/admin/reviews/${r.id}/moderate`, { method: 'POST', body: { status, reason } })
      toast(status === 'HIDDEN' ? 'Review hidden' : 'Review is now shown', 'good')
      load()
    } catch (e) {
      toast(e.message, 'error')
    } finally {
      setBusy(null)
    }
  }

  return (
    <>
      <PageTitle title="Dish reviews" sub="Hiding a review hides its words only; its stars still count." />
      <div className="mb-3 inline-flex rounded-xl bg-card-2 p-1">
        {TABS.map(([k, label]) => (
          <button key={k} onClick={() => setTab(k)}
            className={`rounded-lg px-4 py-1.5 text-sm font-semibold ${tab === k ? 'bg-card shadow-sm' : 'text-muted'}`}>{label}</button>
        ))}
      </div>
      <ErrorNote>{error}</ErrorNote>
      {!list ? <Spinner /> : list.length === 0 ? (
        <Empty icon={Star} title={tab ? 'Nothing to check' : 'No written reviews yet'}>
          {tab ? 'Reviews with links, phone numbers or abusive words wait here.' : 'Students can review each dish after collecting an order.'}
        </Empty>
      ) : (
        <Card className="divide-y divide-line">
          {list.map((r) => {
            const [label, cls] = STATUS[r.status]
            return (
              <div key={r.id} className="flex flex-wrap items-start gap-3 p-4">
                <div className="min-w-0 basis-full sm:flex-1 sm:basis-auto">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-semibold">{r.dishName}</span>
                    <Stars value={r.stars} size="size-3.5" />
                    <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${cls}`}>{label}</span>
                  </div>
                  <p className="mt-1 text-sm">“{r.comment}”</p>
                  <p className="mt-0.5 text-xs text-muted">
                    {r.userName} · {r.userEmail} · {dateOf(r.createdAt)}
                    {r.flagReason ? <span className="font-semibold text-warn"> · {r.flagReason}</span> : null}
                    {r.moderatedBy ? ` · by ${r.moderatedBy}` : ''}
                  </p>
                </div>
                <div className="flex gap-2">
                  {r.status !== 'HIDDEN' && <Button size="sm" variant="danger" loading={busy === r.id} onClick={() => moderate(r, 'HIDDEN')}><EyeOff className="size-4" /> Hide</Button>}
                  {r.status !== 'VISIBLE' && <Button size="sm" variant="good" loading={busy === r.id} onClick={() => moderate(r, 'VISIBLE')}><Eye className="size-4" /> Show</Button>}
                </div>
              </div>
            )
          })}
        </Card>
      )}
    </>
  )
}
