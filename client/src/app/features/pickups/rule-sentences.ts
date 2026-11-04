import {
  LinkedOption,
  LinkedOptionRule,
  LinkedStoreWindowMode,
  PickupEligibility,
  PickupEligibilityReason,
  PolicyBlock,
  PolicyRule,
  StoreRef,
  StoreRuleLink,
  StoreRuleSummary,
} from '../../api';

/**
 * The member's view of a store's pickup rules (decision 165 S2): summary lines for the card on the Pickups tab and the
 * reasons of a blocked sign-up, built from structured data only (never server texts). Pure: the caller passes the
 * formatting (dates in the store's time zone, localised lists) and translates `key` with `params`.
 */

/** Formatting the sentences need; the component binds it to the UI language and the store's time zone. */
export interface RuleFormat {
  /** A pickup instant as a day ("Donnerstag, 29. Oktober"). */
  day(iso: string): string;
  /** A calendar date `YYYY-MM-DD` ("24.12."). */
  date(localDate: string): string;
  /** Names joined with "und" (`and`) or "oder" (`or`). */
  list(names: string[], kind: 'and' | 'or'): string;
  /** An already translated text for a key (nested sentence parts such as the link window). */
  t(key: string, params?: Record<string, string | number>): string;
}

export interface RuleSentence {
  key: string;
  params: Record<string, string | number>;
}

/** One line of the summary card; `rule` names the rule for test ids and icons. */
export interface RuleLine extends RuleSentence {
  rule: 'GAP' | 'GROUP_GAP' | 'LINKED_STORE' | 'EXPERIENCE' | 'CLEANING' | 'MANUAL_DATE';
  icon: string;
}

/** What makes a blocked pickup possible, when the page can help. */
export type BlockAction =
  | { kind: 'jump'; at: string; label: RuleSentence }
  | { kind: 'cleaning'; fspId: string; label: RuleSentence }
  | { kind: 'linked'; store: StoreRef; label: RuleSentence };

/** One reason of the dialog: rule chip, the sentence with the person's data, the fix and an action. */
export interface BlockReason {
  rule: PolicyRule;
  chip: string;
  icon: string;
  text: RuleSentence;
  fix?: RuleSentence;
  action?: BlockAction;
}

const names = (stores: StoreRef[] | undefined): string[] => (stores ?? []).map((store) => store.name);

/** "am selben Tag" / "am selben Tag oder bis 3 Tage danach" / … as a translated part. */
export function linkWindow(link: Pick<StoreRuleLink, 'windowMode' | 'before' | 'after'>, fmt: RuleFormat): string {
  const before = link.windowMode === LinkedStoreWindowMode.LastDays ? Math.max(0, link.before) : 0;
  const after = link.windowMode === LinkedStoreWindowMode.LastDays ? Math.max(0, link.after) : 0;
  if (!before && !after) return fmt.t('pickups.rules.window.sameDay');
  if (!before) return fmt.t('pickups.rules.window.after', { days: after });
  if (!after) return fmt.t('pickups.rules.window.before', { days: before });
  return fmt.t('pickups.rules.window.range', { before, after });
}

/** The active rules as lines; empty while the rules are off (the card is hidden then). */
export function summaryLines(summary: StoreRuleSummary | null | undefined, fmt: RuleFormat): RuleLine[] {
  if (!summary?.enabled) return [];
  const lines: RuleLine[] = [];
  if (summary.gapDays != null && summary.gapDays > 0) {
    lines.push({ rule: 'GAP', icon: 'pi-clock', key: 'pickups.rules.gap', params: { days: summary.gapDays } });
  }
  if (summary.group && summary.group.days > 0 && summary.group.stores.length > 1) {
    lines.push({
      rule: 'GROUP_GAP',
      icon: 'pi-clock',
      key: 'pickups.rules.groupGap',
      params: { stores: fmt.list(names(summary.group.stores), 'and'), days: summary.group.days },
    });
  }
  for (const link of summary.links) {
    if (!link.stores.length) continue;
    lines.push({
      rule: 'LINKED_STORE',
      icon: 'pi-link',
      key: 'pickups.rules.linked',
      params: { stores: fmt.list(names(link.stores), 'or'), window: linkWindow(link, fmt) },
    });
  }
  if (summary.experience) {
    lines.push({ rule: 'EXPERIENCE', icon: 'pi-star', key: 'pickups.rules.experience', params: {} });
  }
  if (summary.cleaningMonths != null) {
    lines.push({ rule: 'CLEANING', icon: 'pi-sparkles', key: 'pickups.rules.cleaning', params: { months: summary.cleaningMonths } });
  }
  const dates = summary.manualDates.map((date) => fmt.date(date));
  if (summary.holidays || dates.length) {
    const key = summary.holidays && dates.length ? 'pickups.rules.manual' : summary.holidays ? 'pickups.rules.holidays' : 'pickups.rules.manualDates';
    lines.push({ rule: 'MANUAL_DATE', icon: 'pi-calendar', key, params: dates.length ? { dates: fmt.list(dates, 'and') } : {} });
  }
  return lines;
}

