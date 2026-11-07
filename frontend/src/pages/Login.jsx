import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { homeFor, useAuth } from '../context/AuthContext'
import { Button, ErrorNote, Input } from '../components/ui'
import AuthShell from './AuthShell'

const DEMO = [
  { label: 'Student', email: 'student@tulas.local', password: 'student123' },
  { label: 'Kitchen', email: 'kitchen@tulas.local', password: 'kitchen123' },
  { label: 'Admin', email: 'admin@tulas.local', password: 'Admin@123' },
]

export default function Login() {
  const { login } = useAuth()
  const nav = useNavigate()
  const [form, setForm] = useState({ email: '', password: '' })
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const u = await login(form.email, form.password)
      nav(homeFor(u.role), { replace: true })
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthShell title="Welcome back" sub="Log in to order from Tula's Canteen.">
      <form onSubmit={submit} className="space-y-4">
        <Input label="Email" type="email" autoComplete="email" required value={form.email}
          onChange={(e) => setForm({ ...form, email: e.target.value })} />
        <Input label="Password" type="password" autoComplete="current-password" required value={form.password}
          onChange={(e) => setForm({ ...form, password: e.target.value })} />
        <ErrorNote>{error}</ErrorNote>
        <Button type="submit" loading={busy} className="w-full" size="lg">Log in</Button>
      </form>
      <p className="mt-5 text-center text-sm text-muted">
        New here? <Link to="/register" className="font-semibold text-brand">Create an account</Link>
      </p>

      {import.meta.env.DEV && (
        <div className="mt-8 rounded-2xl border border-dashed border-line p-3">
          <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted">Demo logins (local only)</p>
          <div className="flex gap-2">
            {DEMO.map((d) => (
              <Button key={d.label} type="button" variant="outline" size="sm" className="flex-1"
                onClick={() => setForm({ email: d.email, password: d.password })}>{d.label}</Button>
            ))}
          </div>
        </div>
      )}
    </AuthShell>
  )
}
