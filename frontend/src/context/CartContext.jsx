import { createContext, useContext, useEffect, useMemo, useState } from 'react'

const CartContext = createContext(null)
const KEY = 'tc_cart'

function load() {
  try { return JSON.parse(localStorage.getItem(KEY)) || {} } catch { return {} }
}

/** Cart lines keyed by dish id: { [id]: { item, qty } }. Kept in localStorage so a refresh doesn't empty it. */
export function CartProvider({ children }) {
  const [lines, setLines] = useState(load)

  useEffect(() => {
    try { localStorage.setItem(KEY, JSON.stringify(lines)) } catch { /* ignore */ }
  }, [lines])

  const api = useMemo(() => {
    const list = Object.values(lines)
    return {
      lines: list,
      count: list.reduce((s, l) => s + l.qty, 0),
      total: list.reduce((s, l) => s + l.qty * Number(l.item.price), 0),
      qtyOf: (id) => lines[id]?.qty || 0,
      /** The canteen this cart is for (all lines are from one canteen). */
      canteenId: list[0]?.item.canteenId ?? null,
      add: (item) => {
        // One canteen per order: adding a dish from another canteen starts a new cart (after asking).
        const other = list.some((l) => l.item.canteenId !== item.canteenId)
        if (other && !window.confirm('Your cart has dishes from another canteen. Start a new cart for this one?')) return
        setLines((c) => {
          const base = other ? {} : c
          return { ...base, [item.id]: { item, qty: (base[item.id]?.qty || 0) + 1 } }
        })
      },
      remove: (id) =>
        setLines((c) => {
          const q = (c[id]?.qty || 0) - 1
          const next = { ...c }
          if (q <= 0) delete next[id]
          else next[id] = { ...c[id], qty: q }
          return next
        }),
      drop: (id) => setLines((c) => { const n = { ...c }; delete n[id]; return n }),
      /** Refresh stored dish info (price, stock) from the latest menu. */
      sync: (menu) =>
        setLines((c) => {
          let changed = false
          const next = { ...c }
          for (const id of Object.keys(c)) {
            const fresh = menu.find((m) => String(m.id) === id)
            if (!fresh) { delete next[id]; changed = true } else if (fresh !== c[id].item) { next[id] = { ...c[id], item: fresh }; changed = true }
          }
          return changed ? next : c
        }),
      clear: () => setLines({}),
    }
  }, [lines])

  return <CartContext.Provider value={api}>{children}</CartContext.Provider>
}

export const useCart = () => useContext(CartContext)