/** How many rules the summary card counts (one per line; the own and the group gap are one rule). */
export function ruleCount(lines: RuleLine[]): number {
  return lines.filter((line) => line.rule !== 'GROUP_GAP' || !lines.some((other) => other.rule === 'GAP')).length;
}

const CHIPS: Partial<Record<PolicyRule, { chip: string; icon: string }>> = {
  [PolicyRule.Gap]: { chip: 'pickups.blocked.rule.GAP', icon: 'pi-clock' },
  [PolicyRule.LinkedStore]: { chip: 'pickups.blocked.rule.LINKED_STORE', icon: 'pi-link' },
  [PolicyRule.Experience]: { chip: 'pickups.blocked.rule.EXPERIENCE', icon: 'pi-star' },
  [PolicyRule.CleaningRequired]: { chip: 'pickups.blocked.rule.CLEANING', icon: 'pi-sparkles' },
  [PolicyRule.NoCleaningFairteiler]: { chip: 'pickups.blocked.rule.CLEANING', icon: 'pi-sparkles' },
};

/** The dialog block of one rule; `now` decides between "your last pickup" and "your pickup on". */
export function blockReason(block: PolicyBlock, fmt: RuleFormat, now: Date): BlockReason {
  const chip = CHIPS[block.rule] ?? { chip: 'pickups.blocked.rule.other', icon: 'pi-lock' };
  const p = block.params;
  const base = { rule: block.rule, chip: chip.chip, icon: chip.icon };
  switch (block.rule) {
    case PolicyRule.Gap: {
      const last = p.lastPickupAt ? fmt.day(p.lastPickupAt) : '';
      const other = p.stores?.[0];
      const past = !!p.lastPickupAt && new Date(p.lastPickupAt).getTime() < now.getTime();
      const text: RuleSentence = other
        ? { key: 'pickups.blocked.GAP.group', params: { gap: p.gapDays ?? 0, store: other.name, last } }
        : { key: past ? 'pickups.blocked.GAP' : 'pickups.blocked.GAP.upcoming', params: { gap: p.gapDays ?? 0, last } };
      if (!p.nextPossibleAt) return { ...base, text };
      const next = fmt.day(p.nextPossibleAt);
      return {
        ...base,
        text,
        fix: { key: 'pickups.blocked.GAP.fix', params: { next } },
        action: { kind: 'jump', at: p.nextPossibleAt, label: { key: 'pickups.blocked.GAP.action', params: { next } } },
      };
    }
    case PolicyRule.LinkedStore: {
      const stores = p.stores ?? [];
      const window = linkWindow({ windowMode: p.windowMode ?? LinkedStoreWindowMode.SameDay, before: p.before ?? 0, after: p.after ?? 0 }, fmt);
      const text: RuleSentence = {
        key: 'pickups.blocked.LINKED_STORE',
        params: {
          stores: fmt.list(names(stores), 'or'),
          window,
          from: p.from ? fmt.day(p.from) : '',
          to: p.to ? fmt.day(p.to) : '',
        },
      };
      // decision 165 S3: the linked step of the sign-up dialog books the linked pickup together with this one
      return stores.length
        ? {
            ...base,
            text,
            fix: { key: 'pickups.blocked.LINKED_STORE.fix', params: {} },
            action: {
              kind: 'linked',
              store: stores[0],
              label: { key: 'pickups.blocked.LINKED_STORE.action', params: { store: fmt.list(names(stores), 'or') } },
            },
          }
        : { ...base, text };
    }
    case PolicyRule.Experience:
      return {
        ...base,
        text: { key: 'pickups.blocked.EXPERIENCE', params: {} },
        fix: { key: 'pickups.blocked.EXPERIENCE.fix', params: {} },
      };
    case PolicyRule.CleaningRequired: {
      const required = p.monthsRequired ?? 0;
      const text: RuleSentence =
        p.monthsAgo == null
          ? { key: 'pickups.blocked.CLEANING_REQUIRED.never', params: { required } }
          : { key: 'pickups.blocked.CLEANING_REQUIRED', params: { ago: p.monthsAgo, required } };
      return p.fspId
        ? { ...base, text, action: { kind: 'cleaning', fspId: p.fspId, label: { key: 'pickups.blocked.CLEANING_REQUIRED.action', params: {} } } }
        : { ...base, text };
    }
    case PolicyRule.NoCleaningFairteiler:
      return { ...base, text: { key: 'pickups.blocked.NO_CLEANING_FAIRTEILER', params: {} } };
    default:
      return { ...base, text: { key: 'pickups.blocked.other', params: {} } };
  }
}

