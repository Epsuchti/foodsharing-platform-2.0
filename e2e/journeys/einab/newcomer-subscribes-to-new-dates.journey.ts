import { SEED_NOW } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { ZH, setIntroSettings } from '../../fixtures/flows/einab';
import { Walk } from '../../fixtures/persona';

/**
 * Luca (einab1@local, a Zürich newcomer) first looks at Zürich's next intro pickup dates without being signed in: the
 * public page shows only date, mode, city and free places (decision 156, S17 D5). "Notify me" sends him to the login,
 * and after it to his intro pickups, where he finds his foodsaver ID with its explanation and subscribes to new dates
 * by e-mail. When guide Julien adds a date Luca can book, the mail names the city, never the business, and its link
 * unsubscribes him without a login. Rules behind it (verified users cannot subscribe, unknown tokens): IntroPickupSubscriptionServiceTest.
 */

test('newcomer looks at the public dates, subscribes to new ones and unsubscribes by mail @EA2 @EA3 @EA9', async ({
  page,
  as,
  api,
  mails,
}, testInfo) => {
  test.setTimeout(120_000);
  const walk = new Walk(page, testInfo, 'newcomer');
  let returnPath = '';

  await walk.step('looks at the public dates of Zürich', async () => {
    await page.goto('/r/zuerich-stadt/intro-pickups');
    await expect(page.getByTestId('introPickups.public.state')).toHaveAttribute('data-state', 'success');
    const cards = page.getByTestId('introPickups.public.card');
    await expect(cards).toHaveCount(15);
    await expect(cards.first().getByTestId('introPickups.card.mode')).toBeVisible();
    await expect(cards.first().getByTestId('introPickups.card.city')).toHaveText('Zürich Stadt');
    await expect(cards.first().getByTestId('introPickups.card.free')).toBeVisible();
    // no category, business, area, address, guide or private info
    await expect(page.getByTestId('introPickups.card.category')).toHaveCount(0);
    await expect(page.getByTestId('introPickups.card.location')).toHaveCount(0);
    for (const hidden of ['Kreis 4', 'Babu', 'Badenerstrasse 1', 'Personaleingang']) await expect(page.getByText(hidden)).toHaveCount(0);
    await expect(page.locator('fs-user-chip')).toHaveCount(0);
    await checkpoint(page, 'public-dates');

    await walk.click(page.getByTestId('introPickups.public.notifyMe'));
    await expect(page).toHaveURL(/\/login\?returnUrl=/);
    returnPath = new URL(page.url()).searchParams.get('returnUrl')!;
    expect(returnPath).toContain(`/me/intro-pickups?subscribe=${ZH}`);
  });

  // he signs in (the login itself is the auth journeys' business)
  const luca = await as('einab1');
  walk.note('signs in between: the login is not part of this walk');

  await walk.step('finds his foodsaver ID on his intro pickups', async () => {
    await luca.goto(returnPath);
    await expect(luca.getByTestId('introPickups.my.state')).toHaveAttribute('data-state', 'success');
    await expect(luca.getByTestId('introPickups.my.foodsaverNumber')).toHaveText('100004');
    await walk.click(luca.getByTestId('introPickups.my.foodsaverId').getByTestId('shared.foodsaverId.help'));
    await expect(luca.getByRole('dialog')).toBeVisible();
    await expect(luca.getByTestId('shared.foodsaverId.number')).toHaveText('100004');
    await checkpoint(luca, 'foodsaver-id-help');
    await luca.keyboard.press('Escape');
    await expect(luca.getByRole('dialog')).toHaveCount(0);
  });

  await walk.step('subscribes to new dates by e-mail', async () => {
    const card = luca.getByTestId('introPickups.subscription');
    await expect(card.getByTestId('introPickups.subscription.subscribe')).toBeVisible();
    await walk.click(card.getByTestId('introPickups.subscription.channel'));
    await walk.click(luca.getByRole('option', { name: 'E-Mail' }));
    await expect(card.getByTestId('introPickups.subscription.channel')).toContainText('E-Mail');
    await walk.click(card.getByTestId('introPickups.subscription.subscribe'));
    await expect(card.getByTestId('introPickups.subscription.active')).toBeVisible();
    await checkpoint(luca, 'subscribed');
  });

  // Luca already had EinAbs with both Zürich guides: the ambassador allows a second one with the same guide, and guide
  // Julien adds a date with the Fairteiler visit his third EinAb needs (only subscribers who may book it are told)
  await api.loginAs('bot');
  await setIntroSettings(api, { preventDuplicateGuideBookings: false });
  await mails.clear();
  await api.loginAs('guide');
  await api.postJson(`/api/regions/${ZH}/guide/intro-pickups`, {
    category: 'RESTAURANT',
    startAt: new Date(Date.parse(SEED_NOW) + 9 * 86_400_000).toISOString(),
    slotCount: 1,
    visitFairteiler: true,
    businessName: 'Restaurant Lokal',
    privateAddress: 'Langstrasse 5, 8004 Zürich',
    publicGeneralLocation: 'Kreis 4',
  });

  await walk.step('unsubscribes with the link in the mail', async () => {
    // the subscription confirmation may still be in the outbox (sent every 10 s): wait for the new-date mail itself
    const mail = await mails.waitForMail({ to: 'einab1@local', subjectContains: 'Neue Einführungsabholung', timeoutMs: 30_000 });
    expect(JSON.stringify(mail)).toContain('Zürich Stadt');
    expect(JSON.stringify(mail)).not.toContain('Restaurant Lokal');
    const unsubscribe = mails.extractPath(mail, /intro-pickups\/unsubscribe/);
    // the link works without a session (opened on another device)
    const anonymous = await as(null);
    await anonymous.goto(unsubscribe);
    walk.count(1);
    await expect(anonymous.getByTestId('introPickups.unsubscribe.done')).toBeVisible();
    await checkpoint(anonymous, 'unsubscribed');
    await luca.reload();
    await expect(luca.getByTestId('introPickups.subscription.subscribe')).toBeVisible();
  });

  await walk.step('finds his foodsaver ID on his profile, too', async () => {
    await luca.goto('/account/profile');
    await expect(luca.getByTestId('account.profile.foodsaverNumber')).toContainText('100004');
    await walk.click(luca.getByTestId('shared.foodsaverId.help'));
    await expect(luca.getByTestId('shared.foodsaverId.number')).toHaveText('100004');
    await luca.keyboard.press('Escape');
  });

  await test.step('a mistyped region link shows not found', async () => {
    await page.goto('/r/no-such-region/intro-pickups');
    await expect(page.getByTestId('introPickups.public.regionState')).toHaveAttribute('data-state', 'notFound');
  });

  walk.finish({ steps: 5, clicks: 7, fields: 0 });
});
