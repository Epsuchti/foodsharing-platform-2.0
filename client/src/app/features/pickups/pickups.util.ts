/** Date helpers of the pickup pages: pickups are UTC instants shown in the store region's time zone (A20). */

/** Offset of `timeZone` from UTC at `instant`, in milliseconds. */
function offsetMs(instant: number, timeZone: string): number {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone,
    hourCycle: 'h23',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).formatToParts(new Date(instant));
  const get = (type: string) => Number(parts.find((p) => p.type === type)?.value);
  const asUtc = Date.UTC(get('year'), get('month') - 1, get('day'), get('hour'), get('minute'), get('second'));
  return asUtc - Math.floor(instant / 1000) * 1000;
}

/** `2026-11-05` + `19:00` in `Europe/Zurich` → `2026-11-05T18:00:00Z` (the API's pickupAt). */
export function zonedToUtc(date: string, time: string, timeZone: string): string {
  const [y, m, d] = date.split('-').map(Number);
  const [hh, mm] = time.split(':').map(Number);
  const guess = Date.UTC(y, m - 1, d, hh, mm);
  let instant = guess - offsetMs(guess, timeZone);
  instant = guess - offsetMs(instant, timeZone);
  return new Date(instant).toISOString().replace('.000Z', 'Z');
}

/** Calendar day (`YYYY-MM-DD`) of an instant in `timeZone` (day grouping, agenda). */
export function dayOf(instant: string | number | Date, timeZone: string): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone, year: 'numeric', month: '2-digit', day: '2-digit' }).format(
    new Date(instant),
  );
}

/** `YYYY-MM-DD` plus `days`. */
export function addDays(date: string, days: number): string {
  const [y, m, d] = date.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d + days)).toISOString().slice(0, 10);
}

export interface DayGroup<T> {
  day: string;
  items: T[];
}

/** Groups items (sorted by `pickupAt`) by their local calendar day. */
export function groupByDay<T extends { pickupAt: string }>(items: readonly T[], timeZone: string): DayGroup<T>[] {
  const groups: DayGroup<T>[] = [];
  for (const item of [...items].sort((a, b) => a.pickupAt.localeCompare(b.pickupAt))) {
    const day = dayOf(item.pickupAt, timeZone);
    const last = groups.at(-1);
    if (last?.day === day) last.items.push(item);
    else groups.push({ day, items: [item] });
  }
  return groups;
}
