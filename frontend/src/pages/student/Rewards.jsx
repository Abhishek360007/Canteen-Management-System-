import { useCallback, useEffect, useState } from 'react'
import { Cake, Copy, Gift, Share2, Sparkles, Trophy } from 'lucide-react'
import { api, dateOf, rupees } from '../../api'
import { useLive } from '../../context/LiveContext'
import { Button, Card, ErrorNote, PageTitle, Spinner, useToast } from '../../components/ui'

const TIER = {
  BRONZE: { label: 'Bronze', cls: 'from-amber-700 to-orange-500' },
  SILVER: { label: 'Silver', cls: 'from-slate-500 to-slate-400' },
  GOLD: { label: 'Gold', cls: 'from-yellow-600 to-amber-400' },
}
const TYPE = { EARN: 'Earned', REDEEM: 'Used', RESTORE: 'Returned', BIRTHDAY: 'Birthday', REFERRAL: 'Invite bonus' }

export default function Rewards() {
  const toast = useToast()
  const [r, setR] = useState(null)
  const [error, setError] = useState(null)
  const [bday, setBday] = useState('')
  const [busy, setBusy] = useState(false)

  const load = useCallback(() => api('/api/rewards').then(setR).catch((e) => setError(e.message)), [])
  useEffect(() => { load() }, [load])
  useLive('points', load)

  if (error) return <ErrorNote>{error}</ErrorNote>
  if (!r) return <Spinner />

  const tier = TIER[r.tier]
  const next = r.nextTier ? TIER[r.nextTier] : null
  const progress = r.nextTier
    ? Math.min(100, (Number(r.lifetimeSpend) / (Number(r.lifetimeSpend) + Number(r.spendToNextTier))) * 100)
    : 100
  const inviteLink = `${window.location.origin}/register?ref=${r.referralCode}`

  const share = async () => {
    const text = `Order ahead at Tula's Canteen and skip the queue! Sign up with my code ${r.referralCode} and we both get 50 points.`
    try {
      if (navigator.share) await navigator.share({ title: "Tula's Canteen", text, url: inviteLink })
      else { await navigator.clipboard.writeText(`${text} ${inviteLink}`); toast('Invite copied', 'good') }
    } catch { /* cancelled */ }
  }

  const saveBirthday = async (e) => {
    e.preventDefault()
    setBusy(true)
    try {
      setR(await api('/api/rewards/birthday', { method: 'PUT', body: { birthday: bday } }))
      toast('Birthday saved. See you on your big day!', 'good')
    } catch (err) {
      toast(err.message, 'error')
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <PageTitle title="Rewards" />
      <div className={`relative overflow-hidden rounded-3xl bg-gradient-to-br ${tier.cls} p-5 text-white`}>
        <Trophy className="absolute -right-3 -top-3 size-28 opacity-15" />
        <p className="text-sm font-semibold uppercase tracking-wider opacity-90">{tier.label} member · {r.multiplier}× points</p>
        <p className="mt-1 font-display text-4xl font-extrabold">{r.points} <span className="text-xl font-bold">points</span></p>
        <p className="text-sm opacity-90">Worth {rupees(r.pointsValue)} off your orders</p>
        {next && (
          <div className="mt-4">
            <div className="h-2 overflow-hidden rounded-full bg-white/25"><div className="h-2 rounded-full bg-white" style={{ width: `${progress}%` }} /></div>
            <p className="mt-1.5 text-xs opacity-90">Spend {rupees(r.spendToNextTier)} more to reach {next.label} ({r.nextTier === 'SILVER' ? '1.25' : '1.5'}× points)</p>
          </div>
        )}
      </div>

      <Card className="mt-4 p-4">
        <h2 className="flex items-center gap-2 font-semibold"><Sparkles className="size-4 text-brand" /> How it works</h2>
        <ul className="mt-2 space-y-1 text-sm text-ink-2">
          <li>• Earn <b>1 point for every ₹10</b> when you collect an order (Silver 1.25×, Gold 1.5×).</li>
          <li>• Use points at checkout: <b>{r.redeemStep} points = {rupees(r.redeemStep * r.pointValue)} off</b>, up to half the order.</li>
          <li>• Cancelled order? The points you used come straight back.</li>
        </ul>
      </Card>

      <Card className="mt-4 p-4">
        <h2 className="flex items-center gap-2 font-semibold"><Gift className="size-4 text-brand" /> Invite friends, both get 50 points</h2>
        <p className="mt-1 text-sm text-muted">They sign up with your code; you both get 50 points when their first order is collected.</p>
        <div className="mt-3 flex items-center gap-2">
          <span className="flex-1 rounded-xl bg-card-2 py-2.5 text-center font-mono text-xl font-bold tracking-widest">{r.referralCode}</span>
          <Button variant="outline" onClick={() => { navigator.clipboard?.writeText(r.referralCode); toast('Code copied', 'good') }} aria-label="Copy code"><Copy className="size-4" /></Button>
          <Button onClick={share}><Share2 className="size-4" /> Share</Button>
        </div>
      </Card>

      <Card className="mt-4 p-4">
        <h2 className="flex items-center gap-2 font-semibold"><Cake className="size-4 text-brand" /> Birthday treat</h2>
        {r.birthday ? (
          <p className="mt-1 text-sm text-ink-2">You'll get 50 bonus points every year on {new Date(r.birthday + 'T00:00:00').toLocaleDateString('en-IN', { day: 'numeric', month: 'long' })}. 🎂</p>
        ) : (
          <form onSubmit={saveBirthday} className="mt-2 flex items-end gap-2">
            <label className="flex-1">
              <span className="mb-1 block text-sm text-muted">Add your birthday for 50 points every year (can be set once)</span>
              <input type="date" required value={bday} onChange={(e) => setBday(e.target.value)} max={new Date().toISOString().slice(0, 10)}
                className="h-11 w-full rounded-xl border border-line bg-card px-3 outline-none focus:border-brand" />
            </label>
            <Button type="submit" variant="soft" loading={busy}>Save</Button>
          </form>
        )}
      </Card>

      <h2 className="mb-2 mt-6 text-sm font-semibold uppercase tracking-wide text-muted">Points history</h2>
      {r.history.length === 0 ? <p className="text-sm text-muted">No points yet. Collect an order to start earning.</p> : (
        <Card className="divide-y divide-line">
          {r.history.map((t) => (
            <div key={t.id} className="flex items-center justify-between gap-3 p-3.5 text-sm">
              <div className="min-w-0">
                <p className="truncate font-medium">{t.note || TYPE[t.type]}</p>
                <p className="text-xs text-muted">{TYPE[t.type]} · {dateOf(t.createdAt)}</p>
              </div>
              <span className={`shrink-0 font-semibold ${t.points > 0 ? 'text-good' : ''}`}>{t.points > 0 ? '+' : ''}{t.points}</span>
            </div>
          ))}
        </Card>
      )}
    </>
  )
}
