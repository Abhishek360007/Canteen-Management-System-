import { Plus, Sparkles } from 'lucide-react'
import { rupees } from '../api'
import { DishArt } from './Brand'
import { VegDot } from './ui'

/**
 * A horizontal strip of recommended dishes. `recs` are {menuItemId, reason} from the API;
 * `menuById` maps ids to the full dish (so price/stock are always current).
 */
export function RecStrip({ title, hint, recs, menuById, onAdd, qtyOf }) {
  const items = (recs || []).map((r) => ({ ...r, dish: menuById.get(r.menuItemId) })).filter((r) => r.dish && r.dish.available && !r.dish.soldOut)
  if (!items.length) return null
  return (
    <section className="mb-5">
      <div className="mb-2 flex items-baseline justify-between">
        <h2 className="flex items-center gap-1.5 font-display text-lg font-bold">{title}</h2>
        {hint && <span className="text-xs text-muted">{hint}</span>}
      </div>
      <div className="no-scrollbar -mx-4 flex gap-3 overflow-x-auto px-4 pb-1">
        {items.map(({ dish, reason }) => (
          <div key={dish.id} className="w-40 shrink-0 overflow-hidden rounded-2xl border border-line bg-card">
            <DishArt item={dish} className="h-20 w-full [&>span]:text-3xl" />
            <div className="p-2.5">
              <div className="flex items-start gap-1.5"><VegDot veg={dish.veg} /><p className="line-clamp-1 text-sm font-semibold">{dish.name}</p></div>
              <p className="mt-0.5 line-clamp-2 min-h-8 text-[11px] leading-4 text-muted">{reason}</p>
              <div className="mt-1.5 flex items-center justify-between">
                <span className="font-display font-bold">{rupees(dish.price)}</span>
                <button onClick={() => onAdd(dish)} aria-label={`Add ${dish.name}`}
                  className="flex h-7 items-center gap-0.5 rounded-lg border-2 border-brand px-2 text-xs font-bold text-brand hover:bg-brand-soft">
                  {qtyOf(dish.id) > 0 ? qtyOf(dish.id) : <Plus className="size-3.5" />} {qtyOf(dish.id) > 0 ? 'in cart' : 'ADD'}
                </button>
              </div>
            </div>
          </div>
        ))}
      </div>
    </section>
  )
}

/** Compact rows, e.g. "Goes well with your order" in the cart. */
export function RecRows({ title, recs, menuById, onAdd }) {
  const items = (recs || []).map((r) => ({ ...r, dish: menuById.get(r.menuItemId) })).filter((r) => r.dish && r.dish.available && !r.dish.soldOut)
  if (!items.length) return null
  return (
    <div className="mt-4 rounded-2xl border border-dashed border-brand/40 bg-brand-soft/40 p-3">
      <p className="mb-2 flex items-center gap-1.5 text-sm font-semibold text-brand-ink"><Sparkles className="size-4" /> {title}</p>
      <ul className="space-y-2">
        {items.map(({ dish, reason }) => (
          <li key={dish.id} className="flex items-center gap-3">
            <DishArt item={dish} className="size-11 shrink-0 rounded-lg [&>span]:text-xl" />
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-semibold">{dish.name} <span className="font-normal text-ink-2">· {rupees(dish.price)}</span></p>
              <p className="truncate text-xs text-muted">{reason}</p>
            </div>
            <button onClick={() => onAdd(dish)} className="grid size-8 place-items-center rounded-lg bg-brand text-white" aria-label={`Add ${dish.name}`}>
              <Plus className="size-4" />
            </button>
          </li>
        ))}
      </ul>
    </div>
  )
}
