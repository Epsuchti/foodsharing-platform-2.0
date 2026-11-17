import fs from 'node:fs';
import path from 'node:path';
import type { Page } from '@playwright/test';
import { type ApiClient, SEED_NOW, acceptPendingConsents, expectDataState, pickOption } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { acceptConfirm } from '../../fixtures/flows/auth';
import { expectFitsScreen, expectTableFits } from '../../fixtures/flows/shell';
import { Walk, openNewestBell } from '../../fixtures/persona';

/**
 * ORGA (orga@local) on operations duty: checks the integrations (every channel in log mode, probe and test message),
 * follows the INTEGRATION_DOWN bell of a geocoding outage to its open breaker and closes it with a probe (AU12), runs
 * the statistics aggregation from the job list and checks the public statistics page (D6.3, C3), looks through the
 * mail outbox (N6), and tidies the uploads: deletes an unused one, and after two days the nightly cleanup, run from
 * the job list, removes the forgotten one (C10). Rules behind it: CircuitBreakerTest, JobRunnerScheduleTest,
 * AdminOperationsServiceTest (manual run recorded, unknown job), AdminJobsControllerTest (ORGA only),
 * ScheduledJobInventoryTest (§6.3 jobs), UploadServiceTest (unclaimed uploads expire, admin listing), UploadsControllerTest
 * (ETag, 304, Cache-Control), CleanupJobTest; client admin-ops.spec, admin-settings.spec, orga-routes.spec (ORGA only),
 * content-q10.spec (statistics tiles).
 */
test.use({ role: 'orga' });

const PNG = fs.readFileSync(path.join(__dirname, '..', '..', 'fixtures', 'visual', 'tile.png'));

/** §6.3 jobs the ORGA finds on the job list. */
const SECTION_6_3_JOBS = [
  'mail-outbox',
  'open-slot-adverts',
  'signout-alerts',
  'pickup-reminder',
  'onboarding-nudges',
  'region-maintenance',
  'store-maintenance',
  'region-statistics',
  'statistics-aggregation',
  'audit-retention',
  'hygiene-expiry',
  'cleanup',
  'history-retention',
  'automation-evaluate',
  'request-automation',
  'newsletter-send',
  'telegram-poll',
];

const STATISTICS = ['kgSaved', 'pickups', 'foodsavers', 'stores', 'cooperations', 'foodSharePoints', 'baskets'] as const;

/** Breakers live in memory (a seed reset keeps them): a successful probe closes them again. */
async function closeBreakers(api: ApiClient): Promise<void> {
  for (const integration of ['SMTP', 'WEB_PUSH', 'TELEGRAM', 'GEOCODING']) {
    const response = await api.fetch('POST', `/api/admin/integrations/${integration}/probe`);
    expect(response.ok(), await response.text()).toBe(true);
  }
}

async function upload(api: ApiClient, name: string): Promise<string> {
  const response = await api.fetch('POST', '/api/uploads', undefined, {
    multipart: { file: { name, mimeType: 'image/png', buffer: PNG } },
  });
  expect(response.status(), await response.text()).toBe(201);
  return ((await response.json()) as { id: string }).id;
}

/** The counter's visible text without grouping characters (de-CH uses ’ as thousands separator). */
async function shownNumber(page: Page, key: string): Promise<number> {
  const text = await page.getByTestId(`content.statistics.${key}`).locator('[aria-hidden="true"]').innerText();
  return Number(text.replace(/[^\d]/g, ''));
}

