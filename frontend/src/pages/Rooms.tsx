import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router';
import { api, type Room } from '../api';
import { useAuth } from '../auth';
import { Empty, ErrorBanner, errorMessage } from '../components/ui';

export default function Rooms() {
  const { user } = useAuth();
  const [rooms, setRooms] = useState<Room[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [minCapacity, setMinCapacity] = useState('');

  useEffect(() => {
    api.rooms().then(setRooms).catch((e) => setError(errorMessage(e)));
  }, []);

  const visible = useMemo(() => {
    const q = query.trim().toLowerCase();
    const min = Number(minCapacity) || 0;
    return (rooms ?? []).filter((r) => r.name.toLowerCase().includes(q) && r.capacity >= min);
  }, [rooms, query, minCapacity]);

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Rooms</h1>
          <p className="muted">Pick a room to see its schedule and book a slot.</p>
        </div>
        {user?.role === 'ADMIN' && <Link className="btn" to="/admin/rooms">Manage rooms</Link>}
      </div>

      <div className="filters">
        <input type="search" placeholder="Search by name" value={query} onChange={(e) => setQuery(e.target.value)} aria-label="Search rooms" />
        <input type="number" min={1} placeholder="Min. seats" value={minCapacity} onChange={(e) => setMinCapacity(e.target.value)} aria-label="Minimum capacity" />
      </div>

      <ErrorBanner error={error} />
      {rooms === null && !error && <p className="muted">Loading rooms…</p>}
      {rooms?.length === 0 && (
        <Empty>
          No rooms yet.
          {user?.role === 'ADMIN' && <> <Link to="/admin/rooms">Add the first one</Link>.</>}
        </Empty>
      )}
      {rooms !== null && rooms.length > 0 && visible.length === 0 && <Empty>No rooms match your filters.</Empty>}

      <div className="grid">
        {visible.map((room) => (
          <Link key={room.id} to={`/rooms/${room.id}`} className="card room-card">
            <h3>{room.name}</h3>
            <div className="room-meta">
              <span>👥 {room.capacity} {room.capacity === 1 ? 'seat' : 'seats'}</span>
              <span>🏢 Floor {room.floor}</span>
            </div>
            <span className="room-cta">View schedule →</span>
          </Link>
        ))}
      </div>
    </>
  );
}
