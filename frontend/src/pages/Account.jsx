import { useState } from 'react'
import { LogOut, Moon, Sun } from 'lucide-react'
import { api, tokenStore } from '../api'
import { useAuth } from '../context/AuthContext'
import { useTheme } from '../context/ThemeContext'
import { Button, Card, ErrorNote, Input, PageTitle, useToast } from '../components/ui'
import PushToggle from '../components/PushToggle'

export default function Account() {
  const { user, setUser, logout } = useAuth()
  const { dark, toggle } = useTheme()
  const toast = useToast()
  const [profile, setProfile] = useState({ name: user.name, phone: user.phone || '' })
  const [pw, setPw] = useState({ currentPassword: '', newPassword: '' })
  const [errors, setErrors] = useState({})
  const [busy, setBusy] = useState(null)

  const saveProfile = async (e) => {
    e.preventDefault()
    setBusy('profile')
    setErrors({})
    try {
      setUser(await api('/api/auth/me', { method: 'PUT', body: profile }))
      toast('Profile saved', 'good')
    } catch (err) {
      setErrors({ profile: err.message })
    } finally {
      setBusy(null)
    }
  }

  const changePassword = async (e) => {
    e.preventDefault()
    setBusy('pw')
    setErrors({})
    try {
      // The server logs out every other device and returns a fresh token for this one.
      const res = await api('/api/auth/password', { method: 'POST', body: pw })
      tokenStore.set(res.token)
      setUser(res.user)
      setPw({ currentPassword: '', newPassword: '' })
      toast('Password changed. Other devices have been logged out.', 'good')
    } catch (err) {
      setErrors({ pw: err.message })
    } finally {
      setBusy(null)
    }
  }

  return (
    <>
      <PageTitle title="Account" sub={user.email} />
      <div className="space-y-4">
        <Card className="p-4">
          <div className="flex items-center gap-3">
            <div className="grid size-12 place-items-center rounded-2xl bg-brand-soft font-display text-lg font-bold text-brand-ink">
              {user.name.split(' ').map((w) => w[0]).slice(0, 2).join('').toUpperCase()}
            </div>
            <div>
              <p className="font-semibold">{user.name}</p>
              <p className="text-sm text-muted">
                {user.role === 'STUDENT' ? `Student${user.rollNo ? ' · ' + user.rollNo : ''}` : user.role === 'STAFF' ? 'Kitchen staff' : 'Administrator'}
              </p>
            </div>
          </div>
        </Card>

        <PushToggle role={user.role} />

        <Card className="p-4">
          <h2 className="mb-3 font-semibold">Profile</h2>
          <form onSubmit={saveProfile} className="space-y-3">
            <Input label="Name" required maxLength={80} value={profile.name} onChange={(e) => setProfile({ ...profile, name: e.target.value })} />
            <Input label="Phone" inputMode="numeric" pattern="[0-9]{10}" title="10 digits" maxLength={10}
              value={profile.phone} onChange={(e) => setProfile({ ...profile, phone: e.target.value })} />
            <ErrorNote>{errors.profile}</ErrorNote>
            <Button type="submit" variant="soft" loading={busy === 'profile'}>Save profile</Button>
          </form>
        </Card>

        <Card className="p-4">
          <h2 className="mb-3 font-semibold">Change password</h2>
          <form onSubmit={changePassword} className="space-y-3">
            <Input label="Current password" type="password" required autoComplete="current-password"
              value={pw.currentPassword} onChange={(e) => setPw({ ...pw, currentPassword: e.target.value })} />
            <Input label="New password" type="password" required minLength={6} autoComplete="new-password"
              value={pw.newPassword} onChange={(e) => setPw({ ...pw, newPassword: e.target.value })} />
            <ErrorNote>{errors.pw}</ErrorNote>
            <Button type="submit" variant="soft" loading={busy === 'pw'}>Change password</Button>
          </form>
        </Card>

        <Card className="divide-y divide-line">
          <button onClick={toggle} className="flex w-full items-center gap-3 p-4 text-left hover:bg-card-2">
            {dark ? <Sun className="size-5 text-muted" /> : <Moon className="size-5 text-muted" />}
            <span className="font-medium">{dark ? 'Light mode' : 'Dark mode'}</span>
          </button>
          <button onClick={logout} className="flex w-full items-center gap-3 p-4 text-left text-bad hover:bg-card-2">
            <LogOut className="size-5" /> <span className="font-medium">Log out</span>
          </button>
        </Card>
      </div>
    </>
  )
}
