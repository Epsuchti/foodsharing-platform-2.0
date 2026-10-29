import type { Page } from '@playwright/test';
import {
  BASKETS,
  ZURICH_HB,
  acceptPendingConsents,
  bubbles,
  expect,
  expectDataState,
  expectErrorState,
  injectApiError,
  lookupConversation,
  test,
} from '../../fixtures';

/**
 * B2 nearby baskets and requests (plan-m7 Q5, plan-m6 B5). Seed: 8 ACTIVE baskets within 5 km of Zürich HB, two in
 * Winterthur (~20 km); jumper@local lives at 47.386/8.525 and has not requested bieb's apples (`04`, message
 * contact). A request rings a BASKET_REQUEST bell for the owner and, with a message, sends a direct chat message;
 * PICKED_UP marks the basket TAKEN and rings BASKET_REQUEST_ANSWERED for the requester.
 */
test.use({ seedReset: 'test' });

const card = (page: Page, basketId: string) => page.locator(`fs-basket-card[data-basket-id="${basketId}"]`);

/** On narrow screens list and map are switched; on wide ones both show. */
async function showMap(page: Page): Promise<void> {
  const toggle = page.getByTestId('baskets.nearby.showMap');
  if (await toggle.isVisible()) await toggle.click();
}

test.describe('jumper', () => {
  test.use({ role: 'jumper' });

  test('B2 nearby list and map show seed baskets; request with message creates bell and chat message; owner marks picked up @B2 @N1 @N3 @page', async ({
    page,
    as,
    apiOf,
    profiles,
    notifications,
  }) => {
    const jumperId = await profiles.userId('jumper');

    // nearby around jumper's home: the 8 Zürich baskets in 10 km, Winterthur only with a larger radius
    await page.goto('/baskets');
    await expectDataState(page, 'baskets.nearby.state', 'success');
    await expect(page.getByTestId('baskets.card')).toHaveCount(8);
    await expect(card(page, BASKETS.aepfel)).toBeVisible();
    await expect(card(page, BASKETS.salat)).toHaveCount(0); // TAKEN
    await expect(card(page, BASKETS.winterthurBrot)).toHaveCount(0);
    await page.getByTestId('baskets.nearby.distance').fill('30');
    await expect(card(page, BASKETS.winterthurBrot)).toBeVisible();
    await expect(page.getByTestId('baskets.card')).toHaveCount(10);

    await showMap(page);
    const map = page.getByTestId('baskets.nearby.map');
    await expect(map.locator('.fs-map-marker[data-kind="basket"]')).toHaveCount(10);
    await expect(map.locator('.fs-map-marker[data-kind="home"]')).toHaveCount(1);

    // request bieb's apples with a message
    const nearbyList = page.getByTestId('baskets.nearby.showList');
    if (await nearbyList.isVisible()) await nearbyList.click();
    await card(page, BASKETS.aepfel).getByTestId('baskets.card.link').click();
    await expect(page).toHaveURL(new RegExp(`/baskets/${BASKETS.aepfel}$`));
    await expectDataState(page, 'baskets.detail.state', 'success');
    await expect(page.getByTestId('baskets.detail.photo')).toBeVisible();
    await expect(page.getByTestId('baskets.detail.requests')).toHaveCount(0);
    const message = `Ich würde die Äpfel gern heute um 18 Uhr holen ${Date.now()}`;
    await page.getByTestId('baskets.detail.request').click();
    await page.getByTestId('baskets.requestDialog.message').fill(message);
    await page.getByTestId('baskets.requestDialog.send').click();
    await expect(page.getByTestId('baskets.detail.myRequest')).toHaveAttribute('data-status', 'REQUESTED');
    await expect(page.getByTestId('baskets.detail.request')).toHaveCount(0);
    await expect(page.getByTestId('baskets.detail.withdraw')).toBeVisible();

    // bieb gets a bell and the message as a direct chat message
    await expect.poll(async () => (await notifications.bells('bieb', 'BASKET_REQUEST')).map((bell) => bell.link)).toContain(`/baskets/${BASKETS.aepfel}`);
    const bieb = await as('bieb');
    await acceptPendingConsents(apiOf(bieb));
    const conversation = await lookupConversation(apiOf(bieb), [jumperId]);
    await bieb.goto(`/messages/${conversation.id}`);
    await expect(bubbles(bieb).filter({ hasText: message })).toHaveCount(1);

    // bieb opens the bell and marks the request picked up: the basket is taken
    await bieb.goto('/bells');
    await expectDataState(bieb, 'bells.page.state', 'success');
    await bieb.locator('[data-testid="bells.page.item"][data-type="BASKET_REQUEST"]').first().getByTestId('bells.page.item.open').click();
    await expect(bieb).toHaveURL(new RegExp(`/baskets/${BASKETS.aepfel}$`));
    await expectDataState(bieb, 'baskets.detail.state', 'success');
    const row = bieb.locator(`[data-testid="baskets.detail.requestRow"][data-requester-id="${jumperId}"]`);
    await expect(row.getByTestId('baskets.detail.requestMessage')).toContainText(message);
    // the owner opening the basket marks the open request read
    await expect(row.getByTestId('baskets.detail.requestStatus')).toHaveAttribute('data-status', 'READ');
    await row.getByTestId('baskets.detail.answer.PICKED_UP').click();
    await expect(row.getByTestId('baskets.detail.requestStatus')).toHaveAttribute('data-status', 'PICKED_UP');
    await expect(bieb.getByTestId('baskets.detail.status')).toBeVisible();
    await expect(bieb.getByTestId('baskets.detail.edit')).toHaveCount(0);

    // jumper is told and sees the answer; the basket left the nearby list
    await expect.poll(async () => (await notifications.bells('jumper', 'BASKET_REQUEST_ANSWERED')).length).toBeGreaterThan(0);
    await page.reload();
    await expect(page.getByTestId('baskets.detail.myRequest')).toHaveAttribute('data-status', 'PICKED_UP');
    await expect(page.getByTestId('baskets.detail.withdraw')).toHaveCount(0);
    await page.goto('/baskets/mine');
    await expectDataState(page, 'baskets.mine.requestedState', 'success');
    await expect(card(page, BASKETS.aepfel)).toBeVisible();
    await page.goto('/baskets');
    await expectDataState(page, 'baskets.nearby.state', 'success');
    await expect(card(page, BASKETS.aepfel)).toHaveCount(0);
  });

  test('B2 requester withdraws an open request @B2', async ({ page }) => {
    await page.goto(`/baskets/${BASKETS.reis}`);
    await expectDataState(page, 'baskets.detail.state', 'success');
    // jumper's earlier request was declined: no new request possible
    await expect(page.getByTestId('baskets.detail.myRequest')).toHaveAttribute('data-status', 'DENIED');
    await page.goto(`/baskets/${BASKETS.konserven}`);
    await expectDataState(page, 'baskets.detail.state', 'success');
    // mobile only: no message button, the request dialog says so
    await expect(page.getByTestId('baskets.detail.message')).toHaveCount(0);
    await expect(page.getByTestId('baskets.detail.mobile')).toBeVisible();
    await page.getByTestId('baskets.detail.request').click();
    await expect(page.getByTestId('baskets.requestDialog.noMessage')).toBeVisible();
    await page.getByTestId('baskets.requestDialog.send').click();
    await expect(page.getByTestId('baskets.detail.myRequest')).toHaveAttribute('data-status', 'REQUESTED');
    await page.getByTestId('baskets.detail.withdraw').click();
    await expect(page.getByTestId('baskets.detail.myRequest')).toHaveAttribute('data-status', 'WITHDRAWN');
    await expect(page.getByTestId('baskets.detail.request')).toBeVisible();
  });

  test('B2 nearby load error shows retry @B2', async ({ page }) => {
    const injection = await injectApiError(page, { url: '/api/baskets/nearby', status: 503, times: 3 });
    await page.goto('/baskets');
    const { retry } = await expectErrorState(page, 'baskets.nearby.state');
    expect(injection.calls()).toBe(3);
    await retry.click();
    await expectDataState(page, 'baskets.nearby.state', 'success');
  });
});

test.describe('profile without address', () => {
  test.use({ role: 'profile', geolocation: ZURICH_HB, permissions: ['geolocation'] });

  test('B2 without a profile address the browser location finds nearby baskets @B2', async ({ page, apiOf }) => {
    await acceptPendingConsents(apiOf(page));
    await page.goto('/baskets');
    await expect(page.getByTestId('baskets.nearby.position')).toBeVisible();
    await page.getByTestId('baskets.nearby.locate').click();
    await expectDataState(page, 'baskets.nearby.state', 'success');
    await expect(page.getByTestId('baskets.nearby.fromDevice')).toBeVisible();
    await expect(page.getByTestId('baskets.card')).toHaveCount(8);
  });
});
