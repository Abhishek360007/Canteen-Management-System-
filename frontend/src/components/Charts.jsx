import { useEffect, useRef, useState } from 'react'

/** Width of an element in CSS pixels, kept up to date as it resizes. */
function useWidth() {
  const ref = useRef(null)
  const [w, setW] = useState(600)
  useEffect(() => {
    if (!ref.current) return
    const ro = new ResizeObserver(([e]) => setW(Math.max(200, Math.round(e.contentRect.width))))
    ro.observe(ref.current)
    return () => ro.disconnect()
  }, [])
  return [ref, w]
}

/**
 * Single-series vertical bar chart (SVG). One hue (--chart), thin bars with rounded data-ends,
 * recessive grid, hover/focus tooltip per bar, and a screen-reader table.
 * The SVG is drawn at its real pixel width so labels stay 12px at any size.
 */
export function BarChart({ data, format = (v) => v, height = 180, label }) {
  const [hover, setHover] = useState(null)
  const [box, W] = useWidth()
  const max = Math.max(1, ...data.map((d) => d.value))
  const pad = { t: 12, b: 26, l: 8, r: 8 }
  const innerH = height - pad.t - pad.b
  const slot = (W - pad.l - pad.r) / data.length
  const barW = Math.min(36, slot - 2 - slot * 0.35)
  const r = Math.min(4, barW / 2)

  return (
    <div className="relative" ref={box}>
      <svg viewBox={`0 0 ${W} ${height}`} width={W} height={height} className="block max-w-full" role="img" aria-label={label}>
        {[0.25, 0.5, 0.75, 1].map((f) => (
          <line key={f} x1={pad.l} x2={W - pad.r} y1={pad.t + innerH * (1 - f)} y2={pad.t + innerH * (1 - f)}
            stroke="var(--chart-grid)" strokeWidth="1" />
        ))}
        <line x1={pad.l} x2={W - pad.r} y1={pad.t + innerH} y2={pad.t + innerH} stroke="var(--line)" strokeWidth="1" />
        {data.map((d, i) => {
          const h = d.value > 0 ? Math.max(2, (d.value / max) * innerH) : 0
          const x = pad.l + slot * i + (slot - barW) / 2
          const y = pad.t + innerH - h
          const rr = Math.min(r, h)
          // Rounded top only; square at the baseline.
          const path = h > 0
            ? `M${x},${y + h} V${y + rr} Q${x},${y} ${x + rr},${y} H${x + barW - rr} Q${x + barW},${y} ${x + barW},${y + rr} V${y + h} Z`
            : ''
          return (
            <g key={d.label}
              onMouseEnter={() => setHover(i)} onMouseLeave={() => setHover(null)}
              onFocus={() => setHover(i)} onBlur={() => setHover(null)} tabIndex={0}
              aria-label={`${d.label}: ${format(d.value)}`} className="outline-none">
              <rect x={pad.l + slot * i} y={pad.t} width={slot} height={innerH} fill="transparent" />
              {path && <path d={path} fill="var(--chart)" opacity={hover === null || hover === i ? 1 : 0.45} />}
              <text x={x + barW / 2} y={height - 8} textAnchor="middle" fontSize="12" fill="var(--muted)">{d.short ?? d.label}</text>
            </g>
          )
        })}
      </svg>
      {hover !== null && (
        <div className="pointer-events-none absolute -top-2 rounded-lg border border-line bg-card px-2.5 py-1.5 text-xs shadow-md"
          style={{ left: `${((pad.l + slot * hover + slot / 2) / W) * 100}%`, transform: 'translate(-50%, -100%)' }}>
          <div className="text-muted">{data[hover].label}</div>
          <div className="font-semibold text-ink">{format(data[hover].value)}</div>
          {data[hover].extra && <div className="text-ink-2">{data[hover].extra}</div>}
        </div>
      )}
      <table className="sr-only">
        <caption>{label}</caption>
        <tbody>{data.map((d) => <tr key={d.label}><th>{d.label}</th><td>{format(d.value)}</td></tr>)}</tbody>
      </table>
    </div>
  )
}

/** Horizontal ranked bars with the value printed beside each bar (text in ink colours, bar carries the hue). */
export function RankBars({ rows, format = (v) => v }) {
  const max = Math.max(1, ...rows.map((r) => r.value))
  return (
    <ul className="space-y-2.5">
      {rows.map((r) => (
        <li key={r.label} title={r.extra}>
          <div className="mb-1 flex justify-between gap-2 text-sm">
            <span className="truncate text-ink">{r.label}</span>
            <span className="shrink-0 font-semibold text-ink-2">{format(r.value)}</span>
          </div>
          <div className="h-2 rounded-full bg-card-2">
            <div className="h-2 rounded-full" style={{ width: `${(r.value / max) * 100}%`, background: 'var(--chart)' }} />
          </div>
        </li>
      ))}
    </ul>
  )
}

/**
 * Actual (bars, --chart) vs forecast (line with markers, --chart-2) on one axis, with a legend,
 * a per-day hover tooltip and a screen-reader table.
 */
