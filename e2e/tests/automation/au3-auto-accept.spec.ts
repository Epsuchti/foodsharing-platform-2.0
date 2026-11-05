import type { Page } from '@playwright/test';
import { type NotificationHelpers, acceptPendingConsents, expect, expectDataState, test } from '../../fixtures';
import { openAutomation, saveSection, setSwitch, typeNumber } from './_automation';

/**
 * AU3: team request automation on S-07 (welcome manages it, guide has applied; the seeded request policy, 5 km, is off
 * since changeset 188: no dry run, decision 167). Switching it on decides the open application in the next job run.
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

async function requestDecision(page: Page): Promise<import('@playwright/test').Locator> {
  await page.getByTestId('automation.store.nav.audits').click();
  await page.getByTestId('automation.audits.kind.REQUEST').click();
  await expectDataState(page, 'automation.audits.state', 'success');
  return page.locator('[data-testid="automation.audits.row"][data-kind="REQUEST"]:not([data-status^="WOULD_"])').first();
}

test('AU3 application auto-accepted; far-away applicant declined with distance message @AU3 @page', async ({ page, resetSeed, apiOf, notifications }) => {
  // welcome still has to accept the store-manager confidentiality notice (seed)
  await acceptPendingConsents(apiOf(page));
  await switchOnAndRun(page, notifications, 5);
  const approved = await requestDecision(page);
  await expect(approved).toHaveAttribute('data-status', 'APPROVED');

  // same applicant, but now farther away than the limit
  await resetSeed();
  // the reset ends all sessions: log in again in the same browser context
  await apiOf(page).loginAs('welcome');
  await acceptPendingConsents(apiOf(page));
  await switchOnAndRun(page, notifications, 0.1);
  const declined = await requestDecision(page);
  await expect(declined).toHaveAttribute('data-status', 'DECLINED');
  await declined.getByTestId('automation.audits.toggle').click();
  await expect(declined.locator('[data-testid="automation.audits.reason"][data-rule="DISTANCE"]')).toBeVisible();
});
