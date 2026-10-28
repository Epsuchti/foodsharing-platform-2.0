import { addDays, dayOf, groupByDay, zonedToUtc } from './pickups.util';

describe('pickups util (A20)', () => {
  it('converts store-local date and time to the UTC pickupAt, across DST', () => {
    expect(zonedToUtc('2026-11-05', '19:00', 'Europe/Zurich')).toBe('2026-11-05T18:00:00Z');
    expect(zonedToUtc('2026-07-01', '19:00', 'Europe/Zurich')).toBe('2026-07-01T17:00:00Z');
  });

  it('groups pickups by local day and adds days', () => {
    expect(dayOf('2026-11-04T23:30:00Z', 'Europe/Zurich')).toBe('2026-11-05');
    expect(addDays('2026-11-30', 1)).toBe('2026-12-01');
    const groups = groupByDay(
      [{ pickupAt: '2026-11-05T18:00:00Z' }, { pickupAt: '2026-11-04T17:30:00Z' }, { pickupAt: '2026-11-05T09:00:00Z' }],
      'Europe/Zurich',
    );
    expect(groups.map((g) => [g.day, g.items.length])).toEqual([
      ['2026-11-04', 1],
      ['2026-11-05', 2],
    ]);
  });
});
