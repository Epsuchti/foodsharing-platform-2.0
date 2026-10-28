import { acceptPendingConsents, expect, expectDataState, expectNoSeriousViolations, test } from '../../fixtures';

/** MB1 region mailboxes (A35): bot administrates Kanton Zürich (no mailbox yet); welcome-zuerich is taken. */
test.use({ seedReset: 'test' });

test.describe('bot', () => {
  test.use({ role: 'bot' });

  test('MB1 ambassador creates region mailbox with address, invalid and taken names rejected, members managed @MB1 @page', async ({ page, dev }) => {
    const reader = await dev.user('bieb');
    await page.goto('/mailboxes/new');
    await page.getByTestId('mailboxes.create.unit').click();
    await page.getByRole('option', { name: /Kanton/ }).first().click();

    const name = page.getByTestId('mailboxes.create.emailName');
    const hint = page.getByTestId('mailboxes.create.hint');
    await name.fill('Zürich!');
    await expect(name).toHaveAttribute('aria-invalid', 'true');
    await expect(page.getByTestId('mailboxes.create.submit').locator('button')).toBeDisabled();

    await name.fill('welcome-zuerich');
    await page.getByTestId('mailboxes.create.submit').click();
    await expect(hint).toHaveAttribute('data-code', /.+/);

    await name.fill('kanton-zuerich');
    await page.getByTestId('mailboxes.create.name').fill('Kanton Zürich');
    await page.getByTestId('mailboxes.create.submit').click();
    await expect(page).toHaveURL(/\/mailboxes\/[0-9a-f-]{36}\/inbox$/);
    const box = page.locator('[data-testid="mailboxes.rail.mailbox"][data-address^="kanton-zuerich@"]');
    await expect(box).toBeVisible();
    await expectDataState(page, 'mailboxes.list.state', 'empty');
    // settle (the success toast fades out) for the a11y check
    await page.reload();
    await expectDataState(page, 'mailboxes.list.state', 'empty');
    await expectNoSeriousViolations(page);

    await box.getByTestId('mailboxes.rail.manage').click();
    const dialog = page.getByRole('dialog');
    await expect(dialog.getByTestId('mailboxes.manage.address')).toContainText('kanton-zuerich@');
    await dialog.getByTestId('mailboxes.manage.picker.search').fill(reader.displayName.slice(0, 4));
    await dialog.locator(`[data-testid="mailboxes.manage.picker.hit"][data-user-id="${reader.id}"]`).getByTestId('mailboxes.manage.picker.add').click();
    const member = dialog.locator(`[data-testid="mailboxes.manage.member"][data-user-id="${reader.id}"]`);
    await expect(member).toBeVisible();
    await member.getByTestId('mailboxes.manage.removeMember').click();
    await expect(member).toHaveCount(0);

    // deleting is ORGA only
    await expect(dialog.getByTestId('mailboxes.manage.delete')).toHaveCount(0);
  });
});

test.describe('orga', () => {
  test.use({ role: 'orga' });

  test('MB6 orga creates personal mailbox for an ambassador who then sees it @MB6', async ({ page, dev, as, apiOf, api }) => {
    const welcome = await dev.user('welcome');
    await page.goto('/mailboxes/new');
    await page.getByTestId('mailboxes.create.kindUser').check();
    await page.getByTestId('mailboxes.create.picker.search').fill(welcome.displayName.slice(0, 4));
    await page.locator(`[data-testid="mailboxes.create.picker.hit"][data-user-id="${welcome.id}"]`).getByTestId('mailboxes.create.picker.add').click();
    await expect(page.getByTestId('mailboxes.create.user')).toBeVisible();
    await page.getByTestId('mailboxes.create.emailName').fill('lea.persoenlich');
    await page.getByTestId('mailboxes.create.submit').click();
    await expect(page).toHaveURL(/\/mailboxes\/[0-9a-f-]{36}\/inbox$/);
    const mailboxId = page.url().split('/').at(-2)!;

    const welcomePage = await as('welcome');
    await acceptPendingConsents(apiOf(welcomePage));
    await welcomePage.goto('/mailboxes');
    await expect(welcomePage.locator('[data-testid="mailboxes.rail.mailbox"][data-address^="lea.persoenlich@"]')).toBeVisible();

    // ORGA deletes it again (DELETE is ORGA only; ORGA does not read personal mailboxes)
    await expect((await api.delete(`/api/mailboxes/${mailboxId}?force=true`)).status()).toBeLessThan(300);
    await welcomePage.reload();
    await expect(welcomePage.locator('[data-testid="mailboxes.rail.mailbox"][data-address^="lea.persoenlich@"]')).toHaveCount(0);
  });
});
