/**
 * Pure helpers of the shared date/datetime/time inputs (decision 150). Form values are the strings native inputs
 * gave: date `yyyy-MM-dd`, datetime `yyyy-MM-ddTHH:mm` (wall time), time `HH:mm`, empty `''`. The display is the
 * Swiss format `dd.mm.yyyy` / 24h `HH:mm` in every UI language.
 *
 * Only local `Date` components are read and written (`getFullYear`, `getHours` …): `toISOString()` would shift the
 * value by the UTC offset.
 */

const pad = (value: number) => String(value).padStart(2, '0');

/** `true` for a 24h `HH:mm` time. */
export function validTime(time: string): boolean {
  return /^([01]\d|2[0-3]):[0-5]\d$/.test(time);
}

function isoDate(year: number, month: number, day: number): string | null {
  if (year < 1000 || year > 9999 || month < 1 || month > 12 || day < 1) {
    return null;
  }
  // day 0 of the next month is the last day of this one
  const days = new Date(year, month, 0).getDate();
  return day > days ? null : `${year}-${pad(month)}-${pad(day)}`;
}

/** Two-digit years: 00–69 → 2000–2069, 70–99 → 1970–1999 (POSIX `%y`, no clock needed). */
function fullYear(year: string): number {
  const value = Number(year);
  if (year.length !== 2) {
    return value;
  }
  return value < 70 ? 2000 + value : 1900 + value;
}

/**
 * Typed date → `yyyy-MM-dd`. Accepts `dd.mm.yyyy`, `d.m.yyyy`, `d.m.yy` and ISO `yyyy-mm-dd`; `''` for blank text,
 * `null` for text that is not a real date (31.02., 29.02. of a common year, month 13, garbage).
 */
export function parseDisplayDate(text: string): string | null {
  const value = text.trim();
  if (!value) {
    return '';
  }
  const swiss = /^(\d{1,2})\.(\d{1,2})\.(\d{4}|\d{2})$/.exec(value);
  if (swiss) {
    return isoDate(fullYear(swiss[3]), Number(swiss[2]), Number(swiss[1]));
  }
  const iso = /^(\d{4})-(\d{1,2})-(\d{1,2})$/.exec(value);
  if (iso) {
    return isoDate(Number(iso[1]), Number(iso[2]), Number(iso[3]));
  }
  return null;
}

/** Typed time → `HH:mm`. Accepts `H:mm`, `HH:mm` and `H.mm`; `''` for blank text, `null` otherwise. */
export function parseDisplayTime(text: string): string | null {
  const value = text.trim();
  if (!value) {
    return '';
  }
  const match = /^(\d{1,2})[:.](\d{2})$/.exec(value);
  if (!match) {
    return null;
  }
  const time = `${pad(Number(match[1]))}:${match[2]}`;
  return validTime(time) ? time : null;
}

/**
 * Typed date and time → `yyyy-MM-ddTHH:mm`. Accepts a date of {@link parseDisplayDate} and a time of
 * {@link parseDisplayTime}, separated by blanks (or `T`, as in ISO); `''` for blank text, `null` otherwise.
 */
export function parseDisplayDateTime(text: string): string | null {
  const value = text.trim();
  if (!value) {
    return '';
  }
  const match = /^(\S+?)(?:\s+|T)(\S+)$/.exec(value);
  if (!match) {
    return null;
  }
  const date = parseDisplayDate(match[1]);
  const time = parseDisplayTime(match[2]);
  return date && time ? `${date}T${time}` : null;
}

/** `yyyy-MM-dd` → `dd.mm.yyyy` (`''` for an empty or malformed value). */
export function formatDisplayDate(iso: string | null | undefined): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso ?? '');
  return match ? `${match[3]}.${match[2]}.${match[1]}` : '';
}

/** `yyyy-MM-ddTHH:mm` → `dd.mm.yyyy HH:mm` (`''` for an empty or malformed value). */
export function formatDisplayDateTime(iso: string | null | undefined): string {
  const match = /^(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2})/.exec(iso ?? '');
  return match ? `${formatDisplayDate(match[1])} ${match[2]}` : '';
}

/** `yyyy-MM-dd` → local midnight of that day, `null` for an empty or invalid value. */
export function isoDateToDate(iso: string | null | undefined): Date | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso ?? '');
  if (!match || !isoDate(Number(match[1]), Number(match[2]), Number(match[3]))) {
    return null;
  }
  return new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]));
}

/** Local day of a `Date` → `yyyy-MM-dd`. */
export function dateToIsoDate(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/** `yyyy-MM-ddTHH:mm` (seconds ignored) → that local wall time, `null` for an empty or invalid value. */
export function isoDateTimeToDate(iso: string | null | undefined): Date | null {
  const match = /^(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2})/.exec(iso ?? '');
  const day = match ? isoDateToDate(match[1]) : null;
  if (!match || !day || !validTime(match[2])) {
    return null;
  }
  day.setHours(Number(match[2].slice(0, 2)), Number(match[2].slice(3)));
  return day;
}

/** Local wall time of a `Date` → `yyyy-MM-ddTHH:mm`. */
export function dateToIsoDateTime(date: Date): string {
  return `${dateToIsoDate(date)}T${dateToTime(date)}`;
}

/** `HH:mm` → that time on the local day of `day` (default: 1 Jan 2000, a fixed day), `null` when invalid. */
export function timeToDate(time: string | null | undefined, day = new Date(2000, 0, 1)): Date | null {
  if (!time || !validTime(time.slice(0, 5))) {
    return null;
  }
  const date = new Date(day.getFullYear(), day.getMonth(), day.getDate());
  date.setHours(Number(time.slice(0, 2)), Number(time.slice(3, 5)));
  return date;
}

/** Local time of a `Date` → `HH:mm`. */
export function dateToTime(date: Date): string {
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/**
 * Whether a value lies within `min`/`max` (inclusive; blank bounds are open). All three are strings of the same
 * fixed-width ISO format, so they compare as text.
 */
export function withinRange(value: string, min?: string | null, max?: string | null): boolean {
  return (!min || value >= min) && (!max || value <= max);
}
