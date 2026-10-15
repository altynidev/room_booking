// The API speaks UTC ISO-8601; the UI shows and accepts the browser's local time.

export const browserZone = Intl.DateTimeFormat().resolvedOptions().timeZone;

const pad = (n: number) => String(n).padStart(2, '0');

/** Local calendar date as YYYY-MM-DD. */
export function toDateInput(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** Local wall-clock time as HH:MM. */
export function toTimeInput(d: Date): string {
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** Local date (YYYY-MM-DD) + time (HH:MM) -> Date. */
export function fromInputs(date: string, time: string): Date {
  return new Date(`${date}T${time}`);
}

/** Local midnight of a YYYY-MM-DD date. */
export function startOfDay(date: string): Date {
  return new Date(`${date}T00:00`);
}

export function addDays(date: string, days: number): string {
  const d = startOfDay(date);
  d.setDate(d.getDate() + days);
  return toDateInput(d);
}

const timeFmt = new Intl.DateTimeFormat(undefined, { hour: '2-digit', minute: '2-digit' });
const dateFmt = new Intl.DateTimeFormat(undefined, { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' });

export const formatTime = (iso: string | Date) => timeFmt.format(new Date(iso));
export const formatDate = (iso: string | Date) => dateFmt.format(new Date(iso));

/** "09:00 – 10:30", with the end shown as 24:00 when a slot runs to the next midnight. */
export function formatRange(start: string, end: string): string {
  const e = new Date(end);
  const endLabel = e.getHours() === 0 && e.getMinutes() === 0 && new Date(start).getDate() !== e.getDate()
    ? '24:00'
    : formatTime(e);
  return `${formatTime(start)} – ${endLabel}`;
}

export function durationLabel(start: string, end: string): string {
  const minutes = Math.round((new Date(end).getTime() - new Date(start).getTime()) / 60000);
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return [h ? `${h} h` : '', m ? `${m} min` : ''].filter(Boolean).join(' ') || '0 min';
}
