import type { Page } from '@playwright/test';
import { ACTIVE_ZURICH_FSPS, FSPS, expectDataState, regionId } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { Walk, openNewestBell } from '../../fixtures/persona';

/**
 * Food share points (Fairteiler) of zuerich-stadt. Sarah (foodsaver@local) looks through her region's Fairteiler and
 * proposes a new one: the pin follows the address she types, the description is Markdown, and the proposal waits for
 * the region's Fairteiler admins. Bot (bot@local) administers the Zürich FSP group: he is told by bell about a
 * proposal (Sarah's second one, through her session), activates it and adds Julien (guide@local) as a second manager.
 * Who may create, activate, edit and see an inactive Fairteiler is unit-tested (FspPermissionMatrixTest); the pin from
 * the address too (FspLocateTest, fsp.util.spec.ts).
 */
const ZURICH = regionId('zuerich-stadt');
const card = (page: Page, fspId: string) => page.locator(`fs-fsp-card[data-fsp-id="${fspId}"]`);

test.describe('member', () => {
  test.use({ role: 'foodsaver' });

  test('member looks through the Fairteiler of her region and proposes one @FS1 @R3', async ({ page, apiOf }, testInfo) => {
    test.setTimeout(120_000);
    const walk = new Walk(page, testInfo, 'member');

    await walk.step('looks through the Fairteiler of her region', async () => {
      await page.goto(`/regions/${ZURICH}`);
      await walk.click(page.getByTestId('regions.page.tab.foodSharePoints'));
      await expect(page).toHaveURL(new RegExp(`/regions/${ZURICH}/food-share-points$`));
      await expectDataState(page, 'foodSharePoints.region.state', 'success');
      // the four active ones and her own proposal, the deleted one never
      for (const id of ACTIVE_ZURICH_FSPS) await expect(card(page, id)).toBeVisible();
      await expect(page.getByTestId('foodSharePoints.card')).toHaveCount(5);
      await expect(card(page, FSPS.altstetten).getByTestId('foodSharePoints.card.inactive')).toBeVisible();
      await expect(card(page, FSPS.wiedikon).getByTestId('foodSharePoints.card.following')).toBeVisible();
      await expect(card(page, FSPS.enge)).toHaveCount(0);
      const map = page.getByTestId('foodSharePoints.region.map');
      await expect(map.locator('.fs-map-marker[data-kind="fsp"]')).toHaveCount(ACTIVE_ZURICH_FSPS.length + 1);
      await checkpoint(page, 'region-fairteiler');
    });

    await walk.step('proposes a Fairteiler; the pin follows the address', async () => {
      await walk.click(page.getByTestId('foodSharePoints.region.add'));
      await expect(page).toHaveURL(new RegExp(`/food-share-points/new\\?regionId=${ZURICH}$`));
      await expectDataState(page, 'foodSharePoints.form.state', 'success');
      await expect(page.getByTestId('foodSharePoints.form.activationNote')).toBeVisible();
      await expect(page.getByTestId('foodSharePoints.form.description')).toHaveAttribute('rows', '10');
      await walk.fill(page.getByTestId('foodSharePoints.form.name'), 'Fairteiler Langstrasse Hof');
      await walk.fill(page.getByTestId('foodSharePoints.form.description'), '**Kühlschrank** im Hof, _täglich_ offen.');
      const position = page.getByTestId('foodSharePoints.form.position');
      await walk.fill(page.getByTestId('foodSharePoints.form.street'), 'Langstrasse 100');
      await walk.fill(page.getByTestId('foodSharePoints.form.postcode'), '8004');
      await walk.fill(page.getByTestId('foodSharePoints.form.city'), 'Zürich');
      // static geocoder in e2e: the postcode's point
      await expect(position).toContainText('47.37860');
      await expect(position).toContainText('8.52030');
      await expect(page.getByTestId('foodSharePoints.form.geocodeStatus')).toHaveAttribute('data-status', 'found');
      // without the pin the button places it again
      await walk.click(page.getByTestId('foodSharePoints.form.clearPin'));
      await expect(position).toHaveCount(0);
      await walk.click(page.getByTestId('foodSharePoints.form.geocode'));
      await expect(position).toContainText('47.37860');
      await checkpoint(page, 'proposal-form');
    });

    await walk.step('sends the proposal, which waits for activation', async () => {
      await walk.click(page.getByTestId('foodSharePoints.form.submit'));
      await expect(page).toHaveURL(/\/food-share-points\/[0-9a-f-]{36}$/);
      const fspId = new URL(page.url()).pathname.split('/').pop()!;
      await expectDataState(page, 'foodSharePoints.detail.state', 'success');
      await expect(page.getByTestId('foodSharePoints.detail.inactive')).toBeVisible();
      await expect(page.getByTestId('foodSharePoints.detail.activate')).toHaveCount(0);
      await expect(page.getByTestId('foodSharePoints.managers.item')).toHaveCount(1);
      const description = page.getByTestId('foodSharePoints.detail.description');
      await expect(description.locator('strong')).toHaveText('Kühlschrank');
      await expect(description.locator('em')).toHaveText('täglich');
      const fsp = await apiOf(page).getJson<{ lat: number; lon: number }>(`/api/food-share-points/${fspId}`);
      expect([fsp.lat, fsp.lon]).toEqual([47.3786, 8.5203]);
      await checkpoint(page, 'proposal-waiting');
    });

    // measured 2026-11-15, desktop and phone
    walk.finish({ steps: 3, clicks: 5, fields: 5 });
  });
});

