import { useEffect, useState } from 'react'
import { useOutletContext } from 'react-router-dom'
import { api } from '../../api'
import { Button, Card, ErrorNote, Input, PageTitle, Spinner, Toggle, useToast } from '../../components/ui'
import { CanteenSwitch } from '../kitchen/Board'

export default function Settings() {
  const { canteen } = useOutletContext()
  const toast = useToast()
  const [f, setF] = useState(null)
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    api('/api/admin/settings').then((s) => setF({ ...s, openTime: s.openTime.slice(0, 5), closeTime: s.closeTime.slice(0, 5) }))
      .catch((e) => setError(e.message))
  }, [])

  const save = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await api('/api/admin/settings', {
        method: 'PUT',
        body: {
          openTime: f.openTime, closeTime: f.closeTime, maxActiveOrders: Number(f.maxActiveOrders),
          maxQtyPerItem: Number(f.maxQtyPerItem), noShowMinutes: Number(f.noShowMinutes), cashAllowed: f.cashAllowed,
        },
      })
      toast('Settings saved', 'good')
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  if (!f) return error ? <ErrorNote>{error}</ErrorNote> : <Spinner />
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value })

  return (
    <>
      <PageTitle title="Canteen settings" />
      <Card className="mb-4 flex flex-wrap items-center justify-between gap-3 p-4">
        <div><p className="font-semibold">Status right now</p><p className="text-sm text-muted">Busy still takes orders but warns students about waiting.</p></div>
        <CanteenSwitch canteen={canteen} />
      </Card>
      <Card className="p-4">
        <form onSubmit={save} className="space-y-4">
          <div className="grid grid-cols-2 gap-3">
            <Input label="Opens at" type="time" required value={f.openTime} onChange={set('openTime')} />
            <Input label="Closes at" type="time" required value={f.closeTime} onChange={set('closeTime')} />
          </div>
          <div className="grid gap-3 sm:grid-cols-3">
            <Input label="Orders in progress per student" type="number" min={1} max={10} required value={f.maxActiveOrders} onChange={set('maxActiveOrders')} />
            <Input label="Max plates of one dish" type="number" min={1} max={20} required value={f.maxQtyPerItem} onChange={set('maxQtyPerItem')} />
            <Input label="Hold ready food (minutes)" type="number" min={10} max={240} required value={f.noShowMinutes} onChange={set('noShowMinutes')}
              hint="Then it's marked not collected and plates go back on sale." />
          </div>
          <div className="flex items-center justify-between rounded-xl bg-card-2 p-3">
            <div><p className="text-sm font-medium">Allow cash / UPI at counter</p><p className="text-xs text-muted">Off = wallet payments only</p></div>
            <Toggle checked={f.cashAllowed} onChange={(v) => setF({ ...f, cashAllowed: v })} label="Allow cash at counter" />
          </div>
          <ErrorNote>{error}</ErrorNote>
          <Button type="submit" loading={busy}>Save settings</Button>
        </form>
      </Card>
    </>
  )
}
