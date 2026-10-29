import { acceptPendingConsents, expect, expectDataState, test } from '../../fixtures';
import { chooseOption } from '../stores/_stores';

/**
 * A20 info mail (plan-m7 Q3, A49): ORGA creates a newsletter of kind INFO_MAIL; it reaches every active user who kept
 * "info mails" on, regardless of the newsletter opt-in. Seed: foodsaver@local has no newsletter opt-in, every user
 * keeps `info_mail_message` (default true); jumper switches it off here in the settings.
 */
test.use({ seedReset: 'test' });

test('A20 orga sends info mail reaching users without newsletter opt-in who kept the flag @A20 @N7', async ({
  as,
  apiOf,
  mails,
  notifications,
}) => {
  // jumper switches info mails off, foodsaver switches forum mentions off (A20 settings switches)
  const jumper = await as('jumper');
  await acceptPendingConsents(apiOf(jumper));
  await jumper.goto('/account/settings');
  await expectDataState(jumper, 'account.settings.mailSwitches.state', 'success');
  await expect(jumper.getByTestId('account.settings.infoMail').locator('input')).toBeChecked();
  await jumper.getByTestId('account.settings.infoMail').click();
  await expect(jumper.getByTestId('account.settings.infoMail').locator('input')).not.toBeChecked();
  await expect.poll(async () => (await apiOf(jumper).getJson<{ infoMailMessage: boolean }>('/api/users/me')).infoMailMessage).toBe(false);

  const foodsaver = await as('foodsaver');
  await foodsaver.goto('/account/settings');
  await foodsaver.getByTestId('account.settings.mentions').click();
  await expect
    .poll(async () => (await apiOf(foodsaver).getJson<{ mentionNotifications: boolean }>('/api/users/me')).mentionNotifications)
    .toBe(false);
  await foodsaver.reload();
  await expect(foodsaver.getByTestId('account.settings.mentions').locator('input')).not.toBeChecked();
  await expect(foodsaver.getByTestId('account.settings.infoMail').locator('input')).toBeChecked();

  // orga: kind "info mail" in the global scope
  const orga = await as('orga');
  await orga.goto('/newsletters');
  await expect(orga.getByTestId('newsletters.list.state')).toHaveAttribute('data-state', /success|empty/);
  await chooseOption(orga, 'newsletters.list.kind', /Info-Mail|Info mail/);
  await orga.getByTestId('newsletters.list.create').click();
  await expect(orga).toHaveURL(/\/newsletters\/[0-9a-f-]{36}$/);
  await expect(orga.getByTestId('newsletters.editor.state')).toHaveAttribute('data-state', 'success');
  await expect(orga.getByTestId('newsletters.editor.kind')).toHaveAttribute('data-kind', 'INFO_MAIL');
  await expect(orga.getByTestId('newsletters.editor.infoMailHint')).toBeVisible();
  const subject = `E2E Info-Mail ${Date.now()}`;
  await orga.getByTestId('newsletters.editor.subject').fill(subject);
  await orga.getByTestId('newsletters.editor.body').fill('Wichtig für **alle** Mitglieder.');
  await orga.getByTestId('newsletters.editor.save').click();
  await expect(orga.getByTestId('newsletters.editor.save').locator('button')).toBeDisabled();

  const count = Number(await orga.getByTestId('newsletters.editor.recipients').getAttribute('data-count'));
  expect(count).toBeGreaterThan(0);
  await orga.getByTestId('newsletters.editor.queue').click();
  await expect(orga.locator('.p-confirmdialog')).toContainText(String(count));
  await orga.locator('.p-confirmdialog-accept-button').click();
  await notifications.runJob('newsletter-send');
  await expect(orga.getByTestId('newsletters.editor.progress')).toHaveAttribute('data-status', 'SENT', { timeout: 20_000 });

  // foodsaver (no newsletter opt-in, info mails on) gets it; jumper (opted in, info mails off) does not
  await mails.waitForMail({ to: 'foodsaver@local', subjectContains: subject });
  expect((await mails.list('jumper@local')).some((mail) => mail.subject.includes(subject))).toBe(false);

  // the list marks it as info mail
  await orga.goto('/newsletters');
  await expect(orga.getByTestId('newsletters.list.item').filter({ hasText: subject }).getByTestId('newsletters.list.infoMail')).toBeVisible();
});
