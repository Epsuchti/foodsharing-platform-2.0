import { acceptPendingConsents } from '../../fixtures';
import { checkpoint, checkpointElement, expect, journey as test } from '../../fixtures/checkpoint';
import { USERS, VERIFY_MESSAGE, queueCard } from '../../fixtures/flows/verification';
import { Walk } from '../../fixtures/persona';

/**
 * Ambassador Reto (bot@local, Zürich Stadt) opens the verification queue, confirms newcomer Amélie's identity (C1:
 * verifying is locked until then), verifies her with the required message and reads her verification history. Amélie
 * gets a bell and a mail, the ambassadors' "new member" bell about her is gone. Then Reto takes the verification back
 * with a message, which the history records too (D11.5.5 persona walkthrough). Rules behind it: VerificationServiceTest
 * (message length, identity first, no self-confirmation), VerificationQueueServiceTest (READY vs all unverified).
 */
test.use({ role: 'bot' });

interface Bell {
  type: string;
  params?: Record<string, unknown>;
}

test('ambassador confirms a newcomer\'s identity, verifies her and takes it back @D11.5.5 @Ve2 @A8 @D10.3', async ({
  page,
  as,
  apiOf,
  mails,
}, testInfo) => {
  test.setTimeout(120_000);
  await acceptPendingConsents(apiOf(page));
  const walk = new Walk(page, testInfo, 'ambassador');
  const card = queueCard(page, USERS.pending);
  const verify = card.getByTestId('verification.queue.verify').locator('button');

  await walk.step('opens the verification queue', async () => {
    await page.goto('/dashboard');
    await expect(page.getByTestId('dashboard.page.welcome')).toBeVisible();
    await walk.nav('verification');
    await expect(page).toHaveURL(/\/verification$/);
    await expect(page.getByTestId('verification.queue.state')).toHaveAttribute('data-state', 'success');
    await expect(card).toHaveAttribute('data-verified', 'false');
    await expect(card.getByTestId('verification.queue.introPickups')).toHaveText(/3\s*\/\s*3/);
    // verified members never show up, unverified ones without complete EinAbs only under "all unverified"
    await expect(queueCard(page, USERS.foodsaver)).toHaveCount(0);
    await expect(queueCard(page, '00000001-0001-4000-8000-000000000004')).toHaveCount(0);
    await checkpoint(page, 'queue-ready');
  });

  await walk.step('looks at all unverified members', async () => {
    await walk.click(page.getByTestId('verification.queue.filter').locator('.p-togglebutton').nth(1));
    await expect(page.getByTestId('verification.queue.state')).toHaveAttribute('data-state', 'success');
    await expect(queueCard(page, '00000001-0001-4000-8000-000000000004')).toBeVisible();
    await expect(card).toBeVisible();
    await checkpoint(page, 'queue-all-unverified');
  });

  await walk.step('confirms Amélie\'s identity', async () => {
    await expect(card.getByTestId('verification.queue.identityOpen')).toBeVisible();
    await expect(card.getByTestId('verification.queue.identityRequired')).toBeVisible();
    await expect(verify).toBeDisabled();
    await checkpointElement(card, 'card-identity-open');
    await walk.click(card.getByTestId('verification.queue.confirmIdentity'));
    await expect(card.getByTestId('verification.queue.identityTag')).toBeVisible();
    await expect(card.getByTestId('verification.queue.identityRequired')).toHaveCount(0);
    await expect(card.getByTestId('verification.queue.confirmIdentity')).toHaveCount(0);
    await expect(verify).toBeEnabled();
  });

  await walk.step('verifies her with a message', async () => {
    await walk.click(card.getByTestId('verification.queue.verify'));
    const message = page.getByTestId('verification.verifyDialog.message');
    // too short: the dialog stays open with the length hint as error
    await walk.fill(message, 'kurz');
    await walk.click(page.getByTestId('verification.verifyDialog.confirm'));
    await expect(page.locator('.hint--error')).toBeVisible();
    await expect(card).toHaveAttribute('data-verified', 'false');
    await checkpoint(page, 'verify-dialog-too-short');
    await walk.fill(message, VERIFY_MESSAGE);
    await walk.click(page.getByTestId('verification.verifyDialog.confirm'));
    await expect(message).toBeHidden();
    await expect(card).toHaveAttribute('data-verified', 'true');
    await expect(card.getByTestId('verification.queue.verifiedTag')).toBeVisible();
    await expect(card.getByTestId('verification.queue.unverify')).toBeVisible();
    await checkpointElement(card, 'card-verified');
  });

  // Amélie is verified, gets a bell and a mail; the ambassadors' "new member" bell about her is gone
  const amelie = apiOf(await as('pending'));
  expect((await amelie.session()).user?.verified).toBe(true);
  expect((await amelie.getJson<{ items: Bell[] }>('/api/bells')).items.map((bell) => bell.type)).toContain('USER_VERIFIED');
  await mails.waitForMail({ to: 'pending@local' });
  const botBells = await apiOf(page).getJson<{ items: Bell[] }>('/api/bells');
  expect(botBells.items.filter((bell) => bell.type === 'REGION_NEW_MEMBER' && bell.params?.['userName'] === 'Amélie Dubois')).toHaveLength(0);

  const entries = page.locator('[data-testid^="verification.history.verification"][data-action]');
  await walk.step('reads her verification history', async () => {
    await walk.click(card.getByTestId('verification.queue.history'));
    await expect(page).toHaveURL(new RegExp(`/users/${USERS.pending}/verification-history$`));
    await expect(page.getByTestId('verification.history.user')).toHaveAttribute('data-verified', 'true');
    await expect(page.getByTestId('verification.history.identity')).toHaveAttribute('data-confirmed', 'true');
    await expect(entries.and(page.locator('[data-action="USER.VERIFIED"]'))).toHaveCount(1);
    await expect(entries.and(page.locator('[data-action="USER.IDENTITY_CONFIRMED"]'))).toHaveCount(1);
    await checkpoint(page, 'verification-history');
  });

  await walk.step('takes the verification back with a message', async () => {
    await walk.click(page.getByTestId('verification.history.unverify'));
    const message = page.getByTestId('verification.unverifyDialog.message');
    await walk.fill(message, 'Hat den Ausweis nicht gezeigt.');
    await checkpoint(page, 'unverify-dialog');
    await walk.click(page.getByTestId('verification.unverifyDialog.confirm'));
    await expect(message).toBeHidden();
    await expect(page.getByTestId('verification.history.user')).toHaveAttribute('data-verified', 'false');
    await expect(page.getByTestId('verification.history.verify')).toBeVisible();
    await expect(entries.and(page.locator('[data-action="USER.UNVERIFIED"]'))).toHaveCount(1);
    await checkpoint(page, 'verification-history-unverified');
  });

  walk.finish({ steps: 6, clicks: 10, fields: 3 });
});