export function ActualVsForecast({ data, height = 200, label }) {
  const [hover, setHover] = useState(null)
  const [box, W] = useWidth()
  const max = Math.max(1, ...data.flatMap((d) => [d.actual, d.predicted]))
  const pad = { t: 12, b: 26, l: 8, r: 8 }
  const innerH = height - pad.t - pad.b
  const slot = (W - pad.l - pad.r) / Math.max(1, data.length)
  const barW = Math.min(28, slot - 2 - slot * 0.4)
  const y = (v) => pad.t + innerH - (v / max) * innerH
  const cx = (i) => pad.l + slot * i + slot / 2
  const line = data.map((d, i) => `${i ? 'L' : 'M'}${cx(i)},${y(d.predicted)}`).join(' ')

  return (
    <div>
      <div className="mb-2 flex flex-wrap gap-4 text-xs text-ink-2" aria-hidden="true">
        <span className="flex items-center gap-1.5"><span className="h-3 w-3 rounded-sm" style={{ background: 'var(--chart)' }} /> Actual plates sold</span>
        <span className="flex items-center gap-1.5"><span className="h-0.5 w-4" style={{ background: 'var(--chart-2)' }} /><span className="-ml-2.5 size-2 rounded-full border-2" style={{ borderColor: 'var(--chart-2)', background: 'var(--card)' }} /> Forecast</span>
      </div>
      <div className="relative" ref={box}>
        <svg viewBox={`0 0 ${W} ${height}`} width={W} height={height} className="block max-w-full" role="img" aria-label={label}>
          {[0.25, 0.5, 0.75, 1].map((f) => (
            <line key={f} x1={pad.l} x2={W - pad.r} y1={pad.t + innerH * (1 - f)} y2={pad.t + innerH * (1 - f)} stroke="var(--chart-grid)" />
          ))}
          <line x1={pad.l} x2={W - pad.r} y1={pad.t + innerH} y2={pad.t + innerH} stroke="var(--line)" />
          {data.map((d, i) => {
            const h = (d.actual / max) * innerH
            const x = cx(i) - barW / 2
            const r = Math.min(4, barW / 2, h)
            const top = pad.t + innerH - h
            return (
              <g key={d.label} onMouseEnter={() => setHover(i)} onMouseLeave={() => setHover(null)}
                onFocus={() => setHover(i)} onBlur={() => setHover(null)} tabIndex={0} className="outline-none"
                aria-label={`${d.label}: actual ${d.actual}, forecast ${d.predicted}`}>
                <rect x={pad.l + slot * i} y={pad.t} width={slot} height={innerH} fill="transparent" />
                {h > 0 && (
                  <path d={`M${x},${top + h} V${top + r} Q${x},${top} ${x + r},${top} H${x + barW - r} Q${x + barW},${top} ${x + barW},${top + r} V${top + h} Z`}
                    fill="var(--chart)" opacity={hover === null || hover === i ? 1 : 0.45} />
                )}
                <text x={cx(i)} y={height - 8} textAnchor="middle" fontSize="12" fill="var(--muted)">{d.short}</text>
              </g>
            )
          })}
          <path d={line} fill="none" stroke="var(--chart-2)" strokeWidth="2" pointerEvents="none" />
          {data.map((d, i) => (
            <circle key={d.label} cx={cx(i)} cy={y(d.predicted)} r="4" fill="var(--card)" stroke="var(--chart-2)" strokeWidth="2" pointerEvents="none" />
          ))}
        </svg>
        {hover !== null && (
          <div className="pointer-events-none absolute -top-2 rounded-lg border border-line bg-card px-2.5 py-1.5 text-xs shadow-md"
            style={{ left: `${(cx(hover) / W) * 100}%`, transform: 'translate(-50%, -100%)' }}>
            <div className="text-muted">{data[hover].label}</div>
            <div className="text-ink">Actual <b>{data[hover].actual}</b></div>
            <div className="text-ink">Forecast <b>{data[hover].predicted}</b></div>
          </div>
        )}
        <table className="sr-only">
          <caption>{label}</caption>
          <thead><tr><th>Day</th><th>Actual</th><th>Forecast</th></tr></thead>
          <tbody>{data.map((d) => <tr key={d.label}><th>{d.label}</th><td>{d.actual}</td><td>{d.predicted}</td></tr>)}</tbody>
        </table>
      </div>
    </div>
  )
}

/** Weekday × hour heatmap, one hue light→dark (sequential). Values on hover; table for screen readers. */
export function Heatmap({ rows, cols, values, format = (v) => v, label }) {
  const max = Math.max(0.0001, ...values.flat())
  return (
    <div className="overflow-x-auto">
      <table className="w-full border-separate border-spacing-0.5 text-xs" aria-label={label}>
        <thead>
          <tr><th />{cols.map((c) => <th key={c} scope="col" className="px-0.5 pb-1 text-center font-normal text-muted">{c}</th>)}</tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={r}>
              <th scope="row" className="pr-2 text-left font-medium text-ink-2">{r}</th>
              {values[i].map((v, j) => (
                <td key={j} title={`${r} ${cols[j]}: ${format(v)}`} aria-label={`${r} ${cols[j]}: ${format(v)}`}
                  className="h-7 min-w-6 rounded-[4px]"
                  style={{ background: v > 0 ? `color-mix(in srgb, var(--chart) ${Math.round(15 + (v / max) * 85)}%, var(--card))` : 'var(--card-2)' }} />
              ))}
            </tr>
          ))}
        </tbody>
      </table>
      <div className="mt-2 flex items-center gap-2 text-xs text-muted" aria-hidden="true">
        Fewer
        <span className="h-2.5 w-24 rounded-full" style={{ background: 'linear-gradient(90deg, color-mix(in srgb, var(--chart) 15%, var(--card)), var(--chart))' }} />
        More orders
      </div>
    </div>
  )
}
