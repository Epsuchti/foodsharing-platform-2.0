import { type ApiClient, expect, expectDataState, seedStore, test } from '../../fixtures';
import { S06, S06_A, S06_B, createPickup, openAutomation, savePolicy, setSwitch, typeNumber, waitForAudit } from './_automation';

/** AU1: pickup sign-up automation per store (rules, dry run, excluded dates, holidays, linked stores, run now, preview). */
test.use({ role: 'bieb', seedReset: 'test' });

/** bieb sets S-06's policy: experience off, gap `gap` days, optional dry run. */
async function configureS06(page: import('@playwright/test').Page, api: ApiClient, gap: number, dryRun: boolean): Promise<void> {
  await createPickup(api, 'S-06', S06_A);
  await createPickup(api, 'S-06', S06_B);
  await openAutomation(page, 'S-06', 'policy');
  await expect(page.getByTestId('automation.policy.root')).toHaveAttribute('data-enabled', 'true');
  await setSwitch(page.getByTestId('automation.policy.experience'), false);
  await setSwitch(page.getByTestId('automation.policy.gapEnabled'), true);
  await typeNumber(page.getByTestId('automation.policy.gapDays'), gap);
  await setSwitch(page.getByTestId('automation.policy.dryRun'), dryRun);
  await expect(page.getByTestId('automation.policy.dryRunBanner')).toHaveCount(dryRun ? 1 : 0);
  await savePolicy(page);
}

test('AU1 manager enables gap rule; second sign-up two days later is auto-declined with message, audit shows GAP @AU1 @page', async ({
  page,
  store,
  api,
}) => {
  await configureS06(page, api, 3, false);
  await store.signUp('sleepy', 'S-06', S06_A);
  expect((await waitForAudit(api, 'S-06', S06_A)).status).toBe('CONFIRMED');
  await store.signUp('sleepy', 'S-06', S06_B);
  const declined = await waitForAudit(api, 'S-06', S06_B);
  expect(declined.status).toBe('DECLINED');
  expect(declined.reasons.map((r) => r.rule)).toContain('GAP');

  // the decline is visible in the decisions list with rule chip and the user's message
  await page.getByTestId('automation.store.nav.audits').click();
  await expectDataState(page, 'automation.audits.state', 'success');
  const row = page.locator(`[data-testid="automation.audits.row"][data-status="DECLINED"]`).first();
  await row.getByTestId('automation.audits.toggle').click();
  await expect(row.locator('[data-testid="automation.audits.reason"][data-rule="GAP"]')).toBeVisible();
  await expect(row.getByTestId('automation.audits.message')).not.toBeEmpty();

  // the second sign-up was removed from the pickup
  const pickups = await store.pickupsOf(api, 'S-06');
  const second = pickups.find((p) => p.pickupAt === S06_B)!;
  expect(second.occupiedSlots.map((s) => s.user.displayName)).not.toContain('Marco Bernasconi');
});

test('AU1 dry run records WOULD_DECLINE and keeps the sign-up @AU1', async ({ page, store, api }) => {
  await configureS06(page, api, 3, true);
  await store.signUp('sleepy', 'S-06', S06_A);
  await store.signUp('sleepy', 'S-06', S06_B);
  const audit = await waitForAudit(api, 'S-06', S06_B);
  expect(audit.status).toBe('WOULD_DECLINE');
  expect(audit.dryRun).toBe(true);
  const second = (await store.pickupsOf(api, 'S-06')).find((p) => p.pickupAt === S06_B)!;
  expect(second.occupiedSlots.map((s) => s.user.displayName)).toContain('Marco Bernasconi');

  await page.getByTestId('automation.store.nav.audits').click();
  await page.getByTestId('automation.audits.kind.PICKUP').click();
  await expect(page.locator('[data-testid="automation.audits.row"][data-status="WOULD_DECLINE"]').first().getByTestId('automation.audits.dryRun')).toBeVisible();
});

