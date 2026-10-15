import type { ReactNode } from 'react';
import { Navigate, Route, Routes, useLocation } from 'react-router';
import { useAuth } from './auth';
import Layout from './components/Layout';
import AdminRooms from './pages/AdminRooms';
import Login from './pages/Login';
import MyBookings from './pages/MyBookings';
import NotFound from './pages/NotFound';
import Register from './pages/Register';
import RoomDetail from './pages/RoomDetail';
import Rooms from './pages/Rooms';

function RequireAuth({ admin = false, children }: { admin?: boolean; children: ReactNode }) {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) return <p className="muted center">Loading…</p>;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  if (admin && user.role !== 'ADMIN') return <Navigate to="/" replace />;
  return children;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route element={<RequireAuth><Layout /></RequireAuth>}>
        <Route index element={<Rooms />} />
        <Route path="rooms/:id" element={<RoomDetail />} />
        <Route path="bookings" element={<MyBookings />} />
        <Route path="admin/rooms" element={<RequireAuth admin><AdminRooms /></RequireAuth>} />
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  );
}
