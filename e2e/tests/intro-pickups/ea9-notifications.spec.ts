import { SEED_NOW, expect, test } from '../../fixtures';
import { ZH } from './_intro';

/** EA9: subscribe to new EinAb dates, get a mail when one becomes bookable, unsubscribe by link; verified users don't. */
test.use({ seedReset: 'test' });

test('EA9 subscribe, new bookable EinAb notifies by mail, unsubscribe link, verified stops @EA9', async ({ as, api, apiOf, mails }) => {
  const page = await as('einab1');
  await page.goto(`/me/intro-pickups?subscribe=${ZH}`);
  const card = page.getByTestId('introPickups.subscription');
  await expect(card.getByTestId('introPickups.subscription.subscribe')).toBeVisible();
  await card.getByTestId('introPickups.subscription.channel').click();
  await page.getByRole('option', { name: 'E-Mail' }).click();
  await expect(card.getByTestId('introPickups.subscription.channel')).toContainText('E-Mail');
  await card.getByTestId('introPickups.subscription.subscribe').click();
  await expect(card.getByTestId('introPickups.subscription.active')).toBeVisible();
  const subscription = await apiOf(page).getJson<{ channel: string; active: boolean }>('/api/users/me/intro-pickup-subscription');
  expect(subscription).toMatchObject({ channel: 'EMAIL', active: true });

  // the guide adds a date → a mail to einab1 with an unsubscribe link
  await mails.clear();
  await api.loginAs('guide');
  await api.postJson(`/api/regions/${ZH}/guide/intro-pickups`, {
    category: 'RESTAURANT',
    startAt: new Date(Date.parse(SEED_NOW) + 9 * 86_400_000).toISOString(),
    slotCount: 1,
    visitFairteiler: false,
    businessName: 'Restaurant Lokal',
    privateAddress: 'Langstrasse 5, 8004 Zürich',
    publicGeneralLocation: 'Kreis 4',
  });
  const mail = await mails.waitForMail({ to: 'einab1@local' });
  const unsubscribe = mails.extractPath(mail, /intro-pickups\/unsubscribe/);

  const anonymous = await as(null);
  await anonymous.goto(unsubscribe);
  await expect(anonymous.getByTestId('introPickups.unsubscribe.done')).toBeVisible();
  await page.reload();
  await expect(page.getByTestId('introPickups.subscription.subscribe')).toBeVisible();

  // verified users have no subscription card and the API refuses it
  const foodsaver = await as('foodsaver');
  await foodsaver.goto('/me/intro-pickups');
  await expect(foodsaver.getByTestId('introPickups.my.state')).toHaveAttribute('data-state', /success|empty/);
  await expect(foodsaver.getByTestId('introPickups.subscription')).toHaveCount(0);
  await api.loginAs('foodsaver');
  const refused = await api.put('/api/users/me/intro-pickup-subscription', { regionId: ZH, channel: 'IN_APP', language: 'de' });
  expect(refused.status()).toBe(409);
});

test('EA9 unsubscribe page without token explains the problem @EA9', async ({ page }) => {
  await page.goto('/intro-pickups/unsubscribe');
  await expect(page.getByTestId('introPickups.unsubscribe.missing')).toBeVisible();
  await page.goto('/intro-pickups/unsubscribe?token=nope');
  await expect(page.getByTestId('introPickups.unsubscribe.state')).toHaveAttribute('data-state', /error|notFound/);
});