test('AU1 excluded date and holiday skip automatic confirmation @AU1', async ({ page, store, api }) => {
  // excluded date through the API (the multi-date picker is covered by the chip list below)
  const policyUrl = `/api/stores/${S06.id}/automation/pickup-policy`;
  const automation = await api.getJson<{ pickupPolicy: Record<string, unknown> }>(`/api/stores/${S06.id}/automation`);
  const excluded = S06_A.slice(0, 10);
  await createPickup(api, 'S-06', S06_A);
  await api.putJson(policyUrl, { ...automation.pickupPolicy, experienceRuleEnabled: false, excludedDates: [excluded] });

  await openAutomation(page, 'S-06', 'policy');
  await expect(page.locator(`[data-testid="automation.policy.excludedDate"][data-date="${excluded}"]`)).toBeVisible();

  await store.signUp('sleepy', 'S-06', S06_A);
  const skipped = await waitForAudit(api, 'S-06', S06_A);
  expect(skipped.status).toBe('SKIPPED');
  expect(skipped.reasons.map((r) => r.rule)).toContain('MANUAL_DATE');

  // Christmas (CH national holiday) lies outside the sign-up window: the preview shows the holiday rule
  const christmas = '2026-12-25T18:00:00Z';
  await api.putJson(`/api/stores/${S06.id}/pickups/${encodeURIComponent(christmas)}`, { slotCount: 2 });
  const sleepy = await store.withRole('sleepy', (c) => c.getJson<{ user: { id: string } }>('/api/auth/session'));
  const holiday = await api.postJson<{ decision: string; reasons: { rule: string }[] }>(`/api/stores/${S06.id}/automation/pickup-policy/preview`, {
    userId: sleepy.user.id,
    pickupAt: christmas,
  });
  expect(holiday.decision).toBe('SKIPPED');
  expect(holiday.reasons.map((r) => r.rule)).toContain('PUBLIC_HOLIDAY');
});

test('AU1 cleaning rule decides a sign-up without cleaning by the cleaning rule @AU1', async ({ page, store, api }) => {
  await openAutomation(page, 'S-06', 'policy');
  await setSwitch(page.getByTestId('automation.policy.experience'), false);
  await setSwitch(page.getByTestId('automation.policy.gapEnabled'), false);
  await setSwitch(page.getByTestId('automation.policy.cleaning'), true);
  await expect(page.getByTestId('automation.policy.exemptionsLink')).toBeVisible();
  await savePolicy(page);
  await createPickup(api, 'S-06', S06_A);
  await store.signUp('sleepy', 'S-06', S06_A);
  const audit = await waitForAudit(api, 'S-06', S06_A);
  expect(audit.reasons.map((r) => r.rule).some((rule) => rule.startsWith('CLEANING'))).toBe(true);
});

test('AU1 linked-store editor saves a bidirectional rule with message preview, mirrored on the linked store @AU1', async ({ page }) => {
  await openAutomation(page, 'S-06', 'policy');
  await page.getByTestId('automation.policy.addLink').click();
  const link = page.getByTestId('automation.policy.link.0');
  await link.getByTestId('automation.policy.link.0.bidirectional').click();
  const stores = link.getByTestId('automation.policy.link.0.stores');
  await stores.click();
  await page.locator('.p-multiselect-overlay').getByText(seedStore('S-01').name, { exact: true }).click();
  await stores.locator('.p-multiselect-dropdown').click();
  await expect(page.locator('.p-multiselect-overlay')).toHaveCount(0);
  await link.getByTestId('automation.policy.link.0.message.placeholder.linkedStoreName').click();
  await expect(link.getByTestId('automation.policy.link.0.message.preview')).toContainText(seedStore('S-01').name);
  await savePolicy(page);

  // the mirror rule shows up (read-only) on the linked store
  await openAutomation(page, 'S-01', 'policy');
  await expect(page.getByTestId('automation.policy.link.mirror')).toContainText(S06.name);
});

test('AU1 run now and preview decision @AU1', async ({ page }) => {
  await openAutomation(page, 'S-01', 'policy');
  await expect(page.getByTestId('automation.policy.root')).toHaveAttribute('data-dry-run', 'true');
  await page.getByTestId('automation.policy.runDry').click();
  await expect(page.getByTestId('automation.policy.runResult')).toBeVisible();

  await page.getByTestId('automation.policy.preview').click();
  const dialog = page.getByTestId('automation.preview.dialog');
  await dialog.getByTestId('automation.preview.user.search').fill('Marco');
  await dialog.getByTestId('automation.preview.user.add').first().click();
  await dialog.getByTestId('automation.preview.pickup').click();
  await page.locator('.p-select-overlay .p-select-option').first().click();
  await dialog.getByTestId('automation.preview.run').click();
  await expect(dialog.getByTestId('automation.preview.result')).toHaveAttribute('data-decision', /[A-Z_]+/);
});
