import { useCallback, useEffect, useState } from 'react'
import { Megaphone, Trash2 } from 'lucide-react'
import { api, dateOf } from '../../api'
import { Button, Card, Empty, ErrorNote, PageTitle, Spinner, Toggle, useToast } from '../../components/ui'

export default function Announcements() {
  const toast = useToast()
  const [list, setList] = useState(null)
  const [msg, setMsg] = useState('')
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  const load = useCallback(() => api('/api/admin/announcements').then(setList).catch((e) => setError(e.message)), [])
  useEffect(() => { load() }, [load])

  const add = async (e) => {
    e.preventDefault()
    setBusy(true)
    try {
      await api('/api/admin/announcements', { method: 'POST', body: { message: msg, active: true } })
      setMsg('')
      load()
      toast('Announcement posted', 'good')
    } catch (err) {
      toast(err.message, 'error')
    } finally {
      setBusy(false)
    }
  }

  const update = async (a, active) => {
    try {
      await api(`/api/admin/announcements/${a.id}`, { method: 'PUT', body: { message: a.message, active } })
      load()
    } catch (e) {
      toast(e.message, 'error')
    }
  }

  const remove = async (a) => {
    if (!confirm('Delete this announcement?')) return
    await api(`/api/admin/announcements/${a.id}`, { method: 'DELETE' }).catch((e) => toast(e.message, 'error'))
    load()
  }

  return (
    <>
      <PageTitle title="Announcements" sub="The newest active one shows at the top of every student's screen." />
      <Card className="mb-4 p-4">
        <form onSubmit={add} className="space-y-3">
          <textarea value={msg} onChange={(e) => setMsg(e.target.value)} required maxLength={280} rows={3}
            placeholder="e.g. Special: Paneer biryani today at ₹99!" aria-label="New announcement"
            className="w-full rounded-xl border border-line bg-card px-3 py-2 outline-none placeholder:text-muted focus:border-brand" />
          <div className="flex items-center justify-between">
            <span className="text-xs text-muted">{msg.length}/280</span>
            <Button type="submit" loading={busy} disabled={!msg.trim()}><Megaphone className="size-4" /> Post</Button>
          </div>
        </form>
      </Card>
      <ErrorNote>{error}</ErrorNote>
      {!list ? <Spinner /> : list.length === 0 ? <Empty icon={Megaphone} title="No announcements" /> : (
        <Card className="divide-y divide-line">
          {list.map((a) => (
            <div key={a.id} className="flex items-start gap-3 p-4">
              <div className="min-w-0 flex-1">
                <p className={a.active ? '' : 'text-muted line-through'}>{a.message}</p>
                <p className="mt-0.5 text-xs text-muted">{dateOf(a.createdAt)} · {a.active ? 'Showing' : 'Hidden'}</p>
              </div>
              <Toggle checked={a.active} onChange={(v) => update(a, v)} label="Show to students" />
              <button onClick={() => remove(a)} className="rounded-lg p-1.5 text-bad hover:bg-bad-soft" aria-label="Delete"><Trash2 className="size-4" /></button>
            </div>
          ))}
        </Card>
      )}
    </>
  )
}
