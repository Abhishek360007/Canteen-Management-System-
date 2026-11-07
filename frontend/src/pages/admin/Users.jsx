import { useCallback, useEffect, useState } from 'react'
import { Ban, IndianRupee, KeyRound, LogOut, Search, UserPlus, Users as UsersIcon } from 'lucide-react'
import { api, dateOf, rupees, timeOf } from '../../api'
import { Button, Card, Empty, ErrorNote, Input, Modal, PageTitle, Select, Spinner, useToast } from '../../components/ui'
import { useCanteens } from '../../context/CanteenContext'

const ROLES = [['', 'Everyone'], ['STUDENT', 'Students'], ['STAFF', 'Kitchen staff'], ['ADMIN', 'Admins']]

export default function UsersPage() {
  const toast = useToast()
  const [q, setQ] = useState('')
  const [role, setRole] = useState('STUDENT')
  const [page, setPage] = useState(0)
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  const [recharge, setRecharge] = useState(null)
  const [history, setHistory] = useState(null)
  const [staffOpen, setStaffOpen] = useState(false)
  const [temp, setTemp] = useState(null)
  const { canteens } = useCanteens()

  const load = useCallback(() => {
    const p = new URLSearchParams({ page })
    if (q.trim()) p.set('q', q.trim())
    if (role) p.set('role', role)
    api(`/api/admin/users?${p}`).then(setData).catch((e) => setError(e.message))
  }, [q, role, page])

  useEffect(() => {
    const t = setTimeout(load, 250) // wait for typing to pause
    return () => clearTimeout(t)
  }, [load])

  const toggleBlock = async (u) => {
    if (!confirm(`${u.blocked ? 'Unblock' : 'Block'} ${u.name}?${u.blocked ? '' : ' They will be logged out and can\'t order.'}`)) return
    try {
      await api(`/api/admin/users/${u.id}/block`, { method: 'POST', body: { blocked: !u.blocked } })
      load()
    } catch (e) {
      toast(e.message, 'error')
    }
  }

  const moveStaff = async (u, canteenId) => {
    try {
      await api(`/api/admin/users/${u.id}/canteen`, { method: 'PUT', body: { canteenId: Number(canteenId) } })
      toast(`${u.name} now works at ${canteens.find((c) => c.id === Number(canteenId))?.name}`, 'good')
      load()
    } catch (e) {
      toast(e.message, 'error')
    }
  }

  const resetPassword = async (u) => {
    if (!confirm(`Reset ${u.name}'s password?\n\nThey get a temporary password and are logged out on every device.`)) return
    try {
      const r = await api(`/api/admin/users/${u.id}/reset-password`, { method: 'POST' })
      setTemp({ user: u, password: r.tempPassword })
    } catch (e) {
      toast(e.message, 'error')
    }
  }

  const logoutEverywhere = async (u) => {
    if (!confirm(`Log ${u.name} out on all devices? Use this if they lost their phone.`)) return
    try {
      await api(`/api/admin/users/${u.id}/logout-everywhere`, { method: 'POST' })
      toast(`${u.name} has been logged out everywhere`, 'good')
    } catch (e) {
      toast(e.message, 'error')
    }
  }

  const showHistory = async (u) => {
    setHistory({ user: u, wallet: null })
    try {
      setHistory({ user: u, wallet: await api(`/api/admin/users/${u.id}/wallet`) })
    } catch (e) {
      toast(e.message, 'error')
      setHistory(null)
    }
  }

  return (
    <>
      <PageTitle title="Users" right={<Button size="sm" variant="outline" onClick={() => setStaffOpen(true)}><UserPlus className="size-4" /> Add staff</Button>} />

      <div className="mb-3 flex flex-col gap-2 sm:flex-row">
        <div className="relative flex-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 size-4.5 -translate-y-1/2 text-muted" />
          <input value={q} onChange={(e) => { setQ(e.target.value); setPage(0) }} placeholder="Search name, email or roll number" aria-label="Search users"
            className="h-11 w-full rounded-xl border border-line bg-card pl-10 pr-3 outline-none focus:border-brand" />
        </div>
        <div className="no-scrollbar flex gap-1.5 overflow-x-auto">
          {ROLES.map(([r, label]) => (
            <button key={r} onClick={() => { setRole(r); setPage(0) }}
              className={`shrink-0 rounded-full px-3.5 py-2 text-sm font-semibold ${role === r ? 'bg-ink text-bg' : 'border border-line bg-card text-ink-2'}`}>{label}</button>
          ))}
        </div>
      </div>

      <ErrorNote>{error}</ErrorNote>
      {!data ? <Spinner /> : data.items.length === 0 ? <Empty icon={UsersIcon} title="No users found" /> : (
        <>
          <Card className="divide-y divide-line">
            {data.items.map((u) => (
              <div key={u.id} className="flex flex-wrap items-center gap-3 p-3.5">
                <div className="min-w-0 basis-full sm:flex-1 sm:basis-auto">
                  <p className="font-semibold">{u.name} {u.blocked && <span className="ml-1 rounded bg-bad-soft px-1.5 text-xs font-semibold text-bad">Blocked</span>}</p>
                  <p className="truncate text-sm text-muted">{u.email}{u.rollNo ? ` · ${u.rollNo}` : ''}{u.phone ? ` · ${u.phone}` : ''}</p>
                </div>
                {u.role === 'STUDENT' ? (
                  <button onClick={() => showHistory(u)} className="mr-auto text-left hover:underline sm:mr-0 sm:text-right" title="Wallet history">
                    <p className="font-display font-bold">{rupees(u.walletBalance)}</p>
                    <p className="text-xs text-muted">wallet</p>
                  </button>
                ) : u.role === 'STAFF' && canteens.length > 1 ? (
                  <select value={u.canteenId ?? ''} aria-label={`Canteen for ${u.name}`} onChange={(e) => moveStaff(u, e.target.value)}
                    className="mr-auto rounded-lg border border-line bg-card px-2 py-1 text-xs font-semibold text-ink-2 sm:mr-0">
                    {canteens.map((c) => <option key={c.id} value={c.id}>Kitchen · {c.name}</option>)}
                  </select>
                ) : <span className="rounded-full bg-card-2 px-2.5 py-1 text-xs font-semibold text-ink-2">{u.role === 'STAFF' ? 'Kitchen' : 'Admin'}</span>}
                <div className="flex gap-1.5">
                  {u.role === 'STUDENT' && <Button size="sm" variant="soft" onClick={() => setRecharge(u)}><IndianRupee className="size-4" /> Recharge</Button>}
                  {u.role !== 'ADMIN' && (
                    <>
                      <Button size="sm" variant="ghost" onClick={() => resetPassword(u)} title="Reset password" aria-label={`Reset ${u.name}'s password`}>
                        <KeyRound className="size-4" />
                      </Button>
                      <Button size="sm" variant="ghost" onClick={() => logoutEverywhere(u)} title="Log out on all devices" aria-label={`Log ${u.name} out on all devices`}>
                        <LogOut className="size-4" />
                      </Button>
                      <Button size="sm" variant={u.blocked ? 'outline' : 'ghost'} onClick={() => toggleBlock(u)} title={u.blocked ? 'Unblock' : 'Block'} aria-label={u.blocked ? 'Unblock' : 'Block'}>
                        <Ban className="size-4" />{u.blocked ? 'Unblock' : ''}
                      </Button>
                    </>
                  )}
                </div>
              </div>
            ))}
          </Card>
          {data.totalPages > 1 && (
            <div className="mt-3 flex items-center justify-center gap-3 text-sm">
              <Button size="sm" variant="outline" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</Button>
              <span className="text-muted">Page {page + 1} of {data.totalPages}</span>
              <Button size="sm" variant="outline" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>Next</Button>
            </div>
          )}
        </>
      )}

      <RechargeModal user={recharge} onClose={() => setRecharge(null)} onDone={() => { setRecharge(null); load() }} />
      <StaffModal open={staffOpen} onClose={() => setStaffOpen(false)} onDone={() => { setStaffOpen(false); setRole('STAFF') }} />

      <Modal open={!!temp} onClose={() => setTemp(null)} title="Temporary password">
        <p className="text-sm text-ink-2">Give this to <b>{temp?.user.name}</b> in person. They should change it under Account after logging in. It won't be shown again.</p>
        <p className="my-4 select-all rounded-xl bg-card-2 py-4 text-center font-mono text-2xl font-bold tracking-wider">{temp?.password}</p>
        <Button className="w-full" onClick={() => { navigator.clipboard?.writeText(temp.password).catch(() => {}); toast('Copied', 'good') }}>Copy</Button>
      </Modal>

      <Modal open={!!history} onClose={() => setHistory(null)} title={`${history?.user.name}'s wallet`}>
        {!history?.wallet ? <Spinner /> : (
          <>
            <p className="mb-3 font-display text-3xl font-bold">{rupees(history.wallet.balance)}</p>
            {history.wallet.transactions.length === 0 ? <p className="text-sm text-muted">No transactions yet.</p> : (
              <ul className="divide-y divide-line text-sm">
                {history.wallet.transactions.map((t) => (
                  <li key={t.id} className="flex justify-between gap-3 py-2">
                    <span><span className="font-medium">{t.note}</span><br /><span className="text-xs text-muted">{dateOf(t.createdAt)}, {timeOf(t.createdAt)} · {t.createdBy}</span></span>
                    <span className={`font-semibold ${Number(t.amount) > 0 ? 'text-good' : ''}`}>{Number(t.amount) > 0 ? '+' : '−'}{rupees(Math.abs(t.amount))}</span>
                  </li>
                ))}
              </ul>
            )}
          </>
        )}
      </Modal>
    </>
  )
}

