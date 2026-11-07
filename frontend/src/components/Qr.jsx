import { useEffect, useRef, useState } from 'react'
import QRCode from 'qrcode'
import { Camera, X } from 'lucide-react'

/** The pickup QR holds "TULAS|<code>|<otp>", which the counter scanner splits back apart. */
export const qrPayload = (code, otp) => `TULAS|${code}|${otp}`

export function parseQr(text) {
  const m = /^TULAS\|([A-Za-z]-?\d+)\|(\d{4})$/.exec((text || '').trim())
  return m ? { code: m[1].toUpperCase(), otp: m[2] } : null
}

export function QrImage({ value, size = 180 }) {
  const [src, setSrc] = useState(null)
  useEffect(() => {
    QRCode.toDataURL(value, { width: size * 2, margin: 1, color: { dark: '#2b1a10', light: '#ffffff' } })
      .then(setSrc)
      .catch(() => setSrc(null))
  }, [value, size])
  return src
    ? <img src={src} width={size} height={size} alt="Pickup QR code" className="rounded-xl bg-white p-2" />
    : <div style={{ width: size, height: size }} className="rounded-xl bg-card-2" />
}

/**
 * Camera QR scanner using the browser's built-in BarcodeDetector (Chrome / Android, Edge).
 * Where it isn't supported, the counter types the code and OTP instead.
 */
export function QrScanner({ onResult, onClose }) {
  const video = useRef(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    let stream
    let raf
    let stopped = false
    async function start() {
      if (!('BarcodeDetector' in window)) {
        setError("This browser can't scan QR codes. Type the order number and code instead.")
        return
      }
      try {
        stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } })
        video.current.srcObject = stream
        await video.current.play()
        const detector = new window.BarcodeDetector({ formats: ['qr_code'] })
        const tick = async () => {
          if (stopped) return
          try {
            const codes = await detector.detect(video.current)
            const hit = codes.map((c) => parseQr(c.rawValue)).find(Boolean)
            if (hit) { onResult(hit); return }
          } catch { /* frame not ready */ }
          raf = requestAnimationFrame(tick)
        }
        tick()
      } catch {
        setError('Camera permission was denied. Type the order number and code instead.')
      }
    }
    start()
    return () => {
      stopped = true
      cancelAnimationFrame(raf)
      stream?.getTracks().forEach((t) => t.stop())
    }
  }, [onResult])

  return (
    <div className="relative overflow-hidden rounded-2xl bg-black">
      {error
        ? <p className="p-6 text-center text-sm text-white">{error}</p>
        : <video ref={video} playsInline muted className="aspect-square w-full object-cover" />}
      {!error && <div className="pointer-events-none absolute inset-10 rounded-2xl border-2 border-white/80" />}
      <button onClick={onClose} className="absolute right-2 top-2 rounded-full bg-black/60 p-2 text-white" aria-label="Close scanner">
        <X className="size-5" />
      </button>
      {!error && <p className="absolute inset-x-0 bottom-2 flex items-center justify-center gap-1.5 text-xs text-white/90"><Camera className="size-3.5" /> Point at the student's pickup QR</p>}
    </div>
  )
}
