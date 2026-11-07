import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate, useOutletContext } from 'react-router-dom'
import { Banknote, Gift, Minus, Plus, ShoppingBag, Trash2, Wallet } from 'lucide-react'
import { RecRows } from '../../components/Recs'
import { api, newKey, rupees } from '../../api'
import { useCart } from '../../context/CartContext'
import { useLive } from '../../context/LiveContext'
import { DishArt } from '../../components/Brand'
import { Button, Card, Empty, ErrorNote, PageTitle, Select, Toggle, VegDot } from '../../components/ui'

/** Pickup slots every 15 minutes from ~20 minutes from now until closing. */
function slots(closeTime) {
  if (!closeTime) return []
  const [ch, cm] = closeTime.split(':').map(Number)
  const close = new Date()
  close.setHours(ch, cm, 0, 0)
  const t = new Date(Date.now() + 20 * 60000)
  t.setMinutes(Math.ceil(t.getMinutes() / 15) * 15, 0, 0)
  const out = []
  while (t < close && out.length < 40) {
    out.push(`${String(t.getHours()).padStart(2, '0')}:${String(t.getMinutes()).padStart(2, '0')}`)
    t.setMinutes(t.getMinutes() + 15)
  }
  return out
}

const label = (hhmm) => {
  const [h, m] = hhmm.split(':').map(Number)
  const d = new Date()
  d.setHours(h, m)
  return d.toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit' })
}

