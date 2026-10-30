import { expect, test } from '../../fixtures';

/**
 * A13 account deletion (plan-m7 Q4, A40): the danger zone deletes (anonymises) the own account after the re-typed
 * address; the session ends, a dev login is refused, authored posts stay with "Gelöschtes Mitglied" and the admin
 * user page shows the archive flag with the reason. Seed: foodsaver@local (Sarah Müller) wrote post `0002-…02` in the
 * zuerich-stadt welcome thread `0001-…01` (docs/seed-ids.md).
 */
test.use({ seedReset: 'test' });

const FOODSAVER_ID = '00000001-0001-4000-8000-000000000006';
const WELCOME_THREAD = '00000043-0001-4000-8000-000000000001';
const FOODSAVER_POST = '00000043-0002-4000-8000-000000000002';

test('A13 user deletes account with reason, cannot log in, posts show anonymised author, admin sees archive flag @A13', async ({
  as,
  apiOf,
  api,
}) => {
  const page = await as('foodsaver');
  await page.goto('/account/settings');
  await page.getByTestId('account.settings.danger.open').click();
  const dialog = page.getByTestId('account.settings.danger.dialog');
  await dialog.getByTestId('account.settings.danger.reason').fill('Ich ziehe weg');
  const confirm = page.getByTestId('account.settings.danger.confirm').locator('button');
  await dialog.getByTestId('account.settings.danger.retype').fill('foodsaver@loca');
  await expect(confirm).toBeDisabled();
  await dialog.getByTestId('account.settings.danger.retype').fill('foodsaver@local');
  await confirm.click();

  // logged out on the start page; the old session is dead and a new login is refused
  await expect(page).toHaveURL(/\/$/);
  await expect(page.getByTestId('public.landing.hero')).toBeVisible();
  expect((await apiOf(page).get('/api/users/me')).status()).toBe(401);
  const login = await api.post('/api/dev/login-as', { userId: FOODSAVER_ID });
  expect(login.status()).toBeGreaterThanOrEqual(400);
  expect(login.status()).toBeLessThan(500);

  // the forum post stays, written by the anonymised member
  const member = await as('bieb');
  await member.goto(`/forum/threads/${WELCOME_THREAD}`);
  const post = member.locator(`[data-testid="forum.thread.post"][data-post-id="${FOODSAVER_POST}"]`);
  await expect(post).toContainText('Gelöschtes Mitglied');
  await expect(post).not.toContainText('Sarah Müller');

  // ORGA sees the archive flag with the reason; the account can no longer be changed
  const orga = await as('orga');
  await orga.goto(`/users/${FOODSAVER_ID}`);
  await expect(orga.getByTestId('usersAdmin.user.deletedTag')).toBeVisible();
  await expect(orga.getByTestId('usersAdmin.user.archive')).toBeVisible();
  await expect(orga.getByTestId('usersAdmin.user.deleteReason')).toContainText('Ich ziehe weg');
  await expect(orga.getByTestId('usersAdmin.user.delete')).toHaveCount(0);
});

test('A13 ORGA deletes another account from the user page with a reason @A13', async ({ as }) => {
  const orga = await as('orga');
  const sleepyId = '00000001-0001-4000-8000-000000000008';
  await orga.goto(`/users/${sleepyId}`);
  await orga.getByTestId('usersAdmin.user.delete').click();
  await orga.getByTestId('usersAdmin.user.deleteDialog.message').fill('Auf eigenen Wunsch per Mail');
  await orga.getByTestId('usersAdmin.user.deleteDialog.confirm').click();
  await expect(orga.getByTestId('usersAdmin.user.archive')).toBeVisible();
  await expect(orga.getByTestId('usersAdmin.user.deleteReason')).toContainText('Auf eigenen Wunsch per Mail');

  // ambassadors may administer users but not delete them
  const bot = await as('bot');
  await bot.goto('/users/00000001-0001-4000-8000-000000000007');
  await expect(bot.getByTestId('usersAdmin.user.account')).toBeVisible();
  await expect(bot.getByTestId('usersAdmin.user.delete')).toHaveCount(0);
});
