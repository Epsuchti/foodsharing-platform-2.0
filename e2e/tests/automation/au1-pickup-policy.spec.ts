import { type ApiClient, expect, expectDataState, seedStore, test } from '../../fixtures';
import { type AuditDto, S06, S06_A, S06_B, createPickup, openAutomation, savePolicy, setSwitch, typeNumber, waitForAudit } from './_automation';

/** AU1: pickup sign-up automation per store (one switch, rules, excluded dates, linked stores; no dry run: decision 165). */
test.use({ role: 'bieb', seedReset: 'test' });

/** bieb sets S-06's policy: experience off, gap `gap` days, rules on or off. */
async function configureS06(page: import('@playwright/test').Page, api: ApiClient, gap: number, enabled = true): Promise<void> {
  await createPickup(api, 'S-06', S06_A);
  await createPickup(api, 'S-06', S06_B);
  await openAutomation(page, 'S-06', 'policy');
  await expect(page.getByTestId('automation.policy.root')).toHaveAttribute('data-enabled', 'true');
  await setSwitch(page.getByTestId('automation.policy.experience'), false);
  await setSwitch(page.getByTestId('automation.policy.gapEnabled'), true);
  await typeNumber(page.getByTestId('automation.policy.gapDays'), gap);
  await setSwitch(page.getByTestId('automation.policy.enabled'), enabled);
  await savePolicy(page);
}

test('AU1 manager enables gap rule; a second sign-up two days later is refused up front, audit shows GAP @AU1 @D10.3 @page', async ({
  page,
  store,
  api,
}) => {
  await configureS06(page, api, 3);
  await store.signUp('sleepy', 'S-06', S06_A);
  expect((await waitForAudit(api, 'S-06', S06_A)).status).toBe('CONFIRMED');
  // changed on purpose (decision 165 S2): the rule refuses before the sign-up is stored (409), no later decline
  expect(await store.trySignUp('sleepy', 'S-06', S06_B)).toBe(false);
  const declined = await waitForAudit(api, 'S-06', S06_B);
  expect(declined.status).toBe('DECLINED');
  expect(declined.reasons.map((r) => r.rule)).toContain('GAP');

  // the refusal is visible in the protocol as "Gesperrt" with the rule chip
  await page.getByTestId('automation.store.nav.audits').click();
  await expectDataState(page, 'automation.audits.state', 'success');
  const row = page.locator(`[data-testid="automation.audits.row"][data-status="DECLINED"]`).first();
  await expect(row.getByTestId('automation.audits.status')).toHaveText('Gesperrt');
  await row.getByTestId('automation.audits.toggle').click();
  await expect(row.locator('[data-testid="automation.audits.reason"][data-rule="GAP"]')).toBeVisible();

  // the second sign-up was never stored
  const pickups = await store.pickupsOf(api, 'S-06');
  const second = pickups.find((p) => p.pickupAt === S06_B)!;
  expect(second.occupiedSlots.map((s) => s.user.displayName)).not.toContain('Marco Bernasconi');
});

test('AU1 rules switched off: sign-ups wait for confirmation, nothing is decided or audited @AU1', async ({ page, store, api }) => {
  await configureS06(page, api, 3, false);
  await expect(page.getByTestId('automation.policy.root')).toHaveAttribute('data-enabled', 'false');
  await store.signUp('sleepy', 'S-06', S06_A);
  await store.signUp('sleepy', 'S-06', S06_B);
  const second = (await store.pickupsOf(api, 'S-06')).find((p) => p.pickupAt === S06_B)!;
  expect(second.occupiedSlots.map((s) => s.user.displayName)).toContain('Marco Bernasconi');
  const audits = await api.getJson<{ items: AuditDto[] }>(`/api/stores/${S06.id}/automation/audits?kind=PICKUP&size=50`);
  expect(audits.items.filter((a) => a.pickupAt === S06_A || a.pickupAt === S06_B)).toEqual([]);
});

test('AU1 excluded date skips automatic confirmation @AU1', async ({ page, store, api }) => {
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
});

test('AU1 cleaning rule decides a sign-up without cleaning by the cleaning rule @AU1', async ({ page, store, api }) => {
  await openAutomation(page, 'S-06', 'policy');
  await setSwitch(page.getByTestId('automation.policy.experience'), false);
  await setSwitch(page.getByTestId('automation.policy.gapEnabled'), false);
  await setSwitch(page.getByTestId('automation.policy.cleaning'), true);
  await expect(page.getByTestId('automation.policy.exemptionsLink')).toBeVisible();
  await savePolicy(page);
  await createPickup(api, 'S-06', S06_A);
  // stored with a reminder or refused up front (CLEANING_REQUIRED, decision 165 S2): either way the cleaning rule decides
  await store.trySignUp('sleepy', 'S-06', S06_A);
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

test('AU1 one switch "Regeln anwenden": the former dry-run policy of S-01 is off, no test mode, run or preview @AU1', async ({ page }) => {
  // decision 165 / changeset 188: dry-run policies became disabled
  await openAutomation(page, 'S-01', 'policy');
  const root = page.getByTestId('automation.policy.root');
  await expect(root).toHaveAttribute('data-enabled', 'false');
  await expect(page.getByTestId('automation.policy.enabled').locator('input')).not.toBeChecked();
  await expect(page.getByTestId('automation.policy.enabledHint')).not.toBeEmpty();
  for (const gone of ['dryRun', 'dryRunBanner', 'runDry', 'run', 'runResult', 'preview', 'saveFirst']) {
    await expect(page.getByTestId(`automation.policy.${gone}`)).toHaveCount(0);
  }
  const offHint = await page.getByTestId('automation.policy.enabledHint').textContent();
  await setSwitch(page.getByTestId('automation.policy.enabled'), true);
  await expect(page.getByTestId('automation.policy.enabledHint')).not.toHaveText(offHint ?? '');
});
