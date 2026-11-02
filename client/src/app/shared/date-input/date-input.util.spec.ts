import {
  dateToIsoDate,
  dateToIsoDateTime,
  dateToTime,
  formatDisplayDate,
  formatDisplayDateTime,
  isoDateTimeToDate,
  isoDateToDate,
  parseDisplayDate,
  parseDisplayDateTime,
  parseDisplayTime,
  timeToDate,
  validTime,
  withinRange,
} from './date-input.util';

describe('date-input.util (decision 150)', () => {
  describe('parseDisplayDate', () => {
    it.each([
      ['01.02.2026', '2026-02-01'],
      ['1.2.2026', '2026-02-01'],
      ['1.2.26', '2026-02-01'],
      ['31.12.85', '1985-12-31'],
      ['  29.02.2028 ', '2028-02-29'],
      ['2026-02-01', '2026-02-01'],
      ['2026-2-1', '2026-02-01'],
    ])('reads %s as %s', (text, iso) => {
      expect(parseDisplayDate(text)).toBe(iso);
    });

    it('gives an empty value for blank text', () => {
      expect(parseDisplayDate('')).toBe('');
      expect(parseDisplayDate('   ')).toBe('');
    });

    it.each(['31.02.2026', '29.02.2027', '01.13.2026', '00.01.2026', '1.1.', 'morgen', '01/02/2026', '2026-02-30', '1.2.202'])(
      'rejects %s',
      (text) => {
        expect(parseDisplayDate(text)).toBeNull();
      },
    );
  });

  describe('parseDisplayTime', () => {
    it.each([
      ['18:30', '18:30'],
      ['8:05', '08:05'],
      ['8.05', '08:05'],
      ['00:00', '00:00'],
      ['23:59', '23:59'],
    ])('reads %s as %s', (text, time) => {
      expect(parseDisplayTime(text)).toBe(time);
    });

    it.each(['24:00', '12:60', '6 PM', '1830', 'x'])('rejects %s', (text) => {
      expect(parseDisplayTime(text)).toBeNull();
    });

    it('gives an empty value for blank text', () => {
      expect(parseDisplayTime(' ')).toBe('');
    });
  });

  describe('parseDisplayDateTime', () => {
    it.each([
      ['04.11.2026 18:30', '2026-11-04T18:30'],
      ['4.11.26  8:05', '2026-11-04T08:05'],
      ['2026-11-04T18:30', '2026-11-04T18:30'],
    ])('reads %s as %s', (text, iso) => {
      expect(parseDisplayDateTime(text)).toBe(iso);
    });

    it.each(['04.11.2026', '31.11.2026 18:30', '04.11.2026 25:00', '18:30'])('rejects %s', (text) => {
      expect(parseDisplayDateTime(text)).toBeNull();
    });
  });

  it('formats values for display', () => {
    expect(formatDisplayDate('2026-02-01')).toBe('01.02.2026');
    expect(formatDisplayDate('')).toBe('');
    expect(formatDisplayDateTime('2026-11-04T18:30')).toBe('04.11.2026 18:30');
    expect(formatDisplayDateTime(undefined)).toBe('');
  });

  describe('Date conversion uses local components only (no zone shift)', () => {
    it('round-trips a date at local midnight', () => {
      const date = isoDateToDate('2026-03-29')!;
      expect([date.getFullYear(), date.getMonth(), date.getDate(), date.getHours()]).toEqual([2026, 2, 29, 0]);
      expect(dateToIsoDate(date)).toBe('2026-03-29');
    });

    it('keeps the local day where the UTC day differs', () => {
      // 00:30 local is the previous UTC day east of Greenwich and 23:30 local the next one west of it:
      // the result must follow the local day in every zone
      const early = new Date(2026, 0, 1, 0, 30);
      const late = new Date(2026, 0, 1, 23, 30);
      expect(dateToIsoDate(early)).toBe('2026-01-01');
      expect(dateToIsoDate(late)).toBe('2026-01-01');
      // a fixed-offset check: whatever the zone, the UTC rendering is off by exactly the offset
      const offsetMinutes = early.getTimezoneOffset();
      expect(new Date(early.getTime() - offsetMinutes * 60_000).toISOString().slice(0, 10)).toBe('2026-01-01');
    });

    it('round-trips a wall time', () => {
      const date = isoDateTimeToDate('2026-10-25T02:30')!;
      expect(dateToIsoDateTime(date).slice(0, 10)).toBe('2026-10-25');
      expect(dateToIsoDateTime(isoDateTimeToDate('2026-11-04T18:30')!)).toBe('2026-11-04T18:30');
      expect(dateToIsoDateTime(isoDateTimeToDate('2026-11-04T18:30:00')!)).toBe('2026-11-04T18:30');
    });

    it('round-trips a time', () => {
      expect(dateToTime(timeToDate('07:45')!)).toBe('07:45');
      expect(timeToDate('')).toBeNull();
      expect(timeToDate('25:00')).toBeNull();
    });

    it('rejects invalid values', () => {
      expect(isoDateToDate('2026-02-30')).toBeNull();
      expect(isoDateToDate('')).toBeNull();
      expect(isoDateTimeToDate('2026-02-01')).toBeNull();
    });
  });

  it('compares min/max as strings', () => {
    expect(withinRange('2026-02-01', '2026-01-31', '2026-02-01')).toBe(true);
    expect(withinRange('2026-01-30', '2026-01-31')).toBe(false);
    expect(withinRange('2026-02-02', undefined, '2026-02-01')).toBe(false);
    expect(withinRange('2026-11-04T18:30', '2026-11-04T18:00', '')).toBe(true);
    expect(withinRange('09:00', '10:00', null)).toBe(false);
  });

  it('validates 24h times', () => {
    expect(validTime('23:59')).toBe(true);
    expect(validTime('7:00')).toBe(false);
  });
});
