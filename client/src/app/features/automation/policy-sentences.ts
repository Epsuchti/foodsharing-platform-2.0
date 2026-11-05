import { LinkedStoreRule, LinkedStoreWindowMode, StoreRef, StoreRuleSummary } from '../../api';
import { RuleFormat, summaryLines } from '../pickups/rule-sentences';

/**
 * The member sentences of the rule cards (decision 167 S4, proposal §3.1): the lines members read on the Pickups tab
 * (rule-sentences.ts), built for one rule of the manager's draft. Pure: `fmt` formats and translates.
 */
export function ruleSentence(rules: Partial<StoreRuleSummary>, fmt: RuleFormat): string | undefined {
  const summary: StoreRuleSummary = { enabled: true, links: [], experience: false, manualDates: [], holidays: false, ...rules };
  const lines = summaryLines(summary, fmt);
  return lines.length ? lines.map((line) => fmt.t(line.key, line.params)).join(' ') : undefined;
}

/** Own gap (when on) and the group gap of the gap list (decision 152); `group` lists every store, this one first. */
export function gapSentence(
  ownGapDays: number | undefined,
  group: { days: number; stores: StoreRef[] } | undefined,
  fmt: RuleFormat,
): string | undefined {
  return ruleSentence({ gapDays: ownGapDays, group }, fmt);
}

/** One linked-store rule; undefined without stores. */
export function linkSentence(
  rule: Pick<LinkedStoreRule, 'windowMode' | 'windowDaysBefore' | 'windowDaysAfter'>,
  stores: StoreRef[],
  fmt: RuleFormat,
): string | undefined {
  if (!stores.length) return undefined;
  const lastDays = rule.windowMode === LinkedStoreWindowMode.LastDays;
  return ruleSentence(
    { links: [{ stores, windowMode: rule.windowMode, before: lastDays ? rule.windowDaysBefore : 0, after: lastDays ? rule.windowDaysAfter : 0 }] },
    fmt,
  );
}

/**
 * Active rules of a saved policy for the status strip: the gap (own or group) counts once, each linked rule (own or
 * mirrored), experience, cleaning, holidays, and excluded dates once; 0 while the rules are off.
 */
export function activeRuleCount(policy: {
  enabled: boolean;
  gapRuleEnabled: boolean;
  minimumGapDays: number;
  gapStoreIds?: string[];
  experienceRuleEnabled: boolean;
  cleaningRuleEnabled: boolean;
  publicHolidayRuleEnabled: boolean;
  linkedStores: unknown[];
  excludedDates: string[];
} | undefined): number {
  if (!policy?.enabled) return 0;
  const gap = (policy.gapRuleEnabled && policy.minimumGapDays > 0) || !!policy.gapStoreIds?.length;
  return (
    Number(gap) +
    Number(policy.experienceRuleEnabled) +
    Number(policy.cleaningRuleEnabled) +
    Number(policy.publicHolidayRuleEnabled) +
    policy.linkedStores.length +
    Number(policy.excludedDates.length > 0)
  );
}
