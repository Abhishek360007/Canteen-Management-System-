import { Link } from 'react-router-dom'
import { Boxes, ChefHat, ChevronRight, IndianRupee, Megaphone, ScanLine, Settings, User } from 'lucide-react'
import { Card, PageTitle } from '../../components/ui'

const LINKS = [
  ['Kitchen', [
    ['/kitchen', 'Live order board', ChefHat],
    ['/kitchen/verify', 'Hand over / verify code', ScanLine],
    ['/kitchen/stock', "Today's stock", Boxes],
    ['/kitchen/summary', 'Day summary & cash', IndianRupee],
  ]],
  ['Manage', [
    ['/admin/announcements', 'Announcements', Megaphone],
    ['/admin/settings', 'Canteen settings', Settings],
    ['/account', 'My account', User],
  ]],
]

export default function More() {
  return (
    <>
      <PageTitle title="More" />
      {LINKS.map(([group, items]) => (
        <section key={group} className="mb-5">
          <h2 className="mb-2 text-sm font-semibold uppercase tracking-wide text-muted">{group}</h2>
          <Card className="divide-y divide-line">
            {items.map(([to, label, Icon]) => (
              <Link key={to} to={to} className="flex items-center gap-3 p-4 hover:bg-card-2">
                <span className="grid size-9 place-items-center rounded-xl bg-brand-soft text-brand-ink"><Icon className="size-5" /></span>
                <span className="flex-1 font-medium">{label}</span>
                <ChevronRight className="size-5 text-muted" />
              </Link>
            ))}
          </Card>
        </section>
      ))}
    </>
  )
}
