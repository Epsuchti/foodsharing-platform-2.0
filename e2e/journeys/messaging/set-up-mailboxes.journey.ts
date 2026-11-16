import { acceptPendingConsents, expectDataState } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { Walk } from '../../fixtures/persona';

/**
 * Mailboxes are set up (MB1, MB6, A35). Ambassador Reto (bot@local, administrates Kanton Zürich, which has no mailbox
 * yet) creates the region's address: a name with "!" is refused at once, `welcome-zuerich` is taken, `kanton-zuerich`
 * works; he adds Nina (bieb@local) as a reader and removes her again — deleting is not offered to him. ORGA creates a
 * personal mailbox for ambassador Lea (welcome@local), who finds it in her mailboxes. The name rules, uniqueness, who
 * may create which mailbox and member rights are unit tests (MailboxServiceTest, MailboxAccessServiceTest); ORGA
 * deleting a mailbox (no UI lists personal mailboxes for ORGA) is unit MailboxDeleteTest.
 */

test.describe('ambassador', () => {
  test.use({ role: 'bot' });

  test('ambassador creates the region mailbox and manages its readers @MB1', async ({ page, dev }, testInfo) => {
    test.setTimeout(120_000);
    const walk = new Walk(page, testInfo, 'ambassador');
    const reader = await dev.user('bieb');

    await walk.step('creates the address of Kanton Zürich', async () => {
      await page.goto('/mailboxes/new');
      await walk.click(page.getByTestId('mailboxes.create.unit'));
      await walk.click(page.getByRole('option', { name: /Kanton/ }).first());
      const name = page.getByTestId('mailboxes.create.emailName');
      await walk.fill(name, 'Zürich!');
      await expect(name).toHaveAttribute('aria-invalid', 'true');
      await expect(page.getByTestId('mailboxes.create.submit').locator('button')).toBeDisabled();
      await checkpoint(page, 'invalid-name');

      await walk.fill(name, 'welcome-zuerich');
      await walk.click(page.getByTestId('mailboxes.create.submit'));
      await expect(page.getByTestId('mailboxes.create.hint')).toHaveAttribute('data-code', /.+/);
      await checkpoint(page, 'taken-name');

      await walk.fill(name, 'kanton-zuerich');
      await walk.fill(page.getByTestId('mailboxes.create.name'), 'Kanton Zürich');
      await walk.click(page.getByTestId('mailboxes.create.submit'));
      await expect(page).toHaveURL(/\/mailboxes\/[0-9a-f-]{36}\/inbox$/);
      await expect(page.locator('[data-testid="mailboxes.rail.mailbox"][data-address^="kanton-zuerich@"]')).toBeVisible();
      await expectDataState(page, 'mailboxes.list.state', 'empty');
      await checkpoint(page, 'new-mailbox');
    });

    await walk.step('adds Nina as a reader and removes her again', async () => {
      const box = page.locator('[data-testid="mailboxes.rail.mailbox"][data-address^="kanton-zuerich@"]');
      await walk.click(box.getByTestId('mailboxes.rail.manage'));
      const dialog = page.getByRole('dialog');
      await expect(dialog.getByTestId('mailboxes.manage.address')).toContainText('kanton-zuerich@');
      await walk.fill(dialog.getByTestId('mailboxes.manage.picker.search'), reader.displayName.slice(0, 4));
      await walk.click(
        dialog.locator(`[data-testid="mailboxes.manage.picker.hit"][data-user-id="${reader.id}"]`).getByTestId('mailboxes.manage.picker.add'),
      );
      const member = dialog.locator(`[data-testid="mailboxes.manage.member"][data-user-id="${reader.id}"]`);
      await expect(member).toBeVisible();
      // deleting a mailbox is ORGA only
      await expect(dialog.getByTestId('mailboxes.manage.delete')).toHaveCount(0);
      await checkpoint(page, 'manage-readers');
      await walk.click(member.getByTestId('mailboxes.manage.removeMember'));
      await expect(member).toHaveCount(0);
    });

    walk.finish({ steps: 2, clicks: 7, fields: 5 });
  });
});

test.describe('orga', () => {
  test.use({ role: 'orga' });

  test('ORGA gives an ambassador a personal mailbox @MB6', async ({ page, dev, as, apiOf }, testInfo) => {
    test.setTimeout(120_000);
    const walk = new Walk(page, testInfo, 'orga');
    const welcome = await dev.user('welcome');
    const lea = await as('welcome');
    await acceptPendingConsents(apiOf(lea));
    const leasBox = () => lea.locator('[data-testid="mailboxes.rail.mailbox"][data-address^="lea.persoenlich@"]');

    await walk.step('creates the personal mailbox for Lea', async () => {
      await page.goto('/mailboxes/new');
      await walk.check(page.getByTestId('mailboxes.create.kindUser'));
      await walk.fill(page.getByTestId('mailboxes.create.picker.search'), welcome.displayName.slice(0, 4));
      await walk.click(
        page.locator(`[data-testid="mailboxes.create.picker.hit"][data-user-id="${welcome.id}"]`).getByTestId('mailboxes.create.picker.add'),
      );
      await expect(page.getByTestId('mailboxes.create.user')).toBeVisible();
      await walk.fill(page.getByTestId('mailboxes.create.emailName'), 'lea.persoenlich');
      await checkpoint(page, 'personal-mailbox-form');
      await walk.click(page.getByTestId('mailboxes.create.submit'));
      await expect(page).toHaveURL(/\/mailboxes\/[0-9a-f-]{36}\/inbox$/);
    });

    await test.step('Lea finds it in her mailboxes', async () => {
      await lea.goto('/mailboxes');
      await expect(leasBox()).toBeVisible();
      await checkpoint(lea, 'lea-mailboxes');
    });

    walk.finish({ steps: 1, clicks: 3, fields: 2 });
  });
});