function RechargeModal({ user, onClose, onDone }) {
  const toast = useToast()
  const [amount, setAmount] = useState('')
  const [note, setNote] = useState('')
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)
  useEffect(() => { setAmount(''); setNote(''); setError(null) }, [user])

  const submit = async (e) => {
    e.preventDefault()
    if (!confirm(`Add ${rupees(amount)} to ${user.name}'s wallet? Make sure you've received the money.`)) return
    setBusy(true)
    try {
      await api(`/api/admin/users/${user.id}/recharge`, { method: 'POST', body: { amount: Number(amount), note } })
      toast(`${rupees(amount)} added to ${user.name}'s wallet`, 'good')
      onDone()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal open={!!user} onClose={onClose} title={`Recharge ${user?.name}`}>
      <form onSubmit={submit} className="space-y-3">
        <p className="text-sm text-muted">Current balance {rupees(user?.walletBalance)}</p>
        <div className="flex gap-2">
          {[100, 200, 500, 1000].map((a) => (
            <button type="button" key={a} onClick={() => setAmount(String(a))}
              className={`flex-1 rounded-xl border py-2 text-sm font-semibold ${amount === String(a) ? 'border-brand bg-brand-soft text-brand-ink' : 'border-line'}`}>₹{a}</button>
          ))}
        </div>
        <Input label="Amount (₹)" type="number" required min={10} max={5000} step="1" value={amount} onChange={(e) => setAmount(e.target.value)} />
        <Input label="Note" maxLength={120} placeholder="Cash / UPI ref" value={note} onChange={(e) => setNote(e.target.value)} />
        <ErrorNote>{error}</ErrorNote>
        <Button type="submit" className="w-full" loading={busy} disabled={!amount}>Add money</Button>
      </form>
    </Modal>
  )
}

