import { api } from './api'

let loading = null

/** Loads Razorpay's checkout script once (only when someone actually pays). */
function loadCheckout() {
  if (window.Razorpay) return Promise.resolve()
  if (!loading) {
    loading = new Promise((resolve, reject) => {
      const s = document.createElement('script')
      s.src = 'https://checkout.razorpay.com/v1/checkout.js'
      s.onload = resolve
      s.onerror = () => { loading = null; reject(new Error("Couldn't load the payment page. Check your internet and try again.")) }
      document.body.appendChild(s)
    })
  }
  return loading
}

/**
 * Pay online into the wallet. The server fixes the amount and creates the Razorpay order; after the student pays,
 * the server checks Razorpay's signature and credits the wallet.
 * Resolves with { credited, amount, balance }; resolves null if the student closes the checkout; rejects on errors.
 */
export async function payOnline({ amount, purpose = 'wallet', themeColor = '#d9480f' }) {
  await loadCheckout()
  const order = await api('/api/payments/razorpay/order', { method: 'POST', body: { amount, purpose } })
  return new Promise((resolve, reject) => {
    const rzp = new window.Razorpay({
      key: order.keyId,
      order_id: order.orderId,
      amount: order.amount,
      currency: order.currency,
      name: order.name,
      description: order.description,
      prefill: order.prefill,
      theme: { color: themeColor },
      handler: async (res) => {
        try {
          resolve(await api('/api/payments/razorpay/verify', {
            method: 'POST',
            body: { orderId: res.razorpay_order_id, paymentId: res.razorpay_payment_id, signature: res.razorpay_signature },
          }))
        } catch (e) {
          reject(e)
        }
      },
      modal: {
        ondismiss: () => {
          api('/api/payments/razorpay/cancel', { method: 'POST', body: { orderId: order.orderId } }).catch(() => {})
          resolve(null)
        },
      },
    })
    rzp.on('payment.failed', (r) => {
      // Razorpay keeps its window open so the student can retry; we only explain what happened.
      console.warn('Payment failed:', r?.error?.description)
    })
    rzp.open()
  })
}
