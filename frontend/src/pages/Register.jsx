import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { Button, ErrorNote, Input } from '../components/ui'
import AuthShell from './AuthShell'

export default function Register() {
  const { register } = useAuth()
  const nav = useNavigate()
  const [form, setForm] = useState({ name: '', email: '', rollNo: '', phone: '', password: '' })
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await register(form)
      nav('/menu', { replace: true })
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthShell title="Create your account" sub="Takes 30 seconds. Then order ahead and skip the line.">
      <form onSubmit={submit} className="space-y-4">
        <Input label="Full name" required maxLength={80} autoComplete="name" value={form.name} onChange={set('name')} />
        <Input label="College email" type="email" required autoComplete="email" value={form.email} onChange={set('email')} />
        <div className="grid grid-cols-2 gap-3">
          <Input label="Roll number" maxLength={30} value={form.rollNo} onChange={set('rollNo')} />
          <Input label="Phone" inputMode="numeric" pattern="[0-9]{10}" title="10 digits" maxLength={10}
            value={form.phone} onChange={set('phone')} />
        </div>
        <Input label="Password" type="password" required minLength={6} autoComplete="new-password"
          hint="At least 6 characters" value={form.password} onChange={set('password')} />
        <ErrorNote>{error}</ErrorNote>
        <Button type="submit" loading={busy} className="w-full" size="lg">Create account</Button>
      </form>
      <p className="mt-5 text-center text-sm text-muted">
        Already have an account? <Link to="/login" className="font-semibold text-brand">Log in</Link>
      </p>
    </AuthShell>
  )
}
