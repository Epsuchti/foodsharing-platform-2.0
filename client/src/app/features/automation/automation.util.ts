import {
  AdvertChannel,
  LinkedStoreRule,
  LinkedStoreWindowMode,
  OpenSlotAdvert,
  PickupPolicy,
  RequestPolicy,
  SignoutAlertChannel,
  SignoutAlertSetting,
} from '../../api';

/** Placeholders of open-slot advert messages (server `AdvertTemplates.VARIABLES`). */
export const ADVERT_PLACEHOLDERS = ['date', 'dateDe', 'weekday', 'time', 'datetime', 'datetimeDe', 'storeName', 'freeSlots', 'link'] as const;

/** Placeholders of linked-store messages (server `PickupPolicyEvaluator`). */
export const LINK_PLACEHOLDERS = ['name', 'storeName', 'linkedStoreName', 'pickupDate', 'graceMinutes', 'requirement'] as const;

/** Badge tone of an audit status / decision. */
export type Tone = 'ok' | 'bad' | 'dry' | 'neutral' | 'error';

export function decisionTone(status: string): Tone {
  if (status === 'FAILED') return 'error';
  if (status.startsWith('WOULD_') || status === 'DRY_RUN_SUMMARY') return 'dry';
  if (['CONFIRMED', 'APPROVED', 'SENT', 'REPLACED', 'REJOINED', 'ADDED'].includes(status)) return 'ok';
  if (['DECLINED', 'NOT_REPLACED'].includes(status)) return 'bad';
  if (status.startsWith('LATE_CANCELLATION') || status === 'OPEN') return 'bad';
  return 'neutral';
}

/** Replaces `{{key}}` with the given values; unknown placeholders stay visible. */
export function renderTemplate(template: string, values: Record<string, string | number>): string {
  return (template ?? '').replace(/\{\{\s*([a-zA-Z]+)\s*\}\}/g, (match, key: string) =>
    key in values ? String(values[key]) : match,
  );
}

/** Placeholders used in `template` that are not in `allowed` (server answers INVALID_TEMPLATE_VARIABLE). */
export function unknownPlaceholders(template: string, allowed: readonly string[]): string[] {
  const found = [...(template ?? '').matchAll(/\{\{\s*([a-zA-Z]+)\s*\}\}/g)].map((m) => m[1]);
  return [...new Set(found.filter((key) => !allowed.includes(key)))];
}

/** Inserts `{{key}}` at the end of `text` (with a separating space). */
export function appendPlaceholder(text: string, key: string): string {
  const base = text ?? '';
  return `${base}${base && !/\s$/.test(base) ? ' ' : ''}{{${key}}}`;
}

/** Values for the advert preview of the next pickup, formatted in `locale` / `timeZone`. */
export function advertValues(
  pickupAt: string,
  freeSlots: number,
  storeName: string,
  link: string,
  locale: string,
  timeZone: string,
): Record<string, string | number> {
  const at = new Date(pickupAt);
  const fmt = (lc: string, opts: Intl.DateTimeFormatOptions) => new Intl.DateTimeFormat(lc, { timeZone, ...opts }).format(at);
  const date = fmt(locale, { day: '2-digit', month: '2-digit', year: 'numeric' });
  const dateDe = fmt('de-CH', { day: '2-digit', month: '2-digit', year: 'numeric' });
  const time = fmt('de-CH', { hour: '2-digit', minute: '2-digit' });
  return {
    date,
    dateDe,
    weekday: fmt(locale, { weekday: 'long' }),
    time,
    datetime: `${date} ${time}`,
    datetimeDe: `${dateDe} ${time}`,
    storeName,
    freeSlots,
    link,
  };
}

export function emptyPickupPolicy(): PickupPolicy {
  return {
    enabled: false,
    gapRuleEnabled: false,
    minimumGapDays: 2,
    cleaningRuleEnabled: false,
    experienceRuleEnabled: false,
    publicHolidayRuleEnabled: false,
    linkedStores: [],
    excludedDates: [],
    gapStoreIds: [],
  };
}

export function emptyLinkRule(): LinkedStoreRule {
  return {
    storeIds: [],
    bidirectional: false,
    windowMode: LinkedStoreWindowMode.SameDay,
    windowDaysBefore: 0,
    windowDaysAfter: 0,
    // decision 165 S3 (Q4): "Frist nach Austragung" in the linked store, 2 hours by default
    graceMinutes: 120,
    message: '',
  };
}

export function emptyRequestPolicy(): RequestPolicy {
  return { enabled: false, distanceRuleEnabled: true, maximumDistanceKm: 5 };
}

export function emptyAdvert(advertNumber: number): OpenSlotAdvert {
  return {
    advertNumber,
    enabled: false,
    triggerHoursBefore: 24,
    channels: [AdvertChannel.Push],
    storeMessages: [],
    pushMessages: [],
    telegramMessages: [],
    sendLateCancellationMessage: false,
    lateCancellationMessage: '',
    lateCancellationMaxHoursBefore: 12,
    resendLatestAdvertAfterLateCancellation: false,
  };
}

export function emptySignoutAlert(): SignoutAlertSetting {
  return { enabled: false, cutoffEnabled: true, maximumHoursBeforePickup: 72, channels: [SignoutAlertChannel.Bell], telegramChatIds: [] };
}

