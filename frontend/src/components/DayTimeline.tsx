import type { TimeSlot } from '../api';
import { addDays, formatRange, startOfDay } from '../time';

interface Props {
  date: string;
  booked: TimeSlot[];
  /** Range the user is about to book, drawn as a preview. */
  selection?: { start: Date; end: Date } | null;
}

const TICKS = [0, 3, 6, 9, 12, 15, 18, 21, 24];

/** A 24-hour bar for one local day: booked slots, the elapsed part of today and the pending selection. */
export default function DayTimeline({ date, booked, selection }: Props) {
  const dayStart = startOfDay(date).getTime();
  const dayEnd = startOfDay(addDays(date, 1)).getTime();
  const span = dayEnd - dayStart;
  const pct = (t: number) => `${(Math.min(Math.max(t - dayStart, 0), span) / span) * 100}%`;
  const width = (a: number, b: number) =>
    `${((Math.min(b, dayEnd) - Math.max(a, dayStart)) / span) * 100}%`;

  const now = Date.now();
  const selStart = selection?.start.getTime();
  const selEnd = selection?.end.getTime();
  const showSelection = selStart !== undefined && selEnd !== undefined && selEnd > selStart
    && selEnd > dayStart && selStart < dayEnd;

  return (
    <div className="timeline" aria-label="Room schedule for the day">
      <div className="timeline-bar">
        {now > dayStart && (
          <div className="tl-past" style={{ left: 0, width: width(dayStart, now) }} title="Already passed" />
        )}
        {booked.map((s) => {
          const a = new Date(s.start).getTime();
          const b = new Date(s.end).getTime();
          return (
            <div key={s.start} className="tl-booked" style={{ left: pct(a), width: width(a, b) }}
              title={`Booked ${formatRange(s.start, s.end)}`} />
          );
        })}
        {showSelection && (
          <div className="tl-selection" style={{ left: pct(selStart), width: width(selStart, selEnd) }} />
        )}
        {now > dayStart && now < dayEnd && <div className="tl-now" style={{ left: pct(now) }} title="Now" />}
      </div>
      <div className="timeline-ticks">
        {TICKS.map((h) => (
          <span key={h} style={{ left: `${(h / 24) * 100}%` }}>{String(h).padStart(2, '0')}</span>
        ))}
      </div>
      <div className="legend">
        <span><i className="sw sw-free" /> Free</span>
        <span><i className="sw sw-booked" /> Booked</span>
        <span><i className="sw sw-selection" /> Your selection</span>
      </div>
    </div>
  );
}
