import { Card } from './ui'

export function Stat({ label, value, hint, strong }) {
  return (
    <Card className={`p-4 ${strong ? 'border-brand bg-brand-soft' : ''}`}>
      <p className="text-xs font-medium text-muted">{label}</p>
      <p className="font-display text-2xl font-bold tracking-tight">{value}</p>
      {hint && <p className="mt-0.5 text-xs text-ink-2">{hint}</p>}
    </Card>
  )
}
