import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach, beforeAll } from 'vitest';

import { MetricKey, MetricSeries } from '../../api';
import { provideDataStateTesting } from '../../core/testing/data-state-testing';
import { AUTOMATION_ROUTES } from './automation.routes';
import { addDays, isoDate } from './automation.util';
import de from './i18n/de.json';
import { bucketWeeks, METRIC_GROUPS, metricLabelKey, topSeries } from './metric-groups';

const R = '00000002-0001-4000-8000-000000000003';

describe('metric groups (AU9)', () => {
  it('puts every MetricKey into exactly one group', () => {
    for (const key of Object.values(MetricKey)) {
      expect(METRIC_GROUPS.filter((g) => g.keys.includes(key)).map((g) => g.id), key).toHaveLength(1);
    }
    expect(METRIC_GROUPS.map((g) => g.id)).toEqual(['PICKUP', 'DECLINE_REASONS', 'SKIP_REASONS', 'REQUESTS', 'CANCELLATIONS', 'ADVERTS']);
    expect(METRIC_GROUPS.find((g) => g.id === 'ADVERTS')?.keys).toEqual([
      MetricKey.AdvertSent,
      MetricKey.AdvertFilledAfterAdvert,
      MetricKey.ExtraSlotAdded,
      MetricKey.IntroPickupDone,
    ]);
  });

  it('metricLabelKey replaces every dot', () => {
    expect(metricLabelKey('SLOT_DECLINE_RULE.GAP')).toBe('automation.metric.SLOT_DECLINE_RULE_GAP');
    expect(metricLabelKey('A.B.C')).toBe('automation.metric.A_B_C');
  });

  it('bucketWeeks sums per week starting on Monday, with partial first and last weeks', () => {
    // 2026-10-01 is a Thursday, 2026-10-14 a Wednesday
    const points = [
      { day: '2026-10-01', value: 1 },
      { day: '2026-10-04', value: 2 }, // Sunday: still the first (partial) week
      { day: '2026-10-05', value: 3 }, // Monday: second week
      { day: '2026-10-11', value: 4 }, // Sunday
      { day: '2026-10-12', value: 5 }, // Monday: last (partial) week
      { day: '2026-10-14', value: 6 },
    ];
    expect(bucketWeeks(points, '2026-10-01', '2026-10-14')).toEqual([
      { day: '2026-10-01', value: 3 },
      { day: '2026-10-05', value: 7 },
      { day: '2026-10-12', value: 11 },
    ]);
    expect(bucketWeeks([], '2026-10-05', '2026-10-05')).toEqual([{ day: '2026-10-05', value: 0 }]);
  });

  it('topSeries returns at most n series, largest total first', () => {
    const s = (key: string, ...values: number[]) => ({ key, points: values.map((value, i) => ({ day: `d${i}`, value })) });
    const all = [s('a', 1), s('b', 5, 5), s('c', 3), s('d', 0), s('e', 7), s('f', 2), s('g', 3)];
    expect(topSeries(all, 5).map((x) => x.key)).toEqual(['b', 'e', 'c', 'g', 'f']);
    expect(topSeries(all.slice(0, 2), 5).map((x) => x.key)).toEqual(['b', 'a']);
  });
});

