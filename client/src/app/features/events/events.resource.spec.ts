import { EventSummary, EventType } from '../../api';
import { EventDraft, eventErrors, fromLocalInput, groupByMonth, toLocalInput } from './events.resource';

const draft = (patch: Partial<EventDraft> = {}): EventDraft => ({
  type: EventType.Offline,
  name: 'Brunch',
  startsAt: '2026-11-05T10:00',
  endsAt: '2026-11-05T12:00',
  locationName: 'Fairteiler',
  onlineLink: '',
  ...patch,
});

describe('event helpers', () => {
  it('validates per type (A37b)', () => {
    expect(eventErrors(draft())).toEqual([]);
    expect(eventErrors(draft({ locationName: ' ' }))).toEqual(['location']);
    expect(eventErrors(draft({ type: EventType.Online, locationName: '' }))).toEqual(['onlineLink']);
    expect(eventErrors(draft({ type: EventType.Online, onlineLink: 'http://x.ch' }))).toEqual(['onlineLink']);
    expect(eventErrors(draft({ type: EventType.Online, onlineLink: 'https://meet.example.org/a' }))).toEqual([]);
    expect(eventErrors(draft({ type: EventType.Other, locationName: '', name: '', endsAt: '2026-11-05T09:00' }))).toEqual(['name', 'endsAt']);
  });

  it('round-trips datetime-local values', () => {
    const iso = fromLocalInput('2026-11-05T10:30');
    expect(toLocalInput(iso)).toBe('2026-11-05T10:30');
    expect(toLocalInput(undefined)).toBe('');
  });

  it('groups events by month in start order', () => {
    const e = (id: string, startsAt: string) => ({ id, startsAt }) as EventSummary;
    const groups = groupByMonth([e('b', '2026-12-02T10:00:00Z'), e('a', '2026-11-20T10:00:00Z'), e('c', '2026-11-03T10:00:00Z')]);
    expect(groups.map((g) => g.key)).toEqual(['2026-11', '2026-12']);
    expect(groups[0].events.map((x) => x.id)).toEqual(['c', 'a']);
  });
});
