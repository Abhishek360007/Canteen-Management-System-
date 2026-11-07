import { useEffect, useState } from 'react'
import { BellOff, BellRing, Smartphone } from 'lucide-react'
import { api } from '../api'
import { disablePush, enablePush, pushState } from '../push'
import { Button, Card, useToast } from './ui'

const TEXT = {
  STUDENT: 'Get an alert on this phone when your food is ready, even with the site closed.',
  STAFF: 'Get an alert on this device when a dish is running low or sells out.',
  ADMIN: 'Get alerts on this device for low stock and other canteen events.',
}

/** "Phone alerts" card: turn browser push on/off for this device and send a test. */
export default function PushToggle({ role, compact = false }) {
  const toast = useToast()
  const [state, setState] = useState('loading')
  const [busy, setBusy] = useState(false)
  useEffect(() => { pushState().then(setState) }, [])

  const turnOn = async () => {
    setBusy(true)
    try {
      const s = await enablePush()
      setState(s)
      if (s === 'on') toast('Alerts are on for this device', 'good')
      if (s === 'blocked') toast('Notifications are blocked. Allow them in your browser settings for this site.', 'error')
    } catch (e) {
      toast(e.message || 'Could not turn on alerts', 'error')
    } finally {
      setBusy(false)
    }
  }

  if (state === 'loading' || (compact && state !== 'off')) return null
  if (compact) {
    return (
      <Button variant="soft" className="w-full" loading={busy} onClick={turnOn}>
        <BellRing className="size-4" /> Alert me on this phone when it's ready
      </Button>
    )
  }

  return (
    <Card className="p-4">
      <h2 className="flex items-center gap-2 font-semibold"><Smartphone className="size-4 text-brand" /> Phone alerts</h2>
      <p className="mt-1 text-sm text-muted">{TEXT[role] || TEXT.STUDENT}</p>
      <div className="mt-3 flex flex-wrap gap-2">
        {state === 'on' && <>
          <Button variant="soft" onClick={() => api('/api/push/test', { method: 'POST' }).then(() => toast('Test sent. Check your notifications.', 'good'))}>
            <BellRing className="size-4" /> Send a test
          </Button>
          <Button variant="ghost" onClick={async () => setState(await disablePush())}><BellOff className="size-4" /> Turn off</Button>
        </>}
        {state === 'off' && <Button loading={busy} onClick={turnOn}><BellRing className="size-4" /> Turn on alerts</Button>}
        {state === 'blocked' && <p className="text-sm text-bad">Blocked in this browser. Allow notifications for this site in the browser's settings, then reload.</p>}
        {state === 'unsupported' && <p className="text-sm text-muted">This browser doesn't support alerts. On iPhone, add the site to your Home Screen first (Share → Add to Home Screen).</p>}
      </div>
    </Card>
  )
}
