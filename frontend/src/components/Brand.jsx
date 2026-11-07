export function Logo({ className = 'size-9' }) {
  return (
    <svg viewBox="0 0 64 64" className={className} aria-hidden="true">
      <rect width="64" height="64" rx="16" fill="var(--brand)" />
      <path d="M14 30h36a18 18 0 0 1-36 0z" fill="#FFF4E6" />
      <path d="M20 26c0-6 4-6 4-11M30 26c0-6 4-6 4-11M40 26c0-6 4-6 4-11" stroke="#FFF4E6" strokeWidth="3" strokeLinecap="round" fill="none" />
      <rect x="12" y="46" width="40" height="4" rx="2" fill="#FFF4E6" />
    </svg>
  )
}

export function Brand({ sub }) {
  return (
    <div className="flex items-center gap-2.5">
      <Logo />
      <div className="leading-tight">
        <div className="font-display text-[17px] font-bold tracking-tight">Tula's Canteen</div>
        {sub && <div className="text-[11px] font-medium uppercase tracking-wider text-muted">{sub}</div>}
      </div>
    </div>
  )
}

/** Gradient tile with the dish emoji, or the dish photo when one is set. */
const TINTS = {
  BREAKFAST: 'from-amber-200 to-orange-300 dark:from-amber-900/60 dark:to-orange-900/60',
  MEALS: 'from-orange-200 to-rose-300 dark:from-orange-900/60 dark:to-rose-900/60',
  SNACKS: 'from-yellow-200 to-amber-300 dark:from-yellow-900/50 dark:to-amber-900/60',
  BEVERAGES: 'from-sky-200 to-teal-200 dark:from-sky-900/50 dark:to-teal-900/50',
  DESSERTS: 'from-pink-200 to-fuchsia-200 dark:from-pink-900/50 dark:to-fuchsia-900/50',
}

export function DishArt({ item, className = '' }) {
  if (item.imageUrl) {
    return <img src={item.imageUrl} alt="" loading="lazy" className={`object-cover ${className}`} />
  }
  return (
    <div className={`grid place-items-center bg-gradient-to-br ${TINTS[item.category] || TINTS.MEALS} ${className}`}>
      <span className="text-4xl drop-shadow-sm" aria-hidden="true">{item.emoji || '🍽️'}</span>
    </div>
  )
}
