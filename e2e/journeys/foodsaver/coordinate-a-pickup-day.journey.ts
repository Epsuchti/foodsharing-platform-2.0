import { S01_THURSDAY, expectDataState, seedStore, zurichPickupAt } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { openBlocked, openPickups, pickupRow } from '../../fixtures/flows/pickups';
import { NAMES, acceptConfirm } from '../../fixtures/flows/stores';
import { Walk } from '../../fixtures/persona';

/**
 * Foodsaver Sarah (foodsaver@local) plans her Thursday at S-01 Coop Bahnhofbrücke (seed: Thursday 2026-11-05 19:00
 * Sarah unconfirmed + Marco confirmed; Monday 2026-11-09 Marco unconfirmed + manager Nina confirmed). She looks at who
 * picks up with her (rich slot dialog for a co-pickup, short one with a profile link for the rest of the team, decision
 * 165), finds Sunday locked by Zürich's region rule (2 pickups per ±7 days, decision 167 S2: the "Gesperrt" chip says
 * why), opens the day chat of Thursday and finally signs out of Thursday again.
 */
test.use({ role: 'foodsaver' });

const S01 = seedStore('S-01');
const S01_SUNDAY = zurichPickupAt(6, '10:00');
const S01_MONDAY = zurichPickupAt(7, '19:00');

interface ConversationDetail {
  name: string;
  members: { id: string }[];
}

const takenSlot = (page: import('@playwright/test').Page, pickupAt: string, name: string) =>
  pickupRow(page, pickupAt).locator(`[data-testid="stores.pickups.slot.taken"][data-user="${name}"]`);

test('foodsaver checks the team of a pickup day, meets the region rule, opens the day chat and signs out @PU3 @PU4 @PU5', async ({
  page,
  api,
  dev,
}, testInfo) => {
  test.setTimeout(120_000);
  const walk = new Walk(page, testInfo, 'foodsaver');
  const [me, sleepy, bieb] = await Promise.all([dev.user('foodsaver'), dev.user('sleepy'), dev.user('bieb')]);

  await walk.step('opens the pickups of her store', async () => {
    await openPickups(page, 'S-01');
    // already signed up for Thursday: its free place is not offered to her again
    await expect(pickupRow(page, S01_THURSDAY).getByTestId('stores.pickups.slot.free')).toBeDisabled();
    await expect(takenSlot(page, S01_THURSDAY, NAMES.foodsaver)).toHaveAttribute('data-confirmed', 'false');
    await checkpoint(page, 'pickups-tab');
  });

  await walk.step('looks at who picks up with her', async () => {
    await walk.click(takenSlot(page, S01_THURSDAY, NAMES.sleepy));
    const dialog = page.getByTestId('stores.pickups.slotDialog');
    await expect(dialog).toHaveAttribute('data-mode', 'rich');
    await expectDataState(page, 'stores.pickups.slotDetail.state', 'success');
    await expect(dialog.getByTestId('stores.pickups.slotState')).toHaveAttribute('data-confirmed', 'true');
    await expect(dialog.getByTestId('stores.pickups.slotDetail.message')).toBeVisible();
    await expect(page.getByTestId('stores.pickups.confirmSlot')).toHaveCount(0);
    await expect(page.getByTestId('stores.pickups.removeSlot')).toHaveCount(0);
    await checkpoint(page, 'slot-co-pickup');
    await walk.click(page.getByTestId('stores.pickups.slotDetail.close'));
    await expect(dialog).toBeHidden();
  });

  await walk.step('opens the profile of the manager on Monday', async () => {
    await walk.click(takenSlot(page, S01_MONDAY, NAMES.bieb));
    const dialog = page.getByTestId('stores.pickups.slotDialog');
    await expect(dialog).toHaveAttribute('data-mode', 'short');
    await expect(dialog.getByTestId('stores.pickups.slotDetail.message')).toHaveCount(0);
    await expect(dialog.getByTestId('stores.pickups.slotDetail.pickupCount')).toHaveCount(0);
    await checkpoint(page, 'slot-team-member');
    await walk.click(dialog.getByTestId('stores.pickups.slotDetail.profile'));
    await expect(page).toHaveURL(new RegExp(`/profile/${bieb.id}$`));
    await page.goBack();
    await expectDataState(page, 'stores.pickups.state', 'success');
  });

  await walk.step('finds Sunday locked and reads why', async () => {
    await openBlocked(page, S01_SUNDAY);
    await walk.count(1);
    await expect(page.locator('[data-testid="shared.eligibility.reason"][data-reason="REGION_RULE_TIMESPAN_LIMIT"]')).toBeVisible();
    // review fix A3: one plain line per limit
    await expect(page.getByTestId('shared.eligibility.ruleTimespan')).toContainText(
      'höchstens 2 Abholungen innerhalb von 7 Tagen vor oder nach diesem Termin',
    );
    await expect(page.getByTestId('shared.eligibility.ruleDay')).toContainText('Höchstens 1 pro Tag.');
    await expect(page.getByTestId('pickups.blocked.signUp')).toHaveCount(0);
    await checkpoint(page, 'blocked-region-rule');
    await page.keyboard.press('Escape');
    await expect(page.getByTestId('pickups.blocked.state')).toBeHidden();
  });

  await walk.step('opens the day chat of Thursday', async () => {
    const button = page.locator('[data-testid="stores.pickups.day"][data-day="2026-11-05"]').getByTestId('stores.pickups.dayChat');
    await expect(button).toHaveAccessibleName('Chat öffnen');
    await walk.click(button);
    await expect(page).toHaveURL(/\/messages\/[0-9a-f-]{36}$/);
    const chat = await api.getJson<ConversationDetail>(`/api/conversations/${page.url().split('/').pop()}`);
    expect(chat.name).toContain(S01.name);
    expect(chat.members.map((m) => m.id).sort()).toEqual([me.id, sleepy.id].sort());
    await checkpoint(page, 'day-chat');
  });

  await walk.step('signs out of Thursday', async () => {
    await openPickups(page, 'S-01');
    await walk.click(takenSlot(page, S01_THURSDAY, NAMES.foodsaver));
    await expect(page.locator('.p-confirmdialog')).toBeVisible();
    await checkpoint(page, 'sign-out-confirm');
    await acceptConfirm(page);
    await walk.count(1);
    await expect(pickupRow(page, S01_THURSDAY).locator(`[data-user="${NAMES.foodsaver}"]`)).toHaveCount(0);
    await checkpoint(page, 'signed-out');
  });

  walk.finish({ steps: 6, clicks: 8, fields: 0 });
});
