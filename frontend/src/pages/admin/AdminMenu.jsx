import { useCallback, useEffect, useState } from 'react'
import { ListPlus, Pencil, Plus, Star, Trash2 } from 'lucide-react'
import { api, rupees } from '../../api'
import { DishArt } from '../../components/Brand'
import { Button, Card, ErrorNote, Input, Modal, PageTitle, Select, Spinner, Toggle, VegDot, useToast } from '../../components/ui'

const CATS = ['BREAKFAST', 'MEALS', 'SNACKS', 'BEVERAGES', 'DESSERTS']
const EMPTY = { name: '', description: '', price: '', category: 'MEALS', veg: true, emoji: '🍽️', imageUrl: '',
  available: true, stockToday: '', defaultStock: '', prepMinutes: 10 }

export default function AdminMenu() {
  const toast = useToast()
  const [menu, setMenu] = useState(null)
  const [error, setError] = useState(null)
  const [edit, setEdit] = useState(null) // null | form object (with optional id)
  const [formError, setFormError] = useState(null)
  const [busy, setBusy] = useState(false)

  const load = useCallback(() => api('/api/menu').then(setMenu).catch((e) => setError(e.message)), [])
  useEffect(() => { load() }, [load])

  const open = (item) => {
    setFormError(null)
    setEdit(item ? {
      ...item, description: item.description || '', imageUrl: item.imageUrl || '', emoji: item.emoji || '🍽️',
      stockToday: item.stockToday ?? '', defaultStock: item.defaultStock ?? '',
    } : { ...EMPTY })
  }

  const save = async (e) => {
    e.preventDefault()
    setBusy(true)
    setFormError(null)
    const num = (v) => (v === '' || v === null ? null : Number(v))
    const body = { ...edit, price: Number(edit.price), stockToday: num(edit.stockToday), defaultStock: num(edit.defaultStock), prepMinutes: Number(edit.prepMinutes) }
    try {
      await api(edit.id ? `/api/admin/menu/${edit.id}` : '/api/admin/menu', { method: edit.id ? 'PUT' : 'POST', body })
      toast(edit.id ? 'Dish updated' : 'Dish added', 'good')
      setEdit(null)
      load()
    } catch (err) {
      setFormError(err.message)
    } finally {
      setBusy(false)
    }
  }

  const [importing, setImporting] = useState(false)
  const importDefaults = async () => {
    if (!confirm("Add Tula's standard canteen menu (dosa, thali, biryani, snacks, drinks, desserts)? Dishes you already have are skipped.")) return
    setImporting(true)
    try {
      const r = await api('/api/admin/menu/import-defaults', { method: 'POST' })
      toast(r.added ? `${r.added} dishes added` : 'All standard dishes are already on your menu', 'good')
      load()
    } catch (e) {
      toast(e.message, 'error')
    } finally {
      setImporting(false)
    }
  }

  const remove = async (item) => {
    if (!confirm(`Remove ${item.name} from the menu? Past orders keep their history.`)) return
    try {
      await api(`/api/admin/menu/${item.id}`, { method: 'DELETE' })
      load()
    } catch (e) {
      toast(e.message, 'error')
    }
  }

  const set = (k) => (e) => setEdit({ ...edit, [k]: e.target.value })

  return (
    <>
      <PageTitle title="Menu" sub={menu ? `${menu.length} dishes` : null}
        right={
          <div className="flex gap-2">
            <Button size="sm" variant="outline" loading={importing} onClick={importDefaults} title="Add a standard canteen menu">
              <ListPlus className="size-4" /> <span className="hidden sm:inline">Standard menu</span>
            </Button>
            <Button size="sm" onClick={() => open(null)}><Plus className="size-4" /> Add dish</Button>
          </div>
        } />
      <ErrorNote>{error}</ErrorNote>
      {menu && menu.length === 0 && (
        <Card className="mb-4 p-5 text-center">
          <p className="font-semibold">Your menu is empty</p>
          <p className="mt-1 text-sm text-muted">Start with a standard canteen menu of 40+ dishes, then edit prices and remove what you don't make.</p>
          <Button className="mt-3" loading={importing} onClick={importDefaults}><ListPlus className="size-4" /> Add standard menu</Button>
        </Card>
      )}
      {!menu ? <Spinner /> : CATS.map((cat) => {
        const items = menu.filter((m) => m.category === cat)
        if (!items.length) return null
        return (
          <section key={cat} className="mb-5">
            <h2 className="mb-2 text-sm font-semibold uppercase tracking-wide text-muted">{cat.toLowerCase()}</h2>
            <Card className="divide-y divide-line">
              {items.map((m) => (
                <div key={m.id} className="flex items-center gap-3 p-3">
                  <DishArt item={m} className="size-12 rounded-xl [&>span]:text-2xl" />
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-1.5"><VegDot veg={m.veg} /><p className="truncate font-semibold">{m.name}</p>{!m.available && <span className="rounded bg-card-2 px-1.5 text-[11px] text-muted">off today</span>}</div>
                    <p className="text-xs text-muted">
                      {rupees(m.price)} · {m.defaultStock == null ? 'no daily limit' : `${m.defaultStock}/day`} · {m.prepMinutes} min
                      {m.rating && <> · <Star className="inline size-3 fill-warn text-warn" /> {m.rating} ({m.ratingCount})</>}
                    </p>
                  </div>
                  <button onClick={() => open(m)} className="rounded-lg p-2 text-ink-2 hover:bg-card-2" aria-label={`Edit ${m.name}`}><Pencil className="size-4" /></button>
                  <button onClick={() => remove(m)} className="rounded-lg p-2 text-bad hover:bg-bad-soft" aria-label={`Remove ${m.name}`}><Trash2 className="size-4" /></button>
                </div>
              ))}
            </Card>
          </section>
        )
      })}

      <Modal open={!!edit} onClose={() => setEdit(null)} title={edit?.id ? 'Edit dish' : 'Add dish'}>
        {edit && (
          <form onSubmit={save} className="space-y-3">
            <div className="grid grid-cols-[4.5rem_1fr] gap-3">
              <Input label="Emoji" maxLength={8} value={edit.emoji} onChange={set('emoji')} className="[&_input]:text-center [&_input]:text-xl" />
              <Input label="Name" required maxLength={80} value={edit.name} onChange={set('name')} />
            </div>
            <Input label="Description" maxLength={240} value={edit.description} onChange={set('description')} />
            <div className="grid grid-cols-2 gap-3">
              <Input label="Price (₹)" type="number" required min={1} max={5000} step="0.5" value={edit.price} onChange={set('price')} />
              <Select label="Category" value={edit.category} onChange={set('category')}>
                {CATS.map((c) => <option key={c} value={c}>{c.charAt(0) + c.slice(1).toLowerCase()}</option>)}
              </Select>
            </div>
            <div className="grid grid-cols-3 gap-3">
              <Input label="Plates/day" type="number" min={0} max={9999} placeholder="No limit" value={edit.defaultStock} onChange={set('defaultStock')} />
              <Input label="Left today" type="number" min={0} max={9999} placeholder="No limit" value={edit.stockToday} onChange={set('stockToday')} />
              <Input label="Prep (min)" type="number" required min={1} max={120} value={edit.prepMinutes} onChange={set('prepMinutes')} />
            </div>
            <Input label="Photo link (optional)" type="url" maxLength={500} placeholder="https://…" value={edit.imageUrl} onChange={set('imageUrl')}
              hint="Leave empty to show the emoji tile." />
            <div className="flex items-center justify-between rounded-xl bg-card-2 p-3">
              <span className="flex items-center gap-2 text-sm font-medium"><VegDot veg={edit.veg} /> Vegetarian</span>
              <Toggle checked={edit.veg} onChange={(v) => setEdit({ ...edit, veg: v })} label="Vegetarian" />
            </div>
            <div className="flex items-center justify-between rounded-xl bg-card-2 p-3">
              <span className="text-sm font-medium">On today's menu</span>
              <Toggle checked={edit.available} onChange={(v) => setEdit({ ...edit, available: v })} label="On today's menu" />
            </div>
            <ErrorNote>{formError}</ErrorNote>
            <Button type="submit" className="w-full" loading={busy}>{edit.id ? 'Save changes' : 'Add dish'}</Button>
          </form>
        )}
      </Modal>
    </>
  )
}