export default function Cart() {
  const cart = useCart()
  const nav = useNavigate()
  const { canteen } = useOutletContext()
  const [balance, setBalance] = useState(null)
  const [pay, setPay] = useState('WALLET')
  const [slot, setSlot] = useState('')
  const [note, setNote] = useState('')
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)
  // One key per checkout attempt: a double tap or retry can't create two orders.
  const key = useRef(newKey())

  const loadWallet = () => api('/api/wallet').then((w) => setBalance(Number(w.balance))).catch(() => {})
  useEffect(() => { loadWallet() }, [])
  useLive('wallet', loadWallet)

  // Reward points: use the most allowed (steps of 10, at most half the order) when switched on.
  const [rewards, setRewards] = useState(null)
  const [usePoints, setUsePoints] = useState(false)
  useEffect(() => { api('/api/rewards').then(setRewards).catch(() => {}) }, [])
  const step = rewards?.redeemStep || 10
  const pointValue = Number(rewards?.pointValue || 0.5)
  const maxByOrder = Math.floor(cart.total / 2 / pointValue / step) * step
  const usablePoints = rewards ? Math.max(0, Math.min(Math.floor(rewards.points / step) * step, maxByOrder)) : 0
  const redeem = usePoints ? usablePoints : 0
  const discount = redeem * pointValue
  const toPay = cart.total - discount

  // "Goes well with your order": basket suggestions for what's in the cart.
  const [menu, setMenu] = useState([])
  const [suggest, setSuggest] = useState([])
  useEffect(() => { api('/api/menu').then(setMenu).catch(() => {}) }, [])
  const cartKey = cart.lines.map((l) => l.item.id).sort().join(',')
  useEffect(() => {
    if (!cartKey) return
    const t = setTimeout(() => {
      api('/api/recommendations/cart', { method: 'POST', body: { itemIds: cartKey.split(',').map(Number) } })
        .then(setSuggest).catch(() => setSuggest([]))
    }, 300)
    return () => clearTimeout(t)
  }, [cartKey])
  const menuById = useMemo(() => new Map(menu.map((m) => [m.id, m])), [menu])

  const slotList = useMemo(() => slots(canteen?.closeTime), [canteen])
  const short = pay === 'WALLET' && balance !== null && balance < toPay
  const closed = canteen && !canteen.openNow

  const place = async () => {
    setBusy(true)
    setError(null)
    try {
      const order = await api('/api/orders', {
        method: 'POST',
        headers: { 'Idempotency-Key': key.current },
        body: {
          items: cart.lines.map((l) => ({ menuItemId: l.item.id, quantity: l.qty })),
          paymentMethod: pay,
          pickupSlot: slot,
          note,
          redeemPoints: redeem,
        },
      })
      cart.clear()
      key.current = newKey()
      nav(`/orders/${order.id}`, { replace: true, state: { fresh: true } })
    } catch (e) {
      setError(e.message)
      // A rejected order (sold out, low balance…) didn't happen, so the next try is a new attempt.
      if (e.status && e.status < 500) key.current = newKey()
    } finally {
      setBusy(false)
    }
  }

  if (cart.count === 0) {
    return (
      <Empty icon={ShoppingBag} title="Your cart is empty">
        <p>Add something tasty from the menu.</p>
        <Link to="/menu"><Button className="mt-4">Browse menu</Button></Link>
      </Empty>
    )
  }

  return (
    <>
      <PageTitle title="Your cart" sub={`${cart.count} item${cart.count > 1 ? 's' : ''}`} />

      <Card className="divide-y divide-line">
        {cart.lines.map(({ item, qty }) => (
          <div key={item.id} className="flex items-center gap-3 p-3">
            <DishArt item={item} className="size-14 rounded-xl [&>span]:text-2xl" />
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-1.5"><VegDot veg={item.veg} /><p className="truncate font-semibold">{item.name}</p></div>
              <p className="text-sm text-muted">{rupees(item.price)} × {qty} = <span className="font-semibold text-ink">{rupees(item.price * qty)}</span></p>
            </div>
            <div className="flex h-9 items-center rounded-xl border border-line">
              <button onClick={() => cart.remove(item.id)} className="grid size-9 place-items-center" aria-label="One less">
                {qty === 1 ? <Trash2 className="size-4 text-bad" /> : <Minus className="size-4" />}
              </button>
              <span className="w-5 text-center font-bold">{qty}</span>
              <button onClick={() => cart.add(item)} disabled={item.stockToday != null && qty >= item.stockToday}
                className="grid size-9 place-items-center disabled:opacity-30" aria-label="One more"><Plus className="size-4" /></button>
            </div>
          </div>
        ))}
      </Card>

      <RecRows title="Goes well with your order" recs={suggest} menuById={menuById} onAdd={(d) => cart.add(d)} />

      <Card className="mt-4 space-y-4 p-4">
        <Select label="Pickup time" value={slot} onChange={(e) => setSlot(e.target.value)}>
          <option value="">As soon as possible</option>
          {slotList.map((s) => <option key={s} value={s}>{label(s)}</option>)}
        </Select>
        <label className="block">
          <span className="mb-1.5 block text-sm font-medium text-ink-2">Note for the kitchen (optional)</span>
          <textarea value={note} onChange={(e) => setNote(e.target.value)} maxLength={200} rows={2}
            placeholder="Less spicy, no onion…"
            className="w-full rounded-xl border border-line bg-card px-3 py-2 text-[15px] outline-none placeholder:text-muted focus:border-brand" />
        </label>

        <fieldset>
          <legend className="mb-1.5 text-sm font-medium text-ink-2">Payment</legend>
          <div className="grid grid-cols-2 gap-2">
            <PayOption active={pay === 'WALLET'} onClick={() => setPay('WALLET')} icon={Wallet} title="Wallet"
              sub={balance === null ? '…' : `Balance ${rupees(balance)}`} />
            <PayOption active={pay === 'CASH'} onClick={() => setPay('CASH')} icon={Banknote} title="Cash / UPI"
              sub="Pay at the counter" disabled={canteen && !canteen.cashAllowed} />
          </div>
          {short && <p className="mt-2 text-sm text-bad">Not enough balance. Recharge at the canteen office or pay at the counter.</p>}
        </fieldset>
      </Card>

      {rewards && rewards.points >= step && (
        <Card className="mt-4 flex items-center justify-between gap-3 p-4">
          <div>
            <p className="flex items-center gap-1.5 font-semibold"><Gift className="size-4 text-brand" /> Use reward points</p>
            <p className="text-sm text-muted">
              {usablePoints > 0
                ? `Use ${usablePoints} of your ${rewards.points} points to save ${rupees(usablePoints * pointValue)}`
                : `You have ${rewards.points} points. Add more to your order to use them.`}
            </p>
          </div>
          <Toggle checked={usePoints && usablePoints > 0} disabled={usablePoints === 0} onChange={setUsePoints} label="Use reward points" />
        </Card>
      )}

      <Card className="mt-4 p-4">
        <div className="flex justify-between text-sm text-ink-2"><span>Items total</span><span>{rupees(cart.total)}</span></div>
        {discount > 0 && <div className="mt-1 flex justify-between text-sm text-good"><span>Points ({redeem})</span><span>−{rupees(discount)}</span></div>}
        <div className="mt-2 flex justify-between font-display text-lg font-bold"><span>To pay</span><span>{rupees(toPay)}</span></div>
        <p className="mt-1 text-xs text-muted">You'll earn about {Math.floor(toPay / 10 * (rewards?.multiplier || 1))} points when you collect this order.</p>
      </Card>

      <div className="mt-4 space-y-3">
        <ErrorNote>{error}</ErrorNote>
        <Button size="lg" className="w-full" loading={busy} disabled={short || closed} onClick={place}>
          {closed ? 'Canteen is closed' : `Place order · ${rupees(toPay)}`}
        </Button>
      </div>
    </>
  )
}

function PayOption({ active, onClick, icon: Icon, title, sub, disabled }) {
  return (
    <button type="button" onClick={onClick} disabled={disabled} aria-pressed={active}
      className={`flex items-start gap-2.5 rounded-xl border-2 p-3 text-left transition disabled:opacity-40 ${active ? 'border-brand bg-brand-soft' : 'border-line bg-card'}`}>
      <Icon className={`mt-0.5 size-5 ${active ? 'text-brand-ink' : 'text-muted'}`} />
      <span>
        <span className="block font-semibold">{title}</span>
        <span className="block text-xs text-muted">{disabled ? 'Not available now' : sub}</span>
      </span>
    </button>
  )
}
