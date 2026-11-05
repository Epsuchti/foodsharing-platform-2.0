import { MetricKey } from '../../api';
import { addDays, fromIsoDate } from './automation.util';

/** Display group of the metrics page (AU9). */
export type MetricGroupId = 'PICKUP' | 'DECLINE_REASONS' | 'SKIP_REASONS' | 'REQUESTS' | 'CANCELLATIONS' | 'ADVERTS';

export interface MetricGroup {
  id: MetricGroupId;
  keys: readonly MetricKey[];
}

/** Legacy dry-run keys (decision 167): still in the contract for old rows, never shown. */
export function isLegacyMetricKey(key: string): boolean {
  return key.includes('WOULD_');
}

const values = (Object.values(MetricKey) as MetricKey[]).filter((key) => !isLegacyMetricKey(key));
const byPrefix = (...prefixes: string[]) => values.filter((key) => prefixes.some((prefix) => key.startsWith(prefix)));

/**
 * The metric groups in display order; every MetricKey except the legacy WOULD_* keys is in exactly one group (contract
 * order inside a group).
 */
export const METRIC_GROUPS: readonly MetricGroup[] = [
  { id: 'PICKUP', keys: byPrefix('SLOT_APPROVAL.') },
  { id: 'DECLINE_REASONS', keys: byPrefix('SLOT_DECLINE_RULE.') },
  { id: 'SKIP_REASONS', keys: byPrefix('SLOT_SKIP_RULE.') },
  { id: 'REQUESTS', keys: byPrefix('REQUEST.') },
  { id: 'CANCELLATIONS', keys: byPrefix('CANCELLATION.') },
  { id: 'ADVERTS', keys: byPrefix('ADVERT.', 'EXTRA_SLOT.', 'INTRO_PICKUP.') },
];

/** i18n key of a metric label: `automation.metric.<KEY with every '.' replaced by '_'>` (checked by check-i18n.mjs). */
export function metricLabelKey(key: string): string {
  return `automation.metric.${key.replaceAll('.', '_')}`;
}

export interface MetricPoint {
  day: string;
  value: number;
}

/**
 * Weekly sums of daily points between `from` and `to` (inclusive). Weeks start on Monday; the first and the last week
 * may be partial. Each bucket is labelled with its first day inside the range (`from` for the first one).
 */
export function bucketWeeks(points: readonly MetricPoint[], from: string, to: string): MetricPoint[] {
  const byDay = new Map<string, number>();
  for (const p of points) {
    byDay.set(p.day, (byDay.get(p.day) ?? 0) + p.value);
  }
  const out: MetricPoint[] = [];
  let current: MetricPoint | undefined;
  for (let day = from, n = 0; day <= to && n <= 800; day = addDays(day, 1), n++) {
    if (!current || fromIsoDate(day).getDay() === 1) {
      current = { day, value: 0 };
      out.push(current);
    }
    current.value += byDay.get(day) ?? 0;
  }
  return out;
}

/** Sum of a series' points. */
export function seriesTotal(series: { points: readonly { value: number }[] }): number {
  return series.points.reduce((sum, p) => sum + p.value, 0);
}

/** The `n` series with the largest totals, largest first (stable for equal totals). */
export function topSeries<T extends { points: readonly { value: number }[] }>(series: readonly T[], n: number): T[] {
  return series
    .map((s, index) => ({ s, index, total: seriesTotal(s) }))
    .sort((a, b) => b.total - a.total || a.index - b.index)
    .slice(0, n)
    .map((entry) => entry.s);
}
