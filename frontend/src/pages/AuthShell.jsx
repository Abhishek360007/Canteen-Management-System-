import { Clock, QrCode, Wallet } from 'lucide-react'
import { Logo } from '../components/Brand'

const FOOD = ['🥞', '🍛', '🍱', '🥟', '☕', '🍗', '🥪', '🍮', '🍋', '🌯', '🧀', '🥤']

/** Split screen on desktop, stacked on phones: warm food banner + the form card. */
export default function AuthShell({ title, sub, children }) {
  return (
    <div className="grid min-h-dvh lg:grid-cols-2">
      <section className="relative overflow-hidden bg-brand px-6 pb-10 pt-10 text-white lg:flex lg:flex-col lg:justify-between lg:p-12">
        <div aria-hidden="true" className="pointer-events-none absolute inset-0 grid grid-cols-4 gap-6 p-6 text-4xl opacity-15 sm:grid-cols-6 lg:text-5xl">
          {FOOD.concat(FOOD).map((f, i) => <span key={i} className={i % 2 ? 'translate-y-6' : ''}>{f}</span>)}
        </div>
        <div className="relative flex items-center gap-3">
          <Logo className="size-11 [&>rect]:fill-white/15" />
          <span className="font-display text-xl font-bold">Tula's Canteen</span>
        </div>
        <div className="relative mt-8 lg:mt-0">
          <h1 className="font-display text-3xl font-extrabold leading-tight sm:text-4xl lg:text-5xl">
            Order ahead.<br />Skip the queue.
          </h1>
          <ul className="mt-6 hidden space-y-3 text-white/90 sm:block">
            <li className="flex items-center gap-3"><Clock className="size-5" /> Pick a time, food is ready when you arrive</li>
            <li className="flex items-center gap-3"><QrCode className="size-5" /> Collect with your order number and code</li>
            <li className="flex items-center gap-3"><Wallet className="size-5" /> Pay from your canteen wallet or at the counter</li>
          </ul>
        </div>
        <p className="relative mt-8 hidden text-sm text-white/70 lg:block">Fresh food, no waiting.</p>
      </section>

      <section className="flex items-start justify-center px-4 py-8 lg:items-center">
        <div className="w-full max-w-sm">
          <h2 className="text-2xl font-bold">{title}</h2>
          {sub && <p className="mt-1 text-sm text-muted">{sub}</p>}
          <div className="mt-6">{children}</div>
        </div>
      </section>
    </div>
  )
}
