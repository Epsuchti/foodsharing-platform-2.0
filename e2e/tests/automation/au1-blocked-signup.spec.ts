import { type ApiClient, expect, expectDataState, test } from '../../fixtures';
import { openBlocked, openPickups, pickupRow } from '../pickups/_pickups';
import { S06, S06_A, S06_B, createPickup, openAutomation, waitForAudit } from './_automation';

/**
 * Decision 165 S2: store rules block before the sign-up is stored. Members see the rules on the Pickups tab ("So kannst
 * du dich hier eintragen"), a free place they cannot take as a "Gesperrt" chip with a reason dialog that names their
 * own dates, and the attempt is refused (409) and audited as "Gesperrt". Store managers are never blocked; /pickups
 * leaves blocked pickups out. Seed: S-06 Babu's Bakery has the live policy (holiday rule; team bieb manager + sleepy,
 * sleepy's seeded pickup Tuesday 10.11.).
 */
test.use({ seedReset: 'test', role: 'sleepy' });

/** bieb switches S-06 to the gap rule alone with `gap` days (experience off) and adds the two one-time pickups. */
async function gapRule(bieb: ApiClient, gap: number): Promise<void> {
  const url = `/api/stores/${S06.id}/automation/pickup-policy`;
  const current = await bieb.getJson<{ pickupPolicy: Record<string, unknown> }>(`/api/stores/${S06.id}/automation`);
  await bieb.putJson(url, { ...current.pickupPolicy, enabled: true, experienceRuleEnabled: false, gapRuleEnabled: true, minimumGapDays: gap });
  await createPickup(bieb, 'S-06', S06_A);
  await createPickup(bieb, 'S-06', S06_B);
}

test('AU1 a rule-breaking sign-up is refused up front: rules card, Gesperrt chip, reason dialog, audit @AU1 @PU3 @PU4 @page', async ({
  page,
  as,
  apiOf,
  store,
}) => {
  const biebPage = await as('bieb');
  const bieb = apiOf(biebPage);
  await gapRule(bieb, 3);
  // Friday passes (3 days before the seeded Tuesday) and is confirmed by the rules
  await store.signUp('sleepy', 'S-06', S06_A);
  expect((await waitForAudit(bieb, 'S-06', S06_A)).status).toBe('CONFIRMED');

  // the member summary: collapsed, one line per active rule, remembered open
  await openPickups(page, 'S-06');
  const card = page.getByTestId('stores.pickups.rules');
  await expect(card).toHaveAttribute('data-state', 'success');
  await expect(card).not.toHaveAttribute('open', '');
  await page.getByTestId('stores.pickups.rules.toggle').click();
  await expect(card.locator('[data-testid="stores.pickups.rules.line"][data-rule="GAP"]')).toContainText('3');
  await expect(card.locator('[data-testid="stores.pickups.rules.line"][data-rule="MANUAL_DATE"]')).toBeVisible();
  await expect(card.locator('[data-testid="stores.pickups.rules.line"][data-rule="EXPERIENCE"]')).toHaveCount(0);
  await page.reload();
  await expectDataState(page, 'stores.pickups.state', 'success');
  await expect(page.getByTestId('stores.pickups.rules')).toHaveAttribute('open', '');

  // Sunday is 1 day from Friday and from Tuesday: a lock chip instead of "+ Eintragen"
  const sunday = pickupRow(page, S06_B);
  await expect(sunday.getByTestId('stores.pickups.slot.free')).toHaveCount(0);
  const chip = sunday.getByTestId('stores.pickups.slot.blocked');
  await expect(chip).toHaveAttribute('aria-haspopup', 'dialog');
  await expect(chip).toHaveAttribute('data-rules', '1');
  await openBlocked(page, S06_B);
  const reason = page.locator('[data-testid="pickups.blocked.reason"][data-rule="GAP"]');
  await expect(reason).toHaveCount(1);
  // the own data: the close pickup and the gap; the fix names the next possible day (Saturday 14.11.)
  await expect(reason).toContainText('3 Tage');
  await expect(reason).toContainText('14. November');
  await expect(page.getByTestId('pickups.blocked.seeRules')).toBeVisible();
  await page.getByTestId('pickups.blocked.action').click();
  await expect(page.getByTestId('pickups.blocked.dialog')).toBeHidden();
  await expect(page.getByTestId('stores.pickups.range.all')).toHaveAttribute('aria-pressed', 'true');

  // the server refuses it, too (409 AUTOMATION_RULE), audits it as DECLINED with the rule, and stores nothing
  expect(await store.trySignUp('sleepy', 'S-06', S06_B)).toBe(false);
  const audit = await waitForAudit(bieb, 'S-06', S06_B);
  expect(audit.status).toBe('DECLINED');
  expect(audit.reasons.map((r) => r.rule)).toEqual(['GAP']);
  const pickups = await store.pickupsOf(bieb, 'S-06');
  expect(pickups.find((p) => p.pickupAt === S06_B)!.occupiedSlots).toHaveLength(0);

  // /pickups and the home card: the blocked pickup is not offered
  const options = await store.withRole('sleepy', (client) =>
    client.getJson<{ store: { id: string }; pickupAt: string }[]>('/api/users/me/pickups/options'),
  );
  expect(options.filter((o) => o.store.id === S06.id).map((o) => o.pickupAt)).not.toContain(S06_B);

  // the store manager's protocol labels the refused attempt "Gesperrt"
  await openAutomation(biebPage, 'S-06', 'audits');
  await expectDataState(biebPage, 'automation.audits.state', 'success');
  const row = biebPage.locator('[data-testid="automation.audits.row"][data-status="DECLINED"]').first();
  await expect(row.getByTestId('automation.audits.status')).toHaveText('Gesperrt');
});

test('AU1 store managers are never blocked by the rules @AU1 @PU3', async ({ as, apiOf }) => {
  const biebPage = await as('bieb');
  await gapRule(apiOf(biebPage), 30);
  await openPickups(biebPage, 'S-06');
  await expect(biebPage.getByTestId('stores.pickups.slot.blocked')).toHaveCount(0);
  await expect(pickupRow(biebPage, S06_B).getByTestId('stores.pickups.slot.free')).toBeEnabled();
});

test('AU1 rules off: no rules card and no lock from the rules @AU1 @PU3', async ({ page, as, apiOf }) => {
  const bieb = apiOf(await as('bieb'));
  await gapRule(bieb, 3);
  const current = await bieb.getJson<{ pickupPolicy: Record<string, unknown> }>(`/api/stores/${S06.id}/automation`);
  await bieb.putJson(`/api/stores/${S06.id}/automation/pickup-policy`, { ...current.pickupPolicy, enabled: false });
  await openPickups(page, 'S-06');
  await expect(page.getByTestId('stores.pickups.rules')).toHaveCount(0);
  await expect(page.getByTestId('stores.pickups.slot.blocked')).toHaveCount(0);
});
