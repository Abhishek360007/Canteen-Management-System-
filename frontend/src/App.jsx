import { Navigate, Route, Routes } from 'react-router-dom'
import { homeFor, useAuth } from './context/AuthContext'
import Layout from './components/Layout'
import { Spinner } from './components/ui'
import Login from './pages/Login'
import Register from './pages/Register'
import Account from './pages/Account'
import Menu from './pages/student/Menu'
import Cart from './pages/student/Cart'
import Orders from './pages/student/Orders'
import OrderPass from './pages/student/OrderPass'
import WalletPage from './pages/student/Wallet'
import Rewards from './pages/student/Rewards'
import Board from './pages/kitchen/Board'
import Verify from './pages/kitchen/Verify'
import Stock from './pages/kitchen/Stock'
import Summary from './pages/kitchen/Summary'
import Dashboard from './pages/admin/Dashboard'
import AdminOrders from './pages/admin/AdminOrders'
import AdminMenu from './pages/admin/AdminMenu'
import UsersPage from './pages/admin/Users'
import More from './pages/admin/More'
import Announcements from './pages/admin/Announcements'
import Settings from './pages/admin/Settings'
import Topups from './pages/admin/Topups'
import Activity from './pages/admin/Activity'
import Insights from './pages/admin/Insights'

function Guard({ roles, children }) {
  const { user } = useAuth()
  if (!user) return <Navigate to="/login" replace />
  if (roles && !roles.includes(user.role)) return <Navigate to={homeFor(user.role)} replace />
  return children
}

export default function App() {
  const { user, loading } = useAuth()
  if (loading) return <Spinner label="Opening Tula's Canteen…" />

  const S = ['STUDENT']
  const K = ['STAFF', 'ADMIN']
  const A = ['ADMIN']

  return (
    <Routes>
      <Route path="/login" element={user ? <Navigate to={homeFor(user.role)} replace /> : <Login />} />
      <Route path="/register" element={user ? <Navigate to={homeFor(user.role)} replace /> : <Register />} />

      <Route element={<Guard><Layout /></Guard>}>
        <Route path="/menu" element={<Guard roles={S}><Menu /></Guard>} />
        <Route path="/cart" element={<Guard roles={S}><Cart /></Guard>} />
        <Route path="/orders" element={<Guard roles={S}><Orders /></Guard>} />
        <Route path="/orders/:id" element={<Guard roles={S}><OrderPass /></Guard>} />
        <Route path="/wallet" element={<Guard roles={S}><WalletPage /></Guard>} />
        <Route path="/rewards" element={<Guard roles={S}><Rewards /></Guard>} />

        <Route path="/kitchen" element={<Guard roles={K}><Board /></Guard>} />
        <Route path="/kitchen/verify" element={<Guard roles={K}><Verify /></Guard>} />
        <Route path="/kitchen/stock" element={<Guard roles={K}><Stock /></Guard>} />
        <Route path="/kitchen/summary" element={<Guard roles={K}><Summary /></Guard>} />

        <Route path="/admin" element={<Guard roles={A}><Dashboard /></Guard>} />
        <Route path="/admin/orders" element={<Guard roles={A}><AdminOrders /></Guard>} />
        <Route path="/admin/menu" element={<Guard roles={A}><AdminMenu /></Guard>} />
        <Route path="/admin/users" element={<Guard roles={A}><UsersPage /></Guard>} />
        <Route path="/admin/more" element={<Guard roles={A}><More /></Guard>} />
        <Route path="/admin/announcements" element={<Guard roles={A}><Announcements /></Guard>} />
        <Route path="/admin/settings" element={<Guard roles={A}><Settings /></Guard>} />
        <Route path="/admin/topups" element={<Guard roles={A}><Topups /></Guard>} />
        <Route path="/admin/activity" element={<Guard roles={A}><Activity /></Guard>} />
        <Route path="/admin/insights" element={<Guard roles={A}><Insights /></Guard>} />

        <Route path="/account" element={<Account />} />
      </Route>

      <Route path="*" element={<Navigate to={user ? homeFor(user.role) : '/login'} replace />} />
    </Routes>
  )
}
