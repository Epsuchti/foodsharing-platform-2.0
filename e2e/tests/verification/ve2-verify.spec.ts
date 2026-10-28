import { acceptPendingConsents, expect, test } from '../../fixtures';
import { USERS, VERIFY_MESSAGE, ZH, confirmMessage, openQueue, queueCard, verifyViaApi } from './_verification';

/** Ve2: verification queue, verify / unverify with required message, identity confirmation, verification history. */
test.use({ seedReset: 'test' });

interface Bell {
  type: string;
}

test('Ve2 queue lists ready newcomers with EinAb history and comments @Ve2 @page', async ({ as }) => {
  const page = await as('bot');
  await openQueue(page);
  const card = queueCard(page, USERS.pending);
  await expect(card).toBeVisible();
  await expect(card).toHaveAttribute('data-verified', 'false');
  await expect(card.getByTestId('verification.queue.introPickups')).toHaveText(/3\s*\/\s*3/);
  // verified users never show up, unverified ones without complete EinAbs only under "all unverified"
  await expect(queueCard(page, USERS.foodsaver)).toHaveCount(0);
  await expect(queueCard(page, '00000001-0001-4000-8000-000000000004')).toHaveCount(0);
  await openQueue(page, 'ALL_UNVERIFIED');
  await expect(queueCard(page, '00000001-0001-4000-8000-000000000004')).toBeVisible();

  // guide comments are listed (expandable) when there are any, else a hint
  const comments = card.getByTestId('verification.queue.comments');
  if (await comments.count()) {
    await comments.locator('summary').click();
    await expect(comments.getByTestId('verification.queue.comment').first()).toBeVisible();
  }
});

test('Ve2 verify requires a message, sets verified, sends bell and mail, removes welcome bell @Ve2', async ({ as, apiOf, mails }) => {
  const page = await as('bot');
  await openQueue(page);
  const card = queueCard(page, USERS.pending);
  await card.getByTestId('verification.queue.verify').click();
  // too short → the dialog stays open with the length hint as error
  await page.getByTestId('verification.verifyDialog.message').fill('kurz');
  await page.getByTestId('verification.verifyDialog.confirm').click();
  await expect(page.locator('.hint--error')).toBeVisible();
  await expect(card).toHaveAttribute('data-verified', 'false');
  await confirmMessage(page, 'verification.verifyDialog', VERIFY_MESSAGE);
  await expect(card).toHaveAttribute('data-verified', 'true');
  await expect(card.getByTestId('verification.queue.unverify')).toBeVisible();

  const pending = await as('pending');
  const own = apiOf(pending);
  const session = await own.session();
  expect(session.user?.verified).toBe(true);
  const bells = await own.getJson<{ items: Bell[] }>('/api/bells');
  expect(bells.items.map((bell) => bell.type)).toContain('USER_VERIFIED');
  await mails.waitForMail({ to: 'pending@local' });
  // the "new member" bell of the ambassadors about this person is gone
  const botBells = await apiOf(page).getJson<{ items: (Bell & { params: Record<string, unknown> })[] }>('/api/bells');
  expect(botBells.items.filter((bell) => bell.type === 'REGION_NEW_MEMBER' && bell.params['userName'] === 'Amélie Dubois')).toHaveLength(0);
});

test('Ve2 unverify with message and verification history @Ve2 @A8 @D10.3', async ({ as, apiOf }) => {
  const page = await as('bot');
  await verifyViaApi(apiOf(page), USERS.pending);
  await page.goto(`/users/${USERS.pending}/verification-history`);
  const user = page.getByTestId('verification.history.user');
  await expect(user).toHaveAttribute('data-verified', 'true');
  await page.getByTestId('verification.history.unverify').click();
  await confirmMessage(page, 'verification.unverifyDialog', 'Hat den Ausweis nicht gezeigt.');
  await expect(user).toHaveAttribute('data-verified', 'false');
  await expect(page.getByTestId('verification.history.verify')).toBeVisible();

  const entries = page.locator('[data-testid^="verification.history.verification"][data-action]');
  await expect(entries.and(page.locator('[data-action="USER.VERIFIED"]'))).toHaveCount(1);
  await expect(entries.and(page.locator('[data-action="USER.UNVERIFIED"]'))).toHaveCount(1);
});

test('Ve2 identity confirmation not allowed on self @Ve2', async ({ as, api, apiOf }) => {
  // orga reverts bot's own identity confirmation, so bot could only confirm it themself
  await api.loginAs('orga');
  expect((await api.fetch('DELETE', `/api/users/${USERS.bot}/identity`, { reason: 'Neu prüfen' })).status()).toBe(200);

  const page = await as('bot');
  await page.goto(`/users/${USERS.bot}/verification-history`);
  await expect(page.getByTestId('verification.history.identity')).toHaveAttribute('data-confirmed', 'false');
  await expect(page.getByTestId('verification.history.confirmIdentity')).toHaveCount(0);
  const self = await apiOf(page).post(`/api/users/${USERS.bot}/identity`);
  expect(self.status()).toBe(403);
  expect((await self.json()).code).toBe('CANNOT_CONFIRM_OWN_IDENTITY');

  // confirming someone else works from the queue
  await openQueue(page);
  const card = queueCard(page, USERS.pending);
  await card.getByTestId('verification.queue.confirmIdentity').click();
  await expect(card.getByTestId('verification.queue.identityTag')).toBeVisible();
  await expect(card.getByTestId('verification.queue.confirmIdentity')).toHaveCount(0);
});

test('Ve2 FS management admin may verify, plain member may not @Ve2', async ({ as, apiOf }) => {
  const welcome = await as('welcome');
  await acceptPendingConsents(apiOf(welcome));
  await openQueue(welcome);
  await queueCard(welcome, USERS.pending).getByTestId('verification.queue.verify').click();
  await confirmMessage(welcome, 'verification.verifyDialog', VERIFY_MESSAGE);
  await expect(queueCard(welcome, USERS.pending)).toHaveAttribute('data-verified', 'true');

  const member = await as('foodsaver');
  await member.goto(`/regions/${ZH}/verification`);
  await expect(member.getByTestId('verification.queue.state')).toHaveAttribute('data-state', 'forbidden');
  const denied = await apiOf(member).post(`/api/users/${USERS.jumper}/verification`, { message: VERIFY_MESSAGE });
  expect(denied.status()).toBe(403);
});
