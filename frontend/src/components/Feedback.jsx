import { useCallback, useEffect, useState } from 'react'
import { CircleAlert, MessageSquare, Send, Star } from 'lucide-react'
import { api, dateOf, rupees, timeOf } from '../api'
import { useLive } from '../context/LiveContext'
import { Button, Card, Modal, Spinner, useToast } from './ui'

export const CATEGORIES = [
  ['WRONG_ITEM', 'Wrong item', '🔁'],
  ['MISSING_ITEM', 'Something missing', '🫙'],
  ['QUALITY', 'Cold / stale / taste', '🥶'],
  ['QUANTITY', 'Small portion', '🥄'],
  ['HYGIENE', 'Hygiene', '🧼'],
  ['LATE', 'Late / not ready', '⏰'],
  ['OVERCHARGED', 'Charged wrongly', '💸'],
  ['STAFF', 'Staff behaviour', '🙋'],
  ['OTHER', 'Something else', '💬'],
]
export const categoryLabel = (k) => CATEGORIES.find(([c]) => c === k)?.[1] || k

const WINDOW_HOURS = 48
const textArea = 'w-full rounded-xl border border-line bg-card px-3 py-2 text-[15px] outline-none placeholder:text-muted focus:border-brand'

export function Stars({ value = 0, onChange, size = 'size-7', label = 'Rating' }) {
  return (
    <div className="flex gap-0.5" role={onChange ? 'radiogroup' : 'img'} aria-label={onChange ? label : `${value} out of 5 stars`}>
      {[1, 2, 3, 4, 5].map((n) => onChange ? (
        <button key={n} type="button" role="radio" aria-checked={n === value} aria-label={`${n} star${n > 1 ? 's' : ''}`}
          onClick={() => onChange(n)} className="p-0.5">
          <Star className={`${size} ${n <= value ? 'fill-warn text-warn' : 'text-line'}`} />
        </button>
      ) : (
        <Star key={n} className={`${size} ${n <= value ? 'fill-warn text-warn' : 'text-line'}`} />
      ))}
    </div>
  )
}