// --- linked-store step of the sign-up dialog (decision 165 S3) ------------------------------------------------------

/** Only missing linked-store pickups keep the person out: the sign-up dialog books them in the same step. */
export function onlyLinkMissing(e: PickupEligibility | null | undefined): boolean {
  const blocks = e?.ruleBlocks ?? [];
  return (
    !!e &&
    !e.eligible &&
    e.reasons.every((reason) => reason === PickupEligibilityReason.AutomationRule) &&
    blocks.length > 0 &&
    blocks.every((block) => block.rule === PolicyRule.LinkedStore)
  );
}

/** Rule blocks that keep a free place locked: a missing linked pickup alone does not (the dialog books it). */
export function lockingBlocks(blocks: PolicyBlock[] | undefined): PolicyBlock[] {
  const all = blocks ?? [];
  return all.some((block) => block.rule !== PolicyRule.LinkedStore) ? all : [];
}

const storeKey = (stores: StoreRef[] | undefined) =>
  (stores ?? [])
    .map((store) => store.id)
    .sort()
    .join(',');

/**
 * The rules that need a choice, one step each: those without a satisfying pickup that block the sign-up (`blocks`), or
 * every unsatisfied one when repairing a sign-up whose linked pickup was signed out.
 */
export function linkedSteps(rules: LinkedOptionRule[], blocks: PolicyBlock[] | undefined, repair: boolean): LinkedOptionRule[] {
  const open = rules.filter((rule) => !rule.satisfiedBy);
  if (repair) return open;
  const blocked = new Set((blocks ?? []).filter((b) => b.rule === PolicyRule.LinkedStore).map((b) => storeKey(b.params.stores)));
  const matching = open.filter((rule) => blocked.has(storeKey(rule.stores)));
  return matching.length || !blocked.size ? matching : open;
}

/** A linked pickup the person can take: no eligibility reason and no rule of that store against it. */
export function bookable(option: LinkedOption): boolean {
  return !option.reasons.length && !option.ruleBlocks.length;
}

/** The {@link RuleFormat} of the UI: `t` translates, days in the store's time zone, lists in the UI language. */
export function uiRuleFormat(
  t: (key: string, params?: Record<string, string | number>) => string,
  formatDate: (value: string, style: 'weekday' | 'date', timeZone: string) => string,
  locale: string,
  timeZone: string,
): RuleFormat {
  const list = (names: string[], kind: 'and' | 'or') => {
    try {
      return new Intl.ListFormat(locale, { type: kind === 'and' ? 'conjunction' : 'disjunction' }).format(names);
    } catch {
      return names.join(', ');
    }
  };
  return {
    t,
    list,
    day: (iso) => formatDate(iso, 'weekday', timeZone),
    // a calendar date: noon UTC keeps the day in every time zone
    date: (localDate) => formatDate(`${localDate}T12:00:00Z`, 'date', 'UTC'),
  };
}
