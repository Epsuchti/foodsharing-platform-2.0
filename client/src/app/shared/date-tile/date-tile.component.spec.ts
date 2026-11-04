import { dateTileParts } from './date-tile.component';

describe('fs-date-tile parts', () => {
  const now = new Date('2026-11-04T08:00:00Z');

  it('shows weekday, day and month in the zone and marks today', () => {
    expect(dateTileParts('2026-11-04T17:00:00Z', 'Europe/Zurich', 'en-GB', now)).toEqual({
      weekday: 'Wed',
      day: '4',
      month: 'Nov',
      today: true,
    });
    expect(dateTileParts('2026-11-06T17:30:00Z', 'Europe/Zurich', 'en-GB', now)?.today).toBe(false);
  });

  it('uses the zone for the calendar day (23:30 UTC is the next day in Zürich)', () => {
    const parts = dateTileParts('2026-11-04T23:30:00Z', 'Europe/Zurich', 'de-CH', now);
    expect(parts?.day).toBe('5');
    expect(parts?.today).toBe(false);
  });

  it('crosses month boundaries and ignores invalid dates', () => {
    expect(dateTileParts('2026-12-01T17:30:00Z', 'Europe/Zurich', 'en-GB', now)?.month).toBe('Dec');
    expect(dateTileParts('nope', 'Europe/Zurich', 'en-GB', now)).toBeNull();
  });
});
