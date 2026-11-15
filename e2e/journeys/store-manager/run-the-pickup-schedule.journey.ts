import { S01_THURSDAY, expectDataState, expectStatus, seedStore, zurichPickupAt } from '../../fixtures';
import { checkpoint, checkpointElement, expect, journey as test } from '../../fixtures/checkpoint';
import { openPickups, pickupRow } from '../../fixtures/flows/pickups';
import { NAMES } from '../../fixtures/flows/stores';
import { Walk } from '../../fixtures/persona';
import { fillDate, fillTime } from '../../support/date-inputs';

/**
 * Store manager Nina (bieb@local, manager of S-01 Coop Bahnhofbrücke, KAM of the Coop chain) runs the pickup schedule
 * (decision 165): she reads Sarah's slot in the rich dialog, filters the pickups to "Unbestätigt", confirms a whole
 * pickup in one go and the rest from the ✓ of the split pill (the dialog is the review moment, "Bestätigen" has the
 * focus) until nothing is left, joins Thursday's day chat, takes Marco off Thursday with a message, adds a one-time
 * pickup, changes it and cancels it with 0 slots, looks two weeks ahead and reads the pickup history by range, also of
 * Coop Winterthur Bahnhof where she is KAM but not in the team. Seed S-01: Thursday 5.11. 19:00 Sarah unconfirmed +
 * Marco confirmed, Monday 9.11. 19:00 Marco unconfirmed + Nina. The rules (who confirms, slot limits, the horizon, the
 * history right) are unit tests (PickupSignupEditAndConfirmTest, PickupListRangeTest, StorePermissionMatrixTest).
 */
test.use({ role: 'bieb' });

const S01 = seedStore('S-01');
const S23 = seedStore('S-23');
const ONE_TIME = zurichPickupAt(8, '10:00');

const taken = (page: import('@playwright/test').Page, at: string, name: string) =>
  pickupRow(page, at).locator(`[data-testid="stores.pickups.slot.taken"][data-user="${name}"]`);

