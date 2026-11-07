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
