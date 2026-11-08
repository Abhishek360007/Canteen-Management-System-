import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { CreditCard, Store, TrendingUp, Boxes, ChefHat, ChevronRight, History, IndianRupee, Megaphone, ScanLine, Settings, Smartphone, User } from 'lucide-react'
import { api } from '../../api'
import { useLive } from '../../context/LiveContext'
import { Card, PageTitle } from '../../components/ui'

export default function More() {
  const [pending, setPending] = useState(0)
  const load = useCallback(() => api('/api/admin/topups?status=PENDING').then((l) => setPending(l.length)).catch(() => {}), [])
  useEffect(() => { load() }, [load])
  useLive('topup', load)

  const groups = [
    ['Planning', [
      ['/admin/canteens', 'Canteens & comparison', Store],
      ['/admin/insights', 'Forecast & insights', TrendingUp],
    ]],
    ['Money', [
      ['/admin/payments', 'Online payments (Razorpay)', CreditCard],
      ['/admin/topups', 'UPI top-up requests', Smartphone, pending],
      ['/admin/activity', 'Activity log', History],
    ]],
    ['Kitchen', [
      ['/kitchen', 'Live order board', ChefHat],
      ['/kitchen/verify', 'Hand over / verify code', ScanLine],
      ['/kitchen/stock', "Today's stock", Boxes],
      ['/kitchen/summary', 'Day summary & cash', IndianRupee],
    ]],
    ['Manage', [
      ['/admin/announcements', 'Announcements', Megaphone],
      ['/admin/settings', 'Settings & UPI', Settings],
      ['/account', 'My account', User],
    ]],
  ]

  return (
    <>
      <PageTitle title="More" />
      {groups.map(([group, items]) => (
        <section key={group} className="mb-5">
          <h2 className="mb-2 text-sm font-semibold uppercase tracking-wide text-muted">{group}</h2>
          <Card className="divide-y divide-line">
            {items.map(([to, label, Icon, badge]) => (
              <Link key={to} to={to} className="flex items-center gap-3 p-4 hover:bg-card-2">
                <span className="grid size-9 place-items-center rounded-xl bg-brand-soft text-brand-ink"><Icon className="size-5" /></span>
                <span className="flex-1 font-medium">{label}</span>
                {badge > 0 && <span className="rounded-full bg-brand px-2 py-0.5 text-xs font-bold text-white">{badge}</span>}
                <ChevronRight className="size-5 text-muted" />
              </Link>
            ))}
          </Card>
        </section>
      ))}
    </>
  )
}
