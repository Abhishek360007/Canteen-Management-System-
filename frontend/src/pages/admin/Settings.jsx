import { useEffect, useState } from 'react'
import { Link, useOutletContext } from 'react-router-dom'
import { ChevronRight, Store } from 'lucide-react'
import { api } from '../../api'
import { Button, Card, ErrorNote, Input, PageTitle, Spinner, useToast } from '../../components/ui'
import { CanteenSwitch } from '../kitchen/Board'

/** Campus-wide settings (UPI for wallet top-ups) + the selected canteen's live status. Hours and rules are per canteen. */
export default function Settings() {
  const { canteen } = useOutletContext()
  const toast = useToast()
  const [f, setF] = useState(null)
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    api('/api/admin/settings').then((s) => setF({ upiId: s.upiId || '', upiName: s.upiName || '' })).catch((e) => setError(e.message))
  }, [])

  const save = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await api('/api/admin/settings', { method: 'PUT', body: { upiId: f.upiId.trim(), upiName: f.upiName.trim() } })
      toast('Settings saved', 'good')
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  if (!f) return error ? <ErrorNote>{error}</ErrorNote> : <Spinner />

  return (
    <>
      <PageTitle title="Settings" />
      {canteen && (
        <Card className="mb-4 flex flex-wrap items-center justify-between gap-3 p-4">
          <div><p className="font-semibold">{canteen.name} right now</p><p className="text-sm text-muted">Busy still takes orders but warns students about waiting.</p></div>
          <CanteenSwitch canteen={canteen} />
        </Card>
      )}
      <Link to="/admin/canteens" className="mb-4 flex items-center gap-3 rounded-2xl border border-line bg-card p-4 hover:bg-card-2">
        <span className="grid size-10 place-items-center rounded-xl bg-brand-soft text-brand-ink"><Store className="size-5" /></span>
        <span className="flex-1"><span className="block font-semibold">Canteens, hours and order rules</span><span className="block text-sm text-muted">Each canteen has its own opening hours, limits and cash option.</span></span>
        <ChevronRight className="size-5 text-muted" />
      </Link>
      <Card className="p-4">
        <form onSubmit={save} className="space-y-3">
          <p className="font-semibold">UPI wallet top-ups</p>
          <p className="text-sm text-muted">One wallet works at every canteen. Students pay this UPI ID and send the reference; you approve it under More → UPI top-ups. Leave empty to switch off.</p>
          <div className="grid gap-3 sm:grid-cols-2">
            <Input label="Canteen UPI ID" placeholder="tulascanteen@okaxis" maxLength={60} value={f.upiId} onChange={(e) => setF({ ...f, upiId: e.target.value })} />
            <Input label="Name shown in UPI app" placeholder="Tula's Canteen" maxLength={60} value={f.upiName} onChange={(e) => setF({ ...f, upiName: e.target.value })} />
          </div>
          <ErrorNote>{error}</ErrorNote>
          <Button type="submit" loading={busy}>Save</Button>
        </form>
      </Card>
    </>
  )
}
