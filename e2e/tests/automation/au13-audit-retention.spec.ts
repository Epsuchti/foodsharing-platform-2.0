import { expect, seedRegion, test } from '../../fixtures';
import { S06 } from './_automation';

/**
 * AU13: audit retention (§6.3 `audit-retention`). Automation audits older than 7 days and cancellations/extra slots
 * older than 180 days are archived into anonymous daily counts, then deleted; the metric series stay the same.
 */
test.use({ seedReset: 'test' });

const ZH = seedRegion('zuerich-stadt').id;
const RANGE = 'from=2026-07-01&to=2026-11-30';

type Series = { key: string; points: { day: string; value: number }[] }[];

function totals(series: Series): Record<string, number> {
  return Object.fromEntries(series.map((s) => [s.key, s.points.reduce((n, p) => n + p.value, 0)]));
}

test('AU13 audit retention archives old audits as daily counts and deletes them @AU13', async ({ as, apiOf, clock, notifications }) => {
  const bot = apiOf(await as('bot'));
  const bieb = apiOf(await as('bieb'));
  const before = totals(await bot.getJson<Series>(`/api/regions/${ZH}/automation/metrics?${RANGE}`));
  expect(Object.keys(before).length).toBeGreaterThan(0);
  const auditsBefore = await bieb.getJson<{ items: unknown[] }>(`/api/stores/${S06.id}/automation/audits?${RANGE}&size=100`);
  const cancellationsBefore = await bieb.getJson<unknown[]>(`/api/stores/${S06.id}/automation/signout-alert/cancellations?${RANGE}`);
  expect(auditsBefore.items.length + cancellationsBefore.length).toBeGreaterThan(0);

  // more than 180 days later every seeded audit and cancellation is past its retention
  await clock.fixServer('2027-06-01T09:00:00Z');
  await notifications.runJob('audit-retention');

  expect((await bieb.getJson<{ items: unknown[] }>(`/api/stores/${S06.id}/automation/audits?${RANGE}&size=100`)).items).toHaveLength(0);
  expect(await bieb.getJson<unknown[]>(`/api/stores/${S06.id}/automation/signout-alert/cancellations?${RANGE}`)).toHaveLength(0);
  // the metrics are unchanged: archive + live rows count every audit exactly once
  expect(totals(await bot.getJson<Series>(`/api/regions/${ZH}/automation/metrics?${RANGE}`))).toEqual(before);

  // a second run archives nothing twice
  await notifications.runJob('audit-retention');
  expect(totals(await bot.getJson<Series>(`/api/regions/${ZH}/automation/metrics?${RANGE}`))).toEqual(before);
  await clock.fixServer();
});
