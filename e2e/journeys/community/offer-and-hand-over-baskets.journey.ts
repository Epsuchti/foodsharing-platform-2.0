import path from 'node:path';
import { BASKETS, expectDataState } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { acceptConfirm } from '../../fixtures/flows/forum';
import { NAMES } from '../../fixtures/flows/stores';
import { Walk } from '../../fixtures/persona';

/**
 * Sarah (foodsaver@local, home address in Zürich) offers bread and dairy with a photo, a pin on the map instead of her
 * home, a weight, a lifetime of three days and her mobile number; she corrects the basket, hands her seeded bread
 * basket over to Julien (guide@local requested it) and finally deletes the new basket. The short offer and the first
 * request bell are part of journeys/foodsaver/sign-up-and-offer-a-basket. Location and contact rules, the lifetime and
 * who may answer and delete are unit-tested (BasketRulesTest, baskets.util.spec.ts, baskets-pages.spec.ts).
 */
test.use({ role: 'foodsaver' });

/** A 256 px PNG to crop. */
const PHOTO = path.join(__dirname, '..', '..', 'fixtures', 'visual', 'tile.png');

test('foodsaver offers a basket with photo and pin, edits it, hands over a basket and deletes one @B1 @B2', async ({
  page,
  baskets,
}, testInfo) => {
  test.setTimeout(120_000);
  const walk = new Walk(page, testInfo, 'basket owner');
  const description = 'Brot und Zopf vom Samstag';
  let basketId = '';

  await walk.step('offers bread and dairy with a photo and a pin', async () => {
    await page.goto('/dashboard');
    await expect(page.getByTestId('dashboard.page.welcome')).toBeVisible();
    await walk.nav('baskets');
    await expectDataState(page, 'baskets.nearby.state', 'success');
    await walk.click(page.getByTestId('baskets.nearby.offer'));
    await expect(page).toHaveURL(/\/baskets\/new$/);
    await expectDataState(page, 'baskets.form.state', 'success');
    await walk.fill(page.getByTestId('baskets.form.description'), description);
    await walk.check(page.getByTestId('baskets.form.kind.BAKED_GOODS'));
    await walk.check(page.getByTestId('baskets.form.kind.DAIRY'));
    await walk.fill(page.getByTestId('baskets.form.weight'), '2.5');
    await walk.fill(page.getByTestId('baskets.form.lifetime'), '3');
    // the photo is cropped in the browser and uploaded as JPEG
    await page.getByTestId('baskets.form.photo.input').setInputFiles(PHOTO);
    await walk.click(page.getByTestId('baskets.form.photo.apply'));
    await expect(page.getByTestId('baskets.form.photoPreview')).toBeVisible();
    await expect(page.getByTestId('baskets.form.photoUploading')).toHaveCount(0);
    await expect(page.getByTestId('baskets.form.photoSection')).toContainText(/hochgeladen/);
    // a click on the map sets the pin
    await walk.check(page.getByTestId('baskets.form.locationCustom'));
    const map = page.getByTestId('baskets.form.map').locator('.leaflet-container');
    await expect(map).toBeVisible();
    await walk.click(map);
    await expect(page.getByTestId('baskets.form.position')).toBeVisible();
    await walk.check(page.getByTestId('baskets.form.contactMobile'));
    await walk.fill(page.getByTestId('baskets.form.mobile'), '+41791234506');
    await checkpoint(page, 'offer-form', { mask: [page.getByTestId('baskets.form.position')] });
    await walk.click(page.getByTestId('baskets.form.submit'));
    await expect(page).toHaveURL(/\/baskets\/[0-9a-f-]{36}$/);
    basketId = page.url().split('/').pop()!;
    await expectDataState(page, 'baskets.detail.state', 'success');
    await expect(page.getByTestId('baskets.detail.description')).toHaveText(description);
    await expect(page.getByTestId('baskets.detail.kind')).toHaveCount(2);
    await expect(page.getByTestId('baskets.detail.photo')).toBeVisible();
    await expect(page.getByTestId('baskets.detail.weight')).toBeVisible();
    await expect(page.getByTestId('baskets.detail.requestsEmpty')).toBeVisible();
    const created = await baskets.get('foodsaver', basketId);
    expect(created.locationType).toBe('CUSTOM');
    expect(created.expiresAt).toMatch(/^2026-11-05T09:00/);
    expect([created.lat, created.lon]).not.toEqual([47.389, 8.548]);
    expect((await page.request.get(created.photoUrl!)).status()).toBe(200);
    await checkpoint(page, 'own-basket', { mask: [page.getByTestId('baskets.detail.map'), page.getByTestId('baskets.detail.expires')] });
  });

  await walk.step('corrects the basket', async () => {
    await walk.click(page.getByTestId('baskets.detail.edit'));
    await expect(page).toHaveURL(new RegExp(`/baskets/${basketId}/edit$`));
    await expectDataState(page, 'baskets.form.state', 'success');
    await expect(page.getByTestId('baskets.form.description')).toHaveValue(description);
    // the lifetime is set once
    await expect(page.getByTestId('baskets.form.lifetime')).toHaveCount(0);
    await walk.fill(page.getByTestId('baskets.form.description'), `${description} – nur noch Brot`);
    await page.getByTestId('baskets.form.kind.DAIRY').uncheck();
    walk.count(1);
    await walk.click(page.getByTestId('baskets.form.submit'));
    await expect(page).toHaveURL(new RegExp(`/baskets/${basketId}$`));
    await expect(page.getByTestId('baskets.detail.description')).toHaveText(`${description} – nur noch Brot`);
    await expect(page.getByTestId('baskets.detail.kind')).toHaveCount(1);
  });

  await walk.step('hands her bread basket over to Julien', async () => {
    await walk.nav('baskets');
    await expectDataState(page, 'baskets.nearby.state', 'success');
    await walk.click(page.getByTestId('baskets.nearby.mine'));
    await expect(page).toHaveURL(/\/baskets\/mine$/);
    await expectDataState(page, 'baskets.mine.offeredState', 'success');
    await expectDataState(page, 'baskets.mine.requestedState', 'success');
    await expect(page.locator(`fs-basket-card[data-basket-id="${basketId}"]`)).toBeVisible();
    await checkpoint(page, 'my-baskets');
    await walk.click(page.locator(`fs-basket-card[data-basket-id="${BASKETS.brot}"]`).getByTestId('baskets.card.link'));
    await expectDataState(page, 'baskets.detail.state', 'success');
    const row = page.getByTestId('baskets.detail.requestRow').filter({ hasText: NAMES.guide });
    // opening the basket marks the open request read
    await expect(row.getByTestId('baskets.detail.requestStatus')).toHaveAttribute('data-status', 'READ');
    await checkpoint(page, 'requests-on-my-basket', { mask: [page.getByTestId('baskets.detail.map'), page.locator('time')] });
    await walk.click(row.getByTestId('baskets.detail.answer.PICKED_UP'));
    await expect(row.getByTestId('baskets.detail.requestStatus')).toHaveAttribute('data-status', 'PICKED_UP');
    await expect(page.getByTestId('baskets.detail.status')).toBeVisible();
    await expect(page.getByTestId('baskets.detail.edit')).toHaveCount(0);
    await checkpoint(page, 'basket-handed-over', { mask: [page.getByTestId('baskets.detail.map'), page.locator('time')] });
  });

  await walk.step('deletes the new basket', async () => {
    await page.goto(`/baskets/${basketId}`);
    await expectDataState(page, 'baskets.detail.state', 'success');
    await walk.click(page.getByTestId('baskets.detail.delete'));
    await expect(page.getByRole('alertdialog')).toBeVisible();
    await checkpoint(page, 'delete-confirm', { a11y: { include: ['[role="alertdialog"]'] } });
    await acceptConfirm(page);
    walk.count(1);
    await expect(page).toHaveURL(/\/baskets\/mine$/);
    await expectDataState(page, 'baskets.mine.offeredState', 'success');
    await expect(page.locator(`fs-basket-card[data-basket-id="${basketId}"]`)).toHaveCount(0);
    expect((await page.request.get(`/api/baskets/${basketId}`)).status()).toBe(404);
  });

  // measured 2026-11-15: 18 clicks on desktop, 20 on the phone
  walk.finish({ steps: 4, clicks: 20, fields: 5 });
});
