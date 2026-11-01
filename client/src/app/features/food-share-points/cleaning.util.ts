import { CleaningShift } from '../../api';
import { dayOf } from '../pickups/pickups.util';

/** Fairteiler have no time zone of their own yet; the platform runs in Switzerland (FS2). */
export const CLEANING_TIME_ZONE = 'Europe/Zurich';

export interface ShiftDay {
  /** `YYYY-MM-DD` in the time zone. */
  day: string;
  items: CleaningShift[];
}

/** Shifts grouped by their local day, in time order. */
export function shiftsByDay(shifts: readonly CleaningShift[], timeZone: string): ShiftDay[] {
  const days: ShiftDay[] = [];
  for (const shift of [...shifts].sort((a, b) => a.shiftAt.localeCompare(b.shiftAt))) {
    const day = dayOf(shift.shiftAt, timeZone);
    const last = days[days.length - 1];
    if (last?.day === day) last.items.push(shift);
    else days.push({ day, items: [shift] });
  }
  return days;
}

/**
 * A date (`TT.MM.JJJJ` or `YYYY-MM-DD`, the value of `fs-date-input`) as `YYYY-MM-DD`, or null when
 * it is no real calendar day.
 */
export function parseDay(text: string): string | null {
  const value = text.trim();
  let match = /^(\d{1,2})\.(\d{1,2})\.(\d{4})$/.exec(value);
  let year: number, month: number, day: number;
  if (match) {
    [day, month, year] = [Number(match[1]), Number(match[2]), Number(match[3])];
  } else {
    match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
    if (!match) return null;
    [year, month, day] = [Number(match[1]), Number(match[2]), Number(match[3])];
  }
  const date = new Date(Date.UTC(year, month - 1, day));
  if (
    date.getUTCFullYear() !== year ||
    date.getUTCMonth() !== month - 1 ||
    date.getUTCDate() !== day
  )
    return null;
  return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
}

/** `HH:MM`, 00:00–23:59. */
export function validTime(text: string): boolean {
  return /^([01]\d|2[0-3]):[0-5]\d$/.test(text.trim());
}

/** `YYYY-MM-DD` → `DD.MM.YYYY` (prefill of the date field). */
export function formatDay(day: string): string {
  const [year, month, date] = day.split('-');
  return `${date}.${month}.${year}`;
}
