import { Injectable, Pipe, PipeTransform, inject } from '@angular/core';

import { I18nService } from '../i18n/i18n.service';
import { RegionContextService } from '../region/region-context.service';
import { DEFAULT_TIME_ZONE } from './time-zone';

export { DEFAULT_TIME_ZONE } from './time-zone';

/**
 * `date` "10.11.2026", `time` "18:30" and `dateTime` "10.11.2026, 18:30": one fixed numeric pattern, the same in every
 * UI language (D6). The tile styles `weekdayShort` / `dayNumber` / `monthShort` ("Do" / "5" / "Nov.") are only for
 * graphic date tiles; `relative` ("vor 5 Min.") always comes with the full `dateTime` as a tooltip.
 */
export type DateStyle = 'date' | 'time' | 'dateTime' | 'weekdayShort' | 'dayNumber' | 'monthShort' | 'relative';

type NumericStyle = 'date' | 'time' | 'dateTime';

/** Tile styles: localized names, only for the graphic date tiles ("Do" / "5" / "Nov."). */
const TILE_FORMATS: Record<Exclude<DateStyle, NumericStyle | 'relative'>, Intl.DateTimeFormatOptions> = {
  weekdayShort: { weekday: 'short' },
  dayNumber: { day: 'numeric' },
  monthShort: { month: 'short' },
};

/** A calendar date without time and zone (`yyyy-MM-dd`): shown as that day in every time zone. */
const ISO_DATE = /^(\d{4})-(\d{2})-(\d{2})$/;

const NUMERIC_CACHE = new Map<string, Intl.DateTimeFormat>();

/**
 * The fixed numeric pattern of an instant in a time zone: `dd.MM.yyyy`, `HH:mm` (24 h) or `dd.MM.yyyy, HH:mm`,
 * independent of the language. Built from `formatToParts`, so no locale adds a weekday, slash or missing comma.
 */
export function formatNumeric(date: Date, style: NumericStyle, timeZone = DEFAULT_TIME_ZONE): string {
  let format = NUMERIC_CACHE.get(timeZone);
  if (!format) {
    format = new Intl.DateTimeFormat('en-GB', {
      timeZone,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      hourCycle: 'h23',
      numberingSystem: 'latn',
    });
    NUMERIC_CACHE.set(timeZone, format);
  }
  const parts: Partial<Record<Intl.DateTimeFormatPartTypes, string>> = {};
  for (const part of format.formatToParts(date)) {
    parts[part.type] = part.value;
  }
  const day = `${parts.day}.${parts.month}.${parts.year}`;
  const time = `${parts.hour}:${parts.minute}`;
  return style === 'date' ? day : style === 'time' ? time : `${day}, ${time}`;
}

const RELATIVE_UNITS: [Intl.RelativeTimeFormatUnit, number][] = [
  ['year', 365 * 24 * 3600],
  ['month', 30 * 24 * 3600],
  ['week', 7 * 24 * 3600],
  ['day', 24 * 3600],
  ['hour', 3600],
  ['minute', 60],
  ['second', 1],
];

/**
 * Time-zone-aware date formatting (D6): numeric styles use one fixed pattern for every language, the tile and relative
 * styles the UI locale (de/gws → de-CH, …).
 */
@Injectable({ providedIn: 'root' })
export class DateTimeFormatter {
  private readonly i18n = inject(I18nService);
  private readonly cache = new Map<string, Intl.DateTimeFormat>();

  format(
    value: string | number | Date | null | undefined,
    style: DateStyle = 'dateTime',
    timeZone = DEFAULT_TIME_ZONE,
    now: Date = new Date(),
  ): string {
    if (value === null || value === undefined || value === '') {
      return '';
    }
    let date: Date;
    const isoDate = typeof value === 'string' ? ISO_DATE.exec(value) : null;
    if (isoDate) {
      // a calendar date: noon UTC, shown in UTC, so no time zone moves it to the day before or after
      date = new Date(Date.UTC(Number(isoDate[1]), Number(isoDate[2]) - 1, Number(isoDate[3]), 12));
      timeZone = 'UTC';
    } else {
      date = value instanceof Date ? value : new Date(value);
    }
    if (Number.isNaN(date.getTime())) {
      return '';
    }
    if (style === 'date' || style === 'time' || style === 'dateTime') {
      return formatNumeric(date, style, timeZone);
    }
    const locale = this.i18n.locale();
    if (style === 'relative') {
      return this.relative(date, now, locale);
    }
    const key = `${locale}|${style}|${timeZone}`;
    let formatter = this.cache.get(key);
    if (!formatter) {
      formatter = new Intl.DateTimeFormat(locale, { ...TILE_FORMATS[style], timeZone });
      this.cache.set(key, formatter);
    }
    return formatter.format(date);
  }

  /** Both instants fall on the same calendar day in the time zone (a range then names the date once). */
  sameDay(
    a: string | number | Date | null | undefined,
    b: string | number | Date | null | undefined,
    timeZone = DEFAULT_TIME_ZONE,
  ): boolean {
    const first = this.format(a, 'date', timeZone);
    return first !== '' && first === this.format(b, 'date', timeZone);
  }

  private relative(date: Date, now: Date, locale: string): string {
    const seconds = Math.round((date.getTime() - now.getTime()) / 1000);
    const format = new Intl.RelativeTimeFormat(locale, { numeric: 'auto' });
    for (const [unit, size] of RELATIVE_UNITS) {
      if (Math.abs(seconds) >= size || unit === 'second') {
        return format.format(Math.round(seconds / size), unit);
      }
    }
    return format.format(0, 'second');
  }
}

/** `{{ entry.occurredAt | fsDate: 'dateTime' }}`, `{{ x | fsDate: 'relative' }}`, optional time zone. */
@Pipe({ name: 'fsDate', pure: false })
export class DateTimePipe implements PipeTransform {
  private readonly formatter = inject(DateTimeFormatter);
  private readonly region = inject(RegionContextService);

  transform(
    value: string | number | Date | null | undefined,
    style: DateStyle = 'dateTime',
    timeZone?: string,
  ): string {
    // the active region's zone (A15) unless the caller passes one
    return this.formatter.format(value, style, timeZone ?? this.region.timeZone());
  }
}
