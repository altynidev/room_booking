import { NavLink, Outlet, useNavigate } from 'react-router';
import { useAuth } from '../auth';

export default function Layout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  return (
    <div className="app">
      <header className="topbar">
        <div className="topbar-inner">
          <NavLink to="/" className="brand">
            <img src="/favicon.svg" alt="" width={24} height={24} />
            Room Booking
          </NavLink>
          <nav className="nav">
            <NavLink to="/" end>Rooms</NavLink>
            <NavLink to="/bookings">My bookings</NavLink>
            {user?.role === 'ADMIN' && <NavLink to="/admin/rooms">Manage rooms</NavLink>}
          </nav>
          <div className="user">
            <span className="muted">
              {user?.username}
              {user?.role === 'ADMIN' && <span className="badge">admin</span>}
            </span>
            <button
              className="btn btn-ghost"
              onClick={() => {
                logout();
                navigate('/login');
              }}
            >
              Log out
            </button>
          </div>
        </div>
      </header>
      <main className="container">
        <Outlet />
      </main>
    </div>
  );
}
