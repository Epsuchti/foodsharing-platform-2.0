import { acceptPendingConsents, expect, expectDataState, expectErrorState, injectApiError, test } from '../../fixtures';

/**
 * A7 internal notes (plan-m7 Q4, A48): ambassadors keep notes about a member on the admin user page (`fs-wall`). Only
 * those who may administer the member see them, never the member. Seed: bot@local is ambassador of zuerich-stadt and
 * wrote the admin note "Seit Monaten inaktiv, im Herbst nachfragen." about sleepy@local (`0002-…03`).
 */
test.use({ seedReset: 'test' });

const SLEEPY_ID = '00000001-0001-4000-8000-000000000008';
const NOTE = 'Hat sich telefonisch gemeldet, kommt im Dezember zurück.';

test('A7 ambassador adds and deletes an internal note, member cannot see it @A7', async ({ as, apiOf }) => {
  const bot = await as('bot');
  await bot.goto(`/users/${SLEEPY_ID}`);
  const posts = bot.getByTestId('usersAdmin.user.notes.post');
  await expect(posts).toHaveCount(1);
  await expect(posts.first()).toContainText('Seit Monaten inaktiv');

  await bot.getByTestId('usersAdmin.user.notes.text').fill(NOTE);
  await bot.getByTestId('usersAdmin.user.notes.submit').click();
  await expect(posts).toHaveCount(2);
  const added = posts.filter({ hasText: NOTE });
  await expect(added).toBeVisible();

  // the member herself: no notes, neither through the API nor on her profile
  const sleepy = await as('sleepy');
  await acceptPendingConsents(apiOf(sleepy));
  const own = await apiOf(sleepy).get(`/api/admin/users/${SLEEPY_ID}/notes`);
  expect(own.status()).toBe(403);
  await sleepy.goto(`/users/${SLEEPY_ID}`);
  await expectDataState(sleepy, 'usersAdmin.user.state', 'forbidden');
  await expect(sleepy.getByText(NOTE)).toHaveCount(0);

  // another member neither
  const member = await as('foodsaver');
  expect((await apiOf(member).get(`/api/admin/users/${SLEEPY_ID}/notes`)).status()).toBe(403);

  // the author deletes the note after confirming
  await added.getByTestId('usersAdmin.user.notes.delete').click();
  await bot.locator('.p-confirmdialog-accept-button').click();
  await expect(posts).toHaveCount(1);
  await expect(bot.getByText(NOTE)).toHaveCount(0);
});

test('A7 notes wall error state @A7', async ({ as }) => {
  const bot = await as('bot');
  // GETs are retried twice by the retry interceptor
  await injectApiError(bot, { url: `/api/admin/users/${SLEEPY_ID}/notes`, method: 'GET', status: 500, times: 3 });
  await bot.goto(`/users/${SLEEPY_ID}`);
  const { retry } = await expectErrorState(bot, 'usersAdmin.user.notes.state');
  await retry.click();
  await expect(bot.getByTestId('usersAdmin.user.notes.post')).toHaveCount(1);
});