test('orga checks the integrations, follows an outage, runs jobs, reads the outbox and tidies the uploads @AU12 @D6.3 @C3 @N6 @C10', async ({
  page,
  api,
  as,
  apiOf,
  clock,
}, testInfo) => {
  test.setTimeout(180_000);
  const orgaApi = apiOf(page);
  await acceptPendingConsents(orgaApi);
  await closeBreakers(orgaApi);
  const walk = new Walk(page, testInfo, 'orga');
  const card = (integration: string) => page.locator(`[data-testid="adminOps.integrations.card"][data-integration="${integration}"]`);
  const success = page.locator('.p-toast-message-success');
  /** Times and errors of earlier outages live in memory across tests. */
  const integrationNoise = () => [page.getByTestId('adminOps.integrations.downSince'), page.getByTestId('adminOps.integrations.lastError')];
  const jobRow = (name: string) => page.locator(`[data-testid="adminOps.jobs.row"][data-job="${name}"]`);

  await walk.step('checks the integrations and sends a test mail', async () => {
    await page.goto('/dashboard');
    await expect(page.getByTestId('dashboard.page.welcome')).toBeVisible();
    await walk.nav('adminIntegrations');
    await expect(page).toHaveURL(/\/admin\/integrations$/);
    await expectDataState(page, 'adminOps.integrations.state', 'success');
    for (const integration of ['SMTP', 'WEB_PUSH', 'TELEGRAM']) {
      await expect(card(integration), integration).toHaveAttribute('data-mode', 'LOG');
      await expect(card(integration), integration).toHaveAttribute('data-state', 'CLOSED');
    }
    await checkpoint(page, 'integrations', { mask: integrationNoise() });
    await walk.click(card('SMTP').getByTestId('adminOps.integrations.probe'));
    await expect(success).toBeVisible();
    // the probe toast lives 4 s: under load it can be gone before the second one shows, so count them one at a time
    await expect(success).toHaveCount(0, { timeout: 15_000 });
    await walk.click(card('SMTP').getByTestId('adminOps.integrations.test'));
    await expect(success).toBeVisible();
  });

  await walk.step('follows the outage bell and closes the breaker with a probe', async () => {
    // geocoding goes down (dev endpoint): the breaker opens and the ORGA is rung
    const failed = await api.fetch('POST', '/api/dev/integrations/GEOCODING/fail');
    expect(failed.ok(), await failed.text()).toBe(true);
    await openNewestBell(walk, orgaApi, 'INTEGRATION_DOWN');
    await expect(page).toHaveURL(/\/admin\/integrations$/);
    await expect(card('GEOCODING')).toHaveAttribute('data-state', 'OPEN');
    await expect(card('GEOCODING').getByTestId('adminOps.integrations.downSince')).toBeVisible();
    await checkpoint(page, 'integration-down', { mask: integrationNoise() });
    await walk.click(card('GEOCODING').getByTestId('adminOps.integrations.probe'));
    await expect(card('GEOCODING')).toHaveAttribute('data-state', 'CLOSED');
  });

  await walk.step('runs the statistics aggregation and checks the public statistics', async () => {
    await walk.nav('adminJobs');
    await expect(page).toHaveURL(/\/admin\/jobs$/);
    await expectDataState(page, 'adminOps.jobs.state', 'success');
    const listed = await page.getByTestId('adminOps.jobs.row').evaluateAll((rows) => rows.map((row) => row.getAttribute('data-job')));
    for (const job of SECTION_6_3_JOBS) expect(listed, job).toContain(job);
    await walk.click(jobRow('statistics-aggregation').getByTestId('adminOps.jobs.run'));
    await expect(jobRow('statistics-aggregation')).toHaveAttribute('data-outcome', 'OK');
    await expect(jobRow('statistics-aggregation')).toContainText('von Hand');
    // the job table fits a phone: every row is a block, nothing pushes the page sideways
    await expectFitsScreen(page, 'job list');
    await expectTableFits(page.getByTestId('adminOps.jobs.table'));
    // no capture of the job list: the last runs (real clock), outcomes and locks of the frequent jobs wrap differently
    // from run to run and move the rows and column widths of the whole table

    const stats = await api.getJson<Record<(typeof STATISTICS)[number], number> & { asOf: string }>('/api/public/statistics');
    expect(stats.kgSaved).toBeGreaterThan(0);
    const guest = await as(null);
    await guest.goto('/statistics');
    await expectDataState(guest, 'content.statistics.state', 'success');
    await expect(guest.getByTestId('content.statistics.tile')).toHaveCount(7);
    for (const key of STATISTICS) {
      await expect(guest.getByTestId(`content.statistics.${key}`)).toHaveAttribute('data-value', String(stats[key]));
    }
    // the counters count up and end exactly at the (rounded) totals
    for (const key of STATISTICS) await expect.poll(() => shownNumber(guest, key)).toBe(Math.round(stats[key]));
    await expect(guest.getByTestId('content.statistics.asOf')).toContainText(new Date(stats.asOf).getUTCFullYear().toString());
    await checkpoint(guest, 'statistics', { mask: [guest.getByTestId('content.statistics.asOf')] });
    await guest.close();
  });

  await walk.step('looks for failed mails in the outbox', async () => {
    await walk.nav('adminMailOutbox');
    await expect(page).toHaveURL(/\/admin\/mail-outbox$/);
    const state = page.getByTestId('adminOps.outbox.state');
    await expectDataState(page, 'adminOps.outbox.state', 'success');
    // the test mail of the first step
    await expect(page.getByTestId('adminOps.outbox.row').first()).toContainText('orga@local');
    // status filter and table fit a phone (the filter wraps, rows are blocks)
    await expectFitsScreen(page, 'mail outbox');
    await expectTableFits(page.getByTestId('adminOps.outbox.table'));
    // the seed mails share one creation time and come in any order (reported): only the newest row is shown
    await checkpoint(page, 'outbox', {
      mask: [
        page.locator('[data-testid="adminOps.outbox.row"] td:nth-of-type(1), [data-testid="adminOps.outbox.row"] td:nth-of-type(4)'),
        page.locator('[data-testid="adminOps.outbox.row"]:not(:first-of-type)'),
      ],
    });
    const failed = page.waitForRequest((request) => request.url().includes('/api/admin/mail-outbox') && request.url().includes('status=FAILED'));
    await walk.click(page.getByTestId('adminOps.outbox.filter').locator('.p-togglebutton').nth(3));
    await failed;
    await expect(state).toHaveAttribute('data-state', /success|empty/);
    await expect(page.locator('[data-testid="adminOps.outbox.row"]:not([data-status="FAILED"])')).toHaveCount(0);
    const searched = page.waitForRequest((request) => request.url().includes('/api/admin/mail-outbox') && request.url().includes('search=nobody'));
    await walk.fill(page.getByTestId('adminOps.outbox.search'), 'nobody@nowhere.invalid');
    await searched;
    await expect(state).toHaveAttribute('data-state', 'empty');
  });

  // a member uploads a flyer and never uses it
  const member = apiOf(await as('foodsaver'));
  const flyer = await upload(member, 'flyer-herbst.png');
  const uploadRow = (id: string) => page.locator(`[data-testid="adminSettings.uploads.row"][data-id="${id}"]`);

  await walk.step('deletes an unused upload', async () => {
    await walk.nav('adminUploads');
    await expect(page).toHaveURL(/\/admin\/uploads$/);
    await expectDataState(page, 'adminSettings.uploads.state', 'success');
    // the seed avatars count as profile photos; the new upload is unused
    await expect(page.locator('[data-testid="adminSettings.uploads.module"][data-module="USER_PHOTO"]')).toBeVisible();
    await expect(page.getByTestId('adminSettings.uploads.unclaimedCount')).toHaveText('1');
    await pickOption(page, 'adminSettings.uploads.filterSelect', 'UNCLAIMED');
    walk.count(2);
    const row = uploadRow(flyer);
    await expect(row).toContainText('flyer-herbst.png');
    await expect(row.getByTestId('adminSettings.uploads.unclaimedTag')).toBeVisible();
    const preview = row.locator('img');
    await expect(preview).toHaveAttribute('src', `/api/uploads/${flyer}?size=sm`);
    await expect.poll(() => preview.evaluate((img: HTMLImageElement) => img.complete && img.naturalWidth > 0)).toBe(true);
    await expect(page.locator('[data-testid="adminSettings.uploads.row"]:not([data-module="UNCLAIMED"])')).toHaveCount(0);
    await expectTableFits(page.getByTestId('adminSettings.uploads.table'));
    // upload times come from the real clock
    await checkpoint(page, 'uploads-unclaimed', { mask: [row.locator('td').filter({ hasText: /\d{2}:\d{2}/ })] });
    await walk.click(row.getByTestId('adminSettings.uploads.delete'));
    await acceptConfirm(page);
    walk.count(1);
    await expect(row).toHaveCount(0);
    await expectDataState(page, 'adminSettings.uploads.state', 'empty');
    await expect(page.getByTestId('adminSettings.uploads.unclaimedCount')).toHaveText('0');
    expect((await member.get(`/api/uploads/${flyer}`)).status()).toBe(404);
  });

  // another one is forgotten; two days later it has expired
  const forgotten = await upload(member, 'vergessen.png');
  await clock.fixServer(new Date(new Date(SEED_NOW).getTime() + 2 * 24 * 3600_000).toISOString());

  await walk.step('runs the nightly cleanup, which removes the forgotten upload', async () => {
    await page.reload();
    await pickOption(page, 'adminSettings.uploads.filterSelect', 'UNCLAIMED');
    walk.count(2);
    await expect(uploadRow(forgotten)).toBeVisible();
    await walk.nav('adminJobs');
    await expectDataState(page, 'adminOps.jobs.state', 'success');
    await walk.click(jobRow('cleanup').getByTestId('adminOps.jobs.run'));
    await expect(jobRow('cleanup')).toHaveAttribute('data-outcome', 'OK');
    await walk.nav('adminUploads');
    await expectDataState(page, 'adminSettings.uploads.state', 'success');
    await pickOption(page, 'adminSettings.uploads.filterSelect', 'UNCLAIMED');
    walk.count(2);
    await expectDataState(page, 'adminSettings.uploads.state', 'empty');
    const unclaimed = await orgaApi.getJson<{ items: { id: string }[] }>('/api/admin/uploads?unclaimed=true');
    expect(unclaimed.items.map((item) => item.id)).not.toContain(forgotten);
  });

  walk.finish({ steps: 6, clicks: 30, fields: 1 });
});