export function ComplaintStatus({ status, lastFromStaff, staffView }) {
  if (status === 'RESOLVED') return <span className="rounded-full bg-good-soft px-2.5 py-1 text-xs font-semibold text-good">Resolved</span>
  if (status === 'REJECTED') return <span className="rounded-full bg-card-2 px-2.5 py-1 text-xs font-semibold text-ink-2">Closed</span>
  const waitingOnUs = staffView ? !lastFromStaff : lastFromStaff
  return (
    <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${waitingOnUs ? 'bg-warn-soft text-warn' : 'bg-brand-soft text-brand-ink'}`}>
      {staffView ? (waitingOnUs ? 'Needs reply' : 'Waiting for student') : (waitingOnUs ? 'Canteen replied' : 'Open')}
    </span>
  )
}

/** The original report, the messages and (when closed) the outcome. */
export function Thread({ c, staffView }) {
  return (
    <div className="space-y-2.5">
      <Bubble mine={!staffView} who={staffView ? c.studentName : 'You'} at={c.createdAt}>
        <p className="text-xs font-semibold opacity-80">{categoryLabel(c.category)}{c.dishes ? ` · ${c.dishes}` : ''}</p>
        <p className="whitespace-pre-line">{c.details}</p>
      </Bubble>
      {c.messages?.map((m) => (
        <Bubble key={m.id} mine={staffView ? m.fromStaff : !m.fromStaff}
          who={m.fromStaff ? (staffView ? m.authorName : `${m.authorName} · Canteen`) : (staffView ? m.authorName : 'You')} at={m.createdAt}>
          <p className="whitespace-pre-line">{m.text}</p>
        </Bubble>
      ))}
      {c.status !== 'OPEN' && (
        <div className={`rounded-xl p-3 text-sm ${c.status === 'RESOLVED' ? 'bg-good-soft text-good' : 'bg-card-2 text-ink-2'}`}>
          <p className="font-semibold">
            {c.status === 'RESOLVED' ? (c.refundAmount ? `Resolved · ${rupees(c.refundAmount)} refunded to ${staffView ? 'the' : 'your'} wallet` : 'Resolved') : 'Closed without a refund'}
          </p>
          {c.resolution && <p className="mt-0.5 whitespace-pre-line">{c.resolution}</p>}
          <p className="mt-1 text-xs opacity-80">by {c.resolvedBy} · {dateOf(c.resolvedAt)}, {timeOf(c.resolvedAt)}</p>
        </div>
      )}
    </div>
  )
}

function Bubble({ mine, who, at, children }) {
  return (
    <div className={`flex flex-col ${mine ? 'items-end' : 'items-start'}`}>
      <div className={`max-w-[85%] rounded-2xl px-3.5 py-2 text-sm ${mine ? 'rounded-br-md bg-brand text-white' : 'rounded-bl-md bg-card-2 text-ink'}`}>
        {children}
      </div>
      <span className="mt-0.5 px-1 text-[11px] text-muted">{who} · {dateOf(at)}, {timeOf(at)}</span>
    </div>
  )
}

export function ReplyBox({ onSend, placeholder = 'Write a reply…' }) {
  const [text, setText] = useState('')
  const [busy, setBusy] = useState(false)
  const send = async (e) => {
    e.preventDefault()
    if (!text.trim()) return
    setBusy(true)
    try {
      await onSend(text.trim())
      setText('')
    } finally {
      setBusy(false)
    }
  }
  return (
    <form onSubmit={send} className="mt-3 flex items-end gap-2">
      <textarea value={text} onChange={(e) => setText(e.target.value)} maxLength={600} rows={2} placeholder={placeholder}
        aria-label="Reply" className={textArea} />
      <Button type="submit" loading={busy} disabled={!text.trim()} aria-label="Send"><Send className="size-4" /></Button>
    </form>
  )
}

// ---------------------------------------------------------------- student: order page

/** "Problem with this order?" button, the report form, and the conversation afterwards. */
export function OrderComplaint({ order }) {
  const toast = useToast()
  const [c, setC] = useState(undefined)
  const [open, setOpen] = useState(false)
  const load = useCallback(() => api(`/api/orders/${order.id}/complaint`).then(setC).catch(() => setC(null)), [order.id])
  useEffect(() => { load() }, [load])
  useLive('complaint', (ev) => { if (String(ev?.orderId) === String(order.id)) load() })

  if (c === undefined) return null
  if (c) {
    const send = async (text) => {
      try {
        setC(await api(`/api/orders/${order.id}/complaint/messages`, { method: 'POST', body: { text } }))
      } catch (e) {
        toast(e.message, 'error')
        throw e
      }
    }
    return (
      <Card className="mt-4 p-4">
        <div className="mb-3 flex items-center justify-between gap-2">
          <h2 className="flex items-center gap-2 font-semibold"><MessageSquare className="size-4.5" /> Your complaint</h2>
          <ComplaintStatus status={c.status} lastFromStaff={c.lastFromStaff} />
        </div>
        <Thread c={c} />
        {c.status === 'OPEN' && <ReplyBox onSend={send} placeholder="Add more details…" />}
      </Card>
    )
  }

  const closed = new Date(order.closedAt || order.createdAt).getTime()
  if (!['COLLECTED', 'NO_SHOW'].includes(order.status) || Date.now() - closed > WINDOW_HOURS * 3600e3) return null
  return (
    <>
      <button onClick={() => setOpen(true)}
        className="mt-4 flex w-full items-center justify-center gap-2 rounded-2xl border border-dashed border-line p-3 text-sm font-medium text-ink-2 hover:bg-card-2">
        <CircleAlert className="size-4" /> Problem with this order? Tell us
      </button>
      <ReportModal order={order} open={open} onClose={() => setOpen(false)} onDone={(x) => { setC(x); setOpen(false) }} />
    </>
  )
}

function ReportModal({ order, open, onClose, onDone }) {
  const toast = useToast()
  const [category, setCategory] = useState(null)
  const [dishes, setDishes] = useState([])
  const [details, setDetails] = useState('')
  const [busy, setBusy] = useState(false)

  const toggle = (id) => setDishes((d) => d.includes(id) ? d.filter((x) => x !== id) : [...d, id])
  const submit = async (e) => {
    e.preventDefault()
    setBusy(true)
    try {
      const c = await api(`/api/orders/${order.id}/complaint`, { method: 'POST', body: { category, menuItemIds: dishes, details: details.trim() } })
      toast("Sent. We'll reply here and notify you.", 'good')
      onDone(c)
    } catch (err) {
      toast(err.message, 'error')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal open={open} onClose={onClose} title={`Problem with ${order.code}`}>
      <form onSubmit={submit}>
        <p className="mb-2 text-sm font-medium text-ink-2">What went wrong?</p>
        <div className="mb-4 flex flex-wrap gap-2">
          {CATEGORIES.map(([k, label, emoji]) => (
            <button key={k} type="button" onClick={() => setCategory(k)} aria-pressed={category === k}
              className={`rounded-full border px-3 py-1.5 text-sm ${category === k ? 'border-brand bg-brand-soft font-semibold text-brand-ink' : 'border-line'}`}>
              {emoji} {label}
            </button>
          ))}
        </div>
        {order.items.length > 1 && (
          <>
            <p className="mb-2 text-sm font-medium text-ink-2">Which dishes? <span className="font-normal text-muted">(optional)</span></p>
            <div className="mb-4 space-y-1.5">
              {order.items.map((i) => (
                <label key={i.menuItemId} className="flex items-center gap-2 text-sm">
                  <input type="checkbox" checked={dishes.includes(i.menuItemId)} onChange={() => toggle(i.menuItemId)} className="size-4 accent-brand" />
                  {i.quantity} × {i.name}
                </label>
              ))}
            </div>
          </>
        )}
        <label className="mb-1.5 block text-sm font-medium text-ink-2" htmlFor="complaint-details">Tell us what happened</label>
        <textarea id="complaint-details" value={details} onChange={(e) => setDetails(e.target.value)} rows={4} minLength={10} maxLength={600} required
          placeholder="e.g. I got paneer roll instead of veg roll, and the chutney was missing." className={textArea} />
        <p className="mb-4 mt-1 text-xs text-muted">{details.trim().length}/600 · The canteen team will reply here. Refunds go to your wallet.</p>
        <Button type="submit" className="w-full" loading={busy} disabled={!category || details.trim().length < 10}>Send to the canteen</Button>
      </form>
    </Modal>
  )
}

/** Stars and a few words for each dish of a collected order. */
export function DishReviewsPanel({ order }) {
  const [mine, setMine] = useState(null)
  const load = useCallback(() => api(`/api/orders/${order.id}/reviews`).then(setMine).catch(() => setMine([])), [order.id])
  useEffect(() => { load() }, [load])
  if (!mine) return null
  const recent = Date.now() - new Date(order.closedAt || order.createdAt).getTime() < 14 * 86400e3
  if (!recent && mine.length === 0) return null

  return (
    <Card className="mt-4 p-4">
      <h2 className="font-semibold">Rate each dish</h2>
      <p className="mb-3 text-xs text-muted">Helps other students choose, and tells the kitchen what to fix.</p>
      <div className="divide-y divide-line">
        {order.items.map((i) => (
          <DishReviewRow key={i.menuItemId} orderId={order.id} item={i} saved={mine.find((r) => r.menuItemId === i.menuItemId)}
            editable={recent} onSaved={(r) => setMine((m) => [...m.filter((x) => x.menuItemId !== r.menuItemId), r])} />
        ))}
      </div>
    </Card>
  )
}

function DishReviewRow({ orderId, item, saved, editable, onSaved }) {
  const toast = useToast()
  const [stars, setStars] = useState(saved?.stars || 0)
  const [comment, setComment] = useState(saved?.comment || '')
  const [editing, setEditing] = useState(!saved)
  const [busy, setBusy] = useState(false)

  const save = async () => {
    setBusy(true)
    try {
      const r = await api(`/api/orders/${orderId}/reviews`, { method: 'POST', body: { menuItemId: item.menuItemId, stars, comment: comment.trim() || null } })
      onSaved(r)
      setEditing(false)
      toast(r.status === 'FLAGGED' ? 'Saved. Your words will show once the canteen checks them.' : 'Thanks for your review!', 'good')
    } catch (e) {
      toast(e.message, 'error')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="py-3 first:pt-0 last:pb-0">
      <div className="flex items-center justify-between gap-2">
        <span className="min-w-0 truncate text-sm font-medium">{item.name}</span>
        <Stars value={stars} onChange={editable && editing ? setStars : undefined} size="size-6" label={`Stars for ${item.name}`} />
      </div>
      {editing && editable ? (
        stars > 0 && (
          <div className="mt-2">
            <textarea value={comment} onChange={(e) => setComment(e.target.value)} rows={2} maxLength={400}
              placeholder="What did you like or not? (optional)" aria-label={`Review of ${item.name}`} className={textArea} />
            <div className="mt-2 flex justify-end gap-2">
              {saved && <Button size="sm" variant="ghost" onClick={() => { setEditing(false); setStars(saved.stars); setComment(saved.comment || '') }}>Cancel</Button>}
              <Button size="sm" loading={busy} onClick={save}>{saved ? 'Save changes' : 'Post review'}</Button>
            </div>
          </div>
        )
      ) : saved && (
        <div className="mt-1 flex items-start justify-between gap-2 text-sm">
          <div className="min-w-0">
            {saved.comment && <p className="text-ink-2">“{saved.comment}”</p>}
            {saved.status === 'FLAGGED' && <p className="text-xs text-warn">Waiting for the canteen to check your words</p>}
            {saved.status === 'HIDDEN' && <p className="text-xs text-muted">Your words are hidden by the canteen; your stars still count</p>}
          </div>
          {editable && <button onClick={() => setEditing(true)} className="shrink-0 text-xs font-semibold text-brand">Edit</button>}
        </div>
      )}
    </div>
  )
}

// ---------------------------------------------------------------- menu: what people say

export function DishReviewsModal({ item, onClose }) {
  const [data, setData] = useState(null)
  useEffect(() => {
    if (!item) return
    setData(null)
    api(`/api/reviews/dish/${item.id}`).then(setData).catch(() => setData({ reviews: [] }))
  }, [item])

  return (
    <Modal open={!!item} onClose={onClose} title={item?.name || 'Reviews'}>
      {!data ? <Spinner /> : (
        <>
          {data.rating != null && (
            <div className="mb-4 flex items-center gap-3">
              <span className="font-display text-4xl font-bold">{data.rating}</span>
              <div>
                <Stars value={Math.round(data.rating)} size="size-5" />
                <p className="text-xs text-muted">{data.ratingCount} rating{data.ratingCount === 1 ? '' : 's'}</p>
              </div>
            </div>
          )}
          {data.reviews.length === 0 ? (
            <p className="py-6 text-center text-sm text-muted">No written reviews yet. Order it and be the first!</p>
          ) : (
            <ul className="divide-y divide-line">
              {data.reviews.map((r) => (
                <li key={r.id} className="py-3">
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-sm font-semibold">{r.mine ? 'You' : r.author}</span>
                    <span className="text-xs text-muted">{dateOf(r.createdAt)}</span>
                  </div>
                  <Stars value={r.stars} size="size-3.5" />
                  <p className="mt-1 text-sm text-ink-2">{r.comment}</p>
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </Modal>
  )
}