test.describe('Fairteiler admin', () => {
  test.use({ role: 'bot' });

  test('Fairteiler admin activates a proposal from the bell and adds a manager @FS1 @N3', async ({ page, api, as, apiOf, profiles }, testInfo) => {
    test.setTimeout(120_000);
    const walk = new Walk(page, testInfo, 'Fairteiler admin');
    const guideId = await profiles.userId('guide');

    await walk.step('is told about a proposal and activates it', async () => {
      // Sarah proposes a Fairteiler (her own session)
      const created = await apiOf(await as('foodsaver')).post(`/api/regions/${ZURICH}/food-share-points`, {
        name: 'Fairteiler Albisrieden',
        street: 'Albisriederstrasse 300',
        postcode: '8047',
        city: 'Zürich',
        description: 'Regal im Velokeller, täglich offen.',
      });
      expect(created.status(), await created.text()).toBe(201);
      const fspId = ((await created.json()) as { id: string }).id;
      await page.goto('/dashboard');
      await expect(page.getByTestId('dashboard.page.welcome')).toBeVisible();
      await openNewestBell(walk, api, 'FSP_ACTIVATE');
      await expect(page).toHaveURL(new RegExp(`/food-share-points/${fspId}$`));
      await expectDataState(page, 'foodSharePoints.detail.state', 'success');
      await expect(page.getByTestId('foodSharePoints.detail.inactive')).toBeVisible();
      await checkpoint(page, 'proposal-to-activate');
      await walk.click(page.getByTestId('foodSharePoints.detail.activate'));
      await expect(page.getByTestId('foodSharePoints.detail.inactive')).toHaveCount(0);
    });

    await walk.step('adds Julien as a second manager', async () => {
      await walk.click(page.getByTestId('foodSharePoints.managers.edit'));
      await walk.fill(page.getByTestId('foodSharePoints.managers.picker.search'), 'Favre');
      await walk.click(
        page.locator(`[data-testid="foodSharePoints.managers.picker.hit"][data-user-id="${guideId}"]`).getByTestId('foodSharePoints.managers.picker.add'),
      );
      await expect(page.getByTestId('foodSharePoints.managers.draftItem')).toHaveCount(2);
      await checkpoint(page, 'managers-editor');
      await walk.click(page.getByTestId('foodSharePoints.managers.save'));
      await expect(page.getByTestId('foodSharePoints.managers.item')).toHaveCount(2);
      await expect(page.locator(`[data-testid="foodSharePoints.managers.item"][data-user-id="${guideId}"]`)).toBeVisible();
      await checkpoint(page, 'fairteiler-activated');
    });

    // measured 2026-11-15: 6 clicks on desktop, 7 on the phone
    walk.finish({ steps: 2, clicks: 7, fields: 1 });
  });
});