/** `yyyy-MM-dd` of a local Date (date pickers work in local dates). */
export function isoDate(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/** Local Date of `yyyy-MM-dd`. */
export function fromIsoDate(value: string): Date {
  const [y, m, d] = value.split('-').map(Number);
  return new Date(y, m - 1, d);
}

export function addDays(iso: string, days: number): string {
  const date = fromIsoDate(iso);
  date.setDate(date.getDate() + days);
  return isoDate(date);
}

export function toggle<T>(list: readonly T[], value: T, on: boolean): T[] {
  const without = list.filter((item) => item !== value);
  return on ? [...without, value] : without;
}

/** Other stores per gap list (server: GAP_LIST_TOO_LARGE at 10 with the store itself, decision 151). */
export const MAX_GAP_STORES = 9;

export type GapDayResult = 'PICKUP' | 'BLOCKED' | 'OK';

export interface GapPreviewDay {
  /** `yyyy-MM-dd` */
  date: string;
  offset: number;
  result: GapDayResult;
}

export interface GapPreview {
  days: GapPreviewDay[];
  /** First day a second pickup is confirmed after the example pickup (pickup + gap + 1). */
  next: string;
  /** Free days between the example pickup and the last blocked day after it (for the message). */
  actual: number;
}

/** Free calendar days between two pickups `offset` days apart (server `calendarGapDays`). */
export function calendarGapDays(offset: number): number {
  return Math.max(0, Math.abs(offset) - 1);
}

/**
 * The gap rule around an example pickup on `pickup` (`yyyy-MM-dd`): 5 days before and after; a second sign-up on day
 * `d` is BLOCKED when the free days in between are fewer than `gap` (server `PickupPolicyEvaluator`).
 */
export function gapPreview(pickup: string, gap: number): GapPreview {
  const days: GapPreviewDay[] = [];
  for (let offset = -5; offset <= 5; offset++) {
    days.push({
      date: addDays(pickup, offset),
      offset,
      result: offset === 0 ? 'PICKUP' : calendarGapDays(offset) < gap ? 'BLOCKED' : 'OK',
    });
  }
  return { days, next: addDays(pickup, gap + 1), actual: calendarGapDays(Math.max(1, gap)) };
}

/** Today (`yyyy-MM-dd`) in the time zone (the store's region). */
export function todayIn(timeZone: string | undefined, now: Date = new Date()): string {
  try {
    return new Intl.DateTimeFormat('en-CA', { timeZone, year: 'numeric', month: '2-digit', day: '2-digit' }).format(now);
  } catch {
    return isoDate(now);
  }
}

/** A linked-store rule as seen from its owner (own or mirrored); `linkIndex`: its card on this page, if any. */
export interface GapLinkRule {
  owner: string;
  storeIds: string[];
  windowMode: LinkedStoreWindowMode;
  windowDaysBefore: number;
  windowDaysAfter: number;
  linkIndex?: number;
}

export interface GapLinkFinding {
  rule: GapLinkRule;
  /** The other store of the rule, as seen from this store. */
  store: string;
  blocking: boolean;
}

/** Largest gap a rule allows between two matched pickups: same day 0, otherwise max(before, after) − 1 (D7). */
export function gapLinkReach(rule: Pick<GapLinkRule, 'windowMode' | 'windowDaysBefore' | 'windowDaysAfter'>): number {
  return rule.windowMode === LinkedStoreWindowMode.LastDays ? Math.max(0, Math.max(rule.windowDaysBefore, rule.windowDaysAfter) - 1) : 0;
}

/**
 * Link rules that reach less far than the gap and touch the list (decision 151, D7): blocking (save disabled, server
 * GAP_LINK_CONFLICT) when the owner and every store of the rule are listed, otherwise a warning. A rule and its mirror
 * count once; rules earlier in `rules` win (pass this store's own rules first).
 */
export function gapLinkFindings(self: string, members: readonly string[], gap: number, rules: readonly GapLinkRule[]): GapLinkFinding[] {
  if (!members.length) return [];
  const inList = new Set([self, ...members]);
  const seen = new Set<string>();
  const findings: GapLinkFinding[] = [];
  for (const rule of rules) {
    if (!rule.storeIds.length) continue;
    const key = `${[rule.owner, ...rule.storeIds].sort().join()}|${rule.windowMode}|${gapLinkReach(rule)}`;
    if (seen.has(key)) continue;
    seen.add(key);
    if (gapLinkReach(rule) >= gap) continue;
    if (!inList.has(rule.owner) && !rule.storeIds.some((id) => inList.has(id))) continue;
    const blocking = inList.has(rule.owner) && rule.storeIds.every((id) => inList.has(id));
    const store = rule.owner === self ? (rule.storeIds.find((id) => inList.has(id)) ?? rule.storeIds[0]) : rule.owner;
    findings.push({ rule, store, blocking });
  }
  return findings;
}

/** "A", "A und B", "A, B und C". */
export function joinAll(names: readonly string[], and: string): string {
  if (names.length <= 1) return names.join('');
  return `${names.slice(0, -1).join(', ')} ${and} ${names[names.length - 1]}`;
}

/** Deep copy for editable drafts. */
export function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T;
}

/** Daily series for the line chart: one point per day between from and to (missing days = 0). */
export function fillDays(points: readonly { day: string; value: number }[], from: string, to: string): { day: string; value: number }[] {
  const byDay = new Map(points.map((p) => [p.day, p.value]));
  const out: { day: string; value: number }[] = [];
  for (let day = from; day <= to; day = addDays(day, 1)) {
    out.push({ day, value: byDay.get(day) ?? 0 });
    if (out.length > 800) break;
  }
  return out;
}
