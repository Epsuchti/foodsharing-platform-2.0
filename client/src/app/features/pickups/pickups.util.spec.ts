import {
  addDays,
  countSummary,
  dayOf,
  firstName,
  groupByDay,
  mergeByTime,
  moveSummary,
  pickupSummary,
  slotState,
  timeOf,
  unconfirmedCount,
  zonedToUtc,
} from './pickups.util';
import { UserPickup } from '../../api';

describe('pickups util (A20)', () => {
  it('gives the wall time of an instant in the store zone (PU10 move preset)', () => {
    expect(timeOf('2026-11-05T18:00:00Z', 'Europe/Zurich')).toBe('19:00');
    expect(timeOf('2026-07-01T05:05:00Z', 'Europe/Zurich')).toBe('07:05');
  });

  it('summarises a move: everyone but the mover is told, linked EinAbs with their newcomers (PU10)', () => {
    const slots = [
      { user: { id: 'me' } },
      { user: { id: 'guide' }, linkedIntroPickup: { bookedCount: 2 } },
      { user: { id: 'other' } },
    ];
    expect(moveSummary(slots, 'me')).toEqual({ notified: 2, introPickups: 1, booked: 2 });
    expect(moveSummary(slots, 'nobody')).toEqual({ notified: 3, introPickups: 1, booked: 2 });
    expect(moveSummary([], 'me')).toEqual({ notified: 0, introPickups: 0, booked: 0 });
  });

  it('converts store-local date and time to the UTC pickupAt, across DST', () => {
    expect(zonedToUtc('2026-11-05', '19:00', 'Europe/Zurich')).toBe('2026-11-05T18:00:00Z');
    expect(zonedToUtc('2026-07-01', '19:00', 'Europe/Zurich')).toBe('2026-07-01T17:00:00Z');
  });

  it('merges pickups and cleaning shifts by the real instant (decision 154)', () => {
    const pickup = { store: { id: 's', name: 'Coop' }, pickupAt: '2026-11-05T18:00:00Z', confirmed: true, slotCount: 1 } as UserPickup;
    const early = { fsp: { id: 'f', name: 'Wiedikon' }, shiftAt: '2026-11-05T17:00:00+00:00', confirmed: false };
    const late = { fsp: { id: 'f', name: 'Wiedikon' }, shiftAt: '2026-11-05T20:00:00+01:00', confirmed: true };
    expect(mergeByTime([pickup], [late, early]).map((entry) => entry.at)).toEqual([early.shiftAt, pickup.pickupAt, late.shiftAt]);
    expect(mergeByTime([], [])).toEqual([]);
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

describe('pickup slot helpers (PU3)', () => {
  const slot = (id: string, confirmed: boolean) => ({ user: { id }, confirmed });

  it('tells own slots apart from confirmed and unconfirmed ones', () => {
    expect(slotState(slot('a', true), 'me')).toBe('confirmed');
    expect(slotState(slot('a', false), 'me')).toBe('unconfirmed');
    expect(slotState(slot('me', true), 'me')).toBe('mineConfirmed');
    expect(slotState(slot('me', false), 'me')).toBe('mineUnconfirmed');
    expect(slotState(slot('me', false), undefined)).toBe('unconfirmed');
  });

  it('summarises a pickup from its slots', () => {
    expect(pickupSummary([slot('a', true), slot('b', true), slot('c', false)], 4)).toEqual({
      confirmed: 2,
      unconfirmed: 1,
      total: 4,
    });
    expect(pickupSummary([], 3)).toEqual({ confirmed: 0, unconfirmed: 0, total: 3 });
  });

  it('builds the options page summary from counts, never below 0', () => {
    expect(countSummary(3, 1, 4)).toEqual({ confirmed: 1, unconfirmed: 2, total: 4 });
    expect(countSummary(2, 2, 2)).toEqual({ confirmed: 2, unconfirmed: 0, total: 2 });
    expect(countSummary(1, 3, 2)).toEqual({ confirmed: 1, unconfirmed: 0, total: 2 });
    expect(countSummary(1, -1, 2)).toEqual({ confirmed: 0, unconfirmed: 1, total: 2 });
  });

  it('counts unconfirmed sign-ups across pickups', () => {
    expect(
      unconfirmedCount([
        { occupiedSlots: [slot('a', false), slot('b', true)] },
        { occupiedSlots: [] },
        { occupiedSlots: [slot('c', false), slot('d', false)] },
      ]),
    ).toBe(3);
    expect(unconfirmedCount([])).toBe(0);
  });

  it('takes the first name up to the first space', () => {
    expect(firstName('Marco Bernasconi')).toBe('Marco');
    expect(firstName('  Priya  ')).toBe('Priya');
    expect(firstName('Anna Lena Meier')).toBe('Anna');
  });
});
