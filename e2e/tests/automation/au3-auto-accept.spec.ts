import type { Page } from '@playwright/test';
import { type ApiClient, type NotificationHelpers, acceptPendingConsents, expect, expectDataState, seedStore, test } from '../../fixtures';
import { type AuditDto, openAutomation, saveSection, setSwitch, typeNumber } from './_automation';

/**
 * AU3: team request automation on S-07 (welcome manages it, guide has applied; the seeded request policy, 5 km, is off:
 * no dry run, decision 167). Switching it on decides the open application in the next job run.
 */
test.use({ role: 'welcome', seedReset: 'test' });

async function switchOnAndRun(page: Page, notifications: NotificationHelpers, km: number): Promise<void> {
  await openAutomation(page, 'S-07', 'auto-accept');
  await expect(page.getByTestId('automation.request.root')).toHaveAttribute('data-enabled', 'false');
  // one An/Aus switch, no test mode and no run buttons
  for (const gone of ['dryRun', 'dryRunBanner', 'runDry', 'run', 'runResult', 'saveFirst']) {
    await expect(page.getByTestId(`automation.request.${gone}`)).toHaveCount(0);
  }
  await setSwitch(page.getByTestId('automation.request.enabled'), true);
  await typeNumber(page.getByTestId('automation.request.km'), km);
  await saveSection(page, 'automation.request');
  await expect(page.getByTestId('automation.request.root')).toHaveAttribute('data-enabled', 'true');
  await notifications.runJob('request-automation');
}

/** Seeded S-07 request audits (docs/seed-ids.md, 0022 table 0008): older than the decision of the job run. */
const SEEDED_REQUEST_AUDIT = '00000022-0008-';

async function requestDecision(page: Page, client: ApiClient): Promise<import('@playwright/test').Locator> {
  // the job run wrote a new audit; it is the newest REQUEST row (the seeded ones are days old)
  await expect
    .poll(async () => {
      const audits = await client.getJson<{ items: AuditDto[] }>(`/api/stores/${seedStore('S-07').id}/automation/audits?kind=REQUEST&size=50`);
      return audits.items.some((a) => !a.id.startsWith(SEEDED_REQUEST_AUDIT));
    })
    .toBe(true);
  await page.getByTestId('automation.store.nav.audits').click();
  await page.getByTestId('automation.audits.kind.REQUEST').click();
  await expectDataState(page, 'automation.audits.state', 'success');
  return page.locator('[data-testid="automation.audits.row"][data-kind="REQUEST"]').first();
}

test('AU3 application auto-accepted; far-away applicant declined with distance message @AU3 @page', async ({ page, resetSeed, apiOf, notifications }) => {
  // welcome still has to accept the store-manager confidentiality notice (seed)
  await acceptPendingConsents(apiOf(page));
  await switchOnAndRun(page, notifications, 5);
  const approved = await requestDecision(page, apiOf(page));
  await expect(approved).toHaveAttribute('data-status', 'APPROVED');

  // same applicant, but now farther away than the limit
  await resetSeed();
  // the reset ends all sessions: log in again in the same browser context
  await apiOf(page).loginAs('welcome');
  await acceptPendingConsents(apiOf(page));
  await switchOnAndRun(page, notifications, 0.1);
  const declined = await requestDecision(page, apiOf(page));
  await expect(declined).toHaveAttribute('data-status', 'DECLINED');
  await declined.getByTestId('automation.audits.toggle').click();
  await expect(declined.locator('[data-testid="automation.audits.reason"][data-rule="DISTANCE"]')).toBeVisible();
});
