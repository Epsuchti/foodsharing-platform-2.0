import { S01_THURSDAY, expect, expectDataState, seedStore, test, zurichPickupAt } from '../../fixtures';
import { pickupRow } from '../pickups/_pickups';
import { NAMES } from '../stores/_stores';
import { Walk, isPhone } from './_persona';

/**
 * D11.5.5 persona walkthrough "store manager" (decision 3, design review M7 round 3): Nina (bieb@local, manager of
 * S-01 Coop Bahnhofbrücke) opens her store, confirms Sarah's sign-up for Thursday, adds a regular Tuesday pickup
 * and checks both in the store log. Runs on desktop and on the phone project; on the phone the store's tabs must
 * start on the first screen (round-2 finding: the manager's hero filled it).
 */
test.use({ seedReset: 'test', role: 'bieb' });

const S01 = seedStore('S-01');

test('D11.5.5 store manager: confirm a sign-up, edit regular slots, read the store log @D11.5.5 @PU3 @PU1 @S12 @S1 @page', async ({
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
    await walk.click(card.first());
    await expect(page.getByTestId('stores.page.name')).toHaveText(S01.name);
    await expect(page.getByTestId('stores.page.edit')).toBeVisible();
    if (isPhone(page)) {
      // the hero leaves room for the tabs on the first screen (one "Route" button instead of two map links)
      const tabs = (await page.getByTestId('stores.page.tabs').boundingBox())!;
      expect(tabs.y + tabs.height, 'store tabs on the first phone screen').toBeLessThanOrEqual(page.viewportSize()!.height);
      await expect(page.getByTestId('stores.page.navigate')).toBeHidden();
      await walk.click(page.getByTestId('stores.page.route'));
      await expect(page.getByTestId('stores.page.navigate')).toBeVisible();
      await expect(page.getByTestId('stores.page.navigateApple')).toBeVisible();
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
    await walk.click(page.getByTestId('stores.pickups.confirmSlot'));
    await expect(slot).toHaveAttribute('data-confirmed', 'true');
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
    await walk.click(page.getByTestId('stores.regular.save'));
    await expect(page.locator('.p-dialog')).toHaveCount(0);
    await expect(pickupRow(page, tuesday)).toBeVisible();
    await expect(pickupRow(page, tuesday).getByTestId('stores.pickups.regularBadge')).toBeVisible();
  });

  await walk.step('reads both changes in the store log', async () => {
    await walk.click(page.getByTestId('stores.page.tab.log'));
    await expectDataState(page, 'stores.log.list', 'success');
    await expect(page.locator('[data-testid="stores.log.entry"][data-action="STORE.SLOT_CONFIRMED"]').first()).toBeVisible();
    await expect(page.locator('[data-testid="stores.log.entry"][data-action="STORE.UPDATE_REGULAR_PICKUP_SETTINGS"]').first()).toBeVisible();
  });

  // round 3: 11 clicks on desktop, 13 on the phone (menu drawer, "Route")
  walk.finish({ steps: 4, clicks: 13, fields: 1 });
});
