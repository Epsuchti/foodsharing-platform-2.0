import type { Page } from '@playwright/test';
import { BASKETS, bubbles, expectDataState, listItem, lookupConversation } from '../../fixtures';
import { checkpoint, checkpointElement, expect, journey as test } from '../../fixtures/checkpoint';
import { Walk, isPhone, openNewestBell } from '../../fixtures/persona';

/**
 * Jonas (jumper@local, lives at 47.386 / 8.525) looks for food baskets around his home, widens the radius to reach
 * Winterthur, sees them on the map, asks Nina (bieb@local) for her apples with a message, which also reaches her as a
 * chat message, and is told by bell when she marks them picked up (her own session). Guide's tins can only be asked
 * for by phone: he asks without a message and withdraws again. Finally he looks up the way to Nina's yoghurt: a basket
 * at someone's home shows an approximate place and a generic label. Distance, rounding of home coordinates and the
 * request rules are unit-tested (BasketRulesTest, baskets.util.spec.ts).
 */
test.use({ role: 'jumper' });

const card = (page: Page, basketId: string) => page.locator(`fs-basket-card[data-basket-id="${basketId}"]`);

test('foodsaver finds baskets nearby, requests one with a message, is told it is his and withdraws another @B2 @N1 @N3', async ({
  page,
  api,
  as,
  apiOf,
  profiles,
}, testInfo) => {
  test.setTimeout(120_000);
  const walk = new Walk(page, testInfo, 'basket seeker');
  const jumperId = await profiles.userId('jumper');
  const message = 'Ich würde die Äpfel gern heute um 18 Uhr holen';

  await walk.step('looks for baskets around his home and on the map', async () => {
    await page.goto('/dashboard');
    await expect(page.getByTestId('dashboard.page.welcome')).toBeVisible();
    await walk.nav('baskets');
    await expectDataState(page, 'baskets.nearby.state', 'success');
    // the eight open Zürich baskets, not the taken one, Winterthur only with a larger radius
    await expect(page.getByTestId('baskets.card')).toHaveCount(8);
    await expect(card(page, BASKETS.salat)).toHaveCount(0);
    await expect(card(page, BASKETS.winterthurBrot)).toHaveCount(0);
    await checkpoint(page, 'nearby');
    await walk.fill(page.getByTestId('baskets.nearby.distance'), '30');
    await expect(card(page, BASKETS.winterthurBrot)).toBeVisible();
    await expect(page.getByTestId('baskets.card')).toHaveCount(10);
    // phones switch between list and map, wider screens show both
    if (isPhone(page)) await walk.click(page.getByTestId('baskets.nearby.showMap'));
    const map = page.getByTestId('baskets.nearby.map');
    await expect(map.locator('.fs-map-marker[data-kind="basket"]')).toHaveCount(10);
    await expect(map.locator('.fs-map-marker[data-kind="home"]')).toHaveCount(1);
    // the map keeps or refits its view after the radius change depending on timing (seen 1 of 3 runs): the markers
    // are counted above, the capture masks the map
    await checkpoint(page, 'nearby-30-km', { mask: [map] });
    if (isPhone(page)) await walk.click(page.getByTestId('baskets.nearby.showList'));
  });

  await walk.step('asks Nina for her apples with a message', async () => {
    await walk.click(card(page, BASKETS.aepfel).getByTestId('baskets.card.link'));
    await expect(page).toHaveURL(new RegExp(`/baskets/${BASKETS.aepfel}$`));
    await expectDataState(page, 'baskets.detail.state', 'success');
    await expect(page.getByTestId('baskets.detail.photo')).toBeVisible();
    await walk.click(page.getByTestId('baskets.detail.request'));
    await walk.fill(page.getByTestId('baskets.requestDialog.message'), message);
    await checkpoint(page, 'request-dialog');
    await walk.click(page.getByTestId('baskets.requestDialog.send'));
    await expect(page.getByTestId('baskets.detail.myRequest')).toHaveAttribute('data-status', 'REQUESTED');
    await expect(page.getByTestId('baskets.detail.request')).toHaveCount(0);
    await expect(page.getByTestId('baskets.detail.withdraw')).toBeVisible();
  });

  await walk.step('finds his message in the chat with Nina', async () => {
    const bieb = apiOf(await as('bieb'));
    const conversation = await lookupConversation(bieb, [jumperId]);
    await walk.nav('messages');
    await walk.click(listItem(page, conversation.id));
    await expect(page).toHaveURL(new RegExp(`/messages/${conversation.id}$`));
    await expect(bubbles(page).filter({ hasText: message })).toHaveCount(1);
  });

  await walk.step('is told the apples are his', async () => {
    // Nina marks the request picked up (her own session)
    const bieb = apiOf(await as('bieb'));
    const answered = await bieb.patch(`/api/baskets/${BASKETS.aepfel}/requests/${jumperId}`, { status: 'PICKED_UP' });
    expect(answered.ok(), await answered.text()).toBe(true);
    await openNewestBell(walk, api, 'BASKET_REQUEST_ANSWERED');
    await expect(page).toHaveURL(new RegExp(`/baskets/${BASKETS.aepfel}$`));
    await expectDataState(page, 'baskets.detail.state', 'success');
    await expect(page.getByTestId('baskets.detail.myRequest')).toHaveAttribute('data-status', 'PICKED_UP');
    await expect(page.getByTestId('baskets.detail.withdraw')).toHaveCount(0);
    await checkpoint(page, 'request-picked-up', { mask: [page.getByTestId('baskets.detail.map')] });
  });

  await walk.step('asks for the tins without a message and withdraws', async () => {
    await walk.nav('baskets');
    await expectDataState(page, 'baskets.nearby.state', 'success');
    await expect(card(page, BASKETS.aepfel)).toHaveCount(0);
    await walk.click(card(page, BASKETS.konserven).getByTestId('baskets.card.link'));
    await expectDataState(page, 'baskets.detail.state', 'success');
    // only a mobile number: no message button, the dialog says so
    await expect(page.getByTestId('baskets.detail.message')).toHaveCount(0);
    await expect(page.getByTestId('baskets.detail.mobile')).toBeVisible();
    await walk.click(page.getByTestId('baskets.detail.request'));
    await expect(page.getByTestId('baskets.requestDialog.noMessage')).toBeVisible();
    await walk.click(page.getByTestId('baskets.requestDialog.send'));
    await expect(page.getByTestId('baskets.detail.myRequest')).toHaveAttribute('data-status', 'REQUESTED');
    await walk.click(page.getByTestId('baskets.detail.withdraw'));
    await expect(page.getByTestId('baskets.detail.myRequest')).toHaveAttribute('data-status', 'WITHDRAWN');
    await expect(page.getByTestId('baskets.detail.request')).toBeVisible();
  });

  await walk.step('looks up the way to Nina\'s yoghurt', async () => {
    await page.goto(`/baskets/${BASKETS.joghurt}`);
    await expectDataState(page, 'baskets.detail.state', 'success');
    await walk.click(page.getByTestId('baskets.detail.navigate'));
    await expect(page.getByTestId('baskets.detail.navigate.note')).toBeVisible();
    // three decimals for a basket at someone's home, and never its description as the label
    await expect(page.getByTestId('baskets.detail.navigate.google')).toHaveAttribute('href', /destination=47\.38,8\.51$/);
    await expect(page.getByTestId('baskets.detail.navigate.apple')).toHaveAttribute('href', /daddr=47\.38,8\.51&q=Essenskorb$/);
    await checkpointElement(page.locator('.p-popover'), 'navigate-approximate');
    await page.keyboard.press('Escape');
    await expect(page.getByTestId('baskets.detail.navigate')).toBeFocused();
  });

  // measured 2026-11-15: 14 clicks on desktop, 19 on the phone
  walk.finish({ steps: 6, clicks: 19, fields: 2 });
});
