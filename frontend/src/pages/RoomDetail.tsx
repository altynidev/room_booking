import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router';
import { api, type Availability, type Room, type TimeSlot } from '../api';
import DayTimeline from '../components/DayTimeline';
import { Empty, ErrorBanner, SuccessBanner, errorMessage } from '../components/ui';
import {
  addDays, browserZone, durationLabel, formatDate, formatRange, fromInputs, startOfDay, toDateInput, toTimeInput,
} from '../time';

const STEP_MS = 15 * 60 * 1000;

/** Next quarter hour from now. */
function nextQuarter(): Date {
  return new Date(Math.ceil(Date.now() / STEP_MS) * STEP_MS);
}

export default function RoomDetail() {
  const roomId = Number(useParams().id);
  const today = toDateInput(new Date());

  const [room, setRoom] = useState<Room | null>(null);
  const [roomError, setRoomError] = useState<string | null>(null);
  const [date, setDate] = useState(today);
  const [availability, setAvailability] = useState<Availability | null>(null);
  const [start, setStart] = useState('09:00');
  const [end, setEnd] = useState('10:00');
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api.room(roomId).then(setRoom).catch((e) => setRoomError(errorMessage(e)));
  }, [roomId]);

  const loadAvailability = useCallback(() => {
    api.availability(roomId, date, browserZone)
      .then(setAvailability)
      .catch((e) => setError(errorMessage(e)));
  }, [roomId, date]);

  useEffect(() => {
    setAvailability(null);
    loadAvailability();
  }, [loadAvailability]);

  // "00:00" as an end time means midnight at the end of the selected day.
  const selection = useMemo(() => {
    const s = fromInputs(date, start);
    let e = fromInputs(date, end);
    if (end === '00:00') e = startOfDay(addDays(date, 1));
    return Number.isNaN(s.getTime()) || Number.isNaN(e.getTime()) ? null : { start: s, end: e };
  }, [date, start, end]);

  /** Free slots that are still in the future, trimmed to start no earlier than now. */
  const freeSlots = useMemo<TimeSlot[]>(() => {
    if (!availability) return [];
    const earliest = nextQuarter().getTime();
    return availability.freeSlots
      .map((s) => ({ start: new Date(Math.max(new Date(s.start).getTime(), earliest)).toISOString(), end: s.end }))
      .filter((s) => new Date(s.end).getTime() - new Date(s.start).getTime() >= STEP_MS);
  }, [availability]);

  function pickSlot(slot: TimeSlot) {
    const s = new Date(slot.start);
    const slotEnd = new Date(slot.end).getTime();
    const e = new Date(Math.min(s.getTime() + 60 * 60 * 1000, slotEnd));
    setStart(toTimeInput(s));
    setEnd(toTimeInput(e));
    setSuccess(null);
    setError(null);
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSuccess(null);
    if (!selection || selection.end <= selection.start) {
      setError('End time must be after start time');
      return;
    }
    if (selection.start.getTime() <= Date.now()) {
      setError('Start time must be in the future');
      return;
    }
    setBusy(true);
    try {
      const booking = await api.book(roomId, selection.start.toISOString(), selection.end.toISOString());
      setSuccess(`Booked ${booking.roomName}, ${formatDate(booking.startTime)} ${formatRange(booking.startTime, booking.endTime)}`);
      loadAvailability();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  if (roomError) {
    return (
      <Empty>
        <h2>{roomError}</h2>
        <Link to="/">Back to rooms</Link>
      </Empty>
    );
  }

  return (
    <>
      <Link to="/" className="back">← All rooms</Link>
      <div className="page-head">
        <div>
          <h1>{room?.name ?? 'Loading…'}</h1>
          {room && (
            <p className="muted">
              {room.capacity} {room.capacity === 1 ? 'seat' : 'seats'} · Floor {room.floor}
            </p>
          )}
        </div>
      </div>

      <section className="card">
        <div className="day-nav">
          <button className="btn" onClick={() => setDate(addDays(date, -1))} disabled={date <= today} aria-label="Previous day">‹</button>
          <input type="date" value={date} min={today} onChange={(e) => e.target.value && setDate(e.target.value)} />
          <button className="btn" onClick={() => setDate(addDays(date, 1))} aria-label="Next day">›</button>
          {date !== today && <button className="btn btn-ghost" onClick={() => setDate(today)}>Today</button>}
          <span className="muted small day-label">{formatDate(startOfDay(date))} · times in {browserZone}</span>
        </div>

        {availability ? (
          <DayTimeline date={date} booked={availability.bookedSlots} selection={selection} />
        ) : (
          <p className="muted">Loading schedule…</p>
        )}

        <div className="split">
          <div>
            <h3>Free slots</h3>
            {availability && freeSlots.length === 0 && <p className="muted">No free time left on this day.</p>}
            <div className="chips">
              {freeSlots.map((s) => (
                <button key={s.start} type="button" className="chip" onClick={() => pickSlot(s)} title="Use this slot">
                  {formatRange(s.start, s.end)} <span className="muted">({durationLabel(s.start, s.end)})</span>
                </button>
              ))}
            </div>
            {availability && availability.bookedSlots.length > 0 && (
              <>
                <h3>Booked</h3>
                <ul className="plain">
                  {availability.bookedSlots.map((s) => (
                    <li key={s.start}><span className="dot dot-booked" /> {formatRange(s.start, s.end)}</li>
                  ))}
                </ul>
              </>
            )}
          </div>

          <form className="book-form" onSubmit={submit}>
            <h3>Book this room</h3>
            <div className="row">
              <label>
                From
                <input type="time" step={900} value={start} onChange={(e) => setStart(e.target.value)} required />
              </label>
              <label>
                To
                <input type="time" step={900} value={end} onChange={(e) => setEnd(e.target.value)} required />
              </label>
            </div>
            {selection && selection.end > selection.start && (
              <p className="muted small">Duration: {durationLabel(selection.start.toISOString(), selection.end.toISOString())}</p>
            )}
            <ErrorBanner error={error} />
            <SuccessBanner message={success} />
            <button className="btn btn-primary" disabled={busy}>{busy ? 'Booking…' : 'Book'}</button>
            {success && <Link to="/bookings" className="small">View my bookings →</Link>}
          </form>
        </div>
      </section>
    </>
  );
}