describe('region metrics section (AU9)', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  const all = (selector: string) => [...harness.routeNativeElement!.querySelectorAll(selector)];
  const q = (selector: string) => harness.routeNativeElement!.querySelector(selector);
  const settle = () => harness.fixture.whenStable();

  beforeAll(async () => {
    await Promise.all([import('./pages/region-automation-page.component'), import('./pages/region-sections')]);
  });

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting({ translations: de as Record<string, string> }),
        provideRouter(AUTOMATION_ROUTES.map((route) => ({ ...route, canActivate: [] }))),
        provideOptimus(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => http.verify({ ignoreCancelled: true }));

  const today = isoDate(new Date());
  const day = (offset: number) => addDays(today, -offset);
  const series = (key: MetricKey, ...values: number[]): MetricSeries => ({
    key,
    points: values.map((value, i) => ({ day: day(i * 7), value })),
  });

  async function open(data: MetricSeries[]): Promise<void> {
    await harness.navigateByUrl(`/regions/${R}/automation/metrics`);
    http.expectOne(`/api/regions/${R}`).flush({ id: R, name: 'Zürich' });
    await settle();
    http.expectOne((r) => r.url === `/api/regions/${R}/automation/metrics`).flush(data);
    await settle();
  }

  it('shows the PICKUP group by default, hides zero totals, sums 90 days per week, at most 5 lines, hidden table', async () => {
    await open([
      series(MetricKey.SlotApprovalConfirmed, 4, 2),
      series(MetricKey.SlotApprovalDeclined, 1),
      series(MetricKey.SlotApprovalSkipped, 0, 0),
      series(MetricKey.SlotApprovalWouldConfirm, 3),
      series(MetricKey.SlotApprovalWouldDecline, 2),
      series(MetricKey.SlotApprovalFailed, 1),
      series(MetricKey.SlotDeclineRuleGap, 9),
      series(MetricKey.AdvertSent, 5),
    ]);

    const rows = all('[data-testid="automation.metrics.totals.row"]');
    // default group PICKUP; SKIPPED (total 0) is hidden; ranked by total
    expect(rows.map((r) => r.getAttribute('data-key'))).toEqual([
      'SLOT_APPROVAL.CONFIRMED',
      'SLOT_APPROVAL.WOULD_CONFIRM',
      'SLOT_APPROVAL.WOULD_DECLINE',
      'SLOT_APPROVAL.DECLINED',
      'SLOT_APPROVAL.FAILED',
    ]);
    expect(rows[0].getAttribute('data-total')).toBe('6');

    const legend = all('[data-testid="automation.metrics.chart.legend"]');
    expect(legend.length).toBeLessThanOrEqual(5);
    expect(legend.map((l) => l.getAttribute('data-key'))).not.toContain('SLOT_DECLINE_RULE.GAP');

    // 90 days (from = today - 89) are summed per week: 13 or 14 buckets depending on the weekday
    const table = q('[data-testid="automation.metrics.chart.table"]') as HTMLTableElement;
    expect(table).toBeTruthy();
    expect(table.classList).toContain('fs-visually-hidden');
    const weeks = table.querySelectorAll('tbody tr').length;
    expect(weeks).toBeGreaterThanOrEqual(13);
    expect(weeks).toBeLessThanOrEqual(14);
    expect(q('[data-testid="automation.metrics.chart.range"]')?.textContent).toContain(de['automation.metrics.perWeek']);

    // no raw key is visible anywhere in the section
    const text = q('[data-testid="automation.metrics.state"]')?.textContent ?? '';
    expect(text).not.toMatch(/\b[A-Z_]+\.[A-Z_]+\b/);
    expect(rows[0].textContent).toContain(de['automation.metric.SLOT_APPROVAL_CONFIRMED']);

    // switching the group shows only that group's series
    (q('[data-testid="automation.metrics.group.DECLINE_REASONS"]') as HTMLElement).click();
    await settle();
    expect(all('[data-testid="automation.metrics.totals.row"]').map((r) => r.getAttribute('data-key'))).toEqual(['SLOT_DECLINE_RULE.GAP']);
  });

  it('a group without values shows the inline hint, not the page empty state', async () => {
    await open([series(MetricKey.AdvertSent, 5)]);
    expect(q('[data-testid="automation.metrics.state"]')?.getAttribute('data-state')).toBe('success');
    expect(q('[data-testid="automation.metrics.groupEmpty"]')).toBeTruthy();
    expect(all('[data-testid="automation.metrics.totals.row"]')).toHaveLength(0);
  });
});
