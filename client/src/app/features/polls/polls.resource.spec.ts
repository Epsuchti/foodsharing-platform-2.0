import { PollOption, PollStatus, PollSummary, PollType } from '../../api';
import { buildVote, byStatus, datesValid, optionErrors, optionTotals, seededShuffle } from './polls.resource';

const options: PollOption[] = [0, 1, 2].map((index) => ({ index, text: `o${index}` }));

describe('poll helpers', () => {
  it('builds votes per type (A37c)', () => {
    expect(buildVote(PollType.SelectOne, options, { 1: 1 })).toEqual({ '1': 1 });
    expect(buildVote(PollType.SelectOne, options, {})).toBeNull();
    expect(buildVote(PollType.SelectMultiple, options, { 0: 1, 2: 1, 1: undefined })).toEqual({ '0': 1, '2': 1 });
    expect(buildVote(PollType.SelectMultiple, options, {})).toEqual({});
    expect(buildVote(PollType.Thumbs, options, { 0: 1, 1: 0 })).toBeNull();
    expect(buildVote(PollType.Thumbs, options, { 0: 1, 1: 0, 2: -1 })).toEqual({ '0': 1, '1': 0, '2': -1 });
    expect(buildVote(PollType.Score, options, { 0: 3, 1: -3, 2: 4 })).toBeNull();
  });

  it('shuffles stably per seed', () => {
    const items = Array.from({ length: 10 }, (_, i) => i);
    expect(seededShuffle(items, 'user-a')).toEqual(seededShuffle(items, 'user-a'));
    expect(seededShuffle(items, 'user-a')).not.toEqual(items);
    expect([...seededShuffle(items, 'user-b')].sort((a, b) => a - b)).toEqual(items);
  });

  it('validates options and dates', () => {
    expect(optionErrors(['a'])).toBe('count');
    expect(optionErrors(['a', ' '])).toBe('blank');
    expect(optionErrors(['Ja', 'ja '])).toBe('duplicate');
    expect(optionErrors(['Ja', 'Nein'])).toBeNull();
    const now = Date.parse('2026-11-02T10:00:00Z');
    expect(datesValid('2026-11-02T10:30:00Z', '2026-11-03T10:00:00Z', now)).toBe(false);
    expect(datesValid('2026-11-02T11:00:00Z', '2026-11-03T10:00:00Z', now)).toBe(true);
    expect(datesValid('2026-11-04T11:00:00Z', '2026-11-03T10:00:00Z', now)).toBe(false);
  });

  it('sums results and groups by status', () => {
    expect(optionTotals({ index: 0, text: 'x', values: [{ value: 3, votes: 2 }, { value: -1, votes: 1 }] })).toEqual({ total: 3, sum: 5 });
    const p = (id: string, status: PollStatus) => ({ id, status }) as PollSummary;
    expect(byStatus([p('a', PollStatus.Ended), p('b', PollStatus.Running)]).map((g) => g.status)).toEqual(['RUNNING', 'ENDED']);
  });
});
