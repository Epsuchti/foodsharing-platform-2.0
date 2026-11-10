import { TestBed } from '@angular/core/testing';

import { I18nService } from '../i18n/i18n.service';
import { LANGUAGES, intlLocale } from '../i18n/languages';
import { DateTimeFormatter, formatNumeric } from './date-time.pipe';

function formatterFor(locale: string): DateTimeFormatter {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [{ provide: I18nService, useValue: { locale: () => locale } }],
  });
  return TestBed.inject(DateTimeFormatter);
}

describe('DateTimeFormatter numeric styles (D6)', () => {
  // 10.11.2026 18:30 in Zürich (UTC+1)
  const at = '2026-11-10T17:30:00Z';

  for (const language of LANGUAGES) {
    it(`gives the one pattern in ${language}, French included`, () => {
      const formatter = formatterFor(intlLocale(language));
      expect(formatter.format(at, 'dateTime', 'Europe/Zurich')).toBe('10.11.2026, 18:30');
      expect(formatter.format(at, 'date', 'Europe/Zurich')).toBe('10.11.2026');
      expect(formatter.format(at, 'time', 'Europe/Zurich')).toBe('18:30');
    });
  }

  it('pads day, month, hour and minute and uses 24 hours', () => {
    const formatter = formatterFor('en-CH');
    expect(formatter.format('2026-03-05T07:05:00Z', 'dateTime', 'Europe/Zurich')).toBe('05.03.2026, 08:05');
    expect(formatter.format('2026-11-10T13:00:00Z', 'time', 'Europe/Zurich')).toBe('14:00');
  });

  it('shows the day of the time zone around midnight', () => {
    const formatter = formatterFor('de-CH');
    // 23:30 UTC on 10 Nov is 00:30 on 11 Nov in Zürich
    expect(formatter.format('2026-11-10T23:30:00Z', 'dateTime', 'Europe/Zurich')).toBe('11.11.2026, 00:30');
    expect(formatter.format('2026-11-10T23:30:00Z', 'dateTime', 'UTC')).toBe('10.11.2026, 23:30');
  });

  it('follows daylight saving time', () => {
    const formatter = formatterFor('de-CH');
    // summer time ends on 25.10.2026 at 03:00 local time: 00:30 UTC is 02:30 CEST, 01:30 UTC is 02:30 CET
    expect(formatter.format('2026-10-25T00:30:00Z', 'dateTime', 'Europe/Zurich')).toBe('25.10.2026, 02:30');
    expect(formatter.format('2026-10-25T01:30:00Z', 'dateTime', 'Europe/Zurich')).toBe('25.10.2026, 02:30');
    expect(formatter.format('2026-07-01T16:30:00Z', 'time', 'Europe/Zurich')).toBe('18:30');
  });

  it('keeps a calendar date (yyyy-MM-dd) on its day in every time zone', () => {
    const formatter = formatterFor('fr-CH');
    for (const zone of ['Europe/Zurich', 'America/Los_Angeles', 'Pacific/Kiritimati', 'UTC']) {
      expect(formatter.format('2026-11-10', 'date', zone)).toBe('10.11.2026');
    }
    expect(formatter.format('2026-01-01', 'date', 'America/Los_Angeles')).toBe('01.01.2026');
  });

  it('is empty for missing or invalid values', () => {
    const formatter = formatterFor('de-CH');
    expect(formatter.format(null, 'date')).toBe('');
    expect(formatter.format('', 'dateTime')).toBe('');
    expect(formatter.format('nope', 'dateTime')).toBe('');
  });

  it('formats a Date without the formatter service', () => {
    expect(formatNumeric(new Date(at), 'dateTime', 'Europe/Zurich')).toBe('10.11.2026, 18:30');
  });
});

describe('DateTimeFormatter tile and relative styles', () => {
  it('keeps the localized tile parts', () => {
    const formatter = formatterFor('de-CH');
    expect(formatter.format('2026-11-12T17:30:00Z', 'weekdayShort', 'Europe/Zurich')).toBe('Do');
    expect(formatter.format('2026-11-12T17:30:00Z', 'dayNumber', 'Europe/Zurich')).toBe('12');
    expect(formatter.format('2026-11-12T17:30:00Z', 'monthShort', 'Europe/Zurich')).toBe('Nov');
  });

  it('keeps relative times in the UI language', () => {
    const now = new Date('2026-11-10T17:30:00Z');
    expect(formatterFor('de-CH').format('2026-11-10T17:25:00Z', 'relative', 'Europe/Zurich', now)).toBe(
      'vor 5 Minuten',
    );
    expect(formatterFor('en-CH').format('2026-11-10T17:25:00Z', 'relative', 'Europe/Zurich', now)).toBe(
      '5 minutes ago',
    );
  });
});

describe('DateTimeFormatter.sameDay', () => {
  let formatter: DateTimeFormatter;

  beforeEach(() => {
    formatter = formatterFor('de-CH');
  });

  it('is true for two times on one calendar day', () => {
    expect(formatter.sameDay('2026-10-17T06:00:00Z', '2026-10-17T09:00:00Z', 'Europe/Zurich')).toBe(
      true,
    );
  });

  it('is false across days and judges the day in the given time zone', () => {
    expect(formatter.sameDay('2026-10-17T06:00:00Z', '2026-10-18T09:00:00Z', 'Europe/Zurich')).toBe(
      false,
    );
    // 23:30 and 00:30 local time (UTC+2): one hour apart, but not the same day
    expect(formatter.sameDay('2026-10-17T21:30:00Z', '2026-10-17T22:30:00Z', 'Europe/Zurich')).toBe(
      false,
    );
  });

  it('is false for missing or invalid values', () => {
    expect(formatter.sameDay(null, '2026-10-17T09:00:00Z')).toBe(false);
    expect(formatter.sameDay('nope', 'nope')).toBe(false);
  });
});
