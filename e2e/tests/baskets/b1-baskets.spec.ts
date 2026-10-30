import path from 'node:path';
import { animationsDone } from '../a11y/animations';
import { acceptConfirm } from '../stores/_stores';
import {
  acceptPendingConsents,
  expect,
  expectDataState,
  expectErrorState,
  expectNoSeriousViolations,
  injectApiError,
  openNav,
  test,
} from '../../fixtures';

/**
 * B1 food baskets: offer, edit, delete (plan-m7 Q5, plan-m6 B5). The e2e clock is fixed at SEED_NOW
 * (2026-11-02T09:00Z), so a lifetime of 3 days expires on 2026-11-05. foodsaver@local has home coordinates;
 * profile@local has none (docs/seed-ids.md).
 */
test.use({ seedReset: 'test' });

/** A 256 px PNG to crop (the screenshot tile stub). */
const PHOTO = path.join(__dirname, '..', '..', 'screenshots', 'fixtures', 'tile.png');

test.describe('foodsaver', () => {
  test.use({ role: 'foodsaver' });

  test('B1 create basket with photo, kinds, custom location and lifetime; edit and delete @B1 @page', async ({ page, baskets }) => {
    // the nav entry leads to the nearby page, which offers a basket
    await page.goto('/dashboard');
    // header link, the header's "More" menu (design review M7 R3) or the phone's menu drawer
    await openNav(page, 'baskets');
    await expect(page).toHaveURL(/\/baskets$/);
    await expectDataState(page, 'baskets.nearby.state', 'success');
    await page.getByTestId('baskets.nearby.offer').click();
    await expect(page).toHaveURL(/\/baskets\/new$/);
    await expectDataState(page, 'baskets.form.state', 'success');

    const description = `Brot und Zopf vom Samstag ${Date.now()}`;
    await page.getByTestId('baskets.form.description').fill(description);
    await page.getByTestId('baskets.form.kind.BAKED_GOODS').check();
    await page.getByTestId('baskets.form.kind.DAIRY').check();
    await page.getByTestId('baskets.form.weight').fill('2.5');
    await page.getByTestId('baskets.form.lifetime').fill('3');

    // photo: crop in the browser, upload as JPEG
    await page.getByTestId('baskets.form.photo.input').setInputFiles(PHOTO);
    await page.getByTestId('baskets.form.photo.apply').click();
    await expect(page.getByTestId('baskets.form.photoPreview')).toBeVisible();
    await expect(page.getByTestId('baskets.form.photoUploading')).toHaveCount(0);
    await expect(page.getByTestId('baskets.form.photoSection')).toContainText(/hochgeladen/);

    // custom location: a click on the map sets the pin
    await page.getByTestId('baskets.form.locationCustom').check();
    const map = page.getByTestId('baskets.form.map');
    await expect(map.locator('.leaflet-container')).toBeVisible();
    await map.locator('.leaflet-container').click({ position: { x: 120, y: 80 } });
    await expect(page.getByTestId('baskets.form.position')).toBeVisible();

    // message and mobile contact
    await page.getByTestId('baskets.form.contactMobile').check();
    await page.getByTestId('baskets.form.mobile').fill('+41791234506');

    await page.getByTestId('baskets.form.submit').click();
    await expect(page).toHaveURL(/\/baskets\/[0-9a-f-]{36}$/);
    const basketId = page.url().split('/').pop()!;
    await expectDataState(page, 'baskets.detail.state', 'success');
    await expect(page.getByTestId('baskets.detail.description')).toHaveText(description);
    await expect(page.getByTestId('baskets.detail.kind')).toHaveCount(2);
    await expect(page.getByTestId('baskets.detail.photo')).toBeVisible();
    await expect(page.getByTestId('baskets.detail.weight')).toBeVisible();
    await expect(page.getByTestId('baskets.detail.requestsEmpty')).toBeVisible();

    const created = await baskets.get('foodsaver', basketId);
    expect(created.locationType).toBe('CUSTOM');
    expect(created.kinds.sort()).toEqual(['BAKED_GOODS', 'DAIRY']);
    expect(created.photoUrl).toMatch(/^\/api\/uploads\/[0-9a-f-]{36}/);
    expect(created.expiresAt).toMatch(/^2026-11-05T09:00/);
    // the custom pin is not the home address
    expect([created.lat, created.lon]).not.toEqual([47.389, 8.548]);
    // the photo variant is served
    const photo = await page.request.get(created.photoUrl!);
    expect(photo.status()).toBe(200);

    // edit: new description, one kind less
    await page.getByTestId('baskets.detail.edit').click();
    await expect(page).toHaveURL(new RegExp(`/baskets/${basketId}/edit$`));
    await expectDataState(page, 'baskets.form.state', 'success');
    await expect(page.getByTestId('baskets.form.description')).toHaveValue(description);
    await expect(page.getByTestId('baskets.form.lifetime')).toHaveCount(0);
    await page.getByTestId('baskets.form.description').fill(`${description} – nur noch Brot`);
    await page.getByTestId('baskets.form.kind.DAIRY').uncheck();
    await page.getByTestId('baskets.form.submit').click();
    await expect(page).toHaveURL(new RegExp(`/baskets/${basketId}$`));
    await expect(page.getByTestId('baskets.detail.description')).toHaveText(`${description} – nur noch Brot`);
    await expect(page.getByTestId('baskets.detail.kind')).toHaveCount(1);

    // delete after confirmation: gone from my baskets and from the API
    await page.getByTestId('baskets.detail.delete').click();
    // the confirmation itself passes axe (its header close button is named)
    await expect(page.getByRole('alertdialog')).toBeVisible();
    await animationsDone(page);
    await expectNoSeriousViolations(page, { include: ['[role="alertdialog"]'] });
    await acceptConfirm(page);
    await expect(page).toHaveURL(/\/baskets\/mine$/);
    await expectDataState(page, 'baskets.mine.offeredState', 'success');
    await expect(page.locator(`fs-basket-card[data-basket-id="${basketId}"]`)).toHaveCount(0);
    const gone = await page.request.get(`/api/baskets/${basketId}`);
    expect(gone.status()).toBe(404);
  });
});

