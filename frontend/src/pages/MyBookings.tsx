import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router';
import { api, type Booking } from '../api';
import { Empty, ErrorBanner, errorMessage } from '../components/ui';
import { durationLabel, formatDate, formatRange } from '../time';

type Tab = 'upcoming' | 'past' | 'cancelled';

export default function MyBookings() {
  const [bookings, setBookings] = useState<Booking[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [tab, setTab] = useState<Tab>('upcoming');
  const [cancelling, setCancelling] = useState<number | null>(null);

  useEffect(() => {
    api.myBookings().then(setBookings).catch((e) => setError(errorMessage(e)));
  }, []);

  const groups = useMemo(() => {
    const now = Date.now();
    const all = bookings ?? [];
    const byStart = (a: Booking, b: Booking) => a.startTime.localeCompare(b.startTime);
    return {
      upcoming: all.filter((b) => b.status === 'ACTIVE' && new Date(b.endTime).getTime() > now).sort(byStart),
      past: all.filter((b) => b.status === 'ACTIVE' && new Date(b.endTime).getTime() <= now).sort(byStart).reverse(),
      cancelled: all.filter((b) => b.status === 'CANCELLED').sort(byStart).reverse(),
    };
  }, [bookings]);

  async function cancel(b: Booking) {
    if (!confirm(`Cancel ${b.roomName} on ${formatDate(b.startTime)}, ${formatRange(b.startTime, b.endTime)}?`)) return;
    setCancelling(b.id);
    setError(null);
    try {
      const updated = await api.cancelBooking(b.id);
      setBookings((list) => list?.map((x) => (x.id === updated.id ? updated : x)) ?? null);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setCancelling(null);
    }
  }

  const list = groups[tab];

  return (
    <>
      <div className="page-head">
        <div>
          <h1>My bookings</h1>
          <p className="muted">Your reservations, in your local time.</p>
        </div>
        <Link className="btn btn-primary" to="/">New booking</Link>
      </div>

      <div className="tabs" role="tablist">
        {(['upcoming', 'past', 'cancelled'] as const).map((t) => (
          <button key={t} role="tab" aria-selected={tab === t} className={tab === t ? 'tab active' : 'tab'} onClick={() => setTab(t)}>
            {t[0].toUpperCase() + t.slice(1)} <span className="count">{groups[t].length}</span>
          </button>
        ))}
      </div>

      <ErrorBanner error={error} />
      {bookings === null && !error && <p className="muted">Loading bookings…</p>}
      {bookings !== null && list.length === 0 && (
        <Empty>{tab === 'upcoming' ? <>Nothing booked yet. <Link to="/">Find a room</Link>.</> : `No ${tab} bookings.`}</Empty>
      )}

      {list.length > 0 && (
        <div className="card table-card">
          <table>
            <thead>
              <tr><th>Room</th><th>Date</th><th>Time</th><th>Duration</th><th /></tr>
            </thead>
            <tbody>
              {list.map((b) => (
                <tr key={b.id}>
                  <td><Link to={`/rooms/${b.roomId}`}>{b.roomName}</Link></td>
                  <td>{formatDate(b.startTime)}</td>
                  <td>{formatRange(b.startTime, b.endTime)}</td>
                  <td className="muted">{durationLabel(b.startTime, b.endTime)}</td>
                  <td className="actions">
                    {tab === 'upcoming' && (
                      <button className="btn btn-danger-ghost" disabled={cancelling === b.id} onClick={() => cancel(b)}>
                        {cancelling === b.id ? 'Cancelling…' : 'Cancel'}
                      </button>
                    )}
                    {tab === 'cancelled' && <span className="badge badge-muted">cancelled</span>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
