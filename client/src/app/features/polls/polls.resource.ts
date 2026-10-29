import { PollOption, PollStatus, PollSummary, PollType } from '../../api';

/** Allowed values per option and type (A37c / upstream PollRules.allowedValues). */
export const POLL_VALUES: Readonly<Record<PollType, readonly number[]>> = {
  SELECT_ONE: [1],
  SELECT_MULTIPLE: [1],
  THUMBS: [1, 0, -1],
  SCORE: [3, 2, 1, 0, -1, -2, -3],
};

/**
 * The vote body (A37c): keys are option indices as strings. SELECT_ONE / SELECT_MULTIPLE send only the chosen
 * options with 1, THUMBS / SCORE a value for every option. Returns null while the ballot is incomplete.
 */
export function buildVote(type: PollType, options: readonly PollOption[], choice: Readonly<Record<number, number | undefined>>): Record<string, number> | null {
  const values: Record<string, number> = {};
  if (type === PollType.SelectOne || type === PollType.SelectMultiple) {
    for (const option of options) if (choice[option.index] === 1) values[String(option.index)] = 1;
    return type === PollType.SelectOne && Object.keys(values).length !== 1 ? null : values;
  }
  for (const option of options) {
    const value = choice[option.index];
    if (value === undefined || !POLL_VALUES[type].includes(value)) return null;
    values[String(option.index)] = value;
  }
  return values;
}

/** Stable per-user shuffle (mulberry32 seeded by the user id) so the order does not jump on reloads. */
export function seededShuffle<T>(items: readonly T[], seed: string): T[] {
  let h = 1779033703;
  for (let i = 0; i < seed.length; i++) h = Math.imul(h ^ seed.charCodeAt(i), 3432918353);
  let state = h >>> 0;
  const random = () => {
    state = (state + 0x6d2b79f5) >>> 0;
    let t = state;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
  const result = [...items];
  for (let i = result.length - 1; i > 0; i--) {
    const j = Math.floor(random() * (i + 1));
    [result[i], result[j]] = [result[j], result[i]];
  }
  return result;
}

/** Option list errors (A37c POLL_OPTIONS_INVALID): 2–50 options, none blank, unique ignoring case. */
export function optionErrors(options: readonly string[]): 'count' | 'blank' | 'duplicate' | null {
  if (options.length < 2 || options.length > 50) return 'count';
  if (options.some((o) => !o.trim())) return 'blank';
  const seen = new Set(options.map((o) => o.trim().toLowerCase()));
  return seen.size === options.length ? null : 'duplicate';
}

/** Date rule (A37c POLL_DATES_INVALID): start at least one hour from now and before the end. */
export function datesValid(startsAt: string, endsAt: string, now: number): boolean {
  const start = Date.parse(startsAt);
  const end = Date.parse(endsAt);
  return !Number.isNaN(start) && !Number.isNaN(end) && start >= now + 3_600_000 && start < end;
}

/** Results per option: votes of each value and, for score/thumbs, the weighted sum. */
export function optionTotals(option: PollOption): { total: number; sum: number } {
  const values = option.values ?? [];
  return { total: values.reduce((n, v) => n + v.votes, 0), sum: values.reduce((n, v) => n + v.value * v.votes, 0) };
}

export const STATUS_ORDER: readonly PollStatus[] = [PollStatus.Running, PollStatus.Upcoming, PollStatus.Ended, PollStatus.Cancelled];

export function byStatus(polls: readonly PollSummary[]): { status: PollStatus; polls: PollSummary[] }[] {
  return STATUS_ORDER.map((status) => ({ status, polls: polls.filter((p) => p.status === status) })).filter((g) => g.polls.length);
}
