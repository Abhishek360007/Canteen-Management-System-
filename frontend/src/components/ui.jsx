import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import { CheckCircle2, Clock, ChefHat, Loader2, PackageCheck, X, XCircle, UserX, BellRing } from 'lucide-react'

export function Button({ variant = 'primary', size = 'md', loading, className = '', children, ...props }) {
  const base = 'inline-flex items-center justify-center gap-2 rounded-xl font-semibold transition active:scale-[.98] disabled:opacity-50 disabled:pointer-events-none'
  const sizes = { sm: 'h-9 px-3 text-sm', md: 'h-11 px-4 text-[15px]', lg: 'h-13 px-5 text-base' }
  const variants = {
    primary: 'bg-brand text-white shadow-sm hover:brightness-110',
    soft: 'bg-brand-soft text-brand-ink hover:brightness-95',
    ghost: 'text-ink-2 hover:bg-card-2',
    outline: 'border border-line bg-card text-ink hover:bg-card-2',
    good: 'bg-good text-white hover:brightness-110 dark:text-black',
    danger: 'bg-bad-soft text-bad hover:brightness-95',
  }
  return (
    <button className={`${base} ${sizes[size]} ${variants[variant]} ${className}`} disabled={loading || props.disabled} {...props}>
      {loading && <Loader2 className="size-4 animate-spin" />}
      {children}
    </button>
  )
}

export const Card = ({ className = '', children, ...p }) => (
  <div className={`rounded-2xl border border-line bg-card ${className}`} {...p}>{children}</div>
)

export function Input({ label, hint, error, className = '', ...props }) {
  return (
    <label className={`block ${className}`}>
      {label && <span className="mb-1.5 block text-sm font-medium text-ink-2">{label}</span>}
      <input
        className="h-11 w-full rounded-xl border border-line bg-card px-3 text-[15px] text-ink outline-none placeholder:text-muted focus:border-brand focus:ring-2 focus:ring-brand/20"
        {...props}
      />
      {error ? <span className="mt-1 block text-xs text-bad">{error}</span> : hint && <span className="mt-1 block text-xs text-muted">{hint}</span>}
    </label>
  )
}

export function Select({ label, children, className = '', ...props }) {
  return (
    <label className={`block ${className}`}>
      {label && <span className="mb-1.5 block text-sm font-medium text-ink-2">{label}</span>}
      <select className="h-11 w-full rounded-xl border border-line bg-card px-3 text-[15px] text-ink outline-none focus:border-brand" {...props}>
        {children}
      </select>
    </label>
  )
}

export function Toggle({ checked, onChange, label, disabled }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className={`relative h-7 w-12 shrink-0 rounded-full transition disabled:opacity-50 ${checked ? 'bg-good' : 'bg-line'}`}
    >
      <span className={`absolute top-0.5 size-6 rounded-full bg-white shadow transition-all ${checked ? 'left-[22px]' : 'left-0.5'}`} />
    </button>
  )
}

export const Spinner = ({ label = 'Loading…' }) => (
  <div className="flex items-center justify-center gap-2 py-16 text-muted">
    <Loader2 className="size-5 animate-spin" /> {label}
  </div>
)

export const Empty = ({ icon: Icon, title, children }) => (
  <div className="flex flex-col items-center px-6 py-14 text-center">
    {Icon && <div className="mb-3 grid size-14 place-items-center rounded-2xl bg-brand-soft text-brand-ink"><Icon className="size-7" /></div>}
    <p className="font-display text-lg font-semibold">{title}</p>
    {children && <div className="mt-1 max-w-xs text-sm text-muted">{children}</div>}
  </div>
)

export const ErrorNote = ({ children }) =>
  children ? <div className="rounded-xl bg-bad-soft px-3 py-2.5 text-sm text-bad">{children}</div> : null

export const VegDot = ({ veg }) => (
  <span title={veg ? 'Veg' : 'Non-veg'} aria-label={veg ? 'Veg' : 'Non-veg'}
    className={`inline-grid size-4 shrink-0 place-items-center rounded-[4px] border-2 ${veg ? 'border-good' : 'border-bad'}`}>
    <span className={`size-1.5 rounded-full ${veg ? 'bg-good' : 'bg-bad'}`} />
  </span>
)

const STATUS = {
  PLACED: { label: 'Placed', cls: 'bg-warn-soft text-warn', icon: Clock },
  PREPARING: { label: 'Cooking', cls: 'bg-brand-soft text-brand-ink', icon: ChefHat },
  READY: { label: 'Ready', cls: 'bg-good-soft text-good', icon: BellRing },
  COLLECTED: { label: 'Collected', cls: 'bg-card-2 text-ink-2', icon: PackageCheck },
  CANCELLED: { label: 'Cancelled', cls: 'bg-bad-soft text-bad', icon: XCircle },
  NO_SHOW: { label: 'Not collected', cls: 'bg-bad-soft text-bad', icon: UserX },
}

export function StatusBadge({ status }) {
  const s = STATUS[status] || { label: status, cls: 'bg-card-2', icon: CheckCircle2 }
  const Icon = s.icon
  return (
    <span className={`inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-semibold ${s.cls}`}>
      <Icon className="size-3.5" /> {s.label}
    </span>
  )
}

export function Modal({ open, onClose, title, children }) {
  useEffect(() => {
    if (!open) return
    const onKey = (e) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, onClose])
  if (!open) return null
  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/40 sm:items-center" onClick={onClose}>
      <div role="dialog" aria-modal="true" aria-label={title}
        className="animate-pop max-h-[90vh] w-full overflow-y-auto rounded-t-3xl bg-card p-5 shadow-xl sm:max-w-md sm:rounded-3xl"
        onClick={(e) => e.stopPropagation()}>
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-bold">{title}</h2>
          <button onClick={onClose} className="rounded-lg p-1.5 text-muted hover:bg-card-2" aria-label="Close"><X className="size-5" /></button>
        </div>
        {children}
      </div>
    </div>
  )
}

// ---- toasts

const ToastContext = createContext(() => {})

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([])
  const push = useCallback((message, kind = 'info') => {
    const id = Math.random()
    setToasts((t) => [...t, { id, message, kind }])
    setTimeout(() => setToasts((t) => t.filter((x) => x.id !== id)), 3500)
  }, [])
  return (
    <ToastContext.Provider value={push}>
      {children}
      <div className="pointer-events-none fixed inset-x-0 top-3 z-[60] flex flex-col items-center gap-2 px-4" aria-live="polite">
        {toasts.map((t) => (
          <div key={t.id} className={`animate-pop pointer-events-auto max-w-sm rounded-xl px-4 py-2.5 text-sm font-medium shadow-lg ${
            t.kind === 'error' ? 'bg-bad text-white' : t.kind === 'good' ? 'bg-good text-white dark:text-black' : 'bg-ink text-bg'}`}>
            {t.message}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

export const useToast = () => useContext(ToastContext)

export const PageTitle = ({ title, sub, right }) => (
  <div className="mb-4 flex items-end justify-between gap-3">
    <div>
      <h1 className="text-2xl font-bold tracking-tight">{title}</h1>
      {sub && <p className="mt-0.5 text-sm text-muted">{sub}</p>}
    </div>
    {right}
  </div>
)
