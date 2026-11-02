import { CleaningShift } from '../../api';
import { formatDay, parseDay, shiftsByDay, validTime } from './cleaning.util';

function shift(shiftAt: string): CleaningShift {
  return {
    fspId: 'f',
    shiftAt,
    slotCount: 1,
    occupiedSlots: [],
    isAvailable: true,
    isRegular: false,
  };
}

describe('cleaning.util (FS2)', () => {
  it('groups shifts by their Zürich day in time order', () => {
    const days = shiftsByDay(
      [shift('2026-11-07T09:00:00Z'), shift('2026-11-06T23:30:00Z'), shift('2026-11-07T17:00:00Z')],
      'Europe/Zurich',
    );
    expect(days.map((d) => d.day)).toEqual(['2026-11-07']);
    expect(days[0].items.map((s) => s.shiftAt)).toEqual([
      '2026-11-06T23:30:00Z',
      '2026-11-07T09:00:00Z',
      '2026-11-07T17:00:00Z',
    ]);
  });

  it('parses Swiss and ISO dates and rejects impossible days', () => {
    expect(parseDay('7.11.2026')).toBe('2026-11-07');
    expect(parseDay(' 07.11.2026 ')).toBe('2026-11-07');
    expect(parseDay('2026-11-07')).toBe('2026-11-07');
    expect(parseDay('31.02.2026')).toBeNull();
    expect(parseDay('2026-13-01')).toBeNull();
    expect(parseDay('morgen')).toBeNull();
    expect(formatDay('2026-11-07')).toBe('07.11.2026');
  });

  it('accepts only HH:MM times', () => {
    expect(validTime('09:30')).toBe(true);
    expect(validTime('23:59')).toBe(true);
    expect(validTime('24:00')).toBe(false);
    expect(validTime('9:30')).toBe(false);
  });
});
