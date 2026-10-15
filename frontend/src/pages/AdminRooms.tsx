import { useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { api, type Room, type RoomInput } from '../api';
import { Empty, ErrorBanner, SuccessBanner, errorMessage } from '../components/ui';

const EMPTY: RoomInput = { name: '', capacity: 4, floor: 1 };

export default function AdminRooms() {
  const [rooms, setRooms] = useState<Room[] | null>(null);
  const [form, setForm] = useState<RoomInput>(EMPTY);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = () => api.rooms().then(setRooms).catch((e) => setError(errorMessage(e)));
  useEffect(() => {
    load();
  }, []);

  function edit(room: Room) {
    setEditingId(room.id);
    setForm({ name: room.name, capacity: room.capacity, floor: room.floor });
    setError(null);
    setSuccess(null);
  }

  function reset() {
    setEditingId(null);
    setForm(EMPTY);
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setSuccess(null);
    try {
      const input = { ...form, name: form.name.trim() };
      if (editingId === null) {
        const room = await api.createRoom(input);
        setSuccess(`Room "${room.name}" created`);
      } else {
        const room = await api.updateRoom(editingId, input);
        setSuccess(`Room "${room.name}" updated`);
      }
      reset();
      await load();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  async function remove(room: Room) {
    if (!confirm(`Delete room "${room.name}"? This cannot be undone.`)) return;
    setError(null);
    setSuccess(null);
    try {
      await api.deleteRoom(room.id);
      if (editingId === room.id) reset();
      setSuccess(`Room "${room.name}" deleted`);
      await load();
    } catch (err) {
      setError(errorMessage(err));
    }
  }

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Manage rooms</h1>
          <p className="muted">Rooms that have bookings cannot be deleted.</p>
        </div>
      </div>

      <ErrorBanner error={error} />
      <SuccessBanner message={success} />

      <div className="split admin">
        <form className="card" onSubmit={submit}>
          <h3>{editingId === null ? 'Add a room' : 'Edit room'}</h3>
          <label>
            Name
            <input value={form.name} maxLength={100} required onChange={(e) => setForm({ ...form, name: e.target.value })} />
          </label>
          <div className="row">
            <label>
              Capacity
              <input type="number" min={1} max={10000} required value={form.capacity}
                onChange={(e) => setForm({ ...form, capacity: e.target.valueAsNumber })} />
            </label>
            <label>
              Floor
              <input type="number" min={-10} max={300} required value={form.floor}
                onChange={(e) => setForm({ ...form, floor: e.target.valueAsNumber })} />
            </label>
          </div>
          <div className="row">
            <button className="btn btn-primary" disabled={busy}>{editingId === null ? 'Add room' : 'Save changes'}</button>
            {editingId !== null && <button type="button" className="btn btn-ghost" onClick={reset}>Cancel</button>}
          </div>
        </form>

        <div>
          {rooms === null && <p className="muted">Loading rooms…</p>}
          {rooms?.length === 0 && <Empty>No rooms yet. Add one with the form.</Empty>}
          {rooms && rooms.length > 0 && (
            <div className="card table-card">
              <table>
                <thead>
                  <tr><th>Name</th><th>Seats</th><th>Floor</th><th /></tr>
                </thead>
                <tbody>
                  {rooms.map((r) => (
                    <tr key={r.id} className={editingId === r.id ? 'selected' : undefined}>
                      <td><Link to={`/rooms/${r.id}`}>{r.name}</Link></td>
                      <td>{r.capacity}</td>
                      <td>{r.floor}</td>
                      <td className="actions">
                        <button className="btn btn-ghost" onClick={() => edit(r)}>Edit</button>
                        <button className="btn btn-danger-ghost" onClick={() => remove(r)}>Delete</button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </>
  );
}