function StaffModal({ open, onClose, onDone }) {
  const toast = useToast()
  const { canteens, selectedId } = useCanteens()
  const [f, setF] = useState({ name: '', email: '', password: '', phone: '', canteenId: '' })
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value })

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await api('/api/admin/staff', { method: 'POST', body: { ...f, canteenId: Number(f.canteenId || selectedId) || null } })
      toast(`Kitchen login created for ${f.name}`, 'good')
      setF({ name: '', email: '', password: '', phone: '', canteenId: '' })
      onDone()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal open={open} onClose={onClose} title="Add kitchen staff">
      <form onSubmit={submit} className="space-y-3">
        <Input label="Name" required maxLength={80} value={f.name} onChange={set('name')} />
        <Input label="Email (used to log in)" type="email" required value={f.email} onChange={set('email')} />
        <Input label="Phone" inputMode="numeric" pattern="[0-9]{10}" maxLength={10} value={f.phone} onChange={set('phone')} />
        {canteens.length > 1 && (
          <Select label="Works at" value={f.canteenId || selectedId || ''} onChange={set('canteenId')}>
            {canteens.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </Select>
        )}
        <Input label="Temporary password" type="text" required minLength={6} value={f.password} onChange={set('password')}
          hint="Share it with them; they can change it under Account." />
        <ErrorNote>{error}</ErrorNote>
        <Button type="submit" className="w-full" loading={busy}>Create login</Button>
      </form>
    </Modal>
  )
}
