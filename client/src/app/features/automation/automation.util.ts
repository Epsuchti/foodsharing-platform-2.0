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
    dryRun: true,
    gapRuleEnabled: false,
    minimumGapDays: 2,
    cleaningRuleEnabled: false,
    experienceRuleEnabled: false,
    publicHolidayRuleEnabled: false,
    linkedStores: [],
    excludedDates: [],
  };
}

export function emptyLinkRule(): LinkedStoreRule {
  return {
    storeIds: [],
    bidirectional: false,
    windowMode: LinkedStoreWindowMode.SameDay,
    windowDaysBefore: 0,
    windowDaysAfter: 0,
    graceMinutes: 30,
    message: '',
  };
}

export function emptyRequestPolicy(): RequestPolicy {
  return { enabled: false, dryRun: true, distanceRuleEnabled: true, maximumDistanceKm: 5 };
}

export function emptyAdvert(advertNumber: number): OpenSlotAdvert {
  return {
    advertNumber,
    enabled: false,
    dryRun: true,
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
