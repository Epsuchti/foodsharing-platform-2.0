import { TestBed } from '@angular/core/testing';

import { I18nService } from '../i18n/i18n.service';
import { DateTimeFormatter } from './date-time.pipe';

describe('DateTimeFormatter.sameDay', () => {
  let formatter: DateTimeFormatter;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [{ provide: I18nService, useValue: { locale: () => 'de-CH' } }],
    });
    formatter = TestBed.inject(DateTimeFormatter);
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
