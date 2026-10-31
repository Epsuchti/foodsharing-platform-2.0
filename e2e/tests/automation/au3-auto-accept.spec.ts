import type { Page } from '@playwright/test';
import { acceptPendingConsents, expect, expectDataState, test } from '../../fixtures';
import { openAutomation, saveSection, setSwitch, typeNumber } from './_automation';

/** AU3: team request automation on S-07 (welcome manages it, guide has applied; seeded request policy: dry run, 5 km). */
test.use({ role: 'welcome', seedReset: 'test' });

async function goLiveAndRun(page: Page, km: number): Promise<void> {
  await openAutomation(page, 'S-07', 'auto-accept');
  await expect(page.getByTestId('automation.request.root')).toHaveAttribute('data-enabled', 'true');
  await setSwitch(page.getByTestId('automation.request.dryRun'), false);
  await typeNumber(page.getByTestId('automation.request.km'), km);
  // run evaluates the saved policy: blocked until the save is done and the saved state is reloaded
  const run = page.getByTestId('automation.request.run').locator('button');
  await expect(run).toBeDisabled();
  await expect(page.getByTestId('automation.request.saveFirst')).toBeVisible();
  await saveSection(page, 'automation.request');
  await expect(page.getByTestId('automation.request.saveFirst')).toBeHidden();
  await expect(run).toBeEnabled();
  await run.click();
  await expect(page.getByTestId('automation.request.runResult')).toBeVisible();
}

async function requestDecision(page: Page): Promise<import('@playwright/test').Locator> {
  await page.getByTestId('automation.store.nav.audits').click();
  await page.getByTestId('automation.audits.kind.REQUEST').click();
  await expectDataState(page, 'automation.audits.state', 'success');
  return page.locator('[data-testid="automation.audits.row"][data-kind="REQUEST"][data-dry-run="false"]').first();
}

test('AU3 application auto-accepted; far-away applicant declined with distance message @AU3 @page', async ({ page, resetSeed, apiOf }) => {
  // welcome still has to accept the store-manager confidentiality notice (seed)
  await acceptPendingConsents(apiOf(page));
  await goLiveAndRun(page, 5);
  const approved = await requestDecision(page);
  await expect(approved).toHaveAttribute('data-status', 'APPROVED');

  // same applicant, but now farther away than the limit
  await resetSeed();
  // the reset ends all sessions: log in again in the same browser context
  await apiOf(page).loginAs('welcome');
  await acceptPendingConsents(apiOf(page));
  await goLiveAndRun(page, 0.1);
  const declined = await requestDecision(page);
  await expect(declined).toHaveAttribute('data-status', 'DECLINED');
  await declined.getByTestId('automation.audits.toggle').click();
  await expect(declined.locator('[data-testid="automation.audits.reason"][data-rule="DISTANCE"]')).toBeVisible();
});
