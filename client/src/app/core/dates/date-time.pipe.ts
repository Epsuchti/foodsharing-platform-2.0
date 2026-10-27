import { Injectable, Pipe, PipeTransform, inject } from '@angular/core';

import { I18nService } from '../i18n/i18n.service';

/** Until regions exist (M2) every date is shown in Zürich time. */
export const DEFAULT_TIME_ZONE = 'Europe/Zurich';

export type DateStyle =
  'date' | 'dateLong' | 'time' | 'dateTime' | 'dateTimeLong' | 'weekday' | 'relative';

const FORMATS: Record<Exclude<DateStyle, 'relative'>, Intl.DateTimeFormatOptions> = {
  date: { day: '2-digit', month: '2-digit', year: 'numeric' },
  dateLong: { day: 'numeric', month: 'long', year: 'numeric' },
  time: { hour: '2-digit', minute: '2-digit' },
  dateTime: {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  },
  dateTimeLong: {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  },
  weekday: { weekday: 'long', day: 'numeric', month: 'long' },
};

const RELATIVE_UNITS: [Intl.RelativeTimeFormatUnit, number][] = [
  ['year', 365 * 24 * 3600],
  ['month', 30 * 24 * 3600],
  ['week', 7 * 24 * 3600],
  ['day', 24 * 3600],
  ['hour', 3600],
  ['minute', 60],
  ['second', 1],
];

/** Locale- and time-zone-aware date formatting (locale from the UI language: de/gws → de-CH, …). */
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
    const date = value instanceof Date ? value : new Date(value);
    if (Number.isNaN(date.getTime())) {
      return '';
    }
    const locale = this.i18n.locale();
    if (style === 'relative') {
      return this.relative(date, now, locale);
    }
    const key = `${locale}|${style}|${timeZone}`;
    let formatter = this.cache.get(key);
    if (!formatter) {
      formatter = new Intl.DateTimeFormat(locale, { ...FORMATS[style], timeZone });
      this.cache.set(key, formatter);
    }
    return formatter.format(date);
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

  transform(
    value: string | number | Date | null | undefined,
    style: DateStyle = 'dateTime',
    timeZone?: string,
  ): string {
    return this.formatter.format(value, style, timeZone ?? DEFAULT_TIME_ZONE);
  }
}
