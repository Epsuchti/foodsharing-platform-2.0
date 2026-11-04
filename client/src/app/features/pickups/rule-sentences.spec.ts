import { LinkedStoreWindowMode, PolicyBlock, PolicyRule, StoreRuleSummary } from '../../api';
import { RuleFormat, blockReason, linkWindow, ruleCount, summaryLines } from './rule-sentences';

/** Formatting stub: days and dates as given, lists joined with the kind, nested parts as "key(params)". */
const fmt: RuleFormat = {
  day: (iso) => `day(${iso.slice(0, 10)})`,
  date: (date) => `date(${date})`,
  list: (names, kind) => names.join(` ${kind} `),
  t: (key, params) => (params ? `${key}${JSON.stringify(params)}` : key),
};

const store = (id: string, name: string) => ({ id, name });
const NOW = new Date('2026-11-02T09:00:00Z');

const summary = (patch: Partial<StoreRuleSummary>): StoreRuleSummary => ({
  enabled: true,
  links: [],
  experience: false,
  manualDates: [],
  holidays: false,
  ...patch,
});

describe('rule sentences (decision 165 S2)', () => {
  it('has no lines while the rules are off or missing', () => {
    expect(summaryLines(undefined, fmt)).toEqual([]);
    expect(summaryLines(summary({ enabled: false, gapDays: 7, experience: true }), fmt)).toEqual([]);
  });

  it('builds one line per active rule from structured data', () => {
    const lines = summaryLines(
      summary({
        gapDays: 7,
        group: { days: 3, stores: [store('a', 'Coop'), store('b', 'Denner'), store('c', 'Migros')] },
        links: [{ stores: [store('y', "Babu's Bakery")], windowMode: LinkedStoreWindowMode.LastDays, before: 0, after: 3 }],
        experience: true,
        cleaningMonths: 12,
        manualDates: ['2026-12-24', '2026-12-31'],
        holidays: true,
      }),
      fmt,
    );
    expect(lines.map((line) => line.rule)).toEqual(['GAP', 'GROUP_GAP', 'LINKED_STORE', 'EXPERIENCE', 'CLEANING', 'MANUAL_DATE']);
    expect(lines[0]).toMatchObject({ key: 'pickups.rules.gap', params: { days: 7 } });
    expect(lines[1]).toMatchObject({ key: 'pickups.rules.groupGap', params: { stores: 'Coop and Denner and Migros', days: 3 } });
    expect(lines[2]).toMatchObject({
      key: 'pickups.rules.linked',
      params: { stores: "Babu's Bakery", window: 'pickups.rules.window.after{"days":3}' },
    });
    expect(lines[4]).toMatchObject({ key: 'pickups.rules.cleaning', params: { months: 12 } });
    expect(lines[5]).toMatchObject({ key: 'pickups.rules.manual', params: { dates: 'date(2026-12-24) and date(2026-12-31)' } });
    // own and group gap count as one rule
    expect(ruleCount(lines)).toBe(5);
  });

  it('names holidays and manual dates separately when only one applies', () => {
    expect(summaryLines(summary({ holidays: true }), fmt)[0]).toMatchObject({ key: 'pickups.rules.holidays', params: {} });
    expect(summaryLines(summary({ manualDates: ['2026-12-24'] }), fmt)[0]).toMatchObject({
      key: 'pickups.rules.manualDates',
      params: { dates: 'date(2026-12-24)' },
    });
    // a group gap alone is one rule
    expect(ruleCount(summaryLines(summary({ group: { days: 2, stores: [store('a', 'A'), store('b', 'B')] } }), fmt))).toBe(1);
  });

  it('describes the link window', () => {
    const w = (windowMode: LinkedStoreWindowMode, before: number, after: number) => linkWindow({ windowMode, before, after }, fmt);
    expect(w(LinkedStoreWindowMode.SameDay, 3, 3)).toBe('pickups.rules.window.sameDay');
    expect(w(LinkedStoreWindowMode.LastDays, 0, 0)).toBe('pickups.rules.window.sameDay');
    expect(w(LinkedStoreWindowMode.LastDays, 2, 0)).toBe('pickups.rules.window.before{"days":2}');
    expect(w(LinkedStoreWindowMode.LastDays, 1, 3)).toBe('pickups.rules.window.range{"before":1,"after":3}');
  });

  it('gap: the own data, the next possible day and a jump to it', () => {
    const block: PolicyBlock = {
      rule: PolicyRule.Gap,
      params: { lastPickupAt: '2026-10-29T18:00:00Z', nextPossibleAt: '2026-11-06T23:00:00Z', gapDays: 7 },
    };
    const reason = blockReason(block, fmt, NOW);
    expect(reason.chip).toBe('pickups.blocked.rule.GAP');
    expect(reason.text).toEqual({ key: 'pickups.blocked.GAP', params: { gap: 7, last: 'day(2026-10-29)' } });
    expect(reason.fix).toEqual({ key: 'pickups.blocked.GAP.fix', params: { next: 'day(2026-11-06)' } });
    expect(reason.action).toMatchObject({ kind: 'jump', at: '2026-11-06T23:00:00Z' });
  });

  it('gap: an upcoming pickup here, or a pickup in another store of the gap list', () => {
    const upcoming = blockReason({ rule: PolicyRule.Gap, params: { lastPickupAt: '2026-11-12T18:00:00Z', gapDays: 3 } }, fmt, NOW);
    expect(upcoming.text.key).toBe('pickups.blocked.GAP.upcoming');
    expect(upcoming.action).toBeUndefined();
    const group = blockReason(
      { rule: PolicyRule.Gap, params: { lastPickupAt: '2026-11-10T18:00:00Z', gapDays: 2, stores: [store('c', 'Coop Stauffacher')] } },
      fmt,
      NOW,
    );
    expect(group.text).toEqual({
      key: 'pickups.blocked.GAP.group',
      params: { gap: 2, store: 'Coop Stauffacher', last: 'day(2026-11-10)' },
    });
  });

  it('linked store: stores, window and dates, the action opens the linked store', () => {
    const reason = blockReason(
      {
        rule: PolicyRule.LinkedStore,
        params: {
          stores: [store('y', "Babu's Bakery"), store('z', 'Bäckerei Vohdin')],
          windowMode: LinkedStoreWindowMode.LastDays,
          before: 0,
          after: 3,
          from: '2026-11-08T23:00:00Z',
          to: '2026-11-11T23:00:00Z',
        },
      },
      fmt,
      NOW,
    );
    expect(reason.text.params).toEqual({
      stores: "Babu's Bakery or Bäckerei Vohdin",
      window: 'pickups.rules.window.after{"days":3}',
      from: 'day(2026-11-08)',
      to: 'day(2026-11-11)',
    });
    expect(reason.action).toMatchObject({ kind: 'store', store: { id: 'y' } });
  });

  it('experience, cleaning and unknown rules', () => {
    expect(blockReason({ rule: PolicyRule.Experience, params: {} }, fmt, NOW).fix?.key).toBe('pickups.blocked.EXPERIENCE.fix');
    const never = blockReason({ rule: PolicyRule.CleaningRequired, params: { monthsRequired: 12, fspId: 'f' } }, fmt, NOW);
    expect(never.text).toEqual({ key: 'pickups.blocked.CLEANING_REQUIRED.never', params: { required: 12 } });
    expect(never.action).toMatchObject({ kind: 'cleaning', fspId: 'f' });
    const ago = blockReason({ rule: PolicyRule.CleaningRequired, params: { monthsAgo: 14, monthsRequired: 12 } }, fmt, NOW);
    expect(ago.text).toEqual({ key: 'pickups.blocked.CLEANING_REQUIRED', params: { ago: 14, required: 12 } });
    expect(ago.action).toBeUndefined();
    expect(blockReason({ rule: PolicyRule.NoCleaningFairteiler, params: {} }, fmt, NOW).chip).toBe('pickups.blocked.rule.CLEANING');
    expect(blockReason({ rule: PolicyRule.Distance, params: {} }, fmt, NOW)).toMatchObject({
      chip: 'pickups.blocked.rule.other',
      text: { key: 'pickups.blocked.other' },
    });
  });
});
