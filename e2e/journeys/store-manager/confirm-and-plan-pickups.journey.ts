import { S01_THURSDAY, expectDataState, seedStore, zurichPickupAt } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { pickupRow } from '../../fixtures/flows/pickups';
import { NAMES } from '../../fixtures/flows/stores';
import { Walk, isPhone } from '../../fixtures/persona';

/**
 * Store manager Nina (bieb@local, manager of S-01 Coop Bahnhofbrücke) opens her store, confirms Sarah's sign-up for
 * Thursday, adds a regular Tuesday pickup and checks both in the store log (D11.5.5 persona walkthrough, design
 * review M7 round 3). On the phone the store's tabs start on the first screen (round-2 finding: the manager's hero
 * filled it) and the two map links sit behind one "Navigate" dropdown.
 */
test.use({ role: 'bieb' });

const S01 = seedStore('S-01');

test('store manager confirms a sign-up, adds a regular slot and reads the store log @D11.5.5 @PU3 @PU1 @S12 @S1', async ({
  page,
}, testInfo) => {
  test.setTimeout(120_000);
  const walk = new Walk(page, testInfo, 'store manager');
  const tuesday = zurichPickupAt(1, '19:00');

  await walk.step('opens the store from "my stores"', async () => {
    await page.goto('/dashboard');
    await expect(page.getByTestId('dashboard.page.welcome')).toBeVisible();
    await walk.nav('stores');
    await expect(page).toHaveURL(/\/stores\/mine$/);
    const card = page.getByTestId('stores.mine.store').filter({ hasText: S01.name });
    await checkpoint(page, 'my-stores');
    await walk.click(card.first());
    await expect(page.getByTestId('stores.page.name')).toHaveText(S01.name);
    await expect(page.getByTestId('stores.page.edit')).toBeVisible();
    await checkpoint(page, 'store-overview');
    if (isPhone(page)) {
      const tabs = (await page.getByTestId('stores.page.tabs').boundingBox())!;
      expect(tabs.y + tabs.height, 'store tabs on the first phone screen').toBeLessThanOrEqual(page.viewportSize()!.height);
      await expect(page.getByTestId('stores.page.navigate.google')).toBeHidden();
      await walk.click(page.getByTestId('stores.page.navigate'));
      await expect(page.getByTestId('stores.page.navigate.google')).toBeVisible();
      await expect(page.getByTestId('stores.page.navigate.apple')).toBeVisible();
      await page.keyboard.press('Escape');
      await expect(page.getByTestId('stores.page.navigate.google')).toBeHidden();
      await expect(page.getByTestId('stores.page.navigate')).toBeFocused();
    }
  });

  await walk.step('confirms Sarah for Thursday', async () => {
    await walk.click(page.getByTestId('stores.page.tab.pickups'));
    await expectDataState(page, 'stores.pickups.state', 'success');
    const row = pickupRow(page, S01_THURSDAY);
    await row.scrollIntoViewIfNeeded();
    const slot = row.locator(`[data-testid="stores.pickups.slot.taken"][data-user="${NAMES.foodsaver}"]`);
    await expect(slot).toHaveAttribute('data-confirmed', 'false');
    await walk.click(slot);
    await expect(page.getByTestId('stores.pickups.confirmSlot')).toBeVisible();
    await checkpoint(page, 'slot-menu');
    await walk.click(page.getByTestId('stores.pickups.confirmSlot'));
    await expect(slot).toHaveAttribute('data-confirmed', 'true');
    await checkpoint(page, 'pickups-confirmed');
  });

  await walk.step('adds a regular Tuesday pickup', async () => {
    await walk.click(page.getByTestId('stores.pickups.regular'));
    const rows = page.getByTestId('stores.regular.row');
    await expect(rows).toHaveCount(2);
    await walk.click(page.getByTestId('stores.regular.add'));
    await expect(rows).toHaveCount(3);
    const added = rows.nth(2);
    await walk.click(added.getByTestId('stores.regular.weekday'));
    await walk.click(page.getByRole('option', { name: 'Dienstag' }));
    await walk.fill(added.getByTestId('stores.regular.time'), '19:00');
    await expect(page.getByTestId('stores.regular.duplicate')).toHaveCount(0);
    await checkpoint(page, 'regular-slots-dialog');
    await walk.click(page.getByTestId('stores.regular.save'));
    await expect(page.locator('.p-dialog')).toHaveCount(0);
    await expect(pickupRow(page, tuesday)).toBeVisible();
    await expect(pickupRow(page, tuesday).getByTestId('stores.pickups.regularBadge')).toBeVisible();
  });

  await walk.step('reads both changes in the store log', async () => {
    await walk.click(page.getByTestId('stores.page.tab.log'));
    await expectDataState(page, 'stores.log.list', 'success');
    await expect(page.locator('[data-testid="stores.log.entry"][data-action="STORE.SLOT_CONFIRMED"]').first()).toBeVisible();
    const regular = page.locator('[data-testid="stores.log.entry"][data-action="STORE.UPDATE_REGULAR_PICKUP_SETTINGS"]').first();
    await expect(regular).toBeVisible();
    // the change in words: the field's name and the weekdays in German, no field code or English weekday
    const change = regular.getByTestId('stores.log.entry.change');
    await expect(change).toContainText('Regelmässige Abholzeiten');
    await expect(change).toContainText('Dienstag 19:00');
    await expect(change).not.toContainText(/Regular pickups|TUESDAY/);
    await checkpoint(page, 'store-log');
  });

  // round 3: 11 clicks on desktop, 13 on the phone (menu drawer, "Route"); decision 149: Betriebe is a phone tab,
  // 12 on the phone
  walk.finish({ steps: 4, clicks: 12, fields: 1 });
});