test.describe('profile without address', () => {
  test.use({ role: 'profile' });

  test('B1 validation: location and contact required @B1', async ({ page, apiOf, baskets }) => {
    await acceptPendingConsents(apiOf(page));
    await page.goto('/baskets/new');
    await expectDataState(page, 'baskets.form.state', 'success');
    // without home coordinates the form starts with a custom location and explains the home option
    await expect(page.getByTestId('baskets.form.locationCustom')).toBeChecked();
    await expect(page.getByTestId('baskets.form.locationHomeMissing')).toBeVisible();

    await page.getByTestId('baskets.form.description').fill('Drei Joghurts');
    await page.getByTestId('baskets.form.contactMessage').uncheck();
    await page.getByTestId('baskets.form.submit').click();
    const problems = page.getByTestId('baskets.form.problems').locator('li');
    await expect(problems).toHaveCount(2);
    await expect(problems.nth(0)).toHaveAttribute('data-code', 'BASKET_LOCATION_REQUIRED');
    await expect(problems.nth(1)).toHaveAttribute('data-code', 'BASKET_CONTACT_REQUIRED');
    await expect(page).toHaveURL(/\/baskets\/new$/);

    // HOME without profile coordinates is refused as well
    await page.getByTestId('baskets.form.locationHome').check();
    await page.getByTestId('baskets.form.contactMessage').check();
    await expect(problems).toHaveCount(1);
    await expect(problems.first()).toHaveAttribute('data-code', 'BASKET_LOCATION_REQUIRED');

    // the server enforces the same rules
    const home = await baskets.tryCreate('profile', { description: 'Drei Joghurts', locationType: 'HOME', contactMessage: true, contactPhone: false, contactMobile: false });
    expect(home.status, home.body).toBe(400);
    expect(JSON.parse(home.body).code).toBe('BASKET_LOCATION_REQUIRED');
    const custom = await baskets.tryCreate('profile', {
      description: 'Drei Joghurts',
      locationType: 'CUSTOM',
      lat: 47.3769,
      lon: 8.5417,
      contactMessage: false,
      contactPhone: false,
      contactMobile: false,
    });
    expect(custom.status, custom.body).toBe(400);
    expect(JSON.parse(custom.body).code).toBe('BASKET_CONTACT_REQUIRED');
    const noPhone = await baskets.tryCreate('profile', {
      description: 'Drei Joghurts',
      locationType: 'CUSTOM',
      lat: 47.3769,
      lon: 8.5417,
      contactMessage: false,
      contactPhone: true,
      contactMobile: false,
    });
    expect(noPhone.status, noPhone.body).toBe(400);
  });

  test('B1 basket detail load error shows retry, unknown basket not found @B1', async ({ page, apiOf }) => {
    await acceptPendingConsents(apiOf(page));
    const injection = await injectApiError(page, { url: '/api/baskets/*', method: 'GET', status: 503, times: 3 });
    await page.goto('/baskets/00000048-0001-4000-8000-000000000003');
    const { retry } = await expectErrorState(page, 'baskets.detail.state');
    // the retry interceptor retries a GET twice before the error state shows
    expect(injection.calls()).toBe(3);
    await retry.click();
    await expectDataState(page, 'baskets.detail.state', 'success');
    await expect(page.getByTestId('baskets.detail.description')).toHaveText('Joghurt und Milch, Ablaufdatum morgen');

    await page.goto('/baskets/00000048-0001-4000-8000-0000000009ff');
    await expectErrorState(page, 'baskets.detail.state', { status: 'notFound', retry: false });
  });
});