test('store manager confirms, removes and adds pickups and reads the history @PU1 @PU2 @PU3 @PU6 @S9 @D10.3', async ({
  page,
  as,
  apiOf,
  dev,
  store,
}, testInfo) => {
  test.setTimeout(180_000);
  const walk = new Walk(page, testInfo, 'store manager');

  // Marco signs out of Thursday and in again: now two sign-ups wait there
  const path = `/api/stores/${S01.id}/pickups/${encodeURIComponent(S01_THURSDAY)}/signups`;
  await store.withRole('sleepy', async (client) => {
    const me = await client.session();
    await expectStatus(await client.delete(`${path}/${me.user!.id}`), [200, 204], 'Marco signs out');
  });
  await store.signUp('sleepy', 'S-01', S01_THURSDAY);

  await walk.step('reads Sarah\'s slot', async () => {
    await openPickups(page, 'S-01');
    const summary = pickupRow(page, S01_THURSDAY).getByTestId('stores.pickups.summary');
    await expect(summary).toHaveAttribute('data-pending', '2');
    // every slot button names its state, every ✓ whom it confirms
    const slots = page.locator('[data-testid="stores.pickups.slot.taken"], [data-testid="stores.pickups.slot.free"]');
    for (const name of await slots.evaluateAll((els) => els.map((el) => el.getAttribute('aria-label') ?? ''))) {
      expect(name).toMatch(/bestätigt|unbestätigt|frei/i);
    }
    for (const name of await page.getByTestId('stores.pickups.slot.confirm').evaluateAll((els) => els.map((el) => el.getAttribute('aria-label') ?? ''))) {
      expect(name).toMatch(/^.+ bestätigen$/);
    }
    await checkpoint(page, 'pickups-as-manager');
    await taken(page, S01_THURSDAY, NAMES.foodsaver).scrollIntoViewIfNeeded();
    await walk.click(taken(page, S01_THURSDAY, NAMES.foodsaver));
    const dialog = page.getByTestId('stores.pickups.slotDialog');
    await expect(dialog).toHaveAttribute('data-mode', 'rich');
    await expectDataState(page, 'stores.pickups.slotDetail.state', 'success');
    await expect(dialog.getByTestId('stores.pickups.slotDetail.name')).toHaveText(NAMES.foodsaver);
    await expect(dialog.getByTestId('stores.pickups.slotDetail.call')).toHaveAttribute('href', 'tel:+41791234506');
    await expect(dialog.getByTestId('stores.pickups.slotState')).toHaveAttribute('data-confirmed', 'false');
    await expect(dialog.getByTestId('stores.pickups.slotDetail.pickupCount')).toBeVisible();
    await walk.click(dialog.getByTestId('stores.pickups.slotDetail.upcoming').locator('summary'));
    await expect(dialog.getByTestId('stores.pickups.slotDetail.upcomingItem').first()).toBeVisible();
    await expect(page.getByTestId('stores.pickups.confirmSlot')).toBeVisible();
    await expect(page.getByTestId('stores.pickups.removeSlot')).toBeVisible();
    await checkpoint(page, 'slot-dialog-manager');
    await page.keyboard.press('Escape');
    await expect(dialog).toBeHidden();
  });

  await walk.step('confirms everything that waits', async () => {
    const chip = page.getByTestId('stores.pickups.range.pending');
    await expect(chip).toHaveAttribute('aria-pressed', 'false');
    const count = Number(await chip.getAttribute('data-count'));
    expect(count).toBeGreaterThanOrEqual(3);
    await walk.click(chip);
    await expect(chip).toHaveAttribute('aria-pressed', 'true');
    // every listed pickup has an unconfirmed slot; together they hold the chip's count
    const rows = page.getByTestId('stores.pickups.pickup');
    await expect(rows.first()).toBeVisible();
    const pending = await rows.evaluateAll((els) =>
      els.map((el) => el.querySelectorAll('[data-testid="stores.pickups.slot.taken"][data-confirmed="false"]').length),
    );
    expect(pending.every((n) => n > 0)).toBe(true);
    expect(pending.reduce((a, b) => a + b, 0)).toBe(count);

    // Thursday in one go: one dialog that names both
    const thursday = pickupRow(page, S01_THURSDAY);
    await thursday.scrollIntoViewIfNeeded();
    await walk.click(thursday.getByTestId('stores.pickups.confirmAll'));
    const confirmAll = page.locator('.p-confirmdialog');
    await expect(confirmAll).toContainText(NAMES.foodsaver);
    await expect(confirmAll).toContainText(NAMES.sleepy);
    await checkpoint(page, 'confirm-all');
    await walk.click(page.locator('.p-confirmdialog-accept-button'));
    await expect(thursday).toHaveCount(0);
    await expect(chip).toHaveAttribute('data-count', String(count - 2));

    // the rest from the ✓: the slot dialog opens with "Bestätigen" focused, Enter confirms
    for (let left = count - 2; left > 0; left--) {
      await walk.click(page.getByTestId('stores.pickups.slot.confirm').first());
      await expect(page.getByTestId('stores.pickups.slotState')).toHaveAttribute('data-confirmed', 'false');
      await expect(page.getByTestId('stores.pickups.confirmSlot').locator('button')).toBeFocused();
      await page.keyboard.press('Enter');
      walk.count(1);
      await expect(chip).toHaveAttribute('data-count', String(left - 1));
    }
    await expect(chip).toHaveAttribute('aria-pressed', 'true');
    await expect(page.getByTestId('stores.pickups.pendingEmpty')).toBeVisible();
    await expect(rows).toHaveCount(0);
    await checkpoint(page, 'nothing-left-to-confirm');
  });

  let chatId = '';
  await test.step('Sarah opens Thursday\'s day chat', async () => {
    const sarah = await as('foodsaver');
    await openPickups(sarah, 'S-01');
    await sarah.locator('[data-testid="stores.pickups.day"][data-day="2026-11-05"]').getByTestId('stores.pickups.dayChat').click();
    await expect(sarah).toHaveURL(/\/messages\/[0-9a-f-]{36}$/);
    chatId = sarah.url().split('/').pop()!;
  });

  await walk.step('joins Thursday\'s day chat', async () => {
    await walk.click(page.getByTestId('stores.pickups.range.pending'));
    await expect(page.getByTestId('stores.pickups.range.pending')).toHaveCount(0);
    const thursday = page.locator('[data-testid="stores.pickups.day"][data-day="2026-11-05"]');
    await thursday.scrollIntoViewIfNeeded();
    await walk.click(thursday.getByTestId('stores.pickups.dayChat'));
    // not signed up that day, she lands in the team's chat of that day and joins it
    await expect(page).toHaveURL(new RegExp(`/messages/${chatId}$`));
    const me = await dev.user('bieb');
    const chat = await apiOf(page).getJson<{ name: string; members: { id: string }[] }>(`/api/conversations/${chatId}`);
    expect(chat.name).toContain(S01.name);
    expect(chat.members.map((m) => m.id)).toContain(me.id);
  });

  await walk.step('takes Marco off Thursday with a message', async () => {
    await openPickups(page, 'S-01');
    const marco = taken(page, S01_THURSDAY, NAMES.sleepy);
    await marco.scrollIntoViewIfNeeded();
    await walk.click(marco);
    await walk.click(page.getByTestId('stores.pickups.removeSlot'));
    await walk.fill(page.getByTestId('stores.pickups.removeDialog.message'), 'Du bist im Urlaub, wir übernehmen das.');
    await checkpoint(page, 'remove-dialog');
    await walk.click(page.getByTestId('stores.pickups.removeDialog.confirm'));
    await expect(marco).toHaveCount(0);
    await expect(taken(page, S01_THURSDAY, NAMES.foodsaver)).toHaveAttribute('data-confirmed', 'true');
  });

  await walk.step('adds a one-time pickup, changes it and cancels it', async () => {
    await walk.click(page.getByTestId('stores.pickups.add'));
    await fillDate(page.getByTestId('stores.pickups.edit.date'), '2026-11-10');
    await fillTime(page.getByTestId('stores.pickups.edit.time'), '10:00');
    walk.count(2);
    await walk.fill(page.getByTestId('stores.pickups.edit.description'), 'Sonderabholung Inventur');
    await checkpoint(page, 'one-time-dialog');
    await walk.click(page.getByTestId('stores.pickups.edit.save'));
    const row = pickupRow(page, ONE_TIME);
    await expect(row.getByTestId('stores.pickups.description')).toHaveText('Sonderabholung Inventur');
    await expect(row.getByTestId('stores.pickups.regularBadge')).toHaveCount(0);

    await walk.click(row.getByTestId('stores.pickups.edit'));
    await walk.fill(page.locator('#pickup-edit-slots'), '4');
    await walk.fill(page.getByTestId('stores.pickups.edit.description'), 'Inventur, bitte grosse Taschen');
    await walk.click(page.getByTestId('stores.pickups.edit.save'));
    await expect(row).toHaveAttribute('data-slots', '4');
    await expect(row.getByTestId('stores.pickups.description')).toHaveText('Inventur, bitte grosse Taschen');

    // 0 slots cancels a one-time pickup
    await walk.click(row.getByTestId('stores.pickups.edit'));
    await walk.fill(page.locator('#pickup-edit-slots'), '0');
    await walk.click(page.getByTestId('stores.pickups.edit.save'));
    await expect(page.getByTestId('stores.pickups.edit.description')).toBeHidden();
    await expect(row).toHaveCount(0);
  });

  await walk.step('looks two weeks ahead and back into the history', async () => {
    await walk.click(page.getByTestId('stores.pickups.range.twoWeeks'));
    await expect(page.getByTestId('stores.pickups.range.twoWeeks')).toHaveAttribute('aria-pressed', 'true');
    await expect(page.locator(`[data-testid="stores.pickups.day"][data-day="2026-11-12"]`)).toBeVisible();
    await walk.click(page.getByTestId('stores.pickups.history'));
    await expect(page).toHaveURL(/\/pickups\/history$/);
    await expect(page.getByTestId('stores.page.tab.pickups')).toHaveAttribute('aria-current', 'page');
    await expectDataState(page, 'stores.pickupHistory.state', 'success');
    // the last 30 days: Sarah picked up on 29.10.
    await expect(page.locator(`[data-testid="stores.pickupHistory.person"][data-user="${NAMES.foodsaver}"]`).first()).toBeVisible();
    await fillDate(page.getByTestId('stores.pickupHistory.from'), '2026-10-01');
    await fillDate(page.getByTestId('stores.pickupHistory.to'), '2026-10-15');
    walk.count(2);
    const answered = page.waitForResponse((r) => r.url().includes('/pickup-history?') && r.url().includes('from='));
    await walk.click(page.getByTestId('stores.pickupHistory.apply'));
    expect((await answered).ok()).toBe(true);
    await expectDataState(page, 'stores.pickupHistory.state', 'success');
    const times = () =>
      page.getByTestId('stores.pickupHistory.row').evaluateAll((rows) => rows.map((row) => Date.parse(row.getAttribute('data-pickup-at')!)));
    // only 1–15 October (Zürich days) once the new page is shown
    await expect
      .poll(async () => {
        const shown = await times();
        return shown.length > 0 && shown.every((at) => at >= Date.parse('2026-09-30T22:00:00Z') && at <= Date.parse('2026-10-15T22:00:00Z'));
      })
      .toBe(true);
    await checkpointElement(page.getByTestId('stores.pickupHistory.table'), 'history-1-15-october');
    await walk.click(page.getByTestId('stores.pickupHistory.upcoming'));
    await expectDataState(page, 'stores.pickups.state', 'success');
    // as KAM of the chain she reads Coop Winterthur Bahnhof's history without being in its team
    await page.goto(`/stores/${S23.id}/pickups/history`);
    await expect(page.getByTestId('stores.pickupHistory.state')).toHaveAttribute('data-state', /success|empty/);
  });

  walk.finish({ steps: 6, clicks: 26, fields: 5 });
});
