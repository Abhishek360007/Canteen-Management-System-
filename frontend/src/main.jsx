import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App'
import { AuthProvider } from './context/AuthContext'
import { CartProvider } from './context/CartContext'
import { CanteenProvider } from './context/CanteenContext'
import { LiveProvider } from './context/LiveContext'
import { ThemeProvider } from './context/ThemeContext'
import { ToastProvider } from './components/ui'
import './index.css'
import { registerWorker } from './push'

// The service worker shows push notifications (and lets the app be installed).
registerWorker()

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <BrowserRouter>
      <ThemeProvider>
        <ToastProvider>
          <AuthProvider>
            <LiveProvider>
              <CanteenProvider>
                <CartProvider>
                  <App />
                </CartProvider>
              </CanteenProvider>
            </LiveProvider>
          </AuthProvider>
        </ToastProvider>
      </ThemeProvider>
    </BrowserRouter>
  </StrictMode>,
)
