import { useCallback, useEffect, useState } from 'react'
import { ArrowDownLeft, ArrowUpRight, RotateCcw, Wallet as WalletIcon } from 'lucide-react'
import { api, dateOf, rupees, timeOf } from '../../api'
import { useLive } from '../../context/LiveContext'
import { Card, Empty, ErrorNote, PageTitle, Spinner } from '../../components/ui'

const TXN = {
  RECHARGE: { icon: ArrowDownLeft, label: 'Recharge', cls: 'bg-good-soft text-good' },
  REFUND: { icon: RotateCcw, label: 'Refund', cls: 'bg-good-soft text-good' },
  PAYMENT: { icon: ArrowUpRight, label: 'Payment', cls: 'bg-card-2 text-ink-2' },
}

export default function WalletPage() {
  const [wallet, setWallet] = useState(null)
  const [spend, setSpend] = useState(null)
  const [error, setError] = useState(null)

  const load = useCallback(() => {
    api('/api/wallet').then(setWallet).catch((e) => setError(e.message))
    api('/api/wallet/spending').then(setSpend).catch(() => {})
  }, [])
  useEffect(() => { load() }, [load])
  useLive('wallet', load)

  if (error) return <ErrorNote>{error}</ErrorNote>
  if (!wallet) return <Spinner />

  return (
    <>
      <PageTitle title="Wallet" />
      <div className="relative overflow-hidden rounded-3xl bg-brand p-5 text-white">
        <WalletIcon className="absolute -right-4 -top-4 size-32 opacity-10" />
        <p className="text-sm font-medium opacity-85">Available balance</p>
        <p className="font-display text-4xl font-extrabold">{rupees(wallet.balance)}</p>
        <p className="mt-3 text-sm opacity-85">Recharge at the canteen office with cash or UPI. It shows up here instantly.</p>
      </div>

      {spend && (
        <div className="mt-4 grid grid-cols-2 gap-3">
          <Card className="p-4">
            <p className="text-xs font-medium text-muted">Spent this month</p>
            <p className="font-display text-xl font-bold">{rupees(spend.thisMonth)}</p>
            <p className="text-xs text-muted">{spend.ordersThisMonth} orders · last month {rupees(spend.lastMonth)}</p>
          </Card>
          <Card className="p-4">
            <p className="text-xs font-medium text-muted">You order most</p>
            {spend.favourites.length ? (
              <ul className="mt-0.5 text-sm">
                {spend.favourites.map((f) => <li key={f.name} className="truncate"><span className="font-semibold">{f.count}×</span> {f.name}</li>)}
              </ul>
            ) : <p className="text-sm text-muted">Nothing yet</p>}
          </Card>
        </div>
      )}

      <h2 className="mb-2 mt-6 text-sm font-semibold uppercase tracking-wide text-muted">History</h2>
      {wallet.transactions.length === 0 ? (
        <Empty title="No transactions yet" />
      ) : (
        <Card className="divide-y divide-line">
          {wallet.transactions.map((t) => {
            const m = TXN[t.type]
            const Icon = m.icon
            return (
              <div key={t.id} className="flex items-center gap-3 p-3.5">
                <span className={`grid size-10 place-items-center rounded-xl ${m.cls}`}><Icon className="size-5" /></span>
                <div className="min-w-0 flex-1">
                  <p className="truncate font-medium">{t.note || m.label}</p>
                  <p className="text-xs text-muted">{dateOf(t.createdAt)}, {timeOf(t.createdAt)}{t.type === 'RECHARGE' && t.createdBy ? ` · by ${t.createdBy}` : ''}</p>
                </div>
                <div className="text-right">
                  <p className={`font-semibold ${Number(t.amount) > 0 ? 'text-good' : ''}`}>{Number(t.amount) > 0 ? '+' : '−'}{rupees(Math.abs(t.amount))}</p>
                  <p className="text-xs text-muted">bal {rupees(t.balanceAfter)}</p>
                </div>
              </div>
            )
          })}
        </Card>
      )}
    </>
  )
}
